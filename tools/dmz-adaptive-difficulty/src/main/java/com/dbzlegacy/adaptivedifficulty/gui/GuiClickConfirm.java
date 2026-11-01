package com.dbzlegacy.adaptivedifficulty.gui;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Second click within 10 seconds confirms a free menu action (title clear, free tier downgrade,
 * boost start/end, staff Android convert). Since 4.7.21 nothing that spends money, favor, points or
 * prestige uses this: those open a confirm page with Confirm and Back instead.
 */
public final class GuiClickConfirm {
    public static final long WINDOW_MS = 10_000L;

    private static final ConcurrentHashMap<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private GuiClickConfirm() {}

    /**
     * @return true when this call is the confirming click for {@code key}
     */
    public static boolean confirmed(UUID actor, String key) {
        if (actor == null || key == null || key.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        Pending existing = PENDING.get(actor);
        if (existing != null && key.equals(existing.key) && existing.until > now) {
            PENDING.remove(actor, existing);
            return true;
        }
        PENDING.put(actor, new Pending(key, now + WINDOW_MS));
        return false;
    }

    private static final class Pending {
        final String key;
        final long until;

        Pending(String key, long until) {
            this.key = key;
            this.until = until;
        }
    }
}
