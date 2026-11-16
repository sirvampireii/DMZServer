package com.dmzlegacy.kaipath.ultra.consumers.legacymechanics;

import com.dmzlegacy.kaipath.ultra.UltraScreen;
import com.dmzlegacy.kaipath.ultra.UltraUiAccess;
import com.dmzlegacy.kaipath.ultra.UltraUiBridge;
import com.dmzlegacy.kaipath.ultra.consumers.UltraGuiConsumerRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/**
 * Renders whatever the server sends in {@link GuiOpenPacket} — title, tabs, lines, buttons,
 * field 11 {@code inputsJson} (text + {@code paged_book}), button {@code style} /
 * {@code palette} / {@code accent} / {@code sound} / {@code buttonSkin} / {@code icon},
 * {@code layoutJson.headerBottom} / {@code frame}, optional {@code layoutJson.playerModel}
 * (including {@code parts.form} when {@code combined.forms.v1} is advertised and
 * {@code parts.bodyParts} when {@code bodyParts} is advertised),
 * and button {@code footer:true} ({@code fixedFooter}).
 *
 * <p>No hardcoded screen/tab/button lists. Works for <b>any</b> partner mod — clicks echo on
 * the bound {@code <modid>:gui} channel that opened the screen. Advertise via
 * {@link UltraGuiConsumerRegistry#ADVERTISED_FEATURES_JSON}.
 *
 * <p>JLDK merge note: official KaiPath 1.3.13 uses a scroll-clipped content band with a hard-coded
 * {@code panelH - 28} bottom. Port {@link UltraBridgeFooter} into that path — see
 * {@code FIXED_FOOTER.md}.
 */
public class LmDynamicScreen extends UltraScreen {
    /** Default {@code headerBottom} when under-title tabs are present. */
    private static final int DEFAULT_HEADER_BOTTOM_TABS = 28;
    /** Default {@code headerBottom} when there is no under-title tab row. */
    private static final int DEFAULT_HEADER_BOTTOM = 42;
    private static final int DIVIDER_YELLOW_ARGB = 0xFFE8C840;
    private static final int FRAME_EDGE_ARGB = 0x66E8C840;

    private final GuiOpenPacket packet;
    private final String channelId;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private boolean center = true;
    private int margin = GuiOpenPacket.DEFAULT_MARGIN;
    private int lineHeight = 14;
    private int lineGap = 4;
    private int bodyTop = 36;
    private int bodyPad = 0;
    private int bodyMaxHeight = 96;
    private boolean scrollBody = true;
    private int buttonPad = 8;
    private int buttonMinWidth = 80;
    private String titleAlign = "center";
    /** Panel-relative Y where title + yellow divider must end. */
    private int headerBottom = DEFAULT_HEADER_BOTTOM;
    private boolean tabsUnderTitle = false;
    /** Single-letter Minecraft color code from layoutJson (empty = shared gold). */
    private String layoutAccent = "";
    /** Generic palette color name from layoutJson (preferred over letter accent). */
    private String layoutPalette = "";
    /** Namespaced frame id from layoutJson.frame. */
    private String layoutFrame = "";
    private JsonObject playerModel;
    /** When true, scale designWidth x designHeight onto the screen (config editor). */
    private boolean fillScreen;
    private int designW;
    private int designH;
    private float layoutScaleX = 1f;
    private float layoutScaleY = 1f;
    /** Cached preview entity so inspect does not allocate a RemotePlayer every frame. */
    private LivingEntity previewEntity;
    private String previewEntityKey = "";
    private final List<String> bodyLines = new ArrayList<>();
    /** input id → live EditBox (for bindInput / Enter → args.value). */
    private final Map<String, EditBox> textInputs = new LinkedHashMap<>();
    /** input id → args template from inputsJson (for Enter submit). */
    private final Map<String, JsonObject> inputArgsById = new LinkedHashMap<>();
    /** paged_book id → current page index. */
    private final Map<String, Integer> bookPageById = new LinkedHashMap<>();
    /** Button chrome underlays drawn from {@code style} / {@code palette} / {@code accent}. */
    private final List<StyledChrome> buttonChrome = new ArrayList<>();
    /** Tab chrome for activeTab highlight. */
    private final List<StyledChrome> tabChrome = new ArrayList<>();
    private float turntableDegrees;

    public LmDynamicScreen(GuiOpenPacket packet) {
        this(packet, LmGuiNetwork.CHANNEL_ID);
    }

    public LmDynamicScreen(GuiOpenPacket packet, String channelId) {
        super(Component.m_237113_(plainTitle(packet)));
        this.packet = packet == null ? GuiOpenPacket.emptyHub() : packet;
        this.channelId = channelId == null || channelId.isBlank()
                ? LmGuiNetwork.CHANNEL_ID
                : channelId;
    }

    private static String plainTitle(GuiOpenPacket packet) {
        if (packet == null || packet.title() == null || packet.title().isBlank()) {
            return "Legacy Mechanics";
        }
        return packet.title();
    }

