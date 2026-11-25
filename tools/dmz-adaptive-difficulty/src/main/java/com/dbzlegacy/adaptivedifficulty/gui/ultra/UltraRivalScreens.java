package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.gui.RivalGuiApi;
import com.dbzlegacy.adaptivedifficulty.rival.ProvingGrounds;
import com.dbzlegacy.adaptivedifficulty.rival.RivalConstants;
import com.dbzlegacy.adaptivedifficulty.rival.RivalLink;
import com.dbzlegacy.adaptivedifficulty.rival.RivalStatus;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/**
 * Rival system - CNPC parity via page: navigation and arg-bearing do: buttons.
 * Tabs: Home / My rivals (list + picker) / Challenge (W/L + duels). Home opens My rival stats (RP, tier, perk,
 * TP bonus, record, nemesis), the Leaderboard, Records and Rival TP chat.
 */
public final class UltraRivalScreens {
    /**
     * Online player pickers: two-up rows; page so large servers never truncate. 4.7.84 (X5): 10 a page (was 20), so a
     * full page is five name rows and stays under 300 px with its heading, pager and footer.
     */
    /** 4.7.85 (X5): 8 names a page (was 10), so a full picker page fits the 300 px height budget. */
    private static final int PICK_PER_PAGE = 8;
    // GuiKit 4.7.81 page sizes: one row per request / challenge, so a page stays short.
    private static final int RIVALS_PER_PAGE = 6;
    private static final int DECLARES_PER_PAGE = 4;
    private static final int CHALLENGES_PER_PAGE = 3;
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
        if (p.startsWith("remove_confirm:")) {
            openRemoveConfirm(player, raw.substring("remove_confirm:".length()).trim());
            return;
        }
        if (p.startsWith("records:")) {
            String section = p.substring("records:".length()).trim();
            // 4.7.84: Records - Stats and Records - Title merged into My rival stats; old links land there.
            if ("stats".equals(section) || "title".equals(section)) {
                openInfo(player);
            } else {
                openRecords(player, section);
            }
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
            // The Rival tab lists rivals and opens each profile; stale page:list links land there.
            case "list" -> openActions(player, 0);
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
                .fitMenu()
                .title("Rivals")
                .page(page)
                .activeTab(tab)
                // 4.7.84 (R-4): the second tab is "My rivals" so it no longer repeats the system name. Id unchanged.
                .tabs("home|Home", "actions|My rivals", "challenge|Challenge");
    }

    /** Subpage: no tab row — dual footer (Back + Main menu). */
    private static UltraScreenSpec sub(String page) {
        return UltraScreenSpec.of("rival")
                .fitMenu()
                .title("Rivals")
                .page(page);
    }


