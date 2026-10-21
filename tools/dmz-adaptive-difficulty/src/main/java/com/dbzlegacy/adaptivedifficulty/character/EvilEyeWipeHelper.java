package com.dbzlegacy.adaptivedifficulty.character;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.net.gui.GuiModelSyncPacket;
import com.dbzlegacy.adaptivedifficulty.net.gui.LmGuiNetwork;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Clears a stranded Noea Evil Eye form on characters whose race is no longer demon.
 *
 * <p>The Evil Eye is a Noea overlay, not a DMZ form: its state lives in the player's
 * persistent ForgeData under the {@code DragonBlockNoeaV090} compound
 * ({@code demonEvilEyeForm} / {@code demonEvilEyePower} carry the active stage).
 * LM's race change only calls DMZ's {@code clearActiveForm}/{@code clearActiveStackForm},
 * so the eye is never exited, and its own exit path requires the demon race - which the
 * player just left. A player who changed race in the final form keeps the eye's giant
 * hitbox and reach permanently.
 *
 * <p>Clear-only: {@code demonEvilEye} (owning the eye is legitimate) and the
 * {@code demonEvilEyeLockoutEnd}/{@code demonEvilEyeOverloadEnd} timers are never
 * touched, the stage is never set, and demon-race players are never run. Heal sites
 * call this on race change (new race already committed) and on login, and a heal
 * triggers the normal model-sync resend so other clients drop the old form.
 */
public final class EvilEyeWipeHelper {
    /** Noea's per-player ForgeData compound (Dragon Block Noea). */
    static final String NOEA_TAG = "DragonBlockNoeaV090";
    /** Active eye stage. Non-zero while the form is worn (final form = largest). */
    static final String KEY_FORM = "demonEvilEyeForm";
    /** Eye power charge tied to the worn stage. */
    static final String KEY_POWER = "demonEvilEyePower";
    /** The only race whose exit path can leave the eye legitimately. */
    static final String DEMON_RACE = "demon";

    private EvilEyeWipeHelper() {}

    /**
     * Clear {@code demonEvilEyeForm} + {@code demonEvilEyePower} when the player's race
     * is not demon and an eye stage is active. No-op for the demon race, for a clean
     * (both zero or absent) state, or while character data is missing.
     */
    public static void clearIfNotDemon(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            StatsData data = DmzProgression.stats(player);
            if (data == null) {
                return;
            }
            Character ch = data.getCharacter();
            if (ch == null) {
                return;
            }
            String race = normalizeRace(ch.getRace());
            if (race.isEmpty() || DEMON_RACE.equals(race)) {
                return;
            }
            CompoundTag forge = PersistentDataAccess.get(player);
            if (!forge.m_128441_(NOEA_TAG)) { // no Noea compound - nothing to strand
                return;
            }
            CompoundTag noea = forge.m_128469_(NOEA_TAG); // getCompound
            int form = noea.m_128451_(KEY_FORM); // getInt
            int power = noea.m_128451_(KEY_POWER);
            if (form == 0 && power == 0) {
                return;
            }
            noea.m_128405_(KEY_FORM, 0); // putInt - clear-only, never set a stage
            noea.m_128405_(KEY_POWER, 0);
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] EvilEyeWipe cleared stranded evil eye state for {} (race {})",
                    AdaptiveDifficultyMod.MOD_ID,
                    playerName(player),
                    race);
            // Viewers keep the old form until the authoritative parts land.
            LmGuiNetwork.sendModelSync(player, GuiModelSyncPacket.REASON_CLEAR);
        } catch (Throwable ignored) {
        }
    }

    private static String normalizeRace(String race) {
        return race == null ? "" : race.trim().toLowerCase(Locale.ROOT);
    }

    private static String playerName(ServerPlayer player) {
        try {
            return player.m_7755_().getString();
        } catch (Throwable ignored) {
            return "?";
        }
    }
}
