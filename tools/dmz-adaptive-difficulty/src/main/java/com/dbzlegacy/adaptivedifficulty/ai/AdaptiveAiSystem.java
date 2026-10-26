package com.dbzlegacy.adaptivedifficulty.ai;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.elite.EliteSystem;
import com.dbzlegacy.adaptivedifficulty.scaling.HostileMobs;
import com.dbzlegacy.adaptivedifficulty.scaling.MobScaling;
import com.dbzlegacy.adaptivedifficulty.tick.CombatIndex;
import com.dbzlegacy.adaptivedifficulty.tier.DifficultyTier;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockAbilityCaps;
import com.dbzlegacy.adaptivedifficulty.util.DimensionGates;
import com.dbzlegacy.adaptivedifficulty.util.NearbyPlayers;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.skills.Skills;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Progressive AI spaced across the full Awakened → Zenith ladder.
 * Early tiers teach pressure; late tiers escalate pack tactics, anti-flight,
 * debuffs, and dodge — so multi-million difficulty keeps unlocking new threat.
 * <p>
 * Pack / ki reactions use {@link CombatIndex} only — never world AABB scans.
 */
public final class AdaptiveAiSystem {
    /** One ki-charge pack reaction per player every 40 ticks. */
    private static final Map<UUID, Long> KI_CHARGE_COOLDOWN = new ConcurrentHashMap<>();
    /** One pack-call per target player every 80 ticks. */
    private static final Map<UUID, Long> PACK_COOLDOWN = new ConcurrentHashMap<>();
    /** Per-player, per-effect pressure gap so a pack does not refresh the same debuff together. */
    private static final Map<String, Long> PRESSURE_COOLDOWN = new ConcurrentHashMap<>();

    private AdaptiveAiSystem() {}

