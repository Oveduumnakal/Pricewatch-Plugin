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
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;

/**
 * Settings for the plugin. The group name is deliberately distinct from the
 * Stockpile plugin's, so that sharing a RuneScape profile with it never makes the
 * two overwrite each other's keys. That still matters even though the plugins now
 * conflict and cannot run at once: both leave their settings behind when disabled,
 * and a user who switches back and forth would otherwise find them corrupted.
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
	String KEY_CATEGORIES = "categories";
	String KEY_GE_BUY_LIMITS = "geBuyLimits";
	String KEY_SORT_MODE = "sortMode";
	String KEY_SORT_REVERSED = "sortReversed";
	String KEY_COMPACT_VIEW = "compactView";

	String KEY_PRICE_REFRESH_SECONDS = "priceRefreshSeconds";

	String KEY_PRICE_LINE = "priceLine";
	String KEY_SHOW_COL_HIGH = "showColHigh";
	String KEY_SHOW_COL_LOW = "showColLow";
	String KEY_SHOW_COL_AVG = "showColAvg";
	String KEY_SHOW_COL_VOLUME = "showColVolume";
	String KEY_PRICE_CHANGE_INDICATOR = "priceChangeIndicator";

	String KEY_OVERVIEW_PRESET = "overviewPreset";
	String KEY_PRESSURE_WINDOW = "pressureWindow";
	String KEY_STALE_PRICE_THRESHOLD = "stalePriceThresholdMinutes";

	String KEY_SHOW_ITEM_VALUES = "showItemValues";
	String KEY_SHOW_MARKET_INFO = "showMarketInfo";
	String KEY_SHOW_PRICE_OVERVIEW = "showPriceOverview";
	String KEY_SHOW_PRICE_GRAPH = "showPriceGraph";
	String KEY_SHOW_VOLUME_GRAPH = "showVolumeGraph";
	String KEY_SHOW_ALCH_INFO = "showAlchInfo";
	String KEY_SHOW_LINKS = "showLinks";
	String KEY_SHOW_ALERTS = "showAlerts";

	String KEY_ALERT_STYLE = "alertStyle";

	String KEY_GE_INTEGRATION = "geIntegration";
	String KEY_GE_FOCUS_PANEL = "geFocusPanel";

	String KEY_SHOW_SCREEN_OVERLAY = "showScreenOverlay";
	String KEY_SCREEN_OVERLAY_LAYOUT = "screenOverlayLayout";
	String KEY_SCREEN_OVERLAY_ON_TOP = "screenOverlayOnTop";

	String KEY_ADD_CONTEXT_MENU_OPTION = "addContextMenuOption";
	String KEY_WATCH_ITEM_COLOR = "watchItemColor";
	String KEY_UNWATCH_ITEM_COLOR = "unwatchItemColor";

	String KEY_LAST_SEEN_VERSION = "lastSeenVersion";
	String KEY_VERSION_FIRST_SEEN = "versionFirstSeen";
	String KEY_WHATS_NEW_DISMISSED = "whatsNewDismissed";

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

	/** Order and visibility of the per-item detail view sections. */
	@ConfigSection(
			name = "Detailed View",
			description = "Order and visibility of the item detail view sections",
			position = 2
	)
	String detailViewSection = "detailView";

	/** How per-item price alerts are delivered when their conditions come true. */
	@ConfigSection(
			name = "Alerts",
			description = "How per-item price alerts are delivered",
			position = 3
	)
	String alertsSection = "alerts";

	/** The draggable in-game boxes showing selected items' prices. */
	@ConfigSection(
			name = "On-screen Overlay",
			description = "In-game price boxes for selected watched items",
			position = 4
	)
	String screenOverlaySection = "screenOverlay";

	/** Tying the panel to whatever offer is open on the Grand Exchange. */
	@ConfigSection(
			name = "GE Integration",
			description = "Link the panel to the open Grand Exchange offer",
			position = 5
	)
	String geIntegrationSection = "geIntegration";

	/** The right-click menu entry that adds or removes an item from the watchlist. */
	@ConfigSection(
			name = "Context Menu",
			description = "The right-click option for watching an item",
			position = 6
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
		return true;
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
			description = "Color prices green or red as they move. Only the Latest window tracks "
					+ "movement between refreshes.",
			section = watchedItemSection,
			position = 5
	)
	default PriceIndicatorMode priceChangeIndicator()
	{
		return PriceIndicatorMode.CHANGE;
	}



	@ConfigItem(
			keyName = KEY_SHOW_ITEM_VALUES,
			name = "Show Current Values",
			description = "Position of the Current Values section, or None to hide it",
			section = detailViewSection,
			position = 0
	)
	default SectionSlot showItemValues()
	{
		return SectionSlot.FIRST;
	}

	@ConfigItem(
			keyName = KEY_SHOW_MARKET_INFO,
			name = "Show Market Info",
			description = "Position of the Market Info section, or None to hide it",
			section = detailViewSection,
			position = 1
	)
	default SectionSlot showMarketInfo()
	{
		return SectionSlot.SECOND;
	}

	@ConfigItem(
			keyName = KEY_SHOW_PRICE_OVERVIEW,
			name = "Show Price Overview",
			description = "Position of the Price Overview section, or None to hide it",
			section = detailViewSection,
			position = 2
	)
	default SectionSlot showPriceOverview()
	{
		return SectionSlot.THIRD;
	}

	@ConfigItem(
			keyName = KEY_SHOW_PRICE_GRAPH,
			name = "Show Price Graph",
			description = "Position of the Price Graph section, or None to hide it",
			section = detailViewSection,
			position = 3
	)
	default SectionSlot showPriceGraph()
	{
		return SectionSlot.FOURTH;
	}

	@ConfigItem(
			keyName = KEY_SHOW_VOLUME_GRAPH,
			name = "Show Volume Graph",
			description = "Position of the Volume Graph section, or None to hide it",
			section = detailViewSection,
			position = 4
	)
	default SectionSlot showVolumeGraph()
	{
		return SectionSlot.FIFTH;
	}

	@ConfigItem(
			keyName = KEY_SHOW_ALCH_INFO,
			name = "Show Alchemy",
			description = "Position of the Alchemy section, or None to hide it",
			section = detailViewSection,
			position = 5
	)
	default SectionSlot showAlchInfo()
	{
		return SectionSlot.SIXTH;
	}

	@ConfigItem(
			keyName = KEY_SHOW_LINKS,
			name = "Show Links",
			description = "Position of the Links section, or None to hide it",
			section = detailViewSection,
			position = 6
	)
	default SectionSlot showLinks()
	{
		return SectionSlot.SEVENTH;
	}

	@ConfigItem(
			keyName = KEY_SHOW_ALERTS,
			name = "Show Alerts",
			description = "Position of the Alerts section, or None to hide it",
			section = detailViewSection,
			position = 7
	)
	default SectionSlot showAlerts()
	{
		return SectionSlot.EIGHTH;
	}

	@ConfigItem(
			keyName = KEY_OVERVIEW_PRESET,
			name = "Overview Windows",
			description = "Which time windows the price overview grid shows as rows",
			section = detailViewSection,
			position = 8
	)
	default OverviewPreset overviewPreset()
	{
		return OverviewPreset.STANDARD;
	}

	@Range(min = 1)
	@ConfigItem(
			keyName = KEY_STALE_PRICE_THRESHOLD,
			name = "Stale After (min)",
			description = "How old a last-traded time may be before it is dimmed as stale",
			section = detailViewSection,
			position = 9
	)
	default int stalePriceThresholdMinutes()
	{
		return 60;
	}

	@ConfigItem(
			keyName = KEY_PRESSURE_WINDOW,
			name = "Pressure Window",
			description = "How far back the buy/sell pressure bar looks",
			section = detailViewSection,
			position = 10
	)
	default PressureWindow pressureWindow()
	{
		return PressureWindow.DAY;
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
			keyName = KEY_ALERT_STYLE,
			name = "Alerts",
			description = "Master switch and delivery style for per-item price alerts. Set to Off to silence "
					+ "every alert without deleting its rule; otherwise use the gear to choose how they "
					+ "are delivered. Independent of \"Show Alerts\", which only places the rule editor.",
			section = alertsSection,
			position = 0
	)
	default Notification alertStyle()
	{
		return Notification.ON;
	}

	@ConfigItem(
			keyName = KEY_SHOW_SCREEN_OVERLAY,
			name = "Show On-screen Overlay",
			description = "Draw in-game price boxes for items flagged onto the overlay",
			section = screenOverlaySection,
			position = 0
	)
	default boolean showScreenOverlay()
	{
		return true;
	}

	@ConfigItem(
			keyName = KEY_SCREEN_OVERLAY_LAYOUT,
			name = "Overlay Layout",
			description = "Compact shows just the live buy and sell price; Standard adds the configured price line",
			section = screenOverlaySection,
			position = 1
	)
	default OverlayLayout screenOverlayLayout()
	{
		return OverlayLayout.STANDARD;
	}

	@ConfigItem(
			keyName = KEY_SCREEN_OVERLAY_ON_TOP,
			name = "Draw Over Interfaces",
			description = "Draw the boxes above game interfaces rather than behind them",
			section = screenOverlaySection,
			position = 2
	)
	default boolean screenOverlayOnTop()
	{
		return false;
	}

	@ConfigItem(
			keyName = KEY_GE_INTEGRATION,
			name = "GE Integration",
			description = "Off; a \"View in Pricewatch\" button on the offer screen; auto-open the offer's item "
					+ "in the panel; or both.",
			section = geIntegrationSection,
			position = 0
	)
	default GeIntegrationMode geIntegration()
	{
		return GeIntegrationMode.BOTH;
	}

	@ConfigItem(
			keyName = KEY_GE_FOCUS_PANEL,
			name = "Focus the Panel",
			description = "Open and focus the side panel when an offer's item is shown",
			section = geIntegrationSection,
			position = 1
	)
	default boolean geFocusPanel()
	{
		return true;
	}

	@ConfigItem(
			keyName = KEY_WATCH_ITEM_COLOR,
			name = "\"Watch item\" Color",
			description = "Color of the \"Watch item\" context menu entry",
			section = contextMenuSection,
			position = 1
	)
	default Color watchItemColor()
	{
		return new Color(0xd8, 0xfb, 0xd4);
	}

	@ConfigItem(
			keyName = KEY_UNWATCH_ITEM_COLOR,
			name = "\"Unwatch item\" Color",
			description = "Color of the \"Unwatch item\" context menu entry",
			section = contextMenuSection,
			position = 2
	)
	default Color unwatchItemColor()
	{
		return new Color(0xfb, 0xd4, 0xd4);
	}
}
