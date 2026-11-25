package com.dbzlegacy.adaptivedifficulty.dojo;

import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.Dojo;
import com.dbzlegacy.adaptivedifficulty.sparring.SparCombat;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.LmChat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * LM 4.7.88 dojo world rules ({@code legacymechanics:dojo}), Forge events only.
 *
 * <p>Building: a player may break or place blocks (and use buckets or tools that change a block) only inside their own
 * dojo's plot, and only when they are that dojo's master. Members, visitors, spectators and other masters are
 * refused; outside every plot (the war ring row, the space between plots) nobody builds. No staff bypass. Farmland in
 * the dojo world is never trampled. Only player-driven events are checked: the hall builder ({@link DojoPlacer},
 * {@link DojoBuilder}) places with {@code Level.setBlock} directly, which fires none of these events.
 *
 * <p>Ki: a ki attack (DMZ ki damage, or anything {@link SparCombat#classifyKiType} names) never hurts a player in the
 * dojo world, except between the two fighters of a running board bout or war bout. Blocked silently; covers player
 * ki, mob ki and a fighter's own splash.
 */
public final class DojoWorldRules {
    public static final String ONLY_MASTER = "Only the master of this dojo can build here.";
    public static final String NO_HALL = "Nobody can build outside a dojo hall.";
    /** The same refusal is shown at most once per this many ms per player (a held mouse button would spam it). */
    public static final long NOTICE_GAP_MS = 2_000L;

    public static final DojoWorldRules INSTANCE = new DojoWorldRules();

    private record Notice(String text, long at) {}

    private static final Map<UUID, Notice> LAST_NOTICE = new ConcurrentHashMap<>();

    private DojoWorldRules() {}

    // ------------------------------------------------------------------ building

    /** True for the server side of the dojo world. */
    public static boolean isDojoLevel(LevelAccessor level) {
        return level instanceof Level l && !l.f_46443_ && l.m_46472_().m_135782_().equals(DojoWorld.DIMENSION);
    }

    /**
     * Why {@code player} may not change the block at {@code pos} in {@code level}, or null when they may (not the
     * dojo world, or their own plot as its master).
     */
    public static String buildDenial(Player player, LevelAccessor level, BlockPos pos) {
        if (player == null || pos == null || !isDojoLevel(level)) {
            return null;
        }
        int plot = DojoWorld.plotIndexAt(pos);
        if (plot < 0) {
            return NO_HALL;
        }
        Dojo d = DojoWorld.dojoOnPlot(plot);
        if (d != null && d.masterUuid.equals(player.m_20148_().toString())) {
            return null;
        }
        return ONLY_MASTER;
    }

    /** The first refusal among {@code positions}, or null when every one is allowed. */
    public static String buildDenial(Player player, LevelAccessor level, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            String why = buildDenial(player, level, pos);
            if (why != null) {
                return why;
            }
        }
        return null;
    }

    /** Tell {@code player} why, unless the same words went to them in the last {@value #NOTICE_GAP_MS} ms. */
    static void notice(Player player, String why) {
        if (!(player instanceof ServerPlayer sp) || why == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Notice last = LAST_NOTICE.get(sp.m_20148_());
        if (last != null && last.text().equals(why) && now - last.at() < NOTICE_GAP_MS) {
            return;
        }
        LAST_NOTICE.put(sp.m_20148_(), new Notice(why, now));
        DmzRewards.msg(sp, LmChat.fail("Dojo", why));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        String why = buildDenial(event.getPlayer(), event.getLevel(), event.getPos());
        if (why != null) {
            event.setCanceled(true);
            notice(event.getPlayer(), why);
        }
    }

    /** Single and multi-block placement (beds, doors: {@link BlockEvent.EntityMultiPlaceEvent} arrives here too). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        List<BlockPos> at = new ArrayList<>();
        at.add(event.getPos());
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (BlockSnapshot s : multi.getReplacedBlockSnapshots()) {
                at.add(s.getPos());
            }
        }
        String why = buildDenial(p, event.getLevel(), at);
        if (why != null) {
            event.setCanceled(true);
            notice(p, why);
        }
    }

    /** Filling or emptying a bucket: the clicked block and the block in front of it. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBucket(FillBucketEvent event) {
        Player p = event.getEntity();
        if (p == null || !isDojoLevel(event.getLevel())
                || !(event.getTarget() instanceof BlockHitResult hit) || hit.m_6662_() != HitResult.Type.BLOCK) {
            return;
        }
        String why = buildDenial(p, event.getLevel(),
                List.of(hit.m_82425_(), hit.m_82425_().m_121945_(hit.m_82434_())));
        if (why != null) {
            event.setCanceled(true);
            notice(p, why);
        }
    }

    /** Stripping logs, tilling, path-making and other tool edits of a block. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onToolModify(BlockEvent.BlockToolModificationEvent event) {
        Player p = event.getPlayer();
        String why = buildDenial(p, event.getLevel(), event.getPos());
        if (why != null) {
            event.setCanceled(true);
            if (!event.isSimulated()) {
                notice(p, why);
            }
        }
    }

    /** Nobody tramples farmland in the dojo world (the gardens stay as built). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (isDojoLevel(event.getLevel())) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ ki

    /** A ki attack: DMZ's ki damage type, or a beam, blast, disk or other ki kind {@link SparCombat} recognises. */
    public static boolean isKi(DamageSource source) {
        if (source == null) {
            return false;
        }
        return DmzRewards.isKiDamage(source) || !"other".equals(SparCombat.classifyKiType(source));
    }

    /**
     * True when this ki hit must not land: the victim is a player in the dojo world and the attacker is not their
     * opponent in a running board bout or war bout (mob ki, a bystander's ki, and a fighter's own splash).
     */
    public static boolean kiBlocked(LivingEntity victim, DamageSource source) {
        if (!(victim instanceof ServerPlayer v) || !isDojoLevel(v.m_9236_()) || !isKi(source)) {
            return false;
        }
        Entity causing = source.m_7639_();
        if (causing instanceof ServerPlayer a && a != v && !a.m_20148_().equals(v.m_20148_())
                && (BoardSpar.isBoutPair(v, a) || DojoWar.inBoutTogether(v, a))) {
            return false;
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKiAttack(LivingAttackEvent event) {
        if (kiBlocked(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
        }
    }

    /** Backstop for ki damage that reaches hurt without an attack event. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKiHurt(LivingHurtEvent event) {
        if (kiBlocked(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
        }
    }
}
