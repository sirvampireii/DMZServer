package com.dbzlegacy.adaptivedifficulty.progression;

import com.dragonminez.common.init.entities.ShadowDummyEntity;
import com.dragonminez.common.quest.QuestService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * 4.7.40: form mastery from getting hit is 25% of DMZ's rate, and 12.5% when the hit comes from a
 * sparring NPC (half the player-vs-player figure). Mastery from dealing hits is unchanged.
 * <p>
 * DMZ grants form and stack-form mastery per hit in {@code StatsEvents.onEntityHit}, in two branches of
 * the same handler: the attacker gets {@code masteryPerHitDealt}, the victim {@code masteryPerHitReceived}.
 * Each branch passes its own player to {@code PotionEffectHelper.applyMasteryGainMultiplier} (Mastery Gain
 * effect, which the global mastery boost also uses, and the mutant bonus); the racial multiplier follows
 * in {@code gainMastery}. All of those multiply, so scaling that call's result for the victim is the same
 * as scaling the base, and every boost stays on top exactly as before.
 * <p>
 * A sparring NPC is one of DMZ's non-lethal spar opponents: a shadow dummy (the master / Popo spar
 * clone or the player's own summoned shadow, both copies of the player's stats) or a quest SPAR partner.
 * LM's SparringSystem pays TP only, so player-vs-player sparring uses the same DMZ grant.
 * <p>
 * The mixin on {@code onEntityHit} calls {@link #begin} at HEAD and {@link #end} at RETURN; the mixin on
 * {@code applyMasteryGainMultiplier} calls {@link #scale}, which only changes the hit victim's grant
 * while that hit is being handled on this thread. A player who hits themselves gets the hit-taken factor
 * on both grants (DMZ passes the same player to both).
 */
public final class HitTakenMastery {
    /** Mastery per hit taken, as a share of DMZ's rate. */
    public static final double HIT_TAKEN_FACTOR = 0.25;
    /** Extra factor on a hit taken from a sparring NPC: half the player-vs-player figure. */
    public static final double NPC_SPAR_FACTOR = 0.5;

    private static final ThreadLocal<Mark> ACTIVE = new ThreadLocal<>();

    private record Mark(Entity victim, double factor) {}

    private HitTakenMastery() {}

    public static void begin(LivingHurtEvent event) {
        ACTIVE.remove();
        if (event == null || !(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        Entity attacker = event.getSource() == null ? null : event.getSource().m_7639_();
        ACTIVE.set(new Mark(victim, factor(attacker)));
    }

    public static void end() {
        ACTIVE.remove();
    }

    /** {@code value} times the hit-taken factor when {@code entity} is the victim of the marked hit; else unchanged. */
    public static double scale(Entity entity, double value) {
        Mark mark = ACTIVE.get();
        return mark != null && entity != null && entity == mark.victim() ? value * mark.factor() : value;
    }

    /** Hit-taken factor for a hit from {@code attacker}: 0.25, or 0.125 from a sparring NPC. */
    public static double factor(Entity attacker) {
        return isSparNpc(attacker) ? HIT_TAKEN_FACTOR * NPC_SPAR_FACTOR : HIT_TAKEN_FACTOR;
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
