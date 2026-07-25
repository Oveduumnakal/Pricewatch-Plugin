/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.Color;

/**
 * The plugin's shared colour palette, defined once so the panel, overlays, and
 * charts stay visually consistent and theme tweaks are one-line changes.
 */
final class PricewatchColors
{
	/** Green used for high / instant-buy prices and positive profit. */
	static final Color HIGH = new Color(100, 220, 100);

	/** Red used for low / instant-sell prices and negative profit. */
	static final Color LOW = new Color(220, 100, 100);

	/** Gold used for average prices and active/selected accents. */
	static final Color AVG = new Color(255, 200, 0);

	/** Grey rule used for section dividers and separators. */
	static final Color DIVIDER = new Color(80, 80, 80);

	/** Darker grey used for table grid lines and faint borders. */
	static final Color TABLE_GRID = new Color(60, 60, 60);

	/** Muted grey used for placeholder/secondary text and loading states. */
	static final Color MUTED = new Color(150, 150, 150);

	/** Hover-tint background behind high-price values. */
	static final Color TINT_HIGH = new Color(35, 70, 35);

	/** Hover-tint background behind low-price values. */
	static final Color TINT_LOW = new Color(70, 35, 35);

	/** Hover-tint background behind average-price values. */
	static final Color TINT_AVG = new Color(75, 60, 25);

	/** Hover-tint background behind volume values. */
	static final Color TINT_VOLUME = new Color(55, 55, 55);

	/** @return the colour as a {@code #rrggbb} hex string for inline HTML styling. */
	static String toHex(Color c)
	{
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}

	private PricewatchColors()
	{
	}
}
