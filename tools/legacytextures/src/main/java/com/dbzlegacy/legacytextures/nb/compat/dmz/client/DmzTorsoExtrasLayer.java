package com.dbzlegacy.legacytextures.nb.compat.dmz.client;

import com.dbzlegacy.legacytextures.nb.client.NbArmorModel;
import com.dbzlegacy.legacytextures.nb.client.NbClientModels;
import com.dbzlegacy.legacytextures.nb.compat.dmz.NbDmzArmorItem;
import java.util.EnumSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelPart.Cube;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer;

public class DmzTorsoExtrasLayer<T extends AbstractClientPlayer & GeoAnimatable> extends ItemArmorGeoLayer<T> {
   private static final String BONE = "armorBody";

   public DmzTorsoExtrasLayer(GeoRenderer<T> renderer) {
      super(renderer);
   }

   protected ItemStack getArmorItemForBone(GeoBone bone, T player) {
      if ("armorBody".equals(bone.getName()) && !player.m_5833_() && NbClientModels.torsoExtras() != null) {
         ItemStack chest = player.m_6844_(EquipmentSlot.CHEST);
         return chest.m_41720_() instanceof NbDmzArmorItem ? chest : null;
      } else {
         return null;
      }
   }

   protected HumanoidModel<?> getModelForItem(GeoBone bone, EquipmentSlot slot, ItemStack stack, T player) {
      NbArmorModel extras = NbClientModels.torsoExtras();
      extras.sway(player, Minecraft.m_91087_().m_91296_());
      return extras;
   }

   @Override
   protected Cube getReferenceCubeForModel(GeoBone bone, ModelPart part) {
      return syntheticCube(TorsoFit.referenceExtents(GeoBounds.firstCube(bone), GeoBounds.bodyEnvelope(bone)));
   }

   static Cube syntheticCube(float[] size) {
      return new Cube(
         0, 0, -size[0] / 2.0F, 0.0F, -size[2] / 2.0F, size[0], size[1], size[2], 0.0F, 0.0F, 0.0F, false, 128.0F, 64.0F, EnumSet.allOf(Direction.class)
      );
   }
}
