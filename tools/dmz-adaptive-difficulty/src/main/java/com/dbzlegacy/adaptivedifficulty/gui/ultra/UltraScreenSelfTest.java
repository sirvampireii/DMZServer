package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.editor.ConfigEditor;
import com.dbzlegacy.adaptivedifficulty.net.gui.GuiOpenPacket;
import com.dbzlegacy.adaptivedifficulty.net.gui.LmGuiNetwork;
import com.dbzlegacy.adaptivedifficulty.progression.form.FirstSuperformChoice;
import com.dbzlegacy.adaptivedifficulty.sparring.DojoRankings;
import com.dbzlegacy.adaptivedifficulty.sparring.SparStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Startup self-test: every Ultra builder id produces a non-empty {@link GuiOpenPacket}
 * with valid panel bounds. Runs without a live player (structural probe specs + capture).
 */
public final class UltraScreenSelfTest {
    /**
     * Single-screen menus with no tab bar. Empty {@code tabsJson} ({@code []} / blank) is
     * correct for these - do not treat as a structural failure.
     */
    static final Set<String> TABLESS_SCREEN_IDS = Set.of("skillcheck", "skills");

    private UltraScreenSelfTest() {}

    public static void runAtStartup() {
        Map<String, String> failures = new LinkedHashMap<>();
        int ok = 0;
        for (String id : UltraScreenIds.BUILDER_IDS) {
            if (id == null || id.isBlank()) {
                continue;
            }
            try {
                List<GuiOpenPacket> packets = UltraScreenSpec.captureSends(() -> probeBuilder(id));
                if (packets == null || packets.isEmpty()) {
                    failures.put(id, "no GuiOpenPacket produced");
                    continue;
                }
                for (GuiOpenPacket pkt : packets) {
                    String err = validatePacket(pkt, id);
                    if (err != null) {
                        failures.put(id, err);
                    } else {
                        ok++;
                    }
                }
            } catch (Throwable t) {
                failures.put(
                        id,
                        t.getClass().getSimpleName()
                                + ": "
                                + (t.getMessage() == null ? "" : t.getMessage()));
            }
        }
        if (failures.isEmpty()) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Ultra screen builder self-test OK ({} packets)",
                    AdaptiveDifficultyMod.MOD_ID,
                    ok);
        } else {
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] Ultra screen builder self-test FAILED ({} ok): {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    ok,
                    failures);
            throw new IllegalStateException(
                    "LegacyMechanics Ultra screen builder self-test failed ("
                            + failures.size()
                            + " error(s)): "
                            + failures);
        }
    }

    /** Used by {@link LmGuiNetwork#runPacketRoundTripSelfTest()}. */
    public static void probeBuilderForRoundTrip(String id) {
        probeBuilder(id);
    }

    /**
     * Representative main-page spec mirroring each {@code Ultra*Screens} builder shape.
     * Does not require a {@link net.minecraft.server.level.ServerPlayer}.
     * Unknown ids in {@link UltraScreenIds#BUILDER_IDS} get a generic zero-config probe.
     */
    private static void probeBuilder(String id) {
        UltraScreenSpec spec =
                switch (id) {
                    case "hub" -> UltraScreenSpec.of("hub")
                            .title("Legacy Mechanics")
                            .page("main")
                            .activeTab("home")
                            .tabs("home|Home")
                            .line("self-test")
                            .buttonLeft("open:difficulty", "Difficulty", "normal")
                            .buttonRight("open:rival", "Rival", "normal")
                            .rowGap()
                            .buttonLeft("open:spar", "Sparring", "normal")
                            .buttonRight("open:prestige", "Prestige", "normal")
                            .rowGap()
                            .buttonLeft("open:skillcheck", "Skill Check", "confirm")
                            .buttonRight("open:character", "Character", "confirm");
                    case "admin" -> UltraScreenSpec.of("admin")
                            .title("Staff Admin")
                            .page("admin")
                            .activeTab("home")
                            .tabs("home|Home")
                            .line("self-test")
                            .button("do:reload", "Reload", "confirm")
                            .buttonLeft("open:progression", "Progression", "normal")
                            .buttonRight("open:config", "Config", "normal")
                            .footerBackToMain();
                    case "difficulty" -> UltraScreenSpec.of("difficulty")
                            .title("Difficulty")
                            .page("main")
                            .activeTab("home")
                            .tabs("home|Home", "tiers|Tiers", "summons|Summons", "stats|CR|staff")
                            .line("self-test")
                            .buttonLeft("tab:tiers", "Tiers", "normal")
                            .footerBackToMain();
                    case "rival" -> UltraScreenSpec.of("rival")
                            .title("Rival")
                            .page("home")
                            .activeTab("home")
                            .tabs("home|Home", "actions|Rival", "challenge|Challenge", "info|Info")
                            .line("self-test")
                            .footerBackToMain();
                    case "spar" -> UltraScreenSpec.of("spar")
                            .title("Sparring")
                            .page("main")
                            .activeTab("home")
                            .tabs("home|Home", "dojo|Dojo", "dojo_war|Wars")
                            .line("self-test")
                            .footerBackToMain();
                    case "prestige" -> UltraScreenSpec.of("prestige")
                            .title("Prestige")
                            .page("shop")
                            .activeTab("shop")
                            .tabs("home|Home", "prestige|Prestige", "shop|Shop")
                            .button("page:skills", "Skill shop", "confirm")
                            .button("page:tiers", "Difficulty tiers", "confirm")
                            .button("page:effects", "Effects", "confirm")
                            .line("self-test")
                            .footerBackToMain();
                    case "character" -> UltraScreenSpec.of("character")
                            .title("Character Services")
                            .page("main")
                            .activeTab("home")
                            .tabs("home|Home", "saga|Saga", "android|Android")
                            .line("self-test")
                            .playerModel(248, 60, 86, 120, 0.9f, true)
                            .footerBackToMain();
                    case "skillcheck" -> UltraScreenSpec.of("skillcheck")
                            .title("Skill Check")
                            .page("main")
                            .activeTab("home")
                            .line("self-test")
                            .footerBackToMain();
                    case "skills" -> UltraScreenSpec.of("skills")
                            .title("Skills")
                            .page("main")
                            .activeTab("home")
                            .line("self-test")
                            .footerBackToMain();
                    case "progression" -> UltraScreenSpec.of("progression")
                            .title("Progression")
                            .page("main")
                            .activeTab("home")
                            .tabs("home|Home", "boosts|Boosts", "coins|Coins")
                            .line("self-test")
                            .footerBackToMain();
                    case "config" -> UltraScreenSpec.of("config")
                            .title("Config Editor")
                            .page("list")
                            .activeTab("home")
                            .tabs("home|Home")
                            .line("self-test")
                            .footerBackToMain();
                    default -> UltraScreenSpec.of(id)
                            .title(id)
                            .page("main")
                            .activeTab("home")
                            .tabs("home|Home")
                            .line("self-test")
                            .button("nav:home", "Home", "dim");
                };
        spec.send(null); // probe mode captures without a player
    }

    /** @return error message or null when valid */
    static String validatePacket(GuiOpenPacket pkt, String expectedId) {
        if (pkt == null) {
            return "null packet";
        }
        if (pkt.screenId() == null || pkt.screenId().isBlank()) {
            return "empty screenId";
        }
        if (expectedId != null
                && !expectedId.isBlank()
                && !expectedId.equalsIgnoreCase(pkt.screenId())) {
            return "screenId=" + pkt.screenId() + " (expected " + expectedId + ")";
        }
        if (pkt.title() == null || pkt.title().isBlank()) {
            return "empty title";
        }
        // Tabs render as hub-style buttons under the title line; tabsJson stays empty for all
        // screens so stock KaiPath does not draw its title-chrome tab strip.
        String tabsErr = requireJsonArray("tabsJson", pkt.tabsJson(), true);
        if (tabsErr != null) {
            return tabsErr;
        }
        if (hasTabBarEntries(pkt.tabsJson())) {
            return "tabsJson must be empty (tabs are buttons under the title line)";
        }
        String linesErr = requireJsonArray("linesJson", pkt.linesJson(), true);
        if (linesErr != null) {
            return linesErr;
        }
        String buttonsErr = requireJsonArray("buttonsJson", pkt.buttonsJson(), false);
        if (buttonsErr != null) {
            return buttonsErr;
        }
        if (pkt.buttonsJson() == null || "[]".equals(pkt.buttonsJson().trim())) {
            return "empty buttonsJson";
        }
        if (pkt.panelWidth() <= 0) {
            return "panelWidth=" + pkt.panelWidth();
        }
        if (pkt.panelHeight() <= 0) {
            return "panelHeight=" + pkt.panelHeight();
        }
        if (pkt.panelWidth() > 2000 || pkt.panelHeight() > 2000) {
            return "panel dimensions out of range " + pkt.panelWidth() + "x" + pkt.panelHeight();
        }
        if (pkt.layoutJson() == null || pkt.layoutJson().isBlank()) {
            return "empty layoutJson";
        }
        try {
            com.google.gson.JsonElement el =
                    com.google.gson.JsonParser.parseString(pkt.layoutJson());
            if (!el.isJsonObject()) {
                return "layoutJson not a JSON object";
            }
        } catch (Throwable t) {
            return "layoutJson invalid JSON: " + t.getMessage();
        }
        return null;
    }

    static boolean isTablessScreen(String screenId) {
        return screenId != null
                && !screenId.isBlank()
                && TABLESS_SCREEN_IDS.contains(screenId.trim().toLowerCase(Locale.ROOT));
    }

    /** True when tabsJson declares at least one tab entry (id / tab: action). */
    private static boolean hasTabBarEntries(String tabsJson) {
        if (tabsJson == null || tabsJson.isBlank()) {
            return false;
        }
        String trimmed = tabsJson.trim();
        if ("[]".equals(trimmed)) {
            return false;
        }
        try {
            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(trimmed);
            return el.isJsonArray() && el.getAsJsonArray().size() > 0;
        } catch (Throwable t) {
            // Invalid JSON is already reported by requireJsonArray.
            return trimmed.contains("\"id\"") || trimmed.contains("tab:");
        }
    }

    private static String requireJsonArray(String field, String json, boolean allowEmpty) {
        if (json == null || json.isBlank()) {
            return allowEmpty ? null : "empty " + field;
        }
        try {
            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(json);
            if (!el.isJsonArray()) {
                return field + " not a JSON array";
            }
            if (!allowEmpty && el.getAsJsonArray().size() == 0) {
                return "empty " + field;
            }
            return null;
        } catch (Throwable t) {
            return field + " invalid JSON: " + t.getMessage();
        }
    }

    /**
     * Tab navigation: builders keep the logical screenId; after hub shim the wire id is hub
     * while tab buttons / button args still name the logical system. Never CNPC.
     */
    public static void verifyTabStaysOnScreen() {
        List<String> issues = new ArrayList<>();
        String[] screens = {"difficulty", "character", "rival", "spar", "prestige", "progression"};
        for (String screen : screens) {
            List<GuiOpenPacket> packets =
                    UltraScreenSpec.captureSends(
                            () -> {
                                UltraScreenSpec.of(screen)
                                        .title(screen)
                                        .page("main")
                                        .activeTab("home")
                                        .tabs("home|Home", "other|Other")
                                        .line("tab-probe")
                                        .button("do:probe", "Probe", "normal")
                                        .send(null);
                            });
            if (packets.isEmpty()) {
                issues.add(screen + ": no packet");
                continue;
            }
            GuiOpenPacket logical = packets.get(0);
            if (!screen.equalsIgnoreCase(logical.screenId())) {
                issues.add(screen + ": logical screenId=" + logical.screenId());
            }
            if (hasTabBarEntries(logical.tabsJson())) {
                issues.add(screen + ": tabsJson must stay empty (tabs are buttons)");
            }
            if (logical.buttonsJson() == null || !logical.buttonsJson().contains("tab:")) {
                issues.add(screen + ": buttonsJson missing tab: actions under title");
            }
            if (logical.layoutJson() == null || !logical.layoutJson().contains("tabsUnderTitle")) {
                issues.add(screen + ": layoutJson missing tabsUnderTitle");
            }
            if (logical.layoutJson() == null || !logical.layoutJson().contains("\"titleAlign\":\"center\"")) {
                issues.add(screen + ": layoutJson titleAlign must be center");
            }
            if (logical.buttonsJson() == null || !logical.buttonsJson().contains("\"screen\":\"" + screen + "\"")) {
                issues.add(screen + ": buttonsJson missing args.screen metadata");
            }
            GuiOpenPacket wire = LmGuiNetwork.applyHubShim(logical);
            if (wire == null) {
                issues.add(screen + ": shim returned null");
                continue;
            }
            if (UltraScreenIds.needsHubShim(screen) && !"hub".equalsIgnoreCase(wire.screenId())) {
                issues.add(screen + ": wire screenId=" + wire.screenId() + " (expected hub)");
            }
            if (wire.layoutJson() == null || !wire.layoutJson().contains("\"titleAlign\":\"center\"")) {
                issues.add(screen + ": wire layoutJson titleAlign must stay center after hub shim");
            }
            if (wire.title() == null || !wire.title().equals(logical.title())) {
                issues.add(screen + ": shim altered title");
            }
            if (wire.panelWidth() <= 0 || wire.panelHeight() <= 0) {
                issues.add(screen + ": shim lost panel bounds");
            }
        }
        if (issues.isEmpty()) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Ultra tab: self-check OK (logical screen + hub wire shim, no CNPC)",
                    AdaptiveDifficultyMod.MOD_ID);
        } else {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Ultra tab: self-check issues: {}", AdaptiveDifficultyMod.MOD_ID, issues);
        }
    }

    /**
     * Staff trust boundary: forged GuiActionPackets for admin/config/progression/skills /
     * {@code do:admin:*} / reload must require staff. Player systems must not.
     */
    public static void verifyStaffGate() {
        List<String> broken = new ArrayList<>();
        for (String system : List.of("admin", "config", "progression", "skills")) {
            if (!UltraGuiActions.isStaffOnlySystem(system)) {
                broken.add("isStaffOnlySystem missing " + system);
            }
            if (!UltraGuiActions.requiresStaff(system, "nav:home")) {
                broken.add("staff screen " + system + " must require staff");
            }
            if (!UltraGuiActions.requiresStaff("hub", "open:" + system)) {
                broken.add("open:" + system + " must require staff");
            }
        }
        for (String act :
                List.of(
                        "do:admin:status",
                        "do:reload",
                        "do:syslog:status",
                        "do:guitest_ack",
                        "do:migrate-cnpc",
                        "admin",
                        "config",
                        "progression",
                        "skills")) {
            if (!UltraGuiActions.requiresStaff("hub", act)) {
                broken.add(act + " must require staff on hub");
            }
        }
        if (!UltraGuiActions.requiresStaff("rival", "do:admin:status")) {
            broken.add("rival do:admin:status must require staff");
        }
        if (!UltraGuiActions.requiresStaff("spar", "do:admin:status")) {
            broken.add("spar do:admin:status must require staff");
        }
        for (String act :
                List.of(
                        "open:difficulty",
                        "open:rival",
                        "open:spar",
                        "open:prestige",
                        "open:character",
                        "open:skillcheck",
                        "do:activate:1",
                        "tab:tiers",
                        "nav:hub")) {
            if (UltraGuiActions.requiresStaff("hub", act)) {
                broken.add("player action wrongly staff-gated: " + act);
            }
        }
        if (UltraGuiActions.requiresStaff("rival", "do:challenge:accept")) {
            broken.add("rival challenge must stay player-facing");
        }
        if (UltraGuiActions.requiresStaff("difficulty", "do:activate:1")) {
            broken.add("difficulty activate must stay player-facing");
        }
        if (!UltraGuiActions.requiresStaff(
                "character", "do:android_staff:convert:uuid:00000000-0000-0000-0000-000000000001")) {
            broken.add("android staff convert must require staff");
        }
        if (!UltraGuiActions.requiresStaff(
                "character", "do:android_staff:remove:uuid:00000000-0000-0000-0000-000000000001")) {
            broken.add("android staff remove must require staff");
        }
        if (UltraGuiActions.requiresStaff("character", "do:android_remove")) {
            broken.add("android_remove must stay player-facing");
        }
        if (!UltraGuiActions.requiresStaff("character", "do:android")) {
            broken.add("self android convert must stay staff-only");
        }
        if (!UltraCharacterScreens.isStaffAndroidPage("android_staff")
                || !UltraCharacterScreens.isStaffAndroidPage("android_staff:1")
                || !UltraCharacterScreens.isStaffAndroidPage(
                        "android_target:uuid:00000000-0000-0000-0000-000000000001")
                || !UltraCharacterScreens.isStaffAndroidPage(
                        "android_confirm:remove:uuid:00000000-0000-0000-0000-000000000001")) {
            broken.add("staff android pages must be recognized for the opener gate");
        }
        if (UltraCharacterScreens.isStaffAndroidPage("android_remove")
                || UltraCharacterScreens.isStaffAndroidPage("android")) {
            broken.add("player android tab must not be treated as the staff picker");
        }
        if (broken.isEmpty()) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Ultra staff gate self-check OK", AdaptiveDifficultyMod.MOD_ID);
        } else {
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] Ultra staff gate self-check FAILED: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    broken);
            throw new IllegalStateException(
                    "LegacyMechanics Ultra staff gate self-check failed: " + broken);
        }
    }

    /**
     * Audit hub + Home-tab {@code open:} targets: each must normalize to a registered builder
     * and not collapse to hub unless the target truly is hub.
     */
    public static void verifyMenuLinks() {
        List<String> broken = new ArrayList<>();
        List<String[]> expected =
                List.of(
                        new String[] {"hub", "open:difficulty", "difficulty"},
                        new String[] {"hub", "open:rival", "rival"},
                        new String[] {"hub", "open:spar", "spar"},
                        new String[] {"hub", "open:prestige", "prestige"},
                        new String[] {"hub", "open:skillcheck", "skillcheck"},
                        new String[] {"hub", "open:skills", "skills"},
                        new String[] {"hub", "open:character", "character"},
                        new String[] {"hub", "open:admin", "admin"},
                        new String[] {"admin", "open:progression", "progression"},
                        new String[] {"admin", "open:config", "config"},
                        new String[] {"difficulty", "open:admin", "admin"},
                        new String[] {"prestige", "open:prestige", "prestige"});
        for (String[] row : expected) {
            String from = row[0];
            String action = row[1];
            String want = row[2];
            String target = action.startsWith("open:") ? action.substring(5) : action;
            String logical = UltraScreenIds.normalizeLogical(target);
            if (!want.equals(logical)) {
                broken.add(from + " " + action + " -> normalize=" + logical + " (want " + want + ")");
                continue;
            }
            if (!UltraScreenIds.BUILDER_IDS.contains(logical)
                    && !UltraScreenIds.PROTOCOL_IDS.contains(logical)) {
                broken.add(from + " " + action + " -> " + logical + " not in BUILDER/PROTOCOL ids");
            }
        }
        // Structural: hub probe must emit skillcheck + character opens, not nav:hub back.
        List<GuiOpenPacket> hubPkts =
                UltraScreenSpec.captureSends(() -> probeBuilder("hub"));
        if (hubPkts.isEmpty()) {
            broken.add("hub: no packet for link audit");
        } else {
            String buttons = hubPkts.get(0).buttonsJson() == null ? "" : hubPkts.get(0).buttonsJson();
            if (!buttons.contains("open:skillcheck") && !buttons.contains("open:skills")) {
                broken.add("hub: missing open:skillcheck / open:skills button");
            }
            if (!buttons.contains("open:character")) {
                broken.add("hub: missing open:character button");
            }
            if (buttons.contains("nav:hub")) {
                broken.add("hub: still has nav:hub (Back to main menu) - remove footerBackToMain");
            }
            if (buttons.contains("nav:refresh") || buttons.contains("Refresh")) {
                broken.add("hub: must not show Refresh");
            }
            if (buttons.contains("\"icon\":\"minecraft:")) {
                broken.add("hub: system tiles must not emit vanilla item icons");
            }
            if (buttons.contains("\"icon\":\"\"") || buttons.contains("not-an-rl")) {
                broken.add("hub: blank or invalid icon ids must be omitted");
            }
            if (hubPkts.get(0).layoutJson() == null
                    || !hubPkts.get(0).layoutJson().contains("scrollBody")) {
                broken.add("hub: layoutJson missing scrollBody / shared body metrics");
            }
            if (hubPkts.get(0).layoutJson() == null
                    || !hubPkts.get(0).layoutJson().contains("\"buttonPad\":")) {
                broken.add("hub: layoutJson missing buttonPad");
            }
            if (hubPkts.get(0).layoutJson() == null
                    || !hubPkts.get(0).layoutJson().contains("\"titleAlign\":\"center\"")) {
                broken.add("hub: layoutJson missing titleAlign=center");
            }
        }
        // layoutJson must stay valid + title-centered even when callers omit titleAlign or oversize.
        String forced =
                GuiOpenPacket.sanitizeLayoutJson(
                        "{\"center\":true,\"margin\":10,\"panelWidth\":320,\"panelHeight\":200}",
                        320,
                        200);
        if (forced == null || !forced.contains("\"titleAlign\":\"center\"")) {
            broken.add("sanitizeLayoutJson must force titleAlign=center");
        }
        String recovered = GuiOpenPacket.sanitizeLayoutJson("{not-json", 320, 200);
        if (recovered == null || !recovered.contains("\"titleAlign\":\"center\"")) {
            broken.add("sanitizeLayoutJson invalid JSON must fall back with titleAlign=center");
        }
        if (!"8".equals(UltraStyle.readableButtonInk("normal", "5"))
                || !"8".equals(UltraStyle.readableButtonInk("confirm", "d"))
                || !"8".equals(UltraStyle.readableButtonInk("normal", "6"))
                || !"8".equals(UltraStyle.readableButtonInk("confirm", "e"))) {
            broken.add("purple/gold/yellow button ink must become dark gray on light buttons");
        }
        if (!"5".equals(UltraStyle.readableButtonInk("danger", "5"))
                || !"c".equals(UltraStyle.readableButtonInk("normal", "c"))
                || !"8".equals(UltraStyle.readableButtonInk("dim", "5"))) {
            broken.add("readable button ink must keep danger purple, normal red, and dim gray");
        }
        String mark = UltraStyle.MARK;
        List<GuiOpenPacket> rivalInk =
                UltraScreenSpec.captureSends(() -> UltraScreenSpec.of("rival")
                        .title("Rival")
                        .page("home")
                        .button("do:declare", "Declare", "confirm")
                        .button("do:spectate", "Spectate", "normal")
                        .send(null));
        if (rivalInk.isEmpty()) {
            broken.add("rival ink probe produced no packet");
        } else {
            String buttons = rivalInk.get(0).buttonsJson() == null ? "" : rivalInk.get(0).buttonsJson();
            String layout = rivalInk.get(0).layoutJson() == null ? "" : rivalInk.get(0).layoutJson();
            if (buttons.contains(mark + "5")
                    || buttons.contains(mark + "d")
                    || layout.contains("\"accent\":\"5\"")) {
                broken.add("rival chrome must not use dark purple on the gold header or light buttons");
            }
            if (!buttons.contains(mark + "fDeclare") && !buttons.contains(mark + "8Declare")) {
                broken.add("rival light buttons must use white or dark gray label ink");
            }
        }
        List<GuiOpenPacket> sparInk =
                UltraScreenSpec.captureSends(() -> UltraScreenSpec.of("spar")
                        .title("Sparring")
                        .page("main")
                        .button("do:ping", "Spar", "confirm")
                        .send(null));
        if (sparInk.isEmpty()) {
            broken.add("spar ink probe produced no packet");
        } else {
            String buttons = sparInk.get(0).buttonsJson() == null ? "" : sparInk.get(0).buttonsJson();
            if (buttons.contains(mark + "6Spar")
                    || buttons.contains(mark + "eSpar")
                    || buttons.contains(mark + "4Spar")) {
                broken.add("spar confirm label must not use gold, yellow, or staff dark red");
            }
            if (!buttons.contains(mark + "3Spar")) {
                broken.add("spar confirm label must use dark aqua ink");
            }
            String sparLayout = sparInk.get(0).layoutJson() == null ? "" : sparInk.get(0).layoutJson();
            if (!sparLayout.contains("\"accent\":\"3\"") || sparLayout.contains("\"accent\":\"4\"")) {
                broken.add("spar title accent must be dark aqua, not staff dark red");
            }
        }
        if (!"3".equals(UltraStyle.systemAccentCode("spar"))
                || !"4".equals(UltraStyle.systemAccentCode("admin"))) {
            broken.add("spar accent must stay dark aqua and admin accent dark red");
        }
        String[] systems = {"character", "difficulty", "progression", "prestige", "spar", "rival", "hub"};
        for (String system : systems) {
            if (!UltraStyle.systemIcon(system).isBlank()) {
                broken.add("system icons must stay blank (no vanilla item marks): " + system);
            }
        }
        if (!"minecraft:textures/item/iron_sword.png"
                .equals(UltraStyle.normalizeIcon("Minecraft:textures/item/iron_sword.png"))) {
            broken.add("normalizeIcon must accept a texture path");
        }
        if (!"".equals(UltraStyle.normalizeIcon("not-an-rl"))
                || !"".equals(UltraStyle.normalizeIcon(""))
                || !"".equals(UltraStyle.normalizeIcon("mine/craft:iron_sword"))) {
            broken.add("normalizeIcon must drop ids that are not namespace:path");
        }
        String tabJson = UltraScreenSpec.tabEntryJson("home", "Home", "minecraft:emerald");
        if (tabJson == null
                || !tabJson.contains("\"icon\":\"minecraft:emerald\"")
                || !tabJson.contains("\"id\":\"home\"")) {
            broken.add("tabEntryJson must include id and an explicit icon");
        }
        if (UltraScreenSpec.tabEntryJson("home", "Home", UltraStyle.systemIcon("difficulty")).contains("\"icon\"")
                || UltraScreenSpec.tabEntryJson("home", "Home", "not-an-rl").contains("\"icon\"")) {
            broken.add("tabEntryJson must omit a blank or invalid icon");
        }
        List<GuiOpenPacket> progInk =
                UltraScreenSpec.captureSends(() -> probeBuilder("progression"));
        if (progInk.isEmpty()
                || progInk.get(0).buttonsJson() == null
                || progInk.get(0).buttonsJson().contains("\"icon\":\"minecraft:")) {
            broken.add("progression: tab buttons must not carry a vanilla item icon");
        }
        String[] footerScreens = {
            "hub", "difficulty", "rival", "spar", "prestige", "character",
            "skillcheck", "skills", "admin", "config", "progression"
        };
        for (String id : footerScreens) {
            List<GuiOpenPacket> footerInk =
                    UltraScreenSpec.captureSends(() -> UltraScreenSpec.of(id)
                            .title("Footer")
                            .page("home")
                            .footerBackHome("Home")
                            .send(null));
            if (footerInk.isEmpty() || footerInk.get(0).buttonsJson() == null) {
                broken.add(id + " footer probe produced no packet");
                continue;
            }
            String footerButtons = footerInk.get(0).buttonsJson();
            if (!"normal".equals(footerButtonStyle(footerButtons, "Main menu"))
                    || !"normal".equals(footerButtonStyle(footerButtons, "Back to Home"))) {
                broken.add(id + " footer buttons must use style normal");
            }
            int mainX = footerButtonX(footerButtons, "Main menu");
            if (mainX != UltraStyle.COL_R) {
                broken.add(id + " hub footer must keep the standard right column, x=" + mainX);
            }
        }
        if (!"End Dimension Strength".equals(ConfigEditor.staffLabel("enableEndDimensionStrength"))) {
            broken.add("config label must read End Dimension Strength, not the raw field id");
        }
        String endDesc = ConfigEditor.friendlyDescription(
                "difficulty", "enableEndDimensionStrength", "true");
        if (endDesc == null
                || endDesc.isBlank()
                || endDesc.contains("enableEndDimensionStrength")
                || endDesc.startsWith("Turns ")) {
            broken.add("config description must say what End Dimension Strength does");
        }
        if (!"The End".equals(ConfigEditor.sectionTitle("difficulty", "enableEndDimensionStrength"))) {
            broken.add("enableEndDimensionStrength must be grouped under The End");
        }
        boolean sawBoss = false;
        boolean sawScale = false;
        boolean sawSpawn = false;
        for (ConfigEditor.SectionLink link : ConfigEditor.sections("difficulty")) {
            if (link == null || link.title() == null) {
                continue;
            }
            if ("Boss Behavior".equals(link.title())) {
                sawBoss = true;
            }
            if ("Player Scaling".equals(link.title())) {
                sawScale = true;
            }
            if ("Mob Spawning".equals(link.title())) {
                sawSpawn = true;
            }
        }
        if (!sawBoss || !sawScale || !sawSpawn) {
            broken.add("difficulty config must group Boss Behavior, Player Scaling, and Mob Spawning");
        }
        if (!"ON".equals(ConfigEditor.defaultShown("difficulty", "enableEndDimensionStrength"))) {
            broken.add("End Dimension Strength must show its default as ON");
        }
        List<GuiOpenPacket> charPkts =
                UltraScreenSpec.captureSends(() -> probeBuilder("character"));
        if (!charPkts.isEmpty()) {
            GuiOpenPacket charPkt = charPkts.get(0);
            String layout = charPkt.layoutJson() == null ? "" : charPkt.layoutJson();
            if (!layout.contains("playerModel")) {
                broken.add("character: layoutJson missing playerModel");
            }
            if (!layout.contains("\"y\":60") && !layout.contains("\"y\": 60")) {
                broken.add("character: playerModel y must be 60 (was 36 - head clipped)");
            }
            if (layout.contains("\"scale\":1.5") || layout.contains("\"scale\": 1.5")) {
                broken.add("character: playerModel scale 1.5 overshoots panel - use 0.9");
            } else if (!(layout.contains("\"scale\":0.9") || layout.contains("\"scale\": 0.9"))) {
                broken.add("character: playerModel scale must be 0.9 (1.0 still clipped the head)");
            }
            String buttons = charPkt.buttonsJson() == null ? "" : charPkt.buttonsJson();
            if (!buttons.contains("\"label\":\"Android\"")
                    || !buttons.contains("\"label\":\"Saga\"")
                    || !buttons.contains("\"label\":\"Home\"")) {
                broken.add("character: tab buttons must be Home/Saga/Android (short labels)");
            }
            if (buttons.contains("Android Removal") || buttons.contains("Saga Reset")) {
                broken.add("character: long tab labels overflow and mojibake on KaiPath");
            }
            // No section-sign / mojibake leftovers in any button label.
            if (buttons.indexOf('\u00A7') >= 0 || buttons.indexOf('\u00C2') >= 0) {
                broken.add("character: buttonsJson still has section-sign or C2 mojibake bytes");
            }
            if (buttons.contains("\"icon\":\"minecraft:")) {
                broken.add("character: tab buttons must not carry a vanilla item icon");
            }
            String fit = UltraStyle.fitAsciiLabel("Android Removal", UltraStyle.labelMaxWidth(102));
            if (fit.indexOf('\u00E2') >= 0 || fit.indexOf('\u2026') >= 0) {
                broken.add("fitAsciiLabel must use ASCII ellipsis only");
            }
            if (!fit.endsWith("...") || fit.length() >= "Android Removal".length()) {
                broken.add("fitAsciiLabel must clip labels that exceed tab text budget");
            }
        }
        for (String tablessId : TABLESS_SCREEN_IDS) {
            List<GuiOpenPacket> skillPkts =
                    UltraScreenSpec.captureSends(() -> probeBuilder(tablessId));
            if (skillPkts.isEmpty()) {
                broken.add(tablessId + ": no GuiOpenPacket for tab-less probe");
                continue;
            }
            if (hasTabBarEntries(skillPkts.get(0).tabsJson())) {
                broken.add(tablessId + ": must have no tab bar (empty tabsJson)");
            }
        }
        if (broken.isEmpty()) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Ultra menu link audit OK ({} open: targets + hub/character layout checks)",
                    AdaptiveDifficultyMod.MOD_ID,
                    expected.size());
        } else {
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] Ultra menu link audit FAILED: {}", AdaptiveDifficultyMod.MOD_ID, broken);
            throw new IllegalStateException(
                    "LegacyMechanics Ultra menu link audit failed: " + broken);
        }
    }

    /**
     * Hub shim: when {@link UltraScreenIds#KAIPATH_DYNAMIC_ANY_SCREEN_ID} is false, non-registered
     * logical ids rewrite wire screenId to hub. When dynamic-any is on (KaiPath 1.3.1+), expect
     * an empty tunnel set and identity wire ids.
     */
    public static void verifyHubShim() {
        List<String> issues = new ArrayList<>();
        Set<String> tunneled = UltraScreenIds.hubShimTunnelIds();
        if (UltraScreenIds.KAIPATH_DYNAMIC_ANY_SCREEN_ID) {
            if (!tunneled.isEmpty()) {
                issues.add("dynamic-any on but tunnel set non-empty: " + tunneled);
            }
            for (String logical : UltraScreenIds.BUILDER_IDS) {
                GuiOpenPacket built =
                        UltraScreenSpec.of(logical)
                                .title(logical)
                                .page("main")
                                .activeTab("home")
                                .tabs("home|Home")
                                .line("shim-probe")
                                .buttonLeft("nav:home", "Home", "dim")
                                .toPacket();
                GuiOpenPacket wire = LmGuiNetwork.applyHubShim(built);
                if (wire == null || !logical.equalsIgnoreCase(wire.screenId())) {
                    issues.add(
                            logical
                                    + ": expected identity wire id, got "
                                    + (wire == null ? "null" : wire.screenId()));
                }
            }
            if (issues.isEmpty()) {
                AdaptiveDifficultyMod.LOGGER.info(
                        "[{}] Ultra hub shim self-check OK (KaiPath dynamic-any - no tunneling)",
                        AdaptiveDifficultyMod.MOD_ID);
            } else {
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] Ultra hub shim self-check issues: {}", AdaptiveDifficultyMod.MOD_ID, issues);
            }
            return;
        }
        for (String logical : tunneled) {
            GuiOpenPacket built =
                    UltraScreenSpec.of(logical)
                            .title(logical)
                            .page("main")
                            .activeTab("home")
                            .tabs("home|Home")
                            .line("shim-probe")
                            .buttonLeft("nav:home", "Home", "dim")
                            .toPacket();
            GuiOpenPacket wire = LmGuiNetwork.applyHubShim(built);
            if (wire == null || !"hub".equalsIgnoreCase(wire.screenId())) {
                issues.add(logical + ": wire screenId=" + (wire == null ? "null" : wire.screenId()));
                continue;
            }
            if (wire.layoutJson() == null || !wire.layoutJson().contains(logical)) {
                issues.add(logical + ": layoutJson missing system metadata");
            }
            if (wire.layoutJson() == null || !wire.layoutJson().contains("\"titleAlign\":\"center\"")) {
                issues.add(logical + ": layoutJson titleAlign must be center after hub shim");
            }
            if (wire.buttonsJson() == null || !wire.buttonsJson().contains("\"screen\":\"" + logical + "\"")) {
                issues.add(logical + ": buttons lost args.screen");
            }
            if (wire.panelWidth() != built.panelWidth() || wire.panelHeight() != built.panelHeight()) {
                issues.add(logical + ": panel size changed by shim");
            }
        }
        // Registered ids must not be rewritten.
        GuiOpenPacket hub = UltraScreenSpec.of("hub").title("Hub").page("main").button("nav:home", "Home", "dim").toPacket();
        GuiOpenPacket hubWire = LmGuiNetwork.applyHubShim(hub);
        if (hubWire == null || !"hub".equalsIgnoreCase(hubWire.screenId())) {
            issues.add("hub: unexpected wire id");
        }
        if (issues.isEmpty()) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Ultra hub shim self-check OK (tunneling {})",
                    AdaptiveDifficultyMod.MOD_ID,
                    tunneled);
        } else {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Ultra hub shim self-check issues: {}", AdaptiveDifficultyMod.MOD_ID, issues);
        }
    }

    /**
     * Body copy must end at or above the first content button. A minimum body height that
     * ignores that boundary paints text through the buttons; leftover lines scroll.
     */
    public static void verifyBodyCap() {
        List<String> broken = new ArrayList<>();
        checkBodyCap(
                broken,
                "rival-high-buttons",
                UltraScreenSpec.of("rival")
                        .title("Rivals")
                        .page("actions")
                        .activeTab("actions")
                        .tabs("home|Home", "actions|Rival", "challenge|Challenge", "info|Info")
                        .line("Your rivals.")
                        .line("Pending invites: 1")
                        .setRow(UltraStyle.CONTENT_ROW_TABS + 70)
                        .button("page:pick_rival", "Pick player", "normal")
                        .toPacket(),
                false);
        checkBodyCap(
                broken,
                "progression-absolute-row",
                UltraScreenSpec.of("progression")
                        .title("Progression")
                        .page("boosts")
                        .activeTab("boosts")
                        .tabs("home|Home", "boosts|Boosts", "coins|Coins")
                        .line("Global TP boost: Off")
                        .line("Presets start a boost")
                        .setRow(100)
                        .button("do:boost:end", "End boost", "danger")
                        .toPacket(),
                false);
        UltraScreenSpec counted =
                UltraScreenSpec.of("spar")
                        .title("Spar")
                        .page("main")
                        .activeTab("home")
                        .tabs("home|Home", "dojo|Dojo")
                        .line("Hall of Infamy, your stats, and weights.");
        int before = counted.row();
        counted.buttonsAfterLines();
        if (counted.row() < before + 80) {
            broken.add(
                    "buttonsAfterLines row="
                            + counted.row()
                            + " expected at least "
                            + (before + 80));
        }
        checkBodyCap(
                broken,
                "buttons-after-lines",
                counted.button("page:stats", "Your stats", "normal").toPacket(),
                false);

        UltraScreenSpec packed =
                UltraScreenSpec.of("difficulty")
                        .title("Difficulty")
                        .page("main")
                        .activeTab("home")
                        .tabs("home|Home", "tiers|Tiers");
        for (int i = 0; i < 20; i++) {
            packed.line("Body line " + i + " stays in the scroll region.");
        }
        packed.setRow(UltraStyle.CONTENT_ROW_TABS + 70);
        for (int i = 0; i < 10; i++) {
            packed.button("page:pack" + i, "Row " + i, "normal").rowGap();
        }
        GuiOpenPacket packedPacket = packed.toPacket();
        checkBodyCap(broken, "packed-buttons-scroll", packedPacket, true);
        JsonObject packedLayout =
                JsonParser.parseString(packedPacket.layoutJson()).getAsJsonObject();
        int packedBody = packedLayout.get("bodyMaxHeight").getAsInt();
        if (packedBody >= UltraStyle.BODY_MIN_H) {
            broken.add(
                    "packed-buttons-scroll: bodyMaxHeight="
                            + packedBody
                            + " still floored at the minimum body height");
        }
        checkBodyWrap(broken);
        checkConfigFullscreen(broken);

        if (broken.isEmpty()) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Ultra body cap self-check OK", AdaptiveDifficultyMod.MOD_ID);
        } else {
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] Ultra body cap self-check FAILED: {}", AdaptiveDifficultyMod.MOD_ID, broken);
            throw new IllegalStateException(
                    "LegacyMechanics Ultra body cap self-check failed: " + broken);
        }
    }

    /**
     * Config editor requests a panel larger than the GUI (so KaiPath 1.3.20 clamps the
     * chrome to the window) and lays controls out in a 426x240 design box — the grid the
     * widget coordinates use. KaiPath 1.3.31 scales {@code panel / design}, so a design
     * box equal to the panel request would render the 426-grid widgets tiny in the
     * top-left. A stack of rows must not grow the request toward the compact menu cap.
     */
    private static void checkConfigFullscreen(List<String> broken) {
        UltraScreenSpec grown =
                UltraScreenSpec.of("config").panel(1920, 1080).lockPanel().fillScreen(426, 240).setRow(50);
        for (int i = 0; i < 30; i++) {
            grown.button("do:refresh", "Row " + i, "dim").rowGap();
        }
        GuiOpenPacket grownPkt = grown.toPacket();
        if (grownPkt.panelWidth() != 1920 || grownPkt.panelHeight() != 1080) {
            broken.add(
                    "lockPanel grew the config panel request to "
                            + grownPkt.panelWidth()
                            + "x"
                            + grownPkt.panelHeight());
        }
        JsonObject grownLay = JsonParser.parseString(grownPkt.layoutJson()).getAsJsonObject();
        if (!grownLay.has("designWidth")
                || grownLay.get("designWidth").getAsInt() != 426
                || grownLay.get("designHeight").getAsInt() != 240) {
            broken.add("lockPanel changed the fillScreen design box");
        }
        GuiOpenPacket cfg = UltraConfigScreens.layoutProbe();
        if (cfg.panelWidth() != 1920 || cfg.panelHeight() != 1080) {
            broken.add(
                    "config editor must request 1920x1080 so the client clamps to the window, got "
                            + cfg.panelWidth()
                            + "x"
                            + cfg.panelHeight());
        }
        JsonObject lay = JsonParser.parseString(cfg.layoutJson()).getAsJsonObject();
        if (!lay.has("fillScreen") || !lay.get("fillScreen").getAsBoolean()) {
            broken.add("config editor must set layoutJson.fillScreen");
        }
        if (!lay.has("designWidth")
                || !lay.has("designHeight")
                || lay.get("designWidth").getAsInt() != 426
                || lay.get("designHeight").getAsInt() != 240) {
            broken.add(
                    "config fillScreen design size must be 426x240 (the widget grid;"
                            + " scale = panel / design on KaiPath 1.3.31), got "
                            + lay.get("designWidth")
                            + "x"
                            + lay.get("designHeight"));
        }
        if (!lay.has("margin") || lay.get("margin").getAsInt() != 0) {
            broken.add("config editor must set margin 0 so the clamp fills the GUI");
        }
        if (!lay.has("center") || lay.get("center").getAsBoolean()) {
            broken.add("config editor must set center false");
        }
        int boxW = lay.get("designWidth").getAsInt();
        int boxH = lay.get("designHeight").getAsInt();
        String buttons = cfg.buttonsJson() == null ? "" : cfg.buttonsJson();
        String inputs = cfg.inputsJson() == null ? "" : cfg.inputsJson();
        if (!buttons.contains("Difficulty") || !inputs.contains("input:search")) {
            broken.add("config probe missing the category rail or search field");
        }
        if (!buttons.contains("Mob Spawning") || !buttons.contains("Review")) {
            broken.add("config probe missing the breadcrumb or review button");
        }
        if (!"normal".equals(footerButtonStyle(buttons, "Main menu"))) {
            broken.add("config Main menu footer must use style normal");
        }
        int mainX = footerButtonX(buttons, "Main menu");
        if (mainX + UltraStyle.BTN_W != boxW) {
            broken.add("config Main menu must sit on the design-box right edge, x=" + mainX);
        }
        boolean rail = false;
        JsonArray widgets = JsonParser.parseString(buttons).getAsJsonArray();
        for (JsonElement el : widgets) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject b = el.getAsJsonObject();
            int x = b.get("x").getAsInt();
            int y = b.get("y").getAsInt();
            int w = b.get("w").getAsInt();
            int h = b.get("h").getAsInt();
            if (w == 148) {
                rail = true;
            }
            if (x < 0 || y < 0 || x + w > boxW || y + h > boxH) {
                broken.add("config control outside the design box: " + b.get("label"));
            }
        }
        if (!rail) {
            broken.add("config category rail must be 148px wide");
        }
        JsonArray fields = JsonParser.parseString(inputs).getAsJsonArray();
        for (JsonElement el : fields) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject in = el.getAsJsonObject();
            int x = in.get("x").getAsInt();
            int y = in.get("y").getAsInt();
            int w = in.get("w").getAsInt();
            int h = in.get("h").getAsInt();
            if (x < 0 || y < 0 || x + w > boxW || y + h > boxH) {
                broken.add("config text field outside the design box: " + in.get("id"));
            }
        }
    }

    /**
     * Every editable field must say what it accepts, in plain words, and a rejected entry must read
     * as a friendly sentence - never "Invalid input" or raw parser text. Exercises the numeric,
     * boolean, and text paths against the live difficulty config when it is registered.
     */
    public static void verifyConfigFieldHints() {
        List<String> broken = new ArrayList<>();
        String[][] probes = {
            {"difficulty", "areaDifficultyVariancePercent"},
            {"difficulty", "enabled"},
            {"difficulty", "vanillaDifficulty"},
            {"difficulty", "whitelist"},
        };
        int tested = 0;
        for (String[] probe : probes) {
            String module = probe[0];
            String path = probe[1];
            if (ConfigEditor.tab(module) == null) {
                continue;
            }
            // A section container is not an editable field; this test covers scalar fields.
            if (ConfigEditor.kindOf(module, path) == ConfigEditor.Kind.GROUP) {
                continue;
            }
            tested++;
            String desc = ConfigEditor.friendlyDescription(module, path, "");
            if (desc == null || desc.isBlank()) {
                broken.add(module + "." + path + " has no plain-language description");
            } else {
                String plainDesc = UltraStyle.plain(desc);
                if (plainDesc.endsWith("(") || plainDesc.endsWith("e.g.") || plainDesc.endsWith("i.e.")
                        || plainDesc.endsWith(" -") || plainDesc.endsWith("-")
                        || plainDesc.endsWith("so") || plainDesc.endsWith("the")
                        || plainDesc.endsWith("still") || plainDesc.endsWith("and")) {
                    broken.add(module + "." + path + " description ends mid-sentence: " + plainDesc);
                }
                // No raw ids, no snake_case. Matched as a whole camelCase token so words like
                // "CustomNPCs" or "T5" are not mistaken for a field id.
                if (plainDesc.contains("_")
                        || plainDesc.matches(".*(?<![A-Za-z0-9])[a-z]+(?:[A-Z][a-z]+)+[A-Za-z0-9]*.*")) {
                    broken.add(module + "." + path + " description leaks a raw field id: " + plainDesc);
                }
            }
            String accepts = ConfigEditor.acceptedValues(module, path);
            if (accepts == null || accepts.isBlank()) {
                broken.add(module + "." + path + " states no accepted values");
            } else if (accepts.contains("Invalid") || accepts.contains("Exception")
                    || accepts.contains("java.") || accepts.contains("null")) {
                broken.add(module + "." + path + " accepted-values text is raw: " + accepts);
            }
            String hint = ConfigEditor.invalidHint(module, path, "For input string: \"abc\"");
            if (hint == null || hint.isBlank()) {
                broken.add(module + "." + path + " has no friendly inline message");
            } else if (hint.contains("Invalid input") || hint.contains("Exception")
                    || hint.contains("java.") || hint.contains("For input string")) {
                broken.add(module + "." + path + " inline message leaks raw error text: " + hint);
            }
        }
        if (tested == 0) {
            // Config modules are not registered in this environment; the source-contract audit
            // covers the strings. Do not fail the startup self-test over a missing registry.
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Config field hint self-test skipped (no config modules registered)",
                    AdaptiveDifficultyMod.MOD_ID);
            return;
        }
        if (broken.isEmpty()) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Config field hint self-test OK ({} fields)",
                    AdaptiveDifficultyMod.MOD_ID,
                    tested);
            return;
        }
        AdaptiveDifficultyMod.LOGGER.error(
                "[{}] Config field hint self-test FAILED: {}",
                AdaptiveDifficultyMod.MOD_ID,
                broken);
        throw new IllegalStateException(
                "LegacyMechanics config field hint self-test failed (" + broken.size()
                        + " error(s)): " + broken);
    }

    /**
     * Numeric ranges must be derived from the field, not guessed from a single keyword. The config
     * mixes percent fields ({@code eliteChancePercent} = 2.25, a 0-100 rate) with true 0-1 chance
     * fractions ({@code ancientCoinDropChance} = 0.05). A chance check that matched every field
     * containing "chance" rejected the percent fields' own defaults as out of range, so a staff
     * member could not re-type what was already there. This pins the split both ways: a percent
     * field states 0-100 and accepts 2.25, a chance field states 0-1 and still rejects 5, and the
     * boolean and text paths keep their plain-word guidance.
     */
    public static void verifyConfigNumberRanges() {
        List<String> broken = new ArrayList<>();
        UUID uuid = UUID.randomUUID();
        int tested = 0;
        try {
            // Percent field: must state 0-100 (not 0-1) and accept its own default, 2.25.
            if (ConfigEditor.tab("difficulty") != null
                    && ConfigEditor.kindOf("difficulty", "eliteChancePercent")
                            == ConfigEditor.Kind.NUMBER) {
                tested++;
                String accepts = ConfigEditor.acceptedValues("difficulty", "eliteChancePercent");
                if (accepts == null || !accepts.contains("0 to 100")) {
                    broken.add("eliteChancePercent should state a 0-100 range, got: " + accepts);
                }
                if (accepts != null && accepts.contains("0 to 1") && !accepts.contains("0 to 100")) {
                    broken.add("eliteChancePercent must not be read as a 0-1 chance: " + accepts);
                }
                // The regression: its own default (2.25) used to be rejected as out of range.
                String error = ConfigEditor.stageSet(
                        uuid, "difficulty", "eliteChancePercent", "2.25");
                if (error != null) {
                    broken.add("eliteChancePercent rejected its own default 2.25: " + error);
                }
                // Still a real range: 150 is over the percent cap and must be caught.
                String over = ConfigEditor.stageSet(
                        uuid, "difficulty", "eliteChancePercent", "150");
                if (over == null || !over.contains("0 to 100")) {
                    broken.add("eliteChancePercent should reject 150 as over 100, got: " + over);
                }
            }
            // True 0-1 chance fraction: must state 0-1 and still reject a value above 1.
            if (ConfigEditor.tab("difficulty") != null
                    && ConfigEditor.kindOf("difficulty", "ancientCoinDropChance")
                            == ConfigEditor.Kind.NUMBER) {
                tested++;
                String accepts = ConfigEditor.acceptedValues("difficulty", "ancientCoinDropChance");
                if (accepts == null || !accepts.contains("0 to 1")) {
                    broken.add("ancientCoinDropChance should state a 0-1 range, got: " + accepts);
                }
                String over = ConfigEditor.stageSet(
                        uuid, "difficulty", "ancientCoinDropChance", "5");
                if (over == null || !over.contains("0 to 1")) {
                    broken.add("ancientCoinDropChance should reject 5 as over 1, got: " + over);
                }
            }
            // A plain number with no derivable range falls back to its type, in plain words.
            if (ConfigEditor.tab("difficulty") != null
                    && ConfigEditor.kindOf("difficulty", "mobScaleRadius")
                            == ConfigEditor.Kind.NUMBER) {
                tested++;
                String accepts = ConfigEditor.acceptedValues("difficulty", "mobScaleRadius");
                if (accepts == null || !accepts.contains("number")) {
                    broken.add("mobScaleRadius should fall back to its type, got: " + accepts);
                }
                if (accepts != null && (accepts.contains("0 to 1") || accepts.contains("0 to 100"))) {
                    broken.add("mobScaleRadius has no derivable range, got: " + accepts);
                }
            }
            // Boolean and text keep their plain-word guidance alongside the numeric paths.
            if (ConfigEditor.tab("difficulty") != null) {
                String bool = ConfigEditor.acceptedValues("difficulty", "enabled");
                if (bool == null || !bool.contains("true") || !bool.contains("false")) {
                    broken.add("boolean field should accept true / false, got: " + bool);
                }
            }
        } finally {
            ConfigEditor.discardAll(uuid);
        }
        if (tested == 0) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Config number-range self-test skipped (no config modules registered)",
                    AdaptiveDifficultyMod.MOD_ID);
            return;
        }
        if (broken.isEmpty()) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Config number-range self-test OK ({} fields)",
                    AdaptiveDifficultyMod.MOD_ID,
                    tested);
            return;
        }
        AdaptiveDifficultyMod.LOGGER.error(
                "[{}] Config number-range self-test FAILED: {}",
                AdaptiveDifficultyMod.MOD_ID,
                broken);
        throw new IllegalStateException(
                "LegacyMechanics config number-range self-test failed (" + broken.size()
                        + " error(s)): " + broken);
    }

    /**
     * No field may be taller than the page budget. A block that outgrows it paints straight
     * through the Prev / Review / Next row, which is what put those buttons on top of a full-width
     * Apply / - / + bar. Measured with the code that paints, for every editable module, and it
     * has to hold both for a valid entry and right after a rejected one - the inline hint row
     * is what pushes a field over if the layout adds a second row for it.
     */
    public static void verifyConfigFieldBudget() {
        List<String> broken = new ArrayList<>();
        int budget = UltraConfigScreens.configFieldBudget();
        int checked = 0;
        for (ConfigEditor.EditorTab tab : ConfigEditor.editorTabs()) {
            if (tab == null || !tab.editable()) {
                continue;
            }
            Map<String, Integer> heights = UltraConfigScreens.innerFieldHeights(tab.id());
            if (heights == null || heights.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, Integer> entry : heights.entrySet()) {
                checked++;
                if (entry.getValue() > budget) {
                    broken.add(
                            tab.id() + " " + entry.getKey() + " is " + entry.getValue()
                                    + "px (budget " + budget + ")");
                }
            }
        }
        if (checked == 0) {
            // No config modules are registered in this environment; do not fail startup over it.
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Config field budget self-test skipped (no config modules registered)",
                    AdaptiveDifficultyMod.MOD_ID);
            return;
        }
        if (broken.isEmpty()) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Config field budget self-test OK ({} fields, budget {}px)",
                    AdaptiveDifficultyMod.MOD_ID,
                    checked,
                    budget);
            return;
        }
        AdaptiveDifficultyMod.LOGGER.error(
                "[{}] Config field budget self-test FAILED: {}",
                AdaptiveDifficultyMod.MOD_ID,
                broken);
        throw new IllegalStateException(
                "LegacyMechanics config field budget self-test failed (" + broken.size()
                        + " field(s) outgrow the page budget): " + broken);
    }

    /**
     * Deeper config screens put Prev / Review / Next on one row. Three 80px buttons plus 8px gaps
     * are wider than the main column, so the row must shrink its gaps - never its buttons - or the
     * client's min-width clamp stacks them on top of each other and on the pinned footer bar.
     * Probes every paging shape and asserts the three stay side by side inside the column.
     */
    public static void verifyConfigActionRow() {
        List<String> broken = new ArrayList<>();
        record Box(int x, int y, int w, int h, String label) {}
        int colX = UltraConfigScreens.mainColumnX();
        int colW = UltraConfigScreens.mainColumnW();
        int rowY = UltraConfigScreens.reviewRowY();
        int btnH = UltraConfigScreens.configButtonHeight();
        int footerY = UltraConfigScreens.footerRowY();
        // page/pages shapes: single page, first, middle (all three), last.
        int[][] shapes = {{0, 1}, {0, 3}, {1, 3}, {2, 3}};
        for (int[] shape : shapes) {
            int page = shape[0];
            int pages = shape[1];
            String tag = "config action row page=" + page + "/" + pages;
            GuiOpenPacket pkt = UltraConfigScreens.actionRowProbe(page, pages);
            String err = validatePacket(pkt, "config");
            if (err != null) {
                broken.add(tag + ": " + err);
                continue;
            }
            JsonArray arr = JsonParser.parseString(pkt.buttonsJson()).getAsJsonArray();
            List<Box> boxes = new ArrayList<>();
            for (JsonElement el : arr) {
                if (el == null || !el.isJsonObject()) {
                    continue;
                }
                JsonObject b = el.getAsJsonObject();
                // Labels are inked (dim/confirm), so match on the plain text.
                String label = UltraStyle.plain(b.has("label") ? b.get("label").getAsString() : "");
                if (!(label.startsWith("Prev") || label.startsWith("Review") || label.startsWith("Next"))) {
                    continue;
                }
                int x = b.get("x").getAsInt();
                int y = b.get("y").getAsInt();
                int w = b.get("w").getAsInt();
                int h = b.get("h").getAsInt();
                boxes.add(new Box(x, y, w, h, label));
                if (y != rowY) {
                    broken.add(tag + ": '" + label + "' sits at y=" + y + " (expected review row " + rowY + ")");
                }
                if (h != btnH) {
                    broken.add(tag + ": '" + label + "' is " + h + "px tall (expected " + btnH + ")");
                }
                if (x < colX || x + w > colX + colW) {
                    broken.add(
                            tag + ": '" + label + "' leaves the main column x=" + x + " w=" + w
                                    + " (column " + colX + ".." + (colX + colW) + ")");
                }
                // A min-width button still fits one control row; wider means the gap math broke.
                if (w < UltraStyle.BTN_MIN_W) {
                    broken.add(tag + ": '" + label + "' is " + w + "px wide (min " + UltraStyle.BTN_MIN_W + ")");
                }
                if (y + h > footerY) {
                    broken.add(tag + ": '" + label + "' runs into the pinned footer at y=" + footerY);
                }
            }
            int expected = (page > 0 ? 1 : 0) + 1 + (page + 1 < pages ? 1 : 0);
            if (boxes.size() != expected) {
                broken.add(tag + ": expected " + expected + " paging button(s), found " + boxes.size());
            }
            // Pairwise disjoint: no two buttons may share any X.
            for (int i = 0; i < boxes.size(); i++) {
                for (int j = i + 1; j < boxes.size(); j++) {
                    Box a = boxes.get(i);
                    Box b = boxes.get(j);
                    boolean overlap = a.x() < b.x() + b.w() && b.x() < a.x() + a.w();
                    if (overlap) {
                        broken.add(
                                tag + ": '" + a.label() + "' (x=" + a.x() + " w=" + a.w() + ") overlaps '"
                                        + b.label() + "' (x=" + b.x() + " w=" + b.w() + ")");
                    }
                }
            }
            // Row must fit the column exactly enough that nothing is squeezed off the right edge.
            int right = colX;
            for (Box box : boxes) {
                right = Math.max(right, box.x() + box.w());
            }
            if (right > colX + colW) {
                broken.add(tag + ": row overruns the main column by " + (right - colX - colW) + "px");
            }
        }
        if (broken.isEmpty()) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Config editor paging row self-test OK (4 shapes, no overlap)",
                    AdaptiveDifficultyMod.MOD_ID);
            return;
        }
        AdaptiveDifficultyMod.LOGGER.error(
                "[{}] Config editor paging row self-test FAILED: {}",
                AdaptiveDifficultyMod.MOD_ID,
                broken);
        throw new IllegalStateException(
                "LegacyMechanics config editor paging row self-test failed (" + broken.size()
                        + " error(s)): " + broken);
    }

    /** Long body copy wraps on spaces and still scrolls; KaiPath must not ellipsize it. */
    private static void checkBodyWrap(List<String> broken) {
        String sentence = "Purchased looks carry over. Your class stays if the new race has it.";
        int fit = UltraStyle.bodyTextFitPx(UltraStyle.PANEL_W, -1);
        List<String> wrapped = UltraStyle.wrapBody(sentence, fit);
        if (wrapped.size() < 2) {
            broken.add("race confirm copy must wrap instead of one clipped line");
        }
        StringBuilder joined = new StringBuilder();
        int budget = Math.max(8, fit / UltraStyle.BODY_GLYPH_PX);
        for (String line : wrapped) {
            String plain = UltraStyle.plain(line);
            if (plain.length() > budget) {
                broken.add("wrapped body line exceeds the text box: " + plain);
            }
            if (plain.endsWith("...") || plain.contains("...")) {
                broken.add("wrapped body line must not use an ellipsis: " + plain);
            }
            if (joined.length() > 0) {
                joined.append(' ');
            }
            joined.append(plain);
        }
        if (!sentence.equals(joined.toString())) {
            broken.add("wrap dropped words: " + joined);
        }
        int narrow = UltraStyle.bodyTextFitPx(UltraStyle.PANEL_W, 248);
        String monkey = "Not available for Monkey - this race uses a form model.";
        List<String> besideModel = UltraStyle.wrapBody(monkey, narrow);
        int narrowBudget = Math.max(8, narrow / UltraStyle.BODY_GLYPH_PX);
        StringBuilder monkeyJoined = new StringBuilder();
        for (String line : besideModel) {
            String plain = UltraStyle.plain(line);
            if (plain.length() > narrowBudget) {
                broken.add("model-side body line exceeds the text box: " + plain);
            }
            if (monkeyJoined.length() > 0) {
                monkeyJoined.append(' ');
            }
            monkeyJoined.append(plain);
        }
        if (!monkey.equals(monkeyJoined.toString())) {
            broken.add("model-side wrap dropped words: " + monkeyJoined);
        }
        String token = "X".repeat(budget + 12);
        List<String> split = UltraStyle.wrapBody(token, fit);
        StringBuilder tokenJoined = new StringBuilder();
        for (String line : split) {
            String plain = UltraStyle.plain(line);
            if (plain.length() > budget) {
                broken.add("oversized token still exceeds the text box");
            }
            tokenJoined.append(plain);
        }
        if (!token.equals(tokenJoined.toString()) || split.size() < 2) {
            broken.add("a token wider than the box must continue on the next line");
        }
        String mark = UltraStyle.MARK;
        String midWord = "He" + mark + "cllo";
        List<String> midLines = UltraStyle.wrapBody(midWord, fit);
        String midJoined = String.join("", midLines);
        if (!"Hello".equals(UltraStyle.plain(midJoined)) || !midJoined.contains(mark + "c")) {
            broken.add("wrap must keep a color change in the middle of a word: " + midJoined);
        }
        int midAt = midJoined.indexOf(mark + "c");
        if (midAt < 0 || !"He".equals(UltraStyle.plain(midJoined.substring(0, midAt)))) {
            broken.add("mid-word color must stay after the leading glyphs: " + midJoined);
        }
        String wide = "1234" + mark + "c567890";
        List<String> wideLines = UltraStyle.wrapBody(wide, UltraStyle.BODY_GLYPH_PX * 4);
        if (wideLines.size() < 2) {
            broken.add("colored token must wrap when it is wider than the box");
        } else if (!wideLines.get(1).startsWith(mark + "c")) {
            broken.add("wrapped continuation must repeat the active color: " + wideLines);
        }
        String lone = "Ends" + mark;
        List<String> loneLines = UltraStyle.wrapBody(lone, fit);
        String loneJoined = String.join("", loneLines);
        if (!"Ends".equals(loneJoined) || loneJoined.indexOf(mark.charAt(0)) >= 0) {
            broken.add("a trailing section-sign with no format code must be dropped: " + loneJoined);
        }
        GuiOpenPacket packet =
                UltraScreenSpec.of("character")
                        .title("Character Services")
                        .page("race_confirm")
                        .line(UltraStyle.LABEL + sentence)
                        .line(UltraStyle.LABEL + "Staff cannot undo this for you.")
                        .toPacket();
        String linesJson = packet.linesJson() == null ? "" : packet.linesJson();
        if (!linesJson.contains("Purchased looks carry over.")
                || !linesJson.contains("Staff cannot undo this for you.")) {
            broken.add("race confirm packet dropped wrapped body copy");
        }
        if (linesJson.contains("...")) {
            broken.add("race confirm packet still ellipsizes body copy");
        }
    }

    private static String footerButtonStyle(String buttonsJson, String labelContains) {
        JsonObject button = footerButton(buttonsJson, labelContains);
        if (button == null) {
            return "";
        }
        return button.has("style") ? button.get("style").getAsString() : "";
    }

    private static int footerButtonX(String buttonsJson, String labelContains) {
        JsonObject button = footerButton(buttonsJson, labelContains);
        if (button == null || !button.has("x")) {
            return -1;
        }
        return button.get("x").getAsInt();
    }

    private static JsonObject footerButton(String buttonsJson, String labelContains) {
        if (buttonsJson == null || buttonsJson.isBlank() || labelContains == null) {
            return null;
        }
        JsonArray arr = JsonParser.parseString(buttonsJson).getAsJsonArray();
        for (JsonElement el : arr) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            String label = o.has("label") ? o.get("label").getAsString() : "";
            if (UltraStyle.plain(label).contains(labelContains)) {
                return o;
            }
        }
        return null;
    }

    private static void checkBodyCap(
            List<String> broken, String name, GuiOpenPacket packet, boolean expectScroll) {
        if (packet == null || packet.layoutJson() == null || packet.buttonsJson() == null) {
            broken.add(name + ": missing packet");
            return;
        }
        JsonObject layout = JsonParser.parseString(packet.layoutJson()).getAsJsonObject();
        int bodyTop = layout.get("bodyTop").getAsInt();
        int bodyMax = layout.get("bodyMaxHeight").getAsInt();
        boolean scroll = layout.get("scrollBody").getAsBoolean();
        int minButton = Integer.MAX_VALUE;
        JsonArray buttons = JsonParser.parseString(packet.buttonsJson()).getAsJsonArray();
        for (JsonElement el : buttons) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject button = el.getAsJsonObject();
            if (button.has("footer") && button.get("footer").getAsBoolean()) {
                continue;
            }
            int y = button.get("y").getAsInt();
            if (y < bodyTop) {
                continue;
            }
            minButton = Math.min(minButton, y);
        }
        if (minButton != Integer.MAX_VALUE && bodyTop + bodyMax > minButton) {
            broken.add(
                    name
                            + ": body ends at "
                            + (bodyTop + bodyMax)
                            + " over button y="
                            + minButton);
        }
        if (expectScroll && !scroll) {
            broken.add(name + ": expected scrollBody");
        }
    }

    /**
     * Dojo war challenges are one-shot per pair: a finished war (or a lapsed pending challenge)
     * must stop blocking a fresh challenge the moment it expires, while a still-active war and a
     * live pending challenge must both keep blocking duplicates. Exercises the real lookup over a
     * local map so no live {@link SparStore} state is touched.
     */
    public static void verifyDojoWarExpiry() {
        List<String> broken = new ArrayList<>();
        long now = System.currentTimeMillis();
        Map<String, SparStore.DojoChallenge> challenges = new LinkedHashMap<>();
        challenges.put("dojo-b-expired", dojoChallenge("dojo-a", "dojo-b", now - 60_000L, true));
        challenges.put("dojo-d-pending", dojoChallenge("dojo-c", "dojo-d", now + 3_600_000L, false));
        challenges.put("dojo-f-war", dojoChallenge("dojo-e", "dojo-f", now + 3_600_000L, true));

        // Expired war: must NOT block a rematch (either direction).
        if (DojoRankings.findLiveChallengeIn(challenges, "dojo-a", "dojo-b") != null) {
            broken.add("expired war still blocks a rematch");
        }
        if (DojoRankings.findLiveChallengeIn(challenges, "dojo-b", "dojo-a") != null) {
            broken.add("expired war still blocks a rematch in reverse");
        }
        // Live pending challenge: must still block.
        if (DojoRankings.findLiveChallengeIn(challenges, "dojo-c", "dojo-d") == null) {
            broken.add("live pending challenge no longer blocks a duplicate");
        }
        // Active war: must still block.
        if (DojoRankings.findLiveChallengeIn(challenges, "dojo-e", "dojo-f") == null) {
            broken.add("active war no longer blocks a duplicate");
        }
        // Unrelated pair: nothing found.
        if (DojoRankings.findLiveChallengeIn(challenges, "dojo-x", "dojo-y") != null) {
            broken.add("unrelated dojo pair matched a challenge");
        }
        if (broken.isEmpty()) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Dojo war challenge expiry self-test OK (3 records)", AdaptiveDifficultyMod.MOD_ID);
            return;
        }
        AdaptiveDifficultyMod.LOGGER.error(
                "[{}] Dojo war challenge expiry self-test FAILED: {}",
                AdaptiveDifficultyMod.MOD_ID,
                broken);
        throw new IllegalStateException(
                "LegacyMechanics dojo war challenge expiry self-test failed (" + broken.size()
                        + " error(s)): " + broken);
    }

    private static SparStore.DojoChallenge dojoChallenge(
            String from, String to, long expiresAt, boolean active) {
        SparStore.DojoChallenge c = new SparStore.DojoChallenge();
        c.fromDojoUuid = from;
        c.fromMentorUuid = from;
        c.toDojoUuid = to;
        c.toDojoName = to;
        c.expiresAt = expiresAt;
        c.active = active;
        return c;
    }

    /**
     * grant-first-superform must land on a real superform: the earliest with unlock level 1 or
     * higher. The level-0 form (Oozaru for Saiyans) is skipped and never granted, with no
     * fallback to it. grant-first-legendary is unchanged — the earliest legendary form, level 0
     * included. Selections use the live DMZ form types ({@code superforms} / {@code legendaryforms}).
     */
    public static void verifyFirstSuperformChoice() {
        List<String> broken = new ArrayList<>();
        // Saiyan superforms from the live config: Oozaru at level 0, Super Saiyan at level 1.
        List<FirstSuperformChoice.Candidate> saiyan = List.of(
                form("oozaru", "oozaru", "superforms", 0),
                form("ssgrades", "supersaiyan", "superforms", 1),
                form("supersaiyan", "supersaiyan2", "superforms", 5));
        FirstSuperformChoice.Candidate chosen =
                FirstSuperformChoice.select(saiyan, FirstSuperformChoice.Kind.SUPER);
        if (chosen == null || !"supersaiyan".equals(chosen.form)) {
            broken.add("Saiyan super grant must skip Oozaru (level 0) and pick Super Saiyan, got "
                    + (chosen == null ? "no form" : chosen.label()));
        }
        // A race with no level-0 superform still picks its earliest superform.
        List<FirstSuperformChoice.Candidate> noLevelZero = List.of(
                form("monkey", "wukongspride", "superforms", 2),
                form("monkey", "wukongsrage", "superforms", 1));
        chosen = FirstSuperformChoice.select(noLevelZero, FirstSuperformChoice.Kind.SUPER);
        if (chosen == null || !"wukongsrage".equals(chosen.form)) {
            broken.add("a race without a level-0 superform must pick its earliest, got "
                    + (chosen == null ? "no form" : chosen.label()));
        }
        // A race whose only superform is level 0 gets no selection — never the level-0 form.
        List<FirstSuperformChoice.Candidate> onlyLevelZero = List.of(
                form("oozaru", "oozaru", "superforms", 0));
        if (FirstSuperformChoice.select(onlyLevelZero, FirstSuperformChoice.Kind.SUPER) != null) {
            broken.add("a race whose only superform is level 0 must get no selection");
        }
        // Legendary is unchanged: earliest legendary form, level 0 still eligible.
        List<FirstSuperformChoice.Candidate> legendary = List.of(
                form("legendaryforms", "ssjfullpower", "legendaryforms", 3),
                form("legendaryforms", "ikari", "legendaryforms", 1),
                form("legendaryforms", "levelzerolegend", "legendaryforms", 0));
        chosen = FirstSuperformChoice.select(legendary, FirstSuperformChoice.Kind.LEGENDARY);
        if (chosen == null || !"levelzerolegend".equals(chosen.form)) {
            broken.add("legendary grant must still pick the earliest legendary form (level 0 "
                    + "included), got " + (chosen == null ? "no form" : chosen.label()));
        }
        if (broken.isEmpty()) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] First superform choice self-test OK (level-0 forms skipped)",
                    AdaptiveDifficultyMod.MOD_ID);
            return;
        }
        AdaptiveDifficultyMod.LOGGER.error(
                "[{}] First superform choice self-test FAILED: {}",
                AdaptiveDifficultyMod.MOD_ID,
                broken);
        throw new IllegalStateException(
                "LegacyMechanics first superform choice self-test failed (" + broken.size()
                        + " error(s)): " + broken);
    }

    /**
     * Ultra pickers page instead of truncating: at every picker page size (4, 6, 8, 10) and
     * list length 0-25, walking pages 0..pageCount-1 yields every item exactly once, in the
     * original order, and a page past the end is empty. Also round-trips the page arg.
     */
    public static void verifyPickerPaging() {
        List<String> broken = new ArrayList<>();
        for (int perPage : new int[] {4, 6, 8, 10}) {
            for (int total = 0; total <= 25; total++) {
                List<Integer> items = new ArrayList<>();
                for (int i = 0; i < total; i++) {
                    items.add(i);
                }
                int pages = UltraPickHelper.pageCount(total, perPage);
                List<Integer> walked = new ArrayList<>();
                for (int pg = 0; pg < pages; pg++) {
                    List<Integer> slice = UltraPickHelper.pageSlice(items, perPage, pg);
                    if (slice.size() > perPage) {
                        broken.add(total + " items at " + perPage + ": page " + (pg + 1) + " overfull");
                    }
                    walked.addAll(slice);
                }
                if (!walked.equals(items)) {
                    broken.add(total + " items at " + perPage + " per page did not page in order once each");
                }
                if (!UltraPickHelper.pageSlice(items, perPage, pages).isEmpty()) {
                    broken.add(total + " items at " + perPage + ": page past the end repeats items");
                }
            }
        }
        if (UltraPickHelper.pageIndex(UltraPickHelper.pageArg("pick_release", 2)) != 2
                || !"pick_release".equals(UltraPickHelper.pageBase(UltraPickHelper.pageArg("pick_release", 2)))
                || !"pick_release".equals(UltraPickHelper.pageArg("pick_release", 0))
                || UltraPickHelper.pageIndex("pending_decide:IN|uuid:abc") != 0) {
            broken.add("page arg does not round-trip");
        }
        if (broken.isEmpty()) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Picker paging self-test OK (page sizes 4/6/8/10, lists 0-25)",
                    AdaptiveDifficultyMod.MOD_ID);
            return;
        }
        AdaptiveDifficultyMod.LOGGER.error(
                "[{}] Picker paging self-test FAILED: {}", AdaptiveDifficultyMod.MOD_ID, broken);
        throw new IllegalStateException(
                "LegacyMechanics picker paging self-test failed (" + broken.size()
                        + " error(s)): " + broken);
    }

    private static FirstSuperformChoice.Candidate form(
            String group, String name, String formType, int unlockLevel) {
        return new FirstSuperformChoice.Candidate(group, name, formType, unlockLevel, false);
    }
}
