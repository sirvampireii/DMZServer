package com.dbzlegacy.adaptivedifficulty.rival;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.rival.ProvingGrounds.Grounds;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.LmChat;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Official rival challenges: requests, countdown, HP-lost scoring, win/lose/draw payouts.
 */
public final class RivalChallengeManager {
    private static final RivalChallengeManager INSTANCE = new RivalChallengeManager();
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);

    private final Map<String, ChallengeRequest> requests = new ConcurrentHashMap<>();
    private final Map<String, RivalChallenge> activeById = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, PendingHp> pendingHp = new ConcurrentHashMap<>();
    private long lastTickAt;

    public static RivalChallengeManager get() {
        return INSTANCE;
    }

    private RivalChallengeManager() {}

    public boolean isInChallenge(UUID uuid) {
        return uuid != null && activeByPlayer.containsKey(uuid);
    }

    public RivalChallenge getChallenge(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        String id = activeByPlayer.get(uuid);
        return id == null ? null : activeById.get(id);
    }

    public RivalChallenge byId(String id) {
        return id == null ? null : activeById.get(id);
    }

    /** Names of players in a live or countdown challenge, for the spectate picker. */
    public java.util.List<String> activeFighterNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String[] row : activeFighterRows()) {
            if (row[1] != null && !row[1].isBlank() && !names.contains(row[1])) {
                names.add(row[1]);
            }
        }
        return names;
    }

    /**
     * Spectate picker args ({@code uuid:…}) for fighters in a live or countdown challenge.
     * Pending requests are not included. Sorted by display name.
     */
    public java.util.List<String> activeFighterArgs() {
        java.util.List<String[]> rows = activeFighterRows();
        rows.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a[1], b[1]));
        java.util.List<String> out = new java.util.ArrayList<>(rows.size());
        for (String[] row : rows) {
            out.add(row[0]);
        }
        return out;
    }

    private java.util.List<String[]> activeFighterRows() {
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (RivalChallenge ch : activeById.values()) {
            if (!isLiveChallenge(ch)) {
                continue;
            }
            addFighterRow(rows, seen, ch.a, ch.nameA);
            addFighterRow(rows, seen, ch.b, ch.nameB);
        }
        return rows;
    }

    private static boolean isLiveChallenge(RivalChallenge ch) {
        return ch != null
                && ch.status != RivalChallenge.Phase.ENDED
                && (ch.status == RivalChallenge.Phase.ACTIVE || ch.status == RivalChallenge.Phase.COUNTDOWN);
    }

    private static void addFighterRow(
            java.util.List<String[]> rows, java.util.Set<String> seen, java.util.UUID id, String name) {
        if (id == null) {
            return;
        }
        String key = id.toString();
        if (!seen.add(key)) {
            return;
        }
        String shown = name == null || name.isBlank() ? key : name;
        rows.add(new String[] {"uuid:" + key, shown});
    }

    public RivalChallenge anyActive() {
        for (RivalChallenge ch : activeById.values()) {
            if (isLiveChallenge(ch)) {
                return ch;
            }
        }
        return null;
    }

    public ChallengeRequest getRequestInvolving(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (ChallengeRequest req : requests.values()) {
            if (req != null && (uuid.equals(req.from) || uuid.equals(req.to))) {
                return req;
            }
        }
        return null;
    }

    /**
     * Pending duel requests for GUI boards (incoming + outgoing).
     * Tab-separated: counterpartyUuid, name, IN|OUT, expiresAtMs, online(0/1), durationMin, reqId.
     */
    public List<String> pendingRequestCards(ServerPlayer viewer) {
        List<String> out = new ArrayList<>();
        if (viewer == null) {
            return out;
        }
        UUID id = viewer.m_20148_();
        long now = System.currentTimeMillis();
        MinecraftServer server = viewer.m_20194_();
        ChallengeRequest incoming = findIncoming(id);
        if (incoming != null && now <= incoming.expiresAt) {
            out.add(encodePendingCard(incoming, true, server));
        }
        ChallengeRequest outgoing = findOutgoing(id);
        if (outgoing != null && now <= outgoing.expiresAt) {
            out.add(encodePendingCard(outgoing, false, server));
        }
        return out;
    }

    /** Resolve a pending card for {@code viewer} by counterparty picker ({@code uuid:…} or name). */
    public ChallengeRequest findPendingRequestForViewer(ServerPlayer viewer, String pickerArg) {
        if (viewer == null) {
            return null;
        }
        UUID self = viewer.m_20148_();
        ChallengeRequest incoming = findIncoming(self);
        if (incoming != null && matchesCounterparty(incoming.from, pickerArg, incoming.fromName)) {
            return incoming;
        }
        ChallengeRequest outgoing = findOutgoing(self);
        if (outgoing != null && matchesCounterparty(outgoing.to, pickerArg, outgoing.toName)) {
            return outgoing;
        }
        return null;
    }

    public String sendChallenge(ServerPlayer from, ServerPlayer to, int minutes) {
        if (from == null || to == null) {
            return "§cInvalid players.";
        }
        if (from.m_20148_().equals(to.m_20148_())) {
            return "§cYou cannot challenge yourself.";
        }
        if (!DifficultyConfig.get().rivalChallenges) {
            return "§cRival challenges are disabled.";
        }
        long now = System.currentTimeMillis();
        RivalPlayerRecord fromRec = RivalStore.get().ensurePlayer(from);
        RivalPlayerRecord toRec = RivalStore.get().ensurePlayer(to);
        if (fromRec == null || toRec == null) {
            return "§cCould not load rivalry data.";
        }
        if (now - fromRec.lastChallengeEndAt < RivalConstants.CH_BETWEEN_COOLDOWN_MS) {
            long left = RivalConstants.CH_BETWEEN_COOLDOWN_MS - (now - fromRec.lastChallengeEndAt);
            return "§cYou are on challenge cooldown (" + formatMs(left) + ").";
        }
        if (now - toRec.lastChallengeEndAt < RivalConstants.CH_BETWEEN_COOLDOWN_MS) {
            return "§c" + to.m_7755_().getString() + " is on challenge cooldown.";
        }
        if (isInChallenge(from.m_20148_()) || getRequestInvolving(from.m_20148_()) != null) {
            return "§cYou already have a pending or active challenge.";
        }
        if (isInChallenge(to.m_20148_()) || getRequestInvolving(to.m_20148_()) != null) {
            return "§cThat player is already in a challenge.";
        }
        RivalLink link = fromRec.rivals.get(to.m_20148_().toString());
        RivalStatus status = link == null ? RivalStatus.NONE : link.status();
        boolean related = status == RivalStatus.DECLARED
                || status == RivalStatus.MUTUAL
                || status == RivalStatus.NEMESIS
                || status == RivalStatus.PENDING
                || status == RivalStatus.UNKNOWN;
        if (!related && !true /* CH_ALLOW_NON_RIVAL */) {
            return "§cYou can only challenge declared rivals.";
        }
        int mins = Math.max(RivalConstants.CH_MIN_MINUTES, Math.min(RivalConstants.CH_MAX_MINUTES, minutes));
        ChallengeRequest req = new ChallengeRequest();
        req.id = "req-" + NEXT_ID.getAndIncrement();
        req.from = from.m_20148_();
        req.to = to.m_20148_();
        req.fromName = from.m_7755_().getString();
        req.toName = to.m_7755_().getString();
        req.createdAt = now;
        req.expiresAt = now + RivalConstants.CH_REQUEST_EXPIRE_MS;
        req.durationMs = mins * 60_000L;
        storeRequest(req);
        RivalStore.get().saveIfNeeded(now);
        DmzRewards.msg(to, LmChat.note("Challenge", "§e" + req.fromName
                + " §7challenged you for §f" + mins + "§7 min!"));
        DmzRewards.msg(to, LmChat.tip("/rival", "→ Rival"));
        return "§aChallenge sent to §f" + req.toName + "§a (" + mins + " min).";
    }

    public String acceptChallenge(ServerPlayer player) {
        return acceptChallenge(player, null);
    }

    public String acceptChallenge(ServerPlayer player, String counterpartyPicker) {
        ChallengeRequest req = resolveIncomingRequest(player, counterpartyPicker);
        LmLog.event(
                "[{}] rivalAcceptChallenge player={} picker={} requestFound={} from={} to={} expiresAt={} reqId={}",
                AdaptiveDifficultyMod.MOD_ID,
                player == null ? "?" : player.m_7755_().getString(),
                counterpartyPicker,
                req != null,
                req == null ? null : req.fromName,
                req == null ? null : req.toName,
                req == null ? -1L : req.expiresAt,
                req == null ? null : req.id);
        if (req == null) {
            return counterpartyPicker != null && !counterpartyPicker.isBlank()
                    ? "§cNo pending challenge from that player."
                    : "§cNo pending challenge to accept.";
        }
        MinecraftServer server = player.m_20194_();
        ServerPlayer challenger = server == null ? null : server.m_6846_().m_11259_(req.from);
        if (challenger == null) {
            LmLog.event(
                    "[{}] rivalAcceptChallenge challenger offline player={} fromUuid={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    player.m_7755_().getString(),
                    req.from);
            dropRequest(req.id);
            return "§cThe challenger went offline.";
        }
        long now = System.currentTimeMillis();
        RivalPlayerRecord aRec = RivalStore.get().ensurePlayer(challenger);
        RivalPlayerRecord bRec = RivalStore.get().ensurePlayer(player);
        if (aRec != null && now - aRec.lastChallengeEndAt < RivalConstants.CH_BETWEEN_COOLDOWN_MS) {
            return "§cChallenger is on cooldown.";
        }
        if (bRec != null && now - bRec.lastChallengeEndAt < RivalConstants.CH_BETWEEN_COOLDOWN_MS) {
            return "§cYou are on challenge cooldown.";
        }
        dropRequest(req.id);
        teleportInFrontOf(challenger, player);
        RivalChallenge ch = new RivalChallenge();
        ch.id = "ch-" + NEXT_ID.getAndIncrement();
        ch.a = req.from;
        ch.b = req.to;
        ch.nameA = req.fromName;
        ch.nameB = req.toName;
        ch.durationMs = Math.max(RivalConstants.CH_DURATION_MS, req.durationMs);
        ch.status = RivalChallenge.Phase.COUNTDOWN;
        ch.countdownUntil = now + RivalConstants.CH_COUNTDOWN_MS;
        ch.startAt = ch.countdownUntil;
        ch.endsAt = ch.startAt + ch.durationMs;
        activeById.put(ch.id, ch);
        activeByPlayer.put(ch.a, ch.id);
        activeByPlayer.put(ch.b, ch.id);
        broadcast(server, "§e" + ch.nameA + " §7vs §e" + ch.nameB
                + " §8— challenge starts in 5s!");
        DmzRewards.msg(challenger, LmChat.ok("Challenge", "Challenge accepted! Countdown started."));
        return "§aChallenge accepted! Countdown started.";
    }

    public String declineChallenge(ServerPlayer player) {
        return declineChallenge(player, null);
    }

    public String declineChallenge(ServerPlayer player, String counterpartyPicker) {
        ChallengeRequest req = resolveIncomingRequest(player, counterpartyPicker);
        if (req == null) {
            return counterpartyPicker != null && !counterpartyPicker.isBlank()
                    ? "§cNo pending challenge from that player."
                    : "§cNo pending challenge to decline.";
        }
        dropRequest(req.id);
        MinecraftServer server = player.m_20194_();
        ServerPlayer from = server == null ? null : server.m_6846_().m_11259_(req.from);
        if (from != null) {
            DmzRewards.msg(from, LmChat.fail("Challenge", "§f" + player.m_7755_().getString()
                    + " §cdeclined your challenge."));
        }
        return "§eDeclined challenge from " + req.fromName + ".";
    }

    public String cancelChallenge(ServerPlayer player) {
        return cancelChallenge(player, null);
    }

    public String cancelChallenge(ServerPlayer player, String counterpartyPicker) {
        ChallengeRequest outgoing = resolveOutgoingRequest(player, counterpartyPicker);
        if (outgoing != null) {
            dropRequest(outgoing.id);
            return "§eChallenge request cancelled.";
        }
        if (counterpartyPicker != null && !counterpartyPicker.isBlank()) {
            return "§cNo outgoing challenge to that player.";
        }
        RivalChallenge ch = getChallenge(player.m_20148_());
        if (ch == null || ch.status == RivalChallenge.Phase.ENDED) {
            return "§cYou have no active challenge.";
        }
        UUID other = ch.other(player.m_20148_());
        endChallenge(player.m_20194_(), ch, other, player.m_20148_(), false, "forfeit");
        return "§eYou forfeited the challenge.";
    }

    public void queueHurt(ServerPlayer victim, ServerPlayer attacker, boolean ki) {
        if (victim == null || attacker == null) {
            return;
        }
        RivalChallenge ch = getChallenge(victim.m_20148_());
        if (ch == null || ch.status != RivalChallenge.Phase.ACTIVE) {
            return;
        }
        if (!ch.involves(attacker.m_20148_())) {
            return;
        }
        PendingHp pending = new PendingHp();
        pending.attacker = attacker.m_20148_();
        pending.sampleHp = victim.m_21223_();
        pending.ki = ki;
        pending.resolveAt = System.currentTimeMillis() + RivalConstants.CH_PENDING_RESOLVE_MS;
        pendingHp.put(victim.m_20148_(), pending);
    }

    /**
     * Underdog boost (script PG_UNDERDOG_OFFENSE): during an ACTIVE challenge, the fighter who is
     * not the champion and stands on the pair's grounds deals x1.12 melee damage to their
     * opponent. The script multiplied the higher of STR / Strike Power, both melee stats, so ki
     * damage is not boosted. Checked per hit: nothing is stored, and it stops the moment the
     * challenge ends or the fighter leaves the grounds.
     */
    public float underdogDamageScale(ServerPlayer victim, ServerPlayer attacker, boolean kiDamage) {
        if (victim == null || attacker == null || kiDamage || !DifficultyConfig.get().rivalChallenges) {
            return 1.0f;
        }
        RivalChallenge ch = getChallenge(attacker.m_20148_());
        if (ch == null || ch.status != RivalChallenge.Phase.ACTIVE || !ch.involves(victim.m_20148_())) {
            return 1.0f;
        }
        Grounds pg = pairGroundsOf(attacker.m_20148_(), victim.m_20148_());
        return ProvingGrounds.isUnderdogOnGrounds(attacker, pg) ? (float) ProvingGrounds.UNDERDOG_OFFENSE : 1.0f;
    }

    /** The pair's claim from either player's link (read only). */
    private static Grounds pairGroundsOf(UUID a, UUID b) {
        if (a == null || b == null) {
            return ProvingGrounds.fresh();
        }
        RivalStore store = RivalStore.get();
        RivalPlayerRecord recA = store.get(a.toString());
        RivalPlayerRecord recB = store.get(b.toString());
        RivalLink ab = recA == null || recA.rivals == null ? null : recA.rivals.get(b.toString());
        RivalLink ba = recB == null || recB.rivals == null ? null : recB.rivals.get(a.toString());
        return ProvingGrounds.pairGrounds(ab, ba);
    }

    public void onDeath(ServerPlayer victim, ServerPlayer killer) {
        if (victim == null) {
            return;
        }
        RivalChallenge ch = getChallenge(victim.m_20148_());
        if (ch == null || ch.status != RivalChallenge.Phase.ACTIVE) {
            return;
        }
        UUID winner = killer != null && ch.involves(killer.m_20148_())
                ? killer.m_20148_()
                : ch.other(victim.m_20148_());
        endChallenge(victim.m_20194_(), ch, winner, victim.m_20148_(), true, "knockout");
    }

    public void pulse(MinecraftServer server, long now) {
        if (server == null) {
            return;
        }
        expireRequests(now);
        resolvePendingHp(server, now);
        if (now - lastTickAt < RivalConstants.CH_TICK_MS && !activeById.isEmpty()) {
            // still allow end checks at least every tick window
        }
        if (now - lastTickAt < RivalConstants.CH_TICK_MS) {
            return;
        }
        lastTickAt = now;

        for (RivalChallenge ch : activeById.values()) {
            if (ch == null || ch.status == RivalChallenge.Phase.ENDED) {
                continue;
            }
            ServerPlayer a = server.m_6846_().m_11259_(ch.a);
            ServerPlayer b = server.m_6846_().m_11259_(ch.b);
            if (a == null || b == null) {
                UUID winner = a != null ? ch.a : (b != null ? ch.b : null);
                UUID loser = a == null ? ch.a : ch.b;
                endChallenge(server, ch, winner, loser, false, "disconnect");
                continue;
            }
            if (ch.status == RivalChallenge.Phase.COUNTDOWN) {
                if (now >= ch.countdownUntil) {
                    if (a == null || b == null) {
                        endChallenge(server, ch, a != null ? ch.a : ch.b, a == null ? ch.a : ch.b, false, "disconnect");
                        continue;
                    }
                    ch.status = RivalChallenge.Phase.ACTIVE;
                    ch.startAt = now;
                    ch.endsAt = now + ch.durationMs;
                    broadcast(server, "§6FIGHT! §e" + ch.nameA + " §7vs §e" + ch.nameB
                            + " §8(" + (ch.durationMs / 1000L) + "s)");
                    Grounds startPg = pairGroundsOf(ch.a, ch.b);
                    ProvingGrounds.underdogNotice(a, startPg);
                    ProvingGrounds.underdogNotice(b, startPg);
                }
                continue;
            }
            if (ch.status == RivalChallenge.Phase.ACTIVE) {
                if (distance(a, b) > RivalConstants.CH_MAX_DISTANCE * 2.0) {
                    // Script: warn only — fight continues (do not force-end as draw).
                    if (now - ch.lastBroadcastAt >= 3_000L) {
                        ch.lastBroadcastAt = now;
                        DmzRewards.msg(a, LmChat.fail("Challenge", "Too far apart! Return within range!"));
                        DmzRewards.msg(b, LmChat.fail("Challenge", "Too far apart! Return within range!"));
                    }
                }
                if (now >= ch.endsAt) {
                    finishByDamage(server, ch);
                    continue;
                }
                long scoreInterval = ch.durationMs > RivalConstants.CH_LONG_FIGHT_MS
                        ? RivalConstants.CH_BROADCAST_SCORE_LONG_MS
                        : RivalConstants.CH_BROADCAST_SCORE_MS;
                if (now - ch.lastBroadcastAt >= scoreInterval) {
                    ch.lastBroadcastAt = now;
                    DmzRewards.msg(a, scoreLine(ch));
                    DmzRewards.msg(b, scoreLine(ch));
                }
            }
        }
        // purge ended
        Iterator<Map.Entry<String, RivalChallenge>> it = activeById.entrySet().iterator();
        while (it.hasNext()) {
            RivalChallenge ch = it.next().getValue();
            if (ch != null && ch.status == RivalChallenge.Phase.ENDED) {
                it.remove();
            }
        }
    }

    public void cleanupPlayer(UUID uuid) {
        if (uuid == null) {
            return;
        }
        pendingHp.remove(uuid);
        RivalChallenge ch = getChallenge(uuid);
        if (ch != null && ch.status != RivalChallenge.Phase.ENDED) {
            endChallenge(null, ch, ch.other(uuid), uuid, false, "logout");
        }
    }

    private void finishByDamage(MinecraftServer server, RivalChallenge ch) {
        double da = ch.damageA;
        double db = ch.damageB;
        if (Math.abs(da - db) < 1.0) {
            endChallenge(server, ch, null, null, false, "draw");
        } else if (da > db) {
            endChallenge(server, ch, ch.a, ch.b, false, "damage");
        } else {
            endChallenge(server, ch, ch.b, ch.a, false, "damage");
        }
    }

    private void endChallenge(
            MinecraftServer server,
            RivalChallenge ch,
            UUID winner,
            UUID loser,
            boolean knockout,
            String reason
    ) {
        if (ch == null || ch.status == RivalChallenge.Phase.ENDED) {
            return;
        }
        ch.status = RivalChallenge.Phase.ENDED;
        ch.knockout = knockout;
        ch.endReason = reason == null ? "" : reason;
        ch.winner = winner;
        ch.loser = loser;
        activeByPlayer.remove(ch.a);
        activeByPlayer.remove(ch.b);
        long now = System.currentTimeMillis();

        RivalStore store = RivalStore.get();
        RivalPlayerRecord recA = store.get(ch.a == null ? null : ch.a.toString());
        RivalPlayerRecord recB = store.get(ch.b == null ? null : ch.b.toString());
        RivalChallenge.Combat cA = ch.combatOf(ch.a);
        RivalChallenge.Combat cB = ch.combatOf(ch.b);
        if (recA != null) {
            recA.lastChallengeEndAt = now;
            recA.challengesPlayed++;
            accumulateCareer(recA, cA);
        }
        if (recB != null) {
            recB.lastChallengeEndAt = now;
            recB.challengesPlayed++;
            accumulateCareer(recB, cB);
        }

        ServerPlayer pA = server == null || ch.a == null ? null : server.m_6846_().m_11259_(ch.a);
        ServerPlayer pB = server == null || ch.b == null ? null : server.m_6846_().m_11259_(ch.b);

        boolean related = areRelated(recA, recB, ch);
        long duration = Math.max(0L, now - ch.startAt);

        if ("draw".equals(reason) || "distance".equals(reason) || winner == null) {
            float drawBase = RivalConstants.CH_DRAW_TP;
            boolean drawOnPg = related && recA != null && recB != null && ProvingGrounds.fightingOnGrounds(pA, pB,
                    ProvingGrounds.pairGrounds(recA.rivals.get(recB.uuid), recB.rivals.get(recA.uuid)));
            if (drawOnPg) {
                drawBase = (float) (drawBase * ProvingGrounds.ON_GROUNDS_TP_MULT);
            }
            String drawNote = drawBase > RivalConstants.CH_DRAW_TP ? "Rival Draw (Proving Grounds)" : "Rival Draw";
            if (pA != null) {
                float tp = RivalTpCurve.scale(pA, drawBase, "burst");
                DmzRewards.awardTp(pA, tp, drawNote, true, "Challenge");
            }
            if (pB != null) {
                float tp = RivalTpCurve.scale(pB, drawBase, "burst");
                DmzRewards.awardTp(pB, tp, drawNote, true, "Challenge");
            }
            if (related && recA != null && recB != null) {
                awardDrawRp(recA, recB, ch, drawOnPg);
                ProvingGrounds.touchDraw(pA, pB, recA, recB,
                        cA.damage, cB.damage, cA.biggestHit, cB.biggestHit, duration);
            }
            deliverReport(server, ch, null, null, true);
            RivalProgression.get().onChallengeEnd(pA, pB, ch, true, false);
            SystemTelemetry.log("rival", "challenge_end", pA, pB,
                    SystemTelemetry.fields("result", "draw", "reason", reason));
            broadcast(server, "§eDRAW §7— §f" + ch.nameA + " §7vs §f" + ch.nameB
                    + " §8(" + reason + ")");
        } else if ("forfeit".equals(reason) || "logout".equals(reason) || "disconnect".equals(reason)) {
            ServerPlayer winP = winner.equals(ch.a) ? pA : pB;
            ServerPlayer loseP = loser != null && loser.equals(ch.a) ? pA : pB;
            float baseWin = related ? RivalConstants.CH_WIN_TP : RivalConstants.CH_NON_RIVAL_WIN_TP;
            float winTp = RivalTpCurve.scale(winP, baseWin, "burst");
            if (winP != null) {
                DmzRewards.awardTp(winP, winTp, "Rival Forfeit Win", true, "Challenge");
            }
            // Script: no participation TP to the forfeit/disconnect loser.
            if (related) {
                applyWinLoss(recA, recB, ch, winner, loser, false, true, null);
            }
            deliverReport(server, ch, winner, loser, false);
            RivalProgression.get().onChallengeEnd(winP, loseP, ch, false, false);
            SystemTelemetry.log("rival", "challenge_end", winP, loseP,
                    SystemTelemetry.fields("result", "forfeit", "reason", reason));
            broadcast(server, "§a" + nameOf(ch, winner) + " §7wins by forfeit vs §c" + nameOf(ch, loser));
        } else {
            float baseWin = (related ? RivalConstants.CH_WIN_TP : RivalConstants.CH_NON_RIVAL_WIN_TP)
                    + (knockout ? RivalConstants.CH_KO_WIN_TP_BONUS : 0);
            ServerPlayer winP = winner.equals(ch.a) ? pA : pB;
            ServerPlayer loseP = loser != null && loser.equals(ch.a) ? pA : pB;
            RivalPlayerRecord winRec = null;
            RivalPlayerRecord loseRec = null;
            ProvingGrounds.Grounds pairPg = ProvingGrounds.fresh();
            if (related && recA != null && recB != null) {
                winRec = winner.toString().equals(recA.uuid) ? recA : recB;
                loseRec = loser.toString().equals(recA.uuid) ? recA : recB;
                // Either side may hold the claim (one-sided links exist in live data).
                pairPg = ProvingGrounds.pairGrounds(winRec.rivals.get(loseRec.uuid), loseRec.rivals.get(winRec.uuid));
            }
            // Script: on grounds when EITHER fighter is inside at the end, read before the claim
            // changes. Winner x1.35 x1.40 (x1.25 more as underdog); loser x1.35.
            boolean onPg = ProvingGrounds.fightingOnGrounds(winP, loseP, pairPg);
            float pgMult = ProvingGrounds.winnerTpMultiplier(pairPg, winner, onPg);
            float loseBase = RivalConstants.CH_LOSE_TP;
            if (onPg) {
                loseBase = (float) (loseBase * ProvingGrounds.ON_GROUNDS_TP_MULT);
            }
            float winTp = RivalTpCurve.scale(winP, baseWin * pgMult, "burst");
            float loseTp = RivalTpCurve.scale(loseP, loseBase, "burst");
            ProvingGrounds.BattleResult pg = null;
            if (related && winRec != null && loseRec != null) {
                RivalChallenge.Combat wC = ch.combatOf(winner);
                RivalChallenge.Combat lC = ch.combatOf(loser);
                pg = ProvingGrounds.processBattle(winP, loseP, winRec, loseRec,
                        wC.damage, lC.damage, wC.biggestHit, lC.biggestHit, duration);
                applyWinLoss(recA, recB, ch, winner, loser, knockout, false, pg);
            }
            // One grant per player: TP notices share a short cooldown, so a second grant right
            // after the first (the reclaim TP) used to be paid but never shown.
            boolean reclaimed = pg != null && pg.reclaimed;
            if (reclaimed && winP != null) {
                winTp += RivalTpCurve.scale(winP, ProvingGrounds.RECLAIM_TP, "burst");
            }
            String pgNote = onPg ? " (Proving Grounds)" : "";
            if (winP != null) {
                DmzRewards.awardTp(winP, winTp, reclaimed ? "Reclaimed Proving Grounds"
                        : (knockout ? "Rival KO Win" : "Rival Win") + pgNote, true, "Challenge");
            }
            if (loseP != null) {
                DmzRewards.awardTp(loseP, loseTp, "Rival Loss" + pgNote, true, "Challenge");
            }
            deliverReport(server, ch, winner, loser, false);
            if ("knockout".equals(reason)) {
                // The only Nemesis increment: one per KO, whatever the killing damage source.
                registerNemesisKo(store, winner, loser, winP, loseP,
                        nameOf(ch, winner), nameOf(ch, loser), now);
            }
            RivalProgression.get().onChallengeEnd(winP, loseP, ch, false, knockout);
            SystemTelemetry.log("rival", "challenge_end", winP, loseP,
                    SystemTelemetry.fields("result", knockout ? "ko" : "win", "reason", reason,
                            "dmgA", (int) ch.damageA, "dmgB", (int) ch.damageB));
            String tag = knockout ? "KO" : "WIN";
            broadcast(server, "§a" + nameOf(ch, winner) + " §7" + tag + " vs §c" + nameOf(ch, loser)
                    + " §8(" + (int) ch.damageOf(winner) + " / " + (int) ch.damageOf(loser) + ")");
        }
        store.markDirty();
    }

    /**
     * Nemesis climb for a challenge knockout: the loser's mutual link toward the winner gains a
     * death loss (the winner's link a death win). At {@link RivalConstants#NEMESIS_DEATH_LOSSES}
     * the winner becomes the loser's Nemesis. Open-world deaths, forfeits, logouts, disconnects,
     * damage wins, and timeouts never reach here. The caller marks the store dirty.
     */
    private static boolean registerNemesisKo(
            RivalStore store,
            UUID winner,
            UUID loser,
            ServerPlayer winP,
            ServerPlayer loseP,
            String winName,
            String loseName,
            long now
    ) {
        if (store == null || winner == null || loser == null) {
            return false;
        }
        RivalPlayerRecord winRec = store.get(winner.toString());
        RivalPlayerRecord loseRec = store.get(loser.toString());
        if (winRec == null || loseRec == null || loseRec.rivals == null) {
            return false;
        }
        RivalLink loseLink = loseRec.rivals.get(winRec.uuid);
        if (loseLink == null || !loseLink.mutual) {
            return false;
        }
        RivalLink winLink = winRec.rivals == null ? null : winRec.rivals.get(loseRec.uuid);
        loseLink.deathLosses++;
        loseLink.touch(now);
        if (winLink != null) {
            winLink.deathWins++;
            winLink.touch(now);
        }
        if (loseLink.deathLosses >= RivalConstants.NEMESIS_DEATH_LOSSES) {
            loseLink.isNemesis = true;
            loseRec.nemesisUuid = winRec.uuid;
            String winShown = winP != null ? winP.m_7755_().getString() : winName;
            String loseShown = loseP != null ? loseP.m_7755_().getString() : loseName;
            if (loseP != null) {
                DmzRewards.msg(loseP, LmChat.fail("Rival", UltraStyle.WHITE + winShown
                        + " " + UltraStyle.RED + "is now your Nemesis (" + loseLink.deathLosses
                        + " challenge deaths)."));
            }
            if (winP != null) {
                DmzRewards.msg(winP, LmChat.note("Rival", UltraStyle.YELLOW + "You became Nemesis to "
                        + UltraStyle.WHITE + loseShown + " " + UltraStyle.GRAY + "(3 challenge deaths)."));
            }
        }
        return true;
    }

    private void deliverReport(
            MinecraftServer server,
            RivalChallenge ch,
            UUID winner,
            UUID loser,
            boolean draw
    ) {
        String report = buildReport(ch, winner, loser, draw);
        ServerPlayer pA = server == null || ch.a == null ? null : server.m_6846_().m_11259_(ch.a);
        ServerPlayer pB = server == null || ch.b == null ? null : server.m_6846_().m_11259_(ch.b);
        if (pA != null) {
            DmzRewards.msg(pA, report);
        }
        if (pB != null) {
            DmzRewards.msg(pB, report);
        }
        if (!draw) {
            if (pA != null) {
                DmzRewards.msg(pA, pA.m_20148_().equals(winner)
                        ? LmChat.ok("Challenge", "Victory!")
                        : LmChat.fail("Challenge", "Defeat!"));
            }
            if (pB != null) {
                DmzRewards.msg(pB, pB.m_20148_().equals(winner)
                        ? LmChat.ok("Challenge", "Victory!")
                        : LmChat.fail("Challenge", "Defeat!"));
            }
        }
    }

    private static String buildReport(
            RivalChallenge ch,
            UUID winner,
            UUID loser,
            boolean draw
    ) {
        java.util.List<String> body = new java.util.ArrayList<>();
        if (draw) {
            body.add("§8Result  §eDraw");
        } else {
            body.add("§8Winner  §a" + nameOf(ch, winner));
            body.add("§8Runner  §c" + nameOf(ch, loser));
        }
        long elapsed = Math.max(0L, System.currentTimeMillis() - ch.startAt);
        body.add("§8Time    §f" + formatMs(elapsed) + " §8  via  §7" + ch.endReason);
        for (UUID id : new UUID[]{ch.a, ch.b}) {
            if (id == null) {
                continue;
            }
            RivalChallenge.Combat combat = ch.combatOf(id);
            String name = nameOf(ch, id);
            body.add("");
            body.add("§e" + name);
            body.add("§8  Damage  §f" + (int) combat.damage
                    + " §8(Phy " + (int) combat.physical + " / Ki " + (int) combat.ki + ")");
            body.add("§8  Hits  §f" + combat.hits
                    + " §8  Best  §f" + (int) combat.biggestHit
                    + " §8  Combo  §f" + combat.longestCombo);
        }
        return LmChat.card("Rival Battle Report", null, null, body.toArray(new String[0]));
    }

    private void applyWinLoss(
            RivalPlayerRecord recA,
            RivalPlayerRecord recB,
            RivalChallenge ch,
            UUID winner,
            UUID loser,
            boolean knockout,
            boolean forfeit,
            ProvingGrounds.BattleResult pg
    ) {
        if (recA == null || recB == null || winner == null || loser == null) {
            return;
        }
        RivalPlayerRecord winRec = winner.toString().equals(recA.uuid) ? recA : recB;
        RivalPlayerRecord loseRec = loser.toString().equals(recA.uuid) ? recA : recB;
        winRec.officialWins++;
        loseRec.officialLosses++;
        winRec.currentWinStreak++;
        if (winRec.currentWinStreak > winRec.bestWinStreak) {
            winRec.bestWinStreak = winRec.currentWinStreak;
        }
        loseRec.currentWinStreak = 0;
        if (knockout) {
            winRec.knockouts++;
        }
        RivalLink wLink = winRec.getOrCreateLink(loseRec.uuid, loseRec.name, System.currentTimeMillis());
        RivalLink lLink = loseRec.getOrCreateLink(winRec.uuid, winRec.name, System.currentTimeMillis());
        wLink.wins++;
        lLink.losses++;
        wLink.lastBattleAt = System.currentTimeMillis();
        lLink.lastBattleAt = wLink.lastBattleAt;
        boolean mutual = wLink.mutual && lLink.mutual;
        // Script: challenge RP only when the pair is mutual.
        if (!mutual) {
            return;
        }
        int winRp = RivalConstants.CH_WIN_RP;
        int loseRp = RivalConstants.CH_LOSE_RP + (knockout ? RivalConstants.CH_KO_LOSE_RP_BONUS : 0);
        if (forfeit) {
            // Script: loser gets -CH_FORFEIT_RP_PENALTY (not reduced positive lose RP).
            loseRp = -RivalConstants.CH_FORFEIT_RP_PENALTY;
            winRp = RivalConstants.CH_WIN_RP;
        } else {
            double minD = Math.min(ch.damageOf(winner), ch.damageOf(loser));
            double maxD = Math.max(1.0, Math.max(ch.damageOf(winner), ch.damageOf(loser)));
            if (minD / maxD >= RivalConstants.CH_CLOSE_BATTLE_RATIO) {
                winRp += RivalConstants.CH_CLOSE_BATTLE_RP;
                loseRp += RivalConstants.CH_CLOSE_BATTLE_RP;
            }
            winRp += longRivalryBonus(wLink);
            loseRp += longRivalryBonus(lLink);
            if (pg != null && (pg.onGrounds || pg.created)) {
                winRp += ProvingGrounds.ON_GROUNDS_RP_BONUS;
                loseRp += ProvingGrounds.ON_GROUNDS_RP_BONUS;
            }
            if (pg != null && pg.reclaimed) {
                winRp += ProvingGrounds.RECLAIM_RP;
            }
        }
        RivalStore.get().addRp(winRec, loseRec.uuid, winRp, "challenge_win");
        RivalStore.get().addRp(loseRec, winRec.uuid, loseRp,
                forfeit ? "forfeit" : "challenge_lose");
    }

    private static void accumulateCareer(RivalPlayerRecord rec, RivalChallenge.Combat combat) {
        if (rec == null || combat == null) {
            return;
        }
        rec.careerDamageDealt += Math.max(0.0, combat.damage);
        rec.careerHits += Math.max(0, combat.hits);
        if (combat.longestCombo > rec.careerHighestCombo) {
            rec.careerHighestCombo = combat.longestCombo;
        }
    }

    private void awardDrawRp(RivalPlayerRecord a, RivalPlayerRecord b, RivalChallenge ch, boolean onGrounds) {
        RivalLink aLink = a.rivals.get(b.uuid);
        RivalLink bLink = b.rivals.get(a.uuid);
        if (aLink != null) {
            aLink.draws++;
            aLink.lastBattleAt = System.currentTimeMillis();
        }
        if (bLink != null) {
            bLink.draws++;
            bLink.lastBattleAt = System.currentTimeMillis();
        }
        a.officialDraws++;
        b.officialDraws++;
        // Script: draw RP only for mutual pairs.
        if (aLink == null || bLink == null || !aLink.mutual || !bLink.mutual) {
            return;
        }
        int rp = RivalConstants.CH_DRAW_RP;
        double minD = Math.min(ch.damageA, ch.damageB);
        double maxD = Math.max(1.0, Math.max(ch.damageA, ch.damageB));
        if (minD / maxD >= RivalConstants.CH_CLOSE_BATTLE_RATIO) {
            rp += RivalConstants.CH_CLOSE_BATTLE_RP;
        }
        rp += longRivalryBonus(aLink);
        // Script: +12 only when either fighter is on the pair's grounds at the draw.
        if (onGrounds) {
            rp += ProvingGrounds.ON_GROUNDS_RP_BONUS;
        }
        RivalStore.get().addRp(a, b.uuid, rp, "challenge_draw");
        RivalStore.get().addRp(b, a.uuid, rp, "challenge_draw");
    }

    private static int longRivalryBonus(RivalLink link) {
        if (link == null || !link.mutual) {
            return 0;
        }
        long since = link.mutualSince > 0L ? link.mutualSince : link.firstMetAt;
        long days = Math.max(0L, (System.currentTimeMillis() - since) / 86_400_000L);
        days = Math.min(RivalConstants.CH_LONG_RIVALRY_DAY_CAP, days);
        return (int) (days * RivalConstants.CH_LONG_RIVALRY_DAY_RP);
    }

    private static boolean areRelated(RivalPlayerRecord a, RivalPlayerRecord b, RivalChallenge ch) {
        if (a == null || b == null) {
            return false;
        }
        RivalLink link = a.rivals.get(b.uuid);
        if (link == null) {
            return false;
        }
        RivalStatus st = link.status();
        return st == RivalStatus.UNKNOWN || st == RivalStatus.DECLARED
                || st == RivalStatus.PENDING || st == RivalStatus.MUTUAL || st == RivalStatus.NEMESIS;
    }

    private void resolvePendingHp(MinecraftServer server, long now) {
        Iterator<Map.Entry<UUID, PendingHp>> it = pendingHp.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PendingHp> e = it.next();
            PendingHp p = e.getValue();
            if (p == null || now < p.resolveAt) {
                continue;
            }
            it.remove();
            ServerPlayer victim = server.m_6846_().m_11259_(e.getKey());
            if (victim == null) {
                continue;
            }
            RivalChallenge ch = getChallenge(e.getKey());
            if (ch == null || ch.status != RivalChallenge.Phase.ACTIVE) {
                continue;
            }
            float lost = Math.max(0.0f, p.sampleHp - victim.m_21223_());
            if (lost > 0.0f) {
                ch.addDamage(p.attacker, lost, p.ki);
            }
        }
    }

    private void expireRequests(long now) {
        Iterator<Map.Entry<String, ChallengeRequest>> it = requests.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ChallengeRequest> e = it.next();
            ChallengeRequest req = e.getValue();
            if (req == null || now > req.expiresAt) {
                it.remove();
                RivalStore.get().removeChallengeRequest(e.getKey());
            }
        }
    }

    /** Hydrate in-memory pending duels after {@link RivalStore#load()}. */
    public void replaceRequestsFromStore(Map<String, RivalStore.StoredChallengeRequest> stored) {
        requests.clear();
        if (stored == null || stored.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (RivalStore.StoredChallengeRequest s : stored.values()) {
            if (s == null || s.id == null || s.id.isBlank() || s.expiresAt <= now) {
                continue;
            }
            ChallengeRequest req = fromStored(s);
            if (req != null) {
                requests.put(req.id, req);
            }
        }
    }

    private static ChallengeRequest fromStored(RivalStore.StoredChallengeRequest s) {
        if (s == null) {
            return null;
        }
        try {
            ChallengeRequest req = new ChallengeRequest();
            req.id = s.id;
            req.from = UUID.fromString(s.from);
            req.to = UUID.fromString(s.to);
            req.fromName = s.fromName == null ? "" : s.fromName;
            req.toName = s.toName == null ? "" : s.toName;
            req.createdAt = s.createdAt;
            req.expiresAt = s.expiresAt;
            req.durationMs = s.durationMs > 0L ? s.durationMs : RivalConstants.CH_DURATION_MS;
            return req;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void storeRequest(ChallengeRequest req) {
        if (req == null || req.id == null || req.id.isBlank()) {
            return;
        }
        requests.put(req.id, req);
        RivalStore.get().upsertChallengeRequest(req);
    }

    private void dropRequest(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        requests.remove(id);
        RivalStore.get().removeChallengeRequest(id);
    }

    private ChallengeRequest findIncoming(UUID to) {
        for (ChallengeRequest req : requests.values()) {
            if (req != null && to.equals(req.to)) {
                return req;
            }
        }
        return null;
    }

    private ChallengeRequest findOutgoing(UUID from) {
        for (ChallengeRequest req : requests.values()) {
            if (req != null && from.equals(req.from)) {
                return req;
            }
        }
        return null;
    }

    private static String encodePendingCard(ChallengeRequest req, boolean incoming, MinecraftServer server) {
        UUID counterparty = incoming ? req.from : req.to;
        String name = incoming ? req.fromName : req.toName;
        if (name == null) {
            name = "";
        }
        name = name.replace('\t', ' ').replace('\n', ' ');
        boolean online = false;
        if (server != null && counterparty != null) {
            online = server.m_6846_().m_11259_(counterparty) != null;
        }
        int mins = (int) Math.max(1L, req.durationMs / 60_000L);
        return String.join("\t",
                counterparty == null ? "" : counterparty.toString(),
                name,
                incoming ? "IN" : "OUT",
                String.valueOf(Math.max(0L, req.expiresAt)),
                online ? "1" : "0",
                String.valueOf(mins),
                req.id == null ? "" : req.id);
    }

    private ChallengeRequest resolveIncomingRequest(ServerPlayer player, String counterpartyPicker) {
        if (player == null) {
            return null;
        }
        UUID self = player.m_20148_();
        if (counterpartyPicker == null || counterpartyPicker.isBlank()) {
            return findIncoming(self);
        }
        ChallengeRequest req = findPendingRequestForViewer(player, counterpartyPicker);
        if (req != null && self.equals(req.to)) {
            return req;
        }
        ChallengeRequest incoming = findIncoming(self);
        if (incoming != null && matchesCounterparty(incoming.from, counterpartyPicker, incoming.fromName)) {
            return incoming;
        }
        return null;
    }

    private ChallengeRequest resolveOutgoingRequest(ServerPlayer player, String counterpartyPicker) {
        if (player == null) {
            return null;
        }
        UUID self = player.m_20148_();
        if (counterpartyPicker == null || counterpartyPicker.isBlank()) {
            return findOutgoing(self);
        }
        ChallengeRequest req = findPendingRequestForViewer(player, counterpartyPicker);
        if (req != null && self.equals(req.from)) {
            return req;
        }
        ChallengeRequest outgoing = findOutgoing(self);
        if (outgoing != null && matchesCounterparty(outgoing.to, counterpartyPicker, outgoing.toName)) {
            return outgoing;
        }
        return null;
    }

    private static boolean matchesCounterparty(UUID counterparty, String pickerArg, String storedName) {
        if (counterparty == null) {
            return false;
        }
        if (pickerArg == null || pickerArg.isBlank()) {
            return true;
        }
        String raw = pickerArg.trim();
        if (raw.regionMatches(true, 0, "uuid:", 0, 5)) {
            try {
                return counterparty.equals(UUID.fromString(raw.substring(5).trim()));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        if (counterparty.toString().equalsIgnoreCase(raw)) {
            return true;
        }
        if (storedName != null && !storedName.isBlank() && storedName.equalsIgnoreCase(raw)) {
            return true;
        }
        RivalPlayerRecord rec = RivalStore.get().get(counterparty.toString());
        if (rec != null && rec.name != null && rec.name.equalsIgnoreCase(raw)) {
            return true;
        }
        return false;
    }

    private static String scoreLine(RivalChallenge ch) {
        return LmChat.tagged("Challenge", "§f" + ch.nameA + " §e" + (int) ch.damageA
                + " §7- §e" + (int) ch.damageB + " §f" + ch.nameB);
    }

    private static String nameOf(RivalChallenge ch, UUID id) {
        if (id == null) {
            return "?";
        }
        if (id.equals(ch.a)) {
            return ch.nameA;
        }
        if (id.equals(ch.b)) {
            return ch.nameB;
        }
        return id.toString();
    }

    private static void broadcast(MinecraftServer server, String text) {
        if (server == null || text == null) {
            return;
        }
        for (ServerPlayer p : server.m_6846_().m_11314_()) {
            DmzRewards.msg(p, LmChat.tagged("Rival", text));
        }
    }

    /**
     * Place the accepting player {@link RivalConstants#CH_ACCEPT_FRONT_BLOCKS} blocks in front
     * of the challenger, facing them. Works across dimensions. A blocked spot steps up or down
     * a few blocks; if nothing is clear, the raw offset is still used.
     */
    private static void teleportInFrontOf(ServerPlayer challenger, ServerPlayer acceptor) {
        if (challenger == null || acceptor == null) {
            return;
        }
        if (!(challenger.m_9236_() instanceof ServerLevel level)) {
            return;
        }
        float yaw = challenger.m_146908_();
        double rad = Math.toRadians(yaw);
        double x = challenger.m_20185_() - Math.sin(rad) * RivalConstants.CH_ACCEPT_FRONT_BLOCKS;
        double z = challenger.m_20189_() + Math.cos(rad) * RivalConstants.CH_ACCEPT_FRONT_BLOCKS;
        double y = standFeetY(level, x, challenger.m_20186_(), z);
        try {
            acceptor.m_8999_(level, x, y, z, yaw + 180.0f, 0.0f);
            acceptor.m_20256_(new Vec3(0.0, 0.0, 0.0));
            acceptor.f_19789_ = 0.0f;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] rival accept teleport failed challenger={} acceptor={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    challenger.m_7755_().getString(),
                    acceptor.m_7755_().getString(),
                    t);
        }
    }

    private static double standFeetY(ServerLevel level, double x, double preferY, double z) {
        int base = (int) Math.floor(preferY);
        if (feetClear(level, x, base, z)) {
            return base;
        }
        for (int d = 1; d <= 6; d++) {
            if (feetClear(level, x, base + d, z)) {
                return base + d;
            }
            if (feetClear(level, x, base - d, z)) {
                return base - d;
            }
        }
        return preferY;
    }

    private static boolean feetClear(ServerLevel level, double x, int feetY, double z) {
        BlockPos feet = BlockPos.m_274561_(x, feetY, z);
        return passable(level.m_8055_(feet))
                && passable(level.m_8055_(feet.m_7494_()))
                && supportive(level.m_8055_(feet.m_7495_()));
    }

    /** Air and non-occluding blocks (plants, water) can be stood in. */
    private static boolean passable(BlockState state) {
        return state != null && (state.m_60795_() || !state.m_60815_());
    }

    /** Full occluding block under the feet. */
    private static boolean supportive(BlockState state) {
        return state != null && !state.m_60795_() && state.m_60815_();
    }

    private static double distance(LivingEntity a, LivingEntity b) {
        if (a == null || b == null || a.m_9236_() != b.m_9236_()) {
            return Double.MAX_VALUE;
        }
        return a.m_20270_(b);
    }

    private static String formatMs(long ms) {
        long s = Math.max(0L, ms / 1000L);
        return s + "s";
    }

    public static final class ChallengeRequest {
        public String id = "";
        public UUID from;
        public UUID to;
        public String fromName = "";
        public String toName = "";
        public long createdAt;
        public long expiresAt;
        public long durationMs = RivalConstants.CH_DURATION_MS;
    }

    private static final class PendingHp {
        UUID attacker;
        float sampleHp;
        boolean ki;
        long resolveAt;
    }
}
