# LM 4.7.84 Phase 1: new-player walk of the hub, Difficulty, Prestige, Character Services, Skill Check and staff tools

Audit and proposal only. This document changes no code and commits nothing.

## Sources

- Crawl: `C:\Users\antho\DMZRig\results\release-4.7.83-run1\matrix\full\uiprobe-packets.jsonl`, 635 packets. In this
  document `i=N` is the packet's `i` field, and the matching screenshot is `ui-NNN-1080p-gs3.png`.
- Code: worktree `DMZServer-4784` at `6cf5d6db`. All paths are under
  `tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/`.
- Rules: `CLAUDE.md`, `docs/CNPC_GUI_MENU_RULES.md` (six action buttons a page, `Name - Status`, dim locked buttons,
  ask pages act on the first Confirm), `CONVERSATION-CHECKLIST.md`, and `lm-gui-corpus/findings/layout-findings.md`.
- Search was checked by replaying `PageRegistry.search` exactly (title + path + keywords, substring, at most 8
  results, staff pages hidden) over the registry for a non-staff player.

**Two cautions about the crawl.**

1. The crawl ran as staff. That is why it shows `Cost Free for staff`, the Staff Admin and Skills tiles, Server tools,
   Combat rating and Preview forms. Every finding below was checked against what a non-staff player gets.
2. Many `Back to X` labels in the crawl are side effects of the crawl order: the rig jumps straight between screens.
   Two examples are `Back to Android` on Change race (i=32) and `Back to main menu` on every confirm page (i=97, 118,
   180-208). I only report a Back problem when the code shows that a real player hits it too (see N1).

## Fit scale reference

At 1080p with GUI scale 3, the menu has about 340 px of height. Pages taller than that shrink to fit:

| Page | Crawl | Panel height | Buttons | Lines | Approx. fit scale |
|---|---|---|---|---|---|
| My colors | i=522 | 530 | 17 | 8 | 0.64 |
| Change race | i=32 | 495 | 23 | 11 | 0.69 |
| More with 8 search results | i=461 | 477 | 16 | 9 | 0.71 |
| Confirm race change | i=97 | 471 | 7 | 21 | 0.72 |
| Body parts | i=35 | 447 | 21 | 9 | 0.76 |
| Part color swatches | i=538 | 428 | 14 | 6 | 0.79 |
| Character Home | i=5 | 386 | 11 | 14 | 0.88 |
| Difficulty Home during a trial | i=552 | 351 | 10 | 13 | 0.97 |

---

## Cross-cutting navigation findings (every system)

**N1. A system Home's only exit is a "Back to (some old page)" button, not the main menu.** This is a real bug, not
a crawl artifact.

- `UltraScreenSpec.placeBack` (UltraScreenSpec.java:668-675) labels a Home page's single footer from `NavHistory`
  whenever the client has native nav.
- `NavHistory` is cleared only on logout (DifficultyEvents.java:464), so the history survives closing the menu.
- Real scenarios:
  - Goals tab, then "Go to Tiers", then the Home tab: Difficulty Home's only footer reads `Back to Tiers`, with no
    Main menu.
  - Close the menu on Part color, later type `/difficulty`: Difficulty Home says `Back to Part color` (i=552) and
    sends the player into Character Services.
  - Prestige Home shows `Back to Sparring` (i=4); Character Home shows `Back to Prestige` (i=5); Skills shows
    `Back to Staff Admin` (i=8).
- `docs/CNPC_GUI_MENU_RULES.md` says "a system Home shows one Back to main menu". The code no longer honours that.

**N2. Breadcrumbs and Back already say the same thing.** Every sub-page has a breadcrumb line (for example
`Main menu > Character Services > Change race`, screenshot i=32) and also a Back/Main menu footer pair. The pair costs
one button row on every sub-page; the crumbs are already tappable. This is not a change for Phase 1, just a note:
keep the footer, because KaiPath has no crumbs.

**N3. Back labels get cut to nonsense.** `UltraStyle.backTo` (UltraStyle.java:280-283) drops trailing words until the
label fits, which gives `Back to Convert to` (i=96, from "Convert to Android"). It should fall back to a short
registry title or a plain `Back`.

**N4. Confirm pages that can't run show a lone Back and nothing else.** Every Prestige ask page the player can't
afford (i=180-207) shows only the reason line and Back. The rule allows that, but there is no next step. For example,
"Not enough favor" never offers "How to get favor".

---

## 1. Hub (Home / Goals / More)

### 1.1 Navigation clutter

Taps from opening `/lm`:

| What a player wants | Taps | Path |
|---|---|---|
| Start Difficulty, first time | 5 (7 to finish the guide) | Difficulty, Next, Difficulty ON, T1, Activate T1 (+ Next, Got it) |
| Change race | 3 to 5 | Character Services, Change race, race, (keep %), Confirm |
| Change menu skin | 3 | More, Menu skins, skin |
| Search for a page | 2 + typing + 1 | More, type, Search, result |
| Do the "Fastest now" suggestion | 2 to 3 | Sparring, then find the board yourself. The dashboard line has no button. |
| Do a weekly goal | 2 | Goals, Go to ... (good) |

Per page:

- **Home** (i=0): 11 lines and 7 tiles, which is fine. The full-width **Prestige** tile comes first, but a new player
  is 50,000 levels from prestige. Tapping it opens Prestige Home, which has no action (see 2.1). The first tap a new
  player is invited to make is a dead end.
- **More** (i=12, i=461): the search field sits **under** 9 lines of Meditation and menu text, so results land at the
  bottom of a 477 px page (fit 0.71). The result count ("Results for 'upgrades' 8 pages") is in the top box, far from
  the results. Meditation is read-only text that has nothing to do with search or menus.
- **Goals** (i=11): fine. It earns its tab: three goals, three buttons.

### 1.2 Pitfalls (search)

Search replay for a non-staff player. "Rank" is how `PageRegistry.search` sorts (0 = exact title).

| Word | Hits | What goes wrong |
|---|---|---|
| `tier` | 3 | Difficulty > Tiers, then Prestige > Shop > Difficulty tiers. Two pages with the same name do different things (activate with coins vs. own for good with favor). |
| `prestige` | 10, 8 shown | The header says "8 pages": the count is taken **after** the cut to 8 (UltraHubScreens.java:287 + 295). "Prestige > Prestige" shows twice because the Prestige tab's title is "Prestige" (PageRegistry.java:225). |
| `skin` | 3 | Menu skins comes first. A player who means their character's skin wants Reskin, which is second. Acceptable. |
| `race` | 2 | OK |
| `hair` | **0** | Reskin changes hair and Customize model shows "Hair", but neither has the keyword. |
| `tail`, `wings` | 1 | OK (Body parts) |
| `boost` | **0** | Players have Momentum / Mastery Boost on Difficulty, but "boost" is only on the staff Progression pages. |
| `mastery` | 2 | OK |
| `titles` | 1 | Difficulty > Titles. It works, but nothing else tells a player titles live under Difficulty. |
| `coins` | 2 | Only Sparring wager pages. Nothing for "where do I spend or earn Ancient Coins". |
| `help` | 1 | Only the Difficulty guide. |
| `skills` | **0** | Plural miss: "Skill shop" contains "skill", not "skills". `tiers` works only because the title is plural. |
| `skill check` | **0** | Donators can open it from Home, but its registry entry is `staff(...)` (PageRegistry.java:310), so search hides it. |
| `preview`, `eyes`, `favour`, `colour`, `god ki`, `spin` | **0** | No synonyms or British spellings. |
| `favor` | 1 | Prestige Home only, not the Shop where favor is spent. |
| `menu`, `main` | **91** | Every page's path starts with "Main menu", so the words match everything (PageRegistry.java:440-441). The first hit is "Main menu - Main menu". |

