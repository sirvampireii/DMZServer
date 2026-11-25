package com.dbzlegacy.adaptivedifficulty.gui;

import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
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
            lines.add(UltraStyle.DANGER + "Character Services are turned off on this server.");
            return lines;
        }
        if ("race".equals(p)) {
            if (!CharacterServicesConfig.get().raceChange.enabled) {
                lines.add(UltraStyle.DANGER + "Race change is turned off on this server.");
                return lines;
            }
            if (!CharacterServicesAccess.canRaceChange(player)) {
                lines.add(UltraStyle.DANGER + "You do not have permission for race change.");
                return lines;
            }
            // 4.7.84 (C-3): one line about the keep cost; "Listed cost" is gone (no cost is listed on the races).
            // What carries over moved to the confirm page, where the player decides.
            lines.add(UltraStyle.LABEL + "Pick a race, then choose how much of your stats to keep.");
            lines.add(raceKeepCostLine(player));
            lines.add(CharacterServicesSystem.cooldownLine(player, "race"));
            return lines;
        }
        if ("class".equals(p)) {
            if (!CharacterServicesConfig.get().classChange.enabled) {
                lines.add(UltraStyle.DANGER + "Class change is turned off on this server.");
                return lines;
            }
            if (!CharacterServicesAccess.canClassChange(player)) {
                lines.add(UltraStyle.DANGER + "You do not have permission for class change.");
                return lines;
            }
            lines.add(UltraStyle.LABEL + "Pick a new fighting class for your " + UltraStyle.VALUE + "current race" + UltraStyle.LABEL + ".");
            lines.add(CharacterServicesConfig.get().classChange.preserveBaseStats
                    ? UltraStyle.LABEL + "Only classes your race can use are listed. Your base stats stay; class skills and progression reset."
                    : UltraStyle.LABEL + "Only classes your race can use are listed. Class skills and progression reset.");
            lines.add(costLine(player, CharacterServicesSystem.classAmountDue(player)));
            lines.add(CharacterServicesSystem.cooldownLine(player, "class"));
            return lines;
        }
        if (p.startsWith("bones:") || "bones".equals(p)) {
            if (!CharacterServicesConfig.get().headBoneShop.enabled) {
                lines.add(UltraStyle.DANGER + "Model and colors is turned off on this server.");
                return lines;
            }
            if (!CharacterServicesAccess.canHeadBoneShop(player)) {
                lines.add(UltraStyle.DANGER + "Your rank can't use Model and colors yet. Ask staff which rank unlocks it.");
                return lines;
            }
            // 4.7.85 (X5): one model-page line each (44 characters beside the model), so the page fits 300 px.
            lines.add(UltraStyle.LABEL + "One piece per group; demon horns stack.");
            lines.add(UltraStyle.LABEL + "Locked parts ask you to confirm the price.");
            return lines;
        }
        if (p.startsWith("bodyparts:") || "bodyparts".equals(p)) {
            if (!CharacterServicesConfig.get().bodyPartShop.enabled) {
                lines.add(UltraStyle.DANGER + "Body parts are turned off on this server.");
                return lines;
            }
            if (!CharacterServicesAccess.canBodyPartShop(player)) {
                lines.add(UltraStyle.DANGER + "You do not have permission for body parts.");
                return lines;
            }
            lines.add(UltraStyle.LABEL + "Wear extra pieces like wings, horns and tails.");
            lines.add(UltraStyle.LABEL + "Pieces you unlock stay yours, even after you take them off.");
            return lines;
        }
        if (p.startsWith("bodycolors") || "bodycolors".equals(p)) {
            if (!CharacterServicesConfig.get().bodyPartShop.enabled) {
                lines.add(UltraStyle.DANGER + "Body parts are turned off on this server.");
                return lines;
            }
            if (!CharacterServicesAccess.canBodyPartShop(player)) {
                lines.add(UltraStyle.DANGER + "You do not have permission for body parts.");
                return lines;
            }
            lines.add(UltraStyle.LABEL + "Color your body parts: main color, trim, horns and glow.");
            lines.add(UltraStyle.LABEL + "Your choice follows you in every form.");
            lines.add(UltraStyle.LABEL + "Reset puts a color back to your form's own color.");
            return lines;
        }
        // 4.7.84 (C-4): Change skin is one page - what changes, the price, the warning and the pay button. The old
        // reskin_confirm page id reads the same lines.
        if ("reskin".equals(p) || "reskin_confirm".equals(p)) {
            if (!CharacterServicesConfig.get().reskin.enabled) {
                lines.add(UltraStyle.DANGER + "Changing your skin is turned off on this server.");
                return lines;
            }
            if (!CharacterServicesAccess.canReskin(player)) {
                lines.add(UltraStyle.DANGER + "Your rank can't change skin yet. Ask staff which rank unlocks it.");
                return lines;
            }
            long due = CharacterServicesSystem.reskinAmountDue(player);
            lines.add(UltraStyle.LABEL + "Change your hair, eyes, colors and body in the look editor.");
            lines.add(UltraStyle.LABEL + "Only your look changes. Level, stats, race and class stay the same.");
            lines.add(UltraStyle.LABEL + "For ears, horns or tails from other races, use " + UltraStyle.VALUE
                    + "Model and colors" + UltraStyle.LABEL + " or " + UltraStyle.VALUE + "Body parts" + UltraStyle.LABEL + ".");
            lines.add(costLine(player, due));
            lines.add(CharacterServicesSystem.reskinCostExplanation(player));
            if (due > 0L) {
                lines.add(UltraStyle.DIM + "Coins are taken when the editor opens. Closing it does not refund them.");
            }
            lines.add(CharacterServicesSystem.cooldownLine(player, "reskin"));
            return lines;
        }
        if (p.startsWith("race_pct:")) {
            String[] bits = p.split(":", 3);
            int pct = parsePct(bits.length > 2 ? bits[2] : "0");
            lines.addAll(CharacterServicesSystem.statPreviewLines(player, pct));
            lines.add(costLine(player, CharacterServicesSystem.raceAmountDue(player, pct)));
            if (pct > 0) {
                lines.add(CharacterServicesConfig.get().raceChange.levelCostMultiplier
                        ? UltraStyle.DIM + "Price scales with your level"
                        : UltraStyle.DIM + "Fixed price for everyone");
            } else {
                lines.add(UltraStyle.DIM + "Free: your stats start over, then you pick a class and look.");
            }
            return lines;
        }
        if (p.startsWith("race_confirm:")) {
            String[] bits = p.split(":", 3);
            int pct = parsePct(bits.length > 2 ? bits[2] : "0");
            String race = bits.length > 1 ? bits[1] : "?";
            // 4.7.84 (C-5): about ten lines - who you become, what you keep, the stats after, the cost, then what
            // carries over and the warning. The chest GUI reads this list as-is.
            lines.add(UltraStyle.LABEL + "You become: " + UltraStyle.VALUE + title(race));
            lines.add(UltraStyle.LABEL + "You keep: " + UltraStyle.VALUE + pct + "%" + UltraStyle.LABEL + " of your stats");
            lines.addAll(CharacterServicesSystem.statPreviewLines(player, pct));
            long due = CharacterServicesSystem.raceAmountDue(player, pct);
            if (pct <= 0) {
                lines.add(costLine(player, due) + UltraStyle.DIM + " - your stats start over.");
            } else {
                lines.add(costLine(player, due) + (due > 0L && CharacterServicesConfig.get().raceChange.levelCostMultiplier
                        ? UltraStyle.DIM + " (grows with your level)" : ""));
            }
            lines.add(UltraStyle.LABEL + "Purchased looks carry over. Your class stays, or you pick one free.");
            lines.add(CharacterServicesConfig.get().raceChange.keepSkillsOnRaceChange
                    ? UltraStyle.LABEL + "Skills stay. Forms your new race can't use reset."
                    : UltraStyle.LABEL + "Skills, techniques and form progress reset.");
            lines.add(UltraStyle.DANGER + "Staff can't undo this. If it fails, coins are refunded.");
            return lines;
        }
        if (p.startsWith("class_confirm:")) {
            String cls = p.substring("class_confirm:".length());
            lines.add(UltraStyle.LABEL + "New class: " + UltraStyle.VALUE + FightingClassLabels.display(cls));
            lines.add(costLine(player, CharacterServicesSystem.classAmountDue(player)));
            lines.add(UltraStyle.DIM + "Class skills and progression reset. If the change fails, your coins are refunded.");
            return lines;
        }
        lines.add(UltraStyle.LABEL + "Change race, class, look, or model pieces without starting over.");
        return lines;
    }

    /**
     * 4.7.84 (C-3): the one keep-cost line on the race list - "Keeping none is free; keeping all costs 4 Gold."
     * Same prices as the confirm page's keep buttons.
     */
    public static String raceKeepCostLine(ServerPlayer player) {
        long none = CharacterServicesSystem.raceAmountDue(player, 0);
        long all = CharacterServicesSystem.raceAmountDue(player, 100);
        String noneText = none <= 0L ? "is " + UltraStyle.VALUE + "free"
                : "costs " + UltraStyle.VALUE + AncientCoinEconomy.compactCost(none);
        String allText = all <= 0L ? "is " + UltraStyle.VALUE + "free too"
                : "costs " + UltraStyle.VALUE + AncientCoinEconomy.compactCost(all);
        return UltraStyle.LABEL + "Keeping none " + noneText + UltraStyle.LABEL + "; keeping all " + allText
                + UltraStyle.LABEL + ".";
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
            return UltraStyle.DANGER + "This only works in-game - console can't use character services.";
        }
        String act = action == null ? "" : action.toLowerCase(Locale.ROOT).trim();
        String a = arg == null ? "" : arg.trim();
        if ("page".equals(act) || "refresh".equals(act)) {
            return "";
        }
        if ("race_confirm".equals(act)) {
            String[] bits = a.split(":", 2);
            if (bits.length < 2) {
                return UltraStyle.DANGER + "That didn't work - try picking your race again.";
            }
            return CharacterServicesSystem.executeRaceChange(player, bits[0], parsePct(bits[1]));
        }
        if ("class_confirm".equals(act)) {
            if (a.isBlank()) {
                return UltraStyle.DANGER + "Choose a class first, then confirm.";
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
        return UltraStyle.DANGER + "Something went wrong. Try again, or ask staff if it keeps happening.";
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

    /**
     * Android tab cost line: the level-priced conversion after the existing staff/bypass waiver,
     * worded like the other character-service cost lines (free shows its existing reason).
     */
    public static String androidCostLine(ServerPlayer player) {
        return costLine(player, CharacterServicesSystem.androidAmountDue(player));
    }

    private static String costLine(ServerPlayer player, long copperCost) {
        if (copperCost <= 0L) {
            if (CharacterServicesAccess.bypassCost(player)) {
                if (DifficultyConfig.get().staffFreeAncientCoinCosts && StaffAccess.isStaff(player)) {
                    return UltraStyle.LABEL + "Cost " + UltraStyle.GREEN + "free " + UltraStyle.DIM + "(staff - no charge)";
                }
                return UltraStyle.LABEL + "Cost " + UltraStyle.GREEN + "free " + UltraStyle.DIM + "(no charge for your account)";
            }
            return UltraStyle.LABEL + "Cost " + UltraStyle.GREEN + "free";
        }
        // formatCost already ends in "Ancient" ("16x Gold Ancient"): add "Coins", not a second "Ancient" (4.7.81).
        String cost = CharacterServicesSystem.formatCost(copperCost);
        String unit = cost.endsWith(" Ancient") ? "Coins" : "Ancient Coins";
        return UltraStyle.LABEL + "Cost " + UltraStyle.VALUE + cost + " " + UltraStyle.LABEL + unit;
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
