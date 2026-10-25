package com.dbzlegacy.adaptivedifficulty.command;

/** Central help text for all Legacy Mechanics command trees (same layout as {@code /lm admin help}). */
public final class LmCommandHelp {
    private LmCommandHelp() {}

    public static String lmPlayer() {
        return LmStaffHelp.build(
                "Legacy Mechanics",
                "/lm",
                "§7Your hub for every Legacy Mechanics menu. Type §f/lm help §7anytime.",
                new LmStaffHelp.Section("Menus",
                        LmStaffHelp.cmd("/lm", "open the main hub"),
                        LmStaffHelp.cmd("/lm open difficulty", "adaptive difficulty"),
                        LmStaffHelp.cmd("/lm open rival", "rival system"),
                        LmStaffHelp.cmd("/lm open spar", "sparring and dojo"),
                        LmStaffHelp.cmd("/lm open prestige", "prestige shop"),
                        LmStaffHelp.cmd("/lm open character", "race, class, reskin, and saga reset"),
                        LmStaffHelp.cmd("/lm saga", "open saga resets"),
                        LmStaffHelp.cmd("/lm open skillcheck", "donator skill check (if permitted)")),
                new LmStaffHelp.Section("Donator",
                        LmStaffHelp.cmd("/lm revive", "revive yourself (donators)")),
                new LmStaffHelp.Section("Quick actions",
                        LmStaffHelp.cmd("/prestige balance", "favor, held shards, completed runs"),
                        LmStaffHelp.cmd("/prestige buy majin|mutant|godki", "buy a shop effect")),
                new LmStaffHelp.Section("Staff",
                        LmStaffHelp.cmd("/lm admin help", "staff tools and player resets"),
                        LmStaffHelp.note("Shortcut: none — use /lm")));
    }

