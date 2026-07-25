/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link MarketFigures}: the Grand Exchange sell tax and its two
 * boundaries, how trade ages are worded, when a timestamp reads as stale, and
 * the change between two prices.
 */
public class MarketFiguresTest
{
	private static final long NOW = 1_700_000_000L;

	@Test
	public void itemsUnderFiftyGpAreTaxExempt()
	{
		assertEquals(0, MarketFigures.geTax(0));
		assertEquals(0, MarketFigures.geTax(49));
	}

	@Test
	public void theTaxStartsAtFiftyGp()
	{
		assertEquals(1, MarketFigures.geTax(50));
	}

	@Test
	public void theTaxIsTwoPercentRoundedDown()
	{
		assertEquals(20, MarketFigures.geTax(1000));
		assertEquals(2, MarketFigures.geTax(149));
	}

	@Test
	public void theTaxIsCappedAtFiveMillion()
	{
		assertEquals(5_000_000L, MarketFigures.geTax(250_000_000L));
		assertEquals(5_000_000L, MarketFigures.geTax(Integer.MAX_VALUE));
	}

	@Test
	public void anUnknownTradeTimeReadsAsUnknown()
	{
		assertEquals("unknown", MarketFigures.formatAge(0, NOW));
		assertEquals("unknown", MarketFigures.formatAge(-1, NOW));
	}

	@Test
	public void tradeAgesStepThroughSecondsMinutesHoursAndDays()
	{
		assertEquals("30s ago", MarketFigures.formatAge(NOW - 30, NOW));
		assertEquals("5m ago", MarketFigures.formatAge(NOW - 300, NOW));
		assertEquals("3hr ago", MarketFigures.formatAge(NOW - 10_800, NOW));
		assertEquals("2d ago", MarketFigures.formatAge(NOW - 172_800, NOW));
	}

	@Test
	public void aTimestampInTheFutureClampsToZeroRatherThanGoingNegative()
	{
		assertEquals("0s ago", MarketFigures.formatAge(NOW + 500, NOW));
	}

	@Test
	public void stalenessIsDecidedByTheThreshold()
	{
		assertFalse(MarketFigures.isStale(NOW - 59 * 60, NOW, 60));
		assertFalse(MarketFigures.isStale(NOW - 60 * 60, NOW, 60));
		assertTrue(MarketFigures.isStale(NOW - 61 * 60, NOW, 60));
	}

	@Test
	public void anUnknownTradeTimeIsNeverStale()
	{
		assertFalse(MarketFigures.isStale(0, NOW, 1));
	}

	@Test
	public void changeIsFractionalAndSigned()
	{
		assertEquals(0.5, MarketFigures.percentChange(150, 100), 0.0001);
		assertEquals(-0.25, MarketFigures.percentChange(75, 100), 0.0001);
	}

	@Test
	public void changeAgainstAnUnknownBaselineIsZeroRatherThanTotal()
	{
		assertEquals(0.0, MarketFigures.percentChange(150, 0), 0.0001);
		assertEquals(0.0, MarketFigures.percentChange(0, 100), 0.0001);
	}

	@Test
	public void changeIsFormattedWithASignAndOneDecimal()
	{
		assertEquals("+50.0%", MarketFigures.formatChange(0.5));
		assertEquals("-25.0%", MarketFigures.formatChange(-0.25));
		assertEquals("+4.2%", MarketFigures.formatChange(0.042));
	}

	@Test
	public void noChangeRendersAsADashRatherThanZeroPercent()
	{
		assertEquals("-", MarketFigures.formatChange(0));
	}
}
