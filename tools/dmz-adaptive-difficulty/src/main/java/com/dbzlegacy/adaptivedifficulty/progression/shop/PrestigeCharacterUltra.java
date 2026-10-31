package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dmzultra.ledger.SlotKey;
import com.dmzultra.slots.SlotStore;
import com.dmzultra.slots.api.CharacterEvent;
import com.dmzultra.slots.api.SlotSummary;
import com.dmzultra.slots.api.Slots;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
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
        ServerPlayer player = event.player();
        SlotKey from = event.from();
        SlotKey to = event.to();
        boolean blank = player != null && to != null && Slots.enabled()
                && blankRecord(player.m_20194_(), to);
        PrestigeCharacter.switchedIn(player, from == null ? null : Slots.characterId(from),
                to == null ? null : Slots.characterId(to), to == null ? -1 : to.slot(), blank);
    }

    private static void onBorn(CharacterEvent.Born event) {
        ServerPlayer player = event.player();
        SlotKey key = event.key();
        if (player == null || key == null || !Slots.enabled()) {
            return;
        }
        MinecraftServer server = player.m_20194_();
        Long created = null;
        for (SlotSummary s : Slots.summaries(server, key.account())) {
            if (!s.deleted() && s.key().slot() == key.slot()) {
                created = s.created();
                break;
            }
        }
        PrestigeCharacter.born(player, key.account(), key.slot(), Slots.characterId(key), created,
                blankRecord(server, key));
    }

    /**
     * The slot's stored record is still the blank one {@code Lifecycle.create} wrote: flagged blank,
     * with no DragonMineZ part. A switch leaves the incoming slot's record on disk until it is parked
     * again, so this is what the character was born from, or switched back into without ever being
     * parked with stats. Unreadable counts as not blank.
     */
    private static boolean blankRecord(MinecraftServer server, SlotKey key) {
        try {
            CompoundTag record = SlotStore.readRecord(server, key.account(), key.slot());
            return record != null && record.m_128471_("blank")
                    && record.m_128469_("parts").m_128469_("dmz").m_128456_();
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] slot {} of {} could not be read to confirm it is new: {}",
                    AdaptiveDifficultyMod.MOD_ID, key.slot() + 1, key.account(), t.toString());
            return false;
        }
    }
}
