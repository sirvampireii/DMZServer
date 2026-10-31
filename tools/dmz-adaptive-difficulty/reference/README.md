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

## Re-baseline (2026-10-26, 4.6.249 GUI follow-ups)

`ProgressionGuiApi` re-extracted: staff progression toggle replies now call
`ProgressionSystem.flagLabel` for the display label and three usage strings were reworded -
javap shows only that call, those strings, and the shifted offsets after them. The other 24
are unchanged.

## Re-baseline (2026-10-26, 4.6.250 kit duels + dodge counter)

Three classes re-extracted: `AdaptiveDifficultyMod` (version constant only), `ConfigRegistry`
(`aiField` now groups every `adaptiveAi*` field plus `enableDodgeCounter` / `dodgeCounterTier` -
javap shows only that method changed), and `ConfigFieldHelp` (five new help lines). The other 22
are unchanged.

## Re-baseline (2026-10-26, 4.6.251 Viper's Call balance)

Only `AdaptiveDifficultyMod` was re-extracted (version constant only, javap-verified). The
Viper's Call changes live outside this set; the other 24 are unchanged.

## Re-baseline (2026-10-28, 4.6.252 summon loot single roll)

Two classes re-extracted: `AdaptiveDifficultyMod` (version constant only) and `ConfigFieldHelp`
(the `summonLootBonusPerTierPercent` help line now says one roll per kill). javap shows only
those constants changed; the other 23 are unchanged.

## Re-baseline (2026-10-28, 4.6.253 LM-owned player data)

Three classes re-extracted: `AdaptiveDifficultyMod` (version constant only), `PrestigePointsSystem`
(the root wallet and breakthrough mirrors now call `LmPlayerStore` instead of `PersistentDataAccess` -
javap shows only those eight call targets changed), and `PrestigePointsSystem$SkillOffer`
(constant-pool renumbering only, no code change). The other 22 are unchanged.

## Re-baseline (2026-10-28, 4.6.254 unlocked tiers load)

Only `AdaptiveDifficultyMod` was re-extracted (version constant only, javap-verified). The
loader fix lives outside this set; the other 24 are unchanged.

## Re-baseline (2026-10-28, 4.6.255 global mastery boost)

Four classes re-extracted: `AdaptiveDifficultyMod` (version constant only), `ConfigRegistry`
(one new `enableGlobalMasteryBoost` key in the progression group - the only new string; the rest
is the rebuilt switch table), `ConfigFieldHelp` (one new help line), and `ProgressionGuiApi`
(new `mastery_boost` placeholder and its default lore line - the only new strings and calls;
other changes are bytecode offsets). javap-verified; the other 21 are unchanged.

## Re-baseline (2026-10-28, 4.6.256 boosts selector)

Two classes re-extracted: `AdaptiveDifficultyMod` (version constant only) and `ProgressionGuiApi`
(new `masteryBoost` handler with its prompt, usage, and code-error helpers, and the `masteryboost`
routing in `handleDo` - the only new strings and calls; the existing call set is unchanged, only
offsets moved). javap-verified; the other 23 are unchanged.

## Re-baseline (2026-10-28, 4.6.257 mastery points wallet)

Twelve classes re-extracted. `AdaptiveDifficultyMod`: version constant only. `PrestigeShopConfig`:
one new `masteryWallet` section field (class `MasteryWalletShop`, `enabled`) and its null guard in
sanitize. Its eight existing inner classes (`CustomEntry`, `CustomShop`, `Exchange`, `FormOffer`,
`Forms`, `GodKi`, `SkillShop`, `TierShop`) changed only in line numbers and the nest list.
`PrestigeShopHooks`: `mastery_buy` / `mastery_apply` dispatch to `MasteryWallet` and two help
lines. `UltraPrestigeScreens`: the Mastery wallet pages, routes, and Shop button. javap-verified:
additions only, nothing removed; the other 13 are unchanged.

## Re-baseline (2026-10-28, 4.6.258 mastery wallet paging)

Two classes re-extracted: `AdaptiveDifficultyMod` (version constant only) and
`UltraPrestigeScreens` (the wallet line and form pickers take a page index and page through
`UltraPickHelper`; the form view carries the line page for Back - the only new calls).
javap-verified; the other 23 are unchanged.

## Re-baseline (2026-10-28, 4.6.259 .dat strip)

