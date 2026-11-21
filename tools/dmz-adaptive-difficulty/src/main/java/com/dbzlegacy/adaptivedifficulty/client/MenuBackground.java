package com.dbzlegacy.adaptivedifficulty.client;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.SaiyanRoyal;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * LM 4.7.62 baked textures for the native menus, so nothing expensive is computed per frame:
 * <ul>
 *   <li>the full-screen themed background of each skin (and its flat Lite version), baked once per skin and
 *       window size from {@link MenuSkins#backgroundPixel} and blitted with linear filtering;</li>
 *   <li>the panel's two soft drop-shadow layers, baked once per panel size and skin strength (one layer in
 *       Lite), from the same ring math the 4.7.60 renderer drew fill by fill every frame.</li>
 * </ul>
 * Textures live in a small least-recently-used cache and are released when evicted or on logout.
 */
final class MenuBackground {
    private MenuBackground() {}

    /** Background bake resolution cap (longest side); upscaled with linear filtering. */
    private static final int BG_MAX = 960;
    private static final int CACHE = 12;

    private record Baked(ResourceLocation id, int w, int h, DynamicTexture tex, int pad, int drop) {}

    private static final Map<String, Baked> BAKED = new LinkedHashMap<>(16, 0.75f, true);
    private static int counter;

    /** Draw {@code skin}'s background over the whole screen at {@code alpha} (crossfades use two calls). */
    static void draw(GuiGraphics g, int screenW, int screenH, MenuSkins.Skin skin, boolean lite, float alpha) {
        if (alpha <= 0f) {
            return;
        }
        Baked b = background(skin, lite, screenW, screenH);
        if (b == null) {
            g.m_280509_(0, 0, screenW, screenH, skin.scrim());
            return;
        }
        blit(g, b, 0, 0, screenW, screenH, alpha);
    }

    /**
     * Draw the panel's drop shadow for a {@code w x h} panel at design (0,0) in the current pose: Full has the
     * wide ambient layer and the tight key layer, Lite only the key layer.
     */
    static void panelShadow(GuiGraphics g, int w, int h, int radius, MenuSkins.Skin skin, boolean lite, float alpha) {
        Baked b = shadow(w, h, radius, skin, lite);
        if (b == null) {
            return;
        }
        blit(g, b, -b.pad(), -b.pad(), b.w(), b.h(), alpha);
    }