    @Override
    protected void m_7856_() {
        m_169413_();
        bodyLines.clear();
        textInputs.clear();
        inputArgsById.clear();
        buttonChrome.clear();
        tabChrome.clear();
        parseLayout();
        placePanel();

        // Title + yellow divider occupy [0, headerBottom). Content starts at/after headerBottom.
        int cursorY = panelY + headerBottom;

        // Compat: tabsJson strip (LM normally sends empty tabsJson + tab buttons at headerBottom)
        JsonArray tabs = parseArray(packet.tabsJson());
        int tabX = panelX + 8;
        int tabW = Math.max(48, Math.min(90, (panelW - 16) / Math.max(1, tabs.size())));
        String active = packet.activeTab() == null ? "" : packet.activeTab().trim();
        String tabHighlightAccent = layoutAccent.isBlank() ? "e" : layoutAccent;
        String tabHighlightPalette = layoutPalette.isBlank() ? "yellow" : layoutPalette;
        for (JsonElement el : tabs) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject tab = el.getAsJsonObject();
            String id = str(tab, "id", "");
            String label = str(tab, "label", id);
            if (id.isBlank()) {
                continue;
            }
            String tabSemantic = stripAtPrefix(id);
            if (tabSemantic.startsWith("tab:")) {
                tabSemantic = tabSemantic.substring(4);
            }
            boolean isActive =
                    !active.isBlank() && active.equalsIgnoreCase(tabSemantic);
            String tabAccent = str(tab, "accent", isActive ? tabHighlightAccent : "");
            String tabPalette = str(tab, "palette", isActive ? tabHighlightPalette : "");
            String tabSound = str(tab, "sound", "");
            String tabIcon = iconId(tab);
            if (isActive || !tabAccent.isBlank() || !tabPalette.isBlank() || !tabIcon.isBlank()) {
                tabChrome.add(
                        new StyledChrome(
                                tabX,
                                cursorY,
                                tabW - 2,
                                16,
                                "normal",
                                tabAccent,
                                tabPalette,
                                "",
                                tabIcon));
            }
            String actionId = id;
            JsonObject tabArgs = new JsonObject();
            addThemedButton(
                    tabX,
                    cursorY,
                    tabW - 2,
                    16,
                    stripSection(label),
                    () -> echoClick(actionId, tabArgs, tabSound));
            tabX += tabW;
        }
        if (tabs.size() > 0) {
            cursorY += 20;
        }

        // fixedFooter — reserve pinned band; body/content must end above it
        JsonArray buttons = parseArray(packet.buttonsJson());
        int footerReserve =
                UltraBridgeFooter.footerReservePx(buttons, 18, Math.max(0, margin));
        int contentBottom =
                UltraBridgeFooter.contentBottomAbs(panelY, panelH, footerReserve);

        // Body lines — honor layoutJson lineHeight/lineGap/bodyMaxHeight; scroll, don't squeeze
        JsonArray lines = parseArray(packet.linesJson());
        for (JsonElement el : lines) {
            if (el != null && el.isJsonPrimitive()) {
                bodyLines.add(el.getAsString());
            }
        }
        int lineStep = Math.max(8, lineHeight + lineGap);
        int pad = Math.max(0, bodyPad);
        int naturalH = bodyLines.isEmpty() ? 0 : pad * 2 + bodyLines.size() * lineStep;
        int listH = scrollBody
                ? Math.max(
                        lineStep + pad * 2,
                        Math.min(
                                Math.max(bodyMaxHeight, lineStep + pad * 2),
                                Math.max(lineStep + pad * 2, naturalH > 0 ? bodyMaxHeight : 0)))
                : Math.max(lineStep + pad * 2, naturalH);
        if (!bodyLines.isEmpty()) {
            int listY = panelY + Math.max(cursorY - panelY, bodyTop);
            // Cap list against pinned footer band (live 1.3.13 hard-coded panelH-28; we raise it).
            int maxListH = Math.max(lineStep, contentBottom - listY);
            listH = Math.min(listH, maxListH);
            addThemedList(panelX + 8, listY, Math.max(1, panelW - 16), listH, bodyLines);
            cursorY = listY + listH + 6;
        }

