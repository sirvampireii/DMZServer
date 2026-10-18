# Ultra GUI protocol (KaiPath)

**Audience:** any Forge mod that wants KaiPath to render server-driven menus — not only
LegacyMechanics (LM).

| | |
|--|--|
| Channel convention | `<modid>:gui` (e.g. `legacymechanics:gui`, `mymod:gui`) |
| Forge protocol string | `"1"` (`UltraGuiProtocol.PROTOCOL_VERSION`) |
| Message 0 | GuiOpenPacket (server → client) |
| Message 1 | GuiActionPacket (client → server) |
| Message 2 | GuiFeaturesPacket (client → server, **optional**) |
| Message 3 | GuiModelSyncPacket (server → client, **optional — `bodyPartsSync` gated**) |
| Client renderer | KaiPath (dynamic — renders the packet, no hardcoded menus) |

**Principle:** the client renders what the server sends. Packet codecs must not embed a
channel namespace or mod id. New menus are added on the **server mod** side only. Optional
fields (`style`, `activeTab`) are **data**, not new hardcoded screens.

**KaiPath 1.3.7** (CurseForge latest / vendored): decodes GuiOpen fields 1–10, honors
button `style`, `layoutJson.titleAlign`, `activeTab`, and `playerModel`, and advertises
those four via `GuiFeaturesPacket`. **Still missing:** field 11 `inputsJson` /
`textInput` — reference kit
(`tools/ultra-gui-kit/kaipath-client/`) has the merge; ship a rebuild past 1.3.7 to
unblock Weight shop text entry. LM **4.6.108+** logs missing optional features via
`GuiFeaturesPacket`. (CurseForge never published 1.3.5; jump was 1.3.1 → 1.3.7.)

Implement-yourself kit: [`tools/ultra-gui-kit/`](../tools/ultra-gui-kit/).
JLDK handoff: [`docs/KAIPATH_JLDK_CLIENT_BRIEF.md`](KAIPATH_JLDK_CLIENT_BRIEF.md).

---

## Client conformance contract (JLDK)

The client **must** render what the server sends (no hardcoded screen/tab/button lists).

| Capability | Required since | **1.3.7** released | Reference kit |
|------------|----------------|--------------------|---------------|
| Decode GuiOpen fields 1–10 | 1.3.1 | yes | yes |
| Dynamic title / tabs / lines / buttons | 1.3.1 | yes | yes |
| Verbatim GuiAction echo | 1.3.1 | yes | yes |
| Any `screenId` (no whitelist) | 1.3.1 | yes | yes |
| Honor **`layoutJson.titleAlign`** | 1.3.1+ | **yes** | yes |
| Honor **`activeTab`** highlight | 1.3.1+ | **yes** | yes |
| Honor button **`style`** | 1.3.7 | **yes** | yes |
| Honor **`layoutJson.playerModel`** (+ optional `parts`) | 1.3.7 | **yes** (entity) | yes (+ parts hook) |
| Honor **`inputsJson`** text fields (field 11) | next | **no** | **yes** |
| Decode GuiOpen strings as **UTF-8** | 1.3.1 | MC `readUtf` | **yes** (explicit) |
| Send **GuiFeaturesPacket** | 1.3.7 | **yes** (4 features) | **yes** (+ `textInput`) |
| `inputsJson` `slider` / `toggle` | later | — | reserved — see [`KAIPATH_BRIDGE_GAPS.md`](KAIPATH_BRIDGE_GAPS.md) |

### Button JSON schema

Every button object in `buttonsJson`:

