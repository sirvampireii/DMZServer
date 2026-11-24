import java.util.List;
import java.util.Locale;

/**
 * LmLook - LegacyMechanics' menu skins for GuiKit (mod-toolkit, 2026-11-24): the five skins of LM's
 * {@code gui/ultra/MenuSkins.java}, every derived tone the native renderer ({@code client/NativeMenuScreen.java})
 * draws, the per-system accents, the colour-letter inks and the themed background. A port, number for number: when
 * MenuSkins / SaiyanRoyal change, change this file (the GuiKit LM corpus in DMZServer\tools\lm-gui-corpus says so too).
 */
final class LmLook {
    private LmLook() {}

    static final int WHITE = 0xFFFFFF, BLACK = 0x000000, GRAY_MID = 0x808080, DANGER = 0xE23B3B;
    // SaiyanRoyal depth constants
    static final int PANEL_RADIUS = 14, BUTTON_RADIUS = 8, BAR_RADIUS = 3, BOX_RADIUS = 6;
    static final int AMBIENT_RINGS = 10, AMBIENT_DROP = 4, AMBIENT_ALPHA = 0x14, KEY_RINGS = 4, KEY_DROP = 2, KEY_ALPHA = 0x30;
    static final int BUTTON_SHADOW_RINGS = 2, BUTTON_SHADOW_DROP = 2, BUTTON_SHADOW_ALPHA = 0x40;
    static final int PRESS_SINK = 2, TAB_LIFT = 1, GLOW_RINGS = 3, GLOW_ALPHA = 0x38, INSET_SHADOW_PX = 4;
    static final double FACE_TOP_LIFT = 0.12, FACE_BOTTOM_DROP = 0.18;
    static final int DIVIDER_ALPHA = 0xC0, GLOSS_LINE = 0x60FFFFFF, EDGE = 10;

    enum Motif { BANNER, NAMEK, VEINS, WISPS, CLOUDS }

    record Skin(String id, String name, boolean dark, int panel, int primary, int trim, int text, int confirm,
                int bgTop, int bgBottom, int motifA, int motifB, Motif motif) {
        int face() { return blend(panel, primary, dark ? 0.12 : 0.10); }
        int faceHover() { return blend(panel, primary, dark ? 0.25 : 0.20); }
        int edgeRest(int tint) { return blend(tint, face(), 0.4); }
        int dimFace() { return dark ? blend(panel, GRAY_MID, 0.08) : blend(panel, BLACK, 0.05); }
        int dimFaceHover() { return blend(dimFace(), text, 0.05); }
        int dimEdge() { return dark ? blend(panel, WHITE, 0.14) : blend(panel, BLACK, 0.16); }
        /**
         * TARGET (corpus): a light skin blends 0.30, not LM's 0.42. At 0.42 Zeno Omni's inactive tabs (live controls drawn
         * in this ink) read 3.3:1 on the dim face, under the 4.5:1 a light skin promises; 0.30 reads 4.7:1.
         */
        int muted() { return blend(text, panel, dark ? 0.52 : 0.30); }
        int confirmFace() { return blend(confirm, panel, dark ? 0.62 : 0.78); }
        int confirmFaceHover() { return blend(confirm, panel, dark ? 0.50 : 0.68); }
        int dangerFace() { return blend(DANGER, panel, dark ? 0.60 : 0.80); }
        int dangerFaceHover() { return blend(DANGER, panel, dark ? 0.48 : 0.70); }
        int infoFill() { return dark ? blend(panel, BLACK, 0.35) : blend(panel, BLACK, 0.04); }
        int insetEdge() { return dark ? blend(panel, BLACK, 0.5) : blend(panel, BLACK, 0.15); }
        int insetShadowAlpha() { return dark ? 0x80 : 0x30; }
        int trackRim() { return blend(trim, panel, 0.7); }
        int trackFill() { return dark ? blend(panel, BLACK, 0.75) : blend(panel, BLACK, 0.10); }
        int fieldEdge() { return blend(trim, panel, 0.65); }
        int fieldFill() { return dark ? blend(panel, BLACK, 0.45) : blend(panel, WHITE, 0.5); }
        int bookFill() { return dark ? blend(panel, primary, 0.045) : blend(panel, BLACK, 0.03); }
        int panelTop() { return blend(panel, WHITE, dark ? 0.07 : 0.5); }
        int panelBottom() { return blend(panel, BLACK, dark ? 0.25 : 0.04); }
        int shadowAlpha(int a) { return dark ? a : a * 55 / 100; }
        int edgeHighlight() { return dark ? 0x40FFFFFF : 0x80FFFFFF; }
        int edgeShade() { return dark ? 0x50000000 : 0x26000000; }
        boolean textShadow() { return dark; }
        int glow() { return primary; }
        int scrim() { return dark ? 0x90000000 : 0x70000000; }
        int frameRing(int accent) { return blend(accent, panel, 0.6); }
        double backgroundDim() { return dark ? 0.15 : 0.08; }
    }

