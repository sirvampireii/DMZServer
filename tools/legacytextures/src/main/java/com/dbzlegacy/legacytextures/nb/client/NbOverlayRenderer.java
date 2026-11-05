package com.dbzlegacy.legacytextures.nb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.dbzlegacy.legacytextures.nb.NBArmors;
import com.dbzlegacy.legacytextures.nb.item.NbVanityItem;
import com.dbzlegacy.legacytextures.nb.registry.NbItems;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public final class NbOverlayRenderer implements ICurioRenderer {
   @Override
   public <T extends LivingEntity, M extends EntityModel<T>> void render(
      ItemStack stack,
      SlotContext slotContext,
      PoseStack pose,
      RenderLayerParent<T, M> parent,
      MultiBufferSource buffers,
      int light,
      float limbSwing,
      float limbSwingAmount,
      float partialTicks,
      float ageInTicks,
      float netHeadYaw,
      float headPitch
   ) {
      if (!(stack.m_41720_() instanceof NbVanityItem item) || !item.isOverlay() || NbItems.dragonMineZLoaded()) {
         return;
      }

      if (parent.m_7200_() instanceof HumanoidModel<?> host && !slotContext.entity().m_20145_()) {
         NbArmorModel model = NbClientModels.named(item.entry().model);
         if (model == null) {
            return;
         }

         // Raw call: the original bytecode copies onto the wildcard host model; javac needs the cast in source.
         ((HumanoidModel) host).m_102872_(model);
         model.sway(limbSwing, Math.min(1.0F, limbSwingAmount));
         model.m_8009_(false);
         model.f_102808_.f_104207_ = item.entry().drawsOn("head");
         model.f_102810_.f_104207_ = item.entry().drawsOn("body");
         model.f_102811_.f_104207_ = item.entry().drawsOn("right_arm");
         model.f_102812_.f_104207_ = item.entry().drawsOn("left_arm");
         model.f_102813_.f_104207_ = item.entry().drawsOn("right_leg");
         model.f_102814_.f_104207_ = item.entry().drawsOn("left_leg");
         int color = item.wornColor(stack);
         float r = (float)(color >> 16 & 0xFF) / 255.0F;
         float g = (float)(color >> 8 & 0xFF) / 255.0F;
         float b = (float)(color & 0xFF) / 255.0F;
         ResourceLocation texture = NBArmors.id("textures/armor/" + item.entry().wornTexture(1));
         VertexConsumer buffer = ItemRenderer.m_115184_(buffers, RenderType.m_110431_(texture), false, stack.m_41790_());
         model.m_7695_(pose, buffer, light, OverlayTexture.f_118083_, r, g, b, 1.0F);
         return;
      }
   }
}
