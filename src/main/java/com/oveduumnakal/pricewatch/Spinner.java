/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.Timer;

import net.runelite.client.ui.ColorScheme;

/** A small indeterminate spinner: an orange arc that rotates while its Swing timer runs. */
final class Spinner extends JComponent
{
	private static final int DIAMETER = 32;

	private final Timer timer;
	private int angle;

	Spinner()
	{
		setPreferredSize(new Dimension(DIAMETER, DIAMETER));
		setMaximumSize(new Dimension(DIAMETER, DIAMETER));
		timer = new Timer(40, e ->
		{
			angle = (angle + 24) % 360;
			repaint();
		});
	}

	void start()
	{
		if (!timer.isRunning())
			timer.start();
	}

	void stop()
	{
		timer.stop();
	}

	/** Paints a grey ring with a rotating orange arc segment. */
	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int size = Math.min(getWidth(), getHeight()) - 6;
		int x = (getWidth() - size) / 2;
		int y = (getHeight() - size) / 2;

		g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
		g2.drawOval(x, y, size, size);
		g2.setColor(ColorScheme.BRAND_ORANGE);
		g2.drawArc(x, y, size, size, angle, 100);

		g2.dispose();
	}
}
