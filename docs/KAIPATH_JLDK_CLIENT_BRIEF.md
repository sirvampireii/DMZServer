# JLDK brief — KaiPath Ultra GUI client conformance

**Status (LM 4.6.216 live + KaiPath 1.3.31):** Client honors style / activeTab / titleAlign /
playerModel / textInput / headerBottom / accent / palette / sound / buttonSkin / frame /
paged_book / **fixedFooter** / **portraitFrame** / **icon** / **bodyParts**. KaiPath **1.3.31**
registers `GuiModelSyncPacket` (message 3) and advertises **bodyPartsSync**.

**Open now — JLDK 1.3.31 bytecode audit vs LM 4.6.216 (2026-10-18):**

1. **Critical — parse `parts.colors` in `BodyPartPreview.applySync`** and thread it to `mergeLook`. The wire already carries `{skin, second, hair, own, trim, horn, light}` (LM 4.6.216, verified); the reflective `FormParts$Look` fallback returns gray for every **remote** player. Contract: use each key **present**; reflective fallback only for absent keys (protocol § playerModel.parts.colors).
2. **Subject contract** — message 3 subjects are `uuid:<owner-uuid>` (documented; the wire has always shipped `uuid:` — the plain-name episode was server log diagnostics only). Non-`uuid:` → null → local render is the correct guard; keep it.
3. **`contentScale` is dead** — zero references jar-wide in 1.3.31. The protocol now marks it **deprecated** (client-internal table); LM never sent it. `fillScreen` + `designWidth`/`designHeight` is the scale contract (`panel / design`). Remove residual handling.
4. **`titleTint`** is not a wire field — drop the read or keep it tolerant and unadvertised; `palette`/`accent` cover title color.
5. Later, KaiPath-side: dedicated config screen; per-part colors (needs a `BodyPartWire$Entry` format bump); thread `WorldState.partsJson` into `mergeLook` so future per-sync metadata does not hit the colors wall again. Full matrix: [`KAIPATH_BRIDGE_GAPS.md`](KAIPATH_BRIDGE_GAPS.md).

Confirm title vertical centering in the gear-icon band. `slider` / `toggle` stay reserved.

Full matrix + pitfalls: [`KAIPATH_BRIDGE_GAPS.md`](KAIPATH_BRIDGE_GAPS.md).

| Kit | Path |
|-----|------|
| Protocol + conformance contract | [`docs/KAIPATH_GUI_PROTOCOL.md`](KAIPATH_GUI_PROTOCOL.md) |
| Bridge gap matrix + pitfalls | [`docs/KAIPATH_BRIDGE_GAPS.md`](KAIPATH_BRIDGE_GAPS.md) |
| KaiPath client sources (reference) | [`tools/ultra-gui-kit/kaipath-client/`](../tools/ultra-gui-kit/kaipath-client/) |
| Server codecs (any mod) | [`tools/ultra-gui-kit/server/`](../tools/ultra-gui-kit/server/) |

---

## Governing rule

The client renders what the server sends — **no hardcoded screen/tab/button/action lists**.

## Confirmed on KaiPath 1.3.17

| Feature | Notes |
|---------|-------|
| Decode GuiOpen fields 1–11 | yes (incl. `inputsJson`) |
| Dynamic title / tabs / lines / buttons | yes |
| Button `style` | yes |
| `layoutJson.titleAlign` = `center` | yes |
| `activeTab` highlight | yes (tint from `accent`) |
| `playerModel` entity preview | yes |
| Text fields + `bindInput` → `args.value` | yes |
| `layoutJson.headerBottom` / `tabsUnderTitle` | yes |
| `layoutJson.accent` + button/tab `accent` | yes (`UltraBridgeChrome`) |
| `palette` / `frame` / `paged_book` (button `sound`/`buttonSkin` omitted by LM) | yes |
| `playerModel.portraitFrame` | paints + **advertised** |
| Button `footer:true` / `fixedFooter` | pins + auto spacing + **advertised** |
| GuiFeaturesPacket | yes — fifteen ids through `combined.forms.v1` |
| Verbatim GuiAction echo | yes |
| Any `screenId` | yes |

## Merge from the reference kit

### `combined.forms.v1`

Generic, not DMZ-only. One preview combines the base entity, optional `parts.headBones`, and optional `parts.form` string ids (`race`, `group`, `form`, `stackGroup`, `stackForm`). Any mod may send its own ids. LM fills them from DMZ. A client that lacks the feature still draws the entity and ignores `parts.form`. `headBones` and the DMZUltra FormParts notes are an LM example — other mods replace them.

### `bodyParts`

