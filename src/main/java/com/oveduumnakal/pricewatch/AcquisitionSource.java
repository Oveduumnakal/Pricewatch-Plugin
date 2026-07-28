/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

/**
 * How a quantity change entered or left the collection, determining how its
 * acquisition lots are priced. {@link #UNKNOWN} is the fallback for changes no
 * detector attributed (mobile/offline resync, unmatched deltas) and the safe
 * default for records persisted before sources existed.
 */
enum AcquisitionSource
{
	/** No detector claimed the change; priced by the fallback (Auto Add) policy. */
	UNKNOWN("Unknown"),

	/** Entered by hand in the acquisitions table. */
	MANUAL("Manual"),

	/** A Grand Exchange offer fill, priced at the actual offer price. */
	GE_TRADE("GE"),

	/** Picked up from or dropped on the ground, priced at 0. */
	GROUND("Ground"),

	/** Gathered from the world via a skill (Hunter, Mining, Fishing, …), priced at 0. */
	GATHER("Gather"),

	/** Claimed from a reward/loot container (raids chest, clue casket, reward pool, …), priced at 0. */
	REWARD("Reward"),

	/** Stolen via Thieving (pickpockets, stalls, chests), priced at 0. */
	THIEVING("Thieving"),

	/** Exchanged in a player trade, priced by apportioning the trade's gp. */
	PLAYER_TRADE("Trade"),

	/** Bought from or sold to an NPC shop, priced from the coins moved. */
	SHOP("Shop"),

	/** Consumed by a High/Low Alchemy cast, priced at the coins received. */
	ALCHEMY("Alchemy"),

	/** Consumed or produced by processing, priced by transferred cost basis. */
	PROCESSING("Processing"),

	/** Swapped between dose ids by decanting a potion; basis follows the liquid, so no profit is realized. */
	DECANT("Decant"),

	/**
	 * Used up — food eaten, ammo fired, a potion dose drunk. A single-use consumable closes at 0
	 * (its cost realizes as a loss); a dose change carries the basis onto the lower-dose id so no
	 * profit is realized just for using a dose.
	 */
	CONSUMED("Consumed"),

	/** Destroyed by processing (burnt food); the input closes as a loss at 0. */
	BURNED("Burned"),

	/** A gem destroyed by a failed cut into gem dust; the uncut gem closes as a loss at 0. */
	CRUSHED("Crushed"),

	/** Lost to or recovered after a death; lots suspend rather than close. */
	DEATH("Death");

	private final String label;

	AcquisitionSource(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
