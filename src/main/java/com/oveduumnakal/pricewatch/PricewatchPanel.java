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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
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
			SortMode.MANUAL, false, false, new ArrayList<>(), false, false);

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
	}

	/** Re-renders the rows from the last data pushed in, applying the current filter. */
	private void redraw()
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

		return row;
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
