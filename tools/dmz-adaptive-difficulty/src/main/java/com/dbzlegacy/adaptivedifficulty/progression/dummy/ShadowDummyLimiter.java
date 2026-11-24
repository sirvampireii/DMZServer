package com.dbzlegacy.adaptivedifficulty.progression.dummy;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Status;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Port of {@code ShadowDummyLimiter.js} + spawn protect from
 * {@code ShadowDummyForgeProtect.js}. Soft-fails when ShadowDummyEntity is absent.
 */
public final class ShadowDummyLimiter {
    private static final long COOLDOWN_MS = 30_000L;
    private static final long SPAWN_PROTECT_MS = 3_000L;
    private static final int SHADOW_PERCENT = 50;
    private static final String TAG_PLAYER_SHADOW = "dmz_player_shadow";
    private static final String TAG_SPAWN_PROTECT = "dmz_minigame_spawn_protect";
    private static final String TAG_SPAWN_PROTECT_UNTIL = "dmz_minigame_spawn_protect_until";
    private static final String NBT_LIMITER_LOCKED = "dmz_shadow_limiter_locked";
    private static final String KEY_COOLDOWN = "lm_shadow_dummy_cd_until";
    /** Legacy CNPC / script key — read so mid-cooldown players keep their CD after migrate. */
    private static final String KEY_COOLDOWN_LEGACY = "dmz_minigame_shadow_dummy_cooldown_until";

    private static final Map<UUID, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PROTECT_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> LAST_ACCEPTED = new ConcurrentHashMap<>();

    private static volatile Boolean shadowClassPresent;
    private static volatile Class<?> shadowClass;

    private ShadowDummyLimiter() {}

    public static boolean shadowAvailable() {
        ensureShadowClass();
        return Boolean.TRUE.equals(shadowClassPresent);
    }

    public static void pulse(ServerPlayer player) {
        if (!DifficultyConfig.get().enableShadowDummyLimiter || player == null || !shadowAvailable()) {
            return;
        }
        StatsData data = DmzProgression.stats(player);
        if (data == null) {
            return;
        }
        Status status;
        try {
            status = data.getStatus();
        } catch (Throwable t) {
            return;
        }
        if (status == null) {
            return;
        }

        long now = System.currentTimeMillis();
        maintainProtect(player, status, now);

        UUID dummyId;
        try {
            dummyId = status.getActiveShadowDummyUUID();
        } catch (Throwable t) {
            return;
        }
        if (dummyId == null) {
            return;
        }

        Entity found = findDummy(player, dummyId);
        if (found == null) {
            return;
        }
        if (!isPlayerShadow(found)) {
            return;
        }

        UUID last = LAST_ACCEPTED.get(player.m_20148_());
        if (dummyId.equals(last)) {
            return;
        }

        long cdUntil = COOLDOWN_UNTIL.getOrDefault(player.m_20148_(), 0L);
        CompoundTag pdata = PersistentDataAccess.get(player);
        if (PersistentDataAccess.isWritable(pdata)) {
            if (pdata.m_128441_(KEY_COOLDOWN)) {
                cdUntil = Math.max(cdUntil, pdata.m_128454_(KEY_COOLDOWN));
            }
            if (pdata.m_128441_(KEY_COOLDOWN_LEGACY)) {
                cdUntil = Math.max(cdUntil, pdata.m_128454_(KEY_COOLDOWN_LEGACY));
            }
        }
        if (now < cdUntil) {
            deny(player, found, data, status, "cooldown");
            return;
        }

        accept(player, found, data, status, dummyId, now);
    }

    public static void onJoin(EntityJoinLevelEvent event) {
        if (!DifficultyConfig.get().enableShadowDummyLimiter || !shadowAvailable()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!isPlayerShadow(entity)) {
            return;
        }
        grantSpawnProtection(entity, System.currentTimeMillis() + SPAWN_PROTECT_MS);
    }

    public static void onHurt(LivingHurtEvent event) {
        if (!DifficultyConfig.get().enableShadowDummyLimiter || !shadowAvailable()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!isPlayerShadow(victim)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!isSpawnProtected(victim, now)) {
            return;
        }
        grantSpawnProtection(victim, PersistentDataAccess.getLong(victim, TAG_SPAWN_PROTECT_UNTIL, now + SPAWN_PROTECT_MS));
        event.setAmount(0.0f);
        event.setCanceled(true);
    }

