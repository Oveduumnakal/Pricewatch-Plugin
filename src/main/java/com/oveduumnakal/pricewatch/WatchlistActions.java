/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.List;

/**
 * Everything the panel can ask the plugin to do. The panel never mutates the
 * watchlist itself: it renders what it is given and calls back here, so all
 * state changes and persistence stay on the client thread in one place.
 */
interface WatchlistActions
{
	/**
	 * Adds an item to the watchlist, or sets it as the preview entry.
	 *
	 * @param mode   whether to watch the item or only preview it
	 * @param itemId the item
	 */
	void addWatchedItem(WatchItemMode mode, int itemId);

	/**
	 * Removes an item from the watchlist.
	 *
	 * @param itemId the item to stop watching
	 */
	void removeWatchedItem(int itemId);

	/**
	 * Stars or unstars an item.
	 *
	 * @param itemId   the item
	 * @param favorite whether it should be a favourite
	 */
	void setFavorite(int itemId, boolean favorite);

	/**
	 * Changes how the list is ordered.
	 *
	 * @param mode the new sort mode
	 */
	void setSortMode(SortMode mode);

	/** Flips the current sort mode's direction. */
	void toggleSortReversed();

	/** Switches the compact row layout on or off. */
	void toggleCompactView();

	/**
	 * Assigns an item to a category.
	 *
	 * @param itemId   the item
	 * @param category the category name, or {@code null} to clear it
	 */
	void setItemCategory(int itemId, String category);

	/**
	 * Rolls a group up or down.
	 *
	 * @param groupKey  the group's persistence key
	 * @param collapsed whether it should be rolled up
	 */
	void setGroupCollapsed(String groupKey, boolean collapsed);

	/**
	 * Creates a category.
	 *
	 * @param name the new category's name
	 */
	void createCategory(String name);

	/**
	 * Renames a category and re-points its items.
	 *
	 * @param oldName the category to rename
	 * @param newName its new name
	 */
	void renameCategory(String oldName, String newName);

	/**
	 * Deletes a category, moving its items to Uncategorised.
	 *
	 * @param name the category to delete
	 */
	void deleteCategory(String name);

	/**
	 * Moves a category to a new position in the ordered list.
	 *
	 * @param name        the category to move
	 * @param targetIndex where it should end up
	 */
	void reorderCategory(String name, int targetIndex);

	/**
	 * Auto-assigns watched items to wiki-derived categories.
	 *
	 * @param includeCategorized also re-categorise items that already have a category
	 * @return a user-facing summary of what will change
	 */
	String autoCategorize(boolean includeCategorized);

	/**
	 * Replaces the watchlist's stored order after a drag.
	 *
	 * @param orderedIds every watched item id, in the new order
	 */
	void reorderWatchlist(List<Integer> orderedIds);

	/**
	 * Asks for an item's full history so its detail view can be drawn.
	 *
	 * @param itemId the item whose detail view was opened
	 */
	void requestDetailData(int itemId);

	/**
	 * Reports that an item's alert rules were edited, so they can be persisted. The
	 * rules are mutated in place on the item the panel was handed, so nothing is
	 * passed back here beyond which item to save.
	 *
	 * @param itemId the item whose rules changed
	 */
	void alertsEdited(int itemId);

	/**
	 * @return a shareable code for the current watchlist and its categories
	 */
	String exportShareCode();

	/**
	 * Merges a pasted share code into the current watchlist.
	 *
	 * @param code the pasted token
	 * @return a user-facing summary of what was imported, or why it was refused
	 */
	String importShareCode(String code);
}