    public static String lmAdmin() {
        return LmStaffHelp.build(
                "Legacy Mechanics staff",
                "/lm admin",
                "§7Staff tools and player fixes. Type §f/lm admin help §7anytime.",
                new LmStaffHelp.Section("Config and data",
                        LmStaffHelp.cmd("/lm admin reload", "reload Legacy Mechanics config"),
                        LmStaffHelp.cmd("/lm admin config", "edit config files in a menu"),
                        LmStaffHelp.cmd("/lm admin migrate-cnpc", "import CNPC rival and spar data"),
                        LmStaffHelp.cmd("/lm admin migrate-cnpc force", "wipe LM stores, then re-import"),
                        LmStaffHelp.note("Backup: config/legacymechanics/cnpc-import-backup/")),
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
                                "same legendary grant, name then amount")),
                new LmStaffHelp.Section("Player resets",
                        LmStaffHelp.cmd("/lm admin clear <player>", "clear all LM data, online or offline"),
                        LmStaffHelp.cmd("/lm admin clear <player> rival", "clear rival data only"),
                        LmStaffHelp.cmd("/lm admin clear <player> spar", "clear spar data only"),
                        LmStaffHelp.cmd("/lm admin clear <player> difficulty", "clear difficulty data only"),
                        LmStaffHelp.cmd("/lm admin clear <player> progression", "clear progression data only"),
                        LmStaffHelp.cmd("/lm admin character cooldown clear <player>", "clear all character service cooldowns"),
                        LmStaffHelp.cmd("/lm admin character cooldown clear <player> race", "clear race change cooldown"),
                        LmStaffHelp.cmd("/lm admin character cooldown clear <player> class", "clear class change cooldown"),
                        LmStaffHelp.cmd("/lm admin character cooldown clear <player> reskin", "clear reskin cooldown"),
                        LmStaffHelp.cmd("/lm fusionreset <player>", "clear a stuck fusion cooldown"),
                        LmStaffHelp.cmd("/lm revive <player>", "revive an online player")),
                new LmStaffHelp.Section("Character fixes",
                        LmStaffHelp.cmd("/character admin set-race <player> <race>",
                                "change their race, keeping all stats"),
                        LmStaffHelp.cmd("/character admin set-race <player> <race> <keep%>",
                                "change their race, keeping a % of stats"),
                        LmStaffHelp.cmd("/character admin set-class <player> <class>",
                                "change their fighting class"),
                        LmStaffHelp.cmd("/character admin reskin <player>",
                                "open their appearance editor"),
                        LmStaffHelp.note("Free fixes — no coin charge, permission, lock, or cooldown")),
                new LmStaffHelp.Section("Staff tools",
                        LmStaffHelp.cmd("/lm admin syslog status", "event log on or off"),
                        LmStaffHelp.cmd("/lm admin syslog on", "turn unified event log on"),
                        LmStaffHelp.cmd("/lm admin syslog off", "turn unified event log off"),
                        LmStaffHelp.cmd("/lm admin syslog flush", "flush logs to disk"),
                        LmStaffHelp.cmd("/lm admin inspect <player>", "view menus as that player"),
                        LmStaffHelp.cmd("/lm admin inspect clear", "stop inspect mode")),
                new LmStaffHelp.Section("Related staff trees",
                        LmStaffHelp.cmd("/difficulty admin help", "adaptive difficulty staff commands"),
                        LmStaffHelp.cmd("/padmin help", "prestige shop staff commands"),
                        LmStaffHelp.note("Difficulty shortcut: /diff")));
    }

    public static String difficultyPlayer() {
        return LmStaffHelp.build(
                "Adaptive difficulty",
                "/difficulty",
                "§7Open your difficulty menu here or from §f/lm§7. Staff: §f/difficulty admin help§7 · shortcut §f/diff",
                new LmStaffHelp.Section("Menus",
                        LmStaffHelp.cmd("/difficulty", "open your difficulty menu"),
                        LmStaffHelp.cmd("/difficulty reset", "clear active tier (free)")),
                new LmStaffHelp.Section("World difficulty (ops)",
                        LmStaffHelp.cmd("/difficulty peaceful", "set vanilla world difficulty"),
                        LmStaffHelp.cmd("/difficulty easy", "set vanilla world difficulty"),
                        LmStaffHelp.cmd("/difficulty normal", "set vanilla world difficulty"),
                        LmStaffHelp.cmd("/difficulty hard", "set vanilla world difficulty")));
    }

    public static String difficultyAdmin() {
        return LmStaffHelp.build(
                "Adaptive difficulty staff",
                "/difficulty admin",
                "§7Player hub: §f/lm §7· Shortcut: §f/diff",
                new LmStaffHelp.Section("Players",
                        LmStaffHelp.cmd("/difficulty", "open your difficulty menu"),
                        LmStaffHelp.cmd("/difficulty reset", "clear active tier (free)"),
                        LmStaffHelp.cmd("/difficulty peaceful", "set vanilla world difficulty (ops)"),
                        LmStaffHelp.cmd("/difficulty easy", "set vanilla world difficulty (ops)"),
                        LmStaffHelp.cmd("/difficulty normal", "set vanilla world difficulty (ops)"),
                        LmStaffHelp.cmd("/difficulty hard", "set vanilla world difficulty (ops)")),
                new LmStaffHelp.Section("System switch",
                        LmStaffHelp.cmd("/difficulty admin status", "show master switch state"),
                        LmStaffHelp.cmd("/difficulty admin on", "enable adaptive difficulty"),
                        LmStaffHelp.cmd("/difficulty admin off", "disable adaptive difficulty"),
                        LmStaffHelp.cmd("/difficulty admin toggle", "flip master switch")),
                new LmStaffHelp.Section("Testing whitelist",
                        LmStaffHelp.cmd("/difficulty admin whitelist list", "who can see scaled mobs while testing"),
                        LmStaffHelp.cmd("/difficulty admin whitelist add <player>", "add player to whitelist"),
                        LmStaffHelp.cmd("/difficulty admin whitelist remove <player>", "remove from whitelist"),
                        LmStaffHelp.cmd("/difficulty admin whitelist on", "restrict scaling to whitelist"),
                        LmStaffHelp.cmd("/difficulty admin whitelist off", "scaling for everyone again"),
                        LmStaffHelp.cmd("/difficulty admin whitelist clear", "empty whitelist")),
                new LmStaffHelp.Section("Staff perks and logs",
                        LmStaffHelp.cmd("/difficulty admin stafffree status", "staff skip Ancient Coin costs"),
                        LmStaffHelp.cmd("/difficulty admin stafffree on", "enable staff free coins"),
                        LmStaffHelp.cmd("/difficulty admin stafffree off", "disable staff free coins"),
                        LmStaffHelp.cmd("/difficulty admin telemetry status", "combat balance telemetry"),
                        LmStaffHelp.cmd("/difficulty admin telemetry on", "enable combat telemetry"),
                        LmStaffHelp.cmd("/difficulty admin telemetry off", "disable combat telemetry"),
                        LmStaffHelp.cmd("/difficulty admin syslog status", "unified system event log"),
                        LmStaffHelp.cmd("/difficulty admin syslog on", "enable system event log"),
                        LmStaffHelp.cmd("/difficulty admin syslog off", "disable system event log")),
                new LmStaffHelp.Section("Inspect and fix players",
                        LmStaffHelp.cmd("/difficulty admin inspect <player>", "open their difficulty menu"),
                        LmStaffHelp.cmd("/difficulty admin inspect clear", "stop inspect mode"),
                        LmStaffHelp.cmd("/difficulty admin resynclevel <player>", "fix stuck level sample on GUI"),
                        LmStaffHelp.cmd("/difficulty admin resetpurchased", "reset purchased tiers (staff)"),
                        LmStaffHelp.cmd("/difficulty admin characterreset", "character wipe hook")),
                new LmStaffHelp.Section("Config",
                        LmStaffHelp.cmd("/difficulty admin reload", "reload adaptivedifficulty.json"),
                        LmStaffHelp.cmd("/difficulty admin settings", "open admin settings menu"),
                        LmStaffHelp.cmd("/difficulty admin area", "show area difficulty at you"),
                        LmStaffHelp.cmd("/difficulty admin gamedifficulty <level>", "set vanilla difficulty"),
                        LmStaffHelp.cmd("/difficulty admin set <key> <value>", "change a live config key"),
                        LmStaffHelp.note("Tier keys live in config/adaptivedifficulty.json")));
    }

    public static String rivalPlayer() {
        return LmStaffHelp.build(
                "Rival system",
                "/rival",
                "§7Most rival actions live in the rival menu. Type §f/rival help §7when you need the full list.",
                new LmStaffHelp.Section("Menus",
                        LmStaffHelp.cmd("/rival", "open rival menu"),
                        LmStaffHelp.cmd("/rival season", "season progress menu"),
                        LmStaffHelp.cmd("/rival quests", "quests menu"),
                        LmStaffHelp.cmd("/rival achievements", "achievements menu"),
                        LmStaffHelp.cmd("/rival hof", "hall of fame"),
                        LmStaffHelp.cmd("/rival journal", "rival journal"),
                        LmStaffHelp.cmd("/rival title", "title picker")),
                new LmStaffHelp.Section("Info",
                        LmStaffHelp.cmd("/rival list", "list your rivals"),
                        LmStaffHelp.cmd("/rival stats", "your rival stats"),
                        LmStaffHelp.cmd("/rival stats <player>", "another player's stats"),
                        LmStaffHelp.cmd("/rival top", "leaderboard (RP)"),
                        LmStaffHelp.cmd("/rival top <category>", "leaderboard by category")),
                new LmStaffHelp.Section("Teleport messages",
                        LmStaffHelp.cmd("/rival tpmsg", "show TP message toggle state"),
                        LmStaffHelp.cmd("/rival tpmsg on", "enable rival TP messages"),
                        LmStaffHelp.cmd("/rival tpmsg off", "disable rival TP messages")),
                new LmStaffHelp.Section("Staff",
                        LmStaffHelp.cmd("/rival admin help", "save, refresh, status")));
    }

    public static String rivalAdmin() {
        return LmStaffHelp.build(
                "Rival staff",
                "/rival admin",
                "§7Use §f/rival admin help §7anytime.",
                new LmStaffHelp.Section("Data",
                        LmStaffHelp.cmd("/rival admin save", "write rivalry data to disk"),
                        LmStaffHelp.cmd("/rival admin refresh", "reload stores from disk"),
                        LmStaffHelp.cmd("/rival admin status", "enabled flag and file paths")),
                new LmStaffHelp.Section("Rivalry actions (your account)",
                        LmStaffHelp.cmd("/rival declare <player>", "declare a rival"),
                        LmStaffHelp.cmd("/rival accept <player>", "accept a rival request"),
                        LmStaffHelp.cmd("/rival decline <player>", "decline a rival request"),
                        LmStaffHelp.cmd("/rival remove <player>", "remove a rival"),
                        LmStaffHelp.note("Players use the rival menu for these")),
                new LmStaffHelp.Section("Duel challenge (your account)",
                        LmStaffHelp.cmd("/rival challenge send <player>", "send a duel request"),
                        LmStaffHelp.cmd("/rival challenge send <player> <minutes>", "send with custom window"),
                        LmStaffHelp.cmd("/rival challenge accept", "accept pending duel"),
                        LmStaffHelp.cmd("/rival challenge decline", "decline pending duel"),
                        LmStaffHelp.cmd("/rival challenge cancel", "cancel your outgoing duel")),
                new LmStaffHelp.Section("Spectate (your account)",
                        LmStaffHelp.cmd("/rival spectate", "spectate an active duel"),
                        LmStaffHelp.cmd("/rival spectate <player>", "spectate a player's duel"),
                        LmStaffHelp.cmd("/rival spectate stop", "stop spectating")));
    }

    public static String sparPlayer() {
        return LmStaffHelp.build(
                "Sparring",
                "/spar",
                "§7Training, mentors, and dojo wars — most live in the sparring menu. Type §f/spar help §7for commands.",
                new LmStaffHelp.Section("Menus",
                        LmStaffHelp.cmd("/spar", "open sparring menu")),
                new LmStaffHelp.Section("Session",
                        LmStaffHelp.cmd("/spar stats", "your spar stats"),
                        LmStaffHelp.cmd("/spar stats <player>", "another player's stats"),
                        LmStaffHelp.cmd("/spar end", "end your active spar session"),
                        LmStaffHelp.cmd("/spar top", "leaderboard (training points)"),
                        LmStaffHelp.cmd("/spar top <category>", "leaderboard by category")),
                new LmStaffHelp.Section("Mentor",
                        LmStaffHelp.cmd("/spar mentor", "mentor bond status"),
                        LmStaffHelp.cmd("/spar mentor <player>", "invite a mentor")),
                new LmStaffHelp.Section("Apprentice",
                        LmStaffHelp.cmd("/spar apprentice", "apprentice status"),
                        LmStaffHelp.cmd("/spar apprentice <player>", "invite an apprentice"),
                        LmStaffHelp.cmd("/spar apprentice remove", "release your apprentice"),
                        LmStaffHelp.cmd("/spar apprentice remove <player>", "release one apprentice")),
                new LmStaffHelp.Section("Dojo",
                        LmStaffHelp.cmd("/spar dojo", "your dojo summary"),
                        LmStaffHelp.cmd("/spar dojo members", "list dojo members"),
                        LmStaffHelp.cmd("/spar dojo hof", "dojo hall of fame"),
                        LmStaffHelp.cmd("/spar dojo top", "dojo leaderboard")),
                new LmStaffHelp.Section("Staff",
                        LmStaffHelp.cmd("/spar admin help", "save, status, mentor cooldown reset")));
    }

    public static String sparAdmin() {
        return LmStaffHelp.build(
                "Sparring staff",
                "/spar admin",
                "§7Use §f/spar admin help §7anytime.",
                new LmStaffHelp.Section("Data",
                        LmStaffHelp.cmd("/spar admin save", "write spar data to disk"),
                        LmStaffHelp.cmd("/spar admin status", "enabled flag and file path")),
                new LmStaffHelp.Section("Mentor cooldown",
                        LmStaffHelp.cmd("/spar admin mentor resetcd", "clear your mentor invite cooldown"),
                        LmStaffHelp.cmd("/spar admin mentor resetcd <player>", "clear cooldown for a player")),
                new LmStaffHelp.Section("Mentor decisions (your account)",
                        LmStaffHelp.cmd("/spar mentor accept", "accept mentor invite"),
                        LmStaffHelp.cmd("/spar mentor decline", "decline mentor invite"),
                        LmStaffHelp.cmd("/spar mentor leave", "leave mentor bond"),
                        LmStaffHelp.note("Players use the sparring menu for these")),
                new LmStaffHelp.Section("Dojo wars (your account)",
                        LmStaffHelp.cmd("/spar dojo accept", "accept dojo war invite"),
                        LmStaffHelp.cmd("/spar dojo decline", "decline dojo war invite"),
                        LmStaffHelp.cmd("/spar dojo challenge <player>", "challenge another dojo")),
                new LmStaffHelp.Section("Weights shop",
                        LmStaffHelp.cmd("/spar weight quote <type> <value>", "check a training weight price"),
                        LmStaffHelp.cmd("/spar weight buy <type> <value>", "buy training weights"),
                        LmStaffHelp.note("Types: turtle_shell, workout_weights, piccolo_cape")));
    }

    public static String progressionPlayer() {
        return LmStaffHelp.build(
                "Progression",
                "/lm",
                "§7Spar to raise Potential Unlock. Prestige and the shop live under §f/lm§7.",
                new LmStaffHelp.Section("Android",
                        LmStaffHelp.cmd("/progression android remove", "remove your Android upgrade"),
                        LmStaffHelp.note("Run it again within 10 seconds to confirm")),
                new LmStaffHelp.Section("Hub",
                        LmStaffHelp.cmd("/lm", "open the hub"),
                        LmStaffHelp.note("Staff tools: /progression help")));
    }

    public static String progressionStaff() {
        return LmStaffHelp.build(
                "Progression staff",
                "/progression",
                "§7Staff progression tools—boosts, flags, and android conversion.",
                new LmStaffHelp.Section("Menus",
                        LmStaffHelp.cmd("/progression", "open progression staff menu")),
                new LmStaffHelp.Section("Natural boost",
                        LmStaffHelp.cmd("/progression boost status", "show active boost"),
                        LmStaffHelp.cmd("/progression boost end", "end active boost"),
                        LmStaffHelp.cmd("/progression boost start <encoded>", "start boost from encoded id"),
                        LmStaffHelp.cmd("/progression boost start <multiplier> <minutes>", "start timed boost")),
                new LmStaffHelp.Section("Android (staff)",
                        LmStaffHelp.cmd("/progression android <player>", "convert player to android"),
                        LmStaffHelp.cmd("/progression android remove <player>", "remove android from player")),
                new LmStaffHelp.Section("Flags",
                        LmStaffHelp.cmd("/progression admin <flag> <on>", "toggle progression flag"),
                        LmStaffHelp.cmd("/progression admin <flag> <off>", "toggle progression flag off")));
    }

    public static String endDragonStaff() {
        return LmStaffHelp.build(
                "End dragon staff",
                "/enddragon",
                "§7Players summon dragons from the Difficulty menu only.",
                new LmStaffHelp.Section("Maintenance",
                        LmStaffHelp.cmd("/enddragon clear", "remove stray end dragons"),
                        LmStaffHelp.cmd("/enddragon repair", "repair End podium"),
                        LmStaffHelp.note("Players summon from the Difficulty menu")));
    }

    public static String skillCheck() {
        return LmStaffHelp.build(
                "Skill check",
                "/skillcheck",
                "§7Donator or staff permission required.",
                new LmStaffHelp.Section("Menus",
                        LmStaffHelp.cmd("/skillcheck", "open skill check menu"),
                        LmStaffHelp.cmd("/skillcheck <page>", "open a specific page")),
                new LmStaffHelp.Section("Hub",
                        LmStaffHelp.cmd("/lm open skillcheck", "open from Legacy Mechanics hub")));
    }

    public static String characterPlayer() {
        return LmStaffHelp.build(
                "Character services",
                "/character",
                "§7Race, class, and reskin services — same purchases as the character menu. Use §f/character help §7anytime.",
                new LmStaffHelp.Section("Menus",
                        LmStaffHelp.cmd("/character", "open character services menu"),
                        LmStaffHelp.cmd("/lm open character", "same menu from the hub"),
                        LmStaffHelp.note("Browse races and classes in the menu — costs and cooldowns shown there")));
    }

    public static String characterStaff() {
        return LmStaffHelp.build(
                "Character services staff",
                "/character admin",
                "§7Fix another player's race, class, or appearance. Type §f/character admin help §7anytime.",
                new LmStaffHelp.Section("Fixes",
                        LmStaffHelp.cmd("/character admin set-race <player> <race>",
                                "change their race, keeping all stats"),
                        LmStaffHelp.cmd("/character admin set-race <player> <race> <keep%>",
                                "change their race, keeping a % of stats"),
                        LmStaffHelp.cmd("/character admin set-class <player> <class>",
                                "change their fighting class"),
                        LmStaffHelp.cmd("/character admin reskin <player>",
                                "open their appearance editor"),
                        LmStaffHelp.note("Free fixes — no coin charge, permission, lock, or cooldown")),
                new LmStaffHelp.Section("Related",
                        LmStaffHelp.cmd("/lm admin character cooldown clear <player>",
                                "clear their service cooldowns"),
                        LmStaffHelp.note("Players use the Character Services menu for their own changes")));
    }

    public static String androidifyStaff() {
        return LmStaffHelp.build(
                "Androidify",
                "/androidify",
                "§7Staff-only race conversion tool.",
                new LmStaffHelp.Section("Usage",
                        LmStaffHelp.cmd("/androidify", "convert yourself (in-game)"),
                        LmStaffHelp.cmd("/androidify <player>", "convert another online player")));
    }

    public static String skillsStaff() {
        return LmStaffHelp.build(
                "Skills admin",
                "/skills",
                "§7Staff skill-unlock browser (not the donator skill check).",
                new LmStaffHelp.Section("Menus",
                        LmStaffHelp.cmd("/skills", "open skills admin menu"),
                        LmStaffHelp.cmd("/skills do page <page>", "scripted page open")));
    }

    public static String prestigePlayer() {
        return LmStaffHelp.build(
                "Prestige shop",
                "/prestige",
                "§7Everything the Prestige → Shop screens sell — exchange, skills, tiers, and effects. Use §f/prestige help §7anytime.",
                new LmStaffHelp.Section("Exchange",
                        LmStaffHelp.cmd("/prestige balance", "favor, held shards, completed runs"),
                        LmStaffHelp.cmd("/prestige turnin <1|2|3|6|9>", "bank a prestige run")),
                new LmStaffHelp.Section("Skills and tiers",
                        LmStaffHelp.cmd("/prestige buy skill <skill>", "buy a passive skill level"),
                        LmStaffHelp.cmd("/prestige buy tier <1-7>", "buy a difficulty tier unlock")),
                new LmStaffHelp.Section("Effects",
                        LmStaffHelp.cmd("/prestige effects", "view your current shop effects"),
                        LmStaffHelp.cmd("/prestige buy majin", "buy Majin absorption"),
                        LmStaffHelp.cmd("/prestige buy mutant", "buy mutant upgrade"),
                        LmStaffHelp.cmd("/prestige buy godki", "buy God Ki enhancement"),
                        LmStaffHelp.cmd("/prestige remove majin", "remove Majin absorption"),
                        LmStaffHelp.cmd("/prestige remove mutant", "remove mutant upgrade")),
                new LmStaffHelp.Section("Staff",
                        LmStaffHelp.note("Staff menu: /prestige menu · wallet edits: /padmin help")));
    }

    public static String prestigeAdmin() {
        return LmStaffHelp.build(
                "Prestige admin",
                "/padmin",
                "§7Staff-only prestige shop adjustments. Use §f/padmin help §7anytime.",
                new LmStaffHelp.Section("Look up",
                        LmStaffHelp.cmd("/padmin info", "your prestige summary"),
                        LmStaffHelp.cmd("/padmin info <player>", "another player's summary")),
                new LmStaffHelp.Section("Wallet counts",
                        LmStaffHelp.cmd("/padmin points <player> set <amount>", "set wallet points"),
                        LmStaffHelp.cmd("/padmin points <player> add <amount>", "add wallet points"),
                        LmStaffHelp.cmd("/padmin points <player> remove <amount>", "remove wallet points"),
                        LmStaffHelp.cmd("/padmin held <player> set <amount>", "set held prestige count"),
                        LmStaffHelp.cmd("/padmin held <player> add <amount>", "add held prestige count"),
                        LmStaffHelp.cmd("/padmin held <player> remove <amount>", "remove held prestige count"),
                        LmStaffHelp.cmd("/padmin completed <player> set <amount>", "set completed count"),
                        LmStaffHelp.cmd("/padmin completed <player> add <amount>", "add completed count"),
                        LmStaffHelp.cmd("/padmin completed <player> remove <amount>", "remove completed count"),
                        LmStaffHelp.cmd("/padmin breakthroughs <player> set <0-5>", "set breakthroughs"),
                        LmStaffHelp.cmd("/padmin breakthroughs <player> add <n>", "add breakthroughs"),
                        LmStaffHelp.cmd("/padmin breakthroughs <player> remove <n>", "remove breakthroughs"),
                        LmStaffHelp.cmd("/padmin godki <player> set <0-5>", "set God Ki investments"),
                        LmStaffHelp.cmd("/padmin godki <player> add <n>", "add God Ki investments"),
                        LmStaffHelp.cmd("/padmin godki <player> remove <n>", "remove God Ki investments")),
                new LmStaffHelp.Section("Shop unlocks",
                        LmStaffHelp.cmd("/padmin tier <player> set <0-7>", "set highest purchased tier"),
                        LmStaffHelp.cmd("/padmin tier <player> give <1-7>", "grant a tier unlock"),
                        LmStaffHelp.cmd("/padmin tier <player> clear <1-7>", "clear one tier unlock"),
                        LmStaffHelp.cmd("/padmin tier <player> clear all", "clear all tier unlocks"),
                        LmStaffHelp.cmd("/padmin skills <player>", "list invested skill floors"),
                        LmStaffHelp.cmd("/padmin skill <player> <skillId> set <levels>", "set skill floor level"),
                        LmStaffHelp.cmd("/padmin skill <player> <skillId> add <levels>", "add skill floor levels"),
                        LmStaffHelp.cmd("/padmin skill <player> <skillId> remove <levels>", "remove skill floor levels"),
                        LmStaffHelp.note("Skill IDs match the prestige shop (e.g. potentialunlock)")));
    }
}
