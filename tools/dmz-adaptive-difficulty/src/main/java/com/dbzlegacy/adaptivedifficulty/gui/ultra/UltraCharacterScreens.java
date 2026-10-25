package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.character.CharacterServicesAccess;
import com.dbzlegacy.adaptivedifficulty.character.CharacterServicesConfig;
import com.dbzlegacy.adaptivedifficulty.character.CharacterServicesSystem;
import com.dbzlegacy.adaptivedifficulty.character.CosmeticBodyPartService;
import com.dbzlegacy.adaptivedifficulty.character.CosmeticHeadBoneService;
import com.dbzlegacy.adaptivedifficulty.character.HeadPartPieces;
import com.dbzlegacy.adaptivedifficulty.gui.CharacterServicesGuiApi;
import com.dbzlegacy.adaptivedifficulty.gui.RivalGuiApi;
import com.dbzlegacy.adaptivedifficulty.progression.race.AndroidConversion;
import com.dbzlegacy.adaptivedifficulty.quest.SagaResetConfig;
import com.dbzlegacy.adaptivedifficulty.quest.SagaResetService;
import com.dbzlegacy.adaptivedifficulty.quest.SagaResetService.Offer;
import com.dbzlegacy.adaptivedifficulty.rival.NemesisSpawnMode;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Character Services - Home / Saga Reset / Android Removal (+ race, class, model pages). */
public final class UltraCharacterScreens {
    private static final int ANDROID_PICK_PER_PAGE = 8;

    private UltraCharacterScreens() {}

    public static void open(ServerPlayer player, String page) {
        if (player == null) {
            return;
        }
        String p = page == null || page.isBlank() ? "main" : page.trim();
        String lower = p.toLowerCase(Locale.ROOT);
        if (lower.startsWith("saga_confirm:")) {
            openSagaConfirm(player, p.substring("saga_confirm:".length()).trim());
            return;
        }
        if (lower.startsWith("race_confirm:")) {
            openRaceConfirm(player, p.substring("race_confirm:".length()).trim());
            return;
        }
        if (lower.startsWith("class_confirm:")) {
            openClassConfirm(player, p.substring("class_confirm:".length()).trim());
            return;
        }
        if (lower.startsWith("bones:") || "bones".equals(lower)) {
            openBones(player, parseBonePage(lower));
            return;
        }
        if (lower.startsWith("bodyparts:") || "bodyparts".equals(lower)) {
            openBodyParts(player, parseBodyPartPage(lower));
            return;
        }
        if (lower.startsWith("bodycolors") || "bodycolors".equals(lower)) {
            openBodyColors(player, lower);
            return;
        }
        if (isStaffAndroidPage(lower)) {
            if (!staffMayEditOthers(player)) {
                openAndroidRemove(player);
                return;
            }
            if (lower.startsWith("android_confirm:")) {
                openAndroidConfirm(player, p.substring("android_confirm:".length()).trim());
                return;
            }
            if (lower.startsWith("android_target:")) {
                openAndroidTarget(player, p.substring("android_target:".length()).trim());
                return;
            }
            openAndroidStaff(player, parseStaffListPage(p));
            return;
        }
        if (lower.startsWith("race:") || "race".equals(lower)) {
            openRace(player, parseRaceListPage(lower));
            return;
        }
        if (lower.startsWith("class:") || "class".equals(lower)) {
            openClass(player, parseListPage(lower, "class"));
            return;
        }
        if (lower.startsWith("reset:")
                || lower.startsWith("saga:")
                || "reset".equals(lower)
                || "saga".equals(lower)
                || "sagareset".equals(lower)
                || "saga_reset".equals(lower)) {
            openSagaReset(player, parseListPage(lower, "reset", "saga", "sagareset", "saga_reset"));
            return;
        }
        switch (lower) {
            case "android_remove", "android", "androidremove", "remove_android" ->
                    openAndroidRemove(player);
            case "reskin" -> openReskin(player);
            case "reskin_confirm" -> openReskinConfirm(player);
            default -> openHome(player);
        }
    }

    private static UltraScreenSpec base(ServerPlayer player, String page, String tab, boolean preview) {
        // y=60 sits below the tab button row (TAB_ROW_Y + BTN_H); y=36 clipped the head.
        UltraScreenSpec spec = UltraScreenSpec.of("character")
                .title("Character Services")
                .page(page)
                .activeTab(tab)
                // Exact short labels only — UltraScreenSelfTest.verifyMenuLinks requires
                // buttonsJson "label":"Home"|"Saga"|"Android" with no section-sign/color prefixes.
                .tabs("home|Home", "saga|Saga", "android|Android")
                .lineGap(2)
                .bodyPad(4);
        // Preview only on Home (and model customize via sub) — not on Saga/Android dumps.
        if (preview) {
            spec.playerModel(player, 248, 60, 86, 120, 0.9f, true);
        }
        return spec;
    }

    /** Subpage: no tab row — dual footer (Back + Main menu). */
    private static UltraScreenSpec sub(ServerPlayer player, String page, boolean preview) {
        UltraScreenSpec spec = UltraScreenSpec.of("character")
                .title("Character Services")
                .page(page)
                .lineGap(2)
                .bodyPad(4);
        // Preview only on model customize (bones). Race/class/reskin stay text-only.
        if (preview) {
            spec.playerModel(player, 248, 60, 86, 120, 0.9f, true);
        }
        return spec;
    }


