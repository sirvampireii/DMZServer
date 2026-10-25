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

## Re-baseline (2026-10-24, integrated 4.6.229 build)

The 25 files were re-extracted from the integrated `LegacyMechanics-4.6.229.jar` (the authored
228.1 source compiled into the repo build). A rebuild of the same source can **never** be
byte-identical to the live 228.1 jar — the ship-version string bump, the CFR-derived codegen
provenance, and javac slot allocation all differ — so the reference pins the **integrated build's**
bytes instead. Semantic parity with live 228.1 was established *before* re-baselining:

- normalized bytecode diff of all 25 classes vs live (offsets/slots normalized) — residuals all classified,
- CFR-decompiled source diff of the six changed shop classes — no logic divergence,
- repo-source ground truth at every divergence point (character-identical to live's render),
- raw `javap -c` traces with real branch targets for ambiguous methods, and
- a JVM-verifier pass over the jar (`java -Xverify:all`, 25/25 reference classes OK, 0 verify failures).

The gate therefore guards the integrated source against drift: a half-integrated edit, a dropped class,
or a toolchain mismatch fails loudly.

## Re-baseline (2026-10-25, hand-written source)

`PrestigePointsSystem`, `GodKiEnhancementShop` and `UltraPrestigeScreens` had been integrated as CFR output,
which dropped the hand-written source the feature audits read. They were restored from the hand-written
4.6.229 source with the shop changes ported in, and these four classes were re-extracted from that build:
`UltraPrestigeScreens`, `GodKiEnhancementShop`, `PrestigePointsSystem`, `PrestigePointsSystem$SkillOffer`.
Parity with the previous reference was checked per method on invoke / field / constant fingerprints
(`javap -c -p`): residuals are codegen only (`List` vs `ArrayList` call sites, a stream lambda vs a loop,
string-concat call-site renumbering, a cached local) plus `pointsForTurnIn` computing the
`exchange` payout inline with the same formula as `PrestigeShopConfig.Exchange.payout`.

## Refresh the reference set

```bash
REF="tools/dmz-adaptive-difficulty/reference/prestige-shop-228.1"
# CLASS_PATHS list lives in sim/audit_prestige_shop_classes.py (25 entries).
# For each <path> in that list: unzip -p "$AUDITED_JAR" <path> > "$REF/<path>"
```

`AdaptiveDifficultyMod.class` carries the ship version string — bumping the ship line changes it and
will trip the gate; re-baseline in the same commit as the version bump. Do not hand-edit `.class`
files; re-extract from an audited jar when the baseline changes. Keep the `CLASS_PATHS` list in
`sim/audit_prestige_shop_classes.py` in sync with the committed files.
