/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Serializes a watchlist (item ids, favourites, categories and overlay flags) to
 * a single compact, shareable token and back.
 *
 * <p>The token is {@link #PREFIX} followed by a URL-safe Base64 encoding of the
 * gzipped JSON, so a whole watchlist pastes as one line into chat. {@link #decode}
 * also accepts raw JSON directly (for hand-editing and debugging), detected by a
 * leading brace. Decoding is defensive: any malformed input yields {@code null}
 * rather than throwing, so the import dialog can report a friendly error.
 *
 * <p>The prefix deliberately differs from the Stockpile plugin's {@code STKPL1:}.
 * A Stockpile code carries quantities and acquisition lots that mean nothing
 * here, and its entries are shaped differently; refusing it outright is far
 * better than silently importing half of it.
 */
public final class WatchlistShareCodec
{
	/** Token marker + format version; a future breaking change bumps the digit. */
	static final String PREFIX = "PRCWT1:";

	private final Gson gson;

	/**
	 * @param gson the client's configured Gson instance
	 */
	public WatchlistShareCodec(Gson gson)
	{
		this.gson = gson;
	}

	/**
	 * @param snapshot the watchlist to share
	 * @return the shareable token, never {@code null}
	 */
	public String encode(Snapshot snapshot)
	{
		String json = gson.toJson(snapshot);

		try
		{
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (GZIPOutputStream gzip = new GZIPOutputStream(bytes))
			{
				gzip.write(json.getBytes(StandardCharsets.UTF_8));
			}

			return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
		}
		catch (IOException e)
		{
			throw new IllegalStateException("In-memory gzip should never fail", e);
		}
	}

	/**
	 * Parses a token (or raw JSON) back into a snapshot.
	 *
	 * @param input the pasted token
	 * @return the decoded snapshot, or {@code null} if the input is blank, not a
	 *         recognisable token, or fails to parse
	 */
	public Snapshot decode(String input)
	{
		if (input == null)
			return null;

		String trimmed = input.trim();
		if (trimmed.isEmpty())
			return null;

		String json;
		if (trimmed.startsWith("{"))
			json = trimmed;
		else
			json = inflate(trimmed);

		if (json == null)
			return null;

		try
		{
			Snapshot snapshot = gson.fromJson(json, Snapshot.class);
			return snapshot != null && snapshot.items != null ? snapshot : null;
		}
		catch (JsonSyntaxException e)
		{
			return null;
		}
	}

	/**
	 * Base64-decodes and gunzips a token body.
	 *
	 * <p>A token carrying a foreign prefix is rejected outright rather than having
	 * the prefix stripped and the remainder attempted: Stockpile's codes are also
	 * gzipped Base64 JSON, so a lenient reader could inflate one and silently
	 * import a mangled half of it.
	 *
	 * @return the JSON, or {@code null} on any corruption or foreign prefix
	 */
	private String inflate(String token)
	{
		int marker = token.indexOf(':');
		if (marker >= 0 && !token.startsWith(PREFIX))
			return null;

		String body = token.startsWith(PREFIX) ? token.substring(PREFIX.length()) : token;

		try
		{
			byte[] compressed = Base64.getUrlDecoder().decode(body);
			try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed)))
			{
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buffer = new byte[4096];
				int read;
				while ((read = gzip.read(buffer)) != -1)
					out.write(buffer, 0, read);

				return new String(out.toByteArray(), StandardCharsets.UTF_8);
			}
		}
		catch (IllegalArgumentException | IOException e)
		{
			return null;
		}
	}

	/** The exported watchlist: the entries plus the category definitions to recreate. */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Snapshot
	{
		/** Format version, for forward-compatible decoding. */
		int v;

		List<Entry> items = new ArrayList<>();

		List<CategoryState> categories = new ArrayList<>();
	}

	/**
	 * One shared watched item: its id, category (nullable), favourite flag and
	 * whether it sits on the on-screen overlay.
	 *
	 * <p>Deliberately carries no quantity and no acquisition lots — there is
	 * nothing here that describes ownership, so a shared watchlist reveals only
	 * which items someone finds interesting.
	 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Entry
	{
		int id;

		String category;

		boolean favorite;

		boolean onOverlay;
	}
}