Only `AdaptiveDifficultyMod` was re-extracted (version constant only, javap-verified). The strip
pass lives outside this set; the other 24 are unchanged.

## Re-baseline (2026-10-28, 4.6.260 boost encoded reject)

Only `AdaptiveDifficultyMod` was re-extracted (version constant only, javap-verified). The boost
reply change lives outside this set; the other 24 are unchanged.

## Re-baseline (2026-10-28, 4.6.261 boost start dispatch)

Only `AdaptiveDifficultyMod` was re-extracted (version constant only, javap-verified). The command
tree change lives outside this set; the other 24 are unchanged.

## Re-baseline (2026-10-30, 4.7.0 DragonMineZ 2.2 port)

Three classes re-extracted from `mods/LegacyMechanics-4.7.0.jar`, javap-verified:
`AdaptiveDifficultyMod` (version constant and the boot banner now names DragonMineZ 2.2),
`ConfigRegistry` (the general group lists the new `debugLogging` field: one more string-switch
case, hash `0x4a3b998c`, no C2 A7 bytes), and `ConfigFieldHelp` (the `debugLogging` help line).
The other 22 are byte-identical after the 2.2 recompile.

The weapon hot-path baseline (`weapon-hotpath-4.6.229/`) was re-seeded in the same build:
`WeaponRatioConfig.info`/`warn` lost their `System.out` fallback (javap: only the two
try/catch println blocks are gone). `WeaponRatioConfig$Json` and `$Snapshot` differ only in their
line-number tables (the source file is 10 lines shorter). The other 6 weapon classes are unchanged.

## Re-baseline (2026-10-30, renamed 4.7.0)

The 2.2 port ships as 4.7.0 instead of 4.6.262 (that number never shipped). Only
`AdaptiveDifficultyMod` was re-extracted (version constant only, javap-verified).

## Re-baseline (2026-10-30, 4.7.1 Hakai mixin unregistered)

Only `AdaptiveDifficultyMod` was re-extracted (version constant only, javap-verified).

## Re-baseline (2026-10-30, 4.7.2 prestige follows Overhaul)

Seven classes re-extracted from `mods/LegacyMechanics-4.7.2.jar`; javap member and string diffs
checked: `AdaptiveDifficultyMod` (version), `PrestigePointsSystem` (breakthrough members, constants,
and their strings removed), `PrestigePointsSystem$SkillOffer` (constant-pool order only),
`ProgressionGuiApi` (breakthrough placeholders, lore and admin paths removed; Need copy states the
cap rule), `PrestigeAdminCommandTree` (`breakthroughs` subcommand removed), `UltraPrestigeScreens`
(cap-rule copy), `ConfigFieldHelp` (one help line no longer mentions breakthroughs). The other 18 are
unchanged.

## Re-baseline (2026-10-30, 4.7.2 native cap ladder)

Only `ProgressionGuiApi` re-extracted: one prestige lore line now says each prestige you hold raises
your cap (javap: that string only). The other 24 are unchanged.

## Re-baseline (2026-10-30, 4.7.3 prestige per character)

Two classes re-extracted from `mods/LegacyMechanics-4.7.3.jar`; javap member and string diffs
checked: `AdaptiveDifficultyMod` (version string, and the boot call to `PrestigeCharacter.register`),
`PrestigePointsSystem` (`getPoints` / `setPoints` read and write another character's favor through
`PrestigeCharacter`; the account path is unchanged). The other 23 are unchanged.

