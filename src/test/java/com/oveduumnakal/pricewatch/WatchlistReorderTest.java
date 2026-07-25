/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link WatchlistReorder}: where a dragged item lands, which drops are
 * refused as no-ops, and the permutation guard that stops a stale drag from
 * dropping or duplicating items.
 */
public class WatchlistReorderTest
{
	private static final List<Integer> ORDER = Arrays.asList(1, 2, 3, 4);

	@Test
	public void movingForwardPlacesTheItemBeforeItsTarget()
	{
		assertEquals(Arrays.asList(2, 3, 1, 4), WatchlistReorder.moveBefore(ORDER, 1, 4));
	}

	@Test
	public void movingBackwardPlacesTheItemBeforeItsTarget()
	{
		assertEquals(Arrays.asList(1, 4, 2, 3), WatchlistReorder.moveBefore(ORDER, 4, 2));
	}

	@Test
	public void aNullTargetMovesTheItemToTheEnd()
	{
		assertEquals(Arrays.asList(2, 3, 4, 1), WatchlistReorder.moveBefore(ORDER, 1, null));
	}

	@Test
	public void droppingAnItemOnItselfChangesNothing()
	{
		assertEquals(ORDER, WatchlistReorder.moveBefore(ORDER, 2, 2));
	}

	@Test
	public void droppingOntoTheItemAlreadyBehindItChangesNothing()
	{
		assertEquals(ORDER, WatchlistReorder.moveBefore(ORDER, 1, 2));
	}

	@Test
	public void anUnknownMovedItemChangesNothing()
	{
		assertEquals(ORDER, WatchlistReorder.moveBefore(ORDER, 99, 2));
	}

	@Test
	public void anUnknownTargetChangesNothing()
	{
		assertEquals(ORDER, WatchlistReorder.moveBefore(ORDER, 1, 99));
	}

	@Test
	public void theInputOrderIsNeverModified()
	{
		List<Integer> original = Arrays.asList(1, 2, 3, 4);

		WatchlistReorder.moveBefore(original, 1, 4);

		assertEquals(Arrays.asList(1, 2, 3, 4), original);
	}

	@Test
	public void everyResultKeepsTheSameItems()
	{
		assertTrue(WatchlistReorder.isPermutationOf(ORDER, WatchlistReorder.moveBefore(ORDER, 1, 4)));
		assertTrue(WatchlistReorder.isPermutationOf(ORDER, WatchlistReorder.moveBefore(ORDER, 4, 2)));
		assertTrue(WatchlistReorder.isPermutationOf(ORDER, WatchlistReorder.moveBefore(ORDER, 3, null)));
	}

	@Test
	public void aReorderedListIsAPermutation()
	{
		assertTrue(WatchlistReorder.isPermutationOf(ORDER, Arrays.asList(4, 3, 2, 1)));
	}

	@Test
	public void aListMissingAnItemIsNotAPermutation()
	{
		assertFalse(WatchlistReorder.isPermutationOf(ORDER, Arrays.asList(1, 2, 3)));
	}

	@Test
	public void aListWithAnExtraItemIsNotAPermutation()
	{
		assertFalse(WatchlistReorder.isPermutationOf(ORDER, Arrays.asList(1, 2, 3, 4, 5)));
	}

	@Test
	public void aSameLengthListWithADifferentItemIsNotAPermutation()
	{
		assertFalse(WatchlistReorder.isPermutationOf(ORDER, Arrays.asList(1, 2, 3, 9)));
	}

	@Test
	public void aSameLengthListWithADuplicateIsNotAPermutation()
	{
		assertFalse(WatchlistReorder.isPermutationOf(ORDER, Arrays.asList(1, 1, 2, 3)));
	}

	@Test
	public void twoEmptyListsArePermutations()
	{
		assertTrue(WatchlistReorder.isPermutationOf(Collections.emptyList(), Collections.emptyList()));
	}
}
