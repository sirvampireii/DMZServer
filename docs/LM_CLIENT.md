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
