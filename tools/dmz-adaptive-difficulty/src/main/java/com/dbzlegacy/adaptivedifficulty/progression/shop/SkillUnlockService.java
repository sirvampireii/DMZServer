package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.calc.LmOverhaulScaledCombat;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.progression.DmzSkillUtil;
import com.dbzlegacy.adaptivedifficulty.progression.InvestedStrength;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import com.dbzlegacy.adaptivedifficulty.progression.skills.LivingWorldNearbyMeditation;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.skills.Skills;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Port of {@code SkillUnlockNPC.js} + {@code SkillCheckCommand.js} (trigger 21)
 * as {@code /skills} chat GUI pages. Also exposes list builders for inventory GUIs.
 */
public final class SkillUnlockService {
    public static final int TRIGGER_ID = 21;
    private static final int HARD_MAX_POTENTIAL = 30;

    private SkillUnlockService() {}

    public static void open(ServerPlayer player, String page) {
        open(player, page, false);
    }

    /**
     * @param skillCheck when true, titles/nav use Skill Check branding ({@code /skillcheck}).
     */
    public static void open(ServerPlayer player, String page, boolean skillCheck) {
        if (!DifficultyConfig.get().enableSkillUnlockService || player == null) {
            return;
        }
        // core, natural, saga, and advanced all open the same skill list.
        showPage(player, "core", skillCheck);
        SystemTelemetry.log(
                skillCheck ? "skillcheck" : "skills",
                "open",
                player,
                null,
                Map.of("page", page == null ? "core" : page));
    }

    /** Trigger-21 equivalent — donator Skill Check only. */
    public static void trigger21(ServerPlayer player) {
        com.dbzlegacy.adaptivedifficulty.progression.shop.SkillCheckService.trigger21(player);
    }

    /** Header + core skill lines for inventory GUI lore. */
    public static List<String> coreLines(ServerPlayer player) {
        return buildPageLines(player, "core");
    }

    /**
     * Ultra Skill Check home: skills only (no DMZ level / Ki / Energy header, no tip padding).
     * One short line per skill.
     */
    public static List<String> skillCheckGuiLines(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        StatsData data = DmzProgression.stats(player);
        if (data == null) {
            out.add(UltraStyle.DANGER
                    + "No DMZ data found. Rejoin or ask staff if this keeps happening.");
            return out;
        }
        Skills skills;
        try {
            skills = data.getSkills();
        } catch (Throwable t) {
            skills = null;
        }
        if (skills == null) {
            out.add(UltraStyle.DANGER + "No skill data found. Ask staff if skill unlock is on.");
            return out;
        }
        DmzSkillUtil.prepareForRead(skills);
        appendPotentialCompact(out, player, skills);
        LivingWorldNearbyMeditation.appendCompactLine(out, player, skills);
        appendSagaCompact(out, player, skills);
        return out;
    }

    /** @deprecated Advanced folded into Saga — returns saga lines. */
    public static List<String> advancedLines(ServerPlayer player) {
        return buildPageLines(player, "saga");
    }

    /** Header + saga skill lines for inventory GUI lore. */
    public static List<String> sagaLines(ServerPlayer player) {
        return buildPageLines(player, "saga");
    }

    private static List<String> buildPageLines(ServerPlayer player, String page) {
        List<String> out = new ArrayList<>();
        StatsData data = DmzProgression.stats(player);
        if (data == null) {
            out.add("§c[Skill Progress] ERROR: No DMZ data found.");
            return out;
        }
        Skills skills;
        try {
            skills = data.getSkills();
        } catch (Throwable t) {
            skills = null;
        }
        if (skills == null) {
            out.add("§c[Skill Progress] ERROR: No DMZ skill data found.");
            return out;
        }
        // Repair aliases + align max levels with skills.json before we read them.
        DmzSkillUtil.prepareForRead(skills);
        int level = safeLevel(data);
        double kiDamage = LmOverhaulScaledCombat.ki(data);
        double maxEnergy = safeEnergy(data);
        int investedStr = InvestedStrength.points(player);
        // Slim header — one live line for inventory GUIs (chat path still gets separator).
        out.add("§7DMZ §f" + level
                + " §8· §7Ki §f" + format(kiDamage)
                + " §8· §7Energy §f" + format(maxEnergy)
                + " §8· §7STR §f" + format(LmOverhaulScaledCombat.effectiveInvested(data, "STR"))
                + " §8(§f" + investedStr + "§8 invested) §8"
                + LmOverhaulScaledCombat.formatScale(data));
        out.add("§8----------------------------");
        out.add("§6§lSkills");
        appendPotential(out, player, skills);
        LivingWorldNearbyMeditation.appendLines(out, player, skills);
        appendSaga(out, player, skills);
        return out;
    }

