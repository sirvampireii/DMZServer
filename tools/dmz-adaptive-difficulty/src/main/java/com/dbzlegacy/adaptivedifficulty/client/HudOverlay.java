package com.dbzlegacy.adaptivedifficulty.client;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins;
import com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork;
import com.dbzlegacy.adaptivedifficulty.net.gui.NativeHudPacket;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * LM 4.7.72 native HUD (approved proposal {@code docs/LM_NATIVE_HUD_PROPOSAL.md}): draws what the server sends on
 * message 4 and nothing else. Five elements: the Underdog pill and the TP notice and death TP penalty pills in a
 * stack at the top centre, Rival Instinct cards on the right edge (3 at most, 4 s each), and the Momentum meter
 * above the hotbar.
 *
 * <p>Lag rules (4.7.61): each element's chrome (rounded panel, accent edge and strip, soft shadow in Full; flat with
 * no shadow in Lite) is baked once per size, GUI scale, skin and accent into a cached texture. Text is laid out
 * when a message arrives (or the skin changes), never per frame. A frame only blits, draws the laid-out text and
 * fills the bar: no allocation.
 *
 * <p>Clicks: while the chat screen is open the mouse is free; an element with a page under the cursor turns the
 * cursor into a hand and a click sends its action like a menu button. An LM key, unbound by default, opens the
 * page of the newest clickable element.
 */
final class HudOverlay {
    private HudOverlay() {}

    /** Gap from the screen edge and between stacked elements (GUI px). */
    static final int EDGE = 4;
    static final int GAP = 3;
    static final int LINE_H = 10;
    static final int PAD_X = 6;
    /** Shadow room baked around each panel in Full (GUI px) and its drop. */
    static final int SHADOW = 3;
    static final int DROP = 1;
    static final int CARD_W = 160;
    static final int CARD_MAX = 3;
    static final int CARD_FADE_MS = 500;
    static final int SYMBOL_W = 12;
    static final int MOMENTUM_W = 90;
    /** Momentum meter: this far above the bottom (clear of the hotbar, XP, health, food and armor rows). */
    static final int MOMENTUM_UP = 72;

    /** One element on screen: its laid-out text, bar, chrome size and where it was last drawn. */
    static final class Slot {
        final String element;
        List<String> raw = List.of();
        FormattedCharSequence[] seq = new FormattedCharSequence[0];
        String symbol = "";
        float progress = -1f;
        String accent = "";
        int accentRgb = MenuSkins.WHITE;
        String action = "";
        int durationMs;
        boolean drain;
        long start;
        boolean visible;
        int w;
        int h;
        String skinId = "";
        // last drawn rectangle (GUI px), for clicks
        int dx;
        int dy;
        int dw;
        int dh;
        boolean drawn;

        Slot(String element) {
            this.element = element;
        }
    }

    private static final Slot UNDERDOG = new Slot(LmHud.UNDERDOG);
    private static final Slot TP = new Slot(LmHud.TP);
    private static final Slot PENALTY = new Slot(LmHud.PENALTY);
    private static final Slot MOMENTUM = new Slot(LmHud.MOMENTUM);
    private static final Slot[] TOP = {UNDERDOG, TP, PENALTY};
    private static final ArrayDeque<Slot> CARDS = new ArrayDeque<>();

    private static KeyMapping openKey;
    private static long handCursor;
    private static boolean handShown;

    // ------------------------------------------------------------------ setup

    static void init() {
        LmNativeNetwork.onHud(HudOverlay::apply);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(HudOverlay::onRender);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(HudOverlay::onScreenRender);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(HudOverlay::onMouse);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(HudOverlay::onClientTick);
        try {
            net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus()
                    .addListener(HudOverlay::onRegisterKeys);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] HUD key not registered: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        // Unbound by default; the names read as plain text in Controls (no translation file needed).
        openKey = new KeyMapping("Open the newest HUD page", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN, "Legacy Mechanics");
        event.register(openKey);
    }

    // ------------------------------------------------------------------ state from the server

