/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Pure, client-free core of source-attributed pricing (#64). Detectors observe
 * game events and register {@linkplain #claim claims} — "I expect a quantity
 * change of this item, from this source, at this unit price" — and the quantity
 * sync {@linkplain #attribute attributes} each detected delta against them.
 * Deltas no claim matches fall back to {@link AcquisitionSource#UNKNOWN}, whose
 * pricing is the caller's legacy policy.
 *
 * <p>Claims expire after a few ticks so a stale expectation can never mis-price
 * an unrelated later change. All operations are O(open claims) with no
 * allocation when idle, keeping the per-tick cost negligible; the class touches
 * no client types, so it is unit-testable in isolation.
 */
class SourceAttributionCore
{
	/** How many ticks a claim stays valid before {@link #expire} discards it. */
	static final int CLAIM_TTL_TICKS = 3;

	/** One registered expectation of a quantity change. */
	private static final class Claim
	{
		final AcquisitionSource source;
		final int itemId;
		final long unitPrice;
		final int expiryTick;
		int quantity;

		Claim(AcquisitionSource source, int itemId, int quantity, long unitPrice, int expiryTick)
		{
			this.source = source;
			this.itemId = itemId;
			this.quantity = quantity;
			this.unitPrice = unitPrice;
			this.expiryTick = expiryTick;
		}
	}

	/** The outcome of attributing one delta: its source and, when known, a unit price. */
	static final class Attribution
	{
		static final Attribution UNKNOWN = new Attribution(AcquisitionSource.UNKNOWN, null);

		private final AcquisitionSource source;
		private final Long unitPrice;

		Attribution(AcquisitionSource source, Long unitPrice)
		{
			this.source = source;
			this.unitPrice = unitPrice;
		}

		AcquisitionSource source()
		{
			return source;
		}

		/** @return the observed unit price, or {@code fallback} when the source didn't carry one. */
		long unitPriceOr(long fallback)
		{
			return unitPrice == null ? fallback : unitPrice;
		}
	}

	private final Deque<Claim> claims = new ArrayDeque<>();

	/**
	 * Registers a detector's expectation that {@code quantity} units of
	 * {@code itemId} are about to change hands at {@code unitPrice} gp each.
	 */
	void claim(AcquisitionSource source, int itemId, int quantity, long unitPrice, int currentTick)
	{
		if (quantity <= 0)
			return;

		claims.addLast(new Claim(source, itemId, quantity, unitPrice, currentTick + CLAIM_TTL_TICKS));
	}

	/**
	 * Attributes a detected quantity change of {@code quantity} units (magnitude,
	 * direction-agnostic) of {@code itemId}, consuming the oldest live matching
	 * claim — partially when the claim is larger than the delta.
	 *
	 * @return the claim's attribution, or {@link Attribution#UNKNOWN} when nothing matches
	 */
	Attribution attribute(int itemId, int quantity, int currentTick)
	{
		if (claims.isEmpty() || quantity <= 0)
			return Attribution.UNKNOWN;

		Iterator<Claim> it = claims.iterator();
		while (it.hasNext())
		{
			Claim c = it.next();
			if (c.itemId != itemId || c.expiryTick < currentTick)
				continue;

			if (c.quantity <= quantity)
				it.remove();
			else
				c.quantity -= quantity;

			return new Attribution(c.source, c.unitPrice);
		}

		return Attribution.UNKNOWN;
	}

	/** Discards expired claims; call once per tick. No-op (and allocation-free) when idle. */
	void expire(int currentTick)
	{
		if (claims.isEmpty())
			return;

		claims.removeIf(c -> c.expiryTick < currentTick);
	}

	/** Drops every open claim (logout, plugin shutdown). */
	void clear()
	{
		claims.clear();
	}
}
