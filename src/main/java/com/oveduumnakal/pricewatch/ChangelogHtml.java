/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Value;

/**
 * Renders a {@link Changelog.Release} to the HTML the changelog window displays, and
 * lists the sections its navigation column links to.
 *
 * <p>Deliberately minimal markdown: {@code ##}/{@code ###}/{@code ####} headings become
 * sized, weighted and coloured headers that each indent a level deeper, their content
 * indents a level further still, and {@code [#12](url)} becomes a clickable anchor.
 * Nothing else is supported, because Swing's renderer is HTML-3.2-era and the bundled
 * changelog uses nothing else.
 *
 * <p>Pure and Swing-free so both the rendering and the anchor numbering can be tested
 * without a client.
 */
final class ChangelogHtml
{
	private static final String SECTION_STYLE = "font-size:14px;font-weight:bold;color:#ffffff;margin-top:14px;";

	private static final String AREA_STYLE = "font-size:13px;font-weight:bold;color:#e0a54a;margin-top:12px;";

	private static final String FEATURE_STYLE = "font-weight:bold;color:#d4d4d4;margin-top:8px;";

	private static final String TEXT_STYLE = "color:#9a9a9a;margin-top:2px;";

	/** Pixels of left indent added per nesting level. */
	private static final int INDENT_STEP = 12;

	/** A markdown link {@code [label](url)}, used for the changelog's issue references. */
	private static final Pattern MD_LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");

	private ChangelogHtml()
	{
	}

	/**
	 * @param release the release to render
	 * @return the release's version and date heading followed by its rendered body
	 */
	static String render(Changelog.Release release)
	{
		StringBuilder sb = new StringBuilder("<html><body style='font-family:sans-serif; margin:4px 8px;'>");

		sb.append("<div style='font-size:15px; font-weight:bold;'>");
		sb.append(escape(release.getVersion()));

		if (release.getDate() != null)
		{
			sb.append(" <span style='color:gray; font-weight:normal; font-size:10px;'>");
			sb.append(escape(release.getDate()));
			sb.append("</span>");
		}

		sb.append("</div>");
		sb.append(renderBody(release.getBody()));
		sb.append("</body></html>");

		return sb.toString();
	}

	/**
	 * @param body a release's markdown body
	 * @return its {@code ##} and {@code ###} headings in order, each with the scroll
	 *         anchor {@link #render} emits for it
	 */
	static List<Section> sections(String body)
	{
		List<Section> sections = new ArrayList<>();
		int index = 0;

		for (String raw : body.split("\n", -1))
		{
			String line = raw.trim();
			if (line.startsWith("### "))
			{
				sections.add(new Section(1, line.substring(4), anchor(index)));
				index++;
			}
			else if (line.startsWith("## "))
			{
				sections.add(new Section(0, line.substring(3), anchor(index)));
				index++;
			}
		}

		return sections;
	}

	/** @return the markdown body as HTML divs, with an anchor before each navigable heading. */
	private static String renderBody(String body)
	{
		StringBuilder sb = new StringBuilder();
		int contentLevel = 0;
		int sectionIndex = 0;

		for (String raw : body.split("\n", -1))
		{
			String line = raw.trim();
			if (line.isEmpty())
				continue;

			if (line.startsWith("#### "))
			{
				appendDiv(sb, FEATURE_STYLE, 2, inlineLinks(line.substring(5)));
				contentLevel = 3;
			}
			else if (line.startsWith("### "))
			{
				appendAnchor(sb, sectionIndex);
				sectionIndex++;
				appendDiv(sb, AREA_STYLE, 1, inlineLinks(line.substring(4)));
				contentLevel = 2;
			}
			else if (line.startsWith("## "))
			{
				appendAnchor(sb, sectionIndex);
				sectionIndex++;
				appendDiv(sb, SECTION_STYLE, 0, inlineLinks(line.substring(3)));
				contentLevel = 1;
			}
			else
			{
				appendDiv(sb, TEXT_STYLE, contentLevel, inlineLinks(line));
			}
		}

		return sb.toString();
	}

	/** @return the scroll-anchor name for the section at {@code index}. */
	private static String anchor(int index)
	{
		return "sec" + index;
	}

	/** Appends a named scroll anchor matching the ids {@link #sections} hands the navigation column. */
	private static void appendAnchor(StringBuilder sb, int sectionIndex)
	{
		sb.append("<a name='");
		sb.append(anchor(sectionIndex));
		sb.append("'></a>");
	}

	/** Appends a {@code <div>} carrying the given inline CSS and left indent, wrapping {@code html}. */
	private static void appendDiv(StringBuilder sb, String style, int indentLevel, String html)
	{
		sb.append("<div style='");
		sb.append(style);

		if (indentLevel > 0)
		{
			sb.append("margin-left:");
			sb.append(indentLevel * INDENT_STEP);
			sb.append("px;");
		}

		sb.append("'>");
		sb.append(html);
		sb.append("</div>");
	}

	/** @return {@code text} escaped, with its markdown links turned into clickable anchors. */
	private static String inlineLinks(String text)
	{
		Matcher matcher = MD_LINK.matcher(escape(text));
		StringBuffer sb = new StringBuffer();

		while (matcher.find())
		{
			String anchor = "<a href='" + matcher.group(2) + "'>" + matcher.group(1) + "</a>";

			matcher.appendReplacement(sb, Matcher.quoteReplacement(anchor));
		}

		matcher.appendTail(sb);

		return sb.toString();
	}

	/** @return {@code text} with its HTML-significant characters escaped so it renders literally. */
	private static String escape(String text)
	{
		return text
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;");
	}

	/** One navigable section: heading depth (0 for {@code ##}, 1 for {@code ###}), text, and anchor. */
	@Value
	static class Section
	{
		int level;

		String text;

		String anchor;
	}
}
