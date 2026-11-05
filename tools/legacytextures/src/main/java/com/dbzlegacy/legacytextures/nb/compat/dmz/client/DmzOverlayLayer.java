package com.dbzlegacy.legacytextures.nb.compat.dmz.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.dbzlegacy.legacytextures.nb.NBArmors;
import com.dbzlegacy.legacytextures.nb.catalog.CatalogEntry;
import com.dbzlegacy.legacytextures.nb.client.NbArmorModel;
import com.dbzlegacy.legacytextures.nb.client.NbClientModels;
import com.dbzlegacy.legacytextures.nb.client.NbPartModel;
import com.dbzlegacy.legacytextures.nb.client.NbVanityModel;
import com.dbzlegacy.legacytextures.nb.item.NbScouterItem;
import com.dbzlegacy.legacytextures.nb.item.NbVanityItem;
import com.dbzlegacy.legacytextures.nb.item.NbWornOverlay;
import com.dbzlegacy.legacytextures.nb.model.LegacyModelData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelPart.Cube;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

public class DmzOverlayLayer<T extends AbstractClientPlayer & GeoAnimatable> extends ItemArmorGeoLayer<T> {
   private static final float GAP = 0.1F;
   private static final String HEAD_TECH = "head_tech";
   private static final float[] HEAD = new float[]{8.0F, 8.0F, 8.0F};
   private static final float[] LIMB = new float[]{4.0F, 12.0F, 4.0F};
   private static final Map<String, float[]> TORSO_OF_MODEL = new ConcurrentHashMap<>();
   private CatalogEntry current;

   public DmzOverlayLayer(GeoRenderer<T> renderer) {
      super(renderer);
   }

   private static String bipedOf(GeoBone bone) {
      String var1 = bone.getName();

      return switch (var1) {
         case "armorHead" -> "head";
         case "armorBody" -> "body";
         case "armorRightArm" -> "right_arm";
         case "armorLeftArm" -> "left_arm";
         case "armorRightLeg" -> "right_leg";
         case "armorLeftLeg" -> "left_leg";
         default -> null;
      };
   }

   private static List<ItemStack> wornOn(AbstractClientPlayer player, String biped) {
      List<ItemStack> worn = new ArrayList<>();
      if (!player.m_5833_() && !player.m_20145_()) {
         CuriosApi.getCuriosInventory(player).resolve().ifPresent(handler -> {
            ICurioStacksHandler vanity = handler.getCurios().get("vanity");
            if (vanity != null) {
               for (int i = 0; i < vanity.getSlots(); i++) {
                  ItemStack stack = vanity.getStacks().getStackInSlot(i);
                  if (isShown(vanity, i)) {
                     Item patt4684$temp = stack.m_41720_();
                     if (patt4684$temp instanceof NbVanityItem) {
                        NbVanityItem item = (NbVanityItem)patt4684$temp;
                        if (item.isOverlay() && item.entry().drawsOn(biped)) {
                           worn.add(stack);
                        }
                     }
                  }
               }
            }

            ICurioStacksHandler headTech = handler.getCurios().get("head_tech");
            if (headTech != null) {
               for (int ix = 0; ix < headTech.getSlots(); ix++) {
                  ItemStack stack = shownHeadTech(headTech, ix);
                  Item patt5147$temp = stack.m_41720_();
                  if (patt5147$temp instanceof NbScouterItem) {
                     NbScouterItem item = (NbScouterItem)patt5147$temp;
                     if (item.isOverlay() && item.entry().drawsOn(biped)) {
                        worn.add(stack);
                     }
                  }
               }
            }
         });
         return worn;
      } else {
         return worn;
      }
   }

   private static boolean isShown(ICurioStacksHandler slot, int index) {
      return index >= slot.getRenders().size() || slot.getRenders().get(index);
   }

   private static ItemStack shownHeadTech(ICurioStacksHandler slot, int index) {
      if (!isShown(slot, index)) {
         return ItemStack.f_41583_;
      } else {
         ItemStack cosmetic = slot.getCosmeticStacks().getStackInSlot(index);
         return cosmetic.m_41619_() ? slot.getStacks().getStackInSlot(index) : cosmetic;
      }
   }

   protected ItemStack getArmorItemForBone(GeoBone bone, T player) {
      String biped = bipedOf(bone);
      if (biped == null) {
         return null;
      } else {
         List<ItemStack> worn = wornOn(player, biped);
         return worn.isEmpty() ? null : worn.get(0);
      }
   }