    /** Message 4 (client main thread). */
    static void apply(NativeHudPacket p) {
        if (p == null) {
            return;
        }
        String e = p.element();
        if (LmHud.INSTINCT.equals(e)) {
            if (!p.show()) {
                CARDS.clear();
                return;
            }
            Slot card = new Slot(e);
            fill(card, p);
            CARDS.addFirst(card);
            while (CARDS.size() > CARD_MAX) {
                CARDS.pollLast();
            }
            return;
        }
        Slot s = slot(e);
        if (s == null) {
            return;
        }
        if (!p.show()) {
            s.visible = false;
            return;
        }
        fill(s, p);
    }

    private static Slot slot(String element) {
        if (element == null) {
            return null;
        }
        return switch (element) {
            case LmHud.UNDERDOG -> UNDERDOG;
            case LmHud.TP -> TP;
            case LmHud.PENALTY -> PENALTY;
            case LmHud.MOMENTUM -> MOMENTUM;
            default -> null;
        };
    }

    private static void fill(Slot s, NativeHudPacket p) {
        List<String> lines = p.lines();
        if (LmHud.INSTINCT.equals(s.element) && !lines.isEmpty()) {
            s.symbol = symbol(lines.get(0));
            lines = lines.subList(1, lines.size());
        }
        s.raw = lines;
        s.progress = p.progress();
        s.accent = p.accent();
        s.action = p.action();
        s.durationMs = p.durationMs();
        s.drain = p.drain();
        s.start = System.currentTimeMillis();
        s.visible = true;
        layout(s);
    }

    /** Lay out the text and size the panel (on arrival and on a skin change, never per frame). */
    private static void layout(Slot s) {
        Minecraft mc = Minecraft.m_91087_();
        Font font = mc.f_91062_;
        MenuSkins.Skin skin = MenuLook.skin();
        s.skinId = skin.id();
        s.accentRgb = accentRgb(s.accent, skin);
        NativeText.Ink ink = NativeText.Ink.on(skin, skin.panel());
        List<FormattedCharSequence> out = new ArrayList<>();
        int maxW;
        int minW;
        switch (s.element) {
            case LmHud.UNDERDOG -> {
                maxW = 260;
                minW = 120;
            }
            case LmHud.INSTINCT -> {
                maxW = CARD_W;
                minW = CARD_W;
            }
            case LmHud.MOMENTUM -> {
                maxW = 140;
                minW = MOMENTUM_W;
            }
            default -> {
                maxW = 240;
                minW = 80;
            }
        }
        int textRoom = maxW - 2 * PAD_X - (s.symbol.isEmpty() ? 0 : SYMBOL_W);
        int widest = 0;
        for (String raw : s.raw) {
            Component c = NativeText.parse(raw, skin.text(), false, false, ink);
            if (LmHud.INSTINCT.equals(s.element)) {
                // A card wraps to two lines.
                List<FormattedCharSequence> parts = font.m_92923_(c, textRoom);
                for (int i = 0; i < parts.size() && i < 2; i++) {
                    out.add(parts.get(i));
                }
            } else {
                out.add(NativeText.fit(font, c, textRoom));
            }
        }
        for (FormattedCharSequence q : out) {
            widest = Math.max(widest, font.m_92724_(q));
        }
        s.seq = out.toArray(new FormattedCharSequence[0]);
        int bar = s.progress >= 0f ? 3 : 0;
        s.w = Math.max(minW, Math.min(maxW, widest + 2 * PAD_X + (s.symbol.isEmpty() ? 0 : SYMBOL_W)));
        s.h = 4 + Math.max(1, s.seq.length) * LINE_H + bar;
    }

    private static int accentRgb(String accent, MenuSkins.Skin skin) {
        int rgb;
        if (LmHud.ACCENT_DIFFICULTY.equals(accent)) {
            rgb = skin.primary();
        } else if (LmHud.ACCENT_RIVAL.equals(accent)) {
            rgb = MenuSkins.systemAccentHex('f');
        } else if (accent != null && accent.startsWith("#") && accent.length() == 7) {
            try {
                rgb = Integer.parseInt(accent.substring(1), 16);
            } catch (NumberFormatException e) {
                rgb = skin.trim();
            }
        } else {
            rgb = skin.trim();
        }
        return MenuSkins.readable(rgb & MenuSkins.RGB_MASK, skin.panel(), 3.0);
    }

