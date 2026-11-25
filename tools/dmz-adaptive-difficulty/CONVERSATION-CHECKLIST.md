# Conversation commitments checklist

Verified against LegacyMechanics **2.3.63**. These are decisions from the
progression / CNPC-free / GUI-audit conversation thread — do not revert.

| Commitment | Where | Status |
|------------|-------|--------|
| End portals stay blocked; reach End via TP then Difficulty GUI summon | `EndPortalGuard`, `CNPC-FREE.md` | locked |
| End staff: `/lm admin enddragon clear|repair` via `StaffAccess` (no spawn); delete CMI enddragon aliases | `ProgressionCommands` | locked |
| End hit-cap after mitigation; egg clear = `dragon_egg` only | `EndDimensionStrength` | locked |
| Skill Check: no soft `contains("rival")`; cancel on tag **or** name | `CnpcGuiOpener`, `SkillCheckService` | locked |
| `/lm skillcheck` perm-gated; NPC tag opens without perm | `StaffAccess`, Skill Check NPC | locked |
| Mentor invite **1 hour** (Pending Accept/Decline) | `SparringSystem.MENTOR_INVITE_MS` | locked |
| Spar Stats: last **3** spars as **individual** report cards | `appendRecentReport` | locked |
| Spar TP Msg toggle GUI-only (no `/lm spar tpmsg`) | Spar GUI / `SparGuiApi` | locked |
| Meditation levels near a Living World circle; no `/lm progression meditation` slash | `LivingWorldNearbyMeditation` | locked |
| Player Prestige + Remove Android on hub (not staff-only) | Hub chest/CMI/chat | locked |
| Player slash whitelist: `/lm` `/lm spar` `/lm difficulty` `/lm rival` `/lm skillcheck` | Bukkit+Forge gates | locked |
| Prestige / android remove / skills via GUI only for players | `/lm prestige` staff slash; `/lm` hub | locked |
| `/lm progression` is staff-only; meditation is not a slash command | `ProgressionCommands`, Bukkit tree | locked |
| Hub hops via `/lmdo lm open hub` (not bare `/lm`) | All system GUIs | locked |
| Inspect sessions preserved; `android_remove` inspectable | `AdaptiveDifficultyGuiPlugin` | locked |
| Chat-backend Remove Android opens inventory confirm | `isAndroidRemovePage` | locked |
| CNPC-free: scripts backups only; tags + Forge commands | `CNPC-FREE.md`, `player_scripts.json` | locked |
| Yardrat starters `kimanipulation` / `kicontrol` | `YardratProgression` | locked |
| RaceLock clears stuck saga `difficultyChosen` | `RaceLock` | locked |

Related reports: `SCRIPT-AUDIT.md`, `GUI-COMMAND-AUDIT.md`, `CNPC-FREE.md`.
