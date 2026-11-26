# LegacyMechanics (2.4.x)

> **LM 4.8.2:** the Legacy Mechanics client is now required on players' machines (`requireLmClient` defaults to
> true; players without `LegacyMechanics-<version>.jar` are turned away with an install message). Every LM menu
> is drawn by the LM client on `legacymechanics:native_ui`; the KaiPath bridge (`legacymechanics:gui`) is gone.
> Statements below that the mod is server-only or that KaiPath draws the menus are historical.

**Server-side only** Forge mod for Mohist/Forge 1.20.1.  
Clients do **not** need this jar to join.

**CNPC scripts are not required.** This mod replaces the old CustomNPC pack —
see `CNPC-FREE.md` (tags + Forge commands). Repo script folders are backups only.

Java package stays `com.dbzlegacy.adaptivedifficulty` (GUI reflection ABI). Player NBT root `dmz_adaptive_difficulty` and PlaceholderAPI `%dmzdiff_*%` are unchanged.

**Audits** (run after build / before ship):

```bash
python3 tools/dmz-adaptive-difficulty/sim/audit_gui_abi.py   # Forge↔GUI reflection + version handshake
python3 tools/dmz-adaptive-difficulty/sim/audit_features.py  # intended product features vs source
python3 tools/dmz-adaptive-difficulty/sim/audit_concept.py   # stated balance concept (soft-cap / landing / skills)
python3 tools/dmz-adaptive-difficulty/sim/validate_scaling.py # PWR/ENE + class/top-2 combat sim
python3 tools/dmz-adaptive-difficulty/sim/simulate_build_matrix.py --check  # race × class × archetype
python3 tools/dmz-adaptive-difficulty/sim/simulate_race_forms.py --check  # full race/form pack
python3 tools/dmz-adaptive-difficulty/sim/validate_tier_costs.py  # buy-cost ladder at unlock / 100k
python3 tools/dmz-adaptive-difficulty/sim/audit_tier_level_matrix.py  # T1–T7 × levels 1–150k + all races/forms
```

Latest consolidated run: `sim/out/full-mod-audit.md`.

Ship **2.4.x** only: bump the **same** patch on Forge and GUI together (`2.4.1`, `2.4.2`, …).  
Do not return to `2.3.x` patch numbers — they sort above `2.4.0` in file lists and confuse deploys.  
Keep Forge `AdaptiveDifficultyMod.VERSION` = GUI `plugin.yml` version (both jars identical).  
Do not rename the package, `DifficultyCache` / `DifficultyActions` / snapshot fields, or Bukkit `openMenu*` / `openChestMenu*` entrypoints.

**Standing rule — no raw section-sign (`§`) in player-facing Java.**  
All colors go through `UltraStyle` constants/helpers (`CONFIRM`, `DANGER`, `DIM`, `confirm()`, …).  
`UltraStyle.java` is the only class allowed to own the mark (`\u00A7`).  
`DifficultyChatMenu` / `DifficultyActions` / `gui/ultra/**` are fail-closed via
`sim/audit_no_section_sign.py` (also run from `build.sh`). Do not reintroduce bare `§`
when touching chat or action messages.

## Model

