# End Dragon — Adaptive Difficulty summon (2.3.80)

## Player path (Difficulty GUI only)
- Button: **Summon End Dragon** on `/lm difficulty` main page
- Requirements:
  - Personal Adaptive Difficulty **ON**
  - Active Unlock Tier **T4–T7** (Prestige 4 unlocks T4 eligibility — still must buy/activate)
  - Standing in **The End**
  - Cost: **3 Ancient Netherite** coins (pay-up OK, change returned)
- Dragon is painted with the summoner's **Adaptive Difficulty boss profile**:
  - HP / ATK / Armor from `PlayerCombatProfile.targetMob*` × `bossStatMultiplier` × 1.25 dragon pad
  - Live retarget on form/stat changes (same signature path as nearby AD mobs)
  - **No** legacy End Strength HP log-curve or DEF sponge / hit-cap
- **Only the summoner** can damage it (anti-farm)
- Ki beam / blast use painted AD attack
- **Multi-player:** each eligible player may summon their own dragon. Spawn is **near the summoner** (player position + ~16 Y), including off the main island — not forced to `0,0`.
- Hygiene keeps **at most one living dragon per summoner** and never culls another player's fight.

## Staff
- **No staff spawn** — `/lm admin enddragon spawn` and `/spawndragon` are not commands. Players summon from the Difficulty menu.
- Clear: `/cleardragons`, `/killdragons`, `/lm admin enddragon clear|cleanup`
- Repair exit podium: `/lm admin enddragon repair`
- Bare `/lm admin enddragon` prints staff usage help

## Config
- `enableEndPlayerDragonSummon` (default true)
- `endDragonSummonNetheriteCost` (default 3)
- `enableEndNaturalDragonSpawn` (default **false** — GUI is the player path)
- `endEnforceSingleDragon` (default true) — per-summoner cap + strip unauthorized vanilla dragons
- `bossStatMultiplier` (default 1.5) — shared with AD bosses

## Fight AI + AD isolation
- Player-summoned dragons focus the summoner: faster ki cadence, strafe/charge phase steering, optional beam+blast combo.
- While your summoned dragon is alive, **nearby Adaptive Difficulty mob scaling is suspended** for you (reverts claimed mobs).

## Despawn
- Despawn when the summoner: turns personal difficulty **OFF**, **dies**, goes offline, or stops participating in AD.
- Auto / vanilla End Dragon spawning is **removed**.

## Loot (all entities/ender_dragon mods) + XP
- Player-summon kills **force-roll** `minecraft:entities/ender_dragon` at the death position via Forge’s patched loot API, so **every** loot-table / GLM mod applies, including:
  - **Iron’s Spellbooks** (dragonskin, arcane essence via global loot modifier)
  - **Simply Swords / Simply More** (unique weapon injects)
  - Any other mod injecting into that table
- Vanilla `LivingDrops` for GUI summons are cleared then replaced by that single roll (no doubles).
- AD zero-HP force-kill is skipped for Ender Dragons (`EnderDragon.kill()` would strip loot/XP).
- Vanilla dragon **XP** (12 000 first End kill / 500 after) is awarded at the **death position**; dying animation is stopped so XP is not double-dropped at the main island.
