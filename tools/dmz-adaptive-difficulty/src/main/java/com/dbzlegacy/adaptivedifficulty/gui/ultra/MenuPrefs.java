package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * LM 4.7.62: a player's menu look (skin, themed backgrounds, Full/Lite), per account. Stored in the player's LM
 * record ({@link LmPlayerStore}, {@code config/legacymechanics/players/<uuid>.json}, key {@value #KEY}), which is
 * one file per account: every character slot shares it, and a slot switch, race change or DMZ reset never
 * touches it. Default for everyone, including players who never pick: Saiyan Royal, backgrounds on, Full.
 */
public final class MenuPrefs {
    private MenuPrefs() {}

    public static final String KEY = "lm_menu_prefs";

    /** One player's choice. */
    public record Prefs(String skin, boolean backgrounds, boolean lite) {
        public MenuSkins.Skin skinOrDefault() {
            return MenuSkins.byId(skin);
        }
    }

    public static final Prefs DEFAULT = new Prefs(MenuSkins.DEFAULT.id(), true, false);

    public static Prefs get(ServerPlayer player) {
        return player == null ? DEFAULT : get(player.m_20148_());
    }

    public static Prefs get(UUID id) {
        if (id == null) {
            return DEFAULT;
        }
        CompoundTag root = LmPlayerStore.root(id);
        if (!root.m_128441_(KEY)) {
            return DEFAULT;
        }
        CompoundTag t = root.m_128469_(KEY);
        String skin = t.m_128441_("skin") ? t.m_128461_("skin") : DEFAULT.skin();
        boolean bg = !t.m_128441_("backgrounds") || t.m_128471_("backgrounds");
        boolean lite = t.m_128441_("lite") && t.m_128471_("lite");
        return new Prefs(MenuSkins.known(skin) ? MenuSkins.byId(skin).id() : DEFAULT.skin(), bg, lite);
    }

    public static Prefs setSkin(ServerPlayer player, String skinId) {
        Prefs now = get(player);
        return put(player, new Prefs(MenuSkins.byId(skinId).id(), now.backgrounds(), now.lite()));
    }

    public static Prefs toggleBackgrounds(ServerPlayer player) {
        Prefs now = get(player);
        return put(player, new Prefs(now.skin(), !now.backgrounds(), now.lite()));
    }

    public static Prefs toggleLite(ServerPlayer player) {
        Prefs now = get(player);
        return put(player, new Prefs(now.skin(), now.backgrounds(), !now.lite()));
    }

    /** Save and write the player's file now (a choice survives a crash or a relog right after). */
    public static Prefs put(ServerPlayer player, Prefs prefs) {
        if (player == null || prefs == null) {
            return DEFAULT;
        }
        UUID id = player.m_20148_();
        CompoundTag t = new CompoundTag();
        t.m_128359_("skin", MenuSkins.byId(prefs.skin()).id());
        t.m_128379_("backgrounds", prefs.backgrounds());
        t.m_128379_("lite", prefs.lite());
        LmPlayerStore.root(id).m_128365_(KEY, t);
        LmPlayerStore.flush(id);
        return get(id);
    }
}
