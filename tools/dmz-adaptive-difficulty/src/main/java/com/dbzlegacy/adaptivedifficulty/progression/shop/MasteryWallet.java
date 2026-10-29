package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore;
import com.dbzlegacy.adaptivedifficulty.gui.GuiClickConfirm;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.progression.DmzSkillUtil;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.util.DmzLang;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.config.SkillsConfig;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.FormMasteries;
import com.dragonminez.common.util.TransformationsHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/**
 * Mastery points wallet (Prestige shop, Shop tab). Favor buys points at a fixed rate, points add
 * mastery to a form the player has unlocked. There is no direct favor-to-mastery path and nothing
 * is refunded.
 *
 * <p>The balance is one top-level record ({@link #KEY}) in the player's LM-owned file
 * ({@link LmPlayerStore}), never Forge persistent data, StatsData or BonusStats, so DMZ resets,
 * race changes and player-data wipes leave it alone.
 *
 * <p>The form picker is built live on every open from the gates DMZ's transformation radial uses:
 * the radial's group walk (race groups whose skill the race may use, then stack groups) and
 * {@code TransformationsHelper.getUnlockedForms} / {@code getUnlockedStackForms} for each group
 * (skill access + mastery requisite). The radial's free-transform selectability filter is not
 * applied: investing in a form does not need free-transform mastery.
 */
public final class MasteryWallet {
    public static final String KEY = "lm_mastery_points";
    public static final int POINTS_PER_FAVOR = 10;
    public static final int MASTERY_PER_POINT = 50;
    /** Favor packs on the wallet page. */
    public static final int[] FAVOR_PACKS = {1, 2, 5, 10};
    /** Point amounts offered on a form page (plus "all that fit"). */
    public static final int[] APPLY_AMOUNTS = {1, 5, 10, 25, 50, 100};
    /** Line kinds in radial order: superforms, then more forms, then stack forms. */
    public static final int KIND_SUPER = 0;
    public static final int KIND_MORE = 1;
    public static final int KIND_STACK = 2;

    private MasteryWallet() {}

    /** One form the player has unlocked, with its live mastery. */
    public static final class Form {
        public final String name;
        public final String label;
        public final double current;
        public final double cap;

        public Form(String name, String label, double current, double cap) {
            this.name = name;
            this.label = label;
            this.current = current;
            this.cap = cap;
        }

        public double headroom() {
            return Math.max(0.0, cap - current);
        }

        public int maxPoints() {
            return maxPointsFor(current, cap);
        }
    }

    /** One form line (a DMZ form group) with the unlocked forms in it. */
    public static final class Line {
        public final String group;
        public final String label;
        public final boolean stack;
        public final int kind;
        public final List<Form> forms;

        public Line(String group, String label, boolean stack, String formType, List<Form> forms) {
            this.group = group;
            this.label = label;
            this.stack = stack;
            this.kind = kindOf(formType, stack);
            this.forms = forms == null ? List.of() : forms;
        }

        /** Page / action token: {@code s:<group>} or {@code n:<group>}. */
        public String token() {
            return (stack ? "s:" : "n:") + group;
        }

        public Form form(String name) {
            for (Form f : forms) {
                if (f.name.equalsIgnoreCase(name)) {
                    return f;
                }
            }
            return null;
        }
    }

    // ── Pure rules (self-test covers these) ───────────────────────────

    public static int pointsForFavor(int favor) {
        return Math.max(0, favor) * POINTS_PER_FAVOR;
    }

    public static boolean isPack(int favor) {
        for (int p : FAVOR_PACKS) {
            if (p == favor) {
                return true;
            }
        }
        return false;
    }

    /** Whole points that fit under the cap: headroom / 50, rounded down. Under 50 left is 0. */
    public static int maxPointsFor(double current, double cap) {
        double room = cap - current;
        if (!(room > 0.0) || Double.isNaN(room)) {
            return 0;
        }
        return (int) Math.floor(room / MASTERY_PER_POINT + 1.0E-9);
    }