Other search behaviour:

- Results open the page directly (`go:`). Back returns to More with the query and results still there (`QUERY` keeps
  them). That part is good.
- Recents keep 5, newest first, for the session (i=470, 507).
- Results are buttons labelled `Title  -  Path` (two spaces each side). A system Home reads `Dojo  -  Main menu`
  (i=485), which looks like a link to the main menu.
- The empty-state copy promises "a menu, a page or a setting", but players never get settings: settings search is
  staff-only (UltraHubScreens.java:288).

### 1.3 Humanization

| Where | Now | Rewrite |
|---|---|---|
| HubDashboard.java:99 | `Fastest now: a close-power spar, up to x3 TP.` | `Fastest now: spar someone close to your power for up to 3x TP.` |
| HubDashboard.java:151 | `Rival: no RP yet. Challenge someone.` | `Rival: no rank points yet. Challenge someone to start.` |
| HubDashboard.java:175 | `Dojo: none yet. Find a master on Sparring.` | `Dojo: not in one yet. Find a master in Dojo > Training bonds.` The old line is also wrong: masters moved to Dojo in 4.7.78 (PageRegistry `dojo\|mentor`). |
| HubDashboard.java:215 | `Potential Unlock 0 of 30: 0 / 100 points` | `Potential Unlock: level 0 of 30 (0 of 100 points to the next)` |
| HubDashboard.java:59 | `Prestige slots full. Turn some in.` | `You hold the most prestiges allowed. Trade some for favor in Prestige > Shop.` |
| UltraHubScreens.java:291 | `Type part of a name - a menu, a page or a setting - then tap Search.` | Players: `Type what you are looking for, like race, tiers or skin, then tap Search.` Staff keep "or a setting". |
| UltraHubScreens.java:295 | `Results for 'upgrades' 8 pages` | `8 matches for 'upgrades'`, or `Showing 8 of 10 for 'prestige' - add a word to narrow it` when cut. |
| UltraHubScreens.java:331 | `Dojo  -  Main menu` | `Dojo` for a system Home; otherwise `Trophy hall - in Dojo > Upgrades` |
| LivingWorldNearbyMeditation compact line, shown at i=12 | `Meditation 0/10 - 0s/1m` | `Meditation level 0 of 10 (0s of 1m to the next)` |

### 1.4 Shortcomings

- The "Fastest now" line has no button that takes the player to it.
- Goals list no reward. The code comment says "Progress only: no rewards in this build"
  (UltraHubScreens.java:417-419), but the page never says so. A player will expect one.
  **Owner decision:** say "No reward yet - these count toward ..." or show nothing.
- More has no "How Legacy Mechanics works" / help entry. The only guide is inside Difficulty.

---

## 2. Prestige (Home / Prestige / Shop)

### 2.1 Navigation clutter

Taps from the main menu:

| What a player wants | Taps |
|---|---|
| See how far to prestige | 1, and Prestige Home only repeats hub numbers |
| Prestige now | 4: Prestige, Prestige tab, Prestige now, Confirm prestige |
| Trade a prestige for favor | 5: Prestige, Shop, Exchange shop, pack, confirm |
| Buy a kept skill level | 5: Prestige, Shop, Skill shop, skill, Buy |
| Own a tier for good | 5 |
| Buy mastery points | 5; applying them takes 7 to 8 (Mastery wallet, Apply points, line, form, Level up, confirm) |

Dead ends and duplicates:

- **Home is a dead end** (i=4, screenshot): 7 lines and zero action buttons. The only button is the history Back
  (`Back to Sparring`).
- Home and the Prestige tab repeat the same three wallet rows ("Lifetime prestiges", "Current prestige amount",
  "Prestige favor": UltraPrestigeScreens.java:141-145, used by both `openHome` and `openTurnIn`).
- The Prestige tab (i=28) adds one sentence and one button, which is dim until level 50,000. That tab does not earn
  its place.
- Home's level lines say the same thing three times: "Level cap 50,000", "Reach your level cap to earn your next
  prestige.", "Reach level 50,000 first." (UltraPrestigeScreens.java:164-169).
- Shop (i=29) is a 6-way picker. One of its six entries, "Exchange shop", is how you **earn** favor, not spend it, so
  it is misfiled under "Spend Prestige favor".
- Prestige > Shop > Difficulty tiers duplicates the name and the T1 to T7 list of Difficulty > Tiers.
- Skill shop (i=82): 16 skill buttons, with only one line above them. That is fine.

### 2.2 Pitfalls

- **Buttons a player can't use are not dim.** With 0 favor:
  - every Skill shop button is normal (UltraPrestigeScreens.java:757-759);
  - "T1 Awakened - 1 favor" is **green** (UltraPrestigeScreens.java:795-798; `canBuy` checks order, not favor);
  - "Buy Majin", "Buy Mutant" and "Buy Ultimate" are green (i=84).

  Each tap lands on "Not enough favor". The locked-button rule says these should be dim, and each confirm page
  already explains why.
- Exchange packs show `Exchange 1` dim with no reason (i=81). The fallback at UltraPrestigeScreens.java:696-698 drops
  "hold 1 first". Following the `Name - Status` rule it should read `Trade 1 - Locked`.
- Skill shop labels lose their level when the name is long: "Defense Penetration" and "Instant Transmission" have no
  `0/10` (i=82), while the others do. It reads like those two have no levels.
- The Prestige tab's "Prestige favor - exchange current prestige on the Shop tab (loses the stat bonus)."
  (UltraPrestigeScreens.java:188) points to the Shop tab, then a sub-page, for an action that is 2 taps away.
- The Effects page (i=84) sells "Majin", "Mutant" and "Ultimate" without one word on what they do.

### 2.3 Humanization

