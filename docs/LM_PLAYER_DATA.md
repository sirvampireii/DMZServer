# LM player data files (4.6.253+)

LegacyMechanics keeps its durable per-player records in its own files, not in Forge persistent
data (`ForgeData` inside `world/playerdata/<uuid>.dat`). A DMZ reset, a race change, or a wiped
player file cannot remove them.

## Where the records live

| File | What it holds |
|---|---|
| `config/legacymechanics/players/<uuid>.json` | One file per player (new in 4.6.253) |
| `config/legacymechanics/player-data-migration.json` | Completion marker for the one-time move |
| `config/legacymechanics/player-data-nbt-strip.json` | Completion marker for the one-time `.dat` strip (4.6.259) |
| `config/legacymechanics/player-data-nbt-strip-backup/<uuid>.dat` | The keys the strip removed, per player (the rollback since 4.6.259) |
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
| `prestige_need_floor` | Old prestige need floor; not read since 4.7.2 (the need is the Overhaul cap), kept in the file |
| `lm_prestige_held` | Prestige favor |
| `lm_prestige_points` | Prestige points (root mirror) |
| `pp_level_breakthroughs`, `lm_personal_breakthroughs`, `lm_personal_level_cap` | Old breakthrough counts (not read since 4.7.2, kept in the file) and the level cap mirror for the client |
| `lmNemesisSpawnMode` | Nemesis spawn preference |
| `lm_mastery_points` | Mastery points wallet balance (4.6.257; written only by `MasteryWallet`, flushed on every change) |
| `lm_prestige_confirm_until` | Prestige confirm window (short timer, kept with the rest of the prestige records) |

Code: `data/LmPlayerStore.java` (store), `data/LmPlayerDataMigration.java` (one-time move).
`DifficultyCache`, `ProgressionData.stored*`, `PrestigeSystem`, `PrestigePointsSystem`,
`PersonalLevelCapMirror`, `NemesisSpawnMode`, and `MasteryWallet` read and write only through the store.

Files are written when the content changed: on every difficulty save, on the player save event, on
logout, every 30 seconds, and on server stop. Writes go to a temp file and are swapped in.

## Prestige per character (4.7.3)

DMZ Ultra 0.4.0 lets one account hold several characters. Each character has its own UUID
(`Slots.characterId`); the first character (slot 0) uses the account UUID. These records are kept
per character, in `players/<character-uuid>.json`:

- `lm_prestige_held`, `prestige_total_completed`, `prestige_total_staff_override`,
  `lm_prestige_points` (favor), and `lm_prestige_confirm_until`.
- Every prestige-shop purchase: the `pp_*` keys of the `lm_progression` bag (Permanent Majin,
  Mutant and Ultimate, skill floors, difficulty tiers, God Ki, special perks). `ProgressionData`
  sends every `pp_*` key to the active character's bag (`ProgressionData.shop`), so the shop, the
  re-apply loop and the special-perk reconcile only ever see that character's purchases.
- Everything else stays in the account file: difficulty data, the rest of the `lm_progression`
  bag (flight, meditation, potential), mastery wallet, cap mirrors, Nemesis mode, rival, sparring
  and character services.

Migration: slot 0 is the character that already had the account's prestige and purchases, and it
keeps reading and writing the account file. Every other character starts at 0 held, 0 lifetime,
0 favor and no purchases the first time it is read (`lm_prestige_seeded` marks it); nothing is
copied, so the totals across characters equal the account's values. CNPC faction 4 and the
shop-purchase inference of lifetime prestiges are per account: they are only read for, and faction
4 is only written for, the account character.

On a switch LM, on the next tick, sets the Overhaul count from the new character's held, re-applies
that character's purchases (the special-perk reconcile drops the previous character's attribute
bonuses), and re-checks difficulty tiers, which revokes another character's purchased tiers.

A character Ultra creates (`CharacterEvent.Born`) starts with held 0, lifetime 0, favor 0, no
purchases, and fresh DragonMineZ stats (new `StatsData`, base attributes). LM only does this when
the slot's stored Ultra record is still the blank one `Lifecycle.create` wrote, and when Ultra
knows the slot's creation time. The account file's `lm_character_births` records each handled birth
(slot number and creation time), so a repeated event changes nothing, and a re-created slot (new
creation time) is handled again.

