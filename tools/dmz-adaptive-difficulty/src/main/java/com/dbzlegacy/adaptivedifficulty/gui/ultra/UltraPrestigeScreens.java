package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.gui.ProgressionGuiApi;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraGui;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraScreenSpec;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeCustomShop;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeShopConfig;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeUniverseRoleWarn;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

public final class UltraPrestigeScreens {
    private UltraPrestigeScreens() {
    }

    public static void open(ServerPlayer serverPlayer, String string) {
        if (serverPlayer != null) {
            String string2 = string != null && !string.isBlank() ? string.trim() : "main";
            String string3 = string2.toLowerCase(Locale.ROOT);
            if (UltraPrestigeScreens.parseSkillsPage(string3) != null) {
                UltraPrestigeScreens.openSkillsShop(serverPlayer);
            } else if (string3.startsWith("perk:")) {
                UltraPrestigeScreens.openPerk(serverPlayer, string3.substring("perk:".length()));
            } else {
                switch (string3) {
                    case "turnin": 
                    case "points": 
                    case "prestige": {
                        UltraPrestigeScreens.openTurnIn(serverPlayer);
                        break;
                    }
                    case "confirm": 
                    case "prestige_confirm": {
                        UltraPrestigeScreens.openConfirm(serverPlayer);
                        break;
                    }
                    case "shop": 
                    case "shop_hub": 
                    case "catalog": {
                        UltraPrestigeScreens.openShopHub(serverPlayer);
                        break;
                    }
                    case "exchange": 
                    case "turnin_shop": 
                    case "favor_shop": {
                        UltraPrestigeScreens.openExchange(serverPlayer);
                        break;
                    }
                    case "skills": 
                    case "skill": 
                    case "skills_p0": {
                        UltraPrestigeScreens.openSkillsShop(serverPlayer);
                        break;
                    }
                    case "tiers": 
                    case "tier": 
                    case "difficulty": {
                        UltraPrestigeScreens.openTiers(serverPlayer);
                        break;
                    }
                    case "effects": 
                    case "effect": 
                    case "forms": 
                    case "form": {
                        UltraPrestigeScreens.openEffects(serverPlayer);
                        break;
                    }
                    case "custom": 
                    case "perks": {
                        UltraPrestigeScreens.openCustom(serverPlayer);
                        break;
                    }
                    case "staff_edit": {
                        if (StaffAccess.isStaff((ServerPlayer)serverPlayer)) {
                            UltraGui.open((ServerPlayer)serverPlayer, (String)"config", (String)"edit:prestige-shop:0:");
                            break;
                        }
                        UltraPrestigeScreens.openShopHub(serverPlayer);
                        break;
                    }
                    default: {
                        UltraPrestigeScreens.openHome(serverPlayer);
                    }
                }
            }
        }
    }

    private static Integer parseSkillsPage(String string) {
        if (string == null || string.isBlank()) {
            return null;
        }
        if (string.startsWith("skills_p")) {
            return UltraPrestigeScreens.parseNonNegInt(string.substring("skills_p".length()));
        }
        if (string.startsWith("skills:")) {
            return UltraPrestigeScreens.parseNonNegInt(string.substring("skills:".length()));
        }
        if (!string.startsWith("shop:skills:") && !string.startsWith("shop:skills_p")) {
            String string2;
            if (string.startsWith("shop:") && !(string2 = string.substring("shop:".length()).trim()).isEmpty() && string2.chars().allMatch(Character::isDigit)) {
                return UltraPrestigeScreens.parseNonNegInt(string2);
            }
            return null;
        }
        return 0;
    }

    private static Integer parseNonNegInt(String string) {
        try {
            int n = Integer.parseInt(string == null ? "" : string.trim());
            return Math.max(0, n);
        }
        catch (NumberFormatException numberFormatException) {
            return 0;
        }
    }

    private static UltraScreenSpec base(String string, String string2) {
        return UltraScreenSpec.of((String)"prestige").title("Prestige").page(string).activeTab(string2).tabs(new String[]{"home|Home", "prestige|Prestige", "shop|Shop"});
    }

