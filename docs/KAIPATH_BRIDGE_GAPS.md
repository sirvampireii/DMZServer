# Ultra bridge gap matrix (LM ↔ KaiPath)

**LM 4.6.216** + **KaiPath 1.3.31** (recommended; bytecode-audited 2026-10-18 — see the audit section below). Official KaiPath updates are owned by the KaiPath mod developer.

**At a glance:** Bridge chrome gaps closed on **1.3.17** — `fixedFooter` pins `footer:true`; `portraitFrame` is advertised. LM wire already matched (`footer:true`); guitest tracks `FEATURE_FIXED_FOOTER` as shipped. Custom **`bodyParts`** closed on **1.3.29** — the client advertises it and LM ships the Body Parts Shop (player-chosen pieces on model customize). JLDK one-pager: [`KAIPATH_JLDK_CLIENT_BRIEF.md`](KAIPATH_JLDK_CLIENT_BRIEF.md).

Advertise set (KaiPath 1.3.31; `bodyParts` added on 1.3.29 after the 1.3.22 sixteen; `bodyPartsSync` on 1.3.31 — advertised **only** when message 3 `GuiModelSyncPacket` is registered):

```text
style, activeTab, titleAlign, playerModel, textInput, headerBottom, accent,
palette, sound, buttonSkin, frame, pagedBook, portraitFrame, fixedFooter,
combined.forms.v1, icon, bodyParts, bodyPartsSync
```

## Open bridge gaps (KaiPath next)

| Gap | Owner | LM status | KaiPath 1.3.17 | Notes |
|-----|-------|-----------|---------------|-------|
| **Title vertical centering** | **JLDK-only** | N/A (no field) | Confirm | Centers via `(headerBottom - 2 - 9) / 2`. Confirm gear-icon band alignment on all themes. No protocol field. |
| **Partner auto-discovery** | **JLDK** (kit done) | Ships `META-INF/kaipath-ultra.txt` | Manual list today | Seed `legacymechanics`, then marker jars under `mods/` and `config/kaipath-ultra-partners.txt`. A new mod does not need a KaiPath release. |
| **`slider` / `toggle`** | Later | Not on the wire | Reserved | Booleans stay styled buttons; numbers stay text + steppers. |

## Open bridge gaps — JLDK 1.3.31 bytecode audit vs LM 4.6.216 (2026-10-18)

LM verified holding its end: the live 4.6.216 jar's sync JSON (`subject uuid:…`, `bodyParts`, `colors` with `{skin, second, hair, own, trim, horn, light}`, `extras`) and the `fillScreen` design box are bytecode-correct. Every open item below is **KaiPath-side**.

| Gap | Owner | LM status | KaiPath action |
|-----|-------|-----------|----------------|
| **`parts.colors` dropped in `applySync`** — parts render gray (critical) | **JLDK** | On the wire since 4.6.216 | Parse the `colors` object in `BodyPartPreview.applySync` and thread it to `mergeLook`. Contract (protocol § playerModel.parts.colors): use each key **present**; reflective `FormParts$Look` only for absent keys. Today the parse skips `colors` entirely, so the gray reflection fallback wins for every remote player. |
| World render depends on dmzultra client-side | JLDK (design note) | Sync pipeline complete | `FormPartsPreviewMixin` is the only world path (optional-mixin guarded on `com.dmzultra.client.forms.FormParts`). Clients without dmzultra draw the base entity with no pieces — intended optional-mod contract, not a sync bug. |
| No dedicated config screen | Later | Config editor works via `GuiOpenPacket` + `fillScreen` | `LmScreens` registers `hub` / `emptyHub` only; a native config screen id would be cleaner but is not required. |
| Per-part colors not in wire format | Later (reserved) | Per-category `parts.colors` covers LM | `BodyPartWire$Entry` is the 3-field record `(id, source, slot)` — a per-part color needs a format bump. Reserve; do not overload the record. |
| `WorldState` strands `partsJson` | JLDK (internal) | n/a | `WorldState` stores the full partsJson but `mergeLook(look, meshes)` never receives it — thread it so future per-sync metadata does not hit the same wall colors did. |
| `contentScale` dead (0 refs jar-wide) | Closed on LM | Never sent; protocol client-internal table now marks it **deprecated** | Remove residual handling. `fillScreen` + `designWidth`/`designHeight` is the scale contract (`panel / design`). |
| `titleTint` read but unadvertised | Closed on LM | Never sent — not a wire field (protocol § client-internal) | Drop the read or keep it tolerant and unadvertised; `palette`/`accent` already cover title color. |
| Subject format drift | Documented | `uuid:<owner-uuid>` on message 3 (protocol § GuiModelSyncPacket) | Keep resolving only `uuid:`-prefixed subjects (already the 1.3.31 guard). Note for the record: LM's wire subject has **always** been `uuid:`; the "plain username" incident was server **log diagnostics** (`safeName`), not the wire. |

