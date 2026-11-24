package com.dbzlegacy.adaptivedifficulty.net.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Server to client on {@code legacymechanics:native_ui} (LM 4.7.72), message 4: one native HUD element. Sent only
 * to an LM client whose hello listed {@link LmNativeProtocol#FEATURE_HUD}, so an older LM client never receives a
 * message id it did not register. Never sent over the KaiPath bridge.
 *
 * <p>The server owns every number: {@code lines} are the exact strings the boss bar or chat line carries (colour
 * codes included), {@code progress} the same fraction the bar shows (-1 for no bar). {@code accent} is
 * {@code rival}, {@code difficulty} or {@code #rrggbb}. {@code action} is what a click sends ({@code ""}: not
 * clickable). {@code durationMs} > 0 makes the element hide itself after that long; with {@code drain} its bar
 * drains from {@code progress} to 0 over that time on the client, so a 3-second notice needs one message.
 *
 * <p>Wire: element, show flag, line count (0-4) and lines, progress, accent, action, duration, drain.
 */
public final class NativeHudPacket {
    public static final int MAX_LINES = 4;

    private final String element;
    private final boolean show;
    private final List<String> lines;
    private final float progress;
    private final String accent;
    private final String action;
    private final int durationMs;
    private final boolean drain;

    public NativeHudPacket(String element, boolean show, List<String> lines, float progress, String accent,
            String action, int durationMs, boolean drain) {
        this.element = GuiOpenPacket.trim(element == null ? "" : element, 32);
        this.show = show;
        List<String> l = new ArrayList<>();
        if (lines != null) {
            for (String s : lines) {
                if (l.size() >= MAX_LINES) {
                    break;
                }
                l.add(GuiOpenPacket.trim(s == null ? "" : s, 256));
            }
        }
        this.lines = List.copyOf(l);
        this.progress = Float.isNaN(progress) ? -1f : Math.max(-1f, Math.min(1f, progress));
        this.accent = GuiOpenPacket.trim(accent == null ? "" : accent, 16);
        this.action = GuiOpenPacket.trim(action == null ? "" : action, 128);
        this.durationMs = Math.max(0, durationMs);
        this.drain = drain;
    }

    /** Hide {@code element}. */
    public static NativeHudPacket hide(String element) {
        return new NativeHudPacket(element, false, List.of(), -1f, "", "", 0, false);
    }

    public String element() {
        return element;
    }

    public boolean show() {
        return show;
    }

    public List<String> lines() {
        return lines;
    }

    public float progress() {
        return progress;
    }

    public String accent() {
        return accent;
    }

    public String action() {
        return action;
    }

    public int durationMs() {
        return durationMs;
    }

    public boolean drain() {
        return drain;
    }

    /** What the server compares to skip a resend: everything but the toast timing. */
    public String signature() {
        return element + '\u0001' + show + '\u0001' + String.join("\u0002", lines) + '\u0001'
                + Math.round(progress * 1000f) + '\u0001' + accent + '\u0001' + action + '\u0001' + durationMs
                + '\u0001' + drain;
    }

    public static void encode(NativeHudPacket msg, FriendlyByteBuf buf) {
        GuiOpenPacket.writeUtf8(buf, msg.element);
        buf.writeBoolean(msg.show);
        buf.writeByte(msg.lines.size());
        for (String s : msg.lines) {
            GuiOpenPacket.writeUtf8(buf, s);
        }
        buf.writeFloat(msg.progress);
        GuiOpenPacket.writeUtf8(buf, msg.accent);
        GuiOpenPacket.writeUtf8(buf, msg.action);
        buf.writeInt(msg.durationMs);
        buf.writeBoolean(msg.drain);
    }

    public static NativeHudPacket decode(FriendlyByteBuf buf) {
        String element = GuiOpenPacket.readUtf8(buf);
        boolean show = buf.readBoolean();
        int n = Math.min(MAX_LINES, Math.max(0, buf.readByte()));
        List<String> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            lines.add(GuiOpenPacket.readUtf8(buf));
        }
        float progress = buf.readFloat();
        String accent = GuiOpenPacket.readUtf8(buf);
        String action = GuiOpenPacket.readUtf8(buf);
        int duration = buf.readInt();
        boolean drain = buf.readBoolean();
        return new NativeHudPacket(element, show, lines, progress, accent, action, duration, drain);
    }
}
