# LM 4.7.84: friendlier navigation, visual appeal, plain words. Proposal (Phase 1)

**Status:** proposal only. No game code has changed. Phase 2 builds only what the owner approves below.

**Branch:** `lm-4784-navigation`, off `main` at `6cf5d6db` (LM 4.7.82). The version bump to 4.7.84 comes with the
Phase 2 code.

## What this is based on

- **Real menus.** A full crawl of 635 page packets (411 distinct pages) from the 4.7.83 release check, with a
  screenshot of each.
  - Source file: `DMZRig/results/release-4.7.83-run1/matrix/full/`.
  - The menus are the same as `main`; 4.7.83 only changes a boot self-test.
- **GuiKit, rerun on those real packets.** A new script, `tools/lm-gui-corpus/gen/from_crawl.py`, checks all 411
  pages at 1920x1080, 1366x768 and 854x480, at GUI 2, 3 and Auto. The results are in
  `tools/lm-gui-corpus/findings/real-crawl-findings.md`.
- **Two new-player walks**, from the main menu down to the deepest page and back. Every claim was checked against
  the code.
  - [walk-hub-difficulty-prestige-character.md](lm4784-work/walk-hub-difficulty-prestige-character.md): hub,
    Difficulty, Prestige, Character Services, Skill Check, staff tools.
  - [walk-rival-sparring-dojo.md](lm4784-work/walk-rival-sparring-dojo.md): Rival, Sparring, Dojo.

  **The walks are part of this proposal.** Each change below names its walk section, which holds the exact
  before/after copy for every rewritten line, with file:line.
- **The dojo audit.** [DOJO_IMPROVEMENT_AUDIT_2026-11-24.md](DOJO_IMPROVEMENT_AUDIT_2026-11-24.md). Every P0–P3
  item maps to a change or an open pick below.

## The short version

The menus work. They feel cluttered for five reasons, the same five in every system:

1. **The main action is not on Home.**
   - Sparring Home shows Weights and TP Chat; posting or accepting a spar is two tabs away.
   - Dojo Home has 9 buttons but no way to the Spar Board.
   - Prestige Home has no buttons at all.
2. **Pages are too tall, so they shrink until the buttons are tiny.**
   - 109 pages drop below 0.75x at 1366x768, GUI 3. Below that size the buttons are under 12 px.
   - 82 pages are too tall even at the 0.5x floor. At small windows the client then scrolls them anyway, despite
     the no-scroll rule. The worst are More, Dojo Home, My colors, the race pages and Body parts.
   - The fix is fewer and shorter rows on those pages, not scrolling.
3. **One thing has several names and several doors.**
   - Dojo Home has "Roster and bonds", "Members and elites" and "Training bonds".
   - A ring is called a ring, an arena and a mat.
   - "TP Chat" exists in both Rival and Sparring, and they set different things.
4. **Back goes to the wrong place.**
   - A system's Home says "Back to <whatever you saw last>": Prestige Home says "Back to Sparring", Rival Home
     says "Back to Difficulty guide". The trail is only cleared at logout.
   - Switching tabs adds steps to the trail.
   - Long names give cut labels like "Back to Wager Ancient".
