package com.dbzlegacy.adaptivedifficulty.progression;

import com.dragonminez.common.init.entities.ShadowDummyEntity;
import com.dragonminez.common.quest.QuestService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Form mastery from getting hit, by attacker (4.7.40; non-PvP split 4.7.42). Of DMZ's rate:
 * <ul>
 *   <li>a player (PvP): 0.25;</li>
 *   <li>any other living attacker (mobs, bosses, God Chamber wave and Whis trial foes, quest kill targets):
 *       0.0625, a quarter of the PvP rate;</li>
 *   <li>a sparring NPC: 0.03125, half of that again.</li>
 * </ul>
 * Mastery from dealing hits is unchanged.
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
    /** Mastery per hit taken from a player, as a share of DMZ's rate. */
    public static final double HIT_TAKEN_FACTOR = 0.25;
    /** Extra factor on a hit taken from anything that is not a player: a quarter of the PvP figure. */
    public static final double NON_PVP_FACTOR = 0.25;
    /** Extra factor on a hit taken from a sparring NPC: half the other non-player figure. */
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

    /**
     * Hit-taken factor for a hit from {@code attacker}: 0.25 from a player, 0.03125 from a sparring NPC, 0.0625
     * from anything else. No attacker never reaches DMZ's grant; it still gets the non-player figure, never PvP's.
     */
    public static double factor(Entity attacker) {
        if (attacker instanceof ServerPlayer) {
            return HIT_TAKEN_FACTOR;
        }
        if (isSparNpc(attacker)) {
            return HIT_TAKEN_FACTOR * NON_PVP_FACTOR * NPC_SPAR_FACTOR;
        }
        return HIT_TAKEN_FACTOR * NON_PVP_FACTOR;
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
