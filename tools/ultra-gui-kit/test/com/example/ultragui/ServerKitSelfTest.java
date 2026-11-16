package com.example.ultragui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.network.FriendlyByteBuf;

/** Compile-and-behavior check for the server kit (SRG stubs + Gson). */
public final class ServerKitSelfTest {
    public static void main(String[] args) throws Exception {
        roundTripOpen();
        sanitizeKeepsJson();
        actionCap();
        featuresRoundTrip();
        modelSyncRoundTrip();
        sketchAndFixture();
        announceAndLink();
        System.out.println("ServerKitSelfTest OK");
    }

    /** kaipath:partners payload is writeUtf(modId); the link degrades to false without KaiPath. */
    private static void announceAndLink() {
        byte[] p = UltraGuiAnnounce.payload("MyShop");
        if (p.length != 7 || p[0] != 6 || !new String(p, 1, 6, java.nio.charset.StandardCharsets.UTF_8).equals("myshop")) {
            throw new AssertionError("announce payload must be VarInt length + lowercase UTF-8 id");
        }
        byte[] longId = UltraGuiAnnounce.payload("a".repeat(200));
        if ((longId[0] & 0xFF) != (0x80 | (200 & 0x7F)) || longId[1] != 1 || longId.length != 202) {
            throw new AssertionError("announce payload VarInt must use two bytes for a 200-byte id");
        }
        // KaiPath is not on this classpath: every call reports false, nothing throws.
        if (UltraGuiLink.kaiPathPresent()
                || UltraGuiLink.link("myshop", (pl, a) -> {}, null)
                || UltraGuiLink.sendOpen("myshop", new Object(), new byte[0])
                || UltraGuiLink.sendModelSync("myshop", new Object(), new byte[0])) {
            throw new AssertionError("UltraGuiLink must report false when KaiPath is absent");
        }
    }

    private static void roundTripOpen() {
        UltraGuiOpenPacket open =
                new UltraGuiOpenPacket(
                        "hello",
                        "My Shop",
                        "main",
                        "[]",
                        "",
                        "[\"Welcome.\"]",
                        "[{\"id\":\"ping\",\"label\":\"Ping\",\"style\":\"confirm\"}]",
                        350,
                        300,
                        "{\"titleAlign\":\"left\",\"margin\":10}",
                        "[]");
        if (!open.layoutJson().contains("\"titleAlign\":\"center\"")) {
            fail("open layout must force titleAlign=center");
        }
        FriendlyByteBuf buf = new FriendlyByteBuf();
        UltraGuiOpenPacket.encode(open, buf);
        UltraGuiOpenPacket decoded = UltraGuiOpenPacket.decode(buf);
        eq("screenId", open.screenId(), decoded.screenId());
        eq("title", open.title(), decoded.title());
        eq("pageId", open.pageId(), decoded.pageId());
        eq("lines", open.linesJson(), decoded.linesJson());
        eq("buttons", open.buttonsJson(), decoded.buttonsJson());
        eq("panelW", String.valueOf(open.panelWidth()), String.valueOf(decoded.panelWidth()));
        eq("panelH", String.valueOf(open.panelHeight()), String.valueOf(decoded.panelHeight()));
        eq("layout", open.layoutJson(), decoded.layoutJson());
        eq("inputs", open.inputsJson(), decoded.inputsJson());
    }

    private static void sanitizeKeepsJson() {
        String broken = UltraGuiOpenPacket.sanitizeLayoutJson("{not-json", 350, 300);
        JsonObject fallback = JsonParser.parseString(broken).getAsJsonObject();
        if (!"center".equals(fallback.get("titleAlign").getAsString())) {
            fail("invalid layout must fall back with titleAlign=center");
        }
        StringBuilder raw = new StringBuilder();
        raw.append("{\"titleAlign\":\"left\",\"margin\":10,\"playerModel\":{\"x\":1,\"parts\":{\"form\":\"");
        while (raw.length() < 5000) {
            raw.append("abcdefghij");
        }
        raw.append("\"}}}");
        String sanitized = UltraGuiOpenPacket.sanitizeLayoutJson(raw.toString(), 350, 300);
        if (sanitized.length() > UltraGuiOpenPacket.LAYOUT_JSON_MAX) {
            fail("sanitized layout longer than cap: " + sanitized.length());
        }
        JsonObject o = JsonParser.parseString(sanitized).getAsJsonObject();
        if (!"center".equals(o.get("titleAlign").getAsString())) {
            fail("oversized layout must keep titleAlign=center");
        }
        if (o.has("playerModel") && o.getAsJsonObject("playerModel").has("parts")) {
            fail("oversized layout must drop playerModel.parts before cutting JSON");
        }
        String cut = raw.substring(0, UltraGuiOpenPacket.LAYOUT_JSON_MAX);
        try {
            JsonParser.parseString(cut).getAsJsonObject();
            fail("test fixture substring was accidentally valid JSON");
        } catch (Throwable expected) {
            // substring trim is the bug sanitize exists to avoid
        }
    }

