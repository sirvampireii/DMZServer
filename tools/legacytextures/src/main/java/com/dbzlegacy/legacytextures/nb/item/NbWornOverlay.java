package com.dbzlegacy.legacytextures.nb.item;

import com.dbzlegacy.legacytextures.nb.catalog.CatalogEntry;
import net.minecraft.world.item.ItemStack;

public interface NbWornOverlay {
   CatalogEntry entry();

   boolean isOverlay();

   int wornColor(ItemStack var1);
}
