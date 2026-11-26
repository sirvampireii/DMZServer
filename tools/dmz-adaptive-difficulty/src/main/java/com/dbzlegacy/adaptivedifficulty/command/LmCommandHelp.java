package com.dbzlegacy.adaptivedifficulty.command;

import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;

/**
 * Central help text for the one Legacy Mechanics command tree. 4.7.88: everything lives under {@code /lm}.
 * Player help ({@link #lmPlayer()} and the per-system player pages) is written as a short, friendly guide:
 * grouped by what you want to do, one command per line, no admin commands and no jargon. Staff help keeps the
 * detailed one-command-per-line layout of {@link LmStaffHelp}.
 */
public final class LmCommandHelp {
    private LmCommandHelp() {}

    // ------------------------------------------------------------------ player guide layout

    private record Topic(String name, String... lines) {}

    private static String guide(String title, String intro, Topic... topics) {
        StringBuilder sb = new StringBuilder();
        sb.append(UltraStyle.GOLD).append(title);
        if (intro != null && !intro.isBlank()) {
            sb.append('\n').append(UltraStyle.GRAY).append(intro.trim());
        }
        for (Topic t : topics) {
            sb.append('\n').append('\n').append(UltraStyle.YELLOW).append(t.name);
            for (String line : t.lines) {
                sb.append('\n').append(line);
            }
        }
        return sb.toString();
    }

    /** One line: the command to type, then what it does. */
    private static String say(String command, String whatItDoes) {
        return UltraStyle.WHITE + command + UltraStyle.GRAY + "  " + whatItDoes;
    }

    private static String tip(String text) {
        return UltraStyle.DARK_GRAY + text;
    }

    // ------------------------------------------------------------------ /lm help

    public static String lmPlayer() {
        return guide(
                "Legacy Mechanics: how to use /lm",
                "Every Legacy Mechanics command starts with /lm. Most things are easier in the menus.",
                new Topic("Open a menu",
                        say("/lm", "the main menu, with everything in one place"),
                        say("/lm difficulty", "pick how hard monsters are (and what they drop)"),
                        say("/lm rival", "rivals, duels and your rival season"),
                        say("/lm spar", "sparring, mentors and training weights"),
                        say("/lm dojo", "your dojo, or found and join one"),
                        say("/lm prestige", "the prestige shop"),
                        say("/lm character", "change race, class or looks"),
                        say("/lm open goals", "this week's goals")),
                new Topic("Your stuff",
                        say("/lm prestige balance", "how much favor and how many runs you have"),
                        say("/lm rival stats", "your rival record"),
                        say("/lm spar stats", "your sparring record"),
                        say("/lm hud on", "show fight, spar and war notices on screen"),
                        say("/lm hud off", "show them in chat instead"),
                        say("/lm revive", "bring yourself back (higher donor ranks, every 5 minutes)")),
                new Topic("Get help",
                        say("/lm help", "this list"),
                        say("/lm difficulty help", "more about one system; works for rival, spar, dojo, prestige "
                                + "and character too"),
                        tip("Stuck? Ask a staff member.")));
    }

    // ------------------------------------------------------------------ /lm admin help

