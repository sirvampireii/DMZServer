# DMZ 2.2.1 God Ki copy / Divine Ki patch (v3 = 2.2.1.1)

Builds `dragonminez-<PATCH_VERSION>.jar` (now `dragonminez-2.2.1.1.jar`) from the stock
`dragonminez-2.2.1.jar` (sha256 `039ce269…8181`). Live ran stock 2.2.1, then rev 3 as
`dragonminez-2.2.1-godki.jar`. Not an LM change. KaiPath and DMZUltra are untouched.

```bash
DMZ_JAR=/path/to/dragonminez-2.2.1.jar [PATCH_VERSION=2.2.1.N] bash tools/dmz-godki-patch/build.sh
# -> tools/dmz-godki-patch/out/dragonminez-<PATCH_VERSION>.jar, godki-patch.diff, fidelity.txt, sha256.txt
```

## Patch version

Each patch build stamps its own version on the one `version="2.2.1"` line of `META-INF/mods.toml`
(the only DMZ version that matters; there is no version constant in code). `PATCH_VERSION` defaults to
`2.2.1.1` in `build.sh`. **The next patch build is 2.2.1.2**: bump the default (or set the env var); no
code change is needed. The jar is named after it, `dragonminez-<PATCH_VERSION>.jar`. `repack.py`
accepts only `2.2.1.N` and checks that the mods.toml change is exactly that line. `MANIFEST.MF` keeps
stock `2.2.1`.

DMZ's `displayTest` is `MATCH_VERSION`: **the client pack must ship the same jar**, or clients still on
2.2.1 (or an older patch) are refused at join with a version mismatch. Every dependent mod checked
accepts 2.2.1.N: LM, dmzrevamp and Living World `[2.2,)`; Ultra, KaiPath and Ultimate Key `[2.2,2.3)`;
apothicdmz and dmz_spellbooks `[2.1.2,)`.

## What changes

All of it is in `com.dragonminez.common.stats.StatsData` (shared server and client code), the
two transform handlers (`StackFormModeHandler`, `FormModeHandler`) and, from v3, the instant-transform
key (`ExecuteActionC2S`).

