package com.dmzlegacy.kaipath.ultra.consumers.legacymechanics;

import com.dmzultra.client.forms.FormParts;
import com.google.gson.JsonObject;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Offline checks for message 3 ({@link GuiModelSyncPacket}) and the world-sync cache in
 * {@link BodyPartPreview}. Uses the SRG stubs — no game runtime. Invoked from
 * {@code build-kaipath-overlay.sh} via {@link GuiOpenPacketRoundTripTest}.
 */
public final class GuiModelSyncPacketTest {
    private GuiModelSyncPacketTest() {}

    public static void main(String[] args) {
        roundTrip();
        caps();
        stateFromPartsRules();
        colorsContract();
        applySyncEndToEnd();
        mergeLookColorsFromMessage3();
        shopTailSuppression();
        System.out.println("GuiModelSyncPacketTest OK (message 3 + world sync)");
    }

    /**
     * The double-tail fix: a natural Saiyan tail and a chosen shop tail are drawn by two
     * systems that never see each other, so the payload has to say which one wins. A shop
     * tail plus {@code suppressNaturalTail:true} must resolve to exactly one tail mesh, and
     * the race bones must be told to stay hidden. Unset, or once the piece is unequipped, the
     * natural tail renders exactly as before.
     */
    private static void shopTailSuppression() {
        BodyPartPreview.clearSync();
        Minecraft mc = Minecraft.m_91087_();
        LocalPlayer local = new LocalPlayer();
        mc.f_91074_ = local;
        UUID id = local.m_20148_();

        String shopJson =
                "{\"schema\":1,\"subject\":\"uuid:" + id + "\","
                        + "\"bodyParts\":[{\"id\":\"tail_lion\",\"source\":\"dmzultra\",\"slot\":\"tail\"}],"
                        + "\"suppressNaturalTail\":true,\"extras\":[]}";
        BodyPartPreview.WorldState shop =
                BodyPartPreview.stateFromParts(shopJson, BodyPartPreview::piecesFor);
        assertTrue(shop.suppressNaturalTail(), "a chosen tail must carry suppressNaturalTail");

        BodyPartPreview.applySync(shopJson, GuiModelSyncPacket.REASON_LOGIN);
        assertTrue(
                BodyPartPreview.meshesFor(local).contains("tail_lion"),
                "uuid-self sync must serve the chosen tail mesh");

        // The character is still a natural-tail Saiyan (raceTailShown=true), so the kit must
        // leave exactly one tail mesh standing — its own — and never contribute the race one.
        Set<String> resolved = BodyPartPreview.worldMeshes(local, id, true);
        assertTrue(resolved.contains("tail_lion"), "the chosen tail mesh must survive");
        assertEq("tail meshes", 1, countTailMeshes(resolved));
        assertTrue(
                !resolved.contains(BodyPartWire.SAIYAN_TAIL_MESH),
                "the race tail mesh must not be added alongside a chosen piece");
        assertTrue(
                BodyPartPreview.suppressesRaceTail(local, true),
                "suppressNaturalTail must hide the race bones while the piece is on");

        // The race tail mesh is dropped from the merged look before the chosen pieces go in,
        // so the FormParts path cannot re-add what the race layer draws.
        FormParts.Design raced =
                new FormParts.Design(Set.of(BodyPartWire.SAIYAN_TAIL_MESH), 1, 2, 3, 4, 0f, 0f);
        FormParts.Look racedLook = new FormParts.Look(raced, 1, 2, 3);
        Object merged = BodyPartPreview.mergeLook(racedLook, resolved, null, shopJson);
        assertTrue(merged instanceof FormParts.Look, "shop-tail merge must build a Look");
        Set<String> parts = ((FormParts.Look) merged).design().parts();
        assertTrue(!parts.contains(BodyPartWire.SAIYAN_TAIL_MESH),
                "the merged look must not carry the race tail mesh");
        assertTrue(parts.contains("tail_lion"), "the merged look must carry the chosen tail");

        // Without the flag the race tail is left alone — the flag is what makes it hide.
        String plainJson =
                "{\"schema\":1,\"subject\":\"uuid:" + id + "\","
                        + "\"bodyParts\":[{\"id\":\"tail_lion\",\"source\":\"dmzultra\",\"slot\":\"tail\"}]}";
        assertTrue(
                !BodyPartPreview.stateFromParts(plainJson, BodyPartPreview::piecesFor)
                        .suppressNaturalTail(),
                "a payload without the flag must not suppress the race tail");
        BodyPartPreview.applySync(plainJson, GuiModelSyncPacket.REASON_LOGIN);
        assertTrue(
                !BodyPartPreview.suppressesRaceTail(local, true),
                "an unsuppressed entity must keep rendering its natural tail");

        // Unequipping goes back to false and clears the pieces: the natural tail returns.
        BodyPartPreview.applySync(
                "{\"schema\":1,\"subject\":\"uuid:" + id + "\","
                        + "\"bodyParts\":[],\"suppressNaturalTail\":false,\"extras\":[]}",
                GuiModelSyncPacket.REASON_CLEAR);
        assertTrue(
                BodyPartPreview.meshesFor(local).isEmpty(),
                "unequipping the tail must clear the chosen piece");
        assertTrue(
                !BodyPartPreview.suppressesRaceTail(local, true),
                "unequipping the tail must stop suppressing the race tail");

        // The aliased natural tail still yields to the race layer, exactly as before.
        BodyPartPreview.applySync(
                "{\"schema\":1,\"subject\":\"uuid:" + id + "\","
                        + "\"bodyParts\":[{\"id\":\"saiyan_tail\",\"source\":\"dmzultra\",\"slot\":\"tail\"}],"
                        + "\"suppressNaturalTail\":false,\"extras\":[]}",
                GuiModelSyncPacket.REASON_LOGIN);
        assertTrue(
                BodyPartPreview.worldMeshes(local, id, true).isEmpty(),
                "the aliased natural tail must still yield to the race layer");
        assertTrue(
                !BodyPartPreview.suppressesRaceTail(local, true),
                "a natural-tail-only player must never be flagged for suppression");

        BodyPartPreview.clearSync();
    }

