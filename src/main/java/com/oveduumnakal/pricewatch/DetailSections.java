/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns the per-section {@link SectionSlot} settings into the order the detail
 * view draws, and works out which section a newly-assigned slot collides with.
 *
 * <p>Slots are assigned independently, so nothing stops two sections claiming
 * the same position. Rather than refuse the change, the plugin swaps the two —
 * which is what a user dragging one section above another expects — and this is
 * where the "which one did I displace" question is answered.
 *
 * <p>Stateless utility.
 */
final class DetailSections
{
	private DetailSections()
	{
	}

	/**
	 * Orders the visible sections.
	 *
	 * <p>Sections set to {@link SectionSlot#NONE}, and any the caller has no entry
	 * for, are left out. Two sections sharing a slot are ordered by their enum
	 * declaration so the result stays deterministic while a collision is being
	 * resolved.
	 *
	 * @param slots each section's assigned position
	 * @return the sections to draw, in order
	 */
	static List<DetailSection> ordered(Map<DetailSection, SectionSlot> slots)
	{
		return slots.entrySet().stream()
				.filter(entry -> entry.getValue() != null && !entry.getValue().isNone())
				.sorted(Comparator
						.comparing((Map.Entry<DetailSection, SectionSlot> entry) -> entry.getValue().ordinal())
						.thenComparing(entry -> entry.getKey().ordinal()))
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());
	}

	/**
	 * Finds the section a slot was taken from.
	 *
	 * @param slots   each section's assigned position, including the new one
	 * @param changed the section the user just moved
	 * @return the other section now sharing {@code changed}'s slot, or {@code null}
	 *         when there is no collision to resolve
	 */
	static DetailSection displacedBy(Map<DetailSection, SectionSlot> slots, DetailSection changed)
	{
		final SectionSlot target = slots.get(changed);

		if (target == null || target.isNone())
			return null;

		return slots.entrySet().stream()
				.filter(entry -> entry.getKey() != changed)
				.filter(entry -> target.equals(entry.getValue()))
				.map(Map.Entry::getKey)
				.findFirst()
				.orElse(null);
	}
}
