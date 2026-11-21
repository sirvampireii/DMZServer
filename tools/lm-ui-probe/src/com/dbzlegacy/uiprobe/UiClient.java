package com.dbzlegacy.uiprobe;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Client half (test only): joins -Duiprobe.server; for each "uishot i screen page" cue, waits for the LM
 * screen, then for each GUI size records every widget's bounds and takes a screenshot, then says "uinext".
 */
final class UiClient {
    /** window w, h, guiScale (0 = auto), label. GUI sizes: 960x540, 640x360, 480x270, 427x240. */
    static final int[][] CONFIGS = {{1920, 1080, 2}, {1920, 1080, 3}, {1920, 1080, 4}};
    static final String[] NAMES = {"1080p-gs2", "1080p-gs3", "1080p-gs4"};
    /** Fallback when the OS clamps a 1920x1080 window: same GUI size at half the pixels. */
    static final int[][] FALLBACK = {{960, 540, 1}, {640, 360, 1}, {480, 270, 1}};
    /** 4.7.55: after the top capture, scroll the page to its end and capture again. */
    private boolean scrolled;

    private final String server = System.getProperty("uiprobe.server");
    private int settle, joins, timeout;
    private boolean joined, done;
    private String cue;
    private Screen cueScreen;
    private int cfg = -1, wait;
    private String pendingShot;
    private int[][] use;
    private int calib = -1;

