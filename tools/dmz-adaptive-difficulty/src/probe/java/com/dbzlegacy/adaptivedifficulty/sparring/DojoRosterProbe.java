package com.dbzlegacy.adaptivedifficulty.sparring;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.gui.SparGuiApi;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * Test-only probe (4.7.44; ships only in the testprobe jar, {@code -Dlm.probe.dojoRoster}): the Dojo members
 * page showed the append-only season board, so ex-members stayed listed. A master M with apprentices A and B,
 * and a season board for M's dojo holding M, A, B and ex-member X:
 * <ul>
 *   <li>Members (M's view and A's view) list the live roster with board numbers; X is not there.</li>
 *   <li>Previous members list X with his RP / TP / W-L; roster players are not there.</li>
 *   <li>M releases B the real way: B leaves the members page and shows on Previous members, same session.</li>
 *   <li>The /spar lines equal the GUI page lines (apart from the heading); the commands parse.</li>
 *   <li>/spar dojo season advance from the console previews only; a non-staff player cannot run it; the confirm
 *       form ends the season once (season id + 1, one Hall of Chosen record with M's dojo on top, board and
 *       Previous members empty).</li>
 *   <li>The 75-day expiry goes through the same rollover (id + 1, one more Hall of Chosen record).</li>
 * </ul>
 * The store's season, Hall of Chosen and the probe's bonds are restored afterwards. Logs
 * "dojo roster probe check ...: OK|WRONG" and a final OK / FAILED line.
 */
public final class DojoRosterProbe {
    private static final UUID M = UUID.fromString("6c6d2d64-6f6a-6f2d-0000-00000000000a");
    private static final UUID A = UUID.fromString("6c6d2d64-6f6a-6f2d-0000-00000000000b");
    private static final UUID B = UUID.fromString("6c6d2d64-6f6a-6f2d-0000-00000000000c");
    private static final UUID X = UUID.fromString("6c6d2d64-6f6a-6f2d-0000-00000000000d");
    private static int failed;

    private DojoRosterProbe() {}

