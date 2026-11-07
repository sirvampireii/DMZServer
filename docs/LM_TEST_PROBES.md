# LM test-only probes

Some LM regression checks need a fake player driving real game events over several minutes. That
harness never ships: it lives in `tools/dmz-adaptive-difficulty/src/probe/java`, which a normal
`build.sh` does not compile, and `build.sh` fails if a probe class ever lands in the production jar.

**Never enable a probe on live.** No live or deploy script sets these flags, and the production jar
in `mods/` has no probe classes, so the flag does nothing there except log one warning.

## God forms gate probe (`-Dlm.probe.godForms`)

- **Source:** `src/probe/java/com/dbzlegacy/adaptivedifficulty/progression/GodFormsGateProbe.java` (4.7.37).
- **Build:** the same testprobe jar as below (`LM_TEST_PROBES=1`).
- **Hook:** `DifficultyEvents` looks the class up by name at server start, only when the flag is set,
  and calls `GodFormsGateProbe.run(server)` once.
- **What it checks** on a fake player with no God Ki skill and no other stack mastery:
  - `godki:godki` mastery 999, wallet 0: the `godforms` gate blocks with the "1000 God Ki mastery" message.
  - Mastery 1000, wallet 0: the gate passes, through the mastery read alone.
  - The `godkienhancement` group's message is unchanged.
  - Wallet Lv1: passes, and `reapply` raises the mastery to 1000.
- **Output:** `god forms gate probe check <name>: OK|WRONG`, then `god forms gate probe OK` or `FAILED: <n>`.
- **Run:** any test server with DragonMineZ, started with `-Dlm.probe.godForms=1`.

## Chamber wave cap probe (`-Dlm.probe.chamberCap`)

- **Source:** `src/probe/java/com/dbzlegacy/adaptivedifficulty/progression/ChamberWaveCapProbe.java` (4.7.37).
- **Build and hook:** as the God forms probe (testprobe jar, one run at server start).
- **What it does:** real `GodChamberWaves.start` calls by fake players standing beside Mr. Popo (Time
  Chamber) and an entity named Whis (God Chamber realm). Each run is stopped at once. It checks:
  - 30 x 8 training runs as 10 x 5, with one notice on the action bar.
  - 10 x 5 training is unchanged, with no notice.
  - 30 x 5 training caps only the wave count.
  - A trial request in the Time Chamber, which Ultra turns into training, is capped.
  - The God Trial runs 5 x 5, also when sent as 30 x 8, with no notice.
- **Test server needs:** DragonMineZ (for `dragonminez:master_popo`) and Ultra 0.4.x.
- **Output:** `chamber cap probe check <name>: OK|WRONG`, then `chamber cap probe OK` or `FAILED: <n>`.

## Born fresh stats probe (`-Dlm.probe.bornFreshStats`)

- **Source:** `src/probe/java/com/dbzlegacy/adaptivedifficulty/progression/shop/BornFreshStatsProbe.java` (4.7.41).
- **Build and hook:** as the God forms probe (testprobe jar, one run at server start).
- **What it does:** a fake player holds BrevinM's live main stats and the DMZ Mohist melee fix saves them
  (its 1-second snapshot). It checks:
  - Control: the pre-4.7.41 reset (new `StatsData`, attribute bases set to 0 directly) is undone by the
    first stat read. This is the live bug.
  - `PrestigeCharacter.freshStats`: all six stats read 0, also after the melee fix's 5-second check, and
    its saved copy is 0.
  - DMZ creation as a Frost Demon warrior then applies that race's base stats.
  - Switching into a played character makes the saved copy that character's own stats.
- **Test server needs:** DragonMineZ 2.2.1.x and the melee fix. On plain Forge its Mohist-only
  `ServerPlayerChangeDimensionMixin` does not apply, so use a test copy of the jar with that one entry
  removed from `dmz_mohist_melee_fix.mixins.json`; the stat snapshot code is unchanged.
- **Output:** `born fresh stats probe check <name>: OK|WRONG`, then `born fresh stats probe OK` or
  `FAILED: <n>`.

## Slot capture probe (`-Dlm.probe.slotCapture`)

