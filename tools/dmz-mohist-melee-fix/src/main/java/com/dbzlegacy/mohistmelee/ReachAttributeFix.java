package com.dbzlegacy.mohistmelee;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Repairs collapsed Forge reach attributes that brick DMZ range checks on Mohist.
 * <p>
 * Does <b>not</b> write {@code dragonminez:*} damage attributes — earlier versions
 * reset {@code ki_damage}/{@code melee_damage}/{@code strike_damage} bases/modifiers
 * to defaults (often 0), wiping gear/form bonuses. NaN on those is handled read-side
 * in {@code StatsDataSecondaryAttrMixin}.
 */
public final class ReachAttributeFix {
    private static final Logger LOGGER = LogManager.getLogger(DmzMohistMeleeFix.MOD_ID);
    private static final AtomicInteger LOGS = new AtomicInteger();

    private ReachAttributeFix() {}

    /** @return true if any reach attribute was changed */
    public static boolean repair(Player player, String reason) {
        if (player == null) {
            return false;
        }
        boolean changed = false;
        try {
            Attribute entityReach = ForgeMod.ENTITY_REACH.get();
            Attribute blockReach = ForgeMod.BLOCK_REACH.get();
            if (entityReach != null) {
                changed |= repairReach(player, entityReach, "forge:entity_reach", 3.0D, reason);
            }
            if (blockReach != null) {
                changed |= repairReach(player, blockReach, "forge:block_reach", 4.5D, reason);
            }
            // Vanilla attack damage NaN makes LivingAttackEvent amount NaN; DMZ FixVanillaEvents
            // then cancels the hit (animation already played). Repair base only when non-finite.
            changed |= repairFiniteBaseOnly(
                    player,
                    Attributes.f_22281_,
                    "minecraft:generic.attack_damage",
                    1.0D,
                    reason
            );
        } catch (Throwable t) {
            if (LOGS.get() < 5) {
                LOGGER.warn("[{}] reach repair failed: {}", DmzMohistMeleeFix.MOD_ID, t.toString());
            }
        }
        return changed;
    }

    /** Only rewrite attribute base when it is non-finite — never strip modifiers. */
    private static boolean repairFiniteBaseOnly(
            Player player,
            Attribute attribute,
            String label,
            double fallbackDefault,
            String reason
    ) {
        if (attribute == null) {
            return false;
        }
        AttributeInstance inst = player.m_21051_(attribute);
        if (inst == null) {
            return false;
        }
        double oldBase = inst.m_22115_();
        if (Double.isFinite(oldBase)) {
            // Still strip NaN modifiers only.
            return stripNaNModifiers(inst, player, label, oldBase, reason);
        }
        double def = attribute.m_22082_();
        if (!Double.isFinite(def) || def < 0.0D) {
            def = fallbackDefault;
        }
        inst.m_22100_(def);
        stripNaNModifiers(inst, player, label, oldBase, reason);
        int n = LOGS.incrementAndGet();
        if (n <= 40) {
            MeleeDebug.log(
                    "[{}] repaired {} player={} base={}->{} value={} reason={}",
                    DmzMohistMeleeFix.MOD_ID,
                    label,
                    playerName(player),
                    oldBase,
                    inst.m_22115_(),
                    inst.m_22135_(),
                    reason
            );
        }
        return true;
    }

    private static boolean stripNaNModifiers(
            AttributeInstance inst,
            Player player,
            String label,
            double oldBase,
            String reason
    ) {
        boolean changed = false;
        List<UUID> badMods = new ArrayList<>();
        for (AttributeModifier mod : inst.m_22122_()) {
            if (!Double.isFinite(mod.m_22218_())) {
                badMods.add(mod.m_22209_());
            }
        }
        for (UUID id : badMods) {
            inst.m_22120_(id);
            changed = true;
        }
        if (changed) {
            int n = LOGS.incrementAndGet();
            if (n <= 40) {
                MeleeDebug.log(
                        "[{}] stripped NaN modifiers {} player={} base={} value={} reason={}",
                        DmzMohistMeleeFix.MOD_ID,
                        label,
                        playerName(player),
                        oldBase,
                        inst.m_22135_(),
                        reason
                );
            }
        }
        return changed;
    }

