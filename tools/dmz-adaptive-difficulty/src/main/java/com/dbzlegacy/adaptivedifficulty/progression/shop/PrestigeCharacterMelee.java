package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.mohistmelee.PrimaryStatRepair;
import net.minecraft.server.level.ServerPlayer;

/**
 * The only class that names the DMZ Mohist melee fix's stat snapshot (4.7.41). {@link PrestigeCharacter}
 * loads it after checking the mod is installed, so LM runs without it.
 * <p>
 * The melee fix keeps each account's last six DragonMineZ stats (keyed by the account UUID, not the
 * character) and writes them back over any of those stats it reads as 0, to undo Mohist's dimension-change
 * wipes. A new character's stats are all 0, so without this the first stat read after a character switch
 * put the previous character's stats on the new one.
 */
final class PrestigeCharacterMelee {
    private PrestigeCharacterMelee() {}

    /** Forget the account's saved stats: nothing is written back over a reset to 0. */
    static void zero(ServerPlayer player) {
        PrimaryStatRepair.forceZeroSnapshot(player);
    }

    /** The saved stats become the active character's own (including any 0). */
    static void adopt(ServerPlayer player) {
        PrimaryStatRepair.adoptCurrent(player);
    }
}
