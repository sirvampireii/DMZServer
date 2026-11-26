# LM client (native menus, LM 4.7.58+)

> **LM 4.8.2: the LM client is mandatory and the KaiPath bridge is gone.** `requireLmClient` defaults to true, every
> LM menu goes to the LM client or nowhere, and the body-parts world sync is native message 5. The "Who sees what"
> table and the requireLmClient section below are current; KaiPath-bridge notes further down are historical.

Since 4.7.58 `LegacyMechanics-<version>.jar` is one jar for both sides. On the server it is the
whole mod, unchanged. On a game client it only draws LM menus natively; every server feature stays
off there, and it is optional while it is tested.

## Who sees what

| Player's client (4.8.2) | LM menus |
|---|---|
| LM client (with or without KaiPath) | Drawn by LM on `legacymechanics:native_ui` |
| KaiPath only, or neither | Turned away at login with the install message (`requireLmClient`, on by default). With it off: they join, and every menu answers with the same install message in chat |

Until 4.8.2 a KaiPath-only client got the menus over the KaiPath bridge (`legacymechanics:gui`); the rest of this
section describes that era. 4.8.2: a menu the LM client cannot fully draw is refused with "This menu needs a newer
Legacy Mechanics client. Update the mod, then join again."; a missing preview hint still opens natively.

