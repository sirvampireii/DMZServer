package com.dmzlegacy.kaipath.ultra.consumers.legacymechanics;

/**
 * Drop-in math for {@code fillScreen} screens (any partner channel). Pure Java: no Minecraft
 * types, so it merges into any KaiPath version unchanged and is tested offline
 * ({@code UltraBridgeFitTest}). Merge guide with the exact 1.5.3 insertion points:
 * {@code FILLSCREEN_FIT.md} next to this file.
 *
 * <p>Three fixes, no wire change (protocol {@code "1"}, no new layout keys):
 *
 * <ol>
 *   <li>{@link #fit}: one scale for x and y, {@code min(availW / designW, availH / designH)}, with
 *       the scaled box centered. Scaling x and y separately (window / design on each axis)
 *       stretches buttons and the model rectangle on any window that is not the design box's
 *       shape, and the model's {@code min(w / 0.6, h / 1.8)} fit can flip axis and cut a giant
 *       form. On a window with the box's shape both give the same result.
 *   <li>{@link #titleScale}: the header band is scaled with the box, so the title is drawn at the
 *       box scale too (never below 1, never past the band or the panel width).
 *   <li>{@link #scrollBar}: a scroll bar for the content band when it overflows, so controls
 *       below the fold are visible as such; {@link #scrollForThumb} turns a thumb drag into a
 *       scroll offset.
 * </ol>
 */
public final class UltraBridgeFit {
    /** Scroll bar width in GUI pixels (not scaled: it is chrome, like the Close band). */
    public static final int BAR_W = 4;
    /** Gap between the bar and the panel's right edge. */
    public static final int BAR_INSET = 2;
    /** Smallest thumb, so it stays grabbable on a long list. */
    public static final int MIN_THUMB_H = 10;

    private UltraBridgeFit() {}

    /** Where the design box lands on the screen, and its one scale. */
    public record Placement(int x, int y, int w, int h, float scale) {
        /** Design-space value to screen pixels (both axes use the same scale). */
        public int px(int design) {
            return Math.round(design * scale);
        }
    }

    /**
     * Uniform fit of {@code designW x designH} into the screen inset by {@code margin}: the box is
     * scaled by one factor so it is as large as fits, then centered on the leftover axis.
     * Replaces {@code panelW = availW; panelH = availH; sx = panelW / dw; sy = panelH / dh}.
     */
    public static Placement fit(int screenW, int screenH, int margin, int designW, int designH) {
        int availW = Math.max(64, screenW - margin * 2);
        int availH = Math.max(64, screenH - margin * 2);
        int dw = Math.max(1, designW);
        int dh = Math.max(1, designH);
        float scale = Math.min(availW / (float) dw, availH / (float) dh);
        int w = Math.min(availW, Math.round(dw * scale));
        int h = Math.min(availH, Math.round(dh * scale));
        int x = margin + (availW - w) / 2;
        int y = margin + (availH - h) / 2;
        return new Placement(x, y, w, h, scale);
    }

    /**
     * Scale for the title text: the box scale, at least 1 (vanilla size), and small enough that
     * the scaled title fits the header band ({@code bandH}, already scaled) and {@code maxW}.
     *
     * @param titleW title width at scale 1 ({@code font.width(title)})
     * @param fontH  font line height at scale 1 (9)
     */
    public static float titleScale(float boxScale, int titleW, int fontH, int bandH, int maxW) {
        float s = Math.max(1f, boxScale);
        if (fontH > 0 && bandH > 0) {
            s = Math.min(s, Math.max(1f, (bandH - 2) / (float) fontH));
        }
        if (titleW > 0 && maxW > 0) {
            s = Math.min(s, Math.max(1f, maxW / (float) titleW));
        }
        return s;
    }

    /** A vertical scroll bar: the track and the thumb, in screen pixels. */
    public record Bar(int x, int trackTop, int trackH, int thumbTop, int thumbH) {
        public boolean hits(double mx, double my) {
            return mx >= x - 2 && mx <= x + BAR_W + 2 && my >= trackTop && my <= trackTop + trackH;
        }

        public boolean hitsThumb(double my) {
            return my >= thumbTop && my <= thumbTop + thumbH;
        }
    }

    /**
     * Scroll bar for a content band from {@code bandTop} to {@code bandBottom} whose right edge is
     * {@code bandRight}, or {@code null} when nothing scrolls ({@code maxScroll <= 0}).
     *
     * @param scroll    current offset, 0 to {@code maxScroll}
     * @param maxScroll how far the content scrolls (content height minus band height)
     */
    public static Bar scrollBar(int bandRight, int bandTop, int bandBottom, int scroll, int maxScroll) {
        int trackH = bandBottom - bandTop;
        if (maxScroll <= 0 || trackH <= 0) {
            return null;
        }
        int thumbH = Math.max(Math.min(MIN_THUMB_H, trackH),
                Math.round(trackH * (trackH / (float) (trackH + maxScroll))));
        int travel = trackH - thumbH;
        int clamped = Math.max(0, Math.min(maxScroll, scroll));
        int thumbTop = bandTop + (travel <= 0 ? 0 : Math.round(travel * (clamped / (float) maxScroll)));
        return new Bar(bandRight - BAR_INSET - BAR_W, bandTop, trackH, thumbTop, thumbH);
    }

    /**
     * Scroll offset for a thumb dragged so its top sits at {@code mouseY - grabOffset}
     * ({@code grabOffset} = where in the thumb the press landed). Clamped to the track.
     */
    public static int scrollForThumb(Bar bar, double mouseY, double grabOffset, int maxScroll) {
        if (bar == null || maxScroll <= 0) {
            return 0;
        }
        int travel = bar.trackH() - bar.thumbH();
        if (travel <= 0) {
            return 0;
        }
        double top = mouseY - grabOffset - bar.trackTop();
        double frac = Math.max(0d, Math.min(1d, top / travel));
        return (int) Math.round(frac * maxScroll);
    }

    /** Scroll offset after a wheel notch, {@code step} pixels per notch, clamped. */
    public static int wheel(int scroll, double delta, int step, int maxScroll) {
        int next = scroll - (int) Math.round(delta * Math.max(1, step));
        return Math.max(0, Math.min(Math.max(0, maxScroll), next));
    }

    /** Track and thumb colors: a dim track, a lighter thumb (ARGB). */
    public static final int TRACK_ARGB = 0x40FFFFFF;
    public static final int THUMB_ARGB = 0xC0E0E0E0;
}
