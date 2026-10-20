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
 * the race's first superform, or its first legendary form.
 *
 * <p>The first superform is the earliest superform with unlock level 1 or
 * higher. Level-0 forms such as Oozaru for Saiyans are skipped, so a race
 * whose only superform sits at level 0 gets no grant.
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
            return fail("Race has no superform past its level 0 form to grant mastery to.");
        }
        FormMasteries masteries = stack ? character.getStackFormMasteries() : character.getFormMasteries();
        if (masteries == null) {
            return fail("Could not read form mastery for " + race + ".");
        }
        double cap = masteryCap(race, chosen, stack);
        ensureSkill(data, chosen, stack);
        double current = masteries.getMastery(chosen.group, chosen.form);
        double target = Math.min(cap, Math.max(current, amount));
        boolean raised = target > current + 1.0E-9;
        if (raised) {
            masteries.setMastery(chosen.group, chosen.form, target, cap);
        }
        DmzSkillUtil.sync(player);
        String who = player.m_6302_();
        String label = chosen.label();
        if (!raised) {
            return new Outcome(true,
                    UltraStyle.YELLOW + who + " already has " + UltraStyle.WHITE + format(current)
                            + UltraStyle.YELLOW + " mastery on " + UltraStyle.WHITE + label
                            + UltraStyle.YELLOW + ".");
        }
        return new Outcome(true,
                UltraStyle.CONFIRM + "Granted mastery on " + UltraStyle.WHITE + label
                        + UltraStyle.CONFIRM + " (" + race + ") for " + UltraStyle.WHITE + who
                        + UltraStyle.CONFIRM + ". Now " + UltraStyle.WHITE + format(target)
                        + UltraStyle.CONFIRM + ".");
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

    private static List<Candidate> configuredForms(String race) {
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

    private static void ensureSkill(StatsData data, Candidate chosen, boolean stack) {
        try {
            String skill = skillName(chosen, stack);
            if (skill == null || skill.isBlank()) {
                return;
            }
            Skills skills = data.getSkills();
            if (skills == null) {
                return;
            }
            int need = Math.max(0, chosen.unlockLevel);
            boolean missing = !skills.hasSkill(skill) || skills.getSkillLevel(skill) < need;
            if (missing) {
                skills.setSkillLevel(skill, need);
            }
        } catch (Throwable ignored) {
        }
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
