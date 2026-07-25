/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.event.MouseEvent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;

/**
 * Buy/Sell pressure label of the form {@code "55% Buy (550)"} whose short-format volume
 * parenthetical reveals the full number in a tooltip when hovered.
 */
final class PressureVolumeLabel extends JLabel
{
	private long volume = -1;

	PressureVolumeLabel()
	{
		ToolTipManager.sharedInstance().registerComponent(this);
	}

	void setVolume(long volume)
	{
		this.volume = volume;
	}

	/**
	 * Shows the full-volume tooltip only while the pointer is over the parenthetical,
	 * measuring the rendered text with font metrics to find its on-screen extent.
	 */
	@Override
	public String getToolTipText(MouseEvent event)
	{
		if (volume < 0)
			return null;

		String text = getText();
		int open = text.indexOf('(');
		int close = text.indexOf(')');
		if (open < 0 || close <= open)
			return null;

		FontMetrics fm = getFontMetrics(getFont());
		Insets insets = getInsets();
		int avail = getWidth() - insets.left - insets.right;
		int textWidth = fm.stringWidth(text);

		int startX;
		if (getHorizontalAlignment() == SwingConstants.RIGHT)
			startX = insets.left + avail - textWidth;
		else if (getHorizontalAlignment() == SwingConstants.CENTER)
			startX = insets.left + (avail - textWidth) / 2;
		else
			startX = insets.left;

		int parenStart = startX + fm.stringWidth(text.substring(0, open));
		int parenEnd = startX + fm.stringWidth(text.substring(0, close + 1));

		return event.getX() >= parenStart && event.getX() <= parenEnd
				? GpFormat.grouped(volume)
				: null;
	}
}
