# Legacy Textures (`legacytextures`)

A Forge 1.20.1 mod that carries the HD textures for DragonMineZ and NB Armors, and lets each player
switch between HD and the standard look for their own game. It is a new mod, separate from
LegacyMechanics. Source: `tools/legacytextures/`.

- **Identity:** modId `legacytextures`, version 0.1.0, `displayTest = MATCH_VERSION`.
- **Dependencies:** forge `[47,)` and minecraft `[1.20.1,1.21)`. dragonminez `[2.2,)` and nbarmors
  `[0.1.0,)` are optional and load first.
- **Both sides need the jar.** The server keeps each player's choice and the client draws.
- The mod jar is now the source of truth for HD textures, not a server resource pack.

## What players get

`/legacytextures` opens the texture menu.

| Control | What it changes | When it applies |
|---|---|---|
| All HD / Players HD only / All vanilla | One tap sets all three switches | At once, unless the world switch changes |
| Players and DragonMineZ armor | DMZ bodies, faces, hair, tattoos, forms, DMZ armor, DMZ NPC models | At once (no reload) |
| NB Armors outfits | NB Armors armor and vanity textures (jbra layouts) | At once (no reload) |
| World, items and menus | Block, item and particle textures (atlases), menu art | One texture reload with the loading screen |
| HD set | Which HD set the switches use (only when more than one is installed) | Same as the switches |

The same choices can be typed:

- `/legacytextures preset all_hd|players_hd_only|all_vanilla`
- `/legacytextures players|nbarmor|world hd|vanilla`
- `/legacytextures look <set>`
- `/legacytextures status`

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

## How it draws (client)

Three hooks, all fail-soft (`require = 0`, try/catch, one log line if a hook ever throws):

| Hook | Target (checked against) | What it does |
|---|---|---|
| `TextureManagerMixin` | `TextureManager.getTexture(RL)`, `getTexture(RL, AbstractTexture)`, `bindForSetup` (MC 1.20.1, SRG) | Binds the active set's file. HD and vanilla are different keys, so both stay loaded and a switch is instant. A DMZ or NB texture missing from every set draws transparent instead of the purple-black checkerboard. |
| `ReloadableResourceManagerMixin` | `ReloadableResourceManager.getResource` (MC 1.20.1, SRG), client manager only | Answers "does this file exist / read it" from the active set. See the list below. |
| `GeoModelMixin` | GeckoLib `GeoModel.getBakedModel(RL)` (geckolib-forge-1.20.1-4.8.4; skipped if GeckoLib is absent) | Uses the HD model when GeckoLib baked one. Both are baked at reload, so switching is instant. |

The resource-manager hook covers every place DMZ 2.2.1.1 checks or reads a file before drawing:

- `TextureCounter` (the character creator's option counts)
- `DMZSkinLayer.getSafeTexture`
- `DMZPlayerModel.fileExists`
- `ArmorTextureResolver.textureExists`
- `SkinGathererProvider` (shaded layers read the image)
- `DBSagaModel`

**The selection-skew guard comes from this.** A viewer's creator only offers options their own game
has, and every validated layer agrees with what the bind hook draws.

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
- `SkinGathererProvider.clearShadedLayers()`
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
  - `audit/audit_legacytextures.py`: identity and dependencies, assets only under `legacytextures:`,
    no lang, the payload matches the manifest, the set files, mixin method visibility, no raw `§`,
    and every DMZ cache target present.
- **Version pins:** `LegacyTextures.VERSION`, `mods.toml` and `MANIFEST.MF` must agree.

`test/clientprobe/run.sh <game dir> <dmz jar> <nbarmors jar>` starts a real Forge client offline. It
uses a minimal mod set (DMZ, GeckoLib, Curios, TerraBlender, NB Armors, Legacy Textures, and a
test-only probe mod), with no HD resource pack. At the menu the probe walks every preset through the
real hooks, then quits the game:

- the DMZ creator's tattoo count;
- a world reload;
- three toggles;
- a third set dropped in as one JSON;
- every NB texture the catalog requests.

## Deploy notes

- Ship `legacytextures-<version>.jar` in the **modpack** and in the server's `mods/`, together with
  `nbarmors-0.1.0.jar`. Neither is on live yet.
- `MATCH_VERSION`: clients need the same jar as the server.
- The old `dbzlr-texture-pack-assets-0.2.2.jar` is a code-less snapshot of a different pack lineage
  (no overlap with ZoneMC v2.1). Leave it in place until Legacy Textures is live, then remove it.
- The live server resource pack (Halloween Redux, minecraft namespace only) is unaffected.
- Players who still install the ZoneMC zip as a client resource pack override the `dragonminez:`
  paths directly. That brings back the old per-viewer mismatch, so tell players to remove it once
  Legacy Textures ships.
