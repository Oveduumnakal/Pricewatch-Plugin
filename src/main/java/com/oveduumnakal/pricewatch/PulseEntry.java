/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.Color;
import javax.swing.JLabel;

/** One in-flight price-change pulse: the label being animated, its base color, and the animation start time. */
final class PulseEntry
{
	final JLabel label;
	final Color base;
	final long start;

	PulseEntry(JLabel label, Color base, long start)
	{
		this.label = label;
		this.base = base;
		this.start = start;
	}
}
