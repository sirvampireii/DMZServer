# Dojo visuals (LM 4.7.68, Phase 1: pictures for approval)

These pictures are for approval before any placement code is written. Every one is rendered from the same structure
files the game will place, so a built dojo looks like its picture.

## How they are made: the mod-toolkit, not a new generator

The dojo is authored with the owner's mod-toolkit (`Desktop/Repository/mod-toolkit`, plugged into LM by
`tools/dmz-adaptive-difficulty/tools/toolkit.json`). One command rebuilds everything:

```
bash tools/dmz-adaptive-difficulty/tools/dojo/make.sh          # or: make.sh ki_t2 for one style and tier
```

1. **BuildKit** builds each tier's hall shell from a recipe (`tools/dojo/hall_t1.json`, `hall_t2.json`,
   `hall_t3.json`) in the toolkit's `japanese` style. The tiers are style variants made with `"extends": "japanese"`.
   The shell has a foundation terrace with a veranda, the hall with shoji walls and dark posts, a flared hip roof
   (refined and masterwork add a ring eave round the hall), and an entrance porch with a gable roof.
2. **DojoKit** (`tools/fx/DojoKit.java`, LM's own toolkit class) fills the one gap BuildKit has no part for: the
   rooms inside one hall. It reads every position from the recipe's `"dojo"` section: ring anchors, the south-wing
   rooms, the balcony, the doors and the landing points. Phase 2's placement code reads the same section. DojoKit
   also places the style banners, the grounds (path, stone lanterns, SceneKit trees, torii) and a DetailKit weathering
   pass, and renders the pictures with BlockRender.
3. **BuildLint** checks every structure that ships (`buildlint.txt`).
4. **The menu mockups come from the real LM client**, in the current look (Saiyan Royal, the Sparring accent). The
   UI probe's mock mode (`tools/lm-ui-probe`, "Menu mockups" in its README) builds the proposed pages with LM's own
   screen-spec API, the real client draws them, and the rig shoots each one at GUI scales 2, 3 and 4.
   `mock_compose.py` then puts the floor-plan picture into the room the upgrade pages leave for it. That picture is
   the one new menu element; everything else in the shots is the client as it is today.

The structures are committed to `tools/dmz-adaptive-difficulty/src/main/resources/data/legacymechanics/structures/dojo/`:

- `hall_t1.nbt`, `hall_t2.nbt`, `hall_t3.nbt`: the bare shells.
- `dojo_<style>_t<tier>.nbt`: the whole plot for each style and tier, 12 in all.

In game they load as `legacymechanics:dojo/...` through `StructureTemplate`.

## The dojo is one Japanese hall

Every room is inside the hall. Only the garden and grounds are outside.

- **The hall floor:** six ring spaces in two rows of three, 17 x 17 each. A ring you haven't bought is plain tatami.
  Pillars with lights run between the rings, and lanterns hang on chains over each ring.
- **The spectator balcony:** a mezzanine along the north wall with a rail, benches and stairs at the west end.
- **The south wing:** behind a shoji partition, the training area, meditation room, trophy hall and master quarters,
  with a corridor from the main doors.
- **The entrance porch** with the main doors and a noren of the style's colour.
- **Outside:** a path with stone lanterns. Refined adds trees, and masterwork adds cherry trees and a torii.

The pictures show tier N with 2N rings and every room and the balcony at tier N.

## Tiers set the materials, styles set the accents

| Part | Basic | Refined | Masterwork |
|---|---|---|---|
| Foundation | Cobblestone | Stone bricks | Polished blackstone |
| Walls and posts | Birch walls, spruce frame | White shoji, dark oak frame | White shoji, vermilion (mangrove) frame |
| Roof | Wood, single eave | Deepslate tile, ring eave | Deepslate tile, ring eave |
| Floors | Oak | Spruce, tatami rooms | Dark oak, tatami rings |
| Grounds | Path and stone lanterns | Adds trees | Cherry trees and a torii |

| Style | Accent | Training area |
|---|---|---|
| Ki Masters | Sky blue and cyan, soul lanterns | Focus stones with end rods; a beacon at masterwork |
| Melee | Crimson | Straw dummies with arms, targets |
| Strike Moves | Orange and iron | Anvils on iron |
| Evasion | Green and lime | Bamboo poles, azalea |

A style shows in the banners round the hall, the noren, the ring borders and inlays, the hall banners, the cushions
and the training pieces. It never changes the building.

## Pictures

- `sheet_plan.jpg`: floor plans with the roof off, labeled. Rows are Ki Masters, Melee, Strike Moves and Evasion;
  columns are basic, refined and masterwork. Single images: `plan_<style>_t<tier>.jpg`.
- `sheet_arena.jpg`: the hall floor from the spectator balcony (`arena_*.jpg`).
- `sheet_training.jpg`: the training area from its door (`training_*.jpg`).
- `sheet_outside.jpg`, `sheet_front.jpg`, `sheet_night.jpg`: the plot from above, from the path, and at night.
- `menu_main_menu.png`, `menu_spar_board.png`, `menu_my_dojo.png`, `menu_upgrades.png`, `menu_upgrade_ring.png`:
  the menus at GUI scale 3, drawn by the real LM client. `menu_<page>_gs2.png` and `_gs4.png` are the other scales.
- `menu_plan_ki_t2.png` and `menu_plan_plain_ki_t2.png`: the upgrade pages' map, a BlockRender top view of the hall
  (with the ring being bought in green, and plain).
- `buildlint.txt`: BuildLint's findings for every structure.

## Navigation (3 taps or fewer)

The main menu's "Sparring & Dojo" tile becomes **Spar Board**, and "Dojo wars" becomes **My dojo** (wars move
inside it).

| From the main menu | Taps |
|---|---|
| Spar Board | 1 |
| Accept a post, post a spar, watch | 2 |
| Challenge a player | 3 (Spar Board, Challenge a player, pick) |
| My dojo | 1 |
| Visit dojo, Upgrades, Style and bonus, Wars, Members and elites, Browse dojos | 2 |
| Buy an upgrade, pick a style or bonus, declare or join a war, visit another dojo | 3 |
