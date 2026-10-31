package com.dbzlegacy.adaptivedifficulty.sparring;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.CombatRating;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.progression.race.AndroidConversion;
import com.dbzlegacy.adaptivedifficulty.rival.RivalChallengeManager;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.TpBossNotify;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import net.minecraft.ChatFormatting;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Status;
import com.dragonminez.server.util.GravityLogic;
import net.minecraft.server.level.ServerPlayer;

/** Sparring TP formula from Sparring Tp System 3.2.11. */
public final class SparCombat {
    public static final float BASE_TP_PER_HIT = 280.0f;
    public static final float DAMAGE_QUALITY_REF = 800.0f;
    public static final float MIN_DAMAGE_QUALITY = 0.35f;
    public static final float MAX_DAMAGE_QUALITY = 1.80f;
    public static final float MAX_BASE_TP_PER_HIT = 700.0f;
    public static final float MAX_TP_PER_ACTION = 250_000.0f;
    public static final float MAX_TP_PER_ACTION_BP_SCALE = 4.0f;
    public static final float GLOBAL_TP_GAIN_MULT = 1.50f;
    public static final float BLOCK_TP_BASE = 40.0f;
    public static final float BEAM_CLASH_TP_PER_TICK = 35.0f;
    public static final float KNOCKBACK_RECOVERY_TP = 45.0f;
    public static final float MENTOR_SHARE_PCT = 0.15f;
    public static final float MENTOR_SPAR_BONUS_PCT = 0.18f;
    /** Bonus when two apprentices of the same mentor spar each other (dojo peers). */
    public static final float DOJO_PEER_SPAR_BONUS_PCT = 0.10f;
    public static final float MAX_RIVAL_MULTIPLIER = 3.0f;
    public static final float MAX_BP_MULTIPLIER = 600.0f;
    public static final float MAX_GRAVITY = 1000.0f;
    public static final float MAX_GRAVITY_MULTIPLIER = 5.0f;
    public static final float MAX_EFFECTIVE_WEIGHT = 1000.0f;
    public static final float MAX_WEIGHT_MULTIPLIER = 2.0f;
    public static final float MIN_RELEASE_PERCENT = 100.0f;
    public static final float MAX_RELEASE_PERCENT = 200.0f;
    public static final float MAX_RELEASE_MULTIPLIER = 2.0f;
    public static final int MAX_PRESTIGE_LEVEL = 20;
    public static final float PRESTIGE_MULTIPLIER_PER_LEVEL = 0.10f;
    public static final float SESSION_BONUS_PER_MINUTE = 0.05f;
    public static final float MAX_SESSION_BONUS = 0.50f;
    public static final float PERFECT_TRAINING_MULTIPLIER = 2.0f;
    public static final float PERFECT_BP_DIFFERENCE = 0.10f;
    public static final float PERFECT_RELEASE_MIN = 180.0f;
    public static final float PERFECT_RELEASE_MAX = 200.0f;
    public static final float MELEE_EFF = 1.00f;
    public static final float RELEASE_CONTROL_TP_PER_SEC = 4.0f;
    public static final float HIGH_RELEASE_THRESHOLD = 180.0f;
    public static final long PERFECT_ACTIONBAR_MS = 2500L;
    public static final float STREAK_BONUS_PER_DAY = 0.02f;
    public static final int MAX_STREAK_DAYS_FOR_BONUS = 14;
    public static final float MAX_STREAK_MULTIPLIER = 1.25f;
    public static final long MOMENTUM_DURATION_MS = 10_000L;
    public static final boolean SHOW_MOMENTUM_MESSAGES = true;
    /** Extra anti-spam on top of the 2s TP burst batch window. */
    private static final long TP_SUBTITLE_CD_MS = 1_500L;
    private static final String TP_SUBTITLE_CD_KEY = "lm.spar.tp.subtitle";
    /** Keep staff TP boss-bar text short enough to read mid-fight. */
    private static final int TP_SUBTITLE_MAX_CHARS = 64;

    private static final int[] MOMENTUM_THRESHOLDS = {5, 10, 15, 20, 30, 40};
    private static final float[] MOMENTUM_MULTIPLIERS = {1.05f, 1.10f, 1.20f, 1.35f, 1.50f, 2.00f};