    static final Skin SAIYAN_ROYAL = new Skin("saiyan_royal", "Saiyan Royal", true, 0x160B0D, 0xE23B3B, 0xF0B429, 0xF6E8E4,
            0x3FAF55, 0x2A0A0E, 0x050102, 0x8A1C22, 0xF0B429, Motif.BANNER);
    static final Skin NAMEK_SERENITY = new Skin("namek_serenity", "Namek Serenity", true, 0x0D201B, 0x57D9A3, 0xFFD166,
            0xE6F4EC, 0x3FAF55, 0x2B6E62, 0x041612, 0x3AA58A, 0xFFE9A8, Motif.NAMEK);
    static final Skin OBSIDIAN_GOLD = new Skin("obsidian_gold", "Obsidian & Gold", true, 0x17140F, 0xE8B33C, 0xE8B33C,
            0xF3EAD8, 0x4CAF6D, 0x16120D, 0x030202, 0xE8B33C, 0x6B4A12, Motif.VEINS);
    static final Skin BEERUS_DESTRUCTION = new Skin("beerus_destruction", "Beerus Destruction", true, 0x281840, 0xF0B429,
            0xE86A9A, 0xF3EAFF, 0x3FAF55, 0x2C1452, 0x08031A, 0xE86A9A, 0xF0B429, Motif.WISPS);
    static final Skin ZENO_OMNI = new Skin("zeno_omni", "Zeno Omni", false, 0xF6F9FF, 0x2563EB, 0xF59E0B, 0x1E2A4A,
            0x16A34A, 0xB9D6FF, 0xF4F8FF, 0xFFFFFF, 0xF59E0B, Motif.CLOUDS);
    static final List<Skin> ALL = List.of(SAIYAN_ROYAL, NAMEK_SERENITY, OBSIDIAN_GOLD, BEERUS_DESTRUCTION, ZENO_OMNI);
    static final List<String> IDS = List.of("saiyan_royal", "namek_serenity", "obsidian_gold", "beerus_destruction", "zeno_omni");

    static Skin byId(String id) {
        for (Skin s : ALL) if (s.id().equalsIgnoreCase(id == null ? "" : id.trim())) return s;
        return null;
    }

    /** A system's fixed accent (MenuSkins.systemAccentHex via SaiyanRoyal.bridgeAccent), a palette name, or -1. */
    static int accent(String name, Skin skin) {
        if (name == null || name.isBlank()) return skin.trim();
        String k = name.trim().toLowerCase(Locale.ROOT);
        if (k.matches("#[0-9a-f]{6}")) return Integer.parseInt(k.substring(1), 16);
        return switch (k) {
            case "difficulty", "crimson", "red" -> 0xE23B3B;
            case "rival", "white" -> 0xFFFFFF;
            case "spar", "sparring", "dojo", "dark_aqua", "teal" -> 0x00AAAA;
            case "prestige", "aqua", "cyan" -> 0x55FFFF;
            case "character", "green" -> 0x55FF55;
            case "skillcheck", "skills", "yellow" -> 0xFFFF55;
            case "admin", "config", "dark_red" -> 0xAA0000;
            case "gold" -> 0xF0B429;
            case "dark_green" -> 0x00AA00;
            case "purple", "dark_purple" -> 0xC058C0;
            case "light_purple", "pink" -> 0xFF55FF;
            case "blue" -> 0x5555FF;
            case "gray", "grey" -> 0xAAAAAA;
            case "none", "trim", "hub", "default" -> skin.trim();
            default -> -1;
        };
    }