| System | Behavior |
|--------|----------|
| Unlock tiers (1–7) | Live gate: current DMZ level **or** Prestige ≥ tier id. Prestige-up / level reset revokes tiers you no longer qualify for (coins alone cannot keep them). |
| Buy tier | Spend Ancient Coins (pay-up OK with lower/higher mix; overpay returned as change) |
| Active difficulty | Set only by tier purchase — no +difficulty upgrades |
| Personal toggle | GUI on/off for that player only (default OFF). Every server restart forces personal OFF on first login that boot; mid-session reconnect keeps the toggle. OFF freezes scaling, kill coins, AI pressure, and tier buy/lower until turned back on |
| Coin drop chat | GUI mute for "Dropped X Ancient Coins" (default OFF) |
| Death | Clears active tier + level whenever the system allows the player (personal OFF cannot skip). Unlocks / prestige / coins kept. Disconnect / logout keeps the purchased tier |
| Nearby scaling | Hostiles scale to soft-blended **STR/SKP/PWR** (+ mild **ENE** pool) × tier % for damage and soft **VIT** for HP (max 5/player). **VIT/RES damage floors** press tank dumps; **Ki Infusion / Potential Unlock** raise pack sponge; **class counters** + **top-2 combat stats** (STR/SKP/RES/VIT/PWR/ENE) ramp with tier%. Mob damage is VIT-capped (raised budgets) so higher tiers pressure **ki protection** without one-punch bag dumps; stronger durability sponge keeps STR/PWR dumps from vaporizing packs. Form soft-curve on STR/SKP/PWR/ENE. Leave range / personal off / logout reverts stats **and** strips elite/mutation/boss nameplates. |
| Combat Rating | Rewards / display / area readouts (not mob HP) |
| Teams | Mutual rivals extend tier ceiling (opt-in, capped) |
| Titles | Restored; equip from GUI |
| Kill rewards | Personal ON: **5%** chance per eligible kill — pre-T1 Copper; with tier, normal ladder |
| Upgrade drop | **0.5%** dual drop: original coin + next-higher (e.g. Copper + Iron) |
| Feature gates | T1+ AI/evo kits by unlock band · T4 elite · T5 mutation · T6 boss · T7 Zenith ceiling |

## Install

1. `mods/LegacyMechanics-2.3.63.jar` (remove older AD jars)
2. `plugins/LegacyMechanicsGUI-2.3.63.jar` (or matching GUI if available)
3. **Do not load CNPC Global Player / NPC scripts** for systems this mod owns (`CNPC-FREE.md`)
4. Tag GUI NPCs (`lm_rival`, `lm_spar`, `lm_skillcheck`, …) or use slash commands
5. Restart — config at `config/legacymechanics.json` (auto-migrates from `dmz_adaptive_difficulty.json`)
6. `/lm difficulty` → hub: Buy / Lower / Titles + personal & coin-chat toggles (Details is ops-only)

## Build notes: dojo structures (4.7.68)

The dojo plots are structure files made with the owner's mod-toolkit (`Desktop/Repository/mod-toolkit`, plugged in by
`tools/toolkit.json`), not by LM code. DojoKit's export ships in the jar:

- `data/legacymechanics/structures/dojo/t<tier>_<style>/`: `base.nbt` (the empty hall of that tier and style) and a
  piece per ring (`arena_1`..`arena_6`), room level, balcony level and garden level. Styles: none, ki, melee, strike,
  evasion. 15 bases and 360 pieces.
- `data/legacymechanics/dojo/index.json`: the plot index (size, floor, ring centres, landing points, balcony, the box
  each part occupies, where each piece goes). `DojoLayout` reads it; `DojoPlacer` places from it.
- `assets/legacymechanics/textures/gui/dojo/`: the menu pictures (floor plans and close-ups, 256 colours).

- Sources: the BuildKit hall recipes `tools/dojo/hall_t1.json`..`hall_t3.json` (japanese style; the `"dojo"` section
  holds the ring anchors, rooms, balcony, doors and landing points) and `tools/fx/DojoKit.java` (interior, grounds,
  export).
- Rebuild: `bash tools/dojo/make.sh` (BuildKit, DojoKit pictures and export, BuildLint; pictures go to
  `docs/dojo-visuals`).
- A structure file must never ship from a recipe or DojoKit change that is not committed.

## Commands

- `/lm difficulty` — GUI
- `/lm admin clear <player> [all|rival|spar|difficulty|progression]` — staff wipe of one player's LM data
- `/lm admin migrate-cnpc` / `force` — import CNPC Rival/Spar into LM stores
- `/lm admin difficulty off|on|toggle|status` — master system switch (ops / staff)
- `/lm admin difficulty reload|settings|set <key> <value>`
- Actions: `activate <1-7>`, `reset`, `character_reset`, title equip, `toggle_personal`, `toggle_coin_chat`

