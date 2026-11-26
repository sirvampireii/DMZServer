package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * LM 4.7.71: the one list of every menu page: screen (system) and page id to its title, its parent page and whether
 * staff alone see it. Breadcrumbs, the hub search, Back on bridge clients and the UI probe's navigation audit all
 * read it; nothing else describes where a page sits.
 *
 * <p>A page id pattern is split on {@code :}; {@code *} matches one part, {@code **} the rest (any number of parts).
 * A parent is {@code screen|page}; {@code $1}, {@code $2} ... put the parts the pattern's wildcards matched into it,
 * so a family of pages (every config section, every confirm page) points at the right list. The first matching
 * entry wins, so specific pages come before their family.
 *
 * <p>Adding a page means adding one line here. A page the screens declare without an entry fails the build
 * (sim/audit_page_registry.py) and is logged at run time; the self-test checks every parent resolves and every
 * chain reaches the main menu.
 */
public final class PageRegistry {
    private PageRegistry() {}

    /** One page: {@code search} false for per-item pages (confirms, details) found through their list. */
    public record Page(String screen, String pattern, String title, String parent, boolean staff, boolean search,
            String keywords) {}

    /** A page as shown: screen, concrete page id, title. */
    public record Ref(String screen, String page, String title) {
        public String key() {
            return screen + "|" + page;
        }
    }

    public static final String HUB = "hub";
    public static final String HUB_MAIN = "main";

    private static final List<Page> PAGES = new ArrayList<>();

    private static void p(String screen, String pattern, String title, String parent) {
        PAGES.add(new Page(screen, pattern, title, parent, false, !pattern.contains("*"), ""));
    }

    private static void p(String screen, String pattern, String title, String parent, String keywords) {
        PAGES.add(new Page(screen, pattern, title, parent, false, !pattern.contains("*"), keywords));
    }

    /** A per-item page (a confirm, a detail): breadcrumbs and Back, not search. */
    private static void item(String screen, String pattern, String title, String parent) {
        PAGES.add(new Page(screen, pattern, title, parent, false, false, ""));
    }

    private static void staff(String screen, String pattern, String title, String parent) {
        PAGES.add(new Page(screen, pattern, title, parent, true, !pattern.contains("*"), ""));
    }

    /**
     * 4.7.84 (N4): an optional short title for Back labels, used when "Back to " + the full title does not fit a
     * footer button ("Back to Coin wager", never the cut "Back to Wager Ancient"). Keyed by the entry's
     * {@code screen|pattern}.
     */
    private static final Map<String, String> SHORT = new LinkedHashMap<>();

    private static void shortTitle(String screen, String pattern, String shortTitle) {
        SHORT.put(screen + "|" + pattern, shortTitle);
    }

