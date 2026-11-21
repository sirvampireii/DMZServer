package com.dbzlegacy.adaptivedifficulty.net.gui;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraScreenIds;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraScreenSelfTest;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraScreenSpec;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/**
 * LM 4.7.58 boot self-test for the native client path (runs on the server with the Ultra
 * self-tests; a failure logs an ERROR, or stops boot under {@code -Dlm.selftest.strict=true}).
 *
 * <p>Pins: the channel name and protocol, the open / hello codecs, what each widget needs
 * ({@link LmNativeProtocol#analyze}), every routing branch ({@link LmNativeNetwork#decide}), the
 * scale-to-fit math ({@link NativeMenuFit}) at 1080p GUI scale 3 and 4, and that every packet the
 * menu builders produce is drawable by the protocol-1 client. If a menu constant this test pins
 * moves, update the pin here in the same change.
 */
public final class LmNativeSelfTest {
    private LmNativeSelfTest() {}

    public static void run() {
        List<String> broken = new ArrayList<>();
        channel(broken);
        codecs(broken);
        needs(broken);
        routing(broken);
        fit(broken);
        builders(broken);
        if (!broken.isEmpty()) {
            throw new IllegalStateException("LM native client self-test failed: " + String.join("; ", broken));
        }
        AdaptiveDifficultyMod.LOGGER.info("[{}] LM native client self-test OK (channel {}, protocol {}, {} features)",
                AdaptiveDifficultyMod.MOD_ID, LmNativeNetwork.CHANNEL_ID, LmNativeProtocol.PROTOCOL_VERSION,
                LmNativeProtocol.CLIENT_FEATURES.size());
    }

    private static void check(List<String> broken, boolean ok, String what) {
        if (!ok) {
            broken.add(what);
        }
    }

    private static void channel(List<String> broken) {
        String id = LmNativeNetwork.CHANNEL_ID.toString();
        check(broken, "legacymechanics:native_ui".equals(id), "native channel is " + id);
        check(broken, !id.equals(UltraGuiProtocol.channelId(AdaptiveDifficultyMod.MOD_ID)),
                "native channel collides with the KaiPath bridge channel");
        check(broken, !id.endsWith(":gui") && !id.startsWith("kaipath:"),
                "native channel uses a KaiPath-owned name");
        check(broken, "1".equals(LmNativeProtocol.PROTOCOL_VERSION) && "1".equals(UltraGuiProtocol.PROTOCOL_VERSION),
                "protocol strings moved off \"1\"");
        check(broken, LmNativeProtocol.MSG_OPEN == 0 && LmNativeProtocol.MSG_ACTION == 1
                && LmNativeProtocol.MSG_HELLO == 2, "native message ids moved");
    }

    private static void codecs(List<String> broken) {
        String layout = "{\"panelWidth\":426,\"panelHeight\":250,\"center\":true,\"margin\":10,"
                + "\"playerModel\":{\"x\":330,\"y\":46,\"width\":86,\"height\":120,\"rotate\":false}}";
        GuiOpenPacket open = new GuiOpenPacket("difficulty", "Difficulty " + UltraStyle.CONFIRM + "Tiers", "tiers",
                "[]", "tiers", "[\"a\",\"b\"]",
                "[{\"id\":\"@difficulty:nav:none\",\"label\":\"Summon locked\",\"style\":\"dim\",\"x\":10,"
                        + "\"y\":60,\"w\":150,\"h\":16}]",
                426, 250, layout, "[{\"id\":\"@config:input:x\",\"type\":\"text\",\"x\":10,\"y\":80,\"w\":200,\"h\":16}]");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        GuiOpenPacket.encode(open, buf);
        GuiOpenPacket back = GuiOpenPacket.decode(buf);
        check(broken, open.title().equals(back.title()) && open.buttonsJson().equals(back.buttonsJson())
                && open.layoutJson().equals(back.layoutJson()) && open.inputsJson().equals(back.inputsJson())
                && open.panelWidth() == back.panelWidth() && open.panelHeight() == back.panelHeight()
                && open.activeTab().equals(back.activeTab()), "native open round trip lost a field");
        FriendlyByteBuf hb = new FriendlyByteBuf(Unpooled.buffer());
        NativeHelloPacket.encode(new NativeHelloPacket(AdaptiveDifficultyMod.VERSION,
                LmNativeProtocol.clientFeaturesJson()), hb);
        NativeHelloPacket hello = NativeHelloPacket.decode(hb);
        check(broken, AdaptiveDifficultyMod.VERSION.equals(hello.clientVersion())
                && LmNativeProtocol.parseFeatures(hello.featuresJson()).equals(
                        new LinkedHashSet<>(LmNativeProtocol.CLIENT_FEATURES)), "hello round trip lost a field");
        FriendlyByteBuf ab = new FriendlyByteBuf(Unpooled.buffer());
        GuiActionPacket.encode(new GuiActionPacket("difficulty", "@difficulty:page:tier_ask:3", "{\"value\":\"x\"}"), ab);
        GuiActionPacket action = GuiActionPacket.decode(ab);
        check(broken, "@difficulty:page:tier_ask:3".equals(action.actionId())
                && "{\"value\":\"x\"}".equals(action.argsJson()), "action round trip lost a field");
    }

