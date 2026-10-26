package com.dbzlegacy.adaptivedifficulty.service;

import com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache;
import com.dbzlegacy.adaptivedifficulty.calc.DifficultySnapshot;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.calc.PlayerCombatProfile;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy;
import com.dbzlegacy.adaptivedifficulty.gui.GuiClickConfirm;
import com.dbzlegacy.adaptivedifficulty.gui.ProgressionGuiApi;
import com.dbzlegacy.adaptivedifficulty.gui.ProgressionMenu;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.util.PaidFeatureAccess;
import com.dbzlegacy.adaptivedifficulty.data.PlayerDifficultyData;
import com.dbzlegacy.adaptivedifficulty.data.TeamMode;
import com.dbzlegacy.adaptivedifficulty.team.TeamScaling;
import com.dbzlegacy.adaptivedifficulty.gui.DifficultyMenu;
import com.dbzlegacy.adaptivedifficulty.scaling.MobScaling;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockSystem;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockTier;
import com.dbzlegacy.adaptivedifficulty.tick.NearbyMobScaler;
import com.dbzlegacy.adaptivedifficulty.tick.ScaledMobTracker;
import com.dbzlegacy.adaptivedifficulty.title.DifficultyTitle;
import com.dbzlegacy.adaptivedifficulty.title.TitleSystem;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.dbzlegacy.adaptivedifficulty.util.SystemGate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;

/**
 * Player actions: buy/lower tiers, titles, reset.
 * Difficulty points are not player-facing — only Unlock Tiers.
 */
public final class DifficultyActions {
    public static final String ACT_BUY = "buy";
    public static final String ACT_ACTIVATE = "activate";
    public static final String ACT_PURCHASE_TIER = "purchase_tier";
    public static final String ACT_LOWER_TIER = "lower_tier";
    public static final String ACT_TEAM = "team";
    public static final String ACT_RESET = "reset";
    public static final String ACT_CHARACTER_RESET = "character_reset";
    public static final String ACT_REFRESH = "refresh";
    public static final String ACT_PAGE = "page";
    public static final String ACT_EQUIP_TITLE = "equip_title";
    public static final String ACT_CLEAR_TITLE = "clear_title";
    public static final String ACT_TOGGLE_PERSONAL = "toggle_personal";
    public static final String ACT_TOGGLE_COIN_CHAT = "toggle_coin_chat";
    public static final String ACT_TOGGLE_TITLE_SENSE = "toggle_title_sense";
    public static final String ACT_TOGGLE_STAFF_FREE_COINS = "toggle_staff_free_coins";
    public static final String ACT_SUMMON_END_DRAGON = "summon_end_dragon";
    public static final String ACT_END_DRAGON = "end_dragon";
    public static final String ACT_SUMMON_WITHER = "summon_wither";
    public static final String ACT_SUMMON_WARDEN = "summon_warden";

    /**
     * When true, {@link #openGui} syncs unlocks/titles but does not reopen a menu.
     * Used by the Bukkit companion so it owns inventory reopen after {@code /difficulty do}.
     */
    private static final ThreadLocal<Boolean> SUPPRESS_GUI_REOPEN =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Login/respawn: keep re-sampling DMZ level until Character attaches (or deadline). */
    private static final Map<UUID, Long> LEVEL_PULL_UNTIL_MS = new ConcurrentHashMap<>();

    private DifficultyActions() {}

    /**
     * Re-read live DMZ level/data before painting Buy GUI / placeholders.
     * Bukkit {@code /difficulty} opens inventory without going through {@link #openGui},
     * so this must also be invoked from the companion bridge on every open.
     */
    public static void prepareGui(ServerPlayer player) {
        if (player == null) {
            return;
        }
        int sampled = DmzProgression.sampleLevelOnGuiOpen(player);
        if (DmzProgression.hasReliableUnlockGateSample(player)
                && !DmzProgression.isTransformed(player)) {
            DifficultyCache.data(player).noteDmzLevel(sampled);
        }
        try {
            com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem
                    .reapplyTierUnlocks(player);
        } catch (Throwable ignored) {
        }
        // refresh() re-samples + snapshot syncs unlocks/gate level.
        DifficultyCache.refresh(player);
        TitleSystem.syncTierTitles(player, false);
        // Keep pulling while Character is missing, painted level is still a
        // placeholder, OR battle power/CR is high while level still looks wrong —
        // high-form players often load BP before getLevel() catches up.
        int shown = DmzProgression.guiDisplayDmzLevel(player);
        double tp = DmzProgression.transformationPower(player);
        if (DmzProgression.character(player) == null
                || shown <= 1
                || (tp >= 25.0 && shown <= 1)
                || !DmzProgression.hasReliableUnlockGateSample(player)) {
            scheduleLevelPull(player);
        }
    }

