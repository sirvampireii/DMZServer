package com.dbzlegacy.adaptivedifficulty.progression.shop;

import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;

/**
 * Soft warnings when prestiging would strip a universe role (God of Destruction / Supreme Kai).
 *
 * <p>KaiPath is optional at compile time, so it is resolved by reflection. A missing mod or any
 * reflection failure returns false so prestige is never blocked by a failed check.
 */
public final class PrestigeUniverseRoleWarn {
    /** Player-facing GoD warning (plain ASCII). */
    public static final String GOD_OF_DESTRUCTION =
            "You are a God of Destruction - prestiging will remove you from your universe role.";

    /** Player-facing Supreme Kai warning (plain ASCII). */
    public static final String SUPREME_KAI =
            "You are a Supreme Kai - prestiging will remove you from your universe role.";

    /** Cached {@code KaiApi.isSupremeKai(ServerPlayer)}. Misses are not cached. */
    private static volatile Method KAI_IS_SUPREME;

    private PrestigeUniverseRoleWarn() {}

    /**
     * Always false since 4.7.47. The God of Destruction role this warned about was Noea's; Noea
     * is gone. DMZ Ultra has its own Destroyer path ({@code Hosts.destroyer().rank}), but nothing
     * in Ultra reacts to prestige, so prestiging does not take that seat away and there is
     * nothing to warn about. The method stays so its callers keep compiling unchanged.
     */
    public static boolean isGodOfDestruction(ServerPlayer player) {
        return false;
    }

    /**
     * True when KaiPath reports this player as a Supreme Kai. False when KaiPath is absent or
     * the check fails.
     */
    public static boolean isSupremeKai(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        try {
            Method method = KAI_IS_SUPREME;
            if (method == null) {
                Class<?> api = Class.forName(
                        "com.dmzlegacy.kaipath.KaiApi",
                        false,
                        PrestigeUniverseRoleWarn.class.getClassLoader());
                method = api.getMethod("isSupremeKai", ServerPlayer.class);
                KAI_IS_SUPREME = method;
            }
            Object result = method.invoke(null, player);
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
