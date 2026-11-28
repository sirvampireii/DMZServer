# LM 4.8.3 build notes

Admin commands for the dojo and the other systems that had no `/lm admin` home after the 4.7.88 fold. Commands
only: no menu, data migration or player-facing change otherwise.

| | |
|---|---|
| Branch | `lm-483-admin-commands` (off `main` at f8facf48, which has 4.8.2; the brief said 66c3dea8, but 4.8.1 and 4.8.2 had merged since, and a 4.8.3 below them would conflict with their version and audit changes) |
| Manifest row | `lm-483-admin-commands`, checks in `§ lm 4.8.3` |
| Probes | `V483AdminDojo`, `V483AdminSparRival`, `V483AdminSystems`; alone with `-Duiprobe.only=v483` |
| Check run | `DMZRig/results/v483-admin` (see Verification) |

Every leaf sits under `/lm admin` (staff, in creative when a player runs it; console counts as staff) and works from
the console. Bare forms and `help` print the branch's own leaves. Destructive leaves ask for a trailing `confirm`
and say what will happen without it.

## A. /lm admin dojo (new, `command/DojoAdminCommands`)

`<dojo>` is the dojo's name (any case, spaces allowed, quotes optional) or its master's name; tab completes dojo
names.

- `list`, `status <dojo|player>`: every dojo, or one dojo's record (master, disciples with elite and war rights,
  Dojo Points, clout and season, style, bonus, rings, banner, free rename, war record, running wars and challenges,
  plot and hall). A player name shows every dojo that player is in.
- `save`, `reload`: save dojos.json and the member bonds; re-read dojos.json (refused while a war or board spar runs).
- `points grant <dojo> <amount>`, `clout grant <dojo> <amount>`: negative takes, never below 0. Points use a new
  uncapped `DojoService.grantPoints` (`earn` only takes fixed daily-capped events); clout goes through
  `DojoService.clout`.
- `close`, `reopen`: the master's close and reopen (reopen drops upgrades one tier, as for the master).
- `rebuild <dojo>`: rebuilds the whole hall even when it is up to date.
- `disband <dojo> [confirm]`: calls off the dojo's wars (no result), releases every disciple, removes the dojo from
  dojos.json and the ladder (the Hall of Chosen is kept), clears the hall's blocks, items and entities under the
  build time budget, and tells the members. Refused while a board spar runs there. **The plot number is not reused**:
  the locked 4.7.68 rule "plots 4000 blocks apart, never reused" stands, so new dojos still get new plots and the
  cleared plot stays empty. Reusing plot numbers would need that rule changed first.
- `rename`, `style <ki|melee|strike|evasion>`, `banner <dojo> <colour> [pattern:colour ...]` or `banner <dojo>
  default`: no cost, no wait, the free rename is not spent; name and style rules still apply; style and banner
  changes rebuild the hall. Pattern codes are vanilla's (e.g. `red bs:white cr:15`).