    static {
        // ---- the hub (Home / Goals / More)
        p(HUB, HUB_MAIN, "Main menu", null, "home hub start");
        p(HUB, "more", "More", "hub|main", "settings meditation search find hud");
        p(HUB, "goals", "Weekly goals", "hub|main", "goals week weekly progress");
        p(HUB, "coins", "Where to get coins", "hub|more", "coins ancient money drop earn where get");
        p(HUB, "skins", "Menu skins", "hub|more", "theme look colors colour background");
        p(HUB, "skin_custom", "My colors", "hub|skins", "custom skin hex colour wheel");
        // 4.7.84 (H6): My colors > From a skin (copy a skin's colors into the fields, or the defaults).
        p(HUB, "skin_custom_from", "From a skin", "hub|skin_custom", "copy preset reset default colors colour");
        item(HUB, "skin:**", "Menu skin", "hub|skins");

        // ---- Difficulty (Home / Tiers / Summons)
        // 4.7.73: the state-routed ids (main, tiers, your_tier) are the pages and parents. The guide ids always replay
        // the guide (DifficultyTutorial.route), so they are never a parent and only the guide itself is searchable.
        // 4.7.84 (H2): players type "boost" for Momentum and Mastery Boost, "help" for the guide.
        p("difficulty", "main", "Difficulty", "hub|main", "tier level momentum your tier boost mastery help");
        p("difficulty", "guide", "Difficulty guide", "difficulty|main", "how tutorial help start");
        p("difficulty", "tiers", "Tiers", "difficulty|main", "pick level");
        item("difficulty", "guide_tiers", "Tiers", "difficulty|guide");
        p("difficulty", "summons", "Summons", "difficulty|main", "boss dragon wither warden");
        staff("difficulty", "stats", "Combat rating", "difficulty|main");
        item("difficulty", "tier_ask:*", "Tier", "difficulty|tiers");
        item("difficulty", "guide_your_tier", "Your tier", "difficulty|guide_tiers");
        item("difficulty", "your_tier", "Your tier", "difficulty|main");
        item("difficulty", "end_dragon", "End Dragon", "difficulty|summons");
        item("difficulty", "summon_ask:*", "Summon", "difficulty|summons");
        item("difficulty", "clear_tier_ask", "Clear active tier", "difficulty|tiers");
        item("difficulty", "difficulty_off_ask", "Turn difficulty off", "difficulty|main");
        item("difficulty", "wither", "Wither", "difficulty|summons");
        item("difficulty", "warden", "Warden", "difficulty|summons");
        p("difficulty", "titles_p0", "Titles", "difficulty|main", "badge");
        item("difficulty", "titles_p*", "Titles", "difficulty|titles_p0");
        item("difficulty", "titles", "Titles", "difficulty|main");
        item("difficulty", "title_detail:*", "Title", "difficulty|titles_p0");
        item("difficulty", "title_unequip_ask", "Unequip title", "difficulty|titles_p0");
        staff("difficulty", "admin", "Server tools", "difficulty|main");
        staff("difficulty", "eventlog", "Event log", "difficulty|admin");
        item("difficulty", "flush_ask", "Flush", "difficulty|eventlog");

        // ---- Rivals (Home / Rival / Challenge)
        p("rival", "home", "Rivals", "hub|main", "rp nemesis");
        item("rival", "main", "Rivals", "hub|main");
        // 4.7.84 (R-4): the Rival tab reads "My rivals".
        p("rival", "actions", "My rivals", "rival|home", "rival list requests");
        p("rival", "challenge", "Challenge", "rival|home");
        // 4.7.73: the pager's later pages (placePrevNext adds :N).
        item("rival", "actions:*", "My rivals", "rival|home");
        item("rival", "challenge:*", "Challenge", "rival|home");
        p("rival", "info", "My rival stats", "rival|home");
        p("rival", "top", "Leaderboard", "rival|home", "ranking");
        p("rival", "records:history", "Records", "rival|home", "history");
        p("rival", "records:season", "Season", "rival|records:history");
        p("rival", "records:quests", "Quests", "rival|records:history");
        p("rival", "records:achievements", "Achievements", "rival|records:history");
        p("rival", "records:hof", "Hall of fame", "rival|records:history");
        p("rival", "records:journal", "Journal", "rival|records:history");
        // 4.7.84 (R-2): records:stats and records:title open the merged stats page (info); records:** catches them.
        item("rival", "records:**", "Records", "rival|records:history");
        // 4.7.84 (X7): one TP chat setting per system, named for what it sets.
        p("rival", "settings", "Rival TP chat", "rival|home", "tp chat messages instinct settings");
        staff("rival", "admin", "Rival staff tools", "rival|home");
        p("rival", "pick_rival", "Pick a rival", "rival|actions");
        item("rival", "pick_rival:**", "Pick a rival", "rival|actions");
        p("rival", "requests", "Requests", "rival|actions");
        item("rival", "requests:*", "Requests", "rival|actions");
        p("rival", "pick_challenge", "Send a challenge", "rival|challenge");
        item("rival", "pick_challenge:**", "Send a challenge", "rival|challenge");
        p("rival", "pick_spectate", "Spectate", "rival|challenge");
        item("rival", "pick_spectate:**", "Spectate", "rival|challenge");
        item("rival", "challenge_time:**", "Challenge time", "rival|challenge");
        item("rival", "list_detail:**", "Rival profile", "rival|actions");
        item("rival", "remove_confirm:**", "Remove rival", "rival|actions");
        item("rival", "accept_confirm:**", "Accept request", "rival|requests");
        item("rival", "rival_type:**", "Rival type", "rival|pick_rival");
        p("rival", "pick_replace_mutual", "Replace a rival", "rival|actions");
        item("rival", "pick_replace_mutual:**", "Replace a rival", "rival|actions");
        item("rival", "replace_confirm:**", "Replace rival", "rival|pick_replace_mutual");

        // ---- Sparring (Home / Leaderboard / Spar Board), 4.7.78
        p("spar", "main", "Sparring", "hub|main", "spar");
        p("spar", "leaderboard", "Leaderboard", "spar|main", "hall of infamy stats ranking");
        p("spar", "sparboard", "Spar Board", "spar|main", "post accept fight");
        // 4.7.84 (SP-2): the board's later pages; posting has its own page.
        item("spar", "sparboard:**", "Spar Board", "spar|main");
        p("spar", "board_post", "Post a spar", "spar|sparboard", "post kind wager range looking for wants");
        p("spar", "result", "Last fight", "spar|sparboard", "rematch");
        p("spar", "board_wager", "Wager", "spar|board_post", "stake bet coins points");
        p("spar", "board_wager_coins", "Wager Ancient Coins", "spar|board_wager", "stake coins");
        p("spar", "board_wager_points", "Wager Dojo Points", "spar|board_wager", "stake points");
        item("spar", "board_accept:**", "Accept a spar", "spar|sparboard");
        p("spar", "pick_challenge", "Challenge a player", "spar|sparboard");
        item("spar", "pick_challenge:**", "Challenge a player", "spar|sparboard");
        p("spar", "watch", "Watch spars", "spar|sparboard", "spectate");
        p("spar", "top_tp", "Hall of Infamy", "spar|leaderboard", "leaderboard");
        item("spar", "infamy", "Hall of Infamy", "spar|leaderboard");
        p("spar", "top_sessions", "Sessions", "spar|top_tp");
        p("spar", "top_perfect", "Perfect spars", "spar|top_tp");
        p("spar", "top_combo", "Combo", "spar|top_tp");
        p("spar", "top_time", "Time", "spar|top_tp");
        p("spar", "stats", "Your spar stats", "spar|leaderboard");
        item("spar", "fight_report:*", "Fight report", "spar|stats");
        p("spar", "weights", "Training weights", "spar|main", "training weights");
        item("spar", "weights_input:*", "Weight amount", "spar|weights");
        item("spar", "weights_confirm:**", "Buy weights", "spar|weights");
        p("spar", "settings", "Spar TP chat", "spar|main", "tp chat messages mentor settings");
        staff("spar", "admin", "Sparring staff tools", "spar|main");

        // ---- Dojo (Home / Upgrades / Wars), its own system since 4.7.78 (was Sparring > Dojo)
        p("dojo", "home", "Dojo", "hub|main", "my dojo");
        item("dojo", "main", "Dojo", "hub|main");
        item("dojo", "dojo", "Dojo", "hub|main");
        item("dojo", "dojo_home", "Dojo", "hub|main");
        // 4.7.88: a player in two dojos (their own and their master's) picks one here first.
        p("dojo", "dojo_mine", "Your dojos", "hub|main", "my dojos switch master disciple pick");
        p("dojo", "dojo_upgrades", "Upgrades", "dojo|home", "build hall");
        item("dojo", "upgrades", "Upgrades", "dojo|home");
        // 4.7.84 (X7): one word, "ring" (the page id stays dojo_up:arena).
        p("dojo", "dojo_up:arena", "Sparring rings", "dojo|dojo_upgrades", "ring arena sparring");
        p("dojo", "dojo_up:training_hall", "Training hall", "dojo|dojo_upgrades");
        p("dojo", "dojo_up:meditation", "Meditation room", "dojo|dojo_upgrades");
        p("dojo", "dojo_up:spectator", "Spectator balcony", "dojo|dojo_upgrades");
        p("dojo", "dojo_up:trophy_hall", "Trophy hall", "dojo|dojo_upgrades");
        p("dojo", "dojo_up:quarters", "Master quarters", "dojo|dojo_upgrades");
        p("dojo", "dojo_up:garden", "Garden grounds", "dojo|dojo_upgrades");
        p("dojo", "dojo_up:capacity", "Disciple capacity", "dojo|dojo_upgrades");
        p("dojo", "dojo_style", "Style and bonus", "dojo|dojo_upgrades");
        item("dojo", "dojo_style_ask:**", "Change style", "dojo|dojo_style");
        item("dojo", "dojo_bonus_ask:**", "Buy a bonus", "dojo|dojo_style");
        // 4.7.84 (DJ-5): style and bonus are separate pages.
        p("dojo", "dojo_style_pick", "Change style", "dojo|dojo_style", "style pick change");
        p("dojo", "dojo_wars", "Wars", "dojo|home", "war bracket gauntlet");
        item("dojo", "wars", "Wars", "dojo|home");
        item("dojo", "dojo_war", "Wars", "dojo|home");
        p("dojo", "dojo_war_pick", "Declare a war", "dojo|dojo_wars", "challenge dojo war");
        item("dojo", "dojo_war_pick:**", "Declare a war", "dojo|dojo_wars");
        item("dojo", "dojo_war_fmt:**", "War format", "dojo|dojo_wars");
        item("dojo", "dojo_war_view:**", "War", "dojo|dojo_wars");
        item("dojo", "dojo_war_pending", "War invites", "dojo|dojo_wars");
        item("dojo", "dojo_war_pending:**", "War invites", "dojo|dojo_wars");
        item("dojo", "dojo_war_pending_decide:**", "War invite", "dojo|dojo_war_pending");
        // 4.7.84 (DJ-12): the pick_dojo_challenge pages are gone (wars are declared from Wars).
        item("dojo", "dojo_top_rp", "Top dojos", "dojo|dojo_wars");
        item("dojo", "dojo_top_wars", "Top dojos", "dojo|dojo_wars");
        item("dojo", "dojo_hof", "Hall of Chosen", "dojo|dojo_wars");
        p("dojo", "dojo_list", "Dojo leaderboard", "dojo|dojo_wars", "browse dojos ranking clout top join ask master");
        item("dojo", "dojo_list:**", "Dojo leaderboard", "dojo|dojo_wars");
        p("dojo", "dojo_list_wars", "Dojo leaderboard - war wins", "dojo|dojo_wars", "war wins ranking top dojos");
        item("dojo", "dojo_list_wars:**", "Dojo leaderboard - war wins", "dojo|dojo_wars");
        // 4.7.84 (DJ-3): a list row opens the dojo's profile, which has "Ask to join".
        item("dojo", "dojo_view:**", "Dojo profile", "dojo|dojo_list");
        item("dojo", "dojo_join_ask:**", "Ask to join", "dojo|dojo_list");
        // 4.7.84 (DJ-1, DJ-2): Home's settings page, and one Members page for "Roster and bonds" and "Members and
        // elites" (their old ids open it).
        p("dojo", "dojo_settings", "Dojo settings", "dojo|home", "rename banner close");
        p("dojo", "dojo_members", "Members", "dojo|home", "members roster elites disciples title");
        item("dojo", "dojo_members:**", "Members", "dojo|home");
        item("dojo", "dojo_member:**", "Member", "dojo|dojo_members");
        item("dojo", "dojo_elites", "Members", "dojo|home");
        item("dojo", "dojo_close", "Close dojo", "dojo|home");
        item("dojo", "dojo_roster", "Members", "dojo|home");
        // 4.7.85: an old link (the one Members page replaced it in 4.7.84); kept routable, not a page of its own.
        item("dojo", "dojo_members_own", "Dojo members", "dojo|dojo_members");
        item("dojo", "dojo_members_*", "Dojo members", "dojo|dojo_members");
        p("dojo", "dojo_previous_members_own", "Previous members", "dojo|dojo_members", "left");
        item("dojo", "dojo_previous_members", "Previous members", "dojo|dojo_members");
        item("dojo", "dojo_previous_members_*", "Previous members", "dojo|dojo_members");
        p("dojo", "mentor", "Training bonds", "dojo|home", "master apprentice");
        p("dojo", "pick_apprentice", "Recruit", "dojo|mentor");
        item("dojo", "pick_apprentice:**", "Recruit", "dojo|mentor");
        p("dojo", "pick_mentor", "Request a master", "dojo|mentor");
        item("dojo", "pick_mentor:**", "Request a master", "dojo|mentor");
        p("dojo", "pending", "Bond invites", "dojo|mentor");
        item("dojo", "pending:*", "Bond invites", "dojo|mentor");
        item("dojo", "pending_decide:**", "Bond invite", "dojo|pending");
        p("dojo", "pick_release", "Release", "dojo|mentor");
        item("dojo", "pick_release:**", "Release", "dojo|mentor");
        item("dojo", "pick_confirm:**", "Confirm", "dojo|mentor");
        // founding and identity (4.7.78)
        p("dojo", "found", "Found a dojo", "dojo|home", "create start new dojo");
        item("dojo", "found_style", "Style", "dojo|found");
        item("dojo", "found_banner", "Banner", "dojo|found");
        item("dojo", "found_confirm", "Confirm", "dojo|found");
        item("dojo", "dojo_banner", "Dojo banner", "dojo|home");
        item("dojo", "dojo_banner_ask:**", "Dojo banner", "dojo|dojo_banner");
        item("dojo", "dojo_rename", "Rename the dojo", "dojo|home");

        // ---- Prestige (Home / Prestige / Shop)
        p("prestige", "main", "Prestige", "hub|main", "favors favor favour");
        // 4.7.84 (P-1): tabs are Home / Shop / Trade, with "Prestige now" on Home (turnin opens Home).
        item("prestige", "turnin", "Prestige", "prestige|main");
        item("prestige", "confirm", "Prestige now", "prestige|main");
        p("prestige", "shop", "Shop", "prestige|main", "favour favor spend buy");
        p("prestige", "exchange", "Trade", "prestige|main", "trade favor exchange turn in favour");
        p("prestige", "skills", "Skill shop", "prestige|shop", "skills buy levels");
        p("prestige", "tiers", "Own tiers for good", "prestige|shop", "tier permanent own difficulty");
        p("prestige", "effects", "Effects", "prestige|shop", "majin mutant ultimate");
        p("prestige", "mastery_wallet", "Mastery wallet", "prestige|shop", "god ki form mastery points");
        p("prestige", "mastery_lines", "Forms", "prestige|mastery_wallet");
        item("prestige", "mastery_lines:*", "Apply points to a form", "prestige|mastery_wallet");
        // mw_form:<s|n>:<group>:<form>[:<line page>] goes back to that line's page.
        item("prestige", "mw_form:*:*:*", "Form mastery", "prestige|mw_line:$1:$2");
        item("prestige", "mw_form:*:*:*:*", "Form mastery", "prestige|mw_line:$1:$2:$4");
        item("prestige", "mw_line:**", "Mastery line", "prestige|mastery_lines");
        p("prestige", "custom", "Custom shop", "prestige|shop", "perk");
        item("prestige", "perk:*", "Perk", "prestige|custom");
        item("prestige", "ask:custom_buy:*", "Buy perk", "prestige|perk:$1");
        item("prestige", "ask:custom_remove:*", "Remove perk", "prestige|perk:$1");
        item("prestige", "ask:unmajin", "Remove Majin", "prestige|effects");
        item("prestige", "ask:unmutant", "Remove Mutant", "prestige|effects");
        item("prestige", "ask:turnin:*", "Exchange", "prestige|exchange");
        item("prestige", "ask:skill:*", "Skill", "prestige|skills");
        item("prestige", "ask:tier:*", "Tier", "prestige|tiers");
        item("prestige", "ask:majin", "Majin", "prestige|effects");
        item("prestige", "ask:mutant", "Mutant", "prestige|effects");
        item("prestige", "ask:ultimate", "Ultimate", "prestige|effects");
        item("prestige", "ask:mastery_buy:*", "Buy mastery points", "prestige|mastery_wallet");
        item("prestige", "ask:mastery_apply:*:*:*:*", "Level up", "prestige|mw_form:$1:$2:$3");
        item("prestige", "ask:mastery_apply:**", "Level up", "prestige|mastery_lines");

        // ---- Character Services (Home / Saga / Android)
        // 4.7.84 (C-2): tabs are Home / Looks / Story; Android folds into Home.
        p("character", "main", "Character Services", "hub|main", "race class model android");
        p("character", "looks", "Looks", "character|main",
                "look looks appearance skin hair eyes colour color model parts");
        p("character", "reset:0", "Saga reset", "character|main", "reset story saga replay");
        item("character", "reset:*", "Saga reset", "character|main");
        item("character", "saga_confirm:*", "Reset a saga", "character|reset:0");
        item("character", "android_remove", "Android", "character|main");
        item("character", "android_remove_confirm", "Remove Android", "character|main");
        item("character", "android_convert_confirm", "Convert to Android", "character|main");
        staff("character", "android_staff", "Android (another player)", "character|main");
        PAGES.add(new Page("character", "android_staff:*", "Android (another player)", "character|main", true, false, ""));
        PAGES.add(new Page("character", "android_target:**", "Android target", "character|android_staff", true, false, ""));
        item("character", "android_confirm:**", "Confirm Android", "character|main");
        p("character", "race:0", "Change race", "character|main");
        item("character", "race:*", "Change race", "character|main");
        item("character", "race_confirm:**", "Change race", "character|race:0");
        p("character", "class:0", "Change class", "character|main");
        item("character", "class:*", "Change class", "character|main");
        item("character", "class_confirm:*", "Change class", "character|class:0");
        p("character", "bones:0", "Model and colors", "character|looks", "head bone hair horns ears antenna customize");
        item("character", "bones:*", "Model and colors", "character|looks");
        item("character", "clear_ask:default:bones:**", "Race default", "character|bones:0");
        item("character", "clear_ask:bones:bones:**", "Clear parts", "character|bones:0");
        item("character", "bone_ask:*:**", "Unlock part", "character|bones:$1");
        p("character", "bodyparts:0", "Body parts", "character|looks", "tail ears wings horns colour");
        item("character", "bodyparts:*", "Body parts", "character|looks");
        item("character", "bodyparts:*:*", "Body parts", "character|bodyparts:$1");
        p("character", "bodypart_find", "Find a body part", "character|bodyparts:0", "search");
        item("character", "bodypart_find:*", "Find a body part", "character|bodyparts:0");
        item("character", "clear_ask:body:bodyparts:**", "Clear parts", "character|bodyparts:0");
        item("character", "part_ask:**", "Unlock part", "character|bodyparts:0");
        p("character", "bodycolors", "Part colors", "character|looks", "color colour");
        item("character", "bodycolors:**", "Part color", "character|bodycolors");
        p("character", "reskin", "Change skin", "character|looks", "skin reskin hair eyes look appearance colour");
        item("character", "reskin_confirm", "Change skin", "character|looks");
        staff("character", "form_preview:0", "Preview forms", "character|looks");
        PAGES.add(new Page("character", "form_preview:*", "Preview forms", "character|looks", true, false, ""));

        // ---- Progression (Home / Boosts / Coins)
        p("progression", "main", "Progression", "hub|main", "boost coins");
        p("progression", "boosts", "Boosts", "progression|main");
        p("progression", "coins", "Coins", "progression|main", "ancient coins");
        p("progression", "boosts_tp", "TP Boost", "progression|boosts");
        p("progression", "boosts_mastery", "Mastery Boost", "progression|boosts");

        // ---- staff systems
        staff("admin", "admin", "Staff Admin", "hub|main");
        PAGES.add(new Page("admin", "main", "Staff Admin", "hub|main", true, false, ""));
        PAGES.add(new Page("admin", "**", "Staff Admin", "admin|admin", true, false, ""));
        staff("skills", "main", "Skills", "hub|main");
        PAGES.add(new Page("skills", "**", "Skills", "skills|main", true, false, ""));
        // 4.7.84 (H2): donators open Skill Check from Home, so search finds it too (the screen gates who opens it).
        p("skillcheck", "main", "Skill Check", "hub|main", "skill check skills donator");
        PAGES.add(new Page("skillcheck", "**", "Skill Check", "skillcheck|main", true, false, ""));
        staff("config", "edit:difficulty:0:", "Difficulty settings", "admin|admin");
        staff("config", "edit:prestige-shop:0:", "Prestige shop settings", "admin|admin");
        PAGES.add(new Page("config", "edit:*:*:**", "Settings", "config|edit:$1:0:", true, false, ""));
        PAGES.add(new Page("config", "review:**", "Review changes", "admin|admin", true, false, ""));
        PAGES.add(new Page("config", "**", "Settings", "admin|admin", true, false, ""));

        // ---- 4.7.84 (N4) short titles for Back labels: the full title does not fit "Back to ..." on a footer.
        shortTitle("spar", "board_wager_coins", "Coin wager");
        shortTitle("spar", "board_wager_points", "Points wager");
        shortTitle("spar", "pick_challenge", "Challenge");
        shortTitle("spar", "pick_challenge:**", "Challenge");
        shortTitle("spar", "admin", "Staff tools");
        shortTitle("dojo", "dojo_list_wars", "War wins");
        shortTitle("dojo", "dojo_list_wars:**", "War wins");
        shortTitle("spar", "board_post", "Post");
        shortTitle("dojo", "dojo_settings", "Settings");
        shortTitle("dojo", "dojo_members:**", "Members");
        shortTitle("dojo", "dojo_view:**", "Dojo");
        shortTitle("dojo", "dojo_style_pick", "Style");
        shortTitle("prestige", "mastery_lines:*", "Forms");
        shortTitle("prestige", "mastery_wallet", "Wallet");
        shortTitle("prestige", "mw_line:**", "Line");
        shortTitle("prestige", "mw_form:*:*:*", "Form");
        shortTitle("prestige", "mw_form:*:*:*:*", "Form");
        shortTitle("prestige", "ask:mastery_apply:*:*:*:*", "Level up");
        shortTitle("prestige", "ask:mastery_apply:**", "Level up");
        shortTitle("prestige", "ask:mastery_buy:*", "Buy points");
        shortTitle("character", "main", "Character");
        shortTitle("character", "android_convert_confirm", "Android");
        shortTitle("character", "android_staff", "Android staff");
        shortTitle("config", "edit:prestige-shop:0:", "Shop settings");
    }

