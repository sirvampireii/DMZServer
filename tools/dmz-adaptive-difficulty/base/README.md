# Forge mod working base (consolidated 4.5.49+)

**Do not reimplement ki/stamina overflow fixes in isolation.** Builds merge CNPC/GUI patches from `src/` onto this jar.

| File | Role |
|------|------|
| **`LegacyMechanics-4.5.49-consolidated.jar`** | Canonical base (ki/stamina + CNPC line deployed 2026-09-22) |
| `LegacyMechanics-4.5.23-direct-dmz-resource-max.jar` | Legacy fallback only if consolidated base missing |

`build.sh` merges CNPC/GUI from `src/` and overlays the ki/stamina pool slice plus **`legacymechanics.mixins.json`**. That config must **not** register `StatsDataHudPoolMaxMixin` or `StatsDataOverhaulCombatScaleMixin`. Prestige scale stays with DragonMineZ and dmzrevamp.

Refresh from live after a good deploy:

```bash
bash scripts/pull-lm-base-jar.sh
# or copy last good build:
bash scripts/refresh-lm-consolidated-base.sh
```

Output artifact: **`mods/LegacyMechanics-${VERSION}.jar`** (not the old `direct-dmz-resource-max` filename).

## Weapon hotpath classes (source-compiled, byte-verify baseline)

The nine `kube/hotpath` weapon classes now have **committed source** (reconstructed from the
audited live bytecode; the old overlay workflow is **retired**). `build.sh` no longer copies
bytecode — the classes compile from `src/` like everything else.

| Classes | Source |
|---------|--------|
| `WeaponBonusScale` + `$Info` + `$Result` | `src/.../kube/hotpath/WeaponBonusScale.java` |
| `WeaponBonusTables` + `$WeaponDef` | `src/.../kube/hotpath/WeaponBonusTables.java` |
| `WeaponRatioConfig` + `$Json` + `$Snapshot` | `src/.../kube/hotpath/WeaponRatioConfig.java` |
| `VipersCallBlacklist` | `src/.../kube/hotpath/VipersCallBlacklist.java` |

A **byte-verify guard** keeps them honest: every build checks that all nine shipped classes are
byte-identical to the committed baseline in **`reference/weapon-hotpath-4.6.229/`**, so a future
refactor can never silently change or drop them (a dropped class fails the presence check; any
drift fails the sha256 compare). The guard is **fail-closed** — if the baseline is missing it
errors rather than shipping unverified classes.

**Re-baseline after a good live deploy** (and after any *intended* source change) so the next
build inherits the current classes:

```bash
LM_WEAPON_SEED=1 ./build.sh   # extract the 9 classes from the fresh jar into reference/weapon-hotpath-4.6.229/
git add reference/weapon-hotpath-4.6.229   # commit the updated baseline
```

The baseline was reconstructed by decompiling the live 4.6.227 bytecode (CFR) and restoring the
types CFR dropped; the public API round-trips byte-for-byte against the original jar (verified via
`javap -p` diff). The recompiled bytecode is functionally identical but not byte-identical to the
pre-reconstruction live classes — that is expected and why the baseline is re-seeded from the first
validated build rather than from the old `weapon-hotpath-source.jar`.
