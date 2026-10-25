package com.dbzlegacy.adaptivedifficulty.bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit-side entrypoint so {@code /difficulty}, {@code /rival}, and {@code /spar}
 * work for all players on Mohist.
 * Prefers CMILib (CMI) inventory GUIs, then a plain chest GUI.
 * <p>
 * On Mohist, this plugin owns {@code /difficulty}, {@code /rival}, and {@code /spar} —
 * admin switches (on/off/whitelist) must be handled here and forwarded into the Forge mod config.
 */
public final class AdaptiveDifficultyGuiPlugin extends JavaPlugin {
    private DifficultyChestGui chestGui;
    private RivalChestGui rivalChestGui;
    private SparChestGui sparChestGui;
    private HubChestGui hubChestGui;
    private ProgressionChestGui progressionChestGui;
    private PrestigeChestGui prestigeChestGui;
    private CharacterServicesChestGui characterServicesChestGui;
    private SkillsChestGui skillsChestGui;
    private ProgressionCommandTree progressionCommandTree;

    @Override
    public void onEnable() {
        chestGui = new DifficultyChestGui(this);
        rivalChestGui = new RivalChestGui(this);
        sparChestGui = new SparChestGui(this);
        hubChestGui = new HubChestGui(this);
        progressionChestGui = new ProgressionChestGui(this);
        prestigeChestGui = new PrestigeChestGui(this);
        characterServicesChestGui = new CharacterServicesChestGui(this);
        skillsChestGui = new SkillsChestGui(this);
        progressionCommandTree = new ProgressionCommandTree(this);
        getServer().getPluginManager().registerEvents(chestGui, this);
        getServer().getPluginManager().registerEvents(rivalChestGui, this);
        getServer().getPluginManager().registerEvents(sparChestGui, this);
        getServer().getPluginManager().registerEvents(hubChestGui, this);
        getServer().getPluginManager().registerEvents(progressionChestGui, this);
        getServer().getPluginManager().registerEvents(prestigeChestGui, this);
        getServer().getPluginManager().registerEvents(characterServicesChestGui, this);
        getServer().getPluginManager().registerEvents(skillsChestGui, this);
        getServer().getPluginManager().registerEvents(new DeathDropGuard(), this);
        getServer().getPluginManager().registerEvents(new ClaimOverlapGuard(this), this);
        GuiTooltips.init(this);

        var progCmd = getCommand("progression");
        if (progCmd != null) {
            progCmd.setTabCompleter(progressionCommandTree);
        }
        var lmCmd = getCommand("lm");
        if (lmCmd != null) {
            lmCmd.setTabCompleter(this);
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new DmzDiffExpansion(this).register();
            getLogger().info("Registered PlaceholderAPI expansion: dmzdiff");
        }

        boolean forge = ForgeBridge.forgeAvailable();
        getLogger().info("GUI backend: CMILib=" + CmiDifficultyGui.available()
                + " forgeMod=" + forge);
        if (!forge) {
            String err = ForgeBridge.lastError();
            getLogger().severe("LegacyMechanics Forge mod NOT reachable — GUI actions will fail."
                    + (err == null || err.isBlank() ? "" : " (" + err + ")"));
            getLogger().severe("Install mods/LegacyMechanics-*.jar and restart.");
        } else {
            String modVer = ForgeBridge.modVersion();
            String pluginVer = getDescription().getVersion();
            if (modVer == null || modVer.isBlank()) {
                getLogger().severe("VERSION HANDSHAKE FAILED: could not read AdaptiveDifficultyMod.VERSION"
                        + " — GUI may call missing Forge APIs.");
            } else if (pluginVer != null && !modVer.equals(pluginVer)) {
                getLogger().severe("VERSION SKEW: Forge mod=" + modVer + " GUI plugin=" + pluginVer
                        + " — install matching LegacyMechanics + LegacyMechanicsGUI jars (same version).");
            } else {
                getLogger().info("Version handshake OK: " + pluginVer);
            }
        }
        getLogger().info("Registered Bukkit /difficulty /rival /spar /lm /progression /androidify /prestige /padmin /skills /skillcheck /terminal.");
    }

    /**
     * Called by the Forge mod via reflection when {@code guiBackend} is cmi/chest/auto.
     * Always opens an inventory GUI (does not re-read chat preference — Forge already decided).
     */
    public void openMenu(Player player, String page) {
        if (player == null) {
            return;
        }
        AdminInspectSessions.clear(player.getUniqueId());
        ForgeBridge.prepareDifficultyGui(player);
        openInventory(player, page);
    }