    // ------------------------------------------------------------------ lookups

    /** Every entry, in match order. */
    public static List<Page> all() {
        return Collections.unmodifiableList(PAGES);
    }

    /** The entry for {@code screen} page {@code page}, or null. */
    public static Page find(String screen, String page) {
        String s = norm(screen), pg = page == null ? "" : page.trim();
        for (Page e : PAGES) {
            if (e.screen().equals(s) && match(e.pattern(), pg) != null) {
                return e;
            }
        }
        return null;
    }

    /** The wildcard parts {@code pattern} matched in {@code page}, or null when it does not match. */
    static List<String> match(String pattern, String page) {
        String[] p = pattern.split(":", -1);
        String[] g = page.split(":", -1);
        List<String> caught = new ArrayList<>();
        int i = 0;
        for (; i < p.length; i++) {
            if (p[i].equals("**")) {
                caught.add(String.join(":", java.util.Arrays.copyOfRange(g, Math.min(i, g.length), g.length)));
                return caught;
            }
            if (i >= g.length) {
                return null;
            }
            if (p[i].equals("*")) {
                caught.add(g[i]);
            } else if (p[i].endsWith("*") && p[i].length() > 1) {
                String head = p[i].substring(0, p[i].length() - 1);
                if (!g[i].startsWith(head)) {
                    return null;
                }
                caught.add(g[i].substring(head.length()));
            } else if (!p[i].equals(g[i])) {
                return null;
            }
        }
        return i == g.length ? caught : null;
    }

