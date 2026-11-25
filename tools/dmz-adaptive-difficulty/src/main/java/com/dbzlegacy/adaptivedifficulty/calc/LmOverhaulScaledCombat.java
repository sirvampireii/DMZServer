package com.dbzlegacy.adaptivedifficulty.calc;

import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulPrestigeIntegration;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Stats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Live combat numbers <b>after</b> Overhaul {@code scaleMultiplier}
 * ({@code 1 + prestige × scaleBonusPerPrestige}).
 * <p>
 * Combat numbers are the live getters. LM does not multiply them by prestige scale.
 */
public final class LmOverhaulScaledCombat {
    private LmOverhaulScaledCombat() {}

    public static double scale(StatsData data) {
        return LmOverhaulPrestigeIntegration.combatScaleMultiplier(data);
    }

    public static boolean scaled(StatsData data) {
        return scale(data) > 1.000_001d;
    }

    public static double melee(StatsData data) {
        return sane(read(data, StatsData::getMeleeDamage, 1.0d), 1.0d);
    }

    public static double strike(StatsData data) {
        return sane(read(data, StatsData::getStrikeDamage, 1.0d), 1.0d);
    }

    public static double ki(StatsData data) {
        return sane(read(data, StatsData::getKiDamage, 1.0d), 1.0d);
    }

    public static double energy(StatsData data) {
        try {
            float scaled = com.dbzlegacy.adaptivedifficulty.progression.DmzResourcePoolClamp
                    .actualMaxEnergy(data);
            if (Float.isFinite(scaled) && scaled > 1f) {
                return sane(scaled, 1.0d);
            }
        } catch (Throwable ignored) {
        }
        return sane(read(data, d -> (double) d.getMaxEnergy(), 1.0d), 1.0d);
    }

    public static double defense(StatsData data) {
        return sane(read(data, StatsData::getDefense, 1.0d), 1.0d);
    }

    public static double maxDefense(StatsData data) {
        return sane(read(data, StatsData::getMaxDefense, 1.0d), 1.0d);
    }

    public static double health(StatsData data) {
        return sane(read(data, d -> (double) d.getMaxHealth(), 20.0d), 20.0d);
    }

    /**
     * Effective invested display: {@code points × race scaling × totalMult}
     * (totalMult already includes Overhaul scale for combat keys).
     */
    public static double effectiveInvested(StatsData data, String key) {
        if (data == null || key == null) {
            return 0.0d;
        }
        try {
            Stats stats = data.getStats();
            if (stats == null) {
                return 0.0d;
            }
            double invested = switch (key.toUpperCase()) {
                case "STR" -> stats.getStrength();
                case "SKP", "SPD" -> stats.getStrikePower();
                case "RES", "DEF" -> stats.getResistance();
                case "VIT" -> stats.getVitality();
                case "PWR" -> stats.getKiPower();
                case "ENE" -> stats.getEnergy();
                default -> data.getCurrentStatValue(key);
            };
            double scaling = Math.max(0.0d, data.getStatScaling(key));
            double mult = Math.max(0.0d, data.getTotalMultiplier(key));
            double out = Math.max(0.0d, invested) * (scaling > 0.0d ? scaling : 1.0d)
                    * (mult > 0.0d ? mult : 1.0d);
            return Double.isFinite(out) ? out : 0.0d;
        } catch (Throwable ignored) {
            return 0.0d;
        }
    }

    public static void putPlaceholders(Map<String, String> out, ServerPlayer player) {
        putPlaceholders(out, (Player) player);
    }

    public static void putPlaceholders(Map<String, String> out, Player player) {
        putPlaceholders(out, player == null ? null : DmzProgression.stats(player));
    }

    public static void putPlaceholders(Map<String, String> out, StatsData data) {
        if (out == null) {
            return;
        }
        String scale = formatScale(data);
        String melee = formatNum(melee(data));
        String strike = formatNum(strike(data));
        String ki = formatNum(ki(data));
        String defense = formatNum(defense(data));
        String health = formatNum(health(data));
        String str = formatNum(effectiveInvested(data, "STR"));
        String skp = formatNum(effectiveInvested(data, "SKP"));
        String res = formatNum(effectiveInvested(data, "RES"));
        String vit = formatNum(effectiveInvested(data, "VIT"));
        String pwr = formatNum(effectiveInvested(data, "PWR"));
        out.put("overhaul_scale", scale);
        out.put("melee_scaled", melee);
        out.put("strike_scaled", strike);
        out.put("ki_scaled", ki);
        out.put("defense_scaled", defense);
        out.put("health_scaled", health);
        out.put("str_scaled", str);
        out.put("skp_scaled", skp);
        out.put("res_scaled", res);
        out.put("vit_scaled", vit);
        out.put("pwr_scaled", pwr);
        // Existing GUI / PAPI keys — show live combat after Overhaul scale.
        out.put("melee", melee);
        out.put("strike", strike);
        out.put("ki", ki);
        out.put("defense", defense);
        out.put("health", health);
        out.put("ki_damage", ki);
        out.put("str", str);
        out.put("skp", skp);
        out.put("res", res);
        out.put("vit", vit);
        out.put("pwr", pwr);
    }

    /** Chat / lore lines: Overhaul scale + live melee/strike/ki/defense. */
    public static List<String> compactLines(StatsData data) {
        List<String> lines = new ArrayList<>(3);
        lines.add(UltraStyle.GRAY + "Overhaul scale " + UltraStyle.WHITE + formatScale(data));
        lines.add(UltraStyle.GRAY + "Melee " + UltraStyle.WHITE + formatNum(melee(data))
                + " " + UltraStyle.DARK_GRAY + "· " + UltraStyle.GRAY + "Strike " + UltraStyle.WHITE + formatNum(strike(data)));
        lines.add(UltraStyle.GRAY + "Ki " + UltraStyle.WHITE + formatNum(ki(data))
                + " " + UltraStyle.DARK_GRAY + "· " + UltraStyle.GRAY + "Defense " + UltraStyle.WHITE + formatNum(defense(data)));
        return lines;
    }

    private static String formatNum(double v) {
        if (!Double.isFinite(v)) {
            return "0";
        }
        if (Math.abs(v - Math.rint(v)) < 0.05d) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    public static String formatScale(StatsData data) {
        double s = scale(data);
        if (s <= 1.000_001d) {
            return "x1";
        }
        if (Math.abs(s - Math.rint(s)) < 0.001d) {
            return "x" + (long) Math.rint(s);
        }
        return String.format(java.util.Locale.ROOT, "x%.2f", s);
    }

    private static double read(StatsData data, StatFn fn, double fallback) {
        if (data == null) {
            return fallback;
        }
        try {
            double v = fn.get(data);
            return Double.isFinite(v) ? v : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static double sane(double value, double floor) {
        return CombatSanity.saneLive(value, floor);
    }

    @FunctionalInterface
    private interface StatFn {
        double get(StatsData data);
    }
}
