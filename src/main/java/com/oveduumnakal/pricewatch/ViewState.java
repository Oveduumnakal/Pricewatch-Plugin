/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Everything about how the watchlist is currently presented: the sort mode and
 * its direction, whether rows are drawn compactly, and the categories with the
 * collapsed state of each group.
 *
 * <p>Pushed from the plugin to the panel on every refresh. The one piece of view
 * state not here is the filter text, which the panel owns outright because
 * nothing outside it cares.
 */
@Data
@AllArgsConstructor
class ViewState
{
	private SortMode sortMode;
	private boolean sortReversed;
	private boolean compact;
	private List<CategoryState> categories;
	private boolean favoritesCollapsed;
	private boolean uncategorizedCollapsed;
}
