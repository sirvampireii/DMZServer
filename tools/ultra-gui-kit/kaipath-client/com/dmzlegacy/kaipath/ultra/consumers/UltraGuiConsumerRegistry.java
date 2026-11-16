package com.dmzlegacy.kaipath.ultra.consumers;

import com.dmzlegacy.kaipath.ultra.UltraUiBridge;
import com.dmzlegacy.kaipath.ultra.consumers.legacymechanics.BodyPartPreview;
import com.dmzlegacy.kaipath.ultra.consumers.legacymechanics.GuiActionPacket;
import com.dmzlegacy.kaipath.ultra.consumers.legacymechanics.GuiFeaturesPacket;
import com.dmzlegacy.kaipath.ultra.consumers.legacymechanics.GuiModelSyncPacket;
import com.dmzlegacy.kaipath.ultra.consumers.legacymechanics.GuiOpenPacket;
import com.dmzlegacy.kaipath.ultra.consumers.legacymechanics.LmScreens;
import io.netty.buffer.Unpooled;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
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
    /**
     * Partner server code running in this JVM (singleplayer, LAN host, or a partner installed on
     * both sides) that linked instead of registering its own channel ({@link #linkServer}).
     * Clicks and feature lists that reach the integrated server on a KaiPath channel go here.
     */
    private static final Map<String, ServerLink> LINKS = new ConcurrentHashMap<>();
    /**
     * Namespaces the connected server announced on {@code kaipath:partners} that have no local
     * channel (server-only partners not in the seed or config). Their packets are read raw
     * ({@link #handleRaw}) and clicks go back raw. Cleared on logout.
     */
    private static final Set<String> RAW = ConcurrentHashMap.newKeySet();

    private record ServerLink(BiConsumer<Object, String[]> onAction, BiConsumer<Object, String> onFeatures) {}

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
        SimpleChannel channel;
        try {
            channel = NetworkRegistry.newSimpleChannel(
                    new ResourceLocation(ns, CHANNEL_PATH),
                    () -> proto,
                    accept,
                    accept);
        } catch (RuntimeException e) {
            // Another mod in this game already owns <ns>:gui: a partner installed on both sides
            // that registered its own channel. Forge allows one per name, so do not crash the
            // game; that partner should link instead (UltraGuiLink in the server kit).
            UltraUiBridge.LOGGER.warn(
                    "[kaipath] Ultra GUI channel {} is already registered by another mod ({}); KaiPath will "
                            + "not draw its menus. The partner should call UltraGuiLink.link instead of "
                            + "registering {} itself when KaiPath is installed.",
                    channelId,
                    e.getMessage(),
                    channelId);
            return;
        }
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
                .consumerMainThread((packet, ctx) -> {
                    NetworkEvent.Context c = ctx.get();
                    // Integrated server (singleplayer / LAN host): hand the click to the linked partner.
                    if (c.getDirection().getReceptionSide().isServer()) {
                        ServerLink link = LINKS.get(channelId);
                        if (link != null && link.onAction() != null) {
                            link.onAction().accept(c.getSender(),
                                    new String[] {packet.screenId(), packet.actionId(), packet.argsJson()});
                        }
                    }
                    c.setPacketHandled(true);
                })
                .add();
        channel.messageBuilder(GuiFeaturesPacket.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GuiFeaturesPacket::encode)
                .decoder(GuiFeaturesPacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    NetworkEvent.Context c = ctx.get();
                    if (c.getDirection().getReceptionSide().isServer()) {
                        ServerLink link = LINKS.get(channelId);
                        if (link != null && link.onFeatures() != null) {
                            link.onFeatures().accept(c.getSender(), packet.featuresJson());
                        }
                    }
                    c.setPacketHandled(true);
                })
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
        RAW.clear();
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
        String rawNs = rawNamespace(channelId);
        if (bound == null && rawNs != null) {
            UltraUiBridge.LOGGER.info(
                    "[kaipath] GuiActionPacket (raw) → channel={} screenId={} actionId={}", channelId, screenId, actionId);
            sendRaw(rawNs, 1, b -> GuiActionPacket.encode(new GuiActionPacket(screenId, actionId, argsJson), b));
            return;
        }
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

    // ---- Partner server code in this JVM (singleplayer, LAN host, both-sides partners) ----

    /**
     * Called by a partner's server code (through {@code UltraGuiLink} in the server kit, by
     * reflection, so the partner needs no compile dependency on KaiPath) when KaiPath is in the
     * same game. KaiPath owns {@code <modId>:gui} here; the partner must not register it too.
     *
     * @param onAction   {@code (ServerPlayer, [screenId, actionId, argsJson])} for every click
     * @param onFeatures {@code (ServerPlayer, featuresJson)} for every advertise (may be null)
     * @return whether the channel is registered and linked
     */
    public static boolean linkServer(
            String modId, BiConsumer<Object, String[]> onAction, BiConsumer<Object, String> onFeatures) {
        String ns = UltraGuiPartners.sanitizeModId(modId);
        // KaiPath owns partner channels only in a game with a client (it is also installed on dedicated
        // servers for its own kaipath:main, but registers no partner channels there): the partner
        // registers its own channel on a dedicated server.
        if (ns.isEmpty() || !ownsPartnerChannels()) {
            return false;
        }
        registerClientChannel(ns);
        String channelId = ns + ":" + CHANNEL_PATH;
        if (!CHANNELS.containsKey(channelId)) {
            return false;
        }
        LINKS.put(channelId, new ServerLink(onAction, onFeatures));
        UltraUiBridge.LOGGER.info("[kaipath] Ultra GUI partner {} linked its server side to {}", ns, channelId);
        return true;
    }

    /** Whether KaiPath registers partner channels in this game: only where there is a client. */
    public static boolean ownsPartnerChannels() {
        try {
            return net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Send an open from linked partner server code: {@code wire} is the partner's
     * {@code UltraGuiOpenPacket} encoded with its own codec (same wire format by protocol).
     */
    public static boolean sendOpenBytes(String modId, Object player, byte[] wire) {
        BoundChannel bound = CHANNELS.get(UltraGuiPartners.sanitizeModId(modId) + ":" + CHANNEL_PATH);
        if (bound == null || !(player instanceof ServerPlayer target) || wire == null) {
            return false;
        }
        GuiOpenPacket packet = GuiOpenPacket.decode(new FriendlyByteBuf(Unpooled.wrappedBuffer(wire)));
        bound.channel.send(PacketDistributor.PLAYER.with(() -> target), packet);
        return true;
    }

    /** Message 3 from linked partner server code ({@code UltraGuiModelSyncPacket} bytes). */
    public static boolean sendModelSyncBytes(String modId, Object player, byte[] wire) {
        BoundChannel bound = CHANNELS.get(UltraGuiPartners.sanitizeModId(modId) + ":" + CHANNEL_PATH);
        if (bound == null || !(player instanceof ServerPlayer target) || wire == null) {
            return false;
        }
        GuiModelSyncPacket packet = GuiModelSyncPacket.decode(new FriendlyByteBuf(Unpooled.wrappedBuffer(wire)));
        bound.channel.send(PacketDistributor.PLAYER.with(() -> target), packet);
        return true;
    }

    // ---- Server-only partners announced on kaipath:partners (no restart, no config) ----

    /**
     * The connected server says {@code modId} speaks this protocol ({@code UltraGuiAnnounce}).
     * A registered channel just gets its advertise; otherwise the namespace is read raw for the
     * rest of the session.
     */
    public static void acceptAnnounced(String modId) {
        String ns = UltraGuiPartners.sanitizeModId(modId);
        if (ns.isEmpty()) {
            return;
        }
        String channelId = ns + ":" + CHANNEL_PATH;
        if (CHANNELS.containsKey(channelId)) {
            advertiseFeatures(channelId, ADVERTISED_FEATURES_JSON);
            return;
        }
        if (RAW.add(ns)) {
            UltraUiBridge.LOGGER.info("[kaipath] Ultra GUI partner {} announced by the server; reading {} directly",
                    ns, channelId);
            sendRaw(ns, 2, b -> GuiFeaturesPacket.encode(new GuiFeaturesPacket(ADVERTISED_FEATURES_JSON), b));
        }
    }

    /** Whether packets on {@code <ns>:gui} are read raw (announced, no registered channel). */
    public static boolean isRawPartner(String namespace, String path) {
        return CHANNEL_PATH.equals(path) && namespace != null && RAW.contains(namespace)
                && !CHANNELS.containsKey(namespace + ":" + CHANNEL_PATH);
    }

    /**
     * One raw SimpleChannel frame from {@code <ns>:gui}: one unsigned byte message index, then the
     * message (same codecs as the registered path). Main thread.
     */
    public static void handleRaw(String ns, byte[] frame) {
        String channelId = ns + ":" + CHANNEL_PATH;
        try {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(frame));
            int index = buf.readUnsignedByte();
            if (index == 0) {
                LmScreens.open(GuiOpenPacket.decode(buf), channelId);
            } else if (index == 3) {
                GuiModelSyncPacket sync = GuiModelSyncPacket.decode(buf);
                BodyPartPreview.applySync(channelId, sync.partsJson(), sync.reason());
            }
        } catch (Throwable t) {
            UltraUiBridge.LOGGER.warn("[kaipath] raw Ultra GUI frame on {} could not be read: {}", channelId, t.toString());
        }
    }

    private static String rawNamespace(String channelId) {
        if (channelId == null) {
            return null;
        }
        int colon = channelId.indexOf(':');
        String ns = colon < 0 ? channelId : channelId.substring(0, colon);
        String path = colon < 0 ? CHANNEL_PATH : channelId.substring(colon + 1);
        return isRawPartner(ns, path) ? ns : null;
    }

    /** One SimpleChannel frame as a plain custom payload: index byte, then the message body. */
    private static void sendRaw(String ns, int index, Consumer<FriendlyByteBuf> body) {
        Minecraft mc = Minecraft.m_91087_();
        ClientPacketListener conn = mc == null ? null : mc.m_91403_();
        if (conn == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(index);
        body.accept(buf);
        conn.m_104955_(new ServerboundCustomPayloadPacket(new ResourceLocation(ns, CHANNEL_PATH), buf));
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
