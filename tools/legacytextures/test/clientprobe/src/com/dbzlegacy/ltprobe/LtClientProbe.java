package com.dbzlegacy.ltprobe;

import com.dbzlegacy.legacytextures.LegacyTextures;
import com.dbzlegacy.legacytextures.TextureMode;
import com.dbzlegacy.legacytextures.client.ClientState;
import com.dbzlegacy.legacytextures.client.ClientStatus;
import com.dbzlegacy.legacytextures.client.LtHooks;
import com.dbzlegacy.legacytextures.faceanim.FaceAnimConfig;
import com.dbzlegacy.legacytextures.faceanim.FaceAnimator;
import com.dbzlegacy.legacytextures.faceanim.FaceFlags;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.CameraType;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Test-only client probe for Legacy Textures (never shipped). At the menu it walks the presets through
 * the real hooks, the DMZ option counter, GeckoLib model geometry, a world reload, three toggles, a third
 * set dropped in as one JSON and the absorbed NB Armors registry, models and textures. With
 * -Dltprobe.server=host:port it then joins that server and checks an NB outfit and Curios vanity item
 * on a DMZ player in both modes (screenshots), /legacytextures status and the world-reload chat line.
 * Writes ltprobe-results.txt in the game folder and quits.
 */
@Mod("ltprobe")
public final class LtClientProbe {
    private static final Logger LOG = LoggerFactory.getLogger("LtProbe");
    private static final ResourceLocation BODY = rl("dragonminez", "textures/entity/races/humansaiyan/bodytype_male_1_layer1.png");
    private static final ResourceLocation BODY_HD = rl("legacytextures", "textures/hd/dragonminez/entity/races/humansaiyan/bodytype_male_1_layer1.png");
    private static final ResourceLocation BODY6 = rl("dragonminez", "textures/entity/races/humansaiyan/bodytype_male_6_layer1.png");
    private static final ResourceLocation BODY6_HD = rl("legacytextures", "textures/hd/dragonminez/entity/races/humansaiyan/bodytype_male_6_layer1.png");
    /** DMZ's getSafeTexture fallback: in neither DMZ nor the HD set. */
    private static final ResourceLocation BODY0 = rl("dragonminez", "textures/entity/races/humansaiyan/bodytype_male_0_layer1.png");
    private static final ResourceLocation TATTOO6_HD = rl("legacytextures", "textures/hd/dragonminez/entity/races/tattoos/tattoo_6.png");
    private static final ResourceLocation FEMALE3 = rl("dragonminez", "textures/entity/races/humansaiyan/bodytype_female_3_layer1.png");
    private static final ResourceLocation TATTOO6 = rl("dragonminez", "textures/entity/races/tattoos/tattoo_6.png");
    private static final ResourceLocation GI = rl("nbarmors", "textures/armor/angel1_1jbra.png");
    /** The pieces joint_lt.sh hands out, as the HD run drew them. */
    private static final List<ResourceLocation> WORN = List.of(rl("nbarmors", "textures/armor/angel1_1jbra.png"),
            rl("nbarmors", "textures/armor/aeos_2jbra.png"), rl("nbarmors", "textures/armor/aeos_3jbra.png"),
            rl("nbarmors", "textures/armor/aeoshead_0jbra.png"));
    private static final ResourceLocation GI_HD = rl("legacytextures", "textures/hd/nbarmors/armor/angel1_1jbra.png");
    private static final ResourceLocation GEO = rl("dragonminez", "geo/entity/races/frostdemon.geo.json");
    private static final ResourceLocation GEO_HD = rl("legacytextures", "geo/hd/dragonminez/entity/races/frostdemon.geo.json");
    private static final ResourceLocation[] GEOMETRY = {
            rl("dragonminez", "geo/entity/master/master_roshi.geo.json"),
            rl("dragonminez", "geo/entity/races/frostdemon_fifth.geo.json")};
    private static final ResourceLocation WIDGETS = rl("minecraft", "textures/gui/widgets.png");
    private static final ResourceLocation WIDGETS_HD = rl("legacytextures", "textures/hd/minecraft/gui/widgets.png");

    private final List<String> lines = new ArrayList<>();
    private final String server = System.getProperty("ltprobe.server", "");
    private int failures;
    private int step;
    private int settle;
    private int wait;
    private int joins;
    private int gearWaits;
    private String pendingShot;
    private Runnable afterShot;
    private int vanillaTattoos;
    private int reloadsSeen;
    private boolean overlayWas;
    private int chatMark;
    private int reloadsBefore;

    public LtClientProbe() {
        MinecraftForge.EVENT_BUS.addListener(this::onTick);
        MinecraftForge.EVENT_BUS.addListener(this::onScreen);
    }

    private static ResourceLocation rl(String ns, String path) {
        return ResourceLocation.fromNamespaceAndPath(ns, path);
    }

