package com.dbzlegacy.mohistmelee.mixin;

import com.dbzlegacy.mohistmelee.DmzMohistMeleeFix;
import com.dbzlegacy.mohistmelee.MeleeDebug;
import com.dbzlegacy.mohistmelee.PersonalSagaGuard;
import com.dragonminez.common.quest.PlayerQuestData.QuestProgress;
import com.dragonminez.common.quest.PlayerQuestData.QuestStatus;
import java.util.concurrent.atomic.AtomicInteger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents party operations from granting {@code SUCCESS} to players who were not on the quest.
 * <p>
 * DragonMineZ 2.2 deleted {@code QuestProgress.mergeForwardFrom}. A progress now only reaches
 * {@code SUCCESS} through {@code setStatus}; a real completion ({@code completeQuest}) also calls
 * {@code recordCompletion}, which bumps {@code timesCompleted} and {@code lastCompletedRealMs}.
 * While a party operation runs ({@link PersonalSagaGuard#inPartyOp}), a progress that was not
 * {@code ACCEPTED} (or already {@code SUCCESS}) may not become {@code SUCCESS}. Outside party
 * operations nothing changes: solo and co-op turn-ins, admin finishes, and migrations go through.
 */
@Mixin(value = QuestProgress.class, remap = false)
public abstract class QuestProgressMergeMixin {
    private static final AtomicInteger LOGS = new AtomicInteger();

    @Shadow
    public abstract String getQuestId();

    @Shadow
    public abstract QuestStatus getStatus();

    @Inject(
            method = "setStatus(Lcom/dragonminez/common/quest/PlayerQuestData$QuestStatus;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void dbzlegacy$blockUnearnedSuccess(QuestStatus status, CallbackInfo ci) {
        if (status != QuestStatus.SUCCESS || !PersonalSagaGuard.inPartyOp()) {
            return;
        }
        QuestStatus own = getStatus();
        if (PersonalSagaGuard.mayTakeSuccessDuringPartyOp(own)) {
            return;
        }
        // Refuse borrowed completions entirely (the strip after the party operation is the safety net).
        ci.cancel();
        if (LOGS.incrementAndGet() <= 40) {
            MeleeDebug.log(
                    "[{}] blocked party SUCCESS: quest={} ownStatus={}",
                    DmzMohistMeleeFix.MOD_ID,
                    getQuestId(),
                    own
            );
        }
    }
}
