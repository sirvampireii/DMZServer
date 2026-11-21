package com.dbzlegacy.uiprobe;

import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraGui;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraGuiActions;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraScreenSpec;
import com.dbzlegacy.adaptivedifficulty.net.gui.GuiOpenPacket;
import com.dbzlegacy.adaptivedifficulty.net.gui.LmGuiNetwork;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test only (never shipped). Server half: crawls every LM Ultra menu a real joined player can reach by
 * following navigation buttons only (page:, open:, nav:, tab:; never do:), records each packet, then sends
 * them one by one; the client half screenshots each at several GUI sizes and answers "uinext".
 */
@Mod("uiprobe")
public final class UiProbe {
    static final Logger LOG = LoggerFactory.getLogger("UiProbe");
    static final String[] ROOTS = {"hub", "difficulty", "rival", "spar", "prestige", "character", "skillcheck",
            "progression", "admin", "skills", "config"};
    private ServerPlayer player;
    private int ticks;
    private boolean crawled;
    private final List<GuiOpenPacket> packets = new ArrayList<>();
    private final List<String> origin = new ArrayList<>();
    private int sendIdx = -1;
    /** First packet the scripted checks kept; -Duiprobe.scriptshots=true screenshots from here only. */
    private int firstScript = -1;
    private volatile boolean advance;
    private int waitTicks;