- **Source:** `src/probe/java/com/dbzlegacy/adaptivedifficulty/progression/shop/SlotCaptureProbe.java` (4.7.43).
- **Build and hook:** as the God forms probe (testprobe jar, one run at server start).
- **What it does:** a real (non-fake) ServerPlayer runs DMZ Ultra 0.4.0's own login, create, switch, logout
  and stop handlers. It checks:
  - The main, with Kokopeli0007's record stats, creates an alt and switches back; after training to his live
    stats the main's record still holds the old ones (the live bug).
  - Logout writes the record: trained stats, savedAt advanced, gen = the index's, the same keys and parts as
    the switch path's record, equal to a fresh `SlotService.record(player, 0, gen, false)` apart from
    savedAt; the live form and stats are untouched (no toBase).
  - Login, then main to alt to main: the main keeps the trained stats.
  - Server stop with the player still listed writes the newest stats.
- **Test server needs:** DragonMineZ 2.2.1.x and DMZ Ultra 0.4.0 (character slots on).
- **Output:** `slot capture probe check <name>: OK|WRONG`, then `slot capture probe OK` or `FAILED: <n>`.

## Hit taken mastery probe (`-Dlm.probe.hitTakenMastery`)

- **Source:** `src/probe/java/com/dbzlegacy/adaptivedifficulty/progression/HitTakenMasteryProbe.java` (4.7.40).
- **Build and hook:** as the God forms probe (testprobe jar, one run at server start).
- **What it does:** a fake player in a form with per-hit mastery takes, then deals, one small hit
  (1 damage, so DMZ's damage scale is 1) through DMZ's own `StatsEvents.onEntityHit`. The opponents
  are another player, a master / Popo spar shadow, a summoned player shadow, a quest SPAR partner and
  a zombie, plus a God Chamber and a Whis trial foe. Mastery is reset before every hit. "DMZ formula" is a direct `gainMastery` with the same
  inputs, the rate before 4.7.40. It checks:
  - Hits taken from a player (PvP): 0.25 x the DMZ formula.
  - Hits taken from a zombie and from Ultra God Chamber / Whis trial foes (DMZ saga Saibaman and Vegeta,
    tagged as Ultra spawns them): 0.0625 x, a quarter of the PvP figure (4.7.42).
  - Hits taken from each sparring NPC: 0.03125 x, half the non-PvP figure.
  - Hits dealt to every opponent: exactly the DMZ formula (unchanged).
  - With a Mastery Gain effect the boost still multiplies on top and the same shares hold.
  - Nothing is left marked after the hits.
- **Test server needs:** DragonMineZ 2.2.1.x (shadow dummy, quest registry with a SPAR quest).
- **Output:** `hit taken mastery probe check <name>: OK|WRONG`, per-hit figures, then `hit taken mastery
  probe OK` or `FAILED: <n>`.

## Dragon ball probe (`-Dlm.probe.dragonBalls`)

- **Source:** `src/probe/java/com/dbzlegacy/adaptivedifficulty/event/DragonBallProbe.java`
  (4.7.35, piston checks 4.7.36).
- **Build:** `LM_TEST_PROBES=1 bash tools/dmz-adaptive-difficulty/build.sh` writes
  `build/LegacyMechanics-<version>-testprobe.jar` (the production jar plus the probe classes) next to
  the normal `mods/LegacyMechanics-<version>.jar`. Put the testprobe jar on the local test server only.
- **Hook:** `DifficultyEvents` looks the class up by name once, only when the flag is set, and calls
  `DragonBallProbe.tick(server, tick)` every server tick.
- **Modes:**
  - `-Dlm.probe.dragonBalls=main` on a fresh world: radar-only break (pickaxe, fist, wrong radar,
    right radar, Bi-Dimensional Radar), one set per player (inventory, ender chest, Sophisticated
    Backpacks, partial set, other set), pistons, the 3-minute respawn at new spots, a 3-of-7 set that
    starts nothing, a summon and wish, then the Earth set is collected again and it logs `MAIN DONE`.
  - `-Dlm.probe.dragonBalls=restart` on the same world after a stop: the set that was gone at boot is
    back 3 minutes after boot; logs `RESTART DONE`.
- **Output:** one server log line per check, `dragon ball probe check <name>: OK|WRONG`.
- **Test server needs:** DragonMineZ 2.2.1.1, GeckoLib, Curios, Sophisticated Backpacks + Core (for
  the backpack checks; they are skipped without them), plus LM's usual compile-only companions.
