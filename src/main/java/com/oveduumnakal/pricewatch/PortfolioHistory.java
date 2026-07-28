/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Per-item time series of portfolio value and cost basis for the "portfolio value
 * over time" chart. Each tracked item keeps its own thinned series of
 * {@code {epochSeconds, value, costBasis}} points; the chart line is the sum across
 * the stored items at each timestamp ({@link #aggregate()}).
 *
 * <p>Keeping the data per item (rather than one aggregate series) means removing an
 * item drops exactly its contribution from every past point, and an item added
 * mid-history only affects points from when it was first recorded — the aggregate is
 * always consistent with the set of items currently stored.
 *
 * <p>Config size is bounded per item: recent history is kept at hourly resolution for
 * the last {@value #HOURLY_HOURS} hours (≈ 7 days) and older history is collapsed to
 * one point per {@value #BUCKET_HOURS}-hour bucket, up to {@value #RETENTION_DAYS}
 * days; anything older is dropped. Points are {@code long[]} so persistence is a plain
 * map of primitive lists with no schema shape to guard.
 */
public final class PortfolioHistory
{
	/** Hours of recent history kept at one-point-per-hour resolution (7 days). */
	static final int HOURLY_HOURS = 168;

	/** Bucket width, in hours, that history older than the hourly window is thinned to. */
	static final int BUCKET_HOURS = 6;

	/** Days of history retained before points are dropped. */
	static final int RETENTION_DAYS = 45;

	private static final long HOUR = 3600L;

	private static final long DAY = 86_400L;

	/** itemId → that item's thinned series of {@code {epochSeconds, value, costBasis}}. */
	private final Map<Integer, List<long[]>> series = new LinkedHashMap<>();

	/** Replaces all series with {@code stored} (as loaded from config); ignores malformed entries. */
	public void load(Map<Integer, List<long[]>> stored)
	{
		series.clear();
		if (stored == null)
			return;

		stored.forEach((id, points) ->
		{
			if (id == null || points == null)
				return;

			List<long[]> clean = new ArrayList<>();
			points.stream()
					.filter(p -> p != null && p.length >= 3)
					.forEach(p -> clean.add(new long[]{p[0], p[1], p[2]}));
			clean.sort((a, b) -> Long.compare(a[0], b[0]));
			if (!clean.isEmpty())
				series.put(id, clean);
		});
	}

	/**
	 * Records a snapshot at {@code epochSeconds} for each item in {@code perItem}
	 * (id → {@code {value, costBasis}}). Within the same hour as an item's latest point
	 * the point is updated in place (so a burst of refreshes yields one hourly point);
	 * otherwise a new point is appended. Each touched series is then thinned.
	 */
	public void record(long epochSeconds, Map<Integer, long[]> perItem)
	{
		perItem.forEach((id, valueCost) ->
		{
			List<long[]> points = series.computeIfAbsent(id, k -> new ArrayList<>());
			if (!points.isEmpty())
			{
				long[] last = points.get(points.size() - 1);
				if (last[0] / HOUR == epochSeconds / HOUR)
				{
					last[0] = epochSeconds;
					last[1] = valueCost[0];
					last[2] = valueCost[1];
					thin(points, epochSeconds);
					return;
				}
			}

			points.add(new long[]{epochSeconds, valueCost[0], valueCost[1]});
			thin(points, epochSeconds);
		});
	}

	/** Drops the series for {@code itemId} (e.g. when it is untracked) so it leaves the aggregate. */
	public void removeItem(int itemId)
	{
		series.remove(itemId);
	}

	/**
	 * Collapses points older than the hourly window to one per {@value #BUCKET_HOURS}-hour
	 * bucket (keeping the latest in each bucket) and drops points beyond the retention window.
	 */
	private void thin(List<long[]> points, long nowSeconds)
	{
		long dropBefore = nowSeconds - RETENTION_DAYS * DAY;
		long hourlyCutoff = nowSeconds - HOURLY_HOURS * HOUR;
		long bucket = BUCKET_HOURS * HOUR;

		Map<Long, long[]> bucketKept = new LinkedHashMap<>();
		List<long[]> recent = new ArrayList<>();

		for (long[] p : points)
		{
			if (p[0] < dropBefore)
				continue;

			if (p[0] < hourlyCutoff)
				bucketKept.put(p[0] / bucket, p);
			else
				recent.add(p);
		}

		List<long[]> merged = new ArrayList<>(bucketKept.values());
		merged.addAll(recent);
		merged.sort((a, b) -> Long.compare(a[0], b[0]));

		points.clear();
		points.addAll(merged);
	}

	/**
	 * @return the aggregate chart series {@code {epochSeconds, totalValue, totalCostBasis}}:
	 *         at each timestamp any stored item was recorded, the summed value and cost of
	 *         the items recorded there. Chronological order.
	 */
	public List<long[]> aggregate()
	{
		Map<Long, long[]> byEpoch = new TreeMap<>();
		for (List<long[]> points : series.values())
			for (long[] p : points)
			{
				long[] agg = byEpoch.computeIfAbsent(p[0], k -> new long[]{k, 0, 0});
				agg[1] += p[1];
				agg[2] += p[2];
			}

		return new ArrayList<>(byEpoch.values());
	}

	/** @return the per-item series for persistence (defensive copy). */
	public Map<Integer, List<long[]>> seriesByItem()
	{
		Map<Integer, List<long[]>> copy = new LinkedHashMap<>();
		series.forEach((id, points) ->
		{
			List<long[]> pointsCopy = new ArrayList<>(points.size());
			points.forEach(p -> pointsCopy.add(new long[]{p[0], p[1], p[2]}));
			copy.put(id, pointsCopy);
		});

		return copy;
	}

	/** @return whether any item has stored points. */
	public boolean isEmpty()
	{
		return series.isEmpty();
	}

	/** Drops all stored series, e.g. when the tracked list is emptied. */
	public void clear()
	{
		series.clear();
	}
}
