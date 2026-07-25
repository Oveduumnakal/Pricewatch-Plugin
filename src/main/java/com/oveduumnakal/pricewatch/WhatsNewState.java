/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.time.Duration;

/**
 * Decides whether the panel's changelog badge should read "What's New" rather than
 * "Change log".
 *
 * <p>A release is announced for {@link #WINDOW} from the moment the user first launches
 * it, not from its release date, so someone who updates late still gets their week. The
 * announcement also ends the first time they open the changelog window.
 *
 * <p>Pure and client-free — the stored values and the current time are all passed in —
 * so the window arithmetic is unit-testable.
 */
final class WhatsNewState
{
	/** How long after first launching a new release the badge stays highlighted. */
	static final Duration WINDOW = Duration.ofDays(7);

	private WhatsNewState()
	{
	}

	/**
	 * @param current  the changelog's newest version, or {@code null} when it has no entries
	 * @param lastSeen the version stored at the last launch, or {@code null} on a fresh install
	 * @return whether this launch is the first on a new release, and so should restamp the
	 *         first-seen time and re-arm the badge
	 */
	static boolean isNewRelease(String current, String lastSeen)
	{
		return current != null && !current.equals(lastSeen);
	}

	/**
	 * @param current   the changelog's newest version, or {@code null} when it has no entries
	 * @param dismissed whether the changelog window has been opened on this release
	 * @param firstSeen when this release was first launched, or {@code null} when unrecorded
	 * @param now       the current epoch-millisecond
	 * @return whether the badge should be highlighted
	 */
	static boolean highlight(String current, Boolean dismissed, Long firstSeen, long now)
	{
		if (current == null || Boolean.TRUE.equals(dismissed))
			return false;

		if (firstSeen == null)
			return true;

		return now - firstSeen < WINDOW.toMillis();
	}
}
