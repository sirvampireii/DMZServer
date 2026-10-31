package com.dbzlegacy.adaptivedifficulty.progression;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.dragonminez.common.stats.techniques.TechniqueData;
import com.dragonminez.common.stats.techniques.Techniques;
import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * Dev-only probe (JVM flag {@code -Dlm.probe.questTechnique=true}; never runs otherwise) for 4.7.19:
 * grants skills through DMZ's real {@code StatsData.grantSkillLevel} (the quest SKILL reward path) on a
 * fake player and logs whether the technique was unlocked, that a pure skill adds none, and that a
 * re-grant keeps the owned technique and its experience.
 */
public final class QuestTechniqueProbe {
    private static final UUID PROBE_ID = UUID.fromString("6c6d2d70-726f-6265-2d71-7565732d7465");

    private QuestTechniqueProbe() {}

    public static void runIfRequested(MinecraftServer server) {
        if (server == null || !Boolean.getBoolean("lm.probe.questTechnique")) {
            return;
        }
        try {
            FakePlayer fp = FakePlayerFactory.get(server.m_129783_(), new GameProfile(PROBE_ID, "lm_tech_probe"));
            StatsData data = DmzProgression.stats(fp);
            Techniques base = data.getBaseTechniques();
            for (Map<String, ? extends TechniqueData> registry : java.util.List.of(PredefinedTechniques.REGISTRY,
                    PredefinedTechniques.STRIKE_REGISTRY, PredefinedTechniques.EVASION_REGISTRY)) {
                String id = registry.keySet().stream().filter(k -> !base.getUnlockedTechniques().containsKey(k))
                        .findFirst().orElse(null);
                if (id == null) {
                    continue;
                }
                int before = base.getUnlockedTechniques().size();
                data.grantSkillLevel(id, 1);
                boolean unlocked = base.getUnlockedTechniques().containsKey(id);
                log("grant " + id + " (0 -> 1): unlocked=" + unlocked + ", techniques " + before + " -> "
                        + base.getUnlockedTechniques().size());
                TechniqueData owned = base.getUnlockedTechniques().get(id);
                if (owned != null) {
                    owned.setExperience(777);
                    data.getBaseSkills().setSkillLevel(id, 0);
                    data.grantSkillLevel(id, 1);
                    TechniqueData after = base.getUnlockedTechniques().get(id);
                    log("re-grant " + id + " while owned: same entry=" + (after == owned) + ", experience="
                            + (after == null ? -1 : after.getExperience()) + ", techniques " + base.getUnlockedTechniques().size());
                }
            }
            int before = base.getUnlockedTechniques().size();
            data.grantSkillLevel("kicontrol", 1);
            log("grant kicontrol (pure skill): skill level " + data.getBaseSkills().getSkillLevel("kicontrol")
                    + ", techniques " + before + " -> " + base.getUnlockedTechniques().size());
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] technique probe failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString(), t);
        }
    }

    private static void log(String line) {
        AdaptiveDifficultyMod.LOGGER.info("[{}] technique probe {}", AdaptiveDifficultyMod.MOD_ID, line);
    }
}
