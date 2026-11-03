package com.dbzlegacy.adaptivedifficulty.gui;

import com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache;
import com.dbzlegacy.adaptivedifficulty.calc.DifficultySnapshot;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.calc.LmOverhaulScaledCombat;
import com.dbzlegacy.adaptivedifficulty.calc.PlayerCombatProfile;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy;
import com.dbzlegacy.adaptivedifficulty.util.PaidFeatureAccess;
import com.dbzlegacy.adaptivedifficulty.data.PlayerDifficultyData;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockSystem;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockTier;
import com.dbzlegacy.adaptivedifficulty.title.DifficultyTitle;
import com.dbzlegacy.adaptivedifficulty.title.TitleSystem;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.dbzlegacy.adaptivedifficulty.util.SystemGate;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

/**
 * Chat fallback when CMI/chest GUI is unavailable.
 * Tier-centric: no difficulty points, no V3 branding.
 *
 * <p>Player-facing colors go through {@link UltraStyle} only — never raw section-sign
 * literals in this class.
 */
public final class DifficultyChatMenu {
    private DifficultyChatMenu() {}

    public static void open(ServerPlayer player, String page) {
        // Chat pages open without DifficultyMenu sometimes — still re-sample DMZ level.
        int sampled = DmzProgression.sampleLevelOnGuiOpen(player);
        if (DmzProgression.hasReliableUnlockGateSample(player)
                && !DmzProgression.isTransformed(player)) {
            DifficultyCache.data(player).noteDmzLevel(sampled);
        }
        DifficultyCache.refresh(player);
        if ("settings".equalsIgnoreCase(page)) {
            if (StaffAccess.isStaff(player)) {
                settings(player);
            } else {
                main(player);
            }
            return;
        }
        if ((!DifficultyConfig.isEnabled() || !SystemGate.allows(player))
                && (page == null || page.isBlank() || !"settings".equalsIgnoreCase(page))) {
            main(player);
            return;
        }
        if (page == null || page.isBlank() || "main".equalsIgnoreCase(page)) {
            main(player);
        } else if ("tiers".equalsIgnoreCase(page) || "tier".equalsIgnoreCase(page)
                || "buy".equalsIgnoreCase(page) || "purchase".equalsIgnoreCase(page)
                || "unlock".equalsIgnoreCase(page) || "enemies".equalsIgnoreCase(page)
                || "rewards".equalsIgnoreCase(page) || "adjust".equalsIgnoreCase(page)
                || "change".equalsIgnoreCase(page) || "set".equalsIgnoreCase(page)
                || "lower".equalsIgnoreCase(page)) {
            tiers(player);
        } else if ("titles".equalsIgnoreCase(page) || "title".equalsIgnoreCase(page)) {
            titles(player);
        } else if ("team".equalsIgnoreCase(page) || "teams".equalsIgnoreCase(page)) {
            teams(player);
        } else if ("stats".equalsIgnoreCase(page) || "statistics".equalsIgnoreCase(page)
                || "details".equalsIgnoreCase(page)) {
            if (isStaff(player)) {
                stats(player);
            } else {
                main(player);
            }
        } else {
            main(player);
        }
    }