When disabled: no mob scaling, kill coins, AI, death reset, or tier purchases.
Config key: `enabled` (also `admin set enabled false`).

Staff gate: op level ≥2 / Bukkit op, or `difficulty.admin` (config `adminPermission`).
Live `admin set` is allowlisted + clamped; change `adminPermission` only in JSON + reload.

### Testing whitelist
- `/lm admin difficulty whitelist on|off|toggle`
- `/lm admin difficulty whitelist add <player>` / `remove <player>` / `list` / `clear`
- Alias: `wl`
- When on, only listed players use AD (scaling, coins, purchases). Persists in config.

## Adding a new race (future-proof)

AD is **race-agnostic** — it never hardcodes race ids. Drop a new folder under `config/dragonminez/races/<id>/` with `character.json`, `stats.json`, and `forms/*.json` (DMZ loads it; AD reads live STR/SKP/VIT/RES + form⊕stack).

Checklist so new races stay balanced:
1. Set `character.json` → `raceName` to the **same** folder id (form mult lookup uses it).
2. Put combat power in `strMultiplier` / `skpMultiplier` / `pwrMultiplier` / `eneMultiplier` / `defMultiplier` / `vitMultiplier` (and mastery `maxStatsMultiplier` if desired). AD scales against STR/SKP/PWR (+ mild ENE) and counters top-2 of all six stats.
3. Prefer DMZ form multipliers over baking power only into BonusStats/attributes (AD still has baselines + form-key polling as fallback).
4. Re-run the sim after adding forms:  
   `python3 tools/dmz-adaptive-difficulty/sim/simulate_race_forms.py`  
   CI-style: `python3 tools/dmz-adaptive-difficulty/sim/simulate_race_forms.py --check`
5. Optional clamps (already safe by default): `maxFormBoost` (100), `maxLiveCombatChannel` (5e7).

Runtime guards for unknown races: NaN/absurd form&stat clamps, race-tagged baselines (cleared on race swap), active form-key polling when FormChangeEvent is missing, fail-soft profile build.

## Notes

- The End scales hostiles like other dimensions. The **Ender Dragon** is hard-exempt (End Strength script). To disable the whole End again, add `minecraft:the_end` to `disabledDimensions`.
- Ancient Coins are real Lightman's `coin_ancient` items. Tier buys spend from **inventory first, then the equipped wallet** — never from Lightman's bank. Pay-up allowed; overpay is returned as Ancient Coin change.
- Coin ladder values: Copper 1 → Iron 10 → Gold 100 → Emerald 1k → Diamond 10k → Netherite 100k (9 letter variants equal). Lapis / Ender Pearl unused.
- Example: a 1× Iron cost can be paid with 10+ Copper, or 1× Gold (overpay returned as change).
- Tier costs cap at 128 of one coin type, then promote to the next denomination (rounded up; top = 128× Netherite).
- Stock tier costs (copper-value bases): T1 **1× Copper** · T2 5 · T3 15 · T4 50 · T5 150 · T6 500 · T7 1500. Level scaling is exponential in **absolute** DMZ level from 1 → `tierCostLevelAnchor` (**150000**): T1 at level 1 stays **1× Copper**, T7 at level 150k is **100× Netherite** (`tierCostT7TargetCopper` = 10_000_000). Progress clamps at the anchor. Higher tiers are always ≥ ~25% more than the previous tier’s cost for the same player. Buy prices / unlock gates use a **base-form** DMZ level — transforming must not change the cost.
- Saga/quest entities are exempt by class (`DBSagasEntity`) and by tags (`dmz_quest_*` / `dmz_saga_id`). Vanilla cage spawners, SDD Advanced Spawner mobs (`sdd_spawner` / `sdd_boss`), the Ender Dragon, and **slime/magma cube split children** are also never AD-converted. Transform forms keep quest HP — AD never rolls them back to the entity-default 300 max health.
- **God forms:** hit-cap blends soft↔live HP and pulls live-offense pressure so transforms cannot out-tank packs after DMZ DEF. T4+ also pierces DMZ’s hard cancel (`flatMit ≥ dmg×2.5`) using live `getFlatMitigation()` (DEF form), with a LivingDamageEvent safety net that restores tier-scaled landing damage if a hit is still zeroed (SSJB knockback-with-0-damage fix).
- **Potential Unlock:** sparring raises the skill through level 30. Each next level costs `level * 100` points (5,500 to reach 10, 46,500 to reach 30). While transformed, pack HP sponge adds `level / 30 * 12%` (4% at 10, 12% at 30). Mentor TP stays based on character level.
### Bugfix (2.3.159)
- GUI coherence: Skill Check cleanup + Skills frame/center; Prestige hub slot matches Spar

