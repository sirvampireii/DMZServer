package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons;
import com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache;
import com.dbzlegacy.adaptivedifficulty.calc.DifficultySnapshot;
import com.dbzlegacy.adaptivedifficulty.calc.PlayerCombatProfile;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy;
import com.dbzlegacy.adaptivedifficulty.data.PlayerDifficultyData;
import com.dbzlegacy.adaptivedifficulty.calc.LmOverhaulScaledCombat;
import com.dbzlegacy.adaptivedifficulty.progression.end.EndDimensionStrength;
import com.dbzlegacy.adaptivedifficulty.service.DifficultyActions;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockSystem;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockTier;
import com.dbzlegacy.adaptivedifficulty.title.DifficultyTitle;
import com.dbzlegacy.adaptivedifficulty.title.TitleSystem;
import com.dbzlegacy.adaptivedifficulty.util.PaidFeatureAccess;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.dbzlegacy.adaptivedifficulty.util.SystemGate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/**
 * Difficulty - Home / Tiers / Summons (4.7.71: three tabs for everyone; staff open Combat rating from Home). Sub-pages cover
 * teams, titles, and staff tools. Coin drop chat is a home-page toggle. Weights live under Sparring.
 */
public final class UltraDifficultyScreens {
    /** Titles shown per list page (enum can grow past this). */
    private static final int TITLES_PER_PAGE = 8;

    private UltraDifficultyScreens() {}

