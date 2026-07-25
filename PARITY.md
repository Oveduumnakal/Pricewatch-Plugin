# Parity with Stockpile

Pricewatch's market code was copied from the [Stockpile plugin](https://github.com/Oveduumnakal/Stockpile-Plugin) and has diverged since. A market bug fixed in one repo is not fixed in the other, and nothing enforces that it will be. This file is the record of which Stockpile changes have crossed over, so a fix is not silently lost and a deliberate divergence is not mistaken for one.

## Fork point

Pricewatch was cut from Stockpile **1.4** at `1eb39e3` (24 July 2026), on 25 July 2026.

## When a Stockpile change needs an entry here

Only market-side changes. Anything touching the tracking half — quantities, cost basis, acquisitions, the collection log, portfolio history, profit of any kind — is out of scope by construction and needs no entry, because Pricewatch has none of it.

A Stockpile change needs an entry when it touches any of the files below, which exist in both repos and started identical. Add a row when the Stockpile fix merges, not when it is ported — an unported fix is exactly what this file is for.

### Copied verbatim, still identical

A fix to any of these applies here as-is, and should be ported by copying the change across.

`BuySellBar`, `EllipsisText`, `GeIntegrationMode`, `GpFormat`, `ItemCategoryClassifier`, `MarketClassifier`, `NotificationOperation`, `OverlayLayout`, `OverviewPreset`, `PressureVolumeLabel`, `PressureWindow`, `PriceGraphPanel`, `PriceIndicatorMode`, `PriceRangeBar`, `PriceStats`, `TimeWindow`, `WikiRealtimePriceClient`

### Copied and adapted

Same origin, already changed here. A fix to one of these needs reading before it is applied.

| File | How it differs |
|---|---|
| `NotificationMetric` | `ITM_PROFIT` and `QUANTITY` deleted — both need a holding. `HA_PROFIT` kept. |
| `SortMode` | Value and Profit modes absent, both being quantity-derived. |
| `SectionSlot` | Eight positions rather than ten: no collection log, no profit block. |
| `GeOfferTracker` | Only `BUY` + `FILL` is consumed; the sell side has no counterpart here. |
| `CategoryState` | Package and imports only. |
| `NotificationRule` | Package and imports only. |
| `PopoutHandle` | Package and imports only. |

### Not copied

Stockpile's tracking half, and `scripts/gen-source-icons.sh`. Changes to these never need an entry.

## Ported changes

| Stockpile change | Date | Status | Notes |
|---|---|---|---|
| — | — | — | Nothing yet. Stockpile has had no commits since the fork point, so no market fix has come up to port. |

## Deliberate divergences

These are not gaps. They were decided during the extraction and should not be "fixed" by porting Stockpile's behaviour back in.

| Divergence | Why |
|---|---|
| No ground or inventory highlights | Built ([#35](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/35)) and removed again ([#40](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/40)). Outlining a watched item answers "where is my stuff", which is Stockpile's question, not this plugin's. |
| Favourites are a group, not pinned rows | A starred item appears under Favourites and nowhere else, rather than being duplicated at the top of the list. |
| `WatchlistShareCodec` refuses a foreign prefix before decoding | A different magic prefix alone is not enough: both plugins emit gzipped-Base64 JSON, so the inherited `inflate` would happily attempt a Stockpile code's body and fail somewhere less legible. The prefix is checked first. |
| GE offer-screen button defaults to off | Both plugins inject a button into the same corner of the offer screen. Two buttons fighting for the space should be a choice the user makes with the result in front of them, not something a fresh install does to them. |
| Its own wiki user agent | `WikiRealtimePriceClient` and `scripts/gen-item-categories.py` both identify as Pricewatch. Sharing Stockpile's identifier would make the two indistinguishable in the wiki's rate-limit accounting. |
| Config group `pricewatch` | Sharing `stockpile` would make the two plugins overwrite each other's keys in the same RuneScape profile. |

## Shared infrastructure

`scripts/check-style.py`, `.gitattributes`, `.gitignore`, and the four `.github/workflows` files are copies of Stockpile's and are currently byte-identical. They are not market code, but they drift the same way — if a style rule or CI gate is added there, it belongs here too. Verified identical as of 25 July 2026.
