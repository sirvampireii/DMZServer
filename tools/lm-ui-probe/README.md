# LM UI probe (test only)

In-game test rig for LegacyMechanics menus. Never shipped, never put on the live server. It is a tiny Forge mod
(`uiprobe`) that runs on a local test server and on one real probe client.

**Server half (`UiProbe`):**
- Crawls every LM menu a joined player can reach through navigation buttons (never `do:` actions).
- Runs the scripted checks and writes `uiprobe-script.txt` (PASS/FAIL lines):
  - Difficulty guide flow
  - Guide shows once per account (4.7.63): first open is Step 1; Got it, Skip on Step 1 and on Step 2 each mark it
    done and land on Home; reopen goes straight to Home; Tiers is the plain list; How difficulty works replays all
    three steps without clearing the mark; a record without the key (a pre-4.7.63 player) sees it once; slot
    switch, record reload and logout keep it. On the real client the final phase clicks Skip guide and How
    difficulty works (`guide1`..`guide4` REC lines in `uiprobe-client.txt`).
  - Central hub (4.7.64): Home dashboard (next prestige, Fastest now, Train / Compete / Progress), tabs Home /
    Goals / More in order, grouped tiles, no made-up numbers or other mods' commands; Goals placeholder pays
    nothing; More has meditation, Menu skins and Staff Admin for staff; every hub link lands on the right screen
    (`LINK` lines); Difficulty and Prestige Homes show their lines from other systems; the spar streak line only
    with a streak. The final phase clicks the Goals tab and the Dojo wars tile on the real client
    (`hub1`..`hub3` REC lines). Before the screenshots the probe player is made invulnerable and the script's
    mobs are cleared (a leftover zombie once killed it mid-shot).
  - Dojos, Spar Board and wars (4.7.68), with three stand-in players: no dojo means no spar (the message, Find a
    dojo, a dim Post); tabs Home / Dojo / Spar Board; found a dojo; the dojo dimension is loaded; a disciple
    belongs to the master's dojo; the ranked gate (0 of 30, ranked dim); trading hits starts no spar; post,
    the listing details, accept teleports both into the poster's arena, forfeit sends them back to the exact
    spot; an instant forfeit doesn't count, a counted friendly spar adds to the gate and pays the host 2
    points and no TP; a 5-netherite coin wager (both held, KO pays the winner both), ranked credit reaches the
    leaderboard, the same pair at once gets no credit; a 20-point wager refunded on a forfeit; a second arena,
    built in the world, two spars at once in one dojo; spectating refused without the room, then from the
    gallery (can't be hit, put back when stepping into a ring); the upgrade map preview; capacity to 30;
    style and bonus (+5% only on the style's hits, a race tradition only for that race); an elite's war needs
    sign-off; close and reopen one tier down; the season reset keeps rooms; visiting another plot 4000+
    blocks away and leaving. The final phase on the real client: board, dojo and upgrade-preview shots
    (`board1`, `dojo1`, `dojo2`), a lethal blow in a board spar is a KO (`KO` lines), and a bracket and a
    gauntlet war against a stand-in who leaves the ring (`WAR` lines: record, re-challenge, bracket page).
  - Friendly Fist never lets a spar kill (4.7.65), in the final phase on the real probe player with a stand-in
    partner: three lethal partner hits (a non-melee source, Friendly Fist on the victim only, the case DMZ's own
    knockdown misses) each leave the player alive, healed to full, the spar still on and no "death" record; a
    lethal melee hit with only the attacker's switch on is caught too; a zombie's lethal hit on a fighter is not
    guarded; with Friendly Fist off for both a lethal partner hit kills and the spar ends as death. The
    `ff1-after-three-lethal-hits` REC shot shows the client still in game.
  - Custom menu skin (4.7.66): the picker's Custom row after Zeno Omni; picking it wears Saiyan Royal tones and
    opens My colors (five fields, five color wheels on the LM client, Style, Scene, Apply, Preview, From, Reset);
    Apply with both hex spellings saves and reopens wearing them; the look goes out as the palette token; "red",
    "#12" and "#gggggg" show the human error, apply nothing and keep every field; empty fields keep their colors;
    unreadable text is adjusted (3:1 dark, 4.5:1 light) with the note; From and Reset fill fields without saving;
    relog, slot switch, a preset and back keep the colors; Backgrounds off keeps Custom. Without the 4.7.66 LM client
    (the kai stage) the editor is the plain note. The final phase taps the top of the Panel wheel on the real client
    (`uiclick wheel:0`, red) and clicks Apply (`custom1`..`custom4` REC lines); `FINAL CUSTOM` checks it saved.
  - Part colors, any color (4.7.67): the own channel's swatch page keeps its presets (six-digit swatch actions) and
    adds Pick any color (one hex field, Apply this color, a wheel on the LM client; the plain note without text
    input); typed `#e23b3b` and `3B82F6` save through the swatch path; "red", "#12", "#gggggg" show the human error
    and change nothing; empty keeps the color; a swatch still saves instantly; trim is untouched; a slot switch keeps
    it; Reset returns Default. The final phase taps the page's wheel (red) and clicks Apply this color
    (`part1`..`part3` REC lines); `FINAL PARTCOLOR` checks it saved.
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

## One command

```bash
export RIG_SERVER=... RIG_CLIENT=... RIG_KAIPATH_JAR=... RIG_LIVECFG=... RIG_EXTRA_MODS="..."
bash tools/lm-ui-probe/matrix.sh mods/LegacyMechanics-<version>.jar [<older run>/full]
```

`matrix.sh` builds the probe and runs three stages. **full** uses the LM client and KaiPath with class-load logging.
**kai** is KaiPath only. **reject** turns `requireLmClient` on with no LM client. Then `report.py` writes two files to
`$RIG_OUT`. A baseline (an older run's `full/` folder) adds the bridge wire comparison.

- `REPORT.md`: one PASS / FAIL / SKIP line per check, with the evidence:
  - server boot and strict self-tests
  - no client class on the dedicated server
  - crawl completed
  - scripted checks
  - spin stop front-facing
  - skin and accent per menu
  - bridge wire vs baseline
  - layout rules
  - scale-to-fit
  - look (contrast, cut labels, panel edge)
  - KaiPath-only path
  - one spec for both paths (4.7.67: a page whose native packet has a color wheel and whose KaiPath packet has
    none differs on purpose and is counted separately; any other difference fails)
  - the requireLmClient rejection
  - every menu native with the LM client (Preview forms too)
- `review.html`: a contact sheet for the owner. It shows GUI scale 3 and 4 shots of every Difficulty page, the main page
  of every other menu, and the spin-stop shots.

Menu skins (4.7.62): after the scripted checks the probe runs a skins pass: every skin on one page per system, Lite
on three pages per skin, backgrounds off on the main menu for two skins. Each page goes out in its look (the probe
sets the player's Menu skins choice right before it), and the client waits a full second per GUI size so the
`fps=` on each SCREEN line is the menu at rest. `report.py` then checks:
- every page in its expected skin, background and look (`analyze_skin.py`);
- 60+ FPS at GUI 3/4 in every skin, Full and Lite, and Saiyan Royal no slower than before (`RIG_FPS_BASE` = an older
  run's `full/`; a scripted-only run on the old jar is enough);
- `review.html` gets one card per look.
`RIG_EXPECTED_DIFF="hub|main"` lists pages a release changes on purpose, so the bridge comparison names them instead of
failing. The probe keeps the client's hello across its fake logout (the Momentum and trial checks post one), so later
menus route as they would in play.

Settings:
- `ONLY_SCRIPTED=1` screenshots only the scripted pages, a quick rerun after a client-only change.
- `SKIP="kai reject"` drops stages.

Runs are exclusive: a run lock and a matrix lock in the shared `$RIG_SERVER` folder (not `$RIG_OUT`, so two output folders cannot overlap). Stop a matrix with `bash tools/lm-ui-probe/stop.sh`: stopping only its outer shell leaves `matrix.sh` running into its next stage. Every process a run starts carries `-Dlmrig.run=<id>`. Stale servers,
clients and command feeds from an interrupted run are stopped before the next starts, and a run stops its own on
exit.

## Menu mockups

Before a menu changes, its pages are mocked up in the current look by the real LM client, not drawn by hand:

```
SRVJVM="-Duiprobe.mock=true" bash tools/lm-ui-probe/run.sh mock <LM jar> 1 <LM jar> 1
python -I tools/lm-ui-probe/mock_compose.py "$RIG_OUT/mock" tools/dmz-adaptive-difficulty/build/dojo/pics docs/dojo-visuals
```

Mock mode skips the crawl and the checks. `UiProbe.mockCrawl` builds the proposed pages with LM's real spec API
(`UltraScreenSpec`; the main menu is the live hub page with its tiles renamed), the rig shoots each at GUI 2, 3 and 4,
and the client quits. `mock_compose.py` names the shots `menu_<page>_gs<N>.png` and adds a picture where a page leaves
room for a new element the client can't draw yet (4.7.68: the dojo floor plan). The layout and look analyzers run on
a mock run like any other.

## Analyzers

- `analyze_layout.py`: buttons inside the panel, at least 80px wide, 16px rows, 6px gaps, 426 wide, no overlaps, and
  scale-to-fit per GUI scale. With the LM client every menu is native, Preview forms included (4.7.61); `LMRIG_ALLOW_FALLBACK="character:form_preview"` re-allows that page when testing an older jar.
- `analyze_skin.py`: Saiyan Royal and the system accent per native screen, plus the wire comparison with an older run.
- `analyze_look.py`: what a reviewer catches by eye.
  - Label-on-face and text-on-background contrast must be at least 3:1. This is how red labels on green buttons
    would have been caught.
  - Labels the renderer had to shorten.
  - Controls sitting on the panel's bottom or right edge (the rounded border cuts them).
  - Live buttons whose label is the dark-gray locked ink (they read as disabled).
  - Text field hints wider than their field (`T ... hint="..." hintw= room=` lines, 4.7.61 client).
  - Raw Java values printed as text (`[I@2feb1f5f`).
  - NOTE only, not a failure: a wrapped body line that leaves one short word alone on the next line.
  - It reads the `face= ink= cut=` and `I` lines that the LM client's `debugLayout` writes.

## Kit corpus

After a release that changes menus, refresh the Ultra GUI kit's real-menu corpus from a full run:
`python tools/ultra-gui-kit/test/gen_lm_corpus.py <run>/full tools/ultra-gui-kit/test/fixtures/lm-specs.jsonl`.
The kit's `LmCorpusTest` then checks every real LM menu against what KaiPath relies on.

## Use (single stages)

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
