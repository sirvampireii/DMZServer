# DMZUltra asset reuse via the KaiPath Ultra bridge

**Audience:** Cursor (server partners) + JLDK (KaiPath client).  
**Reference client assets:** Kinetic live `mods/dmzultra-0.2.9.jar` (**59,038,311** bytes,
verified over SFTP). Client-side only — not required in server CI.  
**Protocol home:** KaiPath (`docs/KAIPATH_GUI_PROTOCOL.md`) — **not** DMZUltra.

### Live jar verification (0.2.9)

| Check | Result |
|-------|--------|
| `{modId}:gui` / GuiOpen / GuiFeatures | **Absent** — zero protocol code in this jar |
| `*Screen.class` count | **57** (monolithic features — do not subclass) |
| Russo One | `assets/dmzultra/font/russo_one.ttf` |
| UI sounds | `ui_confirm`, `scene_tick`, `travel_tick` registered in `sounds.json` as `dmzultra:<id>` |
| Arena chrome | `textures/gui/arena/{button,card_frame,header,panel_tile,portrait_frame,portrait_frame_grey}.png` |
| Button state textures | **Single** `button.png` only — hover/pressed/disabled are client Look/tint, not separate files |
| Widgets | `UltraButton`, `UltraList`, `UltraEditBox`, `UltraUi` |
| Journal UX | `com.dmzultra.client.journal.JournalScreen` (+ page classes) |
| `UltraUi$Palette` | **Record** `(int accent, int second, int tint)` — data-driven RGB triple |
| `UltraUi$Theme` | **Hardcoded enum:** `ULTRA`, `ANGEL`, `DIVINE`, `TIME`, `MULTIVERSE`, `FORGE`, `PARTY`, `JOURNAL`, `SCOUTER`, `KI`, `SAGA`, `SHENRON` |
| `UltraUi$Look` | **Hardcoded enum:** `NORMAL`, `SELECTED`, `ACCENT`, `DANGER`, `DISABLED` (maps near bridge `style`) |
## Governing rules

1. **All DMZUltra assets are client-side.** Server mods must never reference
   `assets/dmzultra/...` paths, never bundle those files, and never subclass DMZUltra
   `Screen` classes (Arena, Travel, Divine, Tournament, Journal, … are monolithic features).
2. **The only clean integration point is the KaiPath bridge** — channel
   `{modId}:gui`, wire format in [`KAIPATH_GUI_PROTOCOL.md`](KAIPATH_GUI_PROTOCOL.md).
3. **Every reuse = protocol field + JLDK render.** Cursor adds a mod-agnostic field;
   JLDK maps it onto DMZUltra / KaiPath widgets and asset ids.
4. **Bridge PRs must work for any mod.** If a field name, default, or doc example only
   makes sense for one partner mod, it is wrong. Partners map their own domains to
   generic values (see `palette` below).
5. **DMZUltra 0.2.9 contains zero `{modId}:gui` protocol code.** Do not look there for
   codecs or consumers. Ignore its 57 Screen classes, entity/block textures, combat
   animations, and VFX shaders when designing menu chrome.

## LM client exception (owner decision, 2026-11-21, LM 4.7.61)

