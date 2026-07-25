/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link GpFormat}: compact K/M/B abbreviation, single-decimal
 * narrowing, and full grouped formatting, including sign handling.
 */
public class GpFormatTest
{
	@Test
	public void shortValueKeepsSmallValuesAsGroupedDigits()
	{
		assertEquals("0", GpFormat.shortValue(0));
		assertEquals("999", GpFormat.shortValue(999));
		assertEquals("-5", GpFormat.shortValue(-5));
	}

	@Test
	public void shortValueAbbreviatesToThreeSignificantFigures()
	{
		assertEquals("1K", GpFormat.shortValue(1_000));
		assertEquals("1.5K", GpFormat.shortValue(1_500));
		assertEquals("2.34K", GpFormat.shortValue(2_340));
		assertEquals("12.3K", GpFormat.shortValue(12_340));
		assertEquals("234K", GpFormat.shortValue(234_000));
		assertEquals("1M", GpFormat.shortValue(1_000_000));
		assertEquals("1.23M", GpFormat.shortValue(1_230_000));
		assertEquals("2.1B", GpFormat.shortValue(2_100_000_000L));
	}

	@Test
	public void shortValueKeepsNegativeSign()
	{
		assertEquals("-1.5K", GpFormat.shortValue(-1_500));
		assertEquals("-2.1B", GpFormat.shortValue(-2_100_000_000L));
	}

	@Test
	public void shortValue1dpCapsMantissaToOneDecimal()
	{
		assertEquals("2.3K", GpFormat.shortValue1dp(2_340));
		assertEquals("1.5K", GpFormat.shortValue1dp(1_500));
		assertEquals("950", GpFormat.shortValue1dp(950));
	}

	@Test
	public void gpSuffixVariants()
	{
		assertEquals("1.5K gp", GpFormat.shortGp(1_500));
		assertEquals("1,234,567 gp", GpFormat.fullGp(1_234_567));
	}

	@Test
	public void abbreviationDropsTrailingZeros()
	{
		assertEquals("2K", GpFormat.shortValue(2_000));
		assertEquals("2.5M", GpFormat.shortValue(2_500_000));
	}
}