    private static UltraScreenSpec sub(String string) {
        return UltraScreenSpec.of((String)"prestige").title("Prestige").page(string);
    }

    private static void paintWallet(UltraScreenSpec ultraScreenSpec, Map<String, String> map) {
        ultraScreenSpec.labelValue("Lifetime prestiges", map.getOrDefault("completed", "0"));
        ultraScreenSpec.labelValue("Current prestige amount", map.getOrDefault("held", "0") + "/" + map.getOrDefault("held_max", "10"));
        ultraScreenSpec.labelValue("Prestige favor", map.getOrDefault("points", "0"));
    }

    private static void paintFavorBalance(UltraScreenSpec ultraScreenSpec, Map<String, String> map) {
        ultraScreenSpec.labelValue("Prestige favor", map.getOrDefault("points", "0"));
    }

    private static void openHome(ServerPlayer serverPlayer) {
        Map<String, String> map = ProgressionGuiApi.prestigePlaceholders(serverPlayer);
        UltraScreenSpec ultraScreenSpec = UltraPrestigeScreens.base("main", "home").lineGap(2).bodyPad(4);
        UltraPrestigeScreens.paintWallet(ultraScreenSpec, map);
        if (!"true".equalsIgnoreCase(map.get("system_enabled"))) {
            ultraScreenSpec.line(UltraStyle.danger((String)"Prestige system is disabled."));
        } else {
            ultraScreenSpec.labelValue("Need", map.getOrDefault("required_fmt", "0"));
            if ("true".equalsIgnoreCase(map.get("ready"))) {
                ultraScreenSpec.line(UltraStyle.confirm((String)"Ready to prestige."));
            } else {
                ultraScreenSpec.line(UltraStyle.dim((String)"Need a higher level first."));
            }
        }
        ultraScreenSpec.buttonsAfterLines();
        ultraScreenSpec.footerBackToMain();
        ultraScreenSpec.send(serverPlayer);
    }

    private static void openTurnIn(ServerPlayer serverPlayer) {
        Map<String, String> map = ProgressionGuiApi.prestigePlaceholders(serverPlayer);
        UltraScreenSpec ultraScreenSpec = UltraPrestigeScreens.base("turnin", "prestige").lineGap(2).bodyPad(4);
        UltraPrestigeScreens.paintWallet(ultraScreenSpec, map);
        if (PrestigeShopConfig.get().exchange.enabled) {
            ultraScreenSpec.line(UltraStyle.LABEL + "Prestige favor - exchange current prestige on the Shop tab (loses the stat bonus).");
        }
        ultraScreenSpec.buttonsAfterLines();
        boolean bl = "true".equalsIgnoreCase(map.get("ready"));
        if (bl) {
            ultraScreenSpec.button("page:confirm", UltraStyle.danger((String)"Prestige now"), "danger").rowGap();
        } else {
            ultraScreenSpec.button("page:confirm", UltraStyle.dim((String)"Need higher level"), "dim").rowGap();
        }
        ultraScreenSpec.footerBackToMain();
        ultraScreenSpec.send(serverPlayer);
    }

    private static void openConfirm(ServerPlayer serverPlayer) {
        boolean bl;
        Map<String, String> map = ProgressionGuiApi.prestigePlaceholders(serverPlayer);
        UltraScreenSpec ultraScreenSpec = UltraPrestigeScreens.sub("confirm").lineGap(2).bodyPad(4).line(UltraStyle.LABEL + "Confirm prestige").labelValue("Current prestige amount", map.getOrDefault("held", "0") + "/" + map.getOrDefault("held_max", "10")).labelValue("Need", map.getOrDefault("required_fmt", "0")).notice("This resets your stats and adds one to your current prestige amount.");
        if (PrestigeUniverseRoleWarn.isGodOfDestruction((ServerPlayer)serverPlayer)) {
            ultraScreenSpec.line(UltraStyle.danger((String)"You are a God of Destruction - prestiging will remove you from your universe role."));
        }
        if (PrestigeUniverseRoleWarn.isSupremeKai((ServerPlayer)serverPlayer)) {
            ultraScreenSpec.line(UltraStyle.danger((String)"You are a Supreme Kai - prestiging will remove you from your universe role."));
        }
        if (!(bl = "true".equalsIgnoreCase(map.get("ready")))) {
            ultraScreenSpec.line(UltraStyle.danger((String)"You need a higher level before you can prestige."));
        }
        ultraScreenSpec.buttonsAfterLines();
        if (bl) {
            ultraScreenSpec.button("do:confirm_now", UltraStyle.danger((String)"Confirm prestige"), "danger").rowGap();
        }
        ultraScreenSpec.footerBack("page:turnin", "Prestige");
        ultraScreenSpec.send(serverPlayer);
    }

