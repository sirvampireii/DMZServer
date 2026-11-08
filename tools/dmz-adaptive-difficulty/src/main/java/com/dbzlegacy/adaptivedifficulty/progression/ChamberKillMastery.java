package com.dbzlegacy.adaptivedifficulty.progression;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.server.util.PotionEffectHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

/**
 * Form mastery per kill of a DMZ Ultra chamber wave foe (4.7.45). Ultra's {@code GodChamberWaves} spawns the foes
 * of the Whis trial, Whis training, Popo's Time Chamber training, Beerus demands and Angel drills, and marks each
 * with its owner's uuid under {@link #WAVE_FOE} in its persistent data. Ultra pays TP per foe down and mastery
 * only as a lump sum at session end ({@code payOut}, forms with a passive rate only); both are unchanged.
 * <p>
 * A kill is worth {@link #KILL_HITS} landed hits of the active form's {@code masteryPerHitDealt} (1.0 for a
 * 0.04/hit form), routed like DMZ's dealt-hit grant in {@code StatsEvents.onEntityHit}:
 * {@code PotionEffectHelper.applyMasteryGainMultiplier} (Mastery Gain effect, global boost, mutant bonus), then
 * {@code Character.gainMastery} (racial multiplier; clamps at the form's max mastery). No LM factor applies: it is
 * a dealt-side grant, and {@link HitTakenMastery} only scales the victim of a marked hit.
 * <p>
 * Only a death with a player killer counts: a foe that vanishes or despawns never dies, so it never reaches the
 * death event. Main form only, with Ultra's payOut guard (group, form and form data present).
 */
public final class ChamberKillMastery {
    /** Ultra's {@code GodChamberWaves.TAG}: a uuid in the foe's persistent data. */
    public static final String WAVE_FOE = "dmzultra_chamber_enemy";
    /** A wave foe kill is worth this many landed hits of the active form's per-hit rate. */
    public static final double KILL_HITS = 25.0;

    private ChamberKillMastery() {}

    /** Lowest-priority, uncancelled {@code LivingDeathEvent}: grants the killer's form mastery for a wave foe. */
    public static void onDeath(LivingDeathEvent event) {
        try {
            LivingEntity dead = event == null ? null : event.getEntity();
            if (dead == null || dead.m_9236_().f_46443_ || !PersistentDataAccess.get(dead).m_128403_(WAVE_FOE)) {
                return;
            }
            ServerPlayer killer = killer(event.getSource());
            if (killer != null) {
                grant(killer);
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] chamber kill mastery failed: {}", AdaptiveDifficultyMod.MOD_ID,
                    t.toString());
        }
    }

    /**
     * The player behind a death: the source's causing entity (DMZ ki attacks name their owner there), else the
     * owner of the projectile or summon that dealt it. Fake players never count.
     */
    public static ServerPlayer killer(DamageSource source) {
        if (source == null) {
            return null;
        }
        Entity cause = source.m_7639_();
        ServerPlayer player = owner(cause);
        if (player == null) {
            player = owner(source.m_7640_());
        }
        return player instanceof FakePlayer ? null : player;
    }

    private static ServerPlayer owner(Entity entity) {
        for (int i = 0; i < 4 && entity != null; i++) {
            if (entity instanceof ServerPlayer player) {
                return player;
            }
            if (entity instanceof Projectile projectile) {
                entity = projectile.m_19749_();
            } else if (entity instanceof OwnableEntity ownable) {
                entity = ownable.m_269323_();
            } else {
                return null;
            }
        }
        return null;
    }

    /** Grants the kill to the active form. Returns the amount passed to gainMastery (0 when nothing was granted). */
    public static double grant(ServerPlayer killer) {
        StatsData data = DmzProgression.stats(killer);
        if (data == null || !data.getStatus().isHasCreatedCharacter()) {
            return 0.0;
        }
        Character character = data.getCharacter();
        String group = character.getActiveFormGroup();
        String form = character.getActiveForm();
        FormConfig.FormData formData = character.getActiveFormData();
        if (group == null || group.isEmpty() || form == null || form.isEmpty() || formData == null) {
            return 0.0;
        }
        Double perHit = formData.getMasteryPerHitDealt();
        if (perHit == null || perHit <= 0.0) {
            return 0.0;
        }
        double value = PotionEffectHelper.applyMasteryGainMultiplier(killer, perHit * KILL_HITS);
        character.gainMastery(group, form, value);
        NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(killer), killer);
        return value;
    }
}
