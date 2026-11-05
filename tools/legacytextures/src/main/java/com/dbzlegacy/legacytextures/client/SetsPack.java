package com.dbzlegacy.legacytextures.client;

import com.dbzlegacy.legacytextures.LegacyTextures;
import com.dbzlegacy.legacytextures.resolve.Category;
import com.dbzlegacy.legacytextures.resolve.SetRegistry;
import com.dbzlegacy.legacytextures.resolve.TextureSet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.fml.ModList;

/**
 * The one client pack this mod adds (always on, fixed on top, rebuilt on every resource reload).
 * It serves two things:
 * <ol>
 *   <li>every set folder, config/legacytextures/texture_sets/&lt;id&gt;/assets/..., as-is, so a set
 *       dropped in needs no rebuild;</li>
 *   <li>when the viewer has world HD on, the active set's world textures at their original paths
 *       (minecraft:textures/block/..., dragonminez:textures/item/...), because block and item
 *       textures are baked into atlases and can only change with a reload.</li>
 * </ol>
 * With world HD off and no set folders it is empty, so it changes nothing.
 */
final class SetsPack implements PackResources {
    static final String ID = "legacytextures_sets";
    private static final PackMetadataSection META = new PackMetadataSection(
            Component.m_237113_("Legacy Textures sets"), 15);

    private final Map<ResourceLocation, Path> files;
    private final Map<String, Set<String>> byNamespace = new HashMap<>();

    private SetsPack(Map<ResourceLocation, Path> files) {
        this.files = files;
        for (ResourceLocation loc : files.keySet()) {
            byNamespace.computeIfAbsent(loc.m_135827_(), k -> new HashSet<>()).add(loc.m_135815_());
        }
    }

    static RepositorySource source() {
        return consumer -> {
            Pack.ResourcesSupplier supplier = id -> open();
            // Forge's 5-arg Info: hidden from the resource pack screen (it is required, so it cannot be turned off).
            Pack.Info info = new Pack.Info(Component.m_237113_("HD texture sets and the world HD switch"), 15, 15,
                    net.minecraft.world.flag.FeatureFlagSet.m_246902_(), true);
            consumer.accept(Pack.m_245512_(ID, Component.m_237113_("Legacy Textures"), true, supplier, info,
                    PackType.CLIENT_RESOURCES, Pack.Position.TOP, true, PackSource.f_10528_));
        };
    }

    private static SetsPack open() {
        ClientState.beginReload();
        SetRegistry sets = ClientState.registry();
        Map<ResourceLocation, Path> files = new HashMap<>();
        for (Path assets : sets.folders().values()) {
            index(assets, files, null);
        }
        if (ClientState.worldHdLoaded()) {
            List<TextureSet> chain = new ArrayList<>(sets.chain(sets.hdOrFallback(ClientState.mode().set)));
            Collections.reverse(chain); // the active set last, so it wins
            for (TextureSet set : chain) {
                if (set.isVanilla() || !set.covers(Category.WORLD)) {
                    continue;
                }
                Path folder = sets.folders().get(set.id);
                if (folder != null) {
                    overlay(folder.resolve(set.namespace), set, files);
                }
                Path inJar = modAssets(set.namespace);
                if (inJar != null) {
                    overlay(inJar, set, files);
                }
            }
        }
        LegacyTextures.LOG.info("sets pack: {} files ({} set folders, world HD {})", files.size(), sets.folders().size(),
                ClientState.worldHdLoaded() ? "on" : "off");
        return new SetsPack(files);
    }

    /** assets/&lt;ns&gt;/&lt;path&gt; under {@code assets} -> {@code ns:path}. */
    private static void index(Path assets, Map<ResourceLocation, Path> out, String onlyNs) {
        try (Stream<Path> walk = Files.walk(assets)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                Path rel = assets.relativize(p);
                if (rel.getNameCount() < 2) {
                    return;
                }
                String ns = rel.getName(0).toString();
                if (onlyNs != null && !onlyNs.equals(ns)) {
                    return;
                }
                ResourceLocation loc = ResourceLocation.m_214293_(ns, rel.subpath(1, rel.getNameCount()).toString()
                        .replace('\\', '/'));
                if (loc != null) {
                    out.put(loc, p);
                }
            });
        } catch (IOException e) {
            LegacyTextures.LOG.warn("could not read set folder {}: {}", assets, e.getMessage());
        }
    }

    /** The set's world-category textures under {@code nsRoot}, keyed by the file they stand in for. */
    private static void overlay(Path nsRoot, TextureSet set, Map<ResourceLocation, Path> out) {
        Path textures = nsRoot.resolve("textures");
        if (!set.basePath.isEmpty()) {
            textures = textures.resolve(set.basePath);
        }
        if (!Files.isDirectory(textures)) {
            return;
        }
        Path base = nsRoot;
        try (Stream<Path> walk = Files.walk(textures)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                ResourceLocation source = set.source(base.relativize(p).toString().replace('\\', '/'));
                if (source != null && Category.of(source) == Category.WORLD) {
                    out.put(source, p);
                }
            });
        } catch (IOException e) {
            LegacyTextures.LOG.warn("could not read set {} world textures: {}", set.id, e.getMessage());
        }
    }

    private static Path modAssets(String namespace) {
        try {
            Path p = ModList.get().getModFileById(LegacyTextures.MODID).getFile().findResource("assets", namespace);
            return Files.isDirectory(p) ? p : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public IoSupplier<InputStream> m_8017_(String... path) {
        return null;
    }

    @Override
    public IoSupplier<InputStream> m_214146_(PackType type, ResourceLocation loc) {
        if (type != PackType.CLIENT_RESOURCES) {
            return null;
        }
        Path p = files.get(loc);
        return p == null ? null : IoSupplier.m_246697_(p);
    }

    @Override
    public void m_8031_(PackType type, String namespace, String path, PackResources.ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES) {
            return;
        }
        Set<String> paths = byNamespace.get(namespace);
        if (paths == null) {
            return;
        }
        String prefix = path.endsWith("/") ? path : path + "/";
        for (String p : paths) {
            if (p.startsWith(prefix)) {
                ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(namespace, p);
                output.accept(loc, IoSupplier.m_246697_(files.get(loc)));
            }
        }
    }

    @Override
    public Set<String> m_5698_(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? byNamespace.keySet() : Set.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T m_5550_(MetadataSectionSerializer<T> serializer) {
        return serializer == PackMetadataSection.f_243696_ ? (T) META : null;
    }

    @Override
    public String m_5542_() {
        return ID;
    }

    @Override
    public boolean m_246538_() {
        return true;
    }

    @Override
    public void close() {
    }
}
