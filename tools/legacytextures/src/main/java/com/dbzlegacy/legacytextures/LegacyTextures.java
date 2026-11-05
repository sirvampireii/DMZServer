package com.dbzlegacy.legacytextures;

import com.dbzlegacy.legacytextures.net.LtNetwork;
import com.dbzlegacy.legacytextures.server.ServerEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Legacy Textures: HD texture sets for DragonMineZ and NB Armors with a per-player switch, and
 * DragonMineZ face animation (blink and shout, absorbed from DMZ Face Anim in 0.2.3).
 * The server only stores each player's choice; everything that draws lives in {@code client}.
 */
@Mod(LegacyTextures.MODID)
public final class LegacyTextures {
    public static final String MODID = "legacytextures";
    public static final String VERSION = "0.2.3";
    public static final Logger LOG = LoggerFactory.getLogger("LegacyTextures");

    @SuppressWarnings("removal") // FMLJavaModLoadingContext.get(): still the 47.x way to reach the mod bus
    public LegacyTextures() {
        LtNetwork.register();
        // [faceanim] in legacytextures-client.toml: what this viewer sees (client side only).
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                com.dbzlegacy.legacytextures.faceanim.FaceAnimConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(ServerEvents.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.dbzlegacy.legacytextures.client.ClientEvents.init(FMLJavaModLoadingContext.get().getModEventBus()));
        LOG.info("Legacy Textures {} loaded", VERSION);
    }
}
