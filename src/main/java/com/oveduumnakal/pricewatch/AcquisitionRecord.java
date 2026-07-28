/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single buy (and optional matching sell) of a tracked item, forming one lot
 * of its cost basis.
 *
 * <p>{@code quantity} units were bought at {@code boughtAt} gp each. While
 * {@code soldAt} is {@code null} the lot is still held and contributes to the
 * item's cost basis and unrealized profit; once set, the lot is realized at that
 * sale price. {@code source} records how the lot entered the collection and
 * {@code sellSource} how it left it; records persisted before sources existed
 * have {@code null}, which {@link #sourceOrUnknown()} and
 * {@link #sellSourceOrUnknown()} map to {@link AcquisitionSource#UNKNOWN} — the
 * safe legacy default the schema fixtures and snapshot guard.
 *
 * <p>A sold lot whose sell source is {@link AcquisitionSource#UNKNOWN} was closed
 * at an estimated price (the current average) rather than an observed sale, and
 * is marked as such in the acquisitions table.
 */
@Data
@NoArgsConstructor
public class AcquisitionRecord
{
	private int quantity;
	private long boughtAt;
	private Long soldAt;
	private AcquisitionSource source;
	private AcquisitionSource sellSource;

	public AcquisitionRecord(int quantity, long boughtAt, Long soldAt)
	{
		this(quantity, boughtAt, soldAt, null);
	}

	public AcquisitionRecord(int quantity, long boughtAt, Long soldAt, AcquisitionSource source)
	{
		this.quantity = quantity;
		this.boughtAt = boughtAt;
		this.soldAt = soldAt;
		this.source = source;
	}

	/** @return the lot's source, mapping the legacy {@code null} to {@link AcquisitionSource#UNKNOWN}. */
	public AcquisitionSource sourceOrUnknown()
	{
		return source == null ? AcquisitionSource.UNKNOWN : source;
	}

	/** @return the lot's sell source, mapping the legacy {@code null} to {@link AcquisitionSource#UNKNOWN}. */
	public AcquisitionSource sellSourceOrUnknown()
	{
		return sellSource == null ? AcquisitionSource.UNKNOWN : sellSource;
	}

	/** @return whether this lot was closed at an estimated price rather than an observed sale. */
	public boolean isSellEstimated()
	{
		return soldAt != null && sellSourceOrUnknown() == AcquisitionSource.UNKNOWN;
	}
}