    private static void blit(GuiGraphics g, Baked b, int x, int y, int w, int h, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        g.m_280411_(b.id(), x, y, w, h, 0f, 0f, b.w(), b.h(), b.w(), b.h());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static Baked background(MenuSkins.Skin skin, boolean lite, int screenW, int screenH) {
        int sw = Math.max(1, screenW);
        int sh = Math.max(1, screenH);
        double k = Math.min(1.0, BG_MAX / (double) Math.max(sw, sh));
        int tw = Math.max(16, (int) Math.round(sw * k));
        int th = Math.max(9, (int) Math.round(sh * k));
        String key = "bg|" + skin.id() + "|" + lite + "|" + tw + "x" + th;
        Baked hit = BAKED.get(key);
        if (hit != null) {
            return hit;
        }
        try {
            NativeImage img = new NativeImage(tw, th, false);
            for (int y = 0; y < th; y++) {
                double v = (y + 0.5) / th;
                for (int x = 0; x < tw; x++) {
                    img.m_84988_(x, y, abgr(0xFF, MenuSkins.backgroundPixel(skin, (x + 0.5) / tw, v, lite)));
                }
            }
            return register(key, img, 0, 0);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] menu background bake failed for {}: {}",
                    AdaptiveDifficultyMod.MOD_ID, skin.id(), t.toString());
            return null;
        }
    }

    private static Baked shadow(int w, int h, int radius, MenuSkins.Skin skin, boolean lite) {
        int ambient = lite ? 0 : SaiyanRoyal.AMBIENT_RINGS;
        int pad = Math.max(ambient, SaiyanRoyal.KEY_RINGS) + 1;
        int drop = Math.max(lite ? 0 : SaiyanRoyal.AMBIENT_DROP, SaiyanRoyal.KEY_DROP);
        int tw = w + 2 * pad;
        int th = h + 2 * pad + drop;
        String key = "sh|" + w + "x" + h + "|" + radius + "|" + skin.dark() + "|" + lite;
        Baked hit = BAKED.get(key);
        if (hit != null) {
            return hit;
        }
        try {
            NativeImage img = new NativeImage(tw, th, false);
            int ambA = skin.shadowAlpha(SaiyanRoyal.AMBIENT_ALPHA);
            int keyA = skin.shadowAlpha(SaiyanRoyal.KEY_ALPHA);
            for (int y = 0; y < th; y++) {
                for (int x = 0; x < tw; x++) {
                    double px = x - pad + 0.5;
                    double py = y - pad + 0.5;
                    double a = 0;
                    // Same layering as the 4.7.60 rings: outer rings first, each composited over.
                    for (int i = ambient; i >= 1; i--) {
                        if (inRounded(px, py - SaiyanRoyal.AMBIENT_DROP, -i, -i, w + 2 * i, h + 2 * i, radius + i)) {
                            double ai = SaiyanRoyal.ringAlpha(ambA, ambient, i) / 255.0;
                            a = ai + a * (1 - ai);
                        }
                    }
                    for (int i = SaiyanRoyal.KEY_RINGS; i >= 1; i--) {
                        if (inRounded(px, py - SaiyanRoyal.KEY_DROP, -i, -i, w + 2 * i, h + 2 * i, radius + i)) {
                            double ai = SaiyanRoyal.ringAlpha(keyA, SaiyanRoyal.KEY_RINGS, i) / 255.0;
                            a = ai + a * (1 - ai);
                        }
                    }
                    img.m_84988_(x, y, abgr((int) Math.round(a * 255), MenuSkins.BLACK));
                }
            }
            return register(key, img, pad, drop);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean inRounded(double px, double py, int x, int y, int w, int h, int r) {
        if (px < x || py < y || px >= x + w || py >= y + h) {
            return false;
        }
        int rr = SaiyanRoyal.radiusFor(w, h, r);
        double cx = px < x + rr ? x + rr : px > x + w - rr ? x + w - rr : px;
        double cy = py < y + rr ? y + rr : py > y + h - rr ? y + h - rr : py;
        return (px - cx) * (px - cx) + (py - cy) * (py - cy) <= rr * rr;
    }

    private static Baked register(String key, NativeImage img, int pad, int drop) {
        DynamicTexture tex = new DynamicTexture(img);
        tex.m_117960_(true, false);
        ResourceLocation id = new ResourceLocation(AdaptiveDifficultyMod.MOD_ID, "menu_bake/" + (counter++));
        Minecraft.m_91087_().m_91097_().m_118495_(id, tex);
        Baked b = new Baked(id, img.m_84982_(), img.m_85084_(), tex, pad, drop);
        BAKED.put(key, b);
        while (BAKED.size() > CACHE) {
            Iterator<Map.Entry<String, Baked>> it = BAKED.entrySet().iterator();
            Baked old = it.next().getValue();
            it.remove();
            Minecraft.m_91087_().m_91097_().m_118513_(old.id());
        }
        return b;
    }

    /** NativeImage stores ABGR. */
    private static int abgr(int alpha, int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int gr = (rgb >> 8) & 0xFF;
        int bl = rgb & 0xFF;
        return (alpha & 0xFF) << 24 | bl << 16 | gr << 8 | r;
    }

    /** Release every baked texture (logout). */
    static void clear() {
        for (Baked b : BAKED.values()) {
            Minecraft.m_91087_().m_91097_().m_118513_(b.id());
        }
        BAKED.clear();
    }

    /** Test probes: how many textures are baked right now. */
    static int bakedCount() {
        return BAKED.size();
    }
}
