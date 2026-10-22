# Branch manifest matrix

Owner-request markers from [LM_SHIP_MANIFEST.md](../../../docs/LM_SHIP_MANIFEST.md).
✓ = present at branch tip · ✗ = missing or regressed

| Branch | LM ver | prestige-held-need | prestige-veteran-gate | prestige-gui-need | prestige-no-cap-chest | skillcheck-no-bypass | skillcheck-plugin-gate | rival-challenge-pending | rival-challenge-backend | rival-chest-challenge-pending | rival-declare-decide | spar-pending-decide | spar-dojo-war-decide | spar-no-end-session | cnpc-defer-reopen | manifest-doc | deploy-gate | build-prestige-jar-overlay | manifest-javap-prestige | skillcheck-no-redundant-ui | admin-no-cnpc-import-chest | admin-no-cnpc-import-cnpc | cnpc-flash-widget-ids | rival-tp-copy | gui-tp-audit-script | difficulty-tier-personal-gate | rival-challenge-persist | rival-challenge-reopen | dojo-war-offline | build-backend-ship-overlay | android-no-saiyan-deny | android-gui-copy | cnpc-preview-live-sync | manifest-android-row | manifest-preview-row | prestige-tier-cnpc-ids | class-stamina-config-scale | class-stamina-mixin-registered | death-tp-penalty | death-tp-penalty-mixin | score |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **main** | 4.6.225 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ | ✓ | ✓ | ✓ | ✓ | ✗ | ✓ | ✗ | ✗ | ✗ | ✓ | ✗ | ✓ | ✗ | ✓ | ✓ | ✓ | ✓ | ✗ | ✓ | ✓ | ✗ | ✓ | ✓ | ✓ | ✓ | 30/39 |
| main | 4.6.225 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ | ✓ | ✓ | ✓ | ✓ | ✗ | ✓ | ✗ | ✗ | ✗ | ✓ | ✗ | ✓ | ✗ | ✓ | ✓ | ✓ | ✓ | ✗ | ✓ | ✓ | ✗ | ✓ | ✓ | ✓ | ✓ | 30/39 |

## Interpretation

- **Best tip:** `main` (30/39, LM 4.6.225)
- **`main` fails matrix:** cnpc-defer-reopen, skillcheck-no-redundant-ui, admin-no-cnpc-import-cnpc, cnpc-flash-widget-ids, rival-tp-copy, difficulty-tier-personal-gate, rival-challenge-reopen, cnpc-preview-live-sync, prestige-tier-cnpc-ids

## Branch regressions (vs best tip)

- None — every scanned branch includes all markers from the best tip.

## Extra markers on feature branches (not on main)

- None — merge feature branches into `main` if version is ahead.
