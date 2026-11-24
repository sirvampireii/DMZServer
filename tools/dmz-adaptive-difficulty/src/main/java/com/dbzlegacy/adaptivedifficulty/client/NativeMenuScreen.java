package com.dbzlegacy.adaptivedifficulty.client;

import com.dbzlegacy.adaptivedifficulty.gui.ultra.ColorWheel;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.SaiyanRoyal;
import com.dbzlegacy.adaptivedifficulty.net.gui.GuiOpenPacket;
import com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork;
import com.dbzlegacy.adaptivedifficulty.net.gui.NativeMenuFit;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * LM 4.7.58 native menu: draws one server menu spec ({@link GuiOpenPacket}) exactly where the server
 * laid it out, and sends clicks back on {@code legacymechanics:native_ui}.
 *
 * <p>Everything is laid out in the server's design pixels and drawn through one transform
 * ({@link NativeMenuFit}): native size when the panel fits, scaled down to fit (never under 0.5x),
 * and only then the whole menu scrolls. Widgets: title and divider, body text, buttons (normal,
 * confirm, danger, dim; tabs with the active one marked; locked buttons that do nothing), text
 * fields, paged books, the player model, and the Close button in the bottom band.
 *
 * <p>4.7.60 depth system: a rounded panel (14px) lifted on two soft shadow layers, lighter at the top; raised
 * buttons with their own shadow, a top-edge sheen and a darker bottom edge that sink 2px when pressed; inset
 * info boxes, fields, books and bar tracks; glossy bars; the active tab raised with a glow. Only the drawn shapes
 * move or round; every click area stays the full rectangle from the server layout.
 *
 * <p>4.7.62: every tone comes from the player's menu skin ({@link MenuSkins}, five skins, picked on Main menu &gt;
 * Menu skins); each system keeps its accent. A themed background sits behind the panel (or the game world, when
 * backgrounds are off). Polish uses transforms and opacity only: pages crossfade and slide, buttons lift on hover
 * and sink on press, the tab indicator slides, the Momentum bar fills smoothly and shimmers while time is banked,
 * and the keyboard focus has a ring. The background and panel shadow are baked textures and shapes reuse cached
 * geometry, so nothing expensive is recomputed per frame. Lite drops the ambient shadow, glow, sheen and motion,
 * flattens the background, and skips rows outside the view. If the frame rate sags while a menu is open, a small
 * notice suggests Lite once; it never switches anything itself.
 */
public final class NativeMenuScreen extends Screen {
    /** Action id of a locked button: drawn dim, never sends anything. */
    static final String NO_ACTION = "nav:none";
    private static final int CLOSE_BAND = 28;
    private static final int EDGE = 10;

    /** Page change: the old page fades out while the new one fades and slides in. */
    private static final long TRANSITION_NS = 160_000_000L;
    private static final float SLIDE_PX = 6f;
    /** Hover lift / press sink easing rate (per second). */
    private static final float LIFT_RATE = 18f;
    private static final float HOVER_LIFT = 1f;
    private static final long TAB_SLIDE_NS = 180_000_000L;
    private static final long BAR_FILL_NS = 400_000_000L;
    private static final long SHIMMER_NS = 1_800_000_000L;
    /** Frame-rate watch: settle, then measure over a window; under the floor once, the Lite notice shows. */
    private static final long FPS_SETTLE_NS = 1_000_000_000L;
    private static final long FPS_WINDOW_NS = 3_000_000_000L;
    private static final double FPS_FLOOR = 45.0;
    private static final long NOTICE_NS = 8_000_000_000L;
    /** Menu skins picker (hub page) and its apply action. */
    private static final String SKINS_PAGE = "skins";
    private static final String SKIN_ACTION = "do:menu_skin:";
    /** 4.7.66 My colors: its page, the Apply action prefix (then the palette token), the tone field keys. */
    private static final String CUSTOM_PAGE = "skin_custom";
    private static final String CUSTOM_APPLY = "do:menu_custom:apply:";
    private static final String[] CUSTOM_TONES = {"panel", "primary", "trim", "text", "confirm"};
    /** Side of a color field's preview square. */
    private static final int PREVIEW_SQ = 8;

    /** Last Momentum-bar share shown per screen, page and line, so the next packet fills from there. */
    private static final Map<String, Double> BAR_SHOWN = new HashMap<>();
    /** Last active-tab rectangle per screen, so the indicator slides from it. */
    private static final Map<String, int[]> TAB_SHOWN = new HashMap<>();

    /** 4.7.73: logout drops the per-page animation state and the shape cache (nothing carries to another server). */
    static void clearCaches() {
        BAR_SHOWN.clear();
        TAB_SHOWN.clear();
        SHAPES.clear();
    }

    private enum Kind { SERVER, CLOSE, BOOK_PREV, BOOK_NEXT }

    private record Btn(Kind kind, String id, String label, String style, int x, int y, int w, int h, int pad,
            JsonObject args, String sound, String icon, String accent, String palette, String book) {}

    private record Book(String id, int x, int y, int w, int h, JsonArray pages) {}

    private record Field(String id, int x, int y, int w, int h, String value, String placeholder, int maxLength,
            JsonObject args, boolean colorPreview) {}

    /** 4.7.66 color wheel: a hue ring and saturation/value square that fill {@code target} with a hex color. */
    private record Wheel(String id, int x, int y, int w, int h, String target) {
        int side() {
            return Math.max(8, Math.min(w, h));
        }

        int left() {
            return x + (w - side()) / 2;
        }

        int top() {
            return y + (h - side()) / 2;
        }
    }

    private final GuiOpenPacket packet;
    private final List<String> lines = new ArrayList<>();
    private final List<Btn> serverButtons = new ArrayList<>();
    private final List<Btn> buttons = new ArrayList<>();
    private final List<Book> books = new ArrayList<>();
    private final List<Field> fields = new ArrayList<>();
    private final Map<String, EditBox> boxes = new LinkedHashMap<>();
    private final Map<String, Integer> bookPage = new LinkedHashMap<>();
    private final List<Wheel> wheels = new ArrayList<>();
    /** Wheel id to its current {h, s, v}; seeded from the target field's color. */
    private final Map<String, double[]> wheelHsv = new HashMap<>();
    /** The wheel a left-button drag started on, or null. */
    private Wheel dragWheel;
    private final Map<String, String> typed = new LinkedHashMap<>();

    private int panelW;
    private int panelH;
    private int margin = 10;
    private boolean fillScreen;
    private int designW;
    private int designH;
    private int headerBottom = 42;
    private int bodyTop = 42;
    private int bodyPad = 6;
    private int lineH = 11;
    private int lineGap = 2;
    private int bodyMaxH;
    private boolean scrollBody;
    private String titleAlign = "center";
    private String accent = "";
    private String palette = "";
    private boolean frame;
    private JsonObject model;
    /** The player's skin and Lite flag, as of the last look message. */
    private MenuSkins.Skin skin = MenuLook.skin();
    private boolean lite = MenuLook.lite();
    /** This system's accent (exact hex), or the skin's default accent on menus without one. */
    private int accentRgb;

    private NativeMenuFit.Placement place = new NativeMenuFit.Placement(0, 0, 1, 0);
    private float scroll;
    private float bodyScroll;

    // Polish state (transforms and opacity only).
    private final long openedNs = System.nanoTime();
    private NativeMenuScreen previous;
    private final Map<Btn, Float> lift = new HashMap<>();
    private long lastFrameNs;
    private int[] tabFrom;
    private final Map<Integer, Double> barFrom = new HashMap<>();
    private int focus = -1;
    private long fpsStart;
    private int fpsFrames;
    private boolean fpsDone;
    private long noticeUntil;

    public NativeMenuScreen(GuiOpenPacket packet, NativeMenuScreen previous) {
        super(Component.m_237113_(NativeText.plain(packet.title())));
        this.packet = packet;
        parse();
        boolean samePage = previous != null && previous.packet.screenId().equals(packet.screenId())
                && previous.packet.pageId().equals(packet.pageId());
        if (samePage) {
            // Same page again (a toggle or a refresh): keep where the player was, no page transition.
            this.scroll = previous.scroll;
            this.bodyScroll = previous.bodyScroll;
            this.bookPage.putAll(previous.bookPage);
            this.focus = previous.focus;
        } else if (previous != null && !lite) {
            // Navigation: the previous page crossfades out under this one.
            this.previous = previous;
            previous.previous = null;
        }
        int[] lastTab = TAB_SHOWN.get(packet.screenId());
        if (lastTab != null && !lite) {
            tabFrom = lastTab;
        }
        for (int i = 0; i < lines.size(); i++) {
            double share = SaiyanRoyal.barFill(NativeText.plain(lines.get(i)));
            if (share >= 0) {
                barFrom.put(i, BAR_SHOWN.getOrDefault(barKey(i), share));
            }
        }
        if (model != null) {
            NativeModelPreview.onPacket(bool(model, "rotate", false));
        }
    }

    GuiOpenPacket packet() {
        return packet;
    }

    /** 4.7.74: this screen's laid-out text (cleared when the skin or the window changes). */
    private final TextCache textCache = new TextCache();

    /** The player's look changed (message 3) while this menu is open: redraw it in the new skin. */
    void lookChanged() {
        textCache.clear();
        skin = MenuLook.skin();
        lite = MenuLook.lite();
        accentRgb = resolveAccent();
        if (this.f_96541_ != null) {
            m_7856_();
        }
    }

    // ------------------------------------------------------------------ spec

