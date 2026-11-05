package com.dbzlegacy.legacytextures.nb.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

public final class NbArmorModel extends HumanoidModel<LivingEntity> implements NbPartModel {
   ArmorSway.Profile swayProfile = ArmorSway.ARMOR;

   public NbArmorModel(ModelPart root) {
      super(root);
   }

   public void sway(LivingEntity wearer, float partialTick) {
      this.sway(wearer.f_267362_.m_267590_(partialTick), Math.min(1.0F, wearer.f_267362_.m_267711_(partialTick)));
   }

   public void sway(float limbSwing, float limbSwingAmount) {
      ArmorSway.apply(this.f_102810_, this.swayProfile, limbSwingAmount, limbSwing);
   }

   @Override
   public ModelPart part(String biped) {
      return switch (biped) {
         case "head" -> this.f_102808_;
         case "body" -> this.f_102810_;
         case "right_arm" -> this.f_102811_;
         case "left_arm" -> this.f_102812_;
         case "right_leg" -> this.f_102813_;
         case "left_leg" -> this.f_102814_;
         default -> null;
      };
   }
}
