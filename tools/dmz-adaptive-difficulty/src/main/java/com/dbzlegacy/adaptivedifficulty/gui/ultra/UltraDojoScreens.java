package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoCatalog;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoService;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.Dojo;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoWar;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld;
import com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * LM 4.7.68 Sparring pages for dojos and the Spar Board: the Spar Board tab (posts, challenges, the ranked gate,
 * wagers, level ranges, forfeit, watching), the fight result with a rematch, and the Dojo tab (found / close /
 * reopen, Dojo Points and clout, upgrades with the layout-map preview and "Visit dojo", style and bonus, elites,
 * wars with the live bracket, the dojo leaderboard). Buttons for what the player can't do yet are dim and inert,
 * never hidden, and the page says why.
 *
 * <p>4.7.84: every page fits about 300 px of panel height (no scrolling). The Spar Board shows 4 posts a page with
 * a pager (posts aimed at you first) and posting moved to its own page; Dojo Home has 5 actions with the floor plan
 * beside the stats; one Members page (6 a page) replaces "Roster and bonds" and "Members and elites"; the dojo list
 * opens a dojo profile with "Ask to join"; the Wars tab shows a war's sign-up with its countdown.
 */
public final class UltraDojoScreens {
    private UltraDojoScreens() {}

    static final String TAB_BOARD = "sparboard";
    /** 4.7.84 (SP-2): posts a board page shows; a pager reaches the rest. */
    static final int BOARD_SHOWN = 4;
    /** 4.7.84 (SP-2): the compose page (kind, wager, range, looking for, Post). */
    static final String PAGE_POST = "board_post";

    static UltraScreenSpec base(String page, String tab, String notice) {
        UltraScreenSpec spec = UltraScreenSpec.of("spar")
                .fitMenu()
                .title("Sparring")
                .page(page)
                .activeTab(tab)
                .tabs("home|Home", "leaderboard|Leaderboard", TAB_BOARD + "|Spar Board");
        if (notice != null && !notice.isBlank()) {
            spec.notice(notice);
        }
        return spec;
    }

    // 4.7.78: Dojo is its own system (screen dojo): Home | Upgrades | Wars.
    static final String DOJO = "dojo";
    static final String TAB_DOJO_HOME = "home";
    static final String TAB_UPGRADES = "dojo_upgrades";
    static final String TAB_WARS = "dojo_wars";
    static final String PAGE_SETTINGS = "dojo_settings";
    static final String PAGE_MEMBERS = "dojo_members";
    static final String PAGE_MEMBER = "dojo_member:";
    static final String PAGE_VIEW = "dojo_view:";
    static final String PAGE_JOIN_ASK = "dojo_join_ask:";
    static final String PAGE_STYLE_PICK = "dojo_style_pick";
    static final String ACT_JOIN = "dojo_join";
    /** 4.7.88: "Your dojos", the picker for a player in more than one dojo. */
    static final String PAGE_MINE = "dojo_mine";
    /** 4.7.88: View on "Your dojos": that dojo's Home (and every Dojo page after it) shows and acts on that dojo. */
    static final String ACT_VIEW = "dojo_switch";
    /** 4.7.84 (DJ-2): members a Members page shows. */
    static final int MEMBERS_SHOWN = 6;

    static UltraScreenSpec dojoBase(String page, String tab, String notice) {
        UltraScreenSpec spec = UltraScreenSpec.of("dojo")
                .fitMenu()
                .title("Dojo")
                .page(page)
                .activeTab(tab)
                .tabs(TAB_DOJO_HOME + "|Home", TAB_UPGRADES + "|Upgrades", TAB_WARS + "|Wars");
        if (notice != null && !notice.isBlank()) {
            spec.notice(notice);
        }
        return spec;
    }

    static UltraScreenSpec dojoSub(String page, String notice) {
        UltraScreenSpec spec = UltraScreenSpec.of("dojo").fitMenu().title("Dojo").page(page);
        if (notice != null && !notice.isBlank()) {
            spec.notice(notice);
        }
        return spec;
    }

