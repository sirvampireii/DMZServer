package com.dbzlegacy.legacytextures.nb.compat.dmz.client;

import net.minecraftforge.common.MinecraftForge;
import software.bernie.geckolib.event.GeoRenderEvent.Entity.CompileRenderLayers;

public final class DmzClientCompat {
   private static final String PLAYER_RENDERER = "com.dragonminez.client.render.DMZPlayerRenderer";

   private DmzClientCompat() {
   }

   public static void register() {
      MinecraftForge.EVENT_BUS.addListener(DmzClientCompat::onCompileLayers);
   }

   public static void onCompileLayers(CompileRenderLayers event) {
      if (isPlayerRenderer(event.getRenderer().getClass())) {
         event.addLayer(new DmzTorsoExtrasLayer(event.getRenderer()));
         event.addLayer(new DmzOverlayLayer(event.getRenderer()));
      }
   }

   private static boolean isPlayerRenderer(Class<?> type) {
      for (Class<?> c = type; c != null; c = c.getSuperclass()) {
         if ("com.dragonminez.client.render.DMZPlayerRenderer".equals(c.getName())) {
            return true;
         }
      }

      return false;
   }
}
