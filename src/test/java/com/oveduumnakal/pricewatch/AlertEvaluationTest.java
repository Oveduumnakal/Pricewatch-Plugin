/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link AlertEvaluation}: which readings a rule takes, when a rule cannot
 * be evaluated at all, and how a fired rule is worded.
 */
public class AlertEvaluationTest
{
	private static final long NATURE = 90;

	private static final long FIRE = 5;

	/** @return a watched item with the given stats already set on the LIVE window. */
	private static WatchedItem itemWith(long high, long low, long avg, long volume)
	{
		WatchedItem item = new WatchedItem(4151, "Abyssal whip");

		item.getWindowStats().put(TimeWindow.LIVE, new PriceStats(high, low, avg, volume));

		return item;
	}

	/** @return a rule over the LIVE window with the given metric, operator and threshold. */
	private static NotificationRule ruleOf(NotificationMetric metric, NotificationOperation op, String value)
	{
		NotificationRule rule = new NotificationRule();

		rule.setMetric(metric);
		rule.setTimeWindow(TimeWindow.LIVE);
		rule.setOperation(op);
		rule.setValue(value);

		return rule;
	}

	@Test
	public void aPriceRuleComparesTheWindowsReading()
	{
		WatchedItem item = itemWith(2_500_000, 2_400_000, 2_450_000, 100);

		assertTrue(AlertEvaluation.evaluate(item,
				ruleOf(NotificationMetric.HIGH, NotificationOperation.GT, "2m"), NATURE, FIRE));
		assertFalse(AlertEvaluation.evaluate(item,
				ruleOf(NotificationMetric.HIGH, NotificationOperation.LT, "2m"), NATURE, FIRE));
	}

	@Test
	public void eachNumericMetricReadsItsOwnFigure()
	{
		WatchedItem item = itemWith(300, 100, 200, 4242);

		assertTrue(AlertEvaluation.evaluate(item,
				ruleOf(NotificationMetric.LOW, NotificationOperation.EQ, "100"), NATURE, FIRE));
		assertTrue(AlertEvaluation.evaluate(item,
				ruleOf(NotificationMetric.AVERAGE, NotificationOperation.EQ, "200"), NATURE, FIRE));
		assertTrue(AlertEvaluation.evaluate(item,
				ruleOf(NotificationMetric.VOLUME, NotificationOperation.EQ, "4242"), NATURE, FIRE));
	}

	@Test
	public void anIncompleteRuleCannotBeEvaluated()
	{
		WatchedItem item = itemWith(300, 100, 200, 10);

		assertNull(AlertEvaluation.evaluate(item,
				ruleOf(null, NotificationOperation.GT, "100"), NATURE, FIRE));
		assertNull(AlertEvaluation.evaluate(item,
				ruleOf(NotificationMetric.HIGH, null, "100"), NATURE, FIRE));
	}

	@Test
	public void anUnparseableThresholdCannotBeEvaluated()
	{
		WatchedItem item = itemWith(300, 100, 200, 10);

		assertNull(AlertEvaluation.evaluate(item,
				ruleOf(NotificationMetric.HIGH, NotificationOperation.GT, "soon"), NATURE, FIRE));
		assertNull(AlertEvaluation.evaluate(item,
				ruleOf(NotificationMetric.HIGH, NotificationOperation.GT, ""), NATURE, FIRE));
	}

	@Test
	public void aWindowWithNoStatsYetCannotBeEvaluated()
	{
		WatchedItem item = new WatchedItem(4151, "Abyssal whip");

		assertNull(AlertEvaluation.evaluate(item,
				ruleOf(NotificationMetric.HIGH, NotificationOperation.GT, "1"), NATURE, FIRE));
	}

	@Test
	public void alchemyProfitNetsBothRuneCostsOffThePayout()
	{
		WatchedItem item = itemWith(0, 0, 1000, 0);

		item.setHighAlch(1200);

		assertTrue(AlertEvaluation.evaluate(item,
				ruleOf(NotificationMetric.HA_PROFIT, NotificationOperation.EQ, "85"), NATURE, FIRE));
	}

