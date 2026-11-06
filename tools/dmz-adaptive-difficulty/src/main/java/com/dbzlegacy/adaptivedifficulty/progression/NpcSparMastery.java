package com.dbzlegacy.adaptivedifficulty.progression;

import com.dragonminez.common.init.entities.ShadowDummyEntity;
import com.dragonminez.common.quest.QuestService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * 4.7.40: form mastery from sparring an NPC is half the player-vs-player rate.
 * <p>
 * DMZ grants form and stack-form mastery per hit in {@code StatsEvents.onEntityHit}: the attacker
 * gets {@code masteryPerHitDealt}, the victim {@code masteryPerHitReceived}, each passed through
 * {@code PotionEffectHelper.applyMasteryGainMultiplier} (Mastery Gain effect, which the global mastery
 * boost also uses, and the mutant bonus) and then the racial multiplier. All of those multiply, so
 * scaling the base by {@link #NPC_SPAR_FACTOR} keeps every boost on top exactly as before.
 * <p>
 * A sparring NPC is one of DMZ's non-lethal spar opponents: a shadow dummy (the master / Popo spar
 * clone or the player's own summoned shadow, both copies of the player's stats) or a quest SPAR
 * partner. Player vs player, ordinary mobs, and quest kill targets keep the full rate. TP is untouched.
 * <p>
 * The mixin on {@code onEntityHit} calls {@link #begin} at HEAD and {@link #end} at RETURN; the mixin
 * on {@code applyMasteryGainMultiplier} calls {@link #scaleBase}, which only changes a value while a
 * spar-NPC hit is being handled on this thread.
 */
public final class NpcSparMastery {
    /** Mastery per hit against a sparring NPC, as a share of the player-vs-player rate. */
    public static final double NPC_SPAR_FACTOR = 0.5;

    private static final ThreadLocal<Double> ACTIVE = new ThreadLocal<>();

    private NpcSparMastery() {}

    public static void begin(LivingHurtEvent event) {
        ACTIVE.remove();
        if (event == null) {
            return;
        }
        double factor = factor(event.getEntity(), event.getSource() == null ? null : event.getSource().m_7639_());
        if (factor != 1.0) {
            ACTIVE.set(factor);
        }
    }

    public static void end() {
        ACTIVE.remove();
    }

    public static double scaleBase(double base) {
        Double factor = ACTIVE.get();
        return factor == null ? base : base * factor;
    }

    /** {@link #NPC_SPAR_FACTOR} when a player hits a sparring NPC or a sparring NPC hits a player; else 1. */
    public static double factor(Entity victim, Entity attacker) {
        if (attacker instanceof ServerPlayer && isSparNpc(victim)) {
            return NPC_SPAR_FACTOR;
        }
        if (victim instanceof ServerPlayer && isSparNpc(attacker)) {
            return NPC_SPAR_FACTOR;
        }
        return 1.0;
    }

    public static boolean isSparNpc(Entity entity) {
        if (entity == null || entity instanceof ServerPlayer) {
            return false;
        }
        if (entity instanceof ShadowDummyEntity) {
            return true;
        }
        try {
            return QuestService.isSparPartner(entity);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