    public static String lmAdmin() {
        return LmStaffHelp.build(
                "Legacy Mechanics staff",
                "/lm admin",
                UltraStyle.GRAY + "Staff tools and player fixes. In game, staff admin commands need creative mode "
                        + "(console is unaffected). Type " + UltraStyle.WHITE + "/lm admin help" + UltraStyle.GRAY
                        + " anytime.",
                new LmStaffHelp.Section("Config and data",
                        LmStaffHelp.cmd("/lm admin reload", "reload Legacy Mechanics config"),
                        LmStaffHelp.cmd("/lm admin config", "edit config files in a menu"),
                        LmStaffHelp.cmd("/lm admin syslog status|on|off|flush", "unified event log")),
                new LmStaffHelp.Section("Each system's staff tree",
                        LmStaffHelp.cmd("/lm admin difficulty help", "switch, whitelist, telemetry, inspect, set"),
                        LmStaffHelp.cmd("/lm admin rival help", "save, refresh, status"),
                        LmStaffHelp.cmd("/lm admin spar help", "save, status, mentor cooldown reset"),
                        LmStaffHelp.cmd("/lm admin progression help", "TP boost, mastery boost, android, flags"),
                        LmStaffHelp.cmd("/lm admin prestige help", "wallets, tiers, skills, shop, staff menu"),
                        LmStaffHelp.cmd("/lm admin character help", "set-race, set-class, reskin, cooldowns"),
                        LmStaffHelp.cmd("/lm admin skills", "skill unlock browser"),
                        LmStaffHelp.cmd("/lm admin enddragon help", "clear stray dragons, repair the podium"),
                        LmStaffHelp.cmd("/lm admin androidify <player>", "convert a player to android")),
                new LmStaffHelp.Section("Quest rewards",
                        LmStaffHelp.cmd("/lm mastery grant-first-superform <amount>",
                                "mastery on the first superform past the level 0 form"),
                        LmStaffHelp.cmd("/lm mastery grant-first-superform <amount> <player>",
                                "same superform grant for that player"),
                        LmStaffHelp.cmd("/lm mastery grant-first-superform <player> <amount>",
                                "same superform grant, name then amount"),
                        LmStaffHelp.cmd("/lm mastery grant-first-legendary <amount>",
                                "mastery on the first legendary form"),
                        LmStaffHelp.cmd("/lm mastery grant-first-legendary <amount> <player>",
                                "same legendary grant for that player"),
                        LmStaffHelp.cmd("/lm mastery grant-first-legendary <player> <amount>",
                                "same legendary grant, name then amount"),
                        LmStaffHelp.cmd("/lm mastery grant-first-godform <amount>",
                                "mastery on the first god form (Super Saiyan God for Saiyans)"),
                        LmStaffHelp.cmd("/lm mastery grant-first-godform <amount> <player>",
                                "same god form grant for that player"),
                        LmStaffHelp.cmd("/lm mastery grant-first-godform <player> <amount>",
                                "same god form grant, name then amount")),
                new LmStaffHelp.Section("Player resets",
                        LmStaffHelp.cmd("/lm admin clear <player>", "clear all LM data, online or offline"),
                        LmStaffHelp.cmd("/lm admin clear <player> rival|spar|difficulty|progression", "clear one system"),
                        LmStaffHelp.cmd("/lm admin character cooldown clear <player> [race|class|reskin]",
                                "clear character service cooldowns"),
                        LmStaffHelp.cmd("/lm fusionreset <player>", "clear a stuck fusion cooldown"),
                        LmStaffHelp.cmd("/lm revive <player>", "revive an online player (staff or revive access)"),
                        LmStaffHelp.cmd("/lm revive all", "revive every dead online player (staff or revive access)"),
                        LmStaffHelp.note("Revive keeps its own rules (no creative needed); 5-minute cooldown for non-ops")),
                new LmStaffHelp.Section("Look at a player",
                        LmStaffHelp.cmd("/lm admin inspect <player> [system]", "view menus as that player"),
                        LmStaffHelp.cmd("/lm admin inspect clear", "stop inspect mode"),
                        LmStaffHelp.note("Systems: hub, difficulty, rival, spar, dojo, skillcheck, character, "
                                + "prestige, progression, skills")),
                new LmStaffHelp.Section("Dojo (staff)",
                        LmStaffHelp.cmd("/lm dojo season advance", "end the dojo season now (asks to confirm)")),
                new LmStaffHelp.Section("World difficulty",
                        LmStaffHelp.note("LM no longer owns /difficulty. Vanilla /difficulty <level> (ops) sets the "
                                + "world difficulty.")));
    }

    // ------------------------------------------------------------------ difficulty

    public static String difficultyPlayer() {
        return guide(
                "Difficulty",
                "Harder monsters give better drops. You pick the tier.",
                new Topic("Commands",
                        say("/lm difficulty", "open the difficulty menu to pick a tier"),
                        say("/lm difficulty reset", "go back to the normal tier (free; tiers you bought stay yours)")),
                new Topic("Get help",
                        say("/lm help", "every /lm command")));
    }

