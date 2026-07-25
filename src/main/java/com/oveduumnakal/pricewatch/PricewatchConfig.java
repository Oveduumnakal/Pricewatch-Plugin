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

	String KEY_PRICE_LINE = "priceLine";
	String KEY_SHOW_COL_HIGH = "showColHigh";
	String KEY_SHOW_COL_LOW = "showColLow";
	String KEY_SHOW_COL_AVG = "showColAvg";
	String KEY_SHOW_COL_VOLUME = "showColVolume";
	String KEY_PRICE_CHANGE_INDICATOR = "priceChangeIndicator";

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

	/** What the price line under each watchlist row's name shows. */
	@ConfigSection(
			name = "Watched Item Display",
			description = "Controls the price line under each watched item",
			position = 1
	)
	String watchedItemSection = "watchedItem";

	/** The right-click menu entry that adds or removes an item from the watchlist. */
	@ConfigSection(
			name = "Context Menu",
			description = "The right-click option for watching an item",
			position = 2
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
			keyName = KEY_PRICE_LINE,
			name = "Price Line",
			description = "Which price data the line under the item name shows. None hides the line, "
					+ "leaving a plain icon and name row.",
			section = watchedItemSection,
			position = 0
	)
	default TimeWindow priceLine()
	{
		return TimeWindow.LIVE;
	}

	@ConfigItem(
			keyName = KEY_SHOW_COL_HIGH,
			name = "Show High",
			description = "Show the high (instant-buy) price on the price line",
			section = watchedItemSection,
			position = 1
	)
	default boolean showColHigh()
	{
		return true;
	}

	@ConfigItem(
			keyName = KEY_SHOW_COL_LOW,
			name = "Show Low",
			description = "Show the low (instant-sell) price on the price line",
			section = watchedItemSection,
			position = 2
	)
	default boolean showColLow()
	{
		return true;
	}

	@ConfigItem(
			keyName = KEY_SHOW_COL_AVG,
			name = "Show Average",
			description = "Show the average price on the price line",
			section = watchedItemSection,
			position = 3
	)
	default boolean showColAvg()
	{
		return false;
	}

	@ConfigItem(
			keyName = KEY_SHOW_COL_VOLUME,
			name = "Show Volume",
			description = "Show traded volume on the price line. The Latest window carries no volume, "
					+ "so pick an averaged window to see a figure.",
			section = watchedItemSection,
			position = 4
	)
	default boolean showColVolume()
	{
		return false;
	}

	@ConfigItem(
			keyName = KEY_PRICE_CHANGE_INDICATOR,
			name = "Price Change Indicator",
			description = "Colour prices green or red as they move. Only the Latest window tracks "
					+ "movement between refreshes.",
			section = watchedItemSection,
			position = 5
	)
	default PriceIndicatorMode priceChangeIndicator()
	{
		return PriceIndicatorMode.CHANGE;
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
