package com.dbzlegacy.adaptivedifficulty.rival;

import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player policy for dmzultra nemesis spawns (the rival-journal mob that
 * visits and fights players on its own). Three states:
 *
 * <ul>
 *   <li>{@link #AUTO} — current dmzultra behavior: tick visits can spawn a nemesis
 *       and deliberate spawns come too (the default; nothing changes for players
 *       who never touch the toggle).
 *   <li>{@link #MANUAL} — automatic tick-visits never spawn; everything the player
 *       starts (journal {@code challenge}, writ proving summons) still comes.
 *   <li>{@link #OFF} — no automatic spawns; the deliberate nemesis-menu summon
 *       (journal {@code challenge}) still works.
 * </ul>
 *
 * Persisted per player in entity custom data. The dmzultra side is gated by
 * {@code DmzultraNemesisSpawnGateMixin} (bytecode call graph: {@code Rival.meet}
 * is the spawn choke — {@code onTick} calls it with "tired"/"found", the manual
 * journal challenge calls it with "appear"; writ actions reach {@code spawn}
 * through {@code Rival.champion} directly).
 */
public final class NemesisSpawnMode {

    /** Tick visits and deliberate spawns may come — dmzultra's shipped behavior. */
    public static final int AUTO = 0;
    /** Automatic tick-visits never spawn; player-started spawns (challenge, writs) do. */
    public static final int MANUAL = 1;
    /** No automatic spawns; the nemesis-menu challenge still summons your nemesis. */
    public static final int OFF = 2;

    private static final String KEY = "lmNemesisSpawnMode";

    private NemesisSpawnMode() {}

    /** Stored policy, clamped to a known state. Missing key = {@link #AUTO}. */
    public static int mode(ServerPlayer player) {
        if (player == null) {
            return AUTO;
        }
        int stored = com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore.getInt(player, KEY, AUTO);
        return stored == MANUAL || stored == OFF ? stored : AUTO;
    }

    /**
     * Deliberate writ proving summons may spawn — everything except {@link #OFF},
     * where only the nemesis-menu challenge survives. Used by the mixin's
     * {@code champion}-origin branch at {@code spawn}.
     */
    public static boolean allowManual(ServerPlayer player) {
        return mode(player) != OFF;
    }

    /** Automatic tick-driven visits may spawn a nemesis (only {@link #AUTO}). */
    public static boolean allowTickSpawn(ServerPlayer player) {
        return mode(player) == AUTO;
    }

    /** Advance AUTO → MANUAL → OFF → AUTO, persist, and return menu-inline feedback. */
    public static String cycle(ServerPlayer player) {
        if (player == null) {
            return "§cPlayers only.";
        }
        int next = switch (mode(player)) {
            case AUTO -> MANUAL;
            case MANUAL -> OFF;
            default -> AUTO;
        };
        com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore.putInt(player, KEY, next);
        return switch (next) {
            case MANUAL -> "§eNemesis spawn - manual. " + description(MANUAL);
            case OFF -> "§8Nemesis spawn - OFF. " + description(OFF);
            default -> "§aNemesis spawn - ON. " + description(AUTO);
        };
    }

    /**
     * Plain one-line description of a state (no formatting codes) — the single
     * source for toggle feedback and the Character Services hint line.
     */
    public static String description(int mode) {
        return switch (mode) {
            case MANUAL -> "No automatic visits - challenges and writ summons you start still come.";
            case OFF -> "No automatic nemesis spawns — you can still summon yours from the nemesis menu.";
            default -> "Your nemesis can come for you anytime.";
        };
    }

    /** Short state word for labels: {@code ON}, {@code manual}, or {@code OFF}. */
    public static String stateName(int mode) {
        return switch (mode) {
            case MANUAL -> "manual";
            case OFF -> "OFF";
            default -> "ON";
        };
    }
}
