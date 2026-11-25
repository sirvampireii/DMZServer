package com.dbzlegacy.adaptivedifficulty.net.gui;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ConnectionData;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * LM 4.7.58: the LM client's own menu channel, {@code legacymechanics:native_ui} protocol "1".
 *
 * <p>Both sides register it (the server from {@link LmGuiNetwork#register}, the client from its
 * boot class). Neither side requires it: a client without the LM client joins as before, and the
 * LM client joins servers without LM. The server reads the client's channel list from the Forge
 * login handshake, so it knows at login whether the LM client is there.
 *
 * <p>Routing ({@link #route}): a player with the LM client gets each menu here, unless the menu
 * needs something the client does not draw; then it goes over the KaiPath bridge (when the player
 * has KaiPath). A menu is never sent on both channels.
 *
 * <p>Isolation: this class is loaded on the client too. It never names a client class, and it
 * reaches {@link LmGuiNetwork} only inside server-side handler bodies, because loading
 * {@link LmGuiNetwork} registers {@code legacymechanics:gui}, which KaiPath owns on a client.
 */
public final class LmNativeNetwork {
    private LmNativeNetwork() {}

    public static final ResourceLocation CHANNEL_ID =
            new ResourceLocation(AdaptiveDifficultyMod.MOD_ID, LmNativeProtocol.CHANNEL_PATH);

    /** Where one menu goes. */
    public enum Route {
        /** The LM client draws it. */
        NATIVE,
        /** KaiPath draws it (no LM client, or the LM client lacks a feature this menu needs). */
        BRIDGE,
        /** LM client without a needed feature and no KaiPath: tell the player instead. */
        REFUSE
    }

    private static SimpleChannel channel;
    private static volatile Consumer<GuiOpenPacket> clientOpener;
    private static volatile Consumer<NativePrefsPacket> clientPrefs;
    private static volatile Consumer<NativeHudPacket> clientHud;
    private static final Map<UUID, Set<String>> FEATURES = new ConcurrentHashMap<>();
    private static final Map<UUID, String> CLIENT_VERSION = new ConcurrentHashMap<>();

    /** Server: register the channel. Safe to call twice. */
    public static void register() {
        register(null);
    }

    /**
     * Register the channel (once per game). The client passes the callback that opens a menu; it runs
     * on the client main thread.
     */
    public static synchronized void register(Consumer<GuiOpenPacket> opener) {
        if (opener != null) {
            clientOpener = opener;
        }
        if (channel != null) {
            return;
        }
        SimpleChannel ch = NetworkRegistry.newSimpleChannel(
                CHANNEL_ID,
                () -> LmNativeProtocol.PROTOCOL_VERSION,
                NetworkRegistry.acceptMissingOr(LmNativeProtocol.PROTOCOL_VERSION),
                NetworkRegistry.acceptMissingOr(LmNativeProtocol.PROTOCOL_VERSION));
        ch.messageBuilder(GuiOpenPacket.class, LmNativeProtocol.MSG_OPEN, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GuiOpenPacket::encode)
                .decoder(GuiOpenPacket::decode)
                .consumerMainThread(LmNativeNetwork::onClientOpen)
                .add();
        ch.messageBuilder(GuiActionPacket.class, LmNativeProtocol.MSG_ACTION, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GuiActionPacket::encode)
                .decoder(GuiActionPacket::decode)
                .consumerNetworkThread(LmNativeNetwork::onServerAction)
                .add();
        ch.messageBuilder(NativeHelloPacket.class, LmNativeProtocol.MSG_HELLO, NetworkDirection.PLAY_TO_SERVER)
                .encoder(NativeHelloPacket::encode)
                .decoder(NativeHelloPacket::decode)
                .consumerNetworkThread(LmNativeNetwork::onServerHello)
                .add();
        // 4.7.62: the player's menu look, only to clients whose hello listed menuSkins.
        ch.messageBuilder(NativePrefsPacket.class, LmNativeProtocol.MSG_PREFS, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(NativePrefsPacket::encode)
                .decoder(NativePrefsPacket::decode)
                .consumerMainThread(LmNativeNetwork::onClientPrefs)
                .add();
        // 4.7.72: native HUD elements, only to clients whose hello listed hud.
        ch.messageBuilder(NativeHudPacket.class, LmNativeProtocol.MSG_HUD, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(NativeHudPacket::encode)
                .decoder(NativeHudPacket::decode)
                .consumerMainThread(LmNativeNetwork::onClientHud)
                .add();
        channel = ch;
        AdaptiveDifficultyMod.LOGGER.info(
                "[{}] native menu channel {} protocol={} (msg0 open S>C, msg1 action C>S, msg2 hello C>S, msg3 look S>C,"
                        + " msg4 hud S>C)",
                AdaptiveDifficultyMod.MOD_ID, CHANNEL_ID, LmNativeProtocol.PROTOCOL_VERSION);
    }

    // ------------------------------------------------------------------ client side (no client classes)

    private static void onClientOpen(GuiOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        Consumer<GuiOpenPacket> opener = clientOpener;
        if (opener != null && msg != null) {
            opener.accept(msg);
        }
        ctx.get().setPacketHandled(true);
    }

    /** Client: true when the server's Forge handshake listed LM and this channel. */
    public static boolean serverHasChannel(Connection connection) {
        return channel != null && handshakeHasLm(connection);
    }

    /** Client: send a click (byte-identical to a bridge GuiActionPacket). */
    public static void sendAction(String screenId, String actionId, String argsJson) {
        if (channel != null) {
            channel.sendToServer(new GuiActionPacket(screenId, actionId, argsJson));
        }
    }

    /** Client: tell the server this client draws menus natively, and what it draws. */
    public static void sendHello(String clientVersion) {
        if (channel != null) {
            channel.sendToServer(new NativeHelloPacket(clientVersion,
                    LmNativeProtocol.clientFeaturesJson(LmNativeProtocol.clientDrawsForms(),
                            LmNativeProtocol.clientDrawsBodyParts())));
        }
    }

    // ------------------------------------------------------------------ server side

    /** Client: where the menu look goes (the LM client's MenuLook). */
    public static void onPrefs(Consumer<NativePrefsPacket> consumer) {
        clientPrefs = consumer;
    }

    /** Client: where HUD elements go (the LM client's HudOverlay). */
    public static void onHud(Consumer<NativeHudPacket> consumer) {
        clientHud = consumer;
    }

    private static void onClientHud(NativeHudPacket msg, Supplier<NetworkEvent.Context> ctx) {
        Consumer<NativeHudPacket> c = clientHud;
        if (c != null && msg != null) {
            c.accept(msg);
        }
        ctx.get().setPacketHandled(true);
    }

    private static void onClientPrefs(NativePrefsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        Consumer<NativePrefsPacket> c = clientPrefs;
        if (c != null && msg != null) {
            c.accept(msg);
        }
        ctx.get().setPacketHandled(true);
    }

    private static void onServerAction(GuiActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        // Same path as a KaiPath click: log, then dispatch on the main thread.
        LmGuiNetwork.handleGuiAction(msg, ctx);
    }

    private static void onServerHello(NativeHelloPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer sender = context.getSender();
        if (sender != null && msg != null) {
            Set<String> features = LmNativeProtocol.parseFeatures(msg.featuresJson());
            FEATURES.put(sender.m_20148_(), features);
            CLIENT_VERSION.put(sender.m_20148_(), msg.clientVersion());
            LmLog.event("[{}] native client hello player={} lmClient={} server={} features={}",
                    AdaptiveDifficultyMod.MOD_ID, sender.m_7755_().getString(), msg.clientVersion(),
                    AdaptiveDifficultyMod.VERSION, features);
            // 4.7.62: the client draws in the player's skin from the first menu on.
            context.enqueueWork(() -> sendPrefs(sender));
            // 4.7.87: login notices that waited to learn whether this client takes the HUD.
            context.enqueueWork(() -> com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.helloArrived(sender));
        }
        context.setPacketHandled(true);
    }

    /**
     * True when this player's client has the LM client: its Forge login handshake listed the
     * {@code legacymechanics} mod and the {@code legacymechanics:native_ui} channel.
     *
     * <p>Only the handshake counts. Forge's channel "remote present" check also trusts the
     * {@code minecraft:register} list, and a KaiPath-only client can carry LM channel names there
     * (4.7.58 test: it routed a KaiPath-only player's menus to a channel it does not have).
     */
    public static boolean hasNativeClient(ServerPlayer player) {
        if (player == null || channel == null) {
            return false;
        }
        return handshakeHasLm(LmGuiNetwork.connectionOf(player));
    }

    /** The Forge handshake data of {@code connection} lists the LM mod and the native channel. */
    static boolean handshakeHasLm(Connection connection) {
        if (connection == null) {
            return false;
        }
        try {
            ConnectionData data = NetworkHooks.getConnectionData(connection);
            return data != null && data.getChannels().containsKey(CHANNEL_ID)
                    && data.getModList().contains(AdaptiveDifficultyMod.MOD_ID);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** What the player's LM client said it draws (the protocol-1 list until its hello arrives). */
    public static Set<String> features(ServerPlayer player) {
        Set<String> f = player == null ? null : FEATURES.get(player.m_20148_());
        return f != null ? f : LmNativeProtocol.parseFeatures("");
    }

    /** 4.7.87: this player's LM client hello has arrived (its feature list is known). */
    public static boolean helloArrived(ServerPlayer player) {
        return player != null && FEATURES.containsKey(player.m_20148_());
    }

    /** LM client version from the hello, or "" before it arrives. */
    public static String clientVersion(ServerPlayer player) {
        String v = player == null ? null : CLIENT_VERSION.get(player.m_20148_());
        return v == null ? "" : v;
    }

    /** Where this menu goes for this player. Never both. */
    public static Route route(ServerPlayer player, GuiOpenPacket packet) {
        if (!hasNativeClient(player)) {
            return Route.BRIDGE;
        }
        Set<String> supported = features(player);
        LmNativeProtocol.Needs needs = LmNativeProtocol.analyze(packet, id -> isOnline(player, id));
        if (needs.metBy(supported)) {
            return Route.NATIVE;
        }
        boolean kaipath = LmGuiNetwork.clientHasKaiPath(player);
        LmLog.event("[{}] native menu fallback player={} screen={} page={} missing={} kaipath={}",
                AdaptiveDifficultyMod.MOD_ID, player.m_7755_().getString(), packet.screenId(), packet.pageId(),
                needs.missing(supported), kaipath);
        return decide(true, needs, supported, kaipath);
    }

    /**
     * The routing rule, without a live player (the boot self-test checks every branch):
     * <ul>
     *   <li>no LM client: the KaiPath bridge, exactly as 4.7.57;
     *   <li>LM client that draws everything the menu uses: native;
     *   <li>LM client missing something, KaiPath present: the bridge (never half a menu);
     *   <li>LM client missing only a preview hint, no KaiPath: native (the base figure still draws);
     *   <li>LM client missing a widget, no KaiPath: refused with a plain message.
     * </ul>
     */
    public static Route decide(boolean nativeClient, LmNativeProtocol.Needs needs, Set<String> supported,
            boolean kaipath) {
        if (!nativeClient) {
            return Route.BRIDGE;
        }
        if (needs.metBy(supported)) {
            return Route.NATIVE;
        }
        if (kaipath) {
            return Route.BRIDGE;
        }
        return needs.hardMetBy(supported) ? Route.NATIVE : Route.REFUSE;
    }

    private static boolean isOnline(ServerPlayer viewer, UUID id) {
        try {
            return viewer.m_20194_() != null && viewer.m_20194_().m_6846_().m_11259_(id) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Server: send one menu on the native channel. */
    public static void sendOpen(ServerPlayer player, GuiOpenPacket packet) {
        if (channel != null && player != null && packet != null) {
            channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    /** True when this player's LM client said it takes message 3 (menu skins). */
    public static boolean takesPrefs(ServerPlayer player) {
        Set<String> f = player == null ? null : FEATURES.get(player.m_20148_());
        return f != null && f.contains(LmNativeProtocol.FEATURE_MENU_SKINS);
    }

    /** 4.7.66: this player's LM client edits a custom skin (many fields per click, live swatch). */
    public static boolean takesCustomSkin(ServerPlayer player) {
        Set<String> f = player == null ? null : FEATURES.get(player.m_20148_());
        return f != null && f.contains(LmNativeProtocol.FEATURE_CUSTOM_SKIN);
    }

    /** 4.7.71: this player's LM client takes the navigation layer (history Back, breadcrumbs, hub search). */
    public static boolean takesNav(ServerPlayer player) {
        Set<String> f = player == null ? null : FEATURES.get(player.m_20148_());
        return f != null && f.contains(LmNativeProtocol.FEATURE_NAV);
    }

    /** 4.7.72: this player's LM client draws the native HUD (message 4). */
    public static boolean takesHud(ServerPlayer player) {
        Set<String> f = player == null ? null : FEATURES.get(player.m_20148_());
        return f != null && f.contains(LmNativeProtocol.FEATURE_HUD);
    }

    /** Server: send one HUD element (callers check {@link #takesHud} first; {@code LmHud} does). */
    public static void sendHud(ServerPlayer player, NativeHudPacket packet) {
        if (channel != null && player != null && packet != null && takesHud(player)) {
            channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    /** 4.7.68: this player's LM client draws pictures in a page's body (the dojo upgrade previews). */
    public static boolean takesPictures(ServerPlayer player) {
        Set<String> f = player == null ? null : FEATURES.get(player.m_20148_());
        return f != null && f.contains(LmNativeProtocol.FEATURE_PICTURES);
    }

    /** 4.7.66: this player's LM client draws the color wheel. */
    public static boolean takesColorWheel(ServerPlayer player) {
        Set<String> f = player == null ? null : FEATURES.get(player.m_20148_());
        return f != null && f.contains(com.dbzlegacy.adaptivedifficulty.gui.ultra.ColorWheel.FEATURE);
    }

    /**
     * Server: send the player's menu look to their LM client (no-op for a client that cannot take it). The skin
     * string is the preset id, or for Custom the palette token (4.7.66, {@code MenuSkins.customToken}): the same
     * message 3 wire, so an LM client before 4.7.66 reads an unknown id and draws Saiyan Royal.
     */
    public static void sendPrefs(ServerPlayer player) {
        if (channel == null || player == null || !takesPrefs(player)) {
            return;
        }
        com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuPrefs.Prefs p =
                com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuPrefs.get(player);
        channel.send(PacketDistributor.PLAYER.with(() -> player),
                new NativePrefsPacket(p.skinOrDefault().id(), p.backgrounds(), p.lite()));
    }

    /** Forget the per-session hello (logout). */
    public static void clear(ServerPlayer player) {
        if (player != null) {
            FEATURES.remove(player.m_20148_());
            CLIENT_VERSION.remove(player.m_20148_());
            com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.forget(player);
            com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraHubScreens.forgetDraft(player);
        }
    }

    /** Plain-language reason shown when {@code requireLmClient} turns a player away. */
    public static String requiredClientMessage() {
        return "This server needs the Legacy Mechanics client mod. Add LegacyMechanics-"
                + AdaptiveDifficultyMod.VERSION + ".jar to your mods folder, then join again.";
    }

    /**
     * {@code requireLmClient}: disconnect a player who joined without the LM client, with an install
     * message. Returns true when the player was turned away. Off (the default) lets everyone in.
     */
    public static boolean rejectWithoutClient(ServerPlayer player, boolean required) {
        if (!required || player == null || hasNativeClient(player)) {
            return false;
        }
        AdaptiveDifficultyMod.LOGGER.info("[{}] requireLmClient: turning away {} (no LM client)",
                AdaptiveDifficultyMod.MOD_ID, player.m_7755_().getString());
        try {
            player.f_8906_.m_9942_(Component.m_237113_(requiredClientMessage()));
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] requireLmClient disconnect failed for {}",
                    AdaptiveDifficultyMod.MOD_ID, player.m_7755_().getString(), t);
        }
        return true;
    }
}
