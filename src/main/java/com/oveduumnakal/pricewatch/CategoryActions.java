/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

/** The category management operations the panel invokes; implemented by the plugin. */
public interface CategoryActions
{
	void setItemCategory(int itemId, String category);

	void create(String name);

	void rename(String oldName, String newName);

	void delete(String name);

	void reorder(String name, int targetIndex);

	/**
	 * Auto-assigns tracked items to generated categories from the bundled wiki
	 * category snapshot.
	 *
	 * @param includeCategorized when {@code true} also re-categorizes items already in a
	 *                           category; otherwise only uncategorized items are touched
	 * @return a user-facing summary of how many items were categorized
	 */
	String autoCategorize(boolean includeCategorized);
}
