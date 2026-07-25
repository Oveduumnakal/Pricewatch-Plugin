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

	private static final String UP_HEX = "#28c258";

	private static final String DOWN_HEX = "#e3463f";

	private static final String FLAT_HEX = "#a0a0a0";

	private final ItemManager itemManager;

	private final PricewatchConfig config;

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
	 * @param config       the plugin settings driving the price line
	 * @param onAddItem    called with the item id and the mode to add it in
	 * @param onRemoveItem called with the item id to stop watching
	 */
	public PricewatchPanel(ItemManager itemManager, PricewatchConfig config,
			ObjIntConsumer<WatchItemMode> onAddItem, IntConsumer onRemoveItem)
	{
		super(false);

		this.itemManager = itemManager;
		this.config = config;
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

		final PriceLineOptions options = new PriceLineOptions(config);

		if (preview != null)
		{
			list.add(sectionLabel("Preview"));
			list.add(buildRow(preview, options, true));
			list.add(Box.createVerticalStrut(6));
			list.add(sectionLabel("Watchlist"));
		}

		if (items.isEmpty())
			list.add(empty);

		for (WatchedItem item : items)
		{
			list.add(buildRow(item, options, false));
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
	private JPanel buildRow(WatchedItem item, PriceLineOptions options, boolean isPreview)
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

		final String line = priceText(item, options);
		final JPanel text = new JPanel(new GridLayout(line == null ? 1 : 2, 1));

		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		text.add(name);

		if (line != null)
		{
			final JLabel prices = new JLabel(line);

			prices.setForeground(priceColor(item));
			prices.setFont(prices.getFont().deriveFont(Font.PLAIN, 11f));
			prices.setToolTipText(priceTooltip(item, options));
			text.add(prices);
		}

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

	/**
	 * Builds the price line under an item's name.
	 *
	 * <p>Returns {@code null} when the line is switched off entirely, either by
	 * setting the window to {@link TimeWindow#NONE} or by turning off every column
	 * — both leave a plain icon and name row.
	 *
	 * @param item    the item to describe
	 * @param options what the line should show
	 * @return the HTML line, a short explanation when there is no price to show, or
	 *         {@code null} to omit the line
	 */
	static String priceText(WatchedItem item, PriceLineOptions options)
	{
		if (options.window == TimeWindow.NONE || !options.anyColumn())
			return null;

		if (!item.isTradeable())
			return "Not tradeable";

		if (item.isPriceLoadFailed())
			return "No price data";

		if (!item.hasPrices())
			return "Loading...";

		final PriceStats stats = statsFor(item, options.window);
		final boolean live = options.window == TimeWindow.LIVE;
		final StringBuilder html = new StringBuilder("<html>");

		if (options.high)
			html.append(cell("H", stats.getHigh(), live ? item.getHighDelta() : 0, options));

		if (options.low)
			html.append(cell("L", stats.getLow(), live ? item.getLowDelta() : 0, options));

		if (options.avg)
			html.append(cell("A", stats.getAvg(), live ? item.getAvgDelta() : 0, options));

		if (options.volume)
			html.append(volumeCell(stats.getVolume()));

		return html.append("</html>").toString();
	}

	/**
	 * @return the item's stats for a window, falling back to its current prices when
	 *         the series behind that window has not been fetched yet
	 */
	private static PriceStats statsFor(WatchedItem item, TimeWindow window)
	{
		final PriceStats stats = item.getWindowStats().get(window);
		if (stats != null)
			return stats;

		return new PriceStats(item.getHighPrice(), item.getLowPrice(), item.getAvgPrice(), 0);
	}

	/** @return one labelled figure, tinted by its movement when the indicator allows. */
	private static String cell(String label, long value, int delta, PriceLineOptions options)
	{
		final String text = GpFormat.shortValue(value);
		final String colour = options.colourFor(delta);

		if (colour == null)
			return label + " " + text + "&nbsp;&nbsp;";

		return label + " <font color='" + colour + "'>" + text + "</font>&nbsp;&nbsp;";
	}

	/** @return the traded-volume figure, or a dash for a window that carries no volume. */
	private static String volumeCell(long volume)
	{
		return "V " + (volume > 0 ? GpFormat.shortValue(volume) : "&mdash;") + "&nbsp;&nbsp;";
	}

	/** @return the untruncated figures for the row tooltip. */
	private static String priceTooltip(WatchedItem item, PriceLineOptions options)
	{
		if (!item.hasPrices() || !item.isTradeable())
			return item.getName();

		final PriceStats stats = statsFor(item, options.window);

		return item.getName() + " — " + options.window.getLongLabel()
				+ ": high " + GpFormat.fullGp(stats.getHigh())
				+ ", low " + GpFormat.fullGp(stats.getLow())
				+ ", avg " + GpFormat.fullGp(stats.getAvg());
	}

	/**
	 * What the price line should show: which window's figures, which of the four
	 * columns, and whether movement is tinted.
	 */
	static final class PriceLineOptions
	{
		private final TimeWindow window;
		private final boolean high;
		private final boolean low;
		private final boolean avg;
		private final boolean volume;
		private final PriceIndicatorMode indicator;

		/**
		 * Captures the current display settings.
		 *
		 * @param config the plugin settings to read
		 */
		PriceLineOptions(PricewatchConfig config)
		{
			this(config.priceLine(), config.showColHigh(), config.showColLow(),
					config.showColAvg(), config.showColVolume(), config.priceChangeIndicator());
		}

		/**
		 * Explicit constructor, used by the tests.
		 *
		 * @param window    which window's figures to show
		 * @param high      show the high price
		 * @param low       show the low price
		 * @param avg       show the average price
		 * @param volume    show traded volume
		 * @param indicator when to tint a figure by its movement
		 */
		PriceLineOptions(TimeWindow window, boolean high, boolean low, boolean avg, boolean volume,
				PriceIndicatorMode indicator)
		{
			this.window = window;
			this.high = high;
			this.low = low;
			this.avg = avg;
			this.volume = volume;
			this.indicator = indicator;
		}

		/** @return whether any column at all is switched on. */
		boolean anyColumn()
		{
			return high || low || avg || volume;
		}

		/**
		 * Picks the tint for a figure that moved by {@code delta} since the last
		 * refresh: green up, red down, and — under {@link PriceIndicatorMode#ALL}
		 * only — a neutral grey for a figure that held steady.
		 *
		 * @param delta the sign of the movement, or 0 when unchanged
		 * @return an HTML colour, or {@code null} to leave the figure untinted
		 */
		String colourFor(int delta)
		{
			if (indicator == PriceIndicatorMode.OFF)
				return null;

			if (delta > 0)
				return UP_HEX;

			if (delta < 0)
				return DOWN_HEX;

			return indicator == PriceIndicatorMode.ALL ? FLAT_HEX : null;
		}
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
