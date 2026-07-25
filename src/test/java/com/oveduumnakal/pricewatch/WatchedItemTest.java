/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.Collections;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link WatchedItem}: which history series each {@link TimeWindow}
 * reads from, and how the price-presence flags distinguish a live fetch from
 * values restored out of the persisted cache.
 */
public class WatchedItemTest
{
	/** @return a watched item with no prices yet. */
	private static WatchedItem item()
	{
		return new WatchedItem(4151, "Abyssal whip");
	}

	@Test
	public void shortWindowsReadTheFiveMinuteSeries()
	{
		WatchedItem item = item();

		assertSame(item.getSeries5m(), item.getSeriesFor(TimeWindow.LIVE));
		assertSame(item.getSeries5m(), item.getSeriesFor(TimeWindow.M5));
		assertSame(item.getSeries5m(), item.getSeriesFor(TimeWindow.H24));
	}

	@Test
	public void longerWindowsReadCoarserSeries()
	{
		WatchedItem item = item();

		assertSame(item.getSeries1h(), item.getSeriesFor(TimeWindow.WEEK));
		assertSame(item.getSeries6h(), item.getSeriesFor(TimeWindow.MONTH));
		assertSame(item.getSeries24h(), item.getSeriesFor(TimeWindow.MONTH3));
		assertSame(item.getSeries24h(), item.getSeriesFor(TimeWindow.MONTH6));
		assertSame(item.getSeries24h(), item.getSeriesFor(TimeWindow.YEAR));
	}

	@Test
	public void seriesForNeverReturnsNull()
	{
		WatchedItem item = item();

		for (TimeWindow window : TimeWindow.values())
			assertEquals(Collections.emptyList(), item.getSeriesFor(window));
	}

	@Test
	public void hasPricesNeedsOneSideOnly()
	{
		WatchedItem item = item();

		assertFalse(item.hasPrices());

		item.setLowPrice(100);
		assertTrue(item.hasPrices());

		item.setLowPrice(0);
		item.setHighPrice(100);
		assertTrue(item.hasPrices());
	}

	@Test
	public void hydratedPricesAreNotLivePrices()
	{
		WatchedItem item = item();

		item.setHighPrice(100);
		item.setPriceCacheHydrated(true);

		assertTrue(item.hasPrices());
		assertFalse(item.hasLivePrices());

		item.setPriceCacheHydrated(false);
		assertTrue(item.hasLivePrices());
	}

	@Test
	public void defaultsToAWatchlistEntry()
	{
		assertEquals(WatchItemMode.WATCH, item().getMode());
		assertTrue(item().isTradeable());
	}
}
