package com.dbzlegacy.adaptivedifficulty.kube.hotpath;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Hard-cap AttributesLib healing values. Same UUIDs and MULTIPLY_TOTAL math as lifesteal_cap.js.
 */
public final class HealCap {
    public static final int TICK_INTERVAL = 20;

    private static final Cap[] CAPS = {
        new Cap("attributeslib:life_steal", 0.0, "dbz_lifesteal_cap",
                UUID.fromString("c4a1e8d2-5b73-4f0a-9d6e-2f8b1a0c7e55")),
        new Cap("attributeslib:overheal", 0.05, "dbz_overheal_cap",
                UUID.fromString("d5b2f9e3-6c84-501b-ae7f-309c2b1d8f66")),
        new Cap("attributeslib:healing_received", 1.05, "dbz_healing_received_cap",
                UUID.fromString("e6c3a0f4-7d95-612c-bf80-41ad3c2e9077"))
    };

    private HealCap() {}

    /**
     * 4.7.75: the healing caps as one line ("Life Steal off | Overheal 5% | Received +5%"), for Character Services
     * Home; null when the healing caps are off. The login chat line it replaces is gone (nothing to act on at login).
     */
    public static String summary(ServerPlayer player) {
        if (player == null || !HotPathConfig.get().healing.enabled) {
            return null;
        }
        double overheal = read(player, "attributeslib:overheal", 0.0);
        double received = read(player, "attributeslib:healing_received", 1.0);
        HotPathConfig.Healing healing = HotPathConfig.get().healing;
        String lifeStealLabel = healing.lifeStealMax <= 0.0
                ? "Life Steal off"
                : "Life Steal " + pct(read(player, "attributeslib:life_steal", 0.0)) + "%";
        return lifeStealLabel + " | Overheal " + pct(overheal) + "% | Received +" + receivedPct(received) + "%";
    }

    public static void announce(ServerPlayer player) {
        if (player == null || !HotPathConfig.get().healing.enabled) {
            return;
        }
        double overheal = read(player, "attributeslib:overheal", 0.0);
        double received = read(player, "attributeslib:healing_received", 1.0);
        HotPathConfig.Healing healing = HotPathConfig.get().healing;
        String lifeStealLabel =
                healing.lifeStealMax <= 0.0
                        ? "Life Steal off"
                        : "Life Steal " + pct(read(player, "attributeslib:life_steal", 0.0)) + "%";
        HotLog.tell(
                player,
                ChatFormatting.GRAY,
                "Healing caps: "
                        + lifeStealLabel
                        + " | Overheal "
                        + pct(overheal)
                        + "% | Received +"
                        + receivedPct(received)
                        + "%");
    }

    public static void clamp(ServerPlayer player) {
        if (player == null || !HotPathConfig.get().healing.enabled) {
            return;
        }
        for (Cap cap : CAPS) {
            try {
                clampOne(player, cap);
            } catch (Throwable t) {
                HotLog.debug("[Heal Cap] " + cap.attrId + " failed: " + t);
            }
        }
    }

    private static void clampOne(ServerPlayer player, Cap cap) {
        Attribute attribute = cap.attribute();
        if (attribute == null) {
            return;
        }
        AttributeInstance inst = player.m_21051_(attribute);
        if (inst == null) {
            return;
        }
        try {
            inst.m_22120_(cap.uuid);
        } catch (Throwable ignored) {
        }
        double value = inst.m_22135_();
        double amount = multiplyCapAmount(value, maxFor(cap));
        if (amount == 0.0) {
            return;
        }
        AttributeModifier mod = new AttributeModifier(
                cap.uuid, cap.name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL);
        try {
            inst.m_22118_(mod);
        } catch (Throwable transientFailed) {
            inst.m_22125_(mod);
        }
    }

    private static double read(ServerPlayer player, String id, double fallback) {
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(id));
        if (attribute == null) {
            return fallback;
        }
        try {
            return player.m_21133_(attribute);
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static String pct(double value) {
        double shown = value * 100.0;
        if (shown < 0.0) {
            shown = 0.0;
        }
        return trim(shown);
    }

    private static String receivedPct(double value) {
        double shown = (value - 1.0) * 100.0;
        if (shown < 0.0) {
            shown = 0.0;
        }
        return trim(shown);
    }

    private static String trim(double shown) {
        double rounded = Math.round(shown * 10.0) / 10.0;
        if (rounded == Math.rint(rounded)) {
            return Long.toString(Math.round(rounded));
        }
        return Double.toString(rounded);
    }

    private static double maxFor(Cap cap) {
        HotPathConfig.Healing healing = HotPathConfig.get().healing;
        return switch (cap.attrId) {
            case "attributeslib:life_steal" -> healing.lifeStealMax;
            case "attributeslib:overheal" -> healing.overhealMax;
            case "attributeslib:healing_received" -> healing.healingReceivedMax;
            default -> cap.max;
        };
    }

    static double multiplyCapAmount(double value, double max) {
        if (!(value > max) || !(value > 0.0) || !Double.isFinite(value)) {
            return 0.0;
        }
        return max / value - 1.0;
    }

    private static final class Cap {
        final String attrId;
        final double max;
        final String name;
        final UUID uuid;
        private Attribute resolved;
        private boolean looked;

        Cap(String attrId, double max, String name, UUID uuid) {
            this.attrId = attrId;
            this.max = max;
            this.name = name;
            this.uuid = uuid;
        }

        Attribute attribute() {
            if (looked) {
                return resolved;
            }
            looked = true;
            int colon = attrId.indexOf(':');
            if (colon <= 0 || colon >= attrId.length() - 1) {
                return null;
            }
            resolved = ForgeRegistries.ATTRIBUTES.getValue(
                    new ResourceLocation(attrId.substring(0, colon), attrId.substring(colon + 1)));
            return resolved;
        }
    }
}
