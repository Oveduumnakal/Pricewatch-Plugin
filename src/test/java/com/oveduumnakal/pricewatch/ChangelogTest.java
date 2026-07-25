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

/**
 * Tests for the changelog parser: where one release ends and the next begins, that the
 * body's own headings are not mistaken for release boundaries, and what an empty or
 * headingless changelog reports.
 */
public class ChangelogTest
{
	private static final String TWO_RELEASES =
			"# 0.2 - August 1 2026\n"
			+ "## Quick Overview\n"
			+ "Second release.\n"
			+ "\n"
			+ "# 0.1 - July 25 2026\n"
			+ "## Quick Overview\n"
			+ "First release.\n";

	@Test
	public void splitsReleasesOnTopLevelHeadings()
	{
		List<Changelog.Release> releases = Changelog.parse(TWO_RELEASES).releases();

		assertEquals(2, releases.size());
		assertEquals("0.2", releases.get(0).getVersion());
		assertEquals("August 1 2026", releases.get(0).getDate());
		assertEquals("0.1", releases.get(1).getVersion());
		assertEquals("July 25 2026", releases.get(1).getDate());
	}

	@Test
	public void keepsTheBodyBeneathEachHeading()
	{
		List<Changelog.Release> releases = Changelog.parse(TWO_RELEASES).releases();

		assertEquals("## Quick Overview\nSecond release.", releases.get(0).getBody());
		assertEquals("## Quick Overview\nFirst release.", releases.get(1).getBody());
	}

	@Test
	public void bodyHeadingsAreNotReleaseBoundaries()
	{
		String markdown = "# 0.1 - July 25 2026\n## Section\n### Area\n#### Feature\nText.\n";
		List<Changelog.Release> releases = Changelog.parse(markdown).releases();

		assertEquals(1, releases.size());

		String body = releases.get(0).getBody();

		assertTrue(body.contains("#### Feature"));
	}

	@Test
	public void theNewestVersionIsTheFirstInDocumentOrder()
	{
		Changelog log = Changelog.parse(TWO_RELEASES);

		assertEquals("0.2", log.currentVersion());
		assertTrue(log.hasVersion("0.1"));
		assertFalse(log.hasVersion("0.3"));
	}

	@Test
	public void aChangelogWithNoHeadingsHasNoReleases()
	{
		Changelog log = Changelog.parse("Just some prose with no headings at all.\n");

		assertTrue(log.releases().isEmpty());
		assertNull(log.currentVersion());
		assertFalse(log.hasVersion("0.1"));
	}

	@Test
	public void theBundledChangelogParses()
	{
		List<Changelog.Release> releases = Changelog.load().releases();

		assertFalse(releases.isEmpty());
		assertEquals("0.1", releases.get(releases.size() - 1).getVersion());
	}
}
