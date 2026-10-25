package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.gui.RivalGuiApi;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/**
 * Rival system - CNPC parity via page: navigation and arg-bearing do: buttons.
 * Tabs: Home / Rival (list + picker) / Challenge (W/L + duels) / Info (RP, tier, nemesis).
 */
public final class UltraRivalScreens {
    /** Online player pickers: two-up rows; page so large servers never truncate. */
    private static final int PICK_PER_PAGE = 8;
    // Page sizes below are the old hard caps, so each page keeps its layout.
    private static final int RIVALS_PER_PAGE = 8;
    private static final int DECLARES_PER_PAGE = 4;
    private static final int CHALLENGES_PER_PAGE = 6;
    private static final int MUTUALS_PER_PAGE = 6;

    private UltraRivalScreens() {}

    public static void open(ServerPlayer player, String page) {
        if (player == null) {
            return;
        }
        String raw = page == null || page.isBlank() ? "home" : page.trim();
        String p = raw.toLowerCase(Locale.ROOT);
        if (p.startsWith("challenge_time:")) {
            openChallengeTime(player, raw.substring("challenge_time:".length()).trim());
            return;
        }
        if (p.startsWith("list_detail:")) {
            openListDetail(player, raw.substring("list_detail:".length()).trim());
            return;
        }
        if (p.startsWith("records:")) {
            openRecords(player, p.substring("records:".length()).trim());
            return;
        }
        if (p.startsWith("replace_confirm:")) {
            openReplaceConfirm(player, raw.substring("replace_confirm:".length()).trim());
            return;
        }
        if (p.startsWith("accept_confirm:")) {
            openAcceptConfirm(player, raw.substring("accept_confirm:".length()).trim());
            return;
        }
        if (p.startsWith("rival_type:")) {
            openRivalType(player, raw.substring("rival_type:".length()).trim());
            return;
        }
        int pickPage = parsePickPage(p);
        if (pickPage >= 0) {
            String kind = pickKind(p);
            if ("rival".equals(kind) || "declare".equals(kind) || "silent".equals(kind)) {
                // One picker for Silent / Declare / Accept — type chosen on the next page.
                openNamePick(player, "Choose a player", "rival_pick", "actions", pickPage);
                return;
            }
            if ("challenge".equals(kind) || "challenge_pick".equals(kind)) {
                openNamePick(player, "Challenge rival", "challenge_pick", "challenge", pickPage);
                return;
            }
            if ("spectate".equals(kind)) {
                openNamePick(player, "Spectate", "spectate", "challenge", pickPage);
                return;
            }
        }
        // Paged lists: "list:2" reopens the third page of the rival list.
        int listPage = UltraPickHelper.pageIndex(p);
        switch (UltraPickHelper.pageBase(p)) {
            case "actions", "action", "rival" -> openActions(player, listPage);
            case "info", "information" -> openInfo(player);
            case "challenge", "challenges" -> openChallenge(player, listPage);
            case "list" -> openList(player, listPage);
            case "top", "leaderboard" -> openTop(player);
            case "records" -> openRecords(player, "history");
            case "requests", "pending", "invites" -> openRequests(player, listPage);
            case "settings" -> openSettings(player);
            case "pick_rival", "pick_declare", "pick_silent" ->
                    openNamePick(player, "Choose a player", "rival_pick", "actions", 0);
            case "pick_challenge" -> openNamePick(player, "Challenge rival", "challenge_pick", "challenge", 0);
            case "pick_spectate" -> openNamePick(player, "Spectate", "spectate", "challenge", 0);
            case "pick_replace_mutual", "replace_mutual" -> openReplacePick(player, listPage);
            case "admin" -> openAdmin(player);
            default -> openHome(player);
        }
    }

    private static UltraScreenSpec base(String page, String tab) {
        return UltraScreenSpec.of("rival")
                .title("Rivals")
                .page(page)
                .activeTab(tab)
                .tabs("home|Home", "actions|Rival", "challenge|Challenge", "info|Info");
    }

    /** Subpage: no tab row — dual footer (Back + Main menu). */
    private static UltraScreenSpec sub(String page) {
        return UltraScreenSpec.of("rival")
                .title("Rivals")
                .page(page);
    }


    private static void openHome(ServerPlayer player) {
        UltraScreenSpec spec = base("home", "home")
                .line(UltraStyle.LABEL + "Leaderboard, records, and TP chat.")
                .buttonsAfterLines();
        UltraPickHelper.placePageButtons(
                spec, "top|Leaderboard", "records|Records", "settings|TP Chat",
                // Home is the index: the rival list opens from here too, not only through
                // /rival list or a post-action return. Requests lives on the Rival tab.
                "list|Rival list");
        if (StaffAccess.isStaff(player)) {
            spec.buttonLeft("page:admin", UltraStyle.danger("Staff Admin"), "danger").rowGap();
        }
        spec.footerBackToMain();
        spec.send(player);
    }

