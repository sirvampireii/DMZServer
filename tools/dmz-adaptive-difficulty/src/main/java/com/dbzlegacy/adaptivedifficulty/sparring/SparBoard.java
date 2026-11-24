package com.dbzlegacy.adaptivedifficulty.sparring;

import com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * LM 4.7.64 spar board helpers kept for the hub and the dashboard. Since 4.7.68 the board itself is the Spar Board
 * tab ({@link BoardSpar}): posts, direct challenges, ranked and friendly spars in dojo arenas. The 4.7.64 "list me"
 * and "ask" flow is gone; a spar starts only when someone accepts a board post.
 */
public final class SparBoard {
    private SparBoard() {}

    /** Posts {@code viewer} can see on the Spar Board right now. */
    public static List<BoardSpar.Post> looking(ServerPlayer viewer) {
        return viewer == null ? new ArrayList<>() : BoardSpar.board(viewer);
    }

    public static void onLogout(UUID id) {
        BoardSpar.onLogout(id);
    }

    /** {@code 14:59} for milliseconds left. */
    public static String clock(long ms) {
        return BoardSpar.clock(ms);
    }

    /** {@code x2.5} for a closeness multiplier. */
    public static String mult(float m) {
        double r = Math.round(m * 10.0) / 10.0;
        return "x" + (r == Math.rint(r) ? String.valueOf((long) r) : String.format(Locale.ROOT, "%s", r));
    }

    public static List<String> selfTest() {
        List<String> bad = new ArrayList<>();
        if (!"x2.5".equals(mult(2.5f)) || !"x3".equals(mult(3.0f)) || !"x1".equals(mult(1.0f))) {
            bad.add("match multiplier text");
        }
        if (!"15:00".equals(clock(15L * 60L * 1000L)) || !"0:01".equals(clock(1L))) {
            bad.add("board clock");
        }
        bad.addAll(BoardSpar.selfTest());
        bad.addAll(com.dbzlegacy.adaptivedifficulty.dojo.DojoService.selfTest());
        bad.addAll(com.dbzlegacy.adaptivedifficulty.dojo.DojoWar.selfTest());
        return bad;
    }
}
