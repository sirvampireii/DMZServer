package com.dbzlegacy.adaptivedifficulty;

import com.dbzlegacy.adaptivedifficulty.command.CharacterCommands;
import com.dbzlegacy.adaptivedifficulty.command.DifficultyCommands;
import com.dbzlegacy.adaptivedifficulty.command.RivalCommands;
import com.dbzlegacy.adaptivedifficulty.command.MechanicsCommands;
import com.dbzlegacy.adaptivedifficulty.command.ProgressionCommands;
import com.dbzlegacy.adaptivedifficulty.command.SparCommands;
import com.dbzlegacy.adaptivedifficulty.command.TerminalCommands;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy;
import com.dbzlegacy.adaptivedifficulty.event.DifficultyEvents;
import com.dbzlegacy.adaptivedifficulty.event.FallDamageDiag;
import com.dbzlegacy.adaptivedifficulty.scaling.AttributeLimits;
import com.dbzlegacy.adaptivedifficulty.team.TeamScaling;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * LegacyMechanics — Forge entrypoint (class AdaptiveDifficultyMod kept for GUI reflection).
 *
 * <p><b>ABI:</b> Java package {@code com.dbzlegacy.adaptivedifficulty} and class
 * {@code AdaptiveDifficultyMod} must stay stable for Mohist/Bukkit bridges when used.
 * Player NBT root {@code dmz_adaptive_difficulty} and mob tags {@code dmz_ad_*} are unchanged.
 */
@Mod(AdaptiveDifficultyMod.MOD_ID)
public final class AdaptiveDifficultyMod {
    /** Forge modId — lowercase; not used for NBT / GUI class lookup. */
    public static final String MOD_ID = "legacymechanics";
    /** Product line version (was DMZ Adaptive Difficulty 3.3.x). */
    public static final String VERSION = "4.6.225";

    private static boolean dedicatedServer() {
        try {
            Class<?> loader = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            Object dist = loader.getMethod("getDist").invoke(null);
            return dist != null && "DEDICATED_SERVER".equals(String.valueOf(dist));
        } catch (Throwable ignored) {
            return true;
        }
    }
    public static final String DISPLAY_NAME = "LegacyMechanics";
    public static final Logger LOGGER = LogManager.getLogger(DISPLAY_NAME);

    public AdaptiveDifficultyMod() {
        // Server-side only: clients may join without this mod installed.
        ModLoadingContext.get().registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(
                        () -> NetworkConstants.IGNORESERVERONLY,
                        (remoteVersion, isFromServer) -> true
                )
        );

        if (!dedicatedServer()) {
            return;
        }

        DifficultyConfig.load();
        com.dbzlegacy.adaptivedifficulty.progression.race.RaceLockConfig.load();
        com.dbzlegacy.adaptivedifficulty.character.CharacterServicesConfig.load();
        com.dbzlegacy.adaptivedifficulty.quest.SagaResetConfig.load();
        com.dbzlegacy.adaptivedifficulty.kube.hotpath.HotPathConfig.load();
        com.dbzlegacy.adaptivedifficulty.character.CharacterServicesStore.get().load();
        com.dbzlegacy.adaptivedifficulty.progression.classdef.FightingClassCatalog.load();
        // Vanilla max_health 1024 / armor 30 / attack-damage 2048 would silently hard-cap scaling.
        AttributeLimits.uncapOffenseAttributes();
        // FallDamageDiag before DifficultyEvents so HIGHEST onHurt aligns the pool first.
        MinecraftForge.EVENT_BUS.register(new FallDamageDiag());
        MinecraftForge.EVENT_BUS.register(new DifficultyEvents());
        MinecraftForge.EVENT_BUS.register(com.dbzlegacy.adaptivedifficulty.kube.LmHotPath.INSTANCE);
        MinecraftForge.EVENT_BUS.register(com.dbzlegacy.adaptivedifficulty.kube.ScriptEvents.INSTANCE);
        com.dbzlegacy.adaptivedifficulty.kube.LmHotPath.logReady();
        com.dbzlegacy.adaptivedifficulty.claim.ClaimOverlapGuard.register();
        com.dbzlegacy.adaptivedifficulty.net.gui.LmGuiNetwork.register();
        DifficultyCommands.register();
        RivalCommands.register();
        SparCommands.register();
        MechanicsCommands.register();
        CharacterCommands.register();
        ProgressionCommands.register();
        TerminalCommands.register();
        LOGGER.info(
                "[{}] v{} server-only: Lightman's={}, FTB Teams={}, guiBackend={}, CNPC={}, Progression={}",
                MOD_ID,
                VERSION,
                AncientCoinEconomy.realCoinsAvailable(),
                TeamScaling.ftbAvailable(),
                DifficultyConfig.get().guiBackend,
                noppes.npcs.api.NpcAPI.IsAvailable(),
                DifficultyConfig.get().enableProgression
        );
        LOGGER.info(
                "[{}] personal level-cap mixin: StatsDataMixin via MixinConfigs=legacymechanics.mixins.json",
                MOD_ID
        );
    }
}
