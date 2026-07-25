/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.ArrayList;
import java.util.List;

/**
 * Works out the new watchlist order produced by dragging one row onto another.
 *
 * <p>The panel only ever sees the grouped, filtered, sorted view, but the order
 * that gets persisted is the flat one the plugin holds. So a drop is expressed
 * as "put the moved item immediately before this other item" and resolved
 * against the flat order here, which keeps the arithmetic away from the mouse
 * handling and makes it testable.
 *
 * <p>Stateless utility.
 */
final class WatchlistReorder
{
	private WatchlistReorder()
	{
	}

	/**
	 * Moves one item to sit immediately before another.
	 *
	 * <p>A no-op that returns a copy of the input when the move would change
	 * nothing: dropping an item on itself, naming an item that is not in the
	 * order, or moving an item to where it already is.
	 *
	 * @param order    the current flat order of item ids
	 * @param movedId  the item being dragged
	 * @param beforeId the item to land in front of, or {@code null} to move to the end
	 * @return a new list; the input is not modified
	 */
	static List<Integer> moveBefore(List<Integer> order, int movedId, Integer beforeId)
	{
		if (!order.contains(movedId))
			return new ArrayList<>(order);

		if (beforeId != null && (beforeId == movedId || !order.contains(beforeId)))
			return new ArrayList<>(order);

		final List<Integer> result = new ArrayList<>(order);

		result.remove(Integer.valueOf(movedId));

		if (beforeId == null)
		{
			result.add(movedId);
			return result;
		}

		result.add(result.indexOf(beforeId), movedId);

		return result;
	}

	/**
	 * Checks that a proposed order is a faithful permutation of the current one.
	 *
	 * <p>Guards against a stale drag result being applied after the watchlist has
	 * changed underneath it — applying one would silently drop or duplicate items.
	 *
	 * @param current  the order the plugin holds
	 * @param proposed the order the panel produced
	 * @return whether the proposed order contains exactly the same ids
	 */
	static boolean isPermutationOf(List<Integer> current, List<Integer> proposed)
	{
		if (current.size() != proposed.size())
			return false;

		return new ArrayList<>(current).containsAll(proposed)
				&& new ArrayList<>(proposed).containsAll(current);
	}
}