    private static final double[] BP_ANCHORS = {
            1,
            100_000,
            1_000_000,
            10_000_000,
            100_000_000,
            1_000_000_000L,
            10_000_000_000L,
            100_000_000_000L,
            1_000_000_000_000L,
            10_000_000_000_000L,
            100_000_000_000_000L
    };
    private static final double[] BP_MULT_ANCHORS = {
            1.0, 2.0, 5.0, 12.0, 25.0, 50.0, 100.0, 150.0, 250.0, 400.0, 600.0
    };
    /** Token floor when a ki hit lands but DMZ fully mitigates HP loss (script KI_FULL_MIT_FLOOR). */
    public static final float KI_FULL_MIT_FLOOR = 12.0f;
    /**
     * {@code getBattlePower()} / Exact above this is an overflow (script ANDROID_FAKE_BP_THRESHOLD),
     * not a real power level. Anchors run through 100T, so this stays far above the curve.
     */
    private static final double ABSURD_BATTLE_POWER = 1.0e30;

    private SparCombat() {}

    public static float damageQuality(double damage) {
        if (!(damage > 0.0) || !(DAMAGE_QUALITY_REF > 0.0f)) {
            return MIN_DAMAGE_QUALITY;
        }
        double quality = Math.sqrt(damage / DAMAGE_QUALITY_REF);
        return (float) DmzRewards.clamp(quality, MIN_DAMAGE_QUALITY, MAX_DAMAGE_QUALITY);
    }

    public static float battlePowerMultiplier(double battlePower) {
        if (!(battlePower > 0.0)) {
            return 1.0f;
        }
        if (battlePower <= BP_ANCHORS[0]) {
            return (float) BP_MULT_ANCHORS[0];
        }
        int n = Math.min(BP_ANCHORS.length, BP_MULT_ANCHORS.length);
        for (int i = 0; i < n - 1; i++) {
            if (battlePower <= BP_ANCHORS[i + 1]) {
                double lowerLog = Math.log10(BP_ANCHORS[i]);
                double upperLog = Math.log10(BP_ANCHORS[i + 1]);
                double currentLog = Math.log10(battlePower);
                double progress = (currentLog - lowerLog) / Math.max(0.0001, upperLog - lowerLog);
                return (float) (BP_MULT_ANCHORS[i] + (BP_MULT_ANCHORS[i + 1] - BP_MULT_ANCHORS[i]) * progress);
            }
        }
        double finalBp = BP_ANCHORS[n - 1];
        double finalMult = BP_MULT_ANCHORS[n - 1];
        double extraDecades = (Math.log(battlePower) - Math.log(finalBp)) / Math.log(10.0);
        return (float) Math.min(MAX_BP_MULTIPLIER, finalMult + extraDecades * 200.0);
    }

    /**
     * Live DragonMineZ battle power for this player, read when TP is awarded.
     * Prefers {@code getBattlePowerExact()} so the float getter cannot flatten the curve.
     * Android upgrades and non-finite / overflow reads fall back to released combat stats.
     * This does not use the overhaul stat proxy, which ignores battle power once prestige scale is on.
     */
    public static double battlePower(ServerPlayer player) {
        if (player == null) {
            return 0.0;
        }
        try {
            if (AndroidConversion.isAndroidUpgraded(player)) {
                return CombatRating.releasedStatPower(player);
            }
            StatsData data = DmzProgression.stats(player);
            if (data != null) {
                double exact = data.getBattlePowerExact();
                if (usableBattlePower(exact)) {
                    return exact;
                }
                double raw = data.getBattlePower();
                if (usableBattlePower(raw)) {
                    return raw;
                }
            }
        } catch (Throwable ignored) {
        }
        return CombatRating.releasedStatPower(player);
    }

    private static boolean usableBattlePower(double value) {
        return Double.isFinite(value) && value > 0.0 && value < ABSURD_BATTLE_POWER;
    }

    /** This fighter's own live battle power. A missing read stays at the lowest tier. */
    private static double earnerBattlePower(TrainingValues earner) {
        if (earner == null || !usableBattlePower(earner.bp)) {
            return 0.0;
        }
        return earner.bp;
    }

    public static float rivalMultiplier(double bpA, double bpB) {
        double highest = Math.max(bpA, bpB);
        if (!(highest > 0.0)) {
            return 1.0f;
        }
        double ratio = Math.min(bpA, bpB) / highest;
        if (ratio >= 0.90) {
            return Math.min(3.0f, MAX_RIVAL_MULTIPLIER);
        }
        if (ratio >= 0.80) {
            return Math.min(2.5f, MAX_RIVAL_MULTIPLIER);
        }
        if (ratio >= 0.50) {
            return Math.min(2.0f, MAX_RIVAL_MULTIPLIER);
        }
        if (ratio >= 0.25) {
            return Math.min(1.5f, MAX_RIVAL_MULTIPLIER);
        }
        return 1.0f;
    }

