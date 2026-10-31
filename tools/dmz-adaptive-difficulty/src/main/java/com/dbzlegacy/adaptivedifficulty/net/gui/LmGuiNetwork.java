package com.dbzlegacy.adaptivedifficulty.net.gui;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraScreenIds;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraScreenSelfTest;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Server half of this mod's Ultra GUI channel ({@code <modid>:gui}).
 *
 * <p>Channel namespace comes from {@link AdaptiveDifficultyMod#MOD_ID} via
 * {@link UltraGuiProtocol#channel(String)} — other mods should register the same packet
 * shape on <em>their</em> {@code modid:gui}. KaiPath keys client consumers by channel.
 * No client screen classes are referenced here.
 */
public final class LmGuiNetwork {
    /** This mod's channel namespace (= Forge mod id). Not part of the packet payload. */
    public static final String CHANNEL_NAMESPACE = AdaptiveDifficultyMod.MOD_ID;
    /** @see UltraGuiProtocol#CHANNEL_PATH */
    public static final String CHANNEL_PATH = UltraGuiProtocol.CHANNEL_PATH;
    /**
     * Forge SimpleChannel protocol string shared with KaiPath.
     * Bumping this requires coordinated KaiPath release — mismatched clients drop the channel.
     * Must stay equal to {@link UltraGuiProtocol#PROTOCOL_VERSION}.
     */
    public static final String PROTOCOL_VERSION = "1";

    /** Forge mod id for KaiPath. */
    public static final String KAIPATH_MOD_ID = "kaipath";
    /**
     * Minimum KaiPath version that speaks the Ultra GUI protocol on {@code <modid>:gui}.
     * Full dynamic menus (10-field decode + buttonsJson) require <b>1.3.1+</b>; channel
     * handshake still accepts {@code >= 1.2.5}.
     */
    public static final String KAIPATH_MIN_VERSION = "1.2.5";

    /** First KaiPath release with dynamic Ultra GUI (10 fields + multi-mod registry). */
    public static final String KAIPATH_DYNAMIC_UI_VERSION = "1.3.1";

    /**
     * Current recommended/released KaiPath for Ultra (live). 1.3.17 chrome, 1.3.22
     * {@code icon}, 1.3.29 {@code bodyParts} (custom pieces on the model preview), 1.3.31+
     * {@code bodyPartsSync} (message 3 world sync).
     */
    public static final String KAIPATH_RECOMMENDED_VERSION = "1.5.1";

    /**
     * Optional channel: clients without KaiPath omit it. Accept missing so those players
     * still join; UltraGui then falls back to CustomNPCs for them.
     */
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            UltraGuiProtocol.channel(CHANNEL_NAMESPACE),
            () -> PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION));

    private static boolean registered;
    private static Field connectionField;
    /** Players already warned this session for missing style/activeTab on GuiOpen. */
    private static final java.util.Set<java.util.UUID> FEATURE_GAP_WARNED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
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
        String featureSummary = UltraClientFeatures.summarizeForStaff(player);
        LmLog.event(
                "[{}] guitest → player={} {} — sending GuiOpenPacket with style=confirm + activeTab=home",
                AdaptiveDifficultyMod.MOD_ID,
                safeName(player),
                featureSummary);
        UltraClientFeatures.warnMissingOptionalFeatures(player, "guitest");
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
     * Login probe: whether this connection advertised KaiPath (Ultra path eligibility).
     */
    public static void logClientGuiCapability(ServerPlayer player) {
        if (player == null) {
            return;
        }
        boolean kaipath = clientHasKaiPath(player);
        LmLog.event(
                "[{}] login GUI probe player={} kaipath={} channel={}:{} protocol={} guiBackend={}",
                AdaptiveDifficultyMod.MOD_ID,
                safeName(player),
                kaipath,
                CHANNEL_NAMESPACE,
                CHANNEL_PATH,
                PROTOCOL_VERSION,
                com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig.get().guiBackend);
        if (!kaipath
                && com.dbzlegacy.adaptivedifficulty.gui.GuiBackend.fromConfig()
                        == com.dbzlegacy.adaptivedifficulty.gui.GuiBackend.ULTRA) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] player={} has no KaiPath ≥ {} — Ultra menus require KaiPath "
                            + "(no CNPC fallback)",
                    AdaptiveDifficultyMod.MOD_ID,
                    safeName(player),
                    KAIPATH_MIN_VERSION);
        } else if (kaipath) {
            // The client advertises just after this login event; check once it has had time to.
            UltraClientFeatures.scheduleLoginCheck(player);
        }
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
        AdaptiveDifficultyMod.LOGGER.info(
                "[{}] registering channel {}:{} protocol={} "
                        + "msg0=GuiOpenPacket(S→C) msg1=GuiActionPacket(C→S) msg2=GuiFeaturesPacket(C→S) "
                        + "msg3=GuiModelSyncPacket(S→C, bodyPartsSync-gated)",
                AdaptiveDifficultyMod.MOD_ID,
                CHANNEL_NAMESPACE,
                CHANNEL_PATH,
                PROTOCOL_VERSION);
        CHANNEL.messageBuilder(GuiOpenPacket.class, UltraGuiProtocol.MSG_OPEN, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GuiOpenPacket::encode)
                .decoder(buf -> decodeOpenLogged(buf))
                .consumerMainThread(GuiOpenPacket::handle)
                .add();
        // Network-thread receive → enqueueWork (main thread) for all ServerPlayer access.
        CHANNEL.messageBuilder(
                        GuiActionPacket.class, UltraGuiProtocol.MSG_ACTION, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GuiActionPacket::encode)
                .decoder(buf -> decodeActionLogged(buf))
                .consumerNetworkThread(LmGuiNetwork::handleGuiAction)
                .add();
        CHANNEL.messageBuilder(
                        GuiFeaturesPacket.class, UltraGuiProtocol.MSG_FEATURES, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GuiFeaturesPacket::encode)
                .decoder(GuiFeaturesPacket::decode)
                .consumerNetworkThread(GuiFeaturesPacket::handle)
                .add();
        // Message 3 is registered but NEVER sent to clients that did not advertise
        // bodyPartsSync — KaiPath before 1.3.31 has no handler for it (unregistered id =
        // dropped connection). sendModelSync enforces the gate.
        CHANNEL.messageBuilder(
                        GuiModelSyncPacket.class, UltraGuiProtocol.MSG_MODEL_SYNC, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GuiModelSyncPacket::encode)
                .decoder(GuiModelSyncPacket::decode)
                .consumerMainThread(GuiModelSyncPacket::handle)
                .add();
        validateBuilderScreenIdsAtStartup();
        UltraScreenSelfTest.runAtStartup();
        UltraScreenSelfTest.verifyTabStaysOnScreen();
        UltraScreenSelfTest.verifyHubShim();
        UltraScreenSelfTest.verifyMenuLinks();
        UltraScreenSelfTest.verifyStaffGate();
        UltraScreenSelfTest.verifyBodyCap();
        UltraScreenSelfTest.verifyConfigActionRow();
        UltraScreenSelfTest.verifyConfigFieldHints();
        UltraScreenSelfTest.verifyConfigNumberRanges();
        UltraScreenSelfTest.verifyConfigFieldBudget();
        UltraScreenSelfTest.verifyDojoWarExpiry();
        UltraScreenSelfTest.verifyFirstSuperformChoice();
        UltraScreenSelfTest.verifyPickerPaging();
        UltraScreenSelfTest.verifyTitlesSurvivePrestige();
        runPacketRoundTripSelfTest();
        AdaptiveDifficultyMod.LOGGER.info(
                "[{}] Ultra GUI bridge ready channel={} protocol={} — KaiPath {}+ dynamic UI "
                        + "(any screenId; no hub shim). Recommended KaiPath {}; "
                        + "optional features via GuiFeaturesPacket "
                        + "(style/activeTab/titleAlign/playerModel/textInput; bodyParts, bodyPartsSync).",
                AdaptiveDifficultyMod.MOD_ID,
                UltraGuiProtocol.channelId(CHANNEL_NAMESPACE),
                PROTOCOL_VERSION,
                KAIPATH_DYNAMIC_UI_VERSION,
                KAIPATH_RECOMMENDED_VERSION);
    }

    private static GuiOpenPacket decodeOpenLogged(net.minecraft.network.FriendlyByteBuf buf) {
        try {
            return GuiOpenPacket.decode(buf);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] GuiOpenPacket decode failed protocol={} channel={}:{}",
                    AdaptiveDifficultyMod.MOD_ID,
                    PROTOCOL_VERSION,
                    CHANNEL_NAMESPACE,
                    CHANNEL_PATH,
                    t);
            throw t;
        }
    }

    private static GuiActionPacket decodeActionLogged(net.minecraft.network.FriendlyByteBuf buf) {
        try {
            GuiActionPacket pkt = GuiActionPacket.decode(buf);
            LmLog.event(
                    "[{}] GuiActionPacket decoded protocol={} screenId={} actionId={} argsJson={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    PROTOCOL_VERSION,
                    pkt == null ? "<null>" : pkt.screenId(),
                    pkt == null ? "<null>" : pkt.actionId(),
                    pkt == null ? "<null>" : pkt.argsJson());
            return pkt;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] GuiActionPacket decode failed protocol={} channel={}:{} — handler will not run",
                    AdaptiveDifficultyMod.MOD_ID,
                    PROTOCOL_VERSION,
                    CHANNEL_NAMESPACE,
                    CHANNEL_PATH,
                    t);
            throw t;
        }
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

            GuiFeaturesPacket features =
                    new GuiFeaturesPacket("[\"style\",\"activeTab\"]");
            net.minecraft.network.FriendlyByteBuf featBuf =
                    new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            GuiFeaturesPacket.encode(features, featBuf);
            GuiFeaturesPacket featuresBack = GuiFeaturesPacket.decode(featBuf);
            java.util.Set<String> parsed = GuiFeaturesPacket.parseFeatures(featuresBack.featuresJson());
            if (!parsed.contains(UltraClientFeatures.FEATURE_STYLE)
                    || !parsed.contains(UltraClientFeatures.FEATURE_ACTIVE_TAB)) {
                failures.add(
                        "GuiFeaturesPacket round-trip failed featuresJson="
                                + featuresBack.featuresJson()
                                + " parsed="
                                + parsed);
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
                        + "(BUILDER_IDS 10-field + style/activeTab + GuiActionPacket + "
                        + "GuiFeaturesPacket + GuiModelSyncPacket)",
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
                UltraClientFeatures.summarizeForStaff(player));
        validatePanelBounds(wire);
        if (FEATURE_GAP_WARNED.add(player.m_20148_())) {
            UltraClientFeatures.warnMissingOptionalFeatures(player, "GuiOpenPacket");
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), wire);
    }

    /** Clear per-session feature advertisement (call on logout). */
    public static void clearClientSession(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UltraClientFeatures.clear(player);
        FEATURE_GAP_WARNED.remove(player.m_20148_());
        MODEL_SYNCED.remove(player.m_20148_());
        MODEL_SYNC_PENDING.remove(player.m_20148_());
    }

    /**
     * True when the client advertised {@code bodyPartsSync} (message 3). KaiPath ≤1.3.29
     * never advertises it and has no message-3 handler — sending would abort their
     * connection, so this gate must guard every {@link GuiModelSyncPacket} send.
     */
    public static boolean supportsModelSync(ServerPlayer player) {
        return player != null
                && UltraClientFeatures.get(player).has(UltraClientFeatures.FEATURE_BODY_PARTS_SYNC);
    }

    /**
     * Authoritative world sync of the player's equipped body parts
     * ({@link GuiModelSyncPacket}, message 3). Server decides everything — the client
     * replaces (never merges) its cached state with this snapshot. No-op unless the client
     * advertised {@code bodyPartsSync}. Send on login (initial) and after every
     * equip/unequip change.
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
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
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
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
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
     * Login trigger: when the client's first feature advertisement includes
     * {@code bodyPartsSync}, send the initial world sync once per session. Deferred a few
     * ticks so DMZ character data (race tail / forms) has loaded before the snapshot. The
     * same task backfills every other online player's parts to the new viewer: their
     * track-start events fired before this advertisement reached the server, so those
     * targeted sends were gated off.
     */
    public static void onClientAdvertisement(ServerPlayer player) {
        if (player == null || !supportsModelSync(player)) {
            return;
        }
        java.util.UUID id = player.m_20148_();
        // Done once per session, but only marked done after it was sent: a skipped attempt
        // (player not resolvable yet) is retried on the client's next advertisement (4.7.10).
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

    /**
     * True when the player's connection lists KaiPath at {@link #KAIPATH_MIN_VERSION} or newer.
     * Forge's client handshake may omit version strings ({@code Pair("", "")}); presence alone
     * is accepted in that case. When a version is present it must be {@code >= 1.2.5}.
     */
    public static boolean clientHasKaiPath(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        String version = probeKaiPathVersion(player);
        if (version == null) {
            // null = mod absent; blank = present without version string
            return false;
        }
        if (version.isBlank()) {
            return true;
        }
        return isVersionAtLeast(version, KAIPATH_MIN_VERSION);
    }

    /**
     * KaiPath version from the Forge client mod list, or {@code ""} when present without a
     * version string, or {@code null} when KaiPath is not listed.
     */
    public static String probeKaiPathVersion(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        try {
            Connection connection = connectionOf(player);
            if (connection == null) {
                return null;
            }
            Class<?> hooks = Class.forName("net.minecraftforge.network.NetworkHooks");
            Object data = hooks.getMethod("getConnectionData", Connection.class).invoke(null, connection);
            if (data == null) {
                return null;
            }
            Object modData = data.getClass().getMethod("getModData").invoke(data);
            if (modData instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() == null
                            || !KAIPATH_MOD_ID.equalsIgnoreCase(String.valueOf(entry.getKey()))) {
                        continue;
                    }
                    String version = pairVersion(entry.getValue());
                    return version == null ? "" : version;
                }
            }
            Object modList = data.getClass().getMethod("getModList").invoke(data);
            if (modList instanceof Iterable<?> mods) {
                for (Object mod : mods) {
                    if (mod != null && KAIPATH_MOD_ID.equalsIgnoreCase(String.valueOf(mod))) {
                        return "";
                    }
                }
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Compare dotted numeric versions ({@code 1.2.5}, {@code 1.2.6}, {@code 1.3.0}).
     * Non-numeric suffixes on a segment are ignored after the leading integer.
     * Missing trailing segments are treated as {@code 0}.
     */
    public static boolean isVersionAtLeast(String actual, String minimum) {
        if (actual == null || actual.isBlank() || minimum == null || minimum.isBlank()) {
            return false;
        }
        int[] a = parseVersionParts(actual);
        int[] m = parseVersionParts(minimum);
        int len = Math.max(a.length, m.length);
        for (int i = 0; i < len; i++) {
            int av = i < a.length ? a[i] : 0;
            int mv = i < m.length ? m[i] : 0;
            if (av > mv) {
                return true;
            }
            if (av < mv) {
                return false;
            }
        }
        return true;
    }

    private static int[] parseVersionParts(String version) {
        String cleaned = version.trim();
        int plus = cleaned.indexOf('+');
        if (plus >= 0) {
            cleaned = cleaned.substring(0, plus);
        }
        int dash = cleaned.indexOf('-');
        if (dash >= 0) {
            cleaned = cleaned.substring(0, dash);
        }
        String[] bits = cleaned.split("\\.");
        int[] parts = new int[bits.length];
        for (int i = 0; i < bits.length; i++) {
            parts[i] = leadingInt(bits[i]);
        }
        return parts;
    }

    private static int leadingInt(String segment) {
        if (segment == null || segment.isEmpty()) {
            return 0;
        }
        int n = 0;
        boolean any = false;
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            any = true;
            n = n * 10 + (c - '0');
        }
        return any ? n : 0;
    }

    /** {@code Pair<String,String>} is (modName, modVersion); prefer right, then left. */
    private static String pairVersion(Object pair) {
        if (pair == null) {
            return null;
        }
        try {
            Method getRight = pair.getClass().getMethod("getRight");
            Object right = getRight.invoke(pair);
            if (right != null && !String.valueOf(right).isBlank()) {
                return String.valueOf(right);
            }
            Method getLeft = pair.getClass().getMethod("getLeft");
            Object left = getLeft.invoke(pair);
            if (left != null && !String.valueOf(left).isBlank()) {
                // Only treat left as version when it looks like one (starts with a digit).
                String s = String.valueOf(left);
                if (!s.isEmpty() && s.charAt(0) >= '0' && s.charAt(0) <= '9') {
                    return s;
                }
            }
            return right != null ? String.valueOf(right) : null;
        } catch (Throwable ignored) {
            return String.valueOf(pair);
        }
    }

    private static Connection connectionOf(ServerPlayer player) {
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
