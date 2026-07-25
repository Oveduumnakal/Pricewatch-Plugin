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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
 * <p>This is the phase 1 price spine. The watchlist is seeded from a fixed list
 * of items because there is no way to add one yet; search, categories and the
 * detail view arrive in later phases.
 */
@Slf4j
@PluginDescriptor(
		name = "Pricewatch",
		description = "Watchlist of Grand Exchange prices with charts, market ratings and alerts",
		tags = {"price", "prices", "ge", "grand exchange", "market", "watchlist", "alert", "chart"}
)
public class PricewatchPlugin extends Plugin
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

	private PricewatchPanel panel;

	private NavigationButton navButton;

	private final Map<Integer, WatchedItem> watchedItems = new LinkedHashMap<>();

	/** An item being looked at without being watched: priced like the rest, never persisted. */
	private WatchedItem previewItem;

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
		panel = new PricewatchPanel(itemManager, config, this::addWatchedItem, this::removeWatchedItem);

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
		previewItem = null;
		itemMappings = new HashMap<>();
		mappingsLoaded = false;
		itemsLoaded = false;
		lastPriceCacheSave = null;
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
	void addWatchedItem(WatchItemMode mode, int itemId)
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
	void removeWatchedItem(int itemId)
	{
		clientThread.invokeLater(() ->
		{
			if (watchedItems.remove(itemId) == null)
				return;

			persistWatchedItems();
			refreshPanel();
		});
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

		SwingUtilities.invokeLater(() -> panel.rebuild(snapshot, preview));
	}
}