    public static void run(MinecraftServer server) {
        failed = 0;
        SparStore store = SparStore.get();
        SparStore.DojoSeason savedSeason = store.dojoSeason;
        List<SparStore.DojoSeasonRecord> savedHof = new ArrayList<>(store.dojoHallOfFame);
        try {
            ServerLevel level = server.m_129783_();
            FakePlayer m = FakePlayerFactory.get(level, new GameProfile(M, "ProbeMaster"));
            FakePlayer a = FakePlayerFactory.get(level, new GameProfile(A, "ProbeAlpha"));
            FakePlayer b = FakePlayerFactory.get(level, new GameProfile(B, "ProbeBravo"));
            String mk = M.toString();

            SparStore.MentorBond mb = store.bond(M);
            mb.addApprentice(A.toString(), "ProbeAlpha");
            mb.addApprentice(B.toString(), "ProbeBravo");
            for (UUID ap : new UUID[] {A, B}) {
                SparStore.MentorBond ab = store.bond(ap);
                ab.mentorUuid = mk;
                ab.mentorName = "ProbeMaster";
            }
            SparStore.DojoSeason season = new SparStore.DojoSeason();
            season.seasonId = 41;
            season.startedAt = System.currentTimeMillis() - 10L * 86_400_000L - 60_000L;
            season.leaderboard = new ConcurrentHashMap<>();
            SparStore.DojoEntry e = new SparStore.DojoEntry();
            e.seasonRp = 900;
            e.members = new ConcurrentHashMap<>();
            row(e, M, "ProbeMaster", 400, 3000, 4, 1);
            row(e, A, "ProbeAlpha", 120, 1500, 2, 3);
            row(e, B, "ProbeBravo", 222, 1800, 3, 2);
            row(e, X, "ProbeXray", 333, 2500, 5, 0);
            season.leaderboard.put(mk, e);
            SparStore.DojoEntry other = new SparStore.DojoEntry();
            other.seasonRp = 100;
            season.leaderboard.put("6c6d2d64-6f6a-6f2d-0000-0000000000ff", other);
            store.dojoSeason = season;

            List<String> own = SparGuiApi.ownedDojoMemberLines(m);
            log("members, M's view", own);
            check("members (M): Master M, apprentices A and B, each with board numbers",
                    has(own, "Master", "ProbeMaster") && has(own, "Apprentice", "ProbeAlpha")
                            && has(own, "Apprentice", "ProbeBravo") && has(own, "400 RP") && has(own, "120 RP")
                            && has(own, "222 RP") && has(own, "120 RP", "TP", "2W", "3L"));
            check("members (M): ex-member X is not listed", !has(own, "ProbeXray") && !has(own, "333 RP"));
            List<String> joined = SparGuiApi.joinedDojoMemberLines(a);
            log("members, A's view", joined);
            check("members (A): Master M, You A, Apprentice B with numbers; no X",
                    has(joined, "Master", "ProbeMaster") && has(joined, "You", "ProbeAlpha")
                            && has(joined, "Apprentice", "ProbeBravo") && has(joined, "120 RP")
                            && !has(joined, "ProbeXray"));

            List<String> prevOwn = SparGuiApi.ownedDojoPreviousMemberLines(m);
            log("previous, M's view", prevOwn);
            check("previous (M): only X, with RP / TP / W-L",
                    has(prevOwn, "ProbeXray", "333 RP", "5W", "0L") && !has(prevOwn, "ProbeAlpha")
                            && !has(prevOwn, "ProbeBravo") && !has(prevOwn, "ProbeMaster"));
            List<String> prevJoined = SparGuiApi.joinedDojoPreviousMemberLines(a);
            check("previous (A): only X", has(prevJoined, "ProbeXray") && !has(prevJoined, "ProbeBravo"));

            String released = SparringSystem.removeApprentice(m, "uuid:" + B);
            check("M releases B (" + released + ")", !store.bond(M).isMentoringUuid(B.toString()));
            own = SparGuiApi.ownedDojoMemberLines(m);
            prevOwn = SparGuiApi.ownedDojoPreviousMemberLines(m);
            log("members after the release", own);
            log("previous after the release", prevOwn);
            check("after the release B is gone from members (both views)",
                    !has(own, "ProbeBravo") && !has(SparGuiApi.joinedDojoMemberLines(a), "ProbeBravo"));
            check("after the release B is on previous members with RP / TP / W-L, ranked under X",
                    has(prevOwn, "#1", "ProbeXray") && has(prevOwn, "#2", "ProbeBravo", "222 RP", "3W", "2L"));
            check("the board itself is not pruned", e.members.size() == 4);

            check("/spar dojo members lines = the GUI page (M and A)",
                    body(SparringSystem.dojoMemberLines(m)).equals(body(own))
                            && body(SparringSystem.dojoMemberLines(a)).equals(body(SparGuiApi.joinedDojoMemberLines(a))));
            check("/spar dojo previous-members lines = the GUI page (M and A)",
                    body(SparringSystem.dojoPreviousMemberLines(m)).equals(body(prevOwn))
                            && body(SparringSystem.dojoPreviousMemberLines(a))
                                    .equals(body(SparGuiApi.joinedDojoPreviousMemberLines(a))));
            check("/spar dojo members and previous-members run for a player",
                    cmd(server, m, "spar dojo members") == 1 && cmd(server, m, "spar dojo previous-members") == 1);

            int hof = store.dojoHallOfFame.size();
            check("non-staff player cannot run /spar dojo season advance confirm",
                    cmd(server, a, "spar dojo season advance confirm") <= 0 && store.dojoSeason.seasonId == 41);
            List<String> preview = DojoRankings.seasonAdvancePreviewLines();
            log("season advance preview", preview);
            check("preview names season #41, 10 of 75 days, the leading dojo and the confirm form",
                    has(preview, "#41", "10 of 75 days") && has(preview, "Leading", "900 RP")
                            && has(preview, "/spar dojo season advance confirm"));
            check("console /spar dojo season advance only previews",
                    console(server, "spar dojo season advance") == 1 && store.dojoSeason == season
                            && store.dojoHallOfFame.size() == hof);
            check("console /spar dojo season advance confirm ends the season once",
                    console(server, "spar dojo season advance confirm") == 1 && store.dojoSeason.seasonId == 42
                            && store.dojoHallOfFame.size() == hof + 1
                            && store.dojoHallOfFame.get(0).seasonId == 41
                            && store.dojoHallOfFame.get(0).championUuid.equals(mk)
                            && store.dojoHallOfFame.get(0).championRp == 900);
            check("new season: the board is empty and previous members is empty",
                    store.dojoSeason.leaderboard.isEmpty()
                            && has(SparGuiApi.ownedDojoPreviousMemberLines(m), "No previous members this season."));

            store.dojoSeason.startedAt = System.currentTimeMillis() - DojoRankings.SEASON_MS - 1000L;
            store.dojoSeason.leaderboard.put(mk, e);
            int hof2 = store.dojoHallOfFame.size();
            DojoRankings.ensureSeason();
            check("75-day expiry uses the same rollover: season #43, one more Hall of Chosen record",
                    store.dojoSeason.seasonId == 43 && store.dojoHallOfFame.size() == hof2 + 1
                            && store.dojoHallOfFame.get(0).seasonId == 42);
        } catch (Throwable t) {
            failed++;
            AdaptiveDifficultyMod.LOGGER.warn("[{}] dojo roster probe threw: {}", AdaptiveDifficultyMod.MOD_ID, t.toString(), t);
        } finally {
            store.dojoSeason = savedSeason;
            store.dojoHallOfFame.clear();
            store.dojoHallOfFame.addAll(savedHof);
            for (UUID id : new UUID[] {M, A, B, X}) {
                store.bondsByPlayer.remove(id.toString());
            }
            store.markDirty();
        }
        AdaptiveDifficultyMod.LOGGER.info("[{}] dojo roster probe {}", AdaptiveDifficultyMod.MOD_ID,
                failed == 0 ? "OK" : "FAILED: " + failed);
    }

