package com.dbzlegacy.legacytextures.nb.item;

import com.dbzlegacy.legacytextures.nb.catalog.CatalogEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public class NbScouterItem extends Item implements ICurioItem, NbWornOverlay {
   private final CatalogEntry entry;

   public NbScouterItem(CatalogEntry entry) {
      super(new Properties().m_41487_(1).m_41486_().m_41503_(entry.durability));
      this.entry = entry;
   }

   @Override
   public boolean isOverlay() {
      return this.entry.overlay != null;
   }

   @Override
   public CatalogEntry entry() {
      return this.entry;
   }

   @Override
   public int wornColor(ItemStack stack) {
      return 16777215;
   }
}
