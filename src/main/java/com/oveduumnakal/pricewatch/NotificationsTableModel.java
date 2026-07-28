/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.OptionalDouble;
import javax.swing.table.AbstractTableModel;

/**
 * Swing table model backing the notification rules: one row per
 * {@link NotificationRule} with metric, timeframe, operator, and value columns.
 * Editing a cell mutates the rule and notifies the plugin to persist it.
 */
class NotificationsTableModel extends AbstractTableModel
{
	private static final String[] COLS = {"Metric", "Time", "Op", "Value", "↻"};

	private final Runnable notifyEdited;
	private TrackedItem item;

	NotificationsTableModel(Runnable notifyEdited)
	{
		this.notifyEdited = notifyEdited;
	}

	void setItem(TrackedItem item)
	{
		this.item = item;
		fireTableStructureChanged();
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

	/** @return {@code Boolean} for the repeat column so the table renders/edits it as a checkbox. */
	@Override
	public Class<?> getColumnClass(int c)
	{
		return c == 4 ? Boolean.class : Object.class;
	}

	@Override
	public boolean isCellEditable(int r, int c)
	{
		if (item == null || r < 0 || r >= item.getNotifications().size())
			return false;

		NotificationMetric m = item.getNotifications()
				.get(r)
				.getMetric();
		switch (c)
		{
			case 1: return m == null || (!m.isTimeframeDisabled() && !m.locksTimeframeToMonth());
			case 2: return m == null || !m.locksOperationToEquals();
			default: return true;
		}
	}

	@Override
	public Object getValueAt(int r, int c)
	{
		NotificationRule rule = item.getNotifications().get(r);
		NotificationMetric m = rule.getMetric();
		switch (c)
		{
			case 0: return m;
			case 1: return m != null && m.isTimeframeDisabled() ? "—" : rule.getTimeWindow();
			case 2: return rule.getOperation();
			case 3: return rule.getValue();
			case 4: return rule.isRepeat();
			default: return "";
		}
	}

	@Override
	public void setValueAt(Object value, int r, int c)
	{
		if (item == null || r < 0 || r >= item.getNotifications().size())
			return;

		NotificationRule rule = item.getNotifications().get(r);
		switch (c)
		{
			case 0:
				if (!(value instanceof NotificationMetric) || value == rule.getMetric())
					return;

				NotificationMetric m = (NotificationMetric) value;
				rule.setMetric(m);

				if (m.locksTimeframeToMonth())
					rule.setTimeWindow(TimeWindow.MONTH);
				else if (m.isTimeframeDisabled())
					rule.setTimeWindow(null);
				else if (rule.getTimeWindow() == null)
					rule.setTimeWindow(TimeWindow.LIVE);

				if (m.locksOperationToEquals())
					rule.setOperation(NotificationOperation.EQ);
				else if (rule.getOperation() == null)
					rule.setOperation(NotificationOperation.GTE);

				rule.setValue(m.isCategorical() ? m.getOptions().get(0) : "");
				fireTableRowsUpdated(r, r);
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
			case 4:
				if (!(value instanceof Boolean))
					return;

				rule.setRepeat((Boolean) value);
				break;
			default:
				return;
		}

		notifyEdited.run();
	}

	/**
	 * Normalises an edited value into the rule: categorical values are stored as
	 * typed, while percent and numeric inputs are parsed and reformatted
	 * (e.g. {@code "5000000"} &rarr; {@code "5m"}), ignored when unparseable.
	 */
	private void applyValueEdit(NotificationRule rule, String raw)
	{
		NotificationMetric m = rule.getMetric();

		if (m == null || m.isCategorical())
		{
			rule.setValue(raw.trim());
			return;
		}

		if (m.getKind() == NotificationMetric.Kind.PERCENT)
		{
			OptionalDouble v = NotificationRule.parsePercent(raw);
			if (v.isPresent())
				rule.setValue(NotificationRule.formatPercent(v.getAsDouble()));

			return;
		}

		OptionalDouble v = NotificationRule.parseNumeric(raw);
		if (v.isPresent())
			rule.setValue(GpFormat.shortValue((long) v.getAsDouble()));
	}
}
