package com.dbzlegacy.mohistmelee;

import com.dragonminez.common.init.MainAttributes;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Empty-hand / fist melee uses {@code Stats.getMeleeDamage()} → {@code Stats.getStrength()},
 * which reads the {@code dragonminez:strength} AttributeInstance base on the player.
 * Held non-DMZ items skip that path in {@code CombatEvent.onLivingHurt} and keep vanilla damage.
 * <p>
 * On Mohist, cross-dimension teleports (raid arenas) can reset custom attribute bases to their
 * registry defaults (0 for primary stats). Snapshot last-known-good primaries and restore them
 * after dim/teleport. Does <b>not</b> write {@code ki_damage} / melee / strike damage attrs.
 * <p>
 * Intentional DMZ writes ({@code Stats.setStrength(0)} / {@code resetPlayerProgress}) must update
 * or clear the snapshot — otherwise tick/read-side restore undoes player stat resets.
 */
public final class PrimaryStatRepair {
    private static final Logger LOGGER = LogManager.getLogger(DmzMohistMeleeFix.MOD_ID);
    private static final AtomicInteger LOGS = new AtomicInteger();
    /** uuid -> [STR, SKP, RES, VIT, PWR, ENE] last-known-good bases */
    private static final Map<UUID, int[]> SNAPSHOTS = new ConcurrentHashMap<>();
    /** uuid -> suppress restore/fallback while an intentional reset is in progress */
    private static final Map<UUID, Integer> SUPPRESS_TICKS = new ConcurrentHashMap<>();

    private static final String[] KEYS = {"STR", "SKP", "RES", "VIT", "PWR", "ENE"};

    @SuppressWarnings("unchecked")
    private static final RegistryObject<Attribute>[] ATTRS = new RegistryObject[] {
            MainAttributes.STRENGTH,
            MainAttributes.STRIKE_POWER,
            MainAttributes.RESISTANCE,
            MainAttributes.VITALITY,
            MainAttributes.KI_POWER,
            MainAttributes.ENERGY
    };

    private PrimaryStatRepair() {}

    /** Cache current primary bases when they look valid (> 0). */
    public static void snapshot(Player player) {
        if (player == null || isSuppressed(player)) {
            return;
        }
        try {
            int[] saved = SNAPSHOTS.getOrDefault(player.m_20148_(), new int[ATTRS.length]);
            int[] next = Arrays.copyOf(saved, ATTRS.length);
            boolean any = false;
            for (int i = 0; i < ATTRS.length; i++) {
                Attribute attr = ATTRS[i].get();
                if (attr == null) {
                    continue;
                }
                AttributeInstance inst = player.m_21051_(attr);
                if (inst == null) {
                    continue;
                }
                double base = inst.m_22115_();
                if (Double.isFinite(base) && base > 0.0D) {
                    next[i] = (int) Math.round(base);
                    any = true;
                }
            }
            if (any) {
                SNAPSHOTS.put(player.m_20148_(), next);
            }
        } catch (Throwable t) {
            if (LOGS.get() < 5) {
                LOGGER.warn("[{}] primary snapshot failed: {}", DmzMohistMeleeFix.MOD_ID, t.toString());
            }
        }
    }

    /**
     * Record an intentional DMZ primary write (including 0). Mohist dim-wipes do <em>not</em>
     * go through {@code Stats.setAttributeBaseValue}, so those keep the previous snapshot.
     */
    public static void record(Player player, Attribute attribute, int value) {
        if (player == null || attribute == null) {
            return;
        }
        try {
            int idx = indexOf(attribute);
            if (idx < 0) {
                return;
            }
            int[] saved = SNAPSHOTS.getOrDefault(player.m_20148_(), new int[ATTRS.length]);
            int[] next = Arrays.copyOf(saved, ATTRS.length);
            next[idx] = Math.max(0, value);
            SNAPSHOTS.put(player.m_20148_(), next);
        } catch (Throwable t) {
            if (LOGS.get() < 5) {
                LOGGER.warn("[{}] primary record failed: {}", DmzMohistMeleeFix.MOD_ID, t.toString());
            }
        }
    }

