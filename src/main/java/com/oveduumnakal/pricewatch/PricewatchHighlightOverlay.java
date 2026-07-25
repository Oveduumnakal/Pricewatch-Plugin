/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;

import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Widget overlay that outlines watched items in the inventory and bank.
 *
 * <p>For each rendered item widget whose canonical id is watched, it fetches the item's
 * outline and blits it in the configured colour, modulated by the plugin's breathing
 * alpha. Draws nothing when inventory/bank highlighting is switched off.
 */
public class PricewatchHighlightOverlay extends WidgetItemOverlay
{
	private final PricewatchPlugin plugin;

	private final PricewatchConfig config;

	private final ItemManager itemManager;

	@Inject
	PricewatchHighlightOverlay(PricewatchPlugin plugin, PricewatchConfig config, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;

		showOnInventory();
		showOnBank();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!config.highlightWatchedItems().invBank()
				|| !plugin.isWatched(itemManager.canonicalize(itemId)))
		{
			return;
		}

		final Rectangle bounds = widgetItem.getCanvasBounds();
		if (bounds == null)
			return;

		final BufferedImage outline = itemManager.getItemOutline(
				itemId, widgetItem.getQuantity(), config.highlightColor());

		final Composite original = graphics.getComposite();

		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, plugin.breathingAlpha()));
		graphics.drawImage(outline, bounds.x, bounds.y, null);
		graphics.setComposite(original);
	}
}