    private static void accept(
            ServerPlayer player,
            Entity dummy,
            StatsData data,
            Status status,
            UUID dummyId,
            long now
    ) {
        long protectUntil = now + SPAWN_PROTECT_MS;
        grantSpawnProtection(dummy, protectUntil);
        PROTECT_UNTIL.put(player.m_20148_(), protectUntil);

        try {
            Class<?> packet = Class.forName("com.dragonminez.common.network.C2S.SummonPlayerShadowDummyC2S");
            packet.getMethod("removePenalties", ServerPlayer.class, StatsData.class)
                    .invoke(null, player, data);
            if (dummy instanceof LivingEntity living && shadowClass != null && shadowClass.isInstance(dummy)) {
                shadowClass.getMethod("copyStatsFromPlayerWithPercent", ServerPlayer.class, int.class)
                        .invoke(dummy, player, SHADOW_PERCENT);
            }
            packet.getMethod("applyPenalties", ServerPlayer.class, StatsData.class, int.class)
                    .invoke(null, player, data, SHADOW_PERCENT);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] shadow dummy configure soft-fail: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }

        try {
            status.setShadowDummyPercent(SHADOW_PERCENT);
            status.setActiveShadowDummyUUID(dummyId);
        } catch (Throwable ignored) {
        }

        CompoundTag tag = PersistentDataAccess.get(dummy);
        if (PersistentDataAccess.isWritable(tag)) {
            tag.m_128379_(TAG_PLAYER_SHADOW, true);
            tag.m_128405_("dmz_shadow_percent", SHADOW_PERCENT);
            tag.m_128359_("dmz_quest_owner", player.m_20148_().toString());
        }
        fullyHeal(dummy);
        grantSpawnProtection(dummy, protectUntil);

        COOLDOWN_UNTIL.put(player.m_20148_(), now + COOLDOWN_MS);
        LAST_ACCEPTED.put(player.m_20148_(), dummyId);
        CompoundTag pdata = PersistentDataAccess.get(player);
        if (PersistentDataAccess.isWritable(pdata)) {
            pdata.m_128356_(KEY_COOLDOWN, now + COOLDOWN_MS);
            pdata.m_128356_(KEY_COOLDOWN_LEGACY, now + COOLDOWN_MS);
        }

