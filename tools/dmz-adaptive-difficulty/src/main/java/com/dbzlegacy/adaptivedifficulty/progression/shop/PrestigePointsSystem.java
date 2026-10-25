package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.progression.DmzSkillUtil;
import com.dbzlegacy.adaptivedifficulty.gui.GuiClickConfirm;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.progression.PersonalLevelCapMirror;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dragonminez.common.stats.skills.Skills;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;

/**
 * Prestige Points shop: turn in held prestiges for points, spend on DMZ skill levels,
 * Permanent Majin / Mutant / Ultimate, and personal level-cap breakthroughs.
 *
 * <p>Turn-in amounts: {@code 1, 2, 3, 6, 9} only.
 * Points: {@code N + T(N/3)} where {@code T(k)=k(k+1)/2}
 * (3→4, 6→9, 9→15).
 * Skills: 1 point = +1 permanent skill level for Skill Check skills only
 * (Natural + Saga — not ultimates / ki attacks). Survives prestige reset.
 * {@code potentialunlock} is the exception: 1 point = +2 levels.
 * Caps come from {@code skills.json} cost ladders.
 * Majin/Mutant: 5 points each, mutually exclusive; unpurchase the current form
 * before buying the other (no refund, no silent swap).
 * Ultimate: 3 points and independent — it stacks with Majin or Mutant, so neither
 * the Ultimate buy checks those forms nor a Majin/Mutant buy checks Ultimate.
 * Removing Ultimate refunds all 3 points and never touches Majin/Mutant.
 * Breakthroughs: raise <b>your</b> DMZ level cap by +10k (max 5 → 150k) so you can
 * level normally into the new cap. Server {@code maxValue} is 150k (client UI/level
 * math); personal soft-locks keep everyone else at 100k. Costs 15, 20, 25, 30, 35.
 * Difficulty tiers: permanent unlock with prestige points —
 * T1–2 = 1pt, T3–4 = 2pt, T5–6 = 3pt, T7 = 4pt ({@code (tier+1)/2}).
 *
 * <p>Since 4.6.228.1 the exchange, skill shop, tier shop, Majin / Mutant / Ultimate and
 * God Ki prices come from {@link PrestigeShopConfig} ({@code prestige-shop.json}); the
 * numbers above and the constants below are its defaults. Majin / Mutant removal refunds
 * {@code refundPercent} of the price (default 0), Ultimate defaults to a full refund.
 * Breakthroughs are not configurable. {@link PrestigeCustomShop} ("Special perks") is
 * reapplied alongside every other purchase here.
 */
public final class PrestigePointsSystem {
    public static final int BASE_LEVEL_CAP = 100_000;
    public static final int BREAKTHROUGH_STEP = 10_000;
    public static final int MAX_BREAKTHROUGHS = 5;
    public static final int ABSOLUTE_LEVEL_CAP = BASE_LEVEL_CAP + MAX_BREAKTHROUGHS * BREAKTHROUGH_STEP;
    /** Default Majin / Mutant price ({@code forms.majin.cost} / {@code forms.mutant.cost}). */
    public static final int FORM_COST = 5;
    /**
     * Default prestige-point cost of Permanent Ultimate ({@code forms.ultimate.cost}) —
     * independent of the Majin/Mutant pair.
     */
    public static final int ULTIMATE_COST = 3;
    /** Default skill-shop price per purchase ({@code skillShop.costPerPurchase}). */
    public static final int SKILL_POINT_COST = 1;
    /** Default levels granted per point for Potential Unlock in the prestige shop. */
    public static final int POTENTIAL_UNLOCK_LEVELS_PER_POINT = 2;
    /** Skills shown per prestige shop inventory page. */
    public static final int SKILL_SHOP_PAGE_SIZE = 21;

    private static final String KEY_POINTS = "prestige_points";
    /** Root LongTag backup — same Mohist pattern as breakthroughs ({@code lm_personal_*}). */
    public static final String ROOT_POINTS = "lm_prestige_points";
    private static final String KEY_BREAKTHROUGHS = "pp_level_breakthroughs";
    private static final String KEY_MAJIN = "pp_perm_majin";
    private static final String KEY_MUTANT = "pp_perm_mutant";
    /** Permanent Ultimate — own flag, never part of the Majin/Mutant exclusivity. */
    private static final String KEY_ULTIMATE = "pp_perm_ultimate";
    /**
     * DMZ skill id that actually grants the Ultimate form group. Old Kai writes
     * {@code skills.setSkillLevel("ultimate", 1)}; the ultimate stack-form group
     * ({@code forms/ultimate.json}) unlocks on that skill level. The DMZ effects list has no
     * Ultimate handler, so an {@code effects.addEffect("ultimate", ...)} entry is dead data —
     * Ultimate is granted here, never via {@link #runDmzEffect}.
     */
    private static final String DMZ_SKILL_ULTIMATE = "ultimate";
    private static final String KEY_SKILL_PREFIX = "pp_skill_";
    /** Permanent difficulty-tier unlocks bought with prestige points ({@code pp_tier_1}…{@code 7}). */
    private static final String KEY_TIER_PREFIX = "pp_tier_";

    /**
     * Legacy named map kept for callers/audits — resolves to live catalog entries.
     * Prefer {@link #skillOffers()} / {@link #resolveOffer(String)}.
     */
    public static final Map<String, SkillOffer> SKILL_OFFERS = new LinkedHashMap<>();

    static {
        // Seed with Skill Check Natural skills so static audits / early boot still see entries.
        for (String id : DmzSkillUtil.SKILL_CHECK_NATURAL) {
            SkillOffer offer = resolveOffer(id);
            if (offer != null) {
                SKILL_OFFERS.put(id, offer);
            }
        }
    }

    private PrestigePointsSystem() {}

    public record SkillOffer(String id, String label, int maxLevel) {}