    /** The parent of {@code screen} page {@code page} as {screen, page}, or null at the main menu or when unknown. */
    public static String[] parent(String screen, String page) {
        Page e = find(screen, page);
        if (e == null || e.parent() == null) {
            return null;
        }
        String parent = e.parent();
        List<String> caught = match(e.pattern(), page == null ? "" : page.trim());
        if (caught != null) {
            for (int i = caught.size(); i >= 1; i--) {
                parent = parent.replace("$" + i, caught.get(i - 1));
            }
        }
        int bar = parent.indexOf('|');
        return new String[] {parent.substring(0, bar), parent.substring(bar + 1)};
    }

    /** The title for {@code screen} page {@code page} (the entry's title), or the screen id when unknown. */
    public static String title(String screen, String page) {
        Page e = find(screen, page);
        return e == null ? norm(screen) : e.title();
    }

    /**
     * 4.7.84 (N4): the name a Back label uses for {@code screen} page {@code page}: the full title when "Back to " +
     * it fits a footer button, else the entry's short title when it has one, else the full title (then
     * {@link UltraStyle#backTo} says a plain "Back" rather than cut it).
     */
    public static String backTitle(String screen, String page) {
        Page e = find(screen, page);
        if (e == null) {
            return norm(screen);
        }
        if (UltraStyle.backFits(e.title())) {
            return e.title();
        }
        String s = SHORT.get(e.screen() + "|" + e.pattern());
        return s == null || s.isBlank() ? e.title() : s;
    }