    /** In the world, keep DMZ's character screen closed so the screenshots show the player. */
    private void onScreen(net.minecraftforge.client.event.ScreenEvent.Opening e) {
        if (step >= 20 && e.getNewScreen() != null && e.getNewScreen().getClass().getName().startsWith("com.dragonminez.")) {
            e.setCanceled(true);
        }
    }

    private void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || step >= 99) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        boolean overlay = mc.m_91265_() != null;
        if (overlay && !overlayWas) {
            reloadsSeen++;
        }
        overlayWas = overlay;
        try {
            if (step < 20) {
                menuPhase(mc);
            } else {
                worldPhase(mc);
            }
        } catch (Throwable t) {
            check("step " + step + " threw " + t, false);
            LOG.error("probe step {} failed", step, t);
            finish(mc);
        }
    }

    // ---------------------------------------------------------------- menu (no world)

    private void menuPhase(Minecraft mc) throws Exception {
        // DragonMineZ replaces the vanilla title screen: wait for any menu with no world and no loading overlay.
        if (mc.f_91080_ == null || mc.f_91073_ != null || mc.m_91265_() != null) {
            settle = 0;
            return;
        }
        if (settle++ < 20) {
            return;
        }
        switch (step++) {
            case 0 -> {
                line("== startup: Legacy Textures " + LegacyTextures.VERSION + ", no nbarmors jar installed");
                nbAbsorbed();
                check("mode is ALL_VANILLA", ClientState.mode().preset() == TextureMode.Preset.ALL_VANILLA);
                check("sets loaded: vanilla + hd", ClientState.registry().get("hd") != null);
                check("HD payload is in the loaded packs", raw(BODY_HD) && raw(GI_HD) && raw(GEO_HD) && raw(WIDGETS_HD));
                sync(TextureMode.Preset.ALL_VANILLA, "hd");
                check("vanilla: body binds the DMZ file", bound(BODY).equals(BODY));
                // 0.2.2: vanilla prefers DMZ's file and falls back to HD only for files DMZ lacks
                check("vanilla: body type 1 is DMZ's own file, byte for byte (no HD leak)",
                        sha(BODY).equals(rawSha("dragonminez", BODY)) && !sha(BODY).equals(sha(BODY_HD)));
                check("vanilla: HD-only body type 6 is a file through the HD fallback (DMZ keeps the body)", raw(BODY6));
                check("vanilla: HD-only body type 6 binds its HD body, not blank (no floating head)", bound(BODY6).equals(BODY6_HD));
                check("vanilla: HD-only tattoo_6 and female body type 3 resolve through HD", raw(TATTOO6) && raw(FEMALE3)
                        && bound(TATTOO6).equals(TATTOO6_HD));
                check("vanilla: a file missing from both sets binds transparent, not the checkerboard",
                        !raw(BODY0) && bound(BODY0).equals(LtHooks.BLANK));
                check("vanilla: NB gi binds the file now shipped in Legacy Textures", bound(GI).equals(GI));
                check("vanilla: frostdemon model is the DMZ one", LtHooks.modelTarget(GEO).equals(GEO));
                vanillaTattoos = tattoos();
                line("   DMZ tattoo option count (vanilla) = " + vanillaTattoos);
                geometry(false);
            }
            case 1 -> {
                line("== PLAYERS_HD_ONLY (instant)");
                sync(TextureMode.Preset.PLAYERS_HD_ONLY, "hd");
                check("no reload started", mc.m_91265_() == null);
                check("body binds HD", bound(BODY).equals(BODY_HD));
                check("tattoo_6 is a file through the HD set", raw(TATTOO6));
                check("female bodytype 3 is a file through the HD set", raw(FEMALE3));
                int hd = tattoos();
                line("   DMZ tattoo option count (HD) = " + hd);
                check("vanilla and HD offer the same tattoo count (HD-only options draw in vanilla since 0.2.2)",
                        hd == vanillaTattoos);
                check("NB gi binds HD", bound(GI).equals(GI_HD));
                check("world/menus stay vanilla", bound(WIDGETS).equals(WIDGETS));
                geometry(true);
                LtHooks.keyLoad(true);
                String whileLoading;
                try {
                    whileLoading = sha(BODY);
                } finally {
                    LtHooks.keyLoad(false);
                }
                check("HD on: a texture key loading the DMZ body reads the DMZ file, not the HD one",
                        whileLoading.equals(rawSha("dragonminez", BODY)) && !whileLoading.equals(sha(BODY_HD)));
            }
            case 2 -> {
                line("== nbArmor vanilla, players HD");
                ClientState.onServerSync(TextureMode.of(TextureMode.Preset.PLAYERS_HD_ONLY, "hd").withNbArmor(false), false);
                check("NB gi binds the standard file", bound(GI).equals(GI));
                check("body still HD", bound(BODY).equals(BODY_HD));
                line("== ALL_HD: world switch reloads once");
                sync(TextureMode.Preset.ALL_HD, "hd");
                check("a reload started (loading screen)", mc.m_91265_() != null || !ClientState.worldHdLoaded());
            }
            case 3 -> {
                check("after the reload: world HD is loaded", ClientState.worldHdLoaded());
                check("menus bind HD widgets", bound(WIDGETS).equals(WIDGETS_HD));
                check("HD geometry still selected after a reload (re-baked by GeckoLib)",
                        geoModel().getBakedModel(GEOMETRY[0]) == GeckoLibCache.getBakedModels().get(hd(GEOMETRY[0])));
                sync(TextureMode.Preset.PLAYERS_HD_ONLY, "hd");
            }
            case 4 -> {
                check("world HD unloaded", !ClientState.worldHdLoaded() && bound(WIDGETS).equals(WIDGETS));
                line("== toggle players 3x (instant)");
                int before = reloadsSeen;
                for (int i = 1; i <= 3; i++) {
                    sync(TextureMode.Preset.ALL_VANILLA, "hd");
                    boolean v = bound(BODY).equals(BODY) && bound(BODY6).equals(BODY6_HD) && bound(BODY0).equals(LtHooks.BLANK)
                            && tattoos() == vanillaTattoos && cubes(geoModel().getBakedModel(GEOMETRY[0])) == cubes(GEOMETRY[0]);
                    sync(TextureMode.Preset.PLAYERS_HD_ONLY, "hd");
                    boolean h = bound(BODY).equals(BODY_HD) && raw(TATTOO6) && tattoos() == vanillaTattoos
                            && cubes(geoModel().getBakedModel(GEOMETRY[0])) == cubes(hd(GEOMETRY[0]));
                    check("toggle " + i + ": textures, option counts and geometry fresh each way", v && h);
                }
                check("no reload during the toggles", reloadsSeen == before && mc.m_91265_() == null);
                line("== third set: one JSON, no rebuild");
                Files.writeString(ClientState.setsDir().resolve("hdcopy.json"), "{\"id\":\"hdcopy\",\"name\":\"HD copy (probe)\","
                        + "\"namespace\":\"legacytextures\",\"basePath\":\"hd\",\"categories\":[\"players\",\"armor\"],"
                        + "\"fallback\":\"vanilla\"}", StandardCharsets.UTF_8);
                mc.m_91391_();
            }
            case 5 -> {
                check("third set is listed for the menu's set picker",
                        ClientState.registry().hdSets().stream().anyMatch(s -> s.id.equals("hdcopy")));
                ClientState.onServerSync(TextureMode.of(TextureMode.Preset.PLAYERS_HD_ONLY, "hdcopy"), false);
                check("third set draws (body binds its file)", bound(BODY).equals(BODY_HD));
                Files.deleteIfExists(ClientState.setsDir().resolve("hdcopy.json"));
                line("== NB Armors base textures (no nbarmors jar, no HD pack)");
                sync(TextureMode.Preset.ALL_VANILLA, "hd");
                nbBase();
                line("== status report (client half)");
                ClientStatus.report();
                check("status: drawing + file-check + model hooks all ran", counter("binds") > 0 && counter("lookups") > 0
                        && counter("models") > 0);
                if (server.isEmpty()) {
                    finish(mc);
                } else {
                    join(mc);
                    step = 20;
                }
            }
            default -> {
            }
        }
    }

    /** The absorbed NB Armors mod: registered, modelled, translated, from this jar alone. */
    private void nbAbsorbed() throws Exception {
        check("mod nbarmors is loaded", ModList.get().isLoaded("nbarmors"));
        check("nbarmors comes from the Legacy Textures jar (no separate nbarmors jar)",
                ModList.get().getModFileById("nbarmors").getFile() == ModList.get().getModFileById("legacytextures").getFile());
        check("nbarmors version " + LegacyTextures.VERSION,
                ModList.get().getModContainerById("nbarmors").get().getModInfo().getVersion().toString().equals(LegacyTextures.VERSION));
        long items = BuiltInRegistries.f_257033_.m_6566_().stream().filter(k -> k.m_135827_().equals("nbarmors")).count();
        line("   nbarmors items registered: " + items);
        check("all 1,043 NB items registered under nbarmors: ids", items == 1043);
        long tabs = BuiltInRegistries.f_279662_.m_6566_().stream().filter(k -> k.m_135827_().equals("nbarmors")).count();
        line("   nbarmors creative tabs registered: " + tabs);
        check("NB creative tabs registered", tabs > 0);
        var models = Minecraft.m_91087_().m_91304_();
        String missingModels = "";
        for (String id : new String[] {"angel1_chest", "aeos_chest", "aeos_legs", "aeos_boots", "aeoshead"}) {
            if (models.m_119422_(new ModelResourceLocation(rl("nbarmors", id), "inventory")) == models.m_119409_()) {
                missingModels += id + " ";
            }
        }
        check("angel chest, aeos set and aeos head have their item models" + (missingModels.isEmpty() ? "" : ": missing " + missingModels),
                missingModels.isEmpty());
        check("lang: NB item names and Legacy Textures menu text are translated",
                Language.m_128107_().m_6722_("item.nbarmors.aeos_chest") && Language.m_128107_().m_6722_("legacytextures.menu.title")
                        && Language.m_128107_().m_6722_("key.legacytextures.menu"));
        boolean key = false;
        for (KeyMapping k : Minecraft.m_91087_().f_91066_.f_92059_) {
            key |= k.m_90860_().equals("key.legacytextures.menu");
        }
        check("menu key registered in Controls", key);
    }

    /** GeckoLib really bakes the HD geos, and a GeoModel draws the mode's geometry. */
    private void geometry(boolean hdOn) {
        for (ResourceLocation geo : GEOMETRY) {
            BakedGeoModel v = GeckoLibCache.getBakedModels().get(geo);
            BakedGeoModel h = GeckoLibCache.getBakedModels().get(hd(geo));
            if (!hdOn) {
                line("   " + geo.m_135815_() + ": cubes " + cubes(v) + " standard, " + cubes(h) + " HD");
                check("GeckoLib baked the HD " + name(geo) + " and its geometry differs", h != null && cubes(h) != cubes(v));
            }
            BakedGeoModel drawn = geoModel().getBakedModel(geo);
            check((hdOn ? "HD" : "vanilla") + ": a GeckoLib model asking for " + name(geo) + " gets the "
                    + (hdOn ? "HD" : "standard") + " geometry", drawn == (hdOn ? h : v));
        }
    }

    private static String name(ResourceLocation geo) {
        String p = geo.m_135815_();
        return p.substring(p.lastIndexOf('/') + 1);
    }

    private static ResourceLocation hd(ResourceLocation geo) {
        return rl("legacytextures", geo.m_135815_().replaceFirst("^geo/", "geo/hd/dragonminez/"));
    }

    private static int cubes(ResourceLocation loc) {
        return cubes(GeckoLibCache.getBakedModels().get(loc));
    }

    private static int cubes(BakedGeoModel m) {
        if (m == null) {
            return -1;
        }
        int n = 0;
        List<GeoBone> todo = new ArrayList<>(m.topLevelBones());
        while (!todo.isEmpty()) {
            GeoBone b = todo.remove(todo.size() - 1);
            n += b.getCubes().size();
            todo.addAll(b.getChildBones());
        }
        return n;
    }

    private static GeoModel<GeoAnimatable> model;

    /** A plain GeckoLib model: its getBakedModel goes through Legacy Textures' model hook like DMZ's do. */
    private static GeoModel<GeoAnimatable> geoModel() {
        if (model == null) {
            model = new GeoModel<>() {
                @Override
                public ResourceLocation getModelResource(GeoAnimatable a) {
                    return GEOMETRY[0];
                }

                @Override
                public ResourceLocation getTextureResource(GeoAnimatable a) {
                    return BODY;
                }

                @Override
                public ResourceLocation getAnimationResource(GeoAnimatable a) {
                    return rl("dragonminez", "animations/entity/master/masters.animation.json");
                }
            };
        }
        return model;
    }

    // ---------------------------------------------------------------- in a world (server)

    private void join(Minecraft mc) {
        joins++;
        line("== joining " + server + " (try " + joins + ")");
        net.minecraft.client.gui.screens.ConnectScreen.m_278792_(new TitleScreen(), mc,
                net.minecraft.client.multiplayer.resolver.ServerAddress.m_171864_(server),
                new ServerData("probe", server, false), false);
    }

    private void worldPhase(Minecraft mc) throws Exception {
        if (mc.f_91074_ == null || mc.f_91073_ == null) {
            if (mc.f_91080_ instanceof net.minecraft.client.gui.screens.DisconnectedScreen d) {
                StringBuilder why = new StringBuilder(d.m_96636_().getString());
                for (java.lang.reflect.Field f : d.getClass().getDeclaredFields()) {
                    if (net.minecraft.network.chat.Component.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        why.append(" | ").append(((net.minecraft.network.chat.Component) f.get(d)).getString());
                    }
                }
                line("   disconnected before the world loaded: " + why);
                if (joins >= 3) {
                    check("joined " + server, false);
                    finish(mc);
                } else {
                    join(mc);
                }
            }
            return;
        }
        if (mc.f_91080_ != null) {
            mc.m_91152_(null); // DMZ may open its character screen for a new player; look at the world instead
        }
        if (pendingShot != null) {
            String file = pendingShot;
            pendingShot = null;
            Screenshot.m_92295_(mc.f_91069_, file, mc.m_91385_(), msg -> line("   screenshot " + file));
            if (afterShot != null) {
                Runnable r = afterShot;
                afterShot = null;
                r.run();
            }
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Path gameDir = FMLPaths.GAMEDIR.get();
        switch (step++) {
            case 20 -> {
                line("== in world on " + server + " as " + mc.f_91074_.m_36316_().getName());
                check("server sent the stored choice: ALL_VANILLA default", ClientState.mode().preset() == TextureMode.Preset.ALL_VANILLA);
                mc.f_91066_.m_92157_(CameraType.THIRD_PERSON_FRONT);
                Files.writeString(gameDir.resolve("ltprobe-need-gear"), mc.f_91074_.m_36316_().getName());
                wait = 20;
            }
            case 21 -> {
                Item chest = mc.f_91074_.m_6844_(EquipmentSlot.CHEST).m_41720_();
                if (!BuiltInRegistries.f_257033_.m_7981_(chest).equals(rl("nbarmors", "angel1_chest"))) {
                    step = 21; // wait for the server to hand out the outfit
                    wait = 10;
                    return;
                }
                ItemStack head = CuriosApi.getCuriosInventory(mc.f_91074_).resolve()
                        .flatMap(inv -> inv.getStacksHandler("vanity")).map(h -> h.getStacks().getStackInSlot(0))
                        .orElse(ItemStack.f_41583_);
                if (head.m_41619_() && gearWaits++ < 30) {
                    step = 21; // the server hands out the Curios head a few seconds after the chest
                    wait = 10;
                    return;
                }
                check("NB angel chest worn", true);
                check("Curios vanity slot holds the NB aeos head", BuiltInRegistries.f_257033_.m_7981_(head.m_41720_())
                        .equals(rl("nbarmors", "aeoshead")));
                check("aeos head is in the curios:vanity tag (the slot accepts it)",
                        head.m_204117_(TagKey.m_203882_(net.minecraft.core.registries.Registries.f_256913_, rl("curios", "vanity"))));
                wait = 60;
            }
            case 22 -> {
                Map<?, ?> loaded = loaded();
                List<String> nb = new ArrayList<>();
                for (Object k : loaded.keySet()) {
                    if (k.toString().startsWith("nbarmors:textures/armor/") || k.toString().contains("hd/nbarmors/")) {
                        nb.add(k.toString());
                    }
                }
                line("   NB textures drawn (standard): " + nb);
                boolean std = true;
                for (ResourceLocation worn : WORN) {
                    std &= bound(worn).equals(worn);
                }
                check("standard: the worn NB outfit binds its nbarmors: files (no HD, no blank)", std);
                check("standard: DMZ's armor layer drew the NB torso (angel1_layer1, the DMZ compat path)",
                        loaded.containsKey(rl("nbarmors", "textures/armor/angel1_layer1.png")));
                shot(mc, "ltprobe-standard.png");
                afterShot = () -> mc.f_91074_.f_108617_.m_246623_("legacytextures preset players_hd_only");
                wait = 60;
            }
            case 23 -> {
                check("command preset players_hd_only reached this game", ClientState.mode().players && ClientState.mode().nbArmor);
                Map<?, ?> loaded = loaded();
                List<String> hdNb = new ArrayList<>();
                for (Object k : loaded.keySet()) {
                    if (k.toString().startsWith("legacytextures:textures/hd/nbarmors/armor/")) {
                        hdNb.add(k.toString());
                    }
                }
                line("   NB textures drawn (HD): " + hdNb);
                boolean hdOk = true;
                for (ResourceLocation worn : WORN) {
                    hdOk &= bound(worn).equals(rl("legacytextures", "textures/hd/nbarmors/" + worn.m_135815_().substring("textures/".length())));
                }
                check("HD: the worn NB outfit binds HD files, with no reload", hdOk && mc.m_91265_() == null);
                shot(mc, "ltprobe-hd.png");
                chatMark = chatSize();
                mc.f_91074_.f_108617_.m_246623_("legacytextures status");
                wait = 40;
            }
            case 24 -> {
                String chat = chatSince(chatMark);
                line("   status chat: " + chat.replace('\n', '|'));
                check("/legacytextures status shows the choice", chat.contains("Players HD only"));
                check("/legacytextures status shows hook health: all three working",
                        chat.contains("drawing working, file checks working, models working"));
                chatMark = chatSize();
                reloadsBefore = reloadsSeen;
                mc.f_91074_.f_108617_.m_246623_("legacytextures world hd");
                wait = 40;
            }
            case 25 -> {
                if (mc.m_91265_() != null) {
                    step = 25;
                    return;
                }
                String chat = chatSince(chatMark);
                line("   world chat: " + chat.replace('\n', '|'));
                check("world hd: chat warns that textures reload once", chat.contains("reloads its textures once"));
                check("world hd: exactly one reload", reloadsSeen - reloadsBefore == 1 && ClientState.worldHdLoaded());
                // 0.2.1: show the hair cap; the run script clears the vanity head when this file appears
                Files.writeString(gameDir.resolve("ltprobe-need-nohead"), mc.f_91074_.m_36316_().getName());
                wait = 40;
            }
            case 26 -> {
                if (Files.exists(gameDir.resolve("ltprobe-need-nohead"))) {
                    step = 26;
                    wait = 10;
                    return;
                }
                ItemStack head = CuriosApi.getCuriosInventory(mc.f_91074_).resolve()
                        .flatMap(inv -> inv.getStacksHandler("vanity")).map(h -> h.getStacks().getStackInSlot(0))
                        .orElse(ItemStack.f_41583_);
                check("vanity head removed for the hair shots", head.m_41619_());
                check("ALL_HD for the hair shot", ClientState.mode().players && ClientState.mode().world);
                shot(mc, "ltprobe-hair-hd.png");
                afterShot = () -> mc.f_91074_.f_108617_.m_246623_("legacytextures players vanilla");
                wait = 40;
            }
            case 27 -> {
                check("players back to standard for the vanilla hair shot", !ClientState.mode().players);
                shot(mc, "ltprobe-hair-standard.png");
                wait = 10;
            }
            case 28 -> {
                line("== face animation (absorbed DMZ Face Anim, 0.2.3)");
                check("face animation mixin applied", logHas("hook applied: DMZSkinLayerFaceMixin"));
                check("face animation hook ran while the player was drawn", logHas("face animation hook active in DMZSkinLayer"));
                faceStats = StatsProvider.get(StatsCapability.INSTANCE, mc.f_91074_).orElse(null);
                check("DMZ stats for the player", faceStats != null && faceStats.getStatus() != null);
                int closed = closedTick(mc.f_91074_);
                String blink = routeAt(mc, closed, EYE);
                check("blink: at a closed-eye tick the eye layer draws its closed frame, and that frame is a file",
                        blink.equals(EYE_CLOSED) && raw(rl("dragonminez", blink)));
                String angel = routeAt(mc, closed, ANGEL_EYE);
                check("blink: Angel eyes close too (angel/ frames, base eyes shipped)", angel.equals(ANGEL_CLOSED)
                        && raw(rl("dragonminez", angel)) && raw(rl("dragonminez", ANGEL_EYE)));
                int blinks = 0;
                boolean was = false;
                for (int t = 0; t < 900; t++) {
                    boolean shut = !routeAt(mc, 9000 + t, EYE).equals(EYE);
                    blinks += shut && !was ? 1 : 0;
                    was = shut;
                }
                line("   blinks in 900 ticks (45 s): " + blinks);
                check("blink cycle: one blink every 90 ticks (about 4.5 s)", blinks == 10);
                faceStats.getStatus().setChargingKi(true);
                String mouth = MOUTH;
                for (int i = 1; i <= 5; i++) {
                    mouth = routeAt(mc, 20000 + i, MOUTH);
                }
                faceStats.getStatus().setChargingKi(false);
                for (int i = 6; i <= 12; i++) {
                    routeAt(mc, 20000 + i, MOUTH);
                }
                check("scream: charging ki opens the mouth into a scream frame (a file)",
                        mouth.contains("anim/mouth_scream_") && raw(rl("dragonminez", mouth)));
                check("scream: stops once charging stops", routeAt(mc, 20013, MOUTH).equals(MOUTH));
                configValue("BLINK", false);
                check("viewer [faceanim] enableBlink=false: no blink for this viewer", routeAt(mc, closed, EYE).equals(EYE));
                configValue("BLINK", true);
                chatMark = chatSize();
                mc.f_91074_.f_108617_.m_246623_("faceanim off");
                wait = 40;
            }
            case 29 -> {
                String chat = chatSince(chatMark);
                line("   faceanim chat: " + chat.replace('\n', '|'));
                check("/faceanim off: reply says the face stays still for everyone", chat.contains("Face animation off"));
                check("/faceanim off: the switch reached this game (as a viewer of yourself)",
                        !FaceFlags.enabled(mc.f_91074_.m_20148_()));
                check("/faceanim off: no blink for this player", routeAt(mc, closedTick(mc.f_91074_), EYE).equals(EYE));
                line("== rejoin: /faceanim off must survive a fresh login");
                mc.f_91073_.m_7462_();
                mc.m_91320_(new TitleScreen());
                join(mc);
                wait = 60;
            }
            case 30 -> {
                check("fresh login: /faceanim off is kept (stored on the server, sent on login)",
                        !FaceFlags.enabled(mc.f_91074_.m_20148_()));
                chatMark = chatSize();
                mc.f_91074_.f_108617_.m_246623_("faceanim on");
                wait = 40;
            }
            case 31 -> {
                faceStats = StatsProvider.get(StatsCapability.INSTANCE, mc.f_91074_).orElse(null);
                check("/faceanim on: the face animates again", FaceFlags.enabled(mc.f_91074_.m_20148_())
                        && routeAt(mc, closedTick(mc.f_91074_), EYE).equals(EYE_CLOSED));
                line("== shaded bodies follow the switch (Majin, Frost Demon, BioAndroid)");
                for (ResourceLocation body : SHADED_BODIES) {
                    shadedSwitch(body);
                }
                wait = 10;
            }
            case 32 -> {
                finish(mc);
            }
            default -> {
            }
        }
    }

    private static List<?> chat() throws Exception {
        Field f = net.minecraft.client.gui.components.ChatComponent.class.getDeclaredField("f_93760_");
        f.setAccessible(true);
        return (List<?>) f.get(Minecraft.m_91087_().f_91065_.m_93076_());
    }

    private static int chatSize() throws Exception {
        return chat().size();
    }

    /** Chat lines added since {@code mark} (the list is newest first). */
    private static String chatSince(int mark) throws Exception {
        List<?> all = chat();
        StringBuilder sb = new StringBuilder();
        for (int i = all.size() - mark - 1; i >= 0; i--) {
            sb.append(((GuiMessage) all.get(i)).f_240363_().getString()).append('\n');
        }
        return sb.toString();
    }

    /** Taken on the next tick, after a frame drawn with no screen open (DMZ may open its character screen). */
    private void shot(Minecraft mc, String file) {
        mc.m_91152_(null);
        pendingShot = file;
    }

    // ---------------------------------------------------------------- helpers

    // ---------------------------------------------------------------- 0.2.3: face animation and shaded bodies

    private static final String EYE = "textures/entity/races/humansaiyan/faces/humansaiyan_eye_0_0.png";
    private static final String EYE_CLOSED = "textures/entity/races/humansaiyan/faces/anim/humansaiyan_eye_0_0_closed.png";
    private static final String ANGEL_EYE = "textures/entity/races/angel/faces/angel_eye_0_0.png";
    private static final String ANGEL_CLOSED = "textures/entity/races/angel/faces/anim/angel_eye_0_0_closed.png";
    private static final String MOUTH = "textures/entity/races/humansaiyan/faces/humansaiyan_mouth_0.png";
    private static final List<ResourceLocation> SHADED_BODIES = List.of(
            rl("dragonminez", "textures/entity/races/majin/bodytype_female_0_layer1.png"),
            rl("dragonminez", "textures/entity/races/frostdemon/bodytype_0_layer1.png"),
            rl("dragonminez", "textures/entity/races/bioandroid/base_0_layer1.png"));
    private StatsData faceStats;

    /** FaceAnimator's route for {@code path} as if the player were drawn at {@code tick}. */
    private String routeAt(Minecraft mc, int tick, String path) {
        int real = mc.f_91074_.f_19797_;
        try {
            mc.f_91074_.f_19797_ = tick;
            FaceAnimator.begin(mc.f_91074_, faceStats, 0.0F);
            return FaceAnimator.route(path);
        } finally {
            FaceAnimator.end();
            mc.f_91074_.f_19797_ = real;
        }
    }

    /** A tick at which this player's eyes are fully closed (FaceAnimator's own blink formula). */
    private static int closedTick(net.minecraft.client.player.AbstractClientPlayer player) throws Exception {
        java.lang.reflect.Method blink = FaceAnimator.class.getDeclaredMethod("blinkFrame",
                net.minecraft.client.player.AbstractClientPlayer.class, float.class);
        blink.setAccessible(true);
        for (int t = 1000; t < 1200; t++) {
            if ((int) blink.invoke(null, player, (float) t) == 2) {
                return t;
            }
        }
        throw new IllegalStateException("no closed-eye tick in two blink cycles");
    }

    private static void configValue(String field, boolean on) throws Exception {
        Field f = FaceAnimConfig.class.getDeclaredField(field);
        f.setAccessible(true);
        ((net.minecraftforge.common.ForgeConfigSpec.BooleanValue) f.get(null)).set(on);
    }

    private static boolean logHas(String text) throws IOException {
        return Files.readString(FMLPaths.GAMEDIR.get().resolve("logs/latest.log"), StandardCharsets.UTF_8).contains(text);
    }

    /**
     * Build DMZ's shaded body for {@code body} in HD, switch players to vanilla and build it again: the
     * map must be empty after the switch and the rebuilt body must differ. Negative control: put the HD
     * entry back as if the map had not been cleared and DMZ hands back the HD body (the stays-HD bug).
     */
    @SuppressWarnings("unchecked")
    private void shadedSwitch(ResourceLocation body) throws Exception {
        Class<?> sgp = Class.forName("com.dragonminez.client.util.SkinGathererProvider");
        Field mapField = sgp.getDeclaredField("SHADED_LAYERS");
        mapField.setAccessible(true);
        Map<ResourceLocation, ResourceLocation[]> map = (Map<ResourceLocation, ResourceLocation[]>) mapField.get(null);
        java.lang.reflect.Method build = sgp.getDeclaredMethod("shadedLayer", ResourceLocation.class);
        build.setAccessible(true);
        String race = body.m_135815_().split("/")[3];
        ClientState.onServerSync(ClientState.mode().withPlayers(true), false);
        ResourceLocation[] hd = (ResourceLocation[]) build.invoke(null, body);
        long hdHash = shadedHash(hd);
        ClientState.onServerSync(ClientState.mode().withPlayers(false), false);
        boolean emptied = !map.containsKey(body);
        int left = shadedLeft();
        ResourceLocation[] vanilla = (ResourceLocation[]) build.invoke(null, body);
        long vanillaHash = shadedHash(vanilla);
        line("   " + race + ": HD shaded " + Long.toHexString(hdHash) + ", vanilla shaded " + Long.toHexString(vanillaHash)
                + ", DMZ left " + left);
        check(race + ": HD -> vanilla empties DMZ's shaded layer and rebuilds the body from the vanilla file",
                emptied && hd != null && hdHash != vanillaHash);
        ClientState.onServerSync(ClientState.mode().withPlayers(true), false);
        ResourceLocation[] hd2 = (ResourceLocation[]) build.invoke(null, body);
        ClientState.onServerSync(ClientState.mode().withPlayers(false), false);
        map.put(body, hd2); // what an uncleared map looks like
        boolean stale = build.invoke(null, body) == hd2;
        check(race + ": negative control - an uncleared shaded layer keeps the HD body after the switch", stale);
        map.remove(body);
    }

    private static int shadedLeft() throws Exception {
        java.lang.reflect.Method m = Class.forName("com.dbzlegacy.legacytextures.client.CacheFlush")
                .getDeclaredMethod("shadedLeft");
        m.setAccessible(true);
        return (int) m.invoke(null);
    }

    private static long shadedHash(ResourceLocation[] shaded) {
        if (shaded == null) {
            return 0L;
        }
        long h = 1125899906842597L;
        for (ResourceLocation r : shaded) {
            AbstractTexture tex = Minecraft.m_91087_().m_91097_().m_118506_(r);
            if (tex instanceof net.minecraft.client.renderer.texture.DynamicTexture dyn && dyn.m_117991_() != null) {
                com.mojang.blaze3d.platform.NativeImage img = dyn.m_117991_();
                for (int y = 0; y < img.m_85084_(); y++) {
                    for (int x = 0; x < img.m_84982_(); x++) {
                        h = 31 * h + img.m_84985_(x, y);
                    }
                }
            }
        }
        return h;
    }

    private void sync(TextureMode.Preset p, String set) {
        ClientState.onServerSync(TextureMode.of(p, set), false);
    }

    /** Every texture the NB catalog requests (wornTexture + DMZ's torso _layer1) is in this jar. */
    private void nbBase() throws Exception {
        Class<?> catalog = Class.forName("com.dbzlegacy.legacytextures.nb.catalog.Catalog");
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
            List<String> want = new ArrayList<>(List.of((String) c.getMethod("wornTexture", int.class).invoke(e, layer)));
            if ((boolean) c.getField("dmzTorso").get(e)) {
                want.add(c.getField("texturePrefix").get(e) + "_layer1.png");
            }
            for (String f : want) {
                asked++;
                ResourceLocation loc = rl("nbarmors", "textures/armor/" + f);
                if (!raw(loc) || bound(loc).equals(LtHooks.BLANK)) {
                    missing.add(f);
                }
            }
        }
        line("   NB catalog entries " + entries.size() + ", textures requested " + asked + ", missing " + missing.size());
        check("every NB texture the catalog requests draws from the Legacy Textures jar", missing.isEmpty());
    }

    private static long counter(String name) throws Exception {
        Field f = LtHooks.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.getLong(null);
    }

    private int tattoos() throws Exception {
        return (int) Class.forName("com.dragonminez.client.util.TextureCounter").getMethod("getMaxTattooTypes", String.class)
                .invoke(null, "human");
    }

    private static Map<?, ?> loaded() throws Exception {
        Field f = net.minecraft.client.renderer.texture.TextureManager.class.getDeclaredField("f_118468_");
        f.setAccessible(true);
        return (Map<?, ?>) f.get(Minecraft.m_91087_().m_91097_());
    }

    private static String sha(ResourceLocation loc) throws Exception {
        try (java.io.InputStream in = Minecraft.m_91087_().m_91098_().m_213713_(loc).orElseThrow().m_215507_()) {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
        }
    }

    private static String rawSha(String modId, ResourceLocation loc) throws Exception {
        Path p = ModList.get().getModFileById(modId).getFile().findResource("assets", loc.m_135827_(), loc.m_135815_());
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
    }

    private static boolean raw(ResourceLocation loc) {
        return Minecraft.m_91087_().m_91098_().m_213713_(loc).isPresent();
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