Audit fixes, same day: `PrestigePointsSystem` re-extracted again. javap: the two skill-floor scans
read `ProgressionData.shop` (the active character's purchases) instead of `ProgressionData.stored`;
nothing else changed. `GodKiEnhancementShop` and `PrestigeCustomShop` are unchanged: their `pp_*`
keys reach the character's bag through `ProgressionData`.

## Re-baseline (2026-10-30, 4.7.4 Ultra form scaling off, blank switch-in reset)

Three classes re-extracted from `mods/LegacyMechanics-4.7.4.jar`; javap member and string diffs
checked: `AdaptiveDifficultyMod` (version string only), `ConfigRegistry` (one new string, the
`disableUltraFormScaling` case in the progression field switch; members unchanged),
`ConfigFieldHelp` (one new help line for `disableUltraFormScaling`). The other 22 are unchanged.

## Re-baseline (2026-10-30, 4.7.6 Ultra form-scale workaround removed)

`ConfigRegistry` and `ConfigFieldHelp` restored to their 4.7.3 files: removing
`disableUltraFormScaling` makes the build byte-identical to them again. `AdaptiveDifficultyMod`
re-extracted from `mods/LegacyMechanics-4.7.6.jar` (javap: version string only). The other 22 are
unchanged.

## Re-baseline (2026-10-31, 4.7.7 body-parts catalog from DMZ Ultra 0.4.0)

`AdaptiveDifficultyMod` re-extracted from `mods/LegacyMechanics-4.7.7.jar` (javap: version string
only). The other 24 are unchanged. (The catalog was first built as 4.7.5 and never shipped; 4.7.6
went out first, so it ships as 4.7.7.)

## Re-baseline (2026-10-31, 4.7.8 GUI bridge login check)

`AdaptiveDifficultyMod` re-extracted from `mods/LegacyMechanics-4.7.8.jar` (javap: version string
only). The other 24 are unchanged.

## Re-baseline (2026-10-31, 4.7.9 Saiyan tail restore)

`AdaptiveDifficultyMod` re-extracted from `mods/LegacyMechanics-4.7.9.jar` (javap: version string
only). The other 24 are unchanged.

## Re-baseline (2026-10-31, 4.7.10 model sync hardening)

`AdaptiveDifficultyMod` re-extracted from `mods/LegacyMechanics-4.7.10.jar` (javap: version string
only). The other 24 are unchanged.

## Re-baseline (2026-10-31, 4.7.11 Character Home index)

`AdaptiveDifficultyMod` re-extracted from `mods/LegacyMechanics-4.7.11.jar` (javap: version string
only). The other 24 are unchanged.

## Re-baseline (2026-10-31, 4.7.12 prestige cap 20)

Four classes re-extracted from `mods/LegacyMechanics-4.7.12.jar`; javap diffs checked:
`AdaptiveDifficultyMod` (version string), `ConfigFieldHelp` (one new help line,
`referenceMaxPrestige20MigratedV1`), `ProgressionGuiApi` and `UltraPrestigeScreens` (the `held_max`
fallback `"10"` is now the cap constant, 20; branch offsets shift with it). The other 21 are
unchanged.

## Re-baseline (2026-10-31, 4.7.13 prestige exchange packs)

Fourteen classes re-extracted from `mods/LegacyMechanics-4.7.13.jar`; javap diffs checked:
`AdaptiveDifficultyMod` (version string), `PrestigeShopConfig` (8 default packs, one-time
`migratePacks18` on load and save-back), `PrestigeShopConfig$Exchange` (`PACK_BONUS` table, new
`packs18MigratedV1` field, `payout` reads the table), `PrestigeShopHooks` (two help lines),
`PrestigePointsSystem` (`TURN_IN_AMOUNTS` 8 packs, `pointsForTurnIn` delegates to `payout`),
`UltraPrestigeScreens` (Bulk line helper, `exchangeLabel` "Exchange 18: +28 favor", exchange self-test), `ProgressionGuiApi` (lore). The
seven other `PrestigeShopConfig` inner classes differ only in line numbers. The other 11 are
unchanged.

## Re-baseline (2026-10-31, 4.7.14 dmzrevamp 2.2.4)

`AdaptiveDifficultyMod` re-extracted from `mods/LegacyMechanics-4.7.14.jar` (javap: version string
only). The other 24 are unchanged, also when compiled against dmzrevamp 2.2.4 and DragonMineZ 2.2.1.

## Re-baseline (2026-10-31, 4.7.15 prestige need ladder)

Three classes re-extracted from `mods/LegacyMechanics-4.7.15.jar`; javap diffs checked:
`AdaptiveDifficultyMod` (version string), `ProgressionGuiApi` and `UltraPrestigeScreens` (player text:
"Need" instead of "your cap"; lore lines built with `UltraStyle`). The other 22 are unchanged.

## Re-baseline (2026-10-31, 4.7.16 prestige level cap ladder)

`AdaptiveDifficultyMod` re-extracted from `mods/LegacyMechanics-4.7.16.jar` (javap: version string
only). The other 24 are unchanged.

## Re-baseline (2026-10-31, 4.7.17 sparring stun / clash)

Two classes re-extracted from `mods/LegacyMechanics-4.7.17.jar`; javap diffs checked:
`AdaptiveDifficultyMod` (version string) and `ConfigFieldHelp` (help lines for the new sparring
timer fields). The other 23 are unchanged.

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
