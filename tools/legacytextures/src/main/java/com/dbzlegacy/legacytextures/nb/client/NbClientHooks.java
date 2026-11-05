package com.dbzlegacy.legacytextures.nb.client;

import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public final class NbClientHooks {
   private NbClientHooks() {
   }

   public static IClientItemExtensions armorExtensions() {
      return armorExtensions(entity -> false);
   }

   public static IClientItemExtensions catalogArmorExtensions(final Supplier<String> modelName) {
      return new IClientItemExtensions() {
         @Override
         public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
            String name = modelName.get();
            NbArmorModel model = name != null ? NbClientModels.named(name) : NbClientModels.armor(slot == EquipmentSlot.LEGS);
            if (model != null) {
               model.sway(entity, Minecraft.m_91087_().m_91296_());
            }

            return (HumanoidModel<?>)(model != null ? model : original);
         }
      };
   }

   public static IClientItemExtensions armorExtensions(final Predicate<LivingEntity> torsoExtrasDrawnSeparately) {
      return new IClientItemExtensions() {
         @Override
         public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
            NbArmorModel model = slot != EquipmentSlot.LEGS && torsoExtrasDrawnSeparately.test(entity)
               ? NbClientModels.armorBase()
               : NbClientModels.armor(slot == EquipmentSlot.LEGS);
            if (model != null) {
               model.sway(entity, Minecraft.m_91087_().m_91296_());
            }

            return (HumanoidModel<?>)(model != null ? model : original);
         }
      };
   }
}
