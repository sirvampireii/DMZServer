# Ultra GUI — implement-yourself kit (any Forge mod + KaiPath / JLDK)

Protocol + conformance: [`docs/KAIPATH_GUI_PROTOCOL.md`](../../docs/KAIPATH_GUI_PROTOCOL.md).
Gap matrix / pitfalls: [`docs/KAIPATH_BRIDGE_GAPS.md`](../../docs/KAIPATH_BRIDGE_GAPS.md).
DMZUltra asset reuse: [`docs/DMZULTRA_ASSET_REUSE.md`](../../docs/DMZULTRA_ASSET_REUSE.md).
JLDK brief: [`docs/KAIPATH_JLDK_CLIENT_BRIEF.md`](../../docs/KAIPATH_JLDK_CLIENT_BRIEF.md).
Non-LM chrome fixture: [`server/BridgeFutureFieldsFixture.json`](server/BridgeFutureFieldsFixture.json).
fixedFooter merge notes: [`kaipath-client/.../FIXED_FOOTER.md`](kaipath-client/com/dmzlegacy/kaipath/ultra/consumers/legacymechanics/FIXED_FOOTER.md).

**This bridge is multi-mod.** Every server mod owns its own `{modId}:gui` channel. Packet
codecs never embed a mod id. LegacyMechanics is one partner example — not the owner of the
protocol. KaiPath registers one client consumer per partner mod id.

| Who you are | What to pull |
|-------------|--------------|
| **Any server mod author** | [`server/`](server/) — copy codecs + channel helper; rename package to yours |
| **KaiPath / JLDK** | [`kaipath-client/`](kaipath-client/) — merge into the next official KaiPath release. Partners are discovered; do not hardcode each new mod id |

**Server mods do not ship or deploy KaiPath updates.** The KaiPath mod developer owns the official jar. This kit is the handoff for what to merge.

---

## Server mod authors (any mod)

1. Copy `server/*.java` into your mod; rename the `com.example.ultragui` package.
2. **Link first** ([`UltraGuiLink`](server/UltraGuiLink.java)). `UltraGuiLink.link(MOD_ID, onClick, onFeatures)`
   returns true where KaiPath owns `{yourModId}:gui` (singleplayer, LAN host, your mod installed on clients):
   then do **not** register the channel, and send with `UltraGuiLink.sendOpen`. Registering it there too
   crashes the game at startup (Forge allows one channel per name), and whoever registers first owns the
   click handler.
3. Otherwise (dedicated server, a game without KaiPath) register `{yourModId}:gui` protocol `"1"`:
   - msg 0 = open (`UltraGuiOpenPacket`)
   - msg 1 = action (`UltraGuiActionPacket`)
   - msg 2 = features (`UltraGuiFeaturesPacket`, optional C→S)
   - msg 3 = model sync (`UltraGuiModelSyncPacket`, optional S→C — **only** to clients that advertised `bodyPartsSync` via msg 2; an unregistered message id aborts the connection)
   - Accept clients **without** the channel (`remote -> true` / `NetworkRegistry.acceptMissingOr("1")`), or
     every player without KaiPath is kicked at login.
4. **Let KaiPath find you.**
   - Server-only mod: send the announce at login ([`UltraGuiAnnounce`](server/UltraGuiAnnounce.java), one
     custom payload on `kaipath:partners`). KaiPath then reads your channel for that session: no KaiPath
     release, no player config, no restart.
   - Mod installed on clients too: ship `META-INF/kaipath-ultra.txt` (first non-comment line = your mod id;
     see [`server/META-INF/kaipath-ultra.txt.example`](server/META-INF/kaipath-ultra.txt.example)) and link (step 2).
   - Either way, `config/kaipath-ultra-partners.txt` and the built-in seed (`legacymechanics`) still work.
5. **Open only for players whose client advertised** (msg 2). Without KaiPath nobody draws your packet: tell
   those players in chat instead.
6. Send opens with `style` + `activeTab` (+ `inputsJson` / `playerModel` / `headerBottom` / `palette` when needed). Prefer plain ASCII labels; tint via `palette` / `style`.
7. Handle clicks verbatim (`actionId` + `argsJson` echoed from the button), and check them on the server:
   a client can send any action id.
