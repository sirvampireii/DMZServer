package com.dbzlegacy.adaptivedifficulty.dojo;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.Dojo;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.WarFight;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.WarRecord;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.LmChat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * LM 4.7.68 dojo wars: a knockout tournament between two dojos, recreated in LM on DragonMineZ's tournament pattern
 * (formats BRACKET and GAUNTLET, qualifier slots a power of 2 from 2 to 16, seeds shuffled, phases RULES,
 * INTERMISSION, PREVIEW, BOUT; KO or ring-out decides, {@value DojoCatalog#RING_OUT_BLOCKS} blocks from the ring
 * forfeits, a bout timeout, forfeit on leave or disconnect, non-lethal, a return teleport after). DMZ's own
 * tournament is never called: the seeds are dojo fighters who sign up when the war starts.
 *
 * <p>Flow: a master (or an elite; an elite needs the master's sign-off unless they hold war rights) declares a war
 * and picks the format; the other dojo's master or an elite accepts; members of both dojos sign up for
 * {@value #SIGNUP_MS} ms; the bracket is drawn and fought in a war ring built for the war. The record keeps the
 * winner, the score (bouts won per dojo) and every fight; challenges expire after a day, so a pair can always
 * challenge again once a war is over.
 */
public final class DojoWar {
    private DojoWar() {}

    public static final long SIGNUP_MS = 60_000L;
    public static final long RULES_MS = 6_000L;
    public static final long INTERMISSION_MS = 8_000L;
    public static final long PREVIEW_MS = 5_000L;
    public static final long BOUT_MS = 180_000L;
    public static final int MAX_SEEDS = 16;
    public static final int MAX_HISTORY = 60;

    public static final String ACT_DECLARE = "war_declare";
    public static final String ACT_SIGNOFF = "war_signoff";
    public static final String ACT_ACCEPT = "war_accept";
    public static final String ACT_DECLINE = "war_decline";
    public static final String ACT_CANCEL = "war_cancel";
    public static final String ACT_JOIN = "war_join";

    /** A war being fought. */
    static final class Run {
        WarRecord rec;
        int ring;
        double cx;
        double cz;
        long signupUntil;
        final Set<UUID> signed = new LinkedHashSet<>();
        final Map<UUID, String> dojoOf = new LinkedHashMap<>();
        final Map<UUID, String> names = new LinkedHashMap<>();
        List<UUID> seeds = new ArrayList<>();
        List<UUID> round = new ArrayList<>();
        List<UUID> next = new ArrayList<>();
        int roundNo;
        int pair;
        UUID holder;
        int nextIdx;
        UUID x;
        UUID y;
        String phase = "SIGNUP";
        long phaseUntil;
        long boutStart;
        boolean rulesShown;
        final Map<UUID, Double> dealt = new ConcurrentHashMap<>();
        volatile boolean boutDone;
        int boutSeq;
    }

    static final Map<String, Run> RUNS = new ConcurrentHashMap<>();
    static final Map<UUID, String> FIGHTER = new ConcurrentHashMap<>();
    private static final Random RNG = new Random();

    static DojoStore store() {
        return DojoStore.get();
    }

    public static boolean fighting(UUID id) {
        return id != null && FIGHTER.containsKey(id);
    }

    /** The running war a dojo is in, or null. */
    public static WarRecord runningFor(String dojo) {
        for (Run r : RUNS.values()) {
            if (r.rec.fromDojo.equals(dojo) || r.rec.toDojo.equals(dojo)) {
                return r.rec;
            }
        }
        return null;
    }

    public static WarRecord byId(String id) {
        for (WarRecord w : store().wars) {
            if (w.id.equals(id)) {
                return w;
            }
        }
        return null;
    }

    static boolean open(WarRecord w) {
        return "PENDING_SIGNOFF".equals(w.status) || "CHALLENGED".equals(w.status) || "RUNNING".equals(w.status);
    }

    /** Wars a dojo is part of, newest first. */
    public static List<WarRecord> warsOf(String dojo) {
        List<WarRecord> out = new ArrayList<>();
        for (WarRecord w : store().wars) {
            if (w.fromDojo.equals(dojo) || w.toDojo.equals(dojo)) {
                out.add(w);
            }
        }
        Collections.reverse(out);
        return out;
    }

    /** Expired challenges stop blocking: the pair can challenge again. Old history is trimmed. */
    public static void prune() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (WarRecord w : store().wars) {
            if (("PENDING_SIGNOFF".equals(w.status) || "CHALLENGED".equals(w.status)) && now > w.expiresAt) {
                w.status = "EXPIRED";
                changed = true;
            }
        }
        while (store().wars.size() > MAX_HISTORY) {
            store().wars.remove(0);
            changed = true;
        }
        if (changed) {
            store().markDirty();
        }
    }

    // ------------------------------------------------------------------ declaring and accepting

    public static String declare(ServerPlayer p, String targetDojo, String format) {
        prune();
        // 4.7.88: the war goes out from the dojo the player is viewing ("Your dojos"), with the rights they have there.
        Dojo mine = DojoService.viewedDojo(p);
        if (mine == null || !DojoService.canManage(p.m_20148_(), mine)) {
            return "Only a master or an elite can declare a war.";
        }
        Dojo them = DojoService.byMaster(targetDojo);
        if (them == null || !them.open) {
            return "That dojo is closed.";
        }
        if (them == mine) {
            return "You can't declare war on your own dojo.";
        }
        String fmt = "GAUNTLET".equalsIgnoreCase(format) ? "GAUNTLET" : "BRACKET";
        for (WarRecord w : store().wars) {
            if (open(w) && ((w.fromDojo.equals(mine.masterUuid) && w.toDojo.equals(them.masterUuid))
                    || (w.fromDojo.equals(them.masterUuid) && w.toDojo.equals(mine.masterUuid)))) {
                return "A war with " + them.name + " is already open.";
            }
        }
        WarRecord w = new WarRecord();
        w.id = "war-" + System.currentTimeMillis() + "-" + Math.abs(RNG.nextInt(10000));
        w.fromDojo = mine.masterUuid;
        w.fromName = mine.name;
        w.toDojo = them.masterUuid;
        w.toName = them.name;
        w.format = fmt;
        w.declaredBy = p.m_20148_().toString();
        w.createdAt = System.currentTimeMillis();
        w.expiresAt = w.createdAt + DojoCatalog.WAR_CHALLENGE_MS;
        boolean needsSignoff = DojoService.isElite(p.m_20148_(), mine)
                && !mine.warRights.contains(p.m_20148_().toString());
        w.status = needsSignoff ? "PENDING_SIGNOFF" : "CHALLENGED";
        store().wars.add(w);
        store().markDirty();
        if (needsSignoff) {
            request(p.m_20194_(), mine.masterUuid, p.m_7755_().getString() + " wants to declare a " + fmt.toLowerCase()
                    + " war on " + them.name + ". Sign it off on Dojo > Wars.");
            return "Sent to your master for sign-off. The war goes out once they approve.";
        }
        notifyChallenged(p.m_20194_(), w);
        return "Declared a " + fmt.toLowerCase() + " war on " + them.name + ". They have a day to accept.";
    }

    static void notifyChallenged(MinecraftServer server, WarRecord w) {
        Dojo them = DojoService.byMaster(w.toDojo);
        if (them == null) {
            return;
        }
        String msg = w.fromName + " declared a " + w.format.toLowerCase() + " war on your dojo. Accept it or turn it down"
                + " on Dojo > Wars.";
        request(server, them.masterUuid, msg);
        for (String e : them.elites) {
            request(server, e, msg);
        }
    }

    public static String signoff(ServerPlayer p, String warId) {
        WarRecord w = byId(warId);
        // 4.7.88: the player's dojo on this war's side (a player can be in two dojos).
        Dojo mine = w == null ? null : DojoService.managedDojoAmong(p.m_20148_(), w.fromDojo);
        if (w == null || !"PENDING_SIGNOFF".equals(w.status)) {
            return "That war isn't waiting for sign-off.";
        }
        if (mine == null || !DojoService.isMaster(p.m_20148_(), mine) || !w.fromDojo.equals(mine.masterUuid)) {
            return "Only your master can sign off a war.";
        }
        w.status = "CHALLENGED";
        w.expiresAt = System.currentTimeMillis() + DojoCatalog.WAR_CHALLENGE_MS;
        store().markDirty();
        notifyChallenged(p.m_20194_(), w);
        return "Signed off. The challenge went to " + w.toName + ".";
    }

    public static String decline(ServerPlayer p, String warId) {
        WarRecord w = byId(warId);
        Dojo mine = w == null ? null : DojoService.managedDojoAmong(p.m_20148_(), w.toDojo, w.fromDojo);
        if (w == null || !open(w) || "RUNNING".equals(w.status) || mine == null
                || !DojoService.canManage(p.m_20148_(), mine)) {
            return "There's no war to turn down.";
        }
        if (w.toDojo.equals(mine.masterUuid)) {
            w.status = "DECLINED";
        } else if (w.fromDojo.equals(mine.masterUuid)) {
            w.status = "CANCELLED";
        } else {
            return "That war isn't yours.";
        }
        store().markDirty();
        return "DECLINED".equals(w.status) ? "You turned down the war." : "You called the war off.";
    }

    /** Accept: the war starts with a sign-up minute, then the bracket. */
    public static String accept(ServerPlayer p, String warId) {
        prune();
        WarRecord w = byId(warId);
        Dojo mine = w == null ? null : DojoService.managedDojoAmong(p.m_20148_(), w.toDojo);
        if (w == null || !"CHALLENGED".equals(w.status)) {
            return "That challenge is no longer open.";
        }
        if (mine == null || !DojoService.canManage(p.m_20148_(), mine) || !w.toDojo.equals(mine.masterUuid)) {
            return "Only the challenged dojo's master or an elite can accept.";
        }
        if (runningFor(w.fromDojo) != null || runningFor(w.toDojo) != null) {
            return "One of the dojos is already in a war. Accept once it ends.";
        }
        ServerLevel level = DojoWorld.level(p.m_20194_());
        if (level == null) {
            return DojoWorld.NOT_LOADED;
        }
        Run r = new Run();
        r.rec = w;
        r.ring = store().nextRing++;
        BlockPos o = DojoWorld.ringOrigin(r.ring);
        r.cx = o.m_123341_() + 0.5;
        r.cz = o.m_123343_() + 0.5;
        r.signupUntil = System.currentTimeMillis() + SIGNUP_MS;
        r.phase = "SIGNUP";
        w.status = "RUNNING";
        w.startedAt = System.currentTimeMillis();
        store().markDirty();
        DojoBuilder.warRing(level, o);
        RUNS.put(w.id, r);
        // 4.7.84 (DJ-6): the chat line says where to join; the Wars tab shows the sign-up card with the countdown.
        String msg = "War! " + w.fromName + " vs " + w.toName + " (" + w.format.toLowerCase() + "). Sign-up is open for "
                + (SIGNUP_MS / 1000L) + " seconds: open Dojo > Wars and tap Join the war.";
        // 4.7.87: the accepter signs up first, so their sign-up pill already reads "Signed up". A HUD player gets the
        // pill (same words, live countdown) instead of the chat line.
        join(p, w.id);
        for (String dojo : new String[] {w.fromDojo, w.toDojo}) {
            Dojo d = DojoService.byMaster(dojo);
            signupNotice(online(p.m_20194_(), dojo), w, msg);
            for (String m : DojoService.disciples(d)) {
                signupNotice(online(p.m_20194_(), m), w, msg);
            }
        }
        return "War accepted. Sign-up is open for one minute.";
    }

    /** A member of either dojo signs up during the sign-up minute. */
    public static String join(ServerPlayer p, String warId) {
        Run r = RUNS.get(warId);
        if (r == null || !"SIGNUP".equals(r.phase)) {
            return "Sign-up for that war is closed.";
        }
        Dojo d = DojoService.memberDojoAmong(p.m_20148_(), r.rec.fromDojo, r.rec.toDojo);
        if (d == null || !(d.masterUuid.equals(r.rec.fromDojo) || d.masterUuid.equals(r.rec.toDojo))) {
            return "Only members of the two dojos can fight in this war.";
        }
        if (BoardSpar.inBout(p.m_20148_())) {
            return "Finish your board spar first.";
        }
        if (!r.signed.add(p.m_20148_())) {
            return "You're already signed up.";
        }
        r.dojoOf.put(p.m_20148_(), d.masterUuid);
        r.names.put(p.m_20148_(), p.m_7755_().getString());
        return "Signed up. The bracket is drawn when sign-up closes.";
    }

    // ------------------------------------------------------------------ the bracket

    /** Largest power of two from 2 to 16 not above {@code n}; 0 when fewer than 2. */
    public static int slots(int n) {
        if (n < 2) {
            return 0;
        }
        int s = 1;
        while (s * 2 <= Math.min(n, MAX_SEEDS)) {
            s *= 2;
        }
        return s;
    }

    /** Pick the seeds: shuffled, a power of two, at least one fighter from each dojo when possible. */
    static List<UUID> draw(Run r, Random rng) {
        List<UUID> pool = new ArrayList<>(r.signed);
        Collections.shuffle(pool, rng);
        int n = slots(pool.size());
        List<UUID> seeds = new ArrayList<>(pool.subList(0, n));
        boolean from = false;
        boolean to = false;
        for (UUID u : seeds) {
            from |= r.rec.fromDojo.equals(r.dojoOf.get(u));
            to |= r.rec.toDojo.equals(r.dojoOf.get(u));
        }
        if (!(from && to)) {
            String missing = from ? r.rec.toDojo : r.rec.fromDojo;
            for (UUID u : pool.subList(n, pool.size())) {
                if (missing.equals(r.dojoOf.get(u)) && !seeds.isEmpty()) {
                    seeds.set(seeds.size() - 1, u);
                    break;
                }
            }
        }
        Collections.shuffle(seeds, rng);
        return seeds;
    }

    /** Once a second: the phase clock for every running war. */
    public static void pulse(MinecraftServer server) {
        prune();
        long now = System.currentTimeMillis();
        for (Run r : new ArrayList<>(RUNS.values())) {
            try {
                step(server, r, now);
            } catch (Throwable t) {
                AdaptiveDifficultyMod.LOGGER.error("[{}] dojo war {} step failed: {}", AdaptiveDifficultyMod.MOD_ID,
                        r.rec.id, t.toString());
            }
        }
    }

    static void step(MinecraftServer server, Run r, long now) {
        ServerLevel level = DojoWorld.level(server);
        switch (r.phase) {
            case "SIGNUP" -> {
                if (now < r.signupUntil) {
                    signupHud(server, r);
                    return;
                }
                signupHudHide(server, r);
                r.signed.removeIf(u -> server.m_6846_().m_11259_(u) == null);
                r.seeds = draw(r, RNG);
                boolean from = false;
                boolean to = false;
                for (UUID u : r.seeds) {
                    from |= r.rec.fromDojo.equals(r.dojoOf.get(u));
                    to |= r.rec.toDojo.equals(r.dojoOf.get(u));
                }
                if (r.seeds.size() < 2 || !from || !to || level == null) {
                    r.rec.status = "CANCELLED";
                    r.rec.endedAt = now;
                    store().markDirty();
                    announce(server, r, "The war was called off: each dojo needs at least one fighter signed up.");
                    RUNS.remove(r.rec.id);
                    return;
                }
                for (UUID u : r.seeds) {
                    FIGHTER.put(u, r.rec.id);
                    ServerPlayer p = server.m_6846_().m_11259_(u);
                    if (p != null) {
                        DojoWorld.rememberSpot(p, "war");
                        toStands(p, r, level);
                    }
                }
                if ("GAUNTLET".equals(r.rec.format)) {
                    r.holder = r.seeds.get(0);
                    r.nextIdx = 1;
                } else {
                    r.round = new ArrayList<>(r.seeds);
                    r.roundNo = 1;
                    r.pair = 0;
                }
                announce(server, r, "Sign-up closed: " + r.seeds.size() + " fighters. " + seedLine(r));
                nextBout(server, r, now);
            }
            case "RULES" -> {
                if (now >= r.phaseUntil) {
                    r.phase = "INTERMISSION";
                    r.phaseUntil = now + INTERMISSION_MS;
                }
            }
            case "INTERMISSION" -> {
                if (now >= r.phaseUntil) {
                    r.phase = "PREVIEW";
                    r.phaseUntil = now + PREVIEW_MS;
                    ServerPlayer px = server.m_6846_().m_11259_(r.x);
                    ServerPlayer py = server.m_6846_().m_11259_(r.y);
                    if (px == null || py == null) {
                        finishBout(server, r, px == null ? r.y : r.x, "FORFEIT");
                        return;
                    }
                    DojoWorld.teleport(px, level, r.cx - 8, DojoWorld.FLOOR_Y + 1, r.cz, -90.0f);
                    DojoWorld.teleport(py, level, r.cx + 8, DojoWorld.FLOOR_Y + 1, r.cz, 90.0f);
                    px.m_21153_(px.m_21233_());
                    py.m_21153_(py.m_21233_());
                    announce(server, r, "Next bout: " + name(r, r.x) + " vs " + name(r, r.y) + ".");
                }
            }
            case "PREVIEW" -> {
                if (now >= r.phaseUntil) {
                    r.phase = "BOUT";
                    r.boutStart = now;
                    r.phaseUntil = now + BOUT_MS;
                    r.dealt.clear();
                    announce(server, r, "Fight! KO or ring-out wins. " + (BOUT_MS / 60000L) + " minutes on the clock.");
                }
            }
            case "BOUT" -> {
                for (UUID u : new UUID[] {r.x, r.y}) {
                    ServerPlayer p = server.m_6846_().m_11259_(u);
                    if (p == null) {
                        finishBout(server, r, u.equals(r.x) ? r.y : r.x, "FORFEIT");
                        return;
                    }
                    boolean out = !DojoWorld.inDojoWorld(p) || p.m_20186_() < DojoWorld.FLOOR_Y - 10
                            || Math.hypot(p.m_20185_() - r.cx, p.m_20189_() - r.cz) > DojoCatalog.RING_OUT_BLOCKS;
                    if (out) {
                        finishBout(server, r, u.equals(r.x) ? r.y : r.x, "RING_OUT");
                        return;
                    }
                }
                boutHud(server, r, now);
                if (now >= r.phaseUntil) {
                    double dx = r.dealt.getOrDefault(r.x, 0.0);
                    double dy = r.dealt.getOrDefault(r.y, 0.0);
                    UUID w;
                    if (dx != dy) {
                        w = dx > dy ? r.x : r.y;
                    } else {
                        ServerPlayer px = server.m_6846_().m_11259_(r.x);
                        ServerPlayer py = server.m_6846_().m_11259_(r.y);
                        double hx = px == null ? 0 : px.m_21223_() / Math.max(1f, px.m_21233_());
                        double hy = py == null ? 0 : py.m_21223_() / Math.max(1f, py.m_21233_());
                        w = hy > hx ? r.y : r.x;
                    }
                    finishBout(server, r, w, "TIMEOUT");
                }
            }
            default -> {
            }
        }
    }

    /** Set up the next bout, or end the war when someone holds the final. */
    static void nextBout(MinecraftServer server, Run r, long now) {
        r.boutDone = false;
        r.boutSeq++;
        r.boutStart = 0L;
        if ("GAUNTLET".equals(r.rec.format)) {
            if (r.nextIdx >= r.seeds.size()) {
                endWar(server, r, r.holder);
                return;
            }
            r.x = r.holder;
            r.y = r.seeds.get(r.nextIdx++);
        } else {
            if (r.pair * 2 >= r.round.size()) {
                if (r.next.size() == 1) {
                    endWar(server, r, r.next.get(0));
                    return;
                }
                r.round = new ArrayList<>(r.next);
                r.next = new ArrayList<>();
                r.roundNo++;
                r.pair = 0;
            }
            r.x = r.round.get(r.pair * 2);
            r.y = r.round.get(r.pair * 2 + 1);
            r.pair++;
        }
        if (!r.rulesShown) {
            r.rulesShown = true;
            r.phase = "RULES";
            r.phaseUntil = now + RULES_MS;
            announce(server, r, "Rules: KO or ring-out wins a bout (" + (int) DojoCatalog.RING_OUT_BLOCKS
                    + " blocks from the ring is out). Leaving forfeits. Nobody dies: a lethal blow is a KO.");
        } else {
            r.phase = "INTERMISSION";
            r.phaseUntil = now + INTERMISSION_MS;
        }
    }

    /** A bout is decided: record it, pay the bout, move the winner on. */
    static void finishBout(MinecraftServer server, Run r, UUID winner, String how) {
        if (r.boutDone) {
            return;
        }
        r.boutDone = true;
        for (UUID u : new UUID[] {r.x, r.y}) {
            com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.hide(u == null ? null : server.m_6846_().m_11259_(u), com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.FIGHT);
        }
        UUID loser = winner.equals(r.x) ? r.y : r.x;
        // 4.7.80 weekly goal: both fighters of a war bout took part.
        com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals.note(r.x, com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals.DOJO_WAR, 1);
        com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals.note(r.y, com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals.DOJO_WAR, 1);
        WarFight f = new WarFight();
        f.round = "GAUNTLET".equals(r.rec.format) ? r.nextIdx - 1 : r.roundNo;
        f.a = r.x.toString();
        f.aName = name(r, r.x);
        f.aDojo = r.dojoOf.getOrDefault(r.x, "");
        f.b = r.y.toString();
        f.bName = name(r, r.y);
        f.bDojo = r.dojoOf.getOrDefault(r.y, "");
        f.winner = winner.toString();
        f.winnerName = name(r, winner);
        f.how = how;
        f.durationMs = r.boutStart > 0 ? System.currentTimeMillis() - r.boutStart : 0L;
        r.rec.fights.add(f);
        String wd = r.dojoOf.getOrDefault(winner, "");
        if (wd.equals(r.rec.fromDojo)) {
            r.rec.fromScore++;
        } else if (wd.equals(r.rec.toDojo)) {
            r.rec.toScore++;
        }
        Dojo wdojo = DojoService.byMaster(wd);
        DojoService.earn(wdojo, DojoCatalog.Earn.WAR_BOUT);
        DojoService.clout(wdojo, "warbout", DojoCatalog.CLOUT_WAR_BOUT, 0);
        for (UUID u : new UUID[] {r.x, r.y}) {
            DojoStore.PlayerRecord rec = store().player(u.toString(), name(r, u));
            Dojo d = DojoService.byMaster(r.dojoOf.getOrDefault(u, ""));
            if (!rec.firstWarFightPaid && d != null && !DojoService.isMaster(u, d)) {
                rec.firstWarFightPaid = true;
                DojoService.earn(d, DojoCatalog.Earn.MILESTONE);
            }
        }
        store().markDirty();
        announce(server, r, name(r, winner) + " beats " + name(r, loser) + " by " + BoardSpar.howText(how) + ".");
        ServerLevel level = DojoWorld.level(server);
        for (UUID u : new UUID[] {r.x, r.y}) {
            ServerPlayer p = server.m_6846_().m_11259_(u);
            if (p != null && level != null) {
                p.m_21153_(p.m_21233_());
                toStands(p, r, level);
            }
        }
        if ("GAUNTLET".equals(r.rec.format)) {
            r.holder = winner;
        } else {
            r.next.add(winner);
        }
        // Losers are out: they go home now.
        FIGHTER.remove(loser);
        ServerPlayer lp = server.m_6846_().m_11259_(loser);
        if (lp != null) {
            DojoWorld.returnPlayer(lp);
        }
        nextBout(server, r, System.currentTimeMillis());
    }

    static void endWar(MinecraftServer server, Run r, UUID champion) {
        WarRecord w = r.rec;
        w.status = "DONE";
        w.endedAt = System.currentTimeMillis();
        w.champion = name(r, champion);
        w.winnerDojo = r.dojoOf.getOrDefault(champion, "");
        w.winnerName = w.winnerDojo.equals(w.fromDojo) ? w.fromName : w.toName;
        Dojo win = DojoService.byMaster(w.winnerDojo);
        Dojo lose = DojoService.byMaster(w.winnerDojo.equals(w.fromDojo) ? w.toDojo : w.fromDojo);
        if (win != null) {
            win.warWins++;
            DojoService.earn(win, DojoCatalog.Earn.WAR_WIN);
            DojoService.clout(win, "warwin", DojoCatalog.CLOUT_WAR_WIN, 0);
        }
        if (lose != null) {
            lose.warLosses++;
        }
        store().markDirty();
        announce(server, r, w.winnerName + " wins the war " + w.fromScore + "-" + w.toScore + "! Champion: "
                + w.champion + ".");
        for (UUID u : new ArrayList<>(r.seeds)) {
            FIGHTER.remove(u);
            ServerPlayer p = server.m_6846_().m_11259_(u);
            if (p != null) {
                p.m_21153_(p.m_21233_());
                DojoWorld.returnPlayer(p);
            }
        }
        RUNS.remove(w.id);
    }

    /**
     * 4.8.3 (staff {@code /lm admin dojo war cancel}): call off every open war between the dojos of {@code dojoA} and
     * {@code dojoB} (master UUIDs, either order): a running one (sign-up or bracket) or a stuck one (RUNNING with no
     * live run after a restart), and any pending challenge. Fighters are healed and put back where they stood, the
     * way a war end does, and the HUD pills go; the war is marked CANCELLED with no winner, no points, no clout and
     * no win or loss. Returns how many wars were called off.
     */
    public static int staffCancel(MinecraftServer server, String dojoA, String dojoB) {
        if (dojoA == null || dojoB == null) {
            return 0;
        }
        int n = 0;
        long now = System.currentTimeMillis();
        for (WarRecord w : new ArrayList<>(store().wars)) {
            boolean pair = (w.fromDojo.equals(dojoA) && w.toDojo.equals(dojoB))
                    || (w.fromDojo.equals(dojoB) && w.toDojo.equals(dojoA));
            if (!pair || !open(w)) {
                continue;
            }
            Run r = RUNS.remove(w.id);
            if (r != null) {
                stopRun(server, r, "Staff called off the war between " + w.fromName + " and " + w.toName
                        + ". No result is recorded.");
            }
            w.status = "CANCELLED";
            w.endedAt = now;
            n++;
        }
        if (n > 0) {
            store().markDirty();
        }
        return n;
    }

    /**
     * 4.8.3 (staff disband): call off every open war {@code dojo} (master UUID) is part of, running or pending, the
     * same way as {@link #staffCancel}. Returns how many.
     */
    public static int staffCancelAll(MinecraftServer server, String dojo) {
        int n = 0;
        for (WarRecord w : new ArrayList<>(store().wars)) {
            if (open(w) && (w.fromDojo.equals(dojo) || w.toDojo.equals(dojo))) {
                n += staffCancel(server, w.fromDojo, w.toDojo);
            }
        }
        return n;
    }

    /** 4.8.3: stop a live run with no result: pills off, a last line to the signed-up, fighters healed and home. */
    private static void stopRun(MinecraftServer server, Run r, String text) {
        if (server == null) {
            for (UUID u : r.seeds) {
                FIGHTER.remove(u);
            }
            FIGHTER.values().removeIf(id -> id.equals(r.rec.id));
            return;
        }
        signupHudHide(server, r);
        for (UUID u : new UUID[] {r.x, r.y}) {
            if (u != null) {
                com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.hide(server.m_6846_().m_11259_(u),
                        com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.FIGHT);
            }
        }
        announce(server, r, text);
        r.boutDone = true;
        for (UUID u : new ArrayList<>(r.seeds)) {
            boolean fighting = r.rec.id.equals(FIGHTER.remove(u));
            ServerPlayer p = server.m_6846_().m_11259_(u);
            if (p != null && fighting) {
                p.m_21153_(p.m_21233_());
                DojoWorld.returnPlayer(p);
            }
        }
        FIGHTER.values().removeIf(id -> id.equals(r.rec.id));
    }

    static void toStands(ServerPlayer p, Run r, ServerLevel level) {
        double x = r.cx - 15 + RNG.nextInt(30);
        DojoWorld.teleport(p, level, x, DojoWorld.FLOOR_Y + 5, r.cz + 26, 180.0f);
    }

    /**
     * 4.7.75: the fight pill for the two fighters of a war bout (names, damage dealt, clock). The bout announcements
     * stay in chat for players without the HUD; 4.7.87: a HUD player reads them as war toasts (the record is Wars).
     */
    static void boutHud(MinecraftServer server, Run r, long now) {
        for (UUID u : new UUID[] {r.x, r.y}) {
            ServerPlayer p = u == null ? null : server.m_6846_().m_11259_(u);
            if (!com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.active(p)) {
                continue;
            }
            int dx = (int) Math.round(r.dealt.getOrDefault(r.x, 0.0));
            int dy = (int) Math.round(r.dealt.getOrDefault(r.y, 0.0));
            com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.show(p, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.FIGHT, java.util.List.of(
                    com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.color("f", name(r, r.x)) + " " + com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.color("e", String.valueOf(dx)) + " "
                            + com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.color("7", "-") + " " + com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.color("e", String.valueOf(dy)) + " "
                            + com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.color("f", name(r, r.y)),
                    com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.LABEL + "War bout - " + com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.clock(r.phaseUntil - now) + " left"),
                    (float) Math.max(0.0, Math.min(1.0, (r.phaseUntil - now) / (double) BOUT_MS)),
                    com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.accentOf("spar"), "open:spar", 0, false);
        }
    }

    static String name(Run r, UUID u) {
        return u == null ? "?" : r.names.getOrDefault(u, DojoService.nameOf(u.toString()));
    }

    static String seedLine(Run r) {
        List<String> n = new ArrayList<>();
        for (UUID u : r.seeds) {
            n.add(name(r, u));
        }
        return "Order: " + String.join(", ", n) + ".";
    }

    /**
     * 4.7.87: war announcements (sign-up closed, rules, next bout, fight, bout results, the winner) go to the signed-up
     * fighters. A HUD player reads each as a war toast (the war's record is the Wars tab); everyone else in chat.
     */
    static void announce(MinecraftServer server, Run r, String text) {
        for (UUID u : new LinkedHashSet<>(r.signed)) {
            ServerPlayer p = server.m_6846_().m_11259_(u);
            if (p != null) {
                warNotice(p, text, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.WAR_TOAST_MS);
            }
        }
    }

    /** 4.7.87: one war line: chat, or for a HUD player a war toast with the same words that opens Dojo > Wars. */
    public static void warNotice(ServerPlayer p, String text, int ms) {
        com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.toastOrChat(p, LmChat.info("War", UltraStyle.VALUE + text),
                com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.WAR,
                com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.accentOf("dojo"), WARS_ACTION, ms);
    }

    /** Dojo &gt; Wars, where war requests are decided and the war's record lives. */
    public static final String WARS_ACTION = "go:dojo|dojo_wars";

    static void tell(MinecraftServer server, String uuid, String text) {
        ServerPlayer p = online(server, uuid);
        if (p != null) {
            warNotice(p, text, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.WAR_TOAST_MS);
        }
    }

    /** 4.7.87: a request that waits on the player (declare, sign-off): the war toast stays a little longer. */
    static void request(MinecraftServer server, String uuid, String text) {
        ServerPlayer p = online(server, uuid);
        if (p != null) {
            warNotice(p, text, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.WAR_REQUEST_MS);
        }
    }

    static ServerPlayer online(MinecraftServer server, String uuid) {
        try {
            return server == null || uuid == null ? null : server.m_6846_().m_11259_(UUID.fromString(uuid));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 4.7.87: the accept line to a member of either dojo. Chat as before; a HUD player gets the sign-up pill instead
     * (the same words, with the countdown and who signed up, from the 4.7.84 DJ-6 sign-up state).
     */
    public static void signupNotice(ServerPlayer p, WarRecord w, String msg) {
        if (p == null) {
            return;
        }
        if (com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.active(p)) {
            signupPill(p, w);
            return;
        }
        DmzRewards.msg(p, LmChat.info("War", UltraStyle.VALUE + msg));
    }

    /** Members of both dojos online now (masters, elites, disciples), once each. */
    static List<ServerPlayer> members(MinecraftServer server, WarRecord w) {
        List<ServerPlayer> out = new ArrayList<>();
        for (String dojo : new String[] {w.fromDojo, w.toDojo}) {
            Dojo d = DojoService.byMaster(dojo);
            ServerPlayer m = online(server, dojo);
            if (m != null && !out.contains(m)) {
                out.add(m);
            }
            for (String u : DojoService.disciples(d)) {
                ServerPlayer q = online(server, u);
                if (q != null && !out.contains(q)) {
                    out.add(q);
                }
            }
        }
        return out;
    }

    /** Once a second while sign-up runs: the pill for every HUD member (sent only when its text changes). */
    static void signupHud(MinecraftServer server, Run r) {
        for (ServerPlayer p : members(server, r.rec)) {
            if (com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.active(p)) {
                signupPill(p, r.rec);
            }
        }
    }

    /** Sign-up closed: the pill goes (the next war line is a toast). */
    static void signupHudHide(MinecraftServer server, Run r) {
        for (ServerPlayer p : members(server, r.rec)) {
            com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.hide(p, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.WAR);
        }
    }

    /**
     * The sign-up pill: "War! A vs B (bracket).", "Sign-up closes in m:ss.", who signed up from each side, and the
     * way in ("Open Dojo > Wars and tap Join the war." or the join reply once signed). Tapping it opens Dojo > Wars.
     */
    public static void signupPill(ServerPlayer p, WarRecord w) {
        Dojo mine = DojoService.memberDojoAmong(p.m_20148_(), w.fromDojo, w.toDojo);
        boolean ours = mine == null || w.fromDojo.equals(mine.masterUuid);
        String my = ours ? w.fromDojo : w.toDojo;
        String their = ours ? w.toDojo : w.fromDojo;
        long left = signupLeftMs(w.id);
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.VALUE + "War! " + w.fromName + " vs " + w.toName + " (" + w.format.toLowerCase() + ").");
        lines.add(UltraStyle.LABEL + "Sign-up closes in " + UltraStyle.VALUE + BoardSpar.clock(left) + UltraStyle.LABEL
                + ".");
        lines.add(UltraStyle.LABEL + "Signed up: " + signedCount(w.id, my) + " from your dojo, "
                + signedCount(w.id, their) + " from theirs.");
        lines.add(signedUp(w.id, p.m_20148_())
                ? UltraStyle.GREEN + "Signed up. The bracket is drawn when sign-up closes."
                : UltraStyle.YELLOW + "Open Dojo > Wars and tap Join the war.");
        com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.show(p, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.WAR,
                lines, (float) Math.max(0.0, Math.min(1.0, left / (double) SIGNUP_MS)),
                com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.accentOf("dojo"), WARS_ACTION, SIGNUP_PILL_MS, false);
    }

    /** The pill hides itself this long after its last refresh (a refresh comes every second while sign-up runs). */
    static final int SIGNUP_PILL_MS = 3_000;

    // ------------------------------------------------------------------ combat rules

    static Run runOf(UUID id) {
        String w = id == null ? null : FIGHTER.get(id);
        return w == null ? null : RUNS.get(w);
    }

    /** True when {@code a} and {@code b} are the two fighters of a live war bout. */
    public static boolean inBoutTogether(ServerPlayer a, ServerPlayer b) {
        if (a == null || b == null) {
            return false;
        }
        Run r = runOf(a.m_20148_());
        return r != null && "BOUT".equals(r.phase) && !r.boutDone
                && ((a.m_20148_().equals(r.x) && b.m_20148_().equals(r.y))
                        || (a.m_20148_().equals(r.y) && b.m_20148_().equals(r.x)));
    }

    /** Waiting war fighters can't be hit and can't hit; fighters can't be hit outside their bout. */
    public static boolean blocksDamage(ServerPlayer victim, ServerPlayer attacker) {
        boolean v = victim != null && fighting(victim.m_20148_());
        boolean a = attacker != null && fighting(attacker.m_20148_());
        if (!v && !a) {
            return false;
        }
        return !inBoutTogether(victim, attacker);
    }

    /** Count damage for the timeout; returns the amount to apply (a lethal blow is cut to leave 1 HP: a KO). */
    public static float onBoutDamage(ServerPlayer victim, ServerPlayer attacker, float amount) {
        Run r = runOf(victim.m_20148_());
        if (r == null || !inBoutTogether(victim, attacker)) {
            return amount;
        }
        r.dealt.merge(attacker.m_20148_(), (double) amount, Double::sum);
        float hp = victim.m_21223_();
        if (amount < hp) {
            return amount;
        }
        MinecraftServer server = victim.m_20194_();
        UUID w = attacker.m_20148_();
        int seq = r.boutSeq;
        server.m_6937_(new net.minecraft.server.TickTask(server.m_129921_(), () -> {
            if (r.boutSeq == seq && "BOUT".equals(r.phase) && !r.boutDone) {
                finishBout(server, r, w, "KO");
            }
        }));
        return Math.max(0.0f, hp - 1.0f);
    }

    /** A war fighter left: if they were in the bout, the other wins by forfeit (next pulse). */
    public static void onLogout(UUID id) {
        // The bout loop sees the missing player and calls the forfeit; waiting fighters forfeit when called up.
    }

    // ------------------------------------------------------------------ menu

    /** The live bracket for a war: rounds, results, the bout on now. */
    public static List<String> bracketLines(WarRecord w) {
        List<String> out = new ArrayList<>();
        Run r = RUNS.get(w.id);
        out.add(UltraStyle.labelValue(w.fromName + " vs " + w.toName, w.format.toLowerCase() + ", "
                + w.fromScore + "-" + w.toScore));
        if (r != null) {
            long left = Math.max(0L, ("SIGNUP".equals(r.phase) ? r.signupUntil : r.phaseUntil)
                    - System.currentTimeMillis());
            String phase = switch (r.phase) {
                case "SIGNUP" -> "Sign-up open (" + r.signed.size() + " signed up)";
                case "RULES" -> "Rules";
                case "INTERMISSION" -> "Intermission";
                case "PREVIEW" -> "Preview: " + name(r, r.x) + " vs " + name(r, r.y);
                case "BOUT" -> "Bout: " + name(r, r.x) + " vs " + name(r, r.y);
                default -> r.phase;
            };
            out.add(UltraStyle.labelValue("Now", phase + ", " + BoardSpar.clock(left) + " left"));
        }
        // 4.7.84 (DJ-7): the last 4 results (with their round headings), so a long war fits the page.
        int shown = 4;
        int skip = Math.max(0, w.fights.size() - shown);
        if (skip > 0) {
            out.add(UltraStyle.LABEL + skip + (skip == 1 ? " earlier bout" : " earlier bouts") + " not shown.");
        }
        int lastRound = -1;
        for (WarFight f : w.fights.subList(skip, w.fights.size())) {
            if (f.round != lastRound) {
                lastRound = f.round;
                out.add(UltraStyle.header(("GAUNTLET".equals(w.format) ? "Challenger " : "Round ") + f.round));
            }
            out.add(UltraStyle.VALUE + f.aName + " vs " + f.bName + UltraStyle.LABEL + " - " + f.winnerName + " by "
                    + BoardSpar.howText(f.how));
        }
        if ("DONE".equals(w.status)) {
            out.add(UltraStyle.labelValue("Winner", w.winnerName + " (champion " + w.champion + ")"));
        }
        return out;
    }

    public static boolean signupOpen(String warId) {
        Run r = RUNS.get(warId);
        return r != null && "SIGNUP".equals(r.phase);
    }

    public static boolean signedUp(String warId, UUID id) {
        Run r = RUNS.get(warId);
        return r != null && r.signed.contains(id);
    }

    /** 4.7.84 (DJ-6): milliseconds left to sign up for a war, 0 when sign-up is not open. */
    public static long signupLeftMs(String warId) {
        Run r = RUNS.get(warId);
        return r == null || !"SIGNUP".equals(r.phase) ? 0L : Math.max(0L, r.signupUntil - System.currentTimeMillis());
    }

    /** 4.7.84 (DJ-6): fighters from {@code dojo} signed up for a war so far. */
    public static int signedCount(String warId, String dojo) {
        Run r = RUNS.get(warId);
        if (r == null || dojo == null) {
            return 0;
        }
        int n = 0;
        for (String d : r.dojoOf.values()) {
            if (dojo.equals(d)) {
                n++;
            }
        }
        return n;
    }

    /** 4.7.84 (DJ-6): the war of {@code dojo} whose sign-up is open now, or null. */
    public static WarRecord signupFor(String dojo) {
        for (Run r : RUNS.values()) {
            if ("SIGNUP".equals(r.phase) && (r.rec.fromDojo.equals(dojo) || r.rec.toDojo.equals(dojo))) {
                return r.rec;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the old war commands (owner pick a)

    /**
     * 4.7.84 (owner pick a): {@code /lm dojo challenge <player>} and the legacy GUI "dojo challenge" actions declare
     * the same war as the menu ({@code war_declare:<master>:BRACKET}). {@code who} names the other dojo by any of its
     * members online, its master's name or UUID ({@code uuid:} allowed), or its dojo name. Nothing writes the old
     * 2x-ranking-point challenge records any more.
     */
    public static String legacyChallenge(ServerPlayer p, String who) {
        String raw = who == null ? "" : who.trim();
        if (raw.regionMatches(true, 0, "uuid:", 0, 5)) {
            raw = raw.substring(5).trim();
        }
        if (raw.isBlank()) {
            return "Name the dojo to declare war on: a member's name or the dojo's name. Or open Dojo > Wars and tap"
                    + " Declare a war.";
        }
        Dojo them = null;
        try {
            UUID u = UUID.fromString(raw);
            them = DojoService.byMaster(u.toString());
            if (them == null) {
                them = DojoService.dojoOf(u);
            }
        } catch (IllegalArgumentException ignored) {
            // a name, not a UUID
        }
        if (them == null && p.m_20194_() != null) {
            for (ServerPlayer o : p.m_20194_().m_6846_().m_11314_()) {
                if (o.m_7755_().getString().equalsIgnoreCase(raw)) {
                    them = DojoService.dojoOf(o);
                    if (them == null) {
                        return o.m_7755_().getString() + " isn't in a dojo, so there's no dojo to fight.";
                    }
                    break;
                }
            }
        }
        if (them == null) {
            for (Dojo d : store().dojos.values()) {
                if (d.open && (d.name.equalsIgnoreCase(raw) || d.masterName.equalsIgnoreCase(raw))) {
                    them = d;
                    break;
                }
            }
        }
        if (them == null) {
            return "No open dojo goes by " + raw + ". Check the dojo list on Dojo > Wars > Declare a war.";
        }
        return declare(p, them.masterUuid, "BRACKET");
    }

    /**
     * 4.7.84 (owner pick a): {@code /lm dojo accept|decline} and the legacy GUI actions answer the newest war
     * challenge waiting on the player's dojo, the same as Accept / Turn it down on Dojo > Wars.
     */
    public static String legacyAnswer(ServerPlayer p, boolean accept) {
        prune();
        Dojo mine = DojoService.dojoOf(p);
        if (mine == null) {
            return "You're not in a dojo yet, so there's no war to answer.";
        }
        WarRecord newest = null;
        for (WarRecord w : store().wars) {
            if ("CHALLENGED".equals(w.status) && w.toDojo.equals(mine.masterUuid)
                    && (newest == null || w.createdAt >= newest.createdAt)) {
                newest = w;
            }
        }
        if (newest == null) {
            return "No war challenge is waiting for your dojo. Open Dojo > Wars to see your wars.";
        }
        return accept ? accept(p, newest.id) : decline(p, newest.id);
    }

    public static String apply(ServerPlayer p, String action, String arg) {
        if (p == null || action == null) {
            return null;
        }
        String a = arg == null ? "" : arg.trim();
        return switch (action) {
            case ACT_DECLARE -> {
                int c = a.lastIndexOf(':');
                yield c < 0 ? declare(p, a, "BRACKET") : declare(p, a.substring(0, c), a.substring(c + 1));
            }
            case ACT_SIGNOFF -> signoff(p, a);
            case ACT_ACCEPT -> accept(p, a);
            case ACT_DECLINE, ACT_CANCEL -> decline(p, a);
            case ACT_JOIN -> join(p, a);
            default -> null;
        };
    }

    public static List<String> selfTest() {
        List<String> bad = new ArrayList<>();
        if (slots(1) != 0 || slots(2) != 2 || slots(3) != 2 || slots(5) != 4 || slots(16) != 16 || slots(40) != 16) {
            bad.add("war slots: a power of two from 2 to 16");
        }
        Run r = new Run();
        r.rec = new WarRecord();
        r.rec.fromDojo = "A";
        r.rec.toDojo = "B";
        for (int i = 0; i < 6; i++) {
            UUID u = new UUID(7L, i);
            r.signed.add(u);
            r.dojoOf.put(u, i == 5 ? "B" : "A");
        }
        List<UUID> seeds = draw(r, new Random(1));
        boolean hasB = false;
        for (UUID u : seeds) {
            hasB |= "B".equals(r.dojoOf.get(u));
        }
        if (seeds.size() != 4 || !hasB) {
            bad.add("the draw keeps a power of two and at least one fighter from each dojo");
        }
        return bad;
    }
}
