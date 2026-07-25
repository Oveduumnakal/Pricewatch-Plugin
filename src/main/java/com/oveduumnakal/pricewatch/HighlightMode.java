/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

/**
 * Where watched items are highlighted: on the {@link #GROUND}, in the
 * {@link #INV_BANK} (inventory/bank), {@link #BOTH}, or {@link #OFF}. Query the
 * surfaces with {@link #ground()} and {@link #invBank()} rather than comparing
 * constants. The {@code displayName} is the label shown in the config dropdown.
 */
public enum HighlightMode
{
	GROUND("Ground"),
	INV_BANK("Inv/Bank"),
	BOTH("Both"),
	OFF("Off");

	private final String displayName;

	HighlightMode(String displayName)
	{
		this.displayName = displayName;
	}

	/** @return whether ground items should be highlighted in this mode. */
	public boolean ground()
	{
		return this == GROUND || this == BOTH;
	}

	/** @return whether inventory/bank items should be highlighted in this mode. */
	public boolean invBank()
	{
		return this == INV_BANK || this == BOTH;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
