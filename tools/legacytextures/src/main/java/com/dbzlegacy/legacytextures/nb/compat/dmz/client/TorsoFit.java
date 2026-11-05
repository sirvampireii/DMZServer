package com.dbzlegacy.legacytextures.nb.compat.dmz.client;

public final class TorsoFit {
   public static final float[] NB_TORSO = new float[]{8.22F, 12.22F, 4.22F};
   public static final float DMZ_TORSO_INFLATION = 1.045F;

   private TorsoFit() {
   }

   public static float[] torsoOf(float inflation) {
      return new float[]{8.0F + 2.0F * inflation, 12.0F + 2.0F * inflation, 4.0F + 2.0F * inflation};
   }

   public static float[] scale(float[] envelope) {
      return scale(envelope, NB_TORSO);
   }

   public static float[] scale(float[] envelope, float[] torso) {
      float[] scale = new float[3];

      for (int i = 0; i < 3; i++) {
         scale[i] = envelope[i] * 1.045F / torso[i];
      }

      return scale;
   }

   public static float[] referenceExtents(float[] bone, float[] envelope) {
      return referenceExtents(bone, envelope, NB_TORSO);
   }

   public static float[] withGap(float[] own, float[] bone, float gap) {
      float[] reference = new float[3];

      for (int i = 0; i < 3; i++) {
         reference[i] = own[i] * bone[i] / (bone[i] + 2.0F * gap);
      }

      return reference;
   }

   public static float[] referenceExtents(float[] bone, float[] envelope, float[] torso) {
      float[] merged = new float[3];

      for (int i = 0; i < 3; i++) {
         merged[i] = Math.max(envelope[i], bone[i]);
      }

      float[] scale = scale(merged, torso);
      float[] reference = new float[3];

      for (int i = 0; i < 3; i++) {
         reference[i] = bone[i] / scale[i];
      }

      return reference;
   }
}
