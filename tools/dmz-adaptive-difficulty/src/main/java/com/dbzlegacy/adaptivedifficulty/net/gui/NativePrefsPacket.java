package com.dbzlegacy.adaptivedifficulty.net.gui;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server to client on {@code legacymechanics:native_ui} (LM 4.7.62), message 3: the player's menu look (skin id,
 * themed backgrounds on/off, Lite on/off). Sent once after the hello and once per change, only to an LM client
 * whose hello listed {@link LmNativeProtocol#FEATURE_MENU_SKINS}, so an older LM client never receives a message
 * id it did not register. Never sent over the KaiPath bridge.
 *
 * <p>Wire: one length-prefixed UTF-8 string, then two booleans.
 */
public final class NativePrefsPacket {
    private final String skin;
    private final boolean backgrounds;
    private final boolean lite;

    public NativePrefsPacket(String skin, boolean backgrounds, boolean lite) {
        this.skin = GuiOpenPacket.trim(skin == null ? "" : skin, 64);
        this.backgrounds = backgrounds;
        this.lite = lite;
    }

    public String skin() {
        return skin;
    }

    public boolean backgrounds() {
        return backgrounds;
    }

    public boolean lite() {
        return lite;
    }

    public static void encode(NativePrefsPacket msg, FriendlyByteBuf buf) {
        GuiOpenPacket.writeUtf8(buf, msg.skin);
        buf.writeBoolean(msg.backgrounds);
        buf.writeBoolean(msg.lite);
    }

    public static NativePrefsPacket decode(FriendlyByteBuf buf) {
        return new NativePrefsPacket(GuiOpenPacket.readUtf8(buf), buf.readBoolean(), buf.readBoolean());
    }
}
