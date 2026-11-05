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

public final class NbVanityRenderer implements ICurioRenderer {
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
      if (!(stack.m_41720_() instanceof NbVanityItem item) || !item.entry().isAnchoredModel() || item.entry().vanityType == null || NbItems.dragonMineZLoaded()
         )
       {
         return;
      }

      if (parent.m_7200_() instanceof HumanoidModel<?> host) {
         NbVanityModel model = NbClientModels.vanity(item.entry().vanityType);
         if (model != null && !slotContext.entity().m_20145_()) {
            model.poseFrom(host);
            model.look(netHeadYaw, headPitch);
            model.animate(item.entry().vanityType, limbSwing, limbSwingAmount, slotContext.entity().m_6047_(), ageInTicks);
            int color = item.wornColor(stack);
            float r = (float)(color >> 16 & 0xFF) / 255.0F;
            float g = (float)(color >> 8 & 0xFF) / 255.0F;
            float b = (float)(color & 0xFF) / 255.0F;
            ResourceLocation texture = NBArmors.id("textures/armor/" + item.entry().wornTexture(1));
            VertexConsumer buffer = ItemRenderer.m_115184_(buffers, RenderType.m_110431_(texture), false, stack.m_41790_());
            model.m_7695_(pose, buffer, light, OverlayTexture.f_118083_, r, g, b, 1.0F);
            return;
         }

         return;
      }
   }
}
