package com.dbzlegacy.adaptivedifficulty.progression.skills;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.progression.DmzSkillUtil;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionConfig;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.ScreenNotify;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Status;
import com.dragonminez.common.stats.skills.Skills;
import com.dragonminez.server.util.GravityLogic;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Port of {@code Potential.js} — PvP hit/block progress into {@code potentialunlock}.
 * <p>
 * Script parity: movement gate + warnings, method streak (check-before-increment),
 * gravity/weight/prestige multipliers, hard max 30, mentor TP.
 * Natural sparring runs the same {@code next * 100} point curve through level 30.
 */
public final class PotentialProgression {
    private static final String SKILL = "potentialunlock";
    private static final int HARD_MAX = 30;
    private static final int MAX_SAME_STREAK = 5;
    private static final long DUP_WINDOW_MS = 500L;
    private static final int MAX_POINTS_PER_HIT = 30;
    private static final double MAX_G = 1000.0;
    private static final double MAX_W = 1000.0;
    private static final double MAX_G_MULT = 5.0;
    private static final double MAX_W_MULT = 2.0;
    private static final double PRESTIGE_PER = 0.10;
    private static final int MENTOR_TP = 50;
    private static final long MENTOR_TP_MSG_CD_MS = 10_000L;
    private static final int TP_PER_LEVEL_DIFF = 500;
    private static final double MIN_MOVE = 2.0;
    private static final long MOVE_VALID_MS = 5000L;
    private static final long MOVE_WARN_CD_MS = 10_000L;

    private PotentialProgression() {}