| Rule | Where |
|---|---|
| God Ki copies the strongest unlocked god form: each stat is that form's mastery-adjusted multiplier, with no `+ own - 1`. Pool: `godforms`, `biogodforms`, `divineforms`, `evildivineforms`, plus any group whose formType contains `godform`. "Strongest" means the highest average of the six mastery-adjusted multipliers (STR/SKP/DEF/VIT/PWR/ENE), the same measure DMZ's Ultimate uses, skipping forms incompatible with `godki.godki`. | `getStackFormMultiplier` → `getGodKiCopyMultiplier` |
| No god form unlocked: God Ki gives the pool form with the lowest average base multiplier (mastery 0), read from the loaded configs. | `getGodKiCopyMultiplier` |
| God Ki stands alone: `getFormMultiplier` returns 1.0 while the `godki.godki` stack form is active. | `getFormMultiplier` |
| v3: Divine Ki on a **god form** multiplies the combined total by `1 + 0.01 + 0.09 * min(mastery, 10000) / 10000` (×1.01 at mastery 0 to ×1.10 at 10,000), after the add-or-multiply step. Its own stack term is 1.0 there, so the factor is the whole boost. `DIVINEKI_FULL_MASTERY` (10000) must match Divine Ki's `maxMastery` in godki.json. | `getTotalMultiplier` (`getDivineKiFactor`), `getStackFormMultiplier` |
| v3: Divine Ki on a **normal form** (any active form that isn't a DMZ / Ultra god form, KaiPath Kai / Destroyer forms included) is an ordinary stack form: its config multipliers (STR / SKP / PWR 3.6, DEF 2.4, VIT 2.0, SPD 1.1), mastery-adjusted like any stack form, and no factor. | `getStackFormMultiplier` falls through to the stock path |
| v3: Divine Ki activates in any form (god or normal) and is refused in base form and while God Ki is copying. The refusal uses the existing `message.dragonminez.form.not_stackable`. | `canActivateDivineKi`; `StackFormModeHandler.canCharge` / `attemptTransform`; `ExecuteActionC2S.instantTransformStackForm` |
| v3: the instant-transform key carries the same gates: Divine Ki as above, God Ki base-only, and no form transform while God Ki is copying. Stock's instant path skips both action-mode handlers, so before v3 it bypassed them. | `ExecuteActionC2S.instantTransformStackForm` / `instantTransformForm` |
| v2: for players with a god form to copy, God Ki activates from base form only (no active form, or form `base`). It never stacks on any form, so the rule is code, not an `incompatibleWith` list (a finite list can't say "any form"; God Ki's list stays `[]`). Refused with `not_stackable`. | `StackFormModeHandler.canCharge` / `attemptTransform` (`canActivateGodKi`) |
| v2: while God Ki is copying, transforming into any form is refused with `not_stackable`, and God Ki stays on. Stock DMZ would instead drop an incompatible stack form (`stack_removed`). Reverting to base keeps God Ki, since base contributes 1.0. | `FormModeHandler.canCharge` / `attemptTransform` |
| v2: races with no god form to copy keep stock God Ki. With no god-form pool (no unlocked god form and no base pool form), God Ki is the stock stack form: config multipliers, mastery-adjusted, combined with the active form as in stock 2.2.1. It activates from any form, the two gates above don't apply, and it is that race's god form. "Has a copy" uses the copy's own pool scan (`findGodKiCopySource`), not a race list. From v3, Divine Ki works for these races on any form, in config mode (they have no god form for the factor). | `isGodKiCopyActive` = God Ki active **and** `hasGodKiCopy()` |

God Ki and Divine Ki are both forms in the `godki` stack group. Every check therefore keys on the
group *and* the form name, so Divine Ki is never treated as God Ki.

`incompatibleWith` is only compared against the active **base** form (`areFormsCompatible`), never
the active stack form. Listing `godki.godki` in Divine Ki's `incompatibleWith` would not block
God Ki → Divine Ki (with a stack form active, the next stack form is the next form in the same
group), so the patch adds an explicit check.

Config (step 4): `scripts/patch-godki-json-godki-copy.py <env> [--apply]` clears God Ki's
`incompatibleWith`. It is a dry run without `--apply`. Ship it with the jar, never on the stock jar.

Config (v3): `scripts/patch-godki-json-v3.py <env> [--apply]` sets God Ki `maxMastery` and Divine Ki
`maxMastery` / `unlockOnMastery` to 10000 (Divine Ki unlocks when God Ki is fully mastered). Dry run
without `--apply`; then `/dmzreload config`. godki.json is not tracked in this repo. On the rev 3 jar
the divine factor stops at ×1.05 (it caps at 5,000); on the v3 jar with the old 5,000 caps, divine
mastery can't pass 5,000, so the factor stops at ×1.055.

## God form discovery (DMZ, Ultra, KaiPath)

Every check runs live, at the moment it's needed, so a race that gains or loses god forms switches
behaviour with no code change. Nothing is cached per race and there are no race or form lists.

| Source | How God Ki finds it | Status |
|---|---|---|
| DMZ | `FusionForms.allFormGroups(this)`. A group is a god group if its name is `godforms` / `biogodforms` / `divineforms` / `evildivineforms` or its `formType` contains `godform`. | Wired (existing scan) |
| Ultra 0.4.0 | Ultra installs its god lines as DMZ form groups with `formType` `godforms` (live: `arcosian_god`, `monkey_god`, `namek_god`, `human_god_forms`, `dark_god`, `viltrumgod`, `ancestral_divinity`, `divine_evolution`). `FormInstallerEvents` calls `ConfigManager.reload()` after installing, so new lines reach the DMZ scan without a restart. Ultra's own `RaceExtraForms.godKi()` is **not** a god-form registry: it lists per-form God Ki *aura* variants (about 90 forms, including non-god forms such as Ultra Super Saiyan 2-4 and Seiun-jin's Event Horizon / Starforged / Flare Bloom), so it isn't used. | Covered by the DMZ scan; nothing extra to wire |
| KaiPath 1.5.2 | KaiPath's Kai (`kaipath_kai`) and Destroyer (`kaipath_hakaishin`) divine forms are injected into every race's `getAllFormsForRace` by `DivineFormConfigMixin`, with `formType` = the group id, so the name/type scan misses them. `isKaiPathDivineGroup` asks `com.dmzlegacy.kaipath.DivineForms.managed(group)` through a one-time reflective lookup; absent KaiPath simply means false. KaiPath definitions use race `*`, so those groups exist for every race. They therefore count only as **unlocked** copy candidates (a player who actually has a Kai / Destroyer form), never for the lowest-base fallback. Otherwise every race would get a pool and lose old God Ki. | Wired (optional reflection) |

`isInGodForm` (which Divine Ki mode applies) is DMZ / Ultra god groups only, so KaiPath forms are normal
forms for Divine Ki: config mode, no factor.

## How the jar is built (and why it is safe)

