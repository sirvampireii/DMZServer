package com.dbzlegacy.adaptivedifficulty.gui;

import com.dbzlegacy.adaptivedifficulty.character.CharacterServicesAccess;
import com.dbzlegacy.adaptivedifficulty.character.CharacterServicesConfig;
import com.dbzlegacy.adaptivedifficulty.character.CharacterServicesSystem;
import com.dbzlegacy.adaptivedifficulty.character.CosmeticBodyPartService;
import com.dbzlegacy.adaptivedifficulty.character.CosmeticHeadBoneService;
import com.dbzlegacy.adaptivedifficulty.character.FightingClassLabels;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.calc.LmOverhaulScaledCombat;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy;
import com.dbzlegacy.adaptivedifficulty.rival.NemesisSpawnMode;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/** Bukkit GUI bridge for Character Services ({@code /character} / hub). */
public final class CharacterServicesGuiApi {
    private CharacterServicesGuiApi() {}

    public static Map<String, String> placeholders(ServerPlayer player) {
        Map<String, String> out = new HashMap<>();
        out.put("bridge_ok", "false");
        if (player == null) {
            return out;
        }
        CharacterServicesConfig cfg = CharacterServicesConfig.get();
        out.put("bridge_ok", "true");
        out.put("enabled", cfg.enabled ? "true" : "false");
        out.put("race_enabled", cfg.raceChange.enabled ? "true" : "false");
        out.put("class_enabled", cfg.classChange.enabled ? "true" : "false");
        out.put("reskin_enabled", cfg.reskin.enabled ? "true" : "false");
        out.put("head_bone_shop_enabled", cfg.headBoneShop.enabled ? "true" : "false");
        out.put("active_head_bone", titleBone(CosmeticHeadBoneService.activeBone(player)));
        out.put("current_race", title(DmzProgression.race(player)));
        out.put("current_class", FightingClassLabels.display(resolveFightingClassRaw(player)));
        putWalletPlaceholders(player, out);
        boolean bypassCost = CharacterServicesAccess.bypassCost(player);
        out.put("bypass_cost", bypassCost ? "true" : "false");
        out.put("reskin_cost", CharacterServicesSystem.formatCost(CharacterServicesSystem.reskinAmountDue(player)));
        out.put("class_cost", CharacterServicesSystem.formatCost(CharacterServicesSystem.classAmountDue(player)));
        out.put("race_cost_100", CharacterServicesSystem.formatCost(CharacterServicesSystem.raceAmountDue(player, 100)));
        out.put("race_keep_steps", keepStepsLabel());
        out.put("race_cooldown", CharacterServicesSystem.cooldownLine(player, "race"));
        out.put("class_cooldown", CharacterServicesSystem.cooldownLine(player, "class"));
        out.put("reskin_cooldown", CharacterServicesSystem.cooldownLine(player, "reskin"));
        out.put("can_services", cfg.enabled && CharacterServicesAccess.canUseServices(player) ? "true" : "false");
        out.put("can_race_change",
                cfg.enabled && cfg.raceChange.enabled && CharacterServicesAccess.canRaceChange(player)
                        ? "true"
                        : "false");
        out.put("can_class_change",
                cfg.enabled && cfg.classChange.enabled && CharacterServicesAccess.canClassChange(player)
                        ? "true"
                        : "false");
        out.put("can_reskin",
                cfg.enabled && cfg.reskin.enabled && CharacterServicesAccess.canReskin(player) ? "true" : "false");
        boolean formModelBlocked = CosmeticHeadBoneService.isFormModelRace(player);
        out.put("head_bones_form_model_blocked", formModelBlocked ? "true" : "false");
        out.put("can_head_bones",
                cfg.enabled
                                && cfg.headBoneShop.enabled
                                && CharacterServicesAccess.canHeadBoneShop(player)
                                && !formModelBlocked
                        ? "true"
                        : "false");
        LmOverhaulScaledCombat.putPlaceholders(out, player);
        return out;
    }