A menu is sent on one channel only, never both (`LmGuiNetwork.sendOpen` → `LmNativeNetwork.route`).
With both mods installed the LM client wins, except when a menu needs something it does not draw;
that menu goes to KaiPath instead of rendering half-drawn. Today that is one case: a made-up preview
figure (Preview forms, the rival profile's "you" view) wearing a form or body pieces. The LM client
draws the viewer's own model and real players natively; made-up figures wear the viewer's skin but
not the form pieces. Without KaiPath such a page still opens natively with the base figure (the
protocol lets a client skip `parts.form` / `parts.bodyParts`). A menu with a widget the client cannot
draw at all, and no KaiPath, is refused with "This menu needs a newer Legacy Mechanics client."

## Protocol

- Channel `legacymechanics:native_ui`, Forge protocol `"1"`, optional on both sides
  (`acceptMissingOr`), so clients without the LM client and servers without LM connect as before.
- Message 0 (S→C): the menu, the same `GuiOpenPacket` bytes the bridge sends.
- Message 1 (C→S): a click, the same `GuiActionPacket` bytes; the server runs the same handler.
- Message 2 (C→S): hello, the LM client version and the JSON list of features it draws
  (`LmNativeProtocol.CLIENT_FEATURES`). Until it arrives the server assumes the protocol-1 list.
- Message 3 (S→C, 4.7.62): the player's menu look (`NativePrefsPacket`), only when the hello listed `menuSkins`.
- Message 4 (S→C, 4.7.72): one native HUD element (`NativeHudPacket`), only when the hello listed `hud`. See
  "Native HUD" below.
- Message 5 (S→C, 4.8.2): the equipped body-parts world sync (`GuiModelSyncPacket`, the bridge's old message 3),
  only when the hello listed `bodyPartsSync` (the client lists it when DMZUltra is installed). The LM client draws
  those pieces on players in the world (`FormPartsPreviewMixin`, `RaceTailSuppressMixin`).
- The server knows at login: the client's channel list from the Forge handshake.
- 4.8.2: the server no longer registers the KaiPath bridge (`legacymechanics:gui`). The LM client never
  registered it.

## Renderer

`client/NativeMenuScreen` draws the spec where the server laid it out (426 wide, 6 px gaps,
16 px buttons; centered, content-sized). The panel renders at native GUI size when it fits, scales
down uniformly to fit the window first (never under 0.5x, `NativeMenuFit`), and only below that
floor does the whole menu scroll. The staff config editor (`fillScreen`) scales its 426x240 box to
the window. Locked buttons (`nav:none`) are dim and send nothing. Spin off (`rotate:false`) puts the
model back facing forward. Close sits in the bottom 28 px band like KaiPath's.

4.7.62 menu skins: players pick a skin on Main menu > Menu skins (Saiyan Royal default, Namek Serenity,
Obsidian & Gold, Beerus Destruction, Zeno Omni). The choice, plus Backgrounds on/off and Look Full/Lite, is per
account (`lm_menu_prefs` in the player's LM record) and reaches this client on native message 3 (`NativePrefsPacket`),
sent only when the hello listed `menuSkins`.
- Every tone comes from `MenuSkins` (no hex in `NativeMenuScreen` or `NativeText`). Each skin is dark or light; inks
  are made readable on their surface (3:1 on dark skins, 4.5:1 on Zeno Omni, the light one).
- `MenuBackground` bakes each skin's themed background once per window size and the panel's drop shadow once per
  panel size; rounded shapes reuse cached geometry. Per frame the menu blits textures and draws cached shapes.
- Polish (transforms and opacity only): page crossfade and slide, hover lift and press sink, sliding tab indicator,
  smooth Momentum fill with a shimmer while time is banked, background crossfade on a skin switch, a skin-tinted glow
  on the active tab and confirm buttons, and a keyboard focus ring (Tab / Shift+Tab, Enter or Space).
- Lite: no ambient shadow, glow, sheen, shimmer or motion; a flat background; rows outside the view are skipped.
- A frame-rate sag (under 45 FPS over 3 seconds) shows a small notice suggesting Lite, once per session; it never
  switches anything itself.

4.7.61 Preview forms: Character Services > Preview forms is native. The made-up figure is a client-only
stand-in that wears the race and form the page names (`NativeFormLook` sets DragonMineZ race, form and stack
form), and its body parts join DMZUltra 0.5.7's look through the client-only mixins in
`legacymechanics.client.mixins.json` (`FormPartsPreviewMixin`, `RaceTailSuppressMixin`). They act only while
an LM menu draws its preview. The hello adds `combined.forms.v1` (DragonMineZ) and `bodyParts` (DMZUltra),
so a client missing either still gets the KaiPath page.

4.7.61 polish (client only, no wire change):
- A `footer:true` button the server put on the design box's edge (the config editor sends margin 0)
  moves into the bottom band, 10 px inside the right border. Close then takes the left end of the
  same row at the same height. KaiPath pins the same footer itself.
- A text field's grey hint is shortened with "..." to the field's inner width. The vanilla EditBox
  draws hints unclipped, so "Search by name" used to run under the Search button.
- The bridge darkens yellow and gold labels to dark gray (`§8`) for KaiPath's light button face. On a
  live (not dim, not locked) button, the native renderer draws that label in skin text, because dark
  gray is the locked look on the dark Saiyan Royal face.

## Native HUD (4.7.72)

Approved proposal: `docs/LM_NATIVE_HUD_PROPOSAL.md`. The server owns every element (`gui/ultra/LmHud`); the client
(`client/HudOverlay`) draws what message 4 says and nothing else.

| Element | Replaces (for HUD players only) | Where | Click (chat open) |
|---|---|---|---|
| Underdog pill | the Proving Grounds boss bar (`ProvingGroundsBar`) | top centre | Rival page |
| Rival Instinct cards | the "Instinct" chat lines (`RivalInstinct.deliver`) | right edge, 45% down, 3 at most, 4 s | that rival's profile |
| TP notice | the 3-second TP boss bar (`TpBossNotify`) | top centre, under the Underdog pill | none |
| Death TP penalty | the penalty boss bar (`DeathTpPenalty`) | top centre, last row | Difficulty |
| Momentum meter | new (the Difficulty menu's numbers) | above the hotbar, right of centre | Difficulty |
| Fight pill (4.7.75) | rival challenge score and "Too far apart" lines, the spectator line; new for spar sessions and war bouts | top centre, under the Underdog pill | Rival / Sparring |
| Spar chip (4.7.75) | "Perfect Training active", "Combo broken", the recover line | bottom-right row, above Momentum | Sparring |
| Potential meter (4.7.75) | the Potential subtitles ("Switch training methods", "Move at least") | bottom-right row | none |
| Coin toast (4.7.75) | "Dropped X Ancient Coins" per kill, summed over 5 s | right edge, above the cards | none |
| Elite chip (4.7.75) | the title sense chat line every 45 s | top centre | none |
| Boost chip (4.7.75) | new: global TP / mastery boosts with minutes left (purchase and end broadcasts stay chat) | top centre | Prestige |

- Who gets it: `LmHud.active` = the hello listed `hud` and the player's HUD switch is on (More tab, `lm_hud` in the
  player's LM record, default on). Everyone else keeps the boss bars and chat lines exactly as before.
- Same text and numbers: the HUD lines are the bar or chat strings (`ProvingGroundsBar.barParts` / `hudLines`,
  `DeathTpPenalty.barText`, the Instinct chat body, the TP notice text); the bar fraction is the boss bar's.
- Sends only on change (`LmHud.show` keeps each element's last packet per player); Instinct cards and TP notices are
  events and always go. A TP notice is one message: the client drains its bar over 3 s and hides it.
- Momentum shows while time is banked and the player hit or was hit by a difficulty mob in the last 10 s
  (`Momentum.markFight` from the DMZ hit handler, `Momentum.hudPulse` once a second).
- Drawing: each element's chrome (rounded panel in the skin's panel colour, accent edge and left strip; soft shadow in
  Full, none in Lite) is baked once per size, GUI scale, skin and accent into a texture (`HudOverlay.HudChrome`, LRU
  of 24, released on logout). Text is laid out when a message arrives or the skin changes. A frame blits, draws the
  laid-out text and fills the bar: no allocation.
- Clicks: while the chat screen is open, an element with a page under the mouse shows a hand cursor; a click sends its
  action on `hub` like a menu button. Controls > Legacy Mechanics > "Open the newest HUD page" (unbound by default)
  opens the newest element's page.
- F1 hides it with the rest of the HUD.
- 4.7.73 placement: a GUI overlay just under chat; the top stack starts below any boss bar and stays clear of the
  status-effect icons; the bottom row (Momentum, spar chip, Potential) sits above the action bar and the right-hand
  status rows; cards stop above the bottom row. With everything up on a small window, elements that do not fit are
  skipped, never overlapped (the probe's layout verdict checks this at every GUI size).

## Server config

`requireLmClient` (in `legacymechanics.json`, default **true** since 4.8.2): a player who joins
without the LM client is disconnected with "This server needs the Legacy Mechanics client mod. Add
LegacyMechanics-<version>.jar to your mods folder, then join again." The owner can turn it off; such
players then join, but no LM menu opens for them (each answers with the same message in chat), and
HUD notices reach them as boss bars and chat lines.

## Build and isolation

`build.sh` compiles the server classes first (without `client/`), then `client/` against the
Forge 1.20.1-47.4.10 client jars (CurseForge launcher libraries, or `CLIENT_LIBS`). A server class
cannot name a client class (it would not compile), and the packaging check fails if any class
outside `client/` references the client package. `AdaptiveDifficultyMod` starts `client.LmClient`
by name, only on a physical client. Boot self-test: `LmNativeSelfTest` (channel, codecs, routing,
scale-to-fit, every builder packet drawable).

## Saiyan Royal skin (4.7.60, every menu)

Defined once in `gui/ultra/SaiyanRoyal.java`; both render paths read it. Hex is the source of truth.

| Name | Hex | Native LM client | KaiPath bridge (same letters as 4.7.57) |
|---|---|---|---|
| Panel | `#160b0d` | Panel fill | Not expressible; KaiPath draws its dark default |
| Crimson | `#e23b3b` | Difficulty's accent, danger edges, red text | Difficulty accent `c` (red) |
| Gold trim | `#f0b429` | Title, headers, 1px panel border, divider, bar fill, done dots; the accent on menus without one | Headers `6` + bold (gold) |
| Text | `#f6e8e4` | Body text and values | Values `f` (white); labels stay `7` (gray) |

- **Which screens:** every LM menu in the native renderer. The shared chrome (panel, gold trim, text) is the
  skin everywhere; each system keeps its own accent on button edges, the active tab and frames, in exact hex:
  Difficulty crimson `#e23b3b`, Rival white, Sparring dark aqua `#00aaaa`, Prestige aqua `#55ffff`, Character
  green `#55ff55`, SkillCheck yellow `#ffff55`, Admin and config dark red `#aa0000`. Main-menu tiles take their
  system's accent.
- **KaiPath bridge:** unchanged bytes on every menu: headers gold bold, labels gray, values white, accents as
  4.7.57. The panel tint and round corners cannot be sent; KaiPath keeps its square dark default.
- **Native shapes:** rounded panel (14px, as the Difficulty Menu Concept mock) with a soft shadow, rounded buttons and tier rows (8px, never more
  than half the height), rounded Momentum bar, round progress dots, rounded scroll bar. Corners are cut at a
  quarter-pixel step so they stay smooth at GUI scale 3 and 4. Confirm stays green, danger red, dim and locked
  dark gray.
- **Depth (native only):** the panel lifts off the background on two shadow layers (wide ambient, tight key)
  and is lighter at the top. Raised buttons and tier rows carry their own shadow, a face lighter at the top, a
  top-edge highlight and a darker bottom edge. While held they sink 2px and lose the shadow. Dim and locked
  rows stay flat. The body text, fields, paged books and bar tracks sit in inset boxes with an inner shadow
  (the body box is skipped when a control would sit under it). The Momentum bar fills with a glossy gold
  gradient in a recessed track. The active tab is raised 1px with a gold glow.
- **Clicks:** only the drawn shape rounds or moves. Every click area is still the full rectangle from the server
  layout.
- **Self-test:** `SaiyanRoyal.selfTest()` runs at server boot (strict mode throws). It pins the hex values,
  the bridge letters and native accent hex for every system, the corner math and the bar and dots readers. Move
  a constant on purpose: update the self-test and the `§lm 4.7.60` ship-manifest check together.

## Model preview spin stop (4.7.60)

A menu with `playerModel.rotate:false` shows the model front-facing: the character's front toward the viewer,
never the back, whatever angle the spin had reached. `LmNativeProtocol.PREVIEW_FRONT_YAW` (body yaw 180 under
the in-GUI renderer's roll, as the vanilla inventory draws), `spinAfterPacket` and `previewFacing` name it; the
native self-test stops a back-facing spin and expects the front. KaiPath's side of the same rule is the kit's
`PreviewFacing`.

## Test rig

`tools/lm-ui-probe/` is the in-game test rig for every LM menu: crawl, screenshots at GUI scale 2/3/4, scripted
checks, layout and skin analyzers. See its README; keep it current with every menu change.
