package com.dbzlegacy.adaptivedifficulty.sparring;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.calc.LmOverhaulScaledCombat;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.rival.RivalChallengeManager;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.LmChat;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.dragonminez.common.combat.logic.weapon.WeaponRegistry;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Status;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;

/** Sparring Tp System 3.2.11 facade. */
public final class SparringSystem {
    private static final Map<UUID, SparPlayerRuntime> RUNTIME = new ConcurrentHashMap<>();

    public static final double MAX_SPAR_DISTANCE = 30.0;
    public static final long MELEE_HIT_ACTIVITY_WINDOW_MS = 4500L;
    public static final long KI_HIT_ACTIVITY_WINDOW_MS = 10_000L;
    public static final long SESSION_START_WINDOW_MS = 15_000L;
    public static final long PAIR_RESTART_COOLDOWN_MS = 3000L;
    public static final long SESSION_GRACE_PERIOD_MS = 4000L;
    public static final long DISTANCE_GRACE_PERIOD_MS = 4000L;
    public static final long MOVEMENT_ACTIVITY_WINDOW_MS = 10_000L;
    public static final double MIN_MOVEMENT_DISTANCE = 0.35;
    public static final double MIN_MOTION_SPEED = 0.08;
    public static final double HEAVY_MOTION_SPEED = 0.55;
    public static final long COMBO_TIMEOUT_MS = 2500L;
    public static final long PENDING_HP_RESOLVE_MS = 75L;
    public static final long MENTOR_CHANGE_COOLDOWN_MS = 12L * 60L * 60L * 1000L;
    /** Mentor invite TTL — 1 hour so Pending Accept/Decline isn't rushed. */
    public static final long MENTOR_INVITE_MS = 60L * 60L * 1000L;
    /** Max apprentices per mentor (dojo roster). Each player still has at most one master. */
    public static final int MAX_APPRENTICES = 8;
    public static final long TICK_MS = 250L;
    public static final long MIN_COUNTED_SESSION_MS = 30_000L;
    public static final long STREAK_MIN_SESSION_MS = 300_000L;
    /** Keep match alive while charging / briefly after charge so the ki shot can land. */
    public static final long KI_CHARGE_HOLD_MS = 2800L;
    /** Soft activity hold while clash is reported (does not count as combat for drip TP). */
    public static final long CLASH_HOLD_MS = 2500L;
    /** Real combat window for release-control drip (tighter than AFK hit windows). */
    public static final long RELEASE_COMBAT_WINDOW_MS = 3500L;

    private static long lastPulseAt;

    private SparringSystem() {}

    public static SparPlayerRuntime runtime(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return RUNTIME.computeIfAbsent(uuid, u -> new SparPlayerRuntime());
    }

    public static boolean isSparring(UUID uuid) {
        SparPlayerRuntime rt = uuid == null ? null : RUNTIME.get(uuid);
        return rt != null && rt.active;
    }

    public static void onLogin(ServerPlayer player) {
        if (player != null) {
            runtime(player.m_20148_());
        }
    }