### Bugfix (2.3.158)
- My Dojo Release tip: substitute `{name}` with dojo roster label

### Bugfix (2.3.157)
- Prestige skill floors reapply after prestige/reset (longer window + continuous pulse + race-lock)

### Bugfix (2.3.156)
- Skill Check: human tips (no formulas); Potential Unlock sparring progresses through level 30

### Bugfix (2.3.155)
- Difficulty GUI: merge Buy Tier + Lower Tier into one **Tiers** menu (buy / lower / reset)

### Bugfix (2.3.154)
- Difficulty titles: unlock via DMZ/Prestige (no active-tier regress); rarity presence (land/TP/AD); stronger perks

### Bugfix (2.3.153)
- Unify player chat to Meditation-style §5§l[System] tags across Spar/Rival/Mentor/Prestige/skills

### Bugfix (2.3.152)
- Dojo: mentor + peers roster; My Dojo switch when you also mentor

### Bugfix (2.3.151)
- Mentor Actions: fix Leave `?` and Release `{name}` tip placeholders

### Bugfix (2.3.150)
- Mentor Actions Leave/Release tip: rewrite stale on-disk **7-day** cooldown lore to **12 hours**

### Bugfix (2.3.149)
- Mentor invite TTL 1 hour; dojo peer spar +10% TP

### Bugfix (2.3.148)
- Live telemetry retune: T4 landFrac 0.48; T6 soft/landCap/landFrac lift (fingerprint 41)

### Bugfix (2.3.147)
- Spar TP chat: players see simple +TP (style); staff see full bonus/stack detail

### Bugfix (2.3.146)
- Mentor bonds: Actions-style GUI (Invite · Pending · Dojo · Leave / Release); pending Accept/Decline submenu

### Bugfix (2.3.145)
- Spar: CNPC multiplier stack hardened (prestige/momentum/streak) + TP bonus tags

### Bugfix (2.3.144)
- Mentor dojo: 12h cooldown, multi-apprentice (max 8), diluted mentor share 15%/N

### Bugfix (2.3.143)
- Spar: drip TP only on real combat / live both-clash; ki charge holds match open without fake hits

### Bugfix (2.3.142)
- Rival list: Nemesis → Mutual → Declared → Silent; underdog for any declared rival

### Bugfix (2.3.141)
- Accept at max Mutuals: pick which Mutual to replace

### Bugfix (2.3.140)
- Dual Silent Declared: both get Pending Mutual confirm; both must Accept

### Bugfix (2.3.139)
- Rival Actions: Declare · Pending · Remove · Silent; Pending click opens Accept/Decline submenu

### Bugfix (2.3.138)
- Rival Pending: Accept/Decline on each incoming invite row (no separate Accept…/Decline… menus)

### Bugfix (2.3.135)
- Rival Progress: 4-button hub + Records; single-item detail boards

### Bugfix (2.3.134)
- Rival remove keeps other side Silent after Mutual end; Pending only in invites; Nemesis = 3 challenge KOs; Silent label; cleaner Progress hub

### Bugfix (2.3.133)
- Live telemetry retune: T4 landFrac 0.48 + T6 soft/landCap 0.60 (fingerprint 41)

### Bugfix (2.3.131)
- Android upgrade is race-agnostic (Human/Saiyan/Frost Demon/Viltrumite); fix humans-only copy + telemetry flag

