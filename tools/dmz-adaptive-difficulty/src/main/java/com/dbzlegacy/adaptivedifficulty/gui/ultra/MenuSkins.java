package com.dbzlegacy.adaptivedifficulty.gui.ultra;

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
 *   <li>LM 4.8.2: the KaiPath bridge is gone, and with it the per-skin bridge letters and the send-time letter
 *       rewrite ({@code bridgeRewrite}). Skins exist only in the LM client's native renderer.</li>
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

    /** One skin's palette, exact hex from the UI Skin Concepts mock, plus its background. */
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
            Motif motif) {

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
            // 4.7.81 (GuiKit): light skins blend less (0.30), so inactive tabs read 4.5:1 on Zeno Omni.
            return blend(text, panel, dark ? 0.52 : 0.30);
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
    /** 4.7.68: the dark band behind a picture's caption (dojo upgrade previews). */
    public static final int PICTURE_CAPTION_FILL = 0xB0000000;
    /** Glossy bar highlight line (ARGB). */
    public static final int GLOSS_LINE = 0x60FFFFFF;
    /** Shimmer band over an active Momentum fill (ARGB). */
    public static final int SHIMMER = 0x38FFFFFF;
    public static final Skin SAIYAN_ROYAL = new Skin("saiyan_royal", "Saiyan Royal", true,
            0x160B0D, 0xE23B3B, 0xF0B429, 0xF6E8E4, 0x3FAF55,
            0x2A0A0E, 0x050102, 0x8A1C22, 0xF0B429, Motif.BANNER);
    public static final Skin NAMEK_SERENITY = new Skin("namek_serenity", "Namek Serenity", true,
            0x0D201B, 0x57D9A3, 0xFFD166, 0xE6F4EC, 0x3FAF55,
            0x2B6E62, 0x041612, 0x3AA58A, 0xFFE9A8, Motif.NAMEK);
    /** No distinct trim in the mock: trim is the same gold. Confirm is the mock's green #4caf6d. */
    public static final Skin OBSIDIAN_GOLD = new Skin("obsidian_gold", "Obsidian & Gold", true,
            0x17140F, 0xE8B33C, 0xE8B33C, 0xF3EAD8, 0x4CAF6D,
            0x16120D, 0x030202, 0xE8B33C, 0x6B4A12, Motif.VEINS);
    /** Primary is the gold, trim the mock's pink accent. */
    public static final Skin BEERUS_DESTRUCTION = new Skin("beerus_destruction", "Beerus Destruction", true,
            0x281840, 0xF0B429, 0xE86A9A, 0xF3EAFF, 0x3FAF55,
            0x2C1452, 0x08031A, 0xE86A9A, 0xF0B429, Motif.WISPS);
    /** The light skin. */
    public static final Skin ZENO_OMNI = new Skin("zeno_omni", "Zeno Omni", false,
            0xF6F9FF, 0x2563EB, 0xF59E0B, 0x1E2A4A, 0x16A34A,
            0xB9D6FF, 0xF4F8FF, 0xFFFFFF, 0xF59E0B, Motif.CLOUDS);

    /**
     * 4.7.78: the skin's gold, for currency (the HUD coin toast): of the skin's trim and primary, the one whose hue is
     * nearest gold. Saiyan Royal, Namek, Obsidian and Zeno keep gold in the trim; Beerus keeps it in the primary (its
     * trim is pink); a custom palette gets whichever of its two accents reads as gold.
     */
    public static int currencyGold(Skin skin) {
        Skin s = skin == null ? DEFAULT : skin;
        return goldDistance(s.trim()) <= goldDistance(s.primary()) ? s.trim() : s.primary();
    }

    /** Hue distance (degrees) from gold (about 40), with low-saturation colours pushed away. */
    private static double goldDistance(int rgb) {
        float[] hsv = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        double hue = hsv[0] * 360.0;
        double d = Math.abs(hue - 40.0);
        d = Math.min(d, 360.0 - d);
        return d + (hsv[1] < 0.35f ? 180.0 : 0.0);
    }

    /** Picker order. Saiyan Royal is the default for every player. */
    public static final List<Skin> ALL = List.of(SAIYAN_ROYAL, NAMEK_SERENITY, OBSIDIAN_GOLD, BEERUS_DESTRUCTION,
            ZENO_OMNI);
    public static final Skin DEFAULT = SAIYAN_ROYAL;

    /** Skin by id; unknown or blank ids get the default (never a blank menu). */
    public static Skin byId(String id) {
        if (id != null) {
            String k = id.trim().toLowerCase(Locale.ROOT);
            if (isCustom(k)) {
                // 4.7.66: a custom token is the palette itself; "custom" alone is the Saiyan Royal starting tones.
                Custom c = parseCustom(k);
                return c == null ? DEFAULT : custom(c);
            }
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
        if (isCustom(k)) {
            // 4.7.66: "custom" alone (the stored choice) or a full custom token.
            return CUSTOM_ID.equals(k) || parseCustom(k) != null;
        }
        for (Skin s : ALL) {
            if (s.id().equals(k)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 4.7.66 custom skin

    /** Stored skin id of the player's own palette (Menu skins &gt; Custom). Never in {@link #ALL}. */
    public static final String CUSTOM_ID = "custom";
    public static final String CUSTOM_NAME = "Custom";

    /**
     * A player's own palette: the five base tones, dark or light, and a motif. Every other tone of the skin
     * (faces, muted, fills, glow, background) is derived from these by {@link #custom(Custom)}.
     */
    public record Custom(boolean dark, Motif motif, int panel, int primary, int trim, int text, int confirm) {
        /** Saiyan Royal's tones: what a player gets who picks Custom without ever editing it. */
        public static final Custom DEFAULT = from(SAIYAN_ROYAL);

        public static Custom from(Skin s) {
            return new Custom(s.dark(), s.motif(), s.panel(), s.primary(), s.trim(), s.text(), s.confirm());
        }

        /** Base tones in editor order: panel, primary, trim, text, confirm. */
        public int[] tones() {
            return new int[] {panel, primary, trim, text, confirm};
        }

        public Custom withTones(int[] t) {
            return new Custom(dark, motif, t[0] & RGB_MASK, t[1] & RGB_MASK, t[2] & RGB_MASK, t[3] & RGB_MASK,
                    t[4] & RGB_MASK);
        }
    }

    /** True for {@code custom} and for a {@code custom.} token. */
    public static boolean isCustom(String id) {
        if (id == null) {
            return false;
        }
        String k = id.trim().toLowerCase(Locale.ROOT);
        return CUSTOM_ID.equals(k) || k.startsWith(CUSTOM_ID + ".");
    }

    /**
     * Wire form of a custom palette, used as the custom skin's id: {@code custom.d.banner.160b0d.e23b3b.f0b429.f6e8e4.3faf55}
     * (dark {@code d} or light {@code l}, motif, then panel, primary, trim, text, confirm). At most 50 characters, so
     * it fits message 3's skin string (64) and an action id; an older LM client that does not know it draws Saiyan
     * Royal ({@link #byId}).
     */
    public static String customToken(Custom c) {
        Custom x = c == null ? Custom.DEFAULT : c;
        StringBuilder sb = new StringBuilder(CUSTOM_ID).append('.').append(x.dark() ? 'd' : 'l').append('.')
                .append(x.motif().name().toLowerCase(Locale.ROOT));
        for (int t : x.tones()) {
            sb.append('.').append(String.format(Locale.ROOT, "%06x", t & RGB_MASK));
        }
        return sb.toString();
    }

    /** The palette in a token; {@link Custom#DEFAULT} for {@code custom} alone; null for anything else. */
    public static Custom parseCustom(String token) {
        if (token == null) {
            return null;
        }
        String k = token.trim().toLowerCase(Locale.ROOT);
        if (CUSTOM_ID.equals(k)) {
            return Custom.DEFAULT;
        }
        String[] p = k.split("\\.", -1);
        if (p.length != 8 || !CUSTOM_ID.equals(p[0]) || !("d".equals(p[1]) || "l".equals(p[1]))) {
            return null;
        }
        Motif motif = motifByName(p[2]);
        if (motif == null) {
            return null;
        }
        int[] t = new int[5];
        for (int i = 0; i < 5; i++) {
            t[i] = parseHex(p[3 + i]);
            if (t[i] < 0) {
                return null;
            }
        }
        return new Custom("d".equals(p[1]), motif, t[0], t[1], t[2], t[3], t[4]);
    }

    public static Motif motifByName(String name) {
        if (name == null) {
            return null;
        }
        for (Motif m : Motif.values()) {
            if (m.name().equalsIgnoreCase(name.trim())) {
                return m;
            }
        }
        return null;
    }

    /** {@code #RRGGBB} or {@code RRGGBB}, any case, spaces around ignored. -1 when it is not 6 hex digits. */
    public static int parseHex(String s) {
        if (s == null) {
            return -1;
        }
        String v = s.trim();
        if (v.startsWith("#")) {
            v = v.substring(1);
        }
        if (v.length() != 6) {
            return -1;
        }
        for (int i = 0; i < 6; i++) {
            if (Character.digit(v.charAt(i), 16) < 0) {
                return -1;
            }
        }
        return Integer.parseInt(v, 16);
    }

    /** {@code #rrggbb}. */
    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06x", rgb & RGB_MASK);
    }

    /** Contrast target for body text: 3:1 on a dark skin, 4.5:1 on a light one. */
    public static double textTarget(boolean dark) {
        return dark ? 3.0 : 4.5;
    }

    /**
     * The custom text ink as drawn: the chosen text run through {@link #readable} on the button face and the
     * info box, then on the panel last, so text on the panel always reads (3:1 dark, 4.5:1 light).
     */
    public static int customText(Custom c) {
        double min = textTarget(c.dark());
        int face = blend(c.panel(), c.primary(), c.dark() ? 0.12 : 0.10);
        int info = c.dark() ? blend(c.panel(), BLACK, 0.35) : blend(c.panel(), BLACK, 0.04);
        int t = c.text() & RGB_MASK;
        t = readable(t, face, min);
        t = readable(t, info, min);
        return readable(t, c.panel(), min);
    }

    /** True when the chosen text color had to change to stay readable (the editor says so). */
    public static boolean customTextAdjusted(Custom c) {
        return c != null && customText(c) != (c.text() & RGB_MASK);
    }

    /**
     * The custom skin: the five base tones (text made readable), the background derived the way the presets
     * darken theirs (a dark skin: panel tinted 10% toward primary on top, panel 75% toward black at the bottom,
     * motif in primary darkened 40% and trim; a light skin: panel 30% toward primary on top, panel 30% toward
     * white below, motif in near-white and trim).
     */
    public static Skin custom(Custom c) {
        Custom x = c == null ? Custom.DEFAULT : c;
        int text = customText(x);
        int bgTop;
        int bgBottom;
        int motifA;
        if (x.dark()) {
            bgTop = blend(x.panel(), x.primary(), 0.10);
            bgBottom = blend(x.panel(), BLACK, 0.75);
            motifA = blend(x.primary(), BLACK, 0.40);
        } else {
            bgTop = blend(x.panel(), x.primary(), 0.30);
            bgBottom = blend(x.panel(), WHITE, 0.30);
            motifA = blend(x.panel(), WHITE, 0.80);
        }
        return new Skin(customToken(x), CUSTOM_NAME, x.dark(), x.panel(), x.primary(), x.trim(), text, x.confirm(),
                bgTop, bgBottom, motifA, x.trim(), x.motif());
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
        // 4.7.66: a mid-tone surface (a custom panel) can defeat the first pole; try the other one. Every preset
        // ink reads before this point, so their colors are unchanged.
        int other = target == WHITE ? BLACK : WHITE;
        for (int i = 1; i <= 20; i++) {
            int next = blend(rgb, other, i * 0.05);
            if (contrast(next, bg) >= min) {
                return next;
            }
        }
        return contrast(WHITE, bg) >= contrast(BLACK, bg) ? WHITE : BLACK;
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

    // ------------------------------------------------------------------ self-test

    /** Pins the registry. Update with the 4.7.62 ship-manifest check if a value moves on purpose. */
    public static List<String> selfTest() {
        List<String> bad = new ArrayList<>();
        if (ALL.size() != 5) {
            bad.add("registry must hold exactly the five skins, has " + ALL.size());
        }
        // 4.7.78: the coin toast's gold follows the skin.
        if (currencyGold(SAIYAN_ROYAL) != SAIYAN_ROYAL.trim() || currencyGold(NAMEK_SERENITY) != NAMEK_SERENITY.trim()
                || currencyGold(ZENO_OMNI) != ZENO_OMNI.trim() || currencyGold(OBSIDIAN_GOLD) != OBSIDIAN_GOLD.trim()
                || currencyGold(BEERUS_DESTRUCTION) != BEERUS_DESTRUCTION.primary()) {
            bad.add("currency gold per skin: the trim (Saiyan, Namek, Zeno, Obsidian), Beerus's gold primary");
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
            if (byId(s.id()) != s || !known(s.id())) {
                bad.add(s.id() + ": id lookup");
            }
        }
        if (byId("nope") != DEFAULT || byId(null) != SAIYAN_ROYAL || known("nope")) {
            bad.add("unknown skin ids fall back to Saiyan Royal");
        }
        if (systemAccentHex('c') != SaiyanRoyal.CRIMSON || systemAccentHex('3') != 0x00AAAA || systemAccentHex('x') != -1) {
            bad.add("system accents are the same in every skin");
        }
        // 4.7.66 Custom.
        if (!known(CUSTOM_ID) || !isCustom("custom") || isCustom("saiyan_royal") || known("custom.x")) {
            bad.add("custom: 'custom' is a known skin id, a broken token is not");
        }
        Skin start = byId(CUSTOM_ID);
        if (!CUSTOM_NAME.equals(start.name()) || start.panel() != SAIYAN_ROYAL.panel()
                || start.primary() != SAIYAN_ROYAL.primary() || start.text() != SAIYAN_ROYAL.text()) {
            bad.add("custom: picking Custom without editing gives Saiyan Royal's tones");
        }
        for (Skin s : ALL) {
            Custom c = Custom.from(s);
            String tok = customToken(c);
            if (tok.length() > 64 || !c.equals(parseCustom(tok)) || !byId(tok).equals(custom(c)) || !known(tok)) {
                bad.add(s.id() + ": custom token must round-trip and fit message 3 (64 chars): " + tok);
            }
        }
        if (parseHex("#E23B3B") != 0xE23B3B || parseHex("e23b3b") != 0xE23B3B || parseHex(" #e23b3b ") != 0xE23B3B
                || parseHex("red") != -1 || parseHex("#12") != -1 || parseHex("#gggggg") != -1 || parseHex("") != -1
                || parseHex(null) != -1 || parseHex("#e23b3b0") != -1) {
            bad.add("custom: hex is #RRGGBB or RRGGBB in any case, anything else is refused");
        }
        int[] probe = {0x000000, 0x202020, 0x555555, 0x777777, 0x808080, 0x999999, 0xBBBBBB, 0xFFFFFF, 0xE23B3B,
                0x2563EB, 0xFFFF55, 0x00AA00, 0x160B0D, 0xF6F9FF};
        for (boolean dark : new boolean[] {true, false}) {
            for (int panel : probe) {
                for (int text : probe) {
                    Custom c = new Custom(dark, Motif.BANNER, panel, 0xE23B3B, 0xF0B429, text, 0x3FAF55);
                    Skin s = custom(c);
                    if (contrast(s.text(), s.panel()) < textTarget(dark)) {
                        bad.add(String.format(Locale.ROOT, "custom %s: text %06x on panel %06x unreadable",
                                dark ? "dark" : "light", text, panel));
                    }
                }
            }
        }
        if (customTextAdjusted(Custom.DEFAULT)
                || !customTextAdjusted(new Custom(true, Motif.BANNER, 0x160B0D, 0xE23B3B, 0xF0B429, 0x160B0D, 0x3FAF55))) {
            bad.add("custom: the readability note shows only when the text color had to change");
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
