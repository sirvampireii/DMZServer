package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LM 4.7.60 "Saiyan Royal" menu skin, Difficulty first. Defined once here; both render paths read it.
 *
 * <ul>
 *   <li><b>Native LM client:</b> draws the exact hex palette below with rounded panels, buttons, tier rows
 *       and bars, a soft panel shadow and a 1px gold-trim border. Shapes are renderer-owned, never spec
 *       fields, and the clickable bounds stay the full rectangles from the spec layout.</li>
 *   <li><b>KaiPath bridge:</b> cannot draw a panel tint or round corners, so the palette maps onto the color
 *       letters the bridge already understands: Difficulty accent red (the crimson), headers gold + bold (the
 *       gold trim), labels gray, values white (the near-white text). These are the 4.7.57 bytes, unchanged.</li>
 * </ul>
 *
 * <p>Only the screens in {@link #SCREENS} wear the skin; every other system keeps its current look.
 */
public final class SaiyanRoyal {
    private SaiyanRoyal() {}

    public static final String NAME = "saiyan_royal";

    /** Source of truth: the hex values the owner picked (2026-11-20). */
    public static final String PANEL_HEX = "#160b0d";
    public static final String CRIMSON_HEX = "#e23b3b";
    public static final String GOLD_TRIM_HEX = "#f0b429";
    public static final String TEXT_HEX = "#f6e8e4";

    public static final int PANEL = rgb(PANEL_HEX);
    public static final int CRIMSON = rgb(CRIMSON_HEX);
    public static final int GOLD_TRIM = rgb(GOLD_TRIM_HEX);
    public static final int TEXT = rgb(TEXT_HEX);

    /** Bridge mapping onto UltraStyle's existing color letters (no new wire values). */
    public static final char BRIDGE_ACCENT = 'c';
    public static final char BRIDGE_HEADER = '6';
    public static final char BRIDGE_LABEL = '7';
    public static final char BRIDGE_VALUE = 'f';

    /** Screen ids that wear the skin. Difficulty first; no other system in 4.7.60. */
    public static final Set<String> SCREENS = Set.of("difficulty");

    /** Native shapes, in menu design pixels (matching the UI Skin Concepts picker preview). */
    public static final int PANEL_RADIUS = 12;
    public static final int BUTTON_RADIUS = 8;
    public static final int BAR_RADIUS = 3;
    /** Soft drop shadow under the panel: rings, offset down, alpha fading outwards. */
    public static final int SHADOW_RINGS = 6;
    public static final int SHADOW_DROP = 3;
    public static final int SHADOW_ALPHA = 0x2C;

    private static final Pattern BAR = Pattern.compile("\\[(#*)(-*)\\]\\s*$");
    private static final Pattern DOTS = Pattern.compile("(\\[[x ]\\])(\\s\\[[x ]\\])+\\s*$");

    public static boolean appliesTo(String screenId) {
        return screenId != null && SCREENS.contains(screenId.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Native ink for a color letter on a skinned screen: the accent letter draws crimson, the header letter
     * gold trim, the value letter the near-white text. Every other letter keeps its usual color (-1).
     */
    public static int nativeInk(char code) {
        char c = Character.toLowerCase(code);
        if (c == BRIDGE_ACCENT) {
            return CRIMSON;
        }
        if (c == BRIDGE_HEADER) {
            return GOLD_TRIM;
        }
        if (c == BRIDGE_VALUE) {
            return TEXT;
        }
        return -1;
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

    private static int rgb(String hex) {
        return Integer.parseInt(hex.substring(1), 16);
    }

    /**
     * Pins the palette, the bridge mapping and the shape math. If a constant moves on purpose, update these
     * pins and the 4.7.60 ship-manifest check in the same change.
     */
    public static List<String> selfTest() {
        List<String> bad = new ArrayList<>();
        if (PANEL != 0x160B0D || CRIMSON != 0xE23B3B || GOLD_TRIM != 0xF0B429 || TEXT != 0xF6E8E4) {
            bad.add("Saiyan Royal palette must be panel #160b0d, crimson #e23b3b, gold trim #f0b429, text #f6e8e4");
        }
        String m = UltraStyle.MARK;
        if (!UltraStyle.systemAccentCode("difficulty").equals(String.valueOf(BRIDGE_ACCENT))
                || !UltraStyle.ACCENT_DIFFICULTY.equals(m + BRIDGE_ACCENT)
                || !"red".equals(UltraStyle.systemPaletteName("difficulty"))) {
            bad.add("Difficulty accent over the bridge must stay red (the crimson)");
        }
        if (!UltraStyle.HEADER.equals(m + BRIDGE_HEADER + UltraStyle.BOLD) || !UltraStyle.LABEL.equals(m + BRIDGE_LABEL)
                || !UltraStyle.VALUE.equals(m + BRIDGE_VALUE)) {
            bad.add("headers gold + bold, labels gray, values white over the bridge");
        }
        if (!UltraStyle.systemAccentCode("rival").equals("f") || !UltraStyle.systemAccentCode("spar").equals("3")
                || !UltraStyle.systemAccentCode("prestige").equals("b")
                || !UltraStyle.systemAccentCode("character").equals("a")) {
            bad.add("other systems keep their accents (rival white, sparring dark aqua, prestige aqua, character green)");
        }
        if (!appliesTo("difficulty") || appliesTo("rival") || appliesTo("spar") || appliesTo("prestige")
                || appliesTo("character") || appliesTo("hub")) {
            bad.add("only Difficulty wears the skin");
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
                || radiusFor(426, 300, PANEL_RADIUS) != 12) {
            bad.add("radius never exceeds half the shorter side");
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
