# DMZ 2.2.1 God Ki copy / Divine Ki factor patch

Builds `dragonminez-2.2.1-godki.jar` from the stock `dragonminez-2.2.1.jar`
(sha256 `039ce269…8181`, the jar live runs). Not an LM change. KaiPath and DMZUltra are untouched.

```bash
DMZ_JAR=/path/to/dragonminez-2.2.1.jar bash tools/dmz-godki-patch/build.sh
# -> tools/dmz-godki-patch/out/dragonminez-2.2.1-godki.jar, godki-patch.diff, fidelity.txt, sha256.txt
```

## What changes

All of it is in `com.dragonminez.common.stats.StatsData` (shared server and client code) and the
stack transform handler.

| Rule | Where |
|---|---|
| God Ki copies the strongest unlocked god form: each stat is that form's mastery-adjusted multiplier, with no `+ own - 1`. Pool: `godforms`, `biogodforms`, `divineforms`, `evildivineforms`, plus any group whose formType contains `godform`. "Strongest" means the highest average of the six mastery-adjusted multipliers (STR/SKP/DEF/VIT/PWR/ENE), the same measure DMZ's Ultimate uses, skipping forms incompatible with `godki.godki`. | `getStackFormMultiplier` → `getGodKiCopyMultiplier` |
| No god form unlocked: God Ki gives the pool form with the lowest average base multiplier (mastery 0), read from the loaded configs. | `getGodKiCopyMultiplier` |
| God Ki stands alone: `getFormMultiplier` returns 1.0 while the `godki.godki` stack form is active. | `getFormMultiplier` |
| Divine Ki multiplies the combined total by `1 + 0.01 + 0.04 * min(mastery, 5000) / 5000` (×1.01 to ×1.05), after the add-or-multiply step. It does this only while the base form is a god form. Its own stack multiplier is 1.0, so its fixed config values are inert. | `getTotalMultiplier`, `getStackFormMultiplier` |
| Divine Ki can't be activated or charged while God Ki is active or off a god form. The refusal uses the existing `message.dragonminez.form.not_stackable`. | `StackFormModeHandler.canCharge` / `attemptTransform` |

God Ki and Divine Ki are both forms in the `godki` stack group. Every check therefore keys on the
group *and* the form name, so Divine Ki is never treated as God Ki.

`incompatibleWith` is only compared against the active **base** form (`areFormsCompatible`), never
the active stack form. Listing `godki.godki` in Divine Ki's `incompatibleWith` would not block
God Ki → Divine Ki (with a stack form active, the next stack form is the next form in the same
group), so the patch adds an explicit check.

Config (step 4): `scripts/patch-godki-json-godki-copy.py <env> [--apply]` clears God Ki's
`incompatibleWith`. It is a dry run without `--apply`. Ship it with the jar, never on the stock jar.

## How the jar is built (and why it is safe)

1. Vineflower 1.10.1 decompiles only `StatsData`, `StatsData$1` and `StackFormModeHandler`. Vineflower
   and lombok come from Maven Central, checked against Maven's SHA-1. One decompiler name clash is
   fixed (`apply_patch.py --fixup-only`).
2. `apply_patch.py` applies the patch, and javac compiles it against the stock jar and the server
   libraries in `libraries/`.
3. `Transplant.java` (ASM 9.6) starts from the **stock** class bytes and swaps in only
   `getTotalMultiplier`, `getFormMultiplier`, `getStackFormMultiplier` (StatsData) and `canCharge`,
   `attemptTransform` (StackFormModeHandler), then adds the new helper methods. Every other method
   keeps its original bytecode. The other 175 StatsData methods never go through the decompiler.
4. `fidelity.py` gates the build: it fails if any method outside that list differs from stock.
5. `repack.py` copies the stock jar entry for entry and swaps just the two class files. It checks
   that every other entry is byte-identical (9,127 of 9,129). `mods.toml` stays `2.2.1`, and the jar
   is unsigned.

Clients need the same jar for the scouter, stats screen and radial to show the copied values. The
server computes damage either way.

## Test-server results (2026-11-03)

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
