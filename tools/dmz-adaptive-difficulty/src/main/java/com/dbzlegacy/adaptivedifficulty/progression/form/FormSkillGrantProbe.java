package com.dbzlegacy.adaptivedifficulty.progression.form;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.extras.FormMasteries;
import com.dragonminez.common.stats.skills.Skills;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * Dev-only probe (JVM flag {@code -Dlm.probe.formSkillGrant=true}; never runs otherwise) for 4.7.30:
 * the grant-first-* form skill must end up usable. Checks, on DMZ's real {@code Skills}: an absent
 * godforms skill is unlocked with a usable max; a 0 / 0 superforms record (what raw
 * {@code setSkillLevel} leaves) is repaired; a healthy max is not lowered; a skill that will not take
 * a level fails. Then a fake Seiun-jin player gets grant-first-superform from scratch, and again with
 * the skill broken back to 0 / 0 and the mastery already there. 4.7.32: mastery hair
 * (9.999999999999831) with a broken skill is topped up to exactly 10.0 and repaired in one grant, and a
 * grant on the exact 10.0 reports "already has".
 */
public final class FormSkillGrantProbe {
    private static final UUID PROBE_ID = UUID.fromString("6c6d2d70-726f-6265-2d66-6f726d736b6c");

    private FormSkillGrantProbe() {}

