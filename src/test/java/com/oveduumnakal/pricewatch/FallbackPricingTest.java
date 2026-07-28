/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Verifies the legacy {@code AutoAddMode} → {@link FallbackPricing} migration mapping (#219). */
public class FallbackPricingTest
{
	@Test
	public void pricingModesMapToTheirEquivalent()
	{
		assertEquals(FallbackPricing.HIGH, FallbackPricing.fromLegacyMode("HIGH"));
		assertEquals(FallbackPricing.LOW, FallbackPricing.fromLegacyMode("LOW"));
		assertEquals(FallbackPricing.AVG, FallbackPricing.fromLegacyMode("AVG"));
		assertEquals(FallbackPricing.ZERO, FallbackPricing.fromLegacyMode("ZERO"));
	}

	@Test
	public void offKeepsAutoAddOffButDefaultsPricingToAvg()
	{
		assertEquals(FallbackPricing.AVG, FallbackPricing.fromLegacyMode("OFF"));
	}

	@Test
	public void alreadyMigratedOrUnknownValuesAreLeftAlone()
	{
		assertNull(FallbackPricing.fromLegacyMode("true"));
		assertNull(FallbackPricing.fromLegacyMode("false"));
		assertNull(FallbackPricing.fromLegacyMode(null));
		assertNull(FallbackPricing.fromLegacyMode("garbage"));
	}
}