    public static void onPlayerHurt(
            ServerPlayer victim,
            ServerPlayer attacker,
            DamageSource source
    ) {
        if (!ProgressionConfig.potential() || victim == null || attacker == null) {
            return;
        }
        if (victim.m_20148_().equals(attacker.m_20148_())) {
            return;
        }
        // 4.7.68: inside a formal spar session (a Spar Board spar in a dojo arena) Potential Unlock trains only for
        // a fighter who opted in ("Looking for: Potential Unlock" on a friendly spar); ranked spars never train it.
        // The session decides only WHETHER a fighter's points accrue, never how many: the same apply() gates and
        // curve as the world. Outside a session nothing changes.
        boolean session = com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.inSessionTogether(victim, attacker);
        boolean attackerTrains = !session
                || com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.trainsPotentialInSession(attacker);
        boolean victimTrains = !session
                || com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.trainsPotentialInSession(victim);
        if (!attackerTrains && !victimTrains) {
            return;
        }
        try {
            // Attacker gains from hitting (script damagedEntity)
            String atkMethod = "physical_hit";
            int atkPoints = 3;
            Entity immediate = source == null ? null : source.m_7640_(); // getDirectEntity
            if (immediate instanceof AbstractKiProjectile
                    || (immediate instanceof Projectile p && isKiLike(p))) {
                atkMethod = "ki_attack";
                atkPoints = 3;
            }
            if (attackerTrains) {
                apply(attacker, victim, atkMethod, atkPoints);
            }

            // Victim gains from getting hit / blocking (script damaged)
            String defMethod = "getting_hit";
            int defPoints = 1;
            try {
                StatsData data = DmzProgression.stats(victim);
                Status status = data == null ? null : data.getStatus();
                if (status != null && status.isBlocking()) {
                    defMethod = "blocking";
                    defPoints = 2;
                }
            } catch (Throwable ignored) {
            }
            if (victimTrains) {
                apply(victim, attacker, defMethod, defPoints);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isKiLike(Projectile projectile) {
        try {
            return projectile.getClass().getName().toLowerCase().contains("ki");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void apply(
            ServerPlayer player,
            ServerPlayer other,
            String method,
            int basePoints
    ) {
        StatsData playerData = DmzProgression.stats(player);
        StatsData otherData = DmzProgression.stats(other);
        if (playerData == null || otherData == null) {
            return;
        }
        Skills skills = playerData.getSkills();
        if (skills == null) {
            return;
        }
        // Natural sparring continues through the hard max of 30.
        DmzSkillUtil.ensureRegistered(skills, SKILL, HARD_MAX);
        int current = DmzSkillUtil.level(skills, SKILL);
        resetIfNeeded(player, current);
        if (current >= HARD_MAX) {
            return;
        }
        if (!hasMovedEnough(player)) {
            return;
        }
        // Script: duplicate window before method-streak so rapid same-target hits
        // do not burn the 5-streak rotation.
        if (isDuplicate(player, method, other.m_19879_())) {
            return;
        }
        if (!allowMethod(player, method)) {
            return;
        }
        int points = calculatePoints(player, basePoints);
        int next = current + 1;
        int required = next * 100;
        String progressKey = "potentialunlock_points_to_level_" + next;
        int progress = (int) ProgressionData.storedGetLong(player, progressKey, 0L) + points;
        if (progress > required) {
            progress = required;
        }
        giveMentorTrainingTp(player, playerData, other, otherData);
        potentialHud(player, progress, required, null);
        if (progress < required) {
            ProgressionData.storedPut(player, progressKey, progress);
            return;
        }
        ProgressionData.storedPut(player, progressKey, required);
        DmzSkillUtil.setLevel(skills, SKILL, next);
        int confirmed = DmzSkillUtil.level(skills, SKILL);
        if (confirmed < next) {
            potentialSubtitle(player, "Level-up failed.", ChatFormatting.RED, 0L);
            potentialSubtitle(
                    player,
                    "DMZ still reports Potential level " + confirmed + ".",
                    ChatFormatting.GRAY,
                    0L);
            potentialSubtitle(
                    player,
                    "Progress remains at " + required + "/" + required + ".",
                    ChatFormatting.GRAY,
                    0L);
            return;
        }
        ProgressionData.storedPut(player, "potentialunlock_last_known_level", confirmed);
        DmzSkillUtil.sync(player);
        potentialSubtitle(player, "Increased to level " + confirmed + ".", ChatFormatting.YELLOW, 0L);
        giveMentorLevelUpTp(player, playerData, other, otherData);
        SystemTelemetry.log("progression", "potential_level", player, other,
                Map.of("level", confirmed, "method", method));
    }

    /** Script resetPotentialProgressIfNeeded. */
    private static void resetIfNeeded(ServerPlayer player, int current) {
        boolean hasKey = ProgressionData.storedHas(player, "potentialunlock_last_known_level");
        long last = ProgressionData.storedGetLong(player, "potentialunlock_last_known_level", current);
        if (current < last) {
            for (int i = 1; i <= HARD_MAX; i++) {
                ProgressionData.storedRemove(player, "potentialunlock_points_to_level_" + i);
            }
            ProgressionData.storedRemove(player, "potentialunlock_last_method");
            ProgressionData.storedRemove(player, "potentialunlock_same_method_streak");
            ProgressionData.storedRemove(player, "potential_last_move_x");
            ProgressionData.storedRemove(player, "potential_last_move_z");
            ProgressionData.storedRemove(player, "potential_movement_valid_until");
            ProgressionData.storedPut(player, "potentialunlock_last_known_level", current);
            HUD_LAST.remove(player.m_20148_());          // 4.7.77: the old numbers no longer apply
            potentialSubtitle(
                    player,
                    "Progress requirements were reset because your Potential level was lowered.",
                    ChatFormatting.YELLOW,
                    0L);
            return;
        }
        if (current > last || !hasKey) {
            ProgressionData.storedPut(player, "potentialunlock_last_known_level", current);
        }
    }

    /** Script hasMovedEnoughForPotential + tellMovementRequired. */
    private static boolean hasMovedEnough(ServerPlayer player) {
        long now = System.currentTimeMillis();
        long validUntil = ProgressionData.storedGetLong(player, "potential_movement_valid_until", 0L);
        if (now < validUntil) {
            return true;
        }
        double x = player.m_20185_();
        double z = player.m_20189_();
        if (!ProgressionData.storedHas(player, "potential_last_move_x")
                || !ProgressionData.storedHas(player, "potential_last_move_z")) {
            ProgressionData.storedPut(player, "potential_last_move_x", x);
            ProgressionData.storedPut(player, "potential_last_move_z", z);
            tellMovementRequired(player);
            return false;
        }
        double ox = ProgressionData.storedGetDouble(player, "potential_last_move_x", x);
        double oz = ProgressionData.storedGetDouble(player, "potential_last_move_z", z);
        double dx = x - ox;
        double dz = z - oz;
        if (Math.sqrt(dx * dx + dz * dz) < MIN_MOVE) {
            tellMovementRequired(player);
            return false;
        }
        ProgressionData.storedPut(player, "potential_last_move_x", x);
        ProgressionData.storedPut(player, "potential_last_move_z", z);
        ProgressionData.storedPut(player, "potential_movement_valid_until", now + MOVE_VALID_MS);
        return true;
    }

    private static void tellMovementRequired(ServerPlayer player) {
        long now = System.currentTimeMillis();
        long next = ProgressionData.tempGetLong(player, "potential_move_warning_cooldown", 0L);
        if (now < next) {
            return;
        }
        ProgressionData.tempPut(player, "potential_move_warning_cooldown", now + MOVE_WARN_CD_MS);
        potentialSubtitle(
                player,
                "Move at least " + (int) MIN_MOVE + " blocks to keep gaining Potential progress.",
                ChatFormatting.YELLOW,
                0L);
    }

    /**
     * Script allowPotentialMethod — check streak before incrementing; on deny do not
     * update stored streak, and tell the player to switch methods.
     */
    private static boolean allowMethod(ServerPlayer player, String method) {
        String last = ProgressionData.storedGet(player, "potentialunlock_last_method", "");
        int streak = (int) ProgressionData.storedGetLong(player, "potentialunlock_same_method_streak", 0L);
        if (method != null && method.equals(last)) {
            if (streak >= MAX_SAME_STREAK) {
                potentialSubtitle(
                        player,
                        "Switch training methods to continue progressing.",
                        ChatFormatting.YELLOW,
                        0L);
                return false;
            }
            streak++;
        } else {
            last = method == null ? "" : method;
            streak = 1;
        }
        ProgressionData.storedPut(player, "potentialunlock_last_method", last);
        ProgressionData.storedPut(player, "potentialunlock_same_method_streak", streak);
        return true;
    }

    private static boolean isDuplicate(ServerPlayer player, String method, int entityId) {
        long now = System.currentTimeMillis();
        String key = "potentialunlock_duplicate_" + method + "_" + entityId;
        long last = ProgressionData.tempGetLong(player, key, 0L);
        if (last > 0L && now - last < DUP_WINDOW_MS) {
            return true;
        }
        ProgressionData.tempPut(player, key, now);
        return false;
    }

    private static int calculatePoints(ServerPlayer player, int basePoints) {
        double gMult = gravityMult(player);
        double wMult = weightMult(player);
        // DMZ prestige skill tracks the held wallet, not lifetime completed.
        int prestige = Math.min(10, Math.max(0, DmzProgression.prestige(player)));
        double pMult = 1.0 + prestige * PRESTIGE_PER;
        int calculated = (int) Math.floor(basePoints * gMult * wMult * pMult);
        if (calculated < 1) {
            calculated = 1;
        }
        return Math.min(calculated, MAX_POINTS_PER_HIT);
    }

    private static double gravityMult(ServerPlayer player) {
        try {
            double gravity = GravityLogic.getNetGravity(player);
            if (!(gravity >= 1.0) || Double.isNaN(gravity)) {
                gravity = 1.0;
            }
            double capped = Math.min(gravity, MAX_G);
            double progress = (capped - 1.0) / (MAX_G - 1.0);
            progress = Math.max(0.0, Math.min(1.0, progress));
            return 1.0 + progress * (MAX_G_MULT - 1.0);
        } catch (Throwable ignored) {
            return 1.0;
        }
    }

    /** Script getPotentialWeightMultiplier — effective weight only (no totalWeight fallback). */
    private static double weightMult(ServerPlayer player) {
        try {
            double weight = GravityLogic.getEffectiveWeight(player);
            if (Double.isNaN(weight) || weight < 0.0) {
                weight = 0.0;
            }
            double capped = Math.min(weight, MAX_W);
            double progress = capped / MAX_W;
            progress = Math.max(0.0, Math.min(1.0, progress));
            return 1.0 + progress * (MAX_W_MULT - 1.0);
        } catch (Throwable ignored) {
            return 1.0;
        }
    }

    private static void giveMentorTrainingTp(
            ServerPlayer player, StatsData playerData, ServerPlayer other, StatsData otherData
    ) {
        try {
            int playerLevel = playerData.getLevel();
            int otherLevel = otherData.getLevel();
            if (otherLevel <= playerLevel) {
                return;
            }
            DmzRewards.awardTp(other, MENTOR_TP, "potential mentor", false, "Mentor");
            long now = System.currentTimeMillis();
            long next = ProgressionData.tempGetLong(other, "potential_mentor_tp_message_cooldown", 0L);
            if (now >= next) {
                ProgressionData.tempPut(other, "potential_mentor_tp_message_cooldown", now + MENTOR_TP_MSG_CD_MS);
                potentialSubtitle(
                        other,
                        "You are gaining TP for helping train a lower-level player.",
                        ChatFormatting.YELLOW,
                        0L);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void giveMentorLevelUpTp(
            ServerPlayer player, StatsData playerData, ServerPlayer other, StatsData otherData
    ) {
        try {
            int playerLevel = playerData.getLevel();
            int otherLevel = otherData.getLevel();
            if (otherLevel <= playerLevel) {
                return;
            }
            int tp = (otherLevel - playerLevel) * TP_PER_LEVEL_DIFF;
            if (tp > 0) {
                // Script: "Gained X TP for helping unlock…" — showMessage true with that wording.
                DmzRewards.awardTp(other, tp, "helping unlock a lower-level player's potential",
                        true, "Potential");
            }
        } catch (Throwable ignored) {
        }
    }

    /** Potential notices are subtitles only (no chat). */
    private static final class HintRun {
        long at;
        final java.util.List<String> lines = new java.util.ArrayList<>();
    }

    private static final java.util.Map<java.util.UUID, HintRun> HUD_HINTS = new java.util.concurrent.ConcurrentHashMap<>();

    /** 4.7.77: drop a player's HUD state on logout. */
    public static void forgetHud(java.util.UUID id) {
        if (id != null) {
            HUD_LAST.remove(id);
            HUD_HINTS.remove(id);
        }
    }

    /** 4.7.75: last Potential progress shown per player (the hints reuse it). */
    private static final java.util.Map<java.util.UUID, int[]> HUD_LAST = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 4.7.75 Potential meter for HUD players: "Potential 34/200" with its bar, plus the hint line that would have
     * been a subtitle ("Switch training methods...", "Move at least..."). Fades a few seconds after training stops.
     */
    private static void potentialHud(ServerPlayer player, int progress, int required, String hint) {
        if (!com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.active(player)) {
            return;
        }
        int[] last = HUD_LAST.get(player.m_20148_());
        if (required > 0) {
            last = new int[] {progress, required};
            HUD_LAST.put(player.m_20148_(), last);
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        float bar = -1f;
        if (last != null && last[1] > 0) {
            lines.add(com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.LABEL + "Potential " + com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.color("f", last[0] + "/" + last[1]));
            bar = (float) Math.min(1.0, last[0] / (double) last[1]);
        } else {
            lines.add(com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.LABEL + "Potential");
        }
        if (hint != null) {
            // 4.7.77: notices sent together (a failed level-up sends three) stack instead of replacing each other.
            long now = System.currentTimeMillis();
            HintRun run = HUD_HINTS.get(player.m_20148_());
            if (run == null || now - run.at > 250L) {
                run = new HintRun();
                HUD_HINTS.put(player.m_20148_(), run);
            }
            run.at = now;
            if (run.lines.size() >= 3) {
                run.lines.remove(0);
            }
            run.lines.add(hint);
            lines.addAll(run.lines);
        }
        com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.show(player, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.POTENTIAL, lines, bar, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.accentOf("character"), "", com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.POTENTIAL_MS, false);
    }

    private static void potentialSubtitle(
            ServerPlayer player, String body, ChatFormatting color, long cooldownMs
    ) {
        if (player == null || body == null || body.isBlank()) {
            return;
        }
        if (com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.active(player)) {
            // 4.7.75: the hint rides the Potential meter instead of a subtitle on every hit.
            potentialHud(player, 0, 0, com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.color(color == ChatFormatting.RED ? "c" : color == ChatFormatting.GRAY ? "7" : "e", body));
            return;
        }
        ScreenNotify.subtitleOnly(
                player,
                body,
                color,
                cooldownMs > 0L ? "lm.potential.subtitle" : "",
                cooldownMs);
    }
}