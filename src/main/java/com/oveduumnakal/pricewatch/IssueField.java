/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

/**
 * A single field in an issue form mapped to a GitHub form {@code id}. A {@code null}
 * {@link #options} makes it a free-text area {@link #rows} tall; a non-null one makes it
 * a dropdown whose entries must match the template's option labels exactly.
 */
final class IssueField
{
	final String id;
	final String label;
	final int rows;
	final String[] options;

	IssueField(String id, String label, int rows)
	{
		this(id, label, rows, null);
	}

	IssueField(String id, String label, String[] options)
	{
		this(id, label, 0, options);
	}

	private IssueField(String id, String label, int rows, String[] options)
	{
		this.id = id;
		this.label = label;
		this.rows = rows;
		this.options = options;
	}
}
