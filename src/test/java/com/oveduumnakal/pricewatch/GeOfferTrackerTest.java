/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link GeOfferTracker}: placement vs. incremental fills vs. cancellation,
 * a cancellation carrying a simultaneous final fill, under-offer buy pricing, the
 * login-replay baseline guard, and slot reset on empty.
 */
public class GeOfferTrackerTest
{
	private final GeOfferTracker tracker = new GeOfferTracker();

	/** Convenience: a buy-side offer event. */
	private List<GeOfferTracker.Event> buy(int slot, int total, int sold, long spent)
	{
		return tracker.onOffer(slot, 560, true, false, false, total, sold, spent);
	}

	/** Convenience: a sell-side offer event. */
	private List<GeOfferTracker.Event> sell(int slot, int total, int sold, long spent)
	{
		return tracker.onOffer(slot, 560, false, false, false, total, sold, spent);
	}

	/** Convenience: the single event an offer change is expected to produce. */
	private GeOfferTracker.Event only(List<GeOfferTracker.Event> events)
	{
		assertEquals(1, events.size());
		return events.get(0);
	}

	@Test
	public void freshSellPlacementEmitsPlaced()
	{
		GeOfferTracker.Event e = only(sell(0, 100, 0, 0));
		assertEquals(GeOfferTracker.Type.PLACED, e.type);
		assertEquals(GeOfferTracker.Kind.SELL, e.kind);
		assertEquals(100, e.quantity);
	}

	@Test
	public void incrementalBuyFillsCarryPerFillPrice()
	{
		buy(0, 100, 0, 0);

		GeOfferTracker.Event first = only(buy(0, 100, 40, 40 * 90L));
		assertEquals(GeOfferTracker.Type.FILL, first.type);
		assertEquals(GeOfferTracker.Kind.BUY, first.kind);
		assertEquals(40, first.quantity);
		assertEquals(90, first.unitPrice);

		GeOfferTracker.Event second = only(buy(0, 100, 100, 40 * 90L + 60 * 80L));
		assertEquals(60, second.quantity);
		assertEquals(80, second.unitPrice);
	}

	@Test
	public void underOfferBuyPriceReflectsActualSpend()
	{
		buy(0, 10, 0, 0);
		GeOfferTracker.Event e = only(buy(0, 10, 10, 10 * 45L));
		assertEquals(45, e.unitPrice);
	}

	@Test
	public void noProgressUpdateEmitsNothing()
	{
		buy(0, 100, 40, 40 * 90L);
		assertTrue(buy(0, 100, 40, 40 * 90L).isEmpty());
	}

	@Test
	public void cancelReportsReturnedQuantity()
	{
		sell(0, 100, 0, 0);
		sell(0, 100, 30, 30 * 120L);

		GeOfferTracker.Event c = only(tracker.onOffer(0, 560, false, true, false, 100, 30, 30 * 120L));
		assertEquals(GeOfferTracker.Type.CANCELLED, c.type);
		assertEquals(70, c.quantity);
	}

	@Test
	public void cancelCarryingAFinalSellFillEmitsTheFillFirst()
	{
		sell(0, 100, 0, 0);
		sell(0, 100, 40, 40 * 120L);

		List<GeOfferTracker.Event> events = tracker.onOffer(0, 560, false, true, false,
				100, 50, 40 * 120L + 10 * 110L);
		assertEquals(2, events.size());

		GeOfferTracker.Event fill = events.get(0);
		assertEquals(GeOfferTracker.Type.FILL, fill.type);
		assertEquals(10, fill.quantity);
		assertEquals(110, fill.unitPrice);

		GeOfferTracker.Event cancel = events.get(1);
		assertEquals(GeOfferTracker.Type.CANCELLED, cancel.type);
		assertEquals(50, cancel.quantity);
	}

	@Test
	public void cancelCarryingAFinalBuyFillEmitsTheFillFirst()
	{
		buy(0, 100, 0, 0);

		List<GeOfferTracker.Event> events = tracker.onOffer(0, 560, true, true, false, 100, 25, 25 * 90L);
		assertEquals(2, events.size());
		assertEquals(GeOfferTracker.Type.FILL, events.get(0).type);
		assertEquals(25, events.get(0).quantity);
		assertEquals(90, events.get(0).unitPrice);
		assertEquals(GeOfferTracker.Type.CANCELLED, events.get(1).type);
		assertEquals(75, events.get(1).quantity);
	}

	@Test
	public void loginReplayOfPartialOfferSeedsBaselineWithoutFill()
	{
		assertTrue(buy(0, 100, 40, 40 * 90L).isEmpty());

		GeOfferTracker.Event next = only(buy(0, 100, 70, 40 * 90L + 30 * 90L));
		assertEquals(GeOfferTracker.Type.FILL, next.type);
		assertEquals(30, next.quantity);
	}

	@Test
	public void emptyResetsSlotSoReuseStartsFresh()
	{
		buy(0, 100, 100, 100 * 90L);
		assertTrue(tracker.onOffer(0, 560, true, false, true, 0, 0, 0).isEmpty());

		GeOfferTracker.Event placed = only(sell(0, 50, 0, 0));
		assertEquals(GeOfferTracker.Type.PLACED, placed.type);
		assertEquals(50, placed.quantity);
	}

	@Test
	public void seededSellOfferDoesNotReplayAsPlacementAndStillCancels()
	{
		tracker.seed(0, 560, 0, 0);

		assertTrue(sell(0, 10, 0, 0).isEmpty());

		GeOfferTracker.Event cancel = only(tracker.onOffer(0, 560, false, true, false, 10, 0, 0));
		assertEquals(GeOfferTracker.Type.CANCELLED, cancel.type);
		assertEquals(GeOfferTracker.Kind.SELL, cancel.kind);
		assertEquals(10, cancel.quantity);
	}
}
