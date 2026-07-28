/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Spot-checks snapshot lookups, charge-variant normalization, and fallbacks of {@link ItemCategoryClassifier}. */
public class ItemCategoryClassifierTest
{
	@Test
	public void classifiesCommonItems()
	{
		assertEquals("Runes", ItemCategoryClassifier.classify("Nature rune"));
		assertEquals("Seeds", ItemCategoryClassifier.classify("Ranarr seed"));
		assertEquals("Potions", ItemCategoryClassifier.classify("Prayer potion(4)"));
		assertEquals("Potions", ItemCategoryClassifier.classify("Weapon poison(++)"));
		assertEquals("Herbs", ItemCategoryClassifier.classify("Grimy ranarr weed"));
		assertEquals("Logs", ItemCategoryClassifier.classify("Magic logs"));
		assertEquals("Ores & Bars", ItemCategoryClassifier.classify("Runite ore"));
		assertEquals("Ores & Bars", ItemCategoryClassifier.classify("Adamantite bar"));
		assertEquals("Ammo", ItemCategoryClassifier.classify("Dragon arrow"));
		assertEquals("Food", ItemCategoryClassifier.classify("Raw shark"));
		assertEquals("Food", ItemCategoryClassifier.classify("Herring"));
		assertEquals("Weapons", ItemCategoryClassifier.classify("Abyssal whip"));
		assertEquals("Armour", ItemCategoryClassifier.classify("Rune platebody"));
		assertEquals("Gems", ItemCategoryClassifier.classify("Uncut diamond"));
		assertEquals("Bones", ItemCategoryClassifier.classify("Dragon bones"));
		assertEquals("Hunter", ItemCategoryClassifier.classify("Red chinchompa"));
	}

	/** Non-GE items are covered because the snapshot is keyed by wiki page name, not GE id. */
	@Test
	public void classifiesNonGeItems()
	{
		assertEquals("Seeds", ItemCategoryClassifier.classify("Pillar frag"));
		assertEquals("Seeds", ItemCategoryClassifier.classify("Elkhorn frag"));
		assertEquals("Seeds", ItemCategoryClassifier.classify("Acorn"));
		assertEquals("Seeds", ItemCategoryClassifier.classify("Mushroom spore"));
	}

	/** Charge variants resolve by stripping the trailing parenthetical back to the base page name. */
	@Test
	public void chargeVariantsResolveToBaseName()
	{
		assertEquals("Jewellery", ItemCategoryClassifier.classify("Ring of dueling(4)"));
		assertEquals("Jewellery", ItemCategoryClassifier.classify("Amulet of glory(6)"));
		assertEquals("Jewellery", ItemCategoryClassifier.classify("Games necklace(8)"));
	}

	@Test
	public void classifiesJewellery()
	{
		assertEquals("Jewellery", ItemCategoryClassifier.classify("Ruby bracelet"));
		assertEquals("Jewellery", ItemCategoryClassifier.classify("Regen bracelet"));
		assertEquals("Jewellery", ItemCategoryClassifier.classify("Brimstone ring"));
		assertEquals("Jewellery", ItemCategoryClassifier.classify("Seers ring"));
	}

	/** Gem-material equipment lands in its equipment bucket, not Gems, via bucket precedence. */
	@Test
	public void gemMaterialEquipmentPrefersEquipmentBucket()
	{
		assertEquals("Weapons", ItemCategoryClassifier.classify("Machete"));
		assertEquals("Weapons", ItemCategoryClassifier.classify("Red topaz machete"));
		assertEquals("Weapons", ItemCategoryClassifier.classify("Opal machete"));
		assertEquals("Ammo", ItemCategoryClassifier.classify("Ruby bolts (e)"));
		assertEquals("Ammo", ItemCategoryClassifier.classify("Diamond dragon bolts"));
		assertEquals("Gems", ItemCategoryClassifier.classify("Opal"));
		assertEquals("Gems", ItemCategoryClassifier.classify("Jade"));
		assertEquals("Gems", ItemCategoryClassifier.classify("Red topaz"));
	}

	/** Robe and bark set pieces classify as Armour from wiki membership, with no name heuristics. */
	@Test
	public void classifiesRobeAndBarkArmour()
	{
		assertEquals("Armour", ItemCategoryClassifier.classify("Bloodbark body"));
		assertEquals("Armour", ItemCategoryClassifier.classify("Bloodbark legs"));
		assertEquals("Armour", ItemCategoryClassifier.classify("Mystic robe top"));
	}

	/** Items the wiki doesn't file under a bucket's categories honestly land in Other. */
	@Test
	public void unmatchedFallsToOther()
	{
		assertEquals("Other", ItemCategoryClassifier.classify("Coins"));
		assertEquals("Other", ItemCategoryClassifier.classify("Clue scroll"));
		assertEquals("Other", ItemCategoryClassifier.classify("Bow string"));
		assertEquals("Other", ItemCategoryClassifier.classify("Pure essence"));
	}

	@Test
	public void nullAndBlankAreOther()
	{
		assertEquals("Other", ItemCategoryClassifier.classify(null));
		assertEquals("Other", ItemCategoryClassifier.classify("   "));
	}
}
