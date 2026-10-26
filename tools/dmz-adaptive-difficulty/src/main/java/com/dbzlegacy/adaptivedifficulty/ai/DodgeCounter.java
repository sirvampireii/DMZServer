package com.dbzlegacy.adaptivedifficulty.ai;

import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.scaling.MobScaling;
import com.dbzlegacy.adaptivedifficulty.tier.DifficultyTier;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * High-tier counter to Iron's Spells dodges. When a difficulty-scaled mob at or above
 * {@code dodgeCounterTier} attacks a player, the player's Evasion and Abyssal Shroud are removed
 * before Iron's Spells checks them for that hit (LM listens to {@link LivingAttackEvent} at HIGH;
 * Iron's dodge handler runs at NORMAL), so the hit connects. A re-cast is stripped again on the
 * next high-tier hit, and a removed effect is not refreshed by Simply More's Viper's Call.
 * <p>
 * Never touches Shield or Scapegoat, never edits Evasion's hit count, and never acts on players
 * hit by unscaled or lower-tier mobs. The effects are looked up by registry name, so there is no
 * compile dependency and nothing runs when Iron's Spells is absent.
 */
public final class DodgeCounter {
    private static final ResourceLocation EVASION_ID = new ResourceLocation("irons_spellbooks", "evasion");
    private static final ResourceLocation SHROUD_ID = new ResourceLocation("irons_spellbooks", "abyssal_shroud");

    private static volatile boolean resolved;
    private static MobEffect evasion;
    private static MobEffect shroud;

    private DodgeCounter() {}

    public static void onAttack(LivingAttackEvent event) {
        DifficultyConfig cfg = DifficultyConfig.get();
        if (event == null || event.isCanceled() || !cfg.enabled || !cfg.enableDodgeCounter) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity causing = event.getSource() == null ? null : event.getSource().m_7639_(); // getEntity
        if (!(causing instanceof Mob mob) || !PersistentDataAccess.flag(mob, MobScaling.TAG_SCALED)) {
            return;
        }
        resolve();
        if (evasion == null && shroud == null) {
            return;
        }
        boolean hasEvasion = evasion != null && player.m_21023_(evasion); // hasEffect
        boolean hasShroud = shroud != null && player.m_21023_(shroud);
        if (!hasEvasion && !hasShroud) {
            return;
        }
        DifficultyTier counter = DifficultyTier.parse(cfg.dodgeCounterTier, DifficultyTier.MASTER);
        DifficultyTier tier = AdaptiveAiSystem.resolveTier(mob, MobScaling.difficultyOf(mob));
        if (tier.ordinalPower() < counter.ordinalPower()) {
            return;
        }
        if (hasEvasion) {
            player.m_21195_(evasion); // removeEffect
        }
        if (hasShroud) {
            player.m_21195_(shroud);
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        try {
            evasion = ForgeRegistries.MOB_EFFECTS.containsKey(EVASION_ID)
                    ? ForgeRegistries.MOB_EFFECTS.getValue(EVASION_ID) : null;
            shroud = ForgeRegistries.MOB_EFFECTS.containsKey(SHROUD_ID)
                    ? ForgeRegistries.MOB_EFFECTS.getValue(SHROUD_ID) : null;
        } catch (Throwable ignored) {
            evasion = null;
            shroud = null;
        }
        resolved = true;
    }
}
