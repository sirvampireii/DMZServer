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
 *
 * <p>4.7.66: the Custom skin. The stored skin id is {@value MenuSkins#CUSTOM_ID}; the player's palette sits beside it
 * under {@code custom} (five {@code #rrggbb} tones as typed, {@code dark}, {@code motif}) and stays when the player
 * switches to a preset, so switching back restores it. The fixed registry cannot hold a per-player palette, so
 * {@link Prefs#skinOrDefault} builds the Custom skin from these values.
 */
public final class MenuPrefs {
    private MenuPrefs() {}

    public static final String KEY = "lm_menu_prefs";
    static final String CUSTOM_KEY = "custom";
    static final String[] TONE_KEYS = {"panel", "primary", "trim", "text", "confirm"};

    /** One player's choice. */
    public record Prefs(String skin, boolean backgrounds, boolean lite, MenuSkins.Custom custom) {
        public Prefs(String skin, boolean backgrounds, boolean lite) {
            this(skin, backgrounds, lite, MenuSkins.Custom.DEFAULT);
        }

        public MenuSkins.Skin skinOrDefault() {
            return MenuSkins.isCustom(skin) ? MenuSkins.custom(custom) : MenuSkins.byId(skin);
        }

        public boolean usingCustom() {
            return MenuSkins.isCustom(skin);
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
        String skinId = MenuSkins.isCustom(skin) ? MenuSkins.CUSTOM_ID
                : MenuSkins.known(skin) ? MenuSkins.byId(skin).id() : DEFAULT.skin();
        return new Prefs(skinId, bg, lite, readCustom(t));
    }

    /** The saved palette; a missing or unreadable tone keeps Saiyan Royal's, never black. */
    static MenuSkins.Custom readCustom(CompoundTag prefs) {
        MenuSkins.Custom d = MenuSkins.Custom.DEFAULT;
        if (prefs == null || !prefs.m_128441_(CUSTOM_KEY)) {
            return d;
        }
        CompoundTag c = prefs.m_128469_(CUSTOM_KEY);
        int[] tones = d.tones();
        for (int i = 0; i < TONE_KEYS.length; i++) {
            int v = c.m_128441_(TONE_KEYS[i]) ? MenuSkins.parseHex(c.m_128461_(TONE_KEYS[i])) : -1;
            if (v >= 0) {
                tones[i] = v;
            }
        }
        boolean dark = !c.m_128441_("dark") || c.m_128471_("dark");
        MenuSkins.Motif motif = c.m_128441_("motif") ? MenuSkins.motifByName(c.m_128461_("motif")) : null;
        return new MenuSkins.Custom(dark, motif == null ? d.motif() : motif, 0, 0, 0, 0, 0).withTones(tones);
    }

    public static Prefs setSkin(ServerPlayer player, String skinId) {
        Prefs now = get(player);
        String id = MenuSkins.isCustom(skinId) ? MenuSkins.CUSTOM_ID : MenuSkins.byId(skinId).id();
        return put(player, new Prefs(id, now.backgrounds(), now.lite(), now.custom()));
    }

    /** 4.7.66: save the player's own palette and wear it. */
    public static Prefs setCustom(ServerPlayer player, MenuSkins.Custom custom) {
        Prefs now = get(player);
        return put(player, new Prefs(MenuSkins.CUSTOM_ID, now.backgrounds(), now.lite(),
                custom == null ? MenuSkins.Custom.DEFAULT : custom));
    }

    public static Prefs toggleBackgrounds(ServerPlayer player) {
        Prefs now = get(player);
        return put(player, new Prefs(now.skin(), !now.backgrounds(), now.lite(), now.custom()));
    }

    public static Prefs toggleLite(ServerPlayer player) {
        Prefs now = get(player);
        return put(player, new Prefs(now.skin(), now.backgrounds(), !now.lite(), now.custom()));
    }

    /** Save and write the player's file now (a choice survives a crash or a relog right after). */
    public static Prefs put(ServerPlayer player, Prefs prefs) {
        if (player == null || prefs == null) {
            return DEFAULT;
        }
        UUID id = player.m_20148_();
        CompoundTag t = new CompoundTag();
        t.m_128359_("skin", MenuSkins.isCustom(prefs.skin()) ? MenuSkins.CUSTOM_ID : MenuSkins.byId(prefs.skin()).id());
        t.m_128379_("backgrounds", prefs.backgrounds());
        t.m_128379_("lite", prefs.lite());
        MenuSkins.Custom c = prefs.custom() == null ? MenuSkins.Custom.DEFAULT : prefs.custom();
        CompoundTag ct = new CompoundTag();
        int[] tones = c.tones();
        for (int i = 0; i < TONE_KEYS.length; i++) {
            ct.m_128359_(TONE_KEYS[i], MenuSkins.hex(tones[i]));
        }
        ct.m_128379_("dark", c.dark());
        ct.m_128359_("motif", c.motif().name().toLowerCase(java.util.Locale.ROOT));
        t.m_128365_(CUSTOM_KEY, ct);
        LmPlayerStore.root(id).m_128365_(KEY, t);
        LmPlayerStore.flush(id);
        return get(id);
    }
}
