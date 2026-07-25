/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Narrows the watchlist by name and puts it in the chosen order.
 *
 * <p>This is the ordering step only — splitting the result into accordion groups
 * is {@link WatchlistGrouping}'s job, and favourites are a group rather than a
 * set of pinned rows.
 *
 * <p>Stateless utility.
 */
final class WatchlistOrder
{
	private WatchlistOrder()
	{
	}

	/**
	 * Filters the watchlist by name and sorts what survives.
	 *
	 * @param items    the watchlist in its stored order
	 * @param mode     the active sort mode
	 * @param reversed whether the mode's natural direction is flipped
	 * @param filter   a case-insensitive name fragment, or blank for everything
	 * @return a new list; the input is not modified
	 */
	static List<WatchedItem> filterAndSort(List<WatchedItem> items, SortMode mode, boolean reversed,
			String filter)
	{
		final List<WatchedItem> matched = items.stream()
				.filter(item -> matches(item, filter))
				.collect(Collectors.toList());

		if (mode.comparator(reversed) == null)
			return matched;

		return matched.stream()
				.sorted(mode.comparator(reversed))
				.collect(Collectors.toList());
	}

	/** @return whether the item's name contains the filter fragment, ignoring case. */
	private static boolean matches(WatchedItem item, String filter)
	{
		if (filter == null || filter.trim().isEmpty())
			return true;

		return item.getName()
				.toLowerCase(Locale.ROOT)
				.contains(filter.trim().toLowerCase(Locale.ROOT));
	}
}
