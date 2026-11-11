import com.dbzlegacy.legacytextures.TextureMode;
import com.dbzlegacy.legacytextures.resolve.Aliases;
import com.dbzlegacy.legacytextures.resolve.Category;
import com.dbzlegacy.legacytextures.resolve.Resolver;
import com.dbzlegacy.legacytextures.resolve.SetRegistry;
import com.dbzlegacy.legacytextures.resolve.TextureSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Resolver and set registry rules, run by build.sh. No game needed. */
public final class ResolverTest {
    private static int failures;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("lt-sets");
        SetRegistry reg = SetRegistry.load(dir,
                name -> ResolverTest.class.getResourceAsStream("/legacytextures/texture_sets/" + name));
        check("defaults written on first load", Files.exists(dir.resolve("vanilla.json")) && Files.exists(dir.resolve("hd.json")));
        check("registry has vanilla + hd", reg.get("vanilla") != null && reg.get("hd") != null);

        Set<ResourceLocation> files = new HashSet<>();
        ResourceLocation body = rl("dragonminez", "textures/entity/races/humansaiyan/bodytype_male_3_layer1.png");
        ResourceLocation bodyHd = rl("legacytextures", "textures/hd/dragonminez/entity/races/humansaiyan/bodytype_male_3_layer1.png");
        ResourceLocation tattoo6 = rl("dragonminez", "textures/entity/races/tattoos/tattoo_6.png");
        ResourceLocation tattoo6Hd = rl("legacytextures", "textures/hd/dragonminez/entity/races/tattoos/tattoo_6.png");
        ResourceLocation body6 = rl("dragonminez", "textures/entity/races/humansaiyan/bodytype_male_6_layer1.png");
        ResourceLocation body6Hd = rl("legacytextures", "textures/hd/dragonminez/entity/races/humansaiyan/bodytype_male_6_layer1.png");
        ResourceLocation missing = rl("dragonminez", "textures/entity/races/humansaiyan/bodytype_male_0_layer1.png");
        ResourceLocation gi = rl("nbarmors", "textures/armor/angel1_1jbra.png");
        ResourceLocation giHd = rl("legacytextures", "textures/hd/nbarmors/armor/angel1_1jbra.png");
        ResourceLocation geo = rl("dragonminez", "geo/entity/races/frostdemon.geo.json");
        ResourceLocation geoHd = rl("legacytextures", "geo/hd/dragonminez/entity/races/frostdemon.geo.json");
        ResourceLocation stone = rl("minecraft", "textures/block/stone.png");
        ResourceLocation widgets = rl("minecraft", "textures/gui/widgets.png");
        ResourceLocation widgetsHd = rl("legacytextures", "textures/hd/minecraft/gui/widgets.png");
        files.add(body);
        files.add(bodyHd);
        files.add(tattoo6Hd); // HD-only option: no vanilla file
        files.add(body6Hd); // HD-only body type
        files.add(gi);
        files.add(giHd);
        files.add(geo);
        files.add(geoHd);
        files.add(stone);
        files.add(widgets);
        files.add(widgetsHd);
        Resolver r = new Resolver(files::contains);

        check("category players / armor / world / gui / none",
                Category.of(body) == Category.PLAYERS && Category.of(gi) == Category.ARMOR
                        && Category.of(stone) == Category.WORLD && Category.of(widgets) == Category.GUI
                        && Category.of(rl("othermod", "textures/x.png")) == null
                        && Category.of(rl("dragonminez", "lang/en_us.json")) == null);
        check("hd map / source round trip", bodyHd.equals(reg.get("hd").map(body))
                && body.equals(reg.get("hd").source("textures/hd/dragonminez/entity/races/humansaiyan/bodytype_male_3_layer1.png")));

        r.configure(reg, mode(TextureMode.Preset.ALL_VANILLA), false, "hd");
        check("ALL_VANILLA: body is the mod's own file (same instance)", r.variant(body) == body);
        check("ALL_VANILLA: gi is the jar file", r.variant(gi) == gi);
        check("ALL_VANILLA: HD-only tattoo falls back to its HD file (0.2.2; vanilla has none)",
                tattoo6Hd.equals(r.variant(tattoo6)) && !r.exists(tattoo6));
        check("ALL_VANILLA: HD-only body type 6 draws its HD body, not a floating head (0.2.2)",
                body6Hd.equals(r.variant(body6)));
        check("ALL_VANILLA: a file missing from both sets stays itself (bind draws blank)",
                r.variant(missing) == missing);