8. Chrome tokens (`frame`, `buttonSkin`, `portraitFrame`) only switch chrome on; KaiPath binds no texture from
   them. Use **your own namespace** (`myshop:panel`), never another mod's. Sounds: `minecraft:` ids. Never bundle
   client asset files into your server jar.
9. Pin dual footer with `"footer": true` on Back / Main menu (client must advertise `fixedFooter`).
10. Read the pitfalls below before shipping.

11. Model preview: `rotate:false` means front-facing (front toward the viewer, never the back) and resets the
    turntable; use [`PreviewFacing`](kaipath-client/com/dmzlegacy/kaipath/ultra/consumers/legacymechanics/PreviewFacing.java)
    (test `PreviewFacingTest`). LM 4.7.60 contract, no wire change.
12. LM's Saiyan Royal skin (4.7.60) changes no bridge bytes: keep drawing the color letters as sent. The hex
    palette and rounded shapes for a native look are listed in `docs/KAIPATH_GUI_PROTOCOL.md` § Saiyan Royal skin.

13. Real LM menus: `test/fixtures/lm-specs.jsonl` holds one recorded packet per LM menu family. `LmCorpusTest`
    runs each through the kit's encode/decode and checks what KaiPath relies on:
    - buttons inside the panel, at least 80px wide
    - 16px menu rows
    - ASCII-only labels
    - known palette names
    - a still preview is front-facing
    - no Java object printed as text (`[I@2feb1f5f`) in a label or line
    Refresh the corpus with `test/gen_lm_corpus.py` from an LM UI probe run (`tools/lm-ui-probe`).

See [`server/HelloWorldServerSketch.java`](server/HelloWorldServerSketch.java) and
[`server/BridgeFutureFieldsFixture.json`](server/BridgeFutureFieldsFixture.json).

---

## KaiPath / JLDK — merge into next official release

**Released 1.3.13** already honors style / activeTab / titleAlign / playerModel / textInput /
headerBottom / accent / palette / sound / buttonSkin / frame / paged_book / portraitFrame paint.

**Still merge from this kit** (shared renderer for every partner channel):

1. Honor **`footer:true` / `fixedFooter`** — pin footer buttons; raise `contentBottom` above the
   footer band; keep footer widgets **out of** `scrollWidgets`. Helpers:
   [`UltraBridgeFooter`](kaipath-client/com/dmzlegacy/kaipath/ultra/consumers/legacymechanics/UltraBridgeFooter.java)
   + [`FIXED_FOOTER.md`](kaipath-client/com/dmzlegacy/kaipath/ultra/consumers/legacymechanics/FIXED_FOOTER.md)
2. Advertise **`portraitFrame`** + **`fixedFooter`** on every registered channel
3. Keep advertise camelCase **`pagedBook`**; widget type snake_case **`paged_book`**
4. Title vertical centering in the header band (no new field) — confirm gear-icon alignment
5. Partner discovery: [`UltraGuiPartners.discoverAndRegister`](kaipath-client/com/dmzlegacy/kaipath/ultra/consumers/UltraGuiPartners.java) (seed + jar marker + config file)
6. Optional button/tab `icon` — item id (`minecraft:emerald`) or texture path (`modid:path/to/texture.png`). Unknown ids are ignored. KaiPath 1.3.22 advertises `icon`.
7. `combined.forms.v1` is generic: one preview draws the entity plus `playerModel.parts.form` string ids. LM's `headBones` / system accent letters are examples — other mods replace them.
8. **ASCII `fitLabel`** — `UltraWidgetReflect.fitLabel` must use `...` (not mojibaked `…`). See [`FITLABEL_ASCII.md`](kaipath-client/com/dmzlegacy/kaipath/ultra/internal/FITLABEL_ASCII.md)
9. **`bodyParts`** — `playerModel.parts.bodyParts` is `{ "id", "source", "slot" }`. Standard slots: `tail`, `wings`, `horns`, `ears`, `extra_arms`. When DMZUltra is installed, `BodyPartPreview` loads its FormParts catalog and `FormPartsPreviewMixin` attaches known pieces to the preview entity. Unknown ids and a missing source mod are skipped; the base entity still draws. Advertise `bodyParts`. Register [`kaipath-bodyparts.mixins.json`](kaipath-bodyparts.mixins.json) (optional — it does not apply when FormParts is absent). LM example: `{ "id": "saiyan_tail", "source": "dmzultra", "slot": "tail" }` maps to the `tail_fur` mesh.
10. **`fillScreen` fit** — one scale for x and y with the box centered (no stretching on non-16:9
    windows), the title scaled with the header band, and a draggable scroll bar for an overflowing
    content band. Drop in [`UltraBridgeFit`](kaipath-client/com/dmzlegacy/kaipath/ultra/consumers/legacymechanics/UltraBridgeFit.java)
    (pure Java) and follow [`FILLSCREEN_FIT.md`](kaipath-client/com/dmzlegacy/kaipath/ultra/consumers/legacymechanics/FILLSCREEN_FIT.md)
    (exact 1.5.3 insertion points). No wire change. Test: `UltraBridgeFitTest`.
