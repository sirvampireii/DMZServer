package com.dbzlegacy.adaptivedifficulty.evolution;

import com.dbzlegacy.adaptivedifficulty.boss.BossScaling;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.elite.EliteSystem;
import com.dbzlegacy.adaptivedifficulty.mutation.MutationSystem;
import com.dbzlegacy.adaptivedifficulty.scaling.HostileMobs;
import com.dbzlegacy.adaptivedifficulty.scaling.MobScaling;
import com.dbzlegacy.adaptivedifficulty.tick.CombatIndex;
import com.dbzlegacy.adaptivedifficulty.tier.DifficultyTier;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockAbilityCaps;
import com.dbzlegacy.adaptivedifficulty.util.DimensionGates;
import com.dbzlegacy.adaptivedifficulty.util.NearbyPlayers;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import java.lang.reflect.Field;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Per-mob evolution kits unlocked by difficulty tiers.
 * Each behavior pulse commits to one action chosen by range, so dash, leap,
 * slam, and ki do not dump on the same tick.
 * Endermen / Wardens apply stacked DMZ gravity-chamber pressure to aggro'd players.
 */
public final class EnemyEvolution {
    public static final String TAG_EVOLVED = "dmz_ad_evolved";

    private static final Field CREEPER_RADIUS;
    private static final Field CREEPER_SWELL;

    static {
        Field radius = null;
        Field swell = null;
        try {
            radius = Creeper.class.getDeclaredField("f_32272_"); // explosionRadius
            radius.setAccessible(true);
            swell = Creeper.class.getDeclaredField("f_32271_"); // maxSwell
            swell.setAccessible(true);
        } catch (Throwable ignored) {
        }
        CREEPER_RADIUS = radius;
        CREEPER_SWELL = swell;
    }

    private EnemyEvolution() {}

    public static boolean isEvolvable(Mob mob) {
        return HostileMobs.isHostile(mob);
    }

