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

The real-player snapshot, world model sync and `rivalFigure` are unchanged.

**Part 3: navigation.**

- `gui/ultra/PageRegistry.java`: one entry per page (screen, page pattern, title, parent, staff, searchable,
  keywords). Back, breadcrumbs, search and the probe all read it. `sim/audit_page_registry.py` (run by `build.sh`)
  fails the build when a page the screens declare or link to has no entry; the boot self-test runs
  `PageRegistry.problems()` (every parent resolves, every chain reaches the main menu, search finds every
  searchable page) and `NavHistory.selfTest()`.
- `NavHistory`: per player, up to 32 pages, cleared on logout.
- One `UltraScreenSpec.back()` replaces `footerBackToMain`, `footerBackHome` and `footerBack` (156 calls in 12
  screen files). LM client: `nav:back`, named after the page you came from. Bridge: the registry parent. A system Home,
  and any page whose Back goes to the main menu, shows one "Back to main menu" on the right; deeper pages show Back on
  the left and Main menu on the right. `footerGuide` (with Skip) and `footerBackOnly` stay.
- Breadcrumbs (LM clients whose hello lists `nav`): pages deeper than a system Home, no tab row, three or more crumbs.
  Drawn in the header strip under the title, so no other element moves; each crumb but the last is a link.
- Hub: tabs Home, Search, More. Home has a search row above the tiles (LM clients that can type). Search lists up to
  8 matching pages as buttons with their path, staff also get the matching settings (the config editor's own search),
  and the last 5 searches per player show while the box is empty. Bridge clients see a short line pointing to Home.
- Hub audit: the Goals placeholder tab is gone (old `goals` links open Search; Goals comes back as a real page), the
  dim Skill Check tile is gone, Difficulty has Home/Tiers/Summons for everyone and staff open Combat rating from a
  Home button.

## Proof

Fill-in from the rig runs below (scripted real client, full matrix with the KaiPath stage, mock shots at GUI 2/3/4).

RESULTS_PLACEHOLDER

## Gaps and follow-ups

Registry:

- Titles are static. Back labels for pages whose name is run-time data now read the registry title: the mastery
  wallet's form page Back reads "Back to Mastery line" (was the line's own name, "Back to Big"), the level-up confirm
  reads "Back to Form mastery" (was the form's name). On the LM client the breadcrumb shows the same titles.
- About 27 computed Back destinations lose context on bridge clients: paged lists (the rival profile returns to the
  Rival tab's first page, not the page it came from), per-item confirms that went back to their item, the body
  part unlock prompts (back to the first body parts page). LM clients keep the exact page through the history.
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

- The kit corpus (`tools/ultra-gui-kit/test/fixtures/lm-specs.jsonl`) is refreshed from this release's crawl. The kit
  ignores `breadcrumb` (an unknown layout key), which is the intended bridge behaviour.
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