    /** Standing (RP, tier, nemesis, mutual) lives on Info, not Home. */
    private static void openInfo(ServerPlayer player) {
        Map<String, String> ph = RivalGuiApi.placeholders(player);
        UltraScreenSpec spec = base("info", "info")
                .labelValue("Current RP", ph.getOrDefault("rp", "0"))
                .labelValue("Rival tier", ph.getOrDefault("tier", "-"))
                .labelValue("Nemesis", RivalGuiApi.nemesisNames(player))
                .labelValue("Mutual rivals", RivalGuiApi.mutualNames(player))
                .labelValue(
                        "Mutual slots",
                        ph.getOrDefault("mutual", "0") + "/" + ph.getOrDefault("mutual_max", "0"))
                .buttonsAfterLines();
        spec.footerBackToMain();
        spec.send(player);
    }

    private static void openActions(ServerPlayer player, int page) {
        // Rivals (8 a page) and incoming declares (4 a page) share one page index on this tab.
        List<String> rivals = rivalListCards(RivalGuiApi.currentRivalCards(player));
        List<String> declares = nonBlank(RivalGuiApi.pendingIncomingDeclareArgs(player));
        int pages = Math.max(
                UltraPickHelper.pageCount(rivals.size(), RIVALS_PER_PAGE),
                UltraPickHelper.pageCount(declares.size(), DECLARES_PER_PAGE));
        int pg = UltraPickHelper.clampPage(page, pages);
        UltraScreenSpec spec = base(UltraPickHelper.pageArg("actions", pg), "actions")
                .line(UltraStyle.LABEL + "Your rivals. Pick a player, then choose Silent, Declare, or Accept.")
                // Matches the Accept/Decline stacks below; outgoing declares are counted apart.
                .labelValue("Incoming requests", String.valueOf(declares.size()));
        int awaiting = RivalGuiApi.outgoingDeclareCount(player);
        if (awaiting > 0) {
            spec.labelValue("Awaiting reply", String.valueOf(awaiting));
        }
        boolean needsMutualSlot = RivalGuiApi.needsMutualReplacePick(player);
        String pendingPlain = "them";
        if (needsMutualSlot) {
            String pending = RivalGuiApi.pendingMutualAcceptName(player);
            pendingPlain = pending == null || pending.isBlank() ? "them" : UltraStyle.plain(pending);
            spec.line(UltraStyle.LABEL + "Free a Mutual slot to accept " + pendingPlain);
        }
        UltraPickHelper.pageLabel(spec, pg, pages);
        spec.buttonsAfterLines();
        placeRivalListButtons(
                spec, player, UltraPickHelper.pageSlice(rivals, RIVALS_PER_PAGE, pg), rivals.isEmpty(), pg);
        placeIncomingDeclareButtons(
                spec, player, UltraPickHelper.pageSlice(declares, DECLARES_PER_PAGE, pg));
        UltraPickHelper.placePrevNext(spec, "actions", pg, pages);
        UltraPickHelper.placePageButtons(spec, "pick_rival|Pick player", "requests|Requests");
        if (needsMutualSlot) {
            spec.button(
                            "page:pick_replace_mutual",
                            UltraStyle.danger("Drop Mutual"),
                            "danger")
                    .rowGap();
        }
        spec.footerBackToMain();
        spec.send(player);
    }

