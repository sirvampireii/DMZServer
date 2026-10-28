package com.dbzlegacy.adaptivedifficulty.data;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.character.CharacterServicesStore;
import com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import com.dbzlegacy.adaptivedifficulty.rival.RivalPlayerRecord;
import com.dbzlegacy.adaptivedifficulty.rival.RivalProgression;
import com.dbzlegacy.adaptivedifficulty.rival.RivalStore;
import com.dbzlegacy.adaptivedifficulty.scaling.AreaDifficulty;
import com.dbzlegacy.adaptivedifficulty.sparring.SparStore;
import com.dbzlegacy.adaptivedifficulty.tick.NearbyMobScaler;
import com.dbzlegacy.adaptivedifficulty.tick.ScaledMobTracker;
import com.dbzlegacy.adaptivedifficulty.title.TitleSystem;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Staff wipe of one player's LegacyMechanics data
 * ({@code /lm admin clear <player> [all|rival|spar|difficulty|progression]}).
 * The player may be offline. A name is resolved from who is online, the rival
 * store, sparring records, or the server player cache. A UUID always works.
 */
public final class PlayerDataClear {
    private PlayerDataClear() {}

    /**
     * Staff reset of character service cooldowns
     * ({@code /lm admin character cooldown clear <player> [race|class|reskin|all]}).
     * The player may be offline.
     */
    public static String clearCharacterCooldowns(MinecraftServer server, String playerArg, String kind) {
        if (playerArg == null || playerArg.isBlank()) {
            return "§cUsage: /lm admin character cooldown clear <player> [race|class|reskin|all]";
        }
        Lookup target = lookup(server, playerArg.trim());
        if (target.error != null) {
            return target.error;
        }
        String detail = CharacterServicesStore.get().clearCooldowns(target.resolved.uuid, kind);
        String who = target.resolved.name == null || target.resolved.name.isBlank()
                ? target.resolved.uuid : target.resolved.name;
        AdaptiveDifficultyMod.LOGGER.info(
                "[{}] Staff cleared character cooldowns uuid={} name={} kind={}",
                AdaptiveDifficultyMod.MOD_ID,
                target.resolved.uuid,
                target.resolved.name,
                kind == null ? "all" : kind);
        return "§aCharacter cooldown reset for §f" + who + "\n" + detail;
    }

