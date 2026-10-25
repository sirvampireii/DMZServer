package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.ConfigPaths;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeCustomShop;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PrestigeShopConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile PrestigeShopConfig INSTANCE;
    public Exchange exchange = new Exchange();
    public SkillShop skillShop = new SkillShop();
    public TierShop tierShop = new TierShop();
    public Forms forms = new Forms();
    public GodKi godKi = new GodKi();
    public CustomShop customShop = new CustomShop();
    public Map<String, CustomEntry> customEntries = PrestigeShopConfig.defaultEntries();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static PrestigeShopConfig get() {
        PrestigeShopConfig prestigeShopConfig = INSTANCE;
        if (prestigeShopConfig != null) return prestigeShopConfig;
        Class<PrestigeShopConfig> clazz = PrestigeShopConfig.class;
        synchronized (PrestigeShopConfig.class) {
            if (INSTANCE != null) return INSTANCE;
            PrestigeShopConfig.load();
            return INSTANCE;
        }
    }

    public static Path path() {
        return ConfigPaths.dataDir().resolve("prestige-shop.json");
    }

    public static void load() {
        block10: {
            Path path = PrestigeShopConfig.path();
            try {
                PrestigeShopConfig prestigeShopConfig;
                Files.createDirectories(path.getParent(), new FileAttribute[0]);
                if (!Files.isRegularFile(path, new LinkOption[0])) {
                    INSTANCE = PrestigeShopConfig.sanitize(new PrestigeShopConfig());
                    PrestigeShopConfig.save();
                    return;
                }
                try (BufferedReader bufferedReader = Files.newBufferedReader(path);){
                    prestigeShopConfig = (PrestigeShopConfig)GSON.fromJson((Reader)bufferedReader, PrestigeShopConfig.class);
                }
                INSTANCE = PrestigeShopConfig.sanitize(prestigeShopConfig == null ? new PrestigeShopConfig() : prestigeShopConfig);
            }
            catch (Throwable throwable) {
                AdaptiveDifficultyMod.LOGGER.warn("[{}] prestige-shop config load failed: {}", (Object)"legacymechanics", (Object)throwable.toString());
                if (INSTANCE != null) break block10;
                INSTANCE = PrestigeShopConfig.sanitize(new PrestigeShopConfig());
            }
        }
        PrestigeCustomShop.onConfigChanged();
    }

    public static boolean save() {
        INSTANCE = PrestigeShopConfig.sanitize(INSTANCE == null ? new PrestigeShopConfig() : INSTANCE);
        Path path = PrestigeShopConfig.path();
        try {
            Files.createDirectories(path.getParent(), new FileAttribute[0]);
            Path path2 = path.resolveSibling(String.valueOf(path.getFileName()) + ".tmp");
            try (BufferedWriter bufferedWriter = Files.newBufferedWriter(path2, new OpenOption[0]);){
                GSON.toJson((Object)INSTANCE, (Appendable)bufferedWriter);
            }
            Files.move(path2, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            PrestigeCustomShop.onConfigChanged();
            return true;
        }
        catch (Throwable throwable) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] prestige-shop config save failed: {}", (Object)"legacymechanics", (Object)throwable.toString());
            return false;
        }
    }

    public static PrestigeShopConfig freshDefaults() {
        return PrestigeShopConfig.sanitize(new PrestigeShopConfig());
    }

    public CustomEntry entry(String string) {
        if (string != null && this.customEntries != null) {
            return this.customEntries.get(string.trim().toLowerCase(Locale.ROOT));
        }
        return null;
    }

    private static PrestigeShopConfig sanitize(PrestigeShopConfig prestigeShopConfig) {
        if (prestigeShopConfig.exchange == null) {
            prestigeShopConfig.exchange = new Exchange();
        }
        if (prestigeShopConfig.skillShop == null) {
            prestigeShopConfig.skillShop = new SkillShop();
        }
        if (prestigeShopConfig.tierShop == null) {
            prestigeShopConfig.tierShop = new TierShop();
        }
        if (prestigeShopConfig.forms == null) {
            prestigeShopConfig.forms = new Forms();
        }
        if (prestigeShopConfig.godKi == null) {
            prestigeShopConfig.godKi = new GodKi();
        }
        if (prestigeShopConfig.customShop == null) {
            prestigeShopConfig.customShop = new CustomShop();
        }
        Exchange exchange = prestigeShopConfig.exchange;
        exchange.amounts = PrestigeShopConfig.positiveSortedUnique(exchange.amounts, List.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3), Integer.valueOf(6), Integer.valueOf(9)));
        exchange.bonusGroupSize = Math.max(0, exchange.bonusGroupSize);
        exchange.payoutOverrides = PrestigeShopConfig.cleanIntMap(exchange.payoutOverrides, 0);
        SkillShop skillShop = prestigeShopConfig.skillShop;
        skillShop.costPerPurchase = Math.max(0, skillShop.costPerPurchase);
        skillShop.levelsPerPurchase = Math.max(1, skillShop.levelsPerPurchase);
        skillShop.levelsPerPurchaseOverrides = PrestigeShopConfig.cleanIntMap(skillShop.levelsPerPurchaseOverrides, 1);
        skillShop.costOverrides = PrestigeShopConfig.cleanIntMap(skillShop.costOverrides, 0);
        skillShop.maxLevelOverrides = PrestigeShopConfig.cleanIntMap(skillShop.maxLevelOverrides, 0);
        skillShop.hiddenSkills = PrestigeShopConfig.cleanIds(skillShop.hiddenSkills);
        skillShop.extraSkills = PrestigeShopConfig.cleanIds(skillShop.extraSkills);
        TierShop tierShop = prestigeShopConfig.tierShop;
        Map<String, Integer> map = PrestigeShopConfig.cleanIntMap(tierShop.costs, 0);
        for (int i = 1; i <= 7; ++i) {
            map.putIfAbsent(String.valueOf(i), (i + 1) / 2);
        }
        tierShop.costs = map;
        tierShop.notForSale = PrestigeShopConfig.positiveSortedUnique(tierShop.notForSale, List.of());
        prestigeShopConfig.forms.majin = PrestigeShopConfig.cleanForm(prestigeShopConfig.forms.majin, 5, 0);
        prestigeShopConfig.forms.mutant = PrestigeShopConfig.cleanForm(prestigeShopConfig.forms.mutant, 5, 0);
        prestigeShopConfig.forms.ultimate = PrestigeShopConfig.cleanForm(prestigeShopConfig.forms.ultimate, 3, 100);
        GodKi godKi = prestigeShopConfig.godKi;
        godKi.costPerPoint = Math.max(0, godKi.costPerPoint);
        godKi.masteryPerPoint = Math.max(1, godKi.masteryPerPoint);
        godKi.maxPoints = Math.max(1, Math.min(100, godKi.maxPoints));
        CustomShop customShop = prestigeShopConfig.customShop;
        if (customShop.menuLabel == null || customShop.menuLabel.isBlank()) {
            customShop.menuLabel = "Special perks";
        }
        customShop.reapplySeconds = Math.max(1, Math.min(300, customShop.reapplySeconds));
        LinkedHashMap<String, CustomEntry> linkedHashMap = new LinkedHashMap<String, CustomEntry>();
        if (prestigeShopConfig.customEntries != null) {
            for (Map.Entry<String, CustomEntry> entry : prestigeShopConfig.customEntries.entrySet()) {
                String string = PrestigeShopConfig.cleanId(entry.getKey());
                if (string.isEmpty()) continue;
                linkedHashMap.put(string, PrestigeShopConfig.cleanEntry(entry.getValue(), string));
            }
        }
        prestigeShopConfig.customEntries = linkedHashMap;
        return prestigeShopConfig;
    }

    private static FormOffer cleanForm(FormOffer formOffer, int n, int n2) {
        FormOffer formOffer2 = formOffer == null ? new FormOffer(n, n2) : formOffer;
        formOffer2.cost = Math.max(0, formOffer2.cost);
        formOffer2.refundPercent = Math.max(0, Math.min(100, formOffer2.refundPercent));
        return formOffer2;
    }

    static CustomEntry cleanEntry(CustomEntry customEntry, String string) {
        CustomEntry customEntry2;
        CustomEntry customEntry3 = customEntry2 = customEntry == null ? new CustomEntry() : customEntry;
        if (customEntry2.name == null || customEntry2.name.isBlank()) {
            customEntry2.name = PrestigeCustomShop.titleCase(string);
        }
        if (customEntry2.description == null) {
            customEntry2.description = "";
        }
        customEntry2.cost = Math.max(0, customEntry2.cost);
        customEntry2.costIncreasePerPurchase = Math.max(0, customEntry2.costIncreasePerPurchase);
        customEntry2.maxPurchases = Math.max(0, customEntry2.maxPurchases);
        customEntry2.refundPercent = Math.max(0, Math.min(100, customEntry2.refundPercent));
        customEntry2.requiredLifetimePrestiges = Math.max(0, customEntry2.requiredLifetimePrestiges);
        customEntry2.requiredLevel = Math.max(0, customEntry2.requiredLevel);
        customEntry2.requiredEntries = PrestigeShopConfig.cleanIds(customEntry2.requiredEntries);
        customEntry2.blockedByEntries = PrestigeShopConfig.cleanIds(customEntry2.blockedByEntries);
        customEntry2.potionEffects = PrestigeShopConfig.cleanLines(customEntry2.potionEffects);
        customEntry2.attributes = PrestigeShopConfig.cleanLines(customEntry2.attributes);
        customEntry2.dmzStats = PrestigeShopConfig.cleanLines(customEntry2.dmzStats);
        customEntry2.dmzSkills = PrestigeShopConfig.cleanLines(customEntry2.dmzSkills);
        customEntry2.dmzEffects = PrestigeShopConfig.cleanLines(customEntry2.dmzEffects);
        customEntry2.commandsOnPurchase = PrestigeShopConfig.cleanLines(customEntry2.commandsOnPurchase);
        customEntry2.commandsOnRemove = PrestigeShopConfig.cleanLines(customEntry2.commandsOnRemove);
        customEntry2.commandsOnReapply = PrestigeShopConfig.cleanLines(customEntry2.commandsOnReapply);
        return customEntry2;
    }

    static String cleanId(String string) {
        return string == null ? "" : string.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static List<String> cleanIds(List<String> list) {
        ArrayList<String> arrayList = new ArrayList<String>();
        if (list != null) {
            for (String string : list) {
                String string2 = PrestigeShopConfig.cleanId(string);
                if (string2.isEmpty() || arrayList.contains(string2)) continue;
                arrayList.add(string2);
            }
        }
        return arrayList;
    }

    private static List<String> cleanLines(List<String> list) {
        ArrayList<String> arrayList = new ArrayList<String>();
        if (list != null) {
            for (String string : list) {
                if (string == null || string.isBlank()) continue;
                arrayList.add(string.trim());
            }
        }
        return arrayList;
    }

    private static Map<String, Integer> cleanIntMap(Map<String, Integer> map, int n) {
        LinkedHashMap<String, Integer> linkedHashMap = new LinkedHashMap<String, Integer>();
        if (map != null) {
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                String string = PrestigeShopConfig.cleanId(entry.getKey());
                if (string.isEmpty() || entry.getValue() == null) continue;
                linkedHashMap.put(string, Math.max(n, ((Number)entry.getValue()).intValue()));
            }
        }
        return linkedHashMap;
    }

    private static List<Integer> positiveSortedUnique(List<Integer> list, List<Integer> list2) {
        ArrayList<Integer> arrayList = new ArrayList<Integer>();
        if (list != null) {
            for (Integer n : list) {
                if (n == null || n <= 0 || arrayList.contains(n)) continue;
                arrayList.add(n);
            }
        }
        if (arrayList.isEmpty() && !list2.isEmpty()) {
            arrayList.addAll(list2);
        }
        arrayList.sort(null);
        return arrayList;
    }

    static Map<String, CustomEntry> defaultEntries() {
        LinkedHashMap<String, CustomEntry> linkedHashMap = new LinkedHashMap<String, CustomEntry>();
        CustomEntry customEntry = new CustomEntry();
        customEntry.enabled = false;
        customEntry.name = "Vitality Training";
        customEntry.description = "Example: +4 max health and permanent Regeneration I. Turn Enabled on to sell it.";
        customEntry.cost = 3;
        customEntry.attributes.add("minecraft:generic.max_health 4 add");
        customEntry.potionEffects.add("minecraft:regeneration 0");
        linkedHashMap.put("example_vitality", customEntry);
        CustomEntry customEntry2 = new CustomEntry();
        customEntry2.enabled = false;
        customEntry2.name = "Saiyan Pride";
        customEntry2.description = "Example: +10% Strength per level, up to 3 levels. Turn Enabled on to sell it.";
        customEntry2.cost = 2;
        customEntry2.costIncreasePerPurchase = 1;
        customEntry2.maxPurchases = 3;
        customEntry2.removable = true;
        customEntry2.refundPercent = 50;
        customEntry2.dmzStats.add("STR 1.10 mult");
        customEntry2.commandsOnPurchase.add("tellraw @a {\"text\":\"%player% took Saiyan Pride level %count%!\",\"color\":\"gold\"}");
        linkedHashMap.put("example_pride", customEntry2);
        return linkedHashMap;
    }

    public static final class Exchange {
        public boolean enabled = true;
        public List<Integer> amounts = new ArrayList<Integer>(List.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3), Integer.valueOf(6), Integer.valueOf(9)));
        public int bonusGroupSize = 3;
        public Map<String, Integer> payoutOverrides = new LinkedHashMap<String, Integer>();

        public int payout(int n) {
            Integer n2;
            Integer n3 = n2 = this.payoutOverrides == null ? null : this.payoutOverrides.get(String.valueOf(n));
            if (n2 != null) {
                return Math.max(0, n2);
            }
            int n4 = Math.max(0, n);
            int n5 = this.bonusGroupSize > 0 ? n4 / this.bonusGroupSize : 0;
            return n4 + n5 * (n5 + 1) / 2;
        }
    }

    public static final class SkillShop {
        public boolean enabled = true;
        public int costPerPurchase = 1;
        public int levelsPerPurchase = 1;
        public Map<String, Integer> levelsPerPurchaseOverrides = new LinkedHashMap<String, Integer>(Map.of("potentialunlock", 2));
        public Map<String, Integer> costOverrides = new LinkedHashMap<String, Integer>();
        public Map<String, Integer> maxLevelOverrides = new LinkedHashMap<String, Integer>();
        public List<String> hiddenSkills = new ArrayList<String>();
        public List<String> extraSkills = new ArrayList<String>();

        public int costFor(String string) {
            Integer n = this.costOverrides == null ? null : this.costOverrides.get(PrestigeShopConfig.cleanId(string));
            return n != null ? n : this.costPerPurchase;
        }

        public int levelsFor(String string) {
            Integer n = this.levelsPerPurchaseOverrides == null ? null : this.levelsPerPurchaseOverrides.get(PrestigeShopConfig.cleanId(string));
            return Math.max(1, n != null ? n : this.levelsPerPurchase);
        }
    }

    public static final class TierShop {
        public boolean enabled = true;
        public boolean requireInOrder = true;
        public Map<String, Integer> costs = new LinkedHashMap<String, Integer>();
        public List<Integer> notForSale = new ArrayList<Integer>();

        public int costFor(int n) {
            Integer n2 = this.costs == null ? null : this.costs.get(String.valueOf(n));
            return n2 != null ? Math.max(0, n2) : (n + 1) / 2;
        }

        public boolean forSale(int n) {
            return this.enabled && n >= 1 && n <= 7 && (this.notForSale == null || !this.notForSale.contains(n));
        }
    }

    public static final class Forms {
        public FormOffer majin = new FormOffer(5, 0);
        public FormOffer mutant = new FormOffer(5, 0);
        public FormOffer ultimate = new FormOffer(3, 100);
        public boolean majinMutantExclusive = true;
    }

    public static final class GodKi {
        public boolean enabled = true;
        public int costPerPoint = 1;
        public int masteryPerPoint = 1000;
        public int maxPoints = 5;
    }

    public static final class CustomShop {
        public boolean enabled = true;
        public String menuLabel = "Special perks";
        public int reapplySeconds = 5;
        public boolean hidePotionParticles = true;
    }

    public static final class CustomEntry {
        public boolean enabled = true;
        public String name = "";
        public String description = "";
        public int cost = 1;
        public int costIncreasePerPurchase = 0;
        public int maxPurchases = 1;
        public boolean removable = false;
        public int refundPercent = 0;
        public int requiredLifetimePrestiges = 0;
        public int requiredLevel = 0;
        public List<String> requiredEntries = new ArrayList<String>();
        public List<String> blockedByEntries = new ArrayList<String>();
        public List<String> potionEffects = new ArrayList<String>();
        public List<String> attributes = new ArrayList<String>();
        public List<String> dmzStats = new ArrayList<String>();
        public List<String> dmzSkills = new ArrayList<String>();
        public List<String> dmzEffects = new ArrayList<String>();
        public List<String> commandsOnPurchase = new ArrayList<String>();
        public List<String> commandsOnRemove = new ArrayList<String>();
        public List<String> commandsOnReapply = new ArrayList<String>();
        public boolean revokeSkillsOnRemove = true;

        public int costFor(int n) {
            long l = (long)this.cost + (long)Math.max(0, n) * (long)this.costIncreasePerPurchase;
            return (int)Math.min(Integer.MAX_VALUE, l);
        }
    }

    public static final class FormOffer {
        public boolean enabled = true;
        public int cost;
        public int refundPercent;

        public FormOffer() {
        }

        FormOffer(int n, int n2) {
            this.cost = n;
            this.refundPercent = n2;
        }

        public int refund() {
            return (int)Math.floor((double)this.cost * (double)this.refundPercent / 100.0);
        }
    }
}
