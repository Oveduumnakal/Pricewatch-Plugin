/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the changelog's markdown-to-HTML rendering: that each heading depth gets its
 * own style, that issue links become anchors, that HTML in the source is escaped rather
 * than rendered, and that the anchors the navigation column jumps to match the ones the
 * body emits.
 */
public class ChangelogHtmlTest
{
	/** @return a release carrying the given markdown body. */
	private static Changelog.Release release(String body)
	{
		return Changelog.Release.builder()
				.version("0.1")
				.date("July 25 2026")
				.body(body)
				.build();
	}

	@Test
	public void rendersTheVersionAndDateHeading()
	{
		String html = ChangelogHtml.render(release("Text."));

		assertTrue(html.contains("0.1"));
		assertTrue(html.contains("July 25 2026"));
	}

	@Test
	public void eachHeadingDepthGetsItsOwnStyle()
	{
		String html = ChangelogHtml.render(release("## Section\n### Area\n#### Feature\nText.\n"));

		assertTrue(html.contains("font-size:14px;font-weight:bold;color:#ffffff"));
		assertTrue(html.contains("font-size:13px;font-weight:bold;color:#e0a54a"));
		assertTrue(html.contains("font-weight:bold;color:#d4d4d4"));
		assertTrue(html.contains("color:#9a9a9a"));
	}

	@Test
	public void contentIndentsBeneathTheHeadingItFollows()
	{
		String html = ChangelogHtml.render(release("## Section\nUnder section.\n"));

		assertTrue(html.contains("margin-left:12px"));
	}

	@Test
	public void issueLinksBecomeAnchors()
	{
		String html = ChangelogHtml.render(
				release("Fixed a thing. [#12](https://example.invalid/issues/12)\n"));

		assertTrue(html.contains("<a href='https://example.invalid/issues/12'>#12</a>"));
	}

	@Test
	public void markupInTheSourceIsEscaped()
	{
		String html = ChangelogHtml.render(release("Use <b>bold</b> & such.\n"));

		assertTrue(html.contains("&lt;b&gt;bold&lt;/b&gt; &amp; such."));
		assertFalse(html.contains("<b>bold</b>"));
	}

	@Test
	public void blankLinesAreDropped()
	{
		String html = ChangelogHtml.render(release("One.\n\n\nTwo.\n"));

		assertEquals(2, html.split("color:#9a9a9a", -1).length - 1);
	}

	@Test
	public void sectionsListTheNavigableHeadingsWithTheirDepth()
	{
		List<ChangelogHtml.Section> sections =
				ChangelogHtml.sections("## Section\nText.\n### Area\n#### Feature\n");

		assertEquals(2, sections.size());
		assertEquals(0, sections.get(0).getLevel());
		assertEquals("Section", sections.get(0).getText());
		assertEquals(1, sections.get(1).getLevel());
		assertEquals("Area", sections.get(1).getText());
	}

	@Test
	public void navAnchorsMatchTheOnesTheBodyEmits()
	{
		String body = "## Section\nText.\n### Area\nMore text.\n";
		String html = ChangelogHtml.render(release(body));

		for (ChangelogHtml.Section section : ChangelogHtml.sections(body))
			assertTrue("body has no anchor " + section.getAnchor(),
					html.contains("<a name='" + section.getAnchor() + "'>"));
	}

	@Test
	public void fourthLevelHeadingsAreNotNavigable()
	{
		assertTrue(ChangelogHtml.sections("#### Feature\nText.\n").isEmpty());
	}
}
