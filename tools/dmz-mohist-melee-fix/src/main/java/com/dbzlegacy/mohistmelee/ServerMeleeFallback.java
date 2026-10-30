package com.dbzlegacy.mohistmelee;

import com.dragonminez.common.combat.logic.player.PlayerAttackHelper;
import com.dragonminez.common.combat.logic.player.TargetHelper;
import com.dragonminez.common.combat.player.AttackHand;
import com.dragonminez.common.init.MainAttributes;
import com.dragonminez.common.network.C2S.CombatAttackRequestC2S;
import com.dragonminez.server.events.players.combat.CombatEvent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * DMZ sends {@code MeleeAnimationS2C} <b>before</b> iterating packet entity IDs.
 * After a world change the client often sends an empty list or stale IDs that do not
 * resolve on the server — animation plays, {@code ServerPlayer.attack} never runs.
 * <p>
 * If the packet produced no LivingHurt (no {@code dmz_last_hit_target_time} bump),
 * scan server-side for a valid target in front of the player and attack it.
 * Still uses vanilla {@code attack()} — no damage redirects.
 * <p>
 * DragonMineZ 2.2 runs hits through {@code MeleeWindupTracker}: {@code processAttackRequest} is
 * called only once the windup has passed (from the packet, or later from the player tick). The
 * rescue hangs off {@code processAttackRequest} ({@code CombatAttackRequestHandleMixin}): the hit
 * time is read at its HEAD and the rescue is queued at its RETURN, behind DMZ's own runnable.
 */
public final class ServerMeleeFallback {
    private static final Logger LOGGER = LogManager.getLogger(DmzMohistMeleeFix.MOD_ID);
    private static final AtomicInteger LOGS = new AtomicInteger();
    private static final int MAX_FALLBACK_HITS = 4;
    /** Hemisphere in front of the player (dot product of look vs to-target). */
    private static final double MIN_ALIGN = 0.0D;

    /** Last-hit time read at {@code processAttackRequest} HEAD, consumed at its RETURN. */
    private static final ThreadLocal<ArrayDeque<Long>> HIT_TIME_BEFORE = ThreadLocal.withInitial(ArrayDeque::new);

    private ServerMeleeFallback() {}

    /** Mohist repairs before a melee packet is handled: stale strike lock, reach, primaries. */
    public static void preHitRepairs(ServerPlayer player, String reason) {
        CombatUnlock.clearStaleStrikeLock(player, reason);
        ReachAttributeFix.repair(player, reason);
        PrimaryStatRepair.ensure(player, reason);
        if (player.m_21205_().m_41619_() && isPrimaryWiped(player)) {
            RespawnLikeRecovery.apply(player, reason + "-empty-hand");
        }
    }

    /** {@code processAttackRequest} HEAD: DMZ's hit runnable has not run yet. */
    public static void beginAttackRequest(ServerPlayer player) {
        HIT_TIME_BEFORE.get().push(player == null ? 0L
                : PersistentDataAccess.get(player).m_128454_(CombatEvent.DMZ_LAST_HIT_TARGET_TIME_TAG));
    }

    /**
     * {@code processAttackRequest} RETURN: DMZ has queued (or, from the windup tick, already run)
     * its hit. Queue the rescue after it.
     */
    public static void queueRescue(ServerPlayer player, CombatAttackRequestC2S request) {
        Long hitTimeBefore = HIT_TIME_BEFORE.get().poll();
        if (player == null || request == null || hitTimeBefore == null) {
            return;
        }
        int packetIds = request.getEntityIds() == null ? 0 : request.getEntityIds().length;
        MinecraftServer server = player.m_20194_();
        if (server != null) {
            server.execute(() -> maybeRescue(player, request, hitTimeBefore, packetIds));
        } else {
            maybeRescue(player, request, hitTimeBefore, packetIds);
        }
    }

