package com.dbzlegacy.legacytextures.nb.item;

import com.dbzlegacy.legacytextures.nb.catalog.CatalogEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;

public class NbDyeableVanityItem extends NbVanityItem implements DyeableLeatherItem {
   public NbDyeableVanityItem(CatalogEntry entry) {
      super(entry);
   }

   @Override
   public int wornColor(ItemStack stack) {
      CompoundTag display = stack.m_41737_("display");
      if (display != null && display.m_128425_("color", 99)) {
         return display.m_128451_("color");
      } else {
         return this.entry().defaultColor != null ? this.entry().defaultColor : 16777215;
      }
   }

   @Override
   public int m_41121_(ItemStack stack) {
      return this.wornColor(stack);
   }
}