| Where | Now | Rewrite |
|---|---|---|
| UltraPrestigeScreens.java:141 | `Lifetime prestiges 0` | `Prestiges earned (all time): 0` |
| UltraPrestigeScreens.java:143, 217, 605, 1173 | `Current prestige amount 0/20` | `Prestiges you hold: 0 of 20` |
| UltraPrestigeScreens.java:145, 150 | `Prestige favor 0` | `Favor: 0` (the Shop line explains it, see below) |
| UltraPrestigeScreens.java:164-169 | `Level cap 50,000` / `Reach your level cap to earn your next prestige.` / `Reach level 50,000 first.` | One line: `Next prestige at level 50,000 (you: 1)` |
| UltraPrestigeScreens.java:188 | `Prestige favor - exchange current prestige on the Shop tab (loses the stat bonus).` | `Need favor? Trade a prestige for favor. A traded prestige loses its stat bonus.` |
| UltraPrestigeScreens.java:220 | `Notice This resets your stats, skills, techniques and quest progress, and adds one to your current prestige amount. Kept skill levels and permanent shop purchases survive.` | `Prestiging resets your stats, skills, techniques and quest progress. You gain 1 prestige. Kept skill levels and shop purchases stay.` |
| UltraPrestigeScreens.java:251 | `Spend Prestige favor. Purchases survive prestige.` | `Spend favor here. Everything you buy stays when you prestige.` |
| UltraPrestigeScreens.java:256 / PageRegistry.java:228 | `Exchange shop` | `Trade prestige for favor` |
| UltraPrestigeScreens.java:607-608 | `Exchanging prestige for Prestige favor removes the stat bonus.` | `Each prestige you trade loses its stat bonus. This can't be undone.` |
| UltraPrestigeScreens.java:646 | `Bulk: 3 for 4 favor, 6 for 8 favor, ...` | `Trade more at once for a bonus: 3 for 4 favor, 6 for 8, 9 for 13, 12 for 18, 15 for 23, 18 for 28.` |
| UltraPrestigeScreens.java:687, 695 | `Exchange 1: +1 favor` / `Exchange 1 - hold 1 first` | `Trade 1 for 1 favor` / `Trade 1 - Locked` |
| UltraPrestigeScreens.java:1180 | `You hold 0 - this pack needs 1.` | `You hold 0 prestiges and this trade needs 1. Reach your next prestige first.` |
| UltraPrestigeScreens.java:720 | `Skill levels - 1 favor each (Potential Unlock +2)` | `Each buy adds 1 kept level for 1 favor (Potential Unlock gets 2). Kept levels stay when you prestige.` |
| UltraPrestigeScreens.java:780 | `Difficulty tiers - buy in order, activate with coins.` | `Own a tier for good, in order. You still switch it on with Ancient Coins in Difficulty > Tiers.` |
| UltraPrestigeScreens.java:783 | `Highest owned T0` | `Tiers you own for good: none yet` / `up to T3` |
| PageRegistry.java:229 / UltraPrestigeScreens.java:262 | `Difficulty tiers` (Shop button) | `Own tiers for good` |
| UltraPrestigeScreens.java:830-834 | `Majin / Mutant - 5 favor. One at a time.` | `Majin or Mutant - 5 favor. You can own one at a time.` + one line each on what it does (**owner supplies the effect text**) |
| ask pages, e.g. i=183 | `Not enough favor: this costs 1 favor and you have 0.` | Keep it, and add `Get favor by trading a prestige.` with a `Trade prestige for favor` button (navigation only). |

### 2.4 Shortcomings

- Nothing says what favor is or how you get it until the player finds the exchange.
- There is no "Prestige now" on Home, the page a player lands on.
- Effects have no description.
- After a purchase there is no visible "You now own ..." line on the shop page. The page reloads with the new balance
  only; check `UltraGuiFeedback` delivery.

---

## 3. Difficulty (Home / Tiers / Summons)

### 3.1 Navigation clutter

Taps from the main menu:

| What a player wants | Taps |
|---|---|
| First run, to an active tier | 5: Difficulty, Next, **Difficulty ON** (step 2), T1, Activate T1. If T1 is tapped before turning difficulty on, the player is stuck (see 3.2). |
| After the guide, change tier | 4: Difficulty, Tiers, tier, Pay |
| Change tier while difficulty is OFF | 6: Difficulty, Tiers, "Go to Difficulty Home", Difficulty ON, Tiers, tier, Pay. Tiers has **no toggle** (UltraDifficultyScreens.java:319-327, 4.7.78 decision). |
| Summon a boss | 5: Difficulty, Summons, boss, Summon, confirm |
| Titles | 2: Difficulty, Titles. Only found through Difficulty Home or search. |

Per page:

- Guide step 2 (i=14, screenshot) has 14 buttons: 3 tabs, 7 tiers, the toggle, Next, Back to Step 1 and Skip guide.
  The toggle sits **below** the tier list, after the player has been invited to "Tap a tier".
- Difficulty Home (i=403) has 13 lines and 7 buttons.
  - The Momentum block takes 6 visual lines, and the same text appears on the guide's step 3 and on every tier page.
  - "Beyond Difficulty" repeats the hub dashboard's prestige and rival lines (UltraDifficultyScreens.java:238-245).
- Tiers with difficulty OFF (i=486) is a dead end. Its two buttons, "Go to Difficulty Home" and "Back to Difficulty",
  are both `nav:home`.
- Tier detail (i=57, i=395) has 12 to 15 lines.
  - "Enemy damage" and "Enemy health" always show the same value (UltraDifficultyScreens.java:589-590).
  - "Mastery gain" and "Beat difficulty mobs to bank Mastery Boost time." are the same on every tier (592, 597).
  - "Ancient Coins Copper 0 - Iron 0 - Gold 0 - Emerald 0 - Diamond 0 - Netherite 0" takes 2 lines
    (`AncientCoinEconomy.inventoryBreakdown`, AncientCoinEconomy.java:200-214, lists every coin type even at 0).
- Summons (i=15): for a new player all three bosses are dim. The tab is fine for a mid-game player.
  **Owner decision:** keep Summons as a tab, or fold Titles and Summons into one "Rewards" tab. The count stays 3.

### 3.2 Pitfalls

- **Wrong instruction on the tier page.** With difficulty OFF, every tier detail says "Turn on difficulty on the Your
  tier step first." (UltraDifficultyScreens.java:605, i=57). The Your tier step (i=60) has no toggle; the toggle is on
  step 2 or Difficulty Home. The button also says `Tier locked` (628) for a tier the player has unlocked.
- **"Tier: T1 T1 Awakened"** (i=403, i=552). UltraDifficultyScreens.java:226 prefixes `"T" + active`, and
  `DifficultySnapshot.activeTierName` already starts with "T1 " (DifficultySnapshot.java:63). Guide step 1 has the
  same bug (UltraDifficultyScreens.java:158).
- `Free trial: Free trials open once you meet this tier's requirement.` (i=395) says "Free trial" twice
  (UltraDifficultyScreens.java:619 + DifficultyTrial.java:66).
- Summon buttons read `Dragon locked`, `Wither locked` and `Warden locked` (UltraDifficultyScreens.java:434, 443-444).
  The menu rules say list buttons read `Name - Status` (`End Dragon - Locked`). The dragon also loses its "End".
- Difficulty Home Back after the guide reads `Back to Your tier` (i=403), which points into the finished guide.
  That is N1 again.

### 3.3 Humanization

