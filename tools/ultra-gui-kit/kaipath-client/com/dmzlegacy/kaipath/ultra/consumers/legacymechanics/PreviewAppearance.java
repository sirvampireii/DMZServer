package com.dmzlegacy.kaipath.ultra.consumers.legacymechanics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;

/**
 * Wears {@code playerModel.parts.form} (and {@code headBones}) on the preview figure for one draw,
 * then puts the figure's own values back. Mirrors KaiPath 1.5.6 {@code PreviewAppearance}.
 *
 * <p>Why: LM's form preview ({@code ModelPreview.formFigure}) draws a made-up {@code uuid:} subject.
 * The client resolves it to a stand-in {@code RemotePlayer} whose DragonMineZ {@code Character} is
 * blank, so DMZUltra {@code FormParts.of} reads no race and no form, and the previewed form's look
 * (and the Body Parts Shop pieces merged into it by {@link BodyPartPreview#mergeLook}) never shows.
 * A real player in view keeps its own state unless the payload names another form.
 *
 * <p>Order at the draw: {@code PreviewAppearance.begin} first, then {@link BodyPartPreview#begin},
 * render, close both (inner first). Every DragonMineZ call is reflective: the kit compiles against
 * stubs, and a missing or changed DragonMineZ leaves the plain figure.
 *
 * <p>Fields set ({@code Character}, private Strings): {@code race}, {@code activeFormGroup},
 * {@code activeForm}, {@code activeStackFormGroup}, {@code activeStackForm}, {@code activeHeadBone}.
 * When race or form change, {@code oozaruCached} is saved and {@code updateOozaruCache()} runs.
 */
public final class PreviewAppearance {
    private PreviewAppearance() {}

    /** Name, group and form of a previewed form (for the form-exists check). */
    public record Form(String race, String group, String form) {}

    /**
     * Apply {@code parts.form} / {@code parts.headBones} to {@code entity}'s Character until the
     * returned scope closes. Never throws; a no-op scope when nothing applies.
     */
    public static Scope begin(LivingEntity entity, JsonObject parts) {
        if (entity == null || parts == null) {
            return new Scope(null);
        }
        try {
            Object character = character(entity);
            if (character == null) {
                return new Scope(null);
            }
            Object raceName = character.getClass().getMethod("getRaceName").invoke(character);
            String current = raceName == null ? "" : String.valueOf(raceName);
            return apply(character, changes(current, parts,
                    PreviewAppearance::raceLoaded,
                    PreviewAppearance::formExists,
                    PreviewAppearance::stackFormExists));
        } catch (Throwable ignored) {
            return new Scope(null);
        }
    }

