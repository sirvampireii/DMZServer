package com.dbzlegacy.adaptivedifficulty.client;

import com.dbzlegacy.adaptivedifficulty.gui.ultra.SaiyanRoyal;
import com.dbzlegacy.adaptivedifficulty.net.gui.GuiOpenPacket;
import com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork;
import com.dbzlegacy.adaptivedifficulty.net.gui.NativeMenuFit;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
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
 * <p>4.7.60: every menu wears the Saiyan Royal skin ({@link SaiyanRoyal}): the exact hex palette, a rounded
 * panel (14px) with a 1px gold-trim border, rounded buttons, tier rows and bars. Each system's accent
 * (Difficulty crimson, Rival white, Sparring dark aqua, ...) stays on button edges, the active tab and frames.
 * Depth: the panel lifts off the background on two shadow layers and is lighter at the top; raised buttons
 * carry their own shadow, a top-edge highlight and a darker bottom edge, and sink 2px while pressed; info
 * boxes, fields, books and bar tracks are inset with an inner shadow; bars fill with a glossy gradient; the
 * active tab is raised with a gold glow. Only the drawn shapes move or round; every click area stays the full
 * rectangle from the server layout.
 */
public final class NativeMenuScreen extends Screen {
    /** Action id of a locked button: drawn dim, never sends anything. */
    static final String NO_ACTION = "nav:none";
    private static final int CLOSE_BAND = 28;
    private static final int EDGE = 10;

    /** Button and row faces on the Saiyan Royal panel. */
    private static final int FACE = 0x2E1316;
    private static final int FACE_HOVER = 0x4A1C20;

    private enum Kind { SERVER, CLOSE, BOOK_PREV, BOOK_NEXT }

    private record Btn(Kind kind, String id, String label, String style, int x, int y, int w, int h, int pad,
            JsonObject args, String sound, String icon, String accent, String palette, String book) {}

    private record Book(String id, int x, int y, int w, int h, JsonArray pages) {}

    private record Field(String id, int x, int y, int w, int h, String value, String placeholder, int maxLength,
            JsonObject args) {}

    private final GuiOpenPacket packet;
    private final List<String> lines = new ArrayList<>();
    private final List<Btn> serverButtons = new ArrayList<>();
    private final List<Btn> buttons = new ArrayList<>();
    private final List<Book> books = new ArrayList<>();
    private final List<Field> fields = new ArrayList<>();
    private final Map<String, EditBox> boxes = new LinkedHashMap<>();
    private final Map<String, Integer> bookPage = new LinkedHashMap<>();
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
    /** Gold-trim headers on every menu. */
    private final int titleRgb = SaiyanRoyal.GOLD_TRIM;
    /** This system's accent (exact hex), or the gold trim on menus without one (the main menu). */
    private int accentRgb = SaiyanRoyal.GOLD_TRIM;

    private NativeMenuFit.Placement place = new NativeMenuFit.Placement(0, 0, 1, 0);
    private float scroll;
    private float bodyScroll;

    public NativeMenuScreen(GuiOpenPacket packet, NativeMenuScreen previous) {
        super(Component.m_237113_(NativeText.plain(packet.title())));
        this.packet = packet;
        parse();
        if (previous != null && previous.packet.screenId().equals(packet.screenId())
                && previous.packet.pageId().equals(packet.pageId())) {
            // Same page again (a toggle or a refresh): keep where the player was.
            this.scroll = previous.scroll;
            this.bodyScroll = previous.bodyScroll;
            this.bookPage.putAll(previous.bookPage);
        }
        if (model != null) {
            NativeModelPreview.onPacket(bool(model, "rotate", false));
        }
    }

