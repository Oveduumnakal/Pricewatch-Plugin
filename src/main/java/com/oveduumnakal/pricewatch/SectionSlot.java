/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

/**
 * An ordinal position ({@link #FIRST}..{@link #EIGHTH}) assigned to a detail-view
 * section to control its order, or {@link #NONE} to hide it. Used by the config
 * so each section can be placed independently. The {@code label} is the name
 * shown in the config dropdown.
 *
 * <p>Eight positions, not Stockpile's ten: the collection log and the Collection
 * Current Values block have no counterpart here, so their slots are gone.
 *
 * <p>Public because it is the return type of a {@code @ConfigItem} accessor: the
 * RuneLite config proxy lives in another module and must be able to access it, or
 * the plugin fails to start with an {@link IllegalAccessError}.
 */
public enum SectionSlot
{
	NONE("None"),
	FIRST("1st"),
	SECOND("2nd"),
	THIRD("3rd"),
	FOURTH("4th"),
	FIFTH("5th"),
	SIXTH("6th"),
	SEVENTH("7th"),
	EIGHTH("8th");

	private final String label;

	SectionSlot(String label)
	{
		this.label = label;
	}

	/** @return whether this slot hides its section rather than placing it. */
	public boolean isNone()
	{
		return this == NONE;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
