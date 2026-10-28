package com.dbzlegacy.adaptivedifficulty.progression;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import com.dragonminez.common.stats.StatsData;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Mirrors the server personal level cap into the player's LM-owned file for DMZ client UI
 * (LegacyMechanics is server-only — clients read Overhaul/DMZ configured max, not our mixins).
 */
public final class PersonalLevelCapMirror {
    public static final String KEY = "lm_personal_level_cap";
    public static final String KEY_BREAKTHROUGHS = "lm_personal_breakthroughs";
    /** Redundant root int — survives if {@code lm_progression} subtree is partial. */
    public static final String ROOT_BREAKTHROUGHS = "pp_level_breakthroughs";

    private static final Map<UUID, Integer> CAP_BY_UUID = new ConcurrentHashMap<>();
    /** Last cap actually written to NBT and sent to the client. */
    private static final Map<UUID, Integer> SYNCED = new ConcurrentHashMap<>();
    /**
     * Mohist often leaves {@code StatsData.player} null. The owner UUID is stable;
     * the cap itself lives in {@link #CAP_BY_UUID} so a breakthrough updates every copy.
     */
    private static final Map<StatsData, UUID> DATA_OWNER =
            Collections.synchronizedMap(new WeakHashMap<>());
    /**
     * Mohist often leaves {@code StatsData.player} null and identity-compare of
     * {@code DmzProgression.stats(sp) == data} fails across copies. Bind the live
     * cap onto the StatsData instance used by Overhaul mixins.
     */
    private static final Map<StatsData, Integer> CAP_BY_DATA =
            Collections.synchronizedMap(new WeakHashMap<>());

    private PersonalLevelCapMirror() {}

    /** Write cap + breakthrough count without re-reading (padmin set must stick). */
    public static void overwrite(ServerPlayer player, int breakthroughs, int cap) {
        if (player == null) {
            return;
        }
        int n = Math.max(0, breakthroughs);
        int c = Math.max(PrestigePointsSystem.BASE_LEVEL_CAP, cap);
        UUID id = player.m_20148_();
        CAP_BY_UUID.put(id, c);
        bindStatsData(player, c);
        rememberOwner(player, c);
        CompoundTag tag = com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore.root(player);
        if (!PersistentDataAccess.isWritable(tag)) {
            return;
        }
        tag.m_128405_(KEY, c);
        tag.m_128405_(KEY_BREAKTHROUGHS, n);
        com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore.putInt(player, ROOT_BREAKTHROUGHS, n);
    }

    /** Remember this StatsData's personal cap so mixins work when {@code getPlayer()} is null. */
    public static void bind(StatsData data, int cap) {
        if (data == null) {
            return;
        }
        int c = Math.max(PrestigePointsSystem.BASE_LEVEL_CAP, cap);
        CAP_BY_DATA.put(data, c);
    }

    public static void bindStatsData(ServerPlayer player, int cap) {
        if (player == null) {
            return;
        }
        try {
            bind(DmzProgression.stats(player), cap);
        } catch (Throwable ignored) {
        }
    }

    /**
     * In-memory cap for a getter. Zero means the cache is cold — callers must not
     * treat that as a published level cap.
     */
    public static int cachedCap(StatsData data) {
        if (data == null) {
            return 0;
        }
        UUID id = DATA_OWNER.get(data);
        if (id == null) {
            return 0;
        }
        Integer cap = CAP_BY_UUID.get(id);
        return cap == null ? 0 : cap;
    }

    public static int cachedCap(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        Integer cap = CAP_BY_UUID.get(player.m_20148_());
        return cap == null ? 0 : cap;
    }

    /** Store a computed cap. Does not write NBT or sync the client. */
    public static void remember(ServerPlayer player, int cap) {
        if (player == null) {
            return;
        }
        int c = Math.max(PrestigePointsSystem.BASE_LEVEL_CAP, cap);
        CAP_BY_UUID.put(player.m_20148_(), c);
        rememberOwner(player, c);
    }

    /** Tie this StatsData instance to the player's cached cap. Later getters skip the player scan. */
    public static void attach(StatsData data, ServerPlayer player, int cap) {
        if (data == null || player == null) {
            return;
        }
        int c = Math.max(PrestigePointsSystem.BASE_LEVEL_CAP, cap);
        UUID id = player.m_20148_();
        CAP_BY_UUID.put(id, c);
        DATA_OWNER.put(data, id);
        bind(data, c);
    }

