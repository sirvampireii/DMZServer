# DragonMineZ 2.2 port (LM 4.7.0, melee fix 3.0.0)

Both jars are built against `mods/dragonminez-2.2.jar` (CurseForge file 9282248, sha256
`b69d922f…414e7d`). Minecraft 1.20.1, Forge 47. Each `mods.toml` asks for DragonMineZ `[2.2,)`, so
neither jar loads next to 2.1.3. Live still runs 2.1.3 with LM 4.6.261 and melee 2.12.25.

## What the build now proves

`scripts/audit_mixin_targets.py` runs in both builds. It reads every registered mixin and fails the
build when a target class, method, or overload is missing or ambiguous, a callback type does not
match the target's return type, handler parameters or static-ness do not match, or an `@At` /
`@Shadow` target is not in the bytecode. Against 2.1.3 it reports every 2.2 retarget below, and
against 2.2 it passes (30 melee mixins, 29 LM mixins). A boot can still fail on things only a live
Mixin run sees (for example a mod that is not on the compile classpath), so the test boot is still
required.

## Melee fix 3.0.0

- Carries live 2.12.26 (never committed): no Shurui raid or SDU mixins.

- Party guard on the 2.2 party API. 2.2 has no party quest merge; quests complete per player.
  `joinParty`, `joinPartyForFusion`, `reconcile` (login), `syncPartyDifficulty`, `kickMember`, and
  `leaveParty(ServerPlayer, boolean)` run inside a guard that refuses a borrowed `SUCCESS` and
  strips any that slips through. Reward claims still need a personal earn.
- NPC master disables use `CallbackInfoReturnable`. A denied action sends the dialogue result with
  the reason, so the player reads "… is disabled on this server." as the NPC's line.
- Melee keeps DMZ 2.2's windup: the fix no longer replaces `CombatAttackRequestC2S.handle`.
- Old Kai Precision skip is **off**: 2.2 checks the stage list on the server, and the client pack
  does not ship this jar.
- Console: `config/dmz_mohist_melee_fix-debug.properties`, `debugLogging=true` to see per-event lines.

## LM 4.7.0

- Retargets: Dende wipe (`handleDende` returns boolean), new-character wipe and post-creation hook
  (`CreateCharacterC2S` body is `lambda$handle$1`), radar pickup (`onDragonBallRemoved`), ki beam
  size argument, `removeEnergy` and TP hooks pinned to the full overloads, nemesis `champion` pinned.
- Reskin and race-change class pick grant DMZ's re-customize wish before opening the editor; 2.2
  throws the editor's save away without one.
- Leaving Saiyan, Namekian, or Majin also clears that race's 2.2 `RacialData`.
- Player data migration marker is written only when no player file was skipped; the `.dat` strip
  marker waits for it (`docs/LM_PLAYER_DATA.md`).
- Console: `debugLogging` in the LM config (`/lm admin config`), off by default.
- Not changed: config file formats. DMZ upgrades its own config to 2.2.0 on first load.

## Before the first test boot

1. Back up `config/dragonminez/` and the world's `playerdata/`.
2. Install `dragonminez-2.2.jar`, `LegacyMechanics-4.7.0.jar`, and `dmz_mohist_melee_fix-3.0.0.jar`;
   remove the 2.1.3, 4.6.261, and 2.12.25 jars.
3. The add-ons on the server were built for 2.1.3: Noea (`Noea-1.2.0-DMZ-2.1.3`), dmzrevamp,
   dmzultra, and KaiPath (Shurui's mods and SDU are no longer on live). Check each loads on 2.2; a failure there is not from these jars.
4. Search the log for `Mixin apply failed`, `InvalidInjectionException`, and `[dmz_mohist_melee_fix]` /
   `[LegacyMechanics]` warnings.

## In-game regression

Character creation, race change, Dende reset (heal, reset, tail), party invite/leave/kick and quest
completion (a member who was not on a quest must not get it), reward claims, dragon-ball radar
pickup, skill buying including a `-1` priceless skill and form, fusion and unfuse (bonuses cleared on
both players), TP gain and the death penalty (x0.5 effect for 10 minutes), master NPC actions
(shadow spar, Guru, Gero must be refused with the reason shown), the demonforms 4th form (Abyssal
Sovereign) unlock, reskin and race-change class pick (the edit must save), Old Kai challenge (all
five stages), and LM records intact after `/dmzstats reset`, a race change, and a Dende reset.
