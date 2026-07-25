/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests for {@link MarketClassifier}'s bucketing: volatility (coefficient of
 * variation), liquidity thresholds, 30-day range extraction, buy/sell volume
 * sums, and range-position labels, including window cutoffs and missing data.
 */
public class MarketClassifierTest
{
	private static final long NOW = System.currentTimeMillis() / 1000L;

	/** @return an in-window price point with the given high/low prices and volumes. */
	private static WikiRealtimePriceClient.PricePoint point(long high, long low, long highVol, long lowVol)
	{
		return new WikiRealtimePriceClient.PricePoint(NOW - 60, high, low, highVol, lowVol);
	}

	@Test
	public void volatilityNeedsAtLeastTwoSamples()
	{
		assertNull(MarketClassifier.volatility(Collections.emptyList()));
		assertNull(MarketClassifier.volatility(Collections.singletonList(point(100, 0, 0, 0))));
	}

	@Test
	public void volatilityIgnoresSamplesOlderThanAWeek()
	{
		WikiRealtimePriceClient.PricePoint stale =
				new WikiRealtimePriceClient.PricePoint(NOW - Duration.ofDays(8).getSeconds(), 1_000_000, 0, 0, 0);
		assertNull(MarketClassifier.volatility(Arrays.asList(stale, point(100, 0, 0, 0))));
	}

	@Test
	public void volatilityBuckets()
	{
		List<WikiRealtimePriceClient.PricePoint> flat = Arrays.asList(point(100, 0, 0, 0), point(100, 0, 0, 0));
		assertEquals("Low", MarketClassifier.volatility(flat));

		List<WikiRealtimePriceClient.PricePoint> mild = Arrays.asList(point(100, 0, 0, 0), point(105, 0, 0, 0));
		assertEquals("Medium", MarketClassifier.volatility(mild));

		List<WikiRealtimePriceClient.PricePoint> wild = Arrays.asList(point(100, 0, 0, 0), point(200, 0, 0, 0));
		assertEquals("High", MarketClassifier.volatility(wild));
	}

	@Test
	public void liquidityBuckets()
	{
		assertNull(MarketClassifier.liquidity(0));
		assertNull(MarketClassifier.liquidity(-1));
		assertEquals("Low", MarketClassifier.liquidity(499));
		assertEquals("Medium", MarketClassifier.liquidity(500));
		assertEquals("Medium", MarketClassifier.liquidity(5_000));
		assertEquals("High", MarketClassifier.liquidity(5_001));
	}

	@Test
	public void thirtyDayRangeFindsMinLowAndMaxHigh()
	{
		List<WikiRealtimePriceClient.PricePoint> series = Arrays.asList(
				point(120, 90, 0, 0),
				point(150, 80, 0, 0),
				point(110, 100, 0, 0));
		assertArrayEquals(new long[]{80, 150}, MarketClassifier.thirtyDayRange(series));
	}

	@Test
	public void thirtyDayRangeIgnoresMissingSidesAndStalePoints()
	{
		WikiRealtimePriceClient.PricePoint stale =
				new WikiRealtimePriceClient.PricePoint(NOW - Duration.ofDays(40).getSeconds(), 999_999, 1, 0, 0);
		List<WikiRealtimePriceClient.PricePoint> series = Arrays.asList(stale, point(150, 0, 0, 0), point(0, 80, 0, 0));
		assertArrayEquals(new long[]{80, 150}, MarketClassifier.thirtyDayRange(series));

		assertArrayEquals(new long[]{0, 0}, MarketClassifier.thirtyDayRange(Collections.emptyList()));
	}

	@Test
	public void buySellVolumeSumsInWindowPointsOnly()
	{
		WikiRealtimePriceClient.PricePoint stale =
				new WikiRealtimePriceClient.PricePoint(NOW - Duration.ofDays(2).getSeconds(), 0, 0, 500, 500);
		List<WikiRealtimePriceClient.PricePoint> series = Arrays.asList(stale, point(0, 0, 30, 20), point(0, 0, 10, 5));
		assertArrayEquals(new long[]{40, 25}, MarketClassifier.buySellVolume(series, Duration.ofDays(1)));
	}

	@Test
	public void rangePositionRejectsInvalidInput()
	{
		assertNull(MarketClassifier.rangePosition(100, 100, 50));
		assertNull(MarketClassifier.rangePosition(200, 100, 50));
		assertNull(MarketClassifier.rangePosition(0, 100, 0));
	}

	@Test
	public void rangePositionBuckets()
	{
		assertEquals("Highest", MarketClassifier.rangePosition(0, 1_000, 990));
		assertEquals("High", MarketClassifier.rangePosition(0, 1_000, 800));
		assertEquals("High Avg", MarketClassifier.rangePosition(0, 1_000, 650));
		assertEquals("Average", MarketClassifier.rangePosition(0, 1_000, 500));
		assertEquals("Low Avg", MarketClassifier.rangePosition(0, 1_000, 300));
		assertEquals("Low", MarketClassifier.rangePosition(0, 1_000, 100));
		assertEquals("Lowest", MarketClassifier.rangePosition(0, 1_000, 10));
	}

	@Test
	public void rangePositionClampsOutOfRangeLivePrices()
	{
		assertEquals("Highest", MarketClassifier.rangePosition(100, 1_000, 5_000));
	}
}