Without Ultra, or with its slots turned off, every record is the account file, as in 4.7.2.
Code: `progression/shop/PrestigeCharacter.java`, `progression/ProgressionData.java` (purchase
routing), and `PrestigeCharacterUltra.java` (the only class that names Ultra's slots API).

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
   - **Not named by a UUID:** not a player file; counted as `notPlayer` and logged.
3. It writes the marker with counts (scanned, migrated, no data, existing, skipped, not player
   files) only when **no player file was skipped** (4.7.0). If any file was unreadable or its LM
   file could not be written, there is no marker and the pass runs again next boot; players moved
   last time already have their LM file, which `writeIfAbsent` never overwrites, so a re-run only
   picks up the ones left behind. Once a pass skips nothing, later boots log
   `player data migration already done` and skip the pass entirely.
4. The old keys stayed in each `.dat` as a rollback fallback until 4.6.259, which strips them
   (see below). Nothing in this pass deletes or rewrites a `.dat`.

There is no per-player migration on login.

**Rollback (4.6.253 to 4.6.258).** An older jar read the old `.dat` keys, frozen at their 4.6.253
boot values. On respawn LM copied them to the new entity, because Forge itself only carries
`PlayerPersisted` across death. 4.6.259 removes both: the keys are stripped from the `.dat` files
and the death copy no longer writes them.

**Staff clear.** `/lm admin clear <player>` clears the LM file. For offline players it still
clears the old `.dat` keys as well, so a rollback cannot bring wiped data back.

## One-time `.dat` strip (4.6.259)

Code: `data/LmPlayerDatStrip.java`. Runs on server start right after the migration pass, before
anyone can join, and only while `player-data-nbt-strip.json` does not exist.

**What it removes**, at the `ForgeData` root only (nested compounds such as `PlayerPersisted`,
`dmzTransformMobEffects`, and Noea / DMZ keys are never touched):

- the 11 durable keys: `dmz_adaptive_difficulty`, `lm_progression`, `prestige_total_completed`,
  `prestige_total_staff_override`, `prestige_need_floor`, `lm_prestige_held`, `lm_prestige_points`,
  `pp_level_breakthroughs`, `lm_personal_level_cap`, `lm_personal_breakthroughs`,
  `lmNemesisSpawnMode`;
- the 3 extra keys the old death copy carried: `lm_prestige_confirm_until`,
  `lm_cnpc_player_migrated`, `lm_shadow_dummy_cd_until`.

`lm.death_tp_penalty_until` and `lm.death_tp_penalty_frac` stay: they are live timers that must
survive relog and respawn, and they are the only keys the death copy still carries.

**Per player:**

1. The `.dat` is read with the server's own gzip NBT IO, one file at a time.
2. **No listed keys:** nothing happens (counted as no data).
3. **Listed keys but no `players/<uuid>.json`:** the player is skipped untouched and logged. The
   `.dat` is their only copy, so it is never stripped without a store file.
4. **Unreadable `.dat`:** counted as skipped, logged, left untouched.
5. Otherwise the listed keys are first written to `player-data-nbt-strip-backup/<uuid>.dat`
   (gzip NBT, written to a temp file and moved into place), then removed, and the `.dat` is
   written to a temp file and moved over the original. Every other tag survives unchanged.

The pass logs each player's removed keys and a totals line (scanned, stripped, skipped, no data),
and writes the marker with the same counts. Store files are never modified. Since 4.7.0 the
strip writes its marker only once the migration marker exists: while the migration is still
retrying a skipped player, that player has no store file, so the strip skips them too and runs
again next boot until they are moved and stripped.

**Idempotent.** A rerun after a crash strips only what is still there. An existing backup is never
overwritten (it was moved into place whole, so it is complete).

**Self-test.** Before touching any file the pass runs `LmPlayerDatStrip.selfTest()`: a fake
`ForgeData` with every listed key plus unrelated keys goes in, the listed keys come out, the
unrelated keys survive byte for byte, a player with no store file is left untouched, and the same
checks run once more on real gzip files in a temp folder. If any check fails, the pass does not run
and no marker is written.

**After it runs there is no `.dat` fallback.** The LM store files are the only live copy, and the
backup folder is the rollback. Vanilla's `<uuid>.dat_old` may still hold the old keys until the
player's next save; LM never reads it.

**Shadow Dummy cooldown.** `ShadowDummyLimiter` still writes its cooldown to Forge data under two
keys with the same value, `lm_shadow_dummy_cd_until` and `dmz_minigame_shadow_dummy_cooldown_until`,
and reads the higher. Stripping the first leaves the second, so a running cooldown is kept. Neither
is carried across a death any more; the limiter's in-memory copy still holds the cooldown until the
next restart.

## DMZ 2.1.3 reset paths

**DragonMineZ 2.2 (4.7.0).** Re-checked against the 2.2 jar: the same entry points, and
`resetPlayerProgress` additionally scales or clears dynamic growth stats and clears story resets.
It still touches only DMZ's own `dmzTransformMobEffects` key in persistent data, and nothing in
the 2.2 jar names `config/legacymechanics/`. `UpdateCharacterC2S` now applies only while the player
holds a pending re-customize wish; LM's reskin and race-change class pick grant one before opening
the editor.

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