    private static void openShopHub(ServerPlayer serverPlayer) {
        Map<String, String> map = ProgressionGuiApi.prestigePlaceholders(serverPlayer);
        PrestigeShopConfig prestigeShopConfig = PrestigeShopConfig.get();
        UltraScreenSpec ultraScreenSpec = UltraPrestigeScreens.base("shop", "shop").lineGap(2).bodyPad(4);
        UltraPrestigeScreens.paintFavorBalance(ultraScreenSpec, map);
        ultraScreenSpec.line(UltraStyle.LABEL + "Spend Prestige favor. Purchases survive prestige.").buttonsAfterLines();
        if (prestigeShopConfig.exchange.enabled) {
            ultraScreenSpec.button("page:exchange", UltraStyle.confirm((String)"Exchange shop"), "confirm").rowGap();
        }
        if (prestigeShopConfig.skillShop.enabled) {
            ultraScreenSpec.button("page:skills", UltraStyle.confirm((String)"Skill shop"), "confirm").rowGap();
        }
        if (prestigeShopConfig.tierShop.enabled) {
            ultraScreenSpec.button("page:tiers", UltraStyle.confirm((String)"Difficulty tiers"), "confirm").rowGap();
        }
        ultraScreenSpec.button("page:effects", UltraStyle.confirm((String)"Effects"), "confirm").rowGap();
        if (prestigeShopConfig.customShop.enabled && !UltraPrestigeScreens.customRows(serverPlayer).isEmpty()) {
            ultraScreenSpec.button("page:custom", UltraStyle.confirm((String)UltraStyle.buttonText((String)prestigeShopConfig.customShop.menuLabel)), "confirm").rowGap();
        }
        if (StaffAccess.isStaff((ServerPlayer)serverPlayer)) {
            ultraScreenSpec.button("page:staff_edit", UltraStyle.dim((String)"Edit shop (staff)"), "dim").rowGap();
        }
        ultraScreenSpec.footerBackToMain();
        ultraScreenSpec.send(serverPlayer);
    }

    private static void openExchange(ServerPlayer serverPlayer) {
        Map<String, String> map = ProgressionGuiApi.prestigePlaceholders(serverPlayer);
        int n = UltraPrestigeScreens.parseInt(map.get("held"), 0);
        UltraScreenSpec ultraScreenSpec = UltraPrestigeScreens.sub("exchange").lineGap(2).bodyPad(4);
        UltraPrestigeScreens.paintFavorBalance(ultraScreenSpec, map);
        ultraScreenSpec.labelValue("Current prestige amount", map.getOrDefault("held", "0") + "/" + map.getOrDefault("held_max", "10"));
        ultraScreenSpec.line(UltraStyle.danger((String)"Exchanging prestige for Prestige favor removes the stat bonus."));
        int[] nArray = PrestigePointsSystem.turnInAmounts();
        StringBuilder stringBuilder = new StringBuilder();
        for (int n2 : nArray) {
            int n3 = PrestigePointsSystem.pointsForTurnIn(n2);
            if (n3 <= n2) continue;
            stringBuilder.append(stringBuilder.length() == 0 ? "Bulk: " : ", ").append(n2).append(" for ").append(n3).append(" favor");
        }
        if (stringBuilder.length() > 0) {
            ultraScreenSpec.line(UltraStyle.LABEL + String.valueOf(stringBuilder) + ".");
        }
        ultraScreenSpec.buttonsAfterLines();
        if (!PrestigeShopConfig.get().exchange.enabled) {
            ultraScreenSpec.line(UltraStyle.dim((String)"The exchange is closed right now."));
        } else {
            for (int i = 0; i < nArray.length; i += 2) {
                UltraPrestigeScreens.exchangeButton(ultraScreenSpec, nArray[i], n, true);
                if (i + 1 < nArray.length) {
                    UltraPrestigeScreens.exchangeButton(ultraScreenSpec, nArray[i + 1], n, false);
                }
                ultraScreenSpec.rowGap();
            }
        }
        ultraScreenSpec.footerBack("page:shop", "Shop");
        ultraScreenSpec.send(serverPlayer);
    }

