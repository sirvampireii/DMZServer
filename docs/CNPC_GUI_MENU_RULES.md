# Legacy Mechanics menu rules (CNPC + chest/CMI)

Automated checks: `python3 tools/dmz-adaptive-difficulty/sim/audit_cnpc_gui_style.py` (also runs from `build.sh`).

## Layout

- **Header:** `CnpcGuiSupport.paintHeader` — not legacy `title()`.
- **Body start:** `bodyBelowHeader(infoY)` or `bodyBelowInfo(...)` after status blocks.
- **Left column width:** `listWidth()` / `textBandWidth()` — never `W - 2*M` (overlaps preview).
- **Character preview:** `paintSystemMainPreview` only on each **system main** page (`footer` when `parentPage == null`, or hub/skill-check core main).

## Scroll (CustomNPCs single panel)

- Long status on **button-only** pages: `paintInfoBlock` (wheel on status band **only when** lines exceed inline max).
- Status **above a pick list:** `paintInfoBeforePickList` then `scrollPickList` — never `paintInfoBlock` + `scrollPickList` on the same scroll panel.
- Pick lists: `scrollPickList` only (not raw `scrollSearchable` in menu classes).
- **Hints:** `HINT_PICK_LIST` appears only when list rows overflow the visible band; pick lists scroll via **search + scrollbar drag** (not the status-band wheel). Head bones: 14 cards/page, single-click row to unlock/equip.

## Toggles

- On/off controls: `CnpcGuiStyle.toggleOn` / `toggleOff` (`§2§lON` / `§8§lOFF` prefix) so state is obvious at a glance.
- Flag grids: use `flagOnOff` / `startsWith("§a")`, not `contains("ON")`.

## Forge-only GUI (no Bukkit plugin)

- `GuiBackend.fromConfig()` defaults to **CNPC**; legacy `cmi` / `chest` / `bukkit` config values map to CNPC.
- Player `*Menu.java` classes open `CnpcLmGui` — not `CmiGuiBridge`. Audit: `sim/audit_forge_gui_backend.py`.

## Copy

- Shared hints: `CnpcGuiStyle.HINT_*` (avoid raw “Click to …” in `paintHeader` subtitles).
- Disabled rivals/spar: `CnpcGuiStyle.MSG_RIVALS_OFF` / `MSG_SPAR_OFF`.
- Separators: `CnpcGuiStyle.SEP` (` §8· `).

## Navigation

Retired CNPC menus used `navSystemRoot` and `navSubmenu`; nothing calls them now. Ultra menus follow the rule below:
`UltraScreenSpec.back()` reads the page registry ("Back to X" on the left, "Main menu" on the right; a system Home
shows one "Back to main menu"). Never "Hub".

## Bukkit chest / CMI

- Button copy: `gui-tooltips.json` keys referenced from Java (`tipBtn` / `GuiTooltips`).
- Policy: `tools/dmz-adaptive-difficulty-gui/sim/gui_tooltip_policy.py` — no banned jargon (telemetry, deep-link, etc.) in player-facing strings.

## Build

- CNPC Java overlays merge onto `base/LegacyMechanics-4.5.49-consolidated.jar`; ki/stamina mixin wiring stays on the base jar (see `base/README.md`).

## Ultra menus: button counts, Back wording, styles (owner rulings, LM 4.7.56)

- **Six action buttons per page.** A page shows at most six action buttons. Two kinds of rows
  are exempt by design and do not count:
  - **List rows / cards**: one button per entry of a list (the Tiers list, Titles, race, class,
    rival and name pickers, the staff Hub tiles). Long lists page with Prev / Next instead.
  - **Navigation rows**: a row of section links that acts like a tab bar (the Rival Records row:
    History, Stats, Season, Quests, Achievements, Hall of fame, Journal, Title).
- **Back wording.** Footers read `Back to <page>` on the left and `Main menu` on the right. The
  live `Back to ...` wording is the rule (CLAUDE.md); menus never use `<< Back` or `Hub`.
- **Status words on list buttons** read `Name - Status` with a capitalised word (`T4 Frost -
  Locked`, `Classic - Done`, `Ultra Instinct - Equipped`), like the `Label - ON/OFF` toggles. When
  that does not fit, the button shows the name alone; it never abbreviates the status.
- **Danger (red) style** is for destructive or irreversible actions only (Flush, Clear parts,
  Discard all, demote, remove). Navigation and read-only status buttons use the normal style.
- **Ask pages act on the first Confirm.** An ask page is the confirmation; its Confirm button
  never needs a second click elsewhere.
- **Locked actions stay visible (LM 4.7.57).** A button for something the player cannot do yet
  renders dim and does nothing when clicked (`UltraScreenSpec.lockedButton`, action `nav:none`);
  it is never hidden. The page text says what is missing. A dim button that opens a detail page
  (a locked tier on the Tiers list) is fine: the click shows why, it never acts.
- **Staff config editor exception.** The editor keeps its dense 15 px row step (13 px buttons,
  2 px apart) in its own full-screen 426x240 frame so a category fits on one page. Player menus
  keep the normal row gap.
