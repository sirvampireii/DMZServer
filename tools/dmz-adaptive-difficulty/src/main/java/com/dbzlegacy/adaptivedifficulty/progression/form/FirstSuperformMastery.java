package com.dbzlegacy.adaptivedifficulty.progression.form;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.progression.DmzSkillUtil;
import com.dbzlegacy.adaptivedifficulty.progression.form.FirstSuperformChoice.Candidate;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.FormMasteries;
import com.dragonminez.common.stats.skills.Skills;
import com.dragonminez.common.util.TransformationsHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/**
 * Quest rewards for the Frieza Saga 16 branch. Each command grants one form:
 * the race's first superform, its first legendary form, or its first god form
 * (Super Saiyan God for Saiyans; 4.7.25).
 *
 * <p>The first superform is the earliest superform with unlock level 1 or
 * higher. Level-0 forms such as Oozaru for Saiyans are skipped, so a race
 * whose only superform sits at level 0 gets no grant. A form's tier comes from
 * its group name or its formType, with a form-name fallback for god and
 * legendary ({@link FirstSuperformChoice}; 4.7.27).
 *
 * <p>The form-group skill is made usable before any mastery is written (4.7.30): transforming needs
 * mastery and the skill at level 1 or more, and DMZ's raw {@code Skills.setSkillLevel} creates a
 * level-0 / max-0 skill when the form skill has no {@code skills.json} costs (superforms,
 * legendaryforms, godforms). {@link #ensureSkill} registers a usable max first (never lowering a
 * healthy one), sets the level, and checks it stuck; when it did not, the grant fails naming the skill
 * instead of reporting mastery. A re-grant repairs a broken skill even when the mastery is already there.
 *
 * <p>Mastery is compared exactly (4.7.32). Fighting gain leaves hair such as 9.999999999999831, and
 * DMZ's {@code meetsMasteryRequisite} is a strict compare, so that value keeps a 10-mastery form
 * locked. Any gap below the target is topped up to the exact target; "already has" only fires when the
 * stored value is already at or above it.
 *
 * <p>Race resolution matches {@code TransformationReward.giveReward}:
 * {@code StatsProvider}/{@code StatsCapability} via {@code DmzProgression.stats},
 * then {@code Character.getRaceName()}. Form groups come from the live race
 * config ({@code ConfigManager.getAllFormsForRace}), the same folders
 * {@code DmzContentDiscovery.discoverRaceIds} scans.
 */
public final class FirstSuperformMastery {
    private FirstSuperformMastery() {}

    public static final class Outcome {
        public final boolean ok;
        public final String message;

