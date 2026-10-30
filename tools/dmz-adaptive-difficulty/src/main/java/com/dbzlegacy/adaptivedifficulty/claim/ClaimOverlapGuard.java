package com.dbzlegacy.adaptivedifficulty.claim;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;
import dev.architectury.event.CompoundEventResult;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.event.ClaimedChunkEvent;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Blocks FTB Chunks claims that overlap an existing GriefPrevention claim.
 */
public final class ClaimOverlapGuard {
    private static final String DENY_KEY = "legacymechanics.claim_overlap_gp";

    private ClaimOverlapGuard() {}

    public static void register() {
        try {
            ClaimedChunkEvent.BEFORE_CLAIM.register(ClaimOverlapGuard::beforeClaim);
            AdaptiveDifficultyMod.LOGGER.info("[{}] FTB Chunks overlap guard registered", AdaptiveDifficultyMod.MOD_ID);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Failed to register FTB Chunks overlap guard: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    t.toString()
            );
        }
    }

    private static CompoundEventResult<ClaimResult> beforeClaim(CommandSourceStack source, ClaimedChunk chunk) {
        if (chunk == null) {
            return CompoundEventResult.pass();
        }
        if (source != null) {
            try {
                var entity = source.m_81375_();
                if (entity instanceof ServerPlayer) {
                    ServerPlayer player = (ServerPlayer) entity;
                    if (canBypass(player)) {
                        return CompoundEventResult.pass();
                    }
                }
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) {
            }
        }
        ChunkDimPos pos = chunk.getPos();
        if (pos == null) {
            return CompoundEventResult.pass();
        }
        if (!GriefPreventionProbe.overlapsGriefPrevention(pos)) {
            return CompoundEventResult.pass();
        }
        LmLog.event(
                "[{}] Blocked FTB Chunks claim at {} — overlaps GriefPrevention",
                AdaptiveDifficultyMod.MOD_ID,
                pos
        );
        return CompoundEventResult.interruptFalse(ClaimResult.customProblem(DENY_KEY));
    }

    private static boolean canBypass(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (player.m_20310_(4)) {
            return true;
        }
        try {
            Object bukkitPlayer = player.getClass().getMethod("getBukkitEntity").invoke(player);
            return bukkitPlayer != null
                    && bukkitPlayer.getClass().getMethod("hasPermission", String.class)
                            .invoke(bukkitPlayer, "legacymechanics.claimoverlap.bypass")
                            .equals(Boolean.TRUE);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