| Where | Now | Rewrite |
|---|---|---|
| UltraDifficultyScreens.java:226 | `Tier: T1 T1 Awakened` | `Tier: T1 Awakened` (use `snap.activeTierName` alone) |
| UltraDifficultyScreens.java:227 | `Difficulty: ON` | `Difficulty: on - mobs near you match your tier` / `off - mobs are normal` |
| UltraDifficultyScreens.java:196-202 | `Momentum: 0:00 banked of 5:00` + `Beat difficulty mobs to bank Mastery Boost time (+10s each).` + `Mastery from hitting them: +50% while Momentum time is banked.` | `Momentum: 0:00 of 5:00 saved` + one line: `Each difficulty kill saves 10s. While you have time saved, hits give +50% mastery.` |
| UltraDifficultyScreens.java:240-244 | `Beyond Difficulty` block | Cut it (the hub shows the same). |
| UltraDifficultyScreens.java:177 | `This guide shows once. Replay it from Home.` | `You only see this guide once. Replay it any time from Difficulty Home.` |
| UltraDifficultyScreens.java:262 | `Replay the difficulty guide` | `How difficulty works` (the 4.7.63 doc comment already uses this name) |
| UltraDifficultyScreens.java:806-807 | `Coin messages - OFF` | `Coin drop chat - OFF` |
| UltraDifficultyScreens.java:322 | `Turn it on from Difficulty Home, then pick a tier here.` | See P-D2: the toggle moves onto this page, so: `Turn difficulty on to pick a tier.` |
| UltraDifficultyScreens.java:329 | `Tap a tier to see what changes. Nothing is bought until you confirm.` | `Tap a tier to see what changes. Nothing is paid until you confirm.` |
| UltraDifficultyScreens.java:589-590 | `Enemy damage 21% of your power (now normal mobs)` + `Enemy health ...` | One row: `Enemies: 21% of your power (now: normal mobs)` |
| UltraDifficultyScreens.java:591 / DifficultyGuide.java:76 | `TP earned Grows with enemy health` | `TP per kill: grows with enemy health` |
| UltraDifficultyScreens.java:592, 597 | `Mastery gain +50% while ...` / `Beat difficulty mobs to bank Mastery Boost time.` | Cut both (same on every tier; Home explains Momentum). |
| UltraDifficultyScreens.java:593 | `Cost Free for staff` | Keep for staff. Players see `Cost: 2 Gold` (unchanged). |
| UltraDifficultyScreens.java:594 / AncientCoinEconomy.java:200-214 | `Ancient Coins Copper 0 - Iron 0 - Gold 0 - Emerald 0 - Diamond 0 - Netherite 0` | `You have: no Ancient Coins` / `You have: 3 Gold, 12 Copper` (non-zero only, in a menu-only variant; chat keeps the full list) |
| UltraDifficultyScreens.java:595-596 | `Needs Level 1 or Prestige 1 - you have it` / `- not yet` | `Unlocks at: level 1 or prestige 1 (you qualify)` / `(not yet)` |
| UltraDifficultyScreens.java:605 | `Turn on difficulty on the Your tier step first.` | `Difficulty is off. Turn it on below, then pick this tier.` |
| UltraDifficultyScreens.java:628 | `Tier locked` (difficulty off, tier unlocked) | Show the `Difficulty - OFF` toggle in that slot. Keep `T5 - Locked` only for a truly locked tier. |
| UltraDifficultyScreens.java:619 + DifficultyTrial.java:66 | `Free trial: Free trials open once you meet this tier's requirement.` | `Free trial: opens once you meet this tier's requirement.` |
| UltraDifficultyScreens.java:427-431 | `Each boss needs a personal difficulty tier to summon.` / `Different bosses unlock at different tiers - open a boss for its requirements.` / `Select a boss, then confirm.` | `Summon a boss that fights at your tier. Each boss needs a tier range and a place - tap one to see what it needs.` |
| UltraDifficultyScreens.java:434, 443-444 | `Dragon locked` / `Wither locked` / `Warden locked` | `End Dragon - Locked` / `Wither - Locked` / `Warden - Locked` |
| UltraDifficultyScreens.java:467, 502 | `Status Requirements not met` | `Status: not ready yet` |
| UltraDifficultyScreens.java:473, 507 | `Clear the notes above, then return here to confirm.` | `Do the steps above, then come back to summon.` |
| UltraDifficultyScreens.java:480, 514 | `Summon locked` | `Summon - Locked` |

### 3.4 Shortcomings

- No way to turn difficulty on from where a player picks a tier (Tiers tab, tier page).
- After **Activate T1** there is no visible confirmation on the page the player lands on (`askReturn` goes to Tiers,
  which just marks "You are here").
- No line says where Ancient Coins come from: they drop from difficulty mobs when chat is on.
- Titles are hidden behind a Difficulty Home button with no description.

---

## 4. Character Services (Home / Saga / Android)

### 4.1 Navigation clutter

Taps from the main menu:

| What a player wants | Taps |
|---|---|
| Change race | 3 to 5: Character, Change race, race (page 2: +1), keep % (optional), Confirm |
| Change class | 4: Character, Change class, class, Confirm |
| Reskin | **4**: Character, Reskin, Review cost, Confirm. The Reskin page and the confirm page repeat the same 5 lines. |
| Wear a tail | 4 to 5: Character, Body parts, Tail filter, part (+ unlock confirm) |
| Recolor a body part | 5: Character, Body parts, Part colors, Part color, swatch |
| Reset a saga | 4 to 9: Character, Saga, Next x0 to 5, saga, Confirm reset |
| Convert to Android | 4: Character, Android, Convert, Confirm and pay |

Per page:

- **Home** (i=5, screenshot, fit 0.88) has 12 visual lines of "Name: description" index text above six buttons with
  the same names. The buttons are buried under their own captions.
  - The **Healing** line is a stat, not a service (UltraCharacterScreens.java:229-232).
  - There is no Main menu button (N1).
- **Three overlapping cosmetic entries:** "Customize model" (head pieces only), "Body parts" and "Reskin" (hair, eyes
  and colors). The Reskin page then calls the first one "Model customization" (CharacterServicesGuiApi.java:173), a
  name that appears on no button.
- **Change race** (i=32): 11 lines + 20 races + Next + footer = 23 buttons, fit 0.69. 24 races at 20 a page puts
  Soulreaper, Sphinxian, Taimujin and Viltrumite on a page of their own (i=117).
- **Confirm race change** (i=97, fit 0.72) has 21 lines.
  - "Race / Keep / You are becoming / Keeping 0% of eligible stats" says two facts in four lines.
  - 6 stat rows `Strength 0 -> 0` come next, and the Confirm button sits below all of them.
- **Body parts** (i=35, fit 0.76): 8 visual lines of rules, 6 filters + Find + 8 cards + Next + Clear + Part colors +
  Spin = 21 buttons. "Find a part" takes a row by itself.
- **Part colors** (i=141) has two buttons per channel that open the same page: `Part color` and `Default`
  (UltraCharacterScreens.java:1301-1312). That is 8 buttons for 4 choices.
