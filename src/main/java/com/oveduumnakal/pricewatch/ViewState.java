/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * How the watchlist is currently being displayed: the sort mode and its
 * direction, and whether rows are drawn compactly. Persisted to the RuneScape
 * profile as three scalar keys rather than as JSON, since none of it is
 * structured.
 */
@Data
@AllArgsConstructor
class ViewState
{
	private SortMode sortMode;
	private boolean sortReversed;
	private boolean compact;
}
