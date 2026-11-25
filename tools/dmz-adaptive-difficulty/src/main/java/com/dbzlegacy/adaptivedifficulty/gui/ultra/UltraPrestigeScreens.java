package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.gui.ProgressionGuiApi;
import com.dbzlegacy.adaptivedifficulty.progression.shop.KiMasteryTrack;
import com.dbzlegacy.adaptivedifficulty.progression.shop.MasteryWallet;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeCustomShop;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeShopConfig;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeUniverseRoleWarn;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/**
 * Prestige Ultra UI - Home / Shop / Trade (4.7.84, P-1).
 *
 * <p>Home: the wallet, the next prestige and "Prestige now" (GUI confirm). Shop: spend favor. Trade: trade
 * held prestiges for favor (the old Exchange shop, now a tab). The old Prestige tab ids ({@code turnin},
 * {@code points}, {@code prestige}) open Home. Shop catalogs show the favor balance only (no full wallet dump).
 *
 * <p>Every button that spends favor, points or prestige opens a confirm page ({@code ask:<action>:<arg>},
 * 4.7.21): what happens, the exact cost, what the player gets, Confirm and Back. Only Confirm runs the
 * {@code do:} action; {@link #askReturn} sends the player back to the page they came from afterwards.
 *
 * <p>Prices, sections and the Custom Shop ("Special perks") come from
 * {@link PrestigeShopConfig}; a section switched off there drops its Shop button
 * (and shows as closed when deep-linked). Staff get an "Edit shop" button that opens
 * the config editor on {@code prestige-shop}.
 */
public final class UltraPrestigeScreens {
    private UltraPrestigeScreens() {}

    public static void open(ServerPlayer player, String page) {
        if (player == null) {
            return;
        }
        String raw = page == null || page.isBlank() ? "main" : page.trim();
        String p = raw.toLowerCase(Locale.ROOT);
        // Legacy skills_pN deep-links still open the single skills page.
        if (parseSkillsPage(p) != null) {
            openSkillsShop(player);
            return;
        }
        if (p.startsWith("ask:")) {
            routeAsk(player, raw.substring("ask:".length()));
            return;
        }
        if (p.startsWith("perk:")) {
            openPerk(player, p.substring("perk:".length()));
            return;
        }
        if (p.startsWith("mw_line:")) {
            openMasteryLine(player, raw.substring("mw_line:".length()));
            return;
        }
        if (p.startsWith("mw_form:")) {
            openMasteryForm(player, raw.substring("mw_form:".length()));
            return;
        }
        // "mastery_lines:2" reopens the third page of the form lines.
        if ("mastery_lines".equals(UltraPickHelper.pageBase(p))) {
            openMasteryLines(player, UltraPickHelper.pageIndex(p));
            return;
        }
        switch (p) {
            // 4.7.84 (P-1): the Prestige tab folded into Home; its old ids open Home.
            case "turnin", "points", "prestige" -> openHome(player);
            case "confirm", "prestige_confirm" -> openConfirm(player);
            case "shop", "shop_hub", "catalog" -> openShopHub(player);
            case "exchange", "trade", "turnin_shop", "favor_shop" -> openExchange(player);
            case "skills", "skill", "skills_p0" -> openSkillsShop(player);
            case "tiers", "tier", "difficulty" -> openTiers(player);
            case "effects", "effect", "forms", "form" -> openEffects(player);
            case "custom", "perks" -> openCustom(player);
            case "mastery_wallet", "mastery" -> openMasteryWallet(player);
            case "staff_edit" -> {
                if (StaffAccess.isStaff(player)) {
                    UltraGui.open(player, "config", "edit:prestige-shop:0:");
                } else {
                    openShopHub(player);
                }
            }
            default -> openHome(player);
        }
    }

    /**
     * Legacy skill-shop page tokens ({@code skills_pN}, {@code skills:N}) - all map to one page.
     */
    private static Integer parseSkillsPage(String p) {
        if (p == null || p.isBlank()) {
            return null;
        }
        if (p.startsWith("skills_p")) {
            return parseNonNegInt(p.substring("skills_p".length()));
        }
        if (p.startsWith("skills:")) {
            return parseNonNegInt(p.substring("skills:".length()));
        }
        if (p.startsWith("shop:skills:") || p.startsWith("shop:skills_p")) {
            return 0;
        }
        if (p.startsWith("shop:")) {
            String rest = p.substring("shop:".length()).trim();
            if (!rest.isEmpty() && rest.chars().allMatch(Character::isDigit)) {
                return parseNonNegInt(rest);
            }
        }
        return null;
    }