    /** Instinct kinds: a small symbol each (arrive, status, charging, surge, fusion, power). */
    static String symbol(String kind) {
        return switch (kind == null ? "" : kind) {
            case "arrive" -> "»";
            case "status" -> "≈";
            case "charge" -> "⚡";
            case "surge" -> "✦";
            case "fusion" -> "∞";
            case "power" -> "▲";
            default -> "•";
        };
    }

    // ------------------------------------------------------------------ drawing

    private static void onRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91074_ == null || mc.f_91066_.f_92062_) {
            return;
        }
        if (handShown && !(mc.f_91080_ instanceof ChatScreen)) {
            setHand(false);
        }
        draw(event.getGuiGraphics(), mc);
    }

    static void draw(GuiGraphics g, Minecraft mc) {
        var win = mc.m_91268_();
        int sw = win.m_85445_();
        int sh = win.m_85446_();
        int scale = Math.max(1, (int) Math.round(win.m_85449_()));
        long now = System.currentTimeMillis();
        Font font = mc.f_91062_;
        String skinId = MenuLook.skin().id();
        // Top centre stack.
        int y = EDGE;
        for (Slot s : TOP) {
            s.drawn = false;
            if (!live(s, now)) {
                continue;
            }
            if (!skinId.equals(s.skinId)) {
                layout(s);
            }
            int w = Math.min(s.w, sw - 2 * EDGE);
            drawSlot(g, font, s, (sw - w) / 2, y, w, scale, now, 1f);
            y += s.h + GAP;
        }
        // Instinct cards on the right edge, newest first, stacking downward from 45% down.
        int cy = (int) (sh * 0.45f);
        Iterator<Slot> it = CARDS.iterator();
        while (it.hasNext()) {
            Slot c = it.next();
            c.drawn = false;
            if (!live(c, now)) {
                it.remove();
                continue;
            }
            if (!skinId.equals(c.skinId)) {
                layout(c);
            }
            long left = c.durationMs - (now - c.start);
            float alpha = left >= CARD_FADE_MS ? 1f : Math.max(0f, left / (float) CARD_FADE_MS);
            int w = Math.min(c.w, sw - 2 * EDGE);
            drawSlot(g, font, c, sw - w - EDGE, cy, w, scale, now, alpha);
            cy += c.h + GAP;
        }
        // Momentum above the hotbar, right of centre.
        MOMENTUM.drawn = false;
        if (live(MOMENTUM, now)) {
            if (!skinId.equals(MOMENTUM.skinId)) {
                layout(MOMENTUM);
            }
            int w = Math.min(MOMENTUM.w, sw / 2 - 2 * EDGE);
            drawSlot(g, font, MOMENTUM, sw / 2 + 10, sh - MOMENTUM_UP, w, scale, now, 1f);
        }
    }

    /** Visible, and not past its own duration (an expired timed element hides itself). */
    private static boolean live(Slot s, long now) {
        if (!s.visible) {
            return false;
        }
        if (s.durationMs > 0 && now - s.start >= s.durationMs) {
            s.visible = false;
            return false;
        }
        return true;
    }

    private static void drawSlot(GuiGraphics g, Font font, Slot s, int x, int y, int w, int scale, long now,
            float alpha) {
        if (alpha <= 0.02f) {
            return;
        }
        MenuSkins.Skin skin = MenuLook.skin();
        boolean lite = MenuLook.lite();
        HudChrome.Baked b = HudChrome.get(w, s.h, scale, skin, lite, s.accentRgb);
        if (b != null) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
            g.m_280411_(b.id(), x - b.pad(), y - b.pad(), w + 2 * b.pad(), s.h + 2 * b.pad(), 0f, 0f, b.tw(), b.th(),
                    b.tw(), b.th());
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
        int a = Math.max(5, Math.round(alpha * 255f)) << 24;
        int tx = x + PAD_X;
        if (!s.symbol.isEmpty()) {
            g.m_280056_(font, s.symbol, x + PAD_X - 1, y + 3, a | s.accentRgb, false);
            tx += SYMBOL_W - 2;
        }
        int ty = y + 3;
        for (FormattedCharSequence q : s.seq) {
            g.m_280649_(font, q, tx, ty, a | MenuSkins.INK_FROM_TEXT, false);
            ty += LINE_H;
        }
        if (s.progress >= 0f) {
            float p = s.progress;
            if (s.drain && s.durationMs > 0) {
                p = p * Math.max(0f, 1f - (now - s.start) / (float) s.durationMs);
            }
            int bx = x + 3;
            int bw = w - 6;
            int by = y + s.h - 4;
            g.m_280509_(bx, by, bx + bw, by + 2, (Math.round(alpha * 0x50) << 24));
            g.m_280509_(bx, by, bx + Math.round(bw * p), by + 2, a | s.accentRgb);
        }
        s.dx = x;
        s.dy = y;
        s.dw = w;
        s.dh = s.h;
        s.drawn = true;
    }

    // ------------------------------------------------------------------ clicks

    /** The clickable element under (mx, my) as last drawn, or null. */
    static Slot hit(double mx, double my) {
        for (Slot s : TOP) {
            if (over(s, mx, my)) {
                return s;
            }
        }
        for (Slot c : CARDS) {
            if (over(c, mx, my)) {
                return c;
            }
        }
        return over(MOMENTUM, mx, my) ? MOMENTUM : null;
    }

    private static boolean over(Slot s, double mx, double my) {
        return s.drawn && s.visible && !s.action.isEmpty() && mx >= s.dx && mx < s.dx + s.dw && my >= s.dy
                && my < s.dy + s.dh;
    }

    private static void onMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof ChatScreen) || event.getButton() != 0) {
            return;
        }
        Slot s = hit(event.getMouseX(), event.getMouseY());
        if (s != null) {
            send(s);
            event.setCanceled(true);
        }
    }

    private static void onScreenRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof ChatScreen) {
            boolean over = hit(event.getMouseX(), event.getMouseY()) != null;
            if (over != handShown) {
                setHand(over);
            }
        }
    }

    private static void setHand(boolean on) {
        long window = Minecraft.m_91087_().m_91268_().m_85439_();
        if (on && handCursor == 0L) {
            handCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
        }
        GLFW.glfwSetCursor(window, on ? handCursor : 0L);
        handShown = on;
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || openKey == null) {
            return;
        }
        while (openKey.m_90859_()) {
            Slot newest = newest();
            if (newest != null) {
                send(newest);
            }
        }
    }

    /** The newest visible element with a page. */
    static Slot newest() {
        long now = System.currentTimeMillis();
        Slot best = null;
        for (Slot s : TOP) {
            if (live(s, now) && !s.action.isEmpty() && (best == null || s.start > best.start)) {
                best = s;
            }
        }
        for (Slot c : CARDS) {
            if (live(c, now) && !c.action.isEmpty() && (best == null || c.start > best.start)) {
                best = c;
            }
        }
        if (live(MOMENTUM, now) && !MOMENTUM.action.isEmpty() && (best == null || MOMENTUM.start > best.start)) {
            best = MOMENTUM;
        }
        return best;
    }

    private static void send(Slot s) {
        LmNativeNetwork.sendAction("hub", s.action, "{}");
    }

    // ------------------------------------------------------------------ lifecycle and probes

    /** Logout: nothing carries over to the next server. */
    static void clear() {
        for (Slot s : TOP) {
            s.visible = false;
        }
        MOMENTUM.visible = false;
        CARDS.clear();
        if (handShown) {
            setHand(false);
        }
        HudChrome.clear();
    }

    /** UI probe: every element on screen as {@code H element x y w h action "line | line"} (GUI px). */
    static List<String> debugLayout() {
        List<String> out = new ArrayList<>();
        for (Slot s : TOP) {
            add(out, s);
        }
        for (Slot c : CARDS) {
            add(out, c);
        }
        add(out, MOMENTUM);
        return out;
    }

    private static void add(List<String> out, Slot s) {
        if (!s.drawn || !s.visible) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String r : s.raw) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.plain(r));
        }
        out.add("H " + s.element + " " + s.dx + " " + s.dy + " " + s.dw + " " + s.dh + " "
                + (s.action.isEmpty() ? "-" : s.action) + " \"" + sb + "\"");
    }

    /**
     * Baked HUD chrome: a rounded panel in the skin's panel colour with a 1px accent edge and a 2px accent strip on
     * the left, plus (Full) a soft drop shadow, at the screen's real pixel density so edges stay crisp at every
     * GUI scale. A small LRU cache; released on logout.
     */
    static final class HudChrome {
        private HudChrome() {}

        record Baked(ResourceLocation id, int tw, int th, int pad) {}

        private static final int CACHE = 24;
        private static final Map<String, Baked> BAKED = new LinkedHashMap<>(32, 0.75f, true);
        private static int counter;

        static Baked get(int w, int h, int scale, MenuSkins.Skin skin, boolean lite, int accent) {
            String key = w + "x" + h + "@" + scale + "|" + skin.id() + "|" + skin.panel() + "|" + lite + "|" + accent;
            Baked hit = BAKED.get(key);
            if (hit != null) {
                return hit;
            }
            try {
                int pad = lite ? 0 : SHADOW;
                int tw = (w + 2 * pad) * scale;
                int th = (h + 2 * pad) * scale;
                NativeImage img = new NativeImage(tw, th, false);
                double r = Math.min(5.0, h / 2.0);
                int panel = skin.panel() & MenuSkins.RGB_MASK;
                int fillA = 0xE6;
                for (int py = 0; py < th; py++) {
                    double gy = (py + 0.5) / scale - pad;
                    for (int px = 0; px < tw; px++) {
                        double gx = (px + 0.5) / scale - pad;
                        double d = sdf(gx, gy, w, h, r);
                        double cover = clamp(0.5 - d * scale);
                        // Panel colour: accent edge within 1px of the border, the strip on the left, fill inside.
                        double edgeMix = clamp((d + 1.0) * scale + 0.5);
                        int rgb = gx < 2.5 ? accent : MenuSkins.blend(panel, accent, edgeMix * 0.85);
                        double alpha = cover * (gx < 2.5 || edgeMix > 0.5 ? 1.0 : fillA / 255.0);
                        int outR = (rgb >> 16) & 0xFF;
                        int outG = (rgb >> 8) & 0xFF;
                        int outB = rgb & 0xFF;
                        if (pad > 0 && cover < 1.0) {
                            double sd = sdf(gx, gy - DROP, w, h, r);
                            double sh = sd <= 0 ? 0.35 : 0.35 * Math.pow(clamp(1.0 - sd / pad), 2);
                            double under = sh * (1.0 - alpha);
                            double total = alpha + under;
                            if (total > 0) {
                                outR = (int) Math.round(outR * alpha / total);
                                outG = (int) Math.round(outG * alpha / total);
                                outB = (int) Math.round(outB * alpha / total);
                            }
                            alpha = total;
                        }
                        int a = (int) Math.round(clamp(alpha) * 255);
                        img.m_84988_(px, py, a << 24 | outB << 16 | outG << 8 | outR);
                    }
                }
                DynamicTexture tex = new DynamicTexture(img);
                tex.m_117960_(true, false);
                ResourceLocation id = new ResourceLocation(AdaptiveDifficultyMod.MOD_ID, "hud_bake/" + (counter++));
                Minecraft.m_91087_().m_91097_().m_118495_(id, tex);
                Baked b = new Baked(id, tw, th, pad);
                BAKED.put(key, b);
                while (BAKED.size() > CACHE) {
                    Iterator<Map.Entry<String, Baked>> it = BAKED.entrySet().iterator();
                    Baked old = it.next().getValue();
                    it.remove();
                    Minecraft.m_91087_().m_91097_().m_118513_(old.id());
                }
                return b;
            } catch (Throwable t) {
                return null;
            }
        }

        /** Signed distance to a rounded rectangle at (0,0) size w x h, radius r (negative inside). */
        private static double sdf(double x, double y, int w, int h, double r) {
            double qx = Math.abs(x - w / 2.0) - (w / 2.0 - r);
            double qy = Math.abs(y - h / 2.0) - (h / 2.0 - r);
            double ox = Math.max(qx, 0);
            double oy = Math.max(qy, 0);
            return Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0) - r;
        }

        private static double clamp(double v) {
            return v < 0 ? 0 : v > 1 ? 1 : v;
        }

        static void clear() {
            for (Baked b : BAKED.values()) {
                Minecraft.m_91087_().m_91097_().m_118513_(b.id());
            }
            BAKED.clear();
        }

        static int bakedCount() {
            return BAKED.size();
        }
    }
}