    public static float releaseMultiplier(double releasePercent) {
        double release = DmzRewards.clamp(releasePercent, MIN_RELEASE_PERCENT, MAX_RELEASE_PERCENT);
        double progress = (release - MIN_RELEASE_PERCENT) / (MAX_RELEASE_PERCENT - MIN_RELEASE_PERCENT);
        return (float) DmzRewards.clamp(1.0 + progress * (MAX_RELEASE_MULTIPLIER - 1.0), 1.0, MAX_RELEASE_MULTIPLIER);
    }

    public static float gravityMultiplier(double gravity) {
        double g = DmzRewards.clamp(gravity, 1.0, MAX_GRAVITY);
        double progress = (g - 1.0) / (MAX_GRAVITY - 1.0);
        return (float) DmzRewards.clamp(1.0 + progress * (MAX_GRAVITY_MULTIPLIER - 1.0), 1.0, MAX_GRAVITY_MULTIPLIER);
    }

    public static float weightMultiplier(double weight) {
        double w = DmzRewards.clamp(weight, 0.0, MAX_EFFECTIVE_WEIGHT);
        double progress = w / MAX_EFFECTIVE_WEIGHT;
        return (float) DmzRewards.clamp(1.0 + progress * (MAX_WEIGHT_MULTIPLIER - 1.0), 1.0, MAX_WEIGHT_MULTIPLIER);
    }

    public static float prestigeMultiplier(int prestige) {
        int p = Math.max(0, Math.min(MAX_PRESTIGE_LEVEL, prestige));
        return 1.0f + p * PRESTIGE_MULTIPLIER_PER_LEVEL;
    }

    /**
     * Prestige for spar TP — the higher of the DMZ prestige skill and the LM held wallet.
     */
    public static int sparPrestigeLevel(ServerPlayer player) {
        int skill = Math.max(0, DmzProgression.prestige(player));
        int held = 0;
        try {
            held = com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeSystem
                    .getHeldWallet(player);
        } catch (Throwable ignored) {
            held = 0;
        }
        return Math.max(0, Math.min(MAX_PRESTIGE_LEVEL, Math.max(skill, held)));
    }

    public static float momentumMultiplier(SparPlayerRuntime rt) {
        if (rt == null) {
            return 1.0f;
        }
        long now = System.currentTimeMillis();
        // Script getMomentumMultiplier clears expired tier on read.
        if (rt.momentumUntil > 0L && now > rt.momentumUntil) {
            rt.momentumTier = 0;
            rt.momentumUntil = 0L;
        }
        if (rt.momentumTier <= 0) {
            return 1.0f;
        }
        int idx = Math.min(rt.momentumTier, MOMENTUM_MULTIPLIERS.length) - 1;
        if (idx < 0) {
            return 1.0f;
        }
        return MOMENTUM_MULTIPLIERS[idx];
    }

    public static float sessionBonusMultiplier(SparPlayerRuntime rt) {
        if (rt == null || rt.startAt <= 0L) {
            return 1.0f;
        }
        long minutes = Math.max(0L, (System.currentTimeMillis() - rt.startAt) / 60_000L);
        float bonus = Math.min(MAX_SESSION_BONUS, minutes * SESSION_BONUS_PER_MINUTE);
        return 1.0f + bonus;
    }

    public static float streakMultiplier(SparStore.MentorBond bond) {
        if (bond == null) {
            return 1.0f;
        }
        // Script getCurrentTrainingStreak: miss a day → treat as 0 until next qualify.
        long today = System.currentTimeMillis() / 86_400_000L;
        int current = bond.streakCurrent;
        if (bond.streakLastDay >= 0 && today - bond.streakLastDay > 1) {
            current = 0;
        }
        int days = Math.max(0, Math.min(MAX_STREAK_DAYS_FOR_BONUS, current));
        return Math.min(MAX_STREAK_MULTIPLIER, 1.0f + days * STREAK_BONUS_PER_DAY);
    }

    /** Matches live Sparring Tp System.js {@code STYLE_BONUS}. */
    public static float styleMultiplier(SparPlayerRuntime rt) {
        String id = styleId(rt);
        return switch (id) {
            case "melee" -> 1.08f;
            case "ki" -> 1.08f;
            case "beam" -> 1.10f;
            case "balanced" -> 1.12f;
            case "guardian" -> 1.06f;
            case "speed" -> 1.05f;
            default -> 1.0f;
        };
    }

    public static String styleDisplayName(String id) {
        return switch (id == null ? "none" : id) {
            case "melee" -> "Melee Specialist";
            case "ki" -> "Ki Specialist";
            case "beam" -> "Beam Specialist";
            case "balanced" -> "Balanced Fighter";
            case "guardian" -> "Guardian";
            case "speed" -> "Speed Fighter";
            default -> "Developing";
        };
    }

