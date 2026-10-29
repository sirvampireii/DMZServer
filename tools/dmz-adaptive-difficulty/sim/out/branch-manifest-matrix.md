# Branch manifest matrix

Owner-request markers from [LM_SHIP_MANIFEST.md](../../../docs/LM_SHIP_MANIFEST.md).
✓ = present at branch tip · ✗ = missing or regressed

| Branch | LM ver | prestige-held-need | prestige-veteran-gate | prestige-gui-need | prestige-no-cap-chest | skillcheck-no-bypass | skillcheck-plugin-gate | rival-challenge-pending | rival-challenge-backend | rival-chest-challenge-pending | rival-declare-decide | spar-pending-decide | spar-dojo-war-decide | spar-no-end-session | manifest-doc | deploy-gate | build-prestige-jar-overlay | manifest-javap-prestige | admin-no-cnpc-import-chest | gui-tp-audit-script | rival-challenge-persist | dojo-war-offline | build-backend-ship-overlay | android-no-saiyan-deny | android-gui-copy | manifest-android-row | manifest-preview-row | class-stamina-config-scale | class-stamina-mixin-registered | death-tp-penalty | death-tp-penalty-mixin | score |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **main** | 4.6.255 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | 30/30 |
| main | 4.6.255 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | 30/30 |

## Interpretation

- **Best tip:** `main` (30/30, LM 4.6.255)
- **`main` matches best** — safe canonical for deploy.

## Branch regressions (vs best tip)

- None — every scanned branch includes all markers from the best tip.

## Extra markers on feature branches (not on main)

- None — merge feature branches into `main` if version is ahead.

## Retired checks (not scored)

- `cnpc-defer-reopen` — CnpcGuiSupport.java (removed)
- `skillcheck-no-redundant-ui` — CnpcLmSkillCheckGui.java (removed)
- `admin-no-cnpc-import-cnpc` — CnpcLmAdminGui.java (removed)
- `cnpc-flash-widget-ids` — CnpcGuiSupport.java (removed)
- `rival-tp-copy` — CnpcLmRivalGui.java (removed)
- `difficulty-tier-personal-gate` — CnpcLmDifficultyGui.java (removed)
- `rival-challenge-reopen` — CnpcLmRivalGui.java (removed)
- `cnpc-preview-live-sync` — CnpcPlayerPreview.java (removed)
- `prestige-tier-cnpc-ids` — CnpcLmPrestigeGui.java (removed)
