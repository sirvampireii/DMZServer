# LM central hub and spar board (4.7.64)

The main menu (`/lm`) ties the LM systems together. It links only to LM's own menus: no button runs another
mod's command or opens another mod's screen. Nothing on these pages pays anything; rewards are 4.7.65.

## Hub tabs

| Tab | What it shows |
|---|---|
| Home (`main`) | Dashboard, then the systems grouped by what they are for |
| Goals (`goals`) | Placeholder: "Weekly goals arrive in the next update." and a dim "No goals yet" button |
| More (`more`) | Meditation progress (LM's own layer), Menu skins, Staff Admin (staff only) |

### Home

Body lines, in order (each header's lines get their own box on the LM client; KaiPath shows them flat):

- **Your next prestige**: `Level 42,310 of 50,000 [######------]`, then `7,690 levels to your next prestige`
  (or `You can prestige now.`). From the same values as Prestige Home (the held-count ladder). Levels, not TP:
  TP per level is DragonMineZ's and TP gain rates vary too much to promise a number.
- **Fastest now**: the one existing thing that moves the player toward the next prestige quickest, in order:
  1. prestige, when ready;
  2. the next difficulty tier the player meets, with its TP-per-kill change from the tier table (TP per kill
     follows enemy health, a share of the player's stats: T1 0.21, T2 0.42, T3 0.65, T4 0.90, T5 1.35, T6 1.60,
     T7 2.00 by default), e.g. `Fastest now: T4 Veteran, +38% TP per kill.`;
  3. players on the spar board;
  4. a close-power spar (the closeness bonus tops out at x3).
- **Train**: `Difficulty: T3 Hardened, Momentum 1:40 banked`; `Your spar streak: 4 days` only when the player
  has a streak (the streak is saved on the player's mentor-bond record, which every player has).
- **Compete**: `Rival: Adversary, 420 RP, #37` (or how to start); `Dojo: 2nd of 5 this season` (or how to join).
- **Progress**: `Potential Unlock 4 of 30: 340 / 500 points`.

Buttons: Prestige (full width), Difficulty / Sparring & Dojo, Rival / Dojo wars (opens Sparring > Wars),
Character Services / Skill Check (dim for players without the perk, as before). Saga reset stays a Character tab.

### Lines from other systems on each Home

| Home | Lines |
|---|---|
| Difficulty (after the guide) | "Beyond Difficulty": levels to your next prestige, your rival standing |
| Prestige | Fastest now |
| Sparring | What a dojo spar win is worth (+12 season RP, x2 in a war), Potential Unlock, streak |

## Spar board (Sparring Home)

| Control | What it does |
|---|---|
| List me for 15 min / Take me off the board | Puts you on the board others see (session memory, per account) |
| Looking for: TP / Potential Unlock / Anything | Cycles what you want from a spar |
| Ask <name> | Sends them a chat line with a click that opens the spar board; 30 s between asks to the same player |
| Say yes to <name> | Tells the asker; then meet up and trade hits to start |

- Who's looking: up to 4 online players, closest power match first, each with
  `about N TP per hit (x2.5 match)`: the base hit TP times the same multiplier product a real hit uses at the
  start of a spar (no session, combo or style bonus yet), times the global spar rate.
- A spar starts the usual way (both players hit each other). Starting one takes both off the board; logging out
  clears your listing, your want and asks to or from you.
- Actions: `do:spar_list`, `do:spar_unlist`, `do:spar_want`, `do:spar_ask:<uuid>`, `do:spar_yes:<uuid>`.

## Native look

A page with two or more header lines draws each header on the panel and the lines under it in their own inset box
(`NativeMenuScreen.lineGroups`). No wire change; a page with one header keeps the single body box.

## Checks

- Boot self-tests: `HubDashboard.selfTest` (levels to go, bar, Fastest now order and text, rival line),
  `SparBoard.selfTest` (want cycle, clock, expiry).
- Ship audit section `lm 4.7.64`; rig scripted checks (hub, tabs, links, Homes, streak, spar board end to end) and
  real-client clicks (Goals tab, Dojo wars tile, List me) in `tools/lm-ui-probe`.
