/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies per-item recording/thinning and the cross-item aggregation of
 * {@link PortfolioHistory} — including that removing an item drops its contribution.
 */
public class PortfolioHistoryTest
{
	private static final long HOUR = 3600L;

	private static final long DAY = 86_400L;

	private static Map<Integer, long[]> item(int id, long value, long cost)
	{
		Map<Integer, long[]> snapshot = new HashMap<>();
		snapshot.put(id, new long[]{value, cost});
		return snapshot;
	}

	@Test
	public void samplesInOneHourCollapseToOnePoint()
	{
		PortfolioHistory history = new PortfolioHistory();
		long base = 300 * DAY;
		history.record(base, item(560, 100, 50));
		history.record(base + 600, item(560, 110, 50));
		history.record(base + 1200, item(560, 120, 50));

		List<long[]> agg = history.aggregate();
		assertEquals(1, agg.size());
		assertEquals(120, agg.get(0)[1]);
	}

	@Test
	public void distinctHoursAppendPoints()
	{
		PortfolioHistory history = new PortfolioHistory();
		long base = 10 * DAY;
		history.record(base, item(560, 100, 0));
		history.record(base + HOUR, item(560, 200, 0));
		history.record(base + 2 * HOUR, item(560, 300, 0));
		assertEquals(3, history.aggregate().size());
	}

	@Test
	public void oldPointsCollapseToBuckets()
	{
		PortfolioHistory history = new PortfolioHistory();
		long start = 100 * DAY;
		int hours = 10 * 24;

		for (int hr = 0; hr < hours; hr++)
			history.record(start + hr * HOUR, item(560, hr, 0));

		long now = start + (hours - 1) * HOUR;
		long hourlyCutoff = now - PortfolioHistory.HOURLY_HOURS * HOUR;
		long bucket = PortfolioHistory.BUCKET_HOURS * HOUR;

		List<long[]> agg = history.aggregate();
		List<long[]> old = agg.stream()
				.filter(p -> p[0] < hourlyCutoff)
				.collect(Collectors.toList());
		long recentCount = agg.stream().filter(p -> p[0] >= hourlyCutoff).count();

		int oldHours = (int) (hourlyCutoff - start) / (int) HOUR;
		long distinctBuckets = old.stream().map(p -> p[0] / bucket).distinct().count();
		assertEquals("no two old points share a bucket", distinctBuckets, old.size());
		assertTrue("old history is thinned below hourly", old.size() < oldHours);
		assertTrue("recent week stays hourly", recentCount >= 160);
	}

	@Test
	public void pointsBeyondRetentionWindowAreDropped()
	{
		PortfolioHistory history = new PortfolioHistory();
		history.record(0, item(560, 100, 0));
		history.record((PortfolioHistory.RETENTION_DAYS + 5) * DAY, item(560, 200, 0));

		List<long[]> agg = history.aggregate();
		assertEquals(1, agg.size());
		assertEquals(200, agg.get(0)[1]);
	}

	@Test
	public void aggregateSumsAcrossItems()
	{
		PortfolioHistory history = new PortfolioHistory();
		long t = 10 * DAY;
		Map<Integer, long[]> snapshot = new HashMap<>();
		snapshot.put(560, new long[]{100, 40});
		snapshot.put(561, new long[]{300, 90});
		history.record(t, snapshot);

		List<long[]> agg = history.aggregate();
		assertEquals(1, agg.size());
		assertEquals("summed value", 400, agg.get(0)[1]);
		assertEquals("summed cost", 130, agg.get(0)[2]);
	}

	@Test
	public void removingAnItemDropsItsContribution()
	{
		PortfolioHistory history = new PortfolioHistory();
		long t = 10 * DAY;
		Map<Integer, long[]> snapshot = new HashMap<>();
		snapshot.put(560, new long[]{100, 40});
		snapshot.put(561, new long[]{300, 90});
		history.record(t, snapshot);

		history.removeItem(561);

		List<long[]> agg = history.aggregate();
		assertEquals(1, agg.size());
		assertEquals("only the remaining item's value", 100, agg.get(0)[1]);
		assertEquals(40, agg.get(0)[2]);
	}

	@Test
	public void itemAddedLaterOnlyAffectsLaterPoints()
	{
		PortfolioHistory history = new PortfolioHistory();
		long t1 = 10 * DAY;
		long t2 = t1 + HOUR;
		history.record(t1, item(560, 100, 40));

		Map<Integer, long[]> both = new HashMap<>();
		both.put(560, new long[]{100, 40});
		both.put(561, new long[]{300, 90});
		history.record(t2, both);

		List<long[]> agg = history.aggregate();
		assertEquals(2, agg.size());
		assertEquals("earlier point has only the first item", 100, agg.get(0)[1]);
		assertEquals("later point has both", 400, agg.get(1)[1]);
	}

	@Test
	public void loadRestoresSortedPerItemHistory()
	{
		PortfolioHistory history = new PortfolioHistory();
		Map<Integer, List<long[]>> stored = new HashMap<>();
		stored.put(560, Arrays.asList(new long[]{200, 20, 10}, new long[]{100, 15, 8}));
		history.load(stored);

		List<long[]> agg = history.aggregate();
		assertEquals(100, agg.get(0)[0]);
		assertEquals(200, agg.get(1)[0]);
	}
}