    /**
     * Must be queued on the server <em>after</em> {@code processAttackRequest}'s runnable
     * so we can see whether a hit already landed.
     */
    public static void maybeRescue(
            ServerPlayer player,
            CombatAttackRequestC2S request,
            long hitTimeBefore,
            int packetIdCount
    ) {
        if (player == null || player.m_9236_().f_46443_ || player.m_213877_()) {
            return;
        }
        // DMZ 2.2 refuses these before striking; the rescue must not strike for them either.
        if (player.m_21224_() || player.m_5833_()
                || request.getSelectedSlot() != player.m_150109_().f_35977_) {
            return;
        }
        long hitTimeAfter = PersistentDataAccess.get(player).m_128454_(CombatEvent.DMZ_LAST_HIT_TARGET_TIME_TAG);
        if (hitTimeAfter > hitTimeBefore) {
            return; // packet path already applied LivingHurt
        }

        // Same pre-hit repairs as the packet path — empty-hand STR / reach can still be wiped.
        preHitRepairs(player, "melee-fallback");

        AttackHand hand = PlayerAttackHelper.getCurrentAttack((Player) player, request.getComboCount());
        if (hand == null || hand.attributes() == null) {
            int n = LOGS.incrementAndGet();
            if (n <= 40) {
                MeleeDebug.log(
                        "[{}] melee fallback: no AttackHand player={} combo={} packetIds={}",
                        DmzMohistMeleeFix.MOD_ID,
                        player.m_36316_().getName(),
                        request.getComboCount(),
                        packetIdCount
                );
            }
            return;
        }

        double weaponRange = hand.attributes().attackRange();
        double maxRange = PlayerAttackHelper.getEffectiveAttackRange((Player) player, weaponRange);
        if (!Double.isFinite(maxRange) || maxRange < 0.75D) {
            maxRange = Math.max(2.0D, weaponRange);
        }

        Level level = player.m_9236_();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // Prefer packet IDs first; if none hit, always AABB-scan nearby.
        // Stale IDs can resolve to the wrong entity after a dim change and would otherwise
        // block the nearby rescue (candidates non-empty but all out of range / facing).
        List<LivingEntity> packetCandidates = resolvePacketCandidates(player, serverLevel, request);
        int nullIds = countNullPacketIds(serverLevel, request);

        int hits = tryHitCandidates(player, packetCandidates, maxRange, hitTimeBefore);
        int nearbyCount = -1;
        if (hits == 0) {
            List<LivingEntity> nearby = scanNearby(player, serverLevel, maxRange);
            nearbyCount = nearby.size();
            // Prefer entities not already tried from stale packet IDs; if that yields nothing,
            // retry the full nearby set (covers "stale ID == only nearby mob" cases).
            Set<Integer> seen = new LinkedHashSet<>();
            for (LivingEntity e : packetCandidates) {
                seen.add(e.m_19879_());
            }
            List<LivingEntity> fresh = new ArrayList<>();
            for (LivingEntity e : nearby) {
                if (!seen.contains(e.m_19879_())) {
                    fresh.add(e);
                }
            }
            hits = tryHitCandidates(
                    player,
                    fresh.isEmpty() ? nearby : fresh,
                    maxRange,
                    hitTimeBefore
            );
        }

        if (hits > 0) {
            int n = LOGS.incrementAndGet();
            if (n <= 60) {
                MeleeDebug.log(
                        "[{}] melee fallback HIT player={} hits={} packetIds={} nullIds={} nearby={} range={}",
                        DmzMohistMeleeFix.MOD_ID,
                        player.m_36316_().getName(),
                        hits,
                        packetIdCount,
                        nullIds,
                        nearbyCount,
                        maxRange
                );
            }
        } else {
            int n = LOGS.incrementAndGet();
            if (n <= 40) {
                MeleeDebug.log(
                        "[{}] melee fallback: no valid target player={} packetCand={} nearby={} packetIds={} nullIds={} range={}",
                        DmzMohistMeleeFix.MOD_ID,
                        player.m_36316_().getName(),
                        packetCandidates.size(),
                        nearbyCount,
                        packetIdCount,
                        nullIds,
                        maxRange
                );
            }
        }
    }

