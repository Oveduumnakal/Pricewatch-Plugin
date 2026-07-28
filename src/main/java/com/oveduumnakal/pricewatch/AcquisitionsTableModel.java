/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import javax.swing.table.AbstractTableModel;

/**
 * Swing table model backing the editable acquisitions log: one row per
 * {@link AcquisitionRecord} with quantity, buy price, sell price, and derived
 * profit columns. Edits are written straight back to the item's records.
 */
class AcquisitionsTableModel extends AbstractTableModel
{
	private static final String[] COLS_FULL = {"Qty", "Bought", "Sold", "Profit"};
	private static final String[] COLS_NO_PROFIT = {"Qty", "Bought", "Sold"};

	/** Read-only text column showing each lot's acquisition source; only in the expanded view. */
	static final String SOURCE_COL = "Source";

	/** Read-only symbol column showing each lot's acquisition source; the compact view's trailing column. */
	static final String SYMBOL_COL = "";

	private final PricewatchConfig config;
	private final Consumer<Integer> onAcquisitionsEdited;
	private final IntSupplier detailItemId;
	private final boolean expanded;
	private TrackedItem item;

	/** The column set last announced via {@code fireTableStructureChanged}, to skip redundant resets. */
	private String[] lastFiredCols;

	AcquisitionsTableModel(PricewatchConfig config, Consumer<Integer> onAcquisitionsEdited,
			IntSupplier detailItemId, boolean expanded)
	{
		this.config = config;
		this.onAcquisitionsEdited = onAcquisitionsEdited;
		this.detailItemId = detailItemId;
		this.expanded = expanded;
	}

	/**
	 * Swaps the backing item, announcing a full structure reset only when the column
	 * set actually changed — a plain data change keeps the table's layout, column
	 * widths, and renderers, so refresh-in-place doesn't collapse the detail card.
	 */
	void setItem(TrackedItem item)
	{
		this.item = item;

		String[] cols = cols();
		if (Arrays.equals(cols, lastFiredCols))
		{
			fireTableDataChanged();
			return;
		}

		lastFiredCols = cols;
		fireTableStructureChanged();
	}

	/**
	 * @return the active column set: the profit column only when configured, plus a trailing source
	 *         column — a text label in the expanded view, a symbol in the compact view.
	 */
	private String[] cols()
	{
		String[] base = config.showItemProfitRow() ? COLS_FULL : COLS_NO_PROFIT;
		String[] withSource = new String[base.length + 1];
		System.arraycopy(base, 0, withSource, 0, base.length);
		withSource[base.length] = expanded ? SOURCE_COL : SYMBOL_COL;
		return withSource;
	}

	/** @return whether column {@code c} is the expanded view's read-only text source column. */
	private boolean isSourceColumn(int c)
	{
		return expanded && c == getColumnCount() - 1;
	}

	/** @return whether column {@code c} is the compact view's read-only source-symbol column. */
	boolean isSymbolColumn(int c)
	{
		return !expanded && c == getColumnCount() - 1;
	}

	/**
	 * @return the source shown for a lot: for a sold lot how it left the collection
	 *         (the sell source — GE, Alchemy, Shop, …), otherwise how it entered
	 */
	private AcquisitionSource displaySource(AcquisitionRecord rec)
	{
		return rec.getSoldAt() != null ? rec.sellSourceOrUnknown() : rec.sourceOrUnknown();
	}

	/** @return the source label for the lot in {@code row}, for the compact table's tooltip. */
	String sourceLabelAt(int row)
	{
		if (item == null || row < 0 || row >= item.getAcquisitions().size())
			return "";

		return displaySource(item.getAcquisitions().get(row)).toString();
	}

	/** @return whether the lot in {@code row} was closed at an estimated price rather than an observed sale. */
	boolean isSellEstimated(int row)
	{
		if (item == null || row < 0 || row >= item.getAcquisitions().size())
			return false;

		AcquisitionRecord rec = item.getAcquisitions().get(row);
		return rec.isSellEstimated();
	}

	@Override
	public int getRowCount()
	{
		return item == null ? 0 : item.getAcquisitions().size();
	}

	@Override
	public int getColumnCount()
	{
		return cols().length;
	}

	@Override
	public String getColumnName(int c)
	{
		return cols()[c];
	}

	@Override
	public boolean isCellEditable(int r, int c)
	{
		return c < 3;
	}

	/** @return a lot's realised profit, or its unrealised profit at the current low price while unsold. */
	long rowProfit(AcquisitionRecord rec)
	{
		if (rec.getSoldAt() != null)
			return (long) rec.getQuantity() * (rec.getSoldAt() - rec.getBoughtAt());

		if (item != null && item.getLowPrice() > 0)
			return (long) rec.getQuantity() * (item.getLowPrice() - rec.getBoughtAt());

		return 0;
	}

	@Override
	public Object getValueAt(int r, int c)
	{
		AcquisitionRecord rec = item.getAcquisitions().get(r);
		if (isSymbolColumn(c))
			return displaySource(rec);

		if (isSourceColumn(c))
			return displaySource(rec).toString();

		switch (c)
		{
			case 0: return rec.getQuantity();
			case 1: return rec.getBoughtAt();
			case 2: return rec.getSoldAt() == null ? "" : rec.getSoldAt();
			case 3: return rowProfit(rec);
			default: return "";
		}
	}

	@Override
	public void setValueAt(Object value, int r, int c)
	{
		if (item == null || r < 0 || r >= item.getAcquisitions().size())
			return;

		AcquisitionRecord rec = item.getAcquisitions().get(r);
		String s = value == null ? "" : value.toString().trim();
		try
		{
			switch (c)
			{
				case 0:
					rec.setQuantity(Math.max(0, Integer.parseInt(s)));
					rec.setSource(AcquisitionSource.MANUAL);
					break;
				case 1:
					rec.setBoughtAt(Math.max(0, Long.parseLong(s)));
					rec.setSource(AcquisitionSource.MANUAL);
					break;
				case 2:
					if (s.isEmpty())
					{
						rec.setSoldAt(null);
						rec.setSellSource(null);
					}
					else
					{
						rec.setSoldAt(Math.max(0, Long.parseLong(s)));
						rec.setSellSource(AcquisitionSource.MANUAL);
					}

					break;
				default:
					return;
			}

			fireTableRowsUpdated(r, r);
			onAcquisitionsEdited.accept(detailItemId.getAsInt());
		}
		catch (NumberFormatException ex)
		{
			// Ignore unparseable input and keep the prior cell value.
		}
	}
}
