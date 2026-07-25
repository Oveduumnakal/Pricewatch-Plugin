/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;

/**
 * Settings for the plugin. The group name is deliberately distinct from the
 * Stockpile plugin's: the two are independent plugins that a user may run side
 * by side, and sharing a group would make them overwrite each other's keys in
 * the same RuneScape profile.
 *
 * <p>No settings are declared yet — the sections arrive with the features they
 * configure, starting with the price spine in phase 1.
 */
@ConfigGroup(PricewatchConfig.GROUP)
public interface PricewatchConfig extends Config
{
	String GROUP = "pricewatch";
}
