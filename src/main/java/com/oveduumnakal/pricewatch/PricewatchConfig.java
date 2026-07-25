/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

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

	String KEY_PRICE_REFRESH_SECONDS = "priceRefreshSeconds";

	/** Top-level panel behaviour: how often prices refresh, and global toggles. */
	@ConfigSection(
			name = "Main View Settings",
			description = "Top-level main view settings",
			position = 0
	)
	String mainViewSection = "mainView";

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
}