    private static void row(SparStore.DojoEntry e, UUID id, String name, double rp, double tp, int wins, int losses) {
        SparStore.DojoMemberStats s = new SparStore.DojoMemberStats();
        s.uuid = id.toString();
        s.name = name;
        s.rpContributed = rp;
        s.tp = tp;
        s.wins = wins;
        s.losses = losses;
        e.members.put(s.uuid, s);
    }

    private static int cmd(MinecraftServer server, FakePlayer p, String command) {
        return server.m_129892_().m_230957_(p.m_20203_(), command);
    }

    private static int console(MinecraftServer server, String command) {
        return server.m_129892_().m_230957_(server.m_129893_(), command);
    }

    /** True when one line holds every part. */
    private static boolean has(List<String> lines, String... parts) {
        for (String line : lines) {
            String s = strip(line);
            boolean all = true;
            for (String p : parts) {
                if (!s.contains(p)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    private static List<String> body(List<String> lines) {
        return lines.size() <= 1 ? List.of() : lines.subList(1, lines.size());
    }

    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    private static void check(String what, boolean ok) {
        if (!ok) {
            failed++;
        }
        AdaptiveDifficultyMod.LOGGER.info("[{}] dojo roster probe check {}: {}", AdaptiveDifficultyMod.MOD_ID, what,
                ok ? "OK" : "WRONG");
    }

    private static void log(String step, List<String> lines) {
        AdaptiveDifficultyMod.LOGGER.info("[{}] dojo roster probe {}: {}", AdaptiveDifficultyMod.MOD_ID, step,
                lines.stream().map(DojoRosterProbe::strip).toList());
    }
}
