# Commit the Prestige Custom Shop source (stops repo builds from dropping the shop)

## Problem

The 4.6.228.1 and 4.6.229 jars were built from source that was never committed. A build from
`main` compiles without `PrestigeCustomShop` / `PrestigeShopConfig` / `PrestigeShopHooks`, passes
every audit (the byte-verify guard in `build.sh` stays dormant until `PrestigeShopConfig.java`
exists), and silently ships a jar with no Custom Shop.

The source is in this working tree, uncommitted. Commit it. Do not rewrite it.

## Steps

1. `git pull` first so you are on the latest `main`.
2. Create branch `lm-prestige-custom-shop-source` from `main`.
3. Stage exactly these 12 files, normalizing line endings to LF (`git -c core.autocrlf=input add -- <files>`;
   the repo stores LF and these working copies are CRLF):
   - `tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeCustomShop.java` (new)
   - `tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeShopConfig.java` (new)
   - `tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeShopHooks.java` (new)
   - `tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/command/PrestigeAdminCommandTree.java`
   - `tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/config/ConfigRegistry.java`
   - `tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/config/editor/ConfigEditor.java`
   - `tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/config/editor/ConfigFieldHelp.java`
   - `tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/gui/ProgressionGuiApi.java`
   - `tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/gui/ultra/UltraPrestigeScreens.java`
   - `tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/progression/shop/GodKiEnhancementShop.java`
   - `tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigePointsSystem.java`
   - `tools/dmz-adaptive-difficulty/sim/audit_ultra_actions.py`
4. Do **not** stage the modified `.class` files under `tools/dmz-adaptive-difficulty/reference/prestige-shop-228.1/`,
   `reference/README.md`, `sim/out/*`, `tools/ultra-gui-kit/stubs/software/`, `push-shop-source.ps1`, or `.claude-upload/`.
   The reference set is re-baselined separately after a passing build.
5. Commit with the message `Commit the Prestige Custom Shop source (4.6.228.1 / 4.6.229)` and push the branch
   (`git push -u origin lm-prestige-custom-shop-source`). Do not merge to `main` and do not touch the live server.
6. Switch back to `main` and move this file to `mimo-tasks/done/`, commit that, push.

## Report

Write `mimo-tasks/done/2026-10-25-commit-prestige-custom-shop-source.result.md` with: the branch name, the commit
SHA, `git show --stat` for the commit, and anything you could not do.
