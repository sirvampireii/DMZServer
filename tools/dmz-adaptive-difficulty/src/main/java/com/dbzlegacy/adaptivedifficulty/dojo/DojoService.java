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
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
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

    // ------------------------------------------------------------------ 4.7.88: more than one dojo

    /** 4.7.88: how a player belongs to a dojo. */
    public enum Role {
        MASTER("Master"),
        DISCIPLE("Disciple");

        public final String label;

        Role(String label) {
            this.label = label;
        }
    }

    /** 4.7.88: one open dojo a player belongs to, and as what. */
    public record Affiliation(Dojo dojo, Role role) {}

    /**
     * 4.7.88: every open dojo {@code player} belongs to: their own (as master) first, then their master's (as disciple)
     * from the mentor bond. Empty when they are in none. {@link #dojoOf(UUID)} is the first of these.
     */
    public static List<Affiliation> affiliations(UUID player) {
        List<Affiliation> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        String id = player.toString();
        Dojo own = store().dojos.get(id);
        if (own != null && own.open) {
            out.add(new Affiliation(own, Role.MASTER));
        }
        SparStore.MentorBond b = SparStore.get().bondsByPlayer.get(id);
        if (b != null && b.mentorUuid != null && !b.mentorUuid.isBlank() && !b.mentorUuid.equals(id)) {
            Dojo m = store().dojos.get(b.mentorUuid);
            if (m != null && m.open && m != own) {
                out.add(new Affiliation(m, Role.DISCIPLE));
            }
        }
        return out;
    }

    /** 4.7.88: {@code player}'s place in {@code d}, or null when they don't belong to it. */
    public static Affiliation affiliation(UUID player, Dojo d) {
        if (d == null) {
            return null;
        }
        for (Affiliation a : affiliations(player)) {
            if (a.dojo() == d) {
                return a;
            }
        }
        return null;
    }

    /** 4.7.88: the dojo each player last opened from "Your dojos" (master UUID); not saved, a restart forgets it. */
    private static final Map<UUID, String> VIEWED = new ConcurrentHashMap<>();

    /**
     * 4.7.88: make {@code masterUuid}'s dojo the one {@code player}'s Dojo pages show and act on. Only a dojo they
     * belong to; false (and nothing changes) otherwise.
     */
    public static boolean view(UUID player, String masterUuid) {
        if (player == null || masterUuid == null) {
            return false;
        }
        for (Affiliation a : affiliations(player)) {
            if (a.dojo().masterUuid.equals(masterUuid)) {
                VIEWED.put(player, masterUuid);
                return true;
            }
        }
        return false;
    }

    /** 4.7.88: back to the default dojo ({@link #dojoOf(UUID)}). */
    public static void clearView(UUID player) {
        if (player != null) {
            VIEWED.remove(player);
        }
    }

    /**
     * 4.7.88: the dojo {@code player}'s Dojo pages show and every dojo management action applies to: the one they picked
     * on "Your dojos" while they still belong to it, else {@link #dojoOf(UUID)}. Rights are always checked against
     * this dojo, so a master viewing a dojo where they are a disciple can do only what a disciple can.
     */
    public static Dojo viewedDojo(UUID player) {
        if (player == null) {
            return null;
        }
        String m = VIEWED.get(player);
        if (m != null) {
            for (Affiliation a : affiliations(player)) {
                if (a.dojo().masterUuid.equals(m)) {
                    return a.dojo();
                }
            }
            VIEWED.remove(player);
        }
        return dojoOf(player);
    }

    public static Dojo viewedDojo(ServerPlayer p) {
        return p == null ? null : viewedDojo(p.m_20148_());
    }

    /**
     * 4.7.88: the dojo among {@code masterUuids} that {@code player} belongs to (the viewed one first), or null. Wars
     * use it: a war names its two dojos, so a player in both sides' dojos acts for the one they are viewing.
     */
    public static Dojo memberDojoAmong(UUID player, String... masterUuids) {
        return pickAmong(player, false, masterUuids);
    }

    /** 4.7.88: {@link #memberDojoAmong}, limited to dojos {@code player} may manage (master or elite). */
    public static Dojo managedDojoAmong(UUID player, String... masterUuids) {
        return pickAmong(player, true, masterUuids);
    }

    private static Dojo pickAmong(UUID player, boolean manage, String... masterUuids) {
        if (player == null || masterUuids == null) {
            return null;
        }
        List<Dojo> fits = new ArrayList<>();
        for (Affiliation a : affiliations(player)) {
            for (String m : masterUuids) {
                if (m != null && a.dojo().masterUuid.equals(m) && (!manage || canManage(player, a.dojo()))) {
                    fits.add(a.dojo());
                    break;
                }
            }
        }
        Dojo viewed = viewedDojo(player);
        return fits.contains(viewed) ? viewed : fits.isEmpty() ? null : fits.get(0);
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

    /**
     * 4.7.84 (owner pick b): the dojo title a disciple carries, "Disciple of Sparmate's Dojo"; null for the master and
     * for players in no dojo. Display only: it changes no stat. LM has no chat, tab-list or name-tag hook, so the
     * Dojo Home and the Members page show it beside the player's name.
     */
    public static String discipleTitle(UUID player) {
        return discipleTitle(player, dojoOf(player));
    }

    /** 4.7.88: {@link #discipleTitle(UUID)} in a given dojo (the one being viewed); null unless a disciple of it. */
    public static String discipleTitle(UUID player, Dojo d) {
        if (d == null || isMaster(player, d) || affiliation(player, d) == null) {
            return null;
        }
        return "Disciple of " + d.name;
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
            return "Your dojo is closed. Reopen it on Dojo Home (every upgrade drops one tier).";
        }
        SparStore.MentorBond b = SparStore.get().bondsByPlayer.get(id.toString());
        if (b != null && b.mentorUuid != null && !b.mentorUuid.isBlank()) {
            return "Your master's dojo is closed. Ask them to reopen it, or leave the bond to found your own.";
        }
        return null;
    }

    /**
     * 4.7.78: found a dojo with its first identity: the name the player typed, the style they picked and, optionally
     * (4.7.84, owner pick d), the banner design they chose (copied, the item is not taken). Without a banner the dojo
     * flies its style's default banner until the master picks one (the first pick stays free). Founding is free and
     * starts no cooldown; a player's first founding pays {@link DojoCatalog#FOUNDING_GRANT} Dojo Points (owner pick c).
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
        UUID id = p.m_20148_();
        boolean hadDojo = store().dojos.containsKey(id.toString());
        Dojo d = fresh(id.toString(), p.m_7755_().getString(), System.currentTimeMillis());
        d.name = name.trim();
        d.style = s.name();
        boolean banner = bannerBase != null && !bannerBase.isBlank();
        d.bannerBase = banner ? bannerBase : "";
        d.bannerPatterns = new ArrayList<>(banner && patterns != null ? patterns : List.of());
        DojoStore.PlayerRecord rec = store().player(d.masterUuid, p.m_7755_().getString());
        int grant = foundingGrant(rec, hadDojo);
        if (grant > 0) {
            rec.foundingGrantPaid = true;
            d.points += grant;
            d.pointsEarnedTotal += grant;
        }
        store().dojos.put(d.masterUuid, d);
        store().markDirty();
        DojoWorld.ensureBuilt(p.m_20194_(), d);
        return "You founded " + d.name + ". Your hall starts with one ring; upgrades build into it."
                + (grant > 0 ? " Your dojo starts with " + grant + " Dojo Points." : "")
                + (banner ? "" : " It flies a default banner until you choose one (free).")
                + " Host spars on the Spar Board to earn more points.";
    }

    /**
     * 4.7.84: the founding grant for a player founding now: {@link DojoCatalog#FOUNDING_GRANT} on their first founding
     * only. A player who already had a dojo (closed, migrated or founded before) or was paid once gets 0.
     */
    public static int foundingGrant(DojoStore.PlayerRecord rec, boolean hadDojo) {
        return hadDojo || (rec != null && rec.foundingGrantPaid) ? 0 : DojoCatalog.FOUNDING_GRANT;
    }

    /** 4.7.84: true when the master has not picked a banner yet (the dojo flies its style's default banner). */
    public static boolean defaultBanner(Dojo d) {
        return d != null && (d.bannerBase == null || d.bannerBase.isBlank());
    }

    /** 4.7.84: the banner colour id the dojo flies: its own, or its style's default (white with no style). */
    public static String bannerColour(Dojo d) {
        if (d == null) {
            return "white";
        }
        if (!defaultBanner(d)) {
            return d.bannerBase;
        }
        DojoCatalog.Style s = DojoCatalog.Style.of(d.style);
        return s == null ? "white" : s.bannerColour;
    }

    /** 4.7.84: "6 days 23h", "5h 12m": the time left on a lock, never rounded up past the real lock. */
    public static String daysText(long ms) {
        if (ms < DAY_MS) {
            return cooldownText(ms);
        }
        long days = ms / DAY_MS;
        long hours = (ms % DAY_MS) / (60L * 60L * 1000L);
        return days + (days == 1 ? " day" : " days") + (hours > 0 ? " " + hours + "h" : "");
    }

    /**
     * 4.7.84 (DJ-3): why {@code p} can't ask {@code d}'s master to take them on right now, or null when they can. The
     * same checks {@code SparringSystem.apprenticeInvite} makes, plus the master being online; short enough for a
     * button ("Ask to join - " + reason).
     */
    public static String joinProblem(ServerPlayer p, Dojo d, boolean masterOnline) {
        if (d == null || !d.open) {
            return "dojo closed";
        }
        UUID id = p.m_20148_();
        if (d.masterUuid.equals(id.toString())) {
            return "your own dojo";
        }
        if (dojoOf(id) != null) {
            return "you're in a dojo";
        }
        SparStore.MentorBond mine = SparStore.get().bondsByPlayer.get(id.toString());
        if (mine != null && mine.mentorUuid != null && !mine.mentorUuid.isBlank()) {
            return "leave your master first";
        }
        if (mine != null && System.currentTimeMillis() < mine.mentorChangeReadyAt) {
            return "in " + cooldownText(mine.mentorChangeReadyAt - System.currentTimeMillis());
        }
        if (disciples(d).size() >= disciplesAllowed(d.masterUuid)) {
            return "dojo full";
        }
        if (!masterOnline) {
            return "master offline";
        }
        return null;
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
        Dojo d = viewedDojo(p);
        if (d == null || !isMaster(p.m_20148_(), d)) {
            return "Only the master can pick the dojo's banner.";
        }
        if (bannerBase == null || bannerBase.isBlank()) {
            return "Design your banner in the editor first. No banner item is needed.";
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
        DojoWorld.ensureBuilt(p.m_20194_(), d);                 // 4.8.2: the hall's banner spots fly the new banner
        return first ? "Your dojo flies its new banner." : "Your dojo's banner changed (" + DojoCatalog.BANNER_CHANGE_COST
                + " Dojo Points).";
    }

    /** Close the dojo (master only). Members, style and upgrades are kept for a reopen. */
    public static String close(ServerPlayer p) {
        Dojo d = store().dojos.get(p.m_20148_().toString());
        if (d == null || !d.open) {
            return "You don't run an open dojo.";
        }
        // 4.7.88: a master viewing a dojo where they are a disciple can't close their own from there.
        if (viewedDojo(p) != d) {
            return "Open your own dojo from Your dojos first, then close it in Dojo settings.";
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
        Dojo d = viewedDojo(p);
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
        // 4.8.2: the first rename is free, works during the cooldown and starts none.
        if (freeRenameLeft(d)) {
            d.freeRenameUsed = true;
            d.name = n;
            store().markDirty();
            com.dbzlegacy.adaptivedifficulty.sparring.DojoRankings.syncDisplayName(d.masterUuid, n);
            return "Your dojo is now called " + n + ". That was your free rename; the next one costs "
                    + DojoCatalog.NAME_CHANGE_COST + " Dojo Points.";
        }
        // 4.7.78: a rename costs Dojo Points and starts the shared 12-hour identity cooldown.
        String bad = identityChangeProblem(d, DojoCatalog.NAME_CHANGE_COST);
        if (bad != null) {
            return bad;
        }
        chargeIdentityChange(d, DojoCatalog.NAME_CHANGE_COST);
        d.name = n;
        store().markDirty();
        com.dbzlegacy.adaptivedifficulty.sparring.DojoRankings.syncDisplayName(d.masterUuid, n);
        return "Your dojo is now called " + n + " (" + DojoCatalog.NAME_CHANGE_COST + " Dojo Points).";
    }

    /** 4.8.2: the dojo's one free rename is still unused (usable any time, no cost, no cooldown). */
    public static boolean freeRenameLeft(Dojo d) {
        return d != null && !d.freeRenameUsed;
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
        Dojo d = viewedDojo(p);
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
        String now = DojoCatalog.ARENA.equals(upgrade) ? (t + 1) + " rings"
                : DojoCatalog.CAPACITY.equals(upgrade) ? DojoCatalog.disciplesAllowed(t + 1) + " disciples"
                : DojoCatalog.lookName(DojoCatalog.lookTier(upgrade, t + 1)) + " look";
        return "Bought " + DojoCatalog.upgradeName(upgrade) + " for " + cost + " points. Now: " + now + ".";
    }

    // ------------------------------------------------------------------ style and bonus

    public static String setStyle(ServerPlayer p, String styleId) {
        Dojo d = viewedDojo(p);
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
        Dojo d = viewedDojo(p);
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
            return "You can change the bonus again in " + daysText(left) + ".";
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
        String bad = promoteProblem(master, targetUuid);
        if (bad != null) {
            return bad;
        }
        Dojo d = viewedDojo(master);
        d.elites.add(targetUuid);
        store().markDirty();
        return nameOf(targetUuid) + " is now an elite: they can buy upgrades and declare wars.";
    }

    /**
     * 4.8.4 (I): why {@code master} can't make {@code targetUuid} an elite right now, or null when they can. These are
     * {@link #promote}'s rules (the Members name-tap page shows the reason before the click).
     */
    public static String promoteProblem(ServerPlayer master, String targetUuid) {
        Dojo d = viewedDojo(master);
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
            return nameOf(targetUuid) + " can be an elite in " + daysText(left) + ".";
        }
        return null;
    }

    public static String demote(ServerPlayer master, String targetUuid) {
        Dojo d = viewedDojo(master);
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
        Dojo d = viewedDojo(master);
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

    // ------------------------------------------------------------------ 4.8.3: staff tools (/lm admin dojo)

    /** 4.8.3: what a staff dojo action did: {@code ok} false when nothing changed, {@code text} says what or why. */
    public record StaffResult(boolean ok, String text) {
        static StaffResult done(String text) {
            return new StaffResult(true, text);
        }

        static StaffResult no(String text) {
            return new StaffResult(false, text);
        }
    }

    /**
     * 4.8.3: every dojo (open or closed) a staff argument names: the dojo's name in any case, else its master's name,
     * else the master's UUID. Several when two dojos share a name; empty when none matches. Sorted by name.
     */
    public static List<Dojo> lookup(String text) {
        List<Dojo> out = new ArrayList<>();
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            return out;
        }
        for (Dojo d : store().dojos.values()) {
            if (d.name != null && d.name.trim().equalsIgnoreCase(t)) {
                out.add(d);
            }
        }
        if (out.isEmpty()) {
            for (Dojo d : store().dojos.values()) {
                if ((d.masterName != null && d.masterName.equalsIgnoreCase(t)) || nameOf(d.masterUuid).equalsIgnoreCase(t)) {
                    out.add(d);
                }
            }
        }
        if (out.isEmpty()) {
            Dojo d = store().dojos.get(t.toLowerCase(Locale.ROOT));
            if (d != null) {
                out.add(d);
            }
        }
        out.sort(Comparator.comparing((Dojo d) -> d.name == null ? "" : d.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    /** 4.8.3: every dojo's name (open and closed), sorted, for staff tab completion. */
    public static List<String> dojoNames() {
        List<String> out = new ArrayList<>();
        for (Dojo d : store().dojos.values()) {
            if (d.name != null && !d.name.isBlank() && !out.contains(d.name)) {
                out.add(d.name);
            }
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** 4.8.3: every disciple's name in {@code d} (staff tab completion). */
    public static List<String> discipleNames(Dojo d) {
        List<String> out = new ArrayList<>();
        for (String u : disciples(d)) {
            out.add(nameOf(u));
        }
        return out;
    }

    /** 4.8.3: the UUID of {@code d}'s disciple named {@code name} (any case) or with that UUID, or null. */
    public static String discipleNamed(Dojo d, String name) {
        String t = name == null ? "" : name.trim();
        for (String u : disciples(d)) {
            if (u.equalsIgnoreCase(t) || nameOf(u).equalsIgnoreCase(t)) {
                return u;
            }
        }
        return null;
    }

    /** 4.8.3: the staff status card for {@code d}: master, members, points, clout, season, style, banner, wars, plot. */
    public static List<String> statusLines(Dojo d) {
        String l = com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.LABEL;
        String v = com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.VALUE;
        String h = com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.GOLD;
        List<String> out = new ArrayList<>();
        rollClout(d);
        out.add(h + d.name + l + (d.open ? " (open)" : " (closed)") + " - master " + v + nameOf(d.masterUuid));
        List<String> members = new ArrayList<>();
        for (String u : disciples(d)) {
            String tag = d.elites.contains(u) ? (d.warRights.contains(u) ? " (elite, war rights)" : " (elite)") : "";
            members.add(nameOf(u) + tag);
        }
        out.add(l + "Disciples " + v + members.size() + l + " of " + v + disciplesAllowed(d.masterUuid)
                + (members.isEmpty() ? "" : l + ": " + v + String.join(", ", members)));
        out.add(l + "Dojo Points " + v + d.points + l + " (" + d.pointsEarnedTotal + " earned in all) - clout " + v
                + d.clout + l + " in season " + v + "#" + currentSeason());
        DojoCatalog.Style s = DojoCatalog.Style.of(d.style);
        DojoCatalog.Bonus b = d.bonus == null || d.bonus.isBlank() ? null : DojoCatalog.bonus(d.bonus);
        out.add(l + "Style " + v + (s == null ? "not picked" : s.label) + l + " - bonus " + v
                + (b == null ? "none" : b.name()) + l + " - " + v + arenas(d) + l + " rings");
        out.add(l + "Banner " + v + (defaultBanner(d) ? "the style's default (" + bannerColour(d).replace('_', ' ') + ")"
                : d.bannerBase.replace('_', ' ') + " with " + d.bannerPatterns.size()
                + (d.bannerPatterns.size() == 1 ? " layer" : " layers"))
                + l + " - free rename " + v + (freeRenameLeft(d) ? "unused" : "used"));
        DojoStore.WarRecord running = DojoWar.runningFor(d.masterUuid);
        int pending = 0;
        for (DojoStore.WarRecord w : DojoWar.warsOf(d.masterUuid)) {
            if (("PENDING_SIGNOFF".equals(w.status) || "CHALLENGED".equals(w.status)) && w != running) {
                pending++;
            }
        }
        String war = running != null ? "at war with " + (running.fromDojo.equals(d.masterUuid) ? running.toName
                : running.fromName) : "no war running";
        out.add(l + "Wars " + v + d.warWins + l + " won, " + v + d.warLosses + l + " lost - " + v + war
                + (pending > 0 ? l + ", " + v + pending + l + " challenge" + (pending == 1 ? "" : "s") + " waiting" : ""));
        String hall = d.plot < 0 ? "no plot yet" : "plot " + d.plot + (DojoPlacer.busy(d.masterUuid) ? ", building now"
                : DojoWorld.ready(d) ? ", hall up to date" : ", hall not built as it should be (rebuild fixes it)");
        out.add(l + "Hall " + v + hall + l + " - spars on now " + v + BoardSpar.boutsIn(d.masterUuid));
        return out;
    }

    /** 4.8.3 (staff): add {@code amount} Dojo Points with no daily cap; below zero takes points (never under 0). */
    public static int grantPoints(Dojo d, int amount) {
        if (d == null || amount == 0) {
            return 0;
        }
        int before = d.points;
        d.points = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) d.points + amount));
        if (d.points > before) {
            d.pointsEarnedTotal += d.points - before;
        }
        store().markDirty();
        return d.points - before;
    }

    /** 4.8.3 (staff): add {@code amount} clout this season with no daily cap ({@link #clout}); below zero takes it. */
    public static int grantClout(Dojo d, int amount) {
        if (d == null || amount == 0) {
            return 0;
        }
        if (amount > 0) {
            return clout(d, "staff", amount, 0);
        }
        rollClout(d);
        int before = d.clout;
        d.clout = Math.max(0, d.clout + amount);
        store().markDirty();
        return d.clout - before;
    }

    /** 4.8.3 (staff): close {@code d} for its master: the same close, refused while fights run in it. */
    public static StaffResult closeByStaff(Dojo d) {
        if (!d.open) {
            return StaffResult.no(d.name + " is already closed.");
        }
        if (BoardSpar.boutsIn(d.masterUuid) > 0 || DojoWar.runningFor(d.masterUuid) != null) {
            return StaffResult.no(d.name + " has fights running. Wait for its spars to end, or call off its war with "
                    + "/lm admin dojo war cancel first.");
        }
        d.open = false;
        d.closedAt = System.currentTimeMillis();
        store().markDirty();
        return StaffResult.done("Closed " + d.name + ". Members, style and upgrades are kept for a reopen.");
    }

    /** 4.8.3 (staff): reopen {@code d} for its master: the same reopen (every upgrade drops one tier). */
    public static StaffResult reopenByStaff(Dojo d) {
        if (d.open) {
            return StaffResult.no(d.name + " is already open.");
        }
        for (String up : DojoCatalog.UPGRADES) {
            d.upgrades.put(up, DojoCatalog.afterReopen(up, tier(d, up)));
        }
        d.open = true;
        d.timesReopened++;
        d.builtSignature = "";
        store().markDirty();
        return StaffResult.done("Reopened " + d.name + ". Every upgrade is one tier lower than before.");
    }

    /** 4.8.3 (staff): rename {@code d} with the name rule, but no cost, no cooldown, and the free rename unspent. */
    public static StaffResult renameByStaff(Dojo d, String name) {
        String n = name == null ? "" : name.trim();
        String bad = nameProblem(n);
        if (bad != null) {
            return StaffResult.no(bad);
        }
        if (n.equals(d.name)) {
            return StaffResult.no(d.name + " is already called that.");
        }
        String old = d.name;
        d.name = n;
        store().markDirty();
        com.dbzlegacy.adaptivedifficulty.sparring.DojoRankings.syncDisplayName(d.masterUuid, n);
        return StaffResult.done("Renamed " + old + " to " + n + ". No points were taken and the free rename is kept.");
    }

    /** 4.8.3 (staff): set {@code d}'s style (one of the four), no cost or cooldown; the bonus ends; the hall rebuilds. */
    public static StaffResult styleByStaff(MinecraftServer server, Dojo d, String styleId) {
        DojoCatalog.Style s = DojoCatalog.Style.of(styleId);
        if (s == null) {
            return StaffResult.no("Pick one of the four styles: ki, melee, strike or evasion.");
        }
        if (s.name().equalsIgnoreCase(d.style)) {
            return StaffResult.no(d.name + " already trains " + s.label + ".");
        }
        boolean had = d.bonus != null && !d.bonus.isBlank();
        d.style = s.name();
        d.bonus = "";
        store().markDirty();
        DojoWorld.ensureBuilt(server, d);
        return StaffResult.done(d.name + " now trains " + s.label + "." + (had ? " Its old bonus ended; the master picks"
                + " a new one." : "") + " The hall is rebuilding in the new style.");
    }

    /** 4.8.3: a staff banner text read into the stored form: base colour name and layers as "pattern:dye id". */
    public record BannerText(String base, List<String> patterns, String problem) {}

    /**
     * 4.8.3: read a staff banner text: {@code default} (back to the style's banner), or a base dye name ({@code red},
     * {@code light_blue}) followed by up to 16 layers, each {@code <pattern>:<dye>} with the vanilla pattern code
     * ({@code bs}, {@code cr}, {@code mc}, ...) and a dye name or dye number ({@code bs:white}, {@code cr:15}).
     * Layers are stored as "code:dye number", the form the Found a Dojo menu saves.
     */
    public static BannerText parseBanner(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            return new BannerText(null, null, "Give a base colour like red, or default for the style's banner.");
        }
        String[] parts = t.split("\\s+");
        if (parts.length == 1 && "default".equalsIgnoreCase(parts[0])) {
            return new BannerText("", List.of(), null);
        }
        net.minecraft.world.item.DyeColor base = parts[0].chars().allMatch(Character::isDigit) ? null
                : DojoPlacer.dye(parts[0]);
        if (base == null) {
            return new BannerText(null, null, parts[0] + " is not a dye colour. Use a name like red, white or light_blue.");
        }
        if (parts.length - 1 > 16) {
            return new BannerText(null, null, "A banner takes at most 16 layers.");
        }
        List<String> layers = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            int cut = p.lastIndexOf(':');
            net.minecraft.world.item.DyeColor c = cut <= 0 ? null : DojoPlacer.dye(p.substring(cut + 1));
            if (c == null) {
                return new BannerText(null, null, "Layer " + p + " needs a pattern and a colour, like bs:white.");
            }
            String code = p.substring(0, cut).toLowerCase(Locale.ROOT);
            if (net.minecraft.world.level.block.entity.BannerPattern.m_222700_(code) == null) {
                return new BannerText(null, null, code + " is not a banner pattern code. Use codes like bs, cr or mc.");
            }
            layers.add(code + ":" + c.m_41060_());
        }
        return new BannerText(base.m_41065_(), List.copyOf(layers), null);
    }

    /** 4.8.3 (staff): set {@code d}'s banner from a {@link #parseBanner} text, no cost or cooldown; the hall rebuilds. */
    public static StaffResult bannerByStaff(MinecraftServer server, Dojo d, String text) {
        BannerText b = parseBanner(text);
        if (b.problem() != null) {
            return StaffResult.no(b.problem());
        }
        d.bannerBase = b.base();
        d.bannerPatterns = new ArrayList<>(b.patterns());
        store().markDirty();
        DojoWorld.ensureBuilt(server, d);                       // the hall's banner spots fly the new banner
        return StaffResult.done(b.base().isEmpty() ? d.name + " flies its style's default banner again."
                : d.name + " flies a " + b.base().replace('_', ' ') + " banner with " + b.patterns().size()
                + (b.patterns().size() == 1 ? " layer" : " layers") + ". The hall is rebuilding with it.");
    }

    /** 4.8.3 (staff): make a disciple an elite: no join-time minimum, but at most {@link DojoCatalog#MAX_ELITES}. */
    public static StaffResult promoteByStaff(Dojo d, String uuid) {
        if (uuid == null || !disciples(d).contains(uuid)) {
            return StaffResult.no("Only a disciple of " + d.name + " can be an elite.");
        }
        if (d.elites.contains(uuid)) {
            return StaffResult.no(nameOf(uuid) + " is already an elite of " + d.name + ".");
        }
        if (d.elites.size() >= DojoCatalog.MAX_ELITES) {
            return StaffResult.no(d.name + " already has " + DojoCatalog.MAX_ELITES + " elites. Demote one first.");
        }
        d.elites.add(uuid);
        store().markDirty();
        return StaffResult.done(nameOf(uuid) + " is now an elite of " + d.name + ": they can buy upgrades and declare wars.");
    }

    /** 4.8.3 (staff): an elite steps down (war rights go with it). */
    public static StaffResult demoteByStaff(Dojo d, String uuid) {
        if (uuid == null || !d.elites.remove(uuid)) {
            return StaffResult.no((uuid == null ? "That player" : nameOf(uuid)) + " isn't an elite of " + d.name + ".");
        }
        d.warRights.remove(uuid);
        store().markDirty();
        return StaffResult.done(nameOf(uuid) + " is no longer an elite of " + d.name + ".");
    }

    /** 4.8.3 (staff): turn an elite's standing war rights on or off. */
    public static StaffResult warRightsByStaff(Dojo d, String uuid) {
        if (uuid == null || !d.elites.contains(uuid)) {
            return StaffResult.no("War rights go only to an elite. Promote them first with /lm admin dojo promote.");
        }
        if (d.warRights.remove(uuid)) {
            store().markDirty();
            return StaffResult.done(nameOf(uuid) + "'s wars need the master's sign-off again.");
        }
        d.warRights.add(uuid);
        store().markDirty();
        return StaffResult.done(nameOf(uuid) + " can now declare wars for " + d.name + " without the master's sign-off.");
    }

    /** 4.8.3: the name a staff founding uses when none is given: "Name's Dojo", cut to the name rule. */
    public static String defaultDojoName(String playerName) {
        String n = (playerName == null ? "" : playerName).replace('_', ' ').replaceAll("[^A-Za-z0-9 '&.-]", "").trim();
        String full = (n.isEmpty() ? "New" : n) + "'s Dojo";
        return full.length() > 24 ? full.substring(0, 24).trim() : full;
    }

    /**
     * 4.8.3 (staff {@code /lm admin dojo found}): found a dojo for {@code p} with the same checks and founding grant as
     * the Found a Dojo menu, but no style yet (the master's first pick stays free) and the default banner. {@code name}
     * blank: {@link #defaultDojoName}.
     */
    public static StaffResult foundByStaff(ServerPlayer p, String name) {
        UUID id = p.m_20148_();
        String who = p.m_7755_().getString();
        Dojo in = dojoOf(id);
        if (in != null) {
            return StaffResult.no(who + " is already in " + in.name + ".");
        }
        Dojo mine = store().dojos.get(id.toString());
        if (mine != null && !mine.open) {
            return StaffResult.no(who + " has a closed dojo, " + mine.name + ". Reopen it with /lm admin dojo reopen "
                    + mine.name + ".");
        }
        SparStore.MentorBond bond = SparStore.get().bondsByPlayer.get(id.toString());
        if (bond != null && bond.mentorUuid != null && !bond.mentorUuid.isBlank()) {
            return StaffResult.no(who + " is still bonded to a master whose dojo is closed. Clear it first with "
                    + "/lm admin clear " + who + " dojo.");
        }
        String n = name == null || name.isBlank() ? defaultDojoName(who) : name.trim();
        String bad = nameProblem(n);
        if (bad != null) {
            return StaffResult.no(bad);
        }
        boolean hadDojo = mine != null;
        Dojo d = fresh(id.toString(), who, System.currentTimeMillis());
        d.name = n;
        DojoStore.PlayerRecord rec = store().player(d.masterUuid, who);
        int grant = foundingGrant(rec, hadDojo);
        if (grant > 0) {
            rec.foundingGrantPaid = true;
            d.points += grant;
            d.pointsEarnedTotal += grant;
        }
        store().dojos.put(d.masterUuid, d);
        store().markDirty();
        DojoWorld.ensureBuilt(p.m_20194_(), d);
        return StaffResult.done("Founded " + n + " for " + who + "." + (grant > 0 ? " It starts with " + grant
                + " Dojo Points." : "") + " The master picks a style and a banner in Dojo settings (the first picks"
                + " are free).");
    }

    /** 4.8.3: end {@code discipleUuid}'s bond to {@code masterUuid} with no cooldown (a staff action, not a choice). */
    static void releaseDisciple(String masterUuid, String discipleUuid) {
        SparStore.MentorBond a = SparStore.get().bondsByPlayer.get(discipleUuid);
        if (a != null && masterUuid.equalsIgnoreCase(a.mentorUuid)) {
            a.mentorUuid = "";
            a.mentorName = "";
        }
        SparStore.MentorBond m = SparStore.get().bondsByPlayer.get(masterUuid);
        if (m != null) {
            m.normalizeApprentices();
            m.removeApprenticeUuid(discipleUuid);
        }
        onDiscipleLeft(masterUuid, discipleUuid);
        SparStore.get().markDirty();
    }

    /**
     * 4.8.3 (staff {@code /lm admin dojo disband}): delete {@code d} for good. Refused while a board spar runs in it.
     * Its wars are called off with no result, every disciple is released (no cooldown), online members are told, the
     * dojo leaves dojos.json and the dojo ladder (this season's board, its profile, old challenges; the Hall of Chosen
     * keeps past seasons), and its plot is cleared to empty void (no dojo is placed there again: plots are never
     * reused).
     */
    public static StaffResult disband(MinecraftServer server, Dojo d) {
        if (BoardSpar.boutsIn(d.masterUuid) > 0) {
            return StaffResult.no(d.name + " has spars running on its rings. Wait for them to end, then disband it.");
        }
        List<String> members = new ArrayList<>(disciples(d));
        int wars = DojoWar.staffCancelAll(server, d.masterUuid);
        for (String u : members) {
            releaseDisciple(d.masterUuid, u);
        }
        store().dojos.remove(d.masterUuid);
        VIEWED.values().removeIf(m -> m.equals(d.masterUuid));
        int ladder = com.dbzlegacy.adaptivedifficulty.sparring.DojoRankings.forgetDojo(d.masterUuid);
        store().markDirty();
        boolean cleared = d.plot >= 0 && DojoWorld.clearPlot(server, d.masterUuid, d.plot);
        List<String> notify = new ArrayList<>(members);
        notify.add(d.masterUuid);
        for (String u : notify) {
            ServerPlayer p = DojoWar.online(server, u);
            if (p != null) {
                com.dbzlegacy.adaptivedifficulty.util.DmzRewards.msg(p, com.dbzlegacy.adaptivedifficulty.util.LmChat
                        .info("Dojo", "Staff disbanded " + d.name + ". You are no longer in that dojo."));
            }
        }
        return StaffResult.done("Disbanded " + d.name + ": " + members.size() + (members.size() == 1 ? " disciple"
                : " disciples") + " released, " + wars + (wars == 1 ? " war" : " wars") + " called off, "
                + ladder + (ladder == 1 ? " ladder record" : " ladder records") + " removed. " + (d.plot < 0
                ? "It had no plot." : cleared ? "Its hall on plot " + d.plot + " is being cleared to empty void."
                : "The dojo world isn't loaded, so its hall on plot " + d.plot + " was left standing (no dojo owns it)."));
    }

    /**
     * 4.8.3 (staff {@code /lm admin clear <player> dojo}): take {@code uuid} out of the dojo they are a disciple of
     * (bond to its master ended with no cooldown, elite and war rights gone) and out of every other dojo's elite and
     * war-rights lists. A dojo they lead is kept. Returns the names of the dojos they left (may be empty).
     */
    public static List<String> removeFromDojos(String uuid) {
        List<String> left = new ArrayList<>();
        if (uuid == null || uuid.isBlank()) {
            return left;
        }
        SparStore.MentorBond bond = SparStore.get().bondsByPlayer.get(uuid);
        if (bond != null && bond.mentorUuid != null && !bond.mentorUuid.isBlank()) {
            Dojo m = byMaster(bond.mentorUuid);
            if (m != null) {
                left.add(m.name);
                releaseDisciple(m.masterUuid, uuid);
            }
        }
        for (Dojo d : store().dojos.values()) {
            if (d.masterUuid.equals(uuid)) {
                continue;
            }
            boolean was = d.elites.remove(uuid) | d.warRights.remove(uuid) | d.joinedAt.remove(uuid) != null;
            if (was && !left.contains(d.name)) {
                left.add(d.name);
            }
        }
        try {
            VIEWED.remove(UUID.fromString(uuid));
        } catch (IllegalArgumentException ignored) {
        }
        store().markDirty();
        return left;
    }

    /**
     * 4.8.3 (staff {@code /lm admin dojo reload}): read dojos.json again (changes since the last save are dropped),
     * then the boot steps: old mentors get their dojo, expired challenges stop blocking. Refused while a war or a
     * board spar runs, because those hold the live records.
     */
    public static StaffResult reloadByStaff() {
        if (!DojoWar.RUNS.isEmpty() || !BoardSpar.bouts().isEmpty()) {
            return StaffResult.no("A dojo war or board spar is running. Reload once the fights end.");
        }
        store().load();
        migrate();
        DojoWar.prune();
        return StaffResult.done("Reloaded dojo data from disk: " + store().dojos.size() + " dojos.");
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
        DojoStore.PlayerRecord rec = new DojoStore.PlayerRecord();
        if (foundingGrant(rec, false) != DojoCatalog.FOUNDING_GRANT || foundingGrant(rec, true) != 0) {
            bad.add("founding grant: paid on a first founding, never when the player already had a dojo");
        }
        rec.foundingGrantPaid = true;
        if (foundingGrant(rec, false) != 0) {
            bad.add("founding grant: paid once per player");
        }
        if (!daysText(7L * DAY_MS - 1000L).startsWith("6 days")) {
            bad.add("bonus lock text: just after buying it reads 6 days and some hours, never 8 days");
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