11. **Any mod can be a partner** (no wire change; message ids and codecs unchanged):
    - [`UltraGuiConsumerRegistry`](kaipath-client/com/dmzlegacy/kaipath/ultra/consumers/UltraGuiConsumerRegistry.java):
      a channel another mod already registered is logged and skipped (was a startup crash); `linkServer`,
      `sendOpenBytes`, `sendModelSyncBytes`, `ownsPartnerChannels` for partner server code in the same game
      (clicks and advertises on the integrated server go to the linked partner); raw read and send for announced
      server-only partners.
    - [`UltraGuiAnnounce`](kaipath-client/com/dmzlegacy/kaipath/ultra/consumers/UltraGuiAnnounce.java): the fixed
      `kaipath:partners` event channel (accepts servers without it).
    - [`UltraRawPayloadMixin`](kaipath-client/com/dmzlegacy/kaipath/mixin/UltraRawPayloadMixin.java) +
      [`kaipath-ultra-bridge.mixins.json`](kaipath-ultra-bridge.mixins.json): reads `<modid>:gui` for announced
      namespaces only, before Forge's hook, bytes copied to the main thread.
    - [`UltraGuiPartners`](kaipath-client/com/dmzlegacy/kaipath/ultra/consumers/UltraGuiPartners.java): markers from
      Forge's mod list (jar-in-jar, dev); partners loaded in this game are not registered eagerly;
      `registerLocalPartners()` on `FMLClientSetupEvent` (the kit's `LmUltraConsumer` hooks it).
    - Checked on a test server with a real client (Forge 47.4.10, overlay on KaiPath 1.5.3): a server-only partner
      announced, its menu opened and its click reached its own server channel; the same partner installed on both
      sides linked on the client (no crash) and worked the same way. Not covered: the integrated-server link path
      (singleplayer).
10. **`bodyPartsSync`** (message 3) — register `GuiModelSyncPacket` as S→C message 3 and advertise `bodyPartsSync` **only** in the same release. `FormPartsPreviewMixin` already asks [`BodyPartPreview.meshesFor`](kaipath-client/com/dmzlegacy/kaipath/ultra/consumers/legacymechanics/BodyPartPreview.java) for **every** player — the menu preview overrides for its subject, everyone else reads the world cache fed by message 3 (`BodyPartPreview.applySync`). Snapshots **replace** (never merge); an empty `bodyParts` array clears all extra pieces; `clearSync()` runs on `ClientPlayerNetworkEvent.LoggingOut`. **Broadcast contract (4.6.216):** the server sends one payload to the owner + every tracker with `subject: "uuid:<owner-uuid>"` — key the cache by that uuid, and when it is the receiver's own UUID feed the local state too (or the owner loses their own parts). Payloads carry `parts.colors` (`skin`/`second`/`hair`/`own`/`trim`/`horn`/`light`, packed RGB ints) — prefer them over the reflective `FormParts$Look` read, which comes back gray (`0x808080`) for a remote player. The mixin threads both channels into `mergeLook(look, meshes, colors, partsJson)` via `colorsFor` + `partsJsonFor` (the raw payload rides along so future per-sync metadata reaches the merge site — and `mergeLook` derives `parts.colors` from the payload when the colors argument is absent); `GuiModelSyncPacketTest.mergeLookColorsFromMessage3` pins the chain end to end. `reason` tokens: `login`/`equip`/`unequip`/`clear`/`track`. The server guarantees a single tail-slot entry per payload; the kit mirrors that defensively. **Generic across mods:** the world cache keeps one snapshot per `<modid>:gui` channel and subject (`applySync(channelId, partsJson, reason)`; message 3 is registered with `(p, ctx) -> GuiModelSyncPacket.handle(p, ctx, channelId)`), so each mod replaces only its own pieces and the render shows their union — first channel's tail wins, `suppressNaturalTail` if any channel sets it, `colors` from the first channel (`GuiModelSyncPacketTest.multiChannelSync`). Message 3 is **feature-gated** — a server must never send it to a client that did not advertise `bodyPartsSync` (KaiPath ≤ 1.3.29 has messages 0–2 only). Wire: `docs/KAIPATH_GUI_PROTOCOL.md` § GuiModelSyncPacket.
11. **Advertise after login, not at setup.** At mod setup there is no server connection, so a setup-time `advertiseFeatures` does nothing. The registry advertises on `ClientPlayerNetworkEvent.LoggingIn` (`advertiseAll`, every registered channel) and again on each `GuiOpenPacket`, as KaiPath 1.5.1 does. LM checks about 10 seconds after login.

### Pitfalls (must-read before release)

Full list: [`docs/KAIPATH_BRIDGE_GAPS.md`](../../docs/KAIPATH_BRIDGE_GAPS.md) § Pitfalls.

1. Server jar ≠ client paint
2. Chrome RLs are enable tokens (no texture bind); missing sound is silent
3. Do not add wire fields for `titleTint` / `contentTop` / `contentBottom` / `contentScroll`
4. Prefer `minecraft:` sounds and your own namespace for chrome tokens so DMZUltra-absent clients stay healthy
5. **One channel per name per game:** link where KaiPath runs (step 2); never register `{modid}:gui` beside it
6. **Your channel name is `{modid}:gui`:** if your mod already uses that name for something else, it cannot also
   be an Ultra partner under that id
7. **Server-only mods are invisible to the client** unless they announce (step 4), are seeded, or are in config
8. **Players without KaiPath get nothing:** gate opens on the msg 2 advertise and say so in chat
9. **`layoutJson` over 4,096 chars** is sanitized and loses optional keys (`fillScreen`, `designWidth`, ...);
   a `layoutJson` starting `combined.forms.v1:` is routed to the forms screen
10. **Body-part pieces** render only from `dmzultra` (DMZ Ultra's FormParts); other `source` ids need their own
    catalog on the client and pieces FormParts can draw, else they are skipped (the base model still draws)
11. **Text is the server's:** no translation keys; plain ASCII labels; a label wider than its button is cut with `...`

### Target advertise

```java
UltraGuiConsumerRegistry.advertiseFeatures(
    "legacymechanics:gui",
    "[\"style\",\"activeTab\",\"titleAlign\",\"playerModel\",\"textInput\","
        + "\"headerBottom\",\"accent\",\"palette\",\"sound\",\"buttonSkin\","
        + "\"frame\",\"pagedBook\",\"portraitFrame\",\"fixedFooter\",\"combined.forms.v1\","
        + "\"bodyParts\",\"icon\",\"bodyPartsSync\"]");
```

`"icon"` shipped on official KaiPath 1.3.22 (item id or texture path). 1.3.17 stops at `combined.forms.v1`. `"bodyParts"` shipped on official KaiPath **1.3.29** — a pre-1.3.29 client is now reported as `missingShipped=bodyParts`. A client that does not advertise `bodyParts` still draws the entity. `"bodyPartsSync"` (message 3 world sync) shipped on KaiPath **1.3.31+** (live: 1.5.1) — advertise it only when `GuiModelSyncPacket` is registered in the same release.

```java
// Client boot (wired via LmUltraConsumer.bootstrap):
UltraGuiPartners.discoverAndRegister();
```

Optional local compile check (does **not** replace the official KaiPath release process):

```bash
KAIPATH_BASE_JAR=mods/kaipath-1.3.13.jar \
KAIPATH_OVERLAY_VERSION=1.3.13-lm-ultra \
  bash tools/ultra-gui-kit/build-kaipath-overlay.sh
# → mods/kaipath-1.3.13-lm-ultra.jar  (dev-only smoke artifact)
```

---

Reference server impl (one partner): `tools/dmz-adaptive-difficulty/…/net/gui/` (**4.6.154**).
