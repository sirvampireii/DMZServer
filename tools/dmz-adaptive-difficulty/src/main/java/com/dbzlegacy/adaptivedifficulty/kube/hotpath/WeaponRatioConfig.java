package com.dbzlegacy.adaptivedifficulty.kube.hotpath;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.ConfigPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class WeaponRatioConfig {
    public static final String FILE_NAME = "weapon-ratios.json";
    static final String[] STATS = WeaponBonusTables.STATS;
    static final String[] SLOTS = new String[]{"MAIN", "OFF", "SLOT9"};
    private static final long RELOAD_CHECK_MS = 5000L;
    private static volatile Snapshot SNAPSHOT;
    private static volatile Path pathOverride;
    private static volatile long lastModified;
    private static volatile long nextCheck;
    private static volatile boolean discoveryDone;
    static final List<String> HELP;

    private WeaponRatioConfig() {
    }

    public static Snapshot get() {
        Snapshot snapshot = SNAPSHOT;
        if (snapshot != null) return snapshot;
        synchronized (WeaponRatioConfig.class) {
            if (SNAPSHOT != null) return SNAPSHOT;
            WeaponRatioConfig.load();
            return SNAPSHOT;
        }
    }

    public static Path path() {
        Path path = pathOverride;
        if (path != null) {
            return path;
        }
        try {
            return ConfigPaths.dataDir().resolve(FILE_NAME);
        }
        catch (Throwable throwable) {
            return Paths.get("config", "legacymechanics", FILE_NAME);
        }
    }

    static void usePathForTest(Path path) {
        pathOverride = path;
        SNAPSHOT = null;
        discoveryDone = false;
    }

    public static void maybeReload() {
        long l = System.currentTimeMillis();
        if (l < nextCheck) {
            return;
        }
        nextCheck = l + 5000L;
        try {
            Path path = WeaponRatioConfig.path();
            long l2 = Files.isRegularFile(path, new LinkOption[0]) ? Files.getLastModifiedTime(path, new LinkOption[0]).toMillis() : Long.MIN_VALUE;
            if (l2 == lastModified) return;
            synchronized (WeaponRatioConfig.class) {
                WeaponRatioConfig.load();
                return;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public static boolean discoveryPending() {
        return !discoveryDone;
    }

    static void markDiscoveryDone() {
        discoveryDone = true;
    }

    static synchronized void load() {
        Path path = WeaponRatioConfig.path();
        boolean bl = false;
        try {
            Map<String, Object> map;
            Object object;
            if (Files.isRegularFile(path, new LinkOption[0])) {
                object = Json.parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
                map = object instanceof Map ? WeaponRatioConfig.castMap(object) : null;
                if (map == null) {
                    throw new IllegalArgumentException("top level is not a JSON object");
                }
            } else {
                map = WeaponRatioConfig.defaultsTree();
                bl = true;
            }
            SNAPSHOT = (Snapshot)(object = WeaponRatioConfig.build(map));
            if (bl |= WeaponRatioConfig.mergeDefaults(map)) {
                WeaponRatioConfig.write(path, map);
            }
            lastModified = Files.isRegularFile(path, new LinkOption[0]) ? Files.getLastModifiedTime(path, new LinkOption[0]).toMillis() : Long.MIN_VALUE;
            WeaponRatioConfig.info("[DMZ Weapon Ratios] loaded " + String.valueOf(path.getFileName()) + ": " + ((Snapshot)object).weapons.size() + " weapons, " + ((Snapshot)object).families.size() + " families, " + ((Snapshot)object).discovered.size() + " discovered");
        }
        catch (Throwable throwable) {
            WeaponRatioConfig.warn("[DMZ Weapon Ratios] failed to load " + String.valueOf(path) + " (" + String.valueOf(throwable) + "); keeping previous values");
            if (SNAPSHOT == null) {
                SNAPSHOT = WeaponRatioConfig.build(WeaponRatioConfig.defaultsTree());
            }
            try {
                lastModified = Files.getLastModifiedTime(path, new LinkOption[0]).toMillis();
            }
            catch (Throwable throwable2) {
                // empty catch block
            }
        }
        discoveryDone = false;
    }

    static synchronized void addDiscovered(List<String> list) {
        if (list.isEmpty()) {
            return;
        }
        Path path = WeaponRatioConfig.path();
        try {
            Map<String, Object> map = Files.isRegularFile(path, new LinkOption[0]) ? WeaponRatioConfig.castMap(Json.parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))) : WeaponRatioConfig.defaultsTree();
            Map<String, Object> map2 = WeaponRatioConfig.section(map, "discovered");
            int n = 0;
            for (String string : list) {
                if (map2.containsKey(string)) continue;
                map2.put(string, new LinkedHashMap());
                ++n;
            }
            if (n > 0) {
                WeaponRatioConfig.write(path, map);
                SNAPSHOT = WeaponRatioConfig.build(map);
                lastModified = Files.getLastModifiedTime(path, new LinkOption[0]).toMillis();
                WeaponRatioConfig.info("[DMZ Weapon Ratios] discovered " + n + " new weapon item(s); listed under \"discovered\" in " + String.valueOf(path.getFileName()));
            }
        }
        catch (Throwable throwable) {
            WeaponRatioConfig.warn("[DMZ Weapon Ratios] could not save discovered items: " + String.valueOf(throwable));
        }
    }

    private static void write(Path path, Map<String, Object> map) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent(), new FileAttribute[0]);
        Path path2 = path.resolveSibling(String.valueOf(path.getFileName()) + ".tmp");
        Files.write(path2, Json.write(map).getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
        try {
            Files.move(path2, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (Throwable throwable) {
            Files.move(path2, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static Map<String, Object> defaultsTree() {
        LinkedHashMap<String, Object> linkedHashMap2 = new LinkedHashMap<String, Object>();
        linkedHashMap2.put("_help", new ArrayList<String>(HELP));
        linkedHashMap2.put("damageDivisor", 100.0);
        linkedHashMap2.put("uniqueMultiplier", 1.0);
        linkedHashMap2.put("rangedWeaponDamage", 9.0);
        linkedHashMap2.put("autoDiscover", Boolean.TRUE);
        linkedHashMap2.put("defaultRatiosSlots", new ArrayList<String>(Collections.singletonList("MAIN")));
        linkedHashMap2.put("defaultRatios", WeaponRatioConfig.ratioTree(WeaponRatioConfig.arr(1.0, 0.0, 0.0, 0.0, 0.0, 0.0)));
        LinkedHashMap<String, Object> linkedHashMap3 = new LinkedHashMap<String, Object>();
        linkedHashMap3.put("ratios", WeaponRatioConfig.ratioTree(WeaponRatioConfig.arr(0.0, 0.0, 0.1, 0.03, 0.0, 0.0)));
        linkedHashMap3.put("scaleWithDurability", Boolean.TRUE);
        linkedHashMap2.put("shield", linkedHashMap3);
        LinkedHashMap<String, Map<String, Object>> linkedHashMap4 = new LinkedHashMap<String, Map<String, Object>>();
        for (Map.Entry<String, double[]> linkedHashMap5 : WeaponBonusTables.familyMap().entrySet()) {
            linkedHashMap4.put(linkedHashMap5.getKey(), WeaponRatioConfig.ratioTree(WeaponRatioConfig.normalize(linkedHashMap5.getValue())));
        }
        linkedHashMap2.put("families", linkedHashMap4);
        LinkedHashMap linkedHashMap6 = new LinkedHashMap();
        for (Map.Entry<String, double[]> entry : WeaponBonusTables.tinkersMap().entrySet()) {
            linkedHashMap6.put(entry.getKey(), WeaponRatioConfig.ratioTree(WeaponRatioConfig.normalize(entry.getValue())));
        }
        linkedHashMap2.put("tinkersFamilies", linkedHashMap6);
        LinkedHashMap<String, Map<String, Object>> linkedHashMap = new LinkedHashMap<String, Map<String, Object>>();
        ArrayList<Object> arrayList = new ArrayList<Object>();
        for (Map.Entry<String, double[]> entry : WeaponRatioConfig.defaultWeaponRatios(arrayList).entrySet()) {
            linkedHashMap.put(entry.getKey(), WeaponRatioConfig.ratioTree(entry.getValue()));
        }
        linkedHashMap2.put("weapons", linkedHashMap);
        linkedHashMap2.put("uniqueWeapons", arrayList);
        linkedHashMap2.put("discovered", new LinkedHashMap());
        return linkedHashMap2;
    }

    static Map<String, double[]> defaultWeaponRatios(List<Object> list) {
        LinkedHashMap<String, double[]> linkedHashMap = new LinkedHashMap<String, double[]>();
        for (Map.Entry<String, WeaponBonusTables.WeaponDef> object2 : WeaponBonusTables.dmzMap().entrySet()) {
            linkedHashMap.put(object2.getKey(), WeaponRatioConfig.normalize(object2.getValue().profile));
        }
        for (Map.Entry<String, WeaponBonusTables.WeaponDef> entry : WeaponBonusTables.ironsMap().entrySet()) {
            linkedHashMap.put(entry.getKey(), WeaponRatioConfig.normalize(entry.getValue().profile));
        }
        LinkedHashSet<String> linkedHashSet = new LinkedHashSet<String>(WeaponBonusTables.simplyExactMap().keySet());
        linkedHashSet.addAll(WeaponBonusTables.uniqueThirdMap().keySet());
        for (String string : WeaponBonusTables.MIMICRY_FORMS) {
            linkedHashSet.add("simplymore:mimicry_" + string);
        }
        Iterator iterator = linkedHashSet.iterator();
        while (iterator.hasNext()) {
            int n;
            String string = (String)iterator.next();
            String string2 = WeaponRatioConfig.simplyFamily(string);
            double[] dArray = WeaponBonusTables.family(string2);
            if (dArray == null) continue;
            boolean bl = WeaponBonusTables.uniqueThird(string) != null || string.startsWith("simplymore:mimicry_");
            double d = 0.0;
            for (double d2 : dArray) {
                if (!Double.isFinite(d2) || !(d2 > 0.0)) continue;
                d += d2;
            }
            double d3 = d > 0.0 ? 1.0 / d : 1.0;
            double[] dArray2 = new double[6];
            for (n = 0; n < 6; ++n) {
                dArray2[n] = Double.isNaN(dArray[n]) ? 0.0 : dArray[n] * (bl ? 1.8 : 1.0) * d3;
            }
            if (bl) {
                n = WeaponRatioConfig.statIndex(WeaponRatioConfig.chooseThird(string, dArray));
                if (n >= 0) {
                    int n2 = n;
                    dArray2[n2] = dArray2[n2] + 0.162 * d3;
                }
                list.add(string);
            }
            linkedHashMap.put(string, WeaponRatioConfig.round3(dArray2));
        }
        return linkedHashMap;
    }

    static String simplyFamily(String string) {
        String string2 = WeaponBonusTables.simplyExact(string);
        if (string2 != null) {
            return string2;
        }
        int n = string.indexOf(58);
        String string3 = n < 0 ? string : string.substring(n + 1);
        for (String[] stringArray : WeaponBonusTables.SIMPLY_SUFFIXES) {
            if (!string3.endsWith(stringArray[0])) continue;
            return stringArray[1];
        }
        return null;
    }

    static String chooseThird(String string, double[] dArray) {
        int n;
        String string2 = WeaponBonusTables.uniqueThird(string);
        if (string2 == null && string.startsWith("simplymore:mimicry_")) {
            string2 = "VIT";
        }
        if (string2 != null && (n = WeaponRatioConfig.statIndex(string2)) >= 0 && Double.isNaN(dArray[n])) {
            return string2;
        }
        for (String string3 : WeaponBonusTables.THIRD_ORDER) {
            int n2 = WeaponRatioConfig.statIndex(string3);
            if (n2 < 0 || !Double.isNaN(dArray[n2])) continue;
            return string3;
        }
        return "VIT";
    }

    static boolean mergeDefaults(Map<String, Object> map) {
        Map<String, Object> map2 = WeaponRatioConfig.defaultsTree();
        boolean bl = false;
        LinkedHashSet<String> linkedHashSet = new LinkedHashSet<String>();
        for (Map.Entry<String, Object> entry : map2.entrySet()) {
            String string = entry.getKey();
            if ("_help".equals(string)) {
                if (HELP.equals(map.get(string))) continue;
                map.put(string, entry.getValue());
                bl = true;
                continue;
            }
            if (!map.containsKey(string)) {
                map.put(string, entry.getValue());
                bl = true;
                if (!"weapons".equals(string)) continue;
                linkedHashSet.addAll(WeaponRatioConfig.castMap(entry.getValue()).keySet());
                continue;
            }
            if ("families".equals(string) || "tinkersFamilies".equals(string) || "weapons".equals(string)) {
                Map<String, Object> object = WeaponRatioConfig.section(map, string);
                for (Map.Entry<String, Object> entry2 : WeaponRatioConfig.castMap(entry.getValue()).entrySet()) {
                    if (object.containsKey(entry2.getKey())) continue;
                    object.put(entry2.getKey(), entry2.getValue());
                    bl = true;
                    if (!"weapons".equals(string)) continue;
                    linkedHashSet.add(entry2.getKey());
                }
                continue;
            }
            if (!"uniqueWeapons".equals(string) || !(map.get(string) instanceof List)) continue;
            List<Object> object = (List<Object>)map.get(string);
            for (Object entry2 : (List<Object>)entry.getValue()) {
                if (!linkedHashSet.contains(entry2) || object.contains(entry2)) continue;
                object.add(entry2);
                bl = true;
            }
        }
        return bl;
    }

    static Snapshot build(Map<String, Object> map) {
        Object object2;
        double d = WeaponRatioConfig.num(map.get("damageDivisor"), 100.0);
        if (!(d > 0.0)) {
            d = 100.0;
        }
        double d2 = Math.max(0.0, WeaponRatioConfig.num(map.get("uniqueMultiplier"), 1.0));
        double d3 = Math.max(0.0, WeaponRatioConfig.num(map.get("rangedWeaponDamage"), 9.0));
        boolean bl = WeaponRatioConfig.bool(map.get("autoDiscover"), true);
        LinkedHashSet<String> linkedHashSet = new LinkedHashSet<String>();
        Object object4 = map.get("defaultRatiosSlots");
        if (object4 instanceof List) {
            for (Object object322 : (List)object4) {
                if (!(object322 instanceof String)) continue;
                object2 = ((String)object322).trim().toUpperCase(Locale.ROOT);
                if (!Arrays.asList(SLOTS).contains(object2)) continue;
                linkedHashSet.add((String)object2);
            }
        } else {
            linkedHashSet.add("MAIN");
        }
        Object object5 = WeaponRatioConfig.ratios(map.get("defaultRatios"), "defaultRatios");
        if (object5 == null) {
            object5 = WeaponRatioConfig.arr(1.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
        Map<String, Object> shieldMap = map.get("shield") instanceof Map ? WeaponRatioConfig.castMap(map.get("shield")) : new LinkedHashMap<String, Object>();
        object2 = WeaponRatioConfig.ratios(shieldMap.get("ratios"), "shield.ratios");
        if (object2 == null) {
            object2 = WeaponRatioConfig.arr(0.0, 0.0, 0.1, 0.03, 0.0, 0.0);
        }
        boolean bl2 = WeaponRatioConfig.bool(shieldMap.get("scaleWithDurability"), true);
        Map<String, double[]> map2 = WeaponRatioConfig.ratioSection(map, "families");
        Map<String, double[]> map3 = WeaponRatioConfig.ratioSection(map, "tinkersFamilies");
        Map<String, double[]> map4 = WeaponRatioConfig.ratioSection(map, "weapons");
        LinkedHashMap<String, double[]> linkedHashMap = new LinkedHashMap<String, double[]>();
        Object object6 = map.get("discovered");
        if (object6 instanceof Map) {
            for (Map.Entry object7 : WeaponRatioConfig.castMap(object6).entrySet()) {
                Object object = WeaponRatioConfig.ratios(object7.getValue(), "discovered." + (String)object7.getKey());
                linkedHashMap.put(((String)object7.getKey()).trim(), (double[])(WeaponRatioConfig.isEmptyRatioObject(object7.getValue()) ? null : object));
            }
        }
        LinkedHashSet linkedHashSet2 = new LinkedHashSet();
        Object object = map.get("uniqueWeapons");
        if (object instanceof List) {
            for (Object e : (List)object) {
                if (!(e instanceof String)) continue;
                linkedHashSet2.add(((String)e).trim());
            }
        }
        return new Snapshot(d, d2, d3, bl, linkedHashSet, (double[])object5, (double[])object2, bl2, map2, map3, map4, linkedHashSet2, linkedHashMap);
    }

    private static boolean isEmptyRatioObject(Object object) {
        return object == null || object instanceof Map && WeaponRatioConfig.castMap(object).isEmpty();
    }

    private static Map<String, double[]> ratioSection(Map<String, Object> map, String string) {
        LinkedHashMap<String, double[]> linkedHashMap = new LinkedHashMap<String, double[]>();
        Object object = map.get(string);
        if (object instanceof Map) {
            for (Map.Entry<String, Object> entry : WeaponRatioConfig.castMap(object).entrySet()) {
                double[] dArray = WeaponRatioConfig.ratios(entry.getValue(), string + "." + entry.getKey());
                if (dArray == null) continue;
                linkedHashMap.put(entry.getKey().trim(), dArray);
            }
        }
        return linkedHashMap;
    }

    static double[] ratios(Object object, String string) {
        if (!(object instanceof Map)) {
            return null;
        }
        double[] dArray = new double[6];
        for (Map.Entry<String, Object> entry : WeaponRatioConfig.castMap(object).entrySet()) {
            int n = WeaponRatioConfig.statIndex(entry.getKey().trim().toUpperCase(Locale.ROOT));
            if (n < 0) {
                WeaponRatioConfig.warn("[DMZ Weapon Ratios] " + string + ": unknown stat '" + entry.getKey() + "' ignored (use STR, SKP, RES, VIT, PWR, ENE)");
                continue;
            }
            double d = WeaponRatioConfig.num(entry.getValue(), 0.0);
            dArray[n] = Double.isFinite(d) && d > 0.0 ? d : 0.0;
        }
        return dArray;
    }

    static void info(String string) {
        try {
            AdaptiveDifficultyMod.LOGGER.info(string);
        }
        catch (Throwable throwable) {
            System.out.println(string);
        }
    }

    static void warn(String string) {
        try {
            AdaptiveDifficultyMod.LOGGER.warn(string);
        }
        catch (Throwable throwable) {
            System.out.println(string);
        }
    }

    static double[] arr(double d, double d2, double d3, double d4, double d5, double d6) {
        return new double[]{d, d2, d3, d4, d5, d6};
    }

    static double[] raw(double[] dArray) {
        double[] dArray2 = new double[6];
        for (int i = 0; i < 6; ++i) {
            double d = dArray[i];
            dArray2[i] = Double.isFinite(d) && d > 0.0 ? (double)Math.round(d * 10000.0) / 10000.0 : 0.0;
        }
        return dArray2;
    }

    static double[] round3(double[] dArray) {
        double[] dArray2 = new double[6];
        for (int i = 0; i < 6; ++i) {
            dArray2[i] = Double.isFinite(dArray[i]) && dArray[i] > 0.0 ? (double)Math.round(dArray[i] * 1000.0) / 1000.0 : 0.0;
        }
        return dArray2;
    }

    static double[] normalize(double[] dArray) {
        double d = 0.0;
        double[] dArray2 = new double[6];
        for (int i = 0; i < 6; ++i) {
            double d2 = dArray[i];
            if (!Double.isFinite(d2) || !(d2 > 0.0)) continue;
            dArray2[i] = d2;
            d += d2;
        }
        if (d <= 0.0) {
            return WeaponRatioConfig.arr(1.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
        double d3 = 0.0;
        int n = -1;
        for (int i = 0; i < 6; ++i) {
            if (!(dArray2[i] > 0.0)) continue;
            dArray2[i] = (double)Math.round(dArray2[i] / d * 1000.0) / 1000.0;
            d3 += dArray2[i];
            n = i;
        }
        if (n >= 0) {
            dArray2[n] = (double)Math.round((dArray2[n] + (1.0 - d3)) * 1000.0) / 1000.0;
        }
        return dArray2;
    }

    static Map<String, Object> ratioTree(double[] dArray) {
        LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<String, Object>();
        for (int i = 0; i < 6; ++i) {
            if (!(dArray[i] > 0.0)) continue;
            linkedHashMap.put(STATS[i], dArray[i]);
        }
        return linkedHashMap;
    }

    static int statIndex(String string) {
        if (string == null) {
            return -1;
        }
        for (int i = 0; i < STATS.length; ++i) {
            if (!STATS[i].equals(string)) continue;
            return i;
        }
        return -1;
    }

    private static double num(Object object, double d) {
        return object instanceof Number ? ((Number)object).doubleValue() : d;
    }

    private static boolean bool(Object object, boolean bl) {
        return object instanceof Boolean ? (Boolean)object : bl;
    }

    static Map<String, Object> castMap(Object object) {
        return (Map)object;
    }

    private static Map<String, Object> section(Map<String, Object> map, String string) {
        Object object = map.get(string);
        if (object instanceof Map) {
            return WeaponRatioConfig.castMap(object);
        }
        LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<String, Object>();
        map.put(string, linkedHashMap);
        return linkedHashMap;
    }

    static {
        lastModified = Long.MIN_VALUE;
        HELP = Arrays.asList("DMZ weapon stat bonuses. Bonus multiplier per stat = 1 + ratio x (weapon attack damage / damageDivisor).", "Example: 100 damage weapon with {\"STR\": 0.8, \"SKP\": 0.2} and damageDivisor 100 -> 1.8x STR and 1.2x SKP.", "Stats: STR, SKP, RES, VIT, PWR, ENE. Normal weapons default to ratios adding up to 1.0; uniques add up to more (about 2.5) to stay stronger. Totals above 1.0 simply give more bonus.", "Lookup order for the held item: weapons -> discovered -> families (Simply Swords/More type by id suffix) -> tinkersFamilies -> defaultRatios.", "uniqueWeapons get their bonus multiplied by uniqueMultiplier.", "Any item with attack damage above bare-hand (or any bow/crossbow) counts as a weapon. Items not listed anywhere use defaultRatios.", "defaultRatiosSlots: where unlisted items may give the default bonus (MAIN, OFF, SLOT9). Listed weapons work in all slots.", "discovered: filled automatically with every weapon-capable item in the game that is not covered above. {} = use defaultRatios; put ratios in to customise, {\"STR\": 0} to disable.", "Weapon damage already includes hot-path.json weaponDamage overrides. Changes to this file apply within ~5 seconds.");
    }

    public static final class Snapshot {
        public final double damageDivisor;
        public final double uniqueMultiplier;
        public final double rangedWeaponDamage;
        public final boolean autoDiscover;
        public final Set<String> defaultSlots;
        public final double[] defaultRatios;
        public final double[] shieldRatios;
        public final boolean shieldScalesWithDurability;
        public final Map<String, double[]> families;
        public final Map<String, double[]> tinkersFamilies;
        public final Map<String, double[]> weapons;
        public final Set<String> uniqueWeapons;
        public final Map<String, double[]> discovered;

        Snapshot(double d, double d2, double d3, boolean bl, Set<String> set, double[] dArray, double[] dArray2, boolean bl2, Map<String, double[]> map, Map<String, double[]> map2, Map<String, double[]> map3, Set<String> set2, Map<String, double[]> map4) {
            this.damageDivisor = d;
            this.uniqueMultiplier = d2;
            this.rangedWeaponDamage = d3;
            this.autoDiscover = bl;
            this.defaultSlots = Collections.unmodifiableSet(set);
            this.defaultRatios = dArray;
            this.shieldRatios = dArray2;
            this.shieldScalesWithDurability = bl2;
            this.families = Collections.unmodifiableMap(map);
            this.tinkersFamilies = Collections.unmodifiableMap(map2);
            this.weapons = Collections.unmodifiableMap(map3);
            this.uniqueWeapons = Collections.unmodifiableSet(set2);
            this.discovered = Collections.unmodifiableMap(map4);
        }
    }

    static final class Json {
        private final String s;
        private int i;

        private Json(String string) {
            this.s = string;
        }

        static Object parse(String string) {
            Json json = new Json(string);
            json.ws();
            Object object = json.value();
            json.ws();
            if (json.i < json.s.length()) {
                throw json.err("trailing characters");
            }
            return object;
        }

        private RuntimeException err(String string) {
            int n = 1;
            for (int i = 0; i < Math.min(this.i, this.s.length()); ++i) {
                if (this.s.charAt(i) != '\n') continue;
                ++n;
            }
            return new IllegalArgumentException("JSON error at line " + n + ": " + string);
        }

        private void ws() {
            while (this.i < this.s.length()) {
                char c = this.s.charAt(this.i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    ++this.i;
                    continue;
                }
                if (c != '/' || this.i + 1 >= this.s.length() || this.s.charAt(this.i + 1) != '/') break;
                while (this.i < this.s.length() && this.s.charAt(this.i) != '\n') {
                    ++this.i;
                }
            }
        }

        private Object value() {
            if (this.i >= this.s.length()) {
                throw this.err("unexpected end");
            }
            char c = this.s.charAt(this.i);
            if (c == '{') {
                return this.object();
            }
            if (c == '[') {
                return this.array();
            }
            if (c == '\"') {
                return this.string();
            }
            if (this.s.startsWith("true", this.i)) {
                this.i += 4;
                return Boolean.TRUE;
            }
            if (this.s.startsWith("false", this.i)) {
                this.i += 5;
                return Boolean.FALSE;
            }
            if (this.s.startsWith("null", this.i)) {
                this.i += 4;
                return null;
            }
            return this.number();
        }

        private Map<String, Object> object() {
            LinkedHashMap<String, Object> linkedHashMap;
            block5: {
                linkedHashMap = new LinkedHashMap<String, Object>();
                ++this.i;
                this.ws();
                if (this.i < this.s.length() && this.s.charAt(this.i) == '}') {
                    ++this.i;
                    return linkedHashMap;
                }
                do {
                    this.ws();
                    if (this.i >= this.s.length() || this.s.charAt(this.i) != '\"') {
                        throw this.err("expected a \"key\"");
                    }
                    String string = this.string();
                    this.ws();
                    if (this.i >= this.s.length() || this.s.charAt(this.i) != ':') {
                        throw this.err("expected ':' after \"" + string + "\"");
                    }
                    ++this.i;
                    this.ws();
                    linkedHashMap.put(string, this.value());
                    this.ws();
                    if (this.i >= this.s.length() || this.s.charAt(this.i) != ',') break block5;
                    ++this.i;
                    this.ws();
                } while (this.i >= this.s.length() || this.s.charAt(this.i) != '}');
                ++this.i;
                return linkedHashMap;
            }
            if (this.i < this.s.length() && this.s.charAt(this.i) == '}') {
                ++this.i;
                return linkedHashMap;
            }
            throw this.err("expected ',' or '}'");
        }

        private List<Object> array() {
            ArrayList<Object> arrayList;
            block3: {
                arrayList = new ArrayList<Object>();
                ++this.i;
                this.ws();
                if (this.i < this.s.length() && this.s.charAt(this.i) == ']') {
                    ++this.i;
                    return arrayList;
                }
                do {
                    this.ws();
                    arrayList.add(this.value());
                    this.ws();
                    if (this.i >= this.s.length() || this.s.charAt(this.i) != ',') break block3;
                    ++this.i;
                    this.ws();
                } while (this.i >= this.s.length() || this.s.charAt(this.i) != ']');
                ++this.i;
                return arrayList;
            }
            if (this.i < this.s.length() && this.s.charAt(this.i) == ']') {
                ++this.i;
                return arrayList;
            }
            throw this.err("expected ',' or ']'");
        }

        private String string() {
            StringBuilder stringBuilder = new StringBuilder();
            ++this.i;
            while (this.i < this.s.length()) {
                char c;
                if ((c = this.s.charAt(this.i++)) == '\"') {
                    return stringBuilder.toString();
                }
                if (c == '\\' && this.i < this.s.length()) {
                    char c2 = this.s.charAt(this.i++);
                    switch (c2) {
                        case 'n': {
                            stringBuilder.append('\n');
                            break;
                        }
                        case 't': {
                            stringBuilder.append('\t');
                            break;
                        }
                        case 'r': {
                            stringBuilder.append('\r');
                            break;
                        }
                        case 'b': {
                            stringBuilder.append('\b');
                            break;
                        }
                        case 'f': {
                            stringBuilder.append('\f');
                            break;
                        }
                        case 'u': {
                            if (this.i + 4 > this.s.length()) {
                                throw this.err("bad \\u escape");
                            }
                            stringBuilder.append((char)Integer.parseInt(this.s.substring(this.i, this.i + 4), 16));
                            this.i += 4;
                            break;
                        }
                        default: {
                            stringBuilder.append(c2);
                            break;
                        }
                    }
                    continue;
                }
                stringBuilder.append(c);
            }
            throw this.err("unterminated string");
        }

        private Double number() {
            int n = this.i;
            while (this.i < this.s.length() && "+-0123456789.eE".indexOf(this.s.charAt(this.i)) >= 0) {
                ++this.i;
            }
            if (n == this.i) {
                throw this.err("unexpected character '" + this.s.charAt(this.i) + "'");
            }
            try {
                return Double.parseDouble(this.s.substring(n, this.i));
            }
            catch (NumberFormatException numberFormatException) {
                throw this.err("bad number");
            }
        }

        static String write(Object object) {
            StringBuilder stringBuilder = new StringBuilder();
            Json.write(stringBuilder, object, 0);
            stringBuilder.append('\n');
            return stringBuilder.toString();
        }

        private static void indent(StringBuilder stringBuilder, int n) {
            for (int i = 0; i < n; ++i) {
                stringBuilder.append("  ");
            }
        }

        private static boolean isRatioMap(Map<?, ?> map) {
            if (map.isEmpty() || map.size() > 6) {
                return map.isEmpty();
            }
            for (Object obj : map.values()) {
                if (obj instanceof Number) continue;
                return false;
            }
            return true;
        }

        private static void write(StringBuilder stringBuilder, Object object, int n) {
            if (object instanceof Map) {
                Map<String, Object> map = (Map<String, Object>)object;
                if (Json.isRatioMap(map)) {
                    stringBuilder.append('{');
                    int n2 = 0;
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        stringBuilder.append(n2++ == 0 ? "" : ", ");
                        Json.str(stringBuilder, (String)entry.getKey());
                        stringBuilder.append(": ");
                        Json.write(stringBuilder, entry.getValue(), n);
                    }
                    stringBuilder.append('}');
                    return;
                }
                stringBuilder.append("{\n");
                int n3 = 0;
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (n3++ > 0) {
                        stringBuilder.append(",\n");
                    }
                    Json.indent(stringBuilder, n + 1);
                    Json.str(stringBuilder, (String)entry.getKey());
                    stringBuilder.append(": ");
                    Json.write(stringBuilder, entry.getValue(), n + 1);
                }
                stringBuilder.append('\n');
                Json.indent(stringBuilder, n);
                stringBuilder.append('}');
            } else if (object instanceof List) {
                List list = (List)object;
                if (list.isEmpty()) {
                    stringBuilder.append("[]");
                    return;
                }
                stringBuilder.append("[\n");
                for (int i = 0; i < list.size(); ++i) {
                    if (i > 0) {
                        stringBuilder.append(",\n");
                    }
                    Json.indent(stringBuilder, n + 1);
                    Json.write(stringBuilder, list.get(i), n + 1);
                }
                stringBuilder.append('\n');
                Json.indent(stringBuilder, n);
                stringBuilder.append(']');
            } else if (object instanceof String) {
                Json.str(stringBuilder, (String)object);
            } else if (object instanceof Double || object instanceof Float) {
                double d = ((Number)object).doubleValue();
                if (d == Math.rint(d) && Math.abs(d) < 1.0E15) {
                    stringBuilder.append((long)d).append(".0");
                } else {
                    stringBuilder.append(d);
                }
            } else if (object == null) {
                stringBuilder.append("null");
            } else {
                stringBuilder.append(object);
            }
        }

        private static void str(StringBuilder stringBuilder, String string) {
            stringBuilder.append('\"');
            block6: for (int i = 0; i < string.length(); ++i) {
                char c = string.charAt(i);
                switch (c) {
                    case '\"': {
                        stringBuilder.append("\\\"");
                        continue block6;
                    }
                    case '\\': {
                        stringBuilder.append("\\\\");
                        continue block6;
                    }
                    case '\n': {
                        stringBuilder.append("\\n");
                        continue block6;
                    }
                    case '\t': {
                        stringBuilder.append("\\t");
                        continue block6;
                    }
                    default: {
                        if (c < ' ') {
                            stringBuilder.append(String.format(Locale.ROOT, "\\u%04x", (int)c));
                            continue block6;
                        }
                        stringBuilder.append(c);
                    }
                }
            }
            stringBuilder.append('\"');
        }
    }
}
