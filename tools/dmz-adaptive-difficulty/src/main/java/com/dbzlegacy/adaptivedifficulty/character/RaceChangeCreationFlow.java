package com.dbzlegacy.adaptivedifficulty.character;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.progression.DmzResourcePoolClamp;
import com.dbzlegacy.adaptivedifficulty.progression.race.AndroidConversion;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.character.Status;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * After a free 0% race change, reopen DMZ character setup (class + appearance) on the client.
 */
public final class RaceChangeCreationFlow {
    private static final long SESSION_MS = 30L * 60L * 1000L;
    private static final Map<UUID, Session> ACTIVE = new ConcurrentHashMap<>();

    private RaceChangeCreationFlow() {}

    public static void begin(ServerPlayer player, String targetRaceId) {
        begin(player, targetRaceId, null);
    }

    public static void begin(ServerPlayer player, String targetRaceId, String headBoneToRestore) {
        if (player == null || targetRaceId == null || targetRaceId.isBlank()) {
            return;
        }
        String keep = null;
        if (headBoneToRestore != null && !headBoneToRestore.isBlank()) {
            String owned = CosmeticHeadBoneService.piecesKeptAcrossRaceChange(player, headBoneToRestore);
            if (!owned.isEmpty()) {
                keep = owned;
            }
        }
        String priorClass = DmzProgression.fightingClass(player);
        ACTIVE.put(
                player.m_20148_(),
                new Session(
                        targetRaceId.trim().toLowerCase(),
                        System.currentTimeMillis(),
                        keep,
                        priorClass == null ? "" : priorClass.trim().toLowerCase()));
    }

    public static boolean isActive(ServerPlayer player) {
        return session(player) != null;
    }

    public static String targetRaceId(ServerPlayer player) {
        Session session = session(player);
        return session == null ? "" : session.targetRaceId;
    }

    public static String priorFightingClass(ServerPlayer player) {
        Session session = session(player);
        return session == null ? "" : session.priorFightingClass;
    }

    public static void clear(ServerPlayer player) {
        if (player != null) {
            ACTIVE.remove(player.m_20148_());
        }
    }

    /** After {@link StatsData#resetPlayerProgress} — target race set, class cleared, creation flag off. */
    public static void prepareCharacterData(ServerPlayer player, StatsData data, String targetRaceId) {
        if (player == null || data == null || targetRaceId == null || targetRaceId.isBlank()) {
            return;
        }
        Character ch = data.getCharacter();
        if (ch != null) {
            ch.setRace(targetRaceId);
            Session session = session(player);
            String priorClass = session == null ? "" : session.priorFightingClass;
            String mapped =
                    RaceChangeClassMapper.resolveClassForRaceAfterChange(
                            ch.getCharacterClass(), priorClass, targetRaceId);
            try {
                ch.setCharacterClass(mapped);
            } catch (Throwable ignored) {
            }
            // Do not set class to "" — DMZ client builds lang keys as class.dragonminez.<id>
            // and an empty id shows as the broken literal "class.dragonminez".
            // CreateCharacterC2S applies the player's new class after setup.
        }
        try {
            Status status = data.getStatus();
            if (status != null) {
                status.setHasCreatedCharacter(false);
            }
        } catch (Throwable ignored) {
        }
        try {
            data.updateTransformationSkillLimits(targetRaceId);
        } catch (Throwable ignored) {
        }
        AndroidConversion.stripIfRaceIneligible(player, targetRaceId);
        clearSagaDifficultyGate(player, data);
    }

    /**
     * Push saga + stats to the client once. With {@code hasCreatedCharacter=false}, DMZ opens the
     * full create-character UI (class + appearance). Do not also send {@code OpenRecustomizeS2C} —
     * that is for reskin-only and causes players to run setup twice.
     */
    public static void openEditor(ServerPlayer player) {
        if (player == null) {
            return;
        }
        var server = player.m_20194_();
        Runnable open = () -> {
            try {
                NetworkHandler.sendToPlayer(new ProgressionSyncS2C(player), player);
                NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
            } catch (Throwable ignored) {
            }
        };
        if (server != null) {
            server.execute(open);
        } else {
            open.run();
        }
    }

    /** When the player finishes DMZ {@code CreateCharacterC2S} after a 0% race change. */
    public static void onCharacterCreated(ServerPlayer player) {
        Session session = session(player);
        if (session == null) {
            return;
        }
        ACTIVE.remove(player.m_20148_());
        String race = session.targetRaceId;
        StatsData data = DmzProgression.stats(player);
        if (data != null && race != null && !race.isBlank()) {
            String picked = DmzProgression.fightingClass(player);
            float[] snap = DmzClassChangeCapture.take(player);
            DmzCharacterClassChangeHooks.onServicesRaceChangeApplied(
                    player, data, race, picked, snap, true, session.priorFightingClass);
        } else {
            try {
                Character ch = DmzProgression.character(player);
                if (ch != null && race != null && !race.isBlank()) {
                    ch.setRace(race);
                }
            } catch (Throwable ignored) {
            }
        }
        if (session.keepHeadBone != null && !session.keepHeadBone.isBlank()) {
            CosmeticHeadBoneService.reapplyHeadBoneAfterRaceChange(player, session.keepHeadBone);
        }
        // Creation completes after executeRaceChange returned; restore the tail on the final race.
        SaiyanTailWipeHelper.restoreIfTailRace(player, "RaceChangeCreationFlow.onCharacterCreated");
        try {
            DmzResourcePoolClamp.syncToClient(player);
        } catch (Throwable ignored) {
        }
    }

    private static Session session(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        Session s = ACTIVE.get(player.m_20148_());
        if (s == null) {
            return null;
        }
        if (System.currentTimeMillis() - s.startedAt > SESSION_MS) {
            ACTIVE.remove(player.m_20148_());
            return null;
        }
        return s;
    }

    private static void clearSagaDifficultyGate(ServerPlayer player, StatsData data) {
        try {
            PlayerQuestData quest = data.getPlayerQuestData();
            if (quest != null) {
                try {
                    quest.requestDifficultyReselect();
                } catch (Throwable ignored) {
                }
                quest.setDifficultyChosen(false);
            }
        } catch (Throwable ignored) {
        }
        // Progression sync is sent from openEditor() after prepareCharacterData.
    }

    private record Session(String targetRaceId, long startedAt, String keepHeadBone, String priorFightingClass) {}
}
