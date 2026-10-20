package com.dbzlegacy.adaptivedifficulty.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * LegacyMechanics is server-primary. Client copies apply no mixins except the optional
 * KaiPath LM-consumer bootstrap fix ({@code KaiPathLmConsumerBootstrapMixin}).
 */
public final class LegacyMechanicsMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Physical client only (plugin-gated): keep KaiPath's LM GUI consumer alive when the
        // LM jar is also installed. Listed under mixins[] so jar audits see the registration.
        if (mixinClassName != null && mixinClassName.endsWith("KaiPathLmConsumerBootstrapMixin")) {
            return !dedicatedServer() && kaipathLmConsumerPresent();
        }
        if (!dedicatedServer()) {
            return false;
        }
        if (mixinClassName != null && mixinClassName.endsWith("DojoWarSenseMixin")) {
            return senseNetworkPresent();
        }
        if (mixinClassName != null && mixinClassName.endsWith("NoeaAbsorptionBonusGateMixin")) {
            return absorptionServicePresent();
        }
        if (mixinClassName != null && (
                mixinClassName.endsWith("DendeResetAbsorptionWipeMixin")
                        || mixinClassName.endsWith("CreateCharacterAbsorptionWipeMixin"))) {
            return absorptionServicePresent();
        }
        if (mixinClassName != null && mixinClassName.endsWith("MeleeStatsResetPlayerBlockMixin")) {
            return meleeResetCommandsPresent();
        }
        if (mixinClassName != null && mixinClassName.endsWith("MeleeStatsResetAbsorptionWipeMixin")) {
            return absorptionServicePresent() && meleeResetCommandsPresent();
        }
        if (mixinClassName != null && mixinClassName.endsWith("NoeaTravelSafetyCorpseFixMixin")) {
            return travelSafetyPresent();
        }
        if (mixinClassName != null && mixinClassName.endsWith("DmzultraNemesisSpawnGateMixin")) {
            return dedicatedServer() && dmzultraRivalPresent();
        }
        return dedicatedServer();
    }

    /**
     * Melee mod's self-target {@code /dmzstats reset} executor.
     * Look the class file up. Defining the mixin target here makes mixin
     * abort startup with "loaded too early".
     */
    private static boolean meleeResetCommandsPresent() {
        try {
            ClassLoader loader = LegacyMechanicsMixinPlugin.class.getClassLoader();
            return loader != null
                    && loader.getResource("com/dbzlegacy/mohistmelee/StatsResetCommands.class") != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Noea absorption service. Absent on a server that does not run Noea. */
    private static boolean absorptionServicePresent() {
        try {
            Class.forName(
                    "com.butterjaffa.noeabosses.MajinAbsorptionService",
                    false,
                    LegacyMechanicsMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Noea off-world inventory. Absent on a server that does not run Noea. */
    private static boolean travelSafetyPresent() {
        try {
            Class.forName(
                    "com.butterjaffa.noeabosses.TravelSafetyService",
                    false,
                    LegacyMechanicsMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Noea's tracking channel. Absent on a server that does not run Noea. */
    private static boolean senseNetworkPresent() {
        try {
            Class.forName(
                    "com.butterjaffa.noeabosses.sense.SenseNetwork",
                    false,
                    LegacyMechanicsMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * dmzultra rival journal — the nemesis gate mixin targets
     * {@code com.dmzultra.journal.Rival}. Absent on a server that does not run dmzultra.
     * Looked up without initializing so the mixin config does not load the target early.
     */
    private static boolean dmzultraRivalPresent() {
        try {
            Class.forName(
                    "com.dmzultra.journal.Rival",
                    false,
                    LegacyMechanicsMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** KaiPath optional LM GUI consumer — absent when KaiPath is not installed. */
    private static boolean kaipathLmConsumerPresent() {
        try {
            ClassLoader loader = LegacyMechanicsMixinPlugin.class.getClassLoader();
            return loader != null
                    && loader.getResource(
                                    "com/dmzlegacy/kaipath/ultra/consumers/legacymechanics/LmUltraConsumer.class")
                            != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean dedicatedServer() {
        try {
            Class<?> loader = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            Object dist = loader.getMethod("getDist").invoke(null);
            return dist != null && "DEDICATED_SERVER".equals(String.valueOf(dist));
        } catch (Throwable ignored) {
            return true;
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
