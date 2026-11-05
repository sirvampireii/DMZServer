package com.dbzlegacy.legacytextures.nb.client;

import com.dbzlegacy.legacytextures.nb.NBArmors;
import net.minecraft.client.model.geom.ModelLayerLocation;

public final class NbModelLayers {
   public static final ModelLayerLocation ARMOR_OUTER = new ModelLayerLocation(NBArmors.id("armor_outer"), "main");
   public static final ModelLayerLocation ARMOR_INNER = new ModelLayerLocation(NBArmors.id("armor_inner"), "main");
   public static final ModelLayerLocation ARMOR_OUTER_BASE = new ModelLayerLocation(NBArmors.id("armor_outer_base"), "main");
   public static final ModelLayerLocation ARMOR_TORSO_EXTRAS = new ModelLayerLocation(NBArmors.id("armor_torso_extras"), "main");

   private NbModelLayers() {
   }

   public static ModelLayerLocation vanity(int type) {
      return new ModelLayerLocation(NBArmors.id("vanity_" + type), "main");
   }

   public static ModelLayerLocation named(String name) {
      return new ModelLayerLocation(NBArmors.id("model_" + name), "main");
   }
}
