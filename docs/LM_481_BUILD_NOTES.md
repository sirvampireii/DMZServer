# LM 4.8.1 build notes

Three CNPC scripts still printed deleted `/progression` commands. Four strings fixed; nothing else changed.

| | |
|---|---|
| Branch | `lm-481-cnpc-strings` (off `main` at 66c3dea8) |
| Manifest row | `lm-481-cnpc-strings`, checks in `§ lm 4.8.1` |

## The fix

4.8.0 (4.7.88) moved every command under `/lm` and deleted `/progression`. The header comments of these scripts
were updated then, but the message bodies were not: each message is built from color pieces
(`COLOR + "7Use " + COLOR + "f/progression boost"`), and both the rewrite and the manifest check looked for the slash
after a section sign or a non-word character, so a piece starting with the color letter `f` slipped past.

| File:line | Before | After |
|---|---|---|
| `customnpcs/scripts/Global TP Boost.js:39` | Need encoded value. Use /progression boost | Need encoded value. Use /lm admin progression boost |
| `customnpcs/scripts/Global TP Boost.js:64` | Use /progression boost (LegacyMechanics). | Use /lm admin progression boost (LegacyMechanics). |
| `customnpcs/scripts/AndrioidConversion.js:46` | Use /progression android (LegacyMechanics). | Use /lm admin progression android (LegacyMechanics). |
| `customnpcs/scripts/TP boost end.js:36` | Use /progression boost end (LegacyMechanics). | Use /lm admin progression boost end (LegacyMechanics). |

Only the command path changed; the text around it, the colors and the layout are the same. `boost` (TP boost) and
`masteryboost` (mastery boost) are untouched. No Java or command-tree change; the jar differs from 4.8.0 only in its
version.

## What the checks scan

- `§ lm 4.7.88` "no string in the mod prints a deleted command": **Java string literals in LM's `src/main/java`
  only** (comment lines skipped; vanilla `/difficulty <level>` allowed). It never read `.js` files. The 4.7.88 build
  notes said every string was rewritten; that line is corrected in `docs/LM_4788_BUILD_NOTES.md`.
- `§ lm 4.8.1` (new): the four strings read `/lm admin progression ...`, and **every string literal (double or single
  quoted) in `customnpcs/scripts/*.js`** is scanned for a deleted root, including a literal that starts with a color
  letter before the slash. Comments are not scanned. Run against `main` before the fix it reports exactly the four
  lines above; after the fix it reports none.

## Repo-wide search

Every text file under `tools/`, `config/`, `docs/`, `kubejs/`, `customnpcs/scripts/`, `scripts/` and `sim/` was
searched for `/difficulty`, `/diff`, `/rival`, `/spar`, `/progression`, `/prestige`, `/padmin`, `/character`,
`/skillcheck`, `/skills`, `/enddragon`, `/androidify` and `/terminal` used as a command (after a space, quote,
bracket, section sign or color letter; path pieces like `docs/character` are not hits). No player-facing string
prints a deleted command. What is left:

- **Allowed:** the retired GUI plugin `tools/dmz-adaptive-difficulty-gui/` (never installed); `archive/` and
  `uploads/` snapshots (not scanned); `UiProbe` assertions that the old roots are unknown (`V88 CMD /difficulty is
  vanilla again`, the `/lm help` check that it has no `/spar` or `/rival`).
- **Intentional:** `/lm admin help` and a `DifficultyCommands` comment name vanilla `/difficulty <level>` (ops,
  world difficulty), which exists again.
- **Comments only:** `customnpcs/scripts/End Dimension Strength.js` lines 14 and 32 mention `/enddragon` in comments;
  they print nothing (left alone: no other script is in scope).
- **Audit check names and assertions** in `sim/audit_features.py`, `sim/audit_lm_admin_commands.py` and
  `sim/audit_ship_manifest.py` (labels of older checks, and checks that old text is absent).
- **Historical records:** `docs/CHANGELOG.md`, earlier manifest rows, earlier build notes (4.7.84-4.7.88),
  `docs/lm4784-work/`, `docs/LM_4784_NAVIGATION_PROPOSAL.md`, `docs/DOJO_IMPROVEMENT_AUDIT_2026-11-24.md`,
  `tools/dmz-adaptive-difficulty/AUDIT.md`.
- **Old crawl captures:** `tools/lm-gui-corpus/tools/real/guikit-real.json` and `guikit-crawl.json` hold menu text
  recorded at 4.7.86, before the 4.7.88 command move (`/skills`, `/rival tpmsg`, `/difficulty admin`). They are
  snapshots of earlier builds, not text the mod prints.

## Deploy

These three scripts are live data files in `customnpcs/scripts/`, not jar contents. After this lands, sync
`Global TP Boost.js`, `TP boost end.js` and `AndrioidConversion.js` to live `customnpcs/scripts/` and reload CNPC
scripts (or restart) so the in-game text changes. The 4.8.1 jar carries no change beyond its version.

## Release check

`DMZRig/results/release-4.8.1`: build OK (every `build.sh` audit, `§ lm 4.8.1` all OK), **379 PASS, 0 FAIL**,
0 matrix rows failing, strict boot self-tests OK (19). Jar `LegacyMechanics-4.8.1.jar` sha256
`3a56821eaac1e0fcccead7d41145c06676d03710b3ddda93c790fbb44b78b2e7`.
