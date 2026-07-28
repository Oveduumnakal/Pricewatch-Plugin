/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.Color;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JLabel;

/**
 * Mouse listener that swaps a value label to a tinted background (and the full
 * comma-grouped number) while hovered, restoring the compact text on exit.
 */
final class HoverTintListener extends MouseAdapter
{
	private final JLabel label;
	private final String shortText;
	private final String highlightedText;
	private final Color tint;

	HoverTintListener(JLabel label, String shortText, Color tint)
	{
		this.label = label;
		this.shortText = shortText;
		this.tint = tint;
		this.highlightedText = tint == null
				? shortText
				: "<html><nobr><span style='background-color:" + PricewatchColors.toHex(tint) + "'>"
						+ shortText + "</span></nobr></html>";
	}

	@Override
	public void mouseEntered(MouseEvent e)
	{
		if (tint == null)
			return;

		label.setText(highlightedText);
	}

	@Override
	public void mouseExited(MouseEvent e)
	{
		if (tint == null)
			return;

		label.setText(shortText);
	}

	/** Applies the highlighted text immediately if the pointer is already over the label. */
	void applyIfHovered()
	{
		if (tint == null || !label.isShowing() || label.getWidth() == 0)
			return;

		PointerInfo info = MouseInfo.getPointerInfo();
		if (info == null)
			return;

		Point screen = info.getLocation();
		Point origin = label.getLocationOnScreen();
		if (screen.x >= origin.x && screen.x < origin.x + label.getWidth()
				&& screen.y >= origin.y && screen.y < origin.y + label.getHeight())
		{
			label.setText(highlightedText);
		}
	}
}
