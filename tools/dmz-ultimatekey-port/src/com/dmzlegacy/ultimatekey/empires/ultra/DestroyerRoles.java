package com.dmzlegacy.ultimatekey.empires.ultra;

import com.dmzultra.divine.DestroyerDomains;
import com.dmzultra.divine.path.DestroyerSeats;
import com.dmzultra.host.Hosts;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Who holds destroyer offices, for the empires layer. Replaces the Noea role registry.
 *
 * <p>{@link #get(ServerLevel)} returns a read-only view of DMZ Ultra's Destroyer Path: each
 * universe's seat holder is a DESTROYER, its apprentices are APPRENTICEs, and online candidates
 * are CANDIDATEs. Ultra owns those seats, so {@link #force} and {@link #remove} refuse on that view
 * and DUK's own term and succession rules stay dormant.
 *
 * <p>An instance made with {@code new} is a plain in-memory registry with the Noea rules, which the
 * empires self-tests use.
 */
public final class DestroyerRoles extends SavedData {
    public static final String ULTRA_OWNS_SEATS =
            "Destroyer seats are held in DMZ Ultra's Destroyer Path. Change them there.";
    private static final int UNIVERSES = 12;
    private static final int MAX_ASSIGNMENTS = 2048;

    private final LinkedHashMap<UUID, Assignment> assignments = new LinkedHashMap<>();
    private boolean ultraView;
    private int revision;

    public DestroyerRoles() {
    }

    /** The current Destroyer Path seats, read from DMZ Ultra. */
    public static DestroyerRoles get(ServerLevel level) {
        DestroyerRoles view = new DestroyerRoles();
        view.ultraView = true;
        MinecraftServer server = level.m_7654_();
        if (server == null || !UltraLink.present() || !DestroyerDomains.enabled()) {
            return view;
        }
        for (int universe = 1; universe <= UNIVERSES; universe++) {
            String pool = DestroyerDomains.pool(universe);
            UUID holder = DestroyerDomains.holderId(server, universe);
            if (holder != null) {
                view.assignments.putIfAbsent(holder, new Assignment(holder,
                        bounded(DestroyerDomains.nameOf(server, holder), 48), pool, DestroyerRank.DESTROYER, "dmzultra", 0L));
            }
            for (DestroyerSeats.Member member : DestroyerSeats.get(server).apprentices(universe)) {
                UUID id = member.key().account();
                view.assignments.putIfAbsent(id, new Assignment(id, bounded(member.name(), 48), pool,
                        DestroyerRank.APPRENTICE, "dmzultra", Math.max(0L, member.lastSeen())));
            }
        }
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            DestroyerRank rank = DestroyerRank.of(Hosts.destroyer().rank(player));
            if (rank == DestroyerRank.CANDIDATE) {
                int universe = Hosts.destroyer().universeOf(player);
                view.assignments.putIfAbsent(player.m_20148_(), new Assignment(player.m_20148_(),
                        bounded(player.m_36316_().getName(), 48), universe > 0 ? DestroyerDomains.pool(universe) : "",
                        DestroyerRank.CANDIDATE, "dmzultra", 0L));
            }
        }
        return view;
    }

    public static DestroyerRoles load(CompoundTag tag) {
        DestroyerRoles data = new DestroyerRoles();
        data.revision = Math.max(0, tag.m_128451_("Revision"));
        ListTag list = tag.m_128437_("Assignments", 10);
        for (int i = 0; i < list.size() && data.assignments.size() < MAX_ASSIGNMENTS; i++) {
            Assignment assignment = Assignment.load(list.m_128728_(i));
            if (assignment == null || assignment.rank() == DestroyerRank.NONE) {
                continue;
            }
            data.assignments.putIfAbsent(assignment.playerId(), assignment);
        }
        return data;
    }

    @Override
    public CompoundTag m_7176_(CompoundTag tag) {
        tag.m_128405_("SchemaVersion", 1);
        tag.m_128405_("Revision", this.revision);
        ListTag list = new ListTag();
        this.assignments.values().stream()
                .sorted(Comparator.comparing(a -> a.playerId().toString()))
                .limit(MAX_ASSIGNMENTS)
                .forEach(a -> list.add((Tag)a.save()));
        tag.m_128365_("Assignments", (Tag)list);
        return tag;
    }

    public Assignment assignment(UUID playerId) {
        return this.assignments.getOrDefault(playerId, Assignment.none(playerId));
    }

    public Collection<Assignment> assignments() {
        return List.copyOf(this.assignments.values());
    }

    public int count(String poolId, DestroyerRank rank) {
        return (int)this.assignments.values().stream().filter(a -> a.poolId().equals(poolId) && a.rank() == rank).count();
    }

    public List<Assignment> appointed(String poolId) {
        return this.assignments.values().stream()
                .filter(a -> a.poolId().equals(poolId) && a.rank() == DestroyerRank.DESTROYER)
                .sorted(Comparator.comparingLong(Assignment::assignedAt))
                .toList();
    }

    public Result force(UUID playerId, String name, String poolId, DestroyerRank rank, String source, long now) {
        if (this.ultraView) {
            return Result.fail(ULTRA_OWNS_SEATS);
        }
        if (rank == null || rank == DestroyerRank.NONE) {
            return this.remove(playerId, "administrator-revocation");
        }
        return this.put(new Assignment(playerId, bounded(name, 48), poolId, rank, bounded(source, 64), Math.max(0L, now)));
    }

    public Result remove(UUID playerId, String reason) {
        if (this.ultraView) {
            return Result.fail(ULTRA_OWNS_SEATS);
        }
        Assignment removed = this.assignments.remove(playerId);
        if (removed == null) {
            return Result.fail("No Destroyer role assignment exists.");
        }
        this.revision++;
        this.m_77762_();
        return new Result(true, Assignment.none(playerId), List.of(), true, bounded(reason, 64));
    }

    public int revision() {
        return this.revision;
    }

    private Result put(Assignment assignment) {
        Assignment old = this.assignments.put(assignment.playerId(), assignment);
        boolean changed = !assignment.equals(old);
        if (changed) {
            this.revision++;
            this.m_77762_();
        }
        return new Result(true, assignment, List.of(), changed, "ok");
    }

    static String bounded(String value, int maximum) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maximum ? trimmed : trimmed.substring(0, maximum);
    }

    public record Assignment(UUID playerId, String lastKnownName, String poolId, DestroyerRank rank, String source, long assignedAt) {
        static Assignment none(UUID playerId) {
            return new Assignment(playerId, "", "", DestroyerRank.NONE, "", 0L);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.m_128362_("Player", this.playerId);
            tag.m_128359_("Name", bounded(this.lastKnownName, 48));
            tag.m_128359_("Pool", bounded(this.poolId, 32));
            tag.m_128359_("Rank", this.rank.name());
            tag.m_128359_("Source", bounded(this.source, 64));
            tag.m_128356_("AssignedAt", Math.max(0L, this.assignedAt));
            return tag;
        }

        static Assignment load(CompoundTag tag) {
            if (!tag.m_128403_("Player")) {
                return null;
            }
            DestroyerRank rank = DestroyerRank.parse(tag.m_128461_("Rank"));
            String pool = bounded(tag.m_128461_("Pool"), 32);
            if (rank == DestroyerRank.NONE || pool.isEmpty()) {
                return null;
            }
            return new Assignment(tag.m_128342_("Player"), bounded(tag.m_128461_("Name"), 48), pool, rank,
                    bounded(tag.m_128461_("Source"), 64), Math.max(0L, tag.m_128454_("AssignedAt")));
        }
    }

    public record Result(boolean success, Assignment assignment, List<UUID> displaced, boolean changed, String message) {
        static Result fail(String message) {
            return new Result(false, null, new ArrayList<>(), false, bounded(message, 192));
        }
    }
}
