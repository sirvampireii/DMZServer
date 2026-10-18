# LegacyMechanics changelog

What changed from **4.5.147** through **4.6.216**.

## Every body part is reachable in the Body Parts Shop

The Model Customization shop's list was never race-filtered — it has always sourced the full 68-piece DMZUltra `pieces` array, and `resolve(race, …)` only ever fed the world sync that decides which pieces your worn form renders. What hid pieces was a pagination mismatch shipped with the shop in 4.6.207: the catalog paged 14 cards at a time while the screen rendered only the first 8 of each page, so 28 of the 68 pieces — four of the five tails, two of the six wings, cape, mane, star crown, and more — were never drawn, and a piece you cannot see is a piece you cannot unlock or equip. Page one in catalog order contains no tails or wings at all, which made the gap look like race locking.

The page stride now matches the screen's two-up row capacity, and the screen renders exactly the cards the page owns instead of a hard-capped subset, so all 68 pieces are browsable and purchasable by every race. Race filtering stays where it belongs — in the world sync. (`saiyan_tail` is deliberately not a shop item: it is the Saiyan's natural race tail, and the shop sells `tail_fur`, the mesh that id maps to.)

## The config editor fills the screen again on KaiPath 1.3.31

The 4.6.206 fullscreen patch declared the config editor's design resolution as 1920×1080 — that is the panel clamp request, not the grid the widgets are actually laid out in (426×240, the smallest auto GUI). KaiPath 1.3.31 implements `fillScreen` properly and computes `scale = panel / design`, so a design equal to the panel made the scale ~1.0 and every widget rendered at raw size in the top-left corner instead of filling the window. The editor now declares its real 426×240 design box, so honoring clients scale the whole layout up to the window; the 1920×1080 panel request, `margin 0`, and `lockPanel` are unchanged, so clients that ignore `fillScreen` still clamp the chrome exactly as before. The startup self-test had been pinned to the buggy 1920×1080 design and now pins the correct one, and the protocol doc — which always described the 426×240 contract — finally matches the wire.

## Other players can finally see your body parts — in color

The world sync that pushes equipped body parts to viewers had two defects. It was addressed `subject:"local"` and sent only to the owning player's client, so no other client ever received your pieces — and the parts a viewer did render came back gray, because the fallback color read for a **remote** player resolves against the observer's own character data, where `0x808080` matches no fallback.

