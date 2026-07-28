/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

/**
 * Which price seeds the cost basis of an unknown-source quantity change — an
 * auto-added item, a mobile/offline resync, or anything else no detector observed
 * (#219). Split out from the old {@code AutoAddMode} so the fallback price is chosen
 * independently of the on/off {@link PricewatchConfig#autoAddItems() auto-add gate}.
 *
 * <ul>
 *   <li>{@link #HIGH}/{@link #LOW}/{@link #AVG} &ndash; seed at the item's current
 *       high, low, or average price.</li>
 *   <li>{@link #ZERO} &ndash; seed at a zero cost basis (pure gain).</li>
 * </ul>
 *
 * <p>The {@code label} is the human-readable name shown in the config dropdown.
 */
public enum FallbackPricing
{
	HIGH("High"),
	LOW("Low"),
	AVG("Avg"),
	ZERO("Zero");

	private final String label;

	FallbackPricing(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}

	/**
	 * Maps a legacy combined {@code AutoAddMode} name (HIGH/LOW/AVG/ZERO/OFF) to the
	 * fallback price it migrates to (#219). OFF carried no pricing choice, so it lands on
	 * {@link #AVG} (today's default). Returns {@code null} for a value already migrated to a
	 * boolean, a fresh install's {@code null}, or any unrecognised string — the caller then
	 * leaves the setting untouched, keeping the migration idempotent.
	 */
	static FallbackPricing fromLegacyMode(String legacy)
	{
		if (legacy == null)
			return null;

		switch (legacy)
		{
			case "HIGH":
				return HIGH;
			case "LOW":
				return LOW;
			case "ZERO":
				return ZERO;
			case "AVG":
			case "OFF":
				return AVG;
			default:
				return null;
		}
	}
}
