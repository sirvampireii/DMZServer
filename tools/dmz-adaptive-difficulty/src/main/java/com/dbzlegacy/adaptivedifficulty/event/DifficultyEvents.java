package com.dbzlegacy.adaptivedifficulty.event;

import com.dbzlegacy.adaptivedifficulty.ai.AdaptiveAiSystem;
import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache;
import com.dbzlegacy.adaptivedifficulty.calc.DifficultySnapshot;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.calc.PlayerCombatProfile;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy;
import com.dbzlegacy.adaptivedifficulty.evolution.CombatGravity;
import com.dbzlegacy.adaptivedifficulty.evolution.EnemyEvolution;
import com.dbzlegacy.adaptivedifficulty.progression.DmzInflatedAttributeReset;
import com.dbzlegacy.adaptivedifficulty.progression.PlayerStatChecker;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionSystem;
import com.dbzlegacy.adaptivedifficulty.progression.end.EndProgression;
import com.dbzlegacy.adaptivedifficulty.reward.RewardSystem;
import com.dbzlegacy.adaptivedifficulty.rival.RivalProgression;
import com.dbzlegacy.adaptivedifficulty.rival.RivalStore;
import com.dbzlegacy.adaptivedifficulty.rival.RivalSystem;
import com.dbzlegacy.adaptivedifficulty.scaling.AreaDifficulty;
import com.dbzlegacy.adaptivedifficulty.scaling.HostileMobs;
import com.dbzlegacy.adaptivedifficulty.scaling.MobScaling;
import com.dbzlegacy.adaptivedifficulty.scaling.SlimeSplitGuard;
import com.dbzlegacy.adaptivedifficulty.service.DifficultyActions;
import com.dbzlegacy.adaptivedifficulty.sparring.SparStore;
import com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem;
import com.dbzlegacy.adaptivedifficulty.tick.BehaviorScheduler;
import com.dbzlegacy.adaptivedifficulty.tick.CombatIndex;
import com.dbzlegacy.adaptivedifficulty.tick.NearbyMobScaler;
import com.dbzlegacy.adaptivedifficulty.telemetry.BalanceTelemetry;
import com.dbzlegacy.adaptivedifficulty.tick.ScaledMobTracker;
import com.dbzlegacy.adaptivedifficulty.title.TitleEffects;
import com.dbzlegacy.adaptivedifficulty.title.TitleSense;
import com.dbzlegacy.adaptivedifficulty.title.TitleSystem;
import com.dbzlegacy.adaptivedifficulty.util.DimensionGates;
import com.dbzlegacy.adaptivedifficulty.util.NearbyPlayers;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import com.dbzlegacy.adaptivedifficulty.util.SystemGate;
import com.dbzlegacy.adaptivedifficulty.world.VanillaDifficultyGuard;
import com.dragonminez.common.events.DMZEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class DifficultyEvents {
    /**
     * Players whose personal toggle was forced OFF for this server boot.
     * Cleared on {@link ServerStartingEvent}; first login that boot resets personal OFF.
     * Mid-session reconnects keep the in-session toggle.
     */
    private static final java.util.Set<UUID> PERSONAL_OFF_THIS_BOOT =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Last polled DMZ form×stack multiplier — catches custom races without FormChangeEvent. */
    private static final Map<UUID, Double> LAST_FORM_MULT = new ConcurrentHashMap<>();
    /** Last polled live offense peak — catches custom forms that never bump form multipliers. */
    private static final Map<UUID, Double> LAST_LIVE_OFFENSE = new ConcurrentHashMap<>();
    /** Last polled race id — clear form baselines when players swap custom races. */
    private static final Map<UUID, String> LAST_RACE = new ConcurrentHashMap<>();
    /** Last polled form key — catches future races that swap forms without mult spikes. */
    private static final Map<UUID, String> LAST_FORM_KEY = new ConcurrentHashMap<>();
    private static final Map<String, Long> PULSE_WARN_AT = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        DifficultyConfig.load();
        try {
            com.dbzlegacy.adaptivedifficulty.progression.race.RaceLockConfig.load();
            com.dbzlegacy.adaptivedifficulty.character.CharacterServicesConfig.load();
            com.dbzlegacy.adaptivedifficulty.quest.SagaResetConfig.load();
            com.dbzlegacy.adaptivedifficulty.character.CharacterServicesPermissionBootstrap.register();
            com.dbzlegacy.adaptivedifficulty.character.CharacterServicesStore.get().load();
        } catch (Throwable ignored) {
        }
        DifficultyCache.invalidateAll();
        AreaDifficulty.clearCache();
        CombatIndex.clear();
        PERSONAL_OFF_THIS_BOOT.clear();
        LAST_FORM_MULT.clear();
        LAST_LIVE_OFFENSE.clear();
        LAST_RACE.clear();
        LAST_FORM_KEY.clear();
        RivalStore.get().load();
        SparStore.get().load();
        try {
            com.dbzlegacy.adaptivedifficulty.sparring.DojoRankings.ensureSeason();
        } catch (Throwable ignored) {
        }
        try {
            com.dbzlegacy.adaptivedifficulty.progression.tp.GlobalTpBoost.load();
        } catch (Throwable ignored) {
        }
        try {
            com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampConfigBridge.onServerStarting();
        } catch (Throwable ignored) {
        }
        RivalProgression.get().load();
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        com.dbzlegacy.adaptivedifficulty.command.MohistCommandBridge.tryRegister(event.getServer());
        DragonBallRadarPickup.registerGriefPrevention();
        VanillaDifficultyGuard.restoreIfPeaceful(event.getServer());
        try {
            com.dbzlegacy.adaptivedifficulty.data.CnpcDataMigrator.migrateWorldIfNeeded(event.getServer());
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] CNPC world migration hook failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // Never throw from stop handlers — a hot-swapped LM jar can split classloaders and
        // abort shutdown while players are still flushed to disk (invalid playerdata).
        try {
            BalanceTelemetry.flushAndClose();
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] BalanceTelemetry stop: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
        try {
            com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry.flushAndClose();
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] SystemTelemetry stop: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
        try {
            RivalStore.get().save();
            SparStore.get().save();
            RivalProgression.get().save();
            com.dbzlegacy.adaptivedifficulty.progression.tp.GlobalTpBoost.save();
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public void onDmzPlayerDataLoad(DMZEvent.PlayerDataLoadEvent event) {
        if (event == null || !(event.getPlayer() instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.getPlayer();
        var server = player.m_20194_();
        Runnable work = () -> {
            try {
                com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampPrestigeBridge
                        .syncFromLegacy(player);
                com.dbzlegacy.adaptivedifficulty.progression.PersonalLevelCapMirror.publish(player);
                com.dbzlegacy.adaptivedifficulty.progression.PrestigeResourceRecovery.pulse(player);
                com.dbzlegacy.adaptivedifficulty.character.CosmeticHeadBoneService.settleAfterLogin(player);
            } catch (Throwable ignored) {
            }
        };
        if (server != null) {
            server.execute(work);
        } else {
            work.run();
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                com.dbzlegacy.adaptivedifficulty.net.gui.LmGuiNetwork.logClientGuiCapability(player);
            } catch (Throwable ignored) {
            }
            DmzInflatedAttributeReset.onLogin(player);
            var data = DifficultyCache.data(player);
            // Every server boot: personal difficulty starts OFF for everyone.
            // First login that boot forces it; reconnects later in the same uptime keep the toggle.
            if (PERSONAL_OFF_THIS_BOOT.add(player.m_20148_()) && data.isPersonalEnabled()) {
                data.setPersonalEnabled(false);
                ScaledMobTracker.releaseAndRevertPlayer(player);
                NearbyMobScaler.processEvictions();
            }
            // Convert any leftover NBT Ancient Coin wallet into real Lightman's items (once/session).
            AncientCoinEconomy.migrateWalletToItems(player);
            try {
                com.dbzlegacy.adaptivedifficulty.data.CnpcDataMigrator.migratePlayerIfNeeded(player);
            } catch (Throwable ignored) {
            }
            DifficultyCache.refresh(player);
            // Persist any unlock-list repairs from refresh so the next disconnect keeps the tier.
            DifficultyCache.save(player);
            TitleSystem.syncTierTitles(player, false);
            RivalSystem.onLogin(player);
            SparringSystem.onLogin(player);
            ProgressionSystem.onLogin(player);
            try {
                com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulCapMath.pinOverhaulLevelCaps();
                com.dbzlegacy.adaptivedifficulty.progression.PersonalLevelCapMirror.publish(player);
                com.dbzlegacy.adaptivedifficulty.progression.PrestigeResourceRecovery.pulse(player);
            } catch (Throwable ignored) {
            }
            try {
                com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampPrestigeBridge
                        .syncFromLegacy(player);
            } catch (Throwable ignored) {
            }
            var server = player.m_20194_();
            if (server != null) {
                server.execute(() -> {
                    if (com.dbzlegacy.adaptivedifficulty.character.RaceHeadBoneSync.syncCharacter(player)) {
                        com.dbzlegacy.adaptivedifficulty.character.RaceHeadBoneSync.syncClient(player);
                    }
                    // Self-heal: a character stuck with a Saiyan tail flag from an old DMZ
                    // race change clears it on the next login (next tick, once DMZ data
                    // attaches - same readiness path as the head-bone reapply above).
                    com.dbzlegacy.adaptivedifficulty.character.SaiyanTailWipeHelper
                            .clearIfRaceCannotHaveTail(player, "DifficultyEvents.onLogin");
                    // Same self-heal for a Noea Evil Eye stranded off demon by an old race
                    // change - clears the worn stage (ownership/timers kept).
                    com.dbzlegacy.adaptivedifficulty.character.EvilEyeWipeHelper
                            .clearIfNotDemon(player);
                });
            }
            // DMZ Character often attaches after this event — keep pulling level until ready.
            DifficultyActions.scheduleLevelPull(player);
        }
    }

    /**
     * Soft-lock TP at the player's personal breakthrough level cap so further
     * progress requires the next breakthrough. Server DMZ {@code maxValue} is 150k
     * (so clients can display/buy into raised caps); personal soft-locks keep
     * non-breakthrough players at 100k / breakthrough buyers at 110k…150k.
     * <p>Silent while at cap — chat spam during combat/mining was unusable.
     * Over-cap buy attempts still tip via {@link #onStatChange}.
     */
    @SubscribeEvent
    public void onTpGain(DMZEvent.TPGainEvent event) {
        if (event == null || event.getTpGain() <= 0) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (DifficultyConfig.get().enablePrestigeSystem) {
            try {
                var data = DmzProgression.stats(player);
                if (data != null) {
                    int raw = Math.max(0, data.getLevel());
                    int cap = com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem
                            .effectiveMaxLevel(player);
                    if (raw >= cap) {
                        event.setTpGain(0);
                        return;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Soft-lock primary-stat purchases past the personal breakthrough ceiling.
     * DMZ {@code maxValue} is 150k for client UI/level math; this reverts over-cap
     * buys and refunds the spent points as pending attribute points.
     */
    @SubscribeEvent
    public void onStatChange(DMZEvent.StatChangeEvent event) {
        if (event == null || event.getNewValue() <= event.getOldValue()) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!DifficultyConfig.get().enablePrestigeSystem) {
            return;
        }
        try {
            int personal = com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem
                    .effectiveMaxLevel(player);
            var data = DmzProgression.stats(player);
            if (data == null || data.getStats() == null) {
                return;
            }
            int total = data.getStats().getTotalStats();
            int maxTotal = com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulCapMath
                    .maxAssignableTotal(data, personal);
            if (total <= maxTotal) {
                return;
            }
            int delta = event.getNewValue() - event.getOldValue();
            if (delta <= 0) {
                return;
            }
            // Revert this primary and refund as pending AP (menu spends AP first).
            revertPrimaryStat(data, event.getStat(), event.getOldValue());
            try {
                data.getResources().addPendingAttributePoints(delta);
            } catch (Throwable ignored) {
            }
            com.dbzlegacy.adaptivedifficulty.progression.DmzSkillUtil.sync(player);
            // Title tip only — no chat. Long cooldown so hold-to-buy cannot spam.
            com.dbzlegacy.adaptivedifficulty.util.ScreenNotify.hint(
                    player,
                    "Personal Cap",
                    "Breakthrough in /lm → Prestige ("
                            + com.dbzlegacy.adaptivedifficulty.util.DmzRewards.formatWhole(personal)
                            + ")",
                    "pp_stat_cap_title",
                    120_000L
            );
        } catch (Throwable ignored) {
        }
    }

    private static void revertPrimaryStat(
            com.dragonminez.common.stats.StatsData data,
            DMZEvent.StatChangeEvent.StatType type,
            int oldValue
    ) {
        if (data == null || data.getStats() == null || type == null) {
            return;
        }
        var stats = data.getStats();
        int v = Math.max(0, oldValue);
        switch (type) {
            case STRENGTH -> stats.setStrength(v);
            case STRIKE_POWER -> stats.setStrikePower(v);
            case RESISTANCE -> stats.setResistance(v);
            case VITALITY -> stats.setVitality(v);
            case KI_POWER -> stats.setKiPower(v);
            case ENERGY -> stats.setEnergy(v);
            default -> {
            }
        }
    }

    /**
     * Forge persists players via SaveToFile — often before {@link PlayerEvent.PlayerLoggedOutEvent}.
     * Always flush AD data here so purchased tiers survive disconnect.
     */
    @SubscribeEvent
    public void onSaveToFile(PlayerEvent.SaveToFile event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DifficultyCache.save(player);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                com.dbzlegacy.adaptivedifficulty.net.gui.LmGuiNetwork.clearClientSession(player);
            } catch (Throwable ignored) {
            }
            com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.onLogout(player);
            com.dbzlegacy.adaptivedifficulty.rival.ProvingGroundsBar.onLogout(player);
            com.dbzlegacy.adaptivedifficulty.util.TpBossNotify.onLogout(player);
            // Flush player AD data only — do NOT clear active tier on disconnect.
            DifficultyCache.save(player);
            DifficultyCache.remove(player.m_20148_());
            CombatGravity.clearPlayer(player);
            // Queue claimed mobs for base-stat revert, then apply immediately.
            // (Mob scaling reverts; the player's purchased unlock tier is kept.)
            ScaledMobTracker.releaseAndRevertPlayer(player);
            NearbyMobScaler.processEvictions();
            PlayerCombatProfile.clear(player.m_20148_());
            PlayerCombatProfile.clearFormBaseline(player.m_20148_());
            DmzProgression.clearBaseFormLevel(player.m_20148_());
            LAST_FORM_MULT.remove(player.m_20148_());
            LAST_LIVE_OFFENSE.remove(player.m_20148_());
            LAST_RACE.remove(player.m_20148_());
            LAST_FORM_KEY.remove(player.m_20148_());
            AncientCoinEconomy.clearMigrateFlag(player.m_20148_());
            com.dbzlegacy.adaptivedifficulty.character.ReskinSessionGuard.clear(player);
            com.dbzlegacy.adaptivedifficulty.character.CosmeticHeadBoneService.restorePreview(player);
            AreaDifficulty.clearCache();
            RivalSystem.onLogout(player);
            SparringSystem.onLogout(player);
            ProgressionSystem.onLogout(player);
            DifficultyActions.clearLevelPull(player.m_20148_());
        }
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer neu) || !(event.getOriginal() instanceof ServerPlayer old)) {
            return;
        }
        DifficultyCache.save(old);
        var data = DifficultyCache.data(old);
        var oldTag = PersistentDataAccess.get(old);
        var neuTag = PersistentDataAccess.get(neu);
        if (PersistentDataAccess.isWritable(neuTag) && PersistentDataAccess.isWritable(oldTag)) {
            // Death respawn builds a new player entity. Forge does not always copy
            // Entity#getPersistentData() for wasDeath — without this, the whole
            // prestige shop (wallet, skills, tiers, Majin/Mutant, breakthroughs)
            // under lm_progression is wiped.
            copyLmPersistentData(oldTag, neuTag);
            data.writeToPlayerNbt(neuTag);
        } else if (PersistentDataAccess.isWritable(neuTag)) {
            data.writeToPlayerNbt(neuTag);
        }
        // Original and clone share the same UUID — never remove() after putData(),
        // or the restored cache entry is dropped and the next read may race an empty bag.
        DifficultyCache.remove(old.m_20148_());
        DifficultyCache.putData(neu, data);
        CombatGravity.clearPlayer(old);
        if (event.isWasDeath()) {
            try {
                com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.tellLiving(neu);
            } catch (Throwable ignored) {
            }
            try {
                com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem
                        .scheduleReapplyAfterDeath(neu);
            } catch (Throwable ignored) {
            }
            // Death rebuilds DMZ Character — clear any polluted sample and re-pull.
            DmzProgression.clearBaseFormLevel(neu.m_20148_());
            DifficultyActions.scheduleLevelPull(neu);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Belt-and-suspenders: DMZ may finish rebuilding skills after Clone.
        try {
            com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem
                    .scheduleReapplyAfterDeath(player);
        } catch (Throwable ignored) {
        }
        // The End return portal respawns the player with isEndConquered.
        // That is not a death, so it must not start the death penalty.
        if (event.isEndConquered()) {
            return;
        }
        try {
            com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.tellLiving(player);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Model sync track-start: when {@code tracker} begins seeing {@code target}, send
     * {@code target}'s body-parts snapshot to {@code tracker} alone. Covers login into
     * range, respawn, dimension change, and walking back into range after an equip change
     * that broadcast while they were apart. The packet itself is feature-gated inside
     * {@link com.dbzlegacy.adaptivedifficulty.net.gui.LmGuiNetwork#sendModelSyncTo} —
     * clients that never registered message 3 must not receive it.
     */
    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer tracker)
                || !(event.getTarget() instanceof ServerPlayer target)) {
            return;
        }
        try {
            com.dbzlegacy.adaptivedifficulty.net.gui.LmGuiNetwork.onStartTracking(
                    tracker, target);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Copy LegacyMechanics persistent bags/keys from the dying (or dimension-travel)
     * player onto the clone. Safe to call even when Forge already merged some keys —
     * we overwrite with the old player's authoritative values.
     */
    private static void copyLmPersistentData(CompoundTag from, CompoundTag to) {
        if (from == null || to == null) {
            return;
        }
        // Prestige wallet + every prestige-shop purchase:
        // prestige_points, pp_skill_*, pp_tier_*, pp_perm_majin/mutant, pp_level_breakthroughs,
        // plus meditation / flight / potential progress, CNPC migrate marks, etc.
        if (from.m_128441_("lm_progression")) {
            to.m_128365_("lm_progression", from.m_128469_("lm_progression").m_6426_());
        }
        // Prestige lifetime / held tokens stored at the persistent-data root.
        copyTagIfPresent(from, to, "prestige_total_completed");
        copyTagIfPresent(from, to, "prestige_total_staff_override");
        copyTagIfPresent(from, to, "prestige_need_floor");
        copyTagIfPresent(from, to, "lm_prestige_held");
        copyTagIfPresent(from, to, "lm_prestige_points");
        copyTagIfPresent(from, to, "lm_prestige_confirm_until");
        copyTagIfPresent(from, to, "lm_cnpc_player_migrated");
        copyTagIfPresent(from, to, "lm_shadow_dummy_cd_until");
        // Death TP penalty is stamped on the dying entity. Respawn is a new entity.
        copyTagIfPresent(from, to, com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.KEY);
        copyTagIfPresent(from, to, com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.FRAC_KEY);
    }

    private static void copyTagIfPresent(CompoundTag from, CompoundTag to, String key) {
        if (from.m_128441_(key)) {
            to.m_128365_(key, from.m_128423_(key).m_6426_());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        LivingEntity entity = event.getEntity();
        if (SystemGate.isDisabled()) {
            return;
        }
        if (entity == null || event.isCanceled() || event.isSpawnCancelled()
                || DimensionGates.isDisabled(entity)) {
            return;
        }
        // Cage / minecart / SDD Advanced Spawner — stamp so nearby rescale can't convert later.
        if (event.getSpawnType() == net.minecraft.world.entity.MobSpawnType.SPAWNER
                || MobScaling.hasSddSpawnerMark(entity)) {
            MobScaling.markFromSpawner(entity);
            return;
        }
        // Slime / magma split children — never AD-convert the cubs.
        if (SlimeSplitGuard.tryMarkSplitChild(entity)) {
            return;
        }
        // Saga/quest kills already carry dmz_quest_* / dmz_saga_id — scaleIfNeeded no-ops.
        MobScaling.scaleIfNeeded(entity);
    }

    /**
     * SDD Advanced Spawner stamps {@code sdd_spawner}/{@code sdd_boss} after finalizeSpawn
     * (and finalize can fail on Mohist). Catch them on world join and undo any AD paint.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingJoin(EntityJoinLevelEvent event) {
        if (SystemGate.isDisabled() || event.getLevel().m_5776_()) {
            return;
        }
        // Viper's Call right-click venom cloud: 10 seconds at most.
        try {
            com.dbzlegacy.adaptivedifficulty.progression.combat.VipersCallCloudCap.onJoin(event.getEntity());
        } catch (Throwable ignored) {
        }
        // Block vanilla / natural End Dragon when LM owns End Strength (GUI/staff only).
        try {
            if (com.dbzlegacy.adaptivedifficulty.progression.end.EndDimensionStrength
                    .rejectUnauthorizedDragonJoin(event.getEntity())) {
                event.setCanceled(true);
                return;
            }
        } catch (Throwable ignored) {
        }
        if (!(event.getEntity() instanceof LivingEntity living) || DimensionGates.isDisabled(living)) {
            return;
        }
        // Split cubs join via addFreshEntity (often without FinalizeSpawn on Mohist).
        // Run before any other AD work so magma/slime children never get claimed.
        if (SlimeSplitGuard.tryMarkSplitChild(living)) {
            MobScaling.ensureExempt(living);
            return;
        }
        if (MobScaling.hasSddSpawnerMark(living)) {
            MobScaling.markFromSpawner(living);
        }
        if (MobScaling.isExemptFromConversion(living)) {
            MobScaling.ensureExempt(living);
            return;
        }
        // Finish deferred leave-area / admin-off reverts once the chunk loads again.
        if (living instanceof Mob mob && ScaledMobTracker.isPendingRevert(mob.m_20148_())) {
            MobScaling.revertToBases(mob);
            CombatIndex.unmark(mob.m_20148_());
            ScaledMobTracker.clearPendingRevert(mob.m_20148_());
            return;
        }
        // Orphan scaled paint with no living claim owner → strip on join.
        if (living instanceof Mob mob
                && PersistentDataAccess.get(mob).m_128471_(MobScaling.TAG_SCALED)
                && ScaledMobTracker.findClaimOwnerId(mob.m_20148_()) == null
                && !ScaledMobTracker.isPendingRevert(mob.m_20148_())) {
            MobScaling.revertToBases(mob);
            CombatIndex.unmark(mob.m_20148_());
        }
    }

    /**
     * Player agro → scale to that player. Hostile→hostile agro → block / redirect to a player
     * so packs do not civil-war (vanilla revenge + splash/ki otherwise re-locks every tick).
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onChangeTarget(LivingChangeTargetEvent event) {
        if (SystemGate.isDisabled()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Mob mob) || DimensionGates.isDisabled(mob)) {
            return;
        }
        LivingEntity neu = event.getNewTarget();
        if (neu instanceof ServerPlayer player && SystemGate.participates(player)) {
            try {
                if (com.dbzlegacy.adaptivedifficulty.progression.end.EndDimensionStrength
                        .hasAliveSummonedDragon(player)) {
                    return;
                }
            } catch (Throwable ignored) {
            }
            CombatIndex.mark(mob);
            MobScaling.retargetToPlayer(mob, player);
            return;
        }
        if (neu != null && HostileMobs.bothHostile(mob, neu)) {
            ServerPlayer player = NearbyPlayers.nearestParticipating(
                    mob, DifficultyConfig.get().mobScaleRadius);
            if (player != null) {
                event.setNewTarget(player);
                CombatIndex.mark(mob);
                MobScaling.retargetToPlayer(mob, player);
            } else {
                event.setNewTarget(null);
                HostileMobs.clearCivilWarAggro(mob);
            }
        }
    }

    /**
     * Cheap early cancel: stop hostile→hostile swings before revenge memory arms.
     * (Heavier combat-index work stays on {@link #onHurt} for player fights only.)
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(LivingAttackEvent event) {
        if (SystemGate.isDisabled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.m_9236_().f_46443_ || DimensionGates.isDisabled(victim)) {
            return;
        }
        Entity causing = event.getSource() == null ? null : event.getSource().m_7639_();
        if (causing == null || causing == victim || causing instanceof Player || victim instanceof Player) {
            return;
        }
        if (causing instanceof LivingEntity attacker && HostileMobs.bothHostile(victim, attacker)) {
            event.setCanceled(true);
        }
    }

    /**
     * High-tier dodge counter: HIGH runs before Iron's Spells' NORMAL dodge handler on the same
     * event, so the stripped Evasion / Abyssal Shroud cannot dodge this hit.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttackDodgeCounter(LivingAttackEvent event) {
        if (SystemGate.isDisabled()) {
            return;
        }
        try {
            com.dbzlegacy.adaptivedifficulty.ai.DodgeCounter.onAttack(event);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Concept §17 — one server-pulse scheduler instead of LivingTick on every entity.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        // Even when master is OFF, drain claims + revert scaled hostiles.
        // Each pulse is isolated so one throw cannot skip the rest of the tick.
        int tick = server.m_129921_(); // getTickCount
        safePulse("mob-scaling", () -> BehaviorScheduler.pulse(server, tick));
        safePulse("rival", () -> RivalSystem.pulse(server, tick));
        safePulse("sparring", () -> SparringSystem.pulse(server, tick));
        safePulse("progression", () -> ProgressionSystem.pulse(server, tick));
        safePulse("level-pull", () -> DifficultyActions.pulseLevelPulls(server));
    }

    private static void safePulse(String name, Runnable pulse) {
        try {
            pulse.run();
        } catch (Throwable t) {
            long now = System.currentTimeMillis();
            Long last = PULSE_WARN_AT.get(name);
            if (last == null || now - last > 20_000L) {
                PULSE_WARN_AT.put(name, now);
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] {} pulse failed: {}",
                        AdaptiveDifficultyMod.MOD_ID,
                        name,
                        t.toString());
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        // Proving Grounds bar is a rival feature, independent of personal difficulty.
        if (player.f_19797_ % 20 == 0) {
            com.dbzlegacy.adaptivedifficulty.rival.ProvingGroundsBar.tick(player);
        }
        if (SystemGate.isDisabled() || !SystemGate.allows(player)) {
            return;
        }
        // Level / prestige / transform-power / form-mult poll.
        // Custom races often skip FormChangeEvent — catch form mult spikes here.
        if (player.f_19797_ % 20 != 0) {
            return;
        }
        // Survivor challenge: accumulate playtime while a tier is active.
        if (DifficultyCache.data(player).getActiveTier() > 0) {
            DifficultyCache.data(player).titleProgress().addPlaySeconds(1L);
            if (player.f_19797_ % 1200 == 0) {
                TitleSystem.syncChallengeTitles(player, true);
                DifficultyCache.save(player);
            }
        }
        if (player.f_19797_ % 40 == 0) {
            TitleSense.pulse(player);
        }
        DifficultySnapshot before = DifficultyCache.get(player);
        int level = DmzProgression.guiDisplayDmzLevel(player);
        int prestige = DmzProgression.prestige(player);
        double transform = DmzProgression.transformationPower(player);
        double formMult = PlayerCombatProfile.liveFormMultiplier(player);
        double liveOffense = liveOffensePeak(player);
        String race = DmzProgression.race(player);
        String formKey = DmzProgression.activeFormKey(player);
        UUID id = player.m_20148_();
        String prevRace = LAST_RACE.put(id, race == null ? "" : race);
        if (prevRace != null && race != null && !prevRace.equals(race)) {
            // New / swapped race — drop baselines so future custom races start clean.
            PlayerCombatProfile.clearFormBaseline(id);
            DmzProgression.clearBaseFormLevel(id);
            PlayerCombatProfile.clear(id);
            LAST_FORM_KEY.remove(id);
            LAST_FORM_MULT.remove(id);
            LAST_LIVE_OFFENSE.remove(id);
        }
        String prevFormKey = LAST_FORM_KEY.put(id, formKey == null ? "" : formKey);
        Double prevForm = LAST_FORM_MULT.put(id, formMult);
        Double prevOffense = LAST_LIVE_OFFENSE.put(id, liveOffense);
        boolean formKeyChanged = prevFormKey != null && formKey != null && !prevFormKey.equals(formKey);
        boolean formChanged = prevForm != null && Math.abs(prevForm - formMult) > 0.08;
        // Lower threshold — custom forms sometimes step up in smaller mastery chunks.
        boolean offenseChanged = prevOffense != null && prevOffense > 1.0 && liveOffense > 1.0
                && Math.abs(liveOffense - prevOffense) / prevOffense > 0.08;
        boolean progressChanged = before.dmzLevel != level
                || before.prestige != prestige
                || Math.abs(before.transformationPower - transform) > 0.5;
        if (formChanged || formKeyChanged || offenseChanged
                || Math.abs(before.transformationPower - transform) > 0.5
                || (prevRace != null && race != null && !prevRace.equals(race))) {
            com.dbzlegacy.adaptivedifficulty.service.DifficultyActions.refreshCombatPaint(player);
            if (formKeyChanged || formChanged) {
                com.dbzlegacy.adaptivedifficulty.character.CosmeticHeadBoneService
                        .scheduleReapplyAfterFormChange(player);
            }
        } else if (progressChanged) {
            DifficultyCache.refresh(player);
        }
    }

    private static double liveOffensePeak(ServerPlayer player) {
        try {
            var data = DmzProgression.stats(player);
            if (data == null) {
                return 1.0;
            }
            // Peak live offense across STR/SKP/PWR (+ mild ENE pool).
            double m = Math.max(1.0, com.dbzlegacy.adaptivedifficulty.calc.LmOverhaulScaledCombat.melee(data));
            double s = Math.max(1.0, com.dbzlegacy.adaptivedifficulty.calc.LmOverhaulScaledCombat.strike(data));
            double k = Math.max(1.0, com.dbzlegacy.adaptivedifficulty.calc.LmOverhaulScaledCombat.ki(data));
            double e = Math.max(1.0, com.dbzlegacy.adaptivedifficulty.calc.LmOverhaulScaledCombat.energy(data) * 0.08);
            return Math.max(m, Math.max(s, Math.max(k, e)));
        } catch (Throwable t) {
            return 1.0;
        }
    }

    @SubscribeEvent
    public void onFormChange(DMZEvent.FormChangeEvent event) {
        if (SystemGate.isDisabled()) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        if (player == null || !SystemGate.allows(player)) {
            return;
        }
        // Immediate claimed-mob repaint — don't wait for the nearby pulse.
        com.dbzlegacy.adaptivedifficulty.service.DifficultyActions.refreshCombatPaint(player);
        com.dbzlegacy.adaptivedifficulty.character.CosmeticHeadBoneService.scheduleReapplyAfterFormChange(
                player);
    }

    @SubscribeEvent
    public void onStackFormChange(DMZEvent.StackFormChangeEvent event) {
        if (SystemGate.isDisabled()) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        if (player == null || !SystemGate.allows(player)) {
            return;
        }
        com.dbzlegacy.adaptivedifficulty.service.DifficultyActions.refreshCombatPaint(player);
        com.dbzlegacy.adaptivedifficulty.character.CosmeticHeadBoneService.scheduleReapplyAfterFormChange(
                player);
    }

    /**
     * Primary damage scaling path (Forge event — reliable on Mohist).
     * Melee uses scaled ATTACK_DAMAGE; this multiplies projectiles / custom hits.
     * <p>
     * Hostile→hostile damage is cancelled: high offense multipliers were turning
     * splash/ki/pack mistakes into mob civil wars.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.m_9236_().f_46443_ || SystemGate.isDisabled()) {
            return;
        }
        // GUI-summoned Wither / Warden: only the summoner may damage them.
        try {
            com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.onHurt(event);
        } catch (Throwable ignored) {
        }
        if (event.isCanceled()) {
            return;
        }
        var source = event.getSource();
        Entity causing = source == null ? null : source.m_7639_(); // getEntity

        // Dimensions listed in disabledDimensions skip AD work entirely.
        if (DimensionGates.isDisabled(victim)) {
            return;
        }

        // Fast reject: ignore damage that cannot involve our systems (no player, no hostile).
        boolean victimPlayer = victim instanceof Player;
        boolean causerPlayer = causing instanceof Player;
        boolean victimHostile = !victimPlayer && HostileMobs.isHostile(victim);
        boolean causerHostile = !causerPlayer && causing instanceof LivingEntity le && HostileMobs.isHostile(le);
        if (!victimPlayer && !causerPlayer && !victimHostile && !causerHostile) {
            return;
        }

        // Whitelist testing: skip player-facing AD unless an allowed tester is in the fight.
        // Hostile↔hostile civil-war cancel below still applies to scaled mobs.
        boolean whitelistBlocksPlayers = DifficultyConfig.isWhitelistEnabled()
                && !SystemGate.allowsAny(victim, causing);

        // Rider↔mount: skeleton/spider jockeys + skeleton horses — ki spawns on the rider
        // and otherwise kills their own vehicle (mount may not be HostileMobs-tagged).
        if (causing != null && causing != victim && HostileMobs.isMountPair(causing, victim)) {
            event.setCanceled(true);
            event.setAmount(0.0f);
            return;
        }

        // Block hostile→hostile (and hostile booms on other hostiles). Allow self-damage
        // so creeper fuse can finish killing the exploding creeper on some Mohist paths.
        if (victimHostile && !causerPlayer && causing != victim) {
            boolean hostileBoom = source != null && source.m_269533_(DamageTypeTags.f_268415_) // IS_EXPLOSION
                    && !causerPlayer;
            if (causerHostile || hostileBoom) {
                event.setCanceled(true);
                event.setAmount(0.0f);
                // Wipe revenge + redirect onto a nearby player (null target lets vanilla re-aggro).
                redirectCivilWar(causing instanceof LivingEntity le ? le : null, victim);
                return;
            }
        }

        if (whitelistBlocksPlayers) {
            return;
        }

        // Combat-index + cached retarget (LivingAttackEvent removed — every swing crushed TPS).
        if (causing instanceof ServerPlayer player && victimHostile && victim instanceof Mob vm) {
            CombatIndex.mark(vm);
            MobScaling.retargetToPlayer(vm, player);
        } else if (victim instanceof ServerPlayer player && causerHostile && causing instanceof Mob atk) {
            CombatIndex.mark(atk);
            MobScaling.retargetToPlayer(atk, player);
        }

        float amount = event.getAmount();
        if (amount > 0.0f && causerHostile) {
            float scaled = MobScaling.scaleOutgoingHurt(amount, event.getSource());
            if (scaled != amount) {
                event.setAmount(scaled);
            }
        }
        // Equipped title / Title Score damage perks (small, capped).
        if (amount > 0.0f && causerPlayer && victimHostile
                && causing instanceof ServerPlayer attacker) {
            float boosted = TitleEffects.applyOutgoingDamageBonus(attacker, victim, event.getAmount());
            if (boosted != event.getAmount()) {
                event.setAmount(boosted);
            }
        }
        if (victimHostile) {
            AdaptiveAiSystem.onHurt(event);
        }
        // Counter-teleport only for kits that use it — never NBT-scan every hurt victim.
        if (victimHostile && event.getAmount() > 0.0f
                && (victim instanceof EnderMan
                || victim instanceof ZombifiedPiglin
                || victim instanceof Warden)) {
            EnemyEvolution.onHurt(victim);
        }
        if (victimHostile && victim.m_21223_() <= 0.0f) {
            MobScaling.terminateIfZeroHealth(victim);
        }

        // Rival / Sparring PvP scoring + natural-progression combat/End/dummy.
        if (victim instanceof ServerPlayer pvpVictim
                && causing instanceof ServerPlayer pvpAttacker) {
            if (event.getAmount() > 0.0f && DifficultyConfig.get().enableRivalSystem) {
                float underdog = com.dbzlegacy.adaptivedifficulty.rival.RivalChallengeManager.get()
                        .underdogDamageScale(pvpVictim, pvpAttacker,
                                com.dbzlegacy.adaptivedifficulty.util.DmzRewards.isKiDamage(source));
                if (underdog != 1.0f) {
                    event.setAmount(event.getAmount() * underdog);
                }
            }
            RivalSystem.onPlayerHurt(pvpVictim, pvpAttacker, source);
            SparringSystem.onPlayerHurt(pvpVictim, pvpAttacker, source);
        }
        ProgressionSystem.onHurt(event);
    }

    @SubscribeEvent
    public void onTravelToDimension(net.minecraftforge.event.entity.EntityTravelToDimensionEvent event) {
        EndProgression.onTravelToDimension(event);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        EndProgression.onRightClickBlock(event);
    }

    /**
     * One left-click with the matching dragon radar takes that ball.
     * Runs even after a claim plugin cancels the click. Only that ball is taken.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        DragonBallRadarPickup.onLeftClickBlock(event);
    }

    /** A claim plugin may still cancel the break. Allow it only for this radar click. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onDragonBallBreak(BlockEvent.BreakEvent event) {
        DragonBallRadarPickup.onBreak(event);
    }

    /** Sneak + right-click another player → DMZ stat dump (PlayerStatChecker.js).
     * Also Skill Check / Rival / Spar / Hub / Difficulty / Prestige CNPC interact. */
    @SubscribeEvent
    public void onPlayerEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel() == null || event.getLevel().m_5776_()) {
            return;
        }
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                && event.getTarget() != null) {
            // CNPC scripter / wand / cloner — let staff edit NPCs; do not force GUIs.
            if (com.dbzlegacy.adaptivedifficulty.gui.CnpcGuiOpener.holdingEditorTool(player)) {
                return;
            }
            if (com.dbzlegacy.adaptivedifficulty.progression.shop.SkillCheckService.tryOpenFromNpc(
                    player, event.getTarget())) {
                event.setCanceled(true);
                return;
            }
            // Tags or strict names — cancel (LegacyMechanics replaces CNPC scripts).
            if (com.dbzlegacy.adaptivedifficulty.gui.CnpcGuiOpener.tryOpenFromNpc(
                    player, event.getTarget())) {
                event.setCanceled(true);
                return;
            }
        }
        PlayerStatChecker.onEntityInteract(event);
    }

    /**
     * Rival + Sparring death / KO / mob-kill hooks.
     * Independent of AD master gate — these systems have their own config flags.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRivalSparDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead == null || dead.m_9236_().f_46443_) {
            return;
        }
        Entity killerEnt = event.getSource() == null ? null : event.getSource().m_7639_();
        if (dead instanceof ServerPlayer victim) {
            LivingEntity killerLiving = killerEnt instanceof LivingEntity le ? le : null;
            RivalSystem.onDeath(victim, killerLiving);
            SparringSystem.onDeath(victim);
            // A canceled death that still kills the player (custom KO) must start the timer.
            // A canceled death that leaves them alive is not a death.
            if (!event.isCanceled() || !victim.m_6084_() || victim.m_21223_() <= 0.0f) {
                try {
                    com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty.onDeath(victim);
                } catch (Throwable ignored) {
                }
            }
        }
        if (killerEnt instanceof ServerPlayer killer
                && dead instanceof LivingEntity
                && !(dead instanceof Player)) {
            RivalSystem.onMobKillNear(killer, (LivingEntity) dead);
            ProgressionSystem.onDeath(event);
        }
    }

    /**
     * Preserve End Dragon loot-table drops from every mod (Iron's Spellbooks GLM,
     * Simply Swords / Simply More Architectury injects, Apotheosis, etc.).
     * <p>
     * Player-summoned dragons: clear vanilla {@link LivingDropsEvent} stacks — we
     * force-roll {@code minecraft:entities/ender_dragon} next tick (Forge GLMs
     * included) at the death position so off-island kills keep full loot.
     * Other dragons: leave drops intact and mark so the fallback does not double.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                || dead instanceof net.minecraft.world.entity.monster.warden.Warden) {
            try {
                com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.onDrops(event);
            } catch (Throwable ignored) {
            }
            return;
        }
        if (!(dead instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon)) {
            return;
        }
        try {
            if (com.dbzlegacy.adaptivedifficulty.progression.end.EndDimensionStrength
                    .isPlayerSummonedDragon(dead)) {
                // Force-roll path owns loot for GUI summons — avoid double stacks.
                event.getDrops().clear();
                return;
            }
            com.dbzlegacy.adaptivedifficulty.progression.end.EndDimensionStrength
                    .markDragonLootRolled(dead);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Post-mitigation safety net.
     * <ul>
     *   <li>Hostiles at ≤0 HP — force terminate</li>
     *   <li>AD mob → player hard-cancelled by DMZ DEF ({@code flatMit ≥ dmg×2.5}) —
     *       restore tier-scaled landing damage so SSJB/T7 cannot knock without hurting</li>
     * </ul>
     * <p>
     * DMZ {@code CombatEvent.overrideVanillaArmorReduction} is also {@code LOWEST} and
     * recalculates from the {@code dmz_raw_damage} NBT tag — often <em>after</em> this
     * handler when registration order puts DragonMineZ later. High-DEF / Android forms
     * then hit {@code applyFullNegation} and the player takes 0 while our telemetry still
     * logged the restored amount. Clearing those tags after we finalize the AD bite stops
     * DMZ from overwriting the landing / soft-cap.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onDamageDone(LivingDamageEvent event) {
        if (SystemGate.isDisabled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        // Never force-terminate Ender Dragons here — EnderDragon.kill() skips loot tables
        // (Iron's Spellbooks, Simply Swords/More, and other entities/ender_dragon loot mods).
        if (victim != null
                && !(victim instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon)
                && HostileMobs.isHostile(victim)
                && victim.m_21223_() <= 0.0f) {
            MobScaling.terminateIfZeroHealth(victim);
        }
        // Personal OFF / whitelist-blocked: never inject landing damage.
        // (allows() alone let stored active tiers bite players with personal OFF.)
        if (!(victim instanceof ServerPlayer player) || !SystemGate.participates(player)) {
            return;
        }
        if (DimensionGates.isDisabled(player)) {
            return;
        }
        var source = event.getSource();
        Entity causing = source == null ? null : source.m_7639_();
        if (!(causing instanceof Mob mob) || !HostileMobs.isHostile(mob)) {
            return;
        }
        // Claimed/profile-scaled only — spawn-init TAG_SCALED shells are not difficulty.
        if (!MobScaling.isAdPainted(mob)) {
            return;
        }
        float preAmount = event.getAmount();
        boolean cancelled = event.isCanceled() || preAmount <= 0.0f;
        PlayerCombatProfile profile = PlayerCombatProfile.of(player);
        if (!profile.active()) {
            return;
        }
        double land = profile.targetLandingDamage(DifficultyConfig.get());
        try {
            double relief = com.dbzlegacy.adaptivedifficulty.title.TitleEffects.landFracRelief(player);
            if (relief > 0.0) {
                land = Math.max(1.0, land * (1.0 - relief));
            }
        } catch (Throwable ignored) {
        }
        boolean intervened = false;
        if (cancelled) {
            // DMZ applyFullNegation zeroed the hit — put the tier bite back.
            event.setCanceled(false);
            event.setAmount((float) Math.max(1.0, land));
            intervened = true;
        } else if (preAmount < land * 0.45f) {
            // Partial shrug only — always filling to landing shredded tanks (1.0.24 regression).
            event.setAmount((float) Math.max(preAmount, land));
            intervened = true;
        }
        // Soft-cap crushing hits — monotonic buy ladder (2.3.161 rollback).
        double bag = Math.max(20.0, profile.liveMaxHealth);
        double maxFrac = profile.incomingSoftCapFrac();
        float softCap = (float) Math.min((double) Float.MAX_VALUE, bag * maxFrac);
        if (event.getAmount() > softCap) {
            event.setAmount(softCap);
            intervened = true;
        }
        // 2.3.130 / 2.3.164: strip DMZ raw-damage NBT once AD has chosen the final amount.
        // DMZ CombatEvent also runs at LOWEST and re-applies applyFullNegation from these tags
        // even on passthrough hits (preAmount already above landing floor) — telemetry logged
        // the bite but HP stayed full (T3 god-pierce >> cancel threshold on 22k veterans).
        clearDmzRawDamageOverride(player);
        if (intervened) {
            event.setCanceled(false);
        }
        // Whitelist telemetry — log pre/post so cancelled zeros stay visible.
        if (BalanceTelemetry.shouldLog(player)) {
            BalanceTelemetry.logIncomingHit(
                    player, mob, profile, preAmount, cancelled, event.getAmount());
        }
    }

    /**
     * DMZ stores pre-armor raw damage on the player and rewrites {@link LivingDamageEvent}
     * at {@code LOWEST}. Remove those tags once AD has chosen the final incoming amount.
     */
    private static void clearDmzRawDamageOverride(ServerPlayer player) {
        try {
            var data = PersistentDataAccess.get(player);
            if (!PersistentDataAccess.isWritable(data)) {
                return;
            }
            data.m_128473_("dmz_raw_damage");
            data.m_128473_("dmz_defense_pen");
            data.m_128473_("dmz_block_multiplier");
        } catch (Throwable ignored) {
            // Never let NBT cleanup break the damage path.
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (SystemGate.isDisabled()) {
            return;
        }
        LivingEntity dead = event.getEntity();
        // Combat gravity must drop the moment the chamber source dies — do not wait
        // for contribution TTL (and never leave a sticky GravityDeviceManager zone).
        if (dead instanceof EnderMan || dead instanceof Warden) {
            CombatGravity.removeSource(dead.m_20148_());
        }
        // Register before vanilla remove() spawns smaller slime/magma cubs.
        SlimeSplitGuard.onParentDeath(dead);
        if (dead instanceof Creeper creeper) {
            EnemyEvolution.onCreeperDeath(creeper, event.getSource());
        }
        // V3 death penalty: clear temporary active tier/level; unlocks & coins stay.
        // Uses allows() (not participates) so toggling personal OFF cannot skip the penalty.
        if (dead instanceof ServerPlayer victim) {
            // Title no-death streaks always break on death (even if personal OFF).
            TitleSystem.noteDeath(victim);
            try {
                com.dbzlegacy.adaptivedifficulty.progression.end.EndDimensionStrength
                        .despawnOwnedDragon(victim);
            } catch (Throwable ignored) {
            }
            if (SystemGate.allows(victim)
                    && DifficultyConfig.get().deathResetsActiveDifficulty) {
                var data = DifficultyCache.data(victim);
                if (data.getActiveTier() > 0 || data.getActiveDifficultyLevel() > 0L) {
                    data.resetTemporary();
                    DifficultyCache.save(victim);
                    DifficultyCache.refresh(victim);
                    ScaledMobTracker.releaseAndRevertPlayer(victim);
                    NearbyMobScaler.processEvictions();
                    victim.m_213846_(net.minecraft.network.chat.Component.m_237113_(
                            "§cDifficulty deactivated on death. §7Unlocks & Ancient Coins kept."));
                }
            }
        }
        if (!(event.getSource().m_7639_() instanceof ServerPlayer killer) || !SystemGate.participates(killer)) {
            return;
        }
        if (dead != null) {
            RewardSystem.onKill(killer, dead);
        }
    }

    /**
     * Replace vanilla projectiles from evolved skeletons / blazes / ghasts with DMZ ki.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onProjectileJoin(EntityJoinLevelEvent event) {
        ProgressionSystem.onJoin(event);
        if (SystemGate.isDisabled() || event.getLevel().m_5776_()) { // isClientSide
            return;
        }
        Entity entity = event.getEntity();
        LivingEntity target = null;
        Mob shooter = null;

        if (entity instanceof AbstractArrow arrow && arrow.m_19749_() instanceof AbstractSkeleton skel) {
            shooter = skel;
            target = skel.m_5448_();
        } else if (entity instanceof SmallFireball ball && ball.m_19749_() instanceof Blaze blaze) {
            shooter = blaze;
            target = blaze.m_5448_();
        } else if (entity instanceof LargeFireball ball && ball.m_19749_() instanceof Ghast ghast) {
            shooter = ghast;
            target = ghast.m_5448_();
        } else {
            return;
        }

        if (shooter == null || target == null || !EnemyEvolution.isEvolvable(shooter)) {
            return;
        }
        if (!PersistentDataAccess.get(shooter).m_128471_(MobScaling.TAG_SCALED)) {
            return;
        }
        if (EnemyEvolution.tryReplaceProjectile(shooter, target)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEnergyRegen(DMZEvent.EnergyRegenEvent event) {
        if (event == null || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        try {
            com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzResourceRegenSync
                    .afterEnergyRegen(player, event.getStatsData(), event.getAmount());
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public void onStaminaRegen(DMZEvent.StaminaRegenEvent event) {
        if (event == null || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        try {
            com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzResourceRegenSync
                    .afterStaminaRegen(player, event.getStatsData(), event.getAmount());
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public void onKiCharge(DMZEvent.KiChargeEvent event) {
        if (SystemGate.isDisabled() || !(event.getPlayer() instanceof ServerPlayer player)
                || !SystemGate.participates(player)) {
            return;
        }
        AdaptiveAiSystem.onPlayerKiCharge(player);
        // Spar: keep match alive through charge→shot without faking combat for drip TP.
        SparringSystem.markKiCharging(player);
    }

    /**
     * After a hostile↔hostile hit/boom is cancelled, clear civil-war agro and point both
     * sides at the nearest allowed player when one is in range.
     */
    private static void redirectCivilWar(LivingEntity causing, LivingEntity victim) {
        ServerPlayer player = null;
        if (victim instanceof Mob vm) {
            player = focusPlayerOrClear(vm, null);
        }
        if (causing instanceof Mob am) {
            focusPlayerOrClear(am, player);
        }
    }

    private static ServerPlayer focusPlayerOrClear(Mob mob, ServerPlayer hint) {
        HostileMobs.clearCivilWarAggro(mob);
        ServerPlayer player = hint;
        if (player == null || !player.m_6084_() || player.m_9236_() != mob.m_9236_()
                || !SystemGate.participates(player)) {
            player = NearbyPlayers.nearestParticipating(
                    mob, DifficultyConfig.get().mobScaleRadius);
        }
        if (player != null) {
            LivingEntity current = mob.m_5448_();
            if (!(current instanceof Player) || !current.m_6084_()) {
                mob.m_6710_(player);
            }
            CombatIndex.mark(mob);
            MobScaling.retargetToPlayer(mob, player);
            return player;
        }
        return null;
    }
}
