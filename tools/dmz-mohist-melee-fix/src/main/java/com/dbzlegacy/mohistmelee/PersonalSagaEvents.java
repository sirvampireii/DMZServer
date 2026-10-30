package com.dbzlegacy.mohistmelee;

import com.dragonminez.common.events.DMZEvent;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Personal saga completion earn tracking + reward claim gate.
 * Does <b>not</b> wipe saga progress on login, leave, or death.
 */
public final class PersonalSagaEvents {
    private static final Logger LOGGER = LogManager.getLogger(DmzMohistMeleeFix.MOD_ID);
    private static final AtomicInteger CLAIM_LOGS = new AtomicInteger();
    private static final String CLAIM_MSG =
            "\u00A7cYou must complete this quest yourself before claiming rewards."
                    + " Party progress from others does not count.";

    private PersonalSagaEvents() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new PersonalSagaEvents());
    }

    /**
     * DragonMineZ 2.2 posts this once per completing player (each turn-in participant gets its own
     * {@code finishTurnIn}). Credit that player only: the admin finish lists the whole party, and
     * party members who were not on the quest did not earn it. {@code completeQuest} credits too.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onQuestCompleted(DMZEvent.QuestCompletedEvent event) {
        String questKey = event.getQuestKey();
        if (questKey == null || questKey.isBlank() || event.getPlayer() == null) {
            return;
        }
        PersonalSagaGuard.markEarned(event.getPlayer(), questKey);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRewardClaim(DMZEvent.QuestRewardClaimEvent event) {
        ServerPlayer player = event.getPlayer();
        String questKey = event.getQuestKey();
        if (player == null || questKey == null || questKey.isBlank()) {
            return;
        }
        PersonalSagaGuard.ensureBootstrapped(player);
        if (PersonalSagaGuard.hasEarned(player, questKey)) {
            return;
        }
        event.setCanceled(true);
        player.m_5661_(Component.m_237113_(CLAIM_MSG), true);
        int n = CLAIM_LOGS.incrementAndGet();
        if (n <= 40) {
            MeleeDebug.log(
                    "[{}] blocked unearned quest reward claim: player={} quest={} rewardIndex={}",
                    DmzMohistMeleeFix.MOD_ID,
                    player.m_36316_().getName(),
                    questKey,
                    event.getRewardIndex()
            );
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            // Bootstrap only — never purge existing saga progress on login/respawn.
            PersonalSagaGuard.ensureBootstrapped(sp);
            // Belt-and-suspenders: clear V-menu ghost party if SavedData is solo.
            GhostPartyHeal.healIfGhost(sp);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PersonalSagaGuard.unload(sp.m_20148_());
        }
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer neu)
                || !(event.getOriginal() instanceof ServerPlayer old)) {
            return;
        }
        // Same UUID across death — keep earn marks; do not unload/rebootstrap empty.
        PersonalSagaGuard.carryOverAfterClone(old, neu);
    }
}
