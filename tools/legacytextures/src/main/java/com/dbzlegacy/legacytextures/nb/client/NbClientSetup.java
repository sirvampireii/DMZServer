package com.dbzlegacy.legacytextures.nb.client;

import com.dbzlegacy.legacytextures.nb.catalog.Catalog;
import com.dbzlegacy.legacytextures.nb.catalog.CatalogEntry;
import com.dbzlegacy.legacytextures.nb.compat.dmz.client.DmzClientCompat;
import com.dbzlegacy.legacytextures.nb.item.NbVanityItem;
import com.dbzlegacy.legacytextures.nb.model.LegacyModelData;
import com.dbzlegacy.legacytextures.nb.registry.NbItems;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent.AddLayers;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryObject;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

@EventBusSubscriber(
   modid = "nbarmors",
   bus = Bus.MOD,
   value = {Dist.CLIENT}
)
public final class NbClientSetup {
   private NbClientSetup() {
   }

   private static Set<Integer> vanityTypes() {
      Set<Integer> types = new TreeSet<>();

      for (CatalogEntry e : Catalog.all()) {
         if (!e.isArmor() && e.isAnchoredModel() && e.vanityType != null) {
            types.add(e.vanityType);
         }
      }

      return types;
   }

   @SubscribeEvent
   public static void registerLayers(RegisterLayerDefinitions event) {
      event.registerLayerDefinition(NbModelLayers.ARMOR_OUTER, () -> LegacyModelBuilder.build(LegacyModelData.load("armor_0_11"), false));
      event.registerLayerDefinition(NbModelLayers.ARMOR_INNER, () -> LegacyModelBuilder.build(LegacyModelData.load("armor_0_1"), false));
      event.registerLayerDefinition(
         NbModelLayers.ARMOR_OUTER_BASE, () -> LegacyModelBuilder.build(LegacyModelData.load("armor_0_11"), false, LegacyModelBuilder.Variant.BASE)
      );
      event.registerLayerDefinition(
         NbModelLayers.ARMOR_TORSO_EXTRAS, () -> LegacyModelBuilder.build(LegacyModelData.load("armor_0_11"), false, LegacyModelBuilder.Variant.TORSO_EXTRAS)
      );

      for (String name : NbClientModels.EXTRA_MODELS) {
         event.registerLayerDefinition(NbModelLayers.named(name), () -> LegacyModelBuilder.build(LegacyModelData.load(name), false));
      }

      for (int type : vanityTypes()) {
         event.registerLayerDefinition(NbModelLayers.vanity(type), () -> LegacyModelBuilder.build(LegacyModelData.load("vanity_" + type), true));
      }
   }

   @SubscribeEvent
   public static void bakeModels(AddLayers event) {
      NbClientModels.bake(event.getEntityModels(), vanityTypes());
   }

   @SubscribeEvent
   public static void clientSetup(FMLClientSetupEvent event) {
      if (ModList.get().isLoaded("dragonminez")) {
         DmzClientCompat.register();
      }

      event.enqueueWork(() -> {
         for (RegistryObject<Item> holder : NbItems.all().values()) {
            Object patt3238$temp = holder.get();
            if (patt3238$temp instanceof NbVanityItem) {
               NbVanityItem item = (NbVanityItem)patt3238$temp;
               if (item.entry().overlay != null) {
                  if (item.entry().isAnchoredModel()) {
                     CuriosRendererRegistry.register(item, NbVanityRenderer::new);
                  } else {
                     CuriosRendererRegistry.register(item, NbOverlayRenderer::new);
                  }
               }
            }
         }
      });
   }

   @SubscribeEvent
   public static void itemColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) {
      Item[] dyeable = NbItems.all().values().stream().map(h -> h.get()).filter(i -> {
         if (i instanceof NbVanityItem v && v.entry().isDyeable()) {
            return true;
         }

         return false;
      }).toArray(Item[]::new);
      event.register((stack, tint) -> tint == 0 ? ((NbVanityItem)stack.m_41720_()).wornColor(stack) | 0xFF000000 : -1, dyeable);
   }
}
