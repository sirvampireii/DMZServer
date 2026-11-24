package com.dbzlegacy.adaptivedifficulty.client;

import com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * LM 4.7.74: laid-out menu text, so a frame draws text instead of building it (the 4.7.61 lag rule "lay text out
 * once"). Before this, every title, body line and button label was parsed into a component tree, contrast-checked
 * colour by colour ({@link MenuSkins#readable}), measured, trimmed and bidi-ordered on every frame.
 *
 * <p>One cache per open screen, keyed by the raw server text plus everything that changes its look (base colour,
 * dim, bold, the surface under it, the skin, the width it must fit). A hit is a map lookup with no allocation. The
 * screen clears it when the skin or the window changes; it also clears itself past {@link #MAX} entries.
 */
final class TextCache {
    static final int MAX = 1024;

    private static final class Entry {
        final int rgb;
        final boolean muted;
        final boolean bold;
        final int bg;
        final MenuSkins.Skin skin;
        final int maxW;
        final FormattedCharSequence seq;
        final List<FormattedCharSequence> lines;
        final int width;
        Entry next;

        Entry(int rgb, boolean muted, boolean bold, int bg, MenuSkins.Skin skin, int maxW, FormattedCharSequence seq,
                List<FormattedCharSequence> lines, int width) {
            this.rgb = rgb;
            this.muted = muted;
            this.bold = bold;
            this.bg = bg;
            this.skin = skin;
            this.maxW = maxW;
            this.seq = seq;
            this.lines = lines;
            this.width = width;
        }

        boolean same(int rgb, boolean muted, boolean bold, int bg, MenuSkins.Skin skin, int maxW, boolean wrapped) {
            return this.rgb == rgb && this.muted == muted && this.bold == bold && this.bg == bg
                    && (this.skin == skin || this.skin.equals(skin))
                    && this.maxW == maxW && (this.lines != null) == wrapped;
        }
    }

    private final Map<String, Entry> map = new HashMap<>();
    private int size;
    /** Width in px of the last {@link #fit} result (read right after the call). */
    int width;

    /** {@code raw} parsed in {@code skin} on surface {@code bg}, cut to {@code maxW} with "..." when it is wider. */
    FormattedCharSequence fit(Font font, String raw, int rgb, boolean muted, boolean bold, MenuSkins.Skin skin, int bg,
            int maxW) {
        String key = raw == null ? "" : raw;
        Entry e = find(key, rgb, muted, bold, bg, skin, maxW, false);
        if (e == null) {
            Component c = NativeText.parse(key, rgb, muted, bold, NativeText.Ink.on(skin, bg));
            FormattedCharSequence seq = NativeText.fit(font, c, maxW);
            e = put(key, new Entry(rgb, muted, bold, bg, skin, maxW, seq, null, font.m_92724_(seq)));
        }
        width = e.width;
        return e.seq;
    }

    /** {@code raw} wrapped to {@code maxW} (book pages). */
    List<FormattedCharSequence> wrap(Font font, String raw, int rgb, MenuSkins.Skin skin, int bg, int maxW) {
        String key = raw == null ? "" : raw;
        Entry e = find(key, rgb, false, false, bg, skin, maxW, true);
        if (e == null) {
            Component c = NativeText.parse(key, rgb, false, false, NativeText.Ink.on(skin, bg));
            e = put(key, new Entry(rgb, false, false, bg, skin, maxW, null, new ArrayList<>(font.m_92923_(c, maxW)), 0));
        }
        return e.lines;
    }

    private Entry find(String key, int rgb, boolean muted, boolean bold, int bg, MenuSkins.Skin skin, int maxW,
            boolean wrapped) {
        for (Entry e = map.get(key); e != null; e = e.next) {
            if (e.same(rgb, muted, bold, bg, skin, maxW, wrapped)) {
                return e;
            }
        }
        return null;
    }

    private Entry put(String key, Entry e) {
        if (size >= MAX) {
            clear();
        }
        e.next = map.get(key);
        map.put(key, e);
        size++;
        return e;
    }

    void clear() {
        map.clear();
        size = 0;
    }

    int size() {
        return size;
    }
}
