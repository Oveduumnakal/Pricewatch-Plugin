/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the watchlist row's price line: when it is omitted entirely, which
 * of the unpriced states it falls into, which columns it renders, and how the
 * change indicator tints a figure that moved.
 *
 * <p>Only the pure text logic is covered — building the row itself needs a live
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

	@Test
	public void noneWindowOmitsTheLineEntirely()
	{
		PricewatchPanel.PriceLineOptions off = new PricewatchPanel.PriceLineOptions(
				TimeWindow.NONE, true, true, true, true, PriceIndicatorMode.CHANGE);

		assertNull(PricewatchPanel.priceText(priced(100, 90), off));
	}

	@Test
	public void turningEveryColumnOffAlsoOmitsTheLine()
	{
		PricewatchPanel.PriceLineOptions bare = new PricewatchPanel.PriceLineOptions(
				TimeWindow.LIVE, false, false, false, false, PriceIndicatorMode.CHANGE);

		assertNull(PricewatchPanel.priceText(priced(100, 90), bare));
	}

	@Test
	public void nonTradeableWinsOverAFailedLoad()
	{
		WatchedItem item = item();

		item.setTradeable(false);
		item.setPriceLoadFailed(true);

		assertEquals("Not tradeable", PricewatchPanel.priceText(item, defaults()));
	}

	@Test
	public void failedPriceLoadSaysSo()
	{
		WatchedItem item = item();

		item.setPriceLoadFailed(true);

		assertEquals("No price data", PricewatchPanel.priceText(item, defaults()));
	}

	@Test
	public void tradeableItemAwaitingItsFirstFetchLoads()
	{
		assertEquals("Loading...", PricewatchPanel.priceText(item(), defaults()));
	}

	@Test
	public void defaultLineShowsHighAndLowAbbreviated()
	{
		String line = PricewatchPanel.priceText(priced(1_500_000, 1_450_000), defaults());

		assertTrue(line, line.contains("H 1.5M"));
		assertTrue(line, line.contains("L 1.45M"));
	}

	@Test
	public void columnsCanBeSwitchedOnIndividually()
	{
		WatchedItem item = priced(200, 100);
		PricewatchPanel.PriceLineOptions avgOnly = new PricewatchPanel.PriceLineOptions(
				TimeWindow.LIVE, false, false, true, false, PriceIndicatorMode.OFF);

		item.setAvgPrice(150);

		String line = PricewatchPanel.priceText(item, avgOnly);

		assertTrue(line, line.contains("A 150"));
		assertTrue(line, !line.contains("H "));
		assertTrue(line, !line.contains("L "));
	}

	@Test
	public void latestWindowCarriesNoVolumeSoItRendersADash()
	{
		PricewatchPanel.PriceLineOptions volumeOnly = new PricewatchPanel.PriceLineOptions(
				TimeWindow.LIVE, false, false, false, true, PriceIndicatorMode.OFF);

		String line = PricewatchPanel.priceText(priced(200, 100), volumeOnly);

		assertTrue(line, line.contains("V &mdash;"));
	}

	@Test
	public void aRisingPriceIsTintedGreenAndAFallingOneRed()
	{
		WatchedItem item = priced(200, 100);

		item.setHighDelta(1);
		item.setLowDelta(-1);

		String line = PricewatchPanel.priceText(item, defaults());

		assertTrue(line, line.contains("#28c258"));
		assertTrue(line, line.contains("#e3463f"));
	}

	@Test
	public void indicatorOffLeavesEveryFigureUntinted()
	{
		WatchedItem item = priced(200, 100);
		PricewatchPanel.PriceLineOptions noTint = new PricewatchPanel.PriceLineOptions(
				TimeWindow.LIVE, true, true, false, false, PriceIndicatorMode.OFF);

		item.setHighDelta(1);

		String line = PricewatchPanel.priceText(item, noTint);

		assertTrue(line, !line.contains("<font"));
	}

	@Test
	public void changeModeLeavesAnUnmovedFigureUntintedButAllMarksIt()
	{
		WatchedItem item = priced(200, 100);
		PricewatchPanel.PriceLineOptions all = new PricewatchPanel.PriceLineOptions(
				TimeWindow.LIVE, true, false, false, false, PriceIndicatorMode.ALL);

		assertTrue(PricewatchPanel.priceText(item, defaults()), !PricewatchPanel
				.priceText(item, defaults()).contains("<font"));

		String allLine = PricewatchPanel.priceText(item, all);

		assertTrue(allLine, allLine.contains("#a0a0a0"));
	}

	@Test
	public void averagedWindowsIgnoreTheLatestDeltas()
	{
		WatchedItem item = priced(200, 100);
		PricewatchPanel.PriceLineOptions hourly = new PricewatchPanel.PriceLineOptions(
				TimeWindow.H1, true, false, false, false, PriceIndicatorMode.CHANGE);

		item.setHighDelta(1);
		item.getWindowStats().put(TimeWindow.H1, new PriceStats(500, 400, 450, 900));

		String line = PricewatchPanel.priceText(item, hourly);

		assertTrue(line, line.contains("H 500"));
		assertTrue(line, !line.contains("<font"));
	}
}
