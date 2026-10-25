# Natural Progression & Fabled Bridges (2.3.38)

LegacyMechanics ports CNPC “natural progression” scripts into Forge, plus soft-dependency
**Fabled** bridges (Bukkit / LuckPerms via reflection — never hard-crash if missing).

**Access:** `/progression` / `/skills` are **staff only** (op level 2 or
`difficulty.admin`). Normal players use Difficulty / Rival / Spar / **Prestige** /
Remove Android from `/lm`. Donators with `legacymechanics.donator` get **Skill Check**
(`/skillcheck` or CNPC).

Staff UI (`/progression` / `/prog`) is a **category hub** matching the script families below.

## Install (test)

1. Build / install `LegacyMechanics-2.3.45.jar` (+ matching `LegacyMechanicsGUI-2.3.45.jar`).
2. Keep `enableProgression` / `enableFabledBridge` true (defaults).
3. **Disable** the CNPC Global Player scripts listed below to avoid double-sync / double TP.
4. Do **not** deploy to live until tested.

## Player vs staff commands

| Audience | Commands |
|----------|----------|
| All players | `/lm`, `/difficulty`, `/rival`, `/spar`, `/lm` → Prestige · Remove Android |
| Donators | `/skillcheck` — requires LuckPerms `legacymechanics.donator` |
| Staff | `/progression` · `/prog` · `/skills` · Flags / Logs · Android convert |

## Skill Check (donators)

- Permission: `legacymechanics.donator` (plugin.yml default **false**; grant via LuckPerms).
- Staff are **not** auto-granted — without the node they see **Skills** (admin) on the hub, not Skill Check.
- Config (`legacymechanics.json`): `enableSkillCheck`, `donatorPermission`,
  `skillCheckNpcNameContains` (default needles: `Skill Check`, `SkillCheck`, `Skill Progress`).
- Reuses the same skill progress lines as staff `/skills` (core / saga) with
  **Skill Check** branding.
- Hub shows **Skill Check** only when the player has the permission.

### CNPC setup (Skill Check)

Any of:

1. Scoreboard / entity tag containing `lm_skillcheck` (case-insensitive)
2. Custom name / display name containing a configured needle (defaults above)
3. Dialog / quest: `noppes script trigger 21 <player>`
4. NPC command (player): `skillcheck` or `lm open skillcheck`

Right-click a marked CustomNPC → Skill Check UI for **any** player (NPC is the gate).
Slash `/skillcheck` still needs `legacymechanics.donator`.

### CNPC setup (Rival / Spar / Hub / Difficulty / Prestige)

Right-click opens the GUI for **all players** (no permission). Match by tag or name:

| System | Tag examples | Name contains |
|--------|--------------|---------------|
| Hub | `lm_hub` | Legacy Mechanics, LM Hub |
| Rival | `lm_rival` | Rival, Rival System |
| Spar | `lm_spar` | Spar, Sparring |
| Difficulty | `lm_difficulty` | Difficulty |
| Prestige | `lm_prestige` | Prestige |

Or NPC interaction command **as the player** (no `/`): `lm` · `rival` · `spar` · `difficulty` · `prestige`

### CNPC setup (staff Skills admin)

Staff `/skills` has no special CNPC name hook — use an NPC interaction command as the player:

- `skills`
- or `lm open skills`

## Staff UI pages (`/progression`)

| Page id | Title | Contents |
|---------|-------|----------|
| `main` | Hub | Status header (boost + meditation) · category buttons · Prestige/Skills shortcuts · staff Flags |
| `skills` | Skills | flight, sprint, meditation, potential |
| `tp` | TP Gains | boost, bio |
| `race` | Race & Form | racelock, yardrat, spiritualist, android (→ Android Tools) |
| `android_panel` | Android Tools | Convert · Remove (staff) |
| `android_convert` | Android Convert | player picker → Gero convert |
| `android_remove` | Remove Android | player picker → two-click confirm remove |
| `combat` | Combat | kiweapons, piercing, dot, apothic |
| `end` | End | end (End Dimension Strength), endportal |
| `shop` | Shop | prestige + skills status · buttons open `/prestige` and `/skills` |
| `fabled` | Fabled Bridges | master + energy/stat/tpsp/attr/prestige skill/faction/cleaner/raceclass/classperm |
| `utility` | Utility | shadow, playerstatchecker |
| `admin` / `flags` | Staff Flags | toggles grouped by the same categories (+ `flags_fabled` for bridge subs) |
| `help` | Help | command list |