	@Test
	public void alchemyProfitNeedsAnAlchValueToBeKnown()
	{
		WatchedItem item = itemWith(0, 0, 1000, 0);

		assertNull(AlertEvaluation.evaluate(item,
				ruleOf(NotificationMetric.HA_PROFIT, NotificationOperation.GT, "0"), NATURE, FIRE));
	}

	@Test
	public void percentChangeComparesTheLivePriceAgainstTheWindowAverage()
	{
		WatchedItem item = itemWith(0, 0, 100, 0);

		item.setAvgPrice(110);

		assertTrue(AlertEvaluation.evaluate(item,
				ruleOf(NotificationMetric.DELTA_PCT, NotificationOperation.EQ, "10%"), NATURE, FIRE));
	}

	@Test
	public void anImplausiblePercentChangeIsSuppressedRatherThanFired()
	{
		WatchedItem item = itemWith(0, 0, 1, 0);

		item.setAvgPrice(1_000_000);

		assertNull(AlertEvaluation.evaluate(item,
				ruleOf(NotificationMetric.DELTA_PCT, NotificationOperation.GT, "5%"), NATURE, FIRE));
	}

	@Test
	public void aCategoricalRuleMatchesItsRatingCaseInsensitively()
	{
		WatchedItem item = new WatchedItem(4151, "Abyssal whip");

		item.getWindowStats().put(TimeWindow.H24, new PriceStats(0, 0, 0, 10_000));

		assertTrue(AlertEvaluation.evaluate(item,
				ruleOf(NotificationMetric.LIQUIDITY, NotificationOperation.EQ, "high"), NATURE, FIRE));
		assertFalse(AlertEvaluation.evaluate(item,
				ruleOf(NotificationMetric.LIQUIDITY, NotificationOperation.EQ, "Low"), NATURE, FIRE));
	}

	@Test
	public void anUnclassifiableRatingCannotBeEvaluated()
	{
		WatchedItem item = new WatchedItem(4151, "Abyssal whip");

		assertNull(AlertEvaluation.evaluate(item,
				ruleOf(NotificationMetric.LIQUIDITY, NotificationOperation.EQ, "High"), NATURE, FIRE));
	}

	@Test
	public void theThirtyDayRangeMetricIgnoresWhateverTimeframeTheRuleCarries()
	{
		WatchedItem item = new WatchedItem(4151, "Abyssal whip");
		NotificationRule rule = ruleOf(NotificationMetric.RANGE_30D, NotificationOperation.EQ, "Highest");

		rule.setTimeWindow(TimeWindow.LIVE);

		assertTrue(NotificationMetric.RANGE_30D.locksTimeframeToMonth());
		assertNull(AlertEvaluation.evaluate(item, rule, NATURE, FIRE));
	}

	@Test
	public void aFiredNumericRuleIsWordedWithGroupedDigits()
	{
		WatchedItem item = itemWith(2_500_000, 0, 0, 0);

		assertEquals("Pricewatch: Abyssal whip - High >= 2,000,000",
				AlertEvaluation.notificationText(item,
						ruleOf(NotificationMetric.HIGH, NotificationOperation.GTE, "2m")));
	}

	@Test
	public void aFiredPercentRuleKeepsItsPercentSign()
	{
		WatchedItem item = itemWith(0, 0, 0, 0);

		assertEquals("Pricewatch: Abyssal whip - Price Change > 10%",
				AlertEvaluation.notificationText(item,
						ruleOf(NotificationMetric.DELTA_PCT, NotificationOperation.GT, "10%")));
	}

	@Test
	public void aFiredCategoricalRuleIsWordedWithItsRating()
	{
		WatchedItem item = itemWith(0, 0, 0, 0);

		assertEquals("Pricewatch: Abyssal whip - Liquidity = High",
				AlertEvaluation.notificationText(item,
						ruleOf(NotificationMetric.LIQUIDITY, NotificationOperation.EQ, "High")));
	}

	@Test
	public void noMetricCanReadAnythingAboutAHolding()
	{
		for (NotificationMetric metric : NotificationMetric.values())
		{
			assertFalse(metric.name() + " must not be a quantity or cost-basis metric",
					metric.name().contains("QUANTITY") || metric.name().contains("ITM"));
		}
	}
}