    public static String difficultyAdmin() {
        return LmStaffHelp.build(
                "Adaptive difficulty staff",
                "/lm admin difficulty",
                UltraStyle.GRAY + "Player menu: " + UltraStyle.WHITE + "/lm difficulty",
                new LmStaffHelp.Section("System switch",
                        LmStaffHelp.cmd("/lm admin difficulty status", "show master switch state"),
                        LmStaffHelp.cmd("/lm admin difficulty on", "enable adaptive difficulty"),
                        LmStaffHelp.cmd("/lm admin difficulty off", "disable adaptive difficulty"),
                        LmStaffHelp.cmd("/lm admin difficulty toggle", "flip master switch")),
                new LmStaffHelp.Section("Testing whitelist",
                        LmStaffHelp.cmd("/lm admin difficulty whitelist list", "who can see scaled mobs while testing"),
                        LmStaffHelp.cmd("/lm admin difficulty whitelist add <player>", "add player to whitelist"),
                        LmStaffHelp.cmd("/lm admin difficulty whitelist remove <player>", "remove from whitelist"),
                        LmStaffHelp.cmd("/lm admin difficulty whitelist on", "restrict scaling to whitelist"),
                        LmStaffHelp.cmd("/lm admin difficulty whitelist off", "scaling for everyone again"),
                        LmStaffHelp.cmd("/lm admin difficulty whitelist clear", "empty whitelist")),
                new LmStaffHelp.Section("Staff perks and logs",
                        LmStaffHelp.cmd("/lm admin difficulty stafffree status|on|off", "staff skip Ancient Coin costs"),
                        LmStaffHelp.cmd("/lm admin difficulty telemetry status|on|off", "combat balance telemetry"),
                        LmStaffHelp.cmd("/lm admin difficulty syslog status|on|off", "unified system event log")),
                new LmStaffHelp.Section("Inspect and fix players",
                        LmStaffHelp.cmd("/lm admin difficulty inspect <player>", "open their difficulty menu"),
                        LmStaffHelp.cmd("/lm admin difficulty inspect clear", "stop inspect mode"),
                        LmStaffHelp.cmd("/lm admin difficulty resynclevel <player>", "fix stuck level sample on GUI"),
                        LmStaffHelp.cmd("/lm admin difficulty resetpurchased", "reset purchased tiers (staff)"),
                        LmStaffHelp.cmd("/lm admin difficulty characterreset", "character wipe hook")),
                new LmStaffHelp.Section("Config",
                        LmStaffHelp.cmd("/lm admin difficulty reload", "reload adaptivedifficulty.json"),
                        LmStaffHelp.cmd("/lm admin difficulty settings", "open admin settings menu"),
                        LmStaffHelp.cmd("/lm admin difficulty area", "show area difficulty at you"),
                        LmStaffHelp.cmd("/lm admin difficulty set <key> <value>", "change a live config key"),
                        LmStaffHelp.note("World difficulty: vanilla /difficulty <level> (ops)")));
    }

    // ------------------------------------------------------------------ rival

    public static String rivalPlayer() {
        return guide(
                "Rivals",
                "Pick a rival, beat them in fights and climb the season. Most of it is in the rival menu.",
                new Topic("Open",
                        say("/lm rival", "the rival menu"),
                        say("/lm rival season", "your season progress"),
                        say("/lm rival quests", "rival quests"),
                        say("/lm rival achievements", "your achievements"),
                        say("/lm rival hof", "the hall of fame"),
                        say("/lm rival journal", "your rival journal"),
                        say("/lm rival title", "pick a title to show")),
                new Topic("Look up",
                        say("/lm rival list", "your rivals"),
                        say("/lm rival stats", "your record"),
                        say("/lm rival stats <name>", "someone else's record"),
                        say("/lm rival top", "the leaderboard")),
                new Topic("Settings",
                        say("/lm rival tpmsg on", "tell me when I earn TP from a rival fight"),
                        say("/lm rival tpmsg off", "stop those messages")));
    }

    public static String rivalAdmin() {
        return LmStaffHelp.build(
                "Rival staff",
                "/lm admin rival",
                UltraStyle.GRAY + "Use " + UltraStyle.WHITE + "/lm admin rival help" + UltraStyle.GRAY + " anytime.",
                new LmStaffHelp.Section("Data",
                        LmStaffHelp.cmd("/lm admin rival save", "write rivalry data to disk"),
                        LmStaffHelp.cmd("/lm admin rival refresh", "reload stores from disk"),
                        LmStaffHelp.cmd("/lm admin rival status", "enabled flag and file paths")),
                new LmStaffHelp.Section("Rivalry actions (your account, staff only)",
                        LmStaffHelp.cmd("/lm rival declare <player>", "declare a rival"),
                        LmStaffHelp.cmd("/lm rival accept <player>", "accept a rival request"),
                        LmStaffHelp.cmd("/lm rival decline <player>", "decline a rival request"),
                        LmStaffHelp.cmd("/lm rival remove <player>", "remove a rival"),
                        LmStaffHelp.note("Players use the rival menu for these")),
                new LmStaffHelp.Section("Duel challenge (your account, staff only)",
                        LmStaffHelp.cmd("/lm rival challenge send <player> [minutes]", "send a duel request"),
                        LmStaffHelp.cmd("/lm rival challenge accept|decline|cancel", "answer or cancel a duel")),
                new LmStaffHelp.Section("Spectate (staff only)",
                        LmStaffHelp.cmd("/lm rival spectate [player]", "spectate a duel"),
                        LmStaffHelp.cmd("/lm rival spectate stop", "stop spectating")));
    }

