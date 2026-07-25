/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * The full state of one watched item: its identity, the latest wiki prices
 * (high/low/average) and their per-side deltas, per-window summary stats and
 * price history, and GE metadata (buy limit, store value, alch values).
 *
 * <p>Deliberately holds nothing about ownership. There is no quantity, no cost
 * basis and no acquisition history — this plugin reports what the market is
 * doing, never what you hold or paid.
 *
 * <p>Price-history {@code series*} lists are {@code transient}: they are fetched
 * at runtime and not persisted with the rest of the item. So are the buy-limit
 * fields, which the plugin sets from its own 4-hour window bookkeeping.
 */
@Data
public class WatchedItem
{
	private final int itemId;
	private final String name;

	private boolean tradeable = true;
	private boolean stackable;
	private boolean priceLoadFailed;

	private boolean favorite;
	private String category;
	private boolean onOverlay;

	private long highPrice;
	private long lowPrice;
	private long avgPrice;
	private transient boolean priceCacheHydrated;

	private long latestHighTime;
	private long latestLowTime;

	private int highDelta;
	private int lowDelta;
	private int avgDelta;
	private long prevHighPrice;
	private long prevLowPrice;
	private long prevAvgPrice;
	private boolean hasDeltas;

	/** Units bought toward the GE buy limit in the current 4-hour window (transient; set from the plugin). */
	private transient int limitBought;

	/** Epoch-second when the current GE buy-limit window resets, or 0 when none (transient). */
	private transient long limitResetEpoch;

	private WatchItemMode mode = WatchItemMode.WATCH;
	private Map<TimeWindow, PriceStats> windowStats = new EnumMap<>(TimeWindow.class);

	private transient List<WikiRealtimePriceClient.PricePoint> series5m = new ArrayList<>();
	private transient List<WikiRealtimePriceClient.PricePoint> series1h = new ArrayList<>();
	private transient List<WikiRealtimePriceClient.PricePoint> series6h = new ArrayList<>();
	private transient List<WikiRealtimePriceClient.PricePoint> series24h = new ArrayList<>();

	private int buyLimit;
	private long geValue;
	private long highAlch;
	private long lowAlch;
	private boolean metadataLoaded;

	/**
	 * Selects the price-history series whose sampling granularity best fits the
	 * given window: 1h points for a week, 6h for a month, 24h for quarter/half/year,
	 * and 5m points for anything shorter.
	 *
	 * @param window the time window being displayed
	 * @return the backing point list (live, not a copy)
	 */
	public List<WikiRealtimePriceClient.PricePoint> getSeriesFor(TimeWindow window)
	{
		switch (window)
		{
			case WEEK:
				return series1h;
			case MONTH:
				return series6h;
			case MONTH3:
			case MONTH6:
			case YEAR:
				return series24h;
			default:
				return series5m;
		}
	}

	/** @return whether any live price is known for this item. */
	public boolean hasPrices()
	{
		return highPrice > 0 || lowPrice > 0;
	}

	/** @return whether this item has prices from a live fetch rather than persisted cache hydration. */
	public boolean hasLivePrices()
	{
		return hasPrices() && !priceCacheHydrated;
	}
}
