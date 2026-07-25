/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link WikiRealtimePriceClient#computeStats} — the aggregation that
 * turns a raw time series into one window's high, low, volume-weighted average
 * and total volume, including the window cutoff and the volume gating that
 * decides which samples count toward each side.
 */
public class WikiRealtimePriceClientTest
{
	private static final long NOW = System.currentTimeMillis() / 1000L;

	/** @return an in-window price point with the given high/low prices and volumes. */
	private static WikiRealtimePriceClient.PricePoint point(long high, long low, long highVol, long lowVol)
	{
		return new WikiRealtimePriceClient.PricePoint(NOW - 60, high, low, highVol, lowVol);
	}

	/** @return a price point aged by the given duration. */
	private static WikiRealtimePriceClient.PricePoint aged(Duration age, long high, long low, long vol)
	{
		return new WikiRealtimePriceClient.PricePoint(NOW - age.getSeconds(), high, low, vol, vol);
	}

	@Test
	public void nullSeriesGivesZeroStats()
	{
		assertEquals(new PriceStats(0, 0, 0, 0), WikiRealtimePriceClient.computeStats(null, TimeWindow.H24));
	}

	@Test
	public void emptySeriesGivesZeroStats()
	{
		assertEquals(new PriceStats(0, 0, 0, 0),
				WikiRealtimePriceClient.computeStats(Collections.emptyList(), TimeWindow.H24));
	}

	@Test
	public void averagesEachSideAcrossSamplesAndSumsVolume()
	{
		PriceStats stats = WikiRealtimePriceClient.computeStats(
				Arrays.asList(point(100, 90, 5, 5), point(200, 110, 5, 5)), TimeWindow.H24);

		assertEquals(150, stats.getHigh());
		assertEquals(100, stats.getLow());
		assertEquals(20, stats.getVolume());
	}

	@Test
	public void ignoresSamplesOlderThanTheWindow()
	{
		PriceStats stats = WikiRealtimePriceClient.computeStats(
				Arrays.asList(aged(Duration.ofDays(3), 5000, 5000, 10), point(100, 100, 1, 1)), TimeWindow.H24);

		assertEquals(100, stats.getHigh());
		assertEquals(100, stats.getLow());
		assertEquals(2, stats.getVolume());
	}

	@Test
	public void zeroDurationWindowKeepsEverySample()
	{
		PriceStats stats = WikiRealtimePriceClient.computeStats(
				Arrays.asList(aged(Duration.ofDays(400), 300, 300, 1), point(100, 100, 1, 1)), TimeWindow.LIVE);

		assertEquals(200, stats.getHigh());
		assertEquals(4, stats.getVolume());
	}

	@Test
	public void averageIsWeightedTowardTheHeavierSide()
	{
		PriceStats stats = WikiRealtimePriceClient.computeStats(
				Collections.singletonList(point(200, 100, 9, 1)), TimeWindow.H24);

		assertEquals(200, stats.getHigh());
		assertEquals(100, stats.getLow());
		assertEquals(190, stats.getAvg());
	}

	@Test
	public void sideWithoutVolumeDoesNotCountTowardThatSide()
	{
		PriceStats stats = WikiRealtimePriceClient.computeStats(
				Collections.singletonList(point(200, 100, 5, 0)), TimeWindow.H24);

		assertEquals(200, stats.getHigh());
		assertEquals(0, stats.getLow());
		assertEquals(5, stats.getVolume());
	}

	/**
	 * A series with prices but no traded volume yields nothing on either side.
	 * Both the high and low averages gate on that side having volume, so when no
	 * sample has any the midpoint fallback for the average is reached with both
	 * sides already zero.
	 */
	@Test
	public void samplesWithoutAnyVolumeGiveZeroStats()
	{
		assertEquals(new PriceStats(0, 0, 0, 0), WikiRealtimePriceClient.computeStats(
				Collections.singletonList(point(200, 100, 0, 0)), TimeWindow.H24));
	}
}