    private static void openHome(ServerPlayer player) {
        // 4.7.84 (R-1): Home says what Rival is and where to start.
        UltraScreenSpec spec = base("home", "home")
                .line(UltraStyle.LABEL + "Pick a rival, beat them in timed challenges, and earn bonus TP for it.")
                .line(UltraStyle.LABEL + "Add one on My rivals, then challenge them on the Challenge tab.")
                .buttonsAfterLines();
        // Rivals, their profiles, and Requests live on the My rivals tab.
        UltraPickHelper.placePageButtons(
                spec, "info|My rival stats", "top|Leaderboard", "records|Records", "settings|Rival TP chat");
        if (StaffAccess.isStaff(player)) {
            spec.buttonLeft("page:admin", UltraStyle.danger("Staff Admin"), UltraStyle.STYLE_DANGER).rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /**
     * 4.7.84 (R-2): the one stats page. It took in Records - Stats and Records - Title: tier and RP, perk, the real
     * TP bonus, record, streak, challenges, then Nemesis and Mutual rivals with the slots. About 9 lines, no buttons.
     */
    private static void openInfo(ServerPlayer player) {
        Map<String, String> ph = RivalGuiApi.placeholders(player);
        UltraScreenSpec spec = sub("info").line(UltraStyle.header("My rival stats"));
        for (String line : RivalGuiApi.myStatsLines(player)) {
            if (line != null && !line.isBlank()) {
                spec.line(line);
            }
        }
        spec.labelValue("Nemesis", RivalGuiApi.nemesisNames(player))
                .labelValue(
                        "Mutual rivals",
                        RivalGuiApi.mutualNames(player) + " (" + ph.getOrDefault("mutual", "0") + " of "
                                + ph.getOrDefault("mutual_max", "0") + " slots)")
                .buttonsAfterLines();
        spec.back();
        spec.send(player);
    }

    private static void openActions(ServerPlayer player, int page) {
        // GuiKit 4.7.81: rivals only (6 a page, normal style). Incoming declares are answered on the Requests
        // page, whose button says how many are waiting.
        List<String> rivals = rivalListCards(RivalGuiApi.currentRivalCards(player));
        int incoming = nonBlank(RivalGuiApi.pendingIncomingDeclareArgs(player)).size();
        int awaiting = RivalGuiApi.outgoingDeclareCount(player);
        int pages = UltraPickHelper.pageCount(rivals.size(), RIVALS_PER_PAGE);
        int pg = UltraPickHelper.clampPage(page, pages);
        UltraScreenSpec spec = base(UltraPickHelper.pageArg("actions", pg), "actions")
                .line(UltraStyle.LABEL + "Your rivals. Tap a name to see how you stack up, or tap Pick a player to add one.")
                .line(UltraStyle.LABEL + "Requests " + UltraStyle.VALUE + incoming + " incoming"
                        + UltraStyle.LABEL + ", " + UltraStyle.VALUE + awaiting + UltraStyle.LABEL
                        + " waiting on a reply");
        boolean needsMutualSlot = RivalGuiApi.needsMutualReplacePick(player);
        if (needsMutualSlot) {
            String pending = RivalGuiApi.pendingMutualAcceptName(player);
            String pendingPlain = pending == null || pending.isBlank() ? "them" : UltraStyle.plain(pending);
            spec.line(UltraStyle.LABEL + "Free a Mutual slot to accept " + pendingPlain + ".");
        }
        if (rivals.isEmpty()) {
            spec.line(UltraStyle.dim("No rivals on your list yet. Declare someone from Pick a player."));
        }
        UltraPickHelper.pageLabel(spec, pg, pages);
        spec.buttonsAfterLines();
        placeRivalListButtons(spec, player, UltraPickHelper.pageSlice(rivals, RIVALS_PER_PAGE, pg), pg);
        UltraPickHelper.placePrevNext(spec, "actions", pg, pages);
        UltraPickHelper.placePageButtons(
                spec, "pick_rival|Pick a player", "requests|" + (incoming > 0 ? "Requests (" + incoming + ")" : "Requests"));
        if (needsMutualSlot) {
            spec.button(
                            "page:pick_replace_mutual",
                            UltraStyle.danger("Drop a Mutual"),
                            UltraStyle.STYLE_DANGER)
                    .rowGap();
        }
        spec.back();
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

    /** One page of current rivals on the Rival tab — tap for profile / remove. A picker: normal style. */
    private static void placeRivalListButtons(
            UltraScreenSpec spec, ServerPlayer player, List<String> cards, int page) {
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
                if (placed % 2 == 0) {
                    spec.buttonLeft(action, labelText, UltraStyle.STYLE_NORMAL);
                } else {
                    spec.buttonRight(action, labelText, UltraStyle.STYLE_NORMAL);
                    spec.rowGap();
                }
                placed++;
            }
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
    }

    /**
     * One request row (GuiKit 4.7.81): the name on the Accept button, Decline beside it, so each pair says whose
     * it is. Minecraft names are at most 16 characters, so "Accept " + name fits the menu's two-column button.
     */
    private static void acceptDeclineRow(
            UltraScreenSpec spec, String acceptId, String declineId, String plain) {
        String who = plain == null || plain.isBlank() ? "them" : plain;
        spec.buttonLeft(acceptId, UltraStyle.confirm("Accept " + who), UltraStyle.STYLE_CONFIRM);
        spec.buttonRight(declineId, UltraStyle.danger("Decline"), UltraStyle.STYLE_DANGER).rowGap();
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
        // 4.7.56: full status words only; a name too long for both drops the status, never
        // abbreviates it to three letters. The profile page always shows the full status.
        String compact = name + " - " + status;
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
        spec.button("do:silent:" + arg, UltraStyle.confirm("Silent (private)"), UltraStyle.STYLE_CONFIRM)
                .rowGap();
        if (incoming) {
            spec.button(
                            "page:accept_confirm:" + arg,
                            UltraStyle.confirm("Accept their declare"),
                            UltraStyle.STYLE_CONFIRM)
                    .rowGap();
            spec.button("do:decline:" + arg, UltraStyle.danger("Decline their declare"), UltraStyle.STYLE_DANGER)
                    .rowGap();
        } else {
            spec.button("do:declare:" + arg, UltraStyle.confirm("Declare (send invite)"), UltraStyle.STYLE_CONFIRM)
                    .rowGap();
        }
        spec.back();
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
        // 1) Challenge info (ends with the "Pending challenges (N)" heading when any wait).
        for (String line : RivalGuiApi.challengeLines(player)) {
            if (line != null && !line.isBlank()) {
                spec.line(line);
            }
        }
        // 2) GuiKit 4.7.81: one row per pending challenge with the name on its button, 3 a page. The buttons
        // name the player, so the body no longer lists the names again.
        List<String> pageChallenges = UltraPickHelper.pageSlice(challenges, CHALLENGES_PER_PAGE, pg);
        UltraPickHelper.pageLabel(spec, pg, pages);
        spec.buttonsAfterLines();
        placePerChallengeButtons(spec, player, pageChallenges);
        UltraPickHelper.placePrevNext(spec, "challenge", pg, pages);
        // 3) Challenge actions. Records (Home) is the history page. 4.7.84 (X6): dim, never hidden, when there is
        // nobody to pick; the label says why.
        List<String> online = RivalGuiApi.onlinePlayerArgs(player);
        if (online == null || online.isEmpty()) {
            spec.lockedButton("Challenge - nobody on");
        } else {
            spec.buttonLeft("page:pick_challenge", "Send a challenge", UltraStyle.STYLE_NORMAL);
        }
        List<String> fights = RivalGuiApi.activeChallengeArgs(player);
        if (fights == null || fights.isEmpty()) {
            spec.lockedButtonRight("Spectate - no fights");
        } else {
            spec.buttonRight("page:pick_spectate", "Spectate", UltraStyle.STYLE_NORMAL);
        }
        spec.rowGap();
        // Normal style: it is a plain action, not the page's primary one.
        if (com.dbzlegacy.adaptivedifficulty.rival.RivalSpectator.isSpectating(player.m_20148_())) {
            spec.button("do:spectate_stop", "Stop spectate", UltraStyle.STYLE_NORMAL).rowGap();
        }
        spec.back();
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

    /**
     * One row per pending challenge: incoming {@code [Accept <name> | Decline]}, outgoing
     * {@code [To <name> - N min (dim) | Cancel]}.
     */
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
                acceptDeclineRow(
                        spec,
                        "do:challenge_accept:" + picker,
                        "do:challenge_decline:" + picker,
                        plain);
            } else {
                String mins = p.length > 5 && p[5] != null && !p[5].isBlank() ? p[5].trim() : "";
                spec.lockedButton("To " + plain + (mins.isEmpty() ? "" : " - " + mins + " min"));
                spec.buttonRight(
                                "do:challenge_cancel:" + picker,
                                UltraStyle.danger("Cancel"),
                                UltraStyle.STYLE_DANGER)
                        .rowGap();
            }
        }
    }

