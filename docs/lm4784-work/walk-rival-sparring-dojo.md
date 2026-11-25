# LM 4.7.84 Phase 1: new-player walk of Rival, Sparring and Dojo

Audit and proposal only. Nothing here changes code, and no Dojo Points earn or cost number changes. Phase 2
builds the items the owner approves.

- **Scope:** Rival (Home | Rival | Challenge), Sparring (Home | Leaderboard | Spar Board) and Dojo
  (Home | Upgrades | Wars). Another agent covers the hub, Difficulty, Prestige and Character.
- **Baseline:** worktree `DMZServer-4784`, origin/main `6cf5d6db` (LM 4.7.82) plus the 4.7.83 label fix.
  Source paths below are under `tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/`.
- **Real menus:** `DMZRig/results/release-4.7.83-run1/matrix/full/uiprobe-packets.jsonl`. "i=N" is the crawl
  index and matches `ui-NNN-1080p-gs3.png`. Screenshots viewed: i=56, 68, 443, 452, 459, 515.
- **Input audit:** `docs/DOJO_IMPROVEMENT_AUDIT_2026-11-24.md`. Every P0-P3 item (#1-#18) is carried into
  section 4 and mapped to a proposed change.

**Crawl caveat.** The crawler often fires a button "from" a page without reopening that page first. The
`Back to X` label comes from the per-player history (`NavHistory`), so many footers in the crawl name the
page the crawler visited last, not the page a player would come from. Examples: i=18 "Back to Challenge",
i=440 "Back to Last fight", i=451 "Back to Disciple capacity". Those are **not** reported as bugs. The
back-label problems that a real player also hits are in X1-X3.

**Pages the crawl did not reach** were read from code only: war pick, war format and war view; the banner
pickers; founding steps 2-3 and the confirm page; Watch spars; the Spar Board's Challenge a player; the
dojo member lists; Close dojo.

---

## 0. Summary for the owner

The menus *work*. The clutter comes from five patterns that repeat across all three systems:

1. **The main action is buried and the Home tab holds the side stuff.** On Sparring Home a new player
   sees Weights, TP Chat and two lines about clout (i=3). Posting or accepting a spar is on the third tab.
   Dojo Home has 9 action buttons (i=459) but no link to the Spar Board, which is what a dojo is for.
2. **Pages are too tall, so they shrink.** Dojo Home (460-486 px, i=458/459), Records - Stats (393 px,
   i=68), an upgrade page (392 px, i=440), Style and bonus (372 px, i=452) and the Spar Board (352 px,
   i=515) all go past the ~300 px that stays readable on a small window. `NativeMenuFit` then scales them
   down to 0.5x, and the buttons end up under 12 px. Dojo Home also wastes 6 blank rows next to its floor
   plan (i=459 screenshot).
3. **The same thing has several names and several doors.** Dojo Home has "Roster and bonds", "Members and
   elites" and "Training bonds" (i=459), and the Roster page shows the same lines as Training bonds (i=499
   vs i=498). A ring is also called an arena and a mat. Sparring and Rival both have a "TP Chat" button,
   and each one sets something different.
4. **Some copy says the wrong thing** (checked against the code). It gets the wager refunds, the
   apprentice cap ("up to 8", but the real cap is 5-29), the capacity step ("5 more", which is +4 at the
   top), the bonus cooldown ("8 days", which is 7), the rival TP bonus ("+100%", which is +0%) and the
   trophy hall ("shows off your war wins", which it does not) wrong. It also says elites "can invite
   members", which they cannot. Founding says "a banner you made at a loom", but any plain banner works.
5. **Joining is the hardest thing to do.** A player browsing dojos has no Join button. The list doesn't
   name the master or say whether they are online. The real path is 7 taps through Training bonds, picking
   the master's name from every player online.

---

## 1. Cross-cutting navigation (all three systems)

| # | Finding | Evidence |
|---|---|---|
| X1 | **Switching tabs is recorded as a Back step.** Going Home > Wars > Home gives Home the footer "Back to Wars" (i=459, i=497/459). From the dojo, "Back" walks through tabs instead of leaving the system. The rule in `docs/CNPC_GUI_MENU_RULES.md` says "a system Home shows one 'Back to main menu'". | `NavHistory.record`/`siblings` (tab pages have no `:` so they are never "siblings") |
| X2 | **A long page name loses its last words in the Back label, and the result reads wrong.** i=512 shows "Back to Wager Ancient" (the page is "Wager Ancient Coins"). | `UltraStyle.backTo` (UltraStyle.java:280) drops words to fit |
| X3 | **Four ways out on every sub-page:** the breadcrumb, Back to X, Main menu and the client's Close button. When the breadcrumb and Back disagree, they point to different places. i=56 has the breadcrumb "Main menu > Dojo > Training bonds" and the footer "Back to Dojo leaderboard". | screenshots i=56, i=68, i=452 |
| X4 | **The six-action rule is broken.** Dojo Home has 9 action buttons. The Spar Board has 8 setting/action buttons plus the Accept rows. Style and bonus has 3 style buttons plus 7 bonus buttons in one grid with no labels. | i=459, i=515, i=452 |
| X5 | **Pages too tall to stay readable.** The corpus floor is 0.75x on an 854x480 window at GUI scale 2. That allows a native panel of roughly **300 px** (estimate: a 240 px GUI height, divided by 0.75, minus margins). Over budget: dojo home 460-486, records stats 393, upgrade detail 392, style 372, spar board 352, upgrades 330. Lists with no cap: elites (one row per disciple, up to 29), Recruit / Request master pickers (`NAMES_PER_PAGE = 20`). | crawl `panelH`; `tools/lm-gui-corpus/findings/layout-findings.md:45` |
| X6 | **Dim buttons are missing where the player can't act yet:** Leave mentor with no mentor, Release with no apprentices (i=56), Spectate with 0 fights (i=17), Send a challenge with no rivals online (i=17/66). Each one opens an empty page instead. | `UltraSparScreens.openMentor` :411-421; `UltraRivalScreens.openChallenge` :340 |
| X7 | **The same thing has different names.** Ring / arena / sparring mat: Home "Hall basic, 1 sparring mat", the Upgrades tab "Sparring arenas", the upgrade page "Now 1 ring", the map "[Ring 1]". Disciples / members / apprentices: Home "Members 2 of 26", capacity "Now 25 disciples", Training bonds "Your apprentices 1/8". "Clout" is never explained. | i=459, i=443, i=440, i=446, i=498 |

### Taps to each common action (hub Main menu = 0)