    public static List<String> linesForPage(ServerPlayer player, String page) {
        String p = page == null || page.isBlank() ? "main" : page.toLowerCase(Locale.ROOT);
        List<String> lines = new ArrayList<>();
        if (!CharacterServicesConfig.get().enabled) {
            lines.add("§cCharacter Services are turned off on this server.");
            return lines;
        }
        if ("race".equals(p)) {
            if (!CharacterServicesConfig.get().raceChange.enabled) {
                lines.add("§cRace change is turned off on this server.");
                return lines;
            }
            if (!CharacterServicesAccess.canRaceChange(player)) {
                lines.add("§cYou do not have permission for race change.");
                return lines;
            }
            lines.add("§7Choose the race you want to become.");
            lines.add(costLine(player, CharacterServicesSystem.raceAmountDue(player, 100)));
            lines.add("§8Listed cost is for keeping §f100%§8. §f0%§8 keep is free - pick keep % on the next screen.");
            if (CharacterServicesConfig.get().raceChange.keepSkillsOnRaceChange) {
                lines.add("§7You keep ki skills, techniques, and shared form progress.");
            } else {
                lines.add("§7Ki skills, techniques, and shared form progress reset with this race.");
            }
            lines.add("§8Forms that only belong to your old race reset if the new race cannot use them.");
            lines.add(CharacterServicesSystem.cooldownLine(player, "race"));
            return lines;
        }
        if ("class".equals(p)) {
            if (!CharacterServicesConfig.get().classChange.enabled) {
                lines.add("§cClass change is turned off on this server.");
                return lines;
            }
            if (!CharacterServicesAccess.canClassChange(player)) {
                lines.add("§cYou do not have permission for class change.");
                return lines;
            }
            lines.add("§7Pick a new fighting class for your §fcurrent race§7.");
            lines.add("§7Only classes that fit your current race are listed.");
            lines.add(CharacterServicesConfig.get().classChange.preserveBaseStats
                    ? "§7Your base combat stats stay - class skills and progression reset."
                    : "§7Class skills and progression reset.");
            lines.add(costLine(player, CharacterServicesSystem.classAmountDue(player)));
            lines.add(CharacterServicesSystem.cooldownLine(player, "class"));
            return lines;
        }
        if (p.startsWith("bones:") || "bones".equals(p)) {
            if (!CharacterServicesConfig.get().headBoneShop.enabled) {
                lines.add("§cModel customization is turned off on this server.");
                return lines;
            }
            if (!CharacterServicesAccess.canHeadBoneShop(player)) {
                lines.add("§cYou do not have permission for model customization.");
                return lines;
            }
            lines.add("§7One piece from each group. Demon horns can be worn together.");
            lines.add("§7A locked part charges Ancient Coins when you turn it on.");
            return lines;
        }
        if (p.startsWith("bodyparts:") || "bodyparts".equals(p)) {
            if (!CharacterServicesConfig.get().bodyPartShop.enabled) {
                lines.add("§cBody parts are turned off on this server.");
                return lines;
            }
            if (!CharacterServicesAccess.canBodyPartShop(player)) {
                lines.add("§cYou do not have permission for body parts.");
                return lines;
            }
            lines.add("§7Extra pieces for your KaiPath model preview - wings, horns, tails.");
            lines.add("§7A locked piece charges Ancient Coins when you turn it on.");
            lines.add("§7Shown on model previews; unlocked pieces stay in your collection.");
            return lines;
        }
        if (p.startsWith("bodycolors") || "bodycolors".equals(p)) {
            if (!CharacterServicesConfig.get().bodyPartShop.enabled) {
                lines.add("§cBody parts are turned off on this server.");
                return lines;
            }
            if (!CharacterServicesAccess.canBodyPartShop(player)) {
                lines.add("§cYou do not have permission for body parts.");
                return lines;
            }
            lines.add("§7Pick a color for each part channel - own, trim, horn, glow.");
            lines.add("§7Your choice follows you in every form.");
            lines.add("§7Reset returns a channel to your form's default colors.");
            return lines;
        }
        if ("reskin".equals(p)) {
            if (!CharacterServicesConfig.get().reskin.enabled) {
                lines.add("§cReskin is turned off on this server.");
                return lines;
            }
            if (!CharacterServicesAccess.canReskin(player)) {
                lines.add("§cYou do not have permission for reskin.");
                return lines;
            }
            lines.add("§7Change hair, colors, and other cosmetics.");
            lines.add("§7Use §fModel customization §7for cross-race ears, horns, and more.");
            lines.add("§7Reskin only changes how you look. To change your fighting class, use §fChange class §7instead.");
            lines.add("§7Level, stats, and race are unchanged.");
            lines.add(costLine(player, CharacterServicesSystem.reskinAmountDue(player)));
            lines.add(CharacterServicesSystem.reskinCostExplanation(player));
            lines.add("§8You'll be charged Ancient Coins when you confirm on the next screen.");
            lines.add(CharacterServicesSystem.cooldownLine(player, "reskin"));
            return lines;
        }
        if ("reskin_confirm".equals(p)) {
            if (!CharacterServicesConfig.get().reskin.enabled) {
                lines.add("§cReskin is turned off on this server.");
                return lines;
            }
            if (!CharacterServicesAccess.canReskin(player)) {
                lines.add("§cYou do not have permission for reskin.");
                return lines;
            }
            lines.add("§7Opens the appearance editor (hair, colors, body, gender).");
            lines.add("§7Level, stats, race, and class stay locked during the session.");
            lines.add(costLine(player, CharacterServicesSystem.reskinAmountDue(player)));
            lines.add(CharacterServicesSystem.reskinCostExplanation(player));
            lines.add("§8Coins are charged when you confirm. Closing the editor does not refund them.");
            lines.add(CharacterServicesSystem.cooldownLine(player, "reskin"));
            String block = CharacterServicesSystem.reskinPrecheck(player);
            if (block != null && !block.isBlank()) {
                lines.add(block);
            }
            return lines;
        }
        if (p.startsWith("race_pct:")) {
            String[] bits = p.split(":", 3);
            int pct = parsePct(bits.length > 2 ? bits[2] : "0");
            lines.addAll(CharacterServicesSystem.statPreviewLines(player, pct));
            lines.add(costLine(player, CharacterServicesSystem.raceAmountDue(player, pct)));
            if (pct > 0) {
                lines.add(CharacterServicesConfig.get().raceChange.levelCostMultiplier
                        ? "§8Price scales with your level"
                        : "§8Fixed price for everyone");
            } else {
                lines.add("§8Free - full wipe, then pick class and look");
            }
            return lines;
        }
        if (p.startsWith("race_confirm:")) {
            String[] bits = p.split(":", 3);
            int pct = parsePct(bits.length > 2 ? bits[2] : "0");
            String race = bits.length > 1 ? bits[1] : "?";
            // Cost stays near the top. Ultra also pins it; the chest GUI reads this list as-is.
            lines.add("§7You are becoming §f" + title(race) + "§7.");
            lines.add("§7Keeping §f" + pct + "% §7of eligible stats.");
            lines.add(costLine(player, CharacterServicesSystem.raceAmountDue(player, pct)));
            if (pct > 0) {
                lines.add(CharacterServicesConfig.get().raceChange.levelCostMultiplier
                        ? "§8Price scales with your level"
                        : "§8Fixed price for everyone");
            } else {
                lines.add("§8Free - full wipe, then pick class and look");
            }
            lines.add("§7Purchased looks carry over. Your class stays if the new race has it.");
            lines.add("§7If not, the editor opens so you can pick a class at no extra cost.");
            lines.addAll(CharacterServicesSystem.statPreviewLines(player, pct));
            lines.add("§8If the change fails, your coins are returned when possible.");
            lines.add("§cStaff cannot undo this for you.");
            return lines;
        }
        if (p.startsWith("class_confirm:")) {
            String cls = p.substring("class_confirm:".length());
            lines.add("§7New class: §f" + FightingClassLabels.display(cls));
            lines.add(costLine(player, CharacterServicesSystem.classAmountDue(player)));
            lines.add("§8If the change fails, your coins are returned when possible.");
            lines.add("§8Class skills and progression will reset.");
            return lines;
        }
        lines.add("§7Change race, class, look, or model pieces without starting over.");
        return lines;
    }

