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

## Re-baseline (2026-10-25, 4.6.230 ship-line bump)

The repo at `33c1dc2b` built to a jar whose 562 entries are byte-identical to the audited
`LegacyMechanics-4.6.229-2` jar (sha256 `04594df2…`, 1,632,944 bytes). That jar's
`exchange.payoutOverrides` / `bonusGroupSize` wiring in `pointsForTurnIn` and its loop-based
`shopSkillIds` are part of this reference set. The ship line moved to **4.6.230**, because
two different jars were already labelled 4.6.229. Only `AdaptiveDifficultyMod.class` was
re-extracted. Its sole difference from 229-2 is the `VERSION` constant (`javap -v` diff:
constant pool #163 plus the matching `ConstantValue` / `ldc`). The other 24 reference classes
are unchanged, so they are still the 229-2 bytes.

## Re-baseline (2026-10-25, 4.6.232 config editor + UI consistency)

Three classes were re-extracted from the 4.6.232 build, all deliberate changes:
`AdaptiveDifficultyMod` (version constant only), `ConfigEditor` (per-save backups, reload
report, save refusal reasons, enum options, list-entry removal, live-editor conflict checks), and
`UltraPrestigeScreens` (javap-verified: the disabled-system line gains "Ask staff to turn it on."
and the staff "Edit shop" button is red instead of dim). The other 22 are unchanged.

## Re-baseline (2026-10-25, 4.6.233 audit follow-ups)

Two classes re-extracted from the 4.6.233 build: `AdaptiveDifficultyMod` (version constant only,
javap-verified) and `ConfigEditor` (raw section signs routed through UltraStyle, same-millisecond
backups get a unique name, pending changes carry their tab and setting path for the Review
"Last changed by" line). The other 23 are unchanged.

## Re-baseline (2026-10-25, 4.6.234 no automatic race tail)

Only `AdaptiveDifficultyMod` was re-extracted (version constant only, javap-verified). The
body-part change lives in classes outside this set; the other 24 are unchanged.

## Re-baseline (2026-10-25, 4.6.235 UI audit fixes)

Three classes re-extracted, all javap-verified: `AdaptiveDifficultyMod` (version constant only);
`UltraPrestigeScreens` (the empty skill-shop line gains its next step); `ProgressionGuiApi` (`handleDo`
result strings now read UltraStyle fields instead of section-sign literals - same rendered text; the
new field references grew the constant pool, so a few unrelated `ldc` became `ldc_w` with identical
constants). The other 22 are unchanged.

## Re-baseline (2026-10-25, 4.6.236 /lm revive + nemesis toggle removal)

Only `AdaptiveDifficultyMod` was re-extracted (version constant only, javap-verified). The
command and Character Services changes live outside this set; the other 24 are unchanged.

## Re-baseline (2026-10-25, 4.6.237 Android tab)

Only `AdaptiveDifficultyMod` was re-extracted (version constant only, javap-verified). The
Android tab changes live outside this set; the other 24 are unchanged.

## Re-baseline (2026-10-25, 4.6.238 title descriptions)

Only `AdaptiveDifficultyMod` was re-extracted (version constant only, javap-verified). The
title changes live outside this set; the other 24 are unchanged.

## Re-baseline (2026-10-25, 4.6.239 Wither / Warden summons)

Three classes re-extracted: `AdaptiveDifficultyMod` (version constant only), `ConfigRegistry` (the
four new summon field names in the progression field list - javap shows only those strings), and
`ConfigFieldHelp` (four new help lines). The other 22 are unchanged.

## Re-baseline (2026-10-25, 4.6.240 Rival requests)

Only `AdaptiveDifficultyMod` was re-extracted (version constant only, javap-verified). The
Rival changes live outside this set; the other 24 are unchanged.

## Re-baseline (2026-10-25, 4.6.241 Rival profile + Nemesis KO count)

Only `AdaptiveDifficultyMod` was re-extracted (version constant only, javap-verified). The
Rival changes live outside this set; the other 24 are unchanged.

## Re-baseline (2026-10-25, 4.6.242 cleanup kill credit)

Only `AdaptiveDifficultyMod` was re-extracted (version constant only, javap-verified). The
MobScaling change lives outside this set; the other 24 are unchanged.

## Re-baseline (2026-10-25, 4.6.243 donator node + revive others)

Three classes re-extracted: `AdaptiveDifficultyMod` (version constant only), `ConfigRegistry`
(`skillCheckPermission` replaced by `donatorPermission` and `reviveOthersPermission` in the
progression field list - javap shows only those strings and the switch rebuilt around them), and
`ConfigFieldHelp` (the matching help lines; the text block grew one line). The other 22 are unchanged.

## Re-baseline (2026-10-25, 4.6.244 donator sub-nodes)

Three classes re-extracted: `AdaptiveDifficultyMod` (version constant only), `ConfigRegistry`
(`selfRevivePermission` and `terminalPermission` added to the progression field list - javap
shows only those two new strings), and `ConfigFieldHelp` (their help lines plus the shorter
`donatorPermission` line). The other 22 are unchanged.

## Re-baseline (2026-10-25, 4.6.245 Proving Grounds bar)

Only `AdaptiveDifficultyMod` was re-extracted (version constant only, javap-verified). The
grounds bar lives outside this set; the other 24 are unchanged.

## Re-baseline (2026-10-25, 4.6.246 Proving Grounds bonus parity)

Only `AdaptiveDifficultyMod` was re-extracted (version constant only, javap-verified). The
grounds bonus changes live outside this set; the other 24 are unchanged.

## Re-baseline (2026-10-25, 4.6.247 death penalty bar)

Only `AdaptiveDifficultyMod` was re-extracted (version constant only, javap-verified). The
penalty bar change lives outside this set; the other 24 are unchanged.

## Re-baseline (2026-10-26, 4.6.248 GUI audit)

Two classes re-extracted: `AdaptiveDifficultyMod` (version constant only) and
`ProgressionGuiApi` (three boost error strings reworded from "Invalid ..." - javap shows only
those string-concat recipes changed, no code). The other 23 are unchanged.

## Re-baseline (2026-10-26, 4.6.249 summon loot bonus)

Three classes re-extracted: `AdaptiveDifficultyMod` (version constant only), `ConfigRegistry`
(`summonLootBonusPerTierPercent` added to the progression field list - javap shows only that new
string), and `ConfigFieldHelp` (its help line in the text block). The other 22 are unchanged.

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