    private static void exchangeButton(UltraScreenSpec ultraScreenSpec, int n, int n2, boolean bl) {
        String string;
        String string2;
        String string3 = String.valueOf(PrestigePointsSystem.pointsForTurnIn(n));
        String string4 = string2 = n2 >= n ? "Exchange " + n + " (+" + string3 + " favor)" : "Exchange " + n + " - need " + n;
        if (!UltraStyle.fitsButton((String)string2)) {
            string2 = n2 >= n ? "Exchange " + n : "Need " + n;
        }
        String string5 = string = n2 >= n ? UltraStyle.confirm((String)string2) : UltraStyle.dim((String)string2);
        if (bl) {
            ultraScreenSpec.buttonLeft("do:turnin:" + n, string, n2 >= n ? "confirm" : "dim");
        } else {
            ultraScreenSpec.buttonRight("do:turnin:" + n, string, n2 >= n ? "confirm" : "dim");
        }
    }

    private static void openSkillsShop(ServerPlayer serverPlayer) {
        Map<String, String> map = ProgressionGuiApi.prestigePlaceholders(serverPlayer);
        PrestigeShopConfig.SkillShop skillShop = PrestigeShopConfig.get().skillShop;
        String[] stringArray = UltraPrestigeScreens.shopSkillIds(map);
        UltraScreenSpec ultraScreenSpec = UltraPrestigeScreens.sub("skills").lineGap(2).bodyPad(4);
        UltraPrestigeScreens.paintFavorBalance(ultraScreenSpec, map);
        StringBuilder stringBuilder = new StringBuilder("Skill levels - " + skillShop.costPerPurchase + " favor each");
        if (skillShop.levelsPerPurchase > 1) {
            stringBuilder.append(" (+").append(skillShop.levelsPerPurchase).append(" levels)");
        }
        for (Map.Entry<String, Integer> stringArray2 : skillShop.levelsPerPurchaseOverrides.entrySet()) {
            if (stringArray2.getValue() == skillShop.levelsPerPurchase || !List.of(stringArray).contains(stringArray2.getKey())) continue;
            stringBuilder.append(stringBuilder.indexOf("(") < 0 ? " (" : ", ").append(map.getOrDefault("skill_" + stringArray2.getKey() + "_label", stringArray2.getKey())).append(" +").append(stringArray2.getValue());
        }
        if (stringBuilder.indexOf("(") >= 0 && stringBuilder.charAt(stringBuilder.length() - 1) != ')') {
            stringBuilder.append(')');
        }
        ultraScreenSpec.line(UltraStyle.LABEL + String.valueOf(stringBuilder)).buttonsAfterLines();
        if (!skillShop.enabled) {
            ultraScreenSpec.line(UltraStyle.dim((String)"The skill shop is closed right now."));
            ultraScreenSpec.footerBack("page:shop", "Shop");
            ultraScreenSpec.send(serverPlayer);
            return;
        }
        int n = 0;
        for (String string : stringArray) {
            if (string == null || string.isBlank()) continue;
            String string2 = map.getOrDefault("skill_" + string + "_label", string);
            int n2 = UltraPrestigeScreens.parseInt(map.get("skill_" + string), 0);
            int n3 = UltraPrestigeScreens.parseInt(map.get("skill_" + string + "_max"), 0);
            int n4 = skillShop.costFor(string);
            boolean bl = n3 > 0 && n2 >= n3;
            String string3 = UltraPrestigeScreens.skillCaption(string2, n2, n3, bl, n4 != skillShop.costPerPurchase ? n4 : -1);
            String string4 = "do:skill:" + string;
            if (n % 2 == 0) {
                ultraScreenSpec.buttonLeft(string4, bl ? UltraStyle.dim((String)string3) : UltraStyle.confirm((String)string3), bl ? "dim" : "confirm");
            } else {
                ultraScreenSpec.buttonRight(string4, bl ? UltraStyle.dim((String)string3) : UltraStyle.confirm((String)string3), bl ? "dim" : "confirm");
                ultraScreenSpec.rowGap();
            }
            ++n;
        }
        if (n % 2 == 1) {
            ultraScreenSpec.rowGap();
        }
        if (n == 0) {
            ultraScreenSpec.line(UltraStyle.dim((String)"No skills for sale right now."));
        }
        ultraScreenSpec.footerBack("page:shop", "Shop");
        ultraScreenSpec.send(serverPlayer);
    }

