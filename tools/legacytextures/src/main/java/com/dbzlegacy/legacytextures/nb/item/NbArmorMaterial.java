package com.dbzlegacy.legacytextures.nb.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.crafting.Ingredient;

public enum NbArmorMaterial implements ArmorMaterial {
   NB;

   @Override
   public int m_266425_(Type type) {
      return 0;
   }

   @Override
   public int m_7366_(Type type) {
      return switch (type) {
         case HELMET -> 3;
         case CHESTPLATE -> 8;
         case LEGGINGS -> 6;
         case BOOTS -> 3;
      };
   }

   @Override
   public int m_6646_() {
      return 30;
   }

   @Override
   public SoundEvent m_7344_() {
      return SoundEvents.f_11673_;
   }

   @Override
   public Ingredient m_6230_() {
      return Ingredient.f_43901_;
   }

   @Override
   public String m_6082_() {
      return "nbarmors:nb";
   }

   @Override
   public float m_6651_() {
      return 0.0F;
   }

   @Override
   public float m_6649_() {
      return 0.0F;
   }
}
