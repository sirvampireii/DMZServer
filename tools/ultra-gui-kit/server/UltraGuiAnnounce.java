package com.example.ultragui;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Tell KaiPath, at login, that your <b>server-only</b> mod speaks this protocol. From
 * {@code tools/ultra-gui-kit/server/}: copy into your mod and change the package.
 *
 * <p><b>Why:</b> a server-only mod has no jar on the player's client, and Forge only lets the client
 * register channels while the game starts, so KaiPath cannot know {@code <modid>:gui} in advance. The
 * old fixes were a KaiPath release (the built-in seed) or every player editing
 * {@code config/kaipath-ultra-partners.txt}. With this, KaiPath learns your id from the server and
 * reads your channel directly for that session; your Forge channel on the server is unchanged.
 *
 * <pre>
 * &#64;SubscribeEvent
 * public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
 *     if (e.getEntity() instanceof ServerPlayer p) {
 *         p.connection.send(new ClientboundCustomPayloadPacket(
 *             new ResourceLocation(UltraGuiAnnounce.NAMESPACE, UltraGuiAnnounce.PATH),
 *             new FriendlyByteBuf(Unpooled.wrappedBuffer(UltraGuiAnnounce.payload(MOD_ID)))));
 *     }
 * }
 * </pre>
 *
 * <p>Clients without KaiPath ignore the payload (an unknown custom payload is dropped). Send it before
 * your first open. Mods that are installed on the client too, or listed in KaiPath's seed or config, do
 * not need it, but sending it is harmless.
 */
public final class UltraGuiAnnounce {
    public static final String NAMESPACE = "kaipath";
    public static final String PATH = "partners";

    private UltraGuiAnnounce() {}

    /** One announce payload: {@code FriendlyByteBuf.writeUtf(modId)} (VarInt byte length, UTF-8). */
    public static byte[] payload(String modId) {
        if (modId == null || modId.isBlank()) {
            throw new IllegalArgumentException("modId required");
        }
        byte[] utf = modId.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(utf.length + 5);
        int v = utf.length;
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
        out.write(utf, 0, utf.length);
        return out.toByteArray();
    }
}