    private static int countTailMeshes(Set<String> meshes) {
        int n = 0;
        for (String mesh : meshes) {
            if (mesh != null && mesh.startsWith("tail")) {
                n++;
            }
        }
        return n;
    }

    /**
     * The broadcast contract: payloads carry {@code colors} (client prefers them over the
     * gray reflective read) and a {@code uuid} subject — including the receiver's own uuid,
     * since the server sends owner + trackers the same snapshot.
     */
    private static void colorsContract() {
        BodyPartPreview.clearSync();
        Minecraft mc = Minecraft.m_91087_();
        LocalPlayer local = new LocalPlayer();
        mc.f_91074_ = local;

        String json =
                "{\"schema\":1,\"subject\":\"uuid:" + local.m_20148_() + "\","
                        + "\"bodyParts\":["
                        + "{\"id\":\"saiyan_tail\",\"source\":\"dmzultra\",\"slot\":\"tail\"}],"
                        + "\"colors\":{\"skin\":16777215,\"second\":16777215,\"hair\":2105380,"
                        + "\"own\":13186347,\"trim\":15320170,\"horn\":15722191,\"light\":8380671},"
                        + "\"extras\":[]}";
        BodyPartPreview.WorldState parsed =
                BodyPartPreview.stateFromParts(json, BodyPartPreview::piecesFor);
        assertTrue(parsed.colors() != null, "stateFromParts must keep the colors object");
        if (parsed.colors() != null) {
            assertEq("colors.hair", "2105380", parsed.colors().get("hair").getAsString());
            assertEq("colors.own", "13186347", parsed.colors().get("own").getAsString());
        }

        // uuid subject addressed to the receiver itself must feed the LOCAL state —
        // the server broadcasts to owner + trackers with one uuid-subject payload.
        BodyPartPreview.applySync(json, GuiModelSyncPacket.REASON_LOGIN);
        assertTrue(BodyPartPreview.meshesFor(local).contains("tail_fur"),
                "uuid-self sync must feed meshesFor for the local player");
        assertTrue(BodyPartPreview.colorsFor(local) != null,
                "uuid-self sync must expose colors for the local player");
        BodyPartPreview.clearSync();
    }

