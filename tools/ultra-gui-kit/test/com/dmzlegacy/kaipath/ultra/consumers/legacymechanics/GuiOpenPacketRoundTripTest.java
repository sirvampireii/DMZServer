package com.dmzlegacy.kaipath.ultra.consumers.legacymechanics;

import com.dmzlegacy.kaipath.ultra.consumers.UltraGuiConsumerRegistry;
import com.dmzlegacy.kaipath.ultra.consumers.UltraGuiPartners;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Offline encode/decode self-test (no Minecraft runtime). Invoked from {@code build-kaipath-overlay.sh}.
 */
public final class GuiOpenPacketRoundTripTest {
    private GuiOpenPacketRoundTripTest() {}

    public static void main(String[] args) {
        BodyPartWireTest.main(args);
        GuiModelSyncPacketTest.main(args);
        PreviewFacingTest.main(args);
        String inputs =
                "[{\"id\":\"weight_value\",\"type\":\"text\",\"x\":8,\"y\":80,\"w\":200,\"h\":18,"
                        + "\"value\":\"50\",\"placeholder\":\"Weight\",\"maxLength\":8,"
                        + "\"args\":{\"bindInput\":\"weight_value\"}}]";
        GuiOpenPacket original =
                new GuiOpenPacket(
                        "difficulty",
                        "Difficulty",
                        "weights",
                        "[{\"id\":\"@difficulty:tab:weights\",\"label\":\"Weights\"}]",
                        "weights",
                        "[\"Line A\",\"Line B\"]",
                        "[{\"id\":\"@difficulty:weights:confirm\",\"label\":\"Confirm\",\"x\":20,\"y\":120,"
                                + "\"w\":150,\"h\":18,\"style\":\"confirm\","
                                + "\"args\":{\"bindInput\":\"weight_value\",\"screen\":\"difficulty\","
                                + "\"page\":\"weights\"}}]",
                        350,
                        220,
                        "{\"center\":true,\"margin\":6,\"titleAlign\":\"center\",\"system\":\"difficulty\"}",
                        inputs);

        FriendlyByteBuf buf = new FriendlyByteBuf();
        GuiOpenPacket.encode(original, buf);
        // Trailing junk after field 11 must be ignored
        buf.writeExtraMarker("future-field-12");

        GuiOpenPacket decoded = GuiOpenPacket.decode(buf);
        assertEq("screenId", original.screenId(), decoded.screenId());
        assertEq("title", original.title(), decoded.title());
        assertEq("pageId", original.pageId(), decoded.pageId());
        assertEq("tabsJson", original.tabsJson(), decoded.tabsJson());
        assertEq("activeTab", original.activeTab(), decoded.activeTab());
        assertEq("linesJson", original.linesJson(), decoded.linesJson());
        assertEq("buttonsJson", original.buttonsJson(), decoded.buttonsJson());
        assertEq(
                "panelWidth",
                String.valueOf(original.panelWidth()),
                String.valueOf(decoded.panelWidth()));
        assertEq(
                "panelHeight",
                String.valueOf(original.panelHeight()),
                String.valueOf(decoded.panelHeight()));
        assertEq("layoutJson", original.layoutJson(), decoded.layoutJson());
        assertEq("inputsJson", original.inputsJson(), decoded.inputsJson());

        // 10-field packet (no field 11) → inputsJson defaults to []
        GuiOpenPacket ten =
                new GuiOpenPacket(
                        "hub",
                        "Title",
                        "main",
                        "[]",
                        "home",
                        "[]",
                        "[]",
                        350,
                        300,
                        "{\"center\":true}");
        FriendlyByteBuf tenBuf = new FriendlyByteBuf();
        // Encode without field 11: copy encode but stop before inputs
        GuiOpenPacket.writeUtf8(tenBuf, ten.screenId());
        GuiOpenPacket.writeUtf8(tenBuf, ten.title());
        GuiOpenPacket.writeUtf8(tenBuf, ten.pageId());
        GuiOpenPacket.writeUtf8(tenBuf, ten.tabsJson());
        GuiOpenPacket.writeUtf8(tenBuf, ten.activeTab());
        GuiOpenPacket.writeUtf8(tenBuf, ten.linesJson());
        GuiOpenPacket.writeUtf8(tenBuf, ten.buttonsJson());
        tenBuf.m_130130_(ten.panelWidth());
        tenBuf.m_130130_(ten.panelHeight());
        GuiOpenPacket.writeUtf8(tenBuf, ten.layoutJson());
        GuiOpenPacket tenDecoded = GuiOpenPacket.decode(tenBuf);
        assertEq("ten.inputsJson", "[]", tenDecoded.inputsJson());
        assertEq("ten.screenId", "hub", tenDecoded.screenId());

        // Truncated packet (only screenId) → defaults
        FriendlyByteBuf shortBuf = new FriendlyByteBuf();
        GuiOpenPacket.writeUtf8(shortBuf, "hub");
        GuiOpenPacket shortDecoded = GuiOpenPacket.decode(shortBuf);
        assertEq("short.screenId", "hub", shortDecoded.screenId());
        assertEq("short.panelW", "350", String.valueOf(shortDecoded.panelWidth()));
        assertEq("short.panelH", "300", String.valueOf(shortDecoded.panelHeight()));
        assertEq("short.buttons", "[]", shortDecoded.buttonsJson());
        assertEq("short.inputs", "[]", shortDecoded.inputsJson());

        // GuiActionPacket verbatim round-trip (incl. bindInput value)
        GuiActionPacket action =
                new GuiActionPacket(
                        "difficulty",
                        "@difficulty:weights:confirm",
                        "{\"bindInput\":\"weight_value\",\"value\":\"50\"}");
        FriendlyByteBuf abuf = new FriendlyByteBuf();
        GuiActionPacket.encode(action, abuf);
        GuiActionPacket adecoded = GuiActionPacket.decode(abuf);
        assertEq("action.screenId", action.screenId(), adecoded.screenId());
        assertEq("action.actionId", action.actionId(), adecoded.actionId());
        assertEq("action.argsJson", action.argsJson(), adecoded.argsJson());
        String longArgs = "{\"v\":\"" + "x".repeat(1500) + "\"}";
        GuiActionPacket longAction = new GuiActionPacket("hub", "ping", longArgs);
        if (longAction.argsJson().length() <= 512 || longAction.argsJson().length() != longArgs.length()) {
            fail("client args cap must keep a 1500-char text-field payload, got "
                    + longAction.argsJson().length());
        }
        GuiActionPacket capped = new GuiActionPacket("hub", "ping", "y".repeat(3000));
        if (capped.argsJson().length() != 2048) {
            fail("client args cap must be 2048, got " + capped.argsJson().length());
        }

        if (!LmGuiNetwork.accepts("1") || !LmGuiNetwork.accepts("2") || !LmGuiNetwork.accepts("")) {
            fail("accepts() must tolerate any remote protocol");
        }
        if (!"legacymechanics:gui".equals(LmGuiNetwork.CHANNEL_ID)) {
            fail("CHANNEL_ID expected legacymechanics:gui got " + LmGuiNetwork.CHANNEL_ID);
        }
        if (!UltraGuiConsumerRegistry.ADVERTISED_FEATURES_JSON.contains("textInput")) {
            fail("ADVERTISED_FEATURES_JSON must include textInput");
        }
        for (String feat :
                new String[] {
                    "style",
                    "activeTab",
                    "titleAlign",
                    "playerModel",
                    "textInput",
                    "headerBottom",
                    "accent",
                    "palette",
                    "sound",
                    "buttonSkin",
                    "frame",
                    "pagedBook",
                    "portraitFrame",
                    "fixedFooter",
                    "combined.forms.v1",
                    "bodyParts",
                    "icon",
                    "bodyPartsSync"
                }) {
            if (!UltraGuiConsumerRegistry.ADVERTISED_FEATURES_JSON.contains(feat)) {
                fail("missing advertise feature: " + feat);
            }
        }
        com.google.gson.JsonObject inspectModel =
                com.google.gson.JsonParser.parseString(
                                "{\"subject\":\"uuid:11111111-1111-1111-1111-111111111111\","
                                        + "\"parts\":{\"subject\":\"local\"}}")
                        .getAsJsonObject();
        if (!"uuid:11111111-1111-1111-1111-111111111111"
                .equals(
                        LmDynamicScreen.previewSubjectToken(
                                inspectModel, inspectModel.getAsJsonObject("parts")))) {
            fail("top-level playerModel.subject must name the inspect preview");
        }
        com.google.gson.JsonObject partsOnly =
                com.google.gson.JsonParser.parseString(
                                "{\"subject\":\"uuid:22222222-2222-2222-2222-222222222222\"}")
                        .getAsJsonObject();
        if (!"uuid:22222222-2222-2222-2222-222222222222"
                .equals(LmDynamicScreen.previewSubjectToken(new com.google.gson.JsonObject(), partsOnly))) {
            fail("parts.subject must name the inspect preview when the top-level field is absent");
        }
        if (!"local".equals(LmDynamicScreen.previewSubjectToken(new com.google.gson.JsonObject(), null))) {
            fail("a missing subject must stay the local player");
        }

        // fixedFooter layout helpers (pure JSON — no Screen init)
        com.google.gson.JsonArray noFooter = com.google.gson.JsonParser.parseString(
                        "[{\"id\":\"a\",\"y\":40,\"h\":18}]")
                .getAsJsonArray();
        if (UltraBridgeFooter.footerReservePx(noFooter, 18, 10)
                != UltraBridgeFooter.DEFAULT_CLOSE_BAND_PX) {
            fail("no footer:true should keep default close band 28");
        }
        com.google.gson.JsonArray dualFooter =
                com.google.gson.JsonParser.parseString(
                                "["
                                        + "{\"id\":\"@x:nav:home\",\"h\":18,\"footer\":true},"
                                        + "{\"id\":\"@x:nav:hub\",\"h\":18,\"footer\":true}"
                                        + "]")
                        .getAsJsonArray();
        int reserve = UltraBridgeFooter.footerReservePx(dualFooter, 18, 10);
        if (reserve != 28) {
            fail("dual footer reserve expected 28 got " + reserve);
        }
        int pinY = UltraBridgeFooter.pinFooterY(300, reserve, 18);
        if (pinY < 270 || pinY > 282) {
            fail("pinFooterY out of band: " + pinY);
        }
        int bottom = UltraBridgeFooter.contentBottomAbs(10, 300, reserve);
        if (bottom != 10 + 300 - reserve) {
            fail("contentBottomAbs expected " + (10 + 300 - reserve) + " got " + bottom);
        }
        if (!UltraBridgeFooter.isFooter(dualFooter.get(0).getAsJsonObject())) {
            fail("isFooter should be true");
        }
        if (UltraBridgeFooter.isFooter(noFooter.get(0).getAsJsonObject())) {
            fail("isFooter should be false without footer field");
        }

        // Multi-mod partners: LM is one entry; list must stay open for other mods
        if (UltraGuiPartners.DEFAULT_PARTNERS == null
                || UltraGuiPartners.DEFAULT_PARTNERS.length < 1) {
            fail("UltraGuiPartners.DEFAULT_PARTNERS must list at least one partner");
        }
        boolean hasLm = false;
        for (String p : UltraGuiPartners.DEFAULT_PARTNERS) {
            if ("legacymechanics".equals(p)) {
                hasLm = true;
            }
        }
        if (!hasLm) {
            fail("DEFAULT_PARTNERS should still include legacymechanics as a partner example");
        }
        // channel-agnostic: same advertise JSON is not tied to a single mod id string
        if (UltraGuiConsumerRegistry.ADVERTISED_FEATURES_JSON.contains("legacymechanics")) {
            fail("ADVERTISED_FEATURES_JSON must not embed a mod id");
        }

        com.google.gson.JsonObject iconGood = new com.google.gson.JsonObject();
        iconGood.addProperty("icon", "Minecraft:Diamond");
        if (!"minecraft:diamond".equals(LmDynamicScreen.iconId(iconGood))) {
            fail("iconId should lowercase a valid namespace:path");
        }
        com.google.gson.JsonObject iconBad = new com.google.gson.JsonObject();
        iconBad.addProperty("icon", "not-an-rl");
        if (!"".equals(LmDynamicScreen.iconId(iconBad))) {
            fail("iconId must ignore ids without a namespace");
        }
        if (!"".equals(LmDynamicScreen.iconId(new com.google.gson.JsonObject()))) {
            fail("iconId must ignore a missing icon field");
        }

        try {
            assertPartnerDiscovery();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            fail("partner discovery: " + ex.getMessage());
        }

        // fitLabel ASCII patcher must rewrite mojibake ellipsis constants
        try {
            java.nio.file.Path patcher =
                    java.nio.file.Path.of("tools/ultra-gui-kit/patch_fitlabel_ascii.py");
            if (!java.nio.file.Files.isRegularFile(patcher)) {
                // When cwd is the kit dir during some runners
                patcher = java.nio.file.Path.of("patch_fitlabel_ascii.py");
            }
            if (!java.nio.file.Files.isRegularFile(patcher)) {
                fail("missing patch_fitlabel_ascii.py");
            }
        } catch (Throwable t) {
            fail("fitLabel patcher check failed: " + t.getMessage());
        }

        System.out.println(
                "GuiOpenPacketRoundTripTest OK (11 fields + discovery + icon + advertise)");
    }

