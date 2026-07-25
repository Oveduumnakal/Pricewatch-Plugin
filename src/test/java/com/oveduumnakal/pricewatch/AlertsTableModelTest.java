/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link AlertsTableModel}: which cells a metric leaves editable, how
 * switching metric reseeds the rest of the row, and how a typed threshold is
 * normalised on the way in.
 */
public class AlertsTableModelTest
{
	private static final int COL_METRIC = 0;

	private static final int COL_TIME = 1;

	private static final int COL_OP = 2;

	private static final int COL_VALUE = 3;

	private static final int COL_REPEAT = 4;

	private WatchedItem item;

	private AlertsTableModel model;

	private int edits;

	@Before
	public void setUp()
	{
		item = new WatchedItem(4151, "Abyssal whip");
		edits = 0;
		model = new AlertsTableModel(() -> edits++);
		model.setItem(item);
		item.getNotifications().add(new NotificationRule());
	}

	/** @return the single rule under test, kept out of the assertions to avoid a three-link chain. */
	private NotificationRule rule()
	{
		return item.getNotifications().get(0);
	}

	@Test
	public void anItemWithNoRulesHasNoRows()
	{
		AlertsTableModel empty = new AlertsTableModel(() -> edits++);

		empty.setItem(new WatchedItem(4151, "Abyssal whip"));

		assertEquals(0, empty.getRowCount());
	}

	@Test
	public void withNoItemAtAllNothingIsEditable()
	{
		AlertsTableModel detached = new AlertsTableModel(() -> edits++);

		assertEquals(0, detached.getRowCount());
		assertFalse(detached.isCellEditable(0, COL_VALUE));
	}

	@Test
	public void choosingAMetricSeedsTheRestOfTheRow()
	{
		model.setValueAt(NotificationMetric.HIGH, 0, COL_METRIC);

		NotificationRule rule = rule();

		assertEquals(NotificationMetric.HIGH, rule.getMetric());
		assertEquals(TimeWindow.LIVE, rule.getTimeWindow());
		assertEquals(NotificationOperation.GTE, rule.getOperation());
		assertEquals(1, edits);
	}

	@Test
	public void aCategoricalMetricIsPinnedToEqualsAndSeededWithItsFirstRating()
	{
		model.setValueAt(NotificationMetric.LIQUIDITY, 0, COL_METRIC);

		NotificationRule rule = rule();

		assertEquals(NotificationOperation.EQ, rule.getOperation());
		assertEquals("Low", rule.getValue());
		assertFalse(model.isCellEditable(0, COL_OP));
	}

	@Test
	public void theThirtyDayRangeMetricPinsItsOwnTimeframe()
	{
		model.setValueAt(NotificationMetric.RANGE_30D, 0, COL_METRIC);

		assertEquals(TimeWindow.MONTH, rule().getTimeWindow());
		assertFalse(model.isCellEditable(0, COL_TIME));
	}

	@Test
	public void anOrdinaryMetricLeavesBothItsTimeframeAndOperatorEditable()
	{
		model.setValueAt(NotificationMetric.HIGH, 0, COL_METRIC);

		assertTrue(model.isCellEditable(0, COL_TIME));
		assertTrue(model.isCellEditable(0, COL_OP));
	}

	@Test
	public void reselectingTheSameMetricChangesNothing()
	{
		model.setValueAt(NotificationMetric.HIGH, 0, COL_METRIC);
		model.setValueAt(TimeWindow.WEEK, 0, COL_TIME);
		model.setValueAt(NotificationMetric.HIGH, 0, COL_METRIC);

		assertEquals(TimeWindow.WEEK, rule().getTimeWindow());
	}

	@Test
	public void aNumericThresholdIsStoredInItsShortForm()
	{
		model.setValueAt(NotificationMetric.HIGH, 0, COL_METRIC);
		model.setValueAt("5000000", 0, COL_VALUE);

		assertEquals("5M", rule().getValue());
	}

	@Test
	public void aPercentThresholdKeepsItsSign()
	{
		model.setValueAt(NotificationMetric.DELTA_PCT, 0, COL_METRIC);
		model.setValueAt("10", 0, COL_VALUE);

		assertEquals("10%", rule().getValue());
	}

	@Test
	public void anUnparseableThresholdLeavesThePreviousOneAlone()
	{
		model.setValueAt(NotificationMetric.HIGH, 0, COL_METRIC);
		model.setValueAt("2m", 0, COL_VALUE);
		model.setValueAt("later", 0, COL_VALUE);

		assertEquals("2M", rule().getValue());
	}

	@Test
	public void aCategoricalThresholdIsStoredAsTyped()
	{
		model.setValueAt(NotificationMetric.VOLATILITY, 0, COL_METRIC);
		model.setValueAt("High", 0, COL_VALUE);

		assertEquals("High", rule().getValue());
	}

	@Test
	public void editingARuleForgetsWhetherItWasPreviouslyTrue()
	{
		NotificationRule rule = rule();

		rule.setLastCondition(true);
		model.setValueAt(NotificationMetric.HIGH, 0, COL_METRIC);

		assertNull(rule.getLastCondition());
	}

	@Test
	public void theRepeatColumnRendersAsACheckbox()
	{
		assertEquals(Boolean.class, model.getColumnClass(COL_REPEAT));

		model.setValueAt(true, 0, COL_REPEAT);

		assertTrue(rule().isRepeat());
	}

	@Test
	public void aWronglyTypedEditIsRefusedRatherThanCoerced()
	{
		model.setValueAt(NotificationMetric.HIGH, 0, COL_METRIC);
		model.setValueAt("not a window", 0, COL_TIME);
		model.setValueAt("not an operator", 0, COL_OP);

		NotificationRule rule = rule();

		assertEquals(TimeWindow.LIVE, rule.getTimeWindow());
		assertEquals(NotificationOperation.GTE, rule.getOperation());
	}

	@Test
	public void editsOutsideTheRowRangeAreIgnored()
	{
		model.setValueAt(NotificationMetric.HIGH, 5, COL_METRIC);

		assertEquals(0, edits);
	}
}
