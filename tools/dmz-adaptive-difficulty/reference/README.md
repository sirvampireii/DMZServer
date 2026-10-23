# Live ki/stamina reference

The **`ki-pool-2.4.115/`** directory is a **reference extract** from `LegacyMechanics-2.4.115.jar` for diffing and audits only. **`build.sh` does not overlay these `.class` files** — doing so caused Java 17 `VerifyError` on `StatsData#load` and **Invalid player data** disconnects. Runtime ki/stamina behavior comes from the matching Java sources in this repo (kept aligned with 2.4.115 semantics).

Ship line bumps `AdaptiveDifficultyMod.VERSION`, `mods.toml`, and `plugin.yml` together.

## Refresh the slice from recycle/live jar

```bash
JAR="${LM_REFERENCE_JAR:-/path/to/recycle/LegacyMechanics-2.4.115.jar}"
REF="tools/dmz-adaptive-difficulty/reference/ki-pool-2.4.115"
# same class list as build.sh overlay — extract with unzip -p "$JAR" <path> > "$REF/<path>"
```

Do not hand-edit `.class` files; re-extract from the reference jar when live ki behavior changes.

---

# Prestige-shop (4.6.228.1) reference set

The **`prestige-shop-228.1/`** directory holds the **25 `.class` files** that make up the complete
class-level delta between this repo's `4.6.228` build and the live-staged `LegacyMechanics-4.6.228.1.jar`
(sha256 `f8063db0…`): the **15 new shop classes** (`PrestigeCustomShop`, `PrestigeShopConfig`, `PrestigeShopHooks`
+ their inners) and the **10 modified classes** (`AdaptiveDifficultyMod`, `PrestigeAdminCommandTree`,
`ConfigRegistry`, `ConfigEditor`, `ConfigFieldHelp`, `ProgressionGuiApi`, `UltraPrestigeScreens`,
`GodKiEnhancementShop`, `PrestigePointsSystem` + `$SkillOffer`). The changed resources `MANIFEST.MF`
and `mods.toml` are **excluded** — they legitimately change per build / version bump.

Unlike the ki-pool slice, this set is a **byte-verify reference** (no overlay). `build.sh` runs
`sim/audit_prestige_shop_classes.py` against every built jar and **fails the build** if any of the 25
classes is missing or not byte-identical to the reference. The guard is **gated**: it only activates
once `PrestigeShopConfig.java` exists in `src/` (i.e. the authored 228.1 source has been integrated).
Until then the marker class is absent and the guard stays dormant so the `4.6.228` build remains green.

## Refresh the reference set

After the authored 228.1 source is integrated, built, deployed, restarted, and **verified in-game**,
re-baseline from the verified live jar so future builds compare against the new good state:

```bash
REF="tools/dmz-adaptive-difficulty/reference/prestige-shop-228.1"
# CLASS_PATHS list lives in sim/audit_prestige_shop_classes.py (25 entries).
# For each <path> in that list: unzip -p "$LIVE_JAR" <path> > "$REF/<path>"
```

Do not hand-edit `.class` files; re-extract from an audited live jar when the baseline changes. Keep
the `CLASS_PATHS` list in `sim/audit_prestige_shop_classes.py` in sync with the committed files.
