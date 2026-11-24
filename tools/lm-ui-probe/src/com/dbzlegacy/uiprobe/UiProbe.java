package com.dbzlegacy.uiprobe;

import com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.NavHistory;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.PageRegistry;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraGui;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraGuiActions;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraHubScreens;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraScreenSpec;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
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
        } else if (e.getRawText().startsWith("uihudrec")) {
            e.setCanceled(true);
            hudReport(e.getRawText());
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
        return sem.startsWith("page:") || sem.startsWith("open:") || sem.startsWith("nav:") || sem.startsWith("tab:")
                || sem.startsWith("go:");
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
        // After the scripted run: the probe account then has a dojo (dojoScript), so the dojo pages are reachable.
        if (com.dbzlegacy.adaptivedifficulty.dojo.DojoService.dojoOf(player) == null) {
            com.dbzlegacy.adaptivedifficulty.dojo.DojoService.found(player);
            note("NAV founded a dojo for the audit");
        }
        navAudit();
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
            {"difficulty", "your_tier"}, {"hub", "goals"}, {"hub", "more"}, {"rival", "home"}, {"spar", "main"}, {"prestige", "main"},
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
        // 4.7.64 rig: the screenshots that follow run for minutes after the combat script. A zombie left over from
        // it killed the probe player mid-shot (a death screen replaced Prestige Home), so protect the player and
        // clear the script's mobs before any screenshot.
        try {
            player.m_150110_().f_35934_ = true;
            player.m_6885_();
            player.m_21153_(player.m_21233_());
            List<net.minecraft.world.entity.Entity> mobs = new ArrayList<>();
            for (net.minecraft.world.entity.Entity e : player.m_284548_().m_142646_().m_142273_()) {
                if (e instanceof net.minecraft.world.entity.monster.Monster) {
                    mobs.add(e);
                }
            }
            mobs.forEach(net.minecraft.world.entity.Entity::m_146870_);
        } catch (Throwable t) {
            LOG.warn("uiprobe could not protect the probe player: {}", t.toString());
        }
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

    // ---- 4.7.64 central hub and spar board (test only) ----
    /** PlayerList.playersByUUID (f_11197_): getPlayer(UUID) reads this map, not the list. */
    @SuppressWarnings("unchecked")
    private Map<java.util.UUID, ServerPlayer> byUuid() {
        try {
            java.lang.reflect.Field f = net.minecraft.server.players.PlayerList.class.getDeclaredField("f_11197_");
            f.setAccessible(true);
            return (Map<java.util.UUID, ServerPlayer>) f.get(player.m_20194_().m_6846_());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<GuiOpenPacket> hubClick(String tag, String screen, String actionId, String page) {
        JsonObject args = new JsonObject();
        args.addProperty("screen", screen);
        args.addProperty("page", page);
        return keep(tag, () -> UltraGuiActions.dispatch(player, screen, actionId, args.toString()));
    }

    private static String where(List<GuiOpenPacket> got) {
        if (got == null || got.isEmpty()) {
            return "(nothing)";
        }
        GuiOpenPacket p = got.get(got.size() - 1);
        return p.screenId() + "|" + p.pageId();
    }

    /** 4.7.64: the two-meditating-NPCs path is gone; the player path credits the full rate. */
    private void meditationScript() {
        try {
            Class<?> med = com.dbzlegacy.adaptivedifficulty.progression.skills.LivingWorldNearbyMeditation.class;
            java.lang.reflect.Method credited = med.getDeclaredMethod("creditedMs", ServerPlayer.class, long.class,
                    boolean.class);
            credited.setAccessible(true);
            long withPlayer = (long) credited.invoke(null, player, 10_000L, true);
            long other = (long) credited.invoke(null, player, 10_000L, false);
            int held = Math.max(0, Math.min(10,
                    com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeSystem.heldCountForNeed(player)));
            long want = 10_000L + 10_000L * held * 10L / 100L;
            note("MEDITATION full rate with a player partner (" + held + " held): " + withPlayer
                    + (withPlayer == want && other == want ? " PASS (no 75% NPC rate left)" : " FAIL want " + want));
            boolean noFighter = true;
            for (java.lang.reflect.Field f : med.getDeclaredFields()) {
                if (f.getName().toLowerCase(java.util.Locale.ROOT).contains("fighter")
                        || f.getName().contains("NPC") || f.getName().contains("CircleMember")) {
                    noFighter = false;
                }
            }
            for (java.lang.reflect.Method m : med.getDeclaredMethods()) {
                if (m.getName().contains("Npc") || m.getName().contains("Fighter") || m.getName().equals("circleMember")) {
                    noFighter = false;
                }
            }
            note("MEDITATION no NPC / fighter code left: " + (noFighter ? "PASS" : "FAIL"));
            java.util.List<String> lines = new java.util.ArrayList<>();
            var stats = com.dbzlegacy.adaptivedifficulty.calc.DmzProgression.stats(player);
            com.dbzlegacy.adaptivedifficulty.progression.skills.LivingWorldNearbyMeditation.appendLines(lines, player,
                    stats.getSkills());
            String all = String.join(" | ", lines).replaceAll("§.", "");
            note("MEDITATION skill lines are player-only: " + (all.contains("Meditate near another player.")
                    && !all.contains("NPC") ? "PASS" : "FAIL") + " | " + all);
        } catch (Throwable t) {
            note("MEDITATION check FAIL " + t);
        }
    }

    private void hubScript() {
        boolean staff = com.dbzlegacy.adaptivedifficulty.util.StaffAccess.isStaff(player);
        for (GuiOpenPacket p : keep("hub home", () -> UltraGui.open(player, "hub", "main"))) {
            note("HUB HOME dashboard: " + (has(p, "Your next prestige") && has(p, "Fastest now:")
                    && (has(p, "to your next prestige") || has(p, "You can prestige now")) && has(p, "Train")
                    && has(p, "Compete") && has(p, "Difficulty:") && has(p, "Rival:") && has(p, "Dojo:")
                    ? "PASS" : "FAIL") + " | " + labels(p));
            // Tabs travel as buttons (tab:<id>), in order.
            String b = p.buttonsJson();
            note("HUB HOME tabs Home/Goals/More, Home first, no Search tab (4.7.80): " + (b.indexOf("tab:home") >= 0
                    && b.indexOf("tab:home") < b.indexOf("tab:goals") && b.indexOf("tab:goals") < b.indexOf("tab:more")
                    && !b.contains("tab:search") ? "PASS" : "FAIL"));
            note("HUB HOME has no search row (4.7.78: it moved to More): " + pf(p.inputsJson() == null
                    || !p.inputsJson().contains("hub_search")));
            note("HUB HOME tiles grouped (Prestige, Difficulty, Sparring, Rival, Dojo, Character): "
                    + (has(p, "Prestige") && labels(p).contains("[Sparring]") && labels(p).contains("[Dojo]")
                    && !has(p, "Spar Board") && !has(p, "My dojo") && !has(p, "Dojo wars")
                    && has(p, "Character Services") && !has(p, "Menu skins") && !has(p, "Staff Admin")
                    ? "PASS" : "FAIL"));
            note("HUB HOME no made-up numbers, no other mods: " + (!has(p, "35%") && !has(p, "/home")
                    && !has(p, "/spawn") && !has(p, "/tpa") && !has(p, "/empire") && !has(p, "/meditate")
                    ? "PASS" : "FAIL"));
        }
        // 4.7.80: Goals is the weekly goals board; search lives on More.
        for (GuiOpenPacket p : hubClick("hub goals tab", "hub", "tab:goals", "main")) {
            note("GOALS tab is the weekly goals board (3 goals, progress, Go to buttons, no rewards): " + pf("goals".equals(p.pageId())
                    && has(p, "Weekly goals") && has(p, "Resets in") && !has(p, "No goals yet")
                    && labels(p).split("\\[Go to ", -1).length - 1 == 3 && !has(p, "Reward")) + " | " + labels(p));
        }
        for (GuiOpenPacket p : hubClick("hub more", "hub", "tab:more", "main")) {
            note("MORE: meditation, Menu skins" + (staff ? ", Staff Admin" : "") + ": " + ("more".equals(p.pageId())
                    && has(p, "Meditation") && has(p, "Menu skins") && (has(p, "Staff Admin") == staff)
                    ? "PASS" : "FAIL"));
        }
        // Every hub link opens the right LM screen.
        String[][] links = {
            {"open:prestige", "prestige|main"}, {"open:difficulty", "difficulty|main"}, {"open:spar", "spar|main"},
            {"open:rival", "rival|home"}, {"page:wars", "dojo|dojo_wars"}, {"open:character", "character|main"},
            {"page:skins", "hub|skins"}, {"tab:home", "hub|main"},
        };
        for (String[] l : links) {
            String at = where(hubClick("hub link " + l[0], "hub", l[0], "main"));
            note("LINK " + l[0] + " -> " + at + (at.equals(l[1]) ? " PASS" : " FAIL (want " + l[1] + ")"));
        }
        for (GuiOpenPacket p : keep("difficulty home lines", () -> UltraGui.open(player, "difficulty", "main"))) {
            note("DIFFICULTY HOME shows prestige + rival: " + (has(p, "Beyond Difficulty") && has(p, "Prestige:")
                    && has(p, "Rival:") ? "PASS" : "FAIL"));
        }
        for (GuiOpenPacket p : keep("prestige home lines", () -> UltraGui.open(player, "prestige", "main"))) {
            note("PRESTIGE HOME shows Fastest now: " + (has(p, "Fastest now:") ? "PASS" : "FAIL"));
        }
        // Streak line only with a streak.
        var bond = com.dbzlegacy.adaptivedifficulty.sparring.SparStore.get().bond(player.m_20148_());
        int oldCur = bond.streakCurrent;
        long oldDay = bond.streakLastDay;
        bond.streakCurrent = 0;
        for (GuiOpenPacket p : keep("hub no streak", () -> UltraGui.open(player, "hub", "main"))) {
            note("STREAK hidden without a streak: " + (!has(p, "Your spar streak") ? "PASS" : "FAIL"));
        }
        bond.streakCurrent = 4;
        bond.streakLastDay = System.currentTimeMillis() / 86_400_000L;
        for (GuiOpenPacket p : keep("hub streak", () -> UltraGui.open(player, "hub", "main"))) {
            note("STREAK shown with a streak: " + (has(p, "Your spar streak: 4 days") ? "PASS" : "FAIL"));
        }
        bond.streakCurrent = oldCur;
        bond.streakLastDay = oldDay;

        // 4.7.68: the Spar Board, dojos and wars end to end (dojoScript), replacing the 4.7.64 list/ask board.
        dojoScript();
        navScript();
        formFigureScript();
        guideNavScript();
        hudScript();
        hud75Script();
        v78Script();
        v80Script();
    }

    // ---- 4.7.71 navigation audit and scripted checks (test only) ----
    /** Registry pages a fresh probe account cannot reach (the page needs state the crawl does not make). */
    static final Map<String, String> NAV_STATEFUL = Map.ofEntries(
            Map.entry("spar|result", "needs a finished spar"),
            Map.entry("dojo|found", "needs an account with no dojo (founding starts on Dojo Home)"),
            Map.entry("dojo|pending", "needs a bond invite"),
            Map.entry("rival|requests", "needs a rival request"),
            Map.entry("prestige|confirm", "needs enough prestige to turn in"),
            Map.entry("rival|pick_replace_mutual", "needs a full rival list"),
            Map.entry("hub|skin_custom", "needs the Custom menu skin picked"),
            Map.entry("prestige|custom", "needs the custom shop turned on with perks"),
            Map.entry("skillcheck|main", "needs the Skill Check permission (staff without it get Skills)"));

    private List<GuiOpenPacket> step(String[] st) {
        if ("open".equals(st[0])) {
            return UltraScreenSpec.captureSends(() -> UltraGui.open(player, st[1], st[2]));
        }
        JsonObject args = new JsonObject();
        args.addProperty("screen", st[1]);
        args.addProperty("page", st[3]);
        return UltraScreenSpec.captureSends(() -> UltraGuiActions.dispatch(player, st[1], st[2], args.toString()));
    }

    private static String entryKey(PageRegistry.Page e) {
        return e.screen() + "|" + e.pattern();
    }

    /**
     * Part 3E: the page registry against the real menus. From the main menu only, follow every navigation button
     * (page:, open:, tab:, nav:, go:; never do:) - each registry entry expanded on up to two concrete pages - then:
     * every searchable registry page is reached, every page reached has an entry (no orphans), Back from each page
     * opens its registry parent, and a search for each title finds it. NAV lines; any violation is a FAIL.
     */
    private void navAudit() {
        java.util.UUID u = player.m_20148_();
        boolean staff = com.dbzlegacy.adaptivedifficulty.util.StaffAccess.isStaff(player);
        Map<String, String> reached = new LinkedHashMap<>();
        Map<String, Integer> perEntry = new HashMap<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.Set<String> tried = new java.util.HashSet<>();
        Map<String, String> orphans = new LinkedHashMap<>();
        Deque<String[]> queue = new ArrayDeque<>();
        queue.add(new String[] {"open", "hub", "main", "main"});
        int steps = 0;
        while (!queue.isEmpty() && steps < 6000) {
            String[] st = queue.poll();
            steps++;
            List<GuiOpenPacket> got;
            try {
                got = step(st);
            } catch (Throwable t) {
                note("NAV step failed " + String.join(" ", st) + " " + t);
                continue;
            }
            for (GuiOpenPacket p : got) {
                String concrete = p.screenId() + "|" + p.pageId();
                PageRegistry.Page e = PageRegistry.find(p.screenId(), p.pageId());
                if (e == null) {
                    orphans.putIfAbsent(concrete, st[1] + "|" + st[3] + " " + st[2]);
                    continue;
                }
                reached.putIfAbsent(entryKey(e), concrete);
                if (!seen.add(concrete) || perEntry.merge(entryKey(e), 1, Integer::sum) > 2) {
                    continue;
                }
                try {
                    for (JsonElement el : JsonParser.parseString(p.buttonsJson()).getAsJsonArray()) {
                        String id = el.getAsJsonObject().get("id").getAsString();
                        String sem = semantic(id);
                        if (navigation(sem) && !"nav:none".equals(sem) && tried.add(concrete + " " + id)) {
                            queue.add(new String[] {"act", p.screenId(), id, p.pageId()});
                        }
                    }
                } catch (Throwable t) {
                    note("NAV bad buttons on " + concrete);
                }
            }
        }
        int fails = 0;
        int want = 0;
        StringBuilder out = new StringBuilder();
        for (PageRegistry.Page e : PageRegistry.all()) {
            if (!e.search() || (e.staff() && !staff) || e.pattern().contains("*")) {
                continue;
            }
            want++;
            if (!reached.containsKey(entryKey(e))) {
                String why = NAV_STATEFUL.get(entryKey(e));
                if (why != null) {
                    out.append("NAV SKIP not reached ").append(entryKey(e)).append(" (").append(why).append(")\n");
                } else {
                    fails++;
                    out.append("NAV FAIL not reachable from the main menu: ").append(entryKey(e)).append('\n');
                }
            }
        }
        for (Map.Entry<String, String> o : orphans.entrySet()) {
            fails++;
            out.append("NAV FAIL orphan page (no registry entry): ").append(o.getKey()).append(" via ").append(o.getValue())
                    .append('\n');
        }
        // Back: open each page fresh (no history), press Back, land on the registry parent.
        int backs = 0;
        int noBack = 0;
        for (String concrete : reached.values()) {
            int bar = concrete.indexOf('|');
            String sc = concrete.substring(0, bar);
            String pg = concrete.substring(bar + 1);
            if (PageRegistry.HUB.equals(sc) && PageRegistry.HUB_MAIN.equals(pg)) {
                continue;
            }
            NavHistory.clear(u);
            List<GuiOpenPacket> page;
            try {
                page = step(new String[] {"act", sc, "go:" + concrete, pg});
            } catch (Throwable t) {
                page = List.of();
            }
            GuiOpenPacket shown = page.isEmpty() ? null : page.get(page.size() - 1);
            if (shown == null || !shown.buttonsJson().contains("nav:back\"")) {
                noBack++;
                out.append("NAV INFO no Back button: ").append(concrete).append('\n');
                continue;
            }
            String showPage = shown.pageId();
            String[] parent = PageRegistry.parent(shown.screenId(), showPage);
            String landed;
            try {
                landed = where(step(new String[] {"act", shown.screenId(), "nav:back", showPage}));
            } catch (Throwable t) {
                landed = "error " + t;
            }
            backs++;
            boolean ok = false;
            if (parent != null && !landed.startsWith("(") && !landed.startsWith("error")) {
                int lb = landed.indexOf('|');
                PageRegistry.Page want1 = PageRegistry.find(parent[0], parent[1]);
                PageRegistry.Page got1 = PageRegistry.find(landed.substring(0, lb), landed.substring(lb + 1));
                ok = landed.equals(parent[0] + "|" + parent[1])
                        || (want1 != null && got1 != null && entryKey(want1).equals(entryKey(got1)));
            }
            if (!ok) {
                fails++;
                out.append("NAV FAIL Back from ").append(shown.screenId()).append('|').append(showPage).append(" -> ")
                        .append(landed).append(" (registry parent ")
                        .append(parent == null ? "none" : parent[0] + "|" + parent[1]).append(")\n");
            }
        }
        NavHistory.clear(u);
        // Search: each searchable title finds its page.
        int misses = 0;
        for (PageRegistry.Page e : PageRegistry.all()) {
            if (!e.search() || (e.staff() && !staff)) {
                continue;
            }
            boolean found = false;
            for (PageRegistry.Hit h : PageRegistry.search(e.title(), staff, 500)) {
                found |= h.page() == e;
            }
            if (!found) {
                misses++;
                fails++;
                out.append("NAV FAIL search '").append(e.title()).append("' misses ").append(entryKey(e)).append('\n');
            }
        }
        String summary = "NAV AUDIT reached " + reached.size() + " registry entries (" + want + " searchable pages), "
                + orphans.size() + " orphans, " + backs + " Backs checked (" + noBack + " pages without Back), "
                + misses + " search misses, " + steps + " steps: " + (fails == 0 ? "PASS" : "FAIL " + fails);
        out.append(summary).append('\n');
        write("uiprobe-nav.txt", out.toString(), false);
        for (String line : out.toString().split("\n")) {
            if (line.startsWith("NAV FAIL") || line.startsWith("NAV SKIP") || line.startsWith("NAV AUDIT")) {
                note(line);
            }
        }
    }

    private static JsonArray crumbs(GuiOpenPacket p) {
        try {
            JsonObject l = JsonParser.parseString(p.layoutJson()).getAsJsonObject();
            return l.has("breadcrumb") ? l.getAsJsonArray("breadcrumb") : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String crumbText(JsonArray c) {
        if (c == null) {
            return "(none)";
        }
        StringBuilder b = new StringBuilder();
        for (JsonElement el : c) {
            b.append(b.length() == 0 ? "" : " > ").append(el.getAsJsonArray().get(0).getAsString());
        }
        return b.toString();
    }

    /** Part 3 real-client checks: search a deep page, Back to search, breadcrumbs on deep pages, recents. */
    private void navScript() {
        java.util.UUID u = player.m_20148_();
        NavHistory.clear(u);
        UltraHubScreens.searchFor(player, "");
        keep("nav hub more", () -> UltraGui.open(player, "hub", "more"));
        JsonObject args = new JsonObject();
        args.addProperty("screen", "hub");
        args.addProperty("page", "more");
        args.addProperty("value", "upgrades");
        for (GuiOpenPacket p : keep("nav search upgrades",
                () -> UltraGuiActions.dispatch(player, "hub", "do:hub_search", args.toString()))) {
            note("SEARCH 'upgrades' on More lists Dojo upgrades with its path: " + ("more".equals(p.pageId())
                    && p.buttonsJson().contains("go:dojo|dojo_upgrades") && labels(p).contains("Upgrades  -  Dojo") ? "PASS" : "FAIL")
                    + " | " + labels(p));
        }
        // A tab page's footer goes to its system Home by design; a deeper result (an upgrade) goes back along history.
        for (GuiOpenPacket p : hubClick("nav search result", "hub", "go:dojo|dojo_up:arena", "more")) {
            note("SEARCH result opens the page: " + (p.screenId() + "|" + p.pageId()) + " Back reads "
                    + (has(p, "Back to More") ? "Back to More PASS" : "FAIL") + " | crumbs " + crumbText(crumbs(p)));
        }
        for (GuiOpenPacket p : hubClick("nav back to search", "dojo", "nav:back", "dojo_up:arena")) {
            note("BACK returns to More with the search results: " + ("hub".equals(p.screenId()) && "more".equals(p.pageId())
                    && p.buttonsJson().contains("go:dojo|dojo_upgrades") ? "PASS" : "FAIL"));
        }
        // Breadcrumbs on three deep pages, one a dojo upgrade; each crumb but the last is a link.
        String[][] deep = {{"dojo", "dojo_up:arena", "Main menu > Dojo > Upgrades > Sparring arenas"},
                {"prestige", "mastery_lines", "Main menu > Prestige > Shop > Mastery wallet > Forms"},
                {"rival", "records:stats", "Main menu > Rivals > Records > Record stats"}};
        for (String[] d : deep) {
            NavHistory.clear(u);
            for (GuiOpenPacket p : keep("nav crumbs " + d[1], () -> UltraGui.open(player, d[0], d[1]))) {
                JsonArray c = crumbs(p);
                boolean links = c != null && c.size() >= 3;
                for (int i = 0; links && i < c.size(); i++) {
                    String a = semantic(c.get(i).getAsJsonArray().get(1).getAsString());
                    links = i == c.size() - 1 ? a.isEmpty() : (a.startsWith("go:") || a.equals("nav:hub"));
                }
                note("CRUMBS " + d[0] + "|" + d[1] + ": " + crumbText(c) + (crumbText(c).equals(d[2]) && links
                        ? " PASS" : " FAIL (want " + d[2] + ")"));
                if (c != null && c.size() >= 2) {
                    String a = c.get(c.size() - 2).getAsJsonArray().get(1).getAsString();
                    note("CRUMB click " + a + " -> " + where(hubClick("nav crumb click " + d[1], d[0], a, d[1])));
                }
            }
        }
        // Recents: newest first, at most five, a tap searches again.
        for (String q : new String[] {"skin", "rival", "dojo", "weights", "titles", "prestige"}) {
            UltraHubScreens.searchFor(player, q);
        }
        java.util.List<String> recent = UltraHubScreens.recentSearches(player);
        note("RECENTS keep five, newest first: " + recent + (recent.size() == 5 && "prestige".equals(recent.get(0))
                && !recent.contains("skin") ? " PASS" : " FAIL"));
        UltraHubScreens.searchFor(player, "");
        for (GuiOpenPacket p : hubClick("nav search recents", "hub", "tab:more", "main")) {
            note("RECENTS shown under an empty search: " + (p.buttonsJson().contains("do:hub_search_recent:0")
                    && has(p, "Recent: prestige") ? "PASS" : "FAIL"));
        }
        for (GuiOpenPacket p : hubClick("nav search recent tap", "hub", "do:hub_search_recent:2", "more")) {
            note("RECENT tap searches again: " + (labels(p).contains("Results for 'weights'") ? "PASS" : "FAIL") + " | " + labels(p));
        }
        UltraHubScreens.searchFor(player, "");
        NavHistory.clear(u);
    }

    /** 4.7.73: Difficulty search results, breadcrumbs and Back never send a player into a guide step by mistake. */
    private void guideNavScript() {
        JsonObject args = new JsonObject();
        args.addProperty("screen", "hub");
        args.addProperty("page", "main");
        args.addProperty("value", "tiers");
        for (GuiOpenPacket p : keep("search tiers", () -> UltraGuiActions.dispatch(player, "hub", "do:hub_search", args.toString()))) {
            String b = p.buttonsJson();
            note("GUIDE search 'tiers' links the Tiers list, not a guide step: " + pf(b.contains("go:difficulty|tiers\"")
                    && !b.contains("go:difficulty|guide_tiers")) + " | " + labels(p));
        }
        String[] up = PageRegistry.parent("difficulty", "titles_p0");
        String[] up2 = PageRegistry.parent("difficulty", "tier_ask:3");
        note("GUIDE Titles and a tier ask go back to Difficulty / Tiers, never a guide step: " + pf(up != null
                && "main".equals(up[1]) && up2 != null && "tiers".equals(up2[1])));
        UltraHubScreens.searchFor(player, "");
        // 4.7.73: dojo style and bonus changes ask first, naming what is lost and what it costs.
        var style = com.dbzlegacy.adaptivedifficulty.dojo.DojoCatalog.Style.values()[0];
        var bonus = com.dbzlegacy.adaptivedifficulty.dojo.DojoCatalog.bonuses(style).get(0);
        for (GuiOpenPacket p : keep("dojo bonus ask", () -> UltraGui.open(player, "spar", "dojo_bonus_ask:" + bonus.id()))) {
            note("ASK dojo bonus names the cost and the 7-day lock before the click: " + pf(has(p, "Dojo Points")
                    && has(p, "change for 7 days") && (labels(p).contains("Buy for 40 points")
                    || labels(p).contains("Needs 40 points") || has(p, "Only your dojo's master"))) + " | " + labels(p));
        }
        for (GuiOpenPacket p : keep("dojo style ask", () -> UltraGui.open(player, "spar", "dojo_style_ask:" + style.name()))) {
            note("ASK dojo style change warns before ending a bonus: " + pf(labels(p).contains("Change style, end the bonus")
                    || has(p, "Only your dojo's master")) + " | " + labels(p));
        }
        // 4.7.77: the dojo leaderboard (clout and war wins); the old Top dojos and Hall of Chosen links open it.
        for (GuiOpenPacket p : keep("dojo leaderboard wars", () -> UltraGui.open(player, "spar", "dojo_list_wars"))) {
            note("LEADERBOARD dojo leaderboard by war wins, with a By clout switch: " + pf(has(p, "Dojo leaderboard (war wins)")
                    && labels(p).contains("By clout")) + " | " + labels(p));
        }
        for (GuiOpenPacket p : keep("old top dojos", () -> UltraGui.open(player, "spar", "dojo_top_rp"))) {
            note("LEADERBOARD old Top dojos link opens the live leaderboard: " + pf(p.pageId().startsWith("dojo_list")));
        }
        for (GuiOpenPacket p : keep("old hall of chosen", () -> UltraGui.open(player, "spar", "dojo_hof"))) {
            note("LEADERBOARD old Hall of Chosen link opens the live leaderboard: " + pf(p.pageId().startsWith("dojo_list")));
        }
        for (GuiOpenPacket p : keep("wars tab leaderboard", () -> UltraGui.open(player, "spar", "dojo_wars"))) {
            note("LEADERBOARD Wars tab links the dojo leaderboard: " + pf(labels(p).contains("Dojo leaderboard")
                    || has(p, "Join or found a dojo")));
        }
        String[] upAsk = PageRegistry.parent("dojo", "dojo_bonus_ask:" + bonus.id());
        note("ASK pages go back to Style and bonus: " + pf(upAsk != null && "dojo_style".equals(upAsk[1])));
    }

    /** Part 1: a form preview wears the viewer's own look for that form (made-up subject, never local). */
    private void formFigureScript() {
        String me = player.m_20148_().toString();
        int n = 0;
        for (String pg : new String[] {"form_preview:0", "form_preview:1"}) {
            for (GuiOpenPacket p : keep("form figure " + pg, () -> UltraGui.open(player, "character", pg))) {
                String l = p.layoutJson();
                n++;
                note("FORM FIGURE " + pg + " carries colors/appearance/headBones/skinOwner, made-up subject: "
                        + (l.contains("\"colors\"") && l.contains("\"appearance\"") && l.contains("\"skinOwner\":\"" + me)
                        && l.contains("\"subject\":\"uuid:") && !l.contains("\"subject\":\"local\"")
                        ? "PASS" : "FAIL") + " headBones=" + l.contains("\"headBones\""));
                note("FORM PREVIEW " + pg + " says it wears your own look: "
                        + (has(p, "The model wears your own colors") ? "PASS" : "FAIL"));
                if (pg.endsWith(":1")) {
                    note("FORM PREVIEW paging does not stack Back (no Back to Preview forms): "
                            + (!has(p, "Back to Preview forms") ? "PASS" : "FAIL") + " | " + labels(p));
                }
            }
        }
        if (n == 0) {
            note("FORM FIGURE no form preview page FAIL");
        }
    }

    // ---- 4.7.68 dojos, Spar Board, wars (test only) ----
    private ServerPlayer dMate;
    private ServerPlayer dMate2;
    private ServerPlayer dMate3;

    private ServerPlayer dojoStandIn(String name) {
        com.mojang.authlib.GameProfile gp = new com.mojang.authlib.GameProfile(
                java.util.UUID.nameUUIDFromBytes(("uiprobe:" + name).getBytes(StandardCharsets.UTF_8)), name);
        ServerPlayer p = net.minecraftforge.common.util.FakePlayerFactory.get(player.m_284548_(), gp);
        p.m_6034_(player.m_20185_() + 3, player.m_20186_(), player.m_20189_());
        backing().add(p);
        byUuid().put(p.m_20148_(), p);
        return p;
    }

    private static com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.Dojo dojoOf(ServerPlayer p) {
        return com.dbzlegacy.adaptivedifficulty.dojo.DojoService.dojoOf(p);
    }

    private static Object call(Class<?> c, String m, Class<?>[] types, Object... args) throws Exception {
        java.lang.reflect.Method mm = c.getDeclaredMethod(m, types);
        mm.setAccessible(true);
        return mm.invoke(null, args);
    }

    /** End a board bout the way the board does (KO, RING_OUT, FORFEIT, TIMEOUT). */
    private void endBout(ServerPlayer someone, java.util.UUID winner, String how) throws Exception {
        var b = com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.boutOf(someone.m_20148_());
        if (b == null) {
            note("DOJO end bout: no bout for " + someone.m_7755_().getString() + " FAIL");
            return;
        }
        call(com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.class, "end",
                new Class<?>[] {com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.Bout.class, java.util.UUID.class,
                        String.class}, b, winner, how);
    }

    private void draft(ServerPlayer p, boolean ranked, int wager) {
        com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.setDraft(p.m_20148_(), ranked, wager);
    }

    private void dojoScript() {
        var server = player.m_20194_();
        var store = com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.get();
        String me = player.m_20148_().toString();
        double hx = player.m_20185_();
        double hy = player.m_20186_();
        double hz = player.m_20189_();
        String homeDim = player.m_9236_().m_46472_().m_135782_().toString();
        player.m_150110_().f_35934_ = true;
        try {
            dMate = dojoStandIn("Sparmate");
            dMate2 = dojoStandIn("Sparmate2");
            dMate3 = dojoStandIn("Sparmate3");
            // ---- no dojo, no spar
            store.dojos.remove(me);
            for (GuiOpenPacket p : keep("dojo board no dojo", () -> UltraGui.open(player, "spar", "sparboard"))) {
                note("DOJO NO DOJO: board says join a dojo, with Find a dojo, Open the Dojo (no founding here) and a dim Post: " + pf(
                        has(p, "Join a dojo first. No dojo, no spar.") && has(p, "Find a dojo")
                                && !has(p, "Found a dojo") && labels(p).contains("[Open the Dojo]")
                                && p.buttonsJson().contains("go:dojo|home") && !p.buttonsJson().contains("dojo_found")
                                && p.buttonsJson().contains("nav:none")) + " | " + labels(p));
            }
            for (GuiOpenPacket p : keep("dojo tabs", () -> UltraGui.open(player, "spar", "main"))) {
                note("SPAR TABS Home, Leaderboard, Spar Board; Home does not advertise the board: " + pf(has(p, "Spar Board")
                        && p.buttonsJson().contains("tab:sparboard") && !p.buttonsJson().contains("tab:wars")
                        && has(p, "Leaderboard") && !has(p, "Open the Spar Board")));
            }
            // 4.7.78: founding is a flow (name, style, banner), tested in v78Script; here the dojo is made directly.
            com.dbzlegacy.adaptivedifficulty.dojo.DojoService.found(player);
            note("DOJO FOUND alone (free), one ring: " + pf(dojoOf(player) != null
                    && com.dbzlegacy.adaptivedifficulty.dojo.DojoService.arenas(dojoOf(player)) == 1));
            com.dbzlegacy.adaptivedifficulty.dojo.DojoService.found(dMate);
            com.dbzlegacy.adaptivedifficulty.dojo.DojoService.found(dMate3);
            boolean dim = com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.level(server) != null;
            note("DOJO WORLD the dojo dimension is loaded from the LM datapack: " + pf(dim));
            if (dim) {
                // 4.7.68 Phase 2: founding places the hall from the toolkit's structures (finished now for the checks).
                com.dbzlegacy.adaptivedifficulty.dojo.DojoPlacer.finishNow();
                var fresh = dojoOf(player);
                note("DOJO PLOT built from the structures, checks OK: " + pf(com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.ready(fresh)
                        && "OK".equals(com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.LAST_CHECK.get(fresh.masterUuid))) + " " + com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.LAST_CHECK.get(fresh.masterUuid));
                note("DOJO PLOT a new dojo is the empty hall with one sparring mat: " + pf(ringBuilt(fresh, 0)
                        && !ringBuilt(fresh, 1) && !ringBuilt(fresh, 5)));
            }
            // mate2 joins the probe player's dojo as a disciple (a bond, as the mentor accept makes it).
            var mb = com.dbzlegacy.adaptivedifficulty.sparring.SparStore.get().bond(player.m_20148_());
            mb.addApprentice(dMate2.m_20148_().toString(), "Sparmate2");
            var ab = com.dbzlegacy.adaptivedifficulty.sparring.SparStore.get().bond(dMate2.m_20148_());
            ab.mentorUuid = me;
            ab.mentorName = player.m_7755_().getString();
            dojoOf(player).joinedAt.put(dMate2.m_20148_().toString(), 0L);
            note("DOJO MEMBER a disciple belongs to the master's dojo: " + pf(dojoOf(dMate2) == dojoOf(player)));

            // ---- ranked gate
            store.player(me, "").unrankedSpars = 0;
            store.player(dMate.m_20148_().toString(), "Sparmate").unrankedSpars = 0;
            for (GuiOpenPacket p : keep("dojo board gate", () -> UltraGui.open(player, "spar", "sparboard"))) {
                note("DOJO RANKED GATE: 0 of 30 shown and ranked dim: " + pf(has(p, "0 of 30 friendly spars done")
                        && has(p, "Ranked: locked")));
            }
            for (GuiOpenPacket p : hubClick("dojo ranked locked", "spar", "do:board_mode", "sparboard")) {
                note("DOJO RANKED GATE: switching to ranked before 30 says how many are left: "
                        + pf(has(p, "Ranked unlocks after 30 friendly spars")));
            }

            // ---- plain PvP is not a spar
            var src = player.m_269291_().m_269075_(dMate);
            com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.onPlayerHurt(player, dMate, src);
            com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.onPlayerHurt(dMate,
                    player, dMate.m_269291_().m_269075_(player));
            note("DOJO PLAIN PVP: trading hits starts no spar: " + pf(
                    !com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.isSparring(player.m_20148_())));

            if (!dim) {
                note("DOJO the dojo world is missing, so arena checks are skipped FAIL");
                return;
            }
            // ---- a friendly board spar: post, accept, teleport, fight, exact return
            draft(player, false, 0);
            for (GuiOpenPacket p : hubClick("dojo post", "spar", "do:board_post", "sparboard")) {
                note("DOJO POST friendly for 15 minutes: " + pf(has(p, "Posted a friendly spar")));
            }
            for (GuiOpenPacket p : keep("dojo board mate view", () -> UltraGui.open(dMate, "spar", "sparboard"))) {
                note("DOJO LISTING shows name, level, Friendly Fist, kind, wait and wager: " + pf(
                        has(p, player.m_7755_().getString()) && has(p, " Lv ") && has(p, "Friendly Fist")
                                && has(p, "friendly") && has(p, "waiting") && has(p, "no wager")
                                && has(p, "Accept " + player.m_7755_().getString())) + " | " + labels(p));
            }
            com.dbzlegacy.adaptivedifficulty.dojo.DojoPlacer.finishNow();
            String r = com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(dMate, "board_accept", me);
            var bout = com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.boutOf(player.m_20148_());
            var host = dojoOf(player);
            var o = com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.plotOrigin(host.plot);
            double[] c0 = com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.ringCenter(host, 0);
            double dist = Math.hypot(player.m_20185_() - c0[0], player.m_20189_() - c0[1]);
            note("DOJO ACCEPT teleports both into the poster's arena and starts the spar: " + r + " "
                    + pf(bout != null && com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.inDojoWorld(player) && dist < 10
                    && com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.isSparring(player.m_20148_())));
            for (GuiOpenPacket p : keep("dojo board in bout", () -> UltraGui.open(player, "spar", "sparboard"))) {
                note("DOJO IN BOUT: board shows the opponent and a forfeit that warns about refunds: "
                        + pf(has(p, "Forfeit this spar") && has(p, "refunded")));
            }
            int unranked0 = store.player(me, "").unrankedSpars;
            endBout(player, dMate.m_20148_(), "FORFEIT");
            boolean back = !com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.inDojoWorld(player)
                    && Math.abs(player.m_20185_() - hx) < 0.6 && Math.abs(player.m_20189_() - hz) < 0.6
                    && Math.abs(player.m_20186_() - hy) < 0.6
                    && player.m_9236_().m_46472_().m_135782_().toString().equals(homeDim);
            note("DOJO RETURN both go back to exactly where they stood: " + pf(back) + " at " + (int) player.m_20185_()
                    + "," + (int) player.m_20186_() + "," + (int) player.m_20189_());
            note("DOJO ANTI-FARM a forfeit right away doesn't count: " + pf(store.player(me, "").unrankedSpars == unranked0));
            for (GuiOpenPacket p : keep("dojo result", () -> UltraGui.open(player, "spar", "result"))) {
                note("DOJO RESULT screen with a rematch: " + pf(has(p, "Rematch Sparmate") && has(p, "forfeit")));
            }
            // A counted friendly spar: a minute, both landing damage.
            draft(player, false, 0);
            com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(player, "board_post", "");
            com.dbzlegacy.adaptivedifficulty.dojo.DojoPlacer.finishNow();
            com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(dMate, "board_accept", me);
            bout = com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.boutOf(player.m_20148_());
            int pts0 = host.points;
            if (bout != null) {
                bout.startAt -= 61_000L;
                // The leaderboard skips spars under 30 s: age the sessions too.
                com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.runtime(player.m_20148_()).startAt -= 61_000L;
                com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.runtime(dMate.m_20148_()).startAt -= 61_000L;
                com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.runtime(player.m_20148_()).sessionDmg = 40;
                com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.runtime(dMate.m_20148_()).sessionDmg = 30;
                endBout(player, player.m_20148_(), "KO");
            }
            note("DOJO COUNTED friendly spar: both +1 toward ranked, the host dojo +2 points: " + pf(
                    store.player(me, "").unrankedSpars == unranked0 + 1 && host.points == pts0 + 2));
            note("DOJO FRIENDLY pays no spar TP: " + pf(com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.result(
                    player.m_20148_()).lines().toString().contains("friendly spars pay nothing")));

            // ---- wagers: coins (KO pays the winner both) and points (forfeit refunds both)
            store.player(me, "").unrankedSpars = 30;
            store.player(dMate.m_20148_().toString(), "Sparmate").unrankedSpars = 30;
            com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.grantExact(player,
                    com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.CoinKind.NETHERITE, 5);
            long strip = com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.balance(dMate);
            if (strip > 0L) {
                com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.charge(dMate, strip);
            }
            // 4.7.80: the wager is set from the sub-menu (kind, then amount), not a cycling button.
            draft(player, true, 0);
            hubClick("dojo wager set", "spar", "do:board_wager_set:"
                    + com.dbzlegacy.adaptivedifficulty.dojo.DojoCatalog.COIN_STEPS.length, "board_wager_coins");
            note("DOJO WAGER set from the Wager sub-menu (5 netherite coins): " + pf(com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar
                    .draft(player.m_20148_()).wager() == com.dbzlegacy.adaptivedifficulty.dojo.DojoCatalog.COIN_STEPS.length));
            String posted = com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(player, "board_post", "");
            com.dbzlegacy.adaptivedifficulty.dojo.DojoPlacer.finishNow();
            var wpost = com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.postOf(player.m_20148_());
            String cant = wpost == null ? null : com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.whyNot(dMate, wpost);
            String refused = com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(dMate, "board_accept", me);
            note("DOJO WAGER an accepter who can't stake the same kind and amount can't accept: " + pf(cant != null
                    && cant.contains("needs") && com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.boutOf(player.m_20148_()) == null)
                    + " | " + cant + " / " + refused);
            com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.grantExact(dMate,
                    com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.CoinKind.NETHERITE, 5);
            long coinsP = com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.balance(player);
            long coinsM = com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.balance(dMate);
            String acc = com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(dMate, "board_accept", me);
            long heldP = com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.balance(player);
            long heldM = com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.balance(dMate);
            note("DOJO WAGER 5 netherite coins: both stakes held at accept: " + posted + " / " + acc + " "
                    + pf(coinsP - heldP == 500_000L && coinsM - heldM == 500_000L));
            bout = com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.boutOf(player.m_20148_());
            if (bout != null) {
                bout.startAt -= 61_000L;
                // The leaderboard skips spars under 30 s: age the sessions too.
                com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.runtime(player.m_20148_()).startAt -= 61_000L;
                com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.runtime(dMate.m_20148_()).startAt -= 61_000L;
                com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.runtime(player.m_20148_()).sessionDmg = 40;
                com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.runtime(dMate.m_20148_()).sessionDmg = 30;
            }
            var lb = com.dbzlegacy.adaptivedifficulty.sparring.SparStore.get().leaderboard.get(me);
            int sessions0 = lb == null ? 0 : lb.sessions;
            int ranked0 = store.player(me, "").rankedSpars;
            endBout(player, player.m_20148_(), "KO");
            long afterP = com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.balance(player);
            note("DOJO WAGER KO: the winner takes both stakes: " + pf(afterP - heldP == 1_000_000L
                    && com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.balance(dMate) == heldM));
            lb = com.dbzlegacy.adaptivedifficulty.sparring.SparStore.get().leaderboard.get(me);
            note("DOJO RANKED counted: ranked spars +1 and the leaderboard sees it: " + pf(
                    store.player(me, "").rankedSpars == ranked0 + 1 && lb != null && lb.sessions == sessions0 + 1));
            // Same pair again at once: no ranked credit (15-minute pair cooldown).
            draft(player, true, 0);
            com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(player, "board_post", "");
            com.dbzlegacy.adaptivedifficulty.dojo.DojoPlacer.finishNow();
            com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(dMate, "board_accept", me);
            bout = com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.boutOf(player.m_20148_());
            if (bout != null) {
                bout.startAt -= 61_000L;
                // The leaderboard skips spars under 30 s: age the sessions too.
                com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.runtime(player.m_20148_()).startAt -= 61_000L;
                com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.runtime(dMate.m_20148_()).startAt -= 61_000L;
                com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.runtime(player.m_20148_()).sessionDmg = 40;
                com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.runtime(dMate.m_20148_()).sessionDmg = 30;
                endBout(player, player.m_20148_(), "KO");
            }
            note("DOJO ANTI-FARM same pair right away: no ranked credit, and the result says why: " + pf(
                    store.player(me, "").rankedSpars == ranked0 + 1 && com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar
                            .result(player.m_20148_()).lines().toString().contains("last 15 minutes")));
            // Points wager, refunded on a forfeit.
            dojoOf(player).points = 20;
            dojoOf(dMate).points = 20;
            draft(player, false, com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.wagerSteps() - 1);
            com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(player, "board_post", "");
            com.dbzlegacy.adaptivedifficulty.dojo.DojoPlacer.finishNow();
            com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(dMate, "board_accept", me);
            note("DOJO WAGER 20 Dojo Points: both dojos' stakes held: " + pf(dojoOf(player).points == 0
                    && dojoOf(dMate).points == 0));
            endBout(dMate, player.m_20148_(), "FORFEIT");
            note("DOJO WAGER forfeit refunds both: " + pf(dojoOf(player).points == 20 && dojoOf(dMate).points == 20));

            // ---- upgrades, two spars at once, the map preview
            host = dojoOf(player);
            host.points = 2000;
            hubClick("dojo buy arena", "spar", "do:dojo_buy:arena", "dojo_up:arena");
            note("DOJO UPGRADE a second ring: " + pf(com.dbzlegacy.adaptivedifficulty.dojo.DojoService.arenas(host) == 2));
            boolean queued = com.dbzlegacy.adaptivedifficulty.dojo.DojoPlacer.busy(host.masterUuid);
            com.dbzlegacy.adaptivedifficulty.dojo.DojoPlacer.finishNow();
            var lvl = com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.level(server);
            double[] c1 = com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.ringCenter(host, 1);
            note("DOJO BUILD the purchase builds ring 2 into the hall (only that part rebuilt): " + pf(queued
                    && ringBuilt(host, 1) && ringBuilt(host, 0) && !ringBuilt(host, 2)
                    && "OK".equals(com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.LAST_CHECK.get(host.masterUuid))));
            draft(player, false, 0);
            com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(player, "board_post", "");
            com.dbzlegacy.adaptivedifficulty.dojo.DojoPlacer.finishNow();
            com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(dMate, "board_accept", me);
            draft(dMate2, false, 0);
            com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(dMate2, "board_post", "");
            com.dbzlegacy.adaptivedifficulty.dojo.DojoPlacer.finishNow();
            com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(dMate3, "board_accept", dMate2.m_20148_().toString());
            var b1 = com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.boutOf(player.m_20148_());
            var b2 = com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.boutOf(dMate2.m_20148_());
            note("DOJO TWO SPARS at once in one dojo (arenas 1 and 2): " + pf(b1 != null && b2 != null
                    && b1.host.equals(b2.host) && b1.arena != b2.arena));
            // Spectators: no room, no watching; with the room, watch from the gallery.
            String noRoom = com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.spectate(player, host);
            note("DOJO SPECTATE without the room: refused: " + pf(noRoom.contains("has no spectator room")));
            if (b1 != null) {
                endBout(player, player.m_20148_(), "FORFEIT");
            }
            hubClick("dojo buy spectator", "spar", "do:dojo_buy:spectator", "dojo_up:spectator");
            com.dbzlegacy.adaptivedifficulty.dojo.DojoPlacer.finishNow();
            String watch = com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.spectate(player, host);
            double gy = player.m_20186_();
            note("DOJO SPECTATE with the balcony: on the balcony above the rings: " + watch + " "
                    + pf(com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.spectating(player.m_20148_())
                    && gy > com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.FLOOR_Y + 5));
            player.m_150110_().f_35934_ = false;
            float hp0 = player.m_21223_();
            player.f_19802_ = 0;
            player.m_6469_(player.m_269291_().m_269075_(dMate3), 6.0f);
            note("DOJO SPECTATOR can't be hit: " + pf(player.m_21223_() >= hp0));
            player.m_150110_().f_35934_ = true;
            com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.teleport(player, lvl, c1[0],
                    com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.FLOOR_Y + 1, c1[1], 0f);
            com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.pulse(server);
            note("DOJO SPECTATOR can't step into a ring (put back on the balcony): " + pf(player.m_20186_()
                    > com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.FLOOR_Y + 5));
            com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.leave(player);
            if (b2 != null) {
                endBout(dMate2, dMate2.m_20148_(), "FORFEIT");
            }
            for (GuiOpenPacket p : keep("dojo upgrade preview", () -> UltraGui.open(player, "spar", "dojo_up:training_hall"))) {
                // The probe player's client draws pictures: Now / After / Where in the body, the text map under them.
                boolean pics = p.layoutJson().contains("\"pictures\"") && p.layoutJson().contains("cu_training_hall_0_t")
                        && p.layoutJson().contains("cu_training_hall_1_") && p.layoutJson().contains("plan_t");
                note("DOJO PREVIEW Now / After / Where pictures, the text map, cost warning, Visit dojo: " + pf(pics
                        && has(p, "*Training*") && has(p, "Visit dojo") && has(p, "Cost")
                        && has(p, "Points are spent at once")) + " | " + labels(p));
            }
            for (GuiOpenPacket p : keep("dojo upgrade list plan", () -> UltraGui.open(player, "spar", "dojo_upgrades"))) {
                note("DOJO UPGRADES list: LM names and costs, the hall's plan: " + pf(has(p, "Spectator balcony")
                        && has(p, "Sparring arenas") && p.layoutJson().contains("\"pictures\"")) + " | " + labels(p));
            }
            hubClick("dojo buy training", "spar", "do:dojo_buy:training_hall", "dojo_up:training_hall");
            hubClick("dojo buy training 2", "spar", "do:dojo_buy:training_hall", "dojo_up:training_hall");
            for (int i = 0; i < 5; i++) {
                hubClick("dojo buy capacity", "spar", "do:dojo_buy:capacity", "dojo_up:capacity");
            }
            note("DOJO CAPACITY 5 disciples to start, 30 members at the top: " + pf(
                    com.dbzlegacy.adaptivedifficulty.dojo.DojoService.disciplesAllowed(me) == 29
                    && com.dbzlegacy.adaptivedifficulty.dojo.DojoCatalog.disciplesAllowed(0) == 5));
            // ---- styles and the bonus
            hubClick("dojo style", "spar", "do:dojo_style:MELEE", "dojo_style");
            hubClick("dojo bonus", "spar", "do:dojo_bonus:melee:focus", "dojo_style");
            double mel = com.dbzlegacy.adaptivedifficulty.dojo.DojoService.styleBonus(player, "melee");
            double ki = com.dbzlegacy.adaptivedifficulty.dojo.DojoService.styleBonus(player, "ki");
            note("DOJO STYLE Melee with Melee focus: +5% on melee hits, nothing on ki: " + pf(mel == 0.05 && ki == 0.0));
            host.bonusChangedAt = 0L;
            hubClick("dojo bonus race", "spar", "do:dojo_bonus:melee:race:majin", "dojo_style");
            String race = com.dbzlegacy.adaptivedifficulty.calc.DmzProgression.race(player);
            double raceBonus = com.dbzlegacy.adaptivedifficulty.dojo.DojoService.styleBonus(player, "melee");
            note("DOJO RACE bonus applies only to that race (probe is " + race + "): " + pf("majin".equals(race)
                    ? raceBonus == 0.08 : raceBonus == 0.0));
            // ---- Potential Unlock: off inside a spar session, normal in the world, no dojo bonus on it
            for (ServerPlayer w : new ServerPlayer[] {player, dMate}) {
                com.dbzlegacy.adaptivedifficulty.progression.ProgressionData.storedPut(w, "potential_movement_valid_until",
                        System.currentTimeMillis() + 120_000L);
            }
            // Scenario per session: who looks for Potential Unlock, ranked or friendly, and what the probe player
            // (the only fighter with DMZ stats) should gain from one hit it lands. 600 ms apart: the PU duplicate
            // window is 500 ms, so each hit is judged on its own.
            Object[][] scenarios = {
                    {"friendly, nobody opted in", false, "TP", "TP", false},
                    {"friendly, the probe player opted in", false, "POTENTIAL", "TP", true},
                    {"friendly, only the opponent opted in", false, "TP", "POTENTIAL", false},
                    {"ranked, both opted in", true, "POTENTIAL", "POTENTIAL", false}};
            String pu1 = "";
            for (Object[] sc : scenarios) {
                store.player(me, "").unrankedSpars = 30;
                store.player(dMate.m_20148_().toString(), "Sparmate").unrankedSpars = 30;
                com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.setDraft(player.m_20148_(), (Boolean) sc[1], 0);
                while (com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.draft(player.m_20148_()).want().name()
                        .equals(sc[2]) == false) {
                    com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(player, "board_want", "");
                }
                while (com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.draft(dMate.m_20148_()).want().name()
                        .equals(sc[3]) == false) {
                    com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(dMate, "board_want", "");
                }
                com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(player, "board_post", "");
                com.dbzlegacy.adaptivedifficulty.dojo.DojoPlacer.finishNow();
                com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(dMate, "board_accept", me);
                for (ServerPlayer w : new ServerPlayer[] {player, dMate}) {
                    com.dbzlegacy.adaptivedifficulty.progression.ProgressionData.storedPut(w,
                            "potential_movement_valid_until", System.currentTimeMillis() + 120_000L);
                }
                try {
                    Thread.sleep(600L);
                } catch (InterruptedException ignored) {
                }
                String before = String.valueOf(com.dbzlegacy.adaptivedifficulty.gui.ultra.HubDashboard.potentialLine(player));
                boolean inSession = com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.inSessionTogether(player, dMate);
                com.dbzlegacy.adaptivedifficulty.progression.skills.PotentialProgression.onPlayerHurt(dMate, player,
                        player.m_269291_().m_269075_(player));
                String after = String.valueOf(com.dbzlegacy.adaptivedifficulty.gui.ultra.HubDashboard.potentialLine(player));
                boolean gained = !after.equals(before);
                note("PU SESSION " + sc[0] + ": the probe player " + ((Boolean) sc[4] ? "gains" : "gains nothing")
                        + ": " + pf(inSession && gained == (Boolean) sc[4]) + " (" + before + " -> " + after + ")");
                endBout(player, player.m_20148_(), "FORFEIT");
                pu1 = after;
            }
            for (GuiOpenPacket p : keep("dojo board want", () -> UltraGui.open(player, "spar", "sparboard"))) {
                // The explanation wraps after "friendly" on the LM client's line width: match the first line only.
                note("PU WANT the board offers Looking for (TP, Potential Unlock, Anything): " + pf(has(p, "Looking for - ")
                        && has(p, "Looking for Potential Unlock trains it in friendly")));
            }
            try {
                Thread.sleep(600L);
            } catch (InterruptedException ignored) {
            }
            for (ServerPlayer w : new ServerPlayer[] {player, dMate}) {
                com.dbzlegacy.adaptivedifficulty.progression.ProgressionData.storedPut(w, "potential_movement_valid_until",
                        System.currentTimeMillis() + 120_000L);
            }
            com.dbzlegacy.adaptivedifficulty.progression.skills.PotentialProgression.onPlayerHurt(dMate, player,
                    player.m_269291_().m_269075_(player));
            String pu2 = String.valueOf(com.dbzlegacy.adaptivedifficulty.gui.ultra.HubDashboard.potentialLine(player));
            note("PU WORLD outside a session (with the Melee bonus active): the same hit gains the normal points: "
                    + (pu2.equals(pu1) ? "SKIP (no points from a stand-in without DMZ stats)" : pf(!pu2.equals(pu1)))
                    + " (" + pu1 + " -> " + pu2 + ")");

            // ---- elites and war sign-off
            hubClick("dojo promote", "spar", "do:dojo_promote:" + dMate2.m_20148_(), "dojo_elites");
            note("DOJO ELITE the master names an elite: " + pf(host.elites.contains(dMate2.m_20148_().toString())));
            String w1 = com.dbzlegacy.adaptivedifficulty.dojo.DojoWar.declare(dMate2, dojoOf(dMate).masterUuid, "BRACKET");
            var pend = com.dbzlegacy.adaptivedifficulty.dojo.DojoWar.warsOf(host.masterUuid).get(0);
            note("DOJO ELITE war needs the master's sign-off: " + w1 + " " + pf("PENDING_SIGNOFF".equals(pend.status)));
            hubClick("dojo signoff", "spar", "do:war_signoff:" + pend.id, "dojo_wars");
            note("DOJO SIGN-OFF sends the challenge: " + pf("CHALLENGED".equals(pend.status)));
            com.dbzlegacy.adaptivedifficulty.dojo.DojoWar.decline(dMate, pend.id);
            // ---- reopen penalty, season reset
            int arenasBefore = com.dbzlegacy.adaptivedifficulty.dojo.DojoService.arenas(host);
            int trainBefore = com.dbzlegacy.adaptivedifficulty.dojo.DojoService.tier(host, "training_hall");
            hubClick("dojo close", "spar", "do:dojo_close", "dojo");
            note("DOJO CLOSE: members have no dojo while it's closed: " + pf(dojoOf(player) == null && dojoOf(dMate2) == null));
            hubClick("dojo reopen", "spar", "do:dojo_reopen", "dojo");
            note("DOJO REOPEN is the same dojo, every upgrade one tier down: " + pf(dojoOf(player) == host
                    && com.dbzlegacy.adaptivedifficulty.dojo.DojoService.arenas(host) == arenasBefore - 1
                    && com.dbzlegacy.adaptivedifficulty.dojo.DojoService.tier(host, "training_hall") == trainBefore - 1));
            host.clout = 77;
            int arenasNow = com.dbzlegacy.adaptivedifficulty.dojo.DojoService.arenas(host);
            com.dbzlegacy.adaptivedifficulty.sparring.DojoRankings.advanceSeasonNow();
            note("DOJO SEASON reset: clout goes to history and restarts, rooms stay: " + pf(host.clout <= 30
                    && host.cloutHistory.containsValue(77)
                    && com.dbzlegacy.adaptivedifficulty.dojo.DojoService.arenas(host) == arenasNow));
            // ---- visits: plots far apart, leave goes back
            var mateDojo = dojoOf(dMate);
            com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.ensureBuilt(server, mateDojo);
            com.dbzlegacy.adaptivedifficulty.dojo.DojoPlacer.finishNow();
            var o2 = com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.plotOrigin(mateDojo.plot);
            String v = com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.visit(player, mateDojo);
            boolean there = com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.inDojoWorld(player)
                    && Math.hypot(player.m_20185_() - o2.m_123341_(), player.m_20189_() - o2.m_123343_()) < 70;
            note("DOJO VISIT another dojo's plot; plots are 4000+ blocks apart: " + v + " " + pf(there
                    && Math.hypot(o2.m_123341_() - o.m_123341_(), o2.m_123343_() - o.m_123343_()) >= 4000));
            com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.leave(player);
            note("DOJO LEAVE goes back to where the visit started: " + pf(!com.dbzlegacy.adaptivedifficulty.dojo
                    .DojoWorld.inDojoWorld(player) && Math.abs(player.m_20185_() - hx) < 0.6));
            for (GuiOpenPacket p : keep("dojo page", () -> UltraGui.open(player, "spar", "dojo"))) {
                note("DOJO PAGE: points, clout, members, hall, style, elites, Upgrades, Wars, Visit: " + pf(
                        has(p, "Dojo Points") && has(p, "Clout this season") && has(p, "Members")
                                && has(p, "Hall") && has(p, "Elites") && has(p, "Upgrades") && has(p, "Wars")
                                && has(p, "Visit dojo")) + " | " + labels(p));
            }
        } catch (Throwable t) {
            note("DOJO script ERROR " + t + " FAIL");
            LOG.error("dojo script", t);
        } finally {
            player.m_150110_().f_35934_ = true;
            for (ServerPlayer p : new ServerPlayer[] {dMate, dMate2, dMate3}) {
                if (p != null && com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.inBout(p.m_20148_())) {
                    try {
                        endBout(p, p.m_20148_(), "FORFEIT");
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.inDojoWorld(player)) {
                com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.returnPlayer(player);
            }
        }
    }

    /** Final phase: wars on the real probe player (bracket and gauntlet), and a lethal blow as a KO. */
    private String warId = "";

    private void dojoFinal(String s) {
        try {
            var store = com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.get();
            switch (s) {
                case "warstart", "warstart2" -> {
                    player.m_150110_().f_35934_ = false;
                    String fmt = "warstart".equals(s) ? "BRACKET" : "GAUNTLET";
                    String d = com.dbzlegacy.adaptivedifficulty.dojo.DojoWar.declare(player, dojoOf(dMate).masterUuid, fmt);
                    var w = com.dbzlegacy.adaptivedifficulty.dojo.DojoWar.warsOf(dojoOf(player).masterUuid).get(0);
                    warId = w.id;
                    String a = com.dbzlegacy.adaptivedifficulty.dojo.DojoWar.accept(dMate, w.id);
                    String j = com.dbzlegacy.adaptivedifficulty.dojo.DojoWar.join(player, w.id);
                    note("WAR " + fmt + " declared, accepted, signed up: " + d + " / " + a + " / " + j + " "
                            + pf("RUNNING".equals(w.status) && com.dbzlegacy.adaptivedifficulty.dojo.DojoWar.signupOpen(w.id)));
                }
                case "warringout" -> {
                    // The stand-in (a fake player, which takes no damage) steps out of the ring: ring-out.
                    // A fake player ignores a same-world teleport (its connection drops it): move it directly.
                    dMate.m_6034_(dMate.m_20185_() + 90, com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.FLOOR_Y + 1,
                            dMate.m_20189_());
                    note("WAR phase at ring-out: in dojo world=" + com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld
                            .inDojoWorld(player));
                }
                case "warcheck" -> {
                    var w = com.dbzlegacy.adaptivedifficulty.dojo.DojoWar.byId(warId);
                    note("WAR record: winner, score and the fight list; everyone back home: " + pf(w != null
                            && "DONE".equals(w.status) && !w.winnerName.isBlank() && w.fights.size() == 1
                            && (w.fromScore + w.toScore) == 1 && !com.dbzlegacy.adaptivedifficulty.dojo.DojoWar
                            .fighting(player.m_20148_())) + " " + (w == null ? "" : w.status + " " + w.winnerName
                            + " " + w.fromScore + "-" + w.toScore + " " + (w.fights.isEmpty() ? "" : w.fights.get(0).how)));
                    String again = com.dbzlegacy.adaptivedifficulty.dojo.DojoWar.declare(player,
                            dojoOf(dMate).masterUuid, "GAUNTLET");
                    var w2 = com.dbzlegacy.adaptivedifficulty.dojo.DojoWar.warsOf(dojoOf(player).masterUuid).get(0);
                    note("WAR the same pair can challenge again after a war: " + again + " "
                            + pf("CHALLENGED".equals(w2.status)));
                    com.dbzlegacy.adaptivedifficulty.dojo.DojoWar.decline(player, w2.id);
                    for (GuiOpenPacket p : keep("war view", () -> UltraGui.open(player, "spar", "dojo_war_view:" + warId))) {
                        note("WAR bracket page shows the rounds and the winner: " + pf(has(p, "Winner") && has(p, " by ")));
                    }
                    player.m_150110_().f_35934_ = true;
                    write("uiprobe-script.txt", scriptLog.toString(), false);
                }
                case "kostart" -> {
                    player.m_150110_().f_35934_ = false;
                    draft(dMate, false, 0);
                    com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(dMate, "board_post", "");
                    com.dbzlegacy.adaptivedifficulty.dojo.DojoPlacer.finishNow();
                    String a = com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(player, "board_accept",
                            dMate.m_20148_().toString());
                    note("KO bout started: " + a);
                }
                case "kohit" -> {
                    player.m_21153_(player.m_21233_());
                    player.f_19802_ = 0;
                    boolean hurt = player.m_6469_(player.m_269291_().m_269075_(dMate), 1.0e6f);
                    note("KO lethal blow landed=" + hurt + " hp=" + player.m_21223_());
                }
                case "kocheck" -> {
                    var r = com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.result(player.m_20148_());
                    note("KO a lethal blow in a board spar is a knockout, nobody dies, both go home: " + pf(
                            player.m_6084_() && r != null && r.lines().toString().contains("knockout")
                                    && !com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.inBout(player.m_20148_())
                                    && !com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.inDojoWorld(player)));
                    player.m_150110_().f_35934_ = true;
                    player.m_21153_(player.m_21233_());
                    write("uiprobe-script.txt", scriptLog.toString(), false);
                }
                default -> { }
            }
        } catch (Throwable t) {
            note("DOJO final " + s + " ERROR " + t + " FAIL");
            LOG.error("dojo final", t);
        }
    }

    // ---- 4.7.63 Difficulty guide shows once per account (test only) ----
    private boolean guideDone() {
        return com.dbzlegacy.adaptivedifficulty.gui.ultra.DifficultyTutorial.done(player);
    }

    private void guideReset() {
        com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore.root(player)
                .m_128473_(com.dbzlegacy.adaptivedifficulty.gui.ultra.DifficultyTutorial.KEY);
    }

    /** The real click path: a button through UltraGuiActions, as the native screen and KaiPath send it. */
    private List<GuiOpenPacket> click(String tag, String actionId, String page) {
        JsonObject args = new JsonObject();
        args.addProperty("screen", "difficulty");
        args.addProperty("page", page);
        return keep(tag, () -> UltraGuiActions.dispatch(player, "difficulty", actionId, args.toString()));
    }

    private static boolean home(GuiOpenPacket p) {
        return "main".equals(p.pageId()) && has(p, "Replay the difficulty guide") && has(p, "Your tier")
                && !has(p, "Step 1 of 3") && !has(p, "Skip guide");
    }

    private void guideScript() {
        // Finish: Step 3's "Got it" marks done and lands on Home.
        for (GuiOpenPacket p : keep("guide step3", () -> UltraGui.open(player, "difficulty", "your_tier"))) {
            note("GUIDE STEP3 not done yet has Got it + Skip: " + (!guideDone() && has(p, "Step 3 of 3 - Your tier")
                    && has(p, "Got it - take me to Difficulty") && has(p, "Skip guide") ? "PASS" : "FAIL"));
        }
        for (GuiOpenPacket p : click("guide got it", "do:guide_done", "guide_your_tier")) {
            note("GOT IT marks done and lands on Home: " + (guideDone() && home(p) ? "PASS" : "FAIL") + " | " + labels(p));
        }
        for (GuiOpenPacket p : keep("guide reopen", () -> UltraGui.open(player, "difficulty", "main"))) {
            note("REOPEN after finish goes straight to Home: " + (home(p) ? "PASS" : "FAIL"));
        }
        for (GuiOpenPacket p : keep("guide your_tier done", () -> UltraGui.open(player, "difficulty", "your_tier"))) {
            note("YOUR TIER after finish is Home: " + (home(p) ? "PASS" : "FAIL"));
        }
        for (GuiOpenPacket p : keep("tiers after guide", () -> UltraGui.open(player, "difficulty", "tiers"))) {
            note("TIERS after guide is the plain list: " + ("tiers".equals(p.pageId()) && !has(p, "Step 2 of 3")
                    && !has(p, "Next: Your tier") && has(p, "Back to Difficulty") && has(p, "Pick your level")
                    && has(p, "T1 Awakened") ? "PASS" : "FAIL") + " | " + labels(p));
        }
        for (GuiOpenPacket p : keep("summons after guide", () -> UltraGui.open(player, "difficulty", "summons"))) {
            // 4.7.71: a tab page goes back to the system Home, named by the page registry.
            note("SUMMONS after guide: Back to Difficulty + Main menu: " + (has(p, "Back to Difficulty") && has(p, "Main menu") ? "PASS" : "FAIL"));
        }
        // Replay: How difficulty works opens every step; finishing again keeps it done.
        for (GuiOpenPacket p : click("guide replay", "page:guide", "main")) {
            note("REPLAY opens Step 1: " + (has(p, "Step 1 of 3 - What is Difficulty") && guideDone() ? "PASS" : "FAIL"));
        }
        for (GuiOpenPacket p : click("guide replay step2", "page:guide_tiers", "guide")) {
            note("REPLAY Next opens Step 2: " + (has(p, "Step 2 of 3 - Pick your level") && has(p, "Skip guide")
                    ? "PASS" : "FAIL"));
        }
        for (GuiOpenPacket p : click("guide replay step3", "page:guide_your_tier", "guide_tiers")) {
            note("REPLAY Next opens Step 3: " + (has(p, "Step 3 of 3 - Your tier") ? "PASS" : "FAIL"));
        }
        for (GuiOpenPacket p : click("guide replay finish", "do:guide_done", "guide_your_tier")) {
            note("REPLAY finish lands on Home, still done: " + (home(p) && guideDone() ? "PASS" : "FAIL"));
        }
        for (GuiOpenPacket p : keep("guide reopen after replay", () -> UltraGui.open(player, "difficulty", "main"))) {
            note("REOPEN after replay goes straight to Home: " + (home(p) ? "PASS" : "FAIL"));
        }
        // Skip on Step 1 (an account that never saw the guide, as every player before 4.7.63).
        guideReset();
        for (GuiOpenPacket p : keep("guide existing player", () -> UltraGui.open(player, "difficulty", "main"))) {
            note("EXISTING PLAYER (no record) sees Step 1: " + (!guideDone() && has(p, "Step 1 of 3") ? "PASS" : "FAIL"));
        }
        for (GuiOpenPacket p : click("guide skip step1", "do:guide_done", "guide")) {
            note("SKIP on Step 1 marks done and lands on Home: " + (guideDone() && home(p) ? "PASS" : "FAIL"));
        }
        for (GuiOpenPacket p : keep("guide reopen after skip", () -> UltraGui.open(player, "difficulty", "main"))) {
            note("REOPEN after skip goes straight to Home: " + (home(p) ? "PASS" : "FAIL"));
        }
        // Skip on Step 2 as well.
        guideReset();
        click("guide skip step2", "do:guide_done", "guide_tiers");
        note("SKIP on Step 2 marks done: " + (guideDone() ? "PASS" : "FAIL"));
        // Stored on disk per account: dropping the cached record and reading it back keeps it (relog).
        com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore.unload(player.m_20148_());
        note("DONE survives a record reload (relog): " + (guideDone() ? "PASS" : "FAIL"));
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
                // 4.7.63: a fresh account opens the guide; it says once that it shows once; Skip on the step.
                note("GUIDE FIRST OPEN shows Step 1 + once line + Skip guide: " + (!guideDone()
                        && has(p, "This guide shows once. Replay it from Home.")
                        && has(p, "Skip guide") ? "PASS" : "FAIL"));
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
                note("SUMMONS chrome: " + (has(p, "Boss summons") && has(p, "Back to Difficulty") && has(p, "Main menu") ? "PASS" : "FAIL"));
            }
            for (GuiOpenPacket p : keep("summon dragon", () -> UltraGui.open(player, "difficulty", "end_dragon"))) {
                note("SUMMON locked still dim+inert: " + (p.buttonsJson().contains("nav:none") && has(p, "Summon locked") ? "PASS" : "FAIL"));
            }
            guideScript();
            hubScript();
            meditationScript();
            customSkinScript();
            partColorScript();

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
            for (GuiOpenPacket p : keep("guide after slot switch", () -> UltraGui.open(player, "difficulty", "main"))) {
                note("SLOT SWITCH keeps the guide done (per account): " + (guideDone() && home(p) ? "PASS" : "FAIL"));
            }

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
            note("LOGOUT keeps the guide done (per account): " + (guideDone() ? "PASS" : "FAIL"));
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
        // 4.7.63: the guide on the real client. Fresh record: Difficulty opens Step 1; click Skip guide; Home;
        // reopen is Home; How difficulty works replays Step 1 (KaiPath-only runs log the clicks as NOT FOUND).
        // 4.7.64: the hub on the real client: Home, a tab click, the Dojo wars tile, and the spar board list button.
        {"open", "hub", "main"}, {"wait", "60"}, {"say", "uirecord hub1-home"}, {"wait", "20"},
        {"say", "uiclick label:Goals"}, {"wait", "60"}, {"say", "uirecord hub2-goals"}, {"wait", "20"},
        {"open", "hub", "main"}, {"wait", "60"}, {"say", "uiclick label:Dojo"}, {"wait", "60"},
        {"say", "uirecord hub3-dojo-wars"}, {"wait", "20"},
        // 4.7.68: the Spar Board tab, the Dojo tab and an upgrade preview on the real client.
        {"open", "spar", "sparboard"}, {"wait", "60"}, {"say", "uirecord board1-spar-board"}, {"wait", "20"},
        {"open", "dojo", "home"}, {"wait", "60"}, {"say", "uirecord dojo1-dojo-home"}, {"wait", "20"},
        {"open", "dojo", "dojo_up:arena"}, {"wait", "60"}, {"say", "uirecord dojo2-upgrade-preview"}, {"wait", "20"},
        {"guidereset"}, {"open", "difficulty", "main"}, {"wait", "60"}, {"say", "uirecord guide1-first-open"},
        {"wait", "20"}, {"say", "uiclick label:Skip guide"}, {"wait", "60"}, {"say", "uirecord guide2-after-skip"},
        {"wait", "20"}, {"guidecheck"}, {"open", "difficulty", "main"}, {"wait", "60"},
        {"say", "uirecord guide3-reopen"}, {"wait", "20"}, {"say", "uiclick label:Replay the difficulty guide"},
        {"wait", "60"}, {"say", "uirecord guide4-replay"}, {"wait", "20"}, {"guidecheck"},
        // 4.7.65: Friendly Fist never lets a spar kill. Lethal partner hits on the real probe player, checked a
        // few ticks later (the heal is queued after the hit), with a screenshot of the client still in game.
        {"ff", "setup"}, {"wait", "10"},
        {"ff", "hitV"}, {"wait", "10"}, {"ff", "checkV"}, {"ff", "hitV"}, {"wait", "10"}, {"ff", "checkV"},
        {"ff", "hitV"}, {"wait", "10"}, {"ff", "checkV"}, {"say", "uirecord ff1-after-three-lethal-hits"},
        {"wait", "20"}, {"ff", "hitA"}, {"wait", "10"}, {"ff", "checkA"},
        // 4.7.66: Custom menu skin on the real client: the picker wearing it, My colors, a tap at the top of the
        // Panel wheel (red), Apply, the editor reopened in the new colors.
        {"custom", "prep"}, {"open", "hub", "skins"}, {"wait", "60"}, {"say", "uirecord custom1-picker"},
        {"wait", "20"}, {"open", "hub", "skin_custom"}, {"wait", "60"}, {"say", "uirecord custom2-editor"},
        {"wait", "20"}, {"say", "uiclick wheel:0"}, {"wait", "20"}, {"say", "uirecord custom3-wheel-picked"},
        {"wait", "20"}, {"say", "uiclick label:Apply my colors"}, {"wait", "60"},
        {"say", "uirecord custom4-applied"}, {"wait", "20"}, {"custom", "check"}, {"custom", "reset"}, {"wait", "20"},
        // 4.7.67: part colors on the real client: the own channel's swatch page, a tap at the top of the wheel (red),
        // Apply this color, the page reopened showing #FF0000 and the model in it.
        {"part", "reset"}, {"open", "character", "bodycolors:own:0"}, {"wait", "60"}, {"say", "uirecord part1-swatches"},
        {"wait", "20"}, {"say", "uiclick wheel:0"}, {"wait", "20"}, {"say", "uirecord part2-wheel-picked"},
        {"wait", "20"}, {"say", "uiclick label:Apply this color"}, {"wait", "60"}, {"say", "uirecord part3-applied"},
        {"wait", "20"}, {"part", "check"}, {"part", "reset"}, {"wait", "20"},
        // 4.7.68: a lethal blow in a board spar is a KO (the real probe player is the victim); then a bracket war and
        // a gauntlet war on the real player against a stand-in (who leaves the ring, a ring-out).
        {"dojo", "kostart"}, {"wait", "40"}, {"say", "uirecord dojo3-in-arena"}, {"dojo", "kohit"}, {"wait", "40"},
        {"dojo", "kocheck"}, {"wait", "20"},
        {"dojo", "warstart"}, {"wait", "1700"}, {"say", "uirecord war1-bout"}, {"dojo", "warringout"}, {"wait", "100"},
        {"dojo", "warcheck"}, {"dojo", "warstart2"}, {"wait", "1700"}, {"dojo", "warringout"}, {"wait", "100"},
        {"dojo", "warcheck"},
        // 4.7.72: the native HUD on the real client: every element up, the client reports what it drew (nothing on
        // a KaiPath-only stage, where the bars and chat lines stay), a screenshot, then everything down.
        {"hud", "show"}, {"wait", "30"}, {"say", "uirecordhud all"}, {"wait", "60"}, {"hud", "hide"}, {"wait", "20"},
        {"trialcheck"},
        // Last, because the probe player dies in both (stand-ins are Forge fake players, which take no damage):
        // a zombie's lethal hit in a Friendly Fist spar still kills; with Friendly Fist off a partner's still kills.
        {"ff", "mob"}, {"wait", "10"}, {"ff", "checkMob"}, {"ff", "respawn"}, {"wait", "40"},
        {"ff", "off"}, {"wait", "10"}, {"ff", "checkOff"}, {"ff", "respawn"}, {"wait", "40"}, {"ff", "cleanup"},
        {"wait", "40"}, {"say", "uiquit"},
    };

    // ---- 4.7.66 Custom menu skin (test only) ----
    private static final String[] TONE_KEYS = {"panel", "primary", "trim", "text", "confirm"};

    private static MenuPrefsView prefsNow(ServerPlayer p) {
        var pr = com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuPrefs.get(p);
        return new MenuPrefsView(pr.skin(), pr.backgrounds(), pr.custom());
    }

    private record MenuPrefsView(String skin, boolean backgrounds,
            com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.Custom custom) {
        boolean custom(int... tones) {
            return java.util.Arrays.equals(custom.tones(), tones);
        }
    }

    /** A My colors click as the LM client sends it: the five fields under their own ids (null leaves one out). */
    private List<GuiOpenPacket> skinClick(String tag, String actionId, String page, String... fields) {
        JsonObject args = new JsonObject();
        args.addProperty("screen", "hub");
        args.addProperty("page", page);
        for (int i = 0; fields != null && i < fields.length && i < TONE_KEYS.length; i++) {
            if (fields[i] != null) {
                args.addProperty("@hub:input:skin_" + TONE_KEYS[i], fields[i]);
            }
        }
        return keep(tag, () -> UltraGuiActions.dispatch(player, "hub", actionId, args.toString()));
    }

    private static String fieldValue(GuiOpenPacket p, String key) {
        try {
            for (JsonElement el : JsonParser.parseString(p.inputsJson()).getAsJsonArray()) {
                JsonObject o = el.getAsJsonObject();
                if (o.get("id").getAsString().endsWith("input:skin_" + key) && o.has("value")) {
                    return o.get("value").getAsString();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int countInputs(GuiOpenPacket p, String type) {
        int n = 0;
        try {
            for (JsonElement el : JsonParser.parseString(p.inputsJson()).getAsJsonArray()) {
                if (type.equals(el.getAsJsonObject().get("type").getAsString())) {
                    n++;
                }
            }
        } catch (Throwable ignored) {
        }
        return n;
    }

    /** The Apply action on a My colors page (its id carries the preview token). */
    private static String applyId(GuiOpenPacket p) {
        try {
            for (JsonElement el : JsonParser.parseString(p.buttonsJson()).getAsJsonArray()) {
                String id = el.getAsJsonObject().get("id").getAsString();
                if (semantic(id).startsWith("do:menu_custom:apply:")) {
                    return semantic(id);
                }
            }
        } catch (Throwable ignored) {
        }
        return "do:menu_custom:apply:custom";
    }

    private static String pf(boolean ok) {
        return ok ? "PASS" : "FAIL";
    }

    private void customSkinScript() {

        String CUSTOM = com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraHubScreens.PAGE_CUSTOM;
        boolean editing = com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraHubScreens.canEditColors(player);
        boolean wheels = com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesColorWheel(player);
        note("CUSTOM client can edit=" + editing + " wheels=" + wheels);
        com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuPrefs.put(player,
                new com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuPrefs.Prefs("saiyan_royal", true, false));
        int[] sr = com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.Custom.DEFAULT.tones();
        for (GuiOpenPacket p : keep("custom picker", () -> UltraGui.open(player, "hub", "skins"))) {
            note("CUSTOM PICKER row after Zeno Omni, tap to make your own: " + pf(has(p, "Custom")
                    && has(p, "Tap to make your own") && p.buttonsJson().indexOf("menu_skin:custom.")
                    > p.buttonsJson().indexOf("menu_skin:zeno_omni")));
        }
        List<GuiOpenPacket> got = hubClick("custom pick", "hub", "do:menu_skin:custom.d.banner.160b0d.e23b3b.f0b429.f6e8e4.3faf55", "skins");
        MenuPrefsView v = prefsNow(player);
        note("CUSTOM PICK wears Custom with Saiyan Royal tones and opens My colors: " + pf("custom".equals(v.skin())
                && v.custom(sr) && CUSTOM.equals(got.isEmpty() ? "" : got.get(got.size() - 1).pageId())));
        if (!editing) {
            for (GuiOpenPacket p : got) {
                note("CUSTOM EDITOR without the 4.7.66 LM client shows the plain note: "
                        + pf(has(p, "Editing your colors needs the Legacy Mechanics client") && countInputs(p, "text") == 0
                                && countInputs(p, "color_wheel") == 0));
            }
            com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuPrefs.put(player,
                    new com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuPrefs.Prefs("saiyan_royal", true, false));
            return;
        }
        GuiOpenPacket ed = got.get(got.size() - 1);
        note("CUSTOM EDITOR five fields, " + (wheels ? "five wheels" : "no wheels") + ", Style, Scene, Apply, Preview, From, Reset: "
                + pf(countInputs(ed, "text") == 5 && countInputs(ed, "color_wheel") == (wheels ? 5 : 0)
                        && has(ed, "Style - Dark") && has(ed, "Scene - Banner") && has(ed, "Apply my colors")
                        && has(ed, "Preview") && has(ed, "From Namek") && has(ed, "Reset to default")
                        && "#e23b3b".equals(fieldValue(ed, "primary")) && ed.buttonsJson().contains("bindInputs")
                        && ed.buttonsJson().contains("\"onEnter\":true")) + " | " + labels(ed));
        // Apply five colors (both spellings).
        got = skinClick("custom apply", applyId(ed), CUSTOM, "#1A1033", "7c4dff", "#ffc857", "E0E0FF", "#2ecc71");
        v = prefsNow(player);
        GuiOpenPacket p = got.isEmpty() ? ed : got.get(got.size() - 1);
        note("CUSTOM APPLY saves the five colors, reopens My colors wearing them: " + pf("custom".equals(v.skin())
                && v.custom(0x1A1033, 0x7C4DFF, 0xFFC857, 0xE0E0FF, 0x2ECC71) && CUSTOM.equals(p.pageId())
                && has(p, "Saved. Every menu now uses your colors.") && "#1a1033".equals(fieldValue(p, "panel"))));
        String token = com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuPrefs.get(player).skinOrDefault().id();
        note("CUSTOM LOOK goes out as the palette token on message 3: " + token + " "
                + pf("custom.d.banner.1a1033.7c4dff.ffc857.e0e0ff.2ecc71".equals(token)));
        // Invalid hex: nothing applies, every field kept as typed.
        for (String[] bad : new String[][] {{"red", null}, {"#12", null}, {"#gggggg", null}}) {
            got = skinClick("custom bad " + bad[0], applyId(p), CUSTOM, bad[0], "#ff0000", null, null, null);
            GuiOpenPacket q = got.isEmpty() ? p : got.get(got.size() - 1);
            MenuPrefsView after = prefsNow(player);
            note("CUSTOM BAD HEX '" + bad[0] + "' shows the human error, applies nothing, keeps the fields: "
                    + pf(has(q, "Panel: That doesn") && has(q, "t look like a color")
                            && after.custom(0x1A1033, 0x7C4DFF, 0xFFC857, 0xE0E0FF, 0x2ECC71)
                            && bad[0].equals(fieldValue(q, "panel")) && "#ff0000".equals(fieldValue(q, "primary"))
                            && "#ffc857".equals(fieldValue(q, "trim"))));
            p = q;
        }
        // Empty field keeps the current color, never black.
        got = skinClick("custom empty", applyId(p), CUSTOM, "", "", "", "", "#27ae60");
        p = got.isEmpty() ? p : got.get(got.size() - 1);
        note("CUSTOM EMPTY fields keep their colors: " + pf(prefsNow(player).custom(0x1A1033, 0x7C4DFF, 0xFFC857,
                0xE0E0FF, 0x27AE60)));
        // Unreadable text on a dark skin: adjusted, the page says so.
        got = skinClick("custom low contrast dark", applyId(p), CUSTOM, "#202020", "#7c4dff", "#ffc857", "#262626", "#27ae60");
        p = got.isEmpty() ? p : got.get(got.size() - 1);
        var sk = com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuPrefs.get(player).skinOrDefault();
        note("CUSTOM DARK text adjusted to read 3:1, note shown: " + pf(has(p, "Text color adjusted so it stays readable")
                && com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.contrast(sk.text(), sk.panel()) >= 3.0));
        // Light: Style toggles to Light (draft), then apply a pale text on a pale panel.
        got = skinClick("custom style light", "do:menu_custom:dark", CUSTOM, "#f0f0f0", null, null, "#eeeeee", null);
        p = got.isEmpty() ? p : got.get(got.size() - 1);
        note("CUSTOM STYLE toggles to Light and keeps the typed fields, saves nothing: " + pf(has(p, "Style - Light")
                && "#f0f0f0".equals(fieldValue(p, "panel")) && "#eeeeee".equals(fieldValue(p, "text"))
                && prefsNow(player).custom().dark()));
        got = skinClick("custom light apply", applyId(p), CUSTOM, "#f0f0f0", null, null, "#eeeeee", null);
        p = got.isEmpty() ? p : got.get(got.size() - 1);
        sk = com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuPrefs.get(player).skinOrDefault();
        note("CUSTOM LIGHT text adjusted to read 4.5:1, note shown: " + pf(!sk.dark() && has(p, "Text color adjusted")
                && com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.contrast(sk.text(), sk.panel()) >= 4.5));
        note("CUSTOM BRIDGE letters readable on the dark bridge panel: " + sk.bridgePrimary() + sk.bridgeText() + " "
                + pf(com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.contrast(
                        com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.vanillaRgb(sk.bridgeText()),
                        com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.BRIDGE_PANEL) >= 4.5));
        // Scene cycles; From fills a preset's colors; Reset fills the defaults; neither saves.
        got = skinClick("custom scene", "do:menu_custom:motif", CUSTOM);
        p = got.isEmpty() ? p : got.get(got.size() - 1);
        note("CUSTOM SCENE cycles to the next motif: " + pf(has(p, "Scene - Namek")));
        int[] saved = prefsNow(player).custom().tones();
        got = skinClick("custom from namek", "do:menu_custom:from:namek_serenity", CUSTOM);
        p = got.isEmpty() ? p : got.get(got.size() - 1);
        note("CUSTOM FROM Namek fills its colors, saves nothing: " + pf("#0d201b".equals(fieldValue(p, "panel"))
                && "#57d9a3".equals(fieldValue(p, "primary")) && has(p, "Filled in Namek Serenity")
                && java.util.Arrays.equals(prefsNow(player).custom().tones(), saved)));
        got = skinClick("custom reset", "do:menu_custom:reset", CUSTOM);
        p = got.isEmpty() ? p : got.get(got.size() - 1);
        note("CUSTOM RESET fills the Saiyan Royal colors, saves nothing: " + pf("#160b0d".equals(fieldValue(p, "panel"))
                && "#e23b3b".equals(fieldValue(p, "primary")) && "#f6e8e4".equals(fieldValue(p, "text"))
                && java.util.Arrays.equals(prefsNow(player).custom().tones(), saved)));
        // Save a known palette, then relog, slot switch, preset and back.
        got = skinClick("custom final apply", applyId(p), CUSTOM, "#1a1033", "#7c4dff", "#ffc857", "#e0e0ff", "#2ecc71");
        int[] mine = {0x1A1033, 0x7C4DFF, 0xFFC857, 0xE0E0FF, 0x2ECC71};
        com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore.unload(player.m_20148_());
        note("CUSTOM survives a record reload (relog): " + pf("custom".equals(prefsNow(player).skin())
                && prefsNow(player).custom(mine)));
        try {
            java.lang.reflect.Method sw = com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeCharacter.class
                    .getDeclaredMethod("switchedIn", ServerPlayer.class, java.util.UUID.class, java.util.UUID.class,
                            int.class, boolean.class);
            sw.setAccessible(true);
            java.util.UUID me = com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeCharacter.id(player);
            sw.invoke(null, player, me, me, 0, false);
            note("CUSTOM survives a character slot switch: " + pf("custom".equals(prefsNow(player).skin())
                    && prefsNow(player).custom(mine)));
        } catch (Throwable t) {
            note("CUSTOM slot switch ERROR " + t + " FAIL");
        }
        hubClick("custom to preset", "hub", "do:menu_skin:zeno_omni", "skins");
        note("CUSTOM switch to a preset keeps the saved colors: " + pf("zeno_omni".equals(prefsNow(player).skin())
                && prefsNow(player).custom(mine)));
        got = hubClick("custom back", "hub", "do:menu_skin:" + com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins
                .customToken(prefsNow(player).custom()), "skins");
        note("CUSTOM back from a preset restores the player's colors: " + pf("custom".equals(prefsNow(player).skin())
                && prefsNow(player).custom(mine)));
        for (GuiOpenPacket q : keep("custom picker in use", () -> UltraGui.open(player, "hub", "skins"))) {
            note("CUSTOM PICKER while worn: You are using this + Edit my colors: " + pf(has(q, "Edit my colors")
                    && q.buttonsJson().contains("page:" + CUSTOM)));
        }
        hubClick("custom backgrounds off", "hub", "do:menu_backgrounds", "skins");
        note("CUSTOM Backgrounds off keeps Custom: " + pf(!prefsNow(player).backgrounds() && "custom".equals(prefsNow(player).skin())));
        hubClick("custom backgrounds on", "hub", "do:menu_backgrounds", "skins");
        note("CUSTOM ACCENTS fixed: " + pf(com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.systemAccentHex('c') == 0xE23B3B));
        com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuPrefs.put(player,
                new com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuPrefs.Prefs("saiyan_royal", true, false));
    }

    // ---- 4.7.67 part colors: any color (test only) ----
    private static Integer partColor(ServerPlayer p, String ch) {
        return com.dbzlegacy.adaptivedifficulty.character.CosmeticBodyPartService.partColor(p, ch);
    }

    /** The swatch page's Apply as a client sends it: the typed hex in args.value (bindInput). */
    private List<GuiOpenPacket> colorApply(String tag, String ch, String typed) {
        JsonObject args = new JsonObject();
        args.addProperty("screen", "character");
        args.addProperty("page", "bodycolors:" + ch + ":0");
        args.addProperty("bindInput", "@character:input:bodycolor_hex");
        args.addProperty("value", typed);
        return keep(tag, () -> UltraGuiActions.dispatch(player, "character", "do:bodypart_color:" + ch + ":",
                args.toString()));
    }

    private void partColorScript() {
        if (!com.dbzlegacy.adaptivedifficulty.character.CosmeticBodyPartService.canUse(player)) {
            note("PARTCOLOR model customization unavailable on this server: SKIP");
            return;
        }
        boolean typing = com.dbzlegacy.adaptivedifficulty.net.gui.UltraClientFeatures.hasTextInput(player);
        boolean wheels = com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesColorWheel(player);
        Integer trimBefore = partColor(player, "trim");
        colorApply("partcolor reset own", "own", "");
        keep("partcolor reset", () -> UltraGuiActions.dispatch(player, "character", "do:bodypart_color_reset:own",
                "{\"screen\":\"character\",\"page\":\"bodycolors:own:0\"}"));
        for (GuiOpenPacket p : keep("partcolor page", () -> UltraGui.open(player, "character", "bodycolors:own:0"))) {
            note("PARTCOLOR PAGE presets kept + Pick any color (" + (wheels ? "wheel" : "no wheel") + "): "
                    + pf(has(p, "Pick a swatch. It saves immediately.") && has(p, "Reset to natural look")
                            && (typing ? countInputs(p, "text") == 1 && has(p, "Apply this color")
                                    && p.buttonsJson().contains("input:bodycolor_hex")
                                    : has(p, "Typed fields need KaiPath 1.3.9 or newer"))
                            && countInputs(p, "color_wheel") == (wheels ? 1 : 0)
                            && p.buttonsJson().contains("do:bodypart_color:own:ffffff") && p.buttonsJson().contains("do:bodypart_color:own:111111")) + " | " + labels(p));
        }
        if (!typing) {
            return;
        }
        List<GuiOpenPacket> got = colorApply("partcolor typed #", "own", "#e23b3b");
        note("PARTCOLOR typed #e23b3b + Apply saves it, the page shows it: " + pf(Integer.valueOf(0xE23B3B)
                .equals(partColor(player, "own")) && !got.isEmpty() && has(got.get(got.size() - 1), "#E23B3B")));
        colorApply("partcolor typed bare", "own", "3B82F6");
        note("PARTCOLOR typed 3B82F6 (no #, upper case) saves it: " + pf(Integer.valueOf(0x3B82F6)
                .equals(partColor(player, "own"))));
        for (String bad : new String[] {"red", "#12", "#gggggg"}) {
            got = colorApply("partcolor bad " + bad, "own", bad);
            note("PARTCOLOR bad '" + bad + "' shows the human error, changes nothing: " + pf(Integer.valueOf(0x3B82F6)
                    .equals(partColor(player, "own")) && !got.isEmpty()
                    && has(got.get(got.size() - 1), "t look like a color")));
        }
        got = colorApply("partcolor empty", "own", "");
        note("PARTCOLOR empty field keeps the color: " + pf(Integer.valueOf(0x3B82F6).equals(partColor(player, "own"))
                && !got.isEmpty() && has(got.get(got.size() - 1), "Nothing changed")));
        keep("partcolor swatch", () -> UltraGuiActions.dispatch(player, "character", "do:bodypart_color:own:00aa00",
                "{\"screen\":\"character\",\"page\":\"bodycolors:own:0\"}"));
        note("PARTCOLOR a swatch still saves instantly: " + pf(Integer.valueOf(0x00AA00).equals(partColor(player, "own"))));
        note("PARTCOLOR channels independent (trim untouched): " + pf(java.util.Objects.equals(trimBefore,
                partColor(player, "trim"))));
        try {
            java.lang.reflect.Method sw = com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeCharacter.class
                    .getDeclaredMethod("switchedIn", ServerPlayer.class, java.util.UUID.class, java.util.UUID.class,
                            int.class, boolean.class);
            sw.setAccessible(true);
            java.util.UUID me = com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeCharacter.id(player);
            sw.invoke(null, player, me, me, 0, false);
            note("PARTCOLOR survives a character slot switch: " + pf(Integer.valueOf(0x00AA00).equals(partColor(player, "own"))));
        } catch (Throwable t) {
            note("PARTCOLOR slot switch ERROR " + t + " FAIL");
        }
        keep("partcolor reset end", () -> UltraGuiActions.dispatch(player, "character", "do:bodypart_color_reset:own",
                "{\"screen\":\"character\",\"page\":\"bodycolors:own:0\"}"));
        note("PARTCOLOR Reset returns the channel to Default: " + pf(partColor(player, "own") == null));
    }

    private void partFinal(String s) {
        switch (s) {
            case "check" -> {
                boolean wheels = com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesColorWheel(player);
                Integer own = partColor(player, "own");
                if (!wheels) {
                    note("FINAL PARTCOLOR wheel tap + Apply on the real client: SKIP (no wheel on this client)");
                } else {
                    note("FINAL PARTCOLOR real client: wheel tap fills the field, Apply saves it: own="
                            + (own == null ? "default" : String.format("%06x", own)) + " "
                            + pf(Integer.valueOf(0xFF0000).equals(own)));
                }
                write("uiprobe-script.txt", scriptLog.toString(), false);
            }
            case "reset" -> com.dbzlegacy.adaptivedifficulty.character.CosmeticBodyPartService.executeColorReset(player, "own");
            default -> { }
        }
    }

    /** Final phase: wear Custom (default tones) so the real client can tap a wheel and Apply. */
    private void customFinal(String s) {
        switch (s) {
            case "prep" -> {
                com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuPrefs.put(player,
                        new com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuPrefs.Prefs("custom", true, false));
                com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.sendPrefs(player);
                note("FINAL custom prep worn=" + prefsNow(player).skin());
            }
            case "check" -> {
                if (!com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraHubScreens.canEditColors(player)) {
                    note("FINAL CUSTOM wheel tap + Apply on the real client: SKIP (this client cannot edit colors)");
                } else {
                    MenuPrefsView v = prefsNow(player);
                    boolean wheels = com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesColorWheel(player);
                    note("FINAL CUSTOM real client: wheel tap fills Panel, Apply saves it: panel="
                            + String.format("%06x", v.custom().panel()) + " "
                            + pf("custom".equals(v.skin()) && (!wheels || v.custom().panel() == 0xFF0000)));
                }
                write("uiprobe-script.txt", scriptLog.toString(), false);
            }
            case "reset" -> {
                com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuPrefs.put(player,
                        new com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuPrefs.Prefs("saiyan_royal", true, false));
                com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.sendPrefs(player);
            }
            default -> { }
        }
    }

    // ---- 4.7.65 Friendly Fist never lets a spar kill (test only) ----
    private ServerPlayer ffMate;
    private ServerPlayer ffMate2;
    private int ffHits;

    private ServerPlayer ffStandIn(String name) {
        com.mojang.authlib.GameProfile gp = new com.mojang.authlib.GameProfile(
                java.util.UUID.nameUUIDFromBytes(("uiprobe:" + name).getBytes(StandardCharsets.UTF_8)), name);
        ServerPlayer p = net.minecraftforge.common.util.FakePlayerFactory.get(player.m_284548_(), gp);
        p.m_6034_(player.m_20185_() + 2, player.m_20186_(), player.m_20189_());
        backing().add(p);
        byUuid().put(p.m_20148_(), p);
        return p;
    }

    private static void ffSet(ServerPlayer p, boolean on) {
        var data = com.dbzlegacy.adaptivedifficulty.calc.DmzProgression.stats(p);
        if (data != null) {
            data.getStatus().setFriendlyFistEnabled(on);
        }
    }

    private static void ffStart(ServerPlayer a, ServerPlayer b) throws Exception {
        var m = com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.class
                .getDeclaredMethod("startSession", ServerPlayer.class, ServerPlayer.class);
        m.setAccessible(true);
        m.invoke(null, a, b);
        // Sessions under 30 s are not recorded; age both so an end would show in recentSessions.
        long old = System.currentTimeMillis() - 60_000L;
        com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.runtime(a.m_20148_()).startAt = old;
        com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.runtime(b.m_20148_()).startAt = old;
    }

    /** One hit far above any health pool; the victim's invulnerability (rig protection, hurt time) is off for it. */
    private boolean ffHit(ServerPlayer victim, net.minecraft.world.damagesource.DamageSource src) {
        victim.m_150110_().f_35934_ = false;
        victim.m_21153_(victim.m_21233_());
        victim.f_19802_ = 0;
        return victim.m_6469_(src, 1.0e6f);
    }

    private static boolean ffRecordedDeath(ServerPlayer p) {
        for (var r : com.dbzlegacy.adaptivedifficulty.sparring.SparStore.get().recentFor(p.m_20148_().toString())) {
            if ("death".equals(r.reason)) {
                return true;
            }
        }
        return false;
    }

    private String ffState(ServerPlayer p) {
        var rt = com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.runtime(p.m_20148_());
        return p.m_7755_().getString() + " alive=" + p.m_6084_() + " hp=" + p.m_21223_() + "/" + p.m_21233_()
                + " sparActive=" + rt.active + " recordedDeath=" + ffRecordedDeath(p);
    }

    private void ffStep(String s) {
        try {
            switch (s) {
                case "setup" -> {
                    com.dbzlegacy.adaptivedifficulty.sparring.SparStore.get().recentSessions.clear();
                    ffMate = ffStandIn("Fistmate");
                    ffStart(player, ffMate);
                    // Friendly Fist on the victim only: DragonMineZ's own knockdown checks only the attacker's
                    // switch, and a non-melee partner source (like a ki blast) skips its melee path entirely.
                    ffSet(player, true);
                    ffSet(ffMate, false);
                    note("FF setup: " + ffState(player) + " | mate stats=" + (com.dbzlegacy.adaptivedifficulty.calc
                            .DmzProgression.stats(ffMate) != null));
                }
                case "hitV" -> {
                    ffHits++;
                    var src = ffMate.m_269291_().m_269333_(ffMate);
                    boolean took = ffHit(player, src);
                    note("FF lethal partner hit " + ffHits + " (" + src.m_19385_() + ", victim FF only) took=" + took
                            + " right after: " + ffState(player));
                }
                case "checkV" -> {
                    boolean ok = player.m_6084_() && player.m_21223_() >= player.m_21233_() - 0.01f
                            && com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.runtime(player.m_20148_()).active
                            && !ffRecordedDeath(player);
                    note("FF ON lethal hit " + ffHits + " -> alive, healed to full, spar continues, no death record: "
                            + (ok ? "PASS" : "FAIL") + " | " + ffState(player));
                }
                case "hitA" -> {
                    ffSet(player, false);
                    ffSet(ffMate, true);
                    var src = ffMate.m_269291_().m_269075_(ffMate);
                    boolean took = ffHit(player, src);
                    note("FF lethal partner melee (attacker FF only) took=" + took + " right after: " + ffState(player));
                }
                case "checkA" -> {
                    boolean ok = player.m_6084_() && com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem
                            .runtime(player.m_20148_()).active && !ffRecordedDeath(player);
                    note("FF ON (attacker's switch) lethal melee -> alive, spar continues: " + (ok ? "PASS" : "FAIL")
                            + " | " + ffState(player));
                }
                case "mob" -> {
                    // A zombie's lethal hit on a fighter in a Friendly Fist spar is not guarded.
                    com.dbzlegacy.adaptivedifficulty.sparring.SparStore.get().recentSessions.clear();
                    ffStart(player, ffMate);
                    ffSet(player, true);
                    ffSet(ffMate, true);
                    var z = spawnZombie(false, false);
                    ffHit(player, player.m_269291_().m_269333_(z));
                    z.m_146870_();
                }
                case "checkMob" -> note("FF does not guard mob damage (zombie kills the fighter, spar ends as death): "
                        + (!player.m_6084_() && ffRecordedDeath(player) ? "PASS" : "FAIL") + " | " + ffState(player));
                case "respawn" -> {
                    if (!player.m_6084_()) {
                        player = player.f_8924_.m_6846_().m_11236_(player, false);
                        // DragonMineZ protects a freshly dead player (ZenkaiKnockout cancels every hit); the next
                        // check needs a normal fighter, so the test clears those cooldowns.
                        var data = com.dbzlegacy.adaptivedifficulty.calc.DmzProgression.stats(player);
                        String cds = data == null ? "?" : String.valueOf(data.getCooldowns().getAllCooldowns().keySet());
                        if (data != null) {
                            data.getCooldowns().clearCooldowns();
                        }
                        // Vanilla also shields a respawned player: a 60-tick spawn timer and the changing-dimension
                        // flag until the client confirms. Both are private; the test clears them.
                        for (String f : new String[] {"f_8921_", "f_8927_"}) {
                            var fld = ServerPlayer.class.getDeclaredField(f);
                            fld.setAccessible(true);
                            if (fld.getType() == int.class) {
                                fld.setInt(player, 0);
                            } else {
                                fld.setBoolean(player, false);
                            }
                        }
                        note("FF respawned the probe player: alive=" + player.m_6084_() + " cleared DMZ cooldowns " + cds);
                    }
                }
                case "off" -> {
                    // Friendly Fist off for both: a lethal partner hit kills and the spar ends as death.
                    com.dbzlegacy.adaptivedifficulty.sparring.SparStore.get().recentSessions.clear();
                    ffMate2 = ffStandIn("Fistmate2");
                    ffSet(player, false);
                    ffSet(ffMate2, false);
                    ffStart(player, ffMate2);
                    var src = ffMate2.m_269291_().m_269333_(ffMate2);
                    boolean took = ffHit(player, src);
                    note("FF OFF lethal partner hit (" + src.m_19385_() + ") took=" + took + " right after: " + ffState(player));
                }
                case "checkOff" -> {
                    boolean ok = !player.m_6084_() && ffRecordedDeath(player);
                    note("FF OFF lethal partner hit -> kills, spar ends as death (as before): " + (ok ? "PASS" : "FAIL")
                            + " | probe " + ffState(player));
                }
                case "cleanup" -> {
                    for (ServerPlayer p : new ServerPlayer[] {ffMate, ffMate2}) {
                        if (p != null) {
                            backing().remove(p);
                            byUuid().remove(p.m_20148_());
                        }
                    }
                    player.m_150110_().f_35934_ = true;
                    player.m_6885_();
                    player.m_21153_(player.m_21233_());
                    write("uiprobe-script.txt", scriptLog.toString(), false);
                }
                default -> { }
            }
        } catch (Throwable t) {
            note("FF step " + s + " ERROR " + t);
            LOG.error("ff step", t);
        }
    }

    private void finalTick() {
        if (MOCK) {
            if ("hud".equals(System.getProperty("uiprobe.mockset", "nav")) && mockHudTick()) {
                return;
            }
            if (!mockDone) {
                mockDone = true;
                write("uiprobe-script.txt", scriptLog.toString(), false);
                say("uiquit");
            }
            return;
        }
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
            case "ff" -> ffStep(st[1]);
            case "custom" -> customFinal(st[1]);
            case "part" -> partFinal(st[1]);
            case "dojo" -> dojoFinal(st[1]);
            case "hud" -> hudFinal(st[1]);
            case "guidereset" -> {
                guideReset();
                note("FINAL guide reset done=" + guideDone());
            }
            case "guidecheck" -> {
                note("FINAL guide done=" + guideDone());
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

    // ---- 4.7.72 native HUD (test only) ----
    private static final String HUD_PKG = "com.dbzlegacy.adaptivedifficulty.";
    /** Elements the client must report drawn after the FINAL "hud show" step (empty on a bridge stage). */
    private String hudExpect = "";
    private int mockHudStep;
    private boolean mockHudWaiting;

    private static boolean mapHas(Class<?> c, String field, Object key) {
        try {
            java.lang.reflect.Field f = c.getDeclaredField(field);
            f.setAccessible(true);
            return ((Map<?, ?>) f.get(null)).containsKey(key);
        } catch (Exception e) {
            return false;
        }
    }

    /** The probe player's captured packets for {@code element} (stand-ins are skipped). */
    private List<com.dbzlegacy.adaptivedifficulty.net.gui.NativeHudPacket> of(List<LmHud.Sent> sent, String element) {
        List<com.dbzlegacy.adaptivedifficulty.net.gui.NativeHudPacket> out = new ArrayList<>();
        for (LmHud.Sent s : sent) {
            if (s.player().equals(player.m_20148_()) && s.packet().element().equals(element)) {
                out.add(s.packet());
            }
        }
        return out;
    }

    private static com.dbzlegacy.adaptivedifficulty.rival.ProvingGrounds.Grounds probeGrounds(ServerPlayer p) {
        var g = new com.dbzlegacy.adaptivedifficulty.rival.ProvingGrounds.Grounds();
        g.active = true;
        g.name = "Probe Grounds";
        g.tier = 3;
        g.championUuid = java.util.UUID.nameUUIDFromBytes("uiprobe:champion".getBytes(StandardCharsets.UTF_8)).toString();
        g.championName = "Kaito";
        g.battles = 7;
        return g;
    }

    /** A rival link whose Proving Grounds sit under the probe player, so the real once-a-second check shows it. */
    private void groundsLink(boolean on) {
        var rec = com.dbzlegacy.adaptivedifficulty.rival.RivalStore.get().ensurePlayer(player);
        if (rec == null) {
            return;
        }
        if (!on) {
            rec.rivals.remove(PROBE_RIVAL.toString());
            com.dbzlegacy.adaptivedifficulty.rival.ProvingGroundsBar.tick(player);
            return;
        }
        var link = new com.dbzlegacy.adaptivedifficulty.rival.RivalLink();
        link.name = "Kaito";
        var g = probeGrounds(player);
        g.x = player.m_20185_();
        g.y = player.m_20186_();
        g.z = player.m_20189_();
        g.radius = 48;
        link.provingGrounds = g;
        rec.rivals.put(PROBE_RIVAL.toString(), link);
    }

    private static final java.util.UUID PROBE_RIVAL =
            java.util.UUID.nameUUIDFromBytes("uiprobe:rival".getBytes(StandardCharsets.UTF_8));

    private static String instinctBody() {
        return UltraStyle.color("e", "Kaito") + " " + UltraStyle.color("c", "is charging ki!");
    }

    /**
     * The HUD on this stage's client: on the LM client every element goes to the HUD with the bar or chat text and
     * no boss bar; on a bridge client (KaiPath) nothing goes to the HUD and the boss bars stay. Instinct off sends no
     * cards, HUD off brings the bars back, and the More tab switch reads and saves.
     */
    private void hudScript() {
        var srv = player.m_20194_();
        java.util.UUID u = player.m_20148_();
        boolean hud = com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesHud(player);
        String stage = hud ? "LM client" : "bridge";
        LmHud.set(player, true);
        note("HUD stage " + stage + ": client takes hud=" + hud);

        // TP notice
        String tpText = "+12 TP (probe notice)";
        List<LmHud.Sent> tp = LmHud.capture(() -> com.dbzlegacy.adaptivedifficulty.util.TpBossNotify.show(player, tpText,
                net.minecraft.ChatFormatting.GREEN, "", 0L));
        boolean tpBar = mapHas(com.dbzlegacy.adaptivedifficulty.util.TpBossNotify.class, "BARS", u);
        var tpP = of(tp, LmHud.TP);
        if (hud) {
            note("HUD TP notice on the HUD with the bar's text, drains 3 s, no boss bar: " + pf(tpP.size() == 1
                    && tpP.get(0).lines().equals(List.of(tpText)) && tpP.get(0).drain()
                    && tpP.get(0).durationMs() == 3000 && !tpBar));
        } else {
            note("HUD bridge TP notice stays a boss bar, nothing for the HUD: " + pf(tp.isEmpty() && tpBar));
        }

        // Death TP penalty
        String key = com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.KEY;
        com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess.putLong(player, key,
                System.currentTimeMillis() + com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.DURATION_MS);
        String penBefore = com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.currentText(player);
        List<LmHud.Sent> pen = LmHud.capture(() -> com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.pulse(srv, 20));
        boolean penBar = mapHas(com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.class, "BARS", u);
        String penText = com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.currentText(player);
        var penP = of(pen, LmHud.PENALTY);
        if (hud) {
            note("HUD penalty on the HUD with the bar's text and fraction, opens Difficulty, no boss bar: "
                    + pf(penP.size() == 1 && (penP.get(0).lines().equals(List.of(penText))
                    || penP.get(0).lines().equals(List.of(penBefore)))
                    && penP.get(0).progress() > 0.95f && "open:difficulty".equals(penP.get(0).action()) && !penBar)
                    + " | sent " + (penP.isEmpty() ? "none" : penP.size() + "x " + penP.get(0).lines() + " p="
                    + penP.get(0).progress() + " " + penP.get(0).action()) + " bar=" + penBar + " | now " + penText);
        } else {
            note("HUD bridge penalty stays a boss bar: " + pf(pen.isEmpty() && penBar) + " | " + penText);
        }
        com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess.putLong(player, key, System.currentTimeMillis() - 1000L);
        List<LmHud.Sent> penEnd = LmHud.capture(() -> com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.pulse(srv, 20));
        boolean penGone = !mapHas(com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.class, "BARS", u);
        note("HUD penalty end hides it (" + stage + "): " + pf(penGone && (hud
                ? of(penEnd, LmHud.PENALTY).stream().anyMatch(x -> !x.show()) : penEnd.isEmpty())));

        // Underdog through the real grounds check: a pill with the bar's parts on the HUD, else the boss bar
        groundsLink(true);
        List<LmHud.Sent> ud = LmHud.capture(() -> com.dbzlegacy.adaptivedifficulty.rival.ProvingGroundsBar.tick(player));
        boolean udBar = mapHas(com.dbzlegacy.adaptivedifficulty.rival.ProvingGroundsBar.class, "BARS", u);
        var udP = of(ud, LmHud.UNDERDOG);
        if (hud) {
            note("HUD Underdog pill on the grounds, the bar's text, opens Rival, no boss bar: " + pf(udP.size() == 1
                    && udP.get(0).lines().size() == 3 && LmHud.joined(udP.get(0).lines()).contains("Champion Kaito - 7 battles")
                    && "open:rival".equals(udP.get(0).action()) && !udBar)
                    + " | " + (udP.isEmpty() ? "none" : LmHud.joined(udP.get(0).lines())));
        } else {
            note("HUD bridge Underdog stays a boss bar: " + pf(ud.isEmpty() && udBar));
        }
        groundsLink(false);
        note("HUD Underdog leaves with the grounds (" + stage + "): " + pf(!mapHas(
                com.dbzlegacy.adaptivedifficulty.rival.ProvingGroundsBar.class, "BARS", u)
                && !LmHud.showing(player, LmHud.UNDERDOG)));

        // Underdog: the pill's lines read as the bar
        String[] parts = com.dbzlegacy.adaptivedifficulty.rival.ProvingGroundsBar.barParts(probeGrounds(player), u.toString());
        List<String> lines = com.dbzlegacy.adaptivedifficulty.rival.ProvingGroundsBar.hudLines(parts);
        note("HUD Underdog pill lines read exactly as the bar: " + pf(LmHud.joined(lines)
                .equals(com.dbzlegacy.adaptivedifficulty.rival.ProvingGroundsBar.join(parts))
                && "You are the underdog challenger".equals(parts[4]) && lines.size() == 3)
                + " | " + com.dbzlegacy.adaptivedifficulty.rival.ProvingGroundsBar.join(parts));

        // Rival Instinct
        String body = instinctBody();
        String chatLine = com.dbzlegacy.adaptivedifficulty.util.LmChat.fail("Instinct", body);
        List<LmHud.Sent> ins = LmHud.capture(() -> com.dbzlegacy.adaptivedifficulty.rival.RivalInstinct.deliver(player,
                "charge", chatLine, body, PROBE_RIVAL));
        var insP = of(ins, LmHud.INSTINCT);
        if (hud) {
            note("HUD Instinct card instead of the chat line, same text, opens that rival, 4 s: " + pf(insP.size() == 1
                    && insP.get(0).lines().equals(List.of("charge", body))
                    && UltraStyle.plain(chatLine).endsWith(UltraStyle.plain(body))
                    && ("go:rival|list_detail:" + PROBE_RIVAL).equals(insP.get(0).action())
                    && insP.get(0).durationMs() == 4000));
        } else {
            note("HUD bridge Instinct stays a chat line: " + pf(ins.isEmpty()));
        }
        var rec = com.dbzlegacy.adaptivedifficulty.rival.RivalStore.get().ensurePlayer(player);
        boolean wasOn = rec == null || rec.instinctEnabled;
        if (rec != null) {
            rec.instinctEnabled = false;
        }
        List<LmHud.Sent> insOff = LmHud.capture(() -> com.dbzlegacy.adaptivedifficulty.rival.RivalInstinct.pulse(srv,
                System.currentTimeMillis() + 120_000L));
        note("HUD Instinct off sends no cards: " + pf(of(insOff, LmHud.INSTINCT).isEmpty()));
        if (rec != null) {
            rec.instinctEnabled = wasOn;
        }

        // Momentum meter
        boolean momOn = com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig.get().momentumEnabled;
        com.dbzlegacy.adaptivedifficulty.progression.Momentum.bank(player);
        com.dbzlegacy.adaptivedifficulty.progression.Momentum.markFight(player);
        List<LmHud.Sent> mom = LmHud.capture(() -> com.dbzlegacy.adaptivedifficulty.progression.Momentum.hudPulse(srv, 20));
        var momP = of(mom, LmHud.MOMENTUM);
        long left = com.dbzlegacy.adaptivedifficulty.progression.Momentum.remainingMillis(player);
        if (hud && momOn) {
            String want = com.dbzlegacy.adaptivedifficulty.progression.Momentum.meterText(left);
            note("HUD Momentum meter while banked and fighting, the menu's clock: " + pf(momP.size() == 1
                    && momP.get(0).show() && momP.get(0).lines().size() == 1
                    && momP.get(0).lines().get(0).startsWith("Momentum ")
                    && Math.abs(clockSecs(momP.get(0).lines().get(0)) - clockSecs(want)) <= 1
                    && momP.get(0).progress() > 0f) + " | " + (momP.isEmpty() ? "none" : momP.get(0).lines()));
        } else {
            note("HUD Momentum meter not sent (" + stage + ", momentum " + (momOn ? "on" : "off") + "): "
                    + pf(momP.isEmpty()));
        }
        com.dbzlegacy.adaptivedifficulty.progression.Momentum.clear(player);
        List<LmHud.Sent> momEnd = LmHud.capture(() -> com.dbzlegacy.adaptivedifficulty.progression.Momentum.hudPulse(srv, 20));
        if (hud && momOn) {
            note("HUD Momentum meter hides when the bank is empty: "
                    + pf(of(momEnd, LmHud.MOMENTUM).stream().anyMatch(x -> !x.show())));
        }

        // HUD off brings the bars back
        if (hud) {
            LmHud.set(player, false);
            List<LmHud.Sent> tp2 = LmHud.capture(() -> com.dbzlegacy.adaptivedifficulty.util.TpBossNotify.show(player,
                    "+3 TP (probe, HUD off)", net.minecraft.ChatFormatting.GREEN, "", 0L));
            note("HUD OFF: the TP notice is a boss bar again, nothing for the HUD: " + pf(tp2.isEmpty()
                    && mapHas(com.dbzlegacy.adaptivedifficulty.util.TpBossNotify.class, "BARS", u)));
            LmHud.set(player, true);
        }

        // The More tab switch
        for (GuiOpenPacket p : keep("hud more tab", () -> UltraGui.open(player, "hub", "more"))) {
            note("HUD switch on the More tab reads HUD - ON: " + pf(labels(p).contains("HUD - ON")) + " | " + labels(p));
        }
        JsonObject a = new JsonObject();
        a.addProperty("screen", "hub");
        a.addProperty("page", "more");
        for (GuiOpenPacket p : keep("hud switch off", () -> UltraGuiActions.dispatch(player, "hub",
                "do:" + LmHud.ACTION_TOGGLE, a.toString()))) {
            note("HUD switch flips to OFF and saves: " + pf(labels(p).contains("HUD - OFF") && !LmHud.enabled(player)));
        }
        LmHud.set(player, true);
    }

    private static int clockSecs(String text) {
        try {
            String c = text.substring(text.lastIndexOf(' ') + 1);
            String[] mmss = c.split(":");
            return Integer.parseInt(mmss[0]) * 60 + Integer.parseInt(mmss[1]);
        } catch (Exception e) {
            return -100;
        }
    }

    // ---- 4.7.75 HUD part 2 (test only) ----
    private static com.dbzlegacy.adaptivedifficulty.rival.RivalChallenge probeChallenge(long now) {
        var ch = new com.dbzlegacy.adaptivedifficulty.rival.RivalChallenge();
        ch.nameA = "Kaito";
        ch.nameB = "Mira";
        ch.damageA = 120;
        ch.damageB = 98;
        ch.status = com.dbzlegacy.adaptivedifficulty.rival.RivalChallenge.Phase.ACTIVE;
        ch.durationMs = 120_000L;
        ch.endsAt = now + 83_000L;
        return ch;
    }

    private static com.dbzlegacy.adaptivedifficulty.sparring.SparPlayerRuntime probeSparRuntime(long now) {
        var rt = new com.dbzlegacy.adaptivedifficulty.sparring.SparPlayerRuntime();
        rt.active = true;
        rt.perfectSeenAt = now;
        rt.combo = 5;
        rt.graceUntil = now + 6_000L;
        rt.graceReason = "too far apart";
        return rt;
    }

    /**
     * 4.7.75: the repeated chat moved to the HUD. Same words as the chat lines; a HUD player gets the element, a bridge
     * player keeps the chat (coin toast, Potential hint); the fight pill and spar chip lines are built from the same
     * strings as the chat lines; the boost chip follows a running boost; the healing caps sit on Character Services.
     */
    private void hud75Script() {
        var srv = player.m_20194_();
        boolean hud = LmHud.active(player);
        long now = System.currentTimeMillis();
        var ch = probeChallenge(now);
        List<String> fl = com.dbzlegacy.adaptivedifficulty.rival.RivalChallengeManager.fightLines(ch, now, true);
        note("HUD2 fight pill: the chat score line, the clock and the range warning: " + pf(fl.size() == 3
                && fl.get(0).equals(com.dbzlegacy.adaptivedifficulty.rival.RivalChallengeManager.scoreBody(ch))
                && UltraStyle.plain(fl.get(1)).equals("1:23 left")
                && UltraStyle.plain(fl.get(2)).equals("Too far apart. Return within range.")) + " | " + fl);
        var rt = probeSparRuntime(now);
        List<String> chip = com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.sparChipLines(rt, now);
        String chipText = UltraStyle.plain(String.join(" | ", chip));
        note("HUD2 spar chip: Perfect Training, combo, recover countdown: " + pf(chipText.contains("Perfect Training active")
                && chipText.contains("Combo x5") && chipText.contains("Recover within 6s")) + " | " + chipText);
        // Coin toast: two drops inside the window are one summed toast (HUD), or nothing on the HUD (bridge).
        try {
            Class<?> coins = com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.class;
            List<LmHud.Sent> c = LmHud.capture(() -> {
                try {
                    call(coins, "coinToast", new Class<?>[] {ServerPlayer.class, long.class}, player, 25L);
                    call(coins, "coinToast", new Class<?>[] {ServerPlayer.class, long.class}, player, 25L);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            var cp = of(c, LmHud.COINS);
            String sum = com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.formatExactCost(50L);
            if (hud) {
                note("HUD2 coin toast sums drops in its 5 s window: " + pf(cp.size() == 2
                        && UltraStyle.plain(cp.get(1).lines().get(0)).replace('×', 'x')
                                .contains(UltraStyle.plain(sum).replace('×', 'x'))
                        && !UltraStyle.plain(cp.get(1).lines().get(0)).contains("Ancient Ancient")
                        && cp.get(1).durationMs() == 5000) + " | " + (cp.isEmpty() ? "none" : cp.get(1).lines()));
            } else {
                note("HUD2 bridge: coin drops stay chat lines: " + pf(cp.isEmpty()));
            }
        } catch (Throwable t) {
            note("HUD2 coin toast ERROR " + t + " FAIL");
        }
        // Potential hint rides the meter instead of a subtitle.
        try {
            Class<?> pot = com.dbzlegacy.adaptivedifficulty.progression.skills.PotentialProgression.class;
            List<LmHud.Sent> ps = LmHud.capture(() -> {
                try {
                    call(pot, "potentialSubtitle", new Class<?>[] {ServerPlayer.class, String.class,
                            net.minecraft.ChatFormatting.class, long.class}, player,
                            "Switch training methods to continue progressing.", net.minecraft.ChatFormatting.YELLOW, 0L);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            var pp = of(ps, LmHud.POTENTIAL);
            if (hud) {
                note("HUD2 Potential hint on the meter, not a subtitle: " + pf(pp.size() == 1
                        && UltraStyle.plain(String.join(" ", pp.get(0).lines())).contains("Switch training methods")));
            } else {
                note("HUD2 bridge: Potential hint stays a subtitle: " + pf(pp.isEmpty()));
            }
        } catch (Throwable t) {
            note("HUD2 Potential ERROR " + t + " FAIL");
        }
        // Boost chip follows a running boost and goes when it ends.
        String started = com.dbzlegacy.adaptivedifficulty.progression.tp.GlobalTpBoost.startBoostMinutes(null, 1.5, 30, "Probe");
        if (com.dbzlegacy.adaptivedifficulty.progression.tp.GlobalTpBoost.isActive()) {
            List<LmHud.Sent> b = LmHud.capture(() -> com.dbzlegacy.adaptivedifficulty.progression.Momentum.boostHudPulse(srv, 10));
            var bp = of(b, LmHud.BOOST);
            com.dbzlegacy.adaptivedifficulty.progression.tp.GlobalTpBoost.endBoost(false);
            List<LmHud.Sent> b2 = LmHud.capture(() -> com.dbzlegacy.adaptivedifficulty.progression.Momentum.boostHudPulse(srv, 10));
            if (hud) {
                note("HUD2 boost chip while a TP boost runs, gone after: " + pf(bp.size() == 1
                        && UltraStyle.plain(bp.get(0).lines().get(0)).startsWith("TP x1.5")
                        && UltraStyle.plain(bp.get(0).lines().get(0)).endsWith("30m left")
                        && of(b2, LmHud.BOOST).stream().anyMatch(x -> !x.show()))
                        + " | " + (bp.isEmpty() ? "none" : bp.get(0).lines()));
            } else {
                note("HUD2 bridge: no boost chip: " + pf(bp.isEmpty()));
            }
        } else {
            note("HUD2 boost chip NAV SKIP (TP boost off in the test config): " + UltraStyle.plain(started));
        }
        for (GuiOpenPacket p : keep("character home healing", () -> UltraGui.open(player, "character", "main"))) {
            String sum = com.dbzlegacy.adaptivedifficulty.kube.hotpath.HealCap.summary(player);
            note("HUD2 healing caps on Character Services Home, not at login: " + pf(sum == null || has(p, "Healing:"))
                    + " | " + sum);
        }
        LmHud.hideAll(player);
    }

    // ---- 4.7.78 Difficulty rework, Sparring/Dojo split, founding (test only) ----
    private int bannersOf(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (net.minecraft.world.item.ItemStack st : p.m_150109_().f_35974_) {
            if (!st.m_41619_() && st.m_41720_() == item) {
                n += st.m_41613_();
            }
        }
        return n;
    }

    private void v78Script() {
        java.util.UUID u = player.m_20148_();
        // A: the coin toast's accent is the skin's gold, resolved on the client per skin.
        var skins = com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.class;
        note("V78 coin toast gold per skin (Saiyan, Namek, Zeno, Obsidian): " + pf(
                com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.currencyGold(com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.SAIYAN_ROYAL) == com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.SAIYAN_ROYAL.trim()
                && com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.currencyGold(com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.NAMEK_SERENITY) == com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.NAMEK_SERENITY.trim()
                && com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.currencyGold(com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.ZENO_OMNI) == com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.ZENO_OMNI.trim()
                && com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.currencyGold(com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.OBSIDIAN_GOLD) == com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.OBSIDIAN_GOLD.trim()));
        if (LmHud.active(player)) {
            try {
                List<LmHud.Sent> c = LmHud.capture(() -> {
                    try {
                        call(com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.class, "coinToast",
                                new Class<?>[] {ServerPlayer.class, long.class}, player, 10L);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
                var cp = of(c, LmHud.COINS);
                note("V78 coin toast sends the currency accent (no fixed colour): " + pf(!cp.isEmpty()
                        && LmHud.ACCENT_CURRENCY.equals(cp.get(0).accent())));
            } catch (Throwable t) {
                note("V78 coin toast ERROR " + t + " FAIL");
            }
        }
        // B: search typed in More lands on the results.
        for (GuiOpenPacket p : keep("v78 more", () -> UltraGui.open(player, "hub", "more"))) {
            note("V78 More has the search row: " + pf(!com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesNav(player)
                    || (p.inputsJson() != null && p.inputsJson().contains("hub_search"))));
        }
        JsonObject sa = new JsonObject();
        sa.addProperty("screen", "hub");
        sa.addProperty("page", "more");
        sa.addProperty("value", "dojo");
        for (GuiOpenPacket p : keep("v78 search from more", () -> UltraGuiActions.dispatch(player, "hub", "do:hub_search", sa.toString()))) {
            note("V78 search typed in More lands on the results, on More (4.7.80): " + pf("more".equals(p.pageId()) && has(p, "Results for")));
        }
        UltraHubScreens.searchFor(player, "");
        // C: Difficulty off -> Tiers locked, no rows, no toggle; on -> rows; Home keeps the toggle.
        var data = com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache.data(player);
        boolean wasOn = data.isPersonalEnabled();
        data.setPersonalEnabled(false);
        for (GuiOpenPacket p : keep("v78 tiers off", () -> UltraGui.open(player, "difficulty", "tiers"))) {
            note("V78 Difficulty off: Tiers shows no rows and no toggle, a way back to Home: " + pf(has(p, "Difficulty is off")
                    && !labels(p).contains("[T1") && !labels(p).contains("Difficulty - OFF") && labels(p).contains("Go to Difficulty Home")));
        }
        for (GuiOpenPacket p : keep("v78 home off", () -> UltraGui.open(player, "difficulty", "main"))) {
            note("V78 Difficulty Home turns it on (toggle), no Change tier, no Rival teams: " + pf(labels(p).contains("Difficulty - OFF")
                    && !labels(p).contains("Change tier") && !labels(p).contains("Rival teams")
                    && labels(p).contains("Replay the difficulty guide")));
        }
        data.setPersonalEnabled(true);
        for (GuiOpenPacket p : keep("v78 tiers on", () -> UltraGui.open(player, "difficulty", "tiers"))) {
            note("V78 Difficulty on: Tiers lists the tiers: " + pf(labels(p).contains("[T1")));
            boolean active = data.getActiveTier() > 0;
            note("V78 Clear active tier sits in the Tiers list when a tier is active: " + pf(!active
                    || labels(p).contains("Clear active tier")) + (active ? "" : " (no active tier on the probe account)"));
        }
        for (GuiOpenPacket p : keep("v78 home on", () -> UltraGui.open(player, "difficulty", "main"))) {
            note("V78 Clear active tier is gone from Home: " + pf(!labels(p).contains("Clear active tier")));
        }
        data.setPersonalEnabled(wasOn);
        for (GuiOpenPacket p : keep("v78 guide step 3", () -> UltraGui.open(player, "difficulty", "guide_your_tier"))) {
            String l = labels(p);
            note("V78 guide Step 3 shows only Got it (plus the guide footer): " + pf(l.contains("Got it")
                    && !l.contains("Titles") && !l.contains("Difficulty - ") && !l.contains("Clear active tier")
                    && !l.contains("Server tools") && !l.contains("Coin messages") && !l.contains("Rival teams")) + " | " + l);
        }
        // C: rival teams gone; a grouped player scales personally only.
        for (GuiOpenPacket p : keep("v78 old team link", () -> UltraGui.open(player, "difficulty", "team"))) {
            note("V78 no Rival teams page or route (old link opens Difficulty): " + pf(!has(p, "Rival teams")
                    && PageRegistry.find("difficulty", "team") == null));
        }
        var snap = com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache.refresh(player);
        note("V78 scaling is personal only (no team threshold or contribution): " + pf(snap != null
                && snap.teamThresholdBonus == 0L && snap.teamContribution == 0L));
        // D: Sparring Home / Leaderboard; Dojo its own system; old links land on Dojo pages.
        for (GuiOpenPacket p : keep("v78 spar home", () -> UltraGui.open(player, "spar", "main"))) {
            String l = labels(p);
            note("V78 Sparring Home: no board button, Weights and TP Chat, tabs Home/Leaderboard/Spar Board: "
                    + pf(!l.contains("Open the Spar Board") && l.contains("Weights") && l.contains("TP Chat")
                    && !l.contains("Hall of Infamy") && p.buttonsJson().contains("tab:leaderboard")
                    && !p.buttonsJson().contains("tab:dojo")) + " | " + l);
        }
        for (GuiOpenPacket p : keep("v78 spar leaderboard", () -> UltraGui.open(player, "spar", "leaderboard"))) {
            note("V78 Sparring Leaderboard tab holds Hall of Infamy and Your stats: " + pf(labels(p).contains("Hall of Infamy")
                    && labels(p).contains("Your stats")));
        }
        for (GuiOpenPacket p : keep("v78 dojo home", () -> UltraGui.open(player, "dojo", "home"))) {
            note("V78 Dojo opens as its own system, tabs Home/Upgrades/Wars: " + pf("dojo".equals(p.screenId())
                    && p.buttonsJson().contains("tab:home") && p.buttonsJson().contains("tab:dojo_upgrades")
                    && p.buttonsJson().contains("tab:dojo_wars")));
        }
        String[][] old = {{"dojo_upgrades", "dojo_upgrades"}, {"dojo_style", "dojo_style"}, {"dojo_wars", "dojo_wars"},
                {"mentor", "mentor"}, {"dojo_roster", "dojo_roster"}, {"dojo_list", "dojo_list"}, {"dojo", "home"}};
        for (String[] o : old) {
            for (GuiOpenPacket p : keep("v78 old link " + o[0], () -> UltraGui.open(player, "spar", o[0]))) {
                note("V78 old Sparring link spar|" + o[0] + " lands on dojo|" + o[1] + ": " + pf("dojo".equals(p.screenId())
                        && o[1].equals(p.pageId())) + " | got " + p.screenId() + "|" + p.pageId());
            }
        }
        // D: founding on a stand-in with no dojo: name, style, banner; no banner refuses; the banner item stays.
        ServerPlayer f = dojoStandIn("Founder78");
        com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.get().dojos.remove(f.m_20148_().toString());
        var gui = com.dbzlegacy.adaptivedifficulty.gui.ultra.DojoFounding.class;
        JsonObject named = new JsonObject();
        named.addProperty("value", "Probe Hall");
        List<GuiOpenPacket> st1 = UltraScreenSpec.captureSends(() ->
                com.dbzlegacy.adaptivedifficulty.gui.ultra.DojoFounding.handle(f, "dojo_found_name", "", named));
        note("V78 founding step 1 takes the typed name, then asks for a style: " + pf(!st1.isEmpty()
                && "found_style".equals(st1.get(0).pageId()) && has(st1.get(0), "Probe Hall")));
        List<GuiOpenPacket> st2 = UltraScreenSpec.captureSends(() ->
                com.dbzlegacy.adaptivedifficulty.gui.ultra.DojoFounding.handle(f, "dojo_found_style", "KI", new JsonObject()));
        boolean noBanner = !st2.isEmpty() && "found_banner".equals(st2.get(0).pageId()) && has(st2.get(0), "You have no banner")
                && !labels(st2.get(0)).contains("Use:");
        note("V78 founding without a banner says to make one and offers nothing to found: " + pf(noBanner));
        var bannerItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                new net.minecraft.resources.ResourceLocation("minecraft", "red_banner"));
        var stack = new net.minecraft.world.item.ItemStack(bannerItem);
        var be = stack.m_41698_("BlockEntityTag");
        var pats = new net.minecraft.nbt.ListTag();
        var pt = new net.minecraft.nbt.CompoundTag();
        pt.m_128359_("Pattern", "cr");
        pt.m_128405_("Color", 4);
        pats.add(pt);
        be.m_128365_("Patterns", pats);
        f.m_150109_().m_36054_(stack);
        int before = bannersOf(f, bannerItem);
        List<GuiOpenPacket> st3 = UltraScreenSpec.captureSends(() ->
                com.dbzlegacy.adaptivedifficulty.gui.ultra.DojoFounding.handle(f, "dojo_found_style", "KI", new JsonObject()));
        String src = "";
        if (!st3.isEmpty()) {
            for (JsonElement el : JsonParser.parseString(st3.get(0).buttonsJson()).getAsJsonArray()) {
                String id = el.getAsJsonObject().has("id") ? el.getAsJsonObject().get("id").getAsString() : "";
                int k = id.indexOf("do:dojo_found_banner:");
                if (k >= 0) {
                    src = id.substring(k + "do:dojo_found_banner:".length());
                }
            }
        }
        final String source = src;
        List<GuiOpenPacket> st4 = UltraScreenSpec.captureSends(() ->
                com.dbzlegacy.adaptivedifficulty.gui.ultra.DojoFounding.handle(f, "dojo_found_banner", source, new JsonObject()));
        note("V78 founding lists the carried banner and confirms name, style and banner: " + pf(!source.isEmpty() && !st4.isEmpty()
                && "found_confirm".equals(st4.get(0).pageId()) && has(st4.get(0), "Probe Hall") && has(st4.get(0), "Red, 1 pattern")));
        UltraScreenSpec.captureSends(() ->
                com.dbzlegacy.adaptivedifficulty.gui.ultra.DojoFounding.handle(f, "dojo_found_confirm", "", new JsonObject()));
        var made = com.dbzlegacy.adaptivedifficulty.dojo.DojoService.dojoOf(f);
        note("V78 founding makes the dojo with the typed name, the style and the banner; the banner item is kept: "
                + pf(made != null && "Probe Hall".equals(made.name) && "KI".equals(made.style) && "red".equals(made.bannerBase)
                && made.bannerPatterns.size() == 1 && bannersOf(f, bannerItem) == before && made.identityChangedAt == 0L)
                + " | " + (made == null ? "no dojo" : made.name + "/" + made.style + "/" + made.bannerBase));
        if (made != null) {
            List<GuiOpenPacket> home = UltraScreenSpec.captureSends(() ->
                    com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraDojoScreens.openSystem(f, "home"));
            note("V78 Dojo Home shows name, style and banner: " + pf(!home.isEmpty() && has(home.get(0), "Probe Hall")
                    && has(home.get(0), "Ki") && has(home.get(0), "Red, 1 pattern")));
            // Amendment: changes cost Dojo Points and share one 12-hour cooldown.
            made.points = 5;
            String poor = com.dbzlegacy.adaptivedifficulty.dojo.DojoService.rename(f, "Probe Keep");
            note("V78 rename refuses without 20 Dojo Points: " + pf(poor.contains("costs 20") && "Probe Hall".equals(made.name)));
            made.points = 100;
            String ok = com.dbzlegacy.adaptivedifficulty.dojo.DojoService.rename(f, "Probe Keep");
            note("V78 rename costs 20 and starts the cooldown: " + pf("Probe Keep".equals(made.name) && made.points == 80
                    && made.identityChangedAt > 0L) + " | " + ok);
            String again = com.dbzlegacy.adaptivedifficulty.dojo.DojoService.setStyle(f, "MELEE");
            note("V78 style change refused during the 12-hour cooldown: " + pf(again.contains("can change again")
                    && "KI".equals(made.style) && made.points == 80));
            List<GuiOpenPacket> cdHome = UltraScreenSpec.captureSends(() ->
                    com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraDojoScreens.openSystem(f, "home"));
            note("V78 Dojo Home shows the changes dim with the time left: " + pf(!cdHome.isEmpty()
                    && labels(cdHome.get(0)).contains("Rename in")));
            com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.get().dojos.remove(f.m_20148_().toString());
            com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.get().markDirty();
        }
    }

    // ---- 4.7.80 hub Goals, search on More, board founding, wager sub-menu, HUD no-doubling (test only) ----
    private void v80Script() {
        java.util.UUID u = player.m_20148_();
        // 1: hub tabs Home | Goals | More, no Search tab anywhere; search lives on More.
        for (String pg : new String[] {"main", "goals", "more"}) {
            for (GuiOpenPacket p : keep("v80 hub " + pg, () -> UltraGui.open(player, "hub", pg))) {
                String b = p.buttonsJson();
                note("V80 hub " + pg + " tabs Home/Goals/More, no Search tab: " + pf(b.contains("tab:home") && b.contains("tab:goals")
                        && b.contains("tab:more") && !b.contains("tab:search")));
            }
        }
        for (GuiOpenPacket p : keep("v80 old search link", () -> UltraGui.open(player, "hub", "search"))) {
            note("V80 the old Search page id opens More: " + pf("more".equals(p.pageId())));
        }
        UltraHubScreens.searchFor(player, "");
        JsonObject sa = new JsonObject();
        sa.addProperty("screen", "hub");
        sa.addProperty("page", "more");
        sa.addProperty("value", "wager");
        for (GuiOpenPacket p : keep("v80 search on more", () -> UltraGuiActions.dispatch(player, "hub", "do:hub_search", sa.toString()))) {
            note("V80 search typed in More lands on the results on More: " + pf("more".equals(p.pageId()) && has(p, "Results for") && has(p, "wager")
                    && p.buttonsJson().contains("go:spar|board_wager")) + " | " + labels(p));
        }
        UltraHubScreens.searchFor(player, "");
        for (GuiOpenPacket p : keep("v80 more recents", () -> UltraGui.open(player, "hub", "more"))) {
            note("V80 More shows the 5 recents under an empty search: " + pf(p.buttonsJson().contains("do:hub_search_recent:0")
                    || !com.dbzlegacy.adaptivedifficulty.net.gui.UltraClientFeatures.hasTextInput(player)));
        }
        // 1: Goals: this week's three goals, from three systems, with progress; a hook counts toward one.
        var goals = com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals.thisWeek();
        note("V80 Goals: three goals this week from three systems: " + pf(goals.size() == 3
                && goals.stream().map(com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals.Goal::system).distinct().count() == 3)
                + " | " + goals.stream().map(com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals.Goal::id).toList());
        if (!goals.isEmpty()) {
            var g = goals.get(0);
            long before = com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals.progress(u, g.id());
            com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals.note(u, g.id(), g.minutes() ? 60_000L : 1L);
            long after = com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals.progress(u, g.id());
            note("V80 Goals: progress counts toward this week's goal: " + pf(after > before || after >= g.target()));
            for (GuiOpenPacket p : keep("v80 goals", () -> UltraGui.open(player, "hub", "goals"))) {
                boolean links = true;
                for (var x : goals) {
                    links &= p.buttonsJson().contains(x.go()) && has(p, x.title());
                }
                note("V80 Goals board shows each goal, its progress bar and its Go to button: " + pf(links && has(p, "[#")
                        || links && has(p, "[-")) + " | " + labels(p));
            }
        }
        // 2 + 3: the Wager sub-menu (kind first, then the amount) and the accept page that says the stake.
        boolean hadDojo = dojoOf(player) != null;
        if (!hadDojo) {
            com.dbzlegacy.adaptivedifficulty.dojo.DojoService.found(player);
        }
        JsonObject sp = new JsonObject();
        sp.addProperty("screen", "spar");
        sp.addProperty("page", "sparboard");
        for (GuiOpenPacket p : keep("v80 board", () -> UltraGui.open(player, "spar", "sparboard"))) {
            note("V80 board Wager button opens the sub-menu (no cycling): " + pf(p.buttonsJson().contains("page:board_wager")
                    && !p.buttonsJson().contains("do:board_wager\"")));
        }
        for (GuiOpenPacket p : keep("v80 wager menu", () -> UltraGuiActions.dispatch(player, "spar", "do:board_wager", sp.toString()))) {
            note("V80 Wager sub-menu: Ancient Coins, Dojo Points or No wager: " + pf("board_wager".equals(p.pageId())
                    && labels(p).contains("[Ancient Coins]") && labels(p).contains("[Dojo Points]") && labels(p).contains("No wager")
                    && has(p, "stakes exactly the same")) + " | " + labels(p));
        }
        int coinSteps = com.dbzlegacy.adaptivedifficulty.dojo.DojoCatalog.COIN_STEPS.length;
        int steps = com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.wagerSteps();
        for (GuiOpenPacket p : keep("v80 wager coins", () -> UltraGui.open(player, "spar", "board_wager_coins"))) {
            boolean all = true;
            for (int i = 1; i <= coinSteps; i++) {
                all &= p.buttonsJson().contains("do:board_wager_set:" + i + "\"");
            }
            note("V80 Wager coins: every coin step up to 5 netherite, no point steps: " + pf(all
                    && !p.buttonsJson().contains("do:board_wager_set:" + (coinSteps + 1) + "\"")) + " | " + labels(p));
        }
        for (GuiOpenPacket p : keep("v80 wager points", () -> UltraGui.open(player, "spar", "board_wager_points"))) {
            boolean all = true;
            for (int i = coinSteps + 1; i < steps; i++) {
                all &= p.buttonsJson().contains("do:board_wager_set:" + i + "\"");
            }
            note("V80 Wager Dojo Points (master): every point step up to 20: " + pf(all && labels(p).contains("20 Dojo Points")));
        }
        JsonObject wc = new JsonObject();
        wc.addProperty("screen", "spar");
        wc.addProperty("page", "board_wager_coins");
        for (GuiOpenPacket p : keep("v80 wager pick", () -> UltraGuiActions.dispatch(player, "spar", "do:board_wager_set:2", wc.toString()))) {
            String want = com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.compactCost(
                    com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.wagerAmount(2));
            note("V80 picking an amount sets the next post's wager and returns to the board: " + pf("sparboard".equals(p.pageId())
                    && com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.draft(u).wager() == 2 && labels(p).contains("Wager - " + want))
                    + " | " + labels(p));
        }
        for (GuiOpenPacket p : keep("v80 wager bad", () -> UltraGuiActions.dispatch(player, "spar", "do:board_wager_set:99", wc.toString()))) {
            note("V80 an unknown wager step changes nothing and says why: " + pf(com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.draft(u).wager() == 2
                    && has(p, "on the list")));
        }
        ServerPlayer w = dojoStandIn("Wager80");
        com.dbzlegacy.adaptivedifficulty.dojo.DojoService.found(w);
        com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.grantExact(w,
                com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.CoinKind.NETHERITE, 1);
        com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.setDraft(w.m_20148_(), false, 1);
        String wp = com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(w, "board_post", "");
        var post = com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.postOf(w.m_20148_());
        if (post == null) {
            note("V80 wager accept page NAV SKIP (the stand-in could not post: " + wp + ")");
        } else {
            String stake = com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.wagerText(post.wagerKind(), post.wager());
            for (GuiOpenPacket p : keep("v80 board listing", () -> UltraGui.open(player, "spar", "sparboard"))) {
                note("V80 the listing shows the wager and Accept asks first: " + pf(has(p, "Emerald")
                        && p.buttonsJson().contains("page:board_accept:" + w.m_20148_())) + " | " + labels(p));
            }
            for (GuiOpenPacket p : keep("v80 accept ask", () -> UltraGui.open(player, "spar", "board_accept:" + w.m_20148_()))) {
                note("V80 the accept page says the stake (the same kind and amount) before the click: " + pf(has(p, "You stake")
                        && has(p, "Emerald (the same as Wager80)") && has(p, "The winner takes both") && has(p, "refunds both")
                        && (p.buttonsJson().contains("do:board_accept:" + w.m_20148_()) || labels(p).contains("Accept and stake")))
                        + " | " + labels(p));
            }
            com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.apply(w, "board_unpost", "");
        }
        com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.get().dojos.remove(w.m_20148_().toString());
        com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar.setDraft(u, false, 0);
        // 2: no founding from the board (no dojo state).
        var mine = dojoOf(player);
        if (mine != null) {
            com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.get().dojos.remove(mine.masterUuid);
        }
        for (GuiOpenPacket p : keep("v80 board no dojo", () -> UltraGui.open(player, "spar", "sparboard"))) {
            note("V80 Spar Board without a dojo: Find a dojo and Open the Dojo, no Found a dojo: " + pf(labels(p).contains("[Find a dojo]")
                    && labels(p).contains("[Open the Dojo]") && !has(p, "Found a dojo") && !p.buttonsJson().contains("dojo_found")));
        }
        JsonObject bd = new JsonObject();
        bd.addProperty("screen", "spar");
        bd.addProperty("page", "sparboard");
        for (GuiOpenPacket p : keep("v80 open the dojo", () -> UltraGuiActions.dispatch(player, "spar", "go:dojo|home", bd.toString()))) {
            note("V80 Open the Dojo lands on Dojo Home, where founding lives: " + pf("dojo".equals(p.screenId())
                    && "home".equals(p.pageId())) + " | " + p.screenId() + "|" + p.pageId());
        }
        if (mine != null) {
            com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.get().dojos.put(mine.masterUuid, mine);
        }
        if (!hadDojo && dojoOf(player) != null) {
            com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.get().dojos.remove(dojoOf(player).masterUuid);
        }
        com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.get().markDirty();
        // 4: every HUD element once, on the HUD only, for a HUD player; the vanilla version for everyone else.
        v80HudMatrix();
    }

    static final String[] HUD_ELEMENTS = {LmHud.UNDERDOG, LmHud.INSTINCT, LmHud.TP, LmHud.PENALTY, LmHud.MOMENTUM,
            LmHud.FIGHT, LmHud.SPAR, LmHud.POTENTIAL, LmHud.COINS, LmHud.ELITE, LmHud.BOOST};

    /** Fire one element through the code that sends it in game (both versions: HUD and chat / boss bar / subtitle). */
    private void trigger80(String el, long now) throws Exception {
        var srv = player.m_20194_();
        switch (el) {
            case LmHud.UNDERDOG -> {
                groundsLink(true);
                com.dbzlegacy.adaptivedifficulty.rival.ProvingGroundsBar.tick(player);
            }
            case LmHud.INSTINCT -> {
                String body = instinctBody();
                com.dbzlegacy.adaptivedifficulty.rival.RivalInstinct.deliver(player, "charge",
                        com.dbzlegacy.adaptivedifficulty.util.LmChat.fail("Instinct", body), body, PROBE_RIVAL);
            }
            case LmHud.TP -> com.dbzlegacy.adaptivedifficulty.util.TpBossNotify.show(player, "+7 TP (probe)",
                    net.minecraft.ChatFormatting.GREEN, "", 0L);
            case LmHud.PENALTY -> com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.onDeath(player);
            case LmHud.MOMENTUM -> {
                com.dbzlegacy.adaptivedifficulty.progression.Momentum.bank(player);
                com.dbzlegacy.adaptivedifficulty.progression.Momentum.markFight(player);
                com.dbzlegacy.adaptivedifficulty.progression.Momentum.hudPulse(srv, 20);
            }
            case LmHud.FIGHT -> {
                var ch = probeChallenge(now);
                ch.a = player.m_20148_();
                ch.b = PROBE_RIVAL;
                com.dbzlegacy.adaptivedifficulty.rival.RivalChallengeManager.announceStart(srv, ch);
                com.dbzlegacy.adaptivedifficulty.rival.RivalChallengeManager.fightHud(player, ch, now, false);
                com.dbzlegacy.adaptivedifficulty.rival.RivalChallengeManager.chatUnlessHud(player,
                        com.dbzlegacy.adaptivedifficulty.rival.RivalChallengeManager.scoreLine(ch));
            }
            case LmHud.SPAR -> {
                var rt = probeSparRuntime(now);
                call(com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.class, "sparHud", new Class<?>[] {ServerPlayer.class,
                        ServerPlayer.class, com.dbzlegacy.adaptivedifficulty.sparring.SparPlayerRuntime.class, long.class},
                        player, null, rt, now);
                rt.messageNext = 0L;
                com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.breakCombo(player, rt, "probe");
            }
            case LmHud.POTENTIAL -> call(com.dbzlegacy.adaptivedifficulty.progression.skills.PotentialProgression.class,
                    "potentialSubtitle", new Class<?>[] {ServerPlayer.class, String.class, net.minecraft.ChatFormatting.class,
                            long.class}, player, "Switch training methods to continue progressing.",
                    net.minecraft.ChatFormatting.YELLOW, 0L);
            case LmHud.COINS -> com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.notifyGrant(player,
                    new com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.KillLoot(
                            new com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.Drop(
                                    com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.CoinKind.IRON, 2), null));
            case LmHud.ELITE -> {
                com.dbzlegacy.adaptivedifficulty.title.TitleSense.clear(player.m_20148_());
                com.dbzlegacy.adaptivedifficulty.title.TitleSense.deliver(player,
                        com.dbzlegacy.adaptivedifficulty.title.DifficultyTitle.ELITE_HUNTER, true, false, now);
            }
            case LmHud.BOOST -> {
                com.dbzlegacy.adaptivedifficulty.progression.tp.GlobalTpBoost.startBoostMinutes(null, 1.5, 30, "Probe");
                com.dbzlegacy.adaptivedifficulty.progression.Momentum.boostHudPulse(srv, 10);
            }
            default -> throw new IllegalArgumentException(el);
        }
    }

    private void cleanup80(String el) {
        var srv = player.m_20194_();
        switch (el) {
            case LmHud.UNDERDOG -> groundsLink(false);
            case LmHud.PENALTY -> {
                com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess.putLong(player,
                        com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.KEY, System.currentTimeMillis() - 1000L);
                com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.pulse(srv, 20);
            }
            case LmHud.MOMENTUM -> {
                com.dbzlegacy.adaptivedifficulty.progression.Momentum.clear(player);
                com.dbzlegacy.adaptivedifficulty.progression.Momentum.hudPulse(srv, 20);
            }
            case LmHud.BOOST -> com.dbzlegacy.adaptivedifficulty.progression.tp.GlobalTpBoost.endBoost(false);
            default -> {
            }
        }
        LmHud.hideAll(player);
    }

    /**
     * 4.7.80 no doubling: for each of the 11 elements, through the code that sends it in game, a HUD player gets it
     * once on the HUD and nothing in chat, as a boss bar or as a subtitle; a HUD-off player (and a bridge client) gets
     * the chat / boss bar / subtitle version as before and nothing on the HUD. The Momentum meter has no vanilla
     * version (the Difficulty menu shows the bank), so HUD off it sends nothing at all. The boost headline
     * ("GLOBAL TP BOOST ACTIVATED!") is an announcement, not the chip: a HUD player keeps it, without the multiplier
     * and time the chip shows.
     */
    private void v80HudMatrix() {
        java.util.UUID u = player.m_20148_();
        boolean takes = com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesHud(player);
        boolean wasOn = LmHud.enabled(player);
        boolean momOn = com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig.get().momentumEnabled;
        int checked = 0;
        int passed = 0;
        for (boolean on : new boolean[] {true, false}) {
            LmHud.set(player, on);
            boolean hudMode = on && takes;
            String mode = hudMode ? "HUD on" : takes ? "HUD off" : on ? "bridge" : "bridge, HUD off";
            for (String el : HUD_ELEMENTS) {
                LmHud.hideAll(player);
                long now = System.currentTimeMillis();
                LmHud.Both b;
                try {
                    b = LmHud.captureBoth(() -> {
                        try {
                            trigger80(el, now);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    });
                } catch (Throwable t) {
                    note("V80 HUD once " + mode + " " + el + ": ERROR " + t + " FAIL");
                    cleanup80(el);
                    checked++;
                    continue;
                }
                cleanup80(el);
                long hudN = b.hud().stream().filter(x -> x.player().equals(u) && x.packet().element().equals(el)
                        && x.packet().show()).count();
                List<String> van = new ArrayList<>();
                for (LmHud.Vanilla v : b.vanilla()) {
                    if (v.player().equals(u)) {
                        van.add(v.channel() + ":" + UltraStyle.plain(v.text()));
                    }
                }
                boolean skip = el.equals(LmHud.MOMENTUM) && !momOn
                        || el.equals(LmHud.BOOST) && !com.dbzlegacy.adaptivedifficulty.progression.ProgressionConfig.globalTpBoost();
                if (skip) {
                    note("V80 HUD once " + mode + " " + el + ": NAV SKIP (off in the test config)");
                    continue;
                }
                boolean ok;
                if (hudMode) {
                    List<String> doubled = new ArrayList<>(van);
                    if (el.equals(LmHud.BOOST)) {
                        doubled.removeIf(t -> !t.contains("1.5") && !t.contains("30") && !t.toLowerCase(java.util.Locale.ROOT).contains("remaining"));
                    }
                    ok = hudN >= 1 && doubled.isEmpty();
                } else {
                    boolean vanillaExpected = !el.equals(LmHud.MOMENTUM);
                    ok = hudN == 0 && (vanillaExpected ? !van.isEmpty() : van.isEmpty());
                }
                checked++;
                if (ok) {
                    passed++;
                }
                note("V80 HUD once " + mode + " " + el + ": " + pf(ok) + " | hud=" + hudN + " vanilla=" + van);
            }
        }
        LmHud.set(player, wasOn);
        note("V80 HUD no-doubling matrix: " + passed + " of " + checked + " element checks pass (" + (takes ? "LM client" : "bridge")
                + "): " + pf(passed == checked && checked > 0));
    }

    /** Put every HUD element up (the HUD on the LM client; boss bars and chat on a bridge stage). */
    private void hudShowAll(int tpMs) {
        var srv = player.m_20194_();
        boolean hud = LmHud.active(player);
        // The real grounds check shows the Underdog (pill or bar) within a second.
        groundsLink(true);
        com.dbzlegacy.adaptivedifficulty.rival.ProvingGroundsBar.tick(player);
        if (hud) {
            LmHud.show(player, LmHud.TP, List.of("+12 TP (Rival win)"), 1f, "#55ff55", "", tpMs, true);
        } else {
            hudBar("+12 TP (Rival win)", net.minecraft.world.BossEvent.BossBarColor.GREEN, 1f);
        }
        String body = instinctBody();
        com.dbzlegacy.adaptivedifficulty.rival.RivalInstinct.deliver(player, "charge",
                com.dbzlegacy.adaptivedifficulty.util.LmChat.fail("Instinct", body), body, PROBE_RIVAL);
        String arrive = UltraStyle.color("e", "Mira") + " " + UltraStyle.color("7", "arrived (24m) - feels stronger")
                + " " + UltraStyle.color("8", "[Nemesis]");
        com.dbzlegacy.adaptivedifficulty.rival.RivalInstinct.deliver(player, "arrive",
                com.dbzlegacy.adaptivedifficulty.util.LmChat.note("Instinct", arrive), arrive, PROBE_RIVAL);
        com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess.putLong(player,
                com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.KEY, System.currentTimeMillis() + 552_000L);
        com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.pulse(srv, 20);
        com.dbzlegacy.adaptivedifficulty.progression.Momentum.bank(player);
        com.dbzlegacy.adaptivedifficulty.progression.Momentum.bank(player);
        com.dbzlegacy.adaptivedifficulty.progression.Momentum.bank(player);
        com.dbzlegacy.adaptivedifficulty.progression.Momentum.bank(player);
        com.dbzlegacy.adaptivedifficulty.progression.Momentum.markFight(player);
        com.dbzlegacy.adaptivedifficulty.progression.Momentum.hudPulse(srv, 20);
        // 4.7.75 elements: the same strings the game sends, shown the way the game shows them.
        long now = System.currentTimeMillis();
        var ch = probeChallenge(now);
        var rt = probeSparRuntime(now);
        String eliteChip = UltraStyle.color("e", "\u2726 Elite nearby");
        if (hud) {
            LmHud.show(player, LmHud.FIGHT, com.dbzlegacy.adaptivedifficulty.rival.RivalChallengeManager.fightLines(ch, now, false),
                    0.69f, LmHud.ACCENT_RIVAL, "open:rival", 0, false);
            LmHud.show(player, LmHud.SPAR, com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.sparChipLines(rt, now),
                    -1f, LmHud.accentOf("spar"), "open:spar", 0, false);
            LmHud.show(player, LmHud.POTENTIAL, List.of(UltraStyle.LABEL + "Potential " + UltraStyle.color("f", "134/200"),
                    UltraStyle.color("e", "Switch training methods to continue progressing.")), 0.67f,
                    LmHud.accentOf("character"), "", tpMs, false);
            LmHud.show(player, LmHud.COINS, List.of(UltraStyle.color("6", "+ "
                    + com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.formatExactCost(340L)) + " "
                    + UltraStyle.LABEL + "Coins"), -1f, LmHud.ACCENT_CURRENCY, "", tpMs, false);
            LmHud.show(player, LmHud.ELITE, List.of(eliteChip), -1f, LmHud.ACCENT_DIFFICULTY, "", 0, false);
            LmHud.show(player, LmHud.BOOST, List.of(UltraStyle.color("a", "TP x1.5") + " " + UltraStyle.LABEL + "- 42m left"),
                    -1f, LmHud.accentOf("prestige"), "open:prestige", 0, false);
        } else {
            for (String line : new String[] {
                    com.dbzlegacy.adaptivedifficulty.util.LmChat.tagged("Challenge",
                            com.dbzlegacy.adaptivedifficulty.rival.RivalChallengeManager.scoreBody(ch)),
                    com.dbzlegacy.adaptivedifficulty.util.LmChat.tagged("Spar", UltraStyle.HEADER + "Perfect Training active"),
                    UltraStyle.color("6", "Dropped 3\u00d7 Silver") + UltraStyle.LABEL + " Ancient Coins",
                    UltraStyle.color("e", "\u2726 Your Elite Hunter title senses a powerful enemy nearby.")}) {
                com.dbzlegacy.adaptivedifficulty.util.DmzRewards.msg(player, line);
            }
        }
    }

    private final List<net.minecraft.server.level.ServerBossEvent> hudBars = new ArrayList<>();

    private void hudBar(String text, net.minecraft.world.BossEvent.BossBarColor color, float progress) {
        var bar = new net.minecraft.server.level.ServerBossEvent(Component.m_237113_(text), color,
                net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
        bar.m_142711_(progress);
        bar.m_6543_(player);
        hudBars.add(bar);
    }

    /** Take everything down again (penalty expired, bank cleared, HUD elements hidden, mock bars removed). */
    private void hudHideAll() {
        var srv = player.m_20194_();
        com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess.putLong(player,
                com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.KEY, System.currentTimeMillis() - 1000L);
        com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.pulse(srv, 20);
        com.dbzlegacy.adaptivedifficulty.progression.Momentum.clear(player);
        com.dbzlegacy.adaptivedifficulty.progression.Momentum.hudPulse(srv, 20);
        groundsLink(false);
        LmHud.hideAll(player);
        for (var bar : hudBars) {
            bar.m_7706_();
        }
        hudBars.clear();
    }

    /** FINAL steps: put the HUD up for the client to record, then take it down. */
    private void hudFinal(String step) {
        try {
            switch (step) {
                case "show" -> {
                    player.m_150110_().f_35934_ = true;
                    player.m_6885_();
                    LmHud.set(player, true);
                    hudShowAll(20_000);
                    hudExpect = LmHud.active(player)
                            ? "underdog,tp,penalty,instinct,momentum,fight,spar,potential,coins,elite,boost" : "";
                    note("FINAL hud show expect=" + (hudExpect.isEmpty() ? "nothing (bridge)" : hudExpect));
                }
                case "hide" -> {
                    hudHideAll();
                    write("uiprobe-script.txt", scriptLog.toString(), false);
                }
                default -> { }
            }
        } catch (Throwable t) {
            note("HUD step " + step + " ERROR " + t);
            LOG.error("hud step", t);
        }
    }

    /** The client's report of what its HUD drew ("uihudrec tag element,element,..."). */
    private void hudReport(String raw) {
        String[] f = raw.split(" ", 3);
        String tag = f.length > 1 ? f[1] : "?";
        String rest = f.length > 2 ? f[2].trim() : "";
        // 4.7.73: "<elements> <layout verdict>" (the client checks bounds, overlaps and the action bar row)
        String got = rest.contains(" ") ? rest.substring(0, rest.indexOf(' ')) : rest;
        String layout = rest.contains(" ") ? rest.substring(rest.indexOf(' ') + 1) : "";
        if (got.equals("-")) {
            got = "";
        }
        if (!layout.isEmpty()) {
            note("HUD LAYOUT " + tag + " on screen, no overlaps, Momentum above the action bar: "
                    + pf(layout.startsWith("ok")) + " | " + layout);
        }
        java.util.Set<String> drawn = new java.util.HashSet<>(List.of(got.isEmpty() ? new String[0] : got.split(",")));
        java.util.Set<String> want = new java.util.HashSet<>(List.of(hudExpect.isEmpty() ? new String[0] : hudExpect.split(",")));
        // 4.7.75: with every element up at once a small window has no room for all of them; the five 4.7.72
        // elements must draw, the rest may be skipped (never overlapped), and nothing unexpected may draw.
        java.util.Set<String> core = new java.util.HashSet<>(List.of("underdog", "tp", "penalty", "instinct", "momentum"));
        java.util.Set<String> skipped = new java.util.TreeSet<>(want);
        skipped.removeAll(drawn);
        boolean ok = want.isEmpty() ? drawn.isEmpty() : drawn.containsAll(core) && want.containsAll(drawn);
        note("HUD CLIENT " + tag + " drew " + (got.isEmpty() ? "nothing" : got) + ": " + pf(ok)
                + (want.isEmpty() ? " (bridge: no HUD, bars and chat as before)" : "")
                + (skipped.isEmpty() ? "" : " | no room at this size: " + skipped));
        write("uiprobe-script.txt", scriptLog.toString(), false);
    }

    /** Mock (-Duiprobe.mockset=hud): the HUD on, then the same moment as boss bars and chat (HUD off). */
    private boolean mockHudTick() {
        if (mockHudWaiting) {
            if (!advance) {
                return true;
            }
            advance = false;
            mockHudWaiting = false;
        }
        switch (mockHudStep++) {
            case 0 -> {
                LmHud.set(player, true);
                hudShowAll(60_000);
                note("MOCK hud on");
                say("uihudshot hud-on");
                mockHudWaiting = true;
            }
            case 1 -> {
                hudHideAll();
                LmHud.set(player, false);
                hudShowAll(60_000);
                note("MOCK hud off (boss bars and chat)");
                say("uihudshot hud-off");
                mockHudWaiting = true;
            }
            case 2 -> {
                hudHideAll();
                LmHud.set(player, true);
                return false;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---- 4.7.68 menu mockups (-Duiprobe.mock=true, test only) ----
    // The proposed dojo menus, built with LM's real spec API and drawn by the real LM client in the current look, so
    // a mockup is exactly what the screen will look like. No crawl and no checks: each page is shot at GUI 2, 3 and 4,
    // then the client quits. tools/lm-ui-probe/mock_compose.py adds the floor-plan picture where the page leaves room
    // for it (the picture element itself is Phase 2 work).
    static final boolean MOCK = Boolean.getBoolean("uiprobe.mock");
    private boolean mockDone;

    private void mock(String tag, Runnable r) {
        for (GuiOpenPacket p : keep("mock " + tag, r)) {
            note("MOCK " + tag + " -> " + p.pageId());
        }
    }

    private static UltraScreenSpec mockSpar(String page, String tab) {
        UltraScreenSpec s = UltraScreenSpec.of("spar").fitMenu().title("Sparring").page(page);
        return tab == null ? s : s.activeTab(tab).tabs("home|Home", "leaderboard|Leaderboard", "sparboard|Spar Board");
    }

    private void mockCrawl() {
        firstScript = 0;
        if ("hud".equals(System.getProperty("uiprobe.mockset", "nav"))) {
            // 4.7.72: the More tab with the HUD switch; the HUD itself is shot in finalTick (mockHudTick).
            mock("more tab hud switch", () -> UltraGui.open(player, "hub", "more"));
            return;
        }
        if (!"dojo".equals(System.getProperty("uiprobe.mockset", "nav"))) {
            mockNav();
            return;
        }
        // 1. The main menu: the real hub page, with its two dojo tiles renamed.
        List<GuiOpenPacket> hub;
        try {
            hub = UltraScreenSpec.captureSends(() -> UltraGui.open(player, "hub", "main"));
        } catch (Throwable t) {
            note("ERROR mock hub " + t);
            hub = List.of();
        }
        for (GuiOpenPacket p : hub) {
            String b = p.buttonsJson().replace("Sparring \\u0026 Dojo", "Spar Board").replace("Sparring & Dojo", "Spar Board")
                    .replace("Dojo wars", "My dojo");
            packets.add(new GuiOpenPacket(p.screenId(), p.title(), "mock_main_menu", p.tabsJson(), p.activeTab(),
                    p.linesJson(), b, p.panelWidth(), p.panelHeight(), p.layoutJson(), p.inputsJson()));
            origin.add("script mock main menu");
            note("MOCK main menu -> " + labels(packets.get(packets.size() - 1)));
        }
        // 2. The Spar Board with posts on it.
        mock("spar board", () -> {
            UltraScreenSpec s = mockSpar("mock_spar_board", "sparboard");
            s.line(UltraStyle.header("Your post"))
                    .line(UltraStyle.LABEL + "Nothing posted. Fights happen in Crane School's arena.")
                    .line(UltraStyle.LABEL + "Looking for Potential Unlock trains it in friendly spars. Ranked spars never do.")
                    .line(UltraStyle.labelValue("Ranked", "unlocked"))
                    .line(UltraStyle.header("On the board (3)"))
                    .line(UltraStyle.VALUE + "Kaito" + UltraStyle.LABEL + " Lv 412, ranked, 50 TP wager, looking for TP")
                    .line(UltraStyle.VALUE + "Mira" + UltraStyle.LABEL + " Lv 388, friendly, looking for Potential Unlock")
                    .line(UltraStyle.VALUE + "Ren" + UltraStyle.LABEL + " Lv 401, friendly, looking for Anything")
                    .buttonsAfterLines();
            s.buttonLeft("do:mock", "Kind - Friendly", UltraStyle.STYLE_NORMAL);
            s.buttonRight("do:mock", "Wager - none", UltraStyle.STYLE_NORMAL).rowGap();
            s.buttonLeft("do:mock", "Range - any level", UltraStyle.STYLE_NORMAL);
            s.buttonRight("do:mock", UltraStyle.confirm("Post for 15 min"), UltraStyle.STYLE_CONFIRM).rowGap();
            s.buttonLeft("do:mock", "Looking for - TP", UltraStyle.STYLE_NORMAL).rowGap();
            s.buttonLeft("do:mock", UltraStyle.confirm("Accept Kaito"), UltraStyle.STYLE_CONFIRM);
            s.buttonRight("do:mock", UltraStyle.confirm("Accept Mira"), UltraStyle.STYLE_CONFIRM).rowGap();
            s.buttonLeft("do:mock", UltraStyle.confirm("Accept Ren"), UltraStyle.STYLE_CONFIRM).rowGap();
            s.buttonLeft("do:mock", "Challenge a player", UltraStyle.STYLE_NORMAL);
            s.buttonRight("do:mock", "Watch spars", UltraStyle.STYLE_NORMAL).rowGap();
            s.back();
            s.send(player);
        });
        // 3. My dojo for a new dojo: one sparring mat, no style yet; the dojo's floor plan goes on the right.
        mock("my dojo", () -> {
            UltraScreenSpec s = mockSpar("mock_my_dojo", "dojo");
            s.line(UltraStyle.header("Kaito's Dojo"))
                    .line(UltraStyle.labelValue("Master", "Kaito (you)"))
                    .line(UltraStyle.labelValue("Style", "not picked yet"))
                    .line(UltraStyle.labelValue("Hall", "basic, 1 sparring mat"))
                    .line(UltraStyle.labelValue("Dojo Points", "75"))
                    .line(UltraStyle.labelValue("Members", "1 of 6"))
                    .line(UltraStyle.labelValue("Wars", "0 won, 0 lost"))
                    .line(UltraStyle.LABEL + "Your hall starts empty.")
                    .line(UltraStyle.LABEL + "Upgrades build into it.")
                    .buttonsAfterLines();
            s.button("do:mock", UltraStyle.confirm("Visit dojo"), UltraStyle.STYLE_CONFIRM).rowGap();
            s.buttonLeft("do:mock", "Upgrades", UltraStyle.STYLE_NORMAL);
            s.buttonRight("do:mock", "Style and bonus", UltraStyle.STYLE_NORMAL).rowGap();
            s.buttonLeft("do:mock", "Wars", UltraStyle.STYLE_NORMAL);
            s.buttonRight("do:mock", "Members and elites", UltraStyle.STYLE_NORMAL).rowGap();
            s.buttonLeft("do:mock", "Dojo leaderboard", UltraStyle.STYLE_NORMAL);
            s.buttonRight("do:mock", "Roster and bonds", UltraStyle.STYLE_NORMAL).rowGap();
            s.back();
            s.send(player);
        });
        // 4. Upgrades for that dojo: LM's real names and costs (DojoCatalog), the map on the right.
        mock("upgrades", () -> {
            UltraScreenSpec s = mockSpar("mock_dojo_upgrades", null);
            s.line(UltraStyle.labelValue("Dojo Points", "75"))
                    .line(UltraStyle.LABEL + "Earn points by hosting")
                    .line(UltraStyle.LABEL + "spars, winning war bouts")
                    .line(UltraStyle.LABEL + "and training disciples.")
                    .line(UltraStyle.LABEL)
                    .line(UltraStyle.LABEL + "Each upgrade builds into")
                    .line(UltraStyle.LABEL + "your hall. Tap one to see")
                    .line(UltraStyle.LABEL + "it before you buy.")
                    .line(UltraStyle.LABEL)
                    .buttonsAfterLines();
            s.buttonLeft("do:mock", "Sparring arenas - 40", UltraStyle.STYLE_NORMAL);
            s.buttonRight("do:mock", "Training hall - 30", UltraStyle.STYLE_NORMAL).rowGap();
            s.buttonLeft("do:mock", "Meditation room - 30", UltraStyle.STYLE_NORMAL);
            s.buttonRight("do:mock", "Spectator balcony - 30", UltraStyle.STYLE_NORMAL).rowGap();
            s.buttonLeft("do:mock", "Trophy hall - 30", UltraStyle.STYLE_NORMAL);
            s.buttonRight("do:mock", "Master quarters - 30", UltraStyle.STYLE_NORMAL).rowGap();
            s.buttonLeft("do:mock", "Garden grounds - 30", UltraStyle.STYLE_NORMAL);
            s.buttonRight("do:mock", "Disciple capacity - 25", UltraStyle.STYLE_NORMAL).rowGap();
            s.back();
            s.send(player);
        });
        // 5. Two upgrades from that dojo: Now, After and Where (green) in the band of blank lines, cost before the click.
        mockUpgrade("mock_dojo_up_ring2", "Sparring arenas: ring 2", "Another ring, so more board spars can run at once.",
                40, 75);
        mockUpgrade("mock_dojo_up_training1", "Training hall: basic", "A room in your hall with training dummies.", 30, 75);
    }

    /**
     * 4.7.71 navigation mockups, drawn from the real pages: the main menu with its search row, a search with
     * results, and a deep page with its breadcrumb (a dojo upgrade, reached through the search).
     */
    private void mockNav() {
        java.util.UUID u = player.m_20148_();
        NavHistory.clear(u);
        UltraHubScreens.searchFor(player, "");
        mock("more tab search row", () -> UltraGui.open(player, "hub", "more"));
        mock("main menu", () -> UltraGui.open(player, "hub", "main"));
        mock("sparring home", () -> UltraGui.open(player, "spar", "main"));
        mock("sparring leaderboard", () -> UltraGui.open(player, "spar", "leaderboard"));
        mock("dojo home", () -> UltraGui.open(player, "dojo", "home"));
        mock("dojo upgrades", () -> UltraGui.open(player, "dojo", "dojo_upgrades"));
        mock("dojo wars", () -> UltraGui.open(player, "dojo", "dojo_wars"));
        mock("difficulty home", () -> UltraGui.open(player, "difficulty", "main"));
        mock("difficulty tiers", () -> UltraGui.open(player, "difficulty", "tiers"));
        // 4.7.80: the Goals tab, the Wager sub-menu (kind, then amount).
        mock("hub goals", () -> UltraGui.open(player, "hub", "goals"));
        mock("board wager", () -> UltraGui.open(player, "spar", "board_wager"));
        mock("board wager coins", () -> UltraGui.open(player, "spar", "board_wager_coins"));
        JsonObject args = new JsonObject();
        args.addProperty("screen", "hub");
        args.addProperty("page", "more");
        args.addProperty("value", "dojo");
        mock("search results", () -> UltraGuiActions.dispatch(player, "hub", "do:hub_search", args.toString()));
        JsonObject a2 = new JsonObject();
        a2.addProperty("screen", "hub");
        a2.addProperty("page", "more");
        mock("breadcrumb dojo upgrade", () -> UltraGuiActions.dispatch(player, "hub", "go:spar|dojo_up:arena", a2.toString()));
        NavHistory.clear(u);
        mock("breadcrumb deep page", () -> UltraGui.open(player, "prestige", "mastery_lines"));
        UltraHubScreens.searchFor(player, "");
        NavHistory.clear(u);
    }

    /** The spec drops lines with no visible text, so a page fills its picture band with marker lines (the server lays
     *  the buttons out below them) and the mock blanks them in the captured packet. Phase 2 draws a picture there. */
    static final String BAND = "PICTURE BAND";
    static final int BAND_ROWS = 10;

    private void mockUpgrade(String page, String title, String what, int cost, int have) {
        List<GuiOpenPacket> got;
        try {
            got = UltraScreenSpec.captureSends(() -> mockUpgradeSpec(page, title, what, cost, have));
        } catch (Throwable t) {
            note("ERROR mock " + page + " " + t);
            return;
        }
        for (GuiOpenPacket p : got) {
            JsonArray out = new JsonArray();
            for (JsonElement el : JsonParser.parseString(p.linesJson()).getAsJsonArray()) {
                if (el.isJsonPrimitive() && el.getAsString().contains(BAND)) {
                    out.add("");
                } else {
                    out.add(el);
                }
            }
            packets.add(new GuiOpenPacket(p.screenId(), p.title(), p.pageId(), p.tabsJson(), p.activeTab(), out.toString(),
                    p.buttonsJson(), p.panelWidth(), p.panelHeight(), p.layoutJson(), p.inputsJson()));
            origin.add("script mock " + page);
            note("MOCK " + page + " -> " + p.pageId() + " band rows " + BAND_ROWS);
        }
    }

    private void mockUpgradeSpec(String page, String title, String what, int cost, int have) {
        {
            UltraScreenSpec s = mockSpar(page, null);
            s.line(UltraStyle.header(title)).line(UltraStyle.LABEL + what);
            for (int i = 0; i < BAND_ROWS; i++) {
                s.line(UltraStyle.LABEL + BAND);            // blanked below: the rows the pictures go in
            }
            s.line(UltraStyle.labelValue("Cost", cost + " Dojo Points (you have " + have + ")"))
                    .line(UltraStyle.LABEL + "Points are spent at once. An upgrade can't be undone.")
                    .buttonsAfterLines();
            s.buttonLeft("do:mock", UltraStyle.confirm("Buy for " + cost + " points"), UltraStyle.STYLE_CONFIRM);
            s.buttonRight("do:mock", "Visit dojo", UltraStyle.STYLE_NORMAL).rowGap();
            s.back();
            s.send(player);
        }
    }

    /** 4.7.68 Phase 2: ring {@code k} of {@code d} is built (a corner post stands on the hall floor). */
    private static boolean ringBuilt(com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.Dojo d, int k) {
        var lvl = com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.level(
                net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
        var l = com.dbzlegacy.adaptivedifficulty.dojo.DojoLayout.get();
        if (lvl == null || l == null) {
            return false;
        }
        int[] r = l.rings.get(k);
        int half = l.ringSize / 2;
        var c = com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld.plotCorner(d.plot);
        return !lvl.m_8055_(c.m_7918_(r[0] - half, l.floorY, r[1] - half)).m_60795_();
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
            if (MOCK) {
                mockCrawl();
            } else {
                crawl();
            }
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
