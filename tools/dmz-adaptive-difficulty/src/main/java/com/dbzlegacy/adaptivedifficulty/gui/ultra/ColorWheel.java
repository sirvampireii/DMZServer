package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import java.util.ArrayList;
import java.util.List;

/**
 * LM 4.7.66 color wheel spec element ({@code inputsJson} {@code type=color_wheel}), shared by the custom menu
 * skin editor and the 4.7.67 part colors page. Pure Java: the server self-test and the LM client share the
 * geometry, so a tap the client turns into a color is the color the test expects.
 *
 * <p>Shape inside a {@code size x size} box: a hue ring (outer radius {@code size/2}, inner radius
 * {@link #RING_INNER} of that), and inside it a square whose x is saturation (left 0, right 1) and whose y is
 * value (top 1, bottom 0) at the ring's current hue.
 *
 * <p>Wire: {@code {"id", "type":"color_wheel", "x", "y", "w", "h", "target": <text field id>}}. A tap fills the
 * target field with {@code #rrggbb}. KaiPath and LM clients before 4.7.66 skip unknown input types, so the page
 * still works with the hex field alone; the server adds the wheel only for a client that lists {@link #FEATURE}.
 */
public final class ColorWheel {
    private ColorWheel() {}

    public static final String TYPE = "color_wheel";
    /** Hello feature: this LM client draws {@link #TYPE}. */
    public static final String FEATURE = "colorWheel";
    /** Smallest wheel box (design px): big enough to aim at GUI scale 2. */
    public static final int MIN_SIZE = 40;
    /** Inner ring radius as a share of the outer radius. */
    public static final double RING_INNER = 0.74;

    /** {h 0..1, s 0..1, v 0..1} of an RGB color. */
    public static double[] rgbToHsv(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double d = max - min;
        double h = 0;
        if (d > 1e-9) {
            if (max == r) {
                h = ((g - b) / d) % 6;
            } else if (max == g) {
                h = (b - r) / d + 2;
            } else {
                h = (r - g) / d + 4;
            }
            h /= 6;
            if (h < 0) {
                h += 1;
            }
        }
        return new double[] {h, max <= 0 ? 0 : d / max, max};
    }

    public static int hsvToRgb(double h, double s, double v) {
        double hh = ((h % 1) + 1) % 1 * 6;
        double c = v * s;
        double x = c * (1 - Math.abs(hh % 2 - 1));
        double m = v - c;
        double r;
        double g;
        double b;
        switch ((int) Math.floor(hh)) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return (clamp8(r + m) << 16) | (clamp8(g + m) << 8) | clamp8(b + m);
    }

    private static int clamp8(double v) {
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }

    /** Square inside the ring: {x, y, side} relative to the box's top-left. */
    public static double[] square(int size) {
        double r = size / 2.0 * RING_INNER;
        double side = r * Math.sqrt(2) * 0.86;
        double o = (size - side) / 2.0;
        return new double[] {o, o, side};
    }

    /** What a point in the box is: on the ring (hue), in the square (saturation and value), or neither. */
    public enum Part { RING, SQUARE, NONE }

    public static Part partAt(int size, double px, double py) {
        double c = size / 2.0;
        double d = Math.hypot(px - c, py - c);
        if (d <= c && d >= c * RING_INNER) {
            return Part.RING;
        }
        double[] sq = square(size);
        if (px >= sq[0] && px <= sq[0] + sq[2] && py >= sq[1] && py <= sq[1] + sq[2]) {
            return Part.SQUARE;
        }
        return Part.NONE;
    }

    /** Hue of a point on the ring: 0 at the top, increasing clockwise. */
    public static double hueAt(int size, double px, double py) {
        double c = size / 2.0;
        double a = Math.atan2(px - c, -(py - c));
        double h = a / (2 * Math.PI);
        return h < 0 ? h + 1 : h;
    }

    /** {saturation, value} of a point in the square. */
    public static double[] svAt(int size, double px, double py) {
        double[] sq = square(size);
        double s = Math.max(0, Math.min(1, (px - sq[0]) / sq[2]));
        double v = Math.max(0, Math.min(1, 1 - (py - sq[1]) / sq[2]));
        return new double[] {s, v};
    }

    /**
     * The color a tap at {@code (px, py)} picks, given the wheel's current {h, s, v} ({@code hsv} is updated in
     * place). -1 when the tap misses both the ring and the square. A ring tap keeps saturation and value, but a
     * gray (saturation or value near 0) jumps to full color so the hue shows.
     */
    public static int pick(int size, double px, double py, double[] hsv) {
        Part part = partAt(size, px, py);
        if (part == Part.RING) {
            hsv[0] = hueAt(size, px, py);
            if (hsv[1] < 0.15 || hsv[2] < 0.15) {
                hsv[1] = 1;
                hsv[2] = 1;
            }
        } else if (part == Part.SQUARE) {
            double[] sv = svAt(size, px, py);
            hsv[1] = sv[0];
            hsv[2] = sv[1];
        } else {
            return -1;
        }
        return hsvToRgb(hsv[0], hsv[1], hsv[2]);
    }

    /** Pins the geometry and the conversions. */
    public static List<String> selfTest() {
        List<String> bad = new ArrayList<>();
        for (int rgb : new int[] {0xE23B3B, 0x57D9A3, 0xF0B429, 0x2563EB, 0xFFFFFF, 0x000000, 0x808080, 0xFF00FF}) {
            double[] hsv = rgbToHsv(rgb);
            int back = hsvToRgb(hsv[0], hsv[1], hsv[2]);
            if (Math.abs(((back >> 16) & 0xFF) - ((rgb >> 16) & 0xFF)) > 1
                    || Math.abs(((back >> 8) & 0xFF) - ((rgb >> 8) & 0xFF)) > 1
                    || Math.abs((back & 0xFF) - (rgb & 0xFF)) > 1) {
                bad.add(String.format("color wheel: rgb %06x -> hsv -> %06x", rgb, back));
            }
        }
        int size = 48;
        double[] hsv = {0, 1, 1};
        // Top of the ring is red, a third of the way round clockwise is green.
        if (pick(size, size / 2.0, 1.5, hsv) != 0xFF0000) {
            bad.add("color wheel: the top of the ring must pick red");
        }
        double a = 2 * Math.PI / 3;
        double rr = size / 2.0 * (1 + RING_INNER) / 2;
        int green = pick(size, size / 2.0 + rr * Math.sin(a), size / 2.0 - rr * Math.cos(a), hsv);
        if (((green >> 8) & 0xFF) < 0xF0 || ((green >> 16) & 0xFF) > 0x10) {
            bad.add(String.format("color wheel: a third round must pick green, got %06x", green));
        }
        double[] sq = square(size);
        hsv = new double[] {0, 1, 1};
        if (pick(size, sq[0] + sq[2] / 2, sq[1] + sq[2], hsv) != 0x000000) {
            bad.add("color wheel: the square's bottom must pick black");
        }
        if (pick(size, sq[0], sq[1], hsv) != 0xFFFFFF) {
            bad.add("color wheel: the square's top-left must pick white");
        }
        if (pick(size, 0.5, 0.5, hsv) != -1) {
            bad.add("color wheel: a corner outside the ring picks nothing");
        }
        return bad;
    }
}
