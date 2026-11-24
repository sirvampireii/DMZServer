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

    static {
        // ---- the hub (Home / Goals / More)
        p(HUB, HUB_MAIN, "Main menu", null, "home hub start");
        p(HUB, "more", "More", "hub|main", "settings meditation");
        p(HUB, "search", "Search", "hub|main", "find");
        p(HUB, "skins", "Menu skins", "hub|more", "theme look colors background");
        p(HUB, "skin_custom", "My colors", "hub|skins", "custom skin hex");
        item(HUB, "skin:**", "Menu skin", "hub|skins");

        // ---- Difficulty (Home / Tiers / Summons)
        // 4.7.73: the state-routed ids (main, tiers, your_tier) are the pages and parents. The guide ids always replay
        // the guide (DifficultyTutorial.route), so they are never a parent and only the guide itself is searchable.
        p("difficulty", "main", "Difficulty", "hub|main", "tier level momentum your tier");
        p("difficulty", "guide", "Difficulty guide", "difficulty|main", "how tutorial help");
        p("difficulty", "tiers", "Tiers", "difficulty|main", "pick level");
        item("difficulty", "guide_tiers", "Tiers", "difficulty|guide");
        p("difficulty", "summons", "Summons", "difficulty|main", "boss dragon wither warden");
        staff("difficulty", "stats", "Combat rating", "difficulty|main");
        item("difficulty", "tier_ask:*", "Tier", "difficulty|tiers");
        item("difficulty", "guide_your_tier", "Your tier", "difficulty|guide_tiers");
        item("difficulty", "your_tier", "Your tier", "difficulty|main");
        item("difficulty", "end_dragon", "End Dragon", "difficulty|summons");
        item("difficulty", "summon_ask:*", "Summon", "difficulty|summons");
        item("difficulty", "clear_tier_ask", "Clear active tier", "difficulty|main");
        item("difficulty", "difficulty_off_ask", "Turn difficulty off", "difficulty|main");
        item("difficulty", "wither", "Wither", "difficulty|summons");
        item("difficulty", "warden", "Warden", "difficulty|summons");
        p("difficulty", "titles_p0", "Titles", "difficulty|main", "badge");
        item("difficulty", "titles_p*", "Titles", "difficulty|titles_p0");
        item("difficulty", "titles", "Titles", "difficulty|main");
        item("difficulty", "title_detail:*", "Title", "difficulty|titles_p0");
        item("difficulty", "title_unequip_ask", "Unequip title", "difficulty|titles_p0");
        p("difficulty", "team", "Rival teams", "difficulty|main");
        staff("difficulty", "admin", "Server tools", "difficulty|main");
        staff("difficulty", "eventlog", "Event log", "difficulty|admin");
        item("difficulty", "flush_ask", "Flush", "difficulty|eventlog");

        // ---- Rivals (Home / Rival / Challenge)
        p("rival", "home", "Rivals", "hub|main", "rp nemesis");
        item("rival", "main", "Rivals", "hub|main");
        p("rival", "actions", "Rival", "rival|home");
        p("rival", "challenge", "Challenge", "rival|home");
        // 4.7.73: the pager's later pages (placePrevNext adds :N).
        item("rival", "actions:*", "Rival", "rival|home");
        item("rival", "challenge:*", "Challenge", "rival|home");
        p("rival", "info", "My rival stats", "rival|home");
        p("rival", "top", "Leaderboard", "rival|home", "ranking");
        p("rival", "records:history", "Records", "rival|home", "history");
        p("rival", "records:stats", "Record stats", "rival|records:history");
        p("rival", "records:season", "Season", "rival|records:history");
        p("rival", "records:quests", "Quests", "rival|records:history");
        p("rival", "records:achievements", "Achievements", "rival|records:history");
        p("rival", "records:hof", "Hall of fame", "rival|records:history");
        p("rival", "records:journal", "Journal", "rival|records:history");
        p("rival", "records:title", "Title", "rival|records:history");
        item("rival", "records:**", "Records", "rival|records:history");
        p("rival", "settings", "Rival settings", "rival|home", "tp chat instinct");
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
        item("rival", "list_detail:**", "Rival", "rival|actions");
        item("rival", "remove_confirm:**", "Remove rival", "rival|actions");
        item("rival", "accept_confirm:**", "Accept request", "rival|requests");
        item("rival", "rival_type:**", "Rival type", "rival|pick_rival");
        p("rival", "pick_replace_mutual", "Replace a rival", "rival|actions");
        item("rival", "pick_replace_mutual:**", "Replace a rival", "rival|actions");
        item("rival", "replace_confirm:**", "Replace rival", "rival|pick_replace_mutual");

        // ---- Sparring (Home / Dojo / Spar Board)
        p("spar", "main", "Sparring", "hub|main", "spar");
        p("spar", "dojo", "Dojo", "spar|main", "my dojo");
        item("spar", "dojo_home", "Dojo", "spar|main");
        p("spar", "sparboard", "Spar Board", "spar|main", "post accept fight");
        p("spar", "result", "Last fight", "spar|sparboard", "rematch");
        p("spar", "pick_challenge", "Challenge a player", "spar|sparboard");
        item("spar", "pick_challenge:**", "Challenge a player", "spar|sparboard");
        p("spar", "watch", "Watch spars", "spar|sparboard", "spectate");
        p("spar", "dojo_upgrades", "Upgrades", "spar|dojo", "build hall");
        p("spar", "dojo_up:arena", "Sparring arenas", "spar|dojo_upgrades", "ring");
        p("spar", "dojo_up:training_hall", "Training hall", "spar|dojo_upgrades");
        p("spar", "dojo_up:meditation", "Meditation room", "spar|dojo_upgrades");
        p("spar", "dojo_up:spectator", "Spectator balcony", "spar|dojo_upgrades");
        p("spar", "dojo_up:trophy_hall", "Trophy hall", "spar|dojo_upgrades");
        p("spar", "dojo_up:quarters", "Master quarters", "spar|dojo_upgrades");
        p("spar", "dojo_up:garden", "Garden grounds", "spar|dojo_upgrades");
        p("spar", "dojo_up:capacity", "Disciple capacity", "spar|dojo_upgrades");
        p("spar", "dojo_style", "Style and bonus", "spar|dojo");
        item("spar", "dojo_style_ask:**", "Change style", "spar|dojo_style");
        item("spar", "dojo_bonus_ask:**", "Buy a bonus", "spar|dojo_style");
        p("spar", "dojo_elites", "Members and elites", "spar|dojo");
        p("spar", "dojo_wars", "Wars", "spar|dojo", "war bracket gauntlet");
        item("spar", "wars", "Wars", "spar|dojo");
        item("spar", "dojo_war", "Wars", "spar|dojo");
        p("spar", "dojo_war_pick", "Declare a war", "spar|dojo_wars", "challenge dojo war");
        item("spar", "dojo_war_pick:**", "Declare a war", "spar|dojo_wars");
        item("spar", "dojo_war_fmt:**", "War format", "spar|dojo_wars");
        item("spar", "dojo_war_view:**", "War", "spar|dojo_wars");
        item("spar", "dojo_war_pending", "War invites", "spar|dojo_wars");
        item("spar", "dojo_war_pending:**", "War invites", "spar|dojo_wars");
        // 4.7.73: the war pages' own buttons (Challenge dojo, Top dojos, Hall of Chosen) and the members aliases.
        item("spar", "pick_dojo_challenge", "Challenge a dojo", "spar|dojo_wars");
        item("spar", "pick_dojo_challenge:*", "Challenge a dojo", "spar|dojo_wars");
        item("spar", "dojo_top_rp", "Top dojos", "spar|dojo_wars");
        item("spar", "dojo_top_wars", "Top dojos", "spar|dojo_wars");
        item("spar", "dojo_hof", "Hall of Chosen", "spar|dojo_wars");
        item("spar", "dojo_members", "Dojo members", "spar|dojo_roster");
        item("spar", "dojo_previous_members", "Previous members", "spar|dojo_roster");
        item("spar", "dojo_war_pending_decide:**", "War invite", "spar|dojo_war_pending");
        p("spar", "dojo_list", "Dojo leaderboard", "spar|dojo", "browse dojos ranking clout top");
        item("spar", "dojo_list:**", "Dojo leaderboard", "spar|dojo");
        p("spar", "dojo_list_wars", "Dojo leaderboard - war wins", "spar|dojo", "war wins ranking top dojos");
        item("spar", "dojo_list_wars:**", "Dojo leaderboard - war wins", "spar|dojo");
        item("spar", "dojo_close", "Close dojo", "spar|dojo");
        p("spar", "mentor", "Training bonds", "spar|dojo", "master apprentice");
        p("spar", "dojo_roster", "Roster and bonds", "spar|dojo");
        p("spar", "dojo_members_own", "Dojo members", "spar|dojo_roster", "roster");
        item("spar", "dojo_members_*", "Dojo members", "spar|dojo_roster");
        p("spar", "dojo_previous_members_own", "Previous members", "spar|dojo_roster", "left");
        item("spar", "dojo_previous_members_*", "Previous members", "spar|dojo_roster");
        p("spar", "pick_apprentice", "Recruit", "spar|mentor");
        item("spar", "pick_apprentice:**", "Recruit", "spar|mentor");
        p("spar", "pick_mentor", "Request a master", "spar|mentor");
        item("spar", "pick_mentor:**", "Request a master", "spar|mentor");
        p("spar", "pending", "Bond invites", "spar|mentor");
        item("spar", "pending:*", "Bond invites", "spar|mentor");
        item("spar", "pending_decide:**", "Bond invite", "spar|pending");
        p("spar", "pick_release", "Release", "spar|mentor");
        item("spar", "pick_release:**", "Release", "spar|mentor");
        item("spar", "pick_confirm:**", "Confirm", "spar|mentor");
        p("spar", "top_tp", "Hall of Infamy", "spar|main", "leaderboard");
        item("spar", "infamy", "Hall of Infamy", "spar|main");
        p("spar", "top_sessions", "Sessions", "spar|top_tp");
        p("spar", "top_perfect", "Perfect spars", "spar|top_tp");
        p("spar", "top_combo", "Combo", "spar|top_tp");
        p("spar", "top_time", "Time", "spar|top_tp");
        p("spar", "stats", "Your spar stats", "spar|main");
        item("spar", "fight_report:*", "Fight report", "spar|stats");
        p("spar", "weights", "Weights", "spar|main", "training weights");
        item("spar", "weights_input:*", "Weights", "spar|weights");
        item("spar", "weights_confirm:**", "Buy weights", "spar|weights");
        p("spar", "settings", "Spar settings", "spar|main", "tp chat");
        staff("spar", "admin", "Sparring staff tools", "spar|main");

        // ---- Prestige (Home / Prestige / Shop)
        p("prestige", "main", "Prestige", "hub|main", "favors");
        p("prestige", "turnin", "Prestige", "prestige|main", "turn in");
        item("prestige", "confirm", "Prestige now", "prestige|turnin");
        p("prestige", "shop", "Shop", "prestige|main");
        p("prestige", "exchange", "Exchange shop", "prestige|shop");
        p("prestige", "skills", "Skill shop", "prestige|shop");
        p("prestige", "tiers", "Difficulty tiers", "prestige|shop");
        p("prestige", "effects", "Effects", "prestige|shop", "majin mutant ultimate");
        p("prestige", "mastery_wallet", "Mastery wallet", "prestige|shop");
        p("prestige", "mastery_lines", "Apply points to a form", "prestige|mastery_wallet");
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
        p("character", "main", "Character Services", "hub|main", "race class model");
        p("character", "reset:0", "Saga", "character|main", "reset story");
        item("character", "reset:*", "Saga", "character|main");
        item("character", "saga_confirm:*", "Reset a saga", "character|reset:0");
        p("character", "android_remove", "Android", "character|main", "android");
        item("character", "android_convert_confirm", "Convert to Android", "character|android_remove");
        staff("character", "android_staff", "Android (another player)", "character|android_remove");
        PAGES.add(new Page("character", "android_staff:*", "Android (another player)", "character|android_remove", true, false, ""));
        PAGES.add(new Page("character", "android_target:**", "Android target", "character|android_staff", true, false, ""));
        item("character", "android_confirm:**", "Confirm Android", "character|android_remove");
        p("character", "race:0", "Change race", "character|main");
        item("character", "race:*", "Change race", "character|main");
        item("character", "race_confirm:**", "Change race", "character|race:0");
        p("character", "class:0", "Change class", "character|main");
        item("character", "class:*", "Change class", "character|main");
        item("character", "class_confirm:*", "Change class", "character|class:0");
        p("character", "bones:0", "Customize model", "character|main", "head bone");
        item("character", "bones:*", "Customize model", "character|main");
        item("character", "clear_ask:default:bones:**", "Race default", "character|bones:0");
        item("character", "clear_ask:bones:bones:**", "Clear parts", "character|bones:0");
        item("character", "bone_ask:*:**", "Unlock part", "character|bones:$1");
        p("character", "bodyparts:0", "Body parts", "character|main", "tail ears wings horns");
        item("character", "bodyparts:*", "Body parts", "character|main");
        item("character", "bodyparts:*:*", "Body parts", "character|bodyparts:$1");
        p("character", "bodypart_find", "Find a body part", "character|bodyparts:0", "search");
        item("character", "bodypart_find:*", "Find a body part", "character|bodyparts:0");
        item("character", "clear_ask:body:bodyparts:**", "Clear parts", "character|bodyparts:0");
        item("character", "part_ask:**", "Unlock part", "character|bodyparts:0");
        p("character", "bodycolors", "Part colors", "character|bodyparts:0", "color");
        item("character", "bodycolors:**", "Part color", "character|bodycolors");
        p("character", "reskin", "Reskin", "character|main", "skin");
        item("character", "reskin_confirm", "Review cost", "character|reskin");
        staff("character", "form_preview:0", "Preview forms", "character|main");
        PAGES.add(new Page("character", "form_preview:*", "Preview forms", "character|main", true, false, ""));

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
        staff("skillcheck", "main", "Skill Check", "hub|main");
        PAGES.add(new Page("skillcheck", "**", "Skill Check", "skillcheck|main", true, false, ""));
        staff("config", "edit:difficulty:0:", "Difficulty settings", "admin|admin");
        staff("config", "edit:prestige-shop:0:", "Prestige shop settings", "admin|admin");
        PAGES.add(new Page("config", "edit:*:*:**", "Settings", "config|edit:$1:0:", true, false, ""));
        PAGES.add(new Page("config", "review:**", "Review changes", "admin|admin", true, false, ""));
        PAGES.add(new Page("config", "**", "Settings", "admin|admin", true, false, ""));
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
            String title = e.title().toLowerCase(Locale.ROOT);
            String hay = (title + " " + path + " " + e.keywords()).toLowerCase(Locale.ROOT);
            boolean all = true;
            for (String w : words) {
                all &= hay.contains(w);
            }
            if (!all || dupes.put(path, true) != null) {
                continue;
            }
            int rank = title.equals(q) ? 0 : title.startsWith(q) ? 1 : title.contains(q) ? 2 : 3;
            hits.add(new Hit(e, path, rank));
        }
        hits.sort((a, b) -> a.rank() != b.rank() ? Integer.compare(a.rank(), b.rank())
                : Integer.compare(a.path().length(), b.path().length()));
        return hits.size() > max ? hits.subList(0, max) : hits;
    }

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
        return out;
    }

    private static String norm(String screen) {
        return screen == null ? "" : screen.trim().toLowerCase(Locale.ROOT);
    }
}