    /**
     * Character field → value for {@code parts}. Pure (no DragonMineZ), so the kit test can run it.
     * {@code parts.form.race} empty means the figure's own race. A form object that names
     * {@code group}/{@code form} (or a new race) sets the active form, empty strings included (empty
     * = base form). The form must exist ({@code formOk}) unless it is the base form; the stack form
     * likewise ({@code stackOk}). {@code headBones.token}, else {@code headBones.pieces} joined with
     * {@code +}, sets the head bone.
     */
    static Map<String, String> changes(
            String currentRace,
            JsonObject parts,
            Predicate<String> raceOk,
            Predicate<Form> formOk,
            BiPredicate<String, String> stackOk) {
        Map<String, String> out = new LinkedHashMap<>();
        if (parts == null) {
            return out;
        }
        String current = currentRace == null ? "" : currentRace;
        if (parts.has("form") && parts.get("form").isJsonObject()) {
            JsonObject form = parts.getAsJsonObject("form");
            String race = text(form, "race");
            if (race.isEmpty()) {
                race = current;
            }
            String group = text(form, "group");
            String name = text(form, "form");
            boolean named = form.has("group") || form.has("form");
            if (stringFields(form, "race", "group", "form")
                    && raceOk.test(race)
                    && (!named || (group.isEmpty() && name.isEmpty()) || formOk.test(new Form(race, group, name)))) {
                boolean raceChanged = !race.equalsIgnoreCase(current);
                if (form.has("race") && !text(form, "race").isEmpty()) {
                    out.put("race", race);
                }
                if (named || raceChanged) {
                    out.put("activeFormGroup", group);
                    out.put("activeForm", name);
                }
                String stackGroup = text(form, "stackGroup");
                String stackForm = text(form, "stackForm");
                boolean stackNamed = form.has("stackGroup") || form.has("stackForm");
                if ((stackNamed || raceChanged)
                        && stringFields(form, "stackGroup", "stackForm")
                        && ((stackGroup.isEmpty() && stackForm.isEmpty()) || stackOk.test(stackGroup, stackForm))) {
                    out.put("activeStackFormGroup", stackGroup);
                    out.put("activeStackForm", stackForm);
                }
            }
        }
        if (parts.has("headBones") && parts.get("headBones").isJsonObject()) {
            JsonObject head = parts.getAsJsonObject("headBones");
            if (head.has("token") && head.get("token").isJsonPrimitive()
                    && head.get("token").getAsJsonPrimitive().isString()) {
                out.put("activeHeadBone", head.get("token").getAsString());
            } else if (head.has("pieces") && head.get("pieces").isJsonArray()) {
                List<String> pieces = new ArrayList<>();
                for (JsonElement e : head.getAsJsonArray("pieces")) {
                    if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                        pieces.add(e.getAsString());
                    }
                }
                if (!pieces.isEmpty() || head.getAsJsonArray("pieces").isEmpty()) {
                    out.put("activeHeadBone", String.join("+", pieces));
                }
            }
        }
        return out;
    }

    /** Set each named String field on {@code character}; the scope restores them. */
    static Scope apply(Object character, Map<String, String> changes) {
        Scope scope = new Scope(character);
        if (character == null || changes == null || changes.isEmpty()) {
            return scope;
        }
        try {
            for (Map.Entry<String, String> e : changes.entrySet()) {
                Field field = character.getClass().getDeclaredField(e.getKey());
                if (field.getType() != String.class || Modifier.isFinal(field.getModifiers())) {
                    throw new IllegalArgumentException("Not a cosmetic string field");
                }
                field.setAccessible(true);
                scope.previous.putIfAbsent(field, field.get(character));
                field.set(character, e.getValue());
            }
            if (changes.containsKey("race") || changes.containsKey("activeForm")) {
                try {
                    Field oozaru = character.getClass().getDeclaredField("oozaruCached");
                    oozaru.setAccessible(true);
                    scope.previous.putIfAbsent(oozaru, oozaru.get(character));
                    Method update = character.getClass().getMethod("updateOozaruCache");
                    update.invoke(character);
                } catch (ReflectiveOperationException ignored) {
                    // an older DragonMineZ without the cache
                }
            }
        } catch (ReflectiveOperationException | RuntimeException failed) {
            scope.close();
            return new Scope(null);
        }
        return scope;
    }

    private static boolean stringFields(JsonObject o, String... keys) {
        for (String key : keys) {
            if (o.has(key) && !(o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isString())) {
                return false;
            }
        }
        return true;
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()
                ? e.getAsString().trim().toLowerCase(Locale.ROOT)
                : "";
    }

    /** DragonMineZ {@code StatsProvider.get(StatsCapability.INSTANCE, entity)} → Character, or null. */
    private static Object character(LivingEntity entity) throws ReflectiveOperationException {
        Class<?> capClass = Class.forName("com.dragonminez.common.stats.StatsCapability");
        Object cap = capClass.getField("INSTANCE").get(null);
        Class<?> provider = Class.forName("com.dragonminez.common.stats.StatsProvider");
        Class<?> capability = Class.forName("net.minecraftforge.common.capabilities.Capability");
        Class<?> entityType = Class.forName("net.minecraft.world.entity.Entity");
        Object lazy = provider.getMethod("get", capability, entityType).invoke(null, cap, entity);
        if (lazy == null) {
            return null;
        }
        Object resolved = lazy.getClass().getMethod("resolve").invoke(lazy);
        Object stats = resolved instanceof java.util.Optional<?> opt ? opt.orElse(null) : null;
        if (stats == null) {
            return null;
        }
        return stats.getClass().getMethod("getCharacter").invoke(stats);
    }

    private static Object config(String method, Class<?>[] types, Object... args) {
        try {
            Class<?> cfg = Class.forName("com.dragonminez.common.config.ConfigManager");
            return cfg.getMethod(method, types).invoke(null, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean raceLoaded(String race) {
        return Boolean.TRUE.equals(config("isRaceLoaded", new Class<?>[] {String.class}, race));
    }

    private static boolean formExists(Form f) {
        return config("getForm", new Class<?>[] {String.class, String.class, String.class},
                f.race(), f.group(), f.form()) != null;
    }

    private static boolean stackFormExists(String group, String form) {
        return config("getStackForm", new Class<?>[] {String.class, String.class}, group, form) != null;
    }

    /** Restores every field {@link #apply} changed. Close once; later closes do nothing. */
    public static final class Scope implements AutoCloseable {
        private final Object character;
        private final Map<Field, Object> previous = new LinkedHashMap<>();
        private boolean closed;

        private Scope(Object character) {
            this.character = character;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (Map.Entry<Field, Object> e : previous.entrySet()) {
                try {
                    e.getKey().set(character, e.getValue());
                } catch (IllegalAccessException failed) {
                    throw new IllegalStateException("Cannot restore preview appearance", failed);
                }
            }
        }
    }
}
