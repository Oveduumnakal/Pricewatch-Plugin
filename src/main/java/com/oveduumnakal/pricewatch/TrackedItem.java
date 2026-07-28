/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.pricewatch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * The full state of one item being tracked: its identity and quantity, the
 * latest wiki prices (high/low/average) and their deltas, per-window summary
 * stats and price history, GE metadata (buy limit, alch values), and the
 * acquisition lots that back its cost-basis profit calculations.
 *
 * <p>Price-history {@code series*} lists are {@code transient}: they are fetched
 * at runtime and not persisted with the rest of the item. The value/profit
 * accessors derive figures from {@code quantity}, the current prices, and the
 * {@link AcquisitionRecord} lots.
 */
@Data
public class TrackedItem
{
	private final int itemId;
	private final String name;
	private int quantity;

	private boolean tradeable = true;
	private boolean stackable;
	private boolean priceLoadFailed;

	private boolean favorite;
	private String category;
	private boolean onOverlay;

	private long highPrice;
	private long lowPrice;
	private long avgPrice;
	private transient boolean priceCacheHydrated;

	private long latestHighTime;
	private long latestLowTime;

	private int highDelta;
	private int lowDelta;
	private int avgDelta;
	private long prevHighPrice;
	private long prevLowPrice;
	private long prevAvgPrice;
	private boolean hasDeltas;

	private boolean costBasisInitialized;
	private List<AcquisitionRecord> acquisitions = new ArrayList<>();
	private List<NotificationRule> notifications = new ArrayList<>();

	/**
	 * Units committed to an in-flight GE sell offer: physically gone from held
	 * containers but not yet realized, so their lots stay open (cost basis intact)
	 * until the offer fills. Reconciled from the live open sell offers on login;
	 * legacy records default to 0 (no in-flight sale) — the safe additive default.
	 */
	private int suspendedQuantity;

	/**
	 * Units lost to a death and awaiting recovery: gone from held containers but
	 * their lots stay open (cost basis intact) until a gravestone/Death's Office
	 * reclaim restores them, or the suspension outlives the gravestone timer and
	 * closes at 0. Survives relogs — persisted explicitly through
	 * {@code PersistedItem} (transient here so Gson never touches these fields).
	 */
	private transient int deathSuspendedQuantity;

	/** When the newest death suspension was taken, for the recovery-expiry sweep. */
	private transient Instant deathSuspendedAt;

	/**
	 * Units dropped on the ground and still considered owned: gone from held
	 * containers but their lots stay open (cost basis intact) until re-picked-up
	 * (un-suspends, a net no-op) or lost (despawn/expiry closes them at 0). Kept
	 * transient — floor items rarely survive a logout, so remaining suspensions
	 * close as losses on logout/shutdown rather than persisting.
	 */
	private transient int groundSuspendedQuantity;

	/** When the newest ground suspension was taken, for the lost-drop expiry sweep (transient). */
	private transient Instant groundSuspendedAt;

	/**
	 * Units placed into a player-trade offer and still considered owned: gone from
	 * held containers the moment they were offered, but their lots stay open (cost
	 * basis intact) until the trade finalizes (closes them at the apportioned
	 * trade price) or is withdrawn/declined (un-suspends, a net no-op). Kept
	 * transient — a trade cannot survive a logout, so the offer returns to the
	 * inventory and any remaining suspension is moot on relog (#66).
	 */
	private transient int tradeSuspendedQuantity;

	/**
	 * Units moved into a fur/meat hunting pouch and still considered owned: gone from
	 * held containers the moment they were "Fill"ed in, but their lots stay open (cost
	 * basis and original source intact) until the pouch is emptied to the bank, which
	 * un-suspends them as a net no-op. Any surplus emptied beyond what was filled is
	 * newly-gathered ({@code GATHER}). Survives relogs — the pouch keeps its contents
	 * across a logout — so it is persisted explicitly through {@code PersistedItem}
	 * (transient here so Gson never touches it directly). Legacy records default to 0
	 * (nothing parked in a pouch), the safe additive default (#214).
	 */
	private transient int pouchSuspendedQuantity;

	/** Units bought toward the GE buy limit in the current 4-hour window (transient; set from the plugin). */
	private transient int limitBought;

	/** Epoch-second when the current GE buy-limit window resets, or 0 when none (transient). */
	private transient long limitResetEpoch;

	private boolean notificationsInitialized;

	private TrackItemMode mode = TrackItemMode.TRACK;
	private Map<TimeWindow, PriceStats> windowStats = new EnumMap<>(TimeWindow.class);

	private transient List<WikiRealtimePriceClient.PricePoint> series5m = new ArrayList<>();
	private transient List<WikiRealtimePriceClient.PricePoint> series1h = new ArrayList<>();
	private transient List<WikiRealtimePriceClient.PricePoint> series6h = new ArrayList<>();
	private transient List<WikiRealtimePriceClient.PricePoint> series24h = new ArrayList<>();