Advertise `bodyParts`. `playerModel.parts.bodyParts` is an array of `{ "id", "source", "slot" }`. Standard slots: `tail`, `wings`, `horns`, `ears`, `extra_arms`. When DMZUltra is installed, load its FormParts catalog and attach each known id to the preview entity (`part.<id>` on `dmzultra:geo/entity/form_parts.geo.json`). LM's race tail is `{ "id": "saiyan_tail", "source": "dmzultra", "slot": "tail" }` and uses the `tail_fur` mesh. The same path accepts any other mod's ids: look up `assets/<source>/form_parts/forms.json`. If that mod is absent, or the id is not in the catalog, skip the piece and still draw the base entity. Keep `parts.form` for clients that do not advertise `bodyParts`.

Reference: `BodyPartWire`, `BodyPartPreview`, and `FormPartsPreviewMixin` (optional mixin — do not apply it when `com.dmzultra.client.forms.FormParts` is missing). Register `kaipath-bodyparts.mixins.json` from the kit.

### `bodyPartsSync` (message 3 — world model sync)

Advertise `bodyPartsSync` **only** when `GuiModelSyncPacket` (S→C message 3) is registered.
The server then pushes the authoritative equipped body-parts state: once on login and after
every equip/unequip change. On receive, **replace** — never merge — the cached world-render
parts via `BodyPartPreview.applySync`; an empty `bodyParts` array clears all extra pieces.
`FormPartsPreviewMixin` already consults `BodyPartPreview.meshesFor` for every player, so the
menu preview keeps overriding for its subject while the world model uses the synced cache.
`BodyPartPreview.clearSync()` runs on `ClientPlayerNetworkEvent.LoggingOut` (state is
per-session). A server must never send message 3 to a client that did not advertise the id,
and a client must never advertise it without registering the message — KaiPath ≤ 1.3.29 has
messages 0–2 only and drops the connection on an unregistered id (**1.3.31 registers message 3**). Wire format:
[`KAIPATH_GUI_PROTOCOL.md`](KAIPATH_GUI_PROTOCOL.md) § GuiModelSyncPacket.

### `icon`

Optional item id (`minecraft:iron_sword`) or texture path (`modid:path/to/texture.png`) on a button or tab. KaiPath **1.3.22** advertises `icon` and resolves it with `UltraBridgeIcon`. Unknown or blank ids are ignored and the control still works. LM leaves `icon` blank (system menus are not vanilla items, and no custom gear textures ship). A client that does not advertise `icon` is `missingShipped=icon`.

### Partner discovery

Do not keep a hardcoded partner list that only contains `legacymechanics`. At client boot, before the network registry locks:

1. Seed `legacymechanics`.
2. Scan `mods/*.jar` for `META-INF/kaipath-ultra.txt` (first non-comment line is the mod id; empty marker falls back to `mods.toml` `modId`).
3. Read `config/kaipath-ultra-partners.txt` (one id per line).

Register `<id>:gui` for each discovered id. `UltraGuiPartners.discoverAndRegister()` is the reference.

### Title vertical centering

1.3.13+ centers in the header band via `(headerBottom - 2 - 9) / 2`. Confirm alignment
between the gear icons on all themes. No new protocol field.

## Advertise (KaiPath 1.3.31 — full set)

```java
UltraGuiConsumerRegistry.advertiseFeatures(
    "legacymechanics:gui",
    "[\"style\",\"activeTab\",\"titleAlign\",\"playerModel\",\"textInput\","
        + "\"headerBottom\",\"accent\",\"palette\",\"sound\",\"buttonSkin\","
        + "\"frame\",\"pagedBook\",\"portraitFrame\",\"fixedFooter\",\"combined.forms.v1\","
        + "\"icon\",\"bodyParts\",\"bodyPartsSync\"]");
```

`bodyPartsSync` goes in the set **only** when message 3 `GuiModelSyncPacket` is registered (1.3.31 does). Stock 1.3.17 stops before `icon`: `missingShipped=none pendingNext=icon`. A merged kit client: `pendingNext=none`.

## Pitfalls (must-read)

Full list: [`KAIPATH_BRIDGE_GAPS.md`](KAIPATH_BRIDGE_GAPS.md) § Pitfalls & traps.

1. **Server jar ≠ client** — `mods/kaipath-*.jar` on the dedicated server does not paint Ultra; players need the client mod.
2. **Chrome RLs are enable tokens** — `buttonSkin` / `frame` / `portraitFrame` do not load textures; missing `sound` is a silent no-op. LM uses `minecraft:ui.button.click` + `legacymechanics:*` so DMZUltra is not required.
3. **Spelling** — advertise `pagedBook`; widget `type` is `paged_book`.
4. **Do not add wire fields** for `titleTint` / `contentScale` / `contentTop` / `contentBottom` / `contentScroll`. `contentScale` is deprecated (dead in 1.3.31); the scale contract is `fillScreen` + `designWidth`/`designHeight`.
5. Keep ASCII `fitLabel` ellipsis (`...`).

## Deferred (not blocking)

- `inputsJson` types `slider` / `toggle` — reserved. Styled buttons and text/steppers are the workarounds.
- Full DMZUltra geo application of `playerModel.parts` beyond the entity preview. That geo is LM-specific; other mods supply their own part ids.
