package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.gui.ProgressionGuiApi;
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
 * Prestige Ultra UI - Home / Prestige / Shop.
 *
 * <p>Prestige tab: prestige now (GUI confirm) + favor wording. Exchange shop lives under Shop.
 * Shop catalogs show Prestige favor balance only (no full wallet dump).
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
            case "turnin", "points", "prestige" -> openTurnIn(player);
            case "confirm", "prestige_confirm" -> openConfirm(player);
            case "shop", "shop_hub", "catalog" -> openShopHub(player);
            case "exchange", "turnin_shop", "favor_shop" -> openExchange(player);
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
                .title("Prestige")
                .page(page)
                .activeTab(tab)
                .tabs("home|Home", "prestige|Prestige", "shop|Shop");
    }

    /** Subpage: no tab row - dual footer (Back + Main menu). */
    private static UltraScreenSpec sub(String page) {
        return UltraScreenSpec.of("prestige")
                .title("Prestige")
                .page(page);
    }

    /** Full wallet for Home / Prestige tabs. */
    private static void paintWallet(UltraScreenSpec spec, Map<String, String> ph) {
        spec.labelValue("Lifetime prestiges", ph.getOrDefault("completed", "0"));
        spec.labelValue(
                "Current prestige amount",
                ph.getOrDefault("held", "0") + "/" + ph.getOrDefault("held_max", String.valueOf(com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulPrestigeIntegration.OVERHAUL_MAX_PRESTIGE)));
        spec.labelValue("Prestige favor", ph.getOrDefault("points", "0"));
    }

    /** Shop pages: favor balance only (no player info dump). */
    private static void paintFavorBalance(UltraScreenSpec spec, Map<String, String> ph) {
        spec.labelValue("Prestige favor", ph.getOrDefault("points", "0"));
    }

    private static void openHome(ServerPlayer player) {
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        UltraScreenSpec spec = base("main", "home")
                .lineGap(2)
                .bodyPad(4);
        paintWallet(spec, ph);
        if (!"true".equalsIgnoreCase(ph.get("system_enabled"))) {
            spec.line(UltraStyle.danger("Prestige system is disabled. Ask staff to turn it on."));
        } else {
            spec.labelValue("Need", ph.getOrDefault("required_fmt", "0"));
            spec.line(UltraStyle.dim("Reaching your current cap earns your next prestige."));
            if ("true".equalsIgnoreCase(ph.get("ready"))) {
                spec.line(UltraStyle.confirm("Ready to prestige."));
            } else {
                spec.line(UltraStyle.dim("Reach your cap first."));
            }
        }
        spec.buttonsAfterLines();
        spec.footerBackToMain();
        spec.send(player);
    }

    /** Prestige tab: status + Prestige now (confirm is a GUI subpage). Exchange is under Shop. */
    private static void openTurnIn(ServerPlayer player) {
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        UltraScreenSpec spec = base("turnin", "prestige")
                .lineGap(2)
                .bodyPad(4);
        paintWallet(spec, ph);
        if (PrestigeShopConfig.get().exchange.enabled) {
            spec.line(UltraStyle.LABEL
                    + "Prestige favor - exchange current prestige on the Shop tab (loses the stat bonus).");
        }
        spec.buttonsAfterLines();
        boolean ready = "true".equalsIgnoreCase(ph.get("ready"));
        if (ready) {
            spec.button("page:confirm", UltraStyle.danger("Prestige now"), UltraStyle.STYLE_DANGER)
                    .rowGap();
        } else {
            spec.button(
                            "page:confirm",
                            UltraStyle.dim("Need higher level"),
                            UltraStyle.STYLE_DIM)
                    .rowGap();
        }
        spec.footerBackToMain();
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
                .line(UltraStyle.LABEL + "Confirm prestige")
                .labelValue("Current prestige amount",
                        ph.getOrDefault("held", "0") + "/" + ph.getOrDefault("held_max", String.valueOf(com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulPrestigeIntegration.OVERHAUL_MAX_PRESTIGE)))
                .labelValue("Need", ph.getOrDefault("required_fmt", "0"))
                .notice("This resets your stats and adds one to your current prestige amount.");
        if (PrestigeUniverseRoleWarn.isGodOfDestruction(player)) {
            spec.line(UltraStyle.danger(PrestigeUniverseRoleWarn.GOD_OF_DESTRUCTION));
        }
        if (PrestigeUniverseRoleWarn.isSupremeKai(player)) {
            spec.line(UltraStyle.danger(PrestigeUniverseRoleWarn.SUPREME_KAI));
        }
        boolean ready = "true".equalsIgnoreCase(ph.get("ready"));
        if (!ready) {
            spec.line(UltraStyle.danger("Reach your level cap before you can prestige."));
        }
        spec.buttonsAfterLines();
        if (ready) {
            spec.button(
                            "do:confirm_now",
                            UltraStyle.danger("Confirm prestige"),
                            UltraStyle.STYLE_DANGER)
                    .rowGap();
        }
        spec.footerBack("page:turnin", "Prestige");
        spec.send(player);
    }

    /** Shop hub - favor catalogs + exchange submenu; sections switched off in config are hidden. */
    private static void openShopHub(ServerPlayer player) {
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        PrestigeShopConfig cfg = PrestigeShopConfig.get();
        UltraScreenSpec spec = base("shop", "shop")
                .lineGap(2)
                .bodyPad(4);
        paintFavorBalance(spec, ph);
        spec.line(UltraStyle.LABEL + "Spend Prestige favor. Purchases survive prestige.")
                .buttonsAfterLines();
        if (cfg.exchange.enabled) {
            spec.button("page:exchange", UltraStyle.confirm("Exchange shop"), UltraStyle.STYLE_CONFIRM)
                    .rowGap();
        }
        if (cfg.skillShop.enabled) {
            spec.button("page:skills", UltraStyle.confirm("Skill shop"), UltraStyle.STYLE_CONFIRM)
                    .rowGap();
        }
        if (cfg.tierShop.enabled) {
            spec.button(
                            "page:tiers",
                            UltraStyle.confirm("Difficulty tiers"),
                            UltraStyle.STYLE_CONFIRM)
                    .rowGap();
        }
        spec.button("page:effects", UltraStyle.confirm("Effects"), UltraStyle.STYLE_CONFIRM)
                .rowGap();
        // Shown while selling, or while the player still has points to apply.
        if (cfg.masteryWallet.enabled || MasteryWallet.balance(player) > 0) {
            spec.button("page:mastery_wallet", UltraStyle.confirm("Mastery wallet"), UltraStyle.STYLE_CONFIRM)
                    .rowGap();
        }
        if (cfg.customShop.enabled && !customRows(player).isEmpty()) {
            spec.button(
                            "page:custom",
                            UltraStyle.confirm(UltraStyle.buttonText(cfg.customShop.menuLabel)),
                            UltraStyle.STYLE_CONFIRM)
                    .rowGap();
        }
        if (StaffAccess.isStaff(player)) {
            spec.button("page:staff_edit", UltraStyle.danger("Edit shop (staff)"), UltraStyle.STYLE_DANGER)
                    .rowGap();
        }
        spec.footerBackToMain();
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

    /** Wallet page: balance, favor, the four point packs, and the way into the picker. */
    static UltraScreenSpec walletSpec(int balance, int favor, boolean selling) {
        UltraScreenSpec spec = sub("mastery_wallet")
                .lineGap(2)
                .bodyPad(4);
        spec.labelValue("Mastery points", String.valueOf(balance));
        spec.labelValue("Prestige favor", String.valueOf(favor));
        spec.line(UltraStyle.LABEL + "1 favor buys " + MasteryWallet.POINTS_PER_FAVOR + " points. 1 point adds "
                + MasteryWallet.MASTERY_PER_POINT + " mastery to a form you have unlocked.");
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
                String label = afford ? UltraStyle.confirm(raw) : UltraStyle.dim(raw);
                String style = afford ? "confirm" : "dim";
                if (i % 2 == 0) {
                    spec.buttonLeft("do:mastery_buy:" + cost, label, style);
                } else {
                    spec.buttonRight("do:mastery_buy:" + cost, label, style);
                    spec.rowGap();
                }
            }
            if (packs.length % 2 == 1) {
                spec.rowGap();
            }
        }
        spec.button("page:mastery_lines", UltraStyle.confirm("Apply points to a form"), UltraStyle.STYLE_CONFIRM)
                .rowGap();
        spec.footerBack("page:shop", "Shop");
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
            spec.line(UltraStyle.LABEL + "Pick a form line. Only lines with a form you have unlocked are listed.");
            UltraPickHelper.pageLabel(spec, pg, pages);
            spec.buttonsAfterLines();
            for (MasteryWallet.Line line : UltraPickHelper.pageSlice(lines, MASTERY_PICKS_PER_PAGE, pg)) {
                int n = line.forms.size();
                String raw = line.label + " (" + n + (n == 1 ? " form)" : " forms)");
                if (!UltraStyle.fitsButton(raw)) {
                    raw = line.label;
                }
                spec.button("page:mw_line:" + line.token(), UltraStyle.confirm(raw), UltraStyle.STYLE_CONFIRM)
                        .rowGap();
            }
            UltraPickHelper.placePrevNext(spec, "mastery_lines", pg, pages);
        }
        spec.footerBack("page:mastery_wallet", "Mastery wallet");
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
                            full ? UltraStyle.dim(raw) : UltraStyle.confirm(raw),
                            full ? UltraStyle.STYLE_DIM : UltraStyle.STYLE_CONFIRM)
                    .rowGap();
        }
        UltraPickHelper.placePrevNext(spec, base, pg, pages);
        spec.footerBack("page:mastery_lines", "Form lines");
        return spec;
    }

    /**
     * One form: current mastery, room to the cap, and whole-point amounts that fit. {@code linePage}
     * is the line page the player came from; Back returns there.
     */
    static UltraScreenSpec formSpec(MasteryWallet.Line line, MasteryWallet.Form form, int balance, int linePage) {
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
            String raw = "Apply " + a + " (to " + MasteryWallet.whole(MasteryWallet.resulting(form.current, a, form.cap)) + ")";
            if (!UltraStyle.fitsButton(raw)) {
                raw = "Apply " + a;
            }
            String action = "do:mastery_apply:" + token + ":" + a;
            if (i % 2 == 0) {
                spec.buttonLeft(action, UltraStyle.confirm(raw), "confirm");
            } else {
                spec.buttonRight(action, UltraStyle.confirm(raw), "confirm");
                spec.rowGap();
            }
        }
        if (amounts.size() % 2 == 1) {
            spec.rowGap();
        }
        spec.footerBack("page:" + UltraPickHelper.pageArg("mw_line:" + line.token(), Math.max(0, linePage)), line.label);
        return spec;
    }

    /** Exchange current prestige for favor (was on Prestige tab). */
    private static void openExchange(ServerPlayer player) {
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        int held = parseInt(ph.get("held"), 0);
        UltraScreenSpec spec = sub("exchange")
                .lineGap(2)
                .bodyPad(4);
        paintFavorBalance(spec, ph);
        spec.labelValue(
                "Current prestige amount",
                ph.getOrDefault("held", "0") + "/" + ph.getOrDefault("held_max", String.valueOf(com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulPrestigeIntegration.OVERHAUL_MAX_PRESTIGE)));
        spec.line(UltraStyle.danger(
                "Exchanging prestige for Prestige favor removes the stat bonus."));
        // Bulk line lists only the amounts that pay a bonus, from the configured payouts.
        int[] amounts = PrestigePointsSystem.turnInAmounts();
        StringBuilder bulk = new StringBuilder();
        for (int a : amounts) {
            int gain = PrestigePointsSystem.pointsForTurnIn(a);
            if (gain > a) {
                bulk.append(bulk.length() == 0 ? "Bulk: " : ", ")
                        .append(a).append(" for ").append(gain).append(" favor");
            }
        }
        if (bulk.length() > 0) {
            spec.line(UltraStyle.LABEL + bulk + ".");
        }
        spec.buttonsAfterLines();
        if (!PrestigeShopConfig.get().exchange.enabled) {
            spec.line(UltraStyle.dim("The exchange is closed right now."));
        } else {
            for (int i = 0; i < amounts.length; i += 2) {
                exchangeButton(spec, amounts[i], held, true);
                if (i + 1 < amounts.length) {
                    exchangeButton(spec, amounts[i + 1], held, false);
                }
                spec.rowGap();
            }
        }
        spec.footerBack("page:shop", "Shop");
        spec.send(player);
    }

    /** One exchange button (left or right half of a row); dim when not enough held. */
    private static void exchangeButton(UltraScreenSpec spec, int amount, int held, boolean left) {
        String gain = String.valueOf(PrestigePointsSystem.pointsForTurnIn(amount));
        String raw = held >= amount
                ? "Exchange " + amount + " (+" + gain + " favor)"
                : "Exchange " + amount + " - need " + amount;
        if (!UltraStyle.fitsButton(raw)) {
            raw = held >= amount ? "Exchange " + amount : "Need " + amount;
        }
        String label = held >= amount ? UltraStyle.confirm(raw) : UltraStyle.dim(raw);
        String style = held >= amount ? "confirm" : "dim";
        if (left) {
            spec.buttonLeft("do:turnin:" + amount, label, style);
        } else {
            spec.buttonRight("do:turnin:" + amount, label, style);
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
        // Header from config, e.g. "Skill levels - 1 favor each (Potential Unlock +2)".
        StringBuilder header = new StringBuilder(
                "Skill levels - " + shop.costPerPurchase + " favor each");
        if (shop.levelsPerPurchase > 1) {
            header.append(" (+").append(shop.levelsPerPurchase).append(" levels)");
        }
        for (Map.Entry<String, Integer> o : shop.levelsPerPurchaseOverrides.entrySet()) {
            if (o.getValue() == shop.levelsPerPurchase || !List.of(ids).contains(o.getKey())) {
                continue;
            }
            header.append(header.indexOf("(") < 0 ? " (" : ", ")
                    .append(ph.getOrDefault("skill_" + o.getKey() + "_label", o.getKey()))
                    .append(" +").append(o.getValue());
        }
        if (header.indexOf("(") >= 0 && header.charAt(header.length() - 1) != ')') {
            header.append(')');
        }
        spec.line(UltraStyle.LABEL + header).buttonsAfterLines();
        if (!shop.enabled) {
            spec.line(UltraStyle.dim("The skill shop is closed right now."));
            spec.footerBack("page:shop", "Shop");
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
            String action = "do:skill:" + id;
            if (placed % 2 == 0) {
                spec.buttonLeft(
                        action,
                        maxed ? UltraStyle.dim(caption) : UltraStyle.confirm(caption),
                        maxed ? "dim" : "confirm");
            } else {
                spec.buttonRight(
                        action,
                        maxed ? UltraStyle.dim(caption) : UltraStyle.confirm(caption),
                        maxed ? "dim" : "confirm");
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        if (placed == 0) {
            spec.line(UltraStyle.dim("No skills for sale right now. Ask staff to show a skill in the prestige shop settings."));
        }
        spec.footerBack("page:shop", "Shop");
        spec.send(player);
    }

    private static void openTiers(ServerPlayer player) {
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        PrestigeShopConfig.TierShop shop = PrestigeShopConfig.get().tierShop;
        UltraScreenSpec spec = sub("tiers")
                .lineGap(2)
                .bodyPad(4);
        paintFavorBalance(spec, ph);
        spec.line(UltraStyle.LABEL + (shop.requireInOrder
                        ? "Difficulty tiers - buy in order, activate with coins."
                        : "Difficulty tiers - activate with coins."))
                .labelValue("Highest owned", "T" + ph.getOrDefault("tier_highest_purchased", "0"))
                .buttonsAfterLines();
        for (int t = 1; t <= 7; t++) {
            String label = ph.getOrDefault("tier_" + t + "_label", "T" + t);
            String cost = ph.getOrDefault("tier_" + t + "_cost", "?");
            boolean owned = "true".equalsIgnoreCase(ph.get("tier_" + t + "_owned"));
            boolean canBuy = "true".equalsIgnoreCase(ph.get("tier_" + t + "_can_buy"));
            String action = "do:tier:" + t;
            String tail = owned ? "owned" : (!shop.forSale(t) ? "n/a" : (canBuy ? cost : "lock"));
            String withName = "T" + t + " " + label + " " + tail;
            String caption = UltraStyle.fitsButton(withName) ? withName : ("T" + t + " " + tail);
            if (owned || !canBuy) {
                spec.button(action, UltraStyle.dim(caption), UltraStyle.STYLE_DIM).rowGap();
            } else {
                spec.button(action, UltraStyle.confirm(caption), UltraStyle.STYLE_CONFIRM).rowGap();
            }
        }
        spec.footerBack("page:shop", "Shop");
        spec.send(player);
    }

    private static void openEffects(ServerPlayer player) {
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        PrestigeShopConfig cfg = PrestigeShopConfig.get();
        PrestigeShopConfig.Forms forms = cfg.forms;
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
        if (showMajin || showMutant) {
            String price;
            if (showMajin && showMutant && forms.majin.cost != forms.mutant.cost) {
                price = "Majin " + forms.majin.cost + " / Mutant " + forms.mutant.cost + " favor";
            } else {
                String name = showMajin && showMutant
                        ? "Majin / Mutant - " : (showMajin ? "Majin - " : "Mutant - ");
                price = name + (showMajin ? forms.majin.cost : forms.mutant.cost) + " favor";
            }
            spec.line(UltraStyle.LABEL + price
                            + (forms.majinMutantExclusive && showMajin && showMutant
                                    ? ". One at a time." : "."))
                    .buttonsAfterLines();
            if (majin) {
                spec.button(
                                "do:unmajin",
                                UltraStyle.danger("Remove Majin"),
                                UltraStyle.STYLE_DANGER)
                        .rowGap();
            } else if (forms.majin.enabled) {
                if (majinCan) {
                    spec.button(
                                    "do:majin",
                                    UltraStyle.confirm("Buy Majin"),
                                    UltraStyle.STYLE_CONFIRM)
                            .rowGap();
                } else {
                    spec.button(
                                    "do:majin",
                                    UltraStyle.dim("Need Mutant off"),
                                    UltraStyle.STYLE_DIM)
                            .rowGap();
                }
            }
            if (mutant) {
                spec.button(
                                "do:unmutant",
                                UltraStyle.danger("Remove Mutant"),
                                UltraStyle.STYLE_DANGER)
                        .rowGap();
            } else if (forms.mutant.enabled) {
                if (mutantCan) {
                    spec.button(
                                    "do:mutant",
                                    UltraStyle.confirm("Buy Mutant"),
                                    UltraStyle.STYLE_CONFIRM)
                            .rowGap();
                } else {
                    spec.button(
                                    "do:mutant",
                                    UltraStyle.dim("Need Majin off"),
                                    UltraStyle.STYLE_DIM)
                            .rowGap();
                }
            }
        } else {
            spec.buttonsAfterLines();
        }
        // Ultimate is independent of Majin/Mutant: own label line, own row. No
        // buttonsAfterLines() here - the row cursor already sits below the Mutant button, and
        // re-deriving it from the line count would drop this button on top of the pair above.
        // Off in config and not owned: no Ultimate row at all.
        boolean showUltimate = forms.ultimate.enabled || ultimate;
        if (showUltimate) {
            spec.line(UltraStyle.LABEL + "Ultimate - " + forms.ultimate.cost
                    + " favor. Stacks with Majin and Mutant.");
            if (ultimate) {
                spec.button(
                                "do:unultimate",
                                UltraStyle.danger("Remove Ultimate"),
                                UltraStyle.STYLE_DANGER)
                        .rowGap();
            } else if (ultimateEarned) {
                // Already unlocked via the DMZ ultimate skill (Old Kai) — no Buy, no Remove. Dim
                // no-op row keeps the button-row cursor on the same slot the other states occupy, so
                // the God Ki row below never shifts up into it.
                spec.button(
                                "do:ultimate",
                                UltraStyle.dim("Already unlocked"),
                                UltraStyle.STYLE_DIM)
                        .rowGap();
            } else if (ultimateCan) {
                spec.button(
                                "do:ultimate",
                                UltraStyle.confirm("Buy Ultimate"),
                                UltraStyle.STYLE_CONFIRM)
                        .rowGap();
            }
        }
        int godkiInvested = parseInt(ph.get("godki_invested"), 0);
        // God Ki off in config: hidden unless the player already invested.
        if (!cfg.godKi.enabled && godkiInvested <= 0) {
            spec.footerBack("page:shop", "Shop");
            spec.send(player);
            return;
        }
        boolean godkiMaxed = "true".equalsIgnoreCase(ph.get("godki_maxed"));
        String godkiCost = ph.getOrDefault("godki_cost", "1");
        String godkiPerPoint = ph.getOrDefault("godki_per_point", "1,000");
        String godkiMastery = ph.getOrDefault("godki_mastery", "0");
        spec.line(UltraStyle.LABEL + "God Ki Enhancement - " + godkiCost
                        + " favor per point (+" + godkiPerPoint + " mastery).")
                .labelValue(
                        "Invested",
                        godkiInvested + "/" + cfg.godKi.maxPoints)
                .labelValue("God Ki mastery", godkiMastery);
        // No buttonsAfterLines() anymore: the body estimate caps at 140px, so re-deriving the
        // God Ki row from the line count would land on top of the Ultimate button. The row
        // cursor already sits below it.
        if (godkiMaxed) {
            spec.button(
                            "do:godki",
                            UltraStyle.dim(godkiInvested >= cfg.godKi.maxPoints
                                    ? "Fully invested" : "Not for sale"),
                            UltraStyle.STYLE_DIM)
                    .rowGap();
        } else {
            spec.button(
                            "do:godki",
                            UltraStyle.confirm("Invest God Ki (+" + godkiPerPoint + ")"),
                            UltraStyle.STYLE_CONFIRM)
                    .rowGap();
        }
        spec.footerBack("page:shop", "Shop");
        spec.send(player);
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
            spec.line(UltraStyle.dim("Nothing for sale right now."));
        }
        spec.footerBack("page:shop", "Shop");
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
            spec.line(UltraStyle.dim("Staff: id " + id + (entry.enabled ? "" : " (not for sale)")));
        }
        spec.buttonsAfterLines();
        if (!maxed) {
            String buy = (owned > 0 ? "Buy level " + (owned + 1) : "Buy")
                    + " (" + entry.costFor(owned) + ")";
            spec.button(
                            "do:custom_buy:" + id,
                            blockReason == null ? UltraStyle.confirm(buy) : UltraStyle.dim(buy),
                            blockReason == null ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_DIM)
                    .rowGap();
        }
        if (owned > 0 && entry.removable) {
            spec.button(
                            "do:custom_remove:" + id,
                            UltraStyle.danger(entry.maxPurchases == 1 ? "Remove" : "Remove one level"),
                            UltraStyle.STYLE_DANGER)
                    .rowGap();
        }
        spec.footerBack("page:custom", UltraStyle.buttonText(PrestigeShopConfig.get().customShop.menuLabel));
        spec.send(player);
    }

    /**
     * Skill button: short name plus level, and the price when {@code cost >= 0}. Drops the max tag
     * (or price) when the full caption would clip.
     */
    private static String skillCaption(String label, int bought, int max, boolean maxed, int cost) {
        String name = switch (label == null ? "" : label) {
            case "Instant Transmission" -> "Transmit";
            case "Defense Penetration" -> "Def Pen";
            case "Potential Unlock" -> "Potential";
            case "Ki Manipulation" -> "Ki Manip";
            case "Healing Reduction" -> "Heal Cut";
            case "Ki Protection" -> "Ki Prot";
            default -> label == null || label.isBlank() ? "Skill" : label;
        };
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

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