	private int buyLimit;
	private long geValue;
	private long highAlch;
	private long lowAlch;
	private boolean metadataLoaded;

	/**
	 * Selects the price-history series whose sampling granularity best fits the
	 * given window: 1h points for a week, 6h for a month, 24h for quarter/half/year,
	 * and 5m points for anything shorter.
	 *
	 * @param window the time window being displayed
	 * @return the backing point list (live, not a copy)
	 */
	public List<WikiRealtimePriceClient.PricePoint> getSeriesFor(TimeWindow window)
	{
		switch (window)
		{
			case WEEK:
				return series1h;
			case MONTH:
				return series6h;
			case MONTH3:
			case MONTH6:
			case YEAR:
				return series24h;
			default:
				return series5m;
		}
	}

	/**
	 * @return the stack size to render this item's icon at: the tracked quantity for
	 *         stackable items, else 1 (plain single sprite)
	 */
	public int iconStackSize()
	{
		return stackable ? Math.max(1, quantity) : 1;
	}

	/** @return the tracked quantity valued at the high (instant-buy) price. */
	public long getHighValue()
	{
		return (long) quantity * highPrice;
	}

	/** @return the tracked quantity valued at the low (instant-sell) price. */
	public long getLowValue()
	{
		return (long) quantity * lowPrice;
	}

	/** @return the tracked quantity valued at the average price. */
	public long getAvgValue()
	{
		return (long) quantity * avgPrice;
	}

	/** @return whether any live price is known for this item. */
	public boolean hasPrices()
	{
		return highPrice > 0 || lowPrice > 0;
	}

	/** @return whether this item has prices from a live fetch rather than persisted cache hydration. */
	public boolean hasLivePrices()
	{
		return hasPrices() && !priceCacheHydrated;
	}

	/** @return total gp paid for the lots still held (unsold acquisitions). */
	public long getCostBasis()
	{
		return acquisitions.stream()
				.filter(r -> r.getSoldAt() == null)
				.mapToLong(r -> (long) r.getQuantity() * r.getBoughtAt())
				.sum();
	}

	/** @return profit already locked in from sold lots: sum of qty * (sold - bought). */
	public long getRealizedProfit()
	{
		return acquisitions.stream()
				.filter(r -> r.getSoldAt() != null)
				.mapToLong(r -> (long) r.getQuantity() * (r.getSoldAt() - r.getBoughtAt()))
				.sum();
	}

	/**
	 * @return total gp paid across every logged lot, held and sold. Unlike {@link #getCostBasis()}
	 *         (held lots only), this stays fixed as lots are sold, so it is the running invested
	 *         baseline for the portfolio value chart.
	 */
	public long getInvestedCostBasis()
	{
		return acquisitions.stream()
				.mapToLong(r -> (long) r.getQuantity() * r.getBoughtAt())
				.sum();
	}

	/** @return realized sale proceeds: sum of qty * sold price across sold lots (0 while nothing is sold). */
	public long getRealizedProceeds()
	{
		return acquisitions.stream()
				.filter(r -> r.getSoldAt() != null)
				.mapToLong(r -> (long) r.getQuantity() * r.getSoldAt())
				.sum();
	}

	/** @return total units across the lots still held (unsold acquisitions). */
	public int getRecordQuantitySum()
	{
		return acquisitions.stream()
				.filter(r -> r.getSoldAt() == null)
				.mapToInt(AcquisitionRecord::getQuantity)
				.sum();
	}

	/**
	 * @return units suspended across every source (GE sell, trade, ground, death,
	 *         hunting pouch): owned and still covered by open lots, but held outside
	 *         the containers that {@code quantity} counts
	 */
	public int getTotalSuspendedQuantity()
	{
		return suspendedQuantity + tradeSuspendedQuantity + groundSuspendedQuantity + deathSuspendedQuantity
				+ pouchSuspendedQuantity;
	}

	/**
	 * @return the suspended units valued at the average price. Suspended units are still
	 *         owned and their lots still open, so value/profit figures that subtract an
	 *         open-lot cost basis must add this back — otherwise every in-flight sell,
	 *         trade, drop, or death reads as a loss for its duration
	 */
	public long getSuspendedValue()
	{
		return (long) getTotalSuspendedQuantity() * avgPrice;
	}

	/**
	 * Total profit if the held lots were valued at {@code markPrice}: realized
	 * profit from sold lots plus the unrealized gain/loss on still-held lots.
	 *
	 * @param markPrice the per-unit price used to mark held lots to market
	 * @return realized plus unrealized profit in gp
	 */
	public long getProfitAt(long markPrice)
	{
		return getRealizedProfit() + acquisitions.stream()
				.filter(r -> r.getSoldAt() == null)
				.mapToLong(r -> (long) r.getQuantity() * (markPrice - r.getBoughtAt()))
				.sum();
	}
}
