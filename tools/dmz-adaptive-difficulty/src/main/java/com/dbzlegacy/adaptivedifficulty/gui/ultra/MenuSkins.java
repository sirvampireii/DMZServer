package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * LM 4.7.62 menu skin registry: the five skins a player can pick (Main menu &gt; Menu skins) and every tone the
 * native renderer draws. Nothing outside this class holds a palette value: the LM client's
 * {@code NativeMenuScreen} and {@code NativeText} read faces, edges, insets, inks, shadows and backgrounds from
 * the player's {@link Skin} here.
 *
 * <ul>
 *   <li>A skin is the shared chrome (panel, primary, trim, text, confirm, background). Per-system accents are
 *       not per skin: Difficulty crimson, Rival white, Sparring dark aqua, Prestige aqua, Character green,
 *       SkillCheck yellow and Admin dark red look the same in every skin ({@link #systemAccentHex}).</li>
 *   <li>Each skin is dark or light. A light skin (Zeno Omni) draws dark text on a light panel; its inks are
 *       darkened until they read at 4.5:1 on their surface ({@link #readable}).</li>
 *   <li>KaiPath bridge: no panel tint and no background. Saiyan Royal keeps the 4.7.57 bytes exactly; another
 *       skin rewrites a few color letters at send time ({@link #bridgeRewrite}): its primary on menus without a
 *       system accent, and Zeno Omni's text letter on body lines.</li>
 * </ul>
 */
public final class MenuSkins {
    private MenuSkins() {}

    /** Background motif, drawn from the skin's own colors (native only, static, baked once). */
    public enum Motif {
        /** Dark battlefield: near-black red gradient with a crimson banner. */
        BANNER,
        /** Namek: deep green gradient, soft water and sky, calm light rays. */
        NAMEK,
        /** Black obsidian with gold veins and a warm glow. */
        VEINS,
        /** Deep violet cosmos with pink and gold destruction-energy wisps. */
        WISPS,
        /** Bright sky, soft blue and white with clouds and gold light. */
        CLOUDS
    }

    /** One skin's palette, exact hex from the UI Skin Concepts mock, plus its background and bridge letters. */
    public record Skin(
            String id,
            String name,
            boolean dark,
            int panel,
            int primary,
            int trim,
            int text,
            int confirm,
            int bgTop,
            int bgBottom,
            int motifA,
            int motifB,
            Motif motif,
            char bridgePrimary,
            char bridgeText) {

        /** Button and row face. */
        public int face() {
            return blend(panel, primary, dark ? 0.12 : 0.10);
        }

        public int faceHover() {
            return blend(panel, primary, dark ? 0.25 : 0.20);
        }

        /** Resting edge of a normal button in {@code tint} (the system accent). */
        public int edgeRest(int tint) {
            return blend(tint, face(), 0.4);
        }

        public int dimFace() {
            return dark ? blend(panel, GRAY_MID, 0.08) : blend(panel, BLACK, 0.05);
        }

        public int dimFaceHover() {
            return blend(dimFace(), text, 0.05);
        }

        public int dimEdge() {
            return dark ? blend(panel, WHITE, 0.14) : blend(panel, BLACK, 0.16);
        }

        /** Label ink on a dim or locked button. Still readable (3:1), never mistaken for live text. */
        public int muted() {
            return blend(text, panel, dark ? 0.52 : 0.42);
        }

        public int confirmFace() {
            return blend(confirm, panel, dark ? 0.62 : 0.78);
        }

        public int confirmFaceHover() {
            return blend(confirm, panel, dark ? 0.50 : 0.68);
        }

        public int dangerFace() {
            return blend(DANGER, panel, dark ? 0.60 : 0.80);
        }

        public int dangerFaceHover() {
            return blend(DANGER, panel, dark ? 0.48 : 0.70);
        }

        /** Info box behind body text. */
        public int infoFill() {
            return dark ? blend(panel, BLACK, 0.35) : blend(panel, BLACK, 0.04);
        }

        public int insetEdge() {
            return dark ? blend(panel, BLACK, 0.5) : blend(panel, BLACK, 0.15);
        }

        /** Alpha (0..255) of an inset box's inner shadow. */
        public int insetShadowAlpha() {
            return dark ? 0x80 : 0x30;
        }

        public int trackRim() {
            return blend(trim, panel, 0.7);
        }

        public int trackFill() {
            return dark ? blend(panel, BLACK, 0.75) : blend(panel, BLACK, 0.10);
        }

        public int dotEdge() {
            return blend(trim, panel, 0.4);
        }

        public int dotFill() {
            return dark ? blend(panel, BLACK, 0.4) : blend(panel, BLACK, 0.08);
        }

        public int fieldEdge() {
            return blend(trim, panel, 0.65);
        }

        public int fieldFill() {
            return dark ? blend(panel, BLACK, 0.45) : blend(panel, WHITE, 0.5);
        }

        /** Text typed into a field. */
        public int fieldText() {
            return text;
        }

        public int bookFill() {
            return dark ? blend(panel, primary, 0.045) : blend(panel, BLACK, 0.03);
        }

        public int panelTop() {
            return blend(panel, WHITE, dark ? 0.07 : 0.5);
        }

        public int panelBottom() {
            return blend(panel, BLACK, dark ? 0.25 : 0.04);
        }

        /** Shadow strength: full on dark skins, softer on a light one. */
        public int shadowAlpha(int alpha) {
            return dark ? alpha : alpha * 55 / 100;
        }

        /** ARGB top-edge sheen of a raised button. */
        public int edgeHighlight() {
            return dark ? 0x40FFFFFF : 0x80FFFFFF;
        }

        /** ARGB bottom-edge shade of a raised button. */
        public int edgeShade() {
            return dark ? 0x50000000 : 0x26000000;
        }

        /** Dark skins drop-shadow their labels; a light skin does not (a shadow under dark text smears). */
        public boolean textShadow() {
            return dark;
        }

        /** Skin-tinted glow on the active tab and on primary (confirm) buttons. */
        public int glow() {
            return primary;
        }

        /** Focus ring around the control the keyboard is on. */
        public int focusRing() {
            return dark ? blend(trim, WHITE, 0.25) : primary;
        }

        /** ARGB over the game world when backgrounds are off (the 4.7.60 dim). */
        public int scrim() {
            return dark ? 0x90000000 : 0x70000000;
        }

        /** Title, divider, border and header ink. */
        public int title() {
            return trim;
        }

        /** Default chrome accent on menus without a system accent (the main menu, Menu skins). */
        public int defaultAccent() {
            return trim;
        }

        /** Inner ring of a framed page in {@code accent}. */
        public int frameRing(int accent) {
            return blend(accent, panel, 0.6);
        }

        /** Scroll track (ARGB) and its glossy thumb color (RGB). */
        public int scrollTrack() {
            return dark ? 0x60000000 : 0x30000000;
        }

        public int scrollTrackEdge() {
            return dark ? 0x40FFFFFF : 0x40000000;
        }

        /** Fps notice box (ARGB) and its ink. */
        public int noticeFill() {
            return 0xE0000000 | blend(panel, dark ? BLACK : WHITE, 0.3);
        }

        /** Background as drawn behind the panel: the bake is dimmed so the panel reads first. */
        public double backgroundDim() {
            return dark ? 0.15 : 0.08;
        }
    }

    public static final int OPAQUE = 0xFF000000;
    /** RGB part of an ARGB value. */
    public static final int RGB_MASK = 0xFFFFFF;
    /** Alpha of the title divider. */
    public static final int DIVIDER_ALPHA = 0xC0;
    public static final int WHITE = 0xFFFFFF;
    public static final int BLACK = 0x000000;
    public static final int GRAY_MID = 0x808080;
    /** Danger red (danger-style buttons and edges): the same crimson in every skin. */
    public static final int DANGER = 0xE23B3B;
    /** "Use the component's own colors" ink for GuiGraphics.drawString. */
    public static final int INK_FROM_TEXT = 0xFFFFFF;
    /** Placeholder fill while a player model cannot be drawn yet. */
    public static final int MODEL_PLACEHOLDER = 0x33000000;
    /** Glossy bar highlight line (ARGB). */
    public static final int GLOSS_LINE = 0x60FFFFFF;
    /** Shimmer band over an active Momentum fill (ARGB). */
    public static final int SHIMMER = 0x38FFFFFF;
    /** KaiPath's bridge panel, sampled from a 4.7.60 KaiPath screenshot (DMZUltra panel tile). */
    public static final int BRIDGE_PANEL = 0x191516;

    public static final Skin SAIYAN_ROYAL = new Skin("saiyan_royal", "Saiyan Royal", true,
            0x160B0D, 0xE23B3B, 0xF0B429, 0xF6E8E4, 0x3FAF55,
            0x2A0A0E, 0x050102, 0x8A1C22, 0xF0B429, Motif.BANNER, 'c', 'f');
    public static final Skin NAMEK_SERENITY = new Skin("namek_serenity", "Namek Serenity", true,
            0x0D201B, 0x57D9A3, 0xFFD166, 0xE6F4EC, 0x3FAF55,
            0x2B6E62, 0x041612, 0x3AA58A, 0xFFE9A8, Motif.NAMEK, 'a', 'f');
    /** No distinct trim in the mock: trim is the same gold. Confirm is the mock's green #4caf6d. */
    public static final Skin OBSIDIAN_GOLD = new Skin("obsidian_gold", "Obsidian & Gold", true,
            0x17140F, 0xE8B33C, 0xE8B33C, 0xF3EAD8, 0x4CAF6D,
            0x16120D, 0x030202, 0xE8B33C, 0x6B4A12, Motif.VEINS, '6', 'f');
    /** Primary is the gold, trim the mock's pink accent. */
    public static final Skin BEERUS_DESTRUCTION = new Skin("beerus_destruction", "Beerus Destruction", true,
            0x281840, 0xF0B429, 0xE86A9A, 0xF3EAFF, 0x3FAF55,
            0x2C1452, 0x08031A, 0xE86A9A, 0xF0B429, Motif.WISPS, '6', 'f');
    /**
     * The light skin. Bridge text: KaiPath's panel is dark, so Zeno's navy text maps to the readable letter
     * nearest its hue: dark aqua (6.3:1 on the bridge panel; dark blue and blue fail 4.5:1).
     */
    public static final Skin ZENO_OMNI = new Skin("zeno_omni", "Zeno Omni", false,
            0xF6F9FF, 0x2563EB, 0xF59E0B, 0x1E2A4A, 0x16A34A,
            0xB9D6FF, 0xF4F8FF, 0xFFFFFF, 0xF59E0B, Motif.CLOUDS, '9', '3');

    /** Picker order. Saiyan Royal is the default for every player. */
    public static final List<Skin> ALL = List.of(SAIYAN_ROYAL, NAMEK_SERENITY, OBSIDIAN_GOLD, BEERUS_DESTRUCTION,
            ZENO_OMNI);
    public static final Skin DEFAULT = SAIYAN_ROYAL;

    /** Skin by id; unknown or blank ids get the default (never a blank menu). */
    public static Skin byId(String id) {
        if (id != null) {
            String k = id.trim().toLowerCase(Locale.ROOT);
            for (Skin s : ALL) {
                if (s.id().equals(k)) {
                    return s;
                }
            }
        }
        return DEFAULT;
    }

    public static boolean known(String id) {
        if (id == null) {
            return false;
        }
        String k = id.trim().toLowerCase(Locale.ROOT);
        for (Skin s : ALL) {
            if (s.id().equals(k)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ system accents and named colors

    /**
     * Exact hex of a system accent letter, the same in every skin: Difficulty's red is the crimson, the gold
     * letter is the gold trim of Saiyan Royal; Rival white, Sparring dark aqua, Prestige aqua, Character green,
     * SkillCheck yellow and Admin dark red keep their Minecraft hex. -1 for no accent.
     */
    public static int systemAccentHex(char code) {
        return switch (Character.toLowerCase(code)) {
            case 'c' -> 0xE23B3B;
            case '6' -> 0xF0B429;
            case 'f' -> 0xFFFFFF;
            case '3' -> 0x00AAAA;
            case 'b' -> 0x55FFFF;
            case 'a' -> 0x55FF55;
            case 'e' -> 0xFFFF55;
            case '4' -> 0xAA0000;
            case '2' -> 0x00AA00;
            case '5' -> 0xAA00AA;
            case 'd' -> 0xFF55FF;
            case '9' -> 0x5555FF;
            case '7' -> 0xAAAAAA;
            default -> -1;
        };
    }

    /** Named palette colors (layout and button {@code palette}). Gold names take the skin's trim. */
    public static int paletteRgb(String name, Skin skin, int fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "red" -> DANGER;
            case "dark_red" -> 0xAA0000;
            case "purple", "dark_purple" -> 0xC058C0;
            case "light_purple", "pink" -> 0xFF55FF;
            case "gold", "orange" -> skin.trim();
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

    /** Vanilla color of a code letter, -1 for none. */
    public static int vanillaRgb(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> 0x000000;
            case '1' -> 0x0000AA;
            case '2' -> 0x00AA00;
            case '3' -> 0x00AAAA;
            case '4' -> 0xAA0000;
            case '5' -> 0xAA00AA;
            case '6' -> 0xFFAA00;
            case '7' -> 0xAAAAAA;
            case '8' -> 0x555555;
            case '9' -> 0x5555FF;
            case 'a' -> 0x55FF55;
            case 'b' -> 0x55FFFF;
            case 'c' -> 0xFF5555;
            case 'd' -> 0xFF55FF;
            case 'e' -> 0xFFFF55;
            case 'f' -> 0xFFFFFF;
            default -> -1;
        };
    }

    /**
     * Ink for a code letter in {@code skin}: the Difficulty crimson for its accent letter, the trim for the header
     * letter, the skin text for the value letter, the vanilla color otherwise; then made readable on {@code bg}
     * (3:1 on a dark skin, which leaves the 4.7.60 Saiyan Royal inks as they were; 4.5:1 on a light skin).
     */
    public static int ink(Skin skin, char code, int bg, int fallback) {
        char c = Character.toLowerCase(code);
        int raw;
        if (c == SaiyanRoyal.BRIDGE_ACCENT) {
            raw = DANGER;
        } else if (c == SaiyanRoyal.BRIDGE_HEADER) {
            raw = skin.trim();
        } else if (c == SaiyanRoyal.BRIDGE_VALUE) {
            raw = skin.text();
        } else {
            int v = vanillaRgb(c);
            raw = v >= 0 ? v : fallback;
        }
        return readable(raw, bg, skin.dark() ? 3.0 : 4.5);
    }

    /** {@code rgb} pushed toward white (dark surface) or black (light surface) until it reads at {@code min}. */
    public static int readable(int rgb, int bg, double min) {
        if (contrast(rgb, bg) >= min) {
            return rgb & 0xFFFFFF;
        }
        int target = luminance(bg) < 0.4 ? WHITE : BLACK;
        for (int i = 1; i <= 20; i++) {
            int next = blend(rgb, target, i * 0.05);
            if (contrast(next, bg) >= min) {
                return next;
            }
        }
        return target;
    }

    /** WCAG relative luminance (0..1). */
    public static double luminance(int rgb) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = ((rgb >> (16 - 8 * i)) & 0xFF) / 255.0;
            c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    /** WCAG contrast ratio of two RGB colors. */
    public static double contrast(int a, int b) {
        double la = luminance(a & 0xFFFFFF);
        double lb = luminance(b & 0xFFFFFF);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** Mix {@code a} toward {@code b} by {@code t} (0..1), RGB only. */
    public static int blend(int a, int b, double t) {
        return SaiyanRoyal.blend(a, b, t);
    }

    // ------------------------------------------------------------------ background (native only)

    /**
     * Background pixel (RGB) at {@code (u, v)} in 0..1 for {@code skin}: the base gradient plus the skin's
     * motif, or the base gradient alone in Lite. Deterministic and cheap enough to bake once per skin.
     */
    public static int backgroundPixel(Skin skin, double u, double v, boolean lite) {
        int base = blend(skin.bgTop(), skin.bgBottom(), Math.max(0, Math.min(1, v)));
        if (lite) {
            return blend(base, BLACK, skin.backgroundDim());
        }
        int out = base;
        switch (skin.motif()) {
            case BANNER -> {
                // A tall crimson banner on the left third with a gold hem, a dark battlefield horizon below.
                double banner = Math.abs(u - 0.18) < 0.07 && v < 0.62 - 0.05 * Math.sin(u * 40) ? 1 : 0;
                double edge = Math.abs(Math.abs(u - 0.18) - 0.07) < 0.006 && v < 0.62 ? 1 : 0;
                double hem = Math.abs(v - (0.62 - 0.05 * Math.sin(u * 40))) < 0.008 && Math.abs(u - 0.18) < 0.07 ? 1 : 0;
                out = blend(out, skin.motifA(), 0.55 * banner);
                out = blend(out, skin.motifB(), 0.45 * Math.max(edge, hem));
                double horizon = smooth(0.78, 0.9, v);
                out = blend(out, BLACK, 0.5 * horizon);
                double ember = noise(u * 90, v * 60) > 0.985 && v > 0.55 ? 1 : 0;
                out = blend(out, skin.motifB(), 0.5 * ember);
            }
            case NAMEK -> {
                // Sky band at the top, calm water at the bottom, soft rays from the upper right.
                double sky = 1 - smooth(0.0, 0.45, v);
                out = blend(out, blend(skin.bgTop(), WHITE, 0.25), 0.35 * sky);
                double water = smooth(0.62, 0.7, v);
                double ripple = 0.5 + 0.5 * Math.sin(v * 220 + Math.sin(u * 18) * 2);
                out = blend(out, skin.motifA(), water * (0.25 + 0.15 * ripple));
                double ray = Math.max(0, Math.sin(Math.atan2(v + 0.2, 1.1 - u) * 22)) * (1 - v);
                out = blend(out, skin.motifB(), 0.12 * ray * ray);
            }
            case VEINS -> {
                // Gold veins (thin ridges of a warped wave field) and a warm glow from below.
                double w = Math.sin(u * 14 + Math.sin(v * 9) * 1.8) + Math.sin(v * 11 + Math.cos(u * 7) * 2.2);
                double vein = Math.max(0, 1 - Math.abs(w) * 9);
                out = blend(out, skin.motifA(), 0.55 * vein);
                double glow = Math.max(0, 1 - Math.hypot(u - 0.5, v - 1.05) * 1.4);
                out = blend(out, skin.motifB(), 0.6 * glow);
            }
            case WISPS -> {
                // Violet cosmos: stars, then pink and gold energy wisps curling across.
                double star = noise(u * 300, v * 170) > 0.992 ? 1 : 0;
                out = blend(out, WHITE, 0.7 * star);
                double p = Math.sin(u * 7 + Math.sin(v * 5) * 2.5 + 1.3) - (v - 0.5) * 2.2;
                double pink = Math.max(0, 1 - Math.abs(p) * 3.5);
                double g = Math.sin(u * 5.5 - Math.cos(v * 4) * 2 + 4.1) + (v - 0.45) * 2.6;
                double gold = Math.max(0, 1 - Math.abs(g) * 5);
                out = blend(out, skin.motifA(), 0.45 * pink * pink);
                out = blend(out, skin.motifB(), 0.35 * gold * gold);
            }
            case CLOUDS -> {
                // Soft white clouds in three bands and a gold light from the top.
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
            default -> {
            }
        }
        return blend(out, BLACK, skin.backgroundDim());
    }

    private static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Hash noise in 0..1 (deterministic, so a bake is the same every time). */
    private static double noise(double x, double y) {
        long h = (long) Math.floor(x) * 374761393L + (long) Math.floor(y) * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    // ------------------------------------------------------------------ KaiPath bridge

    /**
     * Rewrite a bridge packet's JSON for {@code skin}. Saiyan Royal returns {@code null} (send the packet as
     * built, the 4.7.57 bytes). Another skin changes only color letters the spec already carries: a menu with
     * no system accent takes the skin's primary letter as its layout accent, and a skin whose bridge text
     * letter is not white (Zeno Omni) puts it on body lines in place of the value letter. Anything it cannot
     * read comes back unchanged, never blank.
     *
     * @return {layoutJson, linesJson}, or null for no change
     */
    public static String[] bridgeRewrite(Skin skin, String layoutJson, String linesJson) {
        if (skin == null || skin == SAIYAN_ROYAL) {
            return null;
        }
        String layoutOut = layoutJson;
        String linesOut = linesJson;
        try {
            JsonElement le = layoutJson == null || layoutJson.isBlank() ? null : JsonParser.parseString(layoutJson);
            if (le != null && le.isJsonObject()) {
                JsonObject layout = le.getAsJsonObject();
                String acc = layout.has("accent") && !layout.get("accent").isJsonNull()
                        ? layout.get("accent").getAsString() : "";
                if (acc.isBlank()) {
                    layout.addProperty("accent", String.valueOf(skin.bridgePrimary()));
                    layoutOut = layout.toString();
                }
            }
        } catch (RuntimeException ignored) {
            layoutOut = layoutJson;
        }
        if (skin.bridgeText() != SaiyanRoyal.BRIDGE_VALUE) {
            try {
                JsonElement ae = linesJson == null || linesJson.isBlank() ? null : JsonParser.parseString(linesJson);
                if (ae != null && ae.isJsonArray()) {
                    String from = UltraStyle.MARK + SaiyanRoyal.BRIDGE_VALUE;
                    String to = UltraStyle.MARK + skin.bridgeText();
                    JsonArray out = new JsonArray();
                    for (JsonElement el : ae.getAsJsonArray()) {
                        out.add(el.isJsonPrimitive() ? el.getAsString().replace(from, to) : el.getAsString());
                    }
                    linesOut = out.toString();
                }
            } catch (RuntimeException ignored) {
                linesOut = linesJson;
            }
        }
        return new String[] {layoutOut, linesOut};
    }

    // ------------------------------------------------------------------ self-test

    /** Pins the registry. Update with the 4.7.62 ship-manifest check if a value moves on purpose. */
    public static List<String> selfTest() {
        List<String> bad = new ArrayList<>();
        if (ALL.size() != 5) {
            bad.add("registry must hold exactly the five skins, has " + ALL.size());
        }
        if (SAIYAN_ROYAL.panel() != SaiyanRoyal.PANEL || SAIYAN_ROYAL.primary() != SaiyanRoyal.CRIMSON
                || SAIYAN_ROYAL.trim() != SaiyanRoyal.GOLD_TRIM || SAIYAN_ROYAL.text() != SaiyanRoyal.TEXT) {
            bad.add("Saiyan Royal: panel #160b0d, crimson #e23b3b, gold trim #f0b429, text #f6e8e4");
        }
        if (NAMEK_SERENITY.panel() != 0x0D201B || NAMEK_SERENITY.primary() != 0x57D9A3
                || NAMEK_SERENITY.trim() != 0xFFD166 || NAMEK_SERENITY.text() != 0xE6F4EC) {
            bad.add("Namek Serenity: panel #0d201b, jade #57d9a3, sun #ffd166, text #e6f4ec");
        }
        if (OBSIDIAN_GOLD.panel() != 0x17140F || OBSIDIAN_GOLD.primary() != 0xE8B33C
                || OBSIDIAN_GOLD.trim() != 0xE8B33C || OBSIDIAN_GOLD.text() != 0xF3EAD8
                || OBSIDIAN_GOLD.confirm() != 0x4CAF6D) {
            bad.add("Obsidian & Gold: panel #17140f, gold #e8b33c (trim too), text #f3ead8, confirm #4caf6d");
        }
        if (BEERUS_DESTRUCTION.panel() != 0x281840 || BEERUS_DESTRUCTION.primary() != 0xF0B429
                || BEERUS_DESTRUCTION.trim() != 0xE86A9A || BEERUS_DESTRUCTION.text() != 0xF3EAFF) {
            bad.add("Beerus Destruction: panel #281840, gold #f0b429, pink #e86a9a, text #f3eaff");
        }
        if (ZENO_OMNI.panel() != 0xF6F9FF || ZENO_OMNI.primary() != 0x2563EB || ZENO_OMNI.trim() != 0xF59E0B
                || ZENO_OMNI.text() != 0x1E2A4A || ZENO_OMNI.dark()) {
            bad.add("Zeno Omni: light skin, panel #f6f9ff, royal blue #2563eb, gold #f59e0b, text #1e2a4a");
        }
        for (Skin s : ALL) {
            if (s.dark() != (luminance(s.panel()) < 0.4)) {
                bad.add(s.id() + ": dark flag disagrees with its panel");
            }
            if (contrast(s.text(), s.panel()) < 7 || contrast(s.text(), s.face()) < 4.5) {
                bad.add(s.id() + ": text must read on the panel (7:1) and on buttons (4.5:1)");
            }
            if (contrast(s.muted(), s.dimFace()) < 3) {
                bad.add(s.id() + ": dim labels must still read (3:1)");
            }
            if (contrast(s.text(), s.confirmFace()) < 4.5 || contrast(s.text(), s.dangerFace()) < 4.5) {
                bad.add(s.id() + ": confirm and danger labels must read (4.5:1)");
            }
            for (char c : "0123456789abcdef".toCharArray()) {
                int bg = s.infoFill();
                if (contrast(ink(s, c, bg, s.text()), bg) < (s.dark() ? 3.0 : 4.5)) {
                    bad.add(s.id() + ": ink " + c + " unreadable on the info box");
                }
            }
            if (contrast(systemAccentHex(s.bridgePrimary()), BRIDGE_PANEL) < 3) {
                bad.add(s.id() + ": bridge primary letter unreadable on the KaiPath panel");
            }
            if (contrast(vanillaRgb(s.bridgeText()), BRIDGE_PANEL) < 4.5) {
                bad.add(s.id() + ": bridge text letter unreadable on the KaiPath panel");
            }
            if (byId(s.id()) != s || !known(s.id())) {
                bad.add(s.id() + ": id lookup");
            }
        }
        if (byId("nope") != DEFAULT || byId(null) != SAIYAN_ROYAL || known("nope")) {
            bad.add("unknown skin ids fall back to Saiyan Royal");
        }
        if (SAIYAN_ROYAL.bridgePrimary() != SaiyanRoyal.BRIDGE_ACCENT || SAIYAN_ROYAL.bridgeText() != 'f'
                || NAMEK_SERENITY.bridgePrimary() != 'a' || OBSIDIAN_GOLD.bridgePrimary() != '6'
                || BEERUS_DESTRUCTION.bridgePrimary() != '6' || ZENO_OMNI.bridgePrimary() != '9'
                || ZENO_OMNI.bridgeText() != '3') {
            bad.add("bridge letters: primary c/a/6/6/9, text f except Zeno Omni dark aqua");
        }
        if (bridgeRewrite(SAIYAN_ROYAL, "{\"accent\":\"\"}", "[\"x\"]") != null) {
            bad.add("Saiyan Royal must keep the 4.7.57 bridge bytes (no rewrite)");
        }
        String v = UltraStyle.MARK + "f";
        String[] z = bridgeRewrite(ZENO_OMNI, "{\"accent\":\"\"}", "[\"" + v + "hi\"]");
        if (z == null || !z[0].contains("\"accent\":\"9\"") || !z[1].contains(UltraStyle.MARK + "3hi")) {
            bad.add("Zeno Omni bridge: primary accent on accent-less menus, dark aqua body text");
        }
        String[] n = bridgeRewrite(NAMEK_SERENITY, "{\"accent\":\"c\"}", "[\"" + v + "hi\"]");
        if (n == null || !n[0].contains("\"accent\":\"c\"") || !n[1].contains(v + "hi")) {
            bad.add("a system accent and white text stay as sent");
        }
        String[] broken = bridgeRewrite(ZENO_OMNI, "{not json", "[oops");
        if (broken == null || !"{not json".equals(broken[0]) || !"[oops".equals(broken[1])) {
            bad.add("an unreadable spec goes out unchanged, never blank");
        }
        if (systemAccentHex('c') != SaiyanRoyal.CRIMSON || systemAccentHex('3') != 0x00AAAA || systemAccentHex('x') != -1) {
            bad.add("system accents are the same in every skin");
        }
        for (Skin s : ALL) {
            // Lite flattens the motif: somewhere on a coarse grid the two bakes must differ.
            boolean differs = false;
            for (int i = 0; i <= 20 && !differs; i++) {
                for (int j = 0; j <= 20 && !differs; j++) {
                    differs = backgroundPixel(s, i / 20.0, j / 20.0, false) != backgroundPixel(s, i / 20.0, j / 20.0, true);
                }
            }
            if (!differs) {
                bad.add(s.id() + ": Lite background should drop the motif");
            }
        }
        return bad;
    }
}
