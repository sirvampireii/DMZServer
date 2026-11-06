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

## NPC spar mastery probe (`-Dlm.probe.npcSparMastery`)

- **Source:** `src/probe/java/com/dbzlegacy/adaptivedifficulty/progression/NpcSparMasteryProbe.java` (4.7.40).
- **Build and hook:** as the God forms probe (testprobe jar, one run at server start).
- **What it does:** a fake player in a form with per-hit mastery takes, then deals, one small hit
  (1 damage, so DMZ's damage scale is 1) through DMZ's own `StatsEvents.onEntityHit`. The opponents
  are another player, a master / Popo spar shadow, a summoned player shadow, a quest SPAR partner and
  a zombie. Mastery is reset before every hit. It checks:
  - Player vs player and the zombie pay exactly DMZ's formula (a direct `gainMastery` with the same
    inputs), so PvP and normal fighting are unchanged.
  - Each sparring NPC pays half the player-vs-player figure, for hits taken and hits dealt.
  - With a Mastery Gain effect the boost still multiplies on top and the half still holds.
  - Nothing is left marked after the hits.
- **Test server needs:** DragonMineZ 2.2.1.x (shadow dummy, quest registry with a SPAR quest).
- **Output:** `npc spar mastery probe check <name>: OK|WRONG`, per-hit figures, then `npc spar mastery
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
