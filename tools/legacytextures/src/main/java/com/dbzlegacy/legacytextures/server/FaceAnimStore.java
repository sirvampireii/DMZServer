package com.dbzlegacy.legacytextures.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Each player's own face animation switch ({@code /faceanim}), kept in their persisted player data like
 * {@link ModeStore}, so it survives death, dimension changes and restarts. Players who never chose are on.
 */
public final class FaceAnimStore {
    private static final String KEY = "legacytextures_faceanim";

    private FaceAnimStore() {
    }

    public static boolean get(Player player) {
        CompoundTag persisted = player.getPersistentData().m_128469_(Player.PERSISTED_NBT_TAG);
        return !persisted.m_128441_(KEY) || persisted.m_128471_(KEY);
    }

    public static void set(Player player, boolean on) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.m_128469_(Player.PERSISTED_NBT_TAG);
        persisted.m_128379_(KEY, on);
        root.m_128365_(Player.PERSISTED_NBT_TAG, persisted);
    }
}
