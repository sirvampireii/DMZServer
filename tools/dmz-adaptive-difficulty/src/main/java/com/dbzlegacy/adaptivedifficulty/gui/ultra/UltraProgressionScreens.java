package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.gui.ProgressionGuiApi;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/** Staff progression panel - Home / Boosts / Coins. */
public final class UltraProgressionScreens {
    /** Boost control views under the Boosts tab (do:boost / do:masteryboost land here). */
    static final String PAGE_TP_BOOST = "boosts_tp";

    static final String PAGE_MASTERY_BOOST = "boosts_mastery";

    private UltraProgressionScreens() {}

    public static void open(ServerPlayer player, String page) {
        if (player == null) {
            return;
        }
        if (!StaffAccess.isStaff(player)) {
            UltraHubScreens.open(player, "main");
            return;
        }
        String p = page == null || page.isBlank() ? "main" : page.toLowerCase(Locale.ROOT);
        switch (p) {
            // Avoid page alias "tpboost": its String.hashCode bytes are C2 A7 in lookupswitch.
            case "boosts", "boost_panel", "tp_boost", "boost" -> openBoosts(player);
            case PAGE_TP_BOOST -> openBoostControls(player, false);
            case PAGE_MASTERY_BOOST -> openBoostControls(player, true);
            case "coins", "economy", "ancient_coins" -> openCoins(player);
            default -> openHome(player);
        }
    }

    private static UltraScreenSpec base(String page, String tab) {
        return UltraScreenSpec.of("progression")
                .title("Progression")
                .page(page)
                .activeTab(tab)
                .tabs("home|Home", "boosts|Boosts", "coins|Coins");
    }

    /** Subpage: no tab row — dual footer (Back + Main menu). */
    private static UltraScreenSpec sub(String page) {
        return UltraScreenSpec.of("progression")
                .title("Progression")
                .page(page);
    }


    /** Overview only - Boosts and Coins live on their tabs. */
    private static void openHome(ServerPlayer player) {
        UltraScreenSpec spec = base("main", "home")
                .line(UltraStyle.LABEL + "Training boosts, Ancient Coins, and staff tools")
                .line(ProgressionGuiApi.statusText())
                .line(UltraStyle.LABEL + "Open the Boosts or Coins tab for controls.")
                .buttonsAfterLines();
        spec.footerBackToMain();
        spec.send(player);
    }

    private static void openBoosts(ServerPlayer player) {
        boostsSelectorSpec(ProgressionGuiApi.placeholders(player)).send(player);
    }

    private static void openBoostControls(ServerPlayer player, boolean mastery) {
        boostControlSpec(ProgressionGuiApi.placeholders(player), mastery).send(player);
    }

    /** Boosts tab: both status lines at a glance, then one button per boost. Self-test builds it too. */
    static UltraScreenSpec boostsSelectorSpec(Map<String, String> ph) {
        UltraScreenSpec spec = base("boosts", "boosts")
                .line(tpBoostLine(ph))
                .line(masteryBoostLine(ph))
                .line(UltraStyle.LABEL + "Pick a boost to open its presets and End button")
                .buttonsAfterLines();
        spec.buttonLeft("page:" + PAGE_TP_BOOST, "TP Boost", "normal")
                .buttonRight("page:" + PAGE_MASTERY_BOOST, "Mastery Boost", "normal")
                .rowGap();
        spec.footerBackToMain();
        return spec;
    }

    /**
     * One boost's controls: its status line, the five presets, and End. The TP and mastery
     * views are the same layout; only the action prefix differs, so each drives its own boost.
     */
    static UltraScreenSpec boostControlSpec(Map<String, String> ph, boolean mastery) {
        String act = mastery ? "do:masteryboost:" : "do:boost:";
        UltraScreenSpec spec = sub(mastery ? PAGE_MASTERY_BOOST : PAGE_TP_BOOST)
                .line(mastery ? masteryBoostLine(ph) : tpBoostLine(ph))
                .line(UltraStyle.LABEL + "Presets start a boost " + UltraStyle.SEP + " End stops it")
                .buttonsAfterLines();
        spec.buttonLeft(
                        act + "1.25:30",
                        UltraStyle.confirm(UltraStyle.boostPreset("1.25", "30")),
                        "confirm")
                .buttonRight(
                        act + "1.5:30",
                        UltraStyle.confirm(UltraStyle.boostPreset("1.5", "30")),
                        "confirm")
                .rowGap();
        spec.buttonLeft(
                        act + "2:30",
                        UltraStyle.confirm(UltraStyle.boostPreset("2", "30")),
                        "confirm")
                .buttonRight(
                        act + "2:60",
                        UltraStyle.confirm(UltraStyle.boostPreset("2", "60")),
                        "confirm")
                .rowGap();
        spec.buttonLeft(
                        act + "3:30",
                        UltraStyle.confirm(UltraStyle.boostPreset("3", "30")),
                        "confirm")
                .buttonRight(act + "end", UltraStyle.danger("End boost"), "danger")
                .rowGap();
        spec.footerBack("page:boosts", "Boosts");
        return spec;
    }

    private static String tpBoostLine(Map<String, String> ph) {
        return ph.getOrDefault("boost", UltraStyle.LABEL + "Global TP boost: Off");
    }

    private static String masteryBoostLine(Map<String, String> ph) {
        return ph.getOrDefault("mastery_boost", UltraStyle.LABEL + "Global mastery boost: Off");
    }

    private static void openCoins(ServerPlayer player) {
        Map<String, String> ph = ProgressionGuiApi.placeholders(player);
        boolean staffFree = "true".equalsIgnoreCase(ph.getOrDefault("staff_free_ancient_coin_costs", "false"));
        UltraScreenSpec spec = base("coins", "coins");
        for (String line : ProgressionGuiApi.linesForPage(player, "economy")) {
            spec.line(line);
        }
        spec.buttonsAfterLines();
        String toggle = staffFree
                ? UltraStyle.confirm("Staff free costs - ON")
                : UltraStyle.dim("Staff free costs - OFF");
        spec.button(
                        "do:toggle_staff_free_coins:" + (staffFree ? "off" : "on"),
                        toggle,
                        staffFree ? "confirm" : "dim")
                .rowGap();
        spec.footerBackToMain();
        spec.send(player);
    }
}