### Bugfix (2.3.130)
- Android / high-DEF T5+: stop DMZ from zeroing AD landing after our safety net (`dmz_raw_damage`)

### Bugfix (2.3.129)
- Live telemetry calibration (22k hits Aug31–Sep1): lift T4/T6 landFrac + T6 painted bite

### Bugfix (2.3.128)
- Form change refreshes CR + clears area cache; Rival form surge uses form mult (scaling still PlayerCombatProfile)

### Bugfix (2.3.127)
- Combat Rating from released combat stats (Android-safe; no raw DMZ BP overflow)

### Bugfix (2.3.126)
- Cap Combat Rating so form BP overflow cannot paint Long.MAX_VALUE

### Bugfix (2.3.125)
- High-CR / form players: recompute DMZ level when getLevel() stuck at placeholder 1

### Bugfix (2.3.124)
- Buy GUI always paints live DMZ getLevel (no stale gate for transformed / late-attach)

### Bugfix (2.3.123)
- Buy GUI level prefers live DMZ sample over polluted highestDmzLevel=1 gate

### Bugfix (2.3.122)
- Pull DMZ level on login/GUI open once Character attaches (no longer waits for death)

### Bugfix (2.3.63)
PvP death + Corpse: GriefPrevention 16.18.x with `AllowCombatItemDrop: false`
cancels Mohist/Paper `PlayerDropItemEvent`s fired on death (no `isDead` guard),
destroying gear before Corpse can store it. `DeathDropGuard` un-cancels drops
only when the player is already dead — combat Q-drop block stays intact.

### Bugfix (2.3.62)
`/lm difficulty` (Bukkit/Mohist) now pulls live DMZ level + unlock data on every open
via `DifficultyActions.prepareGui` / `ForgeBridge.prepareDifficultyGui`. Fixes stuck
Buy GUI samples (level 1 / stale mid-level) that previously needed `resynclevel`.

### Economy (2.3.61)
Tier buy costs retuned to absolute-level anchors:
- **Level 1 / T1** = **1× Copper**
- **Level 150000 / T7** = **100× Netherite**
Shared exponential mult across the ladder; admin knobs
`tierCostLevelAnchor` + `tierCostT7TargetCopper`.

### Economy (2.3.49)
Kill coin drops are chance-gated (was always-on):
- `ancientCoinDropChance` **0.05** (5% any coin)
- `ancientCoinUpgradeChance` **0.005** (0.5% original + next-higher dual)
Existing configs auto-migrate stock 2% upgrade → 0.5%.

### Live balance (2.3.129)
From live `hits-2026-08-31..09-01.jsonl` (22442 hits, post-2.3.57 window):
- Soft-caps unchanged (**T1 0.34 · T2 0.36 · T3 0.44 · T4 0.50 · T5 0.52 · T6 0.58 · T7 0.62**).
- God avgHF was on-target per tier, but **T5→T6 landing was flat** (landFrac both 0.50;
  god +0.014) and **T4→T5 cliffed** (god 0.359→0.501).
- Lift **T4 landFrac 0.37→0.40** / landCap 0.48→0.50; **T6 landFrac 0.50→0.54**.
- T6 form nudge 1.82→1.90; T6/T7 liveShare 0.74→0.80. Formula fingerprint **39**.

### Live balance (2.3.60)
From live `hits-2026-08-29..30.jsonl` (2132 hits pulled from production):
- **T2 gods soft-cap pinned at 43%** — ease early ceilings: **T1 0.34 · T2 0.36 · T3 0.44**
  (T4–T7 unchanged: 0.50 / 0.52 / 0.58 / 0.62).
- Trim T1–T2 threat floors + liveShare; ease T1–T2 landFrac/landCap; lift T5 landFrac → 0.50.
- High wouldCancel at T4/T5 stays expected: soft-cap &lt; mit/thr so pierce cannot clear cancel;
  landing + soft-cap remain the real bag pressure. Formula fingerprint **38**.