    // ------------------------------------------------------------------ spar and dojo

    public static String sparPlayer() {
        return guide(
                "Sparring",
                "Spar with other players to earn TP. Mentors and the Spar Board are in the sparring menu.",
                new Topic("Open",
                        say("/lm spar", "the sparring menu"),
                        say("/lm dojo", "your dojo")),
                new Topic("While sparring",
                        say("/lm spar end", "stop your current spar"),
                        say("/lm spar stats", "your sparring record"),
                        say("/lm spar top", "the leaderboard")),
                new Topic("Mentors",
                        say("/lm spar mentor", "your mentor bond"),
                        say("/lm spar mentor <name>", "ask someone to be your mentor"),
                        say("/lm spar apprentice <name>", "ask someone to be your apprentice"),
                        say("/lm spar apprentice remove", "let your apprentice go")),
                new Topic("Training weights",
                        say("/lm spar weight quote <type> <value>", "check a price (1 ancient copper per value)")));
    }

    public static String dojoPlayer() {
        return guide(
                "Dojo",
                "A dojo is a team with its own hall. Found one, join one, train together and fight dojo wars.",
                new Topic("Open",
                        say("/lm dojo", "your dojo (if you are in two, pick which one first)"),
                        say("/lm open dojo", "the same menu")),
                new Topic("Look up",
                        say("/lm dojo summary", "your dojo's stats in chat"),
                        say("/lm dojo members", "who is in your dojo"),
                        say("/lm dojo previous-members", "who left this season"),
                        say("/lm dojo top", "the dojo leaderboard"),
                        say("/lm dojo hof", "the dojo hall of fame")),
                new Topic("Good to know",
                        tip("Only a dojo's master can build in its hall."),
                        tip("Ki attacks only hurt in the dojo world during a bout.")));
    }

    public static String sparAdmin() {
        return LmStaffHelp.build(
                "Sparring staff",
                "/lm admin spar",
                UltraStyle.GRAY + "Use " + UltraStyle.WHITE + "/lm admin spar help" + UltraStyle.GRAY + " anytime.",
                new LmStaffHelp.Section("Data",
                        LmStaffHelp.cmd("/lm admin spar save", "write spar data to disk"),
                        LmStaffHelp.cmd("/lm admin spar status", "enabled flag and file path")),
                new LmStaffHelp.Section("Mentor cooldown",
                        LmStaffHelp.cmd("/lm admin spar mentor resetcd", "clear your mentor invite cooldown"),
                        LmStaffHelp.cmd("/lm admin spar mentor resetcd <player>", "clear cooldown for a player")),
                new LmStaffHelp.Section("Mentor decisions (your account, staff only)",
                        LmStaffHelp.cmd("/lm spar mentor accept|decline|leave", "answer or leave a mentor bond"),
                        LmStaffHelp.note("Players use the sparring menu for these")),
                new LmStaffHelp.Section("Dojo (staff only)",
                        LmStaffHelp.cmd("/lm dojo accept|decline", "answer a dojo war request"),
                        LmStaffHelp.cmd("/lm dojo challenge <player>", "declare a menu war on that player's dojo"),
                        LmStaffHelp.cmd("/lm dojo season advance", "end the dojo season now (asks to confirm)")),
                new LmStaffHelp.Section("Weights shop (staff only)",
                        LmStaffHelp.cmd("/lm spar weight quote <type> <value>", "check a training weight price"),
                        LmStaffHelp.cmd("/lm spar weight buy <type> <value>", "buy training weights"),
                        LmStaffHelp.note("Types: turtle_shell, workout_weights, piccolo_cape")));
    }

    // ------------------------------------------------------------------ progression

