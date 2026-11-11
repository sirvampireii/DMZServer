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
import com.google.gson.JsonObject;
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

    /** The spin toggle under a page's buttons. {@code page} is the page it reopens. */
    public static void rotateButton(UltraScreenSpec spec, ServerPlayer player, String page) {
        boolean on = rotate(player);
        spec.button("do:" + ROTATE_ACTION + ":" + page,
                        on ? "Stop the spin" : "Spin the model",
                        "normal")
                .rowGap();
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
