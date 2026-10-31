package com.dmzlegacy.kaipath.ultra.consumers.legacymechanics;

import com.dmzlegacy.kaipath.ultra.UltraUiBridge;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server → client: authoritative equipped body-parts state for the receiving player
 * (message 3 on {@code <modid>:gui}).
 *
 * <p><b>Feature-gated</b>: only sent to clients that advertised {@code bodyPartsSync} via
 * {@link GuiFeaturesPacket}. A client that registered this message must advertise the id —
 * and must never advertise it without registering (an unregistered message id aborts the
 * connection).
 *
 * <p>Wire format (append-compatible): field 1 {@code partsJson} (the playerModel parts
 * object — same schema as {@code layoutJson.playerModel.parts}) and field 2 {@code reason}
 * (trigger token, diagnostics only).
 *
 * <p>On receive, {@link BodyPartPreview#applySync} <b>replaces</b> (never merges) the cached
 * world-render parts. The server already guarantees a single tail-slot entry per payload.
 */
public final class GuiModelSyncPacket {
    /** Max {@code partsJson} chars on the wire. */
    public static final int PARTS_JSON_MAX = 8192;

    /** {@code reason} tokens (diagnostics only — not part of the render contract). */
    public static final String REASON_LOGIN = "login";

    public static final String REASON_EQUIP = "equip";

    public static final String REASON_UNEQUIP = "unequip";

    public static final String REASON_CLEAR = "clear";

    /** Sent to a tracker that just started seeing the subject (PlayerEvent.StartTracking). */
    public static final String REASON_TRACK = "track";

    private final String partsJson;
    private final String reason;

    public GuiModelSyncPacket(String partsJson, String reason) {
        this.partsJson = GuiOpenPacket.trim(partsJson == null ? "" : partsJson, PARTS_JSON_MAX);
        this.reason = GuiOpenPacket.trim(reason == null ? "" : reason, 32);
    }

    /** PlayerModel parts object JSON ({@code schema:1} layout). */
    public String partsJson() {
        return partsJson;
    }

    /** Trigger token — diagnostics only. */
    public String reason() {
        return reason;
    }

    /** Serialize 2 fields — no channel / mod-id bytes. */
    public static void encode(GuiModelSyncPacket msg, FriendlyByteBuf buf) {
        GuiOpenPacket.writeUtf8(buf, msg.partsJson);
        GuiOpenPacket.writeUtf8(buf, msg.reason);
    }

    /** Decode fields 1–2; missing trailing fields default. Leftover bytes are ignored. */
    public static GuiModelSyncPacket decode(FriendlyByteBuf buf) {
        String parts = GuiOpenPacket.readUtf8(buf);
        String reason = buf.isReadable() ? GuiOpenPacket.readUtf8(buf) : "";
        return new GuiModelSyncPacket(parts, reason);
    }

    /** Replace the world-render cache with this snapshot (server-authoritative). */
    public static void handle(GuiModelSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        handle(packet, ctx, "");
    }

    /**
     * Replace this channel's snapshot for the subject (server-authoritative). Other
     * {@code <modid>:gui} channels' pieces for the same player stay as they are.
     */
    public static void handle(GuiModelSyncPacket packet, Supplier<NetworkEvent.Context> ctx, String channelId) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            try {
                BodyPartPreview.applySync(channelId, packet.partsJson(), packet.reason());
            } catch (Throwable t) {
                UltraUiBridge.LOGGER.error(
                        "[kaipath] GuiModelSyncPacket apply failed reason={}",
                        packet == null ? "<null>" : packet.reason(),
                        t);
            }
        });
        context.setPacketHandled(true);
    }
}
