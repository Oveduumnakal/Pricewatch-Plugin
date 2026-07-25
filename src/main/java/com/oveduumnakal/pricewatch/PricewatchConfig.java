/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.Color;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * Settings for the plugin. The group name is deliberately distinct from the
 * Stockpile plugin's: the two are independent plugins that a user may run side
 * by side, and sharing a group would make them overwrite each other's keys in
 * the same RuneScape profile.
 *
 * <p>Sections arrive with the features they configure, so this grows over the
 * phases rather than landing whole.
 */
@ConfigGroup(PricewatchConfig.GROUP)
public interface PricewatchConfig extends Config
{
	String GROUP = "pricewatch";

	String KEY_WATCHED_ITEMS = "watchedItems";
	String KEY_PRICE_CACHE = "priceCache";

	String KEY_PRICE_REFRESH_SECONDS = "priceRefreshSeconds";

	String KEY_ADD_CONTEXT_MENU_OPTION = "addContextMenuOption";
	String KEY_WATCH_ITEM_COLOR = "watchItemColor";
	String KEY_UNWATCH_ITEM_COLOR = "unwatchItemColor";

	/** Top-level panel behaviour: how often prices refresh, and global toggles. */
	@ConfigSection(
			name = "Main View Settings",
			description = "Top-level main view settings",
			position = 0
	)
	String mainViewSection = "mainView";

	/** The right-click menu entry that adds or removes an item from the watchlist. */
	@ConfigSection(
			name = "Context Menu",
			description = "The right-click option for watching an item",
			position = 1
	)
	String contextMenuSection = "contextMenu";

	@Range(min = 30)
	@ConfigItem(
			keyName = KEY_PRICE_REFRESH_SECONDS,
			name = "Price Refresh (s)",
			description = "How often to refresh GE prices from the API. Minimum 30 seconds.",
			section = mainViewSection,
			position = 0
	)
	default int priceRefreshSeconds()
	{
		return 60;
	}

	@ConfigItem(
			keyName = KEY_ADD_CONTEXT_MENU_OPTION,
			name = "Right-click Watch Option",
			description = "Add a \"Watch item\" / \"Unwatch item\" entry to item right-click menus",
			section = contextMenuSection,
			position = 0
	)
	default boolean addContextMenuOption()
	{
		return true;
	}

	@ConfigItem(
			keyName = KEY_WATCH_ITEM_COLOR,
			name = "\"Watch item\" Colour",
			description = "Colour of the \"Watch item\" context menu entry",
			section = contextMenuSection,
			position = 1
	)
	default Color watchItemColor()
	{
		return new Color(0xd4, 0xe6, 0xfb);
	}

	@ConfigItem(
			keyName = KEY_UNWATCH_ITEM_COLOR,
			name = "\"Unwatch item\" Colour",
			description = "Colour of the \"Unwatch item\" context menu entry",
			section = contextMenuSection,
			position = 2
	)
	default Color unwatchItemColor()
	{
		return new Color(0xfb, 0xd4, 0xd4);
	}
}
