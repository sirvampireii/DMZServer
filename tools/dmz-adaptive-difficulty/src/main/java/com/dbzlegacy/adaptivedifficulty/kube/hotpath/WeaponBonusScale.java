package com.dbzlegacy.adaptivedifficulty.kube.hotpath;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.BonusStats;
import com.google.common.collect.Multimap;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.ForgeRegistries;

public final class WeaponBonusScale {
    public static final int UPDATE_INTERVAL = 20;
    private static final int SLOT_9 = 8;
    private static final String VERSION = "DMZ_WEAPON_MULTIPLIERS_V12_CONFIG_RATIOS";
    private static final String BONUS_MAIN = "DMZ Weapon Mainhand";
    private static final String BONUS_OFF = "DMZ Weapon Offhand";
    private static final String BONUS_SLOT9 = "DMZ Weapon Slot 9";
    private static final String[] CLEAR_STATS = new String[]{"STR", "SKP", "DEF", "STM", "RES", "VIT", "PWR", "ENE"};
    private static final String[] OLD_SLOTS = new String[]{"MAIN", "OFF", "SLOT9"};
    private static final Map<UUID, String> SIGNATURES = new ConcurrentHashMap<UUID, String>();
    private static final Map<Class<?>, Method> TOOL_DEFINITION = new ConcurrentHashMap();
    private static final Set<Class<?>> NO_TOOL_DEFINITION = ConcurrentHashMap.newKeySet();

    private WeaponBonusScale() {
    }

    public static void clearPlayer(UUID uUID) {
        if (uUID != null) {
            SIGNATURES.remove(uUID);
        }
    }

    public static void update(ServerPlayer serverPlayer) {
        if (serverPlayer != null) {
            try {
                WeaponRatioConfig.maybeReload();
                VipersCallBlacklist.tick();
                if (WeaponRatioConfig.discoveryPending()) {
                    WeaponBonusScale.discoverWeapons();
                }
                WeaponBonusScale.updateInner(serverPlayer);
            }
            catch (Throwable throwable) {
                HotLog.debug((String)("[DMZ Weapon V12 ERROR] " + String.valueOf(throwable)));
            }
        }
    }

    private static void updateInner(ServerPlayer serverPlayer) {
        String string;
        StatsData statsData = DmzProgression.stats((Player)serverPlayer);
        if (statsData == null) {
            return;
        }
        BonusStats bonusStats = statsData.getBonusStats();
        if (bonusStats == null) {
            return;
        }
        UUID uUID = serverPlayer.m_20148_();
        if (!HotPathConfig.get().weaponBonus.enabled) {
            if (!"OFF".equals(SIGNATURES.get(uUID))) {
                WeaponBonusScale.clearWeaponBonuses(bonusStats);
                SIGNATURES.put(uUID, "OFF");
                WeaponBonusScale.sync(serverPlayer);
            }
            return;
        }
        WeaponRatioConfig.Snapshot snapshot = WeaponRatioConfig.get();
        boolean bl = serverPlayer.m_150109_().f_35977_ == 8;
        ItemStack itemStack = serverPlayer.m_21205_();
        ItemStack itemStack2 = serverPlayer.m_21206_();
        ItemStack itemStack3 = serverPlayer.m_150109_().m_8020_(8);
        Info info = WeaponBonusScale.identify(itemStack, "MAIN", snapshot);
        Info info2 = WeaponBonusScale.identify(itemStack2, "OFF", snapshot);
        Info info3 = WeaponBonusScale.identify(itemStack3, "SLOT9", snapshot);
        Result result = WeaponBonusScale.resultOf(itemStack, "MAIN", info, snapshot);
        Result result2 = WeaponBonusScale.resultOf(itemStack2, "OFF", info2, snapshot);
        int n = (result == null ? 0 : 1) + (result2 == null ? 0 : 1);
        Result result3 = null;
        if (!bl && n < 2 && info3 != null && info3.weapon && info3.profile != null) {
            result3 = WeaponBonusScale.calculate(itemStack3, "SLOT9", info3, snapshot);
        }
        if (!(string = "DMZ_WEAPON_MULTIPLIERS_V12_CONFIG_RATIOS|SLOT9SELECTED=" + bl + "|MAINRAW=" + WeaponBonusScale.raw(itemStack, info) + "|OFFRAW=" + WeaponBonusScale.raw(itemStack2, info2) + "|SLOT9RAW=" + WeaponBonusScale.raw(itemStack3, info3) + "|MAIN=" + WeaponBonusScale.signed(result) + "|OFF=" + WeaponBonusScale.signed(result2) + "|SLOT9=" + WeaponBonusScale.signed(result3)).equals(SIGNATURES.get(uUID))) {
            WeaponBonusScale.clearWeaponBonuses(bonusStats);
            WeaponBonusScale.apply(bonusStats, result);
            WeaponBonusScale.apply(bonusStats, result2);
            WeaponBonusScale.apply(bonusStats, result3);
            SIGNATURES.put(uUID, string);
            WeaponBonusScale.sync(serverPlayer);
        }
    }

