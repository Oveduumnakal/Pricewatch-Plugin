/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.ObjIntConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.http.api.item.ItemPrice;

/**
 * The side panel: a search field that adds items to the watchlist, and one row
 * per watched item showing its icon, name and latest prices.
 *
 * <p>List management (add, remove, preview) is delegated to the plugin through
 * the callbacks supplied to the constructor; the plugin pushes data back via
 * {@link #rebuild}. All methods run on the Swing EDT.
 */
public class PricewatchPanel extends PluginPanel
{
	private static final int ICON_WIDTH = 32;

	private static final int MAX_SEARCH_RESULTS = 5;

	private static final int MIN_SEARCH_LENGTH = 2;

	private static final Color ADD_GREEN = new Color(0, 153, 0);

	private static final Color REMOVE_RED = new Color(0xc8, 0x4a, 0x42);

	private final ItemManager itemManager;

	private final ObjIntConsumer<WatchItemMode> onAddItem;

	private final IntConsumer onRemoveItem;

	private final IconTextField searchField = new IconTextField();

	private final JPanel searchResults = new JPanel();

	private final JPanel list = new JPanel();

	private final JLabel empty = new JLabel("Search to add an item", SwingConstants.CENTER);

	private final List<Integer> watchedIds = new ArrayList<>();

	/**
	 * Builds the empty panel.
	 *
	 * @param itemManager  the client's item manager, used for icons and search
	 * @param onAddItem    called with the item id and the mode to add it in
	 * @param onRemoveItem called with the item id to stop watching
	 */
	public PricewatchPanel(ItemManager itemManager, ObjIntConsumer<WatchItemMode> onAddItem,
			IntConsumer onRemoveItem)
	{
		super(false);

		this.itemManager = itemManager;
		this.onAddItem = onAddItem;
		this.onRemoveItem = onRemoveItem;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(ColorScheme.DARK_GRAY_COLOR);

		empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		add(buildSearchHeader(), BorderLayout.NORTH);
		add(buildScrollingList(), BorderLayout.CENTER);
	}

	/** @return the search field with its results dropdown beneath it. */
	private JPanel buildSearchHeader()
	{
		searchField.setIcon(IconTextField.Icon.SEARCH);
		searchField.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchField.setMinimumSize(new Dimension(0, 30));
		searchField.addClearListener(() -> searchResults.setVisible(false));
		searchField.getDocument().addDocumentListener(new SearchListener());

		searchResults.setLayout(new BoxLayout(searchResults, BoxLayout.Y_AXIS));
		searchResults.setBackground(ColorScheme.DARK_GRAY_COLOR);
		searchResults.setBorder(new EmptyBorder(4, 0, 4, 0));
		searchResults.setVisible(false);

		final JPanel header = new JPanel(new BorderLayout());

		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(new EmptyBorder(0, 0, 6, 0));
		header.add(searchField, BorderLayout.NORTH);
		header.add(searchResults, BorderLayout.CENTER);

		return header;
	}

	/** @return the scrolling watchlist, anchored to the top of its viewport. */
	private JScrollPane buildScrollingList()
	{
		final JPanel anchor = new JPanel(new BorderLayout());

		anchor.setBackground(ColorScheme.DARK_GRAY_COLOR);
		anchor.add(list, BorderLayout.NORTH);

		final JScrollPane scroll = new JScrollPane(anchor,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);

		return scroll;
	}

	/**
	 * Replaces the visible rows with the given watchlist.
	 *
	 * @param items   the watched items, in display order
	 * @param preview the item being previewed without being watched, or {@code null}
	 */
	public void rebuild(List<WatchedItem> items, WatchedItem preview)
	{
		list.removeAll();
		watchedIds.clear();
		items.forEach(item -> watchedIds.add(item.getItemId()));

		if (preview != null)
		{
			list.add(sectionLabel("Preview"));
			list.add(buildRow(preview, true));
			list.add(Box.createVerticalStrut(6));
			list.add(sectionLabel("Watchlist"));
		}

		if (items.isEmpty())
			list.add(empty);

		for (WatchedItem item : items)
		{
			list.add(buildRow(item, false));
			list.add(Box.createVerticalStrut(2));
		}

		list.revalidate();
		list.repaint();
	}

	/** @return a small heading used to separate the preview entry from the watchlist. */
	private static JLabel sectionLabel(String text)
	{
		final JLabel label = new JLabel(text);

		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(new EmptyBorder(2, 2, 2, 2));

		return label;
	}

