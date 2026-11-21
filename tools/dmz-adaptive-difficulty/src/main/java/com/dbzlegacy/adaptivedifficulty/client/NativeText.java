package com.dbzlegacy.adaptivedifficulty.client;

import com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

/**
 * Server spec text to drawable text. LM sends Minecraft color codes (a section sign and one letter) in titles,
 * lines and labels; the native renderer draws them in the player's menu skin (LM 4.7.62): the header letter in
 * the skin's trim, the value letter in the skin's text, the Difficulty letter in its crimson, every other letter
 * its usual color, and each ink made readable on the surface it sits on ({@link MenuSkins#ink}). No color value
 * lives here; the registry owns them all.
 */
final class NativeText {
    private NativeText() {}

    /** The color-code mark the server's UltraStyle writes. Parsed here, never written. */
    static final char MARK = (char) 0xA7;

    /** Where a piece of text is drawn: its skin, the surface under it, and the ink of a dim (locked) label. */
    record Ink(MenuSkins.Skin skin, int bg, int muted) {
        static Ink on(MenuSkins.Skin skin, int bg) {
            return new Ink(skin, bg, skin.muted());
        }

        int code(char code, int fallback) {
            return MenuSkins.ink(skin, code, bg, fallback);
        }
    }

    /**
     * Parse color/format codes. {@code muted} paints everything the dim ink; {@code baseBold} keeps the whole
     * text bold (titles); {@code defaultRgb} is the ink before any code (made readable too).
     */
    static MutableComponent parse(String raw, int defaultRgb, boolean muted, boolean baseBold, Ink ink) {
        MutableComponent out = Component.m_237119_();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        int base = MenuSkins.readable(defaultRgb, ink.bg(), ink.skin().dark() ? 3.0 : 4.5);
        int color = muted ? ink.muted() : base;
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
                    color = muted ? ink.muted() : ink.code(code, base);
                    bold = baseBold;
                    italic = under = strike = false;
                } else if (code == 'r') {
                    color = muted ? ink.muted() : base;
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

    /** Ink (RGB) of the first visible character of {@code raw}, as {@link #parse} draws it. */
    static int firstInk(String raw, int fallback, boolean muted, Ink ink) {
        if (muted) {
            return ink.muted();
        }
        int base = MenuSkins.readable(fallback, ink.bg(), ink.skin().dark() ? 3.0 : 4.5);
        int color = base;
        for (int i = 0; raw != null && i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == MARK && i + 1 < raw.length()) {
                char code = Character.toLowerCase(raw.charAt(++i));
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    color = ink.code(code, base);
                } else if (code == 'r') {
                    color = base;
                }
                continue;
            }
            if (!Character.isWhitespace(c)) {
                return color;
            }
        }
        return color;
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