    /**
     * 4.7.78: the Dojo system's pages (screen {@code dojo}). Home, Upgrades and Wars are its tabs; every other dojo page
     * (style, members, wars, the dojo list, founding) opens here too. Pages this class does not own go to the Sparring
     * router, whose dojo pages (previous members, training bonds) also draw on the Dojo screen.
     */
    public static void openSystem(ServerPlayer player, String page) {
        String raw = page == null || page.isBlank() ? "main" : page.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.equals("main")) {
            // 4.7.88: opening the Dojo system (hub tile, open:dojo) asks which dojo first when there are two.
            openDojoEntry(player);
        } else if (lower.equals(TAB_DOJO_HOME) || lower.equals("dojo") || lower.equals("dojo_home")) {
            openDojo(player, null);
        } else if (lower.equals("upgrades")) {
            openUpgrades(player, null);
        } else if (lower.equals("wars")) {
            openWars(player, null);
        } else if (lower.equals("dojo_roster")) {
            // 4.7.84 (DJ-2): "Roster and bonds" became the one Members page.
            openMembers(player, 0, null);
        } else if (!open(player, raw, null)) {
            UltraSparScreens.open(player, raw);
        }
    }

    static UltraScreenSpec sub(String page, String notice) {
        UltraScreenSpec spec = UltraScreenSpec.of("spar").fitMenu().title("Sparring").page(page);
        if (notice != null && !notice.isBlank()) {
            spec.notice(notice);
        }
        return spec;
    }

    /** Route a Sparring page this class owns. True when it opened one. */
    public static boolean open(ServerPlayer player, String raw, String notice) {
        String p = raw == null ? "" : raw.trim();
        String lower = p.toLowerCase(Locale.ROOT);
        String base = UltraPickHelper.pageBase(lower);
        if (base.equals(TAB_BOARD) || base.equals("board")) {
            openBoard(player, UltraPickHelper.pageIndex(lower), notice);
        } else if (lower.equals(PAGE_POST)) {
            openBoardPost(player, notice);
        } else if (lower.equals("result")) {
            openResult(player, notice);
        } else if (lower.equals(PAGE_WAGER)) {
            openWager(player, notice);
        } else if (lower.equals(PAGE_WAGER_COINS)) {
            openWagerAmounts(player, "COINS", notice);
        } else if (lower.equals(PAGE_WAGER_POINTS)) {
            openWagerAmounts(player, "POINTS", notice);
        } else if (lower.startsWith(PAGE_ACCEPT)) {
            openAcceptAsk(player, p.substring(PAGE_ACCEPT.length()), notice);
        } else if (lower.startsWith("pick_challenge")) {
            openChallengePick(player, UltraPickHelper.pageIndex(lower), notice);
        } else if (lower.equals("watch")) {
            openWatch(player, notice);
        } else if (lower.equals("dojo") || lower.equals("dojo_home")) {
            openDojo(player, notice);
        } else if (lower.equals(PAGE_MINE)) {
            openYourDojos(player, notice);
        } else if (DojoFounding.owns(lower)) {
            DojoFounding.open(player, raw, notice);
        } else if (lower.equals(PAGE_SETTINGS)) {
            openSettings(player, notice);
        } else if (lower.equals("dojo_upgrades")) {
            openUpgrades(player, notice);
        } else if (lower.startsWith("dojo_up:")) {
            openUpgrade(player, p.substring("dojo_up:".length()), notice);
        } else if (lower.equals("dojo_style")) {
            openStyle(player, notice);
        } else if (lower.equals(PAGE_STYLE_PICK)) {
            openStylePick(player, notice);
        } else if (lower.startsWith("dojo_style_ask:")) {
            openStyleAsk(player, p.substring("dojo_style_ask:".length()));
        } else if (lower.startsWith("dojo_bonus_ask:")) {
            openBonusAsk(player, p.substring("dojo_bonus_ask:".length()));
        } else if (base.equals(PAGE_MEMBERS) || lower.equals("dojo_elites") || lower.equals("dojo_roster")) {
            // 4.7.84 (DJ-2): one Members page; the old elites and roster page ids open it too.
            openMembers(player, UltraPickHelper.pageIndex(lower), notice);
        } else if (lower.startsWith(PAGE_MEMBER)) {
            openMember(player, p.substring(PAGE_MEMBER.length()), notice);
        } else if (lower.equals("dojo_wars") || lower.equals("wars") || lower.equals("dojo_war")) {
            openWars(player, notice);
        } else if (lower.startsWith("dojo_war_pick")) {
            openWarPick(player, UltraPickHelper.pageIndex(lower), notice);
        } else if (lower.startsWith("dojo_war_fmt:")) {
            openWarFormat(player, p.substring("dojo_war_fmt:".length()), notice);
        } else if (lower.startsWith("dojo_war_view:")) {
            openWarView(player, p.substring("dojo_war_view:".length()), notice);
        } else if (lower.startsWith(PAGE_VIEW)) {
            openDojoView(player, p.substring(PAGE_VIEW.length()), notice);
        } else if (lower.startsWith(PAGE_JOIN_ASK)) {
            openJoinAsk(player, p.substring(PAGE_JOIN_ASK.length()), notice);
        } else if (lower.startsWith("dojo_list_wars")) {
            openList(player, UltraPickHelper.pageIndex(lower), notice, true);
        } else if (lower.startsWith("dojo_list")) {
            openList(player, UltraPickHelper.pageIndex(lower), notice, false);
        } else if (lower.startsWith("dojo_top_") || lower.equals("dojo_rank") || lower.equals("dojo_rankings")
                || lower.equals("dojo_hof") || lower.equals("chosen") || lower.equals("hall_of_chosen")
                || lower.equals("dojo_war_hof")) {
            // 4.7.77: the pre-4.7.68 boards (reputation, war wins, Hall of Chosen) read data the current dojos no
            // longer update; their links open the live leaderboard instead.
            openList(player, 0, notice, lower.equals("dojo_top_wars"));
        } else if (lower.equals("dojo_close")) {
            openCloseConfirm(player);
        } else {
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ Spar Board

    /** 4.7.84 (P2-1c): the in-fight line, the same rule the wager pages state. */
    static final String FORFEIT_LINE = "Forfeiting hands the win and any wager to your opponent.";
    /** 4.7.84 (P2-1c): when stakes come back. */
    static final String REFUND_LINE = "Stakes come back only on a disconnect, a dead-even draw or a server restart.";

    static void openBoard(ServerPlayer player, String notice) {
        openBoard(player, 0, notice);
    }

    /**
     * 4.7.84 (SP-2): the board is the listings. Your post (one line), the ranked progress, 4 posts a page (posts aimed
     * at you first, then the ones you can accept), their Accept buttons in the same order, a pager, then Post a spar,
     * Challenge a player, Watch spars and Last fight.
     */
    static void openBoard(ServerPlayer player, int page, String notice) {
        UUID me = player.m_20148_();
        Dojo mine = DojoService.dojoOf(player);
        BoardSpar.Bout bout = BoardSpar.boutOf(me);
        if (bout != null) {
            UltraScreenSpec spec = base(TAB_BOARD, TAB_BOARD, notice);
            String opp = me.equals(bout.a) ? bout.nameB : bout.nameA;
            Dojo host = DojoService.byMaster(bout.host);
            spec.line(UltraStyle.header("You're fighting"))
                    .line(UltraStyle.labelValue("Opponent", opp))
                    .line(UltraStyle.labelValue("Where", (host == null ? "a dojo" : host.name) + ", ring "
                            + (bout.arena + 1)))
                    .line(UltraStyle.labelValue("Time left", BoardSpar.clock(DojoCatalog.BOUT_TIMEOUT_MS
                            - (System.currentTimeMillis() - bout.startAt))))
                    .line(UltraStyle.LABEL + FORFEIT_LINE)
                    .buttonsAfterLines();
            spec.button("do:" + BoardSpar.ACT_FORFEIT, UltraStyle.danger("Forfeit this spar"), UltraStyle.STYLE_DANGER)
                    .rowGap();
            spec.back();
            spec.send(player);
            return;
        }
        if (mine == null) {
            UltraScreenSpec spec = base(TAB_BOARD, TAB_BOARD, notice);
            spec.line(UltraStyle.header("Spar Board"))
                    .line(UltraStyle.VALUE + "You need a dojo to spar. Spars happen in a dojo's ring.")
                    .line(UltraStyle.LABEL + "Ask to join one from the dojo list, or found your own (free).")
                    .buttonsAfterLines();
            // 4.7.80: founding lives only in the Dojo section; the board links there.
            spec.buttonLeft("page:dojo_list", "Find a dojo", UltraStyle.STYLE_NORMAL);
            spec.buttonRight("go:dojo|home", "Open the Dojo", UltraStyle.STYLE_NORMAL).rowGap();
            spec.lockedButton("Post a spar").rowGap();
            spec.back();
            spec.send(player);
            return;
        }
        int unranked = BoardSpar.unranked(me);
        boolean unlocked = BoardSpar.rankedUnlocked(me);
        BoardSpar.Post mineUp = BoardSpar.postOf(me);
        List<BoardSpar.Post> posts = BoardSpar.board(player);
        posts.removeIf(p -> p.poster().equals(me));
        int pages = UltraPickHelper.pageCount(posts.size(), BOARD_SHOWN);
        int pg = UltraPickHelper.clampPage(page, pages);
        UltraScreenSpec spec = base(UltraPickHelper.pageArg(TAB_BOARD, pg), TAB_BOARD, notice);
        List<BoardSpar.Post> shown = UltraPickHelper.pageSlice(posts, BOARD_SHOWN, pg);
        // 4.7.85: the page fits 285 px with a notice and 4 posts: the heading, then one line per post. Your post and
        // the ranked count get their own line only when there is room; otherwise a short form joins the heading.
        // "You have no post up" and "Ranked spars are unlocked" are gone (the Post a spar button says both).
        String status = null;
        String statusShort = null;
        if (mineUp != null) {
            String left = BoardSpar.clock(BoardSpar.POST_MS - (System.currentTimeMillis() - mineUp.at()));
            status = UltraStyle.labelValue("Your post", (mineUp.ranked() ? "ranked" : "friendly") + ", "
                    + BoardSpar.wagerText(mineUp.wagerKind(), mineUp.wager()) + ", wants " + mineUp.want().label
                    + (mineUp.target() != null ? ", for " + mineUp.targetName() : "") + " - " + left + " left");
            statusShort = "your post: " + left + " left";
        } else if (!unlocked) {
            int toGo = DojoCatalog.RANKED_GATE - unranked;
            status = UltraStyle.LABEL + "Ranked unlocks after " + DojoCatalog.RANKED_GATE
                    + " friendly spars: " + unranked + " done, " + toGo + " to go.";
            statusShort = "ranked in " + toGo + " more spars";
        }
        String heading = "On the board (" + posts.size() + ")" + (pages > 1 ? ", page " + (pg + 1) + " of " + pages
                : "");
        int used = bodyLines(notice == null || notice.isBlank() ? null : UltraStyle.notice(notice)) + 1
                + (posts.isEmpty() ? 2 : shown.size());
        if (status != null && used + bodyLines(status) <= BOARD_BODY_LINES) {
            spec.line(UltraStyle.header(heading)).line(status);
        } else {
            spec.line(UltraStyle.header(heading) + (statusShort == null ? "" : UltraStyle.LABEL + " - " + statusShort));
        }
        if (posts.isEmpty()) {
            spec.line(UltraStyle.LABEL + "Nobody is looking for a spar right now. Post your own, or challenge someone"
                    + " directly.");
        }
        int n = pg * BOARD_SHOWN;
        for (BoardSpar.Post p : shown) {
            spec.line(postLine(++n, p, me, player));
        }
        spec.buttonsAfterLines();
        int placed = 0;
        for (BoardSpar.Post p : shown) {
            String why = BoardSpar.whyNot(player, p);
            String label = "Accept " + p.name();
            boolean left = placed % 2 == 0;
            if (why != null) {
                if (left) {
                    spec.lockedButton(label);
                } else {
                    spec.lockedButtonRight(label);
                }
            } else {
                // 4.7.80: a post with a wager asks first and says the stake the accepter matches.
                String act = p.wager() > 0L && p.wagerKind() != null && !p.wagerKind().isBlank()
                        ? "page:" + PAGE_ACCEPT + p.poster() : "do:" + BoardSpar.ACT_ACCEPT + ":" + p.poster();
                if (left) {
                    spec.buttonLeft(act, UltraStyle.confirm(label), UltraStyle.STYLE_CONFIRM);
                } else {
                    spec.buttonRight(act, UltraStyle.confirm(label), UltraStyle.STYLE_CONFIRM);
                }
            }
            if (!left) {
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        // 4.7.85: two rows of three. Post (or take down), Challenge, Watch; then Last fight and the pager.
        if (mineUp != null) {
            col3(spec, 0, "do:" + BoardSpar.ACT_UNPOST, "Take my post down", UltraStyle.STYLE_NORMAL);
        } else {
            col3(spec, 0, "page:" + PAGE_POST, UltraStyle.confirm("Post a spar"), UltraStyle.STYLE_CONFIRM);
        }
        col3(spec, 1, "page:pick_challenge", "Challenge a player", UltraStyle.STYLE_NORMAL);
        col3(spec, 2, "page:watch", "Watch spars", UltraStyle.STYLE_NORMAL);
        spec.rowGap();
        if (BoardSpar.result(me) != null) {
            col3(spec, 0, "page:result", "Last fight", UltraStyle.STYLE_NORMAL);
        } else {
            col3(spec, 0, null, "Last fight - none yet", null);
        }
        if (pages > 1) {
            col3(spec, 1, pg > 0 ? "page:" + UltraPickHelper.pageArg(TAB_BOARD, pg - 1) : null, "Previous",
                    UltraStyle.STYLE_NORMAL);
            col3(spec, 2, pg + 1 < pages ? "page:" + UltraPickHelper.pageArg(TAB_BOARD, pg + 1) : null, "Next",
                    UltraStyle.STYLE_NORMAL);
        }
        spec.rowGap();
        spec.back();
        spec.send(player);
    }

    /** 4.7.85: body lines the board may use (notice, heading, 4 posts) to stay within 285 px. */
    static final int BOARD_BODY_LINES = 6;

    /** Body lines {@code styled} wraps to on a menu page without a player model (0 for null). */
    static int bodyLines(String styled) {
        return styled == null ? 0
                : UltraStyle.wrapBody(styled, UltraStyle.bodyTextFitPx(UltraStyle.MENU_W, -1)).size();
    }

    /** Characters a menu body line holds ({@link UltraStyle#wrapBody}'s budget). */
    static int bodyChars() {
        return Math.max(8, UltraStyle.bodyTextFitPx(UltraStyle.MENU_W, -1) / UltraStyle.BODY_GLYPH_PX);
    }

    /**
     * 4.7.85: one line per post, "1. Wager80 Lv 1, friendly, 1 Emerald, wants TP" (no wait time; a short wager
     * and "wants"; ", for you" in green on a post aimed at you). A name too long for the line is cut with "..";
     * the Accept button carries the full name.
     */
    static String postLine(int n, BoardSpar.Post p, UUID me, ServerPlayer viewer) {
        String stake = p.wager() > 0L && p.wagerKind() != null && !p.wagerKind().isBlank()
                ? ("POINTS".equals(p.wagerKind()) ? p.wager() + " points"
                : com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.compactCost(p.wager())) : "no wager";
        return postLine(n, p.name(), BoardSpar.levelOf(p.poster(), viewer.m_20194_()), p.ranked(), stake,
                postWant(p.want()), me.equals(p.target()));
    }

    /** {@link #postLine(int, BoardSpar.Post, UUID, ServerPlayer)} from its parts: always one body line. */
    static String postLine(int n, String rawName, int lvl, boolean ranked, String stake, String want, boolean forMe) {
        String head = n + ". ";
        String tail = " Lv " + lvl + ", " + (ranked ? "ranked" : "friendly") + ", " + stake + ", wants " + want;
        String mark = forMe ? ", for you" : "";
        String name = rawName == null ? "?" : rawName;
        int room = bodyChars() - head.length() - tail.length() - mark.length();
        if (name.length() > room) {
            name = name.substring(0, Math.max(1, room - 2)) + "..";
        }
        return UltraStyle.VALUE + head + name + UltraStyle.LABEL + tail + (forMe ? UltraStyle.GREEN + mark : "");
    }

    /** 4.7.85: what a post wants, short enough for one board line: TP, PU (Potential Unlock) or any. */
    static String postWant(BoardSpar.Want w) {
        return w == BoardSpar.Want.POTENTIAL ? "PU" : w == BoardSpar.Want.ANY ? "any" : w.label;
    }

    /**
     * 4.7.84 (SP-2, SP-3): Post a spar. Kind, wager, range and what you're looking for (each button says it changes
     * on a tap), then "Post for 15 min". The ranked lock reads like the other toggles: dim "Kind - Friendly".
     */
    static void openBoardPost(ServerPlayer player, String notice) {
        UltraScreenSpec spec = sub(PAGE_POST, notice);
        UUID me = player.m_20148_();
        if (DojoService.dojoOf(player) == null) {
            spec.line(UltraStyle.LABEL + "You need a dojo to spar. Ask to join one from the dojo list, or found your own"
                    + " (free).").buttonsAfterLines();
            spec.button("page:dojo_list", "Find a dojo", UltraStyle.STYLE_NORMAL).rowGap();
            spec.back();
            spec.send(player);
            return;
        }
        BoardSpar.Draft d = BoardSpar.draft(me);
        boolean unlocked = BoardSpar.rankedUnlocked(me);
        BoardSpar.Post mineUp = BoardSpar.postOf(me);
        spec.line(UltraStyle.header("Post a spar"))
                .line(UltraStyle.LABEL + "Tap a setting to change it. Your post stays on the board for 15 minutes.")
                .line(UltraStyle.LABEL + "Potential Unlock trains only in friendly spars, never in ranked ones.");
        if (!unlocked) {
            int left = DojoCatalog.RANKED_GATE - BoardSpar.unranked(me);
            spec.line(UltraStyle.LABEL + "Ranked unlocks after " + DojoCatalog.RANKED_GATE + " friendly spars: " + left
                    + " to go.");
        }
        if (mineUp != null) {
            spec.line(UltraStyle.LABEL + "You have a post up. Take it down to post a new one.");
        }
        spec.buttonsAfterLines();
        String mode = d.ranked() ? "Ranked" : "Friendly";
        if (!unlocked && !d.ranked()) {
            spec.lockedButton("Kind - Friendly");
        } else {
            spec.buttonLeft("do:" + BoardSpar.ACT_MODE, "Kind - " + mode, UltraStyle.STYLE_NORMAL);
        }
        // 4.7.80: the wager is a sub-menu (kind, then amount).
        spec.buttonRight("page:" + PAGE_WAGER, "Wager - " + shortWager(d), UltraStyle.STYLE_NORMAL).rowGap();
        spec.buttonLeft("do:" + BoardSpar.ACT_RANGE, "Range - " + shortRange(d), UltraStyle.STYLE_NORMAL);
        spec.buttonRight("do:" + BoardSpar.ACT_WANT, "Wants - " + shortWant(d.want()), UltraStyle.STYLE_NORMAL)
                .rowGap();
        if (mineUp != null) {
            spec.buttonLeft("do:" + BoardSpar.ACT_UNPOST, "Take my post down", UltraStyle.STYLE_NORMAL);
            spec.lockedButtonRight("Post for 15 min").rowGap();
        } else {
            spec.button("do:" + BoardSpar.ACT_POST, UltraStyle.confirm("Post for 15 min"), UltraStyle.STYLE_CONFIRM)
                    .rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /** "TP", "Potential Unlock" is cut on a half-width button: "PU training". */
    static String shortWant(BoardSpar.Want w) {
        return w == BoardSpar.Want.POTENTIAL ? "PU training" : w.label;
    }

    static final String PAGE_WAGER = "board_wager";
    static final String PAGE_WAGER_COINS = "board_wager_coins";
    static final String PAGE_WAGER_POINTS = "board_wager_points";
    static final String PAGE_ACCEPT = "board_accept:";

    static boolean canWagerPoints(ServerPlayer player) {
        Dojo mine = DojoService.dojoOf(player);
        return mine != null && DojoService.canManage(player.m_20148_(), mine);
    }

    /** 4.7.80 Wager: pick the kind first (Ancient Coins, Dojo Points, or none), then the amount. */
    static void openWager(ServerPlayer player, String notice) {
        UltraScreenSpec spec = sub(PAGE_WAGER, notice);
        BoardSpar.Draft d = BoardSpar.draft(player.m_20148_());
        boolean points = canWagerPoints(player);
        spec.line(UltraStyle.header("Wager"))
                .line(UltraStyle.labelValue("Your next post", BoardSpar.wagerText(BoardSpar.wagerKind(d.wager()),
                        BoardSpar.wagerAmount(d.wager()))))
                .line(UltraStyle.LABEL + "Pick what to stake, then how much.")
                .line(UltraStyle.LABEL + "Whoever accepts stakes exactly the same. The winner takes both stakes.")
                .line(UltraStyle.LABEL + "That includes a forfeit, and running out of time (more damage wins). "
                        + REFUND_LINE);
        if (!points) {
            spec.line(UltraStyle.LABEL + "Only a master or an elite can wager Dojo Points.");
        }
        spec.buttonsAfterLines();
        spec.buttonLeft("page:" + PAGE_WAGER_COINS, "Ancient Coins", UltraStyle.STYLE_NORMAL);
        if (points) {
            spec.buttonRight("page:" + PAGE_WAGER_POINTS, "Dojo Points", UltraStyle.STYLE_NORMAL).rowGap();
        } else {
            spec.lockedButtonRight("Dojo Points").rowGap();
        }
        boolean none = BoardSpar.wagerKind(d.wager()).isBlank();
        spec.buttonLeft("do:" + BoardSpar.ACT_WAGER_SET + ":0", none ? UltraStyle.confirm("No wager") : "No wager",
                none ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_NORMAL).rowGap();
        spec.back();
        spec.send(player);
    }

    /** {@code 1 Emerald coin}, {@code 3 Netherite coins}: the coin count and kind, said as coins. */
    static String coinLabel(long copper) {
        String cost = com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.compactCost(copper);
        int sp = cost.indexOf(' ');
        if (sp <= 0) {
            return cost;
        }
        return cost + ("1".equals(cost.substring(0, sp)) ? " coin" : " coins");
    }

    /** 4.7.80 Wager amount for one kind, within the caps (5 Netherite coins, 20 Dojo Points). */
    static void openWagerAmounts(ServerPlayer player, String kind, String notice) {
        boolean pts = "POINTS".equals(kind);
        UltraScreenSpec spec = sub(pts ? PAGE_WAGER_POINTS : PAGE_WAGER_COINS, notice);
        BoardSpar.Draft d = BoardSpar.draft(player.m_20148_());
        spec.line(UltraStyle.header(pts ? "Wager Dojo Points" : "Wager Ancient Coins"))
                .line(UltraStyle.LABEL + (pts ? "Taken from your dojo's points when a fight starts, up to "
                        + DojoCatalog.MAX_POINT_WAGER + "."
                        : "Taken from the coins on you when a fight starts, up to "
                        + com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.format(DojoCatalog.MAX_COIN_WAGER) + "."))
                .line(UltraStyle.LABEL + "The winner takes both stakes, even on a forfeit or when time runs out (more"
                        + " damage wins).")
                .line(UltraStyle.LABEL + REFUND_LINE);
        if (pts && !canWagerPoints(player)) {
            spec.line(UltraStyle.LABEL + "Only a master or an elite can wager Dojo Points.").buttonsAfterLines();
            spec.back();
            spec.send(player);
            return;
        }
        spec.buttonsAfterLines();
        int placed = 0;
        for (int step = 1; step < BoardSpar.wagerSteps(); step++) {
            if (!kind.equals(BoardSpar.wagerKind(step))) {
                continue;
            }
            long amt = BoardSpar.wagerAmount(step);
            String label = pts ? amt + " Dojo Points" : coinLabel(amt);
            boolean cur = d.wager() == step;
            String action = "do:" + BoardSpar.ACT_WAGER_SET + ":" + step;
            String text = cur ? UltraStyle.confirm(label) : label;
            String style = cur ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_NORMAL;
            if (placed % 2 == 0) {
                spec.buttonLeft(action, text, style);
            } else {
                spec.buttonRight(action, text, style).rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /** 4.7.80: accepting a wagered post says the stake first (the same kind and amount as the poster's). */
    static void openAcceptAsk(ServerPlayer player, String posterRaw, String notice) {
        UltraScreenSpec spec = sub(PAGE_ACCEPT + posterRaw, notice);
        UUID posterId;
        try {
            posterId = UUID.fromString(posterRaw.trim());
        } catch (IllegalArgumentException e) {
            posterId = null;
        }
        BoardSpar.Post post = posterId == null ? null : BoardSpar.postOf(posterId);
        if (post == null) {
            spec.line(UltraStyle.LABEL + "That post is gone. Pick another one on the Spar Board.").buttonsAfterLines();
            spec.back();
            spec.send(player);
            return;
        }
        String stake = BoardSpar.wagerText(post.wagerKind(), post.wager());
        String why = BoardSpar.whyNot(player, post);
        spec.line(UltraStyle.header("Accept " + post.name() + "'s spar"))
                .line(UltraStyle.labelValue("Kind", post.ranked() ? "Ranked" : "Friendly"))
                .line(UltraStyle.labelValue("You stake", stake + " (the same as " + post.name() + ")"))
                .line(UltraStyle.LABEL + ("POINTS".equals(post.wagerKind()) ? "Taken from your dojo's points"
                        : "Taken from the coins on you") + " when the fight starts.")
                .line(UltraStyle.LABEL + "The winner takes both stakes, even on a forfeit or when time runs out (more"
                        + " damage wins).")
                .line(UltraStyle.LABEL + REFUND_LINE);
        if (why != null) {
            spec.line(UltraStyle.RED + why);
        }
        spec.buttonsAfterLines();
        if (why == null) {
            spec.button("do:" + BoardSpar.ACT_ACCEPT + ":" + post.poster(), UltraStyle.confirm("Accept and stake"),
                    UltraStyle.STYLE_CONFIRM).rowGap();
        } else {
            spec.lockedButton("Accept and stake").rowGap();
            // 4.7.85 (N6): short of the stake: coins, or the dojo's points.
            if (why.contains("in Ancient Coins on you")) {
                spec.offerGetMore(UltraStyle.Shortfall.COINS, null);
            } else if (why.contains("Dojo Points for this wager")) {
                spec.offerGetMore(UltraStyle.Shortfall.DOJO_POINTS, null);
            }
        }
        spec.back();
        spec.send(player);
    }

    static String shortWager(BoardSpar.Draft d) {
        long amt = BoardSpar.wagerAmount(d.wager());
        String kind = BoardSpar.wagerKind(d.wager());
        if (kind.isBlank()) {
            return "none";
        }
        return "POINTS".equals(kind) ? amt + " points"
                : com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.compactCost(amt);
    }

    static String shortRange(BoardSpar.Draft d) {
        int r = BoardSpar.levelRange(d.range());
        return r <= 0 ? "any level" : "+/- " + r + " Lv";
    }

    static void openResult(ServerPlayer player, String notice) {
        UltraScreenSpec spec = sub("result", notice);
        BoardSpar.Result r = BoardSpar.result(player.m_20148_());
        if (r == null) {
            spec.line(UltraStyle.LABEL + "No finished board spar yet.").buttonsAfterLines();
        } else {
            for (String l : r.lines()) {
                spec.line(l);
            }
            spec.buttonsAfterLines();
            spec.button("do:" + BoardSpar.ACT_REMATCH, UltraStyle.confirm("Rematch " + r.opponentName()),
                    UltraStyle.STYLE_CONFIRM).rowGap();
        }
        spec.back();
        spec.send(player);
    }

    static void openChallengePick(ServerPlayer player, int page, String notice) {
        UltraScreenSpec spec = sub(UltraPickHelper.pageArg("pick_challenge", page), notice);
        BoardSpar.Draft d = BoardSpar.draft(player.m_20148_());
        spec.line(UltraStyle.LABEL + "Challenge one player to a " + (d.ranked() ? "ranked" : "friendly") + " spar with "
                + BoardSpar.wagerText(BoardSpar.wagerKind(d.wager()), BoardSpar.wagerAmount(d.wager())) + ". To change"
                + " that, set it on Post a spar first.");
        List<ServerPlayer> others = new ArrayList<>();
        for (ServerPlayer o : player.m_20194_().m_6846_().m_11314_()) {
            if (o != player && DojoService.dojoOf(o) != null) {
                others.add(o);
            }
        }
        if (others.isEmpty()) {
            spec.line(UltraStyle.LABEL + "Nobody online is in a dojo right now.");
        }
        spec.buttonsAfterLines();
        int per = 8;
        int pages = Math.max(1, (others.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(page, pages - 1));
        int placed = 0;
        for (int i = pg * per; i < Math.min(others.size(), (pg + 1) * per); i++) {
            ServerPlayer o = others.get(i);
            String action = "do:" + BoardSpar.ACT_CHALLENGE + ":" + o.m_20148_();
            if (placed % 2 == 0) {
                spec.buttonLeft(action, o.m_7755_().getString(), UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonRight(action, o.m_7755_().getString(), UltraStyle.STYLE_NORMAL).rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        pager(spec, "pick_challenge", pg, pages);
        spec.back();
        spec.send(player);
    }

    static void openWatch(ServerPlayer player, String notice) {
        UltraScreenSpec spec = sub("watch", notice);
        spec.line(UltraStyle.LABEL + "Watch a spar from a dojo's balcony. Dojos without a balcony can't be watched.");
        List<Dojo> withBouts = new ArrayList<>();
        for (BoardSpar.Bout b : BoardSpar.bouts()) {
            Dojo d = DojoService.byMaster(b.host);
            if (d != null && !withBouts.contains(d)) {
                withBouts.add(d);
            }
        }
        if (withBouts.isEmpty()) {
            spec.line(UltraStyle.LABEL + "No spars are running right now.");
        }
        spec.buttonsAfterLines();
        // Full-width rows: "Watch <24-character dojo> (no balcony)" is cut on a half-width button.
        for (Dojo d : withBouts) {
            String label = "Watch " + d.name;
            if (DojoService.tier(d, DojoCatalog.SPECTATOR) <= 0) {
                spec.buttonWide(UltraGuiActions.NO_ACTION, UltraStyle.dim(label + " (no balcony)"), UltraStyle.STYLE_DIM,
                        null).rowGap();
            } else {
                spec.buttonWide("do:dojo_spectate:" + d.masterUuid, label, UltraStyle.STYLE_NORMAL, null).rowGap();
            }
        }
        if (DojoWorld.spectating(player.m_20148_())) {
            spec.button("do:dojo_leave", "Stop watching", UltraStyle.STYLE_NORMAL).rowGap();
        }
        spec.back();
        spec.send(player);
    }

    // ------------------------------------------------------------------ 4.7.88: Your dojos

    /**
     * 4.7.88: the one way into the Dojo system (the hub's Dojo tile, {@code open:dojo}, {@code /lm open dojo},
     * {@code /lm dojo}). In two dojos (your own as master, your master's as disciple): "Your dojos" first, to pick one.
     * In one: straight to its Home. In none: the no-dojo Home (found one or browse).
     */
    public static void openDojoEntry(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (DojoService.affiliations(player.m_20148_()).size() > 1) {
            openYourDojos(player, null);
            return;
        }
        DojoService.clearView(player.m_20148_());
        openDojo(player, null);
    }

    /** 4.7.88: a dojo's style for a picker line ("Ki style", "no style yet"). */
    static String styleText(Dojo d) {
        DojoCatalog.Style s = DojoCatalog.Style.of(d.style);
        return s == null ? "no style yet" : s.label + " style";
    }

    /** 4.7.88: the first label that fits a half-width button, else the last one. */
    static String fitHalf(String... options) {
        int room = UltraStyle.labelMaxWidth(UltraStyle.BTN_W);
        for (String o : options) {
            if (UltraStyle.textWidthPx(o) <= room) {
                return o;
            }
        }
        return options[options.length - 1];
    }

    /**
     * 4.7.88: "Your dojos": one line per dojo the player belongs to (name, Master or Disciple, style) and, in the same
     * order, a row with Visit (teleport there) and View (open its Home). A player in one dojo or none gets that Home.
     */
    static void openYourDojos(ServerPlayer player, String notice) {
        UUID me = player.m_20148_();
        List<DojoService.Affiliation> mine = DojoService.affiliations(me);
        if (mine.size() < 2) {
            DojoService.clearView(me);
            openDojo(player, notice);
            return;
        }
        Dojo viewed = DojoService.viewedDojo(me);
        UltraScreenSpec spec = dojoSub(PAGE_MINE, notice);
        spec.line(UltraStyle.header("Your dojos"))
                .line(UltraStyle.LABEL + "You belong to " + mine.size() + " dojos. View opens a dojo's Home; what you"
                        + " can change there depends on your place in it.");
        int n = 0;
        for (DojoService.Affiliation a : mine) {
            n++;
            Dojo d = a.dojo();
            spec.line(UltraStyle.VALUE + n + ". " + d.name + UltraStyle.LABEL + " - " + a.role().label + ", "
                    + styleText(d) + (d == viewed ? " (open now)" : ""));
        }
        spec.buttonsAfterLines();
        n = 0;
        for (DojoService.Affiliation a : mine) {
            n++;
            Dojo d = a.dojo();
            spec.buttonLeft("do:dojo_visit:" + d.masterUuid, fitHalf("Visit " + d.name, "Visit dojo " + n),
                    UltraStyle.STYLE_NORMAL);
            spec.buttonRight("do:" + ACT_VIEW + ":" + d.masterUuid,
                    UltraStyle.confirm(fitHalf("View " + d.name, "View dojo " + n)), UltraStyle.STYLE_CONFIRM).rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /** 4.7.88: the dojo the Dojo pages show and act on ({@link DojoService#viewedDojo(ServerPlayer)}). */
    static Dojo viewed(ServerPlayer player) {
        return DojoService.viewedDojo(player);
    }

    // ------------------------------------------------------------------ Dojo Home

    static void openDojo(ServerPlayer player, String notice) {
        UltraScreenSpec spec = dojoBase(TAB_DOJO_HOME, TAB_DOJO_HOME, notice);
        UUID me = player.m_20148_();
        Dojo d = viewed(player);
        Dojo own = DojoStore.get().dojos.get(me.toString());
        if (d == null) {
            spec.line(UltraStyle.header("No dojo yet"));
            if (own != null && !own.open) {
                spec.line(UltraStyle.VALUE + own.name + " is closed.")
                        .line(UltraStyle.LABEL + "Reopening keeps your members and style, but every upgrade drops one tier.")
                        .buttonsAfterLines();
                spec.button("do:dojo_reopen", UltraStyle.confirm("Reopen " + own.name), UltraStyle.STYLE_CONFIRM).rowGap();
            } else {
                spec.line(UltraStyle.VALUE + "A dojo is your team's home: you spar in its rings, fight wars as a team, and"
                                + " train with a master.")
                        .line(UltraStyle.LABEL + "Join one from the dojo list, or found your own for free and become its"
                                + " master.")
                        .line(UltraStyle.LABEL + "To found one you need a name and a fighting style. A banner is optional.");
                DojoFounding.grantLine(spec, player);
                spec.buttonsAfterLines();
                spec.button("page:" + DojoFounding.PAGE_NAME, UltraStyle.confirm("Found a dojo (free)"),
                        UltraStyle.STYLE_CONFIRM).rowGap();
            }
            spec.buttonLeft("page:dojo_list", "Browse dojos", UltraStyle.STYLE_NORMAL);
            spec.buttonRight("page:mentor", "Training bonds", UltraStyle.STYLE_NORMAL).rowGap();
            spec.back();
            spec.send(player);
            return;
        }
        boolean master = DojoService.isMaster(me, d);
        DojoCatalog.Style style = DojoCatalog.Style.of(d.style);
        DojoCatalog.Bonus bonus = DojoCatalog.bonus(d.bonus);
        boolean noStyle = style == null;
        boolean noBanner = DojoService.defaultBanner(d);
        // 4.7.84 (DJ-6): a war of this dojo in sign-up shows on Home, and the Spar Board slot becomes Join the war.
        DojoStore.WarRecord signup = DojoWar.signupFor(d.masterUuid);
        boolean canJoinWar = signup != null && !DojoWar.signedUp(signup.id, me);
        if (signup != null) {
            String vs = signup.fromDojo.equals(d.masterUuid) ? signup.toName : signup.fromName;
            spec.line(UltraStyle.noticeLine("War vs " + vs + ": " + BoardSpar.clock(DojoWar.signupLeftMs(signup.id))
                    + " left to join."));
        }
        // 4.7.84 (owner pick b): a disciple's dojo title, shown with their name (display only).
        String title = DojoService.discipleTitle(me, d);
        if (title != null) {
            spec.line(UltraStyle.VALUE + player.m_7755_().getString() + UltraStyle.LABEL + " - " + title);
        }
        // 4.7.85: the name and 5 short stat lines beside the floor plan (6 rows). Style and bonus, points and clout,
        // members and the hall share a line. A master's free first style or banner pick is said in its stat line
        // (4.7.84 DJ-13), and the button below makes it.
        List<String> stats = new ArrayList<>();
        stats.add(UltraStyle.header(d.name));
        stats.add(UltraStyle.labelValue("Master", d.masterName + (master ? " (you)" : "")));
        stats.add(noStyle ? UltraStyle.labelValue("Style", master ? "none yet, first pick free" : "not picked yet")
                : UltraStyle.labelValue("Style", style.label) + UltraStyle.LABEL + " - "
                        + UltraStyle.VALUE + (bonus == null ? "no bonus" : bonus.name()));
        stats.add(UltraStyle.labelValue("Dojo Points", String.valueOf(d.points)) + UltraStyle.LABEL + " - "
                + UltraStyle.labelValue("Clout", d.clout + " this season"));
        int rings = DojoService.arenas(d);
        stats.add(UltraStyle.labelValue("Members", (1 + DojoService.disciples(d).size()) + " of "
                + (1 + DojoCatalog.disciplesAllowed(DojoService.tier(d, DojoCatalog.CAPACITY)))) + UltraStyle.LABEL
                + " - " + UltraStyle.labelValue("Hall", DojoCatalog.lookName(DojoCatalog.lookTier(DojoCatalog.ARENA,
                rings)) + ", " + rings(rings)));
        stats.add(master && noBanner
                ? UltraStyle.labelValue("Banner", DojoFounding.bannerText(d) + ", first pick free")
                : UltraStyle.labelValue("Banner", DojoFounding.bannerText(d)));
        hallPlan(spec, player, d, stats);
        spec.buttonsAfterLines();
        // 4.7.85: two rows of three.
        if (canJoinWar) {
            col3(spec, 0, "do:" + DojoWar.ACT_JOIN + ":" + signup.id, UltraStyle.confirm("Join the war"),
                    UltraStyle.STYLE_CONFIRM);
        } else {
            col3(spec, 0, "go:spar|" + TAB_BOARD, UltraStyle.confirm("Spar Board"), UltraStyle.STYLE_CONFIRM);
        }
        col3(spec, 1, "page:" + PAGE_MEMBERS, "Members", UltraStyle.STYLE_NORMAL);
        col3(spec, 2, "page:mentor", "Training bonds", UltraStyle.STYLE_NORMAL);
        spec.rowGap();
        if (DojoWorld.inDojoWorld(player) && DojoWorld.hasSpot(me)) {
            col3(spec, 0, "do:dojo_leave", "Leave dojo", UltraStyle.STYLE_NORMAL);
        } else {
            col3(spec, 0, "do:dojo_visit:" + d.masterUuid, "Visit dojo", UltraStyle.STYLE_NORMAL);
        }
        // 4.7.88: a player in two dojos gets "Your dojos" in the first free cell of this row.
        boolean several = DojoService.affiliations(me).size() > 1;
        if (master) {
            col3(spec, 1, "page:" + PAGE_SETTINGS, "Dojo settings", UltraStyle.STYLE_NORMAL);
            // 4.7.84 (DJ-1, DJ-13, owner pick d): the free first picks stay on Home until they're made.
            if (noStyle) {
                col3(spec, 2, "page:" + PAGE_STYLE_PICK, UltraStyle.confirm("Pick a style (free)"),
                        UltraStyle.STYLE_CONFIRM);
            } else if (noBanner) {
                col3(spec, 2, "page:" + DojoFounding.PAGE_PICK_BANNER, UltraStyle.confirm("Pick a banner (free)"),
                        UltraStyle.STYLE_CONFIRM);
            } else if (several) {
                col3(spec, 2, "page:" + PAGE_MINE, "Your dojos", UltraStyle.STYLE_NORMAL);
            }
        } else if (several) {
            col3(spec, 1, "page:" + PAGE_MINE, "Your dojos", UltraStyle.STYLE_NORMAL);
        }
        spec.rowGap();
        spec.back();
        spec.send(player);
    }

    /** The Dojo Home with a result line on top (founding, banner, rename). */
    static void openDojoNotice(ServerPlayer player, String notice) {
        openDojo(player, notice);
    }

    /** 4.7.84 (DJ-1): Dojo settings (master): rename, the banner, and, below a gap, Close dojo. */
    static void openSettings(ServerPlayer player, String notice) {
        Dojo d = viewed(player);
        UltraScreenSpec spec = dojoSub(PAGE_SETTINGS, notice);
        if (d == null || !DojoService.isMaster(player.m_20148_(), d)) {
            spec.line(UltraStyle.LABEL + "Only the master can rename the dojo, change its banner or close it.")
                    .buttonsAfterLines();
            spec.back();
            spec.send(player);
            return;
        }
        long cd = DojoService.identityCooldownLeft(d);
        spec.line(UltraStyle.header("Dojo settings"))
                .line(UltraStyle.labelValue("Name", d.name))
                .line(UltraStyle.labelValue("Banner", DojoFounding.bannerText(d)))
                .line(UltraStyle.labelValue("Dojo Points", String.valueOf(d.points)))
                .line(UltraStyle.LABEL + (cd > 0L ? "The name, style and banner can change again in "
                        + DojoService.cooldownText(cd) + "."
                        : "A rename or a new banner locks the name, style and banner for 12 hours."))
                .buttonsAfterLines();
        DojoFounding.identityButtons(spec, player, d, true);
        spec.setRow(spec.row() + 8);
        spec.button("page:dojo_close", UltraStyle.danger("Close dojo"), UltraStyle.STYLE_DANGER).rowGap();
        spec.back();
        spec.send(player);
    }

    static void openCloseConfirm(ServerPlayer player) {
        Dojo d = viewed(player);
        UltraScreenSpec spec = dojoSub("dojo_close", null);
        if (d == null || !DojoService.isMaster(player.m_20148_(), d)) {
            spec.line(UltraStyle.LABEL + "Only the master can close the dojo.").buttonsAfterLines();
        } else {
            spec.line(UltraStyle.danger("Close " + d.name + "?"))
                    .line(UltraStyle.LABEL + "Members can't spar or fight wars while it is closed.")
                    .line(UltraStyle.LABEL + "Reopening keeps members and style, but every upgrade drops one tier.")
                    .buttonsAfterLines();
            spec.button("do:dojo_close", UltraStyle.danger("Yes, close the dojo"), UltraStyle.STYLE_DANGER).rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /** 4.7.84: a page that needs a dojo, for a player in none: what to do, with Browse dojos and Found a dojo. */
    static void noDojo(UltraScreenSpec spec, ServerPlayer player, String line) {
        spec.line(UltraStyle.LABEL + line).buttonsAfterLines();
        spec.buttonLeft("page:dojo_list", "Browse dojos", UltraStyle.STYLE_NORMAL);
        if (DojoService.foundProblem(player) == null) {
            spec.buttonRight("page:" + DojoFounding.PAGE_NAME, "Found a dojo", UltraStyle.STYLE_NORMAL).rowGap();
        } else {
            spec.lockedButtonRight("Found a dojo").rowGap();
        }
        spec.back();
        spec.send(player);
    }

    // ------------------------------------------------------------------ upgrades

    static void openUpgrades(ServerPlayer player, String notice) {
        Dojo d = viewed(player);
        UltraScreenSpec spec = dojoBase(TAB_UPGRADES, TAB_UPGRADES, notice);
        if (d == null) {
            noDojo(spec, player, "You're not in a dojo yet. Join one or found your own on the Dojo Home tab.");
            return;
        }
        // 4.7.84 (DJ-9): how points are earned, with the real numbers from DojoCatalog (display only).
        // 4.7.85: the balance joins the 6 lines beside the plan; the war lines share one; "Tap an upgrade" is gone.
        DojoCatalog.Earn host = DojoCatalog.Earn.HOST;
        DojoCatalog.Earn train = DojoCatalog.Earn.TRAINING;
        String balance = UltraStyle.labelValue("Dojo Points", d.points + " (upgrades cost points)");
        if (com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesPictures(player)) {
            hallPlan(spec, player, d, List.of(balance, UltraStyle.header("How to earn points"),
                    UltraStyle.labelValue("Host a spar", "+" + host.points + " (" + host.dailyCap + " a day)"),
                    UltraStyle.labelValue("Train a disciple", "+" + train.points + " (" + train.dailyCap + " a day)"),
                    UltraStyle.labelValue("Disciple milestone", "+" + DojoCatalog.Earn.MILESTONE.points),
                    UltraStyle.labelValue("War bout won", "+" + DojoCatalog.Earn.WAR_BOUT.points) + UltraStyle.LABEL
                            + ", " + UltraStyle.labelValue("war won", "+" + DojoCatalog.Earn.WAR_WIN.points)));
        } else {
            spec.line(balance)
                    .line(UltraStyle.LABEL + "Earn Dojo Points: host a spar +" + host.points + " (" + host.dailyCap
                            + " a day), train a disciple +" + train.points + " (" + train.dailyCap + " a day), a disciple"
                            + " milestone +" + DojoCatalog.Earn.MILESTONE.points + ", a war bout won +"
                            + DojoCatalog.Earn.WAR_BOUT.points + ", a war won +" + DojoCatalog.Earn.WAR_WIN.points + ".");
            for (String l : DojoWorld.mapLines(d, "", false)) {
                spec.line(l);
            }
        }
        spec.buttonsAfterLines();
        // 4.7.85: three columns, Style and bonus in the ninth cell. One label shape for the whole list: "Name - N"
        // when every label fits its cell, else "Name: N".
        boolean dash = true;
        for (String up : DojoCatalog.UPGRADES) {
            int cost = DojoCatalog.cost(up, DojoService.tier(d, up));
            dash &= cost < 0 || UltraStyle.textWidthPx(DojoCatalog.upgradeName(up) + " - " + cost) <= COL3_LABEL_PX;
        }
        int placed = 0;
        for (String up : DojoCatalog.UPGRADES) {
            int cost = DojoCatalog.cost(up, DojoService.tier(d, up));
            col3(spec, placed % 3, "page:dojo_up:" + up, upgradeLabel(up, cost, dash), UltraStyle.STYLE_NORMAL);
            if (placed % 3 == 2) {
                spec.rowGap();
            }
            placed++;
        }
        // 4.7.78: style and bonus live on the Upgrades tab.
        col3(spec, placed % 3, "page:dojo_style", "Style and bonus", UltraStyle.STYLE_NORMAL);
        spec.rowGap();
        spec.back();
        spec.send(player);
    }

    /**
     * 4.7.85: an upgrade's label in a three-column cell: "Training hall - 30" ({@code dash}) or "Training hall: 30",
     * "(max)" at the top; a short name when the full one doesn't fit ("Balcony: 120").
     */
    static String upgradeLabel(String up, int cost, boolean dash) {
        String tail = cost < 0 ? " (max)" : (dash ? " - " : ": ") + cost;
        return fitCol3(DojoCatalog.upgradeName(up) + tail, shortUpgradeName(up) + tail);
    }

    /** 4.7.85: a short upgrade name for a narrow button. */
    static String shortUpgradeName(String up) {
        return switch (up == null ? "" : up) {
            case DojoCatalog.SPECTATOR -> "Balcony";
            case DojoCatalog.QUARTERS -> "Quarters";
            case DojoCatalog.CAPACITY -> "Disciples";
            case DojoCatalog.GARDEN -> "Garden";
            case DojoCatalog.MEDITATION -> "Meditation";
            case DojoCatalog.ARENA -> "Rings";
            default -> DojoCatalog.upgradeName(up);
        };
    }

    /**
     * Body rows the upgrade pictures take (Now, After, Where side by side). 4.7.84 (DJ-4): 7, was 10. 4.7.85: 5, on
     * the right 60% of the body ({@link #PICTURE_SIDE}) with the name, Now, After, Cost and the balance on the left;
     * the pictures are as tall as 5 rows allow (about 77 x 64 px each).
     */
    static final int PICTURE_ROWS = 5;
    /** 4.7.85: share of the body width (thousandths) the upgrade pictures take on the right. */
    static final int PICTURE_SIDE = 600;
    /** 4.7.85: body rows the dojo's floor plan takes beside the Home stats and the Upgrades earn list. Was 7. */
    static final int PLAN_ROWS = 6;

    static String rings(int n) {
        return n == 1 ? "1 ring" : n + " rings";
    }

    /** The hall as it is now, in words: its look and its rings. */
    static String hallLine(Dojo d) {
        int rings = DojoService.arenas(d);
        return DojoCatalog.lookName(DojoCatalog.lookTier(DojoCatalog.ARENA, rings)) + ", " + rings + " of "
                + DojoCatalog.MAX_ARENAS + " rings";
    }

    /**
     * The dojo's floor plan on the right of {@code text} for LM clients that draw pictures (nothing else changes on
     * the page); KaiPath and older clients get the text lines alone.
     */
    static void hallPlan(UltraScreenSpec spec, ServerPlayer player, Dojo d, List<String> text) {
        com.google.gson.JsonArray plan = com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesPictures(player)
                ? com.dbzlegacy.adaptivedifficulty.dojo.DojoPictures.plan(com.dbzlegacy.adaptivedifficulty.dojo.DojoState.of(d))
                : new com.google.gson.JsonArray();
        if (plan.size() > 0) {
            spec.pictures(text, PLAN_ROWS, 400, plan);
        } else {
            for (String l : text) {
                spec.line(l);
            }
        }
    }

    /**
     * One upgrade: what it builds (Now, After, Where on LM clients; the text map otherwise), the cost, buy, visit.
     * 4.7.84 (DJ-4): LM picture clients get the pictures without the duplicate text map.
     */
    static void openUpgrade(ServerPlayer player, String up, String notice) {
        Dojo d = viewed(player);
        UltraScreenSpec spec = dojoSub("dojo_up:" + up, notice);
        if (d == null || !DojoCatalog.UPGRADES.contains(up)) {
            spec.line(UltraStyle.LABEL + "Pick an upgrade from the list.").buttonsAfterLines();
            spec.back();
            spec.send(player);
            return;
        }
        int t = DojoService.tier(d, up);
        int cost = DojoCatalog.cost(up, t);
        boolean maxed = cost < 0;
        boolean manage = DojoService.canManage(player.m_20148_(), d);
        // 4.7.85: one line of what it builds, then a short card (name, Now, After, Cost, your balance) that sits
        // beside the pictures on LM clients, then one line: the warning before a buy, or why you can't buy.
        spec.line(UltraStyle.VALUE + DojoCatalog.upgradeWhat(up, t));
        List<String> card = new ArrayList<>();
        card.add(UltraStyle.header(DojoCatalog.upgradeName(up)));
        String refit = null;
        if (DojoCatalog.ARENA.equals(up)) {
            card.add(UltraStyle.labelValue("Now", rings(t) + ", " + DojoCatalog.lookName(DojoCatalog.lookTier(up, t))
                    + " hall"));
            if (!maxed) {
                card.add(UltraStyle.labelValue("After", rings(t + 1) + ", "
                        + DojoCatalog.lookName(DojoCatalog.lookTier(up, t + 1)) + " hall"));
                if (DojoCatalog.lookTier(up, t + 1) > DojoCatalog.lookTier(up, t)) {
                    refit = UltraStyle.LABEL + "This ring refits the whole hall in the "
                            + DojoCatalog.lookName(DojoCatalog.lookTier(up, t + 1)) + " look.";
                }
            }
        } else if (DojoCatalog.CAPACITY.equals(up)) {
            card.add(UltraStyle.labelValue("Now", DojoCatalog.disciplesAllowed(t) + " disciples"));
            if (!maxed) {
                card.add(UltraStyle.labelValue("After", DojoCatalog.disciplesAllowed(t + 1) + " disciples"));
            }
        } else {
            card.add(UltraStyle.labelValue("Now", DojoCatalog.lookName(DojoCatalog.lookTier(up, t))));
            if (!maxed) {
                card.add(UltraStyle.labelValue("After", DojoCatalog.lookName(DojoCatalog.lookTier(up, t + 1))
                        + " look"));
            }
        }
        int need = maxed ? 0 : Math.max(0, cost - d.points);
        if (!maxed) {
            card.add(UltraStyle.labelValue("Cost", cost + " points"));
            card.add(UltraStyle.labelValue("You have", String.valueOf(d.points)));
        }
        com.google.gson.JsonArray pics = maxed || !com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesPictures(player)
                ? new com.google.gson.JsonArray()
                : com.dbzlegacy.adaptivedifficulty.dojo.DojoPictures.upgrade(com.dbzlegacy.adaptivedifficulty.dojo.DojoState.of(d), up);
        if (pics.size() > 0) {
            // LM clients: what this builds before and after, and where it goes in the hall (green), beside the card.
            spec.pictures(card, PICTURE_ROWS, PICTURE_SIDE, pics);
        } else {
            for (String l : card) {
                spec.line(l);
            }
            if (!DojoCatalog.CAPACITY.equals(up)) {
                spec.line(UltraStyle.LABEL + "Map: the starred part is the one this builds.");
                for (String l : DojoWorld.mapLines(d, up, !maxed)) {
                    spec.line(l);
                }
            }
        }
        if (refit != null) {
            spec.line(refit);
        }
        boolean canBuy = !maxed && manage && d.points >= cost;
        if (maxed) {
            spec.line(UltraStyle.LABEL + "Fully built. Nothing more to buy here.");
        } else if (!manage) {
            spec.line(UltraStyle.LABEL + "Only the master or an elite can buy upgrades.");
        } else if (need > 0) {
            int spars = (need + DojoCatalog.Earn.HOST.points - 1) / DojoCatalog.Earn.HOST.points;
            spec.line(UltraStyle.LABEL + "Your dojo needs " + need + " more points: about " + spars
                    + (spars == 1 ? " hosted spar." : " hosted spars."));
        } else {
            spec.line(UltraStyle.LABEL + "Points are spent at once. An upgrade can't be undone.");
        }
        spec.buttonsAfterLines();
        if (canBuy) {
            spec.buttonLeft("do:dojo_buy:" + up, UltraStyle.confirm("Buy for " + cost + " points"),
                    UltraStyle.STYLE_CONFIRM);
        } else {
            spec.lockedButton(maxed ? "Fully upgraded" : "Buy for " + cost + " points");
        }
        spec.buttonRight("do:dojo_visit:" + d.masterUuid, "Visit dojo", UltraStyle.STYLE_NORMAL).rowGap();
        if (!maxed && manage && d.points < cost) {
            spec.offerGetMore(UltraStyle.Shortfall.DOJO_POINTS, null);   // 4.7.85 (N6)
        }
        spec.back();
        spec.send(player);
    }

    // ------------------------------------------------------------------ style and bonus

    /**
     * 4.7.84 (DJ-5): the style and the bonus, then the bonus list only (under a "Bonuses for X" heading), then one
     * "Change style" button that opens the style picker. The bonus lock says the real time left (7 days, not 8).
     */
    static void openStyle(ServerPlayer player, String notice) {
        Dojo d = viewed(player);
        UltraScreenSpec spec = dojoSub("dojo_style", notice);
        if (d == null) {
            noDojo(spec, player, "You're not in a dojo yet. Join one or found your own on the Dojo Home tab.");
            return;
        }
        boolean master = DojoService.isMaster(player.m_20148_(), d);
        DojoCatalog.Style style = DojoCatalog.Style.of(d.style);
        DojoCatalog.Bonus active = DojoCatalog.bonus(d.bonus);
        long cd = DojoService.bonusCooldownLeft(d);
        // 4.7.85: "Your style decides which bonuses..." is gone; the list heading says it. The bonuses and Change
        // style sit in three columns (Change style takes two).
        spec.line(UltraStyle.labelValue("Style", style == null ? "not picked yet" : style.label + " (" + style.covers
                        + ")"))
                .line(UltraStyle.labelValue("Bonus", active == null ? "none" : active.name() + ": " + active.description()));
        if (cd > 0L) {
            spec.line(UltraStyle.LABEL + "You can change the bonus in " + DojoService.daysText(cd) + ".");
        }
        if (!master) {
            spec.line(UltraStyle.LABEL + "Only the master picks the style and the bonus.");
        }
        if (style != null) {
            spec.line(UltraStyle.header("Bonuses your " + style.label + " style allows - " + DojoCatalog.BONUS_COST
                    + " points each"));
        } else {
            spec.line(UltraStyle.LABEL + "Pick a style first (free); its bonuses show here.");
        }
        spec.buttonsAfterLines();
        int placed = 0;
        if (style != null) {
            for (DojoCatalog.Bonus b : DojoCatalog.bonuses(style)) {
                boolean can = master && !b.id().equals(d.bonus) && cd <= 0L && d.points >= DojoCatalog.BONUS_COST;
                String label = b.id().equals(d.bonus)
                        ? fitCol3("Active: " + b.name(), b.name() + " (active)",
                                b.name().replace(" tradition", "").replace(" focus", "") + " (active)")
                        : b.name();
                col3(spec, placed % 3, can ? "page:dojo_bonus_ask:" + b.id() : null, label, UltraStyle.STYLE_NORMAL);
                if (placed % 3 == 2) {
                    spec.rowGap();
                }
                placed++;
            }
        }
        String change = style == null ? "Choose a style (free)" : "Change style - " + DojoCatalog.STYLE_CHANGE_COST
                + " points";
        int col = placed % 3;
        int span = col == 2 ? 1 : 2;
        if (col == 2) {
            change = fitCol3(change, "Change style - " + DojoCatalog.STYLE_CHANGE_COST);
        }
        if (master) {
            col3(spec, col, span, "page:" + PAGE_STYLE_PICK, style == null ? UltraStyle.confirm(change) : change,
                    style == null ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_NORMAL);
        } else {
            col3(spec, col, span, null, change, null);
        }
        spec.rowGap();
        spec.back();
        spec.send(player);
    }

    /** 4.7.84 (DJ-5): the other styles; the first pick is free, a change goes through its ask page. */
    static void openStylePick(ServerPlayer player, String notice) {
        Dojo d = viewed(player);
        UltraScreenSpec spec = dojoSub(PAGE_STYLE_PICK, notice);
        if (d == null || !DojoService.isMaster(player.m_20148_(), d)) {
            spec.line(UltraStyle.LABEL + "Only your dojo's master can pick its style.").buttonsAfterLines();
            spec.back();
            spec.send(player);
            return;
        }
        DojoCatalog.Style style = DojoCatalog.Style.of(d.style);
        boolean first = style == null;
        long idCd = DojoService.identityCooldownLeft(d);
        spec.line(UltraStyle.header(first ? "Choose a style" : "Change style"));
        if (first) {
            spec.line(UltraStyle.LABEL + "Your first style is free. Members get the bonus you buy for it on spar TP.");
        } else {
            spec.line(UltraStyle.labelValue("Now", style.label + " (" + style.covers + ")"))
                    .line(UltraStyle.LABEL + "Changing style costs " + DojoCatalog.STYLE_CHANGE_COST
                            + " Dojo Points and ends the bonus you paid for.");
            if (idCd > 0L) {
                spec.line(UltraStyle.LABEL + "The name, style and banner can change again in "
                        + DojoService.cooldownText(idCd) + ".");
            }
        }
        for (DojoCatalog.Style s : DojoCatalog.Style.values()) {
            if (s != style) {
                spec.line(UltraStyle.labelValue(s.label, s.covers));
            }
        }
        spec.buttonsAfterLines();
        int placed = 0;
        for (DojoCatalog.Style s : DojoCatalog.Style.values()) {
            if (s == style) {
                continue;
            }
            boolean left = placed % 2 == 0;
            String act = first ? "do:dojo_style:" + s.name() : "page:dojo_style_ask:" + s.name();
            String lockedLabel = first ? null : idCd > 0L ? s.label + " - " + DojoService.cooldownText(idCd)
                    : d.points < DojoCatalog.STYLE_CHANGE_COST ? s.label + " - needs " + DojoCatalog.STYLE_CHANGE_COST
                    : null;
            if (lockedLabel != null) {
                if (left) {
                    spec.lockedButton(lockedLabel);
                } else {
                    spec.lockedButtonRight(lockedLabel);
                }
            } else if (left) {
                spec.buttonLeft(act, s.label, UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonRight(act, s.label, UltraStyle.STYLE_NORMAL);
            }
            if (!left) {
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /** 4.7.73: changing style ends the paid bonus; 4.7.84 (DJ-5) says the total to switch and get a bonus back. */
    static void openStyleAsk(ServerPlayer player, String styleId) {
        Dojo d = viewed(player);
        DojoCatalog.Style to = DojoCatalog.Style.of(styleId);
        UltraScreenSpec spec = dojoSub("dojo_style_ask:" + styleId, null);
        if (d == null || to == null || !DojoService.isMaster(player.m_20148_(), d)) {
            spec.line(UltraStyle.LABEL + "Only your dojo's master can change its style.").buttonsAfterLines();
            spec.back();
            spec.send(player);
            return;
        }
        DojoCatalog.Bonus active = DojoCatalog.bonus(d.bonus);
        boolean first = d.style == null || d.style.isBlank();
        spec.line(UltraStyle.header("Change style to " + to.label + "?"))
                .line(UltraStyle.labelValue("New style", to.label + " (" + to.covers + ")"));
        if (!first) {
            spec.line(UltraStyle.LABEL + "Changing style costs " + DojoCatalog.STYLE_CHANGE_COST + " Dojo Points.");
        }
        if (active != null) {
            spec.line(UltraStyle.dangerLine("Your " + active.name() + " bonus ends now."))
                    .line(UltraStyle.LABEL + "Its " + DojoCatalog.BONUS_COST + " Dojo Points are not refunded.");
        }
        spec.line(UltraStyle.LABEL + "A new bonus costs " + DojoCatalog.BONUS_COST + " more, and you can buy it right"
                + " away.");
        if (!first) {
            spec.line(UltraStyle.VALUE + "Total to switch and get a bonus back: " + (DojoCatalog.STYLE_CHANGE_COST
                            + DojoCatalog.BONUS_COST) + " Dojo Points (you have " + d.points + ").")
                    .line(UltraStyle.LABEL + "After this, the name, style and banner are locked for 12 hours.");
        }
        spec.buttonsAfterLines();
        String blocked = first ? null : DojoService.identityChangeProblem(d, DojoCatalog.STYLE_CHANGE_COST);
        if (blocked != null) {
            long cd = DojoService.identityCooldownLeft(d);
            spec.lockedButton(cd > 0L ? "Changes again in " + DojoService.cooldownText(cd)
                    : "Needs " + DojoCatalog.STYLE_CHANGE_COST + " points").rowGap();
            if (cd <= 0L) {
                spec.offerGetMore(UltraStyle.Shortfall.DOJO_POINTS, null);   // 4.7.85 (N6)
            }
        } else if (first) {
            spec.button("do:dojo_style:" + to.name(), UltraStyle.confirm("Use this style"), UltraStyle.STYLE_CONFIRM)
                    .rowGap();
        } else {
            String label = "Change style for " + DojoCatalog.STYLE_CHANGE_COST + " points";
            spec.button("do:dojo_style:" + to.name(), active != null ? UltraStyle.danger(label) : UltraStyle.confirm(label),
                    active != null ? UltraStyle.STYLE_DANGER : UltraStyle.STYLE_CONFIRM).rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /** 4.7.73: a bonus spends Dojo Points and locks the choice for a week; say both before the click. */
    static void openBonusAsk(ServerPlayer player, String bonusId) {
        Dojo d = viewed(player);
        DojoCatalog.Bonus b = DojoCatalog.bonus(bonusId);
        UltraScreenSpec spec = dojoSub("dojo_bonus_ask:" + bonusId, null);
        if (d == null || b == null || !DojoService.isMaster(player.m_20148_(), d)) {
            spec.line(UltraStyle.LABEL + "Only your dojo's master can pick its bonus.").buttonsAfterLines();
            spec.back();
            spec.send(player);
            return;
        }
        long days = DojoCatalog.BONUS_COOLDOWN_MS / DojoService.DAY_MS;
        DojoCatalog.Bonus active = DojoCatalog.bonus(d.bonus);
        spec.line(UltraStyle.header("Buy " + b.name() + "?"))
                .line(UltraStyle.LABEL + b.description())
                .line(UltraStyle.labelValue("Cost", DojoCatalog.BONUS_COST + " Dojo Points (you have " + d.points + ")"))
                .line(UltraStyle.LABEL + "After this the bonus can't change for " + days + " days.");
        if (active != null) {
            spec.line(UltraStyle.dangerLine("It replaces " + active.name() + "."));
        }
        spec.buttonsAfterLines();
        if (d.points >= DojoCatalog.BONUS_COST) {
            spec.button("do:dojo_bonus:" + b.id(), UltraStyle.confirm("Buy for " + DojoCatalog.BONUS_COST + " points"),
                    UltraStyle.STYLE_CONFIRM).rowGap();
        } else {
            spec.lockedButton("Needs " + DojoCatalog.BONUS_COST + " points").rowGap();
            spec.offerGetMore(UltraStyle.Shortfall.DOJO_POINTS, null);   // 4.7.85 (N6)
        }
        spec.back();
        spec.send(player);
    }

    // ------------------------------------------------------------------ members

    /**
     * 4.7.84 (DJ-2): one Members page for "Roster and bonds" and "Members and elites". 6 disciples a page with a pager.
     * The master gets [Name] (opens that member's page, where war rights live) and Make elite / Step down; everyone
     * else sees the names dim. Elites are marked; every disciple carries the title "Disciple of X" (owner pick b).
     */
    static void openMembers(ServerPlayer player, int page, String notice) {
        Dojo d = viewed(player);
        if (d == null) {
            noDojo(dojoSub(PAGE_MEMBERS, notice), player, "You're not in a dojo yet. Join one or found your own on the"
                    + " Dojo Home tab.");
            return;
        }
        boolean master = DojoService.isMaster(player.m_20148_(), d);
        List<String> disciples = DojoService.disciples(d);
        int pages = UltraPickHelper.pageCount(disciples.size(), MEMBERS_SHOWN);
        int pg = UltraPickHelper.clampPage(page, pages);
        UltraScreenSpec spec = dojoSub(UltraPickHelper.pageArg(PAGE_MEMBERS, pg), notice);
        int count = 1 + disciples.size();
        spec.line(UltraStyle.labelValue("Members", count + " of " + (1 + DojoCatalog.disciplesAllowed(
                        DojoService.tier(d, DojoCatalog.CAPACITY))) + " - master " + d.masterName + (master ? " (you)" : "")
                        + (pages > 1 ? " - page " + (pg + 1) + " of " + pages : "")))
                .line(UltraStyle.LABEL + "Elites (" + DojoCatalog.MAX_ELITES + " at most) can buy upgrades and declare"
                        + " wars. Disciples carry the title Disciple of " + d.name + ".");
        if (disciples.isEmpty()) {
            spec.line(UltraStyle.LABEL + "No disciples yet. Invite players from Training bonds > Recruit.");
        }
        spec.buttonsAfterLines();
        List<String> shown = UltraPickHelper.pageSlice(disciples, MEMBERS_SHOWN, pg);
        if (master) {
            for (String u : shown) {
                String name = DojoService.nameOf(u);
                boolean elite = d.elites.contains(u);
                spec.buttonLeft("page:" + PAGE_MEMBER + u, UltraStyle.buttonText(name + (elite ? " (elite)" : "")),
                        UltraStyle.STYLE_NORMAL);
                if (elite) {
                    // 4.7.73: a demote is drawn as danger.
                    spec.buttonRight("do:dojo_demote:" + u, UltraStyle.danger("Step down"), UltraStyle.STYLE_DANGER);
                } else if (d.elites.size() >= DojoCatalog.MAX_ELITES) {
                    spec.lockedButtonRight("Make elite - 2 already");
                } else {
                    spec.buttonRight("do:dojo_promote:" + u, "Make elite", UltraStyle.STYLE_NORMAL);
                }
                spec.rowGap();
            }
        } else {
            int placed = 0;
            for (String u : shown) {
                String label = UltraStyle.buttonText(DojoService.nameOf(u) + (d.elites.contains(u) ? " (elite)" : ""));
                if (placed % 2 == 0) {
                    spec.lockedButton(label);
                } else {
                    spec.lockedButtonRight(label).rowGap();
                }
                placed++;
            }
            if (placed % 2 == 1) {
                spec.rowGap();
            }
        }
        pager(spec, PAGE_MEMBERS, pg, pages);
        spec.buttonLeft("page:dojo_previous_members", "Previous members", UltraStyle.STYLE_NORMAL);
        spec.buttonRight("page:mentor", "Training bonds", UltraStyle.STYLE_NORMAL).rowGap();
        spec.back();
        spec.send(player);
    }

    /** 4.7.84 (DJ-2): one disciple (master only): elite or not, war rights for an elite, time in the dojo. */
    static void openMember(ServerPlayer player, String uuid, String notice) {
        Dojo d = viewed(player);
        String u = uuid == null ? "" : uuid.trim();
        UltraScreenSpec spec = dojoSub(PAGE_MEMBER + u, notice);
        if (d == null || !DojoService.isMaster(player.m_20148_(), d) || !DojoService.disciples(d).contains(u)) {
            spec.line(UltraStyle.LABEL + "Only the master manages members, and only their own disciples.")
                    .buttonsAfterLines();
            spec.back();
            spec.send(player);
            return;
        }
        String name = DojoService.nameOf(u);
        boolean elite = d.elites.contains(u);
        Long joined = d.joinedAt.get(u);
        long inDojo = joined == null || joined <= 0L ? -1L : System.currentTimeMillis() - joined;
        spec.line(UltraStyle.header(name + (elite ? " (elite)" : "")))
                .line(UltraStyle.labelValue("Title", "Disciple of " + d.name))
                .line(UltraStyle.labelValue("In the dojo", inDojo < 0L ? "since the dojo began"
                        : DojoService.daysText(Math.max(60_000L, inDojo))));
        if (elite) {
            spec.line(UltraStyle.LABEL + "An elite's war needs your sign-off unless you give them war rights.");
        } else {
            spec.line(UltraStyle.LABEL + "An elite needs " + (DojoCatalog.ELITE_MIN_MS / DojoService.DAY_MS)
                    + " days in the dojo first. Elites can buy upgrades and declare wars.");
        }
        spec.buttonsAfterLines();
        if (elite) {
            spec.buttonLeft("do:dojo_demote:" + u, UltraStyle.danger("Step down"), UltraStyle.STYLE_DANGER);
            spec.buttonRight("do:dojo_warrights:" + u, UltraStyle.toggle("War rights", d.warRights.contains(u)),
                    UltraStyle.STYLE_NORMAL).rowGap();
        } else if (d.elites.size() >= DojoCatalog.MAX_ELITES) {
            spec.lockedButton("Make elite - 2 already");
            spec.lockedButtonRight("War rights - elites only").rowGap();
        } else {
            spec.buttonLeft("do:dojo_promote:" + u, "Make elite", UltraStyle.STYLE_NORMAL);
            spec.lockedButtonRight("War rights - elites only").rowGap();
        }
        spec.back();
        spec.send(player);
    }

    // ------------------------------------------------------------------ wars

    static void openWars(ServerPlayer player, String notice) {
        DojoWar.prune();
        Dojo d = viewed(player);
        UltraScreenSpec spec = dojoBase(TAB_WARS, TAB_WARS, notice);
        if (d == null) {
            noDojo(spec, player, "Wars are fought between dojos. Join or found one first.");
            return;
        }
        UUID me = player.m_20148_();
        boolean manage = DojoService.canManage(me, d);
        boolean master = DojoService.isMaster(me, d);
        // 4.7.84 (DJ-6): a war in sign-up is a card at the top: the countdown, who signed up, Join and Refresh.
        DojoStore.WarRecord signup = DojoWar.signupFor(d.masterUuid);
        if (signup != null) {
            boolean ours = signup.fromDojo.equals(d.masterUuid);
            String vs = ours ? signup.toName : signup.fromName;
            spec.line(UltraStyle.header("War starting!"))
                    .line(UltraStyle.VALUE + d.name + " vs " + vs + UltraStyle.LABEL + " - "
                            + ("GAUNTLET".equals(signup.format) ? "gauntlet" : "bracket") + ". Sign-up closes in "
                            + BoardSpar.clock(DojoWar.signupLeftMs(signup.id)) + ".")
                    .line(UltraStyle.LABEL + "Signed up: " + DojoWar.signedCount(signup.id, d.masterUuid)
                            + " from your dojo, " + DojoWar.signedCount(signup.id, ours ? signup.toDojo : signup.fromDojo)
                            + " from theirs. With one fighter each it's a single bout; with none it's called off.");
        }
        spec.line(UltraStyle.labelValue("Record", d.warWins + " won, " + d.warLosses + " lost") + UltraStyle.LABEL
                + " - a war is a knockout tournament between two dojos, won bout by bout by KO or ring-out.");
        // GuiKit 4.7.81: the buttons carry short action labels (a 24-character dojo name cut "Accept vs ..."), so the
        // open wars are named in lines, in the same order as their button rows. 4.7.84: at most 2 open wars and 3 past.
        List<DojoStore.WarRecord> wars = DojoWar.warsOf(d.masterUuid);
        List<DojoStore.WarRecord> open = new ArrayList<>();
        List<DojoStore.WarRecord> past = new ArrayList<>();
        // With the sign-up card on top, one open war and two past wars, so the page stays within its height.
        int openCap = signup == null ? 2 : 1;
        int pastCap = signup == null ? 3 : 2;
        for (DojoStore.WarRecord w : wars) {
            if (signup != null && w.id.equals(signup.id)) {
                continue;                                   // the card above shows it
            }
            if ("PENDING_SIGNOFF".equals(w.status) || "CHALLENGED".equals(w.status) || "RUNNING".equals(w.status)) {
                if (open.size() < openCap) {
                    open.add(w);
                }
            } else if (past.size() < pastCap) {
                past.add(w);
            }
        }
        for (DojoStore.WarRecord w : open) {
            spec.line(UltraStyle.VALUE + openWarLine(w, d, manage, master));
        }
        if (!past.isEmpty()) {
            spec.line(UltraStyle.header("Past wars"));
            int i = 0;
            for (DojoStore.WarRecord w : past) {
                spec.line(UltraStyle.VALUE + (++i) + ". " + w.fromName + " vs " + w.toName + UltraStyle.LABEL + " - "
                        + warStatus(w));
            }
        }
        if (!manage) {
            spec.line(UltraStyle.LABEL + "Only the master or an elite can declare or accept a war.");
        }
        spec.buttonsAfterLines();
        if (signup != null) {
            if (DojoWar.signedUp(signup.id, me)) {
                spec.lockedButton("Signed up");
            } else {
                spec.buttonLeft("do:" + DojoWar.ACT_JOIN + ":" + signup.id, UltraStyle.confirm("Join the war"),
                        UltraStyle.STYLE_CONFIRM);
            }
            spec.buttonRight("page:" + TAB_WARS, "Refresh", UltraStyle.STYLE_NORMAL).rowGap();
        }
        for (DojoStore.WarRecord w : open) {
            if ("RUNNING".equals(w.status)) {
                spec.lockedButton(DojoWar.signedUp(w.id, me) ? "Signed up" : "Sign-up has closed");
                spec.buttonRight("page:dojo_war_view:" + w.id, "View the bracket", UltraStyle.STYLE_NORMAL).rowGap();
            } else if ("CHALLENGED".equals(w.status) && w.toDojo.equals(d.masterUuid) && manage) {
                spec.buttonLeft("do:" + DojoWar.ACT_ACCEPT + ":" + w.id, UltraStyle.confirm("Accept the war"),
                        UltraStyle.STYLE_CONFIRM);
                spec.buttonRight("do:" + DojoWar.ACT_DECLINE + ":" + w.id, "Turn it down", UltraStyle.STYLE_NORMAL)
                        .rowGap();
            } else if ("PENDING_SIGNOFF".equals(w.status) && master) {
                spec.buttonLeft("do:" + DojoWar.ACT_SIGNOFF + ":" + w.id, UltraStyle.confirm("Sign off the war"),
                        UltraStyle.STYLE_CONFIRM);
                spec.buttonRight("do:" + DojoWar.ACT_CANCEL + ":" + w.id, "Call it off", UltraStyle.STYLE_NORMAL)
                        .rowGap();
            } else {
                spec.lockedButton("PENDING_SIGNOFF".equals(w.status) ? "Awaiting sign-off" : "Waiting on them");
                if (manage && w.fromDojo.equals(d.masterUuid)) {
                    spec.buttonRight("do:" + DojoWar.ACT_CANCEL + ":" + w.id, "Call it off", UltraStyle.STYLE_NORMAL);
                }
                spec.rowGap();
            }
        }
        // 4.7.84 (DJ-7): past wars open their own page (the bracket and the winner), in the same order as the lines.
        int placed = 0;
        for (DojoStore.WarRecord w : past) {
            String action = "page:dojo_war_view:" + w.id;
            if (placed % 2 == 0) {
                spec.buttonLeft(action, "View war " + (placed + 1), UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonRight(action, "View war " + (placed + 1), UltraStyle.STYLE_NORMAL).rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        if (manage) {
            spec.buttonLeft("page:dojo_war_pick", "Declare a war", UltraStyle.STYLE_NORMAL);
        } else {
            spec.lockedButton("Declare a war");
        }
        // 4.7.77: the dojo leaderboard by war wins, for every member.
        spec.buttonRight("page:dojo_list_wars", "Dojo leaderboard", UltraStyle.STYLE_NORMAL).rowGap();
        spec.back();
        spec.send(player);
    }

    /** One line for an open war: who it is against and what its button row does (same branches as openWars). */
    static String openWarLine(DojoStore.WarRecord w, Dojo d, boolean manage, boolean master) {
        boolean ours = w.fromDojo.equals(d.masterUuid);
        String vs = ours ? w.toName : w.fromName;
        if ("RUNNING".equals(w.status)) {
            return "In progress: " + vs + " - " + ("GAUNTLET".equals(w.format) ? "gauntlet" : "bracket");
        }
        if ("CHALLENGED".equals(w.status) && !ours) {
            return "Invite from " + vs + (manage ? " - accept or turn it down" : " - the master or an elite answers");
        }
        if ("PENDING_SIGNOFF".equals(w.status)) {
            return (master ? "Your elite declared on " + vs + " - needs your sign-off"
                    : "Declared on " + vs + " - waiting on the master's sign-off");
        }
        return "Declared on " + vs + " - waiting on them";
    }

    static String warStatus(DojoStore.WarRecord w) {
        return switch (w.status) {
            case "DONE" -> w.winnerName + " won " + w.fromScore + "-" + w.toScore;
            case "DECLINED" -> "turned down";
            case "EXPIRED" -> "expired";
            case "CANCELLED" -> "called off";
            default -> w.status.toLowerCase(Locale.ROOT);
        };
    }

    static void openWarPick(ServerPlayer player, int page, String notice) {
        Dojo mine = viewed(player);
        UltraScreenSpec spec = dojoSub(UltraPickHelper.pageArg("dojo_war_pick", page), notice);
        spec.line(UltraStyle.LABEL + "Pick the dojo to declare war on.");
        List<Dojo> all = DojoService.byClout();
        all.remove(mine);
        if (all.isEmpty()) {
            spec.line(UltraStyle.LABEL + "No other dojos are open right now, so there's nobody to fight yet.");
        }
        spec.buttonsAfterLines();
        int per = 8;
        int pages = Math.max(1, (all.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(page, pages - 1));
        int placed = 0;
        for (int i = pg * per; i < Math.min(all.size(), (pg + 1) * per); i++) {
            Dojo d = all.get(i);
            String action = "page:dojo_war_fmt:" + d.masterUuid;
            if (placed % 2 == 0) {
                spec.buttonLeft(action, UltraStyle.buttonText(d.name), UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonRight(action, UltraStyle.buttonText(d.name), UltraStyle.STYLE_NORMAL).rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        pager(spec, "dojo_war_pick", pg, pages);
        spec.back();
        spec.send(player);
    }

    static void openWarFormat(ServerPlayer player, String target, String notice) {
        Dojo them = DojoService.byMaster(target);
        UltraScreenSpec spec = dojoSub("dojo_war_fmt:" + target, notice);
        spec.line(UltraStyle.header("War on " + (them == null ? "a dojo" : them.name)))
                .line(UltraStyle.labelValue("Bracket", "single elimination; winners advance to the final"))
                .line(UltraStyle.labelValue("Gauntlet", "the winner stays on and faces the next fighter"))
                .line(UltraStyle.LABEL + "Once they accept, members of both dojos have " + (DojoWar.SIGNUP_MS / 1000L)
                        + " seconds to sign up on the Wars tab. With one fighter each, the war is a single bout.")
                .buttonsAfterLines();
        spec.buttonLeft("do:" + DojoWar.ACT_DECLARE + ":" + target + ":BRACKET", UltraStyle.confirm("Declare: bracket"),
                UltraStyle.STYLE_CONFIRM);
        spec.buttonRight("do:" + DojoWar.ACT_DECLARE + ":" + target + ":GAUNTLET",
                UltraStyle.confirm("Declare: gauntlet"), UltraStyle.STYLE_CONFIRM).rowGap();
        spec.back();
        spec.send(player);
    }

    static void openWarView(ServerPlayer player, String warId, String notice) {
        UltraScreenSpec spec = dojoSub("dojo_war_view:" + warId, notice);
        DojoStore.WarRecord w = DojoWar.byId(warId);
        if (w == null) {
            spec.line(UltraStyle.LABEL + "That war is gone.");
        } else {
            for (String l : DojoWar.bracketLines(w)) {
                spec.line(l);
            }
        }
        spec.buttonsAfterLines();
        spec.button("page:dojo_war_view:" + warId, "Refresh", UltraStyle.STYLE_NORMAL).rowGap();
        spec.back();
        spec.send(player);
    }

    // ------------------------------------------------------------------ the dojo list and a dojo's profile

    static void openList(ServerPlayer player, int page, String notice) {
        openList(player, page, notice, false);
    }

    /**
     * The dojo list: by clout this season, or (4.7.77) by war wins. 4.7.84 (DJ-3): each row names the master and says
     * if they're online; the buttons are the dojos' names (2 a row, same order) and open the dojo's profile, where a
     * player can ask to join.
     */
    static void openList(ServerPlayer player, int page, String notice, boolean wars) {
        String base = wars ? "dojo_list_wars" : "dojo_list";
        UltraScreenSpec spec = dojoSub(UltraPickHelper.pageArg(base, page), notice);
        List<Dojo> all = wars ? DojoService.byWarWins() : DojoService.byClout();
        int per = 6;
        int pages = Math.max(1, (all.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(page, pages - 1));
        spec.line(UltraStyle.header((wars ? "Dojos by war wins" : "Dojos by clout this season")
                + (pages > 1 ? " - page " + (pg + 1) + " of " + pages : "")));
        if (all.isEmpty()) {
            spec.line(UltraStyle.LABEL + "No dojos yet. Be the first: found one for free.");
        } else {
            spec.line(UltraStyle.LABEL + "Tap a dojo to see it and ask to join.");
        }
        for (int i = pg * per; i < Math.min(all.size(), (pg + 1) * per); i++) {
            Dojo d = all.get(i);
            String score = wars ? d.warWins + (d.warWins == 1 ? " war win" : " war wins") : d.clout + " clout";
            int members = 1 + DojoService.disciples(d).size();
            spec.line(UltraStyle.VALUE + (i + 1) + ". " + d.name + UltraStyle.LABEL + " - " + d.masterName
                    + (masterOnline(player, d) ? " (online)" : "") + " - " + score + " - " + members
                    + (members == 1 ? " member" : " members"));
        }
        spec.buttonsAfterLines();
        if (all.isEmpty()) {
            if (DojoService.foundProblem(player) == null) {
                spec.button("page:" + DojoFounding.PAGE_NAME, UltraStyle.confirm("Found a dojo (free)"),
                        UltraStyle.STYLE_CONFIRM).rowGap();
            } else {
                spec.lockedButton("Found a dojo (free)").rowGap();
            }
        }
        if (wars) {
            spec.buttonLeft("page:dojo_list", "By clout", UltraStyle.STYLE_NORMAL);
            spec.lockedButtonRight("By war wins");
        } else {
            spec.lockedButton("By clout");
            spec.buttonRight("page:dojo_list_wars", "By war wins", UltraStyle.STYLE_NORMAL);
        }
        spec.rowGap();
        // Two dojo buttons a row (GuiKit 4.7.81), in list order.
        int placed = 0;
        for (int i = pg * per; i < Math.min(all.size(), (pg + 1) * per); i++) {
            Dojo d = all.get(i);
            String action = "page:" + PAGE_VIEW + d.masterUuid;
            String label = UltraStyle.buttonText((i + 1) + ". " + d.name);
            if (placed % 2 == 0) {
                spec.buttonLeft(action, label, UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonRight(action, label, UltraStyle.STYLE_NORMAL).rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        pager(spec, base, pg, pages);
        spec.back();
        spec.send(player);
    }

    static ServerPlayer masterPlayer(ServerPlayer viewer, Dojo d) {
        if (d == null || viewer == null || viewer.m_20194_() == null) {
            return null;
        }
        try {
            return viewer.m_20194_().m_6846_().m_11259_(UUID.fromString(d.masterUuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static boolean masterOnline(ServerPlayer viewer, Dojo d) {
        return masterPlayer(viewer, d) != null;
    }

    /**
     * 4.7.84 (DJ-3): a dojo's profile: the master (online or offline), style, members, clout and war record, then Ask to
     * join (dim with the reason when you can't), Visit dojo, and Declare war for a master or elite of another dojo.
     */
    static void openDojoView(ServerPlayer player, String master, String notice) {
        String m = master == null ? "" : master.trim();
        UltraScreenSpec spec = dojoSub(PAGE_VIEW + m, notice);
        Dojo d = DojoService.byMaster(m);
        if (d == null || !d.open) {
            spec.line(UltraStyle.LABEL + "That dojo is closed. Pick another one from the dojo list.").buttonsAfterLines();
            spec.button("page:dojo_list", "Browse dojos", UltraStyle.STYLE_NORMAL).rowGap();
            spec.back();
            spec.send(player);
            return;
        }
        boolean online = masterOnline(player, d);
        DojoCatalog.Style style = DojoCatalog.Style.of(d.style);
        DojoCatalog.Bonus bonus = DojoCatalog.bonus(d.bonus);
        int members = 1 + DojoService.disciples(d).size();
        spec.line(UltraStyle.header(d.name))
                .line(UltraStyle.labelValue("Master", d.masterName + (online ? " - online" : " - offline")))
                .line(UltraStyle.labelValue("Style", (style == null ? "not picked yet" : style.label)
                        + (bonus == null ? "" : " - bonus " + bonus.name())))
                .line(UltraStyle.labelValue("Members", members + " of " + (1 + DojoCatalog.disciplesAllowed(
                        DojoService.tier(d, DojoCatalog.CAPACITY)))))
                .line(UltraStyle.labelValue("Clout", d.clout + " this season"))
                .line(UltraStyle.labelValue("Wars", d.warWins + " won, " + d.warLosses + " lost"));
        String why = DojoService.joinProblem(player, d, online);
        Dojo mine = viewed(player);
        // 4.7.88: a dojo the player already belongs to (either of their two) shows no Ask to join.
        boolean member = DojoService.affiliation(player.m_20148_(), d) != null;
        if (why == null) {
            spec.line(UltraStyle.LABEL + "Ask to join sends " + d.masterName + " a bond invite. If they accept, you"
                    + " become their apprentice and this dojo is yours.");
        } else if ("master offline".equals(why)) {
            spec.line(UltraStyle.LABEL + d.masterName + " is offline. Ask to join works while they're online.");
        }
        spec.buttonsAfterLines();
        if (why == null) {
            spec.buttonWide("page:" + PAGE_JOIN_ASK + d.masterUuid, UltraStyle.confirm("Ask to join"),
                    UltraStyle.STYLE_CONFIRM, null).rowGap();
        } else if (!member) {
            spec.buttonWide(UltraGuiActions.NO_ACTION, UltraStyle.dim("Ask to join - " + why), UltraStyle.STYLE_DIM,
                    null).rowGap();
        }
        spec.buttonLeft("do:dojo_visit:" + d.masterUuid, "Visit dojo", UltraStyle.STYLE_NORMAL);
        if (mine != null && mine != d && !member) {
            if (DojoService.canManage(player.m_20148_(), mine)) {
                spec.buttonRight("page:dojo_war_fmt:" + d.masterUuid, "Declare war", UltraStyle.STYLE_NORMAL);
            } else {
                spec.lockedButtonRight("Declare war");
            }
        }
        spec.rowGap();
        spec.back();
        spec.send(player);
    }

    /** 4.7.84 (DJ-3): the ask before "Ask to join" sends the bond invite (the existing apprentice invite). */
    static void openJoinAsk(ServerPlayer player, String master, String notice) {
        String m = master == null ? "" : master.trim();
        UltraScreenSpec spec = dojoSub(PAGE_JOIN_ASK + m, notice);
        Dojo d = DojoService.byMaster(m);
        String why = d == null ? "dojo closed" : DojoService.joinProblem(player, d, masterOnline(player, d));
        if (d == null) {
            spec.line(UltraStyle.LABEL + "That dojo is closed. Pick another one from the dojo list.").buttonsAfterLines();
            spec.back();
            spec.send(player);
            return;
        }
        spec.line(UltraStyle.header("Ask to join " + d.name + "?"))
                .line(UltraStyle.VALUE + "Ask " + d.masterName + " to take you as an apprentice? Their dojo becomes"
                        + " yours.")
                .line(UltraStyle.LABEL + "The invite lasts 1 hour. They answer on Training bonds > Bond invites.")
                .line(UltraStyle.LABEL + "Leaving a master later starts a 12-hour wait before you can ask another.")
                .buttonsAfterLines();
        if (why == null) {
            spec.button("do:" + ACT_JOIN + ":" + d.masterUuid, UltraStyle.confirm("Ask " + d.masterName),
                    UltraStyle.STYLE_CONFIRM).rowGap();
        } else {
            spec.buttonWide(UltraGuiActions.NO_ACTION, UltraStyle.dim("Ask to join - " + why), UltraStyle.STYLE_DIM,
                    null).rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /**
     * 4.7.85: a three-column cell on the legacy grid (98 px at 16 / 122 / 228), which the menu reflow widens to about
     * 129 px (above the 80 px minimum). {@code span} 2 takes this column and the next. A null {@code action} is the
     * dim, inert button for something the player can't do yet (shown, never hidden).
     */
    static void col3(UltraScreenSpec spec, int col, int span, String action, String label, String style) {
        int x = UltraStyle.COL_L + col * (COL3_W + UltraStyle.BTN_PAD);
        int w = COL3_W * span + UltraStyle.BTN_PAD * (span - 1);
        if (action == null) {
            spec.button(UltraGuiActions.NO_ACTION, UltraStyle.dim(UltraStyle.plain(label)), UltraStyle.STYLE_DIM, x,
                    spec.row(), w);
        } else {
            spec.button(action, label, style, x, spec.row(), w);
        }
    }

    static void col3(UltraScreenSpec spec, int col, String action, String label, String style) {
        col3(spec, col, 1, action, label, style);
    }

    /** Legacy width of a three-column cell. */
    static final int COL3_W = 98;
    /** Text room in a widened three-column cell (129 px less the button padding). */
    static final int COL3_LABEL_PX = UltraStyle.labelMaxWidth(129);

    /** The first label that fits a three-column cell, else the last one. */
    static String fitCol3(String... options) {
        for (String o : options) {
            if (UltraStyle.textWidthPx(o) <= COL3_LABEL_PX) {
                return o;
            }
        }
        return options[options.length - 1];
    }

    static void pager(UltraScreenSpec spec, String base, int pg, int pages) {
        if (pages <= 1) {
            return;
        }
        if (pg > 0) {
            spec.buttonLeft("page:" + UltraPickHelper.pageArg(base, pg - 1), "Previous", UltraStyle.STYLE_NORMAL);
        } else {
            spec.lockedButton("Previous");
        }
        if (pg + 1 < pages) {
            spec.buttonRight("page:" + UltraPickHelper.pageArg(base, pg + 1), "Next", UltraStyle.STYLE_NORMAL);
        } else {
            spec.lockedButtonRight("Next");
        }
        spec.rowGap();
    }

    // ------------------------------------------------------------------ actions

    /**
     * A dojo, Spar Board or war button. Applies it and reopens the right page with the result as a notice (menu
     * first). True when {@code action} was one of these.
     */
    public static boolean handle(ServerPlayer p, String action, String arg) {
        if (p == null || action == null) {
            return false;
        }
        String a = arg == null ? "" : arg.trim();
        if (BoardSpar.ACT_WAGER.equals(action)) {
            // 4.7.80: the old cycling Wager button opens the wager sub-menu.
            openWager(p, null);
            return true;
        }
        String board = BoardSpar.apply(p, action, a);
        if (board != null) {
            // 4.7.84 (SP-2): the post settings and the wager reopen Post a spar; a post that failed stays there with
            // the reason; everything else (posted, accepted, a challenge, a forfeit) goes to the board's first page.
            boolean compose = BoardSpar.ACT_MODE.equals(action) || BoardSpar.ACT_RANGE.equals(action)
                    || BoardSpar.ACT_WANT.equals(action) || BoardSpar.ACT_WAGER_SET.equals(action)
                    || (BoardSpar.ACT_POST.equals(action) && BoardSpar.postOf(p.m_20148_()) == null);
            if (compose) {
                openBoardPost(p, board);
            } else {
                openBoard(p, 0, board);
            }
            return true;
        }
        String war = DojoWar.apply(p, action, a);
        if (war != null) {
            openWars(p, war);
            return true;
        }
        String msg;
        String page;
        switch (action) {
            case "dojo_found" -> {
                // 4.7.78: founding is a flow (name, style, banner); the old one-tap action starts it.
                msg = null;
                page = DojoFounding.PAGE_NAME;
            }
            case "dojo_close" -> {
                msg = DojoService.close(p);
                page = "dojo";
            }
            case "dojo_reopen" -> {
                msg = DojoService.reopen(p);
                page = "dojo";
            }
            case "dojo_buy" -> {
                msg = DojoService.buy(p, a);
                page = "dojo_up:" + a;
            }
            case "dojo_style" -> {
                msg = DojoService.setStyle(p, a);
                page = "dojo_style";
            }
            case "dojo_bonus" -> {
                msg = DojoService.pickBonus(p, a);
                page = "dojo_style";
            }
            case "dojo_promote" -> {
                msg = DojoService.promote(p, a);
                page = PAGE_MEMBER + a;
            }
            case "dojo_demote" -> {
                msg = DojoService.demote(p, a);
                page = PAGE_MEMBER + a;
            }
            case "dojo_warrights" -> {
                msg = DojoService.toggleWarRights(p, a);
                page = PAGE_MEMBER + a;
            }
            case ACT_JOIN -> {
                // 4.7.84 (DJ-3): the existing apprentice invite to the dojo's master (online only).
                Dojo d = DojoService.byMaster(a);
                ServerPlayer m = masterPlayer(p, d);
                String why = d == null ? "dojo closed" : DojoService.joinProblem(p, d, m != null);
                msg = why != null ? "You can't ask to join right now: " + why + "."
                        : UltraStyle.plain(SparringSystem.apprenticeInvite(p, m));
                page = PAGE_VIEW + a;
            }
            case "dojo_visit" -> {
                // 4.7.88: visiting one of your own dojos also makes it the one the Dojo pages show.
                DojoService.view(p.m_20148_(), a);
                msg = DojoWorld.visit(p, DojoService.byMaster(a));
                page = "dojo";
            }
            case ACT_VIEW -> {
                // 4.7.88: only a dojo the player belongs to; anything else stays on Your dojos with the reason.
                if (DojoService.view(p.m_20148_(), a)) {
                    msg = null;
                    page = "dojo";
                } else {
                    msg = "You're not in that dojo any more. Pick one from the list.";
                    page = PAGE_MINE;
                }
            }
            case "dojo_spectate" -> {
                msg = DojoWorld.spectate(p, DojoService.byMaster(a));
                page = "watch";
            }
            case "dojo_leave" -> {
                msg = DojoWorld.leave(p);
                page = "dojo";
            }
            default -> {
                return false;
            }
        }
        open(p, page, msg);
        return true;
    }
}
