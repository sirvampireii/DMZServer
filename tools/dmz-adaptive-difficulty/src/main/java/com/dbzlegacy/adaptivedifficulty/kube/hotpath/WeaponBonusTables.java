package com.dbzlegacy.adaptivedifficulty.kube.hotpath;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WeaponBonusTables {
    public static final String[] STATS = new String[]{"STR", "SKP", "RES", "VIT", "PWR", "ENE"};
    public static final String[] THIRD_ORDER = new String[]{"VIT", "ENE", "RES", "PWR", "SKP", "STR"};
    public static final String[][] SIMPLY_SUFFIXES = new String[][]{{"_backhand_blade", "backhand_blade"}, {"_quarterstaff", "quarterstaff"}, {"_great_katana", "great_katana"}, {"_great_spear", "great_spear"}, {"_deer_horns", "deer_horns"}, {"_greathammer", "greathammer"}, {"_grandsword", "grandsword"}, {"_twinblade", "twinblade"}, {"_warglaive", "warglaive"}, {"_longsword", "longsword"}, {"_greataxe", "greataxe"}, {"_chakram", "chakram"}, {"_claymore", "claymore"}, {"_cutlass", "cutlass"}, {"_halberd", "halberd"}, {"_khopesh", "khopesh"}, {"_pernach", "pernach"}, {"_rapier", "rapier"}, {"_scythe", "scythe"}, {"_glaive", "glaive"}, {"_katana", "katana"}, {"_dagger", "dagger"}, {"_lance", "lance"}, {"_spear", "spear"}, {"_sai", "sai"}};
    private static final Map<String, double[]> FAMILY;
    private static final Map<String, String> SIMPLY_EXACT;
    private static final Map<String, String> UNIQUE_THIRD;
    private static final Map<String, WeaponDef> IRONS;
    private static final Map<String, WeaponDef> DMZ;
    private static final Map<String, double[]> TINKERS;
    static final String[] MIMICRY_FORMS;

    private WeaponBonusTables() {
    }

    public static double[] family(String string) {
        return string == null ? null : FAMILY.get(string);
    }

    public static String simplyExact(String string) {
        return string == null ? null : SIMPLY_EXACT.get(string);
    }

    public static String uniqueThird(String string) {
        return string == null ? null : UNIQUE_THIRD.get(string);
    }

    public static WeaponDef irons(String string) {
        return string == null ? null : IRONS.get(string);
    }

    public static WeaponDef dmz(String string) {
        return string == null ? null : DMZ.get(string);
    }

    public static double[] tinkers(String string) {
        return string == null ? null : TINKERS.get(string);
    }

    static Map<String, double[]> familyMap() {
        return FAMILY;
    }

    static Map<String, String> simplyExactMap() {
        return SIMPLY_EXACT;
    }

    static Map<String, String> uniqueThirdMap() {
        return UNIQUE_THIRD;
    }

    static Map<String, WeaponDef> ironsMap() {
        return IRONS;
    }

    static Map<String, WeaponDef> dmzMap() {
        return DMZ;
    }

    static Map<String, double[]> tinkersMap() {
        return TINKERS;
    }

    private static double[] p(double d, double d2, double d3, double d4, double d5, double d6) {
        return new double[]{d, d2, d3, d4, d5, d6};
    }

    private static WeaponDef def(String string, double d, double d2, double d3, double d4, double d5, double d6) {
        return new WeaponDef(string, WeaponBonusTables.p(d, d2, d3, d4, d5, d6));
    }

    static {
        MIMICRY_FORMS = new String[]{"backhand_blade", "chakram", "claymore", "cutlass", "dagger", "deer_horns", "glaive", "grandsword", "great_katana", "great_spear", "greataxe", "greathammer", "halberd", "katana", "khopesh", "lance", "longsword", "pernach", "quarterstaff", "rapier", "sai", "scythe", "spear", "twinblade", "warglaive"};
        LinkedHashMap<String, double[]> linkedHashMap = new LinkedHashMap<String, double[]>();
        linkedHashMap.put("backhand_blade", WeaponBonusTables.p(Double.NaN, 0.12, Double.NaN, Double.NaN, Double.NaN, 0.05));
        linkedHashMap.put("chakram", WeaponBonusTables.p(Double.NaN, 0.08, Double.NaN, Double.NaN, Double.NaN, 0.12));
        linkedHashMap.put("claymore", WeaponBonusTables.p(0.16, Double.NaN, 0.07, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap.put("cutlass", WeaponBonusTables.p(0.09, 0.09, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap.put("dagger", WeaponBonusTables.p(Double.NaN, 0.15, Double.NaN, Double.NaN, Double.NaN, 0.04));
        linkedHashMap.put("deer_horns", WeaponBonusTables.p(Double.NaN, 0.12, 0.05, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap.put("glaive", WeaponBonusTables.p(0.08, 0.11, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap.put("grandsword", WeaponBonusTables.p(0.17, Double.NaN, 0.08, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap.put("great_katana", WeaponBonusTables.p(0.07, 0.15, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap.put("great_spear", WeaponBonusTables.p(0.1, 0.11, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap.put("greataxe", WeaponBonusTables.p(0.18, Double.NaN, 0.06, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap.put("greathammer", WeaponBonusTables.p(0.17, Double.NaN, 0.09, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap.put("halberd", WeaponBonusTables.p(0.11, 0.09, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap.put("katana", WeaponBonusTables.p(Double.NaN, 0.15, Double.NaN, Double.NaN, Double.NaN, 0.04));
        linkedHashMap.put("khopesh", WeaponBonusTables.p(0.09, 0.1, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap.put("lance", WeaponBonusTables.p(0.12, 0.08, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap.put("longsword", WeaponBonusTables.p(0.1, 0.1, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap.put("pernach", WeaponBonusTables.p(0.15, Double.NaN, 0.07, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap.put("quarterstaff", WeaponBonusTables.p(Double.NaN, 0.1, 0.09, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap.put("rapier", WeaponBonusTables.p(Double.NaN, 0.17, Double.NaN, Double.NaN, Double.NaN, 0.03));
        linkedHashMap.put("sai", WeaponBonusTables.p(Double.NaN, 0.15, Double.NaN, Double.NaN, Double.NaN, 0.04));
        linkedHashMap.put("scythe", WeaponBonusTables.p(Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.1, 0.18));
        linkedHashMap.put("sickle", WeaponBonusTables.p(Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.08, 0.15));
        linkedHashMap.put("spear", WeaponBonusTables.p(0.08, 0.12, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap.put("twinblade", WeaponBonusTables.p(0.07, 0.12, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap.put("warglaive", WeaponBonusTables.p(0.1, 0.1, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap.put("mace", WeaponBonusTables.p(0.15, Double.NaN, 0.08, Double.NaN, Double.NaN, Double.NaN));
        FAMILY = Collections.unmodifiableMap(linkedHashMap);
        LinkedHashMap<String, String> linkedHashMap2 = new LinkedHashMap<String, String>();
        linkedHashMap2.put("simplyswords:emberlash", "backhand_blade");
        linkedHashMap2.put("simplymore:boas_fang", "backhand_blade");
        linkedHashMap2.put("simplymore:smouldering_ruin", "backhand_blade");
        linkedHashMap2.put("simplyswords:mjolnir", "chakram");
        linkedHashMap2.put("simplyswords:tempest", "chakram");
        linkedHashMap2.put("simplymore:vipers_call", "chakram");
        linkedHashMap2.put("simplymore:blade_of_the_grotesque", "claymore");
        linkedHashMap2.put("simplyswords:caelestis", "claymore");
        linkedHashMap2.put("simplymore:deaths_eyrie", "claymore");
        linkedHashMap2.put("simplyswords:twisted_blade", "claymore");
        linkedHashMap2.put("simplyswords:slumbering_lichblade", "claymore");
        linkedHashMap2.put("simplyswords:waking_lichblade", "claymore");
        linkedHashMap2.put("simplyswords:awakened_lichblade", "claymore");
        linkedHashMap2.put("simplyswords:waxweaver", "claymore");
        linkedHashMap2.put("simplymore:black_pearl", "cutlass");
        linkedHashMap2.put("simplymore:the_vessel_breach", "dagger");
        linkedHashMap2.put("simplymore:culterex", "dagger");
        linkedHashMap2.put("simplymore:tidebreaker", "deer_horns");
        linkedHashMap2.put("simplymore:brassturn", "deer_horns");
        linkedHashMap2.put("simplymore:soul_foreseer", "glaive");
        linkedHashMap2.put("simplyswords:flamewind", "glaive");
        linkedHashMap2.put("simplymore:grandfrost", "grandsword");
        linkedHashMap2.put("simplyswords:enigma", "grandsword");
        linkedHashMap2.put("simplyswords:ribboncleaver", "grandsword");
        linkedHashMap2.put("simplymore:molten_flare", "grandsword");
        linkedHashMap2.put("simplymore:great_slither", "great_katana");
        linkedHashMap2.put("simplymore:lustrous_moxie", "great_katana");
        linkedHashMap2.put("simplymore:perforiscus", "great_spear");
        linkedHashMap2.put("simplymore:serpentine_valour", "great_spear");
        linkedHashMap2.put("simplymore:earthshatter", "greataxe");
        linkedHashMap2.put("simplyswords:soulpyre", "greataxe");
        linkedHashMap2.put("simplyswords:hearthflame", "greathammer");
        linkedHashMap2.put("simplyswords:soulkeeper", "greathammer");
        linkedHashMap2.put("simplyswords:hiveheart", "greathammer");
        linkedHashMap2.put("simplyswords:icewhisper", "halberd");
        linkedHashMap2.put("simplyswords:arcanethyst", "halberd");
        linkedHashMap2.put("simplyswords:thunderbrand", "halberd");
        linkedHashMap2.put("simplymore:revvengine", "katana");
        linkedHashMap2.put("simplyswords:whisperwind", "katana");
        linkedHashMap2.put("simplymore:timekeeper", "khopesh");
        linkedHashMap2.put("simplymore:myrmedge", "khopesh");
        linkedHashMap2.put("simplymore:glimmerstep", "lance");
        linkedHashMap2.put("simplymore:jester_penetrate", "lance");
        linkedHashMap2.put("simplymore:exedrill", "lance");
        linkedHashMap2.put("simplymore:matterbane", "longsword");
        linkedHashMap2.put("simplyswords:decaying_relic", "longsword");
        linkedHashMap2.put("simplyswords:stormbringer", "longsword");
        linkedHashMap2.put("simplyswords:emberblade", "longsword");
        linkedHashMap2.put("simplyswords:dormant_relic", "longsword");
        linkedHashMap2.put("simplyswords:righteous_relic", "longsword");
        linkedHashMap2.put("simplyswords:tainted_relic", "longsword");
        linkedHashMap2.put("simplyswords:sunfire", "longsword");
        linkedHashMap2.put("simplyswords:harbinger", "longsword");
        linkedHashMap2.put("simplymore:the_pan", "pernach");
        linkedHashMap2.put("simplymore:cindergorge", "pernach");
        linkedHashMap2.put("simplymore:ruptured_idol", "pernach");
        linkedHashMap2.put("simplymore:ascended_idol", "pernach");
        linkedHashMap2.put("simplymore:tarnished_idol", "pernach");
        linkedHashMap2.put("simplymore:holylight", "pernach");
        linkedHashMap2.put("simplymore:darksent", "pernach");
        linkedHashMap2.put("simplymore:stasis", "quarterstaff");
        linkedHashMap2.put("simplymore:ruyi_jingu_bang", "quarterstaff");
        linkedHashMap2.put("simplyswords:bramblethorn", "rapier");
        linkedHashMap2.put("simplyswords:shadowsting", "rapier");
        linkedHashMap2.put("simplyswords:soulstealer", "sai");
        linkedHashMap2.put("simplyswords:magiscythe", "scythe");
        linkedHashMap2.put("simplyswords:soulrender", "scythe");
        linkedHashMap2.put("simplymore:the_blood_harvester", "scythe");
        linkedHashMap2.put("simplyswords:wickpiercer", "spear");
        linkedHashMap2.put("simplyswords:magispear", "spear");
        linkedHashMap2.put("simplyswords:sword_on_a_stick", "spear");
        linkedHashMap2.put("simplyswords:storms_edge", "twinblade");
        linkedHashMap2.put("simplyswords:magiblade", "twinblade");
        linkedHashMap2.put("simplyswords:stars_edge", "twinblade");
        linkedHashMap2.put("simplyswords:dreadtide", "twinblade");
        linkedHashMap2.put("simplyswords:frostfall", "mace");
        linkedHashMap2.put("simplyswords:livyatan", "sickle");
        linkedHashMap2.put("simplyswords:molten_edge", "sickle");
        linkedHashMap2.put("simplyswords:bloodwake", "cutlass");
        linkedHashMap2.put("simplyswords:chompolotl", "chakram");
        linkedHashMap2.put("simplyswords:dawnquiver", "claymore");
        linkedHashMap2.put("simplyswords:riftmane", "claymore");
        linkedHashMap2.put("simplyswords:the_devourer", "claymore");
        linkedHashMap2.put("simplyswords:stormscale", "glaive");
        linkedHashMap2.put("simplyswords:ionbound_stormscale", "glaive");
        linkedHashMap2.put("simplyswords:wraithfang", "dagger");
        linkedHashMap2.put("simplyswords:wraithmaw", "dagger");
        linkedHashMap2.put("simplyswords:dreadwhisper", "katana");
        linkedHashMap2.put("simplyswords:gloampiercer", "spear");
        linkedHashMap2.put("simplyswords:soulstalker", "scythe");
        SIMPLY_EXACT = Collections.unmodifiableMap(linkedHashMap2);
        LinkedHashMap<String, String> linkedHashMap3 = new LinkedHashMap<String, String>();
        linkedHashMap3.put("simplyswords:watcher_claymore", "VIT");
        linkedHashMap3.put("simplyswords:brimstone_claymore", "PWR");
        linkedHashMap3.put("simplyswords:storms_edge", "ENE");
        linkedHashMap3.put("simplyswords:stormbringer", "VIT");
        linkedHashMap3.put("simplyswords:bramblethorn", "PWR");
        linkedHashMap3.put("simplyswords:watching_warglaive", "ENE");
        linkedHashMap3.put("simplyswords:toxic_longsword", "PWR");
        linkedHashMap3.put("simplyswords:emberblade", "PWR");
        linkedHashMap3.put("simplyswords:frostfall", "ENE");
        linkedHashMap3.put("simplyswords:soulpyre", "PWR");
        linkedHashMap3.put("simplyswords:molten_edge", "VIT");
        linkedHashMap3.put("simplyswords:livyatan", "RES");
        linkedHashMap3.put("simplyswords:icewhisper", "ENE");
        linkedHashMap3.put("simplyswords:arcanethyst", "PWR");
        linkedHashMap3.put("simplyswords:thunderbrand", "ENE");
        linkedHashMap3.put("simplyswords:hearthflame", "VIT");
        linkedHashMap3.put("simplyswords:twisted_blade", "VIT");
        linkedHashMap3.put("simplyswords:soulrender", "PWR");
        linkedHashMap3.put("simplyswords:soulkeeper", "VIT");
        linkedHashMap3.put("simplyswords:soulstealer", "ENE");
        linkedHashMap3.put("simplyswords:mjolnir", "PWR");
        linkedHashMap3.put("simplyswords:slumbering_lichblade", "PWR");
        linkedHashMap3.put("simplyswords:waking_lichblade", "PWR");
        linkedHashMap3.put("simplyswords:awakened_lichblade", "PWR");
        linkedHashMap3.put("simplyswords:shadowsting", "PWR");
        linkedHashMap3.put("simplyswords:dormant_relic", "RES");
        linkedHashMap3.put("simplyswords:decaying_relic", "RES");
        linkedHashMap3.put("simplyswords:tainted_relic", "PWR");
        linkedHashMap3.put("simplyswords:righteous_relic", "VIT");
        linkedHashMap3.put("simplyswords:sunfire", "VIT");
        linkedHashMap3.put("simplyswords:harbinger", "PWR");
        linkedHashMap3.put("simplyswords:whisperwind", "ENE");
        linkedHashMap3.put("simplyswords:emberlash", "ENE");
        linkedHashMap3.put("simplyswords:waxweaver", "VIT");
        linkedHashMap3.put("simplyswords:hiveheart", "VIT");
        linkedHashMap3.put("simplyswords:stars_edge", "ENE");
        linkedHashMap3.put("simplyswords:wickpiercer", "ENE");
        linkedHashMap3.put("simplyswords:tempest", "PWR");
        linkedHashMap3.put("simplyswords:flamewind", "PWR");
        linkedHashMap3.put("simplyswords:ribboncleaver", "VIT");
        linkedHashMap3.put("simplyswords:magiscythe", "RES");
        linkedHashMap3.put("simplyswords:magiblade", "PWR");
        linkedHashMap3.put("simplyswords:magispear", "PWR");
        linkedHashMap3.put("simplyswords:enigma", "ENE");
        linkedHashMap3.put("simplyswords:caelestis", "ENE");
        linkedHashMap3.put("simplyswords:dreadtide", "PWR");
        linkedHashMap3.put("simplymore:great_slither", "PWR");
        linkedHashMap3.put("simplymore:molten_flare", "PWR");
        linkedHashMap3.put("simplymore:grandfrost", "RES");
        linkedHashMap3.put("simplymore:glimmerstep", "PWR");
        linkedHashMap3.put("simplymore:the_blood_harvester", "VIT");
        linkedHashMap3.put("simplymore:myrmedge", "VIT");
        linkedHashMap3.put("simplymore:black_pearl", "VIT");
        linkedHashMap3.put("simplymore:the_vessel_breach", "VIT");
        linkedHashMap3.put("simplymore:blade_of_the_grotesque", "VIT");
        linkedHashMap3.put("simplymore:vipers_call", "PWR");
        linkedHashMap3.put("simplymore:timekeeper", "PWR");
        linkedHashMap3.put("simplymore:matterbane", "PWR");
        linkedHashMap3.put("simplymore:smouldering_ruin", "PWR");
        linkedHashMap3.put("simplymore:stasis", "ENE");
        linkedHashMap3.put("simplymore:tidebreaker", "RES");
        linkedHashMap3.put("simplymore:ruyi_jingu_bang", "VIT");
        linkedHashMap3.put("simplymore:ruptured_idol", "RES");
        linkedHashMap3.put("simplymore:ascended_idol", "VIT");
        linkedHashMap3.put("simplymore:tarnished_idol", "PWR");
        linkedHashMap3.put("simplymore:holylight", "VIT");
        linkedHashMap3.put("simplymore:darksent", "PWR");
        linkedHashMap3.put("simplymore:boas_fang", "PWR");
        linkedHashMap3.put("simplymore:earthshatter", "VIT");
        linkedHashMap3.put("simplymore:soul_foreseer", "ENE");
        linkedHashMap3.put("simplymore:serpentine_valour", "PWR");
        linkedHashMap3.put("simplymore:lustrous_moxie", "ENE");
        linkedHashMap3.put("simplymore:brassturn", "RES");
        linkedHashMap3.put("simplymore:cindergorge", "VIT");
        linkedHashMap3.put("simplymore:deaths_eyrie", "VIT");
        linkedHashMap3.put("simplymore:perforiscus", "VIT");
        linkedHashMap3.put("simplymore:revvengine", "VIT");
        linkedHashMap3.put("simplymore:exedrill", "VIT");
        linkedHashMap3.put("simplymore:culterex", "PWR");
        linkedHashMap3.put("simplyswords:bloodwake", "VIT");
        linkedHashMap3.put("simplyswords:chompolotl", "VIT");
        linkedHashMap3.put("simplyswords:dawnquiver", "ENE");
        linkedHashMap3.put("simplyswords:riftmane", "PWR");
        linkedHashMap3.put("simplyswords:the_devourer", "VIT");
        linkedHashMap3.put("simplyswords:stormscale", "ENE");
        linkedHashMap3.put("simplyswords:ionbound_stormscale", "ENE");
        linkedHashMap3.put("simplyswords:wraithfang", "PWR");
        linkedHashMap3.put("simplyswords:wraithmaw", "PWR");
        linkedHashMap3.put("simplyswords:dreadwhisper", "ENE");
        linkedHashMap3.put("simplyswords:gloampiercer", "ENE");
        linkedHashMap3.put("simplyswords:soulstalker", "PWR");
        UNIQUE_THIRD = Collections.unmodifiableMap(linkedHashMap3);
        LinkedHashMap<String, WeaponDef> linkedHashMap4 = new LinkedHashMap<String, WeaponDef>();
        linkedHashMap4.put("irons_spellbooks:graybeard_staff", WeaponBonusTables.def("staff", Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.12, 0.16));
        linkedHashMap4.put("irons_spellbooks:pyrium_staff", WeaponBonusTables.def("staff", Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.14, 0.14));
        linkedHashMap4.put("irons_spellbooks:artificer_cane", WeaponBonusTables.def("staff", Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.1, 0.15));
        linkedHashMap4.put("irons_spellbooks:ice_staff", WeaponBonusTables.def("staff", Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.13, 0.15));
        linkedHashMap4.put("irons_spellbooks:lightning_rod", WeaponBonusTables.def("staff", Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.14, 0.13));
        linkedHashMap4.put("irons_spellbooks:blood_staff", WeaponBonusTables.def("staff", Double.NaN, Double.NaN, Double.NaN, 0.08, 0.13, Double.NaN));
        linkedHashMap4.put("irons_spellbooks:improved_blood_staff", WeaponBonusTables.def("staff", Double.NaN, Double.NaN, Double.NaN, 0.1, 0.16, Double.NaN));
        linkedHashMap4.put("irons_spellbooks:staff_of_the_nines", WeaponBonusTables.def("staff", Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.16, 0.16));
        linkedHashMap4.put("irons_spellbooks:hither_thither_wand", WeaponBonusTables.def("wand", Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.08, 0.14));
        linkedHashMap4.put("irons_spellbooks:dev_staff", WeaponBonusTables.def("staff", Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.12, 0.12));
        linkedHashMap4.put("irons_spellbooks:amethyst_rapier", WeaponBonusTables.def("rapier", Double.NaN, 0.17, Double.NaN, Double.NaN, Double.NaN, 0.04));
        linkedHashMap4.put("irons_spellbooks:claymore", WeaponBonusTables.def("claymore", 0.16, Double.NaN, 0.07, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap4.put("irons_spellbooks:keeper_flamberge", WeaponBonusTables.def("flamberge", 0.13, 0.09, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap4.put("irons_spellbooks:legionnaire_flamberge", WeaponBonusTables.def("flamberge", 0.14, 0.09, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap4.put("irons_spellbooks:magehunter", WeaponBonusTables.def("sword", 0.1, 0.1, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap4.put("irons_spellbooks:spellbreaker", WeaponBonusTables.def("sword", 0.09, Double.NaN, 0.1, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap4.put("irons_spellbooks:misery", WeaponBonusTables.def("energy_sword", 0.11, Double.NaN, Double.NaN, Double.NaN, 0.1, Double.NaN));
        linkedHashMap4.put("irons_spellbooks:hellrazor", WeaponBonusTables.def("energy_sword", 0.13, Double.NaN, Double.NaN, Double.NaN, 0.1, Double.NaN));
        linkedHashMap4.put("irons_spellbooks:decrepit_scythe", WeaponBonusTables.def("scythe", Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.09, 0.16));
        linkedHashMap4.put("irons_spellbooks:boreal_blade", WeaponBonusTables.def("energy_sword", Double.NaN, 0.1, Double.NaN, Double.NaN, 0.11, Double.NaN));
        linkedHashMap4.put("irons_spellbooks:twilight_gale", WeaponBonusTables.def("energy_sword", Double.NaN, 0.08, Double.NaN, Double.NaN, 0.13, Double.NaN));
        linkedHashMap4.put("irons_spellbooks:truthseeker", WeaponBonusTables.def("greatsword", 0.1, 0.1, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap4.put("irons_spellbooks:autoloader_crossbow", WeaponBonusTables.def("crossbow", Double.NaN, 0.08, Double.NaN, Double.NaN, 0.1, Double.NaN));
        IRONS = Collections.unmodifiableMap(linkedHashMap4);
        LinkedHashMap<String, WeaponDef> linkedHashMap5 = new LinkedHashMap<String, WeaponDef>();
        linkedHashMap5.put("dragonminez:yajirobe_katana", WeaponBonusTables.def("katana", 0.06, 0.16, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap5.put("dragonminez:z_sword", WeaponBonusTables.def("heavy_sword", 0.18, Double.NaN, 0.08, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap5.put("dragonminez:brave_sword", WeaponBonusTables.def("sword", 0.12, 0.12, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap5.put("dragonminez:dimensional_sword", WeaponBonusTables.def("energy_sword", Double.NaN, 0.1, Double.NaN, Double.NaN, 0.12, Double.NaN));
        linkedHashMap5.put("dragonminez:power_pole", WeaponBonusTables.def("staff", 0.1, 0.12, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap5.put("dragonminez:laser_merus", WeaponBonusTables.def("energy_weapon", Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.14, 0.12));
        linkedHashMap5.put("dragonminez:blaster_cannon", WeaponBonusTables.def("energy_weapon", Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.12, 0.1));
        DMZ = Collections.unmodifiableMap(linkedHashMap5);
        LinkedHashMap<String, double[]> linkedHashMap6 = new LinkedHashMap<String, double[]>();
        linkedHashMap6.put("scythe", WeaponBonusTables.p(Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.1, 0.18));
        linkedHashMap6.put("cleaver", WeaponBonusTables.p(0.18, Double.NaN, 0.06, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap6.put("sword", WeaponBonusTables.p(0.1, 0.1, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap6.put("dagger", WeaponBonusTables.p(Double.NaN, 0.15, Double.NaN, Double.NaN, Double.NaN, 0.04));
        linkedHashMap6.put("kama", WeaponBonusTables.p(Double.NaN, 0.12, Double.NaN, Double.NaN, Double.NaN, 0.05));
        linkedHashMap6.put("hand_axe", WeaponBonusTables.p(0.14, 0.05, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap6.put("broad_axe", WeaponBonusTables.p(0.18, Double.NaN, 0.07, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap6.put("sledge_hammer", WeaponBonusTables.p(0.17, Double.NaN, 0.09, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap6.put("vein_hammer", WeaponBonusTables.p(0.16, Double.NaN, 0.08, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap6.put("war_pick", WeaponBonusTables.p(0.12, 0.08, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap6.put("mattock", WeaponBonusTables.p(0.1, 0.07, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap6.put("pickadze", WeaponBonusTables.p(0.1, 0.07, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap6.put("excavator", WeaponBonusTables.p(0.15, Double.NaN, 0.06, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap6.put("battlesign", WeaponBonusTables.p(Double.NaN, Double.NaN, 0.14, 0.04, Double.NaN, Double.NaN));
        linkedHashMap6.put("swasher", WeaponBonusTables.p(0.09, 0.1, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap6.put("minotaur_axe", WeaponBonusTables.p(0.18, Double.NaN, 0.06, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap6.put("melting_pan", WeaponBonusTables.p(0.12, Double.NaN, 0.07, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap6.put("javelin", WeaponBonusTables.p(0.07, 0.12, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap6.put("shuriken", WeaponBonusTables.p(Double.NaN, 0.12, Double.NaN, Double.NaN, Double.NaN, 0.05));
        linkedHashMap6.put("throwing_axe", WeaponBonusTables.p(0.08, 0.1, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        linkedHashMap6.put("crossbow", WeaponBonusTables.p(Double.NaN, 0.08, Double.NaN, Double.NaN, 0.1, Double.NaN));
        linkedHashMap6.put("longbow", WeaponBonusTables.p(Double.NaN, 0.08, Double.NaN, Double.NaN, 0.1, Double.NaN));
        linkedHashMap6.put("sky_staff", WeaponBonusTables.p(Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.12, 0.15));
        linkedHashMap6.put("earth_staff", WeaponBonusTables.p(Double.NaN, Double.NaN, 0.1, Double.NaN, 0.1, Double.NaN));
        linkedHashMap6.put("ichor_staff", WeaponBonusTables.p(Double.NaN, Double.NaN, Double.NaN, 0.08, 0.14, Double.NaN));
        linkedHashMap6.put("ender_staff", WeaponBonusTables.p(Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.13, 0.14));
        TINKERS = Collections.unmodifiableMap(linkedHashMap6);
    }

    public static final class WeaponDef {
        public final String family;
        public final double[] profile;

        WeaponDef(String string, double[] dArray) {
            this.family = string;
            this.profile = dArray;
        }
    }
}