    private static void actionCap() {
        if (UltraGuiActionPacket.ARGS_JSON_MAX != 2048) {
            fail("ARGS_JSON_MAX must be 2048");
        }
        String payload = "{\"v\":\"" + "x".repeat(1500) + "\"}";
        UltraGuiActionPacket kept = new UltraGuiActionPacket("hello", "@hello:do:ping", payload);
        if (kept.argsJson().length() != payload.length()) {
            fail("1500-char args trimmed to " + kept.argsJson().length());
        }
        UltraGuiActionPacket capped = new UltraGuiActionPacket("hello", "ping", "y".repeat(3000));
        if (capped.argsJson().length() != 2048) {
            fail("args cap got " + capped.argsJson().length());
        }
        FriendlyByteBuf buf = new FriendlyByteBuf();
        UltraGuiActionPacket.encode(kept, buf);
        UltraGuiActionPacket decoded = UltraGuiActionPacket.decode(buf);
        eq("action.screen", kept.screenId(), decoded.screenId());
        eq("action.id", kept.actionId(), decoded.actionId());
        eq("action.args", kept.argsJson(), decoded.argsJson());
    }

    private static void featuresRoundTrip() {
        String json = "[\"style\",\"combined.forms.v1\"]";
        UltraGuiFeaturesPacket msg = new UltraGuiFeaturesPacket(json);
        FriendlyByteBuf buf = new FriendlyByteBuf();
        UltraGuiFeaturesPacket.encode(msg, buf);
        UltraGuiFeaturesPacket decoded = UltraGuiFeaturesPacket.decode(buf);
        eq("features", json, decoded.featuresJson());
    }

    private static void modelSyncRoundTrip() {
        String parts = "{\"schema\":1,\"subject\":\"local\",\"bodyParts\":["
                + "{\"id\":\"saiyan_tail\",\"source\":\"dmzultra\",\"slot\":\"tail\"}],\"extras\":[]}";
        UltraGuiModelSyncPacket msg =
                new UltraGuiModelSyncPacket(parts, UltraGuiModelSyncPacket.REASON_LOGIN);
        FriendlyByteBuf buf = new FriendlyByteBuf();
        UltraGuiModelSyncPacket.encode(msg, buf);
        UltraGuiModelSyncPacket decoded = UltraGuiModelSyncPacket.decode(buf);
        eq("modelSync.parts", parts, decoded.partsJson());
        eq("modelSync.reason", "login", decoded.reason());

        // A 1-field payload (no reason field) decodes with an empty reason.
        FriendlyByteBuf shortBuf = new FriendlyByteBuf();
        UltraGuiOpenPacket.writeUtf8(shortBuf, parts);
        eq("modelSync.missingReason", "", UltraGuiModelSyncPacket.decode(shortBuf).reason());

        // Reason token is capped for diagnostics.
        UltraGuiModelSyncPacket longReason =
                new UltraGuiModelSyncPacket(parts, "x".repeat(100));
        if (longReason.reason().length() != 32) {
            fail("modelSync reason cap expected 32 got " + longReason.reason().length());
        }

        // Oversized payloads degrade: drop headBones, then form — never a mid-JSON cut.
        StringBuilder both = new StringBuilder("{\"schema\":1,\"subject\":\"local\",");
        both.append("\"headBones\":{\"token\":\"").append("a".repeat(3000)).append("\"},");
        both.append("\"form\":{\"race\":\"").append("b".repeat(9000)).append("\"},");
        both.append("\"bodyParts\":[],\"extras\":[]}");
        String dropped = UltraGuiModelSyncPacket.sanitizePartsJson(both.toString());
        if (dropped.length() > UltraGuiModelSyncPacket.PARTS_JSON_MAX) {
            fail("degraded parts still too long: " + dropped.length());
        }
        JsonObject dropOb = JsonParser.parseString(dropped).getAsJsonObject();
        if (dropOb.has("headBones") || dropOb.has("form")) {
            fail("oversized parts must drop headBones then form: " + dropped.substring(0, 80));
        }
        if (!"local".equals(dropOb.get("subject").getAsString())) {
            fail("degraded parts must keep the subject");
        }

        // Nothing left to drop: fall back to the minimal empty-parts object.
        StringBuilder huge = new StringBuilder("{\"schema\":1,\"subject\":\"local\",\"bodyParts\":[");
        huge.append("{\"id\":\"x\",\"source\":\"y\",\"pad\":\"").append("c".repeat(9000)).append("\"}],");
        huge.append("\"extras\":[]}");
        JsonObject fallback = JsonParser.parseString(
                UltraGuiModelSyncPacket.sanitizePartsJson(huge.toString())).getAsJsonObject();
        if (!fallback.get("bodyParts").isJsonArray()
                || !fallback.getAsJsonArray("bodyParts").isEmpty()) {
            fail("unshrinkable parts must degrade to the empty fallback");
        }

        // Garbage falls back too.
        JsonObject garbage = JsonParser.parseString(
                UltraGuiModelSyncPacket.sanitizePartsJson("{not-json")).getAsJsonObject();
        if (!garbage.get("bodyParts").isJsonArray()) {
            fail("garbage parts must degrade to the empty fallback");
        }
    }

