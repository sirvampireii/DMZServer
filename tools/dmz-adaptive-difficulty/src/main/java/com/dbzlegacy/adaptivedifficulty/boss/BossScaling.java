package com.dbzlegacy.adaptivedifficulty.boss;

import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.scaling.MobScaling;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockAbilityCaps;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockTier;
import com.dbzlegacy.adaptivedifficulty.util.EntityDisplayNames;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Concept §16 — bosses inherit nearby difficulty + phased combat.
 * Stat bonuses are applied by {@link MobScaling} from base attrs on each retarget.
 */
public final class BossScaling {
    public static final String TAG_BOSS = "dmz_ad_boss";
    public static final String TAG_PHASE = "dmz_ad_boss_phase";

    private BossScaling() {}

    public static boolean isBoss(LivingEntity entity) {
        return entity != null && PersistentDataAccess.get(entity).m_128471_(TAG_BOSS);
    }

    /**
     * True for real bosses before we scale HP.
     * Must NOT use post-scale max-health — that made every high-difficulty mob a "boss".
     */
    public static boolean isNaturalBoss(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        if (PersistentDataAccess.get(entity).m_128471_(TAG_BOSS)) {
            return true;
        }
        if (entity instanceof Warden) {
            return true;
        }
        // Prefer captured base HP when present (retarget-safe).
        CompoundTag tag = PersistentDataAccess.get(entity);
        if (tag.m_128441_(MobScaling.TAG_BASE_HEALTH)) {
            if (tag.m_128459_(MobScaling.TAG_BASE_HEALTH) >= DifficultyConfig.get().bossHealthThreshold) {
                return true;
            }
        } else {
            AttributeInstance health = entity.m_21051_(Attributes.f_22276_);
            if (health != null) {
                double base = health.m_22115_();
                if (base >= DifficultyConfig.get().bossHealthThreshold) {
                    return true;
                }
            }
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.m_6095_());
        if (id != null) {
            String path = id.m_135815_().toLowerCase(); // getPath
            String full = id.toString().toLowerCase();
            for (String needle : DifficultyConfig.get().bossIdContains) {
                if (needle == null || needle.isBlank()) {
                    continue;
                }
                String n = needle.toLowerCase().trim();
                if (path.contains(n) || full.contains(n)) {
                    if ("raid".equals(n) && !(path.contains("boss") || path.contains("raid_boss") || path.contains("raidboss"))) {
                        continue;
                    }
                    return true;
                }
            }
        }
        String cn = entity.getClass().getName().toLowerCase();
        return cn.contains("raidboss") || cn.contains("bossentity") || cn.contains("boss_");
    }

    /** Flag + cosmetics only. Fight stats come from {@link MobScaling} player-profile retarget. */
    public static void markBoss(LivingEntity entity, long difficulty) {
        DifficultyConfig cfg = DifficultyConfig.get();
        if (!cfg.enableBossScaling || entity == null) {
            return;
        }
        CompoundTag tag = PersistentDataAccess.get(entity);
        if (!PersistentDataAccess.isWritable(tag)) {
            return;
        }
        if (tag.m_128471_(TAG_BOSS) && tag.m_128451_(TAG_PHASE) > 0) {
            return;
        }
        tag.m_128379_(TAG_BOSS, true);
        tag.m_128405_(TAG_PHASE, 1);

        int unlock = tag.m_128441_("dmz_ad_unlock_tier")
                ? tag.m_128451_("dmz_ad_unlock_tier")
                : (int) Math.max(0L, Math.min(7L, difficulty / 10_000L));
        UnlockTier unlockTier = UnlockTier.byId(unlock);
        String band = unlockTier != null
                ? unlockTier.display
                : UnlockAbilityCaps.minAbilityTier(unlock).display;
        String typeName = EntityDisplayNames.of(entity);
        entity.m_6593_(Component.m_237113_("§c☠ Boss §4" + typeName + " §7[" + band + "]"));
        entity.m_20340_(true);
    }

    /**
     * Rare team-scaling promotion when a mob is not already a natural boss candidate.
     *
     * @param bonusPercentPoints flat roll % (0 since 4.7.78: rival teams are gone)
     */
    public static void maybePromote(LivingEntity entity, long rollSeed, double bonusPercentPoints) {
        DifficultyConfig cfg = DifficultyConfig.get();
        if (!cfg.enableBossScaling || entity == null || rollSeed <= 0L || bonusPercentPoints <= 0.0) {
            return;
        }
        CompoundTag tag = PersistentDataAccess.get(entity);
        if (!PersistentDataAccess.isWritable(tag) || tag.m_128471_(TAG_BOSS)) {
            return;
        }
        double chance = Math.max(0.0, Math.min(1.0, bonusPercentPoints / 100.0));
        if (chance <= 0.0 || ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        markBoss(entity, rollSeed);
    }

    /** Advance combat phases at HP thresholds (concept warden-style). */
    public static void tickPhases(LivingEntity entity) {
        if (entity == null || !DifficultyConfig.get().enableBossScaling) {
            return;
        }
        CompoundTag tag = PersistentDataAccess.get(entity);
        if (!tag.m_128471_(TAG_BOSS)) {
            return;
        }
        tickPhases(entity, tag);
    }

    /** Prefer this when the caller already verified the boss flag and loaded the tag. */
    public static void tickPhases(LivingEntity entity, CompoundTag tag) {
        if (!DifficultyConfig.get().enableBossScaling || entity == null || tag == null) {
            return;
        }
        float max = entity.m_21233_();
        if (!(max > 0.0f)) {
            return;
        }
        float pct = entity.m_21223_() / max;
        int phase = tag.m_128451_(TAG_PHASE);
        if (pct <= 0.75f && phase < 2) {
            enterPhase(entity, tag, 2);
        } else if (pct <= 0.50f && phase < 3) {
            enterPhase(entity, tag, 3);
        } else if (pct <= 0.25f && phase < 4) {
            enterPhase(entity, tag, 4);
        }
    }

    private static void enterPhase(LivingEntity entity, CompoundTag tag, int phase) {
        tag.m_128405_(TAG_PHASE, phase);
        entity.m_7292_(new MobEffectInstance(MobEffects.f_19600_, 200, Math.min(3, phase - 1), false, true));
        entity.m_7292_(new MobEffectInstance(MobEffects.f_19596_, 200, 1, false, true));
        if (phase >= 3) {
            entity.m_5634_(entity.m_21233_() * 0.1f);
            entity.m_7292_(new MobEffectInstance(MobEffects.f_19606_, 200, 1, false, true));
        }
        if (phase >= 4 && entity instanceof Mob) {
            entity.m_7292_(new MobEffectInstance(MobEffects.f_19605_, 160, 1, false, true));
        }
        String name = entity.m_7770_() != null ? entity.m_7770_().getString() : "Boss";
        int idx = name.indexOf(" §8P");
        if (idx > 0) {
            name = name.substring(0, idx);
        }
        entity.m_6593_(Component.m_237113_(name + " §8P" + phase));
    }
}
