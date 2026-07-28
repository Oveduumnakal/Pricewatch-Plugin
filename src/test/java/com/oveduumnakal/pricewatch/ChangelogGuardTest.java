/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The build guard for #79: run as part of {@code ./gradlew build} (alongside the
 * style check), it fails the build — locally and in CI — when the bundled changelog
 * has no entry for the current {@code runelite-plugin.properties} version, or when
 * the changelog is malformed. Since bumping that version is the first step of every
 * release, the next build forces the new release's changelog entry to exist.
 */
public class ChangelogGuardTest
{
	private static final Pattern DATE = Pattern.compile("[A-Z][a-z]+ \\d{1,2} \\d{4}");

	private String pluginVersion() throws IOException
	{
		Properties props = new Properties();
		try (FileInputStream in = new FileInputStream("runelite-plugin.properties"))
		{
			props.load(in);
		}

		return props.getProperty("version");
	}

	@Test
	public void currentVersionHasATopChangelogEntry() throws IOException
	{
		String version = pluginVersion();
		assertNotNull("runelite-plugin.properties is missing a version", version);

		Changelog log = Changelog.load();
		assertFalse("changelog.md has no releases", log.releases().isEmpty());
		assertTrue("changelog.md has no entry for version " + version, log.hasVersion(version));
		assertEquals("the newest changelog entry must be the current version " + version,
				version, log.currentVersion());
	}

	@Test
	public void changelogIsWellFormed()
	{
		Changelog log = Changelog.load();
		Set<String> versions = new HashSet<>();

		for (Changelog.Release release : log.releases())
		{
			assertNotNull("a release is missing its version", release.getVersion());
			assertTrue("duplicate release version: " + release.getVersion(), versions.add(release.getVersion()));
			assertNotNull("release " + release.getVersion() + " is missing its date", release.getDate());
			assertTrue("release " + release.getVersion() + " has a malformed date: " + release.getDate(),
					DATE.matcher(release.getDate()).matches());

			assertFalse("release " + release.getVersion() + " has an empty body", release.getBody().isEmpty());
		}
	}
}