    private static void main(ServerPlayer player) {
        DifficultySnapshot snap = DifficultyCache.refresh(player);
        send(player, Component.m_237113_(""));
        send(player, Component.m_237113_(
                UltraStyle.DIM + "── " + UltraStyle.VALUE + "Legacy Mechanics · Difficulty "
                        + UltraStyle.DIM + "──"));
        if (!DifficultyConfig.isEnabled()) {
            send(player, Component.m_237113_(UltraStyle.DANGER + UltraStyle.BOLD + "System disabled"));
            if (isStaff(player)) {
                send(player, Component.m_237113_(
                        UltraStyle.DIM + "Re-enable: " + UltraStyle.VALUE + "/difficulty admin on"));
            } else {
                send(player, Component.m_237113_(UltraStyle.LABEL + "Please try again later."));
            }
            return;
        }
        if (!SystemGate.allows(player)) {
            send(player, Component.m_237113_(UltraStyle.YELLOW + UltraStyle.BOLD + "Not available"));
            if (isStaff(player)) {
                send(player, Component.m_237113_(
                        UltraStyle.DIM + "Whitelist: " + UltraStyle.VALUE
                                + "/difficulty admin whitelist add <you>"));
            } else {
                send(player, Component.m_237113_(UltraStyle.LABEL + "Ask an admin if you need access."));
            }
            return;
        }
        PlayerDifficultyData data = DifficultyCache.data(player);
        boolean personalOn = data.isPersonalEnabled();
        boolean coinChatOn = data.isCoinDropChat();
        if (!personalOn) {
            send(player, Component.m_237113_(UltraStyle.YELLOW + UltraStyle.BOLD + "Difficulty off"));
            send(player, Component.m_237113_(
                    UltraStyle.LABEL + "No scaling, kill coins, AI pressure, or tier buys"));
            send(player, Component.m_237113_(
                    UltraStyle.DIM + "Saved tier " + UltraStyle.VALUE + snap.activeTierName
                            + "  " + UltraStyle.DIM + UltraStyle.SEP + "  "
                            + UltraStyle.LABEL + "Unlocked " + UltraStyle.VALUE + "T"
                            + snap.highestUnlockedTier));
        } else {
            send(player, Component.m_237113_(
                    UltraStyle.LABEL + "Tier " + UltraStyle.VALUE + snap.activeTierName
                            + "  " + UltraStyle.DIM + UltraStyle.SEP + "  "
                            + UltraStyle.color(snap.stateColorCode(), snap.state())
                            + "  " + UltraStyle.DIM + UltraStyle.SEP + "  "
                            + UltraStyle.LABEL + "Unlocked " + UltraStyle.VALUE + "T"
                            + snap.highestUnlockedTier));
        }
        send(player, Component.m_237113_(
                UltraStyle.GOLD + "Ancient Coins " + UltraStyle.VALUE
                        + AncientCoinEconomy.inventoryBreakdown(player)));
        if (!TitleSystem.activeDisplay(player).equals("None")) {
            send(player, Component.m_237113_(
                    UltraStyle.LABEL + "Title " + UltraStyle.YELLOW + TitleSystem.activeDisplay(player)));
        }
        send(player, Component.m_237113_(
                UltraStyle.DANGER + "Warning: " + UltraStyle.LABEL
                        + "Scaled mobs can attack other players as well"));
        send(player, Component.m_237113_(""));
        MutableComponent hub = Component.m_237113_(UltraStyle.LABEL)
                .m_7220_(btn(
                        UltraStyle.YELLOW + "[Tiers]",
                        "/difficulty do page tiers",
                        "Buy higher · lower unlocked · clear active tier"))
                .m_7220_(Component.m_237113_("  "))
                .m_7220_(btn(
                        UltraStyle.LIGHT_PURPLE + "[Titles]",
                        "/difficulty do page titles",
                        "Equip difficulty titles"));
        send(player, hub);
        MutableComponent toggles = Component.m_237113_(UltraStyle.LABEL)
                .m_7220_(btn(
                        personalOn
                                ? UltraStyle.YELLOW + "[Difficulty ON]"
                                : UltraStyle.dim("[Difficulty OFF]"),
                        "/difficulty do toggle_personal 0 main",
                        personalOn
                                ? "Turn OFF — no scaling, kill coins, AI pressure, or tier buys · your summoned dragon despawns"
                                : "Turn ON — restore scaling. Scaled mobs can attack other players as well"))
                .m_7220_(Component.m_237113_("  "))
                .m_7220_(btn(
                        coinChatOn
                                ? UltraStyle.confirm("[Coin messages ON]")
                                : UltraStyle.dim("[Coin messages OFF]"),
                        "/difficulty do toggle_coin_chat 0 main",
                        coinChatOn
                                ? "Mute Ancient Coin drop chat messages"
                                : "Show Ancient Coin drop chat messages"));
        send(player, toggles);
        if (isStaff(player)) {
            send(player, btn(
                    UltraStyle.danger("[Staff details]"),
                    "/difficulty do page stats",
                    "Staff breakdown"));
        }
        send(player, btn(UltraStyle.LABEL + "\u00AB Hub", "/lm", "Main hub"));
    }

