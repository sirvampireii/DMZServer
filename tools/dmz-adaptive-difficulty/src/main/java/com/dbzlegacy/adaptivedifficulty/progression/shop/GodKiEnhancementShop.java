package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.gui.GuiClickConfirm;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.noea.GodKiWipeHelper;
import com.dbzlegacy.adaptivedifficulty.progression.DmzSkillUtil;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeShopConfig;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.ScreenNotify;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.FormMasteries;
import com.dragonminez.common.stats.skills.Skills;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class GodKiEnhancementShop {
    public static final String ENHANCEMENT_GROUP = "godkienhancement";
    public static final String ENHANCEMENT_FORM = "enhancewithgodki";
    public static final String GOD_FORMS_GROUP = "godforms";
    public static final int MASTERY_PER_POINT = 1000;
    public static final int MAX_INVESTMENTS = 5;
    public static final int INVESTMENT_COST = 1;
    private static final String KEY_INVESTED = "pp_godki_enh";

    private GodKiEnhancementShop() {
    }

    public static int invested(ServerPlayer serverPlayer) {
        return serverPlayer == null ? 0 : Math.max(0, Math.min(GodKiEnhancementShop.maxInvestments(), (int)ProgressionData.storedGetLong((ServerPlayer)serverPlayer, (String)KEY_INVESTED, (long)0L)));
    }

    public static int maxInvestments() {
        return PrestigeShopConfig.get().godKi.maxPoints;
    }

    public static int masteryPerPoint() {
        return PrestigeShopConfig.get().godKi.masteryPerPoint;
    }

    public static int expectedMastery(int n) {
        return Math.max(0, Math.min(GodKiEnhancementShop.maxInvestments(), n)) * GodKiEnhancementShop.masteryPerPoint();
    }

    public static boolean canBuy(ServerPlayer serverPlayer) {
        return serverPlayer != null && PrestigeShopConfig.get().godKi.enabled && GodKiEnhancementShop.invested(serverPlayer) < GodKiEnhancementShop.maxInvestments();
    }

    public static double mastery(ServerPlayer serverPlayer) {
        Character character = GodKiEnhancementShop.character(serverPlayer);
        if (character == null) {
            return 0.0;
        }
        double d = GodKiEnhancementShop.masteryGroup(character.getStackFormMasteries());
        return Math.max(d, GodKiEnhancementShop.masteryGroup(character.getFormMasteries()));
    }

    public static boolean hasEnhancement(ServerPlayer serverPlayer) {
        Skills skills;
        if (serverPlayer == null) {
            return false;
        }
        try {
            skills = DmzSkillUtil.skills((ServerPlayer)serverPlayer);
            if (skills != null && skills.hasSkill(ENHANCEMENT_GROUP)) {
                return true;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        Character character = GodKiEnhancementShop.character(serverPlayer);
        return character == null ? false : GodKiEnhancementShop.masteryRecordPresent(character.getStackFormMasteries()) || GodKiEnhancementShop.masteryRecordPresent(character.getFormMasteries());
    }

    public static boolean canUseCustomGodForms(ServerPlayer serverPlayer) {
        return GodKiEnhancementShop.hasEnhancement(serverPlayer);
    }

    public static boolean canUseTrueGodForm(ServerPlayer serverPlayer) {
        return GodKiEnhancementShop.hasEnhancement(serverPlayer) && GodKiEnhancementShop.mastery(serverPlayer) >= (double)GodKiEnhancementShop.trueFormThreshold();
    }

    public static int trueFormThreshold() {
        return Math.max(1, DifficultyConfig.get().godKiTrueFormMasteryThreshold);
    }

    public static String blockReason(ServerPlayer serverPlayer, String string) {
        if (serverPlayer != null && string != null && !string.isBlank()) {
            String string2 = string.trim();
            if (GOD_FORMS_GROUP.equalsIgnoreCase(string2)) {
                return GodKiEnhancementShop.hasEnhancement(serverPlayer) ? null : "God forms need God Ki - invest in God Ki Enhancement (Prestige shop, Effects).";
            }
            if (ENHANCEMENT_GROUP.equalsIgnoreCase(string2)) {
                if (!GodKiEnhancementShop.hasEnhancement(serverPlayer)) {
                    return "You need God Ki - invest in God Ki Enhancement (Prestige shop, Effects).";
                }
                if (GodKiEnhancementShop.mastery(serverPlayer) < (double)GodKiEnhancementShop.trueFormThreshold()) {
                    return "Your God Ki mastery is too low - invest more in God Ki Enhancement (Prestige shop, Effects).";
                }
            }
            return null;
        }
        return null;
    }

    public static void notifyBlocked(ServerPlayer serverPlayer, String string) {
        if (serverPlayer != null && string != null && !string.isBlank()) {
            ScreenNotify.blocked((ServerPlayer)serverPlayer, (String)"God Ki needed", (String)string, (String)"lm.godki.gate", (long)4000L);
        }
    }

    public static String buy(ServerPlayer serverPlayer) {
        if (serverPlayer == null) {
            return UltraStyle.dangerLine((String)"Players only.");
        }
        int n = GodKiEnhancementShop.invested(serverPlayer);
        int n2 = GodKiEnhancementShop.maxInvestments();
        int n3 = PrestigeShopConfig.get().godKi.costPerPoint;
        double d = GodKiEnhancementShop.masteryPerPoint();
        if (n >= n2) {
            return UltraStyle.noticeLine((String)("God Ki Enhancement is fully invested (" + n + "/" + n2 + ")."));
        }
        if (!PrestigeShopConfig.get().godKi.enabled) {
            return UltraStyle.dangerLine((String)"God Ki Enhancement is not for sale right now.");
        }
        int n4 = PrestigePointsSystem.getPoints(serverPlayer);
        if (n4 < n3) {
            return UltraStyle.dangerLine((String)("Need " + n3 + " favor (have " + n4 + ")."));
        }
        if (!GuiClickConfirm.confirmed((UUID)serverPlayer.m_20148_(), (String)("godki-enh:" + n))) {
            return UltraStyle.noticeLine((String)("Invest " + n3 + " favor in God Ki Enhancement (+" + DmzRewards.formatWhole((double)d) + " mastery, " + (n + 1) + "/" + n2 + ")? Click again within 10 seconds."));
        }
        int n5 = n + 1;
        ProgressionData.storedPut((ServerPlayer)serverPlayer, (String)KEY_INVESTED, (long)n5);
        PrestigePointsSystem.setPoints(serverPlayer, n4 - n3);
        GodKiEnhancementShop.reapply(serverPlayer);
        double d2 = GodKiEnhancementShop.mastery(serverPlayer);
        SystemTelemetry.log((String)"prestige_points", (String)"buy_godki_enhancement", (ServerPlayer)serverPlayer, null, Map.of("invested", n5, "mastery", (long)d2, "points", PrestigePointsSystem.getPoints(serverPlayer)));
        return UltraStyle.color((String)"a", (String)("God Ki Enhancement invested " + n5 + "/" + n2 + " (-" + n3 + " favor)")) + "\n" + UltraStyle.dim((String)("God Ki mastery: " + DmzRewards.formatWhole((double)((long)d2)) + " (+" + DmzRewards.formatWhole((double)d) + " per point)")) + (String)(n5 == 1 ? "\n" + UltraStyle.color((String)"a", (String)"God forms are usable now.") : "") + "\n" + UltraStyle.dim((String)("Favor left: " + PrestigePointsSystem.getPoints(serverPlayer)));
    }

    static void adminSetInvested(ServerPlayer serverPlayer, int n) {
        if (serverPlayer != null) {
            int n2 = Math.max(0, Math.min(GodKiEnhancementShop.maxInvestments(), n));
            int n3 = GodKiEnhancementShop.invested(serverPlayer);
            ProgressionData.storedPut((ServerPlayer)serverPlayer, (String)KEY_INVESTED, (long)n2);
            if (n2 != 0) {
                if (n2 < n3) {
                    GodKiWipeHelper.dropEnhancementMastery((Player)serverPlayer);
                }
                GodKiEnhancementShop.reapply(serverPlayer);
            } else if (n3 > 0 || GodKiEnhancementShop.hasEnhancement(serverPlayer)) {
                GodKiWipeHelper.wipe((Player)serverPlayer, (String)"admin_godki");
            }
        }
    }

    public static void reapply(ServerPlayer serverPlayer) {
        Character character;
        int n;
        if (serverPlayer != null && (n = GodKiEnhancementShop.invested(serverPlayer)) > 0 && (character = GodKiEnhancementShop.character(serverPlayer)) != null) {
            double d = GodKiEnhancementShop.expectedMastery(n);
            boolean bl = false;
            try {
                Skills skills = DmzSkillUtil.skills((ServerPlayer)serverPlayer);
                if (skills != null) {
                    DmzSkillUtil.ensureRegistered((Skills)skills, (String)ENHANCEMENT_GROUP, (int)1);
                    if (DmzSkillUtil.level((Skills)skills, (String)ENHANCEMENT_GROUP) < 1) {
                        DmzSkillUtil.setLevel((Skills)skills, (String)ENHANCEMENT_GROUP, (int)1);
                        bl = true;
                    }
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            bl |= GodKiEnhancementShop.raiseMastery(character.getStackFormMasteries(), d);
            if (bl |= GodKiEnhancementShop.raiseMastery(character.getFormMasteries(), d)) {
                try {
                    DmzSkillUtil.sync((ServerPlayer)serverPlayer);
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }
    }

    private static Character character(ServerPlayer serverPlayer) {
        try {
            return DmzProgression.character((Player)serverPlayer);
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    private static boolean raiseMastery(FormMasteries formMasteries, double d) {
        if (formMasteries != null && !(d <= 0.0)) {
            double d2 = GodKiEnhancementShop.masteryGroup(formMasteries);
            if (d2 >= d) {
                return false;
            }
            formMasteries.setMastery(ENHANCEMENT_GROUP, ENHANCEMENT_FORM, d, d);
            return true;
        }
        return false;
    }

    private static double masteryGroup(FormMasteries formMasteries) {
        double d = 0.0;
        for (String string : GodKiEnhancementShop.masteryForms(formMasteries)) {
            d = Math.max(d, formMasteries.getMastery(ENHANCEMENT_GROUP, string));
        }
        return d;
    }

    private static boolean masteryRecordPresent(FormMasteries formMasteries) {
        return !GodKiEnhancementShop.masteryForms(formMasteries).isEmpty();
    }

    private static List<String> masteryForms(FormMasteries formMasteries) {
        ArrayList<String> arrayList = new ArrayList<String>();
        if (formMasteries == null) {
            return arrayList;
        }
        try {
            CompoundTag compoundTag = formMasteries.save();
            if (compoundTag == null) {
                return arrayList;
            }
            String string = "godkienhancement:";
            for (String string2 : compoundTag.m_128431_()) {
                String string3;
                if (string2 == null || string2.length() <= string.length() || !string2.regionMatches(true, 0, string, 0, string.length()) || (string3 = string2.substring(string.length()).trim()).isEmpty()) continue;
                arrayList.add(string3.toLowerCase(Locale.ROOT));
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return arrayList;
    }
}