- **Saga** (i=30): 46 sagas, 8 a page, 6 pages, sorted A to Z (SagaResetService.java:256). A new player pages through
  dim "Not started" entries looking for the one saga they can reset.
- **Android tab** (i=31): for most races it is one line of text ("only for certain races") and no button. The tab
  does not earn its place for most players.

### 4.2 Pitfalls

- The race page says "Listed cost is for keeping 100%" (CharacterServicesGuiApi.java:94), but **no cost is listed** on
  the race buttons. Two lines later the page repeats "Keeping everything costs ... exact cost shown on the next page"
  (UltraCharacterScreens.java:461-463), and the top row says `Keep 0% Free` (458). That is three lines about one fact.
- Keep buttons read `25% free`, `50% free` and so on (UltraCharacterScreens.java:588, i=97). That reads like "25% is
  free", not "keep 25% - free".
- Class buttons are **green confirm style** (UltraCharacterScreens.java:661-662, i=33), while race and saga options
  were made normal in 4.7.81. Twelve green buttons look like twelve primary actions.
- The Saga tab header says `Pay Ancient Coins to replay one saga.` (UltraCharacterScreens.java:1415), but the confirm
  can say `This is free` (SagaResetService.java:123-125).
- Saga names fall back to prettified ids: `Bl Arc Arrancar`, `Bl Arc Soul Society` (i=30; `SagaResetService.prettify`,
  SagaResetService.java:196).
- Remove Android tells the player to `Click again within 10 seconds.` (UltraCharacterScreens.java:1545). That breaks
  "Ask pages act on the first Confirm"; Convert already has a proper confirm page (1592-1607).
- Customize model says `A locked part charges Ancient Coins when you turn it on.` (CharacterServicesGuiApi.java:132).
  In fact a locked part opens an unlock confirm with its price (`bone_ask`, PageRegistry.java:281). The copy makes
  tapping sound risky.
- Reskin shows three different money messages at once (i=36, staff, but the same three lines print for a paying
  player):
  - `Cost free (staff - no charge)` (CharacterServicesGuiApi.java:354)
  - `No charge for your account` (CharacterServicesSystem.java:339)
  - `You'll be charged Ancient Coins when you confirm on the next screen.` (CharacterServicesGuiApi.java:178)

  For a paying player, the third line appears twice in spirit: on both the Reskin and Reskin confirm pages.
- Body parts page title row `Body parts 1/15 On: none` packs a page number, a label and a status with no separators
  (UltraCharacterScreens.java:1081-1082).

### 4.3 Humanization

| Where | Now | Rewrite |
|---|---|---|
| UltraCharacterScreens.java:205 | `Change race: You are a Saiyan. You choose how much of your stats to keep.` | Home index cut to one status row: `Saiyan - Warrior - wearing Hair, no body parts` (see P-C1) |
| UltraCharacterScreens.java:215 | `Customize model: Equipped: Hair. Swap the parts on your head.` | Button renamed `Head parts` |
| UltraCharacterScreens.java:231 / HealCap.java:41-56 | `Healing: Life Steal off \| Overheal 0% \| Received +0%` | `Healing: no life steal, no overheal, normal healing taken`. **Owner decision** on moving it to Skill Check. |
| UltraCharacterScreens.java:457, 633-635 | `Races 24 page 1/2` / `Classes 12` | `Page 1 of 2` (drop the count). Classes: drop the row. |
| UltraCharacterScreens.java:458 | `Keep 0% Free` | Cut |
| CharacterServicesGuiApi.java:94 + UltraCharacterScreens.java:461-463 | `Listed cost is for keeping 100%. 0% keep is free - pick keep % on the next screen.` + `Keeping everything is free - exact cost shown on the next page.` | One line: `Next you choose how much of your stats to keep. Keeping none is free; keeping all costs 4 Gold.` |
| CharacterServicesSystem.java:377 | `Ready whenever you are (no cooldown for your account)` | Hide when there is no cooldown. Otherwise `You can change again in 3h 12m.` |
| CharacterServicesGuiApi.java:222-223 + UltraCharacterScreens.java:571-572 | `Race Ancient Saiyan` / `Keep 0%` / `You are becoming Ancient Saiyan.` / `Keeping 0% of eligible stats.` | `You become: Ancient Saiyan` / `You keep: 0% of your stats` |
| CharacterServicesGuiApi.java:213, 230 | `Free - wipe stats, then pick class and look` | `Free: your stats start over, then you pick a class and look.` |
| CharacterServicesSystem.statPreviewLines (i=97) | 6 rows `Strength 0 -> 0` ... | 2 rows: `Strength 0, Strike 0, Defense 0` / `Vitality 0, Ki Power 0, Energy 0 (after)` |
| CharacterServicesGuiApi.java:235, 243 | `If the change fails, your coins are returned when possible.` | `If the change fails, your coins are refunded.` (keep "when possible" only if refunds can fail) |
| CharacterServicesGuiApi.java:236 | `Staff cannot undo this for you.` | `This can't be undone - not even by staff.` |
| UltraCharacterScreens.java:588 | `25% free` | `Keep 25% - Free` / `Keep 25% - 3 Gold` |
| CharacterServicesGuiApi.java:132 | `A locked part charges Ancient Coins when you turn it on.` | `A part you don't own shows its price first. Nothing is charged until you confirm.` |
| UltraCharacterScreens.java:819 | `Head On: Hair` | `Head parts - wearing: Hair` |
| CharacterServicesGuiApi.java:144 | `Extra pieces for your KaiPath model preview - wings, horns, tails.` | `Wear extra pieces like wings, horns and tails.` (no internal mod name; they show in the world too) |
| CharacterServicesGuiApi.java:145 | `A locked piece charges Ancient Coins when you turn it on.` | Cut (line 1084 already says "Locked pieces show their price...") |
| CharacterServicesGuiApi.java:146 | `Shown on model previews; unlocked pieces stay in your collection.` | `Pieces you unlock stay yours, even after you take them off.` |
| UltraCharacterScreens.java:1081-1082 | `Body parts 1/15 On: none` | `Page 1 of 15 - wearing: nothing` |
| CharacterServicesGuiApi.java:158 | `Pick a color for each part channel - own, trim, horn, glow.` | `Color your body parts: main color, trim, horns and glow.` |
| CharacterServicesGuiApi.java:160 | `Reset returns a channel to your form's default colors.` | `Reset puts a color back to your form's own color.` |
| UltraCharacterScreens.java:1301-1312 | `Part color` + `Default` (two buttons, same page) | One button: `Main color - Default` / `Trim color - #e23b3b` |
| CharacterServicesGuiApi.java:173 | `Use Model customization for cross-race ears, horns, and more.` | `For ears, horns or tails from other races, use Head parts or Body parts.` |
| CharacterServicesGuiApi.java:174-175 | `Reskin only changes how you look. To change your fighting class, use Change class instead.` / `Level, stats, and race are unchanged.` | `Only your look changes. Level, stats, race and class stay the same.` |
| CharacterServicesGuiApi.java:178 | `You'll be charged Ancient Coins when you confirm on the next screen.` | Cut once the pages merge (P-C4) |
| CharacterServicesGuiApi.java:192 | `Level, stats, race, and class stay locked during the session.` | `While the editor is open, only your look can change.` |
| UltraCharacterScreens.java:772 | `Review cost` | Gone after P-C4; the confirm reads `Open the look editor - Pay 2 Gold` |
| UltraCharacterScreens.java:1415 | `Pay Ancient Coins to replay one saga.` | `Reset a saga to play it again. Each one shows its price before you confirm.` |
| UltraCharacterScreens.java:1416-1418 | `Sagas 46 page 1/6` | `Page 1 of 6` |
| SagaResetService.java:196 (prettify) | `Bl Arc Arrancar` | `Bleach: Arrancar Arc` (map the `bl_arc_` and `bleach_` prefixes) |
| UltraCharacterScreens.java:1545 | `Click again within 10 seconds. Converting to Android deletes ...` | Use a confirm page: `Remove Android? Your Super and Legendary forms come back. Free.` + `Remove Android` |
| UltraCharacterScreens.java:1557 | `Priced by level: 1 Gold coin, plus 1 per 100 levels.` | `Price: 1 Gold, plus 1 Gold per 100 levels.` |
| UltraCharacterScreens.java:1580 | `Another player` | `Convert another player` (staff) |
| UltraCharacterScreens.java:1632 | `Staff only. Confirm before convert or remove.` | `Staff only. You will confirm before anything changes.` |
| UltraCharacterScreens.java:960 | Back label `Model customization` | `Head parts` |

