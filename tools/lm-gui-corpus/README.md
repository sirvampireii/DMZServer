# LM GuiKit corpus

Every LegacyMechanics menu, laid out as a GuiKit layout spec and drawn in the current skin system. GuiKit checks each
one at real window sizes (1920x1080, 1366x768, 854x480) and GUI scales 2, 3 and Auto, in Minecraft's own font.

The corpus has three jobs:

- **It is the design source for new menus.** Copy the closest page in `pages/` and change it, then let GuiKit check it
  before anyone writes Java.
- **It is the reference for how LM menus look.** The mockups follow `client/NativeMenuScreen` and `MenuSkins` number
  for number:
  - Saiyan Royal by default, with each system's fixed accent;
  - Full and Lite looks;
  - the rounded panels, shadows, gradients and raised buttons.
- **It is a check on the real layout.** It records what today's Java gets wrong, in `findings/layout-findings.md`.

## Run it

```powershell
cd DMZServer\tools\lm-gui-corpus
.\run.ps1            # exit 0 = every page passes GuiKit; mockups\ is refreshed
.\run.ps1 -NoMockups # check only
```

**Needs:**

- `mod-toolkit` next to `DMZServer`, with this folder's `guikit-src\` files in its `fx\` and `tests\` (see
  `guikit-src\README.md`);
- Python 3 with Pillow (`PYTHONUTF8=1` is set by the script);
- Minecraft 1.20.1 installed through CurseForge (`%USERPROFILE%\curseforge\minecraft\Install`), which is where the font
  comes from.

Other useful commands:

- one page: `..\..\..\mod-toolkit\scripts\guikit.ps1 -Project . -Only hub_home`
- the GuiKit self-test: `..\..\..\mod-toolkit\scripts\guikit.ps1 -SelfTest`

**Target:** exit 0, with 0 errors and 0 warnings. Warnings count.

## How it fits together

| Path | What it is |
|---|---|
| `pages/*.json` | **The corpus source.** One entry per page, written as the builder calls in the `Ultra*Screens` Java: title, tabs, body lines (`&` for the section sign), button rows (legacy x/w on the 350 grid), inputs, model, back footer, and the file/method it mirrors. `mock` holds the target-state changes and fixes the mockup shows instead of today's layout; `variants` holds the same page in another skin or in Lite. |
| `gen/build_corpus.py` | Turns pages into GuiKit specs by mirroring `UltraScreenSpec.toMenuPacket` (the fitMenu reflow), `placeBack`, `placeBreadcrumb`, `appendTabButtons`, server-side label clipping, and `NativeMenuScreen` (cards, bars, dots, the Close spot, swatches). It reads titles and parents straight from `PageRegistry.java`. It also writes `findings/layout-findings.md`. |
| `tools/specs/*.json` | **Generated** GuiKit specs, one file per system group, pulled together by `tools/guikit.json` through `include`. Do not hand-edit them. |
| `gen/from_crawl.py` | Turns a real crawl's `uiprobe-packets.jsonl` (the packets the server sent, geometry as-is) into `tools/real/guikit-crawl.json` and, with `--guikit`, runs GuiKit on it and writes `findings/real-crawl-findings.md`. Run: `python gen/from_crawl.py [packets.jsonl] --guikit` (about 4 minutes). |
| `tools/real/guikit-real.json` | **Generated.** Every page exactly as LM lays it out today (fit floor 0.5x, no mock changes). `run.ps1` checks it and puts the failures into the findings. |
| `mockups/` | `index.html` plus one JPG per screen at 1920x1080, GUI 2, and GuiKit's `report.txt`. Every size and scale is still checked; only that one is kept as a picture. |
| `findings/layout-findings.md` | What the real Java gets wrong, with file/method, and each mockup change to apply. |
| `guikit-src/` | The GuiKit extension (the `"lm"` styles). It replaces mod-toolkit's copies. |

## The rule: the spec changes when the screen code changes

A page in `pages/` is a copy of a builder in `gui/ultra/Ultra*Screens.java` (its `source` field names the method).
When that Java changes (a label, a row, a tab, a new page, a PageRegistry title or parent), change the page in the
same commit, then run `.\run.ps1` and commit the regenerated `tools/specs`, `mockups` and `findings`.

When the renderer or the skins change, update the ports in the same commit:

- `client/NativeMenuScreen`, `MenuSkins`, `SaiyanRoyal` or `net/gui/NativeMenuFit` are ported in
  `guikit-src/fx/LmLook.java` and `GuiKit.java`;
- the `UltraScreenSpec` / `UltraStyle` metrics are ported in `gen/build_corpus.py`.

A new menu starts here: add its page, get GuiKit to exit 0 with no warnings, look at the mockup, then write the Java
from the page.

## What the mockups assume

- **Client and data.** An LM client with the navigation layer, so it gets breadcrumbs and a Back that names the page
  you came from (here, the registry parent). Data are realistic worst cases: 16-character names, 24-character dojo
  names, large numbers.
- **The LM 4.7.80 target state:**
  - Hub is Home | Goals | More, with no Search tab.
  - More has search, recents, meditation, Menu skins, the HUD toggle and Staff Admin.
  - Goals is the weekly goals board.
  - The Spar Board with no dojo offers "Open the Dojo" and "Find a dojo".
  - The Spar Board has a Wager sub-menu.
  - Dojo is Home | Upgrades | Wars; Sparring is Home | Leaderboard | Spar Board.
  - Founding is three steps plus a confirm page.
- **Target-state fixes on every page**, each listed in the findings:
  - the fit floor is 0.75x and the whole menu scrolls past that;
  - body text wraps to the real width;
  - the footer matches the column width;
  - a Notice line is not a card heading;
  - a picker uses the normal style, not green;
  - a page's opening line is a heading.
