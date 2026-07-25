<!--
Pricewatch changelog. Newest release first. Each release is a top-level heading
"# <version> - <written-out date>" followed by a Quick Overview, a Detailed
Breakdown (features grouped by area, each with the issues that make it up), and
Bug Fixes. The build fails if the top entry's version does not match
runelite-plugin.properties (see ChangelogGuardTest), so a version bump forces a
new entry before anything can ship. Order features within a section by user impact.
Bug Fixes lists only bugs that shipped in a previous release; bugs introduced and
fixed within the same release cycle are omitted, since users never saw them.
-->

# 0.1 - July 25 2026

## Quick Overview

The first release of Pricewatch. Keep a watchlist of the items you care about and see what the market is doing to them: live Grand Exchange prices straight from the wiki, price and volume charts, how volatile and how liquid an item is, whether buyers or sellers are winning right now, what's left of your 4-hour buy limit, and what it alchs for. Organise the list into categories and favourites, sort it however you like, and share it with a code. Set price alerts and get a notification when something crosses your threshold. Put the prices you're watching most closely straight onto the game screen, and jump from a Grand Exchange offer to that item's panel with one click.

Pricewatch only ever reports on the market. It never records how many of an item you own or what you paid — the Stockpile plugin does all of this and that too, so the two are alternatives and RuneLite runs one or the other, never both.

## Detailed Breakdown

### Your Watchlist

#### Live prices for the items you watch
Search for any item and add it to your watchlist. Each row shows its icon, its name, and a price line you choose, refreshed from the wiki's real-time prices.
[#4](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/4), [#7](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/7), [#3](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/3)

#### Look up an item without watching it
Search an item to see its full detail view straight away. It stays a preview until you actually add it, so a quick price check doesn't clutter your list.
[#7](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/7)

#### Choose what the price line shows
Pick which figure each row displays — current high, low, average or volume — and whether prices are tinted green or red as they move.
[#8](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/8)

#### Categories, favourites and groups
Group your watchlist into categories you name yourself, star the items you check most, and roll any group up out of the way. One click auto-sorts the whole list into sensible groups based on what the items are.
[#14](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/14), [#9](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/9)

#### Sort, filter and reorder
Sort by name, price or 24-hour change, reverse any sort, filter the list as you type, and drag rows into exactly the order you want.
[#9](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/9), [#10](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/10)

#### Share and back up your list
Copy your watchlist and its categories to a short code, and paste one in to merge it with yours.
[#11](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/11)

### The Item Detail View

#### Price and volume charts
Every item gets price and volume charts over the timeframe you choose, and either chart pops out into its own resizable window beside the client.
[#20](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/20)

#### Everything the market is doing, in one card
Current high, low and average, the day's movement, the 30-day range, trading volume, and when the item last actually bought and sold.
[#19](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/19), [#21](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/21)

#### Ratings and buy/sell pressure
At-a-glance ratings for how volatile and how liquid an item is, and a bar showing whether buyers or sellers currently have the upper hand.
[#22](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/22)

#### Alchemy values
High and Low Alchemy values, with the profit left after the cost of the runes.
[#22](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/22)

#### Arrange the detail view your way
Every section can be switched off or moved, so the things you look at first are the things at the top.
[#19](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/19)

#### The overview grid
A compact grid of every price window at once, for when you want the whole picture rather than one figure.
[#21](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/21)

### Grand Exchange

#### How much of your buy limit is left
Pricewatch watches your own Grand Exchange buys and shows how much of an item's 4-hour limit you have used, with a countdown to when it resets.
[#23](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/23)

#### Jump straight from an offer to the panel
Opening a Grand Exchange offer shows that item in Pricewatch, and a button on the offer screen does the same on click. Either half can be turned off.
[#36](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/36)

### Price Alerts

#### Tell me when it hits my number
Set rules per item — high, low, average or 24-hour change, above or below a threshold you choose, over the timeframe you choose — and get a RuneLite notification when one triggers. Rules can fire once or repeat.
[#29](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/29), [#30](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/30)

### On-Screen Overlay

#### Prices on the game screen
Put up to five watched items onto the screen as draggable price boxes, in a standard or compact layout, so you can keep an eye on them without opening the panel.
[#34](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/34)

### Elsewhere

#### Pricewatch or Stockpile, not both
Stockpile already does everything Pricewatch does, and tracks what you own on top, so running both would be the same panel twice. Turning one on now turns the other off. Each keeps its own settings and list, so switching back later picks up where you left off.
[#55](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/55)

#### "What's New" changelog
This window — a quick way to see what each update added, right inside the plugin.
[#42](https://github.com/Oveduumnakal/Pricewatch-Plugin/issues/42)

## Bug Fixes

Nothing yet — this is the first release.