    private static void showPage(ServerPlayer player, String page, boolean skillCheck) {
        List<String> lines = buildPageLines(player, page);
        if (lines.size() == 1 && lines.get(0).startsWith("§c")) {
            DmzRewards.msg(player, lines.get(0).replace("Skill Progress", skillCheck ? "Skill Check" : "Skill Progress"));
            return;
        }

        send(player, "");
        send(player, skillCheck
                ? "§6§l------ Skill Check ------§r"
                : "§6§l------ Skill Progress ------§r");
        for (String line : lines) {
            send(player, line);
        }
    }

    private static void appendPotential(List<String> out, ServerPlayer player, Skills skills) {
        int level = skillLevel(skills, "potentialunlock");
        if (level >= HARD_MAX_POTENTIAL) {
            out.add("§dPotential Unlock§7: §6§lMAX§r §7(" + level + "/" + HARD_MAX_POTENTIAL + ")");
            tip(out, "You're fully unlocked — Potential can't go any higher.");
            return;
        }
        int next = level + 1;
        int required = next * 100;
        long progress = ProgressionData.storedGetLong(player, "potentialunlock_points_to_level_" + next, 0L);
        if (progress > required) {
            progress = required;
        }
        out.add("§dPotential Unlock§7: §f" + level + "/" + HARD_MAX_POTENTIAL
                + " §8· §f" + progress + "/" + required);
        tip(out, "Spar with other players to raise Potential toward 30.");
    }

    /** Single-line Potential for Ultra Skill Check (no tip rows). */
    private static void appendPotentialCompact(List<String> out, ServerPlayer player, Skills skills) {
        int level = skillLevel(skills, "potentialunlock");
        if (level >= HARD_MAX_POTENTIAL) {
            out.add(UltraStyle.LIGHT_PURPLE + "Potential " + UltraStyle.VALUE + level + "/"
                    + HARD_MAX_POTENTIAL + " " + UltraStyle.DIM + "- " + UltraStyle.GOLD + "Max");
            return;
        }
        int next = level + 1;
        int required = next * 100;
        long progress = ProgressionData.storedGetLong(player, "potentialunlock_points_to_level_" + next, 0L);
        if (progress > required) {
            progress = required;
        }
        out.add(UltraStyle.LIGHT_PURPLE + "Potential " + UltraStyle.VALUE + level + "/"
                + HARD_MAX_POTENTIAL + " " + UltraStyle.DIM + "- " + UltraStyle.VALUE
                + progress + "/" + required);
    }

    private static void appendSaga(List<String> out, ServerPlayer player, Skills skills) {
        appendSagaSkill(out, player, skills, "kicontrol", "Ki Control", "§3", 1);
        appendSagaSkill(out, player, skills, "kimanipulation", "Ki Manipulation", "§9", 10);
        appendSagaSkill(out, player, skills, "kisense", "Ki Sense", "§5", 10);
        appendSagaSkill(out, player, skills, "defense_penetration", "Defense Penetration", "§c", 10);
        appendSagaSkill(out, player, skills, "healing_reduction", "Healing Reduction", "§4", 10);
        appendSagaSkill(out, player, skills, "instant_transmission", "Instant Transmission", "§d", 10);
        appendSagaSkill(out, player, skills, "ki_infusion", "Ki Infusion", "§b", 10);
        appendSagaSkill(out, player, skills, "kiboost", "Ki Boost", "§3", 4);
        appendSagaSkill(out, player, skills, "kiprotection", "Ki Protection", "§9", 10);
        appendSagaSkill(out, player, skills, "kaioken", "Kaioken", "§c", 5);
        appendSagaSkill(out, player, skills, "fusion", "Fusion", "§d", 5);
    }

    private static void appendSagaCompact(List<String> out, ServerPlayer player, Skills skills) {
        appendSagaSkillCompact(out, player, skills, "kicontrol", "Ki Control", UltraStyle.DARK_AQUA, 1);
        appendSagaSkillCompact(out, player, skills, "kimanipulation", "Ki Manipulation", UltraStyle.BLUE, 10);
        appendSagaSkillCompact(out, player, skills, "kisense", "Ki Sense", UltraStyle.DARK_PURPLE, 10);
        appendSagaSkillCompact(out, player, skills, "defense_penetration", "Defense Penetration", UltraStyle.RED, 10);
        appendSagaSkillCompact(out, player, skills, "healing_reduction", "Heal Red", UltraStyle.DARK_RED, 10);
        appendSagaSkillCompact(out, player, skills, "instant_transmission", "IT", UltraStyle.LIGHT_PURPLE, 10);
        appendSagaSkillCompact(out, player, skills, "ki_infusion", "Ki Infusion", UltraStyle.AQUA, 10);
        appendSagaSkillCompact(out, player, skills, "kiboost", "Ki Boost", UltraStyle.DARK_AQUA, 4);
        appendSagaSkillCompact(out, player, skills, "kiprotection", "Ki Protection", UltraStyle.BLUE, 10);
        appendSagaSkillCompact(out, player, skills, "kaioken", "Kaioken", UltraStyle.RED, 5);
        appendSagaSkillCompact(out, player, skills, "fusion", "Fusion", UltraStyle.LIGHT_PURPLE, 5);
    }

