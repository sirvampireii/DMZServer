package com.dbzlegacy.legacytextures.faceanim;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client copy of each player's own face animation switch ({@code /faceanim}), sent by the server on
 * login, when a player comes into view, and on every change. A player the server never sent is on.
 */
public final class FaceFlags {
    private static final Map<UUID, Boolean> FLAGS = new ConcurrentHashMap<>();

    private FaceFlags() {
    }

    public static boolean enabled(UUID player) {
        return player == null || FLAGS.getOrDefault(player, Boolean.TRUE);
    }

    public static void set(UUID player, boolean on) {
        if (player != null) {
            FLAGS.put(player, on);
        }
    }

    /** Leaving a server forgets its players' switches. */
    public static void clear() {
        FLAGS.clear();
    }
}