    /** The short title registered for {@code screen} page {@code page}, or null. */
    public static String shortTitle(String screen, String page) {
        Page e = find(screen, page);
        return e == null ? null : SHORT.get(e.screen() + "|" + e.pattern());
    }

    /** Main menu first, down to the page itself; just the page when its chain is broken. */
    public static List<Ref> crumbs(String screen, String page) {
        List<Ref> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String s = norm(screen), pg = page == null ? "" : page.trim();
        while (s != null && seen.add(s + "|" + pg) && out.size() < 12) {
            out.add(0, new Ref(s, pg, title(s, pg)));
            String[] up = parent(s, pg);
            if (up == null) {
                break;
            }
            s = up[0];
            pg = up[1];
        }
        return out;
    }

    /** The crumbs as words: "Main menu > Sparring > Dojo > Upgrades". */
    public static String path(String screen, String page) {
        List<String> words = new ArrayList<>();
        for (Ref r : crumbs(screen, page)) {
            words.add(r.title());
        }
        return String.join(" > ", words);
    }

    /** True for a system's Home page (its parent is the main menu). */
    public static boolean systemHome(String screen, String page) {
        String[] up = parent(screen, page);
        return up != null && up[0].equals(HUB) && up[1].equals(HUB_MAIN) && !HUB.equals(norm(screen));
    }

