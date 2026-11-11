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
            return ultraSensePresent();
        }
        // 4.7.47: these wipes were gated on Noea's absorption service. Noea is gone (DMZ Ultra
        // replaced it), so they stay off exactly as they already were on live; turning on the
        // God Ki wipe for character creation, Dende and /dmzstats reset is an owner decision.
        if (mixinClassName != null && (
                mixinClassName.endsWith("DendeResetAbsorptionWipeMixin")
                        || mixinClassName.endsWith("CreateCharacterAbsorptionWipeMixin")
                        || mixinClassName.endsWith("MeleeStatsResetAbsorptionWipeMixin"))) {
            return false;
        }
        if (mixinClassName != null && mixinClassName.endsWith("MeleeStatsResetPlayerBlockMixin")) {
            return meleeResetCommandsPresent();
        }
        if (mixinClassName != null && mixinClassName.endsWith("VipersCallUnendingVenomMixin")) {
            return vipersCallPresent();
        }
        if (mixinClassName != null && mixinClassName.endsWith("PrestigeServiceIntegrationMixin")) {
            return dmzRevampClassPresent("com/dmzrevamp/revamp/prestige/PrestigeService.class");
        }
        if (mixinClassName != null && mixinClassName.endsWith("DmzRevampPrestigeCapMixin")) {
            return dmzRevampClassPresent("com/dmzrevamp/revamp/prestige/PrestigeSystem.class");
        }
        if (mixinClassName != null && mixinClassName.endsWith("DmzultraNemesisSpawnGateMixin")) {
            return dedicatedServer() && dmzultraRivalPresent();
        }
        if (mixinClassName != null && (mixinClassName.endsWith("DmzultraChamberWaveTpMixin")
                || mixinClassName.endsWith("DmzultraChamberWaveCooldownMixin")
                || mixinClassName.endsWith("DmzultraTimeChamberTpPreviewMixin")
                || mixinClassName.endsWith("DmzultraGodChamberTpPreviewMixin"))) {
            return dedicatedServer() && dmzultraChamberPresent();
        }
        if (mixinClassName != null && mixinClassName.endsWith("DmzultraSlotCaptureMixin")) {
            return dedicatedServer() && dmzultraSlotsPresent();
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

    /**
     * Simply More's Viper's Call. Look the class file up (it is the mixin target, so it must not
     * be loaded here). Absent on a server without Simply More.
     */
    /** dmzrevamp is not a declared dependency; without it the target class is missing. */
    private static boolean dmzRevampClassPresent(String classFile) {
        try {
            ClassLoader loader = LegacyMechanicsMixinPlugin.class.getClassLoader();
            return loader != null && loader.getResource(classFile) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean vipersCallPresent() {
        try {
            ClassLoader loader = LegacyMechanicsMixinPlugin.class.getClassLoader();
            return loader != null && loader.getResource(
                    "net/rosemarythyme/simplymore/item/uniques/VipersCallItem.class") != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * DMZ Ultra's ki sense service (the dojo war sense target). Looked up as a class file so the
     * mixin config does not load the target early. Absent on a server without DMZ Ultra.
     */
    private static boolean ultraSensePresent() {
        try {
            ClassLoader loader = LegacyMechanicsMixinPlugin.class.getClassLoader();
            return loader != null && loader.getResource("com/dmzultra/sense/SenseService.class") != null;
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

    /**
     * dmzultra chamber waves - the chamber-wave TP mixins target {@code GodChamberWaves},
     * {@code TimeChamber} and {@code GodChamber}. Resource lookup only, so the targets are not loaded
     * early.
     */
    private static boolean dmzultraChamberPresent() {
        try {
            ClassLoader loader = LegacyMechanicsMixinPlugin.class.getClassLoader();
            return loader != null
                    && loader.getResource("com/dmzultra/divine/GodChamberWaves.class") != null
                    && loader.getResource("com/dmzultra/divine/TimeChamber.class") != null
                    && loader.getResource("com/dmzultra/divine/GodChamber.class") != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * dmzultra 0.4 character slots (4.7.43 slot capture at logout / stop): {@code SlotService} and the
     * {@code SlotStore} / {@code SlotIndex} / {@code StandAside} it is used with. Resource lookup only, so
     * the target is not loaded early. Absent on a server without Ultra 0.4.
     */
    private static boolean dmzultraSlotsPresent() {
        try {
            ClassLoader loader = LegacyMechanicsMixinPlugin.class.getClassLoader();
            return loader != null
                    && loader.getResource("com/dmzultra/slots/SlotService.class") != null
                    && loader.getResource("com/dmzultra/slots/SlotStore.class") != null
                    && loader.getResource("com/dmzultra/slots/SlotIndex.class") != null
                    && loader.getResource("com/dmzultra/slots/StandAside.class") != null;
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
