package com.dbzlegacy.mohistmelee.mixin;

import com.dragonminez.common.quest.Difficulty;
import com.dragonminez.common.quest.QuestService;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Calls DragonMineZ's private {@code QuestService.spawnKillObjectives} so the saga enemy top-up
 * ({@link com.dbzlegacy.mohistmelee.SagaEnemyCap}) spawns exactly as quest accept and re-summon do:
 * DMZ still counts the alive mobs, positions them and stamps the quest tags.
 */
@Mixin(value = QuestService.class, remap = false)
public interface QuestServiceInvoker {
    @Invoker(value = "spawnKillObjectives", remap = false)
    static void callSpawnKillObjectives(
            ServerPlayer requester, QuestService.ResolvedQuest resolved, int partySize, Difficulty difficulty) {
        throw new AssertionError("QuestServiceInvoker was not applied");
    }
}
