/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.Component;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntSupplier;
import javax.swing.ImageIcon;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import net.runelite.client.util.ImageUtil;

/**
 * Renders the collection log's read-only source column as a small PNG glyph — one per
 * {@link AcquisitionSource}, converted from the hand-authored SVGs in {@code icons/source/} — with a
 * faint background tint while the cell is hovered.
 */
class SourceGlyphRenderer extends DefaultTableCellRenderer
{
	/** On-screen glyph size; the source PNGs are authored larger and scaled down for crispness. */
	private static final int SIZE = 18;

	private static final Map<AcquisitionSource, ImageIcon> ICONS = loadIcons();

	private final IntSupplier hoverRow;
	private final IntSupplier hoverCol;

	SourceGlyphRenderer(IntSupplier hoverRow, IntSupplier hoverCol)
	{
		this.hoverRow = hoverRow;
		this.hoverCol = hoverCol;
		setHorizontalAlignment(SwingConstants.CENTER);
	}

	/** Loads and scales each source's PNG once, keyed by source. */
	private static Map<AcquisitionSource, ImageIcon> loadIcons()
	{
		Map<AcquisitionSource, ImageIcon> icons = new EnumMap<>(AcquisitionSource.class);
		icons.put(AcquisitionSource.UNKNOWN, icon("source_unknown.png"));
		icons.put(AcquisitionSource.MANUAL, icon("source_manual.png"));
		icons.put(AcquisitionSource.GE_TRADE, icon("source_ge.png"));
		icons.put(AcquisitionSource.GROUND, icon("source_ground.png"));
		icons.put(AcquisitionSource.GATHER, icon("source_gather.png"));
		icons.put(AcquisitionSource.REWARD, icon("source_reward.png"));
		icons.put(AcquisitionSource.THIEVING, icon("source_thieving.png"));
		icons.put(AcquisitionSource.PLAYER_TRADE, icon("source_trade.png"));
		icons.put(AcquisitionSource.SHOP, icon("source_shop.png"));
		icons.put(AcquisitionSource.ALCHEMY, icon("source_alchemy.png"));
		icons.put(AcquisitionSource.PROCESSING, icon("source_processing.png"));
		icons.put(AcquisitionSource.DECANT, icon("source_decant.png"));
		icons.put(AcquisitionSource.CONSUMED, icon("source_consumed.png"));
		icons.put(AcquisitionSource.BURNED, icon("source_burned.png"));
		icons.put(AcquisitionSource.CRUSHED, icon("source_crushed.png"));
		icons.put(AcquisitionSource.DEATH, icon("source_death.png"));
		return icons;
	}

	private static ImageIcon icon(String resource)
	{
		BufferedImage img = ImageUtil.loadImageResource(SourceGlyphRenderer.class, resource);
		return new ImageIcon(img.getScaledInstance(SIZE, SIZE, Image.SCALE_SMOOTH));
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value,
			boolean isSelected, boolean hasFocus, int row, int column)
	{
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		AcquisitionSource source = value instanceof AcquisitionSource
				? (AcquisitionSource) value
				: AcquisitionSource.UNKNOWN;
		setText("");
		setIcon(ICONS.get(source));
		setToolTipText(source.toString());

		boolean hovered = row == hoverRow.getAsInt() && column == hoverCol.getAsInt();
		if (isSelected)
			setBackground(table.getSelectionBackground());
		else if (hovered)
			setBackground(PricewatchColors.TINT_VOLUME);
		else
			setBackground(table.getBackground());

		return this;
	}
}
