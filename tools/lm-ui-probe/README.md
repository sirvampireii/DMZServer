# LM UI probe (test only)

In-game test rig for LegacyMechanics menus. Never shipped, never put on the live server. It is a tiny Forge mod
(`uiprobe`) that runs on a local test server and on one real probe client.

**Server half (`UiProbe`):**
- Crawls every LM menu a joined player can reach through navigation buttons (never `do:` actions).
- Runs the scripted checks and writes `uiprobe-script.txt` (PASS/FAIL lines):
  - Difficulty guide flow
  - Recommendation rule
  - Momentum banking and the cap
  - Mastery x1.5 inside the window, x1.0 outside
  - Wave-foe and unscaled kills
  - Slot switch and logout
  - Free trial start, refusal and revert, including the real 5-minute revert
  - Spin stop
- Sends each crawled menu to the client in turn.

**Client half (`UiClient`):**
- Screenshots each menu at 1920x1080, GUI scale 2, 3 and 4. After the top capture it scrolls to the end and
  takes another (`ui-NNN-1080p-gsN[-end].png`).
- Logs the drawn layout (`uiprobe-client.txt`). `N` lines carry the LM client's own layout, including the
  `SKIN saiyan_royal accent=RRGGBB` line.
- On `uirecord <label>` it also saves `ui-rec-<label>.png`. The spin check saves rot1 to rot4; rot4 is the
  preview stopped right after the back faced the viewer, and it must show the front.

## Use

```bash
LM_JAR=mods/LegacyMechanics-4.7.60.jar bash tools/lm-ui-probe/build.sh
export RIG_SERVER=... RIG_CLIENT=... RIG_KAIPATH_JAR=... RIG_LIVECFG=... RIG_EXTRA_MODS="..."
# LM client + KaiPath (LM wins), staff, with server class-load logging:
SRVJVM="-Xlog:class+load=info:file=classload.log" bash tools/lm-ui-probe/run.sh full mods/LegacyMechanics-4.7.60.jar 1 mods/LegacyMechanics-4.7.60.jar 1
# KaiPath only (bridge regression), first 45 menus:
MAXSHOTS=45 bash tools/lm-ui-probe/run.sh kai mods/LegacyMechanics-4.7.60.jar 1 - 1
# requireLmClient on, no LM client: the login must be turned away with the install message:
MAXSHOTS=0 REQUIRE=1 REJECT=1 bash tools/lm-ui-probe/run.sh reject mods/LegacyMechanics-4.7.60.jar 1 - 1
python tools/lm-ui-probe/analyze_layout.py tools/lm-ui-probe/out/full
python tools/lm-ui-probe/analyze_skin.py tools/lm-ui-probe/out/full tools/lm-ui-probe/out/<older run>
```

`SRVJVM="-Duiprobe.scriptshots=true"` screenshots only the pages the scripted checks kept (Difficulty guide, tier
detail, Step 3 with Momentum empty, banked and full, trial, summons). That is a quick rerun after a client-only change.

`run.sh` lists every environment setting. Results and logs go to `tools/lm-ui-probe/out/` (gitignored).

**Checks to read:**
- **Dedicated server:** `classload.log` has no `adaptivedifficulty.client` class.
- **Self-tests:** the server log shows every LM boot self-test OK. Strict mode is on, so a failed self-test
  stops the boot.
- **`analyze_skin.py`:** every native screen is `saiyan_royal` with its system accent (Difficulty E23B3B,
  Rival FFFFFF, Sparring 00AAAA, Prestige 55FFFF, Character 55FF55, SkillCheck FFFF55, Admin and config AA0000,
  main menu F0B429). The wire comparison against an older run differs only on pages whose content changed on
  purpose.

## Upkeep

Update this rig with every LM change it can see: new scripted checks in `UiProbe`, new accents in
`analyze_skin.py`, new layout rules in `analyze_layout.py`. A feature is not done until its check is here.
