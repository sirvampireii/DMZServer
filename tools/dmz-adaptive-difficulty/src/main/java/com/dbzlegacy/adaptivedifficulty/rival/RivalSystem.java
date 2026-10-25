package com.dbzlegacy.adaptivedifficulty.rival;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.calc.LmOverhaulScaledCombat;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.LmChat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Facade for Rival System 4.7.10 ported into LegacyMechanics.
 */
public final class RivalSystem {
    private RivalSystem() {}

    public static void markMobKill(ServerPlayer killer) {
        if (!DifficultyConfig.get().enableRivalSystem || killer == null) {
            return;
        }
        RivalProximity.markMobKill(killer.m_20148_());
        RivalFusion.onMobKill(killer);
    }

    public static void onMobKillNear(ServerPlayer killer, LivingEntity victim) {
        if (!DifficultyConfig.get().enableRivalSystem || killer == null) {
            return;
        }
        RivalProximity.markMobKill(killer.m_20148_());
        RivalProximity.handleKillNearRivals(killer, victim);
        RivalFusion.onMobKill(killer);
    }

    public static void onLogin(ServerPlayer player) {
        if (!DifficultyConfig.get().enableRivalSystem || player == null) {
            return;
        }
        RivalStore.get().ensurePlayer(player);
    }

