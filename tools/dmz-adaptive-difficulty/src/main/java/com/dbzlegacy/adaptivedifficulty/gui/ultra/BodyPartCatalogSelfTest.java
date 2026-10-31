package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import java.util.List;
import org.apache.logging.log4j.LogManager;

/**
 * Offline check for {@link BodyPartCatalog}. No Minecraft types.
 */
public final class BodyPartCatalogSelfTest {
    private BodyPartCatalogSelfTest() {}

    public static void main(String[] args) {
        if (BodyPartCatalog.formCount() < 200) {
            fail("catalog should load DMZUltra form keys, got " + BodyPartCatalog.formCount());
        }
        // The race tail is drawn by the client's race layer, never sent as a piece: a Saiyan in
        // base form resolves to nothing at all.
        List<BodyPartCatalog.Part> base = BodyPartCatalog.resolve("saiyan", "", "", "", "");
        if (!base.isEmpty()) {
            fail("base Saiyan must send no pieces (race tail is the race layer's), got " + base);
        }
        if (!BodyPartCatalog.SLOT_TAIL.equals(BodyPartCatalog.slot(BodyPartCatalog.SAIYAN_TAIL))) {
            fail("saiyan_tail must still read as a tail-slot id");
        }
        List<BodyPartCatalog.Part> wings =
                BodyPartCatalog.resolve("human", "sageforms", "craneascendant", "", "");
        boolean feather = false;
        for (BodyPartCatalog.Part part : wings) {
            if ("wings_feather".equals(part.id()) && BodyPartCatalog.SLOT_WINGS.equals(part.slot())) {
                feather = true;
            }
            if (BodyPartCatalog.SAIYAN_TAIL.equals(part.id())) {
                fail("crane form must not invent a saiyan tail");
            }
        }
        if (!feather) {
            fail("craneascendant should name wings_feather, got " + wings);
        }
        // DMZUltra 0.4.0 renamed the Noea-era Demon race to Makaijin; its forms moved with it.
        if (!BodyPartCatalog.resolve("demon", "abyssforms", "demongod", "", "").isEmpty()) {
            fail("the Noea-era demon/abyssforms key is gone in DMZUltra 0.4.0 and should send nothing");
        }
        List<BodyPartCatalog.Part> demon =
                BodyPartCatalog.resolve("makaijin", "abyssforms", "demongod", "", "");
        if (demon.stream().noneMatch(p -> "mkl_pinions".equals(p.id()))) {
            fail("makaijin demongod should name its 0.4.0 set pieces, got " + demon);
        }
        // DMZUltra 0.4.0 Soul Reaper forms use pieces every client can draw.
        List<BodyPartCatalog.Part> bankai =
                BodyPartCatalog.resolve("soulreaper", "zanpakutoforms", "bankai", "", "");
        if (bankai.stream().noneMatch(p -> "bankai_coat".equals(p.id()))
                || !BodyPartCatalog.shopPieceIds().containsAll(bankai.stream().map(BodyPartCatalog.Part::id).toList())) {
            fail("bankai should name bankai_coat from the client piece list, got " + bankai);
        }
        // A set piece's tail keeps the single-tail rule: it reads as a tail slot.
        List<BodyPartCatalog.Part> strain =
                BodyPartCatalog.resolve("bioandroid", "genomesaiyan", "saiyanstrain1", "", "");
        if (strain.stream().noneMatch(p -> "ystr_tail".equals(p.id()) && BodyPartCatalog.SLOT_TAIL.equals(p.slot()))) {
            fail("saiyanstrain1 should name ystr_tail in the tail slot, got " + strain);
        }
        if (!BodyPartCatalog.SLOT_BODY.equals(BodyPartCatalog.slot("mua_sash_tails"))) {
            fail("sash tails are cloth, not a tail slot");
        }
        List<BodyPartCatalog.Part> stacked =
                BodyPartCatalog.resolve("saiyan", "", "", "sageforms", "craneascendant");
        // Stack group is not a saiyan form key; craneascendant is human. The lookup
        // uses the player's race, so this misses. A real stack uses the same race.
        List<BodyPartCatalog.Part> sameRaceStack =
                BodyPartCatalog.resolve("human", "", "", "sageforms", "craneascendant");
        boolean stackWing = false;
        boolean stackTail = false;
        for (BodyPartCatalog.Part part : sameRaceStack) {
            if ("wings_feather".equals(part.id())) {
                stackWing = true;
            }
            if (BodyPartCatalog.SAIYAN_TAIL.equals(part.id())) {
                stackTail = true;
            }
        }
        if (!stackWing || stackTail) {
            fail("stack form wings and no race tail, got " + sameRaceStack + " / " + stacked);
        }
        List<BodyPartCatalog.Part> none =
                BodyPartCatalog.resolve("nope", "missing", "form", null, null);
        if (!none.isEmpty()) {
            fail("unknown form should send nothing, got " + none);
        }
        if (!BodyPartCatalog.resolve(null, null, null, null, null).isEmpty()) {
            fail("null identity should send nothing");
        }
        List<String> shop = BodyPartCatalog.shopPieceIds();
        if (shop.size() < 109) {
            fail("shop piece list should mirror the DMZUltra 0.4.0 client catalog, got " + shop.size());
        }
        if (!shop.contains("wings_feather") || !shop.contains("tail_fur") || !shop.contains("horns_vasto")) {
            fail("shop pieces should include the client meshes, got " + shop);
        }
        LogManager.getLogger(BodyPartCatalogSelfTest.class).info(
                "BodyPartCatalogSelfTest OK forms={} shopPieces={}",
                BodyPartCatalog.formCount(),
                shop.size());
    }

    private static void fail(String message) {
        throw new AssertionError(message);
    }
}