`/lm admin guitest` on KaiPath **1.3.29** (advertises `icon` + `bodyParts`):

```text
missingShipped=none pendingNext=none
```

A 1.3.17 client that stops before `icon` reports `missingShipped=icon`. A 1.3.22 client without `bodyParts` reports `missingShipped=bodyParts`. Omitting any shipped id (including `palette`, `sound`, `buttonSkin`, `frame`, `pagedBook`, `portraitFrame`, `combined.forms.v1`, `icon`, `bodyParts`) warns as `missingShipped`.

## Closed on KaiPath 1.3.29

| Gap | Notes |
|-----|-------|
| **`bodyParts`** | Advertised (17th id). Client attaches `playerModel.parts.bodyParts` pieces (`BodyPartPreview` merges known DMZUltra meshes onto the preview). LM moved `FEATURE_BODY_PARTS` from `PENDING_NEXT` into `SHIPPED_ON_RECOMMENDED` and ships the **Body Parts Shop** (4.6.207): Character Services → Customize model → Body parts — paid unlocks, worn pieces ride `parts.bodyParts`. |

## Closed on KaiPath 1.3.22

| Gap | Notes |
|-----|-------|
| **`icon`** | Advertised. `UltraBridgeIcon` resolves an item id (`minecraft:iron_sword`) or a texture path (`modid:path/to/texture.png`). Unknown ids are ignored. LM leaves the field blank (no vanilla item marks; no custom gear textures). Other mods may set it. |

## Closed on KaiPath 1.3.17

| Gap | Notes |
|-----|-------|
| **`fixedFooter`** | Client pins `footer:true` buttons; auto spacing. LM emission unchanged. |
| **Advertise `portraitFrame`** | Was painted but not advertised on 1.3.13 — now advertised. |

## Closed on both sides (not gaps)

| Part | Status |
|------|--------|
| GuiOpen fields 1–11 (`inputsJson`) | **Done** |
| Button `style` (`confirm` / `danger` / `dim` / `normal`) | **Done** (1.3.7+) |
| `activeTab` highlight | **Done** (tint from `accent` on 1.3.13) |
| `layoutJson.titleAlign` | **Done** |
| `layoutJson.playerModel` (+ `parts` schema 1) | **Done** — `headBones` / DMZUltra notes are an LM example; other mods replace them |
| `combined.forms.v1` | **Done** (generic) — one preview = base entity + optional `parts.form` string ids. Not DMZ-only. |
| `textInput` / EditBox / `bindInput` → `args.value` | **Done** (1.3.9+) |
| `layoutJson.headerBottom` / `tabsUnderTitle` | **Done** (1.3.13) |
| `layoutJson.accent` + button/tab `accent` | **Done** (1.3.13 via `UltraBridgeChrome`) |
| GuiFeaturesPacket advertise | **Done** on 1.3.17 (incl. `portraitFrame` + `fixedFooter`) |
| `sound` / `buttonSkin` / `frame` / `paged_book` | Client **Done** (1.3.13+). LM emits `frame` / `paged_book`; **does not** emit button `sound`/`buttonSkin` (skinned path = flat chrome) |
| `footer:true` / `fixedFooter` | **Done** (client 1.3.17 + LM emit 4.6.152+) |
| `playerModel.portraitFrame` advertise | **Done** (1.3.17) |

## Optional chrome (LM emit policy; not tracked as pending)