    /**
     * Preferred Forge entry — UUID only, no Player crossing Mohist classloaders.
     * Always runs on the Bukkit primary thread (CMI requires it).
     */
    public void openMenuForUuid(UUID playerId, String page) {
        if (playerId == null) {
            return;
        }
        String target = page == null || page.isBlank() ? "main" : page;
        Runnable task = () -> {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null && p.isOnline()) {
                AdminInspectSessions.clear(p.getUniqueId());
                openInventory(p, target);
            } else {
                getLogger().warning("openMenuForUuid: player offline/unresolved " + playerId);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    /** Chest-only open for Forge {@code guiBackend=chest}. */
    public void openChestMenu(Player player, String page) {
        if (player == null) {
            return;
        }
        AdminInspectSessions.clear(player.getUniqueId());
        ForgeBridge.prepareDifficultyGui(player);
        chestGui.open(player, page);
    }

    /** UUID chest open — Mohist-safe Forge entry for {@code guiBackend=chest}. */
    public void openChestMenuForUuid(UUID playerId, String page) {
        if (playerId == null) {
            return;
        }
        String target = page == null || page.isBlank() ? "main" : page;
        Runnable task = () -> {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null && p.isOnline()) {
                AdminInspectSessions.clear(p.getUniqueId());
                chestGui.open(p, target);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    /** Clear staff inspect session (Mohist-safe UUID entry). */
    public void clearInspectForUuid(UUID adminId) {
        if (adminId == null) {
            return;
        }
        Runnable task = () -> {
            AdminInspectSessions.clear(adminId);
            Player admin = Bukkit.getPlayer(adminId);
            if (admin != null && admin.isOnline()) {
                openHubInventory(admin, "main");
                admin.sendMessage("§7Inspect closed — showing your own LM hub.");
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    /**
     * Staff inspect: open any LM system chest GUI as admin while painting/editing subject.
     * Default system is hub so staff can jump into Difficulty / Rival / Spar / etc. as that player.
     */
    public void openLmInspectForUuid(UUID adminId, UUID subjectId, String system, String page) {
        if (adminId == null || subjectId == null) {
            return;
        }
        String sys = system == null || system.isBlank() ? "hub" : system.toLowerCase(Locale.ROOT).trim();
        String target = page == null || page.isBlank() ? "main" : page;
        Runnable task = () -> {
            Player admin = Bukkit.getPlayer(adminId);
            Player subject = Bukkit.getPlayer(subjectId);
            if (admin == null || !admin.isOnline()) {
                getLogger().warning("openLmInspectForUuid: admin offline " + adminId);
                return;
            }
            if (subject == null || !subject.isOnline()) {
                admin.sendMessage("§cThat player is not online.");
                return;
            }
            if (!ForgeBridge.isStaff(admin)) {
                admin.sendMessage("§cStaff only.");
                return;
            }
            AdminInspectSessions.set(admin.getUniqueId(), subject.getUniqueId());
            admin.sendMessage("§eInspecting §f" + subject.getName()
                    + "§e's Legacy Mechanics (§8" + sys + "§e). Edits apply to them.");
            admin.sendMessage("§8Exit: §f/lm admin inspect clear §8or §f/difficulty admin gui clear");
            openInspectSystem(admin, subject, sys, target);
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    /** Open one system in inspect mode (chest-only; session already set). */
    private void openInspectSystem(Player admin, Player subject, String system, String page) {
        String s = system == null ? "hub" : system.toLowerCase(Locale.ROOT).trim();
        String p = page == null || page.isBlank() ? "main" : page;
        switch (s) {
            case "hub", "lm", "legacymechanics", "main" -> hubChestGui.open(admin, p);
            case "difficulty", "diff", "ad" -> chestGui.openAs(admin, subject, p);
            case "rival", "rivals", "rivalry" -> rivalChestGui.open(admin, p);
            case "spar", "sparring" -> sparChestGui.open(admin, p);
            case "skillcheck", "skill_check" -> {
                ForgeBridge.markSkillCheckSession(admin);
                skillsChestGui.open(admin, p.equals("main") ? "core" : p);
            }
            case "skills", "skill" -> skillsChestGui.open(admin, p.equals("main") ? "core" : p);
            case "prestige" -> prestigeChestGui.open(admin, p);
            case "character", "characterservices", "charservices", "char" ->
                    characterServicesChestGui.open(admin, p);
            case "progression", "prog" -> progressionChestGui.open(admin, p);
            case "android_remove", "androidremove", "remove_android", "deandroid" ->
                    progressionChestGui.open(admin, "android_remove");
            default -> {
                admin.sendMessage("§cUnknown system: §f" + s
                        + " §8(hub|difficulty|rival|spar|skillcheck|prestige|character|progression|skills|android_remove)");
                hubChestGui.open(admin, "main");
            }
        }
    }

    /**
     * Staff inspect: open chest GUI for admin while painting/editing {@code subjectId}.
     * Chest-only so clicks are bound to the subject (CMI buttons would edit the admin).
     * Defaults to Difficulty for ABI compatibility with older Forge jars.
     */
    public void openInspectForUuid(UUID adminId, UUID subjectId, String page) {
        openLmInspectForUuid(adminId, subjectId, "difficulty", page);
    }

    /** Player-facing open that honors Forge {@code guiBackend}. */
    public void openMenuRespectingConfig(Player player, String page) {
        if (player == null) {
            return;
        }
        // Keep staff inspect sessions — CMI/lmdo difficulty reopen must stay on the subject.
        if (AdminInspectSessions.isInspecting(player.getUniqueId())) {
            Player subject = AdminInspectSessions.resolveSubject(player);
            if (subject != null) {
                ForgeBridge.prepareDifficultyGui(subject);
                chestGui.openAs(player, subject, page == null || page.isBlank() ? "main" : page);
                return;
            }
        }
        AdminInspectSessions.clear(player.getUniqueId());
        ForgeBridge.prepareDifficultyGui(player);
        String backend = ForgeBridge.guiBackend();
        // Ultra-only: retired cnpc/chat/chest values are coerced to ultra in ForgeBridge.
        if ("ultra".equals(backend) || "cnpc".equals(backend)) {
            openUltraMenu(player, "difficulty", page);
            return;
        }
        if ("chat".equals(backend)) {
            if (!ForgeBridge.openChatMenu(player, page)) {
                player.sendMessage("§cChat difficulty menu unavailable (is the Forge mod loaded?).");
            }
            return;
        }
        if ("chest".equals(backend)) {
            chestGui.open(player, page);
            return;
        }
        // Legacy cmi / auto → Ultra (no CNPC ClassNotFoundException path).
        openUltraMenu(player, "difficulty", page);
    }

    private void openInventory(Player player, String page) {
        // prepareDifficultyGui already ran from openMenuRespectingConfig; still safe if
        // Forge openMenuForUuid lands here without that path.
        ForgeBridge.prepareDifficultyGui(player);
        if (CmiDifficultyGui.available() && CmiDifficultyGui.open(player, page)) {
            return;
        }
        if (CmiDifficultyGui.available()) {
            getLogger().warning("CMI GUI open failed for " + player.getName()
                    + " — falling back to chest GUI. Check CMILib version.");
        } else {
            getLogger().warning("CMILib/CMI not available — using chest GUI for "
                    + player.getName() + ". Install CMILib + CMI for the CMI inventory UI.");
        }
        chestGui.open(player, page);
    }

    private void openInspect(Player admin, Player subject, String page) {
        AdminInspectSessions.set(admin.getUniqueId(), subject.getUniqueId());
        admin.sendMessage("§eInspecting §f" + subject.getName()
                + "§e's Legacy Mechanics. Edits apply to them.");
        admin.sendMessage("§8Hub: §f/lm admin inspect " + subject.getName()
                + " hub §8· Exit: §f/lm admin inspect clear");
        chestGui.openAs(admin, subject, page == null || page.isBlank() ? "main" : page);
    }

    // ── Rival ──────────────────────────────────────────────────────────

    public void openRivalMenu(Player player, String page) {
        if (player == null) {
            return;
        }
        openRivalInventory(player, page);
    }

    public void openRivalMenuForUuid(UUID playerId, String page) {
        if (playerId == null) {
            return;
        }
        String target = page == null || page.isBlank() ? "main" : page;
        Runnable task = () -> {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null && p.isOnline()) {
                openRivalInventory(p, target);
            } else {
                getLogger().warning("openRivalMenuForUuid: player offline/unresolved " + playerId);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    public void openRivalChestMenu(Player player, String page) {
        if (player == null) {
            return;
        }
        rivalChestGui.open(player, page);
    }

    public void openRivalChestMenuForUuid(UUID playerId, String page) {
        if (playerId == null) {
            return;
        }
        String target = page == null || page.isBlank() ? "main" : page;
        Runnable task = () -> {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null && p.isOnline()) {
                rivalChestGui.open(p, target);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    private void openRivalInventory(Player player, String page) {
        if (CmiRivalGui.available() && CmiRivalGui.open(player, page)) {
            return;
        }
        rivalChestGui.open(player, page);
    }

    private void openRivalRespectingConfig(Player player, String page) {
        if (player == null) {
            return;
        }
        String backend = ForgeBridge.guiBackend();
        if ("chat".equals(backend)) {
            if (!ForgeBridge.openRivalChatMenu(player, page)) {
                player.sendMessage("§cRival chat menu unavailable (is the Forge mod loaded?).");
            }
            return;
        }
        if ("ultra".equals(backend) || "cnpc".equals(backend)) {
            openUltraMenu(player, "rival", page);
            return;
        }
        if ("chest".equals(backend)) {
            rivalChestGui.open(player, page);
            return;
        }
        openRivalInventory(player, page);
    }

    // ── Spar ───────────────────────────────────────────────────────────

    public void openSparMenu(Player player, String page) {
        if (player == null) {
            return;
        }
        openSparInventory(player, page);
    }

    public void openSparMenuForUuid(UUID playerId, String page) {
        if (playerId == null) {
            return;
        }
        String target = page == null || page.isBlank() ? "main" : page;
        Runnable task = () -> {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null && p.isOnline()) {
                openSparInventory(p, target);
            } else {
                getLogger().warning("openSparMenuForUuid: player offline/unresolved " + playerId);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    public void openSparChestMenu(Player player, String page) {
        if (player == null) {
            return;
        }
        sparChestGui.open(player, page);
    }

    public void openSparChestMenuForUuid(UUID playerId, String page) {
        if (playerId == null) {
            return;
        }
        String target = page == null || page.isBlank() ? "main" : page;
        Runnable task = () -> {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null && p.isOnline()) {
                sparChestGui.open(p, target);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    private void openSparInventory(Player player, String page) {
        if (CmiSparGui.available() && CmiSparGui.open(player, page)) {
            return;
        }
        sparChestGui.open(player, page);
    }

    private void openSparRespectingConfig(Player player, String page) {
        if (player == null) {
            return;
        }
        String backend = ForgeBridge.guiBackend();
        if ("chat".equals(backend)) {
            if (!ForgeBridge.openSparChatMenu(player, page)) {
                player.sendMessage("§cSpar chat menu unavailable (is the Forge mod loaded?).");
            }
            return;
        }
        if ("ultra".equals(backend) || "cnpc".equals(backend)) {
            openUltraMenu(player, "spar", page);
            return;
        }
        if ("chest".equals(backend)) {
            sparChestGui.open(player, page);
            return;
        }
        openSparInventory(player, page);
    }

    // ── Hub (/lm) ──────────────────────────────────────────────────────

    public void openHubMenu(Player player, String page) {
        if (player == null) {
            return;
        }
        openHubInventory(player, page);
    }

    public void openHubMenuForUuid(UUID playerId, String page) {
        runForUuid(playerId, page, "main", this::openHubInventory, "openHubMenuForUuid");
    }

    public void openHubChestMenu(Player player, String page) {
        if (player == null) {
            return;
        }
        hubChestGui.open(player, page);
    }

    public void openHubChestMenuForUuid(UUID playerId, String page) {
        runForUuid(playerId, page, "main", hubChestGui::open, null);
    }

    private void openHubInventory(Player player, String page) {
        if (CmiHubGui.available() && CmiHubGui.open(player, page)) {
            return;
        }
        hubChestGui.open(player, page);
    }

    private void openHubRespectingConfig(Player player, String page) {
        if (player == null) {
            return;
        }
        // Inspect sessions stay on chest hub for the subject (CMI/chat would paint self).
        if (AdminInspectSessions.isInspecting(player.getUniqueId())) {
            Player subject = AdminInspectSessions.resolveSubject(player);
            if (subject != null && !subject.getUniqueId().equals(player.getUniqueId())) {
                hubChestGui.open(player, page == null || page.isBlank() ? "main" : page);
                return;
            }
        }
        String backend = ForgeBridge.guiBackend();
        if ("chat".equals(backend)) {
            if (!ForgeBridge.openHubChatMenu(player, page)) {
                player.sendMessage("§cHub chat menu unavailable (is the Forge mod loaded?).");
            }
            return;
        }
        if ("ultra".equals(backend) || "cnpc".equals(backend)) {
            openUltraMenu(player, "hub", page);
            return;
        }
        if ("chest".equals(backend)) {
            hubChestGui.open(player, page);
            return;
        }
        openHubInventory(player, page);
    }

    /** Forge Ultra / KaiPath UI (replaces deleted CNPC menus). */
    private void openUltraMenu(Player player, String system, String page) {
        if (!ForgeBridge.openUltraLm(player, system, page)) {
            String err = ForgeBridge.lastError();
            player.sendMessage("§cLM menu failed" + (err == null || err.isBlank() ? "." : ": " + err));
        }
    }

    /** @deprecated Use {@link #openUltraMenu}; kept so any leftover call sites still hit Ultra. */
    @Deprecated
    private void openCnpcMenu(Player player, String system, String page) {
        openUltraMenu(player, system, page);
    }

    // ── Progression ────────────────────────────────────────────────────

    public void openProgressionMenu(Player player, String page) {
        if (player == null) {
            return;
        }
        openProgressionInventory(player, page);
    }

    public void openProgressionMenuForUuid(UUID playerId, String page) {
        runForUuid(playerId, page, "main", this::openProgressionInventory, "openProgressionMenuForUuid");
    }

    public void openProgressionChestMenu(Player player, String page) {
        if (player == null) {
            return;
        }
        progressionChestGui.open(player, page);
    }

    public void openProgressionChestMenuForUuid(UUID playerId, String page) {
        runForUuid(playerId, page, "main", progressionChestGui::open, null);
    }

    void openProgressionInventory(Player player, String page) {
        if (CmiProgressionGui.available() && CmiProgressionGui.open(player, page)) {
            return;
        }
        progressionChestGui.open(player, page);
    }

    void openProgressionRespectingConfig(Player player, String page) {
        if (player == null) {
            return;
        }
        String p = page == null || page.isBlank() ? "main" : page;
        // Player-facing Android remove: inventory only (chat progression menu is staff-gated).
        if (isAndroidRemovePage(p)) {
            String androidBackend = ForgeBridge.guiBackend();
            if ("ultra".equals(androidBackend) || "cnpc".equals(androidBackend)) {
                openUltraMenu(player, "android_remove", "main");
            } else if ("chest".equals(androidBackend)) {
                progressionChestGui.open(player, "android_remove");
            } else {
                openUltraMenu(player, "android_remove", "main");
            }
            return;
        }
        String backend = ForgeBridge.guiBackend();
        if ("chat".equals(backend)) {
            if (!ForgeBridge.openProgressionChatMenu(player, p)) {
                player.sendMessage("§cProgression chat menu unavailable (is the Forge mod loaded?).");
            }
            return;
        }
        if ("ultra".equals(backend) || "cnpc".equals(backend)) {
            openUltraMenu(player, "progression", p);
            return;
        }
        if ("chest".equals(backend)) {
            progressionChestGui.open(player, p);
            return;
        }
        openProgressionInventory(player, p);
    }

    private static boolean isAndroidRemovePage(String page) {
        if (page == null) {
            return false;
        }
        return switch (page.toLowerCase(Locale.ROOT).trim()) {
            case "android_remove", "androidremove", "remove_android", "deandroid" -> true;
            default -> false;
        };
    }

    // ── Prestige ───────────────────────────────────────────────────────

    public void openPrestigeMenu(Player player, String page) {
        if (player == null) {
            return;
        }
        openPrestigeInventory(player, page);
    }

    public void openPrestigeMenuForUuid(UUID playerId, String page) {
        runForUuid(playerId, page, "main", this::openPrestigeInventory, "openPrestigeMenuForUuid");
    }

    public void openPrestigeChestMenu(Player player, String page) {
        if (player == null) {
            return;
        }
        prestigeChestGui.open(player, page);
    }

    public void openPrestigeChestMenuForUuid(UUID playerId, String page) {
        runForUuid(playerId, page, "main", prestigeChestGui::open, null);
    }

    private void openPrestigeInventory(Player player, String page) {
        if (CmiPrestigeGui.available() && CmiPrestigeGui.open(player, page)) {
            return;
        }
        prestigeChestGui.open(player, page);
    }

    private void openPrestigeRespectingConfig(Player player, String page) {
        if (player == null) {
            return;
        }
        String backend = ForgeBridge.guiBackend();
        if ("chat".equals(backend)) {
            // No dedicated prestige chat menu — inventory GUI (avoid Forge /prestige forward).
            openPrestigeInventory(player, page);
            return;
        }
        if ("ultra".equals(backend) || "cnpc".equals(backend)) {
            openUltraMenu(player, "prestige", page);
            return;
        }
        if ("chest".equals(backend)) {
            prestigeChestGui.open(player, page);
            return;
        }
        openPrestigeInventory(player, page);
    }

    // ── Character Services ─────────────────────────────────────────────

    public void openCharacterServicesMenu(Player player, String page) {
        if (player == null) {
            return;
        }
        openCharacterServicesInventory(player, page);
    }

    public void openCharacterServicesMenuForUuid(UUID playerId, String page) {
        runForUuid(playerId, page, "main", this::openCharacterServicesInventory, "openCharacterServicesMenuForUuid");
    }

    public void openCharacterServicesChestMenu(Player player, String page) {
        if (player == null) {
            return;
        }
        characterServicesChestGui.open(player, page);
    }

    public void openCharacterServicesChestMenuForUuid(UUID playerId, String page) {
        runForUuid(playerId, page, "main", characterServicesChestGui::open, null);
    }

    private void openCharacterServicesInventory(Player player, String page) {
        characterServicesChestGui.open(player, page);
    }

    private void openCharacterServicesRespectingConfig(Player player, String page) {
        if (player == null) {
            return;
        }
        String backend = ForgeBridge.guiBackend();
        if ("ultra".equals(backend) || "cnpc".equals(backend)) {
            openUltraMenu(player, "character", page);
            return;
        }
        if ("chest".equals(backend)) {
            characterServicesChestGui.open(player, page);
            return;
        }
        openCharacterServicesInventory(player, page);
    }

    // ── Skills ─────────────────────────────────────────────────────────

    public void openSkillsMenu(Player player, String page) {
        if (player == null) {
            return;
        }
        openSkillsInventory(player, page);
    }

    public void openSkillsMenuForUuid(UUID playerId, String page) {
        runForUuid(playerId, page, "core", this::openSkillsInventory, "openSkillsMenuForUuid");
    }

    public void openSkillsChestMenu(Player player, String page) {
        if (player == null) {
            return;
        }
        skillsChestGui.open(player, page);
    }

    public void openSkillsChestMenuForUuid(UUID playerId, String page) {
        runForUuid(playerId, page, "core", skillsChestGui::open, null);
    }

    private void openSkillsInventory(Player player, String page) {
        openSkillsInventory(player, page, false);
    }

    private void openSkillsInventory(Player player, String page, boolean skillCheckMode) {
        if (!ensureSkillsGuiAccess(player, skillCheckMode)) {
            return;
        }
        if (skillCheckMode) {
            ForgeBridge.markSkillCheckSession(player);
        }
        if (CmiSkillsGui.available() && CmiSkillsGui.open(player, page, skillCheckMode)) {
            return;
        }
        skillsChestGui.open(player, page, skillCheckMode);
    }

    private void openSkillsRespectingConfig(Player player, String page) {
        openSkillsRespectingConfig(player, page, false);
    }

    private void openSkillsRespectingConfig(Player player, String page, boolean skillCheckMode) {
        if (player == null) {
            return;
        }
        if (!ensureSkillsGuiAccess(player, skillCheckMode)) {
            return;
        }
        if (skillCheckMode) {
            ForgeBridge.markSkillCheckSession(player);
        }
        String backend = ForgeBridge.guiBackend();
        if ("chat".equals(backend)) {
            // No dedicated skills chat menu — inventory GUI (avoid Forge /skills do forward).
            openSkillsInventory(player, page, skillCheckMode);
            return;
        }
        if ("ultra".equals(backend) || "cnpc".equals(backend)) {
            String sys = skillCheckMode ? "skillcheck" : "skills";
            openUltraMenu(player, sys, page);
            return;
        }
        if ("chest".equals(backend)) {
            skillsChestGui.open(player, page, skillCheckMode);
            return;
        }
        openSkillsInventory(player, page, skillCheckMode);
    }

    /**
     * Skill Check = donator node only. Skills admin browser = staff/op only.
     * Staff inspect may open either mode for an online subject.
     */
    private boolean ensureSkillsGuiAccess(Player player, boolean skillCheckMode) {
        if (player == null) {
            return false;
        }
        if (AdminInspectSessions.isInspecting(player.getUniqueId())) {
            Player subject = AdminInspectSessions.resolveSubject(player);
            if (subject != null && !subject.getUniqueId().equals(player.getUniqueId())) {
                if (!ForgeBridge.isStaff(player)) {
                    player.sendMessage("§cStaff only.");
                    return false;
                }
                return true;
            }
        }
        if (skillCheckMode) {
            if (!ForgeBridge.hasSkillCheck(player)) {
                player.sendMessage("§cSkill Check requires donator access.");
                return false;
            }
            return true;
        }
        if (!ForgeBridge.isStaff(player)) {
            player.sendMessage("§cStaff only. Use Skill Check if you have donator access.");
            return false;
        }
        return true;
    }

    private void runForUuid(
            UUID playerId, String page, String defaultPage,
            java.util.function.BiConsumer<Player, String> opener, String warnLabel) {
        if (playerId == null) {
            return;
        }
        String target = page == null || page.isBlank() ? defaultPage : page;
        Runnable task = () -> {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null && p.isOnline()) {
                opener.accept(p, target);
            } else if (warnLabel != null) {
                getLogger().warning(warnLabel + ": player offline/unresolved " + playerId);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command == null || !"lm".equalsIgnoreCase(command.getName())) {
            return null;
        }
        return completeLmMastery(args);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        if ("rival".equals(name)) {
            return handleRival(sender, args);
        }
        if ("spar".equals(name)) {
            return handleSpar(sender, args);
        }
        if ("lm".equals(name)) {
            return handleHub(sender, args);
        }
        if ("progression".equals(name)) {
            return handleProgression(sender, args);
        }
        if ("androidify".equals(name) || "androidification".equals(name)) {
            return progressionCommandTree.executeAndroidify(sender, args);
        }
        if ("prestige".equals(name) || "padmin".equals(name) || "prestigeadmin".equals(name)) {
            if ("padmin".equals(name) || "prestigeadmin".equals(name)) {
                // Dedicated staff command — never contested by Forge /prestige.
                String[] adminArgs = new String[args.length + 1];
                adminArgs[0] = "admin";
                System.arraycopy(args, 0, adminArgs, 1, args.length);
                return handlePrestige(sender, adminArgs);
            }
            return handlePrestige(sender, args);
        }
        if ("skills".equals(name)) {
            return handleSkills(sender, args);
        }
        if ("skillcheck".equals(name)) {
            return handleSkillCheck(sender, args);
        }
        if ("lmdo".equals(name)) {
            return handleLmDo(sender, args);
        }
        if ("terminal".equals(name)) {
            return handleTerminal(sender);
        }
        if (!"difficulty".equals(name)) {
            return false;
        }
        return handleDifficulty(sender, args);
    }

    /** {@code /terminal} — Lightman's Currency network terminal. Donators, or the terminal permission. */
    private boolean handleTerminal(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        String msg = ForgeBridge.openLightmanTerminal(player);
        if (msg != null && !msg.isBlank()) {
            player.sendMessage(msg);
        }
        return true;
    }

    /**
     * Bukkit-only GUI action bridge. CMI buttons use {@code /lmdo rival|spar …} so Mohist does not
     * route clicks to Forge's incomplete {@code /rival do} / {@code /spar do} trees.
     */
    private boolean handleLmDo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!canUsePlayerGui(player)) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: /lmdo <rival|spar|…> <action> [arg] [page]");
            return true;
        }
        String system = args[0].toLowerCase(Locale.ROOT);
        String action;
        String arg;
        String returnPage;
        if (("spar".equals(system) || "sparring".equals(system)) && args.length >= 3
                && "do".equalsIgnoreCase(args[1])) {
            action = args[2];
            if (args.length == 3) {
                arg = "";
                returnPage = "main";
            } else if (args.length == 4) {
                arg = args[3];
                returnPage = "main";
            } else {
                returnPage = args[args.length - 1];
                StringBuilder mid = new StringBuilder(args[3]);
                for (int i = 4; i < args.length - 1; i++) {
                    mid.append(' ').append(args[i]);
                }
                arg = mid.toString();
            }
        } else {
            action = args[1];
            arg = args.length > 2 ? args[2] : "";
            returnPage = args.length > 3 ? args[3] : "main";
        }
        // For page/refresh, the page name may contain ':' (challenge_time:uuid:…).
        // When only 3 tokens: lmdo rival page challenge_time:uuid:x — arg is the page.
        if (("page".equalsIgnoreCase(action) || "refresh".equalsIgnoreCase(action))
                && args.length >= 3) {
            // Join remaining tokens in case of spaces (unlikely).
            StringBuilder page = new StringBuilder(args[2]);
            for (int i = 3; i < args.length; i++) {
                page.append(' ').append(args[i]);
            }
            arg = page.toString();
            returnPage = arg;
        } else if (args.length > 4) {
            // Extra tokens belong to arg (e.g. spaced names) — last is return page.
            StringBuilder mid = new StringBuilder(args[2]);
            for (int i = 3; i < args.length - 1; i++) {
                mid.append(' ').append(args[i]);
            }
            arg = mid.toString();
            returnPage = args[args.length - 1];
        }

        // Hub "open <system>" must switch menus — do not reopen the hub afterward.
        if (("lm".equals(system) || "hub".equals(system) || "legacymechanics".equals(system))
                && "open".equalsIgnoreCase(action)) {
            String openPage = returnPage == null || returnPage.isBlank() || "main".equalsIgnoreCase(returnPage)
                    ? "main" : returnPage;
            // When only system is given (3 tokens), keep default main page.
            if (args.length < 4) {
                openPage = "main";
            }
            openSystemFromHub(player, arg, openPage);
            return true;
        }

        // End dragon spawn/clear — no GUI reopen (CMI /enddragon aliases land here).
        if ("enddragon".equals(system) || "cleardragons".equals(system)
                || "spawndragon".equals(system) || "killdragons".equals(system)) {
            String endAction = "enddragon".equals(system) || "spawndragon".equals(system)
                    ? action
                    : "clear";
            if ("cleardragons".equals(system) || "killdragons".equals(system)) {
                endAction = "clear";
            }
            String msg = ForgeBridge.endDragon(player, endAction);
            if (msg != null && !msg.isBlank()) {
                GuiChat.sendResult(player, msg);
            }
            return true;
        }

        Player subject = AdminInspectSessions.resolveSubject(player);
        String reopen;
        if ("page".equalsIgnoreCase(action) || "refresh".equalsIgnoreCase(action)) {
            reopen = arg == null || arg.isBlank() ? "main" : arg;
        } else {
            reopen = returnPage == null || returnPage.isBlank() ? "main" : returnPage;
            Player actor = AdminInspectSessions.resolveActor(player, action);
            String msg = switch (system) {
                case "rival", "rivals" -> ForgeBridge.rivalHandleDo(actor, action, arg, reopen);
                case "spar", "sparring" -> ForgeBridge.sparHandleDo(actor, action, arg, reopen);
                case "difficulty", "diff", "ad" -> {
                    ForgeBridge.ActionResult r = ForgeBridge.handleActionResult(subject, action, arg, reopen);
                    yield r.message();
                }
                case "lm", "hub", "legacymechanics" -> ForgeBridge.hubHandleDo(subject, action, arg, reopen);
                case "progression", "prog" -> ForgeBridge.progressionHandleDo(subject, action, arg, reopen);
                case "prestige" -> ForgeBridge.prestigeHandleDo(subject, action, arg, reopen);
                case "skills", "skillcheck" -> ForgeBridge.skillsHandleDo(subject, action, arg, reopen);
                default -> {
                    player.sendMessage("§cUnknown lmdo system: " + system);
                    yield null;
                }
            };
            if (msg != null && !msg.isBlank()) {
                if ("admin".equalsIgnoreCase(action)) {
                    GuiChat.sendChatResult(player, msg);
                } else {
                    GuiChat.sendResult(player, msg);
                }
            }
            if (("rival".equals(system) || "rivals".equals(system))
                    && ("accept".equalsIgnoreCase(action)
                    || "accept_replace".equalsIgnoreCase(action)
                    || "acceptreplace".equalsIgnoreCase(action))
                    && ForgeBridge.rivalNeedsMutualReplace(subject)) {
                reopen = "pick_replace_mutual";
            }
            if (!switch (system) {
                case "rival", "rivals", "spar", "sparring",
                     "difficulty", "diff", "ad",
                     "lm", "hub", "legacymechanics",
                     "progression", "prog", "prestige", "skills", "skillcheck" -> true;
                default -> false;
            }) {
                return true;
            }
        }

        switch (system) {
            case "rival", "rivals" -> {
                if (AdminInspectSessions.isInspecting(player.getUniqueId()) && subject != null) {
                    openInspectSystem(player, subject, "rival", reopen);
                    return true;
                }
                openRivalRespectingConfig(player, reopen);
            }
            case "spar", "sparring" -> {
                if (AdminInspectSessions.isInspecting(player.getUniqueId()) && subject != null) {
                    openInspectSystem(player, subject, "spar", reopen);
                    return true;
                }
                openSparRespectingConfig(player, reopen);
            }
            case "difficulty", "diff", "ad" -> openMenuRespectingConfig(player, reopen);
            case "lm", "hub", "legacymechanics" -> {
                if (AdminInspectSessions.isInspecting(player.getUniqueId()) && subject != null
                        && !subject.getUniqueId().equals(player.getUniqueId())) {
                    openInspectSystem(player, subject, "hub", reopen);
                    return true;
                }
                openHubRespectingConfig(player, reopen);
            }
            case "progression", "prog" -> {
                if (!ForgeBridge.isStaff(player) && !isAndroidRemovePage(reopen)) {
                    player.sendMessage("§cStaff only.");
                    return true;
                }
                if (AdminInspectSessions.isInspecting(player.getUniqueId()) && subject != null) {
                    openInspectSystem(player, subject,
                            isAndroidRemovePage(reopen) ? "android_remove" : "progression",
                            reopen);
                    return true;
                }
                openProgressionRespectingConfig(player, reopen);
            }
            case "prestige" -> {
                if (AdminInspectSessions.isInspecting(player.getUniqueId()) && subject != null) {
                    openInspectSystem(player, subject, "prestige", reopen);
                    return true;
                }
                openPrestigeRespectingConfig(player, reopen);
            }
            case "skills" -> {
                if (!ForgeBridge.isStaff(player)) {
                    player.sendMessage("§cStaff only. Use Skill Check if you have access.");
                    return true;
                }
                if (AdminInspectSessions.isInspecting(player.getUniqueId()) && subject != null) {
                    openInspectSystem(player, subject, "skills", reopen);
                    return true;
                }
                openSkillsRespectingConfig(player, reopen);
            }
            case "skillcheck" -> {
                if (AdminInspectSessions.isInspecting(player.getUniqueId()) && subject != null) {
                    if (!ForgeBridge.isStaff(player)) {
                        player.sendMessage("§cStaff only.");
                        return true;
                    }
                    openInspectSystem(player, subject, "skillcheck", reopen);
                    return true;
                }
                if (!ForgeBridge.hasSkillCheck(player)) {
                    player.sendMessage("§cSkill Check requires donator access.");
                    return true;
                }
                openSkillsRespectingConfig(player, reopen, true);
            }
            default -> player.sendMessage("§cUnknown lmdo system: " + system);
        }
        return true;
    }

    private boolean handleHub(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            if (args.length > 0 && "admin".equalsIgnoreCase(args[0])) {
                return handleLmAdmin(sender, args);
            }
            if (args.length > 0 && "mastery".equalsIgnoreCase(args[0])) {
                return handleLmMastery(sender, args);
            }
            sender.sendMessage("Players only.");
            return true;
        }
        if (!canUsePlayerGui(player)) {
            player.sendMessage("§cNo permission: dmzdiff.gui");
            return true;
        }
        if (args.length == 0 || "gui".equalsIgnoreCase(args[0])) {
            openHubRespectingConfig(player, "main");
            return true;
        }
        String sub = args[0].toLowerCase();
        if ("admin".equals(sub)) {
            return handleLmAdmin(sender, args);
        }
        if ("do".equals(sub)) {
            String action = args.length > 1 ? args[1] : "";
            String arg = args.length > 2 ? args[2] : "";
            String returnPage = args.length > 3 ? args[3] : null;
            if ("open".equalsIgnoreCase(action)) {
                openSystemFromHub(player, arg);
                return true;
            }
            String reopen;
            if ("page".equalsIgnoreCase(action) || "refresh".equalsIgnoreCase(action)) {
                reopen = arg == null || arg.isBlank() ? "main" : arg;
            } else {
                reopen = returnPage == null || returnPage.isBlank() ? "main" : returnPage;
                String msg = ForgeBridge.hubHandleDo(player, action, arg, reopen);
                if (msg != null && !msg.isBlank()) {
                    if (!msg.startsWith("§")) {
                        msg = "§a" + msg;
                    }
                    player.sendMessage(msg);
                }
            }
            if ("chat".equals(ForgeBridge.guiBackend())) {
                ForgeBridge.openHubChatMenu(player, reopen);
            } else {
                openHubInventory(player, reopen);
            }
            return true;
        }
        if ("help".equals(sub) || "logs".equals(sub) || "syslog".equals(sub)) {
            if ("help".equals(sub)) {
                openHubRespectingConfig(player, "main");
            } else {
                openHubRespectingConfig(player, sub);
            }
            return true;
        }
        if ("mastery".equals(sub)) {
            return handleLmMastery(sender, args);
        }
        forwardToForge(player, "lm", args);
        return true;
    }

    /**
     * {@code /lm mastery grant-first-superform} and {@code grant-first-legendary}.
     * Amount then name, or name then amount. Console must name a player.
     */
    private boolean handleLmMastery(CommandSender sender, String[] args) {
        boolean staff = sender instanceof Player player
                ? ForgeBridge.isStaff(player)
                : sender.isOp() || sender.hasPermission(ForgeBridge.adminPermission());
        if (!staff) {
            sender.sendMessage("§cStaff only.");
            return true;
        }
        if (args.length < 2) {
            sendTry(sender, "/lm mastery grant-first-superform <amount> [player]");
            return true;
        }
        String grant = args[1].toLowerCase(Locale.ROOT);
        if (!"grant-first-superform".equals(grant) && !"grant-first-legendary".equals(grant)) {
            sendTry(sender, "/lm mastery grant-first-superform <amount> [player]");
            return true;
        }
        String self = sender instanceof Player player ? player.getName() : null;
        ParsedMastery parsed = parseMasteryArgs(args, self);
        if (parsed == null) {
            String hint = self == null
                    ? "/lm mastery " + grant + " <amount> <player>"
                    : "/lm mastery " + grant + " <amount> [player]";
            sendTry(sender, hint);
            return true;
        }
        boolean notify = self == null || !self.equalsIgnoreCase(parsed.playerName);
        String msg = ForgeBridge.grantFirstFormMastery(
                parsed.playerName, parsed.amount, grant, notify);
        if (msg != null && !msg.isBlank()) {
            for (String line : msg.split("\n")) {
                if (line != null && !line.isBlank()) {
                    sender.sendMessage(line);
                }
            }
        }
        return true;
    }

    private static void sendTry(CommandSender sender, String command) {
        sender.sendMessage("§cTry: §f" + command);
    }

    /** {@code args} is {@code mastery <grant> <token> [token]}. {@code selfName} is null from console. */
    private static ParsedMastery parseMasteryArgs(String[] args, String selfName) {
        if (args.length < 3) {
            return null;
        }
        String first = args[2];
        String second = args.length > 3 ? args[3] : null;
        Double amountFirst = masteryAmount(first);
        if (amountFirst != null) {
            if (second == null || second.isBlank()) {
                if (selfName == null || selfName.isBlank()) {
                    return null;
                }
                return new ParsedMastery(amountFirst, selfName);
            }
            return new ParsedMastery(amountFirst, second);
        }
        Double amountSecond = masteryAmount(second);
        if (amountSecond != null && first != null && !first.isBlank() && !isMasteryAmount(first)) {
            return new ParsedMastery(amountSecond, first);
        }
        return null;
    }

    private static Double masteryAmount(String token) {
        if (!isMasteryAmount(token)) {
            return null;
        }
        return Double.parseDouble(token.trim());
    }

    private static boolean isMasteryAmount(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            double value = Double.parseDouble(token.trim());
            return Double.isFinite(value) && value >= 0.01;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private List<String> completeLmMastery(String[] args) {
        if (args == null || args.length < 2 || !"mastery".equalsIgnoreCase(args[0])) {
            return null;
        }
        if (args.length == 2) {
            return filterPrefix(
                    List.of("grant-first-superform", "grant-first-legendary"), args[1]);
        }
        String grant = args[1] == null ? "" : args[1].toLowerCase(Locale.ROOT);
        if (!"grant-first-superform".equals(grant) && !"grant-first-legendary".equals(grant)) {
            return null;
        }
        if (args.length == 3 && !isMasteryAmount(args[2])) {
            return filterPrefix(onlineNames(), args[2]);
        }
        if (args.length == 4 && isMasteryAmount(args[2])) {
            return filterPrefix(onlineNames(), args[3]);
        }
        return List.of();
    }

    private static List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online != null && online.getName() != null && !online.getName().isBlank()) {
                names.add(online.getName());
            }
        }
        return names;
    }

    private static List<String> filterPrefix(List<String> options, String partial) {
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option != null && option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(option);
            }
        }
        return out;
    }

    private static final class ParsedMastery {
        final double amount;
        final String playerName;

        ParsedMastery(double amount, String playerName) {
            this.amount = amount;
            this.playerName = playerName;
        }
    }

    /**
     * Open a system inventory from the hub without {@code performCommand} to bare
     * {@code /difficulty}/{@code /rival}/… (keeps guide path on {@code /lm}).
     * When the viewer is inspecting another player, opens chest GUIs as that subject
     * and does not clear the inspect session.
     */
    public void openSystemFromHub(Player player, String system) {
        openSystemFromHub(player, system, "main");
    }

    /**
     * Hub hop into a system GUI. {@code page} is honored for multi-page systems
     * (prestige turn-in / shop / forms / cap).
     */
    public void openSystemFromHub(Player player, String system, String page) {
        if (player == null) {
            return;
        }
        String s = system == null ? "" : system.toLowerCase(Locale.ROOT).trim();
        String p = page == null || page.isBlank() ? "main" : page;
        if (AdminInspectSessions.isInspecting(player.getUniqueId())) {
            Player subject = AdminInspectSessions.resolveSubject(player);
            if (subject != null && !subject.getUniqueId().equals(player.getUniqueId())) {
                openInspectSystem(player, subject, s.isBlank() ? "hub" : s, p);
                return;
            }
        }
        switch (s) {
            case "difficulty", "diff", "ad" -> openMenuRespectingConfig(player, p);
            case "rival", "rivals", "rivalry" -> openRivalRespectingConfig(player, p);
            case "spar", "sparring" -> openSparRespectingConfig(player, p);
            case "skillcheck", "skill_check" -> {
                if (!ForgeBridge.hasSkillCheck(player)) {
                    player.sendMessage("§cSkill Check requires donator access.");
                    openHubInventory(player, "main");
                    return;
                }
                openSkillsRespectingConfig(player, "core".equals(p) || "main".equals(p) ? "core" : p, true);
            }
            case "skills", "skill" -> {
                if (!ForgeBridge.isStaff(player)) {
                    player.sendMessage("§cStaff only. Use Skill Check if you have access.");
                    openHubInventory(player, "main");
                    return;
                }
                openSkillsRespectingConfig(player, "core".equals(p) || "main".equals(p) ? "core" : p);
            }
            case "prestige" -> openPrestigeRespectingConfig(player, p);
            case "character", "characterservices", "charservices", "char" ->
                    openCharacterServicesRespectingConfig(player, p);
            case "android_remove", "androidremove", "remove_android", "deandroid" ->
                    openProgressionRespectingConfig(player, "android_remove");
            case "progression", "prog" -> {
                if (!ForgeBridge.isStaff(player)) {
                    player.sendMessage("§cStaff only. §7Use §f/lm §7→ Remove Android for your upgrade.");
                    openHubInventory(player, "main");
                    return;
                }
                openProgressionRespectingConfig(player, p);
            }
            case "admin" -> {
                if (!ForgeBridge.isStaff(player)) {
                    player.sendMessage("§cStaff only.");
                    return;
                }
                openHubRespectingConfig(player, "admin");
            }
            case "logs", "syslog" -> {
                if (!ForgeBridge.isStaff(player)) {
                    player.sendMessage("§cStaff only.");
                    return;
                }
                openHubRespectingConfig(player, "logs");
            }
            case "help", "hub", "lm" -> openHubInventory(player, "main");
            default -> {
                player.sendMessage("§cUnknown system: " + s
                        + " §8(difficulty|rival|spar|skillcheck|prestige|character|progression)");
                openHubInventory(player, "main");
            }
        }
    }

    private boolean handleLmAdmin(CommandSender sender, String[] args) {
        boolean staff = sender instanceof Player p
                ? ForgeBridge.isStaff(p)
                : sender.isOp() || sender.hasPermission(ForgeBridge.adminPermission());
        if (!staff) {
            sender.sendMessage("§cStaff only.");
            return true;
        }
        if (args.length < 2 || "help".equalsIgnoreCase(args[1])) {
            sendLmAdminHelp(sender);
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (ForgeBridge.reloadConfig()) {
                    sender.sendMessage("§aLegacyMechanics config reloaded.");
                } else {
                    String err = ForgeBridge.lastError();
                    sender.sendMessage("§cReload failed" + (err == null ? "." : ": " + err));
                }
                sender.sendMessage(GuiTooltips.reload());
            }
            case "migrate-cnpc", "migratecnpc", "cnpcmigrate", "cnpc-migrate" -> {
                boolean force = args.length > 2 && (
                        "force".equalsIgnoreCase(args[2])
                                || "overwrite".equalsIgnoreCase(args[2]));
                String msg = ForgeBridge.migrateCnpc(force);
                if (msg == null || msg.isBlank()) {
                    sender.sendMessage("§cCNPC migrate failed (is LegacyMechanics Forge mod loaded?).");
                } else {
                    for (String line : msg.split("\n")) {
                        if (line != null && !line.isBlank()) {
                            sender.sendMessage(line);
                        }
                    }
                }
            }
            case "character", "charservices", "characterservices", "char" -> {
                if (args.length < 5
                        || !"cooldown".equalsIgnoreCase(args[2])
                        || !"clear".equalsIgnoreCase(args[3])) {
                    sender.sendMessage(
                            "§cUsage: /lm admin character cooldown clear <player> [race|class|reskin|all]");
                    return true;
                }
                String playerArg = args[4];
                String kind = args.length > 5 ? args[5] : "all";
                if (args.length > 6) {
                    String maybeKind = args[args.length - 1];
                    if (isCharacterCooldownKind(maybeKind)) {
                        kind = maybeKind;
                        StringBuilder sb = new StringBuilder(args[4]);
                        for (int i = 5; i < args.length - 1; i++) {
                            sb.append(' ').append(args[i]);
                        }
                        playerArg = sb.toString();
                    } else {
                        StringBuilder sb = new StringBuilder(args[4]);
                        for (int i = 5; i < args.length; i++) {
                            sb.append(' ').append(args[i]);
                        }
                        playerArg = sb.toString();
                        kind = "all";
                    }
                }
                String msg = ForgeBridge.clearCharacterServiceCooldowns(playerArg, kind);
                if (msg == null || msg.isBlank()) {
                    sender.sendMessage("§cCharacter cooldown clear failed (is LegacyMechanics loaded?).");
                } else {
                    for (String line : msg.split("\n")) {
                        if (line != null && !line.isBlank()) {
                            sender.sendMessage(line);
                        }
                    }
                }
            }
            case "clear", "wipe", "resetplayer" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /lm admin clear <player> [all|rival|spar|difficulty|progression]");
                    return true;
                }
                String playerArg = args[2];
                String scope = args.length > 3 ? args[3] : "all";
                // Multi-word names: last token is scope if known, else all remaining is name
                if (args.length > 4) {
                    String maybeScope = args[args.length - 1];
                    if (isClearScope(maybeScope)) {
                        scope = maybeScope;
                        StringBuilder sb = new StringBuilder(args[2]);
                        for (int i = 3; i < args.length - 1; i++) {
                            sb.append(' ').append(args[i]);
                        }
                        playerArg = sb.toString();
                    } else {
                        StringBuilder sb = new StringBuilder(args[2]);
                        for (int i = 3; i < args.length; i++) {
                            sb.append(' ').append(args[i]);
                        }
                        playerArg = sb.toString();
                        scope = "all";
                    }
                }
                String msg = ForgeBridge.clearPlayerData(playerArg, scope);
                if (msg == null || msg.isBlank()) {
                    sender.sendMessage("§cClear failed (is LegacyMechanics Forge mod loaded?).");
                } else {
                    for (String line : msg.split("\n")) {
                        if (line != null && !line.isBlank()) {
                            sender.sendMessage(line);
                        }
                    }
                }
            }
            case "syslog" -> {
                String mode = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "status";
                sender.sendMessage(ForgeBridge.syslogCommand(mode));
            }
            case "stafffree", "staffcoins", "freecoins" -> {
                String mode = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "status";
                switch (mode) {
                    case "on", "enable" -> sender.sendMessage(ForgeBridge.setStaffFreeAncientCoinCosts(true));
                    case "off", "disable" -> sender.sendMessage(ForgeBridge.setStaffFreeAncientCoinCosts(false));
                    case "toggle" -> sender.sendMessage(ForgeBridge.setStaffFreeAncientCoinCosts(
                            !ForgeBridge.staffFreeAncientCoinCosts()));
                    default -> sender.sendMessage(ForgeBridge.staffFreeAncientCoinCostsStatusText());
                }
                return true;
            }
            case "prestige", "padmin", "prestigeadmin" -> {
                String raw = joinArgs(args, 2);
                if (raw.isBlank()) {
                    raw = "help";
                }
                if (sender instanceof Player player) {
                    if (!ForgeBridge.isStaff(player)) {
                        sender.sendMessage("§cStaff only.");
                        return true;
                    }
                    GuiChat.sendChatResult(player, ForgeBridge.prestigeAdmin(player, raw));
                } else {
                    sendMultilineToSender(sender, ForgeBridge.prestigeAdmin(null, raw));
                }
            }
            case "open" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only for open.");
                    return true;
                }
                // /lm admin open <system>  OR  /lm admin open <player> [system]
                if (args.length < 3) {
                    openSystemFromHub(player, "hub");
                    return true;
                }
                String arg2 = args[2];
                Player maybeSubject = Bukkit.getPlayerExact(arg2);
                if (maybeSubject == null) {
                    maybeSubject = Bukkit.getPlayer(arg2);
                }
                if (maybeSubject != null && maybeSubject.isOnline()
                        && !isKnownSystem(arg2)) {
                    String system = args.length > 3 ? args[3] : "hub";
                    openLmInspectForUuid(player.getUniqueId(), maybeSubject.getUniqueId(), system, "main");
                    return true;
                }
                openSystemFromHub(player, arg2);
            }
            case "inspect", "view", "playergui" -> {
                if (!(sender instanceof Player admin)) {
                    sender.sendMessage("Players only for inspect.");
                    return true;
                }
                if (args.length < 3
                        || "clear".equalsIgnoreCase(args[2])
                        || "self".equalsIgnoreCase(args[2])
                        || "me".equalsIgnoreCase(args[2])) {
                    AdminInspectSessions.clear(admin.getUniqueId());
                    openHubInventory(admin, "main");
                    admin.sendMessage("§7Inspect closed — showing your own LM hub.");
                    return true;
                }
                String name = args[2];
                String system = args.length > 3 ? args[3] : "hub";
                // Allow: inspect <player> <system>  OR  inspect <player> with multi-word name ending in system
                if (args.length >= 4 && isKnownSystem(args[args.length - 1])) {
                    system = args[args.length - 1];
                    StringBuilder sb = new StringBuilder(args[2]);
                    for (int i = 3; i < args.length - 1; i++) {
                        sb.append(' ').append(args[i]);
                    }
                    name = sb.toString();
                }
                Player subject = Bukkit.getPlayerExact(name);
                if (subject == null) {
                    subject = Bukkit.getPlayer(name);
                }
                if (subject == null || !subject.isOnline()) {
                    admin.sendMessage("§cPlayer not online: §f" + name);
                    return true;
                }
                openLmInspectForUuid(admin.getUniqueId(), subject.getUniqueId(), system, "main");
            }
            case "testgui", "test-gui", "cnpcgui", "cnpc-test" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cRun /lm admin testgui in-game.");
                    return true;
                }
                if (!ForgeBridge.openStaffCnpcTestGui(player)) {
                    String err = ForgeBridge.lastError();
                    sender.sendMessage(
                            "§cCNPC test GUI failed"
                                    + (err == null || err.isBlank() ? "." : ": " + err));
                }
            }
            default -> {
                sender.sendMessage("§cUnknown: /lm admin " + sub);
                sendLmAdminHelp(sender);
            }
        }
        return true;
    }

    private static boolean isCharacterCooldownKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return switch (raw.toLowerCase(Locale.ROOT).trim()) {
            case "all", "race", "racechange", "class", "classchange", "reskin", "skin" -> true;
            default -> false;
        };
    }

    private static boolean isClearScope(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return switch (raw.toLowerCase(Locale.ROOT).trim()) {
            case "all", "lm", "*",
                    "rival", "rivals", "rivalry",
                    "spar", "sparring",
                    "difficulty", "diff", "ad",
                    "progression", "prog", "skills", "meditation" -> true;
            default -> false;
        };
    }

    private static boolean isKnownSystem(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return switch (raw.toLowerCase(Locale.ROOT).trim()) {
            case "hub", "lm", "legacymechanics", "main",
                    "difficulty", "diff", "ad",
                    "rival", "rivals", "rivalry",
                    "spar", "sparring",
                    "skillcheck", "skill_check",
                    "skills", "skill",
                    "prestige",
                    "character", "characterservices", "charservices", "char",
                    "android_remove", "androidremove", "remove_android", "deandroid",
                    "progression", "prog",
                    "admin", "logs", "syslog", "help" -> true;
            default -> false;
        };
    }

    private static void sendLmAdminHelp(CommandSender sender) {
        sender.sendMessage("§6§l/lm admin §8— Legacy Mechanics");
        sender.sendMessage("§e/lm admin help §7— this list");
        sender.sendMessage("§e/lm admin reload §7— Forge config + gui-tooltips.json");
        sender.sendMessage("§e/lm admin migrate-cnpc §7— import CNPC Rival/Spar (live → backup → world_data.json)");
        sender.sendMessage("§e/lm admin migrate-cnpc force §7— wipe LM Rival/Spar + re-import from those sources");
        sender.sendMessage("§8If CNPC was wiped: put world_data.json in config/legacymechanics/cnpc-import-backup/ then force");
        sender.sendMessage("§e/lm admin clear <player> [all|rival|spar|difficulty|progression]");
        sender.sendMessage("§e/lm admin character cooldown clear <player> [race|class|reskin|all]");
        sender.sendMessage("§8Offline OK for rival/spar; difficulty + progression NBT need the player online");
        sender.sendMessage("§e/lm admin syslog on|off|status|flush");
        sender.sendMessage("§e/lm admin stafffree on|off|toggle|status §7— staff skip Ancient Coin charges");
        sender.sendMessage("§8GUI: /lm → Progression → Ancient Coins — dye toggle");
        sender.sendMessage("§e/lm admin open <difficulty|rival|spar|character|progression|prestige|skills|hub>");
        sender.sendMessage("§e/lm admin inspect <player> [hub|difficulty|rival|spar|skillcheck|character|prestige|progression|skills]");
        sender.sendMessage("§e/lm admin inspect clear §7— stop inspecting");
        sender.sendMessage("§e/lm admin testgui §7— staff CNPC test hub (all LM systems)");
        sender.sendMessage("§e/padmin points <player> add|remove|set <n> §7— wallet for all prestige shops");
        sender.sendMessage("§8Also: /padmin points set <n>  ·  /padmin points <player> <n>");
        sender.sendMessage("§e/padmin addpoints|setpoints|removepoints <player> <n>");
        sender.sendMessage("§e/padmin info [player] §7— prestige admin (chat)");
        sender.sendMessage("§8Also: /difficulty admin gui|inspect <player>");
    }

    private boolean handleProgression(CommandSender sender, String[] args) {
        return progressionCommandTree.execute(sender, args);
    }

    private boolean handlePrestige(CommandSender sender, String[] args) {
        // Console: staff admin (points add/remove for all prestige shops, etc.).
        if (!(sender instanceof Player player)) {
            if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
                String help = ForgeBridge.prestigeAdmin(null, "help");
                sendMultilineToSender(sender, help);
                return true;
            }
            String sub = args[0].toLowerCase();
            if ("admin".equals(sub) || isPrestigeAdminSub(sub) || "help".equals(sub)) {
                String raw = "admin".equals(sub) ? joinArgs(args, 1) : joinArgs(args, 0);
                if (raw.isBlank()) {
                    raw = "help";
                }
                sendMultilineToSender(sender, ForgeBridge.prestigeAdmin(null, raw));
                return true;
            }
            sender.sendMessage("Players only for prestige GUI. Console: /padmin points <player> add|remove|set <n>");
            return true;
        }
        if (!canUsePlayerGui(player)) {
            player.sendMessage("§cNo permission: dmzdiff.gui");
            return true;
        }
        // Players: Prestige is GUI-only via /lm → Prestige (or /lmdo from CMI).
        if (!ForgeBridge.isStaff(player)) {
            player.sendMessage("§7Open §f/lm §7→ §6Prestige §7(GUI).");
            return true;
        }
        if (args.length == 0 || "gui".equalsIgnoreCase(args[0])) {
            openPrestigeRespectingConfig(player, "main");
            return true;
        }
        String sub = args[0].toLowerCase();
        // Never forward /prestige to Forge brigadier on Mohist — it fails with
        // "Forge command bridge failed". Route admin (and shorthand) via reflection.
        if ("admin".equals(sub) || isPrestigeAdminSub(sub) || "help".equals(sub)) {
            String raw = "admin".equals(sub) ? joinArgs(args, 1) : joinArgs(args, 0);
            if (raw.isBlank()) {
                raw = "help";
            }
            sendMultilineChat(player, ForgeBridge.prestigeAdmin(player, raw));
            return true;
        }
        if ("do".equals(sub)) {
            String action = args.length > 1 ? args[1] : "";
            String arg = args.length > 2 ? args[2] : "";
            String returnPage = args.length > 3 ? args[3] : null;
            String reopen;
            if ("page".equalsIgnoreCase(action) || "refresh".equalsIgnoreCase(action)) {
                reopen = arg == null || arg.isBlank() ? "main" : arg;
            } else {
                reopen = returnPage == null || returnPage.isBlank() ? "main" : returnPage;
                String msg = ForgeBridge.prestigeHandleDo(player, action, arg, reopen);
                if (msg != null && !msg.isBlank()) {
                    if (!msg.startsWith("§")) {
                        msg = "§a" + msg;
                    }
                    player.sendMessage(msg);
                }
            }
            openPrestigeInventory(player, reopen);
            return true;
        }
        sendMultilineChat(player, ForgeBridge.prestigeAdmin(player, "help"));
        return true;
    }

