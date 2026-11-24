package com.dbzlegacy.adaptivedifficulty.progression.race;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.LmChat;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Status;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Clears stuck saga {@code difficultyChosen} while character creation is incomplete.
 * Race selection is never denied and a missing skill never resets the character.
 */
public final class RaceLock {
    private static final long SAGA_DIFF_COOLDOWN_MS = 8_000L;

    private RaceLock() {}

    /**
     * Whether the player may select this DMZ race.
     * Returns {@code null} when allowed; otherwise a short player-facing denial.
     */
    public static String selectBlockReason(ServerPlayer player, String raceId) {
        if (player == null || raceId == null || raceId.isBlank()) {
            return "§cInvalid race.";
        }
        return null;
    }

    public static boolean maySelectRace(ServerPlayer player, String raceId) {
        return selectBlockReason(player, raceId) == null;
    }

    public static void pulse(ServerPlayer player, long nowMs) {
        if (player == null) {
            return;
        }
        if (nowMs < ProgressionData.tempGetLong(player, "restricted_race_command_tick", 0L)) {
            return;
        }
        ProgressionData.tempPut(player, "restricted_race_command_tick", nowMs + 1000L);
        try {
            StatsData data = DmzProgression.stats(player);
            if (data == null) {
                return;
            }
            Status status;
            try {
                status = data.getStatus();
            } catch (Throwable t) {
                return;
            }
            if (status == null) {
                return;
            }

            // Character not finished: allow race GUI preview + unlock stuck saga picker.
            boolean created;
            try {
                created = status.isHasCreatedCharacter();
            } catch (Throwable t) {
                created = true;
            }
            if (!created) {
                maybeAutoUnlockStuckDifficulty(player, data, nowMs);
                ProgressionData.tempRemove(player, "restricted_race_command_last_state");
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * While create is incomplete, keep clearing stuck difficultyChosen / ghost party
     * so the Easy/Normal/Hard picker can open (DMZ RACE LOCK.js maybeAutoUnlockStuckDifficulty).
     */
    private static void maybeAutoUnlockStuckDifficulty(ServerPlayer player, StatsData data, long nowMs) {
        PlayerQuestData quest = questData(data);
        if (quest == null) {
            return;
        }
        boolean chosen = false;
        boolean inParty = false;
        boolean leader = false;
        try {
            chosen = quest.isDifficultyChosen();
        } catch (Throwable ignored) {
        }
        try {
            inParty = quest.isInParty();
            leader = quest.isPartyLeader(player.m_20148_());
        } catch (Throwable ignored) {
        }
        boolean stuck = chosen || (inParty && !leader);
        if (!stuck) {
            return;
        }
        long coolUntil = ProgressionData.tempGetLong(player, "race_lock_saga_diff_cooldown", 0L);
        if (nowMs < coolUntil) {
            return;
        }
        ProgressionData.tempPut(player, "race_lock_saga_diff_cooldown", nowMs + SAGA_DIFF_COOLDOWN_MS);
        clearStuckSagaDifficulty(player, data, false);
    }

    /**
     * Port of clearStuckSagaDifficulty — leave non-leader party, requestDifficultyReselect,
     * force setDifficultyChosen(false), sync.
     */
    private static boolean clearStuckSagaDifficulty(ServerPlayer player, StatsData data, boolean notify) {
        if (data == null || player == null) {
            return false;
        }
        PlayerQuestData quest = questData(data);
        if (quest == null) {
            return false;
        }
        boolean wasInParty = false;
        boolean wasLeader = false;
        try {
            wasInParty = quest.isInParty();
            wasLeader = quest.isPartyLeader(player.m_20148_());
        } catch (Throwable ignored) {
        }
        if (wasInParty && !wasLeader) {
            leaveDmzParty(player, data);
            quest = questData(data);
            if (quest == null) {
                return false;
            }
        }
        try {
            quest.requestDifficultyReselect();
        } catch (Throwable t) {
            try {
                quest.setDifficultyChosen(false);
            } catch (Throwable setErr) {
                if (notify) {
                    DmzRewards.msg(player, LmChat.fail("Race", "Could not unlock your race. Ask staff to check your character."));
                }
                return false;
            }
        }
        try {
            quest.setDifficultyChosen(false);
        } catch (Throwable ignored) {
        }
        syncProgression(player);
        boolean stillChosen = false;
        try {
            stillChosen = quest.isDifficultyChosen();
        } catch (Throwable ignored) {
        }
        if (notify) {
            if (!stillChosen) {
                DmzRewards.msg(player, LmChat.ok("Race", "Saga difficulty unlocked."));
                DmzRewards.msg(player, LmChat.info("Race",
                        "Close and reopen the Saga / Quest Tree, then choose Easy, Normal, or Hard."));
            } else {
                DmzRewards.msg(player, LmChat.fail("Race", "Your race is still locked after the unlock. Ask staff to check your character."));
            }
            if (wasInParty && !wasLeader) {
                DmzRewards.msg(player, LmChat.note("Race",
                        "Left DMZ party so difficulty selection is allowed."));
            }
        }
        return !stillChosen;
    }

    private static void leaveDmzParty(ServerPlayer player, StatsData data) {
        try {
            PartyManager.leaveParty(player);
        } catch (Throwable ignored) {
        }
        try {
            PlayerQuestData quest = questData(data);
            if (quest != null && quest.isInParty()) {
                quest.clearPartyState();
            }
        } catch (Throwable ignored) {
        }
        syncProgression(player);
    }

    private static PlayerQuestData questData(StatsData data) {
        if (data == null) {
            return null;
        }
        try {
            return data.getPlayerQuestData();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void syncProgression(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            NetworkHandler.sendToPlayer(new ProgressionSyncS2C(player), player);
        } catch (Throwable ignored) {
        }
    }

    private static void dispatchConsole(MinecraftServer server, String cmd) {
        if (server == null || cmd == null || cmd.isBlank()) {
            return;
        }
        try {
            Commands commands = server.m_129892_();
            CommandSourceStack source = server.m_129893_();
            commands.m_230957_(source, cmd);
        } catch (Throwable t1) {
            try {
                Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
                Object console = bukkit.getMethod("getConsoleSender").invoke(null);
                Object serverB = bukkit.getMethod("getServer").invoke(null);
                serverB.getClass().getMethod("dispatchCommand",
                                Class.forName("org.bukkit.command.CommandSender"), String.class)
                        .invoke(serverB, console, cmd);
            } catch (Throwable ignored) {
            }
        }
    }
}
