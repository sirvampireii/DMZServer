# Uploaded Script & Jar Inventory

Your uploads are primarily **CustomNPCs (Nashorn) player / trigger scripts**, bridging **DragonMineZ** Forge APIs with **Bukkit plugins** (Fabled, CMI). That requires a **hybrid** server (Arclight / Mohist / similar), not plain Forge alone.

You also asked for **KubeJS** — that stack is installed under `mods/` for Forge-side scripting in parallel.

---

## Layout after organization

| Path | Contents |
|------|----------|
| `customnpcs/scripts/*.js` | All 38 uploaded scripts (CNPC) |
| `mods/CustomNPCs-*.jar` | CustomNPCs Forge mod |
| `mods/kubejs-*`, `rhino-*`, `architectury-*` | KubeJS Forge 1.20.1 stack |
| `mods/dragonminez-*` + deps | DMZ 2.1.3 |
| `plugins/CMI-*.jar` | CMI Bukkit plugin |
| `plugins/fabled-*.jar` | Fabled Bukkit plugin |
| `uploads/` | Original copies |
| `kubejs/` | Empty starter dirs for true KubeJS scripts |

---

## Missing hard dependencies (not in upload)

| Missing | Needed by |
|---------|-----------|
| **Hybrid core** (Arclight 1.20.1 / Mohist / etc.) | `org.bukkit.Bukkit` + Forge together |
| **CMILib** | CMI |
| **CodexCore** | Fabled (`depend: [CodexCore]`) |
| **Apotheosis AttributesLib** (`attributeslib`) | `KiWeapons.js`, `Piercing.js`, `Apothicfireandcolddamage.js` |
| Optional: Pam’s HarvestCraft | crop IDs in `Farming TP Skill.js` |

---

## How your scripts talk to DMZ

Canonical pattern (from your own files):

```javascript
var StatsProvider = Java.type("com.dragonminez.common.stats.StatsProvider");
var StatsCapability = Java.type("com.dragonminez.common.stats.StatsCapability");
var StatsSyncS2C = Java.type("com.dragonminez.common.network.S2C.StatsSyncS2C");
var NetworkHandler = Java.type("com.dragonminez.common.network.NetworkHandler");

var mcPlayer = player.getMCEntity(); // CNPC IPlayer -> ServerPlayer
var data = StatsProvider.get(StatsCapability.INSTANCE, mcPlayer).orElse(null);

data.getSkills().setSkillLevel("fly", 5);
data.getResources().addTrainingPoints(10);
data.getBonusStats().addBonus("STR", "MyBonus", "*", 1.10);
data.getStatus().isChargingKi();
data.getMaxEnergy();

NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(mcPlayer), mcPlayer);
```

Fabled bridge (hybrid only):

```javascript
var bukkitPlayer = Bukkit.getPlayer(UUID.fromString("" + player.getUUID()));
var plugin = Bukkit.getPluginManager().getPlugin("Fabled");
var fabledClass = plugin.getClass().getClassLoader().loadClass("studio.magemonkey.fabled.Fabled");
// reflect getData(bukkitPlayer) -> attributes / mana / persistent data
```

CNPC trigger commands (from your handlers):

```text
noppes script trigger <id> <playerName> [args...]
```

Examples in your pack: SkillCheck `21`, Meditation cycler `41`, Rival `200–230`.

---

## Script roles

### Global Player scripts (`tick` / combat / login)

| Script | Hooks | Purpose |
|--------|-------|---------|
| Fly / Jump / Sprint / ViltrumiteFly | tick | Skill leveling / flight rules |
| Meditation new | tick, damaged | Meditation trials + biomes |
| DMZ Energy | tick | DMZ energy ↔ Fabled mana |
| Attr Fabled Multi bonus | tick | Fabled attrs → DMZ `BonusStats` multipliers |
| DMZ Stat Screen | tick | Push DMZ combat stats into Fabled persistent data |
| DMZ RACE LOCK / Races / Yardrat* / BioAndroid | tick | Race / form gates |
| Potential | damaged | Potential unlock combat logic |
| KiWeapons / Piercing / Spirtualist Ki Control | tick/damaged | Ki weapon / pierce / spiritualist |
| TP IS SP Fabled / Farming TP Skill | tick/broken | TP economy |
| Sparring Tp System | tick, logout, died | Sparring TP |
| Rival System | init/login/tick/damaged/kill/died/logout | Rivalry core |
| ShadowDummyLimiter / End Dimension Strength | tick, kill | Dummy / End strength |
| flight suppression | tick | Suppress flight |
| Prestige / Faction / Value Cleaner / Fabled sync | tick | Prestige ↔ Fabled |

### NPC / trigger scripts (not global player slot)

| Script | Notes |
|--------|-------|
| SkillUnlockNPC / PlayerStatChecker | `interact` |
| SkillCheckCommand / Rival Command Handler / Sparring Command Handler | `noppes script trigger` command handlers |
| ChangeBiomeMED | trigger `41` meditation biome cycle |
| Apothicfireandcolddamage / damageovertime | damage helpers |

Global TP Boost, TP boost end and AndrioidConversion (triggers 30, 31, 45) were stubs forwarding to LM and were deleted from the repo on 2026-11-26; LM runs them (`/lm admin progression boost start|end`, `/lm admin progression android`).

Install player scripts via CustomNPCs **Global Player Scripts** UI (or equivalent). Keep command handlers in the same scripts folder but wire them through triggers / CMI aliases — see comments inside those files.

---

## KubeJS vs CustomNPCs

| | CustomNPCs (your current pack) | KubeJS (installed) |
|--|-------------------------------|--------------------|
| Engine | Nashorn via CNPC | Rhino via KubeJS |
| Player API | `event.player`, `getTempdata()`, `getMCEntity()` | `Player`, Forge/`NativeEvents` |
| Where scripts live | `customnpcs/scripts/` | `kubejs/server_scripts/` etc. |
| Fabled/CMI | Works on hybrid via `Bukkit` | Same if hybrid; pure Forge cannot load those plugins |

A starter DMZ helper for KubeJS is in `kubejs/server_scripts/examples/dmz_bridge_example.js` (disabled by default — rename/enable when ready).

**Mohist melee-until-death:** see [`MOHIST_MELEE_BUG.md`](MOHIST_MELEE_BUG.md) and `mods/dmz_mohist_melee_fix-1.0.0.jar`.

Full Java event/method map: [`DMZ_API_REFERENCE.md`](DMZ_API_REFERENCE.md).