    private void parse() {
        panelW = packet.panelWidth();
        panelH = packet.panelHeight();
        JsonObject layout = obj(packet.layoutJson());
        if (layout != null) {
            panelW = num(layout, "panelWidth", num(layout, "width", panelW));
            panelH = num(layout, "panelHeight", num(layout, "height", panelH));
            margin = Math.max(0, num(layout, "margin", margin));
            fillScreen = bool(layout, "fillScreen", false);
            designW = num(layout, "designWidth", 0);
            designH = num(layout, "designHeight", 0);
            boolean tabs = bool(layout, "tabsUnderTitle", false);
            headerBottom = Math.max(12, num(layout, "headerBottom", tabs ? 28 : 42));
            bodyTop = Math.max(0, num(layout, "bodyTop", headerBottom));
            bodyPad = Math.max(0, num(layout, "bodyPad", bodyPad));
            lineH = Math.max(8, num(layout, "lineHeight", lineH));
            lineGap = Math.max(0, num(layout, "lineGap", lineGap));
            bodyMaxH = Math.max(0, num(layout, "bodyMaxHeight", 0));
            scrollBody = bool(layout, "scrollBody", false);
            titleAlign = str(layout, "titleAlign", "center").toLowerCase(Locale.ROOT);
            accent = str(layout, "accent", "");
            palette = str(layout, "palette", "");
            frame = !str(layout, "frame", "").isBlank();
            if (layout.has("playerModel") && layout.get("playerModel").isJsonObject()) {
                model = layout.getAsJsonObject("playerModel");
            }
            if (layout.has("pictures") && layout.get("pictures").isJsonObject()) {
                pictures = layout.getAsJsonObject("pictures");
            }
            if (layout.has("breadcrumb") && layout.get("breadcrumb").isJsonArray()) {
                breadcrumb = layout.getAsJsonArray("breadcrumb");
            }
        }
        if (fillScreen) {
            if (designW > 0) {
                panelW = designW;
            }
            if (designH > 0) {
                panelH = designH;
            }
        }
        accentRgb = resolveAccent();
        for (JsonElement el : arr(packet.linesJson())) {
            if (el.isJsonPrimitive()) {
                lines.add(el.getAsString());
            }
        }
        for (JsonElement el : arr(packet.buttonsJson())) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject b = el.getAsJsonObject();
            String id = str(b, "id", "");
            if (id.isBlank()) {
                continue;
            }
            JsonObject args = b.has("args") && b.get("args").isJsonObject() ? b.getAsJsonObject("args").deepCopy()
                    : new JsonObject();
            int bx = num(b, "x", EDGE);
            int by = num(b, "y", 0);
            int bw = Math.max(1, num(b, "w", 80));
            int bh = Math.max(8, num(b, "h", 16));
            if (bool(b, "footer", false)) {
                int[] pin = pinFooter(bx, by, bw, bh, panelW, panelH);
                bx = pin[0];
                by = pin[1];
            }
            serverButtons.add(new Btn(Kind.SERVER, id, str(b, "label", id), str(b, "style", "normal"),
                    bx, by, bw, bh,
                    Math.max(0, num(b, "pad", 8)), args, str(b, "sound", ""), str(b, "icon", ""),
                    str(b, "accent", ""), str(b, "palette", ""), ""));
        }
        for (JsonElement el : arr(packet.inputsJson())) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject in = el.getAsJsonObject();
            String id = str(in, "id", "");
            if (id.isBlank()) {
                continue;
            }
            String type = str(in, "type", "text").toLowerCase(Locale.ROOT);
            int x = num(in, "x", EDGE);
            int y = num(in, "y", bodyTop);
            int w = Math.max(40, num(in, "w", panelW - 2 * EDGE));
            if ("paged_book".equals(type)) {
                int h = Math.max(48, num(in, "h", 120));
                JsonArray pages = in.has("pages") && in.get("pages").isJsonArray() ? in.getAsJsonArray("pages")
                        : new JsonArray();
                books.add(new Book(id, x, y, w, h, pages));
                bookPage.put(id, Math.max(0, num(in, "page", 0)));
            } else if ("text".equals(type)) {
                int h = Math.max(12, num(in, "h", 16));
                JsonObject args = in.has("args") && in.get("args").isJsonObject() ? in.getAsJsonObject("args")
                        : new JsonObject();
                fields.add(new Field(id, x, y, w, h, str(in, "value", ""), str(in, "placeholder", ""),
                        Math.max(1, Math.min(256, num(in, "maxLength", 64))), args, bool(in, "colorPreview", false)));
            } else if (ColorWheel.TYPE.equals(type)) {
                int h = Math.max(ColorWheel.MIN_SIZE, num(in, "h", ColorWheel.MIN_SIZE));
                wheels.add(new Wheel(id, x, y, w, h, str(in, "target", "")));
            }
            // Other input types never reach this client: the server routes such menus to KaiPath.
        }
    }

    /** The layout's system accent letter, else its palette name, else the skin's default accent. */
    private int resolveAccent() {
        int sys = accent == null || accent.isBlank() ? -1
                : MenuSkins.systemAccentHex(accent.trim().charAt(accent.trim().length() - 1));
        return sys >= 0 ? sys : MenuSkins.paletteRgb(palette, skin, skin.defaultAccent());
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void m_7856_() {
        textCache.clear();
        for (Map.Entry<String, EditBox> e : boxes.entrySet()) {
            typed.put(e.getKey(), e.getValue().m_94155_());
        }
        boxes.clear();
        place = NativeMenuFit.place(this.f_96543_, this.f_96544_, panelW, panelH, margin, fillScreen, designW, designH);
        scroll = Math.max(0f, Math.min(scroll, place.maxScroll()));
        for (Field f : fields) {
            // The field draws its own rounded frame; the text box sits inside it.
            EditBox box = new EditBox(this.f_96547_, f.x() + 4, f.y() + (f.h() - 8) / 2,
                    f.w() - 8 - (f.colorPreview() ? PREVIEW_SQ + 4 : 0), 8,
                    Component.m_237113_(f.placeholder()));
            box.m_94182_(false);
            box.m_94199_(f.maxLength());
            box.m_94144_(typed.getOrDefault(f.id(), f.value()));
            box.m_94202_(skin.fieldText());
            if (!fieldHint(f).isBlank()) {
                box.m_257771_(Component.m_237113_(fieldHint(f)));
            }
            boxes.put(f.id(), box);
        }
        buttons.clear();
        buttons.addAll(serverButtons);
        for (Book b : books) {
            int pages = Math.max(1, b.pages().size());
            int page = Math.min(pages - 1, bookPage.getOrDefault(b.id(), 0));
            bookPage.put(b.id(), page);
            int navW = Math.max(80, (b.w() - 12) / 3);
            int navY = b.y() + b.h() - 20;
            if (page > 0) {
                buttons.add(new Btn(Kind.BOOK_PREV, b.id() + "#prev", "Previous page", "normal", b.x() + 4, navY,
                        navW, 16, 8, new JsonObject(), "", "", "", "", b.id()));
            }
            if (page < pages - 1) {
                buttons.add(new Btn(Kind.BOOK_NEXT, b.id() + "#next", "Next page", "normal", b.x() + b.w() - 4 - navW,
                        navY, navW, 16, 8, new JsonObject(), "", "", "", "", b.id()));
            }
        }
        int[] close = closeSpot();
        buttons.add(new Btn(Kind.CLOSE, "close", "Close", "normal", close[0], close[1], 80, close[2],
                8, new JsonObject(), "", "", "", "", ""));
        if (focus >= focusables().size()) {
            focus = -1;
        }
    }

    /**
     * Close goes in the bottom band, right side, like KaiPath's. A page whose own controls already
     * use that spot (the config editor's pinned footer) gets it bottom left, else top right.
     */
    private int[] closeSpot() {
        // 4.7.61: a pinned footer owns the bottom band's right end; Close takes the left end of the same row,
        // at the same height, so the band reads as one footer.
        for (Btn b : serverButtons) {
            if (b.y() >= panelH - CLOSE_BAND && free(EDGE, b.y(), 80, b.h())) {
                return new int[] {EDGE, b.y(), b.h()};
            }
        }
        int[][] spots = {
            {panelW - EDGE - 80, panelH - CLOSE_BAND + 6, 16},
            {EDGE, panelH - CLOSE_BAND + 6, 16},
            {panelW - EDGE - 80, 4, Math.max(12, Math.min(16, headerBottom - 8))},
        };
        for (int[] s : spots) {
            if (free(s[0], s[1], 80, s[2])) {
                return s;
            }
        }
        return spots[2];
    }

    /**
     * LM 4.7.61: a {@code footer:true} button the server placed on the design box's edge (the config editor
     * sends margin 0, so its footer sat on the bottom-right corner, cut by the rounded border) moves into the
     * bottom band, {@link #EDGE} inside the right border. A footer already clear of the edges stays put.
     */
    static int[] pinFooter(int x, int y, int w, int h, int panelW, int panelH) {
        if (y + h > panelH - EDGE) {
            y = panelH - CLOSE_BAND + 6;
        }
        if (x + w > panelW - EDGE) {
            x = Math.max(EDGE, panelW - EDGE - w);
        }
        return new int[] {Math.max(EDGE, x), y};
    }

    /**
     * LM 4.7.61: the grey hint inside an empty field, shortened with "..." to the field's inner width. The
     * EditBox draws its hint unclipped, so "Search by name" ran under the Search button on the config editor.
     */
    private String fieldHint(Field f) {
        String hint = f.placeholder() == null ? "" : f.placeholder();
        int room = f.w() - 8;
        if (hint.isBlank() || this.f_96547_.m_92895_(hint) <= room) {
            return hint;
        }
        String cut = this.f_96547_.m_92834_(hint, Math.max(0, room - this.f_96547_.m_92895_("...")));
        return cut.stripTrailing() + "...";
    }

    private boolean free(int x, int y, int w, int h) {
        List<int[]> taken = new ArrayList<>();
        for (Btn b : buttons) {
            taken.add(new int[] {b.x(), b.y(), b.w(), b.h()});
        }
        for (Field f : fields) {
            taken.add(new int[] {f.x(), f.y(), f.w(), f.h()});
        }
        for (Wheel wl : wheels) {
            taken.add(new int[] {wl.x(), wl.y(), wl.w(), wl.h()});
        }
        for (Book b : books) {
            taken.add(new int[] {b.x(), b.y(), b.w(), b.h()});
        }
        if (model != null) {
            taken.add(new int[] {num(model, "x", 0), num(model, "y", 0), num(model, "width", 64), num(model, "height", 96)});
        }
        for (int[] t : taken) {
            if (x < t[0] + t[2] && t[0] < x + w && y < t[1] + t[3] && t[1] < y + h) {
                return false;
            }
        }
        return true;
    }

    private float scale() {
        return place.scale();
    }

    private float originY() {
        return place.y() - scroll;
    }

    private double toDesignX(double mx) {
        return (mx - place.x()) / scale();
    }

    private double toDesignY(double my) {
        return (my - originY()) / scale();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void m_88315_(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = lastFrameNs == 0 ? 0f : Math.min(0.1f, (now - lastFrameNs) / 1.0e9f);
        lastFrameNs = now;
        watchFrameRate(now);
        drawBackdrop(g);
        float t = transition(now);
        if (previous != null) {
            if (t < 1f) {
                previous.drawMenu(g, Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2, partialTick, 1f - t, -SLIDE_PX * t,
                        false, 0f, now);
            } else {
                previous = null;
            }
        }
        drawMenu(g, mouseX, mouseY, partialTick, t, SLIDE_PX * (1f - t), true, dt, now);
        if (place.scrolls()) {
            drawScrollBar(g);
        }
        drawNotice(g, now);
    }

    /** 0 when the page just opened, 1 once its fade-in is over (always 1 in Lite). */
    private float transition(long now) {
        if (lite) {
            return 1f;
        }
        float t = Math.min(1f, (now - openedNs) / (float) TRANSITION_NS);
        return 1f - (1f - t) * (1f - t);
    }

    /**
     * Behind the panel: the skin's baked background (crossfading from the previous skin right after a switch),
     * or the game world under a scrim when backgrounds are off. The config editor covers the window anyway.
     */
    private void drawBackdrop(GuiGraphics g) {
        if (fillScreen) {
            return;
        }
        if (!MenuLook.backgrounds()) {
            g.m_280509_(0, 0, this.f_96543_, this.f_96544_, skin.scrim());
            return;
        }
        MenuSkins.Skin from = MenuLook.fadingFrom();
        if (from != null) {
            MenuBackground.draw(g, this.f_96543_, this.f_96544_, from, lite, 1f);
            MenuBackground.draw(g, this.f_96543_, this.f_96544_, skin, lite, MenuLook.skinFade());
        } else {
            MenuBackground.draw(g, this.f_96543_, this.f_96544_, skin, lite, 1f);
        }
    }

    /** The whole menu at {@code alpha}, shifted {@code dy} screen pixels (page transitions). */
    private void drawMenu(GuiGraphics g, int mouseX, int mouseY, float partialTick, float alpha, float dy,
            boolean live, float dt, long now) {
        if (alpha <= 0.01f) {
            return;
        }
        int mx = (int) Math.floor(toDesignX(mouseX));
        int my = (int) Math.floor(toDesignY(mouseY - dy));
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        PoseStack pose = g.m_280168_();
        pose.m_85836_();
        pose.m_252880_(place.x(), originY() + dy, 0f);
        pose.m_85841_(scale(), scale(), 1f);
        drawPanel(g, alpha);
        drawTitle(g);
        drawBreadcrumb(g, mx, my);
        drawBody(g, now);
        drawPictures(g);
        if (live) {
            drawModel(g);
        }
        for (Book b : books) {
            drawBook(g, b);
        }
        for (Field f : fields) {
            EditBox box = boxes.get(f.id());
            if (box != null) {
                insetBox(g, f.x(), f.y(), f.w(), f.h(), 4, MenuSkins.OPAQUE | (box.m_93696_() ? skin.trim()
                        : skin.fieldEdge()), MenuSkins.OPAQUE | skin.fieldFill(), skin);
                if (live) {
                    box.m_88315_(g, mx, my, partialTick);
                }
                if (f.colorPreview()) {
                    drawFieldColor(g, f, box.m_94155_());
                }
            }
        }
        for (Wheel wl : wheels) {
            drawWheel(g, wl);
        }
        boolean down = live && Minecraft.m_91087_().f_91067_.m_91560_();
        drawTabIndicator(g, now, true);
        for (Btn b : buttons) {
            if (lite && offScreen(b.y(), b.h())) {
                continue;
            }
            boolean hover = live && contains(b, mx, my);
            drawButton(g, b, hover, hover && down, dt);
        }
        drawTabIndicator(g, now, false);
        if (live) {
            drawFocus(g);
        }
        pose.m_85849_();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Lite: rows the player cannot see right now (a scrolling menu) are not drawn at all. */
    private boolean offScreen(int y, int h) {
        if (!place.scrolls()) {
            return false;
        }
        float top = (y + h) * scale() + originY();
        float bottom = y * scale() + originY();
        return top < 0 || bottom > this.f_96544_;
    }

    /**
     * Panel: the baked drop shadow (two layers; one in Lite), a 1px trim border, the panel color as a vertical
     * gradient (lighter at the top), rounded 14px; a trim divider. A framed page adds a faint inner ring in its
     * system accent.
     */
    private void drawPanel(GuiGraphics g, float alpha) {
        int r = SaiyanRoyal.PANEL_RADIUS;
        MenuBackground.panelShadow(g, panelW, panelH, r, skin, lite, alpha);
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        fillRounded(g, 0, 0, panelW, panelH, r, MenuSkins.OPAQUE | skin.trim());
        paint(g, 1, 1, panelW - 2, panelH - 2, r - 1, MenuSkins.OPAQUE | skin.panelTop(),
                MenuSkins.OPAQUE | skin.panelBottom(), panelH - 2);
        if (frame) {
            outlineRounded(g, 2, 2, panelW - 4, panelH - 4, r - 2, MenuSkins.OPAQUE | skin.frameRing(accentRgb));
        }
        int dividerY = Math.max(10, headerBottom - 3);
        fillRounded(g, EDGE, dividerY, panelW - 2 * EDGE, 1, 0, (MenuSkins.DIVIDER_ALPHA << 24) | skin.title());
    }

    private void drawTitle(GuiGraphics g) {
        int maxW = panelW - 2 * EDGE;
        FormattedCharSequence seq = textCache.fit(this.f_96547_, packet.title(), skin.title(), false, true, skin,
                skin.panel(), maxW);
        int tw = textCache.width;
        int band = Math.max(9, headerBottom - 3);
        int ty = Math.max(3, (band - 9) / 2);
        int tx = "left".equals(titleAlign) ? EDGE : (panelW - tw) / 2;
        g.m_280649_(this.f_96547_, seq, tx, ty, MenuSkins.INK_FROM_TEXT, skin.textShadow());
    }

    private int bodyRight() {
        if (model != null) {
            return Math.max(EDGE + 40, num(model, "x", panelW) - 6);
        }
        return panelW - EDGE;
    }

    private int bodyNaturalH() {
        return lines.isEmpty() ? 0 : bodyPad * 2 + lines.size() * (lineH + lineGap);
    }

    private boolean bodyClipped() {
        return scrollBody && bodyMaxH > 0 && bodyNaturalH() > bodyMaxH;
    }

    private void drawBody(GuiGraphics g, long now) {
        if (lines.isEmpty()) {
            return;
        }
        drawInfoBox(g);
        int step = lineH + lineGap;
        int maxW = bodyRight() - EDGE;
        boolean clip = bodyClipped();
        float offset = clip ? bodyScroll : 0f;
        if (clip) {
            float s = scale();
            int x1 = Math.round(place.x() + EDGE * s);
            int y1 = Math.round(originY() + bodyTop * s);
            int x2 = Math.round(place.x() + bodyRight() * s);
            int y2 = Math.round(originY() + (bodyTop + bodyMaxH) * s);
            g.m_280588_(x1, y1, x2, y2);
        }
        NativeText.Ink ink = NativeText.Ink.on(skin, bodyBackground());
        // 4.7.64: on a card page the headers sit on the panel in the skin's trim, like editorial labels.
        boolean cards = !clip && lineGroups(lines) != null;
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            if (raw == null || NativeText.plain(raw).isBlank() || pictureRow(i)) {
                continue;
            }
            int y = Math.round(bodyTop + bodyPad + i * step + (lineH - 9) / 2f + 1 - offset);
            if (lite && offScreen(y, lineH)) {
                continue;
            }
            if (drawSkinLine(g, raw, i, y, maxW, ink, now)) {
                continue;
            }
            int base = cards && headerLine(raw) ? skin.trim() : skin.text();
            FormattedCharSequence seq = textCache.fit(this.f_96547_, raw, base, false, false, ink.skin(), ink.bg(), maxW);
            g.m_280649_(this.f_96547_, seq, EDGE, y, MenuSkins.INK_FROM_TEXT, false);
        }
        if (clip) {
            g.m_280618_();
        }
    }

    // ------------------------------------------------------------------ 4.7.71 breadcrumb

    /** layoutJson {@code breadcrumb}: [title, action] pairs, the main menu first; null for none. */
    private JsonArray breadcrumb;
    /** Where each crumb was drawn (design pixels): {x, y, w, h, index}, rebuilt each frame from the same layout. */
    private final List<int[]> crumbHits = new ArrayList<>();

    /**
     * The breadcrumb under the title, in the strip above the divider: "Main menu > Sparring > Dojo > Upgrades". Each
     * crumb but the last (this page) is a link in the skin's trim, underlined under the mouse; crumbs that do not fit
     * on the left fold into "...".
     */
    private void drawBreadcrumb(GuiGraphics g, int mx, int my) {
        crumbHits.clear();
        if (breadcrumb == null || breadcrumb.isEmpty()) {
            return;
        }
        int n = breadcrumb.size();
        String sep = " > ";
        int sepW = this.f_96547_.m_92895_(sep);
        int max = panelW - 2 * EDGE;
        int first = 0;
        while (first < n - 1 && crumbsWidth(first, sepW) > max) {
            first++;
        }
        int total = crumbsWidth(first, sepW) + (first > 0 ? this.f_96547_.m_92895_("...") + sepW : 0);
        int x = Math.max(EDGE, (panelW - total) / 2);
        int y = Math.max(12, headerBottom - 3) - 11;
        if (first > 0) {
            g.m_280056_(this.f_96547_, "...", x, y, MenuSkins.OPAQUE | skin.fieldEdge(), false);
            x += this.f_96547_.m_92895_("...");
            g.m_280056_(this.f_96547_, sep, x, y, MenuSkins.OPAQUE | skin.fieldEdge(), false);
            x += sepW;
        }
        for (int i = first; i < n; i++) {
            JsonArray c = breadcrumb.get(i).getAsJsonArray();
            String title = c.get(0).getAsString();
            boolean link = c.size() > 1 && !c.get(1).getAsString().isBlank();
            int w = this.f_96547_.m_92895_(title);
            boolean hover = link && mx >= x && mx < x + w && my >= y - 1 && my < y + 9;
            int ink = link ? skin.trim() : skin.text();
            g.m_280056_(this.f_96547_, title, x, y, MenuSkins.OPAQUE | ink, false);
            if (hover) {
                fillRounded(g, x, y + 9, w, 1, 0, MenuSkins.OPAQUE | ink);
            }
            if (link) {
                crumbHits.add(new int[] {x, y - 1, w, 10, i});
            }
            x += w;
            if (i < n - 1) {
                g.m_280056_(this.f_96547_, sep, x, y, MenuSkins.OPAQUE | skin.fieldEdge(), false);
                x += sepW;
            }
        }
    }

    private int crumbsWidth(int from, int sepW) {
        int w = 0;
        for (int i = from; i < breadcrumb.size(); i++) {
            w += this.f_96547_.m_92895_(breadcrumb.get(i).getAsJsonArray().get(0).getAsString()) + (i > from ? sepW : 0);
        }
        return w;
    }

    /** A click on a crumb: the server opens that page (the same action a button would send). */
    private boolean clickCrumb(double x, double y) {
        for (int[] h : crumbHits) {
            if (x >= h[0] && x < h[0] + h[2] && y >= h[1] && y < h[1] + h[3]) {
                JsonArray c = breadcrumb.get(h[4]).getAsJsonArray();
                playClick("");
                JsonObject args = new JsonObject();
                args.addProperty("screen", packet.screenId());
                args.addProperty("page", packet.pageId());
                LmNativeNetwork.sendAction(packet.screenId(), c.get(1).getAsString(), args.toString());
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 4.7.68 pictures

    /** layoutJson {@code pictures} (UltraScreenSpec#pictures), or null. */
    private JsonObject pictures;

    /** A body row the pictures cover in full-width mode: its text is the fallback for clients without pictures. */
    private boolean pictureRow(int i) {
        if (pictures == null || num(pictures, "side", 0) > 0) {
            return false;
        }
        int from = num(pictures, "line", -1);
        return i >= from && i < from + num(pictures, "rows", 0);
    }

    /**
     * The pictures in their rows: side by side, each as large as fits at its aspect, its layers drawn in order (a
     * texture, or a cut-out of one), then its marks (a soft fill and a 1px outline in the mark's colour), then its
     * caption in the corner.
     */
    private void drawPictures(GuiGraphics g) {
        if (pictures == null || !pictures.has("items") || !pictures.get("items").isJsonArray()) {
            return;
        }
        int from = num(pictures, "line", 0), rows = num(pictures, "rows", 0), side = num(pictures, "side", 0);
        int step = lineH + lineGap;
        int top = bodyTop + bodyPad + from * step - 1;
        int bottom = bodyTop + bodyPad + (from + rows) * step - lineGap;
        int left = EDGE, right = bodyRight();
        if (side > 0) {
            left = right - (right - EDGE) * side / 1000;
        }
        JsonArray items = pictures.getAsJsonArray("items");
        int n = Math.max(1, items.size()), gap = 6;
        int cell = (right - left - gap * (n - 1)) / n;
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isJsonObject()) {
                continue;
            }
            JsonObject it = items.get(i).getAsJsonObject();
            double aspect = Math.max(0.2, num(it, "a", 1000) / 1000.0);
            int boxX = left + i * (cell + gap), boxH = bottom - top;
            int w = cell, h = (int) Math.round(cell / aspect);
            if (h > boxH) {
                h = boxH;
                w = (int) Math.round(boxH * aspect);
            }
            int x = boxX + (cell - w) / 2, y = top + (boxH - h) / 2;
            if (it.has("l") && it.get("l").isJsonArray()) {
                for (JsonElement el : it.getAsJsonArray("l")) {
                    drawPictureLayer(g, el, x, y, w, h);
                }
            }
            if (it.has("m") && it.get("m").isJsonArray()) {
                for (JsonElement el : it.getAsJsonArray("m")) {
                    if (!el.isJsonArray() || el.getAsJsonArray().size() < 5) {
                        continue;
                    }
                    JsonArray m = el.getAsJsonArray();
                    int mx = x + w * m.get(0).getAsInt() / 1000, my = y + h * m.get(1).getAsInt() / 1000;
                    int mw = Math.max(2, w * m.get(2).getAsInt() / 1000), mh = Math.max(2, h * m.get(3).getAsInt() / 1000);
                    int rgb = m.get(4).getAsInt() & MenuSkins.RGB_MASK;
                    fillRounded(g, mx, my, mw, mh, 0, (0x66 << 24) | rgb);
                    outlineRounded(g, mx, my, mw, mh, 0, MenuSkins.OPAQUE | rgb);
                }
            }
            String cap = str(it, "c", "");
            if (!cap.isBlank()) {
                FormattedCharSequence seq = textCache.fit(this.f_96547_, cap, skin.text(), false, false, skin,
                        skin.infoFill(), w - 4);
                int tw = this.f_96547_.m_92724_(seq);
                fillRounded(g, x, y, tw + 6, 11, 0, MenuSkins.PICTURE_CAPTION_FILL);
                g.m_280649_(this.f_96547_, seq, x + 3, y + 2, MenuSkins.INK_FROM_TEXT, true);
            }
        }
    }

    /** One layer: [texture, texW, texH, u, v, uW, vH, x, y, w, h] with x..h in thousandths of the picture. */
    private static void drawPictureLayer(GuiGraphics g, JsonElement el, int x, int y, int w, int h) {
        if (!el.isJsonArray() || el.getAsJsonArray().size() < 11) {
            return;
        }
        JsonArray a = el.getAsJsonArray();
        String tex = a.get(0).getAsString();
        if (!tex.matches("[a-z0-9_/]+")) {
            return;
        }
        ResourceLocation rl = new ResourceLocation(com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod.MOD_ID,
                "textures/gui/" + tex + ".png");
        int tw = a.get(1).getAsInt(), th = a.get(2).getAsInt();
        int dx = x + w * a.get(7).getAsInt() / 1000, dy = y + h * a.get(8).getAsInt() / 1000;
        int dw = Math.max(1, w * a.get(9).getAsInt() / 1000), dh = Math.max(1, h * a.get(10).getAsInt() / 1000);
        g.m_280411_(rl, dx, dy, dw, dh, a.get(3).getAsFloat(), a.get(4).getAsFloat(), a.get(5).getAsInt(),
                a.get(6).getAsInt(), tw, th);
    }

    /**
     * The body text sits in an inset info box with an inner shadow, when the box would not run under a
     * control (a page that mixes rows into its text keeps plain text).
     */
    private void drawInfoBox(GuiGraphics g) {
        if (!infoBoxShown()) {
            return;
        }
        int h = (bodyClipped() ? bodyMaxH : bodyNaturalH()) - 2;
        int x = EDGE - 4;
        int w = bodyRight() - EDGE + 8;
        int y = bodyTop + 1;
        // 4.7.64: a page with two or more header lines reads as cards: each header sits on the panel like an
        // editorial label and the lines under it get their own box. Same wire text; KaiPath shows it flat.
        List<int[]> groups = bodyClipped() ? null : lineGroups(lines);
        if (groups != null) {
            int step = lineH + lineGap;
            for (int[] gr : groups) {
                int top = bodyTop + bodyPad + gr[0] * step - 3;
                int bottom = bodyTop + bodyPad + gr[1] * step + lineH + 2;
                insetBox(g, x, top, w, bottom - top, SaiyanRoyal.BOX_RADIUS, MenuSkins.OPAQUE | skin.insetEdge(),
                        MenuSkins.OPAQUE | skin.infoFill(), skin);
            }
            return;
        }
        insetBox(g, x, y, w, h, SaiyanRoyal.BOX_RADIUS, MenuSkins.OPAQUE | skin.insetEdge(),
                MenuSkins.OPAQUE | skin.infoFill(), skin);
    }

    /**
     * The header line marks: UltraStyle.header writes the header letter then bold; the spec drops the header
     * letter on the way out, so a header usually arrives as a line that starts bold.
     */
    private static final String HEADER_MARK = "" + NativeText.MARK + SaiyanRoyal.BRIDGE_HEADER + NativeText.MARK + 'l';
    private static final String BOLD_MARK = "" + NativeText.MARK + 'l';

    private static boolean headerLine(String l) {
        // 4.7.81 (GuiKit): a Notice line also starts bold, but it is not a heading: its text stays in its card.
        return l != null && (l.startsWith(HEADER_MARK) || l.startsWith(BOLD_MARK)) && !notice(l);
    }

    private static boolean notice(String l) {
        String t = l;
        while (t.length() >= 2 && t.charAt(0) == NativeText.MARK) {
            t = t.substring(2);
        }
        return t.startsWith("Notice ");
    }

    /**
     * 4.7.64: {first, last} line index of each run of lines under a header (and of the lines before the first
     * header), or null when the page has fewer than two headers (one box behind the whole body, as before).
     */
    static List<int[]> lineGroups(List<String> body) {
        int headers = 0;
        for (String l : body) {
            if (headerLine(l)) {
                headers++;
            }
        }
        if (headers < 2) {
            return null;
        }
        List<int[]> out = new java.util.ArrayList<>();
        int start = -1;
        for (int i = 0; i < body.size(); i++) {
            String l = body.get(i);
            if (headerLine(l)) {
                if (start >= 0 && i - 1 >= start) {
                    out.add(new int[] {start, i - 1});
                }
                start = i + 1;
            } else if (start < 0) {
                start = i;
            }
        }
        if (start >= 0 && start <= body.size() - 1) {
            out.add(new int[] {start, body.size() - 1});
        }
        return out;
    }

    private boolean infoBoxShown() {
        int h = (bodyClipped() ? bodyMaxH : bodyNaturalH()) - 2;
        return !lines.isEmpty() && h >= 12 && free(EDGE - 4, bodyTop + 1, bodyRight() - EDGE + 8, h);
    }

    private String barKey(int line) {
        return packet.screenId() + "|" + packet.pageId() + "|" + line;
    }

    /**
     * Skinned body lines that end in a text bar ({@code [####----]}) or progress dots ({@code [x] [ ] [ ]})
     * draw the text before it, then a rounded bar or round dots. The bar fills smoothly from the share the
     * last menu showed (Momentum +10s) and shimmers while time is banked. Returns false for an ordinary line.
     */
    private boolean drawSkinLine(GuiGraphics g, String raw, int index, int y, int maxW, NativeText.Ink ink, long now) {
        String plain = NativeText.plain(raw);
        int bar = SaiyanRoyal.barStart(plain);
        int dots = bar < 0 ? SaiyanRoyal.dotsStart(plain) : -1;
        if (bar < 0 && dots < 0) {
            return false;
        }
        int x = EDGE;
        String lead = rawPrefix(raw, bar >= 0 ? bar : dots);
        if (!NativeText.plain(lead).isBlank()) {
            FormattedCharSequence seq = textCache.fit(this.f_96547_, lead, skin.text(), false, false, ink.skin(),
                    ink.bg(), maxW / 2);
            g.m_280649_(this.f_96547_, seq, x, y, MenuSkins.INK_FROM_TEXT, false);
            x += textCache.width + 4;
        }
        int midY = y + 4;
        if (bar >= 0) {
            double target = SaiyanRoyal.barFill(plain);
            double from = barFrom.getOrDefault(index, target);
            double k = lite ? 1 : Math.min(1, (now - openedNs) / (double) BAR_FILL_NS);
            double share = from + (target - from) * (1 - (1 - k) * (1 - k));
            BAR_SHOWN.put(barKey(index), share);
            int w = Math.max(24, Math.min(EDGE + maxW - x, 144));
            int h = 8;
            int ty = midY - h / 2;
            // Recessed track (dim trim rim, darker than the info box so an empty bar still reads), then a
            // glossy trim fill with a highlight along its upper third.
            insetBox(g, x, ty, w, h, SaiyanRoyal.BAR_RADIUS + 1, MenuSkins.OPAQUE | skin.trackRim(),
                    MenuSkins.OPAQUE | skin.trackFill(), skin);
            int fw = share <= 0 ? 0 : Math.max(h - 2, (int) Math.round((w - 2) * share));
            if (fw > 0) {
                glossy(g, x + 1, ty + 1, fw, h - 2, SaiyanRoyal.BAR_RADIUS, skin.trim());
                if (!lite && target > 0) {
                    shimmer(g, x + 1, ty + 1, fw, h - 2, now);
                }
            }
        } else {
            for (boolean done : SaiyanRoyal.dots(plain)) {
                if (done) {
                    glossy(g, x, midY - 4, 8, 8, 4, skin.trim());
                } else {
                    insetBox(g, x, midY - 4, 8, 8, 4, MenuSkins.OPAQUE | skin.dotEdge(), MenuSkins.OPAQUE | skin.dotFill(),
                            skin);
                }
                x += 14;
            }
        }
        return true;
    }

    /** A soft light band sliding across a filled bar (translation only, clipped to the fill). */
    private void shimmer(GuiGraphics g, int x, int y, int w, int h, long now) {
        float phase = (now % SHIMMER_NS) / (float) SHIMMER_NS;
        int band = 10;
        float bx = x - band + phase * (w + band);
        float s = scale();
        g.m_280588_(Math.round(place.x() + x * s), Math.round(originY() + y * s),
                Math.round(place.x() + (x + w) * s), Math.round(originY() + (y + h) * s));
        PoseStack pose = g.m_280168_();
        pose.m_85836_();
        pose.m_252880_(bx, 0f, 0f);
        fillRounded(g, 0, y, band, h, 0, MenuSkins.SHIMMER);
        pose.m_85849_();
        g.m_280618_();
    }

    /** The part of {@code raw} (color codes kept) that holds its first {@code plainLen} visible characters. */
    private static String rawPrefix(String raw, int plainLen) {
        int seen = 0;
        int i = 0;
        while (i < raw.length() && seen < plainLen) {
            if (raw.charAt(i) == NativeText.MARK && i + 1 < raw.length()) {
                i += 2;
                continue;
            }
            seen++;
            i++;
        }
        return raw.substring(0, i);
    }

    private void drawModel(GuiGraphics g) {
        if (model == null) {
            return;
        }
        int x = num(model, "x", 0);
        int y = num(model, "y", 0);
        int w = Math.max(16, num(model, "width", 64));
        int h = Math.max(16, num(model, "height", 96));
        if (!str(model, "portraitFrame", "").isBlank()) {
            insetBox(g, x - 2, y - 2, w + 4, h + 4, 6, MenuSkins.OPAQUE | accentRgb, MenuSkins.OPAQUE | skin.infoFill(),
                    skin);
        }
        LivingEntity entity = NativeModelPreview.resolve(model);
        if (entity == null) {
            fillRounded(g, x, y, w, h, 6, MenuSkins.MODEL_PLACEHOLDER);
            return;
        }
        float scale = model.has("scale") ? (float) dbl(model, "scale", 1.0) : 1f;
        NativeModelPreview.render(g, x, y, w, h, scale, bool(model, "rotate", false), entity,
                model.has("parts") && model.get("parts").isJsonObject() ? model.getAsJsonObject("parts") : null);
    }

    private void drawBook(GuiGraphics g, Book b) {
        insetBox(g, b.x(), b.y(), b.w(), b.h(), SaiyanRoyal.BUTTON_RADIUS, MenuSkins.OPAQUE | skin.fieldEdge(),
                MenuSkins.OPAQUE | skin.bookFill(), skin);
        int pages = Math.max(1, b.pages().size());
        int page = Math.min(pages - 1, bookPage.getOrDefault(b.id(), 0));
        JsonObject p = b.pages().size() > page && b.pages().get(page).isJsonObject()
                ? b.pages().get(page).getAsJsonObject() : new JsonObject();
        int tx = b.x() + 6;
        int maxW = b.w() - 12;
        NativeText.Ink ink = NativeText.Ink.on(skin, skin.bookFill());
        String title = str(p, "title", "Page " + (page + 1)) + "  (" + (page + 1) + "/" + pages + ")";
        g.m_280649_(this.f_96547_, textCache.fit(this.f_96547_, title, skin.title(), false, true, skin, ink.bg(), maxW),
                tx, b.y() + 5, MenuSkins.INK_FROM_TEXT, false);
        int y = b.y() + 18;
        int bottom = b.y() + b.h() - 22;
        if (p.has("lines") && p.get("lines").isJsonArray()) {
            for (JsonElement el : p.getAsJsonArray("lines")) {
                if (!el.isJsonPrimitive()) {
                    continue;
                }
                for (FormattedCharSequence part : textCache.wrap(this.f_96547_, el.getAsString(), skin.text(), skin,
                        ink.bg(), maxW)) {
                    if (y + 9 > bottom) {
                        break;
                    }
                    g.m_280649_(this.f_96547_, part, tx, y, MenuSkins.INK_FROM_TEXT, false);
                    y += lineH + lineGap;
                }
            }
        }
    }

    private void drawButton(GuiGraphics g, Btn b, boolean hover, boolean down, float dt) {
        boolean inert = b.kind() == Kind.SERVER && isNoAction(b.id());
        MenuSkins.Skin swatch = inert ? swatchSkin(b) : null;
        if (swatch != null) {
            drawSwatch(g, b, swatch);
            return;
        }
        String style = inert ? "dim" : b.style().toLowerCase(Locale.ROOT);
        drawSkinButton(g, b, hover, down && !inert, inert, style, isActiveTab(b), dt);
    }

    /**
     * Raised offset of a button, eased toward its target: pressed sinks, hovered lifts, the active tab sits
     * raised, dim rows stay flat. Lite jumps straight to the target (no motion).
     */
    private float liftOf(Btn b, boolean flat, boolean pressed, boolean hover, boolean activeTab, float dt) {
        float target = flat ? 0f : pressed ? SaiyanRoyal.PRESS_SINK
                : activeTab ? -SaiyanRoyal.TAB_LIFT : hover ? -HOVER_LIFT : 0f;
        if (lite) {
            lift.put(b, target);
            return target;
        }
        float now = lift.getOrDefault(b, target);
        float next = now + (target - now) * Math.min(1f, dt * LIFT_RATE);
        if (Math.abs(target - next) < 0.02f) {
            next = target;
        }
        lift.put(b, next);
        return next;
    }

    /**
     * Skinned button or tier row: rounded 8px face with a 1px edge in the skin's tones; the edge takes the
     * system accent (a hub tile its own system's), confirm stays green, danger red, dim and locked flat in the
     * skin's dim tones. Raised rows have their own shadow, a face lighter at the top, a top-edge sheen and a
     * darker bottom edge; they lift on hover and sink 2px pressed, losing shadow and sheen and inverting the
     * gradient. Primary (confirm) buttons glow in the skin's primary. The click area is the full rectangle.
     */
    private void drawSkinButton(GuiGraphics g, Btn b, boolean hover, boolean pressed, boolean inert, String style,
            boolean activeTab, float dt) {
        int[] fe = faceAndEdge(b, style, hover, inert);
        int face = fe[0];
        int edge = fe[1];
        int accentNow = buttonTint(b);
        if (activeTab) {
            edge = MenuSkins.OPAQUE | accentNow;
        }
        boolean flat = "dim".equals(style);
        int r = SaiyanRoyal.BUTTON_RADIUS;
        float off = liftOf(b, flat, pressed, hover, activeTab, dt);
        int w = b.w();
        int h = b.h();
        PoseStack pose = g.m_280168_();
        pose.m_85836_();
        pose.m_252880_(0f, off, 0f);
        int bx = b.x();
        int by = b.y();
        if ("confirm".equals(style) && !lite && !inert) {
            glow(g, bx, by, w, h, r, skin.glow(), 2, SaiyanRoyal.GLOW_ALPHA / 2);
        }
        boolean sunk = off > 0.5f;
        if (!flat && !sunk) {
            shadow(g, bx, by, w, h, r, lite ? 1 : SaiyanRoyal.BUTTON_SHADOW_RINGS, SaiyanRoyal.BUTTON_SHADOW_DROP,
                    skin.shadowAlpha(SaiyanRoyal.BUTTON_SHADOW_ALPHA));
        }
        fillRounded(g, bx, by, w, h, r, edge);
        int rgb = face & MenuSkins.RGB_MASK;
        int top = MenuSkins.OPAQUE | MenuSkins.blend(rgb, MenuSkins.WHITE, flat ? 0 : SaiyanRoyal.FACE_TOP_LIFT);
        int bottom = MenuSkins.OPAQUE | MenuSkins.blend(rgb, MenuSkins.BLACK, flat ? 0 : SaiyanRoyal.FACE_BOTTOM_DROP);
        paint(g, bx + 1, by + 1, w - 2, h - 2, r - 1, sunk ? bottom : top, sunk ? top : bottom, h - 2);
        if (!flat && !sunk && !lite) {
            fillRounded(g, bx + r / 2, by + 1, w - r, 1, 0, skin.edgeHighlight());
            fillRounded(g, bx + r / 2, by + h - 2, w - r, 1, 0, skin.edgeShade());
        }
        int left = b.x() + b.pad();
        int maxW = b.w() - 2 * b.pad();
        if (drawIcon(g, b)) {
            left += 12;
            maxW -= 12;
        }
        boolean muted = "dim".equals(style);
        FormattedCharSequence seq = textCache.fit(this.f_96547_, labelInk(b), skin.text(), muted, false, skin,
                face & MenuSkins.RGB_MASK, Math.max(8, maxW));
        int tw = textCache.width;
        int tx = left + Math.max(0, (maxW - tw) / 2);
        int ty = by + (h - 8) / 2;
        g.m_280649_(this.f_96547_, seq, tx, ty, MenuSkins.INK_FROM_TEXT, !muted && skin.textShadow());
        pose.m_85849_();
    }

    /**
     * The active tab's glow (under the buttons) and underline (over them), sliding from where the last menu of
     * this system had it. Nothing on a page without an active tab.
     */
    private void drawTabIndicator(GuiGraphics g, long now, boolean under) {
        Btn tab = null;
        for (Btn b : buttons) {
            if (isActiveTab(b)) {
                tab = b;
                break;
            }
        }
        if (tab == null) {
            return;
        }
        int[] to = {tab.x(), tab.y() - SaiyanRoyal.TAB_LIFT, tab.w(), tab.h()};
        int[] at = to;
        if (tabFrom != null && !lite) {
            double k = Math.min(1, (now - openedNs) / (double) TAB_SLIDE_NS);
            double e = 1 - (1 - k) * (1 - k);
            at = new int[4];
            for (int i = 0; i < 4; i++) {
                at[i] = (int) Math.round(tabFrom[i] + (to[i] - tabFrom[i]) * e);
            }
            if (k >= 1) {
                tabFrom = null;
            }
        }
        if (!under) {
            TAB_SHOWN.put(packet.screenId(), to);
        }
        int r = SaiyanRoyal.BUTTON_RADIUS;
        if (under) {
            if (!lite) {
                glow(g, at[0], at[1], at[2], at[3], r, skin.glow(), SaiyanRoyal.GLOW_RINGS, SaiyanRoyal.GLOW_ALPHA);
            }
        } else {
            fillRounded(g, at[0] + r, at[1] + at[3] - 3, at[2] - 2 * r, 2, 1, MenuSkins.OPAQUE | buttonTint(tab));
        }
    }

    /**
     * Menu skins picker: the skin a locked row-label previews (same row as a {@code do:menu_skin:<id>}; for Custom
     * the id is the palette token). On My colors (4.7.66) the locked label beside Apply previews the fields as
     * typed: the Apply action's token, with every field that holds a color laid over it.
     */
    private MenuSkins.Skin swatchSkin(Btn b) {
        if ("hub".equalsIgnoreCase(packet.screenId()) && CUSTOM_PAGE.equalsIgnoreCase(packet.pageId())) {
            for (Btn o : serverButtons) {
                String s = semantic(o.id());
                if (o != b && o.y() == b.y() && s.startsWith(CUSTOM_APPLY)) {
                    MenuSkins.Custom c = MenuSkins.parseCustom(s.substring(CUSTOM_APPLY.length()));
                    if (c == null) {
                        return null;
                    }
                    int[] t = c.tones();
                    for (int i = 0; i < CUSTOM_TONES.length; i++) {
                        EditBox box = boxes.get("@hub:input:skin_" + CUSTOM_TONES[i]);
                        int v = box == null ? -1 : MenuSkins.parseHex(box.m_94155_());
                        if (v >= 0) {
                            t[i] = v;
                        }
                    }
                    return MenuSkins.custom(c.withTones(t));
                }
            }
            return null;
        }
        if (!"hub".equalsIgnoreCase(packet.screenId()) || !SKINS_PAGE.equalsIgnoreCase(packet.pageId())) {
            return null;
        }
        for (Btn o : serverButtons) {
            String s = semantic(o.id());
            if (o != b && o.y() == b.y() && s.startsWith(SKIN_ACTION)) {
                return MenuSkins.byId(s.substring(SKIN_ACTION.length()));
            }
        }
        return null;
    }

    /**
     * Live preview of {@code s}: its background, a mini panel with a trim border, a tab and a button in its
     * colors, and its text: "You are using this" on the current skin, a text sample on the others.
     */
    private void drawSwatch(GuiGraphics g, Btn b, MenuSkins.Skin s) {
        int x = b.x();
        int y = b.y();
        int w = b.w();
        int h = b.h();
        paint(g, x, y, w, h, SaiyanRoyal.BUTTON_RADIUS, MenuSkins.OPAQUE | s.bgTop(), MenuSkins.OPAQUE | s.bgBottom(), h);
        fillRounded(g, x + 3, y + 2, w - 6, h - 4, 4, MenuSkins.OPAQUE | s.trim());
        fillRounded(g, x + 4, y + 3, w - 8, h - 6, 3, MenuSkins.OPAQUE | s.panel());
        fillRounded(g, x + 7, y + 5, 12, 3, 1, MenuSkins.OPAQUE | s.primary());
        fillRounded(g, x + 21, y + 5, 10, 3, 1, MenuSkins.OPAQUE | s.face());
        fillRounded(g, x + 7, y + h - 7, 24, 3, 1, MenuSkins.OPAQUE | s.confirmFace());
        String text = swatchText(b, s);
        int left = x + 36;
        int maxW = w - 36 - 6;
        FormattedCharSequence seq = textCache.fit(this.f_96547_, text, s.text(), false, false, s, s.panel(), maxW);
        g.m_280649_(this.f_96547_, seq, left, y + (h - 8) / 2, MenuSkins.INK_FROM_TEXT, s.textShadow());
    }

    private String swatchText(Btn b, MenuSkins.Skin s) {
        if (CUSTOM_PAGE.equalsIgnoreCase(packet.pageId())) {
            return "Preview";
        }
        return s.equals(skin) ? "You are using this" : "Sample text";
    }

    /** The button Enter in field {@code fieldId} clicks (4.7.66 {@code onEnter}), or null for the plain submit. */
    private Btn enterButton(String fieldId) {
        for (Btn o : serverButtons) {
            if (bool(o.args(), "onEnter", false)
                    && java.util.Arrays.asList(str(o.args(), "bindInputs", "").split("\\|")).contains(fieldId)) {
                return o;
            }
        }
        return null;
    }

    /** Small square at a color field's right end showing the color typed in it (a dashed frame when it is not one). */
    private void drawFieldColor(GuiGraphics g, Field f, String text) {
        int x = f.x() + f.w() - 4 - PREVIEW_SQ;
        int y = f.y() + (f.h() - PREVIEW_SQ) / 2;
        int rgb = MenuSkins.parseHex(text);
        fillRounded(g, x - 1, y - 1, PREVIEW_SQ + 2, PREVIEW_SQ + 2, 2, MenuSkins.OPAQUE | skin.fieldEdge());
        if (rgb >= 0) {
            fillRounded(g, x, y, PREVIEW_SQ, PREVIEW_SQ, 1, MenuSkins.OPAQUE | rgb);
        } else {
            fillRounded(g, x, y, PREVIEW_SQ, PREVIEW_SQ, 1, MenuSkins.OPAQUE | skin.fieldFill());
        }
    }

    /** 4.7.82: the field tapped last (a shared wheel colors it). */
    private String lastBox = "";

    /**
     * The field a wheel colors: its target, or for a shared wheel (comma list) the one of its fields tapped last,
     * else the first.
     */
    private String wheelTarget(Wheel w) {
        String t = w.target();
        if (t.indexOf(',') < 0) {
            return t;
        }
        String[] ids = t.split(",");
        for (String id : ids) {
            if (id.equals(lastBox)) {
                return id;
            }
        }
        return ids[0];
    }

    /** {h, s, v} of a wheel, seeded once per target field from that field's color. */
    private double[] hsvOf(Wheel w) {
        String target = wheelTarget(w);
        return wheelHsv.computeIfAbsent(w.id() + "|" + target, k -> {
            EditBox box = boxes.get(target);
            int rgb = box == null ? -1 : MenuSkins.parseHex(box.m_94155_());
            return rgb < 0 ? new double[] {0, 1, 1} : ColorWheel.rgbToHsv(rgb);
        });
    }

    private void drawWheel(GuiGraphics g, Wheel w) {
        int side = w.side();
        int x = w.left();
        int y = w.top();
        double[] hsv = hsvOf(w);
        ColorWheelTex.drawRing(g, x, y, side);
        double[] sq = ColorWheel.square(side);
        int sx = x + (int) Math.round(sq[0]);
        int sy = y + (int) Math.round(sq[1]);
        int ss = (int) Math.round(sq[2]);
        ColorWheelTex.drawSquare(g, sx, sy, ss, hsv[0]);
        // Markers: the hue on the ring, the saturation/value in the square.
        double c = side / 2.0;
        double rr = c * (1 + ColorWheel.RING_INNER) / 2;
        double a = hsv[0] * 2 * Math.PI;
        int hx = x + (int) Math.round(c + rr * Math.sin(a));
        int hy = y + (int) Math.round(c - rr * Math.cos(a));
        marker(g, hx, hy);
        marker(g, sx + (int) Math.round(hsv[1] * ss), sy + (int) Math.round((1 - hsv[2]) * ss));
        if (w.target().indexOf(',') >= 0) {
            // A shared wheel outlines the field it colors.
            String target = wheelTarget(w);
            for (Field f : fields) {
                if (f.id().equals(target)) {
                    int c2 = MenuSkins.OPAQUE | MenuSkins.WHITE;
                    g.m_280509_(f.x() - 2, f.y() - 2, f.x() + f.w() + 2, f.y() - 1, c2);
                    g.m_280509_(f.x() - 2, f.y() + f.h() + 1, f.x() + f.w() + 2, f.y() + f.h() + 2, c2);
                    g.m_280509_(f.x() - 2, f.y() - 2, f.x() - 1, f.y() + f.h() + 2, c2);
                    g.m_280509_(f.x() + f.w() + 1, f.y() - 2, f.x() + f.w() + 2, f.y() + f.h() + 2, c2);
                }
            }
        }
    }

    private static void marker(GuiGraphics g, int x, int y) {
        g.m_280509_(x - 2, y - 2, x + 3, y + 3, MenuSkins.OPAQUE | MenuSkins.BLACK);
        g.m_280509_(x - 1, y - 1, x + 2, y + 2, MenuSkins.OPAQUE | MenuSkins.WHITE);
    }

    /** A tap (or drag) on a wheel: fill its target field with the color picked. True when it hit the wheel. */
    private boolean pickWheel(Wheel w, double x, double y) {
        int side = w.side();
        double px = x - w.left();
        double py = y - w.top();
        if (px < 0 || py < 0 || px >= side || py >= side) {
            return false;
        }
        int rgb = ColorWheel.pick(side, px, py, hsvOf(w));
        if (rgb < 0) {
            return false;
        }
        String target = wheelTarget(w);
        EditBox box = boxes.get(target);
        if (box != null) {
            String hex = MenuSkins.hex(rgb);
            box.m_94144_(hex);
            typed.put(target, hex);
        }
        return true;
    }

    @Override
    public boolean m_7979_(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && dragWheel != null) {
            Wheel w = dragWheel;
            double x = toDesignX(mouseX);
            double y = toDesignY(mouseY);
            // Keep picking while the drag stays inside the wheel's box.
            if (pickWheel(w, x, y)) {
                return true;
            }
        }
        return super.m_7979_(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean m_6348_(double mouseX, double mouseY, int button) {
        dragWheel = null;
        return super.m_6348_(mouseX, mouseY, button);
    }

    /** Face and edge color (ARGB) of a button at rest or hovered, by style; the edge takes the accent. */
    private int[] faceAndEdge(Btn b, String style, boolean hover, boolean inert) {
        return switch (style) {
            case "confirm" -> new int[] {MenuSkins.OPAQUE | (hover ? skin.confirmFaceHover() : skin.confirmFace()),
                    MenuSkins.OPAQUE | skin.confirm()};
            case "danger" -> new int[] {MenuSkins.OPAQUE | (hover ? skin.dangerFaceHover() : skin.dangerFace()),
                    MenuSkins.OPAQUE | MenuSkins.DANGER};
            case "dim" -> new int[] {MenuSkins.OPAQUE | (hover && !inert ? skin.dimFaceHover() : skin.dimFace()),
                    MenuSkins.OPAQUE | skin.dimEdge()};
            default -> {
                int tint = buttonTint(b);
                yield new int[] {MenuSkins.OPAQUE | (hover ? skin.faceHover() : skin.face()),
                        MenuSkins.OPAQUE | (hover ? tint : skin.edgeRest(tint))};
            }
        };
    }

    /** Background behind body text: the info box fill when it is drawn, else the panel. */
    private int bodyBackground() {
        return infoBoxShown() ? skin.infoFill() : skin.panel();
    }

    /**
     * Label text to draw. The spec inks a label in its system accent; the skin already puts that accent on
     * the button edge, so a label whose leading ink is the button's own accent draws in the skin text color
     * (readable on every face). Any other ink, and dim labels, stay as sent.
     */
    private String labelInk(Btn b) {
        String label = b.label() == null ? "" : b.label();
        if (label.length() >= 2 && label.charAt(0) == NativeText.MARK
                && MenuSkins.systemAccentHex(label.charAt(1)) == buttonTint(b)) {
            return label.substring(2);
        }
        // 4.7.61: the bridge darkens yellow and gold labels to dark gray for KaiPath's light button face. On a
        // skinned face that gray is the disabled look, so a live button (Skills' Back to main menu) read as
        // locked. A live button's dark-gray label draws in skin text; dim and locked buttons keep the gray.
        if (label.length() >= 2 && label.charAt(0) == NativeText.MARK && label.charAt(1) == '8'
                && !"dim".equalsIgnoreCase(b.style()) && !(b.kind() == Kind.SERVER && isNoAction(b.id()))) {
            return label.substring(2);
        }
        return label;
    }

    /** Optional button icon: an item id draws that item small; a texture path draws the texture. */
    private boolean drawIcon(GuiGraphics g, Btn b) {
        if (b.icon() == null || b.icon().isBlank()) {
            return false;
        }
        ResourceLocation rl = ResourceLocation.m_135820_(b.icon().toLowerCase(Locale.ROOT));
        if (rl == null) {
            return false;
        }
        int size = Math.min(10, b.h() - 4);
        int ix = b.x() + b.pad();
        int iy = b.y() + (b.h() - size) / 2;
        if (rl.m_135815_().endsWith(".png")) {
            g.m_280163_(rl, ix, iy, 0f, 0f, size, size, size, size);
            return true;
        }
        Item item = BuiltInRegistries.f_257033_.m_7745_(rl);
        if (item == null || item == Items.f_41852_) {
            return false;
        }
        PoseStack pose = g.m_280168_();
        pose.m_85836_();
        pose.m_252880_(ix, iy, 0f);
        pose.m_85841_(size / 16f, size / 16f, 1f);
        g.m_280480_(new ItemStack(item), 0, 0);
        pose.m_85849_();
        return true;
    }

    private void drawScrollBar(GuiGraphics g) {
        float total = panelH * scale();
        float view = total - place.maxScroll();
        int trackTop = margin;
        int trackH = this.f_96544_ - 2 * margin;
        int barH = Math.max(12, Math.round(trackH * view / total));
        int barY = trackTop + Math.round((trackH - barH) * (scroll / place.maxScroll()));
        int x = Math.min(this.f_96543_ - 4, Math.round(place.x() + panelW * scale()) + 3);
        insetBox(g, x - 1, trackTop, 5, trackH, 2, skin.scrollTrack(), skin.scrollTrackEdge(), skin);
        glossy(g, x, barY, 3, barH, 1, skin.trim());
    }

    /** Ring around the control the keyboard is on (Tab / Shift+Tab move it, Enter or Space presses it). */
    private void drawFocus(GuiGraphics g) {
        List<Object> f = focusables();
        if (focus < 0 || focus >= f.size()) {
            return;
        }
        Object o = f.get(focus);
        int[] r = o instanceof Btn b ? new int[] {b.x(), b.y(), b.w(), b.h()}
                : o instanceof Field fl ? new int[] {fl.x(), fl.y(), fl.w(), fl.h()} : null;
        if (r != null) {
            outlineRounded(g, r[0] - 2, r[1] - 2, r[2] + 4, r[3] + 4, SaiyanRoyal.BUTTON_RADIUS + 2,
                    MenuSkins.OPAQUE | skin.focusRing());
        }
    }

    /** Frame-rate watch: after a settle period, one measuring window per menu; a sag shows the Lite notice once. */
    private void watchFrameRate(long now) {
        if (fpsDone || lite || MenuLook.liteSuggested()) {
            return;
        }
        if (now - openedNs < FPS_SETTLE_NS) {
            return;
        }
        if (fpsStart == 0) {
            fpsStart = now;
            fpsFrames = 0;
            return;
        }
        fpsFrames++;
        long span = now - fpsStart;
        if (span >= FPS_WINDOW_NS) {
            fpsDone = true;
            double fps = fpsFrames / (span / 1.0e9);
            if (fps < FPS_FLOOR) {
                MenuLook.markLiteSuggested();
                noticeUntil = now + NOTICE_NS;
            }
        }
    }

    /** The small "menus running slowly" notice at the bottom of the screen. Never blocks a click. */
    private void drawNotice(GuiGraphics g, long now) {
        if (now >= noticeUntil) {
            return;
        }
        String text = "Menus running slowly? Main menu > Menu skins > Look - Lite.";
        int tw = this.f_96547_.m_92895_(text);
        int x = (this.f_96543_ - tw) / 2;
        int y = this.f_96544_ - 18;
        fillRounded(g, x - 6, y - 4, tw + 12, 16, 4, skin.noticeFill());
        g.m_280056_(this.f_96547_, text, x, y, skin.dark() ? skin.text() : MenuSkins.readable(skin.text(),
                skin.noticeFill() & MenuSkins.RGB_MASK, 4.5), false);
    }

    /** Sub-pixel rows per design pixel for rounded corners, so curves stay smooth at GUI scale 3 and 4. */
    private static final int ROUND_Q = 4;

    /** Cached rounded-shape geometry: a shape's rows and colors are computed once, then reused every frame. */
    private record Shape(int x, int y, int w, int h, int radius, int top, int bottom, int rows) {}

    private static final Map<Shape, int[]> SHAPES = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Shape, int[]> eldest) {
            return size() > 2048;
        }
    };

    /** Fill a rounded rectangle (radius in design pixels, capped at half the shorter side) in one color. */
    static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int radius, int argb) {
        paint(g, x, y, w, h, radius, argb, argb, h);
    }

    /**
     * Paint the top {@code rows} design rows of a rounded rectangle with a vertical gradient from {@code top}
     * to {@code bottom} (ARGB). Corners are cut row by row at a quarter-pixel step; the middle goes one design
     * row at a time (or one fill for a flat color). The quads are cached per shape. One batched draw.
     */
    static void paint(GuiGraphics g, int x, int y, int w, int h, int radius, int top, int bottom, int rows) {
        if (w <= 0 || h <= 0 || rows <= 0) {
            return;
        }
        int[] quads = SHAPES.computeIfAbsent(new Shape(x, y, w, h, radius, top, bottom, rows), NativeMenuScreen::quads);
        g.m_286007_(() -> {
            PoseStack pose = g.m_280168_();
            pose.m_85836_();
            pose.m_85841_(1f / ROUND_Q, 1f / ROUND_Q, 1f);
            for (int i = 0; i + 4 < quads.length; i += 5) {
                g.m_280509_(quads[i], quads[i + 1], quads[i + 2], quads[i + 3], quads[i + 4]);
            }
            pose.m_85849_();
        });
    }

    /** {x1, y1, x2, y2, argb}* in quarter pixels for one rounded gradient shape. */
    private static int[] quads(Shape s) {
        int r = SaiyanRoyal.radiusFor(s.w(), s.h(), s.radius());
        int limit = Math.min(s.rows(), s.h());
        int X = s.x() * ROUND_Q;
        int Y = s.y() * ROUND_Q;
        int W = s.w() * ROUND_Q;
        int H = s.h() * ROUND_Q;
        int R = r * ROUND_Q;
        int L = limit * ROUND_Q;
        boolean flat = s.top() == s.bottom();
        List<Integer> out = new ArrayList<>();
        int q = 0;
        while (q < L) {
            boolean corner = q < R || q >= H - R;
            if (corner) {
                int edge = q < R ? q : H - q - 1;
                int in = (int) Math.round(SaiyanRoyal.cornerInset(R, edge + 0.5));
                int c = flat ? s.top() : SaiyanRoyal.lerpArgb(s.top(), s.bottom(), (q + 0.5) / L);
                add(out, X + in, Y + q, X + W - in, Y + q + 1, c);
                q++;
            } else {
                int stop = Math.min(L, H - R);
                int next = flat ? stop : Math.min(stop, q + ROUND_Q);
                int c = flat ? s.top() : SaiyanRoyal.lerpArgb(s.top(), s.bottom(), ((q + next) / 2.0) / L);
                add(out, X, Y + q, X + W, Y + next, c);
                q = next;
            }
        }
        int[] a = new int[out.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = out.get(i);
        }
        return a;
    }

    private static void add(List<Integer> out, int x1, int y1, int x2, int y2, int c) {
        out.add(x1);
        out.add(y1);
        out.add(x2);
        out.add(y2);
        out.add(c);
    }

    /** Soft drop shadow: {@code rings} rounded rings around the box, offset down, fading outwards. */
    static void shadow(GuiGraphics g, int x, int y, int w, int h, int radius, int rings, int drop, int alpha) {
        for (int i = rings; i >= 1; i--) {
            int a = SaiyanRoyal.ringAlpha(alpha, rings, i);
            fillRounded(g, x - i, y - i + drop, w + 2 * i, h + 2 * i, radius + i, a << 24);
        }
    }

    /** Colored glow around a box (no offset), fading outwards. */
    static void glow(GuiGraphics g, int x, int y, int w, int h, int radius, int rgb, int rings, int alpha) {
        for (int i = rings; i >= 1; i--) {
            int a = SaiyanRoyal.ringAlpha(alpha, rings, i);
            fillRounded(g, x - i, y - i, w + 2 * i, h + 2 * i, radius + i, (a << 24) | (rgb & MenuSkins.RGB_MASK));
        }
    }

    /** Rounded box with a 1px edge: the edge color underneath, the fill inset by one pixel on top. */
    static void ring(GuiGraphics g, int x, int y, int w, int h, int radius, int edge, int fill) {
        fillRounded(g, x, y, w, h, radius, edge);
        fillRounded(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), fill);
    }

    /** A 1px rounded outline with nothing filled inside (frames over an already painted surface). */
    static void outlineRounded(GuiGraphics g, int x, int y, int w, int h, int radius, int argb) {
        int r = SaiyanRoyal.radiusFor(w, h, radius);
        fillRounded(g, x + r, y, w - 2 * r, 1, 0, argb);
        fillRounded(g, x + r, y + h - 1, w - 2 * r, 1, 0, argb);
        fillRounded(g, x, y + r, 1, h - 2 * r, 0, argb);
        fillRounded(g, x + w - 1, y + r, 1, h - 2 * r, 0, argb);
        g.m_286007_(() -> {
            PoseStack pose = g.m_280168_();
            pose.m_85836_();
            pose.m_85841_(1f / ROUND_Q, 1f / ROUND_Q, 1f);
            int R = r * ROUND_Q;
            for (int q = 0; q < R; q++) {
                // One sub-row of each corner arc, a pixel thick, at the shape's edge.
                int in = (int) Math.round(SaiyanRoyal.cornerInset(R, q + 0.5));
                int inNext = (int) Math.round(SaiyanRoyal.cornerInset(R, q + 1.5));
                int span = Math.max(ROUND_Q, in - inNext + ROUND_Q);
                int X = x * ROUND_Q;
                int Y = y * ROUND_Q;
                int W = w * ROUND_Q;
                int H = h * ROUND_Q;
                g.m_280509_(X + in, Y + q, X + in + span, Y + q + 1, argb);
                g.m_280509_(X + W - in - span, Y + q, X + W - in, Y + q + 1, argb);
                g.m_280509_(X + in, Y + H - q - 1, X + in + span, Y + H - q, argb);
                g.m_280509_(X + W - in - span, Y + H - q - 1, X + W - in, Y + H - q, argb);
            }
            pose.m_85849_();
        });
    }

    /** Inset box: a 1px edge, the fill, and an inner shadow fading down from the top edge (skin strength). */
    static void insetBox(GuiGraphics g, int x, int y, int w, int h, int radius, int edge, int fill, MenuSkins.Skin skin) {
        ring(g, x, y, w, h, radius, edge, fill);
        int rows = Math.min(SaiyanRoyal.INSET_SHADOW_PX, Math.max(1, (h - 2) / 2));
        paint(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), skin.insetShadowAlpha() << 24, 0, rows);
    }

    /** Glossy fill: a gradient from a light top to a darker bottom, with a highlight along the upper third. */
    static void glossy(GuiGraphics g, int x, int y, int w, int h, int radius, int rgb) {
        paint(g, x, y, w, h, radius, MenuSkins.OPAQUE | MenuSkins.blend(rgb, MenuSkins.WHITE, 0.35),
                MenuSkins.OPAQUE | MenuSkins.blend(rgb, MenuSkins.BLACK, 0.25), h);
        int r = SaiyanRoyal.radiusFor(w, h, radius);
        if (h >= 4 && w > 2 * r + 2) {
            fillRounded(g, x + r, y + Math.max(1, h / 3 - 1), w - 2 * r, 1, 0, MenuSkins.GLOSS_LINE);
        }
    }

    // ------------------------------------------------------------------ input

    private static boolean contains(Btn b, double x, double y) {
        return x >= b.x() && x < b.x() + b.w() && y >= b.y() && y < b.y() + b.h();
    }

    /** Keyboard order: live buttons and fields, top to bottom, left to right. */
    private List<Object> focusables() {
        List<Object> out = new ArrayList<>();
        for (Btn b : buttons) {
            if (!(b.kind() == Kind.SERVER && isNoAction(b.id()))) {
                out.add(b);
            }
        }
        out.addAll(fields);
        out.sort(Comparator.<Object>comparingInt(o -> o instanceof Btn b ? b.y() : ((Field) o).y())
                .thenComparingInt(o -> o instanceof Btn b ? b.x() : ((Field) o).x()));
        return out;
    }

    private void moveFocus(int delta) {
        List<Object> f = focusables();
        if (f.isEmpty()) {
            return;
        }
        focus = focus < 0 ? (delta > 0 ? 0 : f.size() - 1) : Math.floorMod(focus + delta, f.size());
        Object o = f.get(focus);
        for (Map.Entry<String, EditBox> e : boxes.entrySet()) {
            e.getValue().m_93692_(o instanceof Field fl && fl.id().equals(e.getKey()));
        }
        if (o instanceof Field fl) {
            lastBox = fl.id();
        }
    }

    @Override
    public boolean m_6375_(double mouseX, double mouseY, int button) {
        double x = toDesignX(mouseX);
        double y = toDesignY(mouseY);
        if (button == 0) {
            if (clickCrumb(x, y)) {
                return true;
            }
            for (Wheel wl : wheels) {
                if (pickWheel(wl, x, y)) {
                    dragWheel = wl;
                    return true;
                }
            }
            focus = -1;
            boolean anyBox = false;
            for (Field f : fields) {
                EditBox box = boxes.get(f.id());
                if (box == null) {
                    continue;
                }
                boolean hit = x >= f.x() && x < f.x() + f.w() && y >= f.y() && y < f.y() + f.h();
                box.m_93692_(hit);
                if (hit) {
                    lastBox = f.id();
                    // A skinned field's text box sits inside the rounded frame: aim the click into it.
                    double bx = Math.max(box.m_252754_(), Math.min(box.m_252754_() + box.m_5711_() - 1, x));
                    double by = Math.max(box.m_252907_(), Math.min(box.m_252907_() + box.m_93694_() - 1, y));
                    box.m_6375_(bx, by, button);
                    box.m_93692_(true);
                    anyBox = true;
                }
            }
            if (anyBox) {
                return true;
            }
            for (int i = buttons.size() - 1; i >= 0; i--) {
                Btn b = buttons.get(i);
                if (contains(b, x, y)) {
                    click(b);
                    return true;
                }
            }
        }
        return super.m_6375_(mouseX, mouseY, button);
    }

    private void click(Btn b) {
        switch (b.kind()) {
            case CLOSE -> {
                playClick("");
                m_7379_();
            }
            case BOOK_PREV, BOOK_NEXT -> {
                playClick("");
                int delta = b.kind() == Kind.BOOK_NEXT ? 1 : -1;
                bookPage.put(b.book(), Math.max(0, bookPage.getOrDefault(b.book(), 0) + delta));
                m_7856_();
            }
            case SERVER -> {
                if (isNoAction(b.id())) {
                    // Locked: the page text says what is missing; the click does nothing.
                    return;
                }
                playClick(b.sound());
                JsonObject args = b.args().deepCopy();
                String bind = str(args, "bindInput", "");
                if (!bind.isBlank() && boxes.containsKey(bind)) {
                    args.addProperty("value", boxes.get(bind).m_94155_());
                }
                // 4.7.66: a page that reads several fields per click lists them in bindInputs.
                for (String fid : str(args, "bindInputs", "").split("\\|")) {
                    EditBox fb = fid.isBlank() ? null : boxes.get(fid);
                    if (fb != null) {
                        args.addProperty(fid, fb.m_94155_());
                    }
                }
                LmNativeNetwork.sendAction(packet.screenId(), b.id(), args.toString());
            }
        }
    }

    @Override
    public boolean m_7933_(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 258) {
            // Tab / Shift+Tab: move the focus ring.
            moveFocus((modifiers & 1) != 0 ? -1 : 1);
            return true;
        }
        for (Field f : fields) {
            EditBox box = boxes.get(f.id());
            if (box == null || !box.m_93696_()) {
                continue;
            }
            if (keyCode == 257 || keyCode == 335) {
                Btn onEnter = enterButton(f.id());
                if (onEnter != null) {
                    click(onEnter);
                    return true;
                }
                JsonObject args = f.args().deepCopy();
                args.addProperty("value", box.m_94155_());
                playClick("");
                LmNativeNetwork.sendAction(packet.screenId(), f.id(), args.toString());
                return true;
            }
            if (keyCode != 256 && box.m_7933_(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (keyCode != 256) {
                // Typing goes to the field, never to a game key binding.
                return true;
            }
        }
        List<Object> f = focusables();
        if ((keyCode == 257 || keyCode == 335 || keyCode == 32) && focus >= 0 && focus < f.size()
                && f.get(focus) instanceof Btn b) {
            click(b);
            return true;
        }
        return super.m_7933_(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean m_5534_(char c, int modifiers) {
        for (EditBox box : boxes.values()) {
            if (box.m_93696_()) {
                return box.m_5534_(c, modifiers);
            }
        }
        return super.m_5534_(c, modifiers);
    }

    @Override
    public boolean m_6050_(double mouseX, double mouseY, double delta) {
        if (place.scrolls()) {
            scroll = Math.max(0f, Math.min(place.maxScroll(), scroll - (float) delta * 16f));
            return true;
        }
        if (bodyClipped()) {
            float max = bodyNaturalH() - bodyMaxH;
            bodyScroll = Math.max(0f, Math.min(max, bodyScroll - (float) delta * (lineH + lineGap)));
            return true;
        }
        return super.m_6050_(mouseX, mouseY, delta);
    }

    private void playClick(String soundId) {
        Minecraft mc = Minecraft.m_91087_();
        SoundEvent custom = null;
        if (soundId != null && !soundId.isBlank()) {
            ResourceLocation rl = ResourceLocation.m_135820_(soundId);
            custom = rl == null ? null : BuiltInRegistries.f_256894_.m_7745_(rl);
        }
        if (custom != null) {
            mc.m_91106_().m_120367_(SimpleSoundInstance.m_119752_(custom, 1.0f));
        } else {
            mc.m_91106_().m_120367_(SimpleSoundInstance.m_263171_(SoundEvents.f_12490_, 1.0f));
        }
    }

    @Override
    public boolean m_7043_() {
        return false;
    }

    // ------------------------------------------------------------------ helpers

    static boolean isNoAction(String id) {
        return NO_ACTION.equals(semantic(id));
    }

    /** {@code @screen:rest} to {@code rest}. */
    static String semantic(String id) {
        String a = id == null ? "" : id.trim();
        if (a.startsWith("@")) {
            int c = a.indexOf(':');
            if (c > 1 && c < a.length() - 1) {
                return a.substring(c + 1);
            }
        }
        return a;
    }

    private boolean isActiveTab(Btn b) {
        if (b.kind() != Kind.SERVER) {
            return false;
        }
        String s = semantic(b.id());
        String active = packet.activeTab() == null ? "" : packet.activeTab().trim();
        return s.startsWith("tab:") && !active.isEmpty() && s.substring(4).equalsIgnoreCase(active);
    }

    /**
     * Accent of one button: a hub tile's own system palette when it names one, else this screen's accent.
     * (A button's {@code accent} letter is its label ink, not chrome.)
     */
    private int buttonTint(Btn b) {
        return MenuSkins.paletteRgb(b.palette(), skin, accentRgb);
    }

    /**
     * Screen-space layout for test probes: one line per drawn control
     * ({@code kind x y w h style "label"}), after the fit transform and scroll. Button lines end with
     * {@code face=RRGGBB ink=RRGGBB cut=true|false} (cut: the label did not fit and was shortened); body lines
     * have a matching {@code I index ink=RRGGBB bg=RRGGBB} line, so the rig can check contrast. The SKIN line
     * names the player's skin, backgrounds and look; a picker swatch reports the skin it previews.
     */
    public List<String> debugLayout() {
        List<String> out = new ArrayList<>();
        out.add(String.format(Locale.ROOT, "PANEL %.1f %.1f %.1f %.1f scale=%.3f scroll=%.1f maxScroll=%.1f",
                place.x(), originY(), panelW * scale(), panelH * scale(), scale(), scroll, place.maxScroll()));
        out.add(String.format(Locale.ROOT, "SKIN %s accent=%06X bg=%s look=%s dark=%s baked=%d", skin.id(), accentRgb,
                MenuLook.backgrounds() && !fillScreen ? "on" : "off", lite ? "lite" : "full", skin.dark(),
                MenuBackground.bakedCount()));
        for (Btn b : buttons) {
            boolean inert = b.kind() == Kind.SERVER && isNoAction(b.id());
            MenuSkins.Skin swatch = inert ? swatchSkin(b) : null;
            String style = inert ? "dim" : b.style().toLowerCase(Locale.ROOT);
            if (swatch != null) {
                out.add(String.format(Locale.ROOT, "B %.1f %.1f %.1f %.1f swatch %s \"%s\" face=%06X ink=%06X cut=false",
                        place.x() + b.x() * scale(), originY() + b.y() * scale(), b.w() * scale(), b.h() * scale(),
                        "swatch:" + swatch.id(), swatchText(b, swatch), swatch.panel(),
                        MenuSkins.readable(swatch.text(), swatch.panel(), swatch.dark() ? 3.0 : 4.5)));
                continue;
            }
            String label = labelInk(b);
            boolean muted = "dim".equals(style);
            int face = faceAndEdge(b, style, false, inert)[0] & MenuSkins.RGB_MASK;
            NativeText.Ink ink = NativeText.Ink.on(skin, face);
            int room = b.w() - 2 * b.pad() - (b.icon() == null || b.icon().isBlank() ? 0 : 12);
            boolean cut = this.f_96547_.m_92852_(NativeText.parse(label, skin.text(), muted, false, ink)) > room;
            out.add(String.format(Locale.ROOT, "B %.1f %.1f %.1f %.1f %s %s \"%s\" face=%06X ink=%06X cut=%s",
                    place.x() + b.x() * scale(), originY() + b.y() * scale(), b.w() * scale(), b.h() * scale(),
                    inert ? "locked" : b.style(), b.kind() == Kind.SERVER ? b.id() : b.kind().name(),
                    NativeText.plain(b.label()), face, NativeText.firstInk(label, skin.text(), muted, ink), cut));
        }
        for (Field f : fields) {
            out.add(String.format(Locale.ROOT, "T %.1f %.1f %.1f %.1f %s hint=\"%s\" hintw=%d room=%d",
                    place.x() + f.x() * scale(), originY() + f.y() * scale(), f.w() * scale(), f.h() * scale(), f.id(),
                    fieldHint(f), this.f_96547_.m_92895_(fieldHint(f)), f.w() - 8));
        }
        for (Wheel wl : wheels) {
            out.add(String.format(Locale.ROOT, "W %.1f %.1f %.1f %.1f %s target=%s",
                    place.x() + wl.left() * scale(), originY() + wl.top() * scale(), wl.side() * scale(),
                    wl.side() * scale(), wl.id(), wl.target()));
        }
        for (Book b : books) {
            out.add(String.format(Locale.ROOT, "K %.1f %.1f %.1f %.1f %s page=%d", place.x() + b.x() * scale(),
                    originY() + b.y() * scale(), b.w() * scale(), b.h() * scale(), b.id(),
                    bookPage.getOrDefault(b.id(), 0)));
        }
        if (model != null) {
            out.add(String.format(Locale.ROOT, "M %.1f %.1f %.1f %.1f rotate=%s yaw=%.1f",
                    place.x() + num(model, "x", 0) * scale(), originY() + num(model, "y", 0) * scale(),
                    num(model, "width", 64) * scale(), num(model, "height", 96) * scale(),
                    bool(model, "rotate", false), NativeModelPreview.yaw()));
        }
        int step = lineH + lineGap;
        NativeText.Ink ink = NativeText.Ink.on(skin, bodyBackground());
        for (int i = 0; i < lines.size(); i++) {
            out.add(String.format(Locale.ROOT, "L %.1f %.1f %s", place.x() + EDGE * scale(),
                    originY() + (bodyTop + bodyPad + i * step) * scale(), NativeText.plain(lines.get(i))));
            out.add(String.format(Locale.ROOT, "I %d ink=%06X bg=%06X", i,
                    NativeText.firstInk(lines.get(i), skin.text(), false, ink), bodyBackground()));
        }
        return out;
    }

    private static JsonArray arr(String json) {
        try {
            JsonElement el = json == null || json.isBlank() ? null : JsonParser.parseString(json);
            return el != null && el.isJsonArray() ? el.getAsJsonArray() : new JsonArray();
        } catch (RuntimeException e) {
            return new JsonArray();
        }
    }

    private static JsonObject obj(String json) {
        try {
            JsonElement el = json == null || json.isBlank() ? null : JsonParser.parseString(json);
            return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String str(JsonObject o, String key, String fallback) {
        try {
            return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int num(JsonObject o, String key, int fallback) {
        try {
            return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static double dbl(JsonObject o, String key, double fallback) {
        try {
            return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsDouble() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        try {
            return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
