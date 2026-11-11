package com.dbzlegacy.mohistmelee;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Apply a Noea grab mixin only when that target's class file is on the classpath.
 *
 * <p>Intentionally dormant (3.0.3, M-P2-24): Noea is not on live and there is no Noea jar to build or
 * test against, so the three targets in {@code dmz_mohist_melee_fix.noea.mixins.json} are unverified
 * and nothing here applies today. If Noea returns, this gate would turn them back on against those
 * untested targets: re-verify them against that jar first. Removing the config is a separate call.
 * {@code Class.forName} here defines {@code GrabService} before Mixin can transform
 * it, and the grab mixin is then skipped as loaded too early.
 */
public final class OptionalNoeaMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return classFilePresent(targetClassName);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    /** Class-file lookup. Does not define the mixin target. */
    private static boolean classFilePresent(String binaryName) {
        if (binaryName == null || binaryName.isEmpty()) {
            return false;
        }
        try {
            ClassLoader loader = OptionalNoeaMixinPlugin.class.getClassLoader();
            String path = binaryName.replace('.', '/') + ".class";
            return loader != null && loader.getResource(path) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