	/** @return one watchlist row: icon on the left, name over a price line, remove button on the right. */
	private JPanel buildRow(WatchedItem item, boolean isPreview)
	{
		final JPanel row = new JPanel(new BorderLayout(6, 0));

		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 4, 4, 4));

		final JLabel icon = new JLabel();

		icon.setPreferredSize(new Dimension(ICON_WIDTH, ICON_WIDTH));
		icon.setHorizontalAlignment(SwingConstants.CENTER);

		final AsyncBufferedImage image = itemManager.getImage(item.getItemId(), 1, item.isStackable());

		image.addTo(icon);

		final JLabel name = new JLabel();

		name.setForeground(Color.WHITE);
		name.setFont(FontManager.getRunescapeSmallFont());
		EllipsisText.set(name, item.getName());

		final JLabel prices = new JLabel(priceText(item));

		prices.setForeground(priceColor(item));
		prices.setFont(prices.getFont().deriveFont(Font.PLAIN, 11f));

		final JPanel text = new JPanel(new GridLayout(2, 1));

		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		text.add(name);
		text.add(prices);

		row.add(icon, BorderLayout.WEST);
		row.add(text, BorderLayout.CENTER);
		row.add(buildRowButtons(item, isPreview), BorderLayout.EAST);

		return row;
	}

	/** @return the trailing button cluster for a row: add for a preview, remove for a watched item. */
	private JPanel buildRowButtons(WatchedItem item, boolean isPreview)
	{
		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));

		buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		if (isPreview)
		{
			final JButton add = smallButton("+", ADD_GREEN, "Add to watchlist");

			add.addActionListener(e -> onAddItem.accept(WatchItemMode.WATCH, item.getItemId()));
			buttons.add(add);

			return buttons;
		}

		final JButton remove = smallButton("×", REMOVE_RED, "Remove from watchlist");

		remove.addActionListener(e -> onRemoveItem.accept(item.getItemId()));
		buttons.add(remove);

		return buttons;
	}

	/** @return a compact bordered button in the given accent colour. */
	private static JButton smallButton(String text, Color accent, String tooltip)
	{
		final JButton button = new JButton(text);

		button.setPreferredSize(new Dimension(28, 22));
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setForeground(accent);
		button.setFocusPainted(false);
		button.setBorderPainted(true);
		button.setBorder(BorderFactory.createLineBorder(accent));
		button.setToolTipText(tooltip);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		return button;
	}

	/** Rebuilds the results dropdown for a query, skipping items already watched. */
	private void onSearch(String query)
	{
		if (query == null || query.trim().length() < MIN_SEARCH_LENGTH)
		{
			searchResults.setVisible(false);
			return;
		}

		final List<ItemPrice> results = itemManager.search(query);

		searchResults.removeAll();

		int shown = 0;
		for (ItemPrice result : results)
		{
			if (shown >= MAX_SEARCH_RESULTS)
				break;

			if (watchedIds.contains(result.getId()))
				continue;

			searchResults.add(buildSearchResultRow(result.getId(), result.getName()));
			searchResults.add(Box.createVerticalStrut(2));
			shown++;
		}

		searchResults.setVisible(shown > 0);
		searchResults.revalidate();
		searchResults.repaint();
	}

	/** @return one search-result row, with buttons to preview or watch the item. */
	private JPanel buildSearchResultRow(int itemId, String itemName)
	{
		final JPanel row = new JPanel(new BorderLayout());

		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

		final JLabel name = new JLabel();

		name.setForeground(Color.WHITE);
		name.setFont(FontManager.getRunescapeSmallFont());
		EllipsisText.set(name, itemName);

		final JButton view = smallButton("◉", ColorScheme.LIGHT_GRAY_COLOR, "View prices only");

		view.addActionListener(e -> pick(itemId, WatchItemMode.PREVIEW));

		final JButton add = smallButton("+", ADD_GREEN, "Watch item");

		add.addActionListener(e -> pick(itemId, WatchItemMode.WATCH));

		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));

		buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		buttons.add(view);
		buttons.add(add);

		row.add(name, BorderLayout.CENTER);
		row.add(buttons, BorderLayout.EAST);

		return row;
	}

	/** Hands a chosen search result to the plugin and clears the search field. */
	private void pick(int itemId, WatchItemMode mode)
	{
		onAddItem.accept(mode, itemId);
		searchField.setText("");
		searchResults.setVisible(false);
	}

	/** @return the price line for an item, or why there isn't one. */
	static String priceText(WatchedItem item)
	{
		if (!item.isTradeable())
			return "Not tradeable";

		if (item.isPriceLoadFailed())
			return "No price data";

		if (!item.hasPrices())
			return "Loading...";

		return String.format("High %,d    Low %,d", item.getHighPrice(), item.getLowPrice());
	}

	/** @return dimmed text for prices restored from cache, normal text for live ones. */
	private static Color priceColor(WatchedItem item)
	{
		if (!item.hasPrices() || !item.isTradeable())
			return ColorScheme.MEDIUM_GRAY_COLOR;

		return item.hasLivePrices() ? ColorScheme.GRAND_EXCHANGE_PRICE : ColorScheme.MEDIUM_GRAY_COLOR;
	}

	/** Re-runs the search on every edit to the search field. */
	private final class SearchListener implements DocumentListener
	{
		@Override
		public void insertUpdate(DocumentEvent e)
		{
			onSearch(searchField.getText());
		}

		@Override
		public void removeUpdate(DocumentEvent e)
		{
			onSearch(searchField.getText());
		}

		@Override
		public void changedUpdate(DocumentEvent e)
		{
			onSearch(searchField.getText());
		}
	}
}
