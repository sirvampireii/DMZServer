package com.dbzlegacy.adaptivedifficulty.net.gui;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraGuiActions;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client → server. KaiPath button / tab press. Server runs Ultra screen handlers.
 * No client-only classes are referenced here.
 *
 * <p><b>Mod-agnostic wire codec</b> — encode/decode never validate or rewrite action ids.
 * Channel namespace is not part of this packet; see {@link UltraGuiProtocol}. Reference copy:
 * {@code tools/ultra-gui-kit/server/UltraGuiActionPacket.java}.
 *
 * <p>Registration uses {@code consumerNetworkThread} + {@link NetworkEvent.Context#enqueueWork}
 * in {@link LmGuiNetwork} so all {@link ServerPlayer} access runs on the main thread.
 */
public final class GuiActionPacket {
    /** Action ids are {@code @screen:do:…} — allow long saga / module ids. */
    private static final int ACTION_ID_MAX = 192;

    private final String screenId;
    private final String actionId;
    private final String argsJson;

    public GuiActionPacket(String screenId, String actionId, String argsJson) {
        this.screenId = GuiOpenPacket.trim(screenId, 64);
        this.actionId = GuiOpenPacket.trim(actionId, ACTION_ID_MAX);
        // 2048 — typed text-field values from inputsJson bindInput / Enter submit.
        this.argsJson = GuiOpenPacket.trim(argsJson == null ? "{}" : argsJson, 2048);
    }

    public String screenId() {
        return screenId;
    }

    public String actionId() {
        return actionId;
    }

    public String argsJson() {
        return argsJson;
    }

    public static void encode(GuiActionPacket msg, FriendlyByteBuf buf) {
        GuiOpenPacket.writeUtf8(buf, msg.screenId);
        GuiOpenPacket.writeUtf8(buf, msg.actionId);
        GuiOpenPacket.writeUtf8(buf, msg.argsJson);
    }

    public static GuiActionPacket decode(FriendlyByteBuf buf) {
        return new GuiActionPacket(
                GuiOpenPacket.readUtf8(buf),
                GuiOpenPacket.readUtf8(buf),
                GuiOpenPacket.readUtf8(buf));
    }

    /**
     * Network-thread entry (via {@link LmGuiNetwork}). Captures the sender, then
     * {@code enqueueWork}s main-thread dispatch.
     */
    public static void handle(GuiActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        // Capture while the network context is still valid (before deferral).
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> dispatchOnMain(sender, msg));
        context.setPacketHandled(true);
    }

    /** Main-thread work only — no network-context access. */
    public static void dispatchOnMain(ServerPlayer player, GuiActionPacket msg) {
        if (msg == null) {
            return;
        }
        if (player == null) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] GuiActionPacket dropped: null sender screenId={} actionId={} args={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    msg.screenId,
                    msg.actionId,
                    msg.argsJson);
            return;
        }
        try {
            LmLog.event(
                    "[{}] GuiActionPacket main-thread (enqueueWork) player={} screenId={} rawActionId={} argsJson={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    player.m_7755_().getString(),
                    msg.screenId,
                    msg.actionId,
                    msg.argsJson);
            UltraGuiActions.dispatch(player, msg.screenId, msg.actionId, msg.argsJson);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] Ultra GuiActionPacket failed screenId={} rawActionId={} args={} player={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    msg.screenId,
                    msg.actionId,
                    msg.argsJson,
                    player.m_7755_().getString(),
                    t);
            DmzRewards.msg(
                    player,
                    UltraStyle.dangerLine(
                            "Something went wrong. Try again, or ask staff if it keeps happening."));
        }
    }
}
