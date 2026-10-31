package com.dbzlegacy.adaptivedifficulty.noea;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import net.minecraft.world.entity.player.Player;

/**
 * The Majin absorption bonus wipe, called on every reset (race change, character creation,
 * {@code /dmzstats reset}, Dende).
 * <p>
 * The bonus numbers lived on Noea's player data ({@code MajinAbsorptionService} /
 * {@code V090Data}). Noea is no longer installed, so there is no stored bonus to clear, and
 * calling its classes threw {@code NoClassDefFoundError} on every wipe (4.7.9: the reference is
 * gone). The entry points stay so the wipe mixins keep their single call site.
 * Lives outside the mixin package so the server classloader can find it.
 */
public final class MajinAbsorptionStore {
    private MajinAbsorptionStore() {}

    /** Nothing is stored without Noea; logs at debug so a wipe stays traceable. */
    public static void clear(Player player) {
        String name = "null";
        if (player != null) {
            try {
                name = player.m_7755_().getString();
            } catch (Throwable ignored) {
                name = "?";
            }
        }
        AdaptiveDifficultyMod.LOGGER.debug(
                "[{}] MajinAbsorptionStore.clear() for {}: no absorption bonus is stored (Noea not installed)",
                AdaptiveDifficultyMod.MOD_ID,
                name);
    }

    /** Wipe only when character creation is about to replace the current race. */
    public static void clearIfRaceChanges(StatsData stats, String nextRace) {
        if (stats == null || nextRace == null || nextRace.isBlank()) {
            return;
        }
        String current = "";
        try {
            Character character = stats.getCharacter();
            if (character != null && character.getRace() != null) {
                current = character.getRace();
            }
        } catch (Throwable ignored) {
            return;
        }
        if (current.isBlank() || current.equalsIgnoreCase(nextRace.trim())) {
            return;
        }
        clear(stats.getPlayer());
    }
}