| Field | Required | Type | Notes |
|-------|----------|------|--------|
| `id` | yes | string | Action id (echoed verbatim on click) |
| `label` | yes | string | Display text (may include `§` colors) |
| `style` | no | string | Visual role — see table below (default `normal`) |
| `x` | yes | number | Panel-relative X |
| `y` | yes | number | Panel-relative Y |
| `w` | yes | number | Width (minimum **80** — never shrink for long labels) |
| `h` | yes | number | Height |
| `pad` | no | number | Horizontal label padding (default **8**) |
| `footer` | no | boolean | When `true`, pin this button to the **panel footer** (does not scroll with body). Server places Y at the bottom band; clients must keep footer controls visible while body content scrolls above. |
| `sound` | no | string | Registered SoundEvent RL (`ns:path`) played on click. Also accepts `{"id":"ns:path"}`. Missing registry entry → silent no-op. LM **does not** emit (default click). |
| `buttonSkin` | no | string | Non-empty valid RL **enables** chrome button skin paint (KaiPath 1.3.13+). Client does **not** load a texture from this RL. LM **does not** emit — skinned path draws flat squares instead of default chrome. |
| `accent` | no | string | Single-letter color code (optional). LM does **not** emit per-button `accent` — title / activeTab use `layoutJson.accent` only. |
| `palette` | no | string | Named theme on a button (optional). LM leaves this off buttons — panel-level `layoutJson.palette` only. |
| `tint` | no | string | Optional body fill on a button. LM does **not** emit this; buttons use KaiPath default chrome. |
| `icon` | no | string | Optional mark beside the label. **Shipped on KaiPath 1.3.22** (`UltraBridgeIcon`). See [Icon values](#icon-values). Unknown or blank ids are ignored; the button still works. LM leaves this blank — system menus do not use vanilla item icons, and no custom gear textures ship with the client. |
| `args` | recommended | object | Opaque JSON echoed to the server |

```json
{
  "id": "@difficulty:do:activate:1",
  "label": "§aActivate T1",
  "style": "confirm",
  "x": 20,
  "y": 100,
  "w": 150,
  "h": 18,
  "pad": 8,
  "args": { "screen": "difficulty", "page": "tiers" }
}
```

Pinned footer pair (every non-hub page):

```json
[
  {
    "id": "@rival:nav:home",
    "label": "Back to Rival Home",
    "style": "dim",
    "x": 16,
    "y": 272,
    "w": 150,
    "h": 18,
    "footer": true
  },
  {
    "id": "@rival:nav:hub",
    "label": "Main menu",
    "style": "dim",
    "x": 174,
    "y": 272,
    "w": 150,
    "h": 18,
    "footer": true
  }
]
```

Advertise: `fixedFooter` (shipped on KaiPath **1.3.17**). Tabbed system pages emit
right-only `Main menu` (`nav:hub` — tabs are in-system nav). Sub-pages (no tab row) emit
contextual `Back to {destination}` (left) + `Main menu` (right). Never say "Hub" in
player-facing labels.

#### `style` values (client must implement)

| Value | Meaning | Visual expectation |
|-------|---------|-------------------|
| `normal` | Default button | Neutral / standard themed button |
| `confirm` | Safe / positive action | **Green** (or confirm theme) |
| `danger` | Destructive / irreversible | **Red** (or danger theme) |
| `dim` | Disabled / unavailable look | **Dark gray** / muted (still clickable unless server says otherwise) |

Unknown style values → fall back to `normal`. Missing `style` → `normal`.

#### Icon values

KaiPath **1.3.22** advertises feature id `icon` and resolves the string with `UltraBridgeIcon`. Two forms, both `namespace:path` (one colon):

| Form | Example | Client behavior |
|------|---------|-----------------|
| Item registry id | `minecraft:iron_sword` | Draws that item’s icon. The id must exist in the client item registry. |
| Texture path | `minecraft:textures/item/iron_sword.png` | Binds that texture (`modid:path/to/texture.png`). |

Blank, missing, or malformed values are omitted. An unknown id does not hide the button or tab. LM normalizes the string to lowercase before sending.

LM does not send `icon` on hub tiles or tab buttons. Vanilla items (swords, totems, stars) are not the gear marks those menus use, and the client has no per-system gear textures to bind. The field stays in the protocol so other mods can set it.

Any mod on the bridge may set `icon` on its own buttons and tabs. LM leaves `tabsJson` empty and paints tabs as buttons under the title. Partners that use the tab strip put the same field on each `tabsJson` entry.

KaiPath **1.3.1** ignores `style`: all buttons render identically (no green/red/dim
distinction).

### `activeTab` (GuiOpenPacket field 5)

| | |
|--|--|
| Wire field | #5 `activeTab` (string) |
| Meaning | Id of the **currently open** tab |
| Client must | Visually distinguish that tab from the others (highlight / selected state) |

`tabsJson` entries use `{id,label}` and may include optional `icon` (item id or texture path — same rules as buttons; **shipped on KaiPath 1.3.22**). Match `activeTab` against the tab’s **unqualified**
key when the server sends keys like `tiers`, or against the full `id` when that is what
`activeTab` contains. LM builders set `activeTab` to the short key (e.g. `home`, `tiers`).

KaiPath **1.3.1** ignores `activeTab`: the active tab is not highlighted.

### GuiFeaturesPacket (C→S) — message 2

Optional capability advertisement. **Backward compatible:** a client that never sends it
is treated as supporting **nothing beyond the 1.3.1 baseline** (dynamic UI, but no
`style` / `activeTab`).

| # | Field | Type | Notes |
|---|--------|------|--------|
| 1 | `featuresJson` | string | JSON **array** of feature ids |

Known optional feature ids:

| Id | Means the client honors… |
|----|--------------------------|
| `style` | Button `style` values above |
| `activeTab` | GuiOpenPacket field 5 highlight |
| `titleAlign` | `layoutJson.titleAlign` (`center` / `left`) |
| `playerModel` | `layoutJson.playerModel` entity preview, including `subject` (`local` or a player UUID) |
| `textInput` | GuiOpenPacket field 11 `inputsJson` text fields + `bindInput` |
| `headerBottom` | `layoutJson.headerBottom` / `tabsUnderTitle` |
| `accent` | `layoutJson.accent` + button/tab `accent` |
| `palette` | Named palette themes (also drives title tint) |
| `sound` | Button `sound` click RL |
| `buttonSkin` | Button `buttonSkin` chrome |
| `frame` | `layoutJson.frame` panel chrome |
| `pagedBook` | Advertise spelling (camelCase) for `inputsJson` **`type=paged_book`** (snake_case on the wire) |
| `portraitFrame` | `playerModel.portraitFrame` enable token (**advertised on 1.3.17**) |
| `fixedFooter` | Button `footer:true` pinned chrome (**shipped on KaiPath 1.3.17**) |
| `combined.forms.v1` | One preview combines the base entity with optional `playerModel.parts.headBones` and `playerModel.parts.form` string ids. The strings are generic (race, group, form, stack). Any mod may send its own ids. A client that lacks the feature still draws the entity and ignores `parts.form`. |
| `icon` | Optional button/tab `icon` (item id or texture path). **Advertised by KaiPath 1.3.22.** A client that omits it is `missingShipped=icon`, not `pendingNext`. |
| `bodyParts` | `playerModel.parts.bodyParts` — explicit pieces (`id`, `source`, `slot`) attached to that same preview. Any mod may send its own ids. A client that lacks the feature still draws the entity and ignores the array. Unknown ids are skipped. **Advertised by KaiPath 1.3.29.** A client that omits it is `missingShipped=bodyParts`, not `pendingNext`. LM sends worn form pieces plus the player's Body Parts Shop pieces. |
| `bodyPartsSync` | **Message 3** `GuiModelSyncPacket` — authoritative equipped body-parts state for *world* rendering (outside menus). **Not shipped on any KaiPath yet** (pending on JLDK — `pendingNext=bodyPartsSync`). LM only sends message 3 to clients that advertise this id; a client that never registered message 3 must never advertise it (unregistered message id drops the connection). See [`#GuiModelSyncPacket`](#guimodelsyncpacket-s-client--message-3). |

Advertise (KaiPath **1.3.29** — seventeen ids; the first fifteen shipped on 1.3.17, `icon` on 1.3.22, `bodyParts` on 1.3.29; see [`KAIPATH_BRIDGE_GAPS.md`](KAIPATH_BRIDGE_GAPS.md)):

```json
["style","activeTab","titleAlign","playerModel","textInput","headerBottom","accent","palette","sound","buttonSkin","frame","pagedBook","portraitFrame","fixedFooter","combined.forms.v1","icon","bodyParts"]
```

`icon` is no longer pending. Stock 1.3.17 stops before it and is reported as a shipped-client gap. The reference kit (`tools/ultra-gui-kit/`) also advertises `"bodyParts"` after `icon`, and official KaiPath 1.3.29 does too. A missing `bodyParts` advertisement (pre-1.3.29 clients) is now a shipped-client warning (`missingShipped=bodyParts`). Clients that never advertise `bodyParts` still draw the base entity.

**Spelling split:** GuiFeatures advertise uses camelCase **`pagedBook`**; the widget `type` string is snake_case **`paged_book`**.

Send once after channel registration (client setup / first join). Extra ids are ignored.
Extra wire fields after #1 are ignored.

LM logs a **warning** when a player’s feature set lacks expected optionals, naming:

- player
- KaiPath version if known
- missing features
- player-visible impact (e.g. “all buttons render identically…” / “active tab is not highlighted”)

`/lm admin guitest` includes the reported features in staff chat and the open packet body.

### GuiModelSyncPacket (S→C) — message 3

Authoritative sync of a player's equipped body parts for **world rendering** (the model the
player walks around with), as opposed to the menu preview in `layoutJson.playerModel.parts`.
LM ≥ **4.6.209** sends it; the client side is **pending on JLDK** (advertise `bodyPartsSync`).

| | |
|--|--|
| Wire id | `<modid>:gui` message **3** (`UltraGuiProtocol.MSG_MODEL_SYNC`) |
| Direction | server → client |
| Feature gate | **Only sent to clients that advertised `bodyPartsSync`** via message 2. Clients that never registered message 3 (KaiPath ≤ 1.3.29) must not advertise it — an unregistered message id aborts the connection. `LmGuiNetwork.supportsModelSync` / `sendModelSync` enforce this server-side. |
| Trigger: login | Once per session, ~40 ticks after the client's first `bodyPartsSync` advertisement (deferred so DMZ character data — race tail, forms — has loaded). Covers the initial state **plus a backfill**: every other online player's snapshot is sent to the new viewer, because their track-start events fired before the advertisement reached the server and were gated off. |
| Trigger: equip/unequip | Every Body Parts Shop change (`unlock`/`toggle`/`clear`) and every head-bone equip change — sent right after the server persists the change. |
| Trigger: track start | When a player starts seeing another (`PlayerEvent.StartTracking`): login into range, respawn, dimension change, walking back into range. `reason: track`, sent **to that tracker only** — re-broadcasting would spam every other viewer. |
| Trigger: logout | Client drops its cached state for the player on disconnect (state is per-session; the next login re-syncs). |
| Delivery | Equip/login sends **broadcast** to the owner and every tracker (`TRACKING_ENTITY_AND_SELF`), because each snapshot carries `subject uuid:<owner-uuid>` — every receiver binds it to the owner's entity. Only the track-start trigger and the login backfill are single-viewer sends. |
| Server-authoritative | The client **replaces** (never merges) its cached parts for the named `subject` with each received snapshot. An empty `bodyParts` array clears all extra pieces. No client-side guessing. A uuid subject that resolves to the receiver itself feeds its local state. |

Wire format (append-compatible — unknown trailing fields ignored):

| # | Field | Type | Notes |
|---|--------|------|--------|
| 1 | `partsJson` | string | The playerModel **parts object** — byte-identical schema to `layoutJson.playerModel.parts` (see [playerModel parts schema](#4-layout-json)). Always valid JSON, ≤ 8192 chars (oversized payloads drop `headBones`, then `form`, then degrade to a minimal empty-parts object — never a mid-JSON cut; the degraded object **keeps the original `subject`**, or an observer would clear its own parts on a broadcast). |
| 2 | `reason` | string | Trigger token: `login` / `equip` / `unequip` / `clear` / `track`. **Diagnostics only** — rendering must not depend on it. |

`partsJson` field reference (same as the menu preview's `parts` — menu and world rendering
read identical data):

| Field | Type | Meaning |
|-------|------|---------|
| `schema` | int | Parts schema version (`1`) |
| `subject` | string | **`uuid:<owner-uuid>`** on this message — the sync is broadcast to the owner and every tracker, so each receiver binds the snapshot to the owner's entity (a `local` subject would draw the owner's parts on every observer). A receiver whose own UUID matches the subject treats it as its own state. The menu preview (`layoutJson.playerModel.parts`) still uses `local` / bare UUID as documented below. |
| `headBones` | object? | `{token, pieces[]}` DMZUltra head-bone state; absent when none |
| `form` | object? | `{race, group, form, stackGroup, stackForm}` worn-form identity; absent in base |
| `bodyParts` | array | Explicit pieces `{id, source, slot}` — **always present** (empty array = no extras). Slot ids: `tail`, `wings`, `horns`, `ears`, `extra_arms`, `body`. LM guarantees the **single-tail rule**: the array never carries two `tail`-slot entries — a form tail beats a shop tail, and the natural `saiyan_tail` is only sent when no other tail slot is occupied. |
| `colors` | object? | Explicit model colors — see [playerModel.parts.colors](#playermodelpartscolors). Prefer these over a reflective `FormParts$Look` read, which comes back gray for a **remote** player. |
| `extras` | array | Open extension slot — always present, iterate without null checks |

Sample payload (Saiyan with the natural tail, no shop pieces, base form):

```json
{"schema":1,"subject":"uuid:0f8b4c1e-9a2d-4e6f-8c3a-1b2d3e4f5a6b","bodyParts":[{"id":"saiyan_tail","source":"dmzultra","slot":"tail"}],"colors":{"skin":16777215,"second":16777215,"hair":2105380,"own":4870248,"trim":15320170,"horn":15722191,"light":16777215},"extras":[]}
```

After `/bodyparts clear` the server sends the authoritative empty state:

```json
{"schema":1,"subject":"uuid:0f8b4c1e-9a2d-4e6f-8c3a-1b2d3e4f5a6b","bodyParts":[],"extras":[]}
```

Client contract (JLDK):

1. Advertise `bodyPartsSync` in `GuiFeaturesPacket` **only** if message 3 is registered.
2. On receive: parse `partsJson` with the existing parts pipeline (`BodyPartWire` /
   `BodyPartPreview`) and **replace** the cached world-render parts **for the player named
   by `subject`** — `uuid:<id>` keys the cache by that player; when the id is the receiver's
   own UUID, feed the local state too (the server broadcasts one payload to owner + trackers).
3. Apply to the world model via the same merge used by `FormPartsPreviewMixin`
   (`FormParts.of` + cached meshes) — the menu preview path is unchanged. When `colors` is
   present, **prefer its values over the reflective `FormParts$Look` read**: for a remote
   player that read resolves against the observer's own capability data and comes back gray
   (`0x808080` matches none of the fallbacks).
4. Drop the cache on disconnect / dimension change reset if you key by session.

---

## For other mod developers

### 1. Channel naming

```java
new ResourceLocation(YOUR_MOD_ID, "gui")  // → "mymod:gui"
```

Protocol `"1"`. Message 0 open, 1 action, 2 features (optional).

KaiPath discovers partner mods. It always seeds `legacymechanics`, then:

1. Every jar under `mods/` that contains `META-INF/kaipath-ultra.txt`. The first non-comment line is the Forge mod id. An empty marker falls back to `modId` in `META-INF/mods.toml`.
2. Extra ids in `config/kaipath-ultra-partners.txt` (one id per line, `#` comments).

A new server mod does not need a KaiPath release. Copy [`tools/ultra-gui-kit/server/META-INF/kaipath-ultra.txt.example`](../tools/ultra-gui-kit/server/META-INF/kaipath-ultra.txt.example) into your jar as `META-INF/kaipath-ultra.txt`.

### 2. GuiOpenPacket (S→C) — 11 fields

| # | Field | Type | Notes |
|---|--------|------|--------|
| 1 | `screenId` | string | Any string (not a whitelist) |
| 2 | `title` | string | May include `§` colors |
| 3 | `pageId` | string | Page within the system |
| 4 | `tabsJson` | string | JSON array of `{id,label}` |
| 5 | `activeTab` | string | **Active tab key — client must highlight** |
| 6 | `linesJson` | string | JSON array of body lines |
| 7 | `buttonsJson` | string | JSON array of button objects (incl. **style**) |
| 8 | `panelWidth` | varint | Default **350** |
| 9 | `panelHeight` | varint | Default **300** (grows toward **420** when needed) |
| 10 | `layoutJson` | string | JSON object (panel + body/button metrics + optional `playerModel`) |
| 11 | `inputsJson` | string | JSON array of input elements (`type=text` / `type=paged_book`); default `[]` |

Missing trailing fields → defaults. Bytes after the last understood field ignored.

### 3. GuiActionPacket (C→S) — 3 fields

| # | Field | Type | Notes |
|---|--------|------|--------|
| 1 | `screenId` | string | Echo open `screenId` verbatim |
| 2 | `actionId` | string | Echo button/tab/input `id` verbatim |
| 3 | `argsJson` | string | Echo button `args` (+ `value` when submitting a text field) |

#### `inputsJson` (field 11)

```json
[{
  "id": "@config:input:difficulty|some.path",
  "type": "text",
  "x": 16, "y": 200, "w": 318, "h": 18,
  "value": "current",
  "placeholder": "Type a value…",
  "maxLength": 128,
  "args": { "screen": "config", "page": "detail:…", "system": "config" }
}]
```

| `type` | Status | Notes |
|--------|--------|-------|
| `text` | **Shipped** (LM 4.6.108+) | Config TEXT / NUMBER / ENUM detail editors |
| `paged_book` | **Shipped** (LM 4.6.153+ / KaiPath 1.3.13+) | Multi-page book widget. Advertise feature id is **`pagedBook`**. Shape: `{ "id", "type":"paged_book", "x","y","w","h", "pages":[{"title","lines":[…]}], "page":0, "args" }` |
| `toggle` | Reserved | Not on wire yet — BOOLEAN uses styled buttons |
| `slider` | Reserved | Not on wire yet — see [`KAIPATH_BRIDGE_GAPS.md`](KAIPATH_BRIDGE_GAPS.md) |

Buttons that apply a field include `args.bindInput` set to that input’s `id`. On click (or
Enter in the field), the client must set `args.value` to the typed string before sending
`GuiActionPacket`.

**UTF-8:** All GuiOpen string fields (title, lines, labels, placeholders, …) are encoded and
decoded by LM with explicit `StandardCharsets.UTF_8`. Clients must decode the same way;
platform-default charset causes mojibake on accents (é/à).

### 4. Layout JSON

```json
{
  "panelWidth": 350,
  "panelHeight": 300,
  "width": 350,
  "height": 300,
  "center": true,
  "margin": 10,
  "titleAlign": "center",
  "tabsUnderTitle": true,
  "headerBottom": 28,
  "accent": "c",
  "frame": "legacymechanics:panel",
  "lineHeight": 16,
  "lineGap": 8,
  "bodyTop": 42,
  "bodyPad": 10,
  "bodyMaxHeight": 140,
  "scrollBody": true,
  "buttonPad": 8,
  "buttonGap": 6,
  "buttonMinWidth": 80,
  "buttonHeight": 18,
  "playerModel": {
    "x": 248,
    "y": 60,
    "width": 86,
    "height": 120,
    "scale": 1.0,
    "rotate": true,
    "subject": "local",
    "parts": {
      "schema": 1,
      "subject": "local",
      "headBones": {
        "token": "hair+ears1+horns2",
        "pieces": ["hair", "ears1", "horns2"]
      },
      "form": {
        "race": "saiyan",
        "group": "super_saiyan",
        "form": "ssj",
        "stackGroup": "",
        "stackForm": ""
      },
      "bodyParts": [
        { "id": "saiyan_tail", "source": "dmzultra", "slot": "tail" }
      ],
      "extras": []
    }
  }
}
```

Panel at `((screenW - panelWidth) / 2, (screenH - panelHeight) / 2)` when `center` is true;
honor `margin`. Button `x`/`y` are panel-relative.

#### Fullscreen design box

The config editor sets `fillScreen` so the panel uses the whole GUI instead of the
compact 350x300 dialog. `designWidth` and `designHeight` are the coordinate space the
server laid out (currently **426x240**, the smallest auto GUI). Scale every button and
input `x`/`y`/`w`/`h`, and the header/body metrics, from that box onto the panel after
it has been inset by `margin`.

`panelWidth` and `panelHeight` are a separate clamp request (currently **1920x1080**),
with `margin` **0** and `center` **false**. Clients that do not know `fillScreen` keep
that rectangle and clamp it down to the window, which fills the GUI. Controls stay
inside the 426x240 design box so a 240-tall GUI does not clip them. The category rail
is **148** px wide inside that box.

```json
"fillScreen": true,
"panelWidth": 1920,
"panelHeight": 1080,
"margin": 0,
"center": false,
"designWidth": 426,
"designHeight": 240
```

#### Shared body / button metrics

| Field | Meaning |
|-------|---------|
| `titleAlign` | **`center`** (LM default) or `left` — **must** position the title; do not assume left |
| `tabsUnderTitle` | When `true`, LM placed tab controls as regular buttons under the header (not KaiPath's native title-chrome tab strip). Honor `headerBottom`. |
| `headerBottom` | **Panel-relative Y where client header chrome must end.** Title text + yellow divider stay **strictly above** this line. When `tabsUnderTitle` is true, LM places the tab row at this Y (default **28**); when false, default **42** (`bodyTop`). This is the geometry contract that keeps tabs under the yellow divider. |
| `accent` | Optional single-letter Minecraft color code (`c`, `5`, `6`, `b`, `a`, `e`, `4`, …) for **system accent**. Paint the title (and `activeTab` highlight) with this color. Do **not** expect section-sign in the title string — titles stay plain ASCII to avoid mojibake. Omit or ignore when blank. |
| `palette` | Optional named theme (`red`, `gold`, `aqua`, …). When set, overrides accent for title tint. |
| `frame` | Optional RL (`ns:path`). Non-empty valid RL **enables** framed panel chrome (tinted border). LM emits `legacymechanics:panel`. No texture bind from the RL. |
| `lineHeight` | Body line height in GUI pixels (do **not** squeeze below this) |
| `lineGap` | Extra gap between body lines |
| `bodyTop` | Panel-relative Y where the body/scroll region starts |
| `bodyPad` | Vertical padding inside the body scroll region |
| `bodyMaxHeight` | Vertical room for body text inside the panel (grows with `panelHeight`) |
| `scrollBody` | When `true`, long body content **scrolls** instead of compressing |
| `buttonPad` | Horizontal padding inside buttons (default **8**) |
| `buttonGap` | Vertical gap between button rows |
| `buttonMinWidth` | Minimum button width (**80**) — never shrink for long labels |
| `buttonHeight` | Standard button height |

#### Chrome RL fallback (KaiPath 1.3.13, verified)

These fields look like asset ids but KaiPath treats most as **enable tokens**:

| Field | Client behavior without the named asset / without DMZUltra |
|-------|------------------------------------------------------------|
| `sound` | Registry lookup; missing → **silent no-op** (no exception) |
| `buttonSkin` | Valid `ns:path` → boolean skin paint (GuiGraphics tint outline) |
| `frame` | Valid `ns:path` → boolean panel border paint |
| `portraitFrame` | Valid `ns:path` → boolean preview border paint |

LM therefore uses vanilla sound + `legacymechanics:` enable tokens so a KaiPath-only client never depends on DMZUltra being installed. (Server-side `mods/kaipath-*.jar` does not affect client rendering.)

#### Client-internal (not LM wire fields) — adoption eval post-4.6.153

KaiPath 1.3.13 computes these locally — **do not send** them from the server:

| Client field | Derived from | Adopt as LM wire? |
|--------------|--------------|-------------------|
| `titleTint` | `layoutJson.palette` / `layoutJson.accent` via `UltraBridgeChrome.tint` | **No** — already covered by `accent`/`palette` |
| `contentTop` | Panel Y + cursor after header/tabs | **No** — covered by `headerBottom` / `bodyTop` |
| `contentBottom` | Client band above pinned footer (1.3.17+) | **No** — derived from `footer:true` / `fixedFooter`, not a server wire field |
| `contentScroll` | Mouse-wheel offset inside the content band | **No** — client scroll state |
| `contentScale` | Superseded by `fillScreen` + `designWidth`/`designHeight` — the client derives `scale = panel / design` | **No** — **deprecated**: zero references jar-wide in KaiPath **1.3.31**; LM never sent it. Residual handling may be removed. |

LM already emits the geometry contract as `headerBottom`, `bodyTop`, `bodyMaxHeight`, `scrollBody`, and button `footer:true`. Adopting `contentTop`/`contentBottom`/`contentScroll` as wire fields would fight packing and duplicate the footer pin. KaiPath **1.3.17** honors `footer:true` / `fixedFooter` so the content band sits above the pinned footer.

Buttons carry per-button `pad` (same default) and optional **`accent`** for active-tab /
hub-tile highlight. LM does **not** emit per-button `palette` or `tint` — buttons use
KaiPath default chrome. Per-system color stays on **`layoutJson.palette`** (panel frame /
title). Labels stay plain ASCII. Overlong labels truncate with ASCII `...`; pages scroll
rather than squeezing buttons.

**System accent map (LM example — replace with your own letters):** Difficulty `c` · Rival `f` ·
Sparring `4` · Prestige `b` · Character `a` · Skill Check `e` · Staff Admin `4`. Main hub
omits `accent` (shared gold title chrome). Another mod picks its own letters; the protocol
only requires a single Minecraft color code.

#### `playerModel` (optional, client-rendered)

| Field | Type | Notes |
|-------|------|-------|
| `x`, `y` | number | Panel-relative origin |
| `width`, `height` | number | Bounds for the preview |
| `scale` | number | Entity scale (default `1.0`) |
| `rotate` | boolean | Slow turntable when `true` |
| `subject` | string | Who to draw. **`local`** (or omitted) is the player who has the screen open. Another player is their UUID (`550e8400-e29b-41d4-a716-446655440000`) or the same id as **`uuid:<uuid>`**. Any mod may set this. The client draws that player's model, skin, and synced parts. It must not substitute the viewer when the UUID is someone else. |
| `portraitFrame` | string | Optional RL; non-empty valid RL enables preview frame chrome. LM omits this so the model has no border. Advertised + honored on KaiPath **1.3.17**. |
| `parts` | object | Optional cosmetics snapshot for that subject — see below |

**Backward compatible:** clients that do not know `playerModel` ignore it; the rest of the
layout still works. Clients that draw `playerModel` but do not know `subject` keep drawing
the local player. KaiPath **1.3.20** honors `subject` for every partner mod on the bridge,
not only LegacyMechanics. The server declares position, size, who to draw, and (when
present) equipped custom parts. Screen builders opt in per screen (LM Character Services
uses it; others default off).

##### `playerModel.parts` (optional, schema 1)

Any mod may send this so the client can draw extra pieces on the same preview. Omit the
whole `parts` object when nothing extra is equipped. Clients that only honor bounds still
work; unknown sub-fields are ignored.

**LM example — replace with your own.** The `headBones` shape, the form ids, and the
`saiyan_tail` entry below are how LegacyMechanics fills the generic fields from DMZ.
Another mod omits `headBones` and sends its own `bodyParts` ids (any `source` mod id).
Do not treat those DMZ names as required protocol.

##### `combined.forms.v1`

Advertising `combined.forms.v1` means the client draws **one** preview that combines:

1. the base entity,
2. optional `parts.headBones`,
3. optional `parts.form` string ids (`race`, `group`, `form`, `stackGroup`, `stackForm`).

The strings are generic. LM fills them from DMZ transformations; another mod sends its own
ids. A client that does not advertise the feature still draws the entity and ignores
`parts.form`. This is not a second storage map and not a DMZ-only channel.

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `schema` | number | yes | **`1`** for this document. Bump only on breaking shape changes. |
| `subject` | string | no | Same token as top-level `playerModel.subject`. Use it when the cosmetics snapshot and the render target travel together. Top-level `subject` wins when both are set. **`local`** is the viewing player. A UUID or **`uuid:<uuid>`** is another player. Message 3 (world sync) always ships `uuid:<owner-uuid>`. |
| `headBones` | object | no | Equipped DMZ `activeHeadBone` cosmetics. Omit when empty. |
| `headBones.token` | string | yes* | Authoritative DMZ token (`+`-joined). Same string DMZ stores on the character. |
| `headBones.pieces` | string[] | yes* | Atomic bone ids split from the token (stable shop order when known). Prefer this list when iterating geo bones. |
| `form` | object | no | Worn identity string ids. LM fills these from DMZ so a client can attach form pieces. Another mod sends its own ids. Omit when fully base. |
| `form.race` | string | no | Race id (lowercase), e.g. `saiyan`. |
| `form.group` | string | no | Active form group. |
| `form.form` | string | no | Active form name. |
| `form.stackGroup` | string | no | Stack form group (empty when none). |
| `form.stackForm` | string | no | Stack form name (empty when none). |
| `bodyParts` | array | no | Explicit pieces to attach. Omit when empty. Each entry is `{ "id", "source", "slot" }`. See below. |
| `colors` | object | no | Explicit model colors. Omit only when unknown. See [playerModel.parts.colors](#playermodelpartscolors). |
| `extras` | array | no | Open extension list. Each entry is `{ "kind": string, "id": string, … }`. Unknown `kind` values **must** be ignored. LM currently sends `[]`. |

##### `playerModel.parts.colors`

Per-category model colors as **packed RGB ints** (`0xRRGGBB`, decimal on the wire). LM
emits this on message 3 (world sync) so every observer renders the owner with the same
colors the owner sees. **Coordinate key names with JLDK — do not rename without a
protocol bump:** the keys mirror the DMZUltra accessor names (`FormParts$Look` /
`FormParts$Design`), so a client maps them 1:1.

| Key | Source | Fallback when absent |
|-----|--------|----------------------|
| `skin` | Active-form `bodyColor1` override, else the character's body color | white `16777215` |
| `second` | Active-form `bodyColor2` override, else the character's second body color | computed `skin` |
| `hair` | Active-form `hairColor` override, else the character's hair color | `2105380` (`0x202024`) |
| `own` | Form-constant design color (DMZUltra design table key `a`) | reflective read |
| `trim` | Form-constant design color (key `b`) | reflective read |
| `horn` | Form-constant design color (key `c`) | reflective read |
| `light` | Form-constant design color (key `g`) | reflective read |

`skin`/`second`/`hair` mirror DMZUltra `FormParts.of` byte-for-byte (same override chain
and fallbacks). `own`/`trim`/`horn`/`light` are form-constant — identical for every player
wearing the form — and are shipped in LM's `dmzultra-form-parts.json` `designs` map, keyed
`race/group/form`. `own`/`trim`/`horn`/`light` are omitted when the form has no catalog
entry (the client then keeps its own reflective path for exactly those keys).

Client rule: for each key **present** in `colors`, use it; for keys absent, fall back to
the reflective `FormParts$Look` / `FormParts$Design` read as before. `colors` never
replaces the parts pipeline — it only overrides the color inputs.

##### `playerModel.parts.bodyParts`

Any mod may send this array. The client attaches each known piece to the same preview
entity. `form` stays on the wire for older clients that only understand form string ids.

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | string | yes | Piece id in the `source` mod's catalog. Not an LM-only token. |
| `source` | string | yes | Mod id that owns the model (`dmzultra`, `myshop`, …). |
| `slot` | string | yes | Where it anchors. Standard slots: `tail`, `wings`, `horns`, `ears`, `extra_arms`. Other values (including `body`) still attach by `id` when the catalog knows the piece. |

Example (LM — a Saiyan who still has the race tail):

```json
{ "id": "saiyan_tail", "source": "dmzultra", "slot": "tail" }
```

Wings use the same shape with the catalog piece id, for example
`{ "id": "wings_feather", "source": "dmzultra", "slot": "wings" }`.

**Client rules:**

- When `source` is `dmzultra` and that mod is installed, load its FormParts catalog
  (`assets/dmzultra/form_parts/forms.json`) and the model
  `dmzultra:geo/entity/form_parts.geo.json`. Bones are named `part.<id>`.
- `saiyan_tail` is the race tail. Its DMZUltra mesh is `tail_fur` (`part.tail_fur`).
  If the preview entity already draws that race tail from its own character, do not
  stack a second one.
- Another mod's ids resolve the same way against `assets/<source>/form_parts/forms.json`
  when that mod is installed. Ids that are not in the loaded catalog are skipped.
- If the source mod is absent, or every id is unknown, draw the base entity and do not
  throw. `form` is unchanged.

\* When `headBones` is present, both `token` and `pieces` are required. `pieces` may be empty
only if `token` is empty (do not send `headBones` in that case).

**Client render contract (JLDK):**

1. Resolve `playerModel.subject` (else `parts.subject`, else `local`). Draw that player inside
   `x/y/width/height` at `scale`, with turntable when `rotate` is true. Use the tracked player
   when the client has them, so their skin and synced parts render. If they are not loaded,
   draw a remote player from their tab-list profile so the skin still loads. Do not draw the
   viewer in place of a different UUID.
2. If `parts.headBones` is present, apply those pieces. LM's example is DMZ
   `activeHeadBone` (split `token` on `+`, or iterate `pieces`). Other mods replace or omit
   this object.
3. If `parts.form` is present **and** the client advertises `combined.forms.v1`, draw those
   form string ids on the same preview. LM resolves them through DMZUltra FormParts; another
   mod resolves its own catalog. Missing the feature: skip `parts.form`.
4. If `parts.bodyParts` is present **and** the client advertises `bodyParts`, attach each
   entry whose `source` catalog contains that `id`. Skip unknown ids and missing source
   mods. Do not replace the base entity when the array is empty or fully unknown.
5. If `parts.colors` is present, use each key it carries (`skin`, `second`, `hair`, `own`,
   `trim`, `horn`, `light`) in place of the reflective `FormParts$Look` / `Design` value —
   this is the gray-parts fix; a remote player's reflective read comes back `0x808080`.
   Keys the object omits keep the reflective fallback.
6. Iterate `parts.extras`; skip unknown `kind`s.
7. If `parts` is missing entirely, draw the local player with whatever the client already
   knows from entity sync (no server cosmetics hint).

**`extras` entry shape (forward-compatible):**

```json
{ "kind": "dmzultra.formPart", "id": "example_accessory", "meta": {} }
```

| Field | Type | Notes |
|-------|------|-------|
| `kind` | string | Namespaced role (`dmz.headBone` is redundant with `headBones`; prefer the typed object). |
| `id` | string | Piece id within that kind. |
| `meta` | object | Optional opaque bag (tint, slot, …). Ignore unknown keys. |

LM server helper: `PlayerModelParts.snapshot(ServerPlayer)` → Gson object attached under
`playerModel.parts`.

### 5. Action verb contract (recommended)

Optional `@<screen>:` prefix, then bare id or `open:` / `do:` / `tab:` / `nav:` / `page:`.
Client never validates action ids — echo verbatim.

LM enforces this at packet **build** time via `UltraScreenIds.requireValidActionId`
(malformed ids never reach the client).

### 6. Hello world

```java
String buttons = "["
    + "{\"id\":\"@myshop:do:ping\",\"label\":\"§aPing\","
    + "\"style\":\"confirm\",\"x\":20,\"y\":80,\"w\":150,\"h\":18,"
    + "\"pad\":8,\"args\":{\"screen\":\"hello\",\"page\":\"main\"}}"
    + "]";
GuiOpenPacket open = new GuiOpenPacket(
    "hello", "§6My Shop", "main",
    "[{\"id\":\"home\",\"label\":\"Home\"}]",
    "home",
    "[\"Welcome.\"]",
    buttons, 350, 300,
    "{\"center\":true,\"margin\":10,\"titleAlign\":\"center\",\"lineHeight\":16,\"lineGap\":8,"
        + "\"bodyTop\":42,\"bodyPad\":10,\"bodyMaxHeight\":140,\"scrollBody\":true,"
        + "\"buttonPad\":8,\"buttonGap\":6,\"buttonMinWidth\":80,\"buttonHeight\":18}");
CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), open);
```

Client: `UltraGuiConsumerRegistry.registerClientChannel("mymod")` then send
`GuiFeaturesPacket` with `["style","activeTab","titleAlign","playerModel"]` once
implemented.

---

## Shared channel convention (KaiPath)

| Rule | Detail |
|------|--------|
| Format | `<modid>:gui` |
| Registry key | Full channel id |
| Multi-mod | Clicks return on the channel that opened the screen |
| Protocol | `"1"` |

---

## LegacyMechanics notes

LM channel: `legacymechanics:gui`. Startup aborts on catalog errors or encode/decode
round-trip failure (including **style** / **activeTab** survival and `GuiFeaturesPacket`).

With KaiPath **1.3.1+** dynamic-any screen ids, hub wire shim is off
(`UltraScreenIds.KAIPATH_DYNAMIC_ANY_SCREEN_ID`).

Smoke: `/lm admin guitest` → reports client features → click **Ping server**.

---

## Packaging checklist (LM jar)

1. Clean compile output before packaging (`build.sh` does this).
2. Scan jar for missing/referenced classes and inner classes
   (`audit_jar_inner_classes.py`).
3. Scan every `@Mixin` class for non-private helper methods
   (`audit_mixin_visibility.py` — helpers must be `private`).
4. Version pins match: `AdaptiveDifficultyMod.VERSION` == `mods.toml` `version`
   (== `plugin.yml` when a GUI plugin is shipped).
5. Fail-closed audits in `build.sh` (Ultra actions, forge GUI backend, ship manifest, …).
