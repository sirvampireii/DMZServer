# LM 4.7.84 – 4.7.86 build notes

Three builds from one thread of work. 4.7.84 builds the approved navigation proposal. 4.7.85 fixes what the 4.7.84
audit found. 4.7.86 brings every player menu within the height budget and proves it with a real crawl.

| Version | Branch | Merged to `main` |
|---|---|---|
| 4.7.84 | `lm-4784-navigation` | bfce198a, includes 4.7.83 |
| 4.7.85 | `lm-4785-audit-fixes` | 1c61a484 |
| 4.7.86 | `lm-4786-height-budget` | see the merge commit |

## 4.7.84: friendlier navigation, pages that fit, plain words

Built from `docs/LM_4784_NAVIGATION_PROPOSAL.md`, which the owner approved as "build everything".

- **Navigation:**
  - A system Home exits to the main menu.
  - A menu opened fresh starts a new Back trail, and tabs don't stack in it.
  - Short Back names; the breadcrumb follows Back.
- **Hub:**
  - Home is two cards, with a Fastest-now button.
  - Search sits at the top of More, with better keywords and the real match count.
  - My colors is compact, with a "From a skin" page.
- **Systems:**
  - Difficulty shows its switch where you pick a tier.
  - Prestige tabs are Home / Shop / Trade; Character tabs are Home / Looks / Story.
  - Rival's tab reads "My rivals".
  - Sparring Home leads to the board; the board has a pager and a "Post a spar" page.
- **Dojo:**
  - Home has 5 actions and there is one Members page.
  - A dojo profile offers "Ask to join".
  - War sign-up has a card, the style change shows its total cost, and Upgrades shows the earn rates.
- **The owner's picks:**
  - The old `/spar dojo challenge` commands declare menu wars.
  - A first founding grants 15 Dojo Points, once.
  - A dojo can be founded with a default banner.
  - Disciples show "Disciple of <dojo>" in menus. LM has no hook for a name-tag display.
- **Approved fixes:**
  - A forfeit pays the winner both stakes, and so does a timeout when one fighter did more damage. Stakes come back
    only on a disconnect, a dead-even draw or a restart.
  - Ki sense reads running menu wars.

## 4.7.85: the 4.7.84 audit findings

- **N6, shortfall offers.** Every confirm page you can't afford offers the way to get more:
  - "Where to get coins" opens a new page under More, written from the real drop rules.
  - "Earn favor: Trade" goes to Prestige > Trade.
  - "How points are earned" goes to Dojo > Upgrades.

  Where it shows: the Difficulty tier page; the race, class, skin, part and Android confirms; the weight shop; every
  Prestige confirm; the dojo upgrade, style, bonus, rename, banner and wager pages. The offer is the button only, so
  tight pages stay in budget.
- **Dead war path removed.**
  - Removed: the old challenge write path (`challengeDojo`, `challengeDojoByMasterKey`, `acceptChallenge`,
    `declineChallenge` and the four `SparringSystem` wrappers), together with the 2x-RP award and the "(2x RP)" label.
  - Kept: `dojo_war_cancel` → `dojoRevokeWar`.
- **Back labels.** A name too long for its button reads "Back to previous page", never a bare "Back".
- **Release-check fixes.** Eight probe checks were updated to 4.7.84's text and layout. `rival|pick_spectate` and
  `dojo_members_own` are handled as stateful or old links.
- **Reference classes.** The task asked to drop the three modified prestige-shop reference `.class` files as build
  artifacts. They are not build output: `build.sh` verifies the built classes byte for byte against
  `reference/prestige-shop-228.1/` (see `reference/README.md`). Without them the build fails at the prestige-shop verify
  step. So each build re-baselines the class it changes, with a README log entry; 4.7.85 and 4.7.86 both re-baseline
  `UltraPrestigeScreens`.

## 4.7.86: every player menu within 300 px, proven by a real crawl

- **Budget enforced.** `UltraScreenSelfTest.HEIGHT_BUDGET_STRICT = true`: a player page over 300 px of native panel
  height fails the boot self-test. Staff and config pages are allowed. The release-check probe also fails when any
  player page goes over during its crawl.
- **27 pages brought under budget, without scrolling and without smaller text:**

  | Page | Before | After |
  |---|---|---|
  | Spar Board | 382 | 282 (one line per post) |
  | Dojo Home | 360 | 277 (stats beside the plan; 6 buttons in 2 rows) |
  | Upgrade pages | 301–340 | 271–284 (pictures beside a short card) |
  | Upgrades tab and Style | 330 / 337 | 273 / 280 (3 columns) |
  | Character part colors | 402 | 273 (4 swatch columns) |
  | Body parts, model parts, races | 307–334 | 244–281 |
  | Hub Home, More, Menu skins, My colors | 306–342 | 262–281 |
  | Prestige skill shop, Rival picker | 325 / 307 | 281 / 272 |

  Hub Home's prestige card is one line now: "Level X of Y. N levels to your next prestige".
- **Proof.** GuiKit ran on the real 4.7.86 crawl: 404 distinct pages at 1920x1080, 1366x768 and 854x480, GUI 2, 3 and
  Auto. The result is **0 errors**. The findings are in `tools/lm-gui-corpus/findings/real-crawl-findings.md`.
  - **8 exemptions, each listed in the findings.** GuiKit's label-only rules misread six body-part names that begin
    with "Back" ("Back Crystals") as Back buttons. It also flagged two staff config values shown as stored (an
    attribute id and a command).
  - **4 pages warn about click targets** at 854x480 GUI 2 only:
    - Skill Check / Skills is a staff page, allowed over budget.
    - The other three sit at 294–299 px, right at the 0.75x line, so a target there measures about 11.8 px.
- **Release check** (`DMZRig/results/release-4.7.86-run1`): 309 PASS and 4 FAIL, every player page within budget, and
  the strict boot self-test OK. The four failures were fixed:
  - My colors hid the "text color adjusted" note after Apply; it now shows.
  - `hub|coins` is reached from a coin shortfall or search, so it is now a stateful page in the navigation audit.
  - The spec-match row failed on two pages with client-specific lines. More now says its HUD line to every client.
    Menu skins' one-line client difference is filtered.

  The scripted re-run after these fixes is in `DMZRig/results/release-4.7.86`.

## Still owed (in game)

- Menus at GUI scale 3 and 4, in Full and Lite: the new layouts above, the 4-column part colors, the 3-column upgrade
  and skill pages, Dojo Home with its plan, and the Spar Board.
- The bridge fallback (KaiPath): the same pages; upgrade pages there still carry the text map, about 349 px.
- The owner's open items from the 4.7.84 audit, not built:
  - **Finding 3, owner copy:** the Goals note "No rewards yet: goals track your week.", the Majin/Mutant/Ultimate
    effect lines, and where the Healing line lives.
  - **Finding 4, the open picks:** confirm or change the rewire of the old war commands, the 15-point founding grant,
    the default banner, and the menu-only disciple title.