    /** Points a player can apply right now: the cap limit and their balance, whichever is lower. */
    public static int applicable(double current, double cap, int balance) {
        return Math.max(0, Math.min(Math.max(0, balance), maxPointsFor(current, cap)));
    }

    public static double resulting(double current, int points, double cap) {
        return Math.min(cap, current + (double) Math.max(0, points) * MASTERY_PER_POINT);
    }

    /** Radial grouping: super / legendary / android types first, then other race forms, then stacks. */
    public static int kindOf(String formType, boolean stack) {
        if (stack) {
            return KIND_STACK;
        }
        String t = formType == null ? "" : formType.toLowerCase(Locale.ROOT);
        return t.contains("super") || t.contains("legendary") || t.contains("android") ? KIND_SUPER : KIND_MORE;
    }

    /** Drop lines with nothing unlocked; order like the radial (kind, then name A-Z). */
    public static List<Line> ordered(List<Line> raw) {
        List<Line> out = new ArrayList<>();
        if (raw != null) {
            for (Line l : raw) {
                if (l != null && !l.forms.isEmpty()) {
                    out.add(l);
                }
            }
        }
        out.sort(Comparator.<Line>comparingInt(l -> l.kind)
                .thenComparing(l -> l.label, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public static String buyPrompt(int favor) {
        return "Buy " + pointsForFavor(favor) + " mastery points for " + favor + " favor? "
                + "Favor spent is not refunded. Click again within 10 seconds.";
    }

    public static String applyPrompt(int points, String lineLabel, Form form) {
        return "Apply " + points + " mastery point" + (points == 1 ? "" : "s") + " to " + formName(lineLabel, form)
                + "? Mastery goes from " + whole(form.current) + " to "
                + whole(resulting(form.current, points, form.cap)) + " (cap " + whole(form.cap) + "). "
                + "Applied mastery is not refunded. Click again within 10 seconds.";
    }

    public static String formName(String lineLabel, Form form) {
        return lineLabel == null || lineLabel.isBlank() ? form.label : form.label + " (" + lineLabel + ")";
    }

    public static String whole(double v) {
        return DmzRewards.formatWhole(v);
    }

    // ── Balance (LM-owned player file only) ───────────────────────────

    public static int balance(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        long v = LmPlayerStore.getLong(player, KEY, 0L);
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, v));
    }

    private static void setBalance(ServerPlayer player, int points) {
        LmPlayerStore.putLong(player, KEY, Math.max(0, points));
        LmPlayerStore.flush(player.m_20148_());
    }

    public static boolean open() {
        return PrestigeShopConfig.get().masteryWallet.enabled;
    }

    // ── Live picker ───────────────────────────────────────────────────

    /** The player's unlocked form lines, read fresh from their current DMZ state. */
    public static List<Line> lines(ServerPlayer player) {
        List<Line> raw = new ArrayList<>();
        Character ch = character(player);
        StatsData data = player == null ? null : DmzProgression.stats(player);
        if (ch == null || data == null) {
            return raw;
        }
        String race;
        try {
            race = ch.getRaceName();
        } catch (Throwable t) {
            race = null;
        }
        if (race == null || race.isBlank()) {
            return raw;
        }
        String raceKey = race.toLowerCase(Locale.ROOT);
        SkillsConfig skills = null;
        try {
            skills = ConfigManager.getSkillsConfig();
        } catch (Throwable ignored) {
        }
        Map<String, FormConfig> groups = null;
        try {
            groups = ConfigManager.getAllFormsForRace(race);
        } catch (Throwable ignored) {
        }
        if (groups != null) {
            FormMasteries masteries = ch.getFormMasteries();
            for (String group : groups.keySet()) {
                try {
                    FormConfig cfg = ConfigManager.getFormGroup(race, group);
                    if (cfg == null) {
                        continue;
                    }
                    String type = cfg.getFormType() == null ? "" : cfg.getFormType().toLowerCase(Locale.ROOT);
                    if (skills != null && !skills.isSkillAllowedForRace(TransformationsHelper.getSkillNameForType(type), race)) {
                        continue;
                    }
                    List<Form> forms = new ArrayList<>();
                    for (FormConfig.FormData fd : TransformationsHelper.getUnlockedForms(data, race, group)) {
                        if (fd == null || fd.getName() == null) {
                            continue;
                        }
                        String label = DmzLang.text("race.dragonminez." + raceKey + ".form." + group + "." + fd.getName(),
                                DmzLang.titleCase(fd.getName()));
                        forms.add(new Form(fd.getName(), label, mastery(masteries, group, fd.getName()), cap(fd)));
                    }
                    String label = DmzLang.text("race.dragonminez." + raceKey + ".group." + group, DmzLang.titleCase(group));
                    raw.add(new Line(group, label, false, type, forms));
                } catch (Throwable ignored) {
                }
            }
        }
        Map<String, FormConfig> stacks = null;
        try {
            stacks = ConfigManager.getAllStackForms();
        } catch (Throwable ignored) {
        }
        if (stacks != null) {
            FormMasteries masteries = ch.getStackFormMasteries();
            for (String group : stacks.keySet()) {
                try {
                    if (skills != null && !skills.isSkillAllowedForRace(group, race)) {
                        continue;
                    }
                    List<Form> forms = new ArrayList<>();
                    for (FormConfig.FormData fd : TransformationsHelper.getUnlockedStackForms(data, group)) {
                        if (fd == null || fd.getName() == null) {
                            continue;
                        }
                        String label = DmzLang.text("race.dragonminez.stack.form." + group + "." + fd.getName(),
                                DmzLang.titleCase(fd.getName()));
                        forms.add(new Form(fd.getName(), label, mastery(masteries, group, fd.getName()), cap(fd)));
                    }
                    String label = DmzLang.text("race.dragonminez.stack.group." + group, DmzLang.titleCase(group));
                    raw.add(new Line(group, label, true, "", forms));
                } catch (Throwable ignored) {
                }
            }
        }
        return ordered(raw);
    }

    /** The line for a {@code s:<group>} / {@code n:<group>} token, from the live picker. */
    public static Line line(ServerPlayer player, String token) {
        if (token == null) {
            return null;
        }
        for (Line l : lines(player)) {
            if (l.token().equalsIgnoreCase(token.trim())) {
                return l;
            }
        }
        return null;
    }

    private static Character character(ServerPlayer player) {
        try {
            StatsData data = player == null ? null : DmzProgression.stats(player);
            return data == null ? null : data.getCharacter();
        } catch (Throwable t) {
            return null;
        }
    }

    private static double mastery(FormMasteries masteries, String group, String form) {
        try {
            return masteries == null ? 0.0 : masteries.getMastery(group, form);
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /** Same default as YardratProgression: 100 when the form sets no max. */
    private static double cap(FormConfig.FormData fd) {
        Double max = fd.getMaxMastery();
        return max != null && max > 0.0 ? max : 100.0;
    }

    // ── Shop actions ──────────────────────────────────────────────────

    /** {@code do:mastery_buy:<favor>} - one of the favor packs, with the shop confirm. */
    public static String buy(ServerPlayer player, String arg) {
        if (player == null) {
            return UltraStyle.dangerLine("Players only.");
        }
        int favor;
        try {
            favor = Integer.parseInt(arg == null ? "" : arg.trim());
        } catch (NumberFormatException e) {
            favor = -1;
        }
        if (!isPack(favor)) {
            return UltraStyle.dangerLine("That point pack is not on sale. Pick one of the packs on the Mastery wallet page.");
        }
        if (!open()) {
            return UltraStyle.dangerLine("The mastery wallet is not selling points right now. Points you already have still work.");
        }
        int have = PrestigePointsSystem.getPoints(player);
        if (have < favor) {
            return UltraStyle.dangerLine("Need " + favor + " favor (have " + have + ").");
        }
        if (!ProgressionData.storedWritable(player)) {
            return UltraStyle.dangerLine("Could not save prestige data - try relogging, then buy again.");
        }
        int balance = balance(player);
        if (!GuiClickConfirm.confirmed(player.m_20148_(), "mwallet-buy:" + favor + ":" + balance)) {
            return UltraStyle.noticeLine(buyPrompt(favor));
        }
        int gained = pointsForFavor(favor);
        PrestigePointsSystem.setPoints(player, have - favor);
        setBalance(player, balance + gained);
        SystemTelemetry.log("prestige_points", "buy_mastery_points", player, null, Map.of(
                "favor", favor, "points", gained, "balance", balance(player),
                "favor_left", PrestigePointsSystem.getPoints(player)));
        return UltraStyle.color("a", "Bought " + gained + " mastery points (-" + favor + " favor).") + "\n"
                + UltraStyle.dim("Mastery points: " + balance(player) + ". Favor left: "
                        + PrestigePointsSystem.getPoints(player) + ".");
    }

    /** {@code do:mastery_apply:<s|n>:<group>:<form>:<points>} - whole points, confirm first. */
    public static String apply(ServerPlayer player, String arg) {
        if (player == null) {
            return UltraStyle.dangerLine("Players only.");
        }
        String[] parts = arg == null ? new String[0] : arg.trim().split(":");
        if (parts.length < 4) {
            return UltraStyle.dangerLine("Pick a form first, then how many points to apply.");
        }
        int points;
        try {
            points = Integer.parseInt(parts[parts.length - 1].trim());
        } catch (NumberFormatException e) {
            points = 0;
        }
        String formName = parts[parts.length - 2];
        String token = String.join(":", java.util.Arrays.copyOfRange(parts, 0, parts.length - 2));
        Line line = line(player, token);
        Form form = line == null ? null : line.form(formName);
        if (form == null) {
            return UltraStyle.dangerLine("That form is not unlocked for you right now. Open Apply points again to pick one of your forms.");
        }
        String name = formName(line.label, form);
        if (points <= 0) {
            return UltraStyle.dangerLine("Pick how many points to apply with the buttons on the form page.");
        }
        int fit = form.maxPoints();
        if (fit <= 0) {
            return UltraStyle.dangerLine(form.current >= form.cap
                    ? name + " is already at its mastery cap, so it can't take points."
                    : name + " is less than " + MASTERY_PER_POINT + " mastery from its cap. Finish it by training.");
        }
        int balance = balance(player);
        if (balance <= 0) {
            return UltraStyle.dangerLine("You have no mastery points. Buy some with favor on the Mastery wallet page first.");
        }
        if (points > balance) {
            return UltraStyle.dangerLine("You have " + balance + " mastery points. Pick a smaller amount or buy more points.");
        }
        if (points > fit) {
            return UltraStyle.dangerLine(name + " can take at most " + fit + " points before its cap of "
                    + whole(form.cap) + ". Pick a smaller amount.");
        }
        String key = "mwallet-apply:" + line.token() + ":" + form.name + ":" + points + ":" + balance + ":" + whole(form.current);
        if (!GuiClickConfirm.confirmed(player.m_20148_(), key)) {
            return UltraStyle.noticeLine(applyPrompt(points, line.label, form));
        }
        Character ch = character(player);
        FormMasteries masteries = ch == null ? null : (line.stack ? ch.getStackFormMasteries() : ch.getFormMasteries());
        if (masteries == null) {
            return UltraStyle.dangerLine("Could not read your form mastery. Try again, or ask staff if it keeps happening.");
        }
        masteries.addMastery(line.group, form.name, (double) points * MASTERY_PER_POINT, form.cap);
        double after = mastery(masteries, line.group, form.name);
        setBalance(player, balance - points);
        DmzSkillUtil.sync(player);
        SystemTelemetry.log("prestige_points", "apply_mastery_points", player, null, Map.of(
                "group", line.group, "form", form.name, "stack", line.stack, "points", points,
                "mastery", after, "balance", balance(player)));
        return UltraStyle.color("a", "Applied " + points + " point" + (points == 1 ? "" : "s") + " to " + name
                        + ". Mastery is now " + whole(after) + " / " + whole(form.cap) + ".") + "\n"
                + UltraStyle.dim("Mastery points left: " + balance(player) + ".");
    }
}
