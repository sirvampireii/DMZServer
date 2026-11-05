package com.dbzlegacy.legacytextures.nb.item;

import com.dbzlegacy.legacytextures.nb.catalog.CatalogEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public class NbVanityItem extends Item implements ICurioItem, NbWornOverlay {
   private final CatalogEntry entry;

   public NbVanityItem(CatalogEntry entry) {
      super(new Properties().m_41487_(1));
      this.entry = entry;
   }

   public static NbVanityItem of(CatalogEntry entry) {
      return (NbVanityItem)(VanityKinds.classFor(entry) == NbDyeableVanityItem.class ? new NbDyeableVanityItem(entry) : new NbVanityItem(entry));
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
      return this.entry.defaultColor != null ? this.entry.defaultColor : 16777215;
   }
}
