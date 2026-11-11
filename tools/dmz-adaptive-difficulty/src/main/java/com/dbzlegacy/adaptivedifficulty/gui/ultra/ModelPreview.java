package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.util.DmzLang;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.config.SkillsConfig;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.util.TransformationsHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Model previews on LM screens (4.7.48): the spin on/off toggle, the form figure (your race in
 * one of its forms) and the rival preview.
 *
 * <p>A form figure is a made-up player: {@code subject uuid:<fixed id>} plus a schema-1
 * {@code form}. KaiPath draws a stand-in with that race and form. It never uses
 * {@code subject local} with a form: the client then keeps the base form and the override
 * reaches the player's own HUD portrait. KaiPath copies race and form only, so a form figure
 * has the race's default colors, and a "player" name tag can show above it.
 *
 * <p>4.7.49: every model screen draws its spin toggle in one spot, right under the model
 * ({@link #rotateButton}), and the model's scale is fit to the form shown ({@link #fitScale}):
 * KaiPath sizes the preview from the entity's hitbox, which never grows, so a giant form such as
 * Oozaru (form {@code modelScaling} 3.8) would be drawn far past the box and cut off.
 */
public final class ModelPreview {
    /** One action id for every model screen: {@code do:model_rotate:<page to reopen>}. */
    public static final String ROTATE_ACTION = "model_rotate";

    /** Viewers who turned the spin off. Everyone else spins (the default). */
    private static final Set<UUID> STILL = ConcurrentHashMap.newKeySet();
    /** The form a viewer last picked on the form preview page. */
    private static final Map<UUID, Form> PICKED = new ConcurrentHashMap<>();

    /** One form of a race. Base is the empty group and form. */
    public record Form(String race, String group, String form, String label) {
        boolean base() {
            return group.isEmpty() && form.isEmpty();
        }
    }

    /** A model preview box in design pixels. */
    public record Box(int x, int y, int w, int h) {}

    /** The character screen box. UltraScreenSelfTest pins y=60 and scale 0.9. */
    public static final Box STANDARD = new Box(248, 60, 86, 120);
    /** Preview forms: the panel's right side, from under the title down to the toggle. */
    public static final Box FORMS = new Box(190, 46, 144, 186);
    /** Scale for a normal-sized model in either box. */
    public static final float BASE_SCALE = 0.9f;
    /** Spin toggle width: the model box width, at least the 80px button minimum. */
    static final int TOGGLE_W = 86;
    /** DMZ's default model scale (Character's 0.9375); form modelScaling is in the same units. */
    private static final float DMZ_BASE_MODEL_SCALE = 0.9375f;
    /** Player model height and width in geo pixels, for forms without a custom model. */
    private static final float[] PLAYER_GEO = {32f, 16f};
    private static final Map<String, float[]> GEO_SIZE = new ConcurrentHashMap<>();

    private ModelPreview() {}

    /** Whether the model spins for whoever views this screen (staff inspecting: the staff member). */
    public static boolean rotate(ServerPlayer player) {
        ServerPlayer viewer = viewer(player);
        return viewer == null || !STILL.contains(viewer.m_20148_());
    }

    /** Flip the spin for the viewer. Takes effect when the page is sent again. */
    public static void flipRotate(ServerPlayer player) {
        ServerPlayer viewer = viewer(player);
        if (viewer == null) {
            return;
        }
        UUID id = viewer.m_20148_();
        if (!STILL.remove(id)) {
            STILL.add(id);
        }
    }

    /**
     * The spin toggle, right under the {@link #STANDARD} model. Call it right after
     * {@code buttonsAfterLines()}: the page's own buttons then start below the toggle, so none of
     * them sits under the model or on top of the toggle. {@code page} is the page it reopens.
     */
    public static void rotateButton(UltraScreenSpec spec, ServerPlayer player, String page) {
        rotateButton(spec, player, page, STANDARD, true);
    }

    /**
     * The spin toggle under {@code box}, fixed there (it does not move down with the body text).
     * With {@code clearFlow} the next button row starts below it; leave it off when the page's
     * buttons stay left of the box.
     */
    public static void rotateButton(UltraScreenSpec spec, ServerPlayer player, String page, Box box, boolean clearFlow) {
        boolean on = rotate(player);
        int y = box.y() + box.h() + 4;
        spec.fixedButton("do:" + ROTATE_ACTION + ":" + page, on ? "Stop spin" : "Spin model", "normal",
                box.x() + (box.w() - TOGGLE_W) / 2, y, TOGGLE_W);
        if (clearFlow) {
            spec.setRow(Math.max(spec.row(), y + UltraStyle.BTN_H + UltraStyle.BTN_GAP));
        }
    }

    /**
     * Preview scale that keeps the shown form inside {@code box}: {@code base} for a normal-sized
     * model, smaller for a form whose model is drawn taller or wider than the box. KaiPath fits the
     * hitbox (0.6 x 1.8 blocks) with {@code min(w/0.6, h/1.8) x 0.85 x scale} pixels per block,
     * but DMZ draws the form's geo model times its {@code modelScaling}, and the hitbox never
     * changes. Both come from the server's form config (stack form included, larger axis wins)
     * and the model's geo file, so every giant or wide form is covered, Oozaru and Golden Oozaru
     * first; a form that already fits keeps {@code base}.
     */
    public static float fitScale(Box box, float base, String race, String group, String form,
                                 String stackGroup, String stackForm) {
        FormConfig.FormData fd = null;
        FormConfig.FormData sd = null;
        try {
            if (!lower(group).isEmpty() && !lower(form).isEmpty()) {
                fd = ConfigManager.getForm(lower(race), lower(group), lower(form));
            }
            if (!lower(stackGroup).isEmpty() && !lower(stackForm).isEmpty()) {
                sd = ConfigManager.getStackForm(lower(stackGroup), lower(stackForm));
            }
        } catch (Throwable ignored) {
        }
        if (fd == null && sd == null) {
            return base;
        }
        float sx = Math.max(axis(fd, 0), axis(sd, 0));
        float sy = Math.max(axis(fd, 1), axis(sd, 1));
        float[] geo = geoSize(customModel(fd, sd));
        double perBlock = Math.min(box.w() / 0.6, box.h() / 1.8) * 0.85;
        double heightFit = (box.h() - 6) / (geo[0] / 16.0 * sy * perBlock);
        double widthFit = (box.w() - 4) / (geo[1] / 16.0 * sx * perBlock);
        return (float) Math.max(0.1, Math.min(base, Math.min(heightFit, widthFit)));
    }

    /** {@link #fitScale} for the form {@code player} wears now (base when none). */
    public static float wornScale(ServerPlayer player, Box box, float base) {
        try {
            Character ch = DmzProgression.character(player);
            if (ch == null) {
                return base;
            }
            boolean form = ch.hasActiveForm();
            boolean stack = ch.hasActiveStackForm();
            return fitScale(box, base, DmzProgression.race(player),
                    form ? ch.getActiveFormGroup() : "", form ? ch.getActiveForm() : "",
                    stack ? ch.getActiveStackFormGroup() : "", stack ? ch.getActiveStackForm() : "");
        } catch (Throwable ignored) {
            return base;
        }
    }

    /** {@link #fitScale} for a {@link Form}. */
    public static float formScale(Form form, Box box, float base) {
        return form == null ? base : fitScale(box, base, form.race(), form.group(), form.form(), "", "");
    }

    private static float axis(FormConfig.FormData data, int i) {
        try {
            Float[] scaling = data == null ? null : data.getModelScaling();
            if (scaling != null && scaling.length > i && scaling[i] != null && scaling[i] > 0f) {
                return scaling[i];
            }
        } catch (Throwable ignored) {
        }
        return DMZ_BASE_MODEL_SCALE;
    }

    private static String customModel(FormConfig.FormData fd, FormConfig.FormData sd) {
        for (FormConfig.FormData d : new FormConfig.FormData[] {fd, sd}) {
            try {
                if (d != null && Boolean.TRUE.equals(d.hasCustomModel()) && d.getCustomModel() != null
                        && !d.getCustomModel().isBlank()) {
                    return d.getCustomModel().trim().toLowerCase(Locale.ROOT);
                }
            } catch (Throwable ignored) {
            }
        }
        return "";
    }

    /**
     * Height and width in geo pixels of a DMZ race model (assets/dragonminez/geo/entity/races/
     * MODEL.geo.json, read from the DMZ jar on the server). The player model when unknown.
     */
    static float[] geoSize(String model) {
        if (model == null || model.isEmpty()) {
            return PLAYER_GEO;
        }
        return GEO_SIZE.computeIfAbsent(model, ModelPreview::readGeoSize);
    }

    private static float[] readGeoSize(String model) {
        String path = "assets/dragonminez/geo/entity/races/" + model + ".geo.json";
        for (ClassLoader loader : new ClassLoader[] {ConfigManager.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader()}) {
            if (loader == null) {
                continue;
            }
            try (InputStream in = loader.getResourceAsStream(path)) {
                if (in == null) {
                    continue;
                }
                float lowY = Float.MAX_VALUE;
                float highY = -Float.MAX_VALUE;
                float lowX = Float.MAX_VALUE;
                float highX = -Float.MAX_VALUE;
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement g : root.getAsJsonArray("minecraft:geometry")) {
                    JsonArray bones = g.getAsJsonObject().getAsJsonArray("bones");
                    for (JsonElement b : bones == null ? new JsonArray() : bones) {
                        JsonArray cubes = b.getAsJsonObject().getAsJsonArray("cubes");
                        for (JsonElement c : cubes == null ? new JsonArray() : cubes) {
                            JsonArray o = c.getAsJsonObject().getAsJsonArray("origin");
                            JsonArray sz = c.getAsJsonObject().getAsJsonArray("size");
                            lowX = Math.min(lowX, o.get(0).getAsFloat());
                            highX = Math.max(highX, o.get(0).getAsFloat() + sz.get(0).getAsFloat());
                            lowY = Math.min(lowY, o.get(1).getAsFloat());
                            highY = Math.max(highY, o.get(1).getAsFloat() + sz.get(1).getAsFloat());
                        }
                    }
                }
                if (highY > lowY && highX > lowX) {
                    return new float[] {Math.max(PLAYER_GEO[0], highY - lowY), Math.max(PLAYER_GEO[1], highX - lowX)};
                }
            } catch (Throwable ignored) {
            }
        }
        return PLAYER_GEO;
    }

    private static ServerPlayer viewer(ServerPlayer player) {
        ServerPlayer inspecting = UltraScreenSpec.inspectViewer();
        return inspecting != null ? inspecting : player;
    }

    /**
     * Base form first, then every form in the server's DragonMineZ form config for the
     * player's race, in config order. Groups whose transformation skill the race may not
     * learn are left out (the same filter as the mastery screen). Keys come straight from
     * the config, never built by hand: the client only draws forms its synced config knows.
     */
    public static List<Form> raceForms(ServerPlayer player) {
        List<Form> out = new ArrayList<>();
        String race = DmzProgression.race(player);
        if (race.isEmpty()) {
            return out;
        }
        out.add(new Form(race, "", "", "Base form"));
        Map<String, FormConfig> groups = null;
        try {
            groups = ConfigManager.getAllFormsForRace(race);
        } catch (Throwable ignored) {
        }
        if (groups == null) {
            return out;
        }
        SkillsConfig skills = null;
        try {
            skills = ConfigManager.getSkillsConfig();
        } catch (Throwable ignored) {
        }
        for (Map.Entry<String, FormConfig> entry : groups.entrySet()) {
            try {
                FormConfig cfg = entry.getValue();
                if (cfg == null || cfg.getForms() == null) {
                    continue;
                }
                String group = cfg.getGroupName() == null || cfg.getGroupName().isBlank()
                        ? entry.getKey() : cfg.getGroupName();
                String type = cfg.getFormType() == null ? "" : cfg.getFormType().toLowerCase(Locale.ROOT);
                if (skills != null
                        && !skills.isSkillAllowedForRace(TransformationsHelper.getSkillNameForType(type), race)) {
                    continue;
                }
                for (Map.Entry<String, FormConfig.FormData> f : cfg.getForms().entrySet()) {
                    String name = f.getValue() != null && f.getValue().getName() != null
                            && !f.getValue().getName().isBlank() ? f.getValue().getName() : f.getKey();
                    if (group == null || group.isBlank() || name == null || name.isBlank()) {
                        continue;
                    }
                    String label = DmzLang.text("race.dragonminez." + race + ".form." + group + "." + name,
                            DmzLang.titleCase(name));
                    out.add(new Form(race, group, name, label));
                }
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    /** Remember the form the player is looking at, for the rival screen's "you" view. */
    public static void pick(ServerPlayer player, Form form) {
        if (player != null && form != null) {
            PICKED.put(player.m_20148_(), form);
        }
    }

    /**
     * The form shown for "you": the form last picked on the form preview page if it is still
     * one of the race's forms, else the form the player wears now (base when none).
     */
    public static Form selectedForm(ServerPlayer player) {
        List<Form> forms = raceForms(player);
        if (forms.isEmpty()) {
            return null;
        }
        Form picked = PICKED.get(player.m_20148_());
        if (picked != null) {
            for (Form f : forms) {
                if (f.group().equals(picked.group()) && f.form().equals(picked.form())) {
                    return f;
                }
            }
        }
        try {
            Character ch = DmzProgression.character(player);
            if (ch != null && ch.hasActiveForm()) {
                String group = lower(ch.getActiveFormGroup());
                String form = lower(ch.getActiveForm());
                for (Form f : forms) {
                    if (f.group().equalsIgnoreCase(group) && f.form().equalsIgnoreCase(form)) {
                        return f;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return forms.get(0);
    }

    /**
     * Schema-1 parts for a made-up figure of {@code owner}'s race in {@code form}. Body parts are
     * the form's pieces from the active Ultra catalog; no colors are sent. The subject id is
     * fixed per owner, not per form: races whose DragonMineZ body is drawn from the Minecraft
     * skin (Saiyan, Human) get a default skin picked from that id, so one id keeps the same body
     * while the forms change.
     */
    public static JsonObject formFigure(ServerPlayer owner, Form form) {
        if (owner == null || form == null) {
            return null;
        }
        String key = "lm-preview:" + owner.m_20148_();
        JsonObject parts = new JsonObject();
        parts.addProperty("schema", PlayerModelParts.SCHEMA);
        parts.addProperty("subject", "uuid:" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)));
        parts.add("form", formJson(form.race(), form.group(), form.form()));
        JsonArray body = new JsonArray();
        try {
            for (BodyPartCatalog.Part part : BodyPartCatalog.resolve(form.race(), form.group(), form.form(), "", "")) {
                if (part == null || part.id() == null || part.id().isBlank()) {
                    continue;
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("id", part.id());
                entry.addProperty("source", part.source());
                entry.addProperty("slot", part.slot());
                body.add(entry);
            }
        } catch (Throwable ignored) {
        }
        parts.add("bodyParts", body);
        parts.addProperty("suppressNaturalTail", PlayerModelParts.hasChosenTail(body));
        parts.add("extras", new JsonArray());
        return parts;
    }

    /**
     * The rival on the rival screen: {@code rival} through the same builder staff inspect
     * uses ({@code subject uuid:<rival>}). Null when the rival is not online, so the caller
     * shows the viewer instead. A client that has the rival loaded nearby draws them as they
     * are; any other client draws a stand-in of their race and form with default colors,
     * which is why the race is always sent, base form included.
     */
    public static JsonObject rivalFigure(ServerPlayer viewer, ServerPlayer rival) {
        if (rival == null) {
            return null;
        }
        JsonObject parts = PlayerModelParts.otherPlayerSnapshot(rival);
        if (parts != null && !parts.has("form")) {
            parts.add("form", formJson(DmzProgression.race(rival), "", ""));
        }
        AdaptiveDifficultyMod.LOGGER.debug("[{}] rival preview viewer={} rival={} parts={}",
                AdaptiveDifficultyMod.MOD_ID, viewer == null ? "-" : viewer.m_7755_().getString(),
                rival.m_7755_().getString(), parts);
        return parts;
    }

    private static JsonObject formJson(String race, String group, String form) {
        JsonObject f = new JsonObject();
        f.addProperty("race", lower(race));
        f.addProperty("group", lower(group));
        f.addProperty("form", lower(form));
        f.addProperty("stackGroup", "");
        f.addProperty("stackForm", "");
        return f;
    }

    private static String lower(String v) {
        return v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
    }
}