    UiClient() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::chat);
    }

    private void join(Minecraft mc) {
        net.minecraft.client.gui.screens.ConnectScreen.m_278792_(new TitleScreen(), mc,
                net.minecraft.client.multiplayer.resolver.ServerAddress.m_171864_(server),
                new ServerData("ui", server, false), false);
    }

    private void chat(ClientChatReceivedEvent e) {
        String s = e.getMessage().getString();
        if (s.startsWith("uiquit")) {
            done = true;
            Minecraft.m_91087_().m_91395_();
        } else if (s.startsWith("uiclick ")) {
            e.setCanceled(true);
            pendingClick = s.substring(8).trim();
        } else if (s.startsWith("uirecord ")) {
            e.setCanceled(true);
            Minecraft mc = Minecraft.m_91087_();
            Screen sc = mc.f_91080_;
            StringBuilder sb = new StringBuilder("REC " + s.substring(9).trim() + " class="
                    + (sc == null ? "null" : sc.getClass().getName()) + " title="
                    + (sc == null ? "" : sc.m_96636_().getString()) + "\n");
            for (String l : nativeLayout(sc)) {
                sb.append("  N ").append(l).append("\n");
            }
            UiProbe.write("uiprobe-client.txt", sb.toString(), true);
            pendingShot = "ui-rec-" + s.substring(9).trim() + ".png";
        } else if (s.startsWith("uishot ")) {
            cue = s.substring(7).trim();
            cueScreen = null;
            cfg = -1;
            wait = 0;
            timeout = 0;
            e.setCanceled(true);
        }
    }

    private static void apply(Minecraft mc, int[] c) {
        long h = mc.m_91268_().m_85439_();
        GLFW.glfwSetWindowSize(h, c[0], c[1]);
        mc.f_91066_.m_231928_().m_231514_(c[2]);
        mc.m_5741_();
    }

    private String pendingClick;

    /** Ask the server for the next menu; a lost connection is logged and ends the run instead of crashing. */
    private void next(Minecraft mc) {
        if (mc.f_91074_ == null || mc.f_91074_.f_108617_ == null) {
            UiProbe.write("uiprobe-client.txt", "DISCONNECTED during " + cue + System.lineSeparator(), true);
            done = true;
            mc.m_91395_();
            return;
        }
        mc.f_91074_.f_108617_.m_246175_("uinext");
    }

    /** LM 4.7.58 native screen layout (screen space), or empty for any other screen. */
    @SuppressWarnings("unchecked")
    static List<String> nativeLayout(Screen s) {
        if (s == null) {
            return List.of();
        }
        try {
            return (List<String>) s.getClass().getMethod("debugLayout").invoke(s);
        } catch (Throwable t) {
            return List.of();
        }
    }

    static boolean lmScreen(Screen s) {
        String n = s == null ? "" : s.getClass().getName();
        return n.contains("legacymechanics") || n.contains("adaptivedifficulty.client");
    }

    /** Click a native button by "label:X" or "id:substring"; logs what it did. */
    private void doClick(Minecraft mc, String spec) {
        Screen sc = mc.f_91080_;
        String found = null;
        for (String l : nativeLayout(sc)) {
            if (!l.startsWith("B ")) {
                continue;
            }
            String[] f = l.split(" ", 8);
            String label = l.substring(l.indexOf('"') + 1, l.lastIndexOf('"'));
            boolean hit = spec.startsWith("label:") ? label.equals(spec.substring(6))
                    : spec.startsWith("id:") && f[6].contains(spec.substring(3));
            if (hit) {
                double x = Double.parseDouble(f[1]) + Double.parseDouble(f[3]) / 2;
                double y = Double.parseDouble(f[2]) + Double.parseDouble(f[4]) / 2;
                sc.m_6375_(x, y, 0);
                found = l;
                break;
            }
        }
        UiProbe.write("uiprobe-client.txt", "CLICK " + spec + " -> " + (found == null ? "NOT FOUND class="
                + (sc == null ? "null" : sc.getClass().getName()) : found) + "\n", true);
    }

    private void record(Minecraft mc, Screen s, String cfgName) {
        var win = mc.m_91268_();
        int gw = win.m_85445_(), gh = win.m_85446_();
        StringBuilder sb = new StringBuilder();
        sb.append("SCREEN ").append(cue).append(" cfg=").append(cfgName).append(" fb=").append(win.m_85441_()).append('x')
                .append(win.m_85442_()).append(" gui=").append(gw).append('x').append(gh).append(" scale=")
                .append(win.m_85449_()).append(" class=").append(s.getClass().getSimpleName()).append('\n');
        for (String l : nativeLayout(s)) {
            sb.append("  N ").append(l).append("\n");
        }
        List<? extends GuiEventListener> kids = s.m_6702_();
        for (GuiEventListener k : kids) {
            if (k instanceof AbstractWidget w) {
                String label = w.m_6035_() == null ? "" : w.m_6035_().getString().replace('\n', ' ');
                int tw = mc.f_91062_.m_92895_(label);
                sb.append("  W ").append(w.getClass().getSimpleName()).append(' ').append(w.m_252754_()).append(' ')
                        .append(w.m_252907_()).append(' ').append(w.m_5711_()).append(' ').append(w.m_93694_())
                        .append(" vis=").append(w.f_93624_).append(" tw=").append(tw).append(" \"").append(label)
                        .append("\"\n");
            } else {
                sb.append("  L ").append(k.getClass().getSimpleName()).append('\n');
            }
        }
        UiProbe.write("uiprobe-client.txt", sb.toString(), true);
    }

    private void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || done) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        if (pendingClick != null) {
            String c = pendingClick;
            pendingClick = null;
            doClick(mc, c);
        }
        if (pendingShot != null) {
            String f = pendingShot;
            pendingShot = null;
            Screenshot.m_92295_(mc.f_91069_, f, mc.m_91385_(), msg -> { });
        }
        if (!joined) {
            if (mc.f_91080_ == null || mc.f_91073_ != null || mc.m_91265_() != null || settle++ < 40) {
                return;
            }
            join(mc);
            joined = true;
            return;
        }
        if ((mc.f_91074_ == null || mc.f_91073_ == null)
                && mc.f_91080_ instanceof net.minecraft.client.gui.screens.DisconnectedScreen && joins < 3) {
            joins++;
            join(mc);
            return;
        }
        if (cue == null) {
            if (++timeout > 20 * 900) {
                done = true;
                mc.m_91395_();
            }
            return;
        }
        if (use == null) {
            if (calib < 0) {
                apply(mc, CONFIGS[0]);
                calib = 20;
                return;
            }
            if (calib-- > 0) {
                return;
            }
            var w0 = mc.m_91268_();
            use = w0.m_85441_() == CONFIGS[0][0] && w0.m_85442_() == CONFIGS[0][1] ? CONFIGS : FALLBACK;
            UiProbe.write("uiprobe-client.txt", "CALIBRATE fb=" + w0.m_85441_() + "x" + w0.m_85442_() + " use="
                    + (use == CONFIGS ? "full" : "fallback") + "\n", true);
        }
        if (cueScreen == null) {
            Screen s = mc.f_91080_;
            if (s != null && lmScreen(s)) {
                cueScreen = s;
                wait = 4;
            } else if (++timeout > 200) {
                UiProbe.write("uiprobe-client.txt", "NOSCREEN " + cue + " got="
                        + (s == null ? "null" : s.getClass().getName()) + "\n", true);
                cue = null;
                next(mc);
            }
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (mc.f_91080_ == null) {
            UiProbe.write("uiprobe-client.txt", "CLOSED " + cue + "\n", true);
            cue = null;
            next(mc);
            return;
        }
        if (cfg >= 0) {
            String name = NAMES[cfg] + (scrolled ? "-end" : "");
            record(mc, mc.f_91080_, name);
            String idx = cue.split(" ")[0];
            String shot = "ui-" + String.format("%03d", Integer.parseInt(idx)) + "-" + name + ".png";
            Screenshot.m_92295_(mc.f_91069_, shot, mc.m_91385_(), msg -> { });
            if (!scrolled) {
                // Wheel far down at the panel center: KaiPath clamps to the page's last scroll position.
                var win = mc.m_91268_();
                mc.f_91080_.m_6050_(win.m_85445_() / 2.0, win.m_85446_() / 2.0, -10000.0);
                scrolled = true;
                wait = 3;
                return;
            }
            scrolled = false;
        }
        cfg++;
        if (cfg >= CONFIGS.length) {
            cue = null;
            next(mc);
            return;
        }
        apply(mc, use[cfg]);
        wait = 8;
    }
}
