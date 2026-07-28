/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure math for player-trade pricing (#66): splits one side's gp across the other
 * side's items proportionally to their market values, yielding a per-unit price for
 * each item leg. Receiving 10M-for-item values that item's lots at 10M; two items
 * for gp split the gp by their (unit value × quantity) weights. Legs with no market
 * value anywhere split the gp evenly per unit, and pure item-for-item trades (no gp)
 * price at 0. Client-free so the split rules are unit-testable in isolation.
 */
final class TradeApportioner
{
	private TradeApportioner()
	{
	}

	/** One non-coin item leg of a trade side: the item, how many, and its unit market value. */
	static final class Leg
	{
		final int itemId;
		final int quantity;
		final long unitValue;

		Leg(int itemId, int quantity, long unitValue)
		{
			this.itemId = itemId;
			this.quantity = quantity;
			this.unitValue = unitValue;
		}
	}

	/**
	 * Splits {@code gp} across {@code legs} proportionally to their total market
	 * values, returning each leg's per-unit price. A zero or negative {@code gp}
	 * prices every leg at 0; legs whose combined market value is 0 split the gp
	 * evenly per unit instead. Integer division truncates — the dust is ignored.
	 *
	 * <p>The proportional multiply is done in {@code double} so a max-cash {@code gp}
	 * against a multi-billion leg value can't overflow {@code long} and wrap to a
	 * negative per-unit price; a double's 53-bit mantissa covers gp magnitudes exactly.
	 *
	 * @return item id → per-unit price in gp
	 */
	static Map<Integer, Long> apportion(List<Leg> legs, long gp)
	{
		Map<Integer, Long> prices = new HashMap<>();
		if (legs.isEmpty())
			return prices;

		if (gp <= 0)
		{
			for (Leg leg : legs)
				prices.put(leg.itemId, 0L);

			return prices;
		}

		long totalValue = 0;
		long totalQuantity = 0;
		for (Leg leg : legs)
		{
			totalValue += leg.unitValue * leg.quantity;
			totalQuantity += leg.quantity;
		}

		for (Leg leg : legs)
		{
			double legValue = (double) leg.unitValue * leg.quantity;
			long share = totalValue > 0
					? (long) ((double) gp * legValue / totalValue)
					: (long) ((double) gp * leg.quantity / totalQuantity);
			prices.put(leg.itemId, share / leg.quantity);
		}

		return prices;
	}
}
