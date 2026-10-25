package com.dbzlegacy.adaptivedifficulty.gui;

import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraGui;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import net.minecraft.server.level.ServerPlayer;

/** Player UI entrypoint for {@code /difficulty}. */
public final class DifficultyMenu {
    private DifficultyMenu() {}

    public static void open(ServerPlayer player, String page) {
        if (player == null) {
            return;
        }
        String target = page == null || page.isBlank() ? "main" : page;
        if ("settings".equalsIgnoreCase(target)) {
            if (!StaffAccess.isStaff(player)) {
                target = "main";
            } else {
                // Staff settings live in the Ultra config editor, not a chat page.
                UltraGui.open(player, "config", "edit:difficulty:0:");
                return;
            }
        }
        UltraGui.open(player, "difficulty", target);
    }
}