    public static void runIfRequested(MinecraftServer server) {
        if (server == null || !Boolean.getBoolean("lm.probe.formSkillGrant")) {
            return;
        }
        try {
            Skills fresh = new Skills();
            check("absent godforms -> unlocked, level 1, usable max",
                    FirstSuperformMastery.ensureSkill(fresh, "godforms", 1) == FirstSuperformMastery.SkillFix.UNLOCKED
                            && fresh.getSkillLevel("godforms") >= 1 && fresh.getMaxSkillLevel("godforms") >= 1,
                    fresh, "godforms");

            Skills broken = new Skills();
            broken.setSkillLevel("superforms", 0);
            log("raw setSkillLevel left superforms at", broken, "superforms");
            check("0/0 superforms -> repaired, level 1, max 10",
                    FirstSuperformMastery.ensureSkill(broken, "superforms", 1) == FirstSuperformMastery.SkillFix.REPAIRED
                            && broken.getSkillLevel("superforms") == 1 && broken.getMaxSkillLevel("superforms") >= 10,
                    broken, "superforms");

            Skills healthy = new Skills();
            healthy.registerDefaultSkill("superforms", 8);
            healthy.setSkillLevel("superforms", 3);
            check("healthy superforms 3/8 -> ready, max and level unchanged",
                    FirstSuperformMastery.ensureSkill(healthy, "superforms", 1) == FirstSuperformMastery.SkillFix.READY
                            && healthy.getSkillLevel("superforms") == 3 && healthy.getMaxSkillLevel("superforms") == 8,
                    healthy, "superforms");

            Skills stuck = new Skills() {
                @Override
                public void registerDefaultSkill(String name, int max) {
                }

                @Override
                public void setSkillLevel(String name, int level) {
                }
            };
            check("skill that will not take a level -> failed",
                    FirstSuperformMastery.ensureSkill(stuck, "superforms", 1) == FirstSuperformMastery.SkillFix.FAILED,
                    stuck, "superforms");

            FakePlayer fp = FakePlayerFactory.get(server.m_129783_(), new GameProfile(PROBE_ID, "lm_formskill_probe"));
            StatsData data = DmzProgression.stats(fp);
            if (data == null || data.getCharacter() == null || data.getSkills() == null) {
                AdaptiveDifficultyMod.LOGGER.info("[{}] form skill probe: fake player has no DMZ stats, grant steps skipped",
                        AdaptiveDifficultyMod.MOD_ID);
                return;
            }
            data.getCharacter().setRace("seiunjin");
            data.getSkills().removeSkill("superforms");
            FirstSuperformMastery.Outcome first = FirstSuperformMastery.grant(fp, 10.0, FirstSuperformChoice.Kind.SUPER);
            reply("grant-first-superform, no skill", first);
            check("fresh grant: ok, skill unlocked, usable",
                    first.ok && first.message.contains("is unlocked") && data.getSkills().getSkillLevel("superforms") >= 1
                            && data.getSkills().getMaxSkillLevel("superforms") >= 1,
                    data.getSkills(), "superforms");

            data.getSkills().removeSkill("superforms");
            data.getSkills().setSkillLevel("superforms", 0);
            log("skill broken back to", data.getSkills(), "superforms");
            FirstSuperformMastery.Outcome again = FirstSuperformMastery.grant(fp, 10.0, FirstSuperformChoice.Kind.SUPER);
            reply("grant-first-superform, mastery 10, skill 0/0", again);
            check("re-grant on already-has: ok, says repaired, usable",
                    again.ok && again.message.contains("already has") && again.message.contains("repaired")
                            && data.getSkills().getSkillLevel("superforms") >= 1
                            && data.getSkills().getMaxSkillLevel("superforms") >= 1,
                    data.getSkills(), "superforms");

            // 4.7.32: fighting-gain hair below the target, skill broken again. One grant fixes both.
            FirstSuperformChoice.Candidate form = FirstSuperformChoice.select(
                    FirstSuperformMastery.configuredForms("seiunjin"), FirstSuperformChoice.Kind.SUPER);
            if (form == null) {
                AdaptiveDifficultyMod.LOGGER.info("[{}] form skill probe: no Seiun-jin superform, hair steps skipped",
                        AdaptiveDifficultyMod.MOD_ID);
                return;
            }
            FormMasteries masteries = data.getCharacter().getFormMasteries();
            masteries.setMastery(form.group, form.form, 9.999999999999831, Double.MAX_VALUE);
            data.getSkills().removeSkill("superforms");
            data.getSkills().setSkillLevel("superforms", 0);
            FirstSuperformMastery.Outcome hair = FirstSuperformMastery.grant(fp, 10.0, FirstSuperformChoice.Kind.SUPER);
            reply("grant-first-superform, mastery 9.999999999999831, skill 0/0", hair);
            double topped = masteries.getMastery(form.group, form.form);
            check("hair grant: granted (not already has), stored exactly 10.0, skill repaired [stored " + topped + "]",
                    hair.ok && hair.message.contains("Granted mastery") && !hair.message.contains("already has")
                            && hair.message.contains("repaired") && topped == 10.0
                            && data.getSkills().getSkillLevel("superforms") >= 1,
                    data.getSkills(), "superforms");
            FirstSuperformMastery.Outcome exact = FirstSuperformMastery.grant(fp, 10.0, FirstSuperformChoice.Kind.SUPER);
            reply("grant-first-superform, mastery exactly 10.0", exact);
            check("exact grant: already has, still 10.0",
                    exact.ok && exact.message.contains("already has")
                            && masteries.getMastery(form.group, form.form) == 10.0,
                    data.getSkills(), "superforms");
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] form skill probe failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString(), t);
        }
    }

    private static void check(String what, boolean ok, Skills skills, String skill) {
        AdaptiveDifficultyMod.LOGGER.info("[{}] form skill probe check {}: {} (level {}, max {})",
                AdaptiveDifficultyMod.MOD_ID, what, ok ? "OK" : "WRONG",
                skills.getSkillLevel(skill), skills.getMaxSkillLevel(skill));
    }

    private static void log(String step, Skills skills, String skill) {
        AdaptiveDifficultyMod.LOGGER.info("[{}] form skill probe {}: has {}, level {}, max {}",
                AdaptiveDifficultyMod.MOD_ID, step, skills.hasSkill(skill), skills.getSkillLevel(skill),
                skills.getMaxSkillLevel(skill));
    }

    private static void reply(String step, FirstSuperformMastery.Outcome outcome) {
        AdaptiveDifficultyMod.LOGGER.info("[{}] form skill probe {} -> ok={} {}", AdaptiveDifficultyMod.MOD_ID, step,
                outcome.ok, outcome.message == null ? "" : outcome.message.replaceAll("§.", "").replace('\n', ' '));
    }
}
