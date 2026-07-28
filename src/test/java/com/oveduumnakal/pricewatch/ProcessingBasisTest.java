/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Verifies the FIFO open-lot cost walk of {@link ProcessingBasis}. */
public class ProcessingBasisTest
{
	@Test
	public void walksOpenLotsOldestFirst()
	{
		List<AcquisitionRecord> records = List.of(
				new AcquisitionRecord(2, 100, null),
				new AcquisitionRecord(2, 200, null));
		assertEquals(400, ProcessingBasis.openLotCost(records, 3));
	}

	@Test
	public void skipsSoldLots()
	{
		List<AcquisitionRecord> records = List.of(
				new AcquisitionRecord(5, 100, 150L),
				new AcquisitionRecord(2, 200, null));
		assertEquals(400, ProcessingBasis.openLotCost(records, 2));
	}

	@Test
	public void unitsBeyondOpenLotsContributeNothing()
	{
		List<AcquisitionRecord> records = List.of(new AcquisitionRecord(1, 500, null));
		assertEquals(500, ProcessingBasis.openLotCost(records, 10));
	}

	@Test
	public void emptyLogCostsZero()
	{
		assertEquals(0, ProcessingBasis.openLotCost(List.of(), 5));
	}
}