    private static void appendSagaSkill(
            List<String> out, ServerPlayer player, Skills skills, String id, String name,
            String color, int fallbackMax
    ) {
        int level = effectiveSkillLevel(player, skills, id);
        int max = skillMax(skills, id, fallbackMax);
        if (level < 1) {
            out.add(color + name + "§7: §f0/" + max + " §8· §cLocked");
            return;
        }
        if (level >= max) {
            out.add(color + name + "§7: §f" + level + "/" + max + " §8· §6Max");
        } else {
            out.add(color + name + "§7: §f" + level + "/" + max + " §8· §aUnlocked");
        }
    }

    private static void appendSagaSkillCompact(
            List<String> out, ServerPlayer player, Skills skills, String id, String name,
            String color, int fallbackMax
    ) {
        int level = effectiveSkillLevel(player, skills, id);
        int max = skillMax(skills, id, fallbackMax);
        if (level < 1) {
            out.add(color + name + " " + UltraStyle.VALUE + "0/" + max
                    + " " + UltraStyle.DIM + "- " + UltraStyle.DANGER + "Locked");
            return;
        }
        if (level >= max) {
            out.add(color + name + " " + UltraStyle.VALUE + level + "/" + max
                    + " " + UltraStyle.DIM + "- " + UltraStyle.GOLD + "Max");
        } else {
            out.add(color + name + " " + UltraStyle.VALUE + level + "/" + max
                    + " " + UltraStyle.DIM + "- " + UltraStyle.GREEN + "On");
        }
    }

    private static void appendStrengthLine(
            List<String> out, Skills skills, String id, String name, String color, int investedStr
    ) {
        int level = skillLevel(skills, id);
        int max = skillMax(skills, id, 10);
        if (level >= max) {
            out.add(color + name + "§7: §6§lMAX§r §7(" + level + "/" + max + ")");
            tip(out, strengthHow(id, true, investedStr, 0));
            return;
        }
        int next = level + 1;
        int required = strengthRequirement(next);
        int need = Math.max(0, required - investedStr);
        String needNote = need <= 0
                ? " §8· §aready"
                : " §8· need §f" + need + " STR";
        out.add(color + name + "§7: §f" + level + "/" + max + needNote);
        tip(out, strengthHow(id, false, investedStr, required));
    }

    /** One short human tip line per skill (progress lives on the level line). */
    private static void tip(List<String> out, String tip) {
        if (tip == null || tip.isBlank()) {
            return;
        }
        out.add("§8  - §7" + tip);
    }

    private static String strengthHow(String id, boolean maxed, int investedStr, int required) {
        String skill = "jump".equals(id) ? "Jump" : "Sprint";
        if (maxed) {
            return "You've maxed out " + skill + ".";
        }
        if (investedStr >= required) {
            return "You've invested enough Strength — keep using " + skill + " to progress.";
        }
        return "Invest more Strength to unlock the next " + skill + " level.";
    }

    /**
     * Live DMZ level with alias repair, plus prestige-shop floor when live reads 0
     * but the player already invested points (common after prestige reset lag).
     */
    private static int effectiveSkillLevel(ServerPlayer player, Skills skills, String id) {
        int live = skillLevel(skills, id);
        if (live > 0 || player == null || id == null || id.isBlank()) {
            return live;
        }
        int purchased = PrestigePointsSystem.getPurchasedSkillLevels(player, id);
        if (purchased <= 0) {
            return live;
        }
        int max = skillMax(skills, id, purchased);
        return Math.min(max, Math.max(live, purchased));
    }

    private static int skillLevel(Skills skills, String id) {
        return DmzSkillUtil.level(skills, id);
    }

    private static int skillMax(Skills skills, String id, int fallback) {
        return DmzSkillUtil.maxLevel(skills, id, fallback);
    }

    private static int strengthRequirement(int next) {
        return switch (next) {
            case 1 -> 20;
            case 2 -> 100;
            case 3 -> 250;
            case 4 -> 500;
            case 5 -> 1000;
            case 6 -> 1500;
            case 7 -> 2000;
            case 8 -> 2500;
            case 9 -> 3000;
            case 10 -> 3500;
            default -> 3500;
        };
    }

    private static int safeLevel(StatsData data) {
        try {
            return Math.max(1, data.getLevel());
        } catch (Throwable t) {
            return 1;
        }
    }

    private static double safeKi(StatsData data) {
        try {
            return Math.max(0.0, data.getKiDamage());
        } catch (Throwable t) {
            return 0.0;
        }
    }

    private static double safeEnergy(StatsData data) {
        try {
            return Math.max(0.0, data.getMaxEnergy());
        } catch (Throwable t) {
            return 0.0;
        }
    }

    private static int safeStrength(StatsData data) {
        try {
            return Math.max(0, data.getStats().getStrength());
        } catch (Throwable t) {
            return 0;
        }
    }

    private static String format(double v) {
        if (v >= 1000) {
            return DmzRewards.formatWhole(v);
        }
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static void send(ServerPlayer player, String text) {
        player.m_213846_(Component.m_237113_(text));
    }
}
