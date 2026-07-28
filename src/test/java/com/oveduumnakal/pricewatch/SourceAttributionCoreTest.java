/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link SourceAttributionCore}: claim matching (full and partial),
 * per-item isolation, oldest-first consumption, tick expiry, and the
 * {@link AcquisitionSource#UNKNOWN} fallback.
 */
public class SourceAttributionCoreTest
{
	private final SourceAttributionCore core = new SourceAttributionCore();

	@Test
	public void unmatchedDeltaIsUnknown()
	{
		SourceAttributionCore.Attribution a = core.attribute(560, 100, 10);
		assertEquals(AcquisitionSource.UNKNOWN, a.source());
		assertEquals(42, a.unitPriceOr(42));
	}

	@Test
	public void claimMatchesAndCarriesItsPrice()
	{
		core.claim(AcquisitionSource.GE_TRADE, 560, 100, 95, 10);

		SourceAttributionCore.Attribution a = core.attribute(560, 100, 10);
		assertEquals(AcquisitionSource.GE_TRADE, a.source());
		assertEquals(95, a.unitPriceOr(0));

		assertEquals(AcquisitionSource.UNKNOWN, core.attribute(560, 100, 10).source());
	}

	@Test
	public void largerClaimSurvivesPartialConsumption()
	{
		core.claim(AcquisitionSource.SHOP, 560, 100, 5, 10);

		assertEquals(AcquisitionSource.SHOP, core.attribute(560, 60, 10).source());
		assertEquals(AcquisitionSource.SHOP, core.attribute(560, 40, 10).source());
		assertEquals(AcquisitionSource.UNKNOWN, core.attribute(560, 1, 10).source());
	}

	@Test
	public void claimsAreIsolatedPerItem()
	{
		core.claim(AcquisitionSource.GROUND, 560, 10, 0, 10);

		assertEquals(AcquisitionSource.UNKNOWN, core.attribute(4151, 10, 10).source());
		assertEquals(AcquisitionSource.GROUND, core.attribute(560, 10, 10).source());
	}

	@Test
	public void oldestClaimIsConsumedFirst()
	{
		core.claim(AcquisitionSource.GE_TRADE, 560, 10, 95, 10);
		core.claim(AcquisitionSource.SHOP, 560, 10, 80, 10);

		assertEquals(95, core.attribute(560, 10, 10).unitPriceOr(0));
		assertEquals(80, core.attribute(560, 10, 10).unitPriceOr(0));
	}

	@Test
	public void expiredClaimsNeverMatch()
	{
		core.claim(AcquisitionSource.ALCHEMY, 560, 10, 60, 10);

		int afterTtl = 10 + SourceAttributionCore.CLAIM_TTL_TICKS + 1;
		assertEquals(AcquisitionSource.UNKNOWN, core.attribute(560, 10, afterTtl).source());
	}

	@Test
	public void expireDiscardsOnlyStaleClaims()
	{
		core.claim(AcquisitionSource.GE_TRADE, 560, 10, 95, 10);
		core.claim(AcquisitionSource.SHOP, 4151, 10, 80, 20);

		core.expire(10 + SourceAttributionCore.CLAIM_TTL_TICKS + 1);

		assertEquals(AcquisitionSource.UNKNOWN, core.attribute(560, 10, 15).source());
		assertEquals(AcquisitionSource.SHOP, core.attribute(4151, 10, 21).source());
	}

	@Test
	public void clearDropsEverything()
	{
		core.claim(AcquisitionSource.GE_TRADE, 560, 10, 95, 10);
		core.clear();

		assertEquals(AcquisitionSource.UNKNOWN, core.attribute(560, 10, 10).source());
	}
}