    // ------------------------------------------------------------------ search

    /** A search hit: the page, its path, how well it matched (lower first). */
    public record Hit(Page page, String path, int rank) {}

    /**
     * Pages whose title, path or keywords contain every word of {@code query} (case-insensitive), title hits first.
     * Only searchable entries, and staff entries only for {@code staff}. Never more than {@code max}.
     */
    public static List<Hit> search(String query, boolean staff, int max) {
        List<Hit> hits = searchAll(query, staff);
        return hits.size() > max ? hits.subList(0, max) : hits;
    }

    /**
     * Every match for {@code query} (no cut), so the hub can say "10 matches, showing 8" (4.7.84, H2).
     *
     * <ul>
     *   <li>The path is matched without its leading "Main menu &gt; ", so "menu" and "main" no longer match every
     *       page.</li>
     *   <li>A query word longer than 3 letters that ends in "s" also matches without it ("skills" finds Skill
     *       shop).</li>
     * </ul>
     */
    public static List<Hit> searchAll(String query, boolean staff) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Hit> hits = new ArrayList<>();
        if (q.isEmpty()) {
            return hits;
        }
        String[] words = q.split("\\s+");
        Map<String, Boolean> dupes = new LinkedHashMap<>();
        for (Page e : PAGES) {
            if (!e.search() || (e.staff() || UltraGuiActions.isStaffOnlySystem(e.screen())) && !staff) {
                continue;
            }
            String path = path(e.screen(), e.pattern());
            String where = path.startsWith(MAIN_PREFIX) ? path.substring(MAIN_PREFIX.length()) : path;
            String title = e.title().toLowerCase(Locale.ROOT);
            String hay = (title + " " + where + " " + e.keywords()).toLowerCase(Locale.ROOT);
            boolean all = true;
            for (String w : words) {
                all &= hay.contains(w)
                        || (w.length() > 3 && w.endsWith("s") && hay.contains(w.substring(0, w.length() - 1)));
            }
            if (!all || dupes.put(path, true) != null) {
                continue;
            }
            int rank = title.equals(q) ? 0 : title.startsWith(q) ? 1 : title.contains(q) ? 2 : 3;
            hits.add(new Hit(e, path, rank));
        }
        hits.sort((a, b) -> a.rank() != b.rank() ? Integer.compare(a.rank(), b.rank())
                : Integer.compare(a.path().length(), b.path().length()));
        return hits;
    }

    /** The words every path starts with; search ignores them. */
    static final String MAIN_PREFIX = "Main menu > ";

    /**
     * 4.7.84 (H2): what players type that must find a page (the query, then the {@code screen|pattern} it must find),
     * checked by {@link #problems} for a player who is not staff.
     */
    static final String[][] MUST_FIND = {
            {"hair", "character|reskin"}, {"hair", "character|bones:0"}, {"boost", "difficulty|main"},
            {"skills", "prestige|skills"}, {"colour", "hub|skin_custom"}, {"colour", "character|bodycolors"},
            {"favour", "prestige|shop"}, {"skill check", "skillcheck|main"}, {"ring", "dojo|dojo_up:arena"}};

    // ------------------------------------------------------------------ self-test

    /**
     * Every parent of a concrete entry resolves to an entry, every chain reaches the main menu without a loop, every
     * title is set, every searchable page is found by its own title. Returns the problems (empty when fine).
     */
    /** Page ids that always open a guide step (DifficultyTutorial.route), whatever the player's state. */
    static final java.util.Set<String> REPLAY = java.util.Set.of("difficulty|guide", "difficulty|guide_tiers",
            "difficulty|guide_your_tier");

    public static List<String> problems() {
        List<String> out = new ArrayList<>();
        for (Page e : PAGES) {
            if (e.title() == null || e.title().isBlank()) {
                out.add("no title: " + e.screen() + "|" + e.pattern());
            }
            if (e.parent() == null) {
                if (!(e.screen().equals(HUB) && e.pattern().equals(HUB_MAIN))) {
                    out.add("no parent: " + e.screen() + "|" + e.pattern());
                }
                continue;
            }
            if (e.pattern().contains("*")) {
                continue;                               // a family: its parent depends on the page; the audit checks it
            }
            List<Ref> chain = crumbs(e.screen(), e.pattern());
            Ref top = chain.isEmpty() ? null : chain.get(0);
            if (top == null || !top.screen().equals(HUB) || !top.page().equals(HUB_MAIN)) {
                out.add("chain does not reach the main menu: " + e.screen() + "|" + e.pattern() + " ("
                        + path(e.screen(), e.pattern()) + ")");
            }
            String[] up = parent(e.screen(), e.pattern());
            // 4.7.73: a guide step id always replays the guide; only guide steps may lead back to one.
            if (up != null && REPLAY.contains(up[0] + "|" + up[1]) && !REPLAY.contains(e.screen() + "|" + e.pattern())) {
                out.add("parent replays the guide: " + e.screen() + "|" + e.pattern() + " -> " + e.parent());
            }
            if (up != null && find(up[0], up[1]) == null) {
                out.add("parent not registered: " + e.screen() + "|" + e.pattern() + " -> " + e.parent());
            }
            if (e.search()) {
                boolean found = false;
                for (Hit h : search(e.title(), true, 400)) {
                    found |= h.page() == e;
                }
                if (!found) {
                    out.add("search does not find: " + e.title() + " (" + e.screen() + "|" + e.pattern() + ")");
                }
            }
        }
        // 4.7.84 (H2): the words players type find their page, for a player who is not staff.
        for (String[] want : MUST_FIND) {
            boolean found = false;
            for (Hit h : searchAll(want[0], false)) {
                found |= want[1].equals(h.page().screen() + "|" + h.page().pattern());
            }
            if (!found) {
                out.add("search '" + want[0] + "' does not find " + want[1]);
            }
        }
        // "menu" and "main" are in every path; they must not match every page.
        int searchable = 0;
        for (Page e : PAGES) {
            searchable += e.search() && !e.staff() && !UltraGuiActions.isStaffOnlySystem(e.screen()) ? 1 : 0;
        }
        for (String word : new String[] {"main", "menu"}) {
            int n = searchAll(word, false).size();
            if (n * 4 > searchable) {
                out.add("search '" + word + "' matches " + n + " of " + searchable + " pages");
            }
        }
        // N4: every short title belongs to an entry and fits "Back to ..." on a footer.
        for (Map.Entry<String, String> sh : SHORT.entrySet()) {
            boolean known = false;
            for (Page x : PAGES) {
                known |= (x.screen() + "|" + x.pattern()).equals(sh.getKey());
            }
            if (!known) {
                out.add("short title for an unknown entry: " + sh.getKey());
            } else if (!UltraStyle.backFits(sh.getValue())) {
                out.add("short title does not fit 'Back to': " + sh.getValue() + " (" + sh.getKey() + ")");
            }
        }
        return out;
    }

    private static String norm(String screen) {
        return screen == null ? "" : screen.trim().toLowerCase(Locale.ROOT);
    }
}
