package com.dbzlegacy.adaptivedifficulty.character;

import com.dragonminez.common.wish.wishes.ReCustomizeWish;
import net.minecraft.server.level.ServerPlayer;

/**
 * Opens DMZ's re-customize editor for LM's reskin and race-change class pick.
 * <p>
 * DragonMineZ 2.2 only applies the editor's {@code UpdateCharacterC2S} when the player holds a
 * pending re-customize wish ({@code ReCustomizeWish.consume}); sending {@code OpenRecustomizeS2C}
 * alone opens a screen whose save is thrown away. {@code ReCustomizeWish.grant} marks the wish
 * pending (15 minutes, one save) and sends the same open packet.
 */
public final class DmzRecustomizeEditor {
    private static final ReCustomizeWish WISH = new ReCustomizeWish("lm_recustomize", "");

    private DmzRecustomizeEditor() {}

    public static void open(ServerPlayer player) {
        if (player != null) {
            WISH.grant(player);
        }
    }
}
