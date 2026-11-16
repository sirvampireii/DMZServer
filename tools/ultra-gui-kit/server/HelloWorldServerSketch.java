package com.example.ultragui;

/**
 * Sketch only — not compiled by this repo. Minimal server loop after you copy
 * {@link UltraGuiOpenPacket} / {@link UltraGuiActionPacket} / {@link UltraGuiFeaturesPacket} /
 * {@link UltraGuiChannel} into <b>your</b> Forge mod (any {@code modId} — zero coupling to
 * any production partner).
 *
 * <p>JSON twin: {@code BridgeFutureFieldsFixture.json}. Buttons use the shape
 * LegacyMechanics actually sends: {@code id}, {@code label}, {@code style}, {@code x},
 * {@code y}, {@code w}, {@code h}, {@code pad}, optional {@code footer}, optional {@code icon},
 * {@code args}. {@code icon} is an item id ({@code minecraft:emerald}) or a texture path
 * ({@code minecraft:textures/item/emerald.png}). KaiPath 1.3.22 resolves either.
 * Panel {@code palette} and {@code frame} stay on {@code layoutJson}. {@code frame} only switches the
 * framed chrome on (KaiPath binds no texture from it): use your own namespace, never another mod's. Do not put a
 * palette, click sound, or skin on a button, and do not set a portrait frame on
 * {@code playerModel} (a non-empty id paints a border around the preview).
 *
 * <pre>
 * // 1. Link first. In a game that also runs KaiPath (singleplayer, LAN host, your mod installed on
 * //    clients) KaiPath owns MOD_ID:gui: registering it too crashes the game at startup.
 * boolean linked = UltraGuiLink.link(MOD_ID,
 *     (player, a) -> onClick((ServerPlayer) player, a[1]),
 *     (player, featuresJson) -> FEATURES.put(((ServerPlayer) player).getUUID(), featuresJson));
 * if (linked) return;   // send opens with UltraGuiLink.sendOpen (see UltraGuiLink)
 *
 * // 2. Otherwise register your own channel (dedicated server, or a game without KaiPath).
 * //    Accept clients WITHOUT the channel (remote -> true / acceptMissingOr): a strict predicate
 * //    kicks every player who does not run KaiPath.
 * SimpleChannel ch = NetworkRegistry.newSimpleChannel(
 *     new ResourceLocation(MOD_ID, UltraGuiChannel.PATH),
 *     () -> UltraGuiChannel.PROTOCOL_VERSION,
 *     remote -> true,
 *     remote -> true);
 * ch.messageBuilder(UltraGuiOpenPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
 *   .encoder(UltraGuiOpenPacket::encode).decoder(UltraGuiOpenPacket::decode)
 *   .consumerMainThread((m, ctx) -> ctx.get().setPacketHandled(true)).add();
 * ch.messageBuilder(UltraGuiActionPacket.class, 1, NetworkDirection.PLAY_TO_SERVER)
 *   .encoder(UltraGuiActionPacket::encode).decoder(UltraGuiActionPacket::decode)
 *   .consumerNetworkThread((m, ctx) -> {
 *       ServerPlayer p = ctx.get().getSender();
 *       ctx.get().enqueueWork(() -> onClick(p, m));
 *       ctx.get().setPacketHandled(true);
 *   }).add();
 * ch.messageBuilder(UltraGuiFeaturesPacket.class, 2, NetworkDirection.PLAY_TO_SERVER)
 *   .encoder(UltraGuiFeaturesPacket::encode).decoder(UltraGuiFeaturesPacket::decode)
 *   .consumerMainThread((m, ctx) -> {
 *       FEATURES.put(ctx.get().getSender().getUUID(), m.featuresJson());   // this player runs KaiPath
 *       ctx.get().setPacketHandled(true);
 *   }).add();
 * ch.messageBuilder(UltraGuiModelSyncPacket.class, 3, NetworkDirection.PLAY_TO_CLIENT)
 *   .encoder(UltraGuiModelSyncPacket::encode).decoder(UltraGuiModelSyncPacket::decode)
 *   .consumerMainThread((m, ctx) -> ctx.get().setPacketHandled(true)).add();
 *
 * // World body-parts sync (message 3): ONLY to clients that advertised "bodyPartsSync"
 * // via message 2. Track advertisements per connection; an unregistered message id
 * // aborts the connection (KaiPath <= 1.3.29 registers 0-2 only).
 * if (clientFeatures.get(player).contains("bodyPartsSync")) {
 *     String partsJson = "{\"schema\":1,\"subject\":\"local\",\"bodyParts\":["
 *         + "{\"id\":\"saiyan_tail\",\"source\":\"dmzultra\",\"slot\":\"tail\"}],\"extras\":[]}";
 *     ch.send(PacketDistributor.PLAYER.with(() -> player),
 *         new UltraGuiModelSyncPacket(partsJson, UltraGuiModelSyncPacket.REASON_LOGIN));
 * }
 *
 * // 3. Server-only mods: announce at login so KaiPath reads your channel without a KaiPath release
 * //    or player config (harmless for clients without KaiPath, and for mods on both sides).
 * void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
 *     if (e.getEntity() instanceof ServerPlayer p) {
 *         p.connection.send(new ClientboundCustomPayloadPacket(
 *             new ResourceLocation(UltraGuiAnnounce.NAMESPACE, UltraGuiAnnounce.PATH),
 *             new FriendlyByteBuf(Unpooled.wrappedBuffer(UltraGuiAnnounce.payload(MOD_ID)))));
 *     }
 * }
 *
 * // 4. Open only for players whose client advertised (message 2). Anyone else has no Ultra renderer:
 * //    tell them in chat instead of sending a packet that is silently dropped.
 * if (!FEATURES.containsKey(player.getUUID())) {
 *     player.sendSystemMessage(Component.literal("Install KaiPath to open this menu."));
 *     return;
 * }
 *
 * // Open — plain ASCII. Button objects are style + geometry + args only.
 * String buttons = "["
 *     + "{\"id\":\"@myshop:do:ping\",\"label\":\"Ping\",\"style\":\"confirm\","
 *     + "\"icon\":\"minecraft:emerald\","
 *     + "\"x\":20,\"y\":40,\"w\":150,\"h\":18,\"pad\":8,"
 *     + "\"args\":{\"screen\":\"hello\",\"page\":\"main\"}},"
 *     + "{\"id\":\"@myshop:nav:hub\",\"label\":\"Main menu\",\"style\":\"dim\","
 *     + "\"x\":174,\"y\":272,\"w\":150,\"h\":18,\"pad\":8,\"footer\":true,"
 *     + "\"args\":{\"screen\":\"hello\",\"page\":\"main\"}}"
 *     + "]";
 * String inputs = "["
 *     + "{\"id\":\"@myshop:book:guide\",\"type\":\"paged_book\",\"x\":8,\"y\":70,"
 *     + "\"w\":220,\"h\":120,\"page\":0,\"pages\":["
 *     + "{\"title\":\"Welcome\",\"lines\":[\"Page one.\"]},"
 *     + "{\"title\":\"Tips\",\"lines\":[\"Page two.\"]}]}"
 *     + "]";
 * String layout = "{"
 *     + "\"center\":true,\"margin\":10,\"titleAlign\":\"center\","
 *     + "\"headerBottom\":42,\"palette\":\"gold\",\"frame\":\"myshop:panel\","
 *     + "\"playerModel\":{\"x\":248,\"y\":60,\"width\":86,\"height\":120,"
 *     + "\"scale\":1.0,\"rotate\":true,"
 *     + "\"parts\":{\"schema\":1,\"subject\":\"local\",\"bodyParts\":["
 *     + "{\"id\":\"shop_tail\",\"source\":\"myshop\",\"slot\":\"tail\"}]}}"
 *     + "}";
 * UltraGuiOpenPacket open = new UltraGuiOpenPacket(
 *     "hello", "My Shop", "main", "[]", "", "[\"Welcome.\"]",
 *     buttons, 350, 300, layout, inputs);
 * ch.send(PacketDistributor.PLAYER.with(() -> player), open);
 *
 * void onClick(ServerPlayer p, UltraGuiActionPacket m) {
 *     if ("@myshop:do:ping".equals(m.actionId())) {
 *         p.sendSystemMessage(Component.literal("Pong"));
 *     }
 * }
 * </pre>
 */
public final class HelloWorldServerSketch {
    private HelloWorldServerSketch() {}
}
