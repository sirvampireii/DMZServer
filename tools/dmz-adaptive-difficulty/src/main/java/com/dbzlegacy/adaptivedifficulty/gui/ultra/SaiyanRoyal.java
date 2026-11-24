package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LM 4.7.60 "Saiyan Royal" menu skin, on every LM menu. Defined once here; both render paths read it.
 *
 * <ul>
 *   <li><b>Native LM client:</b> every menu draws the shared chrome in the exact hex palette below (panel,
 *       gold trim title, divider and border, near-white text) with rounded panels, buttons, tier rows and
 *       bars and a soft panel shadow. Each system keeps its own accent ({@link #accentHex}) on button edges,
 *       the active tab and frames. Shapes are renderer-owned, never spec fields, and the clickable bounds stay
 *       the full rectangles from the spec layout.</li>
 *   <li><b>KaiPath bridge:</b> cannot draw a panel tint or round corners, so the palette maps onto the color
 *       letters the bridge already understands: headers gold + bold (the gold trim), labels gray, values white
 *       (the near-white text), Difficulty accent red (the crimson); the other accents are unchanged. These are
 *       the 4.7.57 bytes.</li>
 * </ul>
 */
public final class SaiyanRoyal {
    private SaiyanRoyal() {}

    public static final String NAME = "saiyan_royal";

    /**
     * 4.7.62: the palette lives in the skin registry ({@link MenuSkins#SAIYAN_ROYAL}, the owner's hex values from
     * 2026-11-20); these names stay for the bridge chrome and the 4.7.60 checks.
     */
    public static final int PANEL = MenuSkins.SAIYAN_ROYAL.panel();
    public static final int CRIMSON = MenuSkins.SAIYAN_ROYAL.primary();
    public static final int GOLD_TRIM = MenuSkins.SAIYAN_ROYAL.trim();
    public static final int TEXT = MenuSkins.SAIYAN_ROYAL.text();

    /**
     * Bridge mapping onto UltraStyle's existing color letters (no new wire values). Every menu builder takes
     * its header, label, value and system accent letters from here through UltraStyle.
     */
    public static final char BRIDGE_ACCENT = 'c';
    public static final char BRIDGE_HEADER = '6';
    public static final char BRIDGE_LABEL = '7';
    public static final char BRIDGE_VALUE = 'f';

    /** Per-system accent letters (UltraStyle.systemAccentCode), unchanged by the skin. */
    public static final Set<String> SYSTEMS = Set.of("difficulty", "rival", "spar", "dojo", "prestige", "character",
            "skillcheck", "admin");

    /**
     * Native shapes, in menu design pixels, matching the UI Skin Concepts picker preview and the Difficulty Menu
     * Concept mock (panel 14px there).
     */
    public static final int PANEL_RADIUS = 14;
    public static final int BUTTON_RADIUS = 8;
    public static final int BAR_RADIUS = 3;
    public static final int BOX_RADIUS = 6;

    /*
     * Depth (native renderer only). Each shadow is a stack of rounded rings, offset down, alpha fading
     * outwards. The panel has two layers: a wide ambient one and a tight key one.
     */
    public static final int AMBIENT_RINGS = 10;
    public static final int AMBIENT_DROP = 4;
    public static final int AMBIENT_ALPHA = 0x14;
    public static final int KEY_RINGS = 4;
    public static final int KEY_DROP = 2;
    public static final int KEY_ALPHA = 0x30;
    /** Raised buttons and tier rows carry their own small shadow. */
    public static final int BUTTON_SHADOW_RINGS = 2;
    public static final int BUTTON_SHADOW_DROP = 2;
    public static final int BUTTON_SHADOW_ALPHA = 0x40;
    /** A pressed button sinks this far and loses its shadow and highlight. */
    public static final int PRESS_SINK = 2;
    /** The active tab sits this far raised, with a gold glow. */
    public static final int TAB_LIFT = 1;
    public static final int GLOW_RINGS = 3;
    public static final int GLOW_ALPHA = 0x38;
    /** Inner shadow depth of an inset info box or bar track. */
    public static final int INSET_SHADOW_PX = 4;
    public static final int INSET_SHADOW_ALPHA = 0x80;
    /** Panel and button gradients: lighter at the top, darker at the bottom. */
    public static final double GRADIENT_TOP_LIFT = 0.07;
    public static final double GRADIENT_BOTTOM_DROP = 0.25;
    public static final double FACE_TOP_LIFT = 0.12;
    public static final double FACE_BOTTOM_DROP = 0.18;
    /* Top-edge sheen and bottom-edge shade on raised buttons: per skin since 4.7.62 (MenuSkins.Skin). */

    /** Panel gradient: the panel color lifted toward white at the top. */
    public static int panelTop() {
        return MenuSkins.SAIYAN_ROYAL.panelTop();
    }

    /** Panel gradient: the panel color pressed toward black at the bottom. */
    public static int panelBottom() {
        return MenuSkins.SAIYAN_ROYAL.panelBottom();
    }

    /** ARGB mix of {@code a} toward {@code b} by {@code t} (0..1), alpha included. */
    public static int lerpArgb(int a, int b, double t) {
        double k = Math.max(0, Math.min(1, t));
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            out |= ((int) Math.round(ca + (cb - ca) * k) & 0xFF) << shift;
        }
        return out;
    }

    /** Alpha of shadow ring {@code i} (1 = innermost) of {@code rings}: strongest inside, fading out. */
    public static int ringAlpha(int alpha, int rings, int i) {
        return rings <= 0 ? 0 : alpha * (rings - i + 1) / rings;
    }

    /** Vertical offset of a button: pressed sinks, the active tab lifts, others stay put. */
    public static int buttonOffset(boolean pressed, boolean activeTab) {
        return pressed ? PRESS_SINK : activeTab ? -TAB_LIFT : 0;
    }

    private static final Pattern BAR = Pattern.compile("\\[(#*)(-*)\\]\\s*$");
    private static final Pattern DOTS = Pattern.compile("(\\[[x ]\\])(\\s\\[[x ]\\])+\\s*$");

    /**
     * Bridge accent letter of a system's menus, the same letters as 4.7.57: Difficulty red (the crimson), Rival
     * white, Sparring dark aqua, Prestige aqua, Character green, SkillCheck yellow, Admin and config dark red.
     * The main menu and unknown ids have none (they use the shared gold chrome).
     */
    public static String bridgeAccent(String screenId) {
        if (screenId == null || screenId.isBlank()) {
            return "";
        }
        return switch (screenId.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "difficulty" -> String.valueOf(BRIDGE_ACCENT);
            // White, not dark purple: purple on the gold header and yellow button face does not read.
            case "rival" -> "f";
            // Dark aqua: gold is the shared action color and dark red is staff.
            case "spar", "dojo" -> "3";
            case "prestige" -> "b";
            case "character" -> "a";
            case "skillcheck", "skills", "skillcheck_denied" -> "e";
            case "admin", "config", "progression" -> "4";
            default -> "";
        };
    }

    /** Every LM menu wears the skin in the native renderer (owner's pick, 2026-11-20). */
    public static boolean appliesTo(String screenId) {
        return screenId != null;
    }

    /**
     * Exact hex of a system accent letter in the native renderer: Difficulty's red is the crimson, the gold
     * letter is the gold trim; Rival white, Sparring dark aqua, Prestige aqua, Character green, SkillCheck
     * yellow and Admin dark red keep their Minecraft hex. -1 for no accent.
     */
    public static int accentHex(char code) {
        return MenuSkins.systemAccentHex(code);
    }

    /** Accent hex for a bridge accent string ({@code c} or a section sign and {@code c}); -1 when blank. */
    public static int accentHex(String code) {
        if (code == null || code.isBlank()) {
            return -1;
        }
        String c = code.trim();
        return accentHex(c.charAt(c.length() - 1));
    }

    /** Mix {@code a} toward {@code b} by {@code t} (0..1), RGB only. */
    public static int blend(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * Native ink for a color letter on a skinned screen: the accent letter draws crimson, the header letter
     * gold trim, the value letter the near-white text. Every other letter keeps its usual color (-1).
     */
    public static int nativeInk(char code) {
        char c = Character.toLowerCase(code);
        return c == BRIDGE_ACCENT ? CRIMSON : c == BRIDGE_HEADER ? GOLD_TRIM : c == BRIDGE_VALUE ? TEXT : -1;
    }

    /**
     * How far a rounded corner cuts into a row: {@code dy} is the distance from the corner's top (or bottom)
     * edge to the middle of the row, in the same units as {@code radius}. 0 once past the curve.
     */
    public static double cornerInset(double radius, double dy) {
        if (radius <= 0 || dy >= radius) {
            return 0;
        }
        double off = radius - Math.max(0, dy);
        return radius - Math.sqrt(Math.max(0, radius * radius - off * off));
    }

    /** Radius actually drawn for a box: never more than half its shorter side. */
    public static int radiusFor(int w, int h, int wanted) {
        return Math.max(0, Math.min(wanted, Math.min(w, h) / 2));
    }

    /** Index where a trailing text bar ({@code [####----]}) starts in a plain line, or -1. */
    public static int barStart(String plain) {
        Matcher m = BAR.matcher(plain == null ? "" : plain);
        return m.find() && m.end(2) - m.start(1) >= 4 ? m.start() : -1;
    }

    /** Filled share of a trailing text bar, 0..1, or -1 when the line has none. */
    public static double barFill(String plain) {
        Matcher m = BAR.matcher(plain == null ? "" : plain);
        if (!m.find() || m.end(2) - m.start(1) < 4) {
            return -1;
        }
        int filled = m.group(1).length();
        return (double) filled / (filled + m.group(2).length());
    }

    /** Index where trailing progress dots ({@code [x] [ ] [ ]}) start in a plain line, or -1. */
    public static int dotsStart(String plain) {
        Matcher m = DOTS.matcher(plain == null ? "" : plain);
        return m.find() ? m.start() : -1;
    }

    /** Done flags of trailing progress dots, in order; empty when the line has none. */
    public static boolean[] dots(String plain) {
        int at = dotsStart(plain);
        if (at < 0) {
            return new boolean[0];
        }
        String tail = plain.substring(at);
        List<Boolean> out = new ArrayList<>();
        for (int i = tail.indexOf('['); i >= 0 && i + 2 < tail.length(); i = tail.indexOf('[', i + 1)) {
            out.add(tail.charAt(i + 1) == 'x');
        }
        boolean[] r = new boolean[out.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = out.get(i);
        }
        return r;
    }

    private static int lum(int rgb) {
        return ((rgb >> 16) & 0xFF) * 3 + ((rgb >> 8) & 0xFF) * 6 + (rgb & 0xFF);
    }

    /**
     * Pins the palette, the bridge mapping and the shape math. If a constant moves on purpose, update these
     * pins and the 4.7.60 ship-manifest check in the same change.
     */
    public static List<String> selfTest() {
        List<String> bad = new ArrayList<>();
        if (PANEL != 0x160B0D || CRIMSON != 0xE23B3B || GOLD_TRIM != 0xF0B429 || TEXT != 0xF6E8E4) { // pins
            bad.add("Saiyan Royal palette must be panel #160b0d, crimson #e23b3b, gold trim #f0b429, text #f6e8e4");
        }
        String m = UltraStyle.MARK;
        if (!UltraStyle.systemAccentCode("difficulty").equals("c") || !UltraStyle.ACCENT_DIFFICULTY.equals(m + "c")
                || !"red".equals(UltraStyle.systemPaletteName("difficulty"))) {
            bad.add("Difficulty accent over the bridge must stay red (the crimson)");
        }
        if (!UltraStyle.HEADER.equals(m + "6" + m + "l") || !UltraStyle.LABEL.equals(m + "7")
                || !UltraStyle.VALUE.equals(m + "f") || !UltraStyle.ACCENT_RIVAL.equals(m + "f")
                || !UltraStyle.ACCENT_SPAR.equals(m + "3") || !UltraStyle.ACCENT_PRESTIGE.equals(m + "b")
                || !UltraStyle.ACCENT_CHARACTER.equals(m + "a") || !UltraStyle.ACCENT_SKILLCHECK.equals(m + "e")
                || !UltraStyle.ACCENT_ADMIN.equals(m + "4")) {
            bad.add("bridge bytes as 4.7.57: headers gold + bold, labels gray, values white, system accents");
        }
        if (!UltraStyle.systemAccentCode("rival").equals("f") || !UltraStyle.systemAccentCode("spar").equals("3")
                || !UltraStyle.systemAccentCode("prestige").equals("b")
                || !UltraStyle.systemAccentCode("character").equals("a")
                || !UltraStyle.systemAccentCode("skillcheck").equals("e")
                || !UltraStyle.systemAccentCode("admin").equals("4")
                || !UltraStyle.systemAccentCode("config").equals("4")
                || !UltraStyle.systemAccentCode("skills").equals("e")
                || !UltraStyle.systemAccentCode("hub").isEmpty()) {
            bad.add("other systems keep their bridge accents (rival white, sparring dark aqua, prestige aqua,"
                    + " character green, skillcheck yellow, admin dark red)");
        }
        for (String id : List.of("hub", "difficulty", "rival", "spar", "prestige", "character", "skillcheck",
                "admin", "config", "progression", "skills")) {
            if (!appliesTo(id)) {
                bad.add("every menu wears the skin natively: " + id);
            }
        }
        if (accentHex(UltraStyle.systemAccentCode("difficulty")) != CRIMSON
                || accentHex(UltraStyle.systemAccentCode("rival")) != 0xFFFFFF
                || accentHex(UltraStyle.systemAccentCode("spar")) != 0x00AAAA
                || accentHex(UltraStyle.systemAccentCode("prestige")) != 0x55FFFF
                || accentHex(UltraStyle.systemAccentCode("character")) != 0x55FF55
                || accentHex(UltraStyle.systemAccentCode("skillcheck")) != 0xFFFF55
                || accentHex(UltraStyle.systemAccentCode("admin")) != 0xAA0000
                || accentHex(UltraStyle.systemAccentCode("hub")) != -1) {
            bad.add("native accents: Difficulty crimson, Rival white, Sparring dark aqua, Prestige aqua,"
                    + " Character green, SkillCheck yellow, Admin dark red, hub none");
        }
        if (blend(0x000000, 0xFFFFFF, 0.5) != 0x808080 || blend(CRIMSON, PANEL, 0) != CRIMSON) {
            bad.add("accent blend");
        }
        if (nativeInk('c') != CRIMSON || nativeInk('6') != GOLD_TRIM || nativeInk('f') != TEXT || nativeInk('7') != -1
                || nativeInk('a') != -1) {
            bad.add("native inks: accent crimson, header gold trim, value text; others unchanged");
        }
        if (Math.abs(cornerInset(8, 0) - 8) > 1e-9 || cornerInset(8, 8) != 0 || cornerInset(8, 20) != 0
                || cornerInset(0, 0) != 0 || cornerInset(12, 6) <= 0 || cornerInset(12, 6) >= cornerInset(12, 2)) {
            bad.add("rounded corner inset: full radius at the edge, shrinking to 0 past the curve");
        }
        if (radiusFor(80, 16, BUTTON_RADIUS) != 8 || radiusFor(80, 10, BUTTON_RADIUS) != 5
                || radiusFor(426, 300, PANEL_RADIUS) != 14) {
            bad.add("radius never exceeds half the shorter side");
        }
        if (lum(panelTop()) <= lum(PANEL) || lum(panelBottom()) >= lum(PANEL)) {
            bad.add("panel gradient is lighter at the top, darker at the bottom");
        }
        if (lerpArgb(0x00000000, 0xFFFFFFFF, 0) != 0 || lerpArgb(0x00000000, 0xFFFFFFFF, 1) != 0xFFFFFFFF
                || lerpArgb(0x80000000, 0x00000000, 0.5) != 0x40000000) {
            bad.add("ARGB gradient math");
        }
        if (ringAlpha(KEY_ALPHA, KEY_RINGS, 1) != KEY_ALPHA || ringAlpha(KEY_ALPHA, KEY_RINGS, KEY_RINGS) >= KEY_ALPHA) {
            bad.add("shadow rings fade outwards");
        }
        if (buttonOffset(true, false) != 2 || buttonOffset(true, true) != 2 || buttonOffset(false, true) != -1
                || buttonOffset(false, false) != 0 || PRESS_SINK != 2) {
            bad.add("pressed buttons sink 2px; the active tab is raised");
        }
        if (barFill("[######------------------]") != 0.25 || barFill("Momentum 1:00") != -1 || barFill("[x]") != -1
                || barStart("[####]") != 0) {
            bad.add("Momentum text bar is read as a fill share");
        }
        boolean[] d = dots("Progress [x] [x] [ ]");
        if (d.length != 3 || !d[0] || !d[1] || d[2] || dotsStart("Progress [x] [ ] [ ]") != 9
                || dotsStart("Cost [x]") != -1) {
            bad.add("progress dots are read from the step line");
        }
        return bad;
    }
}
