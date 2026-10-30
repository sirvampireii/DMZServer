package com.dbzlegacy.mohistmelee;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Heals desync where {@link PartyManager} SavedData says solo but
 * {@link PlayerQuestData} still has an activePartyId (V-menu ghost party).
 * That ghost also blocks non-leader difficulty / saga progression.
 */
public final class GhostPartyHeal {
    private static final Logger LOGGER = LogManager.getLogger(DmzMohistMeleeFix.MOD_ID);
    private static final AtomicInteger HEAL_LOGS = new AtomicInteger();

    private GhostPartyHeal() {}

    /**
     * @return true if ghost party state was cleared and synced
     */
    public static boolean healIfGhost(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        try {
            if (PartyManager.isInParty(player)) {
                return false;
            }
            PlayerQuestData quest = questData(player);
            if (quest == null || !quest.isInParty()) {
                return false;
            }
            quest.clearPartyState();
            NetworkHandler.sendToPlayer(new ProgressionSyncS2C(player), player);
            int n = HEAL_LOGS.incrementAndGet();
            if (n <= 80) {
                MeleeDebug.log(
                        "[{}] cleared ghost party state for {}",
                        DmzMohistMeleeFix.MOD_ID,
                        player.m_36316_().getName()
                );
            }
            return true;
        } catch (Throwable t) {
            LOGGER.warn(
                    "[{}] ghost party heal failed for {}: {}",
                    DmzMohistMeleeFix.MOD_ID,
                    player.m_36316_().getName(),
                    t.toString()
            );
            return false;
        }
    }

    private static PlayerQuestData questData(ServerPlayer player) {
        return StatsProvider.get(StatsCapability.INSTANCE, player)
                .map(StatsData::getPlayerQuestData)
                .orElse(null);
    }
}
