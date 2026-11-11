package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;

/**
 * Offline check for {@link BodyPartCatalog}. No Minecraft types.
 *
 * <p>{@link #check} runs every assertion against one catalog and is also the gate a catalog
 * generated at boot must pass ({@link BodyPartCatalog#select}). {@link #main} checks both shipped
 * catalogs with their own counts, the selection by Ultra version, and (given DMZ Ultra jars as
 * arguments) that the Java generator reproduces the shipped catalog for that jar.
 *
 * <pre>java -cp &lt;classes;gson;log4j;forge&gt; ...BodyPartCatalogSelfTest [dmzultra-X.jar ...]</pre>
 */
public final class BodyPartCatalogSelfTest {
    private BodyPartCatalogSelfTest() {}

    /**
     * What a catalog must hold. Shipped catalogs have exact counts; a generated one must hold at
     * least what the newest shipped catalog does, and every piece of that catalog's shop.
     */
    public record Expect(
            String label, int shop, int forms, int designs, boolean exact, boolean newRaces, List<String> mustShop) {
        static final List<String> CLIENT_MESHES = List.of("wings_feather", "tail_fur", "horns_vasto");

        static Expect forShipped(String version) {
            return "0.5.3".equals(version)
                    ? new Expect("0.5.3", 115, 410, 408, true, true, CLIENT_MESHES)
                    : new Expect("0.4.0", 109, 220, 220, true, false, CLIENT_MESHES);
        }

        static Expect newerThan(BodyPartCatalog.Catalog base) {
            List<String> must = new ArrayList<>(CLIENT_MESHES);
            must.addAll(base.shopPieces());
            return new Expect("generated, newer than " + base.version(), base.shopPieces().size(),
                    base.forms().size(), base.designs().size(), false,
                    UltraVersion.compare(base.version(), "0.5.3") >= 0, List.copyOf(must));
        }
    }