    GuiOpenPacket packet() {
        return packet;
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
        }
        if (fillScreen) {
            if (designW > 0) {
                panelW = designW;
            }
            if (designH > 0) {
                panelH = designH;
            }
        }
        int sys = SaiyanRoyal.accentHex(accent);
        accentRgb = sys >= 0 ? sys : paletteRgb(palette, SaiyanRoyal.GOLD_TRIM);
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
            serverButtons.add(new Btn(Kind.SERVER, id, str(b, "label", id), str(b, "style", "normal"),
                    num(b, "x", EDGE), num(b, "y", 0), Math.max(1, num(b, "w", 80)), Math.max(8, num(b, "h", 16)),
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
                        Math.max(1, Math.min(256, num(in, "maxLength", 64))), args));
            }
            // Other input types never reach this client: the server routes such menus to KaiPath.
        }
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void m_7856_() {
        for (Map.Entry<String, EditBox> e : boxes.entrySet()) {
            typed.put(e.getKey(), e.getValue().m_94155_());
        }
        boxes.clear();
        place = NativeMenuFit.place(this.f_96543_, this.f_96544_, panelW, panelH, margin, fillScreen, designW, designH);
        scroll = Math.max(0f, Math.min(scroll, place.maxScroll()));
        for (Field f : fields) {
            // The field draws its own rounded frame; the text box sits inside it.
            EditBox box = new EditBox(this.f_96547_, f.x() + 4, f.y() + (f.h() - 8) / 2, f.w() - 8, 8,
                    Component.m_237113_(f.placeholder()));
            box.m_94182_(false);
            box.m_94199_(f.maxLength());
            box.m_94144_(typed.getOrDefault(f.id(), f.value()));
            if (!f.placeholder().isBlank()) {
                box.m_257771_(Component.m_237113_(f.placeholder()));
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
    }

    /**
     * Close goes in the bottom band, right side, like KaiPath's. A page whose own controls already
     * use that spot (the config editor's pinned footer) gets it bottom left, else top right.
     */
    private int[] closeSpot() {
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

    private boolean free(int x, int y, int w, int h) {
        List<int[]> taken = new ArrayList<>();
        for (Btn b : buttons) {
            taken.add(new int[] {b.x(), b.y(), b.w(), b.h()});
        }
        for (Field f : fields) {
            taken.add(new int[] {f.x(), f.y(), f.w(), f.h()});
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
        g.m_280509_(0, 0, this.f_96543_, this.f_96544_, 0x90000000);
        int mx = (int) Math.floor(toDesignX(mouseX));
        int my = (int) Math.floor(toDesignY(mouseY));
        PoseStack pose = g.m_280168_();
        pose.m_85836_();
        pose.m_252880_(place.x(), originY(), 0f);
        pose.m_85841_(scale(), scale(), 1f);
        drawPanel(g);
        drawTitle(g);
        drawBody(g);
        drawModel(g);
        for (Book b : books) {
            drawBook(g, b);
        }
        for (Field f : fields) {
            EditBox box = boxes.get(f.id());
            if (box != null) {
                insetBox(g, f.x(), f.y(), f.w(), f.h(), 4, box.m_93696_() ? 0xFF000000 | SaiyanRoyal.GOLD_TRIM
                        : 0xFF5A3A2C, 0xFF0C0607);
                box.m_88315_(g, mx, my, partialTick);
            }
        }
        boolean down = Minecraft.m_91087_().f_91067_.m_91560_();
        for (Btn b : buttons) {
            boolean hover = contains(b, mx, my);
            drawButton(g, b, hover, hover && down);
        }
        pose.m_85849_();
        if (place.scrolls()) {
            drawScrollBar(g);
        }
    }

    /**
     * Saiyan Royal panel: lifted on a wide ambient shadow and a tight key shadow, 1px gold-trim border, the
     * #160b0d fill as a vertical gradient (lighter at the top), rounded 14px; gold divider. A framed page adds
     * a faint inner ring in its system accent.
     */
    private void drawPanel(GuiGraphics g) {
        int r = SaiyanRoyal.PANEL_RADIUS;
        shadow(g, 0, 0, panelW, panelH, r, SaiyanRoyal.AMBIENT_RINGS, SaiyanRoyal.AMBIENT_DROP, SaiyanRoyal.AMBIENT_ALPHA);
        shadow(g, 0, 0, panelW, panelH, r, SaiyanRoyal.KEY_RINGS, SaiyanRoyal.KEY_DROP, SaiyanRoyal.KEY_ALPHA);
        fillRounded(g, 0, 0, panelW, panelH, r, 0xFF000000 | SaiyanRoyal.GOLD_TRIM);
        paint(g, 1, 1, panelW - 2, panelH - 2, r - 1, 0xFF000000 | SaiyanRoyal.panelTop(),
                0xFF000000 | SaiyanRoyal.panelBottom(), panelH - 2);
        if (frame) {
            outlineRounded(g, 2, 2, panelW - 4, panelH - 4, r - 2,
                    0xFF000000 | SaiyanRoyal.blend(accentRgb, SaiyanRoyal.PANEL, 0.6));
        }
        int dividerY = Math.max(10, headerBottom - 3);
        fillRounded(g, EDGE, dividerY, panelW - 2 * EDGE, 1, 0, 0xC0000000 | SaiyanRoyal.GOLD_TRIM);
    }

    private void drawTitle(GuiGraphics g) {
        MutableComponent title = NativeText.parse(packet.title(), titleRgb, false, true, true);
        int maxW = panelW - 2 * EDGE;
        FormattedCharSequence seq = NativeText.fit(this.f_96547_, title, maxW);
        int tw = this.f_96547_.m_92724_(seq);
        int band = Math.max(9, headerBottom - 3);
        int ty = Math.max(3, (band - 9) / 2);
        int tx = "left".equals(titleAlign) ? EDGE : (panelW - tw) / 2;
        g.m_280649_(this.f_96547_, seq, tx, ty, 0xFFFFFF, true);
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

    private void drawBody(GuiGraphics g) {
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
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            if (raw == null || NativeText.plain(raw).isBlank()) {
                continue;
            }
            int y = Math.round(bodyTop + bodyPad + i * step + (lineH - 9) / 2f + 1 - offset);
            if (drawSkinLine(g, raw, y, maxW)) {
                continue;
            }
            FormattedCharSequence seq = NativeText.fit(this.f_96547_,
                    NativeText.parse(raw, SaiyanRoyal.TEXT, false, false, true), maxW);
            g.m_280649_(this.f_96547_, seq, EDGE, y, 0xFFFFFF, false);
        }
        if (clip) {
            g.m_280618_();
        }
    }

    /**
     * The body text sits in an inset info box with an inner shadow, when the box would not run under a
     * control (a page that mixes rows into its text keeps plain text).
     */
    private void drawInfoBox(GuiGraphics g) {
        int h = bodyClipped() ? bodyMaxH : bodyNaturalH();
        int x = EDGE - 4;
        int w = bodyRight() - EDGE + 8;
        int y = bodyTop + 1;
        h -= 2;
        if (h < 12 || !free(x, y, w, h)) {
            return;
        }
        insetBox(g, x, y, w, h, SaiyanRoyal.BOX_RADIUS, 0xFF000000 | SaiyanRoyal.blend(SaiyanRoyal.PANEL, 0x000000, 0.5),
                0xFF000000 | SaiyanRoyal.blend(SaiyanRoyal.PANEL, 0x000000, 0.35));
    }

    /**
     * Skinned body lines that end in a text bar ({@code [####----]}) or progress dots ({@code [x] [ ] [ ]})
     * draw the text before it, then a rounded bar or round dots. Returns false for an ordinary line.
     */
    private boolean drawSkinLine(GuiGraphics g, String raw, int y, int maxW) {
        String plain = NativeText.plain(raw);
        int bar = SaiyanRoyal.barStart(plain);
        int dots = bar < 0 ? SaiyanRoyal.dotsStart(plain) : -1;
        if (bar < 0 && dots < 0) {
            return false;
        }
        int x = EDGE;
        String lead = rawPrefix(raw, bar >= 0 ? bar : dots);
        if (!NativeText.plain(lead).isBlank()) {
            FormattedCharSequence seq = NativeText.fit(this.f_96547_,
                    NativeText.parse(lead, SaiyanRoyal.TEXT, false, false, true), maxW / 2);
            g.m_280649_(this.f_96547_, seq, x, y, 0xFFFFFF, false);
            x += this.f_96547_.m_92724_(seq) + 4;
        }
        int midY = y + 4;
        if (bar >= 0) {
            double share = SaiyanRoyal.barFill(plain);
            int w = Math.max(24, Math.min(EDGE + maxW - x, 144));
            int h = 8;
            int ty = midY - h / 2;
            // Recessed track, then a glossy gold fill with a highlight along its upper third.
            insetBox(g, x, ty, w, h, SaiyanRoyal.BAR_RADIUS + 1, 0xFF0A0405, 0xFF1A0C0E);
            int fw = share <= 0 ? 0 : Math.max(h - 2, (int) Math.round((w - 2) * share));
            if (fw > 0) {
                glossy(g, x + 1, ty + 1, fw, h - 2, SaiyanRoyal.BAR_RADIUS, SaiyanRoyal.GOLD_TRIM);
            }
        } else {
            for (boolean done : SaiyanRoyal.dots(plain)) {
                if (done) {
                    glossy(g, x, midY - 4, 8, 8, 4, SaiyanRoyal.GOLD_TRIM);
                } else {
                    insetBox(g, x, midY - 4, 8, 8, 4, 0xFF000000 | SaiyanRoyal.blend(SaiyanRoyal.GOLD_TRIM,
                            SaiyanRoyal.PANEL, 0.4), 0xFF0E0607);
                }
                x += 14;
            }
        }
        return true;
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
            insetBox(g, x - 2, y - 2, w + 4, h + 4, 6, 0xFF000000 | accentRgb,
                    0xFF000000 | SaiyanRoyal.blend(SaiyanRoyal.PANEL, 0x000000, 0.3));
        }
        LivingEntity entity = NativeModelPreview.resolve(model);
        if (entity == null) {
            fillRounded(g, x, y, w, h, 6, 0x33000000);
            return;
        }
        float scale = model.has("scale") ? (float) dbl(model, "scale", 1.0) : 1f;
        NativeModelPreview.render(g, x, y, w, h, scale, bool(model, "rotate", false), entity);
    }

    private void drawBook(GuiGraphics g, Book b) {
        insetBox(g, b.x(), b.y(), b.w(), b.h(), SaiyanRoyal.BUTTON_RADIUS, 0xFF5A3A2C, 0xFF1F1012);
        int pages = Math.max(1, b.pages().size());
        int page = Math.min(pages - 1, bookPage.getOrDefault(b.id(), 0));
        JsonObject p = b.pages().size() > page && b.pages().get(page).isJsonObject()
                ? b.pages().get(page).getAsJsonObject() : new JsonObject();
        int tx = b.x() + 6;
        int maxW = b.w() - 12;
        String title = str(p, "title", "Page " + (page + 1)) + "  (" + (page + 1) + "/" + pages + ")";
        MutableComponent t = NativeText.parse(title, SaiyanRoyal.GOLD_TRIM, false, true, true);
        g.m_280649_(this.f_96547_, NativeText.fit(this.f_96547_, t, maxW), tx, b.y() + 5, 0xFFFFFF, false);
        int y = b.y() + 18;
        int bottom = b.y() + b.h() - 22;
        if (p.has("lines") && p.get("lines").isJsonArray()) {
            for (JsonElement el : p.getAsJsonArray("lines")) {
                if (!el.isJsonPrimitive()) {
                    continue;
                }
                for (FormattedCharSequence part : this.f_96547_.m_92923_(
                        NativeText.parse(el.getAsString(), SaiyanRoyal.TEXT, false, false, true),
                        maxW)) {
                    if (y + 9 > bottom) {
                        break;
                    }
                    g.m_280649_(this.f_96547_, part, tx, y, 0xFFFFFF, false);
                    y += lineH + lineGap;
                }
            }
        }
    }

    private void drawButton(GuiGraphics g, Btn b, boolean hover, boolean down) {
        boolean inert = b.kind() == Kind.SERVER && isNoAction(b.id());
        String style = inert ? "dim" : b.style().toLowerCase(Locale.ROOT);
        drawSkinButton(g, b, hover, down && !inert, inert, style, isActiveTab(b));
    }

    /**
     * Saiyan Royal button or tier row: rounded 8px face with a 1px edge. Normal rows sit on a deep red face
     * with an edge in the system accent (a hub tile in its own system's accent); confirm stays green, danger
     * red, dim and locked dark gray (copy rules). Raised rows have their own shadow, a face lighter at the top,
     * a top-edge highlight and a darker bottom edge; pressed they sink 2px, lose the shadow and invert the
     * gradient. Dim and locked rows stay flat (nothing to press). The active tab is raised with a gold glow,
     * the full accent edge and an underline. The click area is still the full rectangle ({@link #contains}).
     */
    private void drawSkinButton(GuiGraphics g, Btn b, boolean hover, boolean pressed, boolean inert, String style,
            boolean activeTab) {
        int face;
        int edge;
        switch (style) {
            case "confirm" -> {
                face = hover ? 0xFF2A7A3A : 0xFF1F5E2C;
                edge = 0xFF3FAF55;
            }
            case "danger" -> {
                face = hover ? 0xFF8A2A2A : 0xFF6A1F1F;
                edge = 0xFF000000 | SaiyanRoyal.CRIMSON;
            }
            case "dim" -> {
                face = hover && !inert ? 0xFF2C2425 : 0xFF221B1C;
                edge = 0xFF3A3233;
            }
            default -> {
                face = 0xFF000000 | (hover ? FACE_HOVER : FACE);
                int tint = buttonTint(b);
                edge = 0xFF000000 | (hover ? tint : SaiyanRoyal.blend(tint, FACE, 0.4));
            }
        }
        int accentNow = buttonTint(b);
        if (activeTab) {
            edge = 0xFF000000 | accentNow;
        }
        boolean flat = "dim".equals(style);
        int r = SaiyanRoyal.BUTTON_RADIUS;
        int bx = b.x();
        int by = b.y() + (flat ? 0 : SaiyanRoyal.buttonOffset(pressed, activeTab));
        int w = b.w();
        int h = b.h();
        if (activeTab) {
            glow(g, bx, by, w, h, r, SaiyanRoyal.GOLD_TRIM, SaiyanRoyal.GLOW_RINGS, SaiyanRoyal.GLOW_ALPHA);
        }
        if (!flat && !pressed) {
            shadow(g, bx, by, w, h, r, SaiyanRoyal.BUTTON_SHADOW_RINGS, SaiyanRoyal.BUTTON_SHADOW_DROP,
                    SaiyanRoyal.BUTTON_SHADOW_ALPHA);
        }
        fillRounded(g, bx, by, w, h, r, edge);
        int rgb = face & 0xFFFFFF;
        int top = 0xFF000000 | SaiyanRoyal.blend(rgb, 0xFFFFFF, flat ? 0 : SaiyanRoyal.FACE_TOP_LIFT);
        int bottom = 0xFF000000 | SaiyanRoyal.blend(rgb, 0x000000, flat ? 0 : SaiyanRoyal.FACE_BOTTOM_DROP);
        paint(g, bx + 1, by + 1, w - 2, h - 2, r - 1, pressed ? bottom : top, pressed ? top : bottom, h - 2);
        if (!flat && !pressed) {
            fillRounded(g, bx + r / 2, by + 1, w - r, 1, 0, SaiyanRoyal.EDGE_HIGHLIGHT);
            fillRounded(g, bx + r / 2, by + h - 2, w - r, 1, 0, SaiyanRoyal.EDGE_SHADE);
        }
        if (activeTab) {
            fillRounded(g, bx + r, by + h - 3, w - 2 * r, 2, 1, 0xFF000000 | accentNow);
        }
        int left = b.x() + b.pad();
        int maxW = b.w() - 2 * b.pad();
        if (drawIcon(g, b)) {
            left += 12;
            maxW -= 12;
        }
        boolean muted = "dim".equals(style);
        FormattedCharSequence seq = NativeText.fit(this.f_96547_,
                NativeText.parse(labelInk(b), SaiyanRoyal.TEXT, muted, false, true), Math.max(8, maxW));
        int tw = this.f_96547_.m_92724_(seq);
        int tx = left + Math.max(0, (maxW - tw) / 2);
        int ty = by + (h - 8) / 2;
        g.m_280649_(this.f_96547_, seq, tx, ty, 0xFFFFFF, !muted);
    }

    /**
     * Label text to draw. The spec inks a label in its system accent; the skin already puts that accent on
     * the button edge, so a label whose leading ink is the button's own accent draws in the skin text color
     * (readable on green confirm and deep red faces). Any other ink, and dim labels, stay as sent.
     */
    private String labelInk(Btn b) {
        String label = b.label() == null ? "" : b.label();
        if (label.length() >= 2 && label.charAt(0) == NativeText.MARK
                && SaiyanRoyal.accentHex(label.charAt(1)) == buttonTint(b)) {
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
        insetBox(g, x - 1, trackTop, 5, trackH, 2, 0x60000000, 0x40FFFFFF);
        glossy(g, x, barY, 3, barH, 1, SaiyanRoyal.GOLD_TRIM);
    }

    /** Sub-pixel rows per design pixel for rounded corners, so curves stay smooth at GUI scale 3 and 4. */
    private static final int ROUND_Q = 4;

    /** Fill a rounded rectangle (radius in design pixels, capped at half the shorter side) in one color. */
    static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int radius, int argb) {
        paint(g, x, y, w, h, radius, argb, argb, h);
    }

    /**
     * Paint the top {@code rows} design rows of a rounded rectangle with a vertical gradient from {@code top}
     * to {@code bottom} (ARGB). Corners are cut row by row at a quarter-pixel step; the middle goes one design
     * row at a time (or one fill for a flat color). One batched draw.
     */
    static void paint(GuiGraphics g, int x, int y, int w, int h, int radius, int top, int bottom, int rows) {
        if (w <= 0 || h <= 0 || rows <= 0) {
            return;
        }
        int r = SaiyanRoyal.radiusFor(w, h, radius);
        int limit = Math.min(rows, h);
        g.m_286007_(() -> {
            PoseStack pose = g.m_280168_();
            pose.m_85836_();
            pose.m_85841_(1f / ROUND_Q, 1f / ROUND_Q, 1f);
            int X = x * ROUND_Q;
            int Y = y * ROUND_Q;
            int W = w * ROUND_Q;
            int H = h * ROUND_Q;
            int R = r * ROUND_Q;
            int L = limit * ROUND_Q;
            boolean flat = top == bottom;
            int q = 0;
            while (q < L) {
                boolean corner = q < R || q >= H - R;
                if (corner) {
                    int edge = q < R ? q : H - q - 1;
                    int in = (int) Math.round(SaiyanRoyal.cornerInset(R, edge + 0.5));
                    int c = flat ? top : SaiyanRoyal.lerpArgb(top, bottom, (q + 0.5) / L);
                    g.m_280509_(X + in, Y + q, X + W - in, Y + q + 1, c);
                    q++;
                } else {
                    int stop = Math.min(L, H - R);
                    int next = flat ? stop : Math.min(stop, q + ROUND_Q);
                    int c = flat ? top : SaiyanRoyal.lerpArgb(top, bottom, ((q + next) / 2.0) / L);
                    g.m_280509_(X, Y + q, X + W, Y + next, c);
                    q = next;
                }
            }
            pose.m_85849_();
        });
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
            fillRounded(g, x - i, y - i, w + 2 * i, h + 2 * i, radius + i, (a << 24) | (rgb & 0xFFFFFF));
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

    /** Inset box: a 1px edge, the fill, and an inner shadow fading down from the top edge. */
    static void insetBox(GuiGraphics g, int x, int y, int w, int h, int radius, int edge, int fill) {
        ring(g, x, y, w, h, radius, edge, fill);
        int rows = Math.min(SaiyanRoyal.INSET_SHADOW_PX, Math.max(1, (h - 2) / 2));
        paint(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), SaiyanRoyal.INSET_SHADOW_ALPHA << 24, 0, rows);
    }

    /** Glossy fill: a gradient from a light top to a darker bottom, with a highlight along the upper third. */
    static void glossy(GuiGraphics g, int x, int y, int w, int h, int radius, int rgb) {
        paint(g, x, y, w, h, radius, 0xFF000000 | SaiyanRoyal.blend(rgb, 0xFFFFFF, 0.35),
                0xFF000000 | SaiyanRoyal.blend(rgb, 0x000000, 0.25), h);
        int r = SaiyanRoyal.radiusFor(w, h, radius);
        if (h >= 4 && w > 2 * r + 2) {
            fillRounded(g, x + r, y + Math.max(1, h / 3 - 1), w - 2 * r, 1, 0, 0x60FFFFFF);
        }
    }

    // ------------------------------------------------------------------ input

    private static boolean contains(Btn b, double x, double y) {
        return x >= b.x() && x < b.x() + b.w() && y >= b.y() && y < b.y() + b.h();
    }

    @Override
    public boolean m_6375_(double mouseX, double mouseY, int button) {
        double x = toDesignX(mouseX);
        double y = toDesignY(mouseY);
        if (button == 0) {
            boolean anyBox = false;
            for (Field f : fields) {
                EditBox box = boxes.get(f.id());
                if (box == null) {
                    continue;
                }
                boolean hit = x >= f.x() && x < f.x() + f.w() && y >= f.y() && y < f.y() + f.h();
                box.m_93692_(hit);
                if (hit) {
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
                LmNativeNetwork.sendAction(packet.screenId(), b.id(), args.toString());
            }
        }
    }

    @Override
    public boolean m_7933_(int keyCode, int scanCode, int modifiers) {
        for (Field f : fields) {
            EditBox box = boxes.get(f.id());
            if (box == null || !box.m_93696_()) {
                continue;
            }
            if (keyCode == 257 || keyCode == 335) {
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
        return paletteRgb(b.palette(), accentRgb);
    }

    static int accentRgb(String code, int fallback) {
        if (code == null || code.isBlank()) {
            return fallback;
        }
        char c = code.trim().charAt(code.trim().length() == 2 && code.trim().charAt(0) == NativeText.MARK ? 1 : 0);
        return NativeText.rgb(c, fallback);
    }

    static int paletteRgb(String name, int fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "red" -> SaiyanRoyal.CRIMSON;
            case "dark_red" -> 0xAA0000;
            case "purple", "dark_purple" -> 0xC058C0;
            case "light_purple", "pink" -> 0xFF55FF;
            case "gold", "orange" -> SaiyanRoyal.GOLD_TRIM;
            case "yellow" -> 0xFFFF55;
            case "green" -> 0x55FF55;
            case "dark_green" -> 0x00AA00;
            case "aqua", "cyan" -> 0x55FFFF;
            case "dark_aqua", "teal" -> 0x00AAAA;
            case "blue" -> 0x5555FF;
            case "white" -> 0xFFFFFF;
            case "gray", "grey" -> 0xAAAAAA;
            default -> fallback;
        };
    }

    /**
     * Screen-space layout for test probes: one line per drawn control
     * ({@code kind x y w h style "label"}), after the fit transform and scroll.
     */
    public List<String> debugLayout() {
        List<String> out = new ArrayList<>();
        out.add(String.format(Locale.ROOT, "PANEL %.1f %.1f %.1f %.1f scale=%.3f scroll=%.1f maxScroll=%.1f",
                place.x(), originY(), panelW * scale(), panelH * scale(), scale(), scroll, place.maxScroll()));
        out.add(String.format(Locale.ROOT, "SKIN %s accent=%06X", SaiyanRoyal.NAME, accentRgb));
        for (Btn b : buttons) {
            out.add(String.format(Locale.ROOT, "B %.1f %.1f %.1f %.1f %s %s \"%s\"", place.x() + b.x() * scale(),
                    originY() + b.y() * scale(), b.w() * scale(), b.h() * scale(),
                    b.kind() == Kind.SERVER && isNoAction(b.id()) ? "locked" : b.style(),
                    b.kind() == Kind.SERVER ? b.id() : b.kind().name(), NativeText.plain(b.label())));
        }
        for (Field f : fields) {
            out.add(String.format(Locale.ROOT, "T %.1f %.1f %.1f %.1f %s", place.x() + f.x() * scale(),
                    originY() + f.y() * scale(), f.w() * scale(), f.h() * scale(), f.id()));
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
        for (int i = 0; i < lines.size(); i++) {
            out.add(String.format(Locale.ROOT, "L %.1f %.1f %s", place.x() + EDGE * scale(),
                    originY() + (bodyTop + bodyPad + i * step) * scale(), NativeText.plain(lines.get(i))));
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
