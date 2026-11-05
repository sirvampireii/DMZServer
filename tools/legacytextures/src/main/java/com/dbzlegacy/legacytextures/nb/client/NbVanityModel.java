package com.dbzlegacy.legacytextures.nb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

public final class NbVanityModel extends Model implements NbPartModel {
   private final ModelPart root;
   private final ModelPart head;
   private final ModelPart body;
   private final ModelPart rightArm;
   private final ModelPart leftArm;
   private final ModelPart rightLeg;
   private final ModelPart leftLeg;

   public NbVanityModel(ModelPart root) {
      super(RenderType::m_110458_);
      this.root = root;
      this.head = root.m_171324_("head");
      this.body = root.m_171324_("body");
      this.rightArm = root.m_171324_("right_arm");
      this.leftArm = root.m_171324_("left_arm");
      this.rightLeg = root.m_171324_("right_leg");
      this.leftLeg = root.m_171324_("left_leg");
   }

   public void poseFrom(HumanoidModel<?> host) {
      this.head.m_104315_(host.f_102808_);
      this.body.m_104315_(host.f_102810_);
      this.rightArm.m_104315_(host.f_102811_);
      this.leftArm.m_104315_(host.f_102812_);
      this.rightLeg.m_104315_(host.f_102813_);
      this.leftLeg.m_104315_(host.f_102814_);
   }

   public void look(float headYawDegrees, float headPitchDegrees) {
      if (this.body.m_233562_("Pbody") && this.body.m_171324_("Pbody").m_233562_("Phead")) {
         ModelPart puarHead = this.body.m_171324_("Pbody").m_171324_("Phead");
         puarHead.f_104204_ = headYawDegrees * (float) (Math.PI / 180.0);
         puarHead.f_104203_ = headPitchDegrees * (float) (Math.PI / 180.0);
      }
   }

   public void animate(int type, float limbSwing, float limbSwingAmount, boolean crouching, float ageInTicks) {
      switch (type) {
         case 0:
            if (this.body.m_233562_("BoTbase")) {
               ModelPart base = this.body.m_171324_("BoTbase");
               base.f_233553_ = 1.2F;
               base.f_233554_ = 1.2F;
               base.f_233555_ = 1.2F;
            }
            break;
         case 1:
            if (!this.head.m_233562_("scarfbase")) {
               return;
            }

            ModelPart base = this.head.m_171324_("scarfbase");
            base.f_233553_ = 1.04F;
            base.f_233555_ = 1.04F;
            float swing = Math.abs(Mth.m_14089_(limbSwing * 0.6662F) * limbSwingAmount) / 2.0F;
            base.m_171324_("scarf4").m_171324_("scarf5").f_104203_ = swing;
            base.m_171324_("scarf4").m_171324_("scarf5").m_171324_("scarf6").f_104203_ = swing;
            base.m_171324_("scarf1").m_171324_("scarf2").f_104203_ = -swing;
            base.m_171324_("scarf1").m_171324_("scarf2").m_171324_("scarf3").f_104203_ = -swing;
            base.f_104203_ = crouching ? 0.5F : 0.10995574F;
         case 2:
         case 3:
         case 4:
         case 5:
         case 7:
         case 8:
         default:
            break;
         case 6:
            if (this.body.m_233562_("tailjb")) {
               ModelPart tail = this.body.m_171324_("tailjb");
               tail.m_233569_();
               tail.f_104203_ = crouching ? 0.5F : 0.0F;
               tail.f_233553_ = 1.2F;
               tail.f_233554_ = 1.2F;
               tail.f_233555_ = 1.2F;
               tail.f_104201_ += -3.2F;
               float yaw = Mth.m_14089_(ageInTicks * 0.14F) * 0.1F / 0.6F;
               float pitch = (Mth.m_14089_(ageInTicks / 8.0F) / 3.0F - 0.2F) / 0.6F;
               ModelPart first = tail.m_171324_("tailj1");
               ModelPart second = first.m_171324_("tailj2");
               ModelPart third = second.m_171324_("tailj3");
               first.m_233569_();
               second.m_233569_();
               third.m_233569_();
               first.f_104203_ = 0.2F + pitch;
               first.f_104204_ = 0.2F + yaw;
               second.f_104204_ = yaw;
               third.f_104204_ = 0.2F + yaw;
            }
            break;
         case 9:
            if (this.body.m_233562_("hoja")) {
               ModelPart blade = this.body.m_171324_("hoja");
               blade.m_233569_();
               if (crouching) {
                  blade.f_104203_ = 0.6F;
               }
            }
            break;
         case 10:
            if (this.body.m_233562_("handle")) {
               ModelPart handle = this.body.m_171324_("handle");
               handle.m_233569_();
               if (crouching) {
                  handle.f_104204_ = -0.5F;
                  handle.f_104202_ += 8.0F;
                  handle.f_104201_ += 9.6F;
               }
            }
      }
   }

   @Override
   public ModelPart part(String biped) {
      return switch (biped) {
         case "head" -> this.head;
         case "body" -> this.body;
         case "right_arm" -> this.rightArm;
         case "left_arm" -> this.leftArm;
         case "right_leg" -> this.rightLeg;
         case "left_leg" -> this.leftLeg;
         default -> null;
      };
   }

   @Override
   public void m_7695_(PoseStack pose, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {
      this.root.m_104306_(pose, buffer, light, overlay, r, g, b, a);
   }
}
