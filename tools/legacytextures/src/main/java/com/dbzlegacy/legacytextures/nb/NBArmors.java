package com.dbzlegacy.legacytextures.nb;

import com.dbzlegacy.legacytextures.nb.registry.NbCreativeTabs;
import com.dbzlegacy.legacytextures.nb.registry.NbItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("nbarmors")
public final class NBArmors {
   public static final String ID = "nbarmors";

   public NBArmors() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      NbItems.ITEMS.register(bus);
      NbCreativeTabs.TABS.register(bus);
   }

   public static ResourceLocation id(String path) {
      return new ResourceLocation("nbarmors", path);
   }
}
