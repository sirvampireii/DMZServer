package com.dbzlegacy.adaptivedifficulty.progression;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore;
import com.dbzlegacy.adaptivedifficulty.progression.shop.GodKiEnhancementShop;
import com.dbzlegacy.adaptivedifficulty.progression.shop.MasteryWallet;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * Dev-only probe (JVM flag {@code -Dlm.probe.godKiWallet=true}; never runs otherwise) for 4.7.21: a fake
 * player with 3 God Ki levels bought on the old Effects page and no refund marker gets the one-time
 * refund through {@code reapply()}, a second {@code reapply()} refunds nothing, then wallet levels are
 * bought with points (one level per 10-point apply, a 5-point apply refused) and a later
 * {@code reapply()} keeps them. 4.7.22: the wallet-first order (3 Effects levels, first action a wallet
 * level) refunds first, then stores level 1, and a second level refunds nothing.
 */
public final class GodKiWalletProbe {
    private static final UUID PROBE_ID = UUID.fromString("6c6d2d70-726f-6265-2d67-6f646b692d77");

    private GodKiWalletProbe() {}

    public static void runIfRequested(MinecraftServer server) {
        if (server == null || !Boolean.getBoolean("lm.probe.godKiWallet")) {
            return;
        }
        try {
            FakePlayer fp = FakePlayerFactory.get(server.m_129783_(), new GameProfile(PROBE_ID, "lm_godki_probe"));
            ProgressionData.storedRemove(fp, "pp_godki_wallet_refund");
            ProgressionData.storedPut(fp, "pp_godki_enh", 3L);
            PrestigePointsSystem.setPoints(fp, 2);
            log("start (3 Effects levels, 2 favor, no marker)", fp);
            GodKiEnhancementShop.reapply(fp);
            log("after first reapply (refund)", fp);
            GodKiEnhancementShop.reapply(fp);
            log("after second reapply (no second refund)", fp);
            LmPlayerStore.putLong(fp, MasteryWallet.KEY, 25L);
            String token = "s:" + GodKiEnhancementShop.ENHANCEMENT_GROUP + ":" + GodKiEnhancementShop.ENHANCEMENT_FORM;
            reply("apply 5 points", MasteryWallet.apply(fp, token + ":5"));
            log("after 5-point apply", fp);
            reply("apply 10 points", MasteryWallet.apply(fp, token + ":10"));
            log("after first level", fp);
            reply("apply 10 points", MasteryWallet.apply(fp, token + ":10"));
            log("after second level", fp);
            GodKiEnhancementShop.reapply(fp);
            log("after reapply with wallet levels", fp);
            reply("old Effects buy", GodKiEnhancementShop.buy(fp));
            log("after old Effects buy", fp);
            // 4.7.22: wallet-first order - the first action after the update is a wallet level.
            ProgressionData.storedRemove(fp, "pp_godki_wallet_refund");
            ProgressionData.storedPut(fp, "pp_godki_enh", 3L);
            PrestigePointsSystem.setPoints(fp, 2);
            log("wallet-first start (3 Effects levels, 2 favor, no marker)", fp);
            int first = GodKiEnhancementShop.levelUpFromWallet(fp);
            log("after first levelUpFromWallet", fp);
            check("wallet-first refund: +3 favor, level 1, 1000 mastery", first == 1
                    && PrestigePointsSystem.getPoints(fp) == 5
                    && ProgressionData.storedGetLong(fp, "pp_godki_enh", -1L) == 1L
                    && (long) GodKiEnhancementShop.mastery(fp) == 1000L);
            int second = GodKiEnhancementShop.levelUpFromWallet(fp);
            log("after second levelUpFromWallet", fp);
            check("second levelUpFromWallet: no second refund, level 2", second == 2
                    && PrestigePointsSystem.getPoints(fp) == 5
                    && ProgressionData.storedGetLong(fp, "pp_godki_enh", -1L) == 2L);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] god ki wallet probe failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString(), t);
        }
    }

    private static void check(String what, boolean ok) {
        AdaptiveDifficultyMod.LOGGER.info("[{}] god ki wallet probe check {}: {}", AdaptiveDifficultyMod.MOD_ID, what,
                ok ? "OK" : "WRONG");
    }

    private static void reply(String step, String msg) {
        AdaptiveDifficultyMod.LOGGER.info("[{}] god ki wallet probe {} -> {}", AdaptiveDifficultyMod.MOD_ID, step,
                msg == null ? "" : msg.replaceAll("§.", "").replace('\n', ' '));
    }

    private static void log(String step, FakePlayer fp) {
        AdaptiveDifficultyMod.LOGGER.info(
                "[{}] god ki wallet probe {}: level {}, favor {}, points {}, mastery {}, enhancement {}, marker {}",
                AdaptiveDifficultyMod.MOD_ID, step, ProgressionData.storedGetLong(fp, "pp_godki_enh", -1L),
                PrestigePointsSystem.getPoints(fp), MasteryWallet.balance(fp), (long) GodKiEnhancementShop.mastery(fp),
                GodKiEnhancementShop.hasEnhancement(fp), ProgressionData.storedGetBool(fp, "pp_godki_wallet_refund"));
    }
}