    private static void openTiers(ServerPlayer serverPlayer) {
        Map<String, String> map = ProgressionGuiApi.prestigePlaceholders(serverPlayer);
        PrestigeShopConfig.TierShop tierShop = PrestigeShopConfig.get().tierShop;
        UltraScreenSpec ultraScreenSpec = UltraPrestigeScreens.sub("tiers").lineGap(2).bodyPad(4);
        UltraPrestigeScreens.paintFavorBalance(ultraScreenSpec, map);
        ultraScreenSpec.line(UltraStyle.LABEL + (tierShop.requireInOrder ? "Difficulty tiers - buy in order, activate with coins." : "Difficulty tiers - activate with coins.")).labelValue("Highest owned", "T" + map.getOrDefault("tier_highest_purchased", "0")).buttonsAfterLines();
        for (int i = 1; i <= 7; ++i) {
            String string;
            String string2 = map.getOrDefault("tier_" + i + "_label", "T" + i);
            String string3 = map.getOrDefault("tier_" + i + "_cost", "?");
            boolean bl = "true".equalsIgnoreCase(map.get("tier_" + i + "_owned"));
            boolean bl2 = "true".equalsIgnoreCase(map.get("tier_" + i + "_can_buy"));
            String string4 = "do:tier:" + i;
            String string5 = bl ? "owned" : (!tierShop.forSale(i) ? "n/a" : (bl2 ? string3 : "lock"));
            String string6 = "T" + i + " " + string2 + " " + string5;
            String string7 = string = UltraStyle.fitsButton((String)string6) ? string6 : "T" + i + " " + string5;
            if (!bl && bl2) {
                ultraScreenSpec.button(string4, UltraStyle.confirm((String)string), "confirm").rowGap();
                continue;
            }
            ultraScreenSpec.button(string4, UltraStyle.dim((String)string), "dim").rowGap();
        }
        ultraScreenSpec.footerBack("page:shop", "Shop");
        ultraScreenSpec.send(serverPlayer);
    }