    /**
     * Resolve a purchasable offer — Skill Check Natural/Saga skills, plus any
     * {@code skillShop.extraSkills}. {@code skillShop.maxLevelOverrides} wins over skills.json.
     */
    public static SkillOffer resolveOffer(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return null;
        }
        String id = skillId.toLowerCase(Locale.ROOT).trim();
        PrestigeShopConfig.SkillShop shop = PrestigeShopConfig.get().skillShop;
        boolean extra = shop.extraSkills.contains(id);
        if (!(DmzSkillUtil.isSkillCheckSkill(id) || extra) || DmzSkillUtil.isFormSkill(id)) {
            return null;
        }
        Integer maxOverride = shop.maxLevelOverrides.get(id);
        int max = maxOverride != null && maxOverride > 0
                ? maxOverride
                : DmzSkillUtil.configuredMaxLevel(id);
        if (max <= 0 && extra) {
            // Extra skill with no skills.json ladder — sell 10 levels.
            max = 10;
        }
        if (max <= 0) {
            // Still allow known Skill Check ids before config is loaded.
            max = switch (id) {
                case "potentialunlock" -> 30;
                case "fusion", "kaioken" -> 5;
                case "kiboost" -> 4;
                case "kicontrol" -> 1;
                case "meditation", "fly", "sprint", "jump",
                     "kimanipulation", "kisense", "defense_penetration",
                     "healing_reduction", "instant_transmission",
                     "ki_infusion", "kiprotection" -> 10;
                default -> 0;
            };
        }
        if (max <= 0) {
            return null;
        }
        return new SkillOffer(id, DmzSkillUtil.prettySkillLabel(id), max);
    }

    /**
     * Prestige shop catalog — Skill Check Natural + Saga (no ultimate / attacks), plus
     * {@code skillShop.extraSkills}, minus {@code skillShop.hiddenSkills}.
     */
    public static List<SkillOffer> skillOffers() {
        PrestigeShopConfig.SkillShop shop = PrestigeShopConfig.get().skillShop;
        List<String> ids = new ArrayList<>(DmzSkillUtil.skillCheckSkillIds());
        for (String extra : shop.extraSkills) {
            if (!ids.contains(extra)) {
                ids.add(extra);
            }
        }
        List<SkillOffer> out = new ArrayList<>();
        for (String id : ids) {
            SkillOffer offer = resolveOffer(id);
            if (offer != null && !shop.hiddenSkills.contains(offer.id())) {
                out.add(offer);
            }
        }
        if (out.isEmpty()) {
            // Config unavailable — fall back to seeded natural skills.
            for (SkillOffer offer : SKILL_OFFERS.values()) {
                if (!shop.hiddenSkills.contains(offer.id())) {
                    out.add(offer);
                }
            }
        }
        return out;
    }

    public static int skillShopPageCount() {
        int n = skillOffers().size();
        return Math.max(1, (n + SKILL_SHOP_PAGE_SIZE - 1) / SKILL_SHOP_PAGE_SIZE);
    }

    public static List<SkillOffer> skillOffersPage(int pageIndex) {
        List<SkillOffer> all = skillOffers();
        int pages = Math.max(1, (all.size() + SKILL_SHOP_PAGE_SIZE - 1) / SKILL_SHOP_PAGE_SIZE);
        int page = Math.max(0, Math.min(pages - 1, pageIndex));
        int from = page * SKILL_SHOP_PAGE_SIZE;
        if (from >= all.size()) {
            return List.of();
        }
        int to = Math.min(all.size(), from + SKILL_SHOP_PAGE_SIZE);
        return all.subList(from, to);
    }

    // ── Points balance ─────────────────────────────────────────────────

    public static int getPoints(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        int fromBag = Math.max(0, (int) ProgressionData.storedGetLong(player, KEY_POINTS, 0L));
        long fromRoot = PersistentDataAccess.getLong(player, ROOT_POINTS, -1L);
        int n = fromBag;
        if (fromRoot > n) {
            n = (int) Math.min(Integer.MAX_VALUE, fromRoot);
        }
        if (n != fromBag && ProgressionData.storedWritable(player)) {
            ProgressionData.storedPut(player, KEY_POINTS, n);
            PersistentDataAccess.putLong(player, ROOT_POINTS, n);
        } else if (fromRoot < 0 && n > 0) {
            PersistentDataAccess.putLong(player, ROOT_POINTS, n);
        }
        return n;
    }

    public static void setPoints(ServerPlayer player, int points) {
        if (player == null) {
            return;
        }
        int n = Math.max(0, points);
        ProgressionData.storedPut(player, KEY_POINTS, n);
        PersistentDataAccess.putLong(player, ROOT_POINTS, n);
    }

    /** True when the player can afford {@code amount} prestige points. */
    public static boolean hasPoints(ServerPlayer player, int amount) {
        return player != null && amount >= 0 && getPoints(player) >= amount;
    }

    /**
     * Spend prestige points for external shops / NPCs.
     * @return {@code true} if deducted; {@code false} if insufficient or invalid
     */
    public static boolean trySpend(ServerPlayer player, int amount, String reason) {
        if (player == null || amount <= 0) {
            if (player != null) {
                ProgressionData.tempPut(player, "pp_last_spend_ok", "false");
            }
            return false;
        }
        int points = getPoints(player);
        if (points < amount) {
            ProgressionData.tempPut(player, "pp_last_spend_ok", "false");
            return false;
        }
        setPoints(player, points - amount);
        ProgressionData.tempPut(player, "pp_last_spend_ok", "true");
        SystemTelemetry.log("prestige_points", "spend", player, null, Map.of(
                "amount", amount,
                "reason", reason == null ? "" : reason,
                "points", getPoints(player)
        ));
        return true;
    }

    /** Grant prestige points (admin / quest rewards). */
    public static void grantPoints(ServerPlayer player, int amount, String reason) {
        if (player == null || amount <= 0) {
            return;
        }
        int balance = getPoints(player) + amount;
        setPoints(player, balance);
        SystemTelemetry.log("prestige_points", "grant", player, null, Map.of(
                "amount", amount,
                "reason", reason == null ? "" : reason,
                "points", balance
        ));
    }

    public static String spendForNpc(ServerPlayer player, int amount, String reason) {
        if (player == null) {
            return "§cPlayers only.";
        }
        if (amount <= 0) {
            return "§cAmount must be positive.";
        }
        if (!trySpend(player, amount, reason)) {
            return "§cYou need §e" + amount + " §cprestige favor. You have §e" + getPoints(player) + "§c.";
        }
        String tag = reason == null || reason.isBlank() ? "shop" : reason.trim();
        return "§aSpent §e" + amount + " §afavor"
                + " §7(" + tag + ") · Balance: §e" + getPoints(player);
    }

    public static String grantForNpc(ServerPlayer player, int amount, String reason) {
        if (player == null) {
            return "§cPlayers only.";
        }
        if (amount <= 0) {
            return "§cAmount must be positive.";
        }
        grantPoints(player, amount, reason);
        return "§aGranted §e" + amount + " §aprestige favor"
                + " · Balance: §e" + getPoints(player);
    }

    /** Default single turn-in pack sizes ({@code exchange.amounts}); live list is {@link #turnInAmounts()}. */
    public static final int[] TURN_IN_AMOUNTS = {1, 2, 3, 6, 9};

    public static boolean isAllowedTurnInAmount(int amount) {
        return PrestigeShopConfig.get().exchange.amounts.contains(amount);
    }

    /** Configured single turn-in pack sizes (GUI + command). */
    public static int[] turnInAmounts() {
        List<Integer> amounts = PrestigeShopConfig.get().exchange.amounts;
        int[] out = new int[amounts.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = amounts.get(i);
        }
        return out;
    }

    /**
     * Points for turning in {@code amount} prestiges in one action: a configured payout
     * override, else {@code N + triangular(N/bonusGroupSize)} → with the default group of 3:
     * 1→1, 2→2, 3→4, 6→9, 9→15.
     */
    public static int pointsForTurnIn(int amount) {
        PrestigeShopConfig.Exchange exchange = PrestigeShopConfig.get().exchange;
        Integer override = exchange.payoutOverrides == null
                ? null : exchange.payoutOverrides.get(String.valueOf(amount));
        if (override != null) {
            return Math.max(0, override);
        }
        int n = Math.max(0, amount);
        int packs = exchange.bonusGroupSize > 0 ? n / exchange.bonusGroupSize : 0;
        int bonus = packs * (packs + 1) / 2;
        return n + bonus;
    }

    /** "§f1§7, §f2§7, ... or §f9" for the turn-in usage message. */
    private static String turnInAmountsText() {
        List<Integer> amounts = PrestigeShopConfig.get().exchange.amounts;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < amounts.size(); i++) {
            if (i > 0) {
                sb.append(i == amounts.size() - 1 ? "§7, or " : "§7, ");
            }
            sb.append("§f").append(amounts.get(i));
        }
        return sb.toString();
    }

    /**
     * Turn in {@code amount} held prestiges for points.
     * The LM held wallet is the only prestige count.
     */
    public static String turnIn(ServerPlayer player, int amount) {
        if (player == null) {
            return "§cPlayers only.";
        }
        int held = PrestigeSystem.getHeld(player);
        int want = Math.max(0, amount);
        if (!PrestigeShopConfig.get().exchange.enabled) {
            return "§cPrestige exchange is closed right now.";
        }
        if (want <= 0) {
            return "§cChoose how many prestiges to turn in.";
        }
        if (!isAllowedTurnInAmount(want)) {
            return "§cTurn in " + turnInAmountsText() + " §cprestiges at a time.";
        }
        if (want > held) {
            return "§cYour current prestige amount is only §6" + held + "§c. Exchange fewer, or prestige again first.";
        }
        int gained = pointsForTurnIn(want);
        int newHeld = held - want;
        PrestigeSystem.setHeldPublic(player, newHeld);
        int balance = getPoints(player) + gained;
        setPoints(player, balance);
        SystemTelemetry.log("prestige_points", "turn_in", player, null, Map.of(
                "amount", want,
                "gained", gained,
                "held", newHeld,
                "points", balance
        ));
        return "§aTurned in §6" + want + " §aprestige" + (want == 1 ? "" : "s")
                + " §7→ §e+" + gained + " §7favor"
                + " §8(+" + (gained - want) + " bonus)"
                + "\n§7Prestige favor: §e" + balance + " §7· Current prestige amount: §6" + newHeld;
    }

    // ── Skill upgrades ─────────────────────────────────────────────────

    public static int getPurchasedSkillLevels(ServerPlayer player, String skillId) {
        if (player == null || skillId == null || skillId.isBlank()) {
            return 0;
        }
        return Math.max(0, (int) ProgressionData.storedGetLong(
                player, KEY_SKILL_PREFIX + skillId.toLowerCase(Locale.ROOT), 0L));
    }

    /**
     * Staff: set the prestige-invested skill floor (levels bought in the prestige shop).
     * Does not change the wallet. Re-applies the live DMZ skill to match.
     */
    public static String adminAdjustSkill(
            ServerPlayer player, String skillId, String mode, int amount
    ) {
        if (player == null) {
            return "§cPlayer not online.";
        }
        SkillOffer offer = resolveOffer(skillId);
        if (offer == null) {
            // Allow adjusting a legacy stored id even if it left the catalog.
            String id = skillId == null ? "" : skillId.toLowerCase(Locale.ROOT).trim();
            if (id.isBlank()) {
                return "§cUsage: skill <player> <skillId> <set|add|remove> <levels>"
                        + "\n§8Example: skill Steve potentialunlock set 10";
            }
            int max = DmzSkillUtil.configuredMaxLevel(id);
            if (max <= 0) {
                max = Math.max(getPurchasedSkillLevels(player, id), 10);
            }
            offer = new SkillOffer(id, DmzSkillUtil.prettySkillLabel(id), max);
        }
        if (!ProgressionData.storedWritable(player)) {
            return "§cCould not save prestige data for §f" + player.m_6302_() + "§c.";
        }
        int before = getPurchasedSkillLevels(player, offer.id);
        Integer next = applyIntMode(before, mode, amount, 0, offer.maxLevel);
        if (next == null) {
            return "§cUsage: skill <player> <skillId> <set|add|remove> <levels>"
                    + "\n§8Example: skill Steve potentialunlock add 2";
        }
        ProgressionData.storedPut(player, KEY_SKILL_PREFIX + offer.id.toLowerCase(Locale.ROOT), next);
        Skills skills = DmzSkillUtil.skills(player);
        int liveBefore = 0;
        int liveAfter = 0;
        if (skills != null) {
            DmzSkillUtil.ensureRegistered(skills, offer.id, offer.maxLevel);
            liveBefore = DmzSkillUtil.level(skills, offer.id);
            int max = Math.max(offer.maxLevel, DmzSkillUtil.maxLevel(skills, offer.id, offer.maxLevel));
            if (next > before) {
                liveAfter = Math.min(max, Math.max(liveBefore, next));
            } else if (next < before && liveBefore > next) {
                // Lowering invested floor — clamp live skill down with it.
                liveAfter = Math.min(max, next);
            } else {
                liveAfter = liveBefore;
            }
            if (liveAfter != liveBefore) {
                DmzSkillUtil.setLevel(skills, offer.id, liveAfter);
                DmzSkillUtil.sync(player);
            }
        }
        reapplySkillBonuses(player);
        SystemTelemetry.log("prestige_admin", "admin_skill", player, null, Map.of(
                "skill", offer.id,
                "mode", mode == null ? "" : mode,
                "before", before,
                "after", next,
                "live_before", liveBefore,
                "live_after", liveAfter
        ));
        String perPoint = levelsPerPoint(offer.id) > 1
                ? " §8(" + levelsPerPoint(offer.id) + " levels per favor in shop)"
                : "";
        return "§a" + offer.label + " §7kept level §f" + before + " §7→ §f" + next
                + " §8/ §f" + offer.maxLevel + perPoint
                + "\n§7Live skill §f" + liveBefore + " §7→ §f" + liveAfter
                + " §8(" + player.m_6302_() + ")";
    }

    /** Skills with a prestige-invested floor &gt; 0 (for admin info). */
    public static List<String> investedSkillSummary(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        for (SkillOffer offer : skillOffers()) {
            int bought = getPurchasedSkillLevels(player, offer.id);
            if (bought > 0) {
                out.add(offer.label + " §f" + bought + "§7/§f" + offer.maxLevel);
            }
        }
        return out;
    }

    private static Integer applyIntMode(int current, String mode, int amount, int min, int max) {
        if (mode == null) {
            return null;
        }
        String m = mode.toLowerCase(Locale.ROOT).trim();
        int next;
        switch (m) {
            case "set" -> next = amount;
            case "add" -> next = current + amount;
            case "remove", "take", "sub" -> next = current - amount;
            default -> {
                return null;
            }
        }
        return Math.max(min, Math.min(max, next));
    }

    /**
     * Buy prestige-invested skill floor levels.
     * Gated only on the stored prestige floor vs catalog max — live DMZ skill
     * level may already be maxed from training; players can still raise the
     * permanent floor that survives prestige reset.
     */
    public static String buySkillLevel(ServerPlayer player, String skillId) {
        if (player == null) {
            return "§cPlayers only.";
        }
        SkillOffer offer = resolveOffer(skillId);
        PrestigeShopConfig.SkillShop shop = PrestigeShopConfig.get().skillShop;
        if (!shop.enabled) {
            return "§cThe skill shop is closed right now.";
        }
        if (offer == null || shop.hiddenSkills.contains(offer.id)) {
            return "§cOnly Skill Check skills are purchasable here (Natural + Saga).";
        }
        int purchased = getPurchasedSkillLevels(player, offer.id);
        int floorMax = Math.max(1, offer.maxLevel);
        int cost = shop.costFor(offer.id);
        if (purchased >= floorMax) {
            return "§c" + offer.label + " kept level is maxed (§f" + floorMax + "§c).";
        }
        int points = getPoints(player);
        if (points < cost) {
            return "§cNeed §e" + cost + " §cfavor (have §e" + points + "§c).";
        }
        int levelsPerPoint = levelsPerPoint(offer.id);
        int room = Math.max(0, floorMax - purchased);
        if (room <= 0) {
            return "§c" + offer.label + " kept level is maxed (§f" + floorMax + "§c).";
        }
        int gain = Math.min(levelsPerPoint, room);
        setPoints(player, points - cost);
        int nextPurchased = Math.min(floorMax, purchased + gain);
        ProgressionData.storedPut(player, KEY_SKILL_PREFIX + offer.id.toLowerCase(Locale.ROOT), nextPurchased);

        // Raise live skill only when below the new floor; never block the floor buy.
        int liveBefore = 0;
        int liveAfter = 0;
        Skills skills = DmzSkillUtil.skills(player);
        if (skills != null) {
            DmzSkillUtil.ensureRegistered(skills, offer.id, floorMax);
            liveBefore = DmzSkillUtil.level(skills, offer.id);
            int liveMax = Math.max(floorMax, DmzSkillUtil.maxLevel(skills, offer.id, floorMax));
            liveAfter = Math.min(liveMax, Math.max(liveBefore, nextPurchased));
            if (liveAfter != liveBefore) {
                DmzSkillUtil.setLevel(skills, offer.id, liveAfter);
                DmzSkillUtil.sync(player);
            }
        }
        SystemTelemetry.log("prestige_points", "buy_skill", player, null, Map.of(
                "skill", offer.id,
                "purchased", nextPurchased,
                "level", liveAfter,
                "gain", gain,
                "points", getPoints(player)
        ));
        String liveNote = liveAfter > liveBefore
                ? " → §fLv " + liveAfter
                : " §8(live already §f" + liveBefore + "§8)";
        return "§a+" + gain + " §7" + offer.label + " kept level §f" + purchased
                + " §7→ §f" + nextPurchased + liveNote
                + " §8(survives prestige, -" + cost + " favor)"
                + "\n§7Favor left: §e" + getPoints(player);
    }

    /**
     * Prestige-shop levels gained per purchase for {@code skillId} — {@code skillShop}
     * override, else {@code levelsPerPurchase} (Potential Unlock defaults to
     * {@link #POTENTIAL_UNLOCK_LEVELS_PER_POINT}).
     */
    public static int levelsPerPoint(String skillId) {
        return PrestigeShopConfig.get().skillShop.levelsFor(skillId);
    }

    /** True when the player owns any prestige-shop skill floor ({@code pp_skill_*}). */
    public static boolean hasAnyPurchasedSkillFloor(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        try {
            CompoundTag tag = ProgressionData.stored(player);
            for (String key : tag.m_128431_()) {
                if (key != null && key.startsWith(KEY_SKILL_PREFIX)
                        && ProgressionData.storedGetLong(player, key, 0L) > 0L) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        for (SkillOffer offer : skillOffers()) {
            if (getPurchasedSkillLevels(player, offer.id) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Re-apply purchased skill floors after prestige reset / on login. */
    public static void reapplySkillBonuses(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Skills skills = DmzSkillUtil.skills(player);
        if (skills == null) {
            // DMZ Character / skills bag often missing right after prestige or reset —
            // keep the pulse window open so floors apply once the bag attaches.
            if (hasAnyPurchasedSkillFloor(player)) {
                long minUntil = System.currentTimeMillis() + 5_000L;
                long until = ProgressionData.tempGetLong(player, KEY_REAPPLY_AT, 0L);
                if (until < minUntil) {
                    ProgressionData.tempPut(player, KEY_REAPPLY_AT, minUntil);
                }
            }
            reapplyForms(player);
            reapplyTierUnlocks(player);
            return;
        }
        boolean changed = false;
        // Shop catalog + any legacy purchased ids still stored on the player.
        LinkedHashMap<String, SkillOffer> apply = new LinkedHashMap<>();
        for (SkillOffer offer : skillOffers()) {
            apply.put(offer.id, offer);
            SKILL_OFFERS.put(offer.id, offer);
        }
        for (String id : DmzSkillUtil.allNonFormSkillIds()) {
            if (apply.containsKey(id)) {
                continue;
            }
            int purchased = getPurchasedSkillLevels(player, id);
            if (purchased <= 0) {
                continue;
            }
            int max = DmzSkillUtil.configuredMaxLevel(id);
            if (max <= 0) {
                max = Math.max(purchased, 10);
            }
            apply.put(id, new SkillOffer(id, DmzSkillUtil.prettySkillLabel(id), max));
        }
        // Also cover any pp_skill_* keys that left the live catalog.
        try {
            CompoundTag tag = ProgressionData.stored(player);
            for (String key : tag.m_128431_()) {
                if (key == null || !key.startsWith(KEY_SKILL_PREFIX)) {
                    continue;
                }
                String id = key.substring(KEY_SKILL_PREFIX.length()).toLowerCase(Locale.ROOT);
                if (id.isBlank() || apply.containsKey(id)) {
                    continue;
                }
                int purchased = getPurchasedSkillLevels(player, id);
                if (purchased <= 0) {
                    continue;
                }
                int max = DmzSkillUtil.configuredMaxLevel(id);
                if (max <= 0) {
                    max = Math.max(purchased, 10);
                }
                apply.put(id, new SkillOffer(id, DmzSkillUtil.prettySkillLabel(id), max));
            }
        } catch (Throwable ignored) {
        }
        for (SkillOffer offer : apply.values()) {
            int purchased = getPurchasedSkillLevels(player, offer.id);
            if (purchased <= 0) {
                continue;
            }
            DmzSkillUtil.ensureRegistered(skills, offer.id, offer.maxLevel);
            int current = DmzSkillUtil.level(skills, offer.id);
            int max = Math.max(offer.maxLevel, DmzSkillUtil.maxLevel(skills, offer.id, offer.maxLevel));
            int target = Math.min(max, Math.max(current, purchased));
            if (target > current) {
                DmzSkillUtil.setLevel(skills, offer.id, target);
                changed = true;
            }
        }
        if (changed) {
            DmzSkillUtil.sync(player);
        }
        reapplyForms(player);
        reapplyTierUnlocks(player);
    }

    // ── Permanent difficulty tiers (prestige points) ───────────────────

    /**
     * Prestige-point cost for permanent unlock of difficulty tier {@code tierId} (1–7):
     * {@code tierShop.costs}, else the default T1–2 → 1, T3–4 → 2, T5–6 → 3, T7 → 4.
     */
    public static int tierPointCost(int tierId) {
        if (tierId < 1 || tierId > 7) {
            return 0;
        }
        return PrestigeShopConfig.get().tierShop.costFor(tierId);
    }

    public static boolean hasPurchasedTier(ServerPlayer player, int tierId) {
        if (player == null || tierId < 1 || tierId > 7) {
            return false;
        }
        return ProgressionData.storedGetBool(player, KEY_TIER_PREFIX + tierId);
    }

    /** Highest permanently purchased tier (0 if none). */
    public static int highestPurchasedTier(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        int best = 0;
        for (int t = 1; t <= 7; t++) {
            if (hasPurchasedTier(player, t)) {
                best = t;
            }
        }
        return best;
    }

    /**
     * True when the tier is permanently purchased <b>or</b> actually unlocked in
     * difficulty data (level/prestige sync).
     */
    public static boolean isTierUnlockedOrPurchased(ServerPlayer player, int tierId) {
        if (player == null || tierId < 1 || tierId > 7) {
            return false;
        }
        if (hasPurchasedTier(player, tierId)) {
            return true;
        }
        try {
            var data = com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache.data(player);
            return data != null && data.hasUnlockedTier(tierId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Whether the player may buy permanent T{@code tierId} with prestige points.
     * Shop ladder ({@code tierShop.requireInOrder}): every lower tier must already be
     * <b>permanently purchased</b> (level unlock alone does not skip the chain — that left
     * gaps after prestige). Tiers listed in {@code tierShop.notForSale} are never buyable.
     */
    public static boolean canBuyDifficultyTier(ServerPlayer player, int tierId) {
        if (player == null || tierId < 1 || tierId > 7) {
            return false;
        }
        PrestigeShopConfig.TierShop shop = PrestigeShopConfig.get().tierShop;
        if (hasPurchasedTier(player, tierId) || !shop.forSale(tierId)) {
            return false;
        }
        for (int prev = 1; shop.requireInOrder && prev < tierId; prev++) {
            if (!hasPurchasedTier(player, prev)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Buy a permanent difficulty-tier unlock with prestige points.
     * Requires T1…T(n-1) permanently purchased first (shop ladder).
     * Allowed even when the tier is already unlocked via level — purchase makes it
     * permanent so it survives prestige / level-gate revoke.
     */
    public static String buyDifficultyTier(ServerPlayer player, int tierId) {
        if (player == null) {
            return "§cPlayers only.";
        }
        var tier = com.dbzlegacy.adaptivedifficulty.tier.UnlockTier.byId(tierId);
        if (tier == null) {
            return "§cUnknown tier. Use 1–7.";
        }
        if (!ProgressionData.storedWritable(player)) {
            return "§cCould not save prestige data — try relogging, then buy again.";
        }
        if (hasPurchasedTier(player, tierId)) {
            return "§eYou already own permanent §fT" + tierId + " " + tier.display + "§e.";
        }
        if (!PrestigeShopConfig.get().tierShop.forSale(tierId)) {
            return "§cPermanent §fT" + tierId + " §cis not for sale right now.";
        }
        if (tierId > 1 && PrestigeShopConfig.get().tierShop.requireInOrder) {
            for (int prev = 1; prev < tierId; prev++) {
                if (!hasPurchasedTier(player, prev)) {
                    return "§cBuy permanent §fT" + prev + " §cfirst (shop ladder T1→T"
                            + tierId + ").";
                }
            }
        }
        int cost = tierPointCost(tierId);
        int points = getPoints(player);
        if (points < cost) {
            return "§cNeed §e" + cost + " §cfavor"
                    + " (have §e" + points + "§c).";
        }
        if (!GuiClickConfirm.confirmed(player.m_20148_(), "prestige-tier:" + tierId)) {
            return "§eUnlock permanent §fT" + tierId + " §efor §6" + cost
                    + " §efavor? Click again within 10 seconds.";
        }
        setPoints(player, points - cost);
        ProgressionData.storedPutBool(player, KEY_TIER_PREFIX + tierId, true);
        // Apply unlock bits for every permanently purchased tier (fills gaps).
        var data = com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache.data(player);
        reapplyTierUnlocks(player);
        try {
            com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache.save(player);
        } catch (Throwable ignored) {
        }
        try {
            if (data != null) {
                com.dbzlegacy.adaptivedifficulty.tier.UnlockSystem.syncUnlocks(player, data);
            }
        } catch (Throwable ignored) {
        }
        reapplyTierUnlocks(player);
        try {
            com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache.save(player);
        } catch (Throwable ignored) {
        }
        try {
            com.dbzlegacy.adaptivedifficulty.title.TitleSystem.syncTierTitles(player, true);
        } catch (Throwable ignored) {
        }
        SystemTelemetry.log("prestige_points", "buy_tier", player, null, Map.of(
                "tier", tierId,
                "cost", cost,
                "points", getPoints(player)
        ));
        return "§aPermanent unlock §fT" + tierId + " " + tier.display
                + " §7(§e-" + cost + " §7favor)"
                + "\n§7Survives prestige · still activate with Ancient Coins via §f/difficulty §7Tiers"
                + "\n§7Favor left: §e" + getPoints(player);
    }

    /** Staff: grant permanent tier unlock(s) without spending points. */
    public static String adminGrantTier(ServerPlayer player, int tierId) {
        if (player == null) {
            return "§cPlayer not online.";
        }
        if (tierId < 1 || tierId > 7) {
            return "§cTier must be 1–7.";
        }
        if (!ProgressionData.storedWritable(player)) {
            return "§cCould not save prestige data for §f" + player.m_6302_() + "§c.";
        }
        // Grant the full ladder through tierId so activation has no gaps.
        for (int t = 1; t <= tierId; t++) {
            ProgressionData.storedPutBool(player, KEY_TIER_PREFIX + t, true);
        }
        reapplyTierUnlocks(player);
        try {
            com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache.save(player);
        } catch (Throwable ignored) {
        }
        return "§aGranted permanent difficulty tiers §fT1–T" + tierId
                + " §8(" + player.m_6302_() + ")"
                + "\n§7Activate via §f/difficulty §7Tiers (Ancient Coins).";
    }

    /**
     * Staff: set/add/remove highest permanent difficulty tier (shop ladder).
     * {@code set 3} → owns T1–T3; {@code set 0} / clear all → none.
     */
    public static String adminAdjustHighestTier(ServerPlayer player, String mode, int amount) {
        if (player == null) {
            return "§cPlayer not online.";
        }
        int before = highestPurchasedTier(player);
        Integer next = applyIntMode(before, mode, amount, 0, 7);
        if (next == null) {
            return "§cUsage: tier <player> <set|add|remove> <0-7>"
                    + "\n§8Or: tier <player> give <1-7> | clear <1-7|all>";
        }
        if (!ProgressionData.storedWritable(player)) {
            return "§cCould not save prestige data for §f" + player.m_6302_() + "§c.";
        }
        for (int t = 1; t <= 7; t++) {
            if (t <= next) {
                ProgressionData.storedPutBool(player, KEY_TIER_PREFIX + t, true);
            } else {
                ProgressionData.storedRemove(player, KEY_TIER_PREFIX + t);
            }
        }
        reapplyTierUnlocks(player);
        try {
            com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache.save(player);
        } catch (Throwable ignored) {
        }
        return "§aPermanent difficulty tiers §fT" + before + " §7→ §fT" + next
                + " §8(" + player.m_6302_() + ")"
                + (next > 0
                ? "\n§7Owns permanent §fT1–T" + next + "§7. Activate via §f/difficulty§7."
                : "\n§7No permanent prestige tiers.");
    }

    /** Staff: clear one or all permanent prestige-purchased tiers. */
    public static String adminClearTier(ServerPlayer player, int tierId) {
        if (player == null) {
            return "§cPlayer not online.";
        }
        if (!ProgressionData.storedWritable(player)) {
            return "§cCould not save prestige data for §f" + player.m_6302_() + "§c.";
        }
        if (tierId <= 0) {
            for (int t = 1; t <= 7; t++) {
                ProgressionData.storedRemove(player, KEY_TIER_PREFIX + t);
            }
            return "§7Cleared all permanent prestige difficulty tiers for §f"
                    + player.m_6302_() + "§7.";
        }
        if (tierId > 7) {
            return "§cTier must be 1–7 (or 0 for all).";
        }
        ProgressionData.storedRemove(player, KEY_TIER_PREFIX + tierId);
        return "§7Cleared permanent §fT" + tierId + " §7for §f" + player.m_6302_() + "§7."
                + "\n§8Level/prestige unlocks are unchanged.";
    }

    /** Ensure prestige-purchased tiers stay unlocked (survives sync revoke + prestige). */
    public static void reapplyTierUnlocks(ServerPlayer player) {
        if (player == null) {
            return;
        }
        var data = com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache.data(player);
        if (data == null) {
            return;
        }
        for (int t = 1; t <= 7; t++) {
            if (hasPurchasedTier(player, t)) {
                data.unlockTier(t);
            }
        }
    }

    public static boolean hasMajin(ServerPlayer player) {
        return player != null && ProgressionData.storedGetBool(player, KEY_MAJIN);
    }

    public static boolean hasMutant(ServerPlayer player) {
        return player != null && ProgressionData.storedGetBool(player, KEY_MUTANT);
    }

    public static boolean hasUltimate(ServerPlayer player) {
        return player != null && ProgressionData.storedGetBool(player, KEY_ULTIMATE);
    }

    /** True when Permanent Majin is on sale and buyable (Mutant not owned, while exclusive). */
    public static boolean canBuyMajin(ServerPlayer player) {
        PrestigeShopConfig.Forms forms = PrestigeShopConfig.get().forms;
        return player != null && forms.majin.enabled && !hasMajin(player)
                && (!forms.majinMutantExclusive || !hasMutant(player));
    }

    /** True when Permanent Mutant is on sale and buyable (Majin not owned, while exclusive). */
    public static boolean canBuyMutant(ServerPlayer player) {
        PrestigeShopConfig.Forms forms = PrestigeShopConfig.get().forms;
        return player != null && forms.mutant.enabled && !hasMutant(player)
                && (!forms.majinMutantExclusive || !hasMajin(player));
    }

    /**
     * True when the player may spend points on Permanent Ultimate. Ownership is the only
     * gate — Ultimate is independent, so Majin / Mutant never block it. A player who already
     * has the DMZ {@code ultimate} skill (earned from Old Kai) is also refused, so a later
     * Remove can never strip a legitimately earned form. Off-sale in config refuses too.
     */
    public static boolean canBuyUltimate(ServerPlayer player) {
        return player != null && PrestigeShopConfig.get().forms.ultimate.enabled
                && !hasUltimate(player) && !hasUltimateSkill(player);
    }

    /**
     * True when the DMZ {@code ultimate} skill is level 1+ — the real Ultimate form, granted by
     * Old Kai. Independent of the shop flag: a shop buyer has both, an Old Kai earner only this.
     */
    public static boolean hasUltimateSkill(ServerPlayer player) {
        return ultimateSkillLevel(player) >= 1;
    }

    /** DMZ {@code ultimate} skill level (0 when missing / player or data unavailable). */
    public static int ultimateSkillLevel(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return com.dbzlegacy.adaptivedifficulty.calc.DmzProgression.skillLevel(
                player, DMZ_SKILL_ULTIMATE);
    }

    /**
     * Grant Permanent Ultimate through the skill system — the only path DMZ honors. Sets the
     * {@code ultimate} skill to 1 (same write Old Kai makes) and syncs, then strips the stale
     * dead {@code effects} entry that older builds wrote for buyers on the broken build.
     * Soft-fails like {@link #runDmzEffect}: a DMZ API hiccup never breaks the shop flow.
     */
    public static void grantUltimate(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            var data = com.dbzlegacy.adaptivedifficulty.calc.DmzProgression.stats(player);
            if (data == null) {
                return;
            }
            var skills = data.getSkills();
            if (skills == null) {
                return;
            }
            skills.setSkillLevel(DMZ_SKILL_ULTIMATE, 1);
            DmzSkillUtil.sync(player);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] prestige ultimate grant soft-fail: {}",
                    AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
        stripDeadUltimateEffect(player);
    }

    /**
     * Revoke shop-bought Permanent Ultimate: skill back to 0 (below {@code unlockOnSkillLevel 1}),
     * stale dead effect entry stripped, synced. Only called for shop buyers — the buy gate in
     * {@link #buyUltimate} guarantees the skill came from the shop, never Old Kai. Soft-fails.
     */
    public static void revokeUltimate(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            var data = com.dbzlegacy.adaptivedifficulty.calc.DmzProgression.stats(player);
            if (data == null) {
                return;
            }
            var skills = data.getSkills();
            if (skills == null) {
                return;
            }
            skills.setSkillLevel(DMZ_SKILL_ULTIMATE, 0);
            DmzSkillUtil.sync(player);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] prestige ultimate revoke soft-fail: {}",
                    AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
        stripDeadUltimateEffect(player);
    }

    /**
     * Remove the dead {@code effects} entry for {@code ultimate} that older builds wrote.
     * DMZ has no UltimateStatusHandler, so the entry is inert data — but it must be cleaned up
     * so it never confuses the form state. Soft-fails like {@link #runDmzEffect}.
     */
    private static void stripDeadUltimateEffect(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            var data = com.dbzlegacy.adaptivedifficulty.calc.DmzProgression.stats(player);
            if (data == null) {
                return;
            }
            var effects = data.getEffects();
            if (effects != null && effects.hasEffect(DMZ_SKILL_ULTIMATE)) {
                effects.removeEffect(DMZ_SKILL_ULTIMATE);
                DmzSkillUtil.sync(player);
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] prestige ultimate effect cleanup soft-fail: {}",
                    AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    public static String buyMajin(ServerPlayer player) {
        return buyForm(player, true);
    }

    public static String buyMutant(ServerPlayer player) {
        return buyForm(player, false);
    }

    public static String unbuyMajin(ServerPlayer player) {
        return unbuyForm(player, true);
    }

    public static String unbuyMutant(ServerPlayer player) {
        return unbuyForm(player, false);
    }

    /**
     * Buy Permanent Ultimate for {@code forms.ultimate.cost} favor (default
     * {@link #ULTIMATE_COST}). No exclusivity check against
     * Majin or Mutant — the two permanents stack with Ultimate in either direction.
     * <p>
     * Gate: a player who already has the DMZ {@code ultimate} skill (earned from Old Kai) with
     * the shop flag unset is refused before any charge or flag write, so a later Remove can
     * never strip a legitimately earned form.
     */
    public static String buyUltimate(ServerPlayer player) {
        if (player == null) {
            return UltraStyle.dangerLine("Players only.");
        }
        if (hasUltimate(player)) {
            return UltraStyle.noticeLine("You already own Permanent Ultimate.");
        }
        if (hasUltimateSkill(player)) {
            return UltraStyle.noticeLine(
                    "You already have the Ultimate form from Old Kai — nothing to buy.");
        }
        PrestigeShopConfig.FormOffer offer = PrestigeShopConfig.get().forms.ultimate;
        if (!offer.enabled) {
            return UltraStyle.dangerLine("Permanent Ultimate is not for sale right now.");
        }
        int cost = offer.cost;
        int points = getPoints(player);
        if (points < cost) {
            return UltraStyle.dangerLine(
                    "Need " + cost + " favor (have " + points + ").");
        }
        if (!GuiClickConfirm.confirmed(player.m_20148_(), "form-buy:Permanent Ultimate")) {
            return UltraStyle.noticeLine(
                    "Buy Permanent Ultimate for " + cost
                            + " favor? Click again within 10 seconds.");
        }
        setPoints(player, points - cost);
        ProgressionData.storedPutBool(player, KEY_ULTIMATE, true);
        grantUltimate(player);
        SystemTelemetry.log("prestige_points", "buy_ultimate", player, null, Map.of(
                "points", getPoints(player),
                "cost", cost
        ));
        return UltraStyle.color("a", "Unlocked Permanent Ultimate (-" + cost + " favor)")
                + "\n"
                + UltraStyle.dim("Favor left: " + getPoints(player));
    }

    /**
     * Remove Permanent Ultimate: click again within 10 seconds to confirm, refund
     * {@code forms.ultimate.refundPercent} of the price (default 100 — the full
     * {@link #ULTIMATE_COST}), clear only the Ultimate flag. Majin / Mutant state is untouched.
     * Revoke only runs when the shop flag was set — the buy gate guarantees the skill came from
     * the shop, never from Old Kai — so an earned form is never stripped here.
     */
    public static String unbuyUltimate(ServerPlayer player) {
        if (player == null) {
            return UltraStyle.dangerLine("Players only.");
        }
        if (!hasUltimate(player)) {
            return UltraStyle.noticeLine("You do not own Permanent Ultimate.");
        }
        int refund = PrestigeShopConfig.get().forms.ultimate.refund();
        if (!GuiClickConfirm.confirmed(player.m_20148_(), "form-remove:Permanent Ultimate")) {
            return UltraStyle.noticeLine(
                    "Remove Permanent Ultimate? "
                            + (refund > 0 ? "You get " + refund + " favor back." : "No favor is refunded.")
                            + " Click again within 10 seconds.");
        }
        ProgressionData.storedPutBool(player, KEY_ULTIMATE, false);
        revokeUltimate(player);
        setPoints(player, getPoints(player) + refund);
        SystemTelemetry.log("prestige_points", "unbuy_ultimate", player, null, Map.of(
                "points", getPoints(player),
                "refund", refund
        ));
        return UltraStyle.color(
                        "a", "Removed Permanent Ultimate"
                                + (refund > 0 ? " (+" + refund + " favor refunded)" : " (no refund)"))
                + "\n"
                + UltraStyle.dim("Favor left: " + getPoints(player));
    }

    private static String buyForm(ServerPlayer player, boolean majin) {
        if (player == null) {
            return "§cPlayers only.";
        }
        String label = majin ? "Permanent Majin" : "Permanent Mutant";
        if (majin ? hasMajin(player) : hasMutant(player)) {
            return "§eYou already own §f" + label + "§e.";
        }
        PrestigeShopConfig.Forms forms = PrestigeShopConfig.get().forms;
        PrestigeShopConfig.FormOffer offer = majin ? forms.majin : forms.mutant;
        PrestigeShopConfig.FormOffer other = majin ? forms.mutant : forms.majin;
        // What removing the other form gives back, for the "remove it first" hint.
        String otherRefund = other.refund() > 0
                ? "§7" + other.refund() + " favor back" : "§cno refund";
        if (!offer.enabled) {
            return "§c" + label + " is not for sale right now.";
        }
        int points = getPoints(player);
        if (points < offer.cost) {
            return "§cNeed §e" + offer.cost + " §cfavor (have §e" + points + "§c).";
        }
        if (forms.majinMutantExclusive && majin && hasMutant(player)) {
            return "§cYou have §fPermanent Mutant§c."
                    + "\n§7Remove Mutant first (" + otherRefund + "§7), then buy Majin.";
        }
        if (forms.majinMutantExclusive && !majin && hasMajin(player)) {
            return "§cYou have §fPermanent Majin§c."
                    + "\n§7Remove Majin first (" + otherRefund + "§7), then buy Mutant.";
        }
        if (!GuiClickConfirm.confirmed(player.m_20148_(), "form-buy:" + label)) {
            return "§eBuy §f" + label + " §efor §6" + offer.cost
                    + " §efavor? Click again within 10 seconds.";
        }
        setPoints(player, points - offer.cost);
        ProgressionData.storedPutBool(player, majin ? KEY_MAJIN : KEY_MUTANT, true);
        runDmzEffect(player, majin ? "majin" : "mutant", true);
        SystemTelemetry.log("prestige_points", majin ? "buy_majin" : "buy_mutant", player, null, Map.of(
                "points", getPoints(player),
                "cost", offer.cost
        ));
        return "§aUnlocked §f" + label + " §7(§e-" + offer.cost + " §7favor)"
                + "\n§7Favor left: §e" + getPoints(player)
                + (forms.majinMutantExclusive
                        ? "\n§8To switch: remove it ("
                                + (offer.refund() > 0 ? offer.refund() + " favor back" : "no refund")
                                + "), then buy the other form."
                        : "");
    }

    private static String unbuyForm(ServerPlayer player, boolean majin) {
        if (player == null) {
            return "§cPlayers only.";
        }
        String label = majin ? "Permanent Majin" : "Permanent Mutant";
        if (!(majin ? hasMajin(player) : hasMutant(player))) {
            return "§eYou do not own §f" + label + "§e.";
        }
        PrestigeShopConfig.Forms forms = PrestigeShopConfig.get().forms;
        int refund = (majin ? forms.majin : forms.mutant).refund();
        if (!GuiClickConfirm.confirmed(player.m_20148_(), "form-remove:" + label)) {
            return "§eRemove §f" + label + "§e? "
                    + (refund > 0
                            ? "You get §6" + refund + " §efavor back."
                            : "You will §cnot §eget your favor back.")
                    + " Click again within 10 seconds.";
        }
        clearForm(player, majin);
        if (refund > 0) {
            setPoints(player, getPoints(player) + refund);
        }
        SystemTelemetry.log("prestige_points", majin ? "unbuy_majin" : "unbuy_mutant", player, null, Map.of(
                "refund", refund
        ));
        return "§7Removed §f" + label + "§7. "
                + (refund > 0 ? "§a+" + refund + " favor refunded." : "§cNo favor refunded.")
                + "\n§7Favor: §e" + getPoints(player);
    }

    private static void clearForm(ServerPlayer player, boolean majin) {
        ProgressionData.storedPutBool(player, majin ? KEY_MAJIN : KEY_MUTANT, false);
        runDmzEffect(player, majin ? "majin" : "mutant", false);
    }

    private static void reapplyForms(ServerPlayer player) {
        if (hasMajin(player)) {
            runDmzEffect(player, "majin", true);
        }
        if (hasMutant(player)) {
            runDmzEffect(player, "mutant", true);
        }
        // Ultimate rides the skill system, not the DMZ effect list — reapply is a heal:
        // re-grant the skill if it is missing, and always strip a stale dead effect entry.
        if (hasUltimate(player)) {
            if (!hasUltimateSkill(player)) {
                grantUltimate(player);
            } else {
                stripDeadUltimateEffect(player);
            }
        }
    }

    // ── Personal level-cap breakthroughs (maxValue 150k + personal soft-lock) ─

    public static int getBreakthroughs(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        boolean bagHas = ProgressionData.storedHas(player, KEY_BREAKTHROUGHS);
        int fromBag = bagHas ? (int) ProgressionData.storedGetLong(player, KEY_BREAKTHROUGHS, 0L) : -1;
        int fromRoot = PersistentDataAccess.getInt(player, PersonalLevelCapMirror.ROOT_BREAKTHROUGHS, -1);
        int fromMirror = PersistentDataAccess.getInt(player, PersonalLevelCapMirror.KEY_BREAKTHROUGHS, -1);
        // Explicit NBT only. Inferring from lm_personal_level_cap (Overhaul 50k/150k)
        // made 0 breakthroughs snap to 5 and padmin set 0 fail at held 1.
        if (fromBag < 0 && fromRoot < 0 && fromMirror < 0) {
            return 0;
        }
        int n = 0;
        if (fromBag >= 0) {
            n = Math.max(n, fromBag);
        }
        if (fromRoot >= 0) {
            n = Math.max(n, fromRoot);
        }
        if (fromMirror >= 0) {
            n = Math.max(n, fromMirror);
        }
        return Math.max(0, Math.min(MAX_BREAKTHROUGHS, n));
    }

    public static int breakthroughCost(int nextIndex) {
        // nextIndex 1..5 → 15, 20, 25, 30, 35
        if (nextIndex < 1 || nextIndex > MAX_BREAKTHROUGHS) {
            return Integer.MAX_VALUE;
        }
        return 15 + (nextIndex - 1) * 5;
    }

    public static void setBreakthroughs(ServerPlayer player, int breakthroughs) {
        if (player == null) {
            return;
        }
        int n = Math.max(0, Math.min(MAX_BREAKTHROUGHS, breakthroughs));
        ProgressionData.storedPut(player, KEY_BREAKTHROUGHS, n);
        PersistentDataAccess.putInt(player, PersonalLevelCapMirror.ROOT_BREAKTHROUGHS, n);
        PersistentDataAccess.putInt(player, PersonalLevelCapMirror.KEY_BREAKTHROUGHS, n);
        try {
            PersonalLevelCapMirror.overwrite(player, n, effectiveMaxLevel(n));
        } catch (Throwable ignored) {
        }
        try {
            DmzSkillUtil.sync(player);
        } catch (Throwable ignored) {
        }
        PersonalLevelCapMirror.markSynced(player, effectiveMaxLevel(n));
    }

    /** Personal DMZ level cap: 100k + breakthroughs×10k (mixin + soft-locks). */
    public static int effectiveMaxLevel(ServerPlayer player) {
        return Math.min(ABSOLUTE_LEVEL_CAP,
                BASE_LEVEL_CAP + getBreakthroughs(player) * BREAKTHROUGH_STEP);
    }

    public static int effectiveMaxLevel(int breakthroughs) {
        int n = Math.max(0, Math.min(MAX_BREAKTHROUGHS, breakthroughs));
        return Math.min(ABSOLUTE_LEVEL_CAP, BASE_LEVEL_CAP + n * BREAKTHROUGH_STEP);
    }

    public static String buyBreakthrough(ServerPlayer player) {
        if (player == null) {
            return "§cPlayers only.";
        }
        int current = getBreakthroughs(player);
        if (current >= MAX_BREAKTHROUGHS) {
            return "§cPersonal level cap fully raised (§f"
                    + DmzRewards.formatWhole(ABSOLUTE_LEVEL_CAP) + "§c).";
        }
        int next = current + 1;
        int cost = breakthroughCost(next);
        int points = getPoints(player);
        if (points < cost) {
            return "§cNeed §e" + cost + " §cfavor for breakthrough §f#" + next
                    + " §c(have §e" + points + "§c).";
        }
        setPoints(player, points - cost);
        setBreakthroughs(player, next);
        int newCap = effectiveMaxLevel(player);
        // Force a live DMZ read so the client/stat screen picks up the raised max.
        int liveCap = newCap;
        int liveMaxStats = 0;
        try {
            var data = com.dbzlegacy.adaptivedifficulty.calc.DmzProgression.stats(player);
            if (data != null) {
                liveCap = Math.max(newCap, data.getConfiguredMaxValue());
                liveMaxStats = Math.max(0, data.getConfiguredMaxTotalStats());
                com.dbzlegacy.adaptivedifficulty.progression.DmzSkillUtil.sync(player);
            }
        } catch (Throwable ignored) {
        }
        try {
            com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampPrestigeBridge
                    .syncFromLegacy(player);
        } catch (Throwable ignored) {
        }
        SystemTelemetry.log("prestige_points", "breakthrough", player, null, Map.of(
                "breakthrough", next,
                "cap", newCap,
                "live_cap", liveCap,
                "live_max_stats", liveMaxStats,
                "cost", cost,
                "points", getPoints(player)
        ));
        String note = liveCap >= newCap
                ? ""
                : "\n§cWarning: live DMZ max still §f" + DmzRewards.formatWhole(liveCap)
                        + " §c(expected §f" + DmzRewards.formatWhole(newCap)
                        + "§c) — remount / report if this persists.";
        return "§aPersonal level cap raised to §f" + DmzRewards.formatWhole(newCap)
                + " §7(§e-" + cost + " §7favor)"
                + "\n§7Keep leveling with TP / buy stats into the new cap."
                + "\n§7Future prestige requirements now scale up to §f"
                + DmzRewards.formatWhole(newCap) + "§7."
                + "\n§8Others stay soft-locked at their personal cap until they breakthrough too."
                + "\n§7Breakthrough §f" + next + "§7/§f" + MAX_BREAKTHROUGHS
                + " · Favor left: §e" + getPoints(player)
                + "\n§7Live DMZ max now: §f" + DmzRewards.formatWhole(liveCap)
                + note;
    }

    // ── Login / post-prestige ──────────────────────────────────────────

    private static final String KEY_REAPPLY_AT = "pp_reapply_at_ms";
    private static final String KEY_FORM_PULSE_AT = "pp_form_pulse_at_ms";
    private static final String KEY_SKILL_PULSE_AT = "pp_skill_pulse_at_ms";
    /** Match old Fabled Passive interval that re-gave majin/mutant when missing. */
    private static final long FORM_PULSE_MS = 5000L;
    /**
     * Keep prestige skill floors live after {@code dmzstats reset} / Character rebuild.
     * DMZ can wipe skills well after the short post-reset window.
     */
    private static final long SKILL_FLOOR_PULSE_MS = 5000L;
    /** Post-prestige / death / race-lock: keep forcing floors until this deadline. */
    private static final long REAPPLY_WINDOW_MS = 30_000L;

    public static void onLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampPrestigeBridge
                    .syncFromLegacy(player);
        } catch (Throwable ignored) {
        }
        try {
            com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulPrestigeIntegration
                    .syncLmWalletFromOverhaulCount(player);
        } catch (Throwable ignored) {
        }
        try {
            PrestigeSystem.reconcileNeedFloor(player);
        } catch (Throwable ignored) {
        }
        // Stagger reapply so DMZ / Fabled finish loading (Character attach can lag).
        ProgressionData.tempPut(player, KEY_REAPPLY_AT, System.currentTimeMillis() + 12_000L);
        reapplyAllShopPurchases(player);
        // Special perks: commandsOnReapply run once per login, not every reapply pass.
        PrestigeCustomShop.runReapplyCommands(player);
    }

    public static void scheduleReapplyAfterPrestige(ServerPlayer player) {
        scheduleReapplyAfterDeath(player);
    }

    /**
     * After death respawn (or prestige reset), re-apply every prestige-shop purchase:
     * skill floors, permanent difficulty tiers, Majin/Mutant/Ultimate, and breakthrough soft-lock.
     * Immediate + next-tick + delayed ticks + pulse window cover DMZ skill rebuild races.
     */
    public static void scheduleReapplyAfterDeath(ServerPlayer player) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        // Pulse path: keep reapplying until this deadline (skills/forms/tiers).
        ProgressionData.tempPut(player, KEY_REAPPLY_AT, now + REAPPLY_WINDOW_MS);
        reapplyAllShopPurchases(player);
        PrestigeCustomShop.runReapplyCommands(player);
        MinecraftServer server = player.m_20194_();
        if (server == null) {
            return;
        }
        final UUID id = player.m_20148_();
        // Next-tick pass after DMZ finishes respawn rebuild (UUID — clone/original share id).
        server.execute(() -> {
            ServerPlayer p = server.m_6846_().m_11259_(id);
            if (p != null && p.m_6084_()) {
                reapplyAllShopPurchases(p);
            }
        });
        // dmzstats reset / Character rebuild often finishes seconds later — retry like level pull.
        for (int delay : new int[] {20, 40, 80, 160, 300, 600}) {
            final int ticks = delay;
            try {
                server.m_6937_(new TickTask(server.m_129921_() + ticks, () -> {
                    ServerPlayer p = server.m_6846_().m_11259_(id);
                    if (p != null && p.m_6084_()) {
                        reapplyAllShopPurchases(p);
                    }
                }));
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Restore live DMZ / difficulty state from stored prestige-shop purchases.
     * Does not spend points — only reapplies owned floors / effects / unlocks.
     */
    public static void reapplyAllShopPurchases(ServerPlayer player) {
        if (player == null) {
            return;
        }
        reapplySkillBonuses(player);
        // God Ki Enhancement: top godkienhancement mastery up to invested x 1000 (never lower).
        try {
            GodKiEnhancementShop.reapply(player);
        } catch (Throwable ignored) {
        }
        // Breakthroughs are read live from NBT (effectiveMaxLevel); force a sync so
        // clients / soft-locks see the personal cap immediately after respawn.
        try {
            DmzSkillUtil.sync(player);
        } catch (Throwable ignored) {
        }
        try {
            com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampPrestigeBridge
                    .syncFromLegacy(player);
        } catch (Throwable ignored) {
        }
        try {
            com.dbzlegacy.adaptivedifficulty.progression.bridge.OverhaulPrestigeResourceScale
                    .pulse(player);
        } catch (Throwable ignored) {
        }
        // Special perks: potions / attributes / DMZ stats, skills and effects.
        PrestigeCustomShop.reapply(player);
    }

    /** Called from shop pulse — drains delayed reapply markers + keeps forms/skills live. */
    public static void pulsePlayer(ServerPlayer player, long nowMs) {
        if (player == null) {
            return;
        }
        // Special perks keep their potion effects topped up on their own timer.
        PrestigeCustomShop.pulse(player, nowMs);
        long at = ProgressionData.tempGetLong(player, KEY_REAPPLY_AT, 0L);
        if (at > 0L) {
            // While a post-death / login window is open, keep reapplying every pulse
            // until the deadline (DMZ may wipe skills mid-window).
            reapplyAllShopPurchases(player);
            if (nowMs >= at) {
                ProgressionData.tempRemove(player, KEY_REAPPLY_AT);
            }
        }
        // Prestige skill floors must survive late Character rebuilds after the window.
        if (hasAnyPurchasedSkillFloor(player)) {
            long nextSkill = ProgressionData.tempGetLong(player, KEY_SKILL_PULSE_AT, 0L);
            if (nextSkill <= 0L || nowMs >= nextSkill) {
                ProgressionData.tempPut(player, KEY_SKILL_PULSE_AT, nowMs + SKILL_FLOOR_PULSE_MS);
                // Skip duplicate work when the intensive reapply window is already open.
                if (at <= 0L || nowMs >= at) {
                    reapplySkillBonuses(player);
                }
            }
        }
        // Permanent Majin/Mutant/Ultimate stay on the DragonMineZ effect.
        if (hasMajin(player) || hasMutant(player) || hasUltimate(player)) {
            long next = ProgressionData.tempGetLong(player, KEY_FORM_PULSE_AT, 0L);
            if (next <= 0L || nowMs >= next) {
                ProgressionData.tempPut(player, KEY_FORM_PULSE_AT, nowMs + FORM_PULSE_MS);
                reapplyForms(player);
            }
        }
    }

    /**
     * Apply / clear DMZ majin or mutant <b>silently</b>.
     * <p>
     * Never call {@code MutantManager.grant} — it always chats
     * {@code message.dragonminez.mutant.gained} even when already mutant, which
     * spammed every form pulse / death reapply. Prefer Effects + MutantSavedData
     * (or {@code reconcileHolder} when already present). No {@code dmzeffect}
     * command fallback on give — Brigadier {@code sendSuccess} also chats.
     */
    private static void runDmzEffect(ServerPlayer player, String effect, boolean give) {
        if (player == null || effect == null || effect.isBlank()) {
            return;
        }
        String effectId = effect.trim().toLowerCase(Locale.ROOT);
        boolean mutant = "mutant".equals(effectId);
        try {
            var data = com.dbzlegacy.adaptivedifficulty.calc.DmzProgression.stats(player);
            if (data == null) {
                return;
            }
            var effects = data.getEffects();
            if (effects == null) {
                return;
            }
            if (mutant) {
                if (give) {
                    ensureMutantSilent(player, data, effects);
                } else if (com.dragonminez.server.util.MutantManager.isMutant(data)) {
                    com.dragonminez.server.util.MutantManager.revoke(player, data);
                }
                return;
            }
            // Majin (and any other non-mutant effect id).
            if (give) {
                ensureEffectSilent(player, effects, effectId, majinEffectPower());
            } else if (effects.hasEffect(effectId)) {
                effects.removeEffect(effectId);
                DmzSkillUtil.sync(player);
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] prestige form API soft-fail ({}): {}",
                    AdaptiveDifficultyMod.MOD_ID, effectId, t.toString());
        }
    }

    /**
     * Keep permanent Mutant applied without {@link com.dragonminez.server.util.MutantManager#grant}'s chat.
     */
    private static void ensureMutantSilent(
            ServerPlayer player,
            com.dragonminez.common.stats.StatsData data,
            com.dragonminez.common.stats.character.Effects effects
    ) {
        if (com.dragonminez.server.util.MutantManager.isMutant(data)) {
            // Already has effect — only ensure lottery holder registration (no chat).
            try {
                com.dragonminez.server.util.MutantManager.reconcileHolder(player, data);
            } catch (Throwable ignored) {
            }
            return;
        }
        effects.addEffect("mutant", 1.0, -1);
        try {
            MinecraftServer server = player.m_20194_();
            if (server != null) {
                com.dragonminez.server.world.data.MutantSavedData.get(server)
                        .addHolder(player.m_20148_());
            }
        } catch (Throwable ignored) {
        }
        DmzSkillUtil.sync(player);
    }

    /** Apply or upgrade an effect to permanent duration without command chat. */
    private static void ensureEffectSilent(
            ServerPlayer player,
            com.dragonminez.common.stats.character.Effects effects,
            String effectId,
            double power
    ) {
        if (effects.hasEffect(effectId) && effects.getEffectDuration(effectId) == -1) {
            return;
        }
        effects.addEffect(effectId, power, -1);
        DmzSkillUtil.sync(player);
    }

    private static double majinEffectPower() {
        try {
            var gameplay = com.dragonminez.common.config.ConfigManager.getServerConfig().getGameplay();
            // Prefer configured majin power when present; 1.0 matches typical give defaults.
            Method m = gameplay.getClass().getMethod("getMajinPower");
            Object v = m.invoke(gameplay);
            if (v instanceof Number n && n.doubleValue() > 0.0) {
                return n.doubleValue();
            }
        } catch (Throwable ignored) {
        }
        return 1.0;
    }
}
