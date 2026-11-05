package com.dbzlegacy.legacytextures.nb.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;

public final class ArmorSway {
   private static final float C = 0.1070796F;
   public static final ArmorSway.Profile ARMOR = new ArmorSway.Profile(
      List.of(
         new ArmorSway.Rule("cape1", ArmorSway.Axis.X, ArmorSway.Shape.UP, 1.0F, 1.2F),
         new ArmorSway.Rule("wing1", ArmorSway.Axis.X, ArmorSway.Shape.UP, 1.0F, 1.0F),
         new ArmorSway.Rule("wing1", ArmorSway.Axis.Z, ArmorSway.Shape.DOWN, 1.0F, 1.0F),
         new ArmorSway.Rule("wing2", ArmorSway.Axis.X, ArmorSway.Shape.UP, 1.0F, 1.0F),
         new ArmorSway.Rule("wing2", ArmorSway.Axis.Z, ArmorSway.Shape.UP, 1.0F, 1.0F),
         new ArmorSway.Rule("cube4", ArmorSway.Axis.X, ArmorSway.Shape.DOWN, 1.0F, 1.0F),
         new ArmorSway.Rule("skirt1", ArmorSway.Axis.X, ArmorSway.Shape.DOWN, 1.0F, 1.0F),
         new ArmorSway.Rule("skirt2", ArmorSway.Axis.X, ArmorSway.Shape.UP, 1.0F, 2.3F),
         new ArmorSway.Rule("skirt3", ArmorSway.Axis.X, ArmorSway.Shape.DOWN, 0.3846154F, 0.41666666F),
         new ArmorSway.Rule("skirt4", ArmorSway.Axis.X, ArmorSway.Shape.UP, 0.4F, 0.2F)
      )
   );
   public static final ArmorSway.Profile TRENCH = new ArmorSway.Profile(trench());
   public static final ArmorSway.Profile COAT = new ArmorSway.Profile(
      List.of(
         new ArmorSway.Rule("skirt1", ArmorSway.Axis.X, ArmorSway.Shape.DOWN, 0.4F, 0.4F),
         new ArmorSway.Rule("skirt2", ArmorSway.Axis.X, ArmorSway.Shape.UP, 0.5F, 0.5F),
         new ArmorSway.Rule("skirt4", ArmorSway.Axis.X, ArmorSway.Shape.UP, 0.6666667F, 0.6666667F)
      )
   );

   private ArmorSway() {
   }

   private static List<ArmorSway.Rule> trench() {
      float[] oddB = new float[]{1.24F, 1.74F, 2.8F, 6.0F, 1.74F, 2.8F, 6.0F};
      float[] evenA = new float[]{0.76F, 0.51F, 0.28F, 0.12F, 0.51F, 0.28F, 0.12F};
      List<ArmorSway.Rule> rules = new ArrayList<>();

      for (int i = 0; i < 7; i++) {
         rules.add(new ArmorSway.Rule("skirt" + (2 * i + 1), ArmorSway.Axis.X, ArmorSway.Shape.DOWN, 1.6666666F, 1.0F / oddB[i]));
         rules.add(new ArmorSway.Rule("skirt" + (2 * i + 2), ArmorSway.Axis.X, ArmorSway.Shape.UP, evenA[i], 1.0F));
      }

      return List.copyOf(rules);
   }

   public static void apply(ModelPart body, ArmorSway.Profile profile, float amount, float s) {
      float cosA = Mth.m_14089_(s * 0.6662F);
      float cosB = Mth.m_14089_(s * 0.6662F + (float) Math.PI);

      for (ArmorSway.Rule rule : profile.rules) {
         if (body.m_233562_(rule.part)) {
            ModelPart part = body.m_171324_(rule.part);
            float a = cosA * rule.kA * amount;
            float b = cosB * rule.kB * amount;
            float delta = rule.shape == ArmorSway.Shape.UP ? (b > 0.0F ? b : (a > 0.0F ? a : 0.0F)) : (b < 0.2141592F ? b : (a < 0.2141592F ? a : 0.2141592F));
            PartPose rest = part.m_233566_();
            if (rule.axis == ArmorSway.Axis.X) {
               part.f_104203_ = rest.f_171408_ + delta;
            } else {
               part.f_104205_ = rest.f_171410_ + delta;
            }
         }
      }
   }

   static enum Axis {
      X,
      Z;
   }

   public static final class Profile {
      private final List<ArmorSway.Rule> rules;

      private Profile(List<ArmorSway.Rule> rules) {
         this.rules = rules;
      }
   }

   static record Rule(String part, ArmorSway.Axis axis, ArmorSway.Shape shape, float kA, float kB) {
   }

   static enum Shape {
      UP,
      DOWN;
   }
}
