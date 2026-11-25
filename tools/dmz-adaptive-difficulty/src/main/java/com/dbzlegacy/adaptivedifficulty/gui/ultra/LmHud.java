package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore;
import com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork;
import com.dbzlegacy.adaptivedifficulty.net.gui.NativeHudPacket;
import com.dbzlegacy.adaptivedifficulty.util.LmChat;
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
    /**
     * 4.7.87: the dojo toast and pill, top row next to FIGHT. The war lifecycle (declare and sign-off requests, the
     * sign-up pill with its countdown, war announcements and results), Spar Board post and accept notices, mentor
     * bond invites and replies, and the dojo login nudge. Tapping it opens the page where the decision lives.
     */
    public static final String WAR = "war";
    public static final List<String> ELEMENTS = List.of(UNDERDOG, INSTINCT, TP, PENALTY, MOMENTUM, FIGHT, SPAR,
            POTENTIAL, COINS, ELITE, BOOST, WAR);
    /** Elements that are events: each send shows (or refreshes) the element, so identical sends still go. */
    static final java.util.Set<String> EVENTS = java.util.Set.of(INSTINCT, TP, COINS, POTENTIAL, WAR);
    /** 4.7.87: how long a dojo / war toast stays (it drains on the client). */
    public static final int WAR_TOAST_MS = 8_000;
    /** 4.7.87: a request that waits on the player (war declare, sign-off, bond invite) stays a little longer. */
    public static final int WAR_REQUEST_MS = 15_000;
    /** 4.7.87: spar session start and end toasts on the TP element. */
    public static final int SPAR_TOAST_MS = 6_000;
    /** 4.7.87: HUD lines wrap at about this many characters (a 240 px pill). */
    static final int WRAP_CHARS = 40;
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

    /**
     * 4.7.80 no-doubling check: one chat line, boss bar, subtitle or action bar sent to a player. The chat, boss bar
     * and subtitle senders report here (only while a capture runs), so the probe can prove that a HUD player gets
     * each element once, on the HUD, and a HUD-off or bridge player gets the vanilla version as before.
     */
    public record Vanilla(UUID player, String channel, String text) {}

    /** HUD packets and vanilla notices from one run. */
    public record Both(List<Sent> hud, List<Vanilla> vanilla) {}

    private static volatile List<Vanilla> vanillaCapture;

    /** A vanilla notice went to {@code player} (no-op unless a capture runs). */
    public static void vanilla(net.minecraft.server.level.ServerPlayer player, String channel, String text) {
        List<Vanilla> cap = vanillaCapture;
        if (cap != null && player != null) {
            cap.add(new Vanilla(player.m_20148_(), channel, text == null ? "" : text));
        }
    }

    /** Run {@code r}; return its HUD packets and its vanilla notices. */
    public static Both captureBoth(Runnable r) {
        List<Vanilla> out = new CopyOnWriteArrayList<>();
        List<Vanilla> prev = vanillaCapture;
        vanillaCapture = out;
        List<Sent> hud;
        try {
            hud = capture(r);
        } finally {
            vanillaCapture = prev;
        }
        return new Both(hud, new ArrayList<>(out));
    }

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
            WAIT_HELLO.remove(player.m_20148_());
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

    // ------------------------------------------------------------------ 4.7.87 chat lines as toasts

    /**
     * 4.7.87 no-doubling: a HUD player gets {@code lines} as a toast on {@code element} that drains over {@code ms};
     * everyone else gets {@code chat} exactly as before (through {@link LmChat#send}, which the vanilla capture sees).
     * True when it went to the HUD.
     */
    public static boolean toastOrChat(ServerPlayer player, String chat, String element, List<String> lines,
            String accent, String action, int ms) {
        if (player == null) {
            return false;
        }
        if (active(player)) {
            show(player, element, lines, 1f, accent, action, ms, true);
            return true;
        }
        LmChat.send(player, chat);
        return false;
    }

    /** As above, with the chat message's own lines ({@link #chatLines}) on the HUD. */
    public static boolean toastOrChat(ServerPlayer player, String chat, String element, String accent, String action,
            int ms) {
        return toastOrChat(player, chat, element, chatLines(chat), accent, action, ms);
    }

    /**
     * HUD lines with the same words and numbers as a chat message: the {@code [System]} tag, the card title, the
     * divider and the tip footer are chrome and are dropped; a long line wraps at word breaks, keeping its colour.
     */
    public static List<String> chatLines(String chat) {
        List<String> out = new ArrayList<>();
        if (chat == null) {
            return out;
        }
        String tagHead = UltraStyle.MARK + "5" + UltraStyle.MARK + "l[";
        String tagTail = "] " + UltraStyle.MARK + "r";
        for (String line : LmChat.normalize(chat).split("\\n", -1)) {
            if (UltraStyle.plain(line).isEmpty() || LmChat.isChrome(line)) {
                continue;
            }
            if (line.startsWith(tagHead)) {
                int close = line.indexOf(tagTail);
                if (close > 0) {
                    line = line.substring(close + tagTail.length());
                }
            }
            out.addAll(wrap(line, WRAP_CHARS));
        }
        return out;
    }

    /** {@code text} cut at word breaks into lines of about {@code max} visible characters; colour codes carry over. */
    public static List<String> wrap(String text, int max) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        char mark = UltraStyle.MARK.charAt(0);
        StringBuilder cur = new StringBuilder();
        String carry = "";
        String lastCode = "";
        String codeAtBreak = "";
        int visible = 0;
        int breakAt = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == mark && i + 1 < text.length()) {
                char k = text.charAt(i + 1);
                boolean colour = k == 'r' || k == 'R' || "0123456789abcdefABCDEF".indexOf(k) >= 0;
                lastCode = colour ? "" + mark + k : lastCode + mark + k;
                cur.append(c).append(k);
                i++;
                continue;
            }
            if (c == ' ') {
                breakAt = cur.length();
                codeAtBreak = lastCode;
            }
            cur.append(c);
            visible++;
            if (visible > max && breakAt > 0) {
                out.add(carry + cur.substring(0, breakAt));
                String rest = cur.substring(breakAt + 1);
                carry = codeAtBreak;
                cur = new StringBuilder(rest);
                visible = UltraStyle.plain(rest).length();
                breakAt = -1;
            }
        }
        if (!UltraStyle.plain(cur.toString()).isEmpty()) {
            out.add(carry + cur);
        }
        return out;
    }

    private static String joinedPlain(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(UltraStyle.plain(l));
        }
        return sb.toString();
    }

    /** 4.7.87: login notices wait at most this long for the LM client's hello (then they go to chat). */
    public static final long HELLO_WAIT_MS = 5_000L;

    private record Waiting(long since, List<Runnable> notices) {}

    private static final Map<UUID, Waiting> WAIT_HELLO = new ConcurrentHashMap<>();

    /**
     * 4.7.87: run a login notice once it is known whether this player takes the HUD. At login an LM client's hello
     * has not arrived yet, so {@link #active} still reads false; the notice waits for the hello (at most
     * {@link #HELLO_WAIT_MS}, then it runs and goes to chat). A bridge or vanilla client runs it now, so chat is
     * exactly as before.
     */
    public static void whenClientKnown(ServerPlayer player, Runnable notice) {
        if (player == null || notice == null) {
            return;
        }
        if (!LmNativeNetwork.hasNativeClient(player) || LmNativeNetwork.helloArrived(player)) {
            notice.run();
            return;
        }
        WAIT_HELLO.computeIfAbsent(player.m_20148_(), k -> new Waiting(System.currentTimeMillis(),
                new CopyOnWriteArrayList<>())).notices().add(notice);
    }

    /** The hello arrived (server thread): run this player's waiting login notices. */
    public static void helloArrived(ServerPlayer player) {
        Waiting w = player == null ? null : WAIT_HELLO.remove(player.m_20148_());
        if (w != null) {
            runAll(w);
        }
    }

    /** Server tick: login notices whose hello never came run now (chat). */
    public static void pulse(net.minecraft.server.MinecraftServer server, int tick) {
        if (server == null || tick % 20 != 0 || WAIT_HELLO.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Waiting> e : WAIT_HELLO.entrySet()) {
            if (now - e.getValue().since() < HELLO_WAIT_MS) {
                continue;
            }
            WAIT_HELLO.remove(e.getKey());
            if (server.m_6846_().m_11259_(e.getKey()) != null) {
                runAll(e.getValue());
            }
        }
    }

    private static void runAll(Waiting w) {
        for (Runnable r : w.notices()) {
            try {
                r.run();
            } catch (Throwable t) {
                com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod.LOGGER.warn("[{}] login notice failed: {}",
                        com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod.MOD_ID, t.toString());
            }
        }
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
        // 4.7.87: the dojo / war element is registered and is an event; chat lines keep their words on the HUD.
        if (!ELEMENTS.contains(WAR) || !EVENTS.contains(WAR)) {
            bad.add("war element registered as an event");
        }
        String card = LmChat.card("Sparring", "/spar", null, UltraStyle.GREEN + "Session started with "
                + UltraStyle.VALUE + "Kai", UltraStyle.DIM + "Stay active.");
        List<String> cl = chatLines(card);
        if (cl.size() != 2 || !"Session started with Kai".equals(UltraStyle.plain(cl.get(0)))) {
            bad.add("card chat lines on the HUD: " + cl);
        }
        String war = "War! Red Dojo vs Blue Dojo (bracket). Sign-up is open for 60 seconds: open Dojo > Wars and tap"
                + " Join the war.";
        List<String> wl = chatLines(LmChat.info("War", UltraStyle.VALUE + war));
        if (wl.size() < 2 || wl.size() > NativeHudPacket.MAX_LINES || !war.equals(joinedPlain(wl))) {
            bad.add("war chat line wraps on the HUD with the same words: " + wl);
        }
        String ink = wl.isEmpty() || wl.get(0).length() < 2 ? "?" : wl.get(0).substring(0, 2);
        for (String l : wl) {
            if (!l.startsWith(ink) || !ink.startsWith(UltraStyle.MARK)) {
                bad.add("wrapped HUD lines keep their colour: " + wl);
                break;
            }
        }
        return bad;
    }
}
