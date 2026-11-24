package com.dbzlegacy.adaptivedifficulty.client;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;

/**
 * LM 4.7.61: dresses a made-up preview figure (Character Services, Preview forms) in the race and form the
 * menu names ({@code playerModel.parts.form}: race, group, form, stackGroup, stackForm), so DragonMineZ and
 * DMZUltra 0.5.7 draw it in that form. Only ever called for a client-side stand-in, never for the viewer
 * or a real player. Any DragonMineZ API change leaves the plain figure.
 */
final class NativeFormLook {
    private NativeFormLook() {}

    /** Last form applied to each stand-in, so a still page does not reset DragonMineZ data every frame. */
    private static final Map<LivingEntity, String> APPLIED = new WeakHashMap<>();

    static void apply(LivingEntity standIn, JsonObject parts) {
        if (standIn == null || parts == null || !parts.has("form") || !parts.get("form").isJsonObject()) {
            return;
        }
        JsonObject form = parts.getAsJsonObject("form");
        String look = text(parts, "appearance");
        String key = form.toString() + "|" + look.hashCode();
        if (key.equals(APPLIED.get(standIn))) {
            return;
        }
        try {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, standIn).orElse(null);
            if (data == null) {
                return;
            }
            Character ch = data.getCharacter();
            // 4.7.71: the owner's own look first (colors, hair, eyes, body type), then the previewed race and form,
            // so DragonMineZ applies the form's own overrides on top of the owner's base colors.
            if (!look.isEmpty()) {
                try {
                    ch.loadAppearance(net.minecraft.nbt.NbtIo.m_128939_(
                            new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(look))));
                } catch (Throwable ignored) {
                    // an appearance this DragonMineZ cannot read: the figure keeps its defaults
                }
            }
            String race = text(form, "race");
            if (!race.isEmpty()) {
                ch.setRace(race);
            }
            String group = text(form, "group");
            String name = text(form, "form");
            if (!group.isEmpty() && !name.isEmpty()) {
                ch.setActiveForm(group, name);
            } else {
                ch.clearActiveForm();
            }
            String stackGroup = text(form, "stackGroup");
            String stackForm = text(form, "stackForm");
            if (!stackGroup.isEmpty() && !stackForm.isEmpty()) {
                ch.setActiveStackForm(stackGroup, stackForm);
            } else {
                ch.clearActiveStackForm();
            }
            data.getStatus().setHasCreatedCharacter(true);
            APPLIED.put(standIn, key);
        } catch (Throwable ignored) {
            // DragonMineZ missing or changed: the plain figure draws
        }
    }

    static void clear() {
        APPLIED.clear();
    }

    private static String text(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString().trim() : "";
    }
}
