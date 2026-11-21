package com.dbzlegacy.adaptivedifficulty.net.gui;

/**
 * LM 4.7.58 native renderer placement: where a menu panel goes on the window and how big it is.
 * Pure math, shared by the client renderer and the server self-test.
 *
 * <p>Centered menus (no {@code fillScreen}) render at native GUI size. A panel that does not fit
 * the window (less {@code margin} on each side) is scaled down uniformly until it fits, never below
 * {@link #MIN_SCALE}. Only a panel still too tall at that floor scrolls, and then the whole menu
 * scrolls. A {@code fillScreen} menu scales its design box to the window (up or down), centered.
 */
public final class NativeMenuFit {
    private NativeMenuFit() {}

    /** Smallest scale a centered menu shrinks to before it scrolls instead. */
    public static final float MIN_SCALE = 0.5f;

    /**
     * @param x screen x of the panel's left edge
     * @param y screen y of the panel's top edge at scroll 0
     * @param scale design px to screen (GUI) px
     * @param maxScroll how far (screen px) the whole menu scrolls; 0 when it fits
     */
    public record Placement(float x, float y, float scale, float maxScroll) {
        public boolean scrolls() {
            return maxScroll > 0.5f;
        }
    }

    public static Placement place(int screenW, int screenH, int panelW, int panelH, int margin,
            boolean fillScreen, int designW, int designH) {
        int m = Math.max(0, margin);
        float availW = Math.max(1, screenW - 2 * m);
        float availH = Math.max(1, screenH - 2 * m);
        if (fillScreen) {
            int dw = designW > 0 ? designW : Math.max(1, panelW);
            int dh = designH > 0 ? designH : Math.max(1, panelH);
            float s = Math.min(availW / dw, availH / dh);
            return new Placement((screenW - dw * s) / 2f, (screenH - dh * s) / 2f, s, 0f);
        }
        int pw = Math.max(1, panelW);
        int ph = Math.max(1, panelH);
        float s = Math.min(1f, Math.min(availW / pw, availH / ph));
        s = Math.max(MIN_SCALE, s);
        float h = ph * s;
        float x = (screenW - pw * s) / 2f;
        if (h <= availH + 0.01f) {
            return new Placement(x, (screenH - h) / 2f, s, 0f);
        }
        return new Placement(x, m, s, h - availH);
    }
}
