/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits a decant's combined input cost across its produced dose lots (#220),
 * dose-weighted so basis follows the liquid: decanting a Potion(3)@100 and a
 * Potion(1)@50 into one Potion(4) carries the full 150gp onto the four-dose lot,
 * and an up-decant leaving a remainder (five doses → a Potion(4) + a Potion(1))
 * splits it 4:1. The allotments sum <em>exactly</em> to the input cost — a flooring
 * remainder is handed to the highest-dose output — so a decant realizes no profit
 * or loss. Client-free and unit-testable.
 */
final class DecantBasis
{
	private DecantBasis()
	{
	}

	/**
	 * @param totalBasis the summed cost of the consumed input lots
	 * @param outputs    each produced dose id paired with its total doses ({@code {id, doses}}), doses &gt; 0
	 * @return each output id mapped to its share of {@code totalBasis}, summing to it exactly
	 */
	static Map<Integer, Long> distribute(long totalBasis, List<int[]> outputs)
	{
		long totalDoses = 0;
		for (int[] output : outputs)
			totalDoses += output[1];

		Map<Integer, Long> shares = new LinkedHashMap<>();
		if (totalDoses <= 0)
		{
			for (int[] output : outputs)
				shares.put(output[0], 0L);

			return shares;
		}

		long allocated = 0;
		for (int[] output : outputs)
		{
			long share = totalBasis * output[1] / totalDoses;
			shares.put(output[0], share);
			allocated += share;
		}

		long leftover = totalBasis - allocated;
		if (leftover != 0)
		{
			int[] top = outputs.get(0);
			for (int[] output : outputs)
				if (output[1] > top[1])
					top = output;

			shares.merge(top[0], leftover, Long::sum);
		}

		return shares;
	}
}
