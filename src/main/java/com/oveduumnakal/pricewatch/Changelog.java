/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Value;

/**
 * Parses the bundled {@code changelog.md} resource into an ordered list of
 * releases (newest first). Each release starts with a top-level {@code # <version> - <date>}
 * heading; everything up to the next such heading is that release's markdown body (a
 * Quick Overview, a Detailed Breakdown of features grouped by area, and Bug Fixes).
 * The parser is offline and deterministic; the newest entry's version is treated as the
 * plugin's current version at runtime, and {@code ChangelogGuardTest} enforces that it
 * matches {@code runelite-plugin.properties}.
 */
public final class Changelog
{
	/** Resource path of the bundled changelog, relative to the classpath root. */
	static final String RESOURCE = "/changelog.md";

	/**
	 * A release heading: {@code # 1.4 - July 25 2026}. The {@code (?!#)} keeps it to a single
	 * {@code #} so the body's {@code ##}/{@code ###}/{@code ####} headings aren't release boundaries.
	 */
	private static final Pattern HEADING = Pattern.compile("^#(?!#)\\s+(\\S+)\\s*-\\s*(.*)$");

	private final List<Release> releases;

	private Changelog(List<Release> releases)
	{
		this.releases = releases;
	}

	/** Loads and parses the bundled changelog resource. */
	public static Changelog load()
	{
		try (InputStream in = Changelog.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
				return new Changelog(Collections.emptyList());

			return parse(read(in));
		}
		catch (IOException e)
		{
			return new Changelog(Collections.emptyList());
		}
	}

	/** Parses changelog markdown into releases in document order (expected newest first). */
	public static Changelog parse(String markdown)
	{
		List<Release> releases = new ArrayList<>();
		Release.ReleaseBuilder current = null;
		StringBuilder body = null;

		for (String line : markdown.split("\n", -1))
		{
			Matcher heading = HEADING.matcher(line);
			if (heading.matches())
			{
				if (current != null)
				{
					String text = body.toString().trim();
					releases.add(current.body(text).build());
				}

				current = Release.builder();
				current.version(heading.group(1));
				String date = heading.group(2).trim();
				current.date(date.isEmpty() ? null : date);
				body = new StringBuilder();
				continue;
			}

			if (body != null)
				body.append(line).append('\n');
		}

		if (current != null)
		{
			String text = body.toString().trim();
			releases.add(current.body(text).build());
		}

		return new Changelog(releases);
	}

	/** @return the releases, newest first (document order). */
	public List<Release> releases()
	{
		return Collections.unmodifiableList(releases);
	}

	/** @return the newest release's version, or {@code null} when the changelog is empty. */
	public String currentVersion()
	{
		return releases.isEmpty() ? null : releases.get(0).getVersion();
	}

	/** @return whether a release with exactly {@code version} exists. */
	public boolean hasVersion(String version)
	{
		return releases.stream().anyMatch(r -> r.getVersion().equals(version));
	}

	private static String read(InputStream in) throws IOException
	{
		StringBuilder sb = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
				sb.append(line).append('\n');
		}

		return sb.toString();
	}

	/** One release: its version, written-out date, and the raw markdown body beneath its heading. */
	@Value
	@lombok.Builder
	public static class Release
	{
		String version;

		String date;

		@lombok.Builder.Default
		String body = "";
	}
}