    /** SaiyanRoyal/MenuSkins.systemAccentHex: the chrome accent of a system's colour letter, or -1. */
    static int letterAccent(char code) {
        return switch (Character.toLowerCase(code)) {
            case 'c' -> 0xE23B3B; case '6' -> 0xF0B429; case 'f' -> 0xFFFFFF; case '3' -> 0x00AAAA; case 'b' -> 0x55FFFF;
            case 'a' -> 0x55FF55; case 'e' -> 0xFFFF55; case '4' -> 0xAA0000; case '2' -> 0x00AA00; case '5' -> 0xAA00AA;
            case 'd' -> 0xFF55FF; case '9' -> 0x5555FF; case '7' -> 0xAAAAAA;
            default -> -1;
        };
    }

    static int vanilla(char code) {
        int i = "0123456789abcdef".indexOf(Character.toLowerCase(code));
        int[] v = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF,
                0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
        return i < 0 ? -1 : v[i];
    }

    /** MenuSkins.ink: c = danger crimson, 6 = trim, f = text, else vanilla; made readable on bg. */
    static int ink(Skin skin, char code, int bg) {
        char c = Character.toLowerCase(code);
        int raw = c == 'c' ? DANGER : c == '6' ? skin.trim() : c == 'f' ? skin.text() : vanilla(c);
        if (raw < 0) raw = skin.text();
        return readable(raw, bg, skin.dark() ? 3.0 : 4.5);
    }

    static int readable(int rgb, int bg, double min) {
        rgb &= 0xFFFFFF;
        if (contrast(rgb, bg) >= min) return rgb;
        int target = luminance(bg) < 0.4 ? WHITE : BLACK;
        for (int i = 1; i <= 20; i++) {
            int next = blend(rgb, target, i * 0.05);
            if (contrast(next, bg) >= min) return next;
        }
        int other = target == WHITE ? BLACK : WHITE;
        for (int i = 1; i <= 20; i++) {
            int next = blend(rgb, other, i * 0.05);
            if (contrast(next, bg) >= min) return next;
        }
        return target;
    }