    private static void sync(ServerPlayer serverPlayer) {
        try {
            NetworkHandler.sendToPlayer((Object)new StatsSyncS2C(serverPlayer), (ServerPlayer)serverPlayer);
        }
        catch (Throwable throwable) {
            HotLog.debug((String)("[DMZ Weapon V12] Sync error: " + String.valueOf(throwable)));
        }
    }

    private static Result resultOf(ItemStack itemStack, String string, Info info, WeaponRatioConfig.Snapshot snapshot) {
        return info != null && info.profile != null ? WeaponBonusScale.calculate(itemStack, string, info, snapshot) : null;
    }

    static Result calculate(ItemStack itemStack, String string, Info info, WeaponRatioConfig.Snapshot snapshot) {
        double d;
        double d2 = 0.0;
        if (info.shield) {
            d = 1.0;
            if (snapshot.shieldScalesWithDurability) {
                double d3 = 336.0;
                try {
                    int n = itemStack.m_41776_();
                    if (n > 0) {
                        d3 = n;
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                d = d3 / 336.0;
            }
        } else {
            d2 = WeaponBonusScale.weaponDamage(itemStack, info) * WeaponDamageControl.multiplier((String)info.id, (String)info.family);
            if (!Double.isFinite(d2) || d2 < 0.0) {
                d2 = 0.0;
            }
            d = d2 / snapshot.damageDivisor;
            if (info.unique) {
                d *= snapshot.uniqueMultiplier;
            }
        }
        double[] dArray = new double[6];
        for (int i = 0; i < 6; ++i) {
            double d4 = info.profile[i];
            dArray[i] = Double.isFinite(d4) && d4 > 0.0 ? d4 * d : 0.0;
        }
        return new Result(string, info.id, info.family, info.definition, d2, dArray);
    }

    private static double weaponDamage(ItemStack itemStack, Info info) {
        double d = WeaponBonusScale.attackDamage(itemStack);
        if (info.ranged && d <= 1.0) {
            return WeaponRatioConfig.get().rangedWeaponDamage;
        }
        return d;
    }

    static Info identify(ItemStack itemStack, String string, WeaponRatioConfig.Snapshot snapshot) {
        double[] dArray;
        Object object;
        String string2;
        if (itemStack == null || itemStack.m_41619_()) {
            return null;
        }
        String string3 = ItemIds.id((ItemStack)itemStack);
        if (string3.isEmpty()) {
            return null;
        }
        if (WeaponBonusScale.isShield(itemStack, string3)) {
            return new Info(string3, "Shield", "shield", "", snapshot.shieldRatios, false, false, true, false);
        }
        boolean bl = WeaponBonusScale.isRanged(itemStack);
        boolean bl2 = snapshot.uniqueWeapons.contains(string3);
        double[] dArray2 = snapshot.weapons.get(string3);
        if (dArray2 != null) {
            return new Info(string3, "Config", "weapon", "", dArray2, bl2, true, false, bl);
        }
        if (snapshot.discovered.containsKey(string3)) {
            double[] dArray3 = snapshot.discovered.get(string3);
            if (dArray3 != null) {
                return new Info(string3, "Discovered", "discovered", "", dArray3, bl2, true, false, bl);
            }
            return WeaponBonusScale.defaultInfo(string3, string, snapshot, bl2, bl, "Discovered");
        }
        if (string3.startsWith("simplyswords:") || string3.startsWith("simplymore:")) {
            string2 = WeaponRatioConfig.simplyFamily(string3);
            object = string2 == null ? null : snapshot.families.get(string2);
            if (object != null) {
                String string4 = string3.startsWith("simplymore:") ? "Simply More" : "Simply Swords";
                return new Info(string3, string4, string2, "", (double[])object, bl2, true, false, bl);
            }
        }
        if ((object = (Object)((string2 = WeaponBonusScale.tinkersDefinition(itemStack)).isEmpty() ? null : WeaponBonusScale.normalizeTinkers(string2))) == null && string3.startsWith("tconstruct:")) {
            object = WeaponBonusScale.normalizeTinkers(string3);
        }
        if (object != null && (dArray = snapshot.tinkersFamilies.get(object)) != null) {
            return new Info(string3, "Tinkers", (String)object, string2, dArray, bl2, true, false, bl);
        }
        if (WeaponBonusScale.isWeaponCapable(itemStack)) {
            return WeaponBonusScale.defaultInfo(string3, string, snapshot, bl2, bl, string2.isEmpty() ? "Default" : "Tinkers");
        }
        return null;
    }

    private static Info defaultInfo(String string, String string2, WeaponRatioConfig.Snapshot snapshot, boolean bl, boolean bl2, String string3) {
        if (!snapshot.defaultSlots.contains(string2)) {
            return null;
        }
        return new Info(string, string3, "default", "", snapshot.defaultRatios, bl, true, false, bl2);
    }

    private static boolean isShield(ItemStack itemStack, String string) {
        return itemStack.m_41720_() instanceof ShieldItem || string.startsWith("tconstruct:") && WeaponBonusScale.path(string).toLowerCase(Locale.ROOT).contains("shield");
    }

    private static boolean isRanged(ItemStack itemStack) {
        try {
            return itemStack.m_41720_() instanceof ProjectileWeaponItem;
        }
        catch (Throwable throwable) {
            return false;
        }
    }

    static boolean isWeaponCapable(ItemStack itemStack) {
        return WeaponBonusScale.isRanged(itemStack) || WeaponBonusScale.attackDamage(itemStack) > 1.000001;
    }

    private static void discoverWeapons() {
        WeaponRatioConfig.markDiscoveryDone();
        WeaponRatioConfig.Snapshot snapshot = WeaponRatioConfig.get();
        if (!snapshot.autoDiscover) {
            return;
        }
        ArrayList<String> arrayList = new ArrayList<String>();
        int n = 0;
        try {
            for (ResourceLocation resourceLocation : ForgeRegistries.ITEMS.getKeys()) {
                String string = resourceLocation.toString();
                if (snapshot.weapons.containsKey(string) || snapshot.discovered.containsKey(string)) continue;
                try {
                    Info info;
                    Item item = (Item)ForgeRegistries.ITEMS.getValue(resourceLocation);
                    if (item == null) continue;
                    ++n;
                    ItemStack itemStack = new ItemStack((ItemLike)item, 1);
                    if (WeaponBonusScale.isShield(itemStack, string) || (info = WeaponBonusScale.identify(itemStack, "MAIN", snapshot)) == null || !"default".equals(info.family)) continue;
                    arrayList.add(string);
                }
                catch (Throwable throwable) {}
            }
        }
        catch (Throwable throwable) {
            HotLog.debug((String)("[DMZ Weapon V12] discovery failed: " + String.valueOf(throwable)));
            return;
        }
        HotLog.debug((String)("[DMZ Weapon V12] discovery scanned " + n + " items, " + arrayList.size() + " new weapon(s) using defaultRatios"));
        WeaponRatioConfig.addDiscovered(arrayList);
    }

    private static String normalizeTinkers(String string) {
        if (string == null || string.isEmpty()) {
            return null;
        }
        String string2 = WeaponBonusScale.path(string).toLowerCase(Locale.ROOT);
        if (WeaponBonusTables.tinkers(string2) != null || WeaponRatioConfig.get().tinkersFamilies.containsKey(string2)) {
            return string2;
        }
        if (string2.contains("scythe")) {
            return "scythe";
        }
        if (string2.contains("cleaver")) {
            return "cleaver";
        }
        if (string2.contains("broad_axe") || string2.contains("broadaxe")) {
            return "broad_axe";
        }
        if (string2.contains("hand_axe") || string2.contains("handaxe")) {
            return "hand_axe";
        }
        if (string2.contains("sledge_hammer") || string2.contains("sledgehammer")) {
            return "sledge_hammer";
        }
        if (string2.contains("vein_hammer") || string2.contains("veinhammer")) {
            return "vein_hammer";
        }
        if (string2.contains("war_pick") || string2.contains("warpick")) {
            return "war_pick";
        }
        if (string2.contains("mattock")) {
            return "mattock";
        }
        if (string2.contains("pickadze")) {
            return "pickadze";
        }
        if (string2.contains("excavator")) {
            return "excavator";
        }
        if (string2.contains("battlesign") || string2.contains("battle_sign")) {
            return "battlesign";
        }
        if (string2.contains("swasher")) {
            return "swasher";
        }
        if (string2.contains("minotaur_axe")) {
            return "minotaur_axe";
        }
        if (string2.contains("melting_pan") || string2.contains("meltingpan")) {
            return "melting_pan";
        }
        if (string2.contains("dagger")) {
            return "dagger";
        }
        if (string2.contains("kama")) {
            return "kama";
        }
        if (string2.contains("javelin")) {
            return "javelin";
        }
        if (string2.contains("shuriken")) {
            return "shuriken";
        }
        if (string2.contains("throwing_axe")) {
            return "throwing_axe";
        }
        if (string2.contains("crossbow")) {
            return "crossbow";
        }
        if (string2.contains("longbow")) {
            return "longbow";
        }
        if (string2.contains("sky_staff")) {
            return "sky_staff";
        }
        if (string2.contains("earth_staff")) {
            return "earth_staff";
        }
        if (string2.contains("ichor_staff")) {
            return "ichor_staff";
        }
        if (string2.contains("ender_staff")) {
            return "ender_staff";
        }
        return !"sword".equals(string2) && !string2.contains("_sword") && !string2.contains("sword_") ? null : "sword";
    }

    private static double attackDamage(ItemStack itemStack) {
        try {
            Multimap multimap = itemStack.m_41638_(EquipmentSlot.MAINHAND);
            if (multimap == null) {
                return 1.0;
            }
            Collection collection = multimap.get((Object)Attributes.f_22281_);
            if (collection == null || collection.isEmpty()) {
                return 1.0;
            }
            double d = 0.0;
            double d2 = 0.0;
            double d3 = 1.0;
            for (Object e : collection) {
                AttributeModifier attributeModifier = (AttributeModifier)e;
                double d4 = attributeModifier.m_22218_();
                AttributeModifier.Operation operation = attributeModifier.m_22217_();
                if (operation == AttributeModifier.Operation.ADDITION) {
                    d += d4;
                    continue;
                }
                if (operation == AttributeModifier.Operation.MULTIPLY_BASE) {
                    d2 += d4;
                    continue;
                }
                if (operation != AttributeModifier.Operation.MULTIPLY_TOTAL) continue;
                d3 *= 1.0 + d4;
            }
            double d5 = (1.0 + d) * (1.0 + d2) * d3;
            if (Double.isFinite(d5) && d5 > 0.0) {
                return d5;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return 1.0;
    }

    private static String tinkersDefinition(ItemStack itemStack) {
        Method method = WeaponBonusScale.toolDefinition(itemStack.m_41720_().getClass());
        if (method == null) {
            return "";
        }
        try {
            Object object = method.invoke(itemStack.m_41720_(), new Object[0]);
            if (object == null) {
                return "";
            }
            Object object2 = object.getClass().getMethod("getId", new Class[0]).invoke(object, new Object[0]);
            return object2 == null ? "" : String.valueOf(object2);
        }
        catch (Throwable throwable) {
            return "";
        }
    }

    private static Method toolDefinition(Class<?> clazz) {
        Method method = TOOL_DEFINITION.get(clazz);
        if (method == null && !NO_TOOL_DEFINITION.contains(clazz)) {
            try {
                Method method2 = clazz.getMethod("getToolDefinition", new Class[0]);
                TOOL_DEFINITION.put(clazz, method2);
                return method2;
            }
            catch (Throwable throwable) {
                NO_TOOL_DEFINITION.add(clazz);
                return null;
            }
        }
        return method;
    }

    private static void clearWeaponBonuses(BonusStats bonusStats) {
        WeaponBonusScale.removeName(bonusStats, BONUS_MAIN);
        WeaponBonusScale.removeName(bonusStats, BONUS_OFF);
        WeaponBonusScale.removeName(bonusStats, BONUS_SLOT9);
        for (String string : OLD_SLOTS) {
            for (String string2 : WeaponBonusTables.STATS) {
                WeaponBonusScale.removeName(bonusStats, "DMZLegacyCNPCWeapon_" + string + "_" + string2);
                WeaponBonusScale.removeName(bonusStats, "DMZLegacyWeapon_" + string + "_" + string2);
            }
        }
        WeaponBonusScale.removeName(bonusStats, "DMZLegacyWeapon_STR");
        WeaponBonusScale.removeName(bonusStats, "DMZLegacyWeapon_SKP");
        WeaponBonusScale.removeName(bonusStats, "DMZLegacyWeapon_RES");
        WeaponBonusScale.removeName(bonusStats, "DMZLegacyWeapon_VIT");
        WeaponBonusScale.removeName(bonusStats, "DMZLegacyWeapon_PWR");
        WeaponBonusScale.removeName(bonusStats, "DMZLegacyWeapon_ENE");
    }

    private static void removeName(BonusStats bonusStats, String string) {
        for (String string2 : CLEAR_STATS) {
            try {
                bonusStats.removeBonus(string2, string);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }

    private static void apply(BonusStats bonusStats, Result result) {
        if (result == null) {
            return;
        }
        for (int i = 0; i < WeaponBonusTables.STATS.length; ++i) {
            double d = result.stats[i];
            if (!Double.isFinite(d) || !(d > 0.0)) continue;
            String string = WeaponBonusTables.STATS[i];
            String string2 = WeaponBonusScale.bonusName(result.slot);
            double d2 = 1.0 + d;
            try {
                if ("RES".equals(string)) {
                    bonusStats.addBonusSplit("RES", string2, "*", d2, false);
                    continue;
                }
                bonusStats.addBonus(string, string2, "*", d2, false);
                continue;
            }
            catch (Throwable throwable) {
                HotLog.debug((String)("[DMZ Weapon V12] apply failed: " + String.valueOf(throwable)));
            }
        }
    }

    private static String bonusName(String string) {
        if ("MAIN".equals(string)) {
            return BONUS_MAIN;
        }
        return "OFF".equals(string) ? BONUS_OFF : BONUS_SLOT9;
    }

    private static String raw(ItemStack itemStack, Info info) {
        String string = ItemIds.id((ItemStack)itemStack);
        if (string.isEmpty()) {
            return "EMPTY";
        }
        return info == null ? string + ":NONE" : string + ":" + info.family + ":" + info.definition;
    }

    private static String signed(Result result) {
        if (result == null) {
            return "NONE";
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(result.slot).append(':').append(result.id).append(':').append(result.family).append(':').append(result.definition).append(':').append(String.format(Locale.US, "%.4f", result.damage));
        for (double d : result.stats) {
            stringBuilder.append(':').append(String.format(Locale.US, "%.6f", d));
        }
        return stringBuilder.toString();
    }

    private static String path(String string) {
        int n = string.indexOf(58);
        return n < 0 ? string : string.substring(n + 1);
    }

    static final class Info {
        final String id;
        final String source;
        final String family;
        final String definition;
        final double[] profile;
        final boolean unique;
        final boolean weapon;
        final boolean shield;
        final boolean ranged;

        Info(String string, String string2, String string3, String string4, double[] dArray, boolean bl, boolean bl2, boolean bl3, boolean bl4) {
            this.id = string;
            this.source = string2;
            this.family = string3;
            this.definition = string4;
            this.profile = dArray;
            this.unique = bl;
            this.weapon = bl2;
            this.shield = bl3;
            this.ranged = bl4;
        }
    }

    static final class Result {
        final String slot;
        final String id;
        final String family;
        final String definition;
        final double damage;
        final double[] stats;

        Result(String string, String string2, String string3, String string4, double d, double[] dArray) {
            this.slot = string;
            this.id = string2;
            this.family = string3;
            this.definition = string4;
            this.damage = d;
            this.stats = dArray;
        }
    }
}