    /** Non-blank entries in source order. */
    private static List<String> nonBlank(List<String> items) {
        List<String> out = new java.util.ArrayList<>();
        if (items != null) {
            for (String s : items) {
                if (s != null && !s.isBlank()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** Rival cards ({@code uuid\tname\tstatus}) that resolve to a uuid or a name, in source order. */
    private static List<String> rivalListCards(List<String> cards) {
        List<String> out = new java.util.ArrayList<>();
        for (String card : nonBlank(cards)) {
            String[] p = card.split("\t", -1);
            if (p.length < 2) {
                continue;
            }
            String uuid = p[0] == null ? "" : p[0].trim();
            String name = p[1] == null ? "" : p[1].trim();
            if (!uuid.isBlank() || !name.isBlank()) {
                out.add(card);
            }
        }
        return out;
    }

    /** One page of current rivals on the Rival tab — tap for profile / remove. */
    private static void placeRivalListButtons(
            UltraScreenSpec spec, ServerPlayer player, List<String> cards, boolean noRivals, int page) {
        int placed = 0;
        if (cards != null) {
            for (String card : cards) {
                String[] p = card.split("\t", -1);
                String uuid = p[0] == null ? "" : p[0].trim();
                String name = p[1] == null ? "" : p[1].trim();
                String status = p.length > 2 && p[2] != null ? p[2].trim() : "";
                String arg = !uuid.isBlank() ? "uuid:" + uuid : name;
                String shown = RivalGuiApi.displayPickerArg(player, arg);
                if (shown == null || shown.isBlank()) {
                    shown = name.isBlank() ? "-" : name;
                }
                String plain = UltraStyle.plain(shown);
                String labelText = rivalListLabel(plain, status);
                String action = "page:list_detail:" + profileArg(arg, page);
                String label = UltraStyle.confirm(labelText);
                if (placed % 2 == 0) {
                    spec.buttonLeft(action, label, "confirm");
                } else {
                    spec.buttonRight(action, label, "confirm");
                    spec.rowGap();
                }
                placed++;
            }
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        if (noRivals) {
            spec.line(UltraStyle.dim("No rivals on your list yet. Declare someone from Pick player."));
        }
    }

    /** One page of incoming declare Accept/Decline on the Rival tab — stacked to avoid misclicks. */
    private static void placeIncomingDeclareButtons(
            UltraScreenSpec spec, ServerPlayer player, List<String> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        for (String arg : incoming) {
            String shown = RivalGuiApi.displayPickerArg(player, arg);
            if (shown == null || shown.isBlank()) {
                shown = "-";
            }
            String plain = UltraStyle.plain(shown);
            stackAcceptDecline(spec, "do:accept:" + arg, "do:decline:" + arg, plain);
        }
    }

    /** Accept above Decline. Long names stay on a line so the buttons stay short. */
    private static void stackAcceptDecline(
            UltraScreenSpec spec, String acceptId, String declineId, String plain) {
        String who = plain == null || plain.isBlank() ? "them" : plain;
        String accept = "Accept " + who;
        String decline = "Decline " + who;
        if (!UltraStyle.fitsButton(accept) || !UltraStyle.fitsButton(decline)) {
            spec.line(UltraStyle.LABEL + who);
            accept = "Accept";
            decline = "Decline";
        }
        spec.button(acceptId, UltraStyle.confirm(accept), "confirm").rowGap();
        spec.button(declineId, UltraStyle.danger(decline), "danger").rowGap();
    }

    /** Name plus a short status tag when {@code Name (Status)} would clip. */
    private static String rivalListLabel(String plain, String status) {
        String name = plain == null || plain.isBlank() ? "-" : plain;
        if (status == null || status.isBlank()) {
            return UltraStyle.buttonText(name);
        }
        String full = name + " (" + status + ")";
        if (UltraStyle.fitsButton(full)) {
            return full;
        }
        String tag = switch (status.toLowerCase(java.util.Locale.ROOT)) {
            case "nemesis" -> "Nem";
            case "mutual" -> "Mut";
            case "declared" -> "Dec";
            case "silent" -> "Sil";
            case "pending" -> "Pen";
            case "past" -> "Past";
            default -> status.length() <= 4 ? status : status.substring(0, 3);
        };
        String compact = name + " " + tag;
        return UltraStyle.fitsButton(compact) ? compact : UltraStyle.buttonText(name);
    }

    /**
     * After picking a player: choose Silent, Declare, or Accept mutual request.
     * One flow replaces separate Silent / Declare / Requests entry points.
     */
    private static void openRivalType(ServerPlayer player, String arg) {
        if (arg == null || arg.isBlank()) {
            openActions(player, 0);
            return;
        }
        String who = RivalGuiApi.displayPickerArg(player, arg);
        String plain = who == null || who.isBlank() ? "-" : UltraStyle.plain(who);
        boolean incoming = RivalGuiApi.hasIncomingDeclareFrom(player, arg);
        UltraScreenSpec spec = sub("rival_type:" + arg)
                .line(UltraStyle.LABEL + "How do you want to rival " + plain + "?")
                .labelValue("Player", plain)
                .line(UltraStyle.LABEL
                        + (incoming
                                ? "They already declared you - Accept becomes Mutual."
                                : "Silent is private. Declare sends an invite for Mutual."))
                .buttonsAfterLines();
        spec.button("do:silent:" + arg, UltraStyle.confirm("Silent (private)"), "confirm")
                .rowGap();
        if (incoming) {
            spec.button(
                            "page:accept_confirm:" + arg,
                            UltraStyle.confirm("Accept their declare"),
                            "confirm")
                    .rowGap();
            spec.button("do:decline:" + arg, UltraStyle.danger("Decline their declare"), "danger")
                    .rowGap();
        } else {
            spec.button("do:declare:" + arg, UltraStyle.confirm("Declare (send invite)"), "confirm")
                    .rowGap();
        }
        spec.footerBack("page:pick_rival", "Players");
        spec.send(player);
    }

    private static void openChallenge(ServerPlayer player, int page) {
        Map<String, String> ph = RivalGuiApi.placeholders(player);
        List<String> challenges = challengeCards(RivalGuiApi.pendingChallengeCards(player));
        int pages = UltraPickHelper.pageCount(challenges.size(), CHALLENGES_PER_PAGE);
        int pg = UltraPickHelper.clampPage(page, pages);
        UltraScreenSpec spec = base(UltraPickHelper.pageArg("challenge", pg), "challenge")
                .labelValue("Wins", ph.getOrDefault("wins", "0"))
                .labelValue("Losses", ph.getOrDefault("losses", "0"));
        // 1) Challenge info
        for (String line : RivalGuiApi.challengeLines(player)) {
            if (line != null && !line.isBlank()) {
                spec.line(line);
            }
        }
        // 2) Active / pending challenges
        for (String line : RivalGuiApi.pendingChallengeLines(player)) {
            if (line != null && !line.isBlank()) {
                spec.line(line);
            }
        }
        UltraPickHelper.pageLabel(spec, pg, pages);
        spec.buttonsAfterLines();
        placePerChallengeButtons(
                spec, player, UltraPickHelper.pageSlice(challenges, CHALLENGES_PER_PAGE, pg));
        UltraPickHelper.placePrevNext(spec, "challenge", pg, pages);
        // 3) Challenge actions, then Spectate with Stop spectate on the same row.
        // Records (Home) is the history page. No per-challenge opponent/result/date log is stored.
        UltraPickHelper.placePageButtons(spec, "pick_challenge|Send challenge");
        spec.buttonLeft("page:pick_spectate", "Spectate", "normal");
        spec.buttonRight("do:spectate_stop", UltraStyle.confirm("Stop spectate"), UltraStyle.STYLE_CONFIRM).rowGap();
        spec.footerBackToMain();
        spec.send(player);
    }

    /** Pending challenge cards ({@code uuid\tname\tIN|OUT}) that name an opponent, in source order. */
    private static List<String> challengeCards(List<String> cards) {
        List<String> out = new java.util.ArrayList<>();
        for (String card : nonBlank(cards)) {
            String[] p = card.split("\t", -1);
            if (p.length < 3) {
                continue;
            }
            String uuid = p[0] == null ? "" : p[0].trim();
            String name = p[1] == null ? "" : p[1].trim();
            if (!uuid.isBlank() || !name.isBlank()) {
                out.add(card);
            }
        }
        return out;
    }

    /** One page of Accept/Decline (or Cancel) pairs, one per pending challenge. */
    private static void placePerChallengeButtons(
            UltraScreenSpec spec, ServerPlayer player, List<String> cards) {
        if (cards == null || cards.isEmpty()) {
            return;
        }
        for (String card : cards) {
            String[] p = card.split("\t", -1);
            String uuid = p[0] == null ? "" : p[0].trim();
            String name = p.length > 1 && p[1] != null && !p[1].isBlank() ? p[1].trim() : uuid;
            String dir = p[2] == null ? "" : p[2].trim();
            String picker = uuid.isBlank() ? name : "uuid:" + uuid;
            String shown = RivalGuiApi.displayPickerArg(player, picker);
            if (shown == null || shown.isBlank()) {
                shown = name.isBlank() ? "-" : name;
            }
            String plain = UltraStyle.plain(shown);
            if ("IN".equalsIgnoreCase(dir)) {
                stackAcceptDecline(
                        spec,
                        "do:challenge_accept:" + picker,
                        "do:challenge_decline:" + picker,
                        plain);
            } else {
                String cancel = "Cancel " + plain;
                if (!UltraStyle.fitsButton(cancel)) {
                    spec.line(UltraStyle.LABEL + plain);
                    cancel = "Cancel";
                }
                spec.button(
                                "do:challenge_cancel:" + picker,
                                UltraStyle.danger(cancel),
                                "danger")
                        .rowGap();
            }
        }
    }

    private static void openList(ServerPlayer player, int page) {
        List<String> args = nonBlank(RivalGuiApi.currentRivalArgs(player));
        int pages = UltraPickHelper.pageCount(args.size(), RIVALS_PER_PAGE);
        int pg = UltraPickHelper.clampPage(page, pages);
        UltraScreenSpec spec = sub(UltraPickHelper.pageArg("list", pg))
                .line(UltraStyle.LABEL + "Your current rivals.");
        for (String line : RivalGuiApi.listLines(player)) {
            if (line != null && !line.isBlank()) {
                spec.line(line);
            }
        }
        UltraPickHelper.pageLabel(spec, pg, pages);
        spec.buttonsAfterLines();
        int placed = 0;
        for (String arg : UltraPickHelper.pageSlice(args, RIVALS_PER_PAGE, pg)) {
            String action = "page:list_detail:" + profileArg(arg, pg);
            String shown = RivalGuiApi.displayPickerArg(player, arg);
            if (shown == null || shown.isBlank()) {
                shown = "-";
            }
            String label = UltraStyle.confirm(shown);
            if (placed % 2 == 0) {
                spec.buttonLeft(action, label, "confirm");
            } else {
                spec.buttonRight(action, label, "confirm");
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        if (placed == 0) {
            spec.line(UltraStyle.dim("No rivals on your list yet. Declare someone from Pick player."));
        }
        UltraPickHelper.placePrevNext(spec, "list", pg, pages);
        spec.footerBack("page:actions", "Rival");
        spec.send(player);
    }

    /**
     * Profile link arg: page 2+ rides along as {@code |N} so the profile's Back returns to that
     * page of the Rival tab. Names and {@code uuid:} args never contain {@code |}.
     */
    private static String profileArg(String arg, int page) {
        return page > 0 ? arg + "|" + page : arg;
    }

    private static void openListDetail(ServerPlayer player, String payload) {
        String arg = payload == null ? "" : payload;
        int returnPage = 0;
        int bar = arg.lastIndexOf('|');
        if (bar > 0) {
            returnPage = UltraPickHelper.pageIndex("p:" + arg.substring(bar + 1));
            if (returnPage > 0) {
                arg = arg.substring(0, bar);
            }
        }
        String who = RivalGuiApi.displayPickerArg(player, arg);
        UltraScreenSpec spec = sub("list_detail:" + payload)
                .line(UltraStyle.LABEL + "Rival profile")
                .labelValue("Target", who == null || who.isBlank() ? "-" : who)
                .notice("Removing a rival cannot be undone from this menu.")
                .buttonsAfterLines();
        boolean stillRival = false;
        if (arg != null && !arg.isBlank()) {
            for (String cur : RivalGuiApi.currentRivalArgs(player)) {
                if (arg.equalsIgnoreCase(cur)) {
                    stillRival = true;
                    break;
                }
            }
        }
        if (stillRival) {
            // Silent can be upgraded from the profile. Declared and above already sent that step.
            if ("silent".equalsIgnoreCase(RivalGuiApi.rivalStatusLabel(player, arg))) {
                if (RivalGuiApi.hasIncomingDeclareFrom(player, arg)) {
                    // They already declared you - a bare Declare would silently
                    // become Accept. Route to the confirm page that says so.
                    spec.button(
                                    "page:accept_confirm:" + arg,
                                    UltraStyle.confirm("Accept their declare"),
                                    "confirm")
                            .rowGap();
                } else {
                    spec.button("do:declare:" + arg, UltraStyle.confirm("Declare"), "confirm")
                            .rowGap();
                }
            }
            spec.button("do:remove:" + arg, UltraStyle.danger("Remove rival"), "danger")
                    .rowGap();
        } else if (arg != null && !arg.isBlank()) {
            spec.line(UltraStyle.dim("Not on your rival list."));
        }
        spec.footerBack("page:" + UltraPickHelper.pageArg("actions", returnPage), "Rival");
        spec.send(player);
    }

    private static void openTop(ServerPlayer player) {
        UltraScreenSpec spec = sub("top").line(UltraStyle.LABEL + "Rival leaderboard");
        for (String line : RivalGuiApi.topLines(player)) {
            if (line != null && !line.isBlank()) {
                spec.line(line);
            }
        }
        spec.buttonsAfterLines();
        spec.footerBackHome("Rival Home");
        spec.send(player);
    }

    private static void openRecords(ServerPlayer player, String sub) {
        String tab = sub == null || sub.isBlank() ? "history" : sub.toLowerCase(Locale.ROOT);
        UltraScreenSpec spec = sub("records:" + tab)
                .line(UltraStyle.LABEL + "Records - " + recordsTitle(tab));
        List<String> lines = switch (tab) {
            case "stats" -> RivalGuiApi.statsLines(player);
            case "season" -> RivalGuiApi.seasonLines(player);
            case "quests" -> RivalGuiApi.questLines(player);
            case "achievements" -> RivalGuiApi.achievementLines(player);
            case "hof" -> RivalGuiApi.hofLines(player);
            case "journal" -> RivalGuiApi.journalLines(player);
            case "title" -> RivalGuiApi.titleLines(player);
            default -> RivalGuiApi.linesForPage(player, "records:" + tab);
        };
        if (lines != null) {
            for (String line : lines) {
                if (line != null && !line.isBlank()) {
                    spec.line(line);
                }
            }
        }
        spec.buttonsAfterLines();
        UltraPickHelper.placePageButtons(
                spec,
                "records:history|History",
                "records:stats|Stats",
                "records:season|Season",
                "records:quests|Quests",
                "records:achievements|Achievements",
                "records:hof|Hall of fame",
                "records:journal|Journal",
                "records:title|Title");
        spec.footerBackHome("Rival Home");
        spec.send(player);
    }

    /** Records sub-page title, worded like its button (never the page id). */
    private static String recordsTitle(String tab) {
        return switch (tab == null ? "" : tab) {
            case "stats" -> "Stats";
            case "season" -> "Season";
            case "quests" -> "Quests";
            case "achievements" -> "Achievements";
            case "hof" -> "Hall of fame";
            case "journal" -> "Journal";
            case "title" -> "Title";
            default -> "History";
        };
    }

    private static void openRequests(ServerPlayer player, int page) {
        List<String> incoming = nonBlank(RivalGuiApi.pendingIncomingDeclareArgs(player));
        int pages = UltraPickHelper.pageCount(incoming.size(), DECLARES_PER_PAGE);
        int pg = UltraPickHelper.clampPage(page, pages);
        UltraScreenSpec spec = sub(UltraPickHelper.pageArg("requests", pg))
                .line(UltraStyle.LABEL + "Incoming and outgoing rival requests.")
                .line(UltraStyle.LABEL + "Accept a declare to become Mutual. Decline leaves them Declared only.");
        List<String> cards = RivalGuiApi.pendingInviteCards(player);
        if (cards != null) {
            for (String card : cards) {
                if (card == null || card.isBlank()) {
                    continue;
                }
                for (String line : RivalGuiApi.pendingInviteDetailLines(card)) {
                    if (line != null && !line.isBlank()) {
                        spec.line(line);
                    }
                }
            }
        }
        UltraPickHelper.pageLabel(spec, pg, pages);
        spec.buttonsAfterLines();
        int placed = 0;
        for (String arg : UltraPickHelper.pageSlice(incoming, DECLARES_PER_PAGE, pg)) {
            String shown = RivalGuiApi.displayPickerArg(player, arg);
            if (shown == null || shown.isBlank()) {
                shown = "-";
            }
            String plain = UltraStyle.plain(shown);
            // Stack Accept above Decline per player — side-by-side is a misclick risk.
            stackAcceptDecline(spec, "do:accept:" + arg, "do:decline:" + arg, plain);
            placed++;
        }
        if (placed == 0) {
            spec.line(UltraStyle.dim("No incoming declare requests right now. Wait for an invite, or declare someone yourself."));
        }
        UltraPickHelper.placePrevNext(spec, "requests", pg, pages);
        spec.footerBack("page:actions", "Rival");
        spec.send(player);
    }

    private static void openSettings(ServerPlayer player) {
        Map<String, String> ph = RivalGuiApi.placeholders(player);
        boolean tpOn = "true".equalsIgnoreCase(ph.getOrDefault("tpMsg", ph.getOrDefault("tp_msg", "false")));
        boolean instinctOn = "true".equalsIgnoreCase(ph.getOrDefault("instinct", "false"));
        UltraScreenSpec spec = sub("settings")
                .line(UltraStyle.LABEL + "Rival chat and instinct toggles.")
                .labelValue("TP Chat", tpOn ? "ON" : "OFF")
                .labelValue("Instinct", instinctOn ? "ON" : "OFF")
                .buttonsAfterLines();
        spec.buttonLeft(
                        "do:tpmsg:toggle",
                        tpOn ? UltraStyle.confirm("TP Chat - ON") : UltraStyle.dim("TP Chat - OFF"),
                        tpOn ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_DIM)
                .buttonRight(
                        "do:instinct",
                        instinctOn
                                ? UltraStyle.confirm("Instinct - ON")
                                : UltraStyle.dim("Instinct - OFF"),
                        instinctOn ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_DIM)
                .rowGap();
        spec.footerBackHome("Rival Home");
        spec.send(player);
    }

    private static void openNamePick(
            ServerPlayer player, String heading, String doAction, String returnTab, int pageIndex) {
        String tab = "actions".equals(returnTab) ? "actions" : "challenge";
        String pageBase;
        if ("challenge_pick".equals(doAction)) {
            pageBase = "pick_challenge";
        } else if ("rival_pick".equals(doAction)
                || "declare".equals(doAction)
                || "silent".equals(doAction)) {
            pageBase = "pick_rival";
        } else {
            pageBase = "pick_" + doAction;
        }
        List<String> args;
        String emptyCopy;
        if ("spectate".equals(doAction)) {
            args = RivalGuiApi.activeChallengeArgs(player);
            emptyCopy = "No active challenges right now. Challenge a rival first.";
        } else if ("rival_pick".equals(doAction)
                || "declare".equals(doAction)
                || "silent".equals(doAction)) {
            List<String> online = RivalGuiApi.onlinePlayerArgs(player);
            args = RivalGuiApi.excludingCurrentRivals(player, online);
            emptyCopy = online == null || online.isEmpty()
                    ? "Nobody is available right now. Try again when other players are on."
                    : "Everyone online is already a rival. Drop a rival first if you want a new one.";
        } else {
            args = RivalGuiApi.onlinePlayerArgs(player);
            emptyCopy = "Nobody is available right now. Try again when other players are on.";
        }
        int total = args == null ? 0 : args.size();
        int pages = UltraPickHelper.pageCount(total, PICK_PER_PAGE);
        int pg = UltraPickHelper.clampPage(pageIndex, pages);
        int from = pg * PICK_PER_PAGE;
        int to = Math.min(total, from + PICK_PER_PAGE);
        UltraScreenSpec spec = sub(UltraPickHelper.pageArg(pageBase, pg))
                .line(UltraStyle.LABEL + heading)
                .line(UltraStyle.LABEL + pickExplainer(doAction))
                .labelValue("spectate".equals(doAction) ? "In a fight" : "Players", String.valueOf(total));
        if (total == 0) {
            spec.line(UltraStyle.dim(emptyCopy));
        }
        UltraPickHelper.pageLabel(spec, pg, pages);
        spec.buttonsAfterLines();
        if ("spectate".equals(doAction)) {
            // Spectate is a plain do: picker, so it uses the shared paged helper (same 8 a page,
            // same do:spectate:<arg> buttons, same labels). Its empty line is shown above.
            List<String> choices = new java.util.ArrayList<>();
            for (String arg : nonBlank(args)) {
                String shown = RivalGuiApi.displayPickerArg(player, arg);
                choices.add(arg + "|" + UltraStyle.plain(shown == null || shown.isBlank() ? "-" : shown));
            }
            UltraPickHelper.placeNameButtons(spec, doAction, choices, PICK_PER_PAGE, pageBase, pg, null);
            spec.footerBack("page:challenge", "Challenge");
            spec.send(player);
            return;
        }
        int placed = 0;
        for (int i = from; i < to; i++) {
            String arg = args.get(i);
            if (arg == null || arg.isBlank()) {
                continue;
            }
            String shown = RivalGuiApi.displayPickerArg(player, arg);
            if (shown == null || shown.isBlank()) {
                shown = "-";
            }
            String plain = UltraStyle.plain(shown);
            String action;
            String label;
            String style;
            if ("challenge_pick".equals(doAction)) {
                action = "page:challenge_time:" + arg;
                label = UltraStyle.confirm(plain);
                style = UltraStyle.STYLE_CONFIRM;
            } else if ("rival_pick".equals(doAction)
                    || "declare".equals(doAction)
                    || "silent".equals(doAction)) {
                // Consolidated flow: pick person first, then Silent / Declare / Accept.
                action = "page:rival_type:" + arg;
                label = UltraStyle.confirm(plain);
                style = UltraStyle.STYLE_CONFIRM;
            } else {
                action = "do:" + doAction + ":" + arg;
                label = UltraStyle.confirm(plain);
                style = UltraStyle.STYLE_CONFIRM;
            }
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
        UltraPickHelper.placePrevNext(spec, pageBase, pg, pages);
        spec.footerBack(
                "page:" + ("challenge".equals(tab) ? "challenge" : "actions"),
                "challenge".equals(tab) ? "Challenge" : "Rival");
        spec.send(player);
    }

    private static void openChallengeTime(ServerPlayer player, String target) {
        String who = RivalGuiApi.displayPickerArg(player, target);
        UltraScreenSpec spec = sub("challenge_time:" + target)
                .line(UltraStyle.LABEL + "Challenge length")
                .labelValue("Rival", who == null || who.isBlank() ? "-" : who)
                .buttonsAfterLines();
        if (target != null && !target.isBlank()) {
            for (int min = 1; min <= 10; min += 2) {
                spec.buttonLeft(
                        "do:challenge_send:" + target + "@" + min,
                        UltraStyle.confirm(min + " min"),
                        "confirm");
                if (min + 1 <= 10) {
                    spec.buttonRight(
                            "do:challenge_send:" + target + "@" + (min + 1),
                            UltraStyle.confirm((min + 1) + " min"),
                            "confirm");
                }
                spec.rowGap();
            }
        }
        spec.footerBack("page:pick_challenge", "Challenge pick");
        spec.send(player);
    }

    private static void openAcceptConfirm(ServerPlayer player, String arg) {
        String who = RivalGuiApi.displayPickerArg(player, arg);
        String plain = who == null || who.isBlank() ? "-" : UltraStyle.plain(who);
        UltraScreenSpec spec = sub("accept_confirm:" + arg)
                .line(UltraStyle.LABEL + "They already declared you")
                .labelValue("Player", plain)
                .notice("Accept mutual rivalry with " + plain + " instead of sending another declare?")
                .buttonsAfterLines();
        if (arg != null && !arg.isBlank()) {
            spec.button("do:accept:" + arg, UltraStyle.confirm("Accept mutual rivalry"), "confirm")
                    .rowGap();
        }
        spec.footerBack("page:pick_rival", "Players");
        spec.send(player);
    }

    private static void openReplacePick(ServerPlayer player, int page) {
        String pending = RivalGuiApi.pendingMutualAcceptName(player);
        String pendingPlain = pending == null || pending.isBlank() ? "them" : UltraStyle.plain(pending);
        List<String> choices = new java.util.ArrayList<>();
        for (String raw : nonBlank(labeledArgs(player, RivalGuiApi.mutualRivalArgs(player)))) {
            int bar = raw.indexOf('|');
            if (!(bar > 0 ? raw.substring(0, bar) : raw).isBlank()) {
                choices.add(raw);
            }
        }
        int pages = UltraPickHelper.pageCount(choices.size(), MUTUALS_PER_PAGE);
        int pg = UltraPickHelper.clampPage(page, pages);
        UltraScreenSpec spec = sub(UltraPickHelper.pageArg("pick_replace_mutual", pg))
                .line(UltraStyle.LABEL + "Drop a Mutual to accept " + pendingPlain)
                .notice("Mutual slots are full. Pick who to demote to Declared so you can accept "
                        + pendingPlain + ".");
        UltraPickHelper.pageLabel(spec, pg, pages);
        spec.buttonsAfterLines();
        // Navigate to confirm — do not accept_replace immediately.
        int placed = 0;
        for (String raw : UltraPickHelper.pageSlice(choices, MUTUALS_PER_PAGE, pg)) {
            int bar = raw.indexOf('|');
            String arg = bar > 0 ? raw.substring(0, bar).trim() : raw.trim();
            String label = bar > 0 ? raw.substring(bar + 1).trim() : arg;
            if (label.isBlank()) {
                label = arg;
            }
            // Page 2+ rides along as "|N" so the confirm page's Back returns here.
            String action = "page:replace_confirm:" + (pg > 0 ? arg + "|" + pg : arg);
            String styled = UltraStyle.danger("Drop " + UltraStyle.plain(label));
            if (placed % 2 == 0) {
                spec.buttonLeft(action, styled, "danger");
            } else {
                spec.buttonRight(action, styled, "danger");
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        if (placed == 0) {
            spec.line(UltraStyle.dim("No Mutual rivals to drop right now. Mutual rivals appear here once you have one."));
        }
        UltraPickHelper.placePrevNext(spec, "pick_replace_mutual", pg, pages);
        // Escape hatch — do not trap the player on this page.
        spec.button("do:cancel_mutual_replace", UltraStyle.dim("Decline instead"), "dim").rowGap();
        spec.footerBack("page:actions", "Rival");
        spec.send(player);
    }

    private static void openReplaceConfirm(ServerPlayer player, String payload) {
        // "arg|N" from page 2+ of the replace pick; names and uuid: args never contain "|".
        String dropArg = payload == null ? "" : payload;
        int returnPage = 0;
        int bar = dropArg.lastIndexOf('|');
        if (bar > 0) {
            returnPage = UltraPickHelper.pageIndex("p:" + dropArg.substring(bar + 1));
            if (returnPage > 0) {
                dropArg = dropArg.substring(0, bar);
            }
        }
        String pending = RivalGuiApi.pendingMutualAcceptName(player);
        String pendingPlain = pending == null || pending.isBlank() ? "them" : UltraStyle.plain(pending);
        String dropShown = RivalGuiApi.displayPickerArg(player, dropArg);
        String dropPlain = dropShown == null || dropShown.isBlank() ? "-" : UltraStyle.plain(dropShown);
        UltraScreenSpec spec = sub("replace_confirm:" + payload)
                .line(UltraStyle.LABEL + "Confirm Mutual demotion")
                .labelValue("Accept", pendingPlain)
                .labelValue("Demote", dropPlain)
                .notice("This will demote " + dropPlain + " from Mutual to Declared. Continue?")
                .buttonsAfterLines();
        // Stack Continue above Cancel — side-by-side is a misclick demotion risk.
        if (dropArg != null && !dropArg.isBlank()) {
            spec.button(
                            "do:accept_replace:" + dropArg,
                            UltraStyle.danger("Continue"),
                            "danger")
                    .rowGap();
        }
        spec.footerBack(
                "page:" + UltraPickHelper.pageArg("pick_replace_mutual", returnPage), "Replace pick");
        spec.send(player);
    }

    /** Short action copy so pickers never imply a different tier outcome. */
    private static String pickExplainer(String doAction) {
        return switch (doAction == null ? "" : doAction) {
            case "rival_pick", "declare", "silent" ->
                    "Next you choose Silent, Declare, or Accept a mutual request.";
            case "challenge_pick" -> "Pick who to challenge. Next step chooses length.";
            case "spectate" -> "Only fights that are in progress.";
            default -> "Choose an online player.";
        };
    }

    private static void openAdmin(ServerPlayer player) {
        if (!StaffAccess.isStaff(player)) {
            openHome(player);
            return;
        }
        UltraScreenSpec spec = sub("admin")
                .line(UltraStyle.LABEL + "Staff rival tools")
                .buttonsAfterLines();
        spec.buttonLeft("do:admin:save", UltraStyle.confirm("Save data"), "confirm")
                .buttonRight("do:admin:refresh", UltraStyle.confirm("Reload data"), "confirm")
                .rowGap();
        spec.button("do:admin:status", UltraStyle.danger("Status"), "danger").rowGap();
        spec.footerBackHome("Rival Home");
        spec.send(player);
    }

    private static List<String> labeledArgs(ServerPlayer player, List<String> args) {
        List<String> out = new ArrayList<>();
        if (args == null) {
            return out;
        }
        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            String shown = RivalGuiApi.displayPickerArg(player, arg);
            if (shown == null || shown.isBlank()) {
                shown = "-";
            }
            out.add(arg + "|" + shown);
        }
        return out;
    }

    /** {@code pick_declare:2} → 2; {@code pick_declare} → 0; unrelated → -1. */
    private static int parsePickPage(String page) {
        if (page == null || page.isBlank()) {
            return -1;
        }
        String p = page.toLowerCase(Locale.ROOT);
        for (String kind : new String[] {"rival", "declare", "silent", "challenge", "spectate"}) {
            String prefix = "pick_" + kind;
            if (p.equals(prefix)) {
                return 0;
            }
            if (p.startsWith(prefix + ":")) {
                try {
                    return Integer.parseInt(p.substring(prefix.length() + 1).trim());
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return -1;
    }

    private static String pickKind(String page) {
        if (page == null || page.isBlank()) {
            return "";
        }
        String p = page.toLowerCase(Locale.ROOT);
        if (p.startsWith("pick_")) {
            String rest = p.substring("pick_".length());
            int colon = rest.indexOf(':');
            return colon < 0 ? rest : rest.substring(0, colon);
        }
        return "";
    }
}
