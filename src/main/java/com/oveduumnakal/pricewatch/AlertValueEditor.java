/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.Component;
import java.util.function.Supplier;
import javax.swing.AbstractCellEditor;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellEditor;

import net.runelite.client.ui.FontManager;

/**
 * Cell editor for an alert rule's threshold column, which adapts to the metric on
 * that row: a dropdown of the allowed ratings for a categorical metric, or a free-text
 * field for a numeric or percent one.
 *
 * <p>A categorical metric has a closed set of answers, so offering a text field would
 * invite thresholds that can never match.
 */
class AlertValueEditor extends AbstractCellEditor implements TableCellEditor
{
	private final Supplier<WatchedItem> currentItem;
	private final JComboBox<String> combo = new JComboBox<>();
	private final JTextField field = new JTextField();

	private JComponent active;

	AlertValueEditor(Supplier<WatchedItem> currentItem)
	{
		this.currentItem = currentItem;

		combo.setFont(FontManager.getRunescapeSmallFont());
		field.setFont(FontManager.getRunescapeSmallFont());
		field.setHorizontalAlignment(SwingConstants.CENTER);
	}

	@Override
	public Component getTableCellEditorComponent(JTable table, Object value,
			boolean isSelected, int row, int column)
	{
		final NotificationMetric metric = metricAt(row);

		if (metric != null && metric.isCategorical())
		{
			combo.removeAllItems();
			metric.getOptions().forEach(combo::addItem);
			combo.setSelectedItem(value == null ? null : value.toString());
			active = combo;
		}
		else
		{
			field.setText(value == null ? "" : value.toString());
			active = field;
		}

		return active;
	}

	@Override
	public Object getCellEditorValue()
	{
		if (active != combo)
			return field.getText();

		final Object selected = combo.getSelectedItem();

		return selected == null ? "" : selected.toString();
	}

	/** @return the metric on this row, or {@code null} when the row has no rule. */
	private NotificationMetric metricAt(int row)
	{
		final WatchedItem item = currentItem.get();
		if (item == null || row < 0 || row >= item.getNotifications().size())
			return null;

		final NotificationRule rule = item.getNotifications().get(row);

		return rule.getMetric();
	}
}
