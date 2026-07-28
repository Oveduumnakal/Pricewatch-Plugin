/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Verifies the dose-suffix parsing of {@link DoseFamily}. */
public class DoseFamilyTest
{
	@Test
	public void parsesDoseAndBase()
	{
		DoseFamily.Parsed parsed = DoseFamily.parse("Prayer potion(4)");
		assertEquals("prayer potion", parsed.base);
		assertEquals(4, parsed.doses);
	}

	@Test
	public void doseVariantsShareABase()
	{
		DoseFamily.Parsed one = DoseFamily.parse("Saradomin brew(1)");
		DoseFamily.Parsed three = DoseFamily.parse("Saradomin brew(3)");
		assertEquals(one.base, three.base);
	}

	@Test
	public void rejectsNamesWithoutADoseSuffix()
	{
		assertNull(DoseFamily.parse("Prayer potion"));
		assertNull(DoseFamily.parse("Abyssal whip"));
	}

	@Test
	public void rejectsChargeCountsBeyondTheDoseRange()
	{
		assertNull(DoseFamily.parse("Ring of dueling(8)"));
		assertNull(DoseFamily.parse("Games necklace(0)"));
	}

	@Test
	public void rejectsNull()
	{
		assertNull(DoseFamily.parse(null));
	}
}
