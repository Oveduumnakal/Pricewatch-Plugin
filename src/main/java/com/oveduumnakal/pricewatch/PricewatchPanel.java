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
import java.awt.Point;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.LinkBrowser;
import net.runelite.http.api.item.ItemPrice;

/**
 * The side panel: a search field that adds items to the watchlist, and one row
 * per watched item showing its icon, name and latest prices.
 *
 * <p>List management is delegated to the plugin through {@link WatchlistActions};
 * the plugin pushes data back via {@link #rebuild}. The filter is the one piece
 * of view state the panel owns outright, since nothing outside it cares.
 * All methods run on the Swing EDT.
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

	private static final Color STAR_GOLD = new Color(0xf6, 0xd8, 0x73);

	private static final int GRAPH_HEIGHT = 140;

	private static final int POPOUT_WIDTH = 720;

	private static final int POPOUT_HEIGHT = 460;

	private static final String WIKI_BASE = "https://oldschool.runescape.wiki/w/";

	private static final String PRICES_BASE = "https://prices.runescape.wiki/osrs/item/";

	private final ItemManager itemManager;

	private final PricewatchConfig config;

	private final WatchlistActions actions;

	private final IconTextField searchField = new IconTextField();

	private final IconTextField filterField = new IconTextField();

	private final JPanel searchResults = new JPanel();

	private final JPanel list = new JPanel();

	private final JLabel empty = new JLabel("Search to add an item", SwingConstants.CENTER);

	private final JButton sortButton = new JButton();

	private final JButton compactButton = new JButton();

	private final JButton shareButton = new JButton();

	private final List<Integer> watchedIds = new ArrayList<>();

	/** Where each rendered row sits, so a drop point can be resolved back to an item. */
	private final List<RowRef> rowRefs = new ArrayList<>();

	private List<WatchedItem> lastItems = new ArrayList<>();

	private WatchedItem lastPreview;

	private ViewState lastView = new ViewState(
			SortMode.MANUAL, false, false, new ArrayList<>(), false, false, new ArrayList<>(), 0, 0);

	/** The item whose detail view is open, or {@code null} while the list is showing. */
	private Integer detailItemId;

	/** Open chart windows, keyed by mode and item id. */
	private final Map<String, PopoutHandle> popouts = new LinkedHashMap<>();

	/**
	 * Builds the empty panel.
	 *
	 * @param itemManager the client's item manager, used for icons and search
	 * @param config      the plugin settings driving the price line
	 * @param actions     what the panel calls back into when the user changes something
	 */
	public PricewatchPanel(ItemManager itemManager, PricewatchConfig config, WatchlistActions actions)
	{
		super(false);

		this.itemManager = itemManager;
		this.config = config;
		this.actions = actions;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(ColorScheme.DARK_GRAY_COLOR);

		empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		add(buildHeader(), BorderLayout.NORTH);
		add(buildScrollingList(), BorderLayout.CENTER);
	}

	/** @return the search field, results dropdown, and the filter/sort/compact controls. */
	private JPanel buildHeader()
	{
		final JPanel header = new JPanel(new BorderLayout());

		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(new EmptyBorder(0, 0, 6, 0));
		header.add(buildSearchHeader(), BorderLayout.NORTH);
		header.add(buildControls(), BorderLayout.SOUTH);

		return header;
	}

	/** @return the filter box with the sort and compact buttons beside it. */
	private JPanel buildControls()
	{
		filterField.setIcon(IconTextField.Icon.SEARCH);
		filterField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		filterField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		filterField.setMinimumSize(new Dimension(0, 24));
		filterField.setPreferredSize(new Dimension(0, 24));
		filterField.addClearListener(this::redraw);
		filterField.getDocument().addDocumentListener(new FilterListener());

		styleControlButton(sortButton, "Sort the watchlist");
		sortButton.addActionListener(e -> showSortMenu());

		styleControlButton(compactButton, "Toggle compact rows");
		compactButton.addActionListener(e -> actions.toggleCompactView());

		styleControlButton(shareButton, "Export or import a share code");
		shareButton.setText("Share");
		shareButton.addActionListener(e -> showShareMenu());

		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));

		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.add(sortButton);
		buttons.add(compactButton);
		buttons.add(shareButton);

		final JPanel controls = new JPanel(new BorderLayout(4, 0));

		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
		controls.setBorder(new EmptyBorder(6, 0, 0, 0));
		controls.add(filterField, BorderLayout.CENTER);
		controls.add(buttons, BorderLayout.EAST);

		return controls;
	}

	/** Applies the shared look to one of the small header buttons. */
	private static void styleControlButton(JButton button, String tooltip)
	{
		button.setPreferredSize(new Dimension(60, 24));
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		button.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		button.setToolTipText(tooltip);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	}

	/** Opens the sort-mode menu, with the active mode's direction offered as a toggle. */
	private void showSortMenu()
	{
		final JPopupMenu menu = new JPopupMenu();

		for (SortMode mode : SortMode.values())
		{
			final JMenuItem entry = new JMenuItem(mode.toString());

			entry.addActionListener(e -> actions.setSortMode(mode));
			menu.add(entry);
		}

		if (lastView.getSortMode() != SortMode.MANUAL)
		{
			final JMenuItem reverse = new JMenuItem(
					lastView.isSortReversed() ? "Undo reverse" : "Reverse order");

			reverse.addActionListener(e -> actions.toggleSortReversed());
			menu.addSeparator();
			menu.add(reverse);
		}

		menu.show(sortButton, 0, sortButton.getHeight());
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

		final JPanel search = new JPanel(new BorderLayout());

		search.setBackground(ColorScheme.DARK_GRAY_COLOR);
		search.add(searchField, BorderLayout.NORTH);
		search.add(searchResults, BorderLayout.CENTER);

		return search;
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
	public void rebuild(List<WatchedItem> items, WatchedItem preview, ViewState view)
	{
		lastItems = items;
		lastPreview = preview;
		lastView = view;

		redraw();
		refreshPopouts();
	}

	/** Opens the detail view for an item and asks the plugin for its history. */
	private void openDetail(int itemId)
	{
		detailItemId = itemId;
		actions.requestDetailData(itemId);
		redraw();
	}

	/** Returns from the detail view to the watchlist. */
	private void closeDetail()
	{
		detailItemId = null;
		redraw();
	}

	/** @return the item the detail view is showing, or {@code null} if it is closed or gone. */
	private WatchedItem detailItem()
	{
		if (detailItemId == null)
			return null;

		if (lastPreview != null && lastPreview.getItemId() == detailItemId)
			return lastPreview;

		return lastItems.stream()
				.filter(item -> item.getItemId() == detailItemId)
				.findFirst()
				.orElse(null);
	}

	/**
	 * Re-renders the panel from the last data pushed in.
	 *
	 * <p>An open detail view whose item has since been removed falls back to the
	 * list rather than showing a stale card.
	 */
	private void redraw()
	{
		final WatchedItem detail = detailItem();

		if (detailItemId != null && detail == null)
			detailItemId = null;

		if (detail != null)
		{
			drawDetail(detail);
			return;
		}

		drawList();
	}

	/** Replaces the panel contents with one item's detail card. */
	private void drawDetail(WatchedItem item)
	{
		list.removeAll();
		rowRefs.clear();

		list.add(buildDetailHeader(item));
		list.add(Box.createVerticalStrut(6));

		for (DetailSection section : lastView.getDetailSections())
		{
			final JPanel body = buildSection(section, item);

			if (body == null)
				continue;

			list.add(sectionLabel(section.getLabel()));
			list.add(body);
			list.add(Box.createVerticalStrut(6));
		}

		list.revalidate();
		list.repaint();
	}

	/** @return the detail card's heading: a back control, the item icon and its name. */
	private JPanel buildDetailHeader(WatchedItem item)
	{
		final JPanel header = new JPanel(new BorderLayout(6, 0));

		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(new EmptyBorder(4, 4, 4, 4));

		final JButton back = smallButton("<", ColorScheme.LIGHT_GRAY_COLOR, "Back to the watchlist");

		back.addActionListener(e -> closeDetail());

		final JLabel name = new JLabel();

		name.setForeground(Color.WHITE);
		name.setFont(FontManager.getRunescapeBoldFont());
		EllipsisText.set(name, item.getName());

		header.add(back, BorderLayout.WEST);
		header.add(buildRowIcon(item), BorderLayout.CENTER);
		header.add(name, BorderLayout.EAST);

		return header;
	}

	/**
	 * @return the body of one detail section, or {@code null} for a section whose
	 *         implementation has not landed yet. The charts, market info, ratings,
	 *         alchemy and alerts each arrive with their own issue; drawing a bare
	 *         heading for them until then would look broken
	 */
	private JPanel buildSection(DetailSection section, WatchedItem item)
	{
		switch (section)
		{
			case ITEM_VALUES:
				return buildCurrentValuesBlock(item);
			case MARKET_INFO:
				return buildMarketInfoBlock(item);
			case PRICE_OVERVIEW:
				return buildOverviewGrid(item);
			case PRICE_GRAPH:
				return buildGraphSection(item, PriceGraphPanel.Mode.PRICE);
			case VOLUME_GRAPH:
				return buildGraphSection(item, PriceGraphPanel.Mode.VOLUME);
			case ALCHEMY:
				return buildAlchemyBlock(item);
			case LINKS:
				return buildLinksBlock(item);
			default:
				return null;
		}
	}

	/**
	 * @return the ratings strip appended to the market info block: volatility,
	 *         liquidity, the 30-day range position, and the buy/sell pressure bar
	 */
	private JPanel buildRatings(WatchedItem item)
	{
		final JPanel ratings = new JPanel(new GridLayout(0, 1, 0, 3));

		ratings.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		ratings.setBorder(new EmptyBorder(4, 8, 4, 8));

		final PriceStats day = item.getWindowStats().get(TimeWindow.H24);

		ratings.add(ratingRow("Volatility", MarketClassifier.volatility(item.getSeriesFor(TimeWindow.WEEK))));
		ratings.add(ratingRow("Liquidity", MarketClassifier.liquidity(day == null ? 0 : day.getVolume())));

		final long[] range = MarketClassifier.thirtyDayRange(item.getSeriesFor(TimeWindow.MONTH));

		if (range != null && range[1] > range[0])
		{
			final PriceRangeBar bar = new PriceRangeBar();

			bar.setRange(range[0], range[1], item.getAvgPrice());
			bar.setToolTipText("30-day range " + GpFormat.grouped(range[0])
					+ " to " + GpFormat.grouped(range[1]) + " - "
					+ MarketClassifier.rangePosition(range[0], range[1], item.getAvgPrice()));
			ratings.add(bar);
		}

		ratings.add(buildPressureBar(item));

		return ratings;
	}

	/** @return a labelled rating, dashed when there is not enough history to classify. */
	private static JPanel ratingRow(String caption, String value)
	{
		final JPanel row = new JPanel(new GridLayout(1, 2, 6, 0));

		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.add(infoCaption(caption));
		row.add(infoValue(value == null ? "-" : value,
				value == null ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.LIGHT_GRAY_COLOR,
				value == null ? "Not enough history yet" : null));

		return row;
	}

	/** @return the buy/sell pressure bar over the configured look-back window. */
	private JPanel buildPressureBar(WatchedItem item)
	{
		final PressureWindow window = config.pressureWindow();
		final long[] volumes = MarketClassifier.buySellVolume(
				item.getSeriesFor(window.window()), window.duration());
		final long total = volumes[0] + volumes[1];

		final BuySellBar bar = new BuySellBar();

		bar.setRatio(total > 0 ? (double) volumes[0] / total : 0.5);
		bar.setToolTipText("Bought " + GpFormat.shortValue(volumes[0])
				+ " against sold " + GpFormat.shortValue(volumes[1]) + " over " + window);

		final JPanel wrapper = new JPanel(new BorderLayout(0, 2));

		wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrapper.add(infoCaption("Pressure (" + window + ")"), BorderLayout.NORTH);
		wrapper.add(bar, BorderLayout.CENTER);

		return wrapper;
	}

	/**
	 * @return the alchemy block: both alch values, and what a cast actually nets once
	 *         the item and its runes are paid for
	 */
	private JPanel buildAlchemyBlock(WatchedItem item)
	{
		final JPanel block = new JPanel(new GridLayout(0, 3, 6, 2));

		block.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		block.setBorder(new EmptyBorder(4, 8, 6, 8));

		block.add(infoCaption(""));
		block.add(infoCaption("Value"));
		block.add(infoCaption("Profit"));

		block.add(infoCaption("High"));
		block.add(figure(item.getHighAlch()));
		block.add(alchProfitCell(item, item.getHighAlch(), MarketFigures.HIGH_ALCH_FIRE_RUNES));

		block.add(infoCaption("Low"));
		block.add(figure(item.getLowAlch()));
		block.add(alchProfitCell(item, item.getLowAlch(), MarketFigures.LOW_ALCH_FIRE_RUNES));

		return block;
	}

	/** @return one alch-profit cell, tinted by sign, with the full sum in its tooltip. */
	private JLabel alchProfitCell(WatchedItem item, long alchValue, int fireQty)
	{
		if (alchValue <= 0 || item.getAvgPrice() <= 0)
			return infoValue("-", ColorScheme.MEDIUM_GRAY_COLOR, "No alch value or price yet");

		final long nature = lastView.getNatureRunePrice();
		final long fire = lastView.getFireRunePrice();
		final long profit = MarketFigures.alchProfit(alchValue, item.getAvgPrice(), nature, fire, fireQty);

		final String tooltip = "<html>" + GpFormat.grouped(alchValue) + " (alch value)<br>"
				+ "- " + GpFormat.grouped(item.getAvgPrice()) + " (item avg)<br>"
				+ "- " + GpFormat.grouped(nature) + " (nature rune)<br>"
				+ "- " + fireQty + " x " + GpFormat.grouped(fire) + " (fire rune)<br>"
				+ "= " + MarketFigures.signed(profit) + "</html>";

		Color colour = ColorScheme.LIGHT_GRAY_COLOR;
		if (profit > 0)
			colour = PricewatchColors.HIGH;
		else if (profit < 0)
			colour = PricewatchColors.LOW;

		return infoValue(MarketFigures.signed(profit), colour, tooltip);
	}

	/**
	 * @return the market info block: the GE sell tax on the current price, and when
	 *         the item last traded on each side, dimmed once those times go stale
	 */
	private JPanel buildMarketInfoBlock(WatchedItem item)
	{
		final long now = System.currentTimeMillis() / 1000L;
		final int threshold = config.stalePriceThresholdMinutes();

		final JPanel block = new JPanel(new GridLayout(0, 2, 6, 2));

		block.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		block.setBorder(new EmptyBorder(4, 8, 6, 8));

		block.add(infoCaption("GE tax"));
		block.add(infoValue(GpFormat.shortGp(MarketFigures.geTax(item.getAvgPrice())),
				ColorScheme.LIGHT_GRAY_COLOR, "2% of the sale price, nothing under 50gp, capped at 5M"));

		block.add(infoCaption("Buy limit"));
		block.add(buyLimitValue(item, now));

		block.add(infoCaption("Last bought"));
		block.add(tradeTime(item.getLatestHighTime(), now, threshold));

		block.add(infoCaption("Last sold"));
		block.add(tradeTime(item.getLatestLowTime(), now, threshold));

		final JPanel section = new JPanel(new BorderLayout(0, 4));

		section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		section.add(block, BorderLayout.NORTH);
		section.add(buildRatings(item), BorderLayout.CENTER);

		return section;
	}

	/**
	 * @return the buy-limit cell: {@code bought / limit} with a reset countdown once
	 *         purchases have been seen this window, the plain limit while untouched,
	 *         and {@code -} for an item the GE does not limit
	 */
	private static JLabel buyLimitValue(WatchedItem item, long now)
	{
		final int limit = item.getBuyLimit();
		if (limit <= 0)
			return infoValue("-", ColorScheme.LIGHT_GRAY_COLOR, "This item has no GE buy limit");

		if (item.getLimitResetEpoch() <= 0)
		{
			return infoValue(GpFormat.grouped(limit), ColorScheme.LIGHT_GRAY_COLOR,
					"How many you may buy per 4-hour window");
		}

		final int bought = item.getLimitBought();
		final String text = GpFormat.grouped(bought) + " / " + GpFormat.grouped(limit);
		final String tooltip = "Bought this window — resets in "
				+ MarketFigures.formatCountdown(item.getLimitResetEpoch() - now);

		return infoValue(text, bought >= limit ? PricewatchColors.LOW : ColorScheme.LIGHT_GRAY_COLOR, tooltip);
	}

	/** @return a trade-time value, dimmed when the timestamp has gone stale. */
	private static JLabel tradeTime(long epochSeconds, long now, int thresholdMinutes)
	{
		final boolean stale = MarketFigures.isStale(epochSeconds, now, thresholdMinutes);

		return infoValue(MarketFigures.formatAge(epochSeconds, now),
				stale ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.LIGHT_GRAY_COLOR,
				stale ? "Older than the stale threshold" : null);
	}

	/** @return a caption cell in a two-column info block. */
	private static JLabel infoCaption(String text)
	{
		final JLabel label = new JLabel(text);

		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setFont(FontManager.getRunescapeSmallFont());

		return label;
	}

	/** @return a value cell in a two-column info block. */
	private static JLabel infoValue(String text, Color colour, String tooltip)
	{
		final JLabel label = new JLabel(text, SwingConstants.RIGHT);

		label.setForeground(colour);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setToolTipText(tooltip);

		return label;
	}

	/**
	 * @return the overview grid: one row per time window in the configured preset,
	 *         with high, low, average, volume and the change against the live price
	 */
	private JPanel buildOverviewGrid(WatchedItem item)
	{
		final JPanel grid = new JPanel(new GridLayout(0, 6, 4, 2));

		grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		grid.setBorder(new EmptyBorder(4, 6, 6, 6));

		for (String heading : new String[]{"", "High", "Low", "Avg", "Vol", "Chg"})
			grid.add(infoCaption(heading));

		for (TimeWindow window : TimeWindow.values())
		{
			if (!config.overviewPreset().getWindows().contains(window))
				continue;

			final PriceStats stats = item.getWindowStats().get(window);

			grid.add(infoCaption(window.getLabel()));
			grid.add(figure(stats == null ? 0 : stats.getHigh()));
			grid.add(figure(stats == null ? 0 : stats.getLow()));
			grid.add(figure(stats == null ? 0 : stats.getAvg()));
			grid.add(figure(stats == null ? 0 : stats.getVolume()));
			grid.add(changeCell(item, stats));
		}

		return grid;
	}

	/** @return a numeric grid cell, dashed when there is no figure. */
	private static JLabel figure(long value)
	{
		return infoValue(value > 0 ? GpFormat.shortValue(value) : "-",
				value > 0 ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.MEDIUM_GRAY_COLOR,
				value > 0 ? GpFormat.grouped(value) : null);
	}

	/** @return the change of the live price against a window's average, tinted by direction. */
	private static JLabel changeCell(WatchedItem item, PriceStats stats)
	{
		final double change = MarketFigures.percentChange(
				item.getAvgPrice(), stats == null ? 0 : stats.getAvg());

		Color colour = ColorScheme.MEDIUM_GRAY_COLOR;
		if (change > 0)
			colour = PricewatchColors.HIGH;
		else if (change < 0)
			colour = PricewatchColors.LOW;

		return infoValue(MarketFigures.formatChange(change), colour, null);
	}

	/** @return the links block: the wiki page and the live prices page for this item. */
	private JPanel buildLinksBlock(WatchedItem item)
	{
		final JPanel block = new JPanel(new GridLayout(1, 2, 6, 0));

		block.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		block.setBorder(new EmptyBorder(4, 8, 6, 8));
		block.add(linkButton("Wiki", WIKI_BASE + item.getName().replace(' ', '_')));
		block.add(linkButton("Live Prices", PRICES_BASE + item.getItemId()));

		return block;
	}

	/** @return a button that opens a URL in the user's browser. */
	private static JButton linkButton(String text, String url)
	{
		final JButton button = new JButton(text);

		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		button.setToolTipText(url);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.addActionListener(e -> LinkBrowser.browse(url));

		return button;
	}

	/** @return a chart for the item, with a control to pop it out into its own window. */
	private JPanel buildGraphSection(WatchedItem item, PriceGraphPanel.Mode mode)
	{
		final PriceGraphPanel graph = new PriceGraphPanel(mode);

		graph.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, GRAPH_HEIGHT));
		feed(graph, item, mode);

		final JButton popout = smallButton("^", ColorScheme.LIGHT_GRAY_COLOR, "Open in a resizable window");

		popout.addActionListener(e -> openGraphPopout(item, mode));

		final JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));

		controls.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		controls.add(popout);

		final JPanel section = new JPanel(new BorderLayout());

		section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		section.add(controls, BorderLayout.NORTH);
		section.add(graph, BorderLayout.CENTER);

		return section;
	}

	/** Pushes an item's four series and current price into a chart. */
	private static void feed(PriceGraphPanel graph, WatchedItem item, PriceGraphPanel.Mode mode)
	{
		graph.setData(item.getSeries5m(), item.getSeries1h(), item.getSeries6h(), item.getSeries24h(),
				mode == PriceGraphPanel.Mode.PRICE ? item.getAvgPrice() : 0);
	}

	/**
	 * Opens a chart in its own resizable window, or focuses the one already open.
	 *
	 * <p>The window is registered so later refreshes push fresh data into it; it
	 * deregisters itself when closed.
	 */
	private void openGraphPopout(WatchedItem item, PriceGraphPanel.Mode mode)
	{
		final String key = mode + ":" + item.getItemId();
		final PopoutHandle existing = popouts.get(key);

		if (existing != null)
		{
			existing.frame.toFront();
			return;
		}

		final PriceGraphPanel graph = new PriceGraphPanel(mode, true);

		feed(graph, item, mode);

		final JFrame frame = new JFrame(item.getName() + " — " + mode);

		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		frame.setSize(POPOUT_WIDTH, POPOUT_HEIGHT);
		frame.setLocationRelativeTo(this);
		frame.add(graph);
		frame.addWindowListener(new PopoutCloseListener(key));
		frame.setVisible(true);

		popouts.put(key, new PopoutHandle(frame,
				fresh -> feed(graph, fresh, mode),
				() -> popouts.remove(key)));
	}

	/** Pushes the latest data into every open pop-out, closing any whose item has gone. */
	private void refreshPopouts()
	{
		new ArrayList<>(popouts.entrySet()).forEach(entry ->
		{
			final int itemId = Integer.parseInt(entry.getKey().substring(entry.getKey().indexOf(':') + 1));
			final WatchedItem item = lastItems.stream()
					.filter(candidate -> candidate.getItemId() == itemId)
					.findFirst()
					.orElse(null);

			if (item == null)
			{
				entry.getValue().frame.dispose();
				return;
			}

			entry.getValue().refresher.accept(item);
		});
	}

	/** Disposes every open pop-out. Called when the plugin shuts down. */
	public void closePopouts()
	{
		new ArrayList<>(popouts.values()).forEach(handle -> handle.frame.dispose());
		popouts.clear();
	}

	/** Deregisters a pop-out when its window is closed. */
	private final class PopoutCloseListener extends WindowAdapter
	{
		private final String key;

		/**
		 * @param key the pop-out's registry key
		 */
		PopoutCloseListener(String key)
		{
			this.key = key;
		}

		@Override
		public void windowClosed(WindowEvent e)
		{
			popouts.remove(key);
		}
	}

	/**
	 * @return the three-cell current values block: high, low and average. Stockpile's
	 *         fourth cell and its profit label are both quantity-derived, so neither
	 *         has a counterpart here
	 */
	private static JPanel buildCurrentValuesBlock(WatchedItem item)
	{
		final JPanel block = new JPanel(new GridLayout(1, 3, 4, 0));

		block.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		block.setBorder(new EmptyBorder(4, 4, 4, 4));

		block.add(valueCell("High", item.getHighPrice()));
		block.add(valueCell("Low", item.getLowPrice()));
		block.add(valueCell("Average", item.getAvgPrice()));

		return block;
	}

	/** @return one labelled figure in the current values block. */
	private static JPanel valueCell(String label, long value)
	{
		final JPanel cell = new JPanel(new GridLayout(2, 1));

		cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		final JLabel caption = new JLabel(label, SwingConstants.CENTER);

		caption.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		caption.setFont(FontManager.getRunescapeSmallFont());

		final JLabel figure = new JLabel(value > 0 ? GpFormat.shortValue(value) : "-", SwingConstants.CENTER);

		figure.setForeground(value > 0 ? ColorScheme.GRAND_EXCHANGE_PRICE : ColorScheme.MEDIUM_GRAY_COLOR);
		figure.setToolTipText(value > 0 ? GpFormat.fullGp(value) : "No price yet");

		cell.add(caption);
		cell.add(figure);

		return cell;
	}

	/** Replaces the panel contents with the grouped watchlist. */
	private void drawList()
	{
		list.removeAll();
		watchedIds.clear();
		rowRefs.clear();
		lastItems.forEach(item -> watchedIds.add(item.getItemId()));

		sortButton.setText(sortButtonText());
		compactButton.setText(lastView.isCompact() ? "Full" : "Compact");

		final PriceLineOptions options = new PriceLineOptions(config);
		final List<WatchlistGrouping.Group> groups = WatchlistGrouping.group(
				lastItems, lastView.getCategories(),
				lastView.isFavoritesCollapsed(), lastView.isUncategorizedCollapsed(),
				lastView.getSortMode(), lastView.isSortReversed(), filterField.getText());

		if (lastPreview != null)
		{
			list.add(sectionLabel("Preview"));
			list.add(buildRow(lastPreview, options, true));
			list.add(Box.createVerticalStrut(6));
		}

		if (groups.isEmpty())
		{
			empty.setText(lastItems.isEmpty() ? "Search to add an item" : "Nothing matches that filter");
			list.add(empty);
		}

		for (WatchlistGrouping.Group group : groups)
		{
			list.add(buildGroupHeader(group));

			if (group.isCollapsed())
				continue;

			for (WatchedItem item : group.getItems())
			{
				final JPanel row = buildRow(item, options, false);

				rowRefs.add(new RowRef(row, item.getItemId(), group.getKey()));
				list.add(row);
				list.add(Box.createVerticalStrut(2));
			}

			list.add(Box.createVerticalStrut(4));
		}

		list.revalidate();
		list.repaint();
	}

	/** @return the sort button's label: the mode, with an arrow when its direction is flipped. */
	private String sortButtonText()
	{
		final SortMode mode = lastView.getSortMode();

		if (mode == SortMode.MANUAL)
			return mode.toString();

		return mode + (mode.descending(lastView.isSortReversed()) ? " v" : " ^");
	}

	/** @return a clickable accordion header that rolls its group up or down. */
	private JPanel buildGroupHeader(WatchlistGrouping.Group group)
	{
		final JPanel header = new JPanel(new BorderLayout());

		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(new EmptyBorder(4, 2, 2, 2));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		final JLabel label = new JLabel((group.isCollapsed() ? "+ " : "- ")
				+ group.getLabel() + "  (" + group.getItems().size() + ")");

		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setFont(FontManager.getRunescapeSmallFont());

		header.add(label, BorderLayout.CENTER);
		header.addMouseListener(new CollapseListener(group));

		return header;
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
		row.setBorder(new EmptyBorder(lastView.isCompact() ? 2 : 4, 4, lastView.isCompact() ? 2 : 4, 4));

		final JLabel name = new JLabel();

		name.setForeground(Color.WHITE);
		name.setFont(FontManager.getRunescapeSmallFont());
		EllipsisText.set(name, item.getName());

		final String line = lastView.isCompact() ? null : priceText(item, options);
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

		row.add(buildRowLeading(item, isPreview), BorderLayout.WEST);
		row.add(text, BorderLayout.CENTER);
		row.add(buildRowButtons(item, isPreview), BorderLayout.EAST);

		text.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		text.addMouseListener(new OpenDetailListener(item.getItemId()));

		return row;
	}

	/** Opens an item's detail view when its name or price line is clicked. */
	private final class OpenDetailListener extends MouseAdapter
	{
		private final int itemId;

		/**
		 * @param itemId the item this row shows
		 */
		OpenDetailListener(int itemId)
		{
			this.itemId = itemId;
		}

		@Override
		public void mouseClicked(MouseEvent e)
		{
			openDetail(itemId);
		}
	}

	/** @return the item's icon, loaded asynchronously into a fixed-size label. */
	private JLabel buildRowIcon(WatchedItem item)
	{
		final JLabel icon = new JLabel();

		icon.setPreferredSize(new Dimension(ICON_WIDTH, ICON_WIDTH));
		icon.setHorizontalAlignment(SwingConstants.CENTER);

		final AsyncBufferedImage image = itemManager.getImage(item.getItemId(), 1, item.isStackable());

		image.addTo(icon);

		return icon;
	}

	/**
	 * @return the row's leading cluster: the drag handle, when dragging is possible,
	 *         followed by the item icon
	 */
	private JPanel buildRowLeading(WatchedItem item, boolean isPreview)
	{
		final JPanel leading = new JPanel(new BorderLayout(2, 0));

		leading.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		if (!isPreview && lastView.getSortMode() == SortMode.MANUAL)
			leading.add(buildDragHandle(item), BorderLayout.WEST);

		leading.add(buildRowIcon(item), BorderLayout.CENTER);

		return leading;
	}

	/**
	 * @return the grip the user drags to reorder. Only offered in manual sort — under
	 *         any other mode the displayed order is computed, so dragging a row would
	 *         appear to do nothing
	 */
	private JLabel buildDragHandle(WatchedItem item)
	{
		final JLabel handle = new JLabel("=", SwingConstants.CENTER);

		handle.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		handle.setFont(FontManager.getRunescapeSmallFont());
		handle.setToolTipText("Drag onto another row to reorder");
		handle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
		handle.setPreferredSize(new Dimension(10, ICON_WIDTH));
		handle.addMouseListener(new DragListener(item.getItemId(), groupKeyFor(item)));

		return handle;
	}

	/** @return the group key an item is currently drawn under. */
	private String groupKeyFor(WatchedItem item)
	{
		if (item.isFavorite())
			return CategoryState.FAVORITES_KEY;

		final String category = item.getCategory();

		if (category == null || category.trim().isEmpty())
			return CategoryState.UNCATEGORIZED_KEY;

		return lastView.getCategories().stream()
				.filter(c -> c.getName().equals(category))
				.findFirst()
				.map(CategoryState::getName)
				.orElse(CategoryState.UNCATEGORIZED_KEY);
	}

	/** @return the trailing button cluster for a row: add for a preview, remove for a watched item. */
	private JPanel buildRowButtons(WatchedItem item, boolean isPreview)
	{
		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));

		buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		if (isPreview)
		{
			final JButton add = smallButton("+", ADD_GREEN, "Add to watchlist");

			add.addActionListener(e -> actions.addWatchedItem(WatchItemMode.WATCH, item.getItemId()));
			buttons.add(add);

			return buttons;
		}

		final boolean starred = item.isFavorite();
		final JButton star = smallButton(starred ? "★" : "☆",
				starred ? STAR_GOLD : ColorScheme.MEDIUM_GRAY_COLOR,
				starred ? "Remove from favourites" : "Add to favourites");

		star.addActionListener(e -> actions.setFavorite(item.getItemId(), !starred));

		final JButton category = smallButton("…", ColorScheme.LIGHT_GRAY_COLOR, "Set category");

		category.addActionListener(e -> showCategoryMenu(category, item));

		final JButton remove = smallButton("×", REMOVE_RED, "Remove from watchlist");

		remove.addActionListener(e -> actions.removeWatchedItem(item.getItemId()));

		buttons.add(star);
		buttons.add(category);
		buttons.add(remove);

		return buttons;
	}

	/** Opens the per-item category picker, with a way into the manage dialog. */
	private void showCategoryMenu(JButton anchor, WatchedItem item)
	{
		final JPopupMenu menu = new JPopupMenu();
		final JMenuItem clear = new JMenuItem("Uncategorised");

		clear.addActionListener(e -> actions.setItemCategory(item.getItemId(), null));
		menu.add(clear);

		if (!lastView.getCategories().isEmpty())
			menu.addSeparator();

		for (CategoryState category : lastView.getCategories())
		{
			final String name = category.getName();
			final JMenuItem entry = new JMenuItem(name.equals(item.getCategory()) ? name + "  ." : name);

			entry.addActionListener(e -> actions.setItemCategory(item.getItemId(), name));
			menu.add(entry);
		}

		final JMenuItem manage = new JMenuItem("Manage categories...");

		manage.addActionListener(e -> openManageCategoriesDialog());
		menu.addSeparator();
		menu.add(manage);

		menu.show(anchor, 0, anchor.getHeight());
	}

	/** Opens the share menu: copy the watchlist out as a code, or paste one in. */
	private void showShareMenu()
	{
		final JPopupMenu menu = new JPopupMenu();
		final JMenuItem export = new JMenuItem("Copy share code");

		export.addActionListener(e -> showExportDialog());

		final JMenuItem load = new JMenuItem("Import share code...");

		load.addActionListener(e -> showImportDialog());

		menu.add(export);
		menu.add(load);
		menu.show(shareButton, 0, shareButton.getHeight());
	}

	/** Shows the generated code in a selectable field, already selected for copying. */
	private void showExportDialog()
	{
		final JTextArea field = new JTextArea(actions.exportShareCode(), 4, 28);

		field.setLineWrap(true);
		field.setEditable(false);
		field.selectAll();

		JOptionPane.showMessageDialog(this, new JScrollPane(field),
				"Share code — copy this", JOptionPane.PLAIN_MESSAGE);
	}

	/** Prompts for a code and reports what the plugin made of it. */
	private void showImportDialog()
	{
		final String code = JOptionPane.showInputDialog(this,
				"Paste a Pricewatch share code. Items you already watch are left as they are.");

		if (code == null)
			return;

		JOptionPane.showMessageDialog(this, actions.importShareCode(code));
	}

	/** Opens the category management dialog: create, rename, delete, reorder and auto-categorise. */
	private void openManageCategoriesDialog()
	{
		final List<String> names = lastView.getCategories().stream()
				.map(CategoryState::getName)
				.collect(Collectors.toList());

		final JPanel content = new JPanel();

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		final JComboBox<String> picker = new JComboBox<>(names.toArray(new String[0]));

		if (!names.isEmpty())
			content.add(labelled("Category", picker));

		content.add(buildManageButtons(picker, names));

		JOptionPane.showMessageDialog(this, content, "Manage categories", JOptionPane.PLAIN_MESSAGE);
	}

	/** @return the action buttons for the manage dialog, operating on the picker's selection. */
	private JPanel buildManageButtons(JComboBox<String> picker, List<String> names)
	{
		final JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 4));

		buttons.add(dialogButton("New category...", e ->
		{
			String name = JOptionPane.showInputDialog(this, "Category name");

			if (name != null)
				actions.createCategory(name);
		}));

		if (!names.isEmpty())
		{
			buttons.add(dialogButton("Rename...", e ->
			{
				String selected = (String) picker.getSelectedItem();
				String name = JOptionPane.showInputDialog(this, "New name for " + selected, selected);

				if (name != null)
					actions.renameCategory(selected, name);
			}));

			buttons.add(dialogButton("Delete", e -> actions.deleteCategory((String) picker.getSelectedItem())));

			buttons.add(dialogButton("Move up", e ->
			{
				String selected = (String) picker.getSelectedItem();

				actions.reorderCategory(selected, names.indexOf(selected) - 1);
			}));

			buttons.add(dialogButton("Move down", e ->
			{
				String selected = (String) picker.getSelectedItem();

				actions.reorderCategory(selected, names.indexOf(selected) + 1);
			}));
		}

		buttons.add(dialogButton("Auto-categorise uncategorised", e ->
				JOptionPane.showMessageDialog(this, actions.autoCategorize(false))));

		buttons.add(dialogButton("Auto-categorise everything", e ->
				JOptionPane.showMessageDialog(this, actions.autoCategorize(true))));

		return buttons;
	}

	/** @return a plain dialog button wired to an action. */
	private static JButton dialogButton(String text, ActionListener action)
	{
		final JButton button = new JButton(text);

		button.addActionListener(action);

		return button;
	}

	/** @return a label and a component side by side, for the dialog's form rows. */
	private static JPanel labelled(String text, JComponent field)
	{
		final JPanel row = new JPanel(new BorderLayout(6, 0));

		row.add(new JLabel(text), BorderLayout.WEST);
		row.add(field, BorderLayout.CENTER);

		return row;
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
		actions.addWatchedItem(mode, itemId);
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

	/** Where one rendered row sits, so a drop point can be resolved back to an item. */
	private static final class RowRef
	{
		private final JPanel row;
		private final int itemId;
		private final String groupKey;

		/**
		 * @param row      the rendered row component
		 * @param itemId   the item it shows
		 * @param groupKey the group it was drawn under
		 */
		RowRef(JPanel row, int itemId, String groupKey)
		{
			this.row = row;
			this.itemId = itemId;
			this.groupKey = groupKey;
		}
	}

	/**
	 * Drags a row onto another to reorder the watchlist, and onto a row in another
	 * group to move it there.
	 *
	 * <p>Only the release point matters — there is no drag ghost and no autoscroll.
	 * The drop resolves to whichever row sits under the cursor, and the moved item
	 * lands immediately before it.
	 */
	private final class DragListener extends MouseAdapter
	{
		private final int itemId;
		private final String groupKey;

		/**
		 * @param itemId   the item this handle belongs to
		 * @param groupKey the group it is currently drawn under
		 */
		DragListener(int itemId, String groupKey)
		{
			this.itemId = itemId;
			this.groupKey = groupKey;
		}

		@Override
		public void mouseReleased(MouseEvent e)
		{
			final RowRef target = rowAt(
					SwingUtilities.convertPoint((JComponent) e.getSource(), e.getPoint(), list));

			if (target == null || target.itemId == itemId)
				return;

			if (!target.groupKey.equals(groupKey))
				actions.setItemCategory(itemId, categoryFor(target.groupKey));

			actions.reorderWatchlist(WatchlistReorder.moveBefore(watchedIds, itemId, target.itemId));
		}
	}

	/** @return the rendered row containing a point in the list's coordinate space, or {@code null}. */
	private RowRef rowAt(Point point)
	{
		return rowRefs.stream()
				.filter(ref -> ref.row.getBounds().contains(point))
				.findFirst()
				.orElse(null);
	}

	/**
	 * @return the category a group key represents, or {@code null} for the two special
	 *         groups — dropping into Favourites or Uncategorised clears the category
	 *         rather than inventing one named after the group
	 */
	private static String categoryFor(String groupKey)
	{
		if (CategoryState.FAVORITES_KEY.equals(groupKey) || CategoryState.UNCATEGORIZED_KEY.equals(groupKey))
			return null;

		return groupKey;
	}

	/** Toggles a group's collapsed state when its header is clicked. */
	private final class CollapseListener extends MouseAdapter
	{
		private final WatchlistGrouping.Group group;

		/**
		 * @param group the group this header belongs to
		 */
		CollapseListener(WatchlistGrouping.Group group)
		{
			this.group = group;
		}

		@Override
		public void mousePressed(MouseEvent e)
		{
			actions.setGroupCollapsed(group.getKey(), !group.isCollapsed());
		}
	}

	/** Re-renders the watchlist on every edit to the filter field. */
	private final class FilterListener implements DocumentListener
	{
		@Override
		public void insertUpdate(DocumentEvent e)
		{
			redraw();
		}

		@Override
		public void removeUpdate(DocumentEvent e)
		{
			redraw();
		}

		@Override
		public void changedUpdate(DocumentEvent e)
		{
			redraw();
		}
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