1. Vineflower 1.10.1 decompiles only `StatsData`, `StatsData$1`, `StackFormModeHandler`, `FormModeHandler` and
   `ExecuteActionC2S` (with its two inner classes, which are not shipped). Vineflower
   and lombok come from Maven Central, checked against Maven's SHA-1. One decompiler name clash is
   fixed (`apply_patch.py --fixup-only`).
2. `apply_patch.py` applies the patch, and javac compiles it against the stock jar and the server
   libraries in `libraries/`.
3. `Transplant.java` (ASM 9.6) starts from the **stock** class bytes and swaps in only
   `getTotalMultiplier`, `getFormMultiplier`, `getStackFormMultiplier` (StatsData) and `canCharge`,
   `attemptTransform` (StackFormModeHandler and FormModeHandler) and `instantTransformForm`,
   `instantTransformStackForm` (ExecuteActionC2S), then adds the new helper methods. Every other method
   keeps its original bytecode. The other 175 StatsData methods never go through the decompiler.
4. `fidelity.py` gates the build: it fails if any method outside that list differs from stock.
5. `repack.py` copies the stock jar entry for entry and swaps just the four class files, then stamps
   the patch version into `mods.toml`. It checks that every other entry is byte-identical (9,124 of
   9,129) and that the `mods.toml` diff is that one line. The jar is unsigned.

Clients need the same jar for the scouter, stats screen and radial to show the copied values. The
server computes damage either way.

## Test-server results, v1 (2026-11-03)

The "God Ki over" rows show v1 behaviour; v2 refuses that activation (see below).

These are from a test-only probe (a fake Saiyan with live Saiyan forms, the live general-server.json
and godki.json with the list cleared), run on stock and patched DMZ. All values are getTotalMultiplier.

| Case | Stock | Patched |
|---|---|---|
| God Ki alone, Blue Perfected the strongest unlocked (alone: STR 14.0175, DEF 9.7127) | 1.3 / 1.2 | 14.0175 / 9.7127 (exact copy, all six stats) |
| God Ki over Super Saiyan / over Blue Perfected | 4.3 / 14.3175 | 14.0175 / 14.0175 (base form 1.0) |
| God Ki, no god form unlocked | 1.3 / 1.2 / 1.67 | 4.25 / 3.25 / 1.0 (Super Saiyan God base) |
| Divine mastery 0 / 5,000 on Blue Perfected | 16.6175 / 18.4175 | 14.157675 (×1.01) / 14.718375 (×1.05) |
| Divine on Super Saiyan | 8.4 | 4.0 (no boost) |
| Divine from God Ki / from Super Saiyan / from a god form | allowed / allowed / allowed | refused / refused / allowed |
| Divine with God Ki mastery 4,999 / 5,000 | locked / unlocked | locked / unlocked |
| Ultimate with no base / over Blue Perfected | 15.0175 / 28.035 | 15.0175 / 28.035 (unchanged) |

## Test-server results, v2 (2026-11-03)

Same probe and live configs, run on stock and on the v2 jar (sha256 `547dbc76…535e`).

| Case | Stock | v2 |
|---|---|---|
| (a) God Ki from Super Saiyan God / Blue / Super Saiyan | canCharge true | canCharge false, no stack form after performAction |
| (b) God Ki from base form | activates, 1.95 / 1.8 (fixed form, mastery-adjusted) | activates, 14.0175 / 9.7127 (Blue Perfected copy) |
| (c) God Ki active, transform into Super Saiyan / Blue Perfected | canCharge false / true | canCharge false / false; God Ki stays on at 14.0175 / 9.7127 |
| (d) Divine from God Ki / base / Super Saiyan / a god form | all allowed | refused / refused / refused / allowed |
| (d) Divine mastery 0 / 5,000 on Blue Perfected | 16.6175 / 18.4175 | 14.157675 (×1.01) / 14.718375 (×1.05) |
| (d) Divine with God Ki mastery 4,999 / 5,000 | locked / unlocked | locked / unlocked |
| (e) Ultimate with no base / over Blue Perfected | 15.0175 / 28.035 | 15.0175 / 28.035 |

The (c) refusal is proven through `canCharge`, the check the transform keybind charge uses. On this
fake player, `performAction` left the form unchanged on stock too, so the `attemptTransform`
refusal is checked by its bytecode, not by a stock-vs-v2 difference.

## Test-server results, v2 revision 3 (2026-11-03)

Live Saiyan and Seiun-jin forms, the live general-server.json and godki.json, Ultra 0.4.0, KaiPath 1.5.2
and Living World 2.5.1 loaded. Totals are getTotalMultiplier (STR / DEF, or STR / DEF / VIT).

