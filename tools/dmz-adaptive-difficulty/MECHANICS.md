# Legacy Mechanics 2.3.63

Forge mod packaging **Difficulty**, **Rival System 4.7.10**, **Sparring TP 3.2.11**,
and **natural progression / Fabled bridges** (from CNPC scripts) under one product.
Java package stays `com.dbzlegacy.adaptivedifficulty` and NBT root
`dmz_adaptive_difficulty` for GUI ABI.

## Install (test)

1. Build / install `LegacyMechanics-2.3.63.jar` (+ matching `LegacyMechanicsGUI-2.3.63.jar`).
2. Disable CNPC Global Player scripts to avoid double systems — see **[PROGRESSION.md](PROGRESSION.md)**
   for the full disable list (Fabled bridges + natural ports + rival/spar).
3. Do **not** deploy to live until tested.

## Data

| System | Path |
|--------|------|
| Config | `config/legacymechanics.json` (migrates from `adaptivedifficulty.json` / `dmz_adaptive_difficulty.json`) |
| Rivalry | `config/legacymechanics/rivalry-v4.json` |
| Progression | `config/legacymechanics/progression-v4.json` |
| Sparring | `config/legacymechanics/sparring.json` |
| Telemetry | `config/legacymechanics/telemetry/` |

Flags: `enableRivalSystem`, `enableSparringSystem`, `rivalPresenceTp`, `rivalInstinct`,
`rivalChallenges`, `enableProgression`, `enableFabledBridge`, `enableSystemTelemetry`,
`balanceTelemetryEnabled` (+ per-module progression/Fabled toggles).

## Player GUIs (CMI → chest → chat)

**Players should use `/lm` only.** Hub buttons call `lm do open <system>` (plugin opens
inventories directly). Bare `/difficulty`, `/rival`, `/spar`, `/skillcheck` still open GUIs
for compatibility, but help text advertises `/lm`.

Removed duplicate aliases (2.3.20+): `/lmgui`, `/dmzdiffgui`, `/adiffgui`,
`/legacymechanicsgui`, `/adifficulty`, `/dmzdifficulty`, `/adaptivedifficulty`,
`/legacymechanicsdiff`, `/sparr`, `/rivalry`, `/rivals`.

Inventory titles use short system names (`Sparring`, `Rival`, …). Only the
main `/lm` hub window is titled **Legacy Mechanics**.

| Command | Who | GUI |
|---------|-----|-----|
| `/lm` (`/legacymechanics`) | All | Hub — Difficulty · Rival · Spar (+ Skill Check / Prestige for donators/staff; staff also Progression · Admin · Logs) |
| `/difficulty` | All | Unlock tiers · Titles · personal toggle (compat; prefer `/lm`) |
| `/rival` | All | List · Challenge · Top · Progress submenu |
| `/spar` (`/sparring`) | All | Stats · Top · Mentor (pickers on Mentor page only) |
| `/skillcheck` | Donators | Skill Check (core / saga) — LuckPerms `legacymechanics.donator` |
| `/progression` (`/prog`) | Staff | Category hub · Flags |
| `/prestige` | Staff | Prestige confirm |
| `/skills` | Staff | Skill unlock admin browser (per-skill items) |

## Admin commands (keep typed)

- `/lm admin help|reload|clear <player> [scope]|migrate-cnpc|syslog|open <system>` — hub staff tree
- `/difficulty admin …` — whitelist, syslog, inspect GUI, reload, settings
- `/rival admin save|refresh|reload|status|open [page]` — rivalry-v4 + progression-v4
- `/spar admin save|status|mentor resetcd [player]` — sparring.json
- `/progression admin …` / `/prog admin <flag> on|off` — module flags
- `/progression boost start|end`, `android` (staff)
- `/enddragon clear|repair`, `/cleardragons` (staff spawn disabled; players use Difficulty GUI)

Data is **mod JSON only** (`config/legacymechanics/rivalry-v4.json`, `progression-v4.json`,
`sparring.json`) — no CNPC writes.

### Rival features

Presence TP, surpass, challenges + battle reports, instinct, Proving Grounds,
kill-near / underdog / anti-gank (offense aura stays off), level TP curve,
seasons / quests / achievements / HOF / journal / titles, spectator, fusion kill share.

### Sparring features

Auto sessions, mentor, block TP, clash drip, Friendly Fist, release-control drip,
perfect training banner, ki type classification, charging holds activity, `/spar top`.

### Progression / Fabled / Skill Check

See **[PROGRESSION.md](PROGRESSION.md)** for module map (grouped by script family),
config toggles, CNPC Skill Check setup, and scripts to disable on test. Full audit:
**[AUDIT.md](AUDIT.md)**.

`/progression` is a **staff category hub**: Skills · TP Gains · Race · Combat · End · Shop ·
Fabled · Utility (plus Prestige/Skills shortcuts). Also includes End portal
guard, and Title progression / Admin inspect (from the server-fixes line).

Donators: grant `legacymechanics.donator` (LuckPerms) — hub hides Skill Check without it.
CNPC Skill Check: tag `lm_skillcheck` or name “Skill Check” (no NPC script).
Slash `/skillcheck` needs `legacymechanics.donator`.

NPC command `skillcheck`. Staff Skills: NPC command `skills` or `lm open skills`.

## Telemetry

- Balance hits (whitelist): `hits-YYYY-MM-DD.jsonl`
- System events (all players, rate-limited): `systems-YYYY-MM-DD.jsonl`
  (includes `system=fabled` bridge sync events)
