/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Turns the raw watchlist into the list the panel draws: filtered by name, then
 * ordered with favourites pinned above everything else.
 *
 * <p>Favourites are pinned in every mode, including {@link SortMode#MANUAL} —
 * starring an item is a statement about where it belongs, so it outranks the
 * chosen ordering rather than competing with it. Within each of the two blocks
 * the selected mode applies, and {@code MANUAL} leaves the underlying order
 * untouched.
 *
 * <p>Stateless utility.
 */
final class WatchlistOrder
{
	private WatchlistOrder()
	{
	}

	/**
	 * Filters and orders the watchlist for display.
	 *
	 * @param items    the watchlist in its stored order
	 * @param mode     the active sort mode
	 * @param reversed whether the mode's natural direction is flipped
	 * @param filter   a case-insensitive name fragment, or blank for everything
	 * @return a new list; the input is not modified
	 */
	static List<WatchedItem> arrange(List<WatchedItem> items, SortMode mode, boolean reversed, String filter)
	{
		final List<WatchedItem> matched = items.stream()
				.filter(item -> matches(item, filter))
				.collect(Collectors.toList());

		return Stream.concat(
						ordered(matched.stream().filter(WatchedItem::isFavorite), mode, reversed),
						ordered(matched.stream().filter(item -> !item.isFavorite()), mode, reversed))
				.collect(Collectors.toList());
	}

	/** @return the stream sorted by the mode, or untouched when the mode is manual. */
	private static Stream<WatchedItem> ordered(Stream<WatchedItem> items, SortMode mode, boolean reversed)
	{
		return mode.comparator(reversed) == null ? items : items.sorted(mode.comparator(reversed));
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
