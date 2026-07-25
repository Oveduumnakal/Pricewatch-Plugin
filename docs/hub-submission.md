# RuneLite plugin hub submission

The steps for getting Pricewatch onto the hub, and the record of what has already been
checked. Written during phase 6; **the submission itself happens after the pre-release
client session and the `R-0.1` tag**, not before.

Requirements below are from the [plugin-hub README](https://github.com/runelite/plugin-hub)
as it stood on 25 July 2026. Re-read it before submitting — it is the authority, and this
file is a snapshot.

## Namespace

**`pricewatch` is free.** Re-checked 25 July 2026 against the full plugin list (2,159
manifests, read from the repository tree rather than the contents API, which silently caps
at 1,000 entries).

The descriptive neighbours the plan flagged all still exist and are all still distinct:
`pricecheck-flipping`, `market-watcher`, `price-graph-opener`. Also nearby but unrelated:
`price-paid`, `bank-watcher`, `runewatch`.

The manifest filename must be `pricewatch`, with no extension.

## Pre-submission checklist

Everything here was verified on 25 July 2026 against the repository as it stands.

| Requirement | State |
|---|---|
| Repository is public | ✅ |
| `LICENSE` is BSD 2-Clause | ✅ `Copyright (c) 2026, Oveduumnakal` |
| `icon.png` at repo root, no larger than 48×72 | ✅ 48×48 RGBA — but the art is still a placeholder, see below |
| `runelite-plugin.properties` complete | ✅ display name, author, description, tags, plugins, version, build |
| `build=standard` | ✅ — `build.gradle` and `settings.gradle` are replaced during hub packaging |
| `@PluginDescriptor` name set | ✅ `Pricewatch` |
| No third-party runtime dependencies | ✅ only `net.runelite:client` (compileOnly), Lombok (compileOnly), and JUnit (test) — so no Gradle dependency verification is needed, which is the single biggest source of review delay |
| Resources read with `getResourceAsStream` | ✅ the hub is explicit that `getResource` returns a jar URL in production and a file URL in the IDE; the only resource read is `ItemCategoryClassifier`, and it uses the stream form. The changelog does too, and icons go through `ImageUtil.loadImageResource` |
| `README.md` present | ✅ |

**Not yet met:**

- **The icon is a generated placeholder** (#50). The hub displays it beside the listing, so
  real art should land first.
- **Nothing has been run in a client.** This is the blocker. Submitting unverified code to
  a hub whose reviewers run it is the wrong order.

## Submission steps

1. Finish the pre-release client session and fix whatever it turns up.
2. Land the art and screenshots (#50).
3. Confirm `runelite-plugin.properties`'s `version` matches the newest `changelog.md`
   heading — `ChangelogGuardTest` enforces this, so a green build is the confirmation.
   Correct the changelog entry's **date** to the actual release date if it has drifted; the
   guard checks the format, not the accuracy.
4. Tag the release:

   ```bash
   git tag R-0.1
   git push origin R-0.1
   ```

   This fires `release.yml`, which creates the GitHub release with generated notes and
   closes the `Release 0.1` milestone. It fails loudly if the milestone does not exist or
   still has open issues, so close or re-milestone anything outstanding first.
5. Note the exact 40-character commit hash the tag points at:

   ```bash
   git rev-parse R-0.1^{commit}
   ```

6. Fork [`runelite/plugin-hub`](https://github.com/runelite/plugin-hub), branch, and add
   `plugins/pricewatch` containing exactly two lines:

   ```
   repository=https://github.com/Oveduumnakal/Pricewatch-Plugin.git
   commit=<the hash from step 5>
   ```

7. Open a pull request against `runelite/plugin-hub` with a short description of what the
   plugin does.
8. Watch both checks on that PR — `build (pull_request)` and `RuneLite Plugin Hub Checks`.
   The second one only matters if it says `Changes are needed.` Push fixes to this repo,
   then update `commit=` in the same hub PR rather than opening a new one.

## Updating later

Every subsequent release is the same manifest with a new `commit=`. From a
`plugin-hub` checkout:

```bash
git fetch upstream
git checkout -B pricewatch upstream/master
# edit plugins/pricewatch, update commit=
git add plugins/pricewatch
git commit -m "update pricewatch"
git push -f -u origin pricewatch
```

## Things the reviewers care about

- The plugin must not be malicious, must not break Jagex's third-party client rules, and
  must not be one of RuneLite's
  [rejected or rolled-back features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features).
  Nothing here reads or automates gameplay: the plugin reads public wiki price data, the
  player's own Grand Exchange offers, and draws its own UI.
- Adding a third-party dependency later would pull in the hash-verification process and
  slow every review from then on. Worth avoiding.