    public static String styleId(SparPlayerRuntime rt) {
        if (rt == null) {
            return "none";
        }
        double melee = rt.styleMelee;
        double ki = rt.styleKi;
        double beam = rt.styleBeam;
        double blocks = rt.styleBlock;
        double move = rt.styleMove;
        double total = melee + ki;
        if (total < 200.0) {
            return "none";
        }
        double meleeRatio = melee / Math.max(1.0, total);
        double kiRatio = ki / Math.max(1.0, total);
        double beamRatio = beam / Math.max(1.0, Math.max(ki, total));
        double blockRatio = blocks / Math.max(1.0, total + blocks);
        if (beamRatio >= 0.35 && kiRatio >= 0.50) {
            return "beam";
        }
        if (blockRatio >= 0.35) {
            return "guardian";
        }
        if (meleeRatio >= 0.80) {
            return "melee";
        }
        if (kiRatio >= 0.80) {
            return "ki";
        }
        if (meleeRatio >= 0.35 && kiRatio >= 0.35) {
            return "balanced";
        }
        if (move > total) {
            return "speed";
        }
        return "none";
    }

    public static float kiEfficiency(String kiKind) {
        if (kiKind == null) {
            return 0.95f;
        }
        return switch (kiKind.toLowerCase()) {
            case "basic" -> 1.00f;
            case "charge" -> 1.10f;
            case "scatter" -> 1.20f;
            case "beam" -> 1.30f;
            case "explosive" -> 0.75f;
            case "barrage" -> 0.90f;
            default -> 0.95f; // other
        };
    }