    public static void onLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        SparPlayerRuntime rt = RUNTIME.get(player.m_20148_());
        if (rt != null && rt.active && rt.partner != null) {
            ServerPlayer partner = player.m_20194_() == null
                    ? null
                    : player.m_20194_().m_6846_().m_11259_(rt.partner);
            endSession(player, partner, "logout");
        }
        RUNTIME.remove(player.m_20148_());
    }

    public static void pulse(MinecraftServer server, int tick) {
        if (!DifficultyConfig.get().enableSparringSystem || server == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPulseAt < TICK_MS) {
            return;
        }
        lastPulseAt = now;
        expireInvites(now);
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            if (player == null) {
                continue;
            }
            SparPlayerRuntime rt = runtime(player.m_20148_());
            resolvePending(player, rt, now);
            if (!rt.active || rt.partner == null) {
                continue;
            }
            if (RivalChallengeManager.get().isInChallenge(player.m_20148_())) {
                ServerPlayer partner = server.m_6846_().m_11259_(rt.partner);
                endSession(player, partner, "rival-challenge");
                continue;
            }
            ServerPlayer partner = server.m_6846_().m_11259_(rt.partner);
            if (partner == null) {
                endSession(player, null, "partner-offline");
                continue;
            }
            tickMovement(player, rt, now);
            tickClash(player, partner, rt, now);
            tickReleaseControl(player, partner, rt, now);
            tickPerfectBanner(player, partner, rt, now);
            tickActivity(player, partner, rt, now);
            SparCombat.tickTpMessages(player, rt, now);
        }
        SparStore.get().saveIfNeeded(now);
    }

    public static void onPlayerHurt(ServerPlayer victim, ServerPlayer attacker, DamageSource source) {
        if (!DifficultyConfig.get().enableSparringSystem) {
            return;
        }
        if (victim == null || attacker == null || victim.m_20148_().equals(attacker.m_20148_())) {
            return;
        }
        if (RivalChallengeManager.get().isInChallenge(victim.m_20148_())
                || RivalChallengeManager.get().isInChallenge(attacker.m_20148_())) {
            return;
        }
        boolean ki = DmzRewards.isKiDamage(source);
        // A left-click with a non-DMZ item stays vanilla item damage. CombatEvent
        // does not rewrite it into melee. That hit must not start a spar, keep one
        // alive, or pay TP — only damage the attacker actually deals does.
        if (!ki && isItemPunch(attacker, source)) {
            return;
        }
        // Potions, TNT, thorns, arrows, tridents, fireworks, and other player-attributed
        // junk must not start a spar or pay TP.
        if (isNonCombatDamage(source, attacker)) {
            return;
        }
        String kiKind = ki ? SparCombat.classifyKiType(source) : "";
        recordCombatExchange(attacker, victim, ki, kiKind);
        SparPlayerRuntime vRt = runtime(victim.m_20148_());
        if (vRt.active && vRt.partner != null && vRt.partner.equals(attacker.m_20148_())) {
            vRt.pendingSampleHp = victim.m_21223_() + victim.m_6103_();
            vRt.pendingAttacker = attacker.m_20148_();
            vRt.pendingKi = ki;
            vRt.pendingKiKind = kiKind;
            vRt.pendingUntil = System.currentTimeMillis() + PENDING_HP_RESOLVE_MS;
            maybeFriendlyFist(victim, attacker, vRt);
        }
    }

    public static void onDeath(ServerPlayer victim) {
        if (victim == null) {
            return;
        }
        SparPlayerRuntime rt = RUNTIME.get(victim.m_20148_());
        if (rt != null && rt.active) {
            ServerPlayer partner = victim.m_20194_() == null || rt.partner == null
                    ? null
                    : victim.m_20194_().m_6846_().m_11259_(rt.partner);
            endSession(victim, partner, "death");
        }
    }

    private static void resolvePending(ServerPlayer player, SparPlayerRuntime rt, long now) {
        if (rt.pendingUntil <= 0L || now < rt.pendingUntil || rt.pendingAttacker == null) {
            return;
        }
        // Health + absorption (script getHealthPool).
        float nowPool = player.m_21223_() + player.m_6103_();
        float lost = Math.max(0.0f, rt.pendingSampleHp - nowPool);
        UUID atkId = rt.pendingAttacker;
        boolean ki = rt.pendingKi;
        String kiKind = rt.pendingKiKind;
        rt.pendingUntil = 0L;
        rt.pendingAttacker = null;
        if (!(lost > 0.01f)) {
            // Fully mitigated kiblast still credits a token floor so ki training registers.
            if (ki) {
                lost = SparCombat.KI_FULL_MIT_FLOOR;
            } else {
                return;
            }
        }
        if (!rt.active || rt.partner == null || !rt.partner.equals(atkId)) {
            return;
        }
        MinecraftServer server = player.m_20194_();
        ServerPlayer attacker = server == null ? null : server.m_6846_().m_11259_(atkId);
        if (attacker == null) {
            return;
        }
        SparPlayerRuntime atkRt = runtime(atkId);
        rt.sessionTaken += lost;
        SparCombat.awardDamageTp(attacker, player, atkRt, lost, ki, kiKind);
        if (SparCombat.isBlocking(player)) {
            rt.sessionBlocks++;
            rt.styleBlock += 1.0; // script: +1 per block, not HP lost
            SparCombat.awardCombatTp(player, attacker, rt, SparCombat.BLOCK_TP_BASE, "melee");
        }
    }

    /**
     * Vanilla player-attack while the main hand is a non-DMZ item.
     * Empty hand and registered weapons are real melee. Ki is handled earlier.
     */
    private static boolean isItemPunch(ServerPlayer attacker, DamageSource source) {
        if (attacker == null || !isPlayerMeleeSource(source)) {
            return false;
        }
        try {
            ItemStack main = attacker.m_21205_();
            if (main == null || main.m_41619_()) {
                return false;
            }
            return WeaponRegistry.getAttributes(main) == null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * True when a player-attributed damage source is not genuine sparring combat.
     * Sparring TP comes from DMZ ki and true melee (held items already filtered by
     * {@link #isItemPunch}) only - never splash or lingering potions, TNT, thorns,
     * magic, arrows, tridents, or fireworks.
     */
    private static boolean isNonCombatDamage(DamageSource source, ServerPlayer attacker) {
        try {
            if (source == null) {
                return true;
            }
            // DMZ ki is always real combat.
            if (DmzRewards.isKiDamage(source)) {
                return false;
            }
            String type = "";
            try {
                type = String.valueOf(source.m_19385_()).toLowerCase(java.util.Locale.ROOT);
            } catch (Throwable ignored) {
            }
            // Thorns pays the defender for the attacker's own hit - never score it.
            if (type.contains("thorns")) {
                return true;
            }
            // 1.20.1 message id is dragonBreath, not dragon_breath.
            if (type.contains("magic") || type.contains("explosion") || type.contains("potion")
                    || type.contains("dragon_breath") || type.contains("dragonbreath")) {
                return true;
            }
            net.minecraft.world.entity.Entity direct = source.m_7640_();
            // True melee: attacker is the direct entity.
            if (direct == attacker) {
                return false;
            }
            // Strict melee and ki only - arrows, tridents, fireworks, and any other
            // indirect source never start a spar or pay TP.
            return true;
        } catch (Throwable ignored) {
            return false; // fail open - never break combat on an unexpected source
        }
    }

    private static boolean isPlayerMeleeSource(DamageSource source) {
        try {
            return source != null && "player".equals(source.m_19385_());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void maybeFriendlyFist(ServerPlayer victim, ServerPlayer attacker, SparPlayerRuntime vRt) {
        if (victim == null || attacker == null || vRt == null) {
            return;
        }
        try {
            if (vRt.ffKdHealed) {
                return;
            }
            StatsData data = DmzProgression.stats(victim);
            Status status = data == null ? null : data.getStatus();
            if (status == null) {
                return;
            }
            // Either fighter having Friendly Fist can save the knockdown victim.
            boolean ffOn = status.isFriendlyFistEnabled()
                    || isFriendlyFistOn(attacker);
            if (!ffOn) {
                return;
            }
            boolean kd = status.isKnockedDown();
            float hp = victim.m_21223_();
            boolean lethal = hp <= 1.5f;
            if (!kd && !lethal) {
                return;
            }
            float max = victim.m_21233_();
            victim.m_21153_(max);
            try {
                status.setKnockedDown(false);
            } catch (Throwable ignored) {
            }
            vRt.ffKdHealed = true;
            long now = System.currentTimeMillis();
            stampHitActivity(vRt, attacker.m_7755_().getString(), now, "ki");
            SparPlayerRuntime aRt = runtime(attacker.m_20148_());
            stampHitActivity(aRt, victim.m_7755_().getString(), now, "ki");
            refreshMovementActivity(victim, vRt, now);
            refreshMovementActivity(attacker, aRt, now);
            vRt.graceUntil = 0L;
            aRt.graceUntil = 0L;
            if (now >= vRt.messageNext) {
                vRt.messageNext = now + 4000L;
                DmzRewards.msg(attacker, LmChat.ok("Spar",
                        "Friendly Fist " + UltraStyle.LABEL + "knockdown — healed " + UltraStyle.VALUE
                                + victim.m_7755_().getString() + UltraStyle.LABEL + "."));
                DmzRewards.msg(victim, LmChat.ok("Spar",
                        "Friendly Fist " + UltraStyle.LABEL + "heal from " + UltraStyle.VALUE
                                + attacker.m_7755_().getString() + UltraStyle.LABEL + "."));
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isFriendlyFistOn(ServerPlayer player) {
        try {
            StatsData data = DmzProgression.stats(player);
            Status status = data == null ? null : data.getStatus();
            return status != null && status.isFriendlyFistEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void recordCombatExchange(
            ServerPlayer attacker,
            ServerPlayer target,
            boolean ki,
            String kiKind
    ) {
        SparPlayerRuntime aRt = runtime(attacker.m_20148_());
        SparPlayerRuntime tRt = runtime(target.m_20148_());
        long now = System.currentTimeMillis();
        String aName = attacker.m_7755_().getString();
        String tName = target.m_7755_().getString();

        boolean stampOut = !aRt.active || (aRt.partner != null && aRt.partner.equals(target.m_20148_()));
        if (stampOut) {
            aRt.lastOutPartner = tName;
            aRt.lastOutAt = now;
            aRt.lastOutKind = ki ? "ki" : "melee";
            // Real damage exchange — used for release-control drip (not charge/clash holds).
            aRt.lastCombatOutPartner = tName;
            aRt.lastCombatOutAt = now;
            aRt.lastCombatOutKind = ki ? "ki" : "melee";
            tRt.lastInPartner = aName;
            tRt.lastInAt = now;
            if (ki) {
                aRt.lastKiOutAt = now;
                aRt.lastLaserOutAt = now;
            }
        }
        if (aRt.active || tRt.active) {
            return;
        }
        if (!tRt.lastOutPartner.equalsIgnoreCase(aName)) {
            return;
        }
        if (now - tRt.lastOutAt > SESSION_START_WINDOW_MS) {
            return;
        }
        if (attacker.m_9236_() != target.m_9236_()) {
            return;
        }
        if (attacker.m_20270_(target) > MAX_SPAR_DISTANCE) {
            return;
        }
        if (now < aRt.restartCooldownUntil || now < tRt.restartCooldownUntil) {
            return;
        }
        startSession(attacker, target);
    }

    private static void startSession(ServerPlayer a, ServerPlayer b) {
        SparPlayerRuntime aRt = runtime(a.m_20148_());
        SparPlayerRuntime bRt = runtime(b.m_20148_());
        long now = System.currentTimeMillis();
        aRt.resetSession();
        bRt.resetSession();
        aRt.active = true;
        bRt.active = true;
        aRt.partner = b.m_20148_();
        bRt.partner = a.m_20148_();
        aRt.startAt = now;
        bRt.startAt = now;
        // Seed movement window only — hits/blocks must not refresh the AFK gate.
        refreshMovementActivity(a, aRt, now);
        refreshMovementActivity(b, bRt, now);
        String tipA = UltraStyle.DIM + "Stay active: trade damage, move, and keep the fight going.";
        String tipB = tipA;
        if (isSparringWithOwnMentor(a, b)) {
            tipA = UltraStyle.AQUA + "Mentor spar " + UltraStyle.DIM + "· +" + Math.round(SparCombat.MENTOR_SPAR_BONUS_PCT * 100) + "% TP";
        } else if (isSparringWithOwnMentor(b, a)) {
            tipB = UltraStyle.AQUA + "Mentor spar " + UltraStyle.DIM + "· +" + Math.round(SparCombat.MENTOR_SPAR_BONUS_PCT * 100) + "% TP";
        } else if (isSparringWithDojoPeer(a, b)) {
            tipA = UltraStyle.AQUA + "Dojo peers " + UltraStyle.DIM + "· +" + Math.round(SparCombat.DOJO_PEER_SPAR_BONUS_PCT * 100) + "% TP";
            tipB = tipA;
        }
        DmzRewards.msg(a, LmChat.card(
                "Sparring",
                "/spar",
                null,
                UltraStyle.GREEN + "Session started with " + UltraStyle.VALUE + b.m_7755_().getString(),
                tipA));
        DmzRewards.msg(b, LmChat.card(
                "Sparring",
                "/spar",
                null,
                UltraStyle.GREEN + "Session started with " + UltraStyle.VALUE + a.m_7755_().getString(),
                tipB));
        SystemTelemetry.log("sparring", "spar_start", a, b, null);
    }

    public static void endSession(ServerPlayer player, ServerPlayer partner, String reason) {
        if (player == null) {
            return;
        }
        SparPlayerRuntime aRt = runtime(player.m_20148_());
        if (!aRt.active) {
            return;
        }
        SparPlayerRuntime bRt = partner == null ? null : runtime(partner.m_20148_());
        long now = System.currentTimeMillis();
        long duration = Math.max(0L, now - aRt.startAt);
        SparCombat.flushTpMessage(player, aRt);
        if (partner != null && bRt != null) {
            SparCombat.flushTpMessage(partner, bRt);
        }
        report(player, aRt, partner, duration, reason);
        if (partner != null && bRt != null && bRt.active) {
            report(partner, bRt, player, duration, reason);
            updateLeaderboard(partner, bRt, duration);
            updateStreak(partner, bRt, duration);
            recordRecentSession(partner, bRt, player, duration, reason);
            DojoRankings.onInterDojoSession(player, aRt, partner, bRt, duration);
            bRt.resetSession();
            bRt.restartCooldownUntil = now + PAIR_RESTART_COOLDOWN_MS;
        }
        updateLeaderboard(player, aRt, duration);
        updateStreak(player, aRt, duration);
        recordRecentSession(player, aRt, partner, duration, reason);
        SystemTelemetry.log("sparring", "spar_end", player, partner,
                SystemTelemetry.fields("reason", reason == null ? "" : reason,
                        "tp", (int) aRt.sessionTp, "ms", duration));
        aRt.resetSession();
        aRt.restartCooldownUntil = now + PAIR_RESTART_COOLDOWN_MS;
        SparStore.get().markDirty();
    }

    private static void report(
            ServerPlayer player,
            SparPlayerRuntime rt,
            ServerPlayer partner,
            long durationMs,
            String reason
    ) {
        String who = partner == null ? "?" : partner.m_7755_().getString();
        DmzRewards.msg(player, LmChat.info("Spar", "Session ended with " + UltraStyle.VALUE + who
                + " " + UltraStyle.DIM + "(" + reason + ", " + (durationMs / 1000L) + "s)"));
        DmzRewards.msg(player, LmChat.note("Spar", UltraStyle.LABEL + "TP " + UltraStyle.GREEN + "+" + DmzRewards.formatWhole(rt.sessionTp)
                + " " + UltraStyle.DIM + "| melee " + UltraStyle.VALUE + (int) rt.sessionMelee
                + " " + UltraStyle.DIM + "| ki " + UltraStyle.VALUE + (int) rt.sessionKi
                + " " + UltraStyle.DIM + "| combo " + UltraStyle.VALUE + rt.sessionMaxCombo));
    }

    private static void updateLeaderboard(ServerPlayer player, SparPlayerRuntime rt, long durationMs) {
        if (durationMs < MIN_COUNTED_SESSION_MS) {
            return;
        }
        SparStore.LeaderboardEntry e = SparStore.get().leaderboard.computeIfAbsent(
                player.m_20148_().toString(), k -> new SparStore.LeaderboardEntry());
        e.name = player.m_7755_().getString();
        e.totalTp += rt.sessionTp;
        e.longestMs = Math.max(e.longestMs, durationMs);
        e.bestPayout = Math.max(e.bestPayout, rt.sessionTp);
        e.totalTimeMs += durationMs;
        e.sessions++;
        if (rt.sessionPerfect) {
            e.perfectSessions++;
        }
        e.highestCombo = Math.max(e.highestCombo, rt.sessionMaxCombo);
    }

    /** Persist a finished spar for the Stats board (same 30s gate as lifetime counts). */
    private static void recordRecentSession(
            ServerPlayer player,
            SparPlayerRuntime rt,
            ServerPlayer partner,
            long durationMs,
            String reason
    ) {
        if (player == null || rt == null || durationMs < MIN_COUNTED_SESSION_MS) {
            return;
        }
        SparStore.RecentSession rec = new SparStore.RecentSession();
        rec.partnerName = partner == null ? "?" : partner.m_7755_().getString();
        rec.partnerUuid = partner == null ? "" : partner.m_20148_().toString();
        rec.tp = rt.sessionTp;
        rec.durationMs = durationMs;
        rec.maxCombo = rt.sessionMaxCombo;
        rec.perfect = rt.sessionPerfect;
        rec.endedAt = System.currentTimeMillis();
        rec.reason = reason == null ? "" : reason;
        rec.melee = rt.sessionMelee;
        rec.ki = rt.sessionKi;
        SparStore.get().pushRecent(player.m_20148_().toString(), rec);
    }

    private static void updateStreak(ServerPlayer player, SparPlayerRuntime rt, long durationMs) {
        if (durationMs < STREAK_MIN_SESSION_MS) {
            return;
        }
        SparStore.MentorBond bond = SparStore.get().bond(player.m_20148_());
        long today = System.currentTimeMillis() / 86_400_000L;
        if (bond.streakLastDay != today) {
            if (bond.streakLastDay == today - 1) {
                bond.streakCurrent = Math.max(0, bond.streakCurrent) + 1;
            } else {
                bond.streakCurrent = 1;
            }
            bond.streakLastDay = today;
            bond.streakBest = Math.max(bond.streakBest, bond.streakCurrent);
            DmzRewards.msg(player, LmChat.ok("Spar", "Daily training secured! " + UltraStyle.YELLOW + "Streak "
                    + bond.streakCurrent + " day" + (bond.streakCurrent == 1 ? "" : "s")));
        }
    }

    /** Seed / hold the movement AFK window (script refreshMovementActivity). */
    private static void refreshMovementActivity(ServerPlayer player, SparPlayerRuntime rt, long now) {
        if (player == null || rt == null) {
            return;
        }
        rt.moveX = player.m_20185_();
        rt.moveY = player.m_20186_();
        rt.moveZ = player.m_20189_();
        rt.moveValidUntil = now + MOVEMENT_ACTIVITY_WINDOW_MS;
    }

    /**
     * Script {@code updateMovement}: only real displacement / velocity refreshes the AFK gate.
     * Hits and blocks never call this — standing still and punching (box farm) expires the window.
     */
    private static void tickMovement(ServerPlayer player, SparPlayerRuntime rt, long now) {
        double x = player.m_20185_();
        double y = player.m_20186_();
        double z = player.m_20189_();
        double dx = x - rt.moveX;
        double dy = y - rt.moveY;
        double dz = z - rt.moveZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double speed = readMotionSpeed(player);
        rt.moveX = x;
        rt.moveY = y;
        rt.moveZ = z;
        if (dist >= MIN_MOVEMENT_DISTANCE || speed >= MIN_MOTION_SPEED) {
            rt.moveValidUntil = now + MOVEMENT_ACTIVITY_WINDOW_MS;
            rt.styleMove += 1.0;
        }
        if (speed >= HEAVY_MOTION_SPEED) {
            rt.heavyMotionUntil = now + 2500L;
        }
    }

    private static double readMotionSpeed(ServerPlayer player) {
        try {
            var motion = player.m_20184_();
            if (motion == null) {
                return 0.0;
            }
            double mx = motion.f_82479_;
            double my = motion.f_82480_;
            double mz = motion.f_82481_;
            return Math.sqrt(mx * mx + my * my + mz * mz);
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static boolean hasRecentMovement(SparPlayerRuntime rt, long now) {
        return rt != null && rt.moveValidUntil > 0L && now <= rt.moveValidUntil;
    }

    private static void tickClash(ServerPlayer player, ServerPlayer partner, SparPlayerRuntime rt, long now) {
        boolean selfClash = DmzRewards.isClashing(player.m_20148_());
        boolean partnerClash = DmzRewards.isClashing(partner.m_20148_());
        boolean bothClashing = selfClash && partnerClash;
        if (selfClash || partnerClash) {
            // Soft linger keeps the match held even if only one side reports clash.
            // Does NOT fake combat clocks — idle drip TP must stop when fighting stops.
            rt.clashUntil = now + 4000L;
        }
        // Script: TP drip only while BOTH fighters are actively clashing.
        if (bothClashing && now >= rt.clashNext) {
            rt.clashNext = now + 500L;
            rt.sessionClashMs += 500L;
            rt.styleBeam += 1.0;
            SparCombat.awardCombatTp(player, partner, rt, SparCombat.BEAM_CLASH_TP_PER_TICK, "clash");
        }
        // Charging / clash holds keep the match alive without fake hit stamps for drip TP.
        holdSparForKiOrClash(player, partner, rt, now);
    }

    /**
     * Standing still mid-charge / clash must not trip AFK gates — but must not fake
     * combat activity either (that was letting idle fighters keep earning release TP).
     * @return true when the pair is currently held
     */
    private static boolean holdSparForKiOrClash(
            ServerPlayer player, ServerPlayer partner, SparPlayerRuntime rt, long now
    ) {
        if (player == null || partner == null || rt == null) {
            return false;
        }
        SparPlayerRuntime pRt = runtime(partner.m_20148_());
        boolean clashing = now <= rt.clashUntil
                || DmzRewards.isClashing(player.m_20148_())
                || DmzRewards.isClashing(partner.m_20148_());
        boolean selfCharging = isChargingKi(player);
        boolean partnerCharging = isChargingKi(partner);
        boolean charging = selfCharging || partnerCharging || now < rt.chargingUntil || now < pRt.chargingUntil;
        if (!clashing && !charging) {
            return isActivityHeld(rt, now) || isActivityHeld(pRt, now);
        }
        if (clashing) {
            markHold(rt, now + CLASH_HOLD_MS);
            markHold(pRt, now + CLASH_HOLD_MS);
        }
        if (selfCharging || partnerCharging) {
            long until = now + KI_CHARGE_HOLD_MS;
            if (selfCharging) {
                markCharging(rt, until);
                markHold(pRt, until);
            }
            if (partnerCharging) {
                markCharging(pRt, until);
                markHold(rt, until);
            }
        }
        // Movement refresh only — never stamp lastOutAt / lastCombatOutAt here.
        refreshMovementActivity(player, rt, now);
        refreshMovementActivity(partner, pRt, now);
        return true;
    }

    /**
     * KiChargeEvent path: Status.isChargingKi can flicker between charge ticks.
     * Refresh a linger so AFK does not end the spar before the shot lands.
     */
    public static void markKiCharging(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableSparringSystem) {
            return;
        }
        SparPlayerRuntime rt = RUNTIME.get(player.m_20148_());
        if (rt == null || !rt.active || rt.partner == null) {
            return;
        }
        MinecraftServer server = player.m_20194_();
        ServerPlayer partner = server == null ? null : server.m_6846_().m_11259_(rt.partner);
        if (partner == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long until = now + KI_CHARGE_HOLD_MS;
        SparPlayerRuntime pRt = runtime(partner.m_20148_());
        markCharging(rt, until);
        markHold(pRt, until);
        refreshMovementActivity(player, rt, now);
        refreshMovementActivity(partner, pRt, now);
    }

    private static void markHold(SparPlayerRuntime rt, long until) {
        if (rt != null && until > rt.holdUntil) {
            rt.holdUntil = until;
        }
    }

    private static void markCharging(SparPlayerRuntime rt, long until) {
        if (rt == null) {
            return;
        }
        if (until > rt.chargingUntil) {
            rt.chargingUntil = until;
        }
        markHold(rt, until);
    }

    private static boolean isActivityHeld(SparPlayerRuntime rt, long now) {
        return rt != null && (now < rt.holdUntil || now < rt.chargingUntil);
    }

    private static void stampHitActivity(SparPlayerRuntime rt, String partnerName, long now, String kind) {
        if (rt == null) {
            return;
        }
        rt.lastOutAt = now;
        rt.lastOutKind = kind == null ? "melee" : kind;
        if (partnerName != null) {
            rt.lastOutPartner = partnerName;
        }
    }

    private static boolean isChargingKi(ServerPlayer player) {
        try {
            StatsData data = DmzProgression.stats(player);
            Status status = data == null ? null : data.getStatus();
            return status != null && (status.isChargingKi() || status.isActionCharging());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasRecentCombatOut(SparPlayerRuntime rt, String partnerName, long now, long windowMs) {
        if (rt == null || partnerName == null || partnerName.isBlank()) {
            return false;
        }
        if (rt.lastCombatOutAt <= 0L) {
            return false;
        }
        if (!rt.lastCombatOutPartner.isBlank() && !rt.lastCombatOutPartner.equalsIgnoreCase(partnerName)) {
            return false;
        }
        return now - rt.lastCombatOutAt <= windowMs;
    }

    private static void tickReleaseControl(
            ServerPlayer player,
            ServerPlayer partner,
            SparPlayerRuntime rt,
            long now
    ) {
        if (rt == null || !rt.active || partner == null) {
            return;
        }
        // No idle drip during recover grace (stopped fighting - match winding down).
        if (rt.graceUntil > 0L && now < rt.graceUntil) {
            return;
        }
        SparCombat.TrainingValues values = SparCombat.liveValues(player);
        if (values == null || values.release < SparCombat.HIGH_RELEASE_THRESHOLD) {
            return;
        }
        boolean bothClashing = DmzRewards.isClashing(player.m_20148_())
                && DmzRewards.isClashing(partner.m_20148_());
        boolean recentCombat = hasRecentCombatOut(
                rt, partner.m_7755_().getString(), now, RELEASE_COMBAT_WINDOW_MS);
        // Charge/clash soft holds must not grant release TP — only live clash or real hits.
        if (!recentCombat && !bothClashing) {
            return;
        }
        if (!bothClashing && !hasRecentMovement(rt, now)) {
            return;
        }
        if (now < rt.releaseCtrlNext) {
            return;
        }
        rt.releaseCtrlNext = now + 1000L;
        SparCombat.awardCombatTp(player, partner, rt, SparCombat.RELEASE_CONTROL_TP_PER_SEC, "release");
    }

    private static void tickPerfectBanner(
            ServerPlayer player,
            ServerPlayer partner,
            SparPlayerRuntime rt,
            long now
    ) {
        if (rt == null || !rt.active || partner == null) {
            return;
        }
        SparCombat.TrainingValues a = SparCombat.liveValues(player);
        SparCombat.TrainingValues b = SparCombat.liveValues(partner);
        if (!SparCombat.isPerfect(a, b)) {
            return;
        }
        rt.sessionPerfect = true;
        if (now < rt.messageNext) {
            return;
        }
        rt.messageNext = now + SparCombat.PERFECT_ACTIONBAR_MS;
        DmzRewards.msg(player, LmChat.tagged("Spar", UltraStyle.HEADER + "Perfect Training active"));
    }

    /**
     * Script processSession activity gates:
     * both fighters must exchange damage AND keep moving (unless clash/ki-charge hold).
     * Prevents AFK box-farming: standing still punching still expires the move window.
     * Ki charge uses holdUntil/chargingUntil — not fake hits — so the match stays open
     * through charge-shot without idle release TP.
     */
    private static void tickActivity(ServerPlayer player, ServerPlayer partner, SparPlayerRuntime rt, long now) {
        SparPlayerRuntime pRt = runtime(partner.m_20148_());
        boolean held = holdSparForKiOrClash(player, partner, rt, now)
                || isActivityHeld(rt, now)
                || isActivityHeld(pRt, now);

        String failure = "";
        if (player.m_9236_() != partner.m_9236_() || player.m_20270_(partner) > MAX_SPAR_DISTANCE) {
            failure = "fighters moved too far apart";
        } else if (!held) {
            boolean hitA = hasRecentOutgoingHit(rt, partner.m_7755_().getString(), now);
            boolean hitB = hasRecentOutgoingHit(pRt, player.m_7755_().getString(), now);
            if (!hitA || !hitB) {
                failure = "both fighters must resume exchanging damage";
            } else if (!hasRecentMovement(rt, now) || !hasRecentMovement(pRt, now)) {
                failure = "both fighters must resume moving";
            }
        }

        if (!failure.isEmpty()) {
            handleRecoverableFailure(player, partner, rt, pRt, failure, now);
            return;
        }

        rt.graceUntil = 0L;
        rt.graceWarned = false;
        rt.graceReason = "";
        pRt.graceUntil = 0L;
        pRt.graceWarned = false;
        pRt.graceReason = "";

        if (rt.combo > 0 && now > rt.comboUntil) {
            rt.combo = 0;
        }
        if (rt.momentumTier > 0 && now > rt.momentumUntil) {
            rt.momentumTier = 0;
        }
    }

    private static void handleRecoverableFailure(
            ServerPlayer player,
            ServerPlayer partner,
            SparPlayerRuntime rt,
            SparPlayerRuntime pRt,
            String reason,
            long now
    ) {
        long graceMs = reason != null && reason.contains("far")
                ? DISTANCE_GRACE_PERIOD_MS
                : SESSION_GRACE_PERIOD_MS;
        if (rt.graceUntil <= 0L || !reason.equals(rt.graceReason)) {
            rt.graceUntil = now + graceMs;
            rt.graceReason = reason;
            rt.graceWarned = false;
            if (pRt != null) {
                pRt.graceUntil = rt.graceUntil;
                pRt.graceReason = reason;
                pRt.graceWarned = false;
            }
        }
        if (!rt.graceWarned) {
            rt.graceWarned = true;
            if (pRt != null) {
                pRt.graceWarned = true;
            }
            long left = Math.max(1L, (rt.graceUntil - now + 999L) / 1000L);
            String msg = LmChat.note("Spar", UltraStyle.YELLOW + "Recover within " + left + "s " + UltraStyle.DIM + "— " + reason);
            DmzRewards.msg(player, msg);
            if (partner != null) {
                DmzRewards.msg(partner, msg);
            }
        }
        if (now >= rt.graceUntil) {
            endSession(player, partner, reason);
        }
    }

    private static boolean hasRecentOutgoingHit(SparPlayerRuntime rt, String partnerName, long now) {
        if (rt == null || partnerName == null || partnerName.isBlank()) {
            return false;
        }
        if (!rt.lastOutPartner.equalsIgnoreCase(partnerName)) {
            return false;
        }
        long window = "ki".equalsIgnoreCase(rt.lastOutKind) || "beam".equalsIgnoreCase(rt.lastOutKind)
                ? KI_HIT_ACTIVITY_WINDOW_MS
                : MELEE_HIT_ACTIVITY_WINDOW_MS;
        return now - rt.lastOutAt <= window;
    }

    public static void registerCombatHit(ServerPlayer player, SparPlayerRuntime rt) {
        long now = System.currentTimeMillis();
        if (now <= rt.comboUntil) {
            rt.combo++;
        } else {
            rt.combo = 1;
        }
        rt.comboUntil = now + COMBO_TIMEOUT_MS;
        rt.sessionMaxCombo = Math.max(rt.sessionMaxCombo, rt.combo);
        SparCombat.updateMomentum(player, rt);
    }

    /** @deprecated prefer {@link #registerCombatHit(ServerPlayer, SparPlayerRuntime)} */
    public static void registerCombatHit(SparPlayerRuntime rt) {
        registerCombatHit(null, rt);
    }

    public static void breakCombo(ServerPlayer player, SparPlayerRuntime rt, String reason) {
        if (rt == null) {
            return;
        }
        if (rt.combo > 0 || rt.momentumTier > 0) {
            long now = System.currentTimeMillis();
            if (now >= rt.messageNext) {
                rt.messageNext = now + 1500L;
                DmzRewards.msg(player, LmChat.fail("Spar", "Combo broken (" + reason + ")."));
            }
        }
        rt.combo = 0;
        rt.comboUntil = 0L;
        rt.momentumTier = 0;
        rt.momentumUntil = 0L;
    }

    public static boolean isSparringWithOwnMentor(ServerPlayer player, ServerPlayer partner) {
        if (player == null || partner == null) {
            return false;
        }
        SparStore.MentorBond bond = SparStore.get().bond(player.m_20148_());
        return bond.mentorUuid != null
                && bond.mentorUuid.equals(partner.m_20148_().toString());
    }

    /**
     * True when both players are apprentices of the same mentor (dojo peers).
     * Does not include mentor↔apprentice spars — use {@link #isSparringWithOwnMentor}.
     */
    public static boolean isSparringWithDojoPeer(ServerPlayer player, ServerPlayer partner) {
        if (player == null || partner == null) {
            return false;
        }
        SparStore.MentorBond a = SparStore.get().bond(player.m_20148_());
        SparStore.MentorBond b = SparStore.get().bond(partner.m_20148_());
        if (a == null || b == null) {
            return false;
        }
        String mentorA = a.mentorUuid;
        String mentorB = b.mentorUuid;
        if (mentorA == null || mentorA.isBlank() || mentorB == null || mentorB.isBlank()) {
            return false;
        }
        if (!mentorA.equalsIgnoreCase(mentorB)) {
            return false;
        }
        // Neither fighter is the shared mentor (that's the mentor-spar path).
        String mentorKey = mentorA;
        return !mentorKey.equalsIgnoreCase(player.m_20148_().toString())
                && !mentorKey.equalsIgnoreCase(partner.m_20148_().toString());
    }

    public static void shareTpWithMentor(ServerPlayer apprentice, int amount) {
        if (apprentice == null || amount <= 0) {
            return;
        }
        SparStore.MentorBond bond = SparStore.get().bond(apprentice.m_20148_());
        if (bond.mentorUuid == null || bond.mentorUuid.isBlank()) {
            return;
        }
        MinecraftServer server = apprentice.m_20194_();
        if (server == null) {
            return;
        }
        try {
            UUID mentorId = UUID.fromString(bond.mentorUuid);
            ServerPlayer mentor = server.m_6846_().m_11259_(mentorId);
            if (mentor == null) {
                return;
            }
            // Dilute share across the dojo so total mentor take stays ≤ one-apprentice rate.
            SparStore.MentorBond mentorBond = SparStore.get().bond(mentorId);
            int roster = mentorBond == null ? 1 : Math.max(1, mentorBond.apprenticeCount());
            float pct = SparCombat.MENTOR_SHARE_PCT / (float) roster;
            int share = Math.round(amount * pct);
            if (share < 1) {
                if (roster == 1) {
                    share = 1;
                } else {
                    return;
                }
            }
            boolean staff = StaffAccess.isStaff(mentor);
            String reason = staff
                    ? ("Mentor share from " + apprentice.m_7755_().getString()
                    + (roster > 1 ? " " + UltraStyle.DIM + "(dojo " + roster + ")" : ""))
                    : "apprentice";
            DmzRewards.awardTp(mentor, share, reason,
                    SparStore.get().mentorTpMessagesOn(mentor.m_20148_()), "Mentor");
        } catch (Throwable ignored) {
        }
    }

    /* ========================= Commands / mentor ========================= */

    public static List<String> statsLines(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        List<SparStore.RecentSession> recent = SparStore.get().recentFor(player.m_20148_().toString());
        if (recent.isEmpty()) {
            lines.add(UltraStyle.LABEL + "No finished spars yet");
            lines.add(UltraStyle.DIM + "  - " + UltraStyle.LABEL + "Fight someone, then open Stats again.");
        } else {
            // One multi-line report tile per spar (GUI groups UltraStyle.DIM + "  -" under the title).
            int i = 1;
            for (SparStore.RecentSession r : recent) {
                appendRecentReport(lines, i, r);
                i++;
            }
        }
        lines.addAll(statsOverviewLines(player));
        return lines;
    }

    /**
     * Compact lifetime / bond summary for Ultra stats (no Combat block, no full report dump).
     * Fight details open via {@link #recentReportLines}.
     */
    public static List<String> statsOverviewLines(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        if (player == null) {
            return lines;
        }
        SparStore.MentorBond bond = SparStore.get().bond(player.m_20148_());
        SparStore.LeaderboardEntry lb = SparStore.get().leaderboard.get(player.m_20148_().toString());
        lines.add(UltraStyle.AQUA + "Training bonds");
        lines.add(UltraStyle.DIM + "  - " + UltraStyle.LABEL + "Mentor " + UltraStyle.VALUE
                + (bond.mentorName == null || bond.mentorName.isBlank() ? "none" : bond.mentorName));
        int apps = bond.apprenticeCount();
        if (apps <= 0) {
            lines.add(UltraStyle.DIM + "  - " + UltraStyle.LABEL + "Apprentices " + UltraStyle.VALUE + "none");
        } else {
            lines.add(UltraStyle.DIM + "  - " + UltraStyle.LABEL + "Apprentices " + UltraStyle.VALUE + apps + UltraStyle.DIM + "/" + UltraStyle.VALUE + MAX_APPRENTICES
                    + " " + UltraStyle.DIM + "- " + UltraStyle.VALUE + bond.apprenticeNamesSummary());
        }
        lines.add(UltraStyle.DIM + "  - " + UltraStyle.LABEL + "Streak " + UltraStyle.VALUE + bond.streakCurrent + " " + UltraStyle.DIM + "(best " + bond.streakBest + ")");
        if (lb != null) {
            lines.add(UltraStyle.YELLOW + "Lifetime");
            lines.add(UltraStyle.DIM + "  - " + UltraStyle.LABEL + "Total TP " + UltraStyle.GREEN + DmzRewards.formatWhole(lb.totalTp));
            lines.add(UltraStyle.DIM + "  - " + UltraStyle.LABEL + "Sessions " + UltraStyle.VALUE + lb.sessions
                    + " " + UltraStyle.DIM + "| best combo " + UltraStyle.VALUE + lb.highestCombo);
            lines.add(UltraStyle.DIM + "  - " + UltraStyle.LABEL + "Best payout " + UltraStyle.GREEN + DmzRewards.formatWhole(lb.bestPayout)
                    + " " + UltraStyle.DIM + "| perfects " + UltraStyle.VALUE + lb.perfectSessions);
        } else {
            lines.add(UltraStyle.LABEL + "No lifetime spar stats yet.");
        }
        return lines;
    }

    /**
     * Button labels for the last finished spars ({@code index\tlabel}, 1-based index).
     * Cap matches {@link SparStore#RECENT_SESSION_LIMIT}.
     */
    public static List<String> recentFightCards(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        List<SparStore.RecentSession> recent = SparStore.get().recentFor(player.m_20148_().toString());
        int i = 1;
        for (SparStore.RecentSession r : recent) {
            if (r == null || i > SparStore.RECENT_SESSION_LIMIT) {
                continue;
            }
            String who = r.partnerName == null || r.partnerName.isBlank() ? "unknown" : r.partnerName;
            who = who.replace('\t', ' ').replace('\n', ' ');
            String when = r.endedAt > 0L ? formatEndedAgo(r.endedAt) : "";
            String label = "vs " + who + (when.isBlank() ? "" : " (" + when + ")");
            out.add(i + "\t" + label);
            i++;
        }
        return out;
    }

    /** Full report lines for one recent spar (1-based index), or empty if missing. */
    public static List<String> recentReportLines(ServerPlayer player, int index) {
        List<String> lines = new ArrayList<>();
        if (player == null || index < 1) {
            return lines;
        }
        List<SparStore.RecentSession> recent = SparStore.get().recentFor(player.m_20148_().toString());
        if (index > recent.size()) {
            return lines;
        }
        SparStore.RecentSession r = recent.get(index - 1);
        appendRecentReport(lines, index, r);
        return lines;
    }

    /** One individual spar report block (title + dashed detail lines). */
    private static void appendRecentReport(List<String> lines, int index, SparStore.RecentSession r) {
        if (lines == null || r == null) {
            return;
        }
        String who = r.partnerName == null || r.partnerName.isBlank() ? "unknown" : r.partnerName;
        String perfect = r.perfect ? " " + UltraStyle.GOLD + "Perfect" : "";
        lines.add(UltraStyle.GOLD + "Spar Report #" + index + " " + UltraStyle.DIM + "vs " + UltraStyle.VALUE + who);
        lines.add(UltraStyle.DIM + "  - " + UltraStyle.LABEL + "TP " + UltraStyle.GREEN + "+" + DmzRewards.formatWhole(r.tp));
        lines.add(UltraStyle.DIM + "  - " + UltraStyle.LABEL + "Time " + UltraStyle.VALUE + formatDuration(r.durationMs));
        lines.add(UltraStyle.DIM + "  - " + UltraStyle.LABEL + "Melee " + UltraStyle.VALUE + DmzRewards.formatWhole(r.melee)
                + " " + UltraStyle.DIM + "| " + UltraStyle.LABEL + "Ki " + UltraStyle.VALUE + DmzRewards.formatWhole(r.ki));
        lines.add(UltraStyle.DIM + "  - " + UltraStyle.LABEL + "Combo " + UltraStyle.VALUE + r.maxCombo + perfect);
        if (r.reason != null && !r.reason.isBlank()) {
            lines.add(UltraStyle.DIM + "  - " + UltraStyle.LABEL + "Ended " + UltraStyle.VALUE + r.reason);
        }
        if (r.endedAt > 0L) {
            lines.add(UltraStyle.DIM + "  - " + UltraStyle.LABEL + "When " + UltraStyle.VALUE + formatEndedAgo(r.endedAt));
        }
    }

    private static String formatEndedAgo(long endedAtMs) {
        long ago = Math.max(0L, System.currentTimeMillis() - endedAtMs);
        long sec = ago / 1000L;
        if (sec < 60L) {
            return sec + "s ago";
        }
        long min = sec / 60L;
        if (min < 60L) {
            return min + "m ago";
        }
        long hr = min / 60L;
        if (hr < 48L) {
            return hr + "h ago";
        }
        return (hr / 24L) + "d ago";
    }

    private static String formatDuration(long ms) {
        long sec = Math.max(0L, ms / 1000L);
        long min = sec / 60L;
        long rem = sec % 60L;
        if (min <= 0) {
            return rem + "s";
        }
        return min + "m" + rem + "s";
    }

    public static List<String> topLines(String category, int limit) {
        List<String> lines = new ArrayList<>();
        String cat = category == null || category.isBlank() ? "tp" : category.trim().toLowerCase();
        String label = switch (cat) {
            case "sessions", "session" -> "Sessions";
            case "perfect", "perfects" -> "Perfect spars";
            case "combo" -> "Combo";
            case "time" -> "Time";
            default -> "Training points";
        };
        lines.add(UltraStyle.HEADER + "Hall of Infamy " + UltraStyle.DIM + "- " + UltraStyle.VALUE + label);
        List<Map.Entry<String, SparStore.LeaderboardEntry>> entries =
                new ArrayList<>(SparStore.get().leaderboard.entrySet());
        entries.sort((a, b) -> {
            SparStore.LeaderboardEntry ea = a.getValue();
            SparStore.LeaderboardEntry eb = b.getValue();
            double va = switch (cat) {
                case "sessions", "session" -> ea == null ? 0 : ea.sessions;
                case "perfect", "perfects" -> ea == null ? 0 : ea.perfectSessions;
                case "combo" -> ea == null ? 0 : ea.highestCombo;
                case "time" -> ea == null ? 0 : ea.totalTimeMs;
                default -> ea == null ? 0 : ea.totalTp;
            };
            double vb = switch (cat) {
                case "sessions", "session" -> eb == null ? 0 : eb.sessions;
                case "perfect", "perfects" -> eb == null ? 0 : eb.perfectSessions;
                case "combo" -> eb == null ? 0 : eb.highestCombo;
                case "time" -> eb == null ? 0 : eb.totalTimeMs;
                default -> eb == null ? 0 : eb.totalTp;
            };
            return Double.compare(vb, va);
        });
        int i = 1;
        for (Map.Entry<String, SparStore.LeaderboardEntry> e : entries) {
            if (i > Math.max(1, limit)) {
                break;
            }
            SparStore.LeaderboardEntry lb = e.getValue();
            if (lb == null) {
                continue;
            }
            String value = switch (cat) {
                case "sessions", "session" -> String.valueOf(lb.sessions);
                case "perfect", "perfects" -> String.valueOf(lb.perfectSessions);
                case "combo" -> String.valueOf(lb.highestCombo);
                case "time" -> (lb.totalTimeMs / 60000L) + "m";
                default -> DmzRewards.formatWhole(lb.totalTp) + " TP";
            };
            lines.add(UltraStyle.YELLOW + "#" + i + " " + UltraStyle.VALUE + (lb.name == null || lb.name.isBlank() ? "?" : lb.name)
                    + " " + UltraStyle.LABEL + value);
            i++;
        }
        if (i == 1) {
            lines.add(UltraStyle.LABEL + "No sparring data yet.");
        }
        return lines;
    }

    public static List<String> dojoTopLines(String category, int limit) {
        return DojoRankings.topLines(category, limit);
    }

    public static List<String> dojoTopCards(String category, int limit) {
        return DojoRankings.topCards(category, limit);
    }

    public static List<String> dojoInfoLines(ServerPlayer player) {
        return DojoRankings.infoLines(player);
    }

    public static String dojoChallenge(ServerPlayer player, ServerPlayer target) {
        return DojoRankings.challengeDojo(player, target);
    }

    public static String dojoChallengeByMasterUuid(ServerPlayer player, String targetMasterUuid) {
        return DojoRankings.challengeDojoByMasterKey(player, targetMasterUuid);
    }

    public static String dojoAcceptWar(ServerPlayer player) {
        return DojoRankings.acceptChallenge(player);
    }

    public static String dojoDeclineWar(ServerPlayer player) {
        return DojoRankings.declineChallenge(player);
    }

    public static List<String> pendingDojoWarCards(ServerPlayer player) {
        return DojoRankings.pendingDojoWarCards(player);
    }

    public static int pendingDojoWarCount(ServerPlayer player) {
        return DojoRankings.pendingDojoWarCount(player);
    }

    public static String dojoRevokeWar(ServerPlayer player, String targetMasterUuid) {
        return DojoRankings.revokeOutgoingChallenge(player, targetMasterUuid);
    }

    public static List<String> rivalDojoCards(ServerPlayer player) {
        return DojoRankings.rivalDojoCards(player);
    }

    public static List<String> dojoHallOfFameLines() {
        return DojoRankings.hallOfFameLines();
    }

    public static List<String> dojoMemberLines(ServerPlayer player) {
        return DojoRankings.memberLines(player);
    }

    public static List<String> dojoMemberCards(ServerPlayer player) {
        return DojoRankings.memberContributionCards(player);
    }

    public static String dojoSetName(ServerPlayer player, String name) {
        return DojoRankings.setDojoName(player, name);
    }

    public static String dojoSetBanner(ServerPlayer player, String material) {
        return DojoRankings.setDojoBanner(player, material);
    }

    public static String endCommand(ServerPlayer player) {
        SparPlayerRuntime rt = runtime(player.m_20148_());
        if (!rt.active) {
            return UltraStyle.DANGER + "No active spar session.";
        }
        ServerPlayer partner = player.m_20194_() == null || rt.partner == null
                ? null
                : player.m_20194_().m_6846_().m_11259_(rt.partner);
        endSession(player, partner, "command");
        return UltraStyle.YELLOW + "Spar session ended.";
    }

    public static String setTpMsg(ServerPlayer player, Boolean on) {
        if (player == null) {
            return UltraStyle.DANGER + "Players only.";
        }
        if (on == null) {
            boolean cur = SparStore.get().tpMessagesOn(player.m_20148_());
            return UltraStyle.YELLOW + "Spar TP chat: " + (cur ? "ON" : "OFF");
        }
        SparStore.get().setTpMessages(player.m_20148_(), on);
        return UltraStyle.YELLOW + "Spar TP chat " + (on ? "ON" : "OFF")
                + (on ? " — combat TP gains show in chat" : " — muted");
    }

    public static String setMentorTpMsg(ServerPlayer player, Boolean on) {
        if (player == null) {
            return UltraStyle.DANGER + "Players only.";
        }
        if (on == null) {
            boolean cur = SparStore.get().mentorTpMessagesOn(player.m_20148_());
            return UltraStyle.YELLOW + "Mentor TP chat: " + (cur ? "ON" : "OFF");
        }
        SparStore.get().setMentorTpMessages(player.m_20148_(), on);
        return UltraStyle.YELLOW + "Mentor TP chat " + (on ? "ON" : "OFF")
                + (on ? " — apprentice share TP shows in chat" : " — muted");
    }

    public static String mentorInvite(ServerPlayer player, ServerPlayer target) {
        if (target == null) {
            return UltraStyle.DANGER + "Player not online.";
        }
        if (player.m_20148_().equals(target.m_20148_())) {
            return UltraStyle.DANGER + "You cannot mentor yourself.";
        }
        SparStore.MentorBond mine = SparStore.get().bond(player.m_20148_());
        long now = System.currentTimeMillis();
        // Cooldown set when releasing an apprentice / changing roster.
        if (now < mine.apprenticeChangeReadyAt) {
            return UltraStyle.DANGER + "Dojo cooldown active (12h).";
        }
        if (mine.isMentoringUuid(target.m_20148_().toString())) {
            return UltraStyle.DANGER + "You already mentor " + UltraStyle.VALUE + target.m_7755_().getString() + UltraStyle.DANGER + ".";
        }
        if (mine.apprenticeCount() >= MAX_APPRENTICES) {
            return UltraStyle.DANGER + "Dojo full (" + UltraStyle.VALUE + MAX_APPRENTICES + " " + UltraStyle.DANGER + "apprentices max).";
        }
        SparStore.MentorBond theirs = SparStore.get().bond(target.m_20148_());
        if (theirs.mentorUuid != null && !theirs.mentorUuid.isBlank()) {
            return UltraStyle.DANGER + "They already have a mentor.";
        }
        SparStore.BondInvite invite = new SparStore.BondInvite();
        invite.fromUuid = player.m_20148_().toString();
        invite.fromName = player.m_7755_().getString();
        invite.kind = "apprentice"; // player asks target to be apprentice
        invite.expiresAt = now + MENTOR_INVITE_MS;
        SparStore.get().invites.put(target.m_20148_().toString(), invite);
        SparStore.get().markDirty();
        DmzRewards.msg(target, LmChat.note("Mentor", UltraStyle.VALUE + invite.fromName
                + " " + UltraStyle.YELLOW + "wants you as their Apprentice."));
        DmzRewards.msg(target, LmChat.tip("/spar", "- Training bonds - Bond invites to Accept or Decline"));
        return UltraStyle.GREEN + "Invite sent to " + UltraStyle.VALUE + target.m_7755_().getString() + UltraStyle.GREEN + ".";
    }

    public static String apprenticeInvite(ServerPlayer player, ServerPlayer target) {
        if (target == null) {
            return UltraStyle.DANGER + "Player not online.";
        }
        if (player.m_20148_().equals(target.m_20148_())) {
            return UltraStyle.DANGER + "Invalid target.";
        }
        SparStore.MentorBond mine = SparStore.get().bond(player.m_20148_());
        long now = System.currentTimeMillis();
        // Cooldown set when leaving a mentor.
        if (now < mine.mentorChangeReadyAt) {
            return UltraStyle.DANGER + "Mentor change cooldown active (12h).";
        }
        if (mine.mentorUuid != null && !mine.mentorUuid.isBlank()) {
            return UltraStyle.DANGER + "You already have a mentor.";
        }
        SparStore.MentorBond theirs = SparStore.get().bond(target.m_20148_());
        if (theirs.apprenticeCount() >= MAX_APPRENTICES) {
            return UltraStyle.DANGER + "Their dojo is full (" + UltraStyle.VALUE + MAX_APPRENTICES + " " + UltraStyle.DANGER + "apprentices).";
        }
        SparStore.BondInvite invite = new SparStore.BondInvite();
        invite.fromUuid = player.m_20148_().toString();
        invite.fromName = player.m_7755_().getString();
        invite.kind = "mentor"; // player asks target to be mentor
        invite.expiresAt = now + MENTOR_INVITE_MS;
        SparStore.get().invites.put(target.m_20148_().toString(), invite);
        SparStore.get().markDirty();
        DmzRewards.msg(target, LmChat.note("Mentor", UltraStyle.VALUE + invite.fromName
                + " " + UltraStyle.YELLOW + "wants you as their Mentor."));
        DmzRewards.msg(target, LmChat.tip("/spar", "- Training bonds - Bond invites to Accept or Decline"));
        return UltraStyle.GREEN + "Invite sent to " + UltraStyle.VALUE + target.m_7755_().getString() + UltraStyle.GREEN + ".";
    }

    /**
     * Encoded pending mentor invites for GUI boards:
     * {@code uuid\tname\tIN|OUT\texpiresAtMs\tonline(0/1)\tkind(mentor|apprentice)}.
     * IN = someone invited you; OUT = you invited them. Kind is the role requested of the recipient.
     */
    public static List<String> pendingMentorInviteCards(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        expireInvites(System.currentTimeMillis());
        String me = player.m_20148_().toString();
        MinecraftServer server = player.m_20194_();
        long now = System.currentTimeMillis();

        SparStore.BondInvite incoming = SparStore.get().invites.get(me);
        if (incoming != null && now <= incoming.expiresAt) {
            String uuid = incoming.fromUuid == null ? "" : incoming.fromUuid;
            String name = incoming.fromName == null || incoming.fromName.isBlank() ? uuid : incoming.fromName;
            name = name.replace('\t', ' ').replace('\n', ' ');
            boolean online = isOnline(server, uuid);
            String kind = incoming.kind == null || incoming.kind.isBlank() ? "apprentice" : incoming.kind;
            out.add(uuid + "\t" + name + "\tIN\t" + incoming.expiresAt + "\t" + (online ? "1" : "0")
                    + "\t" + kind);
        }

        for (Map.Entry<String, SparStore.BondInvite> e : SparStore.get().invites.entrySet()) {
            SparStore.BondInvite inv = e.getValue();
            if (inv == null || now > inv.expiresAt) {
                continue;
            }
            if (inv.fromUuid == null || !me.equals(inv.fromUuid)) {
                continue;
            }
            String targetUuid = e.getKey() == null ? "" : e.getKey();
            if (targetUuid.equals(me)) {
                continue;
            }
            String name = resolveName(server, targetUuid, inv);
            name = name.replace('\t', ' ').replace('\n', ' ');
            boolean online = isOnline(server, targetUuid);
            String kind = inv.kind == null || inv.kind.isBlank() ? "apprentice" : inv.kind;
            out.add(targetUuid + "\t" + name + "\tOUT\t" + inv.expiresAt + "\t" + (online ? "1" : "0")
                    + "\t" + kind);
        }
        return out;
    }

    public static int pendingMentorInviteCount(ServerPlayer player) {
        return pendingMentorInviteCards(player).size();
    }

    /** Incoming invite args ({@code uuid:} preferred) for Accept/Decline pickers. */
    public static List<String> pendingIncomingMentorArgs(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        expireInvites(System.currentTimeMillis());
        SparStore.BondInvite incoming = SparStore.get().invites.get(player.m_20148_().toString());
        if (incoming == null || System.currentTimeMillis() > incoming.expiresAt) {
            return out;
        }
        if (incoming.fromUuid != null && !incoming.fromUuid.isBlank()) {
            out.add("uuid:" + incoming.fromUuid);
        } else if (incoming.fromName != null && !incoming.fromName.isBlank()) {
            out.add(incoming.fromName);
        }
        return out;
    }

    public static String mentorAccept(ServerPlayer player) {
        return mentorAccept(player, null);
    }

    public static String mentorAccept(ServerPlayer player, String fromArg) {
        String me = player == null ? "?" : player.m_20148_().toString();
        SparStore.BondInvite invite = SparStore.get().invites.get(me);
        long now = System.currentTimeMillis();
        boolean found = invite != null;
        boolean expired = invite != null && now > invite.expiresAt;
        boolean match =
                fromArg == null || fromArg.isBlank() || matchesInviteFrom(invite, fromArg);
        LmLog.event(
                "[{}] mentorAccept player={} fromArg={} inviteFound={} expired={} match={} "
                        + "inviteFromUuid={} inviteFromName={} inviteKind={} expiresInMs={}",
                AdaptiveDifficultyMod.MOD_ID,
                player == null ? "?" : player.m_7755_().getString(),
                fromArg,
                found,
                expired,
                match,
                invite == null ? null : invite.fromUuid,
                invite == null ? null : invite.fromName,
                invite == null ? null : invite.kind,
                invite == null ? -1L : (invite.expiresAt - now));
        if (invite == null || now > invite.expiresAt) {
            SparStore.get().invites.remove(me);
            SparStore.get().markDirty();
            return UltraStyle.DANGER + "No pending invite.";
        }
        if (fromArg != null && !fromArg.isBlank() && !match) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] mentorAccept picker mismatch player={} fromArg={} inviteFromUuid={} inviteFromName={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    player.m_7755_().getString(),
                    fromArg,
                    invite.fromUuid,
                    invite.fromName);
            return UltraStyle.DANGER + "That invite is not pending for you.";
        }
        MinecraftServer server = player.m_20194_();
        ServerPlayer other = resolveInviter(server, invite);
        if (other == null) {
            LmLog.event(
                    "[{}] mentorAccept inviter offline player={} fromUuid={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    player.m_7755_().getString(),
                    invite.fromUuid);
            return UltraStyle.DANGER + "Inviter is no longer online.";
        }
        ServerPlayer mentor;
        ServerPlayer apprentice;
        if ("mentor".equals(invite.kind)) {
            mentor = player;
            apprentice = other;
        } else {
            mentor = other;
            apprentice = player;
        }
        SparStore.MentorBond mentorBond = SparStore.get().bond(mentor.m_20148_());
        SparStore.MentorBond apprenticeBond = SparStore.get().bond(apprentice.m_20148_());
        if (apprenticeBond.mentorUuid != null && !apprenticeBond.mentorUuid.isBlank()
                && !apprenticeBond.mentorUuid.equalsIgnoreCase(mentor.m_20148_().toString())) {
            SparStore.get().invites.remove(player.m_20148_().toString());
            SparStore.get().markDirty();
            return UltraStyle.DANGER + "They already have a mentor.";
        }
        if (!mentorBond.isMentoringUuid(apprentice.m_20148_().toString())
                && mentorBond.apprenticeCount() >= MAX_APPRENTICES) {
            SparStore.get().invites.remove(player.m_20148_().toString());
            SparStore.get().markDirty();
            return UltraStyle.DANGER + "Dojo full (" + UltraStyle.VALUE + MAX_APPRENTICES + " " + UltraStyle.DANGER + "apprentices max).";
        }
        bindMentor(mentor, apprentice);
        SparStore.get().invites.remove(player.m_20148_().toString());
        SparStore.get().markDirty();
        String mentoring = "You are now mentoring " + UltraStyle.VALUE + apprentice.m_7755_().getString()
                + " " + UltraStyle.DIM + "(" + mentorBond.apprenticeCount() + "/" + MAX_APPRENTICES + ")";
        String mentored = "Your mentor is now " + UltraStyle.VALUE + mentor.m_7755_().getString();
        // The player who clicked already gets the return line on the menu. The other person does not.
        if (player == mentor) {
            DmzRewards.msg(apprentice, LmChat.ok("Mentor", mentored));
            return UltraStyle.GREEN + mentoring;
        }
        DmzRewards.msg(mentor, LmChat.ok("Mentor", mentoring));
        return UltraStyle.GREEN + mentored;
    }

    public static String mentorDecline(ServerPlayer player) {
        return mentorDecline(player, null);
    }

    public static String mentorDecline(ServerPlayer player, String fromArg) {
        SparStore.BondInvite invite = SparStore.get().invites.get(player.m_20148_().toString());
        if (invite == null) {
            return UltraStyle.DANGER + "No pending invite.";
        }
        if (fromArg != null && !fromArg.isBlank() && !matchesInviteFrom(invite, fromArg)) {
            return UltraStyle.DANGER + "That invite is not pending for you.";
        }
        SparStore.get().invites.remove(player.m_20148_().toString());
        MinecraftServer server = player.m_20194_();
        if (server != null) {
            try {
                ServerPlayer other = server.m_6846_().m_11259_(UUID.fromString(invite.fromUuid));
                if (other != null) {
                    DmzRewards.msg(other, LmChat.fail("Mentor", UltraStyle.VALUE + player.m_7755_().getString()
                            + " " + UltraStyle.DANGER + "denied your invite."));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        SparStore.get().markDirty();
        return UltraStyle.LABEL + "Invite denied.";
    }

    /** Cancel an outgoing invite you sent to {@code targetArg} (uuid: or name). */
    public static String mentorCancelInvite(ServerPlayer player, String targetArg) {
        if (player == null) {
            return UltraStyle.DANGER + "Players only.";
        }
        if (targetArg == null || targetArg.isBlank()) {
            return UltraStyle.DANGER + "Pick whose invite to cancel.";
        }
        expireInvites(System.currentTimeMillis());
        String me = player.m_20148_().toString();
        String targetKey = resolveInviteTargetKey(player, targetArg);
        if (targetKey == null) {
            return UltraStyle.DANGER + "No outgoing invite to that player.";
        }
        SparStore.BondInvite inv = SparStore.get().invites.get(targetKey);
        if (inv == null || inv.fromUuid == null || !me.equals(inv.fromUuid)) {
            return UltraStyle.DANGER + "No outgoing invite to that player.";
        }
        SparStore.get().invites.remove(targetKey);
        SparStore.get().markDirty();
        MinecraftServer server = player.m_20194_();
        if (server != null) {
            try {
                ServerPlayer other = server.m_6846_().m_11259_(UUID.fromString(targetKey));
                if (other != null) {
                    DmzRewards.msg(other, LmChat.info("Mentor", UltraStyle.VALUE + player.m_7755_().getString()
                            + " " + UltraStyle.LABEL + "cancelled their invite."));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return UltraStyle.LABEL + "Outgoing invite cancelled.";
    }

    /** Leave mentor bond or release apprentice, whichever applies. */
    public static String removeBond(ServerPlayer player) {
        SparStore.MentorBond bond = SparStore.get().bond(player.m_20148_());
        boolean hasMentor = bond.mentorUuid != null && !bond.mentorUuid.isBlank();
        boolean hasApprentice = bond.apprenticeCount() > 0;
        if (hasApprentice && !hasMentor) {
            return removeApprentice(player, null);
        }
        if (hasMentor && !hasApprentice) {
            return removeMentor(player);
        }
        if (hasMentor && hasApprentice) {
            return UltraStyle.YELLOW + "Leave your mentor, or release one of your apprentices?";
        }
        return UltraStyle.DANGER + "You have no mentor bond to remove.";
    }

    public static String removeMentor(ServerPlayer player) {
        SparStore.MentorBond bond = SparStore.get().bond(player.m_20148_());
        if (bond.mentorUuid == null || bond.mentorUuid.isBlank()) {
            return UltraStyle.DANGER + "You have no mentor.";
        }
        String mentorName = bond.mentorName;
        clearBond(player.m_20148_().toString(), bond.mentorUuid, true);
        return UltraStyle.LABEL + "Left mentor " + UltraStyle.VALUE + mentorName + UltraStyle.LABEL + ". 12-hour cooldown started.";
    }

    public static String removeApprentice(ServerPlayer player) {
        return removeApprentice(player, null);
    }

    /**
     * Release one apprentice. With no arg and a single apprentice, releases that one;
     * with multiple apprentices, {@code targetArg} (uuid: or name) is required.
     */
    public static String removeApprentice(ServerPlayer player, String targetArg) {
        SparStore.MentorBond bond = SparStore.get().bond(player.m_20148_());
        bond.normalizeApprentices();
        if (bond.apprenticeCount() <= 0) {
            return UltraStyle.DANGER + "You have no apprentice.";
        }
        String appUuid;
        String name;
        if (targetArg != null && !targetArg.isBlank()) {
            SparStore.ApprenticeRef match = findApprentice(bond, player, targetArg);
            if (match == null) {
                return UltraStyle.DANGER + "That player is not your apprentice.";
            }
            appUuid = match.uuid;
            name = match.name == null || match.name.isBlank() ? match.uuid : match.name;
        } else if (bond.apprenticeCount() == 1) {
            SparStore.ApprenticeRef only = bond.apprentices.get(0);
            appUuid = only.uuid;
            name = only.name == null || only.name.isBlank() ? only.uuid : only.name;
        } else {
            return UltraStyle.YELLOW + "Pick which apprentice to release (" + UltraStyle.VALUE
                    + bond.apprenticeNamesSummary()
                    + UltraStyle.YELLOW + ").\n" + UltraStyle.DIM + "GUI: Training bonds - Release apprentice…";
        }
        clearBond(appUuid, player.m_20148_().toString(), true);
        return UltraStyle.LABEL + "Released apprentice " + UltraStyle.VALUE + name + UltraStyle.LABEL + ". 12-hour cooldown started.";
    }

    /** Encoded dojo roster for GUI: {@code uuid\tname}. */
    public static List<String> apprenticeCards(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        SparStore.MentorBond bond = SparStore.get().bond(player.m_20148_());
        bond.normalizeApprentices();
        for (SparStore.ApprenticeRef r : bond.apprentices) {
            String uuid = r.uuid == null ? "" : r.uuid;
            String name = r.name == null || r.name.isBlank() ? uuid : r.name;
            name = name.replace('\t', ' ').replace('\n', ' ');
            out.add(uuid + "\t" + name);
        }
        return out;
    }

    /**
     * Membership dojo for GUI: mentor first, then every apprentice in that dojo.
     * Encoded as {@code role\tuuid\tname} where role is {@code mentor}, {@code you}, or {@code apprentice}.
     */
    public static List<String> membershipDojoCards(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        SparStore.MentorBond mine = SparStore.get().bond(player.m_20148_());
        if (mine.mentorUuid == null || mine.mentorUuid.isBlank()) {
            return out;
        }
        String mentorUuid = mine.mentorUuid.trim();
        String mentorName = mine.mentorName == null ? "" : mine.mentorName.trim();
        MinecraftServer server = player.m_20194_();
        if (mentorName.isBlank() && server != null) {
            try {
                ServerPlayer onlineMentor = server.m_6846_().m_11259_(UUID.fromString(mentorUuid));
                if (onlineMentor != null) {
                    mentorName = onlineMentor.m_7755_().getString();
                    mine.mentorName = mentorName;
                    SparStore.get().markDirty();
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (mentorName.isBlank()) {
            mentorName = "Mentor";
        }
        mentorName = mentorName.replace('\t', ' ').replace('\n', ' ');
        out.add("mentor\t" + mentorUuid + "\t" + mentorName);

        SparStore.MentorBond mentorBond = SparStore.get().bondsByPlayer.get(mentorUuid);
        String selfUuid = player.m_20148_().toString();
        boolean listedSelf = false;
        if (mentorBond != null) {
            mentorBond.normalizeApprentices();
            for (SparStore.ApprenticeRef r : mentorBond.apprentices) {
                String uuid = r.uuid == null ? "" : r.uuid;
                if (uuid.isBlank()) {
                    continue;
                }
                String name = r.name == null || r.name.isBlank() ? uuid : r.name;
                name = name.replace('\t', ' ').replace('\n', ' ');
                boolean you = selfUuid.equalsIgnoreCase(uuid);
                if (you) {
                    listedSelf = true;
                }
                out.add((you ? "you" : "apprentice") + "\t" + uuid + "\t" + name);
            }
        }
        if (!listedSelf) {
            String selfName = player.m_7755_().getString().replace('\t', ' ').replace('\n', ' ');
            out.add("you\t" + selfUuid + "\t" + selfName);
        }
        return out;
    }

    private static SparStore.ApprenticeRef findApprentice(
            SparStore.MentorBond bond, ServerPlayer mentor, String targetArg
    ) {
        if (bond == null || targetArg == null || targetArg.isBlank()) {
            return null;
        }
        bond.normalizeApprentices();
        String raw = targetArg.trim();
        String wantUuid = null;
        String wantName = null;
        if (raw.regionMatches(true, 0, "uuid:", 0, 5)) {
            wantUuid = raw.substring(5).trim();
        } else if (isUuidLiteral(raw)) {
            // The Release picker hands the bare uuid (cards are uuid\tname). A uuid-shaped
            // arg can never match the online-name lookup or the stored-name match below,
            // so treat it as the uuid key - same as the "uuid:"-prefixed path.
            wantUuid = raw;
        } else {
            wantName = raw;
            MinecraftServer server = mentor == null ? null : mentor.m_20194_();
            if (server != null) {
                ServerPlayer online = server.m_6846_().m_11255_(raw);
                if (online != null) {
                    wantUuid = online.m_20148_().toString();
                }
            }
        }
        for (SparStore.ApprenticeRef r : bond.apprentices) {
            if (wantUuid != null && wantUuid.equalsIgnoreCase(r.uuid)) {
                return r;
            }
            if (wantName != null && r.name != null && wantName.equalsIgnoreCase(r.name)) {
                return r;
            }
        }
        return null;
    }

    /**
     * True when {@code s} parses as a java UUID. GUI pickers emit the bare uuid from their
     * card payload, so a uuid-shaped target arg must key the lookup as a uuid, not a name.
     */
    private static boolean isUuidLiteral(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        try {
            java.util.UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isOnline(MinecraftServer server, String uuid) {
        if (server == null || uuid == null || uuid.isBlank()) {
            return false;
        }
        try {
            return server.m_6846_().m_11259_(UUID.fromString(uuid)) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String resolveName(MinecraftServer server, String uuid, SparStore.BondInvite inv) {
        if (server != null && uuid != null && !uuid.isBlank()) {
            try {
                ServerPlayer online = server.m_6846_().m_11259_(UUID.fromString(uuid));
                if (online != null) {
                    return online.m_7755_().getString();
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return uuid == null || uuid.isBlank() ? "?" : uuid;
    }

    private static ServerPlayer resolveInviter(MinecraftServer server, SparStore.BondInvite invite) {
        if (server == null || invite == null || invite.fromUuid == null || invite.fromUuid.isBlank()) {
            return null;
        }
        try {
            return server.m_6846_().m_11259_(UUID.fromString(invite.fromUuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean matchesInviteFrom(SparStore.BondInvite invite, String fromArg) {
        if (invite == null || fromArg == null || fromArg.isBlank()) {
            return false;
        }
        String raw = fromArg.trim();
        if (raw.regionMatches(true, 0, "uuid:", 0, 5)) {
            return invite.fromUuid != null
                    && invite.fromUuid.equalsIgnoreCase(raw.substring(5).trim());
        }
        return invite.fromUuid != null && invite.fromUuid.equalsIgnoreCase(raw)
                || invite.fromName != null && invite.fromName.equalsIgnoreCase(raw);
    }

    private static String resolveInviteTargetKey(ServerPlayer player, String targetArg) {
        String raw = targetArg.trim();
        if (raw.regionMatches(true, 0, "uuid:", 0, 5)) {
            String id = raw.substring(5).trim();
            if (SparStore.get().invites.containsKey(id)) {
                return id;
            }
            return null;
        }
        if (SparStore.get().invites.containsKey(raw)) {
            return raw;
        }
        MinecraftServer server = player.m_20194_();
        if (server != null) {
            ServerPlayer online = server.m_6846_().m_11255_(raw);
            if (online != null) {
                String id = online.m_20148_().toString();
                if (SparStore.get().invites.containsKey(id)) {
                    return id;
                }
            }
        }
        return null;
    }

    public static String resetMentorCd(ServerPlayer admin, ServerPlayer target) {
        ServerPlayer t = target == null ? admin : target;
        SparStore.MentorBond bond = SparStore.get().bond(t.m_20148_());
        bond.mentorChangeReadyAt = 0L;
        bond.apprenticeChangeReadyAt = 0L;
        SparStore.get().markDirty();
        return UltraStyle.GREEN + "Cleared mentor/apprentice cooldowns for " + UltraStyle.VALUE + t.m_7755_().getString();
    }

    public static String bondStatus(ServerPlayer player) {
        SparStore.MentorBond bond = SparStore.get().bond(player.m_20148_());
        int n = bond.apprenticeCount();
        String apps = n <= 0 ? "none" : n + "/" + MAX_APPRENTICES + " (" + bond.apprenticeNamesSummary() + ")";
        return UltraStyle.GOLD + "Mentor: " + UltraStyle.VALUE + blank(bond.mentorName, "none")
                + " " + UltraStyle.DIM + "| " + UltraStyle.GOLD + "Apprentices: " + UltraStyle.VALUE + apps;
    }

    private static void bindMentor(ServerPlayer mentor, ServerPlayer apprentice) {
        SparStore.MentorBond m = SparStore.get().bond(mentor.m_20148_());
        SparStore.MentorBond a = SparStore.get().bond(apprentice.m_20148_());
        m.addApprentice(apprentice.m_20148_().toString(), apprentice.m_7755_().getString());
        a.mentorUuid = mentor.m_20148_().toString();
        a.mentorName = mentor.m_7755_().getString();
        SparStore.get().markDirty();
    }

    private static void clearBond(String apprenticeUuid, String mentorUuid, boolean cooldown) {
        long ready = System.currentTimeMillis() + MENTOR_CHANGE_COOLDOWN_MS;
        SparStore.MentorBond a = SparStore.get().bondsByPlayer.get(apprenticeUuid);
        SparStore.MentorBond m = SparStore.get().bondsByPlayer.get(mentorUuid);
        if (a != null) {
            a.mentorUuid = "";
            a.mentorName = "";
            if (cooldown) {
                a.mentorChangeReadyAt = ready;
            }
        }
        if (m != null) {
            m.normalizeApprentices();
            m.removeApprenticeUuid(apprenticeUuid);
            if (cooldown) {
                m.apprenticeChangeReadyAt = ready;
            }
        }
        SparStore.get().markDirty();
    }

    private static void expireInvites(long now) {
        Iterator<Map.Entry<String, SparStore.BondInvite>> it = SparStore.get().invites.entrySet().iterator();
        while (it.hasNext()) {
            SparStore.BondInvite inv = it.next().getValue();
            if (inv == null || now > inv.expiresAt) {
                it.remove();
            }
        }
    }

    private static String blank(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }
}
