package com.dbzlegacy.adaptivedifficulty.client;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.ColorWheel;
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
 * LM 4.7.66 baked textures for the color wheel ({@link ColorWheel}): the hue ring once, and the saturation/value
 * square once per hue step, so a wheel costs two blits a frame. Own small cache, released on logout.
 */
final class ColorWheelTex {
    private ColorWheelTex() {}

    /** Bake resolution of the ring box (upscaled with linear filtering). */
    private static final int RING_RES = 128;
    private static final int SQUARE_RES = 32;
    /** Hue steps the square is baked at. */
    private static final int HUE_STEPS = 72;
    private static final int CACHE = 10;

    private record Baked(ResourceLocation id, int w, int h) {}

    private static final java.util.Set<String> FAILED = new java.util.HashSet<>();
    private static final Map<String, Baked> BAKED = new LinkedHashMap<>(16, 0.75f, true);
    private static int counter;

    /** Ring at (x, y) with side {@code size} in the current pose. */
    static void drawRing(GuiGraphics g, int x, int y, int size) {
        Baked b = ring();
        if (b != null) {
            blit(g, b, x, y, size, size);
        }
    }

    /** Saturation/value square for {@code hue} at (x, y), side {@code side}. */
    static void drawSquare(GuiGraphics g, int x, int y, int side, double hue) {
        Baked b = square(hue);
        if (b != null) {
            blit(g, b, x, y, side, side);
        }
    }

    private static void blit(GuiGraphics g, Baked b, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        g.m_280411_(b.id(), x, y, w, h, 0f, 0f, b.w(), b.h(), b.w(), b.h());
    }

    private static Baked ring() {
        String key = "ring";
        Baked hit = BAKED.get(key);
        if (hit != null) {
            return hit;
        }
        if (FAILED.contains(key)) {
            return null;                            // a failed bake is not retried every frame
        }
        NativeImage img = null;
        try {
            img = new NativeImage(RING_RES, RING_RES, false);
            double c = RING_RES / 2.0;
            double outer = c;
            double inner = c * ColorWheel.RING_INNER;
            for (int y = 0; y < RING_RES; y++) {
                for (int x = 0; x < RING_RES; x++) {
                    double px = x + 0.5;
                    double py = y + 0.5;
                    double d = Math.hypot(px - c, py - c);
                    // Soft one-pixel edges inside and out.
                    double a = Math.max(0, Math.min(1, outer - d)) * Math.max(0, Math.min(1, d - inner));
                    int rgb = ColorWheel.hsvToRgb(ColorWheel.hueAt(RING_RES, px, py), 1, 1);
                    img.m_84988_(x, y, abgr((int) Math.round(a * 255), rgb));
                }
            }
            return register(key, img);
        } catch (Throwable t) {
            if (img != null && !BAKED.containsKey(key)) {
                img.close();                        // 4.7.73: no off-heap leak on a failed bake
            }
            if (FAILED.size() < 64) {
                FAILED.add(key);
            }
            AdaptiveDifficultyMod.LOGGER.warn("[{}] color wheel bake failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
            return null;
        }
    }

    private static Baked square(double hue) {
        int step = Math.floorMod((int) Math.round(hue * HUE_STEPS), HUE_STEPS);
        String key = "sq|" + step;
        Baked hit = BAKED.get(key);
        if (hit != null) {
            return hit;
        }
        if (FAILED.contains(key)) {
            return null;                            // a failed bake is not retried every frame
        }
        NativeImage img = null;
        try {
            double h = step / (double) HUE_STEPS;
            img = new NativeImage(SQUARE_RES, SQUARE_RES, false);
            for (int y = 0; y < SQUARE_RES; y++) {
                double v = 1 - y / (double) (SQUARE_RES - 1);
                for (int x = 0; x < SQUARE_RES; x++) {
                    double s = x / (double) (SQUARE_RES - 1);
                    img.m_84988_(x, y, abgr(0xFF, ColorWheel.hsvToRgb(h, s, v)));
                }
            }
            return register(key, img);
        } catch (Throwable t) {
            if (img != null && !BAKED.containsKey(key)) {
                img.close();                        // 4.7.73: no off-heap leak on a failed bake
            }
            if (FAILED.size() < 64) {
                FAILED.add(key);
            }
            return null;
        }
    }

    private static Baked register(String key, NativeImage img) {
        DynamicTexture tex = new DynamicTexture(img);
        tex.m_117960_(true, false);
        ResourceLocation id = new ResourceLocation(AdaptiveDifficultyMod.MOD_ID, "color_wheel/" + (counter++));
        Minecraft.m_91087_().m_91097_().m_118495_(id, tex);
        Baked b = new Baked(id, img.m_84982_(), img.m_85084_());
        BAKED.put(key, b);
        while (BAKED.size() > CACHE) {
            Iterator<Map.Entry<String, Baked>> it = BAKED.entrySet().iterator();
            Map.Entry<String, Baked> old = it.next();
            if ("ring".equals(old.getKey())) {
                // Keep the ring; drop the oldest square instead.
                BAKED.remove("ring");
                BAKED.put("ring", old.getValue());
                continue;
            }
            it.remove();
            Minecraft.m_91087_().m_91097_().m_118513_(old.getValue().id());
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
        FAILED.clear();
    }
}