    private static void openEffects(ServerPlayer serverPlayer) {
        boolean bl;
        Map<String, String> map = ProgressionGuiApi.prestigePlaceholders(serverPlayer);
        PrestigeShopConfig prestigeShopConfig = PrestigeShopConfig.get();
        PrestigeShopConfig.Forms forms = prestigeShopConfig.forms;
        boolean bl2 = "true".equalsIgnoreCase(map.get("majin"));
        boolean bl3 = "true".equalsIgnoreCase(map.get("mutant"));
        boolean bl4 = "true".equalsIgnoreCase(map.get("majin_can_buy"));
        boolean bl5 = "true".equalsIgnoreCase(map.get("mutant_can_buy"));
        boolean bl6 = "true".equalsIgnoreCase(map.get("ultimate"));
        boolean bl7 = "true".equalsIgnoreCase(map.get("ultimate_can_buy"));
        boolean bl8 = "true".equalsIgnoreCase(map.get("ultimate_earned"));
        UltraScreenSpec ultraScreenSpec = UltraPrestigeScreens.sub("effects").lineGap(2).bodyPad(4);
        UltraPrestigeScreens.paintFavorBalance(ultraScreenSpec, map);
        boolean bl9 = forms.majin.enabled || bl2;
        boolean bl10 = bl = forms.mutant.enabled || bl3;
        if (bl9 || bl) {
            String string = forms.majin.cost == forms.mutant.cost || !bl9 || !bl ? (bl9 && bl ? "Majin / Mutant - " : (bl9 ? "Majin - " : "Mutant - ")) + (bl9 ? forms.majin.cost : forms.mutant.cost) + " favor" : "Majin " + forms.majin.cost + " / Mutant " + forms.mutant.cost + " favor";
            ultraScreenSpec.line(UltraStyle.LABEL + string + (forms.majinMutantExclusive && bl9 && bl ? ". One at a time." : ".")).buttonsAfterLines();
            if (bl2) {
                ultraScreenSpec.button("do:unmajin", UltraStyle.danger((String)"Remove Majin"), "danger").rowGap();
            } else if (forms.majin.enabled) {
                if (bl4) {
                    ultraScreenSpec.button("do:majin", UltraStyle.confirm((String)"Buy Majin"), "confirm").rowGap();
                } else {
                    ultraScreenSpec.button("do:majin", UltraStyle.dim((String)"Need Mutant off"), "dim").rowGap();
                }
            }
            if (bl3) {
                ultraScreenSpec.button("do:unmutant", UltraStyle.danger((String)"Remove Mutant"), "danger").rowGap();
            } else if (forms.mutant.enabled) {
                if (bl5) {
                    ultraScreenSpec.button("do:mutant", UltraStyle.confirm((String)"Buy Mutant"), "confirm").rowGap();
                } else {
                    ultraScreenSpec.button("do:mutant", UltraStyle.dim((String)"Need Majin off"), "dim").rowGap();
                }
            }
        } else {
            ultraScreenSpec.buttonsAfterLines();
        }
        if (forms.ultimate.enabled || bl6) {
            ultraScreenSpec.line(UltraStyle.LABEL + "Ultimate - " + forms.ultimate.cost + " favor. Stacks with Majin and Mutant.");
            if (bl6) {
                ultraScreenSpec.button("do:unultimate", UltraStyle.danger((String)"Remove Ultimate"), "danger").rowGap();
            } else if (bl8) {
                ultraScreenSpec.button("do:ultimate", UltraStyle.dim((String)"Already unlocked"), "dim").rowGap();
            } else if (bl7) {
                ultraScreenSpec.button("do:ultimate", UltraStyle.confirm((String)"Buy Ultimate"), "confirm").rowGap();
            }
        }
        int n = UltraPrestigeScreens.parseInt(map.get("godki_invested"), 0);
        if (prestigeShopConfig.godKi.enabled || n > 0) {
            boolean bl11 = "true".equalsIgnoreCase(map.get("godki_maxed"));
            String string = map.getOrDefault("godki_cost", "1");
            String string2 = map.getOrDefault("godki_per_point", "1,000");
            String string3 = map.getOrDefault("godki_mastery", "0");
            ultraScreenSpec.line(UltraStyle.LABEL + "God Ki Enhancement - " + string + " favor per point (+" + string2 + " mastery).").labelValue("Invested", n + "/" + prestigeShopConfig.godKi.maxPoints).labelValue("God Ki mastery", string3);
            if (bl11) {
                ultraScreenSpec.button("do:godki", UltraStyle.dim((String)(n >= prestigeShopConfig.godKi.maxPoints ? "Fully invested" : "Not for sale")), "dim").rowGap();
            } else {
                ultraScreenSpec.button("do:godki", UltraStyle.confirm((String)("Invest God Ki (+" + string2 + ")")), "confirm").rowGap();
            }
        }
        ultraScreenSpec.footerBack("page:shop", "Shop");
        ultraScreenSpec.send(serverPlayer);
    }

