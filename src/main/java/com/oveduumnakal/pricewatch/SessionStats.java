/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import lombok.Value;

/**
 * Tracks how the tracked portfolio's value has moved since a baseline (captured at
 * login or a manual reset), and splits the change into a <em>price</em> component
 * (the baseline holdings revalued at current prices) and a <em>quantity</em>
 * component (units gained or lost, valued at current prices). The two components
 * sum exactly to the total change, so the tooltip breakdown always reconciles.
 *
 * <p>Session state is deliberately in-memory only — it resets each login and is
 * never persisted.
 */
public final class SessionStats
{
	/** Baseline holdings: item id → {@code {quantity, unitPrice}} at baseline time. */
	private final Map<Integer, long[]> baseline = new HashMap<>();

	private boolean hasBaseline;

	/** @return whether a baseline has been captured this session. */
	public boolean hasBaseline()
	{
		return hasBaseline;
	}

	/** Captures {@code snapshot} (id → {@code {quantity, unitPrice}}) as the new session baseline. */
	public void reset(Map<Integer, long[]> snapshot)
	{
		baseline.clear();
		snapshot.forEach((id, qtyPrice) -> baseline.put(id, new long[]{qtyPrice[0], qtyPrice[1]}));
		hasBaseline = true;
	}

	/** Drops the baseline (e.g. on profile change); the next snapshot re-primes it. */
	public void clear()
	{
		baseline.clear();
		hasBaseline = false;
	}

	/**
	 * Folds any ids present in {@code current} but absent from the baseline into the baseline at
	 * their current {@code {quantity, unitPrice}}, so an item newly tracked mid-session enters at
	 * its current value and contributes zero to {@link #delta} instead of counting its whole
	 * holding as a quantity gain. Its later price and quantity moves still register normally. This
	 * is the mirror of the "dropped item keeps its baseline price" handling in {@link #delta}.
	 *
	 * <p>No-op until a baseline exists (the first snapshot is captured wholesale by {@link #reset}).
	 */
	public void absorbNewItems(Map<Integer, long[]> current)
	{
		if (!hasBaseline)
			return;

		current.forEach((id, qtyPrice) -> baseline.computeIfAbsent(id, k -> new long[]{qtyPrice[0], qtyPrice[1]}));
	}

	/**
	 * Drops one item's baseline entry when it is untracked mid-session, so untracking is
	 * session-neutral instead of reading as the item's whole value lost — the removal-side
	 * mirror of {@link #absorbNewItems}. Genuine quantity losses on items that stay tracked
	 * are unaffected: their ids remain in the baseline.
	 */
	public void removeItem(int itemId)
	{
		baseline.remove(itemId);
	}

	/**
	 * Computes the session change for {@code current} (id → {@code {quantity, unitPrice}})
	 * against the baseline. A dropped item keeps its baseline price, so its loss lands
	 * entirely on the quantity side.
	 *
	 * @return the total delta and its price/quantity split; zeros when no baseline is set
	 */
	public Delta delta(Map<Integer, long[]> current)
	{
		if (!hasBaseline)
			return new Delta(0, 0, 0);

		Set<Integer> ids = new HashSet<>(baseline.keySet());
		ids.addAll(current.keySet());

		long priceEffect = 0;
		long quantityEffect = 0;
		for (int id : ids)
		{
			long[] base = baseline.get(id);
			long[] now = current.get(id);

			long baseQty = base != null ? base[0] : 0;
			long basePrice = base != null ? base[1] : 0;
			long currentQty = now != null ? now[0] : 0;
			long currentPrice = now != null ? now[1] : basePrice;

			priceEffect += baseQty * (currentPrice - basePrice);
			quantityEffect += (currentQty - baseQty) * currentPrice;
		}

		return new Delta(priceEffect + quantityEffect, priceEffect, quantityEffect);
	}

	/** The session change decomposed into total, price movement, and quantity movement (all gp). */
	@Value
	public static class Delta
	{
		long total;

		long price;

		long quantity;
	}
}
