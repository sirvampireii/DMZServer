package com.dbzlegacy.adaptivedifficulty.kube;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.kube.hotpath.CapsuleBan;
import com.dbzlegacy.adaptivedifficulty.kube.hotpath.HealCap;
import com.dbzlegacy.adaptivedifficulty.kube.hotpath.HotPathConfig;
import com.dbzlegacy.adaptivedifficulty.kube.hotpath.HeartstopStrip;
import com.dbzlegacy.adaptivedifficulty.kube.hotpath.PothalaClean;
import com.dbzlegacy.adaptivedifficulty.kube.hotpath.RaceLockClear;
import com.dbzlegacy.adaptivedifficulty.kube.hotpath.SilkTouchStrip;
import com.dbzlegacy.adaptivedifficulty.kube.hotpath.SilkyBan;
import com.dbzlegacy.adaptivedifficulty.kube.hotpath.SpawnerVanillaize;
import com.dbzlegacy.adaptivedifficulty.kube.hotpath.WeaponBonusScale;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Native replacements for the KubeJS player-tick and item-spawn hot path.
 * KubeJS skips those listeners when {@link #ENABLED} is visible.
 */
public final class LmHotPath {
    /** KubeJS checks this field and leaves the tick / item-spawn listeners idle. */
    public static final boolean ENABLED = true;
    public static final LmHotPath INSTANCE = new LmHotPath();

    private LmHotPath() {}

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        int age = player.f_19797_;
        if (age < 0) {
            return;
        }
        HotPathConfig config = HotPathConfig.get();
        if (due(age, config.weaponBonus.updateInterval)) {
            WeaponBonusScale.update(player);
        }
        if (due(age, config.healing.tickInterval)) {
            HealCap.clamp(player);
        }
        if (due(age, config.pothala.checkInterval)) {
            PothalaClean.clean(player);
        }
        if (age > 0 && due(age, config.raceLocks.syncInterval)) {
            RaceLockClear.clear(player);
        }
        if (SpawnerVanillaize.staggered(player)) {
            SpawnerVanillaize.scan(player);
        }
        if (due(age, config.capsules.purgeInterval)) {
            CapsuleBan.purge(player);
        }
        if (due(age, config.heartstop.interval)) {
            HeartstopStrip.strip(player);
        }
        if (due(age, config.silkTouch.scanInterval)) {
            SilkTouchStrip.purge(player);
        }
        if (due(age, config.silky.clothInterval)) {
            SilkyBan.purge(player, due(age, config.silky.toolInterval));
        }
    }

    @SubscribeEvent
    public void onItemSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().m_5776_() || !(event.getEntity() instanceof ItemEntity entity)) {
            return;
        }
        ItemStack stack = entity.m_32055_();
        if (stack == null || stack.m_41619_()) {
            return;
        }
        SpawnerVanillaize.onItemSpawn(stack);
        if (CapsuleBan.onItemSpawn(event, entity, stack)) {
            return;
        }
        SilkTouchStrip.onItemSpawn(entity, stack);
        SilkyBan.onItemSpawn(entity, entity.m_32055_());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        WeaponBonusScale.clearPlayer(player.m_20148_());
        WeaponBonusScale.update(player);
        HealCap.clamp(player);
        // 4.7.75: the healing caps show on Character Services Home instead of a chat line at every login.
        PothalaClean.clean(player);
        RaceLockClear.clear(player);
        SpawnerVanillaize.scan(player);
        CapsuleBan.purge(player);
        HeartstopStrip.strip(player);
        SilkTouchStrip.purge(player);
        SilkyBan.purge(player, true);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            WeaponBonusScale.clearPlayer(event.getEntity().m_20148_());
        }
    }

    private static boolean due(int age, int interval) {
        return age % HotPathConfig.ticks(interval) == 0;
    }

    public static void logReady() {
        AdaptiveDifficultyMod.LOGGER.info(
                "[{}] native hot path owns weapon bonus, heal cap, pothala, race-lock clear, "
                        + "spawner vanillaize, capsule ban, heartstop, silk touch, and silky",
                AdaptiveDifficultyMod.MOD_ID);
    }
}
