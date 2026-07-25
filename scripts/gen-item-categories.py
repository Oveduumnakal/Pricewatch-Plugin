#!/usr/bin/env python3
"""Regenerates the bundled item-category snapshot from the OSRS Wiki.

Fetches the member pages of each bucket's wiki categories (including one level
of subcategories) and writes them as ``name<TAB>bucket`` lines to
``src/main/resources/com/oveduumnakal/item-categories.txt``, sorted by name so
regeneration produces stable diffs.

Buckets are listed in precedence order and the first bucket to claim a name
wins, so specific buckets (Jewellery, Ammo) must precede broad ones. Gems is
deliberately last: the wiki files gem-material equipment (e.g. "Ruby bolts",
"Red topaz machete") under the per-gem subcategories, and ranking Gems last
lets Weapons/Ammo/Jewellery claim those items first.

The snapshot is keyed by wiki page name rather than GE item id so non-GE items
(e.g. "Pillar frag") are covered too; charge variants such as "Ring of
dueling(4)" resolve at runtime by stripping the trailing parenthetical to the
base page name. Run this script from the repository root whenever the
groupings need a refresh, then commit the regenerated resource:

    python3 scripts/gen-item-categories.py
"""

import json
import time
import urllib.parse
import urllib.request

USER_AGENT = "Stockpile-Plugin category generator (https://github.com/Oveduumnakal/Stockpile-Plugin)"
API = "https://oldschool.runescape.wiki/api.php"
OUTPUT = "src/main/resources/com/oveduumnakal/item-categories.txt"

# Bucket -> wiki categories, in precedence order: first match wins.
BUCKETS = [
    ("Seeds", ["Seeds"]),
    ("Runes", ["Runes"]),
    ("Herbs", ["Herbs"]),
    ("Jewellery", ["Jewellery"]),
    ("Ammo", ["Ammunition"]),
    ("Potions", ["Potions"]),
    ("Bones", ["Bones"]),
    ("Logs", ["Logs"]),
    ("Ores & Bars", ["Ores", "Metal bars"]),
    ("Hunter", ["Hunter"]),
    ("Food", ["Food"]),
    ("Weapons", ["Weapons"]),
    ("Armour", ["Armour"]),
    ("Gems", ["Gems"]),
]


def query(params):
    url = API + "?" + urllib.parse.urlencode({**params, "format": "json"})
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def members(category, member_type):
    """Returns the titles of a category's members of the given type, following continuations."""
    titles, cont = [], {}
    while True:
        data = query({
            "action": "query",
            "list": "categorymembers",
            "cmtitle": f"Category:{category}",
            "cmlimit": "500",
            "cmtype": member_type,
            "cmprop": "title",
            **cont,
        })
        titles += [m["title"] for m in data.get("query", {}).get("categorymembers", [])]
        if "continue" not in data:
            return titles
        cont = data["continue"]
        time.sleep(0.2)


def category_pages(category):
    """Returns a category's page members plus those of one level of subcategories."""
    names = set(members(category, "page"))
    for subcategory in members(category, "subcat"):
        names |= set(members(subcategory.removeprefix("Category:"), "page"))
        time.sleep(0.15)
    return names


def main():
    snapshot = {}
    for bucket, categories in BUCKETS:
        names = set()
        for category in categories:
            names |= category_pages(category)
            time.sleep(0.2)
        claimed = 0
        for name in names:
            key = name.lower()
            if key not in snapshot:
                snapshot[key] = bucket
                claimed += 1
        print(f"{bucket}: {len(names)} wiki names, {claimed} claimed")

    with open(OUTPUT, "w", encoding="utf-8") as out:
        for name in sorted(snapshot):
            out.write(f"{name}\t{snapshot[name]}\n")
    print(f"\nWrote {len(snapshot)} entries to {OUTPUT}")


if __name__ == "__main__":
    main()
