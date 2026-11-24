# LM full review, November 2026 (after 4.7.72)

A read-through of the whole mod against what 4.7.58-4.7.72 built: native menus and skins, the page registry and
navigation, the native HUD, and the test rig. Four reviews (presentation channels, menu layer, client renderer,
tooling) ran read-only. Findings I re-checked by hand are marked **verified**. "Visible" means a fix changes what
players see; those need the owner's OK and a ship-manifest row.

## A. Bugs in recent work (visible; recommended for 4.7.73)

1. **Difficulty search, breadcrumbs and KaiPath Back replay the guide** (4.7.71, **verified**). `PageRegistry.java:71-92`
   makes the guide steps (`guide`, `guide_tiers`, `guide_your_tier`) the searchable "Difficulty", "Tiers" and "Your
   tier" pages and the parents of Summons, Team, Titles, Tier ask and Clear tier. Those ids always replay the guide
   (`DifficultyTutorial.route`), so a player who finished it is sent back to Step 1-3. Fix: `main`, `tiers`,
   `your_tier` become the searchable pages and the parents (they route by guide state); guide pages become items.
   The navigation audit passed because it compares Back with the registry parent, which was itself wrong; add a check
   that no searchable page or parent is a guide page for a player who finished the guide.
2. **Pages missing from the registry**: `spar|weights_confirm:*:*` (a purchase confirm), `pick_dojo_challenge[:N]`,
   `dojo_top_rp`, `dojo_top_wars`, `dojo_hof`, and page 2+ of paged lists (`rival|actions:N`, `challenge:N`,
   `requests:N`, `spar|pending:N`, `prestige|mastery_lines:N`). They get no breadcrumb, and on KaiPath their Back
   goes to the main menu. Cause: `sim/audit_page_registry.py` only scans `.page("…")` / `"page:…"` literals, not
   `sub(var)` or the pager's `:N` pages. Fix the entries and teach the audit the pager (the runtime already warns,
   `UltraScreenSpec.java:1623`).
3. **HUD placement** (4.7.72):
   - The Momentum meter sits at `sh-72`, the vanilla action bar's row (`sh-68`) and the held-item name. Use Forge's
     right-side height or move it.
   - The top stack starts at y=4, under any other mod's or vanilla boss bars (bars from y=12), the Tab list and
     titles. Start below the drawn boss bars (`CustomizeGuiOverlayEvent.BossEventProgress`).
   - At GUI width 320 the 260-wide Underdog pill crosses the top-right effect icons.
   - Instinct cards have no bottom bound: three cards at sh=240 meet the Momentum meter. They overlap chat, and they
     take clicks meant for chat links.
   - The TP notice sits in the middle of the stack, so the penalty pill jumps while a notice shows. Put transient
     elements last.
   - A clamped width does not re-fit the text.
   - Draw as a registered overlay (under chat) instead of `RenderGuiEvent.Post`.
4. **HUD frame cost and a leak** (4.7.72): `HudChrome.get` builds a string key every frame (keep the baked handle
   on the slot). A failed bake is retried every frame and leaks its `NativeImage`; `MenuBackground` and
   `ColorWheelTex` share the leak.
5. **Dojo style change destroys a paid bonus in one click** (`UltraDojoScreens.java:586`, `DojoService.setStyle`).
   The cooldown keeps running. The bonus purchase also spends points with no ask and no cost on its label. Both
   need an ask page naming the cost and what is lost (CLAUDE.md copy rule).
6. **Smaller rule breaks**:
   - "Step down" uses the normal style (it should be danger).
   - "War rights: on" does not follow the `Label - ON/OFF` toggle format.
   - An active Prev button is drawn dim (the disabled look) in 12 places.
   - Rival settings shows each state twice.

## B. Chat and screen spam the HUD could carry (visible; each needs approval)

From the presentation review, ranked by frequency × annoyance. All are chat today.

| # | Where | Today | HUD idea |
|---|---|---|---|
| 1 | `rival/RivalSpectator.java:102` | `[Spec] A 123 vs B 456 (42s)` every 2 s while spectating | live scoreboard pill |
| 2 | `sparring/SparringSystem.java:1136` | "Perfect Training active" every 2.5 s | status chip |
| 3 | `rival/RivalChallengeManager.java:504` | "Too far apart" every 3 s to both fighters | range warning with distance |
| 4 | `RivalChallengeManager.java:517` | damage score every 15 s | score + clock pill (rival challenges were not excluded) |
| 5 | `progression/skills/PotentialProgression.java:263` | subtitle on **every hit** after a 5-hit streak | Potential meter |
| 6 | `sparring/SparringSystem.java:1271` | "Combo broken" up to every 1.5 s | feed the Momentum meter |
| 7 | `currency/AncientCoinEconomy.java:579` | "Dropped X Ancient Coins" every kill (if on) | running counter toast |
| 8 | `title/TitleSense.java:65` | elite nearby, every 45 s | small indicator |
| 9 | `kube/hotpath/HealCap.java:40` | healing caps line **every login** | drop it, or show it on a stats page |

Keep in chat (receipts and records): spar summaries, streak lines, Proving Grounds claims, match reports, prestige
receipts, title unlocks, race/android confirmations. The Proving Grounds Underdog chat notice
(`ProvingGrounds.java:448`) now repeats what the HUD shows for HUD players.