    public static List<String> raceCards(ServerPlayer player) {
        return CharacterServicesSystem.raceCards(player);
    }

    public static List<String> classCards(ServerPlayer player) {
        return CharacterServicesSystem.classCards(player);
    }

    public static List<String> headBoneCards(ServerPlayer player, int page) {
        return CosmeticHeadBoneService.cards(player, page);
    }

    public static List<String> bodyPartCards(ServerPlayer player, int page) {
        return CosmeticBodyPartService.cards(player, page);
    }

    public static String handleDo(ServerPlayer player, String action, String arg, String page) {
        if (player == null) {
            return "§cThis only works in-game - console can't use character services.";
        }
        String act = action == null ? "" : action.toLowerCase(Locale.ROOT).trim();
        String a = arg == null ? "" : arg.trim();
        if ("page".equals(act) || "refresh".equals(act)) {
            return "";
        }
        if ("nemesis_spawn_toggle".equals(act)) {
            return NemesisSpawnMode.cycle(player);
        }
        if ("race_confirm".equals(act)) {
            String[] bits = a.split(":", 2);
            if (bits.length < 2) {
                return "§cThat didn't work - try picking your race again.";
            }
            return CharacterServicesSystem.executeRaceChange(player, bits[0], parsePct(bits[1]));
        }
        if ("class_confirm".equals(act)) {
            if (a.isBlank()) {
                return "§cChoose a class first, then confirm.";
            }
            return CharacterServicesSystem.executeClassChange(player, a);
        }
        if ("reskin_confirm".equals(act)) {
            return CharacterServicesSystem.executeReskin(player);
        }
        if ("bone_preview".equals(act)) {
            return CosmeticHeadBoneService.previewBone(player, a);
        }
        if ("bone_restore".equals(act)) {
            return CosmeticHeadBoneService.restorePreview(player);
        }
        if ("bone_unlock".equals(act)) {
            return CosmeticHeadBoneService.executeUnlock(player, a);
        }
        if ("bone_equip".equals(act)) {
            return CosmeticHeadBoneService.executeEquip(player, a);
        }
        if ("bone_toggle".equals(act)) {
            return CosmeticHeadBoneService.executeToggle(player, a);
        }
        if ("bone_race_default".equals(act)) {
            return CosmeticHeadBoneService.executeRaceDefaultHeadBone(player);
        }
        if ("bone_unequip".equals(act)) {
            return CosmeticHeadBoneService.executeUnequipHeadBone(player);
        }
        if ("bodypart_unlock".equals(act)) {
            return CosmeticBodyPartService.executeUnlock(player, a);
        }
        if ("bodypart_toggle".equals(act)) {
            return CosmeticBodyPartService.executeToggle(player, a);
        }
        if ("bodypart_clear".equals(act)) {
            return CosmeticBodyPartService.executeClear(player);
        }
        if ("bodypart_color".equals(act)) {
            return CosmeticBodyPartService.executeColorPick(player, a);
        }
        if ("bodypart_color_reset".equals(act)) {
            return CosmeticBodyPartService.executeColorReset(player, a);
        }
        return "§cSomething went wrong. Try again, or ask staff if it keeps happening.";
    }