    /**
     * Profile link arg: page 2+ rides along as {@code |N} so the profile's Back returns to that
     * page of the Rival tab. Names and {@code uuid:} args never contain {@code |}.
     */
    private static String profileArg(String arg, int page) {
        return page > 0 ? arg + "|" + page : arg;
    }

    /** Profile payload suffix for the "you" model view (4.7.48): {@code list_detail:<arg>[|<page>]|you}. */
    private static final String VIEW_YOU = "|you";

    private static void openListDetail(ServerPlayer player, String payload) {
        String arg = payload == null ? "" : payload;
        boolean showYou = arg.endsWith(VIEW_YOU);
        if (showYou) {
            arg = arg.substring(0, arg.length() - VIEW_YOU.length());
        }
        String profile = arg;
        int returnPage = 0;
        int bar = arg.lastIndexOf('|');
        if (bar > 0) {
            returnPage = UltraPickHelper.pageIndex("p:" + arg.substring(bar + 1));
            if (returnPage > 0) {
                arg = arg.substring(0, bar);
            }
        }
        String who = RivalGuiApi.displayPickerArg(player, arg);
        boolean stillRival = false;
        if (arg != null && !arg.isBlank()) {
            for (String cur : RivalGuiApi.currentRivalArgs(player)) {
                if (arg.equalsIgnoreCase(cur)) {
                    stillRival = true;
                    break;
                }
            }
        }
        UltraScreenSpec spec = sub("list_detail:" + payload);
        // Model (4.7.48): the rival when they are online, else (or on "Show you") you, as your
        // race in the form picked on Preview forms.
        ServerPlayer rival = arg == null || arg.isBlank() ? null : RivalGuiApi.resolveOnline(player, arg);
        boolean rivalShown = rival != null && !showYou;
        ModelPreview.Form yours = rivalShown ? null : ModelPreview.selectedForm(player);
        ModelPreview.Box box = ModelPreview.STANDARD;
        // 4.7.49: scale fit to the form shown, so a rival or "you" in a giant form fits the box.
        float scale = rivalShown
                ? ModelPreview.wornScale(rival, ModelPreview.menuBox(box), ModelPreview.BASE_SCALE)
                : ModelPreview.formScale(yours, ModelPreview.menuBox(box), ModelPreview.BASE_SCALE);
        spec.playerModel(box.x(), box.y(), box.w(), box.h(), scale, ModelPreview.rotate(player),
                rivalShown ? ModelPreview.rivalFigure(player, rival) : ModelPreview.formFigure(player, yours));
        spec.line(UltraStyle.LABEL + "Rival profile")
                .labelValue("Target", who == null || who.isBlank() ? "-" : who);
        if (rivalShown) {
            spec.labelValue("Model", UltraStyle.plain(rival.m_7755_().getString()));
        } else if (yours != null) {
            spec.labelValue("Model", "You, " + yours.label());
            if (rival == null && !showYou) {
                spec.line(UltraStyle.dim("They are offline, so the model shows you."));
            }
        }
        if (stillRival) {
            profileStatus(spec, RivalGuiApi.rivalLinkFor(player, arg));
        } else if (arg != null && !arg.isBlank()) {
            // Body lines go before buttonsAfterLines, which places the buttons under them.
            spec.line(UltraStyle.dim("Not on your rival list."));
        }
        spec.buttonsAfterLines();
        ModelPreview.rotateButton(spec, player, "list_detail:" + payload);
        if (stillRival) {
            // Silent can be upgraded from the profile. Declared and above already sent that step.
            if ("silent".equalsIgnoreCase(RivalGuiApi.rivalStatusLabel(player, arg))) {
                if (RivalGuiApi.hasIncomingDeclareFrom(player, arg)) {
                    // They already declared you - a bare Declare would silently
                    // become Accept. Route to the confirm page that says so.
                    spec.button(
                                    "page:accept_confirm:" + arg,
                                    UltraStyle.confirm("Accept their declare"),
                                    UltraStyle.STYLE_CONFIRM)
                            .rowGap();
                } else {
                    spec.button("do:declare:" + arg, UltraStyle.confirm("Declare"), UltraStyle.STYLE_CONFIRM)
                            .rowGap();
                }
            }
            // Ask first (4.7.31): the ask page says what removal does before anything changes.
            spec.button("page:remove_confirm:" + profileArg(arg, returnPage), UltraStyle.danger("Remove rival"), UltraStyle.STYLE_DANGER)
                    .rowGap();
        }
        if (rival != null) {
            spec.button("page:list_detail:" + profile + (showYou ? "" : VIEW_YOU),
                            showYou ? "Show your rival" : "Show you", UltraStyle.STYLE_NORMAL)
                    .rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /**
     * Status block on the rival profile: the same link fields the rival list renders (status,
     * RP tier, RP, record, deaths toward Nemesis, Proving Grounds). Read only.
     */
    private static void profileStatus(UltraScreenSpec spec, RivalLink link) {
        if (link == null) {
            return;
        }
        RivalStatus st = link.status();
        RivalConstants.RpTier tier = RivalConstants.tierFor(link.points);
        spec.labelValue("Status", st.label())
                .labelValue("Rival tier", tier.name())
                .labelValue("RP", String.valueOf((int) link.points))
                .labelValue("Record", count(link.wins, "win", "wins") + ", "
                        + count(link.losses, "loss", "losses") + ", "
                        + count(link.draws, "draw", "draws"));
        if (link.mutual && st != RivalStatus.NEMESIS) {
            spec.labelValue("Challenge deaths",
                    link.deathLosses + "/" + RivalConstants.NEMESIS_DEATH_LOSSES + " toward Nemesis");
        }
        List<String> grounds = ProvingGrounds.listLines(link);
        if (grounds.isEmpty()) {
            spec.line(UltraStyle.dim("Win a challenge against this rival to claim Proving Grounds."));
        } else {
            for (String line : grounds) {
                spec.line(line);
            }
        }
    }

    private static String count(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    private static void openTop(ServerPlayer player) {
        // The board's first line is its "Rival Leaderboard" heading; the page adds none of its own.
        UltraScreenSpec spec = sub("top");
        for (String line : RivalGuiApi.topLines(player)) {
            if (line != null && !line.isBlank()) {
                spec.line(line);
            }
        }
        spec.buttonsAfterLines();
        spec.back();
        spec.send(player);
    }

    /**
     * 4.7.84 (R-2): wrapped body lines a Records section shows under its heading. 300 px less 76 chrome, three button
     * rows and the footer (88) leaves 136 px: the heading plus 9 lines of 13 px.
     */
    private static final int RECORD_LINES = 9;

    /**
     * Records (4.7.84, R-2): six sections, three button rows. Stats and Title merged into My rival stats. A section's
     * own heading ("Rival Journal", "Weekly Rival Quests") is dropped under the page's "Records - X" heading; the
     * season heading carries the season name, so it stands in for the page heading.
     */
    private static void openRecords(ServerPlayer player, String sub) {
        String tab = sub == null || sub.isBlank() ? "history" : sub.toLowerCase(Locale.ROOT);
        UltraScreenSpec spec = sub("records:" + tab);
        List<String> lines = switch (tab) {
            case "history" -> RivalGuiApi.recordsHistoryLines(player);
            case "season" -> RivalGuiApi.seasonLines(player);
            case "quests" -> RivalGuiApi.questLines(player);
            case "achievements" -> RivalGuiApi.achievementLines(player);
            case "hof" -> RivalGuiApi.hofLines(player);
            case "journal" -> RivalGuiApi.journalLines(player);
            default -> RivalGuiApi.linesForPage(player, "records:" + tab);
        };
        List<String> body = new ArrayList<>(nonBlank(lines));
        boolean sourceHeading = "season".equals(tab) || "quests".equals(tab) || "achievements".equals(tab)
                || "journal".equals(tab);
        // A real section always has its heading plus at least one line; the "disabled" notice is one line alone.
        if (sourceHeading && body.size() > 1) {
            String heading = body.remove(0);
            spec.line("season".equals(tab)
                    ? UltraStyle.header("Records - " + UltraStyle.plain(heading).replace("Rival Season", "Season"))
                    : UltraStyle.header("Records - " + recordsTitle(tab)));
        } else {
            spec.line(UltraStyle.header("Records - " + recordsTitle(tab)));
        }
        // Count wrapped lines, not entries: a long history row takes two. Keep one line free for "And N more".
        int fitPx = UltraStyle.bodyTextFitPx(UltraStyle.MENU_W, -1);
        int used = 0;
        int shown = 0;
        for (String line : body) {
            int h = Math.max(1, UltraStyle.wrapBody(UltraStyle.wireText(line), fitPx).size());
            boolean last = shown == body.size() - 1;
            if (used + h > (last ? RECORD_LINES : RECORD_LINES - 1)) {
                break;
            }
            spec.line(line);
            used += h;
            shown++;
        }
        if (shown < body.size()) {
            int more = body.size() - shown;
            spec.line(UltraStyle.dim("And " + more + " more, left out so the page fits."));
        }
        spec.buttonsAfterLines();
        UltraPickHelper.placePageButtons(
                spec,
                "records:history|History",
                "records:season|Season",
                "records:quests|Quests",
                "records:achievements|Achievements",
                "records:hof|Hall of fame",
                "records:journal|Journal");
        spec.back();
        spec.send(player);
    }

    /** Records sub-page title, worded like its button (never the page id). */
    private static String recordsTitle(String tab) {
        return switch (tab == null ? "" : tab) {
            case "season" -> "Season";
            case "quests" -> "Quests";
            case "achievements" -> "Achievements";
            case "hof" -> "Hall of fame";
            case "journal" -> "Journal";
            default -> "History";
        };
    }

    /**
     * Requests (GuiKit 4.7.81): one row per request. Incoming: {@code [Accept <name> | Decline]}. Outgoing: a dim
     * {@code Waiting on <name>} row (there is no withdraw action for a declare; the rival profile's Remove ends it).
     * Rows and their lines come from one paged list, so a page never shows a request without its buttons.
     */
    private static void openRequests(ServerPlayer player, int page) {
        // "IN\t<accept/decline arg>" for incoming declares, then "OUT\t<name>" for declares waiting on a reply.
        List<String> rows = new ArrayList<>();
        for (String arg : nonBlank(RivalGuiApi.pendingIncomingDeclareArgs(player))) {
            rows.add("IN\t" + arg);
        }
        List<String> cards = RivalGuiApi.pendingInviteCards(player);
        if (cards != null) {
            for (String card : cards) {
                if (RivalGuiApi.pendingInviteCardOutgoing(card)) {
                    String[] p = card.split("\t", -1);
                    String name = p.length > 1 && p[1] != null && !p[1].isBlank() ? p[1].trim() : "";
                    if (!name.isBlank()) {
                        rows.add("OUT\t" + name);
                    }
                }
            }
        }
        int pages = UltraPickHelper.pageCount(rows.size(), DECLARES_PER_PAGE);
        int pg = UltraPickHelper.clampPage(page, pages);
        UltraScreenSpec spec = sub(UltraPickHelper.pageArg("requests", pg))
                .line(UltraStyle.LABEL + "Accept to become rivals both ways. Decline and they stay your one-way rival.");
        if (rows.isEmpty()) {
            spec.line(UltraStyle.dim("No rival requests right now. Wait for an invite, or declare someone yourself."));
        }
        UltraPickHelper.pageLabel(spec, pg, pages);
        spec.buttonsAfterLines();
        for (String row : UltraPickHelper.pageSlice(rows, DECLARES_PER_PAGE, pg)) {
            int tab = row.indexOf('\t');
            String kind = row.substring(0, tab);
            String value = row.substring(tab + 1);
            if ("OUT".equals(kind)) {
                spec.lockedButton("Waiting on " + UltraStyle.plain(value)).rowGap();
                continue;
            }
            String shown = RivalGuiApi.displayPickerArg(player, value);
            if (shown == null || shown.isBlank()) {
                shown = "-";
            }
            acceptDeclineRow(spec, "do:accept:" + value, "do:decline:" + value, UltraStyle.plain(shown));
        }
        UltraPickHelper.placePrevNext(spec, "requests", pg, pages);
        spec.back();
        spec.send(player);
    }

    private static void openSettings(ServerPlayer player) {
        Map<String, String> ph = RivalGuiApi.placeholders(player);
        boolean tpOn = "true".equalsIgnoreCase(ph.getOrDefault("tpMsg", ph.getOrDefault("tp_msg", "false")));
        boolean instinctOn = "true".equalsIgnoreCase(ph.getOrDefault("instinct", "false"));
        UltraScreenSpec spec = sub("settings")
                // 4.7.77: each state shows once, on its toggle; the lines say what the toggles do. An OFF toggle is drawn
                // normal (dim is the disabled look, and the button still works).
                // 4.7.84 (X7): one name per system for its TP chat setting: "Rival TP chat" here, "Spar TP chat" on
                // Sparring. Each line says what its toggle does.
                .line(UltraStyle.header("Rival TP chat"))
                .line(UltraStyle.LABEL + "Rival TP chat: a chat line when being near or fighting a rival in the world pays you TP."
                        + " Challenge pay always shows.")
                .line(UltraStyle.LABEL + "Instinct: an alert when a mutual rival or your Nemesis is near.")
                .buttonsAfterLines();
        spec.buttonLeft(
                        "do:tpmsg:toggle",
                        tpOn ? UltraStyle.confirm("Rival TP chat - ON") : "Rival TP chat - OFF",
                        tpOn ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_NORMAL)
                .buttonRight(
                        "do:instinct",
                        instinctOn
                                ? UltraStyle.confirm("Instinct - ON")
                                : "Instinct - OFF",
                        instinctOn ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_NORMAL)
                .rowGap();
        spec.back();
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
                .line(UltraStyle.header(heading))
                .line(UltraStyle.LABEL + pickExplainer(doAction))
                .labelValue("spectate".equals(doAction) ? "In a fight" : "Players", String.valueOf(total));
        if (total == 0) {
            spec.line(UltraStyle.dim(emptyCopy));
        }
        UltraPickHelper.pageLabel(spec, pg, pages);
        spec.buttonsAfterLines();
        if ("spectate".equals(doAction)) {
            // Spectate is a plain do: picker, so it uses the shared paged helper (same 20 a page,
            // same do:spectate:<arg> buttons, same labels). Its empty line is shown above.
            List<String> choices = new java.util.ArrayList<>();
            for (String arg : nonBlank(args)) {
                String shown = RivalGuiApi.displayPickerArg(player, arg);
                choices.add(arg + "|" + UltraStyle.plain(shown == null || shown.isBlank() ? "-" : shown));
            }
            UltraPickHelper.placeNameButtons(spec, doAction, choices, PICK_PER_PAGE, pageBase, pg, null);
            spec.back();
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
            // A picker: normal style, plain name (confirm is kept for a page's one primary action).
            if ("challenge_pick".equals(doAction)) {
                action = "page:challenge_time:" + arg;
            } else if ("rival_pick".equals(doAction)
                    || "declare".equals(doAction)
                    || "silent".equals(doAction)) {
                // Consolidated flow: pick person first, then Silent / Declare / Accept.
                action = "page:rival_type:" + arg;
            } else {
                action = "do:" + doAction + ":" + arg;
            }
            label = plain;
            style = UltraStyle.STYLE_NORMAL;
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
        spec.back();
        spec.send(player);
    }

    private static void openChallengeTime(ServerPlayer player, String target) {
        String who = RivalGuiApi.displayPickerArg(player, target);
        UltraScreenSpec spec = sub("challenge_time:" + target)
                .line(UltraStyle.header("Challenge length"))
                .labelValue("Rival", who == null || who.isBlank() ? "-" : who)
                .line(UltraStyle.LABEL + "Challenge " + (who == null || who.isBlank() ? "this rival" : UltraStyle.plain(who))
                        + " to a spar. Pick how many minutes - the challenge is sent right away.")
                .buttonsAfterLines();
        if (target != null && !target.isBlank()) {
            // Ten length choices: a picker, so normal style (the body says a tap sends the challenge).
            for (int min = 1; min <= 10; min += 2) {
                spec.buttonLeft(
                        "do:challenge_send:" + target + "@" + min,
                        min + " min",
                        UltraStyle.STYLE_NORMAL);
                if (min + 1 <= 10) {
                    spec.buttonRight(
                            "do:challenge_send:" + target + "@" + (min + 1),
                            (min + 1) + " min",
                            UltraStyle.STYLE_NORMAL);
                }
                spec.rowGap();
            }
        }
        spec.back();
        spec.send(player);
    }

    /**
     * Ask page before Remove rival (4.7.31). Removal is reversible: the remover's side moves to
     * History and a Mutual / Declared partner keeps them as Declared, so either can declare again.
     * Silent / Pending links only move to History. {@code payload} is the profile's {@code arg|N}.
     */
    private static void openRemoveConfirm(ServerPlayer player, String payload) {
        String arg = payload == null ? "" : payload;
        int bar = arg.lastIndexOf('|');
        if (bar > 0 && UltraPickHelper.pageIndex("p:" + arg.substring(bar + 1)) > 0) {
            arg = arg.substring(0, bar);
        }
        String who = RivalGuiApi.displayPickerArg(player, arg);
        String plain = who == null || who.isBlank() ? "-" : UltraStyle.plain(who);
        String status = RivalGuiApi.rivalStatusLabel(player, arg);
        boolean oneSided = "silent".equalsIgnoreCase(status) || "pending".equalsIgnoreCase(status);
        // 4.7.33: a one-way Declared (they never declared back) is deleted on both sides, not dropped.
        boolean oneWayDeclare = !oneSided && RivalGuiApi.isOneWayDeclare(player, arg);
        String notice;
        if (oneSided) {
            notice = "End your rivalry with " + plain + "? It moves to History - you can declare again any time.";
        } else if (oneWayDeclare) {
            notice = "End your rivalry with " + plain + "? It moves to History and you come off their rival list"
                    + " - you can declare again any time.";
        } else {
            notice = "End your rivalry with " + plain + "? They drop to Declared - you can declare again any time.";
        }
        UltraScreenSpec spec = sub("remove_confirm:" + payload)
                .line(UltraStyle.header("End rivalry"))
                .labelValue("Rival", plain)
                .notice(notice)
                .buttonsAfterLines();
        if (!arg.isBlank()) {
            spec.button("do:remove:" + arg, UltraStyle.danger("End rivalry"), UltraStyle.STYLE_DANGER)
                    .rowGap();
        }
        spec.back();
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
            spec.button("do:accept:" + arg, UltraStyle.confirm("Accept mutual rivalry"), UltraStyle.STYLE_CONFIRM)
                    .rowGap();
        }
        spec.back();
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
        if (choices.isEmpty()) {
            spec.line(UltraStyle.dim("No Mutual rivals to drop right now. Mutual rivals appear here once you have one."));
        }
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
                spec.buttonLeft(action, styled, UltraStyle.STYLE_DANGER);
            } else {
                spec.buttonRight(action, styled, UltraStyle.STYLE_DANGER);
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        UltraPickHelper.placePrevNext(spec, "pick_replace_mutual", pg, pages);
        // Escape hatch — do not trap the player on this page. A live button: normal, not the dim locked look.
        spec.button("do:cancel_mutual_replace", "Decline instead", UltraStyle.STYLE_NORMAL)
                .rowGap();
        spec.back();
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
                .line(UltraStyle.header("Confirm Mutual demotion"))
                .labelValue("Accept", pendingPlain)
                .labelValue("Demote", dropPlain)
                .notice("This demotes " + dropPlain + " from Mutual to Declared so you can accept " + pendingPlain + ".")
                .buttonsAfterLines();
        // Stack the action above Back - side-by-side is a misclick demotion risk. The button
        // names the demotion (4.7.56); a long pair of names falls back to "Demote X".
        if (dropArg != null && !dropArg.isBlank()) {
            String act = "Demote " + dropPlain + " and accept " + pendingPlain;
            if (!UltraStyle.fitsButton(act)) {
                act = "Demote " + dropPlain;
            }
            spec.button(
                            "do:accept_replace:" + dropArg,
                            UltraStyle.danger(act),
                            UltraStyle.STYLE_DANGER)
                    .rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /** Short action copy so pickers never imply a different tier outcome. */
    private static String pickExplainer(String doAction) {
        return switch (doAction == null ? "" : doAction) {
            case "rival_pick", "declare", "silent" ->
                    "Next: keep it private, or ask them to be rivals back.";
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
                .line(UltraStyle.header("Staff rival tools"))
                .buttonsAfterLines();
        spec.buttonLeft("do:admin:save", UltraStyle.confirm("Save data"), UltraStyle.STYLE_CONFIRM)
                .buttonRight("do:admin:refresh", UltraStyle.confirm("Reload data"), UltraStyle.STYLE_CONFIRM)
                .rowGap();
        spec.button("do:admin:status", UltraStyle.LABEL + "Status", UltraStyle.STYLE_NORMAL).rowGap();
        spec.back();
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
