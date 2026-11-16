package com.dmzlegacy.kaipath.ultra.consumers.legacymechanics;

import com.dmzlegacy.kaipath.ultra.UltraUiBridge;
import com.dmzlegacy.kaipath.ultra.consumers.UltraGuiPartners;
import java.util.List;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Client bootstrap entry (historical name kept for KaiPath binary/mixin compatibility).
 *
 * <p>Registers <b>all</b> {@link UltraGuiPartners} — not only LegacyMechanics. Wire codecs and
 * the dynamic screen are shared across every {@code <modid>:gui} channel. KaiPath owns the
 * client channel. A partner loaded in this game too (both sides, singleplayer) links to it
 * ({@code UltraGuiLink}) instead of registering its own; one that registers anyway keeps its
 * channel and KaiPath skips it (no crash, no menus) - see {@code UltraGuiPartners}.
 *
 * <p>Partners are discovered from {@link UltraGuiPartners#MARKER_ENTRY} inside mod jars and
 * from {@code config/kaipath-ultra-partners.txt}. A new server mod does not need a KaiPath
 * release. {@link UltraGuiPartners#register(String...)} still works for tests.
 */
public final class LmUltraConsumer {
    private static boolean bootstrapped;

    private LmUltraConsumer() {}

    public static void bootstrap() {
        if (bootstrapped || !FMLEnvironment.dist.isClient()) {
            return;
        }
        bootstrapped = true;
        LmScreens.registerDefaults();
        List<String> partners = UltraGuiPartners.discoverAndRegister();
        onClientSetup(UltraGuiPartners::registerLocalPartners);
        UltraUiBridge.LOGGER.info(
                "[kaipath] Ultra GUI consumers ready partners={} (discovered, multi-mod registry)",
                String.join(",", partners));
    }

    /**
     * Run {@code task} on {@code FMLClientSetupEvent} (KaiPath's mod event bus; call during
     * KaiPath's construction). Reflection so the kit has no Forge mod-bus compile dependency;
     * if it fails the task runs now, which is what this class did before.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onClientSetup(Runnable task) {
        try {
            Class<?> ctx = Class.forName("net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext");
            Object bus = ctx.getMethod("getModEventBus").invoke(ctx.getMethod("get").invoke(null));
            Class<?> setup = Class.forName("net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent");
            Class<?> priority = Class.forName("net.minecraftforge.eventbus.api.EventPriority");
            Object normal = Enum.valueOf((Class) priority, "NORMAL");
            java.util.function.Consumer<Object> listener = e -> task.run();
            Class.forName("net.minecraftforge.eventbus.api.IEventBus")
                    .getMethod("addListener", priority, boolean.class, Class.class, java.util.function.Consumer.class)
                    .invoke(bus, normal, false, setup, listener);
        } catch (Throwable t) {
            task.run();
        }
    }
}
