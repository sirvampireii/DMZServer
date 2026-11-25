package com.dbzlegacy.adaptivedifficulty.gui;

import com.dbzlegacy.adaptivedifficulty.character.CharacterServicesConfig;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraGui;
import net.minecraft.server.level.ServerPlayer;

/** Player UI for Character Services ({@code /lm character} / hub). */
public final class CharacterServicesMenu {
    private CharacterServicesMenu() {}

    public static void open(ServerPlayer player, String page) {
        if (player == null || !CharacterServicesConfig.get().enabled) {
            return;
        }
        String target = page == null || page.isBlank() ? "main" : page;
        UltraGui.open(player, "character", target);
    }
}
