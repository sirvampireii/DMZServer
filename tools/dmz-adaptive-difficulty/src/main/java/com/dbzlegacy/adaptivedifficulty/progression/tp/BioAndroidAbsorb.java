package com.dbzlegacy.adaptivedifficulty.progression.tp;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.progression.DmzSkillUtil;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionConfig;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.SkillsConfig;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.character.Cooldowns;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dragonminez.common.stats.character.Status;
import com.dragonminez.common.stats.skills.Skills;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Port of BioAndroid.js — absorb TP / learn skills while DrainActive.
 */
public final class BioAndroidAbsorb {
    private BioAndroidAbsorb() {}

    public static void pulse(ServerPlayer player, long nowMs) {
        if (!ProgressionConfig.bioAndroid() || player == null) {
            return;
        }
        if (nowMs < ProgressionData.tempGetLong(player, "bioandroid_next_check", 0L)) {
            return;
        }
        ProgressionData.tempPut(player, "bioandroid_next_check", nowMs + 250L);
        try {
            StatsData bioData = DmzProgression.stats(player);
            if (bioData == null) {
                return;
            }
            Character bioChar = bioData.getCharacter();
            if (bioChar == null) {
                return;
            }
            String race = String.valueOf(bioChar.getRace()).toLowerCase();
            if (!"bioandroid".equals(race)) {
                return;
            }
            Cooldowns cds = bioData.getCooldowns();
            if (cds == null || !cds.hasCooldown(Cooldowns.DRAIN_ACTIVE)) {
                ProgressionData.tempRemove(player, "bioandroid_absorb_processed_target");
                return;
            }
            Status status = bioData.getStatus();
            if (status == null) {
                return;
            }
            int targetId = status.getDrainingTargetId();
            if (targetId <= 0) {
                return;
            }
            String last = ProgressionData.tempGet(player, "bioandroid_absorb_processed_target", "");
            if (String.valueOf(targetId).equals(last)) {
                return;
            }
            ProgressionData.tempPut(player, "bioandroid_absorb_processed_target", String.valueOf(targetId));

            Entity target = findEntity(player, targetId);
            if (!(target instanceof LivingEntity living)) {
                return;
            }
            // 4.7.50: absorbing pays no TP. Until then an NPC paid 10% of its max HP (a 15B-HP target
            // paid 1.5B), a player lost 10% of their TP to the Bio, and a player with nothing to learn
            // paid 5% of their max HP. Only the skill copy below is left.
            if (!(living instanceof ServerPlayer targetPlayer)) {
                return;
            }
            StatsData targetData = DmzProgression.stats(targetPlayer);
            if (targetData == null) {
                return;
            }
            Skills bioSkills = bioData.getSkills();
            Skills targetSkills = targetData.getSkills();
            if (bioSkills == null || targetSkills == null) {
                return;
            }
            List<String> possible = learnableSkills(bioSkills, targetSkills);
            if (possible.isEmpty()) {
                return;
            }
            int bioLvl = bioData.getLevel();
            int targetLvl = targetData.getLevel();
            double chance = 0.50;
            if (targetLvl > 0) {
                chance = 0.50 + ((bioLvl - targetLvl) / (targetLvl * 2.0));
            }
            chance = Math.max(0.05, Math.min(0.95, chance));
            if (ThreadLocalRandom.current().nextDouble() > chance) {
                return;
            }
            String picked = possible.get(ThreadLocalRandom.current().nextInt(possible.size()));
            int newLevel = Math.min(
                    DmzSkillUtil.level(bioSkills, picked) + 1,
                    DmzSkillUtil.level(targetSkills, picked)
            );
            DmzSkillUtil.setLevel(bioSkills, picked, newLevel);
            DmzSkillUtil.sync(player);
            DmzRewards.msg(player, UltraStyle.LIGHT_PURPLE + "[Absorb] You learned " + UltraStyle.AQUA
                    + skillName(picked) + UltraStyle.LIGHT_PURPLE + " level " + newLevel + " from "
                    + targetPlayer.m_7755_().getString() + ".");
            SystemTelemetry.log("progression", "bio_absorb_skill", player, targetPlayer,
                    Map.of("skill", picked, "level", newLevel));
        } catch (Throwable ignored) {
        }
    }

    /** A skill id as players read it: "ki_control" becomes "Ki Control". */
    private static String skillName(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.replace('_', ' ').replace('-', ' ').trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(java.lang.Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.length() == 0 ? id : out.toString();
    }

    private static List<String> learnableSkills(Skills bioSkills, Skills targetSkills) {
        List<String> out = new ArrayList<>();
        try {
            SkillsConfig cfg = ConfigManager.getSkillsConfig();
            List<String> formSkills = cfg == null ? List.of() : cfg.getFormSkills();
            List<String> stackSkills = cfg == null ? List.of() : cfg.getStackSkills();
            for (String skillName : targetSkills.getAllSkills().keySet()) {
                if (formSkills != null && formSkills.contains(skillName)) {
                    continue;
                }
                if (stackSkills != null && stackSkills.contains(skillName)) {
                    continue;
                }
                if (DmzSkillUtil.level(targetSkills, skillName) > DmzSkillUtil.level(bioSkills, skillName)) {
                    out.add(skillName);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static Entity findEntity(ServerPlayer player, int entityId) {
        try {
            ServerLevel level = player.m_284548_();
            if (level == null) {
                return null;
            }
            return level.m_6815_(entityId);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
