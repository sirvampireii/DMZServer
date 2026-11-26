package com.dbzlegacy.adaptivedifficulty.noea;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.FormMasteries;
import com.dragonminez.common.stats.extras.UsedForms;
import com.dragonminez.common.stats.skills.Skills;
import java.util.ArrayList;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Clears God Ki progress on the same resets that wipe Majin absorption.
 * Lives outside the mixin package so the server classloader can find it.
 *
 * <p>Noea 1.2.0-DMZ-2.1.3 does not store this as a {@code godkienhancement}
 * compound. That string is the stack-form group and the skill id.
 * {@code NoeaGodKiLedgerIdentity} is a string on player persistent data.
 * {@code GodKiVariantLedger} is a static catalog, not per-player state.
 * {@code demonGodTier} is computed from the worn demon form, so it is not a
 * stored key. {@code godforms} is a separate form group and is left alone.
 * Death does not wipe Majin absorption, and it does not wipe God Ki either.
 */
public final class GodKiWipeHelper {
    static final String LEDGER_KEY = "NoeaGodKiLedgerIdentity";
    /** Present in the task notes. Noea does not write this key. Removed if a later build does. */
    static final String ENHANCEMENT_KEY = "godkienhancement";
    static final String GROUP = "godkienhancement";
    static final String SKILL = "godkienhancement";

    private GodKiWipeHelper() {}

