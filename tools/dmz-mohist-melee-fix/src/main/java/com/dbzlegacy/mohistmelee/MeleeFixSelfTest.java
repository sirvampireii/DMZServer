package com.dbzlegacy.mohistmelee;

import com.dragonminez.common.combat.logic.player.PlayerAttackHelper;
import com.dragonminez.common.init.MainAttributes;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Boot probe: ENTITY_REACH repair + prove we do not wipe DMZ damage attributes.
 */
public final class MeleeFixSelfTest {
    private static final Logger LOGGER = LogManager.getLogger(DmzMohistMeleeFix.MOD_ID);
    private static final UUID PROBE_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private MeleeFixSelfTest() {}

    public static void registerIfEnabled() {
        boolean mohist = isMohist();
        boolean forced = Boolean.getBoolean("dmz.melee.fix.selftest");
        if (!mohist && !forced) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(new MeleeFixSelfTest());
        LOGGER.info("[{}] Self-test enabled (mohist={} forced={})", DmzMohistMeleeFix.MOD_ID, mohist, forced);
    }

    private static boolean isMohist() {
        try {
            Class.forName("com.mohistmc.MohistMC");
            return true;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("org.bukkit.Bukkit");
                return true;
            } catch (ClassNotFoundException e2) {
                return false;
            }
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        server.execute(() -> run(server));
    }