        r.configure(reg, mode(TextureMode.Preset.PLAYERS_HD_ONLY), false, "hd");
        check("PLAYERS_HD_ONLY: body -> HD", bodyHd.equals(r.variant(body)));
        check("PLAYERS_HD_ONLY: HD-only tattoo exists through the set", tattoo6Hd.equals(r.variant(tattoo6)));
        check("PLAYERS_HD_ONLY: geo -> HD", geoHd.equals(r.variant(geo)));
        check("PLAYERS_HD_ONLY: NB gi -> HD", giHd.equals(r.variant(gi)));
        check("PLAYERS_HD_ONLY: world and gui stay vanilla", r.variant(stone) == stone && r.variant(widgets) == widgets);

        TextureMode nbVanilla = mode(TextureMode.Preset.PLAYERS_HD_ONLY).withNbArmor(false);
        r.configure(reg, nbVanilla, false, "hd");
        check("nbArmor vanilla: gi is the jar file while players stay HD",
                r.variant(gi) == gi && bodyHd.equals(r.variant(body)));

        r.configure(reg, mode(TextureMode.Preset.ALL_HD), true, "hd");
        check("ALL_HD after reload: gui -> HD", widgetsHd.equals(r.variant(widgets)));
        check("ALL_HD: a file the set lacks falls back to vanilla, never missing", r.variant(stone) == stone);
        r.configure(reg, mode(TextureMode.Preset.ALL_HD), false, "hd");
        check("ALL_HD before the reload finishes: world/gui still vanilla", r.variant(widgets) == widgets);

        for (int i = 0; i < 3; i++) {
            r.configure(reg, mode(TextureMode.Preset.ALL_VANILLA), false, "hd");
            boolean v = r.variant(body) == body;
            r.configure(reg, mode(TextureMode.Preset.PLAYERS_HD_ONLY), false, "hd");
            boolean h = bodyHd.equals(r.variant(body));
            check("toggle " + (i + 1) + ": no stale answer across switches", v && h);
        }

        // A third set, dropped in as one JSON: partial, falls back to hd, then vanilla.
        Files.writeString(dir.resolve("hd2.json"), "{\"id\":\"hd2\",\"name\":\"HD copy\",\"namespace\":\"ltcopy\","
                + "\"basePath\":\"hd2\",\"categories\":[\"players\"],\"fallback\":\"hd\"}");
        Files.writeString(dir.resolve("bad.json"), "{\"id\":\"bad\",\"namespace\":\"dragonminez\",\"basePath\":\"x\"}");
        SetRegistry reg3 = SetRegistry.load(dir, name -> null);
        check("third set appears in the picker list", reg3.hdSets().stream().anyMatch(s -> s.id.equals("hd2")));
        check("a set that reuses a mod namespace is refused", reg3.get("bad") == null && !reg3.warnings().isEmpty());
        files.add(rl("ltcopy", "textures/hd2/dragonminez/entity/races/humansaiyan/bodytype_male_3_layer1.png"));
        r.configure(reg3, mode(TextureMode.Preset.PLAYERS_HD_ONLY).withSet("hd2"), false, "hd");
        check("hd2: its own file wins", "ltcopy".equals(r.variant(body).m_135827_()));
        check("hd2: a file it lacks falls back to hd", tattoo6Hd.equals(r.variant(tattoo6)));
        check("hd2: armor is not its category -> chain gives hd", giHd.equals(r.variant(gi)));
        check("unknown set id uses hd", reg3.hdOrFallback("nope").id.equals("hd"));
        check("fallback loop stops at vanilla", reg3.chain(reg3.get("hd2")).get(reg3.chain(reg3.get("hd2")).size() - 1)
                .isVanilla());

        aliasRules(reg, files);