    private static void tiers(ServerPlayer player) {
        PlayerDifficultyData data = DifficultyCache.data(player);
        UnlockSystem.syncUnlocks(player, data);
        int level = DmzProgression.tierScalingDmzLevel(player);
        int active = data.getActiveTier();
        send(player, Component.m_237113_(""));
        send(player, Component.m_237113_(
                UltraStyle.DIM + "──────── " + UltraStyle.YELLOW + "Difficulty Tiers "
                        + UltraStyle.DIM + "────────"));
        if (!data.isPersonalEnabled()) {
            send(player, Component.m_237113_(
                    UltraStyle.YELLOW
                            + "Personal difficulty is OFF — turn it ON on the main menu to change tiers."));
            send(player, btn(UltraStyle.BACK, "/difficulty do page main", "Return"));
            return;
        }
        int prestige = DmzProgression.prestige(player);
        send(player, Component.m_237113_(
                UltraStyle.LABEL + "Current " + UltraStyle.VALUE
                        + (active <= 0 ? "None" : ("T" + active))
                        + "  " + UltraStyle.DIM + UltraStyle.SEP + "  "
                        + UltraStyle.LABEL + "DMZ " + UltraStyle.VALUE + level
                        + "  " + UltraStyle.DIM + UltraStyle.SEP + "  "
                        + UltraStyle.LABEL + "Prestige " + UltraStyle.VALUE + prestige));
        send(player, Component.m_237113_(
                UltraStyle.DIM + "Upgrades cost Ancient Coins · downgrades are Free · clear active tier."));
        send(player, Component.m_237113_(
                UltraStyle.DIM + "Unlock with " + UltraStyle.VALUE + "DMZ level " + UltraStyle.DIM + "OR "
                        + UltraStyle.VALUE + "Prestige " + UltraStyle.DIM
                        + "(either one). Upgrade cost scales with level."));
        send(player, Component.m_237113_(
                UltraStyle.VALUE + AncientCoinEconomy.inventoryBreakdown(player)));
        send(player, Component.m_237113_(""));
        send(player, btn(
                UltraStyle.danger("[Clear active tier]"),
                "/difficulty do lower_tier 0 tiers",
                "Clear active tier"));
        for (UnlockTier tier : UnlockTier.values()) {
            boolean freeCost = PaidFeatureAccess.bypassAncientCoinCost(player);
            boolean unlocked = data.hasUnlockedTier(tier.id);
            boolean activeHere = active == tier.id;
            boolean canLower = unlocked && active > 0 && tier.id < active;
            MutableComponent line = Component.m_237113_(
                    (activeHere ? UltraStyle.CONFIRM + "● " : unlocked ? UltraStyle.YELLOW : UltraStyle.DIM)
                            + "T" + tier.id + " " + tier.display);
            if (activeHere) {
                line = line.m_7220_(Component.m_237113_(" " + UltraStyle.confirm("Current")));
            } else if (canLower) {
                line = line.m_7220_(Component.m_237113_(" " + UltraStyle.confirm("Free") + " "))
                        .m_7220_(btn(
                                UltraStyle.VALUE + "[Downgrade]",
                                "/difficulty do activate " + tier.id + " tiers",
                                "Downgrade to Tier " + tier.id + " — Free"));
            } else if (unlocked) {
                long directed = AncientCoinEconomy.activationCost(tier, player, active);
                String costLabel = freeCost || directed <= 0L
                        ? (freeCost ? UltraStyle.confirm("free (staff)") : UltraStyle.confirm("Free"))
                        : (UltraStyle.GOLD + AncientCoinEconomy.formatExactCost(directed));
                line = line.m_7220_(Component.m_237113_(" " + costLabel + " "))
                        .m_7220_(btn(
                                UltraStyle.confirm("[Switch]"),
                                "/difficulty do activate " + tier.id + " tiers",
                                freeCost || directed <= 0L
                                        ? "Activate Tier " + tier.id + " — Free"
                                        : "Pay Ancient Coins for Tier " + tier.id + " (pay-up OK, change returned)"));
            } else {
                line = line.m_7220_(Component.m_237113_(
                        " " + UltraStyle.danger("Locked") + " " + UltraStyle.DIM + "("
                                + tier.requirementTip() + ")"));
            }
            send(player, line);
        }
        send(player, Component.m_237113_(""));
        send(player, btn(UltraStyle.BACK, "/difficulty do page main", "Return"));
    }

