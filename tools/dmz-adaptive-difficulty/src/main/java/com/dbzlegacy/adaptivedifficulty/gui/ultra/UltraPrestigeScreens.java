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
 * Prestige Ultra UI - Home / Prestige / Shop.
 *
 * <p>Prestige tab: prestige now (GUI confirm) + favor wording. Exchange shop lives under Shop.
 * Shop catalogs show Prestige favor balance only (no full wallet dump).
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
            // The level cap is the next-prestige target from the held-count ladder
            // (LmOverhaulCapMath.prestigeRequirement: 50k at 0 held, +15k per held, 150k from 7).
            spec.labelValue("Level cap", ph.getOrDefault("required_fmt", "0"));
            spec.line(UltraStyle.dim("Reach your level cap to earn your next prestige."));
            if ("true".equalsIgnoreCase(ph.get("ready"))) {
                spec.line(UltraStyle.confirm("Ready to prestige."));
            } else {
                spec.line(UltraStyle.dim("Reach level " + ph.getOrDefault("required_fmt", "0") + " first."));
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
                            UltraStyle.dim("Reach level " + ph.getOrDefault("required_fmt", "0") + " first"),
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
                .labelValue("Level cap", ph.getOrDefault("required_fmt", "0"))
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
        // Edit prestige shop lives on Staff Admin (4.7.31); page:staff_edit still works for old links.
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
                String label = afford ? UltraStyle.confirm(raw) : UltraStyle.dim(raw);
                String style = afford ? "confirm" : "dim";
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
            spec.line(UltraStyle.LABEL + "Pick a line. God Ki Mastery is open to everyone; the other lines"
                    + " list forms you have unlocked.");
            UltraPickHelper.pageLabel(spec, pg, pages);
            spec.buttonsAfterLines();
            for (MasteryWallet.Line line : UltraPickHelper.pageSlice(lines, MASTERY_PICKS_PER_PAGE, pg)) {
                if (line.godKi && !line.forms.isEmpty()) {
                    MasteryWallet.Form gk = line.forms.get(0);
                    String gkRaw = line.kiName() + " Level " + MasteryWallet.godKiLevel(gk) + "/"
                            + MasteryWallet.godKiMaxLevel(gk);
                    if (!UltraStyle.fitsButton(gkRaw)) {
                        gkRaw = line.label;
                    }
                    // One form only: straight to its page; Back there returns to this list.
                    spec.button("page:mw_form:" + line.token() + ":" + gk.name, UltraStyle.confirm(gkRaw),
                                    UltraStyle.STYLE_CONFIRM)
                            .rowGap();
                    continue;
                }
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
        spec.footerBack("page:mastery_lines", "Form lines");
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
        String bulk = bulkLine(amounts, PrestigePointsSystem::pointsForTurnIn);
        if (!bulk.isEmpty()) {
            spec.line(UltraStyle.LABEL + bulk);
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

    /** "Bulk: 3 for 4 favor, 6 for 8, or 9 for 13." from the packs that pay a bonus; empty if none. */
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
        if (parts.size() > 1) {
            parts.set(parts.size() - 1, "or " + parts.get(parts.size() - 1));
        }
        return "Bulk: " + String.join(", ", parts) + ".";
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
        if (!bulk.equals("Bulk: 3 for 4 favor, 6 for 8, 9 for 13, 12 for 18, 15 for 23, or 18 for 28.")) {
            broken.add("Prestige exchange: Bulk line reads '" + bulk + "'");
        }
    }

    /** "Exchange 18: +28 favor" - fits a standard button for two-digit packs too. */
    static String exchangeLabel(int amount, int gain) {
        return "Exchange " + amount + ": +" + gain + " favor";
    }

    /** One exchange button (left or right half of a row); dim when not enough held. */
    private static void exchangeButton(UltraScreenSpec spec, int amount, int held, boolean left) {
        int gain = PrestigePointsSystem.pointsForTurnIn(amount);
        String raw = held >= amount
                ? exchangeLabel(amount, gain)
                : "Exchange " + amount + " - need " + amount;
        if (!UltraStyle.fitsButton(raw)) {
            raw = held >= amount ? "Exchange " + amount : "Need " + amount;
        }
        String label = held >= amount ? UltraStyle.confirm(raw) : UltraStyle.dim(raw);
        String style = held >= amount ? "confirm" : "dim";
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
            String action = "page:ask:skill:" + id;
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
                .line(UltraStyle.dim("Pick a tier to see its price before you buy."))
                .labelValue("Highest owned", "T" + ph.getOrDefault("tier_highest_purchased", "0"))
                .buttonsAfterLines();
        for (int t = 1; t <= 7; t++) {
            String label = ph.getOrDefault("tier_" + t + "_label", "T" + t);
            String cost = ph.getOrDefault("tier_" + t + "_cost", "?");
            boolean owned = "true".equalsIgnoreCase(ph.get("tier_" + t + "_owned"));
            boolean canBuy = "true".equalsIgnoreCase(ph.get("tier_" + t + "_can_buy"));
            String action = "page:ask:tier:" + t;
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
                                "page:ask:unmajin",
                                UltraStyle.danger("Remove Majin"),
                                UltraStyle.STYLE_DANGER)
                        .rowGap();
            } else if (forms.majin.enabled) {
                if (majinCan) {
                    spec.button(
                                    "page:ask:majin",
                                    UltraStyle.confirm("Buy Majin"),
                                    UltraStyle.STYLE_CONFIRM)
                            .rowGap();
                } else {
                    spec.button(
                                    "page:ask:majin",
                                    UltraStyle.dim("Remove Mutant first"),
                                    UltraStyle.STYLE_DIM)
                            .rowGap();
                }
            }
            if (mutant) {
                spec.button(
                                "page:ask:unmutant",
                                UltraStyle.danger("Remove Mutant"),
                                UltraStyle.STYLE_DANGER)
                        .rowGap();
            } else if (forms.mutant.enabled) {
                if (mutantCan) {
                    spec.button(
                                    "page:ask:mutant",
                                    UltraStyle.confirm("Buy Mutant"),
                                    UltraStyle.STYLE_CONFIRM)
                            .rowGap();
                } else {
                    spec.button(
                                    "page:ask:mutant",
                                    UltraStyle.dim("Remove Majin first"),
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
                    + " favor. Permanent, stacks with Majin and Mutant.");
            if (ultimate) {
                // Permanent Ultimate can't be removed or refunded (4.7.21): a dim owned row.
                spec.button(
                                "page:effects",
                                UltraStyle.dim("Ultimate owned"),
                                UltraStyle.STYLE_DIM)
                        .rowGap();
            } else if (ultimateEarned) {
                // Already unlocked via the DMZ ultimate skill (Old Kai) — nothing to buy. Dim
                // no-op row keeps the button-row cursor on the same slot the other states occupy.
                spec.button(
                                "page:effects",
                                UltraStyle.dim("Already unlocked"),
                                UltraStyle.STYLE_DIM)
                        .rowGap();
            } else if (ultimateCan) {
                spec.button(
                                "page:ask:ultimate",
                                UltraStyle.confirm("Buy Ultimate"),
                                UltraStyle.STYLE_CONFIRM)
                        .rowGap();
            }
        }
        // God Ki moved to the Mastery wallet (4.7.21); the Effects row is gone.
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
            spec.line(UltraStyle.dim("Nothing for sale right now. Ask staff to stock the shop."));
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
            case "Potential Unlock" -> "Potential";
            case "Healing Reduction" -> "Healing Cut";
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
                .line(UltraStyle.LABEL + title);
    }

    /**
     * Confirm page footer: the reason it can't run (no Confirm then) or the Confirm button, and Back
     * to the page the spend button sat on.
     */
    private static UltraScreenSpec askFinish(UltraScreenSpec spec, String block, String doAction, String confirmLabel,
            boolean danger, String backPage, String backLabel) {
        if (block != null) {
            spec.line(UltraStyle.danger(block));
        }
        spec.buttonsAfterLines();
        if (block == null) {
            String raw = UltraStyle.fitsButton(confirmLabel) ? confirmLabel : "Confirm";
            spec.button(doAction,
                            danger ? UltraStyle.danger(raw) : UltraStyle.confirm(raw),
                            danger ? UltraStyle.STYLE_DANGER : UltraStyle.STYLE_CONFIRM)
                    .rowGap();
        }
        spec.footerBack("page:" + backPage, backLabel);
        return spec;
    }

    private static String plural(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    /** Exchange confirm: prestiges in, favor out, both balances before and after. */
    static UltraScreenSpec turnInAskSpec(int amount, int held, int favor, int gain, boolean open) {
        UltraScreenSpec spec = askSpec("turnin:" + amount, "Confirm prestige exchange")
                .labelValue("You give", plural(amount, "prestige", "prestiges"))
                .labelValue("You get", gain + " favor")
                .labelValue("Current prestige amount", held + " -> " + Math.max(0, held - amount))
                .labelValue("Prestige favor", favor + " -> " + (favor + gain))
                .line(UltraStyle.danger("Exchanged prestiges lose their stat bonus. This can't be undone."));
        String block = null;
        if (!open) {
            block = "The exchange is closed for this amount right now.";
        } else if (held < amount) {
            block = "You hold " + held + " - this pack needs " + amount + ".";
        }
        return askFinish(spec, block, "do:turnin:" + amount, exchangeLabel(amount, gain), false,
                "exchange", "Exchange shop");
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
                .labelValue("Prestige favor", favor + " -> " + Math.max(0, favor - cost))
                .line(UltraStyle.dim("Kept levels survive prestige. Favor spent is not refunded."));
        String block = null;
        if (!shop.enabled) {
            block = "The skill shop is closed right now.";
        } else if (offer == null || shop.hiddenSkills.contains(skill)) {
            block = "That skill is not sold here. Pick one from the Skill shop.";
        } else if (bought >= max) {
            block = label + " is already at its kept-level max.";
        } else if (favor < cost) {
            block = "Need " + cost + " favor (have " + favor + ").";
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
                .labelValue("Prestige favor", favor + " -> " + Math.max(0, favor - cost))
                .line(UltraStyle.dim("Unlocks the tier for good - it survives prestige. You still activate it"
                        + " with Ancient Coins on the Difficulty Tiers page."));
        String block = null;
        if (tier == null) {
            block = "That tier does not exist. Pick one from Difficulty tiers.";
        } else if (PrestigePointsSystem.hasPurchasedTier(player, tierId)) {
            block = "You already own permanent T" + tierId + ".";
        } else if (!shop.forSale(tierId)) {
            block = "Permanent T" + tierId + " is not for sale right now.";
        } else if (favor < cost) {
            block = "Need " + cost + " favor (have " + favor + ").";
        } else if (shop.requireInOrder) {
            for (int prev = 1; prev < tierId && block == null; prev++) {
                if (!PrestigePointsSystem.hasPurchasedTier(player, prev)) {
                    block = "Buy permanent T" + prev + " first - tiers are bought in order.";
                }
            }
        }
        askFinish(spec, block, "do:tier:" + tierId, "Buy for " + cost + " favor", false, "tiers", "Difficulty tiers")
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
                .labelValue("Prestige favor", favor + " -> " + Math.max(0, favor - offer.cost))
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
            block = "Need " + offer.cost + " favor (have " + favor + ").";
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
                .labelValue("Prestige favor", favor + " -> " + (favor + Math.max(0, refund)))
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
                .labelValue("Prestige favor", favor + " -> " + Math.max(0, favor - offer.cost))
                .line(UltraStyle.danger("Permanent: Ultimate can't be removed and the favor is not refunded."));
        String block = null;
        if (PrestigePointsSystem.hasUltimate(player)) {
            block = "You already own Permanent Ultimate.";
        } else if (PrestigePointsSystem.hasUltimateSkill(player)) {
            block = "You already have the Ultimate form from Old Kai - nothing to buy.";
        } else if (!offer.enabled) {
            block = "Permanent Ultimate is not for sale right now.";
        } else if (favor < offer.cost) {
            block = "Need " + offer.cost + " favor (have " + favor + ").";
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
                    .labelValue("Prestige favor", favor + " -> " + Math.max(0, favor - cost));
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
                block = "Need " + cost + " favor (have " + favor + ").";
            }
            askFinish(spec, block, "do:custom_buy:" + id, "Buy for " + cost + " favor", false, back, backLabel)
                    .send(player);
            return;
        }
        int refund = owned > 0 ? PrestigeCustomShop.removeRefund(entry, owned) : 0;
        UltraScreenSpec spec = askSpec("custom_remove:" + id, "Remove perk")
                .labelValue("You lose", entry.name + PrestigeCustomShop.levelSuffix(entry, owned))
                .labelValue("Favor back", refund > 0 ? refund + " favor" : "none")
                .labelValue("Prestige favor", favor + " -> " + (favor + refund))
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
                .labelValue("Prestige favor", favor + " -> " + Math.max(0, favor - pack))
                .line(UltraStyle.dim("Favor spent on points is not refunded."));
        String block = null;
        if (!isPack) {
            block = "That point pack is not on sale. Pick one on the Mastery wallet page.";
        } else if (!selling) {
            block = "Points are not on sale right now. Points you have can still be applied.";
        } else if (favor < pack) {
            block = "Need " + pack + " favor (have " + favor + ").";
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
                block = "You need " + points + " points (have " + balance + "). Buy points on the Mastery wallet page.";
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
