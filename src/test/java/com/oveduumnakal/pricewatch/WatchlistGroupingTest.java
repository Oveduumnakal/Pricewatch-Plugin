/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link WatchlistGrouping}: which group each item lands in, the order
 * the groups appear in, that empty groups are dropped, and that an item whose
 * category no longer exists still shows up.
 */
public class WatchlistGroupingTest
{
	/** @return a watched item in the given category (null for uncategorised). */
	private static WatchedItem item(String name, String category)
	{
		WatchedItem item = new WatchedItem(name.hashCode(), name);

		item.setCategory(category);

		return item;
	}

	/** @return a starred watched item in the given category. */
	private static WatchedItem favourite(String name, String category)
	{
		WatchedItem item = item(name, category);

		item.setFavorite(true);

		return item;
	}

	/** @return the group labels, in display order. */
	private static List<String> labels(List<WatchlistGrouping.Group> groups)
	{
		return groups.stream()
				.map(WatchlistGrouping.Group::getLabel)
				.collect(Collectors.toList());
	}

	/** @return the item names in the group with the given label. */
	private static List<String> itemsIn(List<WatchlistGrouping.Group> groups, String label)
	{
		return groups.stream()
				.filter(g -> g.getLabel().equals(label))
				.flatMap(g -> g.getItems().stream())
				.map(WatchedItem::getName)
				.collect(Collectors.toList());
	}

	/** @return the grouping of these items under these categories, unfiltered and unsorted. */
	private static List<WatchlistGrouping.Group> group(List<WatchedItem> items, List<CategoryState> categories)
	{
		return WatchlistGrouping.group(items, categories, false, false, SortMode.MANUAL, false, "");
	}

	@Test
	public void favouritesComeFirstThenCategoriesThenUncategorised()
	{
		List<CategoryState> categories = Arrays.asList(
				new CategoryState("Runes", false), new CategoryState("Logs", false));
		List<WatchedItem> items = Arrays.asList(
				item("Yew logs", "Logs"), item("Shark", null),
				favourite("Abyssal whip", null), item("Nature rune", "Runes"));

		assertEquals(Arrays.asList("Favorites", "Runes", "Logs", "Uncategorized"),
				labels(group(items, categories)));
	}

	@Test
	public void categoriesKeepTheirGivenOrderRatherThanBeingSorted()
	{
		List<CategoryState> categories = Arrays.asList(
				new CategoryState("Zed", false), new CategoryState("Alpha", false));
		List<WatchedItem> items = Arrays.asList(item("One", "Zed"), item("Two", "Alpha"));

		assertEquals(Arrays.asList("Zed", "Alpha"), labels(group(items, categories)));
	}

	@Test
	public void aFavouriteAppearsOnlyUnderFavouritesNotAlsoInItsCategory()
	{
		List<CategoryState> categories = Collections.singletonList(new CategoryState("Runes", false));
		List<WatchedItem> items = Collections.singletonList(favourite("Nature rune", "Runes"));

		List<WatchlistGrouping.Group> groups = group(items, categories);

		assertEquals(Collections.singletonList("Favorites"), labels(groups));
		assertEquals(Collections.singletonList("Nature rune"), itemsIn(groups, "Favorites"));
	}

	@Test
	public void emptyGroupsAreDroppedEntirely()
	{
		List<CategoryState> categories = Arrays.asList(
				new CategoryState("Runes", false), new CategoryState("Empty", false));
		List<WatchedItem> items = Collections.singletonList(item("Nature rune", "Runes"));

		assertEquals(Collections.singletonList("Runes"), labels(group(items, categories)));
	}

	@Test
	public void anItemWhoseCategoryWasDeletedFallsBackToUncategorised()
	{
		List<WatchedItem> items = Collections.singletonList(item("Nature rune", "Gone"));

		List<WatchlistGrouping.Group> groups = group(items, Collections.emptyList());

		assertEquals(Collections.singletonList("Uncategorized"), labels(groups));
		assertEquals(Collections.singletonList("Nature rune"), itemsIn(groups, "Uncategorized"));
	}

	@Test
	public void blankCategoryCountsAsUncategorised()
	{
		List<WatchedItem> items = Arrays.asList(item("A", "   "), item("B", null));

		assertEquals(Collections.singletonList("Uncategorized"),
				labels(group(items, Collections.emptyList())));
	}

	@Test
	public void collapsedStateIsCarriedOntoTheGroup()
	{
		List<CategoryState> categories = Collections.singletonList(new CategoryState("Runes", true));
		List<WatchedItem> items = Arrays.asList(item("Nature rune", "Runes"), favourite("Whip", null));

		List<WatchlistGrouping.Group> groups = WatchlistGrouping.group(
				items, categories, true, false, SortMode.MANUAL, false, "");

		assertTrue(groups.stream()
				.filter(g -> g.getLabel().equals("Runes"))
				.allMatch(WatchlistGrouping.Group::isCollapsed));
		assertTrue(groups.stream()
				.filter(g -> g.getLabel().equals("Favorites"))
				.allMatch(WatchlistGrouping.Group::isCollapsed));
	}

	@Test
	public void aCollapsedGroupStillReportsItsItemsSoTheHeaderCanCountThem()
	{
		List<CategoryState> categories = Collections.singletonList(new CategoryState("Runes", true));
		List<WatchedItem> items = Arrays.asList(item("Nature rune", "Runes"), item("Fire rune", "Runes"));

		List<String> runes = itemsIn(group(items, categories), "Runes");

		assertEquals(2, runes.size());
	}

	@Test
	public void filteringEmptiesGroupsAwayRatherThanLeavingBareHeaders()
	{
		List<CategoryState> categories = Collections.singletonList(new CategoryState("Runes", false));
		List<WatchedItem> items = Arrays.asList(item("Nature rune", "Runes"), item("Shark", null));

		List<WatchlistGrouping.Group> groups = WatchlistGrouping.group(
				items, categories, false, false, SortMode.MANUAL, false, "shark");

		assertEquals(Collections.singletonList("Uncategorized"), labels(groups));
	}

	@Test
	public void sortingAppliesWithinEachGroupIndependently()
	{
		List<CategoryState> categories = Collections.singletonList(new CategoryState("Runes", false));
		List<WatchedItem> items = Arrays.asList(
				item("Zed rune", "Runes"), item("Alpha rune", "Runes"),
				favourite("Zed fav", null), favourite("Alpha fav", null));

		List<WatchlistGrouping.Group> groups = WatchlistGrouping.group(
				items, categories, false, false, SortMode.NAME, false, "");

		assertEquals(Arrays.asList("Alpha fav", "Zed fav"), itemsIn(groups, "Favorites"));
		assertEquals(Arrays.asList("Alpha rune", "Zed rune"), itemsIn(groups, "Runes"));
	}

	@Test
	public void anEmptyWatchlistProducesNoGroupsAtAll()
	{
		assertTrue(group(Collections.emptyList(), Collections.emptyList()).isEmpty());
	}
}
