package com.dbzlegacy.adaptivedifficulty.gui.ultra;

/**
 * Shared purchase-state machine for LM menus: the same ten states DMZ Ultra 0.5.3's forms hub
 * uses ({@code FormsRules.State}), computed server-side so a list shows why each entry can or
 * cannot be bought. Every view has a one-line reason, and only {@link State#tappable()} states
 * get a button: a state that says "can't" never sits behind a live click.
 *
 * <p>The difficulty tier grid uses ACTIVE / OWNED / BUYABLE / SHORT / LOCKED (tiers have no
 * mastery, quest, training or unsold gates). Prestige shop sections and race/class/saga lists
 * are meant to reuse this helper (follow-up).
 */
public final class PurchaseState {
    /** {@link Inputs#price} sentinel: unlocked by training, not sold. */
    public static final long PRICE_TRAINING = Long.MAX_VALUE - 1;
    /** {@link Inputs#price} sentinel: unlocked by a quest, not sold. */
    public static final long PRICE_QUEST = -1L;
    /** {@link Inputs#price} sentinel: not for sale here. */
    public static final long PRICE_UNSOLD = Long.MAX_VALUE;

    private PurchaseState() {}

    public enum State {
        /** Owned and in use right now. */
        ACTIVE,
        /** Owned; tapping uses or switches to it. */
        OWNED,
        /** Can be bought now. */
        BUYABLE,
        /** Can be bought, but the player cannot pay yet. */
        SHORT,
        /** A requirement is not met. */
        LOCKED,
        /** Needs a mastered or stacked entry first. */
        MASTER,
        /** Unlocked by a quest, not sold. */
        QUEST,
        /** Unlocked by training, not sold. */
        TRAINING,
        /** Not for sale here. */
        UNSOLD,
        /** A later step: the one before it comes first. */
        LATER;

        /** True when a tap can succeed: only these states get a button. */
        public boolean tappable() {
            return this == OWNED || this == BUYABLE;
        }
    }

    /**
     * What the caller knows about one entry. {@code price} and {@code funds} share a unit (the
     * caller's currency); {@code price} may be a sentinel ({@link #PRICE_QUEST} and friends).
     */
    public record Inputs(
            boolean owned,
            boolean active,
            boolean next,
            boolean stackFirst,
            boolean masterOnly,
            boolean locked,
            long price,
            long funds) {}

    /** State plus the reason line the menu shows next to it. */
    public record View(State state, String reason) {
        /** {@code SHORT - need 3× Iron Ancient more}. */
        public String line() {
            return state.name() + " - " + reason;
        }
    }

    /** The state machine, in the same order as DMZ Ultra's {@code FormsRules.state}. */
    public static State state(Inputs in) {
        if (in.owned()) {
            return in.active() ? State.ACTIVE : State.OWNED;
        }
        if (!in.next()) {
            return in.locked() ? State.LOCKED : State.LATER;
        }
        if (in.locked()) {
            return State.LOCKED;
        }
        if (in.masterOnly() || in.stackFirst()) {
            return State.MASTER;
        }
        if (in.price() == PRICE_TRAINING) {
            return State.TRAINING;
        }
        if (in.price() == PRICE_QUEST) {
            return State.QUEST;
        }
        if (in.price() == PRICE_UNSOLD || in.price() < 0) {
            return State.UNSOLD;
        }
        return in.funds() >= in.price() ? State.BUYABLE : State.SHORT;
    }

    /** Offline check of every branch; failures go to {@code broken}. */
    static void selfTest(java.util.List<String> broken) {
        Object[][] cases = {
            {new Inputs(true, true, true, false, false, false, 5, 0), State.ACTIVE},
            {new Inputs(true, false, false, false, false, true, 5, 0), State.OWNED},
            {new Inputs(false, false, false, false, false, true, 5, 9), State.LOCKED},
            {new Inputs(false, false, false, false, false, false, 5, 9), State.LATER},
            {new Inputs(false, false, true, false, false, true, 5, 9), State.LOCKED},
            {new Inputs(false, false, true, true, false, false, 5, 9), State.MASTER},
            {new Inputs(false, false, true, false, true, false, 5, 9), State.MASTER},
            {new Inputs(false, false, true, false, false, false, PRICE_TRAINING, 9), State.TRAINING},
            {new Inputs(false, false, true, false, false, false, PRICE_QUEST, 9), State.QUEST},
            {new Inputs(false, false, true, false, false, false, PRICE_UNSOLD, 9), State.UNSOLD},
            {new Inputs(false, false, true, false, false, false, -7, 9), State.UNSOLD},
            {new Inputs(false, false, true, false, false, false, 9, 9), State.BUYABLE},
            {new Inputs(false, false, true, false, false, false, 10, 9), State.SHORT},
        };
        for (Object[] c : cases) {
            State got = state((Inputs) c[0]);
            if (got != c[1]) {
                broken.add("PurchaseState " + c[0] + " should be " + c[1] + ", got " + got);
            }
        }
        for (State s : State.values()) {
            if (s.tappable() != (s == State.OWNED || s == State.BUYABLE)) {
                broken.add("PurchaseState " + s + " tappable() is wrong");
            }
        }
    }
}