        // Field 11 inputsJson — text EditBox + paged_book reader
        JsonArray inputs = parseArray(packet.inputsJson());
        for (JsonElement el : inputs) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject in = el.getAsJsonObject();
            String id = str(in, "id", "");
            if (id.isBlank()) {
                continue;
            }
            String type = str(in, "type", "text");
            if ("paged_book".equalsIgnoreCase(type)) {
                cursorY = Math.max(cursorY, addPagedBook(in, cursorY));
                continue;
            }
            if (!"text".equalsIgnoreCase(type)) {
                continue;
            }
            int x = scaleX(intVal(in, "x", 8));
            int y = scaleY(intVal(in, "y", cursorY - panelY));
            int w = Math.max(40, scaleX(intVal(in, "w", panelW - 16)));
            int h = Math.max(14, scaleY(intVal(in, "h", 18)));
            // Keep text fields out of the pinned footer band
            if (panelY + y + h > contentBottom) {
                y = Math.max(0, contentBottom - panelY - h);
            }
            int maxLen = Math.max(1, Math.min(256, intVal(in, "maxLength", 64)));
            String value = str(in, "value", "");
            String placeholder = str(in, "placeholder", "");
            JsonObject inputArgs =
                    in.has("args") && in.get("args").isJsonObject()
                            ? in.getAsJsonObject("args")
                            : new JsonObject();
            EditBox box =
                    new EditBox(
                            this.f_96547_,
                            panelX + x,
                            panelY + y,
                            w,
                            h,
                            Component.m_237113_(placeholder.isBlank() ? id : placeholder));
            box.m_94199_(maxLen);
            box.m_94144_(value);
            if (!placeholder.isBlank()) {
                try {
                    box.getClass()
                            .getMethod("m_259207_", Component.class)
                            .invoke(box, Component.m_237113_(placeholder));
                } catch (Throwable ignored) {
                    // hint optional
                }
            }
            m_142416_(box);
            textInputs.put(id, box);
            inputArgsById.put(id, inputArgs);
        }

        // Buttons — body rows use packet Y; footer:true pins into the reserved band
        for (JsonElement el : buttons) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject b = el.getAsJsonObject();
            String id = str(b, "id", "");
            if (id.isBlank()) {
                continue;
            }
            String label = str(b, "label", id);
            String style = normalizeStyle(str(b, "style", "normal"));
            String accent = UltraBridgeChrome.normalizeAccent(str(b, "accent", ""));
            String palette = UltraBridgeChrome.normalizePalette(str(b, "palette", ""));
            String sound = str(b, "sound", "");
            String skin = UltraBridgeChrome.buttonSkinId(b);
            String icon = iconId(b);
            boolean footer = UltraBridgeFooter.isFooter(b);
            int x = scaleX(intVal(b, "x", 8));
            int h = Math.max(12, scaleY(intVal(b, "h", 18)));
            int y = footer
                    ? UltraBridgeFooter.pinFooterY(panelH, footerReserve, h)
                    : scaleY(intVal(b, "y", cursorY - panelY));
            int w = Math.max(buttonMinWidth, scaleX(intVal(b, "w", 150)));
            int padBtn = intVal(b, "pad", buttonPad);
            if (padBtn < 0) {
                padBtn = buttonPad;
            }
            JsonObject argsObj = new JsonObject();
            if (b.has("args") && b.get("args").isJsonObject()) {
                argsObj = b.getAsJsonObject("args").deepCopy();
            }
            int absX = panelX + x;
            int absY = panelY + y;
            int absW = Math.max(buttonMinWidth, w);
            int absH = h;
            String semantic = stripAtPrefix(id);
            if (semantic.startsWith("tab:")) {
                semantic = semantic.substring(4);
            }
            boolean isActiveTab =
                    !active.isBlank() && active.equalsIgnoreCase(semantic);
            if (isActiveTab && accent.isBlank() && palette.isBlank()) {
                accent = layoutAccent.isBlank() ? "e" : layoutAccent;
                palette = layoutPalette.isBlank() ? "yellow" : layoutPalette;
            }
            if (!style.equals("normal")
                    || !accent.isBlank()
                    || !palette.isBlank()
                    || !skin.isBlank()
                    || !icon.isBlank()) {
                buttonChrome.add(
                        new StyledChrome(absX, absY, absW, absH, style, accent, palette, skin, icon));
            }
            String actionId = id;
            JsonObject args = argsObj;
            String clickSound = sound;
            addThemedButton(
                    absX,
                    absY,
                    absW,
                    absH,
                    styleLabel(style, accent, stripSection(label)),
                    () -> echoClick(actionId, args, clickSound));
            if (!footer) {
                cursorY = Math.max(cursorY, absY + absH + 6);
            }
        }

        // Always offer Close so a sparse packet cannot trap the player (stays in footer band)
        addThemedButton(
                panelX + panelW - 68,
                panelY + panelH - 22,
                60,
                16,
                "Close",
                this::m_7379_);
    }

    private void echoClick(String actionId, JsonObject argsObj) {
        echoClick(actionId, argsObj, "");
    }

    private void echoClick(String actionId, JsonObject argsObj, String soundId) {
        playUiSound(soundId);
        JsonObject args = argsObj == null ? new JsonObject() : argsObj.deepCopy();
        String bind = str(args, "bindInput", "");
        if (!bind.isBlank()) {
            EditBox box = textInputs.get(bind);
            if (box != null) {
                args.addProperty("value", box.m_94155_() == null ? "" : box.m_94155_());
            }
        }
        UltraGuiConsumerRegistry.sendAction(
                channelId, packet.screenId(), actionId, args.toString());
    }

    /** Best-effort namespaced sound play (reflection — no new compile stubs). */
    private void playUiSound(String soundId) {
        if (soundId == null || soundId.isBlank()) {
            return;
        }
        try {
            Minecraft mc = Minecraft.m_91087_();
            if (mc == null || mc.f_91074_ == null) {
                return;
            }
            String raw = soundId.trim();
            int colon = raw.indexOf(':');
            String ns = colon > 0 ? raw.substring(0, colon) : "minecraft";
            String path = colon > 0 ? raw.substring(colon + 1) : raw;
            Class<?> rlClass = Class.forName("net.minecraft.resources.ResourceLocation");
            Object loc = rlClass.getConstructor(String.class, String.class).newInstance(ns, path);
            Class<?> seClass = Class.forName("net.minecraft.sounds.SoundEvent");
            Method create;
            try {
                create = seClass.getMethod("m_262592_", rlClass); // createVariableRangeEvent
            } catch (NoSuchMethodException e) {
                create = seClass.getMethod("createVariableRangeEvent", rlClass);
            }
            Object event = create.invoke(null, loc);
            // LocalPlayer.playSound(SoundEvent, float, float) — SRG m_5496_
            try {
                mc.f_91074_
                        .getClass()
                        .getMethod("m_5496_", seClass, float.class, float.class)
                        .invoke(mc.f_91074_, event, 1f, 1f);
            } catch (NoSuchMethodException e) {
                mc.f_91074_
                        .getClass()
                        .getMethod("playSound", seClass, float.class, float.class)
                        .invoke(mc.f_91074_, event, 1f, 1f);
            }
        } catch (Throwable t) {
            UltraUiBridge.LOGGER.debug("[kaipath] sound play skipped id={}", soundId, t);
        }
    }

    /**
     * {@code type=paged_book} — title + lines for current page, Prev/Next. Returns absolute Y
     * just below the widget for cursor packing.
     */
    private int addPagedBook(JsonObject in, int cursorYAbs) {
        String id = str(in, "id", "");
        int x = scaleX(intVal(in, "x", 8));
        int y = scaleY(intVal(in, "y", cursorYAbs - panelY));
        int w = Math.max(80, scaleX(intVal(in, "w", panelW - 16)));
        int h = Math.max(48, scaleY(intVal(in, "h", 120)));
        String sound = str(in, "sound", "");
        JsonArray pages =
                in.has("pages") && in.get("pages").isJsonArray()
                        ? in.getAsJsonArray("pages")
                        : new JsonArray();
        int pageCount = Math.max(1, pages.size());
        int page = bookPageById.getOrDefault(id, intVal(in, "page", 0));
        if (page < 0) {
            page = 0;
        }
        if (page >= pageCount) {
            page = pageCount - 1;
        }
        bookPageById.put(id, page);
        JsonObject pageObj =
                pages.size() > 0 && pages.get(page).isJsonObject()
                        ? pages.get(page).getAsJsonObject()
                        : new JsonObject();
        String title = stripSection(str(pageObj, "title", "Page " + (page + 1)));
        List<String> lines = new ArrayList<>();
        lines.add(title);
        if (pageObj.has("lines") && pageObj.get("lines").isJsonArray()) {
            for (JsonElement lineEl : pageObj.getAsJsonArray("lines")) {
                if (lineEl != null && lineEl.isJsonPrimitive()) {
                    lines.add(stripSection(lineEl.getAsString()));
                }
            }
        }
        int absX = panelX + x;
        int absY = panelY + y;
        int listH = Math.max(20, h - 22);
        buttonChrome.add(
                new StyledChrome(absX, absY, w, h, "dim", "", layoutPalette, "", ""));
        addThemedList(absX + 2, absY + 2, Math.max(1, w - 4), listH, lines);
        int navY = absY + h - 18;
        int navW = Math.max(40, (w - 8) / 2);
        String bookId = id;
        String bookSound = sound;
        int pageSnapshot = page;
        int pageCountSnapshot = pageCount;
        if (pageSnapshot > 0) {
            addThemedButton(
                    absX + 2,
                    navY,
                    navW,
                    16,
                    "Prev",
                    () -> {
                        bookPageById.put(bookId, pageSnapshot - 1);
                        playUiSound(bookSound);
                        m_7856_();
                    });
        }
        if (pageSnapshot < pageCountSnapshot - 1) {
            addThemedButton(
                    absX + w - navW - 2,
                    navY,
                    navW,
                    16,
                    "Next",
                    () -> {
                        bookPageById.put(bookId, pageSnapshot + 1);
                        playUiSound(bookSound);
                        m_7856_();
                    });
        }
        return absY + h + 4;
    }

    @Override
    public boolean m_7933_(int keyCode, int scanCode, int modifiers) {
        // Enter in a focused text field → submit as input:<id> with args.value
        if (keyCode == 257 || keyCode == 335) { // Enter / Numpad Enter
            for (Map.Entry<String, EditBox> e : textInputs.entrySet()) {
                EditBox box = e.getValue();
                if (box != null && box.m_93696_()) {
                    JsonObject args =
                            inputArgsById
                                    .getOrDefault(e.getKey(), new JsonObject())
                                    .deepCopy();
                    args.addProperty("value", box.m_94155_() == null ? "" : box.m_94155_());
                    UltraGuiConsumerRegistry.sendAction(
                            channelId, packet.screenId(), e.getKey(), args.toString());
                    return true;
                }
            }
        }
        return super.m_7933_(keyCode, scanCode, modifiers);
    }

    private void parseLayout() {
        center = true;
        margin = GuiOpenPacket.DEFAULT_MARGIN;
        fillScreen = false;
        designW = 0;
        designH = 0;
        lineHeight = 14;
        lineGap = 4;
        bodyTop = 36;
        bodyPad = 0;
        bodyMaxHeight = 96;
        scrollBody = true;
        buttonPad = 8;
        buttonMinWidth = 80;
        titleAlign = "center";
        tabsUnderTitle = false;
        layoutAccent = "";
        layoutPalette = "";
        layoutFrame = "";
        headerBottom = DEFAULT_HEADER_BOTTOM;
        playerModel = null;
        panelW = packet.panelWidth() > 0 ? packet.panelWidth() : GuiOpenPacket.DEFAULT_PANEL_W;
        panelH = packet.panelHeight() > 0 ? packet.panelHeight() : GuiOpenPacket.DEFAULT_PANEL_H;
        boolean hasTabsJson = parseArray(packet.tabsJson()).size() > 0;
        String layout = packet.layoutJson();
        if (layout == null || layout.isBlank()) {
            headerBottom = hasTabsJson ? DEFAULT_HEADER_BOTTOM_TABS : DEFAULT_HEADER_BOTTOM;
            return;
        }
        try {
            JsonObject o = JsonParser.parseString(layout).getAsJsonObject();
            if (o.has("center") && o.get("center").isJsonPrimitive()) {
                center = o.get("center").getAsBoolean();
            }
            if (o.has("margin")) {
                margin = Math.max(0, o.get("margin").getAsInt());
            }
            if (o.has("panelWidth")) {
                int w = o.get("panelWidth").getAsInt();
                if (w > 0) {
                    panelW = w;
                }
            } else if (o.has("width")) {
                int w = o.get("width").getAsInt();
                if (w > 0) {
                    panelW = w;
                }
            }
            if (o.has("panelHeight")) {
                int h = o.get("panelHeight").getAsInt();
                if (h > 0) {
                    panelH = h;
                }
            } else if (o.has("height")) {
                int h = o.get("height").getAsInt();
                if (h > 0) {
                    panelH = h;
                }
            }
            if (o.has("lineHeight")) {
                lineHeight = Math.max(8, o.get("lineHeight").getAsInt());
            }
            if (o.has("lineGap")) {
                lineGap = Math.max(0, o.get("lineGap").getAsInt());
            }
            if (o.has("bodyTop")) {
                bodyTop = Math.max(0, o.get("bodyTop").getAsInt());
            }
            if (o.has("bodyPad")) {
                bodyPad = Math.max(0, o.get("bodyPad").getAsInt());
            }
            if (o.has("titleAlign") && o.get("titleAlign").isJsonPrimitive()) {
                String align = o.get("titleAlign").getAsString();
                titleAlign = align == null ? "center" : align.trim().toLowerCase(Locale.ROOT);
            }
            if (o.has("tabsUnderTitle") && o.get("tabsUnderTitle").isJsonPrimitive()) {
                tabsUnderTitle = o.get("tabsUnderTitle").getAsBoolean();
            }
            if (o.has("accent") && o.get("accent").isJsonPrimitive()) {
                layoutAccent = UltraBridgeChrome.normalizeAccent(o.get("accent").getAsString());
            }
            if (o.has("palette") && o.get("palette").isJsonPrimitive()) {
                layoutPalette = UltraBridgeChrome.normalizePalette(o.get("palette").getAsString());
            }
            layoutFrame = UltraBridgeChrome.frameId(o);
            if (o.has("headerBottom")) {
                headerBottom = Math.max(12, o.get("headerBottom").getAsInt());
            } else {
                headerBottom =
                        (tabsUnderTitle || hasTabsJson)
                                ? DEFAULT_HEADER_BOTTOM_TABS
                                : DEFAULT_HEADER_BOTTOM;
            }
            if (o.has("bodyMaxHeight")) {
                bodyMaxHeight = Math.max(lineHeight, o.get("bodyMaxHeight").getAsInt());
            }
            if (o.has("scrollBody") && o.get("scrollBody").isJsonPrimitive()) {
                scrollBody = o.get("scrollBody").getAsBoolean();
            }
            if (o.has("buttonPad")) {
                buttonPad = Math.max(0, o.get("buttonPad").getAsInt());
            }
            if (o.has("buttonMinWidth")) {
                buttonMinWidth = Math.max(20, o.get("buttonMinWidth").getAsInt());
            }
            if (o.has("playerModel") && o.get("playerModel").isJsonObject()) {
                playerModel = o.getAsJsonObject("playerModel");
            }
            if (o.has("fillScreen") && o.get("fillScreen").isJsonPrimitive()) {
                fillScreen = o.get("fillScreen").getAsBoolean();
            }
            if (o.has("designWidth") && o.get("designWidth").isJsonPrimitive()) {
                designW = Math.max(0, o.get("designWidth").getAsInt());
            }
            if (o.has("designHeight") && o.get("designHeight").isJsonPrimitive()) {
                designH = Math.max(0, o.get("designHeight").getAsInt());
            }
        } catch (Throwable t) {
            UltraUiBridge.LOGGER.warn("[kaipath] layoutJson parse failed — using defaults", t);
            headerBottom = hasTabsJson ? DEFAULT_HEADER_BOTTOM_TABS : DEFAULT_HEADER_BOTTOM;
        }
    }

    private void placePanel() {
        layoutScaleX = 1f;
        layoutScaleY = 1f;
        if (fillScreen) {
            int dw = designW > 0 ? designW : Math.max(1, panelW);
            int dh = designH > 0 ? designH : Math.max(1, panelH);
            // One scale for x and y, box centered: a non-matching window letterboxes instead of
            // stretching buttons and the model rectangle (UltraBridgeFit, FILLSCREEN_FIT.md).
            UltraBridgeFit.Placement fit =
                    UltraBridgeFit.fit(this.f_96543_, this.f_96544_, margin, dw, dh);
            panelW = fit.w();
            panelH = fit.h();
            panelX = fit.x();
            panelY = fit.y();
            layoutScaleX = fit.scale();
            layoutScaleY = fit.scale();
            headerBottom = Math.max(12, scaleY(headerBottom));
            bodyTop = Math.max(0, scaleY(bodyTop));
            bodyMaxHeight = Math.max(8, scaleY(bodyMaxHeight));
            lineHeight = Math.max(8, scaleY(lineHeight));
            lineGap = Math.max(0, scaleY(lineGap));
            bodyPad = Math.max(0, scaleY(bodyPad));
            buttonPad = Math.max(0, scaleX(buttonPad));
            buttonMinWidth = Math.max(20, scaleX(buttonMinWidth));
            return;
        }
        panelW = Math.min(panelW, Math.max(64, this.f_96543_ - margin * 2));
        panelH = Math.min(panelH, Math.max(64, this.f_96544_ - margin * 2));
        if (center) {
            panelX = Math.max(margin, (this.f_96543_ - panelW) / 2);
            panelY = Math.max(margin, (this.f_96544_ - panelH) / 2);
        } else {
            panelX = margin;
            panelY = margin;
        }
    }

    /** Design-space X mapped onto the placed panel. Identity unless {@code fillScreen}. */
    private int scaleX(int v) {
        return Math.round(v * layoutScaleX);
    }

    /** Design-space Y mapped onto the placed panel. Identity unless {@code fillScreen}. */
    private int scaleY(int v) {
        return Math.round(v * layoutScaleY);
    }

    @Override
    public void m_280273_(GuiGraphics g) {
        g.m_280509_(0, 0, this.f_96543_, this.f_96544_, 0xC0101010);
        UltraUiAccess.panel(g, theme(), panelX, panelY, panelW, panelH);
    }

    @Override
    public void m_88315_(GuiGraphics g, int mouseX, int mouseY, float tick) {
        m_280273_(g);
        // Optional panel frame chrome (namespaced id — client maps to textures when available)
        if (!layoutFrame.isBlank()) {
            UltraUiAccess.fillRect(g, theme(), panelX, panelY, panelW, 2, FRAME_EDGE_ARGB);
            UltraUiAccess.fillRect(
                    g, theme(), panelX, panelY + panelH - 2, panelW, 2, FRAME_EDGE_ARGB);
            UltraUiAccess.fillRect(g, theme(), panelX, panelY, 2, panelH, FRAME_EDGE_ARGB);
            UltraUiAccess.fillRect(
                    g, theme(), panelX + panelW - 2, panelY, 2, panelH, FRAME_EDGE_ARGB);
        }
        for (StyledChrome c : tabChrome) {
            paintChrome(g, c);
        }
        for (StyledChrome c : buttonChrome) {
            paintChrome(g, c);
        }
        int dividerY = panelY + Math.max(10, headerBottom - 2);
        UltraUiAccess.fillRect(
                g, theme(), panelX + 8, dividerY, Math.max(1, panelW - 16), 1, DIVIDER_YELLOW_ARGB);
        drawPlayerModel(g, mouseX, mouseY, tick);
        UltraUiAccess.themed(theme(), () -> {
            String title = stripSection(packet.title());
            int maxTitleW = Math.max(8, panelW - 16);
            var titleComp = this.f_96547_.m_92834_(title, maxTitleW);
            int titleW = this.f_96547_.m_92852_(titleComp);
            int titleX = "center".equalsIgnoreCase(titleAlign)
                    ? panelX + Math.max(8, (panelW - titleW) / 2)
                    : panelX + 8;
            int fontH = 9;
            int bandH = Math.max(fontH, headerBottom - 2);
            int titleY = panelY + Math.max(2, (bandH - fontH) / 2);
            int titleColor = UltraBridgeChrome.resolveTintArgb(layoutPalette, layoutAccent);
            // fillScreen: the header band is scaled, so the title is too (UltraBridgeFit.titleScale).
            float ts = fillScreen
                    ? UltraBridgeFit.titleScale(layoutScaleY, titleW, fontH, bandH, maxTitleW)
                    : 1f;
            if (ts > 1f) {
                int scaledW = Math.round(titleW * ts);
                int sx = "center".equalsIgnoreCase(titleAlign)
                        ? panelX + Math.max(8, (panelW - scaledW) / 2)
                        : panelX + 8;
                int sy = panelY + Math.max(2, Math.round((bandH - fontH * ts) / 2f));
                g.m_280168_().m_85836_();
                g.m_280168_().m_252880_(sx, sy, 0f);
                g.m_280168_().m_85841_(ts, ts, 1f);
                g.m_280488_(this.f_96547_, titleComp, 0, 0, titleColor);
                g.m_280168_().m_85849_();
            } else {
                g.m_280488_(this.f_96547_, titleComp, titleX, titleY, titleColor);
            }
            super.m_88315_(g, mouseX, mouseY, tick);
        });
    }

    private void paintChrome(GuiGraphics g, StyledChrome c) {
        if (c == null) {
            return;
        }
        int fill = styleArgb(c.style);
        int bar = styleAccentArgb(c.style);
        if ((c.palette != null && !c.palette.isBlank())
                || (c.accent != null && !c.accent.isBlank())) {
            int tint = UltraBridgeChrome.resolveTintArgb(c.palette, c.accent);
            fill = (tint & 0x00FFFFFF) | 0x40000000;
            bar = tint;
        }
        // buttonSkin present — slightly stronger fill so skinned tiles read as chrome
        if (c.skin != null && !c.skin.isBlank() && fill == 0) {
            fill = 0x40222222;
            bar = UltraBridgeChrome.resolveTintArgb(layoutPalette, layoutAccent);
        }
        boolean hasIcon = c.icon != null && !c.icon.isBlank();
        if (fill == 0 && bar == 0 && !hasIcon) {
            return;
        }
        if (fill != 0) {
            UltraUiAccess.fillRect(g, theme(), c.x, c.y, c.w, c.h, fill);
        }
        if (bar != 0) {
            UltraUiAccess.fillRect(g, theme(), c.x, c.y, 3, c.h, bar);
        }
        // icon is an item/texture id. Unknown ids still reserve a mark so the button stays usable.
        if (hasIcon) {
            int mark = bar != 0 ? bar : 0xFF888888;
            int size = Math.min(8, Math.max(4, c.h - 4));
            int iy = c.y + Math.max(0, (c.h - size) / 2);
            UltraUiAccess.fillRect(g, theme(), c.x + 4, iy, size, size, mark);
        }
    }

    /** Optional {@code icon} field: {@code namespace:path}. Blank or invalid → ignored. */
    static String iconId(JsonObject obj) {
        String raw = str(obj, "icon", "").trim();
        int colon = raw.indexOf(':');
        if (colon <= 0 || colon >= raw.length() - 1 || raw.indexOf(':') != raw.lastIndexOf(':')) {
            return "";
        }
        String ns = raw.substring(0, colon);
        String path = raw.substring(colon + 1);
        if (!rlToken(ns) || !rlToken(path)) {
            return "";
        }
        return ns.toLowerCase(Locale.ROOT) + ":" + path.toLowerCase(Locale.ROOT);
    }

    private static boolean rlToken(String token) {
        if (token == null || token.isEmpty() || token.length() > 128) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_'
                    || c == '-'
                    || c == '.'
                    || c == '/';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private void drawPlayerModel(GuiGraphics g, int mouseX, int mouseY, float tick) {
        if (playerModel == null) {
            return;
        }
        int pmX = intVal(playerModel, "x", 0);
        int pmY = intVal(playerModel, "y", 0);
        int pmW = Math.max(16, intVal(playerModel, "width", 64));
        int pmH = Math.max(16, intVal(playerModel, "height", 96));
        float pmScale = playerModel.has("scale") ? playerModel.get("scale").getAsFloat() : 1f;
        boolean pmRotate = playerModel.has("rotate") && playerModel.get("rotate").getAsBoolean();
        // parts (headBones / form / extras) — apply when DMZUltra/geo helpers are present
        JsonObject parts =
                playerModel.has("parts") && playerModel.get("parts").isJsonObject()
                        ? playerModel.getAsJsonObject("parts")
                        : null;
        if (parts != null) {
            UltraUiBridge.LOGGER.debug("[kaipath] playerModel.parts schema={}", str(parts, "schema", "?"));
        }
        String portraitFrame = str(playerModel, "portraitFrame", "");

        Minecraft mc = Minecraft.m_91087_();
        if (mc == null || mc.f_91074_ == null) {
            return;
        }
        // playerModel.subject (or parts.subject) names who to draw. local is this client.
        LivingEntity entity = previewEntity(mc, playerModel, parts);
        if (entity == null) {
            UltraUiAccess.fillRect(g, theme(), panelX + pmX, panelY + pmY, pmW, pmH, 0x33000000);
            return;
        }
        int centerX = panelX + pmX + pmW / 2;
        int centerY = panelY + pmY + pmH;
        int scale = Math.max(8, Math.round(30f * Math.max(0.1f, pmScale)));
        if (pmRotate) {
            turntableDegrees = (turntableDegrees + tick * 1.2f) % 360f;
        }
        float lookX = pmRotate ? (float) Math.sin(Math.toRadians(turntableDegrees)) * 40f : mouseX - centerX;
        float lookY = pmRotate ? -20f : mouseY - (panelY + pmY + pmH / 2f);
        // Portrait frame chrome (namespaced id — texture map is KaiPath/DMZUltra-owned)
        if (!portraitFrame.isBlank()) {
            int fx = panelX + pmX - 2;
            int fy = panelY + pmY - 2;
            int fw = pmW + 4;
            int fh = pmH + 4;
            int edge = UltraBridgeChrome.resolveTintArgb(layoutPalette, layoutAccent);
            UltraUiAccess.fillRect(g, theme(), fx, fy, fw, 2, edge);
            UltraUiAccess.fillRect(g, theme(), fx, fy + fh - 2, fw, 2, edge);
            UltraUiAccess.fillRect(g, theme(), fx, fy, 2, fh, edge);
            UltraUiAccess.fillRect(g, theme(), fx + fw - 2, fy, 2, fh, edge);
        }
        // bodyParts attach while the entity renderer asks DMZUltra FormParts for a look.
        BodyPartPreview.begin(entity, parts);
        boolean drew;
        try {
            drew = renderEntityPreview(g, centerX, centerY, scale, lookX, lookY, entity);
        } finally {
            BodyPartPreview.end();
        }
        if (!drew) {
            UltraUiAccess.fillRect(
                    g, theme(), panelX + pmX, panelY + pmY, pmW, pmH, 0x33000000);
        }
    }

    /**
     * Who {@code playerModel} names. {@code local} (or blank) is the viewing player. A UUID, or
     * {@code uuid:<id>}, is another player. Top-level {@code subject} wins over {@code parts.subject}.
     */
    static String previewSubjectToken(JsonObject model, JsonObject parts) {
        return InspectPreviewSubjects.token(model, parts);
    }

    private LivingEntity previewEntity(Minecraft mc, JsonObject model, JsonObject parts) {
        String token = previewSubjectToken(model, parts);
        if (previewEntity != null && token.equals(previewEntityKey)) {
            return previewEntity;
        }
        previewEntityKey = token;
        previewEntity = lookupPreviewEntity(mc, token);
        return previewEntity;
    }

    /**
     * Resolve a tracked player by uuid, then a client-only {@code RemotePlayer} with that
     * player's tab-list profile (skin). {@code local} is the viewing player. A different UUID
     * never falls back to the viewer.
     */
    private static LivingEntity lookupPreviewEntity(Minecraft mc, String token) {
        LivingEntity local = mc.f_91074_;
        UUID uuid = InspectPreviewSubjects.playerId(token);
        if (uuid == null) {
            return local;
        }
        UUID localId = entityUuid(local);
        if (localId != null && localId.equals(uuid)) {
            return local;
        }
        Object level = invokeNoArg(local, "m_9236_");
        if (level != null) {
            Object found = invoke(level, "m_46003_", new Class<?>[] {UUID.class}, new Object[] {uuid});
            if (!(found instanceof LivingEntity)) {
                found = invoke(level, "getPlayerByUUID", new Class<?>[] {UUID.class}, new Object[] {uuid});
            }
            if (found instanceof LivingEntity living) {
                return living;
            }
        }
        return fakeRemotePlayer(mc, level, uuid);
    }

    private static LivingEntity fakeRemotePlayer(Minecraft mc, Object level, UUID uuid) {
        if (level == null || uuid == null) {
            return null;
        }
        try {
            Object profile = tabProfile(mc, uuid);
            if (profile == null) {
                Class<?> profiles = Class.forName("com.mojang.authlib.GameProfile");
                profile = profiles.getConstructor(UUID.class, String.class).newInstance(uuid, "player");
            }
            Class<?> remote = Class.forName("net.minecraft.client.player.RemotePlayer");
            for (Constructor<?> ctor : remote.getDeclaredConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 2 && params[1].isInstance(profile)) {
                    ctor.setAccessible(true);
                    Object entity = ctor.newInstance(level, profile);
                    if (entity instanceof LivingEntity living) {
                        return living;
                    }
                }
            }
        } catch (Throwable ignored) {
            return null;
        }
        return null;
    }

    /** Tab-list profile carries the skin textures. A blank profile does not. */
    private static Object tabProfile(Minecraft mc, UUID uuid) {
        if (mc == null || uuid == null) {
            return null;
        }
        Object conn = invokeNoArg(mc, "m_91403_");
        if (conn == null) {
            return null;
        }
        Object info = invoke(conn, "m_104949_", new Class<?>[] {UUID.class}, new Object[] {uuid});
        if (info == null) {
            return null;
        }
        return invokeNoArg(info, "m_105312_");
    }

    private static UUID entityUuid(Object entity) {
        Object id = invokeNoArg(entity, "m_20148_");
        if (!(id instanceof UUID)) {
            id = invokeNoArg(entity, "getUUID");
        }
        return id instanceof UUID uuid ? uuid : null;
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null || name == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object[] args) {
        if (target == null || name == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(name, types);
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Call InventoryScreen entity-preview helpers reflectively — SRG names differ slightly
     * across Forge mappings; try the known 1.20.1 candidates.
     */
    private static boolean renderEntityPreview(
            GuiGraphics g,
            int x,
            int y,
            int scale,
            float lookX,
            float lookY,
            LivingEntity entity) {
        try {
            Class<?> inv = Class.forName("net.minecraft.client.gui.screens.inventory.InventoryScreen");
            // Mojmap: renderEntityInInventoryFollowsMouse(GuiGraphics, int, int, int, float, float, LivingEntity)
            String[] names = {"m_275590_", "m_280432_", "renderEntityInInventoryFollowsMouse"};
            for (String name : names) {
                try {
                    Method m =
                            inv.getMethod(
                                    name,
                                    GuiGraphics.class,
                                    int.class,
                                    int.class,
                                    int.class,
                                    float.class,
                                    float.class,
                                    LivingEntity.class);
                    m.invoke(null, g, x, y, scale, lookX, lookY, entity);
                    return true;
                } catch (NoSuchMethodException ignored) {
                    // try next
                }
            }
        } catch (Throwable t) {
            UltraUiBridge.LOGGER.debug("[kaipath] playerModel draw failed", t);
        }
        return false;
    }

    private static String normalizeStyle(String style) {
        if (style == null || style.isBlank()) {
            return "normal";
        }
        String s = style.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "confirm", "danger", "dim", "normal" -> s;
            default -> "normal";
        };
    }

    private static int styleArgb(String style) {
        return switch (normalizeStyle(style)) {
            case "confirm" -> 0x4022AA44;
            case "danger" -> 0x40CC3333;
            case "dim" -> 0x40333333;
            default -> 0;
        };
    }

    private static int styleAccentArgb(String style) {
        return switch (normalizeStyle(style)) {
            case "confirm" -> 0xFF22AA44;
            case "danger" -> 0xFFCC3333;
            case "dim" -> 0xFF666666;
            default -> 0;
        };
    }

    /**
     * Labels stay plain ASCII. Color comes from button {@code style} / {@code palette} /
     * {@code accent} chrome — never prepend section-sign ({@code §a}/{@code §e}/…) which
     * mojibakes into stray A/E glyphs on stock KaiPath clients.
     */
    private static String styleLabel(String style, String accent, String label) {
        return stripSection(label);
    }

    private static String stripAtPrefix(String actionId) {
        if (actionId == null) {
            return "";
        }
        String a = actionId.trim();
        if (a.startsWith("@")) {
            int colon = a.indexOf(':');
            if (colon > 1 && colon < a.length() - 1) {
                return a.substring(colon + 1);
            }
        }
        return a;
    }

    private static JsonArray parseArray(String json) {
        if (json == null || json.isBlank()) {
            return new JsonArray();
        }
        try {
            JsonElement el = JsonParser.parseString(json);
            return el.isJsonArray() ? el.getAsJsonArray() : new JsonArray();
        } catch (Throwable ignored) {
            return new JsonArray();
        }
    }

    private static String str(JsonObject o, String key, String fallback) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return o.get(key).getAsString();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int intVal(JsonObject o, String key, int fallback) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return o.get(key).getAsInt();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    /**
     * Strip section-sign color codes and common UTF-8-as-Latin-1 mojibake (including the
     * stock {@code fitLabel} ellipsis {@code â€¦}) so labels never show stray A/E glyphs.
     */
    private static String stripSection(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String text = s;
        // Mojibaked U+2026 ellipsis (â€¦) and real ellipsis → ASCII ...
        text = text.replace("\u00e2\u20ac\u00a6", "...");
        text = text.replace("\u2026", "...");
        // Drop section-sign + following code (and already-mojibaked C2 A7 sequences).
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' || c == '\u00c2') {
                // §X or leftover C2 from UTF-8 section-sign — skip mark and next code char
                if (i + 1 < text.length()) {
                    i++;
                }
                continue;
            }
            if (c <= 0x7F) {
                out.append(c);
            }
            // drop other non-ASCII (fancy punctuation that mojibakes)
        }
        return out.toString();
    }

    /**
     * Decorative chrome underlay for a button/tab/book tile.
     *
     * @param style semantic style ({@code confirm}/{@code danger}/{@code dim}/{@code normal})
     * @param accent letter color code (compat; empty when palette set)
     * @param palette generic color name (preferred over accent)
     * @param skin optional {@code buttonSkin} id (chrome strength cue until texture blit)
     * @param icon optional {@code namespace:path} drawn as a mark; unknown ids do not hide the button
     */
    private record StyledChrome(
            int x, int y, int w, int h, String style, String accent, String palette, String skin, String icon) {}
}
