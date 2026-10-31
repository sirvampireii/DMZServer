package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dmzultra.ledger.SlotKey;
import com.dmzultra.slots.api.CharacterEvent;
import com.dmzultra.slots.api.SlotSummary;
import com.dmzultra.slots.api.Slots;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * The only class that names DMZ Ultra's slots API. {@link PrestigeCharacter} loads it after checking
 * the API is installed, so LM runs without Ultra.
 */
final class PrestigeCharacterUltra {
    private PrestigeCharacterUltra() {}

    static void register() {
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL, false, CharacterEvent.SwitchIn.class, PrestigeCharacterUltra::onSwitchIn);
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL, false, CharacterEvent.Born.class, PrestigeCharacterUltra::onBorn);
    }

    /** The active character's UUID, or null when Ultra's slots are off. */
    static UUID characterId(ServerPlayer player) {
        return Slots.enabled() ? Slots.characterId(player) : null;
    }

    private static void onSwitchIn(CharacterEvent.SwitchIn event) {
        SlotKey from = event.from();
        PrestigeCharacter.switchedIn(event.player(), from == null ? null : Slots.characterId(from));
    }

    private static void onBorn(CharacterEvent.Born event) {
        ServerPlayer player = event.player();
        SlotKey key = event.key();
        if (player == null || key == null || !Slots.enabled()) {
            return;
        }
        long created = 0L;
        for (SlotSummary s : Slots.summaries(player.m_20194_(), key.account())) {
            if (!s.deleted() && s.key().slot() == key.slot()) {
                created = s.created();
            }
        }
        PrestigeCharacter.born(player, key.account(), key.slot(), Slots.characterId(key), created);
    }
}