    public static String progressionPlayer() {
        return guide(
                "Progression",
                "Spar to raise Potential Unlock. Prestige and the shop are in the main menu (/lm).",
                new Topic("Android",
                        say("/lm progression android remove", "remove your Android upgrade"),
                        tip("Type it again within 10 seconds to confirm. This cannot be undone.")));
    }

    public static String progressionStaff() {
        return LmStaffHelp.build(
                "Progression staff",
                "/lm admin progression",
                UltraStyle.GRAY + "Staff progression tools: boosts, flags and android conversion.",
                new LmStaffHelp.Section("Menus",
                        LmStaffHelp.cmd("/lm admin progression gui [page]", "open the progression staff menu"),
                        LmStaffHelp.cmd("/lm admin progression status", "flag and boost summary"),
                        LmStaffHelp.cmd("/lm admin progression flags", "open the flags page")),
                new LmStaffHelp.Section("TP boost",
                        LmStaffHelp.cmd("/lm admin progression boost status", "show the active TP boost"),
                        LmStaffHelp.cmd("/lm admin progression boost end", "end the active TP boost"),
                        LmStaffHelp.cmd("/lm admin progression boost start <encoded> [name]", "start from a store code"),
                        LmStaffHelp.cmd("/lm admin progression boost start <multiplier> <minutes> [name]",
                                "start a timed TP boost")),
                new LmStaffHelp.Section("Mastery boost",
                        LmStaffHelp.cmd("/lm admin progression masteryboost status", "show the active mastery boost"),
                        LmStaffHelp.cmd("/lm admin progression masteryboost end", "end the active mastery boost"),
                        LmStaffHelp.cmd("/lm admin progression masteryboost start <code> [name]",
                                "start from a store code (same format as the TP boost)"),
                        LmStaffHelp.cmd("/lm admin progression masteryboost start <multiplier> <minutes> [name]",
                                "start a timed mastery boost")),
                new LmStaffHelp.Section("Android",
                        LmStaffHelp.cmd("/lm admin progression android <player>", "convert a player to android"),
                        LmStaffHelp.cmd("/lm admin progression android remove <player>", "remove android from a player"),
                        LmStaffHelp.note("Players remove their own with /lm progression android remove")),
                new LmStaffHelp.Section("Flags",
                        LmStaffHelp.cmd("/lm admin progression flags <flag> on|off", "toggle a progression flag")));
    }

    public static String endDragonStaff() {
        return LmStaffHelp.build(
                "End dragon staff",
                "/lm admin enddragon",
                UltraStyle.GRAY + "Players summon dragons from the Difficulty menu only.",
                new LmStaffHelp.Section("Maintenance",
                        LmStaffHelp.cmd("/lm admin enddragon clear", "remove stray end dragons"),
                        LmStaffHelp.cmd("/lm admin enddragon repair", "repair End podium")));
    }

    // ------------------------------------------------------------------ skill check

    public static String skillCheck() {
        return guide(
                "Skill Check",
                "See every skill you have and how far each one has gone. Donor ranks only.",
                new Topic("Open",
                        say("/lm skillcheck", "the Skill Check menu"),
                        say("/lm skillcheck <page>", "jump to one page")));
    }

    // ------------------------------------------------------------------ character

    public static String characterPlayer() {
        return guide(
                "Character",
                "Change your race, class or looks. The menu shows every price and wait time before you buy.",
                new Topic("Open",
                        say("/lm character", "the Character menu"),
                        say("/lm saga", "saga resets")));
    }

    public static String characterStaff() {
        return LmStaffHelp.build(
                "Character services staff",
                "/lm admin character",
                UltraStyle.GRAY + "Fix another player's race, class, or appearance.",
                new LmStaffHelp.Section("Fixes",
                        LmStaffHelp.cmd("/lm admin character set-race <player> <race> [keep%]",
                                "change their race, keeping all (or a % of) stats"),
                        LmStaffHelp.cmd("/lm admin character set-class <player> <class>",
                                "change their fighting class"),
                        LmStaffHelp.cmd("/lm admin character reskin <player>",
                                "open their appearance editor"),
                        LmStaffHelp.note("Free fixes — no coin charge, permission, lock, or cooldown")),
                new LmStaffHelp.Section("Cooldowns",
                        LmStaffHelp.cmd("/lm admin character cooldown clear <player> [race|class|reskin]",
                                "clear their service cooldowns"),
                        LmStaffHelp.note("Players use the Character menu for their own changes")));
    }