    /**
     * Reach only: fix non-finite base; remove only NaN modifiers.
     * Never wipe all modifiers / never touch dragonminez damage attrs.
     */
    private static boolean repairReach(
            Player player,
            Attribute attribute,
            String label,
            double fallbackDefault,
            String reason
    ) {
        AttributeInstance inst = player.m_21051_(attribute);
        if (inst == null) {
            int n = LOGS.incrementAndGet();
            if (n <= 20) {
                LOGGER.warn(
                        "[{}] missing {} player={} reason={}",
                        DmzMohistMeleeFix.MOD_ID,
                        label,
                        playerName(player),
                        reason
                );
            }
            return false;
        }

        boolean changed = false;
        double def = attribute.m_22082_();
        if (!Double.isFinite(def) || def <= 0.0D) {
            def = fallbackDefault;
        }

        double oldBase = inst.m_22115_();
        // Only rewrite base when it is actually collapsed — never because value looks low
        // (low value can be legitimate after modifiers; rewriting base would erase gear).
        if (!Double.isFinite(oldBase) || oldBase < 0.25D) {
            inst.m_22100_(def);
            changed = true;
        }

        List<UUID> badMods = new ArrayList<>();
        for (AttributeModifier mod : inst.m_22122_()) {
            if (!Double.isFinite(mod.m_22218_())) {
                badMods.add(mod.m_22209_());
            }
        }
        for (UUID id : badMods) {
            inst.m_22120_(id);
            changed = true;
        }

        // If value is still non-finite after removing bad mods, fix base only — do NOT
        // removeAllModifiers (that wiped reach gear bonuses and DMZ secondaries before).
        double value = inst.m_22135_();
        if (!Double.isFinite(value)) {
            inst.m_22100_(def);
            changed = true;
            value = inst.m_22135_();
            if (!Double.isFinite(value)) {
                // Last resort: strip remaining non-finite mods only (already done) and
                // leave gear mods alone even if value stays weird — read path / sanitize
                // handles combat range.
                int n = LOGS.incrementAndGet();
                if (n <= 20) {
                    LOGGER.warn(
                            "[{}] {} still non-finite after base repair player={} value={} reason={}",
                            DmzMohistMeleeFix.MOD_ID,
                            label,
                            playerName(player),
                            value,
                            reason
                    );
                }
            }
        }

        if (changed) {
            int n = LOGS.incrementAndGet();
            if (n <= 40) {
                MeleeDebug.log(
                        "[{}] repaired {} player={} base={}->{} value={} reason={}",
                        DmzMohistMeleeFix.MOD_ID,
                        label,
                        playerName(player),
                        oldBase,
                        inst.m_22115_(),
                        inst.m_22135_(),
                        reason
                );
            }
        }
        return changed;
    }

    /**
     * If DMZ range math produced NaN / non-positive, repair reach attrs and return a
     * usable range. Does not alter damage attributes.
     */
    public static double sanitizeEffectiveRange(Player player, double weaponAttackRange, double computed) {
        // Fist attack_range is 2; swords ~2.5. Collapsed ENTITY_REACH subtracts from that, so
        // empty-hand fails first after cross-dim. Never allow effective range below the weapon's
        // own attack_range (half-range floors still left fists unusable at ~1.0).
        double minUsable = weaponAttackRange > 0.0D ? weaponAttackRange : 2.0D;
        minUsable = Math.max(2.0D, minUsable);
        if (Double.isFinite(computed) && computed >= minUsable) {
            return computed;
        }
        repair(player, "sanitize-range");

        try {
            Attribute entityReach = ForgeMod.ENTITY_REACH.get();
            if (entityReach != null) {
                AttributeInstance inst = player.m_21051_(entityReach);
                if (inst != null) {
                    double cur = inst.m_22135_();
                    double def = entityReach.m_22082_();
                    if (!Double.isFinite(def) || def <= 0.0D) {
                        def = 3.0D;
                    }
                    // If reach still sits below default after repair, treat delta as 0 so fists
                    // keep their configured attack_range instead of shrinking further.
                    if (!Double.isFinite(cur) || cur < def) {
                        cur = def;
                    }
                    if (Double.isFinite(cur)) {
                        double fixed = weaponAttackRange + (cur - def);
                        if (Double.isFinite(fixed) && fixed >= minUsable) {
                            return fixed;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        double fallback = minUsable;
        int n = LOGS.incrementAndGet();
        if (n <= 30) {
            MeleeDebug.log(
                    "[{}] sanitized range player={} weapon={} computed={} -> {}",
                    DmzMohistMeleeFix.MOD_ID,
                    playerName(player),
                    weaponAttackRange,
                    computed,
                    fallback
            );
        }
        return fallback;
    }

    public static double readEntityReach(Player player) {
        return readAttr(player, ForgeMod.ENTITY_REACH.get());
    }

    public static double readKiDamage(Player player) {
        try {
            return readAttr(player, com.dragonminez.common.init.MainAttributes.KI_DAMAGE.get());
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    private static double readAttr(Player player, Attribute attr) {
        try {
            if (attr == null) {
                return Double.NaN;
            }
            AttributeInstance inst = player.m_21051_(attr);
            return inst == null ? Double.NaN : inst.m_22135_();
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    private static String playerName(Player player) {
        if (player instanceof ServerPlayer sp) {
            return sp.m_36316_().getName();
        }
        return String.valueOf(player.m_20148_());
    }
}
