/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** Small custom-painted bar showing where the live price sits within its 30-day low/high range. */
final class PriceRangeBar extends JPanel
{
	private static final Color RANGE_RED = PricewatchColors.LOW;
	private static final Color RANGE_GOLD = PricewatchColors.AVG;
	private static final Color RANGE_GREEN = PricewatchColors.HIGH;
	private static final int TRIANGLE_H = 9;
	private static final int BAR_H = 5;
	private static final int BAR_ARC = 3;

	private long min;
	private long max;
	private long live;

	PriceRangeBar()
	{
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setPreferredSize(new Dimension(220, TRIANGLE_H + 2 + BAR_H + 16));
		setAlignmentX(Component.LEFT_ALIGNMENT);
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	void setRange(long min, long max, long live)
	{
		this.min = min;
		this.max = max;
		this.live = live;
		repaint();
	}

	/** @return the linear interpolation between two colours at {@code t} in 0..1. */
	private static Color lerp(Color a, Color b, double t)
	{
		return new Color(
				(int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
				(int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
				(int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
	}

	/** @return the gradient colour at fraction {@code f}: red through gold (0.5) to green. */
	private static Color colorAt(double f)
	{
		f = Math.max(0, Math.min(1, f));
		if (f < 0.5)
			return lerp(RANGE_RED, RANGE_GOLD, f / 0.5);

		return lerp(RANGE_GOLD, RANGE_GREEN, (f - 0.5) / 0.5);
	}

	/**
	 * Paints the red-to-green gradient range bar with min/max labels and a triangle
	 * marker at the live price's position, or a grey "No data" bar when the range is
	 * unknown. The gradient is clipped to the rounded bar outline.
	 */
	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setFont(FontManager.getRunescapeSmallFont());
			FontMetrics fm = g2.getFontMetrics();
			int w = getWidth();
			int barW = Math.max(1, w);
			int barY = TRIANGLE_H + 2;

			boolean hasData = max > min;
			if (!hasData)
			{
				g2.setColor(PricewatchColors.DIVIDER);
				g2.fillRoundRect(0, barY, barW, BAR_H, BAR_ARC, BAR_ARC);
				g2.setColor(Color.GRAY);
				String msg = "No data";
				g2.drawString(msg, (barW - fm.stringWidth(msg)) / 2, barY + BAR_H + fm.getAscent() + 2);
				return;
			}

			Shape oldClip = g2.getClip();
			g2.setClip(new RoundRectangle2D.Double(0, barY, barW, BAR_H, BAR_ARC, BAR_ARC));
			for (int x = 0; x < barW; x++)
			{
				g2.setColor(colorAt((double) x / Math.max(1, barW - 1)));
				g2.drawLine(x, barY, x, barY + BAR_H);
			}

			g2.setClip(oldClip);

			double frac = Math.max(0, Math.min(1, (double) (live - min) / (max - min)));
			int tx = (int) Math.round(frac * (barW - 1));
			g2.setColor(colorAt(frac));
			int[] xs = {tx - 5, tx + 5, tx};
			int[] ys = {0, 0, TRIANGLE_H};
			g2.fillPolygon(xs, ys, 3);

			int labelY = barY + BAR_H + fm.getAscent() + 2;
			g2.setColor(RANGE_RED);
			g2.drawString(GpFormat.grouped(min), 0, labelY);
			g2.setColor(RANGE_GREEN);
			String maxS = GpFormat.grouped(max);
			g2.drawString(maxS, barW - fm.stringWidth(maxS), labelY);
		}
		finally
		{
			g2.dispose();
		}
	}
}