    private static void run(MinecraftServer server) {
        try {
            ServerLevel level = server.m_129783_();
            FakePlayer fake = FakePlayerFactory.get(level, new GameProfile(PROBE_UUID, "ReachFixProbe"));

            // --- ENTITY_REACH ---
            double reachBefore = ReachAttributeFix.readEntityReach(fake);
            Attribute reachAttr = ForgeMod.ENTITY_REACH.get();
            AttributeInstance reachInst = reachAttr == null ? null : fake.m_21051_(reachAttr);
            if (reachInst == null) {
                LOGGER.error("[{}] SELFTEST FAIL no ENTITY_REACH instance", DmzMohistMeleeFix.MOD_ID);
                return;
            }
            reachInst.m_22100_(Double.NaN);
            double sanitized = PlayerAttackHelper.getEffectiveAttackRange((Player) fake, 2.0D);
            double reachAfter = ReachAttributeFix.readEntityReach(fake);
            boolean reachOk = Double.isFinite(reachAfter) && reachAfter >= 0.25D
                    && Double.isFinite(sanitized) && sanitized > 0.05D;

            // --- Prove repair does NOT wipe dragonminez:ki_damage ---
            Attribute kiAttr = MainAttributes.KI_DAMAGE.get();
            AttributeInstance kiInst = kiAttr == null ? null : fake.m_21051_(kiAttr);
            if (kiAttr != null && kiInst == null) {
                kiInst = PrimaryStatRepair.injectAttribute(fake, kiAttr, 42.0D);
            }
            boolean kiPreserved = true;
            double kiAfterRepair = Double.NaN;
            if (kiInst != null) {
                kiInst.m_22100_(42.0D);
                ReachAttributeFix.repair(fake, "selftest-preserve-ki");
                PrimaryStatRepair.ensure(fake, "selftest-preserve-ki");
                kiAfterRepair = kiInst.m_22135_();
                kiPreserved = Math.abs(kiAfterRepair - 42.0D) < 1.0E-6D;
            }

            // --- Primary STR wipe after "cross-dim" must restore (empty-hand path) ---
            boolean strOk = true;
            boolean strReadOk = true;
            int strAfter = -1;
            int strRead = -1;
            Attribute strAttr = MainAttributes.STRENGTH.get();
            AttributeInstance strInst = strAttr == null ? null : fake.m_21051_(strAttr);
            if (strAttr != null && strInst == null) {
                strInst = PrimaryStatRepair.injectAttribute(fake, strAttr, 250.0D);
            }
            if (strInst != null) {
                strInst.m_22100_(250.0D);
                PrimaryStatRepair.snapshot(fake);
                strInst.m_22100_(0.0D); // simulate Mohist dim-change wipe
                // Read-side fallback via Stats.getStrength() before explicit restore
                strRead = StatsProvider.get(StatsCapability.INSTANCE, (Entity) fake)
                        .map(d -> d.getStats().getStrength())
                        .orElse(-1);
                strReadOk = strRead == 250;
                PrimaryStatRepair.restore(fake, "selftest-str");
                strAfter = (int) Math.round(strInst.m_22115_());
                strOk = strAfter == 250;
            }

            // --- Respawn-like recovery (what death does) after STR wipe ---
            boolean respawnLikeOk = true;
            int strAfterRespawnLike = -1;
            if (strInst != null) {
                strInst.m_22100_(0.0D);
                RespawnLikeRecovery.apply(fake, "selftest-respawn-like");
                strAfterRespawnLike = (int) Math.round(strInst.m_22115_());
                respawnLikeOk = strAfterRespawnLike == 250;
            }

            // --- Fist range must not shrink below weapon attack_range ---
            double fistSanitized = ReachAttributeFix.sanitizeEffectiveRange(fake, 2.0D, 1.0D);
            boolean fistRangeOk = Double.isFinite(fistSanitized) && fistSanitized >= 2.0D;

            // --- Read-side NaN guard on StatsData ---
            boolean secondaryOk = true;
            double secondaryRead = Double.NaN;
            if (kiInst != null) {
                kiInst.m_22100_(Double.NaN);
                secondaryRead = readSecondaryViaStats(fake, kiAttr, 0.0D);
                secondaryOk = Double.isFinite(secondaryRead) && Math.abs(secondaryRead - 0.0D) < 1.0E-9D;
                // restore a sane value so we don't leave the probe poisoned
                kiInst.m_22100_(42.0D);
            }

            // --- strike lock gate ---
            boolean lockedBefore = false;
            boolean lockedAfter = false;
            try {
                StatsProvider.get(StatsCapability.INSTANCE, (Entity) fake).ifPresent(data -> {
                    data.getStatus().setStrikeLocked(true);
                    data.getStatus().setKnockedDown(false);
                    data.getStatus().setStunEffect(false);
                });
                lockedBefore = StatsProvider.get(StatsCapability.INSTANCE, (Entity) fake)
                        .map(d -> d.getStatus().isStunned())
                        .orElse(false);
                CombatUnlock.clearStaleStrikeLock(fake, "selftest");
                lockedAfter = StatsProvider.get(StatsCapability.INSTANCE, (Entity) fake)
                        .map(d -> d.getStatus().isStunned())
                        .orElse(true);
            } catch (Throwable ignored) {
            }

            boolean pass = reachOk && kiPreserved && secondaryOk && strOk && strReadOk
                    && respawnLikeOk && fistRangeOk
                    && (!lockedBefore || !lockedAfter);
            MeleeDebug.result(pass, 
                    "[{}] SELFTEST {} reachBefore={} reachAfter={} sanitizedRange={} fistSanitized={} kiPreserved={} kiAfterRepair={} strRead={} strAfter={} respawnLikeStr={} secondaryRead={} lockedBefore={} lockedAfter={}",
                    DmzMohistMeleeFix.MOD_ID,
                    pass ? "PASS" : "FAIL",
                    reachBefore,
                    reachAfter,
                    sanitized,
                    fistSanitized,
                    kiPreserved,
                    kiAfterRepair,
                    strRead,
                    strAfter,
                    strAfterRespawnLike,
                    secondaryRead,
                    lockedBefore,
                    lockedAfter
            );

            runShuruiFullHealProbe(fake);
            runEmptyIdFallbackProbe(level, fake);
            runStatResetProbe(fake);
        } catch (Throwable t) {
            LOGGER.error("[{}] SELFTEST FAIL {}", DmzMohistMeleeFix.MOD_ID, t.toString(), t);
        }
    }

