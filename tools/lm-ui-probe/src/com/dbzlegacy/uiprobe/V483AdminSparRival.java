package com.dbzlegacy.uiprobe;

import com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar;
import com.dbzlegacy.adaptivedifficulty.rival.RivalChallengeManager;
import com.dbzlegacy.adaptivedifficulty.rival.RivalLink;
import com.dbzlegacy.adaptivedifficulty.rival.RivalPlayerRecord;
import com.dbzlegacy.adaptivedifficulty.rival.RivalProgression;
import com.dbzlegacy.adaptivedifficulty.rival.RivalStore;
import com.dbzlegacy.adaptivedifficulty.sparring.SparStore;
import com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * LM 4.8.3 Parts B and C (test only): the new {@code /lm admin spar} leaves (end, board remove, bout cancel, reset)
 * and {@code /lm admin rival} leaves (rp set/add, challenge cancel, season advance). Two stand-in players (Forge fake
 * players, added to the player list like UiProbe's dojo stand-ins) get a spar session, a board post, a bout, a spar
 * record, rival RP and a challenge; every leaf runs once from the console and once as the staff probe player, and the
 * check reads the state back. The season advance runs for real and is then rolled back (every rivalry's RP, the
 * progression season and the archive entry), so the rig's data ends as it started.
 *
 * <p>Lines: {@code V483 SPAR|RIVAL <what>: PASS|FAIL | detail}.
 */
public final class V483AdminSparRival {
    private V483AdminSparRival() {}

    public static void run(ServerPlayer player, Consumer<String> note) {
        MinecraftServer server = player.m_20194_();
        ServerPlayer a = standIn(player, "Probe483A");
        ServerPlayer b = standIn(player, "Probe483B");
        String an = a.m_7755_().getString();
        try {
            CommandSourceStack[] sources = {server.m_129893_().m_81324_(), player.m_20203_()};
            String[] who = {"console", "player"};
            for (int i = 0; i < 2; i++) {
                CommandSourceStack src = sources[i];
                String w = who[i];
                spar(server, src, w, a, b, an, note);
                rival(server, src, w, a, b, an, note);
            }
            seasonAdvance(server, sources, who, a, b, note);
        } catch (Throwable t) {
            note.accept("V483 ERROR: FAIL | " + t);
        } finally {
            cleanup(player, a, b);
        }
    }

    // ------------------------------------------------------------------ spar

    private static void spar(MinecraftServer server, CommandSourceStack src, String w, ServerPlayer a, ServerPlayer b,
            String an, Consumer<String> note) throws Exception {
        UUID ia = a.m_20148_();
        UUID ib = b.m_20148_();

        // end <player>: an open spar session ends for both, and nothing lands on the history board.
        SparringSystem.startBoardSession(a, b, "", false, false, false);
        int recentBefore = SparStore.get().recentFor(ia.toString()).size();
        boolean started = SparringSystem.isSparring(ia) && SparringSystem.isSparring(ib);
        run(server, src, "lm admin spar end " + an);
        check(note, "SPAR", "end <player> (" + w + ") ends the session for both fighters, no history card",
                started && !SparringSystem.isSparring(ia) && !SparringSystem.isSparring(ib)
                        && SparStore.get().recentFor(ia.toString()).size() == recentBefore,
                "started=" + started);
        run(server, src, "lm admin spar end " + an);
        check(note, "SPAR", "end <player> (" + w + ") with no session changes nothing", !SparringSystem.isSparring(ia),
                "");

        // board remove <player>
        postsMap().put(ia, new BoardSpar.Post(ia, an, false, "", 0L, 0, null, "", System.currentTimeMillis(),
                BoardSpar.Want.TP));
        boolean posted = BoardSpar.postOf(ia) != null;
        run(server, src, "lm admin spar board remove " + an);
        check(note, "SPAR", "board remove <player> (" + w + ") takes the post down", posted && BoardSpar.postOf(ia) == null,
                "posted=" + posted);

        // bout cancel <player>: no winner, session closed, bout gone, result says cancelled by staff.
        BoardSpar.Bout bout = new BoardSpar.Bout();
        bout.id = "probe483-" + w + "-" + System.currentTimeMillis();
        bout.a = ia;
        bout.b = ib;
        bout.nameA = an;
        bout.nameB = b.m_7755_().getString();
        bout.host = ia.toString();
        bout.ranked = true;
        bout.startAt = System.currentTimeMillis() - 120_000L;
        boutsMap().put(bout.id, bout);
        boutOfMap().put(ia, bout.id);
        boutOfMap().put(ib, bout.id);
        SparringSystem.startBoardSession(a, b, bout.id, true, false, false);
        boolean inBout = BoardSpar.inBout(ia) && SparringSystem.isSparring(ia);
        run(server, src, "lm admin spar bout cancel " + an);
        BoardSpar.Result ra = BoardSpar.result(ia);
        BoardSpar.Result rb = BoardSpar.result(ib);
        check(note, "SPAR", "bout cancel <player> (" + w + ") clears the bout, ends the spar, both results say cancelled",
                inBout && BoardSpar.boutOf(ia) == null && BoardSpar.boutOf(ib) == null && !SparringSystem.isSparring(ia)
                        && !SparringSystem.isSparring(ib) && cancelled(ra) && cancelled(rb)
                        && !SparringSystem.runtime(ia).boardCredit,
                "inBout=" + inBout + " result=" + (ra == null ? "none" : String.join(" / ", ra.lines())));
        boutsMap().remove(bout.id);
        boutOfMap().remove(ia);
        boutOfMap().remove(ib);

        // reset <player> [confirm]
        SparStore store = SparStore.get();
        SparStore.MentorBond bond = store.bond(ia);
        bond.streakCurrent = 4;
        bond.streakBest = 9;
        SparStore.LeaderboardEntry lb = store.leaderboard.computeIfAbsent(ia.toString(),
                k -> new SparStore.LeaderboardEntry());
        lb.name = an;
        lb.totalTp = 1234;
        lb.bestPayout = 200;
        lb.sessions = 7;
        run(server, src, "lm admin spar reset " + an);
        check(note, "SPAR", "reset <player> (" + w + ") without confirm changes nothing",
                bond.streakCurrent == 4 && lb.totalTp == 1234, "");
        run(server, src, "lm admin spar reset " + an + " confirm");
        check(note, "SPAR", "reset <player> confirm (" + w + ") zeroes streak and spar TP, keeps the spar count",
                bond.streakCurrent == 0 && bond.streakBest == 0 && lb.totalTp == 0 && lb.bestPayout == 0
                        && lb.sessions == 7,
                "streak=" + bond.streakCurrent + " tp=" + lb.totalTp + " sessions=" + lb.sessions);
    }

    private static boolean cancelled(BoardSpar.Result r) {
        return r != null && String.join("\n", r.lines()).contains("Cancelled by staff");
    }

    // ------------------------------------------------------------------ rival

    private static void rival(MinecraftServer server, CommandSourceStack src, String w, ServerPlayer a, ServerPlayer b,
            String an, Consumer<String> note) {
        RivalStore store = RivalStore.get();
        RivalPlayerRecord ra = store.ensurePlayer(a);
        store.ensurePlayer(b);
        RivalLink link = ra.getOrCreateLink(b.m_20148_().toString(), b.m_7755_().getString(), System.currentTimeMillis());
        link.points = 10;
        ra.recalcTotalRp();

        run(server, src, "lm admin rival rp set " + an + " 50");
        check(note, "RIVAL", "rp set <player> 50 (" + w + ") sets total RP to 50", Math.round(ra.totalRp) == 50,
                "rp=" + ra.totalRp);
        run(server, src, "lm admin rival rp add " + an + " 25");
        check(note, "RIVAL", "rp add <player> 25 (" + w + ") adds 25", Math.round(ra.totalRp) == 75, "rp=" + ra.totalRp);
        run(server, src, "lm admin rival rp add " + an + " -30");
        check(note, "RIVAL", "rp add <player> -30 (" + w + ") takes 30", Math.round(ra.totalRp) == 45,
                "rp=" + ra.totalRp);
        run(server, src, "lm admin rival rp add " + an + " -1000");
        check(note, "RIVAL", "rp add <player> -1000 (" + w + ") stops at 0", ra.totalRp == 0.0, "rp=" + ra.totalRp);

        // challenge cancel <player>: a pending request, then a live (counting-down) duel.
        RivalChallengeManager cm = RivalChallengeManager.get();
        String sent = cm.sendChallenge(a, b, 1);
        boolean pending = cm.getRequestInvolving(a.m_20148_()) != null;
        run(server, src, "lm admin rival challenge cancel " + an);
        check(note, "RIVAL", "challenge cancel <player> (" + w + ") drops a pending request",
                pending && cm.getRequestInvolving(a.m_20148_()) == null && cm.getRequestInvolving(b.m_20148_()) == null,
                "pending=" + pending + " | " + sent);
        cm.sendChallenge(a, b, 1);
        String acc = cm.acceptChallenge(b, a.m_7755_().getString());
        boolean live = cm.isInChallenge(a.m_20148_());
        int playedBefore = ra.challengesPlayed;
        run(server, src, "lm admin rival challenge cancel " + an);
        check(note, "RIVAL", "challenge cancel <player> (" + w + ") ends a live duel with no result",
                live && !cm.isInChallenge(a.m_20148_()) && !cm.isInChallenge(b.m_20148_())
                        && ra.challengesPlayed == playedBefore,
                "live=" + live + " | " + acc);
    }

    // ------------------------------------------------------------------ season advance (rolled back after)

    private static void seasonAdvance(MinecraftServer server, CommandSourceStack[] sources, String[] who, ServerPlayer a,
            ServerPlayer b, Consumer<String> note) throws Exception {
        RivalStore store = RivalStore.get();
        RivalProgression prog = RivalProgression.get();
        RivalPlayerRecord ra = store.ensurePlayer(a);
        RivalLink link = ra.getOrCreateLink(b.m_20148_().toString(), b.m_7755_().getString(), System.currentTimeMillis());
        Object season = seasonObject(prog);
        for (int i = 0; i < sources.length; i++) {
            link.points = 30;
            ra.recalcTotalRp();
            Map<RivalLink, Double> points = new HashMap<>();
            for (RivalPlayerRecord r : store.players.values()) {
                for (RivalLink l : r.rivals.values()) {
                    points.put(l, l.points);
                }
            }
            Map<String, Object> saved = snapshot(season);
            int archives = store.seasonArchive.size();
            int id = prog.currentSeasonId();
            try {
                run(server, sources[i], "lm admin rival season advance");
                check(note, "RIVAL", "season advance (" + who[i] + ") without confirm changes nothing",
                        store.seasonArchive.size() == archives && prog.currentSeasonId() == id
                                && Math.round(ra.totalRp) == 30, "");
                run(server, sources[i], "lm admin rival season advance confirm");
                RivalStore.SeasonArchive last = store.seasonArchive.isEmpty() ? null
                        : store.seasonArchive.get(store.seasonArchive.size() - 1);
                boolean archived = last != null && store.seasonArchive.size() == archives + 1 && last.season == id
                        && last.endedDate != null && !last.endedDate.isBlank()
                        && last.standings.stream().anyMatch(s -> a.m_20148_().toString().equals(s.uuid) && s.rp == 30);
                boolean zeroed = true;
                for (RivalPlayerRecord r : store.players.values()) {
                    zeroed &= r.totalRp == 0.0;
                }
                check(note, "RIVAL", "season advance confirm (" + who[i] + ") archives the standings, zeroes all RP, "
                        + "bumps the season", archived && zeroed && prog.currentSeasonId() == id + 1,
                        "season " + id + " -> " + prog.currentSeasonId() + " archived=" + archived + " zeroed=" + zeroed);
            } finally {
                // Roll back: every rivalry's RP, the progression season and the archive entry.
                for (Map.Entry<RivalLink, Double> e : points.entrySet()) {
                    e.getKey().points = e.getValue();
                }
                for (RivalPlayerRecord r : store.players.values()) {
                    r.recalcTotalRp();
                }
                while (store.seasonArchive.size() > archives) {
                    store.seasonArchive.remove(store.seasonArchive.size() - 1);
                }
                season = seasonObject(prog);
                restore(season, saved);
                store.markDirty();
                store.save();
                prog.markDirty();
                prog.save();
            }
            check(note, "RIVAL", "season advance (" + who[i] + ") rolled back for the rig",
                    prog.currentSeasonId() == id && store.seasonArchive.size() == archives
                            && Math.round(ra.totalRp) == 30, "");
        }
    }

    private static final String[] SEASON_FIELDS = {"id", "name", "startedAt", "endsAt", "leaderboard"};

    private static Object seasonObject(RivalProgression prog) throws Exception {
        prog.currentSeasonId();
        java.lang.reflect.Field fd = RivalProgression.class.getDeclaredField("data");
        fd.setAccessible(true);
        Object data = fd.get(prog);
        java.lang.reflect.Field fs = data.getClass().getDeclaredField("season");
        fs.setAccessible(true);
        return fs.get(data);
    }

    private static Map<String, Object> snapshot(Object season) throws Exception {
        Map<String, Object> out = new HashMap<>();
        for (String f : SEASON_FIELDS) {
            java.lang.reflect.Field x = season.getClass().getDeclaredField(f);
            x.setAccessible(true);
            Object v = x.get(season);
            out.put(f, v instanceof Map<?, ?> m ? new java.util.concurrent.ConcurrentHashMap<>(m) : v);
        }
        return out;
    }

    private static void restore(Object season, Map<String, Object> saved) throws Exception {
        for (String f : SEASON_FIELDS) {
            java.lang.reflect.Field x = season.getClass().getDeclaredField(f);
            x.setAccessible(true);
            x.set(season, saved.get(f));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void run(MinecraftServer server, CommandSourceStack src, String line) {
        server.m_129892_().m_230957_(src, line);
    }

    private static void check(Consumer<String> note, String area, String what, boolean ok, String detail) {
        note.accept("V483 " + area + " " + what + ": " + (ok ? "PASS" : "FAIL")
                + (detail == null || detail.isEmpty() ? "" : " | " + detail));
    }

    private static ServerPlayer standIn(ServerPlayer player, String name) {
        com.mojang.authlib.GameProfile gp = new com.mojang.authlib.GameProfile(
                UUID.nameUUIDFromBytes(("uiprobe483:" + name).getBytes(StandardCharsets.UTF_8)), name);
        ServerPlayer p = net.minecraftforge.common.util.FakePlayerFactory.get(player.m_284548_(), gp);
        p.m_6034_(player.m_20185_() + 2, player.m_20186_(), player.m_20189_());
        if (!backing(player).contains(p)) {
            backing(player).add(p);
        }
        byUuid(player).put(p.m_20148_(), p);
        return p;
    }

    private static void cleanup(ServerPlayer player, ServerPlayer a, ServerPlayer b) {
        for (ServerPlayer p : new ServerPlayer[] {a, b}) {
            try {
                UUID id = p.m_20148_();
                RivalChallengeManager.get().staffCancel(player.m_20194_(), id);
                if (SparringSystem.isSparring(id)) {
                    SparringSystem.staffEndSession(p);
                }
                postsMap().remove(id);
                boutOfMap().remove(id);
                SparStore.get().bondsByPlayer.remove(id.toString());
                SparStore.get().leaderboard.remove(id.toString());
                SparStore.get().recentSessions.remove(id.toString());
                RivalStore.get().players.remove(id.toString());
                backing(player).remove(p);
                byUuid(player).remove(id);
            } catch (Throwable ignored) {
            }
        }
        SparStore.get().markDirty();
        RivalStore.get().markDirty();
    }

    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> backing(ServerPlayer player) {
        return (List<ServerPlayer>) field(net.minecraft.server.players.PlayerList.class, "f_11196_",
                player.m_20194_().m_6846_());
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> byUuid(ServerPlayer player) {
        return (Map<UUID, ServerPlayer>) field(net.minecraft.server.players.PlayerList.class, "f_11197_",
                player.m_20194_().m_6846_());
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, BoardSpar.Post> postsMap() {
        return (Map<UUID, BoardSpar.Post>) field(BoardSpar.class, "POSTS", null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, BoardSpar.Bout> boutsMap() {
        return (Map<String, BoardSpar.Bout>) field(BoardSpar.class, "BOUTS", null);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, String> boutOfMap() {
        return (Map<UUID, String>) field(BoardSpar.class, "BOUT_OF", null);
    }

    private static Object field(Class<?> c, String name, Object owner) {
        try {
            java.lang.reflect.Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
