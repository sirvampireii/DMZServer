package com.dbzlegacy.legacytextures.client;

import com.dbzlegacy.legacytextures.LegacyTextures;
import com.dragonminez.client.util.SkinGathererProvider;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.config.RaceCharacterConfig;
import com.dragonminez.common.stats.FusedData;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * One set per character (0.2.7). A humansaiyan body type that exists only in the HD set (male 3-7,
 * female 3) is drawn from HD even for a vanilla viewer, or there is no body at all (0.2.2); without this
 * the face on that HD body stayed vanilla. Between {@link #begin} and {@link #end} (DMZSkinLayer.renderFace)
 * {@link #key} hands DragonMineZ's face layers their HD file for such a player, so face and body match.
 * Everyone else, and every HD viewer, is unchanged. Fail-soft: any error leaves the face as it was.
 *
 * <p>Since 0.2.8 the body is found the way DragonMineZ 2.2.1.2 finds it, not from the race alone: the
 * model key is the active stack form's custom model, else the form's, else the race's, else the race
 * name (built-in races) or "human" (SkinGathererProvider.activeLogicKey); a fused player drawn with the
 * partner model uses the race's key (gatherBodyLayers). So a Makaijin in a humanoid-model form
 * (festering_monarch: 4arms) is covered, and a Majin in a majin_ form, which draws a Majin body, is not.
 */
public final class HdFace {
    private static final String BODY = "textures/entity/races/humansaiyan/bodytype_";
    private static boolean active;
    private static boolean logged;

    private HdFace() {
    }

    public static void begin(StatsData stats) {
        active = false;
        try {
            active = !ClientState.mode().players && hdOnlyBody(stats);
        } catch (Throwable t) {
            if (!logged) {
                logged = true;
                LegacyTextures.LOG.warn("HD face check failed; faces stay as the viewer's set draws them: {}", t.toString());
            }
        }
    }

    public static void end() {
        active = false;
    }

    /** Whether the face being drawn belongs to a player with an HD-only body (this viewer on vanilla). */
    public static boolean active() {
        return active;
    }

    /** The texture key a face layer should draw with: its HD file while {@link #active}, else {@code loc}. */
    public static ResourceLocation key(ResourceLocation loc) {
        if (!active || loc == null || !"dragonminez".equals(loc.m_135827_())) {
            return loc;
        }
        return ClientState.RESOLVER.hdVariant(loc);
    }

    /** DragonMineZ draws this player's body from a humansaiyan file that exists only in HD. */
    private static boolean hdOnlyBody(StatsData stats) {
        Character c = stats.getCharacter();
        String race = c.getRaceName().toLowerCase(Locale.ROOT);
        RaceCharacterConfig config = ConfigManager.getRaceCharacter(race);
        if (config == null) {
            return false; // gatherBodyLayers draws no body
        }
        String key = modelKey(stats, c, race, config);
        String form = c.getActiveForm();
        if (key.equals("oozaru") || race.equals("saiyan") && (Objects.equals(form, "oozaru") || Objects.equals(form, "goldenoozaru"))) {
            return false; // resolveBodyOozaru
        }
        ResourceLocation body = bodyFile(c, key, config);
        return body != null && !ClientState.RESOLVER.exists(body) && !ClientState.RESOLVER.hdVariant(body).equals(body);
    }

    /** SkinGathererProvider.gatherBodyLayers' logicKey (activeLogicKey, or the race's key for a partner model). */
    private static String modelKey(StatsData stats, Character c, String race, RaceCharacterConfig config) {
        String raceModel = config.getCustomModel() != null ? config.getCustomModel().toLowerCase(Locale.ROOT) : "";
        FusedData fused = stats.getFusedData();
        String key;
        if (fused != null && fused.usesPartnerModel(c)) {
            key = raceModel;
        } else {
            String formModel = "";
            if (c.hasActiveStackForm() && hasModel(c.getActiveStackFormData())) {
                formModel = c.getActiveStackFormData().getCustomModel().toLowerCase(Locale.ROOT);
            } else if (c.hasActiveForm() && hasModel(c.getActiveFormData())) {
                formModel = c.getActiveFormData().getCustomModel().toLowerCase(Locale.ROOT);
            }
            key = formModel.isEmpty() ? raceModel : formModel;
        }
        if (key.isEmpty()) {
            key = SkinGathererProvider.isBuiltInRace(race) ? race : "human";
        }
        return key.equals("human_slim") || key.equals("majin_slim") || key.equals("base_slim") ? race : key;
    }

    private static boolean hasModel(FormConfig.FormData data) {
        return data != null && Boolean.TRUE.equals(data.hasCustomModel());
    }

    /**
     * The humansaiyan layer1 file resolveRaceBody draws for {@code key}, or null when the body is not a
     * humansaiyan file (the player's own skin, a Majin, Frost Demon, BioAndroid, Namekian or custom body).
     */
    private static ResourceLocation bodyFile(Character c, String key, RaceCharacterConfig config) {
        int bodyType = c.getBodyType();
        if (SkinGathererProvider.isHumanoidKey(key)) {
            if (bodyType == 0) {
                return null; // the player's own skin
            }
            // resolveBodyHumanSaiyan
            String gender = c.getGender().toLowerCase(Locale.ROOT).trim().equals("female") ? "female" : "male";
            String base = BODY + gender + "_" + bodyType + "_";
            if (config.isSlimBodyType(bodyType) && !key.equals("glinddark") && drawable(dmz(base + "slim_layer1.png"))) {
                base = base + "slim_";
            }
            return dmz(base + "layer1.png");
        }
        if (key.equals("trascended")) {
            // resolveBodyGlindTranscended: a female's own body type (at least 1), a male's type 1
            boolean female = "female".equalsIgnoreCase(c.getGender());
            return dmz(BODY + (female ? "female_" + Math.max(1, bodyType) : "male_1") + "_layer1.png");
        }
        return null;
    }

    /** What DMZ's getSafeTexture finds for a vanilla viewer: the mod's file, or the HD fallback (0.2.2). */
    private static boolean drawable(ResourceLocation loc) {
        return ClientState.RESOLVER.exists(loc) || !ClientState.RESOLVER.hdVariant(loc).equals(loc);
    }

    private static ResourceLocation dmz(String path) {
        return ResourceLocation.m_214293_("dragonminez", path);
    }
}