    /**
     * Overwrite the whole snapshot from live attribute bases (0 included).
     * Used after {@code resetPlayerProgress} so restore cannot resurrect pre-reset stats.
     */
    public static void adoptCurrent(Player player) {
        if (player == null) {
            return;
        }
        try {
            int[] next = new int[ATTRS.length];
            for (int i = 0; i < ATTRS.length; i++) {
                Attribute attr = ATTRS[i].get();
                if (attr == null) {
                    continue;
                }
                AttributeInstance inst = player.m_21051_(attr);
                if (inst == null) {
                    next[i] = 0;
                    continue;
                }
                double base = inst.m_22115_();
                next[i] = Double.isFinite(base) ? Math.max(0, (int) Math.round(base)) : 0;
            }
            SNAPSHOTS.put(player.m_20148_(), next);
            int n = LOGS.incrementAndGet();
            if (n <= 40) {
                String name = player instanceof ServerPlayer sp
                        ? sp.m_36316_().getName()
                        : String.valueOf(player.m_20148_());
                MeleeDebug.log(
                        "[{}] adopted primary snapshot player={} STR={} SKP={} RES={} VIT={} PWR={} ENE={}",
                        DmzMohistMeleeFix.MOD_ID,
                        name,
                        next[0],
                        next[1],
                        next[2],
                        next[3],
                        next[4],
                        next[5]
                );
            }
        } catch (Throwable t) {
            if (LOGS.get() < 5) {
                LOGGER.warn("[{}] primary adopt failed: {}", DmzMohistMeleeFix.MOD_ID, t.toString());
            }
        }
    }

    /** Block restore/read-fallback for a short window (intentional reset in progress). */
    public static void suppressRestore(Player player, int ticks) {
        if (player == null) {
            return;
        }
        SUPPRESS_TICKS.put(player.m_20148_(), Math.max(1, ticks));
    }

    public static void tickSuppress(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.m_20148_();
        Integer left = SUPPRESS_TICKS.get(id);
        if (left == null) {
            return;
        }
        if (left <= 1) {
            SUPPRESS_TICKS.remove(id);
        } else {
            SUPPRESS_TICKS.put(id, left - 1);
        }
    }

    public static boolean isSuppressed(Player player) {
        if (player == null) {
            return false;
        }
        Integer left = SUPPRESS_TICKS.get(player.m_20148_());
        return left != null && left > 0;
    }

    /**
     * Re-inject missing primary AttributeInstances and restore bases wiped to 0/NaN after
     * cross-dimension teleports. Never touches secondary damage attributes.
     * Skipped while an intentional reset suppress window is active.
     *
     * @return true if any attribute was changed
     */
    public static boolean restore(Player player, String reason) {
        if (player == null || isSuppressed(player)) {
            return false;
        }
        boolean changed = false;
        try {
            int[] saved = SNAPSHOTS.get(player.m_20148_());
            if (saved == null) {
                return false;
            }
            for (int i = 0; i < ATTRS.length; i++) {
                Attribute attr = ATTRS[i].get();
                if (attr == null || saved[i] <= 0) {
                    continue;
                }
                AttributeInstance inst = player.m_21051_(attr);
                if (inst == null) {
                    inst = injectAttribute(player, attr, saved[i]);
                    if (inst != null) {
                        changed = true;
                        logRestore(player, KEYS[i], Double.NaN, saved[i], reason + "-inject");
                    }
                    continue;
                }
                double base = inst.m_22115_();
                if (!Double.isFinite(base) || base <= 0.0D) {
                    inst.m_22100_((double) saved[i]);
                    changed = true;
                    logRestore(player, KEYS[i], base, saved[i], reason);
                }
            }
        } catch (Throwable t) {
            if (LOGS.get() < 5) {
                LOGGER.warn("[{}] primary restore failed: {}", DmzMohistMeleeFix.MOD_ID, t.toString());
            }
        }
        return changed;
    }

    /** Snapshot then restore — used on dim change / teleport / melee packet. */
    public static boolean ensure(Player player, String reason) {
        if (isSuppressed(player)) {
            return false;
        }
        snapshot(player);
        return restore(player, reason);
    }

