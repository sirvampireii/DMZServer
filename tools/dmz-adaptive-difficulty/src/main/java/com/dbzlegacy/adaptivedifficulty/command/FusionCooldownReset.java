package com.dbzlegacy.adaptivedifficulty.command;

import com.butterjaffa.noeabosses.FusionLifecycleService;
import com.butterjaffa.noeabosses.V090Data;
import com.butterjaffa.noeabosses.V090Network;
import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import java.util.ArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Staff reset for a stuck fusion cooldown.
 * Noea keeps {@code fusionCooldownEnd} on {@link V090Data} and Dragon Mine Z keeps
 * {@code FusionCooldown} on {@code StatsData} cooldowns. A corrupted file can also
 * leave the same keys in another persistent-data compound, so every copy is cleared.
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
            V090Data before = V090Data.getOrMigrate(player);
            long previousEnd = before == null ? 0L : before.fusionCooldownEnd;
            String previousError = before == null || before.fusionValidationError == null
                    ? ""
                    : before.fusionValidationError;
            FusionLifecycleService.resetCooldown(player);
            V090Data data = V090Data.getOrMigrate(player);
            if (data != null) {
                data.fusionCooldownEnd = 0L;
                data.fusionValidationError = "";
                V090Data.save(player, data);
                V090Network.sync(player, data);
            }
            CompoundTag persistent = PersistentDataAccess.get(player);
            int copies = 0;
            if (PersistentDataAccess.isWritable(persistent)) {
                copies = clearPersistentCopies(persistent);
            }
            LmLog.event(
                    "[{}] fusion reset player={} previousEnd={} hadError={} persistentKeys={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    name,
                    previousEnd,
                    !previousError.isBlank(),
                    copies);
            return "§eFusion cooldown cleared for §f" + name
                    + "§e. Cooldown end is 0 and the validation error is cleared.";
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
