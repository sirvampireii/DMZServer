package com.dbzlegacy.adaptivedifficulty.client;

import com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins;
import com.dbzlegacy.adaptivedifficulty.net.gui.NativePrefsPacket;
import net.minecraft.client.Minecraft;

/**
 * LM 4.7.62 client state for the player's menu look, from message 3 ({@link NativePrefsPacket}): skin, themed
 * backgrounds on/off, Full/Lite. Until the server says otherwise every menu draws Saiyan Royal, backgrounds on,
 * Full. A skin switch remembers the previous skin briefly so the background crossfades instead of flashing.
 */
final class MenuLook {
    private MenuLook() {}

    /** Background crossfade after a skin switch. */
    static final long SKIN_FADE_NS = 300_000_000L;

    private static MenuSkins.Skin skin = MenuSkins.DEFAULT;
    private static boolean backgrounds = true;
    private static boolean lite;
    private static MenuSkins.Skin previous;
    private static long changedAt;
    /** The "menus running slowly" notice shows once per session, never switching anything itself. */
    private static boolean liteSuggested;

    static void apply(NativePrefsPacket p) {
        MenuSkins.Skin next = MenuSkins.byId(p.skin());
        if (!next.equals(skin)) {
            previous = skin;
            changedAt = System.nanoTime();
        }
        skin = next;
        backgrounds = p.backgrounds();
        lite = p.lite();
        // The open menu re-lays itself out (field colors, cached shapes) in the new skin right away.
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91080_ instanceof NativeMenuScreen screen) {
            screen.lookChanged();
        }
    }

    static MenuSkins.Skin skin() {
        return skin;
    }

    static boolean backgrounds() {
        return backgrounds;
    }

    static boolean lite() {
        return lite;
    }

    /** The skin being faded out after a switch, or null once the fade is over (or in Lite). */
    static MenuSkins.Skin fadingFrom() {
        if (previous == null || lite || System.nanoTime() - changedAt >= SKIN_FADE_NS) {
            return null;
        }
        return previous;
    }

    /** 0 right after a skin switch, 1 once the new background is fully in. */
    static float skinFade() {
        long dt = System.nanoTime() - changedAt;
        return previous == null || lite ? 1f : Math.min(1f, dt / (float) SKIN_FADE_NS);
    }

    static boolean liteSuggested() {
        return liteSuggested;
    }

    static void markLiteSuggested() {
        liteSuggested = true;
    }

    /** Logout / new server: defaults again (the next server sends its own choice). */
    static void reset() {
        skin = MenuSkins.DEFAULT;
        backgrounds = true;
        lite = false;
        previous = null;
        liteSuggested = false;
    }
}