    /**
     * After login/respawn, pull DMZ level once Character attaches.
     * Immediate + next-tick + delayed ticks cover the DMZ attach race that previously
     * left Buy GUI stuck at level 1 until the player died.
     */
    public static void scheduleLevelPull(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID id = player.m_20148_();
        LEVEL_PULL_UNTIL_MS.put(id, System.currentTimeMillis() + 12_000L);
        pullLevelNow(player);
        MinecraftServer server = player.m_20194_();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer p = server.m_6846_().m_11259_(id);
            if (p != null && p.m_6084_()) {
                pullLevelNow(p);
            }
        });
        // Character often attaches a few seconds after StatsData — retry at 1s/2s/4s/8s.
        for (int delay : new int[] {20, 40, 80, 160}) {
            final int ticks = delay;
            try {
                server.m_6937_(new TickTask(server.m_129921_() + ticks, () -> {
                    ServerPlayer p = server.m_6846_().m_11259_(id);
                    if (p != null && p.m_6084_()) {
                        pullLevelNow(p);
                    }
                }));
            } catch (Throwable ignored) {
            }
        }
    }

    /** Drain pending level pulls (called from server tick). */
    public static void pulseLevelPulls(MinecraftServer server) {
        if (server == null || LEVEL_PULL_UNTIL_MS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (UUID id : LEVEL_PULL_UNTIL_MS.keySet()) {
            Long until = LEVEL_PULL_UNTIL_MS.get(id);
            if (until == null) {
                continue;
            }
            ServerPlayer p = server.m_6846_().m_11259_(id);
            if (p == null || !p.m_6084_()) {
                LEVEL_PULL_UNTIL_MS.remove(id);
                continue;
            }
            if (now > until) {
                LEVEL_PULL_UNTIL_MS.remove(id);
                continue;
            }
            // Pulse every ~1s while waiting for Character.
            if (server.m_129921_() % 20 == 0) {
                pullLevelNow(p);
            }
        }
    }

    private static void pullLevelNow(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID id = player.m_20148_();
        int display = DmzProgression.guiDisplayDmzLevel(player);
        if (DmzProgression.hasReliableUnlockGateSample(player)
                && !DmzProgression.isTransformed(player)
                && display > 1) {
            DifficultyCache.data(player).noteDmzLevel(display);
        }
        // Always refresh the snapshot so Buy GUI / placeholders pick up live level
        // even while transformed (display uses live getLevel).
        if (display > 1 || DmzProgression.character(player) != null) {
            DifficultyCache.refresh(player);
        }
        if (display > 1 && DmzProgression.character(player) != null) {
            LEVEL_PULL_UNTIL_MS.remove(id);
        }
    }

    public static void clearLevelPull(UUID playerId) {
        if (playerId != null) {
            LEVEL_PULL_UNTIL_MS.remove(playerId);
        }
    }

    public static void openGui(ServerPlayer player, String page) {
        String target = page == null || page.isBlank() ? "main" : page;
        prepareGui(player);
        if ("titles".equalsIgnoreCase(target) || "title".equalsIgnoreCase(target)) {
            TitleSystem.syncTierTitles(player, true);
        }
        if (Boolean.TRUE.equals(SUPPRESS_GUI_REOPEN.get())) {
            return;
        }
        DifficultyMenu.open(player, target);
    }

    public static Result handle(ServerPlayer player, String action, long amount, String page) {
        return handleArg(player, action, String.valueOf(amount), page);
    }

    /**
     * Same as {@link #handleArg} but never reopens chat/inventory — Bukkit owns reopen.
     */
    public static Result handleArgNoReopen(ServerPlayer player, String action, String arg, String page) {
        SUPPRESS_GUI_REOPEN.set(Boolean.TRUE);
        try {
            return handleArg(player, action, arg, page);
        } finally {
            SUPPRESS_GUI_REOPEN.set(Boolean.FALSE);
        }
    }

    public static Result handleArg(ServerPlayer player, String action, String arg, String page) {
        if (player == null || action == null) {
            return Result.fail("Invalid action.");
        }
        String act = action.toLowerCase();
        if (ACT_PAGE.equals(act) || ACT_REFRESH.equals(act)) {
            String target = page == null || page.isBlank() ? "main" : page;
            if ("settings".equalsIgnoreCase(target) && !StaffAccess.isStaff(player)) {
                target = "main";
            }
            openGui(player, target);
            return Result.ok("");
        }
        if (!DifficultyConfig.isEnabled()) {
            openGui(player, page == null || page.isBlank() ? "main" : page);
            return Result.fail("Adaptive Difficulty is disabled by an admin.");
        }
        if (!SystemGate.allows(player)) {
            openGui(player, page == null || page.isBlank() ? "main" : page);
            return Result.fail("Adaptive Difficulty is whitelist-only right now. Ask an admin to add you.");
        }
        if (ACT_EQUIP_TITLE.equals(act) || "equip".equals(act)) {
            return equipTitle(player, arg, page == null || page.isBlank() ? "titles" : page);
        }
        if (ACT_CLEAR_TITLE.equals(act) || "unequip_title".equals(act)) {
            String titlePage = page == null || page.isBlank() ? "titles" : page;
            if (!GuiClickConfirm.confirmed(player.m_20148_(), "clear-title")) {
                openGui(player, titlePage);
                return Result.ok(UltraStyle.YELLOW + "Unequip your title? " + UltraStyle.LABEL
                        + "You can re-equip it any time. "
                        + UltraStyle.YELLOW + "Click again within 10 seconds.");
            }
            TitleSystem.clear(player);
            openGui(player, titlePage);
            return Result.ok("Title unequipped.");
        }
        if (ACT_CHARACTER_RESET.equals(act) || "char_reset".equals(act) || "characterreset".equals(act)) {
            return characterReset(player, page);
        }
        if (ACT_TEAM.equals(act)) {
            return setTeamMode(player, arg, page);
        }
        if (ACT_TOGGLE_PERSONAL.equals(act) || "personal".equals(act)
                || "toggle_difficulty".equals(act) || "difficulty_toggle".equals(act)) {
            return togglePersonal(player, page);
        }
        if (ACT_TOGGLE_COIN_CHAT.equals(act) || "coin_chat".equals(act)
                || "toggle_chat".equals(act) || "chat_drops".equals(act)) {
            return toggleCoinChat(player, page);
        }
        if (ACT_TOGGLE_TITLE_SENSE.equals(act) || "title_sense".equals(act)
                || "toggle_sense".equals(act) || "sense_chat".equals(act)) {
            return toggleTitleSense(player, page);
        }
        if (ACT_TOGGLE_STAFF_FREE_COINS.equals(act) || "staff_free_coins".equals(act)
                || "stafffree".equals(act) || "staff_free".equals(act)) {
            return toggleStaffFreeCoins(player, arg, page);
        }
        if (ACT_SUMMON_END_DRAGON.equals(act) || ACT_END_DRAGON.equals(act)
                || "summon_dragon".equals(act) || "dragon_summon".equals(act)) {
            return summonEndDragon(player, page);
        }
        if (ACT_SUMMON_WITHER.equals(act)) {
            return summonBoss(player, page,
                    com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.Boss.WITHER, "wither");
        }
        if (ACT_SUMMON_WARDEN.equals(act)) {
            return summonBoss(player, page,
                    com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.Boss.WARDEN, "warden");
        }

        // Personal OFF freezes buy / lower / reset until the player turns it back on.
        if (!SystemGate.participates(player)
                && (ACT_ACTIVATE.equals(act) || ACT_PURCHASE_TIER.equals(act) || ACT_BUY.equals(act)
                || ACT_LOWER_TIER.equals(act) || ACT_RESET.equals(act) || ACT_TEAM.equals(act)
                || "zero".equals(act) || "clear".equals(act))) {
            openGui(player, page == null || page.isBlank() ? "main" : page);
            String hint = ACT_TEAM.equals(act)
                    ? UltraStyle.danger(
                            "Turn on personal difficulty on the main Difficulty page first, then pick a team mode.")
                    : UltraStyle.danger(
                            "Turn on personal difficulty on the main Difficulty page first, then pick a tier.");
            return Result.fail(hint);
        }

        long amount = 0L;
        if (arg != null && !arg.isBlank()) {
            try {
                amount = Long.parseLong(arg.trim());
            } catch (NumberFormatException ignored) {
                if (ACT_ACTIVATE.equals(act) || ACT_PURCHASE_TIER.equals(act)
                        || ACT_BUY.equals(act) || ACT_LOWER_TIER.equals(act)) {
                    return Result.fail("Invalid tier: " + arg);
                }
            }
        }
        return switch (act) {
            case ACT_ACTIVATE, ACT_PURCHASE_TIER, ACT_BUY -> setTier(player, (int) amount, page);
            case ACT_LOWER_TIER -> lowerTier(player, (int) amount, page);
            case ACT_RESET, "zero", "clear" -> resetActive(player, page);
            default -> Result.fail("Unknown action.");
        };
    }

    private static Result setTeamMode(ServerPlayer player, String arg, String page) {
        PlayerDifficultyData data = DifficultyCache.data(player);
        if (!data.isPersonalEnabled()) {
            openGui(player, page == null || page.isBlank() ? "team" : page);
            return Result.fail("Turn on personal difficulty on the main Difficulty page first, then pick a team mode.");
        }
        TeamMode next = resolveTeamModeArg(data.getTeamMode(), arg);
        if (next != TeamMode.PERSONAL_ONLY) {
            if (!DifficultyConfig.get().enableRivalSystem) {
                openGui(player, page == null || page.isBlank() ? "team" : page);
                return Result.fail("Rival system is disabled — teams need mutual rivals.");
            }
            if (TeamScaling.mutualRivalCount(player) < 1) {
                openGui(player, page == null || page.isBlank() ? "team" : page);
                return Result.fail("No mutual rivals yet — use /rival to declare and accept first.");
            }
        }
        data.setTeamMode(next);
        DifficultyCache.save(player);
        DifficultyCache.refresh(player);
        String returnPage = page == null || page.isBlank() ? "team" : page;
        openGui(player, returnPage);
        return Result.ok(teamModeMessage(next));
    }

    private static TeamMode resolveTeamModeArg(TeamMode current, String arg) {
        if (arg == null || arg.isBlank() || "cycle".equalsIgnoreCase(arg.trim())) {
            return cycleTeamMode(current);
        }
        return TeamMode.fromString(arg);
    }

    private static TeamMode cycleTeamMode(TeamMode current) {
        if (current == null || current == TeamMode.PERSONAL_ONLY) {
            return TeamMode.THRESHOLD_BONUS_ONLY;
        }
        if (current == TeamMode.THRESHOLD_BONUS_ONLY) {
            return TeamMode.FULL_TEAM_SCALING;
        }
        return TeamMode.PERSONAL_ONLY;
    }

    private static String teamModeMessage(TeamMode mode) {
        int contrib = (int) DifficultyConfig.get().contributionPercent;
        return switch (mode) {
            case PERSONAL_ONLY -> "Team mode: Personal — only your tier ceiling applies.";
            case THRESHOLD_BONUS_ONLY ->
                    "Team mode: Threshold — higher tier ceiling when rivals are online and using a team mode."
                            + " More elites, mutants, and bosses.";
            case FULL_TEAM_SCALING ->
                    "Team mode: Full — threshold bonus plus " + contrib + "% of nearby rivals' spare tier room."
                            + " Best elite, mutant, and boss spawn boost when rivals are close.";
        };
    }

    private static Result togglePersonal(ServerPlayer player, String page) {
        PlayerDifficultyData data = DifficultyCache.data(player);
        boolean on = data.togglePersonalEnabled();
        DifficultyCache.save(player);
        DifficultyCache.refresh(player);
        if (!on) {
            ScaledMobTracker.releaseAndRevertPlayer(player);
            NearbyMobScaler.processEvictions();
            try {
                com.dbzlegacy.adaptivedifficulty.progression.end.EndDimensionStrength
                        .despawnOwnedDragon(player);
            } catch (Throwable ignored) {
            }
        }
        String returnPage = page == null || page.isBlank() ? "main" : page;
        openGui(player, returnPage);
        return Result.ok(on
                ? "Personal difficulty ON — scaling, kill coins, and tier buys are active again."
                : "Personal difficulty OFF — scaling, kill coins, AI pressure, and tier buys disabled until you turn it back on.");
    }

    private static Result toggleCoinChat(ServerPlayer player, String page) {
        PlayerDifficultyData data = DifficultyCache.data(player);
        boolean on = data.toggleCoinDropChat();
        DifficultyCache.save(player);
        String returnPage = page == null || page.isBlank() ? "main" : page;
        openGui(player, returnPage);
        return Result.ok(on
                ? "Coin drop chat ON — you'll see Ancient Coin drop messages."
                : "Coin drop chat OFF — drop messages muted.");
    }

    private static Result toggleTitleSense(ServerPlayer player, String page) {
        PlayerDifficultyData data = DifficultyCache.data(player);
        boolean on = !data.titleProgress().titleSenseChat();
        data.titleProgress().setTitleSenseChat(on);
        DifficultyCache.save(player);
        String returnPage = page == null || page.isBlank() ? "titles" : page;
        openGui(player, returnPage);
        return Result.ok(on
                ? "Title Sense ON — Elite/Boss recognition chat enabled."
                : "Title Sense OFF — recognition chat muted.");
    }

    private static Result toggleStaffFreeCoins(ServerPlayer player, String arg, String page) {
        String toggleArg = arg;
        if (toggleArg == null || toggleArg.isBlank() || "0".equals(toggleArg.trim())) {
            toggleArg = "";
        }
        String msg = ProgressionGuiApi.toggleStaffFreeAncientCoinCosts(player, toggleArg);
        if (msg.startsWith(UltraStyle.DANGER)) {
            return Result.fail(UltraStyle.plain(msg));
        }
        ProgressionMenu.open(player, "economy");
        return Result.ok(UltraStyle.plain(msg));
    }

    private static Result summonEndDragon(ServerPlayer player, String page) {
        String returnPage = page == null || page.isBlank() ? "end_dragon" : page;
        var out = com.dbzlegacy.adaptivedifficulty.progression.end.EndDimensionStrength
                .playerSummonOutcome(player);
        openGui(player, returnPage);
        return out.ok() ? Result.ok(out.text()) : Result.fail(out.text());
    }

    /** Wither / Warden paid summon, routed like {@link #summonEndDragon}. */
    private static Result summonBoss(
            ServerPlayer player,
            String page,
            com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.Boss boss,
            String confirmPage) {
        String returnPage = page == null || page.isBlank() ? confirmPage : page;
        var out = com.dbzlegacy.adaptivedifficulty.progression.boss.PlayerBossSummons.summonOutcome(player, boss);
        openGui(player, returnPage);
        return out.ok() ? Result.ok(out.text()) : Result.fail(out.text());
    }

    private static Result equipTitle(ServerPlayer player, String titleId, String page) {
        TitleSystem.syncTierTitles(player, false);
        DifficultyTitle title = DifficultyTitle.byId(titleId);
        if (title == null) {
            openGui(player, page);
            return Result.fail("Unknown title.");
        }
        if (!TitleSystem.has(player, title)) {
            openGui(player, page);
            return Result.fail(
                    "Title locked: " + title.display + " " + UltraStyle.DIM + "("
                            + title.requirementTip() + ")");
        }
        if (title.id.equals(TitleSystem.activeId(player))) {
            TitleSystem.clear(player);
            openGui(player, page);
            return Result.ok("Title unequipped.");
        }
        if (!TitleSystem.equip(player, title.id)) {
            openGui(player, page);
            return Result.fail("Could not equip " + title.display + ".");
        }
        openGui(player, page);
        return Result.ok("Equipped title: " + title.display);
    }

    /**
     * Raise to a higher tier (paid, level-scaled) or switch to a lower unlocked tier (free).
     */
    private static Result setTier(ServerPlayer player, int tierId, String page) {
        PlayerDifficultyData data = DifficultyCache.data(player);
        // Prestige-point permanent unlocks must be visible before eligibility/owned checks.
        try {
            com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem
                    .reapplyTierUnlocks(player);
        } catch (Throwable ignored) {
        }
        UnlockSystem.syncUnlocks(player, data);
        UnlockTier tier = UnlockTier.byId(tierId);
        String returnPage = page == null || page.isBlank() ? "tiers" : page;
        if (tier == null) {
            openGui(player, returnPage);
            return Result.fail("Unknown tier. Use 1–7.");
        }
        // Live gate — unlock bits alone are not enough after a reliable prestige/level reset.
        // While the base-form sample is unavailable, keep already-unlocked tiers usable.
        // Prestige-point purchases count as eligible via UnlockSystem.isEligible.
        boolean reliable = DmzProgression.hasReliableUnlockGateSample(player);
        boolean eligible = UnlockSystem.isEligible(player, tier);
        boolean owned = data.hasUnlockedTier(tier.id);
        boolean prestigeOwned = false;
        try {
            prestigeOwned = com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem
                    .hasPurchasedTier(player, tier.id);
        } catch (Throwable ignored) {
        }
        if (prestigeOwned && !owned) {
            data.unlockTier(tier.id);
            owned = true;
            eligible = true;
        }
        if (!(eligible && owned) && !(owned && !reliable)) {
            openGui(player, returnPage);
            long gate = UnlockSystem.gateLevelForEligibility(player);
            int prestige = DmzProgression.prestige(player);
            String tip = !reliable && !owned
                    ? " Return to base form once so your DMZ level can sync (CR/BP does not unlock tiers)."
                    : " You: DMZ " + gate + " · Prestige " + prestige + " (CR/BP does not unlock tiers).";
            return Result.fail("Tier " + tier.id + " locked. Need " + tier.requirementTip() + "." + tip);
        }
        if (!owned && eligible) {
            data.unlockTier(tier.id);
        }
        int current = data.getActiveTier();
        if (current == tier.id) {
            openGui(player, returnPage);
            return Result.ok("Already on " + tier.display + " (T" + tier.id + ").");
        }

        // Direction-aware cost: downgrade (weaker tier) is always 0.
        long cost = AncientCoinEconomy.activationCost(tier, player, current);
        boolean staffFree = PaidFeatureAccess.bypassAncientCoinCost(player);
        boolean downgrade = current > 0 && tier.id < current;
        long charge = (staffFree || downgrade) ? 0L : cost;
        String costText = downgrade
                ? "Free"
                : (staffFree ? "free (staff)" : AncientCoinEconomy.formatExactCost(cost));

        if (downgrade) {
            if (!GuiClickConfirm.confirmed(player.m_20148_(), "diff-tier:" + tier.id)) {
                openGui(player, returnPage);
                return Result.ok(
                        UltraStyle.YELLOW + "Downgrade to " + UltraStyle.VALUE + tier.display
                                + UltraStyle.YELLOW + " (Tier " + tier.id
                                + ") — Free? Click again within 10 seconds.");
            }
            applyTier(data, player, tier);
            openGui(player, returnPage);
            return Result.ok("Downgraded to " + tier.display + " (T" + tier.id + ") — Free.");
        }

        if (!canPersist(player)) {
            openGui(player, returnPage);
            return Result.fail("Could not save difficulty data — purchase cancelled (try relogging).");
        }
        if (charge > 0L && !AncientCoinEconomy.canAfford(player, charge)) {
            openGui(player, returnPage);
            return Result.fail(AncientCoinEconomy.missingText(player, charge));
        }
        if (!GuiClickConfirm.confirmed(player.m_20148_(), "diff-tier:" + tier.id)) {
            openGui(player, returnPage);
            return Result.ok(
                    UltraStyle.YELLOW + "Buy " + UltraStyle.VALUE + tier.display
                            + UltraStyle.YELLOW + " for " + UltraStyle.GOLD + costText
                            + UltraStyle.YELLOW + "? Click again within 10 seconds.");
        }
        if (charge > 0L && !AncientCoinEconomy.charge(player, charge)) {
            openGui(player, returnPage);
            return Result.fail(AncientCoinEconomy.missingText(player, charge));
        }
        applyTier(data, player, tier);
        TitleSystem.syncTierTitles(player, true);
        DifficultySnapshot snap = DifficultyCache.get(player);
        openGui(player, returnPage);
        return Result.ok("Purchased " + tier.display + " (T" + tier.id + ") for " + costText
                + ". CR " + snap.combatRating);
    }

    private static Result lowerTier(ServerPlayer player, int tierId, String page) {
        String returnPage = page == null || page.isBlank() ? "tiers" : page;
        if (tierId <= 0) {
            return resetActive(player, returnPage);
        }
        PlayerDifficultyData data = DifficultyCache.data(player);
        UnlockTier tier = UnlockTier.byId(tierId);
        if (tier == null) {
            openGui(player, returnPage);
            return Result.fail("Unknown tier. Use 1–7.");
        }
        if (tier.id > data.getActiveTier()) {
            openGui(player, "tiers");
            return Result.fail("Buy a higher tier to raise difficulty.");
        }
        boolean reliable = DmzProgression.hasReliableUnlockGateSample(player);
        boolean eligible = UnlockSystem.isEligible(player, tier);
        boolean owned = data.hasUnlockedTier(tier.id);
        if (!(eligible && owned) && !(owned && !reliable)) {
            openGui(player, returnPage);
            long gate = UnlockSystem.gateLevelForEligibility(player);
            int prestige = DmzProgression.prestige(player);
            return Result.fail("Tier " + tier.id + " is not unlocked. Need "
                    + tier.requirementTip() + ". You: DMZ " + gate + " · Prestige " + prestige
                    + " (CR/BP does not unlock tiers).");
        }
        if (data.getActiveTier() == tier.id) {
            openGui(player, returnPage);
            return Result.ok("Already on " + tier.display + ".");
        }
        applyTier(data, player, tier);
        openGui(player, returnPage);
        return Result.ok("Downgraded to " + tier.display + " (T" + tier.id + ") — Free.");
    }

    private static void applyTier(PlayerDifficultyData data, ServerPlayer player, UnlockTier tier) {
        if (!UnlockSystem.isEligible(player, tier)) {
            return;
        }
        data.unlockTier(tier.id);
        data.setActiveTier(tier.id);
        // Internal CR scale only — not shown as player-facing "points".
        data.setActiveDifficultyLevel(tier.maxDifficulty());
        DifficultyCache.save(player);
        refreshCombatPaint(player);
    }

    /**
     * Immediate combat-profile + claimed-mob repaint after form / tier changes.
     * Avoids ~2s nearby-pulse lag where T7 paint lingered after lower/form-down.
     * Also refreshes CR snapshot (form-boosted released stats) and area CR cache
     * so transform up/down cannot leave stale ratings beside live mob paint.
     */
    public static void refreshCombatPaint(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerCombatProfile.clear(player.m_20148_());
        DifficultyCache.refresh(player);
        try {
            com.dbzlegacy.adaptivedifficulty.scaling.AreaDifficulty.clearCache();
        } catch (Throwable ignored) {
        }
        ScaledMobTracker.forEachClaimedMob(player, mob -> MobScaling.retargetToPlayer(mob, player));
    }

    /** True when player NBT can persist paid tier changes. */
    public static boolean canPersist(ServerPlayer player) {
        return player != null && PersistentDataAccess.isWritable(PersistentDataAccess.get(player));
    }

    private static Result resetActive(ServerPlayer player, String page) {
        if (!GuiClickConfirm.confirmed(player.m_20148_(), "clear-active-tier")) {
            openGui(player, page);
            return Result.ok(
                    UltraStyle.YELLOW + "Clear active tier? " + UltraStyle.LABEL
                            + "Team mode resets to Personal. Unlocks and Ancient Coins kept. "
                            + UltraStyle.YELLOW + "Click again within 10 seconds.");
        }
        PlayerDifficultyData data = DifficultyCache.data(player);
        data.resetTemporary();
        DifficultyCache.save(player);
        DifficultyCache.refresh(player);
        ScaledMobTracker.releaseAndRevertPlayer(player);
        NearbyMobScaler.processEvictions();
        openGui(player, page);
        return Result.ok("Difficulty cleared. Team mode reset to Personal. Unlocks and Ancient Coins kept.");
    }

    private static Result characterReset(ServerPlayer player, String page) {
        PlayerDifficultyData data = DifficultyCache.data(player);
        data.resetTemporary();
        DifficultyCache.save(player);
        DifficultyCache.refresh(player);
        ScaledMobTracker.releaseAndRevertPlayer(player);
        NearbyMobScaler.processEvictions();
        if (page != null && !page.isBlank()) {
            openGui(player, page);
        }
        return Result.ok("Character reset: active tier cleared. Prestige, unlocks, and Ancient Coins kept.");
    }

    public static final class Result {
        public final boolean ok;
        public final String message;

        private Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message == null ? "" : message;
        }

        public boolean ok() {
            return ok;
        }

        public String message() {
            return message == null ? "" : message;
        }

        public void tell(ServerPlayer player) {
            if (player == null || message == null || message.isBlank()) {
                return;
            }
            player.m_213846_(net.minecraft.network.chat.Component.m_237113_(
                    (ok ? UltraStyle.CONFIRM : UltraStyle.DANGER) + message));
        }

        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }
}
