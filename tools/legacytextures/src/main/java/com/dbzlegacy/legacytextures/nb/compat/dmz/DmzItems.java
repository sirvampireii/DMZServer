package com.dbzlegacy.legacytextures.nb.compat.dmz;

import com.dbzlegacy.legacytextures.nb.catalog.CatalogEntry;
import net.minecraft.world.item.Item;

public final class DmzItems {
   public static final String MOD_ID = "dragonminez";

   private DmzItems() {
   }

   public static Item chestArmor(CatalogEntry entry) {
      return new NbDmzArmorItem(entry);
   }
}