## Config flags (`config/legacymechanics.json`)

### Skills

| Flag | Default | Module |
|------|---------|--------|
| `enableProgression` | true | Master switch. Potential Unlock is the only natural skill still in this mod |
| `enablePotential` | true | Potential Unlock |

### TP Gains

| Flag | Default | Module |
|------|---------|--------|
| `enableGlobalTpBoost` | true | Global TP Boost / End TP boost |
| `enableBioAndroid` | true | Bio-Android absorb |

### Race & Form

| Flag | Default | Module |
|------|---------|--------|
| `enableRaceLock` | false | Retired. Race lock is removed and the flag stays off. |
| `enableYardrat` | true | Yardrat race / skills |
| `enableSpiritualistKi` | true | Spiritualist Ki Control |
| `enableAndroidConversion` | true | Android convert + remove |

### Combat

| Flag | Default | Module |
|------|---------|--------|
| `enableKiWeapons` | true | KiWeapons |
| `enablePiercingBonus` | true | Piercing |
| `enableDotExtraDamage` | true | Damage-over-time extra |
| `enableApothicElemental` | true | Apothic fire/cold damage |

### End

| Flag | Default | Module |
|------|---------|--------|
| `enableEndDimensionStrength` | true | End Dimension Strength |
| `enableEndPortalGuard` | true | Disable vanilla End portals / gateways / Eye frames |
| `enableEndMobScaling` | false | End mob HP/DEF scaling (off by default) |

### Shop / Skill Check

| Flag | Default | Module |
|------|---------|--------|
| `enablePrestigeSystem` | true | Prestige levels (staff UI) |
| `enableSkillUnlockService` | true | Skill unlock progress data |
| `enableSkillCheck` | true | Donator Skill Check UI / CNPC |
| `donatorPermission` | `legacymechanics.donator` | LuckPerms node |
| `skillCheckNpcNameContains` | Skill Check / SkillCheck / Skill Progress | CNPC name needles |

### Fabled Bridges

| Flag | Default | Module |
|------|---------|--------|
| `enableFabledBridge` | true | Master for Fabled soft bridges |
| `enableEnergyManaSync` | true | Energy ↔ Mana |
| `enableStatScreenSync` | true | Stat Screen → persistent values |
| `enableTpSpMirror` | true | TP ↔ SP |
| `enableAttrMultiBonus` | true | Attr multiplicative bonuses |
| `enablePrestigeSkillSync` | true | Prestige class → DMZ skill |
| `enablePrestigeFactionSync` | true | Prestige → CNPC faction 4 |
| `enableValueCleaner` | true | Persistent value cleaner |
| `enableRaceClassSync` | true | Race → Fabled class + auto race skill |
| `enableClassPermissionSync` | true | Class → LuckPerms `fabled.skill.*` |

### Utility

| Flag | Default | Module |
|------|---------|--------|
| `enableShadowDummyLimiter` | true | Shadow dummy limiter |
| `enablePlayerStatChecker` | true | Sneak+RMB player → DMZ stat dump |

## Java packages

| Package | Role |
|---------|------|
| `progression/` | `ProgressionSystem`, `ProgressionConfig`, `ProgressionData`, `PlayerStatChecker`, helpers |
| `progression/skills/` | Flight, SprintJump, Meditation, Potential |
| `progression/tp/` | GlobalTpBoost, BioAndroidAbsorb, DeathTpPenalty |
| `progression/race/` | RaceLock, Yardrat, SpiritualistKi, AndroidConversion |
| `progression/combat/` | KiWeapons, Piercing, DOT, Apothic elemental |
| `progression/end/` | End Dimension Strength, EndPortalGuard |
| `progression/shop/` | Prestige / SkillUnlock / **SkillCheckService** |
| `progression/dummy/` | Shadow dummy limiter |
| `progression/bridge/` | **Fabled soft-dependency bridges** |

