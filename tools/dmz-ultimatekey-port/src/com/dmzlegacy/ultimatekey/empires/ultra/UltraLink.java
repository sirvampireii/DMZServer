package com.dmzlegacy.ultimatekey.empires.ultra;

import net.minecraftforge.fml.ModList;

/** Whether DMZ Ultra is loaded. Every Ultra read in this package checks it first. */
final class UltraLink {
    private static Boolean present;

    private UltraLink() {
    }

    static boolean present() {
        if (present == null) {
            ModList mods = ModList.get();
            present = mods != null && mods.isLoaded("dmzultra");
        }
        return present;
    }
}