    public UiProbe() {
        if (System.getProperty("uiprobe") != null) {
            MinecraftForge.EVENT_BUS.addListener(this::login);
            MinecraftForge.EVENT_BUS.addListener(this::tick);
            MinecraftForge.EVENT_BUS.addListener(this::chat);
        }
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT
                && System.getProperty("uiprobe.server") != null) {
            new UiClient();
        }
    }

    private void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            player = p;
            ticks = 0;
        }
    }

    private void chat(ServerChatEvent e) {
        if (e.getRawText().startsWith("uinext")) {
            advance = true;
            e.setCanceled(true);
        }
    }

    private static String semantic(String raw) {
        String s = raw == null ? "" : raw;
        if (s.startsWith("@")) {
            int c = s.indexOf(':');
            s = c > 0 ? s.substring(c + 1) : s;
        }
        return s;
    }

    private static boolean navigation(String sem) {
        return sem.startsWith("page:") || sem.startsWith("open:") || sem.startsWith("nav:") || sem.startsWith("tab:");
    }

    /** Page id with digits collapsed: limits paging explosions (bodyparts:0, bodyparts:1, ...). */
    private static String family(GuiOpenPacket p) {
        return p.screenId() + "|" + p.pageId().replaceAll("[0-9]+", "#");
    }

    /** 4.7.55: stand-in online players (Forge fake players) so the name pickers have more than 20 names. */
    private final List<ServerPlayer> fakes = new ArrayList<>();

    private void addFakes() {
        int n = Integer.getInteger("uiprobe.fakes", 0);
        List<ServerPlayer> online = backing();
        for (int i = 1; i <= n; i++) {
            String name = String.format("Tester%02d", i);
            com.mojang.authlib.GameProfile gp = new com.mojang.authlib.GameProfile(
                    java.util.UUID.nameUUIDFromBytes(("uiprobe:" + name).getBytes(StandardCharsets.UTF_8)), name);
            ServerPlayer f = net.minecraftforge.common.util.FakePlayerFactory.get(player.m_284548_(), gp);
            online.add(f);
            fakes.add(f);
        }
        LOG.info("uiprobe added {} stand-in players; online now {}", n, online.size());
    }

    /** PlayerList.players (f_11196_): getPlayers() is read-only under Forge. */
    @SuppressWarnings("unchecked")
    private List<ServerPlayer> backing() {
        try {
            java.lang.reflect.Field f = net.minecraft.server.players.PlayerList.class.getDeclaredField("f_11196_");
            f.setAccessible(true);
            return (List<ServerPlayer>) f.get(player.m_20194_().m_6846_());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void crawl() {
        addFakes();
        try {
            crawlInner();
        } finally {
            backing().removeAll(fakes);
        }
    }

    private void crawlInner() {
        Map<String, Integer> seen = new LinkedHashMap<>();
        Map<String, Integer> perFamily = new HashMap<>();
        Deque<String[]> queue = new ArrayDeque<>();
        for (String r : ROOTS) {
            queue.add(new String[] {"open", r, "main"});
        }
        // 4.7.55: Preview forms asked for directly (no button), as staff and as a player.
        queue.add(new String[] {"act", "character", "page:form_preview:0", "main"});
        int dispatched = 0;
        while (!queue.isEmpty() && packets.size() < 500 && dispatched < 3000) {
            String[] step = queue.poll();
            dispatched++;
            List<GuiOpenPacket> got;
            try {
                if ("open".equals(step[0])) {
                    got = UltraScreenSpec.captureSends(() -> UltraGui.open(player, step[1], step[2]));
                } else {
                    JsonObject args = new JsonObject();
                    args.addProperty("screen", step[1]);
                    args.addProperty("page", step[3]);
                    got = UltraScreenSpec.captureSends(
                            () -> UltraGuiActions.dispatch(player, step[1], step[2], args.toString()));
                }
            } catch (Throwable t) {
                LOG.warn("uiprobe step failed {} {}", String.join(" ", step), t.toString());
                continue;
            }
            if (got == null) {
                continue;
            }
            for (GuiOpenPacket p : got) {
                String key = p.screenId() + "|" + p.pageId() + "|" + p.title() + "|" + p.activeTab();
                if (seen.containsKey(key)) {
                    continue;
                }
                String fam = family(p);
                int n = perFamily.merge(fam, 1, Integer::sum);
                if (n > 3) {
                    continue;
                }
                seen.put(key, packets.size());
                packets.add(p);
                origin.add(String.join(" ", step));
                try {
                    JsonArray btns = JsonParser.parseString(p.buttonsJson()).getAsJsonArray();
                    for (JsonElement el : btns) {
                        JsonObject b = el.getAsJsonObject();
                        String id = b.get("id").getAsString();
                        if (navigation(semantic(id))) {
                            queue.add(new String[] {"act", p.screenId(), id, p.pageId()});
                        }
                    }
                } catch (Throwable t) {
                    LOG.warn("uiprobe bad buttonsJson {}", key);
                }
            }
        }
        runScript();
        skinPass();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < packets.size(); i++) {
            GuiOpenPacket p = packets.get(i);
            JsonObject o = new JsonObject();
            o.addProperty("i", i);
            o.addProperty("from", origin.get(i));
            o.addProperty("screen", p.screenId());
            o.addProperty("page", p.pageId());
            o.addProperty("title", p.title());
            o.addProperty("tab", p.activeTab());
            o.addProperty("panelW", p.panelWidth());
            o.addProperty("panelH", p.panelHeight());
            o.addProperty("layoutLen", p.layoutJson().length());
            o.add("layout", JsonParser.parseString(p.layoutJson()));
            o.add("buttons", JsonParser.parseString(p.buttonsJson()));
            o.add("lines", JsonParser.parseString(p.linesJson()));
            o.add("inputs", JsonParser.parseString(p.inputsJson() == null || p.inputsJson().isBlank() ? "[]" : p.inputsJson()));
            sb.append(o).append('\n');
        }
        write("uiprobe-packets.jsonl", sb.toString(), false);
        LOG.info("uiprobe crawled {} packets with {} dispatches", packets.size(), dispatched);
    }


    // ---- 4.7.62 menu skins pass (test only) ----
    static final String[] SKIN_IDS = {"saiyan_royal", "namek_serenity", "obsidian_gold", "beerus_destruction",
            "zeno_omni"};
    /** One page per system, then the picker itself. */
    static final String[][] SKIN_PAGES = {{"hub", "main"}, {"hub", "skins"}, {"difficulty", "main"},
            {"difficulty", "your_tier"}, {"rival", "home"}, {"spar", "main"}, {"prestige", "main"},
            {"character", "main"}, {"skills", "main"}, {"admin", "admin"}, {"config", "edit:difficulty:0:"}};
    /** Lite is checked on the picker, the Momentum page and the model page; backgrounds off on the main menu. */
    static final String[][] LITE_PAGES = {{"hub", "skins"}, {"difficulty", "your_tier"}, {"character", "main"}};
    private static final String LOOK_TAG = "script skins look=";
    private String lookNow = "saiyan_royal,on,full";

    /**
     * Every skin on one page per system (Full, backgrounds on), Lite on three pages per skin, backgrounds off on the
     * main menu for two skins. Each packet is captured in its look and tagged "script skins look=skin,bg,look"; the
     * send loop puts the probe player in that look right before the page goes out, so the client draws it there.
     * An LM jar without menu skins (4.7.61 and older) gets one default pass: same pages, for the FPS baseline.
     */
    private void skinPass() {
        boolean skins = cls("com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuPrefs") != null;
        try {
            com.dbzlegacy.adaptivedifficulty.progression.Momentum.bank(player);
        } catch (Throwable ignored) {
        }
        String[] ids = skins ? SKIN_IDS : new String[] {"saiyan_royal"};
        for (String id : ids) {
            for (String[] pg : SKIN_PAGES) {
                if (!skins && "skins".equals(pg[1])) {
                    continue;
                }
                skinKeep(id, true, false, pg);
            }
            if (skins) {
                for (String[] pg : LITE_PAGES) {
                    skinKeep(id, true, true, pg);
                }
            }
        }
        if (skins) {
            skinKeep("saiyan_royal", false, false, SKIN_PAGES[0]);
            skinKeep("zeno_omni", false, false, SKIN_PAGES[0]);
        }
        applyLook("saiyan_royal,on,full");
    }

    private void skinKeep(String id, boolean bg, boolean lite, String[] pg) {
        String look = id + "," + (bg ? "on" : "off") + "," + (lite ? "lite" : "full");
        applyLook(look);
        List<GuiOpenPacket> got;
        try {
            got = UltraScreenSpec.captureSends(() -> UltraGui.open(player, pg[0], pg[1]));
        } catch (Throwable t) {
            note("ERROR skins " + look + " " + pg[0] + "|" + pg[1] + " " + t);
            return;
        }
        for (GuiOpenPacket p : got) {
            packets.add(p);
            origin.add(LOOK_TAG + look + " " + pg[0] + "|" + pg[1]);
        }
    }

    private static Class<?> cls(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Put the probe player in {@code look} ("skin,on|off,full|lite") and push it to their LM client. */
    private void applyLook(String look) {
        if (look.equals(lookNow)) {
            return;
        }
        Class<?> prefs = cls("com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuPrefs");
        Class<?> rec = cls("com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuPrefs$Prefs");
        if (prefs == null || rec == null) {
            return;
        }
        String[] f = look.split(",");
        try {
            Object v = rec.getConstructor(String.class, boolean.class, boolean.class)
                    .newInstance(f[0], "on".equals(f[1]), "lite".equals(f[2]));
            prefs.getMethod("put", ServerPlayer.class, rec).invoke(null, player, v);
            cls("com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork").getMethod("sendPrefs", ServerPlayer.class)
                    .invoke(null, player);
            lookNow = look;
            note("LOOK " + look);
        } catch (ReflectiveOperationException e) {
            note("ERROR look " + look + " " + e);
        }
    }

    /** Per-session client state the logout handler clears (class#field -> this player's value). */
    private static final String[] SESSION = {
        "com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork#FEATURES",
        "com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork#CLIENT_VERSION",
        "com.dbzlegacy.adaptivedifficulty.net.gui.UltraClientFeatures#BY_PLAYER"};

    @SuppressWarnings("unchecked")
    private Map<String, Object> sessionSnapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String ref : SESSION) {
            try {
                String[] f = ref.split("#");
                java.lang.reflect.Field fld = Class.forName(f[0]).getDeclaredField(f[1]);
                fld.setAccessible(true);
                Object v = ((Map<Object, Object>) fld.get(null)).get(player.m_20148_());
                if (v != null) {
                    out.put(ref, v);
                }
            } catch (Throwable t) {
                note("session snapshot " + ref + " " + t);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private void sessionRestore(Map<String, Object> snap) {
        for (Map.Entry<String, Object> e : snap.entrySet()) {
            try {
                String[] f = e.getKey().split("#");
                java.lang.reflect.Field fld = Class.forName(f[0]).getDeclaredField(f[1]);
                fld.setAccessible(true);
                ((Map<Object, Object>) fld.get(null)).put(player.m_20148_(), e.getValue());
            } catch (Throwable t) {
                note("session restore " + e.getKey() + " " + t);
            }
        }
        note("SESSION kept across the fake logout: " + snap.keySet().size() + " entries");
    }

    // ---- 4.7.56 scripted checks (test only) ----
    private final StringBuilder scriptLog = new StringBuilder();

    private void note(String line) {
        scriptLog.append(line).append('\n');
        LOG.info("uiscript {}", line);
    }

    private List<GuiOpenPacket> keep(String tag, Runnable r) {
        List<GuiOpenPacket> got;
        try {
            got = UltraScreenSpec.captureSends(r);
        } catch (Throwable t) {
            note("ERROR " + tag + " " + t);
            return List.of();
        }
        for (GuiOpenPacket p : got) {
            packets.add(p);
            origin.add("script " + tag);
            note("PACKET " + tag + " -> " + p.screenId() + "|" + p.pageId() + " buttons=" + labels(p));
        }
        return got;
    }

    private static String labels(GuiOpenPacket p) {
        StringBuilder b = new StringBuilder();
        try {
            for (JsonElement el : JsonParser.parseString(p.buttonsJson()).getAsJsonArray()) {
                JsonObject o = el.getAsJsonObject();
                if (o.has("label")) {
                    b.append('[').append(o.get("label").getAsString().replaceAll("§.", "")).append(']');
                }
            }
            b.append(" lines=");
            for (JsonElement el : JsonParser.parseString(p.linesJson()).getAsJsonArray()) {
                b.append('{').append(el.getAsString().replaceAll("§.", "")).append('}');
            }
        } catch (Throwable ignored) {
        }
        return b.toString();
    }

    private String act(String action, String arg, String page) {
        com.dbzlegacy.adaptivedifficulty.service.DifficultyActions.Result r =
                com.dbzlegacy.adaptivedifficulty.service.DifficultyActions.handleArgNoReopen(player, action, arg, page);
        return r == null || r.message() == null ? "" : r.message().replaceAll("§.", "");
    }

    private long trialStart;

    private net.minecraft.world.entity.Mob spawnZombie(boolean paint, boolean wave) {
        net.minecraft.server.level.ServerLevel lvl = player.m_284548_();
        net.minecraft.world.entity.Mob z = (net.minecraft.world.entity.Mob)
                net.minecraft.world.entity.EntityType.f_20501_.m_20615_(lvl);
        z.m_6034_(player.m_20185_() + 3.0, player.m_20186_(), player.m_20189_() + 3.0);
        lvl.m_7967_(z);
        if (paint) {
            com.dbzlegacy.adaptivedifficulty.scaling.MobScaling.retargetToPlayer(z, player);
        }
        if (wave) {
            com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess.get(z).m_128362_(
                    com.dbzlegacy.adaptivedifficulty.progression.ChamberKillMastery.WAVE_FOE, player.m_20148_());
        }
        return z;
    }

    private void killByPlayer(net.minecraft.world.entity.Mob z) {
        z.m_6667_(player.m_269291_().m_269075_(player));
        z.m_146870_();
    }

    private static boolean painted(net.minecraft.world.entity.Mob z) {
        return com.dbzlegacy.adaptivedifficulty.scaling.MobScaling.isAdPainted(z);
    }

    private long left() {
        return com.dbzlegacy.adaptivedifficulty.progression.Momentum.remainingMillis(player);
    }

    private double mastery(net.minecraft.world.entity.Mob victim, boolean mark) {
        net.minecraftforge.event.entity.living.LivingHurtEvent ev = new net.minecraftforge.event.entity.living.LivingHurtEvent(
                victim, player.m_269291_().m_269075_(player), 5f);
        if (mark) {
            com.dbzlegacy.adaptivedifficulty.progression.Momentum.begin(ev);
        }
        try {
            return com.dragonminez.server.util.PotionEffectHelper.applyMasteryGainMultiplier(player, 1.0);
        } finally {
            com.dbzlegacy.adaptivedifficulty.progression.Momentum.end();
        }
    }

    private static boolean has(GuiOpenPacket p, String text) {
        return (p.buttonsJson() + p.linesJson()).replaceAll("\u00a7.", "").contains(text);
    }

    private void runScript() {
        firstScript = packets.size();
        boolean staff = com.dbzlegacy.adaptivedifficulty.util.StaffAccess.isStaff(player);
        note("staff=" + staff);
        var cfg = com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig.get();
        var d = com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache.data(player);
        try {
            d.setPersonalEnabled(true);
            d.setActiveTier(0);
            com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache.save(player);
            com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache.refresh(player);
            // ---------------- menu flow
            for (GuiOpenPacket p : keep("step1 home", () -> UltraGui.open(player, "difficulty", "main"))) {
                note("STEP1 " + (has(p, "Step 1 of 3 - What is Difficulty") && has(p, "[x] [ ] [ ]")
                        && has(p, "Next: Pick your level") && has(p, "Back to main menu") ? "PASS" : "FAIL"));
            }
            for (GuiOpenPacket p : keep("step2 tiers no tier", () -> UltraGui.open(player, "difficulty", "tiers"))) {
                note("RECOMMEND no tier -> T1: " + (has(p, "T1 Awakened - Recommended") ? "PASS" : "FAIL")
                        + " | locked T5 dim+opens detail: " + (p.buttonsJson().contains("tier_ask:5")
                        && has(p, "T5 Master - Locked") ? "PASS" : "FAIL") + " | " + labels(p));
            }
            for (GuiOpenPacket p : keep("detail T5 locked", () -> UltraGui.open(player, "difficulty", "tier_ask:5"))) {
                note("LOCKED DETAIL states requirement: " + (has(p, "Needs Level") && has(p, "not yet")
                        && has(p, "T5 is locked") ? "PASS" : "FAIL") + " | " + labels(p));
            }
            for (GuiOpenPacket p : keep("detail T1", () -> UltraGui.open(player, "difficulty", "tier_ask:1"))) {
                note("DETAIL T1 before/after+trial: " + (has(p, "Enemy damage") && has(p, "TP earned")
                        && has(p, "Mastery gain +50% while Momentum time is banked") && has(p, "Try one fight free")
                        && has(p, "Beat difficulty mobs to bank Mastery Boost time") ? "PASS" : "FAIL") + " | " + labels(p));
            }
            String m = act("activate", "1", "tier_ask:1");
            note("CONFIRM T1 -> " + m + " | active=" + d.getActiveTier() + (d.getActiveTier() == 1 ? " PASS" : " FAIL"));
            com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem.adminGrantTier(player, 2);
            for (GuiOpenPacket p : keep("step2 tiers on T1", () -> UltraGui.open(player, "difficulty", "tiers"))) {
                note("YOU ARE HERE T1 + RECOMMEND T2: " + (has(p, "T1 Awakened - You are here")
                        && has(p, "T2 Enhanced - Recommended") ? "PASS" : "FAIL") + " | " + labels(p));
            }
            m = act("activate", "2", "tier_ask:2");
            note("CONFIRM T2 -> " + m + " | active=" + d.getActiveTier() + (d.getActiveTier() == 2 ? " PASS" : " FAIL"));
            for (GuiOpenPacket p : keep("detail T1 down", () -> UltraGui.open(player, "difficulty", "tier_ask:1"))) {
                note("SWITCH DOWN offers Switch to T1 on the confirm page: " + (has(p, "Switch to T1") ? "PASS" : "FAIL"));
            }
            m = act("activate", "1", "tier_ask:1");
            note("SWITCH DOWN -> " + m + " | active=" + d.getActiveTier() + (d.getActiveTier() == 1 ? " PASS" : " FAIL"));
            keep("step3 your tier", () -> UltraGui.open(player, "difficulty", "your_tier"));
            for (GuiOpenPacket p : keep("summons", () -> UltraGui.open(player, "difficulty", "summons"))) {
                note("SUMMONS chrome: " + (has(p, "Boss summons") && has(p, "Back to Step 1") ? "PASS" : "FAIL"));
            }
            for (GuiOpenPacket p : keep("summon dragon", () -> UltraGui.open(player, "difficulty", "end_dragon"))) {
                note("SUMMON locked still dim+inert: " + (p.buttonsJson().contains("nav:none") && has(p, "Summon locked") ? "PASS" : "FAIL"));
            }

            // ---------------- Momentum
            com.dbzlegacy.adaptivedifficulty.progression.Momentum.clear(player);
            var z1 = spawnZombie(true, false);
            note("painted zombie: " + painted(z1));
            killByPlayer(z1);
            long a1 = left();
            note("KILL 1 banks 10s: left=" + a1 + (a1 > 9_000 && a1 <= 10_000 ? " PASS" : " FAIL"));
            Thread.sleep(1_000);
            var z2 = spawnZombie(true, false);
            killByPlayer(z2);
            long a2 = left();
            note("KILL 2 adds 10s inside the window: left=" + a2 + (a2 > a1 + 8_000 && a2 <= 20_000 ? " PASS" : " FAIL"));
            // 4.7.60: Step 3 with time banked, so the screenshots show a filled Momentum bar.
            keep("step3 momentum banked", () -> UltraGui.open(player, "difficulty", "your_tier"));
            Thread.sleep(1_000);
            long before = left();
            note("COUNTS DOWN: " + a2 + " -> " + before + (before < a2 - 800 ? " PASS" : " FAIL"));
            var zw = spawnZombie(true, true);
            killByPlayer(zw);
            note("WAVE FOE kill does not bank: " + before + " -> " + left() + (left() <= before ? " PASS" : " FAIL"));
            var zp = spawnZombie(false, false);
            boolean zpPainted = painted(zp);
            killByPlayer(zp);
            note("UNSCALED kill does not bank (painted=" + zpPainted + "): " + (left() <= before ? "PASS" : "FAIL"));
            int oldMax = cfg.momentumMaxBankSeconds;
            cfg.momentumMaxBankSeconds = 30;
            for (int i = 0; i < 3; i++) {
                killByPlayer(spawnZombie(true, false));
            }
            long capped = left();
            cfg.momentumMaxBankSeconds = oldMax;
            note("CAP at max (30s for the test): left=" + capped + (capped > 29_000 && capped <= 30_000 ? " PASS" : " FAIL"));
            keep("step3 momentum full", () -> UltraGui.open(player, "difficulty", "your_tier"));

            var target = spawnZombie(true, false);
            var plain = spawnZombie(false, false);
            double base = mastery(target, false);
            double inside = mastery(target, true);
            double plainHit = mastery(plain, true);
            note("MASTERY inside window on a difficulty mob: base=" + base + " boosted=" + inside
                    + (Math.abs(inside - base * cfg.momentumMasteryMultiplier) < 1e-9 ? " PASS" : " FAIL")
                    + " | on an unscaled mob: " + plainHit + (plainHit == base ? " PASS" : " FAIL"));
            com.dbzlegacy.adaptivedifficulty.progression.Momentum.clear(player);
            double outside = mastery(target, true);
            note("MASTERY outside window: " + outside + (outside == base ? " PASS" : " FAIL"));
            target.m_146870_();
            plain.m_146870_();

            var friend = net.minecraftforge.common.util.FakePlayerFactory.get(player.m_284548_(),
                    new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("uiprobe:party2".getBytes()), "Party2"));
            com.dbzlegacy.adaptivedifficulty.progression.Momentum.bank(player);
            long mine = left();
            com.dbzlegacy.adaptivedifficulty.progression.Momentum.bank(friend);
            com.dbzlegacy.adaptivedifficulty.progression.Momentum.bank(friend);
            long friendLeft = com.dbzlegacy.adaptivedifficulty.progression.Momentum.remainingMillis(friend);
            note("PARTY banks independent: me=" + mine + " friend=" + friendLeft + " me after=" + left()
                    + (friendLeft > 19_000 && left() <= mine && left() > mine - 500 ? " PASS" : " FAIL"));
            com.dbzlegacy.adaptivedifficulty.progression.Momentum.clear(friend);

            java.lang.reflect.Method sw = com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeCharacter.class
                    .getDeclaredMethod("switchedIn", ServerPlayer.class, java.util.UUID.class, java.util.UUID.class,
                            int.class, boolean.class);
            sw.setAccessible(true);
            java.util.UUID me = com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeCharacter.id(player);
            sw.invoke(null, player, me, me, 0, false);
            note("SLOT SWITCH clears the bank: " + left() + (left() == 0L ? " PASS" : " FAIL"));

            // ---------------- free trial
            m = act("trial", "2", "tier_ask:2");
            note("TRIAL T2 from T1 -> " + m + " | active=" + d.getActiveTier() + (d.getActiveTier() == 2 ? " PASS" : " FAIL"));
            m = act("trial", "2", "tier_ask:2");
            note("TRIAL T2 again (one per tier) -> " + m + (m.contains("already tried") || m.contains("already running") ? " PASS" : " FAIL"));
            for (GuiOpenPacket p : keep("step3 during trial", () -> UltraGui.open(player, "difficulty", "your_tier"))) {
                note("STEP3 shows trial + momentum: " + (has(p, "Free trial") && has(p, "Momentum") ? "PASS" : "FAIL"));
            }
            killByPlayer(spawnZombie(true, false));
            note("MOMENTUM banks during a trial: " + left() + (left() > 9_000 ? " PASS" : " FAIL"));
            com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore.root(player).m_128469_("lm_difficulty_trial")
                    .m_128356_("endsAt", System.currentTimeMillis() - 1L);
            com.dbzlegacy.adaptivedifficulty.service.DifficultyTrial.pulse(player.m_20194_());
            note("TRIAL END reverts T2 -> T1: active=" + d.getActiveTier() + (d.getActiveTier() == 1 ? " PASS" : " FAIL"));
            m = act("trial", "2", "tier_ask:2");
            note("TRIAL T2 after it ended (one per tier per character) -> " + m + " | active=" + d.getActiveTier()
                    + (m.contains("already tried") && d.getActiveTier() == 1 ? " PASS" : " FAIL"));
            m = act("reset", "", "clear_tier_ask");
            note("clear tier -> " + m + " active=" + d.getActiveTier());
            m = act("trial", "1", "tier_ask:1");
            trialStart = System.currentTimeMillis();
            note("REALTIME TRIAL T1 from no tier -> " + m + " | active=" + d.getActiveTier()
                    + (d.getActiveTier() == 1 ? " PASS (checked again after 5 minutes)" : " FAIL"));

            com.dbzlegacy.adaptivedifficulty.progression.Momentum.bank(player);
            long pre = left();
            // The client stays connected: keep its hello (native features, KaiPath features) across the fake
            // logout, or every later menu routes as if the client never said hello (4.7.61 rig: Preview forms
            // fell back to KaiPath only because of this).
            Map<String, Object> session = sessionSnapshot();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
            sessionRestore(session);
            note("LOGOUT clears the bank: " + pre + " -> " + left() + (pre > 0 && left() == 0L ? " PASS" : " FAIL"));
            // The logout handler dropped the cached record; the trial lives in the store, so it still reverts.
        } catch (Throwable t) {
            note("ERROR script " + t);
            LOG.error("script", t);
        }
        write("uiprobe-script.txt", scriptLog.toString(), false);
    }

    static void write(String name, String text, boolean append) {
        try {
            Path f = FMLPaths.GAMEDIR.get().resolve(name);
            if (append) {
                Files.writeString(f, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(f, text, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean finalPhase;
    private int finalStep;
    private int finalWait;

    /** 4.7.58 native checks after the shots: spin snap-back, locked click, tab click, stand-in preview. */
    private static final String[][] FINAL = {
        // 4.7.60 spin check with screenshots: spinning, stopped (front), spin ~5 s to the back, stopped (front).
        {"open", "character", "main"}, {"wait", "60"}, {"say", "uirecord rot1-spinning"}, {"wait", "40"},
        {"say", "uiclick id:model_rotate"}, {"wait", "60"}, {"say", "uirecord rot2-stopped"}, {"wait", "40"},
        {"say", "uiclick id:model_rotate"}, {"wait", "96"}, {"say", "uirecord rot3-spun-to-back"}, {"wait", "4"},
        {"say", "uiclick id:model_rotate"}, {"wait", "60"}, {"say", "uirecord rot4-stopped-after-back"},
        {"wait", "40"},
        {"trialcheck"}, {"wait", "40"}, {"say", "uiquit"},
    };

    private void finalTick() {
        if (finalWait > 0) {
            finalWait--;
            return;
        }
        if (finalStep >= FINAL.length) {
            write("uiprobe-script.txt", scriptLog.toString(), false);
            player = null;
            return;
        }
        String[] st = FINAL[finalStep++];
        switch (st[0]) {
            case "wait" -> finalWait = Integer.parseInt(st[1]);
            case "trialcheck" -> {
                if (trialStart > 0L && System.currentTimeMillis() < trialStart + 305_000L) {
                    finalStep--;
                    finalWait = 20;
                    return;
                }
                var dd = com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache.data(player);
                note("TRIAL REALTIME after " + (System.currentTimeMillis() - trialStart) / 1000L + "s: active="
                        + dd.getActiveTier() + " trial=" + com.dbzlegacy.adaptivedifficulty.service.DifficultyTrial.active(player)
                        + (trialStart > 0L && dd.getActiveTier() == 0
                                && com.dbzlegacy.adaptivedifficulty.service.DifficultyTrial.active(player) == null
                                ? " PASS reverted to no tier" : " FAIL"));
                write("uiprobe-script.txt", scriptLog.toString(), false);
            }
            case "say" -> {
                note("FINAL say " + st[1]);
                say(st[1]);
            }
            case "open" -> {
                // The final checks run for minutes after the combat script: a stray mob must not kill the
                // probe player mid-check (a death screen replaces the menu being checked).
                player.m_150110_().f_35934_ = true;
                player.m_6885_();
                player.m_21153_(player.m_21233_());
                note("FINAL open " + st[1] + " " + st[2]);
                UltraGui.open(player, st[1], st[2]);
            }
            default -> { }
        }
    }

    private void say(String s) {
        player.m_213846_(Component.m_237113_(s));
    }

    private void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || player == null) {
            return;
        }
        ticks++;
        if (!crawled) {
            if (ticks < 300) {
                return;
            }
            crawled = true;
            crawl();
            advance = true;
            return;
        }
        if (System.getProperty("uiprobe.norender") != null) {
            if (sendIdx < 0) {
                sendIdx = 0;
                say("uiquit");
            }
            return;
        }
        if (finalPhase) {
            finalTick();
            return;
        }
        waitTicks++;
        if (advance || waitTicks > 20 * 90) {
            advance = false;
            waitTicks = 0;
            sendIdx++;
            if (Boolean.getBoolean("uiprobe.scriptshots") && firstScript > 0 && sendIdx < firstScript) {
                sendIdx = firstScript;
            }
            int cap = Integer.getInteger("uiprobe.maxshots", Integer.MAX_VALUE);
            if (sendIdx >= packets.size() || sendIdx >= cap) {
                applyLook("saiyan_royal,on,full");
                finalPhase = true;
                return;
            }
            GuiOpenPacket p = packets.get(sendIdx);
            String from = origin.get(sendIdx);
            boolean skinShot = from.startsWith(LOOK_TAG);
            // 4.7.62: the page goes out in its look (default for everything outside the skins pass).
            applyLook(skinShot ? from.substring(LOOK_TAG.length()).split(" ")[0] : "saiyan_royal,on,full");
            if (skinShot) {
                say("uifps");
            }
            say("uishot " + sendIdx + " " + p.screenId() + " " + p.pageId().replace(' ', '_'));
            LmGuiNetwork.sendOpen(player, p);
        }
    }
}
