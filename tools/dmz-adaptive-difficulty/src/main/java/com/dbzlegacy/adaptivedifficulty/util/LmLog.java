package com.dbzlegacy.adaptivedifficulty.util;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;

/**
 * Log lines for single events: a player action, a packet, a fight, a staff command.
 * <p>
 * They go to DEBUG (logs/debug.log) unless {@code debugLogging} is on in the LM config, then to
 * INFO like before. Startup lines, warnings, and errors keep using the logger directly.
 */
public final class LmLog {
    private LmLog() {}

    public static boolean debugLogging() {
        try {
            DifficultyConfig cfg = DifficultyConfig.get();
            return cfg != null && cfg.debugLogging;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void event(String message, Object... args) {
        if (debugLogging()) {
            AdaptiveDifficultyMod.LOGGER.info(message, args);
        } else {
            AdaptiveDifficultyMod.LOGGER.debug(message, args);
        }
    }
}
