package com.dbzlegacy.adaptivedifficulty.client;

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
 */
public final class NativeMenuScreen extends Screen {
    /** Action id of a locked button: drawn dim, never sends anything. */
    static final String NO_ACTION = "nav:none";
    private static final int CLOSE_BAND = 28;
    private static final int EDGE = 10;

    private static final int PANEL_BG = 0xF0141418;
    private static final int PANEL_EDGE = 0xFF3C3C46;
    private static final int DIVIDER = 0xFFE8C840;
    private static final int GOLD = 0xFFAA00;

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
    private int titleRgb = GOLD;

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
        titleRgb = paletteRgb(palette, accentRgb(accent, GOLD));
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
            EditBox box = new EditBox(this.f_96547_, f.x(), f.y(), f.w(), f.h(), Component.m_237113_(f.placeholder()));
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
        buttons.add(new Btn(Kind.CLOSE, "close", "Close", "normal", panelW - EDGE - 80, panelH - CLOSE_BAND + 6, 80, 16,
                8, new JsonObject(), "", "", "", "", ""));
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
                box.m_88315_(g, mx, my, partialTick);
            }
        }
        for (Btn b : buttons) {
            drawButton(g, b, contains(b, mx, my));
        }
        pose.m_85849_();
        if (place.scrolls()) {
            drawScrollBar(g);
        }
    }

    private void drawPanel(GuiGraphics g) {
        g.m_280509_(0, 0, panelW, panelH, PANEL_BG);
        outline(g, 0, 0, panelW, panelH, PANEL_EDGE);
        if (frame) {
            outline(g, 1, 1, panelW - 2, panelH - 2, 0x66000000 | titleRgb);
        }
        int dividerY = Math.max(10, headerBottom - 3);
        g.m_280509_(EDGE, dividerY, panelW - EDGE, dividerY + 1, DIVIDER);
    }

    private void drawTitle(GuiGraphics g) {
        MutableComponent title = NativeText.parse(packet.title(), titleRgb, false, true);
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
            FormattedCharSequence seq = NativeText.fit(this.f_96547_, NativeText.parse(raw, 0xE0E0E0, false), maxW);
            g.m_280649_(this.f_96547_, seq, EDGE, y, 0xFFFFFF, false);
        }
        if (clip) {
            g.m_280618_();
        }
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
            outline(g, x - 2, y - 2, w + 4, h + 4, 0xFF000000 | titleRgb);
        }
        LivingEntity entity = NativeModelPreview.resolve(model);
        if (entity == null) {
            g.m_280509_(x, y, x + w, y + h, 0x33000000);
            return;
        }
        float scale = model.has("scale") ? (float) dbl(model, "scale", 1.0) : 1f;
        NativeModelPreview.render(g, x, y, w, h, scale, bool(model, "rotate", false), entity);
    }

    private void drawBook(GuiGraphics g, Book b) {
        g.m_280509_(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), 0xFF1C1C22);
        outline(g, b.x(), b.y(), b.w(), b.h(), PANEL_EDGE);
        int pages = Math.max(1, b.pages().size());
        int page = Math.min(pages - 1, bookPage.getOrDefault(b.id(), 0));
        JsonObject p = b.pages().size() > page && b.pages().get(page).isJsonObject()
                ? b.pages().get(page).getAsJsonObject() : new JsonObject();
        int tx = b.x() + 6;
        int maxW = b.w() - 12;
        String title = str(p, "title", "Page " + (page + 1)) + "  (" + (page + 1) + "/" + pages + ")";
        MutableComponent t = NativeText.parse(title, GOLD, false, true);
        g.m_280649_(this.f_96547_, NativeText.fit(this.f_96547_, t, maxW), tx, b.y() + 5, 0xFFFFFF, false);
        int y = b.y() + 18;
        int bottom = b.y() + b.h() - 22;
        if (p.has("lines") && p.get("lines").isJsonArray()) {
            for (JsonElement el : p.getAsJsonArray("lines")) {
                if (!el.isJsonPrimitive()) {
                    continue;
                }
                for (FormattedCharSequence part : this.f_96547_.m_92923_(
                        NativeText.parse(el.getAsString(), 0xE0E0E0, false), maxW)) {
                    if (y + 9 > bottom) {
                        break;
                    }
                    g.m_280649_(this.f_96547_, part, tx, y, 0xFFFFFF, false);
                    y += lineH + lineGap;
                }
            }
        }
    }

    private void drawButton(GuiGraphics g, Btn b, boolean hover) {
        boolean inert = b.kind() == Kind.SERVER && isNoAction(b.id());
        String style = inert ? "dim" : b.style().toLowerCase(Locale.ROOT);
        boolean activeTab = isActiveTab(b);
        int face;
        int edge;
        switch (style) {
            case "confirm" -> {
                face = hover ? 0xFF2A7A3A : 0xFF1F5E2C;
                edge = 0xFF3FAF55;
            }
            case "danger" -> {
                face = hover ? 0xFF8A2A2A : 0xFF6A1F1F;
                edge = 0xFFD04040;
            }
            case "dim" -> {
                face = hover && !inert ? 0xFF2E2E34 : 0xFF232327;
                edge = 0xFF34343A;
            }
            default -> {
                face = hover ? 0xFF4C4C58 : 0xFF3A3A44;
                edge = 0xFF5C5C68;
            }
        }
        int tint = buttonTint(b);
        if (activeTab) {
            edge = 0xFF000000 | titleRgb;
        } else if (tint != 0) {
            edge = 0xFF000000 | tint;
        }
        g.m_280509_(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), face);
        outline(g, b.x(), b.y(), b.w(), b.h(), edge);
        if (activeTab) {
            g.m_280509_(b.x() + 1, b.y() + b.h() - 2, b.x() + b.w() - 1, b.y() + b.h() - 1, 0xFF000000 | titleRgb);
        }
        int left = b.x() + b.pad();
        int maxW = b.w() - 2 * b.pad();
        if (drawIcon(g, b)) {
            left += 12;
            maxW -= 12;
        }
        boolean muted = "dim".equals(style);
        FormattedCharSequence seq = NativeText.fit(this.f_96547_, NativeText.parse(b.label(), 0xFFFFFF, muted),
                Math.max(8, maxW));
        int tw = this.f_96547_.m_92724_(seq);
        int tx = left + Math.max(0, (maxW - tw) / 2);
        int ty = b.y() + (b.h() - 8) / 2;
        g.m_280649_(this.f_96547_, seq, tx, ty, 0xFFFFFF, !muted);
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
        g.m_280509_(x, trackTop, x + 3, trackTop + trackH, 0x40FFFFFF);
        g.m_280509_(x, barY, x + 3, barY + barH, 0xC0E8C840);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.m_280509_(x, y, x + w, y + 1, argb);
        g.m_280509_(x, y + h - 1, x + w, y + h, argb);
        g.m_280509_(x, y, x + 1, y + h, argb);
        g.m_280509_(x + w - 1, y, x + w, y + h, argb);
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
            for (EditBox box : boxes.values()) {
                boolean hit = box.m_5953_(x, y);
                box.m_93692_(hit);
                if (hit) {
                    box.m_6375_(x, y, button);
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

    private int buttonTint(Btn b) {
        int t = paletteRgb(b.palette(), accentRgb(b.accent(), 0));
        return t;
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
            case "red" -> 0xFF5555;
            case "dark_red" -> 0xD84848;
            case "purple", "dark_purple" -> 0xC058C0;
            case "light_purple", "pink" -> 0xFF55FF;
            case "gold", "orange" -> 0xFFAA00;
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
