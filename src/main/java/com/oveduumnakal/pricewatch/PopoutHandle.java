/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.util.function.Consumer;
import javax.swing.JFrame;

/** Tracks one open pop-out window and the refresher used to push fresh item data into it. */
final class PopoutHandle
{
	final JFrame frame;
	final Consumer<WatchedItem> refresher;
	final Runnable onClose;

	PopoutHandle(JFrame frame, Consumer<WatchedItem> refresher, Runnable onClose)
	{
		this.frame = frame;
		this.refresher = refresher;
		this.onClose = onClose;
	}
}
