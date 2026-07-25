/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Covers the rolling 4-hour buy-limit window: accumulation, rollover, and persistence round-trips. */
public class BuyLimitWindowsTest
{
	private static final long HOUR = 3600L;

	private static final long NOW = 1_700_000_000L;

	@Test
	public void anUntouchedItemHasNoWindow()
	{
		BuyLimitWindows windows = new BuyLimitWindows();

		assertEquals(0, windows.bought(4151, NOW));
		assertEquals(0, windows.resetEpoch(4151, NOW));
	}

	@Test
	public void purchasesInsideTheWindowAccumulate()
	{
		BuyLimitWindows windows = new BuyLimitWindows();

		windows.record(4151, 3, NOW);
		windows.record(4151, 5, NOW + HOUR);

		assertEquals(8, windows.bought(4151, NOW + 2 * HOUR));
	}

	@Test
	public void theWindowIsMeasuredFromTheFirstPurchaseNotTheLast()
	{
		BuyLimitWindows windows = new BuyLimitWindows();

		windows.record(4151, 3, NOW);
		windows.record(4151, 5, NOW + 3 * HOUR);

		assertEquals(NOW + 4 * HOUR, windows.resetEpoch(4151, NOW + 3 * HOUR));
	}

	@Test
	public void anExpiredWindowReadsAsAbsent()
	{
		BuyLimitWindows windows = new BuyLimitWindows();

		windows.record(4151, 3, NOW);

		assertEquals(0, windows.bought(4151, NOW + 4 * HOUR));
		assertEquals(0, windows.resetEpoch(4151, NOW + 4 * HOUR));
	}

	@Test
	public void buyingAfterExpiryOpensAFreshWindowRatherThanExtendingTheOld()
	{
		BuyLimitWindows windows = new BuyLimitWindows();

		windows.record(4151, 3, NOW);
		windows.record(4151, 5, NOW + 4 * HOUR);

		assertEquals(5, windows.bought(4151, NOW + 4 * HOUR));
		assertEquals(NOW + 8 * HOUR, windows.resetEpoch(4151, NOW + 4 * HOUR));
	}

	@Test
	public void itemsAreCountedIndependently()
	{
		BuyLimitWindows windows = new BuyLimitWindows();

		windows.record(4151, 3, NOW);
		windows.record(560, 900, NOW);

		assertEquals(3, windows.bought(4151, NOW));
		assertEquals(900, windows.bought(560, NOW));
	}

	@Test
	public void aSnapshotRoundTripsThroughRestore()
	{
		BuyLimitWindows windows = new BuyLimitWindows();

		windows.record(4151, 3, NOW);

		BuyLimitWindows restored = new BuyLimitWindows();

		restored.restore(windows.snapshot(), NOW + HOUR);

		assertEquals(3, restored.bought(4151, NOW + HOUR));
		assertEquals(NOW + 4 * HOUR, restored.resetEpoch(4151, NOW + HOUR));
	}

	@Test
	public void restoringDropsWindowsThatExpiredWhileLoggedOut()
	{
		BuyLimitWindows windows = new BuyLimitWindows();

		windows.record(4151, 3, NOW);

		BuyLimitWindows restored = new BuyLimitWindows();

		restored.restore(windows.snapshot(), NOW + 5 * HOUR);

		assertTrue(restored.snapshot().isEmpty());
	}

	@Test
	public void restoringNothingLeavesNoWindows()
	{
		BuyLimitWindows windows = new BuyLimitWindows();

		windows.record(4151, 3, NOW);
		windows.restore(null, NOW);

		assertTrue(windows.snapshot().isEmpty());
	}

	@Test
	public void malformedPersistedEntriesAreIgnoredRatherThanCrashing()
	{
		Map<Integer, long[]> saved = new HashMap<>();

		saved.put(4151, new long[]{NOW, 3});
		saved.put(560, new long[]{NOW});
		saved.put(561, null);

		BuyLimitWindows windows = new BuyLimitWindows();

		windows.restore(saved, NOW);

		assertEquals(1, windows.snapshot().size());
		assertEquals(3, windows.bought(4151, NOW));
	}

	@Test
	public void aSnapshotIsDeepEnoughThatLaterFillsDoNotMutateIt()
	{
		BuyLimitWindows windows = new BuyLimitWindows();

		windows.record(4151, 3, NOW);

		Map<Integer, long[]> snapshot = windows.snapshot();

		windows.record(4151, 5, NOW);
		windows.record(560, 5, NOW);

		assertEquals(1, snapshot.size());
		assertEquals(3, snapshot.get(4151)[1]);
	}
}
