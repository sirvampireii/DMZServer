package com.dbzlegacy.adaptivedifficulty.scaling;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.boss.BossScaling;
import com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache;
import com.dbzlegacy.adaptivedifficulty.calc.PlayerCombatProfile;
import com.dbzlegacy.adaptivedifficulty.calc.ScalingCurves;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.elite.EliteSystem;
import com.dbzlegacy.adaptivedifficulty.evolution.EnemyEvolution;
import com.dbzlegacy.adaptivedifficulty.mutation.MutationSystem;
import com.dbzlegacy.adaptivedifficulty.mutation.MutationType;
import com.dbzlegacy.adaptivedifficulty.tick.ScaledMobTracker;
import com.dbzlegacy.adaptivedifficulty.tier.DifficultyTier;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockAbilityCaps;
import com.dbzlegacy.adaptivedifficulty.util.NearbyPlayers;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import com.dbzlegacy.adaptivedifficulty.util.SystemGate;
import com.dragonminez.common.init.MainDamageTypes;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Scale hostiles near players to a fraction of that player's post-transform /
 * limit-release stats (Unlock Tier %). Spawn only captures bases — nearby /
 * combat retarget paints the real fight stats.
 * <p>
 * Damage is applied two ways for Mohist reliability:
 * <ul>
 *   <li>{@link Attributes#ATTACK_DAMAGE} rewritten on each retarget (melee)</li>
 *   <li>{@link #scaleOutgoingHurt} via Forge {@code LivingHurtEvent} (projectiles / custom hits)</li>
 * </ul>
 */
public final class MobScaling {
    public static final String TAG_DIFFICULTY = "dmz_ad_difficulty";
    public static final String TAG_SCALED = "dmz_ad_scaled";
    public static final String TAG_DMG_MULT = "dmz_ad_dmg_mult";
    public static final String TAG_DMZ_STYLE = "dmz_ad_dmz_mob";
    /** True when ATTACK_DAMAGE was written from our scaler — melee must not be event-multiplied again. */
    public static final String TAG_ATTR_DMG_SCALED = "dmz_ad_attr_dmg";
    /** Fingerprint of the player combat profile this mob was last scaled to. */
    public static final String TAG_PROFILE_SIG = "dmz_ad_profile_sig";
    public static final String TAG_TIER_PERCENT = "dmz_ad_tier_pct";
    /** Stamped class / style / top-stat identity; kit CD uses class + top-1 only. */
    public static final String TAG_COUNTER_CLASS = "dmz_ad_counter_class";
    public static final String TAG_COUNTER_RACE = "dmz_ad_counter_race";
    public static final String TAG_COUNTER_STYLE = "dmz_ad_counter_style";
    public static final String TAG_COUNTER_TOP = "dmz_ad_counter_top";
    public static final String TAG_COUNTER_KIT_CD = "dmz_ad_counter_kit_cd";
    /** Mob is owned by another system (saga/quest/spawner/SDD) — never AD-convert. */
    public static final String TAG_EXEMPT = "dmz_ad_exempt";
    /**
     * GUI-summoned Wither / Warden: owned by its summoner like the End Dragon, so nearby
     * claims, AI kits, and kill rewards leave it alone; its paint comes from the summoner.
     */
    public static final String TAG_SUMMONED_BOSS = "lm_summoned_boss";
    /** Stamped at FinalizeSpawn when {@code MobSpawnType.SPAWNER}. */
    public static final String TAG_FROM_SPAWNER = "dmz_ad_from_spawner";
    /** Slime / magma cube spawned by a parent split — never AD-convert. */
    public static final String TAG_FROM_SLIME_SPLIT = "dmz_ad_slime_split";

    public static final String TAG_BASE_HEALTH = "dmz_ad_base_max_health";
    public static final String TAG_BASE_ATTACK = "dmz_ad_base_attack";
    public static final String TAG_BASE_ARMOR = "dmz_ad_base_armor";
    public static final String TAG_BASE_SPEED = "dmz_ad_base_speed";
    public static final String TAG_BASE_KNOCKBACK = "dmz_ad_base_knockback";

    /** DMZ QuestService / saga spawn markers (Forge persistent data). */
    private static final String[] QUEST_SPAWN_TAGS = {
            "dmz_quest_key",
            "dmz_quest_owner",
            "dmz_quest_objective_index",
            "dmz_saga_id"
    };

    /**
     * Shurui's DMZ Dungeons ({@code shuruis_dmz_dungeons}) Advanced Spawner markers.
     * Stamped on persistent data after finalizeSpawn — must be checked even when
     * the Forge SPAWNER spawn-type path was skipped/failed on Mohist.
     */
    private static final String[] SDD_SPAWNER_TAGS = {
            "sdd_spawner",
            "sdd_boss"
    };

    /**
     * Hot-path cache: entity UUID → last applied player-profile signature.
     */
    private static final Map<UUID, Long> APPLIED_PROFILE = new ConcurrentHashMap<>();

    private MobScaling() {}

    public static long difficultyOf(LivingEntity entity) {
        if (entity == null) {
            return 0L;
        }
        CompoundTag tag = PersistentDataAccess.get(entity);
        return tag.m_128441_(TAG_DIFFICULTY) ? tag.m_128454_(TAG_DIFFICULTY) : 0L;
    }

    /** Stamped unlock-tier ladder percent (stock 0.21–2.00). 0 when unscaled. */
    public static double tierPercentOf(LivingEntity entity) {
        if (entity == null) {
            return 0.0;
        }
        CompoundTag tag = PersistentDataAccess.get(entity);
        if (!tag.m_128441_(TAG_TIER_PERCENT)) {
            return 0.0;
        }
        float pct = tag.m_128457_(TAG_TIER_PERCENT); // getFloat — stamped via putFloat
        if (!(pct > 0.0f) || Float.isNaN(pct) || Float.isInfinite(pct)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(4.0, pct));
    }

    /** Buy-tier id stamped on claim (1–7). 0 when unknown. */
    public static int unlockTierOf(LivingEntity entity) {
        if (entity == null) {
            return 0;
        }
        CompoundTag tag = PersistentDataAccess.get(entity);
        return tag.m_128441_("dmz_ad_unlock_tier") ? Math.max(0, tag.m_128451_("dmz_ad_unlock_tier")) : 0;
    }

    /**
     * Saga/quest (DMZ), vanilla cage spawners, SDD Advanced Spawner mobs, and the
     * Ender Dragon keep their own difficulty — AD must not convert them.
     * <p>
     * All {@link DBSagasEntity} instances are exempt by class (not only quest tags),
     * so transform forms stay protected before {@code dmz_saga_id} is copied on.
     * The Ender Dragon is owned by the End Strength script.
     */
    public static boolean isExemptFromConversion(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        // Saga NPCs / transform forms — class check beats tag timing races.
        if (entity instanceof DBSagasEntity) {
            return true;
        }
        // End Strength script owns the dragon fight — never AD-paint it.
        if (entity instanceof EnderDragon) {
            return true;
        }
        // GUI-summoned Wither / Warden are owned by their summoner, like the dragon.
        if (PersistentDataAccess.get(entity).m_128471_(TAG_SUMMONED_BOSS)) {
            return true;
        }
        if (SlimeSplitGuard.isSplitChild(entity)) {
            return true;
        }
        CompoundTag tag = PersistentDataAccess.get(entity);
        if (tag.m_128471_(TAG_EXEMPT)
                || tag.m_128471_(TAG_FROM_SPAWNER)
                || tag.m_128471_(TAG_FROM_SLIME_SPLIT)) {
            return true;
        }
        if (hasQuestSpawnTags(tag)) {
            return true;
        }
        return hasSddSpawnerMark(tag);
    }

    /** NBT-only slime-split stamp (no pending-window lookup). */
    public static boolean isSlimeSplitTagged(LivingEntity entity) {
        return entity != null && PersistentDataAccess.flag(entity, TAG_FROM_SLIME_SPLIT);
    }

    /** True when Shurui Advanced Spawner / dungeon-boss ownership tags are present. */
    public static boolean hasSddSpawnerMark(LivingEntity entity) {
        return entity != null && hasSddSpawnerMark(PersistentDataAccess.get(entity));
    }

    private static boolean hasSddSpawnerMark(CompoundTag tag) {
        if (tag == null) {
            return false;
        }
        for (String key : SDD_SPAWNER_TAGS) {
            if (tag.m_128441_(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasQuestSpawnTags(CompoundTag tag) {
        if (tag == null) {
            return false;
        }
        for (String key : QUEST_SPAWN_TAGS) {
            if (tag.m_128441_(key)) {
                return true;
            }
        }
        return false;
    }

    /** Stamp a mob from {@link net.minecraft.world.entity.MobSpawnType#SPAWNER}. */
    public static void markFromSpawner(LivingEntity entity) {
        CompoundTag tag = PersistentDataAccess.get(entity);
        if (!PersistentDataAccess.isWritable(tag)) {
            return;
        }
        tag.m_128379_(TAG_FROM_SPAWNER, true);
        tag.m_128379_(TAG_EXEMPT, true);
    }

    /** Stamp a slime/magma cube that came from a parent split. */
    public static void markFromSlimeSplit(LivingEntity entity) {
        CompoundTag tag = PersistentDataAccess.get(entity);
        if (PersistentDataAccess.isWritable(tag)) {
            tag.m_128379_(TAG_FROM_SLIME_SPLIT, true);
            tag.m_128379_(TAG_EXEMPT, true);
            // If nearby scaling already painted this child, roll back to natural size stats.
            if (tag.m_128471_(TAG_SCALED) && tag.m_128441_(TAG_BASE_HEALTH)) {
                revertToBases(entity);
            }
            clearAdBookkeeping(entity, tag);
            tag.m_128379_(TAG_FROM_SLIME_SPLIT, true);
            tag.m_128379_(TAG_EXEMPT, true);
        }
        // Vanilla copies the parent's custom name onto split cubs — strip AD elite/mutation labels.
        try {
            entity.m_6593_(null);
            entity.m_20340_(false); // glowing
        } catch (Throwable ignored) {
        }
        if (entity instanceof Mob mob) {
            ScaledMobTracker.releaseAllClaims(mob);
        }
    }

    /**
     * Persist exempt and drop any AD bookkeeping.
     * <p>
     * Quest/saga/SDD/spawner mobs already have the correct live attributes — never
     * {@link #revertToBases} them. Saga transform forms default to 300 max HP; AD
     * capturing that as a "base" and rolling back after quest HP is applied was
     * wiping Goku SSJ (etc.) down to the entity minimum.
     * Slime-split children may have been painted already — those do revert.
     */
    public static void ensureExempt(LivingEntity entity) {
        CompoundTag tag = PersistentDataAccess.get(entity);
        if (!PersistentDataAccess.isWritable(tag)) {
            return;
        }
        tag.m_128379_(TAG_EXEMPT, true);
        if (tag.m_128471_(TAG_FROM_SLIME_SPLIT)) {
            markFromSlimeSplit(entity);
            return;
        }
        boolean owned = entity instanceof DBSagasEntity
                || entity instanceof EnderDragon
                || tag.m_128471_(TAG_SUMMONED_BOSS)
                || hasQuestSpawnTags(tag)
                || hasSddSpawnerMark(tag)
                || tag.m_128471_(TAG_FROM_SPAWNER);
        if (owned) {
            // Never revertToBases — capture-time bases can be the saga default 300 HP
            // while live attrs already hold quest/form values. Strip AD markers/cosmetics only.
            clearAdCombatPaint(entity, tag);
            clearAdBookkeeping(entity, tag);
            tag.m_128379_(TAG_EXEMPT, true);
            return;
        }
        if (tag.m_128471_(TAG_SCALED)
                && tag.m_128441_(TAG_PROFILE_SIG)
                && tag.m_128454_(TAG_PROFILE_SIG) != 0L) {
            revertToBases(entity);
        }
    }

    /** Drop AD paint markers without touching live attributes. */
    private static void clearAdBookkeeping(LivingEntity entity, CompoundTag tag) {
        tag.m_128473_(TAG_BASE_HEALTH);
        tag.m_128473_(TAG_BASE_ATTACK);
        tag.m_128473_(TAG_BASE_ARMOR);
        tag.m_128473_(TAG_BASE_SPEED);
        tag.m_128473_(TAG_BASE_KNOCKBACK);
        tag.m_128356_(TAG_DIFFICULTY, 0L);
        tag.m_128356_(TAG_PROFILE_SIG, 0L);
        tag.m_128350_(TAG_DMG_MULT, 1.0f);
        tag.m_128379_(TAG_ATTR_DMG_SCALED, false);
        tag.m_128379_(TAG_SCALED, false);
        if (entity != null) {
            APPLIED_PROFILE.remove(entity.m_20148_());
            if (entity instanceof Mob mob) {
                ScaledMobTracker.releaseAllClaims(mob);
            }
        }
    }

    public static void scaleIfNeeded(LivingEntity entity) {
        try {
            if (com.dbzlegacy.adaptivedifficulty.util.DimensionGates.isDisabled(entity)) {
                return;
            }
            // Last-chance split detection — Mohist may join cubs after FinalizeSpawn paint.
            if (SlimeSplitGuard.tryMarkSplitChild(entity)) {
                return;
            }
            if (isExemptFromConversion(entity)) {
                ensureExempt(entity);
                return;
            }
            scaleIfNeededInternal(entity);
        } catch (Throwable t) {
            // Never let scaling abort FinalizeSpawn — that kills natural spawns.
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] mob scaling failed for {}: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    entity == null ? "?" : entity.m_6095_().toString(),
                    t.toString()
            );
        }
    }

    /**
     * Re-scale a hostile to {@code player}'s post-transform / limit-release stats
     * × active Unlock Tier percent. No-op when already matched.
     */
    public static void retargetToPlayer(LivingEntity entity, ServerPlayer player) {
        if (entity == null || player == null || entity.m_9236_().f_46443_) {
            return;
        }
        try {
            if (SlimeSplitGuard.tryMarkSplitChild(entity) || isExemptFromConversion(entity)) {
                ensureExempt(entity);
                return;
            }
            DifficultyConfig cfg = DifficultyConfig.get();
            if (!cfg.enabled || !cfg.enableMobScaling) {
                return;
            }
            if (!SystemGate.participates(player)) {
                return;
            }
            try {
                if (com.dbzlegacy.adaptivedifficulty.progression.end.EndDimensionStrength
                        .hasAliveSummonedDragon(player)) {
                    return;
                }
            } catch (Throwable ignored) {
            }
            if (cfg.scaleHostileOnly && !HostileMobs.isHostile(entity)) {
                return;
            }
            // Cheap gate before profile rebuild / claim work.
            if (DifficultyCache.get(player).activeTier <= 0) {
                return;
            }
            // Hard cap: only a few hostiles per player carry difficulty scaling.
            if (entity instanceof Mob mob && !ScaledMobTracker.tryClaim(player, mob)) {
                return;
            }
            PlayerCombatProfile profile = PlayerCombatProfile.of(player);
            if (!profile.active()) {
                return;
            }
            Long cached = APPLIED_PROFILE.get(entity.m_20148_());
            if (cached != null && cached == profile.signature) {
                return;
            }
            CompoundTag tag = PersistentDataAccess.get(entity);
            if (!PersistentDataAccess.isWritable(tag)) {
                return;
            }
            if (tag.m_128471_(TAG_SCALED)
                    && tag.m_128441_(TAG_BASE_HEALTH)
                    && tag.m_128441_(TAG_PROFILE_SIG)
                    && tag.m_128454_(TAG_PROFILE_SIG) == profile.signature) {
                APPLIED_PROFILE.put(entity.m_20148_(), profile.signature);
                return;
            }
            if (!(entity.m_21223_() > 0.0f) && terminateIfZeroHealth(entity)) {
                return;
            }
            if (!tag.m_128471_(TAG_SCALED) || !tag.m_128441_(TAG_BASE_HEALTH)) {
                scaleIfNeededInternal(entity);
                tag = PersistentDataAccess.get(entity);
            }
            if (!tag.m_128441_(TAG_BASE_HEALTH)) {
                return;
            }
            // One-shot elite / mutation / boss roll from the claim owner's unlock tier.
            rollRarityForClaim(entity, tag, profile.activeTier, cfg, player);
            if (tag.m_128441_(TAG_PROFILE_SIG) && tag.m_128454_(TAG_PROFILE_SIG) == profile.signature) {
                APPLIED_PROFILE.put(entity.m_20148_(), profile.signature);
                return;
            }
            applyForPlayerProfile(entity, tag, profile, cfg);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] retarget scaling failed for {}: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    entity.m_6095_().toString(),
                    t.toString()
            );
        }
    }

    /**
     * If a difficulty-scaled mob is at ≤0 HP but still alive, force it to die.
     * Retarget/HP-ratio math and Mohist edge cases can otherwise leave 0-HP zombies.
     *
     * @return true if the entity was terminated (or already dead/removed)
     */
    public static boolean terminateIfZeroHealth(LivingEntity entity) {
        if (entity == null || entity.m_9236_().f_46443_) {
            return false;
        }
        // EnderDragon.kill() removes without die()/loot — that strips every mod's
    // entities/ender_dragon loot (Iron's Spellbooks, Simply Swords/More, …). Never force-kill.
        if (entity instanceof EnderDragon) {
            return entity.m_213877_() || entity.m_21224_();
        }
        // Fast reject: healthy mobs never need NBT.
        float hp = entity.m_21223_();
        if (hp > 0.0f && !Float.isNaN(hp) && !Float.isInfinite(hp)) {
            return false;
        }
        if (entity.m_213877_()) { // isRemoved
            return true;
        }
        if (entity.m_21224_()) { // isDeadOrDying
            return true;
        }
        CompoundTag tag = PersistentDataAccess.get(entity);
        if (!tag.m_128471_(TAG_SCALED)) {
            return false;
        }
        APPLIED_PROFILE.remove(entity.m_20148_());
        try {
            // Entity.kill() — applies a lethal generic hit and runs normal death.
            entity.m_6074_();
        } catch (Throwable ignored) {
        }
        if (!entity.m_21224_() && !entity.m_213877_()) {
            try {
                DamageSource src = entity.m_269291_().m_269425_(); // generic
                entity.m_21153_(0.0f);
                entity.m_6667_(src); // die
            } catch (Throwable ignored) {
            }
        }
        if (!entity.m_21224_() && !entity.m_213877_()) {
            try {
                entity.m_146870_(); // discard — last resort
            } catch (Throwable ignored) {
            }
        }
        return true;
    }

    private static void scaleIfNeededInternal(LivingEntity entity) {
        DifficultyConfig cfg = DifficultyConfig.get();
        if (!cfg.enabled || !cfg.enableMobScaling || entity == null || entity.m_9236_().f_46443_) {
            return;
        }
        if (isExemptFromConversion(entity)) {
            ensureExempt(entity);
            return;
        }
        CompoundTag tag = PersistentDataAccess.get(entity);
        if (!PersistentDataAccess.isWritable(tag)) {
            return;
        }
        // Already initialized — combat retarget uses {@link #retargetToPlayer}.
        if (tag.m_128471_(TAG_SCALED) && tag.m_128441_(TAG_BASE_HEALTH)) {
            return;
        }

        // Skip non-hostiles when scaleHostileOnly (animals, villagers, etc.).
        if (cfg.scaleHostileOnly) {
            if (!(entity instanceof Mob mob) || !HostileMobs.isHostile(mob)) {
                tag.m_128379_(TAG_SCALED, true);
                tag.m_128356_(TAG_DIFFICULTY, 0L);
                return;
            }
        }

        // Capture natural / current stats before any of our multipliers.
        // Legacy mobs already scaled without bases: reverse the old difficulty mult.
        if (!tag.m_128441_(TAG_BASE_HEALTH)) {
            captureBases(entity, tag, cfg);
        }

        // Detect bosses from captured base HP (not post-scale).
        boolean naturalBoss = BossScaling.isNaturalBoss(entity)
                || (tag.m_128441_(TAG_BASE_HEALTH)
                && tag.m_128459_(TAG_BASE_HEALTH) >= cfg.bossHealthThreshold);

        boolean dmzStyle = shouldApplyDmzStyleExtras(entity, cfg);
        if (dmzStyle) {
            tag.m_128379_(TAG_DMZ_STYLE, true);
        }
        tag.m_128379_(TAG_SCALED, true);
        // Remember natural-boss status; elite/mutation/boss mechanics roll on claim
        // using the claiming player's unlock tier (not area max at spawn).
        if (naturalBoss) {
            tag.m_128379_("dmz_ad_natural_boss", true);
        }
        tag.m_128405_("dmz_ad_unlock_tier", 0);

        // Do NOT bake final fight stats at spawn. Nearby scaler / combat retarget
        // claim one of the player's limited difficulty slots (max 5).
        tag.m_128356_(TAG_DIFFICULTY, 0L);
        tag.m_128356_(TAG_PROFILE_SIG, 0L);
        tag.m_128350_(TAG_DMG_MULT, 1.0f);
        // Prefer an unlocked participant so spawn paint cannot lock onto a
        // spectator / non-participant and then skip scaling forever.
        ServerPlayer nearby = NearbyPlayers.nearestParticipating(entity, Math.max(8.0, cfg.mobScaleRadius));
        if (nearby != null && entity instanceof Mob mob) {
            retargetToPlayer(mob, nearby);
        }
    }

    /** Restore captured natural attributes when a mob loses its difficulty slot. */
    public static void revertToBases(LivingEntity entity) {
        if (entity == null || entity.m_9236_().f_46443_) {
            return;
        }
        try {
            CompoundTag tag = PersistentDataAccess.get(entity);
            if (!tag.m_128441_(TAG_BASE_HEALTH)) {
                // Still strip AD nameplates if somehow painted without bases.
                clearAdCombatPaint(entity, tag);
                return;
            }
            double baseHealth = Math.max(1.0, tag.m_128459_(TAG_BASE_HEALTH));
            setAttributeValue(entity, Attributes.f_22276_, baseHealth);
            float max = entity.m_21233_();
            if (max > 0.0f && entity.m_21223_() > 0.0f) {
                entity.m_21153_(Math.min(max, entity.m_21223_()));
            }
            if (tag.m_128441_(TAG_BASE_ATTACK)) {
                setAttributeValue(entity, Attributes.f_22281_, Math.max(0.0, tag.m_128459_(TAG_BASE_ATTACK)));
            }
            if (tag.m_128441_(TAG_BASE_ARMOR)) {
                setAttributeValue(entity, Attributes.f_22284_, Math.max(0.0, tag.m_128459_(TAG_BASE_ARMOR)));
            }
            if (tag.m_128441_(TAG_BASE_SPEED)) {
                setAttributeValue(entity, Attributes.f_22279_, Math.max(0.0, tag.m_128459_(TAG_BASE_SPEED)));
            }
            if (tag.m_128441_(TAG_BASE_KNOCKBACK)) {
                setAttributeValue(entity, Attributes.f_22278_, Math.max(0.0, tag.m_128459_(TAG_BASE_KNOCKBACK)));
            }
            tag.m_128356_(TAG_DIFFICULTY, 0L);
            tag.m_128356_(TAG_PROFILE_SIG, 0L);
            tag.m_128350_(TAG_DMG_MULT, 1.0f);
            tag.m_128379_(TAG_ATTR_DMG_SCALED, false);
            tag.m_128405_("dmz_ad_unlock_tier", 0);
            clearCounterIdentity(tag);
            APPLIED_PROFILE.remove(entity.m_20148_());
            // Leave-area / personal-off / logout: drop rarity nameplates + identity so the
            // hostile is a normal mob again. Bases + rarity_rolled stay for a clean reclaim.
            clearAdCombatPaint(entity, tag);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] revertToBases failed for {}: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    entity.m_6095_().toString(),
                    t.toString()
            );
        }
    }

    /**
     * Strip elite/mutation/boss/kit cosmetics and rarity identity when a mob loses its
     * difficulty slot. Keeps {@code dmz_ad_rarity_rolled} so leave/re-enter cannot farm
     * new elite rolls; natural bosses are re-stamped on the next claim.
     */
    private static void clearAdCombatPaint(LivingEntity entity, CompoundTag tag) {
        if (entity == null || tag == null || !PersistentDataAccess.isWritable(tag)) {
            return;
        }
        boolean painted = tag.m_128471_(EliteSystem.TAG_ELITE)
                || tag.m_128471_(BossScaling.TAG_BOSS)
                || tag.m_128471_(EnemyEvolution.TAG_EVOLVED)
                || (tag.m_128441_(MutationSystem.TAG_MUTATION)
                && !tag.m_128461_(MutationSystem.TAG_MUTATION).isEmpty())
                || tag.m_128441_("dmz_ad_ability_tier")
                || looksLikeAdNameplate(entity);

        tag.m_128473_(EliteSystem.TAG_ELITE);
        tag.m_128473_("dmz_ad_elite_scale");
        tag.m_128473_(MutationSystem.TAG_MUTATION);
        tag.m_128473_(BossScaling.TAG_BOSS);
        tag.m_128473_(BossScaling.TAG_PHASE);
        tag.m_128473_(EnemyEvolution.TAG_EVOLVED);
        tag.m_128473_("dmz_ad_ability_tier");

        if (!painted) {
            return;
        }
        try {
            if (entity.m_8077_()) {
                entity.m_6593_(null);
            }
            entity.m_20340_(false);
            entity.m_21195_(MobEffects.f_19619_); // remove GLOWING potion if kit left one
        } catch (Throwable ignored) {
            // cosmetic cleanup only
        }
    }

    /** True for AD kit / elite / mutation / boss nameplates we own. */
    private static boolean looksLikeAdNameplate(LivingEntity entity) {
        if (entity == null || !entity.m_8077_()) {
            return false;
        }
        Component name = entity.m_7770_();
        if (name == null) {
            return false;
        }
        String s = name.getString();
        if (s == null || s.isEmpty()) {
            return false;
        }
        if (s.contains("✦") || s.contains("☠") || s.startsWith("§d") || s.contains("§d")) {
            return true;
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

    /**
     * Elite / mutation / boss-mechanics rolls keyed to the claim owner's unlock tier.
     * First roll is sticky against leave/re-enter farming, but when a later claim
     * crosses elite/mutation/boss gates that were unavailable at first roll, those
     * gates get one chance (so T1 first-claim cannot permanently lock out T4+ rarity).
     */
    private static void rollRarityForClaim(
            LivingEntity entity,
            CompoundTag tag,
            int ownerUnlockTier,
            DifficultyConfig cfg,
            ServerPlayer owner) {
        if (entity == null || tag == null || !PersistentDataAccess.isWritable(tag) || cfg == null) {
            return;
        }
        int unlock = Math.max(0, ownerUnlockTier);
        tag.m_128405_("dmz_ad_unlock_tier", unlock);
        long rollSeed = Math.max(1L, unlock) * 10_000L;
        double eliteBonus = owner == null ? 0.0
                : com.dbzlegacy.adaptivedifficulty.team.TeamScaling.rarityBonusPercent(
                owner, com.dbzlegacy.adaptivedifficulty.team.TeamScaling.RarityBonus.ELITE);
        double mutationBonus = owner == null ? 0.0
                : com.dbzlegacy.adaptivedifficulty.team.TeamScaling.rarityBonusPercent(
                owner, com.dbzlegacy.adaptivedifficulty.team.TeamScaling.RarityBonus.MUTATION);
        double bossThresholdBonus = owner == null ? 0.0
                : com.dbzlegacy.adaptivedifficulty.team.TeamScaling.rarityBonusPercent(
                owner, com.dbzlegacy.adaptivedifficulty.team.TeamScaling.RarityBonus.BOSS_THRESHOLD);
        double bossPromotionBonus = owner == null ? 0.0
                : com.dbzlegacy.adaptivedifficulty.team.TeamScaling.rarityBonusPercent(
                owner, com.dbzlegacy.adaptivedifficulty.team.TeamScaling.RarityBonus.BOSS_PROMOTION);
        double bossHpThreshold = com.dbzlegacy.adaptivedifficulty.team.TeamScaling
                .effectiveBossHealthThreshold(cfg, bossThresholdBonus);
        int rolledAt = tag.m_128441_("dmz_ad_rarity_unlock")
                ? Math.max(0, tag.m_128451_("dmz_ad_rarity_unlock"))
                : 0;
        // Leave-area wipe clears AD boss paint; natural bosses need it restored on reclaim
        // even when elite/mutation already rolled once for this mob.
        if (tag.m_128471_("dmz_ad_rarity_rolled")) {
            restoreNaturalBossPaint(entity, tag, unlock, rollSeed, cfg, bossHpThreshold);
            if (unlock > rolledAt) {
                if (unlock >= cfg.eliteMinUnlockTier && rolledAt < cfg.eliteMinUnlockTier) {
                    EliteSystem.maybePromote(entity, rollSeed, eliteBonus);
                }
                if (unlock >= cfg.mutationMinUnlockTier && rolledAt < cfg.mutationMinUnlockTier) {
                    MutationSystem.maybeMutate(entity, rollSeed, mutationBonus);
                }
                tag.m_128405_("dmz_ad_rarity_unlock", unlock);
            }
            return;
        }
        tag.m_128379_("dmz_ad_rarity_rolled", true);
        tag.m_128405_("dmz_ad_rarity_unlock", unlock);
        if (unlock >= cfg.bossMechanicsMinUnlockTier) {
            if (isNaturalBossCandidate(entity, tag, cfg, bossHpThreshold)) {
                BossScaling.markBoss(entity, rollSeed);
            } else if (bossPromotionBonus > 0.0) {
                BossScaling.maybePromote(entity, rollSeed, bossPromotionBonus);
            }
        }
        if (unlock >= cfg.eliteMinUnlockTier) {
            EliteSystem.maybePromote(entity, rollSeed, eliteBonus);
        }
        if (unlock >= cfg.mutationMinUnlockTier) {
            MutationSystem.maybeMutate(entity, rollSeed, mutationBonus);
        }
    }

    private static void restoreNaturalBossPaint(
            LivingEntity entity, CompoundTag tag, int unlock, long rollSeed, DifficultyConfig cfg,
            double bossHpThreshold) {
        if (unlock < cfg.bossMechanicsMinUnlockTier || tag.m_128471_(BossScaling.TAG_BOSS)) {
            return;
        }
        if (isNaturalBossCandidate(entity, tag, cfg, bossHpThreshold)) {
            BossScaling.markBoss(entity, rollSeed);
        }
    }

    /** Natural boss check that does not treat a leftover {@code dmz_ad_boss} flag as proof. */
    private static boolean isNaturalBossCandidate(
            LivingEntity entity, CompoundTag tag, DifficultyConfig cfg) {
        return isNaturalBossCandidate(entity, tag, cfg, cfg.bossHealthThreshold);
    }

    private static boolean isNaturalBossCandidate(
            LivingEntity entity, CompoundTag tag, DifficultyConfig cfg, double healthThreshold) {
        if (tag.m_128471_("dmz_ad_natural_boss")) {
            return true;
        }
        if (tag.m_128441_(TAG_BASE_HEALTH)
                && tag.m_128459_(TAG_BASE_HEALTH) >= healthThreshold) {
            return true;
        }
        // Temporarily ignore AD boss flag so leave-area wipe does not self-qualify.
        boolean hadBoss = tag.m_128471_(BossScaling.TAG_BOSS);
        if (hadBoss) {
            tag.m_128473_(BossScaling.TAG_BOSS);
        }
        try {
            return BossScaling.isNaturalBoss(entity);
        } finally {
            if (hadBoss) {
                tag.m_128379_(BossScaling.TAG_BOSS, true);
            }
        }
    }

    /**
     * Paint mob attributes from a player's transformed/released combat profile.
     */
    public static void applyForPlayerProfile(
            LivingEntity entity, CompoundTag tag, PlayerCombatProfile profile, DifficultyConfig cfg
    ) {
        if (entity == null || tag == null || profile == null || !profile.active()
                || !PersistentDataAccess.isWritable(tag)) {
            return;
        }
        if (!tag.m_128441_(TAG_BASE_HEALTH)) {
            return;
        }

        boolean elite = tag.m_128471_(EliteSystem.TAG_ELITE);
        boolean boss = tag.m_128471_(BossScaling.TAG_BOSS);
        MutationType mutation = MutationType.fromString(tag.m_128461_(MutationSystem.TAG_MUTATION));

        double rarityHealth = 1.0;
        double rarityDamage = 1.0;
        double moveMult = 1.0;
        if (elite) {
            rarityHealth *= Math.max(1.0, cfg.eliteStatMultiplier);
            rarityDamage *= Math.max(1.0, cfg.eliteStatMultiplier);
            moveMult *= 0.92;
        }
        if (boss) {
            rarityHealth *= Math.max(1.0, cfg.bossStatMultiplier);
            rarityDamage *= Math.max(1.0, cfg.bossStatMultiplier);
        }
        if (mutation == MutationType.TITAN_CREEPER) {
            rarityHealth *= 1.75;
        }
        if (mutation == MutationType.BERSERKER_PIGLIN) {
            moveMult *= 1.25;
        }

        double newMaxHealth = profile.targetMobHealth(cfg) * rarityHealth;
        if (cfg.maxScaledHealth > 0.0) {
            newMaxHealth = Math.min(cfg.maxScaledHealth, newMaxHealth);
        }
        if (cfg.maxHealthMultiplier > 1.0) {
            double baseHealth = Math.max(1.0, tag.m_128459_(TAG_BASE_HEALTH));
            newMaxHealth = Math.min(newMaxHealth, baseHealth * cfg.maxHealthMultiplier);
        }
        if (!(newMaxHealth > 0.0) || Double.isNaN(newMaxHealth) || Double.isInfinite(newMaxHealth)) {
            newMaxHealth = Math.max(20.0, tag.m_128459_(TAG_BASE_HEALTH));
        }

        float oldMax = entity.m_21233_();
        float oldHp = entity.m_21223_();
        if (entity.m_21224_() || !(oldHp > 0.0f) || Float.isNaN(oldHp) || Float.isInfinite(oldHp)) {
            terminateIfZeroHealth(entity);
            return;
        }
        double hpRatio = (oldMax > 0.0f && !Float.isNaN(oldMax))
                ? Math.max(0.0, Math.min(1.0, oldHp / oldMax))
                : 1.0;
        if (hpRatio <= 0.0) {
            terminateIfZeroHealth(entity);
            return;
        }

        setAttributeValue(entity, Attributes.f_22276_, newMaxHealth); // MAX_HEALTH
        float appliedMax = entity.m_21233_();
        if (appliedMax > 0.0f && !Float.isNaN(appliedMax) && !Float.isInfinite(appliedMax)) {
            float nextHp = (float) (appliedMax * hpRatio);
            if (!tag.m_128441_(TAG_PROFILE_SIG) && oldHp >= oldMax - 0.5f) {
                nextHp = appliedMax;
            }
            if (nextHp <= 0.0f) {
                terminateIfZeroHealth(entity);
                return;
            }
            entity.m_21153_(Math.min(appliedMax, nextHp));
        }

        double nextAtk = profile.targetMobDamage(cfg) * rarityDamage;
        if (cfg.maxDamageMultiplier > 1.0) {
            double baseAttack = tag.m_128441_(TAG_BASE_ATTACK) ? tag.m_128459_(TAG_BASE_ATTACK) : 1.0;
            nextAtk = Math.min(nextAtk, Math.max(1.0, baseAttack) * cfg.maxDamageMultiplier);
        }
        if (nextAtk > 0.0 && !Double.isNaN(nextAtk) && !Double.isInfinite(nextAtk)) {
            if (setAttributeValue(entity, Attributes.f_22281_, nextAtk)) { // ATTACK_DAMAGE
                tag.m_128379_(TAG_ATTR_DMG_SCALED, true);
            }
        }

        double baseArmor = tag.m_128441_(TAG_BASE_ARMOR) ? Math.max(0.0, tag.m_128459_(TAG_BASE_ARMOR)) : 0.0;
        double nextArmor = baseArmor + profile.targetMobArmor(cfg);
        if (elite) {
            nextArmor += 4.0;
        }
        if (cfg.maxArmorBonus > 0.0) {
            nextArmor = Math.min(cfg.maxArmorBonus, nextArmor);
        }
        if (nextArmor >= 0.0 && !Double.isNaN(nextArmor) && !Double.isInfinite(nextArmor)) {
            setAttributeValue(entity, Attributes.f_22284_, nextArmor); // ARMOR
        }

        double baseSpeed = tag.m_128441_(TAG_BASE_SPEED) ? Math.max(0.0, tag.m_128459_(TAG_BASE_SPEED)) : 0.0;
        if (baseSpeed > 0.0) {
            // Mild unlock-tier chase bump so painted melee close gaps vs ki kits.
            // Elites stay at their rarity moveMult (already 0.92×); bosses unchanged.
            if (!elite && !boss) {
                double tierBump = switch (profile.activeTier) {
                    case 1 -> 1.06;
                    case 2 -> 1.10;
                    case 3 -> 1.14;
                    case 4 -> 1.18;
                    case 5 -> 1.22;
                    case 6 -> 1.26;
                    default -> 1.30;
                };
                moveMult *= tierBump;
            }
            if (cfg.maxMoveMultiplier > 1.0) {
                moveMult = clamp(moveMult, 0.05, cfg.maxMoveMultiplier);
            } else {
                moveMult = clamp(moveMult, 0.05, 2.0);
            }
            double nextSpeed = baseSpeed * Math.max(0.05, moveMult);
            if (nextSpeed > 0.0 && !Double.isNaN(nextSpeed) && !Double.isInfinite(nextSpeed)) {
                setAttributeValue(entity, Attributes.f_22279_, nextSpeed); // MOVEMENT_SPEED
            }
        }

        double baseKnock = tag.m_128441_(TAG_BASE_KNOCKBACK) ? Math.max(0.0, tag.m_128459_(TAG_BASE_KNOCKBACK)) : 0.0;
        if (elite) {
            setAttributeValue(entity, Attributes.f_22278_, Math.min(1.0, baseKnock + 0.6));
        } else if (tag.m_128441_(TAG_BASE_KNOCKBACK)) {
            setAttributeValue(entity, Attributes.f_22278_, baseKnock);
        }

        tag.m_128405_("dmz_ad_unlock_tier", profile.activeTier);
        tag.m_128350_(TAG_TIER_PERCENT, (float) profile.tierPercent);
        // Counter identity — class + top-1 for kit cadence (+ race stamped for debug).
        stampCounterIdentity(tag, profile, cfg);
        // Keep TAG_DIFFICULTY as a readable proxy for AI/evolution curves.
        long proxyDifficulty = Math.max(1L, Math.round(profile.offense * profile.tierPercent));
        tag.m_128356_(TAG_DIFFICULTY, proxyDifficulty);
        // Stamp the unlock-band kit so AI/evo + nameplates match Buy Tier immediately.
        DifficultyTier kit = UnlockAbilityCaps.resolve(proxyDifficulty, profile.activeTier);
        if (kit != DifficultyTier.NONE) {
            tag.m_128359_("dmz_ad_ability_tier", kit.display);
        }
        tag.m_128356_(TAG_PROFILE_SIG, profile.signature);
        // Rarity is already baked into ATTACK_DAMAGE / HP — keep hurt mult at 1 so
        // elite/boss indirect hits do not double-apply rarityDamage.
        tag.m_128350_(TAG_DMG_MULT, 1.0f);
        APPLIED_PROFILE.put(entity.m_20148_(), profile.signature);
        pruneProfileCache();
    }

    private static void stampCounterIdentity(
            CompoundTag tag, PlayerCombatProfile profile, DifficultyConfig cfg
    ) {
        if (tag == null || profile == null || !PersistentDataAccess.isWritable(tag)) {
            return;
        }
        tag.m_128359_(TAG_COUNTER_CLASS, profile.fightingClass == null ? "" : profile.fightingClass);
        tag.m_128359_(TAG_COUNTER_RACE, profile.race == null ? "" : profile.race);
        tag.m_128359_(TAG_COUNTER_STYLE, profile.style == null ? "HYBRID" : profile.style.name());
        tag.m_128359_(TAG_COUNTER_TOP, profile.topStatsLabel());
        float kitCd = (float) profile.kitCooldownScale(cfg == null ? DifficultyConfig.get() : cfg);
        tag.m_128350_(TAG_COUNTER_KIT_CD, kitCd);
    }

    private static void clearCounterIdentity(CompoundTag tag) {
        if (tag == null || !PersistentDataAccess.isWritable(tag)) {
            return;
        }
        tag.m_128473_(TAG_COUNTER_CLASS);
        tag.m_128473_(TAG_COUNTER_RACE);
        tag.m_128473_(TAG_COUNTER_STYLE);
        tag.m_128473_(TAG_COUNTER_TOP);
        tag.m_128473_(TAG_COUNTER_KIT_CD);
    }

    /** Kit cooldown multiplier stamped from the claiming player's counter profile (&lt;1 = faster kits). */
    public static double counterKitCooldownScale(LivingEntity entity) {
        if (entity == null) {
            return 1.0;
        }
        CompoundTag tag = PersistentDataAccess.get(entity);
        if (tag == null || !tag.m_128441_(TAG_COUNTER_KIT_CD)) {
            return 1.0;
        }
        double scale = tag.m_128457_(TAG_COUNTER_KIT_CD);
        if (!(scale > 0.0) || Double.isNaN(scale) || Double.isInfinite(scale)) {
            return 1.0;
        }
        return Math.max(0.55, Math.min(1.0, scale));
    }

    /** Drop applied-signature cache so the next retarget always re-paints. */
    public static void clearAppliedProfiles() {
        APPLIED_PROFILE.clear();
    }

    private static void pruneProfileCache() {
        if (APPLIED_PROFILE.size() <= 4096) {
            return;
        }
        int remove = APPLIED_PROFILE.size() / 2;
        var it = APPLIED_PROFILE.entrySet().iterator();
        while (it.hasNext() && remove-- > 0) {
            it.next();
            it.remove();
        }
    }

    private static void captureBases(LivingEntity entity, CompoundTag tag, DifficultyConfig cfg) {
        double health = attrBase(entity, Attributes.f_22276_, 20.0);
        double attack = attrBase(entity, Attributes.f_22281_, 0.0);
        double armor = attrBase(entity, Attributes.f_22284_, 0.0);
        double speed = attrBase(entity, Attributes.f_22279_, 0.0);
        double knock = attrBase(entity, Attributes.f_22278_, 0.0);

        // Best-effort reverse of a prior one-shot scale (pre-retarget builds).
        if (tag.m_128471_(TAG_SCALED) && tag.m_128441_(TAG_DIFFICULTY)) {
            long oldD = tag.m_128454_(TAG_DIFFICULTY);
            if (oldD > 0L) {
                boolean dmzStyle = tag.m_128471_(TAG_DMZ_STYLE) || shouldApplyDmzStyleExtras(entity, cfg);
                double healthMult = 1.0 + ScalingCurves.healthBonus(oldD, cfg.healthPercentPerDifficulty);
                double dmgMult = 1.0 + ScalingCurves.offenseBonus(oldD, cfg.damagePercentPerDifficulty);
                double moveMult = 1.0 + ((oldD / 100.0) * (cfg.movementPercentPer100Difficulty / 100.0));
                if (dmzStyle) {
                    healthMult += ScalingCurves.healthBonus(oldD, cfg.dmzExtraHealthPercent);
                    dmgMult += ScalingCurves.offenseBonus(oldD, cfg.dmzExtraDamagePercent);
                    dmgMult += ScalingCurves.offenseBonus(oldD, cfg.dmzExtraKiDamagePercent);
                }
                if (cfg.maxHealthMultiplier > 1.0) {
                    healthMult = Math.min(healthMult, cfg.maxHealthMultiplier);
                }
                if (tag.m_128471_(EliteSystem.TAG_ELITE)) {
                    healthMult *= Math.max(1.0, cfg.eliteStatMultiplier);
                    dmgMult *= Math.max(1.0, cfg.eliteStatMultiplier);
                    moveMult *= 0.92;
                }
                if (tag.m_128471_(BossScaling.TAG_BOSS)) {
                    healthMult *= Math.max(1.0, cfg.bossStatMultiplier);
                    dmgMult *= Math.max(1.0, cfg.bossStatMultiplier);
                }
                if (MutationType.TITAN_CREEPER == MutationType.fromString(tag.m_128461_(MutationSystem.TAG_MUTATION))) {
                    healthMult *= 1.75;
                }
                if (MutationType.BERSERKER_PIGLIN == MutationType.fromString(tag.m_128461_(MutationSystem.TAG_MUTATION))) {
                    moveMult *= 1.25;
                }
                if (healthMult > 1.0e-6) {
                    health /= healthMult;
                }
                if (dmgMult > 1.0e-6 && attack > 0.0) {
                    attack /= dmgMult;
                }
                if (moveMult > 1.0e-6 && speed > 0.0) {
                    speed /= moveMult;
                }
            }
        }

        tag.m_128347_(TAG_BASE_HEALTH, Math.max(1.0, health));
        if (attack > 0.0) {
            tag.m_128347_(TAG_BASE_ATTACK, attack);
        }
        tag.m_128347_(TAG_BASE_ARMOR, Math.max(0.0, armor));
        if (speed > 0.0) {
            tag.m_128347_(TAG_BASE_SPEED, speed);
        }
        tag.m_128347_(TAG_BASE_KNOCKBACK, Math.max(0.0, knock));
    }

    private static double attrBase(LivingEntity entity, Attribute attribute, double fallback) {
        AttributeInstance instance = entity.m_21051_(attribute);
        if (instance == null) {
            return fallback;
        }
        double v = instance.m_22115_();
        if (!(v >= 0.0) || Double.isNaN(v) || Double.isInfinite(v)) {
            return fallback;
        }
        return v;
    }

    private static boolean setAttributeValue(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.m_21051_(attribute);
        if (instance == null) {
            return false;
        }
        if (!(value >= 0.0) || Double.isNaN(value) || Double.isInfinite(value)) {
            return false;
        }
        // Skip dirty sync when the value is already effectively set.
        double current = instance.m_22115_();
        if (Math.abs(current - value) < 1.0e-4) {
            return true;
        }
        instance.m_22100_(value);
        return true;
    }

    /** DMZ-style extras apply to every hostile by default. */
    private static boolean shouldApplyDmzStyleExtras(LivingEntity entity, DifficultyConfig cfg) {
        if (isDragonMineZMob(entity)) {
            return true;
        }
        if (!cfg.applyDmzExtrasToAllHostiles) {
            return false;
        }
        return HostileMobs.isHostile(entity);
    }

    public static boolean isDragonMineZMob(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.m_6095_());
        if (id != null && "dragonminez".equals(id.m_135827_())) {
            return true;
        }
        String cn = entity.getClass().getName().toLowerCase();
        return cn.contains("dragonminez") || cn.contains("shurui");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * True when this hostile is actively difficulty-scaled for combat
     * (claimed profile paint), not merely spawn-initialized.
     * <p>
     * Spawn init stamps {@link #TAG_SCALED} with unlock_tier=0 / vanilla ATK while
     * waiting for a claim slot — those shells must NOT count as painted, or the
     * landing safety net treats random world mobs as tier difficulty.
     */
    public static boolean isAdPainted(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        CompoundTag tag = PersistentDataAccess.get(entity);
        if (tag.m_128471_(TAG_ATTR_DMG_SCALED)) {
            return true;
        }
        if (tag.m_128441_(TAG_PROFILE_SIG) && tag.m_128454_(TAG_PROFILE_SIG) != 0L) {
            return true;
        }
        return unlockTierOf(entity) > 0;
    }

    public static float outgoingDamageMultiplier(LivingEntity attacker) {
        if (attacker == null) {
            return 1.0f;
        }
        CompoundTag tag = PersistentDataAccess.get(attacker);
        if (tag.m_128441_(TAG_DMG_MULT)) {
            float cached = tag.m_128457_(TAG_DMG_MULT); // getFloat
            return cached > 0.0f ? cached : 1.0f;
        }
        // Profile-painted mobs stamp TAG_DMG_MULT=1. Missing stamp + ATTR scaled → flat 1
        // (never rebuild legacy CR curve — that exploded fallback kiblasts).
        if (tag.m_128471_(TAG_ATTR_DMG_SCALED) || tag.m_128471_(TAG_SCALED)) {
            if (PersistentDataAccess.isWritable(tag)) {
                tag.m_128350_(TAG_DMG_MULT, 1.0f);
            }
            return 1.0f;
        }
        long d = tag.m_128441_(TAG_DIFFICULTY) ? tag.m_128454_(TAG_DIFFICULTY) : 0L;
        if (d <= 0) {
            return 1.0f;
        }
        // Unpainted legacy path only — soft floor, no elite/boss/DMZ curve stack.
        double mult = 1.0 + Math.min(2.0, ScalingCurves.offenseBonus(d, DifficultyConfig.get().damagePercentPerDifficulty));
        if (PersistentDataAccess.isWritable(tag)) {
            tag.m_128350_(TAG_DMG_MULT, (float) mult);
        }
        return (float) mult;
    }

    /**
     * Scale hurt amount from a hostile attacker.
     * Skips melee when {@link #TAG_ATTR_DMG_SCALED} is set (already on ATTACK_DAMAGE).
     * Still scales projectiles / indirect damage.
     */
    public static float scaleOutgoingHurt(float amount, DamageSource source) {
        if (amount <= 0.0f || source == null) {
            return amount;
        }
        Entity causing = source.m_7639_(); // getEntity
        if (!(causing instanceof LivingEntity attacker) || attacker instanceof Player) {
            return amount;
        }
        // Saga/quest/SDD/spawner: leave their own damage alone.
        if (isExemptFromConversion(attacker)) {
            return amount;
        }
        // Creeper / mob explosions: bake from painted ATTACK_DAMAGE (same idea as kiblasts).
        // Vanilla×tier% still got cancelled to 0 by DMZ DEF on real characters.
        if (source.m_269533_(DamageTypeTags.f_268415_)) { // IS_EXPLOSION
            if (!PersistentDataAccess.flag(attacker, TAG_SCALED) && difficultyOf(attacker) <= 0L) {
                return amount;
            }
            double tierPct = tierPercentOf(attacker);
            if (tierPct <= 0.0 && !PersistentDataAccess.flag(attacker, TAG_ATTR_DMG_SCALED)) {
                return amount;
            }
            return creeperStyleExplosionDamage(amount, attacker, tierPct);
        }
        float mult = outgoingDamageMultiplier(attacker);
        if (mult <= 1.0f) {
            return amount;
        }
        CompoundTag tag = PersistentDataAccess.get(attacker);
        boolean attrScaled = tag.m_128471_(TAG_ATTR_DMG_SCALED);
        Entity direct = source.m_7640_(); // getDirectEntity
        boolean indirect = direct != null && direct != attacker;
        // Melee already boosted via ATTACK_DAMAGE — don't multiply again.
        if (attrScaled && !indirect) {
            return amount;
        }
        // Ki from AD-scaled mobs already bakes ATTACK_DAMAGE into getKiDamage
        // (see KiAttackHelper). A second offense mult made tuning impossible and
        // previously left vanilla-scale kiblasts so tiny DMZ DEF cancelled them to 0.
        if (attrScaled && MainDamageTypes.isKiblastDamage(source)) {
            return amount;
        }
        return amount * mult;
    }

    /**
     * Explosion hurt for AD-painted hostiles.
     * Uses painted melee as the real scale so T1–T2 creepers actually chip DMZ DEF,
     * without returning to the old absolute-proxy 500× curve.
     */
    private static float creeperStyleExplosionDamage(
            float vanillaAmount, LivingEntity attacker, double tierPct
    ) {
        int unlock = unlockTierOf(attacker);
        double atk = readPaintedAttack(attacker);
        // Blast vs painted melee — early tiers slightly above a punch, mid/high harder.
        double ratio = unlock <= 0 ? 1.40
                : unlock <= 2 ? 1.45
                : unlock <= 4 ? 1.80
                : unlock <= 6 ? 2.10
                : 2.40;
        double fromAtk = atk > 1.0 ? atk * ratio : 0.0;
        // Soft vanilla bump as a floor only (never the primary path on DMZ chars).
        double boom = 1.0 + Math.max(0.0, tierPct) * 2.5;
        if (unlock > 0 && unlock <= 2) {
            boom = Math.min(boom, 2.25);
        } else if (unlock == 3) {
            boom = Math.min(boom, 2.75);
        }
        boom = Math.min(4.0, Math.max(1.0, boom));
        double fromVanilla = Math.max(0.0, vanillaAmount) * boom;
        double out = Math.max(fromVanilla, fromAtk);
        // Never soften below a meaningful painted hit once attrs exist.
        if (atk > 1.0) {
            out = Math.max(out, atk * 1.15);
        }
        if (!(out > 0.0) || Double.isNaN(out) || Double.isInfinite(out)) {
            return vanillaAmount;
        }
        return (float) out;
    }

    /**
     * Paint a player-summoned End Dragon with the summoner's Adaptive Difficulty
     * boss profile — same {@link PlayerCombatProfile#targetMobHealth}/{@code Damage}/{@code Armor}
     * formulas as nearby AD mobs, times {@link DifficultyConfig#bossStatMultiplier}.
     * <p>
     * Dragons stay {@link #isExemptFromConversion exempt} from nearby claim scaling;
     * this is the intentional AD path for GUI summons (not the legacy End Strength curve).
     *
     * @return applied max HP, or {@code 0} if paint failed
     */
    public static double applyEndDragonAdProfile(EnderDragon dragon, ServerPlayer summoner) {
        if (dragon == null || summoner == null || dragon.m_9236_().f_46443_) {
            return 0.0;
        }
        try {
            // End Dragon is a deliberate boss fight — slightly above a normal AD boss stamp.
            BossPaint paint = paintBossAdProfile(dragon, summoner, 1.25);
            if (paint == null) {
                return 0.0;
            }
            try {
                dragon.m_6593_(Component.m_237113_(
                        "§5Ender Dragon §8[AD T" + paint.tier
                                + " · " + formatWhole(paint.appliedMax > 0 ? paint.appliedMax : paint.newMaxHealth)
                                + " HP · ATK " + formatWhole(paint.nextAtk) + "]"));
            } catch (Throwable ignored) {
            }
            return paint.appliedMax > 0.0f ? paint.appliedMax : paint.newMaxHealth;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] End Dragon AD paint failed for {}: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    summoner.m_6302_(),
                    t.toString());
            return 0.0;
        }
    }

    /**
     * GUI-summoned Wither / Warden: the same summoner boss profile as the End Dragon (AD boss
     * stats from the summoner's {@link PlayerCombatProfile}, times the boss multiplier), without
     * the dragon's extra pad. Never throws.
     *
     * @return applied max HP, or {@code 0} if paint failed
     */
    public static double applySummonedBossAdProfile(LivingEntity boss, ServerPlayer summoner) {
        if (boss == null || summoner == null || boss.m_9236_().f_46443_) {
            return 0.0;
        }
        try {
            BossPaint paint = paintBossAdProfile(boss, summoner, 1.0);
            if (paint == null) {
                return 0.0;
            }
            return paint.appliedMax > 0.0f ? paint.appliedMax : paint.newMaxHealth;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] summoned boss AD paint failed for {}: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    summoner.m_6302_(),
                    t.getClass().getSimpleName());
            return 0.0;
        }
    }

    private record BossPaint(float appliedMax, double newMaxHealth, double nextAtk, int tier) {}

    /**
     * Paint a summoned boss from its summoner's AD profile: vanilla bases captured once, max HP /
     * attack / armor from {@link PlayerCombatProfile} times {@link DifficultyConfig#bossStatMultiplier}
     * times {@code bossPad}, plus the boss and profile stamps. Null when the summoner is not
     * participating or has no active profile.
     */
    private static BossPaint paintBossAdProfile(LivingEntity boss, ServerPlayer summoner, double bossPad) {
        if (!SystemGate.participates(summoner)) {
            return null;
        }
        DifficultyConfig cfg = DifficultyConfig.get();
        if (cfg == null || !cfg.enabled) {
            return null;
        }
        PlayerCombatProfile profile = PlayerCombatProfile.of(summoner);
        if (profile == null || !profile.active()) {
            return null;
        }
        CompoundTag tag = PersistentDataAccess.get(boss);
        if (!PersistentDataAccess.isWritable(tag)) {
            return null;
        }

        // Capture vanilla bases once so re-paints stay stable.
        if (!tag.m_128441_(TAG_BASE_HEALTH)) {
            tag.m_128347_(TAG_BASE_HEALTH, Math.max(1.0, attrBase(boss, Attributes.f_22276_, 200.0)));
            tag.m_128347_(TAG_BASE_ATTACK, Math.max(0.0, attrBase(boss, Attributes.f_22281_, 0.0)));
            tag.m_128347_(TAG_BASE_ARMOR, Math.max(0.0, attrBase(boss, Attributes.f_22284_, 0.0)));
            tag.m_128347_(TAG_BASE_SPEED, Math.max(0.0, attrBase(boss, Attributes.f_22279_, 0.0)));
            tag.m_128347_(TAG_BASE_KNOCKBACK, Math.max(0.0, attrBase(boss, Attributes.f_22278_, 0.0)));
        }

        long prevSig = tag.m_128441_(TAG_PROFILE_SIG) ? tag.m_128454_(TAG_PROFILE_SIG) : Long.MIN_VALUE;
        boolean sameSig = prevSig != Long.MIN_VALUE && prevSig == profile.signature && tag.m_128471_(TAG_SCALED);
        float oldMax = boss.m_21233_();
        float oldHp = boss.m_21223_();
        double hpRatio = (sameSig && oldMax > 20.0f && oldHp > 0.0f)
                ? Math.max(0.0, Math.min(1.0, oldHp / oldMax))
                : (tag.m_128471_(TAG_SCALED) && oldMax > 20.0f && oldHp > 0.0f
                ? Math.max(0.0, Math.min(1.0, oldHp / oldMax))
                : 1.0);

        double bossMul = Math.max(1.0, cfg.bossStatMultiplier);
        double newMaxHealth = profile.targetMobHealth(cfg) * bossMul * bossPad;
        if (cfg.maxScaledHealth > 0.0) {
            newMaxHealth = Math.min(cfg.maxScaledHealth, newMaxHealth);
        }
        if (!(newMaxHealth > 0.0) || Double.isNaN(newMaxHealth) || Double.isInfinite(newMaxHealth)) {
            newMaxHealth = Math.max(200.0, tag.m_128459_(TAG_BASE_HEALTH));
        }

        setAttributeValue(boss, Attributes.f_22276_, newMaxHealth); // MAX_HEALTH
        float appliedMax = boss.m_21233_();
        if (appliedMax > 0.0f && !Float.isNaN(appliedMax) && !Float.isInfinite(appliedMax)) {
            float nextHp = (float) (appliedMax * hpRatio);
            if (!tag.m_128471_(TAG_SCALED) || oldHp >= oldMax - 0.5f) {
                nextHp = appliedMax;
            }
            if (nextHp > 0.0f) {
                boss.m_21153_(Math.min(appliedMax, Math.max(1.0f, nextHp)));
            }
        }

        double nextAtk = profile.targetMobDamage(cfg) * bossMul * bossPad;
        if (nextAtk > 0.0 && !Double.isNaN(nextAtk) && !Double.isInfinite(nextAtk)) {
            if (setAttributeValue(boss, Attributes.f_22281_, nextAtk)) { // ATTACK_DAMAGE
                tag.m_128379_(TAG_ATTR_DMG_SCALED, true);
            }
        }

        double baseArmor = Math.max(0.0, tag.m_128459_(TAG_BASE_ARMOR));
        double nextArmor = baseArmor + profile.targetMobArmor(cfg) * bossMul;
        if (cfg.maxArmorBonus > 0.0) {
            nextArmor = Math.min(cfg.maxArmorBonus, nextArmor);
        }
        if (nextArmor >= 0.0 && !Double.isNaN(nextArmor) && !Double.isInfinite(nextArmor)) {
            setAttributeValue(boss, Attributes.f_22284_, nextArmor); // ARMOR
        }

        tag.m_128379_(TAG_SCALED, true);
        tag.m_128379_(TAG_DMZ_STYLE, true);
        tag.m_128379_(BossScaling.TAG_BOSS, true);
        tag.m_128405_("dmz_ad_unlock_tier", profile.activeTier);
        tag.m_128350_(TAG_TIER_PERCENT, (float) profile.tierPercent);
        stampCounterIdentity(tag, profile, cfg);
        long proxyDifficulty = Math.max(1L, Math.round(profile.offense * profile.tierPercent));
        tag.m_128356_(TAG_DIFFICULTY, proxyDifficulty);
        tag.m_128350_(TAG_DMG_MULT, 1.0f);
        tag.m_128356_(TAG_PROFILE_SIG, profile.signature);
        APPLIED_PROFILE.put(boss.m_20148_(), profile.signature);

        return new BossPaint(appliedMax, newMaxHealth, nextAtk, profile.activeTier);
    }

    private static String formatWhole(double v) {
        if (!(v >= 0) || Double.isNaN(v) || Double.isInfinite(v)) {
            return "0";
        }
        long n = Math.round(v);
        return Long.toString(n);
    }

    private static double readPaintedAttack(LivingEntity attacker) {
        if (attacker == null) {
            return 0.0;
        }
        try {
            var inst = attacker.m_21051_(Attributes.f_22281_); // ATTACK_DAMAGE
            if (inst != null) {
                double v = inst.m_22135_(); // getValue
                if (v > 1.0 && !Double.isNaN(v) && !Double.isInfinite(v)) {
                    return v;
                }
            }
        } catch (Throwable ignored) {
        }
        return 0.0;
    }
}
