package com.dbzlegacy.adaptivedifficulty.gui;

import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraGui;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraGuiFeedback;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import net.minecraft.server.level.ServerPlayer;

/** {@code /progression} — staff tools + player android remove. */
public final class ProgressionMenu {
    private ProgressionMenu() {}

    public static void open(ServerPlayer player, String page) {
        if (player == null) {
            return;
        }
        String target = page == null || page.isBlank() ? "main" : page;
        boolean androidRemove = isAndroidRemovePage(target);
        if (!androidRemove && !StaffAccess.isStaff(player)) {
            DmzRewards.msg(player, UltraStyle.dangerLine("That menu is for staff only."));
            return;
        }
        if (!DifficultyConfig.get().enableProgression && !androidRemove) {
            // Disabled-system notice goes in the menu (the hub opens with it), not chat.
            UltraGuiFeedback.deliver(player, UltraStyle.dangerLine(
                    "Progression is turned off on this server. Staff can turn it back on in the"
                            + " config editor under Progress."));
            UltraGui.openHub(player, "main");
            return;
        }

        if (androidRemove) {
            UltraGui.open(player, "character", "android_remove");
        } else {
            UltraGui.open(player, "progression", target);
        }
    }

    private static boolean isAndroidRemovePage(String page) {
        if (page == null) {
            return false;
        }
        return switch (page.toLowerCase()) {
            case "android_remove", "androidremove", "remove_android", "deandroid" -> true;
            default -> false;
        };
    }
}
