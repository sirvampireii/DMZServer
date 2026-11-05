package com.dbzlegacy.ltprobe;

import com.dbzlegacy.legacytextures.TextureMode;
import com.dbzlegacy.legacytextures.client.ClientState;
import com.dbzlegacy.legacytextures.client.LtHooks;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test-only client probe for Legacy Textures (never shipped). Once the title screen is up it walks
 * the presets through the real hooks (TextureManager, client ResourceManager, GeckoLib), the DMZ
 * option counter, a world reload, three toggles, a third set dropped in as one JSON, and the NB Armors
 * base textures, then writes ltprobe-results.txt in the game folder and quits.
 */
@Mod("ltprobe")
public final class LtClientProbe {
    private static final Logger LOG = LoggerFactory.getLogger("LtProbe");
    private static final ResourceLocation BODY = rl("dragonminez", "textures/entity/races/humansaiyan/bodytype_male_1_layer1.png");
    private static final ResourceLocation BODY_HD = rl("legacytextures", "textures/hd/dragonminez/entity/races/humansaiyan/bodytype_male_1_layer1.png");
    private static final ResourceLocation FEMALE3 = rl("dragonminez", "textures/entity/races/humansaiyan/bodytype_female_3_layer1.png");
    private static final ResourceLocation TATTOO6 = rl("dragonminez", "textures/entity/races/tattoos/tattoo_6.png");
    private static final ResourceLocation GI = rl("nbarmors", "textures/armor/angel1_1jbra.png");
    private static final ResourceLocation GI_HD = rl("legacytextures", "textures/hd/nbarmors/armor/angel1_1jbra.png");
    private static final ResourceLocation GEO = rl("dragonminez", "geo/entity/races/frostdemon.geo.json");
    private static final ResourceLocation GEO_HD = rl("legacytextures", "geo/hd/dragonminez/entity/races/frostdemon.geo.json");
    private static final ResourceLocation WIDGETS = rl("minecraft", "textures/gui/widgets.png");
    private static final ResourceLocation WIDGETS_HD = rl("legacytextures", "textures/hd/minecraft/gui/widgets.png");

    private final List<String> lines = new ArrayList<>();
    private int failures;
    private int step;
    private int settle;

    public LtClientProbe() {
        MinecraftForge.EVENT_BUS.addListener(this::onTick);
    }

    private static ResourceLocation rl(String ns, String path) {
        return ResourceLocation.fromNamespaceAndPath(ns, path);
    }

