/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.Component;
import java.util.Map;
import java.util.function.IntSupplier;
import javax.swing.AbstractCellEditor;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellEditor;

import net.runelite.client.ui.FontManager;

/**
 * Cell editor for a notification rule's value column that adapts to the row's
 * metric: a dropdown of allowed options for categorical metrics, or a free-text
 * field for numeric/percent ones.
 */
class NotificationValueEditor extends AbstractCellEditor implements TableCellEditor
{
	private final Map<Integer, TrackedItem> currentItems;
	private final IntSupplier detailItemId;
	private final JComboBox<String> combo = new JComboBox<>();
	private final JTextField field = new JTextField();
	private JComponent active;

	NotificationValueEditor(Map<Integer, TrackedItem> currentItems, IntSupplier detailItemId)
	{
		this.currentItems = currentItems;
		this.detailItemId = detailItemId;
		combo.setFont(FontManager.getRunescapeSmallFont());
		field.setFont(FontManager.getRunescapeSmallFont());
		field.setHorizontalAlignment(SwingConstants.CENTER);
	}

	@Override
	public Component getTableCellEditorComponent(JTable table, Object value,
			boolean isSelected, int row, int column)
	{
		NotificationMetric metric = null;
		TrackedItem t = currentItems.get(detailItemId.getAsInt());
		if (t != null && row >= 0 && row < t.getNotifications().size())
			metric = t.getNotifications()
					.get(row)
					.getMetric();

		if (metric != null && metric.isCategorical())
		{
			combo.removeAllItems();
			for (String opt : metric.getOptions())
				combo.addItem(opt);

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
		if (active == combo)
		{
			Object sel = combo.getSelectedItem();
			return sel == null ? "" : sel.toString();
		}

		return field.getText();
	}
}