5. **Some copy says the wrong thing.** Each was checked against the code:
   - wager refunds;
   - the apprentice cap ("up to 8"; it is 5 to 29);
   - the last capacity step ("+5"; it is +4);
   - the bonus lock ("8 days"; it is 7);
   - the rival TP bonus ("+100%"; it is +0%);
   - "elites can invite members" (they can't);
   - "a banner made at a loom" (any plain banner works);
   - "T1 T1 Awakened";
   - "Find a master on Sparring" (masters moved to Dojo).

**Joining a dojo is the hardest thing to do.** The list has no Join button, and doesn't name the master or say if
they're online. Today it takes 7 taps, plus picking the master's name out of every player online.

## Rules every change keeps

- Presentation, navigation and copy only. No behavior, prestige, TP/mastery/scaling or currency changes.
- Dojo Points earn and cost numbers stay the same; they're only shown better.
- Every major system keeps exactly 3 tabs, Home first.
  - A change to *what* a tab holds is flagged **[OWNER]**.
- Things you can't do yet stay visible but dim, with the reason.
- Menus shrink to fit and never scroll.
  - The proposal adds a height budget so pages stay large enough instead (X5).
- Native renderer only for rounded shapes and depth. Per-system accents stay fixed.
- Plain friendly words. Errors say what happened and what to do. Costs are warned about before the click.

Sizes: **S** is small, **M** medium, **L** large. **[OWNER]** means it needs your yes before it's built.

---

## 0. Already approved: two dojo correctness fixes (Phase 2, no pick needed)

| ID | Change | Where |
|---|---|---|
| P2-1 (S) | **Wager settles honestly.** The winner gets both stakes on a forfeit, and on a timeout when one fighter did more damage. Both stakes come back only on a disconnect, a server crash or restart, or a dead-even timeout. | `dojo/BoardSpar.settle` / `settleWager` (dojo audit #3) |
| P2-1c (S) | **Every wager line says the new rule.** The wager page, amounts page, accept page and in-fight page. In-fight, "Forfeiting gives the win away. Any wager is refunded to both." becomes **"Forfeiting hands the win and any wager to your opponent."** All the before/after lines are in walk-rival §5.0. | `UltraDojoScreens` openBoard (in-fight), openWager, openWagerAmounts, openAcceptAsk |
| P2-2 (S) | **Ki sense works in menu wars.** `onlineWarOpponents` also reads running `DojoStore.WarRecord`s. | `sparring/DojoRankings.onlineWarOpponents` (dojo audit #2) |

## 1. Every menu (cross-cutting)

| ID | Change | Effect |
|---|---|---|
| N1 (S) | **A system's Home always exits to the main menu.** It shows one "Main menu" button, bottom right. | No more "Back to Part color" on Difficulty Home. (walk-hub P-N1; walk-rival X3) |
| N2 (S) | **A menu opened fresh (tile, command, key) starts a fresh trail.** History only follows clicks made inside the menu. | Back always means "where I just was". (P-N2) |
| N3 (S) | **Tabs replace each other in the trail instead of stacking.** Back from a tab goes to where you were before the system. | (X1) |
| N4 (S) | **Short back names.** `PageRegistry` gets an optional short title for Back labels, e.g. "Back to Wager" instead of the cut "Back to Wager Ancient". | (X2, P-N3) |
| N5 (S) | **Breadcrumb and Back agree.** When they would disagree, the breadcrumb shows the trail Back follows. | (X3; see pick g4) |
| X5 (M) | **Height budget.** A GuiKit/self-test check that every player page is at most 300 px of native panel height, so it never drops under 0.75x on 854x480 GUI 2 or 1366x768 GUI 3. Each page over budget gets the de-clutter plan below. | Ends the sub-12 px targets on player pages, without scrolling. (walk-rival §5.5 lists each page; walk-hub fit table) |
| X6 (S) | **Dim where you can't act**, with the reason, for anything live today that does nothing. Examples: "Leave mentor" with no mentor; Prestige buys you can't afford, some of which are even green. | (X6, P-P2) |
| X7 (S) | **One word per thing:** "ring" (not arena or mat); "Members"; one "TP messages" setting per system, named for what it sets. | (X7, S5) |
| N6 (S) | **Not enough coins or favor? The confirm page offers the way to get more:** "Earn favor: Trade" / "Where to get coins". | (P-N4) |

## 2. Main menu (hub: Home / Goals / More)

| ID | Change | Before → after copy |
|---|---|---|
| H1 (M) | **Search goes to the top of More; results replace the other sections while shown.** "Clear search" brings them back. | More's fit goes from 0.71 to about 0.94, and it's no longer too tall at 854x480. (P-H1) |
| H2 (S) | **Search finds what players type.** Add keywords so "hair", "boost", "skills", "colour", "favour" and "skill check" hit. Stop "menu" and "main" matching all 91 pages. Report the real count before the cut to 8 ("10 pages, showing 8"). | Keyword list: walk-hub §1.2. |
| H3 (S) | **"Fastest now" gets a button** that opens the system it names. | (P-H3) |
| H4 (S) | **Dashboard copy.** "Find a master on Sparring" → "Find a master in Dojo", plus 4 more lines. | Table walk-hub §1.3 (HubDashboard.java:59, 99, 151, 175, 215). |
| H5 (S) **[OWNER]** | **Goals say what they pay.** Add one line under "Weekly goals", e.g. "No rewards yet: goals track your week." Pick the wording. | (P-H5) |
| H6 (M) | **My colors fits.** Today it is 530 px and too tall even at 0.5x. Pair the five name/box rows into two columns, beside the wheel, and move From/Reset onto one "From a skin" page. | About 300 px. (fit table) |

## 3. Difficulty (Home / Tiers / Summons)

| ID | Change | Before → after copy |
|---|---|---|
| D-1 (S) | **"Tier: T1 T1 Awakened"** → "Tier: T1 Awakened". | UltraDifficultyScreens.java:158, 226 (P-D1) |
| D-2 (S) **[OWNER]** | **Turn difficulty on where you pick a tier.** With difficulty off, every tier page says "Turn on difficulty on the Your tier step first", but that step has no switch, and the button reads "Tier locked" for a tier you have unlocked. Show the switch on Tiers and on the tier page. This reverses the 4.7.78 "toggle only on Home" note. | "Tier locked" → "Turn difficulty on to use this tier". Taps 6 → 4. (P-D2) |
| D-3 (M) | **A lighter tier page.** Today it is 15 lines and 327 px. Group the cost and the effect into one card. | Fit 0.72 → about 1.0. (P-D3) |
| D-4 (S) | **Home copy** and **Summons labels "Name - Status"**, e.g. "End Dragon - Locked". | walk-hub §3.3 (P-D4, P-D5) |
| D-5 (S) | **Guide step 2 puts the toggle first.** | (P-D6) |
| D-6 (S) **[OWNER, optional]** | Keep Summons as a tab, or make it "Rewards" (Titles + Summons). Tab count stays 3. | (walk-hub §3.1) |

## 4. Prestige (Home / Prestige / Shop)

| ID | Change | Notes |
|---|---|---|
| P-1 (M) **[OWNER]** | **Tabs become Home / Shop / Trade, with "Prestige now" on Home.** Today Home has no buttons and the Prestige tab repeats the wallet. Count stays 3. | Saves 1 tap to prestige and 1 to trade. (P-P1) |
| P-2 (S) | **Dim what you can't afford**, with the cost shortfall. | (P-P2) |
| P-3 (S) | **Shop "Difficulty tiers"** → **"Own tiers for good"**, plus the exchange labels and copy. | walk-hub §2.3 (P-P3, P-P4) |
| P-4 (S) **[OWNER content]** | **One-line effect descriptions** for Majin, Mutant and Ultimate. You supply or approve the text. | (P-P5) |
| P-5 (S) | **Fix the two breadcrumbs that fold into "..."** on God Ki pages, with short titles (N4). | GuiKit warnings |

## 5. Character Services (Home / Saga / Android)

| ID | Change | Notes |
|---|---|---|
| C-1 (M) | **Home becomes a short picker.** 12 caption lines become one status line, and the buttons get clearer names: "Customize model" → "Model and colors", "Reskin" → "Change skin". | Fit 0.88 → 1.0. (P-C1) |
| C-2 (L) **[OWNER]** | **Tabs become Home / Looks / Story**, with Android folded into Home. Count stays 3. | (P-C2) |
| C-3 (M) | **Change race fits one page.** Today it is 495 px and too tall at small windows. Use three 131 px columns for all 24 races. The three lines about the same cost become one. Drop "Listed cost", which is listed nowhere. | (P-C3) |
| C-4 (M) | **Reskin is one page.** 4 taps become 3, and the three cost lines that contradict each other go. | (P-C4) |
| C-5 (M) | **Race confirm is readable.** 21 lines become about 10. This covers all 72 race_confirm pages, which are too tall at 854x480 today. | (P-C5) |
| C-6 (S) | **Class options in normal style, not green**, and **one button per part color**, not two to the same page. | (P-C6, P-C7) |
| C-7 (M) | **Body parts tighter.** Today it is 447 px on 10 pages. | (P-C8) |
| C-8 (S) | **Sagas you can reset are listed first**, and **Remove Android gets a confirm page** instead of "Click again within 10 seconds". | (P-C9, P-C10) |
| C-9 (S) | **Copy table** walk-hub §4.3, including the raw `§` lines in CharacterServicesSystem that these rewrites touch. They move to UltraStyle. | (P-C11) |
| C-10 (S) **[OWNER]** | **Where the Healing line lives:** Character Home, Skill Check, or dropped. | |

## 6. Skill Check / Skills

| ID | Change | Notes |
|---|---|---|
| S-1 (S) | **Spell out skill names** ("Heal Red", "IT"), and give the page one action: "Buy skill levels" → the Prestige skill shop. | (P-S1) |

## 7. Rival

| ID | Change | Notes |
|---|---|---|
| R-1 (S) | **Home says what Rival is**, in one plain sentence. | walk-rival §5.2 R1 |
| R-2 (M) | **Merge the stats pages and slim Records.** Records > Stats is 393 px today and 0.60x at 1366@3. | R2 |
| R-3 (S) | **Fix the copy:** "TP bonus +100%" → the real bonus; plus the request and picker copy. | R3, R5; walk-rival §2.3 |
| R-4 (S) **[OWNER]** | **Rename the "Rival" tab to "My rivals."** | R4 |

## 8. Sparring (Home / Leaderboard / Spar Board)

| ID | Change | Notes |
|---|---|---|
| SP-1 (S) **[OWNER: pick g1]** | **Sparring Home leads to the board.** It shows "N players are looking for a spar" and an "Open the Spar Board" button; Weights and TP messages move below. The alternative (S1-alt) makes Home the board. | S1 / S1-alt |
| SP-2 (M) | **Board: 4 posts a page with a pager. Posts aimed at you come first. "Post a spar" opens its own page** (kind, wager, range, looking for), so the board is the listings. | 352 px → about 280; no post is hidden (dojo audit #6). S2 |
| SP-3 (S) | **The ranked lock reads like the other toggles**, dim with "Ranked unlocks after 30 friendly spars". | S3 |
| SP-4 (S) | **Copy fixes** on the board, challenge, watch and result pages. After a forfeit, drop the wrong "1 minute" reason. | S4; walk-rival §3.3 |
| SP-5 (S) | **The settings page says what each setting does** ("TP messages in chat - ON: a line each time you earn TP in a spar"). | S5 |
| SP-6 (S) | **The Leaderboard tab shows the board itself**, not a menu of boards. | S6 |

## 9. Dojo (Home / Upgrades / Wars)

| ID | Change | Notes |
|---|---|---|
| DJ-1 (M) | **Dojo Home de-clutter:**<br>- 9 actions become 5: Spar Board, Members, Upgrades, Wars, Close or leave.<br>- 460 px becomes about 280.<br>- The floor plan sits beside the stats, not above 6 blank rows.<br>- A "War starting!" notice shows when there is one. | D1 |
| DJ-2 (M) | **One "Members" page** replaces "Roster and bonds" and "Members and elites". It shows 6 a page with a pager, and elites and disciples are marked. Training bonds stays one tap from it. | D2 |
| DJ-3 (M) | **"Ask to join" from the dojo list** (dojo audit #5).<br>- A list row opens a short dojo profile: the master (online or offline), members, style and clout.<br>- The profile has "Ask to join", which reuses the existing `apprentice_invite` to the master.<br>- When the master is offline, the button is dim with the reason (unless you pick g2).<br>- 7 taps plus guessing become 5. | D3 |
| DJ-4 (M) | **The Upgrades tab and upgrade pages fit.** The text column is no longer hand-wrapped to 25 characters, and LM clients get the picture *or* the text map, not both. | 392 → about 290 px. D4 |
| DJ-5 (S) | **Style and bonus are separate pages, and the style confirm states the total:** "Switching ends your bonus. Total to switch and get a bonus back: 80 Dojo Points." (dojo audit #11) | D5 |
| DJ-6 (S) | **War sign-up on the menu** (dojo audit #7):<br>- A "War starting!" card on Wars with the countdown, the signed-up count per dojo, a 1v1 warning, and Join / Refresh.<br>- A notice on Dojo Home.<br>- The chat line says "Open Dojo > Wars to join".<br>- The length (60 s) is pick e. | D6 |
| DJ-7 (S) | **War pages, Training bonds and pickers:**<br>- "Request master" lists masters only, not every player online.<br>- "Leave mentor" is dim when you have none.<br>- The cap shows the real dojo capacity (5–29), not "8". | D7, D8 |
| DJ-8 (S) | **Founding copy:**<br>- "a banner you made at a loom" → "any banner you crafted (plain is fine)".<br>- Step 1's "Next" reads "Next: pick a style". | D9 (keeps the requirement; see pick d) |
| DJ-9 (S) | **Show how points are earned**, with today's numbers, on Upgrades: hosting 2 a fight (20 a day), training 3, milestones 5, war bouts 5, a war win 25. | D10 (dojo audit #10, display only) |
| DJ-10 (S) | **Honest room copy.** The trophy hall, meditation room and quarters are described as what they are today (decor). | D11 (dojo audit #17) |
| DJ-11 (S) | **Copy fixes:**<br>- Elites "can buy upgrades and sign off wars", not "invite members" (pick f).<br>- The capacity step says "+4" at the top.<br>- The bonus lock says 7 days.<br>- "1 members" → "1 member".<br>- "1 sparring mat" → "1 ring". | D12, D15 |
| DJ-12 (S) | **Remove dead routes:** the one-tap `found(p)` and the `pick_dojo_challenge` page. | D13 (dojo audit #15) |
| DJ-13 (S) | **First-login nudge** for a master whose dojo has no style or banner: "Your dojo needs a style and a banner - open Dojo". | D14 (dojo audit #4; also eases the 40 migrated dojos) |

---

## 10. Open picks for the owner (not decided; please answer each)

**(a) The old `/spar dojo challenge` war path** (dojo audit #1).
- **Retire it.** One meaning of "war". The command goes, and so does the old 24-hour 2x-RP window. Live has 0 old
  challenges, so nothing needs migrating.
- **Rewire it to `DojoWar.apply`.** The commands keep working and declare the same wars as the menu. The 2x-RP
  window still ends.
- **Keep the 2x-RP idea as a war perk.** Spars between two dojos at war earn 2x ranking points while the war
  runs, or for 24 hours after it. This is new behavior, and the number needs your yes.

**(b) A personal perk for disciples** (dojo audit #8).
- **Nothing**, for now.
- **A contribution line** ("You've earned your dojo 34 points this season"). Display only, but it needs a
  per-player counter.
- **A dojo title** shown with your name ("Disciple of X's Dojo").
- **A share of host points as personal TP.** This is new economy, outside the no-retuning rule.

**(c) Help a new dojo start** (dojo audit #10).
- **Nothing** (DJ-9 shows how points are earned).
- **A founding grant of 10–20 Dojo Points**, for a first founding only (reopening exists).
- **A first-room discount**, e.g. the first upgrade at half cost.
- **Uncapped host points for a new dojo's first N fights.**

Each of the last three changes a Dojo Points number.

**(d) The banner needed to found a dojo** (your 4.7.78 decision).
- **Keep it, with honest copy** (DJ-8): any crafted banner works, plain is fine.
- **Make the banner optional at founding.** The dojo starts with a banner in its style colors, and "Choose your
  dojo's banner (free)" stays on Home until it's picked.

**Also raised by the walks** (each needs a yes or no):
- **(e) War sign-up length:** keep 60 s and rely on DJ-6; or 3–5 minutes; or "until both dojos have 2+ fighters
  or 5 minutes". Separately: warn on a 1v1 war (DJ-6 already warns), or block it.
- **(f) Elites "invite members":** reword it (DJ-11, the default), or wire it so an elite's invite is sent on the
  master's behalf (new behavior).
- **(g) Navigation calls:**
  1. Sparring Home: board summary and button (SP-1), or Home *is* the board (S1-alt).
  2. Ask to join an offline master: deliver it at their next login, and allow more than one pending ask per
     master. Both are new behavior.
  3. Rival tab → "My rivals" (R-4).
  4. Back model: registry parent on sub-pages, or history everywhere (N5).
- **(h) Points wagers for masters and elites only** (dojo audit #12): keep it (the treasury guard), or not.
- **(i) Dojo names with non-ASCII letters, e.g. Cyrillic** (dojo audit #16): allow them, or not.
- **(j) The trophy hall:** keep it as decor (DJ-10), or list the dojo's past wars there (new behavior).
- **(k) Forfeits and ranking:** after P2-1 a forfeit pays the wager but gives no ranked credit. Keep that
  (anti-farm), or count it once the 1-minute damage gate is met.
- **(l) Tab contents** (the count stays 3 everywhere):
  - P-1: Prestige becomes Home / Shop / Trade.
  - C-2: Character becomes Home / Looks / Story.
  - D-6: optionally, Difficulty's Summons becomes Rewards.
- **(m) Copy you supply:**
  - H5: what Goals say about rewards.
  - P-4: effect lines for Majin, Mutant and Ultimate.
  - C-10: where the Healing line lives.
- **(n) D-2:** show the difficulty switch on Tiers and on the tier page (reverses the 4.7.78 note).

## 11. Not in this proposal (left as they are)

- **Scrolling:** menus keep shrinking to fit (your 4.7.81 call). X5 makes the shrink rarely matter on player
  pages.
- **No changes here:**
  - the dojo audit's robustness items #13 (stuck hall build) and #14 (pair cooldowns not saved);
  - the 40-dojo first-boot build storm (#4, a deploy note; DJ-13 covers the nudge).

  These are behavior, not presentation. They can be a separate small build if you want them.
- **The config editor** gets only light staff copy fixes (walk-hub §6, P-A1/P-A2). It's staff-only and fits at
  1.0x or more everywhere.

## 12. What Phase 2 will prove

- The probe's navigation audit and new checks: taps to the common actions, a Back that goes where it says, and
  the Ask to join flow.
- GuiKit on the real crawl: no ERRORs, and every player page within the 300 px budget (X5).
- The boot self-test.
- A manifest row `lm-4784-navigation` with its `§ lm 4.7.84` checks.
- Build notes. The in-game checks stay owed after the build: GUI scale 3 and 4, Full and Lite, and the bridge
  fallback.
