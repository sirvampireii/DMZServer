package com.dbzlegacy.legacytextures.nb.item;

import com.dbzlegacy.legacytextures.nb.catalog.CatalogEntry;

public final class VanityKinds {
   private VanityKinds() {
   }

   public static Class<? extends NbVanityItem> classFor(CatalogEntry entry) {
      return entry.isDyeable() ? NbDyeableVanityItem.class : NbVanityItem.class;
   }
}
