# LM 4.7.88 build notes

One command tree under `/lm`, a plain `/lm help`, a creative-mode gate on staff admin commands, dojo world
protections, and a picker for players who belong to two dojos.

| | |
|---|---|
| Branch | `lm-4788-commands-dojo` (off `main` at bc81a192) |
| Manifest row | `lm-4788-commands-dojo`, checks in `§ lm 4.7.88` |
| Probes | `v88Commands`, `v88DojoRules`, `v88DojoPicker` (`tools/lm-ui-probe`) |

## A. Every command lives under /lm

Each leaf keeps its current behaviour and permission; only its parent changed. Each system still builds its own
branch and registers it into `/lm` (players) or `/lm admin` (staff) through `CommandAccess.underLm` /
`underLmAdmin`; Brigadier merges them, so `/lm admin character` holds both the moved fixes and the existing cooldown
clear.

| Old | New |
|---|---|
| `/difficulty`, `/diff` | `/lm difficulty` (the menu is still also `/lm open difficulty`) |
| `/difficulty admin ...` | `/lm admin difficulty ...` |
| `/difficulty do <action>` | `/lm difficulty do <action>` (chat-button handler) |
| `/difficulty reset` | `/lm difficulty reset` |
| `/rival ...` | `/lm rival ...` |
| `/rival admin ...` | `/lm admin rival ...` |
| `/spar ...` | `/lm spar ...` |
| `/spar dojo ...` | `/lm dojo ...` (`summary`, `members`, `previous-members`, `season advance`, `hof`, `top`, `accept`, `decline`, `challenge`, `name`, `banner`) |
| `/spar admin ...` | `/lm admin spar ...` |
| `/spar weight ...` | `/lm spar weight ...` |
| `/progression` | `/lm progression` (`help`, `android remove`) |
| `/progression boost ...`, `/progression masteryboost ...` | `/lm admin progression boost ...`, `/lm admin progression masteryboost ...` |
| `/progression android <player>`, `android remove <player>` | `/lm admin progression android ...` |
| `/progression admin <flag> <value>` | `/lm admin progression flags <flag> <value>` (no "admin admin") |
| `/progression gui`, `status`, `do ...` | `/lm admin progression gui`, `status`, `do ...` |
| `/prestige ...` | `/lm prestige ...` |
| `/prestige menu`, `/prestige do ...`, `/padmin ...` | `/lm admin prestige menu`, `/lm admin prestige do ...`, `/lm admin prestige ...` (one subtree) |
| `/character ...` | `/lm character ...` |
| `/character admin ...` | `/lm admin character ...` |
| `/skillcheck ...` | `/lm skillcheck ...` |
| `/skills ...` | `/lm admin skills ...` |
| `/enddragon ...` | `/lm admin enddragon ...` |
| `/androidify ...` | `/lm admin androidify ...` |
| `/terminal` | `/lm terminal` (same rule: terminal node or the retired CMI alias node; console still cannot open it) |
| `/lm ...` | unchanged (`help`, `gui`, `open`, `page`, `saga`, `do syslog`, `fusionreset`, `revive`, `mastery`, `admin ...`) |

- The old roots are unknown commands now. The Mohist bridge registers only `lm` (its tab helper reads new paths
  back into the old per-root player-slot rules through `LmCommandSuggestions.legacyView`, so player names still
  complete). `CommandAccess.isStaffOnlyBukkitCommand` and `isSkillCheckBukkitCommand` were deleted; nothing used
  them after the bridge change.
- **Vanilla `/difficulty` is back.** LM no longer owns that name, so vanilla `/difficulty <level>` (ops, world
  difficulty) works again. It was not re-registered.
- Every string that named an old command was rewritten, in Java (help, usage lines, chat buttons such as the
  Difficulty chat menu's `/lm difficulty do page tiers`, `LmChat.tip` links), the CNPC scripts in
  `customnpcs/scripts/` (comments and messages only; none runs an old command), the living docs in
  `tools/dmz-adaptive-difficulty/*.md`, `docs/LM_TEST_PROBES.md`, the GuiKit specs and the kit fixtures. A
  manifest check scans every Java string literal for an old root. Historical records keep their old wording:
  `docs/CHANGELOG.md`, earlier manifest rows and build notes, `AUDIT.md`, and `archive/`/`uploads/` snapshots.
- The retired Bukkit plugin `tools/dmz-adaptive-difficulty-gui` (LegacyMechanicsGUI, never installed) still
  declares the old roots in its `plugin.yml`. It is not in the LM jar and must stay out of `plugins/`.

## B. Dead leaves deleted

- `/lm admin migrate-cnpc` (and `force`), its hub action and `CnpcDataMigrator.forceMigrateWorld`. The one-time
  startup import (`migrateWorldIfNeeded`) is unchanged.
- LM's `/difficulty peaceful|easy|normal|hard` and `/difficulty admin gamedifficulty`.
- Boost names: `boost` (TP boost) and `masteryboost` (mastery boost) are two different features, both registered,
  so both stay. The staff help listed only the TP boost; it now lists both, under `/lm admin progression`.

## C. Tree completed

- `/lm open dojo` and bare `/lm dojo` go to the same entry as the main menu's Dojo tile
  (`UltraDojoScreens.openDojoEntry`, picker first for two dojos). `/lm open goals` opens Goals (`hub|goals`).
  The `/lm open` help no longer says "sparring and dojo".
- `/lm hud on|off`, bare `/lm hud` (or `status`) for the state in plain words: the same switch as the More tab
  toggle (`LmHud.set`, key `lm_hud`, default on).
- `/lm admin inspect <player> dojo` prints the player's dojo record (name, role, style, Dojo Points, clout,
  members) and opens their dojo menu. No dojo clear scope was added.

