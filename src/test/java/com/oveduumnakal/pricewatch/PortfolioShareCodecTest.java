/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Round-trip, compactness, and defensive-decode coverage for {@link PortfolioShareCodec}. */
public class PortfolioShareCodecTest
{
	private final PortfolioShareCodec codec = new PortfolioShareCodec(new Gson());

	private PortfolioShareCodec.Snapshot sample()
	{
		PortfolioShareCodec.Snapshot s = new PortfolioShareCodec.Snapshot();
		s.setV(1);
		s.setItems(Arrays.asList(
				new PortfolioShareCodec.Entry(560, TrackItemMode.TRACK, "Runes", true),
				new PortfolioShareCodec.Entry(4151, TrackItemMode.TRACK, null, false)));
		s.setCategories(Collections.singletonList(new CategoryState("Runes", false)));
		return s;
	}

	@Test
	public void encodeRoundTrips()
	{
		String token = codec.encode(sample());
		assertTrue("token carries the format prefix", token.startsWith(PortfolioShareCodec.PREFIX));

		PortfolioShareCodec.Snapshot back = codec.decode(token);
		List<PortfolioShareCodec.Entry> items = back.getItems();
		assertEquals(2, items.size());

		PortfolioShareCodec.Entry first = items.get(0);
		assertEquals(560, first.getId());
		assertEquals("Runes", first.getCategory());
		assertTrue(first.isFavorite());
		assertNull(items.get(1).getCategory());

		List<CategoryState> categories = back.getCategories();
		assertEquals("Runes", categories.get(0).getName());
	}

	@Test
	public void decodeAcceptsRawJson()
	{
		String json = new Gson().toJson(sample());
		PortfolioShareCodec.Snapshot back = codec.decode(json);
		assertEquals(2, back.getItems().size());
	}

	@Test
	public void tokenIsSingleLineAndCompactForRealLists()
	{
		PortfolioShareCodec.Snapshot big = new PortfolioShareCodec.Snapshot();
		big.setV(1);
		List<PortfolioShareCodec.Entry> items = new ArrayList<>();
		for (int i = 0; i < 60; i++)
			items.add(new PortfolioShareCodec.Entry(1000 + i, TrackItemMode.TRACK, "Category " + (i % 5), i % 2 == 0));

		big.setItems(items);

		String token = codec.encode(big);
		boolean singleLine = token.indexOf('\n') < 0 && token.indexOf(' ') < 0;
		assertTrue("token is a single paste-able line", singleLine);

		int jsonLength = new Gson().toJson(big).length();
		assertTrue("gzip wins on a realistic watchlist", token.length() < jsonLength);

		PortfolioShareCodec.Snapshot decoded = codec.decode(token);
		assertEquals(60, decoded.getItems().size());
	}

	@Test
	public void malformedInputReturnsNullNotThrow()
	{
		assertNull(codec.decode(null));
		assertNull(codec.decode(""));
		assertNull(codec.decode("   "));
		assertNull(codec.decode("STKPL1:not-valid-base64!!!"));

		java.util.Base64.Encoder encoder = java.util.Base64.getUrlEncoder().withoutPadding();
		String notGzip = encoder.encodeToString("not gzip".getBytes());
		assertNull(codec.decode("STKPL1:" + notGzip));
		assertNull(codec.decode("{ this is : not json }"));
	}
}
