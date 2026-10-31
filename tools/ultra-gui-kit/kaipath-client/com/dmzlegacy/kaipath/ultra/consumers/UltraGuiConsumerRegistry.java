package com.dmzlegacy.kaipath.ultra.consumers;

import com.dmzlegacy.kaipath.ultra.UltraUiBridge;
import com.dmzlegacy.kaipath.ultra.consumers.legacymechanics.BodyPartPreview;
import com.dmzlegacy.kaipath.ultra.consumers.legacymechanics.GuiActionPacket;
import com.dmzlegacy.kaipath.ultra.consumers.legacymechanics.GuiFeaturesPacket;
import com.dmzlegacy.kaipath.ultra.consumers.legacymechanics.GuiModelSyncPacket;
import com.dmzlegacy.kaipath.ultra.consumers.legacymechanics.GuiOpenPacket;
import com.dmzlegacy.kaipath.ultra.consumers.legacymechanics.LmScreens;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Multi-mod Ultra GUI client registry. Each server mod owns {@code <modid>:gui}; KaiPath keys
 * consumers by that channel id — never assume a single mod (LegacyMechanics is only one partner).
 *
 * <p>JLDK: register every partner via {@link UltraGuiPartners#registerDefaults()} or
 * {@link #registerClientChannel(String)}. Packet codecs are channel-agnostic; the same
 * GuiOpen/GuiAction/GuiFeatures classes serve all mods. Advertises the full optional set via
 * {@link #ADVERTISED_FEATURES_JSON} on every registered channel.
 */
public final class UltraGuiConsumerRegistry {
    public static final String CHANNEL_PATH = "gui";
    public static final String PROTOCOL_VERSION = "1";

    /**
     * Features this reference client honors on <b>every</b> registered {@code <modid>:gui}
     * channel — keep in sync with {@code LmDynamicScreen} + {@code GuiOpenPacket} UTF-8 decode.
     *
     * <p>KaiPath <b>1.3.17</b> advertises fifteen ids through {@code combined.forms.v1}. This kit
     * also advertises {@code bodyParts} (explicit preview pieces), {@code icon} (optional
     * button/tab mark), and {@code bodyPartsSync} (message 3 world model sync). Partners that do
     * not understand an id ignore it. {@code footer:true} is honored via {@code UltraBridgeFooter}.
     *
     * <p>{@code bodyPartsSync} is advertised <b>because message 3 is registered below</b> —
     * never advertise an id for an unregistered message (an unregistered message id from the
     * server aborts the connection).
     */
    public static final String ADVERTISED_FEATURES_JSON =
            "[\"style\",\"activeTab\",\"titleAlign\",\"playerModel\",\"textInput\","
                    + "\"headerBottom\",\"accent\",\"palette\",\"sound\",\"buttonSkin\",\"frame\","
                    + "\"pagedBook\",\"portraitFrame\",\"fixedFooter\",\"combined.forms.v1\","
                    + "\"bodyParts\",\"icon\",\"bodyPartsSync\"]";

    private static final Map<String, BoundChannel> CHANNELS = new ConcurrentHashMap<>();

    private UltraGuiConsumerRegistry() {}

    /** Register {@code modId:gui} with protocol {@link #PROTOCOL_VERSION}. */
    public static void registerClientChannel(String modId) {
        registerClientChannel(modId, PROTOCOL_VERSION);
    }

    /**
     * Register a client consumer for {@code <modId>:gui}. Idempotent per modId.
     *
     * @param modId Forge mod id of the <em>server</em> partner (channel namespace)
     * @param protocolVersion local Forge protocol string (accept mismatched remotes with warn)
     */
    public static void registerClientChannel(String modId, String protocolVersion) {
        if (modId == null || modId.isBlank()) {
            throw new IllegalArgumentException("modId required");
        }
        String ns = modId.trim().toLowerCase();
        String channelId = ns + ":" + CHANNEL_PATH;
        if (CHANNELS.containsKey(channelId)) {
            return;
        }
        String proto = protocolVersion == null || protocolVersion.isBlank()
                ? PROTOCOL_VERSION
                : protocolVersion;
        Predicate<String> accept = remote -> {
            if (remote != null && !remote.isBlank() && !proto.equals(remote)) {
                UltraUiBridge.LOGGER.warn(
                        "[kaipath] Ultra GUI channel {} protocol remote={} local={} — accepting",
                        channelId,
                        remote,
                        proto);
            }
            return true;
        };
        SimpleChannel channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(ns, CHANNEL_PATH),
                () -> proto,
                accept,
                accept);
        BoundChannel bound = new BoundChannel(channelId, channel);
        channel.messageBuilder(GuiOpenPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GuiOpenPacket::encode)
                .decoder(GuiOpenPacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    ctx.get().enqueueWork(() -> {
                        LmScreens.open(packet, channelId);
                        // Re-advertise: cheap, and covers a login advertise the server missed.
                        advertiseFeatures(channelId, ADVERTISED_FEATURES_JSON);
                    });
                    ctx.get().setPacketHandled(true);
                })
                .add();
        channel.messageBuilder(GuiActionPacket.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GuiActionPacket::encode)
                .decoder(GuiActionPacket::decode)
                .consumerMainThread(GuiActionPacket::handleClientNoop)
                .add();
        channel.messageBuilder(GuiFeaturesPacket.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GuiFeaturesPacket::encode)
                .decoder(GuiFeaturesPacket::decode)
                .consumerMainThread(GuiFeaturesPacket::handleClientNoop)
                .add();
        // Message 3 — world model sync. Registered before the advertise below: the server
        // only sends it to clients advertising "bodyPartsSync".
        channel.messageBuilder(GuiModelSyncPacket.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GuiModelSyncPacket::encode)
                .decoder(GuiModelSyncPacket::decode)
                .consumerMainThread((packet, ctx) -> GuiModelSyncPacket.handle(packet, ctx, channelId))
                .add();
        CHANNELS.put(channelId, bound);
        ensureSessionHooks();
        UltraUiBridge.LOGGER.info(
                "[kaipath] Ultra GUI consumer registered channel={} protocol={} featuresJson={}",
                channelId,
                proto,
                ADVERTISED_FEATURES_JSON);
        // No server connection yet at mod setup, so this usually does nothing; onLoggingIn
        // advertises once the server's channels are known.
        advertiseFeatures(channelId, ADVERTISED_FEATURES_JSON);
    }

    private static boolean SESSION_HOOKS_REGISTERED;

    /**
     * Per-session hooks, registered once per JVM. Login: drop any stale world state and advertise
     * on every registered channel (the server checks a few seconds after login). Logout: drop the
     * server-synced world state (contract: state is per-session; the next login re-syncs).
     */
    private static void ensureSessionHooks() {
        synchronized (UltraGuiConsumerRegistry.class) {
            if (SESSION_HOOKS_REGISTERED) {
                return;
            }
            SESSION_HOOKS_REGISTERED = true;
            try {
                MinecraftForge.EVENT_BUS.addListener(UltraGuiConsumerRegistry::onLoggingIn);
                MinecraftForge.EVENT_BUS.addListener(UltraGuiConsumerRegistry::onLoggingOut);
            } catch (Throwable t) {
                UltraUiBridge.LOGGER.warn(
                        "[kaipath] session hooks not registered ({}); call advertiseFeatures after login "
                                + "and BodyPartPreview.clearSync() on disconnect",
                        t.toString());
            }
        }
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        try {
            BodyPartPreview.clearSync();
        } catch (Throwable ignored) {
            // cache is best-effort
        }
        advertiseAll();
    }

    /** Advertise {@link #ADVERTISED_FEATURES_JSON} on every registered channel the server has. */
    public static void advertiseAll() {
        for (String channelId : CHANNELS.keySet()) {
            advertiseFeatures(channelId, ADVERTISED_FEATURES_JSON);
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        try {
            BodyPartPreview.clearSync();
        } catch (Throwable ignored) {
            // cache is best-effort
        }
    }

    public static boolean isRegistered(String channelId) {
        return channelId != null && CHANNELS.containsKey(channelId);
    }

    /**
     * Send a click on the channel that opened the screen. Echoes screenId / actionId / argsJson
     * verbatim — no client-side filtering.
     */
    public static void sendAction(String channelId, String screenId, String actionId, String argsJson) {
        BoundChannel bound = CHANNELS.get(channelId);
        if (bound == null) {
            UltraUiBridge.LOGGER.warn(
                    "[kaipath] sendAction dropped: unknown channel={} screenId={} actionId={}",
                    channelId,
                    screenId,
                    actionId);
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        if (mc == null || mc.m_91403_() == null) {
            UltraUiBridge.LOGGER.warn(
                    "[kaipath] sendAction dropped: no connection channel={} screenId={} actionId={}",
                    channelId,
                    screenId,
                    actionId);
            return;
        }
        if (!bound.channel.isRemotePresent(mc.m_91403_().m_104910_())) {
            UltraUiBridge.LOGGER.warn(
                    "[kaipath] sendAction dropped: channel not present on server channel={} screenId={} actionId={}",
                    channelId,
                    screenId,
                    actionId);
            return;
        }
        UltraUiBridge.LOGGER.info(
                "[kaipath] GuiActionPacket → channel={} screenId={} actionId={}",
                channelId,
                screenId,
                actionId);
        bound.channel.sendToServer(new GuiActionPacket(screenId, actionId, argsJson));
    }

    /**
     * Advertise optional render features to the server. Reference default is
     * {@link #ADVERTISED_FEATURES_JSON}. Safe to call repeatedly.
     */
    public static void advertiseFeatures(String channelId, String featuresJson) {
        BoundChannel bound = CHANNELS.get(channelId);
        if (bound == null) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        if (mc == null || mc.m_91403_() == null) {
            return;
        }
        if (!bound.channel.isRemotePresent(mc.m_91403_().m_104910_())) {
            return;
        }
        String json = featuresJson == null || featuresJson.isBlank() ? "[]" : featuresJson;
        UltraUiBridge.LOGGER.info(
                "[kaipath] GuiFeaturesPacket → channel={} features={}", channelId, json);
        bound.channel.sendToServer(new GuiFeaturesPacket(json));
    }

    private static final class BoundChannel {
        final String channelId;
        final SimpleChannel channel;

        BoundChannel(String channelId, SimpleChannel channel) {
            this.channelId = channelId;
            this.channel = channel;
        }
    }
}