    private static GuiOpenPacket withLayout(String layout, String inputs) {
        return new GuiOpenPacket("character", "Character", "main", "[]", "", "[]", "[]", 426, 250, layout, inputs);
    }

    private static void needs(List<String> broken) {
        Set<String> client = new LinkedHashSet<>(LmNativeProtocol.CLIENT_FEATURES);
        LmNativeProtocol.Needs plain = LmNativeProtocol.analyze(withLayout("{\"center\":true}", "[]"), id -> true);
        check(broken, plain.hard().isEmpty() && plain.soft().isEmpty(), "plain menu needs " + plain);
        // Spin stopped at any angle (here: back toward the viewer) resets to front-facing, never the back.
        float stopped = LmNativeProtocol.previewFacing(LmNativeProtocol.spinAfterPacket(180f, false));
        check(broken, stopped == LmNativeProtocol.PREVIEW_FRONT_YAW && LmNativeProtocol.PREVIEW_FRONT_YAW == 180f
                && LmNativeProtocol.spinAfterPacket(97f, true) == 97f,
                "model preview stops front-facing (body yaw 180), was " + stopped);
        LmNativeProtocol.Needs inputs = LmNativeProtocol.analyze(withLayout("{}",
                "[{\"id\":\"a\",\"type\":\"text\"},{\"id\":\"b\",\"type\":\"paged_book\"}]"), id -> true);
        check(broken, inputs.hard().contains("textInput") && inputs.hard().contains("pagedBook")
                && inputs.metBy(client), "text field / paged book not drawable natively");
        LmNativeProtocol.Needs slider = LmNativeProtocol.analyze(withLayout("{}",
                "[{\"id\":\"s\",\"type\":\"slider\"}]"), id -> true);
        check(broken, slider.hard().contains("input:slider") && !slider.hardMetBy(client),
                "an unknown widget must not count as drawable");
        String standIn = UUID.nameUUIDFromBytes("lm-selftest-standin".getBytes()).toString();
        String formModel = "{\"playerModel\":{\"x\":330,\"y\":46,\"width\":86,\"height\":120,\"subject\":\"uuid:"
                + standIn + "\",\"parts\":{\"schema\":1,\"form\":{\"race\":\"saiyan\",\"form\":\"ssj\"},"
                + "\"bodyParts\":[{\"id\":\"saiyan_tail\",\"source\":\"dmzultra\",\"slot\":\"tail\"}]}}}";
        LmNativeProtocol.Needs offline = LmNativeProtocol.analyze(withLayout(formModel, "[]"), id -> false);
        check(broken, offline.soft().contains(LmNativeProtocol.FEATURE_FORMS)
                && offline.soft().contains(LmNativeProtocol.FEATURE_BODY_PARTS) && offline.hardMetBy(client)
                && !offline.metBy(client), "a made-up figure with a form must ask for the form feature");
        LmNativeProtocol.Needs online = LmNativeProtocol.analyze(withLayout(formModel, "[]"), id -> true);
        check(broken, online.soft().isEmpty() && online.metBy(client),
                "a real player's model draws from their own entity");
        LmNativeProtocol.Needs local = LmNativeProtocol.analyze(withLayout(
                "{\"playerModel\":{\"x\":1,\"y\":1,\"width\":9,\"height\":9,\"subject\":\"local\","
                        + "\"parts\":{\"form\":{\"race\":\"saiyan\"}}}}", "[]"), id -> false);
        check(broken, local.soft().isEmpty() && local.metBy(client), "the viewer's own model is always native");
        LmNativeProtocol.Needs fill = LmNativeProtocol.analyze(withLayout(
                "{\"fillScreen\":true,\"designWidth\":426,\"designHeight\":240}", "[]"), id -> true);
        check(broken, fill.hard().contains("fillScreen") && fill.metBy(client), "config editor box not drawable");
        check(broken, !client.contains(LmNativeProtocol.FEATURE_FORMS) && !client.contains(LmNativeProtocol.FEATURE_BODY_PARTS),
                "the client advertises form/body pieces it does not draw");
    }

