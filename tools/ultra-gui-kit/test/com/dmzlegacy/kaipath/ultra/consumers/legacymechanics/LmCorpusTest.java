package com.dmzlegacy.kaipath.ultra.consumers.legacymechanics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Offline check of the kit against real LM menus (no Minecraft runtime). The corpus is one recorded packet per
 * LM menu family ({@code test/fixtures/lm-specs.jsonl}, made by {@code test/gen_lm_corpus.py} from an LM UI probe
 * run). Every packet must survive the kit's encode/decode unchanged and follow what KaiPath relies on:
 * buttons inside the panel, 80px minimum width, 16px menu rows, ASCII-only labels (KaiPath mojibakes other
 * characters), palette names the kit knows, and a still model preview that resets to front-facing.
 */
public final class LmCorpusTest {
    private LmCorpusTest() {}

    /** Palette names {@link UltraBridgeChrome} draws in their own color (anything else falls back to gold). */
    private static final Set<String> KNOWN_PALETTES = Set.of("red", "dark_red", "purple", "dark_purple", "gold",
            "yellow", "green", "aqua", "blue", "white", "gray", "grey");

    public static void main(String[] args) throws Exception {
        Path corpus = Path.of(System.getProperty("ultragui.corpus", "test/fixtures/lm-specs.jsonl"));
        List<String> bad = new ArrayList<>();
        int packets = 0;
        int buttons = 0;
        for (String line : Files.readAllLines(corpus, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            JsonObject r = JsonParser.parseString(line).getAsJsonObject();
            String where = r.get("screen").getAsString() + "|" + r.get("page").getAsString();
            GuiOpenPacket p = new GuiOpenPacket(r.get("screen").getAsString(), r.get("title").getAsString(),
                    r.get("page").getAsString(), "[]", r.get("tab").getAsString(), r.get("lines").getAsString(),
                    r.get("buttons").getAsString(), r.get("panelW").getAsInt(), r.get("panelH").getAsInt(),
                    r.get("layout").getAsString(), r.get("inputs").getAsString());
            FriendlyByteBuf buf = new FriendlyByteBuf();
            GuiOpenPacket.encode(p, buf);
            GuiOpenPacket d = GuiOpenPacket.decode(buf);
            if (!d.buttonsJson().equals(p.buttonsJson()) || !d.layoutJson().equals(p.layoutJson())
                    || !d.linesJson().equals(p.linesJson()) || !d.inputsJson().equals(p.inputsJson())
                    || d.panelWidth() != p.panelWidth() || d.panelHeight() != p.panelHeight()) {
                bad.add(where + ": changed in encode/decode");
            }
            packets++;
            JsonObject layout = JsonParser.parseString(p.layoutJson()).getAsJsonObject();
            boolean fill = layout.has("fillScreen") && layout.get("fillScreen").getAsBoolean();
            int panelW = fill && layout.has("designWidth") ? layout.get("designWidth").getAsInt() : p.panelWidth();
            int panelH = fill && layout.has("designHeight") ? layout.get("designHeight").getAsInt() : p.panelHeight();
            String palette = layout.has("palette") ? UltraBridgeChrome.normalizePalette(layout.get("palette").getAsString()) : "";
            if (!palette.isEmpty() && !KNOWN_PALETTES.contains(palette)) {
                bad.add(where + ": palette '" + palette + "' unknown to the kit (draws gold)");
            }
            if (layout.has("playerModel") && layout.get("playerModel").isJsonObject()) {
                JsonObject pm = layout.getAsJsonObject("playerModel");
                boolean rotate = pm.has("rotate") && pm.get("rotate").getAsBoolean();
                if (!rotate && (PreviewFacing.afterPacket(123f, false) != 0f || PreviewFacing.lookX(false, 123f) != 0f)) {
                    bad.add(where + ": still preview is not front-facing");
                }
            }
            JsonArray bs = JsonParser.parseString(p.buttonsJson()).getAsJsonArray();
            for (JsonElement el : bs) {
                JsonObject b = el.getAsJsonObject();
                buttons++;
                String id = b.has("id") ? b.get("id").getAsString() : "";
                String label = b.has("label") ? b.get("label").getAsString() : "";
                String what = where + " '" + plain(label) + "'";
                if (id.isBlank()) {
                    bad.add(what + ": no action id");
                }
                int x = b.get("x").getAsInt();
                int y = b.get("y").getAsInt();
                int w = b.get("w").getAsInt();
                int h = b.get("h").getAsInt();
                if (x < 0 || y < 0 || x + w > panelW || y + h > panelH) {
                    bad.add(what + ": outside the " + panelW + "x" + panelH + " panel");
                }
                if (w < 80) {
                    bad.add(what + ": " + w + "px wide (min 80)");
                }
                if (!fill && h != 16) {
                    bad.add(what + ": row " + h + "px high (menus use 16)");
                }
                for (char c : plain(label).toCharArray()) {
                    if (c > 0x7E || (c < 0x20)) {
                        bad.add(what + ": non-ASCII character U+" + Integer.toHexString(c) + " (KaiPath mojibake)");
                        break;
                    }
                }
            }
        }
        if (packets < 20) {
            bad.add("corpus has only " + packets + " packets; regenerate it from a full LM UI probe run");
        }
        if (!bad.isEmpty()) {
            throw new AssertionError("LmCorpusTest: " + bad.size() + " problem(s): " + bad.subList(0, Math.min(15, bad.size())));
        }
        System.out.println("LmCorpusTest OK (" + packets + " real LM menus, " + buttons + " buttons)");
    }

    /** Label text without color codes. */
    private static String plain(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '§' && i + 1 < s.length()) {
                i++;
                continue;
            }
            out.append(s.charAt(i));
        }
        return out.toString();
    }
}
