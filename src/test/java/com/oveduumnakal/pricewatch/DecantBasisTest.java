/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Verifies the dose-weighted, loss-free basis split of {@link DecantBasis}. */
public class DecantBasisTest
{
	/** Potion(3)@100 + Potion(1)@50 decanted into one Potion(4): the whole 150gp follows the liquid. */
	@Test
	public void mergesInputCostOntoASingleOutput()
	{
		Map<Integer, Long> shares = DecantBasis.distribute(150, List.of(new int[]{4, 4}));
		assertEquals(Long.valueOf(150), shares.get(4));
	}

	/** Five doses @ 50gp up-decanted into a Potion(4) + a Potion(1): a 4:1 split. */
	@Test
	public void splitsBasisDoseWeightedAcrossOutputs()
	{
		Map<Integer, Long> shares = DecantBasis.distribute(50, List.of(new int[]{4, 4}, new int[]{1, 1}));
		assertEquals(Long.valueOf(40), shares.get(4));
		assertEquals(Long.valueOf(10), shares.get(1));
	}

	/** The flooring leftover (100*4/7=57 → +1) goes to the highest-dose output and the total is conserved. */
	@Test
	public void flooringRemainderGoesToTheHighestDoseOutputAndConservesTheTotal()
	{
		List<int[]> outputs = List.of(new int[]{4, 4}, new int[]{1, 1}, new int[]{2, 2});
		Map<Integer, Long> shares = DecantBasis.distribute(100, outputs);

		long sum = 0;
		for (Long share : shares.values())
			sum += share;

		assertEquals(100, sum);
		assertEquals(Long.valueOf(58), shares.get(4));
		assertEquals(Long.valueOf(14), shares.get(1));
		assertEquals(Long.valueOf(28), shares.get(2));
	}

	/**
	 * Consume-down (#218): drinking one dose of a Potion(4)@100 leaves a Potion(3) — a dose is lost
	 * but the full 100gp basis follows onto the three-dose lot, since using a dose realizes no P/L.
	 */
	@Test
	public void consumingADoseCarriesTheFullBasisOntoTheLowerDose()
	{
		Map<Integer, Long> shares = DecantBasis.distribute(100, List.of(new int[]{3, 3}));
		assertEquals(Long.valueOf(100), shares.get(3));
	}

	@Test
	public void zeroBasisStaysZero()
	{
		Map<Integer, Long> shares = DecantBasis.distribute(0, List.of(new int[]{4, 4}, new int[]{1, 1}));
		assertEquals(Long.valueOf(0), shares.get(4));
		assertEquals(Long.valueOf(0), shares.get(1));
	}
}