### 4.4 Shortcomings

- No overview of "what I look like now": the race, class, head parts and body parts summary is spread over five
  index lines.
- No direct way from Body parts' **Find a part** results to Part colors.
- No preview of a race before paying. Preview forms is staff-only (UltraCharacterScreens.java:196).
  **Owner decision:** let players preview a target race's base form on Confirm race change.
- The Saga list doesn't show which sagas the player can reset first.

---

## 5. Skill Check / Skills

### 5.1 Navigation clutter

- One page, 15 lines and **no action button** (i=8): a read-only dead end.
- There are no tabs. That is acceptable for a single donator/staff screen, but it means no Home/3-tab structure.
- No link to where skills are bought (Prestige > Shop > Skill shop) or how they unlock.

### 5.2 Pitfalls

- Abbreviations appear in a list that is otherwise spelled out: `Heal Red` and `IT`
  (SkillUnlockService.java:212-213), while the Prestige skill shop spells them out (i=82).
- `Staff: /skills - Donators: /skillcheck` (UltraSkillCheckScreens.java:74) is a raw command line on a menu page.
  It is staff-only, so it is low priority.
- Every skill shows `Locked` with no hint of what unlocks it.

### 5.3 Humanization

| Where | Now | Rewrite |
|---|---|---|
| SkillUnlockService.java:212 | `Heal Red 0/10 - Locked` | `Healing Reduction 0/10 - Locked` |
| SkillUnlockService.java:213 | `IT 0/10 - Locked` | `Instant Transmission 0/10 - Locked` |
| SkillUnlockService.java:188-190 | `Potential 0/30 - 0/100` | `Potential Unlock 0/30 (0 of 100 points)` |
| meditation compact line (i=8) | `Meditation 0/10 - 0s/1m` | `Meditation 0/10 (0s of 1m)` |
| UltraSkillCheckScreens.java:74 | `Staff: /skills - Donators: /skillcheck` | `Staff open this with /skills; donators with /skillcheck.` |

### 5.4 Shortcomings

- Add one line, `Locked skills unlock through the story. Kept levels can be bought with favor.`, and a
  `Buy skill levels` button (`go:prestige|skills`).

---

## 6. Staff Admin, Progression and config editor (lighter touch)

### 6.1 Navigation and pitfalls

- **Staff Admin** (i=7): 4 actions and a paged help book. Fine. The help reads `Guitest: /lm admin guitest`.
- **Progression Home** (i=6) is a 13-line flag dump with no actions, and the tabs hold the controls.
  - `TP TP boost, Mastery boost and Bio-Android on` reads "TP TP" because the label and value run together; the same
    happens on the `Race ...` and `Shop ...` rows.
  - Its name in Staff Admin is "Progression panel", but the page title is "Progression".
- **Coins tab** (i=38) says `/lm -> Staff Admin -> Progression panel -> Ancient Coins` (ProgressionGuiApi.java:795)
  while the staff member is already on that page.
- **Config editor** (i=41, screenshot) shows only **2 settings a page** on a full-screen frame, so "Player Scaling (36
  settings)" is 18 pages. Each setting takes 5 rows:
  - the name, truncated (`Apply DMZ Extras To...`);
  - `Now ON - default`;
  - the help text, truncated, with the internal `V3:` prefix (ConfigFieldHelp.java:139);
  - the ON toggle;
  - `Accepts: true / false`, which says nothing an ON/OFF toggle doesn't (UltraConfigScreens.java:638).

  The module tab `Progress` is cut from "Progression". The breadcrumb ends in a generic `Settings`
  (PageRegistry.java:315).

### 6.2 Humanization (staff)

| Where | Now | Rewrite |
|---|---|---|
| ConfigFieldHelp.java:139 | `V3: reset active tier/level on player death (unlocks stay).` | `Dying resets the player's active tier (unlocks stay).` (also drop the other 2 `V3` prefixes) |
| UltraConfigScreens.java:638 | `Accepts: true / false` | Hide for on/off settings. |
| UltraProgressionScreens.java:58-61 | `TP TP boost, Mastery boost and Bio-Android on` | `TP: TP boost, Mastery boost and Bio-Android are on` |
| ProgressionGuiApi.java:795 | `/lm -> Staff Admin -> Progression panel -> Ancient Coins` | Cut (they are on that page). |
| PageRegistry.java:315 | crumb `Settings` | the section name (`Player Scaling`) |

---

## Proposed changes

Size: **S** is copy or a small branch in one method; **M** is one screen's layout; **L** spans screens or nav code.
Everything below changes presentation, navigation or copy only: no prices, numbers or rules. **[OWNER]** marks the
items that need an owner decision.

### Cross-cutting

**P-N1 (S). A system Home always exits to the main menu.**

- What: in `UltraScreenSpec.placeBack` (UltraScreenSpec.java:668-675), when `home` is true use `nav:hub` +
  `BACK_TO_MAIN` and skip `NavHistory`. Sub-pages keep history.
- Before: `Back to Sparring` (Prestige Home), `Back to Part color` (Difficulty Home), `Back to Tiers` (Difficulty
  Home after Goals).
- After: `Back to main menu`.
- Effect: removes the worst wrong-destination bug on 5 Homes (Difficulty, Prestige, Character, Skills, Progression)
  and restores the written rule.

**P-N2 (S). Start a fresh trail when a menu is opened fresh.**

- What: clear `NavHistory` for the player when a screen is opened by command, NPC or hotkey rather than by a menu
  click. Today only logout clears it (DifficultyEvents.java:464).