| Case | Result |
|---|---|
| (a) God Ki in Super Saiyan God / Blue / Super Saiyan | refused (canCharge false, no stack form) |
| (b) God Ki from base | 14.0175 / 9.7127 = Blue Perfected alone |
| (c) God Ki on, transform into Super Saiyan / Blue Perfected | refused; God Ki stays at 14.0175 / 9.7127 |
| (d) Divine from God Ki / god form / Super Saiyan | refused / allowed / refused; ×1.01 = 14.157675, ×1.05 = 14.718375; locked at God Ki mastery 4,999, unlocked at 5,000 |
| (e) Ultimate with no base / over Blue Perfected | 15.0175 / 28.035 (stock) |
| (f) Seiun-jin: God Ki in base / Starbirth + God Ki / transform with God Ki on | 1.95 / 1.8 / 2.505; 6.95 / 6.05 / 2.505; allowed. All identical to stock 2.2.1 |
| (g) Saiyan, no god form unlocked, God Ki from base | 4.25 / 3.25 (Super Saiyan God base) |
| (h) Seiun-jin + a godforms group written to config, then `ConfigManager.reload()` (Ultra's install path) | copy mode: God Ki copies it (4.25 / 3.25), refused in Starbirth |
| (h) group deleted, reload | old God Ki again: Starbirth + God Ki 6.95 / 6.05 |
| (h) KaiPath | `kaipath_kai` injected for Seiun-jin; `managed()` true via reflection; 0 unlocked, so Seiun-jin keeps old God Ki. Copying an unlocked Kai / Destroyer form needs a KaiPath office holder and was not exercised |

## Test-server results, v3 (2026-11-04)

Live Saiyan and Seiun-jin forms, the live general-server.json, godki.json with the v3 values
(maxMastery 10000 for both, Divine Ki unlockOnMastery 10000), Ultra 0.4.0 and KaiPath 1.5.2 loaded.
First run on the v3 classes before version stamping (sha256 `f1a0ef49…dec2c`), then rerun on
`dragonminez-2.2.1.1.jar` (sha256 `dd5dfd8a…63bb1`) with identical probe output. Totals are getTotalMultiplier (STR / DEF / VIT).

| Case | Result |
|---|---|
| (b) Divine on Super Saiyan God, mastery 0 / 5,000 / 10,000 (SSG alone 6.375 / 4.875 / 1.0) | ×1.010000 / ×1.055000 / ×1.100000 on all six stats (7.0125 / 5.3625 / 1.1 at 10,000); stack term 1.0 |
| (c) Divine on Super Saiyan, mastery 0 / 5,000 / 10,000 (SSJ alone 6.0 / 5.25 / 1.0) | stack term 3.6 / 4.5 / 5.4 STR (config, mastery-adjusted up to ×1.5); totals 8.6 / 9.5 / 10.4 = SSJ + stack − 1; no factor |
| (d) Divine in base form / with God Ki on | refused on the action-mode path (canCharge false, no stack form) and on the instant key (returns false) |
| (d) Divine in Super Saiyan / Super Saiyan God | allowed on both paths |
| Divine unlock at God Ki mastery 9,999 / 10,000 | locked / unlocked |
| (a) instant key: God Ki from Super Saiyan / SSG | refused (stock allowed it); from base: activates |
| (a) instant key: form transform with God Ki copying / without God Ki | refused, God Ki stays on / transforms |
| (e) Divine on `kaipath_kai.divine` (Saiyan and Seiun-jin) | isInGodForm false, divine activates, config mode: stack 3.6 / 4.5 / 5.4 STR, no factor |
| Seiun-jin (no god pool) | Starbirth + God Ki 6.95 / 6.05 / 2.505 (stock, unchanged); Divine on Starbirth is config mode like (c) |
| (f) whole-jar diff vs stock | 4 class entries differ (StatsData, StackFormModeHandler, FormModeHandler, ExecuteActionC2S) plus the single mods.toml version line; 9,124 identical; fidelity gate passes |
| (g) version | Forge's version checker logs `[dragonminez] Current: 2.2.1.1` (the ModInfo version the mod list and Spark show); Ultra's self-test reads "DragonMineZ 2.2.1.1" |
| (f) hardcoded 5000 scan | none left in the four patched classes (the only 10000 is `DIVINEKI_FULL_MASTERY`) |

LM's own God Ki gate (`StackFormModeHandlerGodKiGateMixin`, "God forms need God Ki") refused the
action-mode Divine charge on SSG for the probe player, who has no God Ki wallet level. That gate is LM's,
sits on the action-mode path only, and is unchanged here.
