/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oveduumnakal.pricewatch;

import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Notification;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;

@Slf4j
/**
 * Plugin entry point: wires up the side panel and overlays and drives all
 * tracking logic.
 *
 * <p>Responsibilities: persisting and restoring the tracked-item set; counting
 * each item across the watched inventory/bank containers (and the rune pouch);
 * polling the wiki for live prices, metadata, and history; maintaining each
 * item's cost-basis lots (FIFO acquire/close) for profit; and evaluating
 * user-defined notification rules. It subscribes to the relevant game events and
 * marshals UI work onto the Swing thread and network work onto a background
 * executor.
 */
@PluginDescriptor(
		name = "Pricewatch",
		description = "Track item quantities across your inventory and bank with live GE prices",
		tags = {"items", "bank", "inventory", "price", "ge", "tracker"},
		conflicts = {"Stockpile"}
)
public class PricewatchPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private PricewatchConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private WikiRealtimePriceClient wikiPriceClient;

	@Inject
	private Notifier notifier;

	@Inject
	private Gson gson;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PricewatchHighlightOverlay highlightOverlay;

	@Inject
	private PricewatchGroundOverlay groundOverlay;

	/** Maximum number of items shown in the on-screen overlay (fixed for now). */
	static final int OVERLAY_MAX = 5;

	/** One independently-draggable overlay box per slot; they start grouped in the same snap corner. */
	private final List<PricewatchScreenOverlay> screenOverlays = new ArrayList<>();

	private static final int[] RUNE_POUCH_TYPE_VARBITS = {
			VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
			VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6
	};
	private static final int[] RUNE_POUCH_QUANTITY_VARBITS = {
			VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
			VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6
	};
	private static final ImmutableSet<Integer> RUNE_POUCH_VARBITS;
	static
	{
		ImmutableSet.Builder<Integer> b = ImmutableSet.builder();
		for (int v : RUNE_POUCH_TYPE_VARBITS)
			b.add(v);

		for (int v : RUNE_POUCH_QUANTITY_VARBITS)
			b.add(v);

		RUNE_POUCH_VARBITS = b.build();
	}

	private static final ImmutableSet<Integer> TRACKED_CONTAINERS = ImmutableSet.of(
			InventoryID.INV,
			InventoryID.WORN,
			InventoryID.BANK,
			InventoryID.LOOTING_BAG,
			InventoryID.SEED_BOX,
			InventoryID.SEED_VAULT,
			InventoryID.TACKLE_BOX,
			InventoryID.FORESTRY_KIT,
			InventoryID.HUNTSMANS_KIT,
			InventoryID.BARBARIAN_KNAPSACK
	);

	/**
	 * Reward/loot containers that hand out free loot into the inventory (#215). These are not
	 * tracked as holdings — they are transient interfaces — but an inventory gain while one is
	 * open marks that gain as a zero-cost {@link AcquisitionSource#REWARD} rather than Unknown.
	 * Point-spending reward shops are deliberately excluded (their withdrawals are purchases,
	 * not free loot). The object-search rewards that loot straight to the inventory with no
	 * reward container are handled elsewhere: the Huntsman's loot sack via a menu hook
	 * ({@link #LOOT_SACK_OPTION}) and the Tempoross reward pool via a chat hook
	 * ({@link #REWARD_LOOT_PREFIX}).
	 */
	private static final ImmutableSet<Integer> REWARD_CONTAINERS = ImmutableSet.of(
			InventoryID.TRAWLER_REWARDINV,
			InventoryID.TRAIL_REWARDINV,
			InventoryID.RAIDS_REWARDS,
			InventoryID.TOB_CHESTS,
			InventoryID.TOA_CHESTS,
			InventoryID.COLOSSEUM_REWARDS,
			InventoryID.PMOON_REWARDINV,
			InventoryID.DOM_LOOTPILE
	);

	/**
	 * Menu option and target substring for the Huntsman's loot sack, whose contents land in
	 * the inventory with no reward {@link ItemContainer} to observe. Live capture confirmed the
	 * loot arrives on the same tick as the "Open" click, so stamping {@link #rewardContainerTick}
	 * here lets {@link #correlateReward()} claim it within the existing window (#215). The Tempoross
	 * reward pool and GOTR reward guardian remain deferred pending their own live capture.
	 */
	private static final String LOOT_SACK_OPTION = "Open";
	private static final String LOOT_SACK_TARGET = "loot sack";

	/**
	 * Chat-line prefix for the generic "loot to inventory" reward message ("You found some loot:
	 * N x Item"). The Tempoross reward pool (Net/Big-search) drops loot straight into the inventory
	 * with no reward {@link ItemContainer} and its object-search click lands three ticks before the
	 * loot; live capture (#215) confirmed this SPAM line fires on the same tick as the inventory
	 * gains, so stamping {@link #rewardContainerTick} here lets {@link #correlateReward()} claim the
	 * whole multi-item drop within the existing window. Other activities that surface reward loot
	 * through the same message (e.g. the GOTR reward guardian) are covered by the same hook.
	 */
	private static final String REWARD_LOOT_PREFIX = "You found some loot:";

	private final Map<Integer, TrackedItem> trackedItems = new LinkedHashMap<>();

	/** Ordered user-defined categories (names + collapsed state); the source of truth for category order. */
	private final List<CategoryState> categories = new ArrayList<>();
	private boolean favoritesCollapsed;
	private boolean uncategorizedCollapsed;

	/**
	 * Transient, non-persisted item backing the read-only detail preview (view-only
	 * button); not in {@link #trackedItems}.
	 */
	private TrackedItem previewItem;

	/** The item shown on the currently-open GE offer screen, or -1 when no offer screen is up (GE integration). */
	private int currentGeItem = -1;
	/** The native-style button injected onto the GE offer screen in Button mode, or null. */
	private Widget geButton;

	private final Map<Integer, Map<Integer, Integer>> containerCounts = new HashMap<>();

	private final Map<Integer, Integer> runePouchCounts = new HashMap<>();

	private final Set<Integer> seenContainersSinceLogin = new HashSet<>();

	private boolean runePouchSeenSinceLogin = false;

	/**
	 * Whether the current logged-in session has been initialised. Guards the one-time
	 * clear+reload so a respawn or region load re-firing {@code LOGGED_IN} mid-session
	 * doesn't wipe pending quantity changes (e.g. a death loss) or reset held state (#70).
	 * Set by whichever path initialises the session: the {@code LOGGED_IN} handler, or
	 * {@code startUp} when the plugin is enabled while already logged in — the two do the
	 * same load, so leaving the flag false there let the next region crossing re-clear.
	 */
	private boolean sessionInitialized = false;
	private int geLoginTick = -1;

	/** Whether an NPC shop interface is open, gating the coin-delta shop pricing (#67). */
	private boolean shopOpen = false;

	/** The partner-side trade container: the offer container id with the "other player" bit set. */
	private static final int TRADE_OTHER_CONTAINER = InventoryID.TRADEOFFER | 0x8000;

	/** Latest captured trade-offer sides (canonical id → qty), read when the trade completes (#66). */
	private final Map<Integer, Integer> myTradeOffer = new HashMap<>();
	private final Map<Integer, Integer> theirTradeOffer = new HashMap<>();

	/** Per-item units queued to move into trade suspension when this tick's offer removals apply (#66). */
	private final Map<Integer, Integer> pendingTradeSuspend = new HashMap<>();

	/** Per-item units queued to leave trade suspension when this tick's additions apply (offer withdrawn). */
	private final Map<Integer, Integer> pendingTradeUnsuspend = new HashMap<>();

	/** Skills whose XP drops identify a processing action for the basis-transfer pairing (#69). */
	private static final Set<Skill> PROCESSING_SKILLS = ImmutableSet.of(
			Skill.COOKING, Skill.SMITHING, Skill.CRAFTING, Skill.FLETCHING, Skill.HERBLORE, Skill.MAGIC,
			Skill.RUNECRAFT);

	/** Skills whose XP drops mark an inventory gain as gathered from the world at 0 cost (#213). */
	private static final Set<Skill> GATHERING_SKILLS = ImmutableSet.of(
			Skill.HUNTER, Skill.MINING, Skill.FISHING, Skill.WOODCUTTING, Skill.FARMING);

	/**
	 * Item categories whose members are used up in a single action — food eaten, a last potion
	 * dose drunk — so an unclaimed removal of one closes at 0 under
	 * {@link AcquisitionSource#CONSUMED} (its cost realizes as a loss) rather than an avg-price
	 * Unknown "sale" (#218). Keyed by {@link ItemCategoryClassifier} category names.
	 * <p>
	 * Two categories are deliberately excluded because they need their own attribution rather than
	 * this generic branch. Ammo splits into destroyed-on-use (a genuine 0-gp loss) and recoverable
	 * ammo that lands on the target's tile and belongs on the ground-suspension path (#234). Runes
	 * are burned by a spellcast and book to a dedicated Cast source; they also never reach here
	 * today, since a Magic XP tick lets {@code correlateProcessing} claim them first (#235).
	 */
	private static final Set<String> CONSUMABLE_CATEGORIES = ImmutableSet.of("Food", "Potions");

	/**
	 * Empty vessels left behind when a potion or drink/dish is finished — a free byproduct of the
	 * consumption, booked at 0 rather than an avg-price Unknown purchase (#218). Claimed only on a
	 * terminal-consumption tick, bounded to the number of vessels emptied.
	 */
	private static final Set<Integer> EMPTY_CONTAINERS = ImmutableSet.of(
			ItemID.VIAL_EMPTY, ItemID.JUG_EMPTY, ItemID.BOWL_EMPTY, ItemID.BUCKET_EMPTY, ItemID.BEER_GLASS);

	/** Menu option that stores held furs/meats into an open hunting pouch (#214). */
	private static final String POUCH_FILL_OPTION = "Fill";

	/** Substrings identifying a fur/meat hunting pouch as the "Fill" menu target, across sizes (#214). */
	private static final Set<String> POUCH_TARGETS = ImmutableSet.of("fur pouch", "meat pouch");

	/**
	 * Chat lines emitted when a hunting pouch is emptied to the bank — the per-pouch
	 * "Empty" deposit (SPAM) and the bank's "Empty containers" button (GAMEMESSAGE).
	 * Neither the pouch container nor a varbit changes, so these are the only signal (#214).
	 */
	private static final String POUCH_DEPOSIT_PREFIX = "You deposit some ";
	private static final String POUCH_DEPOSIT_SUFFIX = " into your bank.";
	private static final String EMPTY_CONTAINERS_MESSAGE = "You empty all of your containers into the bank.";

	/** Per-skill XP as last seen, so a StatChanged can be classified as a real XP gain. */
	private final Map<Skill, Integer> lastSkillXp = new EnumMap<>(Skill.class);

	/** The tick of the most recent processing-skill XP gain, pairing recipe inputs to outputs. */
	private int processingXpTick = -1;

	/** The tick of the most recent gathering-skill XP gain, marking a gain as a free gather (#213). */
	private int gatherXpTick = -1;

	/** The tick a reward/loot container last changed, marking a matching inventory gain as a free reward (#215). */
	private int rewardContainerTick = -1;

	/** The tick of the most recent Thieving XP gain, marking a gain as free stolen loot (#217). */
	private int thievingXpTick = -1;

	/**
	 * The tick a fur/meat hunting pouch was "Fill"ed from the inventory, so the matching
	 * container removal suspends the moved lots (keeping source + basis) rather than
	 * closing them as a loss (#214).
	 */
	private int pouchFillTick = -1;

	/**
	 * The tick a fur/meat hunting pouch was emptied to the bank, so the matching bank
	 * gain first un-suspends previously-filled lots and books only the surplus as a free
	 * {@code GATHER} (directly-caught furs) rather than an unknown-source purchase (#214).
	 */
	private int pouchDepositTick = -1;

	/** Tracked-output item → total input basis to carry onto its processing-produced lot(s) this tick (#69). */
	private final Map<Integer, Long> pendingProcessingOutput = new HashMap<>();

	/** Tracked-output dose id → combined input basis to carry onto its decant-produced lot(s) this tick (#220). */
	private final Map<Integer, Long> pendingDecantOutput = new HashMap<>();

	/** Tracked lower-dose id → basis carried onto the potion left after a dose is drunk this tick (#218). */
	private final Map<Integer, Long> pendingConsumedOutput = new HashMap<>();

	/** The tick a potion/drink was finished and how many vessels it freed, so the empty containers book at 0 (#218). */
	private int potionEmptiedTick = -1;

	private int potionEmptiedCount = 0;

	/** The tick of the local player's most recent death, gating the death-loss window (#70). */
	private int deathTick = -1;

	/**
	 * The tick this death's losses were first consumed, or -1 before any were. The death
	 * wipes the containers in one batch, so consumption is bounded to that batch (plus
	 * {@link #DEATH_LOSS_BATCH_GRACE_TICKS}) — without the bound, every unmatched removal
	 * in the 15-tick window (eating after respawning, dropping an item) was misbooked as
	 * a death loss and later closed at 0. Reset when the next death opens a new window.
	 */
	private int deathLossTick = -1;

	/** How many ticks after a death removals still count as death losses (respawn wipe + lag). */
	private static final int DEATH_LOSS_WINDOW_TICKS = 15;

	/** How many ticks past the first consumed death loss the same death may keep consuming. */
	private static final int DEATH_LOSS_BATCH_GRACE_TICKS = 1;

	/** How long a death suspension may await recovery before its units close as lost at 0. */
	private static final Duration DEATH_SUSPEND_EXPIRY = Duration.ofMinutes(65);

	/**
	 * True once the player's gravestone has been observed active, so its later
	 * disappearance is a real transition rather than a login-time reading (#70).
	 */
	private boolean graveSeen = false;

	/**
	 * The tick the observed gravestone vanished (collected or expired), pending the
	 * recovery grace check; -1 when none.
	 */
	private int graveGoneTick = -1;

	/** Ticks to wait after a gravestone vanishes for a collection's items to return before ruling the rest a loss. */
	private static final int GRAVE_RECOVERY_GRACE_TICKS = 5;

	private boolean pendingQuantitySync = false;
	private final Map<Integer, Integer> pendingItemDeltas = new HashMap<>();

	private final Map<TileItem, Tile> groundItems = new HashMap<>();

	/** This tick's ground spawns/despawns/stack changes, correlated against the inventory deltas (#65). */
	private final List<ItemSpawned> tickGroundSpawns = new ArrayList<>();
	private final List<ItemDespawned> tickGroundDespawns = new ArrayList<>();
	private final List<ItemQuantityChanged> tickGroundQuantityChanges = new ArrayList<>();

	/** Ground items this player dropped: the {@code TileItem} → how many of its units are ours. */
	private final Map<TileItem, Integer> myDrops = new HashMap<>();

	/** Per-item units queued to move into ground suspension when this tick's removals apply. */
	private final Map<Integer, Integer> pendingGroundSuspend = new HashMap<>();

	/** Per-item units queued to leave ground suspension when this tick's additions apply (re-pickups). */
	private final Map<Integer, Integer> pendingGroundUnsuspend = new HashMap<>();

	/** How long a ground suspension may go unresolved before its units close as lost. */
	private static final Duration GROUND_SUSPEND_EXPIRY = Duration.ofMinutes(10);

	private PricewatchPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> priceRefreshTask;
	private Instant lastPriceRefresh = null;

	/** The newest un-rendered panel snapshot; non-null means a rebuild drainer is already queued. */
	private final AtomicReference<Runnable> pendingRebuild = new AtomicReference<>();

	private static final int NATURE_RUNE_ID = 561;
	private static final int FIRE_RUNE_ID = 554;

	private volatile Map<Integer, WikiRealtimePriceClient.ItemMapping> itemMappings = Collections.emptyMap();

	private volatile boolean mappingsLoaded;

	/** Matches detector claims to observed quantity deltas; see {@link SourceAttributionCore}. */
	private final SourceAttributionCore sourceAttribution = new SourceAttributionCore();

	/** Derives discrete increments from the raw GE offer stream; see {@link GeOfferTracker}. */
	private final GeOfferTracker geOfferTracker = new GeOfferTracker();

	/** Per-item FIFO of GE buy fills awaiting collection: each entry is {@code {quantity, unitPrice}}. Persisted. */
	private final Map<Integer, Deque<long[]>> pendingGeBuys = new HashMap<>();

	/** Per-item rolling GE buy-limit window: {@code {firstBuyEpochSec, boughtInWindow}}. Persisted. */
	private final Map<Integer, long[]> geBuyLimits = new HashMap<>();

	/** Units of a just-placed GE sell awaiting their placement inventory decrease (session-only). */
	private final Map<Integer, Integer> pendingSellSuspend = new HashMap<>();

	/** Units of a cancelled GE sell awaiting their return inventory increase (session-only). */
	private final Map<Integer, Integer> pendingSellUnsuspend = new HashMap<>();

	/**
	 * Per-item FIFO of GE sell fills that outran their suspension: each entry is
	 * {@code {quantity, unitPrice}}. A same-tick fill realizes before the placement
	 * inventory decrease has moved {@link #pendingSellSuspend} into {@code suspendedQuantity},
	 * so the shortfall is parked here and drained once the units suspend (session-only).
	 */
	private final Map<Integer, Deque<long[]>> pendingSellRealize = new HashMap<>();

	private static final Type GE_LEDGER_TYPE = new TypeToken<Map<Integer, List<long[]>>>(){}.getType();

	private static final Type GE_LIMITS_TYPE = new TypeToken<Map<Integer, long[]>>(){}.getType();

	/** The rolling GE buy-limit window length. */
	private static final Duration BUY_LIMIT_WINDOW = Duration.ofHours(4);

	/**
	 * Ticks after login during which {@code GrandExchangeOfferChanged} events are treated as the
	 * login offer sync (pre-existing offers) rather than user actions. The client delivers the GE
	 * offers with the login packet within a tick or two, while the player cannot open the GE and
	 * abort an offer anywhere near this fast — so the window reliably separates the two.
	 */
	private static final int GE_LOGIN_SYNC_TICKS = 5;

	/** How often at most the GE ledger/window are rewritten to config during activity. */
	private static final Duration GE_STATE_SAVE_INTERVAL = Duration.ofMinutes(1);

	private Instant lastGeStateSave;

	/** Per-item thinned time series of portfolio value/cost for the history chart. */
	private final PortfolioHistory portfolioHistory = new PortfolioHistory();

	private static final Type PORTFOLIO_HISTORY_TYPE = new TypeToken<Map<Integer, List<long[]>>>(){}.getType();

	/** How often at most the portfolio history is rewritten to config. */
	private static final Duration PORTFOLIO_SAVE_INTERVAL = Duration.ofMinutes(5);

	private Instant lastPortfolioSave;

	/**
	 * Records a portfolio snapshot into the history (persisting throttled): the running
	 * value — owned units (held plus suspended) marked to the current average plus sold
	 * lots at their actual sale price — against the invested cost basis of every logged
	 * lot, which stays fixed as lots sell. Their gap is thus the realized-plus-unrealized
	 * profit. Suspended units must count: their lots are still open on the cost side, so
	 * omitting their value would carve a false loss into the chart for the duration of
	 * every in-flight sell, trade, drop, or death.
	 */
	private void recordPortfolioSnapshot()
	{
		Map<Integer, long[]> perItem = new HashMap<>();
		for (TrackedItem item : trackedItems.values())
		{
			if (!item.hasPrices())
				continue;

			long value = item.getAvgValue() + item.getSuspendedValue() + item.getRealizedProceeds();
			long cost = item.isCostBasisInitialized() ? item.getInvestedCostBasis() : 0;
			perItem.put(item.getItemId(), new long[]{value, cost});
		}

		if (perItem.isEmpty())
			return;

		portfolioHistory.record(Instant.now().getEpochSecond(), perItem);

		if (lastPortfolioSave == null
				|| Duration.between(lastPortfolioSave, Instant.now()).compareTo(PORTFOLIO_SAVE_INTERVAL) >= 0)
			persistPortfolioHistory();
	}

	/** Serializes the per-item portfolio history to per-profile config. */
	private void persistPortfolioHistory()
	{
		lastPortfolioSave = Instant.now();
		configManager.setRSProfileConfiguration(PricewatchConfig.GROUP, PricewatchConfig.KEY_PORTFOLIO_HISTORY,
				gson.toJson(portfolioHistory.seriesByItem(), PORTFOLIO_HISTORY_TYPE));
	}

	/**
	 * Restores the per-item portfolio history from per-profile config, ignoring a corrupt
	 * value. The pre-#152 aggregate format (a JSON array rather than an object) can't be
	 * split per item, so it is discarded — history simply rebuilds from the next snapshot.
	 */
	private void loadPortfolioHistory()
	{
		String saved = configManager.getRSProfileConfiguration(PricewatchConfig.GROUP,
				PricewatchConfig.KEY_PORTFOLIO_HISTORY);
		if (saved == null || !saved.trim().startsWith("{"))
			return;

		try
		{
			Map<Integer, List<long[]>> stored = gson.fromJson(saved.trim(), PORTFOLIO_HISTORY_TYPE);
			portfolioHistory.load(stored);
		}
		catch (JsonSyntaxException e)
		{
			log.warn("Failed to parse persisted portfolio history; ignoring", e);
		}
	}

	/** @return the aggregated portfolio history points ({@code {epochSeconds, value, costBasis}}) for the chart. */
	List<long[]> portfolioHistoryPoints()
	{
		return portfolioHistory.aggregate();
	}

	/** How long after first launching a new release the "What's New" indicator stays highlighted. */
	private static final Duration WHATS_NEW_WINDOW = Duration.ofDays(7);

	/** Bundled release notes, parsed once at startup; the newest entry is the current version. */
	private Changelog changelog;

	/**
	 * Detects a new plugin version by comparing the changelog's current version to the
	 * last-seen version in config. On a change, restamps the first-seen time and re-arms
	 * the "What's New" indicator so late updaters still get their week.
	 */
	private void detectVersionChange()
	{
		String current = changelog.currentVersion();
		if (current == null)
			return;

		String lastSeen = configManager.getConfiguration(
				PricewatchConfig.GROUP, PricewatchConfig.KEY_LAST_SEEN_VERSION);
		if (current.equals(lastSeen))
			return;

		configManager.setConfiguration(PricewatchConfig.GROUP, PricewatchConfig.KEY_LAST_SEEN_VERSION, current);
		configManager.setConfiguration(PricewatchConfig.GROUP, PricewatchConfig.KEY_VERSION_FIRST_SEEN,
				System.currentTimeMillis());
		configManager.setConfiguration(PricewatchConfig.GROUP, PricewatchConfig.KEY_WHATS_NEW_DISMISSED, false);
	}

	/**
	 * One-time migration for #219: the old combined {@code autoAddItems} enum
	 * (High/Low/Avg/Zero/Off) split into a boolean auto-add gate plus a separate
	 * {@link FallbackPricing}. Rewrites a legacy enum name still stored under
	 * {@link PricewatchConfig#KEY_AUTO_ADD_ITEMS} as the boolean gate — Off becomes off,
	 * every pricing value becomes on — and seeds {@link PricewatchConfig#KEY_FALLBACK_PRICING}
	 * from its pricing half (Off, which conflated the two and couldn't carry a pricing
	 * choice, defaults to Avg). Idempotent: a value already migrated to a boolean, or a
	 * fresh install with no value, is left untouched.
	 */
	private void migrateAutoAddSetting()
	{
		String legacy = configManager.getConfiguration(PricewatchConfig.GROUP, PricewatchConfig.KEY_AUTO_ADD_ITEMS);
		FallbackPricing pricing = FallbackPricing.fromLegacyMode(legacy);
		if (pricing == null)
			return;

		boolean autoAdd = !legacy.equals("OFF");
		configManager.setConfiguration(PricewatchConfig.GROUP, PricewatchConfig.KEY_AUTO_ADD_ITEMS, autoAdd);
		configManager.setConfiguration(PricewatchConfig.GROUP, PricewatchConfig.KEY_FALLBACK_PRICING, pricing);
	}

	/** @return whether the indicator should read "What's New" — within a week of first launch and not dismissed. */
	private boolean isWhatsNew()
	{
		if (changelog.currentVersion() == null)
			return false;

		Boolean dismissed = configManager.getConfiguration(PricewatchConfig.GROUP,
				PricewatchConfig.KEY_WHATS_NEW_DISMISSED, Boolean.class);
		if (Boolean.TRUE.equals(dismissed))
			return false;

		Long firstSeen = configManager.getConfiguration(PricewatchConfig.GROUP,
				PricewatchConfig.KEY_VERSION_FIRST_SEEN, Long.class);
		if (firstSeen == null)
			return true;

		return System.currentTimeMillis() - firstSeen < WHATS_NEW_WINDOW.toMillis();
	}

	/** Persists that the user has seen the current release's "What's New", quieting the indicator. */
	private void markWhatsNewSeen()
	{
		configManager.setConfiguration(PricewatchConfig.GROUP, PricewatchConfig.KEY_WHATS_NEW_DISMISSED, true);
	}

	/**
	 * Builds the side panel (wiring its callbacks back to this plugin), registers
	 * the nav button and overlays, restores persisted items, and kicks off the
	 * metadata fetch and recurring price refresh.
	 */
	@Override
	protected void startUp() throws Exception
	{
		changelog = Changelog.load();
		detectVersionChange();
		migrateAutoAddSetting();

		panel = new PricewatchPanel(
				itemManager,
				config,
				this::addTrackedItem,
				this::removeTrackedItem,
				this::onAcquisitionsEdited,
				this::requestDetailData,
				this::clearAcquisitions,
				this::onNotificationsEdited,
				this::clearAllTrackedItems,
				this::examineFor,
				this::reorderTrackedItem,
				this::setGlobalOrder,
				this::toggleCompactView,
				this::setSortMode,
				this::toggleSortReversed,
				this::setFavorite,
				this::setOnOverlay,
				this::setGroupCollapsed,
				new CategoryActions()
				{
					@Override
					public void setItemCategory(int itemId, String category)
					{
						PricewatchPlugin.this.setItemCategory(itemId, category);
					}

					@Override
					public void create(String name)
					{
						createCategory(name);
					}

					@Override
					public void rename(String oldName, String newName)
					{
						renameCategory(oldName, newName);
					}

					@Override
					public void delete(String name)
					{
						deleteCategory(name);
					}

					@Override
					public void reorder(String name, int targetIndex)
					{
						reorderCategory(name, targetIndex);
					}

					@Override
					public String autoCategorize(boolean includeCategorized)
					{
						return PricewatchPlugin.this.autoCategorize(includeCategorized);
					}
				},
				this::buildShareToken,
				this::importTrackedList,
				this::buildAcquisitionsCsv,
				this::portfolioHistoryPoints,
				changelog,
				isWhatsNew(),
				this::markWhatsNewSeen
		);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");

		navButton = NavigationButton.builder()
				.tooltip("Pricewatch")
				.icon(icon)
				.priority(6)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
		overlayManager.add(highlightOverlay);
		overlayManager.add(groundOverlay);
		for (int slot = 0; slot < OVERLAY_MAX; slot++)
		{
			PricewatchScreenOverlay overlay = new PricewatchScreenOverlay(this, config, itemManager, slot);
			screenOverlays.add(overlay);
			overlayManager.add(overlay);
		}

		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				sessionInitialized = true;
				geLoginTick = client.getTickCount();
			}

			loadCategories();
			loadPersistedItems();
			loadGeState();
			loadPortfolioHistory();

			refreshPanel();
			clientThread.invokeLater(this::hydratePriceCache);
		});
		executor.execute(this::fetchItemMappings);
		scheduleRefresh();
	}

	/**
	 * @return the examine text for the given item id from the wiki mapping, or
	 *         {@code null} when the item isn't GE-tradeable or the mapping hasn't
	 *         loaded yet
	 */
	private String examineFor(int itemId)
	{
		WikiRealtimePriceClient.ItemMapping mapping = itemMappings.get(itemId);
		return mapping == null ? null : mapping.getExamine();
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
	 * Re-evaluates GE-tradeability for every tracked item and the preview item now
	 * that the wiki mapping is available, then refreshes the panel. Items absent from
	 * the mapping are not on the Grand Exchange, so they are marked non-tradeable and
	 * any stale price-load failure is cleared.
	 */
	private void resolveTradeabilityForAll()
	{
		trackedItems.values().forEach(this::resolveTradeable);

		if (previewItem != null)
			resolveTradeable(previewItem);

		refreshPanel();
	}

	/**
	 * Narrows an item's tradeable flag using the wiki mapping: an item that the game
	 * composition reports as tradeable but which is absent from the Grand Exchange
	 * mapping (e.g. coins, burnt food) is reclassified as non-tradeable so it shows
	 * "Item not tradeable" rather than a price-load failure. No-op until the mapping
	 * has loaded, so a slow fetch never mislabels a genuinely tradeable item.
	 */
	private void resolveTradeable(TrackedItem item)
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

	/** Tears down the nav button, overlays, panel, and refresh task and clears all in-memory state. */
	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(highlightOverlay);
		overlayManager.remove(groundOverlay);
		screenOverlays.forEach(overlayManager::remove);
		screenOverlays.clear();
		panel.shutdown();
		closeAllGroundSuspensions();
		groundItems.clear();
		tickGroundSpawns.clear();
		tickGroundDespawns.clear();
		tickGroundQuantityChanges.clear();
		clientThread.invokeLater(this::hideGeButton);
		currentGeItem = -1;
		persistPriceCache();
		if (priceRefreshTask != null)
		{
			priceRefreshTask.cancel(false);
			priceRefreshTask = null;
		}

		persistGeState();
		persistPortfolioHistory();
		trackedItems.clear();
		containerCounts.clear();
		runePouchCounts.clear();
		sourceAttribution.clear();
		geOfferTracker.clear();
		pendingSellSuspend.clear();
		pendingSellUnsuspend.clear();
		pendingSellRealize.clear();
		pendingTradeSuspend.clear();
		pendingTradeUnsuspend.clear();
		lastPriceRefresh = null;
	}

	@Provides
	PricewatchConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PricewatchConfig.class);
	}

	/** (Re)schedules the recurring GE price refresh at the configured rate (min 30s), replacing any prior task. */
	private void scheduleRefresh()
	{
		if (priceRefreshTask != null)
			priceRefreshTask.cancel(false);

		int rate = Math.max(30, config.priceRefreshSeconds());
		priceRefreshTask = executor.scheduleAtFixedRate(
				this::refreshGePrices, 0, rate, TimeUnit.SECONDS
		);
	}

	private static final Type PERSIST_TYPE = new TypeToken<List<PersistedItem>>(){}.getType();

	private static final Type PRICE_CACHE_TYPE = new TypeToken<Map<Integer, CachedPrice>>(){}.getType();

	/** How often at most the price cache is rewritten to config during regular refreshes. */
	private static final Duration PRICE_CACHE_SAVE_INTERVAL = Duration.ofMinutes(5);

	/** When the price cache was last written, to throttle per-refresh saves. */
	private Instant lastPriceCacheSave;

	/**
	 * Last-known prices for one tracked item, stored as JSON in the RS profile config
	 * so the panel can show (staleness-dimmed) values immediately at startup instead
	 * of placeholders until the first wiki fetch lands. Package-private so
	 * {@code PersistedSchemaSnapshotTest} can guard its shape; any field change fails
	 * the schema snapshot until it is regenerated and explained in the PR.
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
	 * Serializable snapshot of a tracked item, stored as JSON in the RS profile config.
	 * Package-private so {@code PersistenceCompatTest} can freeze its legacy shape and
	 * {@code PersistedSchemaSnapshotTest} can guard it; any field change fails the
	 * schema snapshot until it is regenerated and explained in the PR.
	 */
	static class PersistedItem
	{
		int itemId;
		int quantity;
		boolean costBasisInitialized;
		List<AcquisitionRecord> acquisitions;
		List<NotificationRule> notifications;
		boolean notificationsInitialized;
		boolean favorite;
		String category;
		boolean onOverlay;
		int deathSuspendedQuantity;
		Long deathSuspendedAt;
		int pouchSuspendedQuantity;
	}

	private static final Type CATEGORIES_TYPE = new TypeToken<CategoryData>(){}.getType();

	/**
	 * Serializable snapshot of the category definitions and special-group collapsed state.
	 * Package-private so {@code PersistenceCompatTest} can freeze its legacy shape and
	 * {@code PersistedSchemaSnapshotTest} can guard it; any field change fails the
	 * schema snapshot until it is regenerated and explained in the PR.
	 */
	static class CategoryData
	{
		List<CategoryState> categories;
		boolean favoritesCollapsed;
		boolean uncategorizedCollapsed;
	}

	/** Restores tracked items from the per-profile JSON written by {@link #persistTrackedItems()}. */
	private void loadPersistedItems()
	{
		String saved = configManager.getRSProfileConfiguration(
				PricewatchConfig.GROUP, PricewatchConfig.KEY_TRACKED_ITEMS);
		if (saved == null || saved.trim().isEmpty())
			return;

		String trimmed = saved.trim();
		if (trimmed.startsWith("["))
		{
			try
			{
				List<PersistedItem> list = gson.fromJson(trimmed, PERSIST_TYPE);
				if (list != null)
				{
					for (PersistedItem p : list)
					{
						addTrackedItem(p.itemId, p.quantity, p.acquisitions, p.notifications,
							p.notificationsInitialized, p.costBasisInitialized, false, false, TrackItemMode.TRACK);
						applyPersistedGrouping(p.itemId, p.favorite, p.category, p.onOverlay);
						applyPersistedDeathSuspension(p.itemId, p.deathSuspendedQuantity, p.deathSuspendedAt);
						applyPersistedPouchSuspension(p.itemId, p.pouchSuspendedQuantity);
					}
				}

				return;
			}
			catch (JsonSyntaxException e)
			{
				log.warn("Failed to parse persisted item JSON; ignoring", e);
				return;
			}
		}
	}

	/**
	 * Applies a persisted item's favorite/category/overlay grouping after it has been added.
	 * Enqueued on the client thread so it runs after the matching {@link #addTrackedItem}
	 * body (which is itself client-thread-deferred), guaranteeing the item exists.
	 */
	private void applyPersistedGrouping(int itemId, boolean favorite, String category, boolean onOverlay)
	{
		clientThread.invokeLater(() ->
		{
			TrackedItem tracked = trackedItems.get(itemId);
			if (tracked == null)
				return;

			tracked.setFavorite(favorite);
			tracked.setCategory(category);
			tracked.setOnOverlay(onOverlay);
		});
	}

	/**
	 * Restores a persisted death suspension after its item has been added, so a
	 * recovery spanning a relog still un-suspends instead of opening new lots.
	 * Client-thread-deferred like {@link #applyPersistedGrouping}.
	 */
	private void applyPersistedDeathSuspension(int itemId, int quantity, Long suspendedAtEpoch)
	{
		if (quantity <= 0)
			return;

		clientThread.invokeLater(() ->
		{
			TrackedItem tracked = trackedItems.get(itemId);
			if (tracked == null)
				return;

			tracked.setDeathSuspendedQuantity(quantity);
			tracked.setDeathSuspendedAt(suspendedAtEpoch == null
					? Instant.now()
					: Instant.ofEpochSecond(suspendedAtEpoch));
		});
	}

	/**
	 * Restores a persisted fur/meat-pouch suspension after its item has been added, so
	 * furs "Fill"ed in before a logout still un-suspend (keeping their original source
	 * and basis) when the pouch is emptied in a later session (#214).
	 * Client-thread-deferred like {@link #applyPersistedGrouping}.
	 */
	private void applyPersistedPouchSuspension(int itemId, int quantity)
	{
		if (quantity <= 0)
			return;

		clientThread.invokeLater(() ->
		{
			TrackedItem tracked = trackedItems.get(itemId);
			if (tracked == null)
				return;

			tracked.setPouchSuspendedQuantity(quantity);
		});
	}

	/** Restores the category definitions and group collapsed state from per-profile JSON. */
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

	/** Serializes the current tracked items (quantity, cost basis, notifications, grouping) to per-profile config. */
	void persistTrackedItems()
	{
		List<PersistedItem> list = new ArrayList<>();
		for (TrackedItem item : trackedItems.values())
		{
			PersistedItem p = new PersistedItem();
			p.itemId = item.getItemId();
			p.quantity = item.getQuantity();
			p.costBasisInitialized = item.isCostBasisInitialized();
			p.acquisitions = item.getAcquisitions();
			p.notifications = item.getNotifications();
			p.notificationsInitialized = item.isNotificationsInitialized();
			p.favorite = item.isFavorite();
			p.category = item.getCategory();
			p.onOverlay = item.isOnOverlay();
			p.deathSuspendedQuantity = item.getDeathSuspendedQuantity();
			p.deathSuspendedAt = item.getDeathSuspendedAt() == null
					? null
					: item.getDeathSuspendedAt().getEpochSecond();
			p.pouchSuspendedQuantity = item.getPouchSuspendedQuantity();
			list.add(p);
		}

		configManager.setRSProfileConfiguration(
				PricewatchConfig.GROUP, PricewatchConfig.KEY_TRACKED_ITEMS, gson.toJson(list, PERSIST_TYPE));
	}

	/** Tracks an item by id with defaults (full tracking mode, no preset cost basis). */
	private void addTrackedItem(int itemId)
	{
		addTrackedItem(itemId, TrackItemMode.TRACK);
	}

	/** Tracks an item by id in the given mode, routing {@link TrackItemMode#VIEW} to a read-only preview instead. */
	private void addTrackedItem(int itemId, TrackItemMode mode)
	{
		if (mode == TrackItemMode.VIEW)
		{
			previewItem(itemId);
			return;
		}

		addTrackedItem(itemId, 0, null, null, false, false, true, true, mode);
	}

	/**
	 * Opens a read-only detail preview for an untracked item without adding it to
	 * the tracked list or persisting anything. Builds a transient {@link TrackedItem},
	 * shows it in the detail view, then fetches its prices and history in the
	 * background. Runs on the client thread.
	 */
	private void previewItem(int itemId)
	{
		clientThread.invokeLater(() ->
		{
			TrackedItem preview = previewItem;
			if (preview == null || preview.getItemId() != itemId)
			{
				var composition = itemManager.getItemComposition(itemId);
				preview = new TrackedItem(itemId, composition.getName());
				preview.setTradeable(composition.isTradeable());
				preview.setMode(TrackItemMode.VIEW);
				applyItemMetadata(preview);
				previewItem = preview;
			}

			final TrackedItem shown = preview;
			SwingUtilities.invokeLater(() -> panel.showPreview(shown));
			requestDetailData(itemId);
			refreshGePrices();
		});
	}

	/**
	 * @return the tracked item for {@code itemId}, or the transient preview item when it
	 *         matches; otherwise {@code null}
	 */
	private TrackedItem lookupItem(int itemId)
	{
		TrackedItem tracked = trackedItems.get(itemId);
		if (tracked == null && previewItem != null && previewItem.getItemId() == itemId)
			return previewItem;

		return tracked;
	}

	/** Tracks an item with a preset quantity and acquisition history (e.g. a restore), using default notifications. */
	private void addTrackedItem(int itemId, int initialQuantity, List<AcquisitionRecord> records,
			boolean costBasisInitialized)
	{
		addTrackedItem(itemId, initialQuantity, records, null, false, costBasisInitialized, true, true,
				TrackItemMode.TRACK);
	}

	/**
	 * Canonical add: creates a {@link TrackedItem} (resolving its name/tradeable
	 * flag from the item composition), seeds its quantity, acquisitions, and
	 * notifications, registers it, and persists/refreshes. No-op if already
	 * tracked. Runs on the client thread.
	 *
	 * @param initialQuantity        starting count
	 * @param records                preset acquisition lots, or {@code null}
	 * @param notifications          preset notification rules, or {@code null}
	 * @param notificationsInitialized whether default rules have already been seeded
	 * @param costBasisInitialized   whether a cost basis has already been established
	 * @param syncOnAdd              recount from containers immediately when in TRACK mode
	 * @param persistOnAdd           persist the tracked list after adding; the persisted-load
	 *                               replay passes {@code false}, both because the data came from
	 *                               config unchanged and because persisting mid-replay would write
	 *                               the item before its deferred grouping/death-suspension
	 *                               callbacks have applied, stripping those fields
	 * @param mode                   tracking vs. view-only
	 */
	private void addTrackedItem(int itemId, int initialQuantity, List<AcquisitionRecord> records,
			List<NotificationRule> notifications, boolean notificationsInitialized,
			boolean costBasisInitialized, boolean syncOnAdd, boolean persistOnAdd, TrackItemMode mode)
	{
		clientThread.invokeLater(() ->
		{
			if (trackedItems.containsKey(itemId))
				return;

			var composition = itemManager.getItemComposition(itemId);
			TrackedItem tracked = new TrackedItem(itemId, composition.getName());
			tracked.setTradeable(composition.isTradeable());
			resolveTradeable(tracked);
			tracked.setQuantity(initialQuantity);
			tracked.setMode(mode == null ? TrackItemMode.TRACK : mode);
			if (records != null)
				tracked.setAcquisitions(new ArrayList<>(records));

			if (notifications != null)
				tracked.setNotifications(new ArrayList<>(notifications));

			tracked.setNotificationsInitialized(notificationsInitialized);
			tracked.setCostBasisInitialized(costBasisInitialized);
			trackedItems.put(itemId, tracked);

			if (syncOnAdd && tracked.getMode() == TrackItemMode.TRACK)
				syncQuantitiesForItem(tracked);

			if (persistOnAdd)
				persistTrackedItems();

			refreshPanel();
			refreshGePrices();
		});
	}

	/**
	 * Stops tracking an item, then persists and refreshes. Also drops the item from the
	 * session baseline — before the panel's next rebuild computes the session delta — so
	 * untracking doesn't read as the item's whole value lost. Runs on the client thread.
	 */
	private void removeTrackedItem(int itemId)
	{
		clientThread.invokeLater(() ->
		{
			trackedItems.remove(itemId);
			portfolioHistory.removeItem(itemId);
			SwingUtilities.invokeLater(() -> panel.removeSessionBaseline(itemId));
			persistTrackedItems();
			persistPortfolioHistory();
			refreshPanel();
		});
	}

	/** Wipes the portfolio value history (in memory and in config), e.g. when the whole tracked list is cleared. */
	private void clearPortfolioHistory()
	{
		portfolioHistory.clear();
		persistPortfolioHistory();
	}

	/**
	 * Builds a shareable code for the current tracked list (ids, modes, categories,
	 * favorites) — "" when empty — and hands it to {@code onResult} on the EDT.
	 * {@code trackedItems} is client-thread state, so the snapshot is taken there
	 * rather than on the EDT the panel's button handler runs on, where a concurrent
	 * mutation (login replay, auto-add, GE fill) could tear the iteration.
	 */
	void buildShareToken(Consumer<String> onResult)
	{
		clientThread.invokeLater(() ->
		{
			List<PortfolioShareCodec.Entry> entries = trackedItems.values().stream()
					.map(t -> new PortfolioShareCodec.Entry(t.getItemId(), t.getMode(), t.getCategory(),
							t.isFavorite()))
					.collect(Collectors.toList());
			String token = entries.isEmpty()
					? ""
					: new PortfolioShareCodec(gson)
							.encode(new PortfolioShareCodec.Snapshot(1, entries, new ArrayList<>(categories)));
			SwingUtilities.invokeLater(() -> onResult.accept(token));
		});
	}

	/**
	 * Merges a shared tracked-list code into the current profile: adds items that
	 * aren't already tracked (with their mode, category, and favorite flag) plus any
	 * missing categories they reference. Non-destructive — existing items are left
	 * untouched. Decode, count, and merge all run on the client thread (the counts
	 * read {@code trackedItems}); the outcome summary is handed to {@code onResult}
	 * on the EDT.
	 */
	void importTrackedList(String token, Consumer<String> onResult)
	{
		clientThread.invokeLater(() ->
		{
			String message = applyImportedList(token);
			SwingUtilities.invokeLater(() -> onResult.accept(message));
		});
	}

	/** Decodes and merges a share code on the client thread. @return the user-facing outcome summary */
	private String applyImportedList(String token)
	{
		PortfolioShareCodec.Snapshot snapshot = new PortfolioShareCodec(gson).decode(token);
		if (snapshot == null || snapshot.getItems() == null)
			return "Couldn't read that code — make sure you pasted all of it.";

		List<PortfolioShareCodec.Entry> incoming = new ArrayList<>(snapshot.getItems());
		List<CategoryState> incomingCategories = snapshot.getCategories() != null
				? new ArrayList<>(snapshot.getCategories())
				: new ArrayList<>();

		long fresh = incoming.stream().filter(e -> !trackedItems.containsKey(e.getId())).count();
		long skipped = incoming.size() - fresh;

		mergeImportedList(incoming, incomingCategories);

		if (fresh == 0)
			return "Nothing new — all " + skipped + " item(s) are already tracked.";

		return "Imported " + fresh + " item(s)" + (skipped > 0 ? ", skipped " + skipped + " already tracked." : ".");
	}

	/** Applies a decoded tracked-list import on the client thread: categories first, then new items. */
	private void mergeImportedList(List<PortfolioShareCodec.Entry> entries, List<CategoryState> importedCategories)
	{
		importedCategories.stream()
				.filter(c -> c != null && c.getName() != null && !c.getName().trim().isEmpty())
				.forEach(c -> ensureCategory(c.getName().trim(), c.isCollapsed()));

		boolean changed = false;
		for (PortfolioShareCodec.Entry entry : entries)
		{
			if (trackedItems.containsKey(entry.getId()))
				continue;

			var composition = itemManager.getItemComposition(entry.getId());
			if (composition == null)
				continue;

			TrackedItem tracked = new TrackedItem(entry.getId(), composition.getName());
			tracked.setTradeable(composition.isTradeable());
			resolveTradeable(tracked);
			tracked.setMode(entry.getMode() == null ? TrackItemMode.TRACK : entry.getMode());
			tracked.setFavorite(entry.isFavorite());
			if (entry.getCategory() != null && !entry.getCategory().trim().isEmpty())
			{
				String category = entry.getCategory().trim();
				ensureCategory(category, false);
				tracked.setCategory(category);
			}

			trackedItems.put(entry.getId(), tracked);
			if (tracked.getMode() == TrackItemMode.TRACK)
				syncQuantitiesForItem(tracked);

			changed = true;
		}

		if (changed)
		{
			persistCategories();
			persistTrackedItems();
			refreshPanel();
			refreshGePrices();
		}
	}

	/** Adds a category by name if one with that name doesn't already exist (case-insensitive). */
	private void ensureCategory(String name, boolean collapsed)
	{
		boolean exists = categories.stream()
				.anyMatch(c -> c.getName() != null && c.getName().equalsIgnoreCase(name));
		if (!exists)
			categories.add(new CategoryState(name, collapsed));
	}

	/**
	 * Builds the acquisitions log of all tracked items as CSV (see
	 * {@link AcquisitionCsvExporter}) on the client thread — the items and their
	 * acquisition lists are client-thread state — and hands it to {@code onResult}
	 * on the EDT.
	 */
	void buildAcquisitionsCsv(Consumer<String> onResult)
	{
		clientThread.invokeLater(() ->
		{
			String csv = AcquisitionCsvExporter.toCsv(new ArrayList<>(trackedItems.values()));
			SwingUtilities.invokeLater(() -> onResult.accept(csv));
		});
	}

	/**
	 * Moves a tracked item to a new position in the list, persisting the new order so it
	 * survives restarts. {@code targetIndex} is clamped to the list bounds; a no-op if the
	 * item is unknown or already at that position. Runs on the client thread.
	 */
	private void reorderTrackedItem(int itemId, int targetIndex)
	{
		clientThread.invokeLater(() ->
		{
			List<TrackedItem> ordered = new ArrayList<>(trackedItems.values());

			int from = -1;
			for (int i = 0; i < ordered.size(); i++)
			{
				if (ordered.get(i).getItemId() == itemId)
				{
					from = i;
					break;
				}
			}

			if (from < 0)
				return;

			int to = Math.max(0, Math.min(targetIndex, ordered.size() - 1));
			if (to == from)
				return;

			ordered.add(to, ordered.remove(from));

			trackedItems.clear();
			ordered.forEach(item -> trackedItems.put(item.getItemId(), item));

			persistTrackedItems();
			refreshPanel();
		});
	}

	/** Persists the chosen sort mode; the resulting {@link ConfigChanged} rebuilds the panel. */
	private void setSortMode(SortMode mode)
	{
		configManager.setConfiguration(PricewatchConfig.GROUP, PricewatchConfig.KEY_SORT_MODE, mode);
	}

	/** Flips the persisted sort direction; the resulting {@link ConfigChanged} rebuilds the panel. */
	private void toggleSortReversed()
	{
		configManager.setConfiguration(PricewatchConfig.GROUP, PricewatchConfig.KEY_SORT_REVERSED,
				!config.sortReversed());
	}

	/** Flips the persisted compact-view flag; the resulting {@link ConfigChanged} rebuilds the panel. */
	private void toggleCompactView()
	{
		configManager.setConfiguration(PricewatchConfig.GROUP, PricewatchConfig.KEY_COMPACT_VIEW,
				!config.compactView());
	}

	/** Sets an item's favorite flag (pinning it to the top "Favorites" group), then persists and refreshes. */
	private void setFavorite(int itemId, boolean favorite)
	{
		clientThread.invokeLater(() ->
		{
			TrackedItem tracked = trackedItems.get(itemId);
			if (tracked == null)
				return;

			tracked.setFavorite(favorite);
			persistTrackedItems();
			refreshPanel();
		});
	}

	/**
	 * Adds/removes an item from the on-screen overlay set, enforcing the {@link #OVERLAY_MAX}
	 * cap (an add beyond the cap is ignored), then persists and refreshes.
	 */
	private void setOnOverlay(int itemId, boolean on)
	{
		clientThread.invokeLater(() ->
		{
			TrackedItem tracked = trackedItems.get(itemId);
			if (tracked == null || tracked.isOnOverlay() == on)
				return;

			if (on && overlayItemCount() >= OVERLAY_MAX)
				return;

			tracked.setOnOverlay(on);
			persistTrackedItems();
			refreshPanel();
		});
	}

	/** @return how many tracked items are currently flagged for the on-screen overlay. */
	private int overlayItemCount()
	{
		return (int) trackedItems.values()
				.stream()
				.filter(TrackedItem::isOnOverlay)
				.count();
	}

	/** @return the tracked items shown on the overlay (in tracked order), capped at {@link #OVERLAY_MAX}. */
	List<TrackedItem> getOverlayItems()
	{
		return trackedItems.values().stream()
				.filter(TrackedItem::isOnOverlay)
				.limit(OVERLAY_MAX)
				.collect(Collectors.toList());
	}

	/** Sets a list group's collapsed state (a category name, or a special-group key), then persists and refreshes. */
	private void setGroupCollapsed(String groupKey, boolean collapsed)
	{
		clientThread.invokeLater(() ->
		{
			if (CategoryState.FAVORITES_KEY.equals(groupKey))
				favoritesCollapsed = collapsed;
			else if (CategoryState.UNCATEGORIZED_KEY.equals(groupKey))
				uncategorizedCollapsed = collapsed;
			else
				categories.stream()
						.filter(c -> c.getName().equals(groupKey))
						.findFirst()
						.ifPresent(c -> c.setCollapsed(collapsed));

			persistCategories();
			refreshPanel();
		});
	}

	/** Assigns an item to a category (null/blank clears it to Uncategorized), then persists and refreshes. */
	private void setItemCategory(int itemId, String category)
	{
		clientThread.invokeLater(() ->
		{
			TrackedItem tracked = trackedItems.get(itemId);
			if (tracked == null)
				return;

			tracked.setCategory(category == null || category.trim().isEmpty() ? null : category.trim());
			persistTrackedItems();
			refreshPanel();
		});
	}

	/**
	 * Auto-assigns tracked items to wiki-derived categories (see {@link ItemCategoryClassifier}),
	 * creating any missing categories. Non-destructive unless {@code includeCategorized} is set:
	 * by default only uncategorized items are touched, so manual assignments are preserved. The
	 * mutation runs on the client thread; the returned message reports the outcome.
	 *
	 * @return a user-facing summary of how many items were categorized
	 */
	String autoCategorize(boolean includeCategorized)
	{
		long willChange = trackedItems.values().stream()
				.filter(t -> inAutoCategorizeScope(t, includeCategorized))
				.filter(t -> !ItemCategoryClassifier.classify(t.getName()).equals(t.getCategory()))
				.count();

		clientThread.invokeLater(() -> applyAutoCategorize(includeCategorized));

		if (willChange == 0)
			return "Nothing to categorize — everything already matches.";

		return "Auto-categorized " + willChange + " item(s).";
	}

	/** @return whether the item is in scope: always when re-categorizing, otherwise only when uncategorized. */
	private boolean inAutoCategorizeScope(TrackedItem item, boolean includeCategorized)
	{
		return includeCategorized || item.getCategory() == null || item.getCategory().trim().isEmpty();
	}

	/** Applies auto-categorization on the client thread: classify each in-scope item, create categories, assign. */
	private void applyAutoCategorize(boolean includeCategorized)
	{
		boolean changed = false;
		List<CategoryState> created = new ArrayList<>();
		for (TrackedItem tracked : trackedItems.values())
		{
			if (!inAutoCategorizeScope(tracked, includeCategorized))
				continue;

			String target = ItemCategoryClassifier.classify(tracked.getName());
			if (target.equals(tracked.getCategory()))
				continue;

			if (categories.stream().noneMatch(c -> c.getName().equalsIgnoreCase(target)))
			{
				CategoryState category = new CategoryState(target, false);
				categories.add(category);
				created.add(category);
			}

			tracked.setCategory(target);
			changed = true;
		}

		if (changed)
		{
			orderGeneratedCategories(created);
			persistCategories();
			persistTrackedItems();
			refreshPanel();
		}
	}

	/**
	 * Orders an auto-categorize run's generated categories alphabetically after any
	 * pre-existing (manually ordered) ones, then keeps "Other" at the very end.
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

	/** Creates a new category (ignoring blanks and case-insensitive duplicates), then persists and refreshes. */
	private void createCategory(String name)
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

	/** Renames a category and re-points its items, ignoring blanks and clashes, then persists and refreshes. */
	private void renameCategory(String oldName, String newName)
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
			trackedItems.values().stream()
					.filter(t -> oldName.equals(t.getCategory()))
					.forEach(t -> t.setCategory(trimmed));

			persistCategories();
			persistTrackedItems();
			refreshPanel();
		});
	}

	/** Deletes a category, moving its items to Uncategorized, then persists and refreshes. */
	private void deleteCategory(String name)
	{
		clientThread.invokeLater(() ->
		{
			if (!categories.removeIf(c -> c.getName().equals(name)))
				return;

			trackedItems.values().stream()
					.filter(t -> name.equals(t.getCategory()))
					.forEach(t -> t.setCategory(null));

			persistCategories();
			persistTrackedItems();
			refreshPanel();
		});
	}

	/** Moves a category to a new position in the ordered list, then persists and refreshes. */
	private void reorderCategory(String name, int targetIndex)
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
	 * Reorders the tracked items to match the given id order (drag reorder), then persists and
	 * refreshes. Applies the new order only when it is a faithful permutation of the current
	 * set, so a stale or partial drag result cannot drop items.
	 */
	private void setGlobalOrder(List<Integer> orderedIds)
	{
		clientThread.invokeLater(() ->
		{
			Map<Integer, TrackedItem> reordered = new LinkedHashMap<>();
			for (Integer id : orderedIds)
			{
				TrackedItem tracked = trackedItems.get(id);
				if (tracked != null)
					reordered.put(id, tracked);
			}

			if (reordered.size() != trackedItems.size())
				return;

			trackedItems.clear();
			trackedItems.putAll(reordered);
			persistTrackedItems();
			refreshPanel();
		});
	}

	/** Removes every tracked item, then persists and refreshes. Runs on the client thread. */
	private void clearAllTrackedItems()
	{
		clientThread.invokeLater(() ->
		{
			trackedItems.clear();
			clearPortfolioHistory();
			SwingUtilities.invokeLater(panel::clearSessionBaseline);
			persistTrackedItems();
			refreshPanel();
		});
	}

	/** Fetches just the 5m series for an item in the background and recomputes its window stats. */
	private void requestSeries(int itemId, boolean refreshAfter)
	{
		executor.execute(() ->
		{
			List<WikiRealtimePriceClient.PricePoint> points = wikiPriceClient.fetchTimeseries(itemId, "5m");
			clientThread.invokeLater(() ->
			{
				TrackedItem tracked = trackedItems.get(itemId);
				if (tracked == null)
					return;

				tracked.setSeries5m(points);
				recomputeWindowStats(tracked);
				if (refreshAfter)
					refreshPanel();
			});
		});
	}

	/**
	 * Fetches all four history series (5m/1h/6h/24h) plus metadata for the
	 * detail view in the background, then updates stats, alch rune prices, and the
	 * detail panel on the appropriate threads.
	 */
	private void requestDetailData(int itemId)
	{
		executor.execute(() ->
		{
			List<WikiRealtimePriceClient.PricePoint> s5 = wikiPriceClient.fetchTimeseries(itemId, "5m");
			List<WikiRealtimePriceClient.PricePoint> s1h = wikiPriceClient.fetchTimeseries(itemId, "1h");
			List<WikiRealtimePriceClient.PricePoint> s6 = wikiPriceClient.fetchTimeseries(itemId, "6h");
			List<WikiRealtimePriceClient.PricePoint> s24 = wikiPriceClient.fetchTimeseries(itemId, "24h");
			clientThread.invokeLater(() ->
			{
				TrackedItem tracked = lookupItem(itemId);
				if (tracked == null)
					return;

				tracked.setSeries5m(s5);
				tracked.setSeries1h(s1h);
				tracked.setSeries6h(s6);
				tracked.setSeries24h(s24);
				applyItemMetadata(tracked);
				recomputeWindowStats(tracked);

				final long nature = runePrice(NATURE_RUNE_ID);
				final long fire = runePrice(FIRE_RUNE_ID);
				SwingUtilities.invokeLater(() ->
				{
					panel.setAlchRunePrices(nature, fire);
					panel.refreshDetailData(itemId);
				});
				refreshPanel();
			});
		});
	}

	/** Rebuilds an item's per-window {@link PriceStats} from its current prices (LIVE) and history series. */
	private void recomputeWindowStats(TrackedItem tracked)
	{
		Map<TimeWindow, PriceStats> stats = new EnumMap<>(TimeWindow.class);
		for (TimeWindow w : TimeWindow.values())
		{
			if (w == TimeWindow.NONE)
				continue;

			if (w == TimeWindow.LIVE)
			{
				stats.put(w, new PriceStats(tracked.getHighPrice(), tracked.getLowPrice(), tracked.getAvgPrice(), 0));
			}
			else
			{
				List<WikiRealtimePriceClient.PricePoint> series = tracked.getSeriesFor(w);
				if (series.isEmpty())
					series = tracked.getSeries5m();

				stats.put(w, WikiRealtimePriceClient.computeStats(series, w));
			}
		}

		tracked.setWindowStats(stats);
	}

	/** Copies cached GE metadata (buy limit, value, alch values) onto an item, if available. */
	private void applyItemMetadata(TrackedItem tracked)
	{
		resolveTradeable(tracked);

		WikiRealtimePriceClient.ItemMapping mapping = itemMappings.get(tracked.getItemId());
		if (mapping == null)
			return;

		tracked.setBuyLimit(mapping.getLimit());
		tracked.setGeValue(mapping.getValue());
		tracked.setHighAlch(mapping.getHighAlch());
		tracked.setLowAlch(mapping.getLowAlch());
		tracked.setMetadataLoaded(true);
	}

	/** @return a price for a rune (for alch calc): the tracked average if present, else the GE price. */
	private long runePrice(int itemId)
	{
		TrackedItem tracked = trackedItems.get(itemId);
		if (tracked != null && tracked.getAvgPrice() > 0)
			return tracked.getAvgPrice();

		return Math.max(0, itemManager.getItemPrice(itemId));
	}

	/** Clears an item's acquisition lots (resetting its cost basis) and persists/refreshes. */
	private void clearAcquisitions(int itemId)
	{
		clientThread.invokeLater(() ->
		{
			TrackedItem tracked = trackedItems.get(itemId);
			if (tracked == null)
				return;

			tracked.getAcquisitions().clear();
			persistTrackedItems();
			refreshPanel();
		});
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
	 * Applies freshly fetched prices to every tracked item: records per-side
	 * deltas against the previous values, updates the LIVE window stats, seeds a
	 * cost basis on first successful price if one wasn't set, then re-evaluates
	 * notifications and refreshes the panel (including the open detail view).
	 * A failed (empty) fetch only triggers a plain refresh.
	 */
	private void applyGePrices(Map<Integer, WikiRealtimePriceClient.ItemPrices> all)
	{
		boolean fetchFailed = all.isEmpty();

		for (TrackedItem item : trackedItems.values())
		{
			WikiRealtimePriceClient.ItemPrices prices = all.get(item.getItemId());
			if (prices != null)
			{
				applyLivePrices(item, prices);

				if (!item.isCostBasisInitialized())
				{
					if (item.getQuantity() > 0 && item.getAcquisitions().isEmpty())
						addOpenAcquisition(item, item.getQuantity(), fallbackPrice(item),
								AcquisitionSource.UNKNOWN);

					item.setCostBasisInitialized(true);
					persistTrackedItems();
				}
			}
			else if (!item.hasPrices() && item.isTradeable() && mappingsLoaded)
				item.setPriceLoadFailed(true);
		}

		if (previewItem != null)
		{
			WikiRealtimePriceClient.ItemPrices prices = all.get(previewItem.getItemId());
			if (prices != null)
				applyLivePrices(previewItem, prices);
			else if (!previewItem.hasPrices() && previewItem.isTradeable() && mappingsLoaded)
				previewItem.setPriceLoadFailed(true);
		}

		if (fetchFailed)
		{
			refreshPanel();
			return;
		}

		lastPriceRefresh = Instant.now();
		if (lastPriceCacheSave == null
				|| Duration.between(lastPriceCacheSave, Instant.now()).compareTo(PRICE_CACHE_SAVE_INTERVAL) >= 0)
			persistPriceCache();

		recordPortfolioSnapshot();

		evaluateNotifications();
		refreshPanel(true);

		final int detailId = panel.getDetailItemId();
		for (TrackedItem item : trackedItems.values())
		{
			if (item.isTradeable() && item.hasPrices())
			{
				if (item.getItemId() == detailId)
					requestDetailData(item.getItemId());
				else
					requestSeries(item.getItemId(), false);
			}
		}

		if (previewItem != null && previewItem.getItemId() == detailId
				&& previewItem.isTradeable() && previewItem.hasPrices())
			requestDetailData(previewItem.getItemId());
	}

	/**
	 * Writes every priced tracked item's current prices to the RS profile config.
	 * Called throttled from refreshes and unconditionally at shutdown.
	 */
	private void persistPriceCache()
	{
		Map<Integer, CachedPrice> cache = new HashMap<>();
		for (TrackedItem item : trackedItems.values())
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
	 * Hydrates tracked items from the persisted price cache so the panel shows
	 * last-known values (dimmed by the existing staleness treatment once their trade
	 * times age past the threshold) instead of placeholders. Live fetches simply
	 * overwrite these; items that already have prices are never touched. Runs on the
	 * client thread after the persisted items have been restored — enqueued from both
	 * initialization paths, since at startUp on the login screen the RS-profile config
	 * isn't available yet and only the {@code LOGGED_IN} load can hydrate.
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

		boolean hydrated = false;
		for (Map.Entry<Integer, CachedPrice> entry : cache.entrySet())
		{
			TrackedItem item = trackedItems.get(entry.getKey());
			if (item == null || item.hasPrices())
				continue;

			CachedPrice p = entry.getValue();
			item.setHighPrice(p.high);
			item.setLowPrice(p.low);
			item.setAvgPrice(p.avg);
			item.setLatestHighTime(p.highTime);
			item.setLatestLowTime(p.lowTime);
			item.setPriceCacheHydrated(true);
			hydrated = true;
		}

		if (hydrated)
			refreshPanel();
	}

	/**
	 * Applies a freshly fetched price set to an item: records per-side deltas, updates
	 * current prices, and refreshes its LIVE window stats.
	 */
	private void applyLivePrices(TrackedItem item, WikiRealtimePriceClient.ItemPrices prices)
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

	/**
	 * Reacts to this plugin's config changes: resolves detail-section slot
	 * conflicts, reschedules the refresh when the interval changes, and otherwise
	 * just repaints the panel. Ignores other plugins' groups.
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!PricewatchConfig.GROUP.equals(event.getGroup()))
			return;

		if (SECTION_SLOT_KEYS.contains(event.getKey()))
		{
			swapConflictingSection(event);
			refreshPanel();
			return;
		}

		switch (event.getKey())
		{
			case PricewatchConfig.KEY_TRACKED_ITEMS:
				return;
			case PricewatchConfig.KEY_PRICE_REFRESH_SECONDS:
				scheduleRefresh();
				return;
			case PricewatchConfig.KEY_SCREEN_OVERLAY_ON_TOP:
				rebucketScreenOverlays();
				return;
			default:
				refreshPanel();
		}
	}

	/** Removes and re-adds the screen overlays so the manager re-buckets them into their (config-driven) layer. */
	private void rebucketScreenOverlays()
	{
		screenOverlays.forEach(overlayManager::remove);
		screenOverlays.forEach(overlayManager::add);
	}

	private static final Set<String> SECTION_SLOT_KEYS = Set.of(
			PricewatchConfig.KEY_SHOW_ITEM_VALUES,
			PricewatchConfig.KEY_SHOW_COLLECTION_VALUES,
			PricewatchConfig.KEY_SHOW_MARKET_INFO,
			PricewatchConfig.KEY_SHOW_PRICE_OVERVIEW,
			PricewatchConfig.KEY_SHOW_PRICE_GRAPH,
			PricewatchConfig.KEY_SHOW_VOLUME_GRAPH,
			PricewatchConfig.KEY_SHOW_ALCH_INFO,
			PricewatchConfig.KEY_SHOW_NOTIFICATIONS,
			PricewatchConfig.KEY_SHOW_ITEM_LOG,
			PricewatchConfig.KEY_SHOW_LINKS);

	/**
	 * Keeps detail-section slots unique: when a section is moved to a slot already
	 * occupied by another, the other section is swapped into the vacated slot.
	 */
	private void swapConflictingSection(ConfigChanged event)
	{
		SectionSlot newSlot;
		SectionSlot oldSlot;
		try
		{
			newSlot = SectionSlot.valueOf(event.getNewValue());
			oldSlot = SectionSlot.valueOf(event.getOldValue());
		}
		catch (IllegalArgumentException | NullPointerException e)
		{
			return;
		}

		if (newSlot == SectionSlot.NONE || newSlot == oldSlot)
			return;

		for (String key : SECTION_SLOT_KEYS)
		{
			if (key.equals(event.getKey()))
				continue;

			SectionSlot other = configManager.getConfiguration(
					PricewatchConfig.GROUP, key, SectionSlot.class);
			if (other == newSlot)
			{
				configManager.setConfiguration(PricewatchConfig.GROUP, key, oldSlot);
				return;
			}
		}
	}

	/**
	 * Claims an upcoming High/Low Alchemy disposal (#68): casting either spell on a
	 * tracked item registers an {@link AcquisitionSource#ALCHEMY} claim for one unit
	 * at the coins the cast actually yields — the item's cached high/low alch value —
	 * so the lot closes at the real proceeds instead of the current average. Casts on
	 * items with no cached alch value stay unclaimed and take the unknown-source path.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String target = event.getMenuTarget();
		if (target == null || event.getItemId() <= 0)
			return;

		if (POUCH_FILL_OPTION.equals(event.getMenuOption()) && isPouchTarget(target))
		{
			pouchFillTick = client.getTickCount();
			return;
		}

		if (LOOT_SACK_OPTION.equals(event.getMenuOption())
				&& target.toLowerCase().contains(LOOT_SACK_TARGET))
		{
			rewardContainerTick = client.getTickCount();
			return;
		}

		boolean high = target.contains("High Level Alchemy");
		if (!high && !target.contains("Low Level Alchemy"))
			return;

		int canonicalId = itemManager.canonicalize(event.getItemId());
		TrackedItem tracked = trackedItems.get(canonicalId);
		if (tracked == null)
			return;

		long alchValue = high ? tracked.getHighAlch() : tracked.getLowAlch();
		if (alchValue <= 0)
			return;

		sourceAttribution.claim(AcquisitionSource.ALCHEMY, canonicalId, 1, alchValue, client.getTickCount());
	}

	/** Adds a "Track Item" / "Stop Tracking" right-click option to item menu entries, when enabled. */
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
			final boolean tracked = trackedItems.containsKey(canonicalId);

			client.getMenu().createMenuEntry(1)
					.setOption(tracked
							? ColorUtil.prependColorTag("Stop Tracking", config.stopTrackingColor())
							: ColorUtil.prependColorTag("Track Item", config.trackItemColor()))
					.setTarget(entry.getTarget())
					.setType(MenuAction.RUNELITE)
					.onClick(e ->
					{
						if (tracked)
							removeTrackedItem(canonicalId);
						else
							addTrackedItem(canonicalId);
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

	/**
	 * Tracks per-container item counts as inventory/bank/etc. change, accumulating
	 * the deltas to apply on the next client tick. The first sight of a container
	 * after login only seeds a baseline (no deltas); seeing the bank can trigger a
	 * full reconcile for auto-add.
	 */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int containerId = event.getContainerId();
		if (containerId == InventoryID.TRADEOFFER || containerId == TRADE_OTHER_CONTAINER)
		{
			boolean mine = containerId == InventoryID.TRADEOFFER;
			captureTradeOffer(mine ? myTradeOffer : theirTradeOffer, event.getItemContainer(), mine);
			return;
		}

		if (REWARD_CONTAINERS.contains(containerId))
			rewardContainerTick = client.getTickCount();

		if (!TRACKED_CONTAINERS.contains(containerId))
			return;

		boolean firstSync = seenContainersSinceLogin.add(containerId);

		Map<Integer, Integer> oldCounts = containerCounts.getOrDefault(containerId, Collections.emptyMap());
		Map<Integer, Integer> newCounts = new HashMap<>();
		ItemContainer container = event.getItemContainer();
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item.getId() > 0 && !isPlaceholder(item.getId()))
					newCounts.merge(itemManager.canonicalize(item.getId()), item.getQuantity(), Integer::sum);
			}
		}

		if (!firstSync)
		{
			Set<Integer> allIds = new HashSet<>(oldCounts.keySet());
			allIds.addAll(newCounts.keySet());
			for (int itemId : allIds)
			{
				int delta = newCounts.getOrDefault(itemId, 0) - oldCounts.getOrDefault(itemId, 0);
				if (delta != 0)
					pendingItemDeltas.merge(itemId, delta, Integer::sum);
			}

			pendingQuantitySync = true;

			if (shopOpen && containerId == InventoryID.INV)
				registerShopClaims(oldCounts, newCounts);
		}

		containerCounts.put(containerId, newCounts);

		if (firstSync && containerId == InventoryID.BANK && config.autoAddItems())
			reconcileAllQuantities();

		refreshPanel();
	}

	/**
	 * Per-tick work: flushes any pending quantity sync, evaluates notifications,
	 * and (when ground highlighting is on) reorders tracked items' "Take" menu
	 * entries to the bottom so they don't get in the way of normal actions.
	 */
	@Subscribe
	public void onClientTick(ClientTick event)
	{
		sourceAttribution.expire(client.getTickCount());
		correlateGroundActivity();
		if (pendingQuantitySync)
		{
			pendingQuantitySync = false;
			correlateProcessing();
			correlateDecant();
			correlateGathering();
			correlateReward();
			correlateThieving();
			syncQuantitiesFromContainers();
		}

		flushPendingSellRealize();

		evaluateNotifications();

		if (!config.highlightTrackedItems().ground() || client.isMenuOpen())
			return;

		final MenuEntry[] entries = client.getMenu().getMenuEntries();
		final List<MenuEntry> normal = new ArrayList<>(entries.length);
		final List<MenuEntry> trackedTakes = new ArrayList<>();

		for (MenuEntry entry : entries)
		{
			if (entry.getType() == MenuAction.GROUND_ITEM_THIRD_OPTION
					&& isTracked(itemManager.canonicalize(entry.getIdentifier())))
			{
				trackedTakes.add(entry);
			}
			else
			{
				normal.add(entry);
			}
		}

		if (trackedTakes.isEmpty())
			return;

		normal.addAll(trackedTakes);
		client.getMenu().setMenuEntries(normal.toArray(new MenuEntry[0]));
	}

	/**
	 * Grand Exchange integration: each tick, detects the item on the open offer setup/details
	 * screen and, per {@link PricewatchConfig#geIntegration()}, either auto-opens it in Pricewatch
	 * or injects a "View in Pricewatch" button. Only acts when the shown item changes.
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		expireGroundSuspensions();
		expireDeathSuspensions();
		closeVanishedGraveLosses();

		GeIntegrationMode mode = config.geIntegration();
		boolean wantButton = mode == GeIntegrationMode.BUTTON || mode == GeIntegrationMode.BOTH;
		if (!wantButton)
			hideGeButton();

		if (mode == GeIntegrationMode.OFF)
		{
			currentGeItem = -1;
			return;
		}

		int item = currentGeOfferItem();
		if (item != currentGeItem)
		{
			currentGeItem = item;
			hideGeButton();

			if (item > 0 && (mode == GeIntegrationMode.AUTO || mode == GeIntegrationMode.BOTH))
				openGeItemInPricewatch(item);
		}

		if (wantButton && item > 0 && geButton == null)
			injectGeButton();
	}

	/** Hides and forgets the injected GE button, if one is currently on the offer interface. */
	private void hideGeButton()
	{
		if (geButton == null)
			return;

		geButton.setHidden(true);
		geButton = null;
	}

	/** Forces the GE button to be re-injected against a freshly (re)built offer interface. */
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.GE_OFFERS)
			geButton = null;

		if (event.getGroupId() == InterfaceID.SHOPMAIN)
			shopOpen = true;
	}

	/** Clears GE-integration state when the offer interface closes, and shop state for #67. */
	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.GE_OFFERS)
		{
			currentGeItem = -1;
			geButton = null;
		}

		if (event.getGroupId() == InterfaceID.SHOPMAIN)
			shopOpen = false;
	}

	/** @return the item shown on the visible GE offer setup/details screen, or -1 when none is open. */
	private int currentGeOfferItem()
	{
		int item = itemInGeContainer(InterfaceID.GeOffers.SETUP);
		if (item > 0)
			return item;

		return itemInGeContainer(InterfaceID.GeOffers.DETAILS);
	}

	/** @return the first item id found in the given GE container's subtree, or -1 when hidden/absent. */
	private int itemInGeContainer(int componentId)
	{
		Widget container = client.getWidget(componentId);
		if (container == null || container.isHidden())
			return -1;

		return scanForItem(container);
	}

	/** Recursively searches a widget subtree for the first child holding a real item id. */
	private int scanForItem(Widget widget)
	{
		if (widget == null)
			return -1;

		if (widget.getItemId() > 0 && isRealItem(widget.getItemId()))
			return widget.getItemId();

		Widget[][] groups = {widget.getStaticChildren(), widget.getDynamicChildren(), widget.getNestedChildren()};
		for (Widget[] group : groups)
		{
			if (group == null)
				continue;

			for (Widget child : group)
			{
				int id = scanForItem(child);
				if (id > 0)
					return id;
			}
		}

		return -1;
	}

	/**
	 * @return whether {@code itemId} resolves to a real, defined item. Empty widget
	 * slots are backed by placeholder items (e.g. id 6512) whose composition name is
	 * the literal string "null"; those must not open a preview.
	 */
	private boolean isRealItem(int itemId)
	{
		String name = itemManager.getItemComposition(itemId).getName();
		return name != null && !name.isEmpty() && !"null".equalsIgnoreCase(name);
	}

	/** Opens the item in Pricewatch's view-only preview, switching to/focusing the panel when configured. */
	private void openGeItemInPricewatch(int itemId)
	{
		if (itemId <= 0)
			return;

		int canonicalId = itemManager.canonicalize(itemId);
		if (trackedItems.containsKey(canonicalId))
			SwingUtilities.invokeLater(() -> panel.openTrackedDetail(canonicalId));
		else
			previewItem(canonicalId);

		if (config.geFocusPanel())
			SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
	}

	/** Injects a native-style "View in Pricewatch" button onto the visible GE offer container. */
	private void injectGeButton()
	{
		Widget container = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (container == null || container.isHidden())
			container = client.getWidget(InterfaceID.GeOffers.DETAILS);

		if (container == null || container.isHidden())
			return;

		Widget button = container.createChild(-1, WidgetType.TEXT);
		button.setText("View in Pricewatch");
		button.setFontId(495);
		button.setTextColor(0xff981f);
		button.setTextShadowed(true);
		button.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		button.setXTextAlignment(WidgetTextAlignment.RIGHT);
		button.setOriginalX(10);
		button.setOriginalY(10);
		button.setOriginalWidth(120);
		button.setOriginalHeight(18);
		button.setHasListener(true);
		button.setAction(0, "View in Pricewatch");
		button.setOnOpListener((JavaScriptCallback) e -> openGeItemInPricewatch(currentGeItem));
		button.setOnMouseOverListener((JavaScriptCallback) e -> button.setTextColor(0xffffff));
		button.setOnMouseLeaveListener((JavaScriptCallback) e -> button.setTextColor(0xff981f));
		button.revalidate();

		geButton = button;
	}

	/** Records a ground item and its tile so the ground overlay can outline it, buffering it for #65. */
	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		groundItems.put(event.getItem(), event.getTile());
		if (isTracked(itemManager.canonicalize(event.getItem().getId())))
			tickGroundSpawns.add(event);
	}

	/** Forgets a ground item once it despawns, buffering it for #65's pickup/lost-drop correlation. */
	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		groundItems.remove(event.getItem());
		if (myDrops.containsKey(event.getItem()) || isTracked(itemManager.canonicalize(event.getItem().getId())))
			tickGroundDespawns.add(event);
	}

	/** Buffers ground-stack quantity changes so drops onto an existing stack correlate like spawns (#65). */
	@Subscribe
	public void onItemQuantityChanged(ItemQuantityChanged event)
	{
		if (myDrops.containsKey(event.getItem()) || isTracked(itemManager.canonicalize(event.getItem().getId())))
			tickGroundQuantityChanges.add(event);
	}

	/**
	 * Correlates this tick's ground-item activity with the pending inventory deltas (#65):
	 * a spawn (or stack increase) on the player's tile matching a pending removal is our
	 * drop — its units queue for ground suspension and the {@code TileItem} is remembered;
	 * a despawn of a remembered drop with no matching pickup closes its units as lost at 0;
	 * a despawn matching a pending addition that isn't ours is a loot pickup, claimed as a
	 * {@link AcquisitionSource#GROUND} acquisition at 0. Runs before the quantity sync
	 * consumes the deltas.
	 */
	private void correlateGroundActivity()
	{
		if (tickGroundSpawns.isEmpty() && tickGroundDespawns.isEmpty() && tickGroundQuantityChanges.isEmpty())
			return;

		WorldPoint myLocation = client.getLocalPlayer() == null
				? null
				: client.getLocalPlayer().getWorldLocation();

		for (ItemSpawned spawn : tickGroundSpawns)
			correlateGroundGain(spawn.getItem(), spawn.getTile(), spawn.getItem().getQuantity(), myLocation);

		for (ItemQuantityChanged change : tickGroundQuantityChanges)
		{
			int delta = change.getNewQuantity() - change.getOldQuantity();
			if (delta > 0)
				correlateGroundGain(change.getItem(), change.getTile(), delta, myLocation);
			else
				correlateGroundTaken(change.getItem(), -delta);
		}

		for (ItemDespawned despawn : tickGroundDespawns)
			correlateGroundTaken(despawn.getItem(), despawn.getItem().getQuantity());

		tickGroundSpawns.clear();
		tickGroundDespawns.clear();
		tickGroundQuantityChanges.clear();
	}

	/**
	 * Handles a ground pile gaining units: on our tile against a pending removal, it's our
	 * drop. Gated by the Source-Based Pricing toggle — when off, no new ground suspensions
	 * are taken, so a drop closes classically at the average price; drops suspended while
	 * the toggle was on still resolve through the un-suspend/lost paths.
	 */
	private void correlateGroundGain(TileItem item, Tile tile, int gained, WorldPoint myLocation)
	{
		if (!config.sourcePricing())
			return;

		if (myLocation == null || !myLocation.equals(tile.getWorldLocation()))
			return;

		int canonicalId = itemManager.canonicalize(item.getId());
		if (!isTracked(canonicalId))
			return;

		int queued = pendingGroundSuspend.getOrDefault(canonicalId, 0);
		int pendingRemoval = -pendingItemDeltas.getOrDefault(canonicalId, 0) - queued;
		if (pendingRemoval <= 0)
			return;

		int qty = Math.min(gained, pendingRemoval);
		pendingGroundSuspend.merge(canonicalId, qty, Integer::sum);
		myDrops.merge(item, qty, Integer::sum);
	}

	/**
	 * Handles a ground pile losing units: a remembered drop with a matching pending
	 * addition is a re-pickup (the greedy un-suspend consumes it during the sync);
	 * with no matching addition its units close as lost at 0. An unfamiliar pile
	 * matching a pending addition is a loot pickup, claimed as {@code GROUND} at 0.
	 */
	private void correlateGroundTaken(TileItem item, int taken)
	{
		int canonicalId = itemManager.canonicalize(item.getId());
		Integer ours = myDrops.get(item);
		int pendingAddition = pendingItemDeltas.getOrDefault(canonicalId, 0);

		if (ours != null)
		{
			int resolved = Math.min(ours, taken);
			if (pendingAddition > 0)
				pendingGroundUnsuspend.merge(canonicalId, Math.min(resolved, pendingAddition), Integer::sum);
			else
				closeGroundLost(canonicalId, resolved);

			if (resolved >= ours)
				myDrops.remove(item);
			else
				myDrops.put(item, ours - resolved);

			return;
		}

		if (pendingAddition > 0 && isTracked(canonicalId))
			sourceAttribution.claim(AcquisitionSource.GROUND, canonicalId, Math.min(taken, pendingAddition), 0,
					client.getTickCount());
	}

	/** Marks the local player's death, opening the death-loss suspension window (#70). */
	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() == client.getLocalPlayer())
		{
			deathTick = client.getTickCount();
			deathLossTick = -1;
			graveGoneTick = -1;
		}
	}

	/**
	 * Marks the tick of processing-skill XP gains (recipe actions, #69), gathering-skill XP
	 * gains (#213), and Thieving XP gains (#217).
	 */
	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Integer previous = lastSkillXp.put(event.getSkill(), event.getXp());
		if (previous == null || event.getXp() <= previous)
			return;

		if (PROCESSING_SKILLS.contains(event.getSkill()))
			processingXpTick = client.getTickCount();

		if (GATHERING_SKILLS.contains(event.getSkill()))
			gatherXpTick = client.getTickCount();

		if (event.getSkill() == Skill.THIEVING)
			thievingXpTick = client.getTickCount();
	}

	/**
	 * Pairs this tick's consumed inputs with the produced output when a processing-skill
	 * XP gain identifies a recipe action (#69), transferring the summed input cost: tracked
	 * inputs contribute (and close at) their FIFO open-lot cost, untracked inputs their
	 * fallback market value, and the total is carried onto the output's new lot(s) by
	 * {@link #consumeProcessingOutput} so their basis sums exactly to it. Multi-output ticks
	 * are unattributable and left to the fallback; tracked inputs with no tracked output
	 * close at 0. A worthless, non-tradeable output is a destroyed product and is handled
	 * without an XP signal — a burn or crush gives none — closing each tracked input as a
	 * realized loss at 0 (#144): a crushed gem tags the input {@link AcquisitionSource#CRUSHED},
	 * any other destroyed product {@link AcquisitionSource#BURNED}. When the player tracks the
	 * destroyed byproduct itself, its gain is booked at 0-cost under that same source (#172).
	 * Coins never participate.
	 */
	private void correlateProcessing()
	{
		pendingProcessingOutput.clear();
		if (!config.sourcePricing() || pendingItemDeltas.isEmpty())
			return;

		List<int[]> inputs = new ArrayList<>();
		int outputId = 0;
		int outputQty = 0;
		int outputKinds = 0;
		for (Map.Entry<Integer, Integer> entry : pendingItemDeltas.entrySet())
		{
			int itemId = entry.getKey();
			int delta = entry.getValue();
			if (itemId == ItemID.COINS || delta == 0)
				continue;

			if (delta < 0)
			{
				inputs.add(new int[]{itemId, -delta});
			}
			else
			{
				outputKinds++;
				outputId = itemId;
				outputQty = delta;
			}
		}

		if (inputs.isEmpty() || outputKinds > 1)
			return;

		if (outputKinds == 1 && isDestroyedProduct(outputId))
		{
			AcquisitionSource loss = outputId == ItemID.CRUSHED_GEMSTONE
					? AcquisitionSource.CRUSHED
					: AcquisitionSource.BURNED;
			for (int[] input : inputs)
				if (isTracked(input[0]))
					sourceAttribution.claim(loss, input[0], input[1], 0, client.getTickCount());

			if (isTracked(outputId))
				sourceAttribution.claim(loss, outputId, outputQty, 0, client.getTickCount());
			return;
		}

		if (client.getTickCount() - processingXpTick > 1)
			return;

		boolean trackedOutput = outputKinds == 1 && isTracked(outputId);
		long totalCost = 0;
		for (int[] input : inputs)
		{
			TrackedItem tracked = trackedItems.get(input[0]);
			if (tracked == null)
			{
				totalCost += untrackedInputValue(input[0]) * input[1];
				continue;
			}

			long basis = ProcessingBasis.openLotCost(tracked.getAcquisitions(), input[1]);
			totalCost += basis;
			sourceAttribution.claim(AcquisitionSource.PROCESSING, input[0], input[1],
					trackedOutput ? basis / input[1] : 0, client.getTickCount());
		}

		if (trackedOutput && outputQty > 0)
			pendingProcessingOutput.put(outputId, totalCost);
	}

	/**
	 * Pairs a dose family's consumed lots with the doses it produces on a single XP-less tick, so
	 * cost basis follows the liquid across the item-id change rather than being realized as a sale.
	 * Groups the tick's non-coin deltas into dose families ({@link DoseFamily}) and hands each family
	 * to {@link #correlateDoseSwapFamily}, which distinguishes two cases:
	 * <ul>
	 *   <li><b>Decant</b> (#220) — consumed doses equal produced doses: a pure swap, so the combined
	 *       input basis is distributed dose-weighted ({@link DecantBasis}) onto the produced ids under
	 *       {@link AcquisitionSource#DECANT}. Up, down, and mixed-basis inputs all merge.</li>
	 *   <li><b>Consume-down</b> (#218) — a dose is drunk (consumed doses exceed produced doses, but
	 *       some remain): the <em>full</em> input basis follows onto the lower-dose id under
	 *       {@link AcquisitionSource#CONSUMED}, since using a dose realizes no profit or loss.</li>
	 * </ul>
	 * Both queue the carried basis in {@code pendingDecantOutput} / {@code pendingConsumedOutput} so the
	 * matching gain opens the output lot carrying it; both close the consumed lots at their FIFO cost
	 * (no P/L). Untracked inputs contribute their fallback value; untracked outputs drop their share.
	 * Runs after {@link #correlateProcessing} — which a processing-XP tick handles instead — and before
	 * the source detectors, whose gains skip any id already queued in {@code pendingProcessingOutput},
	 * {@code pendingDecantOutput}, or {@code pendingConsumedOutput}. Gated by the Source-Based Pricing toggle.
	 */
	private void correlateDecant()
	{
		pendingDecantOutput.clear();
		pendingConsumedOutput.clear();
		potionEmptiedCount = 0;
		if (!config.sourcePricing() || pendingItemDeltas.isEmpty()
				|| client.getTickCount() - processingXpTick <= 1)
			return;

		Map<String, List<int[]>> families = new HashMap<>();
		for (Map.Entry<Integer, Integer> entry : pendingItemDeltas.entrySet())
		{
			int itemId = entry.getKey();
			int delta = entry.getValue();
			if (itemId == ItemID.COINS || delta == 0)
				continue;

			DoseFamily.Parsed parsed = DoseFamily.parse(itemManager.getItemComposition(itemId).getName());
			if (parsed == null)
				continue;

			families.computeIfAbsent(parsed.base, k -> new ArrayList<>()).add(new int[]{itemId, delta, parsed.doses});
		}

		for (List<int[]> members : families.values())
			correlateDoseSwapFamily(members);
	}

	/**
	 * Applies the dose-family basis transfer to one family's members ({@code {id, delta, doses}}):
	 * a dose-conserving swap (consumed doses equal produced doses) is a <b>decant</b> under
	 * {@link AcquisitionSource#DECANT}; a swap that loses doses while leaving some (a dose drunk) is
	 * a <b>consume-down</b> under {@link AcquisitionSource#CONSUMED}. Anything else — no consumption,
	 * or every dose gone (a last dose drunk, left to the {@link #applyDelta} loss path) — is skipped.
	 * The consumed lots close at their FIFO cost and the summed basis is distributed onto the produced
	 * ids: dose-weighted for a decant, but in full for a consume-down since a used dose is not a loss.
	 */
	private void correlateDoseSwapFamily(List<int[]> members)
	{
		long consumedDoses = 0;
		long producedDoses = 0;
		for (int[] member : members)
			if (member[1] < 0)
				consumedDoses += (long) -member[1] * member[2];
			else
				producedDoses += (long) member[1] * member[2];

		if (consumedDoses == 0 || producedDoses == 0 || producedDoses > consumedDoses)
		{
			if (consumedDoses > 0 && producedDoses == 0)
			{
				int emptied = members.stream().filter(m -> m[1] < 0).mapToInt(m -> -m[1]).sum();
				potionEmptiedTick = client.getTickCount();
				potionEmptiedCount += emptied;
			}

			return;
		}

		boolean decant = consumedDoses == producedDoses;
		AcquisitionSource source = decant ? AcquisitionSource.DECANT : AcquisitionSource.CONSUMED;
		Map<Integer, Long> pending = decant ? pendingDecantOutput : pendingConsumedOutput;

		long totalBasis = 0;
		for (int[] member : members)
		{
			if (member[1] >= 0)
				continue;

			int itemId = member[0];
			int qty = -member[1];
			TrackedItem tracked = trackedItems.get(itemId);
			if (tracked == null)
			{
				totalBasis += untrackedInputValue(itemId) * qty;
				continue;
			}

			long basis = ProcessingBasis.openLotCost(tracked.getAcquisitions(), qty);
			totalBasis += basis;
			sourceAttribution.claim(source, itemId, qty, basis / qty, client.getTickCount());
		}

		List<int[]> outputs = new ArrayList<>();
		for (int[] member : members)
			if (member[1] > 0)
				outputs.add(new int[]{member[0], member[1] * member[2]});

		Map<Integer, Long> shares = DecantBasis.distribute(totalBasis, outputs);
		for (Map.Entry<Integer, Long> share : shares.entrySet())
			if (isTracked(share.getKey()))
				pending.put(share.getKey(), share.getValue());
	}

	/**
	 * Attributes this tick's unclaimed inventory gains to {@link AcquisitionSource#GATHER} at
	 * 0 when a gathering-skill XP drop (Hunter, Mining, Fishing, Woodcutting, Farming) marks
	 * them as gathered from the world at no cost (#213) — Sunfire splinters, antlers, ores,
	 * fish, logs, harvested herbs. Runs after {@link #correlateProcessing} (so a paired recipe
	 * output, already queued in {@code pendingProcessingOutput}, is skipped and keeps its
	 * transferred basis) and before the quantity sync consumes the deltas. A gain with no
	 * gathering XP this tick stays unclaimed and takes the unknown-source path. Coins never
	 * participate. Gated by the Source-Based Pricing toggle.
	 *
	 * <p>Yields to {@link #correlateReward}: when a reward-loot signal ({@link #rewardContainerTick})
	 * fired this tick, the gains are reward loot, not gathered — some reward interactions (e.g. the
	 * Tempoross reward pool) also grant gathering XP on the same tick, which would otherwise let a
	 * GATHER claim win the FIFO over the correct REWARD one (#215).
	 */
	private void correlateGathering()
	{
		if (!config.sourcePricing() || client.getTickCount() - gatherXpTick > 1 || pendingItemDeltas.isEmpty())
			return;

		if (client.getTickCount() - rewardContainerTick <= 1)
			return;

		for (Map.Entry<Integer, Integer> entry : pendingItemDeltas.entrySet())
		{
			int itemId = entry.getKey();
			int delta = entry.getValue();
			if (delta <= 0 || itemId == ItemID.COINS || pendingProcessingOutput.containsKey(itemId)
					|| pendingDecantOutput.containsKey(itemId) || pendingConsumedOutput.containsKey(itemId))
				continue;

			if (isTracked(itemId))
				sourceAttribution.claim(AcquisitionSource.GATHER, itemId, delta, 0, client.getTickCount());
		}
	}

	/**
	 * Claims this tick's tracked inventory gains as a free {@link AcquisitionSource#REWARD} at 0
	 * when a reward-loot signal fired on the same tick ({@link #rewardContainerTick}) — a reward/loot
	 * container change ({@link #REWARD_CONTAINERS}), a Huntsman's loot-sack open, or a "you found some
	 * loot" chat line — i.e. loot taken from a raids chest, clue casket, reward pool or similar (#215).
	 * Takes precedence over {@link #correlateGathering}, which yields when this signal is present so a
	 * coincident gathering-XP tick can't mislabel the loot. Runs before the quantity sync consumes the
	 * deltas; a paired recipe output already queued in {@code pendingProcessingOutput} is skipped and
	 * keeps its transferred basis. A gain with no reward signal this tick stays unclaimed and takes the
	 * unknown-source path. Coins never participate. Gated by the Source-Based Pricing toggle.
	 */
	private void correlateReward()
	{
		if (!config.sourcePricing() || client.getTickCount() - rewardContainerTick > 1 || pendingItemDeltas.isEmpty())
			return;

		for (Map.Entry<Integer, Integer> entry : pendingItemDeltas.entrySet())
		{
			int itemId = entry.getKey();
			int delta = entry.getValue();
			if (delta <= 0 || itemId == ItemID.COINS || pendingProcessingOutput.containsKey(itemId)
					|| pendingDecantOutput.containsKey(itemId) || pendingConsumedOutput.containsKey(itemId))
				continue;

			if (isTracked(itemId))
				sourceAttribution.claim(AcquisitionSource.REWARD, itemId, delta, 0, client.getTickCount());
		}
	}

	/**
	 * Attributes this tick's unclaimed inventory gains to {@link AcquisitionSource#THIEVING} at
	 * 0 when a Thieving XP drop marks them as stolen at no cost (#217) — pickpocket loot, stall
	 * produce, chest hauls. An exact mirror of {@link #correlateGathering}: it runs after
	 * {@link #correlateReward} (so reward loot keeps its REWARD claim) and before the quantity sync
	 * consumes the deltas; a paired recipe output already queued in {@code pendingProcessingOutput}
	 * is skipped and keeps its transferred basis. A gain with no Thieving XP this tick stays
	 * unclaimed and takes the unknown-source path. Coins never participate. Gated by the
	 * Source-Based Pricing toggle.
	 *
	 * <p>Yields to {@link #correlateReward}: when a reward-loot signal ({@link #rewardContainerTick})
	 * fired this tick, the gains are reward loot, not stolen, so a coincident Thieving-XP tick can't
	 * mislabel them (precedence: Processing &gt; Reward &gt; Gather/Thieving &gt; Unknown).
	 */
	private void correlateThieving()
	{
		if (!config.sourcePricing() || client.getTickCount() - thievingXpTick > 1 || pendingItemDeltas.isEmpty())
			return;

		if (client.getTickCount() - rewardContainerTick <= 1)
			return;

		for (Map.Entry<Integer, Integer> entry : pendingItemDeltas.entrySet())
		{
			int itemId = entry.getKey();
			int delta = entry.getValue();
			if (delta <= 0 || itemId == ItemID.COINS || pendingProcessingOutput.containsKey(itemId)
					|| pendingDecantOutput.containsKey(itemId) || pendingConsumedOutput.containsKey(itemId))
				continue;

			if (isTracked(itemId))
				sourceAttribution.claim(AcquisitionSource.THIEVING, itemId, delta, 0, client.getTickCount());
		}
	}

	/**
	 * @return whether {@code itemId} is a worthless destroyed processing product — a
	 * non-tradeable item (absent from the GE mapping) with no market value, such as burnt
	 * food or a crushed gem. Requires the mapping to have loaded so a genuine tradeable
	 * item is never mistaken for one before its price is known.
	 */
	private boolean isDestroyedProduct(int itemId)
	{
		return mappingsLoaded
				&& !itemMappings.containsKey(itemId)
				&& itemManager.getItemPrice(itemId) <= 0;
	}

	/** @return an untracked processing input's per-unit value under the configured fallback pricing. */
	private long untrackedInputValue(int itemId)
	{
		switch (config.fallbackPricing())
		{
			case ZERO:
				return 0;
			default:
				return itemManager.getItemPrice(itemId);
		}
	}

	/**
	 * Snapshots one side of the trade window (canonical id → quantity) as its container
	 * changes. For our own side, diffs the new offer against the previous snapshot and
	 * queues the change so the matching inventory removal suspends (rather than closes) the
	 * offered lots, and a later withdrawal un-suspends them (#66).
	 */
	private void captureTradeOffer(Map<Integer, Integer> side, ItemContainer container, boolean mine)
	{
		Map<Integer, Integer> previous = mine ? new HashMap<>(side) : null;
		side.clear();
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item.getId() > 0)
					side.merge(itemManager.canonicalize(item.getId()), item.getQuantity(), Integer::sum);
			}
		}

		if (mine)
			queueTradeSuspension(previous, side);
	}

	/**
	 * Turns the change in our own offer into pending suspend/un-suspend intents: items added to
	 * the offer left our inventory and should suspend, items withdrawn returned and should
	 * un-suspend. Only tracked, non-currency items queue — coins and platinum tokens are the
	 * trade's numerator, not a lot, and untracked items never flow through {@link #applyDelta}
	 * to consume the intent.
	 */
	private void queueTradeSuspension(Map<Integer, Integer> before, Map<Integer, Integer> after)
	{
		if (!config.sourcePricing())
			return;

		Set<Integer> ids = new HashSet<>(before.keySet());
		ids.addAll(after.keySet());
		for (int id : ids)
		{
			if (isTradeCurrency(id) || !isTracked(id))
				continue;

			int delta = after.getOrDefault(id, 0) - before.getOrDefault(id, 0);
			if (delta > 0)
				pendingTradeSuspend.merge(id, delta, Integer::sum);
			else if (delta < 0)
				pendingTradeUnsuspend.merge(id, -delta, Integer::sum);
		}
	}

	/** Registers the completed trade's claims when the game confirms the exchange (#66). */
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() == ChatMessageType.TRADE && "Accepted trade.".equals(event.getMessage()))
			registerTradeClaims();

		if (isPouchDepositMessage(event.getMessage()))
			pouchDepositTick = client.getTickCount();

		if (event.getMessage() != null && event.getMessage().startsWith(REWARD_LOOT_PREFIX))
			rewardContainerTick = client.getTickCount();
	}

	/**
	 * @return whether a chat line signals a hunting pouch emptying into the bank — either the
	 *         per-pouch "Empty" deposit ("You deposit some &lt;fur/meat&gt; into your bank.") or
	 *         the bank's "Empty containers" button. Only pouch emptying produces these lines; a
	 *         normal manual bank deposit is silent, so there is no false positive (#214).
	 */
	private static boolean isPouchDepositMessage(String message)
	{
		if (message == null)
			return false;

		return EMPTY_CONTAINERS_MESSAGE.equals(message)
				|| (message.startsWith(POUCH_DEPOSIT_PREFIX) && message.endsWith(POUCH_DEPOSIT_SUFFIX));
	}

	/** @return whether a "Fill" menu target names a fur/meat hunting pouch (any size) (#214). */
	private static boolean isPouchTarget(String target)
	{
		String lower = target.toLowerCase();
		return POUCH_TARGETS.stream().anyMatch(lower::contains);
	}

	/**
	 * Books a completed trade's item movements as {@link AcquisitionSource#PLAYER_TRADE} (#66):
	 * items received buy in at the gp we gave apportioned across them by market value, and
	 * items given close at the gp we received apportioned the same way. Pure item-for-item
	 * legs price at 0; coins and platinum tokens (valued at 1,000 gp each) are the
	 * numerator, never an apportionment target.
	 *
	 * <p>The two sides settle differently. Received items only enter our inventory now, so they
	 * are registered as claims for the imminent additions to match. Given items already left our
	 * inventory when they were offered (suspended, not closed), so there is no delta to match —
	 * they are closed here directly against their trade suspension.
	 */
	private void registerTradeClaims()
	{
		long gpPaid = tradeGp(myTradeOffer);
		long gpReceived = tradeGp(theirTradeOffer);

		claimReceivedItems(theirTradeOffer, gpPaid);
		closeGivenItems(myTradeOffer, gpReceived);

		myTradeOffer.clear();
		theirTradeOffer.clear();
	}

	/** Gp value of one platinum token, the coin-equivalent currency for trades above max cash. */
	private static final long PLATINUM_TOKEN_GP = 1_000L;

	/**
	 * @return whether the item is trade currency — coins or platinum tokens — which
	 *         forms the trade's gp numerator rather than a lot-bearing item leg
	 */
	private static boolean isTradeCurrency(int itemId)
	{
		return itemId == ItemID.COINS || itemId == ItemID.PLATINUM;
	}

	/** @return one trade side's money in gp: coins plus platinum tokens at 1,000 gp each. */
	private static long tradeGp(Map<Integer, Integer> side)
	{
		return side.getOrDefault(ItemID.COINS, 0)
				+ PLATINUM_TOKEN_GP * side.getOrDefault(ItemID.PLATINUM, 0);
	}

	/** Builds one trade side's non-currency apportionment legs, each weighted by its unit market value. */
	private List<TradeApportioner.Leg> tradeLegs(Map<Integer, Integer> side)
	{
		List<TradeApportioner.Leg> legs = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : side.entrySet())
		{
			if (!isTradeCurrency(entry.getKey()) && entry.getValue() > 0)
				legs.add(new TradeApportioner.Leg(entry.getKey(), entry.getValue(),
						marketUnitValue(entry.getKey())));
		}

		return legs;
	}

	/** Claims received items as buys at the apportioned per-unit price, matched by their inventory additions. */
	private void claimReceivedItems(Map<Integer, Integer> side, long gp)
	{
		List<TradeApportioner.Leg> legs = tradeLegs(side);
		Map<Integer, Long> prices = TradeApportioner.apportion(legs, gp);
		for (TradeApportioner.Leg leg : legs)
		{
			if (isTracked(leg.itemId))
				sourceAttribution.claim(AcquisitionSource.PLAYER_TRADE, leg.itemId, leg.quantity,
						prices.get(leg.itemId), client.getTickCount());
		}
	}

	/**
	 * Closes given items as sells at the apportioned per-unit price, realizing them against the
	 * trade suspension taken when they were offered (bounded by it, so a partial or already-settled
	 * suspension can never over-close).
	 */
	private void closeGivenItems(Map<Integer, Integer> side, long gp)
	{
		List<TradeApportioner.Leg> legs = tradeLegs(side);
		Map<Integer, Long> prices = TradeApportioner.apportion(legs, gp);
		boolean changed = false;
		for (TradeApportioner.Leg leg : legs)
		{
			TrackedItem tracked = trackedItems.get(leg.itemId);
			if (tracked == null)
				continue;

			int qty = Math.min(leg.quantity, tracked.getTradeSuspendedQuantity());
			if (qty <= 0)
				continue;

			closeFifo(tracked, qty, prices.get(leg.itemId), AcquisitionSource.PLAYER_TRADE);
			tracked.setTradeSuspendedQuantity(tracked.getTradeSuspendedQuantity() - qty);
			changed = true;
		}

		if (changed)
		{
			persistTrackedItems();
			refreshPanel();
		}
	}

	/** @return an item's unit market value for apportionment weights: the tracked avg, or the wiki price. */
	private long marketUnitValue(int itemId)
	{
		TrackedItem tracked = trackedItems.get(itemId);
		if (tracked != null && tracked.getAvgPrice() > 0)
			return tracked.getAvgPrice();

		return itemManager.getItemPrice(itemId);
	}

	/**
	 * Claims an inventory change as a shop transaction (#67) when exactly one tracked
	 * non-coin item moved: the coins paid or received, divided across the quantity,
	 * price the item's {@link AcquisitionSource#SHOP} claim. A buy must pay coins; a
	 * sell must not spend them, and a worthless sell the shop pays nothing for is still
	 * a shop sale at 0. Anything murkier — multi-item changes, specialty-currency shops
	 * (tokkul, marks) that move a second item rather than coins — stays unclaimed and
	 * takes the unknown-source path.
	 */
	private void registerShopClaims(Map<Integer, Integer> oldCounts, Map<Integer, Integer> newCounts)
	{
		long coinDelta = 0;
		int changedItem = 0;
		int itemDelta = 0;
		int changedCount = 0;

		Set<Integer> allIds = new HashSet<>(oldCounts.keySet());
		allIds.addAll(newCounts.keySet());
		for (int itemId : allIds)
		{
			int delta = newCounts.getOrDefault(itemId, 0) - oldCounts.getOrDefault(itemId, 0);
			if (delta == 0)
				continue;

			if (itemId == ItemID.COINS)
			{
				coinDelta = delta;
			}
			else
			{
				changedCount++;
				changedItem = itemId;
				itemDelta = delta;
			}
		}

		if (changedCount != 1 || itemDelta == 0 || !isTracked(changedItem))
			return;

		boolean sell = itemDelta < 0;
		if (sell ? coinDelta < 0 : coinDelta >= 0)
			return;

		long unitPrice = Math.abs(coinDelta) / Math.abs(itemDelta);
		sourceAttribution.claim(AcquisitionSource.SHOP, changedItem, Math.abs(itemDelta), unitPrice,
				client.getTickCount());
	}

	/** Closes {@code qty} ground-suspended units of an item as lost: gone from the floor with no pickup. */
	private void closeGroundLost(int itemId, int qty)
	{
		TrackedItem tracked = trackedItems.get(itemId);
		if (tracked == null)
			return;

		int lost = Math.min(qty, tracked.getGroundSuspendedQuantity());
		if (lost <= 0)
			return;

		tracked.setGroundSuspendedQuantity(tracked.getGroundSuspendedQuantity() - lost);
		closeFifo(tracked, lost, 0, AcquisitionSource.GROUND);
		persistTrackedItems();
		refreshPanel();
	}

	/** Closes ground suspensions that outlived {@link #GROUND_SUSPEND_EXPIRY} as lost drops. */
	private void expireGroundSuspensions()
	{
		Instant cutoff = Instant.now().minus(GROUND_SUSPEND_EXPIRY);
		for (TrackedItem tracked : trackedItems.values())
		{
			if (tracked.getGroundSuspendedQuantity() > 0 && tracked.getGroundSuspendedAt() != null
					&& tracked.getGroundSuspendedAt().isBefore(cutoff))
				closeGroundLost(tracked.getItemId(), tracked.getGroundSuspendedQuantity());
		}
	}

	/** Closes every remaining ground suspension as lost — floor items rarely survive a logout. */
	private void closeAllGroundSuspensions()
	{
		for (TrackedItem tracked : trackedItems.values())
		{
			if (tracked.getGroundSuspendedQuantity() > 0)
				closeGroundLost(tracked.getItemId(), tracked.getGroundSuspendedQuantity());
		}

		myDrops.clear();
		pendingGroundSuspend.clear();
		pendingGroundUnsuspend.clear();
	}

	/**
	 * Resets transient and per-login state on game-state transitions: clears
	 * ground items on each load, and on login wipes the count caches and reloads
	 * the persisted tracked items.
	 */
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOADING:
				groundItems.clear();
				myDrops.clear();
				tickGroundSpawns.clear();
				tickGroundDespawns.clear();
				tickGroundQuantityChanges.clear();
				break;
			case LOGGED_IN:
				if (!sessionInitialized)
				{
					sessionInitialized = true;
					trackedItems.clear();
					previewItem = null;
					containerCounts.clear();
					runePouchCounts.clear();
					seenContainersSinceLogin.clear();
					runePouchSeenSinceLogin = false;
					geLoginTick = client.getTickCount();
					pendingQuantitySync = false;
					pendingItemDeltas.clear();
					loadCategories();
					loadPersistedItems();
					loadGeState();
					loadPortfolioHistory();
					geOfferTracker.clear();
					pendingSellSuspend.clear();
					pendingSellUnsuspend.clear();
					pendingSellRealize.clear();
					myDrops.clear();
					pendingGroundSuspend.clear();
					pendingGroundUnsuspend.clear();
					shopOpen = false;
					myTradeOffer.clear();
					theirTradeOffer.clear();
					pendingTradeSuspend.clear();
					pendingTradeUnsuspend.clear();
					lastSkillXp.clear();
					processingXpTick = -1;
					gatherXpTick = -1;
					rewardContainerTick = -1;
					thievingXpTick = -1;
					pouchFillTick = -1;
					pouchDepositTick = -1;
					pendingProcessingOutput.clear();
					pendingDecantOutput.clear();
					pendingConsumedOutput.clear();
					potionEmptiedTick = -1;
					potionEmptiedCount = 0;
					deathTick = -1;
					clientThread.invokeLater(this::hydratePriceCache);
				}

				refreshPanel();
				break;
			case LOGIN_SCREEN:
				sessionInitialized = false;
				closeAllGroundSuspensions();
				refreshPanel();
				break;
			default:
				break;
		}
	}

	/** Resets the session baseline when the RS profile (account) changes, so stats restart per account. */
	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		SwingUtilities.invokeLater(panel::clearSessionBaseline);
	}

	/**
	 * Mirrors rune pouch contents (held in varbits, not a normal container) into
	 * the quantity counts, accumulating deltas like a container change.
	 */
	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() == VarbitID.GRAVESTONE_VISIBLE)
		{
			onGravestoneVisibility(event.getValue() != 0);
			return;
		}

		if (RUNE_POUCH_VARBITS.contains(event.getVarbitId()))
		{
			Map<Integer, Integer> oldPouch = new HashMap<>(runePouchCounts);
			syncRunePouch();
			boolean firstSync = !runePouchSeenSinceLogin;
			runePouchSeenSinceLogin = true;
			if (!firstSync)
			{
				Set<Integer> allIds = new HashSet<>(oldPouch.keySet());
				allIds.addAll(runePouchCounts.keySet());
				for (int itemId : allIds)
				{
					int delta = runePouchCounts.getOrDefault(itemId, 0) - oldPouch.getOrDefault(itemId, 0);
					if (delta != 0)
						pendingItemDeltas.merge(itemId, delta, Integer::sum);
				}

				pendingQuantitySync = true;
			}

			refreshPanel();
		}
	}

	/** Rebuilds {@link #runePouchCounts} by reading the rune pouch type/quantity varbits. */
	private void syncRunePouch()
	{
		runePouchCounts.clear();
		EnumComposition runeEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		for (int i = 0; i < RUNE_POUCH_TYPE_VARBITS.length; i++)
		{
			int typeId = client.getVarbitValue(RUNE_POUCH_TYPE_VARBITS[i]);
			int qty    = client.getVarbitValue(RUNE_POUCH_QUANTITY_VARBITS[i]);
			if (typeId == 0 || qty <= 0)
				continue;

			int itemId = runeEnum.getIntValue(typeId);
			runePouchCounts.merge(itemId, qty, Integer::sum);
		}
	}

	/**
	 * Consumes GE offer progress to price trades and track the buy limit. Buy fills are
	 * ledgered until the items are collected; a sell's placement suspends the offered units
	 * and its fills realize them at the true price; a cancellation restores the remainder.
	 *
	 * <p>Just after login the offer sync replays pre-existing offers here rather than at
	 * container sync (whose offers array isn't populated yet). Within that window the state
	 * is rebuilt via {@link #primeGeStateFromLogin()} and the events are swallowed so they
	 * aren't replayed as fresh placements or fills.
	 */
	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		GrandExchangeOffer offer = event.getOffer();
		if (offer == null)
			return;

		if (geLoginTick >= 0 && client.getTickCount() - geLoginTick <= GE_LOGIN_SYNC_TICKS)
		{
			primeGeStateFromLogin();
			return;
		}

		GrandExchangeOfferState state = offer.getState();
		boolean buying = state == GrandExchangeOfferState.BUYING
				|| state == GrandExchangeOfferState.BOUGHT
				|| state == GrandExchangeOfferState.CANCELLED_BUY;
		boolean cancelled = state == GrandExchangeOfferState.CANCELLED_BUY
				|| state == GrandExchangeOfferState.CANCELLED_SELL;
		boolean empty = state == GrandExchangeOfferState.EMPTY;

		for (GeOfferTracker.Event e : geOfferTracker.onOffer(event.getSlot(), offer.getItemId(),
				buying, cancelled, empty, offer.getTotalQuantity(), offer.getQuantitySold(), offer.getSpent()))
			handleGeEvent(e);
	}

	/**
	 * Applies one derived GE event: ledger a buy, suspend/realize/restore a sell, and record
	 * the buy limit. With Source-Based Pricing off, no new pricing state is created — buys
	 * aren't ledgered (their additions price classically) and placements don't suspend (their
	 * removals close classically at the average price) — while fills and cancels still drain
	 * suspensions taken while the toggle was on, so nothing is stranded. Buy-limit tracking
	 * is informational, not pricing, and stays on either way.
	 */
	private void handleGeEvent(GeOfferTracker.Event e)
	{
		if (e.kind == GeOfferTracker.Kind.BUY)
		{
			if (e.type == GeOfferTracker.Type.FILL)
			{
				recordBuyLimit(e.itemId, e.quantity);
				if (config.sourcePricing())
				{
					pendingGeBuys.computeIfAbsent(e.itemId, k -> new ArrayDeque<>())
							.addLast(new long[]{e.quantity, e.unitPrice});
					scheduleGeStateSave();
				}
			}

			return;
		}

		switch (e.type)
		{
			case PLACED:
				if (config.sourcePricing())
					pendingSellSuspend.merge(e.itemId, e.quantity, Integer::sum);

				break;
			case FILL:
				realizeSell(e.itemId, e.quantity, e.unitPrice);
				break;
			case CANCELLED:
				pendingSellUnsuspend.merge(e.itemId, e.quantity, Integer::sum);
				break;
			default:
				break;
		}
	}

	/** Closes {@code qty} suspended units of a sold item at the realized GE price, then persists and refreshes. */
	private void realizeSell(int itemId, int qty, long unitPrice)
	{
		TrackedItem tracked = trackedItems.get(itemId);
		if (tracked == null)
			return;

		int realized = Math.min(qty, tracked.getSuspendedQuantity());
		if (realized > 0)
		{
			closeFifo(tracked, realized, unitPrice, AcquisitionSource.GE_TRADE);
			tracked.setSuspendedQuantity(tracked.getSuspendedQuantity() - realized);
			persistTrackedItems();
			refreshPanel();
		}

		int shortfall = qty - realized;
		if (shortfall > 0 && config.sourcePricing())
			pendingSellRealize.computeIfAbsent(itemId, k -> new ArrayDeque<>())
					.addLast(new long[]{shortfall, unitPrice});

		scheduleGeStateSave();
	}

	/**
	 * Closes any GE sell fill that outran its suspension, now that the placement inventory
	 * decrease has moved the units into {@code suspendedQuantity}. Runs each tick after the
	 * container sync; unmatched fills stay parked and retry on a later tick.
	 */
	private void flushPendingSellRealize()
	{
		if (pendingSellRealize.isEmpty())
			return;

		boolean changed = false;
		Iterator<Map.Entry<Integer, Deque<long[]>>> it = pendingSellRealize.entrySet().iterator();
		while (it.hasNext())
		{
			Map.Entry<Integer, Deque<long[]>> entry = it.next();
			TrackedItem tracked = trackedItems.get(entry.getKey());
			if (tracked == null)
			{
				it.remove();
				continue;
			}

			Deque<long[]> queue = entry.getValue();
			while (!queue.isEmpty() && tracked.getSuspendedQuantity() > 0)
			{
				long[] chunk = queue.peekFirst();
				int realize = (int) Math.min(chunk[0], tracked.getSuspendedQuantity());
				closeFifo(tracked, realize, chunk[1], AcquisitionSource.GE_TRADE);
				tracked.setSuspendedQuantity(tracked.getSuspendedQuantity() - realize);
				chunk[0] -= realize;
				changed = true;
				if (chunk[0] <= 0)
					queue.removeFirst();
			}

			if (queue.isEmpty())
				it.remove();
		}

		if (changed)
		{
			persistTrackedItems();
			scheduleGeStateSave();
			refreshPanel();
		}
	}

	/** Accumulates a GE purchase into the item's rolling buy-limit window, rolling the window over when it expires. */
	private void recordBuyLimit(int itemId, int qty)
	{
		long now = Instant.now().getEpochSecond();
		long[] window = geBuyLimits.get(itemId);
		if (window == null || now >= window[0] + BUY_LIMIT_WINDOW.getSeconds())
			geBuyLimits.put(itemId, new long[]{now, qty});
		else
			window[1] += qty;
	}

		/**
	 * Prices one item's net container delta. On a gain: restore trade/sell suspensions, then open
	 * positively-detected same-tick production — processing and decant outputs, which carry transferred
	 * cost basis — <em>before</em> draining the GE buy ledger, so a lingering buy for that same id can't
	 * steal a decant/processing output (#220); then the remaining GE and suspension routing, the
	 * source-attribution claim, and finally the classic fallback.
	 */
	private void applyDelta(TrackedItem tracked, int delta)
	{
		int itemId = tracked.getItemId();
		if (delta > 0)
		{
			int remaining = delta;
			remaining = consumeTradeUnsuspend(tracked, remaining);
			remaining = consumeSellUnsuspend(tracked, remaining);
			remaining = consumeProcessingOutput(tracked, remaining);
			remaining = consumeDecantOutput(tracked, remaining);
			remaining = consumeConsumedOutput(tracked, remaining);
			remaining = consumeEmptyContainerByproduct(tracked, remaining);
			remaining = consumeBuyLedger(tracked, remaining);
			remaining = consumeDeathUnsuspend(tracked, remaining);
			remaining = consumeGroundUnsuspend(tracked, remaining);
			remaining = consumePouchUnsuspend(tracked, remaining);
			if (remaining > 0 && isPouchDepositTick())
			{
				addOpenAcquisition(tracked, remaining, 0, AcquisitionSource.GATHER);
				remaining = 0;
			}

			if (remaining > 0)
			{
				SourceAttributionCore.Attribution a = attributeDelta(itemId, remaining);
				addOpenAcquisition(tracked, remaining, a.unitPriceOr(fallbackPrice(tracked)), a.source());
			}
		}
		else
		{
			int mag = consumePouchSuspend(tracked, -delta);
			mag = consumeTradeSuspend(tracked, mag);
			mag = consumeSellSuspend(tracked, mag);
			mag = consumeGroundSuspend(tracked, mag);
			mag = consumeDeathLoss(tracked, mag);
			if (mag > 0)
			{
				SourceAttributionCore.Attribution a = attributeDelta(itemId, mag);
				if (a.source() == AcquisitionSource.UNKNOWN && config.sourcePricing() && isConsumable(itemId))
				{
					closeFifo(tracked, mag, 0, AcquisitionSource.CONSUMED);
				}
				else
				{
					closeFifo(tracked, mag, a.unitPriceOr(tracked.getAvgPrice()), a.source());
				}
			}
		}
	}

	/**
	 * Suspends removals in the post-death window (#70): the units were lost to the
	 * death, so quantities drop but the lots stay open pending gravestone/Death's
	 * Office recovery. Consumption is bounded to the death's own container batch —
	 * the first tick that consumes, plus a one-tick grace for a split
	 * inventory/equipment sync — so ordinary removals later in the window (eating
	 * after respawning, dropping an item) close normally instead of being misbooked
	 * as 0-gp death losses. The suspension timestamp is only set when none exists,
	 * so a second death can't reset the first's recovery-expiry clock. Returns the
	 * unconsumed remainder (0 when consumed).
	 */
	private int consumeDeathLoss(TrackedItem tracked, int qty)
	{
		int tick = client.getTickCount();
		if (qty <= 0 || !config.sourcePricing() || deathTick < 0
				|| tick - deathTick > DEATH_LOSS_WINDOW_TICKS)
			return qty;

		if (deathLossTick >= 0 && tick - deathLossTick > DEATH_LOSS_BATCH_GRACE_TICKS)
		{
			deathTick = -1;
			return qty;
		}

		if (deathLossTick < 0)
			deathLossTick = tick;

		tracked.setDeathSuspendedQuantity(tracked.getDeathSuspendedQuantity() + qty);
		if (tracked.getDeathSuspendedAt() == null)
			tracked.setDeathSuspendedAt(Instant.now());

		return 0;
	}

	/**
	 * Greedily restores an addition from death suspension — a recovery reactivates
	 * the suspended lots with their basis intact, opening nothing new. Returns the
	 * unconsumed remainder.
	 */
	private int consumeDeathUnsuspend(TrackedItem tracked, int qty)
	{
		int suspended = tracked.getDeathSuspendedQuantity();
		if (qty <= 0 || suspended <= 0)
			return qty;

		int restore = Math.min(qty, suspended);
		tracked.setDeathSuspendedQuantity(suspended - restore);
		if (tracked.getDeathSuspendedQuantity() == 0)
			tracked.setDeathSuspendedAt(null);

		return qty - restore;
	}

	/** Closes death suspensions that outlived {@link #DEATH_SUSPEND_EXPIRY} as unrecovered losses at 0. */
	private void expireDeathSuspensions()
	{
		Instant cutoff = Instant.now().minus(DEATH_SUSPEND_EXPIRY);
		boolean changed = false;
		for (TrackedItem tracked : trackedItems.values())
		{
			if (tracked.getDeathSuspendedQuantity() > 0 && tracked.getDeathSuspendedAt() != null
					&& tracked.getDeathSuspendedAt().isBefore(cutoff))
			{
				closeFifo(tracked, tracked.getDeathSuspendedQuantity(), 0, AcquisitionSource.DEATH);
				tracked.setDeathSuspendedQuantity(0);
				tracked.setDeathSuspendedAt(null);
				changed = true;
			}
		}

		if (changed)
		{
			persistTrackedItems();
			refreshPanel();
		}
	}

	/**
	 * Tracks the local player's gravestone via {@link VarbitID#GRAVESTONE_VISIBLE} (non-zero
	 * while a grave stands, the value being its type). A grave that vanishes with its
	 * {@link VarbitID#GRAVESTONE_DURATION} timer run out has expired and its items are lost,
	 * so this arms the grace check in {@link #closeVanishedGraveLosses()}. A grave that
	 * vanishes with time still on the clock was collected — its returning items un-suspend
	 * themselves, so no loss is armed.
	 */
	private void onGravestoneVisibility(boolean present)
	{
		if (present)
		{
			graveSeen = true;
			graveGoneTick = -1;
		}
		else if (graveSeen)
		{
			graveSeen = false;
			if (client.getVarbitValue(VarbitID.GRAVESTONE_DURATION) <= 0)
				graveGoneTick = client.getTickCount();
		}
	}

	/**
	 * Once an expired gravestone has been gone for {@link #GRAVE_RECOVERY_GRACE_TICKS},
	 * closes any death suspension it left standing as lost at 0 (#70). The grace absorbs a
	 * last-tick collection whose items are still landing; anything still suspended after it
	 * is a genuine loss, so the collection log reflects it the moment the grave expires
	 * rather than after the blunt {@link #DEATH_SUSPEND_EXPIRY} fallback.
	 */
	private void closeVanishedGraveLosses()
	{
		if (graveGoneTick < 0 || client.getTickCount() - graveGoneTick < GRAVE_RECOVERY_GRACE_TICKS)
			return;

		graveGoneTick = -1;
		boolean changed = false;
		for (TrackedItem tracked : trackedItems.values())
		{
			if (tracked.getDeathSuspendedQuantity() <= 0)
				continue;

			closeFifo(tracked, tracked.getDeathSuspendedQuantity(), 0, AcquisitionSource.DEATH);
			tracked.setDeathSuspendedQuantity(0);
			tracked.setDeathSuspendedAt(null);
			changed = true;
		}

		if (changed)
		{
			persistTrackedItems();
			refreshPanel();
		}
	}

	/**
	 * Moves up to this tick's correlated drop quantity of a removal into ground
	 * suspension — the units left the containers but sit on the floor, still owned,
	 * lots untouched. Returns the unconsumed remainder.
	 */
	private int consumeGroundSuspend(TrackedItem tracked, int qty)
	{
		Integer pending = pendingGroundSuspend.get(tracked.getItemId());
		if (qty <= 0 || pending == null || pending <= 0)
			return qty;

		int take = Math.min(qty, pending);
		int left = pending - take;
		if (left > 0)
			pendingGroundSuspend.put(tracked.getItemId(), left);
		else
			pendingGroundSuspend.remove(tracked.getItemId());

		tracked.setGroundSuspendedQuantity(tracked.getGroundSuspendedQuantity() + take);
		tracked.setGroundSuspendedAt(Instant.now());
		return qty - take;
	}

	/**
	 * Restores an addition from ground suspension, but only up to what an actual
	 * re-pickup of one of our dropped {@code TileItem}s queued — so a same-item pickup
	 * from an unrelated source (a monster drop while our own is on the floor) can't
	 * cancel the suspension and instead gets its own 0-cost ground lot. A re-pickup of
	 * our drop is the net no-op that opens no new lot. Returns the unconsumed remainder.
	 */
	private int consumeGroundUnsuspend(TrackedItem tracked, int qty)
	{
		Integer pending = pendingGroundUnsuspend.get(tracked.getItemId());
		int suspended = tracked.getGroundSuspendedQuantity();
		if (qty <= 0 || pending == null || pending <= 0 || suspended <= 0)
			return qty;

		int restore = Math.min(qty, Math.min(pending, suspended));
		int left = pending - restore;
		if (left > 0)
			pendingGroundUnsuspend.put(tracked.getItemId(), left);
		else
			pendingGroundUnsuspend.remove(tracked.getItemId());

		tracked.setGroundSuspendedQuantity(suspended - restore);
		return qty - restore;
	}

	/**
	 * Moves a removal into fur/meat-pouch suspension when it lands on the tick the pouch was
	 * "Fill"ed — the units left the inventory into the pouch but stay owned, lots (and their
	 * original source/basis) intact, until the pouch is emptied. Consumes the whole removal,
	 * since a Fill click's only container effect is the furs/meats leaving the inventory.
	 * Returns the unconsumed remainder (0 while the fill tick is live) (#214).
	 */
	private int consumePouchSuspend(TrackedItem tracked, int qty)
	{
		if (qty <= 0 || !config.sourcePricing() || pouchFillTick < 0
				|| client.getTickCount() - pouchFillTick > 1)
			return qty;

		tracked.setPouchSuspendedQuantity(tracked.getPouchSuspendedQuantity() + qty);
		return 0;
	}

	/**
	 * Restores an addition from fur/meat-pouch suspension on an empty-to-bank tick, up to what
	 * was filled in — those units re-enter tracked containers as the net no-op that reopens no
	 * lot, keeping their original source and basis. Any surplus beyond the suspended amount is
	 * left for the caller to book as freshly-gathered {@code GATHER}. Returns the unconsumed
	 * remainder (#214).
	 */
	private int consumePouchUnsuspend(TrackedItem tracked, int qty)
	{
		int suspended = tracked.getPouchSuspendedQuantity();
		if (qty <= 0 || suspended <= 0 || !isPouchDepositTick())
			return qty;

		int restore = Math.min(qty, suspended);
		tracked.setPouchSuspendedQuantity(suspended - restore);
		return qty - restore;
	}

	/** @return whether a fur/meat pouch was emptied to the bank on (or one tick before) this tick (#214). */
	private boolean isPouchDepositTick()
	{
		return config.sourcePricing() && pouchDepositTick >= 0 && client.getTickCount() - pouchDepositTick <= 1;
	}

	/**
	 * Moves up to {@code qty} of a removal into trade suspension — the units were placed into a
	 * player-trade offer, so they left the containers but stay owned with their lots intact until
	 * the trade finalizes or is withdrawn. Returns the unconsumed remainder.
	 */
	private int consumeTradeSuspend(TrackedItem tracked, int qty)
	{
		Integer pending = pendingTradeSuspend.get(tracked.getItemId());
		if (qty <= 0 || pending == null || pending <= 0)
			return qty;

		int take = Math.min(qty, pending);
		int left = pending - take;
		if (left > 0)
			pendingTradeSuspend.put(tracked.getItemId(), left);
		else
			pendingTradeSuspend.remove(tracked.getItemId());

		tracked.setTradeSuspendedQuantity(tracked.getTradeSuspendedQuantity() + take);
		return qty - take;
	}

	/**
	 * Restores an addition from trade suspension — an offered item withdrawn from the trade
	 * returns to the inventory, a net no-op that opens no new lot. Bounded by both the queued
	 * withdrawal and the units actually suspended. Returns the unconsumed remainder.
	 */
	private int consumeTradeUnsuspend(TrackedItem tracked, int qty)
	{
		Integer pending = pendingTradeUnsuspend.get(tracked.getItemId());
		int suspended = tracked.getTradeSuspendedQuantity();
		if (qty <= 0 || pending == null || pending <= 0 || suspended <= 0)
			return qty;

		int restore = Math.min(qty, Math.min(pending, suspended));
		int left = pending - restore;
		if (left > 0)
			pendingTradeUnsuspend.put(tracked.getItemId(), left);
		else
			pendingTradeUnsuspend.remove(tracked.getItemId());

		tracked.setTradeSuspendedQuantity(suspended - restore);
		return qty - restore;
	}

	/**
	 * Opens the output lot(s) of a processing action (#69), carrying the transferred
	 * input basis so their cost sums <em>exactly</em> to it.
	 */
	private int consumeProcessingOutput(TrackedItem tracked, int qty)
	{
		return consumeCarriedOutput(tracked, qty, pendingProcessingOutput, AcquisitionSource.PROCESSING);
	}

	/**
	 * Opens the output lot(s) of a decant (#220), carrying the combined dose-weighted input
	 * basis so the swapped potion keeps its cost — no profit is realized on the swap.
	 */
	private int consumeDecantOutput(TrackedItem tracked, int qty)
	{
		return consumeCarriedOutput(tracked, qty, pendingDecantOutput, AcquisitionSource.DECANT);
	}

	/**
	 * Opens the lower-dose potion left after a dose is drunk (#218), carrying the full basis of the
	 * higher-dose lot so using a dose realizes no profit or loss — the cost simply follows the liquid.
	 */
	private int consumeConsumedOutput(TrackedItem tracked, int qty)
	{
		return consumeCarriedOutput(tracked, qty, pendingConsumedOutput, AcquisitionSource.CONSUMED);
	}

	/**
	 * Opens the empty vessel(s) freed by finishing a potion or drink this tick (#218) at 0 under
	 * {@link AcquisitionSource#CONSUMED} — a leftover byproduct, not a purchase. Bounded to the number
	 * of vessels emptied, so any vials bought separately still price normally. Returns the remainder.
	 */
	private int consumeEmptyContainerByproduct(TrackedItem tracked, int qty)
	{
		if (qty <= 0 || potionEmptiedCount <= 0 || client.getTickCount() != potionEmptiedTick
				|| !EMPTY_CONTAINERS.contains(tracked.getItemId()))
			return qty;

		int free = Math.min(qty, potionEmptiedCount);
		potionEmptiedCount -= free;
		addOpenAcquisition(tracked, free, 0, AcquisitionSource.CONSUMED);
		return qty - free;
	}

	/**
	 * Opens {@code qty} newly-produced units carrying the basis queued in {@code carried} for this
	 * item, tagged with {@code source}. An uneven split gives the remainder units one extra gp each
	 * — 13 gp across 60 units becomes 13 units at 1 gp plus 47 at 0 gp — since a single integer
	 * per-unit price can't hold a sub-gp basis. Consumes the whole addition (returns 0) so it
	 * bypasses the fallback auto-add.
	 */
	private int consumeCarriedOutput(TrackedItem tracked, int qty, Map<Integer, Long> carried, AcquisitionSource source)
	{
		Long totalCost = carried.remove(tracked.getItemId());
		if (qty <= 0 || totalCost == null)
			return qty;

		long base = totalCost / qty;
		int remainder = (int) (totalCost % qty);
		if (remainder > 0)
			addOpenAcquisition(tracked, remainder, base + 1, source);

		addOpenAcquisition(tracked, qty - remainder, base, source);
		return 0;
	}

	/** Restores up to {@code qty} cancelled-sell units to held (un-suspends), returning the unconsumed remainder. */
	private int consumeSellUnsuspend(TrackedItem tracked, int qty)
	{
		Integer pending = pendingSellUnsuspend.get(tracked.getItemId());
		if (pending == null || pending <= 0)
			return qty;

		int take = Math.min(qty, Math.min(pending, tracked.getSuspendedQuantity()));
		if (take <= 0)
			return qty;

		tracked.setSuspendedQuantity(tracked.getSuspendedQuantity() - take);
		int left = pending - take;
		if (left > 0)
			pendingSellUnsuspend.put(tracked.getItemId(), left);
		else
			pendingSellUnsuspend.remove(tracked.getItemId());

		return qty - take;
	}

	/**
	 * Consumes up to {@code qty} from the item's GE buy ledger into priced lots, returning
	 * the unconsumed remainder.
	 */
	private int consumeBuyLedger(TrackedItem tracked, int qty)
	{
		Deque<long[]> ledger = pendingGeBuys.get(tracked.getItemId());
		if (ledger == null || ledger.isEmpty())
			return qty;

		int remaining = qty;
		while (remaining > 0 && !ledger.isEmpty())
		{
			long[] chunk = ledger.peekFirst();
			int take = (int) Math.min(remaining, chunk[0]);
			addOpenAcquisition(tracked, take, chunk[1], AcquisitionSource.GE_TRADE);
			remaining -= take;
			chunk[0] -= take;
			if (chunk[0] <= 0)
				ledger.removeFirst();
		}

		if (ledger.isEmpty())
			pendingGeBuys.remove(tracked.getItemId());

		scheduleGeStateSave();
		return remaining;
	}

	/** Suspends up to {@code qty} units for a just-placed GE sell (no close), returning the unconsumed remainder. */
	private int consumeSellSuspend(TrackedItem tracked, int qty)
	{
		Integer pending = pendingSellSuspend.get(tracked.getItemId());
		if (pending == null || pending <= 0)
			return qty;

		int take = Math.min(qty, pending);
		tracked.setSuspendedQuantity(tracked.getSuspendedQuantity() + take);
		int left = pending - take;
		if (left > 0)
			pendingSellSuspend.put(tracked.getItemId(), left);
		else
			pendingSellSuspend.remove(tracked.getItemId());

		scheduleGeStateSave();
		return qty - take;
	}

	/**
	 * Post-login GE reconciliation, run for each offer event inside the {@link #GE_LOGIN_SYNC_TICKS}
	 * login window (when the offers array is finally populated, unlike at container sync). Seeds the
	 * offer tracker's baselines from the live offers so an offer that already existed at login is not
	 * replayed as a fresh placement or fill, drops the stale session sell-routing maps, and rebuilds
	 * {@code suspendedQuantity} from those offers so a later cancel un-suspends correctly instead of
	 * logging a phantom acquisition. Idempotent, so repeating it as the array fills in is safe.
	 */
	private void primeGeStateFromLogin()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers != null)
		{
			for (int slot = 0; slot < offers.length; slot++)
			{
				GrandExchangeOffer offer = offers[slot];
				if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
					continue;

				geOfferTracker.seed(slot, offer.getItemId(), offer.getQuantitySold(), offer.getSpent());
			}
		}

		pendingSellSuspend.clear();
		pendingSellUnsuspend.clear();
		pendingSellRealize.clear();
		seedCancelledSellReturns(offers);
		reconcileSuspendedFromOffers();
	}

	/**
	 * Queues the uncollected remainder of every cancelled sell offer as a pending un-suspend,
	 * so those units stay suspended (they are still the player's, sitting in the collection
	 * box) and collecting them restores the original lots instead of opening fresh ones.
	 * Runs after the login prime clears the pending maps, so re-priming stays idempotent.
	 */
	private void seedCancelledSellReturns(GrandExchangeOffer[] offers)
	{
		if (offers == null)
			return;

		for (GrandExchangeOffer offer : offers)
		{
			if (offer == null || offer.getState() != GrandExchangeOfferState.CANCELLED_SELL)
				continue;

			int returned = offer.getTotalQuantity() - offer.getQuantitySold();
			if (returned > 0)
				pendingSellUnsuspend.merge(offer.getItemId(), returned, Integer::sum);
		}
	}

	/**
	 * Rewrites {@code suspendedQuantity} from the live open sell offers plus the pending
	 * cancelled-sell returns (units cancelled but not yet collected, which are still the
	 * player's), so offline fills or cancels self-heal at login; released units are then
	 * re-priced by {@link #reconcileAllQuantities}.
	 *
	 * <p>With Source-Based Pricing off no offer suspends: placements made while off were
	 * already closed classically (re-suspending them would double-count), and any leftover
	 * suspension from while the toggle was on zeroes here, letting the reconcile close those
	 * lots at the average price — the classic removal semantics the toggle promises.
	 */
	private void reconcileSuspendedFromOffers()
	{
		if (!config.sourcePricing())
		{
			for (TrackedItem tracked : trackedItems.values())
				tracked.setSuspendedQuantity(0);

			return;
		}

		Map<Integer, Integer> openSell = new HashMap<>();
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers != null)
		{
			for (GrandExchangeOffer offer : offers)
			{
				if (offer != null && offer.getState() == GrandExchangeOfferState.SELLING)
					openSell.merge(offer.getItemId(), offer.getTotalQuantity() - offer.getQuantitySold(), Integer::sum);
			}
		}

		for (TrackedItem tracked : trackedItems.values())
			tracked.setSuspendedQuantity(openSell.getOrDefault(tracked.getItemId(), 0)
					+ pendingSellUnsuspend.getOrDefault(tracked.getItemId(), 0));
	}

	/** Sets the item's transient buy-limit fields from its window, clearing them when the window has expired. */
	private void applyBuyLimitFields(TrackedItem item)
	{
		long[] window = geBuyLimits.get(item.getItemId());
		if (window == null || Instant.now().getEpochSecond() >= window[0] + BUY_LIMIT_WINDOW.getSeconds())
		{
			item.setLimitBought(0);
			item.setLimitResetEpoch(0);
			return;
		}

		item.setLimitBought((int) window[1]);
		item.setLimitResetEpoch(window[0] + BUY_LIMIT_WINDOW.getSeconds());
	}

	/** Persists the GE buy ledger and buy-limit windows to the RS profile config. */
	private void persistGeState()
	{
		Map<Integer, List<long[]>> ledger = new HashMap<>();
		for (Map.Entry<Integer, Deque<long[]>> e : pendingGeBuys.entrySet())
			ledger.put(e.getKey(), new ArrayList<>(e.getValue()));

		lastGeStateSave = Instant.now();
		configManager.setRSProfileConfiguration(PricewatchConfig.GROUP, PricewatchConfig.KEY_GE_BUY_LEDGER,
				gson.toJson(ledger, GE_LEDGER_TYPE));
		configManager.setRSProfileConfiguration(PricewatchConfig.GROUP, PricewatchConfig.KEY_GE_BUY_LIMITS,
				gson.toJson(geBuyLimits, GE_LIMITS_TYPE));
	}

	/** Restores the GE buy ledger and buy-limit windows from the RS profile config, defaulting to empty. */
	private void loadGeState()
	{
		pendingGeBuys.clear();
		geBuyLimits.clear();

		String ledgerJson = configManager.getRSProfileConfiguration(
				PricewatchConfig.GROUP, PricewatchConfig.KEY_GE_BUY_LEDGER);
		if (ledgerJson != null && !ledgerJson.trim().isEmpty())
		{
			try
			{
				Map<Integer, List<long[]>> ledger = gson.fromJson(ledgerJson, GE_LEDGER_TYPE);
				if (ledger != null)
				{
					for (Map.Entry<Integer, List<long[]>> e : ledger.entrySet())
						pendingGeBuys.put(e.getKey(), new ArrayDeque<>(e.getValue()));
				}
			}
			catch (JsonSyntaxException ex)
			{
				log.warn("Failed to parse GE buy ledger; ignoring", ex);
			}
		}

		String limitsJson = configManager.getRSProfileConfiguration(
				PricewatchConfig.GROUP, PricewatchConfig.KEY_GE_BUY_LIMITS);
		if (limitsJson != null && !limitsJson.trim().isEmpty())
		{
			try
			{
				Map<Integer, long[]> limits = gson.fromJson(limitsJson, GE_LIMITS_TYPE);
				if (limits != null)
					geBuyLimits.putAll(limits);
			}
			catch (JsonSyntaxException ex)
			{
				log.warn("Failed to parse GE buy limits; ignoring", ex);
			}
		}
	}

	/** Persists the GE state at most once per {@link #GE_STATE_SAVE_INTERVAL}. */
	private void scheduleGeStateSave()
	{
		if (lastGeStateSave == null
				|| Duration.between(lastGeStateSave, Instant.now()).compareTo(GE_STATE_SAVE_INTERVAL) >= 0)
			persistGeState();
	}

	/**
	 * Attributes a quantity change against the open detector claims, honouring the
	 * Source-Based Pricing kill switch: when disabled, everything is
	 * {@link AcquisitionSource#UNKNOWN} and priced by the classic fallbacks.
	 */
	private SourceAttributionCore.Attribution attributeDelta(int itemId, int quantity)
	{
		if (!config.sourcePricing())
			return SourceAttributionCore.Attribution.UNKNOWN;

		return sourceAttribution.attribute(itemId, quantity, client.getTickCount());
	}

	/**
	 * @return the cost-basis price to seed an unknown-source change with (an auto-add or any
	 * delta no detector observed), per the configured {@link FallbackPricing}.
	 */
	private long fallbackPrice(TrackedItem tracked)
	{
		switch (config.fallbackPricing())
		{
			case HIGH:
				return tracked.getHighPrice();
			case LOW:
				return tracked.getLowPrice();
			case ZERO:
				return 0;
			case AVG:
			default:
				return tracked.getAvgPrice();
		}
	}

	/**
	 * Adds {@code qty} units to an item's held lots at {@code boughtAt} gp.
	 *
	 * <p>First it reverses any equal-and-opposite "wash" closes (a prior sell at
	 * the same price, which a re-acquire should cancel), then merges into an
	 * existing open lot at the same price, or appends a new lot.
	 */
	private void addOpenAcquisition(TrackedItem tracked, int qty, long boughtAt, AcquisitionSource source)
	{
		if (qty <= 0)
			return;

		List<AcquisitionRecord> records = tracked.getAcquisitions();

		int undoBudget = qty;
		Iterator<AcquisitionRecord> it = records.iterator();
		while (it.hasNext() && undoBudget > 0)
		{
			AcquisitionRecord r = it.next();
			Long sold = r.getSoldAt();
			if (sold != null && r.getBoughtAt() == boughtAt && sold == boughtAt)
			{
				int undo = Math.min(r.getQuantity(), undoBudget);
				r.setQuantity(r.getQuantity() - undo);
				if (r.getQuantity() == 0)
					it.remove();

				undoBudget -= undo;
			}
		}

		for (AcquisitionRecord r : records)
		{
			if (r.getSoldAt() == null && r.getBoughtAt() == boughtAt && r.sourceOrUnknown() == source)
			{
				r.setQuantity(r.getQuantity() + qty);
				return;
			}
		}

		records.add(new AcquisitionRecord(qty, boughtAt, null, source));
	}

	/**
	 * Merges {@code qty} into an existing closed (sold) lot with the same
	 * bought/sold prices and sell provenance, to avoid fragmenting the log.
	 *
	 * @return {@code true} if a matching lot absorbed the quantity
	 */
	private boolean mergeClosed(List<AcquisitionRecord> records, int qty, long boughtAt, long soldAtPrice,
			AcquisitionSource sellSource)
	{
		for (AcquisitionRecord r : records)
		{
			Long sold = r.getSoldAt();
			if (sold != null && r.getBoughtAt() == boughtAt && sold == soldAtPrice
					&& r.sellSourceOrUnknown() == sellSource)
			{
				r.setQuantity(r.getQuantity() + qty);
				return true;
			}
		}

		return false;
	}

	/**
	 * Closes {@code amount} units of held inventory at {@code soldAtPrice},
	 * oldest lot first (FIFO), recording {@code sellSource} as the sale's
	 * provenance — {@link AcquisitionSource#UNKNOWN} marks the price as an
	 * estimate rather than an observed sale.
	 *
	 * <p>It first cancels any just-added open lots bought at the same price (a
	 * buy immediately followed by a sell nets out), then realizes the remaining
	 * amount across the oldest open lots, splitting a lot when only part of it is
	 * sold and merging into matching closed lots where possible.
	 */
	private void closeFifo(TrackedItem tracked, int amount, long soldAtPrice, AcquisitionSource sellSource)
	{
		List<AcquisitionRecord> records = tracked.getAcquisitions();
		int remaining = amount;

		Iterator<AcquisitionRecord> cancelIt = records.iterator();
		while (cancelIt.hasNext() && remaining > 0)
		{
			AcquisitionRecord r = cancelIt.next();
			if (r.getSoldAt() == null && r.getBoughtAt() == soldAtPrice)
			{
				int cancel = Math.min(r.getQuantity(), remaining);
				r.setQuantity(r.getQuantity() - cancel);
				if (r.getQuantity() == 0)
					cancelIt.remove();

				remaining -= cancel;
			}
		}

		remaining = realizeOpenLots(records, remaining, soldAtPrice, sellSource, sellSource);
		realizeOpenLots(records, remaining, soldAtPrice, sellSource, null);
	}

	/**
	 * Realizes up to {@code remaining} units across the open lots oldest-first,
	 * closing (or splitting) each at {@code soldAtPrice} with {@code sellSource} and
	 * merging into a matching closed lot where possible. When {@code onlySource} is
	 * non-null, only lots that entered from that source are eligible — so a sell
	 * closes its own source's buys before any others (#137), with the caller running
	 * a matched pass followed by an unrestricted one.
	 *
	 * @return the units still unrealized after this pass
	 */
	private int realizeOpenLots(List<AcquisitionRecord> records, int remaining, long soldAtPrice,
			AcquisitionSource sellSource, AcquisitionSource onlySource)
	{
		int i = 0;
		while (i < records.size() && remaining > 0)
		{
			AcquisitionRecord r = records.get(i);
			if (r.getSoldAt() != null || (onlySource != null && r.sourceOrUnknown() != onlySource))
			{
				i++;
				continue;
			}

			if (r.getQuantity() <= remaining)
			{
				int closeQty = r.getQuantity();
				remaining -= closeQty;
				if (mergeClosed(records, closeQty, r.getBoughtAt(), soldAtPrice, sellSource))
				{
					records.remove(i);
				}
				else
				{
					r.setSoldAt(soldAtPrice);
					r.setSellSource(sellSource);
					i++;
				}
			}
			else
			{
				int closeQty = remaining;
				r.setQuantity(r.getQuantity() - closeQty);
				remaining = 0;
				if (!mergeClosed(records, closeQty, r.getBoughtAt(), soldAtPrice, sellSource))
				{
					AcquisitionRecord closed = new AcquisitionRecord(closeQty, r.getBoughtAt(), soldAtPrice,
							r.getSource());
					closed.setSellSource(sellSource);
					records.add(i, closed);
				}
			}
		}

		return remaining;
	}

	/**
	 * Applies the accumulated per-item container deltas to tracked items: positive
	 * deltas open new lots (auto-add), negative deltas close lots FIFO, and each
	 * item's quantity is adjusted. No-op when auto-add is off. Persists/refreshes
	 * if anything changed.
	 */
	private void syncQuantitiesFromContainers()
	{
		if (pendingItemDeltas.isEmpty())
			return;

		Map<Integer, Integer> deltas = new HashMap<>(pendingItemDeltas);
		pendingItemDeltas.clear();
		if (!config.autoAddItems() || trackedItems.isEmpty())
			return;

		boolean changed = false;
		for (TrackedItem tracked : trackedItems.values())
		{
			if (tracked.getMode() != TrackItemMode.TRACK)
				continue;

			Integer delta = deltas.get(tracked.getItemId());
			if (delta == null || delta == 0)
				continue;

			applyDelta(tracked, delta);

			tracked.setQuantity(tracked.getQuantity() + delta);
			changed = true;
		}

		if (changed)
		{
			persistTrackedItems();
			refreshPanel();
		}
	}

	/**
	 * Recounts every tracked item from scratch across all containers plus the rune
	 * pouch, and reconciles each item's lots to match the true on-hand total
	 * (opening or closing lots as needed). Used to catch up after login when full
	 * container state first becomes available.
	 */
	private void reconcileAllQuantities()
	{
		pendingItemDeltas.clear();
		if (client.getGameState() != GameState.LOGGED_IN || trackedItems.isEmpty())
			return;

		for (int containerId : TRACKED_CONTAINERS)
		{
			ItemContainer container = client.getItemContainer(containerId);
			if (container == null)
				continue;

			Map<Integer, Integer> counts = containerCounts.computeIfAbsent(containerId, k -> new HashMap<>());
			counts.clear();
			for (Item item : container.getItems())
			{
				if (item.getId() > 0 && !isPlaceholder(item.getId()))
					counts.merge(itemManager.canonicalize(item.getId()), item.getQuantity(), Integer::sum);
			}
		}

		syncRunePouch();
		reconcileSuspendedFromOffers();

		boolean changed = false;
		for (TrackedItem tracked : trackedItems.values())
		{
			if (tracked.getMode() != TrackItemMode.TRACK)
				continue;

			int total = runePouchCounts.getOrDefault(tracked.getItemId(), 0)
					+ containerCounts.values().stream()
					.mapToInt(c -> c.getOrDefault(tracked.getItemId(), 0))
					.sum();
			int owned = total + tracked.getTotalSuspendedQuantity();
			int logDelta = owned - tracked.getRecordQuantitySum();
			if (logDelta > 0)
			{
				addOpenAcquisition(tracked, logDelta, fallbackPrice(tracked), AcquisitionSource.UNKNOWN);
				changed = true;
			}
			else if (logDelta < 0)
			{
				closeFifo(tracked, -logDelta, tracked.getAvgPrice(), AcquisitionSource.UNKNOWN);
				changed = true;
			}

			if (tracked.getQuantity() != total)
			{
				tracked.setQuantity(total);
				changed = true;
			}
		}

		if (changed)
			persistTrackedItems();
	}

	/** Recounts a single item across all containers and the rune pouch and sets its quantity. */
	private void syncQuantitiesForItem(TrackedItem tracked)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
			return;

		for (int containerId : TRACKED_CONTAINERS)
		{
			ItemContainer container = client.getItemContainer(containerId);
			if (container == null)
				continue;

			Map<Integer, Integer> counts = containerCounts.computeIfAbsent(containerId, k -> new HashMap<>());
			counts.clear();
			for (Item item : container.getItems())
			{
				if (item.getId() > 0 && !isPlaceholder(item.getId()))
					counts.merge(itemManager.canonicalize(item.getId()), item.getQuantity(), Integer::sum);
			}
		}

		syncRunePouch();

		int total = runePouchCounts.getOrDefault(tracked.getItemId(), 0)
				+ containerCounts.values().stream()
				.mapToInt(c -> c.getOrDefault(tracked.getItemId(), 0))
				.sum();
		tracked.setQuantity(total);
	}

	/** @return whether the given (canonical) item id is currently tracked. */
	boolean isTracked(int itemId)
	{
		return trackedItems.containsKey(itemId);
	}

	/**
	 * @return whether {@code itemId} is a single-use consumable (food, a potion dose) by
	 *         {@link ItemCategoryClassifier} category — an unclaimed removal of one is booked as
	 *         used up at 0 rather than an avg-price Unknown sale (#218). Ammo and runes are
	 *         excluded; see {@link #CONSUMABLE_CATEGORIES}. Client thread only.
	 */
	private boolean isConsumable(int itemId)
	{
		String category = ItemCategoryClassifier.classify(itemManager.getItemComposition(itemId).getName());
		return CONSUMABLE_CATEGORIES.contains(category);
	}

	/**
	 * @return whether {@code itemId} is a bank placeholder variant, which
	 *         {@code canonicalize} would map to the real item — placeholders must
	 *         never count as held quantity. Client thread only.
	 */
	private boolean isPlaceholder(int itemId)
	{
		return itemManager.getItemComposition(itemId).getPlaceholderTemplateId() != -1;
	}

	/**
	 * Callback after the user edits an item's acquisitions: re-derives its held quantity
	 * from the lots and persists. Open lots also cover suspended units (in-flight GE
	 * sells, trades, drops, deaths), which {@code quantity} must exclude — otherwise an
	 * edit made mid-suspension would double-count the suspended units as held.
	 */
	void onAcquisitionsEdited(int itemId)
	{
		clientThread.invokeLater(() ->
		{
			TrackedItem tracked = trackedItems.get(itemId);
			if (tracked == null)
				return;

			tracked.setCostBasisInitialized(true);
			tracked.setQuantity(Math.max(0, tracked.getRecordQuantitySum() - tracked.getTotalSuspendedQuantity()));
			persistTrackedItems();
			refreshPanel();
		});
	}

	/** Callback after the user edits an item's notification rules: just persists the change. */
	private void onNotificationsEdited(int itemId)
	{
		clientThread.invokeLater(() ->
		{
			if (trackedItems.containsKey(itemId))
				persistTrackedItems();
		});
	}

	/**
	 * Maximum plausible Δ% for a notification: changes beyond this magnitude
	 * indicate a sparse/stale window average (a near-zero denominator) rather than
	 * a real move, and are ignored so a one-shot rule isn't fired on noise.
	 */
	private static final double MAX_DELTA_PCT = 1000.0;

	private static final long GLOW_PERIOD_SLOW_MS = 2000;
	private static final long GLOW_PERIOD_MEDIUM_MS = 1500;
	private static final long GLOW_PERIOD_FAST_MS = 1000;
	private static final float GLOW_MIN_ALPHA = 0.2f;
	private static final float GLOW_MAX_ALPHA = 1f;

	/** @return the current highlight alpha, a sine "breathing" pulse whose period depends on the glow speed config. */
	float breathingAlpha()
	{
		long period;
		switch (config.glowEffect())
		{
			case SLOW:
				period = GLOW_PERIOD_SLOW_MS;
				break;
			case MEDIUM:
				period = GLOW_PERIOD_MEDIUM_MS;
				break;
			case FAST:
				period = GLOW_PERIOD_FAST_MS;
				break;
			default:
				return GLOW_MAX_ALPHA;
		}

		double phase = (System.currentTimeMillis() % period) / (double) period;
		double wave = (Math.sin(phase * 2 * Math.PI) + 1) / 2;
		return GLOW_MIN_ALPHA + (GLOW_MAX_ALPHA - GLOW_MIN_ALPHA) * (float) wave;
	}

	/** @return the live map of on-screen ground items to their tiles (used by the ground overlay). */
	Map<TileItem, Tile> getGroundItems()
	{
		return groundItems;
	}

	/** Refreshes the panel without flagging a price update (no change indicators). */
	private void refreshPanel()
	{
		refreshPanel(false);
	}

	/**
	 * Pushes the current tracked items and totals to the panel on the Swing thread.
	 *
	 * <p>Rebuilds are coalesced: the snapshot is published to {@link #pendingRebuild}
	 * (last writer wins) and a drainer is enqueued only when none is pending, so no
	 * matter how fast game events arrive, at most one rebuild sits in the EDT queue
	 * and only the newest snapshot is rendered. Without this, per-tick events queue
	 * full rebuilds faster than one completes and the panel's {@code removeAll} —
	 * which scans the pending queue per removed child — live-locks the EDT (#120).
	 *
	 * @param pricesUpdated whether this refresh follows a price change, enabling
	 *                      the per-row change indicators
	 */
	private void refreshPanel(boolean pricesUpdated)
	{
		final Instant refresh = lastPriceRefresh;
		final PriceIndicatorMode indicatorMode = pricesUpdated
				? config.priceChangeIndicator()
				: PriceIndicatorMode.OFF;
		for (TrackedItem item : trackedItems.values())
			applyBuyLimitFields(item);

		final List<TrackedItem> items = new ArrayList<>(trackedItems.values());

		final GameState gs = client.getGameState();
		final boolean loggedIn = gs == GameState.LOGGED_IN || gs == GameState.LOADING;
		final List<CategoryState> categorySnapshot = new ArrayList<>(categories);
		final boolean favCollapsed = favoritesCollapsed;
		final boolean uncatCollapsed = uncategorizedCollapsed;
		Runnable rebuild = () -> panel.rebuild(items, refresh, indicatorMode, loggedIn,
				categorySnapshot, favCollapsed, uncatCollapsed);
		if (pendingRebuild.getAndSet(rebuild) != null)
			return;

		SwingUtilities.invokeLater(() ->
		{
			Runnable newest = pendingRebuild.getAndSet(null);
			if (newest != null)
				newest.run();
		});
	}

	/**
	 * Evaluates every item's notification rules and fires the configured notifier
	 * for any that are met. A once rule is removed after firing; a repeat rule stays
	 * and re-arms edge-triggered — it fires again only after its condition has gone
	 * false and come back true, and the first evaluation after a (re)load primes it
	 * without firing. Skipped when notifications are disabled or being edited.
	 */
	private void evaluateNotifications()
	{
		Notification style = config.notificationStyle();
		if (!style.isEnabled())
			return;

		if (panel.isEditingNotifications())
			return;

		boolean changed = false;
		for (TrackedItem item : trackedItems.values())
		{
			Iterator<NotificationRule> it = item.getNotifications().iterator();
			while (it.hasNext())
			{
				NotificationRule rule = it.next();
				Boolean condition = evaluateRule(item, rule);
				if (condition == null)
					continue;

				if (rule.isRepeat())
				{
					boolean fire = condition && Boolean.FALSE.equals(rule.getLastCondition());
					rule.setLastCondition(condition);
					if (fire)
						notifier.notify(style, notificationText(item, rule));

					continue;
				}

				if (condition)
				{
					notifier.notify(style, notificationText(item, rule));
					it.remove();
					changed = true;
				}
			}
		}

		if (changed)
		{
			persistTrackedItems();
			refreshPanel();
		}
	}

	/**
	 * Evaluates a single rule against an item.
	 *
	 * @return {@code TRUE}/{@code FALSE} for the condition, or {@code null} when it
	 *         can't be evaluated yet (incomplete rule or missing/unparseable data)
	 */
	private Boolean evaluateRule(TrackedItem item, NotificationRule rule)
	{
		NotificationMetric metric = rule.getMetric();
		if (metric == null || rule.getOperation() == null)
			return null;

		if (metric.isCategorical())
		{
			String current = categoryValue(item, metric);
			if (current == null || rule.getValue() == null)
				return null;

			return current.equalsIgnoreCase(rule.getValue().trim());
		}

		TimeWindow window = metric.locksTimeframeToMonth() ? TimeWindow.MONTH : rule.getTimeWindow();
		OptionalDouble current = numericValue(item, metric, window);
		if (!current.isPresent())
			return null;

		OptionalDouble target = metric.getKind() == NotificationMetric.Kind.PERCENT
				? NotificationRule.parsePercent(rule.getValue())
				: NotificationRule.parseNumeric(rule.getValue());
		if (!target.isPresent())
			return null;

		return rule.getOperation().test(current.getAsDouble(), target.getAsDouble());
	}

	/**
	 * Resolves the current numeric reading of a metric for an item over a window
	 * (price, volume, profit, HA profit, Δ% vs. the window average, or quantity).
	 *
	 * @return the value, or empty when the underlying data is missing or unreliable
	 */
	private OptionalDouble numericValue(TrackedItem item, NotificationMetric metric, TimeWindow window)
	{
		if (metric == NotificationMetric.QUANTITY)
			return OptionalDouble.of(item.getQuantity());

		PriceStats s = item.getWindowStats().get(window);
		long avg = s == null ? 0 : s.getAvg();
		switch (metric)
		{
			case HIGH:
				return s == null ? OptionalDouble.empty() : OptionalDouble.of(s.getHigh());
			case LOW:
				return s == null ? OptionalDouble.empty() : OptionalDouble.of(s.getLow());
			case AVERAGE:
				return s == null ? OptionalDouble.empty() : OptionalDouble.of(s.getAvg());
			case VOLUME:
				return s == null ? OptionalDouble.empty() : OptionalDouble.of(s.getVolume());
			case ITM_PROFIT:
				return avg <= 0 ? OptionalDouble.empty() : OptionalDouble.of(item.getProfitAt(avg));
			case HA_PROFIT:
				if (avg <= 0 || item.getHighAlch() <= 0)
					return OptionalDouble.empty();

				return OptionalDouble.of(item.getHighAlch() - avg - runePrice(NATURE_RUNE_ID)
						- 5 * runePrice(FIRE_RUNE_ID));
			case DELTA_PCT:
			{
				long current = item.getAvgPrice();
				if (current <= 0 || avg <= 0)
					return OptionalDouble.empty();

				double pct = Math.round(((double) (current - avg) / avg) * 1000.0) / 10.0;
				return Math.abs(pct) > MAX_DELTA_PCT ? OptionalDouble.empty() : OptionalDouble.of(pct);
			}
			default:
				return OptionalDouble.empty();
		}
	}

	/**
	 * Resolves the current categorical rating of a metric for an item
	 * (volatility, liquidity, or 30-day range position) via {@link MarketClassifier}.
	 *
	 * @return the rating label, or {@code null} when it can't be classified
	 */
	private String categoryValue(TrackedItem item, NotificationMetric metric)
	{
		switch (metric)
		{
			case VOLATILITY:
				return MarketClassifier.volatility(item.getSeriesFor(TimeWindow.WEEK));
			case LIQUIDITY:
			{
				PriceStats s = item.getWindowStats().get(TimeWindow.H24);
				return MarketClassifier.liquidity(s == null ? 0 : s.getVolume());
			}
			case RANGE_30D:
			{
				long[] range = MarketClassifier.thirtyDayRange(item.getSeriesFor(TimeWindow.MONTH));
				return MarketClassifier.rangePosition(range[0], range[1], item.getAvgPrice());
			}
			default:
				return null;
		}
	}

	/** Builds the user-facing notification message, e.g. {@code "Pricewatch: Coal - High >= 200"}. */
	private String notificationText(TrackedItem item, NotificationRule rule)
	{
		NotificationMetric metric = rule.getMetric();
		String valueDisplay;
		if (metric.isCategorical())
		{
			valueDisplay = rule.getValue();
		}
		else if (metric.getKind() == NotificationMetric.Kind.PERCENT)
		{
			OptionalDouble v = NotificationRule.parsePercent(rule.getValue());
			valueDisplay = v.isPresent() ? NotificationRule.formatPercent(v.getAsDouble()) : rule.getValue();
		}
		else
		{
			OptionalDouble v = NotificationRule.parseNumeric(rule.getValue());
			valueDisplay = v.isPresent()
					? String.format(Locale.US, "%,d", Math.round(v.getAsDouble()))
					: rule.getValue();
		}

		return "Pricewatch: " + item.getName() + " - " + metric.getDisplayName()
				+ " " + rule.getOperation().getSymbol() + " " + valueDisplay;
	}
}