    /**
     * Prove intentional primary zeros (stat reset) are not resurrected by snapshot restore.
     */
    private static void runStatResetProbe(FakePlayer fake) {
        try {
            Attribute strAttr = MainAttributes.STRENGTH.get();
            AttributeInstance strInst = strAttr == null ? null : fake.m_21051_(strAttr);
            if (strAttr != null && strInst == null) {
                strInst = PrimaryStatRepair.injectAttribute(fake, strAttr, 250.0D);
            }
            if (strInst == null) {
                MeleeDebug.log("[{}] RESET SELFTEST SKIP (no STR attr)", DmzMohistMeleeFix.MOD_ID);
                return;
            }
            strInst.m_22100_(250.0D);
            PrimaryStatRepair.snapshot(fake);
            // Simulate DMZ setStrength(0) path: write attr + record
            strInst.m_22100_(0.0D);
            PrimaryStatRepair.record(fake, strAttr, 0);
            PrimaryStatRepair.suppressRestore(fake, 5);
            PrimaryStatRepair.adoptCurrent(fake);

            boolean restoredWhileSuppressed =
                    PrimaryStatRepair.ensure(fake, "selftest-reset-should-not-restore");
            // Expire suppress window, then confirm snapshot stays at 0 and ensure still no-ops.
            for (int i = 0; i < 8; i++) {
                PrimaryStatRepair.tickSuppress(fake);
            }
            boolean restoredAfter = PrimaryStatRepair.ensure(fake, "selftest-reset-after-suppress");
            int saved = PrimaryStatRepair.savedFor(fake, strAttr);
            double live = strInst.m_22115_();
            int read = StatsProvider.get(StatsCapability.INSTANCE, (Entity) fake)
                    .map(d -> d.getStats().getStrength())
                    .orElse(-1);

            boolean pass = !restoredWhileSuppressed && !restoredAfter
                    && saved <= 0 && live <= 0.0D && read <= 0;
            MeleeDebug.result(pass, 
                    "[{}] RESET SELFTEST {} restoredWhile={} restoredAfter={} saved={} live={} read={}",
                    DmzMohistMeleeFix.MOD_ID,
                    pass ? "PASS" : "FAIL",
                    restoredWhileSuppressed,
                    restoredAfter,
                    saved,
                    live,
                    read
            );
        } catch (Throwable t) {
            LOGGER.error("[{}] RESET SELFTEST FAIL {}", DmzMohistMeleeFix.MOD_ID, t.toString(), t);
        }
    }