Copy-rule breaks found on the way:
- Raw mob ids: `enderman`, `shulker` (`EndDimensionStrength.java:2573`).
- A field name, `difficultyChosen` (`RaceLock.java:146`).
- A reason token, `(cooldown)` (`ShadowDummyLimiter.java:219`).
- A lower-cased enum, `[countdown]` (`RivalSpectator.java:101`).
- Raw `§` remains outside `gui/`: about 200 in `ProgressionGuiApi`, about 90 in `PrestigePointsSystem`, plus
  `GlobalTpBoost`, the rival chat files and `EndDimensionStrength`.

## C. Client performance (not visible; the client jar changes, so it ships in a version)

- `NativeMenuScreen` breaks the 4.7.61 rule "lay text out once". Every frame it parses, contrast-checks
  (`MenuSkins.readable`: up to 42 luminance calls, each allocating), measures, trims and bidi-orders every title,
  line and label. `drawBook` re-wraps every page line. Fix: a laid-out text cache built on packet, skin or scale
  change.
- Rounded shapes: each one allocates a key and a lambda, and flushes the buffer twice (about 20 flushes per button,
  hundreds per frame). Moving shapes (tab slide, bar fill) churn the geometry cache. Fix: one shared `ChromeBaker`
  (SDF bake at screen density, 3-slice so width is not in the key). Menus, insets, glossy faces, HUD panels and
  the panel shadow would all use it, in place of three separate implementations.
- Smaller items:
  - `focusables()` sorts every frame.
  - `liftOf` hashes JSON-backed records.
  - `drawIcon` builds an `ItemStack` every frame.
  - The background re-bakes on every intermediate size during a window drag.
  - `BAR_SHOWN`, `TAB_SHOWN` and `SHAPES` are never cleared on logout.

## D. Code structure (not visible)

- 23 hand-built confirm/ask pages across 7 files, with 4 page-id naming styles. Make one `UltraScreenSpec.ask(...)`
  helper: title, cost/loss block, danger button, back().
- 8 inline pagers in `UltraCharacterScreens` alongside `UltraPickHelper.placePrevNext`.
- About 20 `base()`/`sub()` copies, 51 raw `"confirm"`/`"dim"` style strings, and no toggle helper.
- Dead code:
  - `UltraSparScreens.tabFor`
  - `UltraDifficultyScreens.homeLabel`
  - three `UltraDifficultyTierUi` methods
  - `UltraStyle.buttonLabel` and `hubTilePaletteName`
  - `UltraScreenSpec.clearPlayerModel` and `menuFit`
  - `UltraScreenIds.isProtocolId`
  - `ModelPreview.readGeoSize`
- Legacy code:
  - the `migrate-cnpc` actions
  - retired alias routes
  - the CNPC Hub wording in `docs/CNPC_GUI_MENU_RULES.md:35-38`
- Big files to split: `UltraScreenSelfTest` (2674 lines, test code in the player jar), `UltraScreenSpec` (two
  layout engines plus navigation), `UltraCharacterScreens` (9 subsystems), `UltraConfigScreens`,
  `NativeMenuScreen` (it hard-codes hub page ids for the skin preview).
- Registry titles are generic ("Back to Perk" not "Back to Aura"): add an optional title resolver per entry.
  Prestige `main` and `turnin` are both titled "Prestige".

## E. Tools (done in this pass unless marked)

Done, on branch `lm-tools-upgrade`:
- `tools/lm-ui-probe/release-check.sh`: build, mock, matrix, kit corpus refresh and `SUMMARY.md` in one command.
  Exits non-zero on any failure. `rig.env.example` holds the rig paths.
- `bump-version.sh`: sets all four version spots and refuses a reused or retired number.
- The prestige-shop reference check accepts `AdaptiveDifficultyMod.class` when only the version string differs, so a
  bump no longer needs a second build. `rebaseline-version-class.sh` stays available for tidying.
- `audit_ship_manifest.py`:
  - The 189 anchor cuts fail loudly when the anchor is gone, instead of checking the whole file. None are
    currently missing.
  - New `has()`/`ordered()` helpers for checks that survive reformatting.
- `build.sh`:
  - Defaults to the DragonMineZ jar releases use.
  - Checks the client libraries (LWJGL) before compiling.
- `report.py`:
  - Scripted totals per section.
  - A native HUD row.
  - A KaiPath "no HUD over the bridge" row.

Still open:
- **Move the rig out of the temp folder.** The prepared server, the client, the KaiPath jar and the live config copy
  all sit under `AppData/Local/Temp`; a reboot or cleanup loses them.
- Probe `check(id, ok, evidence)` helper writing `CHECK <id> PASS|FAIL`, with a `TOTAL` line. Today it uses about
  55 inline verdicts.
- Split `UiProbe.java` (2968 lines) into a context, a checks helper and one script class per feature. Use the
  existing `call()`/`cls()` helpers instead of 14 ad hoc reflection sites. Merge `dojoStandIn` and `ffStandIn`.
- A page-registry audit for runtime page ids (pager `:N`, `sub(var)`), plus a "guide pages are never parents"
  check (finding A1).
- `build.sh`:
  - Run the source-only audits before compiling.
  - Five audits run twice.
  - The jar is listed 18 times.
  - The full jar listing floods the log.
  - Old jars are deleted before the audits pass.
- `scripts/modpack_push.py` with a dry run (the steps live only in notes today).
- Probe coverage for prestige shop buy/spend-confirm and the staff config editor (source-checked only today).
- `mock_compose.py` side by side for the HUD on/off shots.
