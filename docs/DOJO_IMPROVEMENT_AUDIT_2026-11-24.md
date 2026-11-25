# Dojo system improvement audit — LM 4.7.82 (origin/main `6cf5d6db`)

Read-only source audit + live adoption check, 2026-11-24 ~19:30 EST. Baseline: origin/main tip `6cf5d6db`
(LM 4.7.82). Note: a 4.7.83 branch (`lm-4783-selftest-label`) exists unmerged; it is a boot
self-test/label fix only and does not touch dojo code, so the baseline stands.

Live state verified from the panel the same evening (byte-exact pull of
`config/legacymechanics/sparring.json`, 655,572 B, matching the panel listing):
- **No `config/legacymechanics/dojos.json` exists on live** — the new dojo system (4.7.68+) has
  never run on live; live is LM 4.7.67.
- Old system: dojo season 2 (started 2026-11-18 14:51 UTC), 7 dojos on the season RP
  leaderboard (xcsy leading, 132 RP, 12 sessions), **zero dojo challenges** (`dojoChallenges`: 0),
  1 dojo profile (the user's own), season 1 Hall of Fame: Zendrace champion, 3324 RP.
  All leaderboard W/L are 0 — no war has ever been fought.
- **40 mentor bonds have apprentices** — on first 4.7.68+ load, `migrate()` auto-creates 40 dojos.

Paths in anchors are under
`tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/`.

## P0 — correctness: two live war systems, and ki sense is dead for menu wars

### 1. The old war system was never retired — two "declare war" paths, two meanings
**Verified from source.** The concept said "the old challenge flow goes away". It did not:
- NEW (menus): Ultra Dojo Wars page → `DojoWar.apply` (`war_declare`/`war_accept`/`war_signoff`/
  `war_join`/`war_cancel`) → `DojoStore.WarRecord` with bracket, score, fights, prune
  (`dojo/DojoWar.java:152` declare, `:132` prune). Declared from the menu via pick page
  (`gui/ultra/UltraDojoScreens.java:1045` "Declare a war") → format page → `war_declare`.
- OLD (commands + legacy GUI): `/spar dojo challenge|accept|decline`
  (`command/SparCommands.java:283` accept path) and `SparGuiApi.handleDo` "dojo"/"dojo_challenge"/
  "dojo_accept" actions (`gui/SparGuiApi.java:661,700`) → `SparringSystem.dojoChallenge` →
  `DojoRankings.challengeDojoByMasterKey` (`sparring/DojoRankings.java:1213`) → writes
  `SparStore.DojoChallenge` (from/to/mentor/expiry/active only — no winner, score, or fights).
  Accepting (`DojoRankings.java:1280` `acceptChallenge`) just flips `active=true` and broadcasts
  "spars vs that dojo earn 2x ranking points" — it is a 24h RP window, not a tournament.
- No cross-check either way: `DojoWar.declare` never looks at `SparStore.dojoChallenges` and
  `challengeDojoByMasterKey` never looks at `DojoStore.wars`. One pair of dojos can hold both
  wars at once. Permissions differ too (new: master OR elite; old: master with apprentices only).
- One partial fix landed in the old path: `pruneExpiredChallenges()` runs before the duplicate
  check, so the concept's "never pruned, can never re-challenge" defect is softened — but the
  old record still carries no result.
**Fix:** retire the old command/GUI paths (or rewire them to `DojoWar.apply`) and decide what the
old 2x-RP war becomes. Live has 0 old challenges, so nothing to migrate. **Medium build**
(delete/rewire + menu copy), needs a user decision on whether the RP-war idea survives at all.

### 2. Ki sense does NOT work during a menu-declared war (reverses the 4.7.77 correction)
**Verified from source.** `DojoWarSense.withWarFoes` → `DojoRankings.onlineWarOpponents`
(`sparring/DojoRankings.java:497`) → `activeChallengeFor` (`:1165`), which reads ONLY
`SparStore.dojoChallenges`. The new war system NEVER writes that map (zero hits for
`dojoChallenges`/`DojoChallenge` in `dojo/DojoWar.java`, `dojo/DojoService.java`,
`dojo/DojoStore.java`). So during a war declared from the menu — the only path the menus offer —
`onlineWarOpponents` returns empty and war opponents never appear on ki sense.
The 4.7.77 audit correction ("the live war flow DOES write `SparStore.dojoChallenges`") is true
only for the OLD command path; for the new menu war flow the original 4.7.68 finding was right
in substance. (`homeDojoKey`, `DojoRankings.java:45`, resolves via the mentor bond, so it would
work for new dojos once the record exists — the break is purely the missing write.)
**Fix:** teach `onlineWarOpponents` to also read running `DojoStore.WarRecord`s (or have
`DojoWar.declare`/`accept` mirror a challenge record). **Small tweak.**

### 3. Wagers can be voided by forfeiting or stalling to the clock — and the menu misstates it
**Verified from source.** `BoardSpar.settle` (`dojo/BoardSpar.java:686`): the wager is paid to a
winner only when `decisive && winner != null && !"TIMEOUT".equals(how)`.
- FORFEIT (`:505`): not decisive → `counted=false`, wager refunded. The loser avoids paying,
  and the opponent gets NO ranked credit, no gate progress, no host points — nothing.
- TIMEOUT with a winner (more damage): ranked credit IS awarded (winner gets the ranked win),
  but the wager is STILL refunded. One fight, two different winners: ranking says someone won,
  the wager says nobody did.
- DISCONNECT: same refund.
So a wagered spar can only actually be lost by KO or ring-out. A losing fighter forfeits, or
stalls to the 5-minute clock, and keeps their stake.
- The menu discloses the refund ("A forfeit or the clock running out refunds both",
  wager page) but the in-fight screen overstates it: "Forfeiting gives the win away. Any wager
  is refunded to both." (`gui/ultra/UltraDojoScreens.java:174`) — no win is given away; the
  result screen says "You won - forfeit" while nothing counts.
- The concept promised "winner takes both stakes" with no carve-outs.
**Fix:** pay the winner on forfeit, and on timeout when a winner is decided; keep refund only for
disconnect/crash. Fix the "gives the win away" copy to match. **Small tweak** (settleWager payTo
logic + two copy lines). Needs a user decision only if the refund-on-forfeit was intentional.

## P1 — adoption: why would a player found or join a dojo?

### 4. Deploy shock: 40 dojos auto-create on the first 4.7.68+ boot
**Verified from source + live data.** `DojoService.migrate()` (`dojo/DojoService.java:132`,
called from `event/DifficultyEvents.java:151`) creates a dojo for every mentor with apprentices
— 40 on live — each starting style-less, banner-less, 0 points, named "<name>'s Dojo". Each
founding queues a hall build through the single per-tick builder (`dojo/DojoPlacer.java`); 40
builds back-to-back on first boot, each a ~670k-cell base hall. Every auto-dojo's master then
has to pick a style and a banner (first picks are free, but nothing tells them to).
**Fix:** a deploy checklist item, not code: expect a build storm on first boot; consider a
first-login nudge ("Your dojo needs a style and a banner") for migrated masters. **Small**
(nudge) or zero (just plan the boot).

### 5. No way to join a dojo from the dojo list
**Verified from source.** The leaderboard (`gui/ultra/UltraDojoScreens.java:1146` `openList`)
has only "Visit <name>" buttons. Joining a dojo means becoming an apprentice through the
Training-bonds invite flow — the player has to find the master online and use a different menu.
A player browsing dojos cannot ask to join from the list. The board's no-dojo state links to
the list and to the Dojo section, but neither offers a join request.
**Fix:** "Ask to join" on each list row → mentor invite to that master. **Small tweak.**

### 6. The board shows 5 posts, no pager, no filter
**Verified from source.** `BOARD_SHOWN = 5` (`gui/ultra/UltraDojoScreens.java:27`); `openBoard`
shows `min(5, posts.size())` with no pager. On a busy server, older posts are invisible from the
menu. The concept's fix #1 was "finding a fight stops being chat spam" — a 5-post board with no
paging or want/level filter undercuts it (level range is enforced at accept, not shown as a
filter; want IS shown per listing, which is good).
**Fix:** pager on the board (same `pager()` helper the list uses) and ideally a want filter.
**Small tweak.**

### 7. War sign-up is 60 seconds, announced once by chat
**Verified from source.** `SIGNUP_MS = 60_000L` (`dojo/DojoWar.java:41`). On accept, members of
both dojos get one chat tell; anyone offline, mid-fight, or AFK misses it, and sign-up never
reopens. A war can run 1v1 (one fighter per dojo) — a degenerate bracket the concept never
intended ("each dojo fields its fighters"). There is no minimum-signup check at draw time beyond
one per dojo (`DojoWar.java` draw/step).
**Fix:** longer sign-up (e.g. 5 min) and/or a persistent "war starting — join" notice on the Wars
tab; consider requiring 2+ fighters per dojo or warning when a war will be 1v1. **Small tweak.**

### 8. Disciples get almost nothing personal from the dojo
**Verified from source.** Everything the dojo earns accrues to the dojo: HOST/TRAINING/MILESTONE/
WAR points go to the dojo balance (`dojo/DojoService.java` `earn()`), clout is dojo-level, war
wins are dojo-level. The only member-level benefit is the style bonus (+5% focus / +8% race
tradition on spar TP, `dojo/DojoCatalog.java:173` `BONUS_COST`, applied in
`sparring/SparCombat.java:679`). There is no personal dojo standing, title, cosmetic, or
contribution record for a disciple — nothing that makes *their* profile better for being in one.
**Fix:** a design decision, not a bug — e.g. disciple-visible contribution, a dojo title, or a
share of host points. **Medium/new build**; needs the user to pick the perk.

### 9. Founding requires a loom banner first — the biggest founding friction
**Verified from source, by design.** `DojoService.found` refuses without a banner
("Pick a banner you made at a loom first"), and the flow dead-ends with "Make one at a loom,
then come back" (`gui/ultra/DojoFounding.java` `bannerPicker`). A new player must craft a banner
(wool + dye + loom) before founding anything. The 3-step flow itself is well built (name →
style → banner → confirm, server-side draft, free first picks). This was the user's 4.7.78
decision, so it is a friction to revisit, not a bug.
**Fix (if wanted):** allow founding with a default banner, pick the real one later free.
**Small tweak.**

## P2 — economy: reachable, but slow and hosting-dependent

### 10. The points math (verified)
Costs (`dojo/DojoCatalog.java`): arenas 40×tier (40/80/120/160/200), rooms 30/60/120,
capacity 25/50/75/100/125. Full build ≈ 2,235 points. Earn: HOST 2/fight (cap 20/day),
TRAINING 3/session (cap 15/day), MILESTONE 5 (cap 25/day), WAR_BOUT 5 (cap 40), WAR_WIN 25
(cap 50). No founding grant — a new dojo starts at 0.
- Cheapest room (30): 15 hosted fights, i.e. 2 days at the 10-fight daily cap — if other
  players accept your listings. A solo master with no disciples earns ONLY via hosting.
- Steady state without wars: 35/day → full build ≈ 64 days; with milestones flowing ≈ 37 days.
- A new dojo's only income depends on strangers accepting its board posts — chicken-and-egg
  for the first dojo in a quiet hour.
**Fix options (all inside Dojo Points only):** a small founding grant (e.g. 10–20 pts), a
first-room discount, or uncapped HOST points for a dojo's first N fights. **Small tweak**;
needs a user pick.

### 11. Changing style costs 40 + 40, never shown as one total
**Verified from source.** `setStyle` clears the active bonus (`dojo/DojoService.java`), and
`pickBonus` charges `BONUS_COST` 40 again with a 7-day cooldown. Switching styles with a bonus
costs 80 points, but the style page only says "Change style, end the bonus" — the combined cost
is never stated.
**Fix:** show the combined cost on the style-confirm page, or waive the bonus re-pick when the
style change forced it. **Small tweak.**

### 12. Points wagers are master/elite-only — not in the concept
**Verified from source.** `canStake` (`dojo/BoardSpar.java`) returns "Only a master or an elite
can wager Dojo Points" for any non-manager. Disclosed on the wager page, but the concept said
wagers are "set by the poster and matched by the accepter" with no such restriction — so most
players effectively have no points wagers. Probably correct (treasury protection), but it was
never decided.
**Fix:** confirm the restriction is intended, or allow a personal-points wager. **Decision.**

## P3 — robustness and cleanup

### 13. A failed hall build sticks until restart
**Verified from source, needs an in-game check to trigger.** `DojoWorld.ensureBuilt`
(`dojo/DojoWorld.java:314`) puts the signature in `PENDING`; only the build's done-callback
(`:320`) removes it. If `DojoPlacer.work()` throws, the catch logs and releases — but `PENDING`
stays set, `builtSignature` never updates, and `ensureBuilt` early-returns forever on
`sig.equals(pending)`. The dojo then reads "being built" for visits and spars until a restart
(`PENDING` is in-memory). A missing structure file alone does NOT trigger this (it is skipped,
not thrown), so the trigger needs a real placement exception.
**Fix:** clear `PENDING` on build failure and log loudly. **Small tweak.**

### 14. Pair cooldowns are in-memory only
**Verified from source.** `BoardSpar.PAIR` (`dojo/BoardSpar.java:111`) is a plain
`ConcurrentHashMap`, never persisted. A restart resets the 15-minute ranked-credit/host-point
cooldown, so a pair can re-earn immediately after a reboot.
**Fix:** persist the map in `DojoStore` or accept the gap. **Small tweak.**

### 15. Dead code: one-tap `found(p)`, dead `pick_dojo_challenge` page
**Verified from source.** The pre-4.7.78 one-tap `found(ServerPlayer)` (`dojo/DojoService.java:298`)
has no callers (the menu uses the flow); it would found an auto-named, style-less, banner-less
dojo. `pick_dojo_challenge` has a registry entry and a router fallback
(`gui/ultra/UltraSparScreens.java:44`) but no menu opener. **Fix:** delete both. **Trivial.**

### 16. Dojo names block non-ASCII
**Verified from source.** `nameProblem` (`dojo/DojoService.java:179`) allows only
`[A-Za-z0-9 '&.-]` — no Cyrillic, though some players chat in Russian. **Fix:** widen the regex
if wanted. **Trivial.**

### 17. Meditation room, master quarters, trophy hall are cosmetic
**Verified from source / needs in-game check for the trophy case.** All three rooms exist as
structures, but no mechanic ties to them: the meditation room has no training bonus (the concept
said "style training bonuses apply here" — the style bonus is spar-TP-only and location-free);
the quarters have no elite-desk mechanic; the trophy hall is a static room — nothing observed
in code writes war wins, plaques, or season banners into it (the "Hall of Chosen" lives in the
OLD `sparring.json` system). **Fix:** decide whether any of the three gets a mechanic (e.g.
trophy hall lists the dojo's past wars from `WarRecord`). **Small–medium.**

### 18. GuiKit leftovers
The 4.7.81 pass fixed the dojo label cuts I could re-verify (wars short labels + "Now" block,
watch full-width rows, banner full-width rows, wager "1 Emerald coin", list Visit pairing).
Remaining, from `tools/lm-gui-corpus/findings/layout-findings.md`:
- 46 pages — including `dojo_home`, `dojo_list`, `dojo_wars`, `dojo_war_view`, `dojo_banner`,
  `dojo_elites`, `dojo_style`, `dojo_upgrades`, `dojo_upgrade_arena`, `dojo_pending`,
  `dojo_pick_apprentice`, `dojo_mentor`, `spar_board`, `spar_stats` — have click targets under
  12px at some window sizes, because `NativeMenuFit` shrinks tall menus to 0.5x before scrolling.
  Known 4.7.81 limitation; the user chose shrink-to-fit. No fix proposed here.
- Heading fixes (`dojo_roster` "Training bonds", `dojo_members_own` "Your dojo",
  `dojo_pick_apprentice` "Invite apprentice" as `UltraStyle.header`) — **not re-verified**;
  needs an in-game/menu check.

## What was verified working (so the report is not only gaps)
- PU opt-in end-to-end: `Want.POTENTIAL` → `Bout.potentialA/B` → `rt.boardPotential` →
  `trainsPotentialInSession` (`sparring/SparringSystem.java:557`) → `PotentialProgression.java:66,68`;
  ranked spars never train it. Matches the concept exactly.
- Escrow: coins charged from both, poster refunded if the accepter cannot pay, same-dojo
  double-stake guard, escrow persisted in `DojoStore`, crash refunds via `refundStaleEscrow`,
  offline winnings paid as OWED on login. Persistence runs on a pulse
  (`event/DifficultyEvents.java:785`) plus shutdown save.
- Style bonus actually applies (`sparring/SparCombat.java:679`, +5%/+8%).
- Board spars are non-lethal regardless of Friendly Fist (`sparring/SparringSystem.java:370-376`,
  KO cut + death backstop) — the FF on/off shown per listing is display only for lethality.
- Season rollover wired: `SEASON_MS` 75 days (`sparring/DojoRankings.java:28`), `ensureSeason`
  called from `event/DifficultyEvents.java:145` and `gui/SparGuiApi.java:116`; clout to history,
  placement clout {30,20,10}, rooms untouched.
- War record + prune: winner/score/fights kept, expired challenges → EXPIRED, history capped at 60.
- Reopen −1 tier, elites + war-rights sign-off, balcony-gated spectating, capacity 5→30 — all
  present as built.
- Physical build: 375 `.nbt` structures in the jar (t1/t2/t3 × 4 styles × rooms/arenas), per-tick
  build budget, chunk tickets, post-build safety checks (`DojoWorld.afterBuild`).

## Concept check-off (promised vs built)
Built: board + host arena + return teleport, ranked gate 30, wagers + escrow, PU opt-in,
anti-farm, dojo dimension (plots 4000 apart), one hall with interior rooms, upgrades + look
tiers, layout-map preview, style bonuses, balcony spectating, BRACKET+GAUNTLET wars, war
records + prune, elites + sign-off, seasonal clout, reopen −1, capacity 5→30.
Not built / half-built: old challenge flow NOT retired (#1); ki sense for new wars (#2);
wager forfeit/timeout hole (#3); no join-from-list (#5); board discovery (#6); war sign-up
window (#7); disciple-level rewards (#8); trophy/meditation/quarters mechanics (#17);
test-server screenshot proof (never done — test server stopped, Ultra upload pending).