    private static List<LivingEntity> resolvePacketCandidates(
            ServerPlayer player,
            ServerLevel serverLevel,
            CombatAttackRequestC2S request
    ) {
        List<LivingEntity> candidates = new ArrayList<>();
        int[] ids = request.getEntityIds();
        if (ids == null) {
            return candidates;
        }
        for (int id : ids) {
            Entity entity = TargetHelper.getEntityOrPart((Level) serverLevel, id);
            if (entity == null) {
                continue;
            }
            entity = TargetHelper.resolveHittable(entity);
            if (entity instanceof LivingEntity living && living.m_6084_() && living != player) {
                candidates.add(living);
            }
        }
        return candidates;
    }

    private static int countNullPacketIds(ServerLevel serverLevel, CombatAttackRequestC2S request) {
        int[] ids = request.getEntityIds();
        if (ids == null) {
            return 0;
        }
        int nullIds = 0;
        for (int id : ids) {
            if (TargetHelper.getEntityOrPart((Level) serverLevel, id) == null) {
                nullIds++;
            }
        }
        return nullIds;
    }

    private static List<LivingEntity> scanNearby(ServerPlayer player, ServerLevel serverLevel, double maxRange) {
        double inflate = maxRange + 2.5D;
        AABB box = player.m_20191_().m_82400_(inflate);
        List<LivingEntity> nearby = serverLevel.m_45976_(LivingEntity.class, box);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity living : nearby) {
            if (living == null || living == player || !living.m_6084_()) {
                continue;
            }
            out.add(living);
        }
        return out;
    }

    private static int tryHitCandidates(
            ServerPlayer player,
            List<LivingEntity> candidates,
            double maxRange,
            long hitTimeBefore
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }

        Vec3 look = player.m_20154_().m_82541_();
        Vec3 eye = player.m_146892_();
        double rangeSq = maxRange * maxRange + 16.0D;
        final double useRange = maxRange;

        candidates.sort(Comparator.comparingDouble((LivingEntity e) -> {
            Vec3 to = e.m_20191_().m_82399_().m_82546_(eye);
            double dist = player.m_20280_(e);
            double align = to.m_82556_() < 1.0E-6D ? 0.0D : to.m_82541_().m_82526_(look);
            return dist - align * 4.0D; // prefer closer + more in-front
        }));

        int hits = 0;
        for (LivingEntity target : candidates) {
            if (hits >= MAX_FALLBACK_HITS) {
                break;
            }
            if (player.m_20280_(target) > rangeSq) {
                continue;
            }
            if (!TargetHelper.canAttack((Player) player, (Entity) target, useRange + 4.0D)) {
                continue;
            }
            Vec3 to = target.m_20191_().m_82399_().m_82546_(eye);
            if (to.m_82556_() < 1.0E-6D) {
                continue;
            }
            double align = to.m_82541_().m_82526_(look);
            if (align < MIN_ALIGN) {
                continue;
            }

            PersistentDataAccess.get(player).m_128379_("dmz_first_hit", hits == 0);
            TargetHelper.Relation relation = TargetHelper.getRelation((Player) player, (Entity) target);
            TargetHelper.onSuccessfulAttack((Player) player, (Entity) target, relation);
            float healthBefore = target.m_21223_();
            player.m_5706_(target);
            long hitTag = PersistentDataAccess.get(player).m_128454_(CombatEvent.DMZ_LAST_HIT_TARGET_TIME_TAG);
            boolean damaged = target.m_21223_() < healthBefore
                    || target.f_20916_ > 0
                    || hitTag > hitTimeBefore;
            if (damaged) {
                hits++;
            }
        }
        PersistentDataAccess.get(player).m_128473_("dmz_first_hit");
        return hits;
    }

    private static boolean isPrimaryWiped(ServerPlayer player) {
        try {
            Attribute str = MainAttributes.STRENGTH.get();
            if (str == null) {
                return false;
            }
            AttributeInstance inst = player.m_21051_(str);
            if (inst == null) {
                return true;
            }
            double base = inst.m_22115_();
            return !Double.isFinite(base) || base <= 0.0D;
        } catch (Throwable t) {
            return false;
        }
    }
}
