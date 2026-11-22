package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * LM 4.7.63: the 3-step Difficulty guide shows once per account, then Difficulty opens straight to Home.
 *
 * <p>Completion lives in the player's LM record ({@link LmPlayerStore}, key {@value #KEY}), keyed by the account
 * UUID (not the character id), the same record family as the 4.7.62 menu skin. Every character slot shares it,
 * and a slot switch, race change or DMZ reset never touches it. Missing key = not done, so every player
 * (including players from before this build) sees the guide on their first Difficulty open, once.
 *
 * <p>Finishing ("Got it") and Skip both mark it done. Replaying it from Home ("How difficulty works") opens the
 * guide pages by their own ids and never clears the mark. Routing is server side, so the native renderer and the
 * KaiPath bridge behave the same.
 */
public final class DifficultyTutorial {
    private DifficultyTutorial() {}

    public static final String KEY = "lm_difficulty_tutorial";

    /** Explicit guide pages: always the guide (replay), whatever the player's state. */
    public static final String PAGE_STEP1 = "guide";
    public static final String PAGE_STEP2 = "guide_tiers";
    public static final String PAGE_STEP3 = "guide_your_tier";
    /** Do-action on Skip (every step) and on "Got it" (Step 3): mark done, land on Home. */
    public static final String ACT_DONE = "guide_done";

    /** Where a Difficulty page id lands. */
    public enum View { STEP1, STEP2, STEP3, HOME, TIERS, OTHER }

    public static boolean done(ServerPlayer player) {
        return player != null && done(player.m_20148_());
    }

    public static boolean done(UUID account) {
        if (account == null) {
            return false;
        }
        CompoundTag root = LmPlayerStore.root(account);
        return root.m_128441_(KEY) && root.m_128469_(KEY).m_128471_("done");
    }

    /** Mark the guide done for the account and write the record now (survives a crash or relog right after). */
    public static void markDone(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID id = player.m_20148_();
        CompoundTag t = new CompoundTag();
        t.m_128379_("done", true);
        t.m_128356_("at", System.currentTimeMillis());
        LmPlayerStore.root(id).m_128365_(KEY, t);
        LmPlayerStore.flush(id);
    }

    /**
     * Pure routing rule. Explicit guide ids always open the guide (replay). Home ids and the Tiers / Your tier
     * ids follow the player's state: not done = the matching guide step, done = Home or the plain Tiers list.
     */
    public static View route(String page, boolean done) {
        String p = page == null ? "" : page.trim().toLowerCase(Locale.ROOT);
        switch (p) {
            case PAGE_STEP1, "how", "how_it_works", "tutorial":
                return View.STEP1;
            case PAGE_STEP2:
                return View.STEP2;
            case PAGE_STEP3:
                return View.STEP3;
            case "", "main", "home", "settings", "coins":
                return done ? View.HOME : View.STEP1;
            case "tiers", "buy", "tier", "purchase", "unlock", "adjust", "change", "set", "lower":
                return done ? View.TIERS : View.STEP2;
            case "your_tier", "yourtier", "momentum":
                return done ? View.HOME : View.STEP3;
            default:
                return View.OTHER;
        }
    }

    public static List<String> selfTest() {
        List<String> bad = new ArrayList<>();
        if (route("main", false) != View.STEP1 || route("", false) != View.STEP1 || route(null, false) != View.STEP1) {
            bad.add("a player who has not finished the guide opens Step 1");
        }
        if (route("main", true) != View.HOME || route("home", true) != View.HOME || route(null, true) != View.HOME) {
            bad.add("a player who finished or skipped the guide opens Home, never the guide");
        }
        if (route(PAGE_STEP1, true) != View.STEP1 || route(PAGE_STEP2, true) != View.STEP2
                || route(PAGE_STEP3, true) != View.STEP3) {
            bad.add("How difficulty works replays every step for a player who is done");
        }
        if (route("tiers", true) != View.TIERS || route("tiers", false) != View.STEP2
                || route("your_tier", true) != View.HOME || route("your_tier", false) != View.STEP3) {
            bad.add("Tiers / Your tier follow the guide state");
        }
        if (route("summons", false) != View.OTHER || route("tier_ask:3", true) != View.OTHER) {
            bad.add("other pages are never rerouted");
        }
        return bad;
    }
}