    private void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        // DragonMineZ replaces the vanilla title screen, so wait for any menu with no world loaded and no
        // loading overlay, then let it settle for a second.
        if (mc.f_91080_ == null || mc.f_91073_ != null || mc.m_91265_() != null) {
            settle = 0;
            return;
        }
        if (settle++ < 20) {
            return;
        }
        if (step == 0) {
            line("   menu screen: " + mc.f_91080_.getClass().getName());
            for (String field : new String[] {"modLoadWarnings", "modLoadErrors"}) {
                try {
                    Field f = mc.f_91080_.getClass().getDeclaredField(field);
                    f.setAccessible(true);
                    for (Object w : (List<?>) f.get(mc.f_91080_)) {
                        java.lang.reflect.Method m = w.getClass().getMethod("formatToString");
                        line("   forge " + field + ": " + m.invoke(w));
                    }
                } catch (ReflectiveOperationException ignored) {
                    // not a loading error screen
                }
            }
        }
        try {
            run(mc);
        } catch (Throwable t) {
            check("step " + step + " threw " + t, false);
            LOG.error("probe step {} failed", step, t);
            finish(mc);
        }
    }

    private void run(Minecraft mc) throws Exception {
        switch (step++) {
            case 0 -> {
                line("== startup (no server: default ALL_VANILLA)");
                check("mode is ALL_VANILLA", ClientState.mode().preset() == TextureMode.Preset.ALL_VANILLA);
                check("sets loaded: vanilla + hd", ClientState.registry().get("hd") != null);
                check("HD payload is in the loaded packs", raw(BODY_HD) && raw(GI_HD) && raw(GEO_HD) && raw(WIDGETS_HD));
                sync(TextureMode.Preset.ALL_VANILLA, "hd");
                check("vanilla: body binds the DMZ file", bound(BODY).equals(BODY));
                check("vanilla: HD-only tattoo_6 is not a file (DMZ counts stop before it)", !present(TATTOO6));
                check("vanilla: HD-only female bodytype 3 is not a file", !present(FEMALE3));
                check("vanilla: HD-only tattoo_6 binds transparent, not the checkerboard", bound(TATTOO6).equals(LtHooks.BLANK));
                for (int i = 0; i < 5; i++) {
                    bound(TATTOO6); // a bind every frame must not retry (and log) the missing key
                }
                check("vanilla: binding the missing key again leaves it unregistered (nothing retries it)",
                        !loaded().containsKey(TATTOO6));
                check("vanilla: NB gi binds the nbarmors jar file", bound(GI).equals(GI));
                check("vanilla: frostdemon model is the DMZ one", LtHooks.modelTarget(GEO).equals(GEO));
                line("   DMZ tattoo option count (vanilla) = " + tattoos());
                vanillaTattoos = tattoos();
            }
            case 1 -> {
                line("== PLAYERS_HD_ONLY (instant)");
                sync(TextureMode.Preset.PLAYERS_HD_ONLY, "hd");
                check("no reload started", mc.m_91265_() == null);
                check("body binds HD", bound(BODY).equals(BODY_HD));
                check("tattoo_6 is a file through the HD set", present(TATTOO6));
                check("female bodytype 3 is a file through the HD set", present(FEMALE3));
                int hd = tattoos();
                line("   DMZ tattoo option count (HD) = " + hd);
                check("DMZ creator offers more tattoos with HD on (selection-skew guard)", hd > vanillaTattoos);
                check("NB gi binds HD", bound(GI).equals(GI_HD));
                check("frostdemon model is the HD one (baked by GeckoLib)", LtHooks.modelTarget(GEO).equals(GEO_HD));
                check("world/menus stay vanilla", bound(WIDGETS).equals(WIDGETS));
                LtHooks.keyLoad(true);
                String whileLoading;
                try {
                    whileLoading = sha(BODY);
                } finally {
                    LtHooks.keyLoad(false);
                }
                check("HD on: a texture key loading dragonminez:...body reads the DMZ file, not the HD one",
                        whileLoading.equals(rawSha(BODY)) && !whileLoading.equals(sha(BODY_HD)));
                check("HD on: a DMZ lookup of the body reads the HD file", sha(BODY).equals(sha(BODY_HD)));
            }
            case 2 -> {
                line("== nbArmor vanilla, players HD");
                ClientState.onServerSync(TextureMode.of(TextureMode.Preset.PLAYERS_HD_ONLY, "hd").withNbArmor(false), false);
                check("NB gi binds the jar file", bound(GI).equals(GI));
                check("body still HD", bound(BODY).equals(BODY_HD));
                line("== ALL_HD: world switch reloads once");
                sync(TextureMode.Preset.ALL_HD, "hd");
                check("a reload started (loading screen)", mc.m_91265_() != null || !ClientState.worldHdLoaded());
            }
            case 3 -> {
                check("after the reload: world HD is loaded", ClientState.worldHdLoaded());
                check("menus bind HD widgets", bound(WIDGETS).equals(WIDGETS_HD));
                check("body still HD after the reload", bound(BODY).equals(BODY_HD));
                line("== back to PLAYERS_HD_ONLY (world off: one reload)");
                sync(TextureMode.Preset.PLAYERS_HD_ONLY, "hd");
            }
            case 4 -> {
                check("world HD unloaded", !ClientState.worldHdLoaded() && bound(WIDGETS).equals(WIDGETS));
                line("== toggle players 3x (instant)");
                for (int i = 1; i <= 3; i++) {
                    sync(TextureMode.Preset.ALL_VANILLA, "hd");
                    boolean v = bound(BODY).equals(BODY) && !present(TATTOO6) && bound(TATTOO6).equals(LtHooks.BLANK)
                            && tattoos() == vanillaTattoos;
                    sync(TextureMode.Preset.PLAYERS_HD_ONLY, "hd");
                    boolean h = bound(BODY).equals(BODY_HD) && present(TATTOO6) && bound(TATTOO6).equals(rl("legacytextures",
                            "textures/hd/dragonminez/entity/races/tattoos/tattoo_6.png")) && tattoos() > vanillaTattoos;
                    check("toggle " + i + ": vanilla then HD, every answer fresh", v && h);
                }
                check("no reload during the toggles", mc.m_91265_() == null);
                line("== third set: one JSON, no rebuild");
                Path json = ClientState.setsDir().resolve("hdcopy.json");
                Files.writeString(json, "{\"id\":\"hdcopy\",\"name\":\"HD copy (probe)\",\"namespace\":\"legacytextures\","
                        + "\"basePath\":\"hd\",\"categories\":[\"players\",\"armor\"],\"fallback\":\"vanilla\"}",
                        StandardCharsets.UTF_8);
                mc.m_91391_();
            }
            case 5 -> {
                check("third set is listed for the picker", ClientState.registry().hdSets().stream().anyMatch(s -> s.id.equals("hdcopy")));
                ClientState.onServerSync(TextureMode.of(TextureMode.Preset.PLAYERS_HD_ONLY, "hdcopy"), false);
                check("third set draws (body binds its file)", bound(BODY).equals(BODY_HD));
                Files.deleteIfExists(ClientState.setsDir().resolve("hdcopy.json"));
                line("== NB Armors base textures with no HD pack installed");
                sync(TextureMode.Preset.ALL_VANILLA, "hd");
                nbBase();
                finish(mc);
            }
            default -> {
            }
        }
    }

    private int vanillaTattoos;

    private void sync(TextureMode.Preset p, String set) {
        ClientState.onServerSync(TextureMode.of(p, set), false);
    }

    /** Every texture nbarmors 0.1.0 asks for (CatalogEntry.wornTexture + DMZ's torso _layer1) is in its jar. */
    private void nbBase() throws Exception {
        Class<?> catalog = Class.forName("com.nbarmors.catalog.Catalog");
        List<?> entries = (List<?>) catalog.getMethod("all").invoke(null);
        int asked = 0;
        List<String> missing = new ArrayList<>();
        for (Object e : entries) {
            Class<?> c = e.getClass();
            String kind = (String) c.getField("kind").get(e);
            String slot = (String) c.getField("slot").get(e);
            int layer = "armor".equals(kind) ? switch (slot) {
                case "head" -> 0;
                case "chest" -> 1;
                case "legs" -> 2;
                default -> 3;
            } : 1;
            String file = (String) c.getMethod("wornTexture", int.class).invoke(e, layer);
            List<String> want = new ArrayList<>(List.of(file));
            if ((boolean) c.getField("dmzTorso").get(e)) {
                want.add(c.getField("texturePrefix").get(e) + "_layer1.png");
            }
            for (String f : want) {
                asked++;
                ResourceLocation loc = rl("nbarmors", "textures/armor/" + f);
                if (!present(loc) || bound(loc).equals(LtHooks.BLANK)) {
                    missing.add(f);
                }
            }
        }
        line("   NB catalog entries " + entries.size() + ", textures requested " + asked + ", missing " + missing.size());
        check("every NB texture the mod requests is drawn from its own jar (no HD pack)", missing.isEmpty());
        if (!missing.isEmpty()) {
            line("   missing: " + missing.subList(0, Math.min(20, missing.size())));
        }
    }

    private int tattoos() throws Exception {
        return (int) Class.forName("com.dragonminez.client.util.TextureCounter").getMethod("getMaxTattooTypes", String.class)
                .invoke(null, "human");
    }

    private static java.util.Map<?, ?> loaded() throws Exception {
        Field f = net.minecraft.client.renderer.texture.TextureManager.class.getDeclaredField("f_118468_");
        f.setAccessible(true);
        return (java.util.Map<?, ?>) f.get(Minecraft.m_91087_().m_91097_());
    }

    private static String sha(ResourceLocation loc) throws Exception {
        try (java.io.InputStream in = Minecraft.m_91087_().m_91098_().m_213713_(loc).orElseThrow().m_215507_()) {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
        }
    }

    /** The DMZ jar's own bytes for {@code loc}, read straight from the dragonminez mod file. */
    private static String rawSha(ResourceLocation loc) throws Exception {
        Path p = net.minecraftforge.fml.ModList.get().getModFileById("dragonminez").getFile()
                .findResource("assets", loc.m_135827_(), loc.m_135815_());
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
    }

    private static boolean raw(ResourceLocation loc) {
        return Minecraft.m_91087_().m_91098_().m_213713_(loc).isPresent();
    }

    private static boolean present(ResourceLocation loc) {
        return raw(loc);
    }

    /** The file the texture manager actually loaded for {@code loc}. */
    private static ResourceLocation bound(ResourceLocation loc) throws Exception {
        AbstractTexture tex = Minecraft.m_91087_().m_91097_().m_118506_(loc);
        if (tex instanceof SimpleTexture) {
            Field f = SimpleTexture.class.getDeclaredField("f_118129_");
            f.setAccessible(true);
            return (ResourceLocation) f.get(tex);
        }
        return rl("ltprobe", "not_a_file/" + tex.getClass().getSimpleName().toLowerCase());
    }

    private void line(String s) {
        lines.add(s);
        LOG.info(s);
    }

    private void check(String name, boolean ok) {
        line((ok ? "  ok   " : "  FAIL ") + name);
        if (!ok) {
            failures++;
        }
    }

    private void finish(Minecraft mc) {
        try {
            long warns = Files.readAllLines(FMLPaths.GAMEDIR.get().resolve("logs/latest.log"), StandardCharsets.UTF_8).stream()
                    .filter(l -> l.contains("Failed to load texture")).count();
            check("log: no 'Failed to load texture' lines during the whole run (" + warns + ")", warns == 0);
        } catch (IOException e) {
            check("log readable", false);
        }
        line(failures == 0 ? "PROBE OK" : "PROBE FAILED: " + failures);
        try {
            Files.write(FMLPaths.GAMEDIR.get().resolve("ltprobe-results.txt"), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("could not write results", e);
        }
        step = 99;
        mc.m_91395_();
    }
}
