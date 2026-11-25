# LegacyMechanics ↔ CNPC script audit

**CNPC-free is the intended live setup** — see `CNPC-FREE.md`.
Deleting all CustomNPC scripts on the server is correct; the Forge mod owns
every system that used to live in those scripts.

Historical matrix below compares Java to the old script pack (backups under
`uploads/scripts/` + `uploads/script-backups-full/`). Do **not** re-enable those
scripts while matching `enable*` flags are ON.

## Verdict

**Forge-only.** Right-click GUI NPCs use scoreboard tags / strict names
(`CnpcGuiOpener` + `SkillCheckService`). Commands: `/lm rival` `/lm spar` `/lm prestige`
`/lm skillcheck` `/lm admin enddragon` `/lm progression …` `/lm`. No Global Player scripts,
no `LM-SkillCheck-NPC.js`, no `noppes script trigger` required.

If any CMI/shop/dialog still fires `noppes script trigger N`, retarget it to the
Forge command in `CNPC-FREE.md` (with scripts gone those triggers no-op).

---

## Status matrix (historical — scripts = backups only)

| Script | Live server | Java owner | Flag | Parity |
|--------|-------------|------------|------|--------|
| End Dimension Strength.js | DELETED / backup | `EndDimensionStrength` | `enableEndDimensionStrength` | OK vs live 2.12.0 |
| EndDragon-Forge-Trigger.js | DELETED / backup | same + `/lm admin enddragon` | same | OK — Forge cmds |
| Disable End Portals.js | DELETED / backup | `EndPortalGuard` | `enableEndPortalGuard` | OK |
| Global TP Boost.js / TP boost end.js | DELETED / backup | `GlobalTpBoost` | `enableGlobalTpBoost` | OK — `/lm admin progression boost` |
| Meditation + ChangeBiomeMED | DELETED / backup | `MeditationProgression` | `enableMeditation` | OK |
| Potential.js | DELETED / backup | `PotentialProgression` | `enablePotential` | OK |
| AndrioidConversion.js | DELETED / backup | `AndroidConversion` | `enableAndroidConversion` | OK — `/lm admin progression android` |
| Rival System + Command Handler | DELETED / backup | `RivalSystem` / `RivalCommands` | `enableRivalSystem` | OK — `/lm rival` |
| Sparring TP + Command Handler | DELETED / backup | `SparringSystem` / `SparCommands` | `enableSparringSystem` | OK — `/lm spar` |
| Prestige NPC.js | DELETED / backup | `PrestigeSystem` | `enablePrestigeSystem` | OK — `/lm prestige` |
| SkillCheck / SkillUnlock | DELETED / backup | `SkillCheckService` / `SkillUnlockService` | skill flags | OK — tag / `/lm skillcheck` |
| LM-SkillCheck-NPC.js | **not required** | `SkillCheckService` | `enableSkillCheck` | Use tag `lm_skillcheck` |
| Farming / Flight / SprintJump / combat / Fabled / Race / Yardrat / ShadowDummy / StatChecker | DELETED / backup | matching Java | matching flags | OK |

**Pure LM (no CNPC twin):** Adaptive Difficulty core, titles/tiers, Ancient Coins,
GUIs/hub, telemetry, CNPC data migrator, Building TP.

---

## Admin checklist (CNPC-free)

1. Install `LegacyMechanics-*.jar` + matching GUI jar; remove older product jars.
2. Confirm CNPC Global Player / Forge scripts are empty or disabled.
3. Tag GUI NPCs (`lm_rival`, `lm_spar`, `lm_skillcheck`, `lm_prestige`, …).
4. Delete CMI aliases that shadow Forge (`enddragon`, old rival/spar triggers).
5. Remap any remaining `noppes script trigger` shops to Forge commands.
6. Optional: `/lm admin migrate-cnpc` if Rival/Spar data never left CNPC worlddata.

## Parity fixes already shipped

| Issue | Fix |
|-------|-----|
| Mentor invite 24h vs script 2 min | `MENTOR_INVITE_MS = 120_000` |
| Manual meditation 15m vs ChangeBiomeMED 30m | `MANUAL_TRIAL_DURATION_MS` |
| End hit-cap undone / egg clear wiped portals | 2.3.47 |
| Soft `contains("rival")` stole Skill Check | 2.3.47 |
| Spar Stats packed 3 spars into one line | 2.3.49 individual report cards |
| Name-open did not cancel (script-era) | 2.3.50 cancel tag **and** name |
| Yardrat starter skills used nonexistent IDs | 2.3.54 → `kimanipulation` / `kicontrol` |
| RaceLock missing saga `difficultyChosen` clear | 2.3.54 port of clearStuckSagaDifficulty |
| ShadowDummy CD key rename dropped mid-CD | 2.3.54 dual-read legacy + lm keys |
| Global TP Boost end spam | 2.3.54 5s end lock (script END_LOCK_MS) |

## Intentional differences vs old scripts

- End portals stay blocked (title feedback) — teleport in, then `/lm admin enddragon`.
- `/lm skillcheck` slash needs `legacymechanics.skillcheck`; NPC tag/name does not.
- Global TP Boost persists to `global-tp-boost.json` (survives restart; 2.3.60).
- PlayerStatChecker has a 1.5s cooldown and cancels the interact.
- Rival spectate TTL 10 min via `/lm rival spectate` (script was 2 min `/spectaterival`).
- Conversation-era UX: player Prestige + Remove Android on hub; mentor invite 2 min;
  Spar last-3 individual report cards; GUI hub hops via `/lmdo lm open hub`.
