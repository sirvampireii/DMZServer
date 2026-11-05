package com.dbzlegacy.legacytextures.nb.compat.dmz.client;

import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

final class GeoBounds {
   private GeoBounds() {
   }

   static float[] firstCube(GeoBone bone) {
      GeoCube cube = bone.getCubes().get(0);
      return new float[]{(float)cube.size().f_82479_, (float)cube.size().f_82480_, (float)cube.size().f_82481_};
   }

   static float[] bodyEnvelope(GeoBone armorBone) {
      float[] min = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
      float[] max = new float[]{-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
      include(armorBone, min, max);
      GeoBone parent = armorBone.getParent();
      if (parent != null && "body".equals(parent.getName())) {
         include(parent, min, max);
      }

      float[] size = new float[3];

      for (int i = 0; i < 3; i++) {
         size[i] = Math.max(0.0F, max[i] - min[i]);
      }

      return size;
   }

   private static void include(GeoBone bone, float[] min, float[] max) {
      for (GeoCube cube : bone.getCubes()) {
         for (GeoQuad quad : cube.quads()) {
            if (quad != null) {
               for (GeoVertex vertex : quad.vertices()) {
                  float[] p = new float[]{vertex.position().x * 16.0F, vertex.position().y * 16.0F, vertex.position().z * 16.0F};

                  for (int i = 0; i < 3; i++) {
                     min[i] = Math.min(min[i], p[i]);
                     max[i] = Math.max(max[i], p[i]);
                  }
               }
            }
         }
      }
   }
}
