/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the raw, cumulative {@code GrandExchangeOfferChanged} stream into discrete
 * per-slot increments — a placement, an incremental fill, or a cancellation — so the
 * plugin can act on GE activity. Pure and client-free (callers pass the offer's
 * primitive fields), so it is unit-testable without a client.
 *
 * <p>Each slot's {@code quantitySold}/{@code spent} are cumulative, so a fill's real
 * increment is the difference from the last event for that slot. The first event seen
 * per slot after a (re)login seeds a baseline and emits nothing, so an offer's existing
 * progress isn't replayed as fresh fills.
 *
 * <p>Pricewatch consumes only {@code BUY} fills, to count purchases against the 4-hour
 * buy limit. The sell side is still derived — the tracker is copied unchanged — but
 * nothing acts on it, since what you sold is a fact about what you held.
 */
class GeOfferTracker
{
	/** Which side an offer is: a buy adds items on collection, a sell removes them on placement. */
	enum Kind { BUY, SELL }

	/** What a single offer event means to the consumer. */
	enum Type { PLACED, FILL, CANCELLED }

	/** One discrete change derived from an offer event. */
	static final class Event
	{
		final Type type;
		final Kind kind;
		final int itemId;
		final int quantity;
		final long unitPrice;

		Event(Type type, Kind kind, int itemId, int quantity, long unitPrice)
		{
			this.type = type;
			this.kind = kind;
			this.itemId = itemId;
			this.quantity = quantity;
			this.unitPrice = unitPrice;
		}
	}

	/** Cumulative progress last seen for one GE slot, used to compute each event's increment. */
	private static final class SlotState
	{
		int itemId;
		int lastQuantitySold;
		long lastSpent;
	}

	private final Map<Integer, SlotState> slots = new HashMap<>();

	/**
	 * Records an offer event and returns the discrete changes it means, oldest first;
	 * empty when it carries no actionable change (baseline seed, empty slot, or a
	 * no-progress update). A cancellation whose event also advanced the fill counters
	 * emits that final {@code FILL} before the {@code CANCELLED} remainder, so the
	 * filled units realize at their true price instead of being discarded.
	 *
	 * @param buying whether the state is a buy side ({@code BUYING}/{@code BOUGHT}/{@code CANCELLED_BUY})
	 * @param cancelled whether the state is a cancellation ({@code CANCELLED_BUY}/{@code CANCELLED_SELL})
	 * @param empty whether the slot is now empty (offer collected/removed)
	 */
	List<Event> onOffer(int slot, int itemId, boolean buying, boolean cancelled, boolean empty,
			int totalQuantity, int quantitySold, long spent)
	{
		if (empty)
		{
			slots.remove(slot);
			return Collections.emptyList();
		}

		Kind kind = buying ? Kind.BUY : Kind.SELL;
		SlotState state = slots.get(slot);
		if (state == null)
		{
			state = new SlotState();
			state.itemId = itemId;
			state.lastQuantitySold = quantitySold;
			state.lastSpent = spent;
			slots.put(slot, state);

			if (quantitySold == 0 && !cancelled)
				return Collections.singletonList(new Event(Type.PLACED, kind, itemId, totalQuantity, 0));

			return Collections.emptyList();
		}

		int qtyDelta = quantitySold - state.lastQuantitySold;
		long coinsDelta = spent - state.lastSpent;
		state.lastQuantitySold = quantitySold;
		state.lastSpent = spent;

		List<Event> events = new ArrayList<>();
		if (qtyDelta > 0)
			events.add(new Event(Type.FILL, kind, itemId, qtyDelta, coinsDelta / qtyDelta));

		if (cancelled)
		{
			int returned = totalQuantity - quantitySold;
			if (returned > 0)
				events.add(new Event(Type.CANCELLED, kind, itemId, returned, 0));
		}

		return events;
	}

	/**
	 * Records a slot's current cumulative progress as the baseline <em>without</em> emitting an
	 * event, priming an offer that already existed at (re)login so its pre-existing state is not
	 * replayed as a fresh placement or fill by the next {@link #onOffer} for that slot.
	 */
	void seed(int slot, int itemId, int quantitySold, long spent)
	{
		SlotState state = new SlotState();
		state.itemId = itemId;
		state.lastQuantitySold = quantitySold;
		state.lastSpent = spent;
		slots.put(slot, state);
	}

	/** Drops all slot state (logout, plugin shutdown). */
	void clear()
	{
		slots.clear();
	}
}