    private static void roundTrip() {
        String parts =
                "{\"schema\":1,\"subject\":\"local\",\"bodyParts\":["
                        + "{\"id\":\"saiyan_tail\",\"source\":\"dmzultra\",\"slot\":\"tail\"}],\"extras\":[]}";
        GuiModelSyncPacket original =
                new GuiModelSyncPacket(parts, GuiModelSyncPacket.REASON_LOGIN);
        FriendlyByteBuf buf = new FriendlyByteBuf();
        GuiModelSyncPacket.encode(original, buf);
        buf.writeExtraMarker("future-field-3"); // trailing junk after field 2 is ignored
        GuiModelSyncPacket decoded = GuiModelSyncPacket.decode(buf);
        assertEq("partsJson", parts, decoded.partsJson());
        assertEq("reason", "login", decoded.reason());

        // A 1-field payload (no reason) decodes with an empty reason — diagnostics only.
        FriendlyByteBuf shortBuf = new FriendlyByteBuf();
        GuiOpenPacket.writeUtf8(shortBuf, parts);
        GuiModelSyncPacket shortDecoded = GuiModelSyncPacket.decode(shortBuf);
        assertEq("short.partsJson", parts, shortDecoded.partsJson());
        assertEq("short.reason", "", shortDecoded.reason());
    }

    private static void caps() {
        GuiModelSyncPacket capped =
                new GuiModelSyncPacket("x".repeat(9000), "y".repeat(100));
        if (capped.partsJson().length() != GuiModelSyncPacket.PARTS_JSON_MAX) {
            fail("partsJson cap expected " + GuiModelSyncPacket.PARTS_JSON_MAX
                    + " got " + capped.partsJson().length());
        }
        if (capped.reason().length() != 32) {
            fail("reason cap expected 32 got " + capped.reason().length());
        }
    }

    private static void stateFromPartsRules() {
        // The saiyan_tail wire id aliases to the tail_fur mesh (DMZUltra fallback catalog
        // is used offline — no DMZUltra runtime needed).
        BodyPartPreview.WorldState alias =
                BodyPartPreview.stateFromParts(
                        "{\"schema\":1,\"subject\":\"local\",\"bodyParts\":["
                                + "{\"id\":\"saiyan_tail\",\"source\":\"dmzultra\",\"slot\":\"tail\"}],"
                                + "\"extras\":[]}",
                        BodyPartPreview::piecesFor);
        assertTrue(alias.meshes().contains("tail_fur"), "saiyan_tail must alias to tail_fur");
        assertTrue(alias.aliasTailOnly(), "an aliased-only tail must be flagged");
        assertTrue(alias.noTail().isEmpty(), "noTail must drop the aliased tail");

        // Defensive single-tail rule: never two tail-slot entries — first wins.
        BodyPartPreview.WorldState twoTails =
                BodyPartPreview.stateFromParts(
                        "{\"bodyParts\":["
                                + "{\"id\":\"saiyan_tail\",\"source\":\"dmzultra\",\"slot\":\"tail\"},"
                                + "{\"id\":\"tail_lion\",\"source\":\"dmzultra\",\"slot\":\"tail\"}]}",
                        BodyPartPreview::piecesFor);
        assertTrue(twoTails.meshes().contains("tail_fur"), "first tail-slot entry must win");
        assertTrue(!twoTails.meshes().contains("tail_lion"),
                "second tail-slot entry must be dropped");

        // A direct (non-aliased) tail is not flagged for the race-tail guard.
        BodyPartPreview.WorldState direct =
                BodyPartPreview.stateFromParts(
                        "{\"bodyParts\":[{\"id\":\"tail_lion\",\"source\":\"dmzultra\",\"slot\":\"tail\"}]}",
                        BodyPartPreview::piecesFor);
        assertTrue(!direct.aliasTailOnly(), "a direct tail mesh must not set aliasTailOnly");

        // Empty bodyParts clears the pieces but keeps the raw payload.
        String emptyJson = "{\"schema\":1,\"subject\":\"local\",\"bodyParts\":[],\"extras\":[]}";
        BodyPartPreview.WorldState cleared =
                BodyPartPreview.stateFromParts(emptyJson, BodyPartPreview::piecesFor);
        assertTrue(cleared.meshes().isEmpty(), "empty bodyParts must clear pieces");
        assertEq("raw payload kept", emptyJson, cleared.partsJson());

        // Missing bodyParts keeps the raw payload and no pieces.
        BodyPartPreview.WorldState noArray =
                BodyPartPreview.stateFromParts(
                        "{\"schema\":1,\"subject\":\"local\",\"extras\":[]}",
                        BodyPartPreview::piecesFor);
        assertTrue(noArray.meshes().isEmpty(), "a payload without bodyParts must add no pieces");

        // Garbage never throws.
        assertTrue(BodyPartPreview.stateFromParts("{not-json", BodyPartPreview::piecesFor)
                        .meshes().isEmpty(),
                "garbage partsJson must degrade to empty");
        assertTrue(BodyPartPreview.stateFromParts(null, BodyPartPreview::piecesFor)
                        .meshes().isEmpty(),
                "null partsJson must degrade to empty");
    }

