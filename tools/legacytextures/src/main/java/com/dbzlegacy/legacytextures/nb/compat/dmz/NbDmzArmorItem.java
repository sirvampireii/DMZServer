package com.dbzlegacy.legacytextures.nb.compat.dmz;

import com.dragonminez.common.init.armor.DbzArmorItem;
import com.dbzlegacy.legacytextures.nb.catalog.CatalogEntry;
import com.dbzlegacy.legacytextures.nb.client.NbClientHooks;
import com.dbzlegacy.legacytextures.nb.item.NbArmorItem;
import com.dbzlegacy.legacytextures.nb.item.NbArmorMaterial;
import java.util.function.Consumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public final class NbDmzArmorItem extends DbzArmorItem {
   private final CatalogEntry entry;

   public NbDmzArmorItem(CatalogEntry entry) {
      super(NbArmorMaterial.NB, NbArmorItem.typeOf(entry.slot), new Properties().m_41487_(1), "nbarmors", entry.texturePrefix);
      this.entry = entry;
   }

   public CatalogEntry entry() {
      return this.entry;
   }

   @Override
   public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
      return NbArmorItem.wornTexture(this.entry, slot);
   }

   @Override
   public void initializeClient(Consumer<IClientItemExtensions> consumer) {
      consumer.accept(NbClientHooks.armorExtensions(entity -> entity instanceof AbstractClientPlayer));
   }
}
