/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Development entry point that launches a RuneLite client with the plugin loaded (used by {@code ./gradlew run}). */
public class PricewatchPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PricewatchPlugin.class);
		RuneLite.main(args);
	}
}
