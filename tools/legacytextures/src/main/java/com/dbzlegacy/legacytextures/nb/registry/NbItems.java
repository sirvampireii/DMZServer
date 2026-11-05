package com.dbzlegacy.legacytextures.nb.registry;

import com.dbzlegacy.legacytextures.nb.catalog.Catalog;
import com.dbzlegacy.legacytextures.nb.catalog.CatalogEntry;
import com.dbzlegacy.legacytextures.nb.compat.dmz.DmzItems;
import com.dbzlegacy.legacytextures.nb.item.NbArmorItem;
import com.dbzlegacy.legacytextures.nb.item.NbScouterItem;
import com.dbzlegacy.legacytextures.nb.item.NbVanityItem;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class NbItems {
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "nbarmors");
   private static final Map<String, RegistryObject<Item>> BY_ID = new LinkedHashMap<>();

   private NbItems() {
   }

   private static Item create(CatalogEntry entry) {
      if (entry.isHeadTech()) {
         return new NbScouterItem(entry);
      } else if (!entry.isArmor()) {
         return NbVanityItem.of(entry);
      } else {
         return (Item)(entry.dmzTorso && dragonMineZLoaded() ? DmzItems.chestArmor(entry) : new NbArmorItem(entry));
      }
   }

   public static boolean dragonMineZLoaded() {
      return ModList.get().isLoaded("dragonminez");
   }

   public static Map<String, RegistryObject<Item>> all() {
      return Collections.unmodifiableMap(BY_ID);
   }

   static {
      for (CatalogEntry entry : Catalog.all()) {
         BY_ID.put(entry.id, ITEMS.register(entry.id, () -> create(entry)));
      }
   }
}
