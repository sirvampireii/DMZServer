package com.dbzlegacy.legacytextures.nb.client;

import com.dbzlegacy.legacytextures.nb.model.LegacyModelData;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class LegacyModelBuilder {
   private static final String[] BIPED_PARTS = new String[]{"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};

   private LegacyModelBuilder() {
   }

   public static LayerDefinition build(LegacyModelData data, boolean anchors) {
      return build(data, anchors, LegacyModelBuilder.Variant.FULL);
   }

   public static LayerDefinition build(LegacyModelData data, boolean anchors, LegacyModelBuilder.Variant variant) {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.m_171576_();
      Map<String, PartDefinition> defs = new HashMap<>();
      Set<String> torsoExtras = data.torsoExtraNames();
      if (anchors) {
         for (String name : BIPED_PARTS) {
            defs.put(name, root.m_171599_(name, CubeListBuilder.m_171558_(), PartPose.f_171404_));
         }
      }

      for (LegacyModelData.Part part : data.parts) {
         boolean biped = isBiped(part.name);
         boolean extra = torsoExtras.contains(part.name);
         if ((variant != LegacyModelBuilder.Variant.BASE || !extra) && (variant != LegacyModelBuilder.Variant.TORSO_EXTRAS || biped || extra)) {
            PartDefinition parent = part.parent == null ? root : defs.get(part.parent);
            if (parent == null) {
               throw new IllegalStateException("legacy model part '" + part.name + "' has unknown parent '" + part.parent + "'");
            }

            CubeListBuilder cubes = CubeListBuilder.m_171558_().m_171514_((int)part.uv[0], (int)part.uv[1]).m_171555_(part.mirror);
            if (variant == LegacyModelBuilder.Variant.TORSO_EXTRAS && biped) {
               if (part.name.equals("body")) {
                  cubes.m_171481_(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
               }
            } else {
               for (float[] b : part.boxes) {
                  cubes.m_171488_(b[0], b[1], b[2], b[3], b[4], b[5], new CubeDeformation(b[6]));
               }
            }

            PartPose pose = PartPose.m_171423_(part.pivot[0], part.pivot[1], part.pivot[2], part.rot[0], part.rot[1], part.rot[2]);
            defs.put(part.name, parent.m_171599_(part.name, cubes, pose));
         }
      }

      if (!anchors && !defs.containsKey("hat")) {
         root.m_171599_("hat", CubeListBuilder.m_171558_(), PartPose.f_171404_);
      }

      return LayerDefinition.m_171565_(mesh, data.textureWidth, data.textureHeight);
   }

   private static boolean isBiped(String name) {
      for (String biped : BIPED_PARTS) {
         if (biped.equals(name)) {
            return true;
         }
      }

      return false;
   }

   public static enum Variant {
      FULL,
      BASE,
      TORSO_EXTRAS;
   }
}