    /**
     * @param playerArg in-game name or UUID. Offline names match sparring, rivals, or the player cache.
     * @param scope     {@code all} (default), {@code rival}, {@code spar}, {@code difficulty},
     *                  {@code progression}
     */
    public static String clear(MinecraftServer server, String playerArg, String scope) {
        if (playerArg == null || playerArg.isBlank()) {
            return "§cUsage: /lm admin clear <player> [all|rival|spar|difficulty|progression]";
        }
        String sc = scope == null || scope.isBlank() ? "all" : scope.toLowerCase(Locale.ROOT).trim();
        Lookup found = lookup(server, playerArg.trim());
        if (found.error != null) {
            return found.error;
        }
        Resolved target = found.resolved;

        List<String> done = new ArrayList<>();
        boolean all = "all".equals(sc) || "lm".equals(sc) || "*".equals(sc);
        boolean difficulty = all || "difficulty".equals(sc) || "diff".equals(sc) || "ad".equals(sc);
        boolean progression = all || "progression".equals(sc) || "prog".equals(sc)
                || "skills".equals(sc) || "meditation".equals(sc);
        try {
            if (all || "rival".equals(sc) || "rivals".equals(sc) || "rivalry".equals(sc)) {
                done.add(clearRival(target.uuid, target.name));
            }
            if (all || "spar".equals(sc) || "sparring".equals(sc)) {
                done.add(clearSpar(target.uuid, target.name));
            }
            if (difficulty) {
                if (target.online != null) {
                    done.add(clearDifficulty(target.online));
                }
            }
            if (progression) {
                done.add(clearProgression(target.online, target.uuid));
            }
            if (target.online == null && (difficulty || progression)) {
                done.add(clearOfflineLmFile(target.uuid, difficulty, progression));
                done.add(clearOfflinePlayerFile(server, target.uuid, difficulty, progression));
            }
            if (done.isEmpty()) {
                return "§cUnknown scope: §f" + sc
                        + "\n§8Use all · rival · spar · difficulty · progression";
            }
            String who = target.name == null || target.name.isBlank() ? target.uuid : target.name;
            StringBuilder sb = new StringBuilder("§aCleared LM data for §f").append(who);
            if (target.online == null) {
                sb.append("\n§7offline");
            }
            for (String line : done) {
                if (line != null && !line.isBlank()) {
                    sb.append('\n').append(line);
                }
            }
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Staff cleared player data uuid={} name={} scope={}",
                    AdaptiveDifficultyMod.MOD_ID, target.uuid, target.name, sc);
            return sb.toString();
        } catch (Throwable t) {
            return "§cClear failed: " + t;
        }
    }

    private static String clearRival(String uuid, String name) {
        RivalStore store = RivalStore.get();
        RivalPlayerRecord removed = store.players.remove(uuid);
        int linkScrubs = 0;
        for (RivalPlayerRecord other : store.players.values()) {
            if (other == null) {
                continue;
            }
            if (other.rivals != null && other.rivals.remove(uuid) != null) {
                linkScrubs++;
                other.recalcTotalRp();
            }
            if (other.pastRivals != null && other.pastRivals.remove(uuid) != null) {
                linkScrubs++;
            }
            if (uuid.equalsIgnoreCase(other.nemesisUuid)) {
                other.nemesisUuid = "";
                linkScrubs++;
            }
        }
        int reqs = 0;
        Iterator<Map.Entry<String, RivalStore.DeclareRequest>> it =
                store.declareRequests.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, RivalStore.DeclareRequest> e = it.next();
            RivalStore.DeclareRequest r = e.getValue();
            if (r != null && (uuid.equalsIgnoreCase(r.fromUuid) || uuid.equalsIgnoreCase(r.toUuid))) {
                it.remove();
                reqs++;
            }
        }
        // Season / achievements / journal for this uuid
        try {
            RivalProgression prog = RivalProgression.get();
            prog.clearPlayer(uuid);
        } catch (Throwable ignored) {
        }
        store.markDirty();
        store.save();
        boolean had = removed != null;
        return "§7Rival: " + (had ? "§arecord removed" : "§eno record")
                + " §8· links scrubbed §f" + linkScrubs
                + " §8· requests §f" + reqs
                + (name != null && !name.isBlank() ? " §8(" + name + ")" : "");
    }

    private static String clearSpar(String uuid, String name) {
        SparStore store = SparStore.get();
        SparStore.MentorBond bond = store.bondsByPlayer.remove(uuid);
        store.recentSessions.remove(uuid);
        // Break reverse mentor/apprentice pointers
        int reverse = 0;
        if (bond != null) {
            bond.normalizeApprentices();
            if (bond.mentorUuid != null && !bond.mentorUuid.isBlank()) {
                SparStore.MentorBond mentor = store.bondsByPlayer.get(bond.mentorUuid);
                if (mentor != null) {
                    mentor.normalizeApprentices();
                    if (mentor.removeApprenticeUuid(uuid)) {
                        reverse++;
                    }
                }
            }
            for (SparStore.ApprenticeRef appRef : new ArrayList<>(bond.apprentices)) {
                if (appRef == null || appRef.uuid == null || appRef.uuid.isBlank()) {
                    continue;
                }
                SparStore.MentorBond app = store.bondsByPlayer.get(appRef.uuid);
                if (app != null && uuid.equalsIgnoreCase(app.mentorUuid)) {
                    app.mentorUuid = "";
                    app.mentorName = "";
                    reverse++;
                }
            }
        }
        int invites = 0;
        if (store.invites.remove(uuid) != null) {
            invites++;
        }
        Iterator<Map.Entry<String, SparStore.BondInvite>> invIt =
                store.invites.entrySet().iterator();
        while (invIt.hasNext()) {
            SparStore.BondInvite inv = invIt.next().getValue();
            if (inv != null && uuid.equalsIgnoreCase(inv.fromUuid)) {
                invIt.remove();
                invites++;
            }
        }
        SparStore.LeaderboardEntry lb = store.leaderboard.remove(uuid);
        int nameKeys = 0;
        if (name != null && !name.isBlank()) {
            String safe = name.replaceAll("[^A-Za-z0-9_\\-]", "_").toLowerCase(Locale.ROOT);
            if (store.leaderboard.remove("name:" + safe) != null) {
                nameKeys++;
            }
            // Also drop entries keyed only by matching display name
            Iterator<Map.Entry<String, SparStore.LeaderboardEntry>> it =
                    store.leaderboard.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, SparStore.LeaderboardEntry> e = it.next();
                SparStore.LeaderboardEntry ent = e.getValue();
                if (ent != null && ent.name != null && ent.name.equalsIgnoreCase(name)) {
                    it.remove();
                    nameKeys++;
                }
            }
        }
        store.tpMessages.remove(uuid);
        store.mentorTpMessages.remove(uuid);
        int dojo = scrubDojo(store, uuid);
        store.markDirty();
        store.save();
        return "§7Spar: " + (bond != null ? "§abond cleared" : "§eno bond")
                + " §8· LB §f" + (lb != null ? "removed" : "none")
                + " §8· name keys §f" + nameKeys
                + " §8· invites §f" + invites
                + " §8· reverse §f" + reverse
                + " §8· dojo §f" + dojo;
    }

    /** Drop this uuid from the live dojo season, profiles, and wars. */
    private static int scrubDojo(SparStore store, String uuid) {
        int n = 0;
        if (store.dojoProfiles.remove(uuid) != null) {
            n++;
        }
        if (store.dojoSeason != null && store.dojoSeason.leaderboard != null) {
            if (store.dojoSeason.leaderboard.remove(uuid) != null) {
                n++;
            }
            for (SparStore.DojoEntry entry : store.dojoSeason.leaderboard.values()) {
                if (entry == null || entry.members == null) {
                    continue;
                }
                if (entry.members.remove(uuid) != null) {
                    n++;
                }
            }
        }
        Iterator<Map.Entry<String, SparStore.DojoChallenge>> wars =
                store.dojoChallenges.entrySet().iterator();
        while (wars.hasNext()) {
            SparStore.DojoChallenge war = wars.next().getValue();
            if (war == null) {
                continue;
            }
            if (uuid.equalsIgnoreCase(war.fromMentorUuid)
                    || uuid.equalsIgnoreCase(war.fromDojoUuid)
                    || uuid.equalsIgnoreCase(war.toDojoUuid)) {
                wars.remove();
                n++;
            }
        }
        return n;
    }

    private static String clearDifficulty(ServerPlayer online) {
        if (online == null) {
            return "§7Difficulty: §eskipped (player offline)";
        }
        PlayerDifficultyData data = DifficultyCache.data(online);
        data.resetTemporary();
        // Full staff wipe of unlock ladder + titles for this character.
        for (Integer id : new ArrayList<>(data.getUnlockedTiers())) {
            data.revokeTier(id);
        }
        try {
            TitleSystem.clear(online);
        } catch (Throwable ignored) {
        }
        DmzProgression.clearBaseFormLevel(online.m_20148_());
        DifficultyCache.save(online);
        DifficultyCache.refresh(online);
        DifficultyCache.invalidate(online.m_20148_());
        try {
            ScaledMobTracker.releaseAndRevertPlayer(online);
            NearbyMobScaler.processEvictions();
            AreaDifficulty.clearCache();
        } catch (Throwable ignored) {
        }
        return "§7Difficulty: §aactive + unlocks + titles cleared";
    }

    private static String clearProgression(ServerPlayer online, String uuid) {
        try {
            UUID id = UUID.fromString(uuid);
            ProgressionData.clearPlayer(id);
            try {
                com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeSystem.clearPlayer(id);
            } catch (Throwable ignored) {
            }
            try {
                com.dbzlegacy.adaptivedifficulty.progression.combat.KiWeapons.clearPlayer(id);
                com.dbzlegacy.adaptivedifficulty.progression.combat.PiercingBonus.clearPlayer(id);
            } catch (Throwable ignored) {
            }
            try {
                com.dbzlegacy.adaptivedifficulty.progression.dummy.ShadowDummyLimiter.clearPlayer(id);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        if (online == null) {
            return "§7Progression: §atemp cleared";
        }
        try {
            com.dbzlegacy.adaptivedifficulty.progression.ProgressionSystem.onLogout(online);
        } catch (Throwable ignored) {
        }
        // Wipe the lm_progression bag (flight / meditation / potential / migrate mark) in the LM file.
        try {
            ProgressionData.storedClearAll(online);
        } catch (Throwable ignored) {
        }
        // Allow CNPC→LM player migrate to run again if CNPC keys remain.
        ProgressionData.storedRemove(online, "lm_cnpc_player_migrated");
        ProgressionData.clearPlayer(online.m_20148_());
        LmPlayerStore.flush(online.m_20148_());
        return "§7Progression: §aNBT + temp cleared (re-migrate allowed)";
    }

    /** LM-owned records for someone who is not online (the source of truth since 4.6.253). */
    private static String clearOfflineLmFile(String uuid, boolean difficulty, boolean progression) {
        UUID id;
        try {
            id = UUID.fromString(uuid);
        } catch (Throwable t) {
            return "§7LM file: §eskipped";
        }
        CompoundTag root = LmPlayerStore.root(id);
        boolean changed = false;
        if (difficulty && root.m_128441_(PlayerDifficultyData.NBT_ROOT)) {
            root.m_128473_(PlayerDifficultyData.NBT_ROOT);
            changed = true;
        }
        if (progression) {
            for (String key : OFFLINE_PROGRESSION_KEYS) {
                if (root.m_128441_(key)) {
                    root.m_128473_(key);
                    changed = true;
                }
            }
        }
        LmPlayerStore.unload(id);
        return changed ? "§7LM file: §acleared" : "§7LM file: §enothing to clear";
    }

    private static final String[] OFFLINE_PROGRESSION_KEYS = {
            "lm_progression",
            "lm_personal_level_cap",
            "lm_personal_breakthroughs",
            "pp_level_breakthroughs"
    };

    /**
     * Saved player file for someone who is not online. Removes difficulty and
     * progression keys from Forge persistent data. Spar and rival stores are
     * separate files and do not use this path.
     */
    private static String clearOfflinePlayerFile(
            MinecraftServer server, String uuid, boolean difficulty, boolean progression
    ) {
        if (server == null || uuid == null || uuid.isBlank()) {
            return "§7Player file: §eskipped";
        }
        Path file;
        try {
            file = server.m_129843_(LevelResource.f_78176_).resolve(uuid + ".dat");
        } catch (Throwable t) {
            return "§7Player file: §c" + t;
        }
        if (!Files.isRegularFile(file)) {
            return "§7Player file: §eno saved data";
        }
        File dat = file.toFile();
        CompoundTag root;
        try {
            root = NbtIo.m_128937_(dat);
        } catch (Throwable t) {
            return "§7Player file: §cunreadable";
        }
        if (root == null || !root.m_128441_("ForgeData")) {
            return "§7Player file: §enothing stored";
        }
        CompoundTag forge = root.m_128469_("ForgeData");
        boolean changed = false;
        if (difficulty && forge.m_128441_("dmz_adaptive_difficulty")) {
            forge.m_128473_("dmz_adaptive_difficulty");
            changed = true;
        }
        if (progression) {
            for (String key : OFFLINE_PROGRESSION_KEYS) {
                if (forge.m_128441_(key)) {
                    forge.m_128473_(key);
                    changed = true;
                }
            }
        }
        if (!changed) {
            return "§7Player file: §enothing to clear";
        }
        root.m_128365_("ForgeData", forge);
        Path tmp = file.resolveSibling(uuid + ".dat.lm-clear");
        try {
            NbtIo.m_128944_(root, tmp.toFile());
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Throwable atomic) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable t) {
            try {
                Files.deleteIfExists(tmp);
            } catch (Throwable ignored) {
            }
            return "§7Player file: §cwrite failed";
        }
        return "§7Player file: §acleared";
    }

    private static Lookup lookup(MinecraftServer server, String raw) {
        try {
            UUID id = UUID.fromString(raw);
            ServerPlayer online = server == null ? null : server.m_6846_().m_11259_(id);
            String name = online != null ? online.m_6302_() : nameForUuid(id.toString());
            return Lookup.ok(new Resolved(id.toString(), name == null ? raw : name, online));
        } catch (IllegalArgumentException ignored) {
        }

        if (server != null) {
            ServerPlayer exact = server.m_6846_().m_11255_(raw);
            if (exact != null) {
                return Lookup.ok(new Resolved(exact.m_20148_().toString(), exact.m_6302_(), exact));
            }
            String want = raw.toLowerCase(Locale.ROOT);
            for (ServerPlayer p : server.m_6846_().m_11314_()) {
                if (p.m_6302_().toLowerCase(Locale.ROOT).equals(want)) {
                    return Lookup.ok(new Resolved(p.m_20148_().toString(), p.m_6302_(), p));
                }
            }
        }

        Map<String, String> hits = findOfflineByName(server, raw);
        if (hits.size() == 1) {
            Map.Entry<String, String> only = hits.entrySet().iterator().next();
            return Lookup.ok(new Resolved(only.getKey(), only.getValue(), null));
        }
        if (hits.size() > 1) {
            StringBuilder sb = new StringBuilder("§cMore than one player is named §f").append(raw);
            sb.append("\n§7Pass a UUID:");
            for (Map.Entry<String, String> e : hits.entrySet()) {
                sb.append("\n§8").append(e.getKey());
            }
            return Lookup.fail(sb.toString());
        }
        return Lookup.fail("§cPlayer not found: §f" + raw
                + "\n§8Use the in-game name or UUID. Offline names match sparring, rivals, or the server player cache.");
    }

    private static String nameForUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        RivalPlayerRecord rec = RivalStore.get().get(uuid);
        if (rec != null && rec.name != null && !rec.name.isBlank()) {
            return rec.name;
        }
        SparStore store = SparStore.get();
        SparStore.LeaderboardEntry lb = store.leaderboard.get(uuid);
        if (lb != null && lb.name != null && !lb.name.isBlank()) {
            return lb.name;
        }
        if (store.dojoSeason != null && store.dojoSeason.leaderboard != null) {
            SparStore.DojoEntry dojo = store.dojoSeason.leaderboard.get(uuid);
            if (dojo != null && dojo.mentorName != null && !dojo.mentorName.isBlank()) {
                return dojo.mentorName;
            }
            for (SparStore.DojoEntry entry : store.dojoSeason.leaderboard.values()) {
                if (entry == null || entry.members == null) {
                    continue;
                }
                SparStore.DojoMemberStats member = entry.members.get(uuid);
                if (member != null && member.name != null && !member.name.isBlank()) {
                    return member.name;
                }
            }
        }
        for (SparStore.MentorBond bond : store.bondsByPlayer.values()) {
            if (bond == null) {
                continue;
            }
            bond.normalizeApprentices();
            for (SparStore.ApprenticeRef ref : bond.apprentices) {
                if (ref != null && uuid.equalsIgnoreCase(ref.uuid)
                        && ref.name != null && !ref.name.isBlank()) {
                    return ref.name;
                }
            }
        }
        return null;
    }

    /** uuid → display name. Exact name match, case-insensitive. */
    private static Map<String, String> findOfflineByName(MinecraftServer server, String raw) {
        String want = raw.toLowerCase(Locale.ROOT);
        Map<String, String> hits = new LinkedHashMap<>();
        for (RivalPlayerRecord rec : RivalStore.get().players.values()) {
            if (rec != null && rec.uuid != null && rec.name != null
                    && rec.name.toLowerCase(Locale.ROOT).equals(want)) {
                hits.putIfAbsent(rec.uuid, rec.name);
            }
        }
        SparStore store = SparStore.get();
        for (Map.Entry<String, SparStore.LeaderboardEntry> e : store.leaderboard.entrySet()) {
            SparStore.LeaderboardEntry ent = e.getValue();
            if (e.getKey() == null || ent == null || ent.name == null) {
                continue;
            }
            if (!ent.name.toLowerCase(Locale.ROOT).equals(want)) {
                continue;
            }
            if (looksLikeUuid(e.getKey())) {
                hits.putIfAbsent(e.getKey(), ent.name);
            }
        }
        if (store.dojoSeason != null && store.dojoSeason.leaderboard != null) {
            for (Map.Entry<String, SparStore.DojoEntry> e : store.dojoSeason.leaderboard.entrySet()) {
                SparStore.DojoEntry ent = e.getValue();
                if (ent == null) {
                    continue;
                }
                if (ent.mentorName != null && ent.mentorName.toLowerCase(Locale.ROOT).equals(want)
                        && looksLikeUuid(e.getKey())) {
                    hits.putIfAbsent(e.getKey(), ent.mentorName);
                }
                if (ent.members == null) {
                    continue;
                }
                for (SparStore.DojoMemberStats member : ent.members.values()) {
                    if (member != null && member.uuid != null && member.name != null
                            && member.name.toLowerCase(Locale.ROOT).equals(want)) {
                        hits.putIfAbsent(member.uuid, member.name);
                    }
                }
            }
        }
        for (SparStore.MentorBond bond : store.bondsByPlayer.values()) {
            if (bond == null) {
                continue;
            }
            bond.normalizeApprentices();
            for (SparStore.ApprenticeRef ref : bond.apprentices) {
                if (ref != null && ref.uuid != null && ref.name != null
                        && ref.name.toLowerCase(Locale.ROOT).equals(want)) {
                    hits.putIfAbsent(ref.uuid, ref.name);
                }
            }
        }
        for (Map.Entry<String, List<SparStore.RecentSession>> e : store.recentSessions.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            for (SparStore.RecentSession session : e.getValue()) {
                if (session != null && session.partnerUuid != null && session.partnerName != null
                        && session.partnerName.toLowerCase(Locale.ROOT).equals(want)) {
                    hits.putIfAbsent(session.partnerUuid, session.partnerName);
                }
            }
        }
        if (server != null) {
            try {
                var cache = server.m_129927_();
                if (cache != null) {
                    cache.m_10996_(raw).ifPresent(profile -> {
                        if (profile != null && profile.getId() != null && profile.getName() != null
                                && profile.getName().equalsIgnoreCase(raw)) {
                            hits.putIfAbsent(profile.getId().toString(), profile.getName());
                        }
                    });
                }
            } catch (Throwable ignored) {
            }
        }
        return hits;
    }

    private static boolean looksLikeUuid(String key) {
        if (key == null) {
            return false;
        }
        try {
            UUID.fromString(key);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private record Resolved(String uuid, String name, ServerPlayer online) {}

    private record Lookup(Resolved resolved, String error) {
        static Lookup ok(Resolved resolved) {
            return new Lookup(resolved, null);
        }

        static Lookup fail(String error) {
            return new Lookup(null, error);
        }
    }
}