    /** Every assertion against {@code c}; failures name the catalog version under test. */
    public static List<String> check(BodyPartCatalog.Catalog c, Expect e) {
        List<String> out = new ArrayList<>();
        String tag = "[catalog " + c.version() + " " + c.origin() + "] ";
        count(out, tag, "forms", c.forms().size(), e.forms(), e.exact());
        count(out, tag, "shop pieces", c.shopPieces().size(), e.shop(), e.exact());
        count(out, tag, "designs", c.designs().size(), e.designs(), e.exact());
        // The race tail is drawn by the client's race layer, never sent as a piece: a Saiyan in
        // base form resolves to nothing at all.
        List<BodyPartCatalog.Part> base = c.resolve("saiyan", "", "", "", "");
        if (!base.isEmpty()) {
            out.add(tag + "base Saiyan must send no pieces (race tail is the race layer's), got " + base);
        }
        if (!BodyPartCatalog.SLOT_TAIL.equals(BodyPartCatalog.slot(BodyPartCatalog.SAIYAN_TAIL))) {
            out.add(tag + "saiyan_tail must still read as a tail-slot id");
        }
        List<BodyPartCatalog.Part> wings = c.resolve("human", "sageforms", "craneascendant", "", "");
        boolean feather = false;
        for (BodyPartCatalog.Part part : wings) {
            if ("wings_feather".equals(part.id()) && BodyPartCatalog.SLOT_WINGS.equals(part.slot())) {
                feather = true;
            }
            if (BodyPartCatalog.SAIYAN_TAIL.equals(part.id())) {
                out.add(tag + "crane form must not invent a saiyan tail");
            }
        }
        if (!feather) {
            out.add(tag + "craneascendant should name wings_feather, got " + wings);
        }
        // DMZUltra 0.4.0 renamed the Noea-era Demon race to Makaijin; its forms moved with it.
        if (!c.resolve("demon", "abyssforms", "demongod", "", "").isEmpty()) {
            out.add(tag + "the Noea-era demon/abyssforms key is gone since DMZUltra 0.4.0 and should send nothing");
        }
        List<BodyPartCatalog.Part> demon = c.resolve("makaijin", "abyssforms", "demongod", "", "");
        if (demon.stream().noneMatch(p -> "mkl_pinions".equals(p.id()))) {
            out.add(tag + "makaijin demongod should name its set pieces, got " + demon);
        }
        // 4.7.38: this server renamed Glind's kishinforms group to godforms; the LM-side alias
        // (sim/form-parts-server-aliases.json) gives the godforms key the kishinforms pieces and colors.
        List<String> glindIds = ids(c.resolve("glind", "godforms", "demongodawakened", "", ""));
        List<String> demonGod = List.of("demongod_crown", "demongod_pauldrons", "demongod_breastplate",
                "demongod_vambraces", "demongod_tassets");
        if (!glindIds.equals(demonGod)) {
            out.add(tag + "glind/godforms/demongodawakened should name the 5 demon god pieces, got " + glindIds);
        }
        if (c.resolve("glind", "godforms", "demongodtrue", "", "").size() != 7) {
            out.add(tag + "glind/godforms/demongodtrue should name 7 pieces");
        }
        BodyPartCatalog.Design glindColors = c.design("glind", "godforms", "demongodawakened", "", "");
        if (!new BodyPartCatalog.Design(4915330, 13151080, 4077125, 12482275).equals(glindColors)) {
            out.add(tag + "glind/godforms/demongodawakened colors should match kishinforms, got " + glindColors);
        }
        if (c.resolve("glind", "kishinforms", "demongodawakened", "", "").size() != 5) {
            out.add(tag + "the kishinforms key must still resolve");
        }
        // Soul Reaper forms use pieces every client can draw.
        List<BodyPartCatalog.Part> bankai = c.resolve("soulreaper", "zanpakutoforms", "bankai", "", "");
        if (bankai.stream().noneMatch(p -> "bankai_coat".equals(p.id()))
                || !c.shopPieces().containsAll(ids(bankai))) {
            out.add(tag + "bankai should name bankai_coat from the client piece list, got " + bankai);
        }
        // A set piece's tail keeps the single-tail rule: it reads as a tail slot.
        List<BodyPartCatalog.Part> strain = c.resolve("bioandroid", "genomesaiyan", "saiyanstrain1", "", "");
        if (strain.stream().noneMatch(p -> "ystr_tail".equals(p.id()) && BodyPartCatalog.SLOT_TAIL.equals(p.slot()))) {
            out.add(tag + "saiyanstrain1 should name ystr_tail in the tail slot, got " + strain);
        }
        if (!BodyPartCatalog.SLOT_BODY.equals(BodyPartCatalog.slot("mua_sash_tails"))) {
            out.add(tag + "sash tails are cloth, not a tail slot");
        }
        // Stack group is not a saiyan form key; craneascendant is human. The lookup uses the
        // player's race, so this misses. A real stack uses the same race.
        List<BodyPartCatalog.Part> stacked = c.resolve("saiyan", "", "", "sageforms", "craneascendant");
        List<BodyPartCatalog.Part> sameRaceStack = c.resolve("human", "", "", "sageforms", "craneascendant");
        List<String> stackIds = ids(sameRaceStack);
        if (!stackIds.contains("wings_feather") || stackIds.contains(BodyPartCatalog.SAIYAN_TAIL)) {
            out.add(tag + "stack form wings and no race tail, got " + sameRaceStack + " / " + stacked);
        }
        if (!c.resolve("nope", "missing", "form", null, null).isEmpty()) {
            out.add(tag + "unknown form should send nothing");
        }
        if (!c.resolve(null, null, null, null, null).isEmpty()) {
            out.add(tag + "null identity should send nothing");
        }
        for (String id : e.mustShop()) {
            if (!c.shopPieces().contains(id)) {
                out.add(tag + "shop pieces should include " + id);
            }
        }
        List<String> buffed = c.shopPieces().stream().filter(id -> id.endsWith("_buffed")).toList();
        List<String> crop = ids(c.resolve("cerealian", "hair", "crop", "", ""));
        if (e.newRaces()) {
            // DMZ Ultra 0.5.3: new races, 6 buffed shop pieces, re-authored gamma frames, the
            // Kishin set under its demongodforms alias too.
            if (crop.isEmpty()) {
                out.add(tag + "cerealian/hair/crop should resolve in a 0.5.3 catalog");
            }
            if (buffed.size() < 6) {
                out.add(tag + "the 6 *_buffed shop pieces are missing, got " + buffed);
            }
            if (!ids(c.resolve("human", "gammaframeforms", "gammaframe", "", "")).contains("gm_helmet")) {
                out.add(tag + "the gamma frame should use the 0.5.3 gm_* pieces");
            }
            for (String g : List.of("demongodforms", "godforms")) {
                if (!ids(c.resolve("glind", g, "demongodawakened", "", "")).equals(demonGod)
                        || c.resolve("glind", g, "demongodtrue", "", "").size() != 7) {
                    out.add(tag + "glind/" + g + " alias keys should carry the kishinforms pieces");
                }
            }
            for (String[] k : new String[][] {{"heeter", "superforms", "superheeter"},
                    {"bleach_hollow", "hollowforms", "menos"}}) {
                if (c.forms().keySet().stream().noneMatch(key -> key.startsWith(k[0] + "/"))) {
                    out.add(tag + "no " + k[0] + " forms in a 0.5.3 catalog");
                }
            }
            // The Cerealian "Tail" hairstyle sits on the head: no tail slot, no hidden race tail.
            List<BodyPartCatalog.Part> hairTail = c.resolve("cerealian", "hair", "tail", "", "");
            if (hairTail.isEmpty() || hairTail.stream().anyMatch(p -> BodyPartCatalog.SLOT_TAIL.equals(p.slot()))) {
                out.add(tag + "cerealian/hair/tail pieces must resolve and never take the tail slot, got " + hairTail);
            }
        } else {
            if (!crop.isEmpty()) {
                out.add(tag + "cerealian/hair/crop is 0.5.3 content and must not resolve, got " + crop);
            }
            if (!buffed.isEmpty()) {
                out.add(tag + "*_buffed pieces are 0.5.3 content and must not be in this shop, got " + buffed);
            }
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        List<String> fails = new ArrayList<>();
        for (String v : BodyPartCatalog.SHIPPED) {
            fails.addAll(check(BodyPartCatalog.shipped(v), Expect.forShipped(v)));
        }
        BodyPartCatalog.Catalog old = BodyPartCatalog.shipped("0.4.0");
        BodyPartCatalog.Catalog current = BodyPartCatalog.shipped("0.5.3");
        if (!"shipped".equals(old.origin()) || !"shipped".equals(current.origin())) {
            fails.add("both shipped catalogs must load from the jar, got " + old.origin() + " / " + current.origin());
        }
        // Version compare and selection: never a catalog newer than the installed Ultra.
        compare(fails, "0.5.3", "0.5.4", -1);
        compare(fails, "0.5.4", "0.6.0", -1);
        compare(fails, "0.5.10", "0.5.9", 1);
        compare(fails, "0.5.3-beta.2", "0.5.3", 0);
        compare(fails, "0.5", "0.5.0", 0);
        selects(fails, "0.4.0", "0.4.0", "shipped");
        selects(fails, "0.5.2", "0.4.0", "shipped");
        selects(fails, "0.5.3", "0.5.3", "shipped");
        selects(fails, "0.5.3-beta.2", "0.5.3", "shipped");
        selects(fails, "not-a-version", "0.4.0", "shipped");
        selects(fails, "0.3.9", "0.4.0", "shipped");
        // Newer Ultra with no jar to read (no Forge here): falls back to the newest shipped <= it.
        BodyPartCatalog.installedJarOverride = null;
        selects(fails, "0.6.0", "0.5.3", "shipped");
        UltraVersion.simulate("0.4.0");
        BodyPartCatalog.reselect();
        if (!BodyPartCatalog.resolve("cerealian", "hair", "crop", "", "").isEmpty()
                || BodyPartCatalog.shopPieceIds().size() != 109
                || BodyPartCatalog.shopPieceIds().stream().anyMatch(id -> !old.shopPieces().contains(id))) {
            fails.add("Ultra 0.4.0 must select the 0.4.0 catalog: 109 shop pieces, no Cerealian form");
        }
        UltraVersion.simulate("0.5.3");
        BodyPartCatalog.reselect();
        if (BodyPartCatalog.resolve("cerealian", "hair", "crop", "", "").isEmpty()
                || BodyPartCatalog.shopPieceIds().size() != 115) {
            fails.add("Ultra 0.5.3 must select the 0.5.3 catalog: 115 shop pieces, Cerealian forms resolve");
        }
        int forms053 = BodyPartCatalog.formCount();
        int shop053 = BodyPartCatalog.shopPieceIds().size();
        // Given Ultra jars: the Java generator reproduces the shipped catalog, and a newer Ultra
        // adopts a generated catalog that passes every assertion.
        for (String arg : args) {
            Path jar = Path.of(arg);
            BodyPartCatalog.Catalog gen = UltraFormPartsGenerator.generate(jar, "generated");
            BodyPartCatalog.Catalog ship = BodyPartCatalog.shipped(gen.version());
            if (!"shipped".equals(ship.origin()) || !gen.forms().equals(ship.forms())
                    || !gen.designs().equals(ship.designs()) || !gen.shopPieces().equals(ship.shopPieces())) {
                fails.add("generator on " + jar.getFileName() + " (Ultra " + gen.version()
                        + ") does not reproduce the shipped catalog: forms " + gen.forms().size() + "/"
                        + ship.forms().size() + ", designs " + gen.designs().size() + "/" + ship.designs().size()
                        + ", shop " + gen.shopPieces().size() + "/" + ship.shopPieces().size());
            }
            if ("0.5.3".equals(gen.version())) {
                BodyPartCatalog.installedJarOverride = jar;
                selects(fails, "0.6.0", "0.5.3", "generated");
                BodyPartCatalog.installedJarOverride = null;
            }
        }
        UltraVersion.simulate(null);
        BodyPartCatalog.reselect();
        if (!fails.isEmpty()) {
            throw new AssertionError(fails.size() + " failure(s): " + String.join("; ", fails));
        }
        var log = LogManager.getLogger(BodyPartCatalogSelfTest.class);
        log.info("BodyPartCatalogSelfTest OK forms={} shopPieces={} (0.5.3 selection)", forms053, shop053);
        log.info("BodyPartCatalogSelfTest OK forms={} shopPieces={} (0.4.0 catalog); generator parity on {} jar(s)",
                old.forms().size(), old.shopPieces().size(), args.length);
    }

    private static void selects(List<String> fails, String detected, String version, String origin) {
        UltraVersion.simulate(detected);
        BodyPartCatalog.reselect();
        BodyPartCatalog.Catalog c = BodyPartCatalog.active();
        if (!version.equals(c.version()) && !"generated".equals(origin) || !origin.equals(c.origin())) {
            fails.add("Ultra " + detected + " should select catalog " + version + " (" + origin + "), got "
                    + c.describe());
        }
    }

    private static void compare(List<String> fails, String a, String b, int sign) {
        if (Integer.signum(UltraVersion.compare(a, b)) != sign) {
            fails.add("compare(" + a + ", " + b + ") should be " + sign);
        }
    }

    private static void count(List<String> out, String tag, String what, int got, int want, boolean exact) {
        if (exact ? got != want : got < want) {
            out.add(tag + what + " should be " + (exact ? "" : "at least ") + want + ", got " + got);
        }
    }

    private static List<String> ids(List<BodyPartCatalog.Part> parts) {
        return parts.stream().map(BodyPartCatalog.Part::id).toList();
    }
}