    /** Call after a successful {@link DmzSkillUtil#sync} so the next publish can skip the packet. */
    public static void markSynced(ServerPlayer player, int cap) {
        if (player == null) {
            return;
        }
        SYNCED.put(player.m_20148_(), Math.max(PrestigePointsSystem.BASE_LEVEL_CAP, cap));
    }

    /** Drop the cached cap when the player leaves. The next login reads NBT again. */
    public static void forget(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID id = player.m_20148_();
        CAP_BY_UUID.remove(id);
        SYNCED.remove(id);
        synchronized (DATA_OWNER) {
            DATA_OWNER.entrySet().removeIf(entry -> id.equals(entry.getValue()));
        }
        try {
            StatsData data = DmzProgression.stats(player);
            if (data != null) {
                CAP_BY_DATA.remove(data);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void publish(ServerPlayer player) {
        if (player == null) {
            return;
        }
        int breakthroughs = PrestigePointsSystem.getBreakthroughs(player);
        int cap = PrestigePointsSystem.effectiveMaxLevel(breakthroughs);
        UUID id = player.m_20148_();
        CAP_BY_UUID.put(id, cap);
        rememberOwner(player, cap);
        Integer synced = SYNCED.get(id);
        if (synced != null && synced == cap) {
            return;
        }
        overwrite(player, breakthroughs, cap);
        try {
            DmzSkillUtil.sync(player);
        } catch (Throwable ignored) {
        }
        SYNCED.put(id, cap);
    }

    private static void rememberOwner(ServerPlayer player, int cap) {
        if (player == null) {
            return;
        }
        try {
            StatsData data = DmzProgression.stats(player);
            if (data == null) {
                return;
            }
            DATA_OWNER.put(data, player.m_20148_());
            bind(data, cap);
        } catch (Throwable ignored) {
        }
    }

    public static int read(Player player) {
        if (player == null) {
            return 0;
        }
        int cached = CAP_BY_UUID.getOrDefault(player.m_20148_(), 0);
        if (cached > 0) {
            return cached;
        }
        CompoundTag tag = com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore.root(player);
        if (!tag.m_128441_(KEY)) {
            return inferCapFromBreakthroughs(player);
        }
        return Math.max(0, tag.m_128451_(KEY));
    }

    /** Mohist-safe cap when {@link StatsData#getPlayer()} is null during mixin stat buys. */
    public static int resolveCap(StatsData data) {
        if (data == null) {
            return PrestigePointsSystem.BASE_LEVEL_CAP;
        }
        int cached = cachedCap(data);
        if (cached > 0) {
            return cached;
        }
        try {
            Player owner = data.getPlayer();
            if (owner instanceof ServerPlayer sp) {
                int cap = read(sp);
                if (cap <= 0) {
                    cap = PrestigePointsSystem.effectiveMaxLevel(sp);
                }
                attach(data, sp, cap);
                return cap;
            }
        } catch (Throwable ignored) {
        }
        ServerPlayer resolved = LmStatsDataAccess.serverPlayer(data);
        if (resolved != null) {
            int cap = read(resolved);
            if (cap <= 0) {
                cap = PrestigePointsSystem.effectiveMaxLevel(resolved);
            }
            attach(data, resolved, cap);
            return cap;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer sp : server.m_6846_().m_11314_()) {
                try {
                    if (DmzProgression.stats(sp) == data) {
                        int cap = read(sp);
                        int live = cap > 0 ? cap : PrestigePointsSystem.effectiveMaxLevel(sp);
                        attach(data, sp, live);
                        return live;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        Integer bound = CAP_BY_DATA.get(data);
        if (bound != null && bound > 0) {
            return bound;
        }
        return PrestigePointsSystem.BASE_LEVEL_CAP;
    }

    private static int inferCapFromBreakthroughs(Player player) {
        CompoundTag tag = com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore.root(player);
        int bt = com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore.getInt(player, ROOT_BREAKTHROUGHS, -1);
        if (bt < 0 && tag.m_128441_(KEY_BREAKTHROUGHS)) {
            bt = tag.m_128451_(KEY_BREAKTHROUGHS);
        }
        if (bt >= 0) {
            return PrestigePointsSystem.effectiveMaxLevel(bt);
        }
        return 0;
    }
}
