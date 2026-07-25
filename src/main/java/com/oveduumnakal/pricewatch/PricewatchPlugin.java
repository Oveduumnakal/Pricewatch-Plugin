/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.image.BufferedImage;
import javax.inject.Inject;

import com.google.inject.Provides;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

/**
 * Plugin entry point: registers the side panel that hosts the watchlist.
 *
 * <p>This is the scaffold. Prices, persistence, the detail view, alerts and the
 * overlays all arrive in later phases; for now the plugin does nothing beyond
 * proving that the build, the style checker and the client wiring all work.
 */
@PluginDescriptor(
		name = "Pricewatch",
		description = "Watchlist of Grand Exchange prices with charts, market ratings and alerts",
		tags = {"price", "prices", "ge", "grand exchange", "market", "watchlist", "alert", "chart"}
)
public class PricewatchPlugin extends Plugin
{
	@Inject
	private ClientToolbar clientToolbar;

	private PricewatchPanel panel;

	private NavigationButton navButton;

	/**
	 * Supplies the config instance to the RuneLite injector.
	 *
	 * @param configManager the client's config manager
	 * @return this plugin's settings
	 */
	@Provides
	PricewatchConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PricewatchConfig.class);
	}

	/**
	 * Builds the panel and adds its navigation button to the client toolbar.
	 */
	@Override
	protected void startUp()
	{
		panel = new PricewatchPanel();

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");

		navButton = NavigationButton.builder()
				.tooltip("Pricewatch")
				.icon(icon)
				.priority(7)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
	}

	/**
	 * Removes the navigation button and releases the panel.
	 */
	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);

		navButton = null;
		panel = null;
	}
}