    private static void routing(List<String> broken) {
        Set<String> client = new LinkedHashSet<>(LmNativeProtocol.CLIENT_FEATURES);
        LmNativeProtocol.Needs none = new LmNativeProtocol.Needs(Set.of(), Set.of());
        LmNativeProtocol.Needs softGap = new LmNativeProtocol.Needs(Set.of("playerModel"),
                Set.of(LmNativeProtocol.FEATURE_FORMS));
        LmNativeProtocol.Needs hardGap = new LmNativeProtocol.Needs(Set.of("input:slider"), Set.of());
        check(broken, LmNativeNetwork.decide(false, none, client, true) == LmNativeNetwork.Route.BRIDGE
                && LmNativeNetwork.decide(false, none, client, false) == LmNativeNetwork.Route.BRIDGE,
                "no LM client must stay on the bridge");
        check(broken, LmNativeNetwork.decide(true, none, client, true) == LmNativeNetwork.Route.NATIVE,
                "LM client + KaiPath: the LM client must win");
        check(broken, LmNativeNetwork.decide(true, softGap, client, true) == LmNativeNetwork.Route.BRIDGE
                && LmNativeNetwork.decide(true, hardGap, client, true) == LmNativeNetwork.Route.BRIDGE,
                "a menu the LM client cannot fully draw must go to KaiPath");
        check(broken, LmNativeNetwork.decide(true, softGap, client, false) == LmNativeNetwork.Route.NATIVE,
                "without KaiPath a missing preview hint still draws natively");
        check(broken, LmNativeNetwork.decide(true, hardGap, client, false) == LmNativeNetwork.Route.REFUSE,
                "without KaiPath a missing widget must be refused, never half drawn");
    }

    private static void fit(List<String> broken) {
        int m = UltraStyle.PANEL_MARGIN;
        check(broken, NativeMenuFit.MIN_SCALE == 0.5f, "scale floor moved off 0.5");
        check(broken, UltraStyle.MENU_W == 426 && UltraStyle.MENU_GAP == 6 && UltraStyle.MENU_BTN_H == 16,
                "menu metrics moved (426 wide, 6px gaps, 16px buttons): update this pin with them");
        // 1080p GUI scale 4 = 480x270, GUI scale 3 = 640x360.
        NativeMenuFit.Placement small = NativeMenuFit.place(480, 270, 426, 214, m, false, 0, 0);
        check(broken, small.scale() == 1f && !small.scrolls() && Math.abs(small.x() - 27f) < 0.01f,
                "a short menu must render at native size, centered");
        NativeMenuFit.Placement tall4 = NativeMenuFit.place(480, 270, 426, 400, m, false, 0, 0);
        check(broken, Math.abs(tall4.scale() - 0.625f) < 0.001f && !tall4.scrolls(),
                "a 400px menu must scale to fit GUI 4 without scrolling");
        NativeMenuFit.Placement tall3 = NativeMenuFit.place(640, 360, 426, 400, m, false, 0, 0);
        check(broken, Math.abs(tall3.scale() - 0.85f) < 0.001f && !tall3.scrolls(),
                "a 400px menu must scale to fit GUI 3 without scrolling");
        NativeMenuFit.Placement huge = NativeMenuFit.place(480, 270, 426, 600, m, false, 0, 0);
        check(broken, huge.scale() == 0.5f && Math.abs(huge.maxScroll() - 50f) < 0.01f,
                "past the 0.5x floor the whole menu scrolls");
        NativeMenuFit.Placement box = NativeMenuFit.place(480, 270, 1920, 1080, 0, true, 426, 240);
        check(broken, Math.abs(box.scale() - 1.125f) < 0.001f && !box.scrolls(),
                "the config editor box must fill the window");
    }

    private static void builders(List<String> broken) {
        Set<String> client = new LinkedHashSet<>(LmNativeProtocol.CLIENT_FEATURES);
        int seen = 0;
        for (String id : UltraScreenIds.BUILDER_IDS) {
            if (id == null || id.isBlank()) {
                continue;
            }
            List<GuiOpenPacket> packets;
            try {
                packets = UltraScreenSpec.captureSends(() -> UltraScreenSelfTest.probeBuilderForRoundTrip(id));
            } catch (Throwable t) {
                continue; // the Ultra self-test reports builder failures
            }
            for (GuiOpenPacket p : packets) {
                seen++;
                LmNativeProtocol.Needs n = LmNativeProtocol.analyze(p, uuid -> true);
                if (!n.hardMetBy(client)) {
                    broken.add("menu " + p.screenId() + "/" + p.pageId() + " needs " + n.missing(client));
                }
            }
        }
        check(broken, seen > 0, "no builder packets to check");
    }
}
