package com.dbzlegacy.adaptivedifficulty.progression;

import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Mirrors the player's level cap (Overhaul's, {@link LmOverhaulCapMath#levelCap(ServerPlayer)}) into
 * the player's LM-owned file and pushes a DMZ stats sync when it changes, for the DMZ client UI
 * (LegacyMechanics is server-only — clients read Overhaul/DMZ configured max, not our mixins).
 * <p>
 * Since 4.7.2 nothing per player raises the cap. The old breakthrough keys
 * ({@code lm_personal_breakthroughs}, {@code pp_level_breakthroughs}) stay in player files and are
 * not read.
 */
public final class PersonalLevelCapMirror {
    public static final String KEY = "lm_personal_level_cap";

    /** Last cap written to the player file and sent to the client. */
    private static final Map<UUID, Integer> SYNCED = new ConcurrentHashMap<>();

    private PersonalLevelCapMirror() {}

    /** Drop the last-sent cap when the player leaves. The next login publishes again. */
    public static void forget(ServerPlayer player) {
        if (player != null) {
            SYNCED.remove(player.m_20148_());
        }
    }

    public static void publish(ServerPlayer player) {
        if (player == null) {
            return;
        }
        int cap = LmOverhaulCapMath.levelCap(player);
        UUID id = player.m_20148_();
        Integer synced = SYNCED.get(id);
        if (synced != null && synced == cap) {
            return;
        }
        CompoundTag tag = com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore.root(player);
        if (PersistentDataAccess.isWritable(tag)) {
            tag.m_128405_(KEY, cap);
        }
        try {
            DmzSkillUtil.sync(player);
        } catch (Throwable ignored) {
        }
        SYNCED.put(id, cap);
    }
}