    /** Overview + look tools - Change Race / Class / Model / Reskin on Home. */
    private static void openHome(ServerPlayer player) {
        Map<String, String> ph = CharacterServicesGuiApi.placeholders(player);
        boolean canRace = "true".equals(ph.get("can_race_change"));
        boolean canClass = "true".equals(ph.get("can_class_change"));
        boolean canReskin = "true".equals(ph.get("can_reskin"));
        boolean canBones = "true".equals(ph.get("can_head_bones"));
        boolean monkeyFormModel = "true".equals(ph.get("head_bones_form_model_blocked"));
        int nemesis = NemesisSpawnMode.mode(player);
        UltraScreenSpec spec = base(player, "main", "home", true)
                .labelValue("Race", ph.getOrDefault("current_race", "-"))
                .labelValue("Class", ph.getOrDefault("current_class", "-"))
                .labelValue("Nemesis spawn", NemesisSpawnMode.stateName(nemesis))
                // Plain-text policy line for the current mode (OFF keeps the menu summon).
                .line(UltraStyle.dim(NemesisSpawnMode.description(nemesis)));
        if (monkeyFormModel) {
            spec.line(UltraStyle.LABEL
                    + "Not available for Monkey - this race uses a form model.");
        }
        spec.buttonsAfterLines();

        if (canRace) {
            spec.buttonLeft("page:race:0", UltraStyle.confirm("Change race"), UltraStyle.STYLE_CONFIRM);
        } else {
            spec.buttonLeft("page:race:0", UltraStyle.dim("Change race"), UltraStyle.STYLE_DIM);
        }
        if (canClass) {
            spec.buttonRight("page:class:0", UltraStyle.confirm("Change class"), UltraStyle.STYLE_CONFIRM);
        } else {
            spec.buttonRight("page:class:0", UltraStyle.dim("Change class"), UltraStyle.STYLE_DIM);
        }
        spec.rowGap();

        if (canBones) {
            spec.buttonLeft(
                    "page:bones:0",
                    UltraStyle.confirm("Customize model"),
                    UltraStyle.STYLE_CONFIRM);
        } else {
            spec.buttonLeft(
                    "page:bones:0", UltraStyle.dim("Customize model"), UltraStyle.STYLE_DIM);
        }
        if (canReskin) {
            spec.buttonRight("page:reskin", UltraStyle.confirm("Reskin"), UltraStyle.STYLE_CONFIRM);
        } else {
            spec.buttonRight("page:reskin", UltraStyle.dim("Reskin"), UltraStyle.STYLE_DIM);
        }
        spec.rowGap();

        // Personal spawn preference (moved from Rival > Settings): ON (gold) -> manual -> OFF (dim).
        spec.button(
                        "do:nemesis_spawn_toggle",
                        nemesis == NemesisSpawnMode.AUTO
                                ? UltraStyle.confirm("Nemesis spawn - ON")
                                : nemesis == NemesisSpawnMode.MANUAL
                                        ? UltraStyle.LABEL + "Nemesis spawn - manual"
                                        : UltraStyle.dim("Nemesis spawn - OFF"),
                        nemesis == NemesisSpawnMode.AUTO
                                ? UltraStyle.STYLE_CONFIRM
                                : nemesis == NemesisSpawnMode.MANUAL
                                        ? UltraStyle.STYLE_NORMAL
                                        : UltraStyle.STYLE_DIM)
                .rowGap();

        spec.footerBackToMain();
        spec.send(player);
    }

    /** Races per Ultra page (two columns). Hard-capping without paging hid later races. */
    private static final int RACES_PER_PAGE = 10;
    /** Saga reset / class pickers: two-up rows; page so discovery growth never truncates. */
    private static final int DISCOVERY_PER_PAGE = 8;

