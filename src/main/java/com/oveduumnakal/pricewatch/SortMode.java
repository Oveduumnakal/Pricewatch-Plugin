/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.Comparator;
import java.util.function.Predicate;

/**
 * How the watchlist is ordered. {@link #MANUAL} keeps the user's own order;
 * every other mode sorts for display only and disables drag reordering. Each
 * mode has a natural direction (Name ascending, the numeric modes descending)
 * that the reverse flag flips; items missing the sort key always sort last,
 * regardless of direction.
 *
 * <p>Stockpile's Value and Profit modes are deliberately absent — both are
 * derived from how much you own and what you paid, neither of which exists
 * here. {@link #PRICE} and {@link #VOLUME} take their place as the numeric
 * modes a market watchlist actually wants.
 *
 * <p>Public because it is the return type of a {@code @ConfigItem} accessor: the
 * RuneLite config proxy lives in another module and must be able to access it, or
 * the plugin fails to start with an {@link IllegalAccessError}.
 */
public enum SortMode
{
	MANUAL("Manual"),
	NAME("Name"),
	PRICE("Price"),
	VOLUME("Volume"),
	CHANGE_24H("24h Change");

	private final String label;

	SortMode(String label)
	{
		this.label = label;
	}

	/**
	 * @param reversed whether to flip this mode's natural direction
	 * @return the display comparator, or {@code null} for {@link #MANUAL}
	 */
	Comparator<WatchedItem> comparator(boolean reversed)
	{
		boolean descending = descending(reversed);
		switch (this)
		{
			case NAME:
				return directed(Comparator.comparing(WatchedItem::getName, String.CASE_INSENSITIVE_ORDER),
						item -> true, descending);
			case PRICE:
				return directed(Comparator.comparingLong(WatchedItem::getAvgPrice),
						item -> item.getAvgPrice() > 0, descending);
			case VOLUME:
				return directed(Comparator.comparingLong(SortMode::volumeKey),
						item -> volumeKey(item) > 0, descending);
			case CHANGE_24H:
				return directed(Comparator.comparingDouble(SortMode::changeKey),
						SortMode::hasChange, descending);
			default:
				return null;
		}
	}

	/** @return whether this mode's effective direction is descending once {@code reversed} is applied. */
	boolean descending(boolean reversed)
	{
		return (this != NAME) ^ reversed;
	}

	/**
	 * Applies the sort direction to an ascending {@code key} comparator while always sorting items
	 * that lack the key ({@code hasKey} false) last, whichever direction is active.
	 */
	private static Comparator<WatchedItem> directed(Comparator<WatchedItem> key,
			Predicate<WatchedItem> hasKey, boolean descending)
	{
		Comparator<WatchedItem> ordered = descending ? key.reversed() : key;
		return Comparator.comparing((WatchedItem item) -> !hasKey.test(item)).thenComparing(ordered);
	}

	/** @return the item's traded volume over the last 24 hours, or 0 when that window is unknown. */
	private static long volumeKey(WatchedItem item)
	{
		PriceStats stats = item.getWindowStats().get(TimeWindow.H24);
		return stats == null ? 0 : stats.getVolume();
	}

	/** @return whether the item has both a current price and a 24h baseline to compute a change from. */
	private static boolean hasChange(WatchedItem item)
	{
		PriceStats stats = item.getWindowStats().get(TimeWindow.H24);
		long baseline = stats == null ? 0 : stats.getAvg();
		return item.getAvgPrice() > 0 && baseline > 0;
	}

	/** @return the percent change of the current price vs the 24h average (0 when either side is unknown). */
	private static double changeKey(WatchedItem item)
	{
		PriceStats stats = item.getWindowStats().get(TimeWindow.H24);
		long baseline = stats == null ? 0 : stats.getAvg();
		long current = item.getAvgPrice();
		if (current <= 0 || baseline <= 0)
			return 0;

		return (double) (current - baseline) / baseline;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
