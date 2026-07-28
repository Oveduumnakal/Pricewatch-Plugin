/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

/** Cell renderer for the notifications table, applying the panel's fonts/colors and centered alignment. */
class NotifCellRenderer extends DefaultTableCellRenderer
{
	NotifCellRenderer()
	{
		setHorizontalAlignment(SwingConstants.CENTER);
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value,
			boolean isSelected, boolean hasFocus, int row, int column)
	{
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

		// Metrics show a terse abbreviation in the narrow row cell with the full name on hover; other
		// values render as-is but still carry a tooltip so anything the column truncates stays readable.
		if (value instanceof NotificationMetric)
		{
			NotificationMetric metric = (NotificationMetric) value;
			setText(metric.getAbbreviation());
			setToolTipText(metric.getDisplayName());
		}
		else
		{
			String text = value == null ? "" : value.toString();
			setText(text);
			setToolTipText(text.isEmpty() ? null : text);
		}

		if (!isSelected)
		{
			setBackground(table.getBackground());
			setForeground(Color.WHITE);
		}

		return this;
	}
}
