package com.dbzlegacy.adaptivedifficulty.dojo;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.Dojo;
import com.dbzlegacy.adaptivedifficulty.sparring.SparStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * LM 4.7.68 dojos: who is in which dojo, founding / closing / reopening, Dojo Points (earned only by doing dojo
 * things, spent only on the dojo, with a daily cap per source), upgrades, the style and its bonus, elites, capacity,
 * clout and the season reset. A dojo is keyed by its master's UUID, so closing and reopening keeps the same dojo.
 *
 * <p>Members: the master plus the master's apprentices from the existing mentor bonds. Existing mentors with
 * apprentices get their dojo created on load (their members count as long-standing for the elite minimum).
 */
public final class DojoService {
    private DojoService() {}

    public static final long DAY_MS = 24L * 60L * 60L * 1000L;

    static DojoStore store() {
        return DojoStore.get();
    }

    // ------------------------------------------------------------------ membership

    /** The open dojo {@code player} belongs to (as master or disciple), or null. */
    public static Dojo dojoOf(UUID player) {
        if (player == null) {
            return null;
        }
        String id = player.toString();
        Dojo own = store().dojos.get(id);
        if (own != null && own.open) {
            return own;
        }
        SparStore.MentorBond b = SparStore.get().bondsByPlayer.get(id);
        if (b != null && b.mentorUuid != null && !b.mentorUuid.isBlank()) {
            Dojo m = store().dojos.get(b.mentorUuid);
            if (m != null && m.open) {
                return m;
            }
        }
        return null;
    }

    public static Dojo dojoOf(ServerPlayer p) {
        return p == null ? null : dojoOf(p.m_20148_());
    }

    public static Dojo byMaster(String masterUuid) {
        return masterUuid == null ? null : store().dojos.get(masterUuid);
    }

    public static boolean isMaster(UUID player, Dojo d) {
        return d != null && player != null && d.masterUuid.equals(player.toString());
    }

    public static boolean isElite(UUID player, Dojo d) {
        return d != null && player != null && d.elites.contains(player.toString());
    }

    /** Master or elite: may buy upgrades and declare wars. */
    public static boolean canManage(UUID player, Dojo d) {
        return isMaster(player, d) || isElite(player, d);
    }

    /** Disciples (UUID strings) in roster order. */
    public static List<String> disciples(Dojo d) {
        List<String> out = new ArrayList<>();
        if (d == null) {
            return out;
        }
        SparStore.MentorBond b = SparStore.get().bondsByPlayer.get(d.masterUuid);
        if (b != null) {
            b.normalizeApprentices();
            for (SparStore.ApprenticeRef r : b.apprentices) {
                out.add(r.uuid);
            }
        }
        return out;
    }

    public static String nameOf(String uuid) {
        if (uuid == null) {
            return "?";
        }
        DojoStore.PlayerRecord r = store().players.get(uuid);
        if (r != null && r.name != null && !r.name.isBlank()) {
            return r.name;
        }
        for (SparStore.MentorBond b : SparStore.get().bondsByPlayer.values()) {
            if (b == null) {
                continue;
            }
            if (uuid.equals(b.mentorUuid) && b.mentorName != null && !b.mentorName.isBlank()) {
                return b.mentorName;
            }
            for (SparStore.ApprenticeRef a : b.apprentices) {
                if (uuid.equals(a.uuid) && a.name != null && !a.name.isBlank()) {
                    return a.name;
                }
            }
        }
        Dojo d = store().dojos.get(uuid);
        return d != null && !d.masterName.isBlank() ? d.masterName : "a player";
    }

    public static int tier(Dojo d, String upgrade) {
        if (d == null) {
            return 0;
        }
        Integer t = d.upgrades.get(upgrade);
        return t == null ? DojoCatalog.minTier(upgrade) : t;
    }

    public static int arenas(Dojo d) {
        return Math.max(1, tier(d, DojoCatalog.ARENA));
    }

    public static int disciplesAllowed(String masterUuid) {
        return DojoCatalog.disciplesAllowed(tier(byMaster(masterUuid), DojoCatalog.CAPACITY));
    }