    /**
     * Live repro of post-dim empty/stale entity-ID packets: spawn a zombie in front of the probe,
     * call {@link ServerMeleeFallback#maybeRescue} with no IDs, assert the mob took damage.
     */
    private static void runEmptyIdFallbackProbe(ServerLevel level, FakePlayer fake) {
        net.minecraft.world.entity.monster.Zombie zombie = null;
        net.minecraft.world.entity.monster.Zombie far = null;
        try {
            net.minecraft.core.BlockPos spawn = level.m_220360_();
            double x = spawn.m_123341_() + 0.5D;
            double y = Math.max(spawn.m_123342_(), 64) + 1.0D;
            double z = spawn.m_123343_() + 0.5D;
            // Force spawn chunks loaded so AABB entity queries work.
            level.m_46745_(spawn);
            level.m_46745_(spawn.m_7918_(0, 0, 2));
            level.m_46745_(spawn.m_7918_(0, 0, 8));

            fake.m_146884_(new net.minecraft.world.phys.Vec3(x, y, z));
            fake.m_146922_(0.0F); // yaw facing south (+Z)
            fake.m_146926_(0.0F);
            fake.m_21008_(
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    net.minecraft.world.item.ItemStack.f_41583_
            );

            Attribute strAttr = MainAttributes.STRENGTH.get();
            AttributeInstance strInst = strAttr == null ? null : fake.m_21051_(strAttr);
            if (strAttr != null && strInst == null) {
                strInst = PrimaryStatRepair.injectAttribute(fake, strAttr, 250.0D);
            }
            if (strInst != null) {
                strInst.m_22100_(250.0D);
                PrimaryStatRepair.snapshot(fake);
            }
            ReachAttributeFix.repair(fake, "selftest-fallback");

            zombie = new net.minecraft.world.entity.monster.Zombie(level);
            zombie.m_146884_(new net.minecraft.world.phys.Vec3(x, y, z + 1.5D));
            zombie.m_21153_(zombie.m_21233_());
            boolean addedNear = level.m_7967_(zombie);
            int nearId = zombie.m_19879_();
            final net.minecraft.world.entity.monster.Zombie nearRef = zombie;

            java.util.List<net.minecraft.world.entity.LivingEntity> preScan =
                    level.m_45976_(
                            net.minecraft.world.entity.LivingEntity.class,
                            fake.m_20191_().m_82400_(5.0D)
                    );
            boolean inScan = false;
            for (net.minecraft.world.entity.LivingEntity e : preScan) {
                if (e == nearRef) {
                    inScan = true;
                    break;
                }
            }
            boolean canAtk = com.dragonminez.common.combat.logic.player.TargetHelper.canAttack(
                    (Player) fake, (Entity) zombie, 6.0D
            );
            var relation = com.dragonminez.common.combat.logic.player.TargetHelper.getRelation(
                    (Player) fake, (Entity) zombie
            );
            MeleeDebug.log(
                    "[{}] FALLBACK SELFTEST setup addedNear={} id={} inScan={} scanSize={} canAttack={} relation={} pos={}/{}/{}",
                    DmzMohistMeleeFix.MOD_ID,
                    addedNear,
                    nearId,
                    inScan,
                    preScan.size(),
                    canAtk,
                    relation,
                    x,
                    y,
                    z
            );
            if (!addedNear || !inScan) {
                LOGGER.error(
                        "[{}] FALLBACK SELFTEST FAIL could not place/query zombie in world",
                        DmzMohistMeleeFix.MOD_ID
                );
                return;
            }

            float healthBefore = zombie.m_21223_();
            long hitBefore = PersistentDataAccess.get(fake).m_128454_(
                    com.dragonminez.server.events.players.combat.CombatEvent.DMZ_LAST_HIT_TARGET_TIME_TAG
            );

            com.dragonminez.common.network.C2S.CombatAttackRequestC2S empty =
                    new com.dragonminez.common.network.C2S.CombatAttackRequestC2S(0, false, 0, new int[0]);
            ServerMeleeFallback.maybeRescue(fake, empty, hitBefore, 0);

            float healthAfter = zombie.m_21223_();
            long hitAfter = PersistentDataAccess.get(fake).m_128454_(
                    com.dragonminez.server.events.players.combat.CombatEvent.DMZ_LAST_HIT_TARGET_TIME_TAG
            );
            boolean emptyOk = healthAfter < healthBefore || zombie.f_20916_ > 0 || hitAfter > hitBefore;

            // Stale far ID must not block nearby rescue: heal zombie, clear i-frames,
            // put a far-but-resolved mob id in the packet.
            zombie.m_21153_(zombie.m_21233_());
            zombie.f_20916_ = 0; // hurtTime
            zombie.f_19802_ = 0; // invulnerableTime
            float health2Before = zombie.m_21223_();
            long hit2Before = PersistentDataAccess.get(fake).m_128454_(
                    com.dragonminez.server.events.players.combat.CombatEvent.DMZ_LAST_HIT_TARGET_TIME_TAG
            );

            far = new net.minecraft.world.entity.monster.Zombie(level);
            far.m_146884_(new net.minecraft.world.phys.Vec3(x, y, z + 8.0D));
            far.m_21153_(far.m_21233_());
            boolean addedFar = level.m_7967_(far);
            int farId = far.m_19879_();
            Entity resolvedFar = level.m_143317_(farId);

            com.dragonminez.common.network.C2S.CombatAttackRequestC2S stale =
                    new com.dragonminez.common.network.C2S.CombatAttackRequestC2S(
                            0, false, 0, new int[]{farId}
                    );
            ServerMeleeFallback.maybeRescue(fake, stale, hit2Before, 1);

            float health2After = zombie.m_21223_();
            boolean staleOk = health2After < health2Before || zombie.f_20916_ > 0;

            boolean pass = emptyOk && staleOk;
            MeleeDebug.result(pass, 
                    "[{}] FALLBACK SELFTEST {} emptyOk={} staleOk={} emptyHealth={}->{} staleHealth={}->{} addedFar={} farResolved={}",
                    DmzMohistMeleeFix.MOD_ID,
                    pass ? "PASS" : "FAIL",
                    emptyOk,
                    staleOk,
                    healthBefore,
                    healthAfter,
                    health2Before,
                    health2After,
                    addedFar,
                    resolvedFar != null
            );
        } catch (Throwable t) {
            LOGGER.error("[{}] FALLBACK SELFTEST FAIL {}", DmzMohistMeleeFix.MOD_ID, t.toString(), t);
        } finally {
            if (zombie != null) {
                try {
                    zombie.m_146870_();
                } catch (Throwable ignored) {
                }
            }
            if (far != null) {
                try {
                    far.m_146870_();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static double readSecondaryViaStats(FakePlayer fake, Attribute attr, double fallback) {
        try {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, (Entity) fake).orElse(null);
            if (data == null) {
                return Double.NaN;
            }
            Method m = StatsData.class.getDeclaredMethod(
                    "getSecondaryAttributeValue",
                    Attribute.class,
                    double.class
            );
            m.setAccessible(true);
            Object out = m.invoke(data, attr, fallback);
            return out instanceof Double d ? d : Double.NaN;
        } catch (Throwable t) {
            LOGGER.warn("[{}] secondary read probe failed: {}", DmzMohistMeleeFix.MOD_ID, t.toString());
            return Double.NaN;
        }
    }

    /** Verify Shurui {@code DmzHooks.fullHeal} mixin clears strikeLocked. */
    private static void runShuruiFullHealProbe(FakePlayer fake) {
        try {
            Class<?> hooks = Class.forName("net.shurui.dev.shuruis_raid_bosses.dmz.DmzHooks");
            StatsProvider.get(StatsCapability.INSTANCE, (Entity) fake).ifPresent(data -> {
                data.getStatus().setStrikeLocked(true);
                data.getStatus().setKnockedDown(true);
                data.getStatus().setStunEffect(true);
            });
            boolean lockedBefore = StatsProvider.get(StatsCapability.INSTANCE, (Entity) fake)
                    .map(d -> d.getStatus().isStrikeLocked())
                    .orElse(false);
            hooks.getMethod("fullHeal", Player.class).invoke(null, fake);
            boolean lockedAfter = StatsProvider.get(StatsCapability.INSTANCE, (Entity) fake)
                    .map(d -> d.getStatus().isStrikeLocked())
                    .orElse(true);
            boolean pass = lockedBefore && !lockedAfter;
            MeleeDebug.result(pass, 
                    "[{}] RAIDHEAL SELFTEST {} lockedBefore={} lockedAfter={}",
                    DmzMohistMeleeFix.MOD_ID,
                    pass ? "PASS" : "FAIL",
                    lockedBefore,
                    lockedAfter
            );
        } catch (ClassNotFoundException e) {
            MeleeDebug.log("[{}] RAIDHEAL SELFTEST SKIP (shuruis_raid_bosses not present)", DmzMohistMeleeFix.MOD_ID);
        } catch (Throwable t) {
            LOGGER.error("[{}] RAIDHEAL SELFTEST FAIL {}", DmzMohistMeleeFix.MOD_ID, t.toString(), t);
        }
    }

}
