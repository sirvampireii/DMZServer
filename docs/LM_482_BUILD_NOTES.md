# LM 4.8.2 build notes

Dojo banner editor, the banner in the hall and on every dojo screen, one free dojo rename, and the Legacy
Mechanics client is now required.

| | |
|---|---|
| Branch | `lm-482-banners-mandatory` (off `main` at 4a31b197, which has 4.8.1; the brief said 66c3dea8, but 4.8.1 had merged since, and a 4.8.2 below it would conflict with its version and audit changes) |
| Manifest row | `lm-482-banners-mandatory`, checks in `§ lm 4.8.2` |
| Probes | `V482BannerWorld` (Part B), `V482BannerUi` (Parts A, C, D), run after `v88DojoRules` |
| Release check | `DMZRig/results/release-4.8.2`: **436 PASS, 0 FAIL**, 0 matrix rows failing |

## A. Banner editor (`gui/ultra/DojoBannerEditor`, `BannerDesign`)

- The banner page (`dojo_banner`) opens with **Design a banner here**, then up to 4 carried banners (copying a
  carried banner still works; it is an alternative, not a requirement).
- The editor (`dojo_banner_edit`) builds a vanilla banner: a base colour (16 dyes) and up to 6 layers, each a pattern
  and a dye. Add a layer (pattern page, then colour page), open a layer to move it up or down, recolour or remove it,
  Start over. Add is dim at 6 layers.
- Patterns come from the vanilla banner pattern registry (`BuiltInRegistries.BANNER_PATTERN`), every one except the
  plain base: 40 on 1.20.1, including the ones a loom needs a pattern item for. Names are plain English ("Cross",
  "Diagonal cross", "Border", "Bottom stripe"); a pattern missing from the table shows its registry path.
- The live preview is drawn from exactly the list Save writes: the icon `lmbanner:<base>/<code>:<dye id>,...` and,
  large, the banner cloth drawn as the loom draws its result (`client/BannerIcons`). Every choice button shows the
  banner it would make.
- Save calls `DojoService.setBanner` with the base and the ordered list, in the stored form (`bannerBase` a dye name,
  each layer `<hashname>:<dye id>`, as the carried-banner copy always wrote). The cost rule is unchanged: the first
  banner is free and starts no wait; later changes cost 20 Dojo Points and start the shared 12-hour wait. Save is dim
  with the reason during the wait, when points are short, or when nothing changed.

## B. The hall flies the banner (`dojo/DojoState`, `DojoPlacer`, `DojoWorld`)

- The build signature gains a banner part (`|b=<hash of the design>`), so a banner change marks the hall stale.
  `setBanner` now calls `DojoWorld.ensureBuilt`, the same rebuild a style change uses.
- Every banner the hall's structures place (found from the template palette at placement time, wall and standing,
  every tier and style) is placed in the picked base colour with the same facing, and its block entity gets the
  picked layers in order. Built-hall inspection (`DojoWorld.bannersOn`) checks every spot after a build.
- No banner picked: the style's own banners stay exactly as the structures place them (they vary by tier; the
  `t*_none` halls have none).
- Upgrade: a hall built before 4.8.2 has no banner part. With no pick it is not rebuilt (the stored signature is
  rewritten in place); with a pick it rebuilds once, the next time it is used (visit, spar, spectate, upgrade).

## C. Other players see the banner

- Dojo list and the dojo leaderboard (same page): each dojo's button carries its banner icon; the list now shows
  4 dojos a page so it fits 300 px with the pager, and a row drops the member count, then the master's name, rather
  than wrap.
- Dojo view: a Banner line after Wars, beside a large banner picture.
- War pick (6 a page), war format ("Their banner", with the picture) and war view (each side with its banner icon).
- Banner text names the patterns where it fits ("Blue with Cross (white), Border (black)", else "Blue, 4 layers").
- No pick: the style default ("default (Red)" and its plain banner).
- The picked design in `dojos.json` is the only source for display. The legacy `DojoProfile.bannerMaterial` and its
  data are kept but no longer read for display.

## D. One free dojo rename

- `DojoStore.Dojo.freeRenameUsed` (missing in old data, so false: every existing dojo has its free rename).
- The first rename is free, works during the 12-hour wait and starts none. After it, a rename costs 20 Dojo Points
  and starts the shared wait, as before. Validation, master-only and the same-name check are unchanged.
- Rename page shows "Free"; the settings button reads "Rename (free)" (open during the wait and with no points);
  the settings lock line says the free rename locks nothing.
- `/lm dojo name` (and the legacy `dojo_name` action) now goes through the same rename, so it is no longer an
  unlimited free rename. A rename updates the leaderboard name too: the ladder shows the dojo's own name.

## E. The LM client is required

- `requireLmClient` now defaults to **true** (still a config switch). A player without the LM client is turned away on
  join: "This server needs the Legacy Mechanics client mod. Add LegacyMechanics-4.8.2.jar to your mods folder, then
  join again."
- The KaiPath bridge is removed: no `legacymechanics:gui` channel, no bridge route (`NO_CLIENT` instead), no bridge
  skin rewrite, no KaiPath feature model (`UltraClientFeatures`, `GuiFeaturesPacket`), no KaiPath LM-consumer mixin,
  no `kaipath` dependency in `mods.toml`. KaiPath alone no longer opens LM menus.
- The body-parts world sync now goes to the LM client on its own channel (message 5, `bodyPartsSync` in the hello),
  and the LM client draws the synced pieces in the world.
- HUD: boss bars and chat lines remain only for a player who turned the HUD off, and during the few seconds before
  the client's hello arrives.
- `guiBackend = "ultra"` now means the LM client's menus.
- The rig's kai stage became the reject check: a KaiPath-only client is turned away by the shipped default.

## Release check

`DMZRig/results/release-4.8.2`: build OK (every build.sh audit), **436 PASS, 0 FAIL**, 0 matrix rows failing, strict
boot self-tests OK (19), jar sha256 `d8b15436edc047161928ae4fa38872ee8481a1d23b4829fe78dffefead554daa`. The first run
(`release-4.8.2-run1`, 420/15) found:

- Five 4.7.78 checks outdated by approved 4.8.2 changes (banner text now names patterns; the first rename is free).
  The V78 script now spends the free rename first and then runs the original paid-rename checks unchanged.
- Probe bugs in `V482BannerUi` (it matched unqualified button ids, searched wrapped lines, removed the wrong layers).
- Three pages over 300 px, fixed in code: the dojo list with a pager (now 4 a page) and the banner editor with a
  notice (shorter card and notices).

## Deploy

- **This version turns away every player without the LM client mod.** Ship `LegacyMechanics-4.8.2.jar` to the client
  modpack before or with the live upload. Players on KaiPath only can no longer join.
- An LM client older than 4.8.2 is let in (the gate checks the client is present, not its version) but gets no
  world body-parts sync. Ship the 4.8.2 jar to clients.
- Check the live `legacymechanics.json` for an explicit `"requireLmClient": false`; if present it keeps the old
  behaviour (and there is no bridge any more for those players).
- Full restart after the jar change.

## Still owed (in game)

- A player without the LM client is turned away on the live join; with it, menus and the HUD open.
- The banner editor on a real client: the preview cloth and the small banner icons look right.
- A hall after a banner change shows the picked banners; an existing hall with a pick rebuilds once.
- The free rename, then a paid one, on a real dojo.
