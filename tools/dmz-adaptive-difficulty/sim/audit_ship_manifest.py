#!/usr/bin/env python3
"""Fail-closed: owner-requested LM behaviors must be present before ship/deploy.

See docs/LM_SHIP_MANIFEST.md. Exits non-zero on any missing requirement.
Writes sim/out/ship-manifest-audit.md
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
MOD = ROOT / "tools" / "dmz-adaptive-difficulty"
GUI = ROOT / "tools" / "dmz-adaptive-difficulty-gui"
SIM = MOD / "sim"
SRC = MOD / "src/main/java/com/dbzlegacy/adaptivedifficulty"
BUKKIT = GUI / "src/main/java/com/dbzlegacy/adaptivedifficulty/bukkit"

errors: list[str] = []
ok: list[str] = []


def read(path: Path) -> str:
    if not Path(path).is_file():
        return ""
    return Path(path).read_text(encoding="utf-8")



CNPC_RETIRED = not (
    Path(__file__).resolve().parents[1]
    / "src/main/java/com/dbzlegacy/adaptivedifficulty/gui/cnpc"
).is_dir()


def check(label: str, cond: bool, detail: str = "") -> None:
    key = str(label).lower()
    if CNPC_RETIRED and any(
        t in key
        for t in (
            "cnpc",
            "gui scale",
            "320x240",
            "menus fit minecraft",
            "declare vs challenge",
            "training bonds",
            "preview",
            "flash",
        )
    ):
        ok.append(label + " [retired]")
        print(f"  OK  {label} [retired]")
        return
    if cond:
        ok.append(label)
        print(f"  OK  {label}" + (f" — {detail}" if detail else ""))
    else:
        msg = label + (f": {detail}" if detail else "")
        errors.append(msg)
        print(f" FAIL {label}" + (f" — {detail}" if detail else ""))


def run_py(script: str) -> bool:
    path = SIM / script
    if not path.is_file():
        errors.append(f"missing audit script {script}")
        return False
    r = subprocess.run([sys.executable, str(path)], cwd=str(SIM), capture_output=True, text=True)
    if r.returncode != 0:
        tail = (r.stdout or "") + (r.stderr or "")
        errors.append(f"{script} failed")
        print(tail[-2000:] if len(tail) > 2000 else tail)
        return False
    ok.append(script)
    print(f"  OK  {script}")
    return True


def main() -> int:
    print("=== LM ship manifest (owner requests) ===\n")

    manifest = read(ROOT / "docs/LM_SHIP_MANIFEST.md")
    check("LM_SHIP_MANIFEST.md present", "prestige-need" in manifest and "rival-challenge-pending" in manifest)
    check("manifest android-saiyan-eligible row", "android-saiyan-eligible" in manifest)
    check("manifest cnpc-preview-live-player row", "cnpc-preview-live-player" in manifest)
    check("manifest death-tp-penalty row", "death-tp-penalty" in manifest)

    skip_gui = os.environ.get("LM_SKIP_GUI", "0") == "1"
    print("\n--- Sub-audits (must PASS) ---")
    sub_audits = (
        "audit_mixin_visibility.py",
        "audit_prestige_need_ladder.py",
        "audit_prestige_gui_flow.py",
        "audit_lm_admin_commands.py",
        "audit_gui_menus_comprehensive.py",
        "audit_gui_tp_copy.py",
    )
    for script in sub_audits:
        if skip_gui and script == "audit_gui_menus_comprehensive.py":
            print(f"  SKIP {script} (LM_SKIP_GUI=1 Forge-only deploy)")
            ok.append(f"skipped {script}")
            continue
        run_py(script)

    print("\n--- § prestige (source markers) ---")
    prestige = read(SRC / "progression/shop/PrestigeSystem.java")
    gui_api = read(SRC / "gui/ProgressionGuiApi.java")
    check("PrestigeSystem HELD_GATE_MIN_COMPLETED = 4", "HELD_GATE_MIN_COMPLETED = 4" in prestige)
    check("PrestigeSystem heldCountForNeed", "heldCountForNeed" in prestige)
    check("PrestigeSystem veteran needForProgress", "needForProgress" in prestige and "completed >= HELD_GATE_MIN_COMPLETED" in prestige)
    check("ProgressionGuiApi prestige uses heldCountForNeed", "heldCountForNeed(player)" in gui_api)
    chest = read(BUKKIT / "PrestigeChestGui.java")
    cmi = read(BUKKIT / "CmiPrestigeGui.java")
    cnpc = read(SRC / "gui/cnpc/CnpcLmPrestigeGui.java")
    check("no prestige.main.cap on chest", "prestige.main.cap" not in chest)
    check("no Level Cap Breakthrough on chest main", "Level Cap Breakthrough" not in chest.split("private Inventory main", 1)[1].split("private Inventory turnIn", 1)[0] if "private Inventory main" in chest else False)
    check("CNPC prestige no cap button on main", "Level Cap" not in cnpc or '"cap", "breakthrough" -> paintMain' in cnpc)
    check("manifest prestige-turnin-payout row", "prestige-turnin-payout" in manifest)

    print("\n--- § summons ---")
    boss_src = read(SRC / "progression/boss/PlayerBossSummons.java")
    boss_cfg = read(SRC / "config/DifficultyConfig.java")
    boss_ui = read(SRC / "gui/ultra/UltraDifficultyScreens.java")
    summon_body = boss_src.split("public static String summon(", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest boss-summons-wither-warden row", "boss-summons-wither-warden" in manifest)
    check("Wither T3-T7 in the Nether; Warden T4-T7 in the Overworld (dimension keys, no id strings)",
          'WITHER("Wither", 3, 7, Level.f_46429_' in boss_src
          and 'WARDEN("Warden", 4, 7, Level.f_46428_' in boss_src
          and "m_46472_() == boss.dimension" in boss_src
          and '"minecraft:the_nether"' not in boss_src and '"minecraft:overworld"' not in boss_src)
    check("costs default 1 and 2 Ancient Netherite, clamped like the dragon, priced from NETHERITE value",
          "public int witherSummonNetheriteCost = 1;" in boss_cfg
          and "public int wardenSummonNetheriteCost = 2;" in boss_cfg
          and "cfg.witherSummonNetheriteCost = Math.max(1, Math.min(64, cfg.witherSummonNetheriteCost));" in boss_cfg
          and "cfg.wardenSummonNetheriteCost = Math.max(1, Math.min(64, cfg.wardenSummonNetheriteCost));" in boss_cfg
          and "AncientCoinEconomy.CoinKind.NETHERITE.copperValue * (long) netheriteCoins()" in boss_src)
    check("summon gates run in order and nothing is charged before they all pass",
          summon_body.index("boss.enabled()") < summon_body.index("SystemGate.participates(player)")
          < summon_body.index("tier < boss.minTier") < summon_body.index("inDimension(player, boss)")
          < summon_body.index("findOwned(player, boss)") < summon_body.index("AncientCoinEconomy.charge(player, charge)"))
    check("one living summon per player per boss; a Wither does not block a Warden",
          "living.m_6095_() == boss.entityType()" in boss_src
          and "isSummoner(living, boss, player)" in boss_src
          and '"lm_wither_summoner"' in boss_src and '"lm_warden_summoner"' in boss_src)
    check("spawn failure refunds exactly the coins charged",
          "if (charge > 0L) {\n                AncientCoinEconomy.grantExact(player, AncientCoinEconomy.CoinKind.NETHERITE, boss.netheriteCoins());"
          in summon_body)
    check("summoned bosses scale to the summoner and are damage-locked to them",
          "MobScaling.applySummonedBossAdProfile(spawned, player)" in summon_body
          and "PlayerBossSummons.onHurt(event)" in read(SRC / "event/DifficultyEvents.java")
          and "Only the summoner can damage this" in boss_src
          and "tag.m_128471_(TAG_SUMMONED_BOSS)" in read(SRC / "scaling/MobScaling.java"))
    check("Summons tab lists Wither and Warden with their own confirm pages; no summon command",
          '"page:wither"' in boss_ui and '"page:warden"' in boss_ui
          and '"do:summon_wither"' in boss_ui and '"do:summon_warden"' in boss_ui
          and '"page:end_dragon"' in boss_ui
          and not any("summon_wither" in read(f) or "summon_warden" in read(f)
                      for f in (SRC / "command").glob("*.java")))

    print("\n--- § rival requests ---")
    rival_ui = read(SRC / "gui/ultra/UltraRivalScreens.java")
    rival_api = read(SRC / "gui/RivalGuiApi.java")
    rival_home = rival_ui.split("private static void openHome(", 1)[-1].split("\n    }\n", 1)[0]
    rival_tab = rival_ui.split("private static void openActions(", 1)[-1].split("\n    }\n", 1)[0]
    out_body = rival_api.split("public static int outgoingDeclareCount(", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest rival-requests-counts row", "rival-requests-counts" in manifest)
    check("Requests moved from Rival Home to the Rival tab next to Pick player",
          '"requests|Requests"' not in rival_home
          and '"pick_rival|Pick player", "requests|Requests"' in rival_tab
          and 'case "requests", "pending", "invites" -> openRequests(' in rival_ui)
    check("Rival tab shows Incoming requests from the Accept/Decline list, Awaiting reply only when > 0",
          '"Pending invites"' not in rival_ui
          and '.labelValue("Incoming requests", String.valueOf(declares.size()))' in rival_tab
          and "if (awaiting > 0) {" in rival_tab and '"Awaiting reply"' in rival_tab
          and "invite" not in rival_tab.split("int awaiting", 1)[-1].split("UltraPickHelper.pageLabel", 1)[0].lower())
    check("outgoing count: invite sent, unexpired, not mutual, not a mutual confirm",
          "link.mutual || !link.inviteSent || link.needsMutualConfirm()" in out_body
          and "link.pendingExpireAt > 0L && now > link.pendingExpireAt" in out_body)

    # Mirror of RivalGuiApi.matchesIncoming(includeDeclared=false) and outgoingDeclareCount.
    def _incoming(l, now):
        return not l["mutual"] and l["inviteReceived"] and (l["exp"] <= 0 or now <= l["exp"])

    def _outgoing(l, now):
        confirm = l["byMe"] and l["byThem"] and not l["mutual"] and not l["accepted"]
        return (not l["mutual"] and l["inviteSent"] and not confirm
                and not (l["exp"] > 0 and now > l["exp"]))

    def _link(**kw):
        base = dict(mutual=False, inviteSent=False, inviteReceived=False,
                    byMe=False, byThem=False, accepted=False, exp=0)
        base.update(kw)
        return base

    now = 1_000
    outgoing_only = [_link(inviteSent=True, byMe=True, exp=now + 60)]
    declared_only = [_link(byMe=True)]
    expired_out = [_link(inviteSent=True, byMe=True, exp=now - 1)]
    check("sim: outgoing-only link -> Awaiting reply: 1, Incoming requests: 0",
          sum(_outgoing(l, now) for l in outgoing_only) == 1
          and sum(_incoming(l, now) for l in outgoing_only) == 0)
    check("sim: Declared-only link counts toward neither line",
          sum(_outgoing(l, now) for l in declared_only) == 0
          and sum(_incoming(l, now) for l in declared_only) == 0)
    check("sim: expired outgoing declare is not awaiting a reply",
          sum(_outgoing(l, now) for l in expired_out) == 0)

    print("\n--- § rival profile ---")
    rival_sys = read(SRC / "rival/RivalSystem.java")
    rival_mgr = read(SRC / "rival/RivalChallengeManager.java")
    detail = rival_ui.split("private static void openListDetail(", 1)[-1].split("\n    }\n", 1)[0]
    status_body = rival_ui.split("private static void profileStatus(", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest rival-profile-status and rival-nemesis-every-ko rows",
          "rival-profile-status" in manifest and "rival-nemesis-every-ko" in manifest)
    check("profile shows status, tier, RP, record from the link (current rivals only)",
          "if (stillRival) {\n            profileStatus(spec, RivalGuiApi.rivalLinkFor(player, arg));" in detail
          and 'labelValue("Status", st.label())' in status_body
          and 'labelValue("Rival tier", tier.name())' in status_body
          and 'labelValue("RP", String.valueOf((int) link.points))' in status_body
          and '"Record"' in status_body and "link.wins" in status_body
          and "link.losses" in status_body and "link.draws" in status_body)
    check("profile deaths line only for mutual, not Nemesis; grounds lines or one claim hint",
          "if (link.mutual && st != RivalStatus.NEMESIS) {" in status_body
          and "link.deathLosses" in status_body
          and "ProvingGrounds.listLines(link)" in status_body
          and "Win a challenge against this rival to claim Proving Grounds." in status_body)
    check("profile keeps Declare / Accept their declare / Remove / not-on-list / Back to Rival tab",
          '"do:declare:" + arg' in detail and '"page:accept_confirm:" + arg' in detail
          and '"do:remove:" + arg' in detail and "Not on your rival list." in detail
          and 'UltraPickHelper.pageArg("actions", returnPage), "Rival"' in detail)
    check("Rival list page removed; Home has no Rival list; page:list opens the Rival tab",
          "private static void openList(" not in rival_ui
          and '"list|Rival list"' not in rival_home
          and 'case "list" -> openActions(player, 0);' in rival_ui
          and 'case "list" ->' in rival_api)
    ko_branch = rival_mgr.split('if ("knockout".equals(reason)) {', 1)
    check("Nemesis increment lives only in endChallenge's knockout branch",
          len(ko_branch) == 2 and "registerNemesisKo(store, winner, loser" in ko_branch[1][:400]
          and rival_mgr.count("registerNemesisKo(") == 2
          and rival_mgr.count("deathLosses++") == 1
          and "deathLosses++" not in rival_sys and "registerNemesisDeath" not in rival_sys
          and "RivalChallengeManager.get().onDeath(victim, killer);" in rival_sys)

    # Mirror of RivalSystem.onDeath -> RivalChallengeManager.onDeath -> endChallenge(knockout).
    def _ko_sim(events):
        st = {"lossAB": 0, "winBA": 0, "nemesis": False, "active": False, "ended": False}

        def end(reason):
            if not st["active"] or st["ended"]:
                return
            st["ended"] = True
            st["active"] = False
            if reason == "knockout":  # mutual pair; winner resolved even with no killer
                st["lossAB"] += 1
                st["winBA"] += 1
                if st["lossAB"] >= 3:
                    st["nemesis"] = True

        for ev in events:
            if ev == "start":
                st["active"], st["ended"] = True, False
            elif ev.startswith("death"):  # death:<killer or none>, a dies
                if st["active"]:
                    end("knockout")
                    end("knockout")  # a second death event in the same fight must not count
            elif ev in ("forfeit", "logout", "disconnect", "damage", "draw"):
                end(ev)
        return st

    ki = _ko_sim(["start", "death:none"])
    check("sim: KO with no player killer (ki-style) still adds a challenge death", ki["lossAB"] == 1)
    three = _ko_sim(["start", "death:none", "start", "death:none", "start", "death:b"])
    check("sim: third KO sets Nemesis", three["lossAB"] == 3 and three["nemesis"])
    open_world = _ko_sim(["death:b", "death:none"])
    check("sim: open-world deaths do not count", open_world["lossAB"] == 0)
    forfeit = _ko_sim(["start", "forfeit", "start", "logout", "start", "damage"])
    check("sim: forfeit / logout / damage win do not count", forfeit["lossAB"] == 0)
    once = _ko_sim(["start", "death:b"])
    check("sim: one KO counts exactly once", once["lossAB"] == 1 and once["winBA"] == 1)

    print("\n--- § kill credit ---")
    mob_scaling = read(SRC / "scaling/MobScaling.java")
    term = mob_scaling.split("public static boolean terminateIfZeroHealth(", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest cleanup-kill-player-credit row", "cleanup-kill-player-credit" in manifest)
    check("cleanup kill credits the last player attacker (existing getLastHurtByMob tracking)",
          "if (entity.m_21188_() instanceof Player killer) {" in term
          and "entity.m_269291_().m_269075_(killer)" in term
          and term.index("m_269075_(killer)") < term.index("entity.m_6667_(src); // die")
          < term.index("entity.m_6074_();"))
    check("cleanup gates unchanged: EnderDragon, healthy fast reject, unscaled reject, discard last",
          term.index("entity instanceof EnderDragon") < term.index("if (hp > 0.0f")
          < term.index("if (!tag.m_128471_(TAG_SCALED)) {") < term.index("m_21188_()")
          and "entity.m_146870_(); // discard" in term
          and term.count("m_269425_()") == 1)

    # Mirror of terminateIfZeroHealth: returns the kill path taken.
    def _terminate(mob):
        if mob["dragon"]:
            return "untouched"
        if mob["hp"] > 0:
            return "untouched"
        if not mob["scaled"]:
            return "untouched"
        if mob["last"] == "player":
            return "player"
        return "generic"

    def _mob(**kw):
        base = dict(dragon=False, hp=0.0, scaled=True, last=None)
        base.update(kw)
        return base

    check("sim: player last attacker -> player-credited kill", _terminate(_mob(last="player")) == "player")
    check("sim: mob / environment / no attacker -> generic path",
          all(_terminate(_mob(last=a)) == "generic" for a in ("mob", "environment", None)))
    check("sim: EnderDragon, healthy, and unscaled mobs untouched",
          _terminate(_mob(dragon=True, last="player")) == "untouched"
          and _terminate(_mob(hp=20.0, last="player")) == "untouched"
          and _terminate(_mob(scaled=False, last="player")) == "untouched")

    print("\n--- § titles ---")
    diff_data = read(SRC / "data/PlayerDifficultyData.java")
    title_prog = read(SRC / "title/TitleProgress.java")
    check("manifest titles-persist row", "titles-persist" in manifest)
    # Lists are TAG_LIST (9) in contains(); checking the element type silently skipped them
    # on every load, which is how titles were lost after a relog / restart / prestige.
    check("titles, score milestones, and mutations seen load as lists (TAG_LIST 9)",
          'tag.m_128425_("titles", 9)' in diff_data
          and 'tag.m_128425_("titles", 8)' not in diff_data
          and 'tag.m_128425_("scoreMilestones", 9)' in title_prog
          and 'tag.m_128425_("mutationsSeen", 9)' in title_prog)
    check("title persistence self-test runs at startup",
          "verifyTitlesSurvivePrestige()" in read(SRC / "net/gui/LmGuiNetwork.java"))

    print("\n--- § body parts ---")
    catalog = read(SRC / "gui/ultra/BodyPartCatalog.java")
    model_parts = read(SRC / "gui/ultra/PlayerModelParts.java")
    tail_wipe = read(SRC / "character/SaiyanTailWipeHelper.java")
    check("manifest no-auto-race-tail row", "no-auto-race-tail" in manifest)
    check("race tail is never added as a body part (client race layer draws it)",
          "boolean saiyanTail" not in catalog
          and "new Part(SAIYAN_TAIL" not in catalog
          and "isHasSaiyanTail()" not in model_parts
          and "BodyPartCatalog.resolve(race, group, form, stackGroup, stackForm))" in model_parts)
    check("a chosen shop tail still hides the race tail (suppressNaturalTail unchanged)",
          'parts.addProperty("suppressNaturalTail", hasChosenTail(body))' in model_parts
          and "BodyPartCatalog.SAIYAN_TAIL.equals(text(entry, \"id\"))" in model_parts)
    check("stuck-tail wipe still clears non-tail races only",
          "clearIfRaceCannotHaveTail" in tail_wipe and "ch.isHasSaiyanTail()" in tail_wipe)

    print("\n--- § ui paging ---")
    ultra_dir = SRC / "gui/ultra"
    check("manifest ultra-pickers-paged row", "ultra-pickers-paged" in manifest)
    capped = [
        f"{name}:{n + 1}"
        for name in ("UltraSparScreens.java", "UltraRivalScreens.java",
                     "UltraCharacterScreens.java", "UltraPickHelper.java")
        for n, line in enumerate(read(ultra_dir / name).splitlines())
        if re.search(r"placed\s*>=\s*\w+\s*\|\|", line)
    ]
    check("Ultra pickers never drop items past a hard cap (placed >= N)", not capped,
          ", ".join(capped[:4]))
    pick_helper = read(ultra_dir / "UltraPickHelper.java")
    check("UltraPickHelper pages: pageSlice + placePrevNext + Page X of Y",
          "public static <T> List<T> pageSlice(" in pick_helper
          and "public static void placePrevNext(" in pick_helper
          and '"Page " + (page + 1) + " of " + pages' in pick_helper
          and "placePrevNext(spec, pageBase, pg, pages)" in pick_helper)
    spar_ui = read(ultra_dir / "UltraSparScreens.java")
    war = spar_ui.split("private static void openDojoWarPending(", 1)[-1].split("\n    }\n", 1)[0]
    check("war requests: lines and Accept/Deny buttons share one page slice",
          war.count("for (String card : shown)") == 2 and "pageSlice(cards, CARDS_PER_PAGE, pg)" in war)
    check("Spar picker confirm Back returns to the picker page",
          '"page:pick_confirm:" + action + "|" + here + "|" + name' in spar_ui
          and 'case "pick_apprentice", "pick_mentor" -> "Players"' in spar_ui)
    check("picker paging self-test runs at startup",
          "verifyPickerPaging()" in read(SRC / "net/gui/LmGuiNetwork.java"))
    pend = spar_ui.split("private static void openPending(", 1)[-1].split("\n    }\n", 1)[0]
    check("bond invites: lines and buttons share one page slice (4.6.233)",
          pend.count("for (String card : pageCards)") == 2
          and "SparGuiApi.pendingMentorLine(card)" in pend)
    rival_ui = read(ultra_dir / "UltraRivalScreens.java")
    check("Rival name pickers use Page X of Y + shared Prev/Next; Spectate uses placeNameButtons",
          "UltraPickHelper.pageLabel(spec, pg, pages)" in rival_ui
          and "UltraPickHelper.placePrevNext(spec, pageBase, pg, pages)" in rival_ui
          and '" | page "' not in rival_ui
          and "UltraPickHelper.placeNameButtons(spec, doAction, choices, PICK_PER_PAGE, pageBase, pg, null)" in rival_ui)
    check("rival profile Back returns to the Rival tab page it came from",
          '"page:list_detail:" + profileArg(arg, page)' in rival_ui
          and 'UltraPickHelper.pageArg("actions", returnPage), "Rival"' in rival_ui)
    self_test = read(ultra_dir / "UltraScreenSelfTest.java")
    check("picker paging self-test covers 124 cases (lists 0-30)",
          "for (int total = 0; total <= 30; total++)" in self_test and "cases != 124" in self_test)
    print("\n--- § config editor ---")
    cfg_editor = read(SRC / "config/editor/ConfigEditor.java")
    cfg_ui = read(SRC / "gui/ultra/UltraConfigScreens.java")
    check("manifest config-editor-save-safety row", "config-editor-save-safety" in manifest)
    check("config editor backs up every save and prunes to the newest copies",
          "static boolean backupEverySave(Path file, Instant when)" in cfg_editor
          and "static final int BACKUPS_KEPT = 20;" in cfg_editor
          and "backedUp |= backupEverySave(file, now);" in cfg_editor
          and 'resolveSibling("backups")' in cfg_editor)
    check("save returns a reload report and Review shows it",
          "public record ReportLine(String text, boolean failed)" in cfg_editor
          and "reloading failed, so the server still runs the" in cfg_editor
          and "ConfigEditor.lastSaveReport(uuid)" in cfg_ui)
    check("refused save names the setting and the next step",
          "was refused: " in cfg_editor and "Fix the red rows or Undo them, then save again." in cfg_editor)
    check("enum settings get an Options page; list entries get Remove / Keep",
          "public static List<String> enumOptions(" in cfg_editor
          and 'lower.startsWith("options:")' in cfg_ui
          and 'case "choose" ->' in cfg_ui
          and 'case "remove" ->' in cfg_ui
          and 'UltraStyle.danger("Remove")' in cfg_ui)
    check("another editor's save is caught: Review warns, offers reapply, save refuses overwrite",
          "public static List<EditConflict> conflicts(UUID player)" in cfg_editor
          and "public static String reloadAndReapply(UUID player)" in cfg_editor
          and "after you staged it. Use Reload and reapply" in cfg_editor
          and '"do:reapply"' in cfg_ui and 'case "reapply" ->' in cfg_ui
          and "ConfigEditor.noteActor(uuid," in cfg_ui)
    check("same-millisecond backups get a unique name instead of overwriting",
          'stamp + "~" + String.format(Locale.ROOT, "%02d", next + 1) + ".bak"' in cfg_editor
          and "never into a gap" in cfg_editor
          and "REPLACE_EXISTING" not in cfg_editor.split("static boolean backupEverySave(", 1)[-1]
              .split("private static void writeAudit(", 1)[0])
    check("Review shows who last changed each staged setting (existing audit-log lookup)",
          "ConfigEditor.lastChange(change.moduleId(), change.path())" in cfg_ui
          and '"Last changed by " + last.actor()' in cfg_ui)
    check("ConfigEditor has no raw section sign", "\u00a7" not in cfg_editor)
    check("player record files stay closed in the editor",
          '{"rival", "Rival"}' in cfg_ui and '{"sparring", "Sparring"}' in cfg_ui
          and "player records are not edited here" in cfg_ui)
    points = read(SRC / "progression/shop/PrestigePointsSystem.java")
    turn_in = points.split("public static int pointsForTurnIn(", 1)[1].split("\n    }\n", 1)[0] \
        if "public static int pointsForTurnIn(" in points else ""
    check("turn-in payout checks payoutOverrides before the group bonus (4.6.229-2)",
          "exchange.payoutOverrides" in turn_in and "exchange.bonusGroupSize" in turn_in
          and turn_in.index("payoutOverrides") < turn_in.index("bonusGroupSize"),
          "pointsForTurnIn must read prestige-shop.json exchange overrides, then the group bonus")

    print("\n--- § death tp ---")
    death_tp = read(SRC / "progression/tp/DeathTpPenalty.java")
    death_mixin = read(SRC / "mixin/StatsDataDeathTpPenaltyMixin.java")
    mixins_json = read(MOD / "src/main/resources/legacymechanics.mixins.json")
    check("DeathTpPenalty halves for 10 minutes",
          "MULTIPLIER = 0.5d" in death_tp and "DURATION_MS = 10L * 60L * 1000L" in death_tp)
    resources_mixin = read(SRC / "mixin/ResourcesDeathTpPenaltyMixin.java")
    bonus_mixin = read(SRC / "mixin/PotionEffectHelperTpBonusMixin.java")
    events = read(SRC / "event/DifficultyEvents.java")
    check("death penalty is the TP gain effect at a negative level",
          "MainEffects.TP_GAIN" in death_tp
          and "PENALTY_AMPLIFIER = -3" in death_tp
          and "new MobEffectInstance" in death_tp
          and "tellLiving" in events)
    check("negative TP gain levels use the same 0.25 step",
          "getMultiplierFromEffect" in bonus_mixin
          and "getBonusFromAmplifier" in bonus_mixin
          and "(amplifier + 1) * 0.25d" in bonus_mixin
          and '"tp_gain"' in bonus_mixin
          and "require = 0" not in bonus_mixin
          and "Effect: x0.5" in death_tp
          and '"PotionEffectHelperTpBonusMixin"' in mixins_json)
    check("death penalty does not cut the granted amount",
          "setReturnValue" not in death_mixin
          and "applyToGain" not in death_mixin
          and "applyToAmount" not in death_tp
          and "halveGranted" not in events
          and "addTrainingPoints(FZ)V" in resources_mixin
          and "return amount" in resources_mixin)
    check("death penalty timer is copied onto the respawned player",
          "DeathTpPenalty.KEY" in read(SRC / "event/DifficultyEvents.java")
          and "DeathTpPenalty.FRAC_KEY" in read(SRC / "event/DifficultyEvents.java"))
    check("mixins.json registers StatsDataDeathTpPenaltyMixin",
          '"StatsDataDeathTpPenaltyMixin"' in mixins_json
          and '"ResourcesDeathTpPenaltyMixin"' in mixins_json)

    print("\n--- § class stamina ---")
    stat_mixin = read(SRC / "mixin/StatsDataStatScalingMixin.java")
    pool = read(SRC / "progression/DmzResourcePoolClamp.java")
    plugin = read(SRC / "mixin/LegacyMechanicsMixinPlugin.java")
    check("server pool matches the client bar and LM stays off the client",
          "getStatScaling" in stat_mixin
          and "getInitialBaseStats" in stat_mixin
          and "live / scale" in stat_mixin
          and "setReturnValue" not in stat_mixin
          and "getConfiguredClassStats" not in stat_mixin
          and "getClassStats(" not in stat_mixin
          and "combatScaleMultiplier" not in stat_mixin
          and "ClassRaceStatScale" not in stat_mixin
          and "ClassRaceStatScale" not in pool
          and "return dedicatedServer();" in plugin
          and "return data == null ? 0f : data.getMaxStamina()" in pool)

    print("\n--- § natural skills ---")
    prog_cfg = read(SRC / "progression/ProgressionConfig.java")
    prog_sys = read(SRC / "progression/ProgressionSystem.java")
    diff_cfg = read(SRC / "config/DifficultyConfig.java")
    skill_dir = SRC / "progression/skills"
    check("flight sprint and meditation are removed",
          not (skill_dir / "FlightProgression.java").is_file()
          and not (skill_dir / "SprintJumpProgression.java").is_file()
          and not (skill_dir / "MeditationProgression.java").is_file()
          and "boolean flight()" not in prog_cfg
          and "boolean sprintJump()" not in prog_cfg
          and "boolean meditation()" not in prog_cfg
          and "enablePotential" in prog_cfg.split("boolean potential()")[1].split("boolean globalTpBoost()")[0]
          and "FlightProgression" not in prog_sys
          and "SprintJumpProgression" not in prog_sys
          and "MeditationProgression" not in prog_sys
          and not (skill_dir / "SkillProgression.java").is_file()
          and "PotentialProgression.onPlayerHurt" in prog_sys
          and "enableFlightProgression" not in diff_cfg
          and "enableSprintJump" not in diff_cfg
          and "enableMeditation" not in diff_cfg
          and "enablePotential = true" in diff_cfg)
    nearby = read(skill_dir / "LivingWorldNearbyMeditation.java")
    check("nearby living world meditation",
          "class LivingWorldNearbyMeditation" in nearby
          and "AmbientFighterEntity" in nearby
          and "isMeditating" in nearby
          and "isMeditationCircleMember" in nearby
          and "isPlayerMeditating" in nearby
          and "nearMeditatingPlayer" in nearby
          and "nearTwoLivingCircleNpcs" in nearby
          and "found >= 2" in nearby
          and "NPC_RATE_PERCENT = 75" in nearby
          and "HELD_RATE_PERCENT = 10" in nearby
          and "heldCountForNeed" in nearby
          and "startPlayerMeditation" not in nearby
          and "lm.lw_med.progress_ms" in nearby
          and "MEDITATION BREAKTHROUGH" in nearby
          and "DmzSkillUtil.setLevel" in nearby
          and "meditationDetectionRadius = 16" in diff_cfg
          and "enableLivingWorldMeditation" in diff_cfg
          and "60, 300, 900, 1800, 3600, 7200, 14400, 21600, 28800, 36000" in diff_cfg
          and "LivingWorldNearbyMeditation" in prog_sys
          and "MeditationProgression" not in nearby)

    print("\n--- § commands ---")
    prog_cmds = read(SRC / "command/ProgressionCommands.java")
    cmd_help = read(SRC / "command/LmCommandHelp.java")
    lm_revive = read(SRC / "command/LmRevive.java")
    check("manifest lm-revive row", "lm-revive" in manifest)
    check("/lm revive: donator self form, staff <player> form, DMZ revive called directly",
          "LmRevive.literal()" in read(SRC / "command/MechanicsCommands.java")
          and "StaffAccess.hasSkillCheck(player) && !StaffAccess.isStaff(player)" in lm_revive
          and "StaffAccess.denyUnlessStaff(source)" in lm_revive
          and '"revivePlayers"' in lm_revive and "setAccessible(true)" in lm_revive
          and "Revive is not available right now" in lm_revive)
    check("no top-level /revive or /rev registered by LM",
          not any('m_82127_("revive")' in read(p) and "LmRevive" not in p.name
                  for p in (SRC / "command").glob("*.java"))
          and 'm_82127_("rev")' not in lm_revive)
    check("no meditation slash command",
          'm_82127_("meditation")' not in prog_cmds
          and "/progression meditation" not in cmd_help
          and "/progression meditation" not in prog_cmds)
    check("enddragon spawn is not a command",
          'm_82127_("spawn")' not in prog_cmds
          and "/enddragon spawn" not in cmd_help
          and "enableEndPlayerDragonSummon = true" in diff_cfg
          and "cfg.enableEndPlayerDragonSummon = false" not in diff_cfg
          and "enableEndNaturalDragonSpawn = false" in diff_cfg)
    check("enddragon clear remains for admins",
          'm_82127_("clear")' in prog_cmds
          and "cmdCleanupDragons" in prog_cmds)
    check("player android remove performs the remove",
          "androidRemoveGuiHint" in prog_cmds
          and "return androidRemoveSelf(source);" in prog_cmds
          and "/progression android remove" in cmd_help)
    check("prestige effects view command",
          'm_82127_("effects")' in prog_cmds
          and "prestigeEffects" in prog_cmds
          and "/prestige effects" in cmd_help)

    print("\n--- § fabled removed ---")
    bridge_dir = SRC / "progression/bridge"
    check("FabledBridge class removed", not (bridge_dir / "FabledBridge.java").is_file())
    check("Fabled skill helpers removed",
          not (SRC / "progression/FabledSkills.java").is_file()
          and not (bridge_dir / "PrestigeSkillSync.java").is_file()
          and not (bridge_dir / "EnergyManaSync.java").is_file()
          and not (bridge_dir / "RaceSkillSync.java").is_file()
          and not (bridge_dir / "ClassSkillSync.java").is_file()
          and not (bridge_dir / "ClassPermissionSync.java").is_file())
    overhaul_bridge = read(SRC / "progression/bridge/DmzRevampPrestigeBridge.java")
    race_lock = read(SRC / "progression/race/RaceLock.java")
    energy_guard = read(SRC / "mixin/ResourcesEnergyDrainGuardMixin.java")
    check("Overhaul prestige count follows held wallet",
          "getHeldWallet" in overhaul_bridge and "PrestigeSkillSync" not in overhaul_bridge)
    check("race lock does not reset for a missing skill",
          "FabledSkills" not in race_lock and "dmzstats reset" not in race_lock)
    check("energy drain guard only ignores non-positive amounts",
          "amount <= 0f" in energy_guard and "EnergyManaSync" not in energy_guard)
    check("stamina Fabled drain mixin removed",
          not (SRC / "mixin/ResourcesStaminaDrainGuardMixin.java").is_file())

    print("\n--- § held overhaul ---")
    held_bridge = read(SRC / "progression/bridge/DmzRevampPrestigeBridge.java")
    held_wallet = read(SRC / "progression/shop/PrestigeSystem.java")
    check("Overhaul count is the held wallet 1:1",
          "getHeldWallet" in held_bridge
          and "toOverhaulCount" in held_bridge
          and "ensureHeldRecorded" in held_bridge)
    check("changing held resyncs Overhaul",
          "scheduleSyncAfterStatsReset" in held_wallet.split("void setHeldPublic", 1)[1].split("void ", 1)[0])

    print("\n--- § farming building ---")
    events = read(SRC / "event/DifficultyEvents.java")
    progression = read(SRC / "progression/ProgressionSystem.java")
    diff_cfg = read(SRC / "config/DifficultyConfig.java")
    chest_prog = read(BUKKIT / "ProgressionChestGui.java")
    check("FarmingTp class removed", not (SRC / "progression/tp/FarmingTp.java").is_file())
    check("BuildingTp class removed", not (SRC / "progression/tp/BuildingTp.java").is_file())
    check("block break and place do not award farming or building TP",
          "FarmingTp" not in progression and "BuildingTp" not in progression
          and "onBlockBreak" not in events and "onBlockPlace" not in events)
    check("farming and building flags removed",
          "enableFarmingTp" not in diff_cfg and "enableBuildingTp" not in diff_cfg
          and "Farming TP" not in chest_prog and "Building TP" not in chest_prog)

    print("\n--- § terminal ---")
    terminal = read(SRC / "currency/LightmanTerminal.java")
    terminal_cmd = read(SRC / "command/TerminalCommands.java")
    terminal_plugin = read(BUKKIT / "AdaptiveDifficultyGuiPlugin.java")
    terminal_bridge = read(BUKKIT / "ForgeBridge.java")
    terminal_yml = read(GUI / "src/main/resources/plugin.yml")
    terminal_mohist = read(SRC / "command/MohistCommandBridge.java")
    terminal_staff = read(SRC / "util/StaffAccess.java")
    alias_note = read(ROOT / "uploads/scripts/Aliases-Terminal.yml")
    term_block = terminal_yml.split("\n  terminal:", 1)[1].split("\n  lmdo:", 1)[0]
    check("/terminal opens Lightman network terminal",
          "TerminalMenuProvider.OpenMenu" in terminal and "SimpleValidator.NULL" in terminal)
    check("/terminal is a player command",
          'playerRoot("terminal")' in terminal_cmd
          and "cmi.customalias.terminal" in terminal_cmd
          and "hasDonatorPermission" in terminal_cmd)
    check("bukkit /terminal is registered",
          "\n  terminal:" in terminal_yml
          and "cmi.customalias.terminal" in terminal_yml
          and "permission:" not in term_block
          and '"terminal".equals(name)' in terminal_plugin)
    check("/terminal allows donators or the alias permission",
          "hasDonatorPermission" in terminal_staff
          and "skillCheckPermission" in terminal_staff.split("hasDonatorPermission", 1)[1].split("hasSkillCheck", 1)[0]
          and "TerminalCommands" in terminal_bridge
          and '"allowed".equals' in terminal_bridge
          and '"terminal", List.of(), null' in terminal_mohist)
    check("CMI terminal alias is not reintroduced",
          "forcecast" not in alias_note and "CustomAlias:" not in alias_note
          and "legacymechanics.skillcheck" in alias_note)

    print("\n--- § access (skillcheck + staff admin) ---")
    skill_svc = read(SRC / "progression/shop/SkillCheckService.java")
    staff = read(SRC / "util/StaffAccess.java")
    plugin = read(BUKKIT / "AdaptiveDifficultyGuiPlugin.java")
    yml = read(GUI / "src/main/resources/plugin.yml")
    check("Skill Check NPC opens for any player",
          "openAtNpc(player)" in skill_svc.split("tryOpenFromNpc", 1)[1].split("trigger21", 1)[0]
          and "canUse(player)" in skill_svc)
    check("no openFromNpc bypass", "openFromNpc" not in skill_svc)
    check("hasSkillCheck does not auto-grant staff", "isStaff(player)" not in staff.split("hasSkillCheck", 1)[1].split("hasBukkitIsOp", 1)[0])
    check("ensureSkillsGuiAccess in plugin", "ensureSkillsGuiAccess" in plugin)
    check("skillcheck permission in plugin.yml", "legacymechanics.skillcheck" in yml)
    check("skills staff permission", "permission: difficulty.admin" in yml)
    hub = read(BUKKIT / "HubChestGui.java")
    check("hub Staff Admin tile staff-gated", "ForgeBridge.isStaff(player)" in hub and "Staff Admin" in hub)
    cnpc_admin = read(SRC / "gui/cnpc/CnpcLmAdminGui.java")
    check("admin hub no CNPC import/migrate tile", "hub.admin.migrate" not in hub and "CNPC migrate" not in hub
          and "migrate-cnpc" not in hub.split("private Inventory admin", 1)[1].split("private Inventory", 1)[0]
          if "private Inventory admin" in hub else "hub.admin.migrate" not in hub)
    check("CNPC staff admin no CNPC migrate button", "CNPC data migrate" not in cnpc_admin and "migrate-cnpc" not in cnpc_admin)
    check("hub Skill Check tile donator-gated", "ForgeBridge.hasSkillCheck(player)" in hub)
    cnpc_sk = read(SRC / "gui/cnpc/CnpcLmSkillCheckGui.java")
    check("CNPC skillcheck is one Skills tab",
          "one Skills tab" in cnpc_sk
          and "Natural skills" in cnpc_sk
          and "§dSaga skills" not in cnpc_sk
          and "navSubmenu" not in cnpc_sk)
    unlock = read(SRC / "progression/shop/SkillUnlockService.java")
    check("skill lines show level and locked unlocked or max",
          "§8· §cLocked" in unlock
          and "§8· §aUnlocked" in unlock
          and "§8· §6Max" in unlock
          and "Spar with other players" in unlock
          and "toward 30" in unlock
          and "SOFT CAP" not in unlock
          and "Piccolo" not in unlock)

    print("\n--- § rival ---")
    rival_api = read(SRC / "gui/RivalGuiApi.java")
    rival_mgr = read(SRC / "rival/RivalChallengeManager.java")
    rival_chest = read(BUKKIT / "RivalChestGui.java")
    cmi_rival = read(BUKKIT / "CmiRivalGui.java")
    cnpc_rival = read(SRC / "gui/cnpc/CnpcLmRivalGui.java")
    check("pendingChallengeCards API", "pendingChallengeCards" in rival_api)
    check("pendingRequestCards backend", "pendingRequestCards" in rival_mgr)
    check("chest challenge_pending page", "challenge_pending" in rival_chest)
    check("CMI challenge_pending page", "challenge_pending" in cmi_rival)
    check("CNPC challenge pending paint", "pendingChallengeCards" in cnpc_rival)
    check("rival declare pending_decide", "pending_decide:" in rival_chest and "pending_decide:" in cmi_rival)
    rival_store = read(SRC / "rival/RivalStore.java")
    check("rival duel requests persist in RivalStore", "challengeRequests" in rival_store and "upsertChallengeRequest" in rival_store)
    check("rival duel hydrate on load", "replaceRequestsFromStore" in rival_mgr)
    cleanup = rival_mgr.split("void cleanupPlayer", 1)[1].split("void ", 2)[0] if "void cleanupPlayer" in rival_mgr else ""
    check("rival duel not cleared on logout", "getRequestInvolving" not in cleanup)
    cnpc_rival = read(SRC / "gui/cnpc/CnpcLmRivalGui.java")
    check("rival CNPC reopen duel pending after send", '"challenge_send"' in cnpc_rival and '"challenge_pending"' in cnpc_rival)
    check("rival declare vs challenge labels", "Declare invite" in cnpc_rival and "Challenge request" in cnpc_rival)
    check("rival CNPC TP toggle label", 'toggleOn("TP")' in cnpc_rival and "Teleport msgs" not in cnpc_rival)
    check("rival chest TP toggle label", '"&aTP ON"' in rival_chest and "TP Msg ON" not in rival_chest)
    nemesis_mode = read(SRC / "rival/NemesisSpawnMode.java")
    ultra_rival = read(SRC / "gui/ultra/UltraRivalScreens.java")
    char_api = read(SRC / "gui/CharacterServicesGuiApi.java")
    ultra_char = read(SRC / "gui/ultra/UltraCharacterScreens.java")
    nemesis_mix = read(SRC / "mixin/dmzultra/DmzultraNemesisSpawnGateMixin.java")
    mixins_json = read(MOD / "src/main/resources/legacymechanics.mixins.json")
    check("rival nemesis toggle three states",
          "AUTO" in nemesis_mode and "MANUAL" in nemesis_mode and "OFF" in nemesis_mode)
    # 4.6.236: the menu toggle is gone (stored modes still gate spawns - see the checks below).
    check("manifest nemesis-mode-no-menu-toggle row", "nemesis-mode-no-menu-toggle" in manifest)
    check("character services has no nemesis toggle action or button",
          '"nemesis_spawn_toggle"' not in char_api
          and '"do:nemesis_spawn_toggle"' not in ultra_char
          and "NemesisSpawnMode" not in ultra_char)
    check("stored nemesis mode still read by the spawn gate",
          "NemesisSpawnMode" in nemesis_mix and "public static int mode(" in nemesis_mode)
    check("nemesis toggle absent from rival screens",
          '"nemesis_spawn_toggle"' not in rival_api
          and '"do:nemesis_spawn_toggle"' not in ultra_rival)
    check("nemesis OFF keeps manual menu spawn",
          '"appear".equalsIgnoreCase(trigger)' in nemesis_mix
          and 'method = "champion"' in nemesis_mix
          and "lm$FLOW_APPEAR" in nemesis_mix)
    check("nemesis mode descriptions match behavior",
          "you can still summon yours from the nemesis menu" in nemesis_mode
          and "description(" in nemesis_mode)
    check("rival nemesis mixin registered",
          "DmzultraNemesisSpawnGateMixin" in nemesis_mix
          and '"dmzultra.DmzultraNemesisSpawnGateMixin"' in mixins_json)
    check("rival nemesis mixin gates meet and spawn",
          'method = "meet"' in nemesis_mix and 'method = "spawn"' in nemesis_mix)
    spar_chest = read(BUKKIT / "SparChestGui.java")
    cnpc_spar = read(SRC / "gui/cnpc/CnpcLmSparGui.java")
    check("spar chest TP toggle label", '"&aTP ON"' in spar_chest and "TP Msg ON" not in spar_chest)
    check("spar CNPC TP toggle label", 'toggleOn("TP")' in cnpc_spar and "teleport" not in cnpc_spar.lower())

    print("\n--- § spar ---")
    spar_chest = read(BUKKIT / "SparChestGui.java")
    cmi_spar = read(BUKKIT / "CmiSparGui.java")
    check("spar pending_decide", "pending_decide:" in spar_chest and "pending_decide:" in cmi_spar)
    check("spar dojo war pending decide", "dojo_war_pending_decide:" in spar_chest)
    dojo_rank = read(SRC / "sparring/DojoRankings.java")
    spar_api = read(SRC / "gui/SparGuiApi.java")
    check("dojo war offline by master uuid", "challengeDojoByMasterKey" in dojo_rank)
    check("spar GUI dojo_challenge uuid path", "dojoChallengeByMasterUuid" in read(SRC / "sparring/SparringSystem.java")
          and "uuid:" in spar_api.split("dojo_challenge", 1)[1].split("dojo_accept", 1)[0])
    dojo_war_cnpc = cnpc_spar.split("paintDojoWar", 1)[1].split("paintDojoWarPending", 1)[0] if "paintDojoWar" in cnpc_spar else ""
    check("CNPC dojo war hub no accept/decline", "dojo_accept" not in dojo_war_cnpc)
    check("spar Training bonds wording", "Training bonds" in cnpc_spar and "Training bonds" in spar_chest)
    spar_main = spar_chest.split("private Inventory main", 1)[1].split("private Inventory top", 1)[0] if "private Inventory main" in spar_chest else ""
    check("spar main no End Session button", "spar.main.end_session" not in spar_main)
    spar_combat = read(SRC / "sparring/SparCombat.java")
    award = spar_combat.split("int awardCombatTp", 1)[1].split("awardDamageTp", 1)[0] if "int awardCombatTp" in spar_combat else ""
    check("spar rival bonus raises the action cap",
          "Math.max(1.0f, rival)" in award and "actionCap" in award)

    print("\n--- § difficulty ---")
    cnpc_diff = read(SRC / "gui/cnpc/CnpcLmDifficultyGui.java")
    check("CNPC tiers locked when personal OFF", "paintFeatureLocked" in cnpc_diff
          and "Personal difficulty" in cnpc_diff
          and "main Difficulty page" in cnpc_diff)
    check("CNPC team scaling locked like tiers", "Teams locked" in cnpc_diff
          and "before using team scaling" in cnpc_diff)
    check("Difficulty team action gated when personal OFF", "ACT_TEAM.equals(act)" in read(SRC / "service/DifficultyActions.java"))
    check("CNPC difficulty uses handleArgNoReopen", "handleArgNoReopen" in cnpc_diff)

    print("\n--- § cnpc ---")
    cnpc_support = read(SRC / "gui/cnpc/CnpcGuiSupport.java")
    check("CNPC afterGuiClosed defer reopen", "afterGuiClosed" in cnpc_support)
    check("CNPC flash notice separate widget ids", "ID_FLASH_LABEL_BASE" in cnpc_support)
    cnpc_feedback = read(SRC / "gui/cnpc/CnpcMenuFeedback.java")
    gui_notice = read(SRC / "gui/GuiNotice.java")
    check("CNPC flash notice readable",
          'NOTICE_HEADER = CnpcUltraStyle.HEADER + "Notice"' in cnpc_feedback
          and ("NOTICE_BODY = GuiNotice.NOTICE_BODY" in cnpc_feedback
              or "NOTICE_BODY = CnpcUltraStyle.INFO" in cnpc_feedback)
          and "GuiNotice.noticeBody" in cnpc_feedback
          and "CnpcMenuFeedback.NOTICE_HEADER" in cnpc_support
          and "brightenNoticeLine" in cnpc_support
          and "noticeBody" in cnpc_support
          and "public static String noticeBody" in gui_notice)
    check("CNPC info blocks readable (readableInfoLine)", "readableInfoLine" in cnpc_support
          and "readableInfoLine" in read(SRC / "gui/cnpc/CnpcGuiStyle.java"))
    check("CNPC difficulty clear tier avoids flash ids", "ID_GRID_BASE" in cnpc_diff
          and 'button(gui, 50, "§cClear active tier"' not in cnpc_diff)
    cnpc_preview = read(SRC / "gui/cnpc/CnpcPlayerPreview.java")
    check("CNPC preview live player sync (inventory-style)", "tryBindLivePlayer" in cnpc_preview
          and "setEntitySyncedById" in cnpc_preview)
    cnpc_prestige = read(SRC / "gui/cnpc/CnpcLmPrestigeGui.java")
    check("CNPC prestige section tags avoid info label ids",
          "ID_INLINE_NOTE" in cnpc_prestige and 'paintSectionTag(gui, 11,' not in cnpc_prestige
          and 'paintSectionTag(gui, 12,' not in cnpc_prestige)
    check("Prestige CNPC tier grid avoids flash widget ids",
          "ID_PRESTIGE_TIER_GRID" in cnpc_prestige and "paintTwoColumnButtonGrid(player, gui, row, 50," not in cnpc_prestige)
    build_sh = read(MOD / "build.sh")
    check("build overlays RivalStore + dojo backend", "rival/RivalStore.class" in build_sh and "sparring/DojoRankings.class" in build_sh)
    check(
        "build strips retired CNPC gui/cnpc package",
        'rm -rf "$merge/com/dbzlegacy/adaptivedifficulty/gui/cnpc"' in build_sh
        or "rm -rf" in build_sh and "gui/cnpc" in build_sh,
    )
    print("\n--- § android ---")
    android = read(SRC / "progression/race/AndroidConversion.java")
    check("Android Gero convert no Saiyan deny list", "GERO_RACE_DENY" not in android)
    check("Android CNPC copy does not deny Saiyan", "Saiyan races cannot" not in read(SRC / "gui/cnpc/CnpcLmProgressionGui.java"))
    check("Android convert allows self (not staff-only)", "you can convert yourself" in read(SRC / "gui/ProgressionGuiApi.java"))
    # 4.6.237: Android tab button logic + level-priced self conversion.
    cs_sys = read(SRC / "character/CharacterServicesSystem.java")
    char_ui = read(SRC / "gui/ultra/UltraCharacterScreens.java")
    gui_act = read(SRC / "gui/ultra/UltraGuiActions.java")
    check("Android self conversion costs 1 + level/100 Gold, charged before converting",
          "long gold = 1L + level / 100;" in cs_sys
          and "AncientCoinEconomy.CoinKind.GOLD.copperValue" in cs_sys
          and cs_sys.index("chargeAc(player, cost)", cs_sys.index("executeAndroidConversion"))
              < cs_sys.index("AndroidConversion.convert(player)")
          and "refund(player, cost);" in cs_sys.split("executeAndroidConversion", 1)[1].split("public static long amountDue", 1)[0])
    check("Android tab shows only the applicable button (Convert / Remove / none)",
          "AndroidConversion.eligibility(player)" in char_ui
          and "case UPGRADED ->" in char_ui and "case ELIGIBLE ->" in char_ui
          and '"Eligible races: " + AndroidConversion.eligibleRaceHint()' in char_ui
          and "already an Android by race" in char_ui
          and 'UltraStyle.confirm("Convert to Android")' in char_ui
          and '"do:android"' not in char_ui)
    check("staff target page shows only Convert or Remove; staff tools stay free",
          "targetState == AndroidConversion.Eligibility.ELIGIBLE" in char_ui
          and "targetState == AndroidConversion.Eligibility.UPGRADED" in char_ui
          and "applyAndroidStaff" in gui_act)
    check("self conversion is player-facing and never charges an inspected player",
          '"android_self".equals(act)' in gui_act
          and "subject != null && subject != viewer" in gui_act)
    cnpc_rival = read(SRC / "gui/cnpc/CnpcLmRivalGui.java")
    check("CNPC rival empty labels avoid flash ids", "ID_EMPTY_PLACEHOLDER" in cnpc_rival
          and "addLabel(50," not in cnpc_rival)
    check("CNPC rival challenge duration grid avoids flash ids", "ID_GRID_BASE" in cnpc_rival
          and "paintTwoColumnButtonGrid(pl, gui, row, 30," not in cnpc_rival)
    check("CNPC rival act defers menu reopen", "runDeferred(player, reopen)" in read(SRC / "gui/cnpc/CnpcGuiSupport.java"))
    check("Rival challenge request window >= 5 min", "CH_REQUEST_EXPIRE_MS = 300_000" in read(SRC / "rival/RivalConstants.java"))
    rival_mgr = read(SRC / "rival/RivalChallengeManager.java")
    check("Rival accept not blocked by distance at accept", "blocks to accept" not in rival_mgr)
    cnpc_prog = read(SRC / "gui/cnpc/CnpcLmProgressionGui.java")
    check("CNPC TP boost presets avoid flash ids", "ID_BOOST_PRESET_BASE" in cnpc_prog
          and "boostPreset(gui, player, row, 50," not in cnpc_prog)
    check("Progression CNPC staff flag toggles", "HINT_TOGGLE_STAFF" in cnpc_prog and "paintAllFlags" in cnpc_prog)
    prog_chest = read(BUKKIT / "ProgressionChestGui.java")
    check("Progression chest staff section toggles", "allFlags" in prog_chest and "SlotAction.act(\"flag\"" in prog_chest)
    chat_prog = read(SRC / "gui/ProgressionChatMenu.java")
    check("Progression chat staff flag toggles", "appendFlagToggles" in chat_prog and "[All flags]" in chat_prog)
    cmi_prog = read(BUKKIT / "CmiProgressionGui.java")
    check("Progression CMI no flag board methods", "openFlags" not in cmi_prog and "openFabledFlags" not in cmi_prog)
    check("Android build overlay ships AndroidConversion", "progression/race/AndroidConversion.class" in build_sh)
    check("build overlays TpBossNotify", "util/TpBossNotify.class" in build_sh)
    check("build overlays ConfigFieldHelp", "config/editor/ConfigFieldHelp.class" in build_sh)
    chest_prog = read(BUKKIT / "ProgressionChestGui.java")
    tooltips = read(GUI / "src/main/resources/gui-tooltips.json")
    check("Android GUI copy lists Saiyan eligible", "Saiyan excluded" not in chest_prog and "Saiyan excluded" not in tooltips)
    preview_tex = read(SRC / "gui/cnpc/DmzPreviewTexture.java")
    check("CNPC preview race textures (namekian/bio)", "namekian" in preview_tex and "bioandroid" in preview_tex)
    check("PrestigeSystem shrinkNeedFloor early band", "shrinkNeedFloor" in prestige)

    print("\n--- § ui scale ---")
    ui_fit = read(SRC / "gui/cnpc/CnpcUiFit.java")
    cnpc_support = read(SRC / "gui/cnpc/CnpcGuiSupport.java")

    def mc_gui_scale(fb_w: int, fb_h: int) -> int:
        scale = 1
        while (scale < fb_w and scale < fb_h
               and fb_w // (scale + 1) >= 320 and fb_h // (scale + 1) >= 240):
            scale += 1
        return scale

    check("CNPC menus read the player screen size",
          "getScreenSize" in ui_fit and "compressToWindow" in ui_fit
          and "CnpcUiFit.fit" in cnpc_support and "compressToWindow" in cnpc_support)
    check("menus fit Minecraft's real GUI scale without a second font shrink",
          "guiScale(fb[0], fb[1])" in ui_fit
          and "Math.min(sx, sy)" in ui_fit
          and "tightScale" not in ui_fit
          and "label.setScale" not in ui_fit
          and "new int[] {4, 5}" not in ui_fit)
    check("GUI scale uses Minecraft's 320x240 floor",
          "MIN_SCALED_WIDTH = 320" in ui_fit and "MIN_SCALED_HEIGHT = 240" in ui_fit
          and "framebufferWidth / (scale + 1)" in ui_fit)
    check("1080p Auto GUI scale is 4 (480x270)",
          mc_gui_scale(1920, 1080) == 4)
    check("1440p Auto GUI scale is 6 (shorter than the design window)",
          mc_gui_scale(2560, 1440) == 6)
    check("4K Auto GUI scale is 9",
          mc_gui_scale(3840, 2160) == 9)

    print("\n--- § version ---")
    mod = read(MOD / "src/main/java/com/dbzlegacy/adaptivedifficulty/AdaptiveDifficultyMod.java")
    toml = read(MOD / "src/main/resources/META-INF/mods.toml")
    ver_m = re.search(r'VERSION = "([^"]+)"', mod)
    ver_t = re.search(r'^version = "([^"]+)"', toml, re.M)
    ver_y = re.search(r"^version:\s*['\"]?([^'\"\n]+)", yml, re.M)
    v_mod = ver_m.group(1) if ver_m else None
    v_toml = ver_t.group(1) if ver_t else None
    v_yml = ver_y.group(1).strip() if ver_y else None
    if skip_gui:
        check("VERSION == mods.toml", v_mod == v_toml, f"{v_mod!r} / {v_toml!r}")
    else:
        check("VERSION == mods.toml == plugin.yml", v_mod == v_toml == v_yml, f"{v_mod!r} / {v_toml!r} / {v_yml!r}")
    if v_mod:
        forge_jar = ROOT / "mods" / f"LegacyMechanics-{v_mod}.jar"
        check(f"Forge jar exists for {v_mod}", forge_jar.is_file())
        shipped_gui = list((ROOT / "plugins").glob("LegacyMechanicsGUI-*.jar"))
        check(
            "plugins/ does not ship LegacyMechanicsGUI",
            not shipped_gui,
            ", ".join(p.name for p in shipped_gui) if shipped_gui else "",
        )
        if forge_jar.is_file():
            r = subprocess.run(
                ["javap", "-classpath", str(forge_jar), "-public",
                 "com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeSystem"],
                capture_output=True,
                text=True,
            )
            prestige_abi = (r.stdout or "") + (r.stderr or "")
            check(
                "shipped PrestigeSystem.heldCountForNeed(ServerPlayer)",
                r.returncode == 0 and "heldCountForNeed(net.minecraft.server.level.ServerPlayer)" in prestige_abi,
                "rebuild overlay — ProgressionGuiApi calls method missing from base jar merge",
            )
            r2 = subprocess.run(
                ["javap", "-classpath", str(forge_jar), "-private",
                 "com.dbzlegacy.adaptivedifficulty.gui.cnpc.CnpcPlayerPreview"],
                capture_output=True,
                text=True,
            )
            preview_abi = (r2.stdout or "") + (r2.stderr or "")
            check(
                "shipped CnpcPlayerPreview live sync helper",
                r2.returncode == 0 and "tryBindLivePlayer" in preview_abi,
                "rebuild overlay — CNPC preview must ship from gui/cnpc overlay",
            )
            r3 = subprocess.run(
                ["javap", "-classpath", str(forge_jar), "-private",
                 "com.dbzlegacy.adaptivedifficulty.progression.race.AndroidConversion"],
                capture_output=True,
                text=True,
            )
            android_abi = (r3.stdout or "") + (r3.stderr or "")
            check(
                "shipped AndroidConversion.raceAllowsAndroidForms",
                r3.returncode == 0 and "raceAllowsAndroidForms" in android_abi,
                "rebuild overlay — AndroidConversion.class must merge onto base jar",
            )
            import zipfile

            with zipfile.ZipFile(forge_jar) as zf:
                jar_names = set(zf.namelist())
                cmd_bytes = zf.read(
                    "com/dbzlegacy/adaptivedifficulty/command/MechanicsCommands.class"
                )
                check(
                    "dead chat menus absent from jar (4.6.222)",
                    not any(
                        name.startswith(
                            "com/dbzlegacy/adaptivedifficulty/gui/" + dead
                        )
                        for dead in (
                            "MechanicsChatMenu",
                            "RivalChatMenu",
                            "SparChatMenu",
                        )
                        for name in jar_names
                    ),
                    "deleted chat menus must not ship — drop them from build.sh overlay list",
                )
                check(
                    "shipped MechanicsCommands top-level /lm open",
                    b"open" in cmd_bytes and b"page" in cmd_bytes,
                    "rebuild overlay — MechanicsCommands must merge from src",
                )
                check(
                    "shipped CharacterServicesPermissionBootstrap",
                    "com/dbzlegacy/adaptivedifficulty/character/CharacterServicesPermissionBootstrap.class"
                    in jar_names,
                    "rebuild overlay — character permission bootstrap must ship on Forge-only",
                )

    out = SIM / "out/ship-manifest-audit.md"
    out.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# Ship manifest audit",
        "",
        f"- **OK:** {len(ok)}",
        f"- **Errors:** {len(errors)}",
        "",
    ]
    if errors:
        lines.append("## FAIL\n")
        lines.extend(f"- {e}" for e in errors)
    else:
        lines.append("## Result\n\n**PASS** — all owner manifest checks satisfied.\n")
    lines.append("\nSee [LM_SHIP_MANIFEST.md](../../../docs/LM_SHIP_MANIFEST.md).\n")
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"\nWrote {out.relative_to(ROOT)}")

    print("\n=== Summary ===")
    if errors:
        print(f"FAIL — {len(errors)} error(s). Do not deploy.")
        for e in errors:
            print(f"  - {e}")
        return 1
    print(f"PASS — ship manifest OK ({len(ok)} checks)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
