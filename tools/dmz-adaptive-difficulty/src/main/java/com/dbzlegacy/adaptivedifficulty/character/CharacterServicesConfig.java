package com.dbzlegacy.adaptivedifficulty.character;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.ConfigPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** {@code config/legacymechanics/character-services.json} — rebirth / race / class / reskin costs. */
public final class CharacterServicesConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile CharacterServicesConfig INSTANCE = defaults();

    public boolean enabled = true;
    public Permissions permissions = new Permissions();
    public Restrictions restrictions = new Restrictions();
    public RaceChange raceChange = new RaceChange();
    public ClassChange classChange = new ClassChange();
    public Reskin reskin = new Reskin();
    public HeadBoneShop headBoneShop = new HeadBoneShop();
    public BodyPartShop bodyPartShop = new BodyPartShop();

    public static CharacterServicesConfig get() {
        return INSTANCE;
    }

    /** Field defaults for the editor. Does not replace the live config. */
    public static CharacterServicesConfig freshDefaults() {
        return defaults();
    }

    public static Path path() {
        return ConfigPaths.dataDir().resolve("character-services.json");
    }

    public static void load() {
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            if (!Files.isRegularFile(file)) {
                seedDefault(file);
            }
            try (Reader reader = Files.newBufferedReader(file)) {
                CharacterServicesConfig parsed = GSON.fromJson(reader, CharacterServicesConfig.class);
                INSTANCE = sanitize(parsed == null ? defaults() : parsed);
                CosmeticHeadBoneCatalog.invalidate();
                CosmeticHeadBoneCatalog.refreshIfStale();
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] character-services config load failed: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    t.toString());
            INSTANCE = defaults();
        }
    }

    /** Writes the live config. Staff edits use this so the file matches the menu. */
    public static boolean save() {
        INSTANCE = sanitize(INSTANCE == null ? defaults() : INSTANCE);
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(INSTANCE, writer);
            }
            CosmeticHeadBoneCatalog.invalidate();
            return true;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] character-services config save failed: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    t.toString());
            return false;
        }
    }

    private static void seedDefault(Path file) throws Exception {
        try (var in = CharacterServicesConfig.class.getClassLoader()
                .getResourceAsStream("character-services.default.json")) {
            if (in != null) {
                Files.copy(in, file);
                return;
            }
        }
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(defaults(), w);
        }
    }

    private static CharacterServicesConfig defaults() {
        CharacterServicesConfig c = new CharacterServicesConfig();
        c.raceChange.preservationCostCopper = defaultPreservationCosts();
        return c;
    }

    private static Map<String, Long> defaultPreservationCosts() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (int pct : defaultPreservationSteps()) {
            m.put(String.valueOf(pct), (long) pct * 10_000L);
        }
        return m;
    }

    /**
     * 4.7.81: the keep percents the race-change menu offers (the owner's pick: 0/25/50/75/100). Every one of them is
     * always in {@link #preservationSteps()}, so the server accepts and prices them as before.
     */
    public static final int[] MENU_KEEP_STEPS = {0, 25, 50, 75, 100};

    /** Seed list used only when the live map is empty. */
    public static int[] defaultPreservationSteps() {
        return new int[] {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
    }

    /**
     * Percents the race-change GUI may offer and {@code executeRaceChange} may charge.
     * Configured keys are included, plus 25 and 75, which the confirm menu has always
     * shown even though the default map steps by 10. A missing key is priced by
     * {@link #lookupPreservationCopper}, not by substituting another percent's price.
     */
    public static int[] preservationSteps() {
        Map<String, Long> map = null;
        CharacterServicesConfig live = INSTANCE;
        if (live != null && live.raceChange != null) {
            map = live.raceChange.preservationCostCopper;
        }
        if (map == null || map.isEmpty()) {
            map = defaultPreservationCosts();
        }
        java.util.TreeSet<Integer> steps = new java.util.TreeSet<>(preservationPoints(map).keySet());
        steps.add(0);
        steps.add(25);
        steps.add(50);
        steps.add(75);
        steps.add(100);
        int[] out = new int[steps.size()];
        int i = 0;
        for (int pct : steps) {
            out[i++] = pct;
        }
        return out;
    }

    /**
     * Copper for one preservation percent. An exact map key wins. A missing key is the
     * straight line between the next lower and next higher configured costs.
     * The 100% price is not a stand-in for a missing key.
     */
    public static long lookupPreservationCopper(Map<String, Long> map, int preservationPercent) {
        if (preservationPercent <= 0) {
            return 0L;
        }
        int pct = Math.min(100, preservationPercent);
        java.util.TreeMap<Integer, Long> points = preservationPoints(map);
        points.put(0, 0L);
        Long exact = points.get(pct);
        if (exact != null) {
            return Math.max(0L, exact);
        }
        Map.Entry<Integer, Long> lower = points.floorEntry(pct);
        Map.Entry<Integer, Long> higher = points.ceilingEntry(pct);
        if (lower == null || higher == null || lower.getKey().equals(higher.getKey())) {
            Map.Entry<Integer, Long> only = lower != null ? lower : higher;
            return only == null ? 0L : Math.max(0L, only.getValue());
        }
        long span = (long) higher.getKey() - lower.getKey();
        long rise = higher.getValue() - lower.getValue();
        long steps = (long) pct - lower.getKey();
        double interpolated = lower.getValue() + (rise * (double) steps) / (double) span;
        if (interpolated >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        if (interpolated <= 0.0) {
            return 0L;
        }
        return Math.round(interpolated);
    }

    /** Parse the cost map without assuming Gson stored each value as {@link Long}. */
    static java.util.TreeMap<Integer, Long> preservationPoints(Map<String, Long> map) {
        java.util.TreeMap<Integer, Long> points = new java.util.TreeMap<>();
        if (map == null || map.isEmpty()) {
            return points;
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) map).entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            int pct = parsePercentKey(entry.getKey());
            if (pct < 0 || pct > 100) {
                continue;
            }
            long cost = parseCostValue(entry.getValue());
            if (cost < 0L) {
                continue;
            }
            points.put(pct, cost);
        }
        return points;
    }

    private static int parsePercentKey(Object raw) {
        String text = String.valueOf(raw).trim();
        try {
            if (text.indexOf('.') >= 0) {
                return (int) Math.round(Double.parseDouble(text));
            }
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static long parseCostValue(Object raw) {
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            if (!Double.isFinite(value) || value < 0.0) {
                return -1L;
            }
            return Math.round(value);
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static CharacterServicesConfig sanitize(CharacterServicesConfig c) {
        if (c.permissions == null) {
            c.permissions = new Permissions();
        }
        if (c.restrictions == null) {
            c.restrictions = new Restrictions();
        }
        if (c.raceChange == null) {
            c.raceChange = new RaceChange();
        }
        if (c.classChange == null) {
            c.classChange = new ClassChange();
        }
        if (c.reskin == null) {
            c.reskin = new Reskin();
        }
        if (c.headBoneShop == null) {
            c.headBoneShop = new HeadBoneShop();
        }
        if (c.headBoneShop.boneCosts == null) {
            c.headBoneShop.boneCosts = new LinkedHashMap<>();
        }
        if (c.headBoneShop.displayNames == null) {
            c.headBoneShop.displayNames = new LinkedHashMap<>();
        }
        if (c.headBoneShop.excludeBones == null) {
            c.headBoneShop.excludeBones = Collections.emptyList();
        }
        if (c.bodyPartShop == null) {
            c.bodyPartShop = new BodyPartShop();
        }
        if (c.bodyPartShop.partCosts == null) {
            c.bodyPartShop.partCosts = new LinkedHashMap<>();
        }
        if (c.bodyPartShop.displayNames == null) {
            c.bodyPartShop.displayNames = new LinkedHashMap<>();
        }
        if (c.bodyPartShop.excludeParts == null) {
            c.bodyPartShop.excludeParts = Collections.emptyList();
        }
        if (c.raceChange.preservationCostCopper == null || c.raceChange.preservationCostCopper.isEmpty()) {
            c.raceChange.preservationCostCopper = defaultPreservationCosts();
        } else {
            c.raceChange.preservationCostCopper.putIfAbsent("0", 0L);
        }
        if (c.raceChange.blockedRaces == null) {
            c.raceChange.blockedRaces = Collections.emptyList();
        }
        return c;
    }

    public long raceCostCopper(int preservationPercent) {
        return lookupPreservationCopper(
                raceChange == null ? null : raceChange.preservationCostCopper,
                preservationPercent);
    }

    public Set<String> blockedRaceSet() {
        Set<String> out = new TreeSet<>();
        for (String r : raceChange.blockedRaces) {
            if (r != null && !r.isBlank()) {
                out.add(r.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    public static final class Permissions {
        public String services = "legacymechanics.character.services";
        public String race = "legacymechanics.character.racechange";
        public String classChange = "legacymechanics.character.classchange";
        public String reskin = "legacymechanics.character.reskin";
        public String bypassCost = "legacymechanics.character.bypass.cost";
        public String bypassCooldown = "legacymechanics.character.bypass.cooldown";
        public String admin = "legacymechanics.character.admin";
        public String headBones = "legacymechanics.character.headbones";
        public String bodyParts = "legacymechanics.character.bodyparts";
    }

    public static final class Restrictions {
        public boolean blockWhileDead = true;
        /** When true, legacy strict mode (unused — services auto-detransform instead). */
        public boolean blockWhileTransformed = false;
        public boolean blockWhileInCombat = false;
        public boolean blockWhileSparring = true;
    }

    public static final class RaceChange {
        public boolean enabled = true;
        public long cooldownMs = 12L * 60L * 60L * 1000L;
        public boolean keepSkillsOnRaceChange = true;
        public Map<String, Long> preservationCostCopper = defaultPreservationCosts();
        public boolean levelCostMultiplier = true;
        public java.util.List<String> blockedRaces = Collections.emptyList();
    }

    public static final class ClassChange {
        public boolean enabled = true;
        public long cooldownMs = 12L * 60L * 60L * 1000L;
        public long baseCostCopper = 250_000L;
        public boolean levelCostMultiplier = true;
        public boolean preserveBaseStats = true;
        public boolean resetClassProgression = true;
    }

    public static final class Reskin {
        public boolean enabled = true;
        public long cooldownMs = 12L * 60L * 60L * 1000L;
        public long baseCostCopper = 50_000L;
        public boolean levelCostMultiplier = true;
        public boolean openDmzRecustomizeOnly = true;
        /** DMZ recustomize UI includes a class tab — server locks class while reskin session is active. */
        public boolean lockClassDuringEditor = true;
    }

    /** Global catalog shop — unlock head parts from any race for cross-race cosmetics. */
    public static final class HeadBoneShop {
        public boolean enabled = true;
        public long defaultUnlockCostCopper = 100_000L;
        public boolean levelCostMultiplier = true;
        /** Parts listed on the player's current race config do not require a shop unlock. */
        public boolean nativeRaceBonesFree = true;
        public Map<String, Long> boneCosts = new LinkedHashMap<>();
        public Map<String, String> displayNames = new LinkedHashMap<>();
        public java.util.List<String> excludeBones = Collections.emptyList();
        /** Re-apply {@link CharacterServicesStore.PlayerRecord#equippedHeadBone} after transformations. */
        public boolean persistThroughForms = true;

        public boolean isExcluded(String boneId) {
            if (boneId == null || excludeBones == null) {
                return false;
            }
            String want = boneId.trim().toLowerCase(Locale.ROOT);
            for (String e : excludeBones) {
                if (e != null && want.equals(e.trim().toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Custom body parts shop — DMZUltra FormParts pieces players pick on the model
     * customization screen (KaiPath 1.3.29 attaches {@code playerModel.parts.bodyParts}).
     * Pieces are shown on the KaiPath preview via the bridge wire; unlocks persist in
     * {@link CharacterServicesStore}.
     */
    public static final class BodyPartShop {
        public boolean enabled = true;
        public long defaultUnlockCostCopper = 100_000L;
        public boolean levelCostMultiplier = true;
        public Map<String, Long> partCosts = new LinkedHashMap<>();
        public Map<String, String> displayNames = new LinkedHashMap<>();
        public java.util.List<String> excludeParts = Collections.emptyList();

        public boolean isExcluded(String partId) {
            if (partId == null || excludeParts == null) {
                return false;
            }
            String want = partId.trim().toLowerCase(Locale.ROOT);
            for (String e : excludeParts) {
                if (e != null && want.equals(e.trim().toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
    }
}
