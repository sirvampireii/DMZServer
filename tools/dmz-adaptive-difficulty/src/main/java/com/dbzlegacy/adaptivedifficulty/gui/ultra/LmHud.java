package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore;
import com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork;
import com.dbzlegacy.adaptivedifficulty.net.gui.NativeHudPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * LM 4.7.72 native HUD, server side (approved proposal {@code docs/LM_NATIVE_HUD_PROPOSAL.md}).
 *
 * <p>The server owns every element. For a player whose LM client listed {@code hud} and who has the HUD on
 * ({@link #active}), the Underdog bar, Rival Instinct chat lines, TP notices and the death TP penalty bar go to
 * the client as message 4 instead, with the same text and numbers; the Momentum meter is new. Everyone else keeps
 * the boss bars and chat lines exactly as before.
 *
 * <p>The HUD switch is per account, in the player's LM record under {@value #KEY} (one file per account, like the
 * menu look). Default on. Sends happen only on change: each element's last packet is remembered per player and an
 * identical one is skipped (toasts, which are events, always go).
 */
public final class LmHud {
    private LmHud() {}

    public static final String UNDERDOG = "underdog";
    public static final String INSTINCT = "instinct";
    public static final String TP = "tp";
    public static final String PENALTY = "penalty";
    public static final String MOMENTUM = "momentum";
    // 4.7.75 (docs/LM_HUD_EXPANSION_PROPOSAL.md): repeated chat moved to the HUD.
    /** Rival challenge, spectated challenge, spar session or dojo war bout: names, score, clock, range warning. */
    public static final String FIGHT = "fight";
    /** Spar chip: Perfect Training, combo, recover countdown. */
    public static final String SPAR = "spar";
    /** Potential progress and the "switch methods" / "move" hints. */
    public static final String POTENTIAL = "potential";
    /** Ancient Coins dropped, summed over {@link #COIN_WINDOW_MS}. */
    public static final String COINS = "coins";
    /** Elite Hunter / Boss Slayer / Godslayer sense. */
    public static final String ELITE = "elite";
    /** Global TP and mastery boosts with time left. */
    public static final String BOOST = "boost";
    public static final List<String> ELEMENTS = List.of(UNDERDOG, INSTINCT, TP, PENALTY, MOMENTUM, FIGHT, SPAR,
            POTENTIAL, COINS, ELITE, BOOST);
    /** Elements that are events: each send shows (or refreshes) the element, so identical sends still go. */
    static final java.util.Set<String> EVENTS = java.util.Set.of(INSTINCT, TP, COINS, POTENTIAL);
    /** The coin toast sums drops within this window. */
    public static final long COIN_WINDOW_MS = 5_000L;
    /** Potential meter fades this long after the last training hit. */
    public static final int POTENTIAL_MS = 8_000;

    /** Accents the client resolves in the player's menu skin. */
    public static final String ACCENT_RIVAL = "rival";
    public static final String ACCENT_DIFFICULTY = "difficulty";
    /** 4.7.78: currency gold, resolved per skin on the client ({@link MenuSkins#currencyGold}). */
    public static final String ACCENT_CURRENCY = "currency";

    /** A system's accent as {@code #rrggbb} (the same per-system colour menus use), for the HUD edge. */
    public static String accentOf(String system) {
        int rgb = SaiyanRoyal.accentHex(UltraStyle.systemAccentCode(system));
        return rgb < 0 ? "" : String.format("#%06x", rgb & 0xFFFFFF);
    }

    /** {@code m:ss} for HUD clocks. */
    public static String clock(long ms) {
        long s = Math.max(0L, (ms + 999L) / 1000L);
        return (s / 60L) + ":" + (s % 60L < 10L ? "0" : "") + (s % 60L);
    }

    /** More tab toggle action ({@code do:hud_toggle}). */
    public static final String ACTION_TOGGLE = "hud_toggle";
    /** How long an Instinct card stays (client fades it over the last half second). */
    public static final int INSTINCT_MS = 4_000;
    /** Momentum shows while banked and in a fight in the last this-many millis. */
    public static final long MOMENTUM_FIGHT_MS = 10_000L;

    static final String KEY = "lm_hud";

    private static final Map<UUID, Map<String, String>> LAST = new ConcurrentHashMap<>();
    private static volatile List<Sent> capture;

    /** One captured send (self-test and UI probe). */
    public record Sent(UUID player, NativeHudPacket packet) {}

    // ------------------------------------------------------------------ the switch

    /** The player's HUD switch (default on). */
    public static boolean enabled(UUID id) {
        if (id == null) {
            return true;
        }
        CompoundTag root = LmPlayerStore.root(id);
        if (!root.m_128441_(KEY)) {
            return true;
        }
        CompoundTag t = root.m_128469_(KEY);
        return !t.m_128441_("on") || t.m_128471_("on");
    }

    public static boolean enabled(ServerPlayer player) {
        return player == null || enabled(player.m_20148_());
    }

    /** Flip the switch and save it now. Turning it off hides every element, so the bars and chat come back. */
    public static boolean toggle(ServerPlayer player) {
        if (player == null) {
            return true;
        }
        boolean next = !enabled(player);
        set(player, next);
        return next;
    }

    public static void set(ServerPlayer player, boolean on) {
        if (player == null) {
            return;
        }
        UUID id = player.m_20148_();
        CompoundTag t = new CompoundTag();
        t.m_128379_("on", on);
        LmPlayerStore.root(id).m_128365_(KEY, t);
        LmPlayerStore.flush(id);
        if (!on) {
            hideAll(player);
        }
    }

    /** True when this player's elements go to the native HUD instead of boss bars and chat. */
    public static boolean active(ServerPlayer player) {
        return player != null && LmNativeNetwork.takesHud(player) && enabled(player);
    }

    /** More tab action: true when {@code action} was the HUD toggle. */
    public static boolean applyAction(ServerPlayer player, String action) {
        if (!ACTION_TOGGLE.equals(action)) {
            return false;
        }
        toggle(player);
        return true;
    }

    // ------------------------------------------------------------------ sends

    /**
     * Show (or update) one element. {@code progress} -1 means no bar; {@code durationMs} > 0 makes it hide itself
     * after that long, with {@code drain} draining its bar on the client.
     */
    public static void show(ServerPlayer player, String element, List<String> lines, float progress, String accent,
            String action, int durationMs, boolean drain) {
        if (player == null) {
            return;
        }
        NativeHudPacket p = new NativeHudPacket(element, true, lines, progress, accent, action, durationMs, drain);
        boolean event = EVENTS.contains(element);
        Map<String, String> last = LAST.computeIfAbsent(player.m_20148_(), k -> new ConcurrentHashMap<>());
        String sig = p.signature();
        if (!event && sig.equals(last.get(element))) {
            return;
        }
        last.put(element, sig);
        send(player, p);
    }

    /** Hide one element (sent only when it is showing). */
    public static void hide(ServerPlayer player, String element) {
        if (player == null) {
            return;
        }
        Map<String, String> last = LAST.get(player.m_20148_());
        if (last == null || last.remove(element) == null) {
            return;
        }
        send(player, NativeHudPacket.hide(element));
    }

    /** True when {@code element} was last sent as showing to this player. */
    public static boolean showing(ServerPlayer player, String element) {
        Map<String, String> last = player == null ? null : LAST.get(player.m_20148_());
        return last != null && last.containsKey(element);
    }

    /** Hide everything this player is shown (HUD switched off). */
    public static void hideAll(ServerPlayer player) {
        for (String e : ELEMENTS) {
            hide(player, e);
        }
    }

    /** Logout: forget what this player was shown (the client clears its HUD on logout too). */
    public static void forget(ServerPlayer player) {
        if (player != null) {
            LAST.remove(player.m_20148_());
            com.dbzlegacy.adaptivedifficulty.progression.skills.PotentialProgression.forgetHud(player.m_20148_());
            com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.forgetToast(player.m_20148_());
        }
    }

    private static void send(ServerPlayer player, NativeHudPacket p) {
        List<Sent> cap = capture;
        if (cap != null) {
            cap.add(new Sent(player.m_20148_(), p));
            if (!LmNativeNetwork.takesHud(player)) {
                return;
            }
        }
        LmNativeNetwork.sendHud(player, p);
    }

    /** Run {@code r} and return the HUD packets it sent (also sent for real to a client that takes them). */
    public static List<Sent> capture(Runnable r) {
        List<Sent> out = new CopyOnWriteArrayList<>();
        List<Sent> prev = capture;
        capture = out;
        try {
            r.run();
        } finally {
            capture = prev;
        }
        return new ArrayList<>(out);
    }

    // ------------------------------------------------------------------ text shared with the bars

    /** HUD lines that read, joined with " - ", exactly as {@code joined} (the bar or chat text). */
    public static String joined(List<String> lines) {
        return UltraStyle.plain(String.join(" - ", lines));
    }

    /** Boot self-test: the wire round trip and the change-only rule. Problems, empty when fine. */
    public static List<String> selfTest() {
        List<String> bad = new ArrayList<>();
        NativeHudPacket p = new NativeHudPacket(PENALTY, true, List.of("Effect: x0.5 - 9m 12s left"), 0.92f,
                ACCENT_DIFFICULTY, "open:difficulty", 0, false);
        io.netty.buffer.ByteBuf raw = io.netty.buffer.Unpooled.buffer();
        try {
            net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(raw);
            NativeHudPacket.encode(p, buf);
            NativeHudPacket q = NativeHudPacket.decode(buf);
            if (!p.signature().equals(q.signature())) {
                bad.add("hud packet round trip changed: " + q.signature());
            }
        } finally {
            raw.release();
        }
        NativeHudPacket five = new NativeHudPacket(TP, true, List.of("a", "b", "c", "d", "e"), 2f, "", "", -5, true);
        if (five.lines().size() != NativeHudPacket.MAX_LINES || five.progress() != 1f || five.durationMs() != 0) {
            bad.add("hud packet clamps lines, progress and duration");
        }
        if (!"Grounds - Legendary - Champion Kai - 3 battles - You are the champion".equals(joined(
                List.of("Grounds", UltraStyle.color("6", "Legendary") + " - Champion Kai - 3 battles",
                        "You are the champion")))) {
            bad.add("hud lines join back into the bar text");
        }
        if (!NativeHudPacket.hide(MOMENTUM).signature().contains("false")) {
            bad.add("hide packet");
        }
        return bad;
    }
}
