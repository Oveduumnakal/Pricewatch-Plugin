/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.swing.JPanel;

import net.runelite.client.ui.ColorScheme;

/**
 * A line chart of total portfolio value over time against a grey cost-basis line.
 * The value line is coloured by its position relative to cost basis — green where
 * it sits above (in profit), red below (in loss), grey when equal — splitting each
 * segment at the crossing point. Mirrors the look and feel of {@link PriceGraphPanel}:
 * a "nice" value axis with horizontal gridlines and right-side labels, rotated date
 * labels along the bottom, a legend, and a hover crosshair whose tooltip reports the
 * value, cost, and profit at the point nearest the cursor.
 *
 * <p>Plots the series from {@link PortfolioHistory} points
 * ({@code {epochSeconds, value, costBasis}}). Consecutive points are always joined,
 * so an offline gap reads as one connecting segment between the two known values.
 */
public final class PortfolioChartPanel extends JPanel
{
	private static final Color VALUE_UP = PricewatchColors.HIGH;

	private static final Color VALUE_DOWN = PricewatchColors.LOW;

	private static final Color VALUE_FLAT = new Color(180, 180, 180);

	private static final Color COST_LINE = new Color(150, 150, 150);

	private static final Color GRID = new Color(70, 70, 70, 90);

	private static final Color AXIS_TEXT = Color.GRAY;

	private static final Color CROSSHAIR = new Color(255, 255, 255, 120);

	private static final Color TOOLTIP_LABEL = new Color(160, 160, 160);

	private static final Color TOOLTIP_VALUE = Color.WHITE;

	private static final Color PROFIT_UP = PricewatchColors.HIGH;

