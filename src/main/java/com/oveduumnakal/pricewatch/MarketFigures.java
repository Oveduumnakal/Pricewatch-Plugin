/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

/**
 * The arithmetic behind the market info block and the overview grid: the Grand
 * Exchange sell tax, how old a trade timestamp is, and the change between two
 * prices.
 *
 * <p>Every method takes the current time as an argument rather than reading the
 * clock, so the results are reproducible and the staleness rules can be tested
 * without waiting.
 *
 * <p>Stateless utility.
 */
final class MarketFigures
{
	/** Items below this price are exempt from the Grand Exchange sell tax. */
	private static final long TAX_EXEMPT_BELOW = 50L;

	/** The tax is capped per item, however expensive it is. */
	private static final long TAX_CAP = 5_000_000L;

	private MarketFigures()
	{
	}

	/**
	 * @param price the unit sale price
	 * @return the Grand Exchange sell tax on one unit: 2% rounded down, nothing
	 *         below 50gp, and never more than the cap
	 */
	static long geTax(long price)
	{
		if (price < TAX_EXEMPT_BELOW)
			return 0;

		return Math.min((long) Math.floor(price * 0.02), TAX_CAP);
	}

	/**
	 * Formats a trade timestamp's age compactly.
	 *
	 * @param epochSeconds when the trade happened, or 0 when unknown
	 * @param nowSeconds   the current time in epoch seconds
	 * @return e.g. {@code "5s ago"}, {@code "3hr ago"}, {@code "2d ago"}, or
	 *         {@code "unknown"} when there is no timestamp
	 */
	static String formatAge(long epochSeconds, long nowSeconds)
	{
		if (epochSeconds <= 0)
			return "unknown";

		long ageSec = Math.max(0, nowSeconds - epochSeconds);
		if (ageSec < 60)
			return ageSec + "s ago";

		long mins = ageSec / 60;
		if (mins < 60)
			return mins + "m ago";

		long hours = mins / 60;
		if (hours < 24)
			return hours + "hr ago";

		return (hours / 24) + "d ago";
	}

	/**
	 * @param epochSeconds     when the trade happened, or 0 when unknown
	 * @param nowSeconds       the current time in epoch seconds
	 * @param thresholdMinutes how old a trade may be before it reads as stale
	 * @return whether the timestamp is older than the threshold. An unknown
	 *         timestamp is never stale — there is nothing to be stale about, and
	 *         dimming it would suggest the price itself is old
	 */
	static boolean isStale(long epochSeconds, long nowSeconds, int thresholdMinutes)
	{
		if (epochSeconds <= 0)
			return false;

		return nowSeconds - epochSeconds > (long) thresholdMinutes * 60L;
	}

	/**
	 * @param current  the price now
	 * @param baseline the price to compare against
	 * @return the fractional change from baseline to current, or 0 when either side
	 *         is missing — a change against an unknown baseline is not 100%, it is
	 *         simply unknown
	 */
	static double percentChange(long current, long baseline)
	{
		if (current <= 0 || baseline <= 0)
			return 0;

		return (double) (current - baseline) / baseline;
	}

	/**
	 * @param change the fractional change
	 * @return the change as a signed percentage, e.g. {@code "+4.2%"}, or a dash
	 *         when there is no change to show
	 */
	static String formatChange(double change)
	{
		if (change == 0)
			return "-";

		return String.format("%+.1f%%", change * 100);
	}
}