        Outcome(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    public static Outcome grant(ServerPlayer player, double amount, FirstSuperformChoice.Kind kind) {
        if (player == null) {
            return fail("Run that while logged in as a player, or name one.");
        }
        if (!(amount > 0.0) || Double.isNaN(amount) || Double.isInfinite(amount)) {
            return fail("Mastery amount has to be above 0.");
        }
        StatsData data;
        try {
            data = DmzProgression.stats(player);
        } catch (Throwable t) {
            data = null;
        }
        if (data == null) {
            return fail("Could not read that player's race.");
        }
        Character character;
        try {
            character = data.getCharacter();
        } catch (Throwable t) {
            character = null;
        }
        if (character == null) {
            return fail("Could not read that player's race.");
        }
        String race;
        try {
            race = character.getRaceName();
        } catch (Throwable t) {
            race = null;
        }
        if (race == null || race.isBlank()) {
            return fail("Could not read that player's race.");
        }
        List<Candidate> configured = configuredForms(race);
        Candidate chosen = FirstSuperformChoice.select(configured, kind);
        boolean stack = false;
        if (chosen == null && kind == FirstSuperformChoice.Kind.SUPER) {
            Candidate helper = fromHelper(data, race, false);
            // Same rule as the configured scan: only a superform past level 0 counts.
            if (helper != null && helper.unlockLevel >= 1 && FirstSuperformChoice.isSuperform(helper)) {
                chosen = helper;
            }
        }
        if (chosen == null && kind == FirstSuperformChoice.Kind.SUPER) {
            Candidate stackHelper = fromHelper(data, race, true);
            // The helper resolves a stack form; skip it when its config unlock level is 0.
            if (stackHelper != null && stackHelper.unlockLevel >= 1) {
                chosen = stackHelper;
                stack = true;
            }
        }
        if (chosen == null) {
            if (kind == FirstSuperformChoice.Kind.LEGENDARY) {
                return fail("No legendary form is set for race " + race + ".");
            }
            if (kind == FirstSuperformChoice.Kind.GOD) {
                return fail("Race " + race + " has no god form to grant mastery to.");
            }
            return fail("Race has no superform past its level 0 form to grant mastery to.");
        }
        FormMasteries masteries = stack ? character.getStackFormMasteries() : character.getFormMasteries();
        if (masteries == null) {
            return fail("Could not read form mastery for " + race + ".");
        }
        double cap = masteryCap(race, chosen, stack);
        String skill = skillName(chosen, stack);
        SkillFix fix = ensureSkill(data.getSkills(), skill, chosen.unlockLevel);
        if (fix == SkillFix.FAILED) {
            DmzSkillUtil.sync(player);
            return fail("Could not unlock the " + skillLabel(skill) + " skill for " + chosen.label()
                    + ". Ask staff to re-grant after a relog.");
        }
        String skillNote = switch (fix) {
            case UNLOCKED -> "\n" + UltraStyle.CONFIRM + "The " + skillLabel(skill) + " skill is unlocked, so the form"
                    + " can be used now.";
            case REPAIRED -> "\n" + UltraStyle.CONFIRM + "The " + skillLabel(skill) + " skill was broken and has been"
                    + " repaired, so the form can be used now.";
            default -> "";
        };
        double current = masteries.getMastery(chosen.group, chosen.form);
        double target = Math.min(cap, Math.max(current, amount));
        // Exact compare (4.7.32): DMZ's form gate has no epsilon, so 9.999999999999831 is still locked
        // at 10. Any gap, however small, is written as the exact target and read back.
        boolean raised = target > current;
        if (raised) {
            masteries.setMastery(chosen.group, chosen.form, target, cap);
            if (masteries.getMastery(chosen.group, chosen.form) < target) {
                DmzSkillUtil.sync(player);
                return fail("Could not set mastery on " + chosen.label() + " to " + format(target)
                        + ". Ask staff to re-grant after a relog.");
            }
        }
        DmzSkillUtil.sync(player);
        String who = player.m_6302_();
        String label = chosen.label();
        if (!raised) {
            return new Outcome(true,
                    UltraStyle.YELLOW + who + " already has " + UltraStyle.WHITE + format(current)
                            + UltraStyle.YELLOW + " mastery on " + UltraStyle.WHITE + label
                            + UltraStyle.YELLOW + "." + skillNote);
        }
        return new Outcome(true,
                UltraStyle.CONFIRM + "Granted mastery on " + UltraStyle.WHITE + label
                        + UltraStyle.CONFIRM + " (" + race + ") for " + UltraStyle.WHITE + who
                        + UltraStyle.CONFIRM + ". Now " + UltraStyle.WHITE + format(target)
                        + UltraStyle.CONFIRM + "." + skillNote);
    }

    private static Candidate fromHelper(StatsData data, String race, boolean stack) {
        try {
            String group = stack
                    ? TransformationsHelper.getGroupWithFirstAvailableStackForm(data)
                    : TransformationsHelper.getGroupWithFirstAvailableForm(data);
            String form = stack
                    ? TransformationsHelper.getFirstAvailableStackForm(data)
                    : TransformationsHelper.getFirstAvailableForm(data);
            if (group == null || group.isBlank() || form == null || form.isBlank()) {
                return null;
            }
            FormConfig config = stack
                    ? ConfigManager.getStackFormGroup(group)
                    : ConfigManager.getFormGroup(race, group);
            String type = config == null ? "" : config.getFormType();
            int level = 0;
            if (config != null) {
                FormConfig.FormData formData = config.getForm(form);
                if (formData != null && formData.getUnlockOnSkillLevel() != null) {
                    level = formData.getUnlockOnSkillLevel();
                }
            }
            return new Candidate(group, form, type == null ? "" : type, level, stack);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Package-visible for the dev probe. */
    static List<Candidate> configuredForms(String race) {
        List<Candidate> out = new ArrayList<>();
        Map<String, FormConfig> groups = formsForRace(race);
        if (groups == null) {
            return out;
        }
        for (Map.Entry<String, FormConfig> entry : groups.entrySet()) {
            FormConfig group = entry.getValue();
            if (group == null || group.getForms() == null) {
                continue;
            }
            String groupName = group.getGroupName();
            if (groupName == null || groupName.isBlank()) {
                groupName = entry.getKey();
            }
            String type = group.getFormType() == null ? "" : group.getFormType();
            for (Map.Entry<String, FormConfig.FormData> formEntry : group.getForms().entrySet()) {
                FormConfig.FormData form = formEntry.getValue();
                String formName = form != null && form.getName() != null && !form.getName().isBlank()
                        ? form.getName()
                        : formEntry.getKey();
                if (formName == null || formName.isBlank() || groupName == null || groupName.isBlank()) {
                    continue;
                }
                int level = 0;
                if (form != null && form.getUnlockOnSkillLevel() != null) {
                    level = form.getUnlockOnSkillLevel();
                }
                out.add(new Candidate(groupName, formName, type, level, false));
            }
        }
        return out;
    }

    private static Map<String, FormConfig> formsForRace(String race) {
        try {
            Map<String, FormConfig> groups = ConfigManager.getAllFormsForRace(race);
            if (groups != null && !groups.isEmpty()) {
                return groups;
            }
            String lower = race.toLowerCase(Locale.ROOT);
            if (!lower.equals(race)) {
                return ConfigManager.getAllFormsForRace(lower);
            }
            return groups;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** What {@link #ensureSkill} had to do. */
    enum SkillFix {
        /** Already usable; nothing changed. */
        READY,
        /** The skill was absent and is now set. */
        UNLOCKED,
        /** The skill existed but its level or max was too low (e.g. a 0 / 0 record); fixed. */
        REPAIRED,
        /** The level did not stick: the form is still locked. */
        FAILED
    }

    /**
     * Make the form-group skill usable: level at least {@code max(1, unlockLevel)}. When the skill is
     * absent or its max is too low, it is registered first through {@link DmzSkillUtil#ensureRegistered}
     * with the best-known max (live, skills.json, else 10; never below the level). A healthy max is
     * never lowered: DMZ's register overwrites the max of an existing skill, so it only runs when the
     * max is too low. The level is then set and read back. Package-visible for the dev probe.
     */
    static SkillFix ensureSkill(Skills skills, String skill, int unlockLevel) {
        if (skills == null || skill == null || skill.isBlank()) {
            return SkillFix.FAILED;
        }
        int need = Math.max(1, unlockLevel);
        try {
            boolean had = skills.hasSkill(skill);
            if (had && skills.getMaxSkillLevel(skill) >= need && skills.getSkillLevel(skill) >= need) {
                return SkillFix.READY;
            }
            if (!had || skills.getMaxSkillLevel(skill) < need) {
                int max = Math.max(need, DmzSkillUtil.maxLevel(skills, skill, 10));
                DmzSkillUtil.ensureRegistered(skills, skill, max);
            }
            if (skills.getSkillLevel(skill) < need) {
                DmzSkillUtil.setLevel(skills, skill, need);
            }
            if (!skills.hasSkill(skill) || skills.getSkillLevel(skill) < need) {
                return SkillFix.FAILED;
            }
            return had ? SkillFix.REPAIRED : SkillFix.UNLOCKED;
        } catch (Throwable t) {
            return SkillFix.FAILED;
        }
    }

    /** Player-facing name of a form-group skill. */
    static String skillLabel(String skill) {
        String id = skill == null ? "" : skill.toLowerCase(Locale.ROOT);
        return switch (id) {
            case "superforms" -> "Super Forms";
            case "legendaryforms" -> "Legendary Forms";
            case "godforms" -> "God Forms";
            default -> DmzSkillUtil.prettySkillLabel(skill);
        };
    }

    /** Same skill id {@code TransformationReward.grantFormSkill} writes. */
    private static String skillName(Candidate chosen, boolean stack) {
        if (stack) {
            return chosen.formType == null ? "" : chosen.formType.toLowerCase(Locale.ROOT);
        }
        try {
            return TransformationsHelper.getSkillNameForType(chosen.formType);
        } catch (Throwable ignored) {
            return chosen.formType;
        }
    }

    private static double masteryCap(String race, Candidate chosen, boolean stack) {
        try {
            FormConfig.FormData form = stack
                    ? ConfigManager.getStackForm(chosen.group, chosen.form)
                    : ConfigManager.getForm(race, chosen.group, chosen.form);
            if (form != null && form.getMaxMastery() != null && form.getMaxMastery() > 0.0) {
                return form.getMaxMastery();
            }
        } catch (Throwable ignored) {
        }
        return Double.MAX_VALUE;
    }

    private static Outcome fail(String line) {
        return new Outcome(false, UltraStyle.DANGER + line);
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-6 && !Double.isInfinite(value)) {
            return Long.toString((long) Math.rint(value));
        }
        return Double.toString(value);
    }
}
