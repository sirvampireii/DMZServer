package com.dbzlegacy.ltprobe;

import com.dbzlegacy.legacytextures.LegacyTextures;
import com.dbzlegacy.legacytextures.TextureMode;
import com.dbzlegacy.legacytextures.client.ClientState;
import com.dbzlegacy.legacytextures.client.ClientStatus;
import com.dbzlegacy.legacytextures.client.LtHooks;
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
                check("vanilla: HD-only tattoo_6 is not a file (DMZ counts stop before it)", !raw(TATTOO6));
                check("vanilla: HD-only female bodytype 3 is not a file", !raw(FEMALE3));
                check("vanilla: HD-only tattoo_6 binds transparent, not the checkerboard", bound(TATTOO6).equals(LtHooks.BLANK));
                for (int i = 0; i < 5; i++) {
                    bound(TATTOO6);
                }
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
                check("DMZ creator offers more tattoos with HD on (selection-skew guard)", hd > vanillaTattoos);
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
                    boolean v = bound(BODY).equals(BODY) && !raw(TATTOO6) && bound(TATTOO6).equals(LtHooks.BLANK)
                            && tattoos() == vanillaTattoos && cubes(geoModel().getBakedModel(GEOMETRY[0])) == cubes(GEOMETRY[0]);
                    sync(TextureMode.Preset.PLAYERS_HD_ONLY, "hd");
                    boolean h = bound(BODY).equals(BODY_HD) && raw(TATTOO6) && tattoos() > vanillaTattoos
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
