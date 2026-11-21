package com.dbzlegacy.adaptivedifficulty.client;

import com.dbzlegacy.adaptivedifficulty.gui.ultra.SaiyanRoyal;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

/**
 * Server spec text to drawable text. LM sends Minecraft color codes (a section sign and one letter)
 * in titles, lines and labels; the KaiPath bridge strips some of them, the native renderer draws them.
 * The darkest inks are lifted so they stay readable on the dark panel and button faces.
 */
final class NativeText {
    private NativeText() {}

    /** The color-code mark the server's UltraStyle writes. Parsed here, never written. */
    static final char MARK = (char) 0xA7;
    /** Ink of every label on a dim (locked / unavailable) button. */
    static final int MUTED_RGB = 0x777777;

    /** Vanilla color for a code, with the near-black inks lifted for a dark surface. */
    static int rgb(char code, int fallback) {
        switch (Character.toLowerCase(code)) {
            case '0':
                return 0x8A8A8A;
            case '8':
                return 0x7E7E7E;
            case '1':
                return 0x6070FF;
            case '4':
                return 0xD84848;
            case '5':
                return 0xC058C0;
            default:
                break;
        }
        ChatFormatting f = ChatFormatting.m_126645_(Character.toLowerCase(code));
        if (f != null && f.m_126664_() && f.m_126665_() != null) {
            return f.m_126665_();
        }
        return fallback;
    }

    /** Saiyan Royal ink for a code, else the usual one. */
    static int skinRgb(char code, int fallback) {
        int ink = SaiyanRoyal.nativeInk(code);
        return ink >= 0 ? ink : rgb(code, fallback);
    }

    /** Parse color/format codes. {@code muted} paints everything {@link #MUTED_RGB} (dim buttons). */
    static MutableComponent parse(String raw, int defaultRgb, boolean muted) {
        return parse(raw, defaultRgb, muted, false);
    }

    /** As {@link #parse(String, int, boolean)}; {@code baseBold} keeps the whole text bold (titles). */
    static MutableComponent parse(String raw, int defaultRgb, boolean muted, boolean baseBold) {
        return parse(raw, defaultRgb, muted, baseBold, false);
    }

    /**
     * As above; {@code skin} draws the Saiyan Royal inks (4.7.60): the accent letter crimson, the header
     * letter gold trim, the value letter the near-white text. Other letters keep their usual color.
     */
    static MutableComponent parse(String raw, int defaultRgb, boolean muted, boolean baseBold, boolean skin) {
        MutableComponent out = Component.m_237119_();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        int color = muted ? MUTED_RGB : defaultRgb;
        boolean bold = baseBold;
        boolean italic = false;
        boolean under = false;
        boolean strike = false;
        StringBuilder seg = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == MARK && i + 1 < raw.length()) {
                flush(out, seg, color, bold, italic, under, strike);
                char code = Character.toLowerCase(raw.charAt(++i));
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    color = muted ? MUTED_RGB : skin ? skinRgb(code, defaultRgb) : rgb(code, defaultRgb);
                    bold = baseBold;
                    italic = under = strike = false;
                } else if (code == 'r') {
                    color = muted ? MUTED_RGB : defaultRgb;
                    bold = baseBold;
                    italic = under = strike = false;
                } else if (code == 'l') {
                    bold = true;
                } else if (code == 'o') {
                    italic = true;
                } else if (code == 'n') {
                    under = true;
                } else if (code == 'm') {
                    strike = true;
                }
                // 'k' (obfuscated) is ignored: menus never scramble text.
                continue;
            }
            seg.append(c);
        }
        flush(out, seg, color, bold, italic, under, strike);
        return out;
    }

    private static void flush(MutableComponent out, StringBuilder seg, int color, boolean bold, boolean italic,
            boolean under, boolean strike) {
        if (seg.length() == 0) {
            return;
        }
        Style style = Style.f_131099_.m_131148_(TextColor.m_131266_(color))
                .m_131136_(bold).m_131155_(italic).m_131162_(under).m_178522_(strike);
        out.m_7220_(Component.m_237113_(seg.toString()).m_130948_(style));
        seg.setLength(0);
    }

    /** Text without color codes (for narration and width checks). */
    static String plain(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == MARK) {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** {@code text} cut to {@code maxW} px with a plain "..." when it does not fit. */
    static FormattedCharSequence fit(Font font, Component text, int maxW) {
        if (font.m_92852_(text) <= maxW) {
            return text.m_7532_();
        }
        int room = Math.max(0, maxW - font.m_92895_("..."));
        FormattedText cut = font.m_92854_(text, room);
        return Language.m_128107_().m_5536_(FormattedText.m_130773_(new FormattedText[] {cut, FormattedText.m_130775_("...")}));
    }
}