### Live balance + fixes (1.0.35)
From `hits-2026-08-06..08.jsonl` (14770 hits) + reported bugs:
- **DMZ level clamp:** `getLevel()` is a stat-progress estimate; AD now clamps to
  `min(DMZ maxValue, referenceMaxLevel)` and repairs inflated `highestDmzLevel` NBT
  so a 100k ceiling never displays as ~987k.
- **Skeleton mounts:** cancel rider↔mount damage; kits refuse mount targets (spider
  jockey / skeleton-horse self-kills).
- **Ladder:** T3 landFrac 0.30 · T4 0.37 (ease cliff) · T5 0.48 · T6 0.50 · T7 0.58;
  T7 nudge/liveShare up so T6≉T7. Soft-caps unchanged. Formula fingerprint **37**.

### Live balance (1.0.34)
T4/T5 landing pressure from Aug 6 hits (superseded landFrac by 1.0.35).

### Soft-cap ladder (1.0.33 → 2.3.60)
T3 soft-cap was **0.55 > T4 0.48**, so god forms got *easier* after buying T4.
Was: **T1 0.40 · T2 0.43 · T3 0.46 · T4 0.50 · T5 0.52 · T6 0.58 · T7 0.62**.
Now (2.3.60): **T1 0.34 · T2 0.36 · T3 0.44 · T4 0.50 · T5 0.52 · T6 0.58 · T7 0.62**.

### Ghast aim (1.0.31)
Large / laser / beam kits no longer use a multi-tick cast that lets Ghast flight
AI overwrite look before `fireHability`. Shots fire instantly and use
`launchToward` (same as small blasts). `ghastTick` also re-faces the player every
tick so vanilla charge + kits stay aimed.

### Economy (1.0.30)
Stock `mobHealthScale` **→ 0.75** (was 1.15, briefly 0.92). Mob damage,
defense, and other attrs are unchanged — only max HP / TP-from-kill pace.

### Economy (1.0.29)
Stock `mobHealthScale` 1.15 → 0.92 (superseded by 1.0.30 → 0.75).

### Live balance (1.0.28)
Retuned from claimed hits in `hits-2026-08-05.jsonl` (8821 hits):
- Raise T1–T2 landing / hit-cap bite (T1 high-form packs were ~11% bag)
- Space soft-caps T5/T6/T7 → **0.52 / 0.58 / 0.62** (T6 was under T5 glue)
- Push T6 form nudge + live-offense share so the buy ladder climbs past T5

### UX (1.0.27)
Buy Tier GUI / chat now shows each locked tier’s real gate:
**DMZ level or Prestige N** (either one qualifies), plus your current DMZ/Prestige.
Example: T7 → `Need DMZ 100000 or Prestige 7`. Prestige is an alternate path, not required.

### Bugfix (1.0.26)
Tier buy costs at DMZ ~100k: stock `tierCostLevelDivisor` 1000 → 50000 so excess
scaling no longer makes T6 ≫ T7 at God unlock (monotonic floor was dragging T7 to
~33× Emerald). Superseded by the 2.3.61 absolute-level anchors (L1/T1 = 1× Copper,
L150k/T7 = 100× Netherite).

### Bugfix (1.0.25)
Post-pierce mob ATK clamped to the live incoming soft-cap so high-DEF god forms
no longer paint hitFrac ≫ 0.75 (race/form audit). Soft-cap event path unchanged.

### Live balance (1.0.24)
Retuned from claimed hits in `hits-2026-08-04.jsonl` + `hits-2026-08-05.jsonl`:
- Fix T4 tank ladder dip (fill-to-landing + T4 hitFrac/nudge)
- Cap T1–T2 god landing (was ~38% bag on spiritualist)
- Progressive soft-caps T4→T7 (0.48→0.58)
- KP10 saves ~15% on landing path (was ~10%)

### Bugfix (1.0.23)
Ghost difficulty from unclaimed / personal-OFF fights:
- Landing safety net requires personal ON (`participates`)
- `isAdPainted` only true for claimed/profile-scaled mobs (not spawn-init shells)

### Behavior (1.0.22)
Personal difficulty starts **OFF** for everyone after each server restart (first login that boot). Mid-session reconnect keeps the toggle. New players default OFF.

