package com.dbzlegacy.adaptivedifficulty.net.gui;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client to server on {@code legacymechanics:native_ui} (LM 4.7.58), message 2. The LM client
 * sends it once after login: its LM version and the JSON array of spec features it renders
 * natively. The server routes a menu to the native renderer only when every feature the menu
 * needs is in that list (see {@link LmNativeProtocol#analyze}).
 *
 * <p>Wire: two length-prefixed UTF-8 strings, same encoding as {@link GuiOpenPacket}.
 */
public final class NativeHelloPacket {
    private final String clientVersion;
    private final String featuresJson;

    public NativeHelloPacket(String clientVersion, String featuresJson) {
        this.clientVersion = GuiOpenPacket.trim(clientVersion, 32);
        this.featuresJson = GuiOpenPacket.trim(featuresJson == null ? "[]" : featuresJson, 2048);
    }

    public String clientVersion() {
        return clientVersion;
    }

    public String featuresJson() {
        return featuresJson;
    }

    public static void encode(NativeHelloPacket msg, FriendlyByteBuf buf) {
        GuiOpenPacket.writeUtf8(buf, msg.clientVersion);
        GuiOpenPacket.writeUtf8(buf, msg.featuresJson);
    }

    public static NativeHelloPacket decode(FriendlyByteBuf buf) {
        return new NativeHelloPacket(GuiOpenPacket.readUtf8(buf), GuiOpenPacket.readUtf8(buf));
    }
}
