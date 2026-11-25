package com.dbzlegacy.adaptivedifficulty.dojo;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.Dojo;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.sparring.SparPlayerRuntime;
import com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.LmChat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * LM 4.7.68 Spar Board: the only way a formal spar starts. A player posts (friendly or ranked, an optional wager,
 * a level range), or challenges one player directly; another accepts; both are teleported into the poster's dojo
 * arena, fight, and go back to exactly where they stood. A bout ends by KO (a lethal blow is caught at 1 HP),
 * ring-out (past the ring's edge and its apron, {@value DojoWorld#RING_OUT_HALF} blocks from the centre), forfeit
 * (the other fighter wins and takes any wager), disconnect or the
 * {@value DojoCatalog#BOUT_TIMEOUT_MS} ms timeout (more damage wins). Friendly bouts pay nothing and count toward
 * the ranked gate; ranked bouts pay spar TP and feed the leaderboard and streak. Plain world PvP is not a spar.
 *
 * <p>4.7.84 (P2-1): a wager goes to the winner on a KO, a ring-out, a forfeit, and a timeout one fighter led on
 * damage. Both stakes come back only on a disconnect, a dead-even timeout, or a crash or restart
 * ({@link #refundStaleEscrow}). A forfeit still gives no ranked credit.
 */
public final class BoardSpar {
    private BoardSpar() {}

    public static final long POST_MS = 15L * 60L * 1000L;
    public static final String ACT_POST = "board_post";
    public static final String ACT_UNPOST = "board_unpost";
    public static final String ACT_MODE = "board_mode";
    /** 4.7.80: opens the wager sub-menu (kind, then amount); the old cycling button is gone. */
    public static final String ACT_WAGER = "board_wager";
    /** 4.7.80: sets the next post's wager to one step (0 none, then coin steps, then point steps). */
    public static final String ACT_WAGER_SET = "board_wager_set";
    public static final String ACT_RANGE = "board_range";
    public static final String ACT_ACCEPT = "board_accept";
    public static final String ACT_CHALLENGE = "board_challenge";
    public static final String ACT_FORFEIT = "board_forfeit";
    public static final String ACT_REMATCH = "board_rematch";
    public static final String ACT_WANT = "board_want";

    /**
     * What a player is looking for from a spar. Potential Unlock is the opt-in: on a friendly spar it turns Potential
     * Unlock training on for that player in that session (ranked spars never train it). Cycles TP, Potential Unlock,
     * Anything.
     */
    public enum Want {
        TP("TP"), POTENTIAL("Potential Unlock"), ANY("Anything");

        public final String label;

        Want(String label) {
            this.label = label;
        }

        public Want next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** What the player will post next: friendly or ranked, wager step (0 none), level range step. */
    public record Draft(boolean ranked, int wager, int range, Want want) {}

    /** A board post; {@code target} set for a direct challenge. */
    public record Post(UUID poster, String name, boolean ranked, String wagerKind, long wager, int range, UUID target,
            String targetName, long at, Want want) {}

    /** A running bout in a dojo arena. */
    public static final class Bout {
        public String id;
        public UUID a;
        public UUID b;
        public String nameA;
        public String nameB;
        public String host;
        public int arena;
        public boolean ranked;
        public String wagerKind = "";
        public long wager;
        public long startAt;
        /** Each fighter's Potential Unlock opt-in (friendly spars only). */
        public boolean potentialA;
        public boolean potentialB;
        public double cx;
        public double cz;
        volatile boolean settled;
    }

    /** What the result screen shows one fighter. */
    public record Result(UUID opponent, String opponentName, boolean ranked, String wagerKind, long wager,
            List<String> lines) {}

    static final Map<UUID, Draft> DRAFTS = new ConcurrentHashMap<>();
    static final Map<UUID, Post> POSTS = new ConcurrentHashMap<>();
    static final Map<String, Bout> BOUTS = new ConcurrentHashMap<>();
    static final Map<UUID, String> BOUT_OF = new ConcurrentHashMap<>();
    static final Map<UUID, Result> RESULTS = new ConcurrentHashMap<>();
    /** Pair key to the last time it earned ranked credit / host points. */
    static final Map<String, Long> PAIR = new ConcurrentHashMap<>();
    private static int seq;

    /** Set what {@code id} posts next (the test rig presets a kind and a wager step without cycling buttons). */
    public static void setDraft(UUID id, boolean ranked, int wagerStep) {
        if (id != null) {
            Draft d = draft(id);
            DRAFTS.put(id, new Draft(ranked, Math.floorMod(wagerStep, wagerSteps()), d.range(), d.want()));
        }
    }

    public static Draft draft(UUID id) {
        return id == null ? new Draft(false, 0, 0, Want.TP) : DRAFTS.getOrDefault(id, new Draft(false, 0, 0, Want.TP));
    }

    // ------------------------------------------------------------------ wagers

    /** Wager steps: 0 none, then coin steps, then point steps. */
    public static int wagerSteps() {
        return 1 + DojoCatalog.COIN_STEPS.length + DojoCatalog.POINT_STEPS.length;
    }

    public static String wagerKind(int step) {
        if (step <= 0) {
            return "";
        }
        return step <= DojoCatalog.COIN_STEPS.length ? "COINS" : "POINTS";
    }

    public static long wagerAmount(int step) {
        if (step <= 0) {
            return 0L;
        }
        if (step <= DojoCatalog.COIN_STEPS.length) {
            return DojoCatalog.COIN_STEPS[step - 1];
        }
        int i = step - 1 - DojoCatalog.COIN_STEPS.length;
        return DojoCatalog.POINT_STEPS[Math.min(i, DojoCatalog.POINT_STEPS.length - 1)];
    }

    public static String wagerText(String kind, long amount) {
        if (kind == null || kind.isBlank() || amount <= 0L) {
            return "no wager";
        }
        return "POINTS".equals(kind) ? amount + " Dojo Points" : AncientCoinEconomy.format(amount);
    }

    public static int levelRange(int step) {
        int[] r = DojoCatalog.LEVEL_RANGES;
        return r[Math.floorMod(step, r.length)];
    }

    public static String rangeText(int range) {
        return range <= 0 ? "any level" : "within " + range + " levels";
    }

    // ------------------------------------------------------------------ gate and checks

    public static int unranked(UUID id) {
        DojoStore.PlayerRecord r = id == null ? null : DojoStore.get().players.get(id.toString());
        return r == null ? 0 : r.unrankedSpars;
    }

    public static boolean rankedUnlocked(UUID id) {
        return unranked(id) >= DojoCatalog.RANKED_GATE;
    }

    static int level(ServerPlayer p) {
        try {
            return Math.max(1, DmzProgression.dmzLevel(p));
        } catch (Throwable t) {
            return 1;
        }
    }

    public static boolean inBout(UUID id) {
        return id != null && BOUT_OF.containsKey(id);
    }

    public static Bout boutOf(UUID id) {
        String b = id == null ? null : BOUT_OF.get(id);
        return b == null ? null : BOUTS.get(b);
    }

    public static int boutsIn(String hostMaster) {
        int n = 0;
        for (Bout b : BOUTS.values()) {
            if (b.host.equals(hostMaster)) {
                n++;
            }
        }
        return n;
    }

    public static List<Bout> bouts() {
        return new ArrayList<>(BOUTS.values());
    }

    /** True when {@code a} and {@code b} fight each other in a board bout. */
    public static boolean isBoutPair(ServerPlayer a, ServerPlayer b) {
        if (a == null || b == null) {
            return false;
        }
        Bout x = boutOf(a.m_20148_());
        return x != null && x == boutOf(b.m_20148_());
    }

    /** Why {@code viewer} can't accept {@code post} right now, or null when they can. Human words. */
    public static String whyNot(ServerPlayer viewer, Post post) {
        if (post == null) {
            return "That post is gone.";
        }
        if (post.poster().equals(viewer.m_20148_())) {
            return "That's your own post.";
        }
        if (post.target() != null && !post.target().equals(viewer.m_20148_())) {
            return "This challenge is for " + post.targetName() + ".";
        }
        if (DojoService.dojoOf(viewer) == null) {
            return "Join a dojo first. No dojo, no spar.";
        }
        if (inBout(viewer.m_20148_()) || DojoWar.fighting(viewer.m_20148_())) {
            return "You're already in a fight.";
        }
        if (post.ranked() && !rankedUnlocked(viewer.m_20148_())) {
            int left = DojoCatalog.RANKED_GATE - unranked(viewer.m_20148_());
            return "Ranked unlocks after " + DojoCatalog.RANKED_GATE + " friendly spars. " + left + " to go.";
        }
        ServerPlayer poster = viewer.m_20194_().m_6846_().m_11259_(post.poster());
        if (poster == null) {
            return "The poster went offline.";
        }
        if (post.range() > 0 && Math.abs(level(poster) - level(viewer)) > post.range()) {
            return "Level " + level(viewer) + " is outside this post's range (" + rangeText(post.range()) + " of "
                    + level(poster) + ").";
        }
        String afford = canStake(viewer, post.wagerKind(), post.wager());
        if (afford != null) {
            return afford;
        }
        Dojo host = DojoService.dojoOf(poster);
        if (host == null) {
            return "The poster's dojo closed.";
        }
        if (freeArena(host) < 0) {
            return host.name + "'s arenas are all busy. Try again in a few minutes.";
        }
        return null;
    }

    static String canStake(ServerPlayer p, String kind, long amount) {
        if (kind == null || kind.isBlank() || amount <= 0L) {
            return null;
        }
        if ("COINS".equals(kind)) {
            return AncientCoinEconomy.canAfford(p, amount) ? null
                    : "This wager needs " + AncientCoinEconomy.format(amount) + " in Ancient Coins on you.";
        }
        Dojo d = DojoService.dojoOf(p);
        if (d == null || !DojoService.canManage(p.m_20148_(), d)) {
            return "Only a master or an elite can wager Dojo Points.";
        }
        return d.points >= amount ? null : "Your dojo needs " + amount + " Dojo Points for this wager.";
    }

    static int freeArena(Dojo host) {
        boolean[] used = new boolean[DojoCatalog.MAX_ARENAS];
        for (Bout b : BOUTS.values()) {
            if (b.host.equals(host.masterUuid) && b.arena >= 0 && b.arena < used.length) {
                used[b.arena] = true;
            }
        }
        for (int k = 0; k < DojoService.arenas(host); k++) {
            if (!used[k]) {
                return k;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ the board

    /**
     * Live posts the viewer can see. 4.7.84 (SP-2): challenges for them first, then posts they can accept now, then the
     * ones they can't (dim on the board); inside each group, posts looking for what the viewer looks for, then the
     * oldest first.
     */
    public static List<Post> board(ServerPlayer viewer) {
        long now = System.currentTimeMillis();
        POSTS.values().removeIf(p -> now - p.at() > POST_MS);
        List<Post> out = new ArrayList<>();
        for (Post p : POSTS.values()) {
            if (viewer.m_20194_().m_6846_().m_11259_(p.poster()) == null) {
                continue;
            }
            if (p.target() != null && !p.target().equals(viewer.m_20148_()) && !p.poster().equals(viewer.m_20148_())) {
                continue;
            }
            out.add(p);
        }
        UUID me = viewer.m_20148_();
        Want mine = draft(me).want();
        Map<UUID, Boolean> can = new java.util.HashMap<>();
        for (Post p : out) {
            can.put(p.poster(), p.poster().equals(me) || whyNot(viewer, p) == null);
        }
        out.sort(Comparator.comparing((Post p) -> !me.equals(p.target()))
                .thenComparing(p -> !can.getOrDefault(p.poster(), false))
                .thenComparing(p -> p.want() != mine && p.want() != Want.ANY && mine != Want.ANY)
                .thenComparingLong(Post::at));
        return out;
    }

    public static Post postOf(UUID id) {
        return id == null ? null : POSTS.get(id);
    }

    public static int levelOf(UUID id, MinecraftServer server) {
        ServerPlayer p = server == null ? null : server.m_6846_().m_11259_(id);
        return p == null ? 0 : level(p);
    }

    public static boolean friendlyFist(UUID id, MinecraftServer server) {
        ServerPlayer p = server == null ? null : server.m_6846_().m_11259_(id);
        if (p == null) {
            return false;
        }
        try {
            var data = DmzProgression.stats(p);
            return data != null && data.getStatus().isFriendlyFistEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    static String post(ServerPlayer p, UUID target) {
        UUID id = p.m_20148_();
        if (DojoService.dojoOf(p) == null) {
            return "Join a dojo first. No dojo, no spar.";
        }
        if (inBout(id)) {
            return "You're already in a fight.";
        }
        Draft d = draft(id);
        if (d.ranked() && !rankedUnlocked(id)) {
            return "Ranked unlocks after " + DojoCatalog.RANKED_GATE + " friendly spars. You have " + unranked(id) + ".";
        }
        String kind = wagerKind(d.wager());
        long amount = wagerAmount(d.wager());
        String afford = canStake(p, kind, amount);
        if (afford != null) {
            return afford;
        }
        ServerPlayer t = target == null ? null : p.m_20194_().m_6846_().m_11259_(target);
        if (target != null && t == null) {
            return "That player isn't online.";
        }
        if (t != null && t == p) {
            return "You can't challenge yourself.";
        }
        POSTS.put(id, new Post(id, p.m_7755_().getString(), d.ranked(), kind, amount, levelRange(d.range()), target,
                t == null ? "" : t.m_7755_().getString(), System.currentTimeMillis(), d.want()));
        String what = (d.ranked() ? "ranked" : "friendly") + " spar, " + wagerText(kind, amount);
        if (t != null) {
            postNotice(t, p.m_7755_().getString(), what);
            return "Challenged " + t.m_7755_().getString() + " to a " + what + ". 15 minutes to accept.";
        }
        return "Posted a " + what + " for 15 minutes. The fight happens in your dojo's arena.";
    }

    /** 4.7.87: the Spar Board, where a challenge is accepted. */
    public static final String BOARD_ACTION = "go:spar|sparboard";

    /**
     * A challenge to {@code t}: the chat line with its [Open the Spar Board] link, or for a HUD player the same words
     * as a dojo toast that opens the Spar Board.
     */
    public static void postNotice(ServerPlayer t, String from, String what) {
        String head = LmChat.info("Spar", UltraStyle.VALUE + from + UltraStyle.LABEL + " challenges you to a " + what
                + ". ");
        if (com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.active(t)) {
            java.util.List<String> lines = new ArrayList<>(com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.chatLines(head));
            lines.add(UltraStyle.GREEN + "Open the Spar Board");
            com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.show(t, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.WAR,
                    lines, 1f, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.accentOf("spar"), BOARD_ACTION,
                    com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.WAR_REQUEST_MS, true);
            return;
        }
        Component line = Component.m_237113_(head)
                .m_7220_(Component.m_237113_(UltraStyle.GREEN + "[Open the Spar Board]").m_130948_(Style.f_131099_
                        .m_131142_(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lm open spar sparboard"))
                        .m_131144_(new HoverEvent(HoverEvent.Action.f_130831_,
                                Component.m_237113_("See the challenge and accept it")))));
        com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.vanilla(t, "chat", line.getString());
        t.m_213846_(line);
    }

    /** {@code acceptor} takes {@code poster}'s post: escrow, teleport both into the poster's arena, start. */
    static String accept(ServerPlayer acceptor, UUID posterId) {
        Post post = POSTS.get(posterId);
        String why = whyNot(acceptor, post);
        if (why != null) {
            return why;
        }
        ServerPlayer poster = acceptor.m_20194_().m_6846_().m_11259_(posterId);
        String posterStake = canStake(poster, post.wagerKind(), post.wager());
        if (posterStake != null) {
            return post.name() + " can't cover the wager any more.";
        }
        ServerLevel level = DojoWorld.level(acceptor.m_20194_());
        if (level == null) {
            return DojoWorld.NOT_LOADED;
        }
        Dojo host = DojoService.dojoOf(poster);
        // The fight is in the host's hall: it must be built (a new or upgraded hall goes up over a few seconds).
        DojoWorld.ensureBuilt(acceptor.m_20194_(), host);
        if (!DojoWorld.ready(host)) {
            return host.name + "'s hall is being built. Try again in a few seconds.";
        }
        int arena = freeArena(host);
        if (!escrow(post, poster, acceptor)) {
            return "The wager couldn't be held. Nobody paid anything.";
        }
        POSTS.remove(posterId);
        POSTS.remove(acceptor.m_20148_());
        Bout b = new Bout();
        b.id = "bout-" + (++seq) + "-" + System.currentTimeMillis();
        b.a = poster.m_20148_();
        b.b = acceptor.m_20148_();
        b.nameA = poster.m_7755_().getString();
        b.nameB = acceptor.m_7755_().getString();
        b.host = host.masterUuid;
        b.arena = arena;
        b.ranked = post.ranked();
        b.wagerKind = post.wagerKind() == null ? "" : post.wagerKind();
        b.wager = post.wager();
        b.startAt = System.currentTimeMillis();
        // Potential Unlock opt-ins as each fighter has them at accept time (ranked spars never train it).
        b.potentialA = !b.ranked && post.want() == Want.POTENTIAL;
        b.potentialB = !b.ranked && draft(acceptor.m_20148_()).want() == Want.POTENTIAL;
        double[] c = DojoWorld.ringCenter(host, arena);
        b.cx = c[0];
        b.cz = c[1];
        BOUTS.put(b.id, b);
        BOUT_OF.put(b.a, b.id);
        BOUT_OF.put(b.b, b.id);
        DojoWorld.rememberSpot(poster, "spar");
        DojoWorld.rememberSpot(acceptor, "spar");
        DojoWorld.SPECTATING.remove(poster.m_20148_());
        DojoWorld.SPECTATING.remove(acceptor.m_20148_());
        DojoWorld.teleport(poster, level, b.cx - 6, DojoWorld.FLOOR_Y + 1, b.cz, -90.0f);
        DojoWorld.teleport(acceptor, level, b.cx + 6, DojoWorld.FLOOR_Y + 1, b.cz, 90.0f);
        SparringSystem.startBoardSession(poster, acceptor, b.id, b.ranked, b.potentialA, b.potentialB);
        String what = (b.ranked ? "Ranked" : "Friendly") + " spar in " + host.name + ", ring " + (arena + 1)
                + (b.wager > 0 ? ", " + wagerText(b.wagerKind, b.wager) + " each at stake" : "") + ".";
        // 4.7.87: a HUD poster reads it as a dojo toast (the fight pill and start toast follow from the session).
        com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.toastOrChat(poster, LmChat.ok("Spar",
                acceptor.m_7755_().getString() + " accepted. " + what), com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.WAR,
                com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.accentOf("spar"), "open:spar",
                com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.WAR_TOAST_MS);
        return "Accepted. " + what + " KO, ring-out or the 5-minute clock decides it.";
    }

    /** Hold both stakes (coins taken from both inventories, or points from both dojos). */
    static boolean escrow(Post post, ServerPlayer poster, ServerPlayer acceptor) {
        if (post.wagerKind() == null || post.wagerKind().isBlank() || post.wager() <= 0L) {
            return true;
        }
        long amt = post.wager();
        if ("COINS".equals(post.wagerKind())) {
            if (!AncientCoinEconomy.charge(poster, amt)) {
                return false;
            }
            if (!AncientCoinEconomy.charge(acceptor, amt)) {
                giveCoins(poster, amt);
                return false;
            }
        } else {
            Dojo da = DojoService.dojoOf(poster);
            Dojo db = DojoService.dojoOf(acceptor);
            if (da == null || db == null || da.points < amt || db.points < amt) {
                return false;
            }
            if (da == db && da.points < amt * 2) {
                return false;
            }
            da.points -= (int) amt;
            db.points -= (int) amt;
        }
        DojoStore.Escrow e = new DojoStore.Escrow();
        e.kind = post.wagerKind();
        e.amount = amt;
        e.a = poster.m_20148_().toString();
        e.aDojo = DojoService.dojoOf(poster) == null ? "" : DojoService.dojoOf(poster).masterUuid;
        e.b = acceptor.m_20148_().toString();
        e.bDojo = DojoService.dojoOf(acceptor) == null ? "" : DojoService.dojoOf(acceptor).masterUuid;
        e.at = System.currentTimeMillis();
        DojoStore.get().escrow.put(e.a + "|" + e.b, e);
        DojoStore.get().markDirty();
        return true;
    }

    /** Pay {@code copper} in the fewest coins. */
    static void giveCoins(ServerPlayer p, long copper) {
        long left = copper;
        AncientCoinEconomy.CoinKind[] order = {AncientCoinEconomy.CoinKind.NETHERITE,
                AncientCoinEconomy.CoinKind.DIAMOND, AncientCoinEconomy.CoinKind.EMERALD,
                AncientCoinEconomy.CoinKind.GOLD, AncientCoinEconomy.CoinKind.IRON, AncientCoinEconomy.CoinKind.COPPER};
        for (AncientCoinEconomy.CoinKind k : order) {
            long n = left / k.copperValue;
            if (n > 0) {
                AncientCoinEconomy.grantExact(p, k, n);
                left -= n * k.copperValue;
            }
        }
    }

    // ------------------------------------------------------------------ ending a bout

    /** A lethal blow in a bout: the victim stays at 1 HP and the attacker wins by KO (next tick). */
    public static void queueKnockout(ServerPlayer victim, ServerPlayer attacker) {
        Bout b = boutOf(victim.m_20148_());
        MinecraftServer server = victim.m_20194_();
        if (b == null || server == null || b.settled) {
            return;
        }
        UUID winner = attacker.m_20148_();
        server.m_6937_(new net.minecraft.server.TickTask(server.m_129921_(), () -> end(b, winner, "KO")));
    }

    /** Forfeit from the menu: the other fighter wins and takes any wager (4.7.84, P2-1). */
    static String forfeit(ServerPlayer p) {
        Bout b = boutOf(p.m_20148_());
        if (b == null) {
            return "You're not in a board spar.";
        }
        boolean wagered = b.wager > 0L && b.wagerKind != null && !b.wagerKind.isBlank();
        String other = p.m_20148_().equals(b.a) ? b.nameB : b.nameA;
        end(b, p.m_20148_().equals(b.a) ? b.b : b.a, "FORFEIT");
        return "You forfeited, so " + other + " wins." + (wagered ? " They took both stakes." : "");
    }

    /** Once a second: ring-outs and the timeout. */
    public static void pulse(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (Bout b : new ArrayList<>(BOUTS.values())) {
            if (b.settled) {
                continue;
            }
            for (UUID id : new UUID[] {b.a, b.b}) {
                ServerPlayer p = server.m_6846_().m_11259_(id);
                if (p == null) {
                    continue;
                }
                // Out of the ring: past the ring's edge and apron on either axis (the next ring is beyond that).
                boolean out = !DojoWorld.inDojoWorld(p) || p.m_20186_() < DojoWorld.FLOOR_Y - 10
                        || Math.abs(p.m_20185_() - b.cx) > DojoWorld.RING_OUT_HALF
                        || Math.abs(p.m_20189_() - b.cz) > DojoWorld.RING_OUT_HALF;
                if (out) {
                    end(b, id.equals(b.a) ? b.b : b.a, "RING_OUT");
                    break;
                }
            }
            if (!b.settled && now - b.startAt >= DojoCatalog.BOUT_TIMEOUT_MS) {
                SparPlayerRuntime ra = SparringSystem.runtime(b.a);
                SparPlayerRuntime rb = SparringSystem.runtime(b.b);
                double da = ra == null ? 0 : ra.sessionDmg;
                double db = rb == null ? 0 : rb.sessionDmg;
                end(b, da == db ? null : (da > db ? b.a : b.b), "TIMEOUT");
            }
        }
    }

    /** End a bout from the board side (KO, ring-out, forfeit, timeout): settle, close the session, send them back. */
    static void end(Bout b, UUID winner, String how) {
        if (b == null || b.settled) {
            return;
        }
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        settle(b, winner, how);
        ServerPlayer pa = server == null ? null : server.m_6846_().m_11259_(b.a);
        ServerPlayer pb = server == null ? null : server.m_6846_().m_11259_(b.b);
        if (pa != null) {
            SparringSystem.endSession(pa, pb, "board");
        } else if (pb != null) {
            SparringSystem.endSession(pb, null, "board");
        }
        sendBack(server, b);
    }

    /**
     * SparringSystem is ending a session (logout, inactivity, /lm spar end, death): settle the bout first so the
     * leaderboard sees the right credit. Called at the start of {@code endSession}.
     */
    public static void beforeSessionEnd(ServerPlayer player, ServerPlayer partner, String reason) {
        Bout b = player == null ? null : boutOf(player.m_20148_());
        if (b == null || b.settled) {
            return;
        }
        UUID other = player.m_20148_().equals(b.a) ? b.b : b.a;
        String how = switch (reason == null ? "" : reason) {
            case "logout", "partner-offline" -> "DISCONNECT";
            case "command" -> "FORFEIT";
            case "death" -> "KO";
            default -> "TIMEOUT";
        };
        UUID winner = "TIMEOUT".equals(how) ? null : other;
        settle(b, winner, how);
        MinecraftServer server = player.m_20194_();
        if (server != null) {
            server.m_6937_(new net.minecraft.server.TickTask(server.m_129921_(), () -> sendBack(server, b)));
        }
    }

    /** Both fighters back to their exact spots, healed, with the result screen (offline ones on their next login). */
    static void sendBack(MinecraftServer server, Bout b) {
        BOUTS.remove(b.id);
        BOUT_OF.remove(b.a);
        BOUT_OF.remove(b.b);
        if (server == null) {
            return;
        }
        for (UUID id : new UUID[] {b.a, b.b}) {
            ServerPlayer p = server.m_6846_().m_11259_(id);
            if (p == null) {
                continue;
            }
            DojoWorld.returnPlayer(p);
            try {
                p.m_21153_(p.m_21233_());
            } catch (Throwable ignored) {
            }
            try {
                com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraGui.open(p, "spar", "result");
            } catch (Throwable ignored) {
            }
        }
    }

    /** Points, clout, the ranked gate, wagers and the result lines. Runs once per bout. */
    static synchronized void settle(Bout b, UUID winner, String how) {
        if (b.settled) {
            return;
        }
        b.settled = true;
        long now = System.currentTimeMillis();
        long dur = now - b.startAt;
        SparPlayerRuntime ra = SparringSystem.runtime(b.a);
        SparPlayerRuntime rb = SparringSystem.runtime(b.b);
        double da = ra == null ? 0 : ra.sessionDmg;
        double db = rb == null ? 0 : rb.sessionDmg;
        boolean decisive = "KO".equals(how) || "RING_OUT".equals(how) || "TIMEOUT".equals(how);
        boolean counted = decisive && dur >= DojoCatalog.MIN_FIGHT_MS && da >= DojoCatalog.MIN_FIGHT_DAMAGE
                && db >= DojoCatalog.MIN_FIGHT_DAMAGE;
        String pair = b.a.compareTo(b.b) < 0 ? b.a + "|" + b.b : b.b + "|" + b.a;
        // Two separate cooldowns: ranked credit (only ranked spars set it) and host / training points.
        Long lastRanked = PAIR.get("ranked|" + pair);
        boolean rankedFresh = lastRanked == null || now - lastRanked >= DojoCatalog.PAIR_COOLDOWN_MS;
        Long lastHost = PAIR.get("host|" + pair);
        boolean pairFresh = lastHost == null || now - lastHost >= DojoCatalog.PAIR_COOLDOWN_MS;
        boolean credit = b.ranked && counted && rankedFresh;
        if (ra != null) {
            ra.boardCredit = credit;
        }
        if (rb != null) {
            rb.boardCredit = credit;
        }
        DojoStore store = DojoStore.get();
        DojoStore.PlayerRecord pa = store.player(b.a.toString(), b.nameA);
        DojoStore.PlayerRecord pb = store.player(b.b.toString(), b.nameB);
        Dojo host = DojoService.byMaster(b.host);
        Dojo dojoA = DojoService.dojoOf(b.a);
        Dojo dojoB = DojoService.dojoOf(b.b);
        if (counted && winner != null) {
            // 4.7.80 weekly goal: a counted board spar win.
            com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals.note(winner, com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals.SPAR_WINS, 1);
        }
        if (counted && !b.ranked) {
            pa.unrankedSpars++;
            pb.unrankedSpars++;
        }
        if (counted && pairFresh) {
            PAIR.put("host|" + pair, now);
            if (host != null) {
                DojoService.earn(host, DojoCatalog.Earn.HOST);
                DojoService.clout(host, "host", DojoCatalog.CLOUT_HOST, DojoCatalog.CLOUT_HOST_DAILY);
                host.hostedSpars++;
            }
            // Training with a disciple: the master and their own disciple.
            for (Dojo d : new Dojo[] {dojoA, dojoB}) {
                if (d != null && ((DojoService.isMaster(b.a, d) && DojoService.disciples(d).contains(b.b.toString()))
                        || (DojoService.isMaster(b.b, d) && DojoService.disciples(d).contains(b.a.toString())))) {
                    DojoService.earn(d, DojoCatalog.Earn.TRAINING);
                    break;
                }
            }
        }
        if (credit) {
            PAIR.put("ranked|" + pair, now);
            pa.rankedSpars++;
            pb.rankedSpars++;
            for (Dojo d : new Dojo[] {dojoA, dojoB}) {
                DojoService.clout(d, "member", DojoCatalog.CLOUT_MEMBER_SPAR, DojoCatalog.CLOUT_MEMBER_DAILY);
            }
            if (winner != null) {
                DojoStore.PlayerRecord w = winner.equals(b.a) ? pa : pb;
                Dojo wd = winner.equals(b.a) ? dojoA : dojoB;
                w.rankedWins++;
                if (!w.firstRankedWinPaid && wd != null && !DojoService.isMaster(winner, wd)) {
                    w.firstRankedWinPaid = true;
                    DojoService.earn(wd, DojoCatalog.Earn.MILESTONE);
                }
            }
        }
        String wagerLine = settleWager(b, winner, wagerPayee(winner, how), dojoA, dojoB);
        store.markDirty();
        for (UUID me : new UUID[] {b.a, b.b}) {
            boolean isA = me.equals(b.a);
            UUID opp = isA ? b.b : b.a;
            String oppName = isA ? b.nameB : b.nameA;
            SparPlayerRuntime rt = isA ? ra : rb;
            List<String> lines = new ArrayList<>();
            String verdict = winner == null ? "Draw" : winner.equals(me) ? "You won" : "You lost";
            lines.add(UltraStyle.header(verdict + " - " + howText(how)));
            lines.add(UltraStyle.labelValue("Opponent", oppName));
            lines.add(UltraStyle.labelValue("Kind", b.ranked ? "Ranked" : "Friendly"));
            lines.add(UltraStyle.labelValue("Fight time", (dur / 1000L) + "s"));
            lines.add(UltraStyle.labelValue("TP earned", b.ranked && rt != null ? String.valueOf((long) rt.sessionTp)
                    : "none (friendly spars pay nothing)"));
            lines.add(UltraStyle.labelValue("Wager", wagerLine));
            lines.add(UltraStyle.labelValue("Potential Unlock", (isA ? b.potentialA : b.potentialB)
                    ? "trained this spar" : b.ranked ? "off (ranked spars never train it)" : "off (not looked for)"));
            if (!counted) {
                // 4.7.84 (SP-4): a forfeit or a disconnect never counts, however long the fight ran.
                lines.add(UltraStyle.LABEL + ("FORFEIT".equals(how) ? "Didn't count for ranking: a forfeit never counts."
                        : "DISCONNECT".equals(how) ? "Didn't count: someone disconnected. Any wager was refunded."
                        : "Didn't count: a spar counts after 1 minute with both fighters landing hits."));
            } else if (b.ranked && !rankedFresh) {
                lines.add(UltraStyle.LABEL + "No ranked credit: you two had a ranked spar in the last 15 minutes.");
            }
            DojoStore.PlayerRecord rec = isA ? pa : pb;
            if (!rankedUnlocked(me)) {
                lines.add(UltraStyle.labelValue("Friendly spars", rec.unrankedSpars + " of " + DojoCatalog.RANKED_GATE
                        + " for ranked"));
            }
            RESULTS.put(me, new Result(opp, oppName, b.ranked, b.wagerKind, b.wager, lines));
        }
        AdaptiveDifficultyMod.LOGGER.info("[{}] board bout {} {} vs {} -> {} by {} counted={} credit={}",
                AdaptiveDifficultyMod.MOD_ID, b.id, b.nameA, b.nameB,
                winner == null ? "draw" : (winner.equals(b.a) ? b.nameA : b.nameB), how, counted, credit);
    }

    /**
     * 4.7.84 (P2-1): who takes both stakes. The winner on a KO, a ring-out, a forfeit and a timeout one fighter led on
     * damage; nobody (both refunded) on a disconnect or a dead-even timeout ({@code winner} null). A crash or restart
     * refunds through {@link #refundStaleEscrow}.
     */
    public static UUID wagerPayee(UUID winner, String how) {
        return winner != null && !"DISCONNECT".equals(how) ? winner : null;
    }

    static String howText(String how) {
        return switch (how) {
            case "KO" -> "knockout";
            case "RING_OUT" -> "ring-out";
            case "TIMEOUT" -> "time ran out";
            case "FORFEIT" -> "forfeit";
            case "DISCONNECT" -> "disconnect";
            default -> how.toLowerCase(java.util.Locale.ROOT);
        };
    }

    /** Pay the winner both stakes, or refund both. Returns the line for the result screen. */
    static String settleWager(Bout b, UUID winner, UUID payTo, Dojo dojoA, Dojo dojoB) {
        DojoStore.Escrow e = DojoStore.get().escrow.remove(b.a + "|" + b.b);
        if (e == null || e.amount <= 0L) {
            return "none";
        }
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        String text = wagerText(e.kind, e.amount);
        if (payTo != null) {
            if ("COINS".equals(e.kind)) {
                payCoins(server, payTo.toString(), e.amount * 2);
            } else {
                Dojo wd = payTo.equals(b.a) ? DojoService.byMaster(e.aDojo) : DojoService.byMaster(e.bDojo);
                if (wd != null) {
                    wd.points += (int) (e.amount * 2);
                }
            }
            return (payTo.equals(b.a) ? b.nameA : b.nameB) + " took both stakes (" + text + " x2)";
        }
        if ("COINS".equals(e.kind)) {
            payCoins(server, e.a, e.amount);
            payCoins(server, e.b, e.amount);
        } else {
            Dojo x = DojoService.byMaster(e.aDojo);
            Dojo y = DojoService.byMaster(e.bDojo);
            if (x != null) {
                x.points += (int) e.amount;
            }
            if (y != null) {
                y.points += (int) e.amount;
            }
        }
        return "refunded to both (" + text + " each)";
    }

    /** Coins to a player now, or kept owed until their next login. */
    static void payCoins(MinecraftServer server, String uuid, long copper) {
        ServerPlayer p = server == null ? null : server.m_6846_().m_11259_(UUID.fromString(uuid));
        if (p != null) {
            giveCoins(p, copper);
            return;
        }
        DojoStore.Escrow owed = new DojoStore.Escrow();
        owed.kind = "OWED";
        owed.amount = copper;
        owed.a = uuid;
        owed.at = System.currentTimeMillis();
        DojoStore.get().escrow.put("owed|" + uuid + "|" + owed.at, owed);
    }

    /** Login: pay coins owed, return a player left in the dojo world, refund a stale escrow. */
    public static void onLogin(ServerPlayer p) {
        String id = p.m_20148_().toString();
        for (Map.Entry<String, DojoStore.Escrow> e : new ArrayList<>(DojoStore.get().escrow.entrySet())) {
            DojoStore.Escrow x = e.getValue();
            if ("OWED".equals(x.kind) && id.equals(x.a)) {
                giveCoins(p, x.amount);
                DojoStore.get().escrow.remove(e.getKey());
                DojoStore.get().markDirty();
                long amount = x.amount;
                com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.whenClientKnown(p, () -> winningsNotice(p, amount));
            }
        }
        if (!inBout(p.m_20148_()) && !DojoWar.fighting(p.m_20148_()) && DojoWorld.hasSpot(p.m_20148_())
                && DojoWorld.inDojoWorld(p)) {
            DojoWorld.returnPlayer(p);
        }
        String nudge = loginNudge(DojoStore.get().dojos.get(id));
        if (nudge != null) {
            com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.whenClientKnown(p, () -> nudgeNotice(p, nudge));
        }
    }

    /** 4.7.87: wager winnings paid at login - chat, or the coin toast with the same words and amount. */
    public static void winningsNotice(ServerPlayer p, long amount) {
        com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.toastOrChat(p, LmChat.ok("Spar", "Your wager winnings arrived: "
                + AncientCoinEconomy.format(amount) + "."), com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.COINS,
                com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.ACCENT_CURRENCY, "",
                com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.WAR_TOAST_MS);
    }

    /** 4.7.87: the dojo login nudge - chat, or a dojo toast with the same words that opens the Dojo. */
    public static void nudgeNotice(ServerPlayer p, String nudge) {
        com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.toastOrChat(p, LmChat.info("Dojo", nudge),
                com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.WAR,
                com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.accentOf("dojo"), "go:dojo|home",
                com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.WAR_REQUEST_MS);
    }

    /**
     * 4.7.84 (DJ-13): the login line for a master whose open dojo has no style or no banner of its own yet (a dojo
     * made from an old mentor bond starts with neither), or null. Both first picks are free.
     */
    public static String loginNudge(Dojo d) {
        if (d == null || !d.open) {
            return null;
        }
        boolean noStyle = d.style == null || d.style.isBlank();
        boolean noBanner = DojoService.defaultBanner(d);
        if (!noStyle && !noBanner) {
            return null;
        }
        String what = noStyle && noBanner ? "a fighting style and a banner" : noStyle ? "a fighting style" : "a banner";
        return "Your dojo " + d.name + " still needs " + what + ". " + (noStyle && noBanner ? "Both are" : "It's")
                + " free the first time: open Dojo from the main menu.";
    }

    /** Server start: any escrow left from a crash is refunded (a bout cannot survive a restart). */
    public static void refundStaleEscrow() {
        for (Map.Entry<String, DojoStore.Escrow> e : new ArrayList<>(DojoStore.get().escrow.entrySet())) {
            DojoStore.Escrow x = e.getValue();
            if ("OWED".equals(x.kind)) {
                continue;
            }
            DojoStore.get().escrow.remove(e.getKey());
            if ("COINS".equals(x.kind)) {
                for (String who : new String[] {x.a, x.b}) {
                    DojoStore.Escrow owed = new DojoStore.Escrow();
                    owed.kind = "OWED";
                    owed.amount = x.amount;
                    owed.a = who;
                    owed.at = System.currentTimeMillis();
                    DojoStore.get().escrow.put("owed|" + who + "|" + owed.at + "|" + e.getKey(), owed);
                }
            } else {
                for (String dojo : new String[] {x.aDojo, x.bDojo}) {
                    Dojo d = DojoService.byMaster(dojo);
                    if (d != null) {
                        d.points += (int) x.amount;
                    }
                }
            }
            DojoStore.get().markDirty();
        }
    }

    public static void onLogout(UUID id) {
        if (id != null) {
            POSTS.remove(id);
            DRAFTS.remove(id);
        }
    }

    public static Result result(UUID id) {
        return id == null ? null : RESULTS.get(id);
    }

    // ------------------------------------------------------------------ menu actions

    /** Spar Board button. Returns the menu line, or null when {@code action} is not a board action. */
    public static String apply(ServerPlayer p, String action, String arg) {
        if (p == null || action == null) {
            return null;
        }
        UUID id = p.m_20148_();
        Draft d = draft(id);
        switch (action) {
            case ACT_MODE -> {
                if (!d.ranked() && !rankedUnlocked(id)) {
                    return "Ranked unlocks after " + DojoCatalog.RANKED_GATE + " friendly spars. You have "
                            + unranked(id) + ".";
                }
                DRAFTS.put(id, new Draft(!d.ranked(), d.wager(), d.range(), d.want()));
                return "Next post: " + (!d.ranked() ? "ranked" : "friendly") + ".";
            }
            case ACT_WAGER_SET -> {
                int step;
                try {
                    step = Integer.parseInt(arg == null ? "" : arg.trim());
                } catch (NumberFormatException e) {
                    step = -1;
                }
                if (step < 0 || step >= wagerSteps()) {
                    return "That wager isn't on the list. Pick one from the Wager menu.";
                }
                if ("POINTS".equals(wagerKind(step))) {
                    Dojo mine = DojoService.dojoOf(p);
                    if (mine == null || !DojoService.canManage(id, mine)) {
                        return "Only a master or an elite can wager Dojo Points.";
                    }
                }
                DRAFTS.put(id, new Draft(d.ranked(), step, d.range(), d.want()));
                return "Wager for your next post: " + wagerText(wagerKind(step), wagerAmount(step)) + ".";
            }
            case ACT_RANGE -> {
                int next = (d.range() + 1) % DojoCatalog.LEVEL_RANGES.length;
                DRAFTS.put(id, new Draft(d.ranked(), d.wager(), next, d.want()));
                return "Opponents: " + rangeText(levelRange(next)) + ".";
            }
            case ACT_WANT -> {
                Want next = d.want().next();
                DRAFTS.put(id, new Draft(d.ranked(), d.wager(), d.range(), next));
                POSTS.computeIfPresent(id, (k, x) -> new Post(x.poster(), x.name(), x.ranked(), x.wagerKind(), x.wager(),
                        x.range(), x.target(), x.targetName(), x.at(), next));
                return "Looking for: " + next.label + "." + (next == Want.POTENTIAL
                        ? " Friendly spars will train your Potential Unlock. Ranked spars never do." : "");
            }
            case ACT_POST -> {
                return post(p, null);
            }
            case ACT_UNPOST -> {
                return POSTS.remove(id) != null ? "Your post is off the board." : "You have no post up.";
            }
            case ACT_CHALLENGE -> {
                return post(p, parse(arg));
            }
            case ACT_ACCEPT -> {
                UUID poster = parse(arg);
                return poster == null ? "That post is gone." : accept(p, poster);
            }
            case ACT_FORFEIT -> {
                return forfeit(p);
            }
            case ACT_REMATCH -> {
                Result r = RESULTS.get(id);
                if (r == null) {
                    return "There's no fight to rematch.";
                }
                int wagerStep = 0;
                for (int i = 1; i < wagerSteps(); i++) {
                    if (wagerKind(i).equals(r.wagerKind()) && wagerAmount(i) == r.wager()) {
                        wagerStep = i;
                    }
                }
                DRAFTS.put(id, new Draft(r.ranked() && rankedUnlocked(id), wagerStep, d.range(), d.want()));
                return post(p, r.opponent());
            }
            default -> {
                return null;
            }
        }
    }

    static UUID parse(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String clock(long ms) {
        long s = Math.max(0L, (ms + 999L) / 1000L);
        return (s / 60L) + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60L);
    }

    public static List<String> selfTest() {
        List<String> bad = new ArrayList<>();
        if (!"".equals(wagerKind(0)) || !"COINS".equals(wagerKind(1)) || !"POINTS".equals(wagerKind(wagerSteps() - 1))) {
            bad.add("wager steps: none, coins, then points");
        }
        if (wagerAmount(DojoCatalog.COIN_STEPS.length) != DojoCatalog.MAX_COIN_WAGER
                || wagerAmount(wagerSteps() - 1) != DojoCatalog.MAX_POINT_WAGER) {
            bad.add("wager steps top out at 5 netherite coins and 20 Dojo Points");
        }
        if (levelRange(0) != 0 || levelRange(1) != 25 || levelRange(4) != 0) {
            bad.add("level ranges cycle any, 25, 50, 100");
        }
        if (Want.TP.next() != Want.POTENTIAL || Want.POTENTIAL.next() != Want.ANY || Want.ANY.next() != Want.TP) {
            bad.add("looking for cycles TP, Potential Unlock, Anything");
        }
        UUID w = new UUID(1L, 2L);
        if (!w.equals(wagerPayee(w, "FORFEIT")) || !w.equals(wagerPayee(w, "TIMEOUT")) || !w.equals(wagerPayee(w, "KO"))
                || wagerPayee(w, "DISCONNECT") != null || wagerPayee(null, "TIMEOUT") != null) {
            bad.add("wagers: the winner takes both on a KO, ring-out, forfeit or a timeout with a damage lead; "
                    + "refunds only on a disconnect or a dead-even timeout");
        }
        if (!"5:00".equals(clock(DojoCatalog.BOUT_TIMEOUT_MS)) || parse("x") != null) {
            bad.add("bout clock and id parsing");
        }
        return bad;
    }
}
