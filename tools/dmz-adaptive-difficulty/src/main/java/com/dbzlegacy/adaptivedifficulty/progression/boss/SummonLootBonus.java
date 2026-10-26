package com.dbzlegacy.adaptivedifficulty.progression.boss;

import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Tier-scaled loot bonus for paid Difficulty GUI boss summons (End Dragon, Wither, Warden).
 * <p>
 * Each stack the boss actually drops - vanilla or mod-added, after every loot table, loot
 * modifier, and drop handler has run - gets an independent chance at one extra copy:
 * {@code summonLootBonusPerTierPercent} per tier above the boss's minimum tier, capped at 100%.
 * The tier is the summoner's active tier at summon time, stamped once on the boss. Item drops
 * only; XP is never scaled. Applied once per boss (guard tag), never on a later loot pass.
 */
public final class SummonLootBonus {
    /** Summoner's active tier at summon time. First stamp wins. */
    public static final String TAG_TIER = "lm_summon_loot_tier";
    /** Set once the bonus ran, so no later loot pass can add copies again. */
    public static final String TAG_DONE = "lm_summon_loot_bonus_done";

    private SummonLootBonus() {}

    /** Stamp the summon-time tier, keeping any earlier stamp. */
    public static void stampTier(LivingEntity boss, int tier) {
        CompoundTag tag = PersistentDataAccess.get(boss);
        if (PersistentDataAccess.isWritable(tag) && !tag.m_128441_(TAG_TIER)) {
            tag.m_128405_(TAG_TIER, Math.max(0, tier));
        }
    }

    public static int stampedTier(LivingEntity boss) {
        return PersistentDataAccess.getInt(boss, TAG_TIER, -1);
    }

    /** Bonus chance in whole percent for a tier, 0 at or below the boss's minimum tier. */
    public static int percent(int tier, int minTier) {
        int per = Math.max(0, Math.min(100, DifficultyConfig.get().summonLootBonusPerTierPercent));
        return Math.max(0, Math.min(100, (tier - minTier) * per));
    }

    /** "Loot bonus at T6: +50% chance of a bonus copy of each drop" (or the lowest-tier note). */
    public static String describe(int tier, int minTier) {
        int pct = percent(tier, minTier);
        int per = Math.max(0, Math.min(100, DifficultyConfig.get().summonLootBonusPerTierPercent));
        if (per <= 0) {
            return "No loot bonus for summons right now.";
        }
        if (pct <= 0) {
            return "Loot bonus at T" + tier + ": none at this boss's lowest tier (+" + per
                    + "% per tier above T" + minTier + ").";
        }
        return "Loot bonus at T" + tier + ": +" + pct + "% chance of a bonus copy of each drop.";
    }

    /** Extra copies for {@code stacks}: one per stack, each with an independent chance. */
    public static List<ItemStack> rollCopies(Collection<ItemStack> stacks, int pct, RandomSource rng) {
        List<ItemStack> out = new ArrayList<>();
        if (stacks == null || pct <= 0 || rng == null) {
            return out;
        }
        for (ItemStack stack : stacks) {
            if (stack == null || stack.m_41619_()) {
                continue;
            }
            if (pct >= 100 || rng.m_188503_(100) < pct) {
                out.add(stack.m_41777_());
            }
        }
        return out;
    }

    /**
     * Wither / Warden death drops: add bonus copies next to the drops already in the list.
     * Runs once per boss; returns how many copies were added.
     */
    public static int applyToDrops(LivingEntity boss, Collection<ItemEntity> drops, int minTier) {
        if (boss == null || drops == null || drops.isEmpty()) {
            return 0;
        }
        CompoundTag tag = PersistentDataAccess.get(boss);
        if (!PersistentDataAccess.isWritable(tag) || tag.m_128471_(TAG_DONE)) {
            return 0;
        }
        tag.m_128379_(TAG_DONE, true);
        int tier = stampedTier(boss);
        int pct = tier < 0 ? 0 : percent(tier, minTier);
        if (pct <= 0) {
            return 0;
        }
        RandomSource rng = boss.m_217043_();
        List<ItemEntity> extra = new ArrayList<>();
        for (ItemEntity drop : drops) {
            if (drop == null) {
                continue;
            }
            ItemStack stack = drop.m_32055_();
            if (stack == null || stack.m_41619_()) {
                continue;
            }
            if (pct >= 100 || rng.m_188503_(100) < pct) {
                ItemEntity copy = new ItemEntity(
                        drop.m_9236_(), drop.m_20185_(), drop.m_20186_(), drop.m_20189_(), stack.m_41777_());
                copy.m_32060_(); // setDefaultPickUpDelay
                extra.add(copy);
            }
        }
        drops.addAll(extra);
        return extra.size();
    }

    /** Guard for the dragon's single table roll path. */
    public static boolean markDone(LivingEntity boss) {
        CompoundTag tag = PersistentDataAccess.get(boss);
        if (!PersistentDataAccess.isWritable(tag) || tag.m_128471_(TAG_DONE)) {
            return false;
        }
        tag.m_128379_(TAG_DONE, true);
        return true;
    }
}