### Bugfix (1.0.21)
Enderman/Warden combat gravity no longer sticks after the mob dies.
- Clear contributions on death (`CombatGravity.removeSource`)
- Hysteresis no longer skips unregister when gravity decays to ~0

### Live balance (1.0.20)
Second telemetry pass: high-DEF god forms lived on the **safety-net landing** path.
- Live-bag landing ladder (~12%→48% across T1–T7)
- T3 god-form DEF-cancel pierce (was T4+ only)
- Soft-caps: T5 50% · T6 55% · T7 52% live bag

### Live balance (1.0.19)
Tuned from combat telemetry (`hits-2026-08-04.jsonl`):
- Raise T1–T3 god-form pressure (soft floors + live-share + hitFrac)
- Stretch T4–T6 for tanks (`tankDamageHealthRatio` 0.22→0.28, nudges, live-share)
- Soft-cap crushing T5–T7 landings at 62/58/48% live bag (event clamp)

- **Balance telemetry (Bukkit owns `/lm difficulty` on Mohist):** `/lm admin difficulty telemetry on|off|status|flush|test` — when ON, logs AD hits for **all players** using the difficulty system to `config/legacymechanics/telemetry/hits-YYYY-MM-DD.jsonl` (rate-limited; not whitelist-gated). Use `test` to write a probe line and confirm the folder. Summarize with `sim/summarize_telemetry.py --dir <that folder>`.
- **Skills:** Ki Protection leaves mid/high-tier hits load-bearing (DMZ 1%/lvl mitigation); Ki Infusion / Potential Unlock raise pack HP sponge. Melee kits chase earlier and deal painted shock/slam damage so zombies are not toothless vs skeleton ki.
- Mob damage uses soft-blended STR/SKP/PWR (+ mild ENE) × tier%, then **VIT/RES floors** (`tankDamageHealthRatio` **0.28** / `tankDamageDefenseRatio` 0.45) so VIT dumps and tank class track the same ladder as even builds, then a raised VIT-relative hit cap (ki-protection friendly). Counters (class + top-2 of STR/SKP/RES/VIT/PWR/ENE) ramp with tier%.
- Tier scale vs player fight stats: T1 21% · T2 42% · T3 65% · T4 90% · T5 135% · T6 160% · T7 200%. Form soft curve stock: `transformScaleWeight` 0.65 · `transformScaleExponent` 0.75.
- Side channels (creeper boom, gravity, early kiblasts) also follow tier%/unlock — not absolute offense proxy. Creeper explosions bake from painted attack damage (like kiblasts) so T1–T2 blasts are not cancelled to 0 by DMZ DEF.
- Mob ki blasts/lasers/beams use the mob’s scaled attack damage (not vanilla 3–72), so DMZ DEF mitigation no longer cancels them to 0. Early unlock kiblasts are soft-ratio’d (~60% at T1–T2).
- **Counters:** fighting class + top-2 combat stats; intensity scales with tier%. No race / weak / specialization stacks. Toggles: `enableClassCounters`, `enableStrongStatCounters`.
- Scaled mob HP sponge stock **75%** (`mobHealthScale`, was 115%) with a stronger offense durability floor so glass STR/PWR dumps trade hits. Elite rarity mult stock 1.50×.
- Old NBT wallet balances migrate into Copper Ancient coins once per login.
- AI + Enemy Evolution kits deepen with Buy Tier but stay **silent** (no kit nameplate/glow). Only true rarity rolls get cosmetics: Elite (`✦ Elite …`), Mutation (`§d…`), Boss (`☠ Boss …`). Kit bands: T1 Awakened · T2 Awakened→Enhanced · T3 Enhanced→Elite · T4 Advanced→Master · T5 Master→Divine · T6 Legendary→Mythic · T7 God→Zenith. Stock rarity chances: elite **2.25%**, mutation **3.75%** (bosses are natural T6+ hostiles — no % roll).


See also [MECHANICS.md](MECHANICS.md) for Rival / Sparring / Difficulty hub docs.
