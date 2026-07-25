# Pricewatch README — screenshot / GIF capture list

Keyed to the README's feature sections, in the order they appear, so each capture slots
straight in under its heading. The README already references every filename below, so a
capture is "done" when it lands at `docs/img/<name>` — no README edit needed.

Tracked in issue [#44](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/44).
Captured during the pre-release client session, since nothing in this plugin has been run
in a client yet.

## Live Grand Exchange prices

1. **`01-watchlist.png`** (still) — the hero shot. A populated watchlist with live prices
   visible. Mix expensive and cheap items so the number formatting variety shows (`5M`
   beside `284`).
2. **`02-preview.gif`** (GIF) — searching an item and its detail view opening as a preview,
   without the item joining the list. The point is that it is *not* added.
3. **`03-price-line.png`** (still) — rows configured to show different figures. Easiest as
   one shot after switching the price line setting, ideally with the change tint visible on
   at least one row.

## Charts

4. **`04-charts.png`** (still) — price and volume graphs in the detail view, on a volatile
   item over a longer timeframe, with the hover crosshair visible.
5. **`05-chart-popout.gif`** (GIF) — the pop-out button clicked and the standalone window
   appearing beside the client. Confirms it is a separate resizable window.

## Detailed market information

6. **`06-market-info.png`** (still) — the market info block: 30-day range, volume, last
   bought and sold. Ideally with one stale price dimmed.
7. **`07-ratings-pressure.png`** (still) — volatility and liquidity ratings with the
   buy/sell pressure bar, on a heavily traded item so the bar is not sitting at 50/50.
8. **`08-alchemy.png`** (still) — an item where high alch profit is positive, so the
   rune-cost-adjusted figure is worth looking at.
9. **`09-overview-grid.png`** (still) — the six-column grid at real panel width. **Check
   this one before capturing**: the layout has never been seen at real width, and if it
   does not fit, that is a bug to file rather than a screenshot to take.

## Organize your list

10. **`10-categories-favourites.png`** (still) — the list in 2–3 collapsible categories with
    a Favourites group on top and one group rolled up.
11. **`11-auto-categorise.gif`** (GIF) — one click on auto-categorise and the list
    reorganising. A still cannot sell this one.
12. **`12-compact-filter.gif`** (GIF) — compact toggled on, then two letters typed into the
    filter box and the list narrowing. Two features, one short clip.
13. **`13-share.png`** (still) — the share dialog with a code in it.

## Grand Exchange

14. **`14-buy-limit.png`** (still) — bought against limit with the reset countdown, on an
    item part-way through a window. Needs a real buy on the account first; capture the red
    at-cap state instead if one is available.
15. **`15-ge-integration.gif`** (GIF) — the injected button on an offer screen being
    clicked and the panel opening to that item. **Capture with Stockpile also installed**,
    since the two-button collision is the thing the README's note is about, and it has
    never been seen.

## Price alerts

16. **`16-alert-editor.png`** (still) — a rule half-filled in ("price above 1,000") so the
    metric, operator and timeframe dropdowns are all visible.
17. **`17-alert-firing.png`** (still) — the RuneLite notification for a triggered rule.

## On-screen overlay

18. **`18-overlay-boxes.png`** (still) — standard and compact boxes over the game world,
    against a clean background rather than a busy bank.

## Practical notes

- Capture at a fixed client size so every panel shot comes out the same width.
- Keep GIFs to 2–4 seconds and crop to the panel, so the README stays light.
- The README sizes paired images at `width="30%"` / `width="68%"`, so shots meant to sit
  side by side should be captured at similar aspect ratios.
- **Minimum viable set** if the full list is too much: **1, 4, 6, 10, 15, 18** — one strong
  shot per section.
- Several captures double as verification. The overview grid (9), the GE button (15), the
  alert editor's table (16) and the overlay boxes (18) are all on the pre-release session's
  list of things never seen in a client; if one of them looks wrong, file the bug rather
  than framing around it.
