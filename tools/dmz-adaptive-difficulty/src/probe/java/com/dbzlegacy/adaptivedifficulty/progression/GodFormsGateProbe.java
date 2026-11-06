package com.dbzlegacy.adaptivedifficulty.progression;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.progression.shop.GodKiEnhancementShop;
import com.dbzlegacy.adaptivedifficulty.progression.shop.KiMasteryTrack;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.FormMasteries;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * Test-only probe (4.7.37; ships only in the testprobe jar, {@code -Dlm.probe.godForms}): the
 * {@code godforms} transform gate opens on 1,000 natural {@code godki:godki} stack mastery with no
 * wallet level, stays shut at 999 with the new message, and a wallet Lv1 player still passes. The
 * {@code godkienhancement} group's messages are unchanged. Logs one "god forms gate probe" line per
 * check and a final OK / FAILED line.
 */
public final class GodFormsGateProbe {
    private static final UUID PROBE_ID = UUID.fromString("6c6d2d70-726f-6265-2d67-6f6466726d73");
    private static final String NEW_MESSAGE = "God forms need 1000 God Ki mastery - fight in God Ki form to raise it,"
            + " or add a God Ki Mastery level in the Mastery wallet (Prestige shop).";
    private static final String ENHANCEMENT_MESSAGE =
            "You need God Ki - add a God Ki Mastery level in the Mastery wallet (Prestige shop).";
    private static int failed;

    private GodFormsGateProbe() {}

    public static void run(MinecraftServer server) {
        failed = 0;
        try {
            FakePlayer fp = FakePlayerFactory.get(server.m_129783_(), new GameProfile(PROBE_ID, "lm_godforms_probe"));
            Character character = DmzProgression.character(fp);
            check("DMZ character attached to the probe player", character != null);
            if (character == null) {
                return;
            }
            FormMasteries stack = character.getStackFormMasteries();
            String group = GodKiEnhancementShop.GOD_FORMS_GROUP;

            state(fp, stack, 0L, 999.0);
            String at999 = GodKiEnhancementShop.blockReason(fp, group);
            log("mastery 999, wallet 0", fp, at999);
            check("mastery 999, wallet 0: blocked with the new message", NEW_MESSAGE.equals(at999));

            state(fp, stack, 0L, 1000.0);
            String at1000 = GodKiEnhancementShop.blockReason(fp, group);
            log("mastery 1000, wallet 0", fp, at1000);
            check("mastery 1000, wallet 0: passes, through the mastery read (no enhancement flag)",
                    at1000 == null && !GodKiEnhancementShop.hasEnhancement(fp));
            check("godkienhancement group unchanged: mastery alone still gets its old message",
                    ENHANCEMENT_MESSAGE.equals(GodKiEnhancementShop.blockReason(fp, GodKiEnhancementShop.ENHANCEMENT_GROUP)));

            state(fp, stack, 1L, 0.0);
            check("wallet Lv1 with no mastery: the wallet check alone passes (back-compat)",
                    GodKiEnhancementShop.hasEnhancement(fp));
            KiMasteryTrack.GOD_KI.reapply(fp);
            String wallet = GodKiEnhancementShop.blockReason(fp, group);
            log("wallet 1 after reapply", fp, wallet);
            check("wallet Lv1: passes, and reapply raised godki:godki to 1000", wallet == null
                    && KiMasteryTrack.GOD_KI.formMastery(fp) >= 1000.0);

            state(fp, stack, 0L, 0.0);
        } catch (Throwable t) {
            failed++;
            AdaptiveDifficultyMod.LOGGER.warn("[{}] god forms gate probe threw: {}", AdaptiveDifficultyMod.MOD_ID, t.toString(), t);
        }
        AdaptiveDifficultyMod.LOGGER.info("[{}] god forms gate probe {}", AdaptiveDifficultyMod.MOD_ID,
                failed == 0 ? "OK" : "FAILED: " + failed);
    }

    /** Wallet level and godki:godki mastery exactly as given; no other stack mastery, no skill. */
    private static void state(FakePlayer fp, FormMasteries stack, long walletLevel, double mastery) {
        ProgressionData.storedPut(fp, KiMasteryTrack.GOD_KI.key, walletLevel);
        stack.clear();
        if (mastery > 0) {
            stack.setMastery(KiMasteryTrack.GROUP, KiMasteryTrack.GOD_KI.form, mastery, 10_000.0);
        }
    }

    private static void check(String what, boolean ok) {
        if (!ok) {
            failed++;
        }
        AdaptiveDifficultyMod.LOGGER.info("[{}] god forms gate probe check {}: {}", AdaptiveDifficultyMod.MOD_ID, what,
                ok ? "OK" : "WRONG");
    }

    private static void log(String step, FakePlayer fp, String reason) {
        AdaptiveDifficultyMod.LOGGER.info("[{}] god forms gate probe {}: wallet {}, godki mastery {}, enhancement {}, reason {}",
                AdaptiveDifficultyMod.MOD_ID, step, KiMasteryTrack.GOD_KI.level(fp), KiMasteryTrack.GOD_KI.formMastery(fp),
                GodKiEnhancementShop.hasEnhancement(fp), reason == null ? "(allowed)" : reason);
    }
}