Snapshots now carry `subject:"uuid:<owner>"`, and every equip/login send broadcasts to the owner plus every tracker of that player, so each receiving client binds the parts to the right entity. When a player starts seeing another — login into range, respawn, dimension change, walking back after you changed gear while apart — the target's current snapshot is sent to that new tracker alone, and the login handshake backfills every other online player's parts to the new viewer (those track-start events fired before the viewer's feature advertisement reached the server, so they were gated off at the time).

Snapshots also carry explicit `parts.colors` — `skin`, `second`, `hair` from the character and active-form override chain, and `own`, `trim`, `horn`, `light` from the shipped form-design table — so clients prefer the JSON colors over the gray reflective read. Key names are fixed for JLDK in `docs/KAIPATH_GUI_PROTOCOL.md`. A degraded oversized payload now keeps its uuid subject instead of resetting to `local`, which on a broadcast would clear each observer's own parts.

## The paid character and rival/spar actions move to staff-only commands

Race change, class change, and reskin purchases now happen in the Character Services menu — their typed command forms (`/character race`, `/character class`, `/character reskin`, and the matching `do` actions) are staff-only, and tab-complete no longer offers them to players. In the rival and sparring trees the interaction verbs are gated to staff as well: `/rival declare|accept|decline|remove|challenge|spectate`, `/spar dojo accept|decline|challenge`, `/spar mentor accept|decline|leave`, and `/spar weight quote|buy`. Nothing in any menu changed — the GUI buttons, chat buttons, and chest clicks players already use keep working exactly as before, and every gated handler already checked staff at execution, so this closes the typed path only. `/padmin` now hides itself from non-staff entirely.

Staff fix other players with three new commands: `/character admin set-race <player> <race> [keep%]` (keep% defaults to 100), `/character admin set-class <player> <class>`, and `/character admin reskin <player>` (opens their appearance editor). These are free fixes — no Ancient Coin charge, no permission node, no race lock or cooldown — while the feature switches and safety checks still apply. Players who type the old commands are pointed to the Character Services menu, and the help text moved: player help lists the menus, staff help (`/lm admin`, `/rival admin`, `/spar admin`, `/character admin`) lists the actions.

## /padmin gets godki, and sync is gone

`/padmin godki <player> set|add|remove <0-5>` sets another player's God Ki Enhancement investments directly: raising tops their mastery up to match, lowering drops the enhancement mastery records first so the new count lands correctly, and 0 removes the enhancement entirely — skill, forms, and history — like a reset. No favor changes hands, and the prestige admin menu has the matching God Ki action.

`/padmin sync` is removed everywhere — the command, the menu button, and its bridge handler. Reapplying prestige state does not need a manual trigger in the current build.

## Ending a fusion sweeps every stored bonus, minus the racial ones

Unfusing no longer looks for bonus names containing "fusion" and "zenkai". The tail of `endFusion` now enumerates every stored bonus name from the serialized map and deletes all of them except keys starting with `Zenkai_`, `Assimilation_`, or `Absorption_` — for you and for your partner — then syncs both. That catches `FusionBonus`, every fusion-zenkai spelling the old heuristic could miss, and anything a future stat id would have hidden from the fixed stat list. Deletion matches names exactly, so a racial key that merely mentions fusion elsewhere in its name survives. Other systems that write non-racial bonuses (weapon bonus scaling, piercing, DMZ passives) re-apply on their own tick pulses and login events, so the sweep clears what the fusion produced and play restores what still applies.

## The nemesis toggle moves to Character Services, and OFF keeps your menu summon

The Nemesis spawn toggle moved from Rival → Settings to Character Services → Home — it is a personal spawn preference, not a rival interaction. Same three modes, same per-character memory; the Rival menu no longer shows it.

The OFF gate had a real bug: it blocked everything, including the deliberate spawn from the nemesis menu, because the OFF check ran before the manual-spawn exception. The gate is now reason-aware — the journal challenge passes in all three modes, writ proving summons pass everywhere except OFF, and automatic tick visits only spawn on ON. OFF reads: "No automatic nemesis spawns — you can still summon yours from the nemesis menu."

## The config editor scaling experiment is reverted

The 4.5× coordinate scaling added in 4.6.213 is gone, along with the `contentScale` wire field. It rested on two assumptions that do not hold: KaiPath 1.3.29 does not read `contentScale` from the JSON (zero references in its bytecode), and its default panel is 350×300, not the 426×240 the factor was computed from. Widget coordinates ship unscaled exactly as before 4.6.213 — `panel(1920, 1080)`, `center(false)`, `margin(0)`, and `lockPanel()` unchanged — and the self-test again expects the 148px category rail. A proper fullscreen fix needs KaiPath-side `fillScreen` support from JLDK; LM cannot pre-scale its way there.

## Rival nemesis spawns can be turned down or off

Rival → Settings has a third toggle: Nemesis spawn. ON is today's behavior (gold when active). Click once for MANUAL — only the nemesis fights you start from the journal can bring one, and the wandering tick visits never do. Click again for OFF — no nemesis ever spawns for you and existing visit triggers are suppressed. The choice is saved per character, the page always shows which state you are in, and nothing spams chat. On a server without dmzultra nothing changes.

## Race keep-% charges follow the configured ladder

Race change prices scale with the keep-% you pick, priced from the configured ladder and compressed proportionally under the Ancient Coin cap. This release closes the last gap: a server that turns off `levelCostMultiplier` and configures a steep ladder would still have flattened every high keep-% back to the coin cap; the compression now covers both paths, and a build-time check proves every keep-% charges a distinct amount. (Players who saw every keep-% charge the same capped amount were on the old process — one server restart loads the fix.)

## The config editor fills the panel

KaiPath 1.3.29 ignores the `fillScreen` request, which stranded the config editor on a 426×240 canvas on the left of the screen. The editor now ships its layout natively at 1920×1080 — coordinates, widths, heights, and text metrics are emitted at 4.5× while the panel request stays as before — so fullscreen clients get the same full-panel layout that honoring clients always produced.

## /prestige effects shows what you own

`/prestige effects` lists your current Prestige shop effects in chat: Majin absorption, mutant upgrade, God Ki Enhancement investments and mastery, and your favor balance. View only — buying stays on `/prestige buy ...`.

## The /lm command tree is cleaned up, and every menu purchase has a command

Commands that did nothing are gone. The staff test menus (`/lm admin testgui`, `/lm admin guitest`) and the telemetry hit-log probe (`/difficulty admin telemetry test`) were debugging leftovers; the difficulty "points" commands that only answered "points were removed" are gone too. `/lm admin open` and `/rival admin open` duplicated the normal menu commands, so they are gone as well. `/lm admin inspect` on its own now tells you what to type instead of quietly clearing your inspect mode.

New commands cover what you could previously only do in menus:

- `/prestige` is now the prestige shop command for everyone: `balance`, `turnin <1|2|3|6|9>`, `buy skill <skill>`, `buy tier <1-7>`, `buy majin|mutant|godki`, and `remove majin|mutant`. Staff keep the menu backend under `/prestige menu` and `/prestige do`, and wallet edits stay on `/padmin`.
- `/character` opens your character services menu, and the purchases work from chat too: `/character race <race> <keep%>`, `/character class <class>`, `/character reskin`. Cosmetic head bones and body parts ride along under `/character do <action>`.
- `/spar weight quote <type> <value>` and `/spar weight buy <type> <value>` price-check and buy training weights without opening the shop screen.

Alias clutter is trimmed to one name per action (`/spar mentor decline`, `/spar mentor leave`, `/spar apprentice remove`), the progression chat menu's buttons now point at `/progression` (they said `/prog`, which did not exist on Forge), and help text everywhere drops the old CNPC wording and matches the real tree. Staff-only branches stay behind the same staff checks, and the command-tree guard that prevents the 4.6.205 login crash now audits every LegacyMechanics tree at the end of registration instead of only the first four.

## Prestige store sells God Ki Enhancement

Prestige → Shop → Effects has a new option: God Ki Enhancement. Each point of favor invested grants 1,000 God Ki mastery, up to five investments (5,000). The button shows what you have invested and your current mastery, costs one favor per point, and asks for the usual second click before it takes the favor.

On login the character's God Ki mastery is topped up to whatever your investments grant. It only goes up — a higher mastery earned in play is never lowered. While invested, the God Ki Enhancement stays on the character even though it cannot be obtained through normal play.

God forms now need God Ki. Custom God forms require the enhancement to be present at all; the true God Ki form additionally requires the mastery threshold, which `godKiTrueFormMasteryThreshold` sets (1000 by default, so the first investment unlocks it). Trying to transform without it shows a short notice instead of transforming.

## Menus use their own color, and the config search filters as you type

Each system keeps the same layout. The page title and the active tab use that system's color: Character gold, Rival red, Sparring blue, Progression green, Prestige light purple, Difficulty yellow, Saga aqua, Staff Admin dark red, Config editor gray. Rival opens on Actions, with tabs Actions, Records, and Requests. Sparring opens on Dojo, with tabs Dojo, Rankings, and Wars. Those tabs are the pages, not a second list of the same buttons. Prestige no longer has a Modules list. Config modules stay on Staff Admin → Config editor and `/lm admin config`. Progression lists each module once, opens that module, and Back returns to Progression. The config search filters the current tab by field name while you type, and shows how many fields match.

## Saga reset reads the stored level

Saga reset cost uses `PlayerDifficultyData.getHighestDmzLevel()`, which returns a long and takes no player argument. The coin class on the server does not have `getHighestDmzLevel(ServerPlayer)`.

## Menus share one header, and the config editor explains itself

Page titles are gold, bold, and centered. Section titles are the same gold, left aligned. Card titles are white and bold. Every page uses « Back on the left. Character Services opens on Head parts, with tabs Head parts, Reskin, and Saga Reset. Prestige Modules lists each config module and opens that tab. A closed module stays on the list in gray.

The config editor puts a plain-English line under each field. Fields with the same prefix sit in a section you can collapse. A bad chance shows "Must be between 0 and 1" on the row. Save all opens a review of old and new values, and Confirm writes them. A dirty row has undo for that field only. Recent changes sit at the top of the tab. A tab with more than 30 fields asks you to type a filter. Drafts still stay in memory until Confirm.

## Config editor uses tabs

`/lm admin config` opens one screen. Difficulty, Progression, Character, Saga, Rival, Sparring, and Currency are tabs across the top. The active tab is gold. A search line filters the current module by field name. Each field shows its value, then the control under it: on/off, minus and plus, edit, or manage. A gold dot marks a change that is not saved yet. Save all writes those changes, copies the json once per session, appends `config-audit.log`, and reloads the file. Reload from disk and Discard changes leave the file or the dots. Rival and sparring stay on the bar and stay closed, because those files are player records. Tapping a field shows its type, default, a short description, and the last person who changed it when the audit log has that line.

## Test menu opens each screen directly

`/lm admin testgui` puts Head parts and Reskin at the top. Current menus lists Difficulty, Rival, Sparring, Progression, Prestige, and Staff Admin. Each one opens that screen. The old Character button, which only showed those same choices again, is gone. Skill Check, Skills, and Saga Reset stay as their own buttons.

Head parts is still Head and Horns only. The client-only body accessories stay out of the shop.

## Config editor confirms, then saves

`/lm admin config` opens Difficulty, Progression, Character services, and Saga reset. Fields still come from the live objects. A change shows the old value and the new one before it is written. The file is copied to `.bak` once per session, the change is appended to `config-audit.log`, then the file is saved and reloaded. Numbers have minus and plus. Enum values cycle. Rivalry and sparring record files stay closed. Currency, AI, boss, elite, evolution, telemetry, and tier are registered for a later pass.

Head parts no longer has a Body page. Each part is On or Off.

Saga reset still charges the saga base plus your level times 10 Ancient Coins, and that price is on every row.

## Production menus share one look

Hub, rivals, sparring, progression, prestige, difficulty, character services, staff admin, logs, and skill check use the same gold headers, gray detail, and 6-pixel row gap. Color codes live in one style class. Prestige shows the 20k / 40k / 60k / 80k ladder, then 50k / 100k, on the main page. Menu errors no longer print internal action names. Back is « Back on every submenu.



## Saga reset scales with your level

Character Services has a Saga page. It lists every saga Dragon Mine Z has loaded, including datapack sagas, from `QuestRegistry.getAllSagas()`. A saga with no display name uses a readable id.

The cost is the saga base plus your level times `perLevelCost`. `config/legacymechanics/saga-reset.json` has `defaultBaseCost` for any saga that is not listed. Locked sagas and sagas you have not started cannot be reset. Confirming calls Dragon Mine Z `resetSaga`, which clears that saga's progress and the tracked quest when it belongs to the saga. You need the Ancient Coins first. An old untouched config file picks up these bases; a base you already edited stays.

## God Ki history is removed, not zeroed

The 4.6.67 wipe left a mastery entry at 0, the used-form list, and the previous stack form. Those are removed now. The skill, the live form, and the ledger string still clear on the same resets. Death still leaves God Ki in place.

## God Ki resets with the other wipes

`/dmzstats reset`, the melee reset, Dende's reset, a race change, and a new character clear God Ki the same way they clear Majin absorption. That removes the `NoeaGodKiLedgerIdentity` string, the `godkienhancement` skill, the Enhance with God Ki stack form, and its mastery. Death does not clear it. God forms and demon god tier are left alone: those are not stored God Ki progress.

## Test hub links go straight there

`/lm admin testgui` lists each screen once. A button opens that screen. Hub on any screen opens the main hub. Head parts is Head and Horns only. The Body page is gone.

## Staff config editor

`/lm admin config` and the Staff Admin **Config editor** button open the live settings. Legacy Mechanics, character services, and saga reset are listed from the config objects themselves, so a new field shows up without a new button. Switches flip in place. Numbers, text, lists, and maps can be edited, and the file is saved. The admin permission node and one-time migration flags stay in the file. Rivalry, sparring, and rival progression stay player records, not settings.

## Production menus use the new look

`/lm` and the other menus use gold headers, gray subtitles, a gold divider, section tags, and tab strips. Head parts uses the same on/off colors. The wording from the 4.6.53 humanization audit is unchanged.

`/lm admin testgui` still opens the staff shortcut. Hub opens the main hub.

## Saga reset follows loaded sagas

`/lm saga` and the hub button open a menu of every saga Dragon Mine Z has loaded, including datapack sagas. The list comes from `QuestRegistry.getAllSagas()`. There is no hardcoded saga list.

Cost is Ancient Coins. `config/legacymechanics/saga-reset.json` has `defaultBaseCost` (100,000 copper unless you change it). A saga id under `costs` overrides that. Any saga that is not named, including one added later, uses `defaultBaseCost` with no config edit. Confirming clears that saga's quest progress and your live quest mobs for it. Rewards you already received stay. Leave a quest party first.

## Test menu is its own screen

## Test menu is its own screen

`/lm admin testgui` opens its own menu. The title is gold, the rows have a gap, and Head parts on that menu uses the new colors. Difficulty, rival, sparring, prestige, character, and staff admin still open the current menus. `/lm` is unchanged.

The previous build was still running the old test class from the base jar, so the command opened the normal hub. This build copies `CnpcStaffTestGui` from source.

## Head parts turn on one at a time

The head-parts shop no longer sells precombined looks such as Horns 1 + Antennas 2 + Ears 1. Each DMZ bone is its own on/off row: hair, three ears, two antennae, five horns, and three Majin heads. Several can be worn together. Dragon Mine Z stores them as one `activeHeadBone` string joined with `+`, and the client draws each piece.

A combo that was already unlocked is split into those pieces. A truncated piece such as `ma` is dropped.

DMZUltra's 68 body accessories (antennae, capes, wings, tails, and the rest) are not sold here. They are client form parts (`part.<name>`), chosen by the form you are wearing. `DmzLookS2CPacket` menu and restyle actions do not set them, and `SceneFormS2CPacket` only swaps a cutscene form. The server cannot turn a piece on or pre-select it in the form editor. The Body page lists them so you can see the names.

## Test GUI shows the new CNPC colors

`/lm admin testgui` opens the new look: gold headers, gray subtitles, tab strips, and head-part rows that say equipped, unlocked, or locked. `/lm` and the other menus stay on the current look until that audit is done. Nothing on the client changed.

## Staff test GUI stays on the server

`/lm admin testgui` opens the CustomNPCs hub. The server sends that menu through CustomNPCs, which clients already have. LegacyMechanics does not ship a client screen, a client packet, or a DMZUltra widget, so a staff member does not install this jar to use the menu.

## Fusion mixin target

The unfuse bonus mixin names `FusionLogic` as a string. A class literal loads that class while the mixin config is applied. Both `endFusion` injects use `require = 0`. The mixin plugin does not look the class up at config time.

## Fusion unfuse bonus

Unfusing clears the `FusionBonus` split bonus on the player who unfused and on their partner. Dragon Mine Z only removed that bonus from the fusion leader, and only when the leader was online. Permanent Saiyan zenkai bonuses are left in place. A bonus whose name is both a fusion bonus and a zenkai bonus is removed.

## Menu wording

Sparring leaderboard names match the numbers they show: training points, sessions, perfect spars, combo, and time. Dojo rankings call war wins "War wins" and reputation "Reputation". The prestige page lists 20,000, 40,000, 60,000, and 80,000 for the first four completed prestiges, then 50,000 or 100,000. Notices that only describe status use yellow. Buttons that ask you to pick someone say to select them in the list.

## Prestige requirement follows completed count

The first four completed prestiges require 20,000, then 40,000, then 60,000, then 80,000 power. How many prestige you are holding does not change those four. From the fifth prestige on, holding none requires 50,000 and holding one or more requires 100,000. This build includes the 4.6.51 Hakai helper visibility fix.

## Prestige requirement scaling

4.6.52 keyed those first four amounts off held prestige. 4.6.53 keys them off completed prestige instead. A stored need floor still cannot replace the scaled number with a flat 20,000. The status line and the prestige menu say the current requirement and the one after it.

## Hakai helper visibility

Helpers in `HakaiDestroyerGateMixin` are private. A package-private static method is merged into the Noea class and mixin application fails. The inject handlers stay private and static, because `onKiAttackFire` and `sourceSaysHakai` are static. The build rejects a non-private mixin method that is not an inject, redirect, overwrite, or shadow.

## Mixin cleanup

Default-priority StatsData hooks (load guard, owner lookup, secondary-attribute player, restore clamp, reset wipe) are one mixin, `StatsDataGuardsMixin`. The three default-priority Resources clamps are one mixin, `ResourcesEnergyDrainGuardMixin`. `/dmzstats reset` player block and absorption wipe are one mixin. Every absorption wipe calls `AbsorptionWipeHelper`. The Hakai Destroyer methods are cached after the first successful lookup. A failed lookup is not cached.

These stayed separate because a mixin can only target one class, and a different priority still has to win: the personal stat cap (5000), the absorption bonus (1001), stat scaling (6100), and the death TP slots (6200). `DojoWarSenseMixin`, `PotionEffectHelperTpBonusMixin`, and `DmzRevampPrestigeCapMixin` are still live. This build is not uploaded. Restart onto 4.6.49 until this jar is loaded.

## Hakai mixin applies

The Hakai mixin is no longer skipped during mixin config. Loading `DivineImmortalityEvents` there always failed because Noea is not loaded yet, so the gate returned false and the mixin never applied. The mixin names that class as a string. Each inject uses `require = 0`. Mixin 0.8.5's `@Mixin` annotation has no `require` parameter. Restart onto 4.6.49 to load it.

## Fall health pool

A tall fall logs the player, vanilla current and max health, DMZ max health, fall distance, damage, and whether ki negation consumed the hit. DMZ does not store a current health. When vanilla max health is far below that DMZ max, the vanilla pool is raised to the DMZ max before fall damage is applied. The damage amount is unchanged.

## Hakai damage-message gate

`sourceSaysHakai` reads the projectile technique id, the damage message, and the technique name. A name that contains "hakai" returns false unless the attacker is an apprentice or appointed Destroyer. A missing Destroyer lookup is not a Destroyer. Official Hakai projectiles still go through Noea's own check. Restart onto 4.6.48 to load it.

## Hakai mixin load gate

Destroyer rank is read by reflection when an attack fires, so transforming the Noea listener does not load Noea's Destroyer classes.

## Fall damage log packaging

`FallDamageDiag` is in the Forge jar. 4.6.44 registered the listener and left the class out, which would crash mod init. Do not restart onto 4.6.44.

## Hakai name gate

A ki technique whose id or name contains "hakai" no longer takes Noea's insta-kill path unless the attacker is an apprentice or appointed Destroyer. Official Hakai technique ids are unchanged. They already require that role.

## Fusion cooldown reset

Staff can run `/lm fusionreset <player>` on someone who is online. It sets Noea's `fusionCooldownEnd` to 0, removes Dragon Mine Z's `FusionCooldown`, clears `fusionValidationError`, and syncs both. The player has to be online. The command does not end an active fusion.

## Fall damage log

A tall fall prints one console line, `[LM] Fall damage:`. It shows the entity health, the entity max, DMZ's max health, and the health attribute. It does not change the damage. DMZ does not keep a separate current health.

## Menu notices

Every flash notice uses the same colors. The header is gold, `§6§lNotice`. The body is yellow, `§e`. A screen no longer picks green, gray, or red for that text. On and off are written in the words. The difficulty page's own status lines (personal difficulty, Ancient Coins, scaled mobs) are unchanged.

## Absorption wipe crash

The Majin wipe classes now live outside the mixin package, so the server can load them. Creating a character, changing race, Dende's reset, and prestige can clear the stored absorption bonus. If a wipe throws, the error is printed and the server tick keeps going.

## Stat reset and race change

Players cannot use `/dmzstats reset`, including the percent form. Staff still can. Change race stays in Character Services. Dende's reset still works. Prestige wipes stats on its own, the same full wipe as a 0% reset, and does not go through that command. Class change, reskin, and head parts are unchanged.

## List selection

Highlighting a row no longer previews or opens it. Head parts uses a Preview button on the highlighted part. Race and class use Choose this race and Choose this class. Rival, spar, and difficulty lists use Open, Choose, or Details on the highlighted row. Double-click still equips a title. The wheel scrolls the list.

## Menu layout

The character preview shrinks when you are larger than a normal player, and grows when you are smaller, so a giant form stays inside the preview box. A result notice makes the menu taller instead of squishing the buttons.

## List scrolling

Head parts, rival picks, spar picks, and the other player lists scroll with the mouse wheel. The list is the menu's scroll, so the wheel is not stolen by a second scroll region.

## Menus

Spar dojo home, members, hall of fame, and rankings are one Dojo page. Dojo war stays its own page. Progression sections are tabs on Modules. Difficulty, Rival, and Spar toggles live on Settings. The hub no longer shows section labels or a welcome line. Remove Android is on Character Services. Race change asks how much progress to keep on the confirm page. Rival actions stay their own page. Prestige forms stay their own page.

## Rival records

History, stats, season, quests, achievements, hall of fame, journal, and title are one Records page. The section you are reading is highlighted. Older links to those pages open the same page on that section. Leaderboard stays its own page.

## Head part preview

The head-parts menu shows your own character, not a stand-in model. The part is already on you, so the menu uses that same model. Step back and look closes the menu and leaves the preview on so you can look at it. Back puts the old part back.

## Menu colors

Progression titles and its own buttons are blue. Prestige stays pink, including Open Prestige. Forms stays the Prestige color. Confirm summon is green. Inactive team modes are gray. The 2× boost presets match the other presets. Dojo home uses the spar color. The staff progression panel uses the progression color. On and off on the boost line match the toggle colors.

## Android tools

Android tools and convert stay staff-only. Players can use Remove Android, and Back returns them to the main menu.

## Menus

Long menu results that say "check chat for details" are also sent to chat. Screen titles use the same System · page form. Info blocks use three lines. Button rows use one step. Difficulty tiers, prestige forms, and spar leaderboard tabs use the shared button grid. A missing inner class fails the build before the jar is uploaded.

## Confirm clicks

A second click that confirms a paid action no longer crashes the server. The pending-click class is included in the jar.

## Menu wording

Rival chat [Cancel] no longer forfeits a live challenge under a "cancel yours" hover. A live challenge shows Forfeit. A pending request shows Cancel request. Tier buys, permanent tier buys, Majin and Mutant buy and remove, global TP boost start and end, Android convert, title unequip, and clearing the active tier ask you to click again within 10 seconds. Android convert names who it will convert and says Super forms and Legendary forms are deleted. The spectate list is only people in a live challenge. Spar chat [TP], [Sessions], and [Perfect] open those boards. A 0% race wipe says head-part unlocks and coins stay. Player screens use Head parts, Training bonds, Bond invites, Challenge, and Ancient Coins.

## Reskin gender

During a reskin, the gender picked in the editor is saved. Races that do not have a gender stay male.

## Head bone preview

Clicking a head part shows it on you before it charges. Unlock & equip, Equip, or Restore. Back puts the old part back.

## Sparring menus

The player leaderboard no longer has Wins or Win streak tabs. Those boards were showing Training Points. The dojo Wars tab now lists war wins. Button names match the pages they open. Leaving a master asks for confirmation. An invite you sent can be withdrawn. An invite sent to you can be accepted or declined.

## Sparring non-combat damage

Splash potions, lingering potions, TNT, thorns, and magic do not start a spar or grant TP. Ki, empty-hand melee, a registered weapon, and weapon projectiles (arrows, tridents, firework rockets) still do.

## Offline player clear

`/lm admin clear <player>` works when that player is offline. A name is taken from who is online, sparring, rivals, dojos, or the server player cache. A UUID always works. Two people with the same name need a UUID. Spar and rival data clear from their stores, including that player's dojo season row, profile, and wars. Difficulty and progression for someone who is offline are removed from their saved player file. Training points already on the character stay.

## Sparring TP

Sparring pays TP when a player deals damage to another player. A left-click with an ordinary item is not that damage, so it does not start a spar, keep one going, or grant TP. Empty-hand melee, a registered weapon, and ki still do.

## Corpses in protected dimensions

Death in space, Namek, Beerus' planet, and the other Noea travel dimensions uses the same corpse as everywhere else. Noea was clearing the drop list, and corelib then deleted every inventory item that was not on that list, so the corpse spawned empty and the items came back only on respawn. Stopping that clear leaves the drops in place, and the corpse gets them. They are not returned on respawn. A corpse in the void stays in the void.

## Dragon balls

Left-click a placed dragon ball once with the radar for that set and the ball goes into your inventory. The Earth radar takes Earth balls. The Namek radar takes Namek balls. A different radar leaves the ball where it is. That click still works inside an FTB Chunks claim or a GriefPrevention claim. Other blocks in those claims stay protected. A full inventory drops the leftover at your feet. The radar forgets that spot. Right-click with all seven still summons the dragon.

## Majin absorption bonus

The stored melee and ki bonus applies while absorption power is above zero, scaled by the same power-release percent as melee and ki. The read happens after Noea adds the raw bonus, then replaces that raw add with the scaled one. At zero power the bonus is left out of the damage read and the stored numbers stay, so absorbing again still adds to them. `/dmzstats reset` clears those stored numbers from `StatsCommand.resetStats`. The melee self form clears them from its own executor. Dende's reset (action 2) clears them from `handleDende`. A race change clears them in character services, and finishing a new character clears them again before the new race is applied. The console prints `[LM] wipeAbsorption firing for <name> via <method>` for each of those, then whether Noea's data copy was present, the stored melee before it was zeroed, and the melee read back after save. If `clear()` throws before those lines, the same console prints `[LM] clear() threw` and the stack. Battle power is unchanged.

## Race lock removed

No race is blocked, reset, or padlocked. `race-lock.json` is deleted on load. The race-select sync sends an empty lock list so an old padlock clears. The staff Race Lock toggle is gone. Stuck saga difficulty still clears while character creation is incomplete.

## Inflated max reset

4.6.3 saved huge max-ki and max-stamina bases on the player. 4.6.2 does not rewrite them, so both sides keep showing that number. On login, a base above 100 times the registered default (20, so anything above 2,000) is set back to that default. There is no repeating reconciler.

## Class-change resource sync

Paths that change max ki or stamina now send `ResourceSyncS2C` as well as the stats packet. A fighting-class change sends it next to the other class-change packets. Class commands, race creation, prestige count changes, prestige recovery, and rival fusion go through `DmzResourcePoolClamp.syncToClient`, which sends both packets. `DmzSkillUtil.sync` stays stats-only.

## Level cap cache

Stat getters no longer re-read breakthrough NBT or sync the client. The personal cap is cached per player and refreshed on login, logout, and a breakthrough purchase. A sync packet goes out only when that number changes.



## Dead facades

Chest-menu bridges `BukkitGuiBridge` and `CmiGuiBridge` are gone. So are the unused facades `SkillProgression`, `RaceProgression`, `TpProgression`, and `CnpcBridge`. `ProgressionSystem` already calls the race and training pulses directly.

## Hot-path guards

Owner lookup caches a miss for half a second instead of scanning every online player on each regen read. Stats load clears its thread flag if `load` throws. Stamina regen no longer ticks Dragon Mine Z cooldowns while those cooldowns are already moving, and it drops its maps on logout. A failed mob-scaling pulse no longer skips rival, sparring, and progression for that tick. Meditation looks only near players who are already meditating. The repo keeps one LegacyMechanics jar, one melee-fix jar, and one sdu jar. `plugins/` does not ship `LegacyMechanicsGUI`.

## Difficulty mob fight rhythm

Each kit pulse now commits to one action, picked by range, using the moves that mob already had. Close is a slam, a shock, or a short blast while the mob steps back. Mid is a dash, a laser, or a leap. Far is a beam. A pack no longer refreshes slowness, mining fatigue, or wither on the same tick — one nearby mob owns each of those. Melee sidesteps while closing, and a retreat paths back in.

## End Dragon summon

The Difficulty menu can summon the End Dragon again. Personal Adaptive Difficulty must be on, the active tier must be T4–T7, and the summon costs 3 Ancient Netherite unless staff are free. Natural End dragons still do not spawn, and there is still no `/enddragon spawn`. Admins clear dragons with `/enddragon clear`.

## Meditation rate

Training with another player is full time. More players nearby do not add more. Two living NPCs credit 75% of that time. Each held prestige adds 10% on top of whichever rate you are using. One NPC still does not count.

## End return portal

Walking through the End return portal back to the overworld does not start the death penalty. Dying still does.

## Meditation near two NPCs

Time now counts while you are already meditating within 16 blocks of two living NPCs that are themselves meditating. The circle flag is not required. One NPC still does not count, and standing nearby without meditating still does not count. Two players meditating near each other is unchanged.

## Dojo war tracking

While a dojo war is active, the other dojo's members in your world show up in Noea's tracking signature scan. Open Scan / Track and the compass points at the nearest one until you pick a different signature. Members in another dimension do not show. Positions use the same 4-block snap as other signatures.

## Meditation

Meditation is no longer a trainer, a trial, or a slash command. DragonMineZ Meditation goes up while you are **already meditating** in Living World, and only in one of these situations:

- **Two players** are both meditating within 16 blocks of each other. Both gain time.
- **One player** is meditating within 16 blocks of **two living NPCs** that are meditating.

One NPC is not enough. Standing nearby without meditating does not count. The mod does not start meditation for you. If a partner and a circle are both nearby, you still gain one stretch of real time.

Time is saved when you leave, log out, or the server restarts. Extra time carries into the next level. Each breakthrough raises Meditation by 1 and sends one chat message.

Time to leave each level:

| From | To | Time |
|------|----|------|
| 0 | 1 | 1 minute |
| 1 | 2 | 5 minutes |
| 2 | 3 | 15 minutes |
| 3 | 4 | 30 minutes |
| 4 | 5 | 1 hour |
| 5 | 6 | 2 hours |
| 6 | 7 | 4 hours |
| 7 | 8 | 6 hours |
| 8 | 9 | 8 hours |
| 9 | 10 | 10 hours |

That is **31 hours 51 minutes** to reach level 10. Skill Check shows your level, progress, and time remaining.

## Natural skills

Flight, sprint/jump, and the old meditation trainer are **removed**, not turned off with a flag.

- Potential Unlock still levels from sparring.
- Skill Check is still one Skills tab: Potential Unlock, Meditation, and the saga skills.
- Saga rows show the level and Locked, Unlocked, or Max.

## Prestige shop

The shop still sells **permanent** skill unlocks: Potential Unlock, Fly, Meditation, Jump, Sprint, and the saga skills. Buying one sets the skill floor. It does not replace the new meditation timer. Ki attacks and forms are not in that shop list.

## Commands

`/progression meditation`, `/enddragon spawn`, and `/progression flags_fabled` are gone.

**Android remove works.** `/progression android remove` removes your own upgrade. Run it again within 10 seconds to confirm. Staff can target another player. The hub Remove Android button does the same thing.

**Difficulty summon is back on in 4.5.161.** There is still no `/enddragon spawn`. Natural End dragons stay off. Admins clear dragons with `/enddragon clear` (console works too). `/enddragon repair` is still there.

Players also use `/lm`, `/difficulty`, `/rival`, `/spar`, and `/skillcheck` (donators). Staff keep progression boosts, flags, and android convert.

## Skill Check NPC

Any player can open Skill Check by right-clicking the Skill Check NPC, including dialog trigger 21. Paste `customnpcs/scripts/SkillCheckPlayerNpc.js` onto that NPC. Slash `/skillcheck` and the hub button still need the donator permission `legacymechanics.skillcheck`. The running server keeps the previous jar until a panel restart.

## Already on main before this merge

4.5.147 and earlier stay as they were. That includes the server-only class and stamina behavior (the mod does not rewrite the client bar), the 10-minute death TP penalty, the spar closeness bonus on the training-point cap, and one Skill Check tab.
