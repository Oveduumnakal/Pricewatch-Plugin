/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests for {@link DetailSections}: the order sections are drawn in, which are
 * hidden, and which section a newly-assigned slot displaces.
 */
public class DetailSectionsTest
{
	/** @return an empty section-to-slot map. */
	private static Map<DetailSection, SectionSlot> slots()
	{
		return new EnumMap<>(DetailSection.class);
	}

	/** @return the eight sections in their shipped default positions. */
	private static Map<DetailSection, SectionSlot> defaults()
	{
		Map<DetailSection, SectionSlot> slots = slots();

		slots.put(DetailSection.ITEM_VALUES, SectionSlot.FIRST);
		slots.put(DetailSection.MARKET_INFO, SectionSlot.SECOND);
		slots.put(DetailSection.PRICE_OVERVIEW, SectionSlot.THIRD);
		slots.put(DetailSection.PRICE_GRAPH, SectionSlot.FOURTH);
		slots.put(DetailSection.VOLUME_GRAPH, SectionSlot.FIFTH);
		slots.put(DetailSection.ALCHEMY, SectionSlot.SIXTH);
		slots.put(DetailSection.LINKS, SectionSlot.SEVENTH);
		slots.put(DetailSection.ALERTS, SectionSlot.EIGHTH);

		return slots;
	}

	@Test
	public void theDefaultsOrderAllEightSectionsWithoutGaps()
	{
		assertEquals(Arrays.asList(
				DetailSection.ITEM_VALUES, DetailSection.MARKET_INFO, DetailSection.PRICE_OVERVIEW,
				DetailSection.PRICE_GRAPH, DetailSection.VOLUME_GRAPH, DetailSection.ALCHEMY,
				DetailSection.LINKS, DetailSection.ALERTS),
				DetailSections.ordered(defaults()));
	}

	@Test
	public void slotsDriveTheOrderRatherThanTheEnumDeclaration()
	{
		Map<DetailSection, SectionSlot> slots = slots();

		slots.put(DetailSection.ALERTS, SectionSlot.FIRST);
		slots.put(DetailSection.ITEM_VALUES, SectionSlot.SECOND);

		assertEquals(Arrays.asList(DetailSection.ALERTS, DetailSection.ITEM_VALUES),
				DetailSections.ordered(slots));
	}

	@Test
	public void aNoneSectionIsLeftOut()
	{
		Map<DetailSection, SectionSlot> slots = defaults();

		slots.put(DetailSection.ALCHEMY, SectionSlot.NONE);

		assertEquals(7, DetailSections.ordered(slots).size());
	}

	@Test
	public void gapsInTheSlotsAreHarmless()
	{
		Map<DetailSection, SectionSlot> slots = slots();

		slots.put(DetailSection.LINKS, SectionSlot.SECOND);
		slots.put(DetailSection.ALCHEMY, SectionSlot.EIGHTH);

		assertEquals(Arrays.asList(DetailSection.LINKS, DetailSection.ALCHEMY),
				DetailSections.ordered(slots));
	}

	@Test
	public void everySectionHiddenGivesAnEmptyDetailView()
	{
		Map<DetailSection, SectionSlot> slots = slots();

		for (DetailSection section : DetailSection.values())
			slots.put(section, SectionSlot.NONE);

		assertEquals(Collections.emptyList(), DetailSections.ordered(slots));
	}

	@Test
	public void aTiedSlotStillOrdersDeterministically()
	{
		Map<DetailSection, SectionSlot> slots = slots();

		slots.put(DetailSection.LINKS, SectionSlot.FIRST);
		slots.put(DetailSection.ITEM_VALUES, SectionSlot.FIRST);

		assertEquals(Arrays.asList(DetailSection.ITEM_VALUES, DetailSection.LINKS),
				DetailSections.ordered(slots));
	}

	@Test
	public void movingASectionOntoAnotherReportsWhatItDisplaced()
	{
		Map<DetailSection, SectionSlot> slots = defaults();

		slots.put(DetailSection.ALERTS, SectionSlot.FIRST);

		assertEquals(DetailSection.ITEM_VALUES, DetailSections.displacedBy(slots, DetailSection.ALERTS));
	}

	@Test
	public void aSectionInAnUnoccupiedSlotDisplacesNothing()
	{
		Map<DetailSection, SectionSlot> slots = defaults();

		slots.put(DetailSection.ALCHEMY, SectionSlot.NONE);
		slots.put(DetailSection.ALERTS, SectionSlot.SIXTH);

		assertNull(DetailSections.displacedBy(slots, DetailSection.ALERTS));
	}

	@Test
	public void hidingASectionDisplacesNothing()
	{
		Map<DetailSection, SectionSlot> slots = defaults();

		slots.put(DetailSection.ALERTS, SectionSlot.NONE);

		assertNull(DetailSections.displacedBy(slots, DetailSection.ALERTS));
	}

	@Test
	public void severalHiddenSectionsDoNotCountAsCollidingWithEachOther()
	{
		Map<DetailSection, SectionSlot> slots = defaults();

		slots.put(DetailSection.ALCHEMY, SectionSlot.NONE);
		slots.put(DetailSection.LINKS, SectionSlot.NONE);

		assertNull(DetailSections.displacedBy(slots, DetailSection.LINKS));
	}

	@Test
	public void anUnknownSectionDisplacesNothing()
	{
		assertNull(DetailSections.displacedBy(slots(), DetailSection.ALERTS));
	}
}