    private static void applySyncEndToEnd() {
        BodyPartPreview.clearSync();
        Minecraft mc = Minecraft.m_91087_();
        LocalPlayer local = new LocalPlayer();
        mc.f_91074_ = local;

        String tailJson =
                "{\"schema\":1,\"subject\":\"local\",\"bodyParts\":["
                        + "{\"id\":\"saiyan_tail\",\"source\":\"dmzultra\",\"slot\":\"tail\"}],"
                        + "\"extras\":[]}";
        BodyPartPreview.applySync(tailJson, GuiModelSyncPacket.REASON_LOGIN);
        assertEq("cached raw parts", tailJson, BodyPartPreview.localPartsJson());
        assertTrue(BodyPartPreview.meshesFor(local).contains("tail_fur"),
                "subject:local sync must feed meshesFor for the local player");

        // A sync for another player keys by UUID and never touches the local state.
        LocalPlayer other = new LocalPlayer();
        UUID otherId = other.m_20148_();
        String otherJson =
                "{\"schema\":1,\"subject\":\"uuid:" + otherId + "\",\"bodyParts\":["
                        + "{\"id\":\"wings_feather\",\"source\":\"dmzultra\",\"slot\":\"wings\"}],"
                        + "\"extras\":[]}";
        BodyPartPreview.applySync(otherJson, "equip");
        assertTrue(BodyPartPreview.meshesFor(other).contains("wings_feather"),
                "uuid subject sync must key by player uuid");
        assertTrue(BodyPartPreview.meshesFor(local).contains("tail_fur"),
                "a remote sync must not clear the local state");
        assertTrue(BodyPartPreview.meshesFor(new LocalPlayer()).isEmpty(),
                "an unsynced player must get no extra pieces");

        // Server-authoritative replace: an empty bodyParts array clears the cache.
        BodyPartPreview.applySync(
                "{\"schema\":1,\"subject\":\"local\",\"bodyParts\":[],\"extras\":[]}",
                GuiModelSyncPacket.REASON_CLEAR);
        assertTrue(BodyPartPreview.meshesFor(local).isEmpty(),
                "empty bodyParts must clear the world pieces");

        // Disconnect drops everything (contract: state is per-session).
        BodyPartPreview.applySync(tailJson, GuiModelSyncPacket.REASON_LOGIN);
        BodyPartPreview.clearSync();
        assertTrue(BodyPartPreview.meshesFor(local).isEmpty(),
                "clearSync must drop the local state");
        assertTrue(BodyPartPreview.meshesFor(other).isEmpty(),
                "clearSync must drop remote state");
        assertEq("local raw cleared", "", BodyPartPreview.localPartsJson());
    }

