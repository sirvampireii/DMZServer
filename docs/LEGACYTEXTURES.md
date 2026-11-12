# Legacy Textures (`legacytextures`)

A Forge 1.20.1 mod that carries the HD textures for DragonMineZ and NB Armors, and lets each player
switch between HD and the standard look for their own game. It is a new mod, separate from
LegacyMechanics. Source: `tools/legacytextures/`.

- **Identity:** modId `legacytextures`, version 0.2.5, `displayTest = MATCH_VERSION`.
- **NB Armors is inside (0.2.0).** The jar also declares the mod `nbarmors` (version 0.2.5). See
  [NB Armors inside Legacy Textures](#nb-armors-inside-legacy-textures). The separate
  `nbarmors-0.1.0.jar` must not be installed next to it, because two copies of a mod id will not load.
- **Dependencies:**
  - `legacytextures` needs forge `[47,)` and minecraft `[1.20.1]` exactly. The client hooks target
    1.20.1 SRG names, so another Minecraft must refuse to load, not run with the hooks silently off.
    dragonminez `[2.2,)` is optional and loads first.
  - `nbarmors` needs curios `[5.14.1,)`, with dragonminez optional.
- **Both sides need the jar.** The server keeps each player's choice and the client draws.
- The mod jar is now the source of truth for HD textures, not a server resource pack.

## What players get

`/legacytextures` or the **menu key** opens the texture menu. The key defaults to `]` and can be
changed in Controls → Legacy Textures. All menu, command and status text comes from
`assets/legacytextures/lang/en_us.json`.

| Control | What it changes | When it applies |
|---|---|---|
| All HD / Players HD only / All vanilla | One tap sets all three switches | At once, unless the world switch changes |
| Players and DragonMineZ armor | DMZ bodies, faces, hair, tattoos, forms, DMZ armor, DMZ NPC models | At once (no reload) |
| NB Armors outfits | NB Armors armor and vanity textures (jbra layouts) | At once (no reload) |
| World, items and menus | Block, item and particle textures (atlases), menu art | One texture reload with the loading screen |
| HD set | Which HD set the switches use (only when more than one is installed) | Same as the switches |
| My face (0.2.4) | Your own face animation, for every viewer; the same switch as `/faceanim` | At once, needs the server to run Legacy Textures |
| See blinking / See shouting (0.2.4) | Whether this game draws other players' blinks and shouts (`[faceanim]` config) | At once, only this game |

The same choices can be typed:

- `/legacytextures preset all_hd|players_hd_only|all_vanilla`
- `/legacytextures players|nbarmor|world hd|vanilla`
- `/legacytextures look <set>`
- `/legacytextures status`: your choice, then a health report from your own game:
  - **Texture hooks:** drawing, file checks and models. Each shows "working", "not used yet" (the
    hook never ran, for example because its target changed) or "FAILED".
  - **Sets:** the sets loaded, and whether world HD is loaded.
  - **DMZ caches:** any DragonMineZ cache that could not be cleared, and (0.2.3) whether DragonMineZ
    still held shaded body layers that Legacy Textures had to drop on the last switch.
- `/faceanim on|off|toggle`: your own face animation, for everyone (see Face animation below).

The choice is stored in the player's persisted data. It survives death, dimension changes and
restarts, and is sent to the player on login and after every change. Players who never chose get
`config/legacytextures/server.json` → `defaultPreset`. The default is `ALL_VANILLA`, the look players
have today without a pack.

The mode only changes what **that player's own game draws**; it never changes what anyone else sees.

## Payload (what the jar ships)

Built by `tools/legacytextures/import/import_assets.py` from the two source packs. The source zips
and their checksums are recorded in `import/IMPORT_MANIFEST.json`. To check that the committed
payload still matches the zips, run `import_assets.py --check`.

Every file is an **HD variant** under its own namespace. Nothing ships at a `dragonminez:` or
`nbarmors:` path, so the mod's own file is always the vanilla variant and stays reachable:

```
<ns>:<type>/<rest>   ->   legacytextures:<type>/hd/<ns>/<rest>      (type = textures | geo | animations)
```

| Shipped | Files |
|---|---|
| ZoneMC v2.1 DragonMineZ textures (overrides + 510 pack-only race textures) | 1,090 |
| ZoneMC geo models (7 overrides, 4 pack-only) | 11 |
| ZoneMC master NPC animations | 6 |
| ZoneMC minecraft GUI textures | 4 |
| NB Armors HD overrides of the jar's textures | 1,327 |
| **Total** | **2,438** |

Left out, with the reason recorded for each file:

| Left out | Files | Why |
|---|---|---|
| Byte-identical to the mod jar | 23 + 3 | The fallback already draws the same bytes |
| `dmzanatomy` | 3 | No code references it |
| ZoneMC `lang/en_us.json` | 1 | 400 keys for content 2.2.1.1 does not have, and it renames the quest difficulty "Easy" to "Infernal". Lang files merge globally, so it cannot be per-viewer |
| NB Armors pack-only textures | 336 | nbarmors 0.1.0 never requests them; see below |
| `jinryuudragonbc` NPC textures | 2 | No server mod uses that namespace |

The 6 master animations and 4 of the geos (`glind_female`, `glind_male`, `golden`,
`races/raceparts`) are not referenced by DragonMineZ 2.2.1.1. They ship as asked and are inert:
DMZ uses `masters.animation.json`, and its RaceParts layer uses the jar's own
`geo/entity/raceparts.geo.json`.

The ZoneMC pack has no block or item textures. So the HD set's "world" side today is the 4 menu
textures plus one particle texture. A world set (for example a block pack) can be added as its own set.

### NB Armors: which textures the mod really asks for (checked in bytecode, nbarmors 0.1.0)

- **Armor:** `NbArmorItem.getArmorTexture` → `nbarmors:textures/armor/` + `CatalogEntry.wornTexture(layer)`,
  where layer is head 0, chest 1, legs 2, boots 3.
- **Vanity:** `NbVanityRenderer`, `NbOverlayRenderer` and `DmzOverlayLayer` always use `wornTexture(1)`.
- **`wornTexture(layer)`:**
  - If the entry has `texture`, that file is used.
  - Otherwise the file is `<prefix>_<layer>jbra.png` when `textureLayouts` lists `<layer>jbra`,
    and `<prefix>_<layer>.png` when it does not.
  - The jbra layout always wins. `usesLegacyLayout` has no callers.
- **`LegacyModelBuilder`** builds meshes only; it never picks a texture.
- **Torso chests under DragonMineZ:** the 305 `dmzTorso` chests are `NbDmzArmorItem`, a
  `DbzArmorItem`. They are drawn by DMZ's `DMZCustomArmorLayer` and `DMZRenderHand` through
  `ArmorTextureResolver.resolve("nbarmors", prefix, CHEST)`. That path requests
  `<prefix>_layer1.png`, plus `_damaged_layer1` only when that file exists.

Result: all 1,043 catalog textures and all 305 `_layer1` torso textures are inside the nbarmors jar.

- **Pack-only textures:** the pack's 336 pack-only files are never requested. The 294 listed in
  `textureLayouts` (across 841 entries) are the 1.7.10 non-JBRA layout, which the jbra layout
  always outranks; the other 42 are not listed at all. Nothing needs to ship under `nbarmors:` for
  outfits to render with no HD pack.
- **The `*_layer2.png` / head `*_layer1.png` "gaps":** an earlier count came from applying
  `ArmorTextureResolver`'s pattern to every slot. Only DMZ torso chests use that pattern; legs,
  head and boots use `wornTexture`.

## Face animation (absorbed DMZ Face Anim, 0.2.3)

Eyes blink and the mouth opens into a shout on DragonMineZ faces, for every race DMZ draws a face for
(human/Saiyan, Namekian, Frost Demon, Majin, BioAndroid, Glind, Angel). This was the separate mod
DMZ Face Anim 0.2.0 (`dmzfaceanim-0.2.0.jar`, sha256 7260a2b0...); its owner (the server owner)
relicensed it into Legacy Textures on 2026-11-05, so the GPL-3.0-only license of that jar does not ship.
The code is identifiable in `faceanim/` (`FaceAnimator`, `FaceFlags`, `FaceAnimConfig`) and
`mixin/DMZSkinLayerFaceMixin`; the frames come from `import/import_faceanim.py`
(`FACEANIM_MANIFEST.json`).

- **Blink:** a 90-tick cycle (about 4.5 s) with a per-player offset; half, closed, closed, half.
- **Shout:** while the player charges ki, or charges an action other than fusion; the mouth opens
  over a few ticks and shows `mouth_scream_1..3`, drawn untinted.
- **How:** `DMZSkinLayer.renderFace` marks the face being drawn; each face layer path (eyes, mouth)
  is swapped for its frame and drawn by DMZ's own 11-argument `renderColoredLayer`, so tint, light and
  the HD switch stay DMZ's. Nothing binds a texture or keeps a GL id. Every hook is `require = 0`.
- **Only HD faces animate (0.2.5).** DragonMineZ 2.2.1.2 has no blinking of its own (no `faces/anim`
  files, no blink animation), so a face drawn from DMZ's own file now stays still, as DMZ draws it.
  Before each eye or mouth layer is swapped, `FaceAnimator.route` asks the resolver what this viewer
  draws for that layer. If the answer is DMZ's own file, the layer is drawn as is: no blink frame, no
  shout mouth, no skipped SSJ4 or shiyoken overlay. If it is an HD file, nothing changed. This holds in
  every preset:
  - All HD and Players HD only: faces with an HD file animate.
  - All vanilla: faces stay still, except HD-only faces (for example humansaiyan eyes 13+), which a
    vanilla viewer draws from the HD set as the last resort, so they still animate.
  - Races with no HD face files never animate: Angel (the four LT base eyes and the Angel frames are now
    unused) and most BioAndroid eyes (only 8 BioAndroid face files are HD).
  - `/faceanim`, `enableBlink`/`enableScream`, the 90-tick cycle and the per-player offset are unchanged.
  - If the check throws, the face stays still and one warning is logged.
- **Frames:** 479 files under `dragonminez:textures/entity/races/<race>/faces/anim/` plus the four base
  Angel eyes `angel/faces/angel_eye_0_0..3.png`. They stay in the `dragonminez:` namespace because DMZ
  draws faces by that path; none of them exists in DragonMineZ (the audit checks), so no DMZ file is
  replaced. The Angel art came from the ZoneMC pack's `anjo/` folder and is renamed to DMZ's `angel/`;
  the 30 `anjo_form1..5` frames had no DMZ face and were dropped.
- **Each player's own switch:** `/faceanim on|off|toggle` (no argument shows the setting). It changes
  that player's face for every viewer, is stored in their persisted data on the server (default on),
  and is sent on login, when a player comes into view and on every change (packet `FaceAnimFlag` on
  the Legacy Textures channel, protocol 3). This needs the server to run this jar.
- **What a viewer sees:** `config/legacytextures-client.toml`, section `[faceanim]`: `enableBlink`
  and `enableScream` (both default `true`). Turning one off changes only that game.
- **In the menu (0.2.4):** the bottom row of the texture menu holds both. "My face" shows the switch
  the server sent (on until it says otherwise) and a click runs `/faceanim on|off`, so the store,
  the chat reply and the update to every viewer are the command's own; it is greyed out when the
  server has no `/faceanim`. "See blinking" and "See shouting" write `enableBlink`/`enableScream`
  and save the file; the next frame uses them. The menu redraws when any of the three changes, so a
  typed `/faceanim` shows there too.
- The animation and the body fixes are client-side; only `/faceanim` needs the server.

## NB Armors inside Legacy Textures

Since 0.2.0, Legacy Textures carries NB Armors. The owner approved absorbing the port on 2026-11-05.
The original is "Ported from NB Armors 3.1 by Naruke", All Rights Reserved.

- **Code:** all 44 classes of `nbarmors-0.1.0.jar` were decompiled once (Vineflower 1.10.1) into
  `com.dbzlegacy.legacytextures.nb`. The only change is the package name, plus one raw-type cast
  that javac needs to compile the decompiled source. `audit/audit_nb_fidelity.py` (run by `build.sh`
  when `NB_JAR` is set) checks that every class keeps exactly the original members.
- **Resources:** copied verbatim by `import/import_nbarmors.py`, with `--check` to compare against
  the jar. The jar's sha256 is in `import/NB_MANIFEST.json`. 3,466 files:
  - 2,392 armor and item textures
  - 1,043 item models
  - the lang file
  - 24 legacy model geometries
  - the catalog and tabs JSON
  - the Curios data: the `vanity` slot, its entity binding, and the `curios:vanity` / `curios:head_tech` tags
- **Namespace:** everything keeps `nbarmors:` (items, textures, models, lang keys). The code still
  runs as its own mod `nbarmors`, the second `[[mods]]` entry in this jar, so its registrations
  belong to that namespace with no Forge "alternative prefix" warnings. Legacy Textures' NB switch
  and the transparent-instead-of-checkerboard safety net both key on `nbarmors:`.
- **DragonMineZ touchpoints:** the compat layer uses two things from DMZ, and `build.sh` checks both
  against the live DMZ 2.2.1.1 jar.
  - `DbzArmorItem`'s `(material, type, properties, modId, itemId)` constructor, used by `NbDmzArmorItem`
    for the 305 torso chests.
  - `DMZPlayerRenderer` being a GeckoLib `GeoEntityRenderer`, used by `DmzClientCompat` to add its
    two layers.
  The compile is against that jar too, so a moved constructor fails the build.
- **Worn textures:** the question is settled; see the bytecode notes above. All 1,043 catalog entries
  resolve to files in the jar, and there is no catalog fix.

## Texture sets

`config/legacytextures/texture_sets/*.json`. These are written on first client start if the folder
has no JSON yet:

```json
{ "id": "hd", "name": "HD (ZoneMC v2.1 + NB Armors HD)", "namespace": "legacytextures",
  "basePath": "hd", "categories": ["players", "armor", "gui", "world"], "fallback": "vanilla" }
```

- A set keeps `<ns>:<type>/<rest>` at `<namespace>:<type>/<basePath>/<ns>/<rest>`.
- `vanilla` has a blank namespace and means the mods' own files. It always exists, even if its JSON
  is deleted.
- A file a set lacks comes from its `fallback` set, and in the end from vanilla, never a missing
  texture. A fallback loop is stopped.
- **Vanilla falls back to HD (0.2.2).** In vanilla mode the mods' own file always wins. For a file the
  mods do not have at all (for example DMZ ships human/Saiyan male body types 1-2 only; 3-7, `tattoo_6` and female body type 3
  are HD-only), the viewer's HD set is the last resort. Before 0.2.2 such a character showed a vanilla
  viewer only a floating head: DMZ's `getSafeTexture` fell back to `bodytype_male_0_layer1.png`, which
  exists nowhere, and the bind hook drew it transparent. A file missing from both still draws
  transparent.
- `categories`:
  - `players`: `dragonminez:textures/entity|armor`, `geo`, `animations`.
  - `armor`: everything in `nbarmors:textures`.
  - `gui`: `textures/gui` in dragonminez and minecraft.
  - `world`: every other dragonminez and minecraft texture.
- The players switch uses `players`, the NB Armors switch uses `armor`, and the world switch uses
  `world` and `gui`.
- A set's namespace must be its own (not blank, dragonminez, nbarmors or minecraft). A set that
  breaks this is refused with a log warning.

**Adding a set (no code change, no rebuild):**

1. Drop `<id>.json` into `texture_sets/`.
2. Put its files in `texture_sets/<id>/assets/<namespace>/...`, laid out like a resource pack.
3. Press F3+T or restart. The set appears in the menu's HD set picker.

A JSON can also point at files another mod or pack already provides.

### Cross-race aliases (0.2.5)

`config/legacytextures/aliases.json` lets a race with no HD files of its own borrow a donor race's HD
file. The file is written from the jar's copy when it is missing; adding or removing an entry needs
F3+T or a restart, no code change.

```json
{ "aliases": [ { "from": "dragonminez:textures/entity/races/<race>/",
                 "to":   "dragonminez:textures/entity/races/<donor>/" } ] }
```

- **When it applies:** in HD mode, after every set in the chain misses the exact file and before the
  mod's own file. The donor file is the one at the same relative path under `to`, looked up in the same
  sets. Vanilla mode never aliases.
- **Only files the race ships.** A race texture that does not exist at all is not borrowed, so an alias
  never adds an option to the character creator, and vanilla and HD viewers still agree on what exists.
- **Only player textures.** `from` and `to` must be `dragonminez:textures/...` player paths. Faces
  (`/faces/`), geo, `dmzultra:` and other namespaces are refused with a log warning; eyes stay as the
  race ships them (and, per the face animation rule, stay still).
- **Misses:** when the donor has no HD file, the race's own file is drawn and the miss is logged once
  per file per game (`alias: no HD donor ...`).
- **Shipped table: empty.** The proposed taimujin → humansaiyan and angel → humansaiyan entries were
  dropped. DMZ Ultra names those races' bodies `<race>[_<form>]_<gender>_<n>_layer1..3` (+ emissive),
  while the humansaiyan HD files are `bodytype_<gender>_<n>[_layer1].png`. Not one of the 153 taimujin
  or 121 angel body files (Ultra 0.5.5) has a humansaiyan HD file at the same path, so the entries
  would borrow nothing. A usable alias needs a donor whose files share the race's names and layer
  layout. A Frost Demon geometry gap or a `dmzultra:` race is not an alias case.

## How it draws (client)

Three hooks, all fail-soft (`require = 0`, try/catch, one log line if a hook ever throws):

| Hook | Target (checked against) | What it does |
|---|---|---|
| `TextureManagerMixin` | `TextureManager.getTexture(RL)`, `getTexture(RL, AbstractTexture)`, `bindForSetup` (MC 1.20.1, SRG) | Binds the active set's file. HD and vanilla are different keys, so both stay loaded and a switch is instant. A DMZ or NB texture missing from every set draws transparent instead of the purple-black checkerboard. |
| `ReloadableResourceManagerMixin` | `ReloadableResourceManager.getResource` (MC 1.20.1, SRG), client manager only | Answers "does this file exist / read it" from the active set. See the list below. |
| `GeoModelMixin` | GeckoLib `GeoModel.getBakedModel(RL)` (geckolib-forge-1.20.1-4.8.4; skipped if GeckoLib is absent) | Uses the HD model when GeckoLib baked one. GeckoLib bakes every `geo/` file of every namespace at each resource reload, the 11 HD geos included, so both variants are always in its cache and switching is instant. `DMZPlayerModel` overrides `getBakedModel` but calls `super` for every steady-state model, so it goes through this hook. Only DMZ's transformation blends (`ModelMorph`, a few frames during a transform) read GeckoLib's cache directly and use the standard geometry. The client probe checks the drawn geometry (cube counts) of `master_roshi` and `frostdemon_fifth` in each mode and across three toggles. |

The resource-manager hook covers every place DMZ 2.2.1.1 checks or reads a file before drawing:

- `TextureCounter` (the character creator's option counts)
- `DMZSkinLayer.getSafeTexture`
- `DMZPlayerModel.fileExists`
- `ArmorTextureResolver.textureExists`
- `SkinGathererProvider` (shaded layers read the image)
- `DBSagaModel`

**The selection-skew guard comes from this.** A viewer's creator only offers options their own game
can draw, and every validated layer agrees with what the bind hook draws. Since 0.2.2 a vanilla
viewer's game can draw the HD-only options too (through the fallback above), so `TextureCounter`
counts and offers them in vanilla mode as well: the creator shows the same option counts in both
modes.

There are **no mixins into DMZ or NB Armors classes**. DMZ builds its paths as hardcoded
`dragonminez:` strings and has no resolver hook to use. Redirecting at the two vanilla lookup
points covers all of those strings without depending on DMZ method names.

**Every texture key holds its own file.** Texture reloads read through a separate per-reload
manager. While `TextureManager.loadTexture` runs, the lookup hook stands aside. So
`dragonminez:...png` is always the DMZ file and its HD variant is a different key, even when a key is
first loaded while HD is on. Without this, switching back to vanilla would draw the HD image under the
vanilla key.

**GeckoLib ordering.** GeckoLib has its own HEAD hook on `getTexture` (`wrapAnimatableTexture`). It
registers, and for non-animated textures removes again, any key not yet loaded. The bind hook runs at
mixin priority 900, so in the merged method it comes before GeckoLib's hook (default 1000), and GeckoLib
only ever sees the key that is drawn. In the other order, GeckoLib re-loads the undrawn key on every
bind, and for a missing file it logs "Failed to load texture" every frame. The client probe checks the
log stays clean, and the order was confirmed by exporting the merged class (`-Dmixin.debug.export=true`).

**Cache discipline.** On every mode or set change, before the next frame, `CacheFlush` empties each
DMZ cache that remembered an answer. It is checked against 2.2.1.1, by name, fail-soft, and the build
audits that every target exists in the DMZ jar:

- `TextureCounter.clearCache()` (also clears `DMZSkinLayer.VALIDATED_TEXTURES_CACHE`)
- `DMZSkinLayer.clearValidatedTexturesCache()`
- `SkinGathererProvider.clearShadedLayers()`, then (0.2.3) `SkinGathererProvider.SHADED_LAYERS` emptied
  by reflection as well. DMZ builds a shaded body (Majin, Frost Demon, BioAndroid) once per
  `dragonminez:` layer path and keeps it in that map; the `dragonminez:shaded/...` keys never go
  through the resolver, so a layer left in the map keeps an HD body after a switch to vanilla. The
  call stays first because it also frees the layers' GL textures. If the map still held layers after
  the call, the log warns with the switch and `/legacytextures status` says so.
- `ArmorTextureResolver.clearCache()`
- `DMZPlayerModel.MODEL_RESOLUTION_CACHE` / `FILE_EXISTS_CACHE`
- `DMZRendererCache.clear()`
- `DBSagaModel.clearCache()`

The resolver's own cache is rebuilt at the same moment.

**World HD.** One built-in pack (`legacytextures_sets`, always on, hidden) serves the set folders.
When world HD is on, it also serves the active set's world textures at their original paths, so the
block and item atlases pick them up. Turning world HD on or off runs one `reloadResourcePacks()`
(the normal loading screen).

## Build and test

```
bash tools/legacytextures/build.sh            # DMZ_JAR=<dragonminez jar> to audit against a specific DMZ
```

- **Compile:** a clean compile against the Forge 1.20.1-47.4.10 client.
  - The client SRG jar and the Forge client jar come from the CurseForge install's `libraries/`.
    Set `CLIENT_LIBS` to use another install.
  - Everything else comes from the repo's `libraries/` and `mods/`.
- **Mixin audit:** `scripts/audit_mixin_targets.py`.
- **Resolver unit tests:** `test/ResolverTest.java`.
- **Jar audits:**
  - The inner-class and referenced-class scan.
  - The manifest and mixin config check.
  - `audit/audit_legacytextures.py`: identity and dependencies, assets only under `legacytextures:`
    and `nbarmors:` plus the face frames under `dragonminez:.../faces/` (exactly the
    `FACEANIM_MANIFEST.json` list, none present in the DMZ jar), no lang files except the two own
    `en_us.json`, the payload matches the manifest, the set files, mixin method visibility, no raw `§`,
    every DMZ cache target present (including `SHADED_LAYERS`), the four `DMZSkinLayer` methods the
    face mixin hooks, and GeckoLib's `GeoModel` has exactly one `getBakedModel` overload.
  - With `FA_JAR=<dmzfaceanim-0.2.0.jar> ZONE_ZIP=<DMZ HD Plus.zip>`, the face frames are checked
    byte for byte against their sources (`import/import_faceanim.py --check`).
- **HD hair cap (0.2.1):** the ZoneMC `hair_base.png` hung lower over the forehead than DMZ's and
  showed as a second flat hair under the 3D strands. `import/fit_hair_cap.py <dragonminez jar>` fits it
  to DMZ's 64x64 footprint (248 cells, rows 16-18 clear); `--check` only reports. Rerun it after
  `import_assets.py` re-imports the HD set (`IMPORT_MANIFEST.json` lists it under `local_edits`).
- **Version pins:** `LegacyTextures.VERSION`, both `mods.toml` entries, `MANIFEST.MF` and the boot
  banner (`Legacy Textures <VERSION> loaded`) must agree.
- **NB Armors:**
  - The DMZ touchpoints are checked with javap against `DMZ_JAR`, which defaults to
    `tools/dmz-godki-patch/out/dragonminez-2.2.1.1.jar`, the live jar.
  - With `NB_JAR=<nbarmors-0.1.0.jar>`, the build also checks that the resources are byte-identical
    and the classes keep their members.

`[LT_SERVER=host:port] test/clientprobe/run.sh <game dir> <dmz jar>` starts a real Forge client
offline. It uses a minimal mod set (DMZ, GeckoLib, Curios, TerraBlender, Legacy Textures, and a
test-only probe mod), with **no nbarmors jar** and no HD resource pack.

At the menu the probe walks every preset through the real hooks:

- NB Armors absorbed: 1,043 `nbarmors:` items, the creative tabs, item models, lang and the menu key;
- the DMZ creator's tattoo count;
- the drawn GeckoLib geometry;
- a world reload;
- three toggles;
- a third set dropped in as one JSON;
- every NB texture the catalog requests;
- the status report.

With `LT_SERVER`, the probe then joins that server and checks the following on a DMZ player, taking a
screenshot in each mode:

- An NB angel chest, aeos legs and boots, and an aeos head in the Curios vanity slot. The server hands
  these out when the probe writes `ltprobe-need-gear`.
- The NB textures drawn standard, then HD with no reload.
- `/legacytextures status`.
- `/legacytextures world hd`: its chat warning and exactly one reload.
- Face animation (0.2.5): with players vanilla, a vanilla face stays open through the whole blink
  window, the mouth does not shout and Angel eyes stay still, while an HD-only face (humansaiyan eye 13)
  still blinks. Then `/legacytextures players hd` (stored by the server, so the relogin keeps it) and
  the blink, shout, `/faceanim` and menu checks from 0.2.3/0.2.4 run on HD faces.

`ResolverTest` (run by `build.sh`) also covers the alias rules: the shipped table is empty and valid;
face, geo and `dmzultra:` entries are refused; a shipped file borrows the donor's HD file in HD mode
only; a missing donor draws the race's own file and is logged once; a file the race lacks is not borrowed.

## Deploy notes

- Ship `legacytextures-<version>.jar` in the **modpack** and in the server's `mods/`. Since 0.2.0 it
  carries NB Armors, so **no `nbarmors-*.jar`** goes anywhere: not live, not test, not the modpack.
  The separate jar was never deployed, so no player holds its items.
- Live `mods/` should have exactly one Legacy Textures file. 0.2.0 replaces the
  `legacytextures-0.1.0.jar.pending` staged there; that one is never renamed.
- A client with a different Legacy Textures version can still join. `displayTest = MATCH_VERSION` only
  marks the server red in the server list; Forge's join check compares the network channel version
  (`LtNetwork.PROTOCOL`, "3" since 0.2.4) and the synced registries (the NB Armors items). While
  those stay the same, the modpack can update after the server. 0.2.4 clients join a 0.2.5 server and
  keep their own client behaviour (every face blinks). Bumping `PROTOCOL` or adding or removing
  items locks out older clients, so that release needs the modpack and server to switch together.
- Curios is a hard dependency (`[5.14.1,)`, NB Armors' vanity slot): keep Curios 5.14.1 in the server
  `mods/` and in the client modpack.
- Do not ship 0.2.x until the owner decides the NB Armors license question.
- The old `dbzlr-texture-pack-assets-0.2.2.jar` is a code-less snapshot of a different pack lineage
  (no overlap with ZoneMC v2.1). Leave it in place until Legacy Textures is live, then remove it.
- The live server resource pack (Halloween Redux, minecraft namespace only) is unaffected.
- Players who still install the ZoneMC zip as a client resource pack override the `dragonminez:`
  paths directly. That brings back the old per-viewer mismatch, so tell players to remove it once
  Legacy Textures ships.
