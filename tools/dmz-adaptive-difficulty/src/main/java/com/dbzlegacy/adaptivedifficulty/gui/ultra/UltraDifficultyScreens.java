package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons;
import com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache;
import com.dbzlegacy.adaptivedifficulty.calc.DifficultySnapshot;
import com.dbzlegacy.adaptivedifficulty.calc.PlayerCombatProfile;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy;
import com.dbzlegacy.adaptivedifficulty.data.PlayerDifficultyData;
import com.dbzlegacy.adaptivedifficulty.data.TeamMode;
import com.dbzlegacy.adaptivedifficulty.gui.DifficultyTeamGuiApi;
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
 * Difficulty - Home / Tiers / Summons (plus staff-only Combat Rating tab). Sub-pages cover
 * teams, titles, and staff tools. Coin messages are a home-page toggle. Weights live under Sparring.
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
        switch (lower) {
            case "tiers", "buy", "tier", "purchase", "unlock", "adjust", "change", "set", "lower" ->
                    openTiers(player);
            case "summons", "summon" -> openSummons(player);
            case "end_dragon", "dragon", "summon_dragon", "summon_confirm" -> openSummonConfirm(player);
            case "wither" -> openBossSummonConfirm(player, PlayerBossSummons.Boss.WITHER);
            case "warden" -> openBossSummonConfirm(player, PlayerBossSummons.Boss.WARDEN);
            case "team", "teams" -> openTeam(player);
            case "titles", "title", "titles_p0" -> openTitles(player, 0);
            case "settings", "coins" -> openHome(player);
            case "stats", "statistics", "details", "cr", "combat_rating" -> openStats(player);
            case "admin", "staff" -> openStaffAdmin(player);
            case "eventlog", "event_log", "logs", "syslog" -> openEventLog(player);
            default -> openHome(player);
        }
    }

    private static UltraScreenSpec base(ServerPlayer player, String page, String tab) {
        UltraScreenSpec spec = UltraScreenSpec.of("difficulty")
                .title("Difficulty")
                .page(page)
                .activeTab(tab);
        if (StaffAccess.isStaff(player)) {
            // Combat Rating is staff-only — omitted from the tab row for players.
            // Short label: 4-up tabs need <=10 chars or KaiPath truncates with mojibake.
            spec.tabs("home|Home", "tiers|Tiers", "summons|Summons", "stats|CR|staff");
        } else {
            spec.tabs("home|Home", "tiers|Tiers", "summons|Summons");
        }
        return spec;
    }

    /** Subpage: no tab row — dual footer (Back + Main menu). */
    private static UltraScreenSpec sub(String page) {
        return UltraScreenSpec.of("difficulty")
                .title("Difficulty")
                .page(page);
    }

    /** Overview - where you stand, titles, rival teams, coins. Not a copy of Tiers/Summons. */
    private static void openHome(ServerPlayer player) {
        DifficultySnapshot snap = DifficultyCache.refresh(player);
        PlayerDifficultyData data = DifficultyCache.data(player);
        boolean personal = data != null && data.isPersonalEnabled();
        String tierName = snap == null || snap.activeTier <= 0
                ? "No tier active yet. Open the Tiers tab."
                : snap.activeTierName;
        UltraScreenSpec spec = base(player, "main", "home")
                .labelValue("Personal difficulty", personal ? "On" : "Off")
                .labelValue("Active tier", tierName)
                .labelValue("Title", blankTitle(TitleSystem.activeDisplay(player)))
                .labelValue("Ancient Coins", AncientCoinEconomy.inventoryBreakdown(player))
                .line(UltraStyle.LABEL + "Where you stand, your titles, and rival teams.")
                .buttonsAfterLines();
        UltraPickHelper.placePageButtons(spec, "titles|Titles", "team|Rival teams");
        placeCoinMessageToggle(spec, data != null && data.isCoinDropChat());
        if (StaffAccess.isStaff(player)) {
            // Staff entry reads red, like Staff Admin on the Rival and Sparring homes.
            spec.buttonLeft("page:admin", UltraStyle.danger("Server tools"), UltraStyle.STYLE_DANGER).rowGap();
        }
        spec.footerBackToMain();
        spec.send(player);
    }

    private static void openTiers(ServerPlayer player) {
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
        if (data == null || !data.isPersonalEnabled()) {
            UltraScreenSpec spec = base(player, "tiers", "tiers")
                    .line(UltraStyle.LABEL + "Difficulty is off.")
                    .line(UltraStyle.LABEL + "Turn Difficulty on, then pick a tier.")
                    .line(UltraStyle.LABEL + "Activation cost scales with your level.")
                    .buttonsAfterLines();
            placeDifficultyToggle(spec, false);
            spec.rowGap();
            spec.footerBackToMain();
            spec.send(player);
            return;
        }
        UnlockSystem.syncUnlocks(player, data);
        DifficultySnapshot snap = DifficultyCache.refresh(player);
        int max = snap == null ? 0 : Math.max(0, snap.highestUnlockedTier);
        int active = data.getActiveTier();
        UltraScreenSpec spec = base(player, "tiers", "tiers");
        if (active <= 0) {
            spec.line(UltraStyle.dim("No tier active yet. Pick one below."));
        } else {
            spec.labelValue("Active tier", snap == null ? "T" + active : snap.activeTierName);
        }
        spec.labelValue("Unlocked up to", "T" + max)
                .labelValue("Ancient Coins", AncientCoinEconomy.inventoryBreakdown(player))
                .line(UltraStyle.LABEL + "Scaled mobs can hurt other players nearby.")
                .line(UltraStyle.LABEL + "Upgrades cost Ancient Coins - downgrades are free")
                .line(UltraStyle.LABEL + "Click again within 10 seconds to confirm");
        if (!PaidFeatureAccess.bypassAncientCoinCost(player)) {
            spec.line(UltraStyle.LABEL + "Upgrade cost scales with your level");
        }
        UnlockTier nextLocked = UnlockTier.byId(max + 1);
        if (nextLocked != null) {
            spec.labelValue("Next tier", "T" + nextLocked.id + " - " + UltraStyle.plain(UltraDifficultyTierUi.humanRequirement(nextLocked)))
                    .labelValue(
                            "Cost to activate",
                            UltraStyle.plain(UltraDifficultyTierUi.formatActivationCost(player, nextLocked)));
        }
        spec.buttonsAfterLines();
        for (int t = 1; t <= 7; t += 2) {
            placeTierButton(spec, player, data, max, active, t, true);
            placeTierButton(spec, player, data, max, active, t + 1, false);
            spec.rowGap();
        }
        spec.buttonLeft("do:lower_tier:0", UltraStyle.danger("Clear active tier"), "danger");
        placeDifficultyToggle(spec, data.isPersonalEnabled());
        spec.rowGap();
        spec.footerBackToMain();
        spec.send(player);
    }

    private static void placeTierButton(
            UltraScreenSpec spec,
            ServerPlayer player,
            PlayerDifficultyData data,
            int max,
            int active,
            int t,
            boolean left) {
        UnlockTier ut = UnlockTier.byId(t);
        if (ut == null) {
            return;
        }
        boolean unlocked = data.hasUnlockedTier(t) || t <= max;
        boolean eligible = UltraDifficultyTierUi.isEligible(player, ut);
        String rawLabel = UltraDifficultyTierUi.tierActionLabel(player, t, active, unlocked, eligible);
        String label = UltraStyle.plain(rawLabel);
        boolean enabled = UltraDifficultyTierUi.tierButtonEnabled(t, active, unlocked, eligible);
        String style;
        String action;
        if (active == t) {
            style = "confirm";
            label = UltraStyle.confirm(label);
            action = "do:activate:" + t;
        } else if (enabled) {
            style = "confirm";
            label = UltraStyle.confirm(label);
            action = "do:activate:" + t;
        } else {
            style = "dim";
            label = UltraStyle.dim(label.isBlank() ? ("T" + t + " - locked") : label);
            action = "do:activate:" + t;
        }
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
        spec.footerBackToMain();
        spec.send(player);
    }

    private static void openSummons(ServerPlayer player) {
        boolean ready = EndDimensionStrength.canOpenSummonMenu(player);
        boolean free = PaidFeatureAccess.bypassAncientCoinCost(player);
        String cost = free
                ? "Free for staff"
                : AncientCoinEconomy.formatExactCost(EndDimensionStrength.summonCopperCost());
        UltraScreenSpec spec = base(player, "summons", "summons")
                .line(UltraStyle.LABEL + "Each boss needs a personal difficulty tier to summon.")
                .line(UltraStyle.LABEL
                        + "Different bosses unlock at different tiers - open a boss for its requirements.")
                .line(UltraStyle.LABEL + "Select a boss, then confirm.")
                .labelValue("Cost", cost)
                .buttonsAfterLines();
        // Boss list: ready state only — per-boss requirements live on the confirm page.
        String dragonLabel = ready ? "End Dragon" : "Dragon locked";
        spec.buttonLeft(
                        "page:end_dragon",
                        ready ? UltraStyle.confirm(dragonLabel) : UltraStyle.dim(dragonLabel),
                        ready ? "confirm" : "dim")
                .rowGap();
        // Wither and Warden: ready / locked from each boss's own gates, same as the dragon button.
        boolean witherReady = PlayerBossSummons.canOpenSummonMenu(player, PlayerBossSummons.Boss.WITHER);
        boolean wardenReady = PlayerBossSummons.canOpenSummonMenu(player, PlayerBossSummons.Boss.WARDEN);
        String witherLabel = witherReady ? "Wither" : "Wither locked";
        String wardenLabel = wardenReady ? "Warden" : "Warden locked";
        spec.buttonLeft(
                        "page:wither",
                        witherReady ? UltraStyle.confirm(witherLabel) : UltraStyle.dim(witherLabel),
                        witherReady ? "confirm" : "dim")
                .buttonRight(
                        "page:warden",
                        wardenReady ? UltraStyle.confirm(wardenLabel) : UltraStyle.dim(wardenLabel),
                        wardenReady ? "confirm" : "dim")
                .rowGap();
        spec.footerBackToMain();
        spec.send(player);
    }

    private static void openSummonConfirm(ServerPlayer player) {
        boolean ready = EndDimensionStrength.canOpenSummonMenu(player);
        boolean free = PaidFeatureAccess.bypassAncientCoinCost(player);
        String cost = free
                ? "Free for staff"
                : AncientCoinEconomy.formatExactCost(EndDimensionStrength.summonCopperCost());
        UltraScreenSpec spec = sub("end_dragon")
                .line(UltraStyle.LABEL + "End Dragon")
                .labelValue("Cost", cost)
                .labelValue("Status", ready ? "Ready" : "Requirements not met");
        for (String line : summonStatusLines(player, ready)) {
            spec.line(line);
        }
        spec.line(UltraStyle.VALUE + EndDimensionStrength.summonLootBonusLine(player));
        if (!ready) {
            spec.line(UltraStyle.dim("Select End Dragon first - clear the notes above, then return."));
        }
        spec.buttonsAfterLines();
        if (ready) {
            spec.button("do:summon_end_dragon", UltraStyle.confirm("Confirm summon"), "confirm").rowGap();
        } else {
            spec.button("do:summon_end_dragon", UltraStyle.dim("Summon locked"), "dim")
                    .rowGap();
        }
        spec.footerBack("page:summons", "Summons");
        spec.send(player);
    }

    /**
     * Wither / Warden confirm page, in the End Dragon confirm page's shape: boss, cost, status,
     * and the requirements (tier range, dimension, personal AD, one living per player).
     */
    private static void openBossSummonConfirm(ServerPlayer player, PlayerBossSummons.Boss boss) {
        boolean ready = PlayerBossSummons.canOpenSummonMenu(player, boss);
        String page = boss == PlayerBossSummons.Boss.WITHER ? "wither" : "warden";
        String action = boss == PlayerBossSummons.Boss.WITHER ? "do:summon_wither" : "do:summon_warden";
        UltraScreenSpec spec = sub(page)
                .line(UltraStyle.LABEL + boss.display)
                .labelValue("Cost", PlayerBossSummons.costText(player, boss)
                        + (PaidFeatureAccess.bypassAncientCoinCost(player)
                                ? ""
                                : " (" + boss.netheriteCoins() + " Ancient Netherite)"))
                .labelValue("Tiers", "T" + boss.minTier + " - T" + boss.maxTier)
                .labelValue("Where", boss.dimensionName)
                .labelValue("Status", ready ? "Ready" : "Requirements not met");
        for (String line : PlayerBossSummons.statusLines(player, boss, ready)) {
            spec.line(line);
        }
        if (!ready) {
            spec.line(UltraStyle.dim("Select " + boss.display + " first - clear the notes above, then return."));
        }
        spec.buttonsAfterLines();
        if (ready) {
            spec.button(action, UltraStyle.confirm("Confirm summon"), "confirm").rowGap();
        } else {
            spec.button(action, UltraStyle.dim("Summon locked"), "dim").rowGap();
        }
        spec.footerBack("page:summons", "Summons");
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
                lines.add(UltraStyle.LABEL + "Turn on personal difficulty on the Tiers tab first.");
            } else {
                lines.add(UltraStyle.LABEL + "Activate a tier in that range on the Tiers tab first.");
            }
        } else if (subject != null && !EndDimensionStrength.isTheEnd(subject.m_9236_())) {
            lines.add(UltraStyle.LABEL + "Travel to The End, then confirm the summon.");
        }
        return lines;
    }

    private static void openTeam(ServerPlayer player) {
        if (!DifficultyConfig.isEnabled()) {
            openTeamNotice(
                    player,
                    "Teams locked",
                    "Adaptive Difficulty is off. Ask staff to enable it before using team scaling.");
            return;
        }
        if (!SystemGate.allows(player)) {
            openTeamNotice(
                    player,
                    "Teams locked",
                    "You cannot use personal difficulty yet. Ask staff to add you to the whitelist.");
            return;
        }
        PlayerDifficultyData data = DifficultyCache.data(player);
        if (data == null || !data.isPersonalEnabled()) {
            openTeamNotice(
                    player,
                    "Teams locked",
                    "Turn Difficulty on, then return here to pick a team mode.");
            return;
        }
        TeamMode mode = data.getTeamMode();
        UltraScreenSpec spec = sub("team")
                .line(UltraStyle.LABEL + "How rival teams affect scaling");
        for (String line : DifficultyTeamGuiApi.linesForPage(player, "team")) {
            if (line != null && !line.isBlank()) {
                spec.line(line);
            }
        }
        spec.buttonsAfterLines();
        spec.buttonLeft(
                        "do:mode:personal",
                        mode == TeamMode.PERSONAL_ONLY
                                ? UltraStyle.confirm("Personal - on")
                                : UltraStyle.dim("Personal"),
                        mode == TeamMode.PERSONAL_ONLY ? "confirm" : "dim")
                .buttonRight(
                        "do:mode:threshold",
                        mode == TeamMode.THRESHOLD_BONUS_ONLY
                                ? UltraStyle.confirm("Threshold - on")
                                : UltraStyle.dim("Threshold"),
                        mode == TeamMode.THRESHOLD_BONUS_ONLY ? "confirm" : "dim")
                .rowGap();
        spec.buttonLeft(
                        "do:mode:full",
                        mode == TeamMode.FULL_TEAM_SCALING
                                ? UltraStyle.confirm("Full team - on")
                                : UltraStyle.dim("Full team"),
                        mode == TeamMode.FULL_TEAM_SCALING ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_DIM)
                .rowGap();
        // "Home" fits the footer button. "Difficulty Home" truncates to "Back to Difficulty...".
        spec.footerBackHome("Difficulty Home");
        spec.send(player);
    }

    /**
     * Difficulty on/off. OFF is dim (dark gray), never confirm. This is not a team mode.
     * The Rival teams mode page does not show it; Personal / Threshold / Full team stay there.
     */
    /** One toggle on Difficulty home. No coin submenu. */
    private static void placeCoinMessageToggle(UltraScreenSpec spec, boolean on) {
        spec.button(
                        "do:toggle_coin_chat",
                        on
                                ? UltraStyle.confirm("Coin messages - ON")
                                : UltraStyle.dim("Coin messages - OFF"),
                        on ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_DIM)
                .rowGap();
    }

    private static void placeDifficultyToggle(UltraScreenSpec spec, boolean enabled) {
        if (enabled) {
            spec.buttonRight(
                    "do:toggle_personal",
                    UltraStyle.confirm("Difficulty - ON"),
                    UltraStyle.STYLE_CONFIRM);
            return;
        }
        spec.buttonLeft(
                "do:toggle_personal",
                UltraStyle.dim("Difficulty - OFF"),
                UltraStyle.STYLE_DIM);
    }

    /**
     * Rival teams is a sub-page (no tab row). Locked and open states both use the standard
     * footer: contextual back on the left, main menu on the right.
     */
    private static void openTeamNotice(ServerPlayer player, String title, String body) {
        UltraScreenSpec spec = sub("team")
                .line(UltraStyle.danger(title))
                .line(UltraStyle.LABEL + body)
                .buttonsAfterLines();
        if (DifficultyConfig.isEnabled() && SystemGate.allows(player)) {
            placeDifficultyToggle(spec, false);
            spec.rowGap();
        }
        spec.footerBackHome("Difficulty Home");
        spec.send(player);
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
                    ? UltraStyle.confirm(t.display + " on")
                    : (earned ? t.display : UltraStyle.dim(t.display + " lock"));
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
                    UltraStyle.dim("Prev titles page"),
                    UltraStyle.STYLE_DIM);
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
                        sense ? UltraStyle.confirm("Title Sense - ON") : UltraStyle.dim("Title Sense - OFF"),
                        sense ? "confirm" : "dim")
                .buttonRight("do:clear_title", UltraStyle.danger("Unequip title"), "danger")
                .rowGap();
        spec.footerBackHome("Difficulty Home");
        spec.send(player);
    }

    private static void openTitleDetail(ServerPlayer player, String titleIdRaw) {
        DifficultyTitle title = DifficultyTitle.byId(titleIdRaw);
        UltraScreenSpec spec = sub("title_detail:" + (titleIdRaw == null ? "" : titleIdRaw));
        if (title == null) {
            spec.line(UltraStyle.dim("Couldn't find that - try refreshing the list."))
                    .buttonsAfterLines();
            spec.footerBack("page:titles_p0", "Titles");
            spec.send(player);
            return;
        }
        boolean earned = TitleSystem.has(player, title);
        boolean equipped = title.id.equals(TitleSystem.activeId(player));
        int mastery = DifficultyCache.data(player).titleProgress().masteryLevel(title.id);
        spec.line(UltraStyle.LABEL + title.masteryDisplay(mastery))
                .labelValue("Kind", kindLabel(title.kind))
                .line(earned
                        ? UltraStyle.confirm("You have this title.")
                        : UltraStyle.dim("You do not have this yet."))
                .line(equipped ? UltraStyle.confirm("Currently equipped.") : "")
                .line(UltraStyle.LABEL + "How to earn " + UltraStyle.VALUE + humanRequirement(title))
                .line(UltraStyle.LABEL + "Bonus when worn " + UltraStyle.VALUE
                        + UltraStyle.plain(title.perkTip(mastery)))
                .labelValue("Title score", "+" + title.scorePoints)
                // What the title stands for - added after the existing lines so none of them move.
                .line(UltraStyle.LABEL + "About " + UltraStyle.VALUE + title.description);
        if (!earned) {
            spec.line(UltraStyle.dim("Equip unlocks after you earn the title."));
        }
        spec.buttonsAfterLines();
        if (earned && !equipped) {
            spec.buttonLeft("do:equip_title:" + title.id, UltraStyle.confirm("Equip this title"), "confirm")
                    .rowGap();
        } else if (earned) {
            spec.buttonLeft("do:equip_title:" + title.id, UltraStyle.dim("Already equipped"), UltraStyle.STYLE_DIM)
                    .rowGap();
        }
        spec.footerBack("page:titles_p0", "Titles");
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
            openHome(player);
            return;
        }
        DifficultySnapshot snap = DifficultyCache.refresh(player);
        PlayerCombatProfile profile = PlayerCombatProfile.of(player);
        Map<String, String> ph = new HashMap<>();
        LmOverhaulScaledCombat.putPlaceholders(ph, player);
        long cr = snap == null ? 0L : (snap.combatRating > 0 ? snap.combatRating : snap.calculated);
        UltraScreenSpec spec = base(player, "stats", "stats")
                .line(UltraStyle.LABEL + "Combat rating and scaled stats")
                .labelValue(
                        "Tier",
                        (snap == null ? "-" : snap.activeTierName)
                                + " - State "
                                + (snap == null ? "-" : snap.state()))
                .labelValue(
                        "Combat Rating",
                        String.valueOf(cr)
                                + " - Unlocked T"
                                + (snap == null ? 0 : snap.highestUnlockedTier))
                .labelValue(
                        "Level",
                        (snap == null ? "-" : String.valueOf(snap.dmzLevel))
                                + " - Prestige "
                                + (snap == null ? "-" : String.valueOf(snap.prestige)))
                .labelValue("Title", blankTitle(TitleSystem.activeDisplay(player)))
                .labelValue(
                        "Overhaul",
                        ph.getOrDefault("overhaul_scale", "x1")
                                + " - Melee "
                                + ph.getOrDefault("melee_scaled", "?")
                                + " - Strike "
                                + ph.getOrDefault("strike_scaled", "?"))
                .labelValue(
                        "Ki / Defense",
                        ph.getOrDefault("ki_scaled", "?") + " / " + ph.getOrDefault("defense_scaled", "?"))
                .labelValue(
                        "Class / Style",
                        blank(profile.fightingClass)
                                + " / "
                                + (profile.style == null ? "HYBRID" : profile.style.name()))
                .labelValue("Top stats", blank(profile.topStatsLabel()))
                .labelValue("Ancient Coins", AncientCoinEconomy.inventoryBreakdown(player))
                .buttonsAfterLines();
        spec.footerBackToMain();
        spec.send(player);
    }

    private static void openStaffAdmin(ServerPlayer player) {
        if (!StaffAccess.isStaff(player)) {
            openHome(player);
            return;
        }
        UltraScreenSpec spec = sub("admin")
                .line(UltraStyle.LABEL + "Server tools - typed admin: /difficulty admin")
                .line(UltraStyle.LABEL + "Reload config, event log, and LM Staff Admin hub")
                .buttonsAfterLines();
        spec.buttonLeft("do:reload", UltraStyle.confirm("Reload LM config"), "confirm")
                .buttonRight("page:stats", UltraStyle.danger("Staff details"), "danger")
                .rowGap();
        spec.buttonLeft("open:admin", UltraStyle.danger("LM Staff Admin"), "danger")
                .buttonRight("page:eventlog", UltraStyle.danger("Event log"), "danger")
                .rowGap();
        spec.footerBackHome("Difficulty Home");
        spec.send(player);
    }

    private static void openEventLog(ServerPlayer player) {
        if (!StaffAccess.isStaff(player)) {
            openHome(player);
            return;
        }
        UltraScreenSpec spec = sub("eventlog")
                .line(UltraStyle.LABEL + "Unified system event log")
                .line(UltraStyle.LABEL + "Use the toggles, or typed: /lm admin syslog ...")
                .buttonsAfterLines();
        boolean logOn = DifficultyConfig.get().enableSystemTelemetry;
        spec.buttonLeft("do:syslog:status", UltraStyle.danger("Status"), "danger")
                .buttonRight(
                        "do:syslog:on",
                        logOn ? UltraStyle.confirm("Event log - ON") : "Turn event log on",
                        logOn ? "confirm" : UltraStyle.STYLE_NORMAL)
                .rowGap();
        spec.buttonLeft(
                        "do:syslog:off",
                        logOn
                                ? UltraStyle.danger("Turn event log off")
                                : UltraStyle.dim("Event log - OFF"),
                        logOn ? "danger" : "dim")
                .buttonRight("do:syslog:flush", UltraStyle.danger("Flush"), "danger")
                .rowGap();
        spec.footerBack("page:admin", "Server tools");
        spec.send(player);
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
