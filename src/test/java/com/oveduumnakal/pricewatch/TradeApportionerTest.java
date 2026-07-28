/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Verifies the gp-splitting rules of {@link TradeApportioner}. */
public class TradeApportionerTest
{
	@Test
	public void singleItemTakesAllTheGp()
	{
		Map<Integer, Long> prices = TradeApportioner.apportion(
				List.of(new TradeApportioner.Leg(4151, 1, 1_500_000)), 10_000_000);
		assertEquals(Long.valueOf(10_000_000), prices.get(4151));
	}

	@Test
	public void gpSplitsProportionallyToMarketValue()
	{
		Map<Integer, Long> prices = TradeApportioner.apportion(
				List.of(
						new TradeApportioner.Leg(1, 1, 3_000_000),
						new TradeApportioner.Leg(2, 1, 1_000_000)),
				8_000_000);
		assertEquals(Long.valueOf(6_000_000), prices.get(1));
		assertEquals(Long.valueOf(2_000_000), prices.get(2));
	}

	@Test
	public void quantityWeighsIntoTheSplit()
	{
		Map<Integer, Long> prices = TradeApportioner.apportion(
				List.of(
						new TradeApportioner.Leg(1, 3, 1_000_000),
						new TradeApportioner.Leg(2, 1, 1_000_000)),
				4_000_000);
		assertEquals(Long.valueOf(1_000_000), prices.get(1));
		assertEquals(Long.valueOf(1_000_000), prices.get(2));
	}

	@Test
	public void itemForItemPricesAtZero()
	{
		Map<Integer, Long> prices = TradeApportioner.apportion(
				List.of(new TradeApportioner.Leg(1, 1, 1_500_000)), 0);
		assertEquals(Long.valueOf(0), prices.get(1));
	}

	@Test
	public void unvaluedLegsSplitEvenlyPerUnit()
	{
		Map<Integer, Long> prices = TradeApportioner.apportion(
				List.of(
						new TradeApportioner.Leg(1, 3, 0),
						new TradeApportioner.Leg(2, 1, 0)),
				4_000);
		assertEquals(Long.valueOf(1_000), prices.get(1));
		assertEquals(Long.valueOf(1_000), prices.get(2));
	}

	/**
	 * Max-cash gp (~2.147e9) against a ~4.8e9 leg value (three 1.6b items) overflows a
	 * {@code long} and wraps to a negative per-unit price in raw math; the double multiply
	 * keeps it positive. One leg takes all the gp, so per-unit is {@code gp / 3}.
	 */
	@Test
	public void maxCashAgainstMultiBillionLegDoesNotOverflow()
	{
		long gp = 2_147_000_000L;
		Map<Integer, Long> prices = TradeApportioner.apportion(
				List.of(new TradeApportioner.Leg(1, 3, 1_600_000_000L)), gp);
		assertEquals(Long.valueOf(gp / 3), prices.get(1));
		assertTrue(prices.get(1) > 0);
	}

	/** Splitting max-cash gp across two multi-billion legs stays positive and, being equal-valued, even. */
	@Test
	public void maxCashSplitsAcrossMultiBillionLegsStayPositive()
	{
		long gp = 2_147_000_000L;
		Map<Integer, Long> prices = TradeApportioner.apportion(
				List.of(
						new TradeApportioner.Leg(1, 1, 2_000_000_000L),
						new TradeApportioner.Leg(2, 1, 2_000_000_000L)),
				gp);
		assertTrue(prices.get(1) > 0);
		assertTrue(prices.get(2) > 0);
		assertEquals(prices.get(1), prices.get(2));
	}

	@Test
	public void emptyLegsYieldNothing()
	{
		assertTrue(TradeApportioner.apportion(List.of(), 5_000).isEmpty());
	}
}