    private static void titles(ServerPlayer player) {
        TitleSystem.syncTierTitles(player, true);
        PlayerDifficultyData data = DifficultyCache.data(player);
        send(player, Component.m_237113_(""));
        send(player, Component.m_237113_(
                UltraStyle.DIM + "──────── " + UltraStyle.LIGHT_PURPLE + "Difficulty Titles "
                        + UltraStyle.DIM + "────────"));
        send(player, Component.m_237113_(
                UltraStyle.LABEL + "Unlock with DMZ level or Prestige — keeps after lowering tier."));
        send(player, Component.m_237113_(
                UltraStyle.LABEL + "Swap freely. Rarity adds landing relief · AD damage · TP."));
        send(player, Component.m_237113_(
                UltraStyle.LABEL + "Equipped " + UltraStyle.YELLOW + TitleSystem.activeDisplay(player)));
        send(player, btn(
                UltraStyle.danger("[Clear Title]"),
                "/difficulty do clear_title 0 titles",
                "Unequip title"));
        send(player, Component.m_237113_(""));
        for (DifficultyTitle title : DifficultyTitle.values()) {
            boolean earned = TitleSystem.has(player, title);
            MutableComponent line = Component.m_237113_(
                    (earned ? UltraStyle.GOLD : UltraStyle.DIM)
                            + title.display
                            + " "
                            + UltraStyle.LABEL
                            + title.requirementTip());
            if (earned) {
                boolean equipped = title.id.equals(data.getActiveTitle());
                if (equipped) {
                    line = line.m_7220_(Component.m_237113_(" " + UltraStyle.confirm("Equipped")));
                } else {
                    line = line.m_7220_(Component.m_237113_(" "))
                            .m_7220_(btn(
                                    UltraStyle.confirm("[Equip]"),
                                    "/difficulty do equip_title " + title.id + " titles",
                                    "Equip " + title.display));
                }
            }
            send(player, line);
        }
        send(player, Component.m_237113_(""));
        send(player, btn(UltraStyle.BACK, "/difficulty do page main", "Return"));
    }

    private static void teams(ServerPlayer player) {
        send(player, Component.m_237113_(""));
        send(player, Component.m_237113_(
                UltraStyle.DIM + "──────── " + UltraStyle.AQUA + "Rival Teams "
                        + UltraStyle.DIM + "────────"));
        for (String line : DifficultyTeamGuiApi.linesForPage(player, "team")) {
            // API may still carry legacy marks; strip and re-label plain for chat until that API migrates.
            send(player, Component.m_237113_(UltraStyle.LABEL + UltraStyle.plain(line)));
        }
        send(player, Component.m_237113_(""));
        send(player, btn(
                UltraStyle.LABEL + "Personal",
                "/difficulty do team personal team",
                "Personal ceiling only — no rival bonus."));
        send(player, btn(
                UltraStyle.confirm("Threshold"),
                "/difficulty do team threshold team",
                "Extra max + harder spawns"));
        send(player, btn(
                UltraStyle.DARK_GREEN + "Full team",
                "/difficulty do team full team",
                "Threshold + nearby spare + stronger spawn boost when rivals are close"));
        send(player, btn(
                UltraStyle.GOLD + "Open Rival",
                "/rival",
                "Manage mutual rivals"));
        send(player, btn(UltraStyle.BACK, "/difficulty do page main", "Return"));
    }

    private static void stats(ServerPlayer player) {
        if (!isStaff(player)) {
            main(player);
            return;
        }
        DifficultySnapshot snap = DifficultyCache.refresh(player);
        PlayerCombatProfile profile = PlayerCombatProfile.of(player);
        send(player, Component.m_237113_(
                UltraStyle.DIM + "── " + UltraStyle.VALUE + "Details " + UltraStyle.DIM + "(staff) ──"));
        send(player, Component.m_237113_(
                UltraStyle.LABEL + "Tier " + UltraStyle.VALUE + snap.activeTierName
                        + "  " + UltraStyle.DIM + UltraStyle.SEP + "  "
                        + UltraStyle.LABEL + "State "
                        + UltraStyle.color(snap.stateColorCode(), snap.state())));
        send(player, Component.m_237113_(
                UltraStyle.LABEL + "Combat Rating " + UltraStyle.VALUE + snap.combatRating
                        + "  " + UltraStyle.DIM + UltraStyle.SEP + "  "
                        + UltraStyle.LABEL + "DMZ " + UltraStyle.VALUE + snap.dmzLevel
                        + "  " + UltraStyle.DIM + UltraStyle.SEP + "  "
                        + UltraStyle.LABEL + "Prestige " + UltraStyle.VALUE + snap.prestige));
        send(player, Component.m_237113_(
                UltraStyle.LABEL + "Class " + UltraStyle.VALUE
                        + (profile.fightingClass.isBlank() ? "?" : profile.fightingClass)
                        + "  " + UltraStyle.DIM + UltraStyle.SEP + "  "
                        + UltraStyle.LABEL + "Style " + UltraStyle.VALUE + profile.style.name()));
        send(player, Component.m_237113_(
                UltraStyle.LABEL + "Top stats " + UltraStyle.VALUE + profile.topStatsLabel()));
        for (String line : LmOverhaulScaledCombat.compactLines(DmzProgression.stats(player))) {
            send(player, Component.m_237113_(UltraStyle.LABEL + UltraStyle.plain(line)));
        }
        send(player, Component.m_237113_(
                UltraStyle.LABEL + "Unlocked " + UltraStyle.VALUE + "T" + snap.highestUnlockedTier
                        + "  " + UltraStyle.DIM + UltraStyle.SEP + "  "
                        + UltraStyle.LABEL + "Title " + UltraStyle.YELLOW
                        + TitleSystem.activeDisplay(player)));
        send(player, Component.m_237113_(
                UltraStyle.GOLD + "Ancient Coins " + UltraStyle.VALUE
                        + AncientCoinEconomy.inventoryBreakdown(player)));
        send(player, btn(UltraStyle.BACK, "/difficulty do page main", "Return"));
    }

