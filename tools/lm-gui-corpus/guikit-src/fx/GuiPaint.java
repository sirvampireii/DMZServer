import java.awt.image.BufferedImage;

/**
 * GuiPaint - GuiKit's drawing surface: GUI pixels on a screen of real ones at a whole GUI scale, nothing smoothed (mod-toolkit, 2026-11-22).
 * 2026-11-24: a design transform (a menu scaled to fit the window, as LM's NativeMenuFit does: everything drawn is at
 * tx + x * tk GUI px), rounded gradient shapes cut per real pixel row (LM's rounded panels and buttons), and LM's
 * themed skin background.
 */
final class GuiPaint implements McFont.Pen {
    final int width, height, scale;
    private final int[] px;
    private int cx0, cy0, cx1, cy1;
    private double tx, ty, tk = 1;

    GuiPaint(int width, int height, int scale) {
        this.width = width;
        this.height = height;
        this.scale = scale;
        px = new int[width * height];
        unclip();
    }

    /** From now on x, y and sizes are design pixels: GUI = (ox + x * k, oy + y * k). */
    void transform(double ox, double oy, double k) {
        tx = ox;
        ty = oy;
        tk = k;
    }

    void identity() { transform(0, 0, 1); }

    private double sx(double x) { return (tx + x * tk) * scale; }

    private double sy(double y) { return (ty + y * tk) * scale; }

    void backdrop(BufferedImage shot) {
        boolean fits = shot != null && shot.getWidth() == width && shot.getHeight() == height;
        for (int y = 0; y < height; y++) {
            double t = y / (double) height;
            int c;
            if (t < 0.6) c = Px.mix(0x6E90C0, 0xB4C8DC, t / 0.6);
            else c = Px.mix(0x5C7848, 0x40582F, (t - 0.6) / 0.4);
            for (int x = 0; x < width; x++) px[y * width + x] = fits ? shot.getRGB(x, y) & 0xFFFFFF : c;
        }
    }

    /** LM's themed background (MenuSkins.backgroundPixel) over the whole window. */
    void skinBackdrop(int[] baked) {
        System.arraycopy(baked, 0, px, 0, px.length);
    }

