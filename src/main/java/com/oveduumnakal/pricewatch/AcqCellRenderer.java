/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.Color;
import java.awt.Component;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Cell renderer for the acquisitions table, coloring the profit column, formatting
 * gp values, and marking estimated sell prices with a {@code ~} prefix and tooltip.
 */
class AcqCellRenderer extends DefaultTableCellRenderer
{
	/** Tooltip shown on sell prices that were estimated rather than observed. */
	static final String ESTIMATED_TOOLTIP =
			"Estimated at the average price when the change was noticed — the actual sale price wasn't observed";

	private final boolean profit;
	private final boolean expanded;
	private final IntSupplier hoverRow;
	private final IntSupplier hoverCol;
	private final IntPredicate sellEstimated;

	AcqCellRenderer(boolean profit, boolean expanded, IntSupplier hoverRow, IntSupplier hoverCol)
	{
		this(profit, expanded, hoverRow, hoverCol, null);
	}

	AcqCellRenderer(boolean profit, boolean expanded, IntSupplier hoverRow, IntSupplier hoverCol,
			IntPredicate sellEstimated)
	{
		this.profit = profit;
		this.expanded = expanded;
		this.hoverRow = hoverRow;
		this.hoverCol = hoverCol;
		this.sellEstimated = sellEstimated;
		setHorizontalAlignment(SwingConstants.CENTER);
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value,
			boolean isSelected, boolean hasFocus, int row, int column)
	{
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		if (!(value instanceof Number))
		{
			setText(value == null ? "" : value.toString());
			if (!isSelected)
			{
				setBackground(table.getBackground());
				setForeground(Color.WHITE);
			}

			return this;
		}

		long v = ((Number) value).longValue();

		boolean shortForm = !expanded && Math.abs(v) >= (profit ? 1000 : 10000);
		String text = shortForm
				? GpFormat.shortValue(v)
				: GpFormat.grouped(v);
		if (profit && v > 0)
			text = "+" + text;

		boolean estimated = sellEstimated != null && sellEstimated.test(row);
		if (estimated)
			text = "~" + text;

		setText(text);
		setToolTipText(estimated ? ESTIMATED_TOOLTIP : null);

		Color fg = profit ? (v > 0 ? PricewatchColors.HIGH : v < 0 ? PricewatchColors.LOW : Color.WHITE) : Color.WHITE;
		setForeground(isSelected ? table.getSelectionForeground() : fg);

		boolean hovered = shortForm && row == hoverRow.getAsInt() && column == hoverCol.getAsInt();
		if (isSelected)
		{
			setBackground(table.getSelectionBackground());
		}
		else if (hovered)
		{
			setForeground(fg);
			setBackground(profit
					? (v >= 0 ? PricewatchColors.TINT_HIGH : PricewatchColors.TINT_LOW)
					: PricewatchColors.TINT_VOLUME);
		}
		else
		{
			setBackground(table.getBackground());
		}

		return this;
	}
}