| Action | Path today | Taps | Problem |
|---|---|---|---|
| Post a spar (in a dojo, no wager) | Sparring > Spar Board tab > Post for 15 min | 3 | Not on Sparring Home; Dojo Home has no link to it |
| Post a spar with a coin wager | Sparring > Spar Board > Wager > Ancient Coins > amount (back on the board) > Post | 6 | Settings mixed in with the board list |
| Accept a spar | Sparring > Spar Board > Accept X (> Accept and stake) | 3-4 | Only 5 posts are shown, with no pager (audit #6) |
| Spar with no dojo | Sparring > Spar Board > "Join a dojo first" > Find a dojo > list | 4 then **dead end** | The list has only Visit buttons (audit #5) |
| Find and join a dojo | Dojo > Browse dojos > (no join) > Back > Training bonds > Request master > pick the master from all online players > Ask | 7, plus the master's 4 to accept | The list doesn't name the master or say if they are online |
| Found a dojo | Dojo > Found a dojo (free) > name + Next > style > banner > Found X | 6 | Dead end at step 3 without a banner in the inventory (audit #9) |
| Buy an upgrade | Dojo > Upgrades > item > Buy | 4 | OK |
| Buy a style bonus | Dojo > Upgrades > Style and bonus > bonus > Buy | 5 | OK |
| Declare a war | Dojo > Wars > Declare a war > pick a dojo > Declare: bracket | 5 | Plus the master's sign-off for an elite (3 more) |
| Join a war | Dojo > Wars > Join the war | 3 | Only within 60 s; the only notice is one chat line (audit #7) |
| See a spar result | Sparring > Spar Board > Last fight | 3 | Only the last fight; the button is hidden until there is one |
| See a war result | Dojo > Wars > a "Past wars" line | 2 | Lines only, last 4, no detail page; the bracket view exists only while the war runs |
| Rival: challenge a rival | Rival > Challenge tab > Send a challenge > rival > length | 5 | Fine; the button should be dim when nobody can be picked |

---

## 2. Rival (screen `rival`, `gui/ultra/UltraRivalScreens.java`)

### 2.1 Navigation clutter
- **Home (i=2: 9 buttons, 1 line, 195 px).** Home holds only information: My rival stats, Leaderboard,
  Records and TP Chat. Picking a rival and challenging one are on tabs 2 and 3. The single line describes
  the buttons, not the system.
- **"My rival stats" (i=18) and "Records > Stats" (i=68) overlap.** Both show RP, tier and mutual slots.
  Records > Title (i=74) repeats the tier, perk and TP multiplier from Stats.
- **Records (i=20, 68-74):** every sub-page repeats the 8-button section row, so each page has 10
  buttons. Records - Stats is 15 lines + 10 buttons = **393 px** and shrinks
  (`rival_records_stats` is in the under-12 px list).
- **Tab "Rival" (i=16)** has the same name as the system ("Rivals"). It is really "My rivals".
- Rival pages in the under-12 px list: rival_actions, rival_challenge, rival_list_detail,
  rival_pick_rival, rival_records_stats, rival_requests.

### 2.2 Pitfalls (checked in code)
| Copy | Where | What actually happens |
|---|---|---|
| "Rival TP bonus +100%" for an Acquaintance | `rival/RivalSystem.java:961` | `tpMult` 1.0 means *no* bonus. It prints `tpMult*100` with a "+" in front. Title page "TP Gain: 100%" (`RivalProgression.java:247`) is correct. |
| "highest rp - LTProbe (0 RP)" (Hall of fame, i=72) | `rival/RivalProgression.java:216` | A raw snake_case key (`highest_rp`) with `_` swapped for a space. It is lower case and uses raw `§` (breaks the UltraStyle rule). |
| "TP chat ON (/rival tpmsg)" (i=68) | `RivalSystem.java:970` | Puts a slash command in a menu. The toggle is one tap away on Home. |
| "Combat (after Overhaul)" / "Overhaul scale x1" (i=68) | `RivalSystem.java:979`, `calc/LmOverhaulScaledCombat.java:148` | Internal system name. Raw `§7` at :148. |
| Spectate with 0 fights, Send a challenge with nobody online (i=17) | `UltraRivalScreens.openChallenge` :340 | Both are live buttons that open an empty page (X6). |

### 2.3 Humanization (current copy, then a rewrite)
| File:line | Now | Rewrite |
|---|---|---|
| UltraRivalScreens.java:123 | "Your stats, leaderboard, records, and TP chat." | "Pick a rival, beat them in timed challenges, and earn bonus TP for it." |
| :161 | "Your rivals. Tap one for their profile, or Pick a player to add one." | "Your rivals. Tap a name to see how you stack up, or tap Pick a player to add one." |
| :624 | "Accept a declare to become Mutual. Decline leaves them Declared only." | "Accept to become rivals both ways. Decline and they stay your one-way rival." |
| :951 | "Next you choose Silent, Declare, or Accept a mutual request." | "Next you choose: keep it private, or ask them to be rivals back." |
| RivalGuiApi.java:168 | "Timed fights against a rival. Challenge deaths can raise Nemesis." | "Timed fights against a rival. Die to the same rival in challenges and they can become your Nemesis." |
| RivalSystem.java:961 | "Rival TP bonus +100%" | "Rival TP bonus +0%" (prints `(tpMult-1)*100`) |
| RivalSystem.java:962 | "Record 0/0/0 (KO 0)" | "Record 0 wins, 0 losses, 0 draws (0 knockouts)" |
| RivalSystem.java:965 | "Challenges 0 Surpass 0" | "Challenges 0 - Surpassed a rival 0 times" |
| RivalSystem.java:970 | "TP chat ON (/rival tpmsg)" | (drop the line; the toggle is on Rival TP messages) |
| RivalSystem.java:979 | "Combat (after Overhaul)" | "Your combat stats" |
| RivalProgression.java:216 | "highest rp - LTProbe (0 RP)" | "Most RP: LTProbe (0 RP)", "Most battles: ...", "Longest streak: ..." |

### 2.4 Shortcomings
- There is no "what is a rival" explanation anywhere a new player looks first.
- Stats are split across three places (My rival stats, Records > Stats, Records > Title).

---

## 3. Sparring (screen `spar`, `UltraSparScreens.java` + Spar Board pages in `UltraDojoScreens.java`)

### 3.1 Navigation clutter
- **Home (i=3: 7 buttons, 5 lines).** It holds Weights, TP Chat and Staff Admin (staff only, `StaffAccess`).
  There is no Spar Board link; the comment at :171 says "Home does not advertise it". A new player has to
  find the third tab on their own.
- **Leaderboard tab (i=23).** Two lines that say what its two buttons do, then the two buttons. This is a
  menu page standing in for a tab.
- **Spar Board (i=515: 14 buttons, 8 lines, 352 px).** It mixes four kinds of thing in one grid:
  - post settings (Kind, Wager, Range, Looking for);
  - the Post button;
  - the list of other players' posts (Accept X);
  - other actions (Challenge a player, Watch spars, Last fight).

  "Post for 15 min" sits next to the Wager toggle, and "Accept Wager80" sits under the "Looking for"
  toggle (screenshot). Each post's text is in the body and its Accept button is four rows lower.
- **The board shows 5 posts with no pager.** `BOARD_SHOWN = 5` (`UltraDojoScreens.java:27`).
  `openBoard` :218 uses `min(BOARD_SHOWN, posts.size())`. The "On the board (N)" header counts every post
  but lists only 5. No sort; `BoardSpar.board` order is used.
- **Wager sub-menu** (i=510, i=511, i=512) is clean: kind, then amount. "Back to Wager Ancient" is X2.
- **Result (i=438)** is reached only from the board's "Last fight". Earlier fights are not there.

### 3.2 Pitfalls (checked in code)
| Copy | Where | What actually happens |
|---|---|---|
| "Forfeiting gives the win away. Any wager is refunded to both." (in-fight, i=437) | UltraDojoScreens.java:174 | On FORFEIT `decisive` is false (`BoardSpar.java:623`), so nothing counts: no ranked win, no host points. The wager is refunded (`:686`). No win is given. (Audit #3) |
| "A forfeit or the clock running out refunds both." | :306, :344, :400 | True today. It becomes false once the approved settle fix ships (P2-1 below). |
| "You forfeited. Any wager was refunded." | dojo/BoardSpar.java:511 | Same; changes with the fix. |
| Result after a forfeit: "Didn't count: a spar counts after 1 minute with both fighters landing hits." (i=438) | BoardSpar.java:706 | That reason is wrong for a forfeit or a disconnect. Those never count, however long the fight ran. |
| "Friendly Fist off" on each post (i=515) | UltraDojoScreens.java:223-224 | Board spars are non-lethal whatever Friendly Fist is set to (audit "verified working"). The field suggests a risk that isn't there. |
| "Ranked: locked" dim button (i=434) | :232 | It hides the kind actually in use (Friendly). Also "Label: X" breaks the "Label - Value" pattern. |
| "Spar Board ... found your own in the Dojo section" + "Find a dojo" (i=24) | :184-189 | "Find a dojo" opens a list with only Visit buttons, so the player can't join from it (audit #5). |
| Two "TP Chat" buttons: Sparring > TP Chat (spar + mentor TP) and Rival > TP Chat (rival TP) | UltraSparScreens.java:185, UltraRivalScreens.java:127 | Same label, different settings. The Sparring page has no explanation lines; Rival's has two. |
| Weights input footer "Back to main menu" with no Main menu button (i=77) | UltraWeightShopScreens.openInput | The Back target is the weights shop, but the label names the hub. |

### 3.3 Humanization (current copy, then a rewrite)
| File:line | Now | Rewrite |
|---|---|---|
| UltraSparScreens.java:172 | "What a spar is worth to you" | "Sparring" (header) + "Fight other players in a dojo ring for TP and dojo clout." |
| HubDashboard.java:199 | "Join a dojo: each counted ranked spar adds 1 clout to it (up to 10 a day)." | "Join a dojo to spar. Each ranked spar that counts adds 1 clout (dojo ranking points) to it, up to 10 a day." |
| UltraSparScreens.java:175 | "Potential Unlock trains in the world, and in friendly spars when you look for it." | "Want to train Potential Unlock? Pick 'Looking for - Potential Unlock' on a friendly spar." |
| UltraSparScreens.java:197-198 | "Hall of Infamy the top sparrers by TP, sessions and combos" / "Your stats your spar record and your last three fights" | "See who spars the most, and your own last three fights." |
| UltraDojoScreens.java:184 | "Join a dojo first. No dojo, no spar." | "You need a dojo to spar. Spars happen in a dojo's ring." |
| :185 | "Spars happen in a dojo's arena. Join one, or found your own in the Dojo section." | "Ask to join one from the dojo list, or found your own (free)." |
| :207 | "Nothing posted. Fights happen in LTProbe's Dojo's arena." | "You have no post up. Your fights happen in LTProbe's Dojo." |
| :209 | "Looking for Potential Unlock trains it in friendly spars. Ranked spars never do." | (moves to the Post a spar page) "Potential Unlock trains only in friendly spars, never in ranked ones." |
| :210-211 | "Ranked 0 of 30 friendly spars done, 30 to go" | "Ranked unlocks after 30 friendly spars: 0 done, 30 to go." |
| :216 | "No posts yet. Post one, or challenge a player." | "Nobody is looking for a spar right now. Post your own, or challenge someone directly." |
| :222-227 (post line) | "Wager80 Lv 1, friendly, Friendly Fist off, 1x Emerald, looking for TP, waiting 0:00" | "Wager80 - Lv 1 - friendly - stakes 1 Emerald coin - wants TP - posted 0:00 ago" |
| :232 | "Ranked: locked" (dim) | "Kind - Friendly" (dim; the line above says why) |
| :450-452 | "Challenge one player to a friendly spar (no wager). Change kind and wager on the Spar Board first." | "Challenge one player to a friendly spar with no wager. To change that, set it on Post a spar first." |
| :487 | "Watch from a dojo's spectator room. Only dojos that built one can be watched." | "Watch a spar from a dojo's balcony. Dojos without a balcony can't be watched." |
| UltraSparScreens.java:660 | "Training point messages" + toggles "TP Chat - ON" / "Mentor TP - ON" with no lines | Header "Spar TP messages" + "Spar TP: a chat line each time a spar pays you TP." + "Mentor TP: a chat line when your apprentices' spars pay you a share." Buttons "Spar TP - ON" / "Mentor TP - ON". |

### 3.4 Shortcomings
- The board is the main thing in Sparring, and it sits on the last tab (owner pick G1).
- Only 5 posts are visible, the posts are not sorted, and there is no want/level filter (audit #6).
- A player not in a dojo has no way forward from the board (audit #5).
- Wagers can be dodged by forfeiting or by stalling until time runs out (audit #3; the fix is approved).
- Points wagers are for masters and elites only (audit #12). This is disclosed (:308) but never decided.

---

## 4. Dojo (screen `dojo`, `UltraDojoScreens.java`, `DojoFounding.java`, dojo pages of `UltraSparScreens.java`)

### 4.1 Navigation clutter, by page

**Home, no dojo (i=13: 7 buttons, 6 lines, 238 px).** Clean. The Found button is first and green.
"Browse dojos" leads nowhere useful (no join). The "Training bonds" button is the only real way in, and
its name doesn't say so.

**Home, with a dojo (i=459: 12 buttons, 18 lines of which 6 are blank, 460 px; i=458 486 px).**
- 12 stat lines, then 6 empty rows (`hallPlan(... List.of("Upgrades build into your hall."))` :566 pads to
  the picture's 7 rows). The floor plan sits below the stats instead of beside them (screenshot i=459).
- 9 action buttons: Choose banner, Rename, Roster and bonds, Members and elites, Training bonds, Visit,
  Dojo leaderboard, Close dojo, plus Change banner once a banner exists. Rows 1-2 are half empty
  (screenshot).
- **The same thing behind three buttons.** "Roster and bonds" opens `dojo_roster`, whose body is
  `SparGuiApi.mentorLines`, the same as Training bonds (i=499 = i=498). Its buttons are Dojo members,
  Previous members and Training bonds again. "Members and elites" is the elites page.
- **No Spar Board link.** Hosting spars is how a dojo earns its points.
- "Dojo leaderboard" appears on Home *and* Wars.

**Upgrades (i=443: 14 buttons, 8 lines, 330 px).** The text column is hand-wrapped to 25 characters
(:620-623) next to the plan, with an empty 8th line. 8 upgrade buttons plus "Style and bonus" alone in the
last row.

**Upgrade page (i=440: 4 buttons, 20 lines, 392 px).** LM clients get the picture *and* the text map
(`[Ring 1] *Ring 2* ...`, 6 lines) plus 3 blank rows (`PICTURE_ROWS = 10`, :655). The text map is
needed only by KaiPath clients.

**Style and bonus (i=452: 13 buttons, 10 lines, 372 px).**
- 3 style buttons and 7 bonus buttons sit in one grid, unlabeled. When the bonus is on cooldown, all 7
  bonus buttons are dim and all 3 style buttons are live (screenshot), so the page invites the expensive
  change.
- "The bonus can change again in 8 days" right after a 7-day lock (:780 `cd / DAY_MS + 1`; the same
  off-by-one is in `DojoService.pickBonus` :564).

**Elites (i=455).** One row per disciple and **no pager** (:950-967). At 29 disciples that is 29 rows,
which shrinks to 0.5x and then scrolls.

**Wars (i=456: 9 buttons, 7 lines, 273 px).** A good structure since 4.7.81 ("Now" block in button order).
Past wars are lines with no "View" button. "War starting" is just a button "Join the war" with no
countdown. The no-dojo state (i=51) is one line and two footer buttons. It should offer Found / Browse.

**Dojo list (i=478: 7 buttons, 4 lines).** It is paged (6 a page). Rows show "1. X's Dojo - 30 clout,
2 members, Melee" with **no master name and no online state**. Buttons: sort toggles plus "Visit <name>".
There is no join. "1 members" (:1167). "No dojos yet. Found the first one!" (:1157) has no Found
button on the page.

**Training bonds (i=56: 7 buttons, 6 lines).** Leave mentor is live red with no mentor, and Release is
live with no apprentices (X6). The body ends with "Training bonds has Recruit, Request master and
Release.", which is a line describing the buttons.

**Pickers (pick_apprentice i=171, pick_mentor i=172, pending i=173).** "Request master" lists *every*
online player, not dojo masters. `NAMES_PER_PAGE = 20` (UltraSparScreens.java:429; its comment says "the
page scrolls"), against the no-scroll rule.

**Founding (i=54 and code).** Three steps plus a confirm, built well. On step 1 the "Next" button is
beside the field (105 px, fine). Step 3 ends at "You have no banner." with only Back (DojoFounding.java:278).

### 4.2 Pitfalls (checked in code)
| Copy | Where | What actually happens |
|---|---|---|
| "Dojo: up to 8 apprentices - one master"; "Your apprentices 1/8" (i=56, i=498) | gui/SparGuiApi.java:305, :322 (`SparringSystem.MAX_APPRENTICES = 8`) | The real cap is `apprenticeCap` = `DojoService.disciplesAllowed`: 5 to 29 by capacity tier. Home says "Members 2 of 26" in the same session. |
| "Room for 5 more disciples." at the last tier (i=449: Now 25, After 29) | dojo/DojoCatalog.java:62 | `disciplesAllowed` caps at 29 (`MAX_MEMBERS - 1`), so the last step is +4. |
| "The bonus can change again in 8 days." just after buying | UltraDojoScreens.java:780; DojoService.java:564 | The lock is 7 days; `left / DAY_MS + 1` rounds 6.99 up to 8. |
| "Pedestals that show off your war wins." (Trophy hall) | DojoCatalog.java:58 | Nothing writes war wins into the trophy hall (audit #17). It is decoration. |
| "Elites ... invite members, buy upgrades and declare wars." (i=455); the promote notice says the same | UltraDojoScreens.java:942; DojoService.java:634 | **Elites cannot invite members.** Recruit (`mentorInvite`) makes the recruit the *elite's own* apprentice. `DojoService.disciples` counts only the master's bond and `dojoOf` follows the mentor, so that recruit is in no dojo. |
| "No disciples yet. Invite players from Roster and bonds." | UltraDojoScreens.java:947 | Roster and bonds has no invite button. Recruit is under Training bonds. |
| "Founding asks for a name, a style and a banner you made at a loom." (i=13); "Make one at a loom (a banner, dyes and patterns), then come back." | UltraDojoScreens.java:533; DojoFounding.java:279 | `DojoFounding.addBanner` (:461) accepts **any** banner, including a plain crafted one (the corpus shows "Use: Red, plain"). No loom is needed. The copy makes the friction in audit #9 look bigger than it is. |
| "Changing style ends the current bonus." with no total | UltraDojoScreens.java:775, :875-880 | Style 40 + re-picking the bonus 40 = 80 points; never shown as one number (audit #11). |
| "The war ... starts in a minute. Join it in the Dojo tab to fight." | dojo/DojoWar.java:273-274 | Dojo has been its own system since 4.7.78. Join is on Dojo > **Wars**. The message goes once, to online members only. |
| "Members of both dojos sign up for one minute once they accept." (war format) | UltraDojoScreens.java:1120 | True. It doesn't say a war can end up as a single 1v1 bout, or that it is called off with no fighters (DojoWar.java:379). |
| "Hall basic, 1 sparring mat" | UltraDojoScreens.java:665 | Everywhere else it is a "ring" (X7). |
| "Player not online: <arg>" | gui/SparGuiApi.java:644, :654 | If Ask to join passes `uuid:<id>` (proposed), this prints a raw UUID. |
| "Dojo cooldown active (12h)." / "Mentor change cooldown active (12h)." | sparring/SparringSystem.java:1736, :1772 | Doesn't say how long is left or what to do. |
| "Dojo full (...). Buy capacity in the Dojo tab for more." | SparringSystem.java:1742 and :1936 | The Dojo tab is gone; capacity is on Dojo > Upgrades. |

### 4.3 Humanization (current copy, then a rewrite)
| File:line | Now | Rewrite |
|---|---|---|
| UltraDojoScreens.java:531-533 | "A dojo is your home for spars, wars and training." / "Found your own (free, you become its master), or join one." / "Founding asks for a name, a style and a banner you made at a loom." | "A dojo is your team's home: you spar in its rings, fight wars as a team, and train with a master." / "Join one from the list below, or found your own for free and become its master." / "To found one you need a name, a fighting style and any banner (craft one from 6 wool and a stick)." |
| :548-557, :563, DojoFounding.java:304 (12 stat lines) | Master / Style / Bonus / Dojo Points / Clout / Members / Hall / Wars / Elites / Banner, one per line | 6 lines: "Master LTProbe (you) - Melee style"; "Bonus: Majin tradition"; "Dojo Points 1385 - Clout 30 this season"; "Members 2 of 26 - Elites: Sparmate2"; "Hall: basic, 1 of 6 rings - Wars 0 won, 0 lost"; "Banner: not picked yet - tap Choose banner". |
| :566 | "Upgrades build into your hall." | (drop; the Upgrades tab says it) |
| :613 / :765 / :933 | "Join or found a dojo first." | "You're not in a dojo yet. Join one or found your own on the Dojo Home tab." + live buttons "Browse dojos" / "Found a dojo". |
| :979 | "Join or found a dojo to fight wars." | "Wars are fought between dojos. Join or found one first." + "Browse dojos" / "Found a dojo". |
| :620-623 | "Earn points by hosting / spars, winning war bouts / and training disciples. ..." | "Earn Dojo Points by hosting spars, winning war bouts and training your disciples." / "Tap an upgrade to see it before you buy." |
| :636 | "Sparring arenas - 80" | "Rings - 80 points" (same for every upgrade: "<name> - N points", "<name> (max)") |
| :738 | "Cost already at its best" | "Fully built. Nothing more to buy here." |
| DojoCatalog.java:62 | "Room for 5 more disciples." | "Room for N more disciples." (N = `disciplesAllowed(t+1) - disciplesAllowed(t)`) |
| DojoCatalog.java:58 | "Pedestals that show off your war wins." | "A trophy room with pedestals for your hall (decoration)." *(owner may prefer to keep the promise and build it; audit #17)* |
| :665 | "1 sparring mat" | "1 ring" |
| :772-775 | "Members get the bonus when they fight in the dojo's style." / "Changing style ends the current bonus." | "Your style decides which bonuses you can buy. Members get the bonus on spar TP from that kind of hit." / "Changing style costs 40 and ends the bonus you paid for." |
| :780 | "The bonus can change again in 8 days." | "You can change the bonus in 7 days." (round down to whole days, or "in 6 days 23 hours") |
| :942 | "Elites (2 at most) invite members, buy upgrades and declare wars." | "Elites (2 at most) can buy upgrades and declare wars." *(drop "invite members" unless the owner wires it, pick F)* |
| :947 | "No disciples yet. Invite players from Roster and bonds." | "No disciples yet. Invite players from Training bonds > Recruit." |
| :986 | "A war is a knockout tournament: bracket or gauntlet, KO or ring-out wins." | "A war is a knockout tournament between two dojos. Each bout is won by knockout or by pushing the other fighter out of the ring." |
| :1023 | "Sign-up closed" (dim) | "Sign-up has closed" (dim) + the bracket button |
| :1089 | "There are no other open dojos yet." | "No other dojos are open right now, so there's nobody to fight yet." |
| :1120 | "Members of both dojos sign up for one minute once they accept." | "Once they accept, members of both dojos have 1 minute to sign up on the Wars tab. With one fighter each, the war is a single bout." |
| :1157 | "No dojos yet. Found the first one!" | "No dojos yet. Be the first: found one for free." + live "Found a dojo" button |
| :1166-1167 | "1. X's Dojo - 30 clout, 2 members, Melee" | "1. X's Dojo - master Name (online) - 30 clout - 2 members - Melee" ("1 member" when 1) |
| DojoWar.java:273-274 | "The war A vs B (bracket) starts in a minute. Join it in the Dojo tab to fight." | "War! A vs B (bracket). Sign-up is open for 1 minute: open Dojo > Wars and tap Join the war." |
| SparGuiApi.java:321 | "Recruit apprentices or ask a master from Training bonds." | "Train with a master, or take on apprentices of your own. A master's apprentices are their dojo's members." |
| SparGuiApi.java:322 | "Dojo: up to 8 apprentices - one master" | "Your dojo can hold up to N apprentices. You can have one master." (N = `apprenticeCap`) |
| SparGuiApi.java:308-309 | "Mentor share 15.0% each (dojo split)" | "You get 15% of each apprentice's spar TP." |
| SparGuiApi.java:331 | "Training bonds has Recruit, Request master and Release." | (drop) |
| SparGuiApi.java:383-385 | "No pending mentor invites." / "Outgoing: you invited someone." / "Incoming: they invited you - Accept or Decline." | "No invites waiting. Invites you send or get show here for 1 hour." |
| SparringSystem.java:1736 | "Dojo cooldown active (12h)." | "You changed your apprentices recently. You can invite again in 3h 20m." |
| SparringSystem.java:1772 | "Mentor change cooldown active (12h)." | "You left a master recently. You can ask a new one in 3h 20m." |
| SparringSystem.java:1742 and :1936 | "...Buy capacity in the Dojo tab for more." | "...Buy Disciple capacity on Dojo > Upgrades for more room." |
| SparGuiApi.java:644/654 | "Player not online: <arg>" | "<Name> isn't online right now. Try again when they are." (never print `uuid:`) |
| DojoFounding.java:279 | "Make one at a loom (a banner, dyes and patterns), then come back." | "Craft any banner (6 wool and a stick; add patterns at a loom if you like), hold it or carry it, then come back. Your name and style are saved." |

### 4.4 Shortcomings: every dojo audit item, mapped

| Audit item | Kind | Where it goes in this proposal |
|---|---|---|
| #1 Two war systems; old `/spar dojo challenge` 2x-RP window | behavior + copy | Open pick **(a)**. No menu opens the old path; only commands and the old `SparGuiApi` actions do. |
| #2 Ki sense dead for menu wars | correctness, **owner approved** | **P2-2** |
| #3 Wager refunded on forfeit/timeout; "gives the win away" copy | correctness + copy, **owner approved** | **P2-1** (settle) + **P2-1c** (every copy line, below) |
| #4 40 auto-dojos on first boot; nudge masters to pick a style and banner | deploy + copy | **D14** (Home nudge for a style-less or banner-less master; presentation only). The build storm is a deploy checklist item (outside this menu walk). |
| #5 No join from the list | presentation, reusing an existing action | **D3** "Ask to join" |
| #6 Board 5 posts, no pager/filter | presentation | **S2** |
| #7 War sign-up 60 s, one chat line | presentation + (flag) duration | **D6** (countdown notice); 60 s change = open pick **(e)** |
| #8 Disciples get nothing personal | design | Open pick **(b)** |
| #9 Loom-banner requirement | owner's 4.7.78 decision | Open pick **(d)**. **D9** fixes the copy (any banner works). |
| #10 Slow points / no founding grant | economy | Open pick **(c)**. **D10** shows the earn rates (display only). |
| #11 Style change 40 + 40 never totaled | presentation | **D5** |
| #12 Points wagers master/elite only | decision | Open pick **(h)**; copy kept |
| #13 Failed hall build sticks until restart | robustness (code) | Out of menu scope. Listed under "Other correctness" for Phase 2 if the owner wants it. |
| #14 Pair cooldowns in memory | robustness | Same as #13 |
| #15 Dead `found(p)`, dead `pick_dojo_challenge` page | cleanup | **D13** (delete the dead route + registry entries) |
| #16 Dojo names ASCII only | small rule change | Open pick **(i)**. Copy "3 to 24 letters, numbers or spaces" stays correct either way. |
| #17 Meditation / quarters / trophy cosmetic | design + copy | **D11** (honest copy) + open pick **(j)** |
| #18 Pages with click targets under 12 px; heading fixes | layout | **D1, D2, D4, D7, D8, S1, S2, R2** (de-clutter to under ~300 px; no scrolling) |

Further gaps found in this walk (not in the audit):
- The elite "invite members" promise is not wired (pick **F**).
- The apprentice cap shows 8 instead of the dojo capacity.
- Capacity "+5" is wrong at the top tier.
- The bonus cooldown shows one day too many.
- Changing style during the bonus lock resets the lock. `bonusCooldownLeft` returns 0 once `setStyle` clears
  the bonus, so 80 points skips the 7-day lock. This is a behavior observation; flagged, no change proposed.
- An Ask to join or invite to a busy master overwrites the earlier one: `SparStore.invites` is keyed by the
  target (SparringSystem.java:1753 and :1786).
- Past wars have no detail page.
- The Spar Board has no "posts for you first" sort.

---

## 5. Proposed changes

Size: S = under half a day, M = 1-2 days, L = more. "Owner" = needs an owner decision before Phase 2
builds it. Every rewritten line has its before/after in sections 2.3, 3.3 and 4.3. The rows below cite
those tables and give only what is new.

### 5.0 Owner-approved correctness (Phase 2)

**P2-1: Wager settle on forfeit and timeout.**
- Change: in `dojo/BoardSpar.java` `settle` (:686), the `payTo` argument becomes
  `winner != null && !"DISCONNECT".equals(how) ? winner : null`. A forfeit pays the opponent. A timeout
  with a damage leader pays the leader. A timeout with equal damage (`winner == null`), a disconnect, and a
  crash (`refundStaleEscrow`) still refund both.
- Ranking credit stays as it is (a forfeit still doesn't count); see pick **E**.
- Size S. Owner: approved. Needs an `LM_SHIP_MANIFEST.md` row and a check in `audit_ship_manifest.py`.

**P2-1c: Copy for the new wager rule (every line).**

| File:line | Before | After |
|---|---|---|
| UltraDojoScreens.java:174 (in-fight) | "Forfeiting gives the win away. Any wager is refunded to both." | "Forfeiting ends the spar and your opponent wins. If there's a wager, they take both stakes." |
| UltraDojoScreens.java:305-306 (Wager page) | "Whoever accepts stakes exactly the same. The winner takes both stakes." / "A forfeit or the clock running out refunds both." | "Whoever accepts stakes exactly the same. The winner takes both stakes." / "That includes a forfeit, and running out of time (more damage wins). Stakes come back only on a disconnect, a dead-even draw or a server restart." |
| UltraDojoScreens.java:344 (Wager coins / points) | "The winner takes both stakes. A forfeit or the clock running out refunds both." | "The winner takes both stakes, even on a forfeit or when time runs out (more damage wins)." + new line "Stakes come back only on a disconnect, a dead-even draw or a server restart." |
| UltraDojoScreens.java:400 (Accept page) | same as :344 | same as the new :344 lines |
| dojo/BoardSpar.java:511 (forfeit notice) | "You forfeited. Any wager was refunded." | "You forfeited, so <name> wins." + (with a wager) " They took both stakes." |
| BoardSpar.java:504 and :33 (javadoc) | "the other fighter wins, wagers are refunded" | "the other fighter wins and takes any wager" |
| BoardSpar.java:706 (result, how = FORFEIT or DISCONNECT) | "Didn't count: a spar counts after 1 minute with both fighters landing hits." | FORFEIT: "Didn't count for ranking: a forfeit never counts." DISCONNECT: "Didn't count: someone disconnected. Any wager was refunded." (The 1-minute line stays for KO, ring-out and timeout.) |
| BoardSpar.java:765 (result Wager line) | "refunded to both (X each)" | unchanged (still true for disconnect and draw) |

- Test rig and kit (memory: "upgrade creation tools"): `tools/lm-ui-probe/.../UiProbe.java:1162` (the
  "forfeit refunds both" check becomes "forfeit pays the winner") and `:3072` (`has(p, "refunds both")`).
  Also `tools/lm-gui-corpus/pages/spar_dojo.json:347,380,427,500,549` and the `tools/specs` copies.
- Size S. Owner: approved (copy follows the rule).

**P2-2: Ki sense in menu wars.**
- Change: `sparring/DojoRankings.java` `onlineWarOpponents` (:497) also reads `DojoStore` wars with
  status RUNNING for the viewer's dojo (`DojoService.dojoOf`). It returns the online master and disciples
  of the other dojo. The old `activeChallengeFor` read (:1165) stays for the old path until pick (a)
  decides it.
- Effect: war foes show on ki sense as 4.7.77 promised. Size S. Owner: approved.

### 5.1 Cross-cutting

**X1: Tabs replace each other in history.** In `gui/ultra/NavHistory.java` `record`/`siblings`, treat the
three tab pages of one system (as declared in `.tabs(...)`, or registry pages whose parent is that system's
Home) as one family. Switching tabs then replaces the top entry.
- Effect: from any tab, Back leaves the system ("Back to main menu" or wherever the player came from),
  as `CNPC_GUI_MENU_RULES.md` says. No more Home > "Back to Wars" loop.
- Also update `NavHistory.selfTest`. Size S. Owner: no.

**X2: Short back names.** Add an optional short title to `PageRegistry.p(...)`, used by `UltraStyle.backTo`
when the full one doesn't fit:

| Page | Full title | Short title |
|---|---|---|
| board_wager_coins | Wager Ancient Coins | Coin wager |
| board_wager_points | Wager Dojo Points | Points wager |
| records:stats | Record stats | Stats |

- If neither fits, use the registry parent's name; never a cut phrase. Before: "Back to Wager Ancient".
  After: "Back to Coin wager".
- Size S. Owner: no.

**X3: The breadcrumb and Back agree.** When the history target differs from the registry parent, the
breadcrumb's last link is the history target. Or Back follows the registry parent on sub-pages and history
only across systems. Pick one model; the recommendation is **registry parent for sub-pages, history for the
system Home**, because it is predictable for new players.
- Size M (touches every footer; needs the rig's nav checks). Owner: yes (navigation model).

**X5: Height budget.** Add a GuiKit audit: native panel height ≤ 300 px for every player page in the corpus
(`tools/lm-gui-corpus/gen/build_corpus.py` already measures this). It fails the build like the other
audits. The pages over budget are fixed in D1, D2, D4, D7, D8, S1, S2 and R2.
- Size S (audit) + the per-page items. Owner: no.

**X6: Dim where you can't act.** Make these dim, each with a reason line:
- Leave mentor (no mentor), Release (no apprentices): `UltraSparScreens.openMentor` :411-421.
- Spectate (0 fights), Send a challenge (no rival online): `UltraRivalScreens.openChallenge` :340.
- Recruit / Request master (nobody online).

Labels in the "Name - Status" form: "Leave mentor - none", "Spectate - no fights now".
- Size S. Owner: no.

**X7: One word each.** "Ring" everywhere (not arena or mat). "Members" for the count that includes the
master, "disciples" for everyone else (the master's apprentices). Say "Apprentices" only on the Training
bonds page, where the bond is the subject. Explain clout once: "Clout: your dojo's ranking points this
season".
- Size S. Owner: no.

### 5.2 Rival

**R1: Home says what Rival is.** In `UltraRivalScreens.openHome` :121-133:
- Change the line (2.3, :123).
- Buttons: [My rival stats] [Leaderboard] / [Records] [Rival TP messages] (renamed from "TP Chat").
- Effect: the first screen says the point of the system. "TP Chat" is no longer two different settings.
- Size S. Owner: no.

**R2: Merge the stats pages and slim Records.**
- "My rival stats" (`openInfo` :136) becomes the one stats page: RP, tier, perk, TP bonus, record, streak,
  mutual slots. It absorbs Records > Stats and Title.
- Records keeps History, Season, Quests, Achievements, Hall of fame and Journal: 6 section buttons, so 3
  rows instead of 4.
- Combat stats (Overhaul lines) leave the rival page. They belong to Character (the other agent's area).
- Records - Stats 393 px is gone. The new stats page is about 10 lines + footer ≈ 230 px.
- Size M (`openRecords` :547-582, `recordsTitle`, `RivalSystem.statsLines` :955-981, registry
  `records:stats`/`records:title` items pointed at `info`). Owner: no (no tab change).

**R3: Fix the copy pitfalls.**
- `RivalSystem.java:961` TP bonus becomes `(tpMult-1)*100`.
- `:962`, `:965`, `:970` per 2.3.
- `RivalProgression.java:216`: a label map for the Hall of fame keys, and UltraStyle instead of raw `§`.
  `:196`, `:202` and `:250` have the same underscore-replace pattern.
- `LmOverhaulScaledCombat.java:148`: raw `§7`.
- Size S. Owner: no.

**R4: Rename the "Rival" tab to "My rivals".**
- Change: `UltraRivalScreens.base` :109, "actions|Rival" becomes "actions|My rivals".
- Effect: the tab no longer repeats the system name.
- Size S. **Owner: yes (tab label change).**

**R5: Plain-language copy for requests and pickers.** :161, :624, :951, `RivalGuiApi.java:168` per 2.3.
Size S. Owner: no.

### 5.3 Sparring

**S1: Sparring Home leads to the board.**
- Change: in `UltraSparScreens.openHome` :166-191, the first line block becomes a live board summary:
  - "Sparring" header;
  - "N players are looking for a spar right now." or "Nobody is posting right now.";
  - "Your post: friendly, no wager - 12:40 left" or "You have no post up.";
  - the rewritten worth/PU lines (3.3).
- First button (confirm style): "Open the Spar Board". Then [Training weights shop] [Spar TP messages].
  Staff Admin stays staff-only.
- Effect: the main action is one tap from Home. No tab change.
- Size S. Owner: no.

**S1-alt: Swap the tabs so Home is the board.**
- Change: Sparring tabs become Home (the board) | Leaderboard | More (weights and TP messages).
- Effect: posting and accepting are on the first screen.
- Size M (tab ids, registry, hub links `go:spar|sparboard`, probe). **Owner: yes (tab change).** Offer it
  only if S1 is not enough.

**S2: Board pager, a separate compose page, and sorting.**
- Board (`UltraDojoScreens.openBoard` :160-284) keeps:
  - "Your post" (1 line);
  - the ranked progress (1 line);
  - "On the board (N) - page 1 of 2";
  - **4 posts a page**, each on one line (rewrite in 3.3, Friendly Fist dropped), with Accept buttons
    two per row *in the same order* as the lines;
  - the pager row (reuse `pager(spec, TAB_BOARD, pg, pages)` :1198);
  - then [Post a spar / Take my post down] [Challenge a player] / [Watch spars] [Last fight] (Last fight
    is dim "Last fight - none yet" instead of hidden).
- `BOARD_SHOWN` 5 becomes 4 per page. Paging means `open()` :100 must accept `sparboard:N` (today it checks
  `equals`) and `openBoard` takes a page index. After a board action, `handle` :1231 reopens page 0.
- New compose page `board_post`: Kind / Wager / Range / Looking for (each says "tap to change"), the PU
  line, and "Post for 15 min" (confirm). The settings rows move here from the board (:230-246).
- Sort, with no new filter button: posts that challenge you, then posts you can accept, then dim ones
  (`whyNot != null`). Inside each group, posts whose "Looking for" matches yours come first.
- Effect: the board is ~280 px instead of 352. Every post is reachable. The list and its buttons line up.
- Size M. Owner: no. (A real want/level filter button would be an extra toggle; offered as optional,
  owner pick.)

**S3: Ranked lock reads like the other toggles.** :231-233: dim "Kind - Friendly" + the line "Ranked
unlocks after 30 friendly spars: 12 to go." Size S. Owner: no.

**S4: Copy fixes on the board, challenge and watch pages** (3.3: :184-185, :207-216, :222-227, :450-452,
:487). Size S. Owner: no.

**S5: Rename the spar settings and explain them.** `openSettings` (UltraSparScreens.java:655-675) per 3.3.
Weights input footer: "Back to Training weights" + "Main menu" (`UltraWeightShopScreens.openInput`).
Size S. Owner: no.

**S6: Leaderboard tab shows content, not a menu.**
- Change: `openLeaderboard` :194-203 shows the top 5 by TP directly ("Hall of Infamy - top 5 by TP"),
  then [More rankings] (the existing top_* pages) and [Your stats].
- Size S. Owner: no.

### 5.4 Dojo

**D1: Dojo Home de-clutter (460 to about 280 px, 9 to 5 actions).**
- `UltraDojoScreens.openDojo` :518-586 and `DojoFounding.identityButtons` :312-344.
- Lines: the 6-line block from 4.3, passed as the `text` of `hallPlan(...)`, so the plan sits *beside*
  the stats and the 6 padding rows go away.
- Buttons:

  | Left | Right |
  |---|---|
  | **Spar Board** (confirm style; opens `go:spar\|sparboard`) | **Members** (new merged page, D2) |
  | **Training bonds** | **Visit dojo** / Leave dojo |
  | **Dojo settings** (master: banner, rename, close) | |

  "Dojo leaderboard" leaves Home; it stays on Wars and on the no-dojo Home. The free "Choose your dojo's
  banner" stays on Home, confirm style, as long as there is no banner. It replaces Dojo settings in that
  row until the banner is picked.
- New sub-page `dojo_settings`: Rename - 20 points, Change banner - 20 points, then a gap and Close dojo
  (danger).
- Effect: 5 actions (within the six-action rule), ~280 px, no shrink at 854x480. The two remaining
  buttons each lead to one thing.
- Size M. Owner: no (no tab change; Close moves one level down, still before its confirm page).

**D2: One "Members" page** (replaces "Roster and bonds" + "Members and elites").
- Merge `UltraSparScreens.openDojo(... "dojo_roster")` :316-355 and `UltraDojoScreens.openElites`
  :929-970 into `dojo_members`.
- Lines: "Members 2 of 26", one line on what elites do (corrected copy, 4.3 :942), "An elite needs 3 days
  in the dojo first."
- One row per disciple, **6 a page with a pager**: [Name (elite)] [Make elite / Step down] for the master;
  dim name rows for others. War rights move to a small per-elite ask page (one tap on the elite's name).
- [Previous members] stays as one button.
- Retire `dojo_roster` as an alias that opens `dojo_members`; keep the registry item so old links work.
  "Your mentor's dojo" toggle (:389-397): keep it only when the player is both a master and someone's
  apprentice; otherwise one view.
- Effect: one door instead of three. The elites page can no longer grow to 29 rows.
- Size M. Owner: no.

**D3: "Ask to join" from the dojo list (audit #5).**
- **Existing action reused:** `apprentice_invite` (`gui/SparGuiApi.java:648`, then
  `SparringSystem.apprenticeInvite` :1761). Passing `uuid:<masterUuid>` resolves through
  `SparGuiApi.resolveOnline` :773, which already accepts `uuid:`. The master gets the normal Bond invite
  (1 hour, locked in the checklist) and accepts on Training bonds > Bond invites. So **for an online master
  this is presentation only**: a new button wired to an existing action.
- List change (`openList` :1151-1196): rows show the master's name and "(online)" (4.3). The Visit-only
  buttons become **dojo name buttons** (list rows, 2 per row, 6 a page with the existing pager). They open
  a new **dojo profile page** `dojo_view:<master>`:
  - name, master (online or offline), style, members, clout, war record;
  - buttons: "Ask to join" (confirm style, through the existing `pick_confirm` ask page: "Ask <master> to
    take you as an apprentice? Their dojo becomes yours. Leaving later starts a 12-hour wait."), "Visit
    dojo", and for a master or elite of another dojo "Declare war" (opens the existing `dojo_war_fmt:<id>`).
- Dim states with the reason on the button:

  | Case | Button |
  |---|---|
  | Master is offline | "Ask to join - master offline" |
  | You're already in a dojo | "Ask to join - you're in a dojo" |
  | Dojo is full | "Ask to join - dojo full" |
  | You have a master | "Ask to join - leave your master first" |
  | Cooldown | "Ask to join - in 3h 20m" |

- **New behavior if wanted:** asking an *offline* master (store the invite, tell them on login), and keeping
  more than one pending ask per master (`invites` is keyed by target, so a new ask overwrites the old one).
  Both are owner picks, **G2**.
- The no-dojo Spar Board's "Find a dojo" and the no-dojo Home's "Browse dojos" then lead to a list you can
  join from. The war pick page (`openWarPick` :1082) can become this same list with "Declare war" on the
  profile, which removes one near-duplicate page.
- Effect: joining becomes Dojo > Browse dojos > dojo > Ask to join > Ask, which is 5 taps with the master
  named and their online state shown. Today it is 7 taps plus guessing the master.
- Size M. Owner: no for the online case; **yes** for offline and multi-ask.

**D4: Upgrades tab and upgrade page fit.**
- `openUpgrades` :609-652: text column "Dojo Points 1930" + 2 short lines (4.3). Labels "<name> - N
  points". "Style and bonus" moves into the 5th row next to "Disciple capacity" (pairs 9 buttons as
  4 + 1, so the last row has [Style and bonus] alone but there is no empty 8th text line).
- `openUpgrade` :686-759: on LM picture clients, drop the duplicate text map (:729-730) and put Now /
  After / refit note / Cost / "spent at once" in the picture's text column. `PICTURE_ROWS` drops from 10 to
  7 for the picture block. KaiPath keeps the text map.
- Effect: 392 to about 250 px and 330 to about 290 px.
- Size S. Owner: no.

**D5: Style and bonus split and total cost (audit #11).**
- `openStyle` :761-852 shows the style and bonus lines, then **the bonus list only** (7 buttons, under a
  "Bonuses for Melee" header line), then a "Change style - 40 points" button that opens a new
  `dojo_style_pick` page with the 3 other styles. During the bonus cooldown, the bonus buttons read
  "Human tradition - in 6 days" (dim).
- `openStyleAsk` :855-896: replace :875-880 with:

  | Before | After |
  |---|---|
  | "This change costs 40 Dojo Points (you have 1385)" | "Changing style costs 40 Dojo Points." |
  | "A new bonus costs 40 Dojo Points" | "Your Majin tradition bonus ends now (its 40 points are not refunded)." |
  | "You can pick the new bonus right away." | "A new bonus costs 40 more, and you can buy it right away." |
  | (none) | **"Total to switch and get a bonus back: 80 Dojo Points (you have 1385)."** |
  | (none, unchanged) | "After this, the name, style and banner are locked for 12 hours." |

  The button text "Change style, end the bonus" becomes "Change style for 40 points".
- Fix the 8-days off-by-one at :780 and `DojoService.java:564`. Use `(left + DAY_MS - 1) / DAY_MS` and
  print hours under a day, or reuse `DojoService.cooldownText`.
- Effect: 372 to about 260 px. The cost is shown before the click as one number. Points are not retuned.
- Size S. Owner: no.

**D6: War sign-up shown on the menu, with a countdown (audit #7).**
- Presentation only. Needs a read helper `DojoWar.signupLeftMs(warId)` plus signed-up counts per dojo,
  from `Run.signupUntil` (:266) and `Run.signed`.
- Wars tab (`openWars` :974-1053): while a war is in SIGNUP, put a "War starting!" card **at the top**:
  - "A vs B - bracket"
  - "Sign-up closes in 0:42. Signed up: 3 from your dojo, 2 from theirs."
  - "With one fighter each it becomes a single bout. Nobody signed up and it's called off."
  - Buttons: [Join the war] (confirm) [Refresh].

  After sign-up the card reads "In progress" with [View the bracket].
- Dojo Home: when your dojo has a war in SIGNUP, a notice line at the top ("Your dojo's war vs B is
  starting: 0:42 left to join on the Wars tab") and the Spar Board button's slot shows "Join the war"
  (confirm) instead.
- Chat line `DojoWar.java:273-274` per 4.3.
- The menu does not tick by itself. The countdown is right when the page opens, and Refresh re-reads it.
- **Changing `SIGNUP_MS = 60_000L` (:41) is a behavior change: open pick (e).**
- Size S. Owner: no for the presentation, yes for the duration.

**D7: War pages.**
- Past wars get a "View" button: one row per past war, 4 shown, named in the same order as the lines. It
  opens `dojo_war_view:<id>`, which already works for DONE wars (`bracketLines` adds the Winner line).
- `openWarView` :1130-1144 caps the bracket at the current round plus the last 4 results.
- No-dojo Wars (:979) gets Browse dojos / Found a dojo.
- Size S. Owner: no.

**D8: Training bonds and pickers.**
- `openMentor` (UltraSparScreens.java:402-424): dim states (X6); copy per 4.3; the cap from
  `apprenticeCap`, not `MAX_APPRENTICES` (SparGuiApi.java:305, :322).
- "Request master" picker lists **dojo masters first**, with their dojo names ("Name - X's Dojo"), then
  other players.
- `NAMES_PER_PAGE` 20 becomes 8 (:429), and remove its "the page scrolls" comment.
- Pending: one line instead of three (4.3).
- Effect: the "1/8" contradiction is gone. A player asking for a master can see who runs a dojo. The
  pickers stay under budget.
- Size S. Owner: no.

**D9: Founding copy and the banner step (audit #9).**
- The copy says any banner works: `UltraDojoScreens.java:533`, `DojoFounding.java:279` per 4.3.
- No-dojo Home adds, when the player carries no banner: "You don't have a banner yet. You'll need one at
  step 3."
- Step 3 with no banner gets two dim/live buttons: "Back to step 2" and "I have one now - check again"
  (reopens step 3). The draft is already saved server-side.
- The banner picker keeps `BANNERS_SHOWN = 6` (:52).
- Effect: the founding friction looks as small as it really is. The requirement itself is pick (d).
- Size S. Owner: no.

**D10: Show how points are earned (audit #10, display only).**
- Upgrades tab: a 2-line "How to earn" text with today's real numbers from `DojoCatalog`:
  - "Host a spar +2 (20 a day) - Train a disciple +3 (15 a day)"
  - "War bout +5 - War win +25"
- Every "needs N more points" line (:747) adds "That's about K hosted spars." (from `Earn.HOST`).
- No number changes. Size S. Owner: no.

**D11: Honest copy for the cosmetic rooms (audit #17).**
- `DojoCatalog.upgradeWhat` :55-59: Trophy hall per 4.3. Meditation room and Master quarters stay as they
  are (they don't promise a mechanic).
- Size S. **Owner: yes** if they would rather build the trophy mechanic than reword (pick j).

**D12: Elite copy (pick F).** Until the owner decides, `:942` and `DojoService.java:634` say "can buy
upgrades and declare wars" (drop "invite members"). Size S. Owner: copy no, wiring yes.

**D13: Remove dead routes (audit #15).**
- Delete `DojoService.found(ServerPlayer)` :298 (no callers).
- Delete the `pick_dojo_challenge` registry items (PageRegistry.java:190-191).
- Keep the router fallback at UltraSparScreens.java:44 so old links still land on Wars.
- Size S. Owner: no.

**D14: First-login nudge for migrated or incomplete dojos (audit #4).**
- When a master's dojo has no style or no banner, Dojo Home shows a notice line on top ("Your dojo needs a
  style and a banner. Both are free the first time.").
- The Home button row shows "Choose a style (free)" / "Choose your dojo's banner (free)" in confirm style.
- Presentation only. A chat nudge on login would touch event code (optional).
- Size S. Owner: no.

**D15: Small copy fixes.** "1 members" becomes "1 member" (:1167). "1 sparring mat" becomes "1 ring"
(:665). Empty-state lines per 4.3. Error messages per 4.3 (SparringSystem.java:1736, :1742, :1772;
SparGuiApi.java:644, :654). Size S. Owner: no.

### 5.5 The click-target pages, one by one (audit #18; no scrolling)

| Page | Today | Fix | Target |
|---|---|---|---|
| dojo_home | 460-486 px, 9 actions | D1 | ~280 px |
| dojo_upgrades | 330 px | D4 | ~290 px |
| dojo_upgrade_arena (every upgrade page) | 392 px | D4 | ~250 px |
| dojo_style | 372 px, 10 mixed buttons | D5 | ~260 px |
| dojo_elites | unbounded (1 row per disciple) | D2 (6 a page) | ~260 px |
| dojo_wars | 273 px, grows with open wars | D6/D7 (cap open-war rows at 3, past at 4) | ≤ 300 px |
| dojo_war_view | unbounded bracket lines | D7 (current round + last 4) | ≤ 280 px |
| dojo_list | 6 rows of Visit pairs + sort + pager | D3 (name buttons 2 per row) | ~260 px |
| dojo_mentor | 254 px | D8 (drop 2 lines) | ~230 px |
| dojo_pending, dojo_pick_apprentice | 20 names a page | D8 (8 a page) | ~240 px |
| dojo_banner, dojo_found_banner | up to 6 wide rows + 4-5 lines | D9 (drop the explainer line; keep 6) | ~290 px |
| spar_board | 352 px | S2 | ~280 px |
| spar_stats | stats lines + 3 report buttons | trim `statsOverviewLines` to 4 lines | ~240 px |
| spar_top_tp | heading twice + list + 5 buttons | drop the duplicate heading (layout-findings:128), top 8 | ~260 px |
| rival_records_stats | 393 px | R2 | ~230 px |
| rival_actions / challenge / requests / pick_rival / list_detail | 4.7.81 already paged | check each against the 300 px audit (X5) after R1-R5 | ≤ 300 px |

---

## 6. Open picks for the owner (not decided here)

**(a) The old `/spar dojo challenge` war path.**
- **Retire it.** Delete the command branches (`command/SparCommands.java` accept/decline/challenge) and the
  `SparGuiApi.handleDo` "dojo"/"dojo_challenge"/"dojo_accept" actions (:658-700). The 2x-RP window goes
  away. Live has 0 old challenges, so there is nothing to migrate.
  - Pro: one meaning of "war".
  - Con: anyone who learned the command loses it. P2-2 must then read only `DojoStore`.
- **Rewire it to `DojoWar.apply`.** `/spar dojo challenge <master>` calls `war_declare:<master>:BRACKET`,
  and accept/decline call `war_accept`/`war_decline` on the newest CHALLENGED war.
  - Pro: commands keep working and mean the same as the menu.
  - Con: a little more code to keep. The 2x-RP window still dies unless kept as below.
- **Keep the 2x-RP window as a war perk.** While a `DojoStore` war is RUNNING (or for 24 h after it
  ends), spars between the two dojos earn 2x ranking points.
  - Pro: the RP idea survives inside the new system.
  - Con: new behavior, and its number needs owner sign-off.

**(b) A personal perk for disciples (audit #8).** No new currency. Options:
- A **contribution line** on the Members page and the player's own Dojo Home: "You've earned your dojo 34
  points this season". This is display only from existing `earn()` events, if those are logged per player
  (needs a per-player counter).
- A **dojo title** shown in chat or on the profile ("Disciple of X's Dojo").
- A **share of host points** paid as personal TP. This is new economy and touches the "no retuning" rule.

Trade-off: the first two are presentation, cheap and safe. The third is the only one with real value,
and it changes rewards.

**(c) Help a new dojo start (audit #10).**
- A founding grant of 10-20 Dojo Points: one line in `found`.
  - Pro: the first room (30) is reachable on day 1.
  - Con: farmable by close and found? (Reopen exists, so a grant must be first-found only.)
- A first-room discount (first upgrade at half cost).
  - Pro: no free points.
  - Con: a special case in `cost`.
- Uncapped HOST points for the first N fights.
  - Pro: rewards real activity.
  - Con: needs strangers to accept posts; doesn't fix a quiet hour.
- Do nothing; show the earn rates (D10).

Each of the first three is a Dojo Points number change, so it needs the owner.

**(d) The banner requirement for founding (owner's 4.7.78 decision).**
- Keep it, and fix the copy (D9): any plain banner works, so the friction is one crafted banner.
- Make the banner optional at founding. The dojo gets a default banner in its style colors, and "Choose
  your dojo's banner (free)" stays on Home until picked (D14 already shows it).
  - Pro: founding drops to 3 taps after the name.
  - Con: halls without a custom banner, and it reverses the 4.7.78 decision.

**(e) War sign-up length (`DojoWar.SIGNUP_MS`, 60 s).**
- Keep 60 s and rely on D6.
- 3 or 5 minutes. Offline and AFK members get a chance; the war ring is held longer.
- Variable: until both dojos have 2+ fighters or 5 minutes pass.
- Separately: warn or block a 1v1 war (D6 warns; blocking is behavior).

**(f) Elites "invite members".**
- Reword it (D12): elites can't invite.
- Wire it: an elite's Recruit sends the invite on the master's behalf (the bond is made with the master).
  This is new behavior in `SparringSystem.mentorInvite`.

**(g) Navigation-model and tab picks raised by this walk.**
1. **Sparring tabs.** S1 (the board summary and button on Home, no tab change) vs S1-alt (Home = board).
2. **Ask to join an offline master.** Store the invite and deliver it at login. Also allow more than one
   pending ask per master (today a new ask overwrites the last). Both are new behavior.
3. **Rival tab label.** "Rival" to "My rivals" (R4).
4. **Back model.** Registry parent on sub-pages vs history everywhere (X3).

**(h) Points wagers for masters and elites only (audit #12).** Keep it, as the treasury guard (copy
already says so). Or allow regular members a points wager from a personal pool, which would be a new
currency and is out of the rules unless the owner says otherwise.

**(i) Dojo names: allow non-ASCII (Cyrillic) (audit #16).** Widen `nameProblem`'s regex. Check that the
hall's sign and banner rendering take it.

**(j) The cosmetic rooms (audit #17).** Reword (D11). Or give the trophy hall a real job: list the dojo's
past wars from `WarRecord` on pedestal signs. That is new behavior.

**(E) Forfeit and ranking.** With P2-1, a forfeit pays the wager but still gives no ranked credit or host
points, so the winner's "You won - forfeit" counts for money but not for ranking. The options are to keep
that (anti-farm: two friends trading forfeits), or count a forfeit as a ranked win only after the
1-minute/damage gate.

---

## 7. Top changes by player impact (for the summary)

1. **D3:** "Ask to join" on a dojo profile opened from the list. Masters are named and shown online or
   offline. Reuses `apprentice_invite`.
2. **D1 + D2:** Dojo Home goes from 9 to 5 actions and from 460 to about 280 px, with the floor plan beside
   the stats. One Members page replaces three doors, and Spar Board is one tap from the dojo.
3. **S2:** Spar Board with 4 posts a page and a pager, a separate "Post a spar" page, and posts for you
   first.
4. **P2-1 + P2-1c:** wager paid on forfeit and timeout, with every refund line rewritten.
5. **S1:** Sparring Home leads to the board ("N players looking for a spar", "Open the Spar Board").
6. **D6:** war sign-up card with a countdown and signed-up counts on Wars, plus a Dojo Home notice.
7. **Copy that misstates, fixed:** apprentice cap 8 vs 5-29, capacity +5 vs +4, bonus 8 vs 7 days, rival
   TP +100% vs +0%, "made at a loom", elites "invite members", "Pedestals that show off your war wins".
8. **D5:** style change shows the total "80 Dojo Points" before the click, and styles and bonuses are split.
9. **X1 + X2:** tabs stop piling up in Back, and there are no more cut back labels ("Back to Wager
   Ancient").
10. **X5 + 5.5:** a 300 px height audit, with the dojo, spar and rival pages that drop under 12 px
    de-cluttered to fit (no scrolling).
