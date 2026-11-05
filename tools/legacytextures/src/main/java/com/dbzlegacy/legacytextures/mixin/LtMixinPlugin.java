package com.dbzlegacy.legacytextures.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * All four mixins are client-only (listed under "client"). The GeckoLib one is skipped when
 * GeckoLib is not installed, the face animation one when DragonMineZ (or GeckoLib) is not. Each applied mixin is logged once at startup so a log shows which
 * hooks are live; a hook whose target changed simply does not apply (require = 0) and the game
 * keeps drawing the mods' own textures.
 */
public final class LtMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOG = LoggerFactory.getLogger("LegacyTextures");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName != null && mixinClassName.endsWith("GeoModelMixin")) {
            return classPresent("software/bernie/geckolib/model/GeoModel.class");
        }
        if (mixinClassName != null && mixinClassName.endsWith("DMZSkinLayerFaceMixin")) {
            return classPresent("com/dragonminez/client/render/layer/DMZSkinLayer.class")
                    && classPresent("software/bernie/geckolib/model/GeoModel.class");
        }
        return true;
    }

    // Look the class file up. Loading the mixin target here makes mixin abort with "loaded too early".
    private static boolean classPresent(String resource) {
        try {
            ClassLoader loader = LtMixinPlugin.class.getClassLoader();
            return loader != null && loader.getResource(resource) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        LOG.info("hook applied: {} -> {}", mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1),
                targetClassName);
    }
}
