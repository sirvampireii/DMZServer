package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.gui.RivalGuiApi;
import com.dbzlegacy.adaptivedifficulty.gui.SparGuiApi;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sparring Ultra UI (Home / Dojo / Wars).
 * Home: Hall of Infamy, stats + last three fights, Weights, chat, staff.
 * Dojo: training-bond info, members (your dojo / mentor's dojo), Actions.
 * Wars: challenge, pending, top dojos, and past-season Hall of Chosen lines.
 */
public final class UltraSparScreens {
    private UltraSparScreens() {}

    /**
     * Spar card rows from {@link SparGuiApi} / SparringSystem are tab-encoded
     * ({@code uuid\tname\tIN|OUT\t...}). Splitting on {@code |} left the whole row as the
     * picker arg, so mentor Accept failed {@code matchesInviteFrom}.
     */
    static String[] splitCard(String card) {
        if (card == null || card.isBlank()) {
            return new String[0];
        }
        if (card.indexOf('\t') >= 0) {
            return card.split("\t", -1);
        }
        return card.split("\\|", -1);
    }

    public static void open(ServerPlayer player, String page) {
        if (player == null) {
            return;
        }
        String raw = page == null || page.isBlank() ? "main" : page.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("pending_decide:")) {
            openPendingDecide(player, raw.substring("pending_decide:".length()).trim());
            return;
        }
        if (lower.startsWith("dojo_war_pending_decide:")) {
            openDojoWarDecide(player, raw.substring("dojo_war_pending_decide:".length()).trim());
            return;
        }
        if (lower.startsWith("pick_confirm:")) {
            openPickConfirm(player, raw.substring("pick_confirm:".length()).trim());
            return;
        }
        if (lower.startsWith("top_")) {
            openTop(player, lower.substring(4).trim());
            return;
        }
        if (lower.startsWith("dojo_top_")) {
            openTop(player, lower.substring("dojo_top_".length()).trim());
            return;
        }
        if (lower.startsWith("weights_input:")) {
            UltraWeightShopScreens.openInput(player, raw.substring("weights_input:".length()).trim());
            return;
        }
        if (lower.startsWith("weights_confirm:")) {
            openWeightsConfirm(player, raw.substring("weights_confirm:".length()).trim());
            return;
        }
        if (lower.startsWith("fight_report:")) {
            openFightReport(player, raw.substring("fight_report:".length()).trim());
            return;
        }
        // Pickers page: "pick_release:2" reopens the third page of that picker.
        int pickPage = UltraPickHelper.pageIndex(lower);
        lower = UltraPickHelper.pageBase(lower);
        switch (lower) {
            case "stats" -> openStats(player);
            case "top", "leaderboard", "infamy", "hof", "hall_of_infamy" -> openTop(player, "tp");
            case "dojo_rank", "dojo_rankings" -> openTop(player, "rp");
            case "dojo", "roster" -> openDojo(player, "home");
            case "dojo_members", "dojo_members_own" -> openDojo(player, "members_own");
            case "dojo_members_joined" -> openDojo(player, "members_joined");
            case "dojo_hof", "chosen", "hall_of_chosen", "dojo_war_hof" -> openHallOfChosen(player);
            case "mentor", "actions" -> openMentor(player);
            case "pending", "invites" -> openPending(player, pickPage);
            case "settings" -> openSettings(player);
            case "admin" -> openAdmin(player);
            case "wars", "dojo_war" -> openWars(player);
            case "dojo_war_pending" -> openDojoWarPending(player, pickPage);
            case "pick_apprentice" -> openOnlinePick(
                    player, "Invite apprentice", "mentor_invite", "pick_apprentice", "mentor", pickPage);
            case "pick_mentor" -> openOnlinePick(
                    player, "Ask as apprentice", "apprentice_invite", "pick_mentor", "mentor", pickPage);
            case "pick_release" -> openReleasePick(player, pickPage);
            case "pick_dojo_challenge" -> openDojoChallengePick(player, pickPage);
            case "weights", "weight", "weight_shop" -> UltraWeightShopScreens.openShop(player);
            default -> openHome(player);
        }
    }

    private static void openWeightsConfirm(ServerPlayer player, String rest) {
        if (rest == null || rest.isBlank()) {
            UltraWeightShopScreens.openShop(player);
            return;
        }
        int colon = rest.lastIndexOf(':');
        if (colon <= 0 || colon >= rest.length() - 1) {
            UltraWeightShopScreens.openShop(player);
            return;
        }
        String typeId = rest.substring(0, colon).trim();
        Integer value = UltraWeightShopScreens.parseWeightValue(rest.substring(colon + 1));
        if (value == null) {
            UltraWeightShopScreens.openShop(player);
            return;
        }
        UltraWeightShopScreens.openConfirm(player, typeId, value);
    }

    private static UltraScreenSpec base(String page, String tab) {
        return UltraScreenSpec.of("spar")
                .title("Sparring")
                .page(page)
                .activeTab(tab)
                .tabs("home|Home", "dojo|Dojo", "wars|Wars");
    }

    /** Subpage: no tab row — dual footer (Back + Main menu). */
    private static UltraScreenSpec sub(String page) {
        return UltraScreenSpec.of("spar")
                .title("Sparring")
                .page(page);
    }


    private static String tabFor(String page) {
        if (page == null) {
            return "home";
        }
        String p = page.toLowerCase(Locale.ROOT);
        if (p.startsWith("dojo_war") || "pick_dojo_challenge".equals(p) || p.contains("dojo_challenge")) {
            return "wars";
        }
        if (p.startsWith("dojo_top")
                || "dojo_rank".equals(p)
                || "dojo_rankings".equals(p)
                || p.startsWith("top")
                || "infamy".equals(p)
                || "hof".equals(p)
                || "stats".equals(p)
                || p.startsWith("fight_report")
                || "settings".equals(p)
                || "admin".equals(p)
                || "mentor".equals(p)
                || "actions".equals(p)
                || "pending".equals(p)
                || "invites".equals(p)
                || p.startsWith("pick_apprentice")
                || p.startsWith("pick_mentor")
                || p.startsWith("pick_release")
                || p.startsWith("weights")) {
            return "home";
        }
        if (p.startsWith("dojo") || "roster".equals(p) || "chosen".equals(p) || p.startsWith("pick_confirm")) {
            return "dojo";
        }
        return "home";
    }

    /**
     * Home hub: Hall of Infamy, stats (last three fights), Weights, chat, staff.
     * Mentor bonds live under Dojo → Actions. Session/partner text is staff-only.
     */
    private static void openHome(ServerPlayer player) {
        UltraScreenSpec spec = base("main", "home")
                .line(UltraStyle.LABEL + "Hall of Infamy, your stats, and weights.")
                .buttonsAfterLines();
        UltraPickHelper.placePageButtons(
                spec,
                "infamy|Hall of Infamy",
                "stats|Your stats",
                "weights|Weights",
                "settings|TP Chat");
        if (StaffAccess.isStaff(player)) {
            spec.buttonLeft("page:admin", UltraStyle.danger("Staff Admin"), "danger").rowGap();
        }
        spec.footerBackToMain();
        spec.send(player);
    }

    private static void openStats(ServerPlayer player) {
        UltraScreenSpec spec = sub("stats").line(UltraStyle.LABEL + "Your spar stats");
        for (String line : SparGuiApi.statsOverviewLines(player)) {
            if (line != null && !line.isBlank()) {
                spec.line(line);
            }
        }
        List<String> fights = SparGuiApi.recentFightCards(player);
        if (fights == null || fights.isEmpty()) {
            spec.line(UltraStyle.dim("No finished spars yet. Fight someone, then check back."));
        } else {
            spec.line(UltraStyle.LABEL + "Last three fights:");
        }
        spec.buttonsAfterLines();
        if (fights != null && !fights.isEmpty()) {
            for (String card : fights) {
                if (card == null || card.isBlank()) {
                    continue;
                }
                String[] p = card.split("\t", 2);
                String idx = p[0] == null ? "" : p[0].trim();
                String label = p.length > 1 && p[1] != null && !p[1].isBlank() ? p[1].trim() : ("Fight " + idx);
                int paren = label.indexOf(" (");
                if (paren > 0) {
                    label = label.substring(0, paren);
                }
                label = UltraStyle.buttonText(label);
                if (idx.isBlank()) {
                    continue;
                }
                spec.button(
                                "page:fight_report:" + idx,
                                UltraStyle.confirm(label),
                                "confirm")
                        .rowGap();
            }
        }
        spec.footerBackHome("Spar Home");
        spec.send(player);
    }

    private static void openFightReport(ServerPlayer player, String indexRaw) {
        int index = 1;
        try {
            index = Integer.parseInt(indexRaw == null ? "1" : indexRaw.trim());
        } catch (NumberFormatException ignored) {
            index = 1;
        }
        UltraScreenSpec spec = sub("fight_report:" + index)
                .line(UltraStyle.LABEL + "Fight report");
        for (String line : SparGuiApi.recentReportLines(player, index)) {
            if (line != null && !line.isBlank()) {
                spec.line(line);
            }
        }
        spec.buttonsAfterLines();
        spec.footerBack("page:stats", "Your stats");
        spec.send(player);
    }

    private static void openTop(ServerPlayer player, String category) {
        String cat = category == null || category.isBlank() ? "tp" : category;
        if ("wins".equalsIgnoreCase(cat)
                || "win".equalsIgnoreCase(cat)
                || "streak".equalsIgnoreCase(cat)) {
            cat = "tp";
        }
        if ("reputation".equalsIgnoreCase(cat)) {
            cat = "rp";
        } else if ("war".equalsIgnoreCase(cat)) {
            cat = "wars";
        }
        boolean dojoBoard = "rp".equalsIgnoreCase(cat) || "wars".equalsIgnoreCase(cat);
        List<String> lines = dojoBoard
                ? SparGuiApi.dojoTopLines(player, cat)
                : SparGuiApi.topLines(player, cat);
        String heading = dojoBoard
                ? "Top dojos - " + boardLabel(cat)
                : "Hall of Infamy - " + boardLabel(cat);
        UltraScreenSpec spec = sub(dojoBoard ? "dojo_top_" + cat : "top_" + cat)
                .line(UltraStyle.LABEL + heading);
        if (lines != null) {
            for (String line : lines) {
                if (line != null && !line.isBlank()) {
                    spec.line(line);
                }
            }
        }
        spec.buttonsAfterLines();
        if (dojoBoard) {
            UltraPickHelper.placePageButtons(
                    spec, "dojo_top_rp|Reputation", "dojo_top_wars|War wins");
        } else {
            UltraPickHelper.placePageButtons(
                    spec,
                    "top_tp|Training points",
                    "top_sessions|Sessions",
                    "top_perfect|Perfect spars",
                    "top_combo|Combo",
                    "top_time|Time");
        }
        String backPage = dojoBoard ? "dojo_war" : "main";
        String backLabel = dojoBoard ? "Wars" : "Spar Home";
        spec.footerBack("page:" + backPage, backLabel);
        spec.send(player);
    }

    private static String boardLabel(String cat) {
        return switch (cat == null ? "" : cat.toLowerCase(Locale.ROOT)) {
            case "sessions" -> "Sessions";
            case "perfect" -> "Perfect spars";
            case "combo" -> "Combo";
            case "time" -> "Time";
            case "rp" -> "Reputation";
            case "wars" -> "War wins";
            default -> "Training points";
        };
    }

    private static void openDojo(ServerPlayer player, String sub) {
        String key = sub == null || sub.isBlank() ? "home" : sub;
        if ("hof".equals(key)) {
            openHallOfChosen(player);
            return;
        }
        // Members is a sub-page with its own your-dojo / mentor's-dojo toggle.
        if ("members".equals(key) || "members_own".equals(key) || "members_joined".equals(key)) {
            if ("members".equals(key)) {
                key = SparGuiApi.ownsDojo(player) ? "members_own" : "members_joined";
            }
            openDojoMembers(player, key);
            return;
        }
        Map<String, String> ph = SparGuiApi.placeholders(player);
        UltraScreenSpec spec = base("dojo", "dojo");
        if (!"true".equalsIgnoreCase(ph.get("system_enabled"))) {
            spec.line(UltraStyle.danger("Sparring is turned off. Ask staff to enable it."))
                    .buttonsAfterLines();
            spec.footerBackToMain();
            spec.send(player);
            return;
        }
        for (String line : SparGuiApi.mentorLines(player)) {
            if (line != null && !line.isBlank()) {
                spec.line(line);
            }
        }
        spec.buttonsAfterLines();
        UltraPickHelper.placePageButtons(
                spec,
                "dojo_members|Dojo members",
                "mentor|Actions");
        spec.footerBackToMain();
        spec.send(player);
    }

    /**
     * Dojo members sub-page. The toggle is always available, including when the player
     * owns neither dojo. Recruit and request stay on the Dojo tab.
     */
    private static void openDojoMembers(ServerPlayer player, String key) {
        boolean own = "members_own".equals(key);
        String page = own ? "dojo_members_own" : "dojo_members_joined";
        Map<String, String> ph = SparGuiApi.placeholders(player);
        UltraScreenSpec spec = sub(page);
        if (!"true".equalsIgnoreCase(ph.get("system_enabled"))) {
            spec.line(UltraStyle.danger("Sparring is turned off. Ask staff to enable it."))
                    .buttonsAfterLines();
            spec.footerBack("page:dojo", "Dojo");
            spec.send(player);
            return;
        }
        List<String> lines = own
                ? SparGuiApi.ownedDojoMemberLines(player)
                : SparGuiApi.joinedDojoMemberLines(player);
        if (lines != null) {
            for (String line : lines) {
                if (line != null && !line.isBlank()) {
                    spec.line(line);
                }
            }
        }
        spec.buttonsAfterLines();
        spec.buttonLeft(
                        "page:dojo_members_own",
                        own ? UltraStyle.confirm("Your dojo") : UltraStyle.dim("Your dojo"),
                        own ? "confirm" : "dim")
                .buttonRight(
                        "page:dojo_members_joined",
                        own ? UltraStyle.dim("Your mentor's dojo") : UltraStyle.confirm("Your mentor's dojo"),
                        own ? "dim" : "confirm")
                .rowGap();
        spec.footerBack("page:dojo", "Dojo");
        spec.send(player);
    }

    private static void openMentor(ServerPlayer player) {
        UltraScreenSpec spec = sub("mentor")
                .line(UltraStyle.LABEL + "Actions - recruit, request, or manage training bonds");
        for (String line : SparGuiApi.mentorLines(player)) {
            if (line != null && !line.isBlank()) {
                spec.line(line);
            }
        }
        spec.buttonsAfterLines();
        UltraPickHelper.placePageButtons(
                spec,
                "pick_apprentice|Recruit",
                "pick_mentor|Request master",
                "pending|Bond invites",
                "pick_release|Release");
        spec.buttonLeft(
                        "page:pick_confirm:mentor|mentor|leave",
                        UltraStyle.danger("Leave mentor"),
                        "danger")
                .rowGap();
        spec.footerBack("page:dojo", "Dojo");
        spec.send(player);
    }

    /** Cards per picker page — the old hard cap, so each page keeps its layout. */
    private static final int CARDS_PER_PAGE = 8;
    /** Online-player picker page size (the old cap of 10). */
    private static final int NAMES_PER_PAGE = 10;

    /** Card rows with a usable first field, in source order — what a picker can act on. */
    private static List<String> actionableCards(List<String> cards) {
        List<String> out = new java.util.ArrayList<>();
        if (cards == null) {
            return out;
        }
        for (String card : cards) {
            if (card == null || card.isBlank()) {
                continue;
            }
            String[] parts = splitCard(card);
            if (parts.length > 0 && !parts[0].trim().isBlank()) {
                out.add(card);
            }
        }
        return out;
    }

    /**
     * Decide pages carry the list page they came from as a trailing {@code |N} (page 2+ only),
     * so Back returns to that page. Names and {@code uuid:} args never contain {@code |}.
     */
    private static String withReturnPage(String arg, int page) {
        return page > 0 ? arg + "|" + page : arg;
    }

    /** {@code [arg, page]} from {@link #withReturnPage}; no suffix means page 0. */
    private static String[] splitReturnPage(String arg) {
        String a = arg == null ? "" : arg;
        int bar = a.lastIndexOf('|');
        if (bar > 0 && UltraPickHelper.pageIndex("p:" + a.substring(bar + 1)) > 0) {
            return new String[] {a.substring(0, bar), a.substring(bar + 1)};
        }
        return new String[] {a, "0"};
    }

    private static void openPending(ServerPlayer player, int page) {
        UltraScreenSpec spec = sub(UltraPickHelper.pageArg("pending", page))
                .line(UltraStyle.LABEL + "Mentor / apprentice requests only");
        for (String line : SparGuiApi.pendingMentorLines(player)) {
            if (line != null && !line.isBlank()) {
                spec.line(line);
            }
        }
        List<String> cards = actionableCards(SparGuiApi.pendingMentorInviteCards(player));
        int pages = UltraPickHelper.pageCount(cards.size(), CARDS_PER_PAGE);
        int pg = UltraPickHelper.clampPage(page, pages);
        UltraPickHelper.pageLabel(spec, pg, pages);
        spec.buttonsAfterLines();
        int placed = 0;
        for (String card : UltraPickHelper.pageSlice(cards, CARDS_PER_PAGE, pg)) {
            String[] parts = splitCard(card);
            String arg = parts[0].trim();
            String label = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : arg;
            String dir = parts.length > 2 ? parts[2].trim() : "IN";
            String picker = arg.contains(":") ? arg : "uuid:" + arg;
            String packed = ("OUT".equalsIgnoreCase(dir) ? "OUT|" : "IN|") + picker;
            String action = "page:pending_decide:" + withReturnPage(packed, pg);
            String shown = label.isBlank()
                    ? RivalGuiApi.displayPickerArg(player, picker)
                    : label;
            if (shown == null || shown.isBlank()) {
                shown = "-";
            }
            if (placed % 2 == 0) {
                spec.buttonLeft(action, UltraStyle.confirm(shown), "confirm");
            } else {
                spec.buttonRight(action, UltraStyle.confirm(shown), "confirm");
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        if (placed == 0) {
            spec.line(UltraStyle.dim(
                    "No bond invites waiting. Send one from Actions, or wait for someone to invite you."));
        }
        UltraPickHelper.placePrevNext(spec, "pending", pg, pages);
        spec.footerBack("page:mentor", "Actions");
        spec.send(player);
    }

    private static void openPendingDecide(ServerPlayer player, String pickerArg) {
        String[] withPage = splitReturnPage(pickerArg);
        int returnPage = Integer.parseInt(withPage[1]);
        String dir = "IN";
        String picker = withPage[0];
        if (picker.regionMatches(true, 0, "OUT|", 0, 4) || picker.regionMatches(true, 0, "IN|", 0, 3)) {
            int bar = picker.indexOf('|');
            dir = picker.substring(0, bar);
            picker = picker.substring(bar + 1);
        }
        boolean outgoing = "OUT".equalsIgnoreCase(dir);
        String who = RivalGuiApi.displayPickerArg(player, picker);
        UltraScreenSpec spec = sub("pending_decide:"
                + withReturnPage((outgoing ? "OUT|" : "IN|") + picker, returnPage))
                .line(UltraStyle.LABEL + "Training bond invite")
                .labelValue("Player", who == null || who.isBlank() ? "-" : who);
        if (outgoing) {
            spec.line(UltraStyle.LABEL + "You invited this player.");
        } else {
            spec.line(UltraStyle.LABEL + "Accept to form this training bond.")
                    .line(UltraStyle.LABEL + "Decline to dismiss the invite.");
        }
        spec.buttonsAfterLines();
        if (outgoing) {
            spec.button("do:mentor:cancel:" + picker, UltraStyle.danger("Withdraw invite"), "danger")
                    .rowGap();
        } else {
            // Stack Accept above Decline - side-by-side is a misclick invite-kill risk.
            spec.button("do:mentor_accept:" + picker, UltraStyle.confirm("Accept"), "confirm")
                    .rowGap();
            spec.button("do:mentor_decline:" + picker, UltraStyle.danger("Decline"), "danger")
                    .rowGap();
        }
        spec.footerBack("page:" + UltraPickHelper.pageArg("pending", returnPage), "Invites");
        spec.send(player);
    }

    private static void openPickConfirm(ServerPlayer player, String payload) {
        String[] parts = payload.split("\\|", 3);
        if (parts.length < 3) {
            openMentor(player);
            return;
        }
        String action = parts[0];
        String returnPage = parts[1];
        String targetArg = parts[2];
        String[] copy = confirmCopy(action);
        String who = "mentor".equals(action) && "leave".equalsIgnoreCase(targetArg)
                ? "Your master"
                : RivalGuiApi.displayPickerArg(player, targetArg);
        UltraScreenSpec spec = sub("pick_confirm:" + payload)
                .line(UltraStyle.LABEL + copy[0])
                .labelValue("Target", who == null || who.isBlank() ? "-" : who)
                .line(UltraStyle.LABEL + copy[1])
                .buttonsAfterLines();
        boolean destructive = isDestructiveConfirm(action);
        spec.button(
                        "do:" + action + ":" + targetArg,
                        destructive ? UltraStyle.danger("Confirm") : UltraStyle.confirm("Confirm"),
                        destructive ? UltraStyle.STYLE_DANGER : UltraStyle.STYLE_CONFIRM)
                .rowGap();
        // Pickers pass their own page (with its index) as the return page, so Back lands on
        // the same picker page the player chose from.
        String backLabel =
                switch (UltraPickHelper.pageBase(returnPage == null ? "" : returnPage)) {
                    case "pick_apprentice", "pick_mentor" -> "Players";
                    case "pick_release" -> "Apprentices";
                    case "pick_dojo_challenge" -> "Rival dojos";
                    case "mentor", "pending" -> "Actions";
                    case "dojo_war", "dojo_war_pending", "wars" -> "Wars";
                    default -> "Spar Home";
                };
        spec.footerBack("page:" + returnPage, backLabel);
        spec.send(player);
    }

    /** Leave / release / decline are irreversible or dismissive - wire danger, not confirm. */
    private static boolean isDestructiveConfirm(String action) {
        return switch (action == null ? "" : action) {
            case "mentor", "mentor_release", "mentor_decline" -> true;
            default -> false;
        };
    }

    private static String[] confirmCopy(String action) {
        return switch (action == null ? "" : action) {
            case "mentor_invite" -> new String[] {"Invite apprentice", "Invite this player to train under you."};
            case "apprentice_invite" -> new String[] {"Ask as apprentice", "Ask this player to be your master."};
            case "mentor_release" -> new String[] {
                "Release apprentice", "Release this apprentice. A 12-hour cooldown starts."
            };
            case "mentor" -> new String[] {"Leave mentor", "Leave your master. A 12-hour cooldown starts."};
            case "mentor_accept" -> new String[] {"Accept mentor invite", "Accept to form this training bond."};
            case "mentor_decline" -> new String[] {"Decline mentor invite", "Decline to dismiss the invite."};
            case "dojo_challenge" -> new String[] {"Challenge rival dojo", "Challenge this dojo to a war."};
            default -> new String[] {"Confirm action", "Confirm to continue."};
        };
    }

    private static void openSettings(ServerPlayer player) {
        Map<String, String> ph = SparGuiApi.placeholders(player);
        boolean tpOn = "true".equalsIgnoreCase(ph.get("tpMsg"));
        boolean mentorTpOn = "true".equalsIgnoreCase(ph.get("mentorTpMsg"));
        UltraScreenSpec spec = sub("settings")
                .line(UltraStyle.LABEL + "Training point messages")
                .buttonsAfterLines();
        spec.buttonLeft(
                        "do:tpmsg:toggle",
                        tpOn ? UltraStyle.confirm("TP Chat - ON") : UltraStyle.dim("TP Chat - OFF"),
                        tpOn ? "confirm" : "dim")
                .buttonRight(
                        "do:mentor_tpmsg:toggle",
                        mentorTpOn
                                ? UltraStyle.confirm("Mentor TP - ON")
                                : UltraStyle.dim("Mentor TP - OFF"),
                        mentorTpOn ? "confirm" : "dim")
                .rowGap();
        spec.footerBackHome("Spar Home");
        spec.send(player);
    }

    private static void openWars(ServerPlayer player) {
        UltraScreenSpec spec = base("dojo_war", "wars");
        List<String> war = SparGuiApi.dojoWarLines(player);
        appendBody(spec, war);
        int warPending = SparGuiApi.pendingDojoWarCards(player).size();
        spec.buttonsAfterLines();
        UltraPickHelper.placePageButtons(
                spec,
                "pick_dojo_challenge|Challenge dojo",
                "dojo_top_rp|Top dojos",
                "dojo_hof|Hall of Chosen");
        String pendingLabel = warPending > 0 ? "Requests (" + warPending + ")" : "Requests";
        spec.buttonLeft("page:dojo_war_pending", UltraStyle.confirm(pendingLabel), "confirm").rowGap();
        spec.footerBackToMain();
        spec.send(player);
    }

    /** Past seasons. Same kind of page as Top dojos — not body copy on Wars. */
    private static void openHallOfChosen(ServerPlayer player) {
        UltraScreenSpec spec = sub("dojo_hof");
        List<String> lines = SparGuiApi.dojoHallOfFameLines(player);
        if (lines == null || lines.isEmpty()) {
            spec.line(UltraStyle.LABEL + "Hall of Chosen");
            spec.line(UltraStyle.dim("No past seasons yet."));
        } else {
            appendBody(spec, lines);
        }
        spec.buttonsAfterLines();
        spec.footerBack("page:dojo_war", "Wars");
        spec.send(player);
    }

    private static void appendBody(UltraScreenSpec spec, List<String> lines) {
        if (lines == null) {
            return;
        }
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                spec.line(line);
            }
        }
    }

    private static void openDojoWarPending(ServerPlayer player, int page) {
        UltraScreenSpec spec = sub(UltraPickHelper.pageArg("dojo_war_pending", page))
                .line(UltraStyle.LABEL + "Pending war requests")
                .line(UltraStyle.LABEL + "Accept or deny even if the other master is offline.");
        List<String> all = SparGuiApi.pendingDojoWarCards(player);
        // One slice feeds both the request lines and their buttons, so every request shown
        // on a page has its button on that same page.
        List<String> cards = actionableCards(all);
        int pages = UltraPickHelper.pageCount(cards.size(), CARDS_PER_PAGE);
        int pg = UltraPickHelper.clampPage(page, pages);
        List<String> shown = UltraPickHelper.pageSlice(cards, CARDS_PER_PAGE, pg);
        if (all == null || all.isEmpty()) {
            spec.line(UltraStyle.dim(
                    "No war requests waiting. Challenge another dojo when your roster is ready."));
        } else {
            for (String card : shown) {
                String[] parts = splitCard(card);
                String label = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : parts[0];
                String dir = parts.length > 2 ? parts[2].trim() : "IN";
                boolean online = parts.length > 4 && "1".equals(parts[4]);
                if (label != null && !label.isBlank()) {
                    String side = "OUT".equalsIgnoreCase(dir) ? "Outgoing" : "Incoming";
                    spec.line(UltraStyle.LABEL + side + " " + label
                            + (online ? " (online)" : " (offline)"));
                }
            }
        }
        UltraPickHelper.pageLabel(spec, pg, pages);
        spec.buttonsAfterLines();
        int placed = 0;
        for (String card : shown) {
            String[] parts = splitCard(card);
            String arg = parts[0].trim();
            String label = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : arg;
            String dir = parts.length > 2 ? parts[2].trim() : "IN";
            String packed = ("OUT".equalsIgnoreCase(dir) ? "OUT|" : "IN|") + arg;
            String action = "page:dojo_war_pending_decide:" + withReturnPage(packed, pg);
            if (placed % 2 == 0) {
                spec.buttonLeft(action, UltraStyle.confirm(label), "confirm");
            } else {
                spec.buttonRight(action, UltraStyle.confirm(label), "confirm");
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        UltraPickHelper.placePrevNext(spec, "dojo_war_pending", pg, pages);
        spec.footerBack("page:dojo_war", "Wars");
        spec.send(player);
    }

    private static void openDojoWarDecide(ServerPlayer player, String pickerArg) {
        String[] withPage = splitReturnPage(pickerArg);
        int returnPage = Integer.parseInt(withPage[1]);
        String dir = "IN";
        String picker = withPage[0];
        if (picker.regionMatches(true, 0, "OUT|", 0, 4) || picker.regionMatches(true, 0, "IN|", 0, 3)) {
            int bar = picker.indexOf('|');
            dir = picker.substring(0, bar);
            picker = picker.substring(bar + 1);
        }
        boolean outgoing = "OUT".equalsIgnoreCase(dir);
        String who = RivalGuiApi.displayPickerArg(player, picker);
        if (who == null || who.isBlank()) {
            who = picker;
        }
        UltraScreenSpec spec = sub("dojo_war_pending_decide:"
                        + withReturnPage((outgoing ? "OUT|" : "IN|") + picker, returnPage))
                .line(UltraStyle.LABEL + "Dojo war request")
                .labelValue("Dojo", who == null || who.isBlank() ? "-" : who);
        if (outgoing) {
            spec.line(UltraStyle.LABEL + "You challenged this dojo. Withdraw to cancel.");
        } else {
            spec.line(UltraStyle.LABEL + "Accept to start the war (2x RP vs that dojo).")
                    .line(UltraStyle.LABEL + "Deny to refuse this challenge.");
        }
        spec.buttonsAfterLines();
        if (outgoing) {
            spec.button("do:dojo_war_cancel:" + picker, UltraStyle.danger("Cancel challenge"), "danger")
                    .rowGap();
        } else {
            // Stack Accept above Deny - side-by-side is a misclick risk.
            spec.button("do:dojo_accept:" + picker, UltraStyle.confirm("Accept"), "confirm").rowGap();
            spec.button("do:dojo_decline:" + picker, UltraStyle.danger("Deny"), "danger").rowGap();
        }
        spec.footerBack("page:" + UltraPickHelper.pageArg("dojo_war_pending", returnPage), "Requests");
        spec.send(player);
    }

    private static void openOnlinePick(
            ServerPlayer player, String heading, String action, String page, String back, int pageIndex) {
        List<String> names = new java.util.ArrayList<>();
        List<String> online = RivalGuiApi.onlinePlayerNames(player);
        if (online != null) {
            for (String name : online) {
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
        }
        int pages = UltraPickHelper.pageCount(names.size(), NAMES_PER_PAGE);
        int pg = UltraPickHelper.clampPage(pageIndex, pages);
        String here = UltraPickHelper.pageArg(page, pg);
        UltraScreenSpec spec = sub(here)
                .line(UltraStyle.LABEL + heading)
                .line(UltraStyle.LABEL + "Choose an online player.");
        UltraPickHelper.pageLabel(spec, pg, pages);
        spec.buttonsAfterLines();
        int placed = 0;
        for (String name : UltraPickHelper.pageSlice(names, NAMES_PER_PAGE, pg)) {
            String nav = "page:pick_confirm:" + action + "|" + here + "|" + name;
            if (placed % 2 == 0) {
                spec.buttonLeft(nav, UltraStyle.confirm(name), "confirm");
            } else {
                spec.buttonRight(nav, UltraStyle.confirm(name), "confirm");
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        if (placed == 0) {
            spec.line(UltraStyle.dim("Nobody else is online right now - try again when other players are on."));
        }
        UltraPickHelper.placePrevNext(spec, page, pg, pages);
        spec.footerBack("page:" + back, "Actions");
        spec.send(player);
    }

    private static void openReleasePick(ServerPlayer player, int page) {
        List<String> cards = actionableCards(SparGuiApi.apprenticeCards(player));
        int pages = UltraPickHelper.pageCount(cards.size(), CARDS_PER_PAGE);
        int pg = UltraPickHelper.clampPage(page, pages);
        String back = UltraPickHelper.pageArg("pick_release", pg);
        UltraScreenSpec spec = sub(back)
                .line(UltraStyle.LABEL + "Release apprentice")
                .line(UltraStyle.LABEL + "Choose an apprentice.");
        UltraPickHelper.pageLabel(spec, pg, pages);
        spec.buttonsAfterLines();
        int placed = 0;
        for (String card : UltraPickHelper.pageSlice(cards, CARDS_PER_PAGE, pg)) {
            String[] parts = splitCard(card);
            String arg = parts[0].trim();
            String label = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : arg;
            String nav = "page:pick_confirm:mentor_release|" + back + "|" + arg;
            if (placed % 2 == 0) {
                spec.buttonLeft(nav, UltraStyle.confirm(label), "confirm");
            } else {
                spec.buttonRight(nav, UltraStyle.confirm(label), "confirm");
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        if (placed == 0) {
            spec.line(UltraStyle.dim("No apprentices to release. Take an apprentice from Actions first."));
        }
        UltraPickHelper.placePrevNext(spec, "pick_release", pg, pages);
        spec.footerBack("page:mentor", "Actions");
        spec.send(player);
    }

    private static void openDojoChallengePick(ServerPlayer player, int page) {
        List<String> cards = actionableCards(SparGuiApi.rivalDojoCards(player));
        int pages = UltraPickHelper.pageCount(cards.size(), CARDS_PER_PAGE);
        int pg = UltraPickHelper.clampPage(page, pages);
        String back = UltraPickHelper.pageArg("pick_dojo_challenge", pg);
        UltraScreenSpec spec = sub(back)
                .line(UltraStyle.LABEL + "Challenge another dojo")
                .line(UltraStyle.LABEL + "Pick a rival dojo. The master does not need to be online.");
        UltraPickHelper.pageLabel(spec, pg, pages);
        spec.buttonsAfterLines();
        int placed = 0;
        for (String card : UltraPickHelper.pageSlice(cards, CARDS_PER_PAGE, pg)) {
            String[] parts = splitCard(card);
            String arg = parts[0].trim();
            String label = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : arg;
            // Prefer uuid: so challenge works while the defending master is offline.
            String target = arg.contains(":") ? arg : "uuid:" + arg;
            String nav = "page:pick_confirm:dojo_challenge|" + back + "|" + target;
            if (placed % 2 == 0) {
                spec.buttonLeft(nav, UltraStyle.confirm(label), "confirm");
            } else {
                spec.buttonRight(nav, UltraStyle.confirm(label), "confirm");
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        if (placed == 0) {
            spec.line(UltraStyle.dim("No rival dojos on the ladder yet."));
        }
        UltraPickHelper.placePrevNext(spec, "pick_dojo_challenge", pg, pages);
        spec.footerBack("page:dojo_war", "Wars");
        spec.send(player);
    }

    private static void openAdmin(ServerPlayer player) {
        if (!StaffAccess.isStaff(player)) {
            openHome(player);
            return;
        }
        Map<String, String> ph = SparGuiApi.placeholders(player);
        boolean active = "true".equalsIgnoreCase(ph.get("session_active"));
        String partner = ph.getOrDefault("partner", "");
        UltraScreenSpec spec = sub("admin")
                .line(UltraStyle.LABEL + "Staff spar tools")
                .labelValue("Session", active ? "in progress" : "not running")
                .labelValue("Partner", partner == null || partner.isBlank() ? "none" : partner)
                .buttonsAfterLines();
        spec.buttonLeft("do:admin:save", UltraStyle.confirm("Save data"), "confirm")
                .buttonRight("do:admin:status", UltraStyle.danger("Show status"), "danger")
                .rowGap();
        spec.footerBackHome("Spar Home");
        spec.send(player);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