    static double luminance(int rgb) {
        double[] v = new double[3];
        for (int i = 0; i < 3; i++) {
            double c = ((rgb >> (16 - 8 * i)) & 0xFF) / 255.0;
            v[i] = c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }

    static double contrast(int a, int b) {
        double la = luminance(a & 0xFFFFFF), lb = luminance(b & 0xFFFFFF);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    static int blend(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static int lerpArgb(int a, int b, double t) {
        double k = Math.max(0, Math.min(1, t));
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
            out |= ((int) Math.round(ca + (cb - ca) * k) & 0xFF) << shift;
        }
        return out;
    }

    static int ringAlpha(int alpha, int rings, int i) { return rings <= 0 ? 0 : alpha * (rings - i + 1) / rings; }

    static double cornerInset(double radius, double dy) {
        if (radius <= 0 || dy >= radius) return 0;
        double off = radius - Math.max(0, dy);
        return radius - Math.sqrt(Math.max(0, radius * radius - off * off));
    }

    static double radiusFor(double w, double h, double wanted) { return Math.max(0, Math.min(wanted, Math.floor(Math.min(w, h) / 2))); }

    // ------------------------------------------------------------------ MenuSkins.backgroundPixel
    static int background(Skin skin, double u, double v, boolean lite) {
        int base = blend(skin.bgTop(), skin.bgBottom(), Math.max(0, Math.min(1, v)));
        if (lite) return blend(base, BLACK, skin.backgroundDim());
        int out = base;
        switch (skin.motif()) {
            case BANNER -> {
                double banner = Math.abs(u - 0.18) < 0.07 && v < 0.62 - 0.05 * Math.sin(u * 40) ? 1 : 0;
                double edge = Math.abs(Math.abs(u - 0.18) - 0.07) < 0.006 && v < 0.62 ? 1 : 0;
                double hem = Math.abs(v - (0.62 - 0.05 * Math.sin(u * 40))) < 0.008 && Math.abs(u - 0.18) < 0.07 ? 1 : 0;
                out = blend(out, skin.motifA(), 0.55 * banner);
                out = blend(out, skin.motifB(), 0.45 * Math.max(edge, hem));
                out = blend(out, BLACK, 0.5 * smooth(0.78, 0.9, v));
                double ember = noise(u * 90, v * 60) > 0.985 && v > 0.55 ? 1 : 0;
                out = blend(out, skin.motifB(), 0.5 * ember);
            }
            case NAMEK -> {
                double sky = 1 - smooth(0.0, 0.45, v);
                out = blend(out, blend(skin.bgTop(), WHITE, 0.25), 0.35 * sky);
                double water = smooth(0.62, 0.7, v);
                double ripple = 0.5 + 0.5 * Math.sin(v * 220 + Math.sin(u * 18) * 2);
                out = blend(out, skin.motifA(), water * (0.25 + 0.15 * ripple));
                double ray = Math.max(0, Math.sin(Math.atan2(v + 0.2, 1.1 - u) * 22)) * (1 - v);
                out = blend(out, skin.motifB(), 0.12 * ray * ray);
            }
            case VEINS -> {
                double w = Math.sin(u * 14 + Math.sin(v * 9) * 1.8) + Math.sin(v * 11 + Math.cos(u * 7) * 2.2);
                out = blend(out, skin.motifA(), 0.55 * Math.max(0, 1 - Math.abs(w) * 9));
                out = blend(out, skin.motifB(), 0.6 * Math.max(0, 1 - Math.hypot(u - 0.5, v - 1.05) * 1.4));
            }
            case WISPS -> {
                out = blend(out, WHITE, 0.7 * (noise(u * 300, v * 170) > 0.992 ? 1 : 0));
                double p = Math.sin(u * 7 + Math.sin(v * 5) * 2.5 + 1.3) - (v - 0.5) * 2.2;
                double pink = Math.max(0, 1 - Math.abs(p) * 3.5);
                double g = Math.sin(u * 5.5 - Math.cos(v * 4) * 2 + 4.1) + (v - 0.45) * 2.6;
                double gold = Math.max(0, 1 - Math.abs(g) * 5);
                out = blend(out, skin.motifA(), 0.45 * pink * pink);
                out = blend(out, skin.motifB(), 0.35 * gold * gold);
            }
            case CLOUDS -> {
                double c = 0;
                for (int i = 0; i < 3; i++) {
                    double cy = 0.25 + i * 0.25;
                    double band = Math.exp(-Math.pow((v - cy - 0.04 * Math.sin(u * 9 + i)) * 9, 2));
                    double puff = 0.5 + 0.5 * Math.sin(u * (13 + i * 5) + i * 2);
                    c = Math.max(c, band * (0.5 + 0.5 * puff));
                }
                out = blend(out, skin.motifA(), 0.7 * c);
                double light = Math.max(0, 1 - Math.hypot(u - 0.5, v + 0.1) * 1.6);
                out = blend(out, skin.motifB(), 0.35 * light * light);
            }
        }
        return blend(out, BLACK, skin.backgroundDim());
    }

    private static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private static double noise(double x, double y) {
        long h = (long) Math.floor(x) * 374761393L + (long) Math.floor(y) * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }
}
