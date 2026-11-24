# Dojo visuals (LM 4.7.68)

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
   pass, and renders the pictures with BlockRender. It builds a dojo from its state, the one LM keeps (rings, each
   room's level, the style), so a new dojo, a grown one and every upgrade in between come from the same code.
   `DojoKit upgrade <state> <upgrade>` renders what one upgrade adds: Now, After and Where (see below).
3. **BuildLint** checks every structure that ships (`buildlint.txt`).
4. **The menu mockups come from the real LM client**, in the current look (Saiyan Royal, the Sparring accent). The
   UI probe's mock mode (`tools/lm-ui-probe`, "Menu mockups" in its README) builds the proposed pages with LM's own
   screen-spec API, the real client draws them, and the rig shoots each one at GUI scales 2, 3 and 4.
   `mock_compose.py` then puts DojoKit's pictures into the room the pages leave for them. Those pictures are the one
   new menu element; everything else in the shots is the client as it is today.

The structures are committed to `tools/dmz-adaptive-difficulty/src/main/resources/data/legacymechanics/structures/dojo/`:

- `dojo_start.nbt`: a new dojo. The hall is empty except the free sparring mat (ring 1); no style yet, so no banners.
- `dojo_<style>_t<tier>.nbt`: a dojo with every upgrade at one tier, per style and tier, 12 in all.

The bare hall shells are build inputs only (`build/dojo/hall_t*.nbt`).

In game they load as `legacymechanics:dojo/...` through `StructureTemplate`.

## In the game (Phase 2, after approval)

- `DojoKit export` writes what LM ships: per hall tier and style the empty hall and a piece per ring, room level,
  balcony level and garden level, the plot index, and the menu pictures. `DojoPlacer` places them in the dojo world the
  way DragonMineZ places its tournament grounds (chunk tickets, a time budget per tick, checks when done). A purchase
  rebuilds only its part; a new hall tier or style rebuilds the plot.
- The menus are the ones on this page: the main menu's Spar Board and My dojo tiles, the Dojo tab and the upgrade
  list with the hall's floor plan, and each upgrade with Now, After and Where. KaiPath players get a text map of the
  hall in the same place.

## The dojo is one Japanese hall, and it starts empty

Every room is inside the hall. Only the garden and grounds are outside. A new dojo is the hall, empty except the free
sparring mat (`plan_start.jpg`, `outside_start.jpg`); each upgrade builds its part into it:

- **Sparring arenas:** up to six rings in two rows of three on the hall floor, 17 x 17 each. Space for a ring you
  haven't bought is plain floor. The hall's look follows the rings, like LM's look tier: 1-2 rings basic, 3-4 refined,
  5-6 masterwork.
- **Spectator balcony:** a mezzanine along the north wall with a rail, benches and stairs at the west end.
- **Training hall, meditation room, trophy hall, master quarters:** rooms in the south wing. A room appears when it is
  bought (shoji walls, a sliding-door gap from the hall floor) and gains something at each level: the training hall
  more dummies, drill pieces, then mats and banners; the meditation room a gravel garden with stones, then a water
  basin and bamboo; the trophy hall better pedestals and banners; the quarters a desk, more shelves, then rugs.
- **Garden grounds:** the path is always there. Level 1 adds stone lanterns, 2 trees, 3 cherry trees and a torii.
- **The entrance porch** with the main doors, and a noren in the style's colour once a style is picked.

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
- `*_start.jpg`: a new dojo (plan, outside, front, night, the hall floor).
- `upgrade_<name>_now.jpg`, `_after.jpg`, `_map.jpg`: what one upgrade adds. Now and After are close-ups of that part
  of the dojo with the roof off; Where is the floor plan with the part in green. From a new dojo: `ring2`,
  `training1`, `balcony1`, `garden1`; from a grown Ki Masters dojo (4 rings, every room at level 1): `ring5` (the hall
  becomes masterwork) and `meditation2`.
- The menus at GUI scale 3, drawn by the real LM client (`menu_<page>_gs2.png` and `_gs4.png` are the other scales):
  - `menu_main_menu.png`: the main menu with the Spar Board and My dojo tiles.
  - `menu_spar_board.png`: the Spar Board with posts.
  - `menu_my_dojo.png`: My dojo for a new dojo, with its floor plan.
  - `menu_upgrades.png`: the upgrade list with LM's names and costs, and the plan.
  - `menu_upgrade_ring2.png`, `menu_upgrade_training1.png`: one upgrade, with Now, After and Where above the cost
    and the Buy button.
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
