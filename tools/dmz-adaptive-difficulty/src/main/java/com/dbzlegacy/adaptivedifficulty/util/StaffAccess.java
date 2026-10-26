package com.dbzlegacy.adaptivedifficulty.util;

import com.dbzlegacy.adaptivedifficulty.command.LmCommandFeedback;
import com.dbzlegacy.adaptivedifficulty.command.LmCommandMessages;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Shared access checks: staff (op level 2 / {@code difficulty.admin}), donators (Skill Check),
 * the donator sub-nodes for self revive and the terminal, and revive-others access.
 */
public final class StaffAccess {
    private StaffAccess() {}

    /**
     * Brigadier {@code .requires()} / admin handlers — use {@link CommandSourceStack#m_230896_()}
     * not {@code getPlayer()} (throws on Mohist and hides whole subtrees).
     */
    public static boolean isStaffSource(CommandSourceStack src) {
        if (src == null) {
            return false;
        }
        try {
            if (src.m_6761_(2)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        ServerPlayer player = src.m_230896_();
        return player != null && isStaff(player);
    }

    /**
     * {@code /dmzstats reset} stays available to staff. A player who is not
     * staff is turned away. Prestige does not use this command.
     */
    public static boolean allowDmzStatsReset(CommandSourceStack source) {
        return isStaffSource(source);
    }

    public static void denyDmzStatsReset(CommandSourceStack source) {
        if (source == null) {
            return;
        }
        try {
            source.m_288197_(() -> Component.m_237113_("§cStat reset is turned off."), false);
        } catch (Throwable ignored) {
        }
    }

    /** {@code 1} if staff; else sends denial and returns {@code 0}. */
    public static int denyUnlessStaff(CommandSourceStack source) {
        if (isStaffSource(source)) {
            return 1;
        }
        LmCommandFeedback.tell(source, LmCommandMessages.NEED_STAFF);
        return 0;
    }

    public static boolean isStaff(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        try {
            if (player.m_20310_(2)) { // permission level / op
                return true;
            }
        } catch (Throwable ignored) {
        }
        // Mohist: Bukkit OP often does not map to Forge permission level 2.
        if (hasBukkitIsOp(player)) {
            return true;
        }
        String node = DifficultyConfig.get().adminPermission;
        if (node == null || node.isBlank()) {
            node = "difficulty.admin";
        }
        return hasBukkitPermission(player, node);
    }

    /** The one donator node ({@code donatorPermission}, default {@code legacymechanics.donator}). */
    public static String donatorNode() {
        String node = DifficultyConfig.get().donatorPermission;
        return node == null || node.isBlank() ? "legacymechanics.donator" : node.trim();
    }

    /** Donator node only, without the Skill Check feature flags. */
    public static boolean hasDonatorPermission(ServerPlayer player) {
        return player != null && hasBukkitPermission(player, donatorNode());
    }

    /** Self revive node (default {@code legacymechanics.donator.selfrevive}); the base donator node is not enough. */
    public static String selfReviveNode() {
        String node = DifficultyConfig.get().selfRevivePermission;
        return node == null || node.isBlank() ? "legacymechanics.donator.selfrevive" : node.trim();
    }

    public static boolean hasSelfRevive(ServerPlayer player) {
        return player != null && hasBukkitPermission(player, selfReviveNode());
    }

    /** Terminal node (default {@code legacymechanics.donator.terminal}); the base donator node is not enough. */
    public static String terminalNode() {
        String node = DifficultyConfig.get().terminalPermission;
        return node == null || node.isBlank() ? "legacymechanics.donator.terminal" : node.trim();
    }

    public static boolean hasTerminalPermission(ServerPlayer player) {
        return player != null && hasBukkitPermission(player, terminalNode());
    }

    /** Node for {@code /lm revive <player>} without staff (default {@code legacymechanics.revive.others}). */
    public static String reviveOthersNode() {
        String node = DifficultyConfig.get().reviveOthersPermission;
        return node == null || node.isBlank() ? "legacymechanics.revive.others" : node.trim();
    }

    public static boolean hasReviveOthers(ServerPlayer player) {
        return player != null && hasBukkitPermission(player, reviveOthersNode());
    }

    /** Staff (console included) or a player holding the revive-others node. */
    public static boolean hasReviveOthersSource(CommandSourceStack src) {
        if (src == null) {
            return false;
        }
        if (isStaffSource(src)) {
            return true;
        }
        ServerPlayer player = src.m_230896_();
        return player != null && hasReviveOthers(player);
    }

    /** {@code 1} if staff or revive access; else sends a denial and returns {@code 0}. */
    public static int denyUnlessReviveOthers(CommandSourceStack source) {
        if (hasReviveOthersSource(source)) {
            return 1;
        }
        LmCommandFeedback.tell(source, UltraStyle.dangerLine(
                "Reviving other players is for staff or players with revive access. Ask staff if you need it."));
        return 0;
    }

    /** Donator Skill Check ({@code /skillcheck}) — staff are not auto-granted; console may use staff op. */
    public static boolean hasSkillCheckSource(CommandSourceStack src) {
        if (src == null) {
            return false;
        }
        ServerPlayer player = src.m_230896_();
        if (player != null) {
            return hasSkillCheck(player);
        }
        return isStaffSource(src);
    }

    public static int denyUnlessSkillCheck(CommandSourceStack source) {
        if (hasSkillCheckSource(source)) {
            return 1;
        }
        source.m_288197_(() -> Component.m_237113_(LmCommandMessages.SKILLCHECK_DONATOR), false);
        return 0;
    }

    public static boolean hasSkillCheck(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        DifficultyConfig cfg = DifficultyConfig.get();
        if (!cfg.enableSkillCheck || !cfg.enableSkillUnlockService) {
            return false;
        }
        // Staff are not auto-granted: without the donator node they use /skills (admin).
        return hasBukkitPermission(player, donatorNode());
    }

    private static boolean hasBukkitIsOp(ServerPlayer player) {
        Object bukkit = bukkitEntity(player);
        if (bukkit == null) {
            return false;
        }
        try {
            Object result = bukkit.getClass().getMethod("isOp").invoke(bukkit);
            return result instanceof Boolean b && b;
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static boolean hasPermission(ServerPlayer player, String node) {
        return hasBukkitPermission(player, node);
    }

    private static boolean hasBukkitPermission(ServerPlayer player, String node) {
        if (player == null || node == null || node.isBlank()) {
            return false;
        }
        Object bukkit = bukkitEntity(player);
        if (bukkit == null) {
            return false;
        }
        try {
            Object result = bukkit.getClass().getMethod("hasPermission", String.class).invoke(bukkit, node);
            return result instanceof Boolean b && b;
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Mohist/CraftBukkit bridge — NMS {@code ServerPlayer} often lacks {@code hasPermission} directly. */
    private static Object bukkitEntity(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        try {
            Object bp = player.getClass().getMethod("getBukkitEntity").invoke(player);
            if (bp != null) {
                return bp;
            }
        } catch (Throwable ignored) {
        }
        try {
            UUID id = player.m_20148_();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object byId = bukkit.getMethod("getPlayer", UUID.class).invoke(null, id);
            if (byId != null) {
                return byId;
            }
            String name = player.m_6302_();
            if (name != null && !name.isBlank()) {
                return bukkit.getMethod("getPlayerExact", String.class).invoke(null, name);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
