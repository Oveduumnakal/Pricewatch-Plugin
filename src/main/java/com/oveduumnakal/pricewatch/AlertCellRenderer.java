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

/**
 * Cell renderer for the alerts table, centring every cell and applying the panel's
 * colours.
 *
 * <p>The columns are narrow enough that a metric only fits as an abbreviation, so
 * metrics render short with the full name on hover. Everything else renders as-is
 * but still carries its own text as a tooltip, so a value the column truncates
 * stays readable.
 */
class AlertCellRenderer extends DefaultTableCellRenderer
{
	AlertCellRenderer()
	{
		setHorizontalAlignment(SwingConstants.CENTER);
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value,
			boolean isSelected, boolean hasFocus, int row, int column)
	{
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

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