    private static boolean isPrestigeAdminSub(String sub) {
        return "info".equals(sub) || "held".equals(sub) || "completed".equals(sub)
                || "points".equals(sub) || "breakthroughs".equals(sub) || "breakthrough".equals(sub)
                || "cap".equals(sub)
                || "sync".equals(sub)
                || "addpoints".equals(sub) || "givepoints".equals(sub) || "grantpoints".equals(sub)
                || "removepoints".equals(sub) || "takepoints".equals(sub)
                || "setpoints".equals(sub)
                || "tier".equals(sub) || "tiers".equals(sub) || "difficulty".equals(sub)
                || "skill".equals(sub) || "skills".equals(sub)
                || "invest".equals(sub) || "invested".equals(sub);
    }

    private static void sendMultilineToSender(CommandSender sender, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String line : raw.split("\n")) {
            sender.sendMessage(line);
        }
    }

    private static String joinArgs(String[] args, int from) {
        if (args == null || from >= args.length) {
            return "";
        }
        StringBuilder raw = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (raw.length() > 0) {
                raw.append(' ');
            }
            raw.append(args[i]);
        }
        return raw.toString();
    }

    /** Staff slash results — always chat (never GUI feedback swallow). */
    private static void sendMultilineChat(Player player, String msg) {
        GuiChat.sendChatResult(player, msg);
    }

    private boolean handleSkills(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!ForgeBridge.isStaff(player)) {
            player.sendMessage("§cStaff only.");
            return true;
        }
        if (!canUsePlayerGui(player)) {
            player.sendMessage("§cNo permission: dmzdiff.gui");
            return true;
        }
        if (args.length == 0 || "gui".equalsIgnoreCase(args[0]) || "check".equalsIgnoreCase(args[0])) {
            openSkillsRespectingConfig(player, "core");
            return true;
        }
        String sub = args[0].toLowerCase();
        if ("do".equals(sub)) {
            String action = args.length > 1 ? args[1] : "";
            String arg = args.length > 2 ? args[2] : "";
            String returnPage = args.length > 3 ? args[3] : null;
            String reopen;
            if ("page".equalsIgnoreCase(action) || "refresh".equalsIgnoreCase(action)) {
                reopen = arg == null || arg.isBlank() ? "core" : arg;
            } else {
                reopen = returnPage == null || returnPage.isBlank() ? "core" : returnPage;
                String msg = ForgeBridge.skillsHandleDo(player, action, arg, reopen);
                if (msg != null && !msg.isBlank()) {
                    if (!msg.startsWith("§")) {
                        msg = "§a" + msg;
                    }
                    player.sendMessage(msg);
                }
            }
            openSkillsInventory(player, reopen);
            return true;
        }
        if ("core".equals(sub) || "advanced".equals(sub) || "saga".equals(sub) || "help".equals(sub)) {
            openSkillsRespectingConfig(player, sub);
            return true;
        }
        forwardToForge(player, "skills", args);
        return true;
    }

    private boolean handleSkillCheck(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!ForgeBridge.hasSkillCheck(player)) {
            player.sendMessage("§cSkill Check is for donators. Ask staff about donator access.");
            return true;
        }
        if (!canUsePlayerGui(player)) {
            player.sendMessage("§cNo permission: dmzdiff.gui");
            return true;
        }
        if (args.length == 0 || "gui".equalsIgnoreCase(args[0])) {
            openSkillsRespectingConfig(player, "core", true);
            return true;
        }
        String sub = args[0].toLowerCase();
        if ("do".equals(sub)) {
            String action = args.length > 1 ? args[1] : "";
            String arg = args.length > 2 ? args[2] : "";
            String reopen;
            if ("page".equalsIgnoreCase(action) || "refresh".equalsIgnoreCase(action)) {
                reopen = arg == null || arg.isBlank() ? "core" : arg;
            } else {
                reopen = "core";
            }
            openSkillsInventory(player, reopen, true);
            return true;
        }
        if ("core".equals(sub) || "advanced".equals(sub) || "saga".equals(sub)
                || "natural".equals(sub) || "help".equals(sub)) {
            openSkillsRespectingConfig(player, sub, true);
            return true;
        }
        forwardToForge(player, "skillcheck", args);
        return true;
    }

    private boolean handleRival(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!canUsePlayerGui(player)) {
            player.sendMessage("§cNo permission: dmzdiff.gui");
            return true;
        }
        if (args.length == 0 || "gui".equalsIgnoreCase(args[0])) {
            openRivalRespectingConfig(player, "main");
            return true;
        }
        String sub = args[0].toLowerCase();
        // Staff admin: bare → GUI; with args → ForgeBridge handleDo (not brigadier perm gate)
        if ("admin".equals(sub)) {
            if (!ForgeBridge.isStaff(player)) {
                player.sendMessage("§cStaff only.");
                return true;
            }
            if (args.length == 1 || "gui".equalsIgnoreCase(args[1])) {
                openRivalRespectingConfig(player, "admin");
                return true;
            }
            if ("open".equalsIgnoreCase(args[1])) {
                String page = args.length > 2 ? args[2] : "main";
                openRivalRespectingConfig(player, page);
                return true;
            }
            StringBuilder adminArg = new StringBuilder(args[1]);
            for (int i = 2; i < args.length; i++) {
                adminArg.append(' ').append(args[i]);
            }
            String msg = ForgeBridge.rivalHandleDo(player, "admin", adminArg.toString(), "admin");
            sendMultiline(player, msg);
            return true;
        }
        if ("do".equals(sub)) {
            String action = args.length > 1 ? args[1] : "";
            String arg = args.length > 2 ? args[2] : "";
            String returnPage = args.length > 3 ? args[3] : null;
            String reopen;
            if ("page".equalsIgnoreCase(action) || "refresh".equalsIgnoreCase(action)) {
                reopen = arg == null || arg.isBlank() ? "main" : arg;
            } else {
                reopen = returnPage == null || returnPage.isBlank() ? "main" : returnPage;
                String msg = ForgeBridge.rivalHandleDo(player, action, arg, reopen);
                sendMultiline(player, msg);
            }
            if ("chat".equals(ForgeBridge.guiBackend())) {
                ForgeBridge.openRivalChatMenu(player, reopen);
            } else {
                openRivalInventory(player, reopen);
            }
            return true;
        }
        // GUI pages (only when no extra forge args — e.g. /rival challenge send X forwards)
        if (args.length == 1 && switch (sub) {
            case "list", "stats", "top", "season", "quests", "achievements", "hof",
                 "journal", "title", "titles", "challenge", "help", "progress",
                 "actions", "history", "pending", "invites",
                 "pick_declare", "pick_accept", "pick_decline", "pick_remove", "pick_challenge",
                 "pick_spectate", "pick_silent", "pick_replace_mutual", "replace_mutual" -> true;
            default -> false;
        }) {
            String page = "titles".equals(sub) ? "title" : sub;
            openRivalRespectingConfig(player, page);
            return true;
        }
        // Known typed actions — route via ForgeBridge.handleDo (Mohist brigadier forward fails).
        if (isRivalForgeAction(sub)) {
            routeRivalTypedAction(player, args);
            return true;
        }
        // Bare /rival <onlinePlayer> → silent rival via handleDo
        if (args.length == 1 && Bukkit.getPlayerExact(args[0]) != null) {
            String msg = ForgeBridge.rivalHandleDo(player, "silent", args[0], "main");
            sendMultiline(player, msg);
            return true;
        }
        // Unknown non-admin chatter → open GUI instead of failing typed command
        openRivalRespectingConfig(player, "main");
        return true;
    }

    private static boolean isRivalForgeAction(String sub) {
        return switch (sub.toLowerCase()) {
            case "declare", "accept", "decline", "deny", "remove", "silent",
                 "challenge", "spectate", "tpmsg", "instinct",
                 "refresh", "save" -> true;
            default -> false;
        };
    }

    /**
     * Map typed {@code /rival …} args onto {@link ForgeBridge#rivalHandleDo} so Mohist does not
     * need the Forge brigadier command bridge.
     */
    private static void routeRivalTypedAction(Player player, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "declare", "accept", "decline", "deny", "remove", "silent" -> {
                String action = "deny".equals(sub) ? "decline" : sub;
                String arg = args.length > 1 ? args[1] : "";
                sendMultiline(player, ForgeBridge.rivalHandleDo(player, action, arg, "main"));
            }
            case "challenge" -> {
                if (args.length < 2) {
                    openRivalPage(player, "challenge");
                    return;
                }
                String csub = args[1].toLowerCase(Locale.ROOT);
                if ("send".equals(csub)) {
                    String target = args.length > 2 ? args[2] : "";
                    String arg = target;
                    if (args.length > 3 && !args[3].isBlank()) {
                        arg = target + "@" + args[3];
                    }
                    sendMultiline(player, ForgeBridge.rivalHandleDo(player, "challenge_send", arg, "main"));
                    return;
                }
                if ("accept".equals(csub) || "decline".equals(csub) || "deny".equals(csub) || "cancel".equals(csub)) {
                    String act = "deny".equals(csub) ? "decline" : csub;
                    sendMultiline(player, ForgeBridge.rivalHandleDo(player, "challenge", act, "main"));
                    return;
                }
                // /rival challenge <player> [minutes] → send
                String arg = args[1];
                if (args.length > 2 && !args[2].isBlank()) {
                    arg = args[1] + "@" + args[2];
                }
                sendMultiline(player, ForgeBridge.rivalHandleDo(player, "challenge_send", arg, "main"));
            }
            case "spectate" -> {
                String arg = args.length > 1 ? args[1] : "";
                if (args.length > 1 && ("stop".equalsIgnoreCase(args[1]) || "end".equalsIgnoreCase(args[1]))) {
                    sendMultiline(player, ForgeBridge.rivalHandleDo(player, "spectate_stop", "", "main"));
                } else {
                    sendMultiline(player, ForgeBridge.rivalHandleDo(player, "spectate", arg, "main"));
                }
            }
            case "tpmsg" -> {
                String arg = args.length > 1 ? args[1] : "toggle";
                sendMultiline(player, ForgeBridge.rivalHandleDo(player, "tpmsg", arg, "main"));
            }
            case "instinct" -> {
                sendMultiline(player, ForgeBridge.rivalHandleDo(player, "instinct", "", "main"));
            }
            case "refresh", "save" -> {
                if (!ForgeBridge.isStaff(player)) {
                    player.sendMessage("§cStaff only.");
                    return;
                }
                sendMultiline(player, ForgeBridge.rivalHandleDo(player, "admin", sub, "admin"));
            }
            default -> openRivalPage(player, "main");
        }
    }

    private static void openRivalPage(Player player, String page) {
        AdaptiveDifficultyGuiPlugin plugin = JavaPlugin.getPlugin(AdaptiveDifficultyGuiPlugin.class);
        if (plugin != null) {
            plugin.openRivalRespectingConfig(player, page);
        }
    }

    private static boolean isSparForgeAction(String sub) {
        return switch (sub.toLowerCase()) {
            case "mentor", "apprentice", "save" -> true;
            default -> false;
        };
    }

    /**
     * Player-facing GUI gate for {@code /difficulty}, {@code /lm}, {@code /rival}, {@code /spar}, etc.
     * <p>
     * Always allows online players. Mohist/LuckPerms often reports {@code dmzdiff.gui} as missing/false
     * even when {@code plugin.yml} defaults it to true, which wrongly blocked non-ops. Staff-only
     * commands still use {@link ForgeBridge#isStaff}.
     */
    public static boolean canUsePlayerGui(Player player) {
        return player != null;
    }

    private boolean handleSpar(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!canUsePlayerGui(player)) {
            player.sendMessage("§cNo permission: dmzdiff.gui");
            return true;
        }
        if (args.length == 0 || "gui".equalsIgnoreCase(args[0])) {
            openSparRespectingConfig(player, "main");
            return true;
        }
        String sub = args[0].toLowerCase();
        // Staff admin: bare → GUI; with args → ForgeBridge handleDo (not brigadier perm gate)
        if ("admin".equals(sub)) {
            if (!ForgeBridge.isStaff(player)) {
                player.sendMessage("§cStaff only.");
                return true;
            }
            if (args.length == 1 || "gui".equalsIgnoreCase(args[1])) {
                openSparRespectingConfig(player, "admin");
                return true;
            }
            StringBuilder adminArg = new StringBuilder(args[1]);
            for (int i = 2; i < args.length; i++) {
                adminArg.append(' ').append(args[i]);
            }
            String msg = ForgeBridge.sparHandleDo(player, "admin", adminArg.toString(), "admin");
            sendMultiline(player, msg);
            return true;
        }
        if ("do".equals(sub)) {
            String action = args.length > 1 ? args[1] : "";
            String arg = args.length > 2 ? args[2] : "";
            String returnPage = args.length > 3 ? args[3] : null;
            String reopen;
            if ("page".equalsIgnoreCase(action) || "refresh".equalsIgnoreCase(action)) {
                reopen = arg == null || arg.isBlank() ? "main" : arg;
            } else {
                reopen = returnPage == null || returnPage.isBlank() ? "main" : returnPage;
                String msg = ForgeBridge.sparHandleDo(player, action, arg, reopen);
                sendMultiline(player, msg);
            }
            if ("chat".equals(ForgeBridge.guiBackend())) {
                ForgeBridge.openSparChatMenu(player, reopen);
            } else {
                openSparInventory(player, reopen);
            }
            return true;
        }
        if ("stats".equals(sub) || "help".equals(sub)
                || ("mentor".equals(sub) && args.length == 1)
                || ("top".equals(sub))
                || "pending".equals(sub)
                || "invites".equals(sub)
                || "pick_apprentice".equals(sub)
                || "pick_mentor".equals(sub)
                || "pick_accept".equals(sub)
                || "pick_decline".equals(sub)) {
            String page = sub;
            if ("top".equals(sub) && args.length > 1) {
                page = "top_" + args[1].toLowerCase();
            }
            openSparRespectingConfig(player, page);
            return true;
        }
        if ("end".equals(sub)) {
            String msg = ForgeBridge.sparHandleDo(player, "end", "0", "main");
            if (msg != null && !msg.isBlank()) {
                if (!msg.startsWith("§")) {
                    msg = "§a" + msg;
                }
                player.sendMessage(msg);
            }
            openSparRespectingConfig(player, "main");
            return true;
        }
        // mentor <name> / apprentice <name> / save → handleDo (not brigadier)
        if (isSparForgeAction(sub)) {
            routeSparTypedAction(player, args);
            return true;
        }
        openSparRespectingConfig(player, "main");
        return true;
    }

    private static void routeSparTypedAction(Player player, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("save".equals(sub)) {
            if (!ForgeBridge.isStaff(player)) {
                player.sendMessage("§cStaff only.");
                return;
            }
            sendMultiline(player, ForgeBridge.sparHandleDo(player, "admin", "save", "admin"));
            return;
        }
        if ("mentor".equals(sub)) {
            if (args.length < 2) {
                return;
            }
            String msub = args[1].toLowerCase(Locale.ROOT);
            if ("accept".equals(msub) || "decline".equals(msub) || "deny".equals(msub)
                    || "cancel".equals(msub) || "leave".equals(msub) || "remove".equals(msub)
                    || "clear".equals(msub) || "release".equals(msub)) {
                String rest = args.length > 2 ? args[2] : "";
                String arg = msub + (rest.isBlank() ? "" : " " + rest);
                sendMultiline(player, ForgeBridge.sparHandleDo(player, "mentor", arg, "main"));
                return;
            }
            // /spar mentor <player> → invite as apprentice
            sendMultiline(player, ForgeBridge.sparHandleDo(player, "mentor_invite", args[1], "main"));
            return;
        }
        if ("apprentice".equals(sub)) {
            if (args.length < 2) {
                return;
            }
            String asub = args[1].toLowerCase(Locale.ROOT);
            if ("remove".equals(asub) || "release".equals(asub) || "clear".equals(asub)) {
                sendMultiline(player, ForgeBridge.sparHandleDo(player, "mentor_release", "", "main"));
                return;
            }
            sendMultiline(player, ForgeBridge.sparHandleDo(player, "apprentice_invite", args[1], "main"));
        }
    }

    /** Forward unknown subcommands to Forge brigadier via reflection (avoids Bukkit recursion). */
    private static void forwardToForge(Player player, String root, String[] args) {
        StringBuilder sb = new StringBuilder(root);
        for (String a : args) {
            sb.append(' ').append(a);
        }
        if (!ForgeBridge.forwardCommand(player, sb.toString())) {
            player.sendMessage("§cCould not run §f/" + sb + "§c (Forge command bridge failed).");
        }
    }

    private static void sendMultiline(Player player, String msg) {
        GuiChat.sendResult(player, msg);
    }

    private boolean handleDifficulty(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only. Use /difficulty admin … from console.");
                return true;
            }
            if (!canUsePlayerGui(player)) {
                player.sendMessage("§cNo permission: dmzdiff.gui");
                return true;
            }
            openMenuRespectingConfig(player, "main");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "do" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (!canUsePlayerGui(player)) {
                    player.sendMessage("§cNo permission: dmzdiff.gui");
                    return true;
                }
                String action = args.length > 1 ? args[1] : "";
                String arg = args.length > 2 ? args[2] : null;
                String returnPage = args.length > 3 ? args[3] : null;
                String reopen = ForgeBridge.resolveReturnPage(action, arg, returnPage);
                ForgeBridge.ActionResult result = ForgeBridge.handleActionResult(player, action, arg, reopen);
                if (result.message() != null && !result.message().isBlank()) {
                    String msg = result.message();
                    if (!msg.startsWith("§")) {
                        msg = (result.ok() ? "§a" : "§c") + msg;
                    }
                    player.sendMessage(msg);
                }
                // Bukkit owns reopen: inventory for cmi/chest/auto, chat menu for chat backend.
                if (reopen != null && !reopen.isBlank()) {
                    if ("chat".equals(ForgeBridge.guiBackend())) {
                        ForgeBridge.openChatMenu(player, reopen);
                    } else {
                        openInventory(player, reopen);
                    }
                }
                return true;
            }
            case "admin" -> {
                return handleAdmin(sender, args);
            }
            case "reset", "zero", "clear" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (!canUsePlayerGui(player)) {
                    player.sendMessage("§cNo permission: dmzdiff.gui");
                    return true;
                }
                ForgeBridge.ActionResult result = ForgeBridge.handleActionResult(player, "reset", "0", "main");
                if (result.message() != null && !result.message().isBlank()) {
                    String msg = result.message();
                    if (!msg.startsWith("§")) {
                        msg = (result.ok() ? "§a" : "§c") + msg;
                    }
                    player.sendMessage(msg);
                }
                if ("chat".equals(ForgeBridge.guiBackend())) {
                    ForgeBridge.openChatMenu(player, "main");
                } else {
                    openInventory(player, "main");
                }
                return true;
            }
            case "buy", "purchase", "unlock", "lower", "adjust", "tiers", "tier", "titles", "title",
                 "team", "teams", "stats", "details" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (!canUsePlayerGui(player)) {
                    player.sendMessage("§cNo permission: dmzdiff.gui");
                    return true;
                }
                String page = switch (sub) {
                    case "purchase", "unlock", "buy", "lower", "adjust", "tier" -> "tiers";
                    case "title" -> "titles";
                    case "teams" -> "team";
                    case "details" -> "stats";
                    default -> sub;
                };
                if (("stats".equals(page) || "details".equals(sub)) && !ForgeBridge.isStaff(player)) {
                    openMenuRespectingConfig(player, "main");
                    return true;
                }
                openMenuRespectingConfig(player, page);
                return true;
            }
            case "hard", "normal", "easy", "peaceful" -> {
                boolean staff = sender instanceof Player p
                        ? ForgeBridge.isStaff(p)
                        : sender.isOp() || sender.hasPermission(ForgeBridge.adminPermission());
                if (!staff) {
                    sender.sendMessage("§cOps only.");
                    return true;
                }
                boolean ok = ForgeBridge.setVanillaDifficulty(sub);
                sender.sendMessage(ok
                        ? "§aVanilla difficulty set to §f" + sub
                        : "§cFailed to set vanilla difficulty.");
                if (ok && "peaceful".equals(sub)) {
                    sender.sendMessage("§cHostile mobs will not spawn — adaptive scaling will not run.");
                }
                return true;
            }
            case "help", "?" -> {
                if (sender instanceof Player player && !ForgeBridge.isStaff(player)) {
                    // Normal players: open the clean GUI — no command dump.
                    openMenuRespectingConfig(player, "main");
                    return true;
                }
                sendStaffHelp(sender);
                return true;
            }
            default -> {
                // Unknown args: players just get the menu; staff get command help.
                if (sender instanceof Player player) {
                    if (ForgeBridge.isStaff(player)) {
                        sendStaffHelp(sender);
                    } else {
                        openMenuRespectingConfig(player, "main");
                    }
                    return true;
                }
                sendStaffHelp(sender);
                return true;
            }
        }
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        boolean console = !(sender instanceof Player);
        Player player = console ? null : (Player) sender;

        if (!console && !ForgeBridge.isStaff(player)) {
            // No command-syntax dump for normal players.
            sender.sendMessage("§cStaff only.");
            return true;
        }

        // Bare /difficulty admin shows staff help.
        if (args.length == 1) {
            sendAdminHelp(sender);
            return true;
        }

        String sub = args[1].toLowerCase();

        // Master switch + whitelist.
        if (isDirectStaffSubcommand(sub)) {
            return handleDirectStaff(sender, args, sub);
        }

        switch (sub) {
            case "help" -> {
                sendAdminHelp(sender);
                return true;
            }
            case "resetpurchased" -> {
                if (player == null) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                sender.sendMessage(ForgeBridge.resetPurchased(player));
                return true;
            }
            case "characterreset" -> {
                if (player == null) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                ForgeBridge.ActionResult result =
                        ForgeBridge.handleActionResult(player, "character_reset", "0", "");
                String msg = result.message();
                if (msg == null || msg.isBlank()) {
                    msg = result.ok() ? "Character difficulty reset applied." : "Character reset failed.";
                }
                if (!msg.startsWith("§")) {
                    msg = (result.ok() ? "§a" : "§c") + msg;
                }
                sender.sendMessage(msg);
                return true;
            }
            case "reload" -> {
                if (ForgeBridge.reloadConfig()) {
                    sender.sendMessage("§aAdaptive difficulty config reloaded.");
                } else {
                    String err = ForgeBridge.lastError();
                    sender.sendMessage("§cConfig reload failed"
                            + (err == null || err.isBlank() ? "." : ": " + err));
                }
                return true;
            }
            case "settings" -> {
                if (player == null) {
                    sender.sendMessage("Players only for settings page.");
                    return true;
                }
                if (!ForgeBridge.openChatMenu(player, "settings")) {
                    openMenuRespectingConfig(player, "main");
                }
                sender.sendMessage("§7Use §f/difficulty admin set <key> <value>");
                return true;
            }
            case "area" -> {
                if (player == null) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                sender.sendMessage(ForgeBridge.areaDifficultyText(player));
                return true;
            }
            case "gamedifficulty" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /difficulty admin gamedifficulty <peaceful|easy|normal|hard>");
                    return true;
                }
                boolean ok = ForgeBridge.setVanillaDifficulty(args[2]);
                sender.sendMessage(ok
                        ? "§aVanilla difficulty set to §f" + args[2]
                        : "§cFailed to set vanilla difficulty.");
                return true;
            }
            case "gui", "inspect", "view", "playergui" -> {
                return handleAdminGui(sender, args);
            }
            case "syslog", "systemlog" -> {
                String mode = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "status";
                sender.sendMessage(ForgeBridge.syslogCommand(mode));
                return true;
            }
            case "resynclevel", "resync", "levelresync" -> {
                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                    if (target == null) {
                        target = Bukkit.getPlayer(args[2]);
                    }
                    if (target == null || !target.isOnline()) {
                        sender.sendMessage("§cPlayer not online: §f" + args[2]);
                        return true;
                    }
                } else if (sender instanceof Player self) {
                    target = self;
                } else {
                    sender.sendMessage("§cUsage: /difficulty admin resynclevel <player>");
                    return true;
                }
                sender.sendMessage(ForgeBridge.resyncLevel(target));
                return true;
            }
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /difficulty admin set <key> <value>");
                    return true;
                }
                String key = args[2];
                StringBuilder sb = new StringBuilder(args[3]);
                for (int i = 4; i < args.length; i++) {
                    sb.append(' ').append(args[i]);
                }
                sender.sendMessage(ForgeBridge.adminSet(key, sb.toString()));
                return true;
            }
            default -> {
                sender.sendMessage("§cUnknown admin subcommand. Try §f/difficulty admin help");
                return true;
            }
        }
    }

    private static boolean isDirectStaffSubcommand(String sub) {
        return switch (sub) {
            case "off", "disable", "on", "enable", "toggle", "status",
                 "whitelist", "wl",
                 "telemetry", "tel", "balancelog", "combatlog",
                 "stafffree", "staffcoins", "staffcoinsfree", "freecoins" -> true;
            default -> false;
        };
    }

    private boolean handleDirectStaff(CommandSender sender, String[] args, String sub) {
        switch (sub) {
            case "off", "disable" -> {
                sender.sendMessage(ForgeBridge.setSystemEnabled(false));
                return true;
            }
            case "on", "enable" -> {
                sender.sendMessage(ForgeBridge.setSystemEnabled(true));
                return true;
            }
            case "toggle" -> {
                sender.sendMessage(ForgeBridge.setSystemEnabled(!ForgeBridge.systemEnabled()));
                return true;
            }
            case "status" -> {
                sender.sendMessage(ForgeBridge.systemStatusText());
                return true;
            }
            case "whitelist", "wl" -> {
                return handleWhitelist(sender, args);
            }
            case "telemetry", "tel", "balancelog", "combatlog" -> {
                return handleTelemetry(sender, args);
            }
            case "stafffree", "staffcoins", "staffcoinsfree", "freecoins" -> {
                return handleStaffFreeCoins(sender, args);
            }
            default -> {
                return false;
            }
        }
    }

    private boolean handleStaffFreeCoins(CommandSender sender, String[] args) {
        if (args.length == 2) {
            sender.sendMessage(ForgeBridge.staffFreeAncientCoinCostsStatusText());
            return true;
        }
        String op = args[2].toLowerCase(Locale.ROOT);
        switch (op) {
            case "on", "enable" -> {
                sender.sendMessage(ForgeBridge.setStaffFreeAncientCoinCosts(true));
                return true;
            }
            case "off", "disable" -> {
                sender.sendMessage(ForgeBridge.setStaffFreeAncientCoinCosts(false));
                return true;
            }
            case "toggle" -> {
                sender.sendMessage(ForgeBridge.setStaffFreeAncientCoinCosts(
                        !ForgeBridge.staffFreeAncientCoinCosts()));
                return true;
            }
            case "status" -> {
                sender.sendMessage(ForgeBridge.staffFreeAncientCoinCostsStatusText());
                return true;
            }
            default -> {
                sender.sendMessage("§cUsage: /difficulty admin stafffree on|off|toggle|status");
                return true;
            }
        }
    }

    private boolean handleWhitelist(CommandSender sender, String[] args) {
        // /difficulty admin whitelist
        if (args.length == 2) {
            sender.sendMessage(ForgeBridge.whitelistStatusText());
            return true;
        }
        String op = args[2].toLowerCase();
        switch (op) {
            case "on", "enable" -> {
                sender.sendMessage(ForgeBridge.setWhitelistEnabled(true));
                return true;
            }
            case "off", "disable" -> {
                sender.sendMessage(ForgeBridge.setWhitelistEnabled(false));
                return true;
            }
            case "toggle" -> {
                sender.sendMessage(ForgeBridge.setWhitelistEnabled(!ForgeBridge.whitelistEnabled()));
                return true;
            }
            case "status" -> {
                sender.sendMessage(ForgeBridge.whitelistStatusText());
                return true;
            }
            case "list" -> {
                sender.sendMessage(ForgeBridge.whitelistListText());
                return true;
            }
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /difficulty admin whitelist add <player>");
                    return true;
                }
                sender.sendMessage(ForgeBridge.whitelistAdd(args[3]));
                return true;
            }
            case "remove", "rm", "del" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /difficulty admin whitelist remove <player|uuid>");
                    return true;
                }
                StringBuilder name = new StringBuilder(args[3]);
                for (int i = 4; i < args.length; i++) {
                    name.append(' ').append(args[i]);
                }
                sender.sendMessage(ForgeBridge.whitelistRemove(name.toString()));
                return true;
            }
            case "clear" -> {
                sender.sendMessage(ForgeBridge.whitelistClear());
                return true;
            }
            default -> {
                sender.sendMessage("§cUsage: /difficulty admin whitelist on|off|add|remove|list|clear");
                return true;
            }
        }
    }

    private boolean handleTelemetry(CommandSender sender, String[] args) {
        // /difficulty admin telemetry
        if (args.length == 2) {
            sender.sendMessage(ForgeBridge.telemetryStatusText());
            return true;
        }
        String op = args[2].toLowerCase();
        switch (op) {
            case "on", "enable" -> {
                sender.sendMessage(ForgeBridge.setTelemetryEnabled(true));
                return true;
            }
            case "off", "disable" -> {
                sender.sendMessage(ForgeBridge.setTelemetryEnabled(false));
                return true;
            }
            case "toggle" -> {
                sender.sendMessage(ForgeBridge.setTelemetryEnabled(!ForgeBridge.telemetryEnabled()));
                return true;
            }
            case "status" -> {
                sender.sendMessage(ForgeBridge.telemetryStatusText());
                return true;
            }
            case "flush" -> {
                sender.sendMessage(ForgeBridge.telemetryFlush());
                return true;
            }
            case "test", "probe" -> {
                String name = sender instanceof Player p ? p.getName() : "console";
                sender.sendMessage(ForgeBridge.telemetryTest(name));
                return true;
            }
            default -> {
                sender.sendMessage("§cUsage: /difficulty admin telemetry on|off|status|flush|test");
                return true;
            }
        }
    }


    private boolean handleAdminGui(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage("Players only (open inspect from in-game).");
            return true;
        }
        if (args.length < 3
                || "clear".equalsIgnoreCase(args[2])
                || "self".equalsIgnoreCase(args[2])
                || "me".equalsIgnoreCase(args[2])) {
            AdminInspectSessions.clear(admin.getUniqueId());
            openMenuRespectingConfig(admin, "main");
            admin.sendMessage("§7Inspect closed — showing your own GUI.");
            return true;
        }
        String page = "main";
        String name = args[2];
        if (args.length >= 4
                && ("main".equalsIgnoreCase(args[3])
                || "buy".equalsIgnoreCase(args[3])
                || "lower".equalsIgnoreCase(args[3])
                || "titles".equalsIgnoreCase(args[3])
                || "stats".equalsIgnoreCase(args[3])
                || "details".equalsIgnoreCase(args[3]))) {
            page = "details".equalsIgnoreCase(args[3]) ? "stats" : args[3].toLowerCase();
        } else if (args.length >= 4) {
            StringBuilder sb = new StringBuilder(args[2]);
            int last = args.length - 1;
            String maybePage = args[last].toLowerCase();
            boolean hasPage = switch (maybePage) {
                case "main", "buy", "lower", "titles", "stats", "details" -> true;
                default -> false;
            };
            int end = hasPage ? last : args.length;
            for (int i = 3; i < end; i++) {
                sb.append(' ').append(args[i]);
            }
            name = sb.toString();
            if (hasPage) {
                page = "details".equals(maybePage) ? "stats" : maybePage;
            }
        }
        Player subject = Bukkit.getPlayerExact(name);
        if (subject == null) {
            subject = Bukkit.getPlayer(name);
        }
        if (subject == null || !subject.isOnline()) {
            admin.sendMessage("§cPlayer not online: §f" + name);
            return true;
        }
        openInspect(admin, subject, page);
        return true;
    }

    private static void sendAdminHelp(CommandSender sender) {
        sendStaffHelp(sender);
    }

    /** Full command reference — staff / console only. */
    private static void sendStaffHelp(CommandSender sender) {
        sender.sendMessage("§6Adaptive Difficulty — staff");
        sender.sendMessage("§e/difficulty §7— open GUI");
        sender.sendMessage("§e/difficulty buy|lower|titles|details §7— open those pages");
        sender.sendMessage("§e/difficulty reset §7— clear active tier");
        sender.sendMessage("§e/difficulty admin off|on|toggle|status §7— master system switch");
        sender.sendMessage("§e/difficulty admin whitelist on|off|add|remove|list|clear §7— testing whitelist");
        sender.sendMessage("§e/difficulty admin telemetry on|off|status|flush|test §7— AD hit logs (all players)");
        sender.sendMessage("§e/difficulty admin stafffree on|off|toggle|status §7— staff skip Ancient Coin charges");
        sender.sendMessage("§e/difficulty admin syslog on|off|status|flush §7— unified system event log");
        sender.sendMessage("§e/difficulty admin resynclevel [player] §7— clear stuck DMZ level sample");
        sender.sendMessage("§e/difficulty admin gui|inspect <player> [page] §7— open their GUI (edit/see their state)");
        sender.sendMessage("§e/difficulty admin gui clear §7— stop inspecting");
        sender.sendMessage("§e/difficulty admin reload|settings|area|set §7— config tools");
        sender.sendMessage("§e/difficulty hard|normal|easy|peaceful §7— vanilla difficulty");
        sender.sendMessage("§8Master keys: enabled · whitelistEnabled · staffFreeAncientCoinCosts · balanceTelemetryEnabled");
    }
}
