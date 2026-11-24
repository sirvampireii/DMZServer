package com.dbzlegacy.adaptivedifficulty.net.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * LM 4.7.58 native client protocol: the menu spec the LM client renders itself, on its own channel.
 *
 * <p>The open and click payloads are byte-identical to the KaiPath bridge ({@link GuiOpenPacket},
 * {@link GuiActionPacket}); only the channel differs. Pure Java (Gson only), so the server
 * self-test and the client share it.
 *
 * <p>A menu goes native only when the client renders every feature it uses. {@link #analyze} lists
 * what a packet needs: <b>hard</b> features (a widget the client must draw, or the menu would be
 * half-rendered) and <b>soft</b> ones (a preview hint the protocol lets a client skip: the base
 * entity still draws). {@link LmNativeNetwork} sends a menu the client cannot fully draw over the
 * KaiPath bridge when the player has KaiPath.
 */
public final class LmNativeProtocol {
    private LmNativeProtocol() {}

    /** Channel path under the LM mod id: {@code legacymechanics:native_ui}. Never {@code gui}. */
    public static final String CHANNEL_PATH = "native_ui";
    /** Forge protocol string for the native channel. Independent of the bridge's "1". */
    public static final String PROTOCOL_VERSION = "1";
    public static final int MSG_OPEN = 0;
    public static final int MSG_ACTION = 1;
    public static final int MSG_HELLO = 2;
    /** LM 4.7.62: server to client, the player's menu look ({@link NativePrefsPacket}). */
    public static final int MSG_PREFS = 3;
    /**
     * LM 4.7.62: the client takes message 3 (menu skins, backgrounds, Lite). Never in {@link #CLIENT_FEATURES}:
     * the server assumes that list before a hello arrives, and only a client that said this id may get message 3.
     */
    public static final String FEATURE_MENU_SKINS = "menuSkins";
    /**
     * LM 4.7.66: the client draws a custom menu skin from its token (message 3's skin string, no new message),
     * sends every field of a page with a click ({@code args.bindInputs}) and draws the editor's live swatch.
     * Like {@link #FEATURE_MENU_SKINS} it is hello-only, never in {@link #CLIENT_FEATURES}.
     */
    public static final String FEATURE_CUSTOM_SKIN = "customSkin";
    /**
     * LM 4.7.68: the client draws layoutJson {@code pictures} (UltraScreenSpec#pictures: textures, cut-outs and marks
     * in body rows). Hello-only like the skins; a page sends pictures only to a client that said it.
     */
    public static final String FEATURE_PICTURES = "pictures";
    /**
     * LM 4.7.71: the client takes the navigation layer: Back pops the player's page history ({@code nav:back}), a
     * breadcrumb line (layoutJson {@code breadcrumb}) under the title of every page below a system Home, and the
     * hub's search row. Hello-only; bridge clients keep the registry's parent pages and no search row.
     */
    public static final String FEATURE_NAV = "nav";

    /** Feature ids (bridge spelling) the protocol-1 LM client draws natively. */
    public static final List<String> CLIENT_FEATURES = List.of(
            "style", "activeTab", "titleAlign", "playerModel", "textInput", "headerBottom", "accent",
            "palette", "sound", "buttonSkin", "frame", "pagedBook", "portraitFrame", "fixedFooter", "icon",
            "fillScreen", "modelSubject", "lockedButton");

    /** Feature a stand-in figure with a worn form needs (form pieces on a made-up player). */
    public static final String FEATURE_FORMS = "combined.forms.v1";

    /**
     * Body yaw that turns a menu's model preview front-facing: the character's front toward the viewer,
     * never the back (the in-GUI entity renderer draws with a 180-degree roll, as the vanilla inventory does).
     */
    public static final float PREVIEW_FRONT_YAW = 180f;

    /** Body yaw for a preview spun {@code spin} degrees away from front-facing. */
    public static float previewFacing(float spin) {
        return PREVIEW_FRONT_YAW + spin;
    }

    /**
     * Spin kept when a menu packet arrives: a model with {@code rotate false} resets to 0, front-facing,
     * whatever angle it had reached; a spinning model keeps turning from where it was.
     */
    public static float spinAfterPacket(float spin, boolean rotate) {
        return rotate ? spin : 0f;
    }
    /** Feature a stand-in figure with explicit body pieces needs. */
    public static final String FEATURE_BODY_PARTS = "bodyParts";

    /** What a packet needs from a client. */
    public record Needs(Set<String> hard, Set<String> soft) {
        public boolean metBy(Set<String> supported) {
            return supported.containsAll(hard) && supported.containsAll(soft);
        }

        public boolean hardMetBy(Set<String> supported) {
            return supported.containsAll(hard);
        }

        public Set<String> missing(Set<String> supported) {
            Set<String> out = new LinkedHashSet<>();
            for (String f : hard) {
                if (!supported.contains(f)) {
                    out.add(f);
                }
            }
            for (String f : soft) {
                if (!supported.contains(f)) {
                    out.add(f);
                }
            }
            return out;
        }
    }

    /**
     * Features {@code packet} needs. {@code realPlayer} says whether a subject UUID is a real player
     * the client can draw from its own entity data (online on the server). A made-up subject
     * (Preview forms, the rival profile's "you") draws a stand-in, so its {@code parts.form} and
     * {@code parts.bodyParts} are needed to show the right look.
     */
    public static Needs analyze(GuiOpenPacket packet, Predicate<UUID> realPlayer) {
        Set<String> hard = new LinkedHashSet<>();
        Set<String> soft = new LinkedHashSet<>();
        if (packet == null) {
            return new Needs(hard, soft);
        }
        for (JsonElement el : array(packet.inputsJson())) {
            if (!el.isJsonObject()) {
                continue;
            }
            String type = str(el.getAsJsonObject(), "type", "text").toLowerCase(Locale.ROOT);
            switch (type) {
                case "text" -> hard.add("textInput");
                case "paged_book" -> hard.add("pagedBook");
                // 4.7.66: additive and ignorable (every client skips it), so it never routes a page.
                case com.dbzlegacy.adaptivedifficulty.gui.ultra.ColorWheel.TYPE -> { }
                default -> hard.add("input:" + type);
            }
        }
        JsonObject layout = object(packet.layoutJson());
        if (layout != null) {
            if (layout.has("fillScreen") && bool(layout, "fillScreen")) {
                hard.add("fillScreen");
            }
            if (layout.has("playerModel") && layout.get("playerModel").isJsonObject()) {
                JsonObject pm = layout.getAsJsonObject("playerModel");
                hard.add("playerModel");
                JsonObject parts = pm.has("parts") && pm.get("parts").isJsonObject()
                        ? pm.getAsJsonObject("parts") : null;
                UUID subject = subjectId(pm, parts);
                boolean standIn = subject != null && (realPlayer == null || !realPlayer.test(subject));
                if (subject != null) {
                    hard.add("modelSubject");
                }
                if (standIn && parts != null) {
                    if (parts.has("form") && parts.get("form").isJsonObject()
                            && !parts.getAsJsonObject("form").entrySet().isEmpty()) {
                        soft.add(FEATURE_FORMS);
                    }
                    if (parts.has("bodyParts") && parts.get("bodyParts").isJsonArray()
                            && parts.getAsJsonArray("bodyParts").size() > 0) {
                        soft.add(FEATURE_BODY_PARTS);
                    }
                }
            }
        }
        return new Needs(hard, soft);
    }

    /**
     * The player a preview names: {@code playerModel.subject}, else {@code parts.subject}. Null for
     * {@code local}, blank, or anything that is not a UUID (the viewer is drawn).
     */
    public static UUID subjectId(JsonObject model, JsonObject parts) {
        String token = model != null ? str(model, "subject", "") : "";
        if (token.isBlank() && parts != null) {
            token = str(parts, "subject", "");
        }
        token = token.trim();
        if (token.regionMatches(true, 0, "uuid:", 0, 5)) {
            token = token.substring(5).trim();
        }
        if (token.isEmpty() || "local".equalsIgnoreCase(token)) {
            return null;
        }
        try {
            return UUID.fromString(token);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Feature ids from a hello JSON array; the protocol-1 list when it is blank or unreadable. */
    public static Set<String> parseFeatures(String json) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonElement el : array(json)) {
            if (el.isJsonPrimitive()) {
                String s = el.getAsString().trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        if (out.isEmpty()) {
            out.addAll(CLIENT_FEATURES);
        }
        return out;
    }

    /** JSON array of {@link #CLIENT_FEATURES}, as the client sends it. */
    public static String clientFeaturesJson() {
        return clientFeaturesJson(false, false);
    }

    /**
     * LM 4.7.61: {@link #CLIENT_FEATURES} plus what the installed mods let the LM client draw on a made-up
     * preview figure: {@link #FEATURE_FORMS} with DragonMineZ (race and form on the stand-in) and
     * {@link #FEATURE_BODY_PARTS} with DMZUltra (bodyParts merged into its FormParts look). With both,
     * Character Services Preview forms opens natively instead of falling back to KaiPath.
     */
    public static String clientFeaturesJson(boolean forms, boolean bodyParts) {
        JsonArray a = new JsonArray();
        for (String f : CLIENT_FEATURES) {
            a.add(f);
        }
        if (forms) {
            a.add(FEATURE_FORMS);
        }
        if (bodyParts) {
            a.add(FEATURE_BODY_PARTS);
        }
        a.add(FEATURE_MENU_SKINS);
        a.add(FEATURE_CUSTOM_SKIN);
        a.add(com.dbzlegacy.adaptivedifficulty.gui.ultra.ColorWheel.FEATURE);
        a.add(FEATURE_PICTURES);
        a.add(FEATURE_NAV);
        return a.toString();
    }

    /** Client: the DragonMineZ stats capability is installed (a stand-in can wear a race and form). */
    public static boolean clientDrawsForms() {
        return classPresent("com/dragonminez/common/stats/StatsCapability.class");
    }

    /** Client: DMZUltra's FormParts is installed (bodyParts can join the look). */
    public static boolean clientDrawsBodyParts() {
        return classPresent("com/dmzultra/client/forms/FormParts.class");
    }

    private static boolean classPresent(String resource) {
        try {
            ClassLoader loader = LmNativeProtocol.class.getClassLoader();
            return loader != null && loader.getResource(resource) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    static JsonArray array(String json) {
        if (json == null || json.isBlank()) {
            return new JsonArray();
        }
        try {
            JsonElement el = JsonParser.parseString(json);
            return el.isJsonArray() ? el.getAsJsonArray() : new JsonArray();
        } catch (RuntimeException e) {
            return new JsonArray();
        }
    }

    static JsonObject object(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonElement el = JsonParser.parseString(json);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String str(JsonObject o, String key, String fallback) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject o, String key) {
        try {
            return o.get(key).getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
