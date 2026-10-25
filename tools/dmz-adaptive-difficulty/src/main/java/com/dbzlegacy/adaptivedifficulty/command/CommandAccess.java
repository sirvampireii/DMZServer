package com.dbzlegacy.adaptivedifficulty.command;

import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Player-facing slash commands: {@code /lm}, {@code /diff}, {@code /difficulty},
 * {@code /rival}, {@code /spar}, {@code /progression}, {@code /prestige} (shop),
 * {@code /character} (services), {@code /skillcheck} (donators), {@code /terminal}
 * (donators), and {@code /progression android remove} (self only). Everything else is staff.
 */
public final class CommandAccess {
    private CommandAccess() {}

    public static boolean isStaff(CommandSourceStack src) {
        return StaffAccess.isStaffSource(src);
    }

    public static boolean hasSkillCheck(CommandSourceStack src) {
        return StaffAccess.hasSkillCheckSource(src);
    }

    /** In-game player commands (hub GUIs). Console must be staff. */
    public static boolean isPlayerSlashUser(CommandSourceStack src) {
        if (src == null) {
            return false;
        }
        if (src.m_230896_() != null) {
            return true;
        }
        return isStaff(src);
    }

    public static int denyUnlessPlayerSlashUser(CommandSourceStack source) {
        if (isPlayerSlashUser(source)) {
            return 1;
        }
        source.m_288197_(() -> Component.m_237113_(LmCommandMessages.PLAYERS_ONLY), false);
        return 0;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> playerRoot(String name) {
        return Commands.m_82127_(name).requires(CommandAccess::isPlayerSlashUser);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> staffRoot(String name) {
        return Commands.m_82127_(name).requires(StaffAccess::isStaffSource);
    }

    /** Staff-only literal (hides subtree from tab complete and parsing for non-staff). */
    public static LiteralArgumentBuilder<CommandSourceStack> staffLiteral(String name) {
        return Commands.m_82127_(name).requires(StaffAccess::isStaffSource);
    }

    /** Bukkit {@code PluginCommand} permission for Mohist bridge (defaults {@code difficulty.admin}). */
    public static String staffBukkitPermission() {
        String node = DifficultyConfig.get().adminPermission;
        return node == null || node.isBlank() ? "difficulty.admin" : node;
    }

    public static String skillCheckBukkitPermission() {
        return StaffAccess.donatorNode();
    }

    /**
     * Bukkit labels that stay staff-only at the bridge because their Forge trees are
     * staff-rooted. {@code prestige} and {@code character} are player-facing now (their
     * Forge trees gate per-branch), so players reach them through the bridge.
     */
    public static boolean isStaffOnlyBukkitCommand(String label) {
        if (label == null || label.isBlank()) {
            return false;
        }
        return switch (label.toLowerCase()) {
            case "skills", "enddragon", "androidify", "padmin" -> true;
            default -> false;
        };
    }

    public static boolean isSkillCheckBukkitCommand(String label) {
        return label != null && "skillcheck".equalsIgnoreCase(label);
    }
}