    /** Classify ki projectile / damage type from a damage source. */
    public static String classifyKiType(net.minecraft.world.damagesource.DamageSource source) {
        if (source == null) {
            return "other";
        }
        try {
            var direct = source.m_7640_();
            if (direct != null) {
                String cn = direct.getClass().getName().toLowerCase();
                if (cn.contains("laser") || cn.contains("beam") || cn.contains("kiwave")) {
                    return "beam";
                }
                if (cn.contains("blast")) {
                    return "basic";
                }
                if (cn.contains("disk")) {
                    return "scatter";
                }
                try {
                    var m = direct.getClass().getMethod("getKiType");
                    Object kt = m.invoke(direct);
                    if (kt != null) {
                        String k = String.valueOf(kt).toLowerCase();
                        if (k.contains("beam") || k.contains("wave")) {
                            return "beam";
                        }
                        if (k.contains("blast") || k.contains("ball")) {
                            return "basic";
                        }
                        if (k.contains("disk")) {
                            return "scatter";
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            String type = "";
            try {
                type = String.valueOf(source.m_269150_().m_203543_()
                        .map(k -> k.m_135782_().toString())
                        .orElse("")).toLowerCase();
            } catch (Throwable ignored) {
                try {
                    type = String.valueOf(source.m_19385_()).toLowerCase();
                } catch (Throwable ignored2) {
                    type = "";
                }
            }
            if (type.contains("scatter") || type.contains("disk")) {
                return "scatter";
            }
            if (type.contains("charge")) {
                return "charge";
            }
            if (type.contains("laser") || type.contains("beam") || type.contains("wave")) {
                return "beam";
            }
            if (type.contains("explosive")) {
                return "explosive";
            }
            if (type.contains("barrage") || type.contains("rapid")) {
                return "barrage";
            }
            if (type.contains("kiblast") || type.contains("blast")) {
                return "basic";
            }
        } catch (Throwable ignored) {
        }
        return "other";
    }

    public static TrainingValues liveValues(ServerPlayer player) {
        try {
            StatsData data = DmzProgression.stats(player);
            if (data == null) {
                return null;
            }
            TrainingValues v = new TrainingValues();
            v.bp = battlePower(player);
            v.release = DmzRewards.powerReleasePercent(player);
            try {
                v.gravity = GravityLogic.getNetGravity(player);
            } catch (Throwable ignored) {
                v.gravity = 1.0;
            }
            try {
                v.weight = GravityLogic.getEffectiveWeight(player);
            } catch (Throwable ignored) {
                v.weight = 0.0;
            }
            v.prestige = sparPrestigeLevel(player);
            return v;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isPerfect(TrainingValues a, TrainingValues b) {
        if (a == null || b == null) {
            return false;
        }
        if (percentDiff(a.bp, b.bp) > PERFECT_BP_DIFFERENCE) {
            return false;
        }
        if (a.release < PERFECT_RELEASE_MIN || a.release > PERFECT_RELEASE_MAX) {
            return false;
        }
        if (b.release < PERFECT_RELEASE_MIN || b.release > PERFECT_RELEASE_MAX) {
            return false;
        }
        if (Math.abs(a.gravity - b.gravity) > 0.01) {
            return false;
        }
        return !(Math.abs(a.weight - b.weight) > 1.0);
    }

    private static double percentDiff(double a, double b) {
        double max = Math.max(Math.abs(a), Math.abs(b));
        if (!(max > 0.0)) {
            return 0.0;
        }
        return Math.abs(a - b) / max;
    }

    public static float buildTotalMultiplier(
            ServerPlayer player,
            ServerPlayer partner,
            SparPlayerRuntime rt,
            TrainingValues a,
            TrainingValues b
    ) {
        // Own live battle power. Partner BP still feeds rival closeness and perfect training.
        double trainingBp = earnerBattlePower(a);
        double avgRelease = (a.release + b.release) / 2.0;
        double avgGravity = (a.gravity + b.gravity) / 2.0;
        double avgWeight = (a.weight + b.weight) / 2.0;
        boolean perfect = isPerfect(a, b);
        if (perfect && rt != null) {
            rt.sessionPerfect = true;
        }
        float bpMult = battlePowerMultiplier(trainingBp);
        // CNPC buildCombatMultiplier product — keep every factor.
        float total = bpMult
                * rivalMultiplier(a.bp, b.bp)
                * releaseMultiplier(avgRelease)
                * gravityMultiplier(avgGravity)
                * weightMultiplier(avgWeight)
                * prestigeMultiplier(a.prestige)
                * momentumMultiplier(rt)
                * sessionBonusMultiplier(rt)
                * streakMultiplier(SparStore.get().bond(player.m_20148_()))
                * styleMultiplier(rt)
                * (perfect ? PERFECT_TRAINING_MULTIPLIER : 1.0f);
        return total;
    }

    /** Compact active-bonus tags for staff TP chat. */
    public static String activeBonusTags(
            ServerPlayer player, SparPlayerRuntime rt, TrainingValues a, TrainingValues b
    ) {
        StringBuilder sb = new StringBuilder();
        if (rt != null && rt.combo > 1) {
            sb.append(" §8combo§f").append(rt.combo);
        }
        float mom = momentumMultiplier(rt);
        if (mom > 1.001f) {
            sb.append(" §emom§fx").append(formatMult(mom));
        }
        float streak = 1.0f;
        if (player != null) {
            streak = streakMultiplier(SparStore.get().bond(player.m_20148_()));
        }
        if (streak > 1.001f) {
            sb.append(" §astreak§fx").append(formatMult(streak));
        }
        int prestige = a != null ? a.prestige : (player == null ? 0 : sparPrestigeLevel(player));
        float prest = prestigeMultiplier(prestige);
        if (prest > 1.001f) {
            sb.append(" §dpres§fx").append(formatMult(prest));
        }
        float session = sessionBonusMultiplier(rt);
        if (session > 1.001f) {
            sb.append(" §bsess§fx").append(formatMult(session));
        }
        if (a != null && b != null && isPerfect(a, b)) {
            sb.append(" §6★");
        }
        return sb.toString();
    }

    /**
     * Staff-only multiplier stack tags (BP · rival · release · gravity · weight · style · ×total).
     * Player chat stays simple — see {@link #flushTpMessage}.
     */
    public static String staffStackTags(
            ServerPlayer player, SparPlayerRuntime rt, TrainingValues a, TrainingValues b
    ) {
        if (a == null || b == null) {
            return "";
        }
        double trainingBp = earnerBattlePower(a);
        double avgRelease = (a.release + b.release) / 2.0;
        double avgGravity = (a.gravity + b.gravity) / 2.0;
        double avgWeight = (a.weight + b.weight) / 2.0;
        float bp = battlePowerMultiplier(trainingBp);
        float rival = rivalMultiplier(a.bp, b.bp);
        float release = releaseMultiplier(avgRelease);
        float gravity = gravityMultiplier(avgGravity);
        float weight = weightMultiplier(avgWeight);
        float style = styleMultiplier(rt);
        float total = buildTotalMultiplier(player, null, rt, a, b) * GLOBAL_TP_GAIN_MULT;
        StringBuilder sb = new StringBuilder();
        sb.append(" §8bp§f").append(DmzRewards.formatWhole(trainingBp))
                .append("§8×§f").append(formatMult(bp));
        appendStaffMult(sb, "riv", rival);
        appendStaffMult(sb, "rel", release);
        appendStaffMult(sb, "grav", gravity);
        appendStaffMult(sb, "wt", weight);
        appendStaffMult(sb, "sty", style);
        sb.append(" §8×§f").append(formatMult(total));
        return sb.toString();
    }

    private static void appendStaffMult(StringBuilder sb, String key, float mult) {
        if (mult > 1.001f) {
            sb.append(" §8").append(key).append("§fx").append(formatMult(mult));
        }
    }

    private static String formatMult(float mult) {
        return String.format(java.util.Locale.ROOT, "%.2f", mult);
    }

    /**
     * Burst source label — matches CNPC script (prefer this burst's melee/ki/clash mix
     * over the long-session style name alone).
     */
    public static String burstLabel(
            SparPlayerRuntime rt, int pendingMelee, int pendingKi, int pendingClash
    ) {
        String id = styleId(rt);
        boolean pureClash = pendingClash > 0 && pendingMelee <= 0 && pendingKi <= 0;
        boolean pureKi = pendingKi > 0 && pendingMelee <= 0 && pendingClash <= 0;
        boolean pureMelee = pendingMelee > 0 && pendingKi <= 0 && pendingClash <= 0;
        if (pureClash) {
            return "beam".equals(id) ? styleDisplayName("beam") : "Beam Clash";
        }
        if (pureKi) {
            if ("ki".equals(id)) {
                return styleDisplayName("ki");
            }
            if ("beam".equals(id)) {
                return styleDisplayName("beam");
            }
            return "Ki";
        }
        if (pureMelee) {
            return "melee".equals(id) ? styleDisplayName("melee") : "Melee";
        }
        if (pendingKi > 0 && pendingMelee > 0) {
            if ("balanced".equals(id)) {
                return styleDisplayName("balanced");
            }
            if ("ki".equals(id) || "beam".equals(id)) {
                return styleDisplayName(id);
            }
            if ("melee".equals(id)) {
                return styleDisplayName("melee");
            }
            return "Mixed";
        }
        if (!"none".equals(id)) {
            return styleDisplayName(id);
        }
        return "Combat";
    }

    public static float maxTpForAction(float bpMult) {
        float scaled = (float) Math.floor(BASE_TP_PER_HIT * Math.max(1.0f, bpMult) * MAX_TP_PER_ACTION_BP_SCALE);
        if (!(scaled >= 1.0f)) {
            scaled = MAX_TP_PER_ACTION;
        }
        return Math.min(MAX_TP_PER_ACTION, Math.max(BASE_TP_PER_HIT, scaled));
    }

    public static int awardCombatTp(
            ServerPlayer player,
            ServerPlayer partner,
            SparPlayerRuntime rt,
            float baseAmount,
            String hitKind
    ) {
        if (player == null || partner == null || rt == null || !rt.active) {
            return 0;
        }
        if (RivalChallengeManager.get().isInChallenge(player.m_20148_())
                || RivalChallengeManager.get().isInChallenge(partner.m_20148_())) {
            return 0;
        }
        float base = Math.min(MAX_BASE_TP_PER_HIT, Math.max(0.0f, baseAmount));
        if (!(base > 0.0f)) {
            return 0;
        }
        TrainingValues a = liveValues(player);
        TrainingValues b = liveValues(partner);
        if (a == null || b == null) {
            return 0;
        }
        float totalMult = buildTotalMultiplier(player, partner, rt, a, b);
        float amount = (float) Math.floor(base * totalMult * GLOBAL_TP_GAIN_MULT);
        boolean withMentor = SparringSystem.isSparringWithOwnMentor(player, partner);
        boolean withDojoPeer = !withMentor && SparringSystem.isSparringWithDojoPeer(player, partner);
        float bondBonus = withMentor ? MENTOR_SPAR_BONUS_PCT
                : (withDojoPeer ? DOJO_PEER_SPAR_BONUS_PCT : 0.0f);
        if (bondBonus > 0.0f) {
            amount = (float) Math.floor(amount * (1.0f + bondBonus));
        }
        float bpMult = battlePowerMultiplier(earnerBattlePower(a));
        // The hit cap used to ignore the closeness bonus, so a full rival
        // multiplier never increased TP once other factors already filled it.
        float rival = rivalMultiplier(a.bp, b.bp);
        float actionCap = maxTpForAction(bpMult) * GLOBAL_TP_GAIN_MULT * Math.max(1.0f, rival);
        if (bondBonus > 0.0f) {
            actionCap *= (1.0f + bondBonus);
        }
        if (amount > actionCap) {
            amount = actionCap;
        }
        int award = Math.max(0, Math.round(amount));
        if (award <= 0) {
            return 0;
        }
        if (!DmzRewards.awardTp(player, award, null, false, null)) {
            return 0;
        }
        rt.sessionTp += award;
        logBattlePowerAward(player, partner, a.bp, bpMult, award);
        queueTpMessage(player, rt, award, hitKind, a, b);
        SparringSystem.shareTpWithMentor(player, award);
        return award;
    }

    public static void awardDamageTp(
            ServerPlayer attacker,
            ServerPlayer victim,
            SparPlayerRuntime atkRt,
            double damage,
            boolean ki,
            String kiKind
    ) {
        if (atkRt == null || !atkRt.active) {
            return;
        }
        if (!(damage > 0.0)) {
            return;
        }
        atkRt.sessionDmg += damage;
        if (ki) {
            atkRt.sessionKi += damage;
            atkRt.styleKi += damage;
            if ("beam".equalsIgnoreCase(kiKind)) {
                atkRt.styleBeam += damage;
            }
        } else {
            atkRt.sessionMelee += damage;
            atkRt.styleMelee += damage;
        }
        if (isBlocking(victim)) {
            SparringSystem.breakCombo(attacker, atkRt, "attack blocked");
            return;
        }
        SparringSystem.registerCombatHit(attacker, atkRt);
        long now = System.currentTimeMillis();
        if (now <= atkRt.heavyMotionUntil) {
            atkRt.sessionKb++;
            awardCombatTp(attacker, victim, atkRt, KNOCKBACK_RECOVERY_TP, "melee");
            atkRt.heavyMotionUntil = 0L;
        }
        float eff = ki ? kiEfficiency(kiKind) : MELEE_EFF;
        float base = BASE_TP_PER_HIT * damageQuality(damage) * eff;
        awardCombatTp(attacker, victim, atkRt, base, ki ? "ki" : "melee");
    }

    public static boolean isBlocking(ServerPlayer player) {
        try {
            StatsData data = DmzProgression.stats(player);
            Status status = data == null ? null : data.getStatus();
            return status != null && status.isBlocking();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void queueTpMessage(
            ServerPlayer player, SparPlayerRuntime rt, int amount, String hitKind
    ) {
        queueTpMessage(player, rt, amount, hitKind, null, null);
    }

    public static void queueTpMessage(
            ServerPlayer player,
            SparPlayerRuntime rt,
            int amount,
            String hitKind,
            TrainingValues a,
            TrainingValues b
    ) {
        if (rt == null || amount <= 0) {
            return;
        }
        rt.tpPending += amount;
        String kind = hitKind == null ? "" : hitKind.toLowerCase();
        if ("melee".equals(kind)) {
            rt.tpPendingMelee += amount;
        } else if ("beam".equals(kind) || "clash".equals(kind) || "beam-clash".equals(kind)) {
            rt.tpPendingClash += amount;
        } else if (!kind.isEmpty()) {
            rt.tpPendingKi += amount;
        }
        long now = System.currentTimeMillis();
        if (now >= rt.tpMsgNext) {
            rt.tpMsgNext = now + 2000L;
            flushTpMessage(player, rt);
        }
    }

    /** Flush batched TP chat when the throttle window elapses (even mid-fight with no new hit). */
    public static void tickTpMessages(ServerPlayer player, SparPlayerRuntime rt, long now) {
        if (rt == null || !rt.active || rt.tpPending <= 0) {
            return;
        }
        if (now < rt.tpMsgNext) {
            return;
        }
        rt.tpMsgNext = now + 2000L;
        flushTpMessage(player, rt);
    }

    public static void flushTpMessage(ServerPlayer player, SparPlayerRuntime rt) {
        if (rt == null || rt.tpPending <= 0) {
            return;
        }
        int pending = (int) Math.floor(rt.tpPending);
        int pendingMelee = (int) Math.floor(rt.tpPendingMelee);
        int pendingKi = (int) Math.floor(rt.tpPendingKi);
        int pendingClash = (int) Math.floor(rt.tpPendingClash);
        rt.tpPending = 0;
        rt.tpPendingMelee = 0;
        rt.tpPendingKi = 0;
        rt.tpPendingClash = 0;
        if (player == null || !SparStore.get().tpMessagesOn(player.m_20148_())) {
            return;
        }
        String label = burstLabel(rt, pendingMelee, pendingKi, pendingClash);
        // Players: clean +TP (label). Staff: short mom/combo + m/k/c + session.
        String line;
        if (!StaffAccess.isStaff(player)) {
            line = "+" + DmzRewards.formatWhole(pending) + " TP (" + label + ")";
        } else {
            TrainingValues a = liveValues(player);
            TrainingValues b = null;
            if (rt.partner != null && player.m_20194_() != null) {
                ServerPlayer partner = player.m_20194_().m_6846_().m_11259_(rt.partner);
                if (partner != null) {
                    b = liveValues(partner);
                }
            }
            line = staffTpSubtitle(rt, pending, label, pendingMelee, pendingKi, pendingClash, a, b);
        }
        TpBossNotify.show(player, line, ChatFormatting.GREEN, TP_SUBTITLE_CD_KEY, TP_SUBTITLE_CD_MS);
    }

    /** Compact staff TP line — full stack tags are too long for the boss bar. */
    private static String staffTpSubtitle(
            SparPlayerRuntime rt,
            int pending,
            String label,
            int pendingMelee,
            int pendingKi,
            int pendingClash,
            TrainingValues a,
            TrainingValues b
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append('+').append(DmzRewards.formatWhole(pending)).append(" TP (").append(label).append(')');
        if (rt != null && rt.combo > 1) {
            sb.append(" c").append(rt.combo);
        }
        float mom = momentumMultiplier(rt);
        if (mom > 1.001f) {
            sb.append(" momx").append(formatMult(mom));
        }
        if (pendingMelee > 0 || pendingKi > 0 || pendingClash > 0) {
            sb.append(' ')
                    .append(pendingMelee)
                    .append('/')
                    .append(pendingKi)
                    .append('/')
                    .append(pendingClash);
        }
        sb.append(" sess ").append(DmzRewards.formatWhole(rt == null ? 0 : rt.sessionTp));
        // Keep live values referenced so staff still computes parity in debug paths.
        if (a != null && b != null && isPerfect(a, b)) {
            sb.append(" *");
        }
        String out = sb.toString();
        if (out.length() <= TP_SUBTITLE_MAX_CHARS) {
            return out;
        }
        return out.substring(0, TP_SUBTITLE_MAX_CHARS - 3) + "...";
    }

    public static void updateMomentum(ServerPlayer player, SparPlayerRuntime rt) {
        if (rt == null) {
            return;
        }
        int oldTier = rt.momentumTier;
        // Script refreshes momentum window on every scored hit, not only tier-ups.
        rt.momentumUntil = System.currentTimeMillis() + MOMENTUM_DURATION_MS;
        int tier = 0;
        for (int i = 0; i < MOMENTUM_THRESHOLDS.length; i++) {
            if (rt.combo >= MOMENTUM_THRESHOLDS[i]) {
                tier = i + 1;
            }
        }
        rt.momentumTier = tier;
        rt.sessionMaxMom = Math.max(rt.sessionMaxMom, tier);
        if (SHOW_MOMENTUM_MESSAGES
                && player != null
                && tier > oldTier
                && tier > 0
                && SparStore.get().tpMessagesOn(player.m_20148_())) {
            float mult = MOMENTUM_MULTIPLIERS[Math.min(tier, MOMENTUM_MULTIPLIERS.length) - 1];
            TpBossNotify.show(
                    player,
                    "Momentum " + tier + " (x" + formatMult(mult) + " TP)",
                    ChatFormatting.YELLOW,
                    "lm.spar.momentum.subtitle",
                    1_000L);
        }
    }

    /** @deprecated use {@link #updateMomentum(ServerPlayer, SparPlayerRuntime)} */
    public static void updateMomentum(SparPlayerRuntime rt) {
        updateMomentum(null, rt);
    }

    /**
     * Printed when TP is awarded. Server log is debug-level. The system event log
     * ({@code enableSystemTelemetry}) records the same numbers for in-game checks.
     */
    private static void logBattlePowerAward(
            ServerPlayer player, ServerPlayer partner, double battlePower, float battlePowerMultiplier, int award
    ) {
        AdaptiveDifficultyMod.LOGGER.debug(
                "[{}] spar TP {} battlePower={} battlePowerMultiplier={} award={}",
                AdaptiveDifficultyMod.MOD_ID,
                player.m_7755_().getString(),
                battlePower,
                battlePowerMultiplier,
                award);
        SystemTelemetry.log(
                "sparring",
                "spar_tp",
                player,
                partner,
                SystemTelemetry.fields(
                        "battlePower", battlePower,
                        "battlePowerMultiplier", battlePowerMultiplier,
                        "award", award));
    }

    public static final class TrainingValues {
        /** Set by {@link #liveValues} from {@link #battlePower(ServerPlayer)} at award time. */
        public double bp;
        public double release;
        public double gravity;
        public double weight;
        public int prestige;
    }
}
