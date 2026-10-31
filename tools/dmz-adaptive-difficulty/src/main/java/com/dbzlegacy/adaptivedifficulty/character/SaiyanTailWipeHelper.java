package com.dbzlegacy.adaptivedifficulty.character;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.net.gui.GuiModelSyncPacket;
import com.dbzlegacy.adaptivedifficulty.net.gui.LmGuiNetwork;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.character.Status;
import java.util.Locale;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;

/**
 * Clears a stuck Saiyan-tail flag on characters whose race cannot have one.
 *
 * <p>DMZ stores the tail as character data ({@code Character.HasSaiyanTail}, plus the
 * {@code Status.TailVisible} choice) and draws the tail from that flag. Per DMZ's own race
 * configs only the tail family defaults it true, and DMZ's race change never clears it — so
 * a character created as a Saiyan keeps the drawn tail forever after changing race, and no
 * shop action can remove it because it is data, not an equipped piece. The client's race
 * layer draws a tail from that flag, which is what puts the ghost tail on other players'
 * screens. (Until 4.6.234 LM's body-part sync also sent {@code saiyan_tail} whenever the flag
 * was set; it no longer adds the race tail as a piece.)
 *
 * <p>The clear never sets a flag true and never touches the tail family — a Saiyan who lost
 * their tail keeps it lost, and a Saiyan's {@code TailVisible} choice stays theirs. Heal sites
 * call it on race change (new race already committed) and on login, and a heal triggers the
 * normal model-sync resend so other clients drop the tail.
 *
 * <p>{@link #restoreIfTailRace} is its mirror for a race change only (4.7.9): a player who
 * changes into the tail family gets the tail back, since the clear wiped it when they left and
 * nothing else in LM sets it again. Never called on login, so a tail lost in play stays lost.
 */
public final class SaiyanTailWipeHelper {
    /** Races whose DMZ configs carry the Saiyan tail (config/dragonminez/races - character.json). */
    private static final Set<String> TAIL_RACES =
            Set.of("saiyan", "ancient_saiyan", "sento_saiyan", "monkey");

    private SaiyanTailWipeHelper() {}

    /**
     * Clear {@code HasSaiyanTail} + {@code TailVisible} when the player's race is outside the
     * tail family. No-op for the family, for a clean flag, or while character data is missing.
     */
    public static void clearIfRaceCannotHaveTail(ServerPlayer player, String via) {
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
            if (race.isEmpty() || TAIL_RACES.contains(race)) {
                return;
            }
            if (!ch.isHasSaiyanTail()) {
                return;
            }
            ch.setHasSaiyanTail(false);
            Status status = data.getStatus();
            if (status != null) {
                status.setTailVisible(false);
            }
            String where = via == null || via.isBlank() ? "" : " via " + via;
            LmLog.event(
                    "[{}] SaiyanTailWipe cleared stuck tail flag for {} (race {}){}",
                    AdaptiveDifficultyMod.MOD_ID,
                    playerName(player),
                    race,
                    where);
            // Viewers keep the old tail until the authoritative parts land.
            LmGuiNetwork.sendModelSync(player, GuiModelSyncPacket.REASON_CLEAR);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Race change into the tail family: set {@code HasSaiyanTail} + {@code TailVisible} when the
     * flag is off, and resend DMZ's stats to the player and everyone tracking them so the race
     * layer draws the tail again. No-op outside the family, for a set flag, or while character
     * data is missing. A shop tail still hides the natural one ({@code suppressNaturalTail}).
     */
    public static void restoreIfTailRace(ServerPlayer player, String via) {
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
            if (!restores(race, ch.isHasSaiyanTail())) {
                return;
            }
            ch.setHasSaiyanTail(true);
            Status status = data.getStatus();
            if (status != null) {
                status.setTailVisible(true);
            }
            String where = via == null || via.isBlank() ? "" : " via " + via;
            LmLog.event(
                    "[{}] SaiyanTailWipe restored tail for {} (race {}){}",
                    AdaptiveDifficultyMod.MOD_ID,
                    playerName(player),
                    race,
                    where);
            NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
            LmGuiNetwork.sendModelSync(player, GuiModelSyncPacket.REASON_EQUIP);
        } catch (Throwable ignored) {
        }
    }

    /** The restore runs for a tail-family race whose flag is off. */
    static boolean restores(String race, boolean hasTail) {
        return !hasTail && TAIL_RACES.contains(normalizeRace(race));
    }

    /** Startup self-test: the restore covers the tail family only and never a set flag. */
    public static void selfTest(java.util.List<String> broken) {
        if (!restores("saiyan", false) || !restores("Monkey", false) || !restores("sento_saiyan", false)
                || restores("saiyan", true) || restores("human", false) || restores("", false)) {
            broken.add("Saiyan tail: a race change into the tail family should restore a missing tail, "
                    + "and nothing else");
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
