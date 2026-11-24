# LM 4.7.71 build notes

Branch `lm-4771-nav-preview`, off `main` at 7fc893d5. Version 4.7.71 (4.7.69 and 4.7.70 are retired, never used).
Presentation and navigation only: no gameplay, prices, rates or config change.

## What shipped

**Part 1: form previews in the viewer's look.** `ModelPreview.formFigure` (Preview forms, the rival profile's "you"
view) still sends a made-up `uuid:` subject, never `local`, and now adds:

- `colors`: `PlayerModelParts.previewColors(owner, race, group, form)`, the same resolution as a real player's
  snapshot (skin, second, hair, eye, own/trim/horn/light, same precedence) but with the previewed form's overrides
  instead of the worn form's.
- `headBones`: the viewer's own head bones (`PlayerModelParts.headBonesOf`).
- `appearance`: `Character#saveAppearance` of the viewer as gzip NBT in base64. The LM client
  (`NativeFormLook`) loads it onto the stand-in before race and form, so DragonMineZ applies the form itself.
- `skinOwner`: the viewer's uuid. The LM client's stand-in (`NativeModelPreview.StandIn`) takes the skin texture and
  arm model from that player's `PlayerInfo` (the tab list entry the client already has), so Saiyan and Human figures
  wear the viewer's Minecraft skin.

The real-player snapshot, world model sync and `rivalFigure` are unchanged. The Preview forms page says so per
client: on the LM client "The model wears your own colors, hair and skin, in this form."; on KaiPath it
keeps "The model uses the race's default colors, not your own." until KaiPath applies the new fields.

**Part 3: navigation.**

- `gui/ultra/PageRegistry.java`: one entry per page (screen, page pattern, title, parent, staff, searchable,
  keywords). Back, breadcrumbs, search and the probe all read it. `sim/audit_page_registry.py` (run by `build.sh`)
  fails the build when a page the screens declare or link to has no entry; the boot self-test runs
  `PageRegistry.problems()` (every parent resolves, every chain reaches the main menu, search finds every
  searchable page) and `NavHistory.selfTest()`.
- `NavHistory`: per player, up to 32 pages, cleared on logout. Paging inside one page family (Previous / Next on
  Preview forms, page 2 of a list: same first id part, same registry parent) replaces the page, so Back leaves the
  family instead of stepping through its pages.
- One `UltraScreenSpec.back()` replaces `footerBackToMain`, `footerBackHome` and `footerBack` (156 calls in 12
  screen files). LM client: `nav:back`, named after the page you came from. Bridge: the registry parent. A system Home,
  and any page whose Back goes to the main menu, shows one "Back to main menu" on the right; deeper pages show Back on
  the left and Main menu on the right. `footerGuide` (with Skip) and `footerBackOnly` stay.
- Breadcrumbs (LM clients whose hello lists `nav`): pages deeper than a system Home, no tab row, three or more crumbs.
  Drawn in the header strip under the title, so no other element moves; each crumb but the last is a link.
- Hub: tabs Home, Search, More. Home has a search row above the tiles (LM clients that can type). Search lists up to
  8 matching pages as buttons with where each lives ("Upgrades - Sparring > Dojo"), staff also get the matching
  settings (the config editor's own search), and the last 5 searches per player show while the box is empty. KaiPath
  keeps a working Search tab (text field and result buttons); only the Home search row is LM-client only.
- Tab pages: a system's Home tab shows one "Back to main menu"; its other tabs go back to the system Home (registry,
  never the tab history) with Main menu beside them, as before 4.7.71 ("Back to Difficulty", "Back to Sparring").
- The dojo "Roster and bonds" page had the Dojo tab's page id (`spar|dojo`); it is now `spar|dojo_roster`, so Back,
  breadcrumbs and search tell them apart.
- Long page ids are cut to the packet limit on the wire; the history records the wire id, so Back from such a page
  (the staff Android confirm for another player) works.
- Hub audit: the Goals placeholder tab is gone (old `goals` links open Search; Goals comes back as a real page), the
  dim Skill Check tile is gone, Difficulty has Home/Tiers/Summons for everyone and staff open Combat rating from a
  Home button.

## Proof

Jar: `LegacyMechanics-4.7.71.jar`, sha256 `c3e12b9f35de163bcce8f55cacefa0016107c99481890454f0d995e8bb4f0327`.

- `build.sh`: every audit passes, including the new `audit_page_registry.py` (199 entries, every declared or linked
  page id has one) and the ship manifest (8 new 4.7.71 checks).
- Server boot, strict self-tests: 18 self-tests OK, including the page registry and history self-tests and the
  rewritten footer test (Back left, Main menu right; a Back to the main menu stands alone).
- Scripted real client (LM client + KaiPath): 185 PASS, 0 FAIL. Includes:
  - search "upgrades" -> Dojo upgrades (path shown) -> "Back to Search" -> the search with its results;
  - breadcrumbs on `spar|dojo_up:arena` (Main menu > Sparring > Dojo > Upgrades > Sparring arenas),
    `prestige|mastery_lines` and `rival|records:stats`, every crumb but the last a link, a crumb click opens its page;
  - recents: five kept, newest first, shown under an empty search, a tap searches again;
  - Preview forms 0 and 1: colors, appearance, head bones, skin owner, made-up subject, never local.
