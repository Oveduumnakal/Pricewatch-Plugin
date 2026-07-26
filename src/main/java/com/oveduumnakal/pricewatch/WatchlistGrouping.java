/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Value;

/**
 * Splits the watchlist into the accordion groups the panel draws: favourites
 * first, then each user category in its own order, then everything uncategorised.
 *
 * <p>Favourites are a group rather than a set of pinned rows, matching how
 * Stockpile presents them: an item that is starred appears under Favourites and
 * nowhere else, so it is never listed twice. Empty groups are dropped entirely,
 * including after a filter has emptied them — a header with nothing under it is
 * just noise.
 *
 * <p>Stateless utility.
 */
final class WatchlistGrouping
{
	private WatchlistGrouping()
	{
	}

	/** One rendered accordion group: its identity, its header label, and the rows beneath it. */
	@Value
	static class Group
	{
		/** Stable key used to persist the collapsed state ({@link CategoryState} constants or the name). */
		String key;

		/** Header text shown to the user. */
		String label;

		/** Whether the group is currently rolled up. */
		boolean collapsed;

		/** The rows in this group, already filtered and sorted. */
		List<WatchedItem> items;
	}

	/**
	 * Groups, filters and sorts the watchlist for display.
	 *
	 * @param items                 the watchlist in its stored order
	 * @param categories            the user's categories, in their chosen order
	 * @param favoritesCollapsed    whether the Favourites group is rolled up
	 * @param uncategorizedCollapsed whether the Uncategorised group is rolled up
	 * @param mode                  the active sort mode
	 * @param reversed              whether the mode's natural direction is flipped
	 * @param filter                a case-insensitive name fragment, or blank for everything
	 * @return the non-empty groups, in display order
	 */
	static List<Group> group(List<WatchedItem> items, List<CategoryState> categories,
			boolean favoritesCollapsed, boolean uncategorizedCollapsed,
			SortMode mode, boolean reversed, String filter)
	{
		final List<WatchedItem> matched = WatchlistOrder.filterAndSort(items, mode, reversed, filter);
		final List<Group> groups = new ArrayList<>();

		addGroup(groups, CategoryState.FAVORITES_KEY, "Favorites", favoritesCollapsed,
				matched.stream()
						.filter(WatchedItem::isFavorite)
						.collect(Collectors.toList()));

		for (CategoryState category : categories)
		{
			addGroup(groups, category.getName(), category.getName(), category.isCollapsed(),
					matched.stream()
							.filter(item -> !item.isFavorite())
							.filter(item -> category.getName().equals(item.getCategory()))
							.collect(Collectors.toList()));
		}

		addGroup(groups, CategoryState.UNCATEGORIZED_KEY, "Uncategorized", uncategorizedCollapsed,
				matched.stream()
						.filter(item -> !item.isFavorite())
						.filter(item -> !inAnyCategory(item, categories))
						.collect(Collectors.toList()));

		return groups;
	}

	/** Appends a group, unless it has no rows to show. */
	private static void addGroup(List<Group> groups, String key, String label, boolean collapsed,
			List<WatchedItem> items)
	{
		if (!items.isEmpty())
			groups.add(new Group(key, label, collapsed, items));
	}

	/**
	 * @return whether the item's category is one that still exists. An item whose
	 *         category was deleted out from under it falls back to Uncategorised
	 *         rather than vanishing from the panel
	 */
	private static boolean inAnyCategory(WatchedItem item, List<CategoryState> categories)
	{
		if (item.getCategory() == null || item.getCategory().trim().isEmpty())
			return false;

		return categories.stream().anyMatch(c -> c.getName().equals(item.getCategory()));
	}
}
