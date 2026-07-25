/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for the watchlist row's price line: which of the four states an item
 * falls into, and how a priced row is formatted.
 *
 * <p>Only the pure text logic is covered — building the row itself needs a live
 * {@code ItemManager} and a Swing toolkit.
 */
public class PricewatchPanelTest
{
	/** @return a tradeable watched item with no prices yet. */
	private static WatchedItem item()
	{
		return new WatchedItem(4151, "Abyssal whip");
	}

	@Test
	public void nonTradeableItemSaysSo()
	{
		WatchedItem item = item();

		item.setTradeable(false);

		assertEquals("Not tradeable", PricewatchPanel.priceText(item));
	}

	@Test
	public void nonTradeableWinsOverAFailedLoad()
	{
		WatchedItem item = item();

		item.setTradeable(false);
		item.setPriceLoadFailed(true);

		assertEquals("Not tradeable", PricewatchPanel.priceText(item));
	}

	@Test
	public void failedPriceLoadSaysSo()
	{
		WatchedItem item = item();

		item.setPriceLoadFailed(true);

		assertEquals("No price data", PricewatchPanel.priceText(item));
	}

	@Test
	public void tradeableItemAwaitingItsFirstFetchLoads()
	{
		assertEquals("Loading...", PricewatchPanel.priceText(item()));
	}

	@Test
	public void pricedItemShowsBothSidesGrouped()
	{
		WatchedItem item = item();

		item.setHighPrice(1_500_000);
		item.setLowPrice(1_450_000);

		assertEquals("High 1,500,000    Low 1,450,000", PricewatchPanel.priceText(item));
	}

	@Test
	public void oneSidedPriceStillRenders()
	{
		WatchedItem item = item();

		item.setHighPrice(250);

		assertEquals("High 250    Low 0", PricewatchPanel.priceText(item));
	}
}
