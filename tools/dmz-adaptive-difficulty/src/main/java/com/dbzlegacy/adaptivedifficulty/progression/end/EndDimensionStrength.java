package com.dbzlegacy.adaptivedifficulty.progression.end;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache;
import com.dbzlegacy.adaptivedifficulty.calc.CombatRating;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.calc.LmOverhaulScaledCombat;
import com.dbzlegacy.adaptivedifficulty.calc.PlayerCombatProfile;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;
import com.dbzlegacy.adaptivedifficulty.util.PaidFeatureAccess;
import com.dbzlegacy.adaptivedifficulty.data.PlayerDifficultyData;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.scaling.MobScaling;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import com.dbzlegacy.adaptivedifficulty.util.SystemGate;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Resources;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonChargePlayerPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonStrafePlayerPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Core port of {@code End Dimension Strength.js} 2.12.0:
 * Player End dragons are summoned from the Difficulty GUI (T4–T7, 3× Netherite),
 * painted with the summoner's Adaptive Difficulty boss profile (same formulas as
 * nearby AD mobs × boss mult — not the legacy End Strength HP/DEF curve), and
 * damage-locked to that player. Multiple players may each have their own dragon
 * near them (not forced to the main island). Staff may {@code /cleardragons} /
 * {@code /enddragon clear|repair} only — no staff spawn.
 */
public final class EndDimensionStrength {
    private static final String TAG_BUFFED = "end_strength_v15";
    private static final String NBT_DEF = "end_strength_entity_def";
    private static final String NBT_MAX = "end_strength_real_max";
    private static final String NBT_HITS = "end_strength_hit_target";
    private static final String NBT_DMZ_HP = "end_strength_dmz_hp_src";
    /** UUID string of the Difficulty-GUI summoner (player-summoned dragons only). */
    private static final String NBT_SUMMONER = "end_dragon_summoner";
    private static final String NBT_PLAYER_SUMMON = "end_dragon_player_summon";
    private static final String NBT_AD_TIER = "end_dragon_ad_tier";
    private static final String NBT_KI_MELEE = "end_dragon_ki_melee";
    /** Last {@link PlayerCombatProfile#signature} applied for a player-summoned dragon. */
    private static final String NBT_AD_SIG = "end_dragon_ad_sig";
    /** Legacy staff-spawn stamp — staff spawn removed; kept so old entities are not culled as vanilla. */
    private static final String NBT_STAFF_SPAWN = "end_dragon_staff";
    /**
     * Set when {@code minecraft:entities/ender_dragon} loot (Simply Swords / Simply More)
     * already rolled for this dragon — avoids double drops.
     */
    private static final String NBT_LOOT_ROLLED = "end_dragon_loot_rolled";
    /** Set when we already awarded vanilla End Dragon XP at the death position. */
    private static final String NBT_XP_AWARDED = "end_dragon_xp_awarded";
    private static final Set<UUID> SESSION_LOOT_ROLLED = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SESSION_XP_AWARDED = ConcurrentHashMap.newKeySet();
    private static final String ENDER_DRAGON_LOOT = "minecraft:entities/ender_dragon";
    /** Vanilla first-kill dragon XP (EndDragonFight has not killed before). */
    private static final int DRAGON_XP_FIRST = 12_000;
    /** Vanilla repeat dragon XP. */
    private static final int DRAGON_XP_REPEAT = 500;
    private static final long PLAYER_DRAGON_RETARGET_MS = 750L;

    /** Minimum active Unlock Tier for paid GUI summons. */
    public static final int PLAYER_SUMMON_MIN_TIER = 4;
    /** Maximum active Unlock Tier for paid GUI summons. */
    public static final int PLAYER_SUMMON_MAX_TIER = 7;
    /** Cost in Ancient Netherite coins (each = 100_000 copper). */
    public static final int PLAYER_SUMMON_NETHERITE_COST = 3;

    private static final double SCAN_RADIUS = 96.0;
    private static final long SCAN_MS = 1500L;
    private static final long NATURAL_CHECK_MS = 10_000L;
    private static final long NATURAL_SPAWN_MS = 5L * 60L * 1000L;
    private static final long DRAGON_RESCALE_MS = 3000L;
    private static final double DRAGON_SCALE_SCORE_EPSILON = 0.01;
    private static final int TP_SETTLE_DELAY_TICKS = 6;

    /** Script v2.12.0: min(KI_CLEANUP_INTERVAL_MS, SINGLE_DRAGON_CHECK_MS). */
    private static final long HYGIENE_INTERVAL_MS = 1500L;
    private static final String KI_LASER_ID = "dragonminez:ki_laser";
    private static final String KI_BLAST_ID = "dragonminez:ki_blast";
    private static final AABB END_KI_SCAN_BOX = new AABB(-800.0, 0.0, -800.0, 800.0, 320.0, 800.0);

    /** Live End Dimension Strength.js 2.12.0 — dragon extra DMZ ki attacks. */
    private static final boolean DRAGON_EXTRA_ATTACKS = true;
    private static final long DRAGON_ATTACK_INTERVAL_MS = 3200L;
    /** Faster cadence for Difficulty-GUI summons focused on the summoner. */
    private static final long PLAYER_DRAGON_ATTACK_INTERVAL_MS = 1700L;
    private static final long PLAYER_DRAGON_PHASE_STEER_MS = 4500L;
    private static final double DRAGON_ATTACK_RANGE = 96.0;
    private static final double PLAYER_DRAGON_ATTACK_RANGE = 128.0;
    private static final double DRAGON_KI_BEAM_CHANCE = 0.55;
    private static final double DRAGON_COMBO_CHANCE = 0.28;
    private static final double DRAGON_DMZ_KI_DAMAGE = 450.0;
    private static final double DRAGON_DMZ_KI_MELEE_FRAC = 0.10;
    private static final double DRAGON_DMZ_KI_DAMAGE_CAP = 8000.0;
    private static final float DRAGON_DMZ_KI_SPEED_BEAM = 1.75f;
    private static final float DRAGON_DMZ_KI_BEAM_SIZE = 1.0f;
    private static final float DRAGON_DMZ_KI_SPEED_BLAST = 2.35f;
    private static final float DRAGON_DMZ_KI_SIZE_BLAST = 1.35f;
    private static final int DRAGON_DMZ_KI_LIFE_BEAM = 28;
    private static final int DRAGON_DMZ_KI_LIFE_BLAST = 36;
    private static final int DRAGON_DMZ_KI_COLOR_MAIN = 0xC44CFF;
    private static final int DRAGON_DMZ_KI_COLOR_BORDER = 0x7A1FA2;
    private static final int DRAGON_DMZ_KI_COLOR_OUTLINE = 0xFFFFFF;

    private static final boolean DESTROY_CRYSTALS_ON_KILL = true;
    private static final boolean REMOVE_EGG_BLOCK = true;
    private static final int EGG_CLEAR_RADIUS = 8;
    private static final int EGG_CLEAR_Y_MIN = 50;
    private static final int EGG_CLEAR_Y_MAX = 120;
    /** Central island scan for duplicate exit portals / podium repair. */
    private static final int PODIUM_SCAN_RADIUS = 10;
    private static final int PODIUM_SCAN_Y_MIN = 40;
    private static final int PODIUM_SCAN_Y_MAX = 128;
    private static final long PODIUM_REPAIR_COOLDOWN_MS = 8_000L;

    private static final double DRAGON_BASE_HP = 12_000;
    private static final double DRAGON_HP_CAP = 28_000;
    private static final double DRAGON_HP_LOG_REF = 10_000_000;
    private static final double DRAGON_DEF_BASE = 50_000;
    private static final double DRAGON_DEF_FROM_PLAYER = 2.25;
    private static final double DRAGON_DEF_FROM_MELEE = 1.75;
    private static final double DRAGON_DEF_PER_LEVEL = 750;
    private static final double DRAGON_DEF_PER_BP = 0.15;
    private static final double DRAGON_DEF_CAP = 25_000_000;
    private static final int DRAGON_TARGET_HITS = 250;
    private static final double DRAGON_MIN_DMG_FRAC = 0.008;
    private static final double END_FLAT_ABSORB = 0.55;
    private static final double END_REDUCTION_CAP = 0.92;
    private static final double END_DEF_SCALE = 12.0;

    private static final double END_MOB_DEF_FROM_PLAYER = 1.10;
    private static final double END_MOB_DEF_FROM_MELEE = 0.85;
    private static final double END_MOB_DEF_PER_LEVEL = 120;
    private static final double END_MOB_DEF_SCALE_CAP = 8.0;
    private static final double END_MOB_MIN_DMG_FRAC = 0.03;
    private static final double END_MOB_FLAT_ABSORB = 0.35;
    private static final double END_MOB_REDUCTION_CAP = 0.80;
    private static final double END_MOB_DEF_SCALE = 20.0;
    private static final double ENDERMAN_ATTACK = 12.0;

    private static volatile long lastWorldScanAt;
    private static volatile long lastNaturalCheckAt;
    private static volatile long lastNaturalSpawnAt;
    /** 0 = never set; first natural check arms the timer without spawning. */
    private static volatile boolean naturalTimerArmed;
    private static volatile long lastDragonRescaleAt;
    private static volatile long lastHygieneAt;
    private static volatile long lastDragonAttackAt;
    private static volatile long lastDragonPhaseSteerAt;
    /** Retry crystal/egg podium clear for a few seconds after dragon kill. */
    private static volatile long crystalClearUntil;
    /** Last power score we sized the living dragon to (script TEMP_DRAGON_SCALE_SCORE). */
    private static volatile double lastDragonScaleScore = -1.0;
    private static volatile long lastPodiumRepairAt;

    /** When true, intentional GUI/staff dragon spawns may join The End. */
    private static final ThreadLocal<Boolean> ALLOW_INTENTIONAL_DRAGON_SPAWN =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final Map<UUID, PendingTp> PENDING_TP = new ConcurrentHashMap<>();

    private EndDimensionStrength() {}

    /** True while {@link #spawnFightLinkedDragon} / orphan fallback is intentionally spawning. */
    public static boolean isIntentionalDragonSpawnAllowed() {
        return Boolean.TRUE.equals(ALLOW_INTENTIONAL_DRAGON_SPAWN.get());
    }

    /**
     * Block vanilla / natural End Dragon joins when natural spawn is disabled.
     * Call from {@link net.minecraftforge.event.entity.EntityJoinLevelEvent}.
     *
     * @return true if the dragon was rejected
     */
    public static boolean rejectUnauthorizedDragonJoin(Entity entity) {
        if (!(entity instanceof EnderDragon)) {
            return false;
        }
        if (!DifficultyConfig.get().enableEndDimensionStrength) {
            return false;
        }
        // Product rule: only Difficulty GUI player summons may spawn dragons.
        if (isIntentionalDragonSpawnAllowed()) {
            return false;
        }
        if (DifficultyConfig.get().enableEndNaturalDragonSpawn) {
            return false;
        }
        try {
            entity.m_146870_();
        } catch (Throwable ignored) {
        }
        return true;
    }