    private static void settings(ServerPlayer player) {
        if (!StaffAccess.isStaff(player)) {
            main(player);
            return;
        }
        DifficultyConfig cfg = DifficultyConfig.get();
        send(player, Component.m_237113_(
                UltraStyle.DIM + "──────── " + UltraStyle.VALUE + "Admin " + UltraStyle.DIM + "────────"));
        send(player, Component.m_237113_(
                (cfg.enabled ? UltraStyle.confirm("System enabled") : UltraStyle.danger("System disabled"))
                        + " " + UltraStyle.DIM + UltraStyle.SEP + " "
                        + (cfg.whitelistEnabled
                                ? UltraStyle.YELLOW + "Whitelist ON " + UltraStyle.LABEL + "("
                                        + DifficultyConfig.whitelistEntries().size() + ")"
                                : UltraStyle.LABEL + "Whitelist OFF")));
        send(player, Component.m_237113_(
                UltraStyle.DIM + "/difficulty admin off|on · whitelist on|off|add|remove|list"));
        send(player, Component.m_237113_(
                UltraStyle.LABEL + "Staff Ancient Coin pricing " + UltraStyle.DIM + "→ "
                        + UltraStyle.VALUE + "/lm " + UltraStyle.LABEL + "→ Progression → Ancient Coins"));
        send(player, Component.m_237113_(
                UltraStyle.LABEL + "Tier cost anchor " + UltraStyle.VALUE + (long) cfg.tierCostLevelAnchor
                        + " " + UltraStyle.DIM + UltraStyle.SEP + " T7 target " + UltraStyle.VALUE
                        + cfg.tierCostT7TargetCopper + " copper"));
        send(player, Component.m_237113_(
                UltraStyle.DIM + "/difficulty admin set tierCostLevelAnchor|tierCostT7TargetCopper <n>"));
        send(player, Component.m_237113_(
                UltraStyle.LABEL + "Coin drops " + UltraStyle.VALUE + cfg.enableAncientCoinDrops
                        + "  " + UltraStyle.LABEL + "Death reset " + UltraStyle.VALUE
                        + cfg.deathResetsActiveDifficulty));
        send(player, Component.m_237113_(
                UltraStyle.LABEL + "Drop chance " + UltraStyle.VALUE + cfg.ancientCoinDropChance
                        + "  " + UltraStyle.LABEL + "Dual upgrade " + UltraStyle.VALUE
                        + cfg.ancientCoinUpgradeChance
                        + " " + UltraStyle.DIM + "(stock 0.05 / 0.005)"));
        send(player, Component.m_237113_(
                UltraStyle.DIM
                        + "/difficulty admin set ancientCoinDropChance|ancientCoinUpgradeChance <0-1>"));
        send(player, btn(UltraStyle.BACK, "/difficulty do page main", "Return"));
    }

    private static MutableComponent btn(String label, String command, String hover) {
        return Component.m_237113_(label).m_130948_(
                Style.f_131099_
                        .m_131142_(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .m_131144_(new HoverEvent(HoverEvent.Action.f_130831_, Component.m_237113_(hover)))
        );
    }

    private static boolean isStaff(ServerPlayer player) {
        return StaffAccess.isStaff(player);
    }

    private static void send(ServerPlayer player, Component component) {
        player.m_213846_(component);
    }
}
