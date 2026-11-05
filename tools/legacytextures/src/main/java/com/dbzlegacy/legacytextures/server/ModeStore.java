package com.dbzlegacy.legacytextures.server;

import com.dbzlegacy.legacytextures.TextureMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Keeps each player's choice in their persisted player data, so it survives death,
 * dimension changes and restarts. Players who never chose get the server default.
 */
public final class ModeStore {
    private static final String KEY = "legacytextures";

    private ModeStore() {
    }

    public static TextureMode get(Player player) {
        CompoundTag persisted = player.getPersistentData().m_128469_(Player.PERSISTED_NBT_TAG);
        if (!persisted.m_128441_(KEY)) {
            return ServerSettings.get().defaultMode();
        }
        return TextureMode.fromTag(persisted.m_128469_(KEY));
    }

    public static void set(Player player, TextureMode mode) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.m_128469_(Player.PERSISTED_NBT_TAG);
        persisted.m_128365_(KEY, mode.toTag());
        root.m_128365_(Player.PERSISTED_NBT_TAG, persisted);
    }
}