    public static boolean isTheEnd(Level level) {
        if (level == null) {
            return false;
        }
        try {
            ResourceLocation dim = level.m_46472_().m_135782_(); // dimension().location()
            if (dim == null) {
                return false;
            }
            String id = dim.toString().toLowerCase(Locale.ROOT);
            if ("minecraft:the_end".equals(id) || id.contains("the_end")) {
                return true;
            }
            return id.endsWith(":end") && !id.contains("endermi");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void pulse(MinecraftServer server) {
        if (!DifficultyConfig.get().enableEndDimensionStrength || server == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            if (player == null) {
                continue;
            }
            processKillTpSettle(player);
            if (!isTheEnd(player.m_9236_())) {
                continue;
            }
            // Natural/auto End Dragon spawn removed — Difficulty GUI summon only.
        }

        ServerLevel end = server.m_129880_(Level.f_46430_); // END
        if (end != null) {
            runDragonWorldHygiene(end, now);
            tickDragonExtraAttacks(end, now);
        }

        if (now - lastWorldScanAt < SCAN_MS) {
            return;
        }
        lastWorldScanAt = now;

        // World-wide End dragon scan — dragons fly far; do not require player proximity.
        if (end != null) {
            hygieneDragons(end);
            for (EnderDragon dragon : findDragons(end)) {
                if (isPlayerSummoned(dragon)) {
                    // Live AD retarget to the summoner only (forms in/out). Never other players.
                    if (!maybeDespawnOrphanedPlayerDragon(end, dragon)) {
                        retargetPlayerDragonToSummoner(dragon, end, now);
                    }
                    continue;
                }
                if (isUnauthorizedNaturalDragon(dragon)) {
                    try {
                        dragon.m_146870_();
                    } catch (Throwable ignored) {
                    }
                    continue;
                }
                // Non-player leftover (should be rare) — leave alone after hygiene.
            }
            boolean mobScaling = DifficultyConfig.get().enableEndMobScaling;
            if (mobScaling) {
                for (ServerPlayer player : server.m_6846_().m_11314_()) {
                    if (player == null || !isTheEnd(player.m_9236_())) {
                        continue;
                    }
                    ServerLevel level = player.m_284548_();
                    if (level == null) {
                        continue;
                    }
                    AABB box = player.m_20191_().m_82400_(SCAN_RADIUS);
                    List<LivingEntity> nearby = level.m_45976_(LivingEntity.class, box);
                    PlayerPower localStrong = strongestInEnd(level, player);
                    for (LivingEntity ent : nearby) {
                        String kind = classify(ent);
                        if (kind == null || "dragon".equals(kind)) {
                            continue;
                        }
                        buffMob(ent, kind, nearbyPower(ent, level, localStrong));
                    }
                    break;
                }
            }
        }
    }

    public static void onHurt(LivingHurtEvent event) {
        if (!DifficultyConfig.get().enableEndDimensionStrength) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target == null || !isTheEnd(target.m_9236_())) {
            return;
        }
        String kind = classify(target);
        if (kind == null) {
            return;
        }
        boolean dragon = "dragon".equals(kind);
        boolean mobScaling = DifficultyConfig.get().enableEndMobScaling;
        if (!dragon && !mobScaling) {
            return;
        }
        // Attack-tick path: drop unauthorized vanilla dragons; keep multi-player fights.
        if (dragon && target.m_9236_() instanceof ServerLevel endLevel) {
            hygieneDragons(endLevel);
        }
        // Player-summoned: only summoner may damage; live-retarget to their current AD/form.
        // AD-painted dragons skip legacy End Strength DEF sponge / hit-cap — they fight like AD bosses.
        if (dragon && target instanceof EnderDragon enderDragon && isPlayerSummoned(enderDragon)) {
            ServerPlayer attacker = resolvePlayerAttacker(event);
            if (attacker != null && !isSummoner(enderDragon, attacker)) {
                event.setCanceled(true);
                event.setAmount(0.0f);
                try {
                    attacker.m_213846_(Component.m_237113_(
                            "§c[The End] §7Only the summoner can damage this dragon."));
                } catch (Throwable ignored) {
                }
                return;
            }
            if (attacker != null && isSummoner(enderDragon, attacker)) {
                applySummonerAdStats(enderDragon, attacker, "onhit");
            }
            // No End DEF mitigation — Adaptive Difficulty attributes own the fight.
            return;
        }
        float raw = event.getAmount();
        if (!(raw > 0.0f)) {
            return;
        }
        double def = readDef(target);
        if (!(def > 0.0) && event.getSource() != null
                && event.getSource().m_7639_() instanceof ServerPlayer attacker) {
            // Player-summoned dragons only sync from the summoner's AD profile (handled above).
            if (!(dragon && target instanceof EnderDragon ed && isPlayerSummoned(ed))) {
                ServerLevel level = attacker.m_284548_();
                PlayerPower power = strongestInEnd(level, attacker);
                if (dragon && target instanceof EnderDragon enderDragon) {
                    applyDragonStats(enderDragon, power, "onhit");
                } else if (mobScaling) {
                    buffMob(target, kind, power);
                }
            }
            def = readDef(target);
        }
        if (!(def > 0.0)) {
            return;
        }
        double minFrac = dragon ? DRAGON_MIN_DMG_FRAC : END_MOB_MIN_DMG_FRAC;
        double absorb = dragon ? END_FLAT_ABSORB : END_MOB_FLAT_ABSORB;
        double redCap = dragon ? END_REDUCTION_CAP : END_MOB_REDUCTION_CAP;
        double defScale = dragon ? END_DEF_SCALE : END_MOB_DEF_SCALE;
        float mitigated = (float) mitigate(raw, def, minFrac, absorb, redCap, defScale);
        // Hit-cap AFTER mitigation — do not re-floor with raw*minFrac (that undoes the cap).
        mitigated = capForHits(target, kind, mitigated);
        event.setAmount(mitigated);
    }

    public static void onKill(LivingDeathEvent event, ServerPlayer killer) {
        if (!DifficultyConfig.get().enableEndDimensionStrength || killer == null) {
            return;
        }
        LivingEntity dead = event.getEntity();
        if (dead == null) {
            return;
        }
        String kind = classify(dead);
        if (kind == null) {
            return;
        }
        boolean isDragon = "dragon".equals(kind);
        // Dragon always; other End mobs only when enableEndMobScaling (v2.11.0).
        if (isDragon || DifficultyConfig.get().enableEndMobScaling) {
            scheduleKillTp(killer, kind, dead.m_21233_());
        }
        if (!isDragon) {
            return;
        }
        lastNaturalSpawnAt = System.currentTimeMillis();
        crystalClearUntil = lastNaturalSpawnAt + 12_000L;
        MinecraftServer server = killer.m_20194_();
        if (server != null) {
            ServerLevel end = server.m_129880_(Level.f_46430_);
            if (end != null) {
                try {
                    purgeEndKiCommands(end);
                } catch (Throwable ignored) {
                }
                try {
                    cleanupEndKiProjectiles(end, false);
                } catch (Throwable ignored) {
                }
                try {
                    clearEndCrystals(end);
                } catch (Throwable ignored) {
                }
                try {
                    clearDragonEggBlocks(end);
                } catch (Throwable ignored) {
                }
                try {
                    // Vanilla kill already places an exit portal; older egg-clear wiped end_stone
                    // and left stacked portals / hollow podium — collapse to one clean fountain.
                    repairEndExitPodium(end, true);
                } catch (Throwable ignored) {
                }
            }
        }
        try {
            var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "dragon_egg"));
            ItemStack egg = item == null ? ItemStack.f_41583_ : new ItemStack(item);
            if (!egg.m_41619_()) {
                if (!killer.m_150109_().m_36054_(egg)) {
                    killer.m_36176_(egg, false);
                }
                killer.m_213846_(Component.m_237113_("§d[The End] §fDragon Egg claimed."));
            }
        } catch (Throwable ignored) {
        }
        // Full entities/ender_dragon loot (all mods) + vanilla dragon XP at death pos.
        try {
            scheduleEnsureDragonDeathRewards(dead, killer, event.getSource());
        } catch (Throwable ignored) {
        }
        SystemTelemetry.log("end_strength", "dragon_kill", killer, null, Map.of("kind", kind));
    }

    /** True when this is a Difficulty-GUI player summon (loot force-rolled by LM). */
    public static boolean isPlayerSummonedDragon(LivingEntity entity) {
        return entity instanceof EnderDragon dragon && isPlayerSummoned(dragon);
    }

    /** Mark that entity loot already ran (LivingDropsEvent) — skip our fallback roll. */
    public static void markDragonLootRolled(LivingEntity dragon) {
        if (dragon == null) {
            return;
        }
        SESSION_LOOT_ROLLED.add(dragon.m_20148_());
        try {
            CompoundTag tag = PersistentDataAccess.get(dragon);
            if (PersistentDataAccess.isWritable(tag)) {
                tag.m_128379_(NBT_LOOT_ROLLED, true);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean wasDragonLootRolled(LivingEntity dragon) {
        if (dragon == null) {
            return false;
        }
        if (SESSION_LOOT_ROLLED.contains(dragon.m_20148_())) {
            return true;
        }
        return PersistentDataAccess.flag(dragon, NBT_LOOT_ROLLED);
    }

    private static void markDragonXpAwarded(LivingEntity dragon) {
        if (dragon == null) {
            return;
        }
        SESSION_XP_AWARDED.add(dragon.m_20148_());
        try {
            CompoundTag tag = PersistentDataAccess.get(dragon);
            if (PersistentDataAccess.isWritable(tag)) {
                tag.m_128379_(NBT_XP_AWARDED, true);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean wasDragonXpAwarded(LivingEntity dragon) {
        if (dragon == null) {
            return false;
        }
        if (SESSION_XP_AWARDED.contains(dragon.m_20148_())) {
            return true;
        }
        return PersistentDataAccess.flag(dragon, NBT_XP_AWARDED);
    }

    /**
     * After death processing: force-roll {@code minecraft:entities/ender_dragon} for
     * player summons (Forge GLMs + Architectury injects — Iron's Spellbooks dragonskin,
     * Simply Swords/More uniques, and any other loot-table mod) and award vanilla XP
     * at the death position. Player-summoned dragons are then removed so the dying
     * animation cannot double-award XP at the main island.
     */
    private static void scheduleEnsureDragonDeathRewards(
            LivingEntity dead, ServerPlayer killer, net.minecraft.world.damagesource.DamageSource source
    ) {
        if (!(dead instanceof EnderDragon dragon) || killer == null) {
            return;
        }
        MinecraftServer server = killer.m_20194_();
        if (server == null || !(dragon.m_9236_() instanceof ServerLevel end)) {
            return;
        }
        UUID dragonId = dragon.m_20148_();
        Vec3 origin = dragon.m_20182_();
        boolean playerSummon = isPlayerSummoned(dragon);
        net.minecraft.world.damagesource.DamageSource dmg = source != null
                ? source
                : killer.m_269291_().m_269333_(killer);
        server.execute(() -> {
            try {
                EnderDragon still = null;
                for (EnderDragon d : findDragons(end)) {
                    if (d != null && dragonId.equals(d.m_20148_())) {
                        still = d;
                        break;
                    }
                }
                LivingEntity lootEntity = still != null ? still : dragon;

                // Player summons: always roll the full ender_dragon table once (all mods).
                // Others: fallback only when LivingDrops never marked a roll.
                boolean needLoot = !SESSION_LOOT_ROLLED.contains(dragonId)
                        && !wasDragonLootRolled(lootEntity);
                if (needLoot) {
                    int spawned = rollEnderDragonLootTable(end, lootEntity, killer, dmg, origin);
                    markDragonLootRolled(lootEntity);
                    if (spawned > 0) {
                        LmLog.event(
                                "[{}] End dragon loot table rolled {} stack(s) at death pos"
                                        + " (Iron's / Simply / other entities/ender_dragon mods)",
                                AdaptiveDifficultyMod.MOD_ID, spawned);
                    }
                }

                // Player summons: award vanilla XP at the fight and stop the dying
                // animation so tickDeath cannot drop a second orb shower at 0,0.
                if (playerSummon && !SESSION_XP_AWARDED.contains(dragonId)
                        && !wasDragonXpAwarded(lootEntity)) {
                    int xp = awardVanillaDragonExperience(end, origin);
                    markDragonXpAwarded(lootEntity);
                    if (xp > 0) {
                        LmLog.event(
                                "[{}] End dragon XP awarded at death pos: {}",
                                AdaptiveDifficultyMod.MOD_ID, xp);
                    }
                    if (still != null && !still.m_213877_()) {
                        try {
                            still.m_142687_(Entity.RemovalReason.KILLED); // remove — no tickDeath XP
                        } catch (Throwable t) {
                            try {
                                still.m_146870_();
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                AdaptiveDifficultyMod.LOGGER.debug(
                        "[{}] ensure dragon death rewards: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
            }
        });
    }

    /**
     * Vanilla End Dragon XP totals from {@code EnderDragon.tickDeath}:
     * 12_000 on the world's first kill, otherwise 500.
     */
    private static int awardVanillaDragonExperience(ServerLevel end, Vec3 origin) {
        if (end == null || origin == null) {
            return 0;
        }
        try {
            if (!end.m_46469_().m_46207_(net.minecraft.world.level.GameRules.f_46135_)) { // doMobLoot
                return 0;
            }
        } catch (Throwable ignored) {
        }
        int xp = DRAGON_XP_REPEAT;
        try {
            EndDragonFight fight = end.m_8586_();
            if (fight != null && !fight.m_64099_()) { // hasPreviouslyKilledDragon
                xp = DRAGON_XP_FIRST;
            }
        } catch (Throwable ignored) {
        }
        try {
            net.minecraft.world.entity.ExperienceOrb.m_147082_(end, origin, xp); // award
            return xp;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] dragon XP award failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
            return 0;
        }
    }

    /**
     * Roll {@code minecraft:entities/ender_dragon} via Forge's patched loot API so
     * global loot modifiers (e.g. Iron's Spellbooks dragonskin) and table injects
     * (Simply Swords / Simply More) all apply. Spawns stacks in-world at the death origin.
     */
    private static int rollEnderDragonLootTable(
            ServerLevel end,
            LivingEntity dragon,
            ServerPlayer killer,
            net.minecraft.world.damagesource.DamageSource source,
            Vec3 origin
    ) {
        if (end == null || dragon == null) {
            return 0;
        }
        MinecraftServer server = end.m_7654_();
        if (server == null) {
            return 0;
        }
        var lootData = server.m_278653_(); // getLootData
        if (lootData == null) {
            return 0;
        }
        ResourceLocation tableId = new ResourceLocation("minecraft", "entities/ender_dragon");
        var table = lootData.m_278676_(tableId); // getLootTable
        if (table == null || table == net.minecraft.world.level.storage.loot.LootTable.f_79105_) {
            return 0;
        }
        Vec3 at = origin != null ? origin : dragon.m_20182_();
        var paramsBuilder = new net.minecraft.world.level.storage.loot.LootParams.Builder(end)
                .m_287286_(net.minecraft.world.level.storage.loot.parameters.LootContextParams.f_81455_, dragon) // THIS_ENTITY
                .m_287286_(net.minecraft.world.level.storage.loot.parameters.LootContextParams.f_81460_, at) // ORIGIN
                .m_287286_(net.minecraft.world.level.storage.loot.parameters.LootContextParams.f_81457_,
                        source != null ? source : killer.m_269291_().m_269333_(killer)) // DAMAGE_SOURCE
                .m_287289_(net.minecraft.world.level.storage.loot.parameters.LootContextParams.f_81458_, killer) // KILLER
                .m_287289_(net.minecraft.world.level.storage.loot.parameters.LootContextParams.f_81459_, killer); // DIRECT_KILLER
        if (killer != null) {
            paramsBuilder.m_287286_(
                    net.minecraft.world.level.storage.loot.parameters.LootContextParams.f_81456_, killer); // LAST_DAMAGE_PLAYER
            try {
                paramsBuilder.m_287239_(killer.m_36336_()); // withLuck
            } catch (Throwable ignored) {
            }
        }
        var params = paramsBuilder.m_287235_(
                net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.f_81415_); // ENTITY
        java.util.ArrayList<ItemStack> stacks = new java.util.ArrayList<>();
        table.m_287228_(params, stack -> { // getRandomItems consumer
            if (stack != null && !stack.m_41619_()) {
                stacks.add(stack.m_41777_());
            }
        });
        if (stacks.isEmpty()) {
            return 0;
        }
        // Paid summons: one bonus roll per kill; a hit copies every stack this table roll produced.
        if (isPlayerSummonedDragon(dragon)
                && com.dbzlegacy.adaptivedifficulty.progression.boss.SummonLootBonus.markDone(dragon)) {
            int lootTier = com.dbzlegacy.adaptivedifficulty.progression.boss.SummonLootBonus.stampedTier(dragon);
            int pct = lootTier < 0 ? 0
                    : com.dbzlegacy.adaptivedifficulty.progression.boss.SummonLootBonus.percent(
                            lootTier, PLAYER_SUMMON_MIN_TIER);
            java.util.List<ItemStack> bonus = com.dbzlegacy.adaptivedifficulty.progression.boss.SummonLootBonus
                    .rollCopies(stacks, pct, end.f_46441_);
            if (!bonus.isEmpty()) {
                LmLog.event("[{}] End dragon summon loot bonus: +{} stack(s) (T{})",
                        AdaptiveDifficultyMod.MOD_ID, bonus.size(), lootTier);
                stacks.addAll(bonus);
            }
        }
        int spawned = 0;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.m_41619_()) {
                continue;
            }
            try {
                // Spawn at death origin so off-island kills keep loot near the fight.
                net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                        end, at.f_82479_, at.f_82480_ + 0.5, at.f_82481_, stack);
                drop.m_20334_(
                        (end.f_46441_.m_188501_() - 0.5) * 0.15,
                        0.2,
                        (end.f_46441_.m_188501_() - 0.5) * 0.15);
                end.m_7967_(drop);
                spawned++;
            } catch (Throwable ignored) {
            }
        }
        return spawned;
    }

    /** Spawn is disabled. Admins clear with {@link #cmdCleanupDragons}. */
    public static int cmdSpawnDragon(ServerPlayer player) {
        if (player != null) {
            msg(player, "§c[The End] Dragon spawn is disabled."
                    + " §7Ask staff to clear the dragon.");
        }
        return 0;
    }

    /**
     * Difficulty GUI paid summon: personal AD ON, active T4–T7, 3× Ancient Netherite.
     * Scales to the summoner's Adaptive Difficulty profile; damage-locked to them.
     * Spawns near the summoner so off-island players get a local fight.
     *
     * @return chat message (empty = silent success path already messaged)
     */
    public static com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.SummonOutcome playerSummonOutcome(ServerPlayer player) {
        if (player == null) {
            return com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.SummonOutcome.fail("§cPlayers only.");
        }
        if (!DifficultyConfig.get().enableEndDimensionStrength) {
            return com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.SummonOutcome.fail("§cEnd Dimension Strength is disabled.");
        }
        if (!DifficultyConfig.get().enableEndPlayerDragonSummon) {
            return com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.SummonOutcome.fail("§cDragon spawn is disabled. §7Ask staff to clear the dragon.");
        }
        if (!SystemGate.participates(player)) {
            return com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.SummonOutcome.fail("§cTurn personal Adaptive Difficulty ON to summon the End Dragon.");
        }
        PlayerDifficultyData data = DifficultyCache.data(player);
        int tier = data.getActiveTier();
        if (tier < PLAYER_SUMMON_MIN_TIER || tier > PLAYER_SUMMON_MAX_TIER) {
            return com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.SummonOutcome.fail("§cNeed an active Unlock Tier T"
                    + PLAYER_SUMMON_MIN_TIER + "–T" + PLAYER_SUMMON_MAX_TIER
                    + " (you: T" + tier + "). Prestige "
                    + PLAYER_SUMMON_MIN_TIER + "+ unlocks T"
                    + PLAYER_SUMMON_MIN_TIER + " eligibility — buy/activate it first.");
        }
        if (!isTheEnd(player.m_9236_())) {
            return com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.SummonOutcome.fail("§cYou must be in The End to summon the dragon. §7Use TP to reach it.");
        }
        MinecraftServer server = player.m_20194_();
        if (server == null) {
            return com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.SummonOutcome.fail("§cServer unavailable.");
        }
        ServerLevel end = server.m_129880_(Level.f_46430_);
        if (end == null) {
            return com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.SummonOutcome.fail("§cEnd dimension unavailable.");
        }
        hygieneDragons(end);
        EnderDragon owned = findOwnedDragon(end, player);
        if (owned != null && owned.m_6084_()) {
            return com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.SummonOutcome.fail("§eYour Ender Dragon is already alive."
                    + " §8HP §c" + DmzRewards.formatWhole(owned.m_21233_())
                    + " §8· AD T" + Math.max(0, PersistentDataAccess.getLong(owned, NBT_AD_TIER, tier)));
        }
        long cost = summonCopperCost();
        boolean free = PaidFeatureAccess.bypassAncientCoinCost(player);
        long charge = free ? 0L : cost;
        String costText = free ? "free (staff)" : AncientCoinEconomy.formatExactCost(cost);
        if (charge > 0L && !AncientCoinEconomy.canAfford(player, charge)) {
            return com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.SummonOutcome.fail(AncientCoinEconomy.missingText(player, charge));
        }
        if (charge > 0L && !AncientCoinEconomy.charge(player, charge)) {
            return com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.SummonOutcome.fail(AncientCoinEconomy.missingText(player, charge));
        }
        PlayerPower power = adScaledPower(player);
        EnderDragon dragon = spawnNearPlayerDragon(end, player);
        if (dragon == null) {
            AncientCoinEconomy.grantExact(player, AncientCoinEconomy.CoinKind.NETHERITE,
                    PLAYER_SUMMON_NETHERITE_COST);
            return com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.SummonOutcome.fail("§cFailed to spawn — try again in open sky nearby.");
        }
        stampPlayerSummon(dragon, player, tier, power);
        applySummonerAdStats(dragon, player, "spawn");
        lastDragonScaleScore = score(power);
        lastNaturalSpawnAt = System.currentTimeMillis();
        naturalTimerArmed = true;
        // Menu-first: the confirmation paints on the summon page, not in chat.
        String done = com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.YELLOW
                + "Summoned Adaptive Ender Dragon with "
                + com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.DANGER
                + DmzRewards.formatWhole(dragon.m_21233_())
                + com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.YELLOW
                + " HP (T" + tier + " AD, " + power.name + " / Lv" + power.level + ")."
                + "\n" + com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.LABEL + "Cost "
                + com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.VALUE + costText
                + com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.LABEL
                + " - only you can damage this dragon."
                + "\n" + com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.YELLOW
                + com.dbzlegacy.adaptivedifficulty.progression.boss.SummonLootBonus.describe(
                        tier, PLAYER_SUMMON_MIN_TIER);
        SystemTelemetry.log("end_strength", "dragon_spawn", player, null, Map.of(
                "via", "gui_summon",
                "tier", tier,
                "cost", cost));
        return com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.SummonOutcome.ok(done);
    }

    /** Legacy string form: blank on success (the outcome text is for the menu). */
    public static String cmdPlayerSummon(ServerPlayer player) {
        var out = playerSummonOutcome(player);
        return out.ok() ? "" : out.text();
    }

    /** Copper value of {@link #PLAYER_SUMMON_NETHERITE_COST} Ancient Netherite coins. */
    public static long summonCopperCost() {
        int n = Math.max(1, DifficultyConfig.get().endDragonSummonNetheriteCost);
        return AncientCoinEconomy.CoinKind.NETHERITE.copperValue * (long) n;
    }

    /** Short GUI tip for the summon button. */
    public static String summonRequirementTip(ServerPlayer player) {
        long cost = summonCopperCost();
        boolean free = player != null && PaidFeatureAccess.bypassAncientCoinCost(player);
        String costText = free ? "free (staff)" : AncientCoinEconomy.formatExactCost(cost);
        if (player == null) {
            return "T4–T7 ON · " + costText;
        }
        int tier = DifficultyCache.data(player).getActiveTier();
        boolean on = SystemGate.participates(player);
        return (on ? "§aON" : "§cOFF") + " §8· T" + tier
                + " §8· " + costText;
    }

    /** True when tier/personal gates pass — location is checked on confirm. */
    public static boolean canOpenSummonMenu(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (!DifficultyConfig.get().enableEndDimensionStrength
                || !DifficultyConfig.get().enableEndPlayerDragonSummon) {
            return false;
        }
        if (!SystemGate.participates(player)) {
            return false;
        }
        int tier = DifficultyCache.data(player).getActiveTier();
        return tier >= PLAYER_SUMMON_MIN_TIER && tier <= PLAYER_SUMMON_MAX_TIER;
    }

    /** Loot bonus line for the dragon confirm page at the player's current tier. */
    public static String summonLootBonusLine(ServerPlayer player) {
        int tier = player == null ? 0 : DifficultyCache.data(player).getActiveTier();
        if (tier >= PLAYER_SUMMON_MIN_TIER && tier <= PLAYER_SUMMON_MAX_TIER) {
            return com.dbzlegacy.adaptivedifficulty.progression.boss.SummonLootBonus.describe(
                    tier, PLAYER_SUMMON_MIN_TIER);
        }
        return "Higher tiers drop more: +"
                + Math.max(0, DifficultyConfig.get().summonLootBonusPerTierPercent)
                + "% chance per kill to double every drop, per tier above T" + PLAYER_SUMMON_MIN_TIER + ".";
    }

    /** Lore for Difficulty → End Dragon submenu (CNPC / chest / CMI). */
    public static List<String> summonMenuLines(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        if (!DifficultyConfig.get().enableEndDimensionStrength) {
            lines.add("§cEnd Dimension Strength is disabled.");
            return lines;
        }
        if (!DifficultyConfig.get().enableEndPlayerDragonSummon) {
            lines.add("§cDragon spawn is disabled.");
            lines.add("§7Ask staff to clear the dragon.");
            return lines;
        }
        if (player == null) {
            lines.add("§7Unavailable.");
            return lines;
        }
        PlayerDifficultyData data = DifficultyCache.data(player);
        int tier = data.getActiveTier();
        boolean personalOn = SystemGate.participates(player);
        long cost = summonCopperCost();
        boolean free = PaidFeatureAccess.bypassAncientCoinCost(player);
        String costText = free ? "free (staff)" : AncientCoinEconomy.formatExactCost(cost);
        lines.add("§7Scales to your Adaptive Difficulty boss profile.");
        lines.add("§7Active tier §fT" + tier + " §8(need T"
                + PLAYER_SUMMON_MIN_TIER + "–T" + PLAYER_SUMMON_MAX_TIER + ")");
        lines.add("§7Personal AD §f" + (personalOn ? "ON" : "OFF"));
        lines.add("§7Cost §f" + costText + " §8(" + PLAYER_SUMMON_NETHERITE_COST + " Ancient Netherite)");
        lines.add("§7Location §fThe End §8only");
        lines.add("§8Only you can damage your summoned dragon.");
        if (!personalOn) {
            lines.add("§cTurn on personal difficulty on the main Difficulty page first.");
        } else if (tier < PLAYER_SUMMON_MIN_TIER || tier > PLAYER_SUMMON_MAX_TIER) {
            lines.add("§cActivate an unlock tier T4–T7 first.");
        } else if (!isTheEnd(player.m_9236_())) {
            lines.add("§eTravel to The End, then confirm summon.");
        } else {
            MinecraftServer server = player.m_20194_();
            ServerLevel end = server == null ? null : server.m_129880_(Level.f_46430_);
            if (end != null) {
                EnderDragon owned = findOwnedDragon(end, player);
                if (owned != null && owned.m_6084_()) {
                    lines.add("§eYou already have a living dragon.");
                    lines.add("§8HP §c" + DmzRewards.formatWhole(owned.m_21233_()));
                } else {
                    lines.add("§aReady — confirm below to spawn near you.");
                }
            } else {
                lines.add("§aRequirements met — confirm in The End.");
            }
        }
        return lines;
    }

    /**
     * Spawn a dragon near the summoner without clearing other players' dragons.
     * Uses orphan spawn (not EndDragonFight island create) so off-island fights work.
     */
    private static EnderDragon spawnNearPlayerDragon(ServerLevel end, ServerPlayer requester) {
        if (end == null || requester == null) {
            return null;
        }
        return spawnOrphanFallback(end, requester);
    }

    /**
     * Legacy EndDragonFight-linked spawn at the main island. Kept for internal
     * recovery only — clears existing dragons; do not use for multi-player summons.
     */
    private static EnderDragon spawnFightLinkedDragon(ServerLevel end, ServerPlayer requester) {
        if (end == null) {
            return null;
        }
        ALLOW_INTENTIONAL_DRAGON_SPAWN.set(Boolean.TRUE);
        try {
            EndDragonFight fight = end.m_8586_(); // dragonFight
            if (fight == null) {
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] EndDragonFight is null — visit The End once so the dimension initializes",
                        AdaptiveDifficultyMod.MOD_ID);
                return spawnOrphanFallback(end, requester);
            }
            try {
                fight.m_287277_(); // skipArenaLoadedCheck
            } catch (Throwable ignored) {
            }
            setFightBoolean(fight, "f_64068_", false);
            setFightBoolean(fight, "f_64069_", true);
            try {
                clearEndCrystals(end);
            } catch (Throwable ignored) {
            }
            try {
                fight.m_64101_(); // resetSpikeCrystals
            } catch (Throwable ignored) {
            }
            try {
                restoreTowerCrystals(end);
            } catch (Throwable ignored) {
            }
            EnderDragon dragon = invokeCreateNewDragon(fight);
            if (dragon == null) {
                dragon = spawnOrphanFallback(end, requester);
                if (dragon != null) {
                    try {
                        dragon.m_287231_(fight); // setDragonFight
                    } catch (Throwable ignored) {
                    }
                }
            }
            return dragon;
        } finally {
            ALLOW_INTENTIONAL_DRAGON_SPAWN.set(Boolean.FALSE);
        }
    }

    private static EnderDragon spawnOrphanFallback(ServerLevel end, ServerPlayer requester) {
        boolean nested = isIntentionalDragonSpawnAllowed();
        if (!nested) {
            ALLOW_INTENTIONAL_DRAGON_SPAWN.set(Boolean.TRUE);
        }
        try {
            EnderDragon dragon = net.minecraft.world.entity.EntityType.f_20565_.m_20615_(end);
            if (dragon == null) {
                return null;
            }
            double x = 0.5;
            double y = 128.0;
            double z = 0.5;
            float yaw = 0.0f;
            if (requester != null && isTheEnd(requester.m_9236_())) {
                x = requester.m_20185_();
                y = requester.m_20186_() + 16.0;
                z = requester.m_20189_();
                yaw = requester.m_146908_();
                if (y < 40.0) {
                    y = 72.0;
                }
                if (y > 240.0) {
                    y = 200.0;
                }
            }
            dragon.m_7678_(x, y, z, yaw, 0.0f);
            end.m_7967_(dragon);
            return dragon;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] orphan dragon spawn failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
            return null;
        } finally {
            if (!nested) {
                ALLOW_INTENTIONAL_DRAGON_SPAWN.set(Boolean.FALSE);
            }
        }
    }

    private static EnderDragon invokeCreateNewDragon(EndDragonFight fight) {
        if (fight == null) {
            return null;
        }
        try {
            java.lang.reflect.Method m = EndDragonFight.class.getDeclaredMethod("m_64110_");
            m.setAccessible(true);
            Object raw = m.invoke(fight);
            return raw instanceof EnderDragon d ? d : null;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] createNewDragon reflect failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
            return null;
        }
    }

    private static void setFightBoolean(EndDragonFight fight, String field, boolean value) {
        if (fight == null || field == null) {
            return;
        }
        try {
            java.lang.reflect.Field f = EndDragonFight.class.getDeclaredField(field);
            f.setAccessible(true);
            f.setBoolean(fight, value);
        } catch (Throwable ignored) {
        }
    }

    /** Re-place tower end crystals so the dragon keeps heal/perch AI (script restoreTowerCrystals). */
    private static int restoreTowerCrystals(ServerLevel end) {
        if (end == null) {
            return 0;
        }
        int placed = 0;
        try {
            List<net.minecraft.world.level.levelgen.feature.SpikeFeature.EndSpike> spikes =
                    net.minecraft.world.level.levelgen.feature.SpikeFeature.m_66858_(end);
            if (spikes == null || spikes.isEmpty()) {
                return 0;
            }
            for (net.minecraft.world.level.levelgen.feature.SpikeFeature.EndSpike spike : spikes) {
                int cx = spike.m_66886_(); // getCenterX
                int cz = spike.m_66893_(); // getCenterZ
                int height = spike.m_66899_(); // getHeight
                int y = Math.max(70, height + 1);
                try {
                    EndCrystal crystal = EntityType.f_20564_.m_20615_(end); // END_CRYSTAL
                    if (crystal == null) {
                        continue;
                    }
                    crystal.m_7678_(cx + 0.5, y, cz + 0.5, 0.0f, 0.0f);
                    crystal.m_31056_(true); // setShowBottom
                    end.m_7967_(crystal);
                    placed++;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] restoreTowerCrystals failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
        return placed;
    }

    /** Trigger 51 — clear End dragons. */
    public static int cmdCleanupDragons(ServerPlayer player) {
        MinecraftServer server = player == null ? null : player.m_20194_();
        return cmdCleanupDragons(server, player);
    }

    /** Admin clear from a command source. {@code player} may be null for console. */
    public static int cmdCleanupDragons(MinecraftServer server, ServerPlayer player) {
        if (server == null && player != null) {
            server = player.m_20194_();
        }
        if (server == null) {
            return 0;
        }
        ServerLevel end = server.m_129880_(Level.f_46430_);
        if (end == null) {
            return 0;
        }
        int removed = 0;
        java.util.ArrayList<Entity> dragons = new java.util.ArrayList<>();
        for (Entity e : end.m_8583_()) {
            if (e instanceof EnderDragon) {
                dragons.add(e);
            }
        }
        for (Entity e : dragons) {
            e.m_146870_();
            removed++;
        }
        if (player != null) {
            msg(player, "§7[The End] Cleared §f" + removed + "§7 dragon(s).");
        }
        SystemTelemetry.log("end_strength", "dragon_cleanup", player, null, Map.of("removed", removed));
        return removed;
    }

    /**
     * Despawn the player's Difficulty-GUI End Dragon (personal AD off / death / orphan).
     *
     * @return number of dragons removed
     */
    public static int despawnOwnedDragon(ServerPlayer owner) {
        if (owner == null) {
            return 0;
        }
        MinecraftServer server = owner.m_20194_();
        if (server == null) {
            return 0;
        }
        ServerLevel end = server.m_129880_(Level.f_46430_);
        if (end == null) {
            return 0;
        }
        int removed = 0;
        for (EnderDragon dragon : findDragons(end)) {
            if (!isPlayerSummoned(dragon) || !isSummoner(dragon, owner)) {
                continue;
            }
            try {
                dragon.m_146870_();
                removed++;
            } catch (Throwable ignored) {
            }
        }
        if (removed > 0) {
            msg(owner, "§7[The End] §cYour Ender Dragon despawned.");
            SystemTelemetry.log("end_strength", "dragon_despawn", owner, null,
                    Map.of("removed", removed, "reason", "owner_gate"));
        }
        return removed;
    }

    /**
     * True when this player has a living Difficulty-GUI End Dragon.
     * Used to suspend nearby Adaptive Difficulty mob scaling during the fight.
     */
    public static boolean hasAliveSummonedDragon(ServerPlayer owner) {
        if (owner == null) {
            return false;
        }
        MinecraftServer server = owner.m_20194_();
        if (server == null) {
            return false;
        }
        ServerLevel end = server.m_129880_(Level.f_46430_);
        if (end == null) {
            return false;
        }
        for (EnderDragon dragon : findDragons(end)) {
            if (dragon != null && dragon.m_6084_()
                    && isPlayerSummoned(dragon) && isSummoner(dragon, owner)) {
                return true;
            }
        }
        return false;
    }

    /** Pulse: drop player dragons whose summoner is gone or no longer participating.
     * @return true if the dragon was despawned */
    private static boolean maybeDespawnOrphanedPlayerDragon(ServerLevel end, EnderDragon dragon) {
        if (end == null || dragon == null || !isPlayerSummoned(dragon)) {
            return false;
        }
        // Let kill rewards (loot + XP) finish — do not wipe a dying dragon.
        if (isDragonDying(dragon) || wasDragonXpAwarded(dragon) || wasDragonLootRolled(dragon)) {
            return false;
        }
        ServerPlayer owner = resolveSummoner(end, dragon);
        if (owner == null || !owner.m_6084_() || !SystemGate.participates(owner)) {
            try {
                dragon.m_146870_();
            } catch (Throwable ignored) {
            }
            if (owner != null && owner.m_6084_()) {
                msg(owner, "§7[The End] §cYour Ender Dragon despawned (difficulty off).");
            }
            SystemTelemetry.log("end_strength", "dragon_despawn", owner, null,
                    Map.of("reason", owner == null ? "owner_offline" : "owner_gate"));
            return true;
        }
        return false;
    }

    private static void maybeNaturalDragon(ServerPlayer player, long now) {
        // Natural/auto spawn permanently disabled — Difficulty GUI only.
        if (!DifficultyConfig.get().enableEndNaturalDragonSpawn) {
            return;
        }
        // Staff natural path removed with staff spawn; keep no-op even if config flipped.
    }

    /**
     * End dragon hygiene:
     * <ul>
     *   <li>Remove unauthorized vanilla dragons</li>
     *   <li>At most one living player-summoned dragon per summoner UUID</li>
     *   <li>Never cull another player's summoned dragon</li>
     * </ul>
     *
     * @return any remaining living dragon (for ki-cleanup “has dragon” checks), or null
     */
    static EnderDragon hygieneDragons(ServerLevel end) {
        if (end == null) {
            return null;
        }
        List<EnderDragon> dragons = findDragons(end);
        if (dragons.isEmpty()) {
            return null;
        }
        Map<String, EnderDragon> keepBySummoner = new HashMap<>();
        List<EnderDragon> remove = new ArrayList<>();
        EnderDragon any = null;
        for (EnderDragon d : dragons) {
            if (d == null || !d.m_6084_()) {
                continue;
            }
            if (isUnauthorizedNaturalDragon(d)) {
                remove.add(d);
                continue;
            }
            if (isPlayerSummoned(d)) {
                String owner = PersistentDataAccess.getString(d, NBT_SUMMONER);
                if (owner == null || owner.isBlank()) {
                    remove.add(d);
                    continue;
                }
                if (!DifficultyConfig.get().endEnforceSingleDragon) {
                    any = d;
                    continue;
                }
                EnderDragon prev = keepBySummoner.get(owner.toLowerCase(Locale.ROOT));
                if (prev == null) {
                    keepBySummoner.put(owner.toLowerCase(Locale.ROOT), d);
                    any = d;
                } else if (dragonHealthScore(d) > dragonHealthScore(prev)) {
                    remove.add(prev);
                    keepBySummoner.put(owner.toLowerCase(Locale.ROOT), d);
                    any = d;
                } else {
                    remove.add(d);
                }
                continue;
            }
            // Staff-stamped leftovers (legacy) — leave until staff clear.
            any = d;
        }
        int removed = 0;
        for (EnderDragon extra : remove) {
            try {
                extra.m_146870_();
                removed++;
            } catch (Throwable ignored) {
            }
        }
        if (removed > 0) {
            LmLog.event(
                    "[{}] End dragon hygiene: removed {} (multi-player safe)",
                    AdaptiveDifficultyMod.MOD_ID, removed);
        }
        if (any != null && any.m_6084_()) {
            return any;
        }
        for (EnderDragon d : findDragons(end)) {
            if (d != null && d.m_6084_()) {
                return d;
            }
        }
        return null;
    }

    /** @deprecated use {@link #hygieneDragons(ServerLevel)} — multi-player safe. */
    static EnderDragon enforceSingleDragon(ServerLevel end) {
        return hygieneDragons(end);
    }

    private static EnderDragon findOwnedDragon(ServerLevel end, ServerPlayer owner) {
        if (end == null || owner == null) {
            return null;
        }
        EnderDragon best = null;
        double bestHp = -1.0;
        for (EnderDragon d : findDragons(end)) {
            if (d == null || !d.m_6084_() || !isPlayerSummoned(d) || !isSummoner(d, owner)) {
                continue;
            }
            double hp = dragonHealthScore(d);
            if (hp > bestHp) {
                best = d;
                bestHp = hp;
            }
        }
        return best;
    }

    /**
     * Purge / cap End ki_laser + ki_blast.
     * No dragon: kill all. Dragon alive: hard-cap count.
     */
    static void cleanupEndKiProjectiles(ServerLevel end, boolean hasDragon) {
        if (end == null || !DifficultyConfig.get().endKiCleanupEnabled) {
            return;
        }
        if (!hasDragon && DifficultyConfig.get().endKiPurgeWhenNoDragon) {
            purgeEndKiCommands(end);
            return;
        }
        if (!hasDragon) {
            return;
        }
        List<Entity> ents = collectEndKiEntities(end);
        int n = ents.size();
        int cap = Math.max(1, DifficultyConfig.get().endKiMaxAliveWhileDragon);
        if (n <= cap) {
            return;
        }
        // Flooded End: discard collected entities in one pass (no console kill spam).
        if (n > cap * 2) {
            purgeEndKiCommands(end);
            LmLog.event(
                    "[{}] Ki flood ({} > {}); purged all End ki projectiles",
                    AdaptiveDifficultyMod.MOD_ID, n, cap * 2);
            return;
        }
        int over = n - cap;
        int killed = 0;
        for (int j = 0; j < ents.size() && killed < over; j++) {
            if (discardEntitySafe(ents.get(j))) {
                killed++;
            }
        }
        if (killed < over) {
            purgeEndKiCommands(end);
            LmLog.event(
                    "[{}] Ki cap exceeded ({}); purged all End ki projectiles",
                    AdaptiveDifficultyMod.MOD_ID, n);
        } else if (killed > 0) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] Ki cap: discarded {} (had {})",
                    AdaptiveDifficultyMod.MOD_ID, killed, n);
        }
    }

    /** Throttled single-dragon enforce + ki cleanup (shared across End). */
    static void runDragonWorldHygiene(ServerLevel end, long now) {
        if (end == null) {
            return;
        }
        try {
            if (crystalClearUntil > 0L && now <= crystalClearUntil) {
                clearEndCrystals(end);
                clearDragonEggBlocks(end);
            } else if (crystalClearUntil > 0L && now > crystalClearUntil) {
                crystalClearUntil = 0L;
            }
            if (now - lastHygieneAt < HYGIENE_INTERVAL_MS) {
                return;
            }
            lastHygieneAt = now;
            EnderDragon kept = hygieneDragons(end);
            cleanupEndKiProjectiles(end, kept != null);
            // Opportunistic: only when duplicate portal Y-levels are present.
            if (countExitPortalYLevels(end) > 1) {
                repairEndExitPodium(end, true);
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] runDragonWorldHygiene: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    /** Live 2.12.0+ — periodic DMZ ki beam/blast; each living summon gets its own AI tick. */
    static void tickDragonExtraAttacks(ServerLevel end, long now) {
        if (!DRAGON_EXTRA_ATTACKS || end == null || !DifficultyConfig.get().enableEndDimensionStrength) {
            return;
        }
        try {
            for (EnderDragon dragon : findDragons(end)) {
                if (dragon == null || !dragon.m_6084_() || isUnauthorizedNaturalDragon(dragon)) {
                    continue;
                }
                boolean playerFight = isPlayerSummoned(dragon);
                long interval = playerFight ? PLAYER_DRAGON_ATTACK_INTERVAL_MS : DRAGON_ATTACK_INTERVAL_MS;
                long lastAt = PersistentDataAccess.getLong(dragon, "end_dragon_atk_at", 0L);
                if (now - lastAt < interval) {
                    continue;
                }
                if (isDragonDying(dragon)) {
                    continue;
                }
                double range = playerFight ? PLAYER_DRAGON_ATTACK_RANGE : DRAGON_ATTACK_RANGE;
                ServerPlayer target = preferredDragonTarget(end, dragon, range);
                if (target == null) {
                    continue;
                }
                CompoundTag tag = PersistentDataAccess.get(dragon);
                if (PersistentDataAccess.isWritable(tag)) {
                    tag.m_128356_("end_dragon_atk_at", now);
                }
                // Steer vanilla phases at the summoner (strafe / charge) without replacing ki types.
                if (playerFight) {
                    maybeSteerDragonPhase(dragon, target, now);
                }
                aimLivingAt(dragon, target);
                double roll = Math.random();
                boolean beamFirst = roll < DRAGON_KI_BEAM_CHANCE;
                boolean fired;
                if (beamFirst) {
                    fired = fireDragonKiBeam(end, dragon, target);
                    if (!fired) {
                        fired = fireDragonKiBlast(end, dragon, target);
                    }
                } else {
                    fired = fireDragonKiBlast(end, dragon, target);
                }
                // Occasional combo: second shot of the other type.
                if (fired && playerFight && Math.random() < DRAGON_COMBO_CHANCE) {
                    if (beamFirst) {
                        fireDragonKiBlast(end, dragon, target);
                    } else {
                        fireDragonKiBeam(end, dragon, target);
                    }
                }
                lastDragonAttackAt = now;
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] dragon extra attack: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    private static boolean isDragonDying(EnderDragon dragon) {
        try {
            EnderDragonPhaseManager mgr = dragon.m_31157_();
            if (mgr == null) {
                return false;
            }
            DragonPhaseInstance phase = mgr.m_31415_();
            return phase != null && phase.m_7309_() == EnderDragonPhase.f_31386_; // DYING
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Nudge vanilla EndDragonFight AI toward the summoner (strafe fireballs / charge).
     * Ki beam/blast extras stay separate.
     */
    private static void maybeSteerDragonPhase(EnderDragon dragon, ServerPlayer target, long now) {
        if (dragon == null || target == null) {
            return;
        }
        long lastSteer = PersistentDataAccess.getLong(dragon, "end_dragon_phase_steer_at", 0L);
        if (now - lastSteer < PLAYER_DRAGON_PHASE_STEER_MS) {
            return;
        }
        CompoundTag tag = PersistentDataAccess.get(dragon);
        if (PersistentDataAccess.isWritable(tag)) {
            tag.m_128356_("end_dragon_phase_steer_at", now);
        }
        lastDragonPhaseSteerAt = now;
        try {
            EnderDragonPhaseManager mgr = dragon.m_31157_();
            if (mgr == null) {
                return;
            }
            DragonPhaseInstance cur = mgr.m_31415_();
            if (cur != null && cur.m_7309_() == EnderDragonPhase.f_31386_) { // DYING
                return;
            }
            // Skip while already landing / sitting crystal phases.
            if (cur != null) {
                var t = cur.m_7309_();
                if (t == EnderDragonPhase.f_31379_ // LANDING_APPROACH
                        || t == EnderDragonPhase.f_31380_ // LANDING
                        || t == EnderDragonPhase.f_31382_ // SITTING_FLAMING
                        || t == EnderDragonPhase.f_31383_ // SITTING_SCANNING
                        || t == EnderDragonPhase.f_31384_) { // SITTING_ATTACKING
                    return;
                }
            }
            boolean charge = Math.random() < 0.42;
            if (charge) {
                mgr.m_31416_(EnderDragonPhase.f_31385_); // CHARGING_PLAYER
                DragonChargePlayerPhase phase = mgr.m_31418_(EnderDragonPhase.f_31385_);
                if (phase != null) {
                    phase.m_31207_(new Vec3(
                            target.m_20185_(),
                            target.m_20186_() + target.m_20206_() * 0.35,
                            target.m_20189_()));
                }
            } else {
                mgr.m_31416_(EnderDragonPhase.f_31378_); // STRAFE_PLAYER
                DragonStrafePlayerPhase phase = mgr.m_31418_(EnderDragonPhase.f_31378_);
                if (phase != null) {
                    phase.m_31358_(target); // setAttackTarget
                }
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] dragon phase steer: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    private static ServerPlayer nearestEndPlayer(ServerLevel end, Entity from, double range) {
        if (end == null || from == null) {
            return null;
        }
        ServerPlayer best = null;
        double bestD = range * range;
        try {
            for (ServerPlayer p : end.m_6907_()) {
                if (p == null || !p.m_6084_()) {
                    continue;
                }
                double d = p.m_20275_(from.m_20185_(), from.m_20186_(), from.m_20189_());
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
        } catch (Throwable ignored) {
        }
        return best;
    }

    private static double calcDragonKiDamage(EnderDragon dragon, ServerPlayer target) {
        // Player-summoned AD dragons: use painted Adaptive Difficulty attack directly.
        if (dragon != null && isPlayerSummoned(dragon)) {
            double painted = 0.0;
            try {
                CompoundTag tag = PersistentDataAccess.get(dragon);
                if (PersistentDataAccess.isWritable(tag) && tag.m_128441_(NBT_KI_MELEE)) {
                    painted = Math.max(0.0, tag.m_128459_(NBT_KI_MELEE));
                }
            } catch (Throwable ignored) {
            }
            if (!(painted > 0.0)) {
                try {
                    var inst = dragon.m_21051_(Attributes.f_22281_);
                    if (inst != null) {
                        painted = Math.max(0.0, inst.m_22135_());
                    }
                } catch (Throwable ignored) {
                }
            }
            if (painted > 0.0) {
                return painted;
            }
        }
        double base = DRAGON_DMZ_KI_DAMAGE;
        double melee = 0.0;
        if (dragon != null) {
            try {
                CompoundTag tag = PersistentDataAccess.get(dragon);
                if (PersistentDataAccess.isWritable(tag) && tag.m_128441_(NBT_KI_MELEE)) {
                    melee = Math.max(0.0, tag.m_128459_(NBT_KI_MELEE));
                }
            } catch (Throwable ignored) {
            }
        }
        if (!(melee > 0.0) && target != null) {
            try {
                melee = Math.max(0.0, readPower(target).melee);
            } catch (Throwable ignored) {
            }
        }
        double scaled = Math.max(base, melee * DRAGON_DMZ_KI_MELEE_FRAC);
        return Math.min(DRAGON_DMZ_KI_DAMAGE_CAP, scaled);
    }

    private static void aimLivingAt(LivingEntity living, LivingEntity target) {
        if (living == null || target == null) {
            return;
        }
        try {
            Vec3 from = living.m_146892_();
            double tx = target.m_20185_();
            double ty = target.m_20186_() + target.m_20206_() * 0.45;
            double tz = target.m_20189_();
            double dx = tx - from.f_82479_;
            double dy = ty - from.f_82480_;
            double dz = tz - from.f_82481_;
            double horiz = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
            float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.max(1.0E-4, horiz))));
            if (pitch > 89f) {
                pitch = 89f;
            }
            if (pitch < -89f) {
                pitch = -89f;
            }
            living.m_146922_(yaw);
            living.m_146926_(pitch);
            living.f_20885_ = yaw; // yHeadRot
            living.f_20883_ = yaw; // yBodyRot
        } catch (Throwable ignored) {
        }
    }

    private static boolean fireDragonKiBeam(ServerLevel end, EnderDragon dragon, ServerPlayer target) {
        try {
            aimLivingAt(dragon, target);
            float dmg = (float) calcDragonKiDamage(dragon, target);
            KiLaserEntity beam = new KiLaserEntity(end, dragon);
            try {
                // Mob path: cast=0, spawns into world. Player setup leaves cast>0 / no launch.
                beam.setupKiLaser(dragon, dmg, DRAGON_DMZ_KI_SPEED_BEAM,
                        DRAGON_DMZ_KI_COLOR_MAIN, DRAGON_DMZ_KI_COLOR_BORDER,
                        DRAGON_DMZ_KI_COLOR_OUTLINE, 0);
            } catch (Throwable t1) {
                // DMZ 2.2 added the beam size as the last argument (2.1.3 always used 1.0).
                try {
                    beam.setupKiBeamPlayer(dragon, dmg, DRAGON_DMZ_KI_SPEED_BEAM,
                            DRAGON_DMZ_KI_COLOR_MAIN, DRAGON_DMZ_KI_COLOR_BORDER, DRAGON_DMZ_KI_COLOR_OUTLINE,
                            DRAGON_DMZ_KI_BEAM_SIZE);
                } catch (Throwable t2) {
                    beam.setupKiBeamPlayer(dragon, dmg, DRAGON_DMZ_KI_SPEED_BEAM,
                            DRAGON_DMZ_KI_COLOR_MAIN, DRAGON_DMZ_KI_COLOR_BORDER, DRAGON_DMZ_KI_BEAM_SIZE);
                }
            }
            try {
                beam.setHomingTarget(target.m_19879_());
            } catch (Throwable ignored) {
            }
            return spawnAndFireKi(beam, end, dragon, target, DRAGON_DMZ_KI_SPEED_BEAM, DRAGON_DMZ_KI_LIFE_BEAM);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] dragon ki beam failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
            return false;
        }
    }

    private static boolean fireDragonKiBlast(ServerLevel end, EnderDragon dragon, ServerPlayer target) {
        try {
            aimLivingAt(dragon, target);
            float dmg = (float) calcDragonKiDamage(dragon, target);
            KiBlastEntity blast = new KiBlastEntity(end, dragon);
            try {
                // Mob large-blast path (cast=0). setupKiBlastPlayer parks the shot with
                // maxLife=99999 / firing=false and already adds it to the world — that left
                // dragon blasts hovering until fireHability ran (which the old spawn path skipped).
                blast.setupKiLargeBlast(dragon, dmg, DRAGON_DMZ_KI_SPEED_BLAST,
                        DRAGON_DMZ_KI_COLOR_MAIN, DRAGON_DMZ_KI_COLOR_BORDER,
                        DRAGON_DMZ_KI_COLOR_OUTLINE, DRAGON_DMZ_KI_SIZE_BLAST, 0);
            } catch (Throwable t1) {
                try {
                    blast.setupKiBlastPlayer(dragon, dmg, DRAGON_DMZ_KI_SPEED_BLAST,
                            DRAGON_DMZ_KI_COLOR_MAIN, DRAGON_DMZ_KI_COLOR_BORDER,
                            DRAGON_DMZ_KI_COLOR_OUTLINE, DRAGON_DMZ_KI_SIZE_BLAST);
                } catch (Throwable t2) {
                    blast.setupKiBlastPlayer(dragon, dmg, DRAGON_DMZ_KI_SPEED_BLAST,
                            DRAGON_DMZ_KI_COLOR_MAIN, DRAGON_DMZ_KI_COLOR_BORDER, DRAGON_DMZ_KI_SIZE_BLAST);
                }
            }
            try {
                blast.setHomingTarget(target.m_19879_());
            } catch (Throwable ignored) {
            }
            try {
                blast.setParked(false);
                blast.setControllable(false);
            } catch (Throwable ignored) {
            }
            return spawnAndFireKi(blast, end, dragon, target, DRAGON_DMZ_KI_SPEED_BLAST, DRAGON_DMZ_KI_LIFE_BLAST);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] dragon ki blast failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
            return false;
        }
    }

    /**
     * Finish a DMZ ki projectile for the dragon.
     * <p>
     * DMZ {@code setup*} methods often {@code addFreshEntity} themselves. Returning early
     * when a second add fails left shots parked with {@code firing=false} / zero velocity
     * (hovering in air). Mirror the live script: always {@code fireHability}, then apply
     * explicit launch velocity — EnderDragon body look is unreliable for {@code getViewVector}.
     */
    private static boolean spawnAndFireKi(
            Entity proj,
            ServerLevel end,
            EnderDragon dragon,
            ServerPlayer target,
            float speed,
            int lifeTicks) {
        if (proj == null || end == null) {
            return false;
        }
        try {
            proj.getClass().getMethod("setCastTime", int.class).invoke(proj, 0);
        } catch (Throwable ignored) {
        }
        try {
            proj.getClass().getMethod("setBlockDestructionEnabled", boolean.class).invoke(proj, false);
        } catch (Throwable ignored) {
        }
        // Setup may already have spawned the entity — never abort on a failed second add.
        try {
            end.m_7967_(proj);
        } catch (Throwable ignored) {
        }
        int life = Math.max(10, lifeTicks);
        try {
            proj.getClass().getMethod("fireHability", int.class).invoke(proj, life);
        } catch (Throwable t1) {
            try {
                proj.getClass().getMethod("setFiring", boolean.class).invoke(proj, true);
                proj.getClass().getMethod("setMaxLife", int.class).invoke(proj, life);
            } catch (Throwable ignored) {
            }
        }
        // Explicit shoot toward the player — same fix as KiAttackHelper for mob ki.
        launchDragonKiToward(proj, dragon, target, speed);
        return true;
    }

    /** Apply real launch velocity toward the target (homing alone won't move a zero-speed shot). */
    private static void launchDragonKiToward(
            Entity proj, EnderDragon dragon, ServerPlayer target, float speed) {
        if (!(proj instanceof Projectile projectile) || dragon == null || target == null) {
            return;
        }
        try {
            aimLivingAt(dragon, target);
            double dx = target.m_20185_() - projectile.m_20185_();
            double dy = (target.m_20186_() + target.m_20206_() * 0.45) - projectile.m_20186_();
            double dz = target.m_20189_() - projectile.m_20189_();
            if (dx * dx + dy * dy + dz * dz < 1.0E-6) {
                Vec3 from = dragon.m_146892_();
                Vec3 to = target.m_146892_();
                dx = to.f_82479_ - from.f_82479_;
                dy = to.f_82480_ - from.f_82480_;
                dz = to.f_82481_ - from.f_82481_;
            }
            float launchSpeed = Math.max(0.75f, speed);
            projectile.m_6686_(dx, dy, dz, launchSpeed, 0.15f);
            try {
                proj.getClass().getMethod("setFiring", boolean.class).invoke(proj, true);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    /** Live DESTROY_CRYSTALS_ON_KILL — despawn all End crystals in The End. */
    static int clearEndCrystals(ServerLevel end) {
        if (!DESTROY_CRYSTALS_ON_KILL || end == null) {
            return 0;
        }
        int removed = 0;
        List<Entity> toRemove = new ArrayList<>();
        try {
            for (Entity e : end.m_8583_()) {
                if (e instanceof EndCrystal) {
                    toRemove.add(e);
                }
            }
        } catch (Throwable ignored) {
        }
        for (Entity e : toRemove) {
            try {
                e.m_146870_();
                removed++;
            } catch (Throwable ignored) {
            }
        }
        if (removed <= 0) {
            // Already empty — do not run console kill @e (spams "No entity was found").
            return 0;
        }
        return removed;
    }

    /** Live REMOVE_EGG_BLOCK — clear podium dragon_egg blocks near 0,0 (never end_portal). */
    static int clearDragonEggBlocks(ServerLevel end) {
        if (!REMOVE_EGG_BLOCK || end == null) {
            return 0;
        }
        int removed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -EGG_CLEAR_RADIUS; x <= EGG_CLEAR_RADIUS; x++) {
            for (int z = -EGG_CLEAR_RADIUS; z <= EGG_CLEAR_RADIUS; z++) {
                for (int y = EGG_CLEAR_Y_MIN; y <= EGG_CLEAR_Y_MAX; y++) {
                    pos.m_122178_(x, y, z); // set
                    try {
                        var state = end.m_8055_(pos);
                        if (!isDragonEggBlock(state)) {
                            continue;
                        }
                        end.m_46597_(pos, Blocks.f_50016_.m_49966_()); // AIR
                        removed++;
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return removed;
    }

    /** Dragon egg only — never end_portal / end_gateway / end_stone. */
    private static boolean isDragonEggBlock(BlockState state) {
        if (state == null) {
            return false;
        }
        try {
            // Correct SRG: f_50260_ = dragon_egg (NOT end_gateway).
            if (state.m_60713_(Blocks.f_50260_)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            var key = ForgeRegistries.BLOCKS.getKey(state.m_60734_());
            if (key == null) {
                return false;
            }
            String name = key.toString().toLowerCase(Locale.ROOT);
            // Exact id only — never "end_portal" / end_stone (older builds used wrong SRG ids).
            return "minecraft:dragon_egg".equals(name) || name.endsWith(":dragon_egg");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isEndPortalBlock(BlockState state) {
        if (state == null) {
            return false;
        }
        try {
            // Correct SRG: f_50257_ = end_portal (f_50259_ is end_stone).
            if (state.m_60713_(Blocks.f_50257_)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            var key = ForgeRegistries.BLOCKS.getKey(state.m_60734_());
            if (key == null) {
                return false;
            }
            String name = key.toString().toLowerCase(Locale.ROOT);
            return "minecraft:end_portal".equals(name) || name.endsWith(":end_portal");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isPodiumFillBlock(BlockState state) {
        if (state == null) {
            return false;
        }
        try {
            // bedrock / end_stone / end_portal / dragon_egg / wall_torch
            if (state.m_60713_(Blocks.f_50752_)
                    || state.m_60713_(Blocks.f_50259_)
                    || state.m_60713_(Blocks.f_50257_)
                    || state.m_60713_(Blocks.f_50260_)
                    || state.m_60713_(Blocks.f_50082_)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return isEndPortalBlock(state) || isDragonEggBlock(state);
    }

    /** Staff: rebuild a single clean exit fountain at 0,0 (fixes stacked portals). */
    public static int cmdRepairPodium(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        MinecraftServer server = player.m_20194_();
        if (server == null) {
            return 0;
        }
        ServerLevel end = server.m_129880_(Level.f_46430_);
        if (end == null) {
            msg(player, "§c[The End] End dimension unavailable.");
            return 0;
        }
        lastPodiumRepairAt = 0L; // force
        int yLevels = countExitPortalYLevels(end);
        boolean ok = repairEndExitPodium(end, true);
        if (!ok) {
            msg(player, "§c[The End] Exit podium repair failed.");
            return 0;
        }
        msg(player, "§a[The End] §fExit podium repaired"
                + (yLevels > 1 ? " §8(collapsed " + yLevels + " portal layers)" : "")
                + "§f.");
        return 1;
    }

    /**
     * Collapse duplicate exit portals / hollowed podium back to one vanilla fountain.
     * <p>
     * Older egg-clear used {@code Blocks.f_50259_} thinking it was {@code end_portal};
     * that field is actually {@code end_stone}, so it hollowed the podium skirt. Each
     * later dragon kill then placed another {@code EndPodiumFeature} — stacked portals
     * and a ruined underside. Rebuild once at a pinned origin location.
     */
    static boolean repairEndExitPodium(ServerLevel end, boolean withPortal) {
        if (end == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastPodiumRepairAt < PODIUM_REPAIR_COOLDOWN_MS) {
            return true;
        }
        try {
            BlockPos portalLoc = chooseCanonicalPortalPos(end);
            int keepY = portalLoc.m_123342_();
            int clearedGhosts = clearNonCanonicalPodiums(end, keepY);
            EndDragonFight fight = end.m_8586_();
            if (fight != null) {
                setFightPortalLocation(fight, portalLoc);
                invokeSpawnExitPortal(fight, withPortal);
            } else {
                placeEndPodiumDirect(end, portalLoc, withPortal);
            }
            try {
                clearDragonEggBlocks(end);
            } catch (Throwable ignored) {
            }
            lastPodiumRepairAt = now;
            if (clearedGhosts > 0) {
                LmLog.event(
                        "[{}] End exit podium repaired at {} (removed {} ghost podium blocks)",
                        AdaptiveDifficultyMod.MOD_ID, portalLoc, clearedGhosts);
            }
            return true;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] End exit podium repair failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
            return false;
        }
    }

    static int countExitPortalYLevels(ServerLevel end) {
        if (end == null) {
            return 0;
        }
        Map<Integer, Integer> byY = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -PODIUM_SCAN_RADIUS; x <= PODIUM_SCAN_RADIUS; x++) {
            for (int z = -PODIUM_SCAN_RADIUS; z <= PODIUM_SCAN_RADIUS; z++) {
                for (int y = PODIUM_SCAN_Y_MIN; y <= PODIUM_SCAN_Y_MAX; y++) {
                    pos.m_122178_(x, y, z);
                    try {
                        if (isEndPortalBlock(end.m_8055_(pos))) {
                            byY.merge(y, 1, Integer::sum);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return byY.size();
    }

    private static BlockPos chooseCanonicalPortalPos(ServerLevel end) {
        Map<Integer, Integer> byY = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -PODIUM_SCAN_RADIUS; x <= PODIUM_SCAN_RADIUS; x++) {
            for (int z = -PODIUM_SCAN_RADIUS; z <= PODIUM_SCAN_RADIUS; z++) {
                for (int y = PODIUM_SCAN_Y_MIN; y <= PODIUM_SCAN_Y_MAX; y++) {
                    pos.m_122178_(x, y, z);
                    try {
                        if (isEndPortalBlock(end.m_8055_(pos))) {
                            byY.merge(y, 1, Integer::sum);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        int keepY = Integer.MIN_VALUE;
        int bestCount = -1;
        for (Map.Entry<Integer, Integer> e : byY.entrySet()) {
            if (e.getValue() > bestCount
                    || (e.getValue() == bestCount && e.getKey() > keepY)) {
                bestCount = e.getValue();
                keepY = e.getKey();
            }
        }
        if (keepY == Integer.MIN_VALUE) {
            // No portal left — mirror vanilla spawnExitPortal height walk.
            BlockPos origin = EndPodiumFeature.m_287210_(BlockPos.f_121853_); // ZERO
            BlockPos at = end.m_5452_(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin).m_7495_();
            while (at.m_123342_() > end.m_5736_()
                    && end.m_8055_(at).m_60713_(Blocks.f_50752_)) { // bedrock
                at = at.m_7495_();
            }
            return at;
        }
        return new BlockPos(0, keepY, 0);
    }

    /**
     * Wipe podium-shaped debris at every exit-portal Y except {@code keepY}.
     * Leaves the island end_stone alone; only clears fountain cylinders.
     */
    private static int clearNonCanonicalPodiums(ServerLevel end, int keepY) {
        Map<Integer, Integer> byY = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -PODIUM_SCAN_RADIUS; x <= PODIUM_SCAN_RADIUS; x++) {
            for (int z = -PODIUM_SCAN_RADIUS; z <= PODIUM_SCAN_RADIUS; z++) {
                for (int y = PODIUM_SCAN_Y_MIN; y <= PODIUM_SCAN_Y_MAX; y++) {
                    pos.m_122178_(x, y, z);
                    try {
                        if (isEndPortalBlock(end.m_8055_(pos))) {
                            byY.merge(y, 1, Integer::sum);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        int removed = 0;
        for (int ghostY : byY.keySet()) {
            if (ghostY == keepY) {
                continue;
            }
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    double distSq = (double) x * x + (double) z * z;
                    if (distSq > 3.6 * 3.6) {
                        continue;
                    }
                    for (int y = ghostY - 6; y <= ghostY + 10; y++) {
                        if (y < PODIUM_SCAN_Y_MIN || y > PODIUM_SCAN_Y_MAX || y == keepY) {
                            continue;
                        }
                        pos.m_122178_(x, y, z);
                        try {
                            BlockState state = end.m_8055_(pos);
                            if (!isPodiumFillBlock(state)) {
                                continue;
                            }
                            end.m_46597_(pos, Blocks.f_50016_.m_49966_());
                            removed++;
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }
        return removed;
    }

    private static void setFightPortalLocation(EndDragonFight fight, BlockPos pos) {
        if (fight == null || pos == null) {
            return;
        }
        try {
            java.lang.reflect.Field f = EndDragonFight.class.getDeclaredField("f_64072_");
            f.setAccessible(true);
            f.set(fight, pos.m_7949_()); // immutable copy
        } catch (Throwable ignored) {
        }
    }

    private static void invokeSpawnExitPortal(EndDragonFight fight, boolean active) {
        if (fight == null) {
            return;
        }
        try {
            java.lang.reflect.Method m = EndDragonFight.class.getDeclaredMethod("m_64093_", boolean.class);
            m.setAccessible(true);
            m.invoke(fight, active);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] spawnExitPortal reflect failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    private static void placeEndPodiumDirect(ServerLevel end, BlockPos pos, boolean active) {
        if (end == null || pos == null) {
            return;
        }
        try {
            EndPodiumFeature feature = new EndPodiumFeature(active);
            feature.m_225028_(
                    net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration.f_67737_,
                    end,
                    end.m_7726_().m_8481_(),
                    net.minecraft.util.RandomSource.m_216327_(),
                    pos);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] direct EndPodiumFeature place failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    private static List<EnderDragon> findDragons(ServerLevel end) {
        List<EnderDragon> out = new ArrayList<>();
        if (end == null) {
            return out;
        }
        try {
            for (Entity e : end.m_8583_()) {
                if (e instanceof EnderDragon dragon && dragon.m_6084_()) {
                    out.add(dragon);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static double dragonHealthScore(EnderDragon dragon) {
        if (dragon == null) {
            return -1.0;
        }
        try {
            return Math.max(0.0, dragon.m_21223_());
        } catch (Throwable ignored) {
            return -1.0;
        }
    }

    /**
     * Remove all End ki_laser / ki_blast via entity discard (silent).
     * Avoids console {@code kill @e[…]} which prints "No entity was found" every hygiene pulse
     * when The End is empty.
     */
    private static void purgeEndKiCommands(ServerLevel end) {
        if (end == null) {
            return;
        }
        List<Entity> ents = collectEndKiEntities(end);
        if (ents.isEmpty()) {
            return;
        }
        int killed = 0;
        for (Entity e : ents) {
            if (discardEntitySafe(e)) {
                killed++;
            }
        }
        if (killed > 0) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] Purged {} End ki projectiles", AdaptiveDifficultyMod.MOD_ID, killed);
        }
    }

    private static EntityType<?> resolveKiType(String path) {
        try {
            return ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("dragonminez", path));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<Entity> collectEndKiEntities(ServerLevel end) {
        List<Entity> out = new ArrayList<>();
        if (end == null) {
            return out;
        }
        EntityType<?> laser = resolveKiType("ki_laser");
        EntityType<?> blast = resolveKiType("ki_blast");
        try {
            if (laser != null) {
                collectTyped(end, laser, out);
            }
            if (blast != null) {
                collectTyped(end, blast, out);
            }
            if (!out.isEmpty() || (laser != null && blast != null)) {
                return out;
            }
        } catch (Throwable ignored) {
        }
        // Fallback: scan all End entities by registry id / name.
        try {
            for (Entity e : end.m_8583_()) {
                if (isKiProjectileEntity(e)) {
                    out.add(e);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void collectTyped(ServerLevel end, EntityType<?> type, List<Entity> out) {
        if (end == null || type == null || out == null) {
            return;
        }
        try {
            List list = end.m_142425_((EntityTypeTest) type, END_KI_SCAN_BOX, e -> e != null);
            if (list != null) {
                for (Object o : list) {
                    if (o instanceof Entity entity) {
                        out.add(entity);
                    }
                }
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            for (Entity e : end.m_8583_()) {
                if (e != null && e.m_6095_() == type) {
                    out.add(e);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isKiProjectileEntity(Entity ent) {
        if (ent == null) {
            return false;
        }
        try {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(ent.m_6095_());
            if (id != null) {
                String key = id.toString().toLowerCase(Locale.ROOT);
                if (KI_LASER_ID.equals(key) || KI_BLAST_ID.equals(key)
                        || key.contains("ki_laser") || key.contains("ki_blast")
                        || key.contains("kilaser") || key.contains("kiblast")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            String name = ent.m_6095_().toString().toLowerCase(Locale.ROOT);
            return name.contains("ki_laser") || name.contains("ki_blast")
                    || name.contains("kilaser") || name.contains("kiblast");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean discardEntitySafe(Entity ent) {
        if (ent == null) {
            return false;
        }
        try {
            ent.m_146870_(); // discard
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void maybeRescaleDragon(EnderDragon dragon, ServerLevel level, PlayerPower power, long now) {
        if (dragon == null || power == null) {
            return;
        }
        // Player GUI summons use {@link #retargetPlayerDragonToSummoner} only.
        if (isPlayerSummoned(dragon)) {
            return;
        }
        if (now - lastDragonRescaleAt < DRAGON_RESCALE_MS) {
            return;
        }
        double desired = score(power);
        // Script: only grow when a stronger End player arrives (epsilon gate).
        boolean stronger = desired > lastDragonScaleScore * (1.0 + DRAGON_SCALE_SCORE_EPSILON) + 1.0
                || lastDragonScaleScore < 0.0
                || !alreadyBuffed(dragon);
        if (!stronger) {
            return;
        }
        lastDragonRescaleAt = now;
        applyDragonStats(dragon, power, "rescale");
        lastDragonScaleScore = desired;
    }

    /**
     * Live Adaptive Difficulty retarget for a player-summoned dragon.
     * Tracks the summoner's form/stat signature only — never other players.
     */
    private static void retargetPlayerDragonToSummoner(EnderDragon dragon, ServerLevel end, long now) {
        if (dragon == null || end == null || !isPlayerSummoned(dragon)) {
            return;
        }
        ServerPlayer owner = resolveSummoner(end, dragon);
        if (owner == null || !owner.m_6084_() || !SystemGate.participates(owner)) {
            return;
        }
        long last = PersistentDataAccess.getLong(dragon, "end_dragon_retarget_at", 0L);
        if (now - last < PLAYER_DRAGON_RETARGET_MS) {
            return;
        }
        long sig = 0L;
        try {
            PlayerCombatProfile profile = PlayerCombatProfile.of(owner);
            if (profile != null) {
                sig = profile.signature;
            }
        } catch (Throwable ignored) {
        }
        long prev = PersistentDataAccess.getLong(dragon, NBT_AD_SIG, Long.MIN_VALUE);
        if (prev != Long.MIN_VALUE && prev == sig && alreadyBuffed(dragon) && readDef(dragon) > 0.0) {
            CompoundTag tag = PersistentDataAccess.get(dragon);
            if (PersistentDataAccess.isWritable(tag)) {
                tag.m_128356_("end_dragon_retarget_at", now);
            }
            return;
        }
        CompoundTag tag = PersistentDataAccess.get(dragon);
        if (PersistentDataAccess.isWritable(tag)) {
            tag.m_128356_("end_dragon_retarget_at", now);
        }
        applySummonerAdStats(dragon, owner, "summoner_ad");
    }

    /**
     * Apply the summoner's current Adaptive Difficulty boss profile to the dragon
     * (forms up/down). Uses {@link MobScaling#applyEndDragonAdProfile} — not the
     * legacy End Strength HP/DEF log curve.
     */
    private static void applySummonerAdStats(EnderDragon dragon, ServerPlayer summoner, String source) {
        if (dragon == null || summoner == null || !isPlayerSummoned(dragon)) {
            return;
        }
        if (!isSummoner(dragon, summoner)) {
            return;
        }
        double appliedHp = MobScaling.applyEndDragonAdProfile(dragon, summoner);
        if (!(appliedHp > 0.0)) {
            // Fallback only if AD paint failed — still better than leaving vanilla HP.
            PlayerPower power = adScaledPower(summoner);
            double hp = mapDmzHp(power.maxHp, DRAGON_BASE_HP, DRAGON_HP_CAP);
            setMaxHealth(dragon, hp, hp);
        }
        // Clear legacy End DEF so onHurt never re-applies the old sponge path.
        storeDef(dragon, 0.0);
        storeHits(dragon, 0);
        markBuffed(dragon);
        CompoundTag tag = PersistentDataAccess.get(dragon);
        if (PersistentDataAccess.isWritable(tag)) {
            // Ki uses painted AD attack (boss-scaled), not End Strength melee frac.
            double atk = 0.0;
            try {
                var inst = dragon.m_21051_(Attributes.f_22281_);
                if (inst != null) {
                    atk = Math.max(0.0, inst.m_22135_());
                }
            } catch (Throwable ignored) {
            }
            if (!(atk > 0.0)) {
                try {
                    PlayerCombatProfile profile = PlayerCombatProfile.of(summoner);
                    DifficultyConfig cfg = DifficultyConfig.get();
                    if (profile != null && profile.active() && cfg != null) {
                        atk = profile.targetMobDamage(cfg) * Math.max(1.0, cfg.bossStatMultiplier) * 1.25;
                    }
                } catch (Throwable ignored) {
                }
            }
            tag.m_128347_(NBT_KI_MELEE, Math.max(0.0, atk));
            try {
                PlayerCombatProfile profile = PlayerCombatProfile.of(summoner);
                if (profile != null) {
                    tag.m_128356_(NBT_AD_SIG, profile.signature);
                }
            } catch (Throwable ignored) {
            }
            int tier = DifficultyCache.data(summoner).getActiveTier();
            tag.m_128405_(NBT_AD_TIER, Math.max(0, tier));
            try {
                PlayerCombatProfile profile = PlayerCombatProfile.of(summoner);
                if (profile != null) {
                    storeDmzHp(dragon, profile.maxHealth);
                }
            } catch (Throwable ignored) {
            }
        }
        if (source != null && !source.isBlank() && !"summoner_ad".equals(source)) {
            // Quiet — retarget telemetry is enough via MobScaling stamps.
        }
    }

    private static void applyDragonStats(EnderDragon dragon, PlayerPower power, String source) {
        if (dragon == null || power == null) {
            return;
        }
        double hp = mapDmzHp(power.maxHp, DRAGON_BASE_HP, DRAGON_HP_CAP);
        double def = calcDragonDef(power);
        boolean midFight = alreadyBuffed(dragon) && ("rescale".equals(source) || "onhit".equals(source) || "strongest".equals(source));
        double prevMax = dragon.m_21233_();
        double prevHp = dragon.m_21223_();
        if (midFight && prevMax > 20 && prevHp > 0 && hp <= prevMax + 500) {
            // DEF / name only — leave attributes alone mid-fight.
            storeDef(dragon, def);
            storeHits(dragon, DRAGON_TARGET_HITS);
            markBuffed(dragon);
            return;
        }
        setMaxHealth(dragon, hp, midFight ? Math.max(1.0, hp * (prevHp / Math.max(1.0, prevMax))) : hp);
        storeDef(dragon, def);
        storeHits(dragon, DRAGON_TARGET_HITS);
        storeDmzHp(dragon, power.maxHp);
        markBuffed(dragon);
        try {
            dragon.m_6593_(Component.m_237113_(
                    "§5Ender Dragon §8[" + DmzRewards.formatWhole(hp) + " HP / DEF "
                            + DmzRewards.formatWhole(def) + "]"));
        } catch (Throwable ignored) {
        }
    }

    private static void buffMob(LivingEntity entity, String kind, PlayerPower power) {
        MobTier tier = MobTier.of(kind);
        if (tier == null || power == null) {
            return;
        }
        if (alreadyBuffed(entity) && readDef(entity) > 0.0) {
            return;
        }
        double hp = mapDmzHp(power.maxHp, tier.hp, tier.hpCap);
        double def = calcMobDef(tier, power);
        double dmg = "enderman".equals(kind)
                ? ENDERMAN_ATTACK
                : Math.floor(tier.damage * Math.min(4.0, 1.0 + power.level / 100.0));
        setMaxHealth(entity, hp, hp);
        storeDef(entity, def);
        storeHits(entity, tier.hits);
        storeDmzHp(entity, power.maxHp);
        setAttack(entity, dmg);
        markBuffed(entity);
        try {
            entity.m_6593_(Component.m_237113_(
                    "§d" + tier.label + " §8[" + DmzRewards.formatWhole(hp) + " HP / DEF "
                            + DmzRewards.formatWhole(def) + "]"));
        } catch (Throwable ignored) {
        }
    }

    private static double calcDragonDef(PlayerPower p) {
        double def = Math.max(
                DRAGON_DEF_BASE,
                Math.max(p.defense * DRAGON_DEF_FROM_PLAYER, p.melee * DRAGON_DEF_FROM_MELEE)
        );
        def += p.level * DRAGON_DEF_PER_LEVEL;
        def += p.bp * DRAGON_DEF_PER_BP;
        return Math.min(DRAGON_DEF_CAP, Math.floor(def));
    }

    private static double calcMobDef(MobTier tier, PlayerPower p) {
        double base = tier.defense;
        double tierFactor = Math.max(1, tier.tier);
        double def = Math.max(
                base,
                Math.max(
                        p.defense * END_MOB_DEF_FROM_PLAYER * (0.55 + tierFactor * 0.2),
                        p.melee * END_MOB_DEF_FROM_MELEE * (0.45 + tierFactor * 0.15)
                )
        );
        def += p.level * END_MOB_DEF_PER_LEVEL * tierFactor;
        double maxDef = base * END_MOB_DEF_SCALE_CAP
                + p.defense * END_MOB_DEF_FROM_PLAYER * tierFactor
                + p.melee * END_MOB_DEF_FROM_MELEE * tierFactor;
        if (def > maxDef) {
            def = maxDef;
        }
        return Math.floor(def);
    }

    private static double mapDmzHp(double playerDmzHp, double baseHp, double capHp) {
        baseHp = Math.max(1.0, baseHp);
        capHp = Math.max(baseHp, capHp);
        double src = Math.max(20.0, playerDmzHp);
        double t = Math.log(src / 20.0) / Math.log(DRAGON_HP_LOG_REF / 20.0);
        if (!(t >= 0)) {
            t = 0;
        }
        if (t > 1) {
            t = 1;
        }
        return Math.floor(baseHp + (capHp - baseHp) * t);
    }

    private static double mitigate(
            double raw, double defense, double minFrac, double absorb, double redCap, double defScale
    ) {
        double afterAbsorb = raw * (1.0 - DmzRewards.clamp(absorb, 0.0, 0.95));
        double reduction = defense / (defense + Math.max(1.0, raw * defScale));
        reduction = Math.min(redCap, Math.max(0.0, reduction));
        double out = afterAbsorb * (1.0 - reduction);
        double floor = raw * minFrac;
        return Math.max(out, floor);
    }

    private static float capForHits(LivingEntity entity, String kind, float dmg) {
        int hits = (int) PersistentDataAccess.getLong(entity, NBT_HITS,
                "dragon".equals(kind) ? DRAGON_TARGET_HITS : 14);
        hits = Math.max(4, hits);
        double max = entity.m_21233_();
        double cap = max / hits;
        if (dmg > cap) {
            return (float) cap;
        }
        return dmg;
    }

    private static void scheduleKillTp(ServerPlayer player, String kind, double maxHp) {
        PENDING_TP.put(player.m_20148_(), new PendingTp(kind, maxHp, TP_SETTLE_DELAY_TICKS));
    }

    private static void processKillTpSettle(ServerPlayer player) {
        PendingTp pending = PENDING_TP.get(player.m_20148_());
        if (pending == null) {
            return;
        }
        if (pending.delayTicks > 0) {
            pending.delayTicks--;
            return;
        }
        PENDING_TP.remove(player.m_20148_());
        double awarded = estimateDmzKillTp(player, pending.maxHp);
        double bonus = endKillBonus(pending.kind) * endBpMult(readPower(player).bp);
        double target = Math.min(softCap(readPower(player).level, pending.kind), awarded + bonus);
        double delta = target - awarded;
        if (Math.abs(delta) < 50.0) {
            return;
        }
        if (delta > 0) {
            DmzRewards.awardTp(player, (float) Math.floor(delta), "end-" + pending.kind, true, "§6[End] ");
        } else {
            adjustTp(player, delta);
            DmzRewards.msg(player, "§7[End] Kill TP capped (" + DmzRewards.formatWhole(target)
                    + " TP max for " + pending.kind + ").");
        }
    }

    private static double estimateDmzKillTp(ServerPlayer player, double maxHp) {
        double ratio = 0.25;
        double tpPerHit = 2.0;
        double base = tpPerHit + Math.round(Math.max(0.0, maxHp) * ratio);
        StatsData data = DmzProgression.stats(player);
        if (data == null) {
            return base;
        }
        try {
            return Math.max(0.0, Math.floor(data.calculateTPGain((int) Math.round(base))));
        } catch (Throwable ignored) {
            return base;
        }
    }

    private static double endKillBonus(String kind) {
        return switch (kind == null ? "" : kind) {
            case "dragon" -> 18_000;
            case "shulker" -> 6_000;
            case "enderman" -> 4_500;
            case "phantom" -> 3_000;
            case "endermite" -> 2_000;
            default -> 2_500;
        };
    }

    private static double endBpMult(double bp) {
        double raw = 1.0 + Math.log1p(Math.max(0.0, bp) / 100_000.0);
        double mult = Math.max(1.0, 1.0 + (raw - 1.0) * 0.06);
        return Math.min(2.5, mult);
    }

    private static double softCap(int level, String kind) {
        SoftCapRow[] table = softCapTable(kind);
        return Math.max(1000.0, interpolateSoftCap(table, level, 1_000_000.0));
    }

    private static SoftCapRow[] softCapTable(String kind) {
        return switch (kind == null ? "" : kind) {
            case "dragon" -> new SoftCapRow[]{
                    new SoftCapRow(1000, 280_000),
                    new SoftCapRow(2500, 450_000),
                    new SoftCapRow(4000, 650_000),
                    new SoftCapRow(7000, 950_000),
                    new SoftCapRow(10_000, 1_400_000),
                    new SoftCapRow(20_000, 2_200_000)
            };
            case "shulker" -> new SoftCapRow[]{
                    new SoftCapRow(1000, 90_000),
                    new SoftCapRow(2500, 160_000),
                    new SoftCapRow(4000, 260_000),
                    new SoftCapRow(7000, 400_000),
                    new SoftCapRow(10_000, 560_000),
                    new SoftCapRow(20_000, 850_000)
            };
            case "phantom" -> new SoftCapRow[]{
                    new SoftCapRow(1000, 50_000),
                    new SoftCapRow(2500, 95_000),
                    new SoftCapRow(4000, 150_000),
                    new SoftCapRow(7000, 240_000),
                    new SoftCapRow(10_000, 340_000),
                    new SoftCapRow(20_000, 520_000)
            };
            case "endermite" -> new SoftCapRow[]{
                    new SoftCapRow(1000, 35_000),
                    new SoftCapRow(2500, 65_000),
                    new SoftCapRow(4000, 100_000),
                    new SoftCapRow(7000, 160_000),
                    new SoftCapRow(10_000, 230_000),
                    new SoftCapRow(20_000, 360_000)
            };
            default -> new SoftCapRow[]{ // enderman
                    new SoftCapRow(1000, 70_000),
                    new SoftCapRow(2500, 130_000),
                    new SoftCapRow(4000, 200_000),
                    new SoftCapRow(7000, 320_000),
                    new SoftCapRow(10_000, 450_000),
                    new SoftCapRow(20_000, 700_000)
            };
        };
    }

    private static double interpolateSoftCap(SoftCapRow[] table, int level, double fallback) {
        if (table == null || table.length == 0) {
            return fallback;
        }
        if (level <= table[0].level) {
            return table[0].cap;
        }
        SoftCapRow last = table[table.length - 1];
        if (level >= last.level) {
            return last.cap;
        }
        for (int i = 0; i < table.length - 1; i++) {
            SoftCapRow a = table[i];
            SoftCapRow b = table[i + 1];
            if (level >= a.level && level <= b.level) {
                double t = (level - a.level) / (double) Math.max(1, b.level - a.level);
                return Math.floor(a.cap + (b.cap - a.cap) * t);
            }
        }
        return last.cap;
    }

    private record SoftCapRow(int level, double cap) {}

    private static void adjustTp(ServerPlayer player, double delta) {
        StatsData data = DmzProgression.stats(player);
        if (data == null) {
            return;
        }
        try {
            Resources resources = data.getResources();
            if (resources == null) {
                return;
            }
            if (delta > 0) {
                DmzRewards.awardTp(player, (float) Math.floor(delta), "end-adjust", false, "");
                return;
            }
            try {
                resources.removeTrainingPoints((float) Math.floor(-delta));
            } catch (Throwable t) {
                // DMZ 2.2 keeps TP as a double; the float accessors would round a large balance.
                double cur = resources.getTrainingPointsExact();
                resources.setTrainingPointsExact(Math.max(0.0d, cur + delta));
            }
            try {
                com.dragonminez.common.network.NetworkHandler.sendToTrackingEntityAndSelf(
                        new com.dragonminez.common.network.S2C.StatsSyncS2C(player), player);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    private static PlayerPower strongestInEnd(ServerLevel level, ServerPlayer fallback) {
        PlayerPower best = fallback == null ? null : readPower(fallback);
        double bestScore = best == null ? -1.0 : score(best);
        if (level == null) {
            return best != null ? best : new PlayerPower();
        }
        for (ServerPlayer p : level.m_6907_()) {
            if (p == null || !isTheEnd(p.m_9236_())) {
                continue;
            }
            PlayerPower power = readPower(p);
            double s = score(power);
            if (s > bestScore) {
                bestScore = s;
                best = power;
            }
        }
        if (best == null) {
            return new PlayerPower();
        }
        return best;
    }

    private static PlayerPower nearbyPower(LivingEntity entity, ServerLevel level, PlayerPower fallback) {
        PlayerPower best = fallback;
        double bestScore = score(best);
        AABB box = entity.m_20191_().m_82400_(96.0);
        for (ServerPlayer p : level.m_45976_(ServerPlayer.class, box)) {
            PlayerPower power = readPower(p);
            double s = power.level * 1000.0 + power.defense + power.bp * 0.01 + power.melee * 10.0 + power.maxHp;
            if (s > bestScore) {
                bestScore = s;
                best = power;
            }
        }
        return best;
    }

    private static PlayerPower readPower(ServerPlayer player) {
        PlayerPower out = new PlayerPower();
        if (player == null) {
            return out;
        }
        out.name = player.m_6302_();
        StatsData data = DmzProgression.stats(player);
        if (data == null) {
            return out;
        }
        try {
            out.level = Math.max(1, data.getLevel());
        } catch (Throwable ignored) {
        }
        try {
            out.bp = Math.max(0.0, CombatRating.safeBattlePower(player));
        } catch (Throwable ignored) {
        }
        try {
            out.melee = LmOverhaulScaledCombat.melee(data);
        } catch (Throwable ignored) {
        }
        try {
            out.maxHp = LmOverhaulScaledCombat.health(data);
        } catch (Throwable ignored) {
        }
        try {
            out.defense = LmOverhaulScaledCombat.defense(data);
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static double score(PlayerPower p) {
        if (p == null) {
            return -1;
        }
        return p.melee * 100.0 + p.bp + p.level * 1000.0 + p.maxHp + p.defense;
    }

    private static String classify(LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof EnderDragon) {
            return "dragon";
        }
        if (entity instanceof EnderMan) {
            return "enderman";
        }
        if (entity instanceof Endermite) {
            return "endermite";
        }
        if (entity instanceof Shulker) {
            return "shulker";
        }
        if (entity instanceof Phantom) {
            return "phantom";
        }
        try {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.m_6095_());
            if (id != null) {
                String path = id.m_135815_();
                if ("enderman".equals(path)) {
                    return "enderman";
                }
                if ("endermite".equals(path)) {
                    return "endermite";
                }
                if ("shulker".equals(path)) {
                    return "shulker";
                }
                if ("phantom".equals(path)) {
                    return "phantom";
                }
                if ("ender_dragon".equals(path)) {
                    return "dragon";
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void setMaxHealth(LivingEntity entity, double max, double current) {
        try {
            AttributeInstance attr = entity.m_21051_(Attributes.f_22276_); // MAX_HEALTH
            if (attr != null) {
                attr.m_22100_(max); // setBaseValue
            }
            entity.m_21153_((float) Math.min(max, Math.max(1.0, current)));
            CompoundTag tag = PersistentDataAccess.get(entity);
            if (PersistentDataAccess.isWritable(tag)) {
                tag.m_128347_(NBT_MAX, max);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setAttack(LivingEntity entity, double dmg) {
        try {
            AttributeInstance attr = entity.m_21051_(Attributes.f_22281_);
            if (attr != null) {
                attr.m_22100_(Math.max(1.0, dmg));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void storeDef(LivingEntity entity, double def) {
        CompoundTag tag = PersistentDataAccess.get(entity);
        if (PersistentDataAccess.isWritable(tag)) {
            tag.m_128347_(NBT_DEF, def);
        }
    }

    private static double readDef(LivingEntity entity) {
        CompoundTag tag = PersistentDataAccess.get(entity);
        if (!PersistentDataAccess.isWritable(tag) || !tag.m_128441_(NBT_DEF)) {
            return 0.0;
        }
        try {
            return Math.max(0.0, tag.m_128459_(NBT_DEF));
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static void storeHits(LivingEntity entity, int hits) {
        CompoundTag tag = PersistentDataAccess.get(entity);
        if (PersistentDataAccess.isWritable(tag)) {
            tag.m_128356_(NBT_HITS, hits);
        }
    }

    private static void storeDmzHp(LivingEntity entity, double hp) {
        CompoundTag tag = PersistentDataAccess.get(entity);
        if (PersistentDataAccess.isWritable(tag)) {
            tag.m_128347_(NBT_DMZ_HP, hp);
        }
    }

    private static void markBuffed(LivingEntity entity) {
        CompoundTag tag = PersistentDataAccess.get(entity);
        if (PersistentDataAccess.isWritable(tag)) {
            tag.m_128379_(TAG_BUFFED, true);
        }
    }

    private static boolean alreadyBuffed(LivingEntity entity) {
        return PersistentDataAccess.flag(entity, TAG_BUFFED);
    }

    private static void stampPlayerSummon(
            EnderDragon dragon, ServerPlayer summoner, int tier, PlayerPower power
    ) {
        if (dragon == null || summoner == null) {
            return;
        }
        CompoundTag tag = PersistentDataAccess.get(dragon);
        if (!PersistentDataAccess.isWritable(tag)) {
            return;
        }
        tag.m_128359_(NBT_SUMMONER, summoner.m_20148_().toString());
        tag.m_128379_(NBT_PLAYER_SUMMON, true);
        tag.m_128405_(NBT_AD_TIER, Math.max(0, tier));
        com.dbzlegacy.adaptivedifficulty.progression.boss.SummonLootBonus.stampTier(dragon, tier);
        if (power != null) {
            tag.m_128347_(NBT_KI_MELEE, Math.max(0.0, power.melee));
        }
        try {
            PlayerCombatProfile profile = PlayerCombatProfile.of(summoner);
            if (profile != null) {
                tag.m_128356_(NBT_AD_SIG, profile.signature);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isPlayerSummoned(EnderDragon dragon) {
        return dragon != null && PersistentDataAccess.flag(dragon, NBT_PLAYER_SUMMON);
    }

    private static void stampStaffSpawn(EnderDragon dragon) {
        if (dragon == null) {
            return;
        }
        CompoundTag tag = PersistentDataAccess.get(dragon);
        if (PersistentDataAccess.isWritable(tag)) {
            tag.m_128379_(NBT_STAFF_SPAWN, true);
        }
    }

    /** Vanilla/natural dragon with no GUI summoner and no staff stamp. */
    private static boolean isUnauthorizedNaturalDragon(EnderDragon dragon) {
        if (dragon == null || DifficultyConfig.get().enableEndNaturalDragonSpawn) {
            return false;
        }
        if (isPlayerSummoned(dragon) || PersistentDataAccess.flag(dragon, NBT_STAFF_SPAWN)) {
            return false;
        }
        return true;
    }

    private static boolean isSummoner(EnderDragon dragon, ServerPlayer player) {
        if (dragon == null || player == null) {
            return false;
        }
        String id = PersistentDataAccess.getString(dragon, NBT_SUMMONER);
        if (id == null || id.isBlank()) {
            return false;
        }
        return id.equalsIgnoreCase(player.m_20148_().toString());
    }

    private static ServerPlayer resolveSummoner(ServerLevel end, EnderDragon dragon) {
        if (end == null || dragon == null) {
            return null;
        }
        String id = PersistentDataAccess.getString(dragon, NBT_SUMMONER);
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(id);
            MinecraftServer server = end.m_7654_();
            if (server == null) {
                return null;
            }
            return server.m_6846_().m_11259_(uuid); // getPlayer(UUID)
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ServerPlayer resolvePlayerAttacker(LivingHurtEvent event) {
        if (event == null || event.getSource() == null) {
            return null;
        }
        Entity src = event.getSource().m_7639_(); // getEntity
        if (src instanceof ServerPlayer sp) {
            return sp;
        }
        if (src instanceof Projectile proj) {
            Entity owner = projectileOwner(proj);
            if (owner instanceof ServerPlayer sp) {
                return sp;
            }
        }
        return null;
    }

    private static Entity projectileOwner(Projectile proj) {
        if (proj == null) {
            return null;
        }
        try {
            return proj.m_19749_(); // getOwner
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Method m = proj.getClass().getMethod("getOwner");
            Object o = m.invoke(proj);
            return o instanceof Entity e ? e : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Adaptive Difficulty–scaled power for dragon sizing (player stats × active tier %).
     */
    private static PlayerPower adScaledPower(ServerPlayer player) {
        PlayerPower raw = readPower(player);
        if (player == null) {
            return raw;
        }
        try {
            PlayerCombatProfile profile = PlayerCombatProfile.of(player);
            if (profile == null || profile.activeTier <= 0 || !(profile.tierPercent > 0.0)) {
                return raw;
            }
            double pct = profile.tierPercent;
            // Prefer profile combat numbers (already soft-curved); fall back to raw × pct.
            if (profile.maxHealth > 20.0) {
                raw.maxHp = profile.maxHealth;
            } else {
                raw.maxHp = Math.max(20.0, raw.maxHp * pct);
            }
            if (profile.meleeDamage > 0.0) {
                raw.melee = profile.meleeDamage;
            } else {
                raw.melee = Math.max(0.0, raw.melee * pct);
            }
            if (profile.defense > 0.0) {
                raw.defense = profile.defense;
            } else {
                raw.defense = Math.max(0.0, raw.defense * pct);
            }
            raw.bp = Math.max(0.0, raw.bp * pct);
        } catch (Throwable ignored) {
        }
        return raw;
    }

    /** Prefer the summoner for ki attacks when present; never target bystanders on player fights. */
    private static ServerPlayer preferredDragonTarget(ServerLevel end, EnderDragon dragon, double range) {
        if (dragon != null && isPlayerSummoned(dragon)) {
            ServerPlayer owner = resolveSummoner(end, dragon);
            if (owner != null && owner.m_6084_() && isTheEnd(owner.m_9236_())) {
                double d = owner.m_20275_(dragon.m_20185_(), dragon.m_20186_(), dragon.m_20189_());
                if (d <= range * range) {
                    return owner;
                }
            }
            // Player-summoned dragons do not farm nearby non-summoners.
            return null;
        }
        return nearestEndPlayer(end, dragon, range);
    }

    private static void msg(ServerPlayer player, String text) {
        DmzRewards.msg(player, text);
    }

    private static final class PlayerPower {
        int level = 1;
        double bp;
        double melee;
        double maxHp = 20;
        double defense;
        String name = "?";
    }

    private static final class PendingTp {
        final String kind;
        final double maxHp;
        int delayTicks;

        PendingTp(String kind, double maxHp, int delayTicks) {
            this.kind = kind;
            this.maxHp = maxHp;
            this.delayTicks = delayTicks;
        }
    }

    private enum MobTier {
        ENDERMITE("endermite", 1, 1200, 40, 5000, 10, "Endermite", 2200),
        PHANTOM("phantom", 2, 1800, 70, 9000, 14, "Phantom", 3200),
        ENDERMAN("enderman", 3, 2400, 12, 14000, 18, "Enderman", 4200),
        SHULKER("shulker", 4, 3200, 80, 18000, 22, "Shulker", 5500);

        final String id;
        final int tier;
        final double hp;
        final double damage;
        final double defense;
        final int hits;
        final String label;
        final double hpCap;

        MobTier(String id, int tier, double hp, double damage, double defense, int hits, String label, double hpCap) {
            this.id = id;
            this.tier = tier;
            this.hp = hp;
            this.damage = damage;
            this.defense = defense;
            this.hits = hits;
            this.label = label;
            this.hpCap = hpCap;
        }

        static MobTier of(String kind) {
            if (kind == null) {
                return null;
            }
            for (MobTier t : values()) {
                if (t.id.equals(kind)) {
                    return t;
                }
            }
            return null;
        }
    }
}
