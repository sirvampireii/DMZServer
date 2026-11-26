package com.dbzlegacy.adaptivedifficulty.command;

import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * 4.7.88: every Legacy Mechanics command lives under {@code /lm}. Player branches ({@code /lm difficulty},
 * {@code /lm rival}, {@code /lm spar}, {@code /lm dojo}, {@code /lm prestige}, {@code /lm character},
 * {@code /lm progression}, {@code /lm skillcheck}, {@code /lm terminal}, ...) keep their own rules. Staff tools sit
 * under {@code /lm admin <system>} and every staff-only leaf needs {@link StaffAccess#isAdminSource}: staff, and in
 * creative mode when a player runs it (console unchanged).
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

    /** The one root every LM command hangs from. Each system registers its branch through it (Brigadier merges). */
    public static LiteralArgumentBuilder<CommandSourceStack> lmRoot() {
        return Commands.m_82127_("lm").requires(CommandAccess::isPlayerSlashUser);
    }

    /** {@code /lm admin}: staff, in creative when a player runs it. */
    public static LiteralArgumentBuilder<CommandSourceStack> adminRoot() {
        return staffLiteral("admin");
    }

    /** {@code /lm <branch> ...} attached to the shared root, ready for {@code dispatcher.register}. */
    public static LiteralArgumentBuilder<CommandSourceStack> underLm(LiteralArgumentBuilder<CommandSourceStack> branch) {
        return lmRoot().then(branch);
    }

    /** {@code /lm admin <branch> ...} attached to the shared root, ready for {@code dispatcher.register}. */
    public static LiteralArgumentBuilder<CommandSourceStack> underLmAdmin(
            LiteralArgumentBuilder<CommandSourceStack> branch) {
        return lmRoot().then(adminRoot().then(branch));
    }

    /**
     * Staff-only literal (hides the subtree from tab complete and parsing for everyone else). 4.7.88: staff
     * players also need creative mode.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> staffLiteral(String name) {
        return Commands.m_82127_(name).requires(StaffAccess::isAdminSource);
    }
}
