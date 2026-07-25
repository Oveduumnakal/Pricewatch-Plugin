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

/**
 * Tests for {@link WatchlistOrder}: name filtering, the favourites pin, and how
 * each {@link SortMode} orders the two blocks, including where items missing the
 * sort key land.
 */
public class WatchlistOrderTest
{
	/** @return a watched item with a name and an average price. */
	private static WatchedItem item(String name, long avgPrice)
	{
		WatchedItem item = new WatchedItem(name.hashCode(), name);

		item.setAvgPrice(avgPrice);

		return item;
	}

	/** @return a watched item marked as a favourite. */
	private static WatchedItem favourite(String name, long avgPrice)
	{
		WatchedItem item = item(name, avgPrice);

		item.setFavorite(true);

		return item;
	}

	/** @return the names of the arranged list, in order. */
	private static List<String> names(List<WatchedItem> items)
	{
		return items.stream()
				.map(WatchedItem::getName)
				.collect(Collectors.toList());
	}

	@Test
	public void manualModeKeepsTheGivenOrder()
	{
		List<WatchedItem> items = Arrays.asList(item("Zulrah", 1), item("Abyssal whip", 2));

		assertEquals(Arrays.asList("Zulrah", "Abyssal whip"),
				names(WatchlistOrder.arrange(items, SortMode.MANUAL, false, "")));
	}

	@Test
	public void favouritesArePinnedAboveEverythingEvenInManualMode()
	{
		List<WatchedItem> items = Arrays.asList(
				item("Zulrah", 1), favourite("Shark", 2), item("Abyssal whip", 3));

		assertEquals(Arrays.asList("Shark", "Zulrah", "Abyssal whip"),
				names(WatchlistOrder.arrange(items, SortMode.MANUAL, false, "")));
	}

	@Test
	public void sortingAppliesWithinTheFavouriteAndNormalBlocksSeparately()
	{
		List<WatchedItem> items = Arrays.asList(
				item("Zulrah", 1), favourite("Shark", 2), item("Abyssal whip", 3), favourite("Bones", 4));

		assertEquals(Arrays.asList("Bones", "Shark", "Abyssal whip", "Zulrah"),
				names(WatchlistOrder.arrange(items, SortMode.NAME, false, "")));
	}

	@Test
	public void nameSortsAscendingByDefaultAndDescendingReversed()
	{
		List<WatchedItem> items = Arrays.asList(item("Bones", 1), item("Abyssal whip", 2));

		assertEquals(Arrays.asList("Abyssal whip", "Bones"),
				names(WatchlistOrder.arrange(items, SortMode.NAME, false, "")));
		assertEquals(Arrays.asList("Bones", "Abyssal whip"),
				names(WatchlistOrder.arrange(items, SortMode.NAME, true, "")));
	}

	@Test
	public void priceSortsDescendingByDefault()
	{
		List<WatchedItem> items = Arrays.asList(item("Cheap", 10), item("Dear", 5000));

		assertEquals(Arrays.asList("Dear", "Cheap"),
				names(WatchlistOrder.arrange(items, SortMode.PRICE, false, "")));
	}

	@Test
	public void itemsWithoutTheSortKeySortLastInBothDirections()
	{
		List<WatchedItem> items = Arrays.asList(item("Unpriced", 0), item("Priced", 100));

		assertEquals(Arrays.asList("Priced", "Unpriced"),
				names(WatchlistOrder.arrange(items, SortMode.PRICE, false, "")));
		assertEquals(Arrays.asList("Priced", "Unpriced"),
				names(WatchlistOrder.arrange(items, SortMode.PRICE, true, "")));
	}

	@Test
	public void volumeReadsTheTwentyFourHourWindow()
	{
		WatchedItem quiet = item("Quiet", 100);
		WatchedItem busy = item("Busy", 100);

		quiet.getWindowStats().put(TimeWindow.H24, new PriceStats(0, 0, 100, 5));
		busy.getWindowStats().put(TimeWindow.H24, new PriceStats(0, 0, 100, 5000));

		assertEquals(Arrays.asList("Busy", "Quiet"),
				names(WatchlistOrder.arrange(Arrays.asList(quiet, busy), SortMode.VOLUME, false, "")));
	}

	@Test
	public void filterMatchesNameFragmentsIgnoringCase()
	{
		List<WatchedItem> items = Arrays.asList(item("Abyssal whip", 1), item("Dragon bones", 2));

		assertEquals(Collections.singletonList("Abyssal whip"),
				names(WatchlistOrder.arrange(items, SortMode.MANUAL, false, "ABYSS")));
		assertEquals(Collections.singletonList("Dragon bones"),
				names(WatchlistOrder.arrange(items, SortMode.MANUAL, false, "  bones  ")));
	}

	@Test
	public void blankFilterKeepsEverything()
	{
		List<WatchedItem> items = Arrays.asList(item("Abyssal whip", 1), item("Dragon bones", 2));

		assertEquals(2, WatchlistOrder.arrange(items, SortMode.MANUAL, false, "   ").size());
		assertEquals(2, WatchlistOrder.arrange(items, SortMode.MANUAL, false, null).size());
	}

	@Test
	public void filterAppliesBeforeTheFavouritePin()
	{
		List<WatchedItem> items = Arrays.asList(favourite("Shark", 1), item("Abyssal whip", 2));

		assertEquals(Collections.singletonList("Abyssal whip"),
				names(WatchlistOrder.arrange(items, SortMode.MANUAL, false, "whip")));
	}

	@Test
	public void arrangeDoesNotModifyTheInputList()
	{
		List<WatchedItem> items = Arrays.asList(item("Zulrah", 1), item("Abyssal whip", 2));

		WatchlistOrder.arrange(items, SortMode.NAME, false, "");

		assertEquals(Arrays.asList("Zulrah", "Abyssal whip"), names(items));
	}
}