    /**
     * The JLDK failure case, end to end: a message-3 payload with {@code parts.colors}
     * must reach the merged Look through encode → decode → handle → applySync →
     * colorsFor/partsJsonFor → mergeLook, beating the gray reflective defaults. Also pins
     * the {@code partsJson} thread: the raw payload must reach
     * {@link BodyPartPreview#partsJsonFor}, and mergeLook must derive colors from the
     * payload when the colors argument itself is absent.
     */
    private static void mergeLookColorsFromMessage3() {
        BodyPartPreview.clearSync();
        Minecraft mc = Minecraft.m_91087_();
        LocalPlayer local = new LocalPlayer();
        mc.f_91074_ = local;

        JsonObject colors = new JsonObject();
        colors.addProperty("skin", 0x123456);
        colors.addProperty("second", 0xABCDEF);
        colors.addProperty("hair", 0x111111);
        colors.addProperty("own", 0x2ECC71);
        colors.addProperty("trim", 0xBAD000);
        colors.addProperty("horn", 0xDEAD00);
        colors.addProperty("light", 0xFEED00);
        String payload =
                "{\"schema\":1,\"subject\":\"uuid:" + local.m_20148_() + "\","
                        + "\"bodyParts\":[{\"id\":\"saiyan_tail\",\"source\":\"dmzultra\",\"slot\":\"tail\"}],"
                        + "\"colors\":"
                        + colors
                        + ",\"extras\":[]}";

        // Full message-3 path: encode → decode → handle → applySync (inline on the stub).
        FriendlyByteBuf buf = new FriendlyByteBuf();
        GuiModelSyncPacket.encode(
                new GuiModelSyncPacket(payload, GuiModelSyncPacket.REASON_LOGIN), buf);
        GuiModelSyncPacket received = GuiModelSyncPacket.decode(buf);
        GuiModelSyncPacket.handle(received, () -> new NetworkEvent.Context());

        JsonObject server = BodyPartPreview.colorsFor(local);
        assertTrue(server != null, "message-3 colors must reach colorsFor");
        assertEq(
                "partsJsonFor threads the raw payload",
                payload,
                BodyPartPreview.partsJsonFor(local));

        // A remote player's reflective read comes back gray — seed exactly that.
        FormParts.Design grayDesign =
                new FormParts.Design(Set.of(), 0x808080, 0x808080, 0x808080, 0x808080, 0f, 0f);
        FormParts.Look gray = new FormParts.Look(grayDesign, 0x808080, 0x808080, 0x808080);

        Object merged =
                BodyPartPreview.mergeLook(
                        gray,
                        BodyPartPreview.meshesFor(local),
                        BodyPartPreview.colorsFor(local),
                        BodyPartPreview.partsJsonFor(local));
        assertTrue(
                merged instanceof FormParts.Look, "merge must build a Look with the dmzultra stubs");
        FormParts.Look look = (FormParts.Look) merged;
        assertEq("merged.skin", 0x123456, look.skin());
        assertEq("merged.second", 0xABCDEF, look.second());
        assertEq("merged.hair", 0x111111, look.hair());
        assertEq("merged.design.own", 0x2ECC71, look.design().own());
        assertEq("merged.design.trim", 0xBAD000, look.design().trim());
        assertEq("merged.design.horn", 0xDEAD00, look.design().horn());
        assertEq("merged.design.light", 0xFEED00, look.design().light());
        assertTrue(
                look.design().parts().contains("tail_fur"),
                "merged design must union the synced meshes");

        // The partsJson channel carries colors on its own when the colors arg is null.
        Object fromPayload = BodyPartPreview.mergeLook(gray, null, null, payload);
        assertTrue(
                fromPayload instanceof FormParts.Look, "payload-only merge must build a Look");
        FormParts.Look pl = (FormParts.Look) fromPayload;
        assertEq("payload-only skin", 0x123456, pl.skin());
        assertEq("payload-only own", 0x2ECC71, pl.design().own());

        // Nothing to apply → the original look comes back untouched.
        Object none = BodyPartPreview.mergeLook(gray, null, null, null);
        assertTrue(none == gray, "no pieces and no colors must keep the original look");

        BodyPartPreview.clearSync();
    }

    private static void assertEq(String name, int expected, int actual) {
        if (expected != actual) {
            fail(name + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(boolean cond, String message) {
        if (!cond) {
            fail(message);
        }
    }

    private static void assertEq(String name, String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            fail(name + ": expected=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static void fail(String message) {
        throw new AssertionError(message);
    }
}
