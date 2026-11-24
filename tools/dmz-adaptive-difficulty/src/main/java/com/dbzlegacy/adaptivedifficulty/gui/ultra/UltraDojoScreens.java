package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoCatalog;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoService;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.Dojo;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoWar;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld;
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
 */
public final class UltraDojoScreens {
    private UltraDojoScreens() {}

    static final String TAB_BOARD = "sparboard";
    static final int BOARD_SHOWN = 5;

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
     * (style, elites, wars, leaderboard, founding, roster and bonds) opens here too. Pages this class does not own go
     * to the Sparring router, whose dojo pages (roster, members, training bonds) also draw on the Dojo screen.
     */
    public static void openSystem(ServerPlayer player, String page) {
        String raw = page == null || page.isBlank() ? TAB_DOJO_HOME : page.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.equals("main") || lower.equals(TAB_DOJO_HOME) || lower.equals("dojo") || lower.equals("dojo_home")) {
            openDojo(player, null);
        } else if (lower.equals("upgrades")) {
            openUpgrades(player, null);
        } else if (lower.equals("wars")) {
            openWars(player, null);
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
        if (lower.equals(TAB_BOARD) || lower.equals("board")) {
            openBoard(player, notice);
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
        } else if (DojoFounding.owns(lower)) {
            DojoFounding.open(player, raw, notice);
        } else if (lower.equals("dojo_upgrades")) {
            openUpgrades(player, notice);
        } else if (lower.startsWith("dojo_up:")) {
            openUpgrade(player, p.substring("dojo_up:".length()), notice);
        } else if (lower.equals("dojo_style")) {
            openStyle(player, notice);
        } else if (lower.startsWith("dojo_style_ask:")) {
            openStyleAsk(player, p.substring("dojo_style_ask:".length()));
        } else if (lower.startsWith("dojo_bonus_ask:")) {
            openBonusAsk(player, p.substring("dojo_bonus_ask:".length()));
        } else if (lower.equals("dojo_elites")) {
            openElites(player, notice);
        } else if (lower.equals("dojo_wars") || lower.equals("wars") || lower.equals("dojo_war")) {
            openWars(player, notice);
        } else if (lower.startsWith("dojo_war_pick")) {
            openWarPick(player, UltraPickHelper.pageIndex(lower), notice);
        } else if (lower.startsWith("dojo_war_fmt:")) {
            openWarFormat(player, p.substring("dojo_war_fmt:".length()), notice);
        } else if (lower.startsWith("dojo_war_view:")) {
            openWarView(player, p.substring("dojo_war_view:".length()), notice);
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

    static void openBoard(ServerPlayer player, String notice) {
        UltraScreenSpec spec = base(TAB_BOARD, TAB_BOARD, notice);
        UUID me = player.m_20148_();
        Dojo mine = DojoService.dojoOf(player);
        BoardSpar.Bout bout = BoardSpar.boutOf(me);
        if (bout != null) {
            String opp = me.equals(bout.a) ? bout.nameB : bout.nameA;
            Dojo host = DojoService.byMaster(bout.host);
            spec.line(UltraStyle.header("You're fighting"))
                    .line(UltraStyle.labelValue("Opponent", opp))
                    .line(UltraStyle.labelValue("Where", (host == null ? "a dojo" : host.name) + ", arena "
                            + (bout.arena + 1)))
                    .line(UltraStyle.labelValue("Time left", BoardSpar.clock(DojoCatalog.BOUT_TIMEOUT_MS
                            - (System.currentTimeMillis() - bout.startAt))))
                    .line(UltraStyle.LABEL + "Forfeiting gives the win away. Any wager is refunded to both.")
                    .buttonsAfterLines();
            spec.button("do:" + BoardSpar.ACT_FORFEIT, UltraStyle.danger("Forfeit this spar"), UltraStyle.STYLE_DANGER)
                    .rowGap();
            spec.back();
            spec.send(player);
            return;
        }
        if (mine == null) {
            spec.line(UltraStyle.header("Spar Board"))
                    .line(UltraStyle.VALUE + "Join a dojo first. No dojo, no spar.")
                    .line(UltraStyle.LABEL + "Spars happen in a dojo's arena. Join one, or found your own in the Dojo section.")
                    .buttonsAfterLines();
            // 4.7.80: founding lives only in the Dojo section; the board links there.
            spec.buttonLeft("page:dojo_list", "Find a dojo", UltraStyle.STYLE_NORMAL);
            spec.buttonRight("go:dojo|home", "Open the Dojo", UltraStyle.STYLE_NORMAL).rowGap();
            spec.lockedButton("Post a spar").rowGap();
            spec.back();
            spec.send(player);
            return;
        }
        BoardSpar.Draft d = BoardSpar.draft(me);
        int unranked = BoardSpar.unranked(me);
        boolean unlocked = BoardSpar.rankedUnlocked(me);
        BoardSpar.Post mineUp = BoardSpar.postOf(me);
        spec.line(UltraStyle.header("Your post"));
        if (mineUp != null) {
            spec.line(UltraStyle.VALUE + (mineUp.ranked() ? "Ranked" : "Friendly") + ", "
                    + BoardSpar.wagerText(mineUp.wagerKind(), mineUp.wager()) + ", "
                    + BoardSpar.rangeText(mineUp.range()) + ", looking for " + mineUp.want().label + (mineUp.target() != null
                    ? ", for " + mineUp.targetName() : "") + ". "
                    + BoardSpar.clock(BoardSpar.POST_MS - (System.currentTimeMillis() - mineUp.at())) + " left.");
        } else {
            spec.line(UltraStyle.LABEL + "Nothing posted. Fights happen in " + mine.name + "'s arena.");
        }
        spec.line(UltraStyle.LABEL + "Looking for Potential Unlock trains it in friendly spars. Ranked spars never do.");
        spec.line(UltraStyle.labelValue("Ranked", unlocked ? "unlocked" : unranked + " of " + DojoCatalog.RANKED_GATE
                + " friendly spars done, " + (DojoCatalog.RANKED_GATE - unranked) + " to go"));
        List<BoardSpar.Post> posts = BoardSpar.board(player);
        posts.removeIf(p -> p.poster().equals(me));
        spec.line(UltraStyle.header("On the board (" + posts.size() + ")"));
        if (posts.isEmpty()) {
            spec.line(UltraStyle.LABEL + "No posts yet. Post one, or challenge a player.");
        }
        int shown = Math.min(BOARD_SHOWN, posts.size());
        for (int i = 0; i < shown; i++) {
            BoardSpar.Post p = posts.get(i);
            int lvl = BoardSpar.levelOf(p.poster(), player.m_20194_());
            spec.line(UltraStyle.VALUE + p.name() + UltraStyle.LABEL + " Lv " + lvl + ", "
                    + (p.ranked() ? "ranked" : "friendly") + ", Friendly Fist "
                    + (BoardSpar.friendlyFist(p.poster(), player.m_20194_()) ? "on" : "off") + ", "
                    + BoardSpar.wagerText(p.wagerKind(), p.wager()) + ", looking for " + p.want().label + ", waiting "
                    + BoardSpar.clock(System.currentTimeMillis() - p.at())
                    + (me.equals(p.target()) ? UltraStyle.GREEN + " - challenges you" : ""));
        }
        spec.buttonsAfterLines();
        String mode = d.ranked() ? "Ranked" : "Friendly";
        if (!unlocked && !d.ranked()) {
            spec.lockedButton("Ranked: locked");
        } else {
            spec.buttonLeft("do:" + BoardSpar.ACT_MODE, "Kind - " + mode, UltraStyle.STYLE_NORMAL);
        }
        // 4.7.80: the wager is a sub-menu (kind, then amount).
        spec.buttonRight("page:" + PAGE_WAGER, "Wager - " + shortWager(d), UltraStyle.STYLE_NORMAL).rowGap();
        spec.buttonLeft("do:" + BoardSpar.ACT_RANGE, "Range - " + shortRange(d), UltraStyle.STYLE_NORMAL);
        if (mineUp != null) {
            spec.buttonRight("do:" + BoardSpar.ACT_UNPOST, "Take my post down", UltraStyle.STYLE_NORMAL).rowGap();
        } else {
            spec.buttonRight("do:" + BoardSpar.ACT_POST, UltraStyle.confirm("Post for 15 min"), UltraStyle.STYLE_CONFIRM)
                    .rowGap();
        }
        spec.buttonLeft("do:" + BoardSpar.ACT_WANT, "Looking for - " + d.want().label, UltraStyle.STYLE_NORMAL)
                .rowGap();
        int placed = 0;
        for (int i = 0; i < shown; i++) {
            BoardSpar.Post p = posts.get(i);
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
        spec.buttonLeft("page:pick_challenge", "Challenge a player", UltraStyle.STYLE_NORMAL);
        spec.buttonRight("page:watch", "Watch spars", UltraStyle.STYLE_NORMAL).rowGap();
        if (BoardSpar.result(me) != null) {
            spec.buttonLeft("page:result", "Last fight", UltraStyle.STYLE_NORMAL).rowGap();
        }
        spec.back();
        spec.send(player);
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
                .line(UltraStyle.LABEL + "A forfeit or the clock running out refunds both.");
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
                .line(UltraStyle.LABEL + "The winner takes both stakes. A forfeit or the clock running out refunds both.");
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
            String label = pts ? amt + " Dojo Points"
                    : com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.compactCost(amt);
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
                .line(UltraStyle.LABEL + "The winner takes both stakes. A forfeit or the clock running out refunds both.");
        if (why != null) {
            spec.line(UltraStyle.RED + why);
        }
        spec.buttonsAfterLines();
        if (why == null) {
            spec.button("do:" + BoardSpar.ACT_ACCEPT + ":" + post.poster(), UltraStyle.confirm("Accept and stake"),
                    UltraStyle.STYLE_CONFIRM).rowGap();
        } else {
            spec.lockedButton("Accept and stake").rowGap();
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
        spec.line(UltraStyle.LABEL + "Challenge one player to a " + (d.ranked() ? "ranked" : "friendly") + " spar ("
                + BoardSpar.wagerText(BoardSpar.wagerKind(d.wager()), BoardSpar.wagerAmount(d.wager())) + ").")
                .line(UltraStyle.LABEL + "Change kind and wager on the Spar Board first.");
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
        spec.line(UltraStyle.LABEL + "Watch from a dojo's spectator room. Only dojos that built one can be watched.");
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
        for (Dojo d : withBouts) {
            String label = "Watch " + d.name;
            if (DojoService.tier(d, DojoCatalog.SPECTATOR) <= 0) {
                spec.lockedButton(label + " (no room)").rowGap();
            } else {
                spec.button("do:dojo_spectate:" + d.masterUuid, label, UltraStyle.STYLE_NORMAL).rowGap();
            }
        }
        if (DojoWorld.spectating(player.m_20148_())) {
            spec.button("do:dojo_leave", "Stop watching", UltraStyle.STYLE_NORMAL).rowGap();
        }
        spec.back();
        spec.send(player);
    }

    // ------------------------------------------------------------------ Dojo tab

    static void openDojo(ServerPlayer player, String notice) {
        UltraScreenSpec spec = dojoBase(TAB_DOJO_HOME, TAB_DOJO_HOME, notice);
        UUID me = player.m_20148_();
        Dojo d = DojoService.dojoOf(player);
        Dojo own = DojoStore.get().dojos.get(me.toString());
        if (d == null) {
            spec.line(UltraStyle.header("No dojo yet"));
            if (own != null && !own.open) {
                spec.line(UltraStyle.VALUE + own.name + " is closed.")
                        .line(UltraStyle.LABEL + "Reopening keeps your members and style, but every upgrade drops one tier.")
                        .buttonsAfterLines();
                spec.button("do:dojo_reopen", UltraStyle.confirm("Reopen " + own.name), UltraStyle.STYLE_CONFIRM).rowGap();
            } else {
                spec.line(UltraStyle.VALUE + "A dojo is your home for spars, wars and training.")
                        .line(UltraStyle.LABEL + "Found your own (free, you become its master), or join one.")
                        .line(UltraStyle.LABEL + "Founding asks for a name, a style and a banner you made at a loom.")
                        .buttonsAfterLines();
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
        int members = 1 + DojoService.disciples(d).size();
        spec.line(UltraStyle.header(d.name))
                .line(UltraStyle.labelValue("Master", d.masterName + (master ? " (you)" : "")))
                .line(UltraStyle.labelValue("Style", style == null ? "not picked yet" : style.label))
                .line(UltraStyle.labelValue("Bonus", bonus == null ? "none" : bonus.name()))
                .line(UltraStyle.labelValue("Dojo Points", String.valueOf(d.points)))
                .line(UltraStyle.labelValue("Clout this season", String.valueOf(d.clout)))
                .line(UltraStyle.labelValue("Members", members + " of "
                        + (1 + DojoCatalog.disciplesAllowed(DojoService.tier(d, DojoCatalog.CAPACITY)))))
                .line(UltraStyle.labelValue("Hall", hallLine(d)))
                .line(UltraStyle.labelValue("Wars", d.warWins + " won, " + d.warLosses + " lost"));
        if (!d.elites.isEmpty()) {
            List<String> n = new ArrayList<>();
            for (String e : d.elites) {
                n.add(DojoService.nameOf(e));
            }
            spec.line(UltraStyle.labelValue("Elites", String.join(", ", n)));
        }
        DojoFounding.bannerLines(spec, d);
        hallPlan(spec, player, d, List.of(UltraStyle.LABEL + "Upgrades build into your hall."));
        spec.buttonsAfterLines();
        // 4.7.78: Upgrades (with style and bonus) and Wars are tabs now; Home keeps the dojo, its people and bonds.
        DojoFounding.identityButtons(spec, player, d, master);
        spec.buttonLeft("page:dojo_roster", "Roster and bonds", UltraStyle.STYLE_NORMAL);
        spec.buttonRight("page:dojo_elites", "Members and elites", UltraStyle.STYLE_NORMAL).rowGap();
        spec.buttonLeft("page:mentor", "Training bonds", UltraStyle.STYLE_NORMAL);
        if (DojoWorld.inDojoWorld(player) && DojoWorld.hasSpot(me)) {
            spec.buttonRight("do:dojo_leave", "Leave dojo", UltraStyle.STYLE_NORMAL).rowGap();
        } else {
            spec.buttonRight("do:dojo_visit:" + d.masterUuid, "Visit dojo", UltraStyle.STYLE_NORMAL).rowGap();
        }
        spec.buttonLeft("page:dojo_list", "Dojo leaderboard", UltraStyle.STYLE_NORMAL);
        if (master) {
            spec.buttonRight("page:dojo_close", UltraStyle.danger("Close dojo"), UltraStyle.STYLE_DANGER).rowGap();
        } else {
            spec.rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /** The Dojo Home with a result line on top (founding, banner, rename). */
    static void openDojoNotice(ServerPlayer player, String notice) {
        openDojo(player, notice);
    }

    static void openCloseConfirm(ServerPlayer player) {
        Dojo d = DojoService.dojoOf(player);
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

    static void openUpgrades(ServerPlayer player, String notice) {
        Dojo d = DojoService.dojoOf(player);
        UltraScreenSpec spec = dojoBase(TAB_UPGRADES, TAB_UPGRADES, notice);
        if (d == null) {
            spec.line(UltraStyle.LABEL + "Join or found a dojo first.").buttonsAfterLines();
            spec.back();
            spec.send(player);
            return;
        }
        spec.line(UltraStyle.labelValue("Dojo Points", String.valueOf(d.points)));
        if (com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesPictures(player)) {
            hallPlan(spec, player, d, List.of(UltraStyle.LABEL + "Earn points by hosting", UltraStyle.LABEL
                    + "spars, winning war bouts", UltraStyle.LABEL + "and training disciples.", UltraStyle.LABEL
                    + "Each upgrade builds into", UltraStyle.LABEL + "your hall. Tap one to see", UltraStyle.LABEL
                    + "it before you buy."));
        } else {
            spec.line(UltraStyle.LABEL + "Earn points by hosting spars, winning war bouts and training disciples.")
                    .line(UltraStyle.LABEL + "Each upgrade builds into your hall. Tap one to see it before you buy.");
            for (String l : DojoWorld.mapLines(d, "", false)) {
                spec.line(l);
            }
        }
        spec.buttonsAfterLines();
        int placed = 0;
        for (String up : DojoCatalog.UPGRADES) {
            int t = DojoService.tier(d, up);
            int cost = DojoCatalog.cost(up, t);
            String label = DojoCatalog.upgradeName(up) + (cost < 0 ? " (max)" : " - " + cost);
            boolean left = placed % 2 == 0;
            if (left) {
                spec.buttonLeft("page:dojo_up:" + up, label, UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonRight("page:dojo_up:" + up, label, UltraStyle.STYLE_NORMAL).rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        // 4.7.78: style and bonus live on the Upgrades tab.
        spec.button("page:dojo_style", "Style and bonus", UltraStyle.STYLE_NORMAL).rowGap();
        spec.back();
        spec.send(player);
    }

    /** Body rows the upgrade pictures take (Now, After, Where side by side). */
    static final int PICTURE_ROWS = 10;

    static String rings(int n) {
        return n == 1 ? "1 ring" : n + " rings";
    }

    /** The hall as it is now, in words: its look and its rings. */
    static String hallLine(Dojo d) {
        int rings = DojoService.arenas(d);
        return DojoCatalog.lookName(DojoCatalog.lookTier(DojoCatalog.ARENA, rings)) + ", " + (rings == 1
                ? "1 sparring mat" : rings + " of " + DojoCatalog.MAX_ARENAS + " rings");
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
            spec.pictures(text, 7, 400, plan);
        } else {
            for (String l : text) {
                spec.line(l);
            }
        }
    }

    /** One upgrade: what it builds (Now, After, Where on LM clients; the text map otherwise), the cost, buy, visit. */
    static void openUpgrade(ServerPlayer player, String up, String notice) {
        Dojo d = DojoService.dojoOf(player);
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
        spec.line(UltraStyle.header(DojoCatalog.upgradeName(up)))
                .line(UltraStyle.VALUE + DojoCatalog.upgradeWhat(up));
        if (DojoCatalog.ARENA.equals(up)) {
            spec.line(UltraStyle.labelValue("Now", rings(t) + ", " + DojoCatalog.lookName(DojoCatalog.lookTier(up, t))
                    + " hall"));
            if (!maxed) {
                spec.line(UltraStyle.labelValue("After", rings(t + 1) + ", "
                        + DojoCatalog.lookName(DojoCatalog.lookTier(up, t + 1)) + " hall"));
                if (DojoCatalog.lookTier(up, t + 1) > DojoCatalog.lookTier(up, t)) {
                    spec.line(UltraStyle.LABEL + "This ring refits the whole hall in the "
                            + DojoCatalog.lookName(DojoCatalog.lookTier(up, t + 1)) + " look.");
                }
            }
        } else if (DojoCatalog.CAPACITY.equals(up)) {
            spec.line(UltraStyle.labelValue("Now", DojoCatalog.disciplesAllowed(t) + " disciples"));
            if (!maxed) {
                spec.line(UltraStyle.labelValue("After", DojoCatalog.disciplesAllowed(t + 1) + " disciples"));
            }
        } else {
            spec.line(UltraStyle.labelValue("Now", DojoCatalog.lookName(DojoCatalog.lookTier(up, t))));
            if (!maxed) {
                spec.line(UltraStyle.labelValue("After", DojoCatalog.lookName(DojoCatalog.lookTier(up, t + 1))
                        + " look"));
            }
        }
        com.google.gson.JsonArray pics = maxed || !com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesPictures(player)
                ? new com.google.gson.JsonArray()
                : com.dbzlegacy.adaptivedifficulty.dojo.DojoPictures.upgrade(com.dbzlegacy.adaptivedifficulty.dojo.DojoState.of(d), up);
        if (pics.size() > 0) {
            // LM clients: what this builds before and after, and where it goes in the hall (green)
            List<String> text = new ArrayList<>();
            text.add(UltraStyle.LABEL + "Map: the starred part is the one this builds.");
            text.addAll(DojoWorld.mapLines(d, up, true));
            spec.pictures(text, PICTURE_ROWS, 0, pics);
        } else if (!DojoCatalog.CAPACITY.equals(up)) {
            spec.line(UltraStyle.LABEL + "Map: the starred part is the one this builds.");
            for (String l : DojoWorld.mapLines(d, up, !maxed)) {
                spec.line(l);
            }
        }
        spec.line(UltraStyle.labelValue("Cost", maxed ? "already at its best" : cost + " Dojo Points (you have "
                + d.points + ")"));
        if (!maxed) {
            spec.line(UltraStyle.LABEL + "Points are spent at once. An upgrade can't be undone.");
        }
        boolean canBuy = !maxed && DojoService.canManage(player.m_20148_(), d) && d.points >= cost;
        if (!maxed && !DojoService.canManage(player.m_20148_(), d)) {
            spec.line(UltraStyle.LABEL + "Only the master or an elite can buy upgrades.");
        } else if (!maxed && d.points < cost) {
            spec.line(UltraStyle.LABEL + "Your dojo needs " + (cost - d.points) + " more points.");
        }
        spec.buttonsAfterLines();
        if (canBuy) {
            spec.buttonLeft("do:dojo_buy:" + up, UltraStyle.confirm("Buy for " + cost + " points"),
                    UltraStyle.STYLE_CONFIRM);
        } else {
            spec.lockedButton(maxed ? "Fully upgraded" : "Buy for " + cost + " points");
        }
        spec.buttonRight("do:dojo_visit:" + d.masterUuid, "Visit dojo", UltraStyle.STYLE_NORMAL).rowGap();
        spec.back();
        spec.send(player);
    }

    static void openStyle(ServerPlayer player, String notice) {
        Dojo d = DojoService.dojoOf(player);
        UltraScreenSpec spec = dojoSub("dojo_style", notice);
        if (d == null) {
            spec.line(UltraStyle.LABEL + "Join or found a dojo first.").buttonsAfterLines();
            spec.back();
            spec.send(player);
            return;
        }
        boolean master = DojoService.isMaster(player.m_20148_(), d);
        DojoCatalog.Style style = DojoCatalog.Style.of(d.style);
        spec.line(UltraStyle.labelValue("Style", style == null ? "not picked yet" : style.label + " (" + style.covers
                + ")"))
                .line(UltraStyle.LABEL + "Members get the bonus when they fight in the dojo's style.")
                .line(UltraStyle.LABEL + "Changing style ends the current bonus.");
        DojoCatalog.Bonus active = DojoCatalog.bonus(d.bonus);
        spec.line(UltraStyle.labelValue("Bonus", active == null ? "none" : active.name() + ": " + active.description()));
        long cd = DojoService.bonusCooldownLeft(d);
        if (cd > 0L) {
            spec.line(UltraStyle.LABEL + "The bonus can change again in " + (cd / DojoService.DAY_MS + 1) + " days.");
        }
        spec.line(UltraStyle.labelValue("A bonus costs", DojoCatalog.BONUS_COST + " Dojo Points (you have " + d.points
                + ")"));
        if (!master) {
            spec.line(UltraStyle.LABEL + "Only the master picks the style and the bonus.");
        }
        spec.buttonsAfterLines();
        int placed = 0;
        for (DojoCatalog.Style s : DojoCatalog.Style.values()) {
            boolean left = placed % 2 == 0;
            String label = (s == style ? "Using " : "") + s.label;
            if (!master || s == style) {
                if (left) {
                    spec.lockedButton(label);
                } else {
                    spec.lockedButtonRight(label);
                }
            } else {
                // 4.7.78: the first pick (an older dojo with no style) is free; a change costs Dojo Points, shares
                // the 12-hour identity cooldown and goes through the ask page (which also warns about the bonus).
                boolean first = d.style == null || d.style.isBlank();
                long idCd = DojoService.identityCooldownLeft(d);
                String act = first ? "do:dojo_style:" + s.name() : "page:dojo_style_ask:" + s.name();
                String lockedLabel = first ? null : idCd > 0L ? s.label + " - " + DojoService.cooldownText(idCd)
                        : d.points < DojoCatalog.STYLE_CHANGE_COST
                        ? s.label + " - needs " + DojoCatalog.STYLE_CHANGE_COST : null;
                if (lockedLabel != null) {
                    if (left) {
                        spec.lockedButton(lockedLabel);
                    } else {
                        spec.lockedButtonRight(lockedLabel);
                    }
                } else if (left) {
                    spec.buttonLeft(act, label, UltraStyle.STYLE_NORMAL);
                } else {
                    spec.buttonRight(act, label, UltraStyle.STYLE_NORMAL);
                }
            }
            if (!left) {
                spec.rowGap();
            }
            placed++;
        }
        if (style != null) {
            placed = 0;
            for (DojoCatalog.Bonus b : DojoCatalog.bonuses(style)) {
                boolean left = placed % 2 == 0;
                boolean can = master && !b.id().equals(d.bonus) && cd <= 0L && d.points >= DojoCatalog.BONUS_COST;
                String label = (b.id().equals(d.bonus) ? "Active: " : "") + b.name();
                if (!can) {
                    if (left) {
                        spec.lockedButton(label);
                    } else {
                        spec.lockedButtonRight(label);
                    }
                } else if (left) {
                    spec.buttonLeft("page:dojo_bonus_ask:" + b.id(), label, UltraStyle.STYLE_NORMAL);
                } else {
                    spec.buttonRight("page:dojo_bonus_ask:" + b.id(), label, UltraStyle.STYLE_NORMAL);
                }
                if (!left) {
                    spec.rowGap();
                }
                placed++;
            }
            if (placed % 2 == 1) {
                spec.rowGap();
            }
        }
        spec.back();
        spec.send(player);
    }

    /** 4.7.73: changing style ends the paid bonus; say what is lost before the click. */
    static void openStyleAsk(ServerPlayer player, String styleId) {
        Dojo d = DojoService.dojoOf(player);
        DojoCatalog.Style to = DojoCatalog.Style.of(styleId);
        UltraScreenSpec spec = dojoSub("dojo_style_ask:" + styleId, null);
        if (d == null || to == null || !DojoService.isMaster(player.m_20148_(), d)) {
            spec.line(UltraStyle.LABEL + "Only your dojo's master can change its style.").buttonsAfterLines();
            spec.back();
            spec.send(player);
            return;
        }
        DojoCatalog.Bonus active = DojoCatalog.bonus(d.bonus);
        spec.line(UltraStyle.header("Change style to " + to.label + "?"))
                .line(UltraStyle.labelValue("New style", to.label + " (" + to.covers + ")"));
        if (active != null) {
            spec.line(UltraStyle.dangerLine("The " + active.name() + " bonus ends now."))
                    .line(UltraStyle.LABEL + "Its " + DojoCatalog.BONUS_COST + " Dojo Points are not refunded.");
        }
        boolean first = d.style == null || d.style.isBlank();
        if (!first) {
            // 4.7.78: the change itself costs points and starts the shared cooldown.
            spec.line(UltraStyle.labelValue("This change costs", DojoCatalog.STYLE_CHANGE_COST + " Dojo Points (you have "
                    + d.points + ")"))
                    .line(UltraStyle.LABEL + "After it, the name, style and banner can't change for 12 hours.");
        }
        spec.line(UltraStyle.labelValue("A new bonus costs", DojoCatalog.BONUS_COST + " Dojo Points"))
                .line(UltraStyle.LABEL + "You can pick the new bonus right away.")
                .buttonsAfterLines();
        String blocked = first ? null : DojoService.identityChangeProblem(d, DojoCatalog.STYLE_CHANGE_COST);
        if (blocked != null) {
            long cd = DojoService.identityCooldownLeft(d);
            spec.lockedButton(cd > 0L ? "Changes again in " + DojoService.cooldownText(cd)
                    : "Needs " + DojoCatalog.STYLE_CHANGE_COST + " points").rowGap();
        } else if (active != null) {
            spec.button("do:dojo_style:" + to.name(), UltraStyle.danger("Change style, end the bonus"),
                    UltraStyle.STYLE_DANGER).rowGap();
        } else {
            spec.button("do:dojo_style:" + to.name(), UltraStyle.confirm(first ? "Use this style"
                    : "Change style for " + DojoCatalog.STYLE_CHANGE_COST + " points"), UltraStyle.STYLE_CONFIRM).rowGap();
        }
        spec.back();
        spec.send(player);
    }

    /** 4.7.73: a bonus spends Dojo Points and locks the choice for a week; say both before the click. */
    static void openBonusAsk(ServerPlayer player, String bonusId) {
        Dojo d = DojoService.dojoOf(player);
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
        }
        spec.back();
        spec.send(player);
    }

    static void openElites(ServerPlayer player, String notice) {
        Dojo d = DojoService.dojoOf(player);
        UltraScreenSpec spec = dojoSub("dojo_elites", notice);
        if (d == null) {
            spec.line(UltraStyle.LABEL + "Join or found a dojo first.").buttonsAfterLines();
            spec.back();
            spec.send(player);
            return;
        }
        boolean master = DojoService.isMaster(player.m_20148_(), d);
        List<String> disciples = DojoService.disciples(d);
        spec.line(UltraStyle.labelValue("Members", (1 + disciples.size()) + " of "
                + (1 + DojoCatalog.disciplesAllowed(DojoService.tier(d, DojoCatalog.CAPACITY)))))
                .line(UltraStyle.LABEL + "Elites (2 at most) invite members, buy upgrades and declare wars.")
                .line(UltraStyle.LABEL + "An elite's war needs your sign-off unless you give them war rights.")
                .line(UltraStyle.LABEL + "An elite needs " + (DojoCatalog.ELITE_MIN_MS / DojoService.DAY_MS)
                        + " days in the dojo first.");
        if (disciples.isEmpty()) {
            spec.line(UltraStyle.LABEL + "No disciples yet. Invite players from Roster and bonds.");
        }
        spec.buttonsAfterLines();
        for (String u : disciples) {
            String name = DojoService.nameOf(u);
            boolean elite = d.elites.contains(u);
            if (!master) {
                spec.lockedButton(name + (elite ? " (elite)" : "")).rowGap();
                continue;
            }
            if (elite) {
                // 4.7.73: a demote is drawn as danger; the toggle reads "Label - ON/OFF" like every other toggle.
                spec.buttonLeft("do:dojo_demote:" + u, UltraStyle.danger("Step down " + name), UltraStyle.STYLE_DANGER);
                spec.buttonRight("do:dojo_warrights:" + u, UltraStyle.toggle("War rights", d.warRights.contains(u)),
                        UltraStyle.STYLE_NORMAL).rowGap();
            } else if (d.elites.size() >= DojoCatalog.MAX_ELITES) {
                spec.lockedButton("Make " + name + " elite").rowGap();
            } else {
                spec.button("do:dojo_promote:" + u, "Make " + name + " elite", UltraStyle.STYLE_NORMAL).rowGap();
            }
        }
        spec.back();
        spec.send(player);
    }

    // ------------------------------------------------------------------ wars

    static void openWars(ServerPlayer player, String notice) {
        DojoWar.prune();
        Dojo d = DojoService.dojoOf(player);
        UltraScreenSpec spec = dojoBase(TAB_WARS, TAB_WARS, notice);
        if (d == null) {
            spec.line(UltraStyle.LABEL + "Join or found a dojo to fight wars.").buttonsAfterLines();
            spec.back();
            spec.send(player);
            return;
        }
        boolean manage = DojoService.canManage(player.m_20148_(), d);
        spec.line(UltraStyle.header("Dojo wars"))
                .line(UltraStyle.LABEL + "A war is a knockout tournament: bracket or gauntlet, KO or ring-out wins.")
                .line(UltraStyle.labelValue("Record", d.warWins + " won, " + d.warLosses + " lost"));
        List<DojoStore.WarRecord> wars = DojoWar.warsOf(d.masterUuid);
        List<DojoStore.WarRecord> open = new ArrayList<>();
        int history = 0;
        for (DojoStore.WarRecord w : wars) {
            if ("PENDING_SIGNOFF".equals(w.status) || "CHALLENGED".equals(w.status) || "RUNNING".equals(w.status)) {
                open.add(w);
            } else if (history < 4) {
                spec.line(UltraStyle.VALUE + w.fromName + " vs " + w.toName + UltraStyle.LABEL + " - " + warStatus(w));
                history++;
            }
        }
        if (!manage) {
            spec.line(UltraStyle.LABEL + "Only the master or an elite can declare or accept a war.");
        }
        spec.buttonsAfterLines();
        for (DojoStore.WarRecord w : open) {
            String vs = w.fromDojo.equals(d.masterUuid) ? w.toName : w.fromName;
            if ("RUNNING".equals(w.status)) {
                if (DojoWar.signupOpen(w.id) && !DojoWar.signedUp(w.id, player.m_20148_())) {
                    spec.buttonLeft("do:" + DojoWar.ACT_JOIN + ":" + w.id, UltraStyle.confirm("Join the war"),
                            UltraStyle.STYLE_CONFIRM);
                } else {
                    spec.lockedButton(DojoWar.signedUp(w.id, player.m_20148_()) ? "Signed up" : "Sign-up closed");
                }
                spec.buttonRight("page:dojo_war_view:" + w.id, "Bracket vs " + vs, UltraStyle.STYLE_NORMAL).rowGap();
            } else if ("CHALLENGED".equals(w.status) && w.toDojo.equals(d.masterUuid) && manage) {
                spec.buttonLeft("do:" + DojoWar.ACT_ACCEPT + ":" + w.id, UltraStyle.confirm("Accept vs " + vs),
                        UltraStyle.STYLE_CONFIRM);
                spec.buttonRight("do:" + DojoWar.ACT_DECLINE + ":" + w.id, "Turn down", UltraStyle.STYLE_NORMAL)
                        .rowGap();
            } else if ("PENDING_SIGNOFF".equals(w.status) && DojoService.isMaster(player.m_20148_(), d)) {
                spec.buttonLeft("do:" + DojoWar.ACT_SIGNOFF + ":" + w.id, UltraStyle.confirm("Sign off vs " + vs),
                        UltraStyle.STYLE_CONFIRM);
                spec.buttonRight("do:" + DojoWar.ACT_CANCEL + ":" + w.id, "Call it off", UltraStyle.STYLE_NORMAL)
                        .rowGap();
            } else {
                spec.lockedButton(("PENDING_SIGNOFF".equals(w.status) ? "Awaiting sign-off: " : "Waiting on ") + vs);
                if (manage && w.fromDojo.equals(d.masterUuid)) {
                    spec.buttonRight("do:" + DojoWar.ACT_CANCEL + ":" + w.id, "Call it off", UltraStyle.STYLE_NORMAL);
                }
                spec.rowGap();
            }
        }
        if (manage) {
            spec.button("page:dojo_war_pick", "Declare a war", UltraStyle.STYLE_NORMAL).rowGap();
        } else {
            spec.lockedButton("Declare a war").rowGap();
        }
        // 4.7.77: the dojo leaderboard by war wins, for every member.
        spec.button("page:dojo_list_wars", "Dojo leaderboard", UltraStyle.STYLE_NORMAL).rowGap();
        spec.back();
        spec.send(player);
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
        Dojo mine = DojoService.dojoOf(player);
        UltraScreenSpec spec = dojoSub(UltraPickHelper.pageArg("dojo_war_pick", page), notice);
        spec.line(UltraStyle.LABEL + "Pick the dojo to declare war on.");
        List<Dojo> all = DojoService.byClout();
        all.remove(mine);
        if (all.isEmpty()) {
            spec.line(UltraStyle.LABEL + "There are no other open dojos yet.");
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
                spec.buttonLeft(action, d.name, UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonRight(action, d.name, UltraStyle.STYLE_NORMAL).rowGap();
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
                .line(UltraStyle.LABEL + "Members of both dojos sign up for one minute once they accept.")
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

    static void openList(ServerPlayer player, int page, String notice) {
        openList(player, page, notice, false);
    }

    /** The dojo leaderboard: by clout this season, or (4.7.77) by war wins. */
    static void openList(ServerPlayer player, int page, String notice, boolean wars) {
        String base = wars ? "dojo_list_wars" : "dojo_list";
        UltraScreenSpec spec = dojoSub(UltraPickHelper.pageArg(base, page), notice);
        List<Dojo> all = wars ? DojoService.byWarWins() : DojoService.byClout();
        spec.line(UltraStyle.header(wars ? "Dojo leaderboard (war wins)" : "Dojo leaderboard (clout this season)"));
        if (all.isEmpty()) {
            spec.line(UltraStyle.LABEL + "No dojos yet. Found the first one!");
        }
        int per = 6;
        int pages = Math.max(1, (all.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(page, pages - 1));
        for (int i = pg * per; i < Math.min(all.size(), (pg + 1) * per); i++) {
            Dojo d = all.get(i);
            DojoCatalog.Style s = DojoCatalog.Style.of(d.style);
            String score = wars ? d.warWins + (d.warWins == 1 ? " war win" : " war wins") : d.clout + " clout";
            spec.line(UltraStyle.VALUE + (i + 1) + ". " + d.name + UltraStyle.LABEL + " - " + score + ", "
                    + (1 + DojoService.disciples(d).size()) + " members" + (s == null ? "" : ", " + s.label));
        }
        spec.buttonsAfterLines();
        if (wars) {
            spec.buttonLeft("page:dojo_list", "By clout", UltraStyle.STYLE_NORMAL);
            spec.lockedButtonRight("By war wins");
        } else {
            spec.lockedButton("By clout");
            spec.buttonRight("page:dojo_list_wars", "By war wins", UltraStyle.STYLE_NORMAL);
        }
        spec.rowGap();
        for (int i = pg * per; i < Math.min(all.size(), (pg + 1) * per); i++) {
            Dojo d = all.get(i);
            spec.button("do:dojo_visit:" + d.masterUuid, "Visit " + d.name, UltraStyle.STYLE_NORMAL).rowGap();
        }
        pager(spec, base, pg, pages);
        spec.back();
        spec.send(player);
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
            openBoard(p, board);
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
                page = "dojo_elites";
            }
            case "dojo_demote" -> {
                msg = DojoService.demote(p, a);
                page = "dojo_elites";
            }
            case "dojo_warrights" -> {
                msg = DojoService.toggleWarRights(p, a);
                page = "dojo_elites";
            }
            case "dojo_visit" -> {
                msg = DojoWorld.visit(p, DojoService.byMaster(a));
                page = "dojo";
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
