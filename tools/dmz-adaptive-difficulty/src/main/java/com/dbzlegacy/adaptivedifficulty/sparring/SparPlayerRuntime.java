package com.dbzlegacy.adaptivedifficulty.sparring;

import java.util.UUID;

/** Per-player in-memory sparring session runtime. */
public final class SparPlayerRuntime {
    public UUID partner;
    public boolean active;
    public long startAt;
    public long graceUntil;
    public String graceReason = "";
    public boolean graceWarned;
    public long restartCooldownUntil;

    public String lastOutPartner = "";
    public long lastOutAt;
    public String lastOutKind = "melee";
    /**
     * Real damage exchanges only (not ki-charge / clash holds). Used to gate drip TP
     * so idle fighters stop earning while the session is still held open.
     */
    public String lastCombatOutPartner = "";
    public long lastCombatOutAt;
    public String lastCombatOutKind = "melee";
    public String lastInPartner = "";
    public long lastInAt;
    public long lastKiOutAt;
    public long lastLaserOutAt;
    public long clashUntil;
    /** Activity hold while charging / clash (does not fake combat for drip TP). */
    public long holdUntil;
    /** Extra linger after ki charge ends so the shot can fire before AFK ends the spar. */
    public long chargingUntil;

    public float pendingSampleHp;
    public UUID pendingAttacker;
    public boolean pendingKi;
    public String pendingKiKind = "";
    public long pendingUntil;

    public double moveX;
    public double moveY;
    public double moveZ;
    public long moveValidUntil;
    /** When {@link #moveValidUntil} was last refreshed. */
    public long moveStampAt;
    public long heavyMotionUntil;
    /** Friendly Fist knockdown heal latch (script spar.ff.kdHealed). */
    public boolean ffKdHealed;

    public int combo;
    public long comboUntil;
    public long comboStampAt;
    public int momentumTier;
    public long momentumUntil;
    public long momentumStampAt;

    /**
     * 4.7.17 stun / clash pause. {@code pairFrozenAt}: when either fighter became stunned or clashing
     * (hit, movement and grace windows stop). {@code hitPauseMs}: paused time credited to the hit
     * stamped at {@code hitPauseAnchor} (a new hit drops it). {@code selfFrozenAt}: when this fighter
     * became stunned or clashing (own combo and momentum stop).
     */
    public long pairFrozenAt;
    public long hitPauseMs;
    public long hitPauseAnchor;
    public long selfFrozenAt;
    /** Any clash seen last pulse (post-clash hold on the edge). */
    public boolean wasClashing;
    /** Both fighters were in a strike clash last pulse. */
    public boolean inStrikeClash;
    /** After a strike clash ends: watch for the loser's stun until this time. */
    public long strikeResultUntil;
    public int clashWins;
    public int clashLosses;
    public int clashTies;

    public double sessionTp;
    public double sessionMelee;
    public double sessionKi;
    public double sessionDmg;
    public double sessionTaken;
    public int sessionBlocks;
    public long sessionClashMs;
    public int sessionKb;
    public int sessionMaxCombo;
    public int sessionMaxMom;
    public boolean sessionPerfect;

    public double styleMelee;
    public double styleKi;
    public double styleBeam;
    public double styleBlock;
    public double styleMove;

    public double tpPending;
    public double tpPendingMelee;
    public double tpPendingKi;
    public double tpPendingClash;
    public long tpMsgNext;
    public long clashNext;
    public long releaseCtrlNext;
    public long messageNext;

    public void resetSession() {
        partner = null;
        active = false;
        startAt = 0L;
        graceUntil = 0L;
        graceReason = "";
        graceWarned = false;
        combo = 0;
        comboUntil = 0L;
        momentumTier = 0;
        momentumUntil = 0L;
        comboStampAt = 0L;
        momentumStampAt = 0L;
        pairFrozenAt = 0L;
        hitPauseMs = 0L;
        hitPauseAnchor = 0L;
        selfFrozenAt = 0L;
        wasClashing = false;
        inStrikeClash = false;
        strikeResultUntil = 0L;
        clashWins = 0;
        clashLosses = 0;
        clashTies = 0;
        sessionTp = 0;
        sessionMelee = 0;
        sessionKi = 0;
        sessionDmg = 0;
        sessionTaken = 0;
        sessionBlocks = 0;
        sessionClashMs = 0;
        sessionKb = 0;
        sessionMaxCombo = 0;
        sessionMaxMom = 0;
        sessionPerfect = false;
        styleMelee = 0;
        styleKi = 0;
        styleBeam = 0;
        styleBlock = 0;
        styleMove = 0;
        moveX = 0;
        moveY = 0;
        moveZ = 0;
        moveValidUntil = 0L;
        moveStampAt = 0L;
        heavyMotionUntil = 0L;
        ffKdHealed = false;
        tpPending = 0;
        tpPendingMelee = 0;
        tpPendingKi = 0;
        tpPendingClash = 0;
        clashUntil = 0L;
        holdUntil = 0L;
        chargingUntil = 0L;
        lastCombatOutAt = 0L;
        lastCombatOutPartner = "";
        lastCombatOutKind = "melee";
        pendingUntil = 0L;
        pendingAttacker = null;
    }
}
