/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

/**
 * Pulse rate of the highlight glow effect, from {@link #SLOW} to {@link #FAST},
 * or {@link #OFF} for a steady (non-pulsing) highlight. The {@code displayName}
 * is the label shown in the config dropdown.
 */
public enum GlowSpeed
{
	SLOW("Slow"),
	MEDIUM("Medium"),
	FAST("Fast"),
	OFF("Off");

	private final String displayName;

	GlowSpeed(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
