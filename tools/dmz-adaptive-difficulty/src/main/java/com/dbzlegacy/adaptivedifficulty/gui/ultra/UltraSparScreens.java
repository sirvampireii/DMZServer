package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.gui.RivalGuiApi;
import com.dbzlegacy.adaptivedifficulty.gui.SparGuiApi;
import com.dbzlegacy.adaptivedifficulty.sparring.SparBoard;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sparring Ultra UI (Home / Leaderboard) and the training-bond pages drawn on the Dojo screen.
 * Home: what a spar is worth, Weights, chat, staff. Leaderboard: Hall of Infamy, stats + last three fights.
 * Dojo side: roster, members (your dojo / mentor's dojo), Training bonds. Wars, the Spar Board and the rest
 * of the Dojo tab live in {@link UltraDojoScreens}.
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
        // 4.7.68: the Spar Board tab, results, and the Dojo tab (upgrades, style, elites, wars) live in
        // UltraDojoScreens. The 4.7.64 dojo-challenge pages are gone: wars are declared from the Dojo tab.
        if (lower.startsWith("dojo_war_pending") || lower.startsWith("pick_dojo_challenge")) {
            UltraDojoScreens.open(player, "dojo_wars", null);
            return;
        }
        if ("dojo_roster".equals(lower)) {
            openDojo(player, "home");
            return;
        }
        if (UltraDojoScreens.open(player, raw, null)) {
            return;
        }
        if (lower.startsWith("pending_decide:")) {
            openPendingDecide(player, raw.substring("pending_decide:".length()).trim());
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
            case "leaderboard" -> openLeaderboard(player);
            case "top", "infamy", "hof", "hall_of_infamy" -> openTop(player, "tp");
            case "dojo_rank", "dojo_rankings" -> openTop(player, "rp");
            case "dojo", "roster" -> openDojo(player, "home");
            case "dojo_members", "dojo_members_own" -> openDojo(player, "members_own");
            case "dojo_members_joined" -> openDojo(player, "members_joined");
            case "dojo_previous_members", "dojo_previous_members_own" -> openDojo(player, "previous_own");
            case "dojo_previous_members_joined" -> openDojo(player, "previous_joined");
            case "mentor", "actions" -> openMentor(player);
            case "pending", "invites" -> openPending(player, pickPage);
            case "settings" -> openSettings(player);
            case "admin" -> openAdmin(player);
            case "pick_apprentice" -> openOnlinePick(
                    player, "Invite apprentice", "mentor_invite", "pick_apprentice", "mentor", pickPage);
            case "pick_mentor" -> openOnlinePick(
                    player, "Ask as apprentice", "apprentice_invite", "pick_mentor", "mentor", pickPage);
            case "pick_release" -> openReleasePick(player, pickPage);
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
                .fitMenu()
                .title("Sparring")
                .page(page)
                .activeTab(tab)
                .tabs("home|Home", "leaderboard|Leaderboard", "sparboard|Spar Board");
    }

    /** 4.7.78: the dojo-side pages kept in this class (roster, members, training bonds) draw on the Dojo screen. */
    private static UltraScreenSpec dsub(String page) {
        return UltraScreenSpec.of("dojo").fitMenu().title("Dojo").page(page);
    }

    /** Subpage: no tab row — dual footer (Back + Main menu). */
    private static UltraScreenSpec sub(String page) {
        return UltraScreenSpec.of("spar")
                .fitMenu()
                .title("Sparring")
                .page(page);
    }


    /**
     * Home hub: Hall of Infamy, stats (last three fights), Weights, chat, staff.
     * Mentor bonds live under Dojo > Training bonds. Session/partner text is staff-only.
     */
    private static void openHome(ServerPlayer player) {
        openHome(player, null);
    }

    /**
     * Sparring Home (4.7.64): the spar board, then what a spar is worth to this player, then the Sparring pages.
     * The board lists who is looking (closest match first) with the TP a spar with each would pay this player
     * per hit at the start; one tap asks, and an asked player says yes here. {@code notice} is the result of the
     * board button just pressed (menu first), or null.
     */
    public static void openHome(ServerPlayer player, String notice) {
        UltraScreenSpec spec = base("main", "home");
        if (notice != null && !notice.isBlank()) {
            spec.notice(notice);
        }
        // 4.7.78: the Spar Board is its own tab; Home does not advertise it.
        spec.line(UltraStyle.header("What a spar is worth to you"));
        spec.line(UltraStyle.VALUE + HubDashboard.sparWinLine(player));
        // 4.7.68: spar sessions train Potential Unlock only when you look for it on a friendly spar.
        spec.line(UltraStyle.LABEL + "Potential Unlock trains in the world, and in friendly spars when you look for it.");
        String streak = HubDashboard.streakLine(player);
        if (streak != null) {
            spec.line(UltraStyle.VALUE + streak);
        }
        spec.buttonsAfterLines();
        // 4.7.78: Hall of Infamy and Your stats moved to the Leaderboard tab.
        UltraPickHelper.placePageButtons(
                spec,
                "weights|Weights",
                "settings|TP Chat");
        if (StaffAccess.isStaff(player)) {
            spec.buttonLeft("page:admin", UltraStyle.danger("Staff Admin"), UltraStyle.STYLE_DANGER).rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /** 4.7.78: the Leaderboard tab: Hall of Infamy and your own spar record. */
    private static void openLeaderboard(ServerPlayer player) {
        UltraScreenSpec spec = base("leaderboard", "leaderboard");
        spec.line(UltraStyle.header("Leaderboard"))
                .line(UltraStyle.labelValue("Hall of Infamy", "the top sparrers by TP, sessions and combos"))
                .line(UltraStyle.labelValue("Your stats", "your spar record and your last three fights"));
        spec.buttonsAfterLines();
        UltraPickHelper.placePageButtons(spec, "infamy|Hall of Infamy", "stats|Your stats");
        spec.back();
        spec.send(player);
    }

    private static void openStats(ServerPlayer player) {
        UltraScreenSpec spec = sub("stats").line(UltraStyle.header("Your spar stats"));
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
                // A list of reports to open: normal style, plain label.
                spec.button("page:fight_report:" + idx, label, UltraStyle.STYLE_NORMAL).rowGap();
            }
        }
        spec.back();
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
                .line(UltraStyle.header("Fight report"));
        for (String line : SparGuiApi.recentReportLines(player, index)) {
            if (line != null && !line.isBlank()) {
                spec.line(line);
            }
        }
        spec.buttonsAfterLines();
        spec.back();
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
        // The board's first line is its own heading ("Hall of Infamy - ...", "Dojo Top - ..."); the page adds none.
        UltraScreenSpec spec = sub(dojoBoard ? "dojo_top_" + cat : "top_" + cat);
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
        spec.back();
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
        // Members is a sub-page with its own your-dojo / mentor's-dojo toggle.
        if ("members".equals(key) || "members_own".equals(key) || "members_joined".equals(key)) {
            if ("members".equals(key)) {
                key = SparGuiApi.ownsDojo(player) ? "members_own" : "members_joined";
            }
            openDojoMembers(player, key);
            return;
        }
        // Previous members: the same toggle, listing who left (4.7.44).
        if ("previous_own".equals(key) || "previous_joined".equals(key)) {
            openDojoMembers(player, key);
            return;
        }
        Map<String, String> ph = SparGuiApi.placeholders(player);
        // 4.7.71: the roster is its own page under the Dojo tab (it shared the tab's page id, so Back, the
        // breadcrumb and search could not tell them apart).
        UltraScreenSpec spec = dsub("dojo_roster");
        if (!"true".equalsIgnoreCase(ph.get("system_enabled"))) {
            spec.line(UltraStyle.danger("Sparring is turned off. Ask staff to enable it."))
                    .buttonsAfterLines();
            spec.back();
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
                "dojo_previous_members|Previous members",
                "mentor|Training bonds");
        spec.back();
        spec.send(player);
    }

    /**
     * Dojo members sub-page (the current roster) and, for {@code previous_*} keys, the Previous members
     * sub-page (who left this season). The toggle is always available, including when the player owns
     * neither dojo. Recruit and request stay on the Dojo tab.
     */
    private static void openDojoMembers(ServerPlayer player, String key) {
        boolean previous = key != null && key.startsWith("previous");
        boolean own = "members_own".equals(key) || "previous_own".equals(key);
        String prefix = previous ? "dojo_previous_members" : "dojo_members";
        String page = prefix + (own ? "_own" : "_joined");
        Map<String, String> ph = SparGuiApi.placeholders(player);
        UltraScreenSpec spec = dsub(page);
        if (!"true".equalsIgnoreCase(ph.get("system_enabled"))) {
            spec.line(UltraStyle.danger("Sparring is turned off. Ask staff to enable it."))
                    .buttonsAfterLines();
            spec.back();
            spec.send(player);
            return;
        }
        List<String> lines = previous
                ? (own ? SparGuiApi.ownedDojoPreviousMemberLines(player)
                        : SparGuiApi.joinedDojoPreviousMemberLines(player))
                : (own ? SparGuiApi.ownedDojoMemberLines(player) : SparGuiApi.joinedDojoMemberLines(player));
        if (lines != null) {
            for (String line : lines) {
                if (line != null && !line.isBlank()) {
                    spec.line(line);
                }
            }
        }
        spec.buttonsAfterLines();
        // The view not shown is a live button: normal style (dim is the locked look).
        spec.buttonLeft(
                        "page:" + prefix + "_own",
                        own ? UltraStyle.confirm("Your dojo") : "Your dojo",
                        own ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_NORMAL)
                .buttonRight(
                        "page:" + prefix + "_joined",
                        own ? "Your mentor's dojo" : UltraStyle.confirm("Your mentor's dojo"),
                        own ? UltraStyle.STYLE_NORMAL : UltraStyle.STYLE_CONFIRM)
                .rowGap();
        spec.back();
        spec.send(player);
    }

    private static void openMentor(ServerPlayer player) {
        // mentorLines opens with the "Training bonds" heading (the page's registry title).
        UltraScreenSpec spec = dsub("mentor");
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
                        UltraStyle.STYLE_DANGER)
                .rowGap();
        spec.back();
        spec.send(player);
    }

    /** Cards per picker page — the old hard cap, so each page keeps its layout. */
    private static final int CARDS_PER_PAGE = 8;
    /** Online-player picker page size (20 since 4.7.55; the page scrolls when it is taller than the window). */
    private static final int NAMES_PER_PAGE = 20;

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
        UltraScreenSpec spec = dsub(UltraPickHelper.pageArg("pending", page))
                .line(UltraStyle.header("Bond invites"))
                .line(UltraStyle.LABEL + "Mentor and apprentice requests. Tap one to answer or withdraw it.");
        List<String> all = SparGuiApi.pendingMentorInviteCards(player);
        // One slice feeds both the invite lines and their buttons (as on the war-request page),
        // so each invite's text shows only on the page that has its button.
        List<String> cards = actionableCards(all);
        int pages = UltraPickHelper.pageCount(cards.size(), CARDS_PER_PAGE);
        int pg = UltraPickHelper.clampPage(page, pages);
        List<String> pageCards = UltraPickHelper.pageSlice(cards, CARDS_PER_PAGE, pg);
        if (all == null || all.isEmpty()) {
            for (String line : SparGuiApi.pendingMentorLines(player)) {
                if (line != null && !line.isBlank()) {
                    spec.line(line);
                }
            }
        } else {
            spec.line(SparGuiApi.PENDING_MENTOR_HEADER);
            for (String card : pageCards) {
                String line = SparGuiApi.pendingMentorLine(card);
                if (line != null) {
                    spec.line(line);
                }
            }
        }
        if (pageCards.isEmpty()) {
            spec.line(UltraStyle.dim(
                    "No bond invites waiting. Send one from Training bonds, or wait for someone to invite you."));
        }
        UltraPickHelper.pageLabel(spec, pg, pages);
        spec.buttonsAfterLines();
        int placed = 0;
        for (String card : pageCards) {
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
            // A picker: normal style, plain name.
            String name = UltraStyle.plain(shown);
            if (placed % 2 == 0) {
                spec.buttonLeft(action, name, UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonRight(action, name, UltraStyle.STYLE_NORMAL);
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        UltraPickHelper.placePrevNext(spec, "pending", pg, pages);
        spec.back();
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
        UltraScreenSpec spec = dsub("pending_decide:"
                + withReturnPage((outgoing ? "OUT|" : "IN|") + picker, returnPage))
                .line(UltraStyle.header("Training bond invite"))
                .labelValue("Player", who == null || who.isBlank() ? "-" : who);
        if (outgoing) {
            spec.line(UltraStyle.LABEL + "You invited this player.");
        } else {
            spec.line(UltraStyle.LABEL + "Accept to form this training bond.")
                    .line(UltraStyle.LABEL + "Decline to dismiss the invite.");
        }
        spec.buttonsAfterLines();
        if (outgoing) {
            spec.button("do:mentor:cancel:" + picker, UltraStyle.danger("Withdraw invite"), UltraStyle.STYLE_DANGER)
                    .rowGap();
        } else {
            // Stack Accept above Decline - side-by-side is a misclick invite-kill risk.
            spec.button("do:mentor_accept:" + picker, UltraStyle.confirm("Accept"), UltraStyle.STYLE_CONFIRM)
                    .rowGap();
            spec.button("do:mentor_decline:" + picker, UltraStyle.danger("Decline"), UltraStyle.STYLE_DANGER)
                    .rowGap();
        }
        spec.back();
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
        String plain = who == null || who.isBlank() ? "-" : UltraStyle.plain(who);
        UltraScreenSpec spec = dsub("pick_confirm:" + payload)
                .line(UltraStyle.header(copy[0]))
                .labelValue("Target", plain)
                .line(UltraStyle.LABEL + askLine(action, plain))
                .buttonsAfterLines();
        boolean destructive = isDestructiveConfirm(action);
        spec.button(
                        "do:" + action + ":" + targetArg,
                        destructive ? UltraStyle.danger(confirmVerb(action, plain)) : UltraStyle.confirm(confirmVerb(action, plain)),
                        destructive ? UltraStyle.STYLE_DANGER : UltraStyle.STYLE_CONFIRM)
                .rowGap();
        // Back is named and routed by UltraScreenSpec.back() (page registry / history).
        spec.back();
        spec.send(player);
    }

    /** The confirm button names its action (4.7.56); a long name falls back to the verb alone. */
    private static String confirmVerb(String action, String who) {
        String full = switch (action == null ? "" : action) {
            case "mentor_invite" -> "Invite " + who;
            case "apprentice_invite" -> "Ask " + who;
            case "mentor_release" -> "Release " + who;
            case "mentor" -> "Leave mentor";
            case "mentor_accept" -> "Accept invite";
            case "mentor_decline" -> "Decline invite";
            case "dojo_challenge" -> "Challenge dojo";
            default -> "Confirm";
        };
        if (UltraStyle.fitsButton(full)) {
            return full;
        }
        int sp = full.indexOf(' ');
        return sp > 0 ? full.substring(0, sp) : "Confirm";
    }

    /** Leave / release / decline are irreversible or dismissive - wire danger, not confirm. */
    private static boolean isDestructiveConfirm(String action) {
        return switch (action == null ? "" : action) {
            case "mentor", "mentor_release", "mentor_decline" -> true;
            default -> false;
        };
    }

    /** The ask-page question: names the action and who it is for (4.7.31). */
    private static String askLine(String action, String who) {
        return switch (action == null ? "" : action) {
            case "mentor_invite" -> "Invite " + who + " to train under you?";
            case "apprentice_invite" -> "Ask " + who + " to be your master?";
            case "mentor_release" -> "Release " + who + " as your apprentice? A 12-hour cooldown starts.";
            case "mentor" -> "Leave your master? A 12-hour cooldown starts.";
            case "mentor_accept" -> "Accept " + who + "'s invite and form a training bond?";
            case "mentor_decline" -> "Decline " + who + "'s mentor invite?";
            case "dojo_challenge" -> "Challenge " + who + " to a dojo war?";
            default -> confirmCopy(action)[1];
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
            default -> new String[] {"Sparring action", "Check the target above, then confirm."};
        };
    }

    private static void openSettings(ServerPlayer player) {
        Map<String, String> ph = SparGuiApi.placeholders(player);
        boolean tpOn = "true".equalsIgnoreCase(ph.get("tpMsg"));
        boolean mentorTpOn = "true".equalsIgnoreCase(ph.get("mentorTpMsg"));
        UltraScreenSpec spec = sub("settings")
                .line(UltraStyle.header("Training point messages"))
                .buttonsAfterLines();
        // An OFF toggle still works: normal style, not the dim locked look (as on the rival TP Chat page).
        spec.buttonLeft(
                        "do:tpmsg:toggle",
                        tpOn ? UltraStyle.confirm("TP Chat - ON") : "TP Chat - OFF",
                        tpOn ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_NORMAL)
                .buttonRight(
                        "do:mentor_tpmsg:toggle",
                        mentorTpOn
                                ? UltraStyle.confirm("Mentor TP - ON")
                                : "Mentor TP - OFF",
                        mentorTpOn ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_NORMAL)
                .rowGap();
        spec.back();
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
        UltraScreenSpec spec = dsub(here)
                .line(UltraStyle.header(heading))
                .line(UltraStyle.LABEL + "Choose an online player. You confirm on the next page.");
        if (names.isEmpty()) {
            spec.line(UltraStyle.dim("Nobody else is online right now - try again when other players are on."));
        }
        UltraPickHelper.pageLabel(spec, pg, pages);
        spec.buttonsAfterLines();
        int placed = 0;
        for (String name : UltraPickHelper.pageSlice(names, NAMES_PER_PAGE, pg)) {
            String nav = "page:pick_confirm:" + action + "|" + here + "|" + name;
            // A picker: normal style, plain name.
            if (placed % 2 == 0) {
                spec.buttonLeft(nav, name, UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonRight(nav, name, UltraStyle.STYLE_NORMAL);
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        UltraPickHelper.placePrevNext(spec, page, pg, pages);
        spec.back();
        spec.send(player);
    }

    private static void openReleasePick(ServerPlayer player, int page) {
        List<String> cards = actionableCards(SparGuiApi.apprenticeCards(player));
        int pages = UltraPickHelper.pageCount(cards.size(), CARDS_PER_PAGE);
        int pg = UltraPickHelper.clampPage(page, pages);
        String back = UltraPickHelper.pageArg("pick_release", pg);
        UltraScreenSpec spec = dsub(back)
                .line(UltraStyle.header("Release apprentice"))
                .line(UltraStyle.LABEL + "Choose an apprentice. You confirm on the next page.");
        if (cards.isEmpty()) {
            spec.line(UltraStyle.dim("No apprentices to release. Recruit one from Training bonds first."));
        }
        UltraPickHelper.pageLabel(spec, pg, pages);
        spec.buttonsAfterLines();
        int placed = 0;
        for (String card : UltraPickHelper.pageSlice(cards, CARDS_PER_PAGE, pg)) {
            String[] parts = splitCard(card);
            String arg = parts[0].trim();
            String label = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : arg;
            String nav = "page:pick_confirm:mentor_release|" + back + "|" + arg;
            // A picker: normal style, plain name.
            if (placed % 2 == 0) {
                spec.buttonLeft(nav, label, UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonRight(nav, label, UltraStyle.STYLE_NORMAL);
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        UltraPickHelper.placePrevNext(spec, "pick_release", pg, pages);
        spec.back();
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
                .line(UltraStyle.header("Staff spar tools"))
                .labelValue("Session", active ? "in progress" : "not running")
                .labelValue("Partner", partner == null || partner.isBlank() ? "none" : partner)
                .buttonsAfterLines();
        spec.buttonLeft("do:admin:save", UltraStyle.confirm("Save data"), UltraStyle.STYLE_CONFIRM)
                .buttonRight("do:admin:status", UltraStyle.LABEL + "Show status", UltraStyle.STYLE_NORMAL)
                .rowGap();
        spec.back();
        spec.send(player);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