        DmzRewards.msg(player, "§5[Shadow Dummy] §dShadow Dummy summoned.");
        DmzRewards.msg(player, "§7Its maximum health and copied power are fixed at §f50%§7 of your normal stats.");
        SystemTelemetry.log("shadow_dummy", "accept", player, null, Map.of("percent", SHADOW_PERCENT));
    }

    private static void deny(
            ServerPlayer player, Entity dummy, StatsData data, Status status, String reason
    ) {
        try {
            Class<?> packet = Class.forName("com.dragonminez.common.network.C2S.SummonPlayerShadowDummyC2S");
            packet.getMethod("removePenalties", ServerPlayer.class, StatsData.class)
                    .invoke(null, player, data);
            packet.getMethod("clearPlayerShadowDummy", ServerPlayer.class, StatsData.class)
                    .invoke(null, player, data);
        } catch (Throwable ignored) {
        }
        try {
            status.setActiveShadowDummyUUID(null);
        } catch (Throwable ignored) {
        }
        try {
            dummy.m_146870_();
        } catch (Throwable ignored) {
        }
        long left = Math.max(0L, COOLDOWN_UNTIL.getOrDefault(player.m_20148_(), 0L) - System.currentTimeMillis());
        DmzRewards.msg(player, "§c[Shadow Dummy] §7On cooldown — §f"
                + Math.max(1L, left / 1000L) + "s §7remaining.");
        SystemTelemetry.log("shadow_dummy", "deny", player, null, Map.of("reason", reason));
    }

    private static void maintainProtect(ServerPlayer player, Status status, long now) {
        Long until = PROTECT_UNTIL.get(player.m_20148_());
        if (until == null || now >= until) {
            return;
        }
        UUID dummyId;
        try {
            dummyId = status.getActiveShadowDummyUUID();
        } catch (Throwable t) {
            return;
        }
        if (dummyId == null) {
            return;
        }
        Entity dummy = findDummy(player, dummyId);
        if (dummy != null) {
            grantSpawnProtection(dummy, until);
        }
    }

    private static Entity findDummy(ServerPlayer player, UUID dummyId) {
        if (!(player.m_9236_() instanceof ServerLevel level)) {
            return null;
        }
        for (Entity e : level.m_8583_()) {
            if (e != null && dummyId.equals(e.m_20148_())) {
                return e;
            }
        }
        return null;
    }

    private static void grantSpawnProtection(Entity dummy, long untilMs) {
        if (dummy == null) {
            return;
        }
        setInvulnerable(dummy, true);
        bumpHurtInvuln(dummy);
        if (dummy instanceof Mob mob) {
            try {
                mob.m_21557_(true); // setNoAi
                mob.m_6710_(null); // setTarget
            } catch (Throwable ignored) {
            }
        }
        fullyHeal(dummy);
        CompoundTag tag = PersistentDataAccess.get(dummy);
        if (PersistentDataAccess.isWritable(tag)) {
            tag.m_128379_(TAG_SPAWN_PROTECT, true);
            tag.m_128356_(TAG_SPAWN_PROTECT_UNTIL, untilMs);
            tag.m_128379_(NBT_LIMITER_LOCKED, true);
            tag.m_128379_(TAG_PLAYER_SHADOW, true);
        }
    }

    private static boolean isSpawnProtected(Entity dummy, long now) {
        CompoundTag tag = PersistentDataAccess.get(dummy);
        if (!PersistentDataAccess.isWritable(tag)) {
            return false;
        }
        if (tag.m_128471_(NBT_LIMITER_LOCKED)) {
            long until = tag.m_128454_(TAG_SPAWN_PROTECT_UNTIL);
            if (until > 0 && now >= until) {
                tag.m_128379_(NBT_LIMITER_LOCKED, false);
                tag.m_128379_(TAG_SPAWN_PROTECT, false);
                setInvulnerable(dummy, false);
                if (dummy instanceof Mob mob) {
                    try {
                        mob.m_21557_(false);
                    } catch (Throwable ignored) {
                    }
                }
                return false;
            }
            return until <= 0 || now < until;
        }
        if (!tag.m_128471_(TAG_SPAWN_PROTECT)) {
            return false;
        }
        long until = tag.m_128454_(TAG_SPAWN_PROTECT_UNTIL);
        return until > 0 && now < until;
    }

    private static boolean isPlayerShadow(Entity entity) {
        if (entity == null || !isShadowDummy(entity)) {
            return false;
        }
        return PersistentDataAccess.flag(entity, TAG_PLAYER_SHADOW)
                || PersistentDataAccess.flag(entity, "dmz_player_shadow");
    }

    private static boolean isShadowDummy(Entity entity) {
        ensureShadowClass();
        if (!Boolean.TRUE.equals(shadowClassPresent) || shadowClass == null || entity == null) {
            return false;
        }
        try {
            return shadowClass.isInstance(entity);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void ensureShadowClass() {
        if (shadowClassPresent != null) {
            return;
        }
        synchronized (ShadowDummyLimiter.class) {
            if (shadowClassPresent != null) {
                return;
            }
            try {
                shadowClass = Class.forName("com.dragonminez.common.init.entities.ShadowDummyEntity");
                shadowClassPresent = true;
            } catch (Throwable t) {
                shadowClass = null;
                shadowClassPresent = false;
                AdaptiveDifficultyMod.LOGGER.info(
                        "[{}] ShadowDummyEntity absent — limiter soft-disabled", AdaptiveDifficultyMod.MOD_ID);
            }
        }
    }

    private static void setInvulnerable(Entity entity, boolean on) {
        try {
            entity.m_20331_(on);
        } catch (Throwable ignored) {
        }
    }

    private static void bumpHurtInvuln(Entity entity) {
        if (entity instanceof LivingEntity living) {
            try {
                living.f_19802_ = 40; // invulnerableTime
            } catch (Throwable ignored) {
            }
        }
    }

    private static void fullyHeal(Entity entity) {
        if (entity instanceof LivingEntity living) {
            try {
                living.m_21153_(living.m_21233_());
            } catch (Throwable ignored) {
            }
        }
    }

    public static void clearPlayer(UUID uuid) {
        if (uuid == null) {
            return;
        }
        COOLDOWN_UNTIL.remove(uuid);
        PROTECT_UNTIL.remove(uuid);
        LAST_ACCEPTED.remove(uuid);
    }
}