### Fabled bridge classes

| Class | CNPC source |
|-------|-------------|
| `FabledBridge` | Facade (availability, pulse, Bukkit/Fabled resolve) |
| `EnergyManaSync` | `DMZ Energy.js` |
| `StatScreenSync` | `DMZ Stat Screen.js` |
| `TpSpMirror` | `TP IS SP Fabled.js` |
| `AttrMultiBonus` | `Attr Fabled Multi bonus.js` |
| `PrestigeSkillSync` | `Prestige Sync Fabled.js` |
| `PrestigeFactionSync` | `Fabled Prestige Faction Sync.js` |
| `ValueCleaner` | `Universal Fabled Value Cleaner.js` |
| `RaceClassSync` | `Races.js` (+ race skill ensure/grant) |
| `RaceSkillSync` | (new) DMZ races → Fabled Race skills |
| `ClassPermissionSync` | `DMZ Class Permission.js` |

## CNPC scripts to DISABLE on test

### Skills (when `enableProgression` is on)

- `Fly` / `ViltrumiteFly` / `flight suppression` / `Flight`
- `JumpSprint` / `Jump` / `Sprint`
- `Meditation new`
- `Potential`

### TP Gains

- `Farming TP Skill` — not in LegacyMechanics; leave the stub disabled
- KubeJS `building_tp_place` / `building_tp` — not in LegacyMechanics; leave disabled
- `Global TP Boost` / `TP boost end`
- `BioAndroid`

### Race & Form

- `DMZ RACE LOCK`
- `YardratRace` / `YardratSkills`
- `Spirtualist Ki Control`
- `AndrioidConversion`
- Android Upgrade Removal CNPC (use `/progression android remove` / GUI instead)

### Combat

- `KiWeapons` / `Piercing` / `damageovertime` / `Apothicfireandcolddamage`

### End

- `End Dimension Strength`
- `Disable End Portals`

### Shop

- Prestige NPC / SkillUnlockNPC (when shop ports are active)
- SkillCheckCommand.js (replaced by `/skillcheck` + trigger 21 + CNPC interact)

### Fabled bridges (when `enableFabledBridge` is on)

- `DMZ Energy`
- `DMZ Stat Screen`
- `TP IS SP Fabled`
- `Attr Fabled Multi bonus`
- `Prestige Sync Fabled`
- `Fabled Prestige Faction Sync`
- `Universal Fabled Value Cleaner`
- `Races`
- `DMZ Class Permission`

### Utility

- `ShadowDummyLimiter` / related forge protect scripts
- `PlayerStatChecker` (sneak + right-click inspect)

### Already in Java (rival / spar)

- `Rival System`, `Sparring Tp System`, `Rival Command Handler`, `Sparring Command Handler`

## Chat / telemetry

- `/lm` hub → Difficulty · Rival · Spar · Help (+ Skill Check for donators; staff Progression/Prestige/Skills/Logs)
- Staff: `/prog do page skills|tp|race|combat|end|shop|fabled|utility`
- Staff: `/prog do page admin` · `/prog admin <flag> on|off`
- System telemetry (`enableSystemTelemetry`): rate-limited `system=fabled` events
  (`login_sync`, `energy_spend`, `tp_sp_spend`, `prestige_skill`, `prestige_faction`,
  `race_class`, `race_skill`, `class_perm_grant`, `value_clean`)

## API notes

Fabled calls use `studio.magemonkey.fabled.Fabled` / `PlayerData` from the live jar
(`getData`, `getPoints`/`setPoints`, `getMana`/`setMana`, `getAttribute`,
`setPersistentData`, `setClass`, `getSkillLevel`, etc.).

**Guessed / reflection-only (not public API):**

- Writing `maxMana` via private field (no public `setMaxMana` on this Fabled build)
- LuckPerms node mutate via reflected `Node.builder` + `user.data().add/remove`
- CNPC faction points via `NpcAPI` / `PlayerData.get` + `PlayerFactionData`