    private static List<Map.Entry<String, PrestigeShopConfig.CustomEntry>> customRows(ServerPlayer serverPlayer) {
        ArrayList<Map.Entry<String, PrestigeShopConfig.CustomEntry>> arrayList = new ArrayList<Map.Entry<String, PrestigeShopConfig.CustomEntry>>();
        for (Map.Entry<String, PrestigeShopConfig.CustomEntry> entry : PrestigeShopConfig.get().customEntries.entrySet()) {
            if (!entry.getValue().enabled && PrestigeCustomShop.owned(serverPlayer, entry.getKey()) <= 0) continue;
            arrayList.add(entry);
        }
        return arrayList;
    }

    private static void openCustom(ServerPlayer serverPlayer) {
        Map<String, String> map = ProgressionGuiApi.prestigePlaceholders(serverPlayer);
        PrestigeShopConfig.CustomShop customShop = PrestigeShopConfig.get().customShop;
        UltraScreenSpec ultraScreenSpec = UltraPrestigeScreens.sub("custom").lineGap(2).bodyPad(4);
        UltraPrestigeScreens.paintFavorBalance(ultraScreenSpec, map);
        ultraScreenSpec.line(UltraStyle.LABEL + customShop.menuLabel + " - permanent, survive prestige.").buttonsAfterLines();
        List<Map.Entry<String, PrestigeShopConfig.CustomEntry>> list = customShop.enabled ? UltraPrestigeScreens.customRows(serverPlayer) : List.of();
        for (Map.Entry entry : list) {
            boolean bl;
            PrestigeShopConfig.CustomEntry customEntry = (PrestigeShopConfig.CustomEntry)entry.getValue();
            int n = PrestigeCustomShop.owned(serverPlayer, (String)entry.getKey());
            boolean bl2 = bl = customEntry.maxPurchases > 0 && n >= customEntry.maxPurchases;
            Object object = bl ? (customEntry.maxPurchases == 1 ? "owned" : "max") : customEntry.costFor(n) + " favor";
            Object object2 = customEntry.name + (String)(n > 0 && customEntry.maxPurchases != 1 ? " " + n : "") + " - " + (String)object;
            if (!UltraStyle.fitsButton((String)object2)) {
                object2 = UltraStyle.buttonText((String)customEntry.name);
            }
            boolean bl3 = !bl && PrestigeCustomShop.blockReason(serverPlayer, (String)entry.getKey(), customEntry) == null;
            ultraScreenSpec.button("page:perk:" + (String)entry.getKey(), bl3 ? UltraStyle.confirm((String)object2) : UltraStyle.dim((String)object2), bl3 ? "confirm" : "dim").rowGap();
        }
        if (list.isEmpty()) {
            ultraScreenSpec.line(UltraStyle.dim((String)"Nothing for sale right now."));
        }
        ultraScreenSpec.footerBack("page:shop", "Shop");
        ultraScreenSpec.send(serverPlayer);
    }

