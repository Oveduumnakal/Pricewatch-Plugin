/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.Collection;

/**
 * Renders the acquisition (lot) log of the tracked items to CSV: one row per
 * acquisition, with the realized profit filled in for closed lots and the buy/sell
 * provenance from source-attributed pricing (#64) — {@code Sold Estimated} flags
 * closes priced at the average rather than an observed sale (#71). The output is
 * RFC-4180-style (quotes doubled, fields with commas/quotes/newlines quoted) so it
 * opens cleanly in any spreadsheet.
 */
public final class AcquisitionCsvExporter
{
	private static final String HEADER =
			"Item,Item ID,Quantity,Bought At,Sold At,Realized Profit,Source,Sell Source,Sold Estimated";

	private AcquisitionCsvExporter()
	{
	}

	/**
	 * Builds the CSV for {@code items} in the given order.
	 *
	 * @return the CSV text including the header row; header-only when nothing has lots.
	 */
	public static String toCsv(Collection<TrackedItem> items)
	{
		StringBuilder sb = new StringBuilder(HEADER).append('\n');

		for (TrackedItem item : items)
		{
			if (item.getAcquisitions() == null)
				continue;

			for (AcquisitionRecord record : item.getAcquisitions())
				appendRow(sb, item, record);
		}

		return sb.toString();
	}

	private static void appendRow(StringBuilder sb, TrackedItem item, AcquisitionRecord record)
	{
		Long soldAt = record.getSoldAt();
		String profit = soldAt != null
				? Long.toString((soldAt - record.getBoughtAt()) * record.getQuantity())
				: "";

		sb.append(escape(item.getName())).append(',')
				.append(item.getItemId()).append(',')
				.append(record.getQuantity()).append(',')
				.append(record.getBoughtAt()).append(',')
				.append(soldAt != null ? Long.toString(soldAt) : "").append(',')
				.append(profit).append(',')
				.append(record.sourceOrUnknown()).append(',')
				.append(soldAt != null ? record.sellSourceOrUnknown().toString() : "").append(',')
				.append(record.isSellEstimated() ? "yes" : "").append('\n');
	}

	/** Quotes a field when it contains a comma, quote, or newline, doubling embedded quotes. */
	private static String escape(String value)
	{
		String safe = value != null ? value : "";
		if (safe.indexOf(',') < 0 && safe.indexOf('"') < 0 && safe.indexOf('\n') < 0)
			return safe;

		return '"' + safe.replace("\"", "\"\"") + '"';
	}
}