- Where: the `UltraGui.open` entry used by commands, plus a new `NavHistory.reset(UUID, screen, page)`.
- Effect: no more "Back to (a page from an hour ago)".

**P-N3 (S). Back labels never end on a dangling word.**

- What: in `UltraStyle.backTo` (UltraStyle.java:280-283), if trimming would end on "to", "a", "of" or "the", fall
  back to `Back`.
- Before: `Back to Convert to`. After: `Back`.

**P-N4 (S). Not-enough-favor and not-enough-coins confirms offer the way to get more.**

- What: in `UltraPrestigeScreens.askFinish` (1147-1160), when `block` is a favor shortfall, add a normal
  `Trade prestige for favor` button (`page:exchange`).
- Effect: closes the N4 dead end on 6 confirm families.

### Hub

**P-H1 (M). Search goes to the top of More, and results replace the other sections.**

- Where: `UltraHubScreens.openMore` (387-414) and `searchLines` / `placeSearchButtons` (278-342).
- What: put the field row first. While a query is active, hide the Meditation and Your menus lines, and show
  `8 matches for 'x'` (or `Showing 8 of 10 ...`) right above the results.
- Before/after copy: as in table 1.3 (lines 291, 295, 331).
- Effect: More with results goes from 477 px (fit 0.71) to about 360 px (fit about 0.94). Results sit right under the
  field. 9 lines become 2.

**P-H2 (S). Search finds what players type.**

- Where: `PageRegistry.search` (PageRegistry.java:440-441) and keyword strings.
- What:
  - Match against title + keywords + the path **without** its leading "Main menu > ". That removes the 91-hit `menu`
    and `main` problem.
  - Strip a trailing "s" from each query word before matching, so `skills` finds Skill shop.
  - Add keywords:
    - Reskin: `hair eyes look appearance colour`
    - Head parts: `hair horns ears antenna`
    - Difficulty: `boost momentum help`
    - Prestige shop: `favour favor spend`
    - Mastery wallet: `god ki form mastery`
    - Body parts and Part colors: `colour`
    - Difficulty guide: `help how`
    - Skill shop: `skills buy`
  - Register Skill Check with `p(...)`, not `staff(...)`, so donators find it (the screen already gates who can
    open it).
- Count fix: report `hits` before `subList` (PageRegistry.java:453; UltraHubScreens.java:287, 295).
- Effect: `hair`, `boost`, `skills`, `colour`, `favour` and `skill check` go from 0 hits to the right page; `menu`
  drops from 91 hits.

**P-H3 (S). "Fastest now" gets a button.**

- Where: `UltraHubScreens.openMain` (177), using the destination `HubDashboard.fastestNow` already computes.
- What: add one wide normal button `Go: <short name>` (for example `Go: Spar Board`, `Go: Difficulty Tiers`).
- Effect: saves 1 to 2 taps on the hub's main suggestion. +1 button, so Home has 8.

**P-H4 (S). Dashboard copy.** Rewrite lines 99, 151, 175, 215 and 59 of HubDashboard.java as in table 1.3. This also
fixes the wrong "Find a master on Sparring" pointer.

**P-H5 (S) [OWNER]. Goals say whether they pay a reward.** Add one line under "Weekly goals": `No rewards yet - these
track your week.` Or remove the expectation some other way.

### Prestige

**P-P1 (M) [OWNER: tab contents change, count stays 3]. Home / Shop / Trade.**

- Where: `UltraPrestigeScreens.base` (128), `openHome` (153-177), `openTurnIn` (180-206), `openShopHub` (244-280),
  `openExchange` (597-629).
- Home becomes:
  - `Prestiges you hold: 0 of 20`
  - `Prestiges earned (all time): 0`
  - `Favor: 0 - spend it in the Shop, get it by trading prestiges`
  - `Next prestige at level 50,000 (you: 1)`
  - Fastest now
  - one wide button: `Prestige now` (danger) when ready, else dim `Prestige - Locked`
- The Prestige tab is replaced by **Trade**: today's Exchange page, as a tab.
- The Shop loses "Exchange shop". It keeps Skills, Own tiers for good, Effects, Mastery wallet and Custom shop, which
  is 5 entries.
- Effect:
  - Home stops being a dead end: Prestige now goes from 4 taps to 3.
  - Trading for favor goes from 5 taps to 4.
  - The duplicate wallet block goes away.
  - The "spend" picker holds only spend actions.

**P-P2 (S). Dim what the player can't afford.**

- Where: `openSkillsShop` (757-759), `openTiers` (795-798, add `favor >= cost` to the green test) and `openEffects`
  (the Buy buttons around 848-878).
- What: dim style when favor is short. The button still opens the confirm page, which explains why (allowed by the
  rules).
- Effect: no more green "buy" buttons at 0 favor.

**P-P3 (S). Rename Difficulty tiers in the Shop to `Own tiers for good`.**

- Where: `openShopHub` (262), PageRegistry.java:229, and the copy at 780-783.
- Before/after: as in table 2.3.
- Effect: the `tier` search and the Shop no longer show two identical "Difficulty tiers" menus.

**P-P4 (S). Exchange labels and copy.** Change lines 607-608, 646, 687, 695 and 1180 as in table 2.3. Dim packs read
`Trade 6 - Locked` instead of a bare `Exchange 6`.

**P-P5 (S) [OWNER content]. Effects get a one-line description each.** Majin, Mutant and Ultimate need the effect
text, which the owner supplies (UltraPrestigeScreens.java:830-895).

### Difficulty

**P-D1 (S). Fix "T1 T1 Awakened".** At UltraDifficultyScreens.java:158 and 226, use `snap.activeTierName` alone. The
self-test should assert that no "T# T#" appears.

**P-D2 (S) [OWNER: reverses the 4.7.78 "toggle only on Home" note]. Turn difficulty on where you pick a tier.**

- Where: `openTiers` (319-327) and `openTierAsk` (605, 628).
- What:
  - When personal difficulty is off, Tiers shows the tier list plus the `Difficulty - OFF` toggle (as guide step 2
    already does), instead of the "Go to Difficulty Home" dead end.
  - The tier page puts the toggle where the false `Tier locked` was, with copy
    `Difficulty is off. Turn it on below, then pick this tier.`
- Effect: changing tier while off goes from 6 taps to 4. Removes the duplicated `nav:home` pair and the wrong "Your
  tier step" instruction.

**P-D3 (M). A lighter tier detail page.**

- Where: `openTierAsk` (587-620).
- What: merge damage and health into one row, cut the two lines that are the same on every tier (592, 597), show
  non-zero coins only, and rewrite Needs.
- Copy: as in table 3.3.
- Effect: 12 to 15 lines become 8 to 10. Panel height goes from 327 to about 270, and the Pay button moves up 4 rows.

**P-D4 (S). Difficulty Home copy.**

- What: cut "Beyond Difficulty" (238-245), make Momentum 2 lines, rename "Replay the difficulty guide" to "How
  difficulty works" and "Coin messages" to "Coin drop chat".