- Navigation audit: 149 registry entries reached from the main menu (102 searchable pages), 0 orphans, 128 Backs
  checked and each landed on its registry parent, 0 search misses: PASS. Four pages need state the probe account does
  not have and are listed as NAV SKIP: Custom menu skin colors, Replace a rival (full list), the custom perk shop
  (turned off in the test config), Skill Check (permission node).
- Full matrix (`tools/lm-ui-probe/matrix.sh`), 3654 layouts at GUI 2/3/4: every row PASS (native everywhere, five
  skins, 60+ FPS, layout, scale-to-fit, look/contrast, KaiPath only: every menu through the bridge, requireLmClient).
  "One spec for both render paths": 18 identical, 386 differ only by LM-client navigation (history Back, breadcrumb,
  search row), 8 by the color wheel, 0 other differences. The KaiPath packets carry no `breadcrumb` and no
  `nav:back`. No 4.7.68 wire baseline was used: every page's footer changes on purpose this release.
- Kit: corpus refreshed from the KaiPath stage (93 menu families); `LmCorpusTest` OK (606 buttons) with the other kit
  tests (overlay build read the KaiPath 1.5.6 jar as its base; the KaiPath jar itself is untouched).
- Mock mode (`-Duiprobe.mock=true`, default set `nav`): main menu with the search row, search results, a dojo upgrade
  opened from search (breadcrumb, Back to Search) and a deep Prestige page, each at GUI 2/3/4 (24 shots).

## Gaps and follow-ups

Registry:

- Titles are static. Back labels for pages whose name is run-time data now read the registry title: the mastery
  wallet's form page Back reads "Back to Mastery line" (was the line's own name, "Back to Big"), the level-up confirm
  reads "Back to Form mastery" (was the form's name). On the LM client the breadcrumb shows the same titles.
- Computed Back destinations lose some context on bridge clients, because the registry parent has no run-time
  state: paged lists (the rival profile returns to the Rival tab's first page, not the page it came from), the body
  part unlock prompts (back to the first body parts page), confirms that used to name their item. LM clients keep the
  exact page through the history.
- The Difficulty guide's sub-pages used to say "Back to Step 1"; they now name their registry parent
  ("Back to Difficulty"). The guide's own step footers (Skip) are unchanged.
- Search results link to concrete pages only; per-item pages (a perk, a title, a rival's profile) are found through
  their list, not directly.
- The hub `go:` action is staff-gated per system (`isStaffOnlySystem`); a non-staff search never lists staff pages.

Probe:

- The navigation audit walks from the main menu with the probe's own account state. Pages that need state the crawl
  does not create are listed as `NAV SKIP` with the reason (`UiProbe.NAV_STATEFUL`), not as failures: a finished
  spar, a bond invite, a rival request, enough prestige to turn in.
- "Back lands" is checked from a fresh history (registry parent). Back through the history is checked by the
  scripted search -> result -> Back to Search step.

Toolkit / kit:

- The kit corpus (`tools/ultra-gui-kit/test/fixtures/lm-specs.jsonl`) now comes from the KaiPath stage of the matrix
  (`gen_lm_corpus.py <run>/kai`), since the LM-client run carries LM-only navigation.
- `report.py` counts LM-client navigation differences as by design, like the color wheel; any other difference
  between the two render paths still fails.
- Menu builders: `back()` is the only Back a new page needs; a new page also needs one PageRegistry line or the build
  fails.

## JLDK note (text only; not sent)

> LM 4.7.71 adds four optional fields to the made-up preview figure's `playerModel.parts` (Preview forms and the
> rival profile's "you" view; subject stays `uuid:<id>`):
>
> - `colors`: same object as a real player's snapshot, resolved for the previewed form (skin, second, hair, eye,
>   own/trim/horn/light with that form's overrides).
> - `headBones`: the viewer's head bones, same `{token, pieces}` shape as for real players.
> - `appearance`: DragonMineZ `Character#saveAppearance` of the viewer, gzip NBT in base64.
> - `skinOwner`: the uuid whose Minecraft skin the stand-in should wear.
>
> If KaiPath applies `colors` and `headBones` the way it already does for real players, the preview stops showing
> race-default colours. `appearance` and `skinOwner` are optional extras (LM's own client uses them for hair/eyes and
> the Minecraft skin). Nothing else changes for KaiPath: no new required fields, the layout key `breadcrumb` is only
> sent to LM's own client, and Back on KaiPath stays an ordinary `nav:hub` / `open:<system>` / `nav:home` /
> `page:<id>`. Main menu tabs are now `home`, `search`, `more` (Goals is gone). Details:
> `docs/KAIPATH_GUI_PROTOCOL.md`, sections "Form previews in the viewer's look" and "Page registry, Back and
> breadcrumbs".

## Native HUD (Part 2)

Phase 1 is the proposal in `docs/LM_NATIVE_HUD_PROPOSAL.md`, waiting for the owner's approval. No HUD code is in this
build. What Phase 2 changes once approved:

- New native message `MSG_HUD` (4) and hello feature `hud`; only LM clients that list it get the HUD.
- For those players only, the server stops sending the boss bars / chat lines the HUD replaces (Underdog bar,
  Instinct lines, TP notices, death penalty bar), using the same text and numbers.
- A per-player HUD on/off in the hub's More tab; Off brings the boss bars and chat lines back.
- Momentum gets an on-screen meter (new; today it shows only in the Difficulty menu).
- Probe mock toggle draws every HUD element at GUI 2/3/4; the 4.7.61 lag rules apply (no per-frame allocation, sends
  only on change).
- Open decisions for the owner are listed at the end of the proposal.
