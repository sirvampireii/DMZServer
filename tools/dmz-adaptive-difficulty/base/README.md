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

## Weapon hotpath classes (bytecode-authoritative overlay)

Nine `kube/hotpath` classes must ship the **audited live bytecode**. They fall into two
categories, and a clean compile gets **both** wrong, so `build.sh` overlays all nine
unconditionally from **`base/weapon-hotpath-source.jar`** (gitignored) and then verifies each
shipped class is **byte-identical** to that jar — failing the build loudly on any drift.

| Category | Classes | What a clean compile produces |
|----------|---------|-------------------------------|
| **Bytecode-only** — no repo source at all | `WeaponRatioConfig` + `$Json` + `$Snapshot`, `VipersCallBlacklist` | Absent from the jar → live weapon-ratio / viper-blacklist work silently lost |
| **Stale source** — `.java` exists but compiles to bytecode that **differs from live** | `WeaponBonusScale` + `$Info` + `$Result`, `WeaponBonusTables` + `$WeaponDef` | Present but **wrong** → live weapon-scale work silently reverted |

Because the source-backed copies are *stale* (present-but-wrong), a mere presence check is
insufficient — that is exactly the silent-revert bug the guard exists to prevent. The guard
therefore replaces **all nine** every build and compares sha256 of each against the source jar.
Override the source path with `LM_WEAPON_SOURCE_JAR=/path/to/good.jar`.

**Refresh the source jar after every good live deploy** so the next build inherits the current
classes:

```bash
cp mods/LegacyMechanics-<good-version>.jar tools/dmz-adaptive-difficulty/base/weapon-hotpath-source.jar
# or, if the local jar was pruned by a build, pull it from live first:
#   fetch mods/LegacyMechanics-<good-version>.jar over SFTP, then copy as above
```

If you ever recover the correct `.java` source for these classes, drop it into `src/`. The
source-backed five will then compile to the live bytecode; keep the source jar refreshed (or
delete the overlay block) once the compile is byte-identical on its own.
