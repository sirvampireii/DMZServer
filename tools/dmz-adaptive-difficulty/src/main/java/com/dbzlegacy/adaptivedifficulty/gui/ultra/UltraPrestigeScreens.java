package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.gui.ProgressionGuiApi;
import com.dbzlegacy.adaptivedifficulty.progression.shop.GodKiEnhancementShop;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeUniverseRoleWarn;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/**
 * Prestige Ultra UI - Home / Prestige / Shop.
 *
 * <p>Prestige tab: prestige now (GUI confirm) + favor wording. Exchange shop lives under Shop.
 * Shop catalogs show Prestige favor balance only (no full wallet dump).
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
        switch (p) {
            case "turnin", "points", "prestige" -> openTurnIn(player);
            case "confirm", "prestige_confirm" -> openConfirm(player);
            case "shop", "shop_hub", "catalog" -> openShopHub(player);
            case "exchange", "turnin_shop", "favor_shop" -> openExchange(player);
            case "skills", "skill", "skills_p0" -> openSkillsShop(player);
            case "tiers", "tier", "difficulty" -> openTiers(player);
            case "effects", "effect", "forms", "form" -> openEffects(player);
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
                ph.getOrDefault("held", "0") + "/" + ph.getOrDefault("held_max", "10"));
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
            spec.line(UltraStyle.danger("Prestige system is disabled."));
        } else {
            spec.labelValue("Need", ph.getOrDefault("required_fmt", "0"));
            if ("true".equalsIgnoreCase(ph.get("ready"))) {
                spec.line(UltraStyle.confirm("Ready to prestige."));
            } else {
                spec.line(UltraStyle.dim("Need a higher level first."));
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
        spec.line(UltraStyle.LABEL
                + "Prestige favor - exchange current prestige on the Shop tab (loses the stat bonus).");
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
                        ph.getOrDefault("held", "0") + "/" + ph.getOrDefault("held_max", "10"))
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
            spec.line(UltraStyle.danger("You need a higher level before you can prestige."));
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

    /** Shop hub - favor catalogs + exchange submenu. */
    private static void openShopHub(ServerPlayer player) {
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        UltraScreenSpec spec = base("shop", "shop")
                .lineGap(2)
                .bodyPad(4);
        paintFavorBalance(spec, ph);
        spec.line(UltraStyle.LABEL + "Spend Prestige favor. Purchases survive prestige.")
                .buttonsAfterLines();
        spec.button("page:exchange", UltraStyle.confirm("Exchange shop"), UltraStyle.STYLE_CONFIRM)
                .rowGap();
        spec.button("page:skills", UltraStyle.confirm("Skill shop"), UltraStyle.STYLE_CONFIRM)
                .rowGap();
        spec.button(
                        "page:tiers",
                        UltraStyle.confirm("Difficulty tiers"),
                        UltraStyle.STYLE_CONFIRM)
                .rowGap();
        spec.button("page:effects", UltraStyle.confirm("Effects"), UltraStyle.STYLE_CONFIRM)
                .rowGap();
        spec.footerBackToMain();
        spec.send(player);
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
                ph.getOrDefault("held", "0") + "/" + ph.getOrDefault("held_max", "10"));
        spec.line(UltraStyle.danger(
                        "Exchanging prestige for Prestige favor removes the stat bonus."))
                .line(UltraStyle.LABEL + "Bulk: 3 for 4 favor, 6 for 9, or 9 for 15.")
                .buttonsAfterLines();
        int[] amounts = PrestigePointsSystem.TURN_IN_AMOUNTS;
        for (int i = 0; i < amounts.length; i += 2) {
            int a = amounts[i];
            String gainA = ph.getOrDefault("turnin_" + a + "_points", "0");
            String rawA = held >= a
                    ? "Exchange " + a + " (+" + gainA + " favor)"
                    : "Exchange " + a + " - need " + a;
            if (!UltraStyle.fitsButton(rawA)) {
                rawA = held >= a ? "Exchange " + a : "Need " + a;
            }
            String labelA = held >= a ? UltraStyle.confirm(rawA) : UltraStyle.dim(rawA);
            spec.buttonLeft("do:turnin:" + a, labelA, held >= a ? "confirm" : "dim");
            if (i + 1 < amounts.length) {
                int b = amounts[i + 1];
                String gainB = ph.getOrDefault("turnin_" + b + "_points", "0");
                String rawB = held >= b
                        ? "Exchange " + b + " (+" + gainB + " favor)"
                        : "Exchange " + b + " - need " + b;
                if (!UltraStyle.fitsButton(rawB)) {
                    rawB = held >= b ? "Exchange " + b : "Need " + b;
                }
                String labelB = held >= b ? UltraStyle.confirm(rawB) : UltraStyle.dim(rawB);
                spec.buttonRight("do:turnin:" + b, labelB, held >= b ? "confirm" : "dim");
            }
            spec.rowGap();
        }
        spec.footerBack("page:shop", "Shop");
        spec.send(player);
    }

    /** All skills on one page. */
    private static void openSkillsShop(ServerPlayer player) {
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        String[] ids = shopSkillIds(ph);
        UltraScreenSpec spec = sub("skills")
                .lineGap(2)
                .bodyPad(4);
        paintFavorBalance(spec, ph);
        spec.line(UltraStyle.LABEL + "Skill levels - 1 favor each (Potential Unlock +2)")
                .buttonsAfterLines();
        int placed = 0;
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String label = ph.getOrDefault("skill_" + id + "_label", id);
            int bought = parseInt(ph.get("skill_" + id), 0);
            int max = parseInt(ph.get("skill_" + id + "_max"), 0);
            boolean maxed = max > 0 && bought >= max;
            String caption = skillCaption(label, bought, max, maxed);
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
            spec.line(UltraStyle.dim("No skills for sale right now."));
        }
        spec.footerBack("page:shop", "Shop");
        spec.send(player);
    }

    private static void openTiers(ServerPlayer player) {
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        UltraScreenSpec spec = sub("tiers")
                .lineGap(2)
                .bodyPad(4);
        paintFavorBalance(spec, ph);
        spec.line(UltraStyle.LABEL + "Difficulty tiers - buy in order, activate with coins.")
                .labelValue("Highest owned", "T" + ph.getOrDefault("tier_highest_purchased", "0"))
                .buttonsAfterLines();
        for (int t = 1; t <= 7; t++) {
            String label = ph.getOrDefault("tier_" + t + "_label", "T" + t);
            String cost = ph.getOrDefault("tier_" + t + "_cost", "?");
            boolean owned = "true".equalsIgnoreCase(ph.get("tier_" + t + "_owned"));
            boolean canBuy = "true".equalsIgnoreCase(ph.get("tier_" + t + "_can_buy"));
            String action = "do:tier:" + t;
            String tail = owned ? "owned" : (canBuy ? cost : "lock");
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
        String formCost = ph.getOrDefault("form_cost", String.valueOf(PrestigePointsSystem.FORM_COST));
        boolean majin = "true".equalsIgnoreCase(ph.get("majin"));
        boolean mutant = "true".equalsIgnoreCase(ph.get("mutant"));
        boolean majinCan = "true".equalsIgnoreCase(ph.get("majin_can_buy"));
        boolean mutantCan = "true".equalsIgnoreCase(ph.get("mutant_can_buy"));
        String ultimateCost =
                ph.getOrDefault("ultimate_cost", String.valueOf(PrestigePointsSystem.ULTIMATE_COST));
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
        spec.line(UltraStyle.LABEL + "Majin / Mutant - " + formCost + " favor. One at a time.")
                .buttonsAfterLines();
        if (majin) {
            spec.button(
                            "do:unmajin",
                            UltraStyle.danger("Remove Majin"),
                            UltraStyle.STYLE_DANGER)
                    .rowGap();
        } else if (majinCan) {
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
        if (mutant) {
            spec.button(
                            "do:unmutant",
                            UltraStyle.danger("Remove Mutant"),
                            UltraStyle.STYLE_DANGER)
                    .rowGap();
        } else if (mutantCan) {
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
        // Ultimate is independent of Majin/Mutant: own label line, own row. No
        // buttonsAfterLines() here - the row cursor already sits below the Mutant button, and
        // re-deriving it from the line count would drop this button on top of the pair above.
        spec.line(UltraStyle.LABEL + "Ultimate - " + ultimateCost
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
        int godkiInvested = parseInt(ph.get("godki_invested"), 0);
        boolean godkiMaxed = "true".equalsIgnoreCase(ph.get("godki_maxed"));
        String godkiCost = ph.getOrDefault("godki_cost", "1");
        String godkiPerPoint = ph.getOrDefault("godki_per_point", "1,000");
        String godkiMastery = ph.getOrDefault("godki_mastery", "0");
        spec.line(UltraStyle.LABEL + "God Ki Enhancement - " + godkiCost
                        + " favor per point (+" + godkiPerPoint + " mastery).")
                .labelValue(
                        "Invested",
                        godkiInvested + "/" + GodKiEnhancementShop.MAX_INVESTMENTS)
                .labelValue("God Ki mastery", godkiMastery);
        // No buttonsAfterLines() anymore: the body estimate caps at 140px, so re-deriving the
        // God Ki row from the line count would land on top of the Ultimate button. The row
        // cursor already sits below it.
        if (godkiMaxed) {
            spec.button(
                            "do:godki",
                            UltraStyle.dim("Fully invested"),
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

    /** Skill button: short name plus level. Drops the max tag when the full caption would clip. */
    private static String skillCaption(String label, int bought, int max, boolean maxed) {
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
        String withMax = name + " " + prog + (maxed ? " max" : "");
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
