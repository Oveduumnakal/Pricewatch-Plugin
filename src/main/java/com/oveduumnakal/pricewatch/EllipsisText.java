/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Arrays;
import javax.swing.JLabel;

/**
 * Ellipsis-truncation for labels: assigns text that is shortened with a trailing
 * ellipsis to fit the label's width, keeps the full text in a tooltip, and
 * re-truncates automatically on resize. Stateless utility.
 */
final class EllipsisText
{
	private static final String ELLIPSIS = "…";

	private static final String FULL_TEXT_KEY = "pricewatch.fullText";

	/**
	 * Assigns text to a label, truncating it with a trailing ellipsis so it fits the
	 * label's available width and exposing the untruncated text as a tooltip. The text
	 * is re-truncated automatically whenever the label is resized.
	 */
	static void set(JLabel label, String fullText)
	{
		label.putClientProperty(FULL_TEXT_KEY, fullText);
		label.setToolTipText(fullText);

		boolean hasListener = Arrays.stream(label.getComponentListeners())
				.anyMatch(l -> l instanceof EllipsisResizeListener);

		if (!hasListener)
			label.addComponentListener(new EllipsisResizeListener());

		apply(label);
	}

	/** Re-truncates a label's stored full text with an ellipsis to fit its current width. */
	static void apply(JLabel label)
	{
		Object stored = label.getClientProperty(FULL_TEXT_KEY);

		if (!(stored instanceof String))
			return;

		String fullText = (String) stored;
		Insets insets = label.getInsets();
		int available = label.getWidth() - insets.left - insets.right;

		if (available <= 0)
		{
			label.setText(fullText);
			return;
		}

		FontMetrics fm = label.getFontMetrics(label.getFont());

		if (fm.stringWidth(fullText) <= available)
		{
			label.setText(fullText);
			return;
		}

		int budget = available - fm.stringWidth(ELLIPSIS);

		if (budget <= 0)
		{
			label.setText(ELLIPSIS);
			return;
		}

		int end = 0;
		int used = 0;

		while (end < fullText.length() && used + fm.charWidth(fullText.charAt(end)) <= budget)
		{
			used += fm.charWidth(fullText.charAt(end));
			end++;
		}

		label.setText(fullText.substring(0, end) + ELLIPSIS);
	}

	/** Re-applies ellipsis truncation to its label whenever the label's width changes. */
	private static final class EllipsisResizeListener extends ComponentAdapter
	{
		@Override
		public void componentResized(ComponentEvent e)
		{
			apply((JLabel) e.getComponent());
		}
	}

	private EllipsisText()
	{
	}
}