        if (failures > 0) {
            System.err.println("ResolverTest: " + failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("ResolverTest OK");
    }

    /** Cross-race HD aliases (0.2.5): a synthetic race borrows humansaiyan's HD body file. */
    private static void aliasRules(SetRegistry reg, Set<ResourceLocation> files) throws Exception {
        Path cfg = Files.createTempDirectory("lt-alias").resolve("aliases.json");
        Aliases shipped = Aliases.load(cfg,
                name -> ResolverTest.class.getResourceAsStream("/legacytextures/" + name));
        check("aliases: default file written, valid, and empty (both proposed entries dropped)",
                Files.exists(cfg) && shipped.entries().isEmpty() && shipped.warnings().isEmpty());

        ResourceLocation own = rl("dragonminez", "textures/entity/races/testrace/bodytype_male_3_layer1.png");
        ResourceLocation ownOnly = rl("dragonminez", "textures/entity/races/testrace/testrace_0_layer2.png");
        ResourceLocation notShipped = rl("dragonminez", "textures/entity/races/testrace/bodytype_male_4_layer1.png");
        ResourceLocation face = rl("dragonminez", "textures/entity/races/testrace/faces/humansaiyan_eye_0_0.png");
        ResourceLocation donorHd = rl("legacytextures", "textures/hd/dragonminez/entity/races/humansaiyan/bodytype_male_3_layer1.png");
        ResourceLocation donor4Hd = rl("legacytextures", "textures/hd/dragonminez/entity/races/humansaiyan/bodytype_male_4_layer1.png");
        ResourceLocation donorFaceHd = rl("legacytextures", "textures/hd/dragonminez/entity/races/humansaiyan/faces/humansaiyan_eye_0_0.png");
        files.add(own);
        files.add(ownOnly);
        files.add(face);
        files.add(donor4Hd);
        files.add(donorFaceHd);
        Files.writeString(cfg, "{\"aliases\":["
                + "{\"from\":\"dragonminez:textures/entity/races/testrace/\",\"to\":\"dragonminez:textures/entity/races/humansaiyan/\"},"
                + "{\"from\":\"dragonminez:textures/entity/races/x/faces/\",\"to\":\"dragonminez:textures/entity/races/humansaiyan/faces/\"},"
                + "{\"from\":\"dmzultra:textures/entity/races/alien/\",\"to\":\"dmzultra:textures/entity/races/humansaiyan/\"},"
                + "{\"from\":\"dragonminez:geo/entity/races/\",\"to\":\"dragonminez:geo/entity/x/\"}]}");
        Aliases a = Aliases.load(cfg, name -> null);
        check("aliases: a face, a dmzultra: and a geo entry are refused with warnings",
                a.entries().size() == 1 && a.warnings().size() == 3);

        List<String> logged = new ArrayList<>();
        Resolver r = new Resolver(files::contains);
        r.configure(reg, mode(TextureMode.Preset.PLAYERS_HD_ONLY), false, "hd");
        r.aliases(a, logged::add);
        check("aliases HD: a file the race ships borrows the donor's HD file", donorHd.equals(r.variant(own)));
        check("aliases HD: no donor HD file -> the race's own file, miss logged once",
                r.variant(ownOnly) == ownOnly && logged.size() == 1);
        r.clear();
        r.variant(ownOnly);
        check("aliases HD: the miss is not logged again after a clear", logged.size() == 1);
        check("aliases HD: faces never alias (eyes stay as the race ships them)", r.variant(face) == face);
        check("aliases HD: a file the race does not ship stays missing (no new creator option)",
                r.variant(notShipped) == notShipped);

        r.configure(reg, mode(TextureMode.Preset.ALL_VANILLA), false, "hd");
        check("aliases ALL_VANILLA: the race's own file, no alias", r.variant(own) == own);
        check("aliases ALL_VANILLA: a missing file does not borrow either", r.variant(notShipped) == notShipped);

        r.configure(reg, mode(TextureMode.Preset.PLAYERS_HD_ONLY), false, "hd");
        r.aliases(Aliases.NONE, logged::add);
        check("aliases removed: the race draws its own file again", r.variant(own) == own);
    }

    private static TextureMode mode(TextureMode.Preset p) {
        return TextureMode.of(p, "hd");
    }

    private static ResourceLocation rl(String ns, String path) {
        return ResourceLocation.fromNamespaceAndPath(ns, path);
    }

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name);
        if (!ok) {
            failures++;
        }
    }
}