    private static void sketchAndFixture() throws Exception {
        Path fixture = Path.of(System.getProperty("ultragui.fixture", ""));
        Path sketch = Path.of(System.getProperty("ultragui.sketch", ""));
        if (!Files.isRegularFile(fixture) || !Files.isRegularFile(sketch)) {
            fail("missing fixture or sketch path");
        }
        String sketchText = Files.readString(sketch);
        if (sketchText.contains("buttonSkin") || sketchText.contains("portraitFrame")) {
            fail("HelloWorld still teaches buttonSkin or portraitFrame");
        }
        if (!sketchText.contains("bodyParts")) {
            fail("HelloWorld must show playerModel.parts.bodyParts");
        }
        JsonObject root = JsonParser.parseString(Files.readString(fixture)).getAsJsonObject();
        JsonArray buttons = root.getAsJsonObject("open").getAsJsonArray("buttonsJson");
        for (JsonElement el : buttons) {
            JsonObject b = el.getAsJsonObject();
            if (b.has("palette") || b.has("sound") || b.has("buttonSkin")) {
                fail("fixture button still has palette/sound/buttonSkin: " + b);
            }
            if (!b.has("style") || !b.has("id") || !b.has("label")) {
                fail("fixture button missing proven fields: " + b);
            }
        }
        JsonObject player =
                root.getAsJsonObject("open").getAsJsonObject("layoutJson").getAsJsonObject("playerModel");
        if (player.has("portraitFrame")) {
            fail("fixture playerModel still has portraitFrame");
        }
        if (!player.has("parts")
                || !player.getAsJsonObject("parts").has("bodyParts")
                || !player.getAsJsonObject("parts").get("bodyParts").isJsonArray()
                || player.getAsJsonObject("parts").getAsJsonArray("bodyParts").isEmpty()) {
            fail("fixture playerModel.parts.bodyParts must list pieces");
        }
        boolean forms = false;
        boolean bodyParts = false;
        boolean bodyPartsSync = false;
        for (JsonElement el : root.getAsJsonArray("advertiseFeatures")) {
            if ("combined.forms.v1".equals(el.getAsString())) {
                forms = true;
            }
            if ("bodyParts".equals(el.getAsString())) {
                bodyParts = true;
            }
            if ("bodyPartsSync".equals(el.getAsString())) {
                bodyPartsSync = true;
            }
        }
        if (!forms) {
            fail("fixture advertiseFeatures missing combined.forms.v1");
        }
        if (!bodyParts) {
            fail("fixture advertiseFeatures missing bodyParts");
        }
        if (!bodyPartsSync) {
            fail("fixture advertiseFeatures missing bodyPartsSync");
        }
        // Message 3 fixture: gated world model sync twin.
        if (!sketchText.contains("UltraGuiModelSyncPacket")) {
            fail("HelloWorld must show message-3 registration");
        }
        if (!sketchText.contains("bodyPartsSync")) {
            fail("HelloWorld must gate message 3 on the bodyPartsSync advertisement");
        }
        JsonObject modelSync = root.getAsJsonObject("modelSync");
        if (modelSync == null
                || modelSync.get("messageId").getAsInt() != 3
                || !"bodyPartsSync".equals(modelSync.get("gate").getAsString())
                || !modelSync.has("partsJson")
                || !modelSync.has("reason")) {
            fail("fixture modelSync must twin message 3 (id 3, gate bodyPartsSync, partsJson, reason)");
        }
    }

    private static void eq(String name, String expect, String actual) {
        if (!expect.equals(actual)) {
            fail(name + " expected [" + expect + "] got [" + actual + "]");
        }
    }

    private static void fail(String message) {
        throw new AssertionError(message);
    }

    private ServerKitSelfTest() {}
}
