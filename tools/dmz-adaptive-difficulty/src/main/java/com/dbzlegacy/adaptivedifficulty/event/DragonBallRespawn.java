package com.dbzlegacy.adaptivedifficulty.event;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.dragonball.DragonBallSetDefinition;
import com.dragonminez.server.events.DragonBallsHandler;
import com.dragonminez.server.world.data.DragonBallSavedData;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Always a set to find (4.7.35). When the last ball of a set leaves the world by a pickup,
 * a fresh set of that kind is scattered three minutes later at new random spots (DMZ's own
 * wish scatter, which only places the stars that are missing). Earth and Namek run apart.
 *
 * <p>Only a pickup arms the timer, plus one check at boot for a set that is already gone.
 * A ball that comes back first (placed again, a lost-item respawn, a wish scatter, the admin
 * command) cancels it. No periodic top-up: DMZ's own respawns stay the other triggers.
 */
public final class DragonBallRespawn {
    public static final int DELAY_TICKS = 3 * 60 * 20;
    private static final Map<String, Integer> DUE = new ConcurrentHashMap<>();
    private static volatile boolean bootChecked;

    private DragonBallRespawn() {}

    /** Call after a player took a ball of {@code setId} (radar take or item pickup). */
    public static void afterPickup(MinecraftServer server, String setId) {
        if (server == null || setId == null || DUE.containsKey(setId)) {
            return;
        }
        DragonBallSetDefinition definition = DragonBallDefinitions.getBallSet(setId);
        if (definition == null || !definition.isNaturalSpawn() || inWorld(server, definition) > 0) {
            return;
        }
        arm(server, setId, "the last ball was picked up");
    }

    /** Server pulse, once a second. */
    public static void pulse(MinecraftServer server, int tick) {
        if (server == null) {
            return;
        }
        if (!bootChecked) {
            bootChecked = true;
            for (DragonBallSetDefinition definition : DragonBallDefinitions.getBallSets()) {
                if (definition.isNaturalSpawn() && firstSpawnDone(server, definition)
                        && inWorld(server, definition) == 0 && !summonActive(server, definition.getId())) {
                    arm(server, definition.getId(), "no ball of it is in the world at boot");
                }
            }
        }
        for (Map.Entry<String, Integer> entry : DUE.entrySet()) {
            String setId = entry.getKey();
            DragonBallSetDefinition definition = DragonBallDefinitions.getBallSet(setId);
            if (definition == null) {
                DUE.remove(setId);
                continue;
            }
            if (inWorld(server, definition) > 0) {
                DUE.remove(setId);
                AdaptiveDifficultyMod.LOGGER.info("[{}] Dragon Ball respawn for set {} cancelled: a ball is back in the world",
                        AdaptiveDifficultyMod.MOD_ID, setId);
                continue;
            }
            if (tick < entry.getValue()) {
                continue;
            }
            DUE.remove(setId);
            spawn(server, definition);
        }
    }

    /** Forget timers when the server stops (a restart arms fresh ones at boot). */
    public static void reset() {
        DUE.clear();
        bootChecked = false;
    }

    /** Seconds until the set respawns, or -1 when no timer runs. */
    public static int secondsLeft(MinecraftServer server, String setId) {
        Integer due = DUE.get(setId);
        return due == null || server == null ? -1 : Math.max(0, (due - server.m_129921_() + 19) / 20);
    }

    private static void arm(MinecraftServer server, String setId, String why) {
        DUE.put(setId, server.m_129921_() + DELAY_TICKS);
        AdaptiveDifficultyMod.LOGGER.info("[{}] Dragon Ball set {} respawns in 3 minutes: {}",
                AdaptiveDifficultyMod.MOD_ID, setId, why);
    }

    private static void spawn(MinecraftServer server, DragonBallSetDefinition definition) {
        String setId = definition.getId();
        try {
            if (!ConfigManager.getServerConfig().getWorldGen().getGenerateDragonBalls()) {
                return;
            }
        } catch (Throwable ignored) {
        }
        if (summonActive(server, setId)) {
            // The dragon is up; DMZ scatters this set when the wish ends.
            return;
        }
        ServerLevel home = DragonBallsHandler.getHomeLevel(server, definition);
        if (home == null) {
            return;
        }
        DragonBallsHandler.scatterDragonBalls(home, setId);
        int placed = inWorld(server, definition);
        AdaptiveDifficultyMod.LOGGER.info("[{}] Dragon Ball set {} respawned at new spots in {} ({} of {} balls tracked)",
                AdaptiveDifficultyMod.MOD_ID, setId, home.m_46472_().m_135782_(), placed, definition.getStars().size());
    }

    /** Placed + pending balls of the set across every level (a ball can be placed outside its home). */
    static int inWorld(MinecraftServer server, DragonBallSetDefinition definition) {
        int count = 0;
        for (ServerLevel level : server.m_129785_()) {
            DragonBallSavedData data = DragonBallSavedData.get(level);
            for (int star : definition.getStars()) {
                count += data.getTrackedCount(definition.getId(), star);
            }
        }
        return count;
    }

    private static boolean firstSpawnDone(MinecraftServer server, DragonBallSetDefinition definition) {
        ServerLevel home = DragonBallsHandler.getHomeLevel(server, definition);
        return home != null && DragonBallSavedData.get(home).isFirstSpawnComplete(definition.getId());
    }

    private static boolean summonActive(MinecraftServer server, String setId) {
        for (ServerLevel level : server.m_129785_()) {
            for (DragonBallSavedData.Summon summon : DragonBallSavedData.get(level).getSummons().values()) {
                if (setId.equals(summon.setId())) {
                    return true;
                }
            }
        }
        return false;
    }
}
