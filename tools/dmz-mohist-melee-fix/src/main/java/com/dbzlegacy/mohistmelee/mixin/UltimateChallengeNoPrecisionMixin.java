package com.dbzlegacy.mohistmelee.mixin;

import com.dbzlegacy.mohistmelee.DmzMohistMeleeFix;
import com.dbzlegacy.mohistmelee.MeleeDebug;
import com.dragonminez.common.training.MinigameLogics;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Old Kai Ultimate Challenge stages: Control → Gravity → Memory → Precision → Rhythm.
 * This drops Precision.
 * <p>
 * <b>Not registered on DragonMineZ 2.2.</b> In 2.1.3 the list lived in the client-only
 * {@code UltimateChallenge} constructor. In 2.2 it is {@code MinigameLogics.CHALLENGE_STAGES},
 * which the client walks and the server checks stage by stage
 * ({@code MinigameSessionManager.validateChallengeStage} / {@code resolveChallenge}). Both sides
 * must hold the same list: a client that still runs Precision while the server skipped it (or the
 * other way round) fails the challenge at stage 4. Register this in the common list only when
 * every client also runs this jar; the client modpack does not ship it today.
 */
@Mixin(value = MinigameLogics.class, remap = false)
public abstract class UltimateChallengeNoPrecisionMixin {
    private static boolean LOGGED;

    /** {@code <clinit>} builds IDS first (ordinal 0) and CHALLENGE_STAGES second (ordinal 1). */
    @Redirect(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
                    ordinal = 1,
                    remap = false
            ),
            remap = false
    )
    private static List<?> dbzlegacy$skipPrecisionStage(
            Object control,
            Object gravity,
            Object memory,
            Object precision,
            Object rhythm
    ) {
        if (!LOGGED) {
            LOGGED = true;
            MeleeDebug.log(
                    "[{}] Old Kai Ultimate Challenge: skipping Precision stage",
                    DmzMohistMeleeFix.MOD_ID
            );
        }
        return List.of(control, gravity, memory, rhythm);
    }
}
