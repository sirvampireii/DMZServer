package com.dbzlegacy.legacytextures.nb.catalog;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class Catalog {
   private static final String RESOURCE = "/nbarmors_catalog.json";
   private static List<CatalogEntry> entries;
   private static List<CatalogTab> tabs;

   private Catalog() {
   }

   public static synchronized List<CatalogTab> tabs() {
      if (tabs == null) {
         try (InputStream in = Catalog.class.getResourceAsStream("/nbarmors_tabs.json")) {
            if (in == null) {
               throw new IllegalStateException("missing /nbarmors_tabs.json");
            }

            tabs = List.of((CatalogTab[])new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), CatalogTab[].class));
         } catch (IOException var5) {
            throw new IllegalStateException("cannot read /nbarmors_tabs.json", var5);
         }
      }

      return tabs;
   }

   public static synchronized List<CatalogEntry> all() {
      if (entries == null) {
         try (InputStream in = Catalog.class.getResourceAsStream("/nbarmors_catalog.json")) {
            if (in == null) {
               throw new IllegalStateException("missing /nbarmors_catalog.json");
            }

            entries = List.of((CatalogEntry[])new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), CatalogEntry[].class));
         } catch (IOException var5) {
            throw new IllegalStateException("cannot read /nbarmors_catalog.json", var5);
         }
      }

      return entries;
   }
}