    public static void wipe(Player player, String via) {
        if (player == null) {
            return;
        }
        String name = "null";
        try {
            name = player.m_7755_().getString();
        } catch (Throwable ignored) {
            name = "?";
        }
        String where = via == null || via.isEmpty() ? "" : " via " + via;
        AdaptiveDifficultyMod.LOGGER.debug(
                "[{}] wipeGodKi firing for {}{}", AdaptiveDifficultyMod.MOD_ID, name, where);
        try {
            String ledger = removePersistent(player, LEDGER_KEY);
            String enhancement = removePersistent(player, ENHANCEMENT_KEY);
            int skill = clearSkill(player);
            boolean form = clearForms(player);
            boolean mastery = clearMastery(player);
            boolean history = clearHistory(player);
            if ((skill >= 0 || form || mastery || history) && player instanceof ServerPlayer serverPlayer) {
                NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(serverPlayer), serverPlayer);
            }
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] wipeGodKi complete ledger={} enhancement={} skill={} form={} mastery={} history={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    ledger,
                    enhancement,
                    skill,
                    form,
                    mastery,
                    history);
        } catch (Throwable t) {
            try {
                AbsorptionClearLog.failure(t);
            } catch (Throwable ignored) {
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] wipeGodKi failed", AdaptiveDifficultyMod.MOD_ID, t);
            }
        }
    }

    /**
     * Drop only the {@code godkienhancement:*} mastery records — the staff
     * {@code /lm admin prestige godki} reduce path, which keeps the skill and active form
     * intact ({@link #wipe} removes skill, forms, and history too).
     */
    public static void dropEnhancementMastery(Player player) {
        if (player == null) {
            return;
        }
        try {
            boolean changed = clearMastery(player);
            if (changed && player instanceof ServerPlayer serverPlayer) {
                NetworkHandler.sendToTrackingEntityAndSelf(
                        new StatsSyncS2C(serverPlayer), serverPlayer);
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] godki mastery drop failed for {}", AdaptiveDifficultyMod.MOD_ID, t);
        }
    }

    /** Wipe only when character creation is about to replace the current race. */
    public static void wipeIfRaceChanges(StatsData stats, String nextRace) {
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
        try {
            wipe(stats.getPlayer(), "initializeWithRaceAndClass");
        } catch (Throwable t) {
            try {
                AbsorptionClearLog.failure(t);
            } catch (Throwable ignored) {
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] wipeGodKi ifRaceChanges failed", AdaptiveDifficultyMod.MOD_ID, t);
            }
        }
    }

    /** @return {@code absent}, {@code compound}, {@code string}, or {@code other} */
    private static String removePersistent(Player player, String key) {
        CompoundTag tag = PersistentDataAccess.get(player);
        if (!PersistentDataAccess.isWritable(tag) || !tag.m_128441_(key)) {
            return "absent";
        }
        String kind = "other";
        if (tag.m_128425_(key, 10)) {
            kind = "compound";
        } else if (tag.m_128425_(key, 8)) {
            kind = "string";
        }
        tag.m_128473_(key);
        return kind;
    }

    /** @return the skill level before removal, or {@code -1} when the skill was absent */
    private static int clearSkill(Player player) {
        StatsData data = DmzProgression.stats(player);
        if (data == null) {
            return -1;
        }
        Skills skills = data.getSkills();
        if (skills == null) {
            return -1;
        }
        int level = 0;
        boolean present = false;
        try {
            present = skills.hasSkill(SKILL);
            level = Math.max(0, skills.getSkillLevel(SKILL));
        } catch (Throwable ignored) {
        }
        if (!present && level <= 0) {
            return -1;
        }
        skills.removeSkill(SKILL);
        return level;
    }

    private static boolean clearForms(Player player) {
        Character character = DmzProgression.character(player);
        if (character == null) {
            return false;
        }
        boolean cleared = false;
        if (isGroup(character.getActiveStackFormGroup())) {
            character.clearActiveStackForm(player, false);
            cleared = true;
        }
        if (isGroup(character.getSelectedStackFormGroup())) {
            character.setSelectedStackFormGroup("");
            character.setSelectedStackForm("");
            cleared = true;
        }
        if (isGroup(character.getActiveFormGroup())) {
            character.clearActiveForm(player, false);
            cleared = true;
        }
        if (isGroup(character.getSelectedFormGroup())) {
            character.setSelectedFormGroup("");
            character.setSelectedForm("");
            cleared = true;
        }
        return cleared;
    }

    /** Drop {@code godkienhancement:*} mastery keys. A stored 0 still counts as a leftover record. */
    private static boolean clearMastery(Player player) {
        Character character = DmzProgression.character(player);
        if (character == null) {
            return false;
        }
        boolean cleared = false;
        cleared |= dropMasteryGroup(character.getStackFormMasteries());
        cleared |= dropMasteryGroup(character.getFormMasteries());
        return cleared;
    }

    private static boolean dropMasteryGroup(FormMasteries masteries) {
        if (masteries == null) {
            return false;
        }
        CompoundTag tag = masteries.save();
        if (tag == null) {
            return false;
        }
        boolean changed = false;
        String prefix = GROUP + ":";
        for (String key : new ArrayList<>(tag.m_128431_())) {
            if (key != null && key.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                tag.m_128473_(key);
                changed = true;
            }
        }
        if (changed) {
            masteries.load(tag);
        }
        return changed;
    }

    /** Drop the used-form list and the previous-form record for this group. */
    private static boolean clearHistory(Player player) {
        Character character = DmzProgression.character(player);
        if (character == null) {
            return false;
        }
        boolean cleared = false;
        cleared |= dropUsedGroup(character.getStackFormsUsedBefore());
        cleared |= dropUsedGroup(character.getFormsUsedBefore());
        if (isGroup(character.getPreviousStackFormGroup())) {
            character.clearPreviousStackFormRecord();
            cleared = true;
        }
        if (isGroup(character.getPreviousFormGroup())) {
            character.clearPreviousFormRecord();
            cleared = true;
        }
        return cleared;
    }

    private static boolean dropUsedGroup(UsedForms used) {
        if (used == null) {
            return false;
        }
        CompoundTag tag = used.save();
        if (tag == null) {
            return false;
        }
        boolean changed = false;
        for (String key : new ArrayList<>(tag.m_128431_())) {
            if (key != null && GROUP.equalsIgnoreCase(key)) {
                tag.m_128473_(key);
                changed = true;
            }
        }
        if (changed) {
            used.load(tag);
        }
        return changed;
    }

    private static boolean isGroup(String group) {
        return group != null && GROUP.equalsIgnoreCase(group.trim());
    }
}
