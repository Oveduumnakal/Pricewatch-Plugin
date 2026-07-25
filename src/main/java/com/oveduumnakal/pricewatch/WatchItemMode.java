/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

/**
 * Whether an entry is on the watchlist or is only being looked at.
 *
 * <ul>
 *   <li>{@link #WATCH} &ndash; a persisted watchlist entry.</li>
 *   <li>{@link #PREVIEW} &ndash; shown for its prices and charts only, never persisted.</li>
 * </ul>
 */
public enum WatchItemMode
{
	PREVIEW,
	WATCH
}
