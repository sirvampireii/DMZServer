package com.dbzlegacy.legacytextures.client;

import com.dbzlegacy.legacytextures.LegacyTextures;
import com.dbzlegacy.legacytextures.TextureMode;
import com.dbzlegacy.legacytextures.net.LtNetwork;
import com.dbzlegacy.legacytextures.resolve.Resolver;
import com.dbzlegacy.legacytextures.resolve.SetRegistry;
import java.io.InputStream;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * This client's view choice and the resolver built from it.
 *
 * <p>Players and NB Armors switches apply on the spot: the resolver is rebuilt and every DMZ cache
 * that remembered a texture answer is emptied before the next frame. The world switch only takes
 * effect through one resource reload, because block and item textures are baked into atlases.
 */
public final class ClientState {
    public static final Resolver RESOLVER = new Resolver(LtHooks::rawExists);
    private static volatile TextureMode mode = TextureMode.of(TextureMode.Preset.ALL_VANILLA, TextureMode.DEFAULT_SET);
    private static volatile SetRegistry registry = SetRegistry.vanillaOnly();
    private static volatile boolean worldHdLoaded;
    private static volatile String worldSetLoaded = TextureMode.DEFAULT_SET;
    private static volatile int revision;

    private ClientState() {
    }

    public static Path setsDir() {
        return FMLPaths.CONFIGDIR.get().resolve(LegacyTextures.MODID).resolve("texture_sets");
    }

    public static TextureMode mode() {
        return mode;
    }

    public static SetRegistry registry() {
        return registry;
    }

    public static boolean worldHdLoaded() {
        return worldHdLoaded;
    }

    /** Bumps on every change, so an open menu knows to redraw its buttons. */
    public static int revision() {
        return revision;
    }

    /** The server sent this player's stored choice (on join, after a change, or from /legacytextures). */
    public static void onServerSync(TextureMode next, boolean openMenu) {
        apply(next);
        if (openMenu) {
            Minecraft mc = Minecraft.m_91087_();
            mc.m_91152_(new TextureScreen(mc.f_91080_));
        }
    }

    /** The player picked something in the menu. The server stores it and sends it back. */
    public static void request(TextureMode next) {
        if (serverHasMod()) {
            LtNetwork.CHANNEL.sendToServer(new LtNetwork.SetMode(next));
        } else {
            apply(next);
        }
    }

    private static boolean serverHasMod() {
        ClientPacketListener conn = Minecraft.m_91087_().m_91403_();
        return conn != null && LtNetwork.CHANNEL.isRemotePresent(conn.m_104910_());
    }

    static void apply(TextureMode next) {
        mode = next;
        boolean reloadWorld = next.world != worldHdLoaded || (next.world && !next.set.equals(worldSetLoaded));
        refresh();
        if (reloadWorld) {
            LegacyTextures.LOG.info("world look changed ({}); reloading textures once", next);
            Minecraft.m_91087_().m_91391_();
        }
    }

    /** Rebuilds the resolver for the current choice and drops every cached texture answer. Main thread. */
    static void refresh() {
        RESOLVER.configure(registry, mode, worldHdLoaded, worldSetLoaded);
        CacheFlush.flushAll(mode);
        revision++;
    }

    /**
     * Called when the packs are opened at the start of a resource reload: re-reads the set folder
     * (a set dropped in while the game runs shows up after F3+T) and fixes the world look for the
     * packs being loaded.
     */
    static void beginReload() {
        SetRegistry next = SetRegistry.load(setsDir(), ClientState::defaultSet);
        for (String w : next.warnings()) {
            LegacyTextures.LOG.warn("texture sets: {}", w);
        }
        registry = next;
        worldHdLoaded = mode.world;
        worldSetLoaded = mode.set;
        RESOLVER.configure(registry, mode, worldHdLoaded, worldSetLoaded);
    }

    /** After every resource reload, on the main thread. */
    static void afterReload() {
        refresh();
        LegacyTextures.LOG.info("texture sets loaded: {} (view: {})", registry.all().keySet(), mode);
    }

    private static InputStream defaultSet(String file) {
        return ClientState.class.getResourceAsStream("/legacytextures/texture_sets/" + file);
    }
}
