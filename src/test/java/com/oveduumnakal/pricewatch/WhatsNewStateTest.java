/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the "What's New" badge decision: when a launch counts as a new release, how
 * long the announcement lasts from that launch, and what silences it.
 */
public class WhatsNewStateTest
{
	private static final long NOW = 1_800_000_000_000L;

	private static final long WINDOW = WhatsNewState.WINDOW.toMillis();

	@Test
	public void aFreshInstallIsANewRelease()
	{
		assertTrue(WhatsNewState.isNewRelease("0.1", null));
	}

	@Test
	public void aChangedVersionIsANewRelease()
	{
		assertTrue(WhatsNewState.isNewRelease("0.2", "0.1"));
	}

	@Test
	public void relaunchingTheSameVersionIsNot()
	{
		assertFalse(WhatsNewState.isNewRelease("0.1", "0.1"));
	}

	@Test
	public void anEmptyChangelogIsNeverANewRelease()
	{
		assertFalse(WhatsNewState.isNewRelease(null, "0.1"));
		assertFalse(WhatsNewState.isNewRelease(null, null));
	}

	@Test
	public void anUnstampedReleaseIsAnnounced()
	{
		assertTrue(WhatsNewState.highlight("0.1", null, null, NOW));
	}

	@Test
	public void theAnnouncementLastsAWeekFromFirstLaunch()
	{
		assertTrue(WhatsNewState.highlight("0.1", false, NOW - WINDOW + 1, NOW));
	}

	@Test
	public void theAnnouncementEndsWhenTheWindowElapses()
	{
		assertFalse(WhatsNewState.highlight("0.1", false, NOW - WINDOW, NOW));
		assertFalse(WhatsNewState.highlight("0.1", false, NOW - WINDOW * 2, NOW));
	}

	@Test
	public void openingTheChangelogEndsTheAnnouncementEarly()
	{
		assertFalse(WhatsNewState.highlight("0.1", true, NOW, NOW));
	}

	@Test
	public void anEmptyChangelogIsNeverAnnounced()
	{
		assertFalse(WhatsNewState.highlight(null, false, null, NOW));
	}

	@Test
	public void aClockThatWentBackwardsStillAnnounces()
	{
		assertTrue(WhatsNewState.highlight("0.1", false, NOW + WINDOW, NOW));
	}
}
