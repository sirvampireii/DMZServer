package com.dmzlegacy.kaipath.ultra.consumers;

import com.dmzlegacy.kaipath.ultra.UltraUiBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;

/**
 * {@code kaipath:partners}: how a <b>server-only</b> partner tells KaiPath it speaks this protocol,
 * with no KaiPath release, no client config and no restart.
 *
 * <p>Forge only lets a client register channels while the game starts, and a server-only mod has
 * no jar on the client to scan, so KaiPath cannot know {@code <modid>:gui} in advance. This one
 * channel has a fixed name, so it is always registered. On login a partner server sends one
 * payload per mod id: a single {@code FriendlyByteBuf.writeUtf(modId)} (server kit:
 * {@code UltraGuiAnnounce.payload(modId)}). KaiPath then reads that mod's {@code <modid>:gui}
 * packets directly ({@code UltraRawPayloadMixin}) and sends clicks back the same way, so the
 * partner's own Forge channel on the server needs no change.
 *
 * <p>Both accept predicates take anything, so servers without this channel (every vanilla or
 * non-partner server) still connect.
 */
public final class UltraGuiAnnounce {
    public static final ResourceLocation CHANNEL = new ResourceLocation("kaipath", "partners");
    public static final String PROTOCOL = "1";

    private static boolean registered;

    private UltraGuiAnnounce() {}

    /** Register once while mods load (with the partner channels). Idempotent. */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        try {
            EventNetworkChannel channel = NetworkRegistry.newEventChannel(
                    CHANNEL, () -> PROTOCOL, remote -> true, remote -> true);
            channel.addListener(UltraGuiAnnounce::onPayload);
        } catch (RuntimeException e) {
            UltraUiBridge.LOGGER.warn("[kaipath] {} not registered ({}); server-only partners outside the seed "
                    + "and config will not open", CHANNEL, e.getMessage());
        }
    }

    private static void onPayload(NetworkEvent event) {
        if (!(event instanceof NetworkEvent.ServerCustomPayloadEvent)) {
            return;
        }
        FriendlyByteBuf payload = event.getPayload();
        String modId;
        try {
            modId = payload == null ? "" : payload.m_130136_(64);
        } catch (RuntimeException e) {
            modId = "";
        }
        String id = modId;
        event.getSource().get().enqueueWork(() -> UltraGuiConsumerRegistry.acceptAnnounced(id));
        event.getSource().get().setPacketHandled(true);
    }
}
