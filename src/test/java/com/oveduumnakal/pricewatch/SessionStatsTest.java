/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Verifies the price/quantity decomposition and baseline lifecycle of {@link SessionStats}. */
public class SessionStatsTest
{
	private Map<Integer, long[]> snap(long... idQtyPrice)
	{
		Map<Integer, long[]> m = new HashMap<>();
		for (int i = 0; i < idQtyPrice.length; i += 3)
			m.put((int) idQtyPrice[i], new long[]{idQtyPrice[i + 1], idQtyPrice[i + 2]});

		return m;
	}

	@Test
	public void noBaselineYieldsZero()
	{
		SessionStats stats = new SessionStats();
		assertFalse(stats.hasBaseline());

		SessionStats.Delta d = stats.delta(snap(560, 100, 95));
		assertEquals(0, d.getTotal());
	}

	@Test
	public void purePriceMovement()
	{
		SessionStats stats = new SessionStats();
		stats.reset(snap(560, 100, 95));
		assertTrue(stats.hasBaseline());

		SessionStats.Delta d = stats.delta(snap(560, 100, 100));
		assertEquals(500, d.getTotal());
		assertEquals(500, d.getPrice());
		assertEquals(0, d.getQuantity());
	}

	@Test
	public void pureQuantityMovement()
	{
		SessionStats stats = new SessionStats();
		stats.reset(snap(560, 100, 95));

		SessionStats.Delta d = stats.delta(snap(560, 150, 95));
		assertEquals(50 * 95, d.getTotal());
		assertEquals(0, d.getPrice());
		assertEquals(50 * 95, d.getQuantity());
	}

	@Test
	public void mixedComponentsSumToTotal()
	{
		SessionStats stats = new SessionStats();
		stats.reset(snap(560, 100, 95, 4151, 1, 2_000_000));

		SessionStats.Delta d = stats.delta(snap(560, 120, 100, 4151, 2, 2_100_000));
		assertEquals(d.getTotal(), d.getPrice() + d.getQuantity());
	}

	@Test
	public void droppedItemCountsAsQuantityLoss()
	{
		SessionStats stats = new SessionStats();
		stats.reset(snap(560, 100, 95));

		SessionStats.Delta d = stats.delta(new HashMap<>());
		assertEquals(-100 * 95, d.getTotal());
		assertEquals(0, d.getPrice());
		assertEquals(-100 * 95, d.getQuantity());
	}

	@Test
	public void clearDropsBaseline()
	{
		SessionStats stats = new SessionStats();
		stats.reset(snap(560, 100, 95));
		stats.clear();
		assertFalse(stats.hasBaseline());
	}

	@Test
	public void newlyTrackedOwnedItemContributesNothing()
	{
		SessionStats stats = new SessionStats();
		stats.reset(snap(560, 100, 95));

		Map<Integer, long[]> withNewItem = snap(560, 100, 95, 4151, 3, 2_000_000);
		stats.absorbNewItems(withNewItem);

		SessionStats.Delta d = stats.delta(withNewItem);
		assertEquals(0, d.getTotal());
		assertEquals(0, d.getPrice());
		assertEquals(0, d.getQuantity());
	}

	@Test
	public void absorbedItemStillRegistersLaterMoves()
	{
		SessionStats stats = new SessionStats();
		stats.reset(snap(560, 100, 95));
		stats.absorbNewItems(snap(560, 100, 95, 4151, 3, 2_000_000));

		SessionStats.Delta d = stats.delta(snap(560, 100, 95, 4151, 5, 2_100_000));
		assertEquals(3 * 100_000 + 2 * 2_100_000, d.getTotal());
		assertEquals(3 * 100_000, d.getPrice());
		assertEquals(2 * 2_100_000, d.getQuantity());
	}

	@Test
	public void itemTrackedAtZeroThenBoughtCountsThePurchase()
	{
		SessionStats stats = new SessionStats();
		stats.reset(snap(560, 100, 95));
		stats.absorbNewItems(snap(560, 100, 95, 4151, 0, 2_000_000));

		SessionStats.Delta d = stats.delta(snap(560, 100, 95, 4151, 4, 2_000_000));
		assertEquals(4 * 2_000_000, d.getTotal());
		assertEquals(0, d.getPrice());
		assertEquals(4 * 2_000_000, d.getQuantity());
	}

	@Test
	public void absorbNewItemsIsNoOpWithoutBaseline()
	{
		SessionStats stats = new SessionStats();
		stats.absorbNewItems(snap(560, 100, 95));
		assertFalse(stats.hasBaseline());
	}

	@Test
	public void untrackingAnItemIsSessionNeutral()
	{
		SessionStats stats = new SessionStats();
		stats.reset(snap(560, 100, 95, 4151, 1, 1_200_000_000));

		stats.removeItem(4151);

		SessionStats.Delta d = stats.delta(snap(560, 100, 95));
		assertEquals("untracking must not read as a loss", 0, d.getTotal());
		assertEquals(0, d.getQuantity());
	}

	@Test
	public void genuineLossOnStillTrackedItemsSurvivesARemoval()
	{
		SessionStats stats = new SessionStats();
		stats.reset(snap(560, 100, 95, 4151, 1, 1_200_000_000));

		stats.removeItem(4151);

		SessionStats.Delta d = stats.delta(snap(560, 60, 95));
		assertEquals("the runes actually lost still count", -40 * 95, d.getQuantity());
	}
}