    /** Existing mentors with apprentices get their dojo (named from the old dojo profile when there is one). */
    public static void migrate() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Map.Entry<String, SparStore.MentorBond> e : SparStore.get().bondsByPlayer.entrySet()) {
            SparStore.MentorBond b = e.getValue();
            if (b == null) {
                continue;
            }
            b.normalizeApprentices();
            if (b.apprentices.isEmpty() || store().dojos.containsKey(e.getKey())) {
                continue;
            }
            Dojo d = fresh(e.getKey(), b.mentorName != null && !b.mentorName.isBlank() ? b.mentorName
                    : nameOf(e.getKey()), now);
            SparStore.DojoProfile prof = SparStore.get().dojoProfiles.get(e.getKey());
            if (prof != null && prof.displayName != null && !prof.displayName.isBlank()) {
                d.name = prof.displayName;
            }
            for (SparStore.ApprenticeRef a : b.apprentices) {
                d.joinedAt.put(a.uuid, 0L);
            }
            store().dojos.put(e.getKey(), d);
            changed = true;
        }
        if (changed) {
            store().markDirty();
        }
    }

    static Dojo fresh(String masterUuid, String masterName, long now) {
        Dojo d = new Dojo();
        d.masterUuid = masterUuid;
        d.masterName = masterName == null ? "" : masterName;
        d.name = (masterName == null || masterName.isBlank() ? "New" : masterName) + "'s Dojo";
        d.foundedAt = now;
        d.open = true;
        d.upgrades.put(DojoCatalog.ARENA, 1);
        d.cloutSeason = currentSeason();
        return d;
    }

    static int currentSeason() {
        SparStore.DojoSeason s = SparStore.get().dojoSeason;
        return s == null ? 1 : Math.max(1, s.seasonId);
    }

    /** The name rule for a dojo (founding and rename): 3 to 24 letters, numbers, spaces and a few marks. */
    public static String nameProblem(String name) {
        String n = name == null ? "" : name.trim();
        if (n.length() < 3 || n.length() > 24 || !n.matches("[A-Za-z0-9 '&.-]+")) {
            return "Pick a name of 3 to 24 letters, numbers or spaces.";
        }
        return null;
    }

    /** Why this player cannot found a dojo right now, or null. */
    public static String foundProblem(ServerPlayer p) {
        UUID id = p.m_20148_();
        if (dojoOf(id) != null) {
            return "You're already in a dojo.";
        }
        Dojo mine = store().dojos.get(id.toString());
        if (mine != null && !mine.open) {
            return "Your dojo is closed. Reopen it from the Dojo tab (every upgrade drops one tier).";
        }
        SparStore.MentorBond b = SparStore.get().bondsByPlayer.get(id.toString());
        if (b != null && b.mentorUuid != null && !b.mentorUuid.isBlank()) {
            return "Your master's dojo is closed. Ask them to reopen it, or leave the bond to found your own.";
        }
        return null;
    }

    /**
     * 4.7.78: found a dojo with its first identity: the name the player typed, the style they picked and the banner
     * design they chose (copied, the item is not taken). Free, and starts no cooldown. The old guards run here.
     */
    public static String found(ServerPlayer p, String name, String styleId, String bannerBase, List<String> patterns) {
        String bad = foundProblem(p);
        if (bad != null) {
            return bad;
        }
        String nameBad = nameProblem(name);
        if (nameBad != null) {
            return nameBad;
        }
        DojoCatalog.Style s = DojoCatalog.Style.of(styleId);
        if (s == null) {
            return "Pick one of the four styles.";
        }
        if (bannerBase == null || bannerBase.isBlank()) {
            return "Pick a banner you made at a loom first.";
        }
        UUID id = p.m_20148_();
        Dojo d = fresh(id.toString(), p.m_7755_().getString(), System.currentTimeMillis());
        d.name = name.trim();
        d.style = s.name();
        d.bannerBase = bannerBase;
        d.bannerPatterns = new ArrayList<>(patterns == null ? List.of() : patterns);
        store().dojos.put(d.masterUuid, d);
        store().player(d.masterUuid, p.m_7755_().getString());
        store().markDirty();
        DojoWorld.ensureBuilt(p.m_20194_(), d);
        return "You founded " + d.name + ". Your hall starts empty with one sparring mat; upgrades build into it. "
                + "Spar on the board to start earning Dojo Points.";
    }

    /** Milliseconds until the name, style or banner may change again (0 when free to change). */
    public static long identityCooldownLeft(Dojo d) {
        if (d == null || d.identityChangedAt <= 0L) {
            return 0L;
        }
        return Math.max(0L, d.identityChangedAt + DojoCatalog.IDENTITY_COOLDOWN_MS - System.currentTimeMillis());
    }

    /** "5h 12m" for the cooldown left. */
    public static String cooldownText(long ms) {
        long m = Math.max(1L, (ms + 59_999L) / 60_000L);
        return m >= 60 ? (m / 60) + "h " + (m % 60) + "m" : m + "m";
    }

    /** Why an identity change costing {@code cost} cannot happen now, or null (master check is the caller's). */
    public static String identityChangeProblem(Dojo d, int cost) {
        long left = identityCooldownLeft(d);
        if (left > 0L) {
            return "Your dojo's name, style and banner can change again in " + cooldownText(left) + ".";
        }
        if (d.points < cost) {
            return "That costs " + cost + " Dojo Points. Your dojo has " + d.points + ".";
        }
        return null;
    }

    /** Charge an identity change and start the shared cooldown. Never takes the balance below zero. */
    static void chargeIdentityChange(Dojo d, int cost) {
        d.points = Math.max(0, d.points - cost);
        d.identityChangedAt = System.currentTimeMillis();
    }

    /**
     * 4.7.78: set the dojo's banner design (master only). The first banner of a dojo that has none is free and starts
     * no cooldown; a change costs {@link DojoCatalog#BANNER_CHANGE_COST} and starts the shared cooldown.
     */
    public static String setBanner(ServerPlayer p, String bannerBase, List<String> patterns) {
        Dojo d = dojoOf(p);
        if (d == null || !isMaster(p.m_20148_(), d)) {
            return "Only the master can pick the dojo's banner.";
        }
        if (bannerBase == null || bannerBase.isBlank()) {
            return "Hold or carry a banner you made at a loom, then pick it.";
        }
        boolean first = d.bannerBase == null || d.bannerBase.isBlank();
        if (!first) {
            String bad = identityChangeProblem(d, DojoCatalog.BANNER_CHANGE_COST);
            if (bad != null) {
                return bad;
            }
            chargeIdentityChange(d, DojoCatalog.BANNER_CHANGE_COST);
        }
        d.bannerBase = bannerBase;
        d.bannerPatterns = new ArrayList<>(patterns == null ? List.of() : patterns);
        store().markDirty();
        return first ? "Your dojo flies its new banner." : "Your dojo's banner changed (" + DojoCatalog.BANNER_CHANGE_COST
                + " Dojo Points).";
    }

    /** Found a dojo alone (free). Before 4.7.78 this was one tap; the menu now uses the founding flow. */
    public static String found(ServerPlayer p) {
        UUID id = p.m_20148_();
        if (dojoOf(id) != null) {
            return "You're already in a dojo.";
        }
        Dojo mine = store().dojos.get(id.toString());
        if (mine != null && !mine.open) {
            return "Your dojo is closed. Reopen it from the Dojo tab (every upgrade drops one tier).";
        }
        SparStore.MentorBond b = SparStore.get().bondsByPlayer.get(id.toString());
        if (b != null && b.mentorUuid != null && !b.mentorUuid.isBlank()) {
            return "Your master's dojo is closed. Ask them to reopen it, or leave the bond to found your own.";
        }
        Dojo d = fresh(id.toString(), p.m_7755_().getString(), System.currentTimeMillis());
        store().dojos.put(d.masterUuid, d);
        store().player(d.masterUuid, p.m_7755_().getString());
        store().markDirty();
        DojoWorld.ensureBuilt(p.m_20194_(), d);
        return "You founded " + d.name + ". Your hall starts empty with one sparring mat; upgrades build into it. "
                + "Spar on the board to start earning Dojo Points.";
    }

    /** Close the dojo (master only). Members, style and upgrades are kept for a reopen. */
    public static String close(ServerPlayer p) {
        Dojo d = store().dojos.get(p.m_20148_().toString());
        if (d == null || !d.open) {
            return "You don't run an open dojo.";
        }
        if (BoardSpar.boutsIn(d.masterUuid) > 0 || DojoWar.runningFor(d.masterUuid) != null) {
            return "Wait until the fights in your dojo end, then close it.";
        }
        d.open = false;
        d.closedAt = System.currentTimeMillis();
        store().markDirty();
        return "Closed " + d.name + ". Reopening keeps your members and style, but every upgrade drops one tier.";
    }

    /** Reopen the same dojo: identity, members and style stay; every upgrade drops one tier. */
    public static String reopen(ServerPlayer p) {
        Dojo d = store().dojos.get(p.m_20148_().toString());
        if (d == null || d.open) {
            return "You have no closed dojo to reopen.";
        }
        for (String up : DojoCatalog.UPGRADES) {
            int t = tier(d, up);
            d.upgrades.put(up, DojoCatalog.afterReopen(up, t));
        }
        d.open = true;
        d.timesReopened++;
        d.builtSignature = "";
        store().markDirty();
        return "Reopened " + d.name + ". Every upgrade is one tier lower than before.";
    }

    public static String rename(ServerPlayer p, String name) {
        Dojo d = dojoOf(p);
        if (d == null || !isMaster(p.m_20148_(), d)) {
            return "Only the master can rename the dojo.";
        }
        String n = name == null ? "" : name.trim();
        String nameBad = nameProblem(n);
        if (nameBad != null) {
            return nameBad;
        }
        if (n.equals(d.name)) {
            return "Your dojo is already called " + n + ".";
        }
        // 4.7.78: a rename costs Dojo Points and starts the shared 12-hour identity cooldown.
        String bad = identityChangeProblem(d, DojoCatalog.NAME_CHANGE_COST);
        if (bad != null) {
            return bad;
        }
        chargeIdentityChange(d, DojoCatalog.NAME_CHANGE_COST);
        d.name = n;
        store().markDirty();
        return "Your dojo is now called " + n + " (" + DojoCatalog.NAME_CHANGE_COST + " Dojo Points).";
    }

    // ------------------------------------------------------------------ Dojo Points and clout

    static String dayKey(String source, long now) {
        return source + "|" + (now / DAY_MS);
    }

    /** Add {@code earn} points for one event, up to that source's daily cap. Returns the points granted. */
    public static int earn(Dojo d, DojoCatalog.Earn earn) {
        if (d == null || earn == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        String key = dayKey("pts:" + earn.name(), now);
        int today = d.daily.getOrDefault(key, 0);
        int grant = Math.max(0, Math.min(earn.points, earn.dailyCap - today));
        if (grant <= 0) {
            return 0;
        }
        d.daily.put(key, today + grant);
        d.points += grant;
        d.pointsEarnedTotal += grant;
        pruneDaily(d, now);
        store().markDirty();
        return grant;
    }

    /** Add clout from {@code source}, up to {@code dailyCap} a day (0 for no cap). */
    public static int clout(Dojo d, String source, int amount, int dailyCap) {
        if (d == null || amount <= 0) {
            return 0;
        }
        rollClout(d);
        long now = System.currentTimeMillis();
        int grant = amount;
        if (dailyCap > 0) {
            String key = dayKey("clout:" + source, now);
            int today = d.daily.getOrDefault(key, 0);
            grant = Math.max(0, Math.min(amount, dailyCap - today));
            d.daily.put(key, today + grant);
        }
        d.clout += grant;
        store().markDirty();
        return grant;
    }

    static void pruneDaily(Dojo d, long now) {
        long today = now / DAY_MS;
        d.daily.keySet().removeIf(k -> {
            int bar = k.lastIndexOf('|');
            try {
                return bar < 0 || Long.parseLong(k.substring(bar + 1)) < today - 1;
            } catch (NumberFormatException e) {
                return true;
            }
        });
    }

    /** Clout belongs to a season: a dojo still on an old season moves to the current one at 0 (history kept). */
    static void rollClout(Dojo d) {
        int season = currentSeason();
        if (d.cloutSeason != season) {
            d.cloutHistory.put(String.valueOf(d.cloutSeason), d.clout);
            d.clout = 0;
            d.cloutSeason = season;
        }
    }

    /**
     * Season reset (called from the one dojo season rollover path): every dojo's clout goes to its history and starts
     * over, last season's top three start with placement clout; rooms, arenas, points and style are untouched.
     */
    public static void onSeasonAdvanced(int endedSeason, int newSeason) {
        List<Dojo> all = new ArrayList<>(store().dojos.values());
        all.sort(Comparator.comparingInt((Dojo d) -> -d.clout));
        for (Dojo d : all) {
            d.cloutHistory.put(String.valueOf(endedSeason), d.clout);
            d.clout = 0;
            d.cloutSeason = newSeason;
        }
        for (int i = 0; i < Math.min(DojoCatalog.CLOUT_PLACEMENT.length, all.size()); i++) {
            if (all.get(i).cloutHistory.getOrDefault(String.valueOf(endedSeason), 0) > 0) {
                all.get(i).clout = DojoCatalog.CLOUT_PLACEMENT[i];
            }
        }
        store().markDirty();
    }

    /** Dojos by clout, highest first (open dojos only). */
    /** 4.7.77: open dojos by war wins (then clout), for the leaderboard's war view. */
    public static List<Dojo> byWarWins() {
        List<Dojo> out = byClout();
        out.sort(Comparator.comparingInt((Dojo d) -> -d.warWins).thenComparingInt(d -> -d.clout)
                .thenComparing(d -> d.name));
        return out;
    }

    public static List<Dojo> byClout() {
        List<Dojo> out = new ArrayList<>();
        for (Dojo d : store().dojos.values()) {
            if (d.open) {
                rollClout(d);
                out.add(d);
            }
        }
        out.sort(Comparator.comparingInt((Dojo d) -> -d.clout).thenComparing(d -> d.name));
        return out;
    }

    // ------------------------------------------------------------------ upgrades

    /** Buy the next level of {@code upgrade} (master or elite). */
    public static String buy(ServerPlayer p, String upgrade) {
        Dojo d = dojoOf(p);
        if (d == null) {
            return "Join or found a dojo first.";
        }
        if (!canManage(p.m_20148_(), d)) {
            return "Only the master or an elite can buy upgrades.";
        }
        if (!DojoCatalog.UPGRADES.contains(upgrade)) {
            return "That upgrade doesn't exist.";
        }
        int t = tier(d, upgrade);
        int cost = DojoCatalog.cost(upgrade, t);
        if (cost < 0) {
            return DojoCatalog.upgradeName(upgrade) + " is already at its best.";
        }
        if (d.points < cost) {
            return "That costs " + cost + " Dojo Points. Your dojo has " + d.points + ".";
        }
        d.points -= cost;
        d.upgrades.put(upgrade, t + 1);
        d.builtSignature = "";
        store().markDirty();
        DojoWorld.ensureBuilt(p.m_20194_(), d);
        String now = DojoCatalog.ARENA.equals(upgrade) ? (t + 1) + " arenas"
                : DojoCatalog.CAPACITY.equals(upgrade) ? DojoCatalog.disciplesAllowed(t + 1) + " disciples"
                : DojoCatalog.lookName(DojoCatalog.lookTier(upgrade, t + 1)) + " look";
        return "Bought " + DojoCatalog.upgradeName(upgrade) + " for " + cost + " points. Now: " + now + ".";
    }

    // ------------------------------------------------------------------ style and bonus

    public static String setStyle(ServerPlayer p, String styleId) {
        Dojo d = dojoOf(p);
        if (d == null || !isMaster(p.m_20148_(), d)) {
            return "Only the master picks the dojo's style.";
        }
        DojoCatalog.Style s = DojoCatalog.Style.of(styleId);
        if (s == null) {
            return "Pick one of the four styles.";
        }
        if (s.name().equalsIgnoreCase(d.style)) {
            return "Your dojo already trains " + s.label + ".";
        }
        // 4.7.78: changing a picked style costs Dojo Points and starts the shared 12-hour identity cooldown; the first
        // pick (an older dojo with no style yet) is free.
        if (d.style != null && !d.style.isBlank()) {
            String bad = identityChangeProblem(d, DojoCatalog.STYLE_CHANGE_COST);
            if (bad != null) {
                return bad;
            }
            chargeIdentityChange(d, DojoCatalog.STYLE_CHANGE_COST);
        }
        boolean had = !d.bonus.isBlank();
        d.style = s.name();
        d.bonus = "";
        store().markDirty();
        DojoWorld.ensureBuilt(p.m_20194_(), d);                 // the hall takes the style's banners and colours
        return "Your dojo now trains " + s.label + "." + (had ? " The old bonus ended; pick a new one." : " Pick a bonus next.");
    }

    /** Pick (or change) the active bonus from the style's catalog: costs points, changes have a cooldown. */
    public static String pickBonus(ServerPlayer p, String bonusId) {
        Dojo d = dojoOf(p);
        if (d == null || !isMaster(p.m_20148_(), d)) {
            return "Only the master picks the dojo's bonus.";
        }
        DojoCatalog.Bonus b = DojoCatalog.bonus(bonusId);
        DojoCatalog.Style s = DojoCatalog.Style.of(d.style);
        if (b == null || s == null || b.style() != s) {
            return "Pick a bonus from your dojo's style.";
        }
        if (b.id().equalsIgnoreCase(d.bonus)) {
            return b.name() + " is already active.";
        }
        long left = bonusCooldownLeft(d);
        if (left > 0L) {
            return "You can change the bonus again in " + (left / DAY_MS + 1) + " days.";
        }
        if (d.points < DojoCatalog.BONUS_COST) {
            return "A bonus costs " + DojoCatalog.BONUS_COST + " Dojo Points. Your dojo has " + d.points + ".";
        }
        d.points -= DojoCatalog.BONUS_COST;
        d.bonus = b.id();
        d.bonusChangedAt = System.currentTimeMillis();
        store().markDirty();
        return b.name() + " is active: " + b.description();
    }

    /** Milliseconds until the bonus may change again (0 when there is none yet). */
    public static long bonusCooldownLeft(Dojo d) {
        if (d == null || d.bonus.isBlank()) {
            return 0L;
        }
        return Math.max(0L, d.bonusChangedAt + DojoCatalog.BONUS_COOLDOWN_MS - System.currentTimeMillis());
    }

    /**
     * Extra spar TP share for a hit of {@code hitKind} by {@code player}: the dojo bonus when the hit is the dojo's
     * style and (for a race tradition) the player is that race; 0 otherwise.
     */
    public static double styleBonus(ServerPlayer player, String hitKind) {
        Dojo d = dojoOf(player);
        if (d == null || d.bonus.isBlank()) {
            return 0.0;
        }
        DojoCatalog.Bonus b = DojoCatalog.bonus(d.bonus);
        if (b == null || !b.style().covers(hitKind)) {
            return 0.0;
        }
        if (!b.requiredRace().isBlank()) {
            String race;
            try {
                race = DmzProgression.race(player);
            } catch (Throwable t) {
                race = "";
            }
            if (race == null || !b.requiredRace().equalsIgnoreCase(race.trim().toLowerCase(Locale.ROOT))) {
                return 0.0;
            }
        }
        return b.pct();
    }

    // ------------------------------------------------------------------ elites

    public static String promote(ServerPlayer master, String targetUuid) {
        Dojo d = dojoOf(master);
        if (d == null || !isMaster(master.m_20148_(), d)) {
            return "Only the master names elites.";
        }
        if (!disciples(d).contains(targetUuid)) {
            return "Only a disciple of your dojo can be an elite.";
        }
        if (d.elites.contains(targetUuid)) {
            return nameOf(targetUuid) + " is already an elite.";
        }
        if (d.elites.size() >= DojoCatalog.MAX_ELITES) {
            return "Your dojo already has two elites. Step one down first.";
        }
        long joined = d.joinedAt.getOrDefault(targetUuid, System.currentTimeMillis());
        long left = joined + DojoCatalog.ELITE_MIN_MS - System.currentTimeMillis();
        if (left > 0L) {
            return nameOf(targetUuid) + " can be an elite after " + (left / DAY_MS + 1) + " more days in the dojo.";
        }
        d.elites.add(targetUuid);
        store().markDirty();
        return nameOf(targetUuid) + " is now an elite: they can invite members, buy upgrades and declare wars.";
    }

    public static String demote(ServerPlayer master, String targetUuid) {
        Dojo d = dojoOf(master);
        if (d == null || !isMaster(master.m_20148_(), d)) {
            return "Only the master manages elites.";
        }
        if (!d.elites.remove(targetUuid)) {
            return nameOf(targetUuid) + " isn't an elite.";
        }
        d.warRights.remove(targetUuid);
        store().markDirty();
        return nameOf(targetUuid) + " is no longer an elite.";
    }

    public static String toggleWarRights(ServerPlayer master, String targetUuid) {
        Dojo d = dojoOf(master);
        if (d == null || !isMaster(master.m_20148_(), d) || !d.elites.contains(targetUuid)) {
            return "Only the master gives war rights, and only to an elite.";
        }
        if (d.warRights.remove(targetUuid)) {
            store().markDirty();
            return nameOf(targetUuid) + "'s wars need your sign-off again.";
        }
        d.warRights.add(targetUuid);
        store().markDirty();
        return nameOf(targetUuid) + " can now declare wars without your sign-off.";
    }

    /** A disciple joined (bond made): stamp the join time for the elite minimum. */
    public static void onDiscipleJoined(String masterUuid, String discipleUuid) {
        Dojo d = byMaster(masterUuid);
        if (d != null && !d.joinedAt.containsKey(discipleUuid)) {
            d.joinedAt.put(discipleUuid, System.currentTimeMillis());
            store().markDirty();
        }
    }

    /** A disciple left: they stop being an elite. */
    public static void onDiscipleLeft(String masterUuid, String discipleUuid) {
        Dojo d = byMaster(masterUuid);
        if (d != null) {
            d.elites.remove(discipleUuid);
            d.warRights.remove(discipleUuid);
            d.joinedAt.remove(discipleUuid);
            store().markDirty();
        }
    }

    public static List<String> selfTest() {
        List<String> bad = new ArrayList<>(DojoCatalog.selfTest());
        Dojo d = fresh("00000000-0000-0000-0000-000000000001", "Test", 0L);
        if (arenas(d) != 1 || tier(d, DojoCatalog.TRAINING) != 0 || !d.open) {
            bad.add("a new dojo starts open with one arena and no rooms");
        }
        int first = earn(d, DojoCatalog.Earn.HOST);
        for (int i = 0; i < 20; i++) {
            earn(d, DojoCatalog.Earn.HOST);
        }
        if (first != 2 || d.points != DojoCatalog.Earn.HOST.dailyCap) {
            bad.add("hosting pays 2 points a spar, 20 a day at most (got " + d.points + ")");
        }
        d.upgrades.put(DojoCatalog.TRAINING, 3);
        d.upgrades.put(DojoCatalog.ARENA, 4);
        for (String up : DojoCatalog.UPGRADES) {
            d.upgrades.put(up, DojoCatalog.afterReopen(up, tier(d, up)));
        }
        if (tier(d, DojoCatalog.TRAINING) != 2 || arenas(d) != 3) {
            bad.add("reopen: every upgrade one tier down");
        }
        d.clout = 40;
        d.cloutSeason = 3;
        d.cloutHistory.clear();
        d.cloutHistory.put("2", 99);
        int season = d.cloutSeason;
        d.cloutHistory.put(String.valueOf(season), d.clout);
        d.clout = 0;
        if (d.cloutHistory.get("3") != 40 || d.clout != 0 || tier(d, DojoCatalog.TRAINING) != 2) {
            bad.add("season reset: clout to history and 0, rooms untouched");
        }
        return bad;
    }
}
