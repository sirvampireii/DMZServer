package com.dbzlegacy.adaptivedifficulty.gui;

import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraGui;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import net.minecraft.server.level.ServerPlayer;

/** {@code /skills} (staff) and Skill Check Ultra entry. */
public final class SkillsMenu {
    private SkillsMenu() {}

    public static void open(ServerPlayer player, String page) {
        if (player == null) {
            return;
        }
        if (!StaffAccess.isStaff(player)) {
            DmzRewards.msg(player, UltraStyle.dangerLine("Staff only."));
            return;
        }
        com.dbzlegacy.adaptivedifficulty.progression.shop.SkillCheckService.clearSession(player);
        openInternal(player, page, false);
    }

    public static void openSkillCheck(ServerPlayer player, String page) {
        if (player == null) {
            return;
        }
        if (!com.dbzlegacy.adaptivedifficulty.progression.shop.SkillCheckService.canUse(player)
                && !com.dbzlegacy.adaptivedifficulty.progression.shop.SkillCheckService.inSession(player)) {
            DmzRewards.msg(
                    player,
                    UltraStyle.noticeLine(
                            "Skill Check is a donator perk — ask staff if you want access."));
            return;
        }
        openInternal(player, page, true);
    }

    private static void openInternal(ServerPlayer player, String page, boolean skillCheck) {
        if (!DifficultyConfig.get().enableSkillUnlockService) {
            return;
        }
        if (skillCheck && !DifficultyConfig.get().enableSkillCheck) {
            return;
        }
        String target = page == null || page.isBlank() ? "core" : page;
        if (skillCheck) {
            UltraGui.open(player, "skillcheck", target);
        } else {
            UltraGui.open(player, "skills", target);
        }
    }
}
