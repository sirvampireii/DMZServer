package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.data.PlayerDifficultyData;
import com.dbzlegacy.adaptivedifficulty.gui.GuiClickConfirm;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.progression.DmzSkillUtil;
import com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulPrestigeIntegration;
import com.dbzlegacy.adaptivedifficulty.progression.PersonalLevelCapMirror;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampPrestigeBridge;
import com.dbzlegacy.adaptivedifficulty.progression.bridge.OverhaulPrestigeResourceScale;
import com.dbzlegacy.adaptivedifficulty.progression.shop.GodKiEnhancementShop;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeCustomShop;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeShopConfig;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeSystem;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockSystem;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockTier;
import com.dbzlegacy.adaptivedifficulty.title.TitleSystem;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Effects;
import com.dragonminez.common.stats.skills.Skills;
import com.dragonminez.server.util.MutantManager;
import com.dragonminez.server.world.data.MutantSavedData;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class PrestigePointsSystem {
    public static final int BASE_LEVEL_CAP = 100000;
    public static final int BREAKTHROUGH_STEP = 10000;
    public static final int MAX_BREAKTHROUGHS = 5;
    public static final int ABSOLUTE_LEVEL_CAP = 150000;
    public static final int FORM_COST = 5;
    public static final int ULTIMATE_COST = 3;
    public static final int SKILL_POINT_COST = 1;
    public static final int POTENTIAL_UNLOCK_LEVELS_PER_POINT = 2;
    public static final int SKILL_SHOP_PAGE_SIZE = 21;
    private static final String KEY_POINTS = "prestige_points";
    public static final String ROOT_POINTS = "lm_prestige_points";
    private static final String KEY_BREAKTHROUGHS = "pp_level_breakthroughs";
    private static final String KEY_MAJIN = "pp_perm_majin";
    private static final String KEY_MUTANT = "pp_perm_mutant";
    private static final String KEY_ULTIMATE = "pp_perm_ultimate";
    private static final String DMZ_SKILL_ULTIMATE = "ultimate";
    private static final String KEY_SKILL_PREFIX = "pp_skill_";
    private static final String KEY_TIER_PREFIX = "pp_tier_";
    public static final Map<String, SkillOffer> SKILL_OFFERS = new LinkedHashMap<String, SkillOffer>();
    public static final int[] TURN_IN_AMOUNTS;
    private static final String KEY_REAPPLY_AT = "pp_reapply_at_ms";
    private static final String KEY_FORM_PULSE_AT = "pp_form_pulse_at_ms";
    private static final String KEY_SKILL_PULSE_AT = "pp_skill_pulse_at_ms";
    private static final long FORM_PULSE_MS = 5000L;
    private static final long SKILL_FLOOR_PULSE_MS = 5000L;
    private static final long REAPPLY_WINDOW_MS = 30000L;

    private PrestigePointsSystem() {
    }

    public static SkillOffer resolveOffer(String string) {
        if (string != null && !string.isBlank()) {
            String string2 = string.toLowerCase(Locale.ROOT).trim();
            PrestigeShopConfig.SkillShop skillShop = PrestigeShopConfig.get().skillShop;
            boolean bl = skillShop.extraSkills.contains(string2);
            if ((DmzSkillUtil.isSkillCheckSkill((String)string2) || bl) && !DmzSkillUtil.isFormSkill((String)string2)) {
                int n;
                Integer n2 = skillShop.maxLevelOverrides.get(string2);
                int n3 = n = n2 != null && n2 > 0 ? n2 : DmzSkillUtil.configuredMaxLevel((String)string2);
                if (n <= 0 && bl) {
                    n = 10;
                }
                if (n <= 0) {
                    n = switch (string2) {
                        case "potentialunlock" -> 30;
                        case "fusion", "kaioken" -> 5;
                        case "kiboost" -> 4;
                        case "kicontrol" -> 1;
                        case "meditation", "fly", "sprint", "jump", "kimanipulation", "kisense", "defense_penetration", "healing_reduction", "instant_transmission", "ki_infusion", "kiprotection" -> 10;
                        default -> 0;
                    };
                }
                return n <= 0 ? null : new SkillOffer(string2, DmzSkillUtil.prettySkillLabel((String)string2), n);
            }
            return null;
        }
        return null;
    }

    public static List<SkillOffer> skillOffers() {
        ArrayList<SkillOffer> arrayList = new ArrayList<SkillOffer>();
        PrestigeShopConfig.SkillShop skillShop = PrestigeShopConfig.get().skillShop;
        ArrayList<String> arrayList2 = new ArrayList<String>(DmzSkillUtil.skillCheckSkillIds());
        for (String object : skillShop.extraSkills) {
            if (arrayList2.contains(object)) continue;
            arrayList2.add(object);
        }
        for (String string : arrayList2) {
            SkillOffer skillOffer = PrestigePointsSystem.resolveOffer(string);
            if (skillOffer == null || skillShop.hiddenSkills.contains(skillOffer.id())) continue;
            arrayList.add(skillOffer);
        }
        if (arrayList.isEmpty()) {
            for (SkillOffer skillOffer : SKILL_OFFERS.values()) {
                if (skillShop.hiddenSkills.contains(skillOffer.id())) continue;
                arrayList.add(skillOffer);
            }
        }
        return arrayList;
    }

    public static int skillShopPageCount() {
        int n = PrestigePointsSystem.skillOffers().size();
        return Math.max(1, (n + 21 - 1) / 21);
    }

    public static List<SkillOffer> skillOffersPage(int n) {
        List<SkillOffer> list = PrestigePointsSystem.skillOffers();
        int n2 = Math.max(1, (list.size() + 21 - 1) / 21);
        int n3 = Math.max(0, Math.min(n2 - 1, n));
        int n4 = n3 * 21;
        if (n4 >= list.size()) {
            return List.of();
        }
        int n5 = Math.min(list.size(), n4 + 21);
        return list.subList(n4, n5);
    }

    public static int getPoints(ServerPlayer serverPlayer) {
        if (serverPlayer == null) {
            return 0;
        }
        int n = Math.max(0, (int)ProgressionData.storedGetLong((ServerPlayer)serverPlayer, (String)KEY_POINTS, (long)0L));
        long l = PersistentDataAccess.getLong((Entity)serverPlayer, (String)ROOT_POINTS, (long)-1L);
        int n2 = n;
        if (l > (long)n) {
            n2 = (int)Math.min(Integer.MAX_VALUE, l);
        }
        if (n2 != n && ProgressionData.storedWritable((ServerPlayer)serverPlayer)) {
            ProgressionData.storedPut((ServerPlayer)serverPlayer, (String)KEY_POINTS, (long)n2);
            PersistentDataAccess.putLong((Entity)serverPlayer, (String)ROOT_POINTS, (long)n2);
        } else if (l < 0L && n2 > 0) {
            PersistentDataAccess.putLong((Entity)serverPlayer, (String)ROOT_POINTS, (long)n2);
        }
        return n2;
    }

    public static void setPoints(ServerPlayer serverPlayer, int n) {
        if (serverPlayer != null) {
            int n2 = Math.max(0, n);
            ProgressionData.storedPut((ServerPlayer)serverPlayer, (String)KEY_POINTS, (long)n2);
            PersistentDataAccess.putLong((Entity)serverPlayer, (String)ROOT_POINTS, (long)n2);
        }
    }

    public static boolean hasPoints(ServerPlayer serverPlayer, int n) {
        return serverPlayer != null && n >= 0 && PrestigePointsSystem.getPoints(serverPlayer) >= n;
    }

    public static boolean trySpend(ServerPlayer serverPlayer, int n, String string) {
        if (serverPlayer != null && n > 0) {
            int n2 = PrestigePointsSystem.getPoints(serverPlayer);
            if (n2 < n) {
                ProgressionData.tempPut((ServerPlayer)serverPlayer, (String)"pp_last_spend_ok", (String)"false");
                return false;
            }
            PrestigePointsSystem.setPoints(serverPlayer, n2 - n);
            ProgressionData.tempPut((ServerPlayer)serverPlayer, (String)"pp_last_spend_ok", (String)"true");
            SystemTelemetry.log((String)KEY_POINTS, (String)"spend", (ServerPlayer)serverPlayer, null, Map.of("amount", n, "reason", string == null ? "" : string, "points", PrestigePointsSystem.getPoints(serverPlayer)));
            return true;
        }
        if (serverPlayer != null) {
            ProgressionData.tempPut((ServerPlayer)serverPlayer, (String)"pp_last_spend_ok", (String)"false");
        }
        return false;
    }

    public static void grantPoints(ServerPlayer serverPlayer, int n, String string) {
        if (serverPlayer != null && n > 0) {
            int n2 = PrestigePointsSystem.getPoints(serverPlayer) + n;
            PrestigePointsSystem.setPoints(serverPlayer, n2);
            SystemTelemetry.log((String)KEY_POINTS, (String)"grant", (ServerPlayer)serverPlayer, null, Map.of("amount", n, "reason", string == null ? "" : string, "points", n2));
        }
    }

    public static String spendForNpc(ServerPlayer serverPlayer, int n, String string) {
        if (serverPlayer == null) {
            return "\u00a7cPlayers only.";
        }
        if (n <= 0) {
            return "\u00a7cAmount must be positive.";
        }
        if (!PrestigePointsSystem.trySpend(serverPlayer, n, string)) {
            return "\u00a7cYou need \u00a7e" + n + " \u00a7cprestige favor. You have \u00a7e" + PrestigePointsSystem.getPoints(serverPlayer) + "\u00a7c.";
        }
        String string2 = string != null && !string.isBlank() ? string.trim() : "shop";
        return "\u00a7aSpent \u00a7e" + n + " \u00a7afavor \u00a77(" + string2 + ") \u00b7 Balance: \u00a7e" + PrestigePointsSystem.getPoints(serverPlayer);
    }

    public static String grantForNpc(ServerPlayer serverPlayer, int n, String string) {
        if (serverPlayer == null) {
            return "\u00a7cPlayers only.";
        }
        if (n <= 0) {
            return "\u00a7cAmount must be positive.";
        }
        PrestigePointsSystem.grantPoints(serverPlayer, n, string);
        return "\u00a7aGranted \u00a7e" + n + " \u00a7aprestige favor \u00b7 Balance: \u00a7e" + PrestigePointsSystem.getPoints(serverPlayer);
    }

    public static boolean isAllowedTurnInAmount(int n) {
        return PrestigeShopConfig.get().exchange.amounts.contains(n);
    }

    public static int[] turnInAmounts() {
        List<Integer> list = PrestigeShopConfig.get().exchange.amounts;
        int[] nArray = new int[list.size()];
        for (int i = 0; i < nArray.length; ++i) {
            nArray[i] = list.get(i);
        }
        return nArray;
    }

    public static int pointsForTurnIn(int n) {
        return PrestigeShopConfig.get().exchange.payout(n);
    }

    private static String turnInAmountsText() {
        List<Integer> list = PrestigeShopConfig.get().exchange.amounts;
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < list.size(); ++i) {
            if (i > 0) {
                stringBuilder.append(i == list.size() - 1 ? "\u00a77, or " : "\u00a77, ");
            }
            stringBuilder.append("\u00a7f").append(list.get(i));
        }
        return stringBuilder.toString();
    }

    public static String turnIn(ServerPlayer serverPlayer, int n) {
        if (serverPlayer == null) {
            return "\u00a7cPlayers only.";
        }
        int n2 = PrestigeSystem.getHeld((ServerPlayer)serverPlayer);
        int n3 = Math.max(0, n);
        if (!PrestigeShopConfig.get().exchange.enabled) {
            return "\u00a7cPrestige exchange is closed right now.";
        }
        if (n3 <= 0) {
            return "\u00a7cChoose how many prestiges to turn in.";
        }
        if (!PrestigePointsSystem.isAllowedTurnInAmount(n3)) {
            return "\u00a7cTurn in " + PrestigePointsSystem.turnInAmountsText() + " \u00a7cprestiges at a time.";
        }
        if (n3 > n2) {
            return "\u00a7cYour current prestige amount is only \u00a76" + n2 + "\u00a7c. Exchange fewer, or prestige again first.";
        }
        int n4 = PrestigePointsSystem.pointsForTurnIn(n3);
        int n5 = n2 - n3;
        PrestigeSystem.setHeldPublic((ServerPlayer)serverPlayer, (int)n5);
        int n6 = PrestigePointsSystem.getPoints(serverPlayer) + n4;
        PrestigePointsSystem.setPoints(serverPlayer, n6);
        SystemTelemetry.log((String)KEY_POINTS, (String)"turn_in", (ServerPlayer)serverPlayer, null, Map.of("amount", n3, "gained", n4, "held", n5, "points", n6));
        return "\u00a7aTurned in \u00a76" + n3 + " \u00a7aprestige" + (n3 == 1 ? "" : "s") + " \u00a77\u2192 \u00a7e+" + n4 + " \u00a77favor \u00a78(+" + (n4 - n3) + " bonus)\n\u00a77Prestige favor: \u00a7e" + n6 + " \u00a77\u00b7 Current prestige amount: \u00a76" + n5;
    }

    public static int getPurchasedSkillLevels(ServerPlayer serverPlayer, String string) {
        return serverPlayer != null && string != null && !string.isBlank() ? Math.max(0, (int)ProgressionData.storedGetLong((ServerPlayer)serverPlayer, (String)(KEY_SKILL_PREFIX + string.toLowerCase(Locale.ROOT)), (long)0L)) : 0;
    }

    public static String adminAdjustSkill(ServerPlayer serverPlayer, String string, String string2, int n) {
        if (serverPlayer == null) {
            return "\u00a7cPlayer not online.";
        }
        SkillOffer skillOffer = PrestigePointsSystem.resolveOffer(string);
        if (skillOffer == null) {
            String string3;
            String string4 = string3 = string == null ? "" : string.toLowerCase(Locale.ROOT).trim();
            if (string3.isBlank()) {
                return "\u00a7cUsage: skill <player> <skillId> <set|add|remove> <levels>\n\u00a78Example: skill Steve potentialunlock set 10";
            }
            int n2 = DmzSkillUtil.configuredMaxLevel((String)string3);
            if (n2 <= 0) {
                n2 = Math.max(PrestigePointsSystem.getPurchasedSkillLevels(serverPlayer, string3), 10);
            }
            skillOffer = new SkillOffer(string3, DmzSkillUtil.prettySkillLabel((String)string3), n2);
        }
        if (!ProgressionData.storedWritable((ServerPlayer)serverPlayer)) {
            return "\u00a7cCould not save prestige data for \u00a7f" + serverPlayer.m_6302_() + "\u00a7c.";
        }
        int n3 = PrestigePointsSystem.getPurchasedSkillLevels(serverPlayer, skillOffer.id);
        Integer n4 = PrestigePointsSystem.applyIntMode(n3, string2, n, 0, skillOffer.maxLevel);
        if (n4 == null) {
            return "\u00a7cUsage: skill <player> <skillId> <set|add|remove> <levels>\n\u00a78Example: skill Steve potentialunlock add 2";
        }
        ProgressionData.storedPut((ServerPlayer)serverPlayer, (String)(KEY_SKILL_PREFIX + skillOffer.id.toLowerCase(Locale.ROOT)), (long)n4.intValue());
        Skills skills = DmzSkillUtil.skills((ServerPlayer)serverPlayer);
        int n5 = 0;
        int n6 = 0;
        if (skills != null) {
            DmzSkillUtil.ensureRegistered((Skills)skills, (String)skillOffer.id, (int)skillOffer.maxLevel);
            n5 = DmzSkillUtil.level((Skills)skills, (String)skillOffer.id);
            int n7 = Math.max(skillOffer.maxLevel, DmzSkillUtil.maxLevel((Skills)skills, (String)skillOffer.id, (int)skillOffer.maxLevel));
            n6 = n4 > n3 ? Math.min(n7, Math.max(n5, n4)) : (n4 < n3 && n5 > n4 ? Math.min(n7, n4) : n5);
            if (n6 != n5) {
                DmzSkillUtil.setLevel((Skills)skills, (String)skillOffer.id, (int)n6);
                DmzSkillUtil.sync((ServerPlayer)serverPlayer);
            }
        }
        PrestigePointsSystem.reapplySkillBonuses(serverPlayer);
        SystemTelemetry.log((String)"prestige_admin", (String)"admin_skill", (ServerPlayer)serverPlayer, null, Map.of("skill", skillOffer.id, "mode", string2 == null ? "" : string2, "before", n3, "after", n4, "live_before", n5, "live_after", n6));
        String string5 = PrestigePointsSystem.levelsPerPoint(skillOffer.id) > 1 ? " \u00a78(" + PrestigePointsSystem.levelsPerPoint(skillOffer.id) + " levels per favor in shop)" : "";
        return "\u00a7a" + skillOffer.label + " \u00a77kept level \u00a7f" + n3 + " \u00a77\u2192 \u00a7f" + n4 + " \u00a78/ \u00a7f" + skillOffer.maxLevel + string5 + "\n\u00a77Live skill \u00a7f" + n5 + " \u00a77\u2192 \u00a7f" + n6 + " \u00a78(" + serverPlayer.m_6302_() + ")";
    }

    public static List<String> investedSkillSummary(ServerPlayer serverPlayer) {
        ArrayList<String> arrayList = new ArrayList<String>();
        if (serverPlayer == null) {
            return arrayList;
        }
        for (SkillOffer skillOffer : PrestigePointsSystem.skillOffers()) {
            int n = PrestigePointsSystem.getPurchasedSkillLevels(serverPlayer, skillOffer.id);
            if (n <= 0) continue;
            arrayList.add(skillOffer.label + " \u00a7f" + n + "\u00a77/\u00a7f" + skillOffer.maxLevel);
        }
        return arrayList;
    }

    private static Integer applyIntMode(int n, String string, int n2, int n3, int n4) {
        int n5;
        String string2;
        if (string == null) {
            return null;
        }
        switch (string2 = string.toLowerCase(Locale.ROOT).trim()) {
            case "set": {
                n5 = n2;
                break;
            }
            case "add": {
                n5 = n + n2;
                break;
            }
            case "remove": 
            case "take": 
            case "sub": {
                n5 = n - n2;
                break;
            }
            default: {
                return null;
            }
        }
        return Math.max(n3, Math.min(n4, n5));
    }

    public static String buySkillLevel(ServerPlayer serverPlayer, String string) {
        if (serverPlayer == null) {
            return "\u00a7cPlayers only.";
        }
        SkillOffer skillOffer = PrestigePointsSystem.resolveOffer(string);
        PrestigeShopConfig.SkillShop skillShop = PrestigeShopConfig.get().skillShop;
        if (!skillShop.enabled) {
            return "\u00a7cThe skill shop is closed right now.";
        }
        if (skillOffer == null || skillShop.hiddenSkills.contains(skillOffer.id)) {
            return "\u00a7cOnly Skill Check skills are purchasable here (Natural + Saga).";
        }
        int n = PrestigePointsSystem.getPurchasedSkillLevels(serverPlayer, skillOffer.id);
        int n2 = Math.max(1, skillOffer.maxLevel);
        int n3 = skillShop.costFor(skillOffer.id);
        if (n >= n2) {
            return "\u00a7c" + skillOffer.label + " kept level is maxed (\u00a7f" + n2 + "\u00a7c).";
        }
        int n4 = PrestigePointsSystem.getPoints(serverPlayer);
        if (n4 < n3) {
            return "\u00a7cNeed \u00a7e" + n3 + " \u00a7cfavor (have \u00a7e" + n4 + "\u00a7c).";
        }
        int n5 = PrestigePointsSystem.levelsPerPoint(skillOffer.id);
        int n6 = Math.max(0, n2 - n);
        if (n6 <= 0) {
            return "\u00a7c" + skillOffer.label + " kept level is maxed (\u00a7f" + n2 + "\u00a7c).";
        }
        int n7 = Math.min(n5, n6);
        PrestigePointsSystem.setPoints(serverPlayer, n4 - n3);
        int n8 = Math.min(n2, n + n7);
        ProgressionData.storedPut((ServerPlayer)serverPlayer, (String)(KEY_SKILL_PREFIX + skillOffer.id.toLowerCase(Locale.ROOT)), (long)n8);
        int n9 = 0;
        int n10 = 0;
        Skills skills = DmzSkillUtil.skills((ServerPlayer)serverPlayer);
        if (skills != null) {
            DmzSkillUtil.ensureRegistered((Skills)skills, (String)skillOffer.id, (int)n2);
            n9 = DmzSkillUtil.level((Skills)skills, (String)skillOffer.id);
            int n11 = Math.max(n2, DmzSkillUtil.maxLevel((Skills)skills, (String)skillOffer.id, (int)n2));
            n10 = Math.min(n11, Math.max(n9, n8));
            if (n10 != n9) {
                DmzSkillUtil.setLevel((Skills)skills, (String)skillOffer.id, (int)n10);
                DmzSkillUtil.sync((ServerPlayer)serverPlayer);
            }
        }
        SystemTelemetry.log((String)KEY_POINTS, (String)"buy_skill", (ServerPlayer)serverPlayer, null, Map.of("skill", skillOffer.id, "purchased", n8, "level", n10, "gain", n7, "points", PrestigePointsSystem.getPoints(serverPlayer)));
        String string2 = n10 > n9 ? " \u2192 \u00a7fLv " + n10 : " \u00a78(live already \u00a7f" + n9 + "\u00a78)";
        return "\u00a7a+" + n7 + " \u00a77" + skillOffer.label + " kept level \u00a7f" + n + " \u00a77\u2192 \u00a7f" + n8 + string2 + " \u00a78(survives prestige, -" + n3 + " favor)\n\u00a77Favor left: \u00a7e" + PrestigePointsSystem.getPoints(serverPlayer);
    }

    public static int levelsPerPoint(String string) {
        return PrestigeShopConfig.get().skillShop.levelsFor(string);
    }

    public static boolean hasAnyPurchasedSkillFloor(ServerPlayer serverPlayer) {
        if (serverPlayer == null) {
            return false;
        }
        try {
            CompoundTag compoundTag = ProgressionData.stored((ServerPlayer)serverPlayer);
            for (String string : compoundTag.m_128431_()) {
                if (string == null || !string.startsWith(KEY_SKILL_PREFIX) || ProgressionData.storedGetLong((ServerPlayer)serverPlayer, (String)string, (long)0L) <= 0L) continue;
                return true;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        for (SkillOffer skillOffer : PrestigePointsSystem.skillOffers()) {
            if (PrestigePointsSystem.getPurchasedSkillLevels(serverPlayer, skillOffer.id) <= 0) continue;
            return true;
        }
        return false;
    }

    public static void reapplySkillBonuses(ServerPlayer serverPlayer) {
        if (serverPlayer != null) {
            Skills skills = DmzSkillUtil.skills((ServerPlayer)serverPlayer);
            if (skills == null) {
                if (PrestigePointsSystem.hasAnyPurchasedSkillFloor(serverPlayer)) {
                    long l = System.currentTimeMillis() + 5000L;
                    long l2 = ProgressionData.tempGetLong((ServerPlayer)serverPlayer, (String)KEY_REAPPLY_AT, (long)0L);
                    if (l2 < l) {
                        ProgressionData.tempPut((ServerPlayer)serverPlayer, (String)KEY_REAPPLY_AT, (long)l);
                    }
                }
                PrestigePointsSystem.reapplyForms(serverPlayer);
                PrestigePointsSystem.reapplyTierUnlocks(serverPlayer);
            } else {
                int n;
                int n2;
                boolean bl = false;
                LinkedHashMap<String, SkillOffer> linkedHashMap = new LinkedHashMap<String, SkillOffer>();
                for (SkillOffer object : PrestigePointsSystem.skillOffers()) {
                    linkedHashMap.put(object.id, object);
                    SKILL_OFFERS.put(object.id, object);
                }
                for (String string : DmzSkillUtil.allNonFormSkillIds()) {
                    int n3;
                    if (linkedHashMap.containsKey(string) || (n3 = PrestigePointsSystem.getPurchasedSkillLevels(serverPlayer, string)) <= 0) continue;
                    int n4 = DmzSkillUtil.configuredMaxLevel((String)string);
                    if (n4 <= 0) {
                        n4 = Math.max(n3, 10);
                    }
                    linkedHashMap.put(string, new SkillOffer(string, DmzSkillUtil.prettySkillLabel((String)string), n4));
                }
                try {
                    CompoundTag offerTag = ProgressionData.stored((ServerPlayer)serverPlayer);
                    for (String string : offerTag.m_128431_()) {
                        String string2;
                        if (string == null || !string.startsWith(KEY_SKILL_PREFIX) || (string2 = string.substring(KEY_SKILL_PREFIX.length()).toLowerCase(Locale.ROOT)).isBlank() || linkedHashMap.containsKey(string2) || (n2 = PrestigePointsSystem.getPurchasedSkillLevels(serverPlayer, string2)) <= 0) continue;
                        n = DmzSkillUtil.configuredMaxLevel((String)string2);
                        if (n <= 0) {
                            n = Math.max(n2, 10);
                        }
                        linkedHashMap.put(string2, new SkillOffer(string2, DmzSkillUtil.prettySkillLabel((String)string2), n));
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                for (SkillOffer skillOffer : linkedHashMap.values()) {
                    int n5 = PrestigePointsSystem.getPurchasedSkillLevels(serverPlayer, skillOffer.id);
                    if (n5 <= 0) continue;
                    DmzSkillUtil.ensureRegistered((Skills)skills, (String)skillOffer.id, (int)skillOffer.maxLevel);
                    int n6 = DmzSkillUtil.level((Skills)skills, (String)skillOffer.id);
                    n2 = Math.max(skillOffer.maxLevel, DmzSkillUtil.maxLevel((Skills)skills, (String)skillOffer.id, (int)skillOffer.maxLevel));
                    n = Math.min(n2, Math.max(n6, n5));
                    if (n <= n6) continue;
                    DmzSkillUtil.setLevel((Skills)skills, (String)skillOffer.id, (int)n);
                    bl = true;
                }
                if (bl) {
                    DmzSkillUtil.sync((ServerPlayer)serverPlayer);
                }
                PrestigePointsSystem.reapplyForms(serverPlayer);
                PrestigePointsSystem.reapplyTierUnlocks(serverPlayer);
            }
        }
    }

    public static int tierPointCost(int n) {
        return n >= 1 && n <= 7 ? PrestigeShopConfig.get().tierShop.costFor(n) : 0;
    }

    public static boolean hasPurchasedTier(ServerPlayer serverPlayer, int n) {
        return serverPlayer != null && n >= 1 && n <= 7 ? ProgressionData.storedGetBool((ServerPlayer)serverPlayer, (String)(KEY_TIER_PREFIX + n)) : false;
    }

    public static int highestPurchasedTier(ServerPlayer serverPlayer) {
        if (serverPlayer == null) {
            return 0;
        }
        int n = 0;
        for (int i = 1; i <= 7; ++i) {
            if (!PrestigePointsSystem.hasPurchasedTier(serverPlayer, i)) continue;
            n = i;
        }
        return n;
    }

    public static boolean isTierUnlockedOrPurchased(ServerPlayer serverPlayer, int n) {
        if (serverPlayer == null || n < 1 || n > 7) {
            return false;
        }
        if (PrestigePointsSystem.hasPurchasedTier(serverPlayer, n)) {
            return true;
        }
        try {
            PlayerDifficultyData playerDifficultyData = DifficultyCache.data((ServerPlayer)serverPlayer);
            return playerDifficultyData != null && playerDifficultyData.hasUnlockedTier(n);
        }
        catch (Throwable throwable) {
            return false;
        }
    }

    public static boolean canBuyDifficultyTier(ServerPlayer serverPlayer, int n) {
        if (serverPlayer != null && n >= 1 && n <= 7) {
            PrestigeShopConfig.TierShop tierShop = PrestigeShopConfig.get().tierShop;
            if (PrestigePointsSystem.hasPurchasedTier(serverPlayer, n) || !tierShop.forSale(n)) {
                return false;
            }
            for (int i = 1; tierShop.requireInOrder && i < n; ++i) {
                if (PrestigePointsSystem.hasPurchasedTier(serverPlayer, i)) continue;
                return false;
            }
            return true;
        }
        return false;
    }

    public static String buyDifficultyTier(ServerPlayer serverPlayer, int n) {
        int n2;
        if (serverPlayer == null) {
            return "\u00a7cPlayers only.";
        }
        UnlockTier unlockTier = UnlockTier.byId((int)n);
        if (unlockTier == null) {
            return "\u00a7cUnknown tier. Use 1\u20137.";
        }
        if (!ProgressionData.storedWritable((ServerPlayer)serverPlayer)) {
            return "\u00a7cCould not save prestige data \u2014 try relogging, then buy again.";
        }
        if (PrestigePointsSystem.hasPurchasedTier(serverPlayer, n)) {
            return "\u00a7eYou already own permanent \u00a7fT" + n + " " + unlockTier.display + "\u00a7e.";
        }
        if (!PrestigeShopConfig.get().tierShop.forSale(n)) {
            return "\u00a7cPermanent \u00a7fT" + n + " \u00a7cis not for sale right now.";
        }
        if (n > 1 && PrestigeShopConfig.get().tierShop.requireInOrder) {
            for (n2 = 1; n2 < n; ++n2) {
                if (PrestigePointsSystem.hasPurchasedTier(serverPlayer, n2)) continue;
                return "\u00a7cBuy permanent \u00a7fT" + n2 + " \u00a7cfirst (shop ladder T1\u2192T" + n + ").";
            }
        }
        n2 = PrestigePointsSystem.tierPointCost(n);
        int n3 = PrestigePointsSystem.getPoints(serverPlayer);
        if (n3 < n2) {
            return "\u00a7cNeed \u00a7e" + n2 + " \u00a7cfavor (have \u00a7e" + n3 + "\u00a7c).";
        }
        if (!GuiClickConfirm.confirmed((UUID)serverPlayer.m_20148_(), (String)("prestige-tier:" + n))) {
            return "\u00a7eUnlock permanent \u00a7fT" + n + " \u00a7efor \u00a76" + n2 + " \u00a7efavor? Click again within 10 seconds.";
        }
        PrestigePointsSystem.setPoints(serverPlayer, n3 - n2);
        ProgressionData.storedPutBool((ServerPlayer)serverPlayer, (String)(KEY_TIER_PREFIX + n), (boolean)true);
        PlayerDifficultyData playerDifficultyData = DifficultyCache.data((ServerPlayer)serverPlayer);
        PrestigePointsSystem.reapplyTierUnlocks(serverPlayer);
        try {
            DifficultyCache.save((ServerPlayer)serverPlayer);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        try {
            if (playerDifficultyData != null) {
                UnlockSystem.syncUnlocks((ServerPlayer)serverPlayer, (PlayerDifficultyData)playerDifficultyData);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        PrestigePointsSystem.reapplyTierUnlocks(serverPlayer);
        try {
            DifficultyCache.save((ServerPlayer)serverPlayer);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        try {
            TitleSystem.syncTierTitles((ServerPlayer)serverPlayer, (boolean)true);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        SystemTelemetry.log((String)KEY_POINTS, (String)"buy_tier", (ServerPlayer)serverPlayer, null, Map.of("tier", n, "cost", n2, "points", PrestigePointsSystem.getPoints(serverPlayer)));
        return "\u00a7aPermanent unlock \u00a7fT" + n + " " + unlockTier.display + " \u00a77(\u00a7e-" + n2 + " \u00a77favor)\n\u00a77Survives prestige \u00b7 still activate with Ancient Coins via \u00a7f/difficulty \u00a77Tiers\n\u00a77Favor left: \u00a7e" + PrestigePointsSystem.getPoints(serverPlayer);
    }

    public static String adminGrantTier(ServerPlayer serverPlayer, int n) {
        if (serverPlayer == null) {
            return "\u00a7cPlayer not online.";
        }
        if (n < 1 || n > 7) {
            return "\u00a7cTier must be 1\u20137.";
        }
        if (!ProgressionData.storedWritable((ServerPlayer)serverPlayer)) {
            return "\u00a7cCould not save prestige data for \u00a7f" + serverPlayer.m_6302_() + "\u00a7c.";
        }
        for (int i = 1; i <= n; ++i) {
            ProgressionData.storedPutBool((ServerPlayer)serverPlayer, (String)(KEY_TIER_PREFIX + i), (boolean)true);
        }
        PrestigePointsSystem.reapplyTierUnlocks(serverPlayer);
        try {
            DifficultyCache.save((ServerPlayer)serverPlayer);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return "\u00a7aGranted permanent difficulty tiers \u00a7fT1\u2013T" + n + " \u00a78(" + serverPlayer.m_6302_() + ")\n\u00a77Activate via \u00a7f/difficulty \u00a77Tiers (Ancient Coins).";
    }

    public static String adminAdjustHighestTier(ServerPlayer serverPlayer, String string, int n) {
        if (serverPlayer == null) {
            return "\u00a7cPlayer not online.";
        }
        int n2 = PrestigePointsSystem.highestPurchasedTier(serverPlayer);
        Integer n3 = PrestigePointsSystem.applyIntMode(n2, string, n, 0, 7);
        if (n3 == null) {
            return "\u00a7cUsage: tier <player> <set|add|remove> <0-7>\n\u00a78Or: tier <player> give <1-7> | clear <1-7|all>";
        }
        if (!ProgressionData.storedWritable((ServerPlayer)serverPlayer)) {
            return "\u00a7cCould not save prestige data for \u00a7f" + serverPlayer.m_6302_() + "\u00a7c.";
        }
        for (int i = 1; i <= 7; ++i) {
            if (i <= n3) {
                ProgressionData.storedPutBool((ServerPlayer)serverPlayer, (String)(KEY_TIER_PREFIX + i), (boolean)true);
                continue;
            }
            ProgressionData.storedRemove((ServerPlayer)serverPlayer, (String)(KEY_TIER_PREFIX + i));
        }
        PrestigePointsSystem.reapplyTierUnlocks(serverPlayer);
        try {
            DifficultyCache.save((ServerPlayer)serverPlayer);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return "\u00a7aPermanent difficulty tiers \u00a7fT" + n2 + " \u00a77\u2192 \u00a7fT" + n3 + " \u00a78(" + serverPlayer.m_6302_() + ")" + (String)(n3 > 0 ? "\n\u00a77Owns permanent \u00a7fT1\u2013T" + n3 + "\u00a77. Activate via \u00a7f/difficulty\u00a77." : "\n\u00a77No permanent prestige tiers.");
    }

    public static String adminClearTier(ServerPlayer serverPlayer, int n) {
        if (serverPlayer == null) {
            return "\u00a7cPlayer not online.";
        }
        if (!ProgressionData.storedWritable((ServerPlayer)serverPlayer)) {
            return "\u00a7cCould not save prestige data for \u00a7f" + serverPlayer.m_6302_() + "\u00a7c.";
        }
        if (n > 0) {
            if (n > 7) {
                return "\u00a7cTier must be 1\u20137 (or 0 for all).";
            }
            ProgressionData.storedRemove((ServerPlayer)serverPlayer, (String)(KEY_TIER_PREFIX + n));
            return "\u00a77Cleared permanent \u00a7fT" + n + " \u00a77for \u00a7f" + serverPlayer.m_6302_() + "\u00a77.\n\u00a78Level/prestige unlocks are unchanged.";
        }
        for (int i = 1; i <= 7; ++i) {
            ProgressionData.storedRemove((ServerPlayer)serverPlayer, (String)(KEY_TIER_PREFIX + i));
        }
        return "\u00a77Cleared all permanent prestige difficulty tiers for \u00a7f" + serverPlayer.m_6302_() + "\u00a77.";
    }

    public static void reapplyTierUnlocks(ServerPlayer serverPlayer) {
        PlayerDifficultyData playerDifficultyData;
        if (serverPlayer != null && (playerDifficultyData = DifficultyCache.data((ServerPlayer)serverPlayer)) != null) {
            for (int i = 1; i <= 7; ++i) {
                if (!PrestigePointsSystem.hasPurchasedTier(serverPlayer, i)) continue;
                playerDifficultyData.unlockTier(i);
            }
        }
    }

    public static boolean hasMajin(ServerPlayer serverPlayer) {
        return serverPlayer != null && ProgressionData.storedGetBool((ServerPlayer)serverPlayer, (String)KEY_MAJIN);
    }

    public static boolean hasMutant(ServerPlayer serverPlayer) {
        return serverPlayer != null && ProgressionData.storedGetBool((ServerPlayer)serverPlayer, (String)KEY_MUTANT);
    }

    public static boolean hasUltimate(ServerPlayer serverPlayer) {
        return serverPlayer != null && ProgressionData.storedGetBool((ServerPlayer)serverPlayer, (String)KEY_ULTIMATE);
    }

    public static boolean canBuyMajin(ServerPlayer serverPlayer) {
        PrestigeShopConfig.Forms forms = PrestigeShopConfig.get().forms;
        return serverPlayer != null && forms.majin.enabled && !PrestigePointsSystem.hasMajin(serverPlayer) && (!forms.majinMutantExclusive || !PrestigePointsSystem.hasMutant(serverPlayer));
    }

    public static boolean canBuyMutant(ServerPlayer serverPlayer) {
        PrestigeShopConfig.Forms forms = PrestigeShopConfig.get().forms;
        return serverPlayer != null && forms.mutant.enabled && !PrestigePointsSystem.hasMutant(serverPlayer) && (!forms.majinMutantExclusive || !PrestigePointsSystem.hasMajin(serverPlayer));
    }

    public static boolean canBuyUltimate(ServerPlayer serverPlayer) {
        return serverPlayer != null && PrestigeShopConfig.get().forms.ultimate.enabled && !PrestigePointsSystem.hasUltimate(serverPlayer) && !PrestigePointsSystem.hasUltimateSkill(serverPlayer);
    }

    public static boolean hasUltimateSkill(ServerPlayer serverPlayer) {
        return PrestigePointsSystem.ultimateSkillLevel(serverPlayer) >= 1;
    }

    public static int ultimateSkillLevel(ServerPlayer serverPlayer) {
        return serverPlayer == null ? 0 : DmzProgression.skillLevel((Player)serverPlayer, (String)DMZ_SKILL_ULTIMATE);
    }

    public static void grantUltimate(ServerPlayer serverPlayer) {
        if (serverPlayer != null) {
            try {
                StatsData statsData = DmzProgression.stats((Player)serverPlayer);
                if (statsData == null) {
                    return;
                }
                Skills skills = statsData.getSkills();
                if (skills == null) {
                    return;
                }
                skills.setSkillLevel(DMZ_SKILL_ULTIMATE, 1);
                DmzSkillUtil.sync((ServerPlayer)serverPlayer);
            }
            catch (Throwable throwable) {
                AdaptiveDifficultyMod.LOGGER.debug("[{}] prestige ultimate grant soft-fail: {}", (Object)"legacymechanics", (Object)throwable.toString());
            }
            PrestigePointsSystem.stripDeadUltimateEffect(serverPlayer);
        }
    }

    public static void revokeUltimate(ServerPlayer serverPlayer) {
        if (serverPlayer != null) {
            try {
                StatsData statsData = DmzProgression.stats((Player)serverPlayer);
                if (statsData == null) {
                    return;
                }
                Skills skills = statsData.getSkills();
                if (skills == null) {
                    return;
                }
                skills.setSkillLevel(DMZ_SKILL_ULTIMATE, 0);
                DmzSkillUtil.sync((ServerPlayer)serverPlayer);
            }
            catch (Throwable throwable) {
                AdaptiveDifficultyMod.LOGGER.debug("[{}] prestige ultimate revoke soft-fail: {}", (Object)"legacymechanics", (Object)throwable.toString());
            }
            PrestigePointsSystem.stripDeadUltimateEffect(serverPlayer);
        }
    }

    private static void stripDeadUltimateEffect(ServerPlayer serverPlayer) {
        if (serverPlayer != null) {
            try {
                StatsData statsData = DmzProgression.stats((Player)serverPlayer);
                if (statsData == null) {
                    return;
                }
                Effects effects = statsData.getEffects();
                if (effects != null && effects.hasEffect(DMZ_SKILL_ULTIMATE)) {
                    effects.removeEffect(DMZ_SKILL_ULTIMATE);
                    DmzSkillUtil.sync((ServerPlayer)serverPlayer);
                }
            }
            catch (Throwable throwable) {
                AdaptiveDifficultyMod.LOGGER.debug("[{}] prestige ultimate effect cleanup soft-fail: {}", (Object)"legacymechanics", (Object)throwable.toString());
            }
        }
    }

    public static String buyMajin(ServerPlayer serverPlayer) {
        return PrestigePointsSystem.buyForm(serverPlayer, true);
    }

    public static String buyMutant(ServerPlayer serverPlayer) {
        return PrestigePointsSystem.buyForm(serverPlayer, false);
    }

    public static String unbuyMajin(ServerPlayer serverPlayer) {
        return PrestigePointsSystem.unbuyForm(serverPlayer, true);
    }

    public static String unbuyMutant(ServerPlayer serverPlayer) {
        return PrestigePointsSystem.unbuyForm(serverPlayer, false);
    }

    public static String buyUltimate(ServerPlayer serverPlayer) {
        PrestigeShopConfig.FormOffer formOffer = PrestigeShopConfig.get().forms.ultimate;
        if (serverPlayer == null) {
            return UltraStyle.dangerLine((String)"Players only.");
        }
        if (PrestigePointsSystem.hasUltimate(serverPlayer)) {
            return UltraStyle.noticeLine((String)"You already own Permanent Ultimate.");
        }
        if (PrestigePointsSystem.hasUltimateSkill(serverPlayer)) {
            return UltraStyle.noticeLine((String)"You already have the Ultimate form from Old Kai \u2014 nothing to buy.");
        }
        if (!formOffer.enabled) {
            return UltraStyle.dangerLine((String)"Permanent Ultimate is not for sale right now.");
        }
        int n = PrestigePointsSystem.getPoints(serverPlayer);
        if (n < formOffer.cost) {
            return UltraStyle.dangerLine((String)("Need " + formOffer.cost + " favor (have " + n + ")."));
        }
        if (!GuiClickConfirm.confirmed((UUID)serverPlayer.m_20148_(), (String)"form-buy:Permanent Ultimate")) {
            return UltraStyle.noticeLine((String)("Buy Permanent Ultimate for " + formOffer.cost + " favor? Click again within 10 seconds."));
        }
        PrestigePointsSystem.setPoints(serverPlayer, n - formOffer.cost);
        ProgressionData.storedPutBool((ServerPlayer)serverPlayer, (String)KEY_ULTIMATE, (boolean)true);
        PrestigePointsSystem.grantUltimate(serverPlayer);
        SystemTelemetry.log((String)KEY_POINTS, (String)"buy_ultimate", (ServerPlayer)serverPlayer, null, Map.of("points", PrestigePointsSystem.getPoints(serverPlayer), "cost", formOffer.cost));
        return UltraStyle.color((String)"a", (String)("Unlocked Permanent Ultimate (-" + formOffer.cost + " favor)")) + "\n" + UltraStyle.dim((String)("Favor left: " + PrestigePointsSystem.getPoints(serverPlayer)));
    }

    public static String unbuyUltimate(ServerPlayer serverPlayer) {
        int n = PrestigeShopConfig.get().forms.ultimate.refund();
        if (serverPlayer == null) {
            return UltraStyle.dangerLine((String)"Players only.");
        }
        if (!PrestigePointsSystem.hasUltimate(serverPlayer)) {
            return UltraStyle.noticeLine((String)"You do not own Permanent Ultimate.");
        }
        if (!GuiClickConfirm.confirmed((UUID)serverPlayer.m_20148_(), (String)"form-remove:Permanent Ultimate")) {
            return UltraStyle.noticeLine((String)("Remove Permanent Ultimate? " + (String)(n > 0 ? "You get " + n + " favor back." : "No favor is refunded.") + " Click again within 10 seconds."));
        }
        ProgressionData.storedPutBool((ServerPlayer)serverPlayer, (String)KEY_ULTIMATE, (boolean)false);
        PrestigePointsSystem.revokeUltimate(serverPlayer);
        PrestigePointsSystem.setPoints(serverPlayer, PrestigePointsSystem.getPoints(serverPlayer) + n);
        SystemTelemetry.log((String)KEY_POINTS, (String)"unbuy_ultimate", (ServerPlayer)serverPlayer, null, Map.of("points", PrestigePointsSystem.getPoints(serverPlayer), "refund", n));
        return UltraStyle.color((String)"a", (String)("Removed Permanent Ultimate" + (String)(n > 0 ? " (+" + n + " favor refunded)" : " (no refund)"))) + "\n" + UltraStyle.dim((String)("Favor left: " + PrestigePointsSystem.getPoints(serverPlayer)));
    }

    private static String buyForm(ServerPlayer serverPlayer, boolean bl) {
        String string;
        if (serverPlayer == null) {
            return "\u00a7cPlayers only.";
        }
        PrestigeShopConfig.Forms forms = PrestigeShopConfig.get().forms;
        PrestigeShopConfig.FormOffer formOffer = bl ? forms.majin : forms.mutant;
        PrestigeShopConfig.FormOffer formOffer2 = bl ? forms.mutant : forms.majin;
        String string2 = bl ? "Permanent Majin" : "Permanent Mutant";
        String string3 = string = formOffer2.refund() > 0 ? "\u00a77" + formOffer2.refund() + " favor back" : "\u00a7cno refund";
        if (bl ? !PrestigePointsSystem.hasMajin(serverPlayer) : !PrestigePointsSystem.hasMutant(serverPlayer)) {
            int n = PrestigePointsSystem.getPoints(serverPlayer);
            if (!formOffer.enabled) {
                return "\u00a7c" + string2 + " is not for sale right now.";
            }
            if (n < formOffer.cost) {
                return "\u00a7cNeed \u00a7e" + formOffer.cost + " \u00a7cfavor (have \u00a7e" + n + "\u00a7c).";
            }
            if (forms.majinMutantExclusive && bl && PrestigePointsSystem.hasMutant(serverPlayer)) {
                return "\u00a7cYou have \u00a7fPermanent Mutant\u00a7c.\n\u00a77Remove Mutant first (" + string + "\u00a77), then buy Majin.";
            }
            if (forms.majinMutantExclusive && !bl && PrestigePointsSystem.hasMajin(serverPlayer)) {
                return "\u00a7cYou have \u00a7fPermanent Majin\u00a7c.\n\u00a77Remove Majin first (" + string + "\u00a77), then buy Mutant.";
            }
            if (!GuiClickConfirm.confirmed((UUID)serverPlayer.m_20148_(), (String)("form-buy:" + string2))) {
                return "\u00a7eBuy \u00a7f" + string2 + " \u00a7efor \u00a76" + formOffer.cost + " \u00a7efavor? Click again within 10 seconds.";
            }
            PrestigePointsSystem.setPoints(serverPlayer, n - formOffer.cost);
            ProgressionData.storedPutBool((ServerPlayer)serverPlayer, (String)(bl ? KEY_MAJIN : KEY_MUTANT), (boolean)true);
            PrestigePointsSystem.runDmzEffect(serverPlayer, bl ? "majin" : "mutant", true);
            SystemTelemetry.log((String)KEY_POINTS, (String)(bl ? "buy_majin" : "buy_mutant"), (ServerPlayer)serverPlayer, null, Map.of("points", PrestigePointsSystem.getPoints(serverPlayer), "cost", formOffer.cost));
            return "\u00a7aUnlocked \u00a7f" + string2 + " \u00a77(\u00a7e-" + formOffer.cost + " \u00a77favor)\n\u00a77Favor left: \u00a7e" + PrestigePointsSystem.getPoints(serverPlayer) + (String)(forms.majinMutantExclusive ? "\n\u00a78To switch: remove it (" + (String)(formOffer.refund() > 0 ? formOffer.refund() + " favor back" : "no refund") + "), then buy the other form." : "");
        }
        return "\u00a7eYou already own \u00a7f" + string2 + "\u00a7e.";
    }

    private static String unbuyForm(ServerPlayer serverPlayer, boolean bl) {
        if (serverPlayer == null) {
            return "\u00a7cPlayers only.";
        }
        String string = bl ? "Permanent Majin" : "Permanent Mutant";
        int n = (bl ? PrestigeShopConfig.get().forms.majin : PrestigeShopConfig.get().forms.mutant).refund();
        if (bl ? PrestigePointsSystem.hasMajin(serverPlayer) : PrestigePointsSystem.hasMutant(serverPlayer)) {
            if (!GuiClickConfirm.confirmed((UUID)serverPlayer.m_20148_(), (String)("form-remove:" + string))) {
                return "\u00a7eRemove \u00a7f" + string + "\u00a7e? " + (String)(n > 0 ? "You get \u00a76" + n + " \u00a7efavor back." : "You will \u00a7cnot \u00a7eget your favor back.") + " Click again within 10 seconds.";
            }
            PrestigePointsSystem.clearForm(serverPlayer, bl);
            if (n > 0) {
                PrestigePointsSystem.setPoints(serverPlayer, PrestigePointsSystem.getPoints(serverPlayer) + n);
            }
            SystemTelemetry.log((String)KEY_POINTS, (String)(bl ? "unbuy_majin" : "unbuy_mutant"), (ServerPlayer)serverPlayer, null, Map.of("refund", n));
            return "\u00a77Removed \u00a7f" + string + "\u00a77. " + (String)(n > 0 ? "\u00a7a+" + n + " favor refunded." : "\u00a7cNo favor refunded.") + "\n\u00a77Favor: \u00a7e" + PrestigePointsSystem.getPoints(serverPlayer);
        }
        return "\u00a7eYou do not own \u00a7f" + string + "\u00a7e.";
    }

    private static void clearForm(ServerPlayer serverPlayer, boolean bl) {
        ProgressionData.storedPutBool((ServerPlayer)serverPlayer, (String)(bl ? KEY_MAJIN : KEY_MUTANT), (boolean)false);
        PrestigePointsSystem.runDmzEffect(serverPlayer, bl ? "majin" : "mutant", false);
    }

    private static void reapplyForms(ServerPlayer serverPlayer) {
        if (PrestigePointsSystem.hasMajin(serverPlayer)) {
            PrestigePointsSystem.runDmzEffect(serverPlayer, "majin", true);
        }
        if (PrestigePointsSystem.hasMutant(serverPlayer)) {
            PrestigePointsSystem.runDmzEffect(serverPlayer, "mutant", true);
        }
        if (PrestigePointsSystem.hasUltimate(serverPlayer)) {
            if (!PrestigePointsSystem.hasUltimateSkill(serverPlayer)) {
                PrestigePointsSystem.grantUltimate(serverPlayer);
            } else {
                PrestigePointsSystem.stripDeadUltimateEffect(serverPlayer);
            }
        }
    }

    public static int getBreakthroughs(ServerPlayer serverPlayer) {
        if (serverPlayer == null) {
            return 0;
        }
        boolean bl = ProgressionData.storedHas((ServerPlayer)serverPlayer, (String)KEY_BREAKTHROUGHS);
        int n = bl ? (int)ProgressionData.storedGetLong((ServerPlayer)serverPlayer, (String)KEY_BREAKTHROUGHS, (long)0L) : -1;
        int n2 = PersistentDataAccess.getInt((Entity)serverPlayer, (String)KEY_BREAKTHROUGHS, (int)-1);
        int n3 = PersistentDataAccess.getInt((Entity)serverPlayer, (String)"lm_personal_breakthroughs", (int)-1);
        if (n < 0 && n2 < 0 && n3 < 0) {
            return 0;
        }
        int n4 = 0;
        if (n >= 0) {
            n4 = Math.max(n4, n);
        }
        if (n2 >= 0) {
            n4 = Math.max(n4, n2);
        }
        if (n3 >= 0) {
            n4 = Math.max(n4, n3);
        }
        return Math.max(0, Math.min(5, n4));
    }

    public static int breakthroughCost(int n) {
        return n >= 1 && n <= 5 ? 15 + (n - 1) * 5 : Integer.MAX_VALUE;
    }

    public static void setBreakthroughs(ServerPlayer serverPlayer, int n) {
        if (serverPlayer != null) {
            int n2 = Math.max(0, Math.min(5, n));
            ProgressionData.storedPut((ServerPlayer)serverPlayer, (String)KEY_BREAKTHROUGHS, (long)n2);
            PersistentDataAccess.putInt((Entity)serverPlayer, (String)KEY_BREAKTHROUGHS, (int)n2);
            PersistentDataAccess.putInt((Entity)serverPlayer, (String)"lm_personal_breakthroughs", (int)n2);
            try {
                PersonalLevelCapMirror.overwrite((ServerPlayer)serverPlayer, (int)n2, (int)PrestigePointsSystem.effectiveMaxLevel(n2));
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            try {
                DmzSkillUtil.sync((ServerPlayer)serverPlayer);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            PersonalLevelCapMirror.markSynced((ServerPlayer)serverPlayer, (int)PrestigePointsSystem.effectiveMaxLevel(n2));
        }
    }

    public static int effectiveMaxLevel(ServerPlayer serverPlayer) {
        return Math.min(150000, 100000 + PrestigePointsSystem.getBreakthroughs(serverPlayer) * 10000);
    }

    public static int effectiveMaxLevel(int n) {
        int n2 = Math.max(0, Math.min(5, n));
        return Math.min(150000, 100000 + n2 * 10000);
    }

    public static String buyBreakthrough(ServerPlayer serverPlayer) {
        Object object;
        int n;
        if (serverPlayer == null) {
            return "\u00a7cPlayers only.";
        }
        int n2 = PrestigePointsSystem.getBreakthroughs(serverPlayer);
        if (n2 >= 5) {
            return "\u00a7cPersonal level cap fully raised (\u00a7f" + DmzRewards.formatWhole((double)150000.0) + "\u00a7c).";
        }
        int n3 = n2 + 1;
        int n4 = PrestigePointsSystem.breakthroughCost(n3);
        int n5 = PrestigePointsSystem.getPoints(serverPlayer);
        if (n5 < n4) {
            return "\u00a7cNeed \u00a7e" + n4 + " \u00a7cfavor for breakthrough \u00a7f#" + n3 + " \u00a7c(have \u00a7e" + n5 + "\u00a7c).";
        }
        PrestigePointsSystem.setPoints(serverPlayer, n5 - n4);
        PrestigePointsSystem.setBreakthroughs(serverPlayer, n3);
        int n6 = n = PrestigePointsSystem.effectiveMaxLevel(serverPlayer);
        int n7 = 0;
        try {
            StatsData statsData = DmzProgression.stats((Player)serverPlayer);
            if (statsData != null) {
                n6 = Math.max(n, statsData.getConfiguredMaxValue());
                n7 = Math.max(0, statsData.getConfiguredMaxTotalStats());
                DmzSkillUtil.sync((ServerPlayer)serverPlayer);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        try {
            DmzRevampPrestigeBridge.syncFromLegacy((ServerPlayer)serverPlayer);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        SystemTelemetry.log((String)KEY_POINTS, (String)"breakthrough", (ServerPlayer)serverPlayer, null, Map.of("breakthrough", n3, "cap", n, "live_cap", n6, "live_max_stats", n7, "cost", n4, "points", PrestigePointsSystem.getPoints(serverPlayer)));
        object = n6 >= n ? "" : "\n\u00a7cWarning: live DMZ max still \u00a7f" + DmzRewards.formatWhole((double)n6) + " \u00a7c(expected \u00a7f" + DmzRewards.formatWhole((double)n) + "\u00a7c) \u2014 remount / report if this persists.";
        return "\u00a7aPersonal level cap raised to \u00a7f" + DmzRewards.formatWhole((double)n) + " \u00a77(\u00a7e-" + n4 + " \u00a77favor)\n\u00a77Keep leveling with TP / buy stats into the new cap.\n\u00a77Future prestige requirements now scale up to \u00a7f" + DmzRewards.formatWhole((double)n) + "\u00a77.\n\u00a78Others stay soft-locked at their personal cap until they breakthrough too.\n\u00a77Breakthrough \u00a7f" + n3 + "\u00a77/\u00a7f5 \u00b7 Favor left: \u00a7e" + PrestigePointsSystem.getPoints(serverPlayer) + "\n\u00a77Live DMZ max now: \u00a7f" + DmzRewards.formatWhole((double)n6) + (String)object;
    }

    public static void onLogin(ServerPlayer serverPlayer) {
        if (serverPlayer != null) {
            try {
                DmzRevampPrestigeBridge.syncFromLegacy((ServerPlayer)serverPlayer);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            try {
                LmOverhaulPrestigeIntegration.syncLmWalletFromOverhaulCount((ServerPlayer)serverPlayer);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            try {
                PrestigeSystem.reconcileNeedFloor((ServerPlayer)serverPlayer);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            ProgressionData.tempPut((ServerPlayer)serverPlayer, (String)KEY_REAPPLY_AT, (long)(System.currentTimeMillis() + 12000L));
            PrestigePointsSystem.reapplyAllShopPurchases(serverPlayer);
            PrestigeCustomShop.runReapplyCommands(serverPlayer);
        }
    }

    public static void scheduleReapplyAfterPrestige(ServerPlayer serverPlayer) {
        PrestigePointsSystem.scheduleReapplyAfterDeath(serverPlayer);
    }

    public static void scheduleReapplyAfterDeath(ServerPlayer serverPlayer) {
        if (serverPlayer != null) {
            long l = System.currentTimeMillis();
            ProgressionData.tempPut((ServerPlayer)serverPlayer, (String)KEY_REAPPLY_AT, (long)(l + 30000L));
            PrestigePointsSystem.reapplyAllShopPurchases(serverPlayer);
            PrestigeCustomShop.runReapplyCommands(serverPlayer);
            MinecraftServer minecraftServer = serverPlayer.m_20194_();
            if (minecraftServer != null) {
                UUID uUID = serverPlayer.m_20148_();
                minecraftServer.execute(() -> {
                    ServerPlayer sp = minecraftServer.m_6846_().m_11259_(uUID);
                    if (sp != null && sp.m_6084_()) {
                        PrestigePointsSystem.reapplyAllShopPurchases(sp);
                    }
                });
                int[] nArray = new int[]{20, 40, 80, 160, 300, 600};
                int n = nArray.length;
                for (int i = 0; i < n; ++i) {
                    int n2;
                    int n3 = n2 = nArray[i];
                    try {
                        minecraftServer.m_6937_(new TickTask(minecraftServer.m_129921_() + n3, () -> {
                            ServerPlayer sp = minecraftServer.m_6846_().m_11259_(uUID);
                            if (sp != null && sp.m_6084_()) {
                                PrestigePointsSystem.reapplyAllShopPurchases(sp);
                            }
                        }));
                        continue;
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                }
            }
        }
    }

    public static void reapplyAllShopPurchases(ServerPlayer serverPlayer) {
        if (serverPlayer != null) {
            PrestigePointsSystem.reapplySkillBonuses(serverPlayer);
            try {
                GodKiEnhancementShop.reapply(serverPlayer);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            try {
                DmzSkillUtil.sync((ServerPlayer)serverPlayer);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            try {
                DmzRevampPrestigeBridge.syncFromLegacy((ServerPlayer)serverPlayer);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            try {
                OverhaulPrestigeResourceScale.pulse((ServerPlayer)serverPlayer);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            PrestigeCustomShop.reapply(serverPlayer);
        }
    }

    public static void pulsePlayer(ServerPlayer serverPlayer, long l) {
        if (serverPlayer != null) {
            long l2;
            PrestigeCustomShop.pulse(serverPlayer, l);
            long l3 = ProgressionData.tempGetLong((ServerPlayer)serverPlayer, (String)KEY_REAPPLY_AT, (long)0L);
            if (l3 > 0L) {
                PrestigePointsSystem.reapplyAllShopPurchases(serverPlayer);
                if (l >= l3) {
                    ProgressionData.tempRemove((ServerPlayer)serverPlayer, (String)KEY_REAPPLY_AT);
                }
            }
            if (PrestigePointsSystem.hasAnyPurchasedSkillFloor(serverPlayer) && ((l2 = ProgressionData.tempGetLong((ServerPlayer)serverPlayer, (String)KEY_SKILL_PULSE_AT, (long)0L)) <= 0L || l >= l2)) {
                ProgressionData.tempPut((ServerPlayer)serverPlayer, (String)KEY_SKILL_PULSE_AT, (long)(l + 5000L));
                if (l3 <= 0L || l >= l3) {
                    PrestigePointsSystem.reapplySkillBonuses(serverPlayer);
                }
            }
            if ((PrestigePointsSystem.hasMajin(serverPlayer) || PrestigePointsSystem.hasMutant(serverPlayer) || PrestigePointsSystem.hasUltimate(serverPlayer)) && ((l2 = ProgressionData.tempGetLong((ServerPlayer)serverPlayer, (String)KEY_FORM_PULSE_AT, (long)0L)) <= 0L || l >= l2)) {
                ProgressionData.tempPut((ServerPlayer)serverPlayer, (String)KEY_FORM_PULSE_AT, (long)(l + 5000L));
                PrestigePointsSystem.reapplyForms(serverPlayer);
            }
        }
    }

    private static void runDmzEffect(ServerPlayer serverPlayer, String string, boolean bl) {
        if (serverPlayer != null && string != null && !string.isBlank()) {
            String string2 = string.trim().toLowerCase(Locale.ROOT);
            boolean bl2 = "mutant".equals(string2);
            try {
                StatsData statsData = DmzProgression.stats((Player)serverPlayer);
                if (statsData == null) {
                    return;
                }
                Effects effects = statsData.getEffects();
                if (effects == null) {
                    return;
                }
                if (bl2) {
                    if (bl) {
                        PrestigePointsSystem.ensureMutantSilent(serverPlayer, statsData, effects);
                    } else if (MutantManager.isMutant((StatsData)statsData)) {
                        MutantManager.revoke((ServerPlayer)serverPlayer, (StatsData)statsData);
                    }
                    return;
                }
                if (bl) {
                    PrestigePointsSystem.ensureEffectSilent(serverPlayer, effects, string2, PrestigePointsSystem.majinEffectPower());
                } else if (effects.hasEffect(string2)) {
                    effects.removeEffect(string2);
                    DmzSkillUtil.sync((ServerPlayer)serverPlayer);
                }
            }
            catch (Throwable throwable) {
                AdaptiveDifficultyMod.LOGGER.debug("[{}] prestige form API soft-fail ({}): {}", (Object)"legacymechanics", (Object)string2, (Object)throwable.toString());
            }
        }
    }

    private static void ensureMutantSilent(ServerPlayer serverPlayer, StatsData statsData, Effects effects) {
        if (MutantManager.isMutant((StatsData)statsData)) {
            try {
                MutantManager.reconcileHolder((ServerPlayer)serverPlayer, (StatsData)statsData);
            }
            catch (Throwable throwable) {}
        } else {
            effects.addEffect("mutant", 1.0, -1);
            try {
                MinecraftServer minecraftServer = serverPlayer.m_20194_();
                if (minecraftServer != null) {
                    MutantSavedData.get((MinecraftServer)minecraftServer).addHolder(serverPlayer.m_20148_());
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            DmzSkillUtil.sync((ServerPlayer)serverPlayer);
        }
    }

    private static void ensureEffectSilent(ServerPlayer serverPlayer, Effects effects, String string, double d) {
        if (!effects.hasEffect(string) || effects.getEffectDuration(string) != -1) {
            effects.addEffect(string, d, -1);
            DmzSkillUtil.sync((ServerPlayer)serverPlayer);
        }
    }

    private static double majinEffectPower() {
        try {
            Number number;
            GeneralServerConfig.GameplayConfig gameplayConfig = ConfigManager.getServerConfig().getGameplay();
            Method method = gameplayConfig.getClass().getMethod("getMajinPower", new Class[0]);
            Object object = method.invoke(gameplayConfig, new Object[0]);
            if (object instanceof Number && (number = (Number)object).doubleValue() > 0.0) {
                return number.doubleValue();
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return 1.0;
    }

    static {
        for (String string : DmzSkillUtil.SKILL_CHECK_NATURAL) {
            SkillOffer skillOffer = PrestigePointsSystem.resolveOffer(string);
            if (skillOffer == null) continue;
            SKILL_OFFERS.put(string, skillOffer);
        }
        TURN_IN_AMOUNTS = new int[]{1, 2, 3, 6, 9};
    }

    public record SkillOffer(String id, String label, int maxLevel) {
    }
}
