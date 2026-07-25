/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * The side panel: one row per watched item, showing its icon, name and latest
 * instant-buy/instant-sell prices.
 *
 * <p>Bare by design for phase 1 — the configurable price line, categories,
 * sorting and the detail view all arrive later. All methods run on the Swing EDT.
 */
public class PricewatchPanel extends PluginPanel
{
	private static final int ICON_WIDTH = 32;

	private final ItemManager itemManager;

	private final JPanel list = new JPanel();

	private final JLabel empty = new JLabel("Loading prices...", SwingConstants.CENTER);

	/**
	 * Builds the empty panel.
	 *
	 * @param itemManager the client's item manager, used for row icons
	 */
	public PricewatchPanel(ItemManager itemManager)
	{
		super(false);

		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(ColorScheme.DARK_GRAY_COLOR);

		empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		final JPanel anchor = new JPanel(new BorderLayout());

		anchor.setBackground(ColorScheme.DARK_GRAY_COLOR);
		anchor.add(list, BorderLayout.NORTH);

		final JScrollPane scroll = new JScrollPane(anchor,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);

		add(scroll, BorderLayout.CENTER);
	}

	/**
	 * Replaces the visible rows with the given watchlist.
	 *
	 * @param items the watched items, in display order
	 */
	public void rebuild(List<WatchedItem> items)
	{
		list.removeAll();

		if (items.isEmpty())
			list.add(empty);

		for (WatchedItem item : items)
			list.add(buildRow(item));

		list.revalidate();
		list.repaint();
	}

	/** @return one watchlist row: icon on the left, name over a price line on the right. */
	private JPanel buildRow(WatchedItem item)
	{
		final JPanel row = new JPanel(new BorderLayout(6, 0));

		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 4, 4, 4));

		final JLabel icon = new JLabel();

		icon.setPreferredSize(new Dimension(ICON_WIDTH, ICON_WIDTH));
		icon.setHorizontalAlignment(SwingConstants.CENTER);

		final AsyncBufferedImage image = itemManager.getImage(item.getItemId(), 1, item.isStackable());

		image.addTo(icon);

		final JLabel name = new JLabel(item.getName());

		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		final JLabel prices = new JLabel(priceText(item));

		prices.setForeground(priceColor(item));
		prices.setFont(prices.getFont().deriveFont(Font.PLAIN, 11f));

		final JPanel text = new JPanel(new GridLayout(2, 1));

		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		text.add(name);
		text.add(prices);

		row.add(icon, BorderLayout.WEST);
		row.add(text, BorderLayout.CENTER);

		return row;
	}

	/** @return the price line for an item, or why there isn't one. */
	private static String priceText(WatchedItem item)
	{
		if (!item.isTradeable())
			return "Not tradeable";

		if (item.isPriceLoadFailed())
			return "No price data";

		if (!item.hasPrices())
			return "Loading...";

		return String.format("High %,d    Low %,d", item.getHighPrice(), item.getLowPrice());
	}

	/** @return dimmed text for prices restored from cache, normal text for live ones. */
	private static Color priceColor(WatchedItem item)
	{
		if (!item.hasPrices() || !item.isTradeable())
			return ColorScheme.MEDIUM_GRAY_COLOR;

		return item.hasLivePrices() ? ColorScheme.GRAND_EXCHANGE_PRICE : ColorScheme.MEDIUM_GRAY_COLOR;
	}
}
