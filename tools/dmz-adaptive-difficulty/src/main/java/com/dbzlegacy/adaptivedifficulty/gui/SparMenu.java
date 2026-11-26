package com.dbzlegacy.adaptivedifficulty.gui;

import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraGui;
import net.minecraft.server.level.ServerPlayer;

/** Player UI entrypoint for {@code /lm spar}. */
public final class SparMenu {
    private SparMenu() {}

    public static void open(ServerPlayer player, String page) {
        if (player == null) {
            return;
        }
        String target = page == null || page.isBlank() ? "main" : page;
        UltraGui.open(player, "spar", target);
    }
}
