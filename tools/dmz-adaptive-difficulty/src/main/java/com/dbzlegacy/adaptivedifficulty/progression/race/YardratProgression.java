package com.dbzlegacy.adaptivedifficulty.progression.race;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.progression.DmzSkillUtil;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionConfig;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeCharacter;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.FormMasteries;
import com.dragonminez.common.stats.skills.Skills;
import net.minecraft.server.level.ServerPlayer;

/** Port of Yardrat.js — form mastery double-gain + starter ki skills. */
public final class YardratProgression {
    private static final String RACE = "yardrat";
    /** Live skills.json IDs (script used underscores — those IDs do not exist). */
    private static final String SKILL_ONE = "kimanipulation";
    private static final String SKILL_TWO = "kicontrol";

    private YardratProgression() {}

    public static void pulse(ServerPlayer player, long nowMs) {
        if (!ProgressionConfig.yardrat() || player == null) {
            return;
        }
        if (nowMs < ProgressionData.tempGetLong(player, "yardrat_next_check", 0L)) {
            return;
        }
        ProgressionData.tempPut(player, "yardrat_next_check", nowMs + 1000L);
        try {
            masteryTick(player);
        } catch (Throwable ignored) {
        }
        try {
            skillsTick(player);
        } catch (Throwable ignored) {
        }
    }

    private static void masteryTick(ServerPlayer player) {
        StatsData data = DmzProgression.stats(player);
        Character ch = data == null ? null : data.getCharacter();
        if (ch == null) {
            return;
        }
        String race = String.valueOf(ch.getRace());
        if (race == null || !RACE.equalsIgnoreCase(race)) {
            return;
        }
        boolean changed = false;
        if (ch.hasActiveForm()) {
            String group = String.valueOf(ch.getActiveFormGroup());
            String form = String.valueOf(ch.getActiveForm());
            if (group != null && form != null && !group.isBlank() && !form.isBlank()) {
                FormMasteries masteries = ch.getFormMasteries();
                double current = masteries.getMastery(group, form);
                String key = "yardrat_form_mastery_" + group.toLowerCase() + "_" + form.toLowerCase();
                double last = ProgressionData.tempGetDouble(player, key, current);
                if (current > last) {
                    double gained = current - last;
                    double max = 100.0;
                    try {
                        FormConfig.FormData formData =
                                ConfigManager.getForm(ch.getRaceName(), group, form);
                        if (formData != null && formData.getMaxMastery() != null) {
                            max = formData.getMaxMastery();
                        }
                    } catch (Throwable ignored) {
                    }
                    masteries.addMastery(group, form, gained, max);
                    current = masteries.getMastery(group, form);
                    changed = true;
                }
                ProgressionData.tempPut(player, key, current);
            }
        }
        if (ch.hasActiveStackForm()) {
            String sGroup = String.valueOf(ch.getActiveStackFormGroup());
            String sForm = String.valueOf(ch.getActiveStackForm());
            if (sGroup != null && sForm != null && !sGroup.isBlank() && !sForm.isBlank()) {
                FormMasteries stackMasteries = ch.getStackFormMasteries();
                double sCurrent = stackMasteries.getMastery(sGroup, sForm);
                String sKey = "yardrat_stack_mastery_" + sGroup.toLowerCase() + "_" + sForm.toLowerCase();
                double sLast = ProgressionData.tempGetDouble(player, sKey, sCurrent);
                if (sCurrent > sLast) {
                    double sGained = sCurrent - sLast;
                    double sMax = 100.0;
                    try {
                        FormConfig.FormData stackData = ConfigManager.getStackForm(sGroup, sForm);
                        if (stackData != null && stackData.getMaxMastery() != null) {
                            sMax = stackData.getMaxMastery();
                        }
                    } catch (Throwable ignored) {
                    }
                    stackMasteries.addMastery(sGroup, sForm, sGained, sMax);
                    sCurrent = stackMasteries.getMastery(sGroup, sForm);
                    changed = true;
                }
                ProgressionData.tempPut(player, sKey, sCurrent);
            }
        }
        if (changed) {
            DmzSkillUtil.sync(player);
        }
    }

    /**
     * 4.7.53: the "starting Ki skills granted" mark is per character ({@link PrestigeCharacter#id}). It was per
     * account for the session, so after granting on a Yardrat slot, switching to another race's slot set that
     * slot's Ki Manipulation and Ki Control to 0. Now only the character that got them loses them, when its
     * race stops being Yardrat.
     */
    static String grantedKey(ServerPlayer player) {
        return "yardrat_starting_ki_skills_granted:" + PrestigeCharacter.id(player);
    }

    private static void skillsTick(ServerPlayer player) {
        StatsData data = DmzProgression.stats(player);
        Character ch = data == null ? null : data.getCharacter();
        Skills skills = data == null ? null : data.getSkills();
        if (ch == null || skills == null) {
            return;
        }
        String race = String.valueOf(ch.getRace()).toLowerCase();
        boolean changed = false;
        if (RACE.equals(race)) {
            if (DmzSkillUtil.level(skills, SKILL_ONE) < 5) {
                DmzSkillUtil.setLevel(skills, SKILL_ONE, 5);
                changed = true;
            }
            if (DmzSkillUtil.level(skills, SKILL_TWO) < 5) {
                DmzSkillUtil.setLevel(skills, SKILL_TWO, 5);
                changed = true;
            }
            if (changed) {
                ProgressionData.tempPut(player, grantedKey(player), "1");
                DmzSkillUtil.sync(player);
            }
        } else if (ProgressionData.tempHas(player, grantedKey(player))) {
            if (DmzSkillUtil.level(skills, SKILL_ONE) > 0) {
                DmzSkillUtil.setLevel(skills, SKILL_ONE, 0);
                changed = true;
            }
            if (DmzSkillUtil.level(skills, SKILL_TWO) > 0) {
                DmzSkillUtil.setLevel(skills, SKILL_TWO, 0);
                changed = true;
            }
            ProgressionData.tempRemove(player, grantedKey(player));
            if (changed) {
                DmzSkillUtil.sync(player);
            }
        }
    }
}