## D. Staff admin commands need creative mode

- `StaffAccess.isAdminSource`: staff AND (no player behind the source OR that player in creative). Every
  `staffLiteral` (so `/lm admin` and every `/lm admin <system>`, `/lm do syslog`, `/lm mastery`,
  `/lm fusionreset` and the staff-only leaves inside player branches) and every `.requires(staff)` in
  ProgressionCommands now use it. The gate is in `requires()`, so tab-complete hides admin nodes from staff who are
  not in creative.
- `AdminGate` (Forge `CommandEvent`): a staff player in survival who types an admin command gets "Staff admin
  commands need creative mode. Switch to creative and try again." instead of vanilla's "Unknown command". It also
  resends a staff player's command list after a gamemode change, so tab-complete follows the mode.
- **Consequence for staff:** in game, switch to creative before any `/lm admin ...`, `/lm mastery ...`,
  `/lm fusionreset ...` or staff-only leaf (rival declare/spectate, dojo season advance, spar weight, ...). Console
  is unchanged. Store or panel commands sent from console (for example a store package that runs
  `lm admin progression boost start <code> <name>`) keep working, but **any external command still using the old
  `progression boost ...` form must be updated to `lm admin progression boost ...`.**
- Untouched: `/lm revive` (self, `<player>`, `all`; console `lm revive <player>` still works), `/lm skillcheck`
  (donator node), `/lm terminal` (terminal node), and the player leaves.

## E. /lm help

`/lm help` is a short guide in three groups (Open a menu, Your stuff, Get help), with no admin commands. The
player help pages for difficulty, rival, spar, dojo (new: `/lm dojo help`), progression, prestige, character and
Skill Check use the same plain style. `/lm admin help` stays detailed, with every path on the new tree and a note
on the creative-mode rule.

## F. Dojo world (dimension legacymechanics:dojo)

Forge events in `dojo/DojoWorldRules` (no mixins):

- **Building.** Breaking, placing (also multi-place), buckets and tool changes (strip, till, path) are allowed only
  for the master of the dojo whose plot the block is in (`DojoWorld.plotIndexAt` + the dojo's `masterUuid`).
  Everyone else gets "Only the master of this dojo can build here."; outside every plot (war ring row, empty space)
  nobody builds ("Nobody can build outside a dojo hall."). No staff bypass. Farmland trampling is cancelled in the
  dojo world. Messages repeat at most every 2 seconds.
- This **replaces** the old 4.7.68 rule in `DifficultyEvents` (`onDojoBreak` / `onDojoPlace`), which let only staff
  build anywhere in the dojo world. Blasts still break nothing (`onDojoExplosion` unchanged).
- The hall builder (`DojoWorld.ensureBuilt` → `DojoBuilder` / `DojoPlacer`) places with `Level.setBlock`, which
  fires none of these events, so halls still build.
- **Ki attacks** (per `SparCombat.classifyKiType` or DMZ ki damage) cannot hurt a player in the dojo world unless
  attacker and victim are the two fighters of one active board bout or war bout. Player-fired, mob-fired and
  self-splash ki are cancelled silently. `onDojoAttack` keeps its rules (fall/void, spectators, non-pair bout
  damage).

## G. Your dojos picker

- `DojoService.affiliations(UUID)` lists every open dojo a player belongs to, with the role (Master, Disciple).
- With two or more, opening Dojo (main menu tile, `/lm open dojo`, `/lm dojo`) shows "Your dojos" first: one row
  per dojo with Visit (teleport) and View (that dojo's Home). One affiliation goes straight to Home; none shows the
  no-dojo Home, as before.
- The dojo being viewed is remembered per player (in memory). Management actions (upgrades, style, bonus, banner,
  rename, elites, war rights, close, war declarations and answers) check the viewed dojo, on the server, at click
  time. A disciple viewing their mentor's dojo sees exactly what a disciple sees.

## Reference classes

`AdaptiveDifficultyMod`, `ConfigFieldHelp`, `ProgressionGuiApi`, `PrestigePointsSystem`, `PrestigeShopHooks` and
`PrestigeCustomShop` re-baselined (text only, plus the two new registrations); see `reference/README.md`.

## Still owed (in game)

- Every new path typed in game; the old roots rejected; a staff member in survival gets the plain message and in
  creative is allowed; console `lm revive <player>` still works; tab-complete over the Mohist bridge.
- `/lm help` read by a new player.
- The dojo build and ki rules on a live dojo visit.
- The picker with a real dual-affiliation player.
- CNPC scripts in `customnpcs/scripts/` changed only in comments and message text; they go live with the next script
  sync, not with the jar.
