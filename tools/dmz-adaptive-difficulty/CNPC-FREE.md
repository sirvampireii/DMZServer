# CNPC-free install (LegacyMechanics replaces all CNPC scripts)

LegacyMechanics is the runtime. **No CustomNPC Global Player / Forge / NPC
scripts are required.** Deleting every CNPC script on the live server is the
intended end state.

Repo copies under `customnpcs/scripts/` and `uploads/scripts/` are **backups /
reference only** — do not load them while matching `enable*` flags are ON.

## What players/staff use instead

| Old CNPC / trigger | Forge replacement |
|--------------------|-------------------|
| Rival System + Command Handler | `/lm rival` · `/lm` → Rival · tag `lm_rival` |
| Sparring TP + Command Handler | `/lm spar` · `/lm` → Spar · tag `lm_spar` |
| Prestige NPC | `/lm prestige` · tag `lm_prestige` |
| SkillCheck / SkillUnlock / trigger 21 | `/lm skillcheck` (perm) · NPC tag `lm_skillcheck` |
| End Dimension Strength / triggers 50–51 | `/lm admin enddragon clear|repair` · `/cleardragons` |
| Global TP Boost / triggers 30–31 | `/lm admin progression boost …` |
| Meditation + ChangeBiomeMED / trigger 41 | Living World circle; trigger is a no-op |
| Android trigger 45 | `/lm admin progression android [player]` |
| Flight / SprintJump / Potential / Farming / combat / Fabled bridges / Race Lock / Yardrat / ShadowDummy / StatChecker | automatic (Forge events) |

## GUI NPCs (no scripts on the NPC)

Prefer **scoreboard tags** (right-click opens + cancels default dialog):

| NPC | Tag | Or display name contains |
|-----|-----|---------------------------|
| Skill Check | `lm_skillcheck` | `Skill Check` / `SkillCheck` / `Skill Progress` |
| Rival | `lm_rival` | `Rival` / `Rival System` / `Rival NPC` |
| Spar | `lm_spar` | `Spar` / `Sparring` |
| Prestige | `lm_prestige` | `Prestige` / `Prestige NPC` |
| Hub | `lm_hub` | `Legacy Mechanics` / `LM Hub` |
| Difficulty | `lm_difficulty` | `Difficulty` / `Adaptive Difficulty` |

Example:
```
/tag @e[type=!player,distance=..4,limit=1] add lm_skillcheck
```

Do **not** put Rival tags/names on the Skill Check NPC.

## Remap leftover CMI / dialog commands

If any shop, quest, or CMI alias still runs `noppes script trigger N`, it will do
nothing with scripts deleted. Retarget to the Forge commands in the table above.
Delete CMI aliases for `enddragon` / `cleardragons` / `spawndragon` / `killdragons`
so they do not shadow the mod.

## Optional one-time data

`/lm admin migrate-cnpc` — only if Rival/Spar data still lives in old CNPC
worlddata and has not been imported yet.
