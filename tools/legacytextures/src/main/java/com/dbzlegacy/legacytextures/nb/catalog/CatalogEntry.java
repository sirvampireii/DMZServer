package com.dbzlegacy.legacytextures.nb.catalog;

import java.util.List;

public final class CatalogEntry {
   public String id;
   public String name;
   public String kind;
   public String slot;
   public Integer vanityType;
   public String texturePrefix;
   public List<String> textureLayouts;
   public Integer defaultColor;
   public String icon;
   public String group;
   public String tab;
   public String originalClass;
   public boolean dmzTorso;
   public String model;
   public String overlay;
   public List<String> parts;
   public String texture;
   public String curioSlot;
   public Integer durability;

   public boolean isArmor() {
      return "armor".equals(this.kind);
   }

   public boolean isHeadTech() {
      return "head_tech".equals(this.curioSlot);
   }

   public boolean isDyeable() {
      return this.defaultColor != null;
   }

   public boolean isAnchoredModel() {
      return this.model != null && this.model.startsWith("vanity_");
   }

   public boolean drawsOn(String biped) {
      if (this.parts == null) {
         return false;
      } else {
         return switch (biped) {
            case "head", "body" -> this.parts.contains(biped);
            case "right_arm", "left_arm" -> this.parts.contains("arms");
            case "right_leg", "left_leg" -> this.parts.contains("legs");
            default -> false;
         };
      }
   }

   public String wornTexture(int layer) {
      if (this.texture != null) {
         return this.texture;
      } else {
         String jbra = layer + "jbra";
         boolean hasJbra = this.textureLayouts != null && this.textureLayouts.contains(jbra);
         return this.texturePrefix + "_" + (hasJbra ? jbra : String.valueOf(layer)) + ".png";
      }
   }

   public boolean usesLegacyLayout(int layer) {
      return this.texture != null ? false : this.textureLayouts == null || !this.textureLayouts.contains(layer + "jbra");
   }
}