    private static void openRace(ServerPlayer player, int page) {
        List<String> cards = CharacterServicesGuiApi.raceCards(player);
        int total = cards == null ? 0 : cards.size();
        int pages = Math.max(1, (total + RACES_PER_PAGE - 1) / RACES_PER_PAGE);
        int pg = Math.min(pages - 1, Math.max(0, page));
        int from = pg * RACES_PER_PAGE;
        int to = Math.min(total, from + RACES_PER_PAGE);
        long keep100Cost = CharacterServicesSystem.raceAmountDue(player, 100);
        String keep100Text = keep100Cost <= 0L ? "Free" : CharacterServicesSystem.formatCost(keep100Cost);
        UltraScreenSpec spec = sub(player, "race:" + pg, false)
                .line(UltraStyle.LABEL + "Choose your new race.")
                .labelValue("Races", total + (pages > 1 ? (" page " + (pg + 1) + "/" + pages) : ""))
                .labelValue("Keep 0%", "Free");
        // Full copy wraps and scrolls. The keep-100 price is pinned so it stays visible.
        appendCompactLines(spec, CharacterServicesGuiApi.linesForPage(player, "race"), true);
        spec.line(UltraStyle.dim(
                "Keeping everything costs " + keep100Text
                        + " - exact cost shown on the next page."));
        spec.buttonsAfterLines();
        int placed = 0;
        for (int i = from; i < to; i++) {
            String card = cards.get(i);
            if (card == null || card.isBlank()) {
                continue;
            }
            String[] p = card.split("\t", -1);
            String id = p.length > 0 ? p[0].trim() : "";
            String name = p.length > 1 ? p[1].trim() : id;
            boolean current = p.length > 2 && "1".equals(p[2]);
            if (id.isBlank()) {
                continue;
            }
            String action = "page:race_confirm:" + id + ":0";
            String label;
            String style;
            if (current) {
                String you = name + " (you)";
                label = UltraStyle.dim(UltraStyle.fitsButton(you) ? you : UltraStyle.buttonText(name));
                style = UltraStyle.STYLE_DIM;
            } else {
                label = UltraStyle.confirm(UltraStyle.buttonText(name));
                style = UltraStyle.STYLE_CONFIRM;
            }
            if (placed % 2 == 0) {
                spec.buttonLeft(action, label, style);
            } else {
                spec.buttonRight(action, label, style);
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        if (placed == 0) {
            spec.line(UltraStyle.dim("No races are available right now."));
        }
        if (pg > 0) {
            spec.buttonLeft(
                    "page:race:" + (pg - 1), UltraStyle.dim("Prev"), UltraStyle.STYLE_DIM);
        }
        if (pg + 1 < pages) {
            if (pg > 0) {
                spec.buttonRight(
                        "page:race:" + (pg + 1), UltraStyle.LABEL + "Next", UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonLeft(
                        "page:race:" + (pg + 1), UltraStyle.LABEL + "Next", UltraStyle.STYLE_NORMAL);
            }
            spec.rowGap();
        } else if (pg > 0) {
            spec.rowGap();
        }
        // footerBackHome only - a second tab:home Back stacked a duplicate button.
        spec.footerBackHome("Character Home");
        spec.send(player);
    }

    private static int parseRaceListPage(String page) {
        return parseListPage(page, "race");
    }

    /** Parse {@code prefix} or {@code prefix:N} page index (0-based). */
    private static int parseListPage(String page, String... prefixes) {
        if (page == null || page.isBlank()) {
            return 0;
        }
        String raw = page.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (prefixes != null) {
            for (String prefix : prefixes) {
                if (prefix != null && lower.equals(prefix.toLowerCase(Locale.ROOT))) {
                    return 0;
                }
            }
        }
        int colon = raw.indexOf(':');
        if (colon < 0 || colon + 1 >= raw.length()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.substring(colon + 1).trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static void openRaceConfirm(ServerPlayer player, String raceAndPct) {
        String[] bits = raceAndPct.split(":", 2);
        String raceId = bits[0] == null ? "" : bits[0].trim();
        int selected = 0;
        if (bits.length > 1) {
            try {
                selected = Integer.parseInt(bits[1].trim());
            } catch (NumberFormatException ignored) {
                selected = 0;
            }
        }
        String confirmArg = raceId + ":" + selected;
        long due = CharacterServicesSystem.raceAmountDue(player, selected);
        String costText = due <= 0L ? "Free" : CharacterServicesSystem.formatCost(due);
        String raceLabel = CharacterServicesSystem.raceDisplayName(raceId);
        UltraScreenSpec spec = sub(player, "race_confirm:" + confirmArg, false)
                .line(UltraStyle.LABEL + "Confirm race change")
                .labelValue("Race", raceLabel)
                .labelValue("Keep", selected + "%");
        // Every confirm line is sent. Long lines wrap; overflow scrolls. Cost is pinned
        // under the body so it is not buried in the stat preview.
        appendCompactLines(
                spec, CharacterServicesGuiApi.linesForPage(player, "race_confirm:" + confirmArg), true);
        spec.line(UltraStyle.labelValue("Cost", costText));
        spec.buttonsAfterLines();
        int[] keepSteps = CharacterServicesConfig.preservationSteps();
        int placed = 0;
        for (int keep : keepSteps) {
            String action = "page:race_confirm:" + raceId + ":" + keep;
            long keepDue = CharacterServicesSystem.raceAmountDue(player, keep);
            String keepPrice = keepDue <= 0L
                    ? "free"
                    : com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.compactCost(keepDue);
            String keepLabel = keep + "% " + keepPrice;
            if (!UltraStyle.fitsButton(keepLabel)) {
                keepLabel = "Keep " + keep + "%";
            }
            String label = keep == selected
                    ? UltraStyle.confirm(keepLabel)
                    : UltraStyle.LABEL + keepLabel;
            String style = keep == selected ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_NORMAL;
            if (placed % 2 == 0) {
                spec.buttonLeft(action, label, style);
            } else {
                spec.buttonRight(action, label, style);
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        String confirmLabel = due <= 0L
                ? "Confirm"
                : "Pay " + com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.compactCost(due);
        if (!UltraStyle.fitsButton(confirmLabel)) {
            confirmLabel = "Confirm";
        }
        spec.button(
                        "do:race_confirm:" + confirmArg,
                        UltraStyle.confirm(confirmLabel),
                        UltraStyle.STYLE_CONFIRM)
                .rowGap();
        spec.footerBack("page:race:0", "Race list");
        spec.send(player);
    }

    private static void openClass(ServerPlayer player, int page) {
        List<String> cards = CharacterServicesGuiApi.classCards(player);
        int total = cards == null ? 0 : cards.size();
        int pages = Math.max(1, (total + DISCOVERY_PER_PAGE - 1) / DISCOVERY_PER_PAGE);
        int pg = Math.min(pages - 1, Math.max(0, page));
        int from = pg * DISCOVERY_PER_PAGE;
        int to = Math.min(total, from + DISCOVERY_PER_PAGE);
        long classDue = CharacterServicesSystem.classAmountDue(player);
        String classCost = classDue <= 0L ? "Free" : CharacterServicesSystem.formatCost(classDue);
        UltraScreenSpec spec = sub(player, "class:" + pg, false)
                .line(UltraStyle.LABEL + "Pick a class for your current race.")
                .labelValue(
                        "Classes",
                        total + (pages > 1 ? (" page " + (pg + 1) + "/" + pages) : ""));
        // Full class copy wraps and scrolls. Cost is pinned under the body.
        appendCompactLines(spec, CharacterServicesGuiApi.linesForPage(player, "class"), true);
        spec.line(UltraStyle.labelValue("Cost", classCost));
        spec.buttonsAfterLines();
        int placed = 0;
        for (int i = from; i < to; i++) {
            String card = cards.get(i);
            if (card == null || card.isBlank()) {
                continue;
            }
            String[] p = card.split("\t", -1);
            String id = p.length > 0 ? p[0].trim() : "";
            String name = p.length > 1 ? p[1].trim() : id;
            boolean current = p.length > 2 && "1".equals(p[2]);
            if (id.isBlank()) {
                continue;
            }
            String action = "page:class_confirm:" + id;
            String label;
            String style;
            if (current) {
                String you = name + " (you)";
                label = UltraStyle.dim(UltraStyle.fitsButton(you) ? you : UltraStyle.buttonText(name));
                style = UltraStyle.STYLE_DIM;
            } else {
                label = UltraStyle.confirm(UltraStyle.buttonText(name));
                style = UltraStyle.STYLE_CONFIRM;
            }
            if (placed % 2 == 0) {
                spec.buttonLeft(action, label, style);
            } else {
                spec.buttonRight(action, label, style);
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        if (placed == 0) {
            spec.line(UltraStyle.dim("No classes are available for your race."));
        }
        if (pg > 0) {
            spec.buttonLeft("page:class:" + (pg - 1), UltraStyle.dim("Prev"), UltraStyle.STYLE_DIM);
        }
        if (pg + 1 < pages) {
            if (pg > 0) {
                spec.buttonRight(
                        "page:class:" + (pg + 1), UltraStyle.LABEL + "Next", UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonLeft(
                        "page:class:" + (pg + 1), UltraStyle.LABEL + "Next", UltraStyle.STYLE_NORMAL);
            }
            spec.rowGap();
        } else if (pg > 0) {
            spec.rowGap();
        }
        // footerBackHome only - avoid duplicate Back with tab:home.
        spec.footerBackHome("Character Home");
        spec.send(player);
    }

    private static void openClassConfirm(ServerPlayer player, String classId) {
        String id = classId == null ? "" : classId.trim();
        long classDue = CharacterServicesSystem.classAmountDue(player);
        String classCost = classDue <= 0L ? "Free" : CharacterServicesSystem.formatCost(classDue);
        UltraScreenSpec spec = sub(player, "class_confirm:" + id, false)
                .line(UltraStyle.LABEL + "Confirm class change");
        appendCompactLines(
                spec, CharacterServicesGuiApi.linesForPage(player, "class_confirm:" + id), true);
        spec.line(UltraStyle.labelValue("Cost", classCost));
        spec.buttonsAfterLines();
        spec.button(
                        "do:class_confirm:" + id,
                        UltraStyle.confirm("Confirm & pay"),
                        UltraStyle.STYLE_CONFIRM)
                .rowGap();
        spec.footerBack("page:class:0", "Class list");
        spec.send(player);
    }

    private static void openReskin(ServerPlayer player) {
        UltraScreenSpec spec = sub(player, "reskin", false)
                .line(UltraStyle.LABEL + "Change hair, colors, and other cosmetics.");
        appendCompactLines(spec, CharacterServicesGuiApi.linesForPage(player, "reskin"));
        spec.buttonsAfterLines();
        spec.button("page:reskin_confirm", UltraStyle.confirm("Review cost"), UltraStyle.STYLE_CONFIRM)
                .rowGap();
        spec.footerBackHome("Character Home");
        spec.send(player);
    }

    private static void openReskinConfirm(ServerPlayer player) {
        UltraScreenSpec spec = sub(player, "reskin_confirm", false)
                .line(UltraStyle.LABEL + "Confirm reskin");
        appendCompactLines(spec, CharacterServicesGuiApi.linesForPage(player, "reskin_confirm"));
        String pre = com.dbzlegacy.adaptivedifficulty.character.CharacterServicesSystem.reskinPrecheck(player);
        boolean ready = pre == null || pre.isBlank();
        if (!ready) {
            spec.line(UltraStyle.danger(UltraStyle.plain(pre)));
        }
        spec.buttonsAfterLines();
        if (ready) {
            spec.button(
                            "do:reskin_confirm",
                            UltraStyle.confirm("Confirm & pay"),
                            UltraStyle.STYLE_CONFIRM)
                    .rowGap();
        }
        spec.footerBack("page:reskin", "Reskin");
        spec.send(player);
    }

    private static void openBones(ServerPlayer player, int page) {
        if (CosmeticHeadBoneService.isFormModelRace(player)) {
            UltraScreenSpec spec = sub(player, "bones:0", true)
                    .line(UltraStyle.LABEL + "Model customization")
                    .line(UltraStyle.LABEL
                            + "Not available for Monkey - this race uses a form model.")
                    .buttonsAfterLines();
            spec.footerBackHome("Character Home");
            spec.send(player);
            return;
        }
        int pages = Math.max(1, CosmeticHeadBoneService.pageCount());
        int pg = Math.min(pages - 1, Math.max(0, page));
        String worn = CosmeticHeadBoneService.wornLabel(CosmeticHeadBoneService.activeBone(player));
        int wornCount = HeadPartPieces.fragments(CosmeticHeadBoneService.activeBone(player)).size();
        if (wornCount > 2) {
            worn = wornCount + " parts on";
        }
        UltraScreenSpec spec = sub(player, "bones:" + pg, true)
                .labelValue(HeadPartPieces.pageTitle(pg), "On: " + UltraStyle.plain(worn));
        appendCompactLines(spec, CharacterServicesGuiApi.linesForPage(player, "bones:" + pg));
        spec.buttonsAfterLines();
        // The catalog page is the render page (6 head / 8 horn parts): draw every card it owns,
        // like the Body Parts Shop, so no part is ever dropped off the end.
        List<String> cards = CharacterServicesGuiApi.headBoneCards(player, pg);
        int placed = 0;
        for (String card : cards) {
            if (card == null || card.isBlank()) {
                continue;
            }
            String[] p = card.split("\t", -1);
            String id = p.length > 0 ? p[0].trim() : "";
            String name = p.length > 1 ? p[1].trim() : id;
            String state = p.length > 2 ? p[2].trim() : "";
            if (id.isBlank()) {
                continue;
            }
            String action = "do:bone_toggle:" + id;
            String label;
            String style;
            if ("E".equals(state)) {
                String on = name + " on";
                label = UltraStyle.confirm(UltraStyle.fitsButton(on) ? on : UltraStyle.buttonText(name));
                style = UltraStyle.STYLE_CONFIRM;
            } else if ("L".equals(state)) {
                String locked = name + " lock";
                label = UltraStyle.dim(UltraStyle.fitsButton(locked) ? locked : UltraStyle.buttonText(name));
                style = UltraStyle.STYLE_DIM;
                action = "do:bone_unlock:" + id;
            } else {
                label = UltraStyle.LABEL + UltraStyle.buttonText(name);
                style = UltraStyle.STYLE_NORMAL;
            }
            if (placed % 2 == 0) {
                spec.buttonLeft(action, label, style);
            } else {
                spec.buttonRight(action, label, style);
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        spec.buttonLeft(
                        "do:bone_race_default",
                        UltraStyle.confirm("Race default"),
                        UltraStyle.STYLE_CONFIRM)
                .buttonRight(
                        "do:bone_unequip",
                        UltraStyle.danger("Clear parts"),
                        UltraStyle.STYLE_DANGER)
                .rowGap();
        if (CharacterServicesConfig.get().bodyPartShop.enabled
                && CharacterServicesAccess.canBodyPartShop(player)) {
            spec.buttonLeft(
                            "page:bodyparts:0",
                            UltraStyle.LABEL + "Body parts",
                            UltraStyle.STYLE_NORMAL)
                    .rowGap();
        }
        if (pg > 0) {
            spec.buttonLeft(
                    "page:bones:" + (pg - 1), UltraStyle.dim("Prev"), UltraStyle.STYLE_DIM);
        }
        if (pg + 1 < pages) {
            if (pg > 0) {
                spec.buttonRight(
                        "page:bones:" + (pg + 1), UltraStyle.LABEL + "Next", UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonLeft(
                        "page:bones:" + (pg + 1), UltraStyle.LABEL + "Next", UltraStyle.STYLE_NORMAL);
            }
            spec.rowGap();
        } else if (pg > 0) {
            spec.rowGap();
        }
        spec.footerBackHome("Character Home");
        spec.send(player);
    }

    private static int parseBonePage(String page) {
        if (page == null || page.isBlank() || "bones".equals(page)) {
            return 0;
        }
        if (page.startsWith("bones:")) {
            try {
                return Integer.parseInt(page.substring("bones:".length()).trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static int parseBodyPartPage(String page) {
        if (page == null || page.isBlank() || "bodyparts".equals(page)) {
            return 0;
        }
        if (page.startsWith("bodyparts:")) {
            try {
                return Integer.parseInt(page.substring("bodyparts:".length()).trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Body Parts Shop - custom DMZUltra FormParts pieces the player wears on the KaiPath
     * model preview. Reached from model customize; pieces ride {@code playerModel.parts.bodyParts}
     * (KaiPath 1.3.29 attaches them via BodyPartPreview).
     */
    private static void openBodyParts(ServerPlayer player, int page) {
        if (CosmeticHeadBoneService.isFormModelRace(player)) {
            UltraScreenSpec spec = sub(player, "bodyparts:0", true)
                    .line(UltraStyle.LABEL + "Model customization")
                    .line(UltraStyle.LABEL
                            + "Not available for Monkey - this race uses a form model.")
                    .buttonsAfterLines();
            spec.footerBack("page:bones:0", "Model");
            spec.send(player);
            return;
        }
        int pages = CosmeticBodyPartService.pageCount();
        int pg = Math.min(pages - 1, Math.max(0, page));
        UltraScreenSpec spec = sub(player, "bodyparts:" + pg, true)
                .labelValue(CosmeticBodyPartService.pageTitle(pg),
                        "On: " + UltraStyle.plain(CosmeticBodyPartService.wornLabel(player)));
        appendCompactLines(spec, CharacterServicesGuiApi.linesForPage(player, "bodyparts:" + pg));
        spec.buttonsAfterLines();
        List<String> cards = CharacterServicesGuiApi.bodyPartCards(player, pg);
        int placed = 0;
        for (String card : cards) {
            if (card == null || card.isBlank()) {
                continue;
            }
            String[] p = card.split("\t", -1);
            String id = p.length > 0 ? p[0].trim() : "";
            String name = p.length > 1 ? p[1].trim() : id;
            String state = p.length > 2 ? p[2].trim() : "";
            if (id.isBlank()) {
                continue;
            }
            String action = "do:bodypart_toggle:" + id;
            String label;
            String style;
            if ("E".equals(state)) {
                String on = name + " on";
                label = UltraStyle.confirm(UltraStyle.fitsButton(on) ? on : UltraStyle.buttonText(name));
                style = UltraStyle.STYLE_CONFIRM;
            } else if ("L".equals(state)) {
                String locked = name + " lock";
                label = UltraStyle.dim(UltraStyle.fitsButton(locked) ? locked : UltraStyle.buttonText(name));
                style = UltraStyle.STYLE_DIM;
                action = "do:bodypart_unlock:" + id;
            } else {
                label = UltraStyle.LABEL + UltraStyle.buttonText(name);
                style = UltraStyle.STYLE_NORMAL;
            }
            if (placed % 2 == 0) {
                spec.buttonLeft(action, label, style);
            } else {
                spec.buttonRight(action, label, style);
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        spec.buttonLeft(
                        "do:bodypart_clear",
                        UltraStyle.danger("Clear parts"),
                        UltraStyle.STYLE_DANGER);
        spec.buttonRight(
                        "page:bodycolors", UltraStyle.LABEL + "Part colors", UltraStyle.STYLE_NORMAL);
        spec.rowGap();
        if (pg > 0) {
            spec.buttonLeft(
                    "page:bodyparts:" + (pg - 1), UltraStyle.dim("Prev"), UltraStyle.STYLE_DIM);
        }
        if (pg + 1 < pages) {
            if (pg > 0) {
                spec.buttonRight(
                        "page:bodyparts:" + (pg + 1), UltraStyle.LABEL + "Next", UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonLeft(
                        "page:bodyparts:" + (pg + 1), UltraStyle.LABEL + "Next", UltraStyle.STYLE_NORMAL);
            }
            spec.rowGap();
        } else if (pg > 0) {
            spec.rowGap();
        }
        spec.footerBack("page:bones:0", "Model");
        spec.send(player);
    }

    /**
     * Part colors - per-channel overrides for the four DMZUltra part channels
     * (own/trim/horn/light). Each row opens that channel's swatch page; picking a swatch
     * saves immediately and re-syncs the model (menu preview + world).
     */
    private static void openBodyColors(ServerPlayer player, String pageId) {
        String[] bits = pageId == null ? new String[0] : pageId.split(":", -1);
        if (bits.length >= 2 && CosmeticBodyPartService.isColorChannel(bits[1])) {
            int swatchPage = 0;
            if (bits.length > 2) {
                try {
                    swatchPage = Integer.parseInt(bits[2].trim());
                } catch (NumberFormatException ignored) {
                    swatchPage = 0;
                }
            }
            openBodyColorSwatches(player, bits[1].trim().toLowerCase(Locale.ROOT), swatchPage);
            return;
        }
        UltraScreenSpec spec = sub(player, "bodycolors", true);
        appendCompactLines(spec, CharacterServicesGuiApi.linesForPage(player, "bodycolors"));
        spec.buttonsAfterLines();
        for (String channel : CosmeticBodyPartService.COLOR_CHANNELS) {
            String value = CosmeticBodyPartService.partColorValue(player, channel);
            String action = "page:bodycolors:" + channel + ":0";
            spec.buttonLeft(
                    action,
                    UltraStyle.LABEL
                            + UltraStyle.buttonText(CosmeticBodyPartService.colorChannelLabel(channel)),
                    UltraStyle.STYLE_NORMAL);
            spec.buttonRight(
                    action,
                    UltraStyle.LABEL + UltraStyle.buttonText(value),
                    "Default".equals(value) ? UltraStyle.STYLE_DIM : UltraStyle.STYLE_NORMAL);
            spec.rowGap();
        }
        spec.footerBack("page:bodyparts:0", "Body parts");
        spec.send(player);
    }

    /** Preset swatch grid for one channel, laid out like the shop's card pages. */
    private static void openBodyColorSwatches(ServerPlayer player, String channel, int page) {
        int pages = CosmeticBodyPartService.colorPageCount();
        int pg = Math.min(pages - 1, Math.max(0, page));
        UltraScreenSpec spec =
                sub(player, "bodycolors:" + channel + ":" + pg, true)
                        .labelValue(
                                CosmeticBodyPartService.colorChannelLabel(channel),
                                CosmeticBodyPartService.partColorValue(player, channel));
        spec.line(UltraStyle.LABEL + "Pick a swatch. It saves immediately.");
        spec.buttonsAfterLines();
        int placed = 0;
        for (CosmeticBodyPartService.Preset preset : CosmeticBodyPartService.colorPresets(pg)) {
            String action =
                    "do:bodypart_color:" + channel + ":" + Integer.toHexString(preset.rgb());
            String label = UltraStyle.LABEL + UltraStyle.buttonText(preset.name());
            if (placed % 2 == 0) {
                spec.buttonLeft(action, label, UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonRight(action, label, UltraStyle.STYLE_NORMAL);
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        spec.buttonLeft(
                        "do:bodypart_color_reset:" + channel,
                        UltraStyle.confirm("Reset to default"),
                        UltraStyle.STYLE_CONFIRM)
                .rowGap();
        if (pg > 0) {
            spec.buttonLeft(
                    "page:bodycolors:" + channel + ":" + (pg - 1),
                    UltraStyle.dim("Prev"),
                    UltraStyle.STYLE_DIM);
        }
        if (pg + 1 < pages) {
            if (pg > 0) {
                spec.buttonRight(
                        "page:bodycolors:" + channel + ":" + (pg + 1),
                        UltraStyle.LABEL + "Next",
                        UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonLeft(
                        "page:bodycolors:" + channel + ":" + (pg + 1),
                        UltraStyle.LABEL + "Next",
                        UltraStyle.STYLE_NORMAL);
            }
            spec.rowGap();
        } else if (pg > 0) {
            spec.rowGap();
        }
        spec.footerBack("page:bodycolors", "Part colors");
        spec.send(player);
    }

    private static void openSagaReset(ServerPlayer player, int page) {
        List<Offer> offers = SagaResetService.offers(player);
        int total = offers == null ? 0 : offers.size();
        int pages = Math.max(1, (total + DISCOVERY_PER_PAGE - 1) / DISCOVERY_PER_PAGE);
        int pg = Math.min(pages - 1, Math.max(0, page));
        int from = pg * DISCOVERY_PER_PAGE;
        int to = Math.min(total, from + DISCOVERY_PER_PAGE);
        UltraScreenSpec spec = base(player, "reset:" + pg, "saga", false)
                .line(UltraStyle.LABEL + "Pay Ancient Coins to replay one saga.")
                .labelValue(
                        "Sagas",
                        total + (pages > 1 ? (" page " + (pg + 1) + "/" + pages) : ""))
                .notice("Clears that saga's progress. This cannot be undone.");
        if (!SagaResetConfig.get().enabled) {
            spec.line(UltraStyle.danger(SagaResetService.RESET_OFF))
                    .buttonsAfterLines();
            spec.footerBackToMain();
            spec.send(player);
            return;
        }
        if (total == 0) {
            spec.line(UltraStyle.dim("No sagas are loaded right now."));
        }
        spec.buttonsAfterLines();
        int placed = 0;
        for (int i = from; i < to; i++) {
            Offer offer = offers.get(i);
            if (offer == null || offer.id() == null || offer.id().isBlank()) {
                continue;
            }
            String action = "page:saga_confirm:" + offer.id();
            String label;
            String style;
            if (offer.resettable()) {
                label = UltraStyle.confirm(UltraStyle.buttonText("Reset " + offer.name()));
                style = UltraStyle.STYLE_CONFIRM;
            } else {
                String state = switch (offer.status()) {
                    case COMPLETED -> "done";
                    case IN_PROGRESS -> "active";
                    case LOCKED -> "locked";
                    case NOT_STARTED -> "new";
                };
                String caption = offer.name() + " " + state;
                label = UltraStyle.dim(
                        UltraStyle.fitsButton(caption) ? caption : UltraStyle.buttonText(offer.name()));
                style = UltraStyle.STYLE_DIM;
            }
            if (placed % 2 == 0) {
                spec.buttonLeft(action, label, style);
            } else {
                spec.buttonRight(action, label, style);
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        if (placed == 0) {
            spec.line(UltraStyle.dim("Nothing to reset until you play a saga."));
        }
        if (pg > 0) {
            spec.buttonLeft("page:reset:" + (pg - 1), UltraStyle.dim("Prev"), UltraStyle.STYLE_DIM);
        }
        if (pg + 1 < pages) {
            if (pg > 0) {
                spec.buttonRight(
                        "page:reset:" + (pg + 1), UltraStyle.LABEL + "Next", UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonLeft(
                        "page:reset:" + (pg + 1), UltraStyle.LABEL + "Next", UltraStyle.STYLE_NORMAL);
            }
            spec.rowGap();
        } else if (pg > 0) {
            spec.rowGap();
        }
        spec.footerBackToMain();
        spec.send(player);
    }

    private static void openSagaConfirm(ServerPlayer player, String sagaId) {
        Offer offer = SagaResetService.findOffer(player, sagaId);
        UltraScreenSpec spec = sub(player, "saga_confirm:" + sagaId, false);
        appendCompactLines(spec, SagaResetService.confirmLines(player, sagaId));
        boolean ready = offer != null
                && offer.resettable()
                && SagaResetConfig.get().enabled
                && offer.id() != null
                && !offer.id().isBlank();
        if (!ready) {
            String why = offer == null
                    ? SagaResetService.NOT_LOADED
                    : offer.status() == SagaResetService.Status.LOCKED
                            ? SagaResetService.SAGA_LOCKED
                            : offer.status() == SagaResetService.Status.NOT_STARTED
                                    ? SagaResetService.NOTHING_TO_RESET
                                    : SagaResetService.RESET_OFF;
            spec.line(UltraStyle.danger(why));
        }
        spec.buttonsAfterLines();
        if (ready) {
            spec.button(
                            "do:saga_reset:" + offer.id(),
                            UltraStyle.danger("Confirm reset"),
                            UltraStyle.STYLE_DANGER)
                    .rowGap();
        }
        spec.footerBack("tab:saga", "Saga list");
        spec.send(player);
    }

    private static void openAndroidRemove(ServerPlayer player) {
        UltraScreenSpec spec = base(player, UltraStyle.PAGE_ANDROID_REMOVE, "android", false)
                .line(UltraStyle.LABEL + "Remove Android forms and restore your previous look.")
                .notice("Click again within 10 seconds. This cannot be undone.")
                .buttonsAfterLines();
        spec.button(
                        "do:" + UltraStyle.PAGE_ANDROID_REMOVE,
                        UltraStyle.danger("Remove Android"),
                        UltraStyle.STYLE_DANGER)
                .rowGap();
        if (StaffAccess.isStaff(player) && CharacterServicesConfig.get().enabled) {
            spec.button("do:android", UltraStyle.confirm("Convert to Android"), UltraStyle.STYLE_CONFIRM)
                    .rowGap();
        }
        if (staffMayEditOthers(player) && CharacterServicesConfig.get().enabled) {
            spec.button(
                            "page:android_staff",
                            UltraStyle.LABEL + "Another player",
                            UltraStyle.STYLE_NORMAL)
                    .rowGap();
        }
        spec.footerBackToMain();
        spec.send(player);
    }

    /**
     * Staff picker, target actions, and named confirm pages. {@code page:} is not staff-gated
     * in {@link UltraGuiActions#requiresStaff}, so these routes refuse non-staff here.
     */
    static boolean isStaffAndroidPage(String page) {
        String lower = page == null ? "" : page.toLowerCase(Locale.ROOT);
        return lower.equals("android_staff")
                || lower.startsWith("android_staff:")
                || lower.startsWith("android_target:")
                || lower.startsWith("android_confirm:");
    }

    private static void openAndroidStaff(ServerPlayer player, int pageIndex) {
        ServerPlayer viewer = actingViewer(player);
        List<String> args = RivalGuiApi.onlinePlayerArgs(viewer);
        int total = args == null ? 0 : args.size();
        int pages = Math.max(1, (total + ANDROID_PICK_PER_PAGE - 1) / ANDROID_PICK_PER_PAGE);
        int pg = Math.min(pages - 1, Math.max(0, pageIndex));
        int from = pg * ANDROID_PICK_PER_PAGE;
        int to = Math.min(total, from + ANDROID_PICK_PER_PAGE);
        String pageId = pg > 0 ? "android_staff:" + pg : "android_staff";
        UltraScreenSpec spec = sub(player, pageId, false)
                .line(UltraStyle.LABEL + "Another player's Android")
                .line(UltraStyle.dim("Staff only. Confirm before convert or remove."))
                .labelValue(
                        "Online",
                        total + (pages > 1 ? " | page " + (pg + 1) + "/" + pages : ""));
        if (total == 0) {
            spec.line(UltraStyle.dim("Nobody else is online right now."));
        }
        spec.buttonsAfterLines();
        int placed = 0;
        for (int i = from; i < to; i++) {
            String arg = args.get(i);
            String canonical = canonicalUuidArg(arg);
            if (canonical == null) {
                continue;
            }
            String name = UltraStyle.buttonText(playerName(viewer, canonical));
            String action = "page:android_target:" + canonical;
            if (placed % 2 == 0) {
                spec.buttonLeft(action, UltraStyle.confirm(name), UltraStyle.STYLE_CONFIRM);
            } else {
                spec.buttonRight(action, UltraStyle.confirm(name), UltraStyle.STYLE_CONFIRM);
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        if (pg > 0) {
            spec.buttonLeft(
                    "page:android_staff:" + (pg - 1), UltraStyle.dim("Prev"), UltraStyle.STYLE_DIM);
        }
        if (pg + 1 < pages) {
            if (pg > 0) {
                spec.buttonRight(
                        "page:android_staff:" + (pg + 1),
                        UltraStyle.LABEL + "Next",
                        UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonLeft(
                        "page:android_staff:" + (pg + 1),
                        UltraStyle.LABEL + "Next",
                        UltraStyle.STYLE_NORMAL);
            }
            spec.rowGap();
        } else if (pg > 0) {
            spec.rowGap();
        }
        spec.footerBack("tab:android", "Android");
        spec.send(player);
    }

    private static void openAndroidTarget(ServerPlayer player, String targetArg) {
        String canonical = canonicalUuidArg(targetArg);
        if (canonical == null) {
            openAndroidStaff(player, 0);
            return;
        }
        ServerPlayer viewer = actingViewer(player);
        String name = playerName(viewer, canonical);
        ServerPlayer target = RivalGuiApi.resolveOnline(viewer, canonical);
        String state;
        if (target == null) {
            state = "Offline";
        } else if (AndroidConversion.isAndroidUpgraded(target)) {
            state = "Upgraded Android";
        } else {
            state = "Not an Android";
        }
        UltraScreenSpec spec = sub(player, "android_target:" + canonical, false)
                .line(UltraStyle.LABEL + "Android tools for " + name)
                .labelValue("Player", name)
                .labelValue("Status", state)
                .buttonsAfterLines();
        spec.button(
                        "page:android_confirm:convert:" + canonical,
                        UltraStyle.confirm("Convert Android"),
                        UltraStyle.STYLE_CONFIRM)
                .rowGap();
        spec.button(
                        "page:android_confirm:remove:" + canonical,
                        UltraStyle.danger("Remove Android"),
                        UltraStyle.STYLE_DANGER)
                .rowGap();
        spec.footerBack("page:android_staff", "Players");
        spec.send(player);
    }

    private static void openAndroidConfirm(ServerPlayer player, String rest) {
        String raw = rest == null ? "" : rest.trim();
        boolean convert;
        String targetArg;
        if (raw.regionMatches(true, 0, "convert:", 0, 8)) {
            convert = true;
            targetArg = raw.substring(8).trim();
        } else if (raw.regionMatches(true, 0, "remove:", 0, 7)) {
            convert = false;
            targetArg = raw.substring(7).trim();
        } else {
            openAndroidStaff(player, 0);
            return;
        }
        String canonical = canonicalUuidArg(targetArg);
        if (canonical == null) {
            openAndroidStaff(player, 0);
            return;
        }
        String name = playerName(actingViewer(player), canonical);
        String page = "android_confirm:" + (convert ? "convert:" : "remove:") + canonical;
        UltraScreenSpec spec = sub(player, page, false)
                .line(UltraStyle.LABEL + (convert ? "Convert Android" : "Remove Android"))
                .labelValue("Player", name)
                .line(UltraStyle.LABEL
                        + (convert
                                ? "Convert " + name + " to Android?"
                                : "Remove the Android upgrade from " + name + "?"))
                .notice(convert
                        ? "This deletes Super forms and Legendary forms. It cannot be undone."
                        : "Form skills are restored. Race, stats, and progression stay.");
        spec.buttonsAfterLines();
        spec.button(
                        "do:android_staff:" + (convert ? "convert:" : "remove:") + canonical,
                        convert
                                ? UltraStyle.confirm("Confirm convert")
                                : UltraStyle.danger("Confirm remove"),
                        convert ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_DANGER)
                .rowGap();
        String backToPlayer = "page:android_target:" + canonical;
        spec.footerBack(backToPlayer, "Player");
        spec.send(player);
    }

    private static ServerPlayer actingViewer(ServerPlayer fallback) {
        ServerPlayer viewer = UltraScreenSpec.inspectViewer();
        return viewer != null ? viewer : fallback;
    }

    /** Staff gate uses the clicking viewer, including while inspecting another player. */
    private static boolean staffMayEditOthers(ServerPlayer fallback) {
        return StaffAccess.isStaff(actingViewer(fallback));
    }

    private static int parseStaffListPage(String page) {
        String raw = page == null ? "" : page.trim();
        int colon = raw.indexOf(':');
        if (colon < 0 || colon + 1 >= raw.length()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.substring(colon + 1).trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String canonicalUuidArg(String arg) {
        if (arg == null || !arg.regionMatches(true, 0, "uuid:", 0, 5)) {
            return null;
        }
        try {
            return "uuid:" + UUID.fromString(arg.substring(5).trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String playerName(ServerPlayer viewer, String uuidArg) {
        String shown = UltraStyle.plain(RivalGuiApi.displayPickerArg(viewer, uuidArg));
        if (shown == null || shown.isBlank()) {
            return "Unknown player";
        }
        return shown;
    }

    /** Append every non-blank line. Long lines wrap; overflow scrolls. */
    private static void appendCompactLines(UltraScreenSpec spec, List<String> lines) {
        appendCompactLines(spec, lines, false);
    }

    /**
     * @param skipCost when true, omit {@code Cost ...} body lines. The page pins that price
     *     separately with {@link UltraStyle#labelValue} so it stays above the buttons.
     */
    private static void appendCompactLines(
            UltraScreenSpec spec, List<String> lines, boolean skipCost) {
        if (spec == null || lines == null) {
            return;
        }
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String plain = UltraStyle.plain(line).trim();
            if (plain.isEmpty()) {
                continue;
            }
            // Skip robotic duplicates already covered by the page title line.
            String lower = plain.toLowerCase(Locale.ROOT);
            if (lower.startsWith("choose the race")
                    || lower.startsWith("pick a new fighting class")
                    || lower.startsWith("opens the appearance editor")
                    || lower.startsWith("race | class")
                    || lower.startsWith("rebuild your character")
                    || (skipCost && lower.startsWith("cost "))) {
                continue;
            }
            spec.line(UltraStyle.LABEL + plain);
        }
    }
}