The LM client (the optional `LegacyMechanics` jar on a player's PC) draws one DMZUltra look itself: the
made-up figure on Character Services > Preview forms. Before 4.7.61 that page fell back to the KaiPath Ultra
menu because only KaiPath could dress a stand-in. Now:

- `client/NativeFormLook` sets the stand-in's DragonMineZ race, form and stack form from
  `playerModel.parts.form` (a client-only `RemotePlayer`, never the viewer or a real player).
- `client/mixin/FormPartsPreviewMixin` (config `legacymechanics.client.mixins.json`, client only, applied
  only when `com/dmzultra/client/forms/FormParts` exists) unions `parts.bodyParts` into DMZUltra 0.5.7's
  FormParts look, and only while an LM menu draws its preview. `RaceTailSuppressMixin` hides the race tail
  bones in that preview when `suppressNaturalTail` is set.
- Nothing is bundled from DMZUltra and no DMZUltra screen is subclassed. World rendering (message 3,
  `bodyPartsSync`) stays KaiPath's.
- The client advertises `combined.forms.v1` with DragonMineZ and `bodyParts` with DMZUltra, so the server
  routes Preview forms natively only when both can be drawn.

## Already working (do not rebuild)

| Client capability | Notes |
|-------------------|-------|
| UltraButton / UltraList / UltraEditBox | Via KaiPath `UltraUiAccess` / reflect helpers |
| Russo One font | `assets/dmzultra/font/russo_one.ttf` — client-owned |
| `titleAlign`, `activeTab` (via button style), `playerModel` + `parts` schema 1 | Shipped on KaiPath 1.3.9 |
| `inputsJson` field 11 (`type=text`) | Shipped on KaiPath 1.3.9 |
| `headerBottom` + letter `accent` + `palette` | Emitted by LM 4.6.152; kit honors — merge next KaiPath |

## Reusable assets → protocol fields

| DMZUltra asset (client) | Protocol field (mod-agnostic) | Status |
|-------------------------|-------------------------------|--------|
| `UltraUi$Palette` / theme accents | `layoutJson.palette` + button/tab `palette` (generic color **names**) | **Kit honors** (ARGB); texture Theme map TBD |
| Letter-code tint (compat) | `layoutJson.accent` + button `accent` (`0`-`9`/`a`-`f`) | Emit + kit honor |
| `sounds/ui_confirm.ogg`, `scene_tick.ogg`, `travel_tick.ogg` | Button (or layout) `sound` — namespaced id | **Kit honors** (play) |
| `textures/gui/arena/button.png` (+ states) | Button `buttonSkin` — namespaced skin id or state map | Kit chrome cue; blit TBD |
| `card_frame.png`, `panel_tile.png`, `header.png` | `layoutJson.frame` — namespaced frame id or map | Kit edge rects; blit TBD |
| `portrait_frame.png`, `portrait_frame_grey.png` | `playerModel.portraitFrame` — namespaced id | Emit + kit edge rects; blit TBD |
| Warrior Journal paged UX (`com/dmzultra/client/journal/`) | `inputsJson` `type: "paged_book"` — copy UX, not code | **Kit honors** (Prev/Next) |

Full wire shapes: [`KAIPATH_GUI_PROTOCOL.md`](KAIPATH_GUI_PROTOCOL.md) §4 / §11 (future fields).  
Gap tracking: [`KAIPATH_BRIDGE_GAPS.md`](KAIPATH_BRIDGE_GAPS.md).

## Namespaced identifiers (not filesystem paths)

Use Minecraft `namespace:path` ids. **Never** put `assets/` prefixes or absolute paths on the wire.

| Kind | Example id | Client resolves to (illustrative) |
|------|------------|-----------------------------------|
| Sound | `dmzultra:ui_confirm` | `assets/dmzultra/sounds/ui_confirm.ogg` |
| Sound | `dmzultra:scene_tick` | `assets/dmzultra/sounds/scene_tick.ogg` |
| Sound | `dmzultra:travel_tick` | `assets/dmzultra/sounds/travel_tick.ogg` |
| Button skin | `dmzultra:arena` | Arena button texture set (normal/hover/pressed/disabled) |
| Frame | `dmzultra:card` | card_frame + panel_tile + header set |
| Portrait | `dmzultra:portrait` / `dmzultra:portrait_grey` | Portrait frame textures |

**Fail-open defaults (LM 4.6.154 + kit fixtures):** prefer `minecraft:ui.button.click` and
`legacymechanics:button` / `legacymechanics:panel` / `legacymechanics:portrait`. On KaiPath
1.3.13, skin/frame/portraitFrame are boolean enable tokens (no texture bind); missing sound is
a silent no-op. DMZUltra ids still work when that client mod is present.

Other mods may ship their own namespaces (`myshop:ping`, `myshop:wood_frame`, …). KaiPath
resolves whatever the client has loaded; unknown ids are ignored (no crash).

## Palette names (generic — not partner system names)

Live 0.2.9 clarified the client model:

| Client type | Shape | Bridge implication |
|-------------|-------|--------------------|
| `UltraUi$Palette` | `(accent, second, tint)` ints | **Data-driven** — KaiPath should map wire `palette` **color names** → a `Palette` triple |
| `UltraUi$Theme` | Fixed feature enum (`ULTRA`, `JOURNAL`, …) | **Hardcoded** — DMZUltra feature skins. Do **not** require these names in the multi-mod protocol |
| `UltraUi$Look` | `NORMAL` / `SELECTED` / `ACCENT` / `DANGER` / `DISABLED` | Aligns with semantic bridge `style` (`normal` / `confirm` / `danger` / `dim`) |

Protocol `palette` values stay **generic color names** (not Theme enum names, not partner
system names):

`red` · `dark_red` · `purple` · `dark_purple` · `gold` · `yellow` · `green` · `aqua` · `blue` · `white` · `gray`

Partners map their own systems locally (example only — **not** protocol enums):

| Partner concept (private) | Wire `palette` |
|---------------------------|----------------|
| shop promo tile | `gold` |
| danger / staff | `dark_red` |
| magic / rivalry | `purple` |

Do **not** put `ACCENT_RIVAL`, `SYSTEM_DIFFICULTY`, `ULTRA`, `JOURNAL`, or similar into the
protocol as required values. Optional future `theme` (DMZUltra Theme names) can be a
KaiPath-only extension later if JLDK wants it — not required for other mods.
## Semantic `style` vs decorative `palette`

| Field | Role |
|-------|------|
| `style` | **Semantic** action chrome: `confirm` / `danger` / `dim` / `normal` |
| `palette` | **Decorative** header / tile / tab / system tint |
| `accent` | Legacy letter-code tint (compat); prefer `palette` for new work |

Do not overload `style` with decorative colors. Do not invent a second RGB color system on
the server — reuse DMZUltra’s palette through KaiPath.

## Pitfalls

- Never bundle DMZUltra assets into a server jar.
- Never subclass DMZUltra screens for menus.
- Never hardcode one partner’s mod id into field defaults or protocol examples for new fields.
- When adding fields, update [`KAIPATH_GUI_PROTOCOL.md`](KAIPATH_GUI_PROTOCOL.md) in the
  **same commit** — JLDK implements from the doc.
- Prefer plain ASCII labels (no section-sign); tint via `palette` / `style` / chrome.

## Reference implementation (zero partner imports)

[`tools/ultra-gui-kit/server/HelloWorldServerSketch.java`](../tools/ultra-gui-kit/server/HelloWorldServerSketch.java)
and [`tools/ultra-gui-kit/server/BridgeFutureFieldsFixture.json`](../tools/ultra-gui-kit/server/BridgeFutureFieldsFixture.json)
exercise every proposed field on a fictional `myshop:gui` channel so JLDK can verify without
any production partner mod.
