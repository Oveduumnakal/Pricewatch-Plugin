/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a potion's dose from its display name (#220): {@code Prayer potion(4)}
 * resolves to base {@code "prayer potion"} at 4 doses, so the four dose item ids
 * of one potion share a family and their cost basis can follow the liquid across a
 * decant. Only a trailing {@code (1)}–{@code (4)} counts — the standard potion dose
 * range — which keeps charge variants such as {@code Ring of dueling(8)} out of the
 * family. Client-free and unit-testable.
 */
final class DoseFamily
{
	/** Highest dose a standard tradeable potion holds; larger parentheticals are charges, not doses. */
	private static final int MAX_DOSES = 4;

	private static final Pattern DOSE_SUFFIX = Pattern.compile("^(.*)\\((\\d+)\\)$");

	private DoseFamily()
	{
	}

	/** One item's place in a dose family: the shared base name and this id's dose count. */
	static final class Parsed
	{
		final String base;
		final int doses;

		Parsed(String base, int doses)
		{
			this.base = base;
			this.doses = doses;
		}
	}

	/**
	 * @return the dose family of {@code name}, or {@code null} when it carries no
	 *         trailing {@code (1)}–{@code (4)} dose suffix
	 */
	static Parsed parse(String name)
	{
		if (name == null)
			return null;

		Matcher matcher = DOSE_SUFFIX.matcher(name.trim());
		if (!matcher.matches())
			return null;

		String base = matcher.group(1).trim();
		base = base.toLowerCase(Locale.ROOT);
		int doses = Integer.parseInt(matcher.group(2));
		if (base.isEmpty() || doses < 1 || doses > MAX_DOSES)
			return null;

		return new Parsed(base, doses);
	}
}