| Part | KaiPath 1.3.17 | LM |
|------|---------------|----|
| `sound` (`minecraft:ui.button.click`) | Honors (missing → silent) | **Not emitted** on buttons/tabs (default click) |
| `buttonSkin` (`legacymechanics:button`) | Honors (RL → boolean skinned paint) | **Not emitted** — skinned path draws flat squares |
| `layoutJson.frame` (`legacymechanics:panel`) | Honors (RL → boolean border) | **Emits** |
| `inputsJson` `type=paged_book` (advertise `pagedBook`) | Honors (`BookWidget`) | **Emits** (Staff Admin help book) |
| `playerModel.portraitFrame` | Honors + **advertised** | **Omitted** — a non-empty id draws a preview border |
| `layoutJson.palette` | Honors | Optional (accent already covers tint) |

## Pitfalls & traps (read before KaiPath release)

### 1. Server jar ≠ client render

`mods/kaipath-*.jar` on the **dedicated server** does nothing for Ultra painting. Players need the KaiPath **client** mod. Do not treat “server has 1.3.17” as “players have 1.3.17.”

### 2. DMZUltra-absent chrome RLs

`sound` / `buttonSkin` / `frame` / `portraitFrame` look like asset ids. On 1.3.13+:

- `sound` → registry Optional; missing SoundEvent → **silent no-op** (no crash)
- skin / frame / portraitFrame → `UltraBridgeChrome.id` only validates `ns:path`; result is a **boolean** that enables GuiGraphics tint paint — **no texture bind**

LM keeps `layoutJson.frame` / `portraitFrame` as `legacymechanics:*` enable tokens. Buttons omit `sound`/`buttonSkin` so KaiPath uses default chrome (buttonSkin would force the flat skinned path).

### 3. `pagedBook` vs `paged_book`

| Where | Spelling |
|-------|----------|
| GuiFeatures advertise | `pagedBook` (camelCase) |
| `inputsJson[].type` | `paged_book` (snake_case) |

Mismatched type string → book widget never mounts.

### 4. `contentBottom` / fixedFooter

Pre-1.3.17 hard-coded `contentBottom = panelY + panelH - 28` and ignored `footer:true`. **1.3.17** pins footer buttons and auto-calculates spacing. Do **not** invent server `contentTop`/`contentBottom`/`contentScroll` wire fields.

### 5. `titleTint` is not a wire field

Client derives title color from `palette` / `accent`. LM already sends those. Do not add `titleTint` to GuiOpen.

### 6. UTF-8 mojibake / tab ellipsis

LM Ultra copy prefers ASCII. Pre-fit labels with ASCII `...`. Stock KaiPath `fitLabel` uses ASCII `...` — keep it that way.

### 7. Pending vs shipped diagnostics

Missing `SHIPPED_ON_RECOMMENDED` → warn (wrong/old jar). Missing `PENDING_NEXT` → info (`pendingNext=`). On KaiPath **1.3.29**, `missingShipped=none` and `pendingNext` is empty. `icon` (1.3.22) and `bodyParts` (1.3.29) are in the shipped set. `PENDING_NEXT` stays empty until a client ships the next optional id.

### 8. Older clients without textInput

Pre-1.3.9 ignores field 11. LM Weights / Config show an explicit danger line when `textInput` is missing.

### 9. Player model custom parts

`playerModel.parts` is generic. `headBones`, form ids, and the `saiyan_tail` note in the protocol are an **LM example — replace with your own**. `combined.forms.v1` means one preview can also draw `parts.form` string ids. `bodyParts` is the explicit piece list (`id`, `source`, `slot`). Clients that lack either feature still draw the entity and ignore the field they do not know. Unknown piece ids and unknown `extras.kind` values must be ignored. `bodyParts` is advertised since KaiPath **1.3.29** (same treatment as `icon`); the LM Body Parts Shop sends player-chosen pieces on model customize.

### 10. Slider / toggle deferred

Booleans stay styled buttons; numbers stay text + ± steppers. Not a KaiPath blocker.

### 11. World body parts need dmzultra on the CLIENT