    static int[] bakeSkin(LmLook.Skin skin, boolean lite, int width, int height) {
        int[] out = new int[width * height];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) out[y * width + x] = LmLook.background(skin, (x + 0.5) / width, (y + 0.5) / height, lite);
        return out;
    }

    void clip(double x0, double y0, double x1, double y1) {
        cx0 = Math.max(cx0, (int) Math.round(sx(x0)));
        cy0 = Math.max(cy0, (int) Math.round(sy(y0)));
        cx1 = Math.min(cx1, (int) Math.round(sx(x1)));
        cy1 = Math.min(cy1, (int) Math.round(sy(y1)));
    }

    void unclip() {
        cx0 = 0;
        cy0 = 0;
        cx1 = width;
        cy1 = height;
    }

    private void put(int x, int y, int argb) {
        int a = argb >>> 24, i = y * width + x;
        if (a == 255) px[i] = argb & 0xFFFFFF;
        else if (a > 0) px[i] = Px.mix(px[i], argb & 0xFFFFFF, a / 255.0);
    }

    @Override
    public void fill(double x0, double y0, double x1, double y1, int argb) {
        int a = Math.max(cx0, (int) Math.round(sx(x0))), b = Math.min(cx1, (int) Math.round(sx(x1)));
        int c = Math.max(cy0, (int) Math.round(sy(y0))), d = Math.min(cy1, (int) Math.round(sy(y1)));
        for (int y = c; y < d; y++) for (int x = a; x < b; x++) put(x, y, argb);
    }

    /**
     * A rounded rectangle (design px, radius capped at half the shorter side) filled top to bottom from {@code top} to
     * {@code bottom} (ARGB) over its first {@code rows} design rows, corners cut per real pixel row as LM's
     * NativeMenuScreen.paint cuts them per quarter pixel.
     */
    void round(double x, double y, double w, double h, double radius, int top, int bottom, double rows) {
        if (w <= 0 || h <= 0 || rows <= 0) return;
        double r = LmLook.radiusFor(w, h, radius), lim = Math.min(rows, h);
        int c = Math.max(cy0, (int) Math.round(sy(y))), d = Math.min(cy1, (int) Math.round(sy(y + lim)));
        double unit = scale * tk;
        for (int py = c; py < d; py++) {
            double dy = (py + 0.5) / unit - (ty * scale) / unit - y;          // design px from the shape's top
            double edge = Math.min(dy, h - dy);
            double in = LmLook.cornerInset(r, edge);
            int col = top == bottom ? top : LmLook.lerpArgb(top, bottom, dy / lim);
            int a = Math.max(cx0, (int) Math.round(sx(x + in))), b = Math.min(cx1, (int) Math.round(sx(x + w - in)));
            for (int qx = a; qx < b; qx++) put(qx, py, col);
        }
    }

    void round(double x, double y, double w, double h, double radius, int argb) { round(x, y, w, h, radius, argb, argb, h); }

    /** A part of a picture stretched into a GUI rectangle: nearest, times a tint. */
    void image(BufferedImage img, double sx0, double sy0, double sw, double sh, double x, double y, double w, double h, int tint) {
        int a = Math.max(cx0, (int) Math.round(sx(x))), b = Math.min(cx1, (int) Math.round(sx(x + w)));
        int c = Math.max(cy0, (int) Math.round(sy(y))), d = Math.min(cy1, (int) Math.round(sy(y + h)));
        double unit = scale * tk;
        for (int py = c; py < d; py++) {
            int ty0 = (int) Math.floor(sy0 + ((py + 0.5) / unit - ty / tk - y) / h * sh);
            for (int qx = a; qx < b; qx++) {
                int tx0 = (int) Math.floor(sx0 + ((qx + 0.5) / unit - tx / tk - x) / w * sw);
                if (tx0 < 0 || ty0 < 0 || tx0 >= img.getWidth() || ty0 >= img.getHeight()) continue;
                int p = img.getRGB(tx0, ty0);
                int out = (p >>> 24) * (tint >>> 24) / 255 << 24 | (p >> 16 & 255) * (tint >> 16 & 255) / 255 << 16
                        | (p >> 8 & 255) * (tint >> 8 & 255) / 255 << 8 | (p & 255) * (tint & 255) / 255;
                put(qx, py, out);
            }
        }
    }

    /** Corners kept, middle and edges stretched. */
    void slices(BufferedImage img, int sx, int sy, int sw, int sh, double x, double y, double w, double h, int edge) {
        double e = Math.min(edge, Math.min(w, h) / 2);
        double[] u = {sx, sx + e, sx + sw - e, sx + sw}, v = {sy, sy + e, sy + sh - e, sy + sh};
        double[] a = {x, x + e, x + w - e, x + w}, b = {y, y + e, y + h - e, y + h};
        for (int j = 0; j < 3; j++)
            for (int i = 0; i < 3; i++)
                if (a[i + 1] > a[i] && b[j + 1] > b[j])
                    image(img, u[i], v[j], u[i + 1] - u[i], v[j + 1] - v[j], a[i], b[j], a[i + 1] - a[i], b[j + 1] - b[j], 0xFFFFFFFF);
    }

    /** Part of a ring; angles in radians, 0 = up, clockwise. */
    void ring(double x, double y, double inner, double outer, double from, double to, int argb) {
        int a = Math.max(cx0, (int) Math.floor(sx(x - outer))), b = Math.min(cx1, (int) Math.ceil(sx(x + outer)));
        int c = Math.max(cy0, (int) Math.floor(sy(y - outer))), d = Math.min(cy1, (int) Math.ceil(sy(y + outer)));
        double full = Math.PI * 2, unit = scale * tk;
        for (int py = c; py < d; py++) {
            for (int qx = a; qx < b; qx++) {
                double dx = (qx + 0.5) / unit - tx / tk - x, dy = (py + 0.5) / unit - ty / tk - y, r = Math.sqrt(dx * dx + dy * dy);
                if (r < inner || r >= outer) continue;
                if (to - from < full - 1e-6) {
                    double turn = ((Math.atan2(dx, -dy) - from) % full + full) % full;
                    if (turn >= to - from) continue;
                }
                put(qx, py, argb);
            }
        }
    }

    /** The average colour under a GUI rectangle (design px under a transform). */
    int behind(double x0, double y0, double x1, double y1) {
        int a = Math.max(0, (int) sx(x0)), b = Math.min(width, (int) Math.ceil(sx(x1)));
        int c = Math.max(0, (int) sy(y0)), d = Math.min(height, (int) Math.ceil(sy(y1)));
        long r = 0, g = 0, bl = 0, n = 0;
        for (int y = c; y < d; y += 2) {
            for (int x = a; x < b; x += 2) {
                int p = px[y * width + x];
                r += p >> 16 & 255;
                g += p >> 8 & 255;
                bl += p & 255;
                n++;
            }
        }
        return n == 0 ? 0 : Px.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
    }

    BufferedImage image() {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, width, height, px, 0, width);
        return out;
    }
}
