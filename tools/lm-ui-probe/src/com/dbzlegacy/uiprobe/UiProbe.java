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
            note("HUB HOME tabs Home/Goals/More, Home first: " + (b.indexOf("tab:home") >= 0
                    && b.indexOf("tab:home") < b.indexOf("tab:goals") && b.indexOf("tab:goals") < b.indexOf("tab:more")
                    ? "PASS" : "FAIL"));
            note("HUB HOME tiles grouped (Prestige, Difficulty, Sparring & Dojo, Rival, Dojo wars, Character): "
                    + (has(p, "Prestige") && has(p, "Sparring ") && has(p, "Dojo wars")
                    && has(p, "Character Services") && !has(p, "Menu skins") && !has(p, "Staff Admin")
                    ? "PASS" : "FAIL"));
            note("HUB HOME no made-up numbers, no other mods: " + (!has(p, "35%") && !has(p, "/home")
                    && !has(p, "/spawn") && !has(p, "/tpa") && !has(p, "/empire") && !has(p, "/meditate")
                    ? "PASS" : "FAIL"));
        }
        for (GuiOpenPacket p : hubClick("hub goals", "hub", "tab:goals", "main")) {
            note("GOALS placeholder, no payout: " + ("goals".equals(p.pageId())
                    && has(p, "Weekly goals arrive in the next update.") && has(p, "No goals yet")
                    && p.buttonsJson().contains("nav:none") ? "PASS" : "FAIL"));
        }
        for (GuiOpenPacket p : hubClick("hub more", "hub", "tab:more", "main")) {
            note("MORE: meditation, Menu skins" + (staff ? ", Staff Admin" : "") + ": " + ("more".equals(p.pageId())
                    && has(p, "Meditation") && has(p, "Menu skins") && (has(p, "Staff Admin") == staff)
                    ? "PASS" : "FAIL"));
        }
        // Every hub link opens the right LM screen.
        String[][] links = {
            {"open:prestige", "prestige|main"}, {"open:difficulty", "difficulty|main"}, {"open:spar", "spar|main"},
            {"open:rival", "rival|home"}, {"page:wars", "spar|dojo_war"}, {"open:character", "character|main"},
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

        // Spar board end to end with a stand-in player.
        com.mojang.authlib.GameProfile gp = new com.mojang.authlib.GameProfile(
                java.util.UUID.nameUUIDFromBytes("uiprobe:Sparmate".getBytes(StandardCharsets.UTF_8)), "Sparmate");
        ServerPlayer mate = net.minecraftforge.common.util.FakePlayerFactory.get(player.m_284548_(), gp);
        backing().add(mate);
        byUuid().put(mate.m_20148_(), mate);
        try {
            com.dbzlegacy.adaptivedifficulty.sparring.SparBoard.list(mate);
            for (GuiOpenPacket p : keep("spar board one looking", () -> UltraGui.open(player, "spar", "main"))) {
                // JSON escapes the apostrophe in "Who's", so match the rest.
                note("BOARD lists who is looking with TP per hit: " + (has(p, "s looking (1)")
                        && has(p, "Sparmate: wants TP") && has(p, "TP a hit") && has(p, "Ask Sparmate")
                        && has(p, "vs another dojo") ? "PASS" : "FAIL") + " | " + labels(p));
            }
            for (GuiOpenPacket p : hubClick("spar board list me", "spar", "do:spar_list", "main")) {
                note("LIST ME: " + (has(p, "Listed for 15 minutes") && has(p, "Take me off the board")
                        ? "PASS" : "FAIL"));
            }
            for (GuiOpenPacket p : hubClick("spar board want", "spar", "do:spar_want", "main")) {
                note("WANT cycles to Potential Unlock: " + (has(p, "Looking for: Potential Unlock") ? "PASS" : "FAIL"));
            }
            String ask = "do:spar_ask:" + mate.m_20148_();
            for (GuiOpenPacket p : hubClick("spar board ask", "spar", ask, "main")) {
                note("ASK one tap: " + (has(p, "Asked Sparmate. 5 minutes to say yes.")
                        && has(p, "Asked Sparmate") && p.buttonsJson().contains("nav:none") ? "PASS" : "FAIL"));
            }
            for (GuiOpenPacket p : hubClick("spar board ask again", "spar", ask, "main")) {
                note("ASK cooldown: " + (has(p, "Just asked Sparmate") ? "PASS" : "FAIL"));
            }
            com.dbzlegacy.adaptivedifficulty.sparring.SparBoard.ask(mate, player.m_20148_().toString());
            for (GuiOpenPacket p : keep("spar board asked", () -> UltraGui.open(player, "spar", "main"))) {
                note("ASKED shows Say yes: " + (has(p, "Sparmate asked you to spar.") && has(p, "Say yes to Sparmate")
                        ? "PASS" : "FAIL"));
            }
            for (GuiOpenPacket p : hubClick("spar board yes", "spar", "do:spar_yes:" + mate.m_20148_(), "main")) {
                note("SAY YES tells the asker: " + (has(p, "Told Sparmate.") && !has(p, "Say yes to Sparmate")
                        ? "PASS" : "FAIL"));
            }
            com.dbzlegacy.adaptivedifficulty.sparring.SparBoard.onSessionStart(player, mate);
            long now = System.currentTimeMillis();
            note("SPAR START takes both off the board: " + (com.dbzlegacy.adaptivedifficulty.sparring.SparBoard
                    .listedLeft(player.m_20148_(), now) == 0L && com.dbzlegacy.adaptivedifficulty.sparring.SparBoard
                    .listedLeft(mate.m_20148_(), now) == 0L ? "PASS" : "FAIL"));
            com.dbzlegacy.adaptivedifficulty.sparring.SparBoard.list(mate);
            com.dbzlegacy.adaptivedifficulty.sparring.SparBoard.onLogout(mate.m_20148_());
            for (GuiOpenPacket p : keep("spar board empty", () -> UltraGui.open(player, "spar", "main"))) {
                note("LOGOUT leaves the board, empty board is dim: " + (has(p, "s looking (0)")
                        && has(p, "No one to ask yet") ? "PASS" : "FAIL"));
            }
        } finally {
            backing().remove(mate);
            byUuid().remove(mate.m_20148_());
            com.dbzlegacy.adaptivedifficulty.sparring.SparBoard.onLogout(mate.m_20148_());
            com.dbzlegacy.adaptivedifficulty.sparring.SparBoard.unlist(player.m_20148_());
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
        return "main".equals(p.pageId()) && has(p, "How difficulty works") && has(p, "Your tier")
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
                    && !has(p, "Next: Your tier") && has(p, "Back to Home") && has(p, "Pick your level")
                    && has(p, "T1 Awakened") ? "PASS" : "FAIL") + " | " + labels(p));
        }
        for (GuiOpenPacket p : keep("summons after guide", () -> UltraGui.open(player, "difficulty", "summons"))) {
            note("SUMMONS after guide: Back to Home: " + (has(p, "Back to Home") ? "PASS" : "FAIL"));
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
                note("SUMMONS chrome: " + (has(p, "Boss summons") && has(p, "Back to Step 1") ? "PASS" : "FAIL"));
            }
            for (GuiOpenPacket p : keep("summon dragon", () -> UltraGui.open(player, "difficulty", "end_dragon"))) {
                note("SUMMON locked still dim+inert: " + (p.buttonsJson().contains("nav:none") && has(p, "Summon locked") ? "PASS" : "FAIL"));
            }
            guideScript();
            hubScript();
            meditationScript();

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
        {"open", "hub", "main"}, {"wait", "60"}, {"say", "uiclick label:Dojo wars"}, {"wait", "60"},
        {"say", "uirecord hub3-dojo-wars"}, {"wait", "20"},
        {"open", "spar", "main"}, {"wait", "60"}, {"say", "uiclick label:List me for 15 min"}, {"wait", "60"},
        {"say", "uirecord spar1-listed"}, {"wait", "20"},
        {"guidereset"}, {"open", "difficulty", "main"}, {"wait", "60"}, {"say", "uirecord guide1-first-open"},
        {"wait", "20"}, {"say", "uiclick label:Skip guide"}, {"wait", "60"}, {"say", "uirecord guide2-after-skip"},
        {"wait", "20"}, {"guidecheck"}, {"open", "difficulty", "main"}, {"wait", "60"},
        {"say", "uirecord guide3-reopen"}, {"wait", "20"}, {"say", "uiclick label:How difficulty works"},
        {"wait", "60"}, {"say", "uirecord guide4-replay"}, {"wait", "20"}, {"guidecheck"},
        // 4.7.65: Friendly Fist never lets a spar kill. Lethal partner hits on the real probe player, checked a
        // few ticks later (the heal is queued after the hit), with a screenshot of the client still in game.
        {"ff", "setup"}, {"wait", "10"},
        {"ff", "hitV"}, {"wait", "10"}, {"ff", "checkV"}, {"ff", "hitV"}, {"wait", "10"}, {"ff", "checkV"},
        {"ff", "hitV"}, {"wait", "10"}, {"ff", "checkV"}, {"say", "uirecord ff1-after-three-lethal-hits"},
        {"wait", "20"}, {"ff", "hitA"}, {"wait", "10"}, {"ff", "checkA"},
        {"trialcheck"},
        // Last, because the probe player dies in both (stand-ins are Forge fake players, which take no damage):
        // a zombie's lethal hit in a Friendly Fist spar still kills; with Friendly Fist off a partner's still kills.
        {"ff", "mob"}, {"wait", "10"}, {"ff", "checkMob"}, {"ff", "respawn"}, {"wait", "40"},
        {"ff", "off"}, {"wait", "10"}, {"ff", "checkOff"}, {"ff", "respawn"}, {"wait", "40"}, {"ff", "cleanup"},
        {"wait", "40"}, {"say", "uiquit"},
    };

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
