/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.Map;
import javax.inject.Inject;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Scene overlay that outlines watched items lying on the ground.
 *
 * <p>Each frame it walks the plugin's map of on-screen ground items, keeps those whose
 * canonical id is watched, and draws their tile polygon in the configured colour,
 * pulsing with the plugin's breathing alpha. Draws nothing when ground highlighting
 * is switched off.
 */
public class PricewatchGroundOverlay extends Overlay
{
	private final Client client;

	private final PricewatchPlugin plugin;

	private final PricewatchConfig config;

	private final ItemManager itemManager;

	@Inject
	PricewatchGroundOverlay(Client client, PricewatchPlugin plugin, PricewatchConfig config, ItemManager itemManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.highlightWatchedItems().ground())
			return null;

		final Color base = config.highlightColor();
		final Color pulsing = new Color(base.getRed(), base.getGreen(), base.getBlue(),
				Math.round(plugin.breathingAlpha() * 255));

		for (Map.Entry<TileItem, Tile> entry : plugin.getGroundItems().entrySet())
		{
			if (!plugin.isWatched(itemManager.canonicalize(entry.getKey().getId())))
				continue;

			Tile tile = entry.getValue();
			Shape poly = Perspective.getCanvasTilePoly(client, tile.getLocalLocation());
			if (poly == null)
				continue;

			graphics.setColor(pulsing);
			graphics.draw(poly);
		}

		return null;
	}
}
