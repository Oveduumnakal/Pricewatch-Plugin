/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Decides whether an alert rule's condition currently holds for a watched item, and
 * words the message that fires when it does.
 *
 * <p>Pure and Swing-free: every reading comes from the item's own market data and the
 * two rune prices passed in, so the whole rule surface is unit-testable. The firing
 * policy — which rules re-arm, which are removed, when the notification is raised —
 * belongs to the plugin, not here.
 *
 * <p>A reading that cannot be taken yet is {@code null}/empty rather than false. That
 * distinction matters: a rule over data that has not loaded must neither fire nor be
 * recorded as having been false, or a repeat rule would fire spuriously the moment the
 * data arrives.
 */
final class AlertEvaluation
{
	/**
	 * The largest believable percent change. A window average of a few coins against a
	 * live price of millions produces a nonsense percentage, and alerting on it would
	 * be worse than staying quiet.
	 */
	private static final double MAX_DELTA_PCT = 1000.0;

	private AlertEvaluation()
	{
	}

	/**
	 * Evaluates one rule against an item.
	 *
	 * @param item        the watched item
	 * @param rule        the rule to test
	 * @param naturePrice the current nature rune price, for the alchemy metric
	 * @param firePrice   the current fire rune price, for the alchemy metric
	 * @return {@code TRUE}/{@code FALSE} for the condition, or {@code null} when it
	 *         cannot be evaluated yet — an incomplete rule, or data not yet loaded
	 */
	static Boolean evaluate(WatchedItem item, NotificationRule rule, long naturePrice, long firePrice)
	{
		final NotificationMetric metric = rule.getMetric();
		if (metric == null || rule.getOperation() == null)
			return null;

		if (metric.isCategorical())
		{
			String current = categoryValue(item, metric);
			if (current == null || rule.getValue() == null)
				return null;

			return current.equalsIgnoreCase(rule.getValue().trim());
		}

		final TimeWindow window = metric.locksTimeframeToMonth() ? TimeWindow.MONTH : rule.getTimeWindow();
		final OptionalDouble current = numericValue(item, metric, window, naturePrice, firePrice);
		if (!current.isPresent())
			return null;

		final OptionalDouble target = metric.getKind() == NotificationMetric.Kind.PERCENT
				? NotificationRule.parsePercent(rule.getValue())
				: NotificationRule.parseNumeric(rule.getValue());
		if (!target.isPresent())
			return null;

		return rule.getOperation().test(current.getAsDouble(), target.getAsDouble());
	}

	/**
	 * Resolves the current numeric reading of a metric over a window — a price, volume,
	 * high-alchemy profit, or the live price's percent change against the window average.
	 *
	 * @return the reading, or empty when the underlying data is missing or unreliable
	 */
	static OptionalDouble numericValue(WatchedItem item, NotificationMetric metric, TimeWindow window,
			long naturePrice, long firePrice)
	{
		if (window == null)
			return OptionalDouble.empty();

		final PriceStats stats = item.getWindowStats().get(window);
		if (stats == null)
			return OptionalDouble.empty();

		final long avg = stats.getAvg();
		switch (metric)
		{
			case HIGH:
				return OptionalDouble.of(stats.getHigh());
			case LOW:
				return OptionalDouble.of(stats.getLow());
			case AVERAGE:
				return OptionalDouble.of(avg);
			case VOLUME:
				return OptionalDouble.of(stats.getVolume());
			case HA_PROFIT:
				return alchProfit(item, avg, naturePrice, firePrice);
			case DELTA_PCT:
				return deltaPercent(item, avg);
			default:
				return OptionalDouble.empty();
		}
	}

	/** @return what one high-alchemy cast on this item nets at the window average, or empty when unknown. */
	private static OptionalDouble alchProfit(WatchedItem item, long avg, long naturePrice, long firePrice)
	{
		if (avg <= 0 || item.getHighAlch() <= 0)
			return OptionalDouble.empty();

		return OptionalDouble.of(MarketFigures.alchProfit(item.getHighAlch(), avg,
				naturePrice, firePrice, MarketFigures.HIGH_ALCH_FIRE_RUNES));
	}

	/** @return the live price's percent change against the window average, or empty when implausible. */
	private static OptionalDouble deltaPercent(WatchedItem item, long avg)
	{
		final long current = item.getAvgPrice();
		if (current <= 0 || avg <= 0)
			return OptionalDouble.empty();

		final double pct = Math.round(MarketFigures.percentChange(current, avg) * 1000.0) / 10.0;

		return Math.abs(pct) > MAX_DELTA_PCT ? OptionalDouble.empty() : OptionalDouble.of(pct);
	}

	/**
	 * Resolves the current categorical rating of a metric — volatility, liquidity, or
	 * position in the 30-day range — via {@link MarketClassifier}.
	 *
	 * @return the rating label, or {@code null} when it cannot be classified yet
	 */
	static String categoryValue(WatchedItem item, NotificationMetric metric)
	{
		switch (metric)
		{
			case VOLATILITY:
				return MarketClassifier.volatility(item.getSeriesFor(TimeWindow.WEEK));
			case LIQUIDITY:
				return liquidity(item);
			case RANGE_30D:
				return rangePosition(item);
			default:
				return null;
		}
	}

	/** @return the liquidity rating from the item's 24-hour traded volume. */
	private static String liquidity(WatchedItem item)
	{
		final PriceStats stats = item.getWindowStats().get(TimeWindow.H24);

		return MarketClassifier.liquidity(stats == null ? 0 : stats.getVolume());
	}

	/** @return where the live price sits in the item's 30-day range. */
	private static String rangePosition(WatchedItem item)
	{
		final long[] range = MarketClassifier.thirtyDayRange(item.getSeriesFor(TimeWindow.MONTH));

		return MarketClassifier.rangePosition(range[0], range[1], item.getAvgPrice());
	}

	/**
	 * Words the notification, e.g. {@code "Pricewatch: Coal - High >= 200"}.
	 *
	 * @param item the item whose rule fired
	 * @param rule the rule that fired
	 * @return the user-facing message
	 */
	static String notificationText(WatchedItem item, NotificationRule rule)
	{
		final NotificationMetric metric = rule.getMetric();

		return "Pricewatch: " + item.getName() + " - " + metric.getDisplayName()
				+ " " + rule.getOperation().getSymbol() + " " + valueDisplay(rule, metric);
	}

	/** @return the rule's threshold formatted for its metric kind, falling back to the raw text. */
	private static String valueDisplay(NotificationRule rule, NotificationMetric metric)
	{
		if (metric.isCategorical())
			return rule.getValue();

		if (metric.getKind() == NotificationMetric.Kind.PERCENT)
		{
			OptionalDouble percent = NotificationRule.parsePercent(rule.getValue());

			return percent.isPresent() ? NotificationRule.formatPercent(percent.getAsDouble()) : rule.getValue();
		}

		OptionalDouble numeric = NotificationRule.parseNumeric(rule.getValue());

		return numeric.isPresent()
				? String.format(Locale.US, "%,d", Math.round(numeric.getAsDouble()))
				: rule.getValue();
	}
}
