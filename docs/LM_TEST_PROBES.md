# LM test-only probes

Some LM regression checks need a fake player driving real game events over several minutes. That
harness never ships: it lives in `tools/dmz-adaptive-difficulty/src/probe/java`, which a normal
`build.sh` does not compile, and `build.sh` fails if a probe class ever lands in the production jar.

**Never enable a probe on live.** No live or deploy script sets these flags, and the production jar
in `mods/` has no probe classes, so the flag does nothing there except log one warning.

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
