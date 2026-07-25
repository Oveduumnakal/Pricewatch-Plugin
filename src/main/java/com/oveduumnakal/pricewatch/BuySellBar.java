/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;

import net.runelite.client.ui.ColorScheme;

/** Custom-painted horizontal bar split green (buy fraction, left) and red (sell fraction, right). */
final class BuySellBar extends JPanel
{
	private static final Color BAR_GREEN = PricewatchColors.HIGH;
	private static final Color BAR_RED = PricewatchColors.LOW;
	private static final int BAR_H = 5;
	private static final int BAR_ARC = 3;

	/** Buy fraction 0..1, or negative for the "no data" state. */
	private double buyFraction = -1;

	BuySellBar()
	{
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setPreferredSize(new Dimension(220, BAR_H + 4));
		setAlignmentX(Component.LEFT_ALIGNMENT);
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	void setRatio(double buyFraction)
	{
		this.buyFraction = buyFraction;
		repaint();
	}

	/**
	 * Paints the bar, grey when no ratio is known. The green/red split is clipped to
	 * the rounded bar outline so both ends stay cleanly rounded.
	 */
	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = Math.max(1, getWidth());
			int y = 2;

			if (buyFraction < 0)
			{
				g2.setColor(PricewatchColors.DIVIDER);
				g2.fillRoundRect(0, y, w, BAR_H, BAR_ARC, BAR_ARC);
				return;
			}

			g2.setClip(new RoundRectangle2D.Float(0, y, w, BAR_H, BAR_ARC, BAR_ARC));
			int buyW = (int) Math.round(w * Math.max(0, Math.min(1, buyFraction)));
			g2.setColor(BAR_GREEN);
			g2.fillRect(0, y, buyW, BAR_H);
			g2.setColor(BAR_RED);
			g2.fillRect(buyW, y, w - buyW, BAR_H);
		}
		finally
		{
			g2.dispose();
		}
	}
}
