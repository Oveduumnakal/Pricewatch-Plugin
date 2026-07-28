/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.List;

/**
 * Pure lot math for processing basis transfer (#69): computes what {@code quantity}
 * units about to be consumed are carried at, walking the item's open lots oldest
 * first — the same FIFO order the closure itself will use — so the consumed cost can
 * be transferred onto the produced item's new lots. Units beyond the open lots have
 * no known basis and contribute 0. Client-free and unit-testable.
 */
final class ProcessingBasis
{
	private ProcessingBasis()
	{
	}

	/**
	 * @return the total gp the first {@code quantity} open-lot units are carried at,
	 *         FIFO; units past the open lots contribute 0
	 */
	static long openLotCost(List<AcquisitionRecord> records, int quantity)
	{
		long cost = 0;
		int remaining = quantity;
		for (AcquisitionRecord record : records)
		{
			if (remaining <= 0)
				break;

			if (record.getSoldAt() != null)
				continue;

			int take = Math.min(record.getQuantity(), remaining);
			cost += (long) take * record.getBoughtAt();
			remaining -= take;
		}

		return cost;
	}
}
