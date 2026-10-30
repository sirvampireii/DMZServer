package com.dbzlegacy.mohistmelee;

import com.dragonminez.common.init.MainAttributes;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ResourceSyncS2C;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Stats;
import com.dragonminez.server.events.players.StatsEvents;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Death fixes melee because respawn creates a <b>new</b> player with a fresh AttributeMap,
 * then {@code PlayerEvent.Clone} → {@code Stats.copyFrom} rewrites STR/etc. onto it, then
 * DMZ runs {@code applyHealthBonus} + {@code refreshDimensions} ({@code m_6210_}).
 * <p>
 * Dimension changes keep the same entity, so Mohist-wiped attributes stay broken.
 * This replays the respawn recovery on the live player — no {@code PlayerList.respawn},
 * no gamemode flicker, no damage redirects.
 */
public final class RespawnLikeRecovery {
    private static final Logger LOGGER = LogManager.getLogger(DmzMohistMeleeFix.MOD_ID);
    private static final AtomicInteger LOGS = new AtomicInteger();

    @SuppressWarnings("unchecked")
    private static final RegistryObject<Attribute>[] PRIMARY_ATTRS = new RegistryObject[] {
            MainAttributes.STRENGTH,
            MainAttributes.STRIKE_POWER,
            MainAttributes.RESISTANCE,
            MainAttributes.VITALITY,
            MainAttributes.KI_POWER,
            MainAttributes.ENERGY
    };

    private RespawnLikeRecovery() {}

    public static void apply(ServerPlayer player, String reason) {
        if (player == null || player.m_9236_().f_46443_) {
            return;
        }
        // Intentional stat reset in progress — do not rewrite primaries from an old snapshot.
        if (PrimaryStatRepair.isSuppressed(player)) {
            try {
                ReachAttributeFix.repair(player, reason + "-respawn-like-suppressed");
            } catch (Throwable ignored) {
            }
            return;
        }
        try {
            PrimaryStatRepair.snapshot(player);
            ensureReachInstances(player);

            StatsProvider.get(StatsCapability.INSTANCE, (Entity) player).ifPresent(data -> {
                Stats stats = data.getStats();
                stats.setPlayer(player);
                try {
                    data.getResources().setPlayer(player);
                } catch (Throwable ignored) {
                }

                // Same effect as Clone → Stats.copyFrom: capture values (read-side snapshot
                // fallback applies if live bases were zeroed), then write onto AttributeMap.
                int str = stats.getStrength();
                int skp = stats.getStrikePower();
                int res = stats.getResistance();
                int vit = stats.getVitality();
                int pwr = stats.getKiPower();
                int ene = stats.getEnergy();

                // Prefer last-known-good snapshot when live read is still 0.
                str = preferSaved(player, MainAttributes.STRENGTH, str);
                skp = preferSaved(player, MainAttributes.STRIKE_POWER, skp);
                res = preferSaved(player, MainAttributes.RESISTANCE, res);
                vit = preferSaved(player, MainAttributes.VITALITY, vit);
                pwr = preferSaved(player, MainAttributes.KI_POWER, pwr);
                ene = preferSaved(player, MainAttributes.ENERGY, ene);

                ensurePrimaryInstances(player, str, skp, res, vit, pwr, ene);

                stats.setStrength(str);
                stats.setStrikePower(skp);
                stats.setResistance(res);
                stats.setVitality(vit);
                stats.setKiPower(pwr);
                stats.setEnergy(ene);

                // Exact respawn follow-ups from DMZ (minus full heal / form wipe).
                data.getStatus().setStrikeLocked(false);
                data.getStatus().setStunEffect(false);
                data.getStatus().setKnockedDown(false);
                data.getCooldowns().removeCooldown("KnockdownDuration");

                try {
                    StatsEvents.applyHealthBonus(player);
                } catch (Throwable t) {
                    if (LOGS.get() < 5) {
                        LOGGER.warn("[{}] applyHealthBonus failed: {}", DmzMohistMeleeFix.MOD_ID, t.toString());
                    }
                }
            });

            // Both LivingDeath and PlayerRespawn in DMZ call this.
            try {
                player.m_6210_();
            } catch (Throwable ignored) {
            }

            ReachAttributeFix.repair(player, reason + "-respawn-like");
            PrimaryStatRepair.ensure(player, reason + "-respawn-like");

            try {
                NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), (Entity) player);
                NetworkHandler.sendToTrackingEntityAndSelf(new ResourceSyncS2C(player), (Entity) player);
            } catch (Throwable ignored) {
            }

            int n = LOGS.incrementAndGet();
            if (n <= 40) {
                MeleeDebug.log(
                        "[{}] respawn-like recovery player={} reason={}",
                        DmzMohistMeleeFix.MOD_ID,
                        player.m_36316_().getName(),
                        reason
                );
            }
        } catch (Throwable t) {
            if (LOGS.get() < 8) {
                LOGGER.warn("[{}] respawn-like recovery failed: {}", DmzMohistMeleeFix.MOD_ID, t.toString());
            }
        }
    }

    private static int preferSaved(Player player, RegistryObject<Attribute> attr, int live) {
        if (live > 0) {
            return live;
        }
        try {
            return Math.max(live, PrimaryStatRepair.savedFor(player, attr.get()));
        } catch (Throwable t) {
            return live;
        }
    }

    private static void ensurePrimaryInstances(
            Player player,
            int str,
            int skp,
            int res,
            int vit,
            int pwr,
            int ene
    ) {
        int[] values = {str, skp, res, vit, pwr, ene};
        for (int i = 0; i < PRIMARY_ATTRS.length; i++) {
            Attribute attr = PRIMARY_ATTRS[i].get();
            if (attr == null) {
                continue;
            }
            AttributeInstance inst = player.m_21051_(attr);
            if (inst == null) {
                double base = values[i] > 0 ? values[i] : attr.m_22082_();
                PrimaryStatRepair.injectAttribute(player, attr, base);
            }
        }
        // Melee secondary default is 1.0 — inject only if completely missing (never rewrite).
        try {
            Attribute melee = MainAttributes.MELEE_DAMAGE.get();
            if (melee != null && player.m_21051_(melee) == null) {
                PrimaryStatRepair.injectAttribute(player, melee, 1.0D);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void ensureReachInstances(Player player) {
        try {
            Attribute entityReach = ForgeMod.ENTITY_REACH.get();
            if (entityReach != null && player.m_21051_(entityReach) == null) {
                double def = entityReach.m_22082_();
                if (!Double.isFinite(def) || def <= 0.0D) {
                    def = 3.0D;
                }
                PrimaryStatRepair.injectAttribute(player, entityReach, def);
            }
            Attribute blockReach = ForgeMod.BLOCK_REACH.get();
            if (blockReach != null && player.m_21051_(blockReach) == null) {
                double def = blockReach.m_22082_();
                if (!Double.isFinite(def) || def <= 0.0D) {
                    def = 4.5D;
                }
                PrimaryStatRepair.injectAttribute(player, blockReach, def);
            }
        } catch (Throwable ignored) {
        }
    }
}
