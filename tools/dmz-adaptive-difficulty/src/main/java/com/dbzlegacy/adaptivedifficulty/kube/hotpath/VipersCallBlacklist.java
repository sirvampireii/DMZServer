package com.dbzlegacy.adaptivedifficulty.kube.hotpath;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

public final class VipersCallBlacklist {
    public static final String FILE_NAME = "vipers-call-blacklist.json";
    static final String ITEM_NS = "simplymore";
    static final String ITEM_PATH = "vipers_call";
    static final String FIELD = "effectBlacklist";
    static final List<String> BUILT_IN = Arrays.asList("simplyswords:fatal_flicker", "minecraft:absorption", "simplyswords:flameseed", "simplyswords:frenzy");
    private static final long CHECK_MS = 5000L;
    private static volatile long lastModified = Long.MIN_VALUE;
    private static volatile long nextCheck;
    private static volatile boolean applied;
    private static volatile String[] original;

    private VipersCallBlacklist() {
    }

    static Path path() {
        return WeaponRatioConfig.path().resolveSibling(FILE_NAME);
    }

    public static void tick() {
        long l = System.currentTimeMillis();
        if (applied && l < nextCheck) {
            return;
        }
        nextCheck = l + 5000L;
        long l2 = Long.MIN_VALUE;
        try {
            Path path = VipersCallBlacklist.path();
            long l3 = l2 = Files.isRegularFile(path, new LinkOption[0]) ? Files.getLastModifiedTime(path, new LinkOption[0]).toMillis() : Long.MIN_VALUE;
            if (!applied || l2 != lastModified) {
                VipersCallBlacklist.apply();
            }
        }
        catch (Throwable throwable) {
            applied = true;
            lastModified = l2;
            WeaponRatioConfig.warn("[Viper's Call blacklist] could not apply vipers-call-blacklist.json: " + String.valueOf(throwable) + " (previous list kept)");
        }
    }

    static synchronized void apply() throws Exception {
        String[] stringArray;
        boolean bl;
        Map<String, Object> map;
        Object object;
        Path path = VipersCallBlacklist.path();
        if (Files.isRegularFile(path, new LinkOption[0])) {
            object = WeaponRatioConfig.Json.parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
            if (!(object instanceof Map)) {
                throw new IllegalArgumentException("vipers-call-blacklist.json: top level is not a JSON object");
            }
            map = WeaponRatioConfig.castMap(object);
        } else {
            map = VipersCallBlacklist.defaults();
            Files.createDirectories(path.toAbsolutePath().getParent(), new FileAttribute[0]);
            Files.write(path, WeaponRatioConfig.Json.write(map).getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
        }
        lastModified = Files.getLastModifiedTime(path, new LinkOption[0]).toMillis();
        object = (Item)ForgeRegistries.ITEMS.getValue(new ResourceLocation(ITEM_NS, ITEM_PATH));
        if (object == null || !"simplymore:vipers_call".equals(String.valueOf(ForgeRegistries.ITEMS.getKey((Item)object)))) {
            applied = true;
            return;
        }
        Field field = object.getClass().getField(FIELD);
        if (original == null) {
            Object object2 = field.get(object);
            original = object2 instanceof String[] ? (String[])((String[])object2).clone() : BUILT_IN.toArray(new String[0]);
        }
        boolean bl2 = bl = !(map.get("enabled") instanceof Boolean) || (Boolean)map.get("enabled") != false;
        if (!bl) {
            stringArray = (String[])original.clone();
        } else {
            LinkedHashSet<String> linkedHashSet = new LinkedHashSet<String>();
            Object object3 = map.get(FIELD);
            if (object3 instanceof List) {
                for (Object e : (List)object3) {
                    if (!(e instanceof String) || ((String)e).trim().isEmpty()) continue;
                    VipersCallBlacklist.expand(((String)e).trim(), linkedHashSet);
                }
            }
            stringArray = linkedHashSet.toArray(new String[0]);
        }
        field.set(object, stringArray);
        applied = true;
        WeaponRatioConfig.info("[Viper's Call blacklist] " + (String)(bl ? "applied " + stringArray.length + " blacklisted effect(s)" : "disabled, restored built-in list") + " from " + String.valueOf(path.getFileName()));
    }

    private static void expand(String string, Set<String> set) {
        if (!string.endsWith(":*")) {
            set.add(string);
            return;
        }
        String string2 = string.substring(0, string.length() - 2);
        int n = 0;
        try {
            for (ResourceLocation resourceLocation : ForgeRegistries.MOB_EFFECTS.getKeys()) {
                String string3 = resourceLocation.toString();
                if (!string3.startsWith(string2 + ":")) continue;
                set.add(string3);
                ++n;
            }
        }
        catch (Throwable throwable) {
            WeaponRatioConfig.warn("[Viper's Call blacklist] could not expand " + string + ": " + String.valueOf(throwable));
        }
        if (n == 0) {
            WeaponRatioConfig.warn("[Viper's Call blacklist] " + string + " matched no registered effects");
        }
    }

    static Map<String, Object> defaults() {
        LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<String, Object>();
        linkedHashMap.put("_help", new ArrayList<String>(Arrays.asList("Viper's Call (Simply More) refreshes every potion effect on its holder to 25 ticks each second while held in the main hand.", "Effects listed here are NOT refreshed. Use full effect ids (\"minecraft:regeneration\") or \"modid:*\" for every effect from a mod.", "This list replaces Simply More's built-in one; the first four entries are the built-in defaults.", "enabled = false restores Simply More's original list. Changes apply within ~5 seconds.")));
        linkedHashMap.put("enabled", Boolean.TRUE);
        linkedHashMap.put(FIELD, new ArrayList<String>(BUILT_IN));
        return linkedHashMap;
    }
}
