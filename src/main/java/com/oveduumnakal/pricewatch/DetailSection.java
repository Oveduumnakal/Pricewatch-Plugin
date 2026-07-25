/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

/**
 * The sections a watched item's detail view can show, each placed independently
 * by a {@link SectionSlot} in the config.
 *
 * <p>The set is deliberately smaller than Stockpile's: its collection log and
 * Collection Current Values block both describe what you own, so neither has a
 * counterpart here.
 */
public enum DetailSection
{
	ITEM_VALUES("Current Values"),
	MARKET_INFO("Market Info"),
	PRICE_OVERVIEW("Price Overview"),
	PRICE_GRAPH("Price Graph"),
	VOLUME_GRAPH("Volume Graph"),
	ALCHEMY("Alchemy"),
	LINKS("Links"),
	ALERTS("Alerts");

	private final String label;

	DetailSection(String label)
	{
		this.label = label;
	}

	/** @return the heading shown above this section in the detail view. */
	public String getLabel()
	{
		return label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
