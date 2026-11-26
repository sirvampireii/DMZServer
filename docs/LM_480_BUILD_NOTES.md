# LM 4.8.0 build notes

Version bump only: LM 4.7.88 renumbered as 4.8.0 at the owner's request. No behaviour change, so no manifest row.

| | |
|---|---|
| Branch | `lm-480-version` (off `main` at 02b5e4cd, the 4.7.88 merge) |
| Content | identical to 4.7.88 (see `docs/LM_4788_BUILD_NOTES.md`) |

- `bump-version.sh 4.8.0`: `AdaptiveDifficultyMod.VERSION`, `mods.toml`, `MANIFEST.MF`, `plugin.yml`.
- `rebaseline-version-class.sh`: the prestige reference copy of `AdaptiveDifficultyMod.class` (version string only).
- `tools/lm-ui-probe/run.sh`: the boot-log line match took only `4.7.x`; it now takes any version.
- The in-game checks owed for 4.7.88 are still owed for 4.8.0.
- `UiProbe` HUD client check: the expected list now has `war` (the 4.7.87 War pill). The first 4.8.0 run drew on a
  larger client screen (640x343) where the pill had room, and the check failed because `war` was not expected.

## Release check

`DMZRig/results/release-4.8.0`: **379 PASS, 0 FAIL**, 0 matrix rows failing (`--no-build`, the jar the full run
built, sha256 `a2bb4f0b364f8cf99eddc6a6399099d670140471cbc77369ebbed901b9faf463`). The first run with the old probe
is kept in `release-4.8.0-run1` (378/1, the War pill case above).
