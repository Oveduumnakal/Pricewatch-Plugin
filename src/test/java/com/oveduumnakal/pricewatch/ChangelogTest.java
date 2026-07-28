/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Parsing coverage for {@link Changelog}: release headings, per-release bodies, ordering, and edge cases. */
public class ChangelogTest
{
	private static final String SAMPLE = "<!-- maintainer note -->\n\n"
			+ "# 1.4 - July 25 2026\n\n"
			+ "## Quick Overview\n"
			+ "A friendly summary.\n\n"
			+ "## Detailed Breakdown\n"
			+ "### Tracked Item View\n"
			+ "#### Session stats\n"
			+ "Shows value since login.\n"
			+ "[#62](https://example.test/62)\n\n"
			+ "# 1.3 - July 3 2026\n\n"
			+ "## Quick Overview\n"
			+ "An older release.\n";

	@Test
	public void parsesReleasesNewestFirst()
	{
		Changelog log = Changelog.parse(SAMPLE);
		List<Changelog.Release> releases = log.releases();
		assertEquals(2, releases.size());

		Changelog.Release latest = releases.get(0);
		assertEquals("1.4", latest.getVersion());
		assertEquals("July 25 2026", latest.getDate());
		assertTrue(latest.getBody().contains("Session stats"));
	}

	@Test
	public void bodyIsScopedToItsRelease()
	{
		Changelog log = Changelog.parse(SAMPLE);
		Changelog.Release latest = log.releases().get(0);
		Changelog.Release older = log.releases().get(1);

		assertFalse("body must stop at the next release heading", latest.getBody().contains("An older release"));
		assertEquals("1.3", older.getVersion());
		assertEquals("July 3 2026", older.getDate());
		assertTrue(older.getBody().contains("An older release"));
	}

	/** The {@code ##}/{@code ###}/{@code ####} headings inside a body must not split it into extra releases. */
	@Test
	public void bodyHeadingsAreNotReleaseBoundaries()
	{
		Changelog log = Changelog.parse(SAMPLE);
		assertEquals(2, log.releases().size());
	}

	@Test
	public void currentVersionIsTopEntry()
	{
		Changelog log = Changelog.parse(SAMPLE);
		assertEquals("1.4", log.currentVersion());
		assertTrue(log.hasVersion("1.3"));
		assertFalse(log.hasVersion("9.9"));
	}

	@Test
	public void emptyChangelogHasNoCurrentVersion()
	{
		Changelog log = Changelog.parse("<!-- just a comment, no releases -->\n");
		assertTrue(log.releases().isEmpty());
		assertNull(log.currentVersion());
	}
}
