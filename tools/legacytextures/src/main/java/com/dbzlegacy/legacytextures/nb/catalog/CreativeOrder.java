package com.dbzlegacy.legacytextures.nb.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CreativeOrder {
   private static final List<String> SLOTS = List.of("head", "chest", "legs", "boots");

   private CreativeOrder() {
   }

   public static CreativeOrder.Category categoryOf(CatalogEntry e) {
      if (e.isArmor()) {
         return CreativeOrder.Category.ARMOR_SETS;
      } else if (e.isHeadTech()) {
         return CreativeOrder.Category.SCOUTERS;
      } else {
         String var1 = e.overlay == null ? "" : e.overlay;

         return switch (var1) {
            case "headwear" -> CreativeOrder.Category.HEADWEAR;
            case "bodysuit" -> CreativeOrder.Category.BODYSUITS;
            default -> CreativeOrder.Category.COSMETICS;
         };
      }
   }

   public static String setOf(CatalogEntry e) {
      String suffix = "_" + e.slot;
      return e.id.endsWith(suffix) ? e.id.substring(0, e.id.length() - suffix.length()) : e.id;
   }

   public static List<CatalogEntry> ordered() {
      return ordered(Catalog.all(), Catalog.tabs());
   }

   static List<CatalogEntry> ordered(List<CatalogEntry> entries, List<CatalogTab> tabs) {
      List<String> tabIds = tabs.stream().map(t -> t.id).toList();
      entries.forEach(e -> tabIndex(tabIds, e));
      List<CatalogEntry> sorted = new ArrayList<>(entries);
      sorted.sort(
         Comparator.<CatalogEntry>comparingInt(e -> tabIndex(tabIds, e))
            .thenComparing(e -> categoryOf(e).ordinal())
            .thenComparing(e -> e.isArmor() ? setOf(e) : (e.model == null ? "" : e.model), CreativeOrder::natural)
            .thenComparingInt(e -> e.isArmor() ? SLOTS.indexOf(e.slot) : 0)
            .thenComparing(e -> e.id, CreativeOrder::natural)
      );
      return sorted;
   }

   public static Map<String, List<CatalogEntry>> byTab() {
      Map<String, List<CatalogEntry>> result = new LinkedHashMap<>();

      for (CatalogTab tab : Catalog.tabs()) {
         result.put(tab.id, new ArrayList<>());
      }

      for (CatalogEntry e : ordered()) {
         List<CatalogEntry> tab = result.get(e.tab);
         if (tab == null) {
            throw new IllegalStateException(e.id + ": unknown creative tab " + e.tab);
         }

         tab.add(e);
      }

      return result;
   }

   private static int tabIndex(List<String> tabIds, CatalogEntry e) {
      int i = tabIds.indexOf(e.tab);
      if (i < 0) {
         throw new IllegalStateException(e.id + ": unknown creative tab " + e.tab);
      } else {
         return i;
      }
   }

   static int natural(String a, String b) {
      int i = 0;
      int j = 0;

      while (i < a.length() && j < b.length()) {
         char x = a.charAt(i);
         char y = b.charAt(j);
         if (Character.isDigit(x) && Character.isDigit(y)) {
            int si = i;
            int sj = j;

            while (i < a.length() && Character.isDigit(a.charAt(i))) {
               i++;
            }

            while (j < b.length() && Character.isDigit(b.charAt(j))) {
               j++;
            }

            String na = a.substring(si, i).replaceFirst("^0+(?=.)", "");
            String nb = b.substring(sj, j).replaceFirst("^0+(?=.)", "");
            int c = na.length() != nb.length() ? Integer.compare(na.length(), nb.length()) : na.compareTo(nb);
            if (c != 0) {
               return c;
            }
         } else {
            if (x != y) {
               return Character.compare(x, y);
            }

            i++;
            j++;
         }
      }

      return Integer.compare(a.length() - i, b.length() - j);
   }

   public static enum Category {
      ARMOR_SETS("Armor Sets"),
      HEADWEAR("Headwear"),
      COSMETICS("Cosmetics"),
      BODYSUITS("Bodysuits"),
      SCOUTERS("Scouters");

      public final String title;

      private Category(String title) {
         this.title = title;
      }
   }
}