	private static final Color PROFIT_DOWN = PricewatchColors.LOW;

	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);

	private static final DateTimeFormatter DAY_LABEL =
			DateTimeFormatter.ofPattern("d MMM", Locale.US).withZone(ZoneId.systemDefault());

	private static final DateTimeFormatter HOUR_LABEL =
			DateTimeFormatter.ofPattern("ha", Locale.US).withZone(ZoneId.systemDefault());

	private static final DateTimeFormatter TOOLTIP_TIME =
			DateTimeFormatter.ofPattern("MMMM d, hh:mm a", Locale.US).withZone(ZoneId.systemDefault());

	private static final long HOUR = 3600L;

	private static final long DAY = 86_400L;

	private static final int LEFT_PAD = 10;

	private static final int TOP_PAD = 8;

	private static final int X_LABEL_GAP = 12;

	private final Font baseFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);

	private List<long[]> points = Collections.emptyList();

	private int hoverX = -1;

	/** Rasterized static plot (grid, axes, legend, series); only the hover overlay is redrawn on mouse moves. */
	private transient BufferedImage plotCache;

	private boolean plotCacheDirty = true;

	public PortfolioChartPanel()
	{
		setPreferredSize(new Dimension(520, 320));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setFont(baseFont);

		addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				hoverX = e.getX();
				repaint();
			}
		});
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseExited(MouseEvent e)
			{
				hoverX = -1;
				repaint();
			}
		});
	}

	/** Sets the points to plot ({@code {epochSeconds, value, costBasis}}) and repaints. */
	public void setData(List<long[]> data)
	{
		points = data != null ? data : Collections.emptyList();
		plotCacheDirty = true;
		repaint();
	}

	/**
	 * Paints the chart: the expensive static plot (grid, axes, legend, series) is
	 * rasterized once into {@link #plotCache} and reused, while only the lightweight
	 * hover crosshair is redrawn over it on mouse moves. The cheap layout is recomputed
	 * each paint so the hover overlay maps correctly onto the cached pixels.
	 */
	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);

		int width = getWidth();
		int height = getHeight();
		if (width <= 0 || height <= 0)
			return;

		if (points.size() < 2)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setFont(baseFont);
				drawCentered(g2, "Not enough history yet — check back later.", width, height);
			}
			finally
			{
				g2.dispose();
			}

			return;
		}

		FontMetrics fm = getFontMetrics(baseFont);
		int rightAxisWidth = fm.stringWidth("999.9M") + 8;
		int bottomAxisHeight = X_LABEL_GAP + fm.stringWidth("00 Mmm") + 4;
		int legendHeight = fm.getHeight() + 6;

		int plotLeft = LEFT_PAD;
		int plotTop = TOP_PAD + legendHeight;
		int plotRight = width - rightAxisWidth;
		int plotBottom = height - bottomAxisHeight;
		int plotW = Math.max(1, plotRight - plotLeft);
		int plotH = Math.max(1, plotBottom - plotTop);

		long minTime = points.get(0)[0];
		long maxTime = points.get(points.size() - 1)[0];

		long dataMin = Long.MAX_VALUE;
		long dataMax = Long.MIN_VALUE;
		for (long[] p : points)
		{
			dataMin = Math.min(dataMin, p[1]);
			dataMax = Math.max(dataMax, p[1]);
			if (p[2] > 0)
			{
				dataMin = Math.min(dataMin, p[2]);
				dataMax = Math.max(dataMax, p[2]);
			}
		}

		double[] axis = niceAxis(dataMin, dataMax, 5, 9);
		double axisMin = axis[0];
		double axisMax = axis[1];
		int ticks = (int) axis[2];
		double axisRange = Math.max(1, axisMax - axisMin);

		if (plotCache == null || plotCache.getWidth() != width || plotCache.getHeight() != height || plotCacheDirty)
		{
			BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			Graphics2D cg = img.createGraphics();
			try
			{
				cg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				cg.setFont(baseFont);
				boolean anyCost = points.stream().anyMatch(p -> p[2] > 0);

				drawYAxis(cg, fm, plotLeft, plotRight, plotTop, plotBottom, plotH, axisMin, axisRange, ticks);
				drawXAxis(cg, fm, plotLeft, plotBottom, plotW, minTime, maxTime);
				drawLegend(cg, fm, plotLeft, anyCost);
				drawCostLine(cg, plotLeft, plotTop, plotBottom, plotW, plotH,
						minTime, maxTime, axisMin, axisRange);
				drawValueLine(cg, plotLeft, plotTop, plotBottom, plotW, plotH,
						minTime, maxTime, axisMin, axisRange);
			}
			finally
			{
				cg.dispose();
			}

			plotCache = img;
			plotCacheDirty = false;
		}

		g.drawImage(plotCache, 0, 0, null);

		if (hoverX >= plotLeft && hoverX <= plotRight)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setFont(baseFont);
				drawHover(g2, fm, plotLeft, plotTop, plotBottom, plotW, plotH,
						minTime, maxTime, axisMin, axisRange);
			}
			finally
			{
				g2.dispose();
			}
		}
	}

	/** Draws the horizontal gridlines and their right-side value labels for the "nice" value axis. */
	private void drawYAxis(Graphics2D g2, FontMetrics fm, int left, int right, int top, int bottom,
			int plotH, double axisMin, double axisRange, int ticks)
	{
		for (int i = 0; i <= ticks; i++)
		{
			int y = bottom - (int) ((double) plotH * i / ticks);
			g2.setColor(GRID);
			g2.drawLine(left, y, right, y);

			long val = (long) (axisMin + axisRange * i / ticks);
			g2.setColor(AXIS_TEXT);
			g2.drawString(GpFormat.shortValue(val), right + 4, y + fm.getAscent() / 2);
		}
	}

	/** Draws faint vertical gridlines and rotated date labels at "nice" time ticks along the bottom. */
	private void drawXAxis(Graphics2D g2, FontMetrics fm, int left, int bottom, int plotW,
			long minTime, long maxTime)
	{
		double span = Math.max(1, maxTime - minTime);
		boolean dayStep = maxTime - minTime >= 2 * DAY;
		DateTimeFormatter fmt = dayStep ? DAY_LABEL : HOUR_LABEL;

		for (long ts : buildTimeTicks(minTime, maxTime, Math.max(3, Math.min(8, plotW / 60))))
		{
			int x = left + (int) ((ts - minTime) / span * plotW);
			g2.setColor(GRID);
			g2.drawLine(x, bottom, x, bottom - 3);
			g2.setColor(AXIS_TEXT);
			drawVerticalLabel(g2, fmt.format(Instant.ofEpochSecond(ts)), x, bottom + X_LABEL_GAP, fm);
		}
	}

	/**
	 * Draws the legend: with cost basis present, a grey "Cost basis" line and the green
	 * "Profit" / red "Loss" states of the value line; otherwise just a neutral "Value" swatch.
	 */
	private void drawLegend(Graphics2D g2, FontMetrics fm, int left, boolean anyCost)
	{
		int y = TOP_PAD + fm.getAscent();
		int x = left;
		if (anyCost)
		{
			x = drawLegendEntry(g2, fm, x, y, COST_LINE, "Cost basis");
			x = drawLegendEntry(g2, fm, x, y, VALUE_UP, "Profit");
			drawLegendEntry(g2, fm, x, y, VALUE_DOWN, "Loss");
		}
		else
		{
			drawLegendEntry(g2, fm, x, y, VALUE_FLAT, "Value");
		}
	}

	/** Draws one legend swatch + label starting at {@code x}; returns the x just past it. */
	private int drawLegendEntry(Graphics2D g2, FontMetrics fm, int x, int y, Color color, String label)
	{
		g2.setColor(color);
		g2.setStroke(new BasicStroke(2f));
		g2.drawLine(x, y - fm.getAscent() / 2, x + 16, y - fm.getAscent() / 2);

		g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		g2.drawString(label, x + 20, y);
		return x + 20 + fm.stringWidth(label) + 16;
	}

	/** Draws the grey cost-basis line, joining consecutive points and breaking where cost basis is absent. */
	private void drawCostLine(Graphics2D g2, int left, int top, int bottom, int plotW, int plotH,
			long minTime, long maxTime, double axisMin, double axisRange)
	{
		g2.setColor(COST_LINE);
		g2.setStroke(new BasicStroke(1f));

		double timeSpan = Math.max(1, maxTime - minTime);

		int prevX = 0;
		int prevY = 0;
		boolean have = false;
		for (long[] p : points)
		{
			if (p[2] <= 0)
			{
				have = false;
				continue;
			}

			int x = left + (int) ((p[0] - minTime) / timeSpan * plotW);
			int y = valueY(p[2], axisMin, axisRange, top, bottom, plotH);
			if (have)
				g2.drawLine(prevX, prevY, x, y);

			prevX = x;
			prevY = y;
			have = true;
		}
	}

	/**
	 * Draws the value line, colouring each segment by the value's position relative to cost
	 * basis — green above (profit), red below (loss), grey when equal or when no cost basis
	 * exists — and splitting a segment at the point where the two lines cross.
	 */
	private void drawValueLine(Graphics2D g2, int left, int top, int bottom, int plotW, int plotH,
			long minTime, long maxTime, double axisMin, double axisRange)
	{
		g2.setStroke(new BasicStroke(1.6f));
		double timeSpan = Math.max(1, maxTime - minTime);

		for (int i = 0; i < points.size() - 1; i++)
		{
			long[] a = points.get(i);
			long[] b = points.get(i + 1);

			int ax = left + (int) ((a[0] - minTime) / timeSpan * plotW);
			int ay = valueY(a[1], axisMin, axisRange, top, bottom, plotH);
			int bx = left + (int) ((b[0] - minTime) / timeSpan * plotW);
			int by = valueY(b[1], axisMin, axisRange, top, bottom, plotH);

			boolean noBasis = a[2] <= 0 || b[2] <= 0;
			long da = a[1] - a[2];
			long db = b[1] - b[2];

			if (!noBasis && da > 0 != db > 0 && da != 0 && db != 0)
			{
				double t = (double) da / (da - db);
				int cx = ax + (int) Math.round((bx - ax) * t);
				int cy = ay + (int) Math.round((by - ay) * t);
				g2.setColor(diffColor(da));
				g2.drawLine(ax, ay, cx, cy);
				g2.setColor(diffColor(db));
				g2.drawLine(cx, cy, bx, by);
			}
			else
			{
				g2.setColor(noBasis ? VALUE_FLAT : diffColor(da != 0 ? da : db));
				g2.drawLine(ax, ay, bx, by);
			}
		}
	}

	/** @return green when {@code diff} (value − cost) is positive, red when negative, grey when zero. */
	private static Color diffColor(long diff)
	{
		return diff > 0 ? VALUE_UP : diff < 0 ? VALUE_DOWN : VALUE_FLAT;
	}

	/**
	 * Draws the hover overlay: a vertical crosshair at the cursor, dots on the value and
	 * cost lines at the nearest point, and a tooltip box with its date, value, cost, and
	 * unrealized profit.
	 */
	private void drawHover(Graphics2D g2, FontMetrics fm, int plotLeft, int plotTop, int plotBottom,
			int plotW, int plotH, long minTime, long maxTime, double axisMin, double axisRange)
	{
		int idx = closestIndex(plotLeft, plotW, minTime, maxTime);
		if (idx < 0)
			return;

		long[] p = points.get(idx);
		double timeSpan = Math.max(1, maxTime - minTime);
		int x = plotLeft + (int) ((p[0] - minTime) / timeSpan * plotW);

		g2.setStroke(new BasicStroke(1));
		g2.setColor(CROSSHAIR);
		g2.drawLine(x, plotTop, x, plotBottom);

		int valueY = valueY(p[1], axisMin, axisRange, plotTop, plotBottom, plotH);
		g2.setColor(p[2] > 0 ? diffColor(p[1] - p[2]) : VALUE_FLAT);
		g2.fillOval(x - 3, valueY - 3, 6, 6);

		List<TipLine> lines = new ArrayList<>();
		lines.add(new TipLine(TOOLTIP_TIME.format(Instant.ofEpochSecond(p[0])), "", null));
		lines.add(new TipLine("Value:  ", NUMBER_FORMAT.format(p[1]), TOOLTIP_VALUE));
		if (p[2] > 0)
		{
			int costY = valueY(p[2], axisMin, axisRange, plotTop, plotBottom, plotH);
			g2.setColor(COST_LINE);
			g2.fillOval(x - 3, costY - 3, 6, 6);

			lines.add(new TipLine("Cost:   ", NUMBER_FORMAT.format(p[2]), TOOLTIP_VALUE));
			long profit = p[1] - p[2];
			lines.add(new TipLine("Profit: ", (profit >= 0 ? "+" : "") + NUMBER_FORMAT.format(profit),
					profit >= 0 ? PROFIT_UP : PROFIT_DOWN));
		}

		drawTooltip(g2, fm, lines, plotLeft, plotTop, plotLeft + plotW);
	}

	/**
	 * Draws the hover tooltip box, flipping to the cursor's left near the right edge. Each
	 * line's label is drawn muted and its value in the line's own colour, so the numbers
	 * stand out from the labels (and profit reads green/red).
	 */
	private void drawTooltip(Graphics2D g2, FontMetrics fm, List<TipLine> lines,
			int plotLeft, int plotTop, int plotRight)
	{
		int boxW = 0;
		for (TipLine l : lines)
			boxW = Math.max(boxW, fm.stringWidth(l.label + l.value));

		boxW += 8;
		int boxH = lines.size() * (fm.getHeight() + 1) + 4;
		int bx = hoverX + 8;
		if (bx + boxW > plotRight)
			bx = hoverX - 8 - boxW;

		if (bx < plotLeft)
			bx = plotLeft;

		int by = plotTop + 4;
		g2.setColor(new Color(20, 20, 20, 220));
		g2.fillRoundRect(bx, by, boxW, boxH, 6, 6);

		int ty = by + fm.getAscent() + 2;
		for (TipLine l : lines)
		{
			g2.setColor(l.valueColor == null ? Color.WHITE : TOOLTIP_LABEL);
			g2.drawString(l.label, bx + 4, ty);
			if (l.valueColor != null)
			{
				g2.setColor(l.valueColor);
				g2.drawString(l.value, bx + 4 + fm.stringWidth(l.label), ty);
			}

			ty += fm.getHeight() + 1;
		}
	}

	/** One hover-tooltip line: a muted {@code label} and a {@code value} in {@code valueColor} (null = label only). */
	private static final class TipLine
	{
		private final String label;

		private final String value;

		private final Color valueColor;

		private TipLine(String label, String value, Color valueColor)
		{
			this.label = label;
			this.value = value;
			this.valueColor = valueColor;
		}
	}

	/** Maps a value to its y pixel within the plot, clamped to the plot bounds. */
	private static int valueY(long value, double axisMin, double axisRange, int top, int bottom, int plotH)
	{
		int y = bottom - (int) ((value - axisMin) / axisRange * plotH);
		if (y < top)
			return top;

		if (y > bottom)
			return bottom;

		return y;
	}

	/** @return the index of the point whose x pixel is nearest {@link #hoverX}, or -1 if none. */
	private int closestIndex(int plotLeft, int plotW, long minTime, long maxTime)
	{
		double span = Math.max(1, maxTime - minTime);
		int best = -1;
		int bestDx = Integer.MAX_VALUE;
		for (int i = 0; i < points.size(); i++)
		{
			int x = plotLeft + (int) ((points.get(i)[0] - minTime) / span * plotW);
			int dx = Math.abs(x - hoverX);
			if (dx < bestDx)
			{
				bestDx = dx;
				best = i;
			}
		}

		return best;
	}

	/**
	 * Builds evenly spaced time ticks snapped to natural boundaries: whole days for a
	 * span of two days or more (else whole hours), with the step widened so at most
	 * {@code target} ticks fall in the visible range.
	 *
	 * @return tick timestamps in epoch seconds within {@code [minTime, maxTime]}
	 */
	private List<Long> buildTimeTicks(long minTime, long maxTime, int target)
	{
		List<Long> ticks = new ArrayList<>();
		long span = Math.max(1, maxTime - minTime);
		ZoneId zone = ZoneId.systemDefault();

		long stepSec;
		ZonedDateTime cursor;
		if (span >= 2 * DAY)
		{
			long days = Math.max(1, span / DAY);
			long[] candidates = {1, 2, 3, 7, 14, 30, 60, 90};
			long chosen = candidates[candidates.length - 1];
			for (long d : candidates)
				if (days / d <= target)
				{
					chosen = d;
					break;
				}

			stepSec = chosen * DAY;
			cursor = Instant.ofEpochSecond(minTime)
					.atZone(zone)
					.toLocalDate()
					.atStartOfDay(zone);
		}
		else
		{
			long hours = Math.max(1, span / HOUR);
			long[] candidates = {1, 2, 3, 6, 12};
			long chosen = candidates[candidates.length - 1];
			for (long h : candidates)
				if (hours / h <= target)
				{
					chosen = h;
					break;
				}

			stepSec = chosen * HOUR;
			cursor = Instant.ofEpochSecond(minTime)
					.atZone(zone)
					.truncatedTo(ChronoUnit.HOURS);
		}

		long t = cursor.toEpochSecond();
		while (t < minTime)
			t += stepSec;

		while (t <= maxTime)
		{
			ticks.add(t);
			t += stepSec;
		}

		return ticks;
	}

	/** Draws a string rotated 90° (reading bottom-to-top) hanging below the axis at {@code cx}. */
	private void drawVerticalLabel(Graphics2D g2, String s, int cx, int topY, FontMetrics fm)
	{
		Graphics2D gg = (Graphics2D) g2.create();
		try
		{
			gg.translate(cx, topY);
			gg.rotate(-Math.PI / 2);
			gg.drawString(s, -fm.stringWidth(s), fm.getAscent() / 2);
		}
		finally
		{
			gg.dispose();
		}
	}

	private void drawCentered(Graphics2D g2, String text, int width, int height)
	{
		g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		int textWidth = g2.getFontMetrics().stringWidth(text);
		g2.drawString(text, (width - textWidth) / 2, height / 2);
	}

	/**
	 * Picks a human-friendly value axis covering {@code [dataMin, dataMax]} using a
	 * 1/2/2.5/5 step progression so labels land on round numbers.
	 *
	 * @return {@code [axisMin, axisMax, ticks]}
	 */
	private static double[] niceAxis(long dataMin, long dataMax, int minTicks, int maxTicks)
	{
		if (dataMax <= dataMin)
			dataMax = dataMin + 1;

		double range = dataMax - dataMin;
		double[] niceMults = {1, 2, 2.5, 5};
		double bestStep = range / minTicks;
		double chosenStep = -1;
		for (int k = -2; k <= 12 && chosenStep < 0; k++)
		{
			double pow = Math.pow(10, k);
			for (double m : niceMults)
			{
				double step = m * pow;
				if (step <= 0)
					continue;

				double aMin = Math.floor(dataMin / step) * step;
				double aMax = Math.ceil(dataMax / step) * step;
				int count = (int) Math.round((aMax - aMin) / step);
				if (count >= minTicks && count <= maxTicks)
				{
					chosenStep = step;
					break;
				}
			}
		}

		if (chosenStep < 0)
			chosenStep = bestStep;

		double axisMin = Math.floor(dataMin / chosenStep) * chosenStep;
		double axisMax = Math.ceil(dataMax / chosenStep) * chosenStep;
		int ticks = Math.max(1, (int) Math.round((axisMax - axisMin) / chosenStep));
		return new double[]{axisMin, axisMax, ticks};
	}
}