    public static void onLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID id = player.m_20148_();
        RivalChallengeManager.get().cleanupPlayer(id);
        RivalProximity.clearPlayer(id);
        RivalInstinct.clearPlayer(id);
        RivalSpectator.clear(id);
        RivalFusion.clear(id);
    }

    public static void pulse(MinecraftServer server, int tick) {
        if (!DifficultyConfig.get().enableRivalSystem || server == null) {
            return;
        }
        long now = System.currentTimeMillis();
        RivalStore.get().expireRequests(now);
        RivalChallengeManager.get().pulse(server, now);
        RivalProximity.pulse(server, now);
        RivalInstinct.pulse(server, now);
        RivalSpectator.pulse(server, now);
        RivalFusion.pulse(server, now);
        RivalStore.get().saveIfNeeded(now);
        RivalProgression.get().saveIfNeeded(now);
    }

    public static void onPlayerHurt(ServerPlayer victim, ServerPlayer attacker, DamageSource source) {
        if (!DifficultyConfig.get().enableRivalSystem) {
            return;
        }
        if (victim == null || attacker == null) {
            return;
        }
        if (DifficultyConfig.get().rivalChallenges) {
            RivalChallengeManager.get().queueHurt(victim, attacker, DmzRewards.isKiDamage(source));
        }
        RivalProximity.handleDamagedByRival(victim, attacker);
    }

    public static void onDeath(ServerPlayer victim, LivingEntity killerEntity) {
        if (!DifficultyConfig.get().enableRivalSystem || victim == null) {
            return;
        }
        ServerPlayer killer = killerEntity instanceof ServerPlayer sp ? sp : null;
        // A challenge KO (any damage source, ki and projectiles included) ends the challenge
        // here; the Nemesis climb is counted once in RivalChallengeManager.endChallenge.
        RivalChallengeManager.get().onDeath(victim, killer);
        if (killer != null) {
            RivalProximity.handleKillNearRivals(killer, victim);
        }
    }

    /* ========================= Command API ========================= */

    public static String silentRival(ServerPlayer player, ServerPlayer target) {
        if (player == null || target == null) {
            return "§cPlayer not found.";
        }
        if (player.m_20148_().equals(target.m_20148_())) {
            return "§cYou cannot rival yourself.";
        }
        RivalStore store = RivalStore.get();
        RivalPlayerRecord me = store.ensurePlayer(player);
        RivalPlayerRecord them = store.ensurePlayer(target);
        long now = System.currentTimeMillis();
        RivalLink myLink = me.getOrCreateLink(them.uuid, them.name, now);
        restorePastIfAny(me, them, myLink);

        if (myLink.mutual) {
            return "§eAlready mutual rivals with " + them.name + ".";
        }
        if (myLink.inviteSent || myLink.inviteReceived) {
            return "§cA visible declare is already pending with that player.";
        }
        // Already Declared (both silently rivaled) — Mutual needs Accept from both via Pending.
        if (isReciprocatedSilent(myLink)) {
            if (myLink.acceptedMutualOffer) {
                return "§eAlready Declared with §f" + them.name
                        + "§e. §8Waiting for them to Accept Mutual (Pending).";
            }
            return "§eAlready Declared with §f" + them.name
                    + "§e. §8For Mutual: Rival tab → Accept.";
        }
        // Already one-sided silent.
        if (myLink.declaredByMe && !myLink.declaredByThem) {
            return "§eAlready silently rivaled §f" + them.name
                    + " §8(Silent). §7They are not notified. For Mutual: Declare.";
        }

        RivalLink theirLink = them.rivals.get(me.uuid);
        // They already silently rivaled you → crossed silent → Declared for both.
        if (theirLink != null && theirLink.declaredByMe && !theirLink.mutual
                && !theirLink.inviteSent && !theirLink.inviteReceived) {
            restorePastIfAny(them, me, theirLink);
            promoteDeclared(player, target, me, them, myLink, theirLink, now);
            store.markDirty();
            return "§aDeclared with §f" + them.name
                    + "§a — you both silently rivaled each other."
                    + " §8Both must Accept Mutual in Pending.";
        }

        // One-sided silent: you get Silent benefits; target is not notified / sees nothing.
        myLink.declaredByMe = true;
        myLink.declaredByThem = false;
        myLink.visibleDeclare = false;
        myLink.inviteSent = false;
        myLink.inviteReceived = false;
        myLink.mutual = false;
        myLink.touch(now);
        me.declarationsSent++;
        store.markDirty();
        return "§aSilent rival on §f" + them.name + " §8[Silent]"
                + "\n§8They are not notified and do not see you."
                + "\n§8If they Silent you too, it becomes Declared. For Mutual: Declare.";
    }

    public static String declare(ServerPlayer player, ServerPlayer target) {
        if (player == null || target == null) {
            return "§cPlayer not found.";
        }
        if (player.m_20148_().equals(target.m_20148_())) {
            return "§cYou cannot declare yourself.";
        }
        RivalStore store = RivalStore.get();
        RivalPlayerRecord me = store.ensurePlayer(player);
        RivalPlayerRecord them = store.ensurePlayer(target);
        long now = System.currentTimeMillis();
        if (now - me.lastDeclareAt < RivalConstants.DECLARE_COOLDOWN_MS) {
            return "§cDeclare cooldown (" + ((RivalConstants.DECLARE_COOLDOWN_MS - (now - me.lastDeclareAt)) / 1000L) + "s).";
        }
        RivalLink myLink = me.getOrCreateLink(them.uuid, them.name, now);
        RivalLink theirLink = them.getOrCreateLink(me.uuid, me.name, now);
        restorePastIfAny(me, them, myLink);
        restorePastIfAny(them, me, theirLink);
        if (myLink.mutual) {
            return "§eAlready mutual rivals with " + them.name + ".";
        }
        // If they already sent a visible declare, accepting path (UUID so renames cannot break it).
        if (theirLink.inviteSent || myLink.inviteReceived) {
            return accept(player, "uuid:" + them.uuid);
        }
        if (myLink.inviteSent) {
            return "§eDeclare already pending for " + them.name + ".";
        }

        String declaredNote = "";
        if (isReciprocatedSilent(myLink)) {
            declaredNote = " §8(was Declared — sending visible declare for Mutual)";
        }

        // You keep Declared on your list. They get a Pending invite — accept → Mutual.
        myLink.declaredByMe = true;
        myLink.visibleDeclare = true;
        myLink.inviteSent = true;
        myLink.pendingExpireAt = now + RivalConstants.REQUEST_EXPIRE_MS;
        myLink.mutual = false;
        theirLink.inviteReceived = true;
        theirLink.pendingExpireAt = myLink.pendingExpireAt;
        theirLink.mutual = false;
        // Do NOT set declaredByThem / declaredByMe on them — decline/ignore means no benefits.
        me.lastDeclareAt = now;
        me.declarationsSent++;

        RivalStore.DeclareRequest req = new RivalStore.DeclareRequest();
        req.fromUuid = me.uuid;
        req.fromName = me.name;
        req.toUuid = them.uuid;
        req.toName = them.name;
        req.createdAt = now;
        req.expiresAt = myLink.pendingExpireAt;
        store.declareRequests.put(me.uuid + ">" + them.uuid, req);
        store.markDirty();

        DmzRewards.msg(target, LmChat.tagged("Rival", "§e" + me.name + " §7visibly declared you as a rival!"));
        DmzRewards.msg(target, LmChat.tip("/rival", "→ Rival"));
        return "§aDeclared §f" + them.name + "§a. §8They appear on your list as Declared;"
                + " they were notified (Pending)." + declaredNote;
    }

    public static String accept(ServerPlayer player, String otherName) {
        RivalStore store = RivalStore.get();
        RivalPlayerRecord me = store.ensurePlayer(player);
        RivalPlayerRecord them = findByName(store, otherName);
        if (them == null) {
            return "§cNo pending declare from that player.";
        }
        RivalLink myLink = me.rivals.get(them.uuid);
        long now = System.currentTimeMillis();

        // Declared (both Silent) → each side Accepts Mutual confirm (both required).
        if (myLink != null && isReciprocatedSilent(myLink) && !myLink.mutual) {
            RivalLink theirLink = them.getOrCreateLink(me.uuid, me.name, now);
            if (!myLink.acceptedMutualOffer) {
                myLink.acceptedMutualOffer = true;
                myLink.inviteReceived = false;
                myLink.pendingExpireAt = 0L;
                me.declarationsAccepted++;
                if (!theirLink.acceptedMutualOffer) {
                    store.markDirty();
                    ServerPlayer online = onlineByUuid(player.m_20194_(), them.uuid);
                    if (online != null) {
                        DmzRewards.msg(online, LmChat.tagged("Rival",
                                "§e" + me.name + " §7accepted Mutual — your turn (Pending)."));
                    }
                    return "§aAccepted Mutual with §f" + them.name
                            + "§a. §8Waiting for them to Accept too (Pending).";
                }
            } else if (!theirLink.acceptedMutualOffer) {
                return "§eYou already Accepted — waiting for §f" + them.name + " §eto Accept Mutual.";
            }
            // Both accepted — promote (may need Mutual slot replace).
            myLink.declaredByMe = true;
            myLink.declaredByThem = true;
            theirLink.declaredByMe = true;
            theirLink.declaredByThem = true;
            return completeMutualAccept(player, me, them, myLink, theirLink, now, true);
        }

        if (myLink == null || !myLink.inviteReceived) {
            return "§cNo pending declare from " + them.name + ".";
        }
        RivalLink theirLink = them.getOrCreateLink(me.uuid, me.name, now);
        myLink.declaredByMe = true;
        myLink.declaredByThem = true;
        theirLink.declaredByMe = true;
        theirLink.declaredByThem = true;
        store.clearInviteFlags(them.uuid, me.uuid);
        store.declareRequests.remove(them.uuid + ">" + me.uuid);
        me.declarationsAccepted++;
        return completeMutualAccept(player, me, them, myLink, theirLink, now, false);
    }

    /**
     * Finish Accept after the player picks which Mutual to replace when at the slot cap.
     * {@code replaceArg} is {@code uuid:…} or a rival name.
     */
    public static String acceptReplace(ServerPlayer player, String replaceArg) {
        RivalStore store = RivalStore.get();
        RivalPlayerRecord me = store.ensurePlayer(player);
        String pendingUuid = me.pendingMutualAcceptUuid == null ? "" : me.pendingMutualAcceptUuid.trim();
        if (pendingUuid.isBlank()) {
            return UltraStyle.dangerLine("No Mutual accept waiting for a slot replace.");
        }
        RivalPlayerRecord them = store.get(pendingUuid);
        if (them == null) {
            me.pendingMutualAcceptUuid = "";
            store.markDirty();
            return UltraStyle.dangerLine("That pending Accept expired.");
        }
        RivalPlayerRecord drop = findByName(store, replaceArg);
        if (drop == null) {
            return UltraStyle.dangerLine("Pick one of your Mutual rivals to replace.");
        }
        if (drop.uuid.equals(them.uuid)) {
            return UltraStyle.dangerLine("Pick a different Mutual to replace.");
        }
        RivalLink dropLink = me.rivals.get(drop.uuid);
        if (dropLink == null || !dropLink.mutual) {
            return UltraStyle.dangerLine("That player is not one of your Mutual rivals.");
        }
        demoteMutualPair(me, drop);
        notifyReplacedMutual(player, drop);
        RivalLink myLink = me.getOrCreateLink(them.uuid, them.name, System.currentTimeMillis());
        RivalLink theirLink = them.getOrCreateLink(me.uuid, me.name, System.currentTimeMillis());
        long now = System.currentTimeMillis();
        myLink.declaredByMe = true;
        myLink.declaredByThem = true;
        theirLink.declaredByMe = true;
        theirLink.declaredByThem = true;
        me.pendingMutualAcceptUuid = "";
        promoteMutual(me, them, myLink, theirLink, now);
        store.markDirty();
        notifyMutualAccepted(player, me, them, true);
        // Colors via UltraStyle so bare section-sign never lands in this success path.
        return UltraStyle.GREEN + "Accepted " + UltraStyle.WHITE + them.name
                + UltraStyle.GREEN + " - Mutual!"
                + UltraStyle.DARK_GRAY + " Replaced Mutual with " + UltraStyle.WHITE + drop.name
                + UltraStyle.DARK_GRAY + ".";
    }

    /** True when this player must pick a Mutual to replace before Accept completes. */
    public static boolean needsMutualReplacePick(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        RivalPlayerRecord me = RivalStore.get().ensurePlayer(player);
        return me != null && me.pendingMutualAcceptUuid != null && !me.pendingMutualAcceptUuid.isBlank();
    }

    /**
     * Abort a Mutual accept that is waiting on a slot replace. Leaves both sides Declared
     * (invite flags were already cleared when Accept hit the slot cap).
     */
    public static String cancelMutualReplace(ServerPlayer player) {
        if (player == null) {
            return UltraStyle.dangerLine("This only works in-game.");
        }
        RivalStore store = RivalStore.get();
        RivalPlayerRecord me = store.ensurePlayer(player);
        String pendingUuid = me == null || me.pendingMutualAcceptUuid == null
                ? ""
                : me.pendingMutualAcceptUuid.trim();
        if (pendingUuid.isBlank()) {
            return UltraStyle.dangerLine("No Mutual accept waiting for a slot replace.");
        }
        String name = pendingMutualAcceptName(player);
        me.pendingMutualAcceptUuid = "";
        store.markDirty();
        if (name == null || name.isBlank() || name.equals(pendingUuid)) {
            return UltraStyle.YELLOW + "Cancelled Mutual accept. " + UltraStyle.DARK_GRAY
                    + "You can Accept again later from Requests.";
        }
        return UltraStyle.YELLOW + "Cancelled Mutual accept for " + UltraStyle.WHITE + name
                + UltraStyle.YELLOW + ". " + UltraStyle.DARK_GRAY
                + "You can Accept again later from Requests.";
    }

    public static String pendingMutualAcceptName(ServerPlayer player) {
        if (player == null) {
            return "";
        }
        RivalPlayerRecord me = RivalStore.get().ensurePlayer(player);
        if (me == null || me.pendingMutualAcceptUuid == null || me.pendingMutualAcceptUuid.isBlank()) {
            return "";
        }
        RivalPlayerRecord them = RivalStore.get().get(me.pendingMutualAcceptUuid.trim());
        if (them != null && them.name != null && !them.name.isBlank()) {
            return them.name;
        }
        return me.pendingMutualAcceptUuid.trim();
    }

    private static String completeMutualAccept(
            ServerPlayer player,
            RivalPlayerRecord me,
            RivalPlayerRecord them,
            RivalLink myLink,
            RivalLink theirLink,
            long now,
            boolean dualSilent
    ) {
        RivalStore store = RivalStore.get();
        if (needsMutualSlot(me, them.uuid)) {
            me.pendingMutualAcceptUuid = them.uuid;
            store.markDirty();
            return "§eMutual slots full (§f" + RivalConstants.MAX_MUTUAL_RIVALS
                    + "§e). Pick which Mutual to replace for §f" + them.name + "§e.";
        }
        me.pendingMutualAcceptUuid = "";
        promoteMutual(me, them, myLink, theirLink, now);
        store.markDirty();
        notifyMutualAccepted(player, me, them, dualSilent);
        return dualSilent
                ? "§aAccepted §f" + them.name + " §a— Declared → Mutual!"
                : "§aAccepted rivalry with §f" + them.name + " §a— Mutual!";
    }

    private static void notifyMutualAccepted(
            ServerPlayer player, RivalPlayerRecord me, RivalPlayerRecord them, boolean dualSilent
    ) {
        ServerPlayer online = onlineByUuid(player.m_20194_(), them.uuid);
        if (online != null) {
            // The other player has no menu open. The actor's result is the return line.
            DmzRewards.msg(online, LmChat.card(
                    "Rival Mutual",
                    "/rival",
                    null,
                    dualSilent
                            ? "§a" + me.name + " accepted — Declared → Mutual!"
                            : "§a" + me.name + " accepted your rivalry — Mutual!",
                    "§8Both ways. Full rivalry benefits."));
        }
    }

    private static boolean needsMutualSlot(RivalPlayerRecord record, String newUuid) {
        if (record == null) {
            return false;
        }
        RivalLink existing = record.rivals.get(newUuid);
        if (existing != null && existing.mutual) {
            return false;
        }
        return record.countMutual() >= RivalConstants.MAX_MUTUAL_RIVALS;
    }

    private static void demoteMutualPair(RivalPlayerRecord me, RivalPlayerRecord other) {
        if (me == null || other == null) {
            return;
        }
        RivalLink myLink = me.rivals.get(other.uuid);
        if (myLink != null) {
            myLink.mutual = false;
            myLink.isNemesis = false;
        }
        RivalLink theirLink = other.rivals.get(me.uuid);
        if (theirLink != null) {
            theirLink.mutual = false;
            theirLink.isNemesis = false;
        }
        if (other.uuid.equals(me.nemesisUuid)) {
            me.nemesisUuid = "";
        }
        if (me.uuid.equals(other.nemesisUuid)) {
            other.nemesisUuid = "";
        }
    }

    private static void notifyReplacedMutual(ServerPlayer actor, RivalPlayerRecord other) {
        if (actor == null || other == null) {
            return;
        }
        ServerPlayer otherOnline = onlineByUuid(actor.m_20194_(), other.uuid);
        if (otherOnline != null) {
            DmzRewards.msg(otherOnline, LmChat.tagged("Rival",
                    "§e" + actor.m_7755_().getString()
                            + " §7replaced your Mutual slot — now Declared."));
        }
    }

    public static String decline(ServerPlayer player, String otherName) {
        RivalStore store = RivalStore.get();
        RivalPlayerRecord me = store.ensurePlayer(player);
        RivalPlayerRecord them = findByName(store, otherName);
        if (them == null) {
            return "§cNo pending declare from that player.";
        }
        RivalLink myLink = me.rivals.get(them.uuid);
        if (myLink == null) {
            return "§cNo pending declare from " + them.name + ".";
        }
        // Dual Silent Declared — decline Mutual confirm; stay Declared on both lists.
        if (isReciprocatedSilent(myLink) && !myLink.mutual
                && (myLink.inviteReceived || myLink.needsMutualConfirm())) {
            RivalLink theirLink = them.rivals.get(me.uuid);
            myLink.inviteReceived = false;
            myLink.inviteSent = false;
            myLink.pendingExpireAt = 0L;
            myLink.acceptedMutualOffer = false;
            if (theirLink != null) {
                theirLink.inviteReceived = false;
                theirLink.inviteSent = false;
                theirLink.pendingExpireAt = 0L;
                theirLink.acceptedMutualOffer = false;
            }
            store.declareRequests.remove(them.uuid + ">" + me.uuid);
            store.declareRequests.remove(me.uuid + ">" + them.uuid);
            me.declarationsDeclined++;
            store.markDirty();
            ServerPlayer online = onlineByUuid(player.m_20194_(), them.uuid);
            if (online != null) {
                DmzRewards.msg(online, LmChat.tagged("Rival",
                        "§e" + me.name + " §7declined Mutual — still Declared."));
            }
            return "§eDeclined Mutual with §f" + them.name + "§e. §8Still Declared on both lists.";
        }
        if (!myLink.inviteReceived) {
            return "§cNo pending declare from " + them.name + ".";
        }
        store.clearInviteFlags(them.uuid, me.uuid);
        store.declareRequests.remove(them.uuid + ">" + me.uuid);
        // Decliner keeps no benefits; declarer keeps Declared on their list.
        myLink.declaredByMe = false;
        myLink.visibleDeclare = false;
        myLink.inviteReceived = false;
        myLink.pendingExpireAt = 0L;
        RivalLink theirLink = them.rivals.get(me.uuid);
        if (theirLink != null) {
            theirLink.inviteSent = false;
            theirLink.pendingExpireAt = 0L;
            theirLink.declaredByMe = true;
            theirLink.visibleDeclare = true;
            theirLink.declaredByThem = false;
        }
        me.declarationsDeclined++;
        store.markDirty();
        ServerPlayer online = onlineByUuid(player.m_20194_(), them.uuid);
        if (online != null) {
            DmzRewards.msg(online, LmChat.fail("Rival", "§f" + me.name + " §cdeclined your declare."));
            DmzRewards.msg(online, LmChat.info("Rival", "They stay on your list as Declared."));
        }
        return "§eDeclined rivalry from " + them.name + ".";
    }

    public static String remove(ServerPlayer player, String otherName) {
        RivalStore store = RivalStore.get();
        RivalPlayerRecord me = store.ensurePlayer(player);
        RivalPlayerRecord them = findByName(store, otherName);
        if (them == null) {
            return "§cRival not found: " + otherName;
        }
        RivalLink myLink = me.rivals.get(them.uuid);
        if (myLink == null) {
            return "§cYou have no rivalry with " + them.name + ".";
        }
        RivalStatus st = myLink.status();
        boolean wasShared = st == RivalStatus.MUTUAL || st == RivalStatus.NEMESIS;
        boolean wasDeclared = st == RivalStatus.DECLARED;
        boolean notifyThem = wasShared || wasDeclared;

        // Remover always archives their side into history.
        me.rivals.remove(them.uuid);
        archiveRivalLink(me, them.uuid, myLink);
        me.rivalsRemoved++;
        if (them.uuid.equals(me.nemesisUuid)) {
            me.nemesisUuid = "";
        }

        RivalLink theirLink = them.rivals.get(me.uuid);
        if (wasShared) {
            /*
             * Mutual / Nemesis remove: other keeps a one-way declare (Silent) toward you.
             * Remover sees them in History only.
             */
            if (theirLink != null) {
                demoteToOneWayDeclare(theirLink, me.name);
                if (me.uuid.equals(them.nemesisUuid)) {
                    them.nemesisUuid = "";
                }
                them.recalcTotalRp();
            }
        } else if (wasDeclared) {
            /* Reciprocated Declared: they keep one-way Silent if they still declare you. */
            if (theirLink != null) {
                if (theirLink.declaredByMe) {
                    demoteToOneWayDeclare(theirLink, me.name);
                } else {
                    them.rivals.remove(me.uuid);
                }
                them.recalcTotalRp();
            }
        } else {
            /* Silent / Pending: clear only your side; scrub invite / mirror flags on them. */
            if (theirLink != null) {
                theirLink.declaredByThem = false;
                theirLink.inviteReceived = false;
                theirLink.inviteSent = false;
                theirLink.pendingExpireAt = 0L;
                if (!theirLink.declaredByMe && !theirLink.mutual) {
                    them.rivals.remove(me.uuid);
                }
                them.recalcTotalRp();
            }
        }
        store.clearInviteFlags(me.uuid, them.uuid);
        store.declareRequests.remove(me.uuid + ">" + them.uuid);
        store.declareRequests.remove(them.uuid + ">" + me.uuid);
        me.recalcTotalRp();
        store.markDirty();

        String msg = "§eRemoved rivalry with §f" + them.name + ".";
        if (wasShared) {
            msg += "\n§8Saved to History. They still have you as Declared.";
        } else if (wasDeclared) {
            msg += "\n§8Saved to History. They may still have you as Declared.";
        } else {
            msg += "\n§8Saved to History.";
        }

        if (notifyThem) {
            ServerPlayer online = onlineByUuid(player.m_20194_(), them.uuid);
            if (online != null) {
                if (wasShared) {
                    DmzRewards.msg(online, LmChat.fail("Rival", "§f" + me.name + " §7ended Mutual rivalry with you."));
                    DmzRewards.msg(online, LmChat.info("Rival", "You still have them as Declared."));
                } else if (wasDeclared) {
                    DmzRewards.msg(online, LmChat.note("Rival", "§f" + me.name + " §7ended Declared rivalry with you."));
                    DmzRewards.msg(online, LmChat.info("Rival", "You still have them as Declared."));
                }
            }
        }
        return msg;
    }

    /** Keep one-way Declared (visible) after Mutual remove — benefits for this owner only. */
    private static void demoteToOneWayDeclare(RivalLink link, String otherName) {
        if (link == null) {
            return;
        }
        link.mutual = false;
        link.isNemesis = false;
        link.declaredByMe = true;
        link.visibleDeclare = true;
        link.declaredByThem = false;
        link.inviteSent = false;
        link.inviteReceived = false;
        link.acceptedMutualOffer = false;
        link.pendingExpireAt = 0L;
        link.mutualSince = 0L;
        if (otherName != null && !otherName.isBlank()) {
            link.name = otherName;
        }
        link.touch(System.currentTimeMillis());
    }

    /** Guide when the rival list is empty (CNPC + chat). */
    public static List<String> emptyRivalListGuide() {
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.LABEL + "You do not have any rivals on your list yet.");
        lines.add(UltraStyle.LABEL + "From the Rival menu, open the " + UltraStyle.VALUE + "Rival " + UltraStyle.LABEL + "tab and pick a player.");
        lines.add(UltraStyle.LABEL + "When you both accept, they appear here for duels, rival TP, and progress.");
        lines.add(UltraStyle.DIM + "Silent rivals and declare invites are on that same Rival tab.");
        return lines;
    }

    public static List<String> listLines(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        RivalPlayerRecord me = RivalStore.get().ensurePlayer(player);
        if (me == null || me.rivals.isEmpty()) {
            lines.addAll(emptyRivalListGuide());
            return lines;
        }
        lines.add(UltraStyle.HEADER + "Your rivals");
        List<Map.Entry<String, RivalLink>> entries = new ArrayList<>(me.rivals.entrySet());
        entries.sort(rivalListOrder());
        for (Map.Entry<String, RivalLink> e : entries) {
            RivalLink link = e.getValue();
            if (link == null) {
                continue;
            }
            RivalStatus st = link.status();
            // Incoming Pending Declares live under Pending Invites (accept/decline).
            // Outgoing Declares show on this list as Declared (even while invite is open).
            if (st == RivalStatus.PENDING || st == RivalStatus.NONE) {
                continue;
            }
            if (!link.mutual && !link.declaredByMe) {
                continue;
            }
            RivalConstants.RpTier tier = RivalConstants.tierFor(link.points);
            lines.add(UltraStyle.VALUE + link.name + " " + UltraStyle.DIM + "[" + st.label() + "] " + UltraStyle.MARK
                    + tier.color() + tier.name() + " " + UltraStyle.LABEL + "RP " + UltraStyle.VALUE + (int) link.points
                    + (link.deathLosses > 0 ? " " + UltraStyle.LABEL + "deaths " + UltraStyle.DANGER + link.deathLosses : ""));
            lines.addAll(ProvingGrounds.listLines(link));
        }
        return lines;
    }

    /**
     * Encoded rival cards for inventory GUIs (one string per rival).
     * Fields tab-separated: uuid, name, status, tier, rp, wins, losses, draws,
     * deathLosses, deathWins, online(0/1), past(0/1), presenceMs, lastBattleAt.
     */
    public static List<String> currentRivalCards(ServerPlayer player) {
        return rivalCards(player, false);
    }

    /** Encoded past-rival cards (archived on remove). Same format as {@link #currentRivalCards}. */
    public static List<String> pastRivalCards(ServerPlayer player) {
        return rivalCards(player, true);
    }

    /**
     * Pending declare invites (incoming + outgoing).
     * Fields tab-separated: uuid, name, direction(IN|OUT), expiresAtMs, online(0/1).
     */
    public static List<String> pendingInviteCards(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        RivalPlayerRecord me = RivalStore.get().ensurePlayer(player);
        if (me == null || me.rivals == null || me.rivals.isEmpty()) {
            return out;
        }
        MinecraftServer server = player.m_20194_();
        long now = System.currentTimeMillis();
        List<Map.Entry<String, RivalLink>> entries = new ArrayList<>(me.rivals.entrySet());
        entries.sort(Comparator.comparing((Map.Entry<String, RivalLink> e) -> {
            RivalLink link = e.getValue();
            if (link == null) {
                return Long.MAX_VALUE;
            }
            return link.pendingExpireAt > 0L ? link.pendingExpireAt : Long.MAX_VALUE;
        }));
        for (Map.Entry<String, RivalLink> e : entries) {
            RivalLink link = e.getValue();
            if (link == null || link.mutual) {
                continue;
            }
            boolean mutualConfirm = link.needsMutualConfirm();
            boolean incoming = link.inviteReceived || mutualConfirm;
            boolean outgoing = link.inviteSent && !mutualConfirm;
            if (!incoming && !outgoing) {
                continue;
            }
            if (link.pendingExpireAt > 0L && now > link.pendingExpireAt) {
                continue;
            }
            String uuid = link.uuid == null || link.uuid.isBlank() ? e.getKey() : link.uuid;
            String name = link.name == null || link.name.isBlank() ? uuid : link.name;
            name = name.replace('\t', ' ').replace('\n', ' ');
            boolean online = false;
            if (server != null && uuid != null && !uuid.isBlank()) {
                try {
                    online = server.m_6846_().m_11259_(java.util.UUID.fromString(uuid)) != null;
                } catch (IllegalArgumentException ignored) {
                    online = false;
                }
            }
            // Prefer showing IN when both flags somehow set (should not happen).
            String dir = incoming ? "IN" : "OUT";
            String kind = mutualConfirm ? "mutual" : "";
            out.add(String.join("\t",
                    nullToEmpty(uuid),
                    nullToEmpty(name),
                    dir,
                    String.valueOf(Math.max(0L, link.pendingExpireAt)),
                    online ? "1" : "0",
                    kind));
        }
        return out;
    }

    /** Chat lines for pending declare invites. */
    public static List<String> pendingInviteLines(ServerPlayer player) {
        List<String> cards = pendingInviteCards(player);
        List<String> lines = new ArrayList<>();
        if (cards.isEmpty()) {
            lines.add(UltraStyle.LABEL + "No pending declare invites.");
            lines.add(UltraStyle.DIM + "Outgoing: you Declared someone.");
            lines.add(UltraStyle.DIM + "Incoming: they Declared you — Accept or Decline.");
            return lines;
        }
        lines.add(UltraStyle.YELLOW + UltraStyle.BOLD + "Declare Invites");
        for (String card : cards) {
            String[] p = card.split("\t", -1);
            if (p.length < 3) {
                continue;
            }
            String name = p[1];
            String dir = p[2];
            boolean online = p.length > 4 && "1".equals(p[4]);
            boolean mutualConfirm = p.length > 5 && "mutual".equalsIgnoreCase(p[5]);
            if ("IN".equals(dir)) {
                if (mutualConfirm) {
                    lines.add(UltraStyle.YELLOW + "◀ Mutual confirm " + UltraStyle.VALUE + name + (online ? " " + UltraStyle.GREEN + "●" : " " + UltraStyle.DIM + "○")
                            + " " + UltraStyle.DIM + "— both must Accept");
                } else {
                    lines.add(UltraStyle.GREEN + "◀ Incoming " + UltraStyle.VALUE + name + (online ? " " + UltraStyle.GREEN + "●" : " " + UltraStyle.DIM + "○")
                            + " " + UltraStyle.DIM + "— Accept or Decline");
                }
            } else {
                lines.add(UltraStyle.GOLD + "▶ Outgoing " + UltraStyle.VALUE + name + (online ? " " + UltraStyle.GREEN + "●" : " " + UltraStyle.DIM + "○")
                        + " " + UltraStyle.DIM + "— waiting on them");
            }
        }
        return lines;
    }

    /** Chat/history page lines for previous rivals. */
    public static List<String> historyLines(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        RivalPlayerRecord me = RivalStore.get().ensurePlayer(player);
        if (me == null || me.pastRivals == null || me.pastRivals.isEmpty()) {
            lines.add(UltraStyle.LABEL + "No previous rivals yet.");
            lines.add(UltraStyle.DIM + "Removed rivalries appear here.");
            return lines;
        }
        lines.add(UltraStyle.HEADER + "Previous Rivals");
        List<Map.Entry<String, RivalLink>> entries = new ArrayList<>(me.pastRivals.entrySet());
        entries.sort(Comparator.comparingDouble((Map.Entry<String, RivalLink> e) ->
                e.getValue() == null ? 0.0 : e.getValue().points).reversed());
        for (Map.Entry<String, RivalLink> e : entries) {
            RivalLink link = e.getValue();
            if (link == null) {
                continue;
            }
            RivalConstants.RpTier tier = RivalConstants.tierFor(link.points);
            String name = link.name == null || link.name.isBlank() ? e.getKey() : link.name;
            lines.add(UltraStyle.VALUE + name + " " + UltraStyle.DIM + "[PAST] " + UltraStyle.MARK + tier.color() + tier.name()
                    + " " + UltraStyle.LABEL + "RP " + UltraStyle.VALUE + (int) link.points
                    + " " + UltraStyle.DIM + "· " + UltraStyle.GREEN + link.wins + UltraStyle.LABEL + "/" + UltraStyle.DANGER + link.losses + UltraStyle.LABEL + "/" + UltraStyle.YELLOW + link.draws);
        }
        return lines;
    }

    private static List<String> rivalCards(ServerPlayer player, boolean past) {
        List<String> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        RivalPlayerRecord me = RivalStore.get().ensurePlayer(player);
        if (me == null) {
            return out;
        }
        Map<String, RivalLink> map = past ? me.pastRivals : me.rivals;
        if (map == null || map.isEmpty()) {
            return out;
        }
        MinecraftServer server = player.m_20194_();
        List<Map.Entry<String, RivalLink>> entries = new ArrayList<>(map.entrySet());
        if (past) {
            entries.sort(Comparator.comparingDouble((Map.Entry<String, RivalLink> e) ->
                    e.getValue() == null ? 0.0 : e.getValue().points).reversed());
        } else {
            entries.sort(rivalListOrder());
        }
        for (Map.Entry<String, RivalLink> e : entries) {
            RivalLink link = e.getValue();
            if (link == null) {
                continue;
            }
            // Incoming Pending Declares are Pending Invites only.
            // Outgoing visible Declares stay on the list as Declared.
            if (!past) {
                RivalStatus cur = link.status();
                if (cur == RivalStatus.PENDING || cur == RivalStatus.NONE) {
                    continue;
                }
                if (!link.mutual && !link.declaredByMe) {
                    continue;
                }
            }
            String uuid = link.uuid == null || link.uuid.isBlank() ? e.getKey() : link.uuid;
            String name = link.name == null || link.name.isBlank() ? uuid : link.name;
            name = name.replace('\t', ' ').replace('\n', ' ');
            RivalStatus st = past ? RivalStatus.NONE : link.status();
            String status = past ? "PAST" : (st == null ? "?" : st.label());
            RivalConstants.RpTier tier = RivalConstants.tierFor(link.points);
            boolean online = false;
            if (server != null && uuid != null && !uuid.isBlank()) {
                try {
                    online = server.m_6846_().m_11259_(java.util.UUID.fromString(uuid)) != null;
                } catch (IllegalArgumentException ignored) {
                    online = false;
                }
            }
            out.add(String.join("\t",
                    nullToEmpty(uuid),
                    nullToEmpty(name),
                    nullToEmpty(status),
                    tier == null ? "?" : tier.name(),
                    String.valueOf((int) link.points),
                    String.valueOf(link.wins),
                    String.valueOf(link.losses),
                    String.valueOf(link.draws),
                    String.valueOf(link.deathLosses),
                    String.valueOf(link.deathWins),
                    online ? "1" : "0",
                    past ? "1" : "0",
                    String.valueOf(Math.max(0L, link.presenceMs)),
                    String.valueOf(Math.max(0L, link.lastBattleAt))));
        }
        return out;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * GUI / chat list order: Nemesis → Mutual → Declared → Silent, then RP desc within group.
     * Pending stays off the main list (Pending Invites page).
     */
    private static Comparator<Map.Entry<String, RivalLink>> rivalListOrder() {
        return Comparator
                .comparingInt((Map.Entry<String, RivalLink> e) -> statusListRank(e.getValue()))
                .thenComparing((Map.Entry<String, RivalLink> a, Map.Entry<String, RivalLink> b) ->
                        Double.compare(
                                b.getValue() == null ? 0.0 : b.getValue().points,
                                a.getValue() == null ? 0.0 : a.getValue().points));
    }

    private static int statusListRank(RivalLink link) {
        if (link == null) {
            return 90;
        }
        RivalStatus st = link.status();
        return switch (st) {
            case NEMESIS -> 0;
            case MUTUAL -> 1;
            case DECLARED -> 2;
            case UNKNOWN -> 3;
            case PENDING -> 4;
            case NONE -> 5;
        };
    }

    public static List<String> statsLines(ServerPlayer player) {
        return statsLines(player, null);
    }

    public static List<String> statsLines(ServerPlayer viewer, String targetName) {
        RivalStore store = RivalStore.get();
        RivalPlayerRecord me;
        if (targetName == null || targetName.isBlank()) {
            me = store.ensurePlayer(viewer);
        } else {
            me = findByName(store, targetName);
            if (me == null) {
                ServerPlayer online = findOnline(viewer == null ? null : viewer.m_20194_(), targetName);
                if (online != null) {
                    me = store.ensurePlayer(online);
                }
            }
            if (me == null) {
                List<String> miss = new ArrayList<>();
                miss.add(UltraStyle.DANGER + "No rivalry record for " + UltraStyle.VALUE + targetName);
                return miss;
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.HEADER + "Rival Stats " + UltraStyle.DIM + "— " + UltraStyle.VALUE + me.name);
        RivalConstants.RpTier tier = RivalConstants.tierFor(me.totalRp);
        lines.add(UltraStyle.LABEL + "Total RP " + UltraStyle.VALUE + (int) me.totalRp + " " + UltraStyle.DIM + "(" + UltraStyle.MARK + tier.color() + tier.name() + UltraStyle.DIM + ")");
        lines.add(UltraStyle.LABEL + "Title perk " + UltraStyle.YELLOW + (tier.perk() == null || tier.perk().isBlank() ? "None" : tier.perk()));
        lines.add(UltraStyle.LABEL + "Rival TP bonus " + UltraStyle.GREEN + "+" + Math.round(tier.tpMult() * 100.0) + "%");
        lines.add(UltraStyle.LABEL + "Record " + UltraStyle.GREEN + me.officialWins + UltraStyle.LABEL + "/" + UltraStyle.DANGER + me.officialLosses + UltraStyle.LABEL + "/" + UltraStyle.YELLOW + me.officialDraws
                + " " + UltraStyle.DIM + "(KO " + me.knockouts + ")");
        lines.add(UltraStyle.LABEL + "Streak " + UltraStyle.VALUE + me.currentWinStreak + " " + UltraStyle.DIM + "(best " + me.bestWinStreak + ")");
        lines.add(UltraStyle.LABEL + "Challenges " + UltraStyle.VALUE + me.challengesPlayed + " " + UltraStyle.LABEL + "Surpass " + UltraStyle.VALUE + me.surpassAwards);
        lines.add(UltraStyle.LABEL + "Career dmg " + UltraStyle.VALUE + (int) me.careerDamageDealt
                + " " + UltraStyle.DIM + "| combo " + UltraStyle.VALUE + me.careerHighestCombo
                + " " + UltraStyle.DIM + "| hits " + UltraStyle.VALUE + me.careerHits);
        lines.add(UltraStyle.LABEL + "Mutual slots " + UltraStyle.VALUE + me.countMutual() + UltraStyle.DIM + "/" + UltraStyle.VALUE + RivalConstants.MAX_MUTUAL_RIVALS);
        lines.add(UltraStyle.LABEL + "TP chat " + UltraStyle.VALUE + (me.tpMessages ? "ON" : "OFF") + " " + UltraStyle.DIM + "(/rival tpmsg)");
        ServerPlayer combatSrc = viewer;
        if (targetName != null && !targetName.isBlank() && viewer != null) {
            ServerPlayer online = findOnline(viewer.m_20194_(), targetName);
            if (online != null) {
                combatSrc = online;
            }
        }
        if (combatSrc != null) {
            lines.add(UltraStyle.GOLD + "Combat (after Overhaul)");
            lines.addAll(LmOverhaulScaledCombat.compactLines(DmzProgression.stats(combatSrc)));
        }
        return lines;
    }

    public static String setTpMsg(ServerPlayer player, Boolean on) {
        RivalPlayerRecord me = RivalStore.get().ensurePlayer(player);
        if (on == null) {
            return "§eRival TP chat: " + (me.tpMessages ? "ON" : "OFF");
        }
        me.tpMessages = on;
        RivalStore.get().markDirty();
        return "§eRival TP chat " + (on ? "ON" : "OFF");
    }

    public static List<String> topLines(int limit) {
        return topLines("rp", limit);
    }

    public static List<String> topLines(String category, int limit) {
        String cat = category == null || category.isBlank() ? "rp" : category.trim().toLowerCase();
        List<String> lines = new ArrayList<>();
        String label = switch (cat) {
            case "wins", "win" -> "Wins";
            case "streak", "beststreak" -> "Streak";
            case "damage", "dmg" -> "Damage";
            case "combo" -> "Combo";
            case "hit", "hits" -> "Hits";
            case "battles", "battle", "challenges" -> "Battles";
            default -> "RP";
        };
        lines.add("rp".equals(cat) ? UltraStyle.HEADER + "Rival Leaderboard" : UltraStyle.HEADER + "Rival " + label + " Leaderboard");
        int i = 1;
        for (RivalPlayerRecord rec : RivalStore.get().topBy(limit, cat)) {
            String value = switch (cat) {
                case "wins", "win" -> String.valueOf(rec.officialWins);
                case "streak", "beststreak" -> String.valueOf(rec.bestWinStreak);
                case "damage", "dmg" -> String.valueOf((int) rec.careerDamageDealt);
                case "combo" -> String.valueOf(rec.careerHighestCombo);
                case "hit", "hits" -> String.valueOf(rec.careerHits);
                case "battles", "battle", "challenges" -> String.valueOf(rec.challengesPlayed);
                default -> {
                    RivalConstants.RpTier tier = RivalConstants.tierFor(rec.totalRp);
                    yield (int) rec.totalRp + " " + UltraStyle.DIM + "(" + UltraStyle.MARK + tier.color() + tier.name() + UltraStyle.DIM + ")";
                }
            };
            lines.add(UltraStyle.YELLOW + "#" + i + " " + UltraStyle.VALUE + rec.name + " " + UltraStyle.LABEL + value);
            i++;
        }
        if (i == 1) {
            lines.add(UltraStyle.LABEL + "No rivalry data yet.");
        }
        return lines;
    }

    private static void archiveRivalLink(RivalPlayerRecord owner, String rivalUuid, RivalLink link) {
        if (owner == null || rivalUuid == null || link == null) {
            return;
        }
        if (owner.pastRivals == null) {
            owner.pastRivals = new ConcurrentHashMap<>();
        }
        RivalLink snap = new RivalLink();
        snap.uuid = link.uuid;
        snap.name = link.name;
        snap.points = link.points;
        snap.wins = link.wins;
        snap.losses = link.losses;
        snap.draws = link.draws;
        snap.deathLosses = link.deathLosses;
        snap.deathWins = link.deathWins;
        snap.presenceMs = link.presenceMs;
        snap.createdAt = link.createdAt;
        snap.firstMetAt = link.firstMetAt;
        snap.mutualSince = link.mutualSince;
        snap.lastBattleAt = link.lastBattleAt;
        snap.visibleDeclare = link.visibleDeclare;
        snap.provingGrounds = link.provingGrounds;
        owner.pastRivals.put(rivalUuid, snap);
    }

    private static void restorePastIfAny(RivalPlayerRecord owner, RivalPlayerRecord target, RivalLink link) {
        if (owner == null || target == null || link == null || owner.pastRivals == null) {
            return;
        }
        RivalLink past = owner.pastRivals.remove(target.uuid);
        if (past == null) {
            return;
        }
        link.points = Math.max(link.points, past.points);
        link.wins = Math.max(link.wins, past.wins);
        link.losses = Math.max(link.losses, past.losses);
        link.draws = Math.max(link.draws, past.draws);
        link.deathLosses = Math.max(link.deathLosses, past.deathLosses);
        link.deathWins = Math.max(link.deathWins, past.deathWins);
        link.presenceMs = Math.max(link.presenceMs, past.presenceMs);
        if (past.createdAt > 0 && (link.createdAt <= 0 || past.createdAt < link.createdAt)) {
            link.createdAt = past.createdAt;
        }
        if (past.firstMetAt > 0 && (link.firstMetAt <= 0 || past.firstMetAt < link.firstMetAt)) {
            link.firstMetAt = past.firstMetAt;
        }
        if (past.mutualSince > 0) {
            link.mutualSince = past.mutualSince;
        }
        if (past.lastBattleAt > link.lastBattleAt) {
            link.lastBattleAt = past.lastBattleAt;
        }
        if (link.provingGrounds == null && past.provingGrounds != null) {
            link.provingGrounds = past.provingGrounds;
        }
        if (past.visibleDeclare) {
            link.visibleDeclare = true;
        }
    }

    /** Both sides silently rivaled each other (Declared, not Mutual). */
    private static boolean isReciprocatedSilent(RivalLink link) {
        return link != null
                && link.declaredByMe
                && link.declaredByThem
                && !link.mutual;
    }

    /**
     * Crossed silent rivals → Declared for both + Pending Mutual confirm for both.
     * Mutual requires Accept from both sides.
     */
    private static void promoteDeclared(
            ServerPlayer actor,
            ServerPlayer target,
            RivalPlayerRecord me,
            RivalPlayerRecord them,
            RivalLink myLink,
            RivalLink theirLink,
            long now
    ) {
        long expire = now + RivalConstants.REQUEST_EXPIRE_MS;

        myLink.declaredByMe = true;
        myLink.declaredByThem = true;
        myLink.inviteSent = false;
        myLink.inviteReceived = true;
        myLink.acceptedMutualOffer = false;
        myLink.pendingExpireAt = expire;
        myLink.mutual = false;
        myLink.touch(now);

        theirLink.declaredByMe = true;
        theirLink.declaredByThem = true;
        theirLink.inviteSent = false;
        theirLink.inviteReceived = true;
        theirLink.acceptedMutualOffer = false;
        theirLink.pendingExpireAt = expire;
        theirLink.mutual = false;
        theirLink.touch(now);

        me.declarationsSent++;

        RivalStore store = RivalStore.get();
        RivalStore.DeclareRequest aToB = new RivalStore.DeclareRequest();
        aToB.fromUuid = me.uuid;
        aToB.fromName = me.name;
        aToB.toUuid = them.uuid;
        aToB.toName = them.name;
        aToB.createdAt = now;
        aToB.expiresAt = expire;
        store.declareRequests.put(me.uuid + ">" + them.uuid, aToB);
        RivalStore.DeclareRequest bToA = new RivalStore.DeclareRequest();
        bToA.fromUuid = them.uuid;
        bToA.fromName = them.name;
        bToA.toUuid = me.uuid;
        bToA.toName = me.name;
        bToA.createdAt = now;
        bToA.expiresAt = expire;
        store.declareRequests.put(them.uuid + ">" + me.uuid, bToA);

        // Actor sees the returned line on the Ultra page (or command chat). Target has no menu.
        DmzRewards.msg(target, LmChat.card(
                "Rival Declared",
                "/rival",
                "→ Rival → Accept Mutual",
                "§7Rival   §e" + me.name,
                "§8You both silently rivaled each other.",
                "§8Both must Accept to become Mutual."));
    }

    private static void promoteMutual(
            RivalPlayerRecord me,
            RivalPlayerRecord them,
            RivalLink myLink,
            RivalLink theirLink,
            long now
    ) {
        // Accepter already has a free slot (or just replaced one). Other side may auto-drop oldest.
        ensureMutualRoom(them, me.uuid);
        myLink.mutual = true;
        theirLink.mutual = true;
        myLink.declaredByMe = true;
        myLink.declaredByThem = true;
        theirLink.declaredByMe = true;
        theirLink.declaredByThem = true;
        myLink.inviteSent = false;
        myLink.inviteReceived = false;
        theirLink.inviteSent = false;
        theirLink.inviteReceived = false;
        myLink.acceptedMutualOffer = false;
        theirLink.acceptedMutualOffer = false;
        myLink.pendingExpireAt = 0L;
        theirLink.pendingExpireAt = 0L;
        RivalStore.get().declareRequests.remove(me.uuid + ">" + them.uuid);
        RivalStore.get().declareRequests.remove(them.uuid + ">" + me.uuid);
        if (myLink.mutualSince <= 0L) {
            myLink.mutualSince = now;
        }
        if (theirLink.mutualSince <= 0L) {
            theirLink.mutualSince = now;
        }
    }

    private static void ensureMutualRoom(RivalPlayerRecord record, String excludeUuid) {
        while (record.countMutual() >= RivalConstants.MAX_MUTUAL_RIVALS) {
            String oldest = null;
            long oldestAt = Long.MAX_VALUE;
            for (Map.Entry<String, RivalLink> e : record.rivals.entrySet()) {
                if (e.getKey().equals(excludeUuid)) {
                    continue;
                }
                RivalLink link = e.getValue();
                if (link == null || !link.mutual) {
                    continue;
                }
                long at = link.mutualSince > 0L ? link.mutualSince : link.createdAt;
                if (at < oldestAt) {
                    oldestAt = at;
                    oldest = e.getKey();
                }
            }
            if (oldest == null) {
                break;
            }
            RivalLink old = record.rivals.get(oldest);
            if (old != null) {
                old.mutual = false;
                old.isNemesis = false;
            }
            RivalPlayerRecord other = RivalStore.get().get(oldest);
            if (other != null) {
                RivalLink back = other.rivals.get(record.uuid);
                if (back != null) {
                    back.mutual = false;
                    back.isNemesis = false;
                }
            }
        }
    }

    private static RivalPlayerRecord findByName(RivalStore store, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String want = name.trim();
        if (want.regionMatches(true, 0, "uuid:", 0, 5)) {
            RivalPlayerRecord byId = store.get(want.substring(5).trim());
            if (byId != null) {
                return byId;
            }
            want = want.substring(5).trim();
        }
        RivalPlayerRecord byUuid = store.get(want);
        if (byUuid != null) {
            return byUuid;
        }
        String lower = want.toLowerCase();
        for (RivalPlayerRecord rec : store.players.values()) {
            if (rec.name != null && rec.name.toLowerCase().equals(lower)) {
                return rec;
            }
        }
        return null;
    }

    private static ServerPlayer onlineByUuid(MinecraftServer server, String uuid) {
        if (server == null || uuid == null) {
            return null;
        }
        try {
            return server.m_6846_().m_11259_(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static ServerPlayer findOnline(MinecraftServer server, String name) {
        if (server == null || name == null || name.isBlank()) {
            return null;
        }
        ServerPlayer exact = server.m_6846_().m_11255_(name);
        if (exact != null) {
            return exact;
        }
        String want = name.trim().toLowerCase();
        for (ServerPlayer p : server.m_6846_().m_11314_()) {
            if (p.m_7755_().getString().toLowerCase().equals(want)) {
                return p;
            }
        }
        return null;
    }
}
