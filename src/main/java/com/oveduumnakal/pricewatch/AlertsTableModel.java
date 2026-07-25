/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.OptionalDouble;
import javax.swing.table.AbstractTableModel;

/**
 * Swing table model backing one item's alert rules: a row per {@link NotificationRule},
 * with metric, timeframe, operator, threshold and repeat columns. Editing a cell mutates
 * the rule in place and calls back so the plugin can persist it.
 */
class AlertsTableModel extends AbstractTableModel
{
	private static final String[] COLS = {"Metric", "Time", "Op", "Value", "Rpt"};

	/** The repeat column, rendered as a checkbox and pinned narrow. */
	private static final int COL_REPEAT = 4;

	private final Runnable notifyEdited;

	private WatchedItem item;

	AlertsTableModel(Runnable notifyEdited)
	{
		this.notifyEdited = notifyEdited;
	}

	/**
	 * Points the model at an item's rules.
	 *
	 * @param item the item whose detail card is open, or {@code null} when none is
	 */
	void setItem(WatchedItem item)
	{
		this.item = item;
		fireTableDataChanged();
	}

	/** @return the item whose rules are shown, or {@code null}. */
	WatchedItem getItem()
	{
		return item;
	}

	@Override
	public int getRowCount()
	{
		return item == null ? 0 : item.getNotifications().size();
	}

	@Override
	public int getColumnCount()
	{
		return COLS.length;
	}

	@Override
	public String getColumnName(int c)
	{
		return COLS[c];
	}

	/** @return {@code Boolean} for the repeat column so the table renders and edits it as a checkbox. */
	@Override
	public Class<?> getColumnClass(int c)
	{
		return c == COL_REPEAT ? Boolean.class : Object.class;
	}

	/**
	 * Locks the cells a metric does not get to choose: a categorical rule can only
	 * compare with {@code =}, and the 30-day range is inherently a monthly figure.
	 */
	@Override
	public boolean isCellEditable(int r, int c)
	{
		NotificationRule rule = ruleAt(r);
		if (rule == null)
			return false;

		NotificationMetric metric = rule.getMetric();
		switch (c)
		{
			case 1: return metric == null || !metric.locksTimeframeToMonth();
			case 2: return metric == null || !metric.locksOperationToEquals();
			default: return true;
		}
	}

	@Override
	public Object getValueAt(int r, int c)
	{
		NotificationRule rule = ruleAt(r);
		if (rule == null)
			return "";

		switch (c)
		{
			case 0: return rule.getMetric();
			case 1: return rule.getTimeWindow();
			case 2: return rule.getOperation();
			case 3: return rule.getValue();
			case COL_REPEAT: return rule.isRepeat();
			default: return "";
		}
	}

	@Override
	public void setValueAt(Object value, int r, int c)
	{
		NotificationRule rule = ruleAt(r);
		if (rule == null)
			return;

		switch (c)
		{
			case 0:
				if (!applyMetricEdit(rule, value, r))
					return;

				break;
			case 1:
				if (!(value instanceof TimeWindow))
					return;

				rule.setTimeWindow((TimeWindow) value);
				break;
			case 2:
				if (!(value instanceof NotificationOperation))
					return;

				rule.setOperation((NotificationOperation) value);
				break;
			case 3:
				applyValueEdit(rule, value == null ? "" : value.toString());
				fireTableRowsUpdated(r, r);
				break;
			case COL_REPEAT:
				if (!(value instanceof Boolean))
					return;

				rule.setRepeat((Boolean) value);
				break;
			default:
				return;
		}

		rule.setLastCondition(null);
		notifyEdited.run();
	}

	/**
	 * Switches a rule to a new metric, seeding the other columns with whatever that
	 * metric permits so the row is never left in a state the metric disallows.
	 *
	 * @return whether the metric actually changed
	 */
	private boolean applyMetricEdit(NotificationRule rule, Object value, int row)
	{
		if (!(value instanceof NotificationMetric) || value == rule.getMetric())
			return false;

		NotificationMetric metric = (NotificationMetric) value;

		rule.setMetric(metric);

		if (metric.locksTimeframeToMonth())
			rule.setTimeWindow(TimeWindow.MONTH);
		else if (rule.getTimeWindow() == null)
			rule.setTimeWindow(TimeWindow.LIVE);

		if (metric.locksOperationToEquals())
			rule.setOperation(NotificationOperation.EQ);
		else if (rule.getOperation() == null)
			rule.setOperation(NotificationOperation.GTE);

		rule.setValue(metric.isCategorical() ? metric.getOptions().get(0) : "");
		fireTableRowsUpdated(row, row);

		return true;
	}

	/**
	 * Normalises an edited threshold into the rule: a categorical value is stored as
	 * typed, while numeric and percent input is parsed and written back in canonical
	 * form (so {@code "5000000"} reads back as {@code "5m"}). Unparseable input leaves
	 * the previous threshold alone rather than clearing it.
	 */
	private void applyValueEdit(NotificationRule rule, String raw)
	{
		NotificationMetric metric = rule.getMetric();

		if (metric == null || metric.isCategorical())
		{
			rule.setValue(raw.trim());
			return;
		}

		if (metric.getKind() == NotificationMetric.Kind.PERCENT)
		{
			OptionalDouble percent = NotificationRule.parsePercent(raw);
			if (percent.isPresent())
				rule.setValue(NotificationRule.formatPercent(percent.getAsDouble()));

			return;
		}

		OptionalDouble numeric = NotificationRule.parseNumeric(raw);
		if (numeric.isPresent())
			rule.setValue(GpFormat.shortValue((long) numeric.getAsDouble()));
	}

	/** @return the rule at this row, or {@code null} when the row is out of range. */
	private NotificationRule ruleAt(int r)
	{
		if (item == null || r < 0 || r >= item.getNotifications().size())
			return null;

		return item.getNotifications().get(r);
	}
}
