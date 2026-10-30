package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import java.util.List;
import org.apache.logging.log4j.LogManager;

/**
 * Offline check for {@link BodyPartCatalog}. No Minecraft types.
 */
public final class BodyPartCatalogSelfTest {
    private BodyPartCatalogSelfTest() {}

    public static void main(String[] args) {
        if (BodyPartCatalog.formCount() < 80) {
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
        List<BodyPartCatalog.Part> demon =
                BodyPartCatalog.resolve("demon", "abyssforms", "demongod", "", "");
        boolean horn = false;
        boolean wing = false;
        boolean demonTail = false;
        for (BodyPartCatalog.Part part : demon) {
            if ("horns_swept".equals(part.id()) && BodyPartCatalog.SLOT_HORNS.equals(part.slot())) {
                horn = true;
            }
            if ("wings_bat_small".equals(part.id()) && BodyPartCatalog.SLOT_WINGS.equals(part.slot())) {
                wing = true;
            }
            if ("tail_spade".equals(part.id()) && BodyPartCatalog.SLOT_TAIL.equals(part.slot())) {
                demonTail = true;
            }
        }
        if (!horn || !wing || !demonTail) {
            fail("demongod should name horns, wings, and a tail, got " + demon);
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
        if (shop.size() < 60) {
            fail("shop piece list should mirror the DMZUltra client catalog, got " + shop.size());
        }
        if (!shop.contains("wings_feather") || !shop.contains("tail_fur")) {
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
