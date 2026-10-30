package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.command.LmCommandHelp;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/**
 * Staff tools for adjusting prestige state and prestige-shop investments
 * (skill floors, difficulty tiers, wallet points).
 */
public final class PrestigeAdmin {
    private PrestigeAdmin() {}

    public static String help() {
        return LmCommandHelp.prestigeAdmin();
    }

    public static String info(ServerPlayer target) {
        if (target == null) {
            return "§cPlayer not online.";
        }
        int held = PrestigeSystem.getHeld(target);
        int completed = PrestigeSystem.getCompleted(target);
        int points = PrestigePointsSystem.getPoints(target);
        int need = PrestigeSystem.requiredLevel(target);
        int highestTier = PrestigePointsSystem.highestPurchasedTier(target);
        StringBuilder sb = new StringBuilder();
        sb.append("§6Prestige §f").append(target.m_6302_()).append('\n');
        sb.append("§7Lifetime prestiges: §f").append(completed)
                .append(" §8| §7Current prestige amount: §6").append(held).append("§7/§f")
                .append(PrestigeSystem.maxHeld()).append('\n');
        sb.append("§7Prestige favor: §e").append(points).append('\n');
        sb.append("§7Level cap (Overhaul), the next prestige need: §e")
                .append(DmzRewards.formatWhole(need)).append('\n');
        sb.append("§7Permanent tiers: §fT").append(highestTier).append('\n');
        List<String> invested = PrestigePointsSystem.investedSkillSummary(target);
        if (invested.isEmpty()) {
            sb.append("§7Invested skills: §8none");
        } else {
            sb.append("§7Invested skills:\n");
            for (String line : invested) {
                sb.append("§8  · §7").append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }

    public static String listSkills(ServerPlayer target) {
        if (target == null) {
            return "§cPlayer not online.";
        }
        List<String> invested = PrestigePointsSystem.investedSkillSummary(target);
        StringBuilder sb = new StringBuilder();
        sb.append("§6Prestige skill floors §f").append(target.m_6302_()).append('\n');
        if (invested.isEmpty()) {
            sb.append("§8No prestige-invested skills.");
        } else {
            for (String line : invested) {
                sb.append("§8  · §7").append(line).append('\n');
            }
        }
        sb.append("\n§8Adjust: /padmin skill ").append(target.m_6302_())
                .append(" <id> set|add|remove <levels>");
        return sb.toString().trim();
    }

    public static String adjustHeld(ServerPlayer target, String mode, int amount) {
        if (target == null) {
            return "§cPlayer not online.";
        }
        int before = PrestigeSystem.getHeld(target);
        Integer next = applyMode(before, mode, amount, 0, PrestigeSystem.maxHeld());
        if (next == null) {
            return "§cUsage: held <player> <set|add|remove> <n>";
        }
        PrestigeSystem.setHeldPublic(target, next);
        log(target, "admin_held", mode, before, next);
        return "§aHeld prestige §f" + before + " §7→ §f" + PrestigeSystem.getHeld(target)
                + " §8(" + target.m_6302_() + ")";
    }

    public static String adjustCompleted(ServerPlayer target, String mode, int amount) {
        if (target == null) {
            return "§cPlayer not online.";
        }
        int before = PrestigeSystem.getCompleted(target);
        Integer next = applyMode(before, mode, amount, 0, 10_000);
        if (next == null) {
            return "§cUsage: completed <player> <set|add|remove> <n>";
        }
        PrestigeSystem.setCompletedStaff(target, next);
        log(target, "admin_completed", mode, before, next);
        return "§aLifetime prestiges §f" + before + " §7→ §f" + PrestigeSystem.getCompleted(target)
                + " §8| next need §e" + DmzRewards.formatWhole(PrestigeSystem.requiredLevel(target))
                + " §8(" + target.m_6302_() + ")";
    }

    public static String adjustPoints(ServerPlayer target, String mode, int amount) {
        if (target == null) {
            return "§cPlayer not online.";
        }
        int before = PrestigePointsSystem.getPoints(target);
        Integer next = applyMode(before, mode, amount, 0, 1_000_000);
        if (next == null) {
            return "§cUsage: points <player> <set|add|remove> <n>\n"
                    + "§8Or: points set <n> · points <player> <n>\n"
                    + "§8Or: addpoints|setpoints|removepoints <player> <n>";
        }
        PrestigePointsSystem.setPoints(target, next);
        log(target, "admin_points", mode, before, next);
        return "§aPrestige favor §e" + before + " §7→ §e" + PrestigePointsSystem.getPoints(target)
                + " §8(" + target.m_6302_() + ")"
                + "\n§8Wallet for Skills · Effects · Difficulty Tiers.";
    }

    public static String adjustSkill(
            ServerPlayer target, String skillId, String mode, int amount
    ) {
        if (target == null) {
            return "§cPlayer not online.";
        }
        return PrestigePointsSystem.adminAdjustSkill(target, skillId, mode, amount);
    }

    public static String adjustTier(ServerPlayer target, String mode, int tierId) {
        if (target == null) {
            return "§cPlayer not online.";
        }
        String m = mode == null ? "" : mode.toLowerCase(Locale.ROOT).trim();
        // Unified set/add/remove on highest permanent tier.
        if ("set".equals(m) || "add".equals(m) || "remove".equals(m) || "take".equals(m)
                || "sub".equals(m)) {
            String msg = PrestigePointsSystem.adminAdjustHighestTier(target, m, tierId);
            log(target, "admin_tier_adjust", m,
                    PrestigePointsSystem.highestPurchasedTier(target), tierId);
            return msg;
        }
        return switch (m) {
            case "give", "grant" -> {
                String msg = PrestigePointsSystem.adminGrantTier(target, tierId);
                log(target, "admin_tier_give", m, 0, tierId);
                yield msg;
            }
            case "clear" -> {
                String msg = PrestigePointsSystem.adminClearTier(target, tierId);
                log(target, "admin_tier_clear", m, tierId, 0);
                yield msg;
            }
            default -> "§cUsage: tier <player> <set|add|remove> <0-7>"
                    + "\n§c       tier <player> give <1-7> | clear <1-7|all>";
        };
    }

    /**
     * Staff {@code /padmin godki} — set/add/remove the God Ki Enhancement
     * investment count (0–5) for another player. Menu-inline feedback mirrors
     * {@link #adjustHeld}: value, mastery after the change, then the name.
     */
    public static String adjustGodKi(ServerPlayer target, String mode, int amount) {
        if (target == null) {
            return "§cPlayer not online.";
        }
        int before = GodKiEnhancementShop.invested(target);
        Integer next = applyMode(before, mode, amount, 0, GodKiEnhancementShop.MAX_INVESTMENTS);
        if (next == null) {
            return "§cUsage: godki <player> <set|add|remove> <0-5>";
        }
        GodKiEnhancementShop.adminSetInvested(target, next);
        log(target, "admin_godki", mode, before, next);
        return "§aGod Ki investments §f" + before + " §7→ §f" + GodKiEnhancementShop.invested(target)
                + " §8| mastery §f"
                + DmzRewards.formatWhole((long) GodKiEnhancementShop.mastery(target))
                + " §8(" + target.m_6302_() + ")";
    }

    private static Integer applyMode(int current, String mode, int amount, int min, int max) {
        if (mode == null) {
            return null;
        }
        String m = mode.toLowerCase(Locale.ROOT).trim();
        int next;
        switch (m) {
            case "set" -> next = amount;
            case "add" -> next = current + amount;
            case "remove", "take", "sub" -> next = current - amount;
            default -> {
                return null;
            }
        }
        return Math.max(min, Math.min(max, next));
    }

    private static void log(ServerPlayer target, String action, String mode, int before, int after) {
        SystemTelemetry.log("prestige_admin", action, target, null, Map.of(
                "mode", mode == null ? "" : mode,
                "before", before,
                "after", after
        ));
    }
}
