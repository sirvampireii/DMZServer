package com.dbzlegacy.legacytextures.client;

import com.dbzlegacy.legacytextures.LegacyTextures;
import com.dragonminez.client.util.SkinGathererProvider;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.RaceCharacterConfig;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

/**
 * One set per character (0.2.7). A Saiyan or human whose body type exists only in the HD set (types
 * 3-7) is drawn from HD even for a vanilla viewer, or there is no body at all (0.2.2); without this the
 * face on that HD body stayed vanilla. Between {@link #begin} and {@link #end} (DMZSkinLayer.renderFace)
 * {@link #key} hands DragonMineZ's face layers their HD file for such a player, so face and body match.
 * Everyone else, and every HD viewer, is unchanged. Fail-soft: any error leaves the face as it was.
 */
public final class HdFace {
    private static final String BODY = "textures/entity/races/humansaiyan/bodytype_%s_%d_layer1.png";
    private static boolean active;
    private static boolean logged;

    private HdFace() {
    }

    public static void begin(StatsData stats) {
        active = false;
        try {
            active = !ClientState.mode().players && hdOnlyBody(stats.getCharacter());
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

    /** DragonMineZ's humanoid body file (SkinGathererProvider.resolveBodyHumanSaiyan) exists only in HD. */
    private static boolean hdOnlyBody(Character c) {
        int bodyType = c.getBodyType();
        if (bodyType == 0) {
            return false; // the player's own skin
        }
        String race = c.getRaceName().toLowerCase(Locale.ROOT);
        RaceCharacterConfig config = ConfigManager.getRaceCharacter(race);
        String model = config != null && Boolean.TRUE.equals(config.hasCustomModel())
                ? config.getCustomModel().toLowerCase(Locale.ROOT) : "";
        if (!SkinGathererProvider.isHumanoidKey(model.isEmpty() ? race : model)) {
            return false;
        }
        String gender = "female".equals(c.getGender().toLowerCase(Locale.ROOT).trim()) ? "female" : "male";
        ResourceLocation body = ResourceLocation.m_214293_("dragonminez", String.format(Locale.ROOT, BODY, gender, bodyType));
        return !ClientState.RESOLVER.exists(body) && !ClientState.RESOLVER.hdVariant(body).equals(body);
    }
}
