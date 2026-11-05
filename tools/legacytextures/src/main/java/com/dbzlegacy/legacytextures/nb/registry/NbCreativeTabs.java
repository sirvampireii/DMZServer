package com.dbzlegacy.legacytextures.nb.registry;

import com.dbzlegacy.legacytextures.nb.catalog.Catalog;
import com.dbzlegacy.legacytextures.nb.catalog.CatalogEntry;
import com.dbzlegacy.legacytextures.nb.catalog.CatalogTab;
import com.dbzlegacy.legacytextures.nb.catalog.CreativeOrder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class NbCreativeTabs {
   public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.f_279569_, "nbarmors");
   public static final Map<String, RegistryObject<CreativeModeTab>> BY_ID = new LinkedHashMap<>();

   private NbCreativeTabs() {
   }

   static {
      for (CatalogTab tab : Catalog.tabs()) {
         BY_ID.put(
            tab.id,
            TABS.register(
               tab.id,
               () -> {
                  List<CatalogEntry> entries = CreativeOrder.byTab().get(tab.id);
                  return CreativeModeTab.builder()
                     .m_257941_(Component.m_237115_("itemGroup.nbarmors." + tab.id))
                     .m_257737_(() -> new ItemStack(NbItems.all().get(entries.get(0).id).get()))
                     .m_257501_((params, output) -> entries.forEach(entry -> output.m_246326_(NbItems.all().get(entry.id).get())))
                     .m_257652_();
               }
            )
         );
      }
   }
}
