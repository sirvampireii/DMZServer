package com.dbzlegacy.adaptivedifficulty.command;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Cooldowns;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import java.util.ArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Staff reset for a stuck fusion cooldown.
 * Dragon Mine Z keeps {@code FusionCooldown} on {@code StatsData} cooldowns; it is removed and
 * synced. Noea (gone since DMZ Ultra replaced it, 4.7.47) left {@code fusionCooldownEnd} /
 * {@code fusionValidationError} in persistent data; any copy still there is cleared too.
 */
public final class FusionCooldownReset {
    static final String COOLDOWN_END = "fusionCooldownEnd";
    static final String VALIDATION_ERROR = "fusionValidationError";

    private FusionCooldownReset() {}

    public static String reset(ServerPlayer player) {
        if (player == null) {
            return LmCommandMessages.playerOffline("");
        }
        String name = player.m_6302_();
        try {
            boolean dmz = false;
            StatsData stats = DmzProgression.stats(player);
            if (stats != null && stats.getCooldowns().hasCooldown(Cooldowns.FUSION_CD)) {
                stats.getCooldowns().removeCooldown(Cooldowns.FUSION_CD);
                NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
                dmz = true;
            }
            CompoundTag persistent = PersistentDataAccess.get(player);
            int copies = 0;
            if (PersistentDataAccess.isWritable(persistent)) {
                copies = clearPersistentCopies(persistent);
            }
            LmLog.event(
                    "[{}] fusion reset player={} dmzCooldown={} persistentKeys={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    name,
                    dmz,
                    copies);
            return "§eFusion cooldown cleared for §f" + name + "§e.";
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] fusion reset failed for {}", AdaptiveDifficultyMod.MOD_ID, name, t);
            return "§cFusion reset failed for §f" + name + "§c. Check the server log.";
        }
    }

    /**
     * Set every {@code fusionCooldownEnd} long to 0 and every {@code fusionValidationError}
     * string to empty, including nested compounds and compound lists.
     */
    static int clearPersistentCopies(CompoundTag root) {
        if (root == null) {
            return 0;
        }
        int cleared = 0;
        for (String key : new ArrayList<>(root.m_128431_())) {
            if (COOLDOWN_END.equals(key)) {
                root.m_128356_(key, 0L);
                cleared++;
            } else if (VALIDATION_ERROR.equals(key)) {
                root.m_128359_(key, "");
                cleared++;
            }
            Tag child = root.m_128423_(key);
            if (child instanceof CompoundTag nested) {
                cleared += clearPersistentCopies(nested);
            } else if (child instanceof ListTag list) {
                for (int i = 0; i < list.size(); i++) {
                    Tag entry = list.get(i);
                    if (entry instanceof CompoundTag nested) {
                        cleared += clearPersistentCopies(nested);
                    }
                }
            }
        }
        return cleared;
    }
}
