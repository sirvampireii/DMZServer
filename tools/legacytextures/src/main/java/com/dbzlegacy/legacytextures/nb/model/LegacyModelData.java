package com.dbzlegacy.legacytextures.nb.model;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LegacyModelData {
   public String kind;
   public int textureWidth;
   public int textureHeight;
   public List<String> dropped;
   public List<String> unattached;
   public List<LegacyModelData.Part> parts;

   public Set<String> torsoExtraNames() {
      Set<String> names = new LinkedHashSet<>();

      for (LegacyModelData.Part p : this.parts) {
         if (p.parent != null && (p.parent.equals("body") || names.contains(p.parent))) {
            names.add(p.name);
         }
      }

      return names;
   }

   public float bodyInflation() {
      for (LegacyModelData.Part p : this.parts) {
         if ("body".equals(p.name) && p.boxes != null && p.boxes.length > 0) {
            return p.boxes[0][6];
         }
      }

      return 0.0F;
   }

   public static LegacyModelData load(String name) {
      try {
         LegacyModelData var2;
         try (InputStream in = LegacyModelData.class.getResourceAsStream("/legacy_models/" + name + ".json")) {
            if (in == null) {
               return null;
            }

            var2 = (LegacyModelData)new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), LegacyModelData.class);
         }

         return var2;
      } catch (IOException var6) {
         throw new IllegalStateException("cannot read legacy model " + name, var6);
      }
   }

   public static final class Part {
      public String name;
      public String parent;
      public float[] pivot;
      public float[] rot;
      public float[] uv;
      public boolean mirror;
      public float[][] boxes;
   }
}
