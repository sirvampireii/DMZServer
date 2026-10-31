package com.dbzlegacy.adaptivedifficulty.progression;

import com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeCharacter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Session temp + persistent NBT storage for progression scripts
 * (replaces CNPC tempdata / storeddata). The stored bag lives in the player's LM-owned file
 * ({@link LmPlayerStore}), not Forge persistent data.
 * <p>
 * Prestige-shop purchases ({@code pp_*} keys: permanent forms, skill floors, difficulty tiers, God Ki,
 * special perks) belong to the active DMZ Ultra character (4.7.3): every {@code stored*} call with a
 * {@code pp_} key reads and writes {@link #shop}. Slot 0 and players without Ultra use the account bag,
 * as before; another character has its own bag in its character file, empty until it buys something.
 */
public final class ProgressionData {
    private static final String ROOT = "lm_progression";
    private static final String SHOP_PREFIX = "pp_";
    private static final Map<UUID, Map<String, String>> TEMP = new ConcurrentHashMap<>();

    private ProgressionData() {}

    public static void clearPlayer(UUID id) {
        if (id != null) {
            TEMP.remove(id);
        }
    }

    public static Map<String, String> temp(ServerPlayer player) {
        return TEMP.computeIfAbsent(player.m_20148_(), u -> new ConcurrentHashMap<>());
    }

    public static boolean tempHas(ServerPlayer player, String key) {
        Map<String, String> t = TEMP.get(player.m_20148_());
        return t != null && t.containsKey(key);
    }

    public static String tempGet(ServerPlayer player, String key, String fallback) {
        Map<String, String> t = TEMP.get(player.m_20148_());
        if (t == null) {
            return fallback;
        }
        return t.getOrDefault(key, fallback);
    }

    public static long tempGetLong(ServerPlayer player, String key, long fallback) {
        try {
            String v = tempGet(player, key, null);
            if (v == null || v.isBlank()) {
                return fallback;
            }
            return Long.parseLong(v);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static double tempGetDouble(ServerPlayer player, String key, double fallback) {
        try {
            String v = tempGet(player, key, null);
            if (v == null || v.isBlank()) {
                return fallback;
            }
            return Double.parseDouble(v);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static void tempPut(ServerPlayer player, String key, String value) {
        temp(player).put(key, value == null ? "" : value);
    }

    public static void tempPut(ServerPlayer player, String key, long value) {
        tempPut(player, key, Long.toString(value));
    }

    public static void tempPut(ServerPlayer player, String key, double value) {
        tempPut(player, key, Double.toString(value));
    }

    public static void tempRemove(ServerPlayer player, String key) {
        Map<String, String> t = TEMP.get(player.m_20148_());
        if (t != null) {
            t.remove(key);
        }
    }

    public static CompoundTag stored(ServerPlayer player) {
        CompoundTag root = LmPlayerStore.root(player);
        if (!root.m_128441_(ROOT)) {
            root.m_128365_(ROOT, new CompoundTag());
        }
        return root.m_128469_(ROOT);
    }

    /** The active character's prestige-shop purchase bag ({@code pp_*} keys). */
    public static CompoundTag shop(ServerPlayer player) {
        if (PrestigeCharacter.isAccount(player)) {
            return stored(player);
        }
        CompoundTag root = PrestigeCharacter.root(player);
        if (!root.m_128441_(ROOT)) {
            root.m_128365_(ROOT, new CompoundTag());
        }
        return root.m_128469_(ROOT);
    }

    /** The bag a key lives in: purchases with the character, everything else with the account. */
    private static CompoundTag bag(ServerPlayer player, String key) {
        return key != null && key.startsWith(SHOP_PREFIX) ? shop(player) : stored(player);
    }

    public static boolean storedWritable(ServerPlayer player) {
        return player != null;
    }

    /** Drop the whole stored bag (staff progression wipe). */
    public static void storedClearAll(ServerPlayer player) {
        if (player != null) {
            LmPlayerStore.root(player).m_128473_(ROOT);
            if (!PrestigeCharacter.isAccount(player)) {
                PrestigeCharacter.root(player).m_128473_(ROOT);
            }
        }
    }

    public static boolean storedHas(ServerPlayer player, String key) {
        CompoundTag tag = bag(player, key);
        return tag.m_128441_(key);
    }

    public static String storedGet(ServerPlayer player, String key, String fallback) {
        CompoundTag tag = bag(player, key);
        if (!tag.m_128441_(key)) {
            return fallback;
        }
        try {
            // STRING
            if (tag.m_128425_(key, 8)) {
                String v = tag.m_128461_(key);
                return v == null ? fallback : v;
            }
            // Numeric (byte/short/int/long/float/double) — getString returns "" on these.
            if (tag.m_128425_(key, 99)) {
                return Long.toString(tag.m_128454_(key));
            }
        } catch (Throwable ignored) {
        }
        try {
            String v = tag.m_128461_(key);
            if (v != null && !v.isEmpty()) {
                return v;
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    /**
     * Read a stored number that may be a numeric NBT tag <b>or</b> a decimal string.
     * {@code CompoundTag.getLong} returns {@code 0} (no throw) for string tags, so
     * 2.4.66's "try getLong first" path made prestige points / other string wallets
     * always read as 0 after {@link #storedPut(ServerPlayer, String, long)}.
     */
    public static long storedGetLong(ServerPlayer player, String key, long fallback) {
        CompoundTag tag = bag(player, key);
        if (!tag.m_128441_(key)) {
            return fallback;
        }
        try {
            if (tag.m_128425_(key, 99)) { // TAG_ANY_NUMERIC
                return tag.m_128454_(key);
            }
        } catch (Throwable ignored) {
        }
        try {
            String v = tag.m_128461_(key);
            if (v == null || v.isBlank()) {
                return fallback;
            }
            return Long.parseLong(v.trim());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static double storedGetDouble(ServerPlayer player, String key, double fallback) {
        try {
            String v = storedGet(player, key, null);
            if (v == null || v.isBlank()) {
                return fallback;
            }
            return Double.parseDouble(v);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static boolean storedGetBool(ServerPlayer player, String key) {
        String v = storedGet(player, key, "false");
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    public static void storedPut(ServerPlayer player, String key, String value) {
        if (!storedWritable(player)) {
            return;
        }
        bag(player, key).m_128359_(key, value == null ? "" : value);
    }

    public static void storedPut(ServerPlayer player, String key, long value) {
        if (!storedWritable(player)) {
            return;
        }
        bag(player, key).m_128356_(key, value); // putLong — matches storedGetLong numeric path
    }

    public static void storedPut(ServerPlayer player, String key, double value) {
        storedPut(player, key, Double.toString(value));
    }

    public static void storedPutBool(ServerPlayer player, String key, boolean value) {
        storedPut(player, key, value ? "true" : "false");
    }

    public static void storedRemove(ServerPlayer player, String key) {
        if (!storedWritable(player)) {
            return;
        }
        bag(player, key).m_128473_(key);
    }
}