    private static void putWalletPlaceholders(ServerPlayer player, Map<String, String> out) {
        AncientCoinEconomy.migrateWalletToItems(player);
        out.put("ancient_coins", String.valueOf(AncientCoinEconomy.balance(player)));
        out.put("coins_copper", String.valueOf(AncientCoinEconomy.countOf(player, AncientCoinEconomy.CoinKind.COPPER)));
        out.put("coins_iron", String.valueOf(AncientCoinEconomy.countOf(player, AncientCoinEconomy.CoinKind.IRON)));
        out.put("coins_gold", String.valueOf(AncientCoinEconomy.countOf(player, AncientCoinEconomy.CoinKind.GOLD)));
        out.put("coins_emerald", String.valueOf(AncientCoinEconomy.countOf(player, AncientCoinEconomy.CoinKind.EMERALD)));
        out.put("coins_diamond", String.valueOf(AncientCoinEconomy.countOf(player, AncientCoinEconomy.CoinKind.DIAMOND)));
        out.put("coins_netherite", String.valueOf(AncientCoinEconomy.countOf(player, AncientCoinEconomy.CoinKind.NETHERITE)));
    }

    private static String costLine(ServerPlayer player, long copperCost) {
        if (copperCost <= 0L) {
            if (CharacterServicesAccess.bypassCost(player)) {
                if (DifficultyConfig.get().staffFreeAncientCoinCosts && StaffAccess.isStaff(player)) {
                    return "§7Cost §afree §8(staff - no charge)";
                }
                return "§7Cost §afree §8(no charge for your account)";
            }
            return "§7Cost §afree";
        }
        return "§7Cost §f" + CharacterServicesSystem.formatCost(copperCost) + " §7Ancient Coins";
    }

    /** Same percents {@link CharacterServicesSystem#executeRaceChange} will accept. */
    private static String keepStepsLabel() {
        int[] steps = CharacterServicesConfig.preservationSteps();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < steps.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(steps[i]);
        }
        return sb.toString();
    }

    private static String titleBone(String id) {
        if (id == null || id.isBlank()) {
            return "none";
        }
        return com.dbzlegacy.adaptivedifficulty.character.CosmeticHeadBoneService.wornLabel(id);
    }

    private static int parsePct(String raw) {
        try {
            return Integer.parseInt(raw == null ? "0" : raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String title(String id) {
        if (id == null || id.isBlank()) {
            return "?";
        }
        String labeled = FightingClassLabels.display(id);
        return "Not set".equals(labeled) ? "?" : labeled;
    }

    private static String resolveFightingClassRaw(ServerPlayer player) {
        String cls = DmzProgression.fightingClass(player);
        if (cls != null && !cls.isBlank()) {
            return cls;
        }
        try {
            var ch = DmzProgression.character(player);
            if (ch != null) {
                String raw = ch.getCharacterClass();
                if (raw != null && !raw.isBlank()) {
                    return raw;
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }
}
