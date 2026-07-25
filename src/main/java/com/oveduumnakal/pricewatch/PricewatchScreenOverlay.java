/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * A draggable in-game box showing one watched item's prices, so they can be read
 * without opening the side panel.
 *
 * <p>One instance is created per overlay slot, up to {@link PricewatchPlugin#OVERLAY_MAX},
 * and each carries a slot-unique name so RuneLite persists and drags them independently.
 * A slot with no item behind it draws nothing.
 *
 * <p>The box shows the item's name and its prices, and nothing else. There is no
 * quantity line and no profit line, because there is no holding to count.
 */
public class PricewatchScreenOverlay extends Overlay
{
	private static final Color NAME_COLOR = Color.WHITE;

	private static final Color LABEL_COLOR = new Color(170, 170, 170);

	private static final Color VOLUME_COLOR = new Color(190, 130, 220);

	private static final Color BACKGROUND = ComponentConstants.STANDARD_BACKGROUND_COLOR;

	/** Dark brown border matching RuneLite's tan overlay background rather than a stark black. */
	private static final Color BORDER = new Color(56, 48, 35);

	private static final int PAD = 6;

	private static final int ICON = 18;

	private static final int GAP = 6;

	private static final int SEG_GAP = 5;

	private final PricewatchPlugin plugin;

	private final PricewatchConfig config;

	private final ItemManager itemManager;

	/** Which overlay slot this box renders: the item at that index in the overlay set. */
	private final int slot;

	/** Cached 18px icons keyed by item id, populated asynchronously on first use. */
	private final Map<Integer, BufferedImage> iconCache = new HashMap<>();

	/** One coloured run of text within a rendered line. */
	private static final class Seg
	{
		final String text;
		final Color color;

		Seg(String text, Color color)
		{
			this.text = text;
			this.color = color;
		}
	}

	PricewatchScreenOverlay(PricewatchPlugin plugin, PricewatchConfig config, ItemManager itemManager, int slot)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		this.slot = slot;

		setPosition(OverlayPosition.TOP_LEFT);
	}

	/** @return the layer: above interfaces when configured on top, otherwise behind windows (bank, GE, ...). */
	@Override
	public OverlayLayer getLayer()
	{
		return config.screenOverlayOnTop() ? OverlayLayer.ABOVE_WIDGETS : OverlayLayer.UNDER_WIDGETS;
	}

	/** @return a slot-unique name, so each box is positioned and dragged on its own. */
	@Override
	public String getName()
	{
		return "pricewatchScreenOverlay" + slot;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showScreenOverlay())
			return null;

		final List<WatchedItem> items = plugin.getOverlayItems();
		if (slot >= items.size())
			return null;

		final WatchedItem item = items.get(slot);

		graphics.setFont(FontManager.getRunescapeSmallFont());

		final FontMetrics metrics = graphics.getFontMetrics();
		final int lineHeight = metrics.getHeight();
		final List<List<Seg>> lines = blockLines(item);

		final int width = PAD * 2 + ICON + GAP + maxLineWidth(metrics, lines);
		final int height = PAD * 2 + lines.size() * lineHeight;

		graphics.setColor(BACKGROUND);
		graphics.fillRect(0, 0, width, height);
		graphics.setColor(BORDER);
		graphics.drawRect(0, 0, width - 1, height - 1);

		final BufferedImage icon = iconFor(item);
		if (icon != null)
			graphics.drawImage(icon, PAD, PAD, null);

		int y = PAD;
		for (List<Seg> line : lines)
		{
			drawLine(graphics, metrics, PAD + ICON + GAP, y + metrics.getAscent(), line);
			y += lineHeight;
		}

		return new Dimension(width, height);
	}

	/** @return the item's name line followed by its price lines, used for both measuring and drawing. */
	private List<List<Seg>> blockLines(WatchedItem item)
	{
		final List<List<Seg>> lines = new ArrayList<>();

		lines.add(Arrays.asList(new Seg(item.getName(), NAME_COLOR)));

		if (!item.hasPrices())
		{
			lines.add(Arrays.asList(new Seg(item.isTradeable()
					? "Prices loading…"
					: "Item not tradeable", PricewatchColors.MUTED)));

			return lines;
		}

		if (config.screenOverlayLayout() == OverlayLayout.COMPACT)
		{
			lines.add(compactLine(item));
			return lines;
		}

		final TimeWindow window = config.priceLine();
		if (window != TimeWindow.NONE)
			lines.add(windowLine(item, window));

		return lines;
	}

	/**
	 * @return the compact layout's single line: the live high and low with no window
	 *         label, since a one-line box has no room to say which window it means
	 */
	private static List<Seg> compactLine(WatchedItem item)
	{
		return Arrays.asList(
				new Seg(GpFormat.shortValue(item.getHighPrice()), PricewatchColors.HIGH),
				new Seg(GpFormat.shortValue(item.getLowPrice()), PricewatchColors.LOW));
	}

	/** @return one price line for a window, honouring the configured visible columns. */
	private List<Seg> windowLine(WatchedItem item, TimeWindow window)
	{
		final PriceStats stats = item.getWindowStats().get(window);
		final boolean live = window == TimeWindow.LIVE || stats == null;
		final long high = live ? item.getHighPrice() : stats.getHigh();
		final long low = live ? item.getLowPrice() : stats.getLow();
		final long avg = live ? item.getAvgPrice() : stats.getAvg();

		final List<Seg> line = new ArrayList<>();

		line.add(new Seg(window.getLabel(), LABEL_COLOR));

		if (config.showColHigh())
			line.add(new Seg(GpFormat.shortValue(high), PricewatchColors.HIGH));

		if (config.showColLow())
			line.add(new Seg(GpFormat.shortValue(low), PricewatchColors.LOW));

		if (config.showColAvg())
			line.add(new Seg(GpFormat.shortValue(avg), PricewatchColors.AVG));

		if (config.showColVolume() && !live)
			line.add(new Seg(GpFormat.shortValue(stats.getVolume()), VOLUME_COLOR));

		return line;
	}

	/** Draws one line of coloured segments left to right. */
	private static void drawLine(Graphics2D graphics, FontMetrics metrics, int x, int baseline, List<Seg> segments)
	{
		int cx = x;
		for (Seg seg : segments)
		{
			graphics.setColor(seg.color);
			graphics.drawString(seg.text, cx, baseline);
			cx += metrics.stringWidth(seg.text) + SEG_GAP;
		}
	}

	/** @return the widest of the given lines, in pixels. */
	private static int maxLineWidth(FontMetrics metrics, List<List<Seg>> lines)
	{
		int max = 0;
		for (List<Seg> line : lines)
		{
			int width = 0;
			for (Seg seg : line)
				width += metrics.stringWidth(seg.text) + SEG_GAP;

			max = Math.max(max, width);
		}

		return max;
	}

	/** @return an 18px cached icon for the item, requesting an async load on the first miss. */
	private BufferedImage iconFor(WatchedItem item)
	{
		final BufferedImage cached = iconCache.get(item.getItemId());
		if (cached != null)
			return cached;

		final AsyncBufferedImage image = itemManager.getImage(item.getItemId());

		image.onLoaded(() -> iconCache.put(item.getItemId(),
				toBuffered(image.getScaledInstance(ICON, ICON, Image.SCALE_SMOOTH))));

		return null;
	}

	/** @return a drawable copy of a scaled image. */
	private static BufferedImage toBuffered(Image image)
	{
		final BufferedImage buffered = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D graphics = buffered.createGraphics();

		graphics.drawImage(image, 0, 0, null);
		graphics.dispose();

		return buffered;
	}
}
