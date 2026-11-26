package com.dbzlegacy.adaptivedifficulty.net.gui;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraScreenIds;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraScreenSelfTest;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server side of LM menus: the single send point ({@link #sendOpen}), click dispatch, the
 * body-parts world sync and the boot self-tests.
 *
 * <p>LM 4.8.2: the Legacy Mechanics client is mandatory and the KaiPath bridge channel
 * ({@code legacymechanics:gui}) is gone; it is no longer registered. Every menu, click and
 * world sync goes over the LM client's own channel ({@link LmNativeNetwork},
 * {@code legacymechanics:native_ui}). The packet shapes ({@link GuiOpenPacket},
 * {@link GuiActionPacket}, {@link GuiModelSyncPacket}) keep the KaiPath-era wire format.
 * No client screen classes are referenced here.
 */
public final class LmGuiNetwork {
    /** This mod's channel namespace (= Forge mod id). Not part of the packet payload. */
    public static final String CHANNEL_NAMESPACE = AdaptiveDifficultyMod.MOD_ID;
    /** @see UltraGuiProtocol#CHANNEL_PATH */
    public static final String CHANNEL_PATH = UltraGuiProtocol.CHANNEL_PATH;
    /**
     * Wire-format version of the KaiPath-era packet shapes. Must stay equal to
     * {@link UltraGuiProtocol#PROTOCOL_VERSION}. 4.8.2: no channel is registered with it any more
     * (the native channel has its own protocol string, {@link LmNativeProtocol#PROTOCOL_VERSION}).
     */
    public static final String PROTOCOL_VERSION = "1";

    // 4.8.2: the KaiPath bridge channel (legacymechanics:gui), the KaiPath mod-list version probe
    // with its version constants, and the client feature advertisement packet are gone with the bridge.

    private static boolean registered;
    private static Field connectionField;
    /**
     * Players that already received this session's initial {@link GuiModelSyncPacket}
     * (login trigger fires once per session; equip changes always send).
     */
    private static final java.util.Set<java.util.UUID> MODEL_SYNCED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Login syncs scheduled but not yet sent (so repeat advertisements do not stack them). */
    private static final java.util.Set<java.util.UUID> MODEL_SYNC_PENDING =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private LmGuiNetwork() {}

    /**
     * PLAY_TO_SERVER {@link GuiActionPacket} entry on the network thread.
     * Captures the sender, then {@link NetworkEvent.Context#enqueueWork}s main-thread dispatch.
     */
    static void handleGuiAction(GuiActionPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        // Diagnostic: first line — if clicks produce nothing here, the client is not sending.
        ServerPlayer earlySender = null;
        NetworkEvent.Context context = null;
        try {
            context = ctx == null ? null : ctx.get();
            earlySender = context == null ? null : context.getSender();
        } catch (Throwable ignored) {
            // fall through to INFO log with unknowns
        }
        String playerName = earlySender == null ? "<null>" : safeName(earlySender);
        if (msg == null) {
            LmLog.event(
                    "[{}] GuiActionPacket ← player={} screenId=<null-packet> actionId=<null-packet>",
                    AdaptiveDifficultyMod.MOD_ID,
                    playerName);
            if (context != null) {
                context.setPacketHandled(true);
            }
            return;
        }
        LmLog.event(
                "[{}] GuiActionPacket ← player={} screenId={} actionId={}",
                AdaptiveDifficultyMod.MOD_ID,
                playerName,
                msg.screenId(),
                msg.actionId());
        if (msg.actionId() == null || msg.actionId().isBlank()) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] GuiActionPacket empty actionId — full packet screenId={} actionId={} argsJson={} player={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    msg.screenId(),
                    msg.actionId(),
                    msg.argsJson(),
                    playerName);
            if (context != null) {
                context.setPacketHandled(true);
            }
            return;
        }
        if (context == null) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] GuiActionPacket dropped: null NetworkEvent.Context player={} screenId={} actionId={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    playerName,
                    msg.screenId(),
                    msg.actionId());
            return;
        }
        // Must capture before enqueueWork — context sender is only reliable here.
        ServerPlayer sender = earlySender != null ? earlySender : context.getSender();
        context.enqueueWork(() -> GuiActionPacket.dispatchOnMain(sender, msg));
        context.setPacketHandled(true);
    }

    private static String safeName(ServerPlayer player) {
        if (player == null) {
            return "<null>";
        }
        try {
            return player.m_7755_().getString();
        } catch (Throwable ignored) {
            return "<name?>";
        }
    }

    static int buttonCount(GuiOpenPacket packet) {
        if (packet == null) {
            return 0;
        }
        String json = packet.buttonsJson();
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return 0;
        }
        try {
            return JsonParser.parseString(json).getAsJsonArray().size();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * Diagnostic: send a one-button Ultra menu to {@code player}. Click should produce an INFO
     * {@code GuiActionPacket ←} line; ack is handled by {@code do:guitest_ack}.
     */
    public static void sendGuiTest(ServerPlayer player) {
        if (player == null) {
            return;
        }
        String featureSummary = clientSummary(player);
        LmLog.event(
                "[{}] guitest → player={} {} — sending GuiOpenPacket with style=confirm + activeTab=home",
                AdaptiveDifficultyMod.MOD_ID,
                safeName(player),
                featureSummary);
        com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraScreenSpec.of("hub")
                .title("Ultra GUI Test")
                .page("guitest")
                .activeTab("home")
                .tabs("home|Test", "other|Other")
                .line(com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.LABEL
                        + "Features: "
                        + featureSummary)
                .line(com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.DIM
                        + "Ping=confirm (green). Active tab should highlight home.")
                .buttonsAfterLines()
                .button(
                        "do:guitest_ack",
                        com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.confirm("Ping server"),
                        "confirm")
                .button(
                        "do:guitest_ack",
                        com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.danger("Danger look"),
                        "danger")
                .button(
                        "do:guitest_ack",
                        com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.dim("Dim look"),
                        "dim")
                .send(player);
    }

    /**
     * Login probe (runs after {@code requireLmClient} let the player in): whether this connection has
     * the Legacy Mechanics client. 4.8.2: there is no KaiPath bridge, so a player without the LM client
     * (only possible with {@code requireLmClient} off) gets no menus, just the install message.
     */
    public static void logClientGuiCapability(ServerPlayer player) {
        if (player == null) {
            return;
        }
        boolean lmClient = LmNativeNetwork.hasNativeClient(player);
        LmLog.event(
                "[{}] login GUI probe player={} lmClient={} channel={} protocol={} guiBackend={}",
                AdaptiveDifficultyMod.MOD_ID,
                safeName(player),
                lmClient,
                LmNativeNetwork.CHANNEL_ID,
                LmNativeProtocol.PROTOCOL_VERSION,
                com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig.get().guiBackend);
        if (!lmClient) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] player={} joined without the Legacy Mechanics client (requireLmClient is off): "
                            + "menus will not open for them, each one answers with the install message",
                    AdaptiveDifficultyMod.MOD_ID,
                    safeName(player));
        }
    }

    /** One-line client summary for guitest and the send log: LM client version and its hello features. */
    static String clientSummary(ServerPlayer player) {
        if (!LmNativeNetwork.hasNativeClient(player)) {
            return "lmClient=none";
        }
        String v = LmNativeNetwork.clientVersion(player);
        return "lmClient=" + (v.isBlank() ? "?" : v) + " hello=" + LmNativeNetwork.helloArrived(player)
                + " features=" + LmNativeNetwork.features(player);
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        if (!PROTOCOL_VERSION.equals(UltraGuiProtocol.PROTOCOL_VERSION)) {
            throw new IllegalStateException(
                    "LmGuiNetwork.PROTOCOL_VERSION ("
                            + PROTOCOL_VERSION
                            + ") != UltraGuiProtocol.PROTOCOL_VERSION ("
                            + UltraGuiProtocol.PROTOCOL_VERSION
                            + ")");
        }
        // 4.8.2: the KaiPath bridge channel (legacymechanics:gui) is no longer registered. The LM client's own
        // channel (legacymechanics:native_ui) carries every menu, click and body-parts world sync.
        LmNativeNetwork.register();
        validateBuilderScreenIdsAtStartup();
        // 4.7.46: detect the installed DMZ Ultra and pick the body-parts catalog before any
        // screen or model sync reads it; one boot line names both.
        com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraVersion.logAtBoot();
        // 4.7.49 (M-P1-11): a menu layout self-test failure no longer stops the server (a layout
        // change once crashed live at startup). It logs a loud ERROR and the server keeps starting.
        // Test servers start with -Dlm.selftest.strict=true to keep failing fast.
        try {
            UltraScreenSelfTest.runAll();
        } catch (RuntimeException e) {
            if (Boolean.getBoolean("lm.selftest.strict")) {
                throw e;
            }
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] Ultra GUI self-test FAILED at boot; menus may look wrong, the server keeps running: {}",
                    AdaptiveDifficultyMod.MOD_ID, e.getMessage(), e);
        }
        runPacketRoundTripSelfTest();
        // 4.7.58: native client channel, codecs, routing and scale-to-fit pins.
        try {
            LmNativeSelfTest.run();
        } catch (RuntimeException e) {
            if (Boolean.getBoolean("lm.selftest.strict")) {
                throw e;
            }
            AdaptiveDifficultyMod.LOGGER.error("[{}] {}", AdaptiveDifficultyMod.MOD_ID, e.getMessage(), e);
        }
        // 4.7.59: Momentum timer and hit rule, free trial revert, and the guide's recommendation rule.
        java.util.List<String> guideBroken = new java.util.ArrayList<>();
        guideBroken.addAll(com.dbzlegacy.adaptivedifficulty.progression.Momentum.selfTest());
        guideBroken.addAll(com.dbzlegacy.adaptivedifficulty.service.DifficultyTrial.selfTest());
        // 4.7.80: weekly goals: three a week from three systems, reset text.
        guideBroken.addAll(com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals.selfTest());
        guideBroken.addAll(com.dbzlegacy.adaptivedifficulty.gui.ultra.DifficultyGuide.selfTest());
        // 4.7.63: the guide shows once per account, then Difficulty opens on Home.
        guideBroken.addAll(com.dbzlegacy.adaptivedifficulty.gui.ultra.DifficultyTutorial.selfTest());
        // 4.7.64: hub dashboard text and the spar board.
        guideBroken.addAll(com.dbzlegacy.adaptivedifficulty.gui.ultra.HubDashboard.selfTest());
        guideBroken.addAll(com.dbzlegacy.adaptivedifficulty.sparring.SparBoard.selfTest());
        // 4.7.60: Saiyan Royal skin constants, the wire accent letters and the native shape math.
        guideBroken.addAll(com.dbzlegacy.adaptivedifficulty.gui.ultra.SaiyanRoyal.selfTest());
        // 4.7.62: the five menu skins and their readable tones (4.8.2: no bridge letter rewrite).
        guideBroken.addAll(com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.selfTest());
        // 4.7.66: the custom skin's color wheel geometry and conversions.
        guideBroken.addAll(com.dbzlegacy.adaptivedifficulty.gui.ultra.ColorWheel.selfTest());
        if (!guideBroken.isEmpty()) {
            String msg = "Momentum / trial / guide / skin self-test FAILED: " + String.join("; ", guideBroken);
            if (Boolean.getBoolean("lm.selftest.strict")) {
                throw new IllegalStateException(msg);
            }
            AdaptiveDifficultyMod.LOGGER.error("[{}] {}", AdaptiveDifficultyMod.MOD_ID, msg);
        } else {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Momentum / free trial / guide / skin self-test OK (timer, kill rule, hit scaling, revert, recommendation, guide shows once, hub dashboard, spar board, Saiyan Royal, five menu skins)",
                    AdaptiveDifficultyMod.MOD_ID);
        }
        AdaptiveDifficultyMod.LOGGER.info(
                "[{}] LM menus ready on {} protocol={}: the Legacy Mechanics client is required "
                        + "(requireLmClient={}); the KaiPath bridge is gone.",
                AdaptiveDifficultyMod.MOD_ID,
                LmNativeNetwork.CHANNEL_ID,
                LmNativeProtocol.PROTOCOL_VERSION,
                com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig.get().requireLmClient);
    }

    /**
     * Encode → decode round-trip for every registered screen builder plus a verbatim
     * {@link GuiActionPacket}. Any failure aborts startup — a broken packet format must not
     * reach production.
     */
    static void runPacketRoundTripSelfTest() {
        java.util.List<String> failures = new java.util.ArrayList<>();
        int screensTested = 0;
        try {
            for (String id : UltraScreenIds.BUILDER_IDS) {
                if (id == null || id.isBlank()) {
                    continue;
                }
                java.util.List<GuiOpenPacket> packets =
                        com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraScreenSpec.captureSends(
                                () -> UltraScreenSelfTest.probeBuilderForRoundTrip(id));
                if (packets == null || packets.isEmpty()) {
                    failures.add(id + ": no GuiOpenPacket produced for round-trip");
                    continue;
                }
                for (GuiOpenPacket open : packets) {
                    screensTested++;
                    String mismatch = roundTripOpenFields(open);
                    if (mismatch != null) {
                        failures.add(id + ": " + mismatch);
                    }
                }
            }

            String verbatimAction = "@hub:do:guitest_ack";
            GuiActionPacket action =
                    new GuiActionPacket("hub", verbatimAction, "{\"screen\":\"hub\",\"system\":\"hub\"}");
            net.minecraft.network.FriendlyByteBuf actionBuf =
                    new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            GuiActionPacket.encode(action, actionBuf);
            GuiActionPacket actionBack = GuiActionPacket.decode(actionBuf);
            if (!action.screenId().equals(actionBack.screenId())
                    || !action.actionId().equals(actionBack.actionId())
                    || !action.argsJson().equals(actionBack.argsJson())
                    || !verbatimAction.equals(actionBack.actionId())) {
                failures.add(
                        "GuiActionPacket verbatim actionId round-trip failed original=["
                                + action.screenId()
                                + ","
                                + action.actionId()
                                + ","
                                + action.argsJson()
                                + "] decoded=["
                                + actionBack.screenId()
                                + ","
                                + actionBack.actionId()
                                + ","
                                + actionBack.argsJson()
                                + "]");
            }

            // style + activeTab must survive encode/decode (client conformance contract).
            String styleFail = roundTripStyleAndActiveTab();
            if (styleFail != null) {
                failures.add(styleFail);
            }

            // bodyPartsSync world sync: partsJson + reason survive encode→decode verbatim.
            String syncFail = roundTripModelSync();
            if (syncFail != null) {
                failures.add(syncFail);
            }
            // Loud but never fatal: a catalog problem here must not stop the server booting.
            String fullSet = fullSetModelSync();
            if (fullSet != null) {
                AdaptiveDifficultyMod.LOGGER.error(
                        "[{}] GuiModelSync full-set self-test FAILED: {}", AdaptiveDifficultyMod.MOD_ID, fullSet);
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] packet round-trip self-test threw",
                    AdaptiveDifficultyMod.MOD_ID,
                    t);
            throw new IllegalStateException(
                    "LegacyMechanics Ultra GUI packet round-trip self-test failed — aborting startup",
                    t);
        }

        if (!failures.isEmpty()) {
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] GuiOpen/GuiAction round-trip self-test FAILED screensTested={} failures={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    screensTested,
                    failures);
            throw new IllegalStateException(
                    "LegacyMechanics Ultra GUI packet round-trip self-test failed ("
                            + failures.size()
                            + " error(s), screensTested="
                            + screensTested
                            + "): "
                            + failures);
        }
        LmLog.event(
                "[{}] packet round-trip self-test OK screensTested={} protocol={} "
                        + "(BUILDER_IDS 10-field + style/activeTab + GuiActionPacket + GuiModelSyncPacket)",
                AdaptiveDifficultyMod.MOD_ID,
                screensTested,
                PROTOCOL_VERSION);
    }

    /**
     * Assert {@link GuiModelSyncPacket} partsJson + reason survive encode→decode, that the
     * sanitizer keeps valid JSON (never a mid-string cut), and — since the sync broadcast
     * with a uuid subject — that a degraded payload preserves {@code subject} instead of
     * resetting it to {@code local} (which would clear each observer's own parts).
     *
     * @return null on success, failure message otherwise
     */
    private static String roundTripModelSync() {
        String parts = "{\"schema\":1,\"subject\":\"uuid:00000000-0000-0000-0000-0000000000ff\","
                + "\"bodyParts\":["
                + "{\"id\":\"saiyan_tail\",\"source\":\"dmzultra\",\"slot\":\"tail\"}],"
                + "\"colors\":{\"skin\":16777215,\"second\":16777215,\"hair\":2105380,"
                + "\"own\":13186347,\"trim\":15320170,\"horn\":15722191,\"light\":8380671},"
                + "\"extras\":[]}";
        GuiModelSyncPacket sync = new GuiModelSyncPacket(parts, GuiModelSyncPacket.REASON_EQUIP);
        net.minecraft.network.FriendlyByteBuf buf =
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        GuiModelSyncPacket.encode(sync, buf);
        GuiModelSyncPacket back = GuiModelSyncPacket.decode(buf);
        if (!sync.partsJson().equals(back.partsJson())) {
            return "GuiModelSyncPacket partsJson round-trip failed original=["
                    + sync.partsJson()
                    + "] decoded=["
                    + back.partsJson()
                    + "]";
        }
        if (!GuiModelSyncPacket.REASON_EQUIP.equals(back.reason())) {
            return "GuiModelSyncPacket reason round-trip failed → " + back.reason();
        }
        try {
            com.google.gson.JsonObject parsed =
                    com.google.gson.JsonParser.parseString(back.partsJson()).getAsJsonObject();
            if (!parsed.has("bodyParts") || !parsed.get("bodyParts").isJsonArray()) {
                return "GuiModelSyncPacket partsJson lost bodyParts array after decode";
            }
            if (!parsed.has("colors") || !parsed.get("colors").isJsonObject()) {
                return "GuiModelSyncPacket partsJson lost colors object after decode";
            }
            if (!"uuid:00000000-0000-0000-0000-0000000000ff".equals(
                    parsed.get("subject").getAsString())) {
                return "GuiModelSyncPacket partsJson lost uuid subject after decode → "
                        + parsed.get("subject");
            }
        } catch (Throwable t) {
            return "GuiModelSyncPacket partsJson is not valid JSON after round-trip: " + t;
        }
        // Oversized payload must degrade to valid JSON, never truncate mid-object.
        StringBuilder filler = new StringBuilder();
        while (filler.length() < GuiModelSyncPacket.PARTS_JSON_MAX + 100) {
            filler.append('x');
        }
        String oversized =
                "{\"schema\":1,\"subject\":\"uuid:00000000-0000-0000-0000-0000000000aa\","
                        + "\"pad\":\"" + filler + "\"}";
        GuiModelSyncPacket degraded = new GuiModelSyncPacket(oversized, "login");
        try {
            com.google.gson.JsonObject degradedObj =
                    com.google.gson.JsonParser.parseString(degraded.partsJson())
                            .getAsJsonObject();
            // The sync is broadcast: a fallback that resets subject to "local" would make
            // every observer clear its OWN parts when the owner's payload degraded.
            if (!degradedObj.has("subject")
                    || !"uuid:00000000-0000-0000-0000-0000000000aa".equals(
                            degradedObj.get("subject").getAsString())) {
                return "GuiModelSyncPacket degraded payload lost uuid subject → "
                        + degradedObj;
            }
        } catch (Throwable t) {
            return "GuiModelSyncPacket oversized payload not valid JSON after sanitize: " + t;
        }
        return null;
    }

    /**
     * A player wearing every shop piece plus the largest form's pieces, with oversized
     * {@code headBones}/{@code form} fields, must still get every piece, within the wire cap
     * (4.7.10: the degrade used to send an empty {@code bodyParts}).
     *
     * @return null on success, failure message otherwise
     */
    static String fullSetModelSync() {
        java.util.List<String> shop =
                com.dbzlegacy.adaptivedifficulty.gui.ultra.BodyPartCatalog.shopPieceIds();
        if (shop.size() < 109) {
            return "GuiModelSync full-set test: shop catalog has " + shop.size() + " pieces, expected 109";
        }
        com.google.gson.JsonArray pieces = new com.google.gson.JsonArray();
        java.util.List<com.dbzlegacy.adaptivedifficulty.gui.ultra.BodyPartCatalog.Part> form =
                com.dbzlegacy.adaptivedifficulty.gui.ultra.BodyPartCatalog.resolve(
                        "soulreaper", "innerhollow", "complete", "", "");
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (var part : form) {
            ids.add(part.id());
        }
        ids.addAll(shop);
        for (String id : ids) {
            com.google.gson.JsonObject entry = new com.google.gson.JsonObject();
            entry.addProperty("id", id);
            entry.addProperty("source", "dmzultra");
            entry.addProperty("slot", com.dbzlegacy.adaptivedifficulty.gui.ultra.BodyPartCatalog.slot(id));
            pieces.add(entry);
        }
        com.google.gson.JsonObject full = new com.google.gson.JsonObject();
        full.addProperty("schema", 1);
        full.addProperty("subject", "uuid:00000000-0000-0000-0000-0000000000bb");
        full.add("bodyParts", pieces);
        full.addProperty("suppressNaturalTail", true);
        full.addProperty("form", "x".repeat(3000));
        full.addProperty("headBones", "y".repeat(3000));
        String sent = new GuiModelSyncPacket(full.toString(), GuiModelSyncPacket.REASON_LOGIN).partsJson();
        if (sent.length() > GuiModelSyncPacket.PARTS_JSON_MAX) {
            return "GuiModelSync full-set payload is " + sent.length() + " chars, over the wire cap";
        }
        com.google.gson.JsonArray got = com.google.gson.JsonParser.parseString(sent)
                .getAsJsonObject().getAsJsonArray("bodyParts");
        if (got == null || got.size() != ids.size()) {
            return "GuiModelSync full-set payload sent " + (got == null ? 0 : got.size()) + " of "
                    + ids.size() + " equipped pieces (it must never be sent empty)";
        }
        return null;
    }

    /**
     * Assert button {@code style} values and {@code activeTab} survive encode→decode.
     *
     * @return null on success, failure message otherwise
     */
    private static String roundTripStyleAndActiveTab() {
        GuiOpenPacket open =
                com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraScreenSpec.of("difficulty")
                        .title("Style probe")
                        .page("tiers")
                        .activeTab("tiers")
                        .tabs("tiers|Tiers", "info|Info")
                        .line("style-activeTab probe")
                        .button("do:ok", "Confirm", UltraStyle.STYLE_CONFIRM)
                        .button("do:bad", "Danger", UltraStyle.STYLE_DANGER)
                        .button("do:off", "Dim", UltraStyle.STYLE_DIM)
                        .button("do:plain", "Normal", UltraStyle.STYLE_NORMAL)
                        .textField("input:probe", "hello", "type…", 16, 220, 200, 18, 32)
                        .toPacket();
        if (!"tiers".equals(open.activeTab())) {
            return "style/activeTab probe: activeTab not set on builder packet";
        }
        String buttons = open.buttonsJson() == null ? "" : open.buttonsJson();
        for (String style : new String[] {"confirm", "danger", "dim", "normal"}) {
            if (!buttons.contains("\"style\":\"" + style + "\"")) {
                return "style/activeTab probe: buttonsJson missing style=" + style;
            }
        }
        String inputs = open.inputsJson() == null ? "" : open.inputsJson();
        if (!inputs.contains("\"type\":\"text\"") || !inputs.contains("input:probe")) {
            return "style/activeTab probe: inputsJson missing text field";
        }
        String mismatch = roundTripOpenFields(open);
        if (mismatch != null) {
            return "style/activeTab probe: " + mismatch;
        }
        net.minecraft.network.FriendlyByteBuf buf =
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        GuiOpenPacket.encode(open, buf);
        GuiOpenPacket back = GuiOpenPacket.decode(buf);
        if (!"tiers".equals(back.activeTab())) {
            return "style/activeTab probe: activeTab lost in decode → " + back.activeTab();
        }
        String backButtons = back.buttonsJson() == null ? "" : back.buttonsJson();
        for (String style : new String[] {"confirm", "danger", "dim", "normal"}) {
            if (!backButtons.contains("\"style\":\"" + style + "\"")) {
                return "style/activeTab probe: style=" + style + " lost after decode";
            }
        }
        return null;
    }

    /** @return null when all 10 GuiOpen fields survive encode→decode */
    private static String roundTripOpenFields(GuiOpenPacket open) {
        if (open == null) {
            return "null packet";
        }
        net.minecraft.network.FriendlyByteBuf buf =
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        GuiOpenPacket.encode(open, buf);
        GuiOpenPacket back = GuiOpenPacket.decode(buf);
        if (!java.util.Objects.equals(open.screenId(), back.screenId())) {
            return "screenId " + open.screenId() + " → " + back.screenId();
        }
        if (!java.util.Objects.equals(open.title(), back.title())) {
            return "title mismatch";
        }
        if (!java.util.Objects.equals(open.pageId(), back.pageId())) {
            return "pageId mismatch";
        }
        if (!java.util.Objects.equals(open.tabsJson(), back.tabsJson())) {
            return "tabsJson mismatch";
        }
        if (!java.util.Objects.equals(open.activeTab(), back.activeTab())) {
            return "activeTab mismatch";
        }
        if (!java.util.Objects.equals(open.linesJson(), back.linesJson())) {
            return "linesJson mismatch";
        }
        if (!java.util.Objects.equals(open.buttonsJson(), back.buttonsJson())) {
            return "buttonsJson mismatch";
        }
        if (open.panelWidth() != back.panelWidth()) {
            return "panelWidth " + open.panelWidth() + " → " + back.panelWidth();
        }
        if (open.panelHeight() != back.panelHeight()) {
            return "panelHeight " + open.panelHeight() + " → " + back.panelHeight();
        }
        if (!java.util.Objects.equals(open.layoutJson(), back.layoutJson())) {
            return "layoutJson mismatch";
        }
        if (!java.util.Objects.equals(open.inputsJson(), back.inputsJson())) {
            return "inputsJson mismatch";
        }
        return null;
    }

    public static void sendOpen(ServerPlayer player, GuiOpenPacket packet) {
        if (player == null || packet == null) {
            return;
        }
        GuiOpenPacket wire = applyHubShim(packet);
        LmLog.event(
                "[{}] GuiOpenPacket → player={} screenId={} (logical={}) buttons={} panel={}x{} "
                        + "activeTab={} {}",
                AdaptiveDifficultyMod.MOD_ID,
                safeName(player),
                wire.screenId(),
                logicalSystemOf(packet, wire),
                buttonCount(wire),
                wire.panelWidth(),
                wire.panelHeight(),
                wire.activeTab(),
                clientSummary(player));
        validatePanelBounds(wire);
        // 4.7.58: the LM client draws the menu itself. 4.8.2: there is no KaiPath bridge, so a menu goes native
        // or not at all; a player without the LM client (requireLmClient off) gets the install message.
        switch (LmNativeNetwork.route(player, wire)) {
            case NATIVE -> LmNativeNetwork.sendOpen(player, wire);
            case REFUSE -> com.dbzlegacy.adaptivedifficulty.util.DmzRewards.msg(
                    player,
                    com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.dangerLine(
                            "This menu needs a newer Legacy Mechanics client. Update the mod, then join again."));
            default -> {
                LmLog.event("[{}] menu not sent (no Legacy Mechanics client) player={} screenId={}",
                        AdaptiveDifficultyMod.MOD_ID, safeName(player), wire.screenId());
                com.dbzlegacy.adaptivedifficulty.util.DmzRewards.msg(
                        player,
                        com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.dangerLine(
                                LmNativeNetwork.requiredClientMessage()));
            }
        }
    }

    /** Clear per-session feature advertisement (call on logout). */
    public static void clearClientSession(ServerPlayer player) {
        if (player == null) {
            return;
        }
        LmNativeNetwork.clear(player);
        MODEL_SYNCED.remove(player.m_20148_());
        MODEL_SYNC_PENDING.remove(player.m_20148_());
    }

    /**
     * True when the player's LM client listed {@code bodyPartsSync} in its hello (native message 5).
     * 4.8.2: was the KaiPath bridge's message 3 advertisement. A client that did not list it has no
     * handler for message 5 (an unregistered id drops the connection), so this gate must guard every
     * {@link GuiModelSyncPacket} send.
     */
    public static boolean supportsModelSync(ServerPlayer player) {
        return player != null && LmNativeNetwork.takesModelSync(player);
    }

    /**
     * Authoritative world sync of the player's equipped body parts
     * ({@link GuiModelSyncPacket}, native message 5 since 4.8.2). Server decides everything — the
     * client replaces (never merges) its cached state with this snapshot. No-op unless the LM client
     * listed {@code bodyPartsSync}. Send on login (initial) and after every equip/unequip change.
     *
     * <p><b>Broadcast</b> to the player and everyone tracking them: snapshots carry
     * {@code subject uuid:<owner>} so each observer binds them to the owner's entity.
     * Per-observer sends still use {@link #sendModelSyncTo} (the track-start trigger).
     */
    public static boolean sendModelSync(ServerPlayer player, String reason) {
        if (player == null || !supportsModelSync(player)) {
            return false;
        }
        try {
            GuiModelSyncPacket packet = modelSyncPacket(player, reason);
            LmLog.event(
                    "[{}] GuiModelSyncPacket → broadcast to trackers of {} reason={} partsJson={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    safeName(player),
                    reason,
                    packet.partsJson());
            // Gate per recipient (4.7.10): every player in the same world who advertised
            // bodyPartsSync, the owner included. A blanket tracker broadcast also reached
            // clients that never registered message 3, which drops their connection. A player
            // out of range only caches the snapshot; track start resends when they meet.
            int sent = 0;
            for (ServerPlayer viewer : modelSyncRecipients(player)) {
                if (!supportsModelSync(viewer)) {
                    continue;
                }
                LmNativeNetwork.sendModelSync(viewer, packet);
                sent++;
            }
            return sent > 0;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] GuiModelSyncPacket send failed player={} reason={}: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    safeName(player),
                    reason,
                    t.toString());
            return false;
        }
    }

    /** The owner and every player in the owner's world whose client advertised bodyPartsSync. */
    private static java.util.List<ServerPlayer> modelSyncRecipients(ServerPlayer owner) {
        java.util.List<ServerPlayer> out = new java.util.ArrayList<>();
        out.add(owner);
        var server = owner.m_20194_();
        if (server == null) {
            return out;
        }
        for (ServerPlayer other : server.m_6846_().m_11314_()) {
            if (other != null && !other.equals(owner) && other.m_9236_() == owner.m_9236_()
                    && supportsModelSync(other)) {
                out.add(other);
            }
        }
        return out;
    }

    /**
     * Targeted sync: {@code subject}'s parts sent to one {@code viewer} only (PLAYER
     * distributor). Used when {@code viewer} starts tracking {@code subject} — re-broadcasting
     * there would spam every other tracker. Gate is on the <b>viewer</b> (the recipient must
     * have advertised {@code bodyPartsSync}); the subject's own advertisement is irrelevant.
     */
    public static void sendModelSyncTo(ServerPlayer subject, ServerPlayer viewer, String reason) {
        if (subject == null || viewer == null || !supportsModelSync(viewer)) {
            return;
        }
        if (subject.equals(viewer)) {
            return;
        }
        try {
            GuiModelSyncPacket packet = modelSyncPacket(subject, reason);
            LmLog.event(
                    "[{}] GuiModelSyncPacket → subject={} viewer={} reason={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    safeName(subject),
                    safeName(viewer),
                    reason);
            LmNativeNetwork.sendModelSync(viewer, packet);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] GuiModelSyncPacket targeted send failed subject={} viewer={} reason={}: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    safeName(subject),
                    safeName(viewer),
                    reason,
                    t.toString());
        }
    }

    private static GuiModelSyncPacket modelSyncPacket(ServerPlayer subject, String reason) {
        JsonObject parts = com.dbzlegacy.adaptivedifficulty.gui.ultra.PlayerModelParts
                .syncSnapshot(subject);
        return new GuiModelSyncPacket(parts.toString(), reason);
    }

    /**
     * Track-start trigger: {@code tracker} just began seeing {@code target} (login into
     * range, respawn, dimension change, walking back into range after an equip change
     * that broadcast while they were away). Send {@code target}'s current snapshot to
     * {@code tracker} only. Fires on the server thread.
     */
    public static void onStartTracking(ServerPlayer tracker, ServerPlayer target) {
        if (tracker == null || target == null || tracker.equals(target)) {
            return;
        }
        sendModelSyncTo(target, tracker, GuiModelSyncPacket.REASON_TRACK);
    }

    /**
     * Login trigger: when the LM client's hello lists {@code bodyPartsSync} (4.8.2; was the KaiPath
     * feature advertisement), send the initial world sync once per session. Deferred a few
     * ticks so DMZ character data (race tail / forms) has loaded before the snapshot. The
     * same task backfills every other online player's parts to the new viewer: their
     * track-start events fired before the hello reached the server, so those targeted sends
     * were gated off.
     */
    public static void onClientAdvertisement(ServerPlayer player) {
        if (player == null || !supportsModelSync(player)) {
            return;
        }
        java.util.UUID id = player.m_20148_();
        // Done once per session, but only marked done after it was sent: a skipped attempt
        // (player not resolvable yet) is retried on the client's next hello (4.7.10).
        if (MODEL_SYNCED.contains(id) || !MODEL_SYNC_PENDING.add(id)) {
            return;
        }
        var server = player.m_20194_();
        if (server == null) {
            MODEL_SYNC_PENDING.remove(id);
            if (sendModelSync(player, GuiModelSyncPacket.REASON_LOGIN)) {
                MODEL_SYNCED.add(id);
            }
            return;
        }
        server.m_6937_(new net.minecraft.server.TickTask(server.m_129921_() + 40, () -> {
            MODEL_SYNC_PENDING.remove(id);
            ServerPlayer viewer = server.m_6846_().m_11259_(id);
            if (viewer == null || !sendModelSync(viewer, GuiModelSyncPacket.REASON_LOGIN)) {
                return;
            }
            MODEL_SYNCED.add(id);
            for (ServerPlayer other : server.m_6846_().m_11314_()) {
                if (other != null && !other.equals(viewer)) {
                    sendModelSyncTo(other, viewer, GuiModelSyncPacket.REASON_LOGIN);
                }
            }
        }));
    }

    /**
     * KaiPath compatibility: rewrite wire {@code screenId} to {@code hub} when the logical id
     * is not in {@link UltraScreenIds#KAIPATH_REGISTERED_IDS}. Title, tabs, buttons, panel, and
     * layout stay as built; logical system is kept in button {@code args} / {@code layoutJson}.
     */
    public static GuiOpenPacket applyHubShim(GuiOpenPacket packet) {
        if (packet == null) {
            return null;
        }
        String logical = packet.screenId();
        if (!UltraScreenIds.needsHubShim(logical)) {
            return packet;
        }
        String layout = withLogicalSystem(packet.layoutJson(), logical);
        return new GuiOpenPacket(
                "hub",
                packet.title(),
                packet.pageId(),
                packet.tabsJson(),
                packet.activeTab(),
                packet.linesJson(),
                packet.buttonsJson(),
                packet.panelWidth(),
                packet.panelHeight(),
                layout,
                packet.inputsJson());
    }

    static String withLogicalSystem(String layoutJson, String logical) {
        String system = logical == null || logical.isBlank() ? "hub" : logical.trim().toLowerCase();
        try {
            JsonObject o;
            if (layoutJson == null || layoutJson.isBlank()) {
                o = new JsonObject();
            } else {
                o = JsonParser.parseString(layoutJson).getAsJsonObject();
            }
            o.addProperty(UltraScreenIds.META_SYSTEM, system);
            o.addProperty("screen", system);
            // Stock KaiPath defaults titleAlign to left when the key is missing — always force.
            o.addProperty("titleAlign", UltraStyle.TITLE_ALIGN);
            return o.toString();
        } catch (Throwable ignored) {
            return "{\"system\":\""
                    + system
                    + "\",\"screen\":\""
                    + system
                    + "\",\"center\":true,\"margin\":"
                    + UltraGuiProtocol.DEFAULT_MARGIN
                    + ",\"titleAlign\":\"center\"}";
        }
    }

    private static String logicalSystemOf(GuiOpenPacket original, GuiOpenPacket wire) {
        if (original != null
                && original.screenId() != null
                && !original.screenId().isBlank()
                && UltraScreenIds.needsHubShim(original.screenId())) {
            return original.screenId();
        }
        return wire == null ? "?" : wire.screenId();
    }

    /**
     * Startup check: every Ultra builder {@code screenId} must be in the protocol list.
     * Errors abort channel registration so a mis-cataloged screen never ships live.
     */
    static void validateBuilderScreenIdsAtStartup() {
        Set<String> builderIds = new LinkedHashSet<>(UltraScreenIds.BUILDER_IDS);
        int errors = UltraScreenIds.validateAtStartup(builderIds);
        if (errors > 0) {
            throw new IllegalStateException(
                    "LegacyMechanics Ultra screen id catalog invalid ("
                            + errors
                            + " error(s)) — fix UltraScreenIds.PROTOCOL_IDS / BUILDER_IDS");
        }
    }

    /**
     * Every Ultra screen must send fields 8–10 ({@code panelWidth}, {@code panelHeight},
     * {@code layoutJson}). Log when bounds are missing so a fullscreen client is not blamed on
     * an empty server packet. Does not rewrite the packet — KaiPath must honor the values.
     */
    static void validatePanelBounds(GuiOpenPacket packet) {
        if (packet == null) {
            return;
        }
        int w = packet.panelWidth();
        int h = packet.panelHeight();
        String layout = packet.layoutJson();
        if (w <= 0 || h <= 0) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] GuiOpenPacket missing panel bounds screenId={} page panelWidth={} panelHeight={} layoutJson={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    packet.screenId(),
                    w,
                    h,
                    layout == null || layout.isBlank() ? "<empty>" : layout);
        } else if (layout == null || layout.isBlank()) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] GuiOpenPacket missing layoutJson screenId={} panelWidth={} panelHeight={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    packet.screenId(),
                    w,
                    h);
        }
    }

    static Connection connectionOf(ServerPlayer player) {
        try {
            ServerGamePacketListenerImpl listener = player.f_8906_;
            if (listener == null) {
                return null;
            }
            if (connectionField == null) {
                for (Field field : ServerGamePacketListenerImpl.class.getDeclaredFields()) {
                    if (Connection.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        connectionField = field;
                        break;
                    }
                }
            }
            if (connectionField == null) {
                return null;
            }
            return (Connection) connectionField.get(listener);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