    private static void openPerk(ServerPlayer serverPlayer, String string) {
        Object object;
        boolean bl;
        PrestigeShopConfig.CustomEntry customEntry = PrestigeShopConfig.get().entry(string);
        if (customEntry == null) {
            UltraPrestigeScreens.openCustom(serverPlayer);
            return;
        }
        Map<String, String> map = ProgressionGuiApi.prestigePlaceholders(serverPlayer);
        int n = PrestigeCustomShop.owned(serverPlayer, string);
        UltraScreenSpec ultraScreenSpec = UltraPrestigeScreens.sub("perk:" + string).lineGap(2).bodyPad(4);
        UltraPrestigeScreens.paintFavorBalance(ultraScreenSpec, map);
        ultraScreenSpec.line(UltraStyle.LABEL + customEntry.name);
        if (!customEntry.description.isBlank()) {
            ultraScreenSpec.line(UltraStyle.dim((String)customEntry.description));
        }
        boolean bl2 = bl = customEntry.maxPurchases > 0 && n >= customEntry.maxPurchases;
        if (customEntry.maxPurchases != 1) {
            ultraScreenSpec.labelValue("Owned", n + (String)(customEntry.maxPurchases > 0 ? "/" + customEntry.maxPurchases : ""));
        } else {
            ultraScreenSpec.labelValue("Owned", n > 0 ? "yes" : "no");
        }
        if (!bl) {
            ultraScreenSpec.labelValue("Price", customEntry.costFor(n) + " favor");
        }
        Iterator<String> iterator = PrestigeCustomShop.rewardLines(customEntry).iterator();
        while (iterator.hasNext()) {
            object = iterator.next();
            ultraScreenSpec.line(UltraStyle.dim((String)object));
        }
        if (customEntry.maxPurchases != 1) {
            ultraScreenSpec.line(UltraStyle.dim((String)"Each level adds the rewards again."));
        }
        String blockReason = bl ? null : PrestigeCustomShop.blockReason(serverPlayer, string, customEntry);
        if (blockReason != null) {
            ultraScreenSpec.line(UltraStyle.danger(blockReason));
        }
        if (StaffAccess.isStaff((ServerPlayer)serverPlayer)) {
            for (String string2 : PrestigeCustomShop.problems(string, customEntry)) {
                ultraScreenSpec.line(UltraStyle.danger((String)("Staff: " + string2)));
            }
            ultraScreenSpec.line(UltraStyle.dim((String)("Staff: id " + string + (customEntry.enabled ? "" : " (not for sale)"))));
        }
        ultraScreenSpec.buttonsAfterLines();
        if (!bl) {
            object = (String)(n > 0 ? "Buy level " + (n + 1) : "Buy") + " (" + customEntry.costFor(n) + ")";
            ultraScreenSpec.button("do:custom_buy:" + string, blockReason == null ? UltraStyle.confirm((String)object) : UltraStyle.dim((String)object), blockReason == null ? "confirm" : "dim").rowGap();
        }
        if (n > 0 && customEntry.removable) {
            ultraScreenSpec.button("do:custom_remove:" + string, UltraStyle.danger((String)(customEntry.maxPurchases == 1 ? "Remove" : "Remove one level")), "danger").rowGap();
        }
        ultraScreenSpec.footerBack("page:custom", UltraStyle.buttonText((String)PrestigeShopConfig.get().customShop.menuLabel));
        ultraScreenSpec.send(serverPlayer);
    }

    private static String skillCaption(String string, int n, int n2, boolean bl, int n3) {
        String string2 = string == null ? "" : string;
        String string3 = switch (string2) {
            case "Instant Transmission" -> "Transmit";
            case "Defense Penetration" -> "Def Pen";
            case "Potential Unlock" -> "Potential";
            case "Ki Manipulation" -> "Ki Manip";
            case "Healing Reduction" -> "Heal Cut";
            case "Ki Protection" -> "Ki Prot";
            default -> string != null && !string.isBlank() ? string : "Skill";
        };
        Object object = n + "/" + String.valueOf(n2 > 0 ? Integer.valueOf(n2) : "?");
        String string4 = n3 >= 0 && !bl ? " (" + n3 + ")" : "";
        String string5 = string3 + " " + (String)object + (bl ? " max" : string4);
        if (UltraStyle.fitsButton((String)string5)) {
            return string5;
        }
        String string6 = string3 + " " + (String)object;
        return UltraStyle.fitsButton((String)string6) ? string6 : UltraStyle.buttonText((String)string3);
    }

    private static String[] shopSkillIds(Map<String, String> map) {
        String string2 = map.getOrDefault("shop_skill_ids", "");
        if (string2 != null && !string2.isBlank()) {
            return (String[])Arrays.stream(string2.split(",")).filter(string -> string != null && !string.isBlank()).map(string -> string.trim().toLowerCase(Locale.ROOT)).toArray(String[]::new);
        }
        return new String[]{"meditation", "fly", "sprint", "jump", "potentialunlock"};
    }

    private static int parseInt(String string, int n) {
        try {
            return Integer.parseInt(string == null ? "" : string.trim());
        }
        catch (NumberFormatException numberFormatException) {
            return n;
        }
    }
}
