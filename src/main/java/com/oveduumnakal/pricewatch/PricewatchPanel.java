/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

import net.runelite.client.ui.PluginPanel;

/**
 * The side panel that will host the watchlist. A placeholder for now: it exists
 * so the navigation button has something to open and so the scaffold can be
 * verified end to end in a running client.
 */
public class PricewatchPanel extends PluginPanel
{
	/**
	 * Builds the placeholder panel.
	 */
	public PricewatchPanel()
	{
		super(false);

		setLayout(new BorderLayout());

		final JLabel placeholder = new JLabel("Pricewatch", SwingConstants.CENTER);

		add(placeholder, BorderLayout.CENTER);
	}
}
