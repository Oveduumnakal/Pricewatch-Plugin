/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-item rolling 4-hour Grand Exchange buy-limit windows: how many units of an item
 * have been bought since the window opened, and when it rolls over.
 *
 * <p>A window opens on the first purchase of an item and lasts {@link #WINDOW} from that
 * moment. Purchases inside it accumulate; the first purchase at or after its end opens a
 * fresh window rather than extending the old one, which is how the game's limit behaves.
 * Expired windows are read as absent, so a stale entry never inflates a count.
 *
 * <p>Pure and client-free — every method takes the current epoch-second rather than
 * reading the clock — so the rollover behaviour is unit-testable.
 */
class BuyLimitWindows
{
	/** How long a buy-limit window lasts from the purchase that opened it. */
	static final Duration WINDOW = Duration.ofHours(4);

	/** Item id to {@code {windowStartEpoch, quantityBought}}; the shape persisted to config. */
	private final Map<Integer, long[]> windows = new HashMap<>();

	/**
	 * Accumulates a purchase into the item's window, opening a new one when there is no
	 * live window to add to.
	 *
	 * @param itemId the item bought
	 * @param quantity how many units were bought
	 * @param now the current epoch-second
	 */
	void record(int itemId, int quantity, long now)
	{
		long[] window = live(itemId, now);
		if (window == null)
		{
			windows.put(itemId, new long[]{now, quantity});
			return;
		}

		window[1] += quantity;
	}

	/**
	 * @param itemId the item to read
	 * @param now the current epoch-second
	 * @return units bought in the item's current window, or 0 when none is open
	 */
	int bought(int itemId, long now)
	{
		long[] window = live(itemId, now);

		return window == null ? 0 : (int) window[1];
	}

	/**
	 * @param itemId the item to read
	 * @param now the current epoch-second
	 * @return the epoch-second the item's window resets, or 0 when none is open
	 */
	long resetEpoch(int itemId, long now)
	{
		long[] window = live(itemId, now);

		return window == null ? 0 : window[0] + WINDOW.getSeconds();
	}

	/**
	 * @return the windows in their persisted shape — a deep copy, so a fill landing
	 *         while the snapshot is being serialized cannot alter it mid-write
	 */
	Map<Integer, long[]> snapshot()
	{
		Map<Integer, long[]> copy = new HashMap<>();

		windows.forEach((itemId, window) -> copy.put(itemId, window.clone()));

		return copy;
	}

	/**
	 * Replaces every window with those restored from config, dropping any that already
	 * expired so a long logout does not resurrect a spent window.
	 *
	 * @param restored the persisted windows, or {@code null} when nothing was stored
	 * @param now the current epoch-second
	 */
	void restore(Map<Integer, long[]> restored, long now)
	{
		windows.clear();
		if (restored == null)
			return;

		restored.entrySet().stream()
				.filter(e -> e.getValue() != null && e.getValue().length == 2)
				.filter(e -> !expired(e.getValue(), now))
				.forEach(e -> windows.put(e.getKey(), e.getValue()));
	}

	/** Drops every window. */
	void clear()
	{
		windows.clear();
	}

	/** @return the item's window when one is open, or {@code null} when absent or expired. */
	private long[] live(int itemId, long now)
	{
		long[] window = windows.get(itemId);
		if (window == null || expired(window, now))
			return null;

		return window;
	}

	/** @return whether the window's 4 hours have elapsed by {@code now}. */
	private static boolean expired(long[] window, long now)
	{
		return now >= window[0] + WINDOW.getSeconds();
	}
}
