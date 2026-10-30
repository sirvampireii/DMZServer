package com.dbzlegacy.adaptivedifficulty.character;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dragonminez.common.racial.RacialData;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.BonusStats;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.character.Cooldowns;
import com.dragonminez.common.stats.character.Resources;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Clears racial {@link BonusStats} split stacks when a player leaves the race that earned them.
 *
 * <p>DMZ stores these as prefix keys via {@code addBonusSplit}:
 * {@code Zenkai_N} (Saiyan), {@code Assimilation_N} (Namekian), {@code Absorption_N} (Majin).
 * {@link BonusStats#clearBonusSplit} matches with {@code String.contains}, matching
 * {@code PassiveResetWish} / {@code RacialSkillCommand.resetRacialSkills}.
 */
public final class RacialBonusWipeHelper {
    /** Stats DMZ racial skills boost (RES maps to DEF+STM inside clearBonusSplit). */
    private static final String[] BOOST_STATS = {"STR", "SKP", "RES", "VIT", "PWR", "ENE"};

    private static final String ZENKAI_PREFIX = "Zenkai_";
    private static final String ASSIMILATION_PREFIX = "Assimilation_";
    private static final String ABSORPTION_PREFIX = "Absorption_";
    private static final String ZENKAI_COOLDOWN = "Zenkai";

    private RacialBonusWipeHelper() {}

    /**
     * Strip racial split bonuses earned under {@code oldRace} when moving to {@code newRace}.
     * No-op when staying the same race. Resets {@code racialSkillCount} when anything was cleared.
     */
    public static void wipeLeavingRace(
            Player player, StatsData data, String oldRace, String newRace, String via
    ) {
        if (data == null) {
            return;
        }
        String from = normalizeRace(oldRace);
        String to = normalizeRace(newRace);
        if (from.isEmpty() || from.equals(to)) {
            return;
        }

        // Same call as the bonus-split strip. Strike removal is not gated on
        // removed > 0: a human leaving for Namekian clears no Zenkai splits.
        RacialStrikeWipe.wipe(data, from, to);
        // DMZ 2.2 also records racial uses in RacialData (it never clears them on a race change).
        clearRacialData(data, from, to);

        int removed = 0;
        if ("saiyan".equals(from) && !"saiyan".equals(to)) {
            removed += clearPrefix(data, ZENKAI_PREFIX);
            clearCooldown(data, ZENKAI_COOLDOWN);
        }
        if ("namekian".equals(from) && !"namekian".equals(to)) {
            removed += clearPrefix(data, ASSIMILATION_PREFIX);
        }
        if ("majin".equals(from) && !"majin".equals(to)) {
            removed += clearPrefix(data, ABSORPTION_PREFIX);
        }
        if (removed <= 0) {
            return;
        }
        resetRacialSkillCount(data);
        String name = playerName(player);
        String where = via == null || via.isBlank() ? "" : " via " + via;
        AdaptiveDifficultyMod.LOGGER.debug(
                "[{}] RacialBonusWipe cleared {} split(s) {}->{} for {}{}",
                AdaptiveDifficultyMod.MOD_ID,
                removed,
                from,
                to.isEmpty() ? "?" : to,
                name,
                where);
    }

    /** Character-creation / {@code initializeWithRaceAndClass} path. */
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
        Player player = null;
        try {
            player = stats.getPlayer();
        } catch (Throwable ignored) {
        }
        wipeLeavingRace(player, stats, current, nextRace, "wipeIfRaceChanges");
    }

    /**
     * @return number of matching split entries removed across boost stats
     */
    private static int clearPrefix(StatsData data, String prefix) {
        if (data == null || prefix == null || prefix.isBlank()) {
            return 0;
        }
        BonusStats bonus;
        try {
            bonus = data.getBonusStats();
        } catch (Throwable t) {
            return 0;
        }
        if (bonus == null) {
            return 0;
        }
        int removed = 0;
        for (String stat : BOOST_STATS) {
            try {
                removed += countMatching(bonus, stat, prefix);
                // clearBonusSplit → clearBonus uses String.contains (PassiveResetWish pattern).
                bonus.clearBonusSplit(stat, prefix);
            } catch (Throwable ignored) {
            }
        }
        return removed;
    }

    private static int countMatching(BonusStats bonus, String stat, String prefix) {
        try {
            List<BonusStats.StatBonus> list = bonus.getBonuses(stat);
            if (list == null || list.isEmpty()) {
                return 0;
            }
            int n = 0;
            for (BonusStats.StatBonus entry : list) {
                if (entry != null && entry.name != null && entry.name.contains(prefix)) {
                    n++;
                }
            }
            return n;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * DragonMineZ 2.2 keeps the racial skill record in {@link RacialData}: Zenkai uses, Assimilation
     * and Absorption slots, and the owned bonus names. Clear the part that belongs to the race being
     * left, the same way 2.1.3 cleared {@code racialSkillCount}.
     */
    private static void clearRacialData(StatsData data, String from, String to) {
        RacialData racial;
        try {
            racial = data.getRacialData();
        } catch (Throwable t) {
            return;
        }
        if (racial == null) {
            return;
        }
        try {
            if ("saiyan".equals(from) && !"saiyan".equals(to)) {
                racial.setZenkaiPermanentUses(0);
                racial.setZenkaiReleaseBonus(0.0d);
                racial.setTempZenkaiBuffName("");
                racial.getOwnedBonusNames().removeIf(name -> name != null && name.startsWith(ZENKAI_PREFIX));
            }
            if ("namekian".equals(from) && !"namekian".equals(to)) {
                racial.getAssimilations().clear();
                racial.getOwnedBonusNames().removeIf(name -> name != null && name.startsWith(ASSIMILATION_PREFIX));
            }
            if ("majin".equals(from) && !"majin".equals(to)) {
                racial.getAbsorptions().clear();
                racial.setAbsorptionSlotCounter(0);
                racial.getOwnedBonusNames().removeIf(name -> name != null && name.startsWith(ABSORPTION_PREFIX));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void clearCooldown(StatsData data, String key) {
        try {
            Cooldowns cds = data.getCooldowns();
            if (cds != null && key != null) {
                cds.removeCooldown(key);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void resetRacialSkillCount(StatsData data) {
        try {
            Resources resources = data.getResources();
            if (resources != null) {
                resources.setRacialSkillCount(0);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String normalizeRace(String race) {
        if (race == null || race.isBlank()) {
            return "";
        }
        return race.trim().toLowerCase(Locale.ROOT);
    }

    private static String playerName(Player player) {
        if (player == null) {
            return "null";
        }
        try {
            return player.m_7755_().getString();
        } catch (Throwable ignored) {
            return "?";
        }
    }

    /** Convenience for ServerPlayer call sites. */
    public static void wipeLeavingRace(
            ServerPlayer player, StatsData data, String oldRace, String newRace, String via
    ) {
        wipeLeavingRace((Player) player, data, oldRace, newRace, via);
    }
}