    private static Integer parseNonNegInt(String raw) {
        try {
            int n = Integer.parseInt(raw == null ? "" : raw.trim());
            return Math.max(0, n);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static UltraScreenSpec base(String page, String tab) {
        return UltraScreenSpec.of("prestige")
                .fitMenu()
                .title("Prestige")
                .page(page)
                .activeTab(tab)
                // 4.7.84 (P-1): Home / Shop / Trade. Tab ids: home -> main, shop -> shop, trade -> exchange.
                .tabs("home|Home", "shop|Shop", "trade|Trade");
    }

    /** Subpage: no tab row - dual footer (Back + Main menu). */
    private static UltraScreenSpec sub(String page) {
        return UltraScreenSpec.of("prestige")
                .fitMenu()
                .title("Prestige")
                .page(page);
    }

    /** "0 of 20": prestiges held of the most allowed. */
    private static String heldText(Map<String, String> ph) {
        return ph.getOrDefault("held", "0") + " of " + ph.getOrDefault("held_max", String.valueOf(
                com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulPrestigeIntegration.OVERHAUL_MAX_PRESTIGE));
    }

    /** True when the player holds the most prestiges allowed (so Prestige now waits on a trade). */
    private static boolean heldFull(Map<String, String> ph) {
        return parseInt(ph.get("held"), 0) >= parseInt(ph.get("held_max"), Integer.MAX_VALUE);
    }

    /** Shop and Trade pages: favor balance only (no player info dump). */
    private static void paintFavorBalance(UltraScreenSpec spec, Map<String, String> ph) {
        spec.labelValue("Favor:", ph.getOrDefault("points", "0"));
    }

    /**
     * Home (4.7.84, P-1): the wallet once, the next prestige in one line, Fastest now, and the one action:
     * {@code Prestige now} (danger) when ready, else a dim {@code Prestige - Locked} that still opens the confirm
     * page, which says why.
     */
    private static void openHome(ServerPlayer player) {
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        UltraScreenSpec spec = base("main", "home")
                .lineGap(2)
                .bodyPad(4);
        spec.labelValue("Prestiges you hold:", heldText(ph));
        spec.labelValue("Prestiges earned (all time):", ph.getOrDefault("completed", "0"));
        spec.labelValue("Favor:", ph.getOrDefault("points", "0") + " - spend it in the Shop, get it by trading prestiges");
        boolean enabled = "true".equalsIgnoreCase(ph.get("system_enabled"));
        boolean ready = enabled && "true".equalsIgnoreCase(ph.get("ready"));
        if (!enabled) {
            spec.line(UltraStyle.danger("Prestige system is disabled. Ask staff to turn it on."));
        } else {
            // The next-prestige target comes from the held-count ladder
            // (LmOverhaulCapMath.prestigeRequirement: 50k at 0 held, +15k per held, 150k from 7).
            spec.labelValue("Next prestige at level", ph.getOrDefault("required_fmt", "0")
                    + " (you: " + ph.getOrDefault("level_fmt", ph.getOrDefault("level", "1")) + ")");
            if (ready) {
                spec.line(UltraStyle.confirm("Ready to prestige."));
            } else if (heldFull(ph)) {
                spec.line(UltraStyle.dim("You hold the most prestiges allowed. Trade some for favor on the Trade tab."));
            }
            // 4.7.64: the nearest way to earn toward it, from the systems that feed leveling.
            spec.line(UltraStyle.VALUE + HubDashboard.fastestNow(player));
        }
        spec.buttonsAfterLines();
        if (ready) {
            spec.buttonWide("page:confirm", UltraStyle.danger("Prestige now"), UltraStyle.STYLE_DANGER, null)
                    .rowGap();
        } else {
            // Dim, never hidden; the confirm page it opens says what is missing.
            spec.buttonWide("page:confirm", UltraStyle.dim("Prestige - Locked"), UltraStyle.STYLE_DIM, null)
                    .rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /**
     * GUI confirm subpage - role warnings here (not chat). Confirm runs {@code do:confirm_now}.
     */
    private static void openConfirm(ServerPlayer player) {
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        UltraScreenSpec spec = sub("confirm")
                .lineGap(2)
                .bodyPad(4)
                .line(UltraStyle.header("Confirm prestige"))
                .labelValue("Prestiges you hold:", heldText(ph))
                .labelValue("Next prestige at level", ph.getOrDefault("required_fmt", "0"))
                .notice("Prestiging resets your stats, skills, techniques and quest progress. You gain 1 prestige."
                        + " Kept skill levels and shop purchases stay.");
        if (PrestigeUniverseRoleWarn.isGodOfDestruction(player)) {
            spec.line(UltraStyle.danger(PrestigeUniverseRoleWarn.GOD_OF_DESTRUCTION));
        }
        if (PrestigeUniverseRoleWarn.isSupremeKai(player)) {
            spec.line(UltraStyle.danger(PrestigeUniverseRoleWarn.SUPREME_KAI));
        }
        boolean ready = "true".equalsIgnoreCase(ph.get("ready"));
        if (!ready) {
            spec.line(UltraStyle.danger(heldFull(ph)
                    ? "You hold the most prestiges allowed. Trade some for favor on the Trade tab first."
                    : "Reach level " + ph.getOrDefault("required_fmt", "0") + " before you can prestige."));
        }
        spec.buttonsAfterLines();
        if (ready) {
            spec.button(
                            "do:confirm_now",
                            UltraStyle.danger("Confirm prestige"),
                            UltraStyle.STYLE_DANGER)
                    .rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /**
     * Shop hub - favor catalogs; sections switched off in config are hidden. 4.7.84 (P-1): trading prestiges for
     * favor moved to the Trade tab, so this picker holds only ways to spend.
     */
    private static void openShopHub(ServerPlayer player) {
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        PrestigeShopConfig cfg = PrestigeShopConfig.get();
        UltraScreenSpec spec = base("shop", "shop")
                .lineGap(2)
                .bodyPad(4);
        paintFavorBalance(spec, ph);
        spec.line(UltraStyle.LABEL + "Spend favor here. Everything you buy stays when you prestige.");
        if (cfg.exchange.enabled) {
            spec.line(UltraStyle.dim("Need favor? Trade a prestige for favor on the Trade tab."));
        }
        spec.buttonsAfterLines();
        // 4.7.81 (GuiKit): the sections are a picker - normal style, two columns.
        List<String[]> sections = new ArrayList<>();
        if (cfg.skillShop.enabled) {
            sections.add(new String[] {"page:skills", "Skill shop"});
        }
        if (cfg.tierShop.enabled) {
            sections.add(new String[] {"page:tiers", "Own tiers for good"});
        }
        sections.add(new String[] {"page:effects", "Effects"});
        // Shown while selling, or while the player still has points to apply.
        if (cfg.masteryWallet.enabled || MasteryWallet.balance(player) > 0) {
            sections.add(new String[] {"page:mastery_wallet", "Mastery wallet"});
        }
        if (cfg.customShop.enabled && !customRows(player).isEmpty()) {
            sections.add(new String[] {"page:custom", UltraStyle.buttonText(cfg.customShop.menuLabel)});
        }
        for (int i = 0; i < sections.size(); i++) {
            String[] s = sections.get(i);
            pairButton(spec, i, s[0], UltraStyle.plain(s[1]), UltraStyle.STYLE_NORMAL);
        }
        endPairs(spec, sections.size());
        // Edit prestige shop lives on Staff Admin (4.7.31); page:staff_edit still works for old links.
        spec.back();
        spec.send(player);
    }

    // ── Mastery wallet (Shop > Mastery wallet) ───────────────────────

    /** Single-column picker page size (rivals, discovery, android picks). */
    static final int MASTERY_PICKS_PER_PAGE = 8;

    private static void openMasteryWallet(ServerPlayer player) {
        walletSpec(MasteryWallet.balance(player), PrestigePointsSystem.getPoints(player), MasteryWallet.open())
                .send(player);
    }

    private static void openMasteryLines(ServerPlayer player, int page) {
        linesSpec(MasteryWallet.lines(player), MasteryWallet.balance(player), page).send(player);
    }

    /**
     * {@code mw_line:<s|n>:<group>[:N]}. The token holds a colon, so the trailing page index is
     * stripped first; a line that is no longer unlocked falls back to the line list.
     */
    private static void openMasteryLine(ServerPlayer player, String rest) {
        MasteryWallet.Line line = MasteryWallet.line(player, UltraPickHelper.pageBase(rest));
        int page = UltraPickHelper.pageIndex(rest);
        if (line == null) {
            // A group named only by digits would look like a page index; try it whole.
            line = MasteryWallet.line(player, rest);
            page = 0;
        }
        if (line == null) {
            openMasteryLines(player, 0);
            return;
        }
        lineSpec(line, MasteryWallet.balance(player), page).send(player);
    }

    /**
     * {@code mw_form:<s|n>:<group>:<form>[:N]}, N = the line page the player came from. Read live
     * so the numbers match what Apply will do.
     */
    private static void openMasteryForm(ServerPlayer player, String rest) {
        MasteryWallet.Form form = null;
        MasteryWallet.Line line = null;
        int linePage = UltraPickHelper.pageIndex(rest);
        for (String key : new String[] {UltraPickHelper.pageBase(rest), rest}) {
            int cut = key == null ? -1 : key.lastIndexOf(':');
            line = cut <= 0 ? null : MasteryWallet.line(player, key.substring(0, cut));
            form = line == null ? null : line.form(key.substring(cut + 1));
            if (form != null) {
                break;
            }
            // Second pass: a form named only by digits, no page index.
            linePage = 0;
        }
        if (form == null) {
            openMasteryLines(player, 0);
            return;
        }
        formSpec(line, form, MasteryWallet.balance(player), linePage).send(player);
    }

    /** {@code index} even: left column; odd: right column, then the row ends (4.7.81 two-column pickers). */
    private static void pairButton(UltraScreenSpec spec, int index, String action, String label, String style) {
        if (index % 2 == 0) {
            spec.buttonLeft(action, label, style);
        } else {
            spec.buttonRight(action, label, style);
            spec.rowGap();
        }
    }

    /** Ends a half-filled last row of {@link #pairButton}. */
    private static void endPairs(UltraScreenSpec spec, int placed) {
        if (placed % 2 == 1) {
            spec.rowGap();
        }
    }

    /**
     * Pager row (4.7.81): [Prev | Next], with "Page X of Y" in the slot that has nowhere to go (the startup
     * self-test reads the Prev / Next labels).
     * Nothing for a single page.
     */
    private static void pagerRow(UltraScreenSpec spec, String pageBase, int page, int pages) {
        if (pages <= 1) {
            return;
        }
        String where = "Page " + (page + 1) + " of " + pages;
        if (page > 0) {
            spec.buttonLeft("page:" + UltraPickHelper.pageArg(pageBase, page - 1), UltraStyle.LABEL + "Prev",
                    UltraStyle.STYLE_NORMAL);
        } else {
            spec.lockedButton(where);
        }
        if (page + 1 < pages) {
            spec.buttonRight("page:" + UltraPickHelper.pageArg(pageBase, page + 1), UltraStyle.LABEL + "Next",
                    UltraStyle.STYLE_NORMAL);
        } else {
            spec.lockedButtonRight(where);
        }
        spec.rowGap();
    }

    /** Wallet page: balance, favor, the four point packs, and the way into the picker. */
    static UltraScreenSpec walletSpec(int balance, int favor, boolean selling) {
        UltraScreenSpec spec = sub("mastery_wallet")
                .lineGap(2)
                .bodyPad(4);
        spec.labelValue("Mastery points", String.valueOf(balance));
        spec.labelValue("Favor", String.valueOf(favor));
        spec.line(UltraStyle.LABEL + "1 favor buys " + MasteryWallet.POINTS_PER_FAVOR + " points. 1 point adds "
                + MasteryWallet.MASTERY_PER_POINT + " mastery to a form you have unlocked.");
        spec.line(UltraStyle.LABEL + "God Ki Mastery: " + MasteryWallet.GODKI_POINTS_PER_LEVEL
                + " points a level, open to everyone.");
        spec.line(UltraStyle.dim("Favor spent on points is not refunded, and neither is applied mastery."));
        spec.buttonsAfterLines();
        if (!selling) {
            spec.line(UltraStyle.dim("Points are not on sale right now. Points you have can still be applied."));
        } else {
            int[] packs = MasteryWallet.FAVOR_PACKS;
            for (int i = 0; i < packs.length; i++) {
                int cost = packs[i];
                String raw = MasteryWallet.pointsForFavor(cost) + " points - " + cost + " favor";
                boolean afford = favor >= cost;
                // 4.7.81: a pack is an option (its confirm page asks before paying) - normal, not green.
                String label = afford ? UltraStyle.plain(raw) : UltraStyle.dim(raw);
                String style = afford ? UltraStyle.STYLE_NORMAL : UltraStyle.STYLE_DIM;
                if (i % 2 == 0) {
                    spec.buttonLeft("page:ask:mastery_buy:" + cost, label, style);
                } else {
                    spec.buttonRight("page:ask:mastery_buy:" + cost, label, style);
                    spec.rowGap();
                }
            }
            if (packs.length % 2 == 1) {
                spec.rowGap();
            }
        }
        spec.button("page:mastery_lines", UltraStyle.confirm("Apply points to a form"), UltraStyle.STYLE_CONFIRM)
                .rowGap();
        spec.back();
        return spec;
    }

    /** Form lines with at least one unlocked form, in radial order, 8 per page; says what to do when empty. */
    static UltraScreenSpec linesSpec(List<MasteryWallet.Line> lines, int balance, int page) {
        int pages = UltraPickHelper.pageCount(lines == null ? 0 : lines.size(), MASTERY_PICKS_PER_PAGE);
        int pg = UltraPickHelper.clampPage(page, pages);
        UltraScreenSpec spec = sub(UltraPickHelper.pageArg("mastery_lines", pg))
                .lineGap(2)
                .bodyPad(4);
        spec.labelValue("Mastery points", String.valueOf(balance));
        if (lines == null || lines.isEmpty()) {
            spec.line(UltraStyle.LABEL + "You have no unlocked forms yet, so there is nothing to apply points to.");
            spec.line(UltraStyle.LABEL + "Unlock your first form through its form skill, then come back here.");
            spec.buttonsAfterLines();
        } else {
            spec.line(UltraStyle.LABEL + "Pick a line. God Ki Mastery is open to everyone; the other lines"
                    + " list forms you have unlocked.");
            UltraPickHelper.pageLabel(spec, pg, pages);
            spec.buttonsAfterLines();
            // 4.7.81 (GuiKit): the lines are a picker - normal style, two columns, pager on its own row.
            int placed = 0;
            for (MasteryWallet.Line line : UltraPickHelper.pageSlice(lines, MASTERY_PICKS_PER_PAGE, pg)) {
                if (line.godKi && !line.forms.isEmpty()) {
                    MasteryWallet.Form gk = line.forms.get(0);
                    String gkRaw = line.kiName() + " Level " + MasteryWallet.godKiLevel(gk) + "/"
                            + MasteryWallet.godKiMaxLevel(gk);
                    if (!UltraStyle.fitsButton(gkRaw)) {
                        gkRaw = line.label;
                    }
                    // One form only: straight to its page; Back there returns to this list.
                    pairButton(spec, placed++, "page:mw_form:" + line.token() + ":" + gk.name, UltraStyle.plain(gkRaw),
                            UltraStyle.STYLE_NORMAL);
                    continue;
                }
                int n = line.forms.size();
                String raw = line.label + " (" + n + (n == 1 ? " form)" : " forms)");
                if (!UltraStyle.fitsButton(raw)) {
                    raw = line.label;
                }
                pairButton(spec, placed++, "page:mw_line:" + line.token(), UltraStyle.plain(raw), UltraStyle.STYLE_NORMAL);
            }
            endPairs(spec, placed);
            pagerRow(spec, "mastery_lines", pg, pages);
        }
        spec.back();
        return spec;
    }

    /** The unlocked forms in one line with their mastery, 8 per page; capped forms show dim. */
    static UltraScreenSpec lineSpec(MasteryWallet.Line line, int balance, int page) {
        String base = "mw_line:" + line.token();
        int pages = UltraPickHelper.pageCount(line.forms.size(), MASTERY_PICKS_PER_PAGE);
        int pg = UltraPickHelper.clampPage(page, pages);
        UltraScreenSpec spec = sub(UltraPickHelper.pageArg(base, pg))
                .lineGap(2)
                .bodyPad(4);
        spec.labelValue("Mastery points", String.valueOf(balance));
        spec.labelValue("Form line", line.label);
        spec.line(UltraStyle.LABEL + "Pick a form. Each point adds " + MasteryWallet.MASTERY_PER_POINT
                + " mastery, up to the form's cap.");
        UltraPickHelper.pageLabel(spec, pg, pages);
        spec.buttonsAfterLines();
        for (MasteryWallet.Form form : UltraPickHelper.pageSlice(line.forms, MASTERY_PICKS_PER_PAGE, pg)) {
            boolean full = form.maxPoints() <= 0;
            String raw = form.label + " " + MasteryWallet.whole(form.current) + "/" + MasteryWallet.whole(form.cap);
            if (full) {
                raw = form.label + (form.current >= form.cap ? " - at cap" : " - nearly capped");
            }
            if (!UltraStyle.fitsButton(raw)) {
                raw = form.label;
            }
            // The form page carries this page index so its Back returns here.
            spec.button(
                            "page:" + UltraPickHelper.pageArg("mw_form:" + line.token() + ":" + form.name, pg),
                            full ? UltraStyle.dim(raw) : UltraStyle.plain(raw),
                            full ? UltraStyle.STYLE_DIM : UltraStyle.STYLE_NORMAL)
                    .rowGap();
        }
        UltraPickHelper.placePrevNext(spec, base, pg, pages);
        spec.back();
        return spec;
    }

    /**
     * One form: current mastery, room to the cap, and whole-point amounts that fit. {@code linePage}
     * is the line page the player came from; Back returns there.
     */
    static UltraScreenSpec formSpec(MasteryWallet.Line line, MasteryWallet.Form form, int balance, int linePage) {
        if (line.godKi) {
            return godKiFormSpec(line, form, balance);
        }
        String token = line.token() + ":" + form.name;
        UltraScreenSpec spec = sub(UltraPickHelper.pageArg("mw_form:" + token, Math.max(0, linePage)))
                .lineGap(2)
                .bodyPad(4);
        spec.labelValue("Form", MasteryWallet.formName(line.label, form));
        spec.labelValue("Current mastery", MasteryWallet.whole(form.current) + " / " + MasteryWallet.whole(form.cap));
        spec.labelValue("Room to cap", MasteryWallet.whole(form.headroom()));
        spec.labelValue("Mastery points", String.valueOf(balance));
        int fit = form.maxPoints();
        int can = MasteryWallet.applicable(form.current, form.cap, balance);
        if (fit <= 0) {
            spec.line(UltraStyle.dim(form.current >= form.cap
                    ? "This form is at its mastery cap, so it can't take points."
                    : "Less than " + MasteryWallet.MASTERY_PER_POINT + " mastery is left to the cap. Finish it by training."));
        } else if (balance <= 0) {
            spec.line(UltraStyle.dim("You have no mastery points yet. Buy some on the Mastery wallet page."));
        } else {
            spec.line(UltraStyle.LABEL + "Up to " + fit + " points fit. Each button shows the mastery you end with.");
        }
        spec.buttonsAfterLines();
        List<Integer> amounts = new ArrayList<>();
        for (int a : MasteryWallet.APPLY_AMOUNTS) {
            if (a <= can) {
                amounts.add(a);
            }
        }
        if (can > 0 && !amounts.contains(can)) {
            amounts.add(can);
        }
        for (int i = 0; i < amounts.size(); i++) {
            int a = amounts.get(i);
            String raw = "Apply " + a + " (to " + MasteryWallet.whole(MasteryWallet.resulting(form.current, a, form.cap, form.rate)) + ")";
            if (!UltraStyle.fitsButton(raw)) {
                raw = "Apply " + a;
            }
            String action = "page:ask:mastery_apply:" + token + ":" + a;
            // 4.7.81: an amount is an option (its confirm page asks before applying) - normal, not green.
            pairButton(spec, i, action, UltraStyle.plain(raw), UltraStyle.STYLE_NORMAL);
        }
        if (amounts.size() % 2 == 1) {
            spec.rowGap();
        }
        spec.back();
        return spec;
    }

    /**
     * God Ki / Divine Ki Mastery page: level, mastery, and one Level up button (exactly 10 points).
     * God Ki is shown to every player, Divine Ki once God Ki is mastered; Back returns to the line list.
     */
    static UltraScreenSpec godKiFormSpec(MasteryWallet.Line line, MasteryWallet.Form form, int balance) {
        int level = MasteryWallet.godKiLevel(form);
        int max = MasteryWallet.godKiMaxLevel(form);
        int cost = MasteryWallet.GODKI_POINTS_PER_LEVEL;
        int perLevel = form.rate * cost;
        UltraScreenSpec spec = sub("mw_form:" + line.token() + ":" + form.name)
                .lineGap(2)
                .bodyPad(4);
        String ki = line.kiName();
        boolean god = line.track == KiMasteryTrack.GOD_KI;
        spec.labelValue(line.label, "Level " + level + "/" + max);
        spec.labelValue(ki + " mastery", MasteryWallet.whole(form.current) + " / " + MasteryWallet.whole(form.cap));
        spec.labelValue("Mastery points", String.valueOf(balance));
        spec.line(UltraStyle.LABEL + "Each level costs " + cost + " points and raises " + ki + " form mastery to the"
                + " next " + MasteryWallet.whole(perLevel) + ".");
        spec.line(UltraStyle.dim(god
                ? "Level 1 unlocks God forms. Level " + max + " opens Divine Ki Mastery. Applied points are not refunded."
                : "Applied points are not refunded."));
        if (level >= max) {
            spec.line(UltraStyle.dim(line.label + " is at its top level."));
        } else if (balance < cost) {
            spec.line(UltraStyle.dim("You need " + cost + " points for a level. Buy points on the Mastery wallet page."));
        }
        spec.buttonsAfterLines();
        boolean can = level < max && balance >= cost;
        String raw = level >= max ? "Top level reached" : "Level up (" + cost + " points)";
        spec.button("page:ask:mastery_apply:" + line.token() + ":" + form.name + ":" + cost,
                        can ? UltraStyle.confirm(raw) : UltraStyle.dim(raw),
                        can ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_DIM)
                .rowGap();
        spec.back();
        return spec;
    }

    /**
     * Trade tab (4.7.84, P-1; the old Shop > Exchange shop page, same id {@code exchange}): trade held prestiges
     * for favor. Each pack opens its confirm page.
     */
    private static void openExchange(ServerPlayer player) {
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        int held = parseInt(ph.get("held"), 0);
        UltraScreenSpec spec = base("exchange", "trade")
                .lineGap(2)
                .bodyPad(4);
        paintFavorBalance(spec, ph);
        spec.labelValue("Prestiges you hold:", heldText(ph));
        spec.line(UltraStyle.danger("Each prestige you trade loses its stat bonus. This can't be undone."));
        // Bulk line lists only the amounts that pay a bonus, from the configured payouts.
        int[] amounts = PrestigePointsSystem.turnInAmounts();
        String bulk = bulkLine(amounts, PrestigePointsSystem::pointsForTurnIn);
        if (!bulk.isEmpty()) {
            spec.line(UltraStyle.LABEL + bulk);
        }
        spec.buttonsAfterLines();
        if (!PrestigeShopConfig.get().exchange.enabled) {
            spec.line(UltraStyle.dim("Trading is closed right now."));
        } else {
            for (int i = 0; i < amounts.length; i += 2) {
                exchangeButton(spec, amounts[i], held, true);
                if (i + 1 < amounts.length) {
                    exchangeButton(spec, amounts[i + 1], held, false);
                }
                spec.rowGap();
            }
        }
        spec.back();
        spec.send(player);
    }

    /**
     * "Trade more at once for a bonus: 3 for 4 favor, 6 for 8, 9 for 13." from the bonus packs; empty if none.
     * 4.7.84 (P-3): "favor" is said once.
     */
    static String bulkLine(int[] amounts, java.util.function.IntUnaryOperator payout) {
        List<String> parts = new ArrayList<>();
        for (int a : amounts) {
            int gain = payout.applyAsInt(a);
            if (gain > a) {
                parts.add(a + " for " + gain + (parts.isEmpty() ? " favor" : ""));
            }
        }
        if (parts.isEmpty()) {
            return "";
        }
        return "Trade more at once for a bonus: " + String.join(", ", parts) + ".";
    }

    /** Startup self-test: exchange pack payouts, Bulk line, and button labels that fit. */
    public static void exchangeSelfTest(List<String> broken) {
        PrestigeShopConfig.Exchange fresh = new PrestigeShopConfig.Exchange();
        int[] want = {1, 2, 4, 8, 13, 18, 23, 28};
        // Code defaults only (no PrestigePointsSystem / live config load during mod loading).
        int[] packs = PrestigeShopConfig.Exchange.DEFAULT_AMOUNTS.stream().mapToInt(Integer::intValue).toArray();
        for (int i = 0; i < packs.length; i++) {
            int got = fresh.payout(packs[i]);
            if (i >= want.length || got != want[i]) {
                broken.add("Prestige exchange: " + packs[i] + " should pay "
                        + (i < want.length ? want[i] : "?") + " favor, got " + got);
            }
            String label = exchangeLabel(packs[i], got);
            if (!UltraStyle.fitsButton(label)) {
                broken.add("Prestige exchange: button '" + label + "' does not fit");
            }
        }
        if (!fresh.amounts.equals(PrestigeShopConfig.Exchange.DEFAULT_AMOUNTS)) {
            broken.add("Prestige exchange: default packs should be 1, 2, 3, 6, 9, 12, 15, 18");
        }
        PrestigeShopConfig.Exchange old = new PrestigeShopConfig.Exchange();
        old.amounts = new ArrayList<>(PrestigeShopConfig.Exchange.OLD_DEFAULT_AMOUNTS);
        PrestigeShopConfig.Exchange custom = new PrestigeShopConfig.Exchange();
        custom.amounts = new ArrayList<>(List.of(1, 3, 9));
        if (!PrestigeShopConfig.Exchange.migratePacks18(old) || !old.amounts.equals(PrestigeShopConfig.Exchange.DEFAULT_AMOUNTS)
                || PrestigeShopConfig.Exchange.migratePacks18(custom) || !custom.amounts.equals(List.of(1, 3, 9))
                || PrestigeShopConfig.Exchange.migratePacks18(old)) {
            broken.add("Prestige exchange: only an untouched 1, 2, 3, 6, 9 list should gain 12, 15, 18, once");
        }
        String bulk = bulkLine(packs, fresh::payout);
        if (!bulk.equals("Trade more at once for a bonus: 3 for 4 favor, 6 for 8, 9 for 13, 12 for 18, 15 for 23,"
                + " 18 for 28.")) {
            broken.add("Prestige trade: bonus line reads '" + bulk + "'");
        }
    }

    /** "Trade 18 for 28 favor" (4.7.84, P-3) - fits a standard button for two-digit packs too. */
    static String exchangeLabel(int amount, int gain) {
        return "Trade " + amount + " for " + gain + " favor";
    }

    /**
     * One trade button (left or right half of a row); dim {@code Trade 6 - Locked} when not enough held (the confirm
     * page it still opens says how many you hold).
     */
    private static void exchangeButton(UltraScreenSpec spec, int amount, int held, boolean left) {
        int gain = PrestigePointsSystem.pointsForTurnIn(amount);
        String raw = held >= amount ? exchangeLabel(amount, gain) : "Trade " + amount + " - Locked";
        if (!UltraStyle.fitsButton(raw)) {
            raw = "Trade " + amount;
        }
        // 4.7.81: a pack is an option (its confirm page asks before the exchange) - normal, not green.
        String label = held >= amount ? UltraStyle.plain(raw) : UltraStyle.dim(raw);
        String style = held >= amount ? UltraStyle.STYLE_NORMAL : UltraStyle.STYLE_DIM;
        if (left) {
            spec.buttonLeft("page:ask:turnin:" + amount, label, style);
        } else {
            spec.buttonRight("page:ask:turnin:" + amount, label, style);
        }
    }

    /** All skills on one page. */
    private static void openSkillsShop(ServerPlayer player) {
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        PrestigeShopConfig.SkillShop shop = PrestigeShopConfig.get().skillShop;
        String[] ids = shopSkillIds(ph);
        UltraScreenSpec spec = sub("skills")
                .lineGap(2)
                .bodyPad(4);
        paintFavorBalance(spec, ph);
        // Header from config (4.7.84 copy), e.g. "Each buy adds 1 kept level for 1 favor (Potential Unlock gets 2).
        // Kept levels stay when you prestige."
        StringBuilder header = new StringBuilder("Each buy adds " + shop.levelsPerPurchase + " kept level"
                + (shop.levelsPerPurchase == 1 ? "" : "s") + " for " + shop.costPerPurchase + " favor");
        boolean opened = false;
        for (Map.Entry<String, Integer> o : shop.levelsPerPurchaseOverrides.entrySet()) {
            if (o.getValue() == shop.levelsPerPurchase || !List.of(ids).contains(o.getKey())) {
                continue;
            }
            header.append(opened ? ", " : " (")
                    .append(ph.getOrDefault("skill_" + o.getKey() + "_label", o.getKey()))
                    .append(" gets ").append(o.getValue());
            opened = true;
        }
        if (opened) {
            header.append(')');
        }
        header.append(". Kept levels stay when you prestige.");
        spec.line(UltraStyle.LABEL + header).buttonsAfterLines();
        int favor = parseInt(ph.get("points"), 0);
        if (!shop.enabled) {
            spec.line(UltraStyle.dim("The skill shop is closed right now."));
            spec.back();
            spec.send(player);
            return;
        }
        int placed = 0;
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String label = ph.getOrDefault("skill_" + id + "_label", id);
            int bought = parseInt(ph.get("skill_" + id), 0);
            int max = parseInt(ph.get("skill_" + id + "_max"), 0);
            int cost = shop.costFor(id);
            boolean maxed = max > 0 && bought >= max;
            // Price tag only when this skill's cost differs from the header price.
            String caption = skillCaption(label, bought, max, maxed,
                    cost != shop.costPerPurchase ? cost : -1);
            String action = "page:ask:skill:" + id;
            // 4.7.81: a skill is an option (its confirm page asks before buying) - normal, not green.
            // 4.7.84 (P-2): dim when favor is short, with the shortfall when it fits; it still opens the confirm page.
            boolean shortFavor = !maxed && favor < cost;
            if (shortFavor) {
                caption = shortfallCaption(caption, cost - favor);
            }
            pairButton(spec, placed, action,
                    maxed || shortFavor ? UltraStyle.dim(caption) : UltraStyle.plain(caption),
                    maxed || shortFavor ? UltraStyle.STYLE_DIM : UltraStyle.STYLE_NORMAL);
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        if (placed == 0) {
            spec.line(UltraStyle.dim("No skills for sale right now. Ask staff to show a skill in the prestige shop settings."));
        }
        spec.back();
        spec.send(player);
    }

    private static void openTiers(ServerPlayer player) {
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        PrestigeShopConfig.TierShop shop = PrestigeShopConfig.get().tierShop;
        UltraScreenSpec spec = sub("tiers")
                .lineGap(2)
                .bodyPad(4);
        paintFavorBalance(spec, ph);
        // 4.7.84 (P-3): "Own tiers for good" - what it is, how it differs from Difficulty > Tiers.
        int highest = parseInt(ph.get("tier_highest_purchased"), 0);
        int favor = parseInt(ph.get("points"), 0);
        spec.line(UltraStyle.LABEL + (shop.requireInOrder ? "Own a tier for good, in order." : "Own a tier for good.")
                        + " You still switch it on with Ancient Coins in Difficulty > Tiers.")
                .labelValue("Tiers you own for good:", highest <= 0 ? "none yet" : "up to T" + highest)
                .buttonsAfterLines();
        for (int t = 1; t <= 7; t++) {
            String label = ph.getOrDefault("tier_" + t + "_label", "T" + t);
            String cost = ph.getOrDefault("tier_" + t + "_cost", "?");
            boolean owned = "true".equalsIgnoreCase(ph.get("tier_" + t + "_owned"));
            boolean canBuy = "true".equalsIgnoreCase(ph.get("tier_" + t + "_can_buy"));
            String action = "page:ask:tier:" + t;
            int price = parseInt(cost, 0);
            // 4.7.84 (P-2): next in order but favor is short - dim, with the shortfall.
            boolean shortFavor = !owned && canBuy && shop.forSale(t) && favor < price;
            String tail = owned ? "Owned" : (!shop.forSale(t) ? "Not for sale"
                    : (!canBuy ? "Locked" : shortFavor ? "need " + (price - favor) + " more favor" : cost + " favor"));
            String withName = "T" + t + " " + label + " - " + tail;
            String caption = UltraStyle.fitsButton(withName) ? withName : ("T" + t + " - " + tail);
            if (!UltraStyle.fitsButton(caption)) {
                caption = "T" + t + " - need " + (price - favor);
            }
            // 4.7.81 (GuiKit): two columns, like Difficulty > Tiers.
            if (owned || !canBuy || shortFavor) {
                pairButton(spec, t - 1, action, UltraStyle.dim(caption), UltraStyle.STYLE_DIM);
            } else {
                pairButton(spec, t - 1, action, UltraStyle.confirm(caption), UltraStyle.STYLE_CONFIRM);
            }
        }
        endPairs(spec, 7);
        spec.back();
        spec.send(player);
    }

    // 4.7.84 (P-4): one plain line on what each effect does, from what the shop actually grants
    // (PrestigePointsSystem.runDmzEffect / grantUltimate). Wording only; the effects are DMZ's own.
    // OWNER may edit this effect line
    static final String MAJIN_EFFECT = "Majin: gives you DragonMineZ's Majin mark, a stat boost.";
    // OWNER may edit this effect line
    static final String MUTANT_EFFECT = "Mutant: makes you a DragonMineZ Mutant - more TP and form mastery.";
    // OWNER may edit this effect line
    static final String ULTIMATE_EFFECT = "Ultimate: unlocks the Ultimate form, the one Old Kai can grant.";

    /**
     * Effects (4.7.84): every line first (prices, then one line on what each effect does), then the buttons: Majin
     * and Mutant on one row, Ultimate on the next. A buy the player can't afford is dim with the shortfall (P-2) and
     * still opens its confirm page.
     */
    private static void openEffects(ServerPlayer player) {
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        PrestigeShopConfig cfg = PrestigeShopConfig.get();
        PrestigeShopConfig.Forms forms = cfg.forms;
        int favor = parseInt(ph.get("points"), 0);
        boolean majin = "true".equalsIgnoreCase(ph.get("majin"));
        boolean mutant = "true".equalsIgnoreCase(ph.get("mutant"));
        boolean majinCan = "true".equalsIgnoreCase(ph.get("majin_can_buy"));
        boolean mutantCan = "true".equalsIgnoreCase(ph.get("mutant_can_buy"));
        boolean ultimate = "true".equalsIgnoreCase(ph.get("ultimate"));
        boolean ultimateCan = "true".equalsIgnoreCase(ph.get("ultimate_can_buy"));
        // DMZ ultimate skill from Old Kai with the shop flag unset: the form is already active,
        // so the row shows as unlocked with no Buy and no Remove (a Remove would strip a form the
        // player legitimately earned outside the shop).
        boolean ultimateEarned = "true".equalsIgnoreCase(ph.get("ultimate_earned"));
        UltraScreenSpec spec = sub("effects")
                .lineGap(2)
                .bodyPad(4);
        paintFavorBalance(spec, ph);
        // A form switched off in config still shows for owners, so they can remove it.
        boolean showMajin = forms.majin.enabled || majin;
        boolean showMutant = forms.mutant.enabled || mutant;
        boolean showUltimate = forms.ultimate.enabled || ultimate;
        if (showMajin || showMutant) {
            String price;
            if (showMajin && showMutant && forms.majin.cost != forms.mutant.cost) {
                price = "Majin " + forms.majin.cost + " favor, Mutant " + forms.mutant.cost + " favor";
            } else {
                String name = showMajin && showMutant
                        ? "Majin or Mutant - " : (showMajin ? "Majin - " : "Mutant - ");
                price = name + (showMajin ? forms.majin.cost : forms.mutant.cost) + " favor";
            }
            spec.line(UltraStyle.LABEL + price
                    + (forms.majinMutantExclusive && showMajin && showMutant
                            ? ". You can own one at a time." : "."));
            if (showMajin) {
                spec.line(UltraStyle.VALUE + MAJIN_EFFECT);
            }
            if (showMutant) {
                spec.line(UltraStyle.VALUE + MUTANT_EFFECT);
            }
        }
        if (showUltimate) {
            spec.line(UltraStyle.LABEL + "Ultimate - " + forms.ultimate.cost
                    + " favor. Permanent, stacks with Majin and Mutant.");
            spec.line(UltraStyle.VALUE + ULTIMATE_EFFECT);
        }
        spec.buttonsAfterLines();
        boolean left = true;
        if (majin) {
            effectButton(spec, left, "page:ask:unmajin", UltraStyle.danger("Remove Majin"), UltraStyle.STYLE_DANGER);
            left = false;
        } else if (forms.majin.enabled) {
            effectButton(spec, left, "page:ask:majin", formBuyLabel("Majin", majinCan, "Remove Mutant first",
                    forms.majin.cost, favor), formBuyStyle(majinCan, forms.majin.cost, favor));
            left = false;
        }
        if (mutant) {
            effectButton(spec, left, "page:ask:unmutant", UltraStyle.danger("Remove Mutant"), UltraStyle.STYLE_DANGER);
            left = false;
        } else if (forms.mutant.enabled) {
            effectButton(spec, left, "page:ask:mutant", formBuyLabel("Mutant", mutantCan, "Remove Majin first",
                    forms.mutant.cost, favor), formBuyStyle(mutantCan, forms.mutant.cost, favor));
            left = false;
        }
        if (!left) {
            spec.rowGap();
        }
        // Ultimate is independent of Majin/Mutant: its own row. Off in config and not owned: no row at all.
        if (showUltimate) {
            if (ultimate) {
                // Permanent Ultimate can't be removed or refunded (4.7.21): a dim owned row.
                spec.button("page:effects", UltraStyle.dim("Ultimate owned"), UltraStyle.STYLE_DIM).rowGap();
            } else if (ultimateEarned) {
                // Already unlocked via the DMZ ultimate skill (Old Kai) - nothing to buy.
                spec.button("page:effects", UltraStyle.dim("Already unlocked"), UltraStyle.STYLE_DIM).rowGap();
            } else if (ultimateCan) {
                spec.button("page:ask:ultimate", formBuyLabel("Ultimate", true, "", forms.ultimate.cost, favor),
                                formBuyStyle(true, forms.ultimate.cost, favor))
                        .rowGap();
            }
        }
        // God Ki moved to the Mastery wallet (4.7.21); the Effects row is gone.
        spec.back();
        spec.send(player);
    }

    private static void effectButton(UltraScreenSpec spec, boolean left, String action, String label, String style) {
        if (left) {
            spec.buttonLeft(action, label, style);
        } else {
            spec.buttonRight(action, label, style);
        }
    }

    /** Buy Majin / Mutant / Ultimate: green when it can be bought now, dim with the reason or the favor shortfall. */
    private static String formBuyLabel(String name, boolean can, String blockedLabel, int cost, int favor) {
        if (!can) {
            return UltraStyle.dim(blockedLabel);
        }
        if (favor < cost) {
            return UltraStyle.dim(shortfallCaption("Buy " + name, cost - favor));
        }
        return UltraStyle.confirm("Buy " + name);
    }

    private static String formBuyStyle(boolean can, int cost, int favor) {
        return can && favor >= cost ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_DIM;
    }

    /**
     * 4.7.84 (P-2): a buy the player can't afford reads {@code <caption> - need N more favor}, or a shorter form
     * that fits a button, or the caption alone (the confirm page states the exact shortfall).
     */
    static String shortfallCaption(String caption, int need) {
        for (String c : new String[] {caption + " - need " + need + " more favor", caption + " - need " + need + " more",
                caption + " - need " + need}) {
            if (UltraStyle.fitsButton(c)) {
                return c;
            }
        }
        return caption;
    }

    /** Custom Shop rows: entries on sale, plus switched-off ones the player still owns. */
    private static List<Map.Entry<String, PrestigeShopConfig.CustomEntry>> customRows(ServerPlayer player) {
        List<Map.Entry<String, PrestigeShopConfig.CustomEntry>> rows = new ArrayList<>();
        for (Map.Entry<String, PrestigeShopConfig.CustomEntry> e
                : PrestigeShopConfig.get().customEntries.entrySet()) {
            if (e.getValue().enabled || PrestigeCustomShop.owned(player, e.getKey()) > 0) {
                rows.add(e);
            }
        }
        return rows;
    }

    /** Custom Shop list - one button per perk, opening its detail page. */
    private static void openCustom(ServerPlayer player) {
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        PrestigeShopConfig.CustomShop shop = PrestigeShopConfig.get().customShop;
        UltraScreenSpec spec = sub("custom")
                .lineGap(2)
                .bodyPad(4);
        paintFavorBalance(spec, ph);
        spec.line(UltraStyle.LABEL + shop.menuLabel + " - permanent, survive prestige.")
                .buttonsAfterLines();
        List<Map.Entry<String, PrestigeShopConfig.CustomEntry>> rows =
                shop.enabled ? customRows(player) : List.of();
        for (Map.Entry<String, PrestigeShopConfig.CustomEntry> e : rows) {
            PrestigeShopConfig.CustomEntry entry = e.getValue();
            int owned = PrestigeCustomShop.owned(player, e.getKey());
            boolean maxed = entry.maxPurchases > 0 && owned >= entry.maxPurchases;
            String tail = maxed
                    ? (entry.maxPurchases == 1 ? "owned" : "max")
                    : entry.costFor(owned) + " favor";
            String caption = entry.name
                    + (owned > 0 && entry.maxPurchases != 1 ? " " + owned : "") + " - " + tail;
            if (!UltraStyle.fitsButton(caption)) {
                caption = UltraStyle.buttonText(entry.name);
            }
            boolean buyable = !maxed && PrestigeCustomShop.blockReason(player, e.getKey(), entry) == null;
            spec.button(
                            "page:perk:" + e.getKey(),
                            buyable ? UltraStyle.confirm(caption) : UltraStyle.dim(caption),
                            buyable ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_DIM)
                    .rowGap();
        }
        if (rows.isEmpty()) {
            spec.line(UltraStyle.dim("Nothing for sale right now. Ask staff to stock the shop."));
        }
        spec.back();
        spec.send(player);
    }

    /** One perk: description, rewards, price, Buy / Remove. Staff also see config problems. */
    private static void openPerk(ServerPlayer player, String id) {
        PrestigeShopConfig.CustomEntry entry = PrestigeShopConfig.get().entry(id);
        if (entry == null) {
            openCustom(player);
            return;
        }
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        int owned = PrestigeCustomShop.owned(player, id);
        UltraScreenSpec spec = sub("perk:" + id)
                .lineGap(2)
                .bodyPad(4);
        paintFavorBalance(spec, ph);
        spec.line(UltraStyle.LABEL + entry.name);
        if (!entry.description.isBlank()) {
            spec.line(UltraStyle.dim(entry.description));
        }
        boolean maxed = entry.maxPurchases > 0 && owned >= entry.maxPurchases;
        if (entry.maxPurchases != 1) {
            spec.labelValue("Owned", owned + (entry.maxPurchases > 0 ? "/" + entry.maxPurchases : ""));
        } else {
            spec.labelValue("Owned", owned > 0 ? "yes" : "no");
        }
        if (!maxed) {
            spec.labelValue("Price", entry.costFor(owned) + " favor");
        }
        for (String line : PrestigeCustomShop.rewardLines(entry)) {
            spec.line(UltraStyle.dim(line));
        }
        if (entry.maxPurchases != 1) {
            spec.line(UltraStyle.dim("Each level adds the rewards again."));
        }
        String blockReason = maxed ? null : PrestigeCustomShop.blockReason(player, id, entry);
        if (blockReason != null) {
            spec.line(UltraStyle.danger(blockReason));
        }
        if (StaffAccess.isStaff(player)) {
            for (String problem : PrestigeCustomShop.problems(id, entry)) {
                spec.line(UltraStyle.danger("Staff: " + problem));
            }
            if (!entry.enabled) {
                spec.line(UltraStyle.dim("Staff: not for sale."));
            }
        }
        spec.buttonsAfterLines();
        if (!maxed) {
            String buy = (owned > 0 ? "Buy level " + (owned + 1) : "Buy")
                    + " (" + entry.costFor(owned) + ")";
            spec.button(
                            "page:ask:custom_buy:" + id,
                            blockReason == null ? UltraStyle.confirm(buy) : UltraStyle.dim(buy),
                            blockReason == null ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_DIM)
                    .rowGap();
        }
        if (owned > 0 && entry.removable) {
            spec.button(
                            "page:ask:custom_remove:" + id,
                            UltraStyle.danger(entry.maxPurchases == 1 ? "Remove" : "Remove one level"),
                            UltraStyle.STYLE_DANGER)
                    .rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /**
     * Skill button: short name plus level, and the price when {@code cost >= 0}. Drops the max tag
     * (or price) when the full caption would clip.
     */
    private static String skillCaption(String label, int bought, int max, boolean maxed, int cost) {
        // 4.7.56: the full skill name, as on the ask page; the level and price drop first.
        String name = label == null || label.isBlank() ? "Skill" : label;
        String prog = bought + "/" + (max > 0 ? max : "?");
        String priceTag = cost >= 0 && !maxed ? " (" + cost + ")" : "";
        String withMax = name + " " + prog + (maxed ? " max" : priceTag);
        if (UltraStyle.fitsButton(withMax)) {
            return withMax;
        }
        String base = name + " " + prog;
        return UltraStyle.fitsButton(base) ? base : UltraStyle.buttonText(name);
    }

    private static String[] shopSkillIds(Map<String, String> ph) {
        String raw = ph.getOrDefault("shop_skill_ids", "");
        if (raw == null || raw.isBlank()) {
            return new String[] {"meditation", "fly", "sprint", "jump", "potentialunlock"};
        }
        String[] parts = raw.split(",");
        int n = 0;
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                n++;
            }
        }
        String[] out = new String[n];
        int i = 0;
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                out[i++] = part.trim().toLowerCase(Locale.ROOT);
            }
        }
        return out;
    }

    // ── Confirm pages (ask:<action>:<arg>, 4.7.21) ───────────────────

    /**
     * Where a confirm page sends the player once Confirm ran (or when it is cancelled): the page the
     * spend button sat on. {@code null} when {@code page} is not a confirm page.
     */
    public static String askReturn(String page) {
        if (page == null || !page.toLowerCase(Locale.ROOT).startsWith("ask:")) {
            return null;
        }
        String rest = page.substring("ask:".length());
        int cut = rest.indexOf(':');
        String action = (cut < 0 ? rest : rest.substring(0, cut)).toLowerCase(Locale.ROOT);
        String arg = cut < 0 ? "" : rest.substring(cut + 1);
        return switch (action) {
            case "turnin" -> "exchange";
            case "skill" -> "skills";
            case "tier" -> "tiers";
            case "majin", "mutant", "unmajin", "unmutant", "ultimate" -> "effects";
            case "custom_buy", "custom_remove" -> arg.isBlank() ? "custom" : "perk:" + arg;
            case "mastery_buy" -> "mastery_wallet";
            case "mastery_apply" -> {
                int last = arg.lastIndexOf(':');
                yield last <= 0 ? "mastery_lines" : "mw_form:" + arg.substring(0, last);
            }
            default -> "shop";
        };
    }

    private static void routeAsk(ServerPlayer player, String rest) {
        int cut = rest.indexOf(':');
        String action = (cut < 0 ? rest : rest.substring(0, cut)).toLowerCase(Locale.ROOT).trim();
        String arg = cut < 0 ? "" : rest.substring(cut + 1).trim();
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        int favor = PrestigePointsSystem.getPoints(player);
        switch (action) {
            case "turnin" -> {
                int amount = parseInt(arg, 0);
                turnInAskSpec(amount, parseInt(ph.get("held"), 0), favor,
                        PrestigePointsSystem.pointsForTurnIn(amount),
                        PrestigeShopConfig.get().exchange.enabled
                                && PrestigePointsSystem.isAllowedTurnInAmount(amount)).send(player);
            }
            case "skill" -> askSkill(player, arg, ph, favor);
            case "tier" -> askTier(player, parseInt(arg, 0), favor);
            case "majin", "mutant" -> askFormBuy(player, "majin".equals(action), favor);
            case "unmajin", "unmutant" -> askFormRemove(player, "unmajin".equals(action), favor);
            case "ultimate" -> askUltimate(player, favor);
            case "custom_buy" -> askCustom(player, arg, true, favor);
            case "custom_remove" -> askCustom(player, arg, false, favor);
            case "mastery_buy" -> {
                int pack = parseInt(arg, -1);
                masteryBuyAskSpec(pack, MasteryWallet.balance(player), favor,
                        MasteryWallet.isPack(pack), MasteryWallet.open()).send(player);
            }
            case "mastery_apply" -> askMasteryApply(player, arg);
            default -> openShopHub(player);
        }
    }

    /** Confirm page body: title plus the spec the caller fills in. */
    private static UltraScreenSpec askSpec(String askPage, String title) {
        return sub("ask:" + askPage)
                .lineGap(2)
                .bodyPad(4)
                .line(UltraStyle.header(title));
    }

    /**
     * Confirm page footer: the reason it can't run (no Confirm then) or the Confirm button, and Back
     * to the page the spend button sat on.
     */
    private static UltraScreenSpec askFinish(UltraScreenSpec spec, String block, String doAction, String confirmLabel,
            boolean danger, String backPage, String backLabel) {
        boolean favorShort = isFavorShortfall(block);
        if (block != null) {
            spec.line(UltraStyle.danger(block));
        }
        if (favorShort) {
            spec.line(UltraStyle.LABEL + GET_FAVOR_LINE);
        }
        spec.buttonsAfterLines();
        if (block == null) {
            String raw = UltraStyle.fitsButton(confirmLabel) ? confirmLabel : "Confirm";
            spec.button(doAction,
                            danger ? UltraStyle.danger(raw) : UltraStyle.confirm(raw),
                            danger ? UltraStyle.STYLE_DANGER : UltraStyle.STYLE_CONFIRM)
                    .rowGap();
        } else if (favorShort) {
            placeGetFavorButton(spec);
        }
        spec.back();
        return spec;
    }

    // ── N6 / P-N4 (4.7.84): "not enough favor" offers the way to get more ──────────
    // The one swap point: replace GET_FAVOR_LINE / placeGetFavorButton with the shared N6 helper when it lands.

    /** The line under a "Not enough favor" reason on a confirm page. */
    static final String GET_FAVOR_LINE = "Get favor by trading a prestige on the Trade tab.";

    /** The way to more favor under a "Not enough favor" reason: a normal button to the Trade tab (navigation only). */
    static void placeGetFavorButton(UltraScreenSpec spec) {
        spec.button("page:exchange", UltraStyle.plain("Trade for favor"), UltraStyle.STYLE_NORMAL).rowGap();
    }

    /** True for {@link PrestigePointsSystem#notEnoughFavor}'s text (every favor shortfall on these pages). */
    static boolean isFavorShortfall(String block) {
        return block != null && block.startsWith("Not enough favor");
    }

    private static String plural(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    /** Exchange confirm: prestiges in, favor out, both balances before and after. */
    static UltraScreenSpec turnInAskSpec(int amount, int held, int favor, int gain, boolean open) {
        UltraScreenSpec spec = askSpec("turnin:" + amount, "Trade prestige for favor")
                .labelValue("You give", plural(amount, "prestige", "prestiges"))
                .labelValue("You get", gain + " favor")
                .labelValue("Prestiges you hold", held + " -> " + Math.max(0, held - amount))
                .labelValue("Favor", favor + " -> " + (favor + gain))
                .line(UltraStyle.danger("Traded prestiges lose their stat bonus. This can't be undone."));
        String block = null;
        if (!open) {
            block = "Trading is closed for this amount right now.";
        } else if (held < amount) {
            block = "You hold " + plural(held, "prestige", "prestiges") + " and this trade needs " + amount
                    + ". Reach your next prestige first.";
        }
        // 4.7.81: the exchange cannot be undone, so its button is the danger style.
        return askFinish(spec, block, "do:turnin:" + amount, exchangeLabel(amount, gain), true,
                "exchange", "Trade");
    }

    private static void askSkill(ServerPlayer player, String id, Map<String, String> ph, int favor) {
        PrestigeShopConfig.SkillShop shop = PrestigeShopConfig.get().skillShop;
        PrestigePointsSystem.SkillOffer offer = PrestigePointsSystem.resolveOffer(id);
        String skill = offer == null ? id : offer.id();
        String label = ph.getOrDefault("skill_" + skill + "_label", offer == null ? "Skill" : offer.label());
        int bought = offer == null ? 0 : PrestigePointsSystem.getPurchasedSkillLevels(player, skill);
        int max = offer == null ? 0 : Math.max(1, offer.maxLevel());
        int cost = shop.costFor(skill);
        int gain = Math.min(PrestigePointsSystem.levelsPerPoint(skill), Math.max(0, max - bought));
        UltraScreenSpec spec = askSpec("skill:" + skill, "Confirm skill purchase")
                .labelValue("Skill", label)
                .labelValue("Kept level", bought + " -> " + (bought + gain) + " of " + max)
                .labelValue("Cost", cost + " favor")
                .labelValue("Favor", favor + " -> " + Math.max(0, favor - cost))
                .line(UltraStyle.dim("Kept levels survive prestige. Favor spent is not refunded."));
        String block = null;
        if (!shop.enabled) {
            block = "The skill shop is closed right now.";
        } else if (offer == null || shop.hiddenSkills.contains(skill)) {
            block = "That skill is not sold here. Pick one from the Skill shop.";
        } else if (bought >= max) {
            block = label + " is already at its kept-level max.";
        } else if (favor < cost) {
            block = PrestigePointsSystem.notEnoughFavor(cost, favor);
        }
        askFinish(spec, block, "do:skill:" + skill, "Buy for " + cost + " favor", false, "skills", "Skill shop")
                .send(player);
    }

    private static void askTier(ServerPlayer player, int tierId, int favor) {
        PrestigeShopConfig.TierShop shop = PrestigeShopConfig.get().tierShop;
        var tier = com.dbzlegacy.adaptivedifficulty.tier.UnlockTier.byId(tierId);
        int cost = PrestigePointsSystem.tierPointCost(tierId);
        UltraScreenSpec spec = askSpec("tier:" + tierId, "Confirm permanent tier")
                .labelValue("Tier", tier == null ? "Unknown" : "T" + tierId + " " + tier.display)
                .labelValue("Cost", cost + " favor")
                .labelValue("Favor", favor + " -> " + Math.max(0, favor - cost))
                .line(UltraStyle.dim("Unlocks the tier for good - it survives prestige. You still activate it"
                        + " with Ancient Coins on the Difficulty Tiers page."));
        String block = null;
        if (tier == null) {
            block = "That tier does not exist. Pick one from Own tiers for good.";
        } else if (PrestigePointsSystem.hasPurchasedTier(player, tierId)) {
            block = "You already own permanent T" + tierId + ".";
        } else if (!shop.forSale(tierId)) {
            block = "Permanent T" + tierId + " is not for sale right now.";
        } else if (favor < cost) {
            block = PrestigePointsSystem.notEnoughFavor(cost, favor);
        } else if (shop.requireInOrder) {
            for (int prev = 1; prev < tierId && block == null; prev++) {
                if (!PrestigePointsSystem.hasPurchasedTier(player, prev)) {
                    block = "Buy permanent T" + prev + " first - tiers are bought in order.";
                }
            }
        }
        askFinish(spec, block, "do:tier:" + tierId, "Buy for " + cost + " favor", false, "tiers", "Own tiers for good")
                .send(player);
    }

    private static void askFormBuy(ServerPlayer player, boolean majin, int favor) {
        PrestigeShopConfig.Forms forms = PrestigeShopConfig.get().forms;
        PrestigeShopConfig.FormOffer offer = majin ? forms.majin : forms.mutant;
        String name = majin ? "Majin" : "Mutant";
        String other = majin ? "Mutant" : "Majin";
        UltraScreenSpec spec = askSpec(majin ? "majin" : "mutant", "Confirm Permanent " + name)
                .labelValue("You get", "Permanent " + name)
                .labelValue("Cost", offer.cost + " favor")
                .labelValue("Favor", favor + " -> " + Math.max(0, favor - offer.cost))
                .line(UltraStyle.dim("Survives prestige. Removing it later gives back "
                        + (offer.refund() > 0 ? offer.refund() + " favor." : "no favor.")));
        String block = null;
        if (majin ? PrestigePointsSystem.hasMajin(player) : PrestigePointsSystem.hasMutant(player)) {
            block = "You already own Permanent " + name + ".";
        } else if (!offer.enabled) {
            block = "Permanent " + name + " is not for sale right now.";
        } else if (forms.majinMutantExclusive
                && (majin ? PrestigePointsSystem.hasMutant(player) : PrestigePointsSystem.hasMajin(player))) {
            block = "You have Permanent " + other + ". Remove it first - only one at a time.";
        } else if (favor < offer.cost) {
            block = PrestigePointsSystem.notEnoughFavor(offer.cost, favor);
        }
        askFinish(spec, block, majin ? "do:majin" : "do:mutant", "Buy for " + offer.cost + " favor", false,
                "effects", "Effects").send(player);
    }

    private static void askFormRemove(ServerPlayer player, boolean majin, int favor) {
        PrestigeShopConfig.FormOffer offer = majin
                ? PrestigeShopConfig.get().forms.majin : PrestigeShopConfig.get().forms.mutant;
        String name = majin ? "Majin" : "Mutant";
        int refund = offer.refund();
        UltraScreenSpec spec = askSpec(majin ? "unmajin" : "unmutant", "Remove Permanent " + name)
                .labelValue("You lose", "Permanent " + name)
                .labelValue("Favor back", refund > 0 ? refund + " favor" : "none")
                .labelValue("Favor", favor + " -> " + (favor + Math.max(0, refund)))
                .line(UltraStyle.danger(refund > 0
                        ? "Buying it again later costs the full " + offer.cost + " favor."
                        : "The favor you paid is not refunded."));
        String block = (majin ? PrestigePointsSystem.hasMajin(player) : PrestigePointsSystem.hasMutant(player))
                ? null : "You do not own Permanent " + name + ".";
        askFinish(spec, block, majin ? "do:unmajin" : "do:unmutant", "Remove " + name, true,
                "effects", "Effects").send(player);
    }

    private static void askUltimate(ServerPlayer player, int favor) {
        PrestigeShopConfig.FormOffer offer = PrestigeShopConfig.get().forms.ultimate;
        UltraScreenSpec spec = askSpec("ultimate", "Confirm Permanent Ultimate")
                .labelValue("You get", "Permanent Ultimate")
                .labelValue("Cost", offer.cost + " favor")
                .labelValue("Favor", favor + " -> " + Math.max(0, favor - offer.cost))
                .line(UltraStyle.danger("Permanent: Ultimate can't be removed and the favor is not refunded."));
        String block = null;
        if (PrestigePointsSystem.hasUltimate(player)) {
            block = "You already own Permanent Ultimate.";
        } else if (PrestigePointsSystem.hasUltimateSkill(player)) {
            block = "You already have the Ultimate form from Old Kai - nothing to buy.";
        } else if (!offer.enabled) {
            block = "Permanent Ultimate is not for sale right now.";
        } else if (favor < offer.cost) {
            block = PrestigePointsSystem.notEnoughFavor(offer.cost, favor);
        }
        askFinish(spec, block, "do:ultimate", "Buy for " + offer.cost + " favor", false, "effects", "Effects")
                .send(player);
    }

    private static void askCustom(ServerPlayer player, String rawId, boolean buy, int favor) {
        String id = PrestigeShopConfig.cleanId(rawId);
        PrestigeShopConfig.CustomEntry entry = PrestigeShopConfig.get().entry(id);
        if (entry == null) {
            openCustom(player);
            return;
        }
        int owned = PrestigeCustomShop.owned(player, id);
        String back = "perk:" + id;
        String backLabel = UltraStyle.buttonText(entry.name);
        if (buy) {
            int cost = entry.costFor(owned);
            UltraScreenSpec spec = askSpec("custom_buy:" + id, "Confirm purchase")
                    .labelValue("You get", entry.name + PrestigeCustomShop.levelSuffix(entry, owned + 1))
                    .labelValue("Cost", cost + " favor")
                    .labelValue("Favor", favor + " -> " + Math.max(0, favor - cost));
            for (String line : PrestigeCustomShop.rewardLines(entry)) {
                spec.line(UltraStyle.dim(line));
            }
            spec.line(UltraStyle.dim(entry.removable
                    ? "Permanent - survives prestige."
                    : "Permanent - survives prestige and can't be removed."));
            String block = entry.maxPurchases > 0 && owned >= entry.maxPurchases
                    ? "You already own the most levels of " + entry.name + "."
                    : PrestigeCustomShop.blockReason(player, id, entry);
            if (block == null && favor < cost) {
                block = PrestigePointsSystem.notEnoughFavor(cost, favor);
            }
            askFinish(spec, block, "do:custom_buy:" + id, "Buy for " + cost + " favor", false, back, backLabel)
                    .send(player);
            return;
        }
        int refund = owned > 0 ? PrestigeCustomShop.removeRefund(entry, owned) : 0;
        UltraScreenSpec spec = askSpec("custom_remove:" + id, "Remove perk")
                .labelValue("You lose", entry.name + PrestigeCustomShop.levelSuffix(entry, owned))
                .labelValue("Favor back", refund > 0 ? refund + " favor" : "none")
                .labelValue("Favor", favor + " -> " + (favor + refund))
                .line(UltraStyle.danger(refund > 0
                        ? "Buying it again later costs the full price."
                        : "The favor you paid is not refunded."));
        String block = owned <= 0 ? "You do not own " + entry.name + "."
                : (!entry.removable ? entry.name + " can't be removed." : null);
        askFinish(spec, block, "do:custom_remove:" + id, "Remove", true, back, backLabel).send(player);
    }

    /** Point pack confirm: favor in, points out, both balances before and after. */
    static UltraScreenSpec masteryBuyAskSpec(int pack, int balance, int favor, boolean isPack, boolean selling) {
        int points = MasteryWallet.pointsForFavor(pack);
        UltraScreenSpec spec = askSpec("mastery_buy:" + pack, "Confirm point purchase")
                .labelValue("You pay", pack + " favor")
                .labelValue("You get", points + " mastery points")
                .labelValue("Mastery points", balance + " -> " + (balance + points))
                .labelValue("Favor", favor + " -> " + Math.max(0, favor - pack))
                .line(UltraStyle.dim("Favor spent on points is not refunded."));
        String block = null;
        if (!isPack) {
            block = "That point pack is not on sale. Pick one on the Mastery wallet page.";
        } else if (!selling) {
            block = "Points are not on sale right now. Points you have can still be applied.";
        } else if (favor < pack) {
            block = PrestigePointsSystem.notEnoughFavor(pack, favor);
        }
        return askFinish(spec, block, "do:mastery_buy:" + pack, "Buy " + points + " points", false,
                "mastery_wallet", "Mastery wallet");
    }

    private static void askMasteryApply(ServerPlayer player, String arg) {
        String[] parts = arg.split(":");
        if (parts.length < 4) {
            openMasteryLines(player, 0);
            return;
        }
        int points = parseInt(parts[parts.length - 1], 0);
        String formName = parts[parts.length - 2];
        String token = String.join(":", java.util.Arrays.copyOfRange(parts, 0, parts.length - 2));
        MasteryWallet.Line line = MasteryWallet.line(player, token);
        MasteryWallet.Form form = line == null ? null : line.form(formName);
        if (form == null) {
            openMasteryLines(player, 0);
            return;
        }
        masteryApplyAskSpec(line, form, points, MasteryWallet.balance(player),
                PrestigeShopConfig.get().godKi.enabled).send(player);
    }

    /** Apply confirm: points out, mastery before and after (God Ki: the level and its mastery). */
    static UltraScreenSpec masteryApplyAskSpec(MasteryWallet.Line line, MasteryWallet.Form form, int points,
            int balance, boolean godKiOnSale) {
        String token = line.token() + ":" + form.name;
        String back = "mw_form:" + token;
        String doAction = "do:mastery_apply:" + token + ":" + points;
        if (line.godKi) {
            int level = MasteryWallet.godKiLevel(form);
            int max = MasteryWallet.godKiMaxLevel(form);
            double perLevel = (double) form.rate * MasteryWallet.GODKI_POINTS_PER_LEVEL;
            boolean god = line.track == KiMasteryTrack.GOD_KI;
            UltraScreenSpec spec = askSpec("mastery_apply:" + token + ":" + points, "Confirm " + line.label + " level")
                    .labelValue(line.label, "Level " + level + " -> " + Math.min(max, level + 1) + " of " + max)
                    .labelValue(line.kiName() + " mastery", MasteryWallet.whole(form.current) + " -> "
                            + MasteryWallet.whole(Math.min(form.cap, form.current + perLevel)))
                    .labelValue("Cost", MasteryWallet.GODKI_POINTS_PER_LEVEL + " mastery points")
                    .labelValue("Mastery points", balance + " -> " + Math.max(0, balance - points))
                    .line(UltraStyle.dim((god && level == 0 ? "Level 1 unlocks God forms. " : "")
                            + "Applied points are not refunded."));
            String block = null;
            if (points != MasteryWallet.GODKI_POINTS_PER_LEVEL) {
                block = "A level costs exactly " + MasteryWallet.GODKI_POINTS_PER_LEVEL + " points.";
            } else if (level >= max) {
                block = line.label + " is at its top level.";
            } else if (!godKiOnSale) {
                block = line.label + " is not on sale right now.";
            } else if (balance < points) {
                block = "Not enough points: this costs " + points + " points and you have " + balance + ". Buy points on the Mastery wallet page.";
            }
            return askFinish(spec, block, doAction, "Level up for " + points + " points", false, back,
                    line.label);
        }
        UltraScreenSpec spec = askSpec("mastery_apply:" + token + ":" + points, "Confirm mastery points")
                .labelValue("Form", MasteryWallet.formName(line.label, form))
                .labelValue("Mastery", MasteryWallet.whole(form.current) + " -> "
                        + MasteryWallet.whole(MasteryWallet.resulting(form.current, points, form.cap, form.rate))
                        + " (cap " + MasteryWallet.whole(form.cap) + ")")
                .labelValue("Cost", plural(points, "mastery point", "mastery points"))
                .labelValue("Mastery points", balance + " -> " + Math.max(0, balance - points))
                .line(UltraStyle.dim("Applied mastery is not refunded."));
        String block = null;
        int fit = form.maxPoints();
        if (points <= 0) {
            block = "Pick how many points to apply on the form page.";
        } else if (fit <= 0) {
            block = "This form can't take more points.";
        } else if (points > fit) {
            block = "At most " + fit + " points fit before the cap.";
        } else if (points > balance) {
            block = "You have " + balance + " points. Pick a smaller amount or buy more.";
        }
        return askFinish(spec, block, doAction, "Apply " + points + " points", false, back, form.label);
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