    public static void open(ServerPlayer player, String page) {
        if (player == null) {
            return;
        }
        DifficultyActions.prepareGui(player);
        String raw = page == null || page.isBlank() ? "main" : page.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("tier_ask:")) {
            openTierAsk(player, parseTier(raw.substring("tier_ask:".length())));
            return;
        }
        if (lower.startsWith("summon_ask:")) {
            openSummonAsk(player, lower.substring("summon_ask:".length()).trim());
            return;
        }
        if ("clear_tier_ask".equals(lower)) {
            openClearTierAsk(player);
            return;
        }
        if ("title_unequip_ask".equals(lower)) {
            openUnequipTitleAsk(player);
            return;
        }
        if ("flush_ask".equals(lower)) {
            openFlushAsk(player);
            return;
        }
        if ("difficulty_off_ask".equals(lower)) {
            openDifficultyOffAsk(player);
            return;
        }
        if (lower.startsWith("title_detail:")) {
            openTitleDetail(player, raw.substring("title_detail:".length()).trim());
            return;
        }
        // Weights moved to Sparring — keep old Difficulty deep-links working.
        if (lower.startsWith("weights_input:")
                || lower.startsWith("weights_confirm:")
                || "weights".equals(lower)
                || "weight".equals(lower)
                || "weight_shop".equals(lower)) {
            UltraSparScreens.open(player, raw);
            return;
        }
        if (lower.startsWith("titles_p") || lower.startsWith("titles:")) {
            String num = lower.startsWith("titles_p")
                    ? lower.substring("titles_p".length())
                    : lower.substring("titles:".length());
            int pageIndex = 0;
            try {
                pageIndex = Integer.parseInt(num.trim());
            } catch (NumberFormatException ignored) {
                pageIndex = 0;
            }
            openTitles(player, Math.max(0, pageIndex));
            return;
        }
        // 4.7.63: the 3-step guide shows once per account. Not done: Home / Tiers / Your tier open the matching
        // guide step. Done (finished or skipped): Home and the plain Tiers list. The guide's own page ids always
        // open the guide, so "How difficulty works" replays it without clearing the mark.
        switch (DifficultyTutorial.route(lower, DifficultyTutorial.done(player))) {
            case STEP1 -> {
                openGuideStep1(player);
                return;
            }
            case STEP2 -> {
                openTiers(player, true);
                return;
            }
            case STEP3 -> {
                openYourTier(player, true);
                return;
            }
            case HOME -> {
                openYourTier(player, false);
                return;
            }
            case TIERS -> {
                openTiers(player, false);
                return;
            }
            default -> {
            }
        }
        switch (lower) {
            case "summons", "summon" -> openSummons(player);
            case "end_dragon", "dragon", "summon_dragon", "summon_confirm" -> openSummonConfirm(player);
            case "wither" -> openBossSummonConfirm(player, PlayerBossSummons.Boss.WITHER);
            case "warden" -> openBossSummonConfirm(player, PlayerBossSummons.Boss.WARDEN);
            case "titles", "title", "titles_p0" -> openTitles(player, 0);
            case "stats", "statistics", "details", "cr", "combat_rating" -> openStats(player);
            case "admin", "staff" -> openStaffAdmin(player);
            case "eventlog", "event_log", "logs", "syslog" -> openEventLog(player);
            default -> open(player, "main");
        }
    }

    private static UltraScreenSpec base(ServerPlayer player, String page, String tab) {
        UltraScreenSpec spec = UltraScreenSpec.of("difficulty")
                .fitMenu()
                .title("Difficulty")
                .page(page)
                .activeTab(tab);
        // 4.7.71: every system keeps exactly three tabs, Home first; staff open Combat rating from Home.
        spec.tabs("home|Home", "tiers|Tiers", "summons|Summons");
        return spec;
    }

    /** Subpage: no tab row — dual footer (Back + Main menu). */
    private static UltraScreenSpec sub(String page) {
        return UltraScreenSpec.of("difficulty")
                .fitMenu()
                .title("Difficulty")
                .page(page);
    }

    /**
     * Guide Step 1 (4.7.59): what Difficulty is, in one sentence and three rows. Next goes to Step 2 (the
     * Tiers list). Back sits in the left footer slot on every step; 4.7.63 adds Skip guide in the right slot and
     * one line saying the guide shows once.
     */
    private static void openGuideStep1(ServerPlayer player) {
        DifficultySnapshot snap = DifficultyCache.refresh(player);
        String tierName = snap == null || snap.activeTier <= 0 ? "No tier yet. Pick one in Step 2." : snap.activeTierName;
        UltraScreenSpec spec = base(player, DifficultyTutorial.PAGE_STEP1, "home");
        stepHeader(spec, 1, "What is Difficulty");
        spec.line(UltraStyle.VALUE + "Difficulty makes the mobs near you fight at a level you pick, and pays you more for it.")
                // 4.7.61: "Label: value" rows that each fit one line (the colon separates the gray label from the
                // white text over KaiPath too; the old copy ran on and left "mastery" and "free" alone on a line).
                .labelValue("Stronger enemies:", "mobs near you match your tier")
                .labelValue("Bigger rewards:", "more TP per kill, plus Momentum")
                .labelValue("Change anytime:", "lowering your tier is free")
                .labelValue("Your tier:", tierName)
                .line(UltraStyle.LABEL + GUIDE_ONCE)
                .buttonsAfterLines();
        spec.button("page:" + DifficultyTutorial.PAGE_STEP2, UltraStyle.confirm("Next: Pick your level"),
                UltraStyle.STYLE_CONFIRM).rowGap();
        spec.footerGuide("nav:hub", "main menu", GUIDE_DONE);
        spec.send(player);
    }

    /** 4.7.63: said once, on Step 1. */
    static final String GUIDE_ONCE = "You only see this guide once. Replay it any time from Difficulty Home.";
    /** Skip (every step) and Got it (Step 3): mark the guide done, open Home. */
    private static final String GUIDE_DONE = "do:" + DifficultyTutorial.ACT_DONE;

    /** {@code Step N of 3 - Title} header, then the progress dots. */
    private static void stepHeader(UltraScreenSpec spec, int step, String title) {
        spec.line(UltraStyle.header(DifficultyGuide.stepLabel(step) + " - " + title))
                .line(UltraStyle.LABEL + "Progress " + DifficultyGuide.dots(step));
    }

    /** Momentum status: time banked of the max, a bar, and what it does. */
    private static void momentumLines(UltraScreenSpec spec, ServerPlayer player) {
        DifficultyConfig cfg = DifficultyConfig.get();
        if (!cfg.momentumEnabled) {
            spec.labelValue("Momentum:", "Off on this server");
            return;
        }
        long left = com.dbzlegacy.adaptivedifficulty.progression.Momentum.remainingMillis(player);
        long max = cfg.momentumMaxBankSeconds * 1000L;
        // 4.7.84 (D-4): two short lines plus the bar; the same numbers as before, in plain words.
        spec.labelValue("Momentum:", com.dbzlegacy.adaptivedifficulty.progression.Momentum.clock(left) + " of "
                        + com.dbzlegacy.adaptivedifficulty.progression.Momentum.clock(max) + " saved")
                .line(UltraStyle.color(left > 0L ? "a" : "8", DifficultyGuide.bar(left, max, 24)))
                .line(UltraStyle.LABEL + momentumHowLine(cfg.momentumSecondsPerKill, cfg.momentumMasteryMultiplier));
    }

    /** {@code Each difficulty kill saves 10s. While you have time saved, hits give +50% mastery.} */
    static String momentumHowLine(long secondsPerKill, double masteryMultiplier) {
        long pct = Math.round((masteryMultiplier - 1.0) * 100.0);
        return "Each difficulty kill saves " + secondsPerKill + "s."
                + (pct > 0 ? " While you have time saved, hits give +" + pct + "% mastery." : "");
    }

    /**
     * Guide Step 3 (4.7.59): your tier, any free trial, Momentum, and the tier tools that used to sit on
     * Home (difficulty on/off, clear tier, titles, rival teams, coin messages, staff tools).
     *
     * <p>4.7.63: with {@code guide} false this is Difficulty Home, where a player lands once the guide is done:
     * the same tier, trial and Momentum rows and tools under a plain header, plus "How difficulty works" to
     * replay the guide. In the guide, "Got it - take me to Difficulty" finishes it.
     */
    private static void openYourTier(ServerPlayer player, boolean guide) {
        DifficultySnapshot snap = DifficultyCache.refresh(player);
        PlayerDifficultyData data = DifficultyCache.data(player);
        boolean personal = data != null && data.isPersonalEnabled();
        int active = data == null ? 0 : data.getActiveTier();
        UltraScreenSpec spec = base(player, guide ? DifficultyTutorial.PAGE_STEP3 : "main", "home");
        if (guide) {
            stepHeader(spec, 3, "Your tier");
        } else {
            spec.line(UltraStyle.header("Your tier"));
        }
        spec.labelValue("Tier:", active <= 0 || snap == null
                        ? (guide ? "No tier yet. Pick one in Step 2." : "No tier yet. Pick one in Tiers.")
                        : snap.activeTierName)   // 4.7.84 (D-1): the name already starts with "T1 "
                .labelValue("Difficulty:", personal ? "on - mobs near you match your tier" : "off - mobs are normal");
        com.dbzlegacy.adaptivedifficulty.service.DifficultyTrial.Active trial =
                com.dbzlegacy.adaptivedifficulty.service.DifficultyTrial.active(player);
        if (trial != null) {
            long left = com.dbzlegacy.adaptivedifficulty.service.DifficultyTrial.remainingMillis(player);
            spec.labelValue("Free trial:", "T" + trial.tier() + " for "
                    + com.dbzlegacy.adaptivedifficulty.progression.Momentum.clock(left) + " more, then "
                    + (trial.prev() > 0 ? "back to T" + trial.prev() : "back to no tier"));
        }
        momentumLines(spec, player);
        // 4.7.84 (D-4): the "Beyond Difficulty" prestige and rival lines are gone; the main menu shows both.
        spec.buttonsAfterLines();
        if (guide) {
            // 4.7.78: the end of the guide has one action; every tool waits on Home.
            spec.button(GUIDE_DONE, UltraStyle.confirm("Got it - take me to Difficulty"), UltraStyle.STYLE_CONFIRM)
                    .rowGap();
            spec.footerGuide("page:" + DifficultyTutorial.PAGE_STEP2, "Step 2", GUIDE_DONE);
            spec.send(player);
            return;
        }
        // 4.7.78: Home turns difficulty on and off; tiers (and clearing one) live in the Tiers tab.
        // 4.7.81 (GuiKit): paired rows - [Difficulty | Coin drop chat] [Titles | How difficulty works] [Server tools | Combat rating].
        placeDifficultyToggle(spec, personal, true);
        placeCoinMessageToggle(spec, data != null && data.isCoinDropChat(), false);
        spec.rowGap();
        spec.buttonLeft("page:titles_p0", "Titles", UltraStyle.STYLE_NORMAL);
        // Replay on demand, with the other Home actions; never clears the done mark.
        spec.buttonRight("page:" + DifficultyTutorial.PAGE_STEP1, UltraStyle.plain("How difficulty works"),
                UltraStyle.STYLE_NORMAL).rowGap();
        if (StaffAccess.isStaff(player)) {
            // Staff entry reads red, like Staff Admin on the Rival and Sparring homes.
            spec.buttonLeft("page:admin", UltraStyle.LABEL + "Server tools", UltraStyle.STYLE_NORMAL);
            spec.buttonRight("page:stats", UltraStyle.LABEL + "Combat rating", UltraStyle.STYLE_NORMAL).rowGap();
        }
        // A system Home: the one Back to main menu sits bottom right, like every other Home.
        spec.back();
        spec.send(player);
    }

    /**
     * Tiers list. {@code guide} true: guide Step 2 (step header, Next: Your tier, Back to Step 1, Skip guide).
     * {@code guide} false (4.7.63, guide done): the same list under a plain header, Back to Home.
     */
    private static void openTiers(ServerPlayer player, boolean guide) {
        if (!DifficultyConfig.isEnabled()) {
            openFeatureLocked(
                    player,
                    "tiers",
                    "tiers",
                    "Tiers locked",
                    "Adaptive Difficulty is off on this server. Ask staff to enable it.");
            return;
        }
        if (!SystemGate.allows(player)) {
            openFeatureLocked(
                    player,
                    "tiers",
                    "tiers",
                    "Tiers locked",
                    "This account cannot use personal difficulty. Ask staff to add you to the whitelist.");
            return;
        }
        PlayerDifficultyData data = DifficultyCache.data(player);
        boolean personal = data != null && data.isPersonalEnabled();
        if (data != null) {
            UnlockSystem.syncUnlocks(player, data);
        }
        DifficultySnapshot snap = DifficultyCache.refresh(player);
        int max = snap == null ? 0 : Math.max(0, snap.highestUnlockedTier);
        int active = data == null ? 0 : data.getActiveTier();
        int highestEligible = 0;
        for (int t = 1; t <= 7; t++) {
            UnlockTier ut = UnlockTier.byId(t);
            if (ut != null && UltraDifficultyTierUi.isEligible(player, ut)) {
                highestEligible = t;
            }
        }
        int recommended = DifficultyGuide.recommend(active, highestEligible);
        UltraScreenSpec spec = base(player, guide ? DifficultyTutorial.PAGE_STEP2 : "tiers", "tiers");
        if (guide) {
            stepHeader(spec, 2, "Pick your level");
        } else {
            spec.line(UltraStyle.header("Pick your level"));
        }
        // 4.7.84 (D-2, owner-approved; reverses the 4.7.78 "toggle only on Home" note): with difficulty off, Tiers
        // shows the switch first, then the tier list, in the guide's Step 2 and on the plain Tiers tab alike (D-5).
        if (!personal) {
            spec.line(UltraStyle.danger(guide ? "First, turn difficulty on." : "Turn difficulty on to pick a tier."));
        }
        spec.line(UltraStyle.VALUE + "Tap a tier to see what changes. Nothing is paid until you confirm.");
        // 4.7.57 rule kept: buttons only, no per-tier cost or requirement lines. Every tier opens its
        // detail page; a locked tier is dim but still opens it, where the requirement is spelled out.
        spec.buttonsAfterLines();
        if (!personal) {
            placeDifficultyToggle(spec, false, true);
            spec.rowGap();
        }
        int placed = 0;
        for (int t = 1; t <= 7; t++) {
            UnlockTier ut = UnlockTier.byId(t);
            if (ut == null) {
                continue;
            }
            boolean unlocked = data != null && (data.hasUnlockedTier(t) || t <= max);
            placeTierButton(spec, ut, active, recommended, unlocked || UltraDifficultyTierUi.isEligible(player, ut),
                    placed % 2 == 0);
            if (placed % 2 == 1) {
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        if (!guide && active > 0) {
            // 4.7.78: clearing the active tier lives in the Tiers list (same confirm page and cost warning).
            spec.buttonLeft("page:clear_tier_ask", UltraStyle.danger("Clear active tier"), UltraStyle.STYLE_DANGER)
                    .rowGap();
        }
        if (guide) {
            spec.button("page:" + DifficultyTutorial.PAGE_STEP3, UltraStyle.confirm("Next: Your tier"),
                    UltraStyle.STYLE_CONFIRM).rowGap();
            spec.footerGuide("page:" + DifficultyTutorial.PAGE_STEP1, "Step 1", GUIDE_DONE);
        } else {
            spec.back();
        }
        spec.send(player);
    }

    /**
     * Tier button (4.7.59): {@code T2 Enhanced - You are here} on your tier, {@code - Recommended} on the
     * recommended one, dim {@code - Locked} when the requirement is not met (still opens the detail page).
     */
    private static void placeTierButton(
            UltraScreenSpec spec, UnlockTier ut, int active, int recommended, boolean open, boolean left) {
        int t = ut.id;
        String name = "T" + t + " " + ut.display;
        String label;
        String style;
        if (t == active) {
            label = UltraStyle.plain(name + " - You are here");
            style = UltraStyle.STYLE_NORMAL;
        } else if (!open) {
            label = UltraStyle.dim(name + " - Locked");
            style = UltraStyle.STYLE_DIM;
        } else if (t == recommended) {
            label = UltraStyle.confirm(name + " - Recommended");
            style = UltraStyle.STYLE_CONFIRM;
        } else {
            label = UltraStyle.plain(name);
            style = UltraStyle.STYLE_NORMAL;
        }
        String action = "page:tier_ask:" + t;
        if (left) {
            spec.buttonLeft(action, label, style);
        } else {
            spec.buttonRight(action, label, style);
        }
    }

    private static void openFeatureLocked(
            ServerPlayer player, String page, String tab, String title, String body) {
        UltraScreenSpec spec = base(player, page, tab)
                .line(UltraStyle.danger(title))
                .line(UltraStyle.LABEL + body)
                .buttonsAfterLines();
        if (DifficultyConfig.isEnabled() && SystemGate.allows(player)) {
            placeDifficultyToggle(spec, false);
            spec.rowGap();
        }
        spec.back();
        spec.send(player);
    }

    private static void openSummons(ServerPlayer player) {
        boolean ready = EndDimensionStrength.canOpenSummonMenu(player);
        boolean free = PaidFeatureAccess.bypassAncientCoinCost(player);
        String cost = free
                ? "Free for staff"
                : AncientCoinEconomy.formatExactCost(EndDimensionStrength.summonCopperCost());
        UltraScreenSpec spec = base(player, "summons", "summons")
                // 4.7.59: guide chrome (header, Back in the left slot); bosses work as in 4.7.57.
                .line(UltraStyle.header("Boss summons"))
                // 4.7.84 (D-4): one plain line instead of three.
                .line(UltraStyle.LABEL + "Summon a boss that fights at your tier. Each boss needs a tier range and a"
                        + " place - tap one to see what it needs.")
                .labelValue("Cost", cost)
                .buttonsAfterLines();
        // Boss list: ready state only — per-boss requirements live on the confirm page.
        String dragonLabel = ready ? "End Dragon" : "End Dragon - Locked";
        spec.buttonLeft(
                        "page:end_dragon",
                        ready ? UltraStyle.confirm(dragonLabel) : UltraStyle.dim(dragonLabel),
                        ready ? "confirm" : "dim")
                .rowGap();
        // Wither and Warden: ready / locked from each boss's own gates, same as the dragon button.
        boolean witherReady = PlayerBossSummons.canOpenSummonMenu(player, PlayerBossSummons.Boss.WITHER);
        boolean wardenReady = PlayerBossSummons.canOpenSummonMenu(player, PlayerBossSummons.Boss.WARDEN);
        String witherLabel = witherReady ? "Wither" : "Wither - Locked";
        String wardenLabel = wardenReady ? "Warden" : "Warden - Locked";
        spec.buttonLeft(
                        "page:wither",
                        witherReady ? UltraStyle.confirm(witherLabel) : UltraStyle.dim(witherLabel),
                        witherReady ? "confirm" : "dim")
                .buttonRight(
                        "page:warden",
                        wardenReady ? UltraStyle.confirm(wardenLabel) : UltraStyle.dim(wardenLabel),
                        wardenReady ? "confirm" : "dim")
                .rowGap();
        spec.back();
        spec.send(player);
    }

    private static void openSummonConfirm(ServerPlayer player) {
        boolean ready = EndDimensionStrength.canOpenSummonMenu(player);
        boolean free = PaidFeatureAccess.bypassAncientCoinCost(player);
        String cost = free
                ? "Free for staff"
                : AncientCoinEconomy.formatExactCost(EndDimensionStrength.summonCopperCost());
        UltraScreenSpec spec = sub("end_dragon")
                .line(UltraStyle.header("End Dragon"))
                .labelValue("Cost", cost)
                .labelValue("Status:", ready ? "ready" : "not ready yet");
        for (String line : summonStatusLines(player, ready)) {
            spec.line(line);
        }
        spec.line(UltraStyle.VALUE + EndDimensionStrength.summonLootBonusLine(player));
        if (!ready) {
            spec.line(UltraStyle.dim("Do the steps above, then come back to summon."));
        }
        spec.buttonsAfterLines();
        // 4.7.57 (LM54-C4): a summon that cannot succeed yet shows a dim, inert button.
        if (ready) {
            spec.button("page:summon_ask:end_dragon", UltraStyle.confirm("Summon End Dragon"), UltraStyle.STYLE_CONFIRM).rowGap();
        } else {
            spec.lockedButton("Summon - Locked").rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /**
     * Wither / Warden confirm page, in the End Dragon confirm page's shape: boss, cost, status,
     * and the requirements (tier range, dimension, personal AD, one living per player).
     */
    private static void openBossSummonConfirm(ServerPlayer player, PlayerBossSummons.Boss boss) {
        boolean ready = PlayerBossSummons.canOpenSummonMenu(player, boss);
        String page = boss == PlayerBossSummons.Boss.WITHER ? "wither" : "warden";
        String action = "page:summon_ask:" + page;
        UltraScreenSpec spec = sub(page)
                .line(UltraStyle.header(boss.display))
                .labelValue("Cost", PlayerBossSummons.costText(player, boss)
                        + (PaidFeatureAccess.bypassAncientCoinCost(player)
                                ? ""
                                : " (" + boss.netheriteCoins() + " Ancient Netherite)"))
                .labelValue("Tiers", "T" + boss.minTier + " - T" + boss.maxTier)
                .labelValue("Where", boss.dimensionName)
                .labelValue("Status:", ready ? "ready" : "not ready yet");
        for (String line : PlayerBossSummons.statusLines(player, boss, ready)) {
            spec.line(line);
        }
        if (!ready) {
            spec.line(UltraStyle.dim("Do the steps above, then come back to summon."));
        }
        spec.buttonsAfterLines();
        // 4.7.57 (LM54-C4): a summon that cannot succeed yet shows a dim, inert button.
        if (ready) {
            spec.button(action, UltraStyle.confirm("Summon " + boss.display), UltraStyle.STYLE_CONFIRM).rowGap();
        } else {
            spec.lockedButton("Summon - Locked").rowGap();
        }
        spec.back();
        spec.send(player);
    }

    // ── Confirm pages (4.7.21) ────────────────────────────────────────

    /**
     * After Confirm on a confirm page: back to the page its button sat on. {@code null} when
     * {@code page} is not a confirm page.
     */
    public static String askReturn(String page) {
        String p = page == null ? "" : page.trim().toLowerCase(Locale.ROOT);
        if (p.startsWith("tier_ask:") || "clear_tier_ask".equals(p)) {
            return "tiers";
        }
        if ("title_unequip_ask".equals(p)) {
            return "titles";
        }
        if ("flush_ask".equals(p)) {
            return "eventlog";
        }
        if ("difficulty_off_ask".equals(p)) {
            return "tiers";
        }
        if (p.startsWith("summon_ask:")) {
            String which = p.substring("summon_ask:".length());
            return "end_dragon".equals(which) || "wither".equals(which) || "warden".equals(which) ? which : "summons";
        }
        return null;
    }

    private static String capFirst(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static int parseTier(String raw) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Tier detail page (4.7.57, every tier; 4.7.59 guide): what changes (enemy damage and health, TP, mastery,
     * each as a plain before/after), cost, the requirement in plain words, Momentum in one line, the Confirm
     * button (Pay / Activate / Switch to T#), and "Try one fight free". A blocked change says what is
     * missing and shows a dim, inert button.
     */
    private static void openTierAsk(ServerPlayer player, int t) {
        PlayerDifficultyData data = DifficultyCache.data(player);
        UnlockTier tier = UnlockTier.byId(t);
        if (tier == null || data == null) {
            open(player, "tiers");
            return;
        }
        int active = data.getActiveTier();
        com.dbzlegacy.adaptivedifficulty.service.DifficultyTrial.Active trial =
                com.dbzlegacy.adaptivedifficulty.service.DifficultyTrial.active(player);
        // During a free trial the tier you own is the one the trial replaced.
        int owned = trial != null && trial.tier() == active ? trial.prev() : active;
        DifficultySnapshot snap = DifficultyCache.refresh(player);
        boolean unlocked = data.hasUnlockedTier(t) || (snap != null && t <= snap.highestUnlockedTier);
        boolean eligible = UltraDifficultyTierUi.isEligible(player, tier);
        boolean free = PaidFeatureAccess.bypassAncientCoinCost(player);
        boolean down = owned > 0 && t < owned;
        long cost = free || down ? 0L : AncientCoinEconomy.activationCost(tier, player, owned);
        String heading = owned == t ? "Your tier" : down ? "Switch to a lower tier" : "Move up to this tier";
        UnlockTier now = UnlockTier.byId(owned);
        double before = now == null ? 0.0 : now.enemyScalingMultiplier();
        double after = tier.enemyScalingMultiplier();
        boolean personal = data.isPersonalEnabled();
        boolean open = unlocked || eligible;
        // 4.7.84 (D-3): one row for enemy strength (damage and health always match), plain TP and cost rows, only the
        // coins the player holds, and one unlock row. Mastery gain and the Momentum line are the same on every tier,
        // so they live on Difficulty Home only.
        UltraScreenSpec spec = sub("tier_ask:" + t)
                .line(UltraStyle.header("T" + t + " " + tier.display + " - " + heading))
                .labelValue("Enemies:", DifficultyGuide.strength(before, after))
                .labelValue("TP per kill:", tpPerKill(DifficultyGuide.tp(before, after)))
                .labelValue("Cost:", cost <= 0L ? (free && !down ? "Free for staff" : "Free") : AncientCoinEconomy.formatExactCost(cost))
                .labelValue("You have:", coinsHeld(player))
                .labelValue("Unlocks at:", "level " + tier.requiredDmzLevel() + " or prestige " + tier.requiredPrestige()
                        + (open ? " (you qualify)" : " (not yet)"));
        if (down) {
            spec.line(UltraStyle.dim("Switching down is free. Going back up later costs Ancient Coins again."));
        } else if (owned != t) {
            spec.line(UltraStyle.dim("Coins spent are not refunded. Switching to a lower tier later is free."));
        }
        String block = null;
        if (!personal && open) {
            block = TIER_OFF_LINE;
        } else if (owned == t) {
            block = "T" + t + " is already your tier.";
        } else if (!open) {
            block = "T" + t + " is locked. " + capFirst(UltraDifficultyTierUi.lockedReason(player, tier)) + ".";
        } else if (cost > 0L && !AncientCoinEconomy.canAfford(player, cost)) {
            block = coinShortfallLine(player, cost);
        }
        boolean coinShort = owned != t && open && personal && cost > 0L && !AncientCoinEconomy.canAfford(player, cost);
        if (block != null) {
            spec.line(UltraStyle.danger(block));
        }
        String trialBlock = com.dbzlegacy.adaptivedifficulty.service.DifficultyTrial.blockReason(player, t);
        // Difficulty off is already said above; the trial line only adds a different reason.
        if (trialBlock != null && owned != t && personal) {
            spec.line(UltraStyle.dim("Free trial: " + trialReason(trialBlock)));
        }
        spec.buttonsAfterLines();
        if (coinShort) {
            spec.offerGetMore(UltraStyle.Shortfall.COINS, null);   // 4.7.85 (N6)
        }
        if (!personal) {
            // 4.7.84 (D-2, owner-approved): the switch sits where "Tier locked" used to be. Turning it on is free and
            // instant; the player then picks the tier from the list.
            if (open) {
                spec.buttonWide("do:toggle_personal", UltraStyle.plain(TIER_OFF_BUTTON), UltraStyle.STYLE_CONFIRM, null)
                        .rowGap();
                if (owned != t) {
                    spec.lockedButton("Free trial - Locked").rowGap();
                }
            } else {
                spec.lockedButton("T" + t + " - Locked");
                placeDifficultyToggle(spec, false, false);
                spec.rowGap();
            }
            spec.back();
            spec.send(player);
            return;
        }
        if (block == null) {
            String pay = down ? "Switch to T" + t
                    : cost <= 0L ? "Activate T" + t : "Pay " + AncientCoinEconomy.compactCost(cost);
            spec.buttonLeft("do:activate:" + t, UltraStyle.confirm(UltraStyle.fitsButton(pay) ? pay : "Confirm"),
                    UltraStyle.STYLE_CONFIRM);
        } else if (owned != t) {
            spec.lockedButton(down ? "Switch - Locked" : "T" + t + " - Locked");
        }
        if (owned != t) {
            if (trialBlock == null) {
                spec.buttonRight("do:trial:" + t, UltraStyle.confirm("Try one fight free"), UltraStyle.STYLE_CONFIRM);
            } else {
                spec.lockedButtonRight("Free trial - Locked");
            }
        }
        spec.rowGap();
        spec.back();
        spec.send(player);
    }

    /** 4.7.84 (D-2): the tier page's reason line while difficulty is off (replaces "...the Your tier step first"). */
    static final String TIER_OFF_LINE = "Difficulty is off. Turn it on below, then pick this tier.";
    /** 4.7.84 (D-2): the switch on a tier page the player has unlocked (replaces the dim "Tier locked"). */
    static final String TIER_OFF_BUTTON = "Turn difficulty on to use this tier";

    /**
     * Not enough Ancient Coins for a tier. N6 swap point: one line, so the shared "where to get coins" helper can
     * replace (or follow) it without touching the page layout.
     */
    static String coinShortfallLine(ServerPlayer player, long cost) {
        return UltraStyle.plain(AncientCoinEconomy.missingText(player, cost));
    }

    /** {@code TP per kill: grows with enemy health} (was "TP earned Grows with enemy health"). */
    static String tpPerKill(String tp) {
        return tp != null && tp.startsWith("Grows with enemy health") ? "grows with enemy health" : tp;
    }

    /** {@code Free trial: opens once you meet...}, not {@code Free trial: Free trials open once...}. */
    static String trialReason(String reason) {
        String r = reason == null ? "" : reason.trim();
        return r.startsWith("Free trials open once") ? "opens once" + r.substring("Free trials open once".length()) : r;
    }

    /**
     * 4.7.84 (D-3): the Ancient Coins the player holds, non-zero kinds only, highest first ({@code 3 Gold, 12
     * Copper}), or {@code no Ancient Coins}. Menu only; chat keeps the full per-kind list.
     */
    static String coinsHeld(ServerPlayer player) {
        if (!AncientCoinEconomy.realCoinsAvailable()) {
            return UltraStyle.plain(AncientCoinEconomy.inventoryBreakdown(player));
        }
        AncientCoinEconomy.CoinKind[] kinds = AncientCoinEconomy.CoinKind.values();
        StringBuilder sb = new StringBuilder();
        for (int i = kinds.length - 1; i >= 0; i--) {
            long n = AncientCoinEconomy.countOf(player, kinds[i]);
            if (n > 0L) {
                sb.append(sb.length() == 0 ? "" : ", ").append(n).append(' ').append(kinds[i].display);
            }
        }
        return sb.length() == 0 ? "no Ancient Coins" : sb.toString();
    }

    /** Ask before clearing the active tier (4.7.31): turning a tier back on costs Ancient Coins. */
    private static void openClearTierAsk(ServerPlayer player) {
        PlayerDifficultyData data = DifficultyCache.data(player);
        int active = data == null ? 0 : data.getActiveTier();
        UltraScreenSpec spec = sub("clear_tier_ask")
                .line(UltraStyle.header("Clear active tier"))
                .labelValue("Active tier", active <= 0 ? "No tier active" : "T" + active)
                .notice("Clear your active tier? You will need Ancient Coins to activate a tier again.");
        if (active <= 0) {
            spec.line(UltraStyle.dim("No tier is active, so there is nothing to clear."));
        }
        spec.buttonsAfterLines();
        if (active > 0) {
            spec.button("do:lower_tier:0", UltraStyle.danger("Clear active tier"), UltraStyle.STYLE_DANGER)
                    .rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /** Ask before flushing the event log (4.7.56): it writes out and closes the open log files. */
    private static void openFlushAsk(ServerPlayer player) {
        if (!StaffAccess.isStaff(player)) {
            open(player, "main");
            return;
        }
        UltraScreenSpec spec = sub("flush_ask")
                .line(UltraStyle.header("Flush the event log"))
                .notice("Flush the event log? Recent entries are written out and the log is closed.")
                .buttonsAfterLines();
        spec.button("do:syslog:flush", UltraStyle.danger("Flush the event log"), UltraStyle.STYLE_DANGER).rowGap();
        spec.back();
        spec.send(player);
    }

    /** Ask before turning Difficulty off (4.7.56): it releases scaled mobs and despawns the summoned dragon. */
    private static void openDifficultyOffAsk(ServerPlayer player) {
        PlayerDifficultyData data = DifficultyCache.data(player);
        boolean on = data != null && data.isPersonalEnabled();
        UltraScreenSpec spec = sub("difficulty_off_ask")
                .line(UltraStyle.header("Turn Difficulty off"))
                .notice("Turning Difficulty off releases your scaled mobs and despawns your summoned End Dragon."
                        + " You can turn it back on any time.");
        if (!on) {
            spec.line(UltraStyle.dim("Difficulty is already off."));
        }
        spec.buttonsAfterLines();
        if (on) {
            spec.button("do:toggle_personal", UltraStyle.danger("Turn Difficulty off"), UltraStyle.STYLE_DANGER)
                    .rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /** Ask before unequipping the worn title (4.7.31); equipping it again is free. */
    private static void openUnequipTitleAsk(ServerPlayer player) {
        DifficultyTitle title = DifficultyTitle.byId(TitleSystem.activeId(player));
        UltraScreenSpec spec = sub("title_unequip_ask")
                .line(UltraStyle.header("Unequip title"))
                .labelValue("Equipped", title == null ? "No title equipped" : UltraStyle.plain(title.display))
                .notice("Unequip your title? You can equip it again for free any time.");
        if (title == null) {
            spec.line(UltraStyle.dim("No title is equipped, so there is nothing to unequip."));
        }
        spec.buttonsAfterLines();
        if (title != null) {
            spec.button("do:clear_title", UltraStyle.danger("Unequip title"), UltraStyle.STYLE_DANGER)
                    .rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /** Boss summon confirm: boss, exact cost, coins held, what spawns; Confirm runs the summon. */
    private static void openSummonAsk(ServerPlayer player, String which) {
        boolean dragon = "end_dragon".equals(which);
        PlayerBossSummons.Boss boss = "warden".equals(which) ? PlayerBossSummons.Boss.WARDEN
                : ("wither".equals(which) ? PlayerBossSummons.Boss.WITHER : null);
        if (!dragon && boss == null) {
            openSummons(player);
            return;
        }
        boolean free = PaidFeatureAccess.bypassAncientCoinCost(player);
        String name = dragon ? "End Dragon" : boss.display;
        boolean ready = dragon
                ? EndDimensionStrength.canOpenSummonMenu(player)
                : PlayerBossSummons.canOpenSummonMenu(player, boss);
        String cost = dragon
                ? (free ? "Free for staff" : AncientCoinEconomy.formatExactCost(EndDimensionStrength.summonCopperCost()))
                : PlayerBossSummons.costText(player, boss)
                        + (free ? "" : " (" + boss.netheriteCoins() + " Ancient Netherite)");
        UltraScreenSpec spec = sub("summon_ask:" + which)
                .line(UltraStyle.header("Confirm summon"))
                .labelValue("Boss", name)
                .labelValue("Cost", cost)
                .labelValue("Ancient Coins", AncientCoinEconomy.inventoryBreakdown(player))
                .line(UltraStyle.LABEL + "The " + name + " spawns near you right away. You get its drops"
                        + (dragon ? " and the loot bonus below." : "."));
        if (dragon) {
            spec.line(UltraStyle.VALUE + EndDimensionStrength.summonLootBonusLine(player));
        }
        spec.line(UltraStyle.dim("Coins are taken when it spawns and are not refunded."));
        if (!ready) {
            spec.line(UltraStyle.danger("Requirements not met - the " + name + " page lists what is missing."));
        }
        spec.buttonsAfterLines();
        if (ready) {
            String go = "Summon " + name;
            spec.button(dragon ? "do:summon_end_dragon"
                                    : (boss == PlayerBossSummons.Boss.WITHER ? "do:summon_wither" : "do:summon_warden"),
                            UltraStyle.confirm(UltraStyle.fitsButton(go) ? go : "Confirm summon"), UltraStyle.STYLE_CONFIRM)
                    .rowGap();
        } else {
            // 4.7.81: shown, never hidden; the line above says what is missing.
            spec.lockedButton("Requirements not met").rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /** CNPC {@code summonStatusLines} - Ultra colors, same guidance. */
    private static java.util.List<String> summonStatusLines(ServerPlayer subject, boolean canSummon) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (!DifficultyConfig.get().enableEndDimensionStrength) {
            lines.add(UltraStyle.LABEL + "End strength is turned off. Ask staff to enable it.");
            return lines;
        }
        if (!DifficultyConfig.get().enableEndPlayerDragonSummon) {
            lines.add(UltraStyle.LABEL + "Boss summoning is turned off. Ask staff to turn it on.");
            return lines;
        }
        lines.add(UltraStyle.LABEL + "The dragon matches your difficulty. Only you can damage it.");
        lines.add(
                UltraStyle.LABEL
                        + "You need a tier from "
                        + UltraStyle.VALUE
                        + "T"
                        + EndDimensionStrength.PLAYER_SUMMON_MIN_TIER
                        + UltraStyle.LABEL
                        + " through "
                        + UltraStyle.VALUE
                        + "T"
                        + EndDimensionStrength.PLAYER_SUMMON_MAX_TIER
                        + UltraStyle.LABEL
                        + ", and you must be in The End.");
        if (!canSummon) {
            if (!SystemGate.participates(subject)) {
                lines.add(UltraStyle.LABEL + "Turn difficulty on (Difficulty Home or Tiers) first.");
            } else {
                lines.add(UltraStyle.LABEL + "Activate a tier in that range on the Tiers tab first.");
            }
        } else if (subject != null && !EndDimensionStrength.isTheEnd(subject.m_9236_())) {
            lines.add(UltraStyle.LABEL + "Travel to The End, then confirm the summon.");
        }
        return lines;
    }

    /**
     * One toggle on Difficulty home. No coin submenu. 4.7.81: OFF is still a live control, so it uses the
     * normal style (dim reads as disabled). The caller ends the row.
     */
    private static void placeCoinMessageToggle(UltraScreenSpec spec, boolean on, boolean left) {
        String label = on
                ? UltraStyle.confirm("Coin drop chat - ON")
                : UltraStyle.plain("Coin drop chat - OFF");
        String style = on ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_NORMAL;
        if (left) {
            spec.buttonLeft("do:toggle_coin_chat", label, style);
        } else {
            spec.buttonRight("do:toggle_coin_chat", label, style);
        }
    }

    /** Difficulty on/off in its usual slot: ON on the right, OFF on the left (guide Step 2, locked pages). */
    private static void placeDifficultyToggle(UltraScreenSpec spec, boolean enabled) {
        placeDifficultyToggle(spec, enabled, !enabled);
    }

    /**
     * Difficulty on/off. 4.7.81: OFF is a live control (it turns difficulty on), so it uses the normal style,
     * not dim. The caller ends the row.
     */
    private static void placeDifficultyToggle(UltraScreenSpec spec, boolean enabled, boolean left) {
        // Turning it off asks first (4.7.56): the summoned End Dragon despawns.
        String action = enabled ? "page:difficulty_off_ask" : "do:toggle_personal";
        String label = enabled ? UltraStyle.confirm("Difficulty - ON") : UltraStyle.plain("Difficulty - OFF");
        String style = enabled ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_NORMAL;
        if (left) {
            spec.buttonLeft(action, label, style);
        } else {
            spec.buttonRight(action, label, style);
        }
    }

    private static void openTitles(ServerPlayer player, int pageIndex) {
        DifficultyTitle[] all = DifficultyTitle.values();
        int pageSize = TITLES_PER_PAGE;
        int pages = Math.max(1, (all.length + pageSize - 1) / pageSize);
        int page = Math.min(pages - 1, Math.max(0, pageIndex));
        String active = TitleSystem.activeId(player);
        UltraScreenSpec spec = sub("titles_p" + page)
                .labelValue("Equipped", blank(TitleSystem.activeDisplay(player)))
                .labelValue("Title score", String.valueOf(TitleSystem.computeTitleScore(player)))
                .line(UltraStyle.LABEL + "Equip a title you have unlocked.")
                .labelValue("Titles page", (page + 1) + "/" + pages)
                .buttonsAfterLines();
        int from = page * pageSize;
        int placed = 0;
        for (int i = from; i < all.length && placed < pageSize; i++) {
            DifficultyTitle t = all[i];
            if (t == null) {
                continue;
            }
            boolean earned = TitleSystem.has(player, t);
            boolean on = t.id != null && t.id.equals(active);
            String label = on
                    ? UltraStyle.confirm(statusCaption(t.display, "Equipped"))
                    : (earned ? t.display : UltraStyle.dim(statusCaption(t.display, "Locked")));
            String style = on ? "confirm" : (earned ? "normal" : "dim");
            String action = "page:title_detail:" + t.id;
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
        if (page > 0) {
            spec.buttonLeft(
                    "page:titles_p" + (page - 1),
                    UltraStyle.LABEL + "Prev titles page",
                    UltraStyle.STYLE_NORMAL);
        }
        if (page + 1 < pages) {
            if (page > 0) {
                spec.buttonRight(
                        "page:titles_p" + (page + 1),
                        UltraStyle.LABEL + "Next titles page",
                        UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonLeft(
                        "page:titles_p" + (page + 1),
                        UltraStyle.LABEL + "Next titles page",
                        UltraStyle.STYLE_NORMAL);
            }
            spec.rowGap();
        } else if (page > 0) {
            spec.rowGap();
        }
        PlayerDifficultyData data = DifficultyCache.data(player);
        boolean sense = data != null && data.titleProgress().titleSenseChat();
        spec.buttonLeft(
                        "do:toggle_title_sense",
                        sense ? UltraStyle.confirm("Title Sense - ON") : UltraStyle.plain("Title Sense - OFF"),
                        sense ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_NORMAL)
                .buttonRight("page:title_unequip_ask", UltraStyle.danger("Unequip title"), UltraStyle.STYLE_DANGER)
                .rowGap();
        spec.back();
        spec.send(player);
    }

    private static void openTitleDetail(ServerPlayer player, String titleIdRaw) {
        DifficultyTitle title = DifficultyTitle.byId(titleIdRaw);
        UltraScreenSpec spec = sub("title_detail:" + (titleIdRaw == null ? "" : titleIdRaw));
        if (title == null) {
            spec.line(UltraStyle.dim("Couldn't find that - try refreshing the list."))
                    .buttonsAfterLines();
            spec.back();
            spec.send(player);
            return;
        }
        boolean earned = TitleSystem.has(player, title);
        boolean equipped = title.id.equals(TitleSystem.activeId(player));
        int mastery = DifficultyCache.data(player).titleProgress().masteryLevel(title.id);
        spec.line(UltraStyle.header(title.masteryDisplay(mastery)))
                .labelValue("Kind", kindLabel(title.kind))
                .line(earned
                        ? UltraStyle.confirm("You have this title.")
                        : UltraStyle.dim("You do not have this yet."))
                .line(equipped ? UltraStyle.confirm("Currently equipped.") : "")
                .line(UltraStyle.LABEL + "How to earn " + UltraStyle.VALUE + humanRequirement(title))
                .line(UltraStyle.LABEL + "Bonus when equipped " + UltraStyle.VALUE
                        + UltraStyle.plain(title.perkTip(mastery)))
                .labelValue("Title score", "+" + title.scorePoints)
                // What the title stands for - added after the existing lines so none of them move.
                .line(UltraStyle.LABEL + "About " + UltraStyle.VALUE + title.description);
        if (!earned) {
            spec.line(UltraStyle.dim("Equip unlocks after you earn the title."));
        }
        spec.buttonsAfterLines();
        if (earned && !equipped) {
            spec.buttonLeft("do:equip_title:" + title.id, UltraStyle.confirm("Equip this title"), UltraStyle.STYLE_CONFIRM)
                    .rowGap();
        } else if (earned) {
            spec.buttonLeft("do:equip_title:" + title.id, UltraStyle.dim("Already equipped"), UltraStyle.STYLE_DIM)
                    .rowGap();
        }
        spec.back();
        spec.send(player);
    }

    private static String kindLabel(DifficultyTitle.Kind kind) {
        if (kind == null) {
            return "-";
        }
        return switch (kind) {
            case TIER -> "Tier milestone";
            case COMBAT -> "Combat challenge";
            case CHALLENGE -> "Special challenge";
        };
    }

    private static String humanRequirement(DifficultyTitle title) {
        if (title == null) {
            return "-";
        }
        return UltraStyle.plain(title.requirementTip())
                .replace("DMZ ", "Level ")
                .replace(" or Prestige ", " or prestige ");
    }

    private static void openStats(ServerPlayer player) {
        if (!StaffAccess.isStaff(player)) {
            open(player, "main");
            return;
        }
        DifficultySnapshot snap = DifficultyCache.refresh(player);
        PlayerCombatProfile profile = PlayerCombatProfile.of(player);
        Map<String, String> ph = new HashMap<>();
        LmOverhaulScaledCombat.putPlaceholders(ph, player);
        long cr = snap == null ? 0L : (snap.combatRating > 0 ? snap.combatRating : snap.calculated);
        UltraScreenSpec spec = sub("stats")
                .line(UltraStyle.header("Combat rating and scaled stats"))
                .labelValue(
                        "Tier",
                        (snap == null ? "-" : snap.activeTierName)
                                + (snap == null ? "" : " (" + words(String.valueOf(snap.state())) + ")"))
                .labelValue(
                        "Combat rating",
                        String.format(Locale.ROOT, "%,d", cr)
                                + " - unlocked up to T"
                                + (snap == null ? 0 : snap.highestUnlockedTier))
                .labelValue(
                        "Level",
                        (snap == null ? "-" : String.valueOf(snap.dmzLevel))
                                + " - Prestige "
                                + (snap == null ? "-" : String.valueOf(snap.prestige)))
                .labelValue("Title", blankTitle(TitleSystem.activeDisplay(player)))
                .labelValue(
                        "Scaled stats (Overhaul " + ph.getOrDefault("overhaul_scale", "x1") + ")",
                        "Melee " + ph.getOrDefault("melee_scaled", "-")
                                + " - Strike " + ph.getOrDefault("strike_scaled", "-"))
                .labelValue(
                        "Ki / Defense",
                        ph.getOrDefault("ki_scaled", "-") + " / " + ph.getOrDefault("defense_scaled", "-"))
                .labelValue(
                        "Class / Style",
                        blank(profile.fightingClass)
                                + " / "
                                + words(profile.style == null ? "HYBRID" : profile.style.name()))
                .labelValue("Top stats", blank(profile.topStatsLabel()))
                .labelValue("Ancient Coins", AncientCoinEconomy.inventoryBreakdown(player))
                .buttonsAfterLines();
        spec.back();
        spec.send(player);
    }

    private static void openStaffAdmin(ServerPlayer player) {
        if (!StaffAccess.isStaff(player)) {
            open(player, "main");
            return;
        }
        UltraScreenSpec spec = sub("admin")
                .line(UltraStyle.LABEL + "Server tools - typed admin: /difficulty admin")
                .line(UltraStyle.LABEL + "Reload config, event log, and LM Staff Admin hub")
                .buttonsAfterLines();
        spec.buttonLeft("do:reload", UltraStyle.confirm("Reload Legacy Mechanics config"), UltraStyle.STYLE_CONFIRM)
                .buttonRight("page:stats", UltraStyle.LABEL + "Staff details", UltraStyle.STYLE_NORMAL)
                .rowGap();
        spec.buttonLeft("open:admin", UltraStyle.LABEL + "LM Staff Admin", UltraStyle.STYLE_NORMAL)
                .buttonRight("page:eventlog", UltraStyle.LABEL + "Event log", UltraStyle.STYLE_NORMAL)
                .rowGap();
        spec.back();
        spec.send(player);
    }

    private static void openEventLog(ServerPlayer player) {
        if (!StaffAccess.isStaff(player)) {
            open(player, "main");
            return;
        }
        UltraScreenSpec spec = sub("eventlog")
                .line(UltraStyle.header("Unified system event log"))
                .line(UltraStyle.LABEL + "Use the toggles, or typed: /lm admin syslog ...")
                .buttonsAfterLines();
        boolean logOn = DifficultyConfig.get().enableSystemTelemetry;
        spec.buttonLeft("do:syslog:status", UltraStyle.LABEL + "Status", UltraStyle.STYLE_NORMAL)
                .buttonRight(
                        "do:syslog:on",
                        logOn ? UltraStyle.confirm("Event log - ON") : "Event log - ON",
                        logOn ? "confirm" : UltraStyle.STYLE_NORMAL)
                .rowGap();
        spec.buttonLeft(
                        "do:syslog:off",
                        logOn
                                ? UltraStyle.danger("Event log - OFF")
                                : UltraStyle.dim("Event log - OFF"),
                        logOn ? "danger" : "dim")
                .buttonRight("page:flush_ask", UltraStyle.danger("Flush"), UltraStyle.STYLE_DANGER)
                .rowGap();
        spec.back();
        spec.send(player);
    }

    /**
     * 4.7.84 startup self-test (wire it from UltraScreenSelfTest): the plain-copy helpers and the tier-page switch.
     */
    public static void selfTest(java.util.List<String> broken) {
        if (!"Each difficulty kill saves 10s. While you have time saved, hits give +50% mastery."
                .equals(momentumHowLine(10, 1.5)) || !"Each difficulty kill saves 10s.".equals(momentumHowLine(10, 1.0))) {
            broken.add("Difficulty: Momentum line should read 'Each difficulty kill saves 10s. ...', got "
                    + momentumHowLine(10, 1.5));
        }
        if (!"opens once you meet this tier's requirement.".equals(
                trialReason("Free trials open once you meet this tier's requirement."))) {
            broken.add("Difficulty: the free trial reason must not say 'Free trial' twice");
        }
        if (!"grows with enemy health".equals(tpPerKill("Grows with enemy health"))) {
            broken.add("Difficulty: TP per kill should read 'grows with enemy health'");
        }
        if (UltraStyle.textWidthPx(TIER_OFF_BUTTON) > UltraStyle.labelMaxWidth(UltraStyle.BTN_W * 2 + UltraStyle.BTN_GAP)) {
            broken.add("Difficulty: '" + TIER_OFF_BUTTON + "' must fit the wide tier-page button");
        }
        if (TIER_OFF_LINE.contains("Your tier step")) {
            broken.add("Difficulty: the tier page must not point to the Your tier step for the switch");
        }
    }

    /** {@code SOME_ENUM} -> {@code Some enum} for staff pages (4.7.56). */
    private static String words(String raw) {
        String s = raw == null ? "" : raw.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
        return s.isEmpty() ? "-" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** {@code Name - Status}, or the name alone when that does not fit a button (4.7.56). */
    private static String statusCaption(String name, String status) {
        String full = name + " - " + status;
        return UltraStyle.fitsButton(full) ? full : UltraStyle.buttonText(name);
    }

    private static String blank(String s) {
        if (s == null || s.isBlank() || "None".equalsIgnoreCase(s.trim())) {
            return "-";
        }
        return s;
    }

    /** Active title for Difficulty home — em dash when unequipped (ASCII "-" on the wire). */
    private static String blankTitle(String s) {
        return blank(s);
    }
}
