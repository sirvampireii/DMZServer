package com.example.ultragui;

import java.lang.reflect.Method;
import java.util.function.BiConsumer;

/**
 * Use KaiPath's channel when KaiPath is in the same game. From {@code tools/ultra-gui-kit/server/}:
 * copy into your mod and change the package. No compile dependency on KaiPath (reflection only).
 *
 * <p><b>Why:</b> Forge allows one channel per name in a game. On a dedicated server your mod
 * registers {@code <modid>:gui} (KaiPath is a client mod and is not there). But in singleplayer, on a
 * LAN host, or when your mod is installed on players' clients too, your mod and KaiPath run in the
 * same game: if both register {@code <modid>:gui} the game crashes at startup, and whichever registers
 * first owns the handlers, so clicks never reach your code.
 *
 * <p><b>Rule:</b> link first; register your own channel only when linking returns false.
 *
 * <pre>
 * boolean linked = UltraGuiLink.link(MOD_ID,
 *     (player, a) -> onClick((ServerPlayer) player, a[0], a[1], a[2]),
 *     (player, featuresJson) -> rememberFeatures((ServerPlayer) player, featuresJson));
 * if (!linked) {
 *     registerOwnChannel();   // dedicated server, or a game without KaiPath
 * }
 *
 * // Sending an open: your channel when you registered one, else through KaiPath.
 * if (ownChannel != null) {
 *     ownChannel.send(PacketDistributor.PLAYER.with(() -> player), open);
 * } else {
 *     FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
 *     UltraGuiOpenPacket.encode(open, buf);
 *     byte[] wire = new byte[buf.readableBytes()];
 *     buf.readBytes(wire);
 *     UltraGuiLink.sendOpen(MOD_ID, player, wire);
 * }
 * </pre>
 *
 * <p>The bytes are your own codec's output: the wire format is the protocol, so KaiPath decodes them
 * with its codec. Do the same for message 3 with {@link #sendModelSync}.
 */
public final class UltraGuiLink {
    /** KaiPath's partner registry (client mod). Present only where KaiPath is installed. */
    static final String REGISTRY = "com.dmzlegacy.kaipath.ultra.consumers.UltraGuiConsumerRegistry";

    private UltraGuiLink() {}

    /**
     * Whether KaiPath owns {@code <modid>:gui} in this game: KaiPath is loaded and this game has a client
     * (singleplayer, LAN host, a client with your mod installed). False on a dedicated server, even one
     * with KaiPath installed: there your mod registers its own channel.
     */
    public static boolean kaiPathPresent() {
        return Boolean.TRUE.equals(call(method("ownsPartnerChannels")));
    }

    /**
     * Hand your server-side handlers to KaiPath for {@code <modId>:gui}. {@code onAction} gets
     * {@code (ServerPlayer, [screenId, actionId, argsJson])}; {@code onFeatures} (may be null) gets
     * {@code (ServerPlayer, featuresJson)}. Call where you would register your channel (mod
     * construction or common setup). Returns false when KaiPath is absent or refused.
     */
    public static boolean link(String modId, BiConsumer<Object, String[]> onAction, BiConsumer<Object, String> onFeatures) {
        Object ok = call(method("linkServer", String.class, BiConsumer.class, BiConsumer.class), modId, onAction, onFeatures);
        return Boolean.TRUE.equals(ok);
    }

    /** Send an encoded {@code UltraGuiOpenPacket} to {@code serverPlayer} through KaiPath's channel. */
    public static boolean sendOpen(String modId, Object serverPlayer, byte[] wire) {
        return Boolean.TRUE.equals(call(method("sendOpenBytes", String.class, Object.class, byte[].class), modId, serverPlayer, wire));
    }

    /** Send an encoded {@code UltraGuiModelSyncPacket} (message 3) through KaiPath's channel. */
    public static boolean sendModelSync(String modId, Object serverPlayer, byte[] wire) {
        return Boolean.TRUE.equals(call(method("sendModelSyncBytes", String.class, Object.class, byte[].class), modId, serverPlayer, wire));
    }

    private static Class<?> registry() {
        try {
            return Class.forName(REGISTRY, false, UltraGuiLink.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method method(String name, Class<?>... types) {
        Class<?> r = registry();
        if (r == null) {
            return null;
        }
        try {
            return r.getMethod(name, types);
        } catch (Throwable t) {
            return null;   // an older KaiPath without the link API
        }
    }

    private static Object call(Method m, Object... args) {
        if (m == null) {
            return null;
        }
        try {
            return m.invoke(null, args);
        } catch (Throwable t) {
            return null;
        }
    }
}
