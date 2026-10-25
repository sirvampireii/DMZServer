package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.gui.ProgressionGuiApi;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/** Staff progression panel - Home / Boosts / Coins. */
public final class UltraProgressionScreens {
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
        Map<String, String> ph = ProgressionGuiApi.placeholders(player);
        UltraScreenSpec spec = base("boosts", "boosts")
                .line(ph.getOrDefault("boost", UltraStyle.LABEL + "Global TP boost: Off"))
                .line(UltraStyle.LABEL + "Presets start a boost " + UltraStyle.SEP + " End stops it")
                .buttonsAfterLines();
        spec.buttonLeft(
                        "do:boost:1.25:30",
                        UltraStyle.confirm(UltraStyle.boostPreset("1.25", "30")),
                        "confirm")
                .buttonRight(
                        "do:boost:1.5:30",
                        UltraStyle.confirm(UltraStyle.boostPreset("1.5", "30")),
                        "confirm")
                .rowGap();
        spec.buttonLeft(
                        "do:boost:2:30",
                        UltraStyle.confirm(UltraStyle.boostPreset("2", "30")),
                        "confirm")
                .buttonRight(
                        "do:boost:2:60",
                        UltraStyle.confirm(UltraStyle.boostPreset("2", "60")),
                        "confirm")
                .rowGap();
        spec.buttonLeft(
                        "do:boost:3:30",
                        UltraStyle.confirm(UltraStyle.boostPreset("3", "30")),
                        "confirm")
                .buttonRight("do:boost:end", UltraStyle.danger("End boost"), "danger")
                .rowGap();
        spec.footerBackToMain();
        spec.send(player);
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
