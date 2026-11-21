# LM client (native menus, LM 4.7.58+)

Since 4.7.58 `LegacyMechanics-<version>.jar` is one jar for both sides. On the server it is the
whole mod, unchanged. On a game client it only draws LM menus natively; every server feature stays
off there, and it is optional while it is tested.

## Who sees what

| Player's client | LM menus |
|---|---|
| LM client (with or without KaiPath) | Drawn by LM on `legacymechanics:native_ui` |
| KaiPath only | KaiPath bridge `legacymechanics:gui`, byte-identical to 4.7.57 |
| Neither | "Install KaiPath" message, as before (turned away when `requireLmClient` is on) |

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
- The server knows at login: the client's channel list from the Forge handshake.
- The KaiPath bridge is untouched (protocol `"1"`, no new fields). The LM client never registers
  `legacymechanics:gui`; KaiPath owns that name on a client.

## Renderer

`client/NativeMenuScreen` draws the spec where the server laid it out (426 wide, 6 px gaps,
16 px buttons; centered, content-sized). The panel renders at native GUI size when it fits, scales
down uniformly to fit the window first (never under 0.5x, `NativeMenuFit`), and only below that
floor does the whole menu scroll. The staff config editor (`fillScreen`) scales its 426x240 box to
the window. Locked buttons (`nav:none`) are dim and send nothing. Spin off (`rotate:false`) puts the
model back facing forward. Close sits in the bottom 28 px band like KaiPath's.

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

## Server config

`requireLmClient` (in `legacymechanics.json`, default **false**): when true, a player who joins
without the LM client is disconnected with "This server needs the Legacy Mechanics client mod. Add
LegacyMechanics-<version>.jar to your mods folder, then join again." Leave it off until the LM client
is in the modpack and tested.

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
