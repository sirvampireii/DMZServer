package com.dmzlegacy.kaipath.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Skips the FormParts hook when DMZUltra is not installed so the client still opens.
 */
public final class BodyPartMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName != null && mixinClassName.endsWith("FormPartsPreviewMixin")) {
            return present("com.dmzultra.client.forms.FormParts");
        }
        if (mixinClassName != null && mixinClassName.endsWith("RaceTailSuppressMixin")) {
            return present("com.dragonminez.client.util.BoneVisibilityHandler");
        }
        return true;
    }

    /** True when {@code name} loads in this client. False lets the client still open. */
    private static boolean present(String name) {
        try {
            Class.forName(name, false, BodyPartMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
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
}
