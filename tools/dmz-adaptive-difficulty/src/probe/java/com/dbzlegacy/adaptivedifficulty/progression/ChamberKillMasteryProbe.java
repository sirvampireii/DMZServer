package com.dbzlegacy.adaptivedifficulty.progression;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.init.MainDamageTypes;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.server.util.PotionEffectHelper;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * Test-only probe (4.7.45; ships only in the testprobe jar, {@code -Dlm.probe.chamberKill}): per-kill form mastery
 * for DMZ Ultra chamber wave foes. A real (non-fake) ServerPlayer in a real 0.04/hit form of their race (its passive
 * rate only feeds Ultra's payOut, so any form will do), with power
 * release 0 so DMZ's own per-hit grant stays out of the numbers, kills zombies marked as Ultra marks wave foes
 * (persistent-data uuid {@code dmzultra_chamber_enemy}):
 * <ul>
 *   <li>a melee kill grants 25 x 0.04 = 1.0 before boosts (times the racial factor gainMastery applies);</li>
 *   <li>a ki blast kill (MainDamageTypes.kiblast, owner as the cause) and an arrow kill with no cause (owner
 *       found through the projectile) grant the same;</li>
 *   <li>a 0.05/hit form grants 1.25 before boosts;</li>
 *   <li>Mastery Gain multiplies the kill grant by applyMasteryGainMultiplier's factor, as for fighting gain;</li>
 *   <li>a foe that vanishes (discard), a foe killed by nothing (generic kill), a foe killed by a fake player, an
 *       unmarked zombie, and a kill with no active form grant nothing;</li>
 *   <li>a kill at the cap stays at the form's max mastery.</li>
 * </ul>
 * Ultra's payOut and the wave TP path are Ultra code LM does not touch (see the jar audit). Logs
 * "chamber kill probe check ...: OK|WRONG" and a final OK / FAILED line.
 */
public final class ChamberKillMasteryProbe {
    private static final UUID PROBE_ID = UUID.fromString("6c6d2d63-6b69-6c6c-2d70-726f62650001");
    private static final double EPS = 1e-6;
    private static int failed;

    private ChamberKillMasteryProbe() {}

    public static void run(MinecraftServer server) {
        failed = 0;
        try {
            ServerLevel level = server.m_129783_();
            ServerPlayer p = new ServerPlayer(server, level, new GameProfile(PROBE_ID, "lm_kill_probe"));
            Constructor<?> handler = Class.forName("net.minecraftforge.common.util.FakePlayer$FakePlayerNetHandler")
                    .getDeclaredConstructor(MinecraftServer.class, ServerPlayer.class);
            handler.setAccessible(true);
            p.f_8906_ = (ServerGamePacketListenerImpl) handler.newInstance(server, p);
            StatsData data = DmzProgression.stats(p);
            check("DMZ stats attached to the probe player", data != null);
            if (data == null) {
                return;
            }
            data.getStatus().setHasCreatedCharacter(true);
            data.getResources().setPowerRelease(0);
            Character c = data.getCharacter();
            String race = c.getRaceName();
            String[] f04 = findForm(race, 0.04, null);
            check("a 0.04/hit form for race " + race + ": " + (f04 == null ? "none" : f04[0] + "." + f04[1]), f04 != null);
            if (f04 == null) {
                return;
            }
            c.setActiveForm(f04[0], f04[1]);
            double racial = delta(c, f04, () -> c.gainMastery(f04[0], f04[1], 1.0));
            log("racial factor (gainMastery of 1.0)", racial);

            double melee = delta(c, f04, () -> kill(level, p, s -> level.m_269111_().m_269075_(p), true));
            log("melee kill, 0.04/hit", melee);
            check("melee kill grants 25 x 0.04 = 1.0 before boosts", near(melee, 1.0 * racial));

            double ki = delta(c, f04, () -> kill(level, p, s -> {
                Entity blast = MainEntities.KI_BLAST.get().m_20615_(level);
                ((net.minecraft.world.entity.projectile.Projectile) blast).m_5602_(p);
                return MainDamageTypes.kiblast(level, blast, p);
            }, true));
            log("ki blast kill", ki);
            check("ki blast kill (technique, owner as the cause) grants the same 1.0", near(ki, 1.0 * racial));

            double arrow = delta(c, f04, () -> kill(level, p, s -> {
                Arrow a = EntityType.f_20548_.m_20615_(level);
                a.m_5602_(p);
                return MainDamageTypes.kiblast(level, a, null);
            }, true));
            log("arrow kill, no cause, owner through the projectile", arrow);
            check("projectile with no cause: the owner is found and gets 1.0", near(arrow, 1.0 * racial));

            String[] other = findForm(race, -1, f04);
            FormConfig.FormData otherData = other == null ? null : ConfigManager.getForm(race, other[0], other[1]);
            Double otherRate = otherData == null ? null : otherData.getMasteryPerHitDealt();
            boolean patched = false;
            if (other == null) {
                other = f04;
                otherData = ConfigManager.getForm(race, f04[0], f04[1]);
                otherData.setMasteryPerHitDealt(0.05);
                otherRate = 0.05;
                patched = true;
            }
            String[] o = other;
            c.setActiveForm(o[0], o[1]);
            double scaled = delta(c, o, () -> kill(level, p, s -> level.m_269111_().m_269075_(p), true));
            if (patched) {
                otherData.setMasteryPerHitDealt(0.04);
            }
            double racialO = delta(c, o, () -> c.gainMastery(o[0], o[1], 1.0));
            log("kill in " + o[0] + "." + o[1] + " at " + otherRate + "/hit" + (patched ? " (rate set for the probe)" : ""),
                    scaled);
            check("a form at " + otherRate + "/hit grants " + (otherRate * 25.0) + " before boosts",
                    near(scaled, otherRate * 25.0 * racialO));

            c.setActiveForm(f04[0], f04[1]);
            p.m_7292_(new MobEffectInstance(MainEffects.MASTERY_GAIN.get(), 1200, 0));
            double mult = PotionEffectHelper.applyMasteryGainMultiplier(p, 1.0);
            double boosted = delta(c, f04, () -> kill(level, p, s -> level.m_269111_().m_269075_(p), true));
            log("kill with Mastery Gain (multiplier " + mult + ")", boosted);
            check("Mastery Gain multiplies the kill grant like fighting gain (x" + mult + ")",
                    mult > 1.0 && near(boosted, 1.0 * mult * racial));
            p.m_21219_();

            check("a foe that vanishes grants nothing", near(delta(c, f04, () -> {
                Zombie z = foe(level, p, true);
                z.m_146870_();
            }), 0.0));
            check("a foe killed by nothing (generic kill) grants nothing",
                    near(delta(c, f04, () -> kill(level, p, s -> level.m_269111_().m_269425_(), true)), 0.0));
            FakePlayer fake = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "lm_kill_fake"));
            check("a foe killed by a fake player grants nothing (and the fake is not paid either)",
                    near(delta(c, f04, () -> kill(level, p, s -> level.m_269111_().m_269075_(fake), true)), 0.0));
            check("an unmarked mob killed by the player grants nothing",
                    near(delta(c, f04, () -> kill(level, p, s -> level.m_269111_().m_269075_(p), false)), 0.0));

            FormConfig.FormData d04 = ConfigManager.getForm(race, f04[0], f04[1]);
            double max = d04.getMaxMastery() == null ? 100.0 : d04.getMaxMastery();
            c.getFormMasteries().setMastery(f04[0], f04[1], max - 0.25, max);
            kill(level, p, s -> level.m_269111_().m_269075_(p), true);
            double atCap = c.getFormMasteries().getMastery(f04[0], f04[1]);
            log("mastery after a kill at " + (max - 0.25) + " of " + max, atCap);
            check("a kill at the cap stops at the form's max mastery (" + max + ")", near(atCap, max));

            c.clearActiveForm();
            double noForm = 0.0;
            for (Map.Entry<String, Double> e : c.getFormMasteries().entries().entrySet()) {
                noForm -= e.getValue();
            }
            kill(level, p, s -> level.m_269111_().m_269075_(p), true);
            for (Map.Entry<String, Double> e : c.getFormMasteries().entries().entrySet()) {
                noForm += e.getValue();
            }
            check("no active form: nothing is granted", near(noForm, 0.0));
        } catch (Throwable t) {
            failed++;
            AdaptiveDifficultyMod.LOGGER.warn("[{}] chamber kill probe threw: {}", AdaptiveDifficultyMod.MOD_ID, t.toString(), t);
        } finally {
            AdaptiveDifficultyMod.LOGGER.info("[{}] chamber kill probe {}", AdaptiveDifficultyMod.MOD_ID,
                    failed == 0 ? "OK" : "FAILED: " + failed);
        }
    }

    private interface Source {
        DamageSource of(Zombie foe);
    }

    private static Zombie foe(ServerLevel level, ServerPlayer p, boolean marked) {
        Zombie z = EntityType.f_20501_.m_20615_(level);
        z.m_7678_(p.m_20185_() + 2.0, p.m_20186_(), p.m_20189_(), 0.0f, 0.0f);
        if (marked) {
            com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess.get(z).m_128362_(ChamberKillMastery.WAVE_FOE, p.m_20148_());
        }
        return z;
    }

    /** Kills a fresh zombie through LivingEntity.hurt with the given source; checks it died. */
    private static void kill(ServerLevel level, ServerPlayer p, Source source, boolean marked) {
        Zombie z = foe(level, p, marked);
        z.m_6469_(source.of(z), 1.0e9f);
        if (z.m_6084_()) {
            failed++;
            AdaptiveDifficultyMod.LOGGER.info("[{}] chamber kill probe: the foe survived the hit", AdaptiveDifficultyMod.MOD_ID);
        }
    }

    private static double delta(Character c, String[] form, Runnable action) {
        double before = c.getFormMasteries().getMastery(form[0], form[1]);
        action.run();
        return c.getFormMasteries().getMastery(form[0], form[1]) - before;
    }

    /** A form of the race with that per-hit rate (rate &lt; 0: any rate other than {@code not}'s). */
    private static String[] findForm(String race, double rate, String[] not) {
        Map<String, FormConfig> groups = ConfigManager.getAllFormsForRace(race);
        if (groups == null) {
            return null;
        }
        Double notRate = null;
        if (not != null) {
            FormConfig.FormData n = ConfigManager.getForm(race, not[0], not[1]);
            notRate = n == null ? null : n.getMasteryPerHitDealt();
        }
        for (Map.Entry<String, FormConfig> g : groups.entrySet()) {
            if (g.getValue() == null || g.getValue().getForms() == null) {
                continue;
            }
            for (Map.Entry<String, FormConfig.FormData> f : g.getValue().getForms().entrySet()) {
                FormConfig.FormData d = f.getValue();
                Double r = d == null ? null : d.getMasteryPerHitDealt();
                // The passive rate only feeds Ultra's payOut lump sum, never the kill grant.
                if (r == null || r <= 0.0) {
                    continue;
                }
                boolean match = rate >= 0 ? Math.abs(r - rate) < EPS : notRate != null && Math.abs(r - notRate) > EPS;
                if (match) {
                    return new String[] {g.getKey(), f.getKey()};
                }
            }
        }
        return null;
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-4;
    }

    private static void check(String what, boolean ok) {
        if (!ok) {
            failed++;
        }
        AdaptiveDifficultyMod.LOGGER.info("[{}] chamber kill probe check {}: {}", AdaptiveDifficultyMod.MOD_ID, what,
                ok ? "OK" : "WRONG");
    }

    private static void log(String step, double value) {
        AdaptiveDifficultyMod.LOGGER.info("[{}] chamber kill probe {}: {}", AdaptiveDifficultyMod.MOD_ID, step, value);
    }
}
