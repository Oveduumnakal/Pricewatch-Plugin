/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Verifies the session baseline snapshot only captures live, not cache-hydrated, prices. */
public class PricewatchPanelTest
{
	private TrackedItem item(int id, int quantity, long avgPrice, boolean live)
	{
		TrackedItem item = new TrackedItem(id, "Item " + id);
		item.setQuantity(quantity);
		item.setHighPrice(avgPrice);
		item.setLowPrice(avgPrice);
		item.setAvgPrice(avgPrice);
		item.setPriceCacheHydrated(!live);
		return item;
	}

	@Test
	public void cacheHydratedItemsAreExcludedFromTheBaseline()
	{
		Map<Integer, long[]> snapshot = PricewatchPanel.liveSessionSnapshot(
				Collections.singletonList(item(560, 100, 125, false)));

		assertTrue("a cache-hydrated item must not seed the session baseline", snapshot.isEmpty());
	}

	@Test
	public void livePricedItemsSeedTheBaseline()
	{
		Map<Integer, long[]> snapshot = PricewatchPanel.liveSessionSnapshot(
				Collections.singletonList(item(560, 100, 130, true)));

		assertEquals(1, snapshot.size());
		assertEquals(100, snapshot.get(560)[0]);
		assertEquals(130, snapshot.get(560)[1]);
	}

	@Test
	public void onlyLiveItemsSurviveInAMixedHolding()
	{
		Map<Integer, long[]> snapshot = PricewatchPanel.liveSessionSnapshot(Arrays.asList(
				item(560, 10, 100, true),
				item(4151, 1, 1_000, false)));

		assertEquals(1, snapshot.size());
		assertTrue("the live item is captured", snapshot.containsKey(560));
		assertFalse("the cache-hydrated item is skipped", snapshot.containsKey(4151));
	}
}
