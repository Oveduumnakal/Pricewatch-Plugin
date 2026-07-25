/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;

/**
 * Plugin entry point: owns the watchlist, keeps it fed with live wiki prices,
 * and pushes it into the side panel.
 *
 * <p>Owns the watchlist, the categories it is grouped into, and the view state
 * the panel renders it with. The panel never mutates any of it: it calls back
 * through {@link WatchlistActions} so every change and its persistence happen
 * here, on the client thread.
 */
@Slf4j
@PluginDescriptor(
		name = "Pricewatch",
		description = "Watchlist of Grand Exchange prices with charts, market ratings and alerts",
		tags = {"price", "prices", "ge", "grand exchange", "market", "watchlist", "alert", "chart"}
)
public class PricewatchPlugin extends Plugin implements WatchlistActions
{
	private static final Type PERSIST_TYPE = new TypeToken<List<PersistedItem>>(){}.getType();

	private static final Type PRICE_CACHE_TYPE = new TypeToken<Map<Integer, CachedPrice>>(){}.getType();

	/** How often at most the price cache is rewritten to config during regular refreshes. */
	private static final Duration PRICE_CACHE_SAVE_INTERVAL = Duration.ofMinutes(5);

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private Gson gson;

	@Inject
	private PricewatchConfig config;

	@Inject
	private WikiRealtimePriceClient wikiPriceClient;

	private WatchlistShareCodec shareCodec;

	private PricewatchPanel panel;

	private NavigationButton navButton;

	private final Map<Integer, WatchedItem> watchedItems = new LinkedHashMap<>();

	/** An item being looked at without being watched: priced like the rest, never persisted. */
	private WatchedItem previewItem;

	private final ViewState viewState = new ViewState(
			SortMode.MANUAL, false, false, new ArrayList<>(), false, false);

	private final List<CategoryState> categories = new ArrayList<>();

	private boolean favoritesCollapsed;

	private boolean uncategorizedCollapsed;

	private Map<Integer, WikiRealtimePriceClient.ItemMapping> itemMappings = new HashMap<>();

	private boolean mappingsLoaded;

	private boolean itemsLoaded;

	private ScheduledFuture<?> priceRefreshTask;

	private Instant lastPriceCacheSave;

	/**
	 * Serializable snapshot of a watched item, stored as JSON in the RS profile config.
	 * Package-private so {@code PersistedSchemaSnapshotTest} can guard its shape; any
	 * field change fails the schema snapshot until it is regenerated and explained in
	 * the PR.
	 */
	static class PersistedItem
	{
		int itemId;
		boolean favorite;
		String category;
		boolean onOverlay;
	}

	/**
	 * Last-known prices for one watched item, stored as JSON in the RS profile config
	 * so the panel can show values immediately at startup instead of placeholders
	 * until the first wiki fetch lands. Package-private so
	 * {@code PersistedSchemaSnapshotTest} can guard its shape.
	 */
	static class CachedPrice
	{
		long high;
		long low;
		long avg;
		long highTime;
		long lowTime;
	}

	private static final Type CATEGORIES_TYPE = new TypeToken<CategoryData>(){}.getType();

	/**
	 * Serializable snapshot of the category definitions and the collapsed state of the
	 * two special groups. Package-private so {@code PersistedSchemaSnapshotTest} can
	 * guard its shape; any field change fails the schema snapshot until it is
	 * regenerated and explained in the PR.
	 */
	static class CategoryData
	{
		List<CategoryState> categories;
		boolean favoritesCollapsed;
		boolean uncategorizedCollapsed;
	}

