/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Verifies the invested cost basis and running value building blocks used by the portfolio chart. */
public class TrackedItemTest
{
	private TrackedItem itemWith(int qty, long avg, AcquisitionRecord... lots)
	{
		TrackedItem t = new TrackedItem(560, "Nature rune");
		t.setQuantity(qty);
		t.setAvgPrice(avg);
		t.setAcquisitions(Arrays.asList(lots));
		return t;
	}

	@Test
	public void heldLotsMarkToMarket()
	{
		TrackedItem t = itemWith(100, 125, new AcquisitionRecord(100, 100, null));
		assertEquals(10_000, t.getInvestedCostBasis());
		assertEquals(0, t.getRealizedProceeds());
		assertEquals("100 held @125", 12_500, t.getAvgValue() + t.getRealizedProceeds());
	}

	@Test
	public void sellingAtALossKeepsCostBasisButDropsValue()
	{
		TrackedItem t = itemWith(50, 125,
				new AcquisitionRecord(50, 100, 50L),
				new AcquisitionRecord(50, 100, null));

		assertEquals("invested cost basis stays fixed across the sale", 10_000, t.getInvestedCostBasis());
		assertEquals(2_500, t.getRealizedProceeds());

		long value = t.getAvgValue() + t.getRealizedProceeds();
		assertEquals("50 held @125 + 50 sold @50", 8_750, value);
		assertEquals("value minus invested cost equals total P/L",
				t.getProfitAt(125), value - t.getInvestedCostBasis());
	}

	@Test
	public void totalSuspendedSumsEverySource()
	{
		TrackedItem t = itemWith(40, 125, new AcquisitionRecord(100, 100, null));
		t.setSuspendedQuantity(25);
		t.setTradeSuspendedQuantity(15);
		t.setGroundSuspendedQuantity(12);
		t.setDeathSuspendedQuantity(8);

		assertEquals(60, t.getTotalSuspendedQuantity());
		assertEquals("open lots cover held plus suspended units",
				t.getRecordQuantitySum(), t.getQuantity() + t.getTotalSuspendedQuantity());
	}

	@Test
	public void suspendingUnitsIsValueNeutral()
	{
		TrackedItem t = itemWith(100, 125, new AcquisitionRecord(100, 100, null));
		long before = t.getAvgValue() + t.getSuspendedValue() + t.getRealizedProceeds();

		t.setQuantity(40);
		t.setSuspendedQuantity(60);
		long during = t.getAvgValue() + t.getSuspendedValue() + t.getRealizedProceeds();

		assertEquals(7_500, t.getSuspendedValue());
		assertEquals("moving units into an offer must not dent the running value", before, during);
	}

	@Test
	public void hydratedPricesAreNotLiveSessionPrices()
	{
		TrackedItem t = itemWith(100, 125, new AcquisitionRecord(100, 100, null));
		t.setLowPrice(120);
		t.setHighPrice(130);
		t.setPriceCacheHydrated(true);

		assertEquals(true, t.hasPrices());
		assertEquals(false, t.hasLivePrices());

		t.setPriceCacheHydrated(false);

		assertEquals(true, t.hasLivePrices());
	}
}
