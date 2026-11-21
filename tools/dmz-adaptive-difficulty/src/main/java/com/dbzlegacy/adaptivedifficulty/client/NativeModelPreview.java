package com.dbzlegacy.adaptivedifficulty.client;

import com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeProtocol;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;

/**
 * The menu's player model ({@code layoutJson.playerModel}), drawn with the vanilla in-GUI entity
 * renderer, so DragonMineZ draws the figure's real race, hair and form.
 *
 * <ul>
 *   <li>{@code subject local} (or none) is the viewer; a UUID is that player when this client tracks
 *       them, else their tab-list profile (their skin). A UUID nobody has (a made-up preview figure)
 *       is a stand-in that wears the viewer's skin, never the vanilla default.
 *   <li>{@code rotate true} turns the figure slowly. A menu that arrives with {@code rotate false}
 *       puts it back front-facing, the character's front toward the viewer, never the back (the spin
 *       toggle no longer snaps to a stale angle).
 *   <li>Name tags never draw over the preview.
 * </ul>
 */
final class NativeModelPreview {
    private NativeModelPreview() {}

    /** Turntable speed, degrees per second. */
    private static final float SPIN_DEG_PER_SEC = 36f;
    private static float yaw;
    private static long lastNanos;
    private static Entity drawing;
    private static ClientLevel standInLevel;
    private static final Map<UUID, AbstractClientPlayer> STAND_INS = new HashMap<>();

    /** A menu arrived: a still model is front-facing again (front toward the viewer, never the back). */
    static void onPacket(boolean rotate) {
        yaw = LmNativeProtocol.spinAfterPacket(yaw, rotate);
        lastNanos = 0L;
    }

    static float yaw() {
        return yaw;
    }

    static boolean isDrawing(Entity e) {
        return e != null && e == drawing;
    }

    /** Who the preview names, or null when nobody can be drawn (no level yet). */
    static LivingEntity resolve(JsonObject model) {
        Minecraft mc = Minecraft.m_91087_();
        LocalPlayer viewer = mc.f_91074_;
        ClientLevel level = mc.f_91073_;
        if (viewer == null || level == null) {
            return null;
        }
        JsonObject parts = model.has("parts") && model.get("parts").isJsonObject() ? model.getAsJsonObject("parts") : null;
        UUID id = LmNativeProtocol.subjectId(model, parts);
        if (id == null || id.equals(viewer.m_20148_())) {
            return viewer;
        }
        Player tracked = level.m_46003_(id);
        if (tracked != null) {
            return tracked;
        }
        if (standInLevel != level) {
            STAND_INS.clear();
            standInLevel = level;
        }
        AbstractClientPlayer cached = STAND_INS.get(id);
        if (cached != null) {
            NativeFormLook.apply(cached, parts);
            return cached;
        }
        PlayerInfo info = mc.m_91403_() == null ? null : mc.m_91403_().m_104949_(id);
        AbstractClientPlayer made = info != null
                ? new RemotePlayer(level, info.m_105312_())
                : new StandIn(level, new GameProfile(id, "Preview"), viewer);
        STAND_INS.put(id, made);
        // 4.7.61: a made-up figure wears the race and form the menu names (Preview forms).
        NativeFormLook.apply(made, parts);
        return made;
    }

    /** A made-up figure: the viewer's skin and arm model instead of the default one for its UUID. */
    private static final class StandIn extends RemotePlayer {
        private final LocalPlayer viewer;

        StandIn(ClientLevel level, GameProfile profile, LocalPlayer viewer) {
            super(level, profile);
            this.viewer = viewer;
        }

        @Override
        public ResourceLocation m_108560_() {
            return viewer.m_108560_();
        }

        @Override
        public String m_108564_() {
            return viewer.m_108564_();
        }

        @Override
        public boolean m_6052_() {
            return false;
        }
    }

    /**
     * Draw {@code entity} standing in the box ({@code x,y,w,h} in the current pose), feet at the
     * bottom. {@code scale} is the spec's entity scale (1 = fill the box height).
     */
    static void render(GuiGraphics g, int x, int y, int w, int h, float scale, boolean rotate, LivingEntity entity,
            JsonObject parts) {
        if (rotate) {
            long now = System.nanoTime();
            if (lastNanos != 0L) {
                float dt = Math.min(0.25f, (now - lastNanos) / 1.0e9f);
                yaw = (yaw + dt * SPIN_DEG_PER_SEC) % 360f;
            }
            lastNanos = now;
        } else {
            yaw = LmNativeProtocol.spinAfterPacket(yaw, false);
            lastNanos = 0L;
        }
        int size = Math.max(8, Math.round(h * 0.42f * Math.max(0.1f, scale)));
        int cx = x + w / 2;
        int feet = y + h - 4;
        float bodyRot = entity.f_20883_;
        float bodyRotO = entity.f_20884_;
        float yRot = entity.m_146908_();
        float yRotO = entity.f_19859_;
        float xRot = entity.m_146909_();
        float xRotO = entity.f_19860_;
        float headO = entity.f_20886_;
        float head = entity.f_20885_;
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf();
        float facing = LmNativeProtocol.previewFacing(yaw);
        entity.f_20883_ = facing;
        entity.f_20884_ = facing;
        entity.m_146922_(facing);
        entity.f_19859_ = facing;
        entity.m_146926_(0f);
        entity.f_19860_ = 0f;
        entity.f_20885_ = facing;
        entity.f_20886_ = facing;
        drawing = entity;
        // 4.7.61: the preview's bodyParts join DMZUltra's look while this figure draws (FormPartsPreviewMixin).
        BodyPartPreview.begin(entity, parts);
        try {
            InventoryScreen.m_280432_(g, cx, feet, size, pose, camera, entity);
        } finally {
            BodyPartPreview.end();
            drawing = null;
            entity.f_20883_ = bodyRot;
            entity.f_20884_ = bodyRotO;
            entity.m_146922_(yRot);
            entity.f_19859_ = yRotO;
            entity.m_146926_(xRot);
            entity.f_19860_ = xRotO;
            entity.f_20886_ = headO;
            entity.f_20885_ = head;
        }
    }

    /** Drop stand-ins (logout / new world). */
    static void clear() {
        STAND_INS.clear();
        NativeFormLook.clear();
        standInLevel = null;
        yaw = 0f;
        lastNanos = 0L;
    }
}