    public static String androidifyStaff() {
        return LmStaffHelp.build(
                "Androidify",
                "/lm admin androidify",
                UltraStyle.GRAY + "Staff-only race conversion tool.",
                new LmStaffHelp.Section("Usage",
                        LmStaffHelp.cmd("/lm admin androidify", "convert yourself (in-game)"),
                        LmStaffHelp.cmd("/lm admin androidify <player>", "convert another online player")));
    }

    public static String skillsStaff() {
        return LmStaffHelp.build(
                "Skills admin",
                "/lm admin skills",
                UltraStyle.GRAY + "Staff skill-unlock browser (not the donator Skill Check).",
                new LmStaffHelp.Section("Menus",
                        LmStaffHelp.cmd("/lm admin skills", "open skills admin menu"),
                        LmStaffHelp.cmd("/lm admin skills do page <page>", "open one page")));
    }

    // ------------------------------------------------------------------ prestige

    public static String prestigePlayer() {
        return guide(
                "Prestige shop",
                "Trade prestige runs for favor, then spend favor on skills, tiers and effects. "
                        + "The shop menu (/lm prestige) shows every price.",
                new Topic("Your favor",
                        say("/lm prestige", "open the prestige menu"),
                        say("/lm prestige balance", "your favor, held runs and finished runs"),
                        say("/lm prestige turnin <runs>", "trade held runs for favor (1, 2, 3, 6, 9, 12, 15 or 18)")),
                new Topic("Buy",
                        say("/lm prestige buy skill <skill>", "one more level of a passive skill"),
                        say("/lm prestige buy tier <1-7>", "unlock a difficulty tier for good"),
                        say("/lm prestige buy majin|mutant|godki", "a shop effect")),
                new Topic("Effects",
                        say("/lm prestige effects", "the effects you have now"),
                        say("/lm prestige remove majin|mutant", "remove an effect (no refund)")));
    }

    public static String prestigeAdmin() {
        return LmStaffHelp.build(
                "Prestige admin",
                "/lm admin prestige",
                UltraStyle.GRAY + "Staff-only prestige shop adjustments. Use " + UltraStyle.WHITE
                        + "/lm admin prestige help" + UltraStyle.GRAY + " anytime.",
                new LmStaffHelp.Section("Staff menu",
                        LmStaffHelp.cmd("/lm admin prestige menu", "open the prestige staff menu"),
                        LmStaffHelp.cmd("/lm admin prestige do page <page>", "open one page"),
                        LmStaffHelp.cmd("/lm admin prestige do confirm [page]", "confirm a staff menu prestige")),
                new LmStaffHelp.Section("Look up",
                        LmStaffHelp.cmd("/lm admin prestige info [player]", "prestige summary")),
                new LmStaffHelp.Section("Wallet counts",
                        LmStaffHelp.cmd("/lm admin prestige points <player> set|add|remove <amount>", "wallet points"),
                        LmStaffHelp.cmd("/lm admin prestige held <player> set|add|remove <amount>", "held prestige count"),
                        LmStaffHelp.cmd("/lm admin prestige completed <player> set|add|remove <amount>", "completed count"),
                        LmStaffHelp.cmd("/lm admin prestige godki <player> set <0-10>", "set God Ki investments"),
                        LmStaffHelp.cmd("/lm admin prestige godki <player> add|remove <n>", "change God Ki investments")),
                new LmStaffHelp.Section("Shop unlocks",
                        LmStaffHelp.cmd("/lm admin prestige tier <player> set <0-7>", "set highest purchased tier"),
                        LmStaffHelp.cmd("/lm admin prestige tier <player> give <1-7>", "grant a tier unlock"),
                        LmStaffHelp.cmd("/lm admin prestige tier <player> clear <1-7|all>", "clear tier unlocks"),
                        LmStaffHelp.cmd("/lm admin prestige skills <player>", "list invested skill floors"),
                        LmStaffHelp.cmd("/lm admin prestige skill <player> <skillId> set|add|remove <levels>",
                                "adjust a skill floor"),
                        LmStaffHelp.note("Skill IDs match the prestige shop (e.g. potentialunlock)")),
                new LmStaffHelp.Section("Custom shop",
                        LmStaffHelp.cmd("/lm admin prestige shop list|check|reload", "custom shop entries"),
                        LmStaffHelp.cmd("/lm admin prestige custom <player> [entry give|take|set n]",
                                "custom perks a player owns")));
    }
}