`FormPartsPreviewMixin` — the only world-render path — targets `com.dmzultra.client.forms.FormParts` and is applied only when that class exists. A client without dmzultra renders world players with **no** attached pieces even though the sync pipeline (packet → `applySync` → `WorldState` → `meshesFor`) is complete on both sides. The menu preview attaches only known catalog ids for the same reason. This is the intended optional-mod contract; do not file it as a sync bug.

## Not bridge gaps (LM-only, already shipped)

| Item | Notes |
|------|-------|
| Hub tile styles | All `normal` + per-system `accent` letter; Staff Admin accent `4` |
| Hub compact floor | `PANEL_COMPACT_MIN_H=150` for tile-only menus |
| Active-tab style | LM sends `style=normal` + `accent` |
| Main hub title gold | Hub omits `layoutJson.accent` (shared chrome) |
| Prestige GoD / Supreme Kai soft warn | Ultra + chat paths (4.6.155) |

## Cursor / LM (server) checklist

| Item | Status |
|------|--------|
| Emit `style` on every button | **Done** |
| Emit `inputsJson` field 11 | **Done** |
| Emit `playerModel.parts` schema 1 | **Done** |
| Emit `layoutJson.headerBottom` | **Done** |
| Emit `layoutJson.accent` / button `accent` | **Done** |
| Emit button `footer:true` | **Done** (4.6.152) — matches 1.3.17 |
| Emit `frame` / `paged_book` (not button `sound`/`buttonSkin`) | **Done** — omit buttonSkin (flat skinned chrome) |
| Chrome defaults safe without DMZUltra | **Done** (default button chrome; frame/portrait tokens) |
| Track `FEATURE_FIXED_FOOTER` as shipped on 1.3.17 | **Done** |
| Track `palette` / `sound` / `buttonSkin` / `frame` / `pagedBook` / `portraitFrame` / `combined.forms.v1` as shipped | **Done** |
| Track `icon` as shipped on KaiPath 1.3.22 | **Done** |
| Track `bodyParts` as shipped on KaiPath 1.3.29 | **Done** (4.6.207) |
| Ship Body Parts Shop (`page:bodyparts`; worn pieces on `parts.bodyParts`) | **Done** (4.6.207) |
| Ship `META-INF/kaipath-ultra.txt` (`legacymechanics`) | **Done** |
| Recommend KaiPath **1.3.29** | **Done** (4.6.207) |
| `inputsJson` `slider` / `toggle` | **Deferred** |

## JLDK / KaiPath (client) checklist

| Item | 1.3.17 |
|------|--------|
| Button `style` | **Done** |
| `activeTab` | **Done** (accent tint) |
| `titleAlign` | **Done** |
| `playerModel` | **Done** |
| `textInput` | **Done** |
| `headerBottom` | **Done** |
| `accent` / palette chrome | **Done** |
| `sound` / `buttonSkin` / `frame` / `paged_book` | **Done** |
| Advertise `portraitFrame` + `fixedFooter` | **Done** |
| Honor `footer:true` / `fixedFooter` | **Done** |
| Document `combined.forms.v1` as generic form strings on one preview | **Kit docs done** — merge with the client |
| Optional button/tab `icon` (item id or texture path; unknown ids ignored) | **Done** on 1.3.22 |
| Advertise `bodyParts`; attach `parts.bodyParts` pieces by catalog id | **Done** on 1.3.29 |
| Partner discovery (`META-INF/kaipath-ultra.txt` + config list) | **Kit done** — 1.3.17 still uses a manual partner list |
| Title vertical center | Confirm gear-band alignment |
| `slider` / `toggle` widgets | Reserved — workarounds exist |

## Reserved (later): `inputsJson` slider / toggle

Not implemented on either side. Prefer extending field 11 when added.

## Docs

- Protocol: [`KAIPATH_GUI_PROTOCOL.md`](KAIPATH_GUI_PROTOCOL.md)
- JLDK handoff: [`KAIPATH_JLDK_CLIENT_BRIEF.md`](KAIPATH_JLDK_CLIENT_BRIEF.md)
- Overview: [`KAIPATH_1.3.1_ULTRA.md`](KAIPATH_1.3.1_ULTRA.md)