- `promote`, `demote`, `warrights <dojo> <player>`: the 2-elite limit stays; the join-time minimum is skipped.
- `found <player> [name]`: online player; the founding menu's checks and grant; default name `<Player>'s Dojo`.
- `war cancel <dojo> <dojo>`: calls off every open war between the two (running, stuck, or pending); fighters are
  healed and returned as at a normal war end; no winner, points, clout or record.
- `season advance [confirm]`: the same season end as `/lm dojo season advance`, which still works.
- `/lm admin clear <player> dojo` (also part of `all`): removes the player from their dojo (bond, elite, war rights)
  and every other dojo's lists, online or offline. A master keeps the dojo and is told to disband it first.

## B. /lm admin spar (extended)

- `end <player>`: ends that player's spar with no result (no ranked credit, streak, leaderboard, history card or
  weekly-goal time); a running board bout is cancelled and its wager refunded. Online only. TP already paid during
  the fight stays.
- `board remove <player>`: takes down their Spar Board post.
- `bout cancel <player>`: a stuck board bout ends with no winner, both stakes refunded, both sent back as after a
  forfeit.
- `reset <player> [confirm]`: daily spar streak (current, best, last day), lifetime spar TP and best payout, the
  leaderboard streaks, and the RP they gave their dojo this season. Nothing else.

## C. /lm admin rival (extended)

- `rp set <player> <amount>`, `rp add <player> <amount>`: RP lives on each rivalry; a gain goes to the player's top
  rivalry (mutual first), a loss comes off the highest first, never below 0. A player with no rival is told why.
- `challenge cancel <player>`: ends their live or counting-down duel with no result or cooldown, and drops their
  challenge requests.
- `season advance [confirm]`: saves the final standings (rank, player, RP, season, date) in `RivalStore.seasonArchive`,
  sets every RP to 0, and starts the next rival season (the existing `RivalProgression` season number).

## D–G. saga, skillcheck, goals, difficulty tier

- `/lm admin saga reset <player> <saga> [confirm]`: a free staff reset (skips the coin cost and the reset switch;
  still refuses a locked or unstarted saga, or a player in a quest party). `/lm admin saga unlock <player> <saga>`.
  Sagas are named by readable keys like `saiyan-saga`.
- `/lm admin skillcheck clear <player>`: ends a stuck Skill Check session.
- `/lm admin goals inspect <player>`, `reset <player> [confirm]` (counters back to 0; the week's goals stay, they are
  global per week), `grant <player> <goal> [amount]` (default completes it; goals pay nothing yet).
- `/lm admin difficulty tier grant <player> <tier>`, `tier revoke <player> <tier>`: a staff grant stored with the
  player's difficulty data (`staffGrantedTiers`, missing in old data) that `UnlockSystem.syncUnlocks` never revokes.
  `resetpurchased` and `characterreset` are unchanged and do not remove grants; only `revoke` does.

## H. Old paths and help

- Every staff leaf on player branches (`/lm dojo season ...`, `/lm rival spectate|declare|...`, `/lm spar mentor ...`,
  `/lm spar weight ...`, `/lm mastery grant-first-*`, `/lm fusionreset`, `/lm revive`, syslog) is unchanged.
- `/lm admin help` lists the dojo, saga, skillcheck and goals subtrees, the new spar and rival leaves, the tier
  lines and the dojo clear scope.
- The new commands are registered from `SparCommands.register()` and `DifficultyCommands.register()`, not from
  `AdaptiveDifficultyMod`, whose class is byte-checked against the prestige reference.

## Verification

Per the owner's rule, only the change was checked, not every menu: build.sh (every audit) and the rig run with
`-Duiprobe.only=v483`. Each new leaf runs from the console and as the in-game staff probe player and its state change
is read back; the dojo spot check is a points grant that shows on the dojo record and in `status`.

- build.sh: OK, every audit (`§ lm 4.8.3` all OK). Jar sha256
  `cae94d498923b46c2bc5af225d11d0f5fc085ec941ccd133f1977bb8a4155573`.
- `DMZRig/results/v483-admin/v483/uiprobe-script.txt`: **109 PASS, 0 FAIL** (46 console runs, the rest as the staff
  player in creative), including `/lm admin help` listing the subtrees, the Mohist name tab slots and the points-grant spot check
  ("Gave Probe Admin Hall 50 Dojo Points (now 65)", and the record shows it).
- The first runs failed every player-side check: the probe ran them in survival, and `/lm admin` refuses staff in
  survival as designed. The probe now switches to creative for these checks. One offline check also failed for a rig
  reason: a stand-in has a made-up UUID, and the rig's player cache also answers its name with an offline-mode UUID,
  so by name it is two players and the clear asks for a UUID (the existing lookup). The probe checks the offline
  clear by UUID and logs the by-name answer as INFO.
- Run: `SRVJVM="-Duiprobe.only=v483 -Duiprobe.norender=1" MAXSHOTS=0 bash tools/lm-ui-probe/run.sh v483 <jar> 1 <jar> 1`.

## Deploy

Commands only, no data migration. Dojo `save` and `reload` are safe to use live. Full restart after the jar change.

## Mohist tab completion

- The Mohist bridge's player-name tab helper (`LmCommandSuggestions`) knows every new player slot (saga, skillcheck,
  goals, dojo found, spar end/reset/board remove/bout cancel, rival rp/challenge cancel, difficulty tier), and not the
  amount after it. Probe line `V483 TAB`.