	/**
	 * Supplies the config instance to the RuneLite injector.
	 *
	 * @param configManager the client's config manager
	 * @return this plugin's settings
	 */
	@Provides
	PricewatchConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PricewatchConfig.class);
	}

	/**
	 * Builds the panel, adds its navigation button, fetches the GE item mapping and
	 * starts the recurring price refresh.
	 */
	@Override
	protected void startUp()
	{
		shareCodec = new WatchlistShareCodec(gson);
		panel = new PricewatchPanel(itemManager, config, this);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");

		navButton = NavigationButton.builder()
				.tooltip("Pricewatch")
				.icon(icon)
				.priority(7)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);

		executor.execute(this::fetchItemMappings);
		scheduleRefresh();
	}

	/** Persists the price cache, stops the refresh task and clears all in-memory state. */
	@Override
	protected void shutDown()
	{
		persistPriceCache();

		if (priceRefreshTask != null)
		{
			priceRefreshTask.cancel(false);
			priceRefreshTask = null;
		}

		clientToolbar.removeNavigation(navButton);

		watchedItems.clear();
		categories.clear();
		favoritesCollapsed = false;
		uncategorizedCollapsed = false;
		previewItem = null;
		itemMappings = new HashMap<>();
		mappingsLoaded = false;
		itemsLoaded = false;
		lastPriceCacheSave = null;
		shareCodec = null;
		navButton = null;
		panel = null;
	}

	/**
	 * Loads the watchlist and hydrates its cached prices once logged in. The
	 * RS-profile config is not readable at {@code startUp} on the login screen, so
	 * this is the only place the load can happen.
	 *
	 * @param event the client's game state change
	 */
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN || itemsLoaded)
			return;

		itemsLoaded = true;
		loadViewState();
		loadCategories();
		loadPersistedItems();
		hydratePriceCache();
		refreshPanel();
	}

	/**
	 * Reschedules the price refresh when its interval changes. Ignores other
	 * plugins' groups.
	 *
	 * @param event the config change
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!PricewatchConfig.GROUP.equals(event.getGroup()))
			return;

		if (PricewatchConfig.KEY_PRICE_REFRESH_SECONDS.equals(event.getKey()))
		{
			scheduleRefresh();
			return;
		}

		refreshPanel();
	}

	/**
	 * Adds a "Watch item" / "Unwatch item" right-click option to item menu entries,
	 * when enabled. The wording is deliberately distinct from the Stockpile plugin's
	 * "Track Item", since a user running both sees both entries on the same menu.
	 *
	 * @param event the menu being opened
	 */
	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!config.addContextMenuOption())
			return;

		final MenuEntry[] entries = event.getMenuEntries();
		for (int idx = entries.length - 1; idx >= 0; --idx)
		{
			final MenuEntry entry = entries[idx];
			int itemId = getItemIdFromMenuEntry(entry);
			if (itemId <= 0)
				continue;

			final int canonicalId = itemManager.canonicalize(itemId);
			final boolean watched = watchedItems.containsKey(canonicalId);

			client.getMenu().createMenuEntry(1)
					.setOption(watched
							? ColorUtil.prependColorTag("Unwatch item", config.unwatchItemColor())
							: ColorUtil.prependColorTag("Watch item", config.watchItemColor()))
					.setTarget(entry.getTarget())
					.setType(MenuAction.RUNELITE)
					.onClick(e ->
					{
						if (watched)
							removeWatchedItem(canonicalId);
						else
							addWatchedItem(WatchItemMode.WATCH, canonicalId);
					});
			return;
		}
	}

	/** @return the item id behind a menu entry (ground item or inventory/bank widget), or -1 if none. */
	private int getItemIdFromMenuEntry(MenuEntry entry)
	{
		switch (entry.getType())
		{
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
			case EXAMINE_ITEM_GROUND:
				return entry.getIdentifier();
			default:
				break;
		}

		Widget w = entry.getWidget();
		if (w == null)
			return -1;

		int interfaceId = WidgetUtil.componentToInterface(w.getId());
		if (interfaceId == InterfaceID.INVENTORY
				|| interfaceId == InterfaceID.BANKMAIN
				|| interfaceId == InterfaceID.BANKSIDE
				|| interfaceId == InterfaceID.SHOPMAIN
				|| interfaceId == InterfaceID.SHOPSIDE)
		{
			return w.getItemId();
		}

		return -1;
	}

	/** (Re)schedules the recurring price refresh at the configured rate (min 30s), replacing any prior task. */
	private void scheduleRefresh()
	{
		if (priceRefreshTask != null)
			priceRefreshTask.cancel(false);

		int rate = Math.max(30, config.priceRefreshSeconds());
		priceRefreshTask = executor.scheduleAtFixedRate(
				this::refreshGePrices, 0, rate, TimeUnit.SECONDS
		);
	}

	/** Fetches GE item metadata in the background, keeping the previous map on failure. */
	private void fetchItemMappings()
	{
		Map<Integer, WikiRealtimePriceClient.ItemMapping> mappings = wikiPriceClient.fetchMapping();
		if (mappings.isEmpty())
			return;

		itemMappings = mappings;
		mappingsLoaded = true;

		clientThread.invokeLater(this::resolveTradeabilityForAll);
	}

	/**
	 * Re-evaluates GE-tradeability for every watched item now that the wiki mapping
	 * is available, then refreshes the panel.
	 */
	private void resolveTradeabilityForAll()
	{
		watchedItems.values().forEach(this::resolveTradeable);
		refreshPanel();
	}

	/**
	 * Narrows an item's tradeable flag using the wiki mapping: an item the game
	 * reports as tradeable but which is absent from the Grand Exchange mapping (e.g.
	 * coins) is reclassified as non-tradeable so it reads as "not tradeable" rather
	 * than a price-load failure. No-op until the mapping has loaded, so a slow fetch
	 * never mislabels a genuinely tradeable item.
	 *
	 * @param item the item to re-evaluate
	 */
	private void resolveTradeable(WatchedItem item)
	{
		item.setStackable(itemManager.getItemComposition(item.getItemId()).isStackable());

		if (!mappingsLoaded)
			return;

		if (item.isTradeable() && !itemMappings.containsKey(item.getItemId()))
		{
			item.setTradeable(false);
			item.setPriceLoadFailed(false);
		}
	}

	/** Copies cached GE metadata (buy limit, value, alch values) onto an item, if available. */
	private void applyItemMetadata(WatchedItem item)
	{
		resolveTradeable(item);

		WikiRealtimePriceClient.ItemMapping mapping = itemMappings.get(item.getItemId());
		if (mapping == null)
			return;

		item.setBuyLimit(mapping.getLimit());
		item.setGeValue(mapping.getValue());
		item.setHighAlch(mapping.getHighAlch());
		item.setLowAlch(mapping.getLowAlch());
		item.setMetadataLoaded(true);
	}

	/** Fetches the latest prices for all items in the background, then applies them on the client thread. */
	private void refreshGePrices()
	{
		executor.execute(() ->
		{
			Map<Integer, WikiRealtimePriceClient.ItemPrices> all = wikiPriceClient.fetchAll();

			clientThread.invokeLater(() -> applyGePrices(all));
		});
	}

	/**
	 * Applies freshly fetched prices to every watched item, throttles a price-cache
	 * save, then requests each item's 5m series so its window stats stay current. A
	 * failed (empty) fetch only triggers a plain refresh.
	 *
	 * @param all the latest prices keyed by item id
	 */
	private void applyGePrices(Map<Integer, WikiRealtimePriceClient.ItemPrices> all)
	{
		for (WatchedItem item : priceableItems())
		{
			WikiRealtimePriceClient.ItemPrices prices = all.get(item.getItemId());
			if (prices != null)
				applyLivePrices(item, prices);
			else if (!item.hasPrices() && item.isTradeable() && mappingsLoaded)
				item.setPriceLoadFailed(true);
		}

		if (all.isEmpty())
		{
			refreshPanel();
			return;
		}

		if (lastPriceCacheSave == null
				|| Duration.between(lastPriceCacheSave, Instant.now()).compareTo(PRICE_CACHE_SAVE_INTERVAL) >= 0)
			persistPriceCache();

		refreshPanel();

		for (WatchedItem item : priceableItems())
		{
			if (item.isTradeable() && item.hasPrices())
				requestSeries(item.getItemId());
		}
	}

	/** @return every item that should receive live prices: the watchlist plus any preview entry. */
	private List<WatchedItem> priceableItems()
	{
		List<WatchedItem> items = new ArrayList<>(watchedItems.values());

		if (previewItem != null)
			items.add(previewItem);

		return items;
	}

	/** @return the watched or previewed item with this id, or {@code null}. */
	private WatchedItem lookupItem(int itemId)
	{
		WatchedItem item = watchedItems.get(itemId);
		if (item != null)
			return item;

		return previewItem != null && previewItem.getItemId() == itemId ? previewItem : null;
	}

	/**
	 * Applies a freshly fetched price set to an item: records per-side deltas, updates
	 * current prices, and refreshes its LIVE window stats.
	 *
	 * @param item   the item to update
	 * @param prices the newly fetched prices
	 */
	private void applyLivePrices(WatchedItem item, WikiRealtimePriceClient.ItemPrices prices)
	{
		if (item.hasPrices())
		{
			item.setHighDelta(Long.compare(prices.getHigh(), item.getHighPrice()));
			item.setLowDelta(Long.compare(prices.getLow(), item.getLowPrice()));
			item.setAvgDelta(Long.compare(prices.avg(), item.getAvgPrice()));
			item.setPrevHighPrice(item.getHighPrice());
			item.setPrevLowPrice(item.getLowPrice());
			item.setPrevAvgPrice(item.getAvgPrice());
			item.setHasDeltas(true);
		}

		item.setHighPrice(prices.getHigh());
		item.setLowPrice(prices.getLow());
		item.setAvgPrice(prices.avg());
		item.setLatestHighTime(prices.getHighTime());
		item.setLatestLowTime(prices.getLowTime());
		item.setPriceCacheHydrated(false);
		item.setPriceLoadFailed(false);
		item.getWindowStats().put(TimeWindow.LIVE,
				new PriceStats(prices.getHigh(), prices.getLow(), prices.avg(), 0));
	}

	/** Fetches just the 5m series for an item in the background and recomputes its window stats. */
	private void requestSeries(int itemId)
	{
		executor.execute(() ->
		{
			List<WikiRealtimePriceClient.PricePoint> points = wikiPriceClient.fetchTimeseries(itemId, "5m");
			clientThread.invokeLater(() ->
			{
				WatchedItem item = lookupItem(itemId);
				if (item == null)
					return;

				item.setSeries5m(points);
				applyItemMetadata(item);
				recomputeWindowStats(item);
			});
		});
	}

	/** Rebuilds an item's per-window {@link PriceStats} from its current prices (LIVE) and history series. */
	private void recomputeWindowStats(WatchedItem item)
	{
		Map<TimeWindow, PriceStats> stats = new EnumMap<>(TimeWindow.class);
		for (TimeWindow w : TimeWindow.values())
		{
			if (w == TimeWindow.NONE)
				continue;

			if (w == TimeWindow.LIVE)
			{
				stats.put(w, new PriceStats(item.getHighPrice(), item.getLowPrice(), item.getAvgPrice(), 0));
			}
			else
			{
				List<WikiRealtimePriceClient.PricePoint> series = item.getSeriesFor(w);
				if (series.isEmpty())
					series = item.getSeries5m();

				stats.put(w, WikiRealtimePriceClient.computeStats(series, w));
			}
		}

		item.setWindowStats(stats);
	}

	/**
	 * Adds an item to the watchlist, or sets it as the preview entry.
	 *
	 * <p>A {@link WatchItemMode#PREVIEW} item is shown with prices like any other
	 * but is never persisted, and there is only ever one — previewing a second
	 * replaces the first. Watching an item clears any preview of it.
	 *
	 * @param mode   whether to watch the item or only preview it
	 * @param itemId the item
	 */
	@Override
	public void addWatchedItem(WatchItemMode mode, int itemId)
	{
		clientThread.invokeLater(() ->
		{
			if (mode == WatchItemMode.PREVIEW)
			{
				if (watchedItems.containsKey(itemId))
					return;

				previewItem = buildItem(itemId, WatchItemMode.PREVIEW);
				refreshPanel();
				refreshGePrices();
				return;
			}

			if (previewItem != null && previewItem.getItemId() == itemId)
				previewItem = null;

			if (watchedItems.containsKey(itemId))
				return;

			watchedItems.put(itemId, buildItem(itemId, WatchItemMode.WATCH));
			persistWatchedItems();
			refreshPanel();
			refreshGePrices();
		});
	}

	/** @return a new item with its name, tradeability and cached GE metadata applied. */
	private WatchedItem buildItem(int itemId, WatchItemMode mode)
	{
		WatchedItem item = new WatchedItem(itemId, itemManager.getItemComposition(itemId).getName());

		item.setMode(mode);
		item.setTradeable(itemManager.getItemComposition(itemId).isTradeable());
		applyItemMetadata(item);

		return item;
	}

	/**
	 * Removes an item from the watchlist.
	 *
	 * @param itemId the item to stop watching
	 */
	@Override
	public void removeWatchedItem(int itemId)
	{
		clientThread.invokeLater(() ->
		{
			if (watchedItems.remove(itemId) == null)
				return;

			persistWatchedItems();
			refreshPanel();
		});
	}

	/**
	 * Stars or unstars an item and persists the change.
	 *
	 * @param itemId   the item
	 * @param favorite whether it should be a favourite
	 */
	@Override
	public void setFavorite(int itemId, boolean favorite)
	{
		clientThread.invokeLater(() ->
		{
			WatchedItem item = watchedItems.get(itemId);
			if (item == null || item.isFavorite() == favorite)
				return;

			item.setFavorite(favorite);
			persistWatchedItems();
			refreshPanel();
		});
	}

	/**
	 * Changes how the list is ordered and persists the choice.
	 *
	 * @param mode the new sort mode
	 */
	@Override
	public void setSortMode(SortMode mode)
	{
		if (viewState.getSortMode() == mode)
			return;

		viewState.setSortMode(mode);
		viewState.setSortReversed(false);
		persistViewState();
		refreshPanel();
	}

	/** Flips the current sort mode's direction and persists it. */
	@Override
	public void toggleSortReversed()
	{
		viewState.setSortReversed(!viewState.isSortReversed());
		persistViewState();
		refreshPanel();
	}

	/** Switches the compact row layout on or off and persists it. */
	@Override
	public void toggleCompactView()
	{
		viewState.setCompact(!viewState.isCompact());
		persistViewState();
		refreshPanel();
	}

	/** Writes the sort mode, direction and compact flag to the RS profile config. */
	private void persistViewState()
	{
		configManager.setRSProfileConfiguration(
				PricewatchConfig.GROUP, PricewatchConfig.KEY_SORT_MODE, viewState.getSortMode().name());
		configManager.setRSProfileConfiguration(
				PricewatchConfig.GROUP, PricewatchConfig.KEY_SORT_REVERSED, viewState.isSortReversed());
		configManager.setRSProfileConfiguration(
				PricewatchConfig.GROUP, PricewatchConfig.KEY_COMPACT_VIEW, viewState.isCompact());
	}

	/** Restores the view state, leaving the defaults in place for anything unreadable. */
	private void loadViewState()
	{
		String mode = configManager.getRSProfileConfiguration(
				PricewatchConfig.GROUP, PricewatchConfig.KEY_SORT_MODE);
		if (mode != null)
		{
			try
			{
				viewState.setSortMode(SortMode.valueOf(mode.trim()));
			}
			catch (IllegalArgumentException e)
			{
				log.warn("Unknown persisted sort mode {}; keeping the default", mode);
			}
		}

		viewState.setSortReversed(readFlag(PricewatchConfig.KEY_SORT_REVERSED));
		viewState.setCompact(readFlag(PricewatchConfig.KEY_COMPACT_VIEW));
	}

	/** @return a persisted boolean flag, defaulting to false when absent. */
	private boolean readFlag(String key)
	{
		Boolean value = configManager.getRSProfileConfiguration(PricewatchConfig.GROUP, key, Boolean.class);
		return Boolean.TRUE.equals(value);
	}

	/**
	 * Assigns an item to a category; a blank or null name clears it to Uncategorised.
	 *
	 * @param itemId   the item
	 * @param category the category name, or {@code null} to clear it
	 */
	@Override
	public void setItemCategory(int itemId, String category)
	{
		clientThread.invokeLater(() ->
		{
			WatchedItem item = watchedItems.get(itemId);
			if (item == null)
				return;

			item.setCategory(category == null || category.trim().isEmpty() ? null : category.trim());
			persistWatchedItems();
			refreshPanel();
		});
	}

	/**
	 * Rolls a group up or down and remembers it.
	 *
	 * @param groupKey  the group's persistence key
	 * @param collapsed whether it should be rolled up
	 */
	@Override
	public void setGroupCollapsed(String groupKey, boolean collapsed)
	{
		clientThread.invokeLater(() ->
		{
			if (CategoryState.FAVORITES_KEY.equals(groupKey))
			{
				favoritesCollapsed = collapsed;
			}
			else if (CategoryState.UNCATEGORIZED_KEY.equals(groupKey))
			{
				uncategorizedCollapsed = collapsed;
			}
			else
			{
				categories.stream()
						.filter(c -> c.getName().equals(groupKey))
						.findFirst()
						.ifPresent(c -> c.setCollapsed(collapsed));
			}

			persistCategories();
			refreshPanel();
		});
	}

	/**
	 * Creates a category, ignoring blanks and case-insensitive duplicates.
	 *
	 * @param name the new category's name
	 */
	@Override
	public void createCategory(String name)
	{
		clientThread.invokeLater(() ->
		{
			String trimmed = name == null ? "" : name.trim();
			if (trimmed.isEmpty() || categories.stream().anyMatch(c -> c.getName().equalsIgnoreCase(trimmed)))
				return;

			categories.add(new CategoryState(trimmed, false));
			persistCategories();
			refreshPanel();
		});
	}

	/**
	 * Renames a category and re-points its items, ignoring blanks and clashes.
	 *
	 * @param oldName the category to rename
	 * @param newName its new name
	 */
	@Override
	public void renameCategory(String oldName, String newName)
	{
		clientThread.invokeLater(() ->
		{
			String trimmed = newName == null ? "" : newName.trim();
			if (trimmed.isEmpty())
				return;

			CategoryState target = null;
			for (CategoryState c : categories)
			{
				if (c.getName().equals(oldName))
					target = c;
				else if (c.getName().equalsIgnoreCase(trimmed))
					return;
			}

			if (target == null)
				return;

			target.setName(trimmed);
			watchedItems.values().stream()
					.filter(item -> oldName.equals(item.getCategory()))
					.forEach(item -> item.setCategory(trimmed));

			persistCategories();
			persistWatchedItems();
			refreshPanel();
		});
	}

	/**
	 * Deletes a category, moving its items to Uncategorised.
	 *
	 * @param name the category to delete
	 */
	@Override
	public void deleteCategory(String name)
	{
		clientThread.invokeLater(() ->
		{
			if (!categories.removeIf(c -> c.getName().equals(name)))
				return;

			watchedItems.values().stream()
					.filter(item -> name.equals(item.getCategory()))
					.forEach(item -> item.setCategory(null));

			persistCategories();
			persistWatchedItems();
			refreshPanel();
		});
	}

	/**
	 * Moves a category to a new position in the ordered list.
	 *
	 * @param name        the category to move
	 * @param targetIndex where it should end up, clamped into range
	 */
	@Override
	public void reorderCategory(String name, int targetIndex)
	{
		clientThread.invokeLater(() ->
		{
			int from = -1;
			for (int i = 0; i < categories.size(); i++)
			{
				if (categories.get(i).getName().equals(name))
				{
					from = i;
					break;
				}
			}

			if (from < 0)
				return;

			int to = Math.max(0, Math.min(targetIndex, categories.size() - 1));
			if (to == from)
				return;

			categories.add(to, categories.remove(from));
			persistCategories();
			refreshPanel();
		});
	}

	/**
	 * Auto-assigns watched items to wiki-derived categories (see
	 * {@link ItemCategoryClassifier}), creating any that are missing. Non-destructive
	 * unless {@code includeCategorized} is set: by default only uncategorised items
	 * are touched, so manual assignments survive.
	 *
	 * @param includeCategorized also re-categorise items that already have a category
	 * @return a user-facing summary of what will change
	 */
	@Override
	public String autoCategorize(boolean includeCategorized)
	{
		long willChange = watchedItems.values().stream()
				.filter(item -> inAutoCategorizeScope(item, includeCategorized))
				.filter(item -> !ItemCategoryClassifier.classify(item.getName()).equals(item.getCategory()))
				.count();

		clientThread.invokeLater(() -> applyAutoCategorize(includeCategorized));

		if (willChange == 0)
			return "Nothing to categorise — everything already matches.";

		return "Auto-categorised " + willChange + " item(s).";
	}

	/** @return whether this item is eligible for an auto-categorize run. */
	private boolean inAutoCategorizeScope(WatchedItem item, boolean includeCategorized)
	{
		return includeCategorized || item.getCategory() == null || item.getCategory().trim().isEmpty();
	}

	/** Classifies each in-scope item on the client thread, creating categories as needed. */
	private void applyAutoCategorize(boolean includeCategorized)
	{
		boolean changed = false;
		List<CategoryState> created = new ArrayList<>();
		for (WatchedItem item : watchedItems.values())
		{
			if (!inAutoCategorizeScope(item, includeCategorized))
				continue;

			String target = ItemCategoryClassifier.classify(item.getName());
			if (target.equals(item.getCategory()))
				continue;

			if (categories.stream().noneMatch(c -> c.getName().equalsIgnoreCase(target)))
			{
				CategoryState category = new CategoryState(target, false);
				categories.add(category);
				created.add(category);
			}

			item.setCategory(target);
			changed = true;
		}

		if (changed)
		{
			orderGeneratedCategories(created);
			persistCategories();
			persistWatchedItems();
			refreshPanel();
		}
	}

	/**
	 * Orders a run's generated categories alphabetically after any pre-existing
	 * (manually ordered) ones, then keeps "Other" at the very end.
	 */
	private void orderGeneratedCategories(List<CategoryState> created)
	{
		categories.removeAll(created);
		created.stream()
				.sorted(Comparator.comparing(CategoryState::getName, String.CASE_INSENSITIVE_ORDER))
				.forEach(categories::add);

		List<CategoryState> other = categories.stream()
				.filter(c -> ItemCategoryClassifier.OTHER.equalsIgnoreCase(c.getName()))
				.collect(Collectors.toList());

		categories.removeAll(other);
		categories.addAll(other);
	}

	/** Serializes the category definitions and group collapsed state to per-profile config. */
	private void persistCategories()
	{
		CategoryData data = new CategoryData();

		data.categories = new ArrayList<>(categories);
		data.favoritesCollapsed = favoritesCollapsed;
		data.uncategorizedCollapsed = uncategorizedCollapsed;

		configManager.setRSProfileConfiguration(
				PricewatchConfig.GROUP, PricewatchConfig.KEY_CATEGORIES, gson.toJson(data, CATEGORIES_TYPE));
	}

	/** Restores the category definitions and group collapsed state from per-profile config. */
	private void loadCategories()
	{
		categories.clear();
		favoritesCollapsed = false;
		uncategorizedCollapsed = false;

		String saved = configManager.getRSProfileConfiguration(
				PricewatchConfig.GROUP, PricewatchConfig.KEY_CATEGORIES);
		if (saved == null || saved.trim().isEmpty())
			return;

		try
		{
			CategoryData data = gson.fromJson(saved.trim(), CATEGORIES_TYPE);
			if (data == null)
				return;

			if (data.categories != null)
				data.categories.stream()
						.filter(c -> c != null && c.getName() != null && !c.getName().trim().isEmpty())
						.forEach(categories::add);

			favoritesCollapsed = data.favoritesCollapsed;
			uncategorizedCollapsed = data.uncategorizedCollapsed;
		}
		catch (JsonSyntaxException e)
		{
			log.warn("Failed to parse persisted category JSON; ignoring", e);
		}
	}

	/**
	 * Replaces the watchlist's stored order after a drag.
	 *
	 * <p>Applied only when the proposed order is a faithful permutation of the
	 * current one, so a drag that resolved against a watchlist which has since
	 * changed is discarded rather than dropping or duplicating items.
	 *
	 * @param orderedIds every watched item id, in the new order
	 */
	@Override
	public void reorderWatchlist(List<Integer> orderedIds)
	{
		clientThread.invokeLater(() ->
		{
			List<Integer> current = new ArrayList<>(watchedItems.keySet());
			if (!WatchlistReorder.isPermutationOf(current, orderedIds))
				return;

			Map<Integer, WatchedItem> reordered = new LinkedHashMap<>();
			for (Integer itemId : orderedIds)
				reordered.put(itemId, watchedItems.get(itemId));

			watchedItems.clear();
			watchedItems.putAll(reordered);
			persistWatchedItems();
			refreshPanel();
		});
	}

	/**
	 * @return a shareable code for the current watchlist and its categories
	 */
	@Override
	public String exportShareCode()
	{
		List<WatchlistShareCodec.Entry> entries = watchedItems.values().stream()
				.map(item -> new WatchlistShareCodec.Entry(
						item.getItemId(), item.getCategory(), item.isFavorite(), item.isOnOverlay()))
				.collect(Collectors.toList());

		return shareCodec.encode(new WatchlistShareCodec.Snapshot(1, entries, new ArrayList<>(categories)));
	}

	/**
	 * Merges a pasted share code into the current watchlist.
	 *
	 * <p>Deliberately a merge rather than a replace: a code is something you paste
	 * from chat, and having it silently wipe a watchlist you have curated would be
	 * the worst possible outcome of a misclick. Items already watched keep their own
	 * category and favourite state.
	 *
	 * @param code the pasted token
	 * @return a user-facing summary of what was imported, or why it was refused
	 */
	@Override
	public String importShareCode(String code)
	{
		WatchlistShareCodec.Snapshot snapshot = shareCodec.decode(code);
		if (snapshot == null)
			return "That doesn't look like a Pricewatch share code.";

		if (snapshot.getItems().isEmpty())
			return "That code has no items in it.";

		clientThread.invokeLater(() -> applyImportedList(snapshot));

		return "Importing " + snapshot.getItems().size() + " item(s).";
	}

	/** Adds every unseen item and category from a decoded snapshot, leaving existing entries alone. */
	private void applyImportedList(WatchlistShareCodec.Snapshot snapshot)
	{
		for (CategoryState imported : snapshot.getCategories())
		{
			if (imported == null || imported.getName() == null || imported.getName().trim().isEmpty())
				continue;

			if (categories.stream().noneMatch(c -> c.getName().equalsIgnoreCase(imported.getName())))
				categories.add(new CategoryState(imported.getName().trim(), imported.isCollapsed()));
		}

		for (WatchlistShareCodec.Entry entry : snapshot.getItems())
		{
			if (entry == null || watchedItems.containsKey(entry.getId()))
				continue;

			WatchedItem item = buildItem(entry.getId(), WatchItemMode.WATCH);

			item.setCategory(entry.getCategory());
			item.setFavorite(entry.isFavorite());
			item.setOnOverlay(entry.isOnOverlay());
			watchedItems.put(entry.getId(), item);
		}

		persistCategories();
		persistWatchedItems();
		refreshPanel();
		refreshGePrices();
	}

	/** Writes the watchlist to the RS profile config. */
	private void persistWatchedItems()
	{
		List<PersistedItem> list = new ArrayList<>(watchedItems.size());
		for (WatchedItem item : watchedItems.values())
		{
			PersistedItem p = new PersistedItem();
			p.itemId = item.getItemId();
			p.favorite = item.isFavorite();
			p.category = item.getCategory();
			p.onOverlay = item.isOnOverlay();
			list.add(p);
		}

		configManager.setRSProfileConfiguration(
				PricewatchConfig.GROUP, PricewatchConfig.KEY_WATCHED_ITEMS, gson.toJson(list, PERSIST_TYPE));
	}

	/**
	 * Restores the watchlist from the per-profile JSON written by
	 * {@link #persistWatchedItems()}. A profile that has never watched anything
	 * simply starts empty.
	 */
	private void loadPersistedItems()
	{
		String saved = configManager.getRSProfileConfiguration(
				PricewatchConfig.GROUP, PricewatchConfig.KEY_WATCHED_ITEMS);

		List<PersistedItem> list = null;
		if (saved != null && saved.trim().startsWith("["))
		{
			try
			{
				list = gson.fromJson(saved.trim(), PERSIST_TYPE);
			}
			catch (JsonSyntaxException e)
			{
				log.warn("Failed to parse persisted watchlist JSON; ignoring", e);
			}
		}

		if (list == null)
			return;

		for (PersistedItem p : list)
			restoreItem(p.itemId, p.favorite, p.category, p.onOverlay);
	}

	/** Rebuilds one watched item from its persisted fields. */
	private void restoreItem(int itemId, boolean favorite, String category, boolean onOverlay)
	{
		if (watchedItems.containsKey(itemId))
			return;

		WatchedItem item = buildItem(itemId, WatchItemMode.WATCH);

		item.setFavorite(favorite);
		item.setCategory(category);
		item.setOnOverlay(onOverlay);
		watchedItems.put(itemId, item);
	}

	/**
	 * Writes every priced watched item's current prices to the RS profile config.
	 * Called throttled from refreshes and unconditionally at shutdown.
	 */
	private void persistPriceCache()
	{
		Map<Integer, CachedPrice> cache = new HashMap<>();
		for (WatchedItem item : watchedItems.values())
		{
			if (!item.hasPrices())
				continue;

			CachedPrice p = new CachedPrice();
			p.high = item.getHighPrice();
			p.low = item.getLowPrice();
			p.avg = item.getAvgPrice();
			p.highTime = item.getLatestHighTime();
			p.lowTime = item.getLatestLowTime();
			cache.put(item.getItemId(), p);
		}

		if (cache.isEmpty())
			return;

		lastPriceCacheSave = Instant.now();
		configManager.setRSProfileConfiguration(
				PricewatchConfig.GROUP, PricewatchConfig.KEY_PRICE_CACHE, gson.toJson(cache, PRICE_CACHE_TYPE));
	}

	/**
	 * Hydrates watched items from the persisted price cache so the panel shows
	 * last-known values instead of placeholders at startup. Live fetches simply
	 * overwrite these; items that already have prices are never touched.
	 */
	private void hydratePriceCache()
	{
		String saved = configManager.getRSProfileConfiguration(
				PricewatchConfig.GROUP, PricewatchConfig.KEY_PRICE_CACHE);
		if (saved == null || saved.trim().isEmpty())
			return;

		Map<Integer, CachedPrice> cache;
		try
		{
			cache = gson.fromJson(saved, PRICE_CACHE_TYPE);
		}
		catch (JsonSyntaxException e)
		{
			log.warn("Failed to parse persisted price cache; ignoring", e);
			return;
		}

		if (cache == null || cache.isEmpty())
			return;

		for (Map.Entry<Integer, CachedPrice> entry : cache.entrySet())
		{
			WatchedItem item = watchedItems.get(entry.getKey());
			if (item == null || item.hasPrices())
				continue;

			CachedPrice p = entry.getValue();
			item.setHighPrice(p.high);
			item.setLowPrice(p.low);
			item.setAvgPrice(p.avg);
			item.setLatestHighTime(p.highTime);
			item.setLatestLowTime(p.lowTime);
			item.setPriceCacheHydrated(true);
		}
	}

	/** Pushes the current watchlist into the panel on the Swing EDT. */
	private void refreshPanel()
	{
		if (panel == null)
			return;

		final List<WatchedItem> snapshot = new ArrayList<>(watchedItems.values());
		final WatchedItem preview = previewItem;
		final ViewState view = new ViewState(
				viewState.getSortMode(), viewState.isSortReversed(), viewState.isCompact(),
				new ArrayList<>(categories), favoritesCollapsed, uncategorizedCollapsed);

		SwingUtilities.invokeLater(() -> panel.rebuild(snapshot, preview, view));
	}
}
