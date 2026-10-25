package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.gui.GuiClickConfirm;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.progression.DmzSkillUtil;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeShopConfig;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeSystem;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.BonusStats;
import com.dragonminez.common.stats.character.Effects;
import com.dragonminez.common.stats.skills.Skills;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class PrestigeCustomShop {
    static final String KEY_PREFIX = "pp_custom_";
    private static final String KEY_POTIONS = "pp_custom_fx";
    private static final String KEY_DMZ_EFFECTS = "pp_custom_dmzfx";
    private static final String KEY_PULSE_AT = "pp_custom_pulse_at_ms";
    private static final String KEY_PULSE_STAMP = "pp_custom_stamp";
    private static final String KEY_COMMANDS_AT = "pp_custom_cmd_at_ms";
    private static final String BONUS_PREFIX = "lm_pshop:";
    private static final String[] DMZ_STATS = new String[]{"STR", "SKP", "DEF", "STM", "VIT", "PWR", "ENE"};
    private static final Map<UUID, Map<UUID, Attribute>> APPLIED_ATTRIBUTES = new ConcurrentHashMap<UUID, Map<UUID, Attribute>>();
    private static final AtomicLong STAMP = new AtomicLong(1L);

    private PrestigeCustomShop() {
    }

    public static int owned(ServerPlayer serverPlayer, String string) {
        if (serverPlayer == null || string == null || string.isBlank()) {
            return 0;
        }
        return (int)Math.max(0L, Math.min(Integer.MAX_VALUE, ProgressionData.storedGetLong((ServerPlayer)serverPlayer, (String)(KEY_PREFIX + PrestigeShopConfig.cleanId(string)), (long)0L)));
    }

    private static void setOwned(ServerPlayer serverPlayer, String string, int n) {
        String string2 = KEY_PREFIX + PrestigeShopConfig.cleanId(string);
        if (n <= 0) {
            ProgressionData.storedRemove((ServerPlayer)serverPlayer, (String)string2);
        } else {
            ProgressionData.storedPut((ServerPlayer)serverPlayer, (String)string2, (long)n);
        }
    }

    public static List<Map.Entry<String, PrestigeShopConfig.CustomEntry>> forSale() {
        ArrayList<Map.Entry<String, PrestigeShopConfig.CustomEntry>> arrayList = new ArrayList<Map.Entry<String, PrestigeShopConfig.CustomEntry>>();
        PrestigeShopConfig prestigeShopConfig = PrestigeShopConfig.get();
        if (!prestigeShopConfig.customShop.enabled) {
            return arrayList;
        }
        for (Map.Entry<String, PrestigeShopConfig.CustomEntry> entry : prestigeShopConfig.customEntries.entrySet()) {
            if (!entry.getValue().enabled) continue;
            arrayList.add(entry);
        }
        return arrayList;
    }

    public static String blockReason(ServerPlayer serverPlayer, String string, PrestigeShopConfig.CustomEntry customEntry) {
        if (customEntry == null) {
            return "That perk no longer exists.";
        }
        if (!PrestigeShopConfig.get().customShop.enabled || !customEntry.enabled) {
            return "That perk is not for sale right now.";
        }
        int n = PrestigeCustomShop.owned(serverPlayer, string);
        if (customEntry.maxPurchases > 0 && n >= customEntry.maxPurchases) {
            return customEntry.maxPurchases == 1 ? "You already own it." : "Maxed (" + n + "/" + customEntry.maxPurchases + ").";
        }
        if (customEntry.requiredLifetimePrestiges > 0 && PrestigeSystem.getCompleted((ServerPlayer)serverPlayer) < customEntry.requiredLifetimePrestiges) {
            return "Needs " + customEntry.requiredLifetimePrestiges + " lifetime prestiges.";
        }
        if (customEntry.requiredLevel > 0 && PrestigeCustomShop.dmzLevel(serverPlayer) < customEntry.requiredLevel) {
            return "Needs level " + String.format(Locale.ROOT, "%,d", customEntry.requiredLevel) + ".";
        }
        PrestigeShopConfig prestigeShopConfig = PrestigeShopConfig.get();
        for (String string2 : customEntry.requiredEntries) {
            if (PrestigeCustomShop.owned(serverPlayer, string2) > 0) continue;
            PrestigeShopConfig.CustomEntry customEntry2 = prestigeShopConfig.entry(string2);
            return "Needs " + (customEntry2 == null ? string2 : customEntry2.name) + " first.";
        }
        for (String string2 : customEntry.blockedByEntries) {
            if (PrestigeCustomShop.owned(serverPlayer, string2) <= 0) continue;
            PrestigeShopConfig.CustomEntry customEntry3 = prestigeShopConfig.entry(string2);
            return "Can't be combined with " + (customEntry3 == null ? string2 : customEntry3.name) + ".";
        }
        return null;
    }

    public static String buy(ServerPlayer serverPlayer, String string) {
        PrestigeShopConfig.CustomEntry customEntry;
        if (serverPlayer == null) {
            return UltraStyle.dangerLine((String)"Players only.");
        }
        String string2 = PrestigeShopConfig.cleanId(string);
        String string3 = PrestigeCustomShop.blockReason(serverPlayer, string2, customEntry = PrestigeShopConfig.get().entry(string2));
        if (string3 != null) {
            return UltraStyle.dangerLine((String)string3);
        }
        if (!ProgressionData.storedWritable((ServerPlayer)serverPlayer)) {
            return UltraStyle.dangerLine((String)"Could not save prestige data - try relogging, then buy again.");
        }
        int n = PrestigeCustomShop.owned(serverPlayer, string2);
        int n2 = customEntry.costFor(n);
        int n3 = PrestigePointsSystem.getPoints(serverPlayer);
        if (n3 < n2) {
            return UltraStyle.dangerLine((String)("Need " + n2 + " favor (have " + n3 + ")."));
        }
        if (n2 > 0 && !GuiClickConfirm.confirmed((UUID)serverPlayer.m_20148_(), (String)("pshop-buy:" + string2 + ":" + n))) {
            return UltraStyle.noticeLine((String)("Buy " + customEntry.name + PrestigeCustomShop.levelSuffix(customEntry, n + 1) + " for " + n2 + " favor? Click again within 10 seconds."));
        }
        PrestigePointsSystem.setPoints(serverPlayer, n3 - n2);
        PrestigeCustomShop.setOwned(serverPlayer, string2, n + 1);
        PrestigeCustomShop.runCommands(serverPlayer, string2, customEntry, customEntry.commandsOnPurchase, n + 1);
        PrestigeCustomShop.reconcile(serverPlayer);
        SystemTelemetry.log((String)"prestige_points", (String)"buy_custom", (ServerPlayer)serverPlayer, null, Map.of("entry", string2, "count", n + 1, "cost", n2, "points", PrestigePointsSystem.getPoints(serverPlayer)));
        return UltraStyle.color((String)"a", (String)("Unlocked " + customEntry.name + PrestigeCustomShop.levelSuffix(customEntry, n + 1) + (String)(n2 > 0 ? " (-" + n2 + " favor)" : ""))) + "\n" + UltraStyle.dim((String)("Permanent - survives prestige. Favor left: " + PrestigePointsSystem.getPoints(serverPlayer)));
    }

    public static String remove(ServerPlayer serverPlayer, String string) {
        if (serverPlayer == null) {
            return UltraStyle.dangerLine((String)"Players only.");
        }
        String string2 = PrestigeShopConfig.cleanId(string);
        PrestigeShopConfig.CustomEntry customEntry = PrestigeShopConfig.get().entry(string2);
        int n = PrestigeCustomShop.owned(serverPlayer, string2);
        if (customEntry == null || n <= 0) {
            return UltraStyle.noticeLine((String)"You do not own that perk.");
        }
        if (!customEntry.removable) {
            return UltraStyle.dangerLine((String)(customEntry.name + " can't be removed."));
        }
        int n2 = (int)Math.floor((double)customEntry.costFor(n - 1) * (double)customEntry.refundPercent / 100.0);
        if (!GuiClickConfirm.confirmed((UUID)serverPlayer.m_20148_(), (String)("pshop-remove:" + string2 + ":" + n))) {
            return UltraStyle.noticeLine((String)("Remove " + customEntry.name + PrestigeCustomShop.levelSuffix(customEntry, n) + "? " + (String)(n2 > 0 ? "You get " + n2 + " favor back." : "No favor is refunded.") + " Click again within 10 seconds."));
        }
        PrestigeCustomShop.takeOne(serverPlayer, string2, customEntry, n);
        if (n2 > 0) {
            PrestigePointsSystem.setPoints(serverPlayer, PrestigePointsSystem.getPoints(serverPlayer) + n2);
        }
        SystemTelemetry.log((String)"prestige_points", (String)"remove_custom", (ServerPlayer)serverPlayer, null, Map.of("entry", string2, "count", n - 1, "refund", n2, "points", PrestigePointsSystem.getPoints(serverPlayer)));
        return UltraStyle.color((String)"a", (String)("Removed " + customEntry.name + PrestigeCustomShop.levelSuffix(customEntry, n) + (String)(n2 > 0 ? " (+" + n2 + " favor)" : ""))) + "\n" + UltraStyle.dim((String)("Favor: " + PrestigePointsSystem.getPoints(serverPlayer)));
    }

    private static void takeOne(ServerPlayer serverPlayer, String string, PrestigeShopConfig.CustomEntry customEntry, int n) {
        Map<String, Integer> map = PrestigeCustomShop.wantedSkills(serverPlayer);
        PrestigeCustomShop.setOwned(serverPlayer, string, n - 1);
        PrestigeCustomShop.runCommands(serverPlayer, string, customEntry, customEntry.commandsOnRemove, n - 1);
        if (customEntry.revokeSkillsOnRemove) {
            PrestigeCustomShop.revokeSkills(serverPlayer, map, PrestigeCustomShop.wantedSkills(serverPlayer));
        }
        PrestigeCustomShop.reconcile(serverPlayer);
    }

    public static String adminAdjust(ServerPlayer serverPlayer, String string, String string2, int n) {
        int n2;
        if (serverPlayer == null) {
            return UltraStyle.dangerLine((String)"Player not online.");
        }
        String string3 = PrestigeShopConfig.cleanId(string);
        PrestigeShopConfig.CustomEntry customEntry = PrestigeShopConfig.get().entry(string3);
        if (customEntry == null) {
            return UltraStyle.dangerLine((String)("No custom entry called " + string3 + ". List: /padmin shop list"));
        }
        if (!ProgressionData.storedWritable((ServerPlayer)serverPlayer)) {
            return UltraStyle.dangerLine((String)("Could not save prestige data for " + serverPlayer.m_6302_() + "."));
        }
        int n3 = PrestigeCustomShop.owned(serverPlayer, string3);
        switch (string2 == null ? "" : string2.toLowerCase(Locale.ROOT)) {
            case "set": {
                n2 = n;
                break;
            }
            case "give": 
            case "add": {
                n2 = n3 + n;
                break;
            }
            case "take": 
            case "remove": 
            case "sub": {
                n2 = n3 - n;
                break;
            }
            default: {
                return UltraStyle.dangerLine((String)"Usage: /padmin custom <player> <entry> <give|take|set> <n>");
            }
        }
        int n4 = customEntry.maxPurchases > 0 ? customEntry.maxPurchases : Integer.MAX_VALUE;
        n2 = Math.max(0, Math.min(n4, n2));
        while (n3 > n2) {
            PrestigeCustomShop.takeOne(serverPlayer, string3, customEntry, n3);
            --n3;
        }
        while (n3 < n2) {
            PrestigeCustomShop.setOwned(serverPlayer, string3, n3 + 1);
            PrestigeCustomShop.runCommands(serverPlayer, string3, customEntry, customEntry.commandsOnPurchase, n3 + 1);
            ++n3;
        }
        PrestigeCustomShop.reconcile(serverPlayer);
        SystemTelemetry.log((String)"prestige_admin", (String)"admin_custom", (ServerPlayer)serverPlayer, null, Map.of("entry", string3, "mode", string2, "after", n3));
        return UltraStyle.color((String)"a", (String)(customEntry.name + " for " + serverPlayer.m_6302_() + ": " + n3 + (String)(customEntry.maxPurchases > 0 ? "/" + customEntry.maxPurchases : "")));
    }

    public static List<String> ownedSummary(ServerPlayer serverPlayer) {
        ArrayList<String> arrayList = new ArrayList<String>();
        for (Map.Entry<String, PrestigeShopConfig.CustomEntry> entry : PrestigeShopConfig.get().customEntries.entrySet()) {
            int n = PrestigeCustomShop.owned(serverPlayer, entry.getKey());
            if (n <= 0) continue;
            arrayList.add(entry.getValue().name + " (" + entry.getKey() + ") x" + n);
        }
        return arrayList;
    }

    static void onConfigChanged() {
        STAMP.incrementAndGet();
        MinecraftServer minecraftServer = ServerLifecycleHooks.getCurrentServer();
        if (minecraftServer != null) {
            minecraftServer.execute(() -> {
                for (ServerPlayer serverPlayer : minecraftServer.m_6846_().m_11314_()) {
                    PrestigeCustomShop.reapply(serverPlayer);
                }
            });
        }
    }

    public static void reapply(ServerPlayer serverPlayer) {
        try {
            PrestigeCustomShop.reconcile(serverPlayer);
        }
        catch (Throwable throwable) {
            AdaptiveDifficultyMod.LOGGER.debug("[{}] custom prestige shop reapply soft-fail: {}", (Object)"legacymechanics", (Object)throwable.toString());
        }
    }

    public static void pulse(ServerPlayer serverPlayer, long l) {
        if (serverPlayer == null) {
            return;
        }
        long l2 = STAMP.get();
        long l3 = ProgressionData.tempGetLong((ServerPlayer)serverPlayer, (String)KEY_PULSE_AT, (long)0L);
        if (l >= l3 || ProgressionData.tempGetLong((ServerPlayer)serverPlayer, (String)KEY_PULSE_STAMP, (long)0L) != l2) {
            ProgressionData.tempPut((ServerPlayer)serverPlayer, (String)KEY_PULSE_AT, (long)(l + (long)PrestigeShopConfig.get().customShop.reapplySeconds * 1000L));
            ProgressionData.tempPut((ServerPlayer)serverPlayer, (String)KEY_PULSE_STAMP, (long)l2);
            PrestigeCustomShop.reapply(serverPlayer);
        }
    }

    public static void runReapplyCommands(ServerPlayer serverPlayer) {
        if (serverPlayer == null) {
            return;
        }
        long l = System.currentTimeMillis();
        if (l < ProgressionData.tempGetLong((ServerPlayer)serverPlayer, (String)KEY_COMMANDS_AT, (long)0L)) {
            return;
        }
        ProgressionData.tempPut((ServerPlayer)serverPlayer, (String)KEY_COMMANDS_AT, (long)(l + 10000L));
        for (Map.Entry<String, PrestigeShopConfig.CustomEntry> entry : PrestigeShopConfig.get().customEntries.entrySet()) {
            int n = PrestigeCustomShop.owned(serverPlayer, entry.getKey());
            if (n <= 0 || entry.getValue().commandsOnReapply.isEmpty()) continue;
            PrestigeCustomShop.runCommands(serverPlayer, entry.getKey(), entry.getValue(), entry.getValue().commandsOnReapply, n);
        }
    }

    private static void reconcile(ServerPlayer serverPlayer) {
        if (serverPlayer == null || !serverPlayer.m_6084_()) {
            return;
        }
        LinkedHashMap<String, Integer> linkedHashMap = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, PrestigeShopConfig.CustomEntry> statsData2 : PrestigeShopConfig.get().customEntries.entrySet()) {
            int n = PrestigeCustomShop.owned(serverPlayer, statsData2.getKey());
            if (n <= 0) continue;
            linkedHashMap.put(statsData2.getKey(), n);
        }
        boolean bl = PrestigeCustomShop.reconcilePotions(serverPlayer, linkedHashMap);
        PrestigeCustomShop.reconcileAttributes(serverPlayer, linkedHashMap);
        StatsData statsData = DmzProgression.stats((Player)serverPlayer);
        if (statsData != null) {
            bl |= PrestigeCustomShop.reconcileStats(statsData, linkedHashMap);
            bl |= PrestigeCustomShop.reconcileDmzEffects(serverPlayer, statsData, linkedHashMap);
            bl |= PrestigeCustomShop.applySkills(serverPlayer, PrestigeCustomShop.wantedSkills(serverPlayer));
        }
        if (bl) {
            DmzSkillUtil.sync((ServerPlayer)serverPlayer);
        }
    }

    private static boolean reconcilePotions(ServerPlayer serverPlayer, Map<String, Integer> map) {
        String string2;
        boolean bl = PrestigeShopConfig.get().customShop.hidePotionParticles;
        LinkedHashMap<String, PotionWant> linkedHashMap = new LinkedHashMap<String, PotionWant>();
        PrestigeCustomShop.forEachLine(map, customEntry -> customEntry.potionEffects, (string, n) -> {
            String[] stringArray = PrestigeCustomShop.split(string);
            MobEffect mobEffect = PrestigeCustomShop.effect(stringArray[0]);
            if (mobEffect == null) {
                return;
            }
            int n2 = Math.max(0, Math.min(255, (stringArray.length > 1 ? PrestigeCustomShop.parseInt(stringArray[1], 0) : 0) + n - 1));
            boolean bl2 = stringArray.length > 2 ? "show".equalsIgnoreCase(stringArray[2]) : !bl;
            String potionKey = stringArray[0].toLowerCase(Locale.ROOT);
            PotionWant potionWant = (PotionWant)linkedHashMap.get(potionKey);
            if (potionWant == null || potionWant.amplifier < n2) {
                linkedHashMap.put(potionKey, new PotionWant(mobEffect, potionKey, n2, bl2));
            }
        });
        for (String object2 : PrestigeCustomShop.splitList(ProgressionData.storedGet((ServerPlayer)serverPlayer, (String)KEY_POTIONS, (String)""))) {
            MobEffect mobEffect;
            MobEffectInstance mobEffectInstance;
            int potionWant = object2.lastIndexOf(64);
            string2 = potionWant > 0 ? object2.substring(0, potionWant) : object2;
            int n2 = potionWant > 0 ? PrestigeCustomShop.parseInt(object2.substring(potionWant + 1), 0) : 0;
            PotionWant potionWant2 = (PotionWant)linkedHashMap.get(string2);
            if (potionWant2 != null && potionWant2.amplifier == n2 || (mobEffectInstance = (mobEffect = PrestigeCustomShop.effect(string2)) == null ? null : serverPlayer.m_21124_(mobEffect)) == null || mobEffectInstance.m_19557_() != -1 || mobEffectInstance.m_19564_() != n2) continue;
            serverPlayer.m_21195_(mobEffect);
        }
        ArrayList arrayList = new ArrayList();
        for (PotionWant potionWant : linkedHashMap.values()) {
            MobEffectInstance active = serverPlayer.m_21124_(potionWant.effect);
            if (active == null || active.m_19564_() < potionWant.amplifier || active.m_19564_() == potionWant.amplifier && (active.m_19557_() != -1 || active.m_19572_() != potionWant.visible)) {
                serverPlayer.m_7292_(new MobEffectInstance(potionWant.effect, -1, potionWant.amplifier, true, potionWant.visible, true));
            }
            arrayList.add(potionWant.key + "@" + potionWant.amplifier);
        }
        PrestigeCustomShop.storeList(serverPlayer, KEY_POTIONS, arrayList);
        return false;
    }

    private static MobEffect effect(String string) {
        ResourceLocation resourceLocation = ResourceLocation.m_135820_((String)string.toLowerCase(Locale.ROOT));
        return resourceLocation == null ? null : (MobEffect)ForgeRegistries.MOB_EFFECTS.getValue(resourceLocation);
    }

    private static void reconcileAttributes(ServerPlayer serverPlayer, Map<String, Integer> map) {
        Map<UUID, Attribute> applied = APPLIED_ATTRIBUTES.computeIfAbsent(serverPlayer.m_20148_(), uUID -> new ConcurrentHashMap());
        HashMap<UUID, Attribute> wanted = new HashMap<UUID, Attribute>();
        HashMap<UUID, AttributeModifier> wantedModifiers = new HashMap<UUID, AttributeModifier>();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            PrestigeShopConfig.CustomEntry customEntry = PrestigeShopConfig.get().entry(entry.getKey());
            List<String> attributes = customEntry.attributes;
            for (int i = 0; i < attributes.size(); ++i) {
                String[] stringArray = PrestigeCustomShop.split(attributes.get(i));
                if (stringArray.length < 2) continue;
                ResourceLocation resourceLocation = ResourceLocation.m_135820_((String)stringArray[0].toLowerCase(Locale.ROOT));
                Attribute attribute = resourceLocation == null ? null : (Attribute)ForgeRegistries.ATTRIBUTES.getValue(resourceLocation);
                AttributeModifier.Operation operation = PrestigeCustomShop.operation(stringArray.length > 2 ? stringArray[2] : "add");
                Double d = PrestigeCustomShop.parseDouble(stringArray[1]);
                if (attribute == null || operation == null || d == null) continue;
                UUID uUID2 = UUID.nameUUIDFromBytes((BONUS_PREFIX + entry.getKey() + "|" + i).getBytes(StandardCharsets.UTF_8));
                wanted.put(uUID2, attribute);
                wantedModifiers.put(uUID2, new AttributeModifier(uUID2, BONUS_PREFIX + entry.getKey(), d * (double)entry.getValue().intValue(), operation));
            }
        }
        for (Map.Entry<UUID, Attribute> entry : new ArrayList<>(applied.entrySet())) {
            if (wanted.containsKey(entry.getKey()) && wanted.get(entry.getKey()) == entry.getValue()) continue;
            AttributeInstance attributeInstance = serverPlayer.m_21051_(entry.getValue());
            if (attributeInstance != null) {
                attributeInstance.m_22120_(entry.getKey());
            }
            applied.remove(entry.getKey());
        }
        for (Map.Entry<UUID, AttributeModifier> entry : wantedModifiers.entrySet()) {
            AttributeInstance attributeInstance = serverPlayer.m_21051_(wanted.get(entry.getKey()));
            if (attributeInstance == null) continue;
            AttributeModifier existing = attributeInstance.m_22111_(entry.getKey());
            AttributeModifier attributeModifier2 = entry.getValue();
            if (existing == null || existing.m_22218_() != attributeModifier2.m_22218_() || existing.m_22217_() != attributeModifier2.m_22217_()) {
                attributeInstance.m_22120_(entry.getKey());
                attributeInstance.m_22118_(attributeModifier2);
            }
            applied.put(entry.getKey(), wanted.get(entry.getKey()));
        }
        if (serverPlayer.m_21223_() > serverPlayer.m_21233_()) {
            serverPlayer.m_21153_(serverPlayer.m_21233_());
        }
    }

    private static AttributeModifier.Operation operation(String string) {
        return switch (string.toLowerCase(Locale.ROOT)) {
            case "add", "addition", "+" -> AttributeModifier.Operation.ADDITION;
            case "multiply_base", "base", "multiply" -> AttributeModifier.Operation.MULTIPLY_BASE;
            case "multiply_total", "total", "*" -> AttributeModifier.Operation.MULTIPLY_TOTAL;
            default -> null;
        };
    }

    private static boolean reconcileStats(StatsData statsData, Map<String, Integer> map) {
        BonusStats bonusStats = statsData.getBonusStats();
        if (bonusStats == null) {
            return false;
        }
        HashMap<String, Map<String, StatWant>> hashMap = new HashMap<String, Map<String, StatWant>>();
        for (Map.Entry<String, Integer> stringArray : map.entrySet()) {
            List<String> list = PrestigeShopConfig.get().entry((String)stringArray.getKey()).dmzStats;
            for (int i = 0; i < list.size(); ++i) {
                String[] stringArray2;
                boolean bl;
                String[] stringArray3 = PrestigeCustomShop.split(list.get(i));
                Double bonusValue = stringArray3.length > 1 ? PrestigeCustomShop.parseDouble(stringArray3[1]) : null;
                if (bonusValue == null) continue;
                boolean bl2 = stringArray3.length > 2 && stringArray3[2].toLowerCase(Locale.ROOT).startsWith("mult");
                boolean bl3 = bl = stringArray3.length > 3 && "raw".equalsIgnoreCase(stringArray3[3]) || stringArray3.length > 2 && "raw".equalsIgnoreCase(stringArray3[2]);
                StatWant statWant = bl2 ? new StatWant("*", Math.pow(bonusValue, stringArray.getValue().intValue()), !bl) : new StatWant(bonusValue < 0.0 ? "-" : "+", Math.abs(bonusValue) * (double)stringArray.getValue().intValue(), !bl);
                String string2 = BONUS_PREFIX + stringArray.getKey() + ":" + i;
                String statName = stringArray3[0].toUpperCase(Locale.ROOT);
                if ("RES".equals(statName)) {
                    String[] stringArray4 = new String[2];
                    stringArray4[0] = "DEF";
                    stringArray2 = stringArray4;
                    stringArray4[1] = "STM";
                } else {
                    String[] stringArray5 = new String[1];
                    stringArray2 = stringArray5;
                    stringArray5[0] = statName;
                }
                for (String string3 : stringArray2) {
                    if (!List.of(DMZ_STATS).contains(string3)) continue;
                    hashMap.computeIfAbsent(string3, string -> new HashMap<>()).put(string2, statWant);
                }
            }
        }
        boolean bl = false;
        for (String string4 : DMZ_STATS) {
            Map<String, StatWant> wants = hashMap.getOrDefault(string4, Map.of());
            HashMap<String, Object> hashMap2 = new HashMap<String, Object>();
            List<BonusStats.StatBonus> list = bonusStats.getBonuses(string4);
            if (list != null) {
                for (BonusStats.StatBonus statBonus : new ArrayList<>(list)) {
                    if (statBonus.name == null || !statBonus.name.startsWith(BONUS_PREFIX)) continue;
                    StatWant want = wants.get(statBonus.name);
                    if (want == null || !want.op.equals(statBonus.operation) || want.value != statBonus.value || want.multipliers != statBonus.applyMultipliers) {
                        bonusStats.removeBonus(string4, statBonus.name);
                        bl = true;
                        continue;
                    }
                    hashMap2.put(statBonus.name, want);
                }
            }
            for (Map.Entry<String, StatWant> entry : wants.entrySet()) {
                if (hashMap2.containsKey(entry.getKey())) continue;
                bonusStats.addBonus(string4, entry.getKey(), entry.getValue().op, entry.getValue().value, entry.getValue().multipliers);
                bl = true;
            }
        }
        return bl;
    }

    private static boolean reconcileDmzEffects(ServerPlayer serverPlayer, StatsData statsData, Map<String, Integer> map) {
        Effects effects = statsData.getEffects();
        if (effects == null) {
            return false;
        }
        LinkedHashMap<String, Double> linkedHashMap = new LinkedHashMap<String, Double>();
        PrestigeCustomShop.forEachLine(map, customEntry -> customEntry.dmzEffects, (string, n) -> {
            Double d;
            String[] stringArray = PrestigeCustomShop.split(string);
            Double d2 = d = stringArray.length > 1 ? PrestigeCustomShop.parseDouble(stringArray[1]) : Double.valueOf(1.0);
            if (d != null) {
                linkedHashMap.merge(stringArray[0].toLowerCase(Locale.ROOT), d, Math::max);
            }
        });
        boolean bl = false;
        for (String object : PrestigeCustomShop.splitList(ProgressionData.storedGet((ServerPlayer)serverPlayer, (String)KEY_DMZ_EFFECTS, (String)""))) {
            boolean bl2;
            boolean bl3 = bl2 = "majin".equals(object) && PrestigePointsSystem.hasMajin(serverPlayer);
            if (linkedHashMap.containsKey(object) || bl2 || !effects.hasEffect(object) || effects.getEffectDuration(object) != -1) continue;
            effects.removeEffect(object);
            bl = true;
        }
        for (Map.Entry entry : linkedHashMap.entrySet()) {
            if (effects.hasEffect((String)entry.getKey()) && effects.getEffectDuration((String)entry.getKey()) == -1 && !(effects.getEffectPower((String)entry.getKey()) < (Double)entry.getValue())) continue;
            effects.addEffect((String)entry.getKey(), ((Double)entry.getValue()).doubleValue(), -1);
            bl = true;
        }
        PrestigeCustomShop.storeList(serverPlayer, KEY_DMZ_EFFECTS, new ArrayList<String>(linkedHashMap.keySet()));
        return bl;
    }

    private static Map<String, Integer> wantedSkills(ServerPlayer serverPlayer) {
        LinkedHashMap<String, Integer> linkedHashMap = new LinkedHashMap<String, Integer>();
        LinkedHashMap<String, Integer> linkedHashMap2 = new LinkedHashMap<String, Integer>();
        for (String string2 : PrestigeShopConfig.get().customEntries.keySet()) {
            int n2 = PrestigeCustomShop.owned(serverPlayer, string2);
            if (n2 <= 0) continue;
            linkedHashMap2.put(string2, n2);
        }
        PrestigeCustomShop.forEachLine(linkedHashMap2, customEntry -> customEntry.dmzSkills, (string, n) -> {
            int n2;
            String[] stringArray = PrestigeCustomShop.split(string);
            int n3 = n2 = stringArray.length > 1 ? PrestigeCustomShop.parseInt(stringArray[1], -1) : -1;
            if (n2 > 0 && !DmzSkillUtil.isFormSkill((String)stringArray[0])) {
                linkedHashMap.merge(stringArray[0].toLowerCase(Locale.ROOT), (int)Math.min(Integer.MAX_VALUE, (long)n2 * (long)n), Math::max);
            }
        });
        return linkedHashMap;
    }

    private static boolean applySkills(ServerPlayer serverPlayer, Map<String, Integer> map) {
        if (map.isEmpty()) {
            return false;
        }
        Skills skills = DmzSkillUtil.skills((ServerPlayer)serverPlayer);
        if (skills == null) {
            return false;
        }
        boolean bl = false;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            int n;
            int n2;
            int n3 = Math.max(entry.getValue(), DmzSkillUtil.configuredMaxLevel((String)entry.getKey()));
            DmzSkillUtil.ensureRegistered((Skills)skills, (String)entry.getKey(), (int)n3);
            int n4 = DmzSkillUtil.level((Skills)skills, (String)entry.getKey());
            if (n4 >= (n2 = Math.min(n = Math.max(n3, DmzSkillUtil.maxLevel((Skills)skills, (String)entry.getKey(), (int)n3)), entry.getValue()))) continue;
            DmzSkillUtil.setLevel((Skills)skills, (String)entry.getKey(), (int)n2);
            bl = true;
        }
        return bl;
    }

    private static void revokeSkills(ServerPlayer serverPlayer, Map<String, Integer> map, Map<String, Integer> map2) {
        Skills skills = DmzSkillUtil.skills((ServerPlayer)serverPlayer);
        if (skills == null) {
            return;
        }
        boolean bl = false;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            int n = map2.getOrDefault(entry.getKey(), 0);
            if (n >= entry.getValue()) continue;
            int n2 = DmzSkillUtil.level((Skills)skills, (String)entry.getKey());
            int n3 = Math.max(n, PrestigePointsSystem.getPurchasedSkillLevels(serverPlayer, entry.getKey()));
            if (n2 > entry.getValue() || n2 <= n3) continue;
            DmzSkillUtil.setLevel((Skills)skills, (String)entry.getKey(), (int)n3);
            bl = true;
        }
        if (bl) {
            DmzSkillUtil.sync((ServerPlayer)serverPlayer);
        }
    }

    private static void runCommands(ServerPlayer serverPlayer, String string, PrestigeShopConfig.CustomEntry customEntry, List<String> list, int n) {
        MinecraftServer minecraftServer = serverPlayer.m_20194_();
        if (minecraftServer == null || list == null || list.isEmpty()) {
            return;
        }
        CommandSourceStack commandSourceStack = minecraftServer.m_129893_().m_81324_();
        for (String string2 : list) {
            String string3 = string2.replace("%player%", serverPlayer.m_6302_()).replace("%uuid%", serverPlayer.m_20148_().toString()).replace("%entry%", string).replace("%name%", customEntry.name).replace("%count%", String.valueOf(n));
            if (string3.startsWith("/")) {
                string3 = string3.substring(1);
            }
            try {
                minecraftServer.m_129892_().m_230957_(commandSourceStack, string3);
            }
            catch (Throwable throwable) {
                AdaptiveDifficultyMod.LOGGER.warn("[{}] prestige shop command failed ({}): {}", (Object)"legacymechanics", (Object)string3, (Object)throwable.toString());
            }
        }
    }

    public static List<String> problems(String string, PrestigeShopConfig.CustomEntry customEntry) {
        Object object;
        ArrayList<String> arrayList = new ArrayList<String>();
        for (String object3 : customEntry.potionEffects) {
            String[] stringArray = PrestigeCustomShop.split(object3);
            if (PrestigeCustomShop.effect(stringArray[0]) == null) {
                arrayList.add("potion: unknown effect " + stringArray[0]);
                continue;
            }
            if (stringArray.length <= 1 || PrestigeCustomShop.parseInt(stringArray[1], -1) >= 0) continue;
            arrayList.add("potion: amplifier must be 0 or more in '" + object3 + "'");
        }
        for (String string2 : customEntry.attributes) {
            String[] stringArray = PrestigeCustomShop.split(string2);
            ResourceLocation attributeId = ResourceLocation.m_135820_((String)stringArray[0].toLowerCase(Locale.ROOT));
            if (attributeId == null || ForgeRegistries.ATTRIBUTES.getValue(attributeId) == null) {
                arrayList.add("attribute: unknown attribute " + stringArray[0]);
                continue;
            }
            if (stringArray.length < 2 || PrestigeCustomShop.parseDouble(stringArray[1]) == null) {
                arrayList.add("attribute: needs an amount in '" + string2 + "'");
                continue;
            }
            if (stringArray.length <= 2 || PrestigeCustomShop.operation(stringArray[2]) != null) continue;
            arrayList.add("attribute: operation must be add, multiply_base or multiply_total in '" + string2 + "'");
        }
        for (String string3 : customEntry.dmzStats) {
            String[] stringArray = PrestigeCustomShop.split(string3);
            object = stringArray[0].toUpperCase(Locale.ROOT);
            if (!"RES".equals(object) && !List.of(DMZ_STATS).contains(object)) {
                arrayList.add("dmzStat: unknown stat " + stringArray[0] + " (STR SKP RES DEF STM VIT PWR ENE)");
                continue;
            }
            if (stringArray.length >= 2 && PrestigeCustomShop.parseDouble(stringArray[1]) != null) continue;
            arrayList.add("dmzStat: needs a value in '" + string3 + "'");
        }
        for (String string4 : customEntry.dmzSkills) {
            String[] stringArray = PrestigeCustomShop.split(string4);
            if (stringArray.length < 2 || PrestigeCustomShop.parseInt(stringArray[1], -1) <= 0) {
                arrayList.add("dmzSkill: needs a level above 0 in '" + string4 + "'");
                continue;
            }
            if (!DmzSkillUtil.isFormSkill((String)stringArray[0])) continue;
            arrayList.add("dmzSkill: " + stringArray[0] + " is a form skill (not allowed)");
        }
        for (String string5 : customEntry.dmzEffects) {
            String[] stringArray = PrestigeCustomShop.split(string5);
            if (stringArray.length <= 1 || PrestigeCustomShop.parseDouble(stringArray[1]) != null) continue;
            arrayList.add("dmzEffect: power must be a number in '" + string5 + "'");
        }
        PrestigeShopConfig prestigeShopConfig = PrestigeShopConfig.get();
        for (String string6 : customEntry.requiredEntries) {
            if (prestigeShopConfig.entry(string6) != null) continue;
            arrayList.add("requiredEntries: no entry called " + string6);
        }
        for (String string7 : customEntry.blockedByEntries) {
            if (prestigeShopConfig.entry(string7) != null) continue;
            arrayList.add("blockedByEntries: no entry called " + string7);
        }
        return arrayList;
    }

    public static List<String> rewardLines(PrestigeShopConfig.CustomEntry customEntry) {
        String[] stringArray;
        ArrayList<String> arrayList = new ArrayList<String>();
        for (String string : customEntry.potionEffects) {
            stringArray = PrestigeCustomShop.split(string);
            arrayList.add("Potion: " + PrestigeCustomShop.prettyId(stringArray[0]) + " " + PrestigeCustomShop.roman((stringArray.length > 1 ? PrestigeCustomShop.parseInt(stringArray[1], 0) : 0) + 1));
        }
        for (String string : customEntry.attributes) {
            stringArray = PrestigeCustomShop.split(string);
            arrayList.add("Attribute: " + PrestigeCustomShop.prettyId(stringArray[0]) + " " + (String)(stringArray.length > 2 && PrestigeCustomShop.operation(stringArray[2]) != AttributeModifier.Operation.ADDITION ? "+" + PrestigeCustomShop.percent(stringArray[1]) : PrestigeCustomShop.signed(stringArray.length > 1 ? stringArray[1] : "?")));
        }
        for (String string : customEntry.dmzStats) {
            stringArray = PrestigeCustomShop.split(string);
            boolean bl = stringArray.length > 2 && stringArray[2].toLowerCase(Locale.ROOT).startsWith("mult");
            arrayList.add("Stat: " + stringArray[0].toUpperCase(Locale.ROOT) + " " + (String)(bl ? "x" + (stringArray.length > 1 ? stringArray[1] : "?") : PrestigeCustomShop.signed(stringArray.length > 1 ? stringArray[1] : "?")));
        }
        for (String string : customEntry.dmzSkills) {
            stringArray = PrestigeCustomShop.split(string);
            arrayList.add("Skill: " + DmzSkillUtil.prettySkillLabel((String)stringArray[0].toLowerCase(Locale.ROOT)) + " level " + (stringArray.length > 1 ? stringArray[1] : "?"));
        }
        for (String string : customEntry.dmzEffects) {
            arrayList.add("Effect: " + PrestigeCustomShop.prettyId(PrestigeCustomShop.split(string)[0]));
        }
        return arrayList;
    }

    private static void forEachLine(Map<String, Integer> map, Lines lines, LineAction lineAction) {
        PrestigeShopConfig prestigeShopConfig = PrestigeShopConfig.get();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            PrestigeShopConfig.CustomEntry customEntry = prestigeShopConfig.entry(entry.getKey());
            if (customEntry == null) continue;
            for (String string : lines.of(customEntry)) {
                if (string == null || string.isBlank()) continue;
                lineAction.accept(string, entry.getValue());
            }
        }
    }

    private static void storeList(ServerPlayer serverPlayer, String string, List<String> list) {
        String string2 = String.join((CharSequence)";", list);
        if (!string2.equals(ProgressionData.storedGet((ServerPlayer)serverPlayer, (String)string, (String)""))) {
            if (string2.isEmpty()) {
                ProgressionData.storedRemove((ServerPlayer)serverPlayer, (String)string);
            } else {
                ProgressionData.storedPut((ServerPlayer)serverPlayer, (String)string, (String)string2);
            }
        }
    }

    private static List<String> splitList(String string) {
        ArrayList<String> arrayList = new ArrayList<String>();
        if (string != null) {
            for (String string2 : string.split(";")) {
                if (string2.isBlank()) continue;
                arrayList.add(string2.trim());
            }
        }
        return arrayList;
    }

    private static String[] split(String string) {
        return string.trim().split("\\s+");
    }

    private static int dmzLevel(ServerPlayer serverPlayer) {
        try {
            StatsData statsData = DmzProgression.stats((Player)serverPlayer);
            return statsData == null ? 0 : Math.max(0, statsData.getLevel());
        }
        catch (Throwable throwable) {
            return 0;
        }
    }

    private static int parseInt(String string, int n) {
        try {
            return Integer.parseInt(string.trim());
        }
        catch (RuntimeException runtimeException) {
            return n;
        }
    }

    private static Double parseDouble(String string) {
        try {
            double d = Double.parseDouble(string.trim());
            return Double.isFinite(d) ? Double.valueOf(d) : null;
        }
        catch (RuntimeException runtimeException) {
            return null;
        }
    }

    static String levelSuffix(PrestigeShopConfig.CustomEntry customEntry, int n) {
        return customEntry.maxPurchases == 1 ? "" : " " + PrestigeCustomShop.roman(n);
    }

    static String titleCase(String string) {
        StringBuilder stringBuilder = new StringBuilder();
        for (String string2 : string.replace(':', ' ').replace('_', ' ').replace('.', ' ').split(" ")) {
            if (string2.isEmpty()) continue;
            if (stringBuilder.length() > 0) {
                stringBuilder.append(' ');
            }
            stringBuilder.append(Character.toUpperCase(string2.charAt(0))).append(string2.substring(1));
        }
        return stringBuilder.toString();
    }

    private static String prettyId(String string) {
        String string2 = string.toLowerCase(Locale.ROOT);
        int n = string2.indexOf(58);
        String string3 = string2 = n >= 0 ? string2.substring(n + 1) : string2;
        if (string2.startsWith("generic.")) {
            string2 = string2.substring(8);
        }
        return PrestigeCustomShop.titleCase(string2);
    }

    private static String signed(String string) {
        return string.startsWith("-") ? string : "+" + string;
    }

    private static String percent(String string) {
        Double d = PrestigeCustomShop.parseDouble(string);
        return d == null ? string : Math.round(d * 100.0) + "%";
    }

    static String roman(int n) {
        if (n <= 0 || n > 20) {
            return String.valueOf(n);
        }
        String[] stringArray = new String[]{"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};
        return stringArray[n - 1];
    }

    static interface Lines {
        public List<String> of(PrestigeShopConfig.CustomEntry var1);
    }

    static interface LineAction {
        public void accept(String var1, int var2);
    }

    private record PotionWant(MobEffect effect, String key, int amplifier, boolean visible) {
    }

    private record StatWant(String op, double value, boolean multipliers) {
    }
}
