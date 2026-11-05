package com.dbzlegacy.legacytextures.nb.item;

import com.dbzlegacy.legacytextures.nb.NBArmors;
import com.dbzlegacy.legacytextures.nb.catalog.CatalogEntry;
import com.dbzlegacy.legacytextures.nb.client.NbClientHooks;
import java.util.function.Consumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.Item.Properties;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public class NbArmorItem extends ArmorItem {
   private final CatalogEntry entry;

   public NbArmorItem(CatalogEntry entry) {
      super(NbArmorMaterial.NB, typeOf(entry.slot), new Properties().m_41487_(1));
      this.entry = entry;
   }

   public CatalogEntry entry() {
      return this.entry;
   }

   public static Type typeOf(String slot) {
      return switch (slot) {
         case "head" -> Type.HELMET;
         case "chest" -> Type.CHESTPLATE;
         case "legs" -> Type.LEGGINGS;
         case "boots" -> Type.BOOTS;
         default -> throw new IllegalArgumentException("unknown armor slot: " + slot);
      };
   }

   public static int layerOf(EquipmentSlot slot) {
      return switch (slot) {
         case HEAD -> 0;
         case CHEST -> 1;
         case LEGS -> 2;
         default -> 3;
      };
   }

   public static String wornTexture(CatalogEntry entry, EquipmentSlot slot) {
      return NBArmors.id("textures/armor/" + entry.wornTexture(layerOf(slot))).toString();
   }

   @Override
   public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
      return wornTexture(this.entry, slot);
   }

   @Override
   public void initializeClient(Consumer<IClientItemExtensions> consumer) {
      consumer.accept(NbClientHooks.catalogArmorExtensions(() -> this.entry.model));
   }
}
