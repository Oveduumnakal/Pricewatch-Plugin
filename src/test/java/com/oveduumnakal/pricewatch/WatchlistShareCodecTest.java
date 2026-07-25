/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.Arrays;
import java.util.Collections;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link WatchlistShareCodec}: that a watchlist survives a round trip,
 * that every malformed input is refused rather than throwing, and — most
 * importantly — that a Stockpile code is rejected outright instead of being
 * half-imported.
 */
public class WatchlistShareCodecTest
{
	private final WatchlistShareCodec codec = new WatchlistShareCodec(new Gson());

	/** @return a snapshot with two items and one category. */
	private static WatchlistShareCodec.Snapshot sample()
	{
		return new WatchlistShareCodec.Snapshot(1,
				Arrays.asList(
						new WatchlistShareCodec.Entry(4151, "Weapons", true, false),
						new WatchlistShareCodec.Entry(561, null, false, true)),
				Collections.singletonList(new CategoryState("Weapons", false)));
	}

	@Test
	public void aWatchlistSurvivesTheRoundTrip()
	{
		WatchlistShareCodec.Snapshot decoded = codec.decode(codec.encode(sample()));

		assertNotNull(decoded);
		assertEquals(sample(), decoded);
	}

	@Test
	public void everyEntryFieldSurvivesTheRoundTrip()
	{
		WatchlistShareCodec.Snapshot decoded = codec.decode(codec.encode(sample()));
		WatchlistShareCodec.Entry first = decoded.getItems().get(0);

		assertEquals(4151, first.getId());
		assertEquals("Weapons", first.getCategory());
		assertTrue(first.isFavorite());
		assertEquals(false, first.isOnOverlay());
	}

	@Test
	public void aNullCategorySurvivesAsNull()
	{
		WatchlistShareCodec.Snapshot decoded = codec.decode(codec.encode(sample()));
		WatchlistShareCodec.Entry rune = decoded.getItems().get(1);

		assertNull(rune.getCategory());
	}

	@Test
	public void categoriesSurviveTheRoundTrip()
	{
		WatchlistShareCodec.Snapshot decoded = codec.decode(codec.encode(sample()));

		assertEquals(Collections.singletonList(new CategoryState("Weapons", false)), decoded.getCategories());
	}

	@Test
	public void anEmptyWatchlistRoundTripsToAnEmptyList()
	{
		WatchlistShareCodec.Snapshot empty = new WatchlistShareCodec.Snapshot(
				1, Collections.emptyList(), Collections.emptyList());

		assertEquals(Collections.emptyList(), codec.decode(codec.encode(empty)).getItems());
	}

	@Test
	public void theTokenCarriesThePricewatchPrefix()
	{
		assertTrue(codec.encode(sample()).startsWith("PRCWT1:"));
	}

	@Test
	public void theTokenIsASingleLineSoItPastesIntoChat()
	{
		String token = codec.encode(sample());

		assertEquals(-1, token.indexOf('\n'));
		assertEquals(-1, token.indexOf(' '));
	}

	@Test
	public void rawJsonIsAcceptedForHandEditing()
	{
		String json = new Gson().toJson(sample());

		assertEquals(sample(), codec.decode(json));
	}

	@Test
	public void aStockpileCodeIsRefusedOutright()
	{
		assertNull(codec.decode("STKPL1:H4sIAAAAAAAAAKtWKkstKs7Mz1OyUvJLzE1VqgUAAAD__wMA"));
	}

	@Test
	public void aTokenWithAnUnknownPrefixIsRefused()
	{
		assertNull(codec.decode("SOMETHINGELSE1:H4sIAAAAAAAAAA"));
	}

	@Test
	public void blankAndNullInputsAreRefused()
	{
		assertNull(codec.decode(null));
		assertNull(codec.decode(""));
		assertNull(codec.decode("   "));
	}

	@Test
	public void garbageIsRefusedRatherThanThrowing()
	{
		assertNull(codec.decode("not a code at all"));
		assertNull(codec.decode("PRCWT1:this-is-not-base64!!!"));
		assertNull(codec.decode("PRCWT1:aGVsbG8"));
	}

	/**
	 * JSON with no {@code items} key decodes to a snapshot with an empty list rather
	 * than to {@code null}: the field initialiser runs, because Lombok's no-args
	 * constructor is what Gson uses. Refusing an empty import is the plugin's job,
	 * not the codec's — this pins where that boundary sits.
	 */
	@Test
	public void jsonWithoutAnItemsListDecodesToAnEmptyWatchlist()
	{
		WatchlistShareCodec.Snapshot decoded = codec.decode("{\"v\":1}");

		assertNotNull(decoded);
		assertEquals(Collections.emptyList(), decoded.getItems());
	}

	@Test
	public void surroundingWhitespaceIsTolerated()
	{
		assertEquals(sample(), codec.decode("  " + codec.encode(sample()) + "\n"));
	}
}