- Effect: 13 lines become 7. Panel height goes from 351 to about 270 (fit 1.0). Buttons are unchanged.

**P-D5 (S). Summons labels follow `Name - Status`.** Change lines 434 and 443-444 (`End Dragon - Locked` etc.), plus
the copy at 427-431, 467, 473, 480, 502, 507 and 514.

**P-D6 (S). The guide's step 2 puts the toggle first.**

- Where: `openTiers` guide branch (353-357).
- What: place `Difficulty - OFF` above the tier list while off, with the line `First, turn difficulty on.`
- Effect: the step-2 trap (tapping T1 first) goes away.

### Character Services

**P-C1 (M). Home becomes a picker, not a wall of captions.**

- Where: `UltraCharacterScreens.openHome` (173-253).
- What:
  - Replace the 6 index lines with one status row: `Saiyan - Warrior - wearing Hair, no body parts`.
  - Keep a reason line **only** for a blocked destination (for example `Reskin: available again in 3h`).
  - Move or rewrite Healing (owner decision).
  - Rename the buttons: `Customize model` becomes `Head parts`, and `Reskin` becomes `Hair, eyes and colors`
    (198 px fits).
- Effect: 14 lines become 2 or 3. Panel height goes from 386 to about 260 (fit 0.88 to 1.0). The 6 buttons sit
  right under the title.

**P-C2 (L) [OWNER: tab set change, count stays 3]. Home / Looks / Story.**

- What:
  - Home: Change race, Change class, and Android (a button that is dim with a reason for ineligible races).
  - Looks: Head parts, Body parts, Part colors, Hair, eyes and colors, and Preview forms (staff).
  - Story: Saga reset.
- Where: `UltraCharacterScreens.base` tabs and PageRegistry character entries.
- Effect:
  - Android stops being a near-empty tab for most players.
  - Part colors goes from 5 taps to 4.
  - Each tab has 3 to 4 buttons.

**P-C3 (M). Change race fits on one page.**

- Where: `openRace` (446-523); `RACES_PER_PAGE` (440) becomes 24.
- What:
  - Three columns of 131 px (at least 80, so the rule holds).
  - Cut `Keep 0% Free`, the `Races 24` count and the doubled keep-cost lines, down to one line (table 4.3).
- Effect: 24 races on 1 page instead of 2, 11 lines become 4, and the panel goes from 495 to about 340 (fit 0.69 to
  about 1.0). No Next button.

**P-C4 (M). Reskin is one page.**

- Where: `openReskin` (767-776), `openReskinConfirm` (778-797), and CharacterServicesGuiApi.java:163-202.
- What: Reskin opens the confirm content directly (what changes, price, refund note) with the pay button.
- Effect: 4 taps become 3. 19 lines across two pages become about 5 on one. The three contradicting cost lines
  become one.

**P-C5 (M). Confirm race change is readable.**

- Where: `openRaceConfirm` (554-620) and CharacterServicesGuiApi.java:217-237.
- What: put `You become` and `You keep` on 2 lines, the stat preview on 2 lines, and the keep buttons as
  `Keep 25% - Free`. Confirm stays where it is.
- Effect: 21 lines become about 10. Panel height goes from 471 to about 330 (fit 0.72 to about 1.0).

**P-C6 (S). Class options are normal style.** Change UltraCharacterScreens.java:661-662 from `STYLE_CONFIRM` to
`STYLE_NORMAL`, matching race and saga (4.7.81).

**P-C7 (S). One button per part color.**

- Where: `openBodyColors` (1301-1312).
- What: `Main color - Default` replaces the two buttons.
- Effect: 8 buttons become 4, plus a channel rename (table 4.3).

**P-C8 (M). A tighter Body parts page.**

- What:
  - The rules text drops from 4 sentences to 2 (table 4.3).
  - `Find a part` joins the Clear parts / Part colors row instead of taking its own row (1090).
  - The title row becomes `Page 1 of 15 - wearing: nothing`.
- Effect: panel height goes from 447 to about 350 (fit 0.76 to about 0.97).

**P-C9 (S). Saga list puts what you can reset first.**

- Where: `SagaResetService.offers` sort (256).
- What: sort resettable sagas (Done, In progress) first, then Locked, then Not started, alphabetical within each
  group. Name map for `bl_arc_` and `bleach_`.
- Effect: a player's resettable saga is on page 1, saving up to 5 Next taps.

**P-C10 (S). Remove Android gets a confirm page, like Convert.**

- Where: `openAndroidRemove` UPGRADED branch (1541-1552).
- What: add `page:android_remove_confirm`, and remove the "Click again within 10 seconds" text.
- Effect: meets "Ask pages act on the first Confirm". It still takes 2 clicks, now on clear pages.

**P-C11 (S). Character copy.** Apply every row of table 4.3 that is not covered above: KaiPath, part channel, locked
part, saga header and the Android lines.

### Skill Check / Skills

**P-S1 (S). Spell out the skill names, and give the page one action.**

- Where: SkillUnlockService.java:188-190 and 212-213; `UltraSkillCheckScreens.show` (48-79).
- What: full skill names; add the line `Locked skills unlock through the story. Kept levels can be bought with
  favor.` and a `Buy skill levels` button (`go:prestige|skills`).
- Effect: the page is no longer a dead end; the abbreviations are gone.

### Staff tools

**P-A1 (S). Config editor rows stop wasting space.**

- Where: UltraConfigScreens.java:638 and 1674-1681.
- What: hide `Accepts: true / false` and `Now ON - default` on on/off settings (the toggle shows the state).
- Effect: 5 rows a setting become 3, so about 3 to 4 settings fit a page instead of 2. Player Scaling goes from 18
  pages to about 10.

**P-A2 (S). Staff copy.** Drop the `V3:` help prefixes (ConfigFieldHelp.java), fix "TP TP" (UltraProgressionScreens.java:58-61),
cut the self-referencing path (ProgressionGuiApi.java:795), and rename the `Progress` module tab to `Progression`.

---

## Owner decisions needed

1. **P-C2:** Character tabs become Home / Looks / Story; Android folds into Home. The count stays 3.
2. **P-P1:** Prestige tabs become Home / Shop / Trade; Prestige now moves to Home. The count stays 3.
3. **P-D2:** the Difficulty toggle also appears on Tiers and on the tier page (reverses the 4.7.78 note).
4. **P-H5:** what Goals say about rewards.
5. **P-P5:** effect descriptions for Majin, Mutant and Ultimate.
6. Healing line: keep it on Character Home, move it to Skill Check, or drop it (P-C1).
7. Optional: whether a Summons tab is worth keeping for new players, or Titles + Summons become "Rewards" (3.1).

## Side note (not a menu change)

CLAUDE.md says "No raw `§` in LM Java sources". Player text still builds raw codes in
`character/CharacterServicesSystem.java` (60 occurrences, for example lines 339 and 377), `CosmeticHeadBoneService.java`
(35) and `gui/ProgressionGuiApi.java` (207). The rewrites above touch several of those lines; they should move to
`UltraStyle` while they are edited.
