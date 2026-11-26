package com.dbzlegacy.adaptivedifficulty.net.gui;

import net.minecraft.resources.ResourceLocation;

/**
 * Shared Ultra GUI wire conventions — <b>not LM-specific</b>.
 *
 * <p>Any Forge mod can speak this protocol on its own channel {@code <modid>:gui}.
 * Packet field layouts live in {@link GuiOpenPacket} / {@link GuiActionPacket} and must not
 * embed a channel namespace. See {@code docs/KAIPATH_GUI_PROTOCOL.md} and
 * {@code tools/ultra-gui-kit/server/} (implement-yourself codecs for other mods).
 *
 * <p>LM 4.8.2: Legacy Mechanics no longer registers {@code legacymechanics:gui} (the KaiPath bridge is
 * gone; the LM client is mandatory). LM keeps these constants for the packet shapes and panel
 * defaults it still uses on its own channel ({@code legacymechanics:native_ui}); the message indexes
 * below describe the KaiPath-era channel for other mods and the kit, not LM's server.
 */
public final class UltraGuiProtocol {
    /** Path segment of every Ultra GUI channel ({@code <modid>:gui}). */
    public static final String CHANNEL_PATH = "gui";

    /**
     * Forge SimpleChannel protocol string. Shared with KaiPath. Bumping requires a coordinated
     * KaiPath release.
     */
    public static final String PROTOCOL_VERSION = "1";

    /** Default panel size when a packet omits fields 8–9. */
    public static final int DEFAULT_PANEL_WIDTH = 350;

    public static final int DEFAULT_PANEL_HEIGHT = 300;

    public static final int DEFAULT_MARGIN = 10;

    /** Message index: GuiOpenPacket S→C. */
    public static final int MSG_OPEN = 0;

    /** Message index: GuiActionPacket C→S. */
    public static final int MSG_ACTION = 1;

    /**
     * Message index: GuiFeaturesPacket C→S (optional client capability advertisement). 4.8.2: LM removed its
     * GuiFeaturesPacket; the LM client's hello ({@code LmNativeProtocol.MSG_HELLO}) lists its features instead.
     */
    public static final int MSG_FEATURES = 2;

    /**
     * Message index: GuiModelSyncPacket S→C (authoritative equipped body-parts state).
     * Feature-gated — only sent to clients that advertise {@code bodyPartsSync} via
     * {@link #MSG_FEATURES}; clients that never registered message 3 must never receive it. 4.8.2: LM sends this
     * payload as native message 5 ({@code LmNativeProtocol.MSG_MODEL_SYNC}).
     */
    public static final int MSG_MODEL_SYNC = 3;

    private UltraGuiProtocol() {}

    /** Channel location for {@code modId} → {@code modid:gui}. */
    public static ResourceLocation channel(String modId) {
        if (modId == null || modId.isBlank()) {
            throw new IllegalArgumentException("modId required for Ultra GUI channel");
        }
        return new ResourceLocation(modId.trim().toLowerCase(), CHANNEL_PATH);
    }

    /** Wire id string {@code modid:gui}. */
    public static String channelId(String modId) {
        return channel(modId).toString();
    }
}