    /**
     * Counter ki charging — starts at Master; intensity grows through high tiers.
     * Throttled + combat-index peers only (no {@code getEntitiesOfClass}).
     */
    public static void onPlayerKiCharge(ServerPlayer player) {
        DifficultyConfig cfg = DifficultyConfig.get();
        if (!cfg.enableAdaptiveAi || player == null || !(player.m_9236_() instanceof ServerLevel level)
                || DimensionGates.isDisabled(player)) {
            return;
        }
        long now = level.m_46467_();
        UUID playerId = player.m_20148_();
        Long last = KI_CHARGE_COOLDOWN.get(playerId);
        if (last != null && now - last < 40L) {
            return; // still cooling down — do not refresh the timer
        }
        AtomicInteger reacted = new AtomicInteger();
        CombatIndex.forEachNearPlayer(player, 12.0, 8, mob -> {
            if (reacted.get() >= 6) {
                return;
            }
            if (!PersistentDataAccess.flag(mob, MobScaling.TAG_SCALED)) {
                return;
            }
            DifficultyTier tier = resolveTier(mob, MobScaling.difficultyOf(mob));
            if (tier.ordinalPower() < DifficultyTier.MASTER.ordinalPower()) {
                return;
            }
            mob.m_6710_(player);
            double speed = 1.25 + Math.min(0.75, (tier.ordinalPower() - DifficultyTier.MASTER.ordinalPower()) * 0.06);
            mob.m_21573_().m_5624_(player, speed);
            reacted.incrementAndGet();

            if (tier.ordinalPower() >= DifficultyTier.GOD.ordinalPower()) {
                player.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 40, 0, false, true)); // SLOWNESS
            }
            if (tier.ordinalPower() >= DifficultyTier.DIVINE.ordinalPower()) {
                player.m_7292_(new MobEffectInstance(MobEffects.f_19613_, 40, 0, false, true)); // WEAKNESS
            }
            if (tier.ordinalPower() >= DifficultyTier.OMEGA.ordinalPower()) {
                mob.m_7292_(new MobEffectInstance(MobEffects.f_19600_, 60, 1, false, false)); // STRENGTH
            }
        });
        // Only arm cooldown after a successful pack reaction.
        if (reacted.get() > 0) {
            KI_CHARGE_COOLDOWN.put(playerId, now);
        }
        if (KI_CHARGE_COOLDOWN.size() > 256) {
            KI_CHARGE_COOLDOWN.clear();
        }
    }

    public static void tick(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) {
            return;
        }
        tick(mob, MobScaling.difficultyOf(entity), EliteSystem.isElite(entity));
    }

    public static void tick(Mob mob, long difficulty, boolean elite) {
        DifficultyConfig cfg = DifficultyConfig.get();
        if (!cfg.enableAdaptiveAi || mob == null || !mob.m_6084_() || DimensionGates.isDisabled(mob)) {
            return;
        }
        if (!(mob.m_9236_() instanceof ServerLevel level)) {
            return;
        }
        if (difficulty <= 0 && !elite) {
            return;
        }
        DifficultyTier tier = resolveTier(mob, difficulty);
        if (elite && tier.ordinalPower() < DifficultyTier.ELITE.ordinalPower()) {
            tier = DifficultyTier.ELITE;
        }
        if (tier.ordinalPower() < DifficultyTier.AWAKENED.ordinalPower()) {
            return;
        }

        // Never let scaled AI chase other hostiles (pack/ki mistakes → civil war).
        clearHostileTarget(mob);

        LivingEntity target = mob.m_5448_();
        double scan = Math.min(aiRadius(tier), cfg.mobScaleRadius);

        // Player-list focus (no AABB) when we don't already have a living player target.
        if (!(target instanceof Player) || !target.m_6084_()) {
            if (tier.ordinalPower() >= DifficultyTier.ENHANCED.ordinalPower()) {
                focusWeakest(mob, scan);
            } else {
                focusNearest(mob, Math.min(20.0, scan));
            }
            target = mob.m_5448_();
        }

        // Elite+: tactical retreat when nearly dead
        if (target != null && tier.ordinalPower() >= DifficultyTier.ELITE.ordinalPower()) {
            maybeRetreat(mob, target, tier);
        }

        // Enhanced+: light speed pressure while chasing (was Advanced+ — melee closed gaps too late)
        if (target != null && tier.ordinalPower() >= DifficultyTier.ENHANCED.ordinalPower()) {
            int amp = tier.ordinalPower() >= DifficultyTier.MYTHIC.ordinalPower() ? 2
                    : tier.ordinalPower() >= DifficultyTier.ADVANCED.ordinalPower() ? 1 : 0;
            mob.m_7292_(new MobEffectInstance(MobEffects.f_19596_, 40, amp, false, false)); // SPEED
            // Sidestep while closing so the chase is not a straight line into the player's ki.
            if (!(mob instanceof Ghast)) {
                double gap = mob.m_20270_(target);
                if (gap > 3.4 && gap < 13.0) {
                    strafe(mob, target);
                }
            }
        }

        // Master+: rare pack call via combat-index peers (no world scan)
        if (tier.ordinalPower() >= DifficultyTier.MASTER.ordinalPower()) {
            int pack = packSize(tier);
            if (pack > 0) {
                coordinate(mob, level, Math.min(12.0, scan), pack);
            }
        }

        // Legendary+: anti-flight (moved later so early tiers stay grounded-melee)
        if (target instanceof Player player && tier.ordinalPower() >= DifficultyTier.LEGENDARY.ordinalPower()) {
            antiFlight(mob, player, tier);
        }

        // God+: keep aggression locked on players + jump boost for vertical chase
        if (target instanceof Player && tier.ordinalPower() >= DifficultyTier.GOD.ordinalPower()) {
            mob.m_6710_(target);
            mob.m_7292_(new MobEffectInstance(MobEffects.f_19603_, 40, 1, false, false)); // JUMP_BOOST
        }

        long now = level.m_46467_();

        // Impossible+: one mob owns the slowness pulse so a pack does not refresh it together.
        if (target instanceof ServerPlayer sp
                && tier.ordinalPower() >= DifficultyTier.IMPOSSIBLE.ordinalPower()
                && mob.m_20270_(sp) < 10.0f
                && claimPressure(sp, "slow", now, 55L)) {
            sp.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 50, 1, false, true));
        }

        // Transcendent+: mining fatigue / hunger pressure in melee range
        if (target instanceof ServerPlayer sp
                && tier.ordinalPower() >= DifficultyTier.TRANSCENDENT.ordinalPower()
                && mob.m_20270_(sp) < 6.0f
                && claimPressure(sp, "fatigue", now, 80L)) {
            sp.m_7292_(new MobEffectInstance(MobEffects.f_19599_, 70, 0, false, true)); // MINING_FATIGUE
            if (tier.ordinalPower() >= DifficultyTier.ETERNAL.ordinalPower()) {
                sp.m_7292_(new MobEffectInstance(MobEffects.f_19605_, 70, 1, false, true)); // HUNGER
            }
        }

        // Mythic+: strength self-buff
        if (tier.ordinalPower() >= DifficultyTier.MYTHIC.ordinalPower()) {
            int amp = tier.ordinalPower() >= DifficultyTier.APEX.ordinalPower() ? 2 : 1;
            mob.m_7292_(new MobEffectInstance(MobEffects.f_19600_, 50, amp, false, false));
        }

        // Omega+: rare frenzy — buff nearby scaled allies
        if (tier.ordinalPower() >= DifficultyTier.OMEGA.ordinalPower() && mob.f_19797_ % 200 == 0) {
            frenzyAllies(mob, level, Math.min(10.0, scan), packSize(tier));
        }

        // Absolute+: wither touch while in melee. One mob owns it.
        if (target instanceof ServerPlayer sp
                && tier.ordinalPower() >= DifficultyTier.ABSOLUTE.ordinalPower()
                && mob.m_20270_(sp) < 3.5f
                && claimPressure(sp, "wither", now, 45L)) {
            sp.m_7292_(new MobEffectInstance(MobEffects.f_19615_, 50, 0, false, true)); // WITHER
        }
    }

    public static void onHurt(LivingHurtEvent event) {
        DifficultyConfig cfg = DifficultyConfig.get();
        if (!cfg.enableAdaptiveAi || event.isCanceled()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Mob mob) || entity.m_9236_().f_46443_) {
            return;
        }
        if (!HostileMobs.isHostile(mob) || !PersistentDataAccess.flag(mob, MobScaling.TAG_SCALED)) {
            return;
        }
        long difficulty = MobScaling.difficultyOf(entity);
        if (difficulty <= 0L && !EliteSystem.isElite(entity)) {
            return;
        }
        DifficultyTier tier = resolveTier(entity, difficulty);
        // Dodge starts at Advanced, stays modest until high tiers
        if (tier.ordinalPower() < DifficultyTier.ADVANCED.ordinalPower()) {
            return;
        }
        double dodge = dodgeChance(tier);
        if (EliteSystem.isElite(entity)) {
            dodge += 0.03;
        }
        if (ThreadLocalRandom.current().nextDouble() < dodge) {
            event.setAmount(0.0f);
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            double force = tier.ordinalPower() >= DifficultyTier.MYTHIC.ordinalPower() ? 0.95 : 0.6;
            entity.m_5997_(Math.cos(angle) * force, 0.15, Math.sin(angle) * force);
        }

        // Divine+: retaliate with brief weakness on the attacker when hit
        if (tier.ordinalPower() >= DifficultyTier.DIVINE.ordinalPower()
                && event.getSource().m_7639_() instanceof ServerPlayer attacker
                && ThreadLocalRandom.current().nextDouble() < chance(tier, 0.15, 0.45)) {
            attacker.m_7292_(new MobEffectInstance(MobEffects.f_19613_, 40, 0, false, true));
        }
    }

    static DifficultyTier resolveTier(LivingEntity entity, long difficulty) {
        int unlockTier = 0;
        if (entity != null) {
            CompoundTag tag = PersistentDataAccess.get(entity);
            if (tag.m_128441_("dmz_ad_unlock_tier")) {
                unlockTier = tag.m_128451_("dmz_ad_unlock_tier");
            }
        }
        DifficultyTier tier = UnlockAbilityCaps.resolve(difficulty, unlockTier);
        if (EliteSystem.isElite(entity) && tier.ordinalPower() < DifficultyTier.ELITE.ordinalPower()
                && unlockTier >= 3) {
            // Elites only floor to Elite kit when the unlock band allows it.
            tier = UnlockAbilityCaps.clamp(DifficultyTier.ELITE, unlockTier);
        }
        return tier;
    }

    private static double aiRadius(DifficultyTier tier) {
        return 16.0 + Math.min(16.0, Math.max(0, tier.ordinalPower() - DifficultyTier.ENHANCED.ordinalPower()) * 1.2);
    }

    /**
     * Allies one pack call (or frenzy) may pull in. High tiers fight as duels, so the curve goes
     * down: capped at {@code adaptiveAiPackCallMaxAllies} (default 4) from Master up, and 0 from
     * {@code adaptiveAiPackCallOffTier} (default Mythic) up.
     */
    static int packSize(DifficultyTier tier) {
        DifficultyConfig cfg = DifficultyConfig.get();
        DifficultyTier off = DifficultyTier.parse(cfg.adaptiveAiPackCallOffTier, DifficultyTier.MYTHIC);
        if (tier.ordinalPower() >= off.ordinalPower()) {
            return 0;
        }
        return Math.min(Math.max(0, cfg.adaptiveAiPackCallMaxAllies), basePackSize(tier));
    }

    private static int basePackSize(DifficultyTier tier) {
        int base = 2;
        if (tier.ordinalPower() >= DifficultyTier.GOD.ordinalPower()) {
            base = 4;
        }
        if (tier.ordinalPower() >= DifficultyTier.IMPOSSIBLE.ordinalPower()) {
            base = 6;
        }
        if (tier.ordinalPower() >= DifficultyTier.OMEGA.ordinalPower()) {
            base = 8;
        }
        if (tier.ordinalPower() >= DifficultyTier.ZENITH.ordinalPower()) {
            base = 10;
        }
        return base;
    }

    /** Advanced ~3% → Zenith ~22% (keeps mid-tier fights readable). */
    private static double dodgeChance(DifficultyTier tier) {
        int steps = Math.max(0, tier.ordinalPower() - DifficultyTier.ADVANCED.ordinalPower());
        return Math.min(0.22, 0.03 + steps * 0.012);
    }

    private static double chance(DifficultyTier tier, double min, double max) {
        double t = Math.max(0, tier.ordinalPower() - DifficultyTier.LEGENDARY.ordinalPower()) / 12.0;
        return min + (max - min) * Math.min(1.0, t);
    }

    private static void focusWeakest(Mob mob, double radius) {
        ServerPlayer weakest = NearbyPlayers.weakestParticipating(mob, Math.max(8.0, radius));
        if (weakest != null) {
            LivingEntity current = mob.m_5448_();
            if (current == null || current.m_21223_() > weakest.m_21223_() + 4.0f) {
                mob.m_6710_(weakest);
            }
        }
    }

    /** Awakened fallback — lock the nearest participating player so kits do not idle. */
    private static void focusNearest(Mob mob, double radius) {
        ServerPlayer nearest = NearbyPlayers.nearestParticipating(mob, Math.max(8.0, radius));
        if (nearest != null) {
            mob.m_6710_(nearest);
        }
    }

    private static void antiFlight(Mob mob, Player player, DifficultyTier tier) {
        boolean grounded = false;

        if (player.m_150110_().f_35937_) { // instabuild
            return;
        }

        if (player.m_150110_().f_35935_) {
            player.m_150110_().f_35935_ = false;
            grounded = true;
        }

        try {
            StatsData stats = DmzProgression.stats(player);
            Skills skills = stats == null ? null : stats.getSkills();
            if (skills != null && skills.isSkillActive("fly")) {
                skills.setSkillActive("fly", false);
                grounded = true;
                if (player instanceof ServerPlayer sp) {
                    try {
                        NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(sp), sp);
                    } catch (Throwable ignored) {
                    }
                }
            }
            MobEffect flyFx = MainEffects.FLY.get();
            if (flyFx != null && player.m_21023_(flyFx)) {
                player.m_21195_(flyFx);
                grounded = true;
            }
        } catch (Throwable ignored) {
        }

        double airGap = player.m_20186_() - mob.m_20186_();
        double gapNeed = tier.ordinalPower() >= DifficultyTier.APEX.ordinalPower() ? 1.5 : 2.5;
        if (!grounded && !player.m_20096_() && airGap > gapNeed) {
            grounded = true;
        }

        if (!grounded) {
            return;
        }
        double slam = tier.ordinalPower() >= DifficultyTier.ZENITH.ordinalPower() ? -1.35
                : tier.ordinalPower() >= DifficultyTier.OMEGA.ordinalPower() ? -1.15 : -0.85;
        player.m_20256_(new Vec3(0.0, slam, 0.0));
        double dx = player.m_20185_() - mob.m_20185_();
        double dz = player.m_20189_() - mob.m_20189_();
        double leap = tier.ordinalPower() >= DifficultyTier.MYTHIC.ordinalPower() ? 0.65 : 0.45;
        mob.m_5997_(dx * 0.08, leap, dz * 0.08);
    }

    private static void maybeRetreat(Mob mob, LivingEntity target, DifficultyTier tier) {
        float pct = mob.m_21223_() / Math.max(1.0f, mob.m_21233_());
        float trigger = tier.ordinalPower() >= DifficultyTier.GOD.ordinalPower() ? 0.35f : 0.25f;
        if (pct > trigger) {
            return;
        }
        // High tiers prefer fighting through — rarer retreats
        if (tier.ordinalPower() >= DifficultyTier.TRANSCENDENT.ordinalPower()
                && ThreadLocalRandom.current().nextDouble() < 0.65) {
            return;
        }
        CompoundTag tag = PersistentDataAccess.get(mob);
        long cd = tier.ordinalPower() >= DifficultyTier.DIVINE.ordinalPower() ? 140 : 100;
        long last = tag.m_128454_("dmz_ad_retreat");
        if (mob.f_19797_ - last < cd) {
            return;
        }
        tag.m_128356_("dmz_ad_retreat", mob.f_19797_);
        double dx = mob.m_20185_() - target.m_20185_();
        double dz = mob.m_20189_() - target.m_20189_();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) {
            return;
        }
        mob.m_5997_((dx / len) * 0.9, 0.2, (dz / len) * 0.9);
        // Step back, then path in again so the retreat is a beat and not a wander-off.
        double reengage = tier.ordinalPower() >= DifficultyTier.GOD.ordinalPower() ? 1.2 : 1.05;
        mob.m_21573_().m_5624_(target, reengage);
    }

    /** One nearby mob applies this debuff. The rest wait out the gap. */
    private static boolean claimPressure(ServerPlayer player, String kind, long now, long gapTicks) {
        if (player == null || kind == null) {
            return false;
        }
        String key = player.m_20148_() + ":" + kind;
        Long last = PRESSURE_COOLDOWN.get(key);
        if (last != null && now - last < gapTicks) {
            return false;
        }
        PRESSURE_COOLDOWN.put(key, now);
        if (PRESSURE_COOLDOWN.size() > 256) {
            PRESSURE_COOLDOWN.clear();
        }
        return true;
    }

    private static void strafe(Mob mob, LivingEntity target) {
        double dx = target.m_20185_() - mob.m_20185_();
        double dz = target.m_20189_() - mob.m_20189_();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) {
            return;
        }
        double side = ((mob.f_19797_ / 40) % 2 == 0) ? 1.0 : -1.0;
        double speed = 0.28;
        mob.m_5997_((-dz / len) * speed * side, 0.0, (dx / len) * speed * side);
    }

    private static void coordinate(Mob mob, ServerLevel level, double radius, int maxAllies) {
        LivingEntity target = mob.m_5448_();
        // Pack call is player-only — never point allies at other mobs.
        if (!(target instanceof Player player)) {
            return;
        }
        long now = level.m_46467_();
        Long last = PACK_COOLDOWN.get(player.m_20148_());
        if (last != null && now - last < 80L) {
            return;
        }
        PACK_COOLDOWN.put(player.m_20148_(), now);
        if (PACK_COOLDOWN.size() > 256) {
            PACK_COOLDOWN.clear();
        }
        AtomicInteger shared = new AtomicInteger();
        CombatIndex.forEachNear(mob, Math.min(12.0, radius), maxAllies + 4, ally -> {
            if (shared.get() >= maxAllies) {
                return;
            }
            clearHostileTarget(ally);
            if (ally.m_5448_() != null) {
                return;
            }
            CompoundTag allyTag = PersistentDataAccess.get(ally);
            if (!allyTag.m_128471_(MobScaling.TAG_SCALED) && !allyTag.m_128471_(EliteSystem.TAG_ELITE)) {
                return;
            }
            ally.m_6710_(target);
            CombatIndex.mark(ally, now);
            shared.incrementAndGet();
        });
    }

    /** Drop targets + revenge that point at other hostiles so packs don't civil-war. */
    private static void clearHostileTarget(Mob mob) {
        HostileMobs.clearCivilWarAggro(mob);
    }

    private static void frenzyAllies(Mob mob, ServerLevel level, double radius, int maxAllies) {
        AtomicInteger buffed = new AtomicInteger();
        // Self-buff always.
        mob.m_7292_(new MobEffectInstance(MobEffects.f_19596_, 80, 1, false, false));
        mob.m_7292_(new MobEffectInstance(MobEffects.f_19600_, 80, 0, false, false));
        buffed.incrementAndGet();
        if (maxAllies <= 0) {
            return; // duel tiers: the frenzy buffs only this mob
        }
        CombatIndex.forEachNear(mob, Math.min(10.0, radius), maxAllies + 4, ally -> {
            if (buffed.get() >= 1 + maxAllies) {
                return;
            }
            if (!PersistentDataAccess.flag(ally, MobScaling.TAG_SCALED)) {
                return;
            }
            ally.m_7292_(new MobEffectInstance(MobEffects.f_19596_, 80, 1, false, false));
            ally.m_7292_(new MobEffectInstance(MobEffects.f_19600_, 80, 0, false, false));
            buffed.incrementAndGet();
        });
    }
}
