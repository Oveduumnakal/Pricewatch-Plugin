/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Freezes the Release-1.3-era persisted JSON shapes against the current models:
 * legacy data must load unchanged, and every field added later must prove its safe
 * default here. Where {@link PersistedSchemaSnapshotTest} guards the shape, these
 * fixtures guard the <em>values</em> old data deserializes to. The fixture strings
 * are historical artifacts — extend the assertions, never the fixtures.
 */
public class PersistenceCompatTest
{
	private static final Gson GSON = new Gson();

	private static final Type ITEMS_TYPE = new TypeToken<List<PricewatchPlugin.PersistedItem>>(){}.getType();

	/** Two tracked items as Release 1.3 wrote them: lots, a rule, grouping flags. */
	private static final String ITEMS_1_3 = "["
			+ "{\"itemId\":560,\"quantity\":2500,\"costBasisInitialized\":true,"
			+ "\"acquisitions\":[{\"quantity\":2000,\"boughtAt\":95},"
			+ "{\"quantity\":500,\"boughtAt\":90,\"soldAt\":110}],"
			+ "\"notifications\":[{\"metric\":\"HIGH\",\"timeWindow\":\"LIVE\","
			+ "\"operation\":\"GTE\",\"value\":\"120\"}],"
			+ "\"notificationsInitialized\":true,\"favorite\":true,\"onOverlay\":true},"
			+ "{\"itemId\":4151,\"quantity\":1,\"costBasisInitialized\":false,"
			+ "\"acquisitions\":[],\"notifications\":[],\"notificationsInitialized\":false,"
			+ "\"favorite\":false,\"category\":\"Weapons\",\"onOverlay\":false}"
			+ "]";

	/** Category state as Release 1.3 wrote it. */
	private static final String CATEGORIES_1_3 = "{\"categories\":[{\"name\":\"Weapons\",\"collapsed\":false},"
			+ "{\"name\":\"Herbs\",\"collapsed\":true}],\"favoritesCollapsed\":false,\"uncategorizedCollapsed\":true}";

	@Test
	public void legacyTrackedItemsLoadUnchanged()
	{
		List<PricewatchPlugin.PersistedItem> items = GSON.fromJson(ITEMS_1_3, ITEMS_TYPE);
		assertEquals(2, items.size());

		PricewatchPlugin.PersistedItem runes = items.get(0);
		assertEquals(560, runes.itemId);
		assertEquals(2500, runes.quantity);
		assertTrue(runes.costBasisInitialized);
		assertTrue(runes.favorite);
		assertTrue(runes.onOverlay);
		assertNull(runes.category);
		assertEquals(2, runes.acquisitions.size());
		assertEquals(2000, runes.acquisitions.get(0).getQuantity());
		assertEquals(95, runes.acquisitions.get(0).getBoughtAt());
		assertNull(runes.acquisitions.get(0).getSoldAt());
		assertEquals(Long.valueOf(110), runes.acquisitions.get(1).getSoldAt());
		assertEquals(1, runes.notifications.size());
		assertEquals(NotificationMetric.HIGH, runes.notifications.get(0).getMetric());
		assertNull(runes.acquisitions.get(0).getSource());
		assertEquals(AcquisitionSource.UNKNOWN, runes.acquisitions.get(0).sourceOrUnknown());
		assertNull(runes.acquisitions.get(1).getSellSource());
		assertEquals(AcquisitionSource.UNKNOWN, runes.acquisitions.get(1).sellSourceOrUnknown());
		assertTrue(runes.acquisitions.get(1).isSellEstimated());
		assertFalse(runes.acquisitions.get(0).isSellEstimated());

		PricewatchPlugin.PersistedItem whip = items.get(1);
		assertEquals(4151, whip.itemId);
		assertEquals("Weapons", whip.category);
		assertFalse(whip.favorite);
		assertTrue(whip.acquisitions.isEmpty());

		assertEquals(0, runes.deathSuspendedQuantity);
		assertNull(runes.deathSuspendedAt);
	}

	@Test
	public void legacyTrackedItemHasNoSuspendedQuantity()
	{
		TrackedItem legacy = GSON.fromJson(
				"{\"itemId\":560,\"quantity\":100,\"acquisitions\":[]}", TrackedItem.class);
		assertEquals(0, legacy.getSuspendedQuantity());
	}

	@Test
	public void legacyCategoriesLoadUnchanged()
	{
		PricewatchPlugin.CategoryData data = GSON.fromJson(CATEGORIES_1_3, PricewatchPlugin.CategoryData.class);
		assertEquals(2, data.categories.size());
		assertEquals("Weapons", data.categories.get(0).getName());
		assertFalse(data.categories.get(0).isCollapsed());
		assertTrue(data.categories.get(1).isCollapsed());
		assertFalse(data.favoritesCollapsed);
		assertTrue(data.uncategorizedCollapsed);
	}

	@Test
	public void newerJsonWithUnknownFieldsStillLoads()
	{
		String future = "[{\"itemId\":560,\"quantity\":1,\"someFutureField\":\"x\","
				+ "\"acquisitions\":[{\"quantity\":1,\"boughtAt\":5,\"futureSource\":\"GE_TRADE\"}]}]";
		List<PricewatchPlugin.PersistedItem> items = GSON.fromJson(future, ITEMS_TYPE);
		PricewatchPlugin.PersistedItem item = items.get(0);
		assertEquals(560, item.itemId);
		assertEquals(5, item.acquisitions.get(0).getBoughtAt());
	}

	@Test
	public void legacyRoundTripKeepsEveryLegacyKey()
	{
		List<PricewatchPlugin.PersistedItem> items = GSON.fromJson(ITEMS_1_3, ITEMS_TYPE);
		String rewritten = GSON.toJson(items, ITEMS_TYPE);

		Type rawType = new TypeToken<List<Map<String, Object>>>(){}.getType();
		List<Map<String, Object>> raw = GSON.fromJson(rewritten, rawType);
		Map<String, Object> first = raw.get(0);

		for (String key : new String[]{"itemId", "quantity", "costBasisInitialized", "acquisitions",
				"notifications", "notificationsInitialized", "favorite", "onOverlay"})
			assertTrue("missing legacy key: " + key, first.containsKey(key));
	}
}