    /**
     * Last-known-good base for a primary attribute, or 0 if none.
     * Readable even while restore is suppressed — percentage resets need accurate
     * pre-reset values when live attribute bases are briefly 0/missing.
     */
    public static int savedFor(Player player, Attribute attribute) {
        if (player == null || attribute == null) {
            return 0;
        }
        int[] saved = SNAPSHOTS.get(player.m_20148_());
        if (saved == null) {
            return 0;
        }
        int idx = indexOf(attribute);
        return idx >= 0 ? saved[idx] : 0;
    }

    public static void clear(UUID id) {
        if (id != null) {
            SNAPSHOTS.remove(id);
            SUPPRESS_TICKS.remove(id);
        }
    }

    /** Force snapshot primaries to 0 (full intentional reset). */
    public static void forceZeroSnapshot(Player player) {
        if (player == null) {
            return;
        }
        SNAPSHOTS.put(player.m_20148_(), new int[ATTRS.length]);
    }

    /**
     * Start an intentional DMZ reset: cancel delayed teleport repairs and suppress
     * write-back restore for {@code suppressTicks}.
     * <p>
     * Keeps the existing snapshot so percentage resets can still read last-known
     * primaries if Mohist briefly reports 0 during {@code getStrength()} etc.
     * {@link #endIntentionalReset} adopts/zeros the snapshot after DMZ finishes.
     */
    public static void beginIntentionalReset(Player player, int suppressTicks) {
        if (player == null) {
            return;
        }
        if (player instanceof ServerPlayer sp) {
            CombatUnlock.cancelFollowups(sp);
        }
        // Capture current live bases before DMZ mutates them (ignore suppress).
        try {
            int[] next = new int[ATTRS.length];
            boolean any = false;
            for (int i = 0; i < ATTRS.length; i++) {
                Attribute attr = ATTRS[i].get();
                if (attr == null) {
                    continue;
                }
                AttributeInstance inst = player.m_21051_(attr);
                if (inst == null) {
                    continue;
                }
                double base = inst.m_22115_();
                if (Double.isFinite(base) && base > 0.0D) {
                    next[i] = (int) Math.round(base);
                    any = true;
                }
            }
            if (any) {
                SNAPSHOTS.put(player.m_20148_(), next);
            }
        } catch (Throwable ignored) {
        }
        suppressRestore(player, suppressTicks);
    }

    /**
     * Finish an intentional DMZ reset. Full resets ({@code keepPercentage == null}) force a
     * zero snapshot; percentage resets adopt live post-reset values.
     */
    public static void endIntentionalReset(Player player, Integer keepPercentage, int suppressTicks) {
        if (player == null) {
            return;
        }
        if (keepPercentage == null) {
            forceZeroSnapshot(player);
        } else {
            adoptCurrent(player);
        }
        suppressRestore(player, suppressTicks);
    }

    private static int indexOf(Attribute attribute) {
        for (int i = 0; i < ATTRS.length; i++) {
            try {
                if (ATTRS[i].get() == attribute) {
                    return i;
                }
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    static AttributeInstance injectAttribute(Player player, Attribute attribute, double base) {
        try {
            Object map = player.m_21204_();
            java.lang.reflect.Field field = null;
            Class<?> c = map.getClass();
            while (c != null && field == null) {
                try {
                    field = c.getDeclaredField("f_22139_");
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }
            if (field == null) {
                return null;
            }
            field.setAccessible(true);
            Object raw = field.get(map);
            if (!(raw instanceof java.util.Map<?, ?>)) {
                return null;
            }
            java.util.Map<Attribute, AttributeInstance> instances =
                    (java.util.Map<Attribute, AttributeInstance>) raw;
            AttributeInstance created = new AttributeInstance(attribute, ignored -> {});
            created.m_22100_(base);
            instances.put(attribute, created);
            return created;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void logRestore(Player player, String key, double from, int to, String reason) {
        int n = LOGS.incrementAndGet();
        if (n <= 40) {
            String name = player instanceof ServerPlayer sp
                    ? sp.m_36316_().getName()
                    : String.valueOf(player.m_20148_());
            MeleeDebug.log(
                    "[{}] restored primary {} player={} base={}->{} reason={}",
                    DmzMohistMeleeFix.MOD_ID,
                    key,
                    name,
                    from,
                    to,
                    reason
            );
        }
    }
}