    private static void assertPartnerDiscovery() throws java.io.IOException {
        java.nio.file.Path tmp = java.nio.file.Files.createTempDirectory("kaipath-partners");
        try {
            java.nio.file.Path mods = tmp.resolve("mods");
            java.nio.file.Files.createDirectories(mods);
            java.nio.file.Path config = tmp.resolve("kaipath-ultra-partners.txt");
            java.nio.file.Files.writeString(config, "# comment\ntownyplus  # extra\n\nnot an id!\n");
            writeTinyJar(mods.resolve("myshop.jar"), "META-INF/kaipath-ultra.txt", "myshop\n");
            writeTinyJar(
                    mods.resolve("fromtoml.jar"),
                    "META-INF/kaipath-ultra.txt",
                    "# empty marker\n",
                    "META-INF/mods.toml",
                    "modId=\"otheremod\"\n");
            writeTinyJar(mods.resolve("nomarker.jar"), "META-INF/mods.toml", "modId=\"ignoredmod\"\n");
            java.util.List<String> ids = UltraGuiPartners.discoverIds(config, mods);
            for (String need : new String[] {"legacymechanics", "myshop", "townyplus", "otheremod"}) {
                if (!ids.contains(need)) {
                    fail("discoverIds missing " + need + " got " + ids);
                }
            }
            if (ids.contains("ignoredmod")) {
                fail("discoverIds accepted a jar without the marker: " + ids);
            }
        } finally {
            deleteTree(tmp);
        }
    }

    private static void writeTinyJar(java.nio.file.Path jar, String... nameThenBytes) throws java.io.IOException {
        try (java.util.zip.ZipOutputStream zip =
                new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(jar))) {
            for (int i = 0; i + 1 < nameThenBytes.length; i += 2) {
                zip.putNextEntry(new java.util.zip.ZipEntry(nameThenBytes[i]));
                zip.write(nameThenBytes[i + 1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static void deleteTree(java.nio.file.Path root) throws java.io.IOException {
        if (root == null || !java.nio.file.Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(root)) {
            java.util.List<java.nio.file.Path> paths = walk.sorted(java.util.Comparator.reverseOrder()).toList();
            for (java.nio.file.Path path : paths) {
                java.nio.file.Files.deleteIfExists(path);
            }
        }
    }

    private static void assertEq(String name, String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            fail(name + ": expected=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static void fail(String msg) {
        System.err.println("FAIL: " + msg);
        System.exit(1);
    }
}
