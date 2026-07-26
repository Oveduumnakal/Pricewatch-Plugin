/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.Color;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Tests for the pure decisions behind a watchlist row: which placeholder stands in when
 * there are no figures to draw, which volume a window reports, and how the change
 * indicator recolours a figure that moved.
 *
 * <p>Only that logic is covered — building the row itself needs a live
 * {@code ItemManager} and a Swing toolkit.
 */
public class PricewatchPanelTest
{
	/** @return the default line: latest window, high and low, tint on change. */
	private static PricewatchPanel.PriceLineOptions defaults()
	{
		return new PricewatchPanel.PriceLineOptions(TimeWindow.LIVE, true, true, false, false,
				PriceIndicatorMode.CHANGE);
	}

	/** @return a tradeable watched item with no prices yet. */
	private static WatchedItem item()
	{
		return new WatchedItem(4151, "Abyssal whip");
	}

	/** @return a tradeable watched item priced at the given high and low. */
	private static WatchedItem priced(long high, long low)
	{
		WatchedItem item = item();

		item.setHighPrice(high);
		item.setLowPrice(low);

		return item;
	}

	/** @return a resting colour distinct from every movement tint. */
	private static Color resting()
	{
		return new Color(0x11, 0x22, 0x33);
	}

	@Test
	public void noneWindowLeavesNothingToShow()
	{
		PricewatchPanel.PriceLineOptions off = new PricewatchPanel.PriceLineOptions(
				TimeWindow.NONE, true, true, true, true, PriceIndicatorMode.CHANGE);

		assertEquals("No figures selected", PricewatchPanel.rowStatus(priced(100, 90), off));
	}

	@Test
	public void turningEveryColumnOffLeavesNothingToShow()
	{
		PricewatchPanel.PriceLineOptions bare = new PricewatchPanel.PriceLineOptions(
				TimeWindow.LIVE, false, false, false, false, PriceIndicatorMode.CHANGE);

		assertEquals("No figures selected", PricewatchPanel.rowStatus(priced(100, 90), bare));
	}

	@Test
	public void anUntradeableItemSaysSoInsteadOfShowingFigures()
	{
		WatchedItem item = priced(100, 90);

		item.setTradeable(false);

		assertEquals("Not tradeable", PricewatchPanel.rowStatus(item, defaults()));
	}

	@Test
	public void aFailedPriceLoadSaysSoInsteadOfShowingFigures()
	{
		WatchedItem item = item();

		item.setPriceLoadFailed(true);

		assertEquals("Unable to load price", PricewatchPanel.rowStatus(item, defaults()));
	}

	@Test
	public void anItemAwaitingItsFirstPriceReportsLoading()
	{
		assertEquals("Prices loading...", PricewatchPanel.rowStatus(item(), defaults()));
	}

	@Test
	public void aPricedItemHasNoPlaceholderAtAll()
	{
		assertNull(PricewatchPanel.rowStatus(priced(1_500_000, 1_450_000), defaults()));
	}

	@Test
	public void aNamedWindowReportsItsOwnVolume()
	{
		WatchedItem item = priced(200, 100);

		item.getWindowStats().put(TimeWindow.H1, new PriceStats(200, 100, 150, 4_000));

		assertEquals(4_000, PricewatchPanel.volumeFor(item, TimeWindow.H1));
	}

	@Test
	public void aNamedWindowWithoutStatsReportsNoVolume()
	{
		assertEquals(0, PricewatchPanel.volumeFor(priced(200, 100), TimeWindow.H1));
	}

	/**
	 * The wiki's latest-price endpoint carries no volume, so the default Live line borrows
	 * the widest window that reports one rather than always drawing a dash — which is what
	 * made the Show Volume setting look like it did nothing.
	 */
	@Test
	public void theLiveWindowBorrowsTheWidestReportedVolume()
	{
		WatchedItem item = priced(200, 100);

		item.getWindowStats().put(TimeWindow.H1, new PriceStats(200, 100, 150, 1_000));
		item.getWindowStats().put(TimeWindow.H24, new PriceStats(200, 100, 150, 9_000));

		assertEquals(9_000, PricewatchPanel.volumeFor(item, TimeWindow.LIVE));
	}

	@Test
	public void theLiveWindowFallsPastWindowsReportingNoVolume()
	{
		WatchedItem item = priced(200, 100);

		item.getWindowStats().put(TimeWindow.H24, new PriceStats(200, 100, 150, 0));
		item.getWindowStats().put(TimeWindow.H6, new PriceStats(200, 100, 150, 700));

		assertEquals(700, PricewatchPanel.volumeFor(item, TimeWindow.LIVE));
	}

	@Test
	public void theLiveWindowReportsNoVolumeWhenNoSeriesHasArrived()
	{
		assertEquals(0, PricewatchPanel.volumeFor(priced(200, 100), TimeWindow.LIVE));
	}

	@Test
	public void aFigureThatRoseIsRecolouredUpwards()
	{
		assertEquals(Color.decode("#28c258"), defaults().movementColour(1, resting()));
	}

	@Test
	public void aFigureThatFellIsRecolouredDownwards()
	{
		assertEquals(Color.decode("#e3463f"), defaults().movementColour(-1, resting()));
	}

	@Test
	public void anUnchangedFigureKeepsItsRestingColour()
	{
		Color resting = resting();

		assertSame(resting, defaults().movementColour(0, resting));
	}

	@Test
	public void allModeMarksAnUnchangedFigureGrey()
	{
		PricewatchPanel.PriceLineOptions all = new PricewatchPanel.PriceLineOptions(
				TimeWindow.LIVE, true, false, false, false, PriceIndicatorMode.ALL);

		assertEquals(Color.decode("#a0a0a0"), all.movementColour(0, resting()));
	}

	@Test
	public void theIndicatorSwitchedOffLeavesEvenAMovedFigureResting()
	{
		PricewatchPanel.PriceLineOptions noTint = new PricewatchPanel.PriceLineOptions(
				TimeWindow.LIVE, true, true, false, false, PriceIndicatorMode.OFF);
		Color resting = resting();

		assertSame(resting, noTint.movementColour(1, resting));
	}
}
