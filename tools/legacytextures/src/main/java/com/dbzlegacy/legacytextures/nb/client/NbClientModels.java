package com.dbzlegacy.legacytextures.nb.client;

import com.dbzlegacy.legacytextures.nb.catalog.CatalogEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.EntityModelSet;

public final class NbClientModels {
   public static final List<String> EXTRA_MODELS = List.of(
      "armor_0_13", "trench_0_13", "headwear", "bodysuit", "armor_0_046", "armor_0_256", "armor_0_056", "trenchcoat"
   );
   static NbArmorModel armorOuter;
   static NbArmorModel armorInner;
   static NbArmorModel armorOuterBase;
   static NbArmorModel torsoExtras;
   private static final Map<String, NbArmorModel> NAMED = new HashMap<>();
   private static final Map<Integer, NbVanityModel> VANITY = new HashMap<>();

   private NbClientModels() {
   }

   static void bake(EntityModelSet models, Iterable<Integer> vanityTypes) {
      armorOuter = new NbArmorModel(models.m_171103_(NbModelLayers.ARMOR_OUTER));
      armorInner = new NbArmorModel(models.m_171103_(NbModelLayers.ARMOR_INNER));
      armorOuterBase = new NbArmorModel(models.m_171103_(NbModelLayers.ARMOR_OUTER_BASE));
      torsoExtras = new NbArmorModel(models.m_171103_(NbModelLayers.ARMOR_TORSO_EXTRAS));
      NAMED.clear();

      for (String name : EXTRA_MODELS) {
         NbArmorModel model = new NbArmorModel(models.m_171103_(NbModelLayers.named(name)));

         model.swayProfile = switch (name) {
            case "trench_0_13" -> ArmorSway.TRENCH;
            case "trenchcoat" -> ArmorSway.COAT;
            default -> ArmorSway.ARMOR;
         };
         NAMED.put(name, model);
      }

      VANITY.clear();

      for (int type : vanityTypes) {
         VANITY.put(type, new NbVanityModel(models.m_171103_(NbModelLayers.vanity(type))));
      }
   }

   public static NbArmorModel armor(boolean inner) {
      return inner ? armorInner : armorOuter;
   }

   public static NbArmorModel armorBase() {
      return armorOuterBase;
   }

   public static NbArmorModel torsoExtras() {
      return torsoExtras;
   }

   public static NbArmorModel named(String name) {
      return NAMED.get(name);
   }

   public static NbVanityModel vanity(int type) {
      return VANITY.get(type);
   }

   public static NbPartModel partModel(CatalogEntry entry) {
      if (entry.model == null) {
         return null;
      } else {
         return (NbPartModel)(entry.isAnchoredModel() && entry.vanityType != null ? vanity(entry.vanityType) : named(entry.model));
      }
   }
}