   protected EquipmentSlot getEquipmentSlotForBone(GeoBone bone, ItemStack stack, T player) {
      return "armorHead".equals(bone.getName()) ? EquipmentSlot.HEAD : EquipmentSlot.CHEST;
   }

   protected HumanoidModel<?> getModelForItem(GeoBone bone, EquipmentSlot slot, ItemStack stack, T player) {
      return stack.m_41720_() instanceof NbWornOverlay item ? NbClientModels.named(item.entry().model) : null;
   }

   private static boolean hasContent(ModelPart part) {
      return !part.m_171326_() || part.m_171331_().count() > 1L;
   }

   private static float[] torsoOf(CatalogEntry entry) {
      return TORSO_OF_MODEL.computeIfAbsent(entry.model, name -> {
         LegacyModelData data = entry.isAnchoredModel() ? null : LegacyModelData.load(name);
         return TorsoFit.torsoOf(data == null ? 0.0F : data.bodyInflation());
      });
   }

   @Override
   protected Cube getReferenceCubeForModel(GeoBone bone, ModelPart part) {
      float[] boneSize = GeoBounds.firstCube(bone);
      if ("armorBody".equals(bone.getName())) {
         return DmzTorsoExtrasLayer.syntheticCube(
            TorsoFit.referenceExtents(boneSize, GeoBounds.bodyEnvelope(bone), this.current == null ? TorsoFit.torsoOf(0.0F) : torsoOf(this.current))
         );
      } else {
         float[] own;
         if (part.m_171326_()) {
            own = "armorHead".equals(bone.getName()) ? HEAD : LIMB;
         } else {
            Cube cube = super.getReferenceCubeForModel(bone, part);
            own = new float[]{cube.f_104338_ - cube.f_104335_, cube.f_104339_ - cube.f_104336_, cube.f_104340_ - cube.f_104337_};
         }

         return DmzTorsoExtrasLayer.syntheticCube(TorsoFit.withGap(own, boneSize, 0.1F));
      }
   }

   public void renderForBone(
      PoseStack poseStack,
      T player,
      GeoBone bone,
      RenderType renderType,
      MultiBufferSource bufferSource,
      VertexConsumer buffer,
      float partialTick,
      int packedLight,
      int packedOverlay
   ) {
      String biped = bipedOf(bone);
      if (biped != null) {
         for (ItemStack stack : wornOn(player, biped)) {
            NbWornOverlay item = (NbWornOverlay)stack.m_41720_();
            NbPartModel model = NbClientModels.partModel(item.entry());
            ModelPart part = model == null ? null : model.part(biped);
            if (part != null && hasContent(part)) {
               if (model instanceof NbVanityModel vanity) {
                  float yaw = Mth.m_14177_(
                     Mth.m_14189_(partialTick, player.f_20886_, player.f_20885_) - Mth.m_14189_(partialTick, player.f_20884_, player.f_20883_)
                  );
                  vanity.look(yaw, Mth.m_14179_(partialTick, player.f_19860_, player.m_146909_()));
                  vanity.animate(
                     item.entry().vanityType == null ? -1 : item.entry().vanityType,
                     player.f_267362_.m_267590_(partialTick),
                     Math.min(1.0F, player.f_267362_.m_267711_(partialTick)),
                     player.m_6047_(),
                     (float)player.f_19797_ + partialTick
                  );
               }

               if (model instanceof NbArmorModel armor) {
                  armor.sway(player, partialTick);
               }

               this.current = item.entry();
               poseStack.m_85836_();
               poseStack.m_85841_(-1.0F, -1.0F, 1.0F);
               this.prepModelPartForRender(poseStack, bone, part);
               int color = item.wornColor(stack);
               VertexConsumer armorBuffer = ItemRenderer.m_115184_(
                  bufferSource, RenderType.m_110431_(NBArmors.id("textures/armor/" + item.entry().wornTexture(1))), false, stack.m_41790_()
               );
               part.m_104306_(
                  poseStack,
                  armorBuffer,
                  packedLight,
                  OverlayTexture.f_118083_,
                  (float)(color >> 16 & 0xFF) / 255.0F,
                  (float)(color >> 8 & 0xFF) / 255.0F,
                  (float)(color & 0xFF) / 255.0F,
                  1.0F
               );
               poseStack.m_85849_();
               this.current = null;
            }
         }

         bufferSource.m_6299_(renderType);
      }
   }
}
