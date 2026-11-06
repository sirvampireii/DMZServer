package com.dbzlegacy.adaptivedifficulty.progression;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dmzultra.divine.GodChamberRealm;
import com.dmzultra.divine.GodChamberWaves;
import com.dmzultra.divine.TimeChamber;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Test-only probe (4.7.37; ships only in the testprobe jar, {@code -Dlm.probe.chamberCap}): real
 * {@code GodChamberWaves.start} calls on fake players next to Mr. Popo (Time Chamber) and Whis (God
 * Chamber realm). A 30 x 8 training start runs as 10 x 5 with one cap notice; a 10 x 5 start is unchanged
 * with no notice; the God Trial runs 5 x 5 (also when the menu sends larger numbers) with no notice; a
 * trial request in the Time Chamber, which Ultra turns into training, is capped too. Each run is stopped
 * at once. Logs "chamber cap probe check ...: OK|WRONG" and a final OK / FAILED line.
 */
public final class ChamberWaveCapProbe {
    private static int failed;

    private ChamberWaveCapProbe() {}

    public static void run(MinecraftServer server) {
        failed = 0;
        try {
            ServerLevel chamber = server.m_129880_(TimeChamber.DIMENSION);
            ServerLevel realm = server.m_129880_(GodChamberRealm.DIMENSION);
            check("Time Chamber and God Chamber realm are loaded", chamber != null && realm != null);
            if (chamber != null) {
                Entity popo = host(chamber, ForgeRegistries.ENTITY_TYPES.getValue(
                        new ResourceLocation("dragonminez", "master_popo")), null);
                check("Mr. Popo placed", popo != null);
                Run big = start(chamber, new GodChamberWaves.Config(false, 8, 30, 0, 100));
                check("training 30 x 8 runs as 10 waves x 5 foes", big.training() && big.waves == 10 && big.perWave == 5);
                check("training 30 x 8: one cap notice on the action bar",
                        big.capNotices() == 1 && big.said.contains("[bar] Wave count capped at 10. Enemies per wave capped at 5."));
                Run fits = start(chamber, new GodChamberWaves.Config(false, 5, 10, 0, 100));
                check("training 10 x 5 is unchanged, no notice", fits.training() && fits.waves == 10 && fits.perWave == 5
                        && fits.capNotices() == 0);
                Run one = start(chamber, new GodChamberWaves.Config(false, 5, 30, 0, 100));
                check("training 30 x 5: only the wave count is capped", one.training() && one.waves == 10 && one.perWave == 5
                        && one.said.contains("[bar] Wave count capped at 10."));
                Run asTraining = start(chamber, new GodChamberWaves.Config(true, 8, 30, 0, 100));
                check("trial request in the Time Chamber (Ultra makes it training) is capped too",
                        asTraining.training() && asTraining.waves == 10 && asTraining.perWave == 5 && asTraining.capNotices() == 1);
                if (popo != null) {
                    popo.m_146870_();
                }
            }
            if (realm != null) {
                Entity whis = host(realm, EntityType.f_20529_, "Whis");
                check("Whis placed", whis != null);
                Run trial = start(realm, new GodChamberWaves.Config(true, 5, 5, 0, 100));
                check("God Trial runs 5 x 5, no notice", trial.mode == GodChamberWaves.Status.TRIAL
                        && trial.waves == 5 && trial.perWave == 5 && trial.capNotices() == 0);
                Run trialBig = start(realm, new GodChamberWaves.Config(true, 8, 30, 0, 100));
                check("God Trial sent as 30 x 8 still runs 5 x 5, no notice", trialBig.mode == GodChamberWaves.Status.TRIAL
                        && trialBig.waves == 5 && trialBig.perWave == 5 && trialBig.capNotices() == 0);
                if (whis != null) {
                    whis.m_146870_();
                }
            }
        } catch (Throwable t) {
            failed++;
            AdaptiveDifficultyMod.LOGGER.warn("[{}] chamber cap probe threw: {}", AdaptiveDifficultyMod.MOD_ID, t.toString(), t);
        }
        AdaptiveDifficultyMod.LOGGER.info("[{}] chamber cap probe {}", AdaptiveDifficultyMod.MOD_ID,
                failed == 0 ? "OK" : "FAILED: " + failed);
    }

    /** A still, gravity-free host entity at the probe spot (0, 120, 0). */
    private static Entity host(ServerLevel level, EntityType<?> type, String name) {
        if (type == null) {
            return null;
        }
        level.m_6325_(0, 0);
        Entity e = type.m_20615_(level);
        if (e == null) {
            return null;
        }
        e.m_7678_(0.5, 120.0, 0.5, 0.0F, 0.0F);
        e.m_20242_(true);
        if (name != null) {
            e.m_6593_(Component.m_237113_(name));
        }
        return level.m_7967_(e) ? e : null;
    }

    /** One real start for a fresh fake player beside the host; the run is stopped right after. */
    private static Run start(ServerLevel level, GodChamberWaves.Config config) {
        Recorder fp = new Recorder(level, new GameProfile(UUID.randomUUID(), "lm_chamber_probe"));
        fp.m_7678_(0.5, 120.0, 2.5, 0.0F, 0.0F);
        GodChamberWaves.start(fp, config);
        GodChamberWaves.Status status = GodChamberWaves.status(fp);
        Run run = new Run(status == null ? -1 : status.mode(), status == null ? -1 : status.waves(),
                status == null ? -1 : status.perWave(), fp.said);
        GodChamberWaves.stop(fp, false);
        AdaptiveDifficultyMod.LOGGER.info("[{}] chamber cap probe start trial={} {}x{} -> mode {} {}x{}, said {}",
                AdaptiveDifficultyMod.MOD_ID, config.trial(), config.waves(), config.perWave(), run.mode, run.waves,
                run.perWave, run.said);
        return run;
    }

    private static void check(String what, boolean ok) {
        if (!ok) {
            failed++;
        }
        AdaptiveDifficultyMod.LOGGER.info("[{}] chamber cap probe check {}: {}", AdaptiveDifficultyMod.MOD_ID, what,
                ok ? "OK" : "WRONG");
    }

    private record Run(int mode, int waves, int perWave, List<String> said) {
        boolean training() {
            return mode == GodChamberWaves.Status.TRAINING;
        }

        long capNotices() {
            return said.stream().filter(s -> s.contains("capped at")).count();
        }
    }

    /** A fake player that keeps what the game tells it. */
    private static final class Recorder extends FakePlayer {
        final List<String> said = new ArrayList<>();

        Recorder(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public void m_5661_(Component message, boolean actionBar) {
            said.add((actionBar ? "[bar] " : "") + message.getString());
        }
    }
}
