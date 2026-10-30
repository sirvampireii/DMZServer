package com.dbzlegacy.mohistmelee.mixin;

import com.dbzlegacy.mohistmelee.GhostPartyHeal;
import com.dbzlegacy.mohistmelee.PersonalSagaGuard;
import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.server.world.data.PartySavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Party operations must not hand already-finished quests to players who did not earn them, and
 * must not wipe saga progress on leave, login, or death.
 * <p>
 * DragonMineZ 2.2 has no party quest merge any more ({@code syncQuestProgress} and
 * {@code onPlayerLogin} are gone). Every operation that moves players between parties or pushes
 * party state onto members runs inside {@link PersonalSagaGuard#beginPartyOp} /
 * {@link PersonalSagaGuard#endPartyOp}: completions that appear during it are refused
 * ({@code QuestProgressMergeMixin}) or stripped afterwards unless earned or already accepted.
 * Ghost party UI state is cleared after a leave and after the login reconcile.
 * <p>
 * Every target is pinned by descriptor. {@code leaveParty(ServerPlayer)} is a one-line stub that
 * calls {@code leaveParty(ServerPlayer, boolean)}, so a name-only target would run each hook twice.
 */
@Mixin(value = PartyManager.class, remap = false)
public abstract class PartyManagerSyncQuestMixin {

    private static final String JOIN_PARTY =
            "joinParty(Lnet/minecraft/server/level/ServerPlayer;"
                    + "Lcom/dragonminez/server/world/data/PartySavedData$PartyInstance;"
                    + "Lnet/minecraft/server/level/ServerPlayer;)V";
    private static final String JOIN_FOR_FUSION =
            "joinPartyForFusion(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/UUID;Z)V";
    private static final String RECONCILE = "reconcile(Lnet/minecraft/server/level/ServerPlayer;)V";
    private static final String SYNC_DIFFICULTY =
            "syncPartyDifficulty(Lnet/minecraft/server/level/ServerPlayer;)V";
    private static final String KICK_MEMBER =
            "kickMember(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/UUID;)"
                    + "Lcom/dragonminez/common/quest/PartyManager$KickResult;";
    private static final String LEAVE_PARTY = "leaveParty(Lnet/minecraft/server/level/ServerPlayer;Z)V";

    @Inject(method = JOIN_PARTY, at = @At("HEAD"), remap = false)
    private static void dbzlegacy$beginJoin(
            ServerPlayer leader,
            PartySavedData.PartyInstance party,
            ServerPlayer member,
            CallbackInfo ci
    ) {
        PersonalSagaGuard.beginPartyOp(dbzlegacy$partyOf(leader, member));
    }

    @Inject(method = JOIN_PARTY, at = @At("RETURN"), remap = false)
    private static void dbzlegacy$endJoin(
            ServerPlayer leader,
            PartySavedData.PartyInstance party,
            ServerPlayer member,
            CallbackInfo ci
    ) {
        PersonalSagaGuard.endPartyOp();
    }

    @Inject(method = JOIN_FOR_FUSION, at = @At("HEAD"), remap = false)
    private static void dbzlegacy$beginFusionJoin(ServerPlayer mover, UUID partyId, boolean restore, CallbackInfo ci) {
        List<ServerPlayer> players = dbzlegacy$partyOf(mover, null);
        // The party the mover is about to join.
        try {
            PartySavedData.PartyInstance target = partyId == null || mover.m_20194_() == null
                    ? null : PartySavedData.get(mover.m_20194_()).getParty(partyId);
            if (target != null) {
                for (UUID id : target.getMembers()) {
                    ServerPlayer member = mover.m_20194_().m_6846_().m_11259_(id);
                    if (member != null && !players.contains(member)) {
                        players.add(member);
                    }
                }
            }
        } catch (Throwable ignored) {
            // Party SavedData not ready: the mover and their current party are still guarded.
        }
        PersonalSagaGuard.beginPartyOp(players);
    }

    @Inject(method = JOIN_FOR_FUSION, at = @At("RETURN"), remap = false)
    private static void dbzlegacy$endFusionJoin(ServerPlayer mover, UUID partyId, boolean restore, CallbackInfo ci) {
        PersonalSagaGuard.endPartyOp();
    }

    @Inject(method = RECONCILE, at = @At("HEAD"), remap = false)
    private static void dbzlegacy$beginReconcile(ServerPlayer player, CallbackInfo ci) {
        PersonalSagaGuard.beginPartyOp(dbzlegacy$partyOf(player, null));
    }

    /** Login path in 2.2 (QuestEvents.onPlayerDataReady). Clears a ghost party left in quest NBT. */
    @Inject(method = RECONCILE, at = @At("RETURN"), remap = false)
    private static void dbzlegacy$endReconcile(ServerPlayer player, CallbackInfo ci) {
        PersonalSagaGuard.endPartyOp();
        GhostPartyHeal.healIfGhost(player);
    }

    @Inject(method = SYNC_DIFFICULTY, at = @At("HEAD"), remap = false)
    private static void dbzlegacy$beginDifficulty(ServerPlayer source, CallbackInfo ci) {
        PersonalSagaGuard.beginPartyOp(dbzlegacy$partyOf(source, null));
    }

    @Inject(method = SYNC_DIFFICULTY, at = @At("RETURN"), remap = false)
    private static void dbzlegacy$endDifficulty(ServerPlayer source, CallbackInfo ci) {
        PersonalSagaGuard.endPartyOp();
    }

    @Inject(method = KICK_MEMBER, at = @At("HEAD"), remap = false)
    private static void dbzlegacy$beginKick(
            ServerPlayer leader,
            UUID targetId,
            CallbackInfoReturnable<PartyManager.KickResult> cir
    ) {
        PersonalSagaGuard.beginPartyOp(dbzlegacy$partyOf(leader, null));
    }

    @Inject(method = KICK_MEMBER, at = @At("RETURN"), remap = false)
    private static void dbzlegacy$endKick(
            ServerPlayer leader,
            UUID targetId,
            CallbackInfoReturnable<PartyManager.KickResult> cir
    ) {
        PersonalSagaGuard.endPartyOp();
    }

    @Inject(method = LEAVE_PARTY, at = @At("HEAD"), remap = false)
    private static void dbzlegacy$beginLeave(ServerPlayer player, boolean forfeitTournament, CallbackInfo ci) {
        PersonalSagaGuard.beginPartyOp(dbzlegacy$partyOf(player, null));
    }

    /**
     * Full overload only: the 1-arg {@code leaveParty} delegates here, so this runs once per leave.
     * Clears a ghost party left in quest NBT after every leave attempt.
     */
    @Inject(method = LEAVE_PARTY, at = @At("RETURN"), remap = false)
    private static void dbzlegacy$endLeave(ServerPlayer player, boolean forfeitTournament, CallbackInfo ci) {
        PersonalSagaGuard.endPartyOp();
        GhostPartyHeal.healIfGhost(player);
    }

    /** The player, an extra player (a joining member), and everyone online in the player's party. */
    private static List<ServerPlayer> dbzlegacy$partyOf(ServerPlayer player, ServerPlayer extra) {
        List<ServerPlayer> players = new ArrayList<>();
        if (player != null) {
            players.add(player);
            try {
                for (ServerPlayer member : PartyManager.getAllPartyMembers(player)) {
                    if (!players.contains(member)) {
                        players.add(member);
                    }
                }
            } catch (Throwable ignored) {
                // Party SavedData not ready (early login): the player alone is still guarded.
            }
        }
        if (extra != null && !players.contains(extra)) {
            players.add(extra);
        }
        return players;
    }
}
