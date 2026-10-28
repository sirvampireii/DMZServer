# LM player data files (4.6.253+)

LegacyMechanics keeps its durable per-player records in its own files, not in Forge persistent
data (`ForgeData` inside `world/playerdata/<uuid>.dat`). A DMZ reset, a race change, or a wiped
player file cannot remove them.

## Where the records live

| File | What it holds |
|---|---|
| `config/legacymechanics/players/<uuid>.json` | One file per player (new in 4.6.253) |
| `config/legacymechanics/player-data-migration.json` | Completion marker for the one-time move |
| `config/legacymechanics/rivalry-v4.json`, sparring, rival progression | Already LM-owned before 4.6.253, unchanged |
| `config/legacymechanics/character-services-players.json` | Already LM-owned before 4.6.253, unchanged |

Each player file has `uuid`, `updatedAt`, and `records`. Every record is the exact SNBT text of the
NBT value it used to have in `ForgeData`, so types (int, long, string, lists) are preserved:

| Record key | Contents |
|---|---|
| `dmz_adaptive_difficulty` | Unlocked tiers, active tier, titles, active title, title progress (tier progress), settings |
| `lm_progression` | Prestige shop wallet and purchases (`pp_*`), God Ki enhancement, meditation, potential, CNPC migrate mark |
| `prestige_total_completed` | Lifetime prestiges |
| `prestige_total_staff_override` | Staff-set lifetime prestige lock |
| `prestige_need_floor` | Prestige need floor |
| `lm_prestige_held` | Prestige favor |
| `lm_prestige_points` | Prestige points (root mirror) |
| `pp_level_breakthroughs`, `lm_personal_breakthroughs`, `lm_personal_level_cap` | Breakthroughs and personal level cap mirrors |
| `lmNemesisSpawnMode` | Nemesis spawn preference |
| `lm_prestige_confirm_until` | Prestige confirm window (short timer, kept with the rest of the prestige records) |

Code: `data/LmPlayerStore.java` (store), `data/LmPlayerDataMigration.java` (one-time move).
`DifficultyCache`, `ProgressionData.stored*`, `PrestigeSystem`, `PrestigePointsSystem`,
`PersonalLevelCapMirror`, and `NemesisSpawnMode` read and write only through the store.

Files are written when the content changed: on every difficulty save, on the player save event, on
logout, every 30 seconds, and on server stop. Writes go to a temp file and are swapped in.

## Audit of LM writes into DMZ-owned storage

**Forge persistent data on players.** The durable records are the keys in the table above. These
stay on the entity because they are transient, not records:

- Death TP penalty timer: `lm.death_tp_penalty_until`, `lm.death_tp_penalty_frac`.
- Shadow dummy cooldown: `lm_shadow_dummy_cd_until`.
- DMZ combat scratch keys that LM clears: `dmz_raw_damage`, `dmz_defense_pen`, `dmz_block_multiplier`.
- The Noea keys that the God Ki and Evil Eye wipes clear: `NoeaGodKiLedgerIdentity`,
  `godkienhancement`, and the Noea compound. These are Noea's own data.

**Forge persistent data on mobs, bosses and dragons.** Scaling, elite, mutation, boss,
summon-loot, quest and End strength tags are transient entity tags. They are unchanged.

**StatsData and BonusStats.** LM keeps no record of its own here. Every LM write into StatsData,
including BonusStats, skills, effects, race and class, TP and resources, is DMZ game state. Each
one is derived from an LM record that lives in the files above or in an LM JSON store, for example:

- `pp_custom_*` rebuilds the `lm_pshop:*` BonusStats rows;
- `pp_skill_*` sets skill levels;
- `pp_perm_majin` / `pp_perm_mutant` set the matching effects.

No mixin persists an extra field with StatsData.

**Absorption and God Ki.** Their reset rules are unchanged. The wipes still clear DMZ, Noea and
`lm_progression` entries through the same calls, which now act on the LM file.

## One-time migration

The move runs on server start (`ServerStartingEvent`), before anyone can join, and only if
`player-data-migration.json` does not exist.

1. It reads the playerdata folder from the running server (`getWorldPath(PLAYER_DATA_DIR)`); no
   world name is hardcoded.
2. For each `<uuid>.dat`, it copies the record keys above from `ForgeData` into
   `players/<uuid>.json`.
   - **No LM keys:** the player is skipped and no file is created.
   - **Missing or unreadable:** the player is logged (`unreadable ... skipped`) and skipped; the
     boot continues.
   - **LM file already exists:** the file is never overwritten; the player is logged.
3. It writes the marker with counts (scanned, migrated, no data, existing, skipped). Later boots
   log `player data migration already done` and skip the pass entirely.
4. The old keys stay in each `.dat` as a rollback fallback. Nothing in the pass deletes or
   rewrites a `.dat`.

There is no per-player migration on login.

**Rollback.** An older jar reads the old `.dat` keys. Those keys are frozen at their 4.6.253 boot
values, so progress made on 4.6.253 or later is not in them. On respawn LM keeps copying them to
the new entity, because Forge itself only carries `PlayerPersisted` across death.

**Staff clear.** `/lm admin clear <player>` clears the LM file. For offline players it still
clears the old `.dat` keys as well, so a rollback cannot bring wiped data back.

## DMZ 2.1.3 reset paths

These results come from DMZ 2.1.3 bytecode: `dragonminez-2.1.3.jar`, disassembled with javap, with
each reset entry point's call graph walked.

| Reset path | Entry | Touches ForgeData? | File I/O? | Can reach LM files? |
|---|---|---|---|---|
| Core reset | `StatsData.resetPlayerProgress` | Removes only DMZ's own `dmzTransformMobEffects` | Creates the `logs/` folder for its own log | No |
| `/dmzstats ... reset` | `StatsCommand.resetStats` → `resetPlayerProgress(p, pct, keepSkills, false)` | Same single key | Logging only | No |
| Dende reset | `NPCActionC2S.handleDende` action 2 → `resetPlayerProgress(p, null, false, true)` | Same single key | Logging only | No |
| Race change | `StatsSyncC2S` (`setRace`, gender, class, appearance) | No | Logging only | No |
| New character | `CreateCharacterC2S` (`initializeWithRaceAndClass`) | No | No | No |
| Appearance update | `UpdateCharacterC2S` | No | No | No |

- **What the core reset touches.** It resets only StatsData: stats, status, resources, quests,
  cooldowns, BonusStats, and (without `keepSkills`) skills, effects and techniques.
- **What it never does.** No reset path respawns, clones or kills the player, or rewrites the
  `.dat`. Nothing in the jar clears the whole persistent-data compound.
- **Where DMZ writes files.** Only under `config/dragonminez/`, `<world>/dragonminez/`, the
  Otherworld region folder, `dmzdebug/`, and client caches. `/dmzconfig` takes a single word as
  the file name, so it cannot climb out of `config/dragonminez/`. No DMZ path reaches
  `config/legacymechanics/`.

## Acceptance test (test server)

1. On 4.6.252, give test players prestige favor, lifetime prestiges, titles and tier progress.
   Leave at least one of them offline.
2. Install 4.6.253 and boot with nobody online.
   - The log shows `player data migration done: N ... migrated`.
   - `players/<uuid>.json` exists for every player who had data, with matching values.
   - `player-data-migration.json` exists.
3. Boot again.
   - The log shows `already done; skipped`.
   - The players' files are unchanged (same `updatedAt`).
4. Log in a test player and run a full `/dmzstats` reset, a race change and a Dende reset.
   - Prestige favor, lifetime prestiges, titles, tier progress, rival, sparring and character
     services records are all intact.
