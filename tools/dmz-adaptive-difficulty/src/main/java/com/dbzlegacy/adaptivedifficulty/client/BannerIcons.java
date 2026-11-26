package com.dbzlegacy.adaptivedifficulty.client;

import com.dbzlegacy.adaptivedifficulty.gui.ultra.BannerDesign;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.Holder;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;

/**
 * LM 4.8.2: draws a banner icon ({@code lmbanner:<base>/<code>:<dye id>,...}, see {@link BannerDesign}) on the LM
 * client. A button icon is the vanilla banner item with its {@code BlockEntityTag.Patterns}, which the item renderer
 * draws with every layer. A picture (the editor's live preview, a dojo's profile) is the flat banner cloth as the loom
 * draws its result: the base, then each layer in order. Built stacks and pattern lists are cached per icon string
 * (menus draw every frame).
 */
final class BannerIcons {
    private BannerIcons() {}

    private static final int CACHE = 128;
    private static final Map<String, ItemStack> STACKS = new LinkedHashMap<>();
    private static final Map<String, List<Pair<Holder<BannerPattern>, DyeColor>>> CLOTH = new LinkedHashMap<>();
    /** The loom's flag part (20 x 40 model units), baked once. */
    private static ModelPart flag;

    static boolean isBanner(String icon) {
        return BannerDesign.isIcon(icon);
    }

    static ItemStack stack(String icon) {
        ItemStack s = STACKS.get(icon);
        if (s == null) {
            if (STACKS.size() >= CACHE) {
                STACKS.clear();
            }
            s = BannerDesign.stack(icon);
            STACKS.put(icon, s);
        }
        return s;
    }

    /** The banner item, {@code size} px square at (x, y). False when the icon is not a valid banner icon. */
    static boolean drawItem(GuiGraphics g, String icon, int x, int y, int size) {
        ItemStack s = stack(icon);
        if (s.m_41619_()) {
            return false;
        }
        PoseStack pose = g.m_280168_();
        pose.m_85836_();
        pose.m_252880_(x, y, 0f);
        pose.m_85841_(size / 16f, size / 16f, 1f);
        g.m_280480_(s, 0, 0);
        pose.m_85849_();
        return true;
    }

    private static List<Pair<Holder<BannerPattern>, DyeColor>> cloth(String icon) {
        List<Pair<Holder<BannerPattern>, DyeColor>> c = CLOTH.get(icon);
        if (c == null) {
            BannerDesign.Parsed p = BannerDesign.parse(icon);
            if (p == null) {
                return null;
            }
            if (CLOTH.size() >= CACHE) {
                CLOTH.clear();
            }
            c = BannerBlockEntity.m_58484_(DyeColor.m_41057_(p.base(), DyeColor.WHITE),
                    BannerDesign.patternsTag(p.layers()));
            CLOTH.put(icon, c);
        }
        return c;
    }

    /**
     * The flat banner cloth, as large as fits (x, y, w, h) at its 1:2 shape, centred. Falls back to the banner item
     * when the cloth cannot be drawn.
     */
    static boolean drawCloth(GuiGraphics g, String icon, int x, int y, int w, int h) {
        List<Pair<Holder<BannerPattern>, DyeColor>> layers = cloth(icon);
        if (layers == null) {
            return false;
        }
        float k = Math.max(0.1f, Math.min(w / 20f, h / 40f));
        float fx = x + (w - 20f * k) / 2f;
        float fy = y + (h - 40f * k) / 2f;
        PoseStack pose = g.m_280168_();
        pose.m_85836_();
        boolean pushed = true;
        try {
            if (flag == null) {
                flag = Minecraft.m_91087_().m_167973_().m_171103_(ModelLayers.f_171264_).m_171324_("flag");
            }
            // The loom's result transform (LoomScreen.renderBg) draws the 20 x 40 cloth at x 2..22, y -44..-4 of its
            // origin; move that box to (fx, fy) and scale it by k.
            pose.m_252880_(fx, fy, 100f);
            pose.m_85841_(k, k, 1f);
            pose.m_252880_(-2f, 44f, 0f);
            pose.m_85841_(24f, -24f, 1f);
            pose.m_252880_(0.5f, 0.5f, 0.5f);
            pose.m_85841_(2f / 3f, -2f / 3f, -2f / 3f);
            flag.f_104203_ = 0f;
            flag.f_104201_ = -32f;
            Lighting.m_84930_();
            BannerRenderer.m_112065_(pose, g.m_280091_(), 15728880, OverlayTexture.f_118083_, flag,
                    ModelBakery.f_119224_, true, layers);
            pose.m_85849_();
            pushed = false;
            g.m_280262_();
            Lighting.m_84931_();
            return true;
        } catch (RuntimeException e) {
            if (pushed) {
                pose.m_85849_();
            }
            Lighting.m_84931_();
            int side = Math.min(w, h);
            return drawItem(g, icon, x + (w - side) / 2, y + (h - side) / 2, side);
        }
    }
}