    public static void tick(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) {
            return;
        }
        tick(mob, MobScaling.difficultyOf(entity), EliteSystem.isElite(entity));
    }

    public static void tick(Mob mob, long difficulty, boolean elite) {
        DifficultyConfig cfg = DifficultyConfig.get();
        if (!cfg.enableEnemyEvolution || mob == null || !mob.m_6084_() || DimensionGates.isDisabled(mob)) {
            return;
        }
        if (!(mob.m_9236_() instanceof ServerLevel level)) {
            return;
        }
        if (difficulty <= 0 && !elite) {
            return;
        }
        DifficultyTier tier = resolveAbilityTier(mob, difficulty, elite);
        if (tier.ordinalPower() < DifficultyTier.AWAKENED.ordinalPower()) {
            return;
        }

        markEvolved(mob, tier);

        // Kits are player-focused — drop other-hostile targets/revenge so packs don't civil-war.
        HostileMobs.clearCivilWarAggro(mob);
        LivingEntity target = mob.m_5448_();

        if (mob instanceof Creeper creeper) {
            creeperTick(creeper, level, target, tier, difficulty);
        } else if (mob instanceof ZombifiedPiglin zp) {
            zombiePiglinTick(zp, target, tier);
        } else if (mob instanceof Zombie zombie) {
            zombieTick(zombie, target, tier);
        } else if (mob instanceof AbstractSkeleton skeleton) {
            skeletonTick(skeleton, target, tier);
        } else if (mob instanceof EnderMan enderMan) {
            endermanTick(enderMan, level, target, tier, difficulty);
        } else if (mob instanceof Warden warden) {
            wardenTick(warden, level, target, tier, difficulty);
        } else if (mob instanceof Blaze blaze) {
            blazeTick(blaze, target, tier);
        } else if (mob instanceof Ghast ghast) {
            ghastTick(ghast, target, tier);
        } else if (mob instanceof Hoglin hoglin) {
            hoglinTick(hoglin, target, tier);
        } else if (mob instanceof AbstractPiglin piglin) {
            piglinTick(piglin, target, tier);
        } else if (isRangedStyle(mob)) {
            skeletonTick(mob, target, tier);
        } else {
            zombieTick(mob, target, tier);
        }
    }

    private static boolean isRangedStyle(Mob mob) {
        return mob instanceof RangedAttackMob
                || mob instanceof Witch
                || mob instanceof Guardian
                || mob instanceof Shulker
                || mob instanceof Raider && !(mob instanceof net.minecraft.world.entity.monster.Vindicator)
                        && !(mob instanceof net.minecraft.world.entity.monster.Ravager);
    }

    /**
     * Records which ability kit this mob was claimed with.
     * Intentionally does NOT set a custom nameplate or glow — those are reserved for
     * true rarity rolls (Elite / Mutation / Boss). Older builds named every T3+ kit mob
     * "Elite …", which made almost every scaled hostile look like a rarity variant.
     */
    private static void markEvolved(Mob mob, DifficultyTier tier) {
        if (tier.ordinalPower() < DifficultyTier.AWAKENED.ordinalPower()) {
            return;
        }
        CompoundTag tag = PersistentDataAccess.get(mob);
        String want = tier.display;
        String prev = tag.m_128461_("dmz_ad_ability_tier");
        if (!tag.m_128471_(TAG_EVOLVED) || !want.equals(prev)) {
            tag.m_128379_(TAG_EVOLVED, true);
            tag.m_128359_("dmz_ad_ability_tier", want);
        }
        // Clear leftover kit cosmetics from older jars; rarity nameplates stay.
        stripKitCosmeticIfPresent(mob);
    }

    /** True when this mob rolled a real rarity tag (elite / mutation / boss). */
    public static boolean hasRarityVariant(Mob mob) {
        if (mob == null) {
            return false;
        }
        CompoundTag tag = PersistentDataAccess.get(mob);
        if (tag.m_128471_(EliteSystem.TAG_ELITE) || tag.m_128471_(BossScaling.TAG_BOSS)) {
            return true;
        }
        return tag.m_128441_(MutationSystem.TAG_MUTATION)
                && !tag.m_128461_(MutationSystem.TAG_MUTATION).isEmpty();
    }

    /**
     * Strip leftover kit-only cosmetics from older builds (custom name + glow with no rarity tag).
     * Leaves true elite / mutation / boss nameplates alone.
     */
    public static void stripKitCosmeticIfPresent(Mob mob) {
        if (mob == null || hasRarityVariant(mob)) {
            return;
        }
        CompoundTag tag = PersistentDataAccess.get(mob);
        if (!tag.m_128471_(TAG_EVOLVED)) {
            return;
        }
        try {
            if (mob.m_8077_() && looksLikeKitNameplate(mob)) {
                mob.m_6593_(null);
            }
            mob.m_20340_(false);
        } catch (Throwable ignored) {
            // cosmetic cleanup only
        }
    }

    /** Matches the old kit format {@code §6<Tier> §f<Type>} (and stripped variants). */
    private static boolean looksLikeKitNameplate(Mob mob) {
        Component name = mob.m_7770_();
        if (name == null) {
            return false;
        }
        String s = name.getString();
        if (s == null || s.isEmpty()) {
            return false;
        }
        // Belt-and-suspenders: never strip known rarity nameplate shapes.
        if (s.contains("✦") || s.contains("☠") || s.startsWith("§d")) {
            return false;
        }
        for (DifficultyTier t : DifficultyTier.values()) {
            if (t == DifficultyTier.NONE) {
                continue;
            }
            String d = t.display;
            if (s.startsWith(d + " ") || s.startsWith("§6" + d + " ")) {
                return true;
            }
        }
        return false;
    }

    // ── Creepers: ignite + scaled blast radius / faster fuse ───────────────

    private static void creeperTick(
            Creeper creeper, ServerLevel level, LivingEntity target, DifficultyTier tier, long difficulty) {
        scaleCreeperBlast(creeper, tier, difficulty);
        // Only arm against players — never waste fuse on other mobs.
        if (!(target instanceof Player) || tier.ordinalPower() < DifficultyTier.AWAKENED.ordinalPower()) {
            return;
        }
        float dist = creeper.m_20270_(target);
        int unlock = MobScaling.unlockTierOf(creeper);
        double speed = tier.ordinalPower() >= DifficultyTier.ELITE.ordinalPower() ? 1.35 : 1.22;
        // Chase earlier than Elite so Awakened+ creepers actually close the gap.
        if (dist < 20.0f) {
            creeper.m_21573_().m_26519_(target.m_20185_(), target.m_20186_(), target.m_20189_(), speed);
        }
        // Fuse when close. m_32312_ = ignite(); m_32283_(1) = setSwellDir(1).
        // T1–T2 ignite a bit earlier so they actually get a boom off before dying.
        float igniteDist = unlock > 0 && unlock <= 2 ? 7.0f : 6.0f;
        if (dist < igniteDist) {
            if (!creeper.m_32311_()) { // isIgnited
                creeper.m_32312_(); // ignite
            }
            creeper.m_32283_(1); // setSwellDir toward explosion
        }
        // Light tracking on all unlocked tiers so fuse isn't wasted when the player sidesteps.
        // T4+/Elite keeps the faster sticky chase.
        if (creeper.m_32311_() && dist < 22.0f && target.m_6084_()) {
            boolean sticky = unlock >= 4
                    || tier.ordinalPower() >= DifficultyTier.ELITE.ordinalPower();
            double trackSpeed = sticky
                    ? speed + (tier.ordinalPower() >= DifficultyTier.MASTER.ordinalPower() ? 0.30 : 0.12)
                    : Math.max(1.05, speed - 0.05);
            creeper.m_21573_().m_26519_(target.m_20185_(), target.m_20186_(), target.m_20189_(), trackSpeed);
            creeper.m_6710_(target);
        }
    }

    /**
     * Final boom only when the creeper did <b>not</b> already explode (fuse / other blast).
     * Fuse detonation kills via explosion damage — stacking another blast was near-instant death.
     * Gated to Advanced+ kits so T1–T3 sword kills do not chain a second nuke.
     */
    public static void onCreeperDeath(Creeper creeper, DamageSource source) {
        if (creeper == null || creeper.m_9236_().f_46443_) {
            return;
        }
        // Skip if death was already from an explosion (vanilla fuse boom, TNT, etc.).
        if (source != null && source.m_269533_(DamageTypeTags.f_268415_)) { // IS_EXPLOSION
            return;
        }
        long difficulty = MobScaling.difficultyOf(creeper);
        DifficultyTier tier = resolveAbilityTier(creeper, difficulty, EliteSystem.isElite(creeper));
        int unlock = MobScaling.unlockTierOf(creeper);
        if (tier.ordinalPower() < DifficultyTier.ADVANCED.ordinalPower() && unlock < 4) {
            return;
        }
        CompoundTag tag = PersistentDataAccess.get(creeper);
        if (tag.m_128471_("dmz_ad_final_boom")) {
            return;
        }
        tag.m_128379_("dmz_ad_final_boom", true);
        float power = creeperExplosionPower(creeper, tier);
        if (creeper.m_9236_() instanceof ServerLevel level) {
            level.m_254849_(creeper, creeper.m_20185_(), creeper.m_20186_(), creeper.m_20189_(),
                    power, Level.ExplosionInteraction.MOB);
        }
    }

    private static float creeperExplosionPower(Creeper creeper, DifficultyTier tier) {
        double tierPct = MobScaling.tierPercentOf(creeper);
        int unlock = MobScaling.unlockTierOf(creeper);
        float power = 3.0f + Math.max(0, tier.ordinalPower()) * 0.35f + (float) (tierPct * 3.0);
        float cap = unlock <= 2 ? 4.0f : unlock <= 4 ? 6.5f : unlock <= 6 ? 8.5f : 10.0f;
        return Math.min(cap, power);
    }

    private static void scaleCreeperBlast(Creeper creeper, DifficultyTier tier, long difficulty) {
        CompoundTag tag = PersistentDataAccess.get(creeper);
        if (tag.m_128471_("dmz_ad_blast_scaled")) {
            return;
        }
        tag.m_128379_("dmz_ad_blast_scaled", true);
        int unlock = MobScaling.unlockTierOf(creeper);
        int bonus = Math.max(0, tier.ordinalPower() - DifficultyTier.AWAKENED.ordinalPower());
        if (unlock >= 4) {
            bonus += (int) Math.min(4, difficulty / 1000);
        }
        if (CREEPER_RADIUS == null || CREEPER_SWELL == null) {
            return;
        }
        try {
            int base = Math.max(3, CREEPER_RADIUS.getInt(creeper));
            // T1–T2 need a real blast footprint; prior soft caps made them feel inert.
            int radiusCap = unlock <= 2 ? 6 : unlock <= 4 ? 8 : 11;
            int radiusAdd = unlock <= 2 ? 1 + Math.min(1, bonus / 2) : Math.min(5, bonus / 2);
            CREEPER_RADIUS.setInt(creeper, Math.min(radiusCap, base + radiusAdd));
            int maxSwell = CREEPER_SWELL.getInt(creeper);
            // Faster early fuses so they boom before a transformed player deletes them.
            int fuseCut = unlock <= 2 ? Math.min(12, 8 + bonus) : Math.min(14, bonus + 4);
            int fuseFloor = unlock <= 2 ? 14 : 12;
            CREEPER_SWELL.setInt(creeper, Math.max(fuseFloor, maxSwell - fuseCut));
        } catch (Throwable ignored) {
        }
    }

    // ── Zombies: Leap / Rush / Ground Slam / Short Dash / chained leaps ───

    private static void zombieTick(Mob mob, LivingEntity target, DifficultyTier tier) {
        if (target == null) {
            if (mob.m_9236_() instanceof ServerLevel level) {
                target = nearestPlayer(level, mob, 20.0);
                if (target != null) {
                    mob.m_6710_(target);
                }
            }
        }
        if (target == null) {
            return;
        }
        CompoundTag tag = PersistentDataAccess.get(mob);
        long age = mob.f_19797_;
        double dist = mob.m_20270_(target);
        int power = tier.ordinalPower();

        // Close the gap — Awakened+ chase speed so melee aren't wet noodles vs ki kits.
        // A kiting player gets a small extra push so the dash is not the only answer.
        if (dist < 22.0 && power >= DifficultyTier.AWAKENED.ordinalPower()) {
            int amp = power >= DifficultyTier.MYTHIC.ordinalPower() ? 2
                    : power >= DifficultyTier.ELITE.ordinalPower() ? 1 : 0;
            mob.m_7292_(new MobEffectInstance(MobEffects.f_19596_, 40, amp, false, false));
            double chase = power >= DifficultyTier.GOD.ordinalPower() ? 1.55
                    : power >= DifficultyTier.ELITE.ordinalPower() ? 1.40
                    : power >= DifficultyTier.ENHANCED.ordinalPower() ? 1.28 : 1.18;
            if (targetKiting(mob, target)) {
                chase += 0.12;
            }
            mob.m_21573_().m_5624_(target, chase);
        }

        // One committed action: close = slam or shock, mid = dash, far = leap.
        long dashCd = kitCd(mob, power >= DifficultyTier.MYTHIC.ordinalPower() ? 16
                : power >= DifficultyTier.GOD.ordinalPower() ? 24
                : power >= DifficultyTier.ELITE.ordinalPower() ? 32 : 40);
        long leapCd = kitCd(mob, power >= DifficultyTier.TRANSCENDENT.ordinalPower() ? 18
                : power >= DifficultyTier.ADVANCED.ordinalPower() ? 30 : 50);
        long shockCd = kitCd(mob, power >= DifficultyTier.MYTHIC.ordinalPower() ? 28
                : power >= DifficultyTier.GOD.ordinalPower() ? 36 : 48);
        long slamEvery = kitCd(mob, power >= DifficultyTier.IMPOSSIBLE.ordinalPower() ? 30
                : power >= DifficultyTier.DIVINE.ordinalPower() ? 42 : 58);
        boolean close = dist < 3.8;
        DifficultyTier duelTier = DifficultyTier.parse(
                DifficultyConfig.get().adaptiveAiKitFighterTier, DifficultyTier.MYTHIC);
        if (power >= duelTier.ordinalPower()) {
            duelKit(mob, target, tier, tag, age, dist);
        } else if (close && power >= DifficultyTier.ADVANCED.ordinalPower()
                && age - tag.m_128454_("dmz_ad_slam") > slamEvery) {
            tag.m_128356_("dmz_ad_slam", age);
            markKitAct(tag, age);
            int amp = power >= DifficultyTier.OMEGA.ordinalPower() ? 2 : 1;
            groundSlam(mob, target, 3.2 + Math.min(2.2, power * 0.12), amp);
            if (power >= DifficultyTier.ABSOLUTE.ordinalPower() && target instanceof ServerPlayer sp) {
                sp.m_7292_(new MobEffectInstance(MobEffects.f_19615_, 50, 0, false, true)); // WITHER
            }
        } else if (close && power >= DifficultyTier.ELITE.ordinalPower()
                && age - tag.m_128454_("dmz_ad_melee_shock") > shockCd) {
            tag.m_128356_("dmz_ad_melee_shock", age);
            markKitAct(tag, age);
            double ratio = power >= DifficultyTier.OMEGA.ordinalPower() ? 0.72
                    : power >= DifficultyTier.GOD.ordinalPower() ? 0.60
                    : power >= DifficultyTier.MASTER.ordinalPower() ? 0.52 : 0.42;
            paintedMeleeHit(mob, target, (float) ratio);
        } else if (dist > 1.8 && dist < 8.0 && age - tag.m_128454_("dmz_ad_dash") > dashCd
                && power >= DifficultyTier.AWAKENED.ordinalPower()) {
            tag.m_128356_("dmz_ad_dash", age);
            markKitAct(tag, age);
            double force = power >= DifficultyTier.OMEGA.ordinalPower() ? 1.45
                    : power >= DifficultyTier.ELITE.ordinalPower() ? 1.15 : 1.00;
            pushToward(mob, target, force, 0.14);
        } else if (dist > 6.0 && dist < 15.0 && age - tag.m_128454_("dmz_ad_leap") > leapCd
                && power >= DifficultyTier.ENHANCED.ordinalPower()) {
            tag.m_128356_("dmz_ad_leap", age);
            markKitAct(tag, age);
            pushToward(mob, target, 1.35 + Math.min(0.7, power * 0.045), 0.62);
            if (power >= DifficultyTier.MASTER.ordinalPower()
                    && age - tag.m_128454_("dmz_ad_leap2") > kitCd(mob, 18)) {
                tag.m_128356_("dmz_ad_leap2", age);
                mob.m_5997_(0, 0.28, 0);
            }
        }

        // God+: berserk strength while chasing
        if (power >= DifficultyTier.GOD.ordinalPower() && dist < 16.0) {
            mob.m_7292_(new MobEffectInstance(MobEffects.f_19600_, 40,
                    power >= DifficultyTier.APEX.ordinalPower() ? 2 : 1, false, false));
        }
    }

    // ── Duel kit-fighter (high tiers): one action per beat, cycled by range ──

    private static final String[] DUEL_CLOSE = {"combo", "slam", "dodge", "retreat"};
    private static final String[] DUEL_MID = {"blast", "dash", "dodge"};
    private static final String[] DUEL_FAR = {"laser", "wave", "leap"};

    /**
     * Saga-style duel: instead of winning by numbers, the mob commits to ONE action per beat and
     * cycles through the actions that fit its range - close: melee combo, slam, dodge,
     * retreat-then-reengage; mid: ki blast, dash, dodge; far: ki laser, ki wave, leap. Each
     * action keeps its own cooldown; all cadence follows kitCooldownScale via {@link #kitCd}.
     */
    static void duelKit(Mob mob, LivingEntity target, DifficultyTier tier, CompoundTag tag, long age, double dist) {
        int power = tier.ordinalPower();
        long beat = kitCd(mob, power >= DifficultyTier.OMEGA.ordinalPower() ? 14 : 18);
        if (age - tag.m_128454_("dmz_ad_kit_act") < beat) {
            return;
        }
        // Retreat-reengage: the step back is followed by a dash straight back in.
        long reengageAt = tag.m_128454_("dmz_ad_duel_reengage");
        if (reengageAt > 0L && age >= reengageAt) {
            tag.m_128473_("dmz_ad_duel_reengage");
            pushToward(mob, target, power >= DifficultyTier.OMEGA.ordinalPower() ? 1.5 : 1.3, 0.14);
            markKitAct(tag, age);
            return;
        }
        String[] band;
        String key;
        if (dist < 3.8) {
            band = DUEL_CLOSE;
            key = "dmz_ad_duel_close";
        } else if (dist < 10.0) {
            band = DUEL_MID;
            key = "dmz_ad_duel_mid";
        } else if (dist < 24.0) {
            band = DUEL_FAR;
            key = "dmz_ad_duel_far";
        } else {
            return;
        }
        int step = Math.floorMod(tag.m_128451_(key), band.length);
        for (int i = 0; i < band.length; i++) {
            int idx = (step + i) % band.length;
            if (duelAction(mob, target, tier, band[idx], tag, age)) {
                tag.m_128405_(key, (idx + 1) % band.length);
                markKitAct(tag, age);
                return;
            }
        }
    }

    private static boolean duelAction(
            Mob mob, LivingEntity target, DifficultyTier tier, String act, CompoundTag tag, long age) {
        int power = tier.ordinalPower();
        String cdKey = "dmz_ad_duel_cd_" + act;
        long cd = kitCd(mob, switch (act) {
            case "combo" -> 20;
            case "slam" -> 40;
            case "dodge" -> 30;
            case "retreat" -> 70;
            case "blast" -> 30;
            case "dash" -> 24;
            case "laser" -> 50;
            case "wave" -> 90;
            default -> 30; // leap
        });
        if (age - tag.m_128454_(cdKey) < cd) {
            return false;
        }
        boolean done;
        switch (act) {
            case "combo" -> {
                double ratio = power >= DifficultyTier.OMEGA.ordinalPower() ? 0.72 : 0.60;
                paintedMeleeHit(mob, target, (float) ratio);
                pushToward(mob, target, 0.35, 0.05); // stay on them for the next beat
                done = true;
            }
            case "slam" -> {
                groundSlam(mob, target, 3.2 + Math.min(2.2, power * 0.12),
                        power >= DifficultyTier.OMEGA.ordinalPower() ? 2 : 1);
                done = true;
            }
            case "dodge" -> {
                sidestep(mob, target, power >= DifficultyTier.OMEGA.ordinalPower() ? 0.95 : 0.8);
                done = true;
            }
            case "retreat" -> {
                pushAway(mob, target, 1.0);
                tag.m_128356_("dmz_ad_duel_reengage", age + kitCd(mob, 16));
                done = true;
            }
            case "blast" -> done = KiAttackHelper.fireKiBlast(
                    mob, target, tier, power >= DifficultyTier.GOD.ordinalPower());
            case "dash" -> {
                pushToward(mob, target, power >= DifficultyTier.OMEGA.ordinalPower() ? 1.45 : 1.25, 0.14);
                done = true;
            }
            case "laser" -> done = KiAttackHelper.fireKiLaser(mob, target, tier);
            case "wave" -> done = KiAttackHelper.fireKiBeam(mob, target, tier, false);
            default -> {
                pushToward(mob, target, 1.35 + Math.min(0.7, power * 0.045), 0.62);
                done = true;
            }
        }
        if (done) {
            tag.m_128356_(cdKey, age);
        }
        return done;
    }

    /** Quick lateral step, alternating sides, so the duel mob is not a straight-line target. */
    private static void sidestep(Mob mob, LivingEntity target, double force) {
        double dx = target.m_20185_() - mob.m_20185_();
        double dz = target.m_20189_() - mob.m_20189_();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) {
            return;
        }
        double side = ThreadLocalRandom.current().nextBoolean() ? 1.0 : -1.0;
        mob.m_5997_((-dz / len) * force * side, 0.12, (dx / len) * force * side);
    }

    // ── Skeletons: Ki only (blast / laser / beam / charge) ────────────────

    private static void skeletonTick(Mob mob, LivingEntity target, DifficultyTier tier) {
        if (target == null) {
            if (mob.m_9236_() instanceof ServerLevel level) {
                target = nearestPlayer(level, mob, 28.0);
                if (target != null) {
                    mob.m_6710_(target);
                }
            }
        }
        if (target == null) {
            return;
        }
        // Never aim kits at our own mount (spider jockey / skeleton horse).
        if (HostileMobs.isMountPair(mob, target)) {
            mob.m_6710_(null);
            if (mob.m_9236_() instanceof ServerLevel level) {
                target = nearestPlayer(level, mob, 28.0);
                if (target != null) {
                    mob.m_6710_(target);
                }
            }
            if (target == null || HostileMobs.isMountPair(mob, target)) {
                return;
            }
        }
        float dist = mob.m_20270_(target);
        if (dist > 32.0f) {
            return;
        }
        CompoundTag tag = PersistentDataAccess.get(mob);
        long age = mob.f_19797_;
        int power = tier.ordinalPower();

        // Global gap between ANY skeleton ki shot (tick kits + arrow-replace share this).
        // High tiers stay threatening without stunlocking players in place.
        if (!skeletonKiReady(mob, tag, age, tier)) {
            return;
        }

        // Per-ability cooldowns — tightened vs ki / high-PWR counter targets.
        long chargedCd = kitCd(mob, power >= DifficultyTier.ZENITH.ordinalPower() ? 100
                : power >= DifficultyTier.OMEGA.ordinalPower() ? 120
                : power >= DifficultyTier.IMPOSSIBLE.ordinalPower() ? 140 : 160);
        long beamCd = kitCd(mob, power >= DifficultyTier.MYTHIC.ordinalPower() ? 80
                : power >= DifficultyTier.DIVINE.ordinalPower() ? 95 : 115);
        long laserCd = kitCd(mob, power >= DifficultyTier.GOD.ordinalPower() ? 55
                : power >= DifficultyTier.LEGENDARY.ordinalPower() ? 70 : 90);
        long blastCd = kitCd(mob, power >= DifficultyTier.TRANSCENDENT.ordinalPower() ? 40
                : power >= DifficultyTier.MASTER.ordinalPower() ? 50 : 65);
        long barrageCd = kitCd(mob, power >= DifficultyTier.ZENITH.ordinalPower() ? 140
                : power >= DifficultyTier.OMEGA.ordinalPower() ? 160 : 180);

        // Close: step off the player and fire a blast. Beams wait until there is space.
        if (dist < 6.0f) {
            if (power >= DifficultyTier.ENHANCED.ordinalPower()) {
                pushAway(mob, target, power >= DifficultyTier.GOD.ordinalPower() ? 0.72 : 0.48);
            }
            if (power >= DifficultyTier.AWAKENED.ordinalPower()
                    && age - tag.m_128454_("dmz_ad_ki_blast") >= blastCd) {
                boolean burning = power >= DifficultyTier.GOD.ordinalPower();
                if (KiAttackHelper.fireKiBlast(mob, target, tier, burning)) {
                    markSkeletonKiShot(tag, age, "dmz_ad_ki_blast");
                    markKitAct(tag, age);
                }
            }
            return;
        }

        // Mid: laser, then blast, then a short barrage. Not the charged beam.
        if (dist < 16.0f) {
            if (power >= DifficultyTier.ELITE.ordinalPower()
                    && age - tag.m_128454_("dmz_ad_ki_laser") >= laserCd
                    && dist < 22.0f) {
                if (KiAttackHelper.fireKiLaser(mob, target, tier)) {
                    markSkeletonKiShot(tag, age, "dmz_ad_ki_laser");
                    markKitAct(tag, age);
                    return;
                }
            }
            if (power >= DifficultyTier.AWAKENED.ordinalPower()
                    && age - tag.m_128454_("dmz_ad_ki_blast") >= blastCd
                    && dist < 18.0f) {
                boolean burning = power >= DifficultyTier.GOD.ordinalPower();
                if (KiAttackHelper.fireKiBlast(mob, target, tier, burning)) {
                    markSkeletonKiShot(tag, age, "dmz_ad_ki_blast");
                    markKitAct(tag, age);
                    return;
                }
            }
            if (power >= DifficultyTier.IMPOSSIBLE.ordinalPower()
                    && age - tag.m_128454_("dmz_ad_ki_barrage") >= barrageCd
                    && dist < 20.0f) {
                int shots = 2 + Math.min(3, (power - DifficultyTier.IMPOSSIBLE.ordinalPower()) / 2);
                if (KiAttackHelper.fireKiBarrage(mob, target, tier, shots, power >= DifficultyTier.OMEGA.ordinalPower()) > 0) {
                    markSkeletonKiShot(tag, age, "dmz_ad_ki_barrage");
                    markKitAct(tag, age);
                }
            }
            return;
        }

        // Far: beam zones. Charged beam, then beam, then laser, then blast.
        if (power >= DifficultyTier.MASTER.ordinalPower()
                && age - tag.m_128454_("dmz_ad_ki_charged") >= chargedCd
                && dist < 26.0f) {
            if (KiAttackHelper.fireKiBeam(mob, target, tier, true)) {
                markSkeletonKiShot(tag, age, "dmz_ad_ki_charged");
                markKitAct(tag, age);
                return;
            }
        }
        if (power >= DifficultyTier.ADVANCED.ordinalPower()
                && age - tag.m_128454_("dmz_ad_ki_beam") >= beamCd
                && dist < 24.0f) {
            if (KiAttackHelper.fireKiBeam(mob, target, tier, false)) {
                markSkeletonKiShot(tag, age, "dmz_ad_ki_beam");
                markKitAct(tag, age);
                return;
            }
        }
        if (power >= DifficultyTier.ELITE.ordinalPower()
                && age - tag.m_128454_("dmz_ad_ki_laser") >= laserCd
                && dist < 22.0f) {
            if (KiAttackHelper.fireKiLaser(mob, target, tier)) {
                markSkeletonKiShot(tag, age, "dmz_ad_ki_laser");
                markKitAct(tag, age);
                return;
            }
        }
        if (power >= DifficultyTier.AWAKENED.ordinalPower()
                && age - tag.m_128454_("dmz_ad_ki_blast") >= blastCd
                && dist < 18.0f) {
            boolean burning = power >= DifficultyTier.GOD.ordinalPower();
            if (KiAttackHelper.fireKiBlast(mob, target, tier, burning)) {
                markSkeletonKiShot(tag, age, "dmz_ad_ki_blast");
                markKitAct(tag, age);
            }
        }
    }

    /** Minimum ticks between any skeleton ki projectile (shared by kits + arrow replace). */
    private static long skeletonShotGap(LivingEntity entity, DifficultyTier tier) {
        int power = tier == null ? 0 : tier.ordinalPower();
        long base;
        if (power >= DifficultyTier.ZENITH.ordinalPower()) {
            base = 28L;
        } else if (power >= DifficultyTier.MYTHIC.ordinalPower()) {
            base = 32L;
        } else if (power >= DifficultyTier.GOD.ordinalPower()) {
            base = 36L;
        } else if (power >= DifficultyTier.MASTER.ordinalPower()) {
            base = 40L;
        } else {
            base = 45L;
        }
        return kitCd(entity, base);
    }

    private static boolean skeletonKiReady(
            LivingEntity entity, CompoundTag tag, long age, DifficultyTier tier
    ) {
        if (tag == null) {
            return false;
        }
        return age - tag.m_128454_("dmz_ad_ki_shot") >= skeletonShotGap(entity, tier);
    }

    /** Apply stamped class + top-stat kit cadence (&lt;1 = more aggressive kits). */
    private static long kitCd(LivingEntity entity, long baseTicks) {
        double scale = MobScaling.counterKitCooldownScale(entity);
        return Math.max(8L, Math.round(Math.max(1L, baseTicks) * scale));
    }

    private static void markSkeletonKiShot(CompoundTag tag, long age, String abilityKey) {
        if (tag == null) {
            return;
        }
        tag.m_128356_("dmz_ad_ki_shot", age);
        if (abilityKey != null && !abilityKey.isBlank()) {
            tag.m_128356_(abilityKey, age);
        }
    }

    // ── Endermen: Gravity (stacked) / Solar Flare / Teleport combos ───────

    private static void endermanTick(
            EnderMan ender, ServerLevel level, LivingEntity target, DifficultyTier tier, long difficulty) {
        // Gravity is applied every player tick via CombatGravity.scanNearbySources.
        // Still acquire a player target so flare / TP / chase kits keep working after blinks.
        ServerPlayer player = target instanceof ServerPlayer sp
                ? sp
                : nearestPlayer(level, ender, 28.0);
        if (player == null) {
            return;
        }
        if (target != player) {
            ender.m_6710_(player);
        }
        CompoundTag tag = PersistentDataAccess.get(ender);
        long age = ender.f_19797_;
        float dist = ender.m_20270_(player);

        // Backup contribute (player-tick scan is primary).
        if (tier.ordinalPower() >= DifficultyTier.AWAKENED.ordinalPower() && dist < 28.0f) {
            double g = CombatGravity.gravityFor(ender, tier, difficulty, 1.0);
            CombatGravity.contribute(player, ender.m_20148_(), g, 45);
        }

        // One action: flare up close, otherwise a blink. Chase speed still runs.
        long tpCd = kitCd(ender, tier.ordinalPower() >= DifficultyTier.MASTER.ordinalPower() ? 35
                : tier.ordinalPower() >= DifficultyTier.ELITE.ordinalPower() ? 55 : 80);
        if (tier.ordinalPower() >= DifficultyTier.ENHANCED.ordinalPower()
                && age - tag.m_128454_("dmz_ad_flare") >= kitCd(ender, 90)
                && dist < 14.0f) {
            tag.m_128356_("dmz_ad_flare", age);
            markKitAct(tag, age);
            solarFlare(level, ender, 10.0);
        } else if (tier.ordinalPower() >= DifficultyTier.ELITE.ordinalPower()
                && age - tag.m_128454_("dmz_ad_tp") > tpCd
                && dist > 4.0f) {
            tag.m_128356_("dmz_ad_tp", age);
            markKitAct(tag, age);
            teleportNear(ender, player, dist < 10.0f ? 3.5 : 2.5);
        }

        if (tier.ordinalPower() >= DifficultyTier.MASTER.ordinalPower()) {
            ender.m_7292_(new MobEffectInstance(MobEffects.f_19596_, 40, 1, false, false));
            ender.m_21573_().m_5624_(player, 1.45);
        }
    }

    // ── Wardens: mini raid boss kit + gravity ─────────────────────────────

    private static void wardenTick(
            Warden warden, ServerLevel level, LivingEntity target, DifficultyTier tier, long difficulty) {
        ServerPlayer focused = target instanceof ServerPlayer sp
                ? sp
                : nearestPlayer(level, warden, 32.0);
        if (focused != null && target != focused) {
            warden.m_6710_(focused);
            target = focused;
        }
        if (target == null) {
            return;
        }
        CompoundTag tag = PersistentDataAccess.get(warden);
        long age = warden.f_19797_;
        float dist = warden.m_20270_(target);

        // Backup contribute (player-tick scan is primary).
        if (focused != null
                && tier.ordinalPower() >= DifficultyTier.AWAKENED.ordinalPower()
                && dist < 32.0f) {
            double g = CombatGravity.gravityFor(warden, tier, difficulty, 1.6);
            CombatGravity.contribute(focused, warden.m_20148_(), g, 45);
        }

        // One action: close = wave or leap, mid = large or barrage, far = beam or blink.
        if (dist < 6.0f) {
            if (tier.ordinalPower() >= DifficultyTier.MASTER.ordinalPower()
                    && age - tag.m_128454_("dmz_ad_wave") >= kitCd(warden, 140)) {
                if (KiAttackHelper.fireExplosiveWave(warden, target, tier)) {
                    tag.m_128356_("dmz_ad_wave", age);
                    markKitAct(tag, age);
                }
            } else if (dist > 4.0 && age - tag.m_128454_("dmz_ad_leap") > kitCd(warden, 50)
                    && tier.ordinalPower() >= DifficultyTier.ENHANCED.ordinalPower()) {
                tag.m_128356_("dmz_ad_leap", age);
                markKitAct(tag, age);
                pushToward(warden, target, 1.35, 0.65);
            }
        } else if (dist < 16.0f) {
            if (tier.ordinalPower() >= DifficultyTier.ADVANCED.ordinalPower()
                    && age - tag.m_128454_("dmz_ad_large") >= kitCd(warden, 85)
                    && dist < 24.0f
                    && KiAttackHelper.fireLargeBlast(warden, target, tier)) {
                tag.m_128356_("dmz_ad_large", age);
                markKitAct(tag, age);
            } else if (tier.ordinalPower() >= DifficultyTier.ELITE.ordinalPower()
                    && age - tag.m_128454_("dmz_ad_ki_barrage") >= kitCd(warden, 95)
                    && dist < 22.0f) {
                int shots = 2 + Math.min(1, tier.ordinalPower() / 4);
                if (KiAttackHelper.fireKiBarrage(warden, target, tier, shots) > 0) {
                    tag.m_128356_("dmz_ad_ki_barrage", age);
                    markKitAct(tag, age);
                }
            } else if (age - tag.m_128454_("dmz_ad_leap") > kitCd(warden, 50)
                    && tier.ordinalPower() >= DifficultyTier.ENHANCED.ordinalPower()) {
                tag.m_128356_("dmz_ad_leap", age);
                markKitAct(tag, age);
                pushToward(warden, target, 1.35, 0.65);
            }
        } else if (tier.ordinalPower() >= DifficultyTier.ADVANCED.ordinalPower()
                && age - tag.m_128454_("dmz_ad_ki_beam") >= kitCd(warden, 100)
                && dist < 26.0f
                && KiAttackHelper.fireKiBeam(warden, target, tier,
                        tier.ordinalPower() >= DifficultyTier.MASTER.ordinalPower())) {
            tag.m_128356_("dmz_ad_ki_beam", age);
            markKitAct(tag, age);
        } else if (tier.ordinalPower() >= DifficultyTier.MASTER.ordinalPower()
                && age - tag.m_128454_("dmz_ad_tp") >= kitCd(warden, 100)
                && dist > 12.0f) {
            tag.m_128356_("dmz_ad_tp", age);
            markKitAct(tag, age);
            teleportNear(warden, target, 2.0);
        }
    }

    // ── Blazes: Rapid barrage / Burning blast / Explosion blast ───────────

    private static void blazeTick(Blaze blaze, LivingEntity target, DifficultyTier tier) {
        if (target == null && blaze.m_9236_() instanceof ServerLevel level) {
            target = nearestPlayer(level, blaze, 26.0);
            if (target != null) {
                blaze.m_6710_(target);
            }
        }
        if (target == null) {
            return;
        }
        CompoundTag tag = PersistentDataAccess.get(blaze);
        long age = blaze.f_19797_;
        float dist = blaze.m_20270_(target);
        if (dist > 26.0f) {
            return;
        }

        // Shared ki gap with vanilla fireball replace — prevents kit + projectile double-dump.
        if (!blazeKiReady(blaze, tag, age, tier)) {
            return;
        }

        // Inside melee, step back and use one blast. Barrage waits until there is range.
        if (dist < 6.0f) {
            if (tier.ordinalPower() >= DifficultyTier.ENHANCED.ordinalPower()) {
                pushAway(blaze, target, 0.55);
            }
            if (tier.ordinalPower() >= DifficultyTier.ELITE.ordinalPower()
                    && age - tag.m_128454_("dmz_ad_ki_blast") >= kitCd(blaze, 70)) {
                if (KiAttackHelper.fireKiBlast(blaze, target, tier, true)) {
                    markBlazeKiShot(tag, age, "dmz_ad_ki_blast");
                    markKitAct(tag, age);
                }
            }
            return;
        }

        if (tier.ordinalPower() >= DifficultyTier.AWAKENED.ordinalPower()
                && age - tag.m_128454_("dmz_ad_ki_barrage") >= kitCd(blaze, 85)
                && dist < 18.0f) {
            int shots = 2 + Math.min(1, tier.ordinalPower() / 5);
            if (KiAttackHelper.fireKiBarrage(blaze, target, tier, shots, true) > 0) {
                markBlazeKiShot(tag, age, "dmz_ad_ki_barrage");
                markKitAct(tag, age);
                return;
            }
        }
        if (tier.ordinalPower() >= DifficultyTier.ELITE.ordinalPower()
                && age - tag.m_128454_("dmz_ad_ki_blast") >= kitCd(blaze, 70)
                && dist < 20.0f) {
            if (KiAttackHelper.fireKiBlast(blaze, target, tier, true)) {
                markBlazeKiShot(tag, age, "dmz_ad_ki_blast");
                markKitAct(tag, age);
                return;
            }
        }
        if (tier.ordinalPower() >= DifficultyTier.ADVANCED.ordinalPower()
                && age - tag.m_128454_("dmz_ad_explode") >= kitCd(blaze, 120)
                && dist < 16.0f) {
            if (KiAttackHelper.fireExplosionBlast(blaze, target, tier)) {
                markBlazeKiShot(tag, age, "dmz_ad_explode");
                markKitAct(tag, age);
            }
        }
        // Intentionally no per-tick ignite — burning is applied by successful ki hits only.
    }

    private static long blazeKiGap(LivingEntity entity, DifficultyTier tier) {
        int power = tier == null ? 0 : tier.ordinalPower();
        long base;
        if (power >= DifficultyTier.ZENITH.ordinalPower()) {
            base = 30L;
        } else if (power >= DifficultyTier.GOD.ordinalPower()) {
            base = 36L;
        } else if (power >= DifficultyTier.MASTER.ordinalPower()) {
            base = 42L;
        } else {
            base = 48L;
        }
        return kitCd(entity, base);
    }

    private static boolean blazeKiReady(
            LivingEntity entity, CompoundTag tag, long age, DifficultyTier tier
    ) {
        return tag != null && age - tag.m_128454_("dmz_ad_ki_shot") >= blazeKiGap(entity, tier);
    }

    private static void markBlazeKiShot(CompoundTag tag, long age, String abilityKey) {
        if (tag == null) {
            return;
        }
        tag.m_128356_("dmz_ad_ki_shot", age);
        if (abilityKey != null && !abilityKey.isBlank()) {
            tag.m_128356_(abilityKey, age);
        }
    }

    // ── Ghasts: Large Blast / Burn Beam / Explosion Wave ──────────────────

    private static void ghastTick(Ghast ghast, LivingEntity target, DifficultyTier tier) {
        if (target == null && ghast.m_9236_() instanceof ServerLevel level) {
            target = nearestPlayer(level, ghast, 48.0);
            if (target != null) {
                ghast.m_6710_(target);
            }
        }
        if (target == null) {
            return;
        }
        // Flying AI wanders look; keep facing the player so vanilla charge + our kits aim.
        KiAttackHelper.faceTarget(ghast, target);
        CompoundTag tag = PersistentDataAccess.get(ghast);
        long age = ghast.f_19797_;
        float dist = ghast.m_20270_(target);
        if (dist > 48.0f) {
            return;
        }

        // One shot: far beam, close wave, otherwise the large blast.
        if (dist >= 18.0f
                && tier.ordinalPower() >= DifficultyTier.ELITE.ordinalPower()
                && age - tag.m_128454_("dmz_ad_ki_beam") >= kitCd(ghast, 80)
                && dist < 42.0f
                && KiAttackHelper.fireKiBeam(ghast, target, tier, true, true)) {
            tag.m_128356_("dmz_ad_ki_beam", age);
            markKitAct(tag, age);
        } else if (dist < 22.0f
                && tier.ordinalPower() >= DifficultyTier.ADVANCED.ordinalPower()
                && age - tag.m_128454_("dmz_ad_wave") >= kitCd(ghast, 120)
                && KiAttackHelper.fireExplosiveWave(ghast, target, tier)) {
            tag.m_128356_("dmz_ad_wave", age);
            markKitAct(tag, age);
        } else if (tier.ordinalPower() >= DifficultyTier.ENHANCED.ordinalPower()
                && age - tag.m_128454_("dmz_ad_large") >= kitCd(ghast, 50)
                && dist < 40.0f
                && KiAttackHelper.fireLargeBlast(ghast, target, tier)) {
            // Large blast starts at Enhanced — Awakened (T1) keeps vanilla fireballs only.
            tag.m_128356_("dmz_ad_large", age);
            markKitAct(tag, age);
            target.m_20254_(3);
        }
    }

    // ── Piglins: Leap / Small Blast / Rush Combo ──────────────────────────

    private static void piglinTick(AbstractPiglin piglin, LivingEntity target, DifficultyTier tier) {
        if (target == null && piglin.m_9236_() instanceof ServerLevel level) {
            target = nearestPlayer(level, piglin, 16.0);
            if (target != null) {
                piglin.m_6710_(target);
            }
        }
        if (target == null) {
            return;
        }
        CompoundTag tag = PersistentDataAccess.get(piglin);
        long age = piglin.f_19797_;
        double dist = piglin.m_20270_(target);

        if (dist < 16.0 && tier.ordinalPower() >= DifficultyTier.ELITE.ordinalPower()) {
            double chase = targetKiting(piglin, target) ? 1.52 : 1.4;
            piglin.m_7292_(new MobEffectInstance(MobEffects.f_19596_, 40, 1, false, false));
            piglin.m_21573_().m_5624_(target, chase);
        }
        if (dist < 3.0 && tier.ordinalPower() >= DifficultyTier.ADVANCED.ordinalPower()
                && age - tag.m_128454_("dmz_ad_rush") >= kitCd(piglin, 60)) {
            tag.m_128356_("dmz_ad_rush", age);
            markKitAct(tag, age);
            // Rush combo — slam + brief strength
            groundSlam(piglin, target, 2.5, 0);
            piglin.m_7292_(new MobEffectInstance(MobEffects.f_19600_, 40, 1, false, true));
        } else if (dist > 3.0 && dist < 12.0 && age - tag.m_128454_("dmz_ad_leap") > kitCd(piglin, 55)
                && tier.ordinalPower() >= DifficultyTier.ENHANCED.ordinalPower()) {
            tag.m_128356_("dmz_ad_leap", age);
            markKitAct(tag, age);
            pushToward(piglin, target, 1.2, 0.55);
        } else if (tier.ordinalPower() >= DifficultyTier.ELITE.ordinalPower()
                && age - tag.m_128454_("dmz_ad_ki_blast") >= kitCd(piglin, 50)
                && dist >= 4.0 && dist < 16.0) {
            if (KiAttackHelper.fireKiBlast(piglin, target, tier)) {
                tag.m_128356_("dmz_ad_ki_blast", age);
                markKitAct(tag, age);
            }
        }
    }

    // ── Zombie Pigmen: Leap / Ki Barrage / Counter TP / swarm ─────────────

    private static void zombiePiglinTick(ZombifiedPiglin zp, LivingEntity target, DifficultyTier tier) {
        if (target == null && zp.m_9236_() instanceof ServerLevel level) {
            target = nearestPlayer(level, zp, 18.0);
            if (target != null) {
                zp.m_6710_(target);
            }
        }
        if (target == null) {
            return;
        }
        CompoundTag tag = PersistentDataAccess.get(zp);
        long age = zp.f_19797_;
        double dist = zp.m_20270_(target);

        if (dist < 3.5 && tier.ordinalPower() >= DifficultyTier.ELITE.ordinalPower()
                && age - tag.m_128454_("dmz_ad_melee_shock") > kitCd(zp, 36)) {
            tag.m_128356_("dmz_ad_melee_shock", age);
            markKitAct(tag, age);
            paintedMeleeHit(zp, target, tier.ordinalPower() >= DifficultyTier.GOD.ordinalPower() ? 0.48f : 0.36f);
        } else if (dist > 3.0 && dist < 13.0 && age - tag.m_128454_("dmz_ad_leap") > kitCd(zp, 50)
                && tier.ordinalPower() >= DifficultyTier.ENHANCED.ordinalPower()) {
            tag.m_128356_("dmz_ad_leap", age);
            markKitAct(tag, age);
            pushToward(zp, target, 1.3, 0.6);
        } else if (tier.ordinalPower() >= DifficultyTier.ELITE.ordinalPower()
                && age - tag.m_128454_("dmz_ad_ki_barrage") >= kitCd(zp, 65)
                && dist >= 5.0 && dist < 18.0) {
            int shots = 2 + Math.min(4, tier.ordinalPower() / 2);
            if (KiAttackHelper.fireKiBarrage(zp, target, tier, shots) > 0) {
                tag.m_128356_("dmz_ad_ki_barrage", age);
                markKitAct(tag, age);
            }
        }
        // Aggressive swarm — combat-index peers only (no world AABB)
        if (target instanceof Player packTarget
                && tier.ordinalPower() >= DifficultyTier.ADVANCED.ordinalPower() && age % 40 == 0) {
            CombatIndex.forEachNear(zp, 16.0, 12, ally -> {
                if (!(ally instanceof ZombifiedPiglin) || !ally.m_6084_()) {
                    return;
                }
                LivingEntity allyTarget = ally.m_5448_();
                if (allyTarget != null && !(allyTarget instanceof Player) && HostileMobs.isHostile(allyTarget)) {
                    ally.m_6710_(null);
                }
                ally.m_6710_(packTarget);
                ally.m_21573_().m_5624_(packTarget, 1.3);
                CombatIndex.mark(ally);
            });
            zp.m_7292_(new MobEffectInstance(MobEffects.f_19596_, 50, 1, false, false));
        }
    }

    // ── Hoglins: aerial — launch grounded / smash airborne ────────────────

    private static void hoglinTick(Hoglin hoglin, LivingEntity target, DifficultyTier tier) {
        if (target == null && hoglin.m_9236_() instanceof ServerLevel level) {
            target = nearestPlayer(level, hoglin, 14.0);
            if (target != null) {
                hoglin.m_6710_(target);
            }
        }
        if (target == null || tier.ordinalPower() < DifficultyTier.ENHANCED.ordinalPower()) {
            return;
        }
        CompoundTag tag = PersistentDataAccess.get(hoglin);
        long age = hoglin.f_19797_;
        double dist = hoglin.m_20270_(target);
        if (dist > 14.0 || age - tag.m_128454_("dmz_ad_aerial") < kitCd(hoglin, 55)) {
            return;
        }
        tag.m_128356_("dmz_ad_aerial", age);

        boolean grounded = target.m_20096_(); // onGround
        if (grounded) {
            // Launch upward
            target.m_5997_(0, 1.05 + Math.min(0.6, tier.ordinalPower() * 0.08), 0);
            pushToward(hoglin, target, 0.8, 0.45);
        } else {
            // Smash downward while airborne
            Vec3 v = target.m_20184_();
            target.m_20334_(v.f_82479_ * 0.4, -1.35 - tier.ordinalPower() * 0.08, v.f_82481_ * 0.4);
            hoglin.m_6021_(target.m_20185_(), target.m_20186_(), target.m_20189_());
            target.m_6469_(hoglin.m_269291_().m_269333_(hoglin), 4.0f + tier.ordinalPower());
        }
    }

    // ── Shared helpers ────────────────────────────────────────────────────

    private static ServerPlayer nearestPlayer(ServerLevel level, Mob mob, double radius) {
        // Prefer participants so kits don't lock onto personal-off / blocked players.
        return NearbyPlayers.nearestParticipating(mob, radius);
    }

    private static void solarFlare(ServerLevel level, Mob source, double radius) {
        NearbyPlayers.forEachWithin(source, radius, p -> {
            p.m_7292_(new MobEffectInstance(MobEffects.f_19610_, 70, 0, false, true)); // BLINDNESS
            p.m_7292_(new MobEffectInstance(MobEffects.f_19604_, 50, 0, false, true)); // NAUSEA
            p.m_7292_(new MobEffectInstance(MobEffects.f_19619_, 40, 0, false, true)); // GLOWING flash
        });
        source.m_7292_(new MobEffectInstance(MobEffects.f_19619_, 40, 0, false, true));
    }

    private static void groundSlam(Mob mob, LivingEntity target, double radius, int slowAmp) {
        target.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 60, slowAmp, false, true));
        target.m_5997_(0, 0.35, 0);
        // Painted ATK slice — slam was control-only before, so melee felt toothless vs ki kits.
        paintedMeleeHit(mob, target, 0.55f);
        NearbyPlayers.forEachWithin(mob, radius, p -> {
            if (p != target) {
                p.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 40, 0, false, true));
                paintedMeleeHit(mob, p, 0.28f);
            }
        });
    }

    /** Deal a fraction of the mob's painted ATTACK_DAMAGE (melee parity with ki kits). */
    private static void paintedMeleeHit(Mob mob, LivingEntity target, float ratio) {
        if (mob == null || target == null || !target.m_6084_() || !(ratio > 0.0f)) {
            return;
        }
        var atk = mob.m_21051_(Attributes.f_22281_); // ATTACK_DAMAGE
        double painted = atk == null ? 4.0 : Math.max(1.0, atk.m_22135_());
        float dmg = (float) Math.max(2.0, painted * Math.max(0.05, Math.min(1.5, ratio)));
        target.m_6469_(mob.m_269291_().m_269333_(mob), dmg);
    }

    private static void markKitAct(CompoundTag tag, long age) {
        if (tag != null) {
            tag.m_128356_("dmz_ad_kit_act", age);
        }
    }

    /** True when the target's horizontal motion is carrying them away from the mob. */
    private static boolean targetKiting(Mob mob, LivingEntity target) {
        if (mob == null || target == null) {
            return false;
        }
        Vec3 motion = target.m_20184_();
        double dx = target.m_20185_() - mob.m_20185_();
        double dz = target.m_20189_() - mob.m_20189_();
        return motion.f_82479_ * dx + motion.f_82481_ * dz > 0.03;
    }

    private static void pushToward(Mob mob, LivingEntity target, double horizontal, double upward) {
        double dx = target.m_20185_() - mob.m_20185_();
        double dz = target.m_20189_() - mob.m_20189_();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0.001) {
            mob.m_5997_((dx / len) * horizontal, upward, (dz / len) * horizontal);
        }
    }

    private static void pushAway(Mob mob, LivingEntity target, double horizontal) {
        double dx = mob.m_20185_() - target.m_20185_();
        double dz = mob.m_20189_() - target.m_20189_();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0.001) {
            mob.m_5997_((dx / len) * horizontal, 0.04, (dz / len) * horizontal);
        }
    }

    private static void teleportNear(Mob mob, LivingEntity target, double spread) {
        double ox = (ThreadLocalRandom.current().nextDouble() - 0.5) * spread * 2.0;
        double oz = (ThreadLocalRandom.current().nextDouble() - 0.5) * spread * 2.0;
        mob.m_6021_(target.m_20185_() + ox, target.m_20186_(), target.m_20189_() + oz);
    }

    /** Counter-teleport / counter-movement when damaged. */
    public static void onHurt(LivingEntity entity) {
        if (entity == null || entity.m_9236_().f_46443_) {
            return;
        }
        // Only kits that counter on hurt — bail before any NBT for other entities.
        if (!(entity instanceof EnderMan || entity instanceof ZombifiedPiglin || entity instanceof Warden)) {
            return;
        }
        long difficulty = MobScaling.difficultyOf(entity);
        DifficultyTier tier = resolveAbilityTier(entity, difficulty, EliteSystem.isElite(entity));
        LivingEntity target = entity instanceof Mob mob ? mob.m_5448_() : null;

        if (entity instanceof EnderMan ender
                && tier.ordinalPower() >= DifficultyTier.ADVANCED.ordinalPower()
                && ThreadLocalRandom.current().nextDouble() < 0.4
                && target != null) {
            teleportNear(ender, target, 2.0);
            return;
        }
        if (entity instanceof ZombifiedPiglin zp
                && tier.ordinalPower() >= DifficultyTier.ADVANCED.ordinalPower()
                && ThreadLocalRandom.current().nextDouble() < 0.3
                && target != null) {
            teleportNear(zp, target, 2.5);
            return;
        }
        if (entity instanceof Warden warden
                && tier.ordinalPower() >= DifficultyTier.ELITE.ordinalPower()
                && ThreadLocalRandom.current().nextDouble() < 0.35
                && target != null) {
            // Counter movement — blink or leap at attacker
            if (warden.m_20270_(target) > 6.0f) {
                teleportNear(warden, target, 1.5);
            } else {
                pushToward(warden, target, -1.1, 0.35); // hop back then re-engage next tick
            }
        }
    }

    /**
     * Replace skeleton arrows / ghast fireballs / blaze shots with ki.
     * <p>
     * Skeletons share a global ki-shot cooldown with {@link #skeletonTick}: when the gap
     * is not ready, the vanilla arrow is still cancelled so high-AI bow spam cannot
     * stunlock players with homing blasts.
     */
    public static boolean tryReplaceProjectile(Mob shooter, LivingEntity target) {
        if (shooter == null || target == null || !shooter.m_6084_()) {
            return false;
        }
        long difficulty = MobScaling.difficultyOf(shooter);
        DifficultyTier tier = resolveAbilityTier(shooter, difficulty, EliteSystem.isElite(shooter));
        if (tier.ordinalPower() < DifficultyTier.AWAKENED.ordinalPower()) {
            return false;
        }
        if (shooter instanceof AbstractSkeleton) {
            CompoundTag tag = PersistentDataAccess.get(shooter);
            long age = shooter.f_19797_;
            if (!skeletonKiReady(shooter, tag, age, tier)) {
                // Eat the arrow — do not fall back to unlimited vanilla/ki hybrid spam.
                return true;
            }
            boolean burning = tier.ordinalPower() >= DifficultyTier.GOD.ordinalPower();
            boolean laserOk = tier.ordinalPower() >= DifficultyTier.ELITE.ordinalPower()
                    && age - tag.m_128454_("dmz_ad_ki_laser") >= kitCd(shooter, 55L);
            boolean fired = false;
            if (laserOk && ThreadLocalRandom.current().nextDouble() < 0.28) {
                fired = KiAttackHelper.fireKiLaser(shooter, target, tier);
                if (fired) {
                    markSkeletonKiShot(tag, age, "dmz_ad_ki_laser");
                }
            }
            if (!fired) {
                fired = KiAttackHelper.fireKiBlast(shooter, target, tier, burning);
                if (fired) {
                    markSkeletonKiShot(tag, age, "dmz_ad_ki_blast");
                }
            }
            // Always suppress the vanilla arrow once evolved — rate limit via ki_shot gap.
            return true;
        }
        if (shooter instanceof Blaze) {
            CompoundTag tag = PersistentDataAccess.get(shooter);
            long age = shooter.f_19797_;
            if (!blazeKiReady(shooter, tag, age, tier)) {
                // Eat the vanilla fireball — do not stack with kit barrage.
                return true;
            }
            boolean fired = KiAttackHelper.fireKiBlast(shooter, target, tier, true);
            if (fired) {
                markBlazeKiShot(tag, age, "dmz_ad_ki_blast");
            }
            return true;
        }
        if (shooter instanceof Ghast) {
            return KiAttackHelper.fireLargeBlast(shooter, target, tier);
        }
        return false;
    }

    /** Difficulty ladder clamped by the mob's Unlock Tier (more tiers → more abilities). */
    private static DifficultyTier resolveAbilityTier(LivingEntity entity, long difficulty, boolean elite) {
        int unlockTier = 0;
        if (entity != null) {
            CompoundTag tag = PersistentDataAccess.get(entity);
            if (tag.m_128441_("dmz_ad_unlock_tier")) {
                unlockTier = tag.m_128451_("dmz_ad_unlock_tier");
            }
        }
        DifficultyTier tier = UnlockAbilityCaps.resolve(difficulty, unlockTier);
        if (elite && tier.ordinalPower() < DifficultyTier.ELITE.ordinalPower() && unlockTier >= 3) {
            tier = UnlockAbilityCaps.clamp(DifficultyTier.ELITE, unlockTier);
        }
        return tier;
    }
}
