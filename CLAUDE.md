# DMZServer — rules for coding agents

Forge 1.20.1 server for DragonMineZ 2.1.3 on Mohist. The main mod under development is
**LegacyMechanics (LM)** in `tools/dmz-adaptive-difficulty/`. Details live in `docs/`; this file is
the short list that every session must follow.

## Every task ends with a commit and a push

- When a task changes anything, commit it and push it to GitHub before you finish. Work that exists
  only on this PC is lost work (the Prestige Custom Shop source sat uncommitted while two jars
  shipped from it).
- Push to a topic branch off `main` and say the branch name in your final message. Merge to `main`
  only when the owner asks.
- If you cannot push, say so plainly and list what is uncommitted.
- Before committing, check `git status` for files you built from but did not stage. A jar must never
  ship from source that is not in the repo.

## Live server (Kinetic panel)

- Never upload, restart, or edit files on the live server unless the owner asks in this session.
  Deploy steps: `docs/DEPLOY.md`.
- Never commit passwords or panel credentials; `live-sftp.env` stays gitignored.
- After a Forge jar change the server needs a full restart (`/lm admin reload` only reloads config).
- Live `mods/` keeps exactly one `LegacyMechanics-*.jar`; no `LegacyMechanicsGUI-*.jar` in `plugins/`.

## Building LM

- Build with `bash tools/dmz-adaptive-difficulty/build.sh`; it must pass every audit it runs.
- Packaging order: clean before build, scan the jar for missing/referenced classes and inner classes,
  scan every `@Mixin` class for non-private helper methods.
- Keep the version identical in `AdaptiveDifficultyMod.VERSION`, `mods.toml`, and `plugin.yml`.
  Docs- or kit-only changes do not bump the version.
- A behavior change the owner approves gets a row in `docs/LM_SHIP_MANIFEST.md` and a matching
  check in `sim/audit_ship_manifest.py` in the same change.

## Code and player-facing text

- No raw `§` in LM Java sources; use `UltraStyle` (`docs/ULTRA_STYLE_RULE.md`).
- Player text reads like a friendly guide: no raw IDs, snake_case, or action names; say what a click
  does; warn about costs or irreversible actions before the click; never a bare "Invalid input".
- Buttons are at least 80px wide with 8px horizontal padding. Menus say "Back to X" / "Main menu",
  never "Hub".
- CNPC menu layout rules: `docs/CNPC_GUI_MENU_RULES.md`. Locked design decisions:
  `tools/dmz-adaptive-difficulty/CONVERSATION-CHECKLIST.md`.

## KaiPath and DMZUltra boundary

- Never modify the KaiPath jar — its developer (JLDK) owns it. In scope: LM server code,
  `tools/ultra-gui-kit/` (the reference JLDK copies from), and `docs/KAIPATH_*.md`.
- When the bridge protocol changes, update those docs and keep the kit matching LM's real wire output.
- Never bundle DMZUltra assets, reference `assets/dmzultra/...`, or subclass DMZUltra screens.
  Bridge fields stay mod-agnostic (`docs/DMZULTRA_ASSET_REUSE.md`).

## Task inbox

`mimo-tasks/inbox/` holds standalone task files run by `mimo-tasks/watch-inbox.ps1`. When a task is
done, move it to `mimo-tasks/done/`, write the report it asks for, then commit and push.
