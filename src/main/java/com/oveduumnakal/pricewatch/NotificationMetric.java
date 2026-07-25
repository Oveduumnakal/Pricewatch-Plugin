/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The item attribute a {@link NotificationRule} watches &ndash; a price, alchemy
 * profit, volume, percent change, or a categorical rating.
 *
 * <p>Each constant pairs a short {@code label} (used in the rule chip) with a
 * longer {@code displayName} (dropdown + tooltip) and an even shorter
 * {@code abbreviation} (the alerts-table row cell, which is too narrow for the
 * full name), plus a {@link Kind} that drives how the rule's value is entered
 * and compared. Categorical metrics additionally carry their allowed
 * {@code options}. The {@code locks*} predicates capture per-metric UI
 * constraints (e.g. {@link #RANGE_30D} only makes sense over a month).
 *
 * <p>Every metric here reads off market data alone. There is deliberately nothing
 * to alert on about a holding — no quantity, and no profit against what you paid.
 * {@link #HA_PROFIT} is the one profit metric, and it is a property of the item's
 * price against the alchemy payout rather than of anything you own.
 */
public enum NotificationMetric
{
	HIGH("High", "High", "High", Kind.NUMERIC),
	LOW("Low", "Low", "Low", Kind.NUMERIC),
	AVERAGE("Average", "Average", "Avg", Kind.NUMERIC),
	HA_PROFIT("HA Profit", "HA Profit", "HA P", Kind.NUMERIC),
	VOLUME("Volume", "Volume", "Vol", Kind.NUMERIC),
	DELTA_PCT("Δ%", "Price Change", "Δ%", Kind.PERCENT),
	VOLATILITY("Volatility", "Volatility", "Volat", Kind.CATEGORY, "Low", "Medium", "High"),
	LIQUIDITY("Liquidity", "Liquidity", "Liq", Kind.CATEGORY, "Low", "Medium", "High"),
	RANGE_30D("30d Range", "30 Day Range", "30d R", Kind.CATEGORY,
			"Lowest", "Low", "Low Avg", "Average", "High Avg", "High", "Highest");

	/** The value domain of a metric, controlling input and comparison semantics. */
	public enum Kind
	{
		NUMERIC, PERCENT, CATEGORY
	}

	private final String label;
	private final String displayName;
	private final String abbreviation;
	private final Kind kind;
	private final List<String> options;

	NotificationMetric(String label, String displayName, String abbreviation, Kind kind, String... options)
	{
		this.label = label;
		this.displayName = displayName;
		this.abbreviation = abbreviation;
		this.kind = kind;
		this.options = options.length == 0
				? Collections.emptyList()
				: Collections.unmodifiableList(Arrays.asList(options));
	}

	public String getLabel()
	{
		return label;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	/** @return the terse form shown in the narrow notifications-table row (full name is in the tooltip/dropdown). */
	public String getAbbreviation()
	{
		return abbreviation;
	}

	public Kind getKind()
	{
		return kind;
	}

	public List<String> getOptions()
	{
		return options;
	}

	public boolean isCategorical()
	{
		return kind == Kind.CATEGORY;
	}

	/** Categorical metrics compare by exact match, so the operator is forced to "=". */
	public boolean locksOperationToEquals()
	{
		return kind == Kind.CATEGORY;
	}

	/** {@link #RANGE_30D} is inherently a 30-day metric, so its timeframe is pinned to a month. */
	public boolean locksTimeframeToMonth()
	{
		return this == RANGE_30D;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
