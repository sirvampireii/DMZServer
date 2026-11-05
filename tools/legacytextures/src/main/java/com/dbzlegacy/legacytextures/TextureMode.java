package com.dbzlegacy.legacytextures;

import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One player's view choice. It decides what that player's own game draws; it never changes
 * what anyone else sees. {@code set} is the HD set used by every switch that is on.
 */
public final class TextureMode {
    public static final String DEFAULT_SET = "hd";
    private static final Pattern SET_ID = Pattern.compile("[a-z0-9_-]{1,32}");

    public enum Preset {
        ALL_HD(true, true, true),
        PLAYERS_HD_ONLY(true, true, false),
        ALL_VANILLA(false, false, false);

        public final boolean players;
        public final boolean nbArmor;
        public final boolean world;
        /** Lang key of the player-facing name (assets/legacytextures/lang). */
        public final String key;

        Preset(boolean players, boolean nbArmor, boolean world) {
            this.players = players;
            this.nbArmor = nbArmor;
            this.world = world;
            this.key = "legacytextures.preset." + name().toLowerCase(Locale.ROOT);
        }

        public static Preset parse(String name) {
            for (Preset p : values()) {
                if (p.name().equalsIgnoreCase(name)) {
                    return p;
                }
            }
            return null;
        }
    }

    public final String set;
    public final boolean players;
    public final boolean nbArmor;
    public final boolean world;

    public TextureMode(String set, boolean players, boolean nbArmor, boolean world) {
        this.set = cleanSetId(set);
        this.players = players;
        this.nbArmor = nbArmor;
        this.world = world;
    }

    public static TextureMode of(Preset preset, String set) {
        return new TextureMode(set, preset.players, preset.nbArmor, preset.world);
    }

    public TextureMode withPreset(Preset preset) {
        return of(preset, set);
    }

    public TextureMode withSet(String id) {
        return new TextureMode(id, players, nbArmor, world);
    }

    public TextureMode withPlayers(boolean hd) {
        return new TextureMode(set, hd, nbArmor, world);
    }

    public TextureMode withNbArmor(boolean hd) {
        return new TextureMode(set, players, hd, world);
    }

    public TextureMode withWorld(boolean hd) {
        return new TextureMode(set, players, nbArmor, hd);
    }

    /** The preset this mode matches, or null for a custom mix. */
    public Preset preset() {
        for (Preset p : Preset.values()) {
            if (p.players == players && p.nbArmor == nbArmor && p.world == world) {
                return p;
            }
        }
        return null;
    }

    public static String cleanSetId(String id) {
        String s = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return SET_ID.matcher(s).matches() ? s : DEFAULT_SET;
    }

    public void write(FriendlyByteBuf buf) {
        buf.m_130072_(set, 32);
        buf.writeBoolean(players);
        buf.writeBoolean(nbArmor);
        buf.writeBoolean(world);
    }

    public static TextureMode read(FriendlyByteBuf buf) {
        return new TextureMode(buf.m_130136_(32), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.m_128359_("set", set);
        tag.m_128379_("players", players);
        tag.m_128379_("nbArmor", nbArmor);
        tag.m_128379_("world", world);
        return tag;
    }

    public static TextureMode fromTag(CompoundTag tag) {
        return new TextureMode(tag.m_128461_("set"), tag.m_128471_("players"), tag.m_128471_("nbArmor"),
                tag.m_128471_("world"));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TextureMode m && m.set.equals(set) && m.players == players && m.nbArmor == nbArmor
                && m.world == world;
    }

    @Override
    public int hashCode() {
        return set.hashCode() * 8 + (players ? 4 : 0) + (nbArmor ? 2 : 0) + (world ? 1 : 0);
    }

    @Override
    public String toString() {
        return "set=" + set + " players=" + hd(players) + " nbArmor=" + hd(nbArmor) + " world=" + hd(world);
    }

    private static String hd(boolean on) {
        return on ? "hd" : "vanilla";
    }
}
