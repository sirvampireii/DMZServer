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
    check("PrestigeSystem need is the Overhaul cap (old ladder gone)",
          "DmzRevampPrestigeBridge.levelCap(data)" in prestige and "HELD_GATE_MIN_COMPLETED" not in prestige)
    check("PrestigeSystem heldCountForNeed", "heldCountForNeed" in prestige)
    check("PrestigeSystem has no needForProgress / veteran branch", "needForProgress" not in prestige)
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
    summon_body = boss_src.split("public static SummonOutcome summonOutcome(", 1)[-1].split("\n    }\n", 1)[0]
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

    print("\n--- § donator node ---")
    don_staff = read(SRC / "util/StaffAccess.java")
    don_cfg = read(SRC / "config/DifficultyConfig.java")
    don_reg = read(SRC / "config/ConfigRegistry.java")
    don_help = read(SRC / "config/editor/ConfigFieldHelp.java")
    don_revive = read(SRC / "command/LmRevive.java")
    don_lm_help = read(SRC / "command/LmCommandHelp.java")
    has_sc = don_staff.split("public static boolean hasSkillCheck(", 1)[-1].split("\n    }\n", 1)[0]
    has_don = don_staff.split("public static boolean hasDonatorPermission(", 1)[-1].split("\n    }\n", 1)[0]
    revive_self = don_revive.split("private static int reviveSelf(", 1)[-1].split("\n    }\n", 1)[0]
    revive_other = don_revive.split("private static int reviveOther(", 1)[-1].split("\n    }\n", 1)[0]
    deny_other = don_staff.split("public static int denyUnlessReviveOthers(", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest donator-permission-umbrella row", "donator-permission-umbrella" in manifest)
    check("config: donatorPermission + reviveOthersPermission replace skillCheckPermission",
          'public String donatorPermission = "legacymechanics.donator";' in don_cfg
          and 'public String reviveOthersPermission = "legacymechanics.revive.others";' in don_cfg
          and "skillCheckPermission" not in don_cfg and "skillCheckPermission" not in don_reg
          and "skillCheckPermission" not in don_help
          and '"donatorPermission"' in don_reg and '"reviveOthersPermission"' in don_reg
          and "donatorPermission=" in don_help and "reviveOthersPermission=" in don_help)
    check("blank or * nodes fall back to the defaults",
          '"*".equals(cfg.donatorPermission.trim())' in don_cfg
          and 'cfg.donatorPermission = "legacymechanics.donator";' in don_cfg
          and '"*".equals(cfg.reviveOthersPermission.trim())' in don_cfg
          and 'cfg.reviveOthersPermission = "legacymechanics.revive.others";' in don_cfg)
    check("Skill Check and donator checks read the one donator node; no staff auto-grant",
          "donatorNode()" in has_sc and "cfg.enableSkillCheck || !cfg.enableSkillUnlockService" in has_sc
          and "isStaff" not in has_sc and "donatorNode()" in has_don
          and "legacymechanics.skillcheck" not in "".join(read(f) for f in SRC.rglob("*.java")))
    check("self revive: self-revive node or staff, own message (not the Skill Check one)",
          "StaffAccess.hasSelfRevive(player) && !StaffAccess.isStaff(player)" in revive_self
          and "SELF_DONATOR" in revive_self and "SKILLCHECK_DONATOR" not in don_revive
          and "hasSkillCheck" not in don_revive)
    check("revive others: staff source or revive node; human denial without a node string",
          "StaffAccess.denyUnlessReviveOthers(source) == 0" in revive_other
          and "isStaffSource(src)" in don_staff.split("hasReviveOthersSource(", 1)[-1].split("\n    }\n", 1)[0]
          and "legacymechanics" not in deny_other and "staff or players with revive access" in deny_other
          and '"revive an online player (staff or revive access)"' in don_lm_help)

    # Mirror of StaffAccess / LmRevive gates. perms: set of nodes; staff: op or admin node.
    def _norm(node, default):
        return default if node is None or not node.strip() or node.strip() == "*" else node.strip()

    DON = _norm("legacymechanics.donator", "legacymechanics.donator")
    SELF = _norm("legacymechanics.donator.selfrevive", "legacymechanics.donator.selfrevive")
    TERM = _norm("legacymechanics.donator.terminal", "legacymechanics.donator.terminal")
    REV = _norm("legacymechanics.revive.others", "legacymechanics.revive.others")

    def _skill_check(p, flags=True):
        return p is not None and flags and DON in p["perms"]

    def _terminal(p):  # no staff bypass, same as before
        return TERM in p["perms"] or "cmi.customalias.terminal" in p["perms"]

    def _self_revive(p):
        return SELF in p["perms"] or p["staff"]

    def _revive_others(p):
        return p is None or p["staff"] or REV in p["perms"]  # None = console (staff source)

    def _pl(perms=(), staff=False):
        return {"perms": set(perms), "staff": staff}

    donor = _pl([DON])
    self_only = _pl([SELF])
    term_only = _pl([TERM])
    old_node = _pl(["legacymechanics.skillcheck"])
    staff_only = _pl(staff=True)
    reviver = _pl([REV])
    nobody = _pl()
    check("sim: base donator node passes Skill Check only (no self revive, no terminal)",
          _skill_check(donor) and not _self_revive(donor) and not _terminal(donor))
    check("sim: self-revive node alone passes self revive (no donator node needed), not Skill Check or terminal",
          _self_revive(self_only) and not _skill_check(self_only) and not _terminal(self_only))
    check("sim: terminal node alone passes the terminal, not Skill Check or self revive",
          _terminal(term_only) and not _skill_check(term_only) and not _self_revive(term_only))
    check("sim: old skillcheck node alone fails all three",
          not _skill_check(old_node) and not _self_revive(old_node) and not _terminal(old_node))
    check("sim: staff without nodes fail Skill Check and terminal, pass self revive and revive others",
          not _skill_check(staff_only) and not _terminal(staff_only)
          and _self_revive(staff_only) and _revive_others(staff_only))
    check("sim: revive-others node only passes revive others, fails self revive and Skill Check",
          _revive_others(reviver) and not _self_revive(reviver) and not _skill_check(reviver))
    check("sim: neither node is denied revive others; console passes",
          not _revive_others(nobody) and _revive_others(None))
    check("sim: self revive ignores Skill Check flags being off",
          _self_revive(self_only) and not _skill_check(donor, flags=False))
    check("sim: blank or * donatorPermission falls back to the default",
          _norm("", "legacymechanics.donator") == "legacymechanics.donator"
          and _norm("*", "legacymechanics.donator") == "legacymechanics.donator"
          and _norm(None, "legacymechanics.donator") == "legacymechanics.donator"
          and _norm("vip.node", "legacymechanics.donator") == "vip.node")

    print("\n--- § donator sub-nodes ---")
    sub_term = read(SRC / "command/TerminalCommands.java")
    sub_bridge = read(BUKKIT / "ForgeBridge.java")
    check("manifest donator-subnodes row", "donator-subnodes" in manifest)
    check("config: selfRevivePermission + terminalPermission with defaults, fallback, registry, help",
          'public String selfRevivePermission = "legacymechanics.donator.selfrevive";' in don_cfg
          and 'public String terminalPermission = "legacymechanics.donator.terminal";' in don_cfg
          and 'cfg.selfRevivePermission = "legacymechanics.donator.selfrevive";' in don_cfg
          and 'cfg.terminalPermission = "legacymechanics.donator.terminal";' in don_cfg
          and '"*".equals(cfg.selfRevivePermission.trim())' in don_cfg
          and '"*".equals(cfg.terminalPermission.trim())' in don_cfg
          and '"selfRevivePermission"' in don_reg and '"terminalPermission"' in don_reg
          and "selfRevivePermission=" in don_help and "terminalPermission=" in don_help)
    check("self revive and terminal read their own nodes, not donatorNode()",
          "selfReviveNode()" in don_staff.split("public static boolean hasSelfRevive(", 1)[1].split("}", 1)[0]
          and "terminalNode()" in don_staff.split("public static boolean hasTerminalPermission(", 1)[1].split("}", 1)[0]
          and "hasDonatorPermission" not in don_revive and "hasDonatorPermission" not in sub_term
          and "StaffAccess.hasTerminalPermission(player)" in sub_term
          and "StaffAccess.hasPermission(player, ALIAS_PERMISSION)" in sub_term
          and "isStaff" not in sub_term)
    check("denials say higher donator ranks, no raw node string",
          "perk of higher donator ranks" in don_revive
          and "perk of higher donator ranks" in sub_bridge
          and "legacymechanics" not in don_revive.split("SELF_DONATOR =", 1)[1].split(";", 1)[0])
    check("Skill Check still the base donator node; revive others unchanged",
          "donatorNode()" in has_sc and "StaffAccess.denyUnlessReviveOthers(source) == 0" in revive_other)

    print("\n--- § grounds bar ---")
    pg_src = read(SRC / "rival/ProvingGrounds.java")
    pg_bar = read(SRC / "rival/ProvingGroundsBar.java")
    pg_events = read(SRC / "event/DifficultyEvents.java")
    pg_mgr = read(SRC / "rival/RivalChallengeManager.java")
    pg_mult = pg_src.split("public static float winnerTpMultiplier(", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest proving-grounds-bar row", "proving-grounds-bar" in manifest)
    check("grounds constants unchanged (1.35 / 1.25 / 12 RP / 12000 TP / 45 RP / 0.40 / 5-15 wins / radius 48)",
          "ON_GROUNDS_TP_MULT = 1.35;" in pg_src and "UNDERDOG_TP_MULT = 1.25;" in pg_src
          and "ON_GROUNDS_RP_BONUS = 12;" in pg_src and "RECLAIM_TP = 12000;" in pg_src
          and "RECLAIM_RP = 45;" in pg_src and "CHALLENGE_TP_BONUS = 0.40;" in pg_src
          and "TIER2_WINS = 5;" in pg_src and "TIER3_WINS = 15;" in pg_src and "RADIUS = 48.0;" in pg_src)
    check("underdog multiplier for a non-champion winner; reclaim still pays RECLAIM_TP and RECLAIM_RP",
          "!pg.championUuid.isBlank() && (winner == null || !pg.championUuid.equals(winner.toString()))" in pg_mult
          and "mult *= (float) UNDERDOG_TP_MULT;" in pg_mult
          and "RivalTpCurve.scale(winP, ProvingGrounds.RECLAIM_TP, \"burst\")" in pg_mgr
          and "winRp += ProvingGrounds.RECLAIM_RP;" in pg_mgr)
    check("bar is display only: own map, no bonus, TP, RP, death-penalty or TP notify bar calls",
          "ConcurrentHashMap<UUID, ServerBossEvent> BARS = new ConcurrentHashMap<>();" in pg_bar
          and not any(t in pg_bar for t in ("awardTp", "addRp", "challengeTpMultiplier", "DeathTpPenalty",
                                            "TpBossNotify", "Scoreboard", "UNDERDOG_TP_MULT")))
    check("bar checks the player's own rival links with onGrounds, once a second, and hides on logout",
          "RivalStore.get().get(myUuid)" in pg_bar and "me.rivals.values()" in pg_bar
          and "ProvingGrounds.onGrounds(player, pg)" in pg_bar
          and "m_11314_" not in pg_bar
          and "if (player.f_19797_ % 20 == 0) {\n            com.dbzlegacy.adaptivedifficulty.rival.ProvingGroundsBar.tick(player);" in pg_events
          and "ProvingGroundsBar.onLogout(player);" in pg_events)
    check("bar created once, renamed only on text change, removed on leave",
          pg_bar.count("new ServerBossEvent(") == 1 and "if (bar == null) {" in pg_bar
          and "!text.equals(TEXT.get(id))" in pg_bar and "BARS.remove(id)" in pg_bar)
    check("bar text names the grounds, tier, champion, battles, and the viewer's role",
          "ProvingGrounds.displayName(pg)" in pg_bar and '"Legendary"' in pg_bar and '"Dominant"' in pg_bar
          and '"Claimed"' in pg_bar and '" - Champion "' in pg_bar and "pg.battles" in pg_bar
          and '"You are the champion"' in pg_bar and '"You are the underdog challenger"' in pg_bar)

    # Mirror of ProvingGrounds.challengeTpMultiplier and the reclaim payout.
    def _pg_mult(on_grounds, champion, me):
        if not on_grounds:
            return 1.0
        m = 1.35
        if champion and champion != me:
            m *= 1.25
        return m * (1.0 + 0.40)

    def _reclaim(winner, champion):
        return (12000, 45) if champion and champion != winner else (0, 0)

    check("sim: non-champion on grounds still gets the 1.25 underdog multiplier",
          abs(_pg_mult(True, "champ", "challenger") - 1.35 * 1.25 * 1.40) < 1e-9)
    check("sim: champion on grounds does not get the underdog multiplier",
          abs(_pg_mult(True, "champ", "champ") - 1.35 * 1.40) < 1e-9)
    check("sim: reclaim still pays 12000 TP and 45 RP", _reclaim("challenger", "champ") == (12000, 45))

    print("\n--- § grounds bonus ---")
    end_win = pg_mgr.split("float baseWin = (related ? RivalConstants.CH_WIN_TP", 1)[-1].split(
        "RivalProgression.get().onChallengeEnd(winP, loseP, ch, false, knockout);", 1)[0]
    end_draw = pg_mgr.split('if ("draw".equals(reason) || "distance".equals(reason) || winner == null) {', 1)[-1].split("deliverReport(", 1)[0]
    pb_body = pg_src.split("public static BattleResult processBattle(", 1)[-1].split("\n    public static void touchDraw(", 1)[0]
    check("manifest proving-grounds-bonus-parity row", "proving-grounds-bonus-parity" in manifest)
    check("win TP: on grounds when EITHER fighter is inside (script fightingOnGrounds), read before the claim changes",
          "boolean onPg = ProvingGrounds.fightingOnGrounds(winP, loseP, pairPg);" in end_win
          and "float pgMult = ProvingGrounds.winnerTpMultiplier(pairPg, winner, onPg);" in end_win
          and "if (onPg) {\n                loseBase = (float) (loseBase * ProvingGrounds.ON_GROUNDS_TP_MULT);" in end_win
          and end_win.index("winnerTpMultiplier") < end_win.index("ProvingGrounds.processBattle(")
          and "challengeTpMultiplier" not in pg_src + pg_mgr)
    check("claim read from either link of the pair (one-sided links), in endChallenge, processBattle, and draws",
          "ProvingGrounds.pairGrounds(winRec.rivals.get(loseRec.uuid), loseRec.rivals.get(winRec.uuid))" in end_win
          and "Grounds pg = pairGrounds(wLink, lLink);" in pb_body
          and "Grounds pg = pairGrounds(linkA, linkB);" in pg_src
          and "ProvingGrounds.pairGrounds(recA.rivals.get(recB.uuid), recB.rivals.get(recA.uuid))" in pg_mgr)
    check("draw on grounds pays the 1.35 TP multiplier (script parity) with a grounds label",
          "drawBase = (float) (drawBase * ProvingGrounds.ON_GROUNDS_TP_MULT);" in end_draw
          and '"Rival Draw (Proving Grounds)"' in end_draw)
    check("one TP grant per player at the end: reclaim TP folded into the winner's grant, labels name the grounds",
          "awardTp" not in pb_body
          and "winTp += RivalTpCurve.scale(winP, ProvingGrounds.RECLAIM_TP, \"burst\");" in end_win
          and '"Reclaimed Proving Grounds"' in end_win and 'String pgNote = onPg ? " (Proving Grounds)" : "";' in end_win
          and end_win.count("DmzRewards.awardTp(") == 2
          and end_win.index("ProvingGrounds.processBattle(") < end_win.index("DmzRewards.awardTp("))
    check("RP stays mutual-only (script: challenge RP only for mutual pairs)",
          "// Script: challenge RP only when the pair is mutual.\n        if (!mutual) {\n            return;" in pg_mgr)
    check("raw-id grounds names (time chamber) display as a place name",
          '"the Hyperbolic Time Chamber"' in pg_src and "place.indexOf(':') >= 0 ? battlefieldName(pg.biome, pg.dim)" in pg_src)

    # Mirror of endChallenge (win/KO/damage), processBattle, and applyWinLoss, using raw bases
    # before the level curve. Constants are the shipped ones.
    WIN, KO_BONUS, LOSE, DRAW = 1.0, 0.0, 1.0, 1.0  # relative bases: multipliers are what matter

    def _end(winner, loser, mutual, champion, win_on, lose_on, claim_on_winner_side=True, ko=True):
        claim = {"active": True, "champion": champion}
        links = {"w": claim if claim_on_winner_side else None, "l": None if claim_on_winner_side else claim}
        pair = links["w"] or links["l"]  # pairGrounds
        on = bool(pair and pair["active"] and (win_on or lose_on))  # fightingOnGrounds
        wm = 1.0
        if on:
            wm = 1.35 * 1.40
            if pair["champion"] and pair["champion"] != winner:
                wm *= 1.25
        lm = 1.35 if on else 1.0
        reclaimed = on and pair["champion"] and pair["champion"] != winner
        rp_w = rp_l = 0
        if mutual:
            if on:
                rp_w += 12
                rp_l += 12
            if reclaimed:
                rp_w += 45
        new_champ = winner if reclaimed else pair["champion"]
        return {"wm": wm, "lm": lm, "reclaim_tp": 12000 if reclaimed else 0, "rp_w": rp_w, "rp_l": rp_l,
                "champion": new_champ, "note": "Reclaimed Proving Grounds" if reclaimed else ("Rival KO Win (Proving Grounds)" if on else "Rival KO Win")}

    champ_ko = _end("A", "B", True, "A", True, True)
    check("sim: mutual KO on grounds, champion wins -> winner x1.89, loser x1.35, +12 RP both, no reclaim",
          abs(champ_ko["wm"] - 1.35 * 1.40) < 1e-9 and abs(champ_ko["lm"] - 1.35) < 1e-9
          and champ_ko["rp_w"] == 12 and champ_ko["rp_l"] == 12 and champ_ko["reclaim_tp"] == 0)
    reclaim = _end("B", "A", True, "A", True, True)
    check("sim: underdog reclaim on grounds -> x1.35 x1.40 x1.25, champion swaps, 12000 TP, +12+45 RP",
          abs(reclaim["wm"] - 1.35 * 1.40 * 1.25) < 1e-9 and reclaim["champion"] == "B"
          and reclaim["reclaim_tp"] == 12000 and reclaim["rp_w"] == 57 and reclaim["rp_l"] == 12
          and reclaim["note"] == "Reclaimed Proving Grounds")
    non_mutual = _end("A", "B", False, "A", True, True)
    check("sim: non-mutual pair on grounds -> TP multipliers apply, RP bonus none (mutual-only by script)",
          abs(non_mutual["wm"] - 1.35 * 1.40) < 1e-9 and abs(non_mutual["lm"] - 1.35) < 1e-9
          and non_mutual["rp_w"] == 0 and non_mutual["rp_l"] == 0)
    win_off = _end("A", "B", True, "A", False, True)
    check("sim: winner off grounds at the end, loser on -> fight still counts as on grounds (was 1.0 before 4.6.246)",
          abs(win_off["wm"] - 1.35 * 1.40) < 1e-9 and abs(win_off["lm"] - 1.35) < 1e-9 and win_off["rp_w"] == 12)
    both_off = _end("A", "B", True, "A", False, False)
    check("sim: both off grounds at the end -> no grounds bonus at all",
          both_off["wm"] == 1.0 and both_off["lm"] == 1.0 and both_off["rp_w"] == 0 and both_off["reclaim_tp"] == 0)
    one_sided = _end("B", "A", True, "A", True, True, claim_on_winner_side=False)
    check("sim: claim only on the loser's link -> still found, underdog reclaim pays",
          one_sided["reclaim_tp"] == 12000 and abs(one_sided["wm"] - 1.35 * 1.40 * 1.25) < 1e-9)

    print("\n--- § grounds underdog + draw RP ---")
    ud_events = read(SRC / "event/DifficultyEvents.java")
    draw_rp = pg_mgr.split("private void awardDrawRp(", 1)[-1].split("\n    }\n", 1)[0]
    ud_scale = pg_mgr.split("public float underdogDamageScale(", 1)[-1].split("\n    }\n", 1)[0]
    ud_notice = pg_src.split("public static void underdogNotice(", 1)[-1].split("\n    }\n", 1)[0]
    ud_is = pg_src.split("public static boolean isUnderdogOnGrounds(", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest proving-grounds-underdog-boost row", "proving-grounds-underdog-boost" in manifest)
    check("draw +12 RP only when either fighter is on the grounds; draw RP still mutual-only",
          "awardDrawRp(recA, recB, ch, drawOnPg);" in pg_mgr
          and "if (onGrounds) {\n            rp += ProvingGrounds.ON_GROUNDS_RP_BONUS;" in draw_rp
          and "!aLink.mutual || !bLink.mutual" in draw_rp and "pairGrounds" not in draw_rp)
    check("underdog boost x1.12: active challenge, opponent only, non-champion on grounds, melee only, nothing stored",
          "UNDERDOG_OFFENSE = 1.12;" in pg_src
          and "kiDamage" in ud_scale and "ch.status != RivalChallenge.Phase.ACTIVE" in ud_scale
          and "!ch.involves(victim.m_20148_())" in ud_scale
          and "ProvingGrounds.isUnderdogOnGrounds(attacker, pg)" in ud_scale
          and "!pg.championUuid.isBlank()" in ud_is and "!pg.championUuid.equals(player.m_20148_().toString())" in ud_is
          and "onGrounds(player, pg)" in ud_is
          and not any(t in ud_scale for t in ("addBonus", "getBonusStats", "PersistentDataAccess", "markDirty")))
    check("boost applied in the PvP hurt path; offense aura stays off",
          ".underdogDamageScale(pvpVictim, pvpAttacker," in ud_events
          and "event.setAmount(event.getAmount() * underdog);" in ud_events
          and "OFFENSE_ENABLED = false;" in read(SRC / "rival/RivalConstants.java"))
    check("challenge start: only the underdog on the grounds gets the notice, plain words",
          "ProvingGrounds.underdogNotice(a, startPg);" in pg_mgr and "ProvingGrounds.underdogNotice(b, startPg);" in pg_mgr
          and "if (!isUnderdogOnGrounds(player, pg)) {\n            return;" in ud_notice
          and "Underdog bonus active on " in ud_notice and "challengeFlavor" not in pg_src)

    # Mirror of awardDrawRp and underdogDamageScale.
    def _draw_rp(mutual, claim_active, a_on, b_on, base=0):
        if not mutual:
            return None
        return base + (12 if claim_active and (a_on or b_on) else 0)

    def _underdog(me, champion, on_grounds, active=True, ki=False):
        boosted = active and not ki and bool(champion) and champion != me and on_grounds
        return (1.12 if boosted else 1.0), boosted  # (damage scale, start notice)

    check("sim: draw on grounds, mutual pair -> +12 RP", _draw_rp(True, True, True, False) == 12)
    check("sim: draw off grounds, mutual pair with an active claim -> no +12", _draw_rp(True, True, False, False) == 0)
    ud = _underdog("B", "A", True)
    check("sim: underdog on grounds at start -> notice, melee x1.12 during the challenge", ud == (1.12, True))
    check("sim: champion on grounds -> no boost, no notice", _underdog("A", "A", True) == (1.0, False))
    check("sim: underdog off grounds -> no boost, no notice", _underdog("B", "A", False) == (1.0, False))
    check("sim: boost ends with the challenge and never boosts ki",
          _underdog("B", "A", True, active=False)[0] == 1.0 and _underdog("B", "A", True, ki=True)[0] == 1.0)

    print("\n--- § death penalty bar ---")
    dp = read(SRC / "progression/tp/DeathTpPenalty.java")
    dp_events = read(SRC / "event/DifficultyEvents.java")
    dp_notify = read(SRC / "util/TpBossNotify.java")
    dp_pulse = dp.split("public static void pulse(", 1)[-1].split("\n    }\n", 1)[0]
    dp_show = dp.split("private static void showBar(", 1)[-1].split("\n    }\n", 1)[0]
    dp_hide = dp.split("private static void hideBar(", 1)[-1].split("\n    }\n", 1)[0]
    dp_clear = dp.split("private static void clearExpired(", 1)[-1].split("\n    }\n", 1)[0]
    dp_respawn = dp_events.split("public void onRespawn(", 1)[-1].split("\n    }\n", 1)[0]
    dp_clone = dp_events.split("DifficultyCache.putData(neu, data);", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest death-penalty-one-bar row", "death-penalty-one-bar" in manifest)
    check("one penalty bar: announcement and countdown on the same bar, no second notice bar",
          "TpBossNotify" not in dp and "ANNOUNCE.put(" in dp and dp.count("new ServerBossEvent(") == 1
          and 'EFFECT_LABEL + " - " + remainingLabel(player) + " left"' in dp
          and "Component.m_237113_(barText(player))" in dp_show)
    check("duration, effect, and messages unchanged",
          "DURATION_MS = 10L * 60L * 1000L;" in dp and 'EFFECT_LABEL = "Effect: x0.5";' in dp
          and "PENALTY_AMPLIFIER = -3;" in dp and "Death penalty ended. TP gain is back to normal." in dp
          and "Death penalty refreshed." in dp)
    check("pulse hides a bar with no timer; expiry hides; showBar never raises an inactive penalty",
          "if (BARS.containsKey(player.m_20148_())) {\n                    hideBar(player);" in dp_pulse
          and "clearExpired(player, true);" in dp_pulse and dp_clear.count("hideBar(player);") == 2
          and "if (!active(player)) {\n                return;" in dp_show)
    check("respawn instance swap: bar moves to the new player; hide removes every holder",
          "if (!bar.m_8324_().contains(player)) {\n                bar.m_7706_();\n                bar.m_6543_(player);" in dp_show
          and "bar.m_6539_(player);\n            bar.m_7706_();" in dp_hide)
    check("only a death stamps: clone needs wasDeath, respawn skips End-conquered",
          "if (event.isWasDeath()) {" in dp_clone and "DeathTpPenalty.tellLiving(neu);" in dp_clone
          and dp_respawn.index("if (event.isEndConquered()) {") < dp_respawn.index("DeathTpPenalty.tellLiving(player);"))
    check("TP award notice bar: 3 s window reset per grant, removed for all holders on expiry",
          "SHOW_MS = 3_000L;" in dp_notify and "held.hideAt = System.currentTimeMillis() + SHOW_MS;" in dp_notify
          and "held.event.m_7706_();" in dp_notify)

    # Mirror of DeathTpPenalty (single bar) and TpBossNotify, in ms. pulse every 1000 ms.
    DUR, ANN = 600_000, 3_000

    class _P:
        def __init__(self):
            self.until = 0
            self.bar = None  # (text, progress) or None
            self.ann = None
            self.online = True

    def _active(p, now):
        return p.until > now

    def _show(p, now):
        if not _active(p, now):
            return
        text = p.ann[0] if p.ann and now < p.ann[1] else "countdown"
        p.bar = (text, max(0.0, min(1.0, (p.until - now) / DUR)))

    def _hide(p):
        p.bar = None
        p.ann = None

    def _death(p, now):
        p.until = now + DUR
        p.ann = ("announce", now + ANN)
        _show(p, now)

    def _pulse(p, now):
        if p.until <= 0:
            if p.bar:
                _hide(p)
            return
        if p.until <= now:
            p.until = 0
            _hide(p)
            return
        _show(p, now)

    def _login(p, now):
        if not _active(p, now):
            p.until = 0
            _hide(p)
            return
        _show(p, now)

    pl = _P()
    _death(pl, 0)
    _pulse(pl, DUR - 500)
    on_before = pl.bar is not None
    _pulse(pl, DUR + 500)
    check("sim: penalty expiry while online hides the bar within one pulse", on_before and pl.bar is None)
    pl = _P()
    _death(pl, 0)
    _hide(pl)  # logout
    _login(pl, DUR + 60_000)
    check("sim: expiry while offline -> login shows no bar", pl.bar is None and pl.until == 0)
    pl = _P()
    _death(pl, 0)
    _hide(pl)
    _login(pl, 120_000)
    check("sim: logout/login with an active penalty -> exactly one bar, countdown text",
          pl.bar is not None and pl.bar[0] == "countdown" and abs(pl.bar[1] - 0.8) < 1e-9)
    pl = _P()
    _death(pl, 0)
    _pulse(pl, 300_000)
    half = pl.bar[1]
    _death(pl, 300_000)
    check("sim: death refresh restarts progress from full", abs(half - 0.5) < 1e-9 and pl.bar[1] == 1.0
          and pl.until == 900_000)
    pl = _P()
    _death(pl, 0)
    _pulse(pl, 2_000)
    ann_up = pl.bar[0] == "announce"
    _pulse(pl, 4_000)
    check("sim: announcement shows on the same bar, then the countdown", ann_up and pl.bar[0] == "countdown")

    def _respawn_stamps(was_death, end_conquered):
        return was_death and not end_conquered  # clone: wasDeath; respawn: not End-conquered

    check("sim: no non-death respawn stamps a penalty (End return)", not _respawn_stamps(False, True))

    def _flash(grants, until):
        hide_at, t, shown = None, 0, False
        for g in grants:
            hide_at = g + 3_000
        while t <= until:  # pulse every 250 ms (5 ticks)
            shown = hide_at is not None and t < hide_at and any(g <= t for g in grants)
            t += 250
        return shown

    check("sim: TP notice bar hides 3 s after the last grant", _flash([0], 3_250) is False and _flash([0], 2_750) is True)
    check("sim: continuous grants keep it up by design, then it hides 3 s after the last",
          _flash([0, 2_000, 4_000], 6_750) is True and _flash([0, 2_000, 4_000], 7_250) is False)

    print("\n--- § gui audit 4.6.248 ---")
    g_spec = read(SRC / "gui/ultra/UltraScreenSpec.java")
    g_rival = read(SRC / "gui/ultra/UltraRivalScreens.java")
    g_diff = read(SRC / "gui/ultra/UltraDifficultyScreens.java")
    g_prog = read(SRC / "gui/ProgressionGuiApi.java")
    g_spar = read(SRC / "gui/SparGuiApi.java")
    g_footer = g_spec.split("private void addFooterButton(", 1)[-1].split("\n    }\n", 1)[0]
    g_info = g_rival.split("private static void openInfo(", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest gui-audit-248 row", "gui-audit-248" in manifest)
    check("footer ink matches the system's body buttons (no forced dark gray)",
          'accentCode = "8";' not in g_footer
          and "UltraStyle.readableButtonInk(face, UltraStyle.systemAccentCode(screenId))" in g_footer)
    check("Rival has three tabs; stats open from Home and return to Rival Home",
          '.tabs("home|Home", "actions|Rival", "challenge|Challenge");' in g_rival
          and '"info|My rival stats"' in g_rival and 'sub("info")' in g_info
          and 'footerBackHome("Rival Home")' in g_info)
    check("event log: the turn-on action is never dim while the log is off",
          'logOn ? UltraStyle.confirm("Event log - ON") : "Turn event log on"' in g_diff
          and 'UltraStyle.dim("Event log - ON")' not in g_diff)
    check("no Invalid / Usage replies in progression boosts or spar mentor settings",
          "Invalid" not in g_prog and "Usage: spar do mentor_tpmsg" not in g_spar and "Pick Mentor TP - ON or OFF" in g_spar)

    print("\n--- § summon loot bonus ---")
    sl = read(SRC / "progression/boss/SummonLootBonus.java")
    sl_boss = read(SRC / "progression/boss/PlayerBossSummons.java")
    sl_end = read(SRC / "progression/end/EndDimensionStrength.java")
    sl_events = read(SRC / "event/DifficultyEvents.java")
    sl_cfg = read(SRC / "config/DifficultyConfig.java")
    sl_drops = sl_events.split("public void onLivingDrops(", 1)[-1].split("\n    }\n", 1)[0]
    sl_roll = sl_end.split("private static int rollEnderDragonLootTable(", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest summon-loot-bonus row", "summon-loot-bonus" in manifest)
    check("config summonLootBonusPerTierPercent default 25, clamped 0-100, registry + help",
          "public int summonLootBonusPerTierPercent = 25;" in sl_cfg
          and "cfg.summonLootBonusPerTierPercent = Math.max(0, Math.min(100, cfg.summonLootBonusPerTierPercent));" in sl_cfg
          and '"summonLootBonusPerTierPercent"' in read(SRC / "config/ConfigRegistry.java")
          and "summonLootBonusPerTierPercent=" in read(SRC / "config/editor/ConfigFieldHelp.java"))
    check("tier stamped once at summon for all three bosses",
          "!tag.m_128441_(TAG_TIER)" in sl
          and "SummonLootBonus.stampTier(mob, DifficultyCache.data(player).getActiveTier());" in sl_boss
          and "SummonLootBonus.stampTier(dragon, tier);" in sl_end)
    check("Wither/Warden: bonus at the drop stage (LOWEST), summoner stamp only, once per boss",
          "PlayerBossSummons.onDrops(event);" in sl_drops
          and "WitherBoss" in sl_drops and "Warden" in sl_drops
          and "Boss boss = stampedBoss(dead);" in sl_boss
          and "tag.m_128471_(TAG_DONE)" in sl and "drops.addAll(extra);" in sl)
    check("dragon: bonus copies on the single full-table roll, player summons only, guarded once",
          "isPlayerSummonedDragon(dragon)" in sl_roll and "SummonLootBonus.markDone(dragon)" in sl_roll
          and ".rollCopies(stacks, pct, end.f_46441_);" in sl_roll
          and sl_roll.index("rollCopies") < sl_roll.index("int spawned = 0;"))
    check("XP untouched; confirm pages and summon messages state the bonus",
          "awardVanillaDragonExperience" not in sl and "Experience" not in sl
          and "SummonLootBonus.describe(myTier, boss.minTier)" in sl_boss
          and "SummonLootBonus.describe(tier, boss.minTier)" in sl_boss
          and "summonLootBonusLine(player)" in read(SRC / "gui/ultra/UltraDifficultyScreens.java")
          and "SummonLootBonus.describe(\n                        tier, PLAYER_SUMMON_MIN_TIER)" in sl_end)

    # Mirror of SummonLootBonus.percent / rollCopies.
    def _pct(tier, min_tier, per=25):
        return max(0, min(100, (tier - min_tier) * max(0, min(100, per))))

    check("sim: Wither T3 0%, T4 25%, T5 50%, T6 75%, T7 100% (second nether star guaranteed)",
          [_pct(t, 3) for t in range(3, 8)] == [0, 25, 50, 75, 100])
    check("sim: Warden T4 0%, T5 25%, T6 50%, T7 75%",
          [_pct(t, 4) for t in range(4, 8)] == [0, 25, 50, 75])
    check("sim: Dragon T4 0% up to T7 75%", [_pct(t, 4) for t in range(4, 8)] == [0, 25, 50, 75])
    sl_once = sl.split("static boolean rollOnce(", 1)[-1].split("\n    }\n", 1)[0]
    sl_copies = sl.split("public static List<ItemStack> rollCopies(", 1)[-1].split("\n    }\n", 1)[0]
    sl_apply = sl.split("public static int applyToDrops(", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest summon-loot-single-roll row", "summon-loot-single-roll" in manifest)
    check("one shared roll per kill: rollOnce is the only nextInt, no roll at pct <= 0",
          "if (pct <= 0 || rng == null) {" in sl_once
          and "return pct >= 100 || rng.m_188503_(100) < pct;" in sl_once
          and sl.count("m_188503_") == 1)
    check("Wither/Warden: one roll with the entity random, then every drop copied once",
          "if (!rollOnce(pct, boss.m_217043_())) {" in sl_apply
          and sl_apply.index("rollOnce(") < sl_apply.index("for (ItemEntity drop : drops)")
          and "stack.m_41777_()" in sl_apply and "m_41764_" not in sl)
    check("dragon: one roll with the level random, then every stack copied once",
          "!rollOnce(pct, rng)" in sl_copies
          and sl_copies.index("rollOnce(") < sl_copies.index("for (ItemStack stack : stacks)")
          and "out.add(stack.m_41777_());" in sl_copies)
    check("confirm copy states one roll per kill (no per-drop wording)",
          "% chance per kill to double every drop." in sl
          and "each drop" not in sl and "each drop" not in sl_boss and "each drop" not in sl_end)
    import random as _r
    _rng = _r.Random(7)
    _stacks = ["star", "simplymore", "tendril", "simplyswords_unique"]
    _trials = 20000

    def _roll_once(pct, rng):
        return pct > 0 and (pct >= 100 or rng.randrange(100) < pct)

    _kills = [len(_stacks) if _roll_once(75, _rng) else 0 for _ in range(_trials)]
    check("sim: one roll per kill (75% -> all-or-nothing, ~75% of kills doubled)",
          set(_kills) <= {0, len(_stacks)} and abs(sum(1 for k in _kills if k) / _trials - 0.75) < 0.01)
    check("sim: 0% never rolls, 100% always doubles",
          not _roll_once(0, _rng) and all(_roll_once(100, _rng) for _ in range(100)))
    check("sim: natural bosses (no stamp) get nothing", _pct(-1, 3) == 0)

    print("\n--- § lm-owned player data ---")
    pd_store = read(SRC / "data/LmPlayerStore.java")
    pd_mig = read(SRC / "data/LmPlayerDataMigration.java")
    pd_cache = read(SRC / "cache/DifficultyCache.java")
    pd_prog = read(SRC / "progression/ProgressionData.java")
    pd_events = read(SRC / "event/DifficultyEvents.java")
    pd_start = pd_events.split("public void onServerStarting(", 1)[-1].split("\n    }\n", 1)[0]
    pd_clone = pd_events.split("public void onClone(", 1)[-1].split("\n    }\n", 1)[0]
    pd_owned = {
        "cache/DifficultyCache.java": pd_cache,
        "progression/ProgressionData.java": pd_prog,
        "progression/shop/PrestigeSystem.java": read(SRC / "progression/shop/PrestigeSystem.java"),
        "progression/shop/PrestigePointsSystem.java": read(SRC / "progression/shop/PrestigePointsSystem.java"),
        "progression/PersonalLevelCapMirror.java": read(SRC / "progression/PersonalLevelCapMirror.java"),
        "rival/NemesisSpawnMode.java": read(SRC / "rival/NemesisSpawnMode.java"),
    }
    check("manifest lm-owned-player-data row", "lm-owned-player-data" in manifest)
    check("store: one JSON file per UUID under config/legacymechanics/players, exact SNBT per record",
          'ConfigPaths.dataDir().resolve("players")' in pd_store and '+ ".json"' in pd_store
          and "blob.records.put(key, value.toString());" in pd_store and 'TagParser.m_129359_("{v:" + snbt + "}")' in pd_store)
    durable = ["PlayerDifficultyData.NBT_ROOT", '"lm_progression"', '"prestige_total_completed"',
               '"prestige_total_staff_override"', '"prestige_need_floor"', '"lm_prestige_held"',
               '"lm_prestige_points"', '"pp_level_breakthroughs"', '"lm_personal_level_cap"',
               '"lm_personal_breakthroughs"', '"lmNemesisSpawnMode"']
    pd_keys = pd_store.split("DURABLE_KEYS = Set.of(", 1)[-1].split(");", 1)[0]
    check("store: every durable record key is listed for the bulk pass",
          all(k in pd_keys for k in durable))
    check("durable record owners never read or write Forge persistent data",
          all("PersistentDataAccess.get(player)" not in s and "PersistentDataAccess.putInt(player" not in s
              and "PersistentDataAccess.putLong(player" not in s and "PersistentDataAccess.getInt(player" not in s
              and "PersistentDataAccess.getLong(player" not in s for s in pd_owned.values())
          and "LmPlayerStore.root(" in pd_cache and "LmPlayerStore.root(player)" in pd_prog
          and "writeToPlayerNbt(neuTag)" not in pd_clone)
    check("migration: once at server start before joins, marker-guarded, playerdata from the running server",
          "LmPlayerDataMigration.runIfNeeded(event.getServer());" in pd_start
          and pd_start.index("LmPlayerStore.clearCache();") < pd_start.index("runIfNeeded")
          and "if (Files.exists(marker)) {" in pd_mig
          and "server.m_129843_(LevelResource.f_78176_)" in pd_mig and '"world"' not in pd_mig)
    check("migration: unreadable skipped, no-data skipped without a file, never overwrites, old keys kept",
          "unreadable ({}); skipped" in pd_mig and "result.noData++;" in pd_mig
          and "if (Files.exists(path(id))) {" in pd_store
          and "m_128473_" not in pd_mig and "m_128944_" not in pd_mig)
    check("no per-login migration; flushed on save, logout, timer, and stop",
          "LmPlayerDataMigration" not in pd_events.replace("LmPlayerDataMigration.runIfNeeded(event.getServer());", "")
          and "LmPlayerStore.flush(player.m_20148_());" in pd_events
          and "LmPlayerStore.unload(player.m_20148_());" in pd_events
          and "LmPlayerStore::flushAll" in pd_events and "LmPlayerStore.flushAll();" in pd_events)

    def _mig_sim(forge, has_file=False, marker=False):
        if marker:
            return "skip-pass"
        rec = {k: v for k, v in forge.items() if k in {"lm_prestige_held", "prestige_total_completed",
                                                        "dmz_adaptive_difficulty", "lm_progression"}}
        if not rec:
            return "no-file"
        return "kept-existing" if has_file else rec

    check("sim: migration copies only LM keys, skips empty players, keeps existing files, second boot skips",
          _mig_sim({"lm_prestige_held": 2, "dmz_ad_scaled": 1}) == {"lm_prestige_held": 2}
          and _mig_sim({"dmz_ad_scaled": 1}) == "no-file"
          and _mig_sim({"lm_prestige_held": 2}, has_file=True) == "kept-existing"
          and _mig_sim({"lm_prestige_held": 2}, marker=True) == "skip-pass")

    print("\n--- § unlocked tiers load ---")
    ut_data = read(SRC / "data/PlayerDifficultyData.java")
    ut_tp = read(SRC / "title/TitleProgress.java")
    ut_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    ut_load = ut_data.split("public void load(CompoundTag tag) {", 1)[-1].split("\n    }\n", 1)[0]
    ut_tp_load = ut_tp.split("public void load(CompoundTag tag) {", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest unlocked-tiers-load row", "unlocked-tiers-load" in manifest)
    check("unlockedTiers: contains() checks TAG_LIST (9), getList keeps compound elements (10)",
          'tag.m_128425_("unlockedTiers", 9)' in ut_load and 'tag.m_128437_("unlockedTiers", 10)' in ut_load
          and 'tag.m_128425_("unlockedTiers", 10)' not in ut_data
          and 'tag.m_128365_("unlockedTiers", tiers);' in ut_data)
    import re as _re
    _lists = _re.findall(r'm_128425_\("([A-Za-z]+)", (\d+)\)', ut_load + ut_tp_load)
    check("every list key in difficulty / title-progress load checks TAG_LIST (9)",
          sorted(_lists) == sorted([("unlockedTiers", "9"), ("titles", "9"),
                                    ("mutationsSeen", "9"), ("scoreMilestones", "9")]))
    check("self-test round-trips unlocked tiers through save, SNBT text, and load",
          "before.unlockTier(id);" in ut_test and "unlocked tiers lost on save/load" in ut_test
          and ut_test.index("before.unlockTier(id);") < ut_test.index("before.writeToPlayerNbt(fresh);"))

    print("\n--- § global mastery boost ---")
    gm = read(SRC / "progression/tp/GlobalMasteryBoost.java")
    gt = read(SRC / "progression/tp/GlobalTpBoost.java")
    gm_sys = read(SRC / "progression/ProgressionSystem.java")
    gm_cfg = read(SRC / "progression/ProgressionConfig.java")
    gm_cmd = read(SRC / "command/ProgressionCommands.java")
    gm_events = read(SRC / "event/DifficultyEvents.java")
    gm_start = gm_events.split("public void onServerStarting(", 1)[-1].split("\n    }\n", 1)[0]
    gm_stop = gm_events.split("public void onServerStopping(", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest global-mastery-boost row", "global-mastery-boost" in manifest)
    check("mastery boost applies DMZ MASTERY_GAIN, persisted to its own file; no new mixin",
          "MainEffects.MASTERY_GAIN.get()" in gm and "TP_GAIN" not in gm
          and 'resolve("global-mastery-boost.json")' in read(SRC / "config/ConfigPaths.java")
          and "ConfigPaths.globalMasteryBoostPath()" in gm
          and not any("MASTERY_GAIN" in p.read_text(encoding="utf-8", errors="ignore")
                      for p in (SRC / "mixin").rglob("*.java")))
    _same = ["ENCODED_MINUTE_DIVISOR = 10_000", "EFFECT_REFRESH_BUFFER_TICKS = 100",
             "TRIGGER_LOCK_MS = 3000L", "END_LOCK_MS = 5000L",
             "int amplifier = (int) Math.round(((multiplier - 1.0) / 0.25) - 1.0);",
             "double keptMult = extending ? Math.max(prevMult, multiplier) : multiplier;",
             "long newEnd = extending ? Math.max(now, currentEnd) + durationMs : now + durationMs;",
             "if (DeathTpPenalty.active(player)) {", "player.m_21195_(effect);",
             "(int) Math.ceil(remainingMs / 50.0) + EFFECT_REFRESH_BUFFER_TICKS",
             "sig.equals(TRIGGER_SIGNATURE.get())"]
    check("same window, debounce, encoding, stacking, and death-penalty guard as the TP boost",
          all(s in gm and s in gt for s in _same)
          and gm.count("if (DeathTpPenalty.active(player)) {") == 2)
    check("broadcasts mirror the TP boost wording; telemetry start/end events",
          '"GLOBAL MASTERY BOOST ACTIVATED!"' in gm and '"GLOBAL MASTERY BOOST "' in gm
          and "(highest wins; lower packs only add time)." in gm
          and '"mastery_boost_start"' in gm and gm.count('"mastery_boost_start"') == 2
          and '"mastery_boost_end"' in gm)
    check("config flag enableGlobalMasteryBoost (default true like the TP flag) gates start, pulse, login",
          "public boolean enableGlobalMasteryBoost = true;" in read(SRC / "config/DifficultyConfig.java")
          and "public boolean enableGlobalTpBoost = true;" in read(SRC / "config/DifficultyConfig.java")
          and "DifficultyConfig.get().enableGlobalMasteryBoost" in gm_cfg
          and gm.count("if (!ProgressionConfig.globalMasteryBoost()") == 4
          and '"enableGlobalMasteryBoost"' in read(SRC / "config/ConfigRegistry.java")
          and "enableGlobalMasteryBoost=" in read(SRC / "config/editor/ConfigFieldHelp.java"))
    check("loaded on server start, flushed on stop, pulsed and applied on login",
          "GlobalMasteryBoost.load();" in gm_start and "GlobalMasteryBoost.save();" in gm_stop
          and "GlobalMasteryBoost.pulse(server, tick);" in gm_sys
          and "GlobalMasteryBoost.onLogin(player);" in gm_sys)
    check("commands: masteryboost status / start code / start mult minutes / end beside boost",
          'Commands.m_82127_("masteryboost")' in gm_cmd
          and gm_cmd.index('Commands.m_82127_("boost")') < gm_cmd.index('Commands.m_82127_("masteryboost")')
          and "masteryBoostEncoded(" in gm_cmd and "masteryBoostMinutes(" in gm_cmd
          and "masteryBoostEnd(" in gm_cmd and "masteryBoostStatus(" in gm_cmd)
    check("status line beside the TP boost line in GUI home, Boosts tab, legacy lore, and chat menu",
          "GlobalMasteryBoost.statusLine();" in gm_sys
          and '"mastery_boost"' in read(SRC / "gui/ProgressionGuiApi.java")
          and '"mastery_boost"' in read(SRC / "gui/ultra/UltraProgressionScreens.java")
          and "GlobalMasteryBoost.statusLine()" in read(SRC / "gui/ProgressionChatMenu.java"))
    check("mastery boost copy: no raw section signs, no Invalid, errors say what to do",
          "\u00a7" not in gm and "Invalid" not in gm and "Turn it on in the progression settings first." in gm)

    def _amp(m):
        if not m >= 1.25:
            return -1
        a = round(((m - 1.0) / 0.25) - 1.0)
        return a if abs(1.0 + (a + 1) * 0.25 - m) <= 0.0001 and a >= 0 else -1

    check("sim: 1.25x->0, 1.5x->1, 2x->3, 3x->7; 1.1x and 1.3x rejected",
          [_amp(m) for m in (1.25, 1.5, 2.0, 3.0, 1.1, 1.3)] == [0, 1, 3, 7, -1, -1])

    def _stack(active_mult, active_left, new_mult, new_min):
        return max(active_mult, new_mult), active_left + new_min

    check("sim: stacking a lower pack adds time and keeps the higher multiplier",
          _stack(2.0, 20, 1.5, 30) == (2.0, 50) and _stack(1.5, 20, 2.0, 30) == (2.0, 50))

    print("\n--- § boosts selector ---")
    bs_scr = read(SRC / "gui/ultra/UltraProgressionScreens.java")
    bs_act = read(SRC / "gui/ultra/UltraGuiActions.java")
    bs_api = read(SRC / "gui/ProgressionGuiApi.java")
    bs_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    bs_sel = bs_scr.split("static UltraScreenSpec boostsSelectorSpec(", 1)[-1].split("\n    }\n", 1)[0]
    bs_view = bs_scr.split("static UltraScreenSpec boostControlSpec(", 1)[-1].split("\n    }\n", 1)[0]
    bs_mb = bs_api.split("public static String masteryBoost(ServerPlayer actor, String argsJoined) {", 1)[-1]
    bs_mb = bs_mb.split("\n    }\n", 1)[0]
    bs_tp = bs_api.split("public static String boost(ServerPlayer actor, String argsJoined) {", 1)[-1]
    bs_tp = bs_tp.split("\n    }\n", 1)[0]
    check("manifest boosts-selector row", "boosts-selector" in manifest)
    check("selector: both status lines, TP Boost + Mastery Boost buttons, Back to main menu; tabs unchanged",
          "tpBoostLine(ph)" in bs_sel and "masteryBoostLine(ph)" in bs_sel
          and '"page:" + PAGE_TP_BOOST, "TP Boost"' in bs_sel
          and '"page:" + PAGE_MASTERY_BOOST, "Mastery Boost"' in bs_sel
          and "spec.footerBackToMain();" in bs_sel
          and '.tabs("home|Home", "boosts|Boosts", "coins|Coins")' in bs_scr)
    _presets = ['act + "1.25:30"', 'act + "1.5:30"', 'act + "2:30"', 'act + "2:60"', 'act + "3:30"']
    check("control view: own status line, five confirm presets, danger End, Back to Boosts",
          all(p in bs_view for p in _presets) and bs_view.count('"confirm")') == 5
          and 'act + "end", UltraStyle.danger("End boost"), "danger"' in bs_view
          and 'String act = mastery ? "do:masteryboost:" : "do:boost:";' in bs_view
          and "mastery ? masteryBoostLine(ph) : tpBoostLine(ph)" in bs_view
          and 'spec.footerBack("page:boosts", "Boosts");' in bs_view)
    check("pages boosts_tp / boosts_mastery open the views; actions land back on their own view",
          'case PAGE_TP_BOOST -> openBoostControls(player, false);' in bs_scr
          and 'case PAGE_MASTERY_BOOST -> openBoostControls(player, true);' in bs_scr
          and 'case "boost" -> UltraProgressionScreens.PAGE_TP_BOOST;' in bs_act
          and 'case "masteryboost", "globalmasteryboost" -> UltraProgressionScreens.PAGE_MASTERY_BOOST;' in bs_act)
    def _hb(s):
        x = 0
        for c in s:
            x = (31 * x + ord(c)) & 0xFFFFFFFF
        return x.to_bytes(4, "big")
    check("new switch strings carry no C2 A7 bytes in their hash",
          all(b"\xc2\xa7" not in _hb(s) for s in ("boosts_tp", "boosts_mastery", "masteryboost", "globalmasteryboost")))
    check("handleDo routes masteryboost to masteryBoost(); staff maintenance list includes it",
          'if ("masteryboost".equals(act) || "globalmasteryboost".equals(act)) {\n            return masteryBoost(player, a);' in bs_api.replace("\r\n", "\n")
          and '|| "masteryboost".equals(act) || "globalmasteryboost".equals(act)' in bs_api)
    check("masteryBoost mirrors boost(): gates, status/help, end, encoded:, mult:minutes, space forms",
          "!StaffAccess.isStaff(actor)" in bs_mb and "enableProgression" in bs_mb
          and '"help".equalsIgnoreCase(raw)' in bs_mb and 'startsWith("encoded:")' in bs_mb
          and 'raw.contains(":")' in bs_mb and '"start".equals(head)' in bs_mb
          and bs_mb.count("ProgressionSystem.masteryBoostEnd()") == 2
          and bs_mb.count("ProgressionSystem.masteryBoostStartEncoded(") == 2
          and bs_mb.count("ProgressionSystem.masteryBoostStart(") == 2
          and "ProgressionSystem.boost" not in bs_mb)
    check("mastery confirm keys are their own (masteryboost:), TP keys unchanged",
          bs_mb.count('"masteryboost:end"') == 2 and bs_mb.count('"masteryboost:encoded:"') == 2
          and bs_mb.count('"masteryboost:" + mult') == 2 and '"boost:' not in bs_mb
          and bs_tp.count('"boost:end"') == 2 and "masteryboost" not in bs_tp)
    check("mastery copy: no raw section signs, no Invalid, errors say what to do",
          "\u00a7" not in bs_mb and "Invalid" not in bs_mb and "Ask a staff member" in bs_mb
          and "Turn it on in the progression settings first." in bs_mb)
    check("self-test covers selector, both views, presets + End, and back navigation",
          "checkBoostsScreens(broken);" in bs_test and "boostsSelectorSpec(ph)" in bs_test
          and "boostControlSpec(ph, mastery)" in bs_test and '"Back to Boosts"' in bs_test
          and '"Back to main menu"' in bs_test
          and 'List.of("1.25:30", "1.5:30", "2:30", "2:60", "3:30", "end")' in bs_test)

    print("\n--- § mastery points wallet ---")
    mw = read(SRC / "progression/shop/MasteryWallet.java")
    mw_cfg = read(SRC / "progression/shop/PrestigeShopConfig.java")
    mw_hooks = read(SRC / "progression/shop/PrestigeShopHooks.java")
    mw_scr = read(SRC / "gui/ultra/UltraPrestigeScreens.java")
    mw_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    mw_lines = mw.split("public static List<Line> lines(ServerPlayer player) {", 1)[-1].split("\n    }\n", 1)[0]
    mw_buy = mw.split("public static String buy(ServerPlayer player, String arg) {", 1)[-1].split("\n    }\n", 1)[0]
    mw_apply = mw.split("public static String apply(ServerPlayer player, String arg) {", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest mastery-points-wallet row", "mastery-points-wallet" in manifest)
    check("fixed economy: 1 favor = 10 points, 1 point = 50 mastery, packs 1/2/5/10",
          "POINTS_PER_FAVOR = 10;" in mw and "MASTERY_PER_POINT = 50;" in mw
          and "FAVOR_PACKS = {1, 2, 5, 10};" in mw)
    check("balance lives only in the LM player file (lm_mastery_points via LmPlayerStore)",
          'KEY = "lm_mastery_points";' in mw and "LmPlayerStore.putLong(player, KEY," in mw
          and "LmPlayerStore.getLong(player, KEY," in mw and "LmPlayerStore.flush(" in mw
          and "getPersistentData" not in mw and "m_128469_" not in mw and "BonusStats" not in mw.split("*/", 1)[-1]
          and "StatsData" not in mw.split("public static int balance(", 1)[-1].split("public static boolean open()", 1)[0])
    check("config section masteryWallet.enabled gates selling; Shop button kept while points remain",
          "public MasteryWalletShop masteryWallet = new MasteryWalletShop();" in mw_cfg
          and "prestigeShopConfig.masteryWallet = new MasteryWalletShop();" in mw_cfg
          and "PrestigeShopConfig.get().masteryWallet.enabled" in mw and "if (!open())" in mw_buy
          and "cfg.masteryWallet.enabled || MasteryWallet.balance(player) > 0" in mw_scr
          and '"masteryWallet.enabled"' in mw_hooks)
    check("buy: standard favor wording, shop confirm, favor taken then points added",
          '"Need " + favor + " favor (have " + have + ")."' in mw_buy
          and 'GuiClickConfirm.confirmed(player.m_20148_(), "mwallet-buy:"' in mw_buy
          and mw_buy.index("GuiClickConfirm.confirmed") < mw_buy.index("PrestigePointsSystem.setPoints(player, have - favor);")
          and "not refunded" in mw.split("public static String buyPrompt(", 1)[-1].split("}", 1)[0])
    check("picker is live: radial group walk + getUnlockedForms / getUnlockedStackForms, no selectability filter",
          "TransformationsHelper.getUnlockedForms(data, race, group)" in mw_lines
          and "TransformationsHelper.getUnlockedStackForms(data, group)" in mw_lines
          and "isSkillAllowedForRace(TransformationsHelper.getSkillNameForType(type), race)" in mw_lines
          and "isSkillAllowedForRace(group, race)" in mw_lines
          and "getSelectable" not in mw.split("*/", 2)[-1] and "return ordered(raw);" in mw_lines)
    check("radial order and hidden empty lines; names from DMZ lang",
          "!l.forms.isEmpty()" in mw and "comparingInt(l -> l.kind)" in mw
          and "String.CASE_INSENSITIVE_ORDER" in mw
          and 't.contains("super") || t.contains("legendary") || t.contains("android")' in mw
          and 'DmzLang.text("race.dragonminez." + raceKey + ".form."' in mw_lines
          and 'DmzLang.text("race.dragonminez.stack.group."' in mw_lines)
    check("apply: whole points, headroom / 50 floor, balance bound, confirm, addMastery with cap, sync",
          "Math.floor(room / MASTERY_PER_POINT + 1.0E-9)" in mw
          and "points > fit" in mw_apply and "points > balance" in mw_apply
          and 'GuiClickConfirm.confirmed(player.m_20148_(), key)' in mw_apply
          and "line.stack ? ch.getStackFormMasteries() : ch.getFormMasteries()" in mw_apply
          and "masteries.addMastery(line.group, form.name, (double) points * MASTERY_PER_POINT, form.cap);" in mw_apply
          and "DmzSkillUtil.sync(player);" in mw_apply
          and mw_apply.index("GuiClickConfirm.confirmed") < mw_apply.index("masteries.addMastery("))
    check("apply confirm states points, form, resulting mastery, and no refund",
          all(s in mw.split("public static String applyPrompt(", 1)[-1].split("\n    }\n", 1)[0]
              for s in ('" mastery point"', "formName(lineLabel, form)", "resulting(form.current, points, form.cap)",
                        "Applied mastery is not refunded.")))
    check("routes and dispatch: mastery_wallet / mastery_lines / mw_line: / mw_form:, mastery_buy / mastery_apply",
          'case "mastery_wallet", "mastery" -> openMasteryWallet(player);' in mw_scr
          and '"mastery_lines".equals(UltraPickHelper.pageBase(p))' in mw_scr
          and 'p.startsWith("mw_line:")' in mw_scr and 'p.startsWith("mw_form:")' in mw_scr
          and 'case "mastery_buy" -> MasteryWallet.buy(serverPlayer, string2);' in mw_hooks
          and 'case "mastery_apply" -> MasteryWallet.apply(serverPlayer, string2);' in mw_hooks)
    check("empty picker says there are no unlocked forms and points at the next step",
          "You have no unlocked forms yet" in mw_scr and "Unlock your first form through its form skill" in mw_scr)
    check("wallet copy: no raw section signs, no Invalid",
          "§" not in mw and "Invalid" not in mw
          and "§" not in mw_scr.split("// ── Mastery wallet", 1)[-1].split("/** Exchange current prestige", 1)[0])
    check("self-test: balance, buy confirm, cap math, whole-point limit, picker for a known unlock state",
          "checkMasteryWallet(broken);" in mw_test and "walletSpec(37, 4, true)" in mw_test
          and "mwallet-buy:2:37" in mw_test and "maxPointsFor(c[0], c[1])" in mw_test
          and '"do:mastery_apply:n:ssgrades:supersaiyan:1"' in mw_test
          and 'List.of("n:ssgrades", "n:supersaiyan", "n:oozaru", "s:kaioken")' in mw_test)

    def _fit(cur, cap):
        room = cap - cur
        return 0 if room <= 0 else int(room // 50)

    check("sim: 0/500 -> 10, 450/500 -> 1, 451/500 -> 0, 500/500 -> 0, 101/500 -> 7",
          [_fit(*c) for c in ((0, 500), (450, 500), (451, 500), (500, 500), (101, 500))] == [10, 1, 0, 0, 7])

    print("\n--- § mastery wallet paging ---")
    pg_scr = read(SRC / "gui/ultra/UltraPrestigeScreens.java")
    pg_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    pg_lines = pg_scr.split("static UltraScreenSpec linesSpec(", 1)[-1].split("\n    }\n", 1)[0]
    pg_line = pg_scr.split("static UltraScreenSpec lineSpec(", 1)[-1].split("\n    }\n", 1)[0]
    pg_form = pg_scr.split("static UltraScreenSpec formSpec(", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest mastery-wallet-paging row", "mastery-wallet-paging" in manifest)
    check("page size 8 for both pickers",
          "static final int MASTERY_PICKS_PER_PAGE = 8;" in pg_scr
          and "MASTERY_PICKS_PER_PAGE" in pg_lines and "MASTERY_PICKS_PER_PAGE" in pg_line)
    check("both pickers: pageCount, clampPage, pageSlice, pageLabel, placePrevNext",
          all(s in pg_lines and s in pg_line for s in (
              "UltraPickHelper.pageCount(", "UltraPickHelper.clampPage(page, pages)",
              "UltraPickHelper.pageSlice(", "UltraPickHelper.pageLabel(spec, pg, pages);",
              "UltraPickHelper.placePrevNext(spec, "))
          and 'UltraPickHelper.placePrevNext(spec, "mastery_lines", pg, pages);' in pg_lines
          and "UltraPickHelper.placePrevNext(spec, base, pg, pages);" in pg_line
          and "for (MasteryWallet.Form form : line.forms)" not in pg_line
          and "for (MasteryWallet.Line line : lines)" not in pg_lines)
    check("routes: mastery_lines[:N], mw_line:<token>[:N] via pageBase / pageIndex",
          '"mastery_lines".equals(UltraPickHelper.pageBase(p))' in pg_scr
          and "openMasteryLines(player, UltraPickHelper.pageIndex(p));" in pg_scr
          and "MasteryWallet.line(player, UltraPickHelper.pageBase(rest))" in pg_scr
          and 'sub(UltraPickHelper.pageArg("mastery_lines", pg))' in pg_lines
          and "sub(UltraPickHelper.pageArg(base, pg))" in pg_line)
    check("form view id and Back carry the line page",
          'UltraPickHelper.pageArg("mw_form:" + line.token() + ":" + form.name, pg)' in pg_line
          and 'sub(UltraPickHelper.pageArg("mw_form:" + token, Math.max(0, linePage)))' in pg_form
          and 'footerBack("page:" + UltraPickHelper.pageArg("mw_line:" + line.token(), Math.max(0, linePage)), line.label);' in pg_form)
    check("empty state unchanged; hidden empty lines unchanged",
          "You have no unlocked forms yet, so there is nothing to apply points to." in pg_lines
          and "!l.forms.isEmpty()" in read(SRC / "progression/shop/MasteryWallet.java"))
    check("self-test: 11 lines and 11 forms split 8 + 3, Prev / Next, clamp, Back carries page",
          "checkMasteryWalletPaging(broken);" in pg_test and "i < 11" in pg_test
          and "int want = pg == 0 ? 8 : 3;" in pg_test and "linesSpec(MasteryWallet.ordered(many), 5, 99)" in pg_test
          and '"page:mw_line:n:big:1"' in pg_test)

    def _pages(n, per=8):
        out = []
        for pg in range(max(1, -(-n // per))):
            out.append(list(range(n))[pg * per:(pg + 1) * per])
        return out

    check("sim: 0 -> 1 empty page, 8 -> 1 page, 9 -> 8 + 1, 17 -> 8 + 8 + 1, no repeats",
          [len(p) for p in _pages(0)] == [0] and [len(p) for p in _pages(8)] == [8]
          and [len(p) for p in _pages(9)] == [8, 1] and [len(p) for p in _pages(17)] == [8, 8, 1]
          and sum(_pages(17), []) == list(range(17)))

    print("\n--- § dat nbt strip ---")
    ds = read(SRC / "data/LmPlayerDatStrip.java")
    ds_events = read(SRC / "event/DifficultyEvents.java")
    ds_doc = (ROOT / "docs" / "LM_PLAYER_DATA.md").read_text(encoding="utf-8")
    ds_copy = ds_events.split("private static void copyLmPersistentData(", 1)[-1].split("\n    }\n", 1)[0]
    ds_start = ds_events.split("public void onServerStarting(", 1)[-1].split("\n    }\n", 1)[0]
    ds_file = ds.split("static void stripFile(", 1)[-1].split("\n    }\n", 1)[0]
    _frozen = ["dmz_adaptive_difficulty", "lm_progression", "prestige_total_completed",
               "prestige_total_staff_override", "prestige_need_floor", "lm_prestige_held", "lm_prestige_points",
               "pp_level_breakthroughs", "lm_personal_level_cap", "lm_personal_breakthroughs", "lmNemesisSpawnMode",
               "lm_prestige_confirm_until", "lm_cnpc_player_migrated", "lm_shadow_dummy_cd_until"]
    _keys_block = ds.split("FROZEN_KEYS = ", 1)[-1].split(")));", 1)[0]
    check("manifest dat-nbt-strip row", "dat-nbt-strip" in manifest)
    check("exactly the 14 frozen keys; death-penalty keys never in the list",
          all(('"' + k + '"') in _keys_block for k in _frozen[1:]) and "PlayerDifficultyData.NBT_ROOT" in _keys_block
          and _keys_block.count('"') == 2 * 13 and "death_tp_penalty" not in _keys_block
          and "FROZEN_KEYS.size() != 14" in ds)
    check("runs at server start after the migration, marker-guarded",
          ds_start.index("LmPlayerDataMigration.runIfNeeded") < ds_start.index("LmPlayerDatStrip.runIfNeeded")
          and 'resolve("player-data-nbt-strip.json")' in ds and "if (Files.exists(marker))" in ds)
    check("strip only with a store file; skip untouched otherwise; unreadable skipped",
          "Files.isRegularFile(store)" in ds_file and "if (!storeExists) {" in ds_file
          and ds_file.index("if (!storeExists) {") < ds_file.index("strip(root, true);")
          and "left untouched" in ds_file)
    check("backup first, never overwritten; .dat via temp + atomic move; gzip NBT IO",
          "if (!Files.exists(backup)) {" in ds_file
          and ds_file.index("NbtIo.m_128944_(backupOf(root)") < ds_file.index("strip(root, true);")
          and "NbtIo.m_128937_(file.toFile())" in ds_file and "NbtIo.m_128944_(root, tmp.toFile());" in ds_file
          and "StandardCopyOption.ATOMIC_MOVE" in ds_file and "LmPlayerStore.root(" not in ds)
    check("ForgeData root only; nested compounds untouched",
          'root.m_128425_("ForgeData", 10)' in ds and "forge.m_128473_(key);" in ds
          and "PlayerPersisted" not in ds.split("// ── Self-test", 1)[0])
    check("death copy carries only the two penalty keys",
          ds_copy.count("copyTagIfPresent(") == 2 and "DeathTpPenalty.KEY" in ds_copy
          and "DeathTpPenalty.FRAC_KEY" in ds_copy and "lm_progression" not in ds_copy)
    check("self-test gates the pass: strip, unrelated bytes, no-store skip, file round-trip",
          ds.index("List<String> broken = selfTest();") < ds.index("Files.newDirectoryStream(dir")
          and "unrelated tags changed in the strip" in ds and "a skipped player must be left untouched" in ds
          and "player with no store file was modified" in ds and "fileRoundTrip(broken);" in ds)
    check("totals logged and in the marker (scanned, stripped, skipped, noData)",
          all(("m." + f + " = result." + f) in ds for f in ("scanned", "stripped", "skipped", "noData")))
    check("docs: what it removes, marker, backup location, no .dat fallback after it runs",
          "player-data-nbt-strip.json" in ds_doc and "player-data-nbt-strip-backup/<uuid>.dat" in ds_doc
          and "After it runs there is no `.dat` fallback." in ds_doc and "lm_shadow_dummy_cd_until" in ds_doc)

    def _strip(forge, store):
        keys = [k for k in _frozen if k in forge]
        if not keys:
            return "no_data", forge
        if not store:
            return "skipped", forge
        return "stripped", {k: v for k, v in forge.items() if k not in _frozen}

    _f = {k: 1 for k in _frozen}
    _f.update({"PlayerPersisted": {"a": 1}, "lm.death_tp_penalty_until": 9})
    check("sim: stripped keeps unrelated + penalty, no store -> untouched, none -> no-op",
          _strip(_f, True) == ("stripped", {"PlayerPersisted": {"a": 1}, "lm.death_tp_penalty_until": 9})
          and _strip(_f, False) == ("skipped", _f) and _strip({"x": 1}, True)[0] == "no_data")

    print("\n--- § boost encoded reject ---")
    be_tp = read(SRC / "progression/tp/GlobalTpBoost.java")
    be_gm = read(SRC / "progression/tp/GlobalMasteryBoost.java")
    be_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    check("manifest boost-encoded-reject row", "boost-encoded-reject" in manifest)
    for _name, _src, _cmd in (("TP", be_tp, "boost"), ("mastery", be_gm, "masteryboost")):
        _start = _src.split("public static String startBoost(ServerPlayer actor, int encoded, String purchaser) {", 1)[-1]
        _start = _start.split("\n    }\n", 1)[0]
        # 4.6.261: the int form delegates to the String form, which builds the reply.
        _reply = _src.split("public static String undecodedReply(String encoded) {", 1)[-1].split("\n    }\n", 1)[0]
        check(_name + " boost: undecodable value gets undecodedReply with the value and both forms",
              "if (decoded == null) {\n            return undecodedReply(encoded);" in _start
              and '" was not understood. Use one of these:' in _reply and "+ encoded +" in _reply
              and ('"progression ' + _cmd + ' start <encoded>"') in _reply and '"2000030"' in _reply
              and ('"progression ' + _cmd + ' start <multiplier> <minutes>"') in _reply
              and ('"progression ' + _cmd + ' start 2 30"') in _reply
              and "§" not in _reply and "Invalid" not in _src)
        check(_name + " boost: decode and activate checks unchanged",
              "if (multiplier <= 1.0 || durationMinutes <= 0) {" in _src
              and "int amplifier = (int) Math.round(((multiplier - 1.0) / 0.25) - 1.0);" in _src
              and "return multiplierToAmplifier(multiplier) >= 0 && minutes > 0;" in _src)
    check("self-test: both replies for 2, and 2000030 / 2 x 30 still start (no boost started on boot)",
          "checkBoostStartReplies(broken);" in be_test and "GlobalTpBoost.undecodedReply(2)" in be_test
          and "GlobalMasteryBoost.undecodedReply(2)" in be_test and "encodedWouldStart(2000030)" in be_test
          and "minutesWouldStart(2.0, 30)" in be_test and "startBoost(" not in be_test)

    def _decode(enc):
        if enc <= 0:
            return None
        m, mins = (enc // 10000) / 100.0, enc % 10000
        return None if m <= 1.0 or mins <= 0 else (m, mins)

    check("sim: 2 -> rejected, 2000030 -> 2x 30 min, 1250015 -> 1.25x 15 min",
          _decode(2) is None and _decode(2000030) == (2.0, 30) and _decode(1250015) == (1.25, 15))

    print("\n--- § boost start dispatch ---")
    sd_cmd = read(SRC / "command/ProgressionCommands.java")
    sd_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    check("manifest boost-start-dispatch row", "boost-start-dispatch" in manifest)
    for _tree, _only in (("boost", "boostMultiplierOnly"), ("masteryboost", "masteryBoostMultiplierOnly")):
        _t = sd_cmd.split('Commands.m_82127_("' + _tree + '")', 1)[-1].split('Commands.m_82127_("end")', 1)[0]
        check(_tree + ": multiplier branch registers before encoded, capped, runs on its own",
              0 <= _t.find('m_82129_("multiplier"') < _t.find('m_82129_("encoded"')
              and 'DoubleArgumentType.doubleArg(1.25, MAX_START_MULTIPLIER)' in _t
              and ("executes(ctx -> " + _only + "(") in _t
              and _t.count('m_82129_("purchaser"') == 2)
    _mo = sd_cmd.split("private static int boostMultiplierOnly(", 1)[-1].split("\n    }\n", 1)[0]
    check("cap 100 is below every encoded value; whole numbers go to the encoded reply",
          "static final double MAX_START_MULTIPLIER = 100.0;" in sd_cmd and 100.0 < 1250000
          and "return boostEncoded(source, (int) value, null);" in _mo
          and "GlobalTpBoost.undecodedReply(String.valueOf(value))" in _mo)
    check("dispatch self-test parses the real tree: 2 30 Steve, 2.5 30, 2000030, 2000030 123, 2",
          "dispatcher.register(build(\"progression\"));" in sd_cmd
          and '"start 2 30 Steve"' in sd_cmd and '"start 2.5 30"' in sd_cmd and '"start 2000030"' in sd_cmd
          and '"start 2000030 123"' in sd_cmd and 'expect(broken, dispatcher, src, tree, "start 2", ' in sd_cmd
          and "ProgressionCommands.selfTestBoostDispatch()" in sd_test)

    print("\n--- § dmz 2.2 port ---")
    p22_mig = read(SRC / "data/LmPlayerDataMigration.java")
    p22_strip = read(SRC / "data/LmPlayerDatStrip.java")
    p22_cfg = read(SRC / "config/DifficultyConfig.java")
    p22_log = read(SRC / "util/LmLog.java")
    p22_wish = read(SRC / "character/DmzRecustomizeEditor.java")
    p22_services = read(SRC / "character/CharacterServicesSystem.java")
    p22_pick = read(SRC / "character/RaceChangeClassPickFlow.java")
    p22_racial = read(SRC / "character/RacialBonusWipeHelper.java")
    p22_energy = read(SRC / "mixin/ResourcesEnergyDrainGuardMixin.java")
    p22_tp = read(SRC / "mixin/ResourcesDeathTpPenaltyMixin.java")
    p22_nemesis = read(SRC / "mixin/dmzultra/DmzultraNemesisSpawnGateMixin.java")
    p22_end = read(SRC / "progression/end/EndDimensionStrength.java")
    p22_create = read(SRC / "mixin/CreateCharacterC2SMixin.java")
    p22_build = read(MOD / "build.sh")
    p22_toml = read(MOD / "src/main/resources/META-INF/mods.toml")
    check("manifest dmz22-port row", "dmz22-port" in manifest)
    _run = p22_mig.split("public static void runIfNeeded(", 1)[-1].split("\n    }\n", 1)[0]
    check("migration marker only when nothing was skipped; non-player .dat counted apart",
          "if (result.skipped > 0) {" in _run
          and _run.index("if (result.skipped > 0) {") < _run.index("writeMarker(marker, dir, result);")
          and "result.notPlayer++;" in p22_mig and "m.notPlayer = result.notPlayer;" in p22_mig)
    _srun = p22_strip.split("public static void runIfNeeded(", 1)[-1].split("\n    }\n", 1)[0]
    check(".dat strip marker waits for the migration marker",
          "if (!Files.exists(LmPlayerDataMigration.markerPath())) {" in _srun
          and _srun.index("LmPlayerDataMigration.markerPath()") < _srun.index("writeMarker(marker, dir, result);"))

    def _marker(skipped):
        return skipped == 0

    check("sim: a skipped player file keeps the marker off; a clean pass writes it",
          _marker(0) and not _marker(1) and not _marker(3))
    check("debugLogging flag off by default; LmLog logs DEBUG unless it is on; no System.out in LM",
          "public boolean debugLogging = false;" in p22_cfg
          and "AdaptiveDifficultyMod.LOGGER.debug(message, args);" in p22_log
          and "cfg != null && cfg.debugLogging" in p22_log
          and not any("System.out.print" in p.read_text(encoding="utf-8") for p in SRC.rglob("*.java")))
    check("reskin and class pick open the editor through ReCustomizeWish.grant",
          "WISH.grant(player);" in p22_wish
          and "OpenRecustomizeS2C" not in p22_services and "OpenRecustomizeS2C" not in p22_pick
          and "DmzRecustomizeEditor.open(player);" in p22_services
          and "DmzRecustomizeEditor.open(player);" in p22_pick)
    check("leaving a race clears that race's RacialData",
          "clearRacialData(data, from, to);" in p22_racial
          and "racial.setZenkaiPermanentUses(0);" in p22_racial
          and "racial.getAssimilations().clear();" in p22_racial
          and "racial.getAbsorptions().clear();" in p22_racial)
    check("full-overload pins: removeEnergy(FZ)V, addTrainingPoints(DZZ)V, champion(ServerPlayer, int)",
          'method = "removeEnergy(FZ)V"' in p22_energy and "float amount, boolean drainsSurge, CallbackInfo ci" in p22_energy
          and 'method = "addTrainingPoints(DZZ)V"' in p22_tp and "double lm$leaveTrainingPoints(double amount)" in p22_tp
          and 'method = "champion(Lnet/minecraft/server/level/ServerPlayer;I)Lnet/minecraft/network/chat/Component;"' in p22_nemesis
          and 'method = "lambda$handle$1(Lcom/dragonminez/common/network/C2S/CreateCharacterC2S;' in p22_create)
    check("ki beam passes the 2.2 size argument; End TP fallback uses exact accessors",
          "DRAGON_DMZ_KI_COLOR_OUTLINE,\n                            DRAGON_DMZ_KI_BEAM_SIZE);" in p22_end
          and "private static final float DRAGON_DMZ_KI_BEAM_SIZE = 1.0f;" in p22_end
          and "resources.setTrainingPointsExact(Math.max(0.0d, cur + delta));" in p22_end)
    check("build checks mixin targets against the DMZ jar; mods.toml asks for DMZ 2.2",
          'python3 "$ROOT/scripts/audit_mixin_targets.py" --src "$SRC" --cp "$CP"' in p22_build
          and 'versionRange = "[2.2,)"' in p22_toml)

    print("\n--- § optional-mod mixin gate ---")
    og_json = read(MOD / "src/main/resources/legacymechanics.mixins.json")
    og_plugin = read(SRC / "mixin/LegacyMechanicsMixinPlugin.java")
    og_build = read(MOD / "build.sh")
    og_mf = read(MOD / "src/main/resources/META-INF/MANIFEST.MF")
    check("manifest optional-mod-mixin-gate row", "optional-mod-mixin-gate" in manifest)
    check("Hakai gate unregistered (Noea off live); source kept",
          "HakaiDestroyerGateMixin" not in og_json
          and (SRC / "mixin/noea/HakaiDestroyerGateMixin.java").is_file())
    check("dmzrevamp mixins gated on their target class file",
          'endsWith("PrestigeServiceIntegrationMixin")' in og_plugin
          and 'endsWith("DmzRevampPrestigeCapMixin")' in og_plugin
          and '"com/dmzrevamp/revamp/prestige/PrestigeService.class"' in og_plugin
          and '"com/dmzrevamp/revamp/prestige/PrestigeSystem.class"' in og_plugin)
    check("build: plugin gate for optional-mod mixins; jar manifest matches version and configs",
          '--gate-plugin "$SRC/com/dbzlegacy/adaptivedifficulty/mixin/LegacyMechanicsMixinPlugin.java"' in og_build
          and 'python3 "$ROOT/scripts/audit_jar_manifest.py" "$JAR" "$VERSION"' in og_build
          and "Implementation-Version: 2.4.13" not in og_mf)

    print("\n--- § prestige follows overhaul ---")
    pf_ps = read(SRC / "progression/shop/PrestigeSystem.java")
    pf_pp = read(SRC / "progression/shop/PrestigePointsSystem.java")
    pf_cap = read(SRC / "progression/LmOverhaulCapMath.java")
    pf_bridge = read(SRC / "progression/bridge/DmzRevampPrestigeBridge.java")
    pf_cfg_bridge = read(SRC / "progression/bridge/DmzRevampConfigBridge.java")
    pf_mixin = read(SRC / "mixin/DmzRevampPrestigeCapMixin.java")
    pf_mirror = read(SRC / "progression/PersonalLevelCapMirror.java")
    pf_gui = read(SRC / "gui/ProgressionGuiApi.java")
    pf_admin = read(SRC / "progression/shop/PrestigeAdmin.java")
    pf_ultra = read(SRC / "gui/ultra/UltraPrestigeScreens.java")
    pf_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    check("manifest prestige-follows-overhaul row", "prestige-follows-overhaul" in manifest)
    _req = pf_ps.split("public static int requiredLevel(ServerPlayer player) {", 1)[-1].split("\n    }\n", 1)[0]
    check("requirement = Overhaul levelCap through the bridge; nothing else read",
          "DmzRevampPrestigeBridge.levelCap(data)" in _req
          and "PrestigeSystem.levelCap(data)" in pf_bridge
          and "getCompleted" not in _req and "heldCount" not in _req and "effectiveMaxLevel" not in pf_ps)
    _over = pf_cap.split("public static int overhaulLevelCap(StatsData data) {", 1)[-1].split("\n    }\n", 1)[0]
    check("cap is Overhaul's native ladder: no levelCap override, initialLevelCap pinned 50k, 100k without revamp",
          _over.strip() == "return levelCap(data);"
          and '@Inject(method = "levelCap"' not in pf_mixin
          and "overhaulLevelCap(data)" in pf_mixin and "journeyPrestigeNeed" not in pf_cap
          and "OVERHAUL_INITIAL_LEVEL_CAP = 50_000" in pf_cap
          and "initial.setInt(prestige, OVERHAUL_INITIAL_LEVEL_CAP);" in pf_cap
          and "return OVERHAUL_LEVEL_CAP;" in pf_cap.split("public static int overhaulLevelCap() {", 1)[-1].split("\n    }\n", 1)[0])
    check("breakthroughs gone from code paths; stored keys untouched",
          not any(k in pf_pp for k in ("getBreakthroughs", "setBreakthroughs", "breakthroughCost",
                                        "buyBreakthrough", "effectiveMaxLevel"))
          and "adjustBreakthroughs" not in pf_admin and "breakthroughs_max" not in pf_gui
          and "KEY_BREAKTHROUGHS" not in pf_mirror
          and '"pp_level_breakthroughs"' in read(SRC / "data/LmPlayerStore.java"))
    check("held max = revamp maxPrestigeCount everywhere; LM does not overwrite it",
          "MAX_HELD" not in pf_ps
          and "LmOverhaulCapMath.configuredMaxPrestigeCount()" in pf_ps
          and pf_mixin.count("LmOverhaulCapMath.configuredMaxPrestigeCount()") == 2
          and "maxPrestigeCount" not in pf_cfg_bridge.split("public static void onServerStarting()", 1)[-1]
          and "return LmOverhaulPrestigeIntegration.OVERHAUL_MAX_PRESTIGE;" in pf_cap)
    check("menus state the rule; next requirement shows the cap",
          "Reaching your current cap earns your next prestige." in pf_ultra
          and 'out.put("next_required", String.valueOf(required))' in pf_gui)
    check("self-test covers the requirement, the ladder values, and the held cap",
          "checkPrestigeFollowsOverhaul(broken);" in pf_test
          and "PrestigeSystem.requiredLevel(null)" in pf_test
          and "{50_000, 65_000, 80_000, 95_000, 110_000, 125_000, 140_000, 150_000" in pf_test
          and "DmzRevampPrestigeBridge.levelCap(sd)" in pf_test
          and "configuredMaxPrestigeCount()" in pf_test)

    print("\n--- § prestige per character ---")
    pc = read(SRC / "progression/shop/PrestigeCharacter.java")
    pc_ultra = read(SRC / "progression/shop/PrestigeCharacterUltra.java")
    pc_ps = read(SRC / "progression/shop/PrestigeSystem.java")
    pc_pp = read(SRC / "progression/shop/PrestigePointsSystem.java")
    pc_mod = read(SRC / "AdaptiveDifficultyMod.java")
    pc_ev = read(SRC / "event/DifficultyEvents.java")
    pc_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    check("manifest prestige-per-character row", "prestige-per-character" in manifest)
    check("character id: Slots.characterId when Ultra slots are on, the account UUID otherwise",
          "return Slots.enabled() ? Slots.characterId(player) : null;" in pc_ultra
          and "return character == null ? account : character;" in pc
          and 'Class.forName("com.dmzultra.slots.api.Slots", false' in pc
          and "PrestigeCharacter.register();" in pc_mod)
    check("only PrestigeCharacterUltra names the Ultra slots API",
          not any("com.dmzultra.slots" in read(f) for f in SRC.rglob("*.java")
                  if f.name not in ("PrestigeCharacterUltra.java", "PrestigeCharacter.java"))
          and "import com.dmzultra" not in pc)
    check("every prestige record goes through the character: held, lifetime, override, confirm, favor",
          "LmPlayerStore" not in pc_ps and pc_ps.count("PrestigeCharacter.root(player)") == 10
          and "CONFIRM_UNTIL.get(PrestigeCharacter.id(player))" in pc_ps
          and "CONFIRM_UNTIL.put(PrestigeCharacter.id(player)," in pc_ps
          and "PrestigeCharacter.getLong(player, ROOT_POINTS, 0L)" in pc_pp
          and "PrestigeCharacter.root(player).m_128356_(ROOT_POINTS, n);" in pc_pp)
    pc_pd = read(SRC / "progression/ProgressionData.java")
    pc_build = read(MOD / "build.sh")
    check("account-wide legacy sources (faction 4, shop inference) only feed the account record",
          pc_ps.count("PrestigeCharacter.isAccount(player) ? readFactionPoints(player, FACTION_HELD_ID) : null") == 2
          and "if (!PrestigeCharacter.isAccount(player)) {\n            return false;" in pc_ps)
    check("faction 4 is written only for the account character (ensureHeldRecorded and setHeldPublic)",
          pc_ps.count("if (PrestigeCharacter.isAccount(player)) {\n            mirrorFactionHeld(player, ") == 2
          and pc_ps.count("mirrorFactionHeld(player, ") == 2)
    check("migration: slot 0 keeps the account's prestige; other characters start at 0, nothing copied",
          "static void startEmpty(CompoundTag character) {" in pc
          and "startEmpty(character);" in pc.split("public static CompoundTag root(ServerPlayer player) {", 1)[-1]
          and "accountSeed" not in pc and "lm_prestige_account_seed" not in pc
          and "if (account.equals(id)) {\n            return LmPlayerStore.root(account);" in pc)
    check("purchases per character: every pp_* key in the stored bag goes to the character's shop bag",
          'SHOP_PREFIX = "pp_"' in pc_pd
          and 'return key != null && key.startsWith(SHOP_PREFIX) ? shop(player) : stored(player);' in pc_pd
          and pc_pd.count("CompoundTag tag = bag(player, key);") == 3
          and "bag(player, key).m_128359_(key," in pc_pd and "bag(player, key).m_128356_(key, value);" in pc_pd
          and "bag(player, key).m_128473_(key);" in pc_pd
          and pc_pp.count("CompoundTag tag = ProgressionData.shop(player);") == 2
          and "ProgressionData.stored(player)" not in pc_pp)
    _switch = pc.split("static void switchedIn(", 1)[-1].split("\n    }\n", 1)[0]
    check("switch: Overhaul count, the new character's purchases, and tier eligibility re-applied on the next tick",
          "CharacterEvent.SwitchIn.class" in pc_ultra
          and ".scheduleSyncAfterStatsReset(p);" in _switch
          and "PrestigePointsSystem.reapplyAllShopPurchases(p);" in _switch
          and "UnlockSystem.syncUnlocks(p, data);" in _switch)
    _born = pc.split("static void born(", 1)[-1].split("\n    }\n", 1)[0]
    _reset = pc.split("private static void resetToFresh(", 1)[-1].split("\n    }\n", 1)[0]
    check("birth: active character, blank record, known new creation time, once; fresh prestige, stats, no purchases",
          "CharacterEvent.Born.class" in pc_ultra
          and "!character.equals(id(player))" in _born
          and "if (!blank) {" in _born and "if (created == null) {" in _born
          and "if (!claimBirth(accountRoot, slot, created)) {" in _born
          and "resetToFresh(player, account, character);" in _born
          and "startFresh(root(player));" in _reset and "freshStats(player);" in _reset
          and "PrestigePointsSystem.setPoints(player, 0);" in _reset
          and "clearPurchases(ProgressionData.shop(player));" in _reset
          and "stats.copyFrom(new StatsData((Player) player));" in pc
          and "new StatsSyncS2C(player)" in pc
          and 'record.m_128471_("blank")' in pc_ultra and "SlotStore.readRecord(" in pc_ultra
          and "Long created = null;" in pc_ultra and "created = s.created();\n                break;" in pc_ultra)
    check("character files flushed on save and unloaded on logout",
          "PrestigeCharacter.flush(player);" in pc_ev and "PrestigeCharacter.unload(player);" in pc_ev)
    check("build: clear error when the nemesis mixin's dmzultra jar lacks com.dmzultra.journal.Rival",
          "com/dmzultra/journal/Rival.class" in pc_build
          and "DMZULTRA_JAR=<dmzultra-0.2.9.jar>  DMZULTRA_SLOTS_JAR=<dmzultra-0.4.x.jar>" in pc_build)
    check("self-test covers migration totals, fresh start, purchase clearing, and the birth guard",
          "PrestigeCharacter.selfTest(broken);" in pc_test)

    print("\n--- § blank switch-in reset (4.7.4) ---")
    _on_switch = pc_ultra.split("private static void onSwitchIn(", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest blank-switch-in-reset row", "blank-switch-in-reset" in manifest)
    check("switch-in reads the incoming slot's stored record (blank flag, no dmz part; unreadable = not blank)",
          "SlotKey to = event.to();" in _on_switch
          and "blankRecord(player.m_20194_(), to)" in _on_switch
          and "Slots.characterId(to)" in _on_switch
          and 'record.m_128469_("parts").m_128469_("dmz").m_128456_()' in pc_ultra
          and "return false;\n        }\n    }" in pc_ultra.split("private static boolean blankRecord(", 1)[-1])
    check("a blank incoming character gets the same fresh reset as a birth, every switch, before purchases re-apply",
          "if (resetOnSwitchIn(blank, toCharacter, active)) {" in _switch
          and "resetToFresh(p, account, active);" in _switch
          and _switch.find("resetToFresh(p, account, active);")
              < _switch.find("PrestigePointsSystem.reapplyAllShopPurchases(p);")
          and "claimBirth" not in _switch
          and "return blank && toCharacter != null && toCharacter.equals(active);" in pc)
    check("self-test covers the switch-in reset guard", "resetOnSwitchIn(true, fresh, other)" in pc)

    print("\n--- § ultra form scaling off (4.7.4) ---")
    uf = read(SRC / "progression/bridge/UltraFormScale.java")
    uf_cfg = read(SRC / "config/DifficultyConfig.java")
    uf_help = read(SRC / "config/editor/ConfigFieldHelp.java")
    uf_reg = read(SRC / "config/ConfigRegistry.java")
    uf_started = pc_ev.split("public void onServerStarted(", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest ultra-form-scale-off row", "ultra-form-scale-off" in manifest)
    check("config flag disableUltraFormScaling, default on, with help text and registry entry",
          "public boolean disableUltraFormScaling = true;" in uf_cfg
          and "disableUltraFormScaling=" in uf_help and '"disableUltraFormScaling"' in uf_reg)
    check("OverhaulScale.simulate(false) is called by reflection only, no compile dependency",
          'CLASS = "com.dmzultra.form.OverhaulScale"' in uf
          and 'scale.getMethod("simulate", Boolean.class)' in uf
          and "simulate.invoke(null, Boolean.FALSE);" in uf
          and not any("import com.dmzultra.form" in read(f) for f in SRC.rglob("*.java")))
    check("runs on ServerStarted (after Ultra's boot self-test), never at mod construction",
          "UltraFormScale.onServerStarted();" in uf_started
          and "UltraFormScale" not in pc_mod)
    check("missing class or method: warning, LM keeps booting, scale left on",
          "} catch (Throwable absent) {\n            return null;" in uf
          and "DMZ Ultra form scaling left on:" in uf
          and "UltraFormScale.onServerStarted();\n        } catch (Throwable t) {" in uf_started)
    check("pulse re-applies the disable when active() reads true again, and hands it back when the flag is off",
          'safePulse("ultra-form-scale"' in pc_ev
          and "} else if ((Boolean) active.invoke(null)) {" in uf
          and "simulate.invoke(null, (Object) null);" in uf)
    check("startup log says whether the scale was disabled or left on",
          "DMZ Ultra form scaling disabled" in uf
          and "DMZ Ultra form scaling left on: disableUltraFormScaling is false" in uf)
    check("self-test covers a missing OverhaulScale class", "UltraFormScale.selfTest(broken);" in pc_test)

    print("\n--- § body parts catalog 0.4.0 ---")
    import json as _json
    bp_json = _json.loads(read(SRC.parents[3] / "resources" / "dmzultra-form-parts.json"))
    bp_cat = read(SRC / "gui/ultra/BodyPartCatalog.java")
    bp_test = read(SRC / "gui/ultra/BodyPartCatalogSelfTest.java")
    bp_gen = read(MOD / "sim" / "gen_dmzultra_form_parts.py")
    _sr = ["soulreaper/base/base", "soulreaper/truezanpakutoforms/bloodchains",
           "soulreaper/truezanpakutoforms/hornofsalvation", "soulreaper/truezanpakutoforms/truebankai",
           "soulreaper/truezanpakutoforms/trueshikai", "soulreaper/zanpakutoforms/bankai",
           "soulreaper/zanpakutoforms/mugetsu", "soulreaper/zanpakutoforms/shikai",
           "soulreaper/zanpakutoforms/vastolorde"]
    check("manifest body-parts-catalog-040 row", "body-parts-catalog-040" in manifest)
    check("catalog generated from DMZ Ultra 0.4.0 by the committed generator",
          bp_json.get("ultraVersion") == "0.4.0"
          and bp_json.get("generator") == "tools/dmz-adaptive-difficulty/sim/gen_dmzultra_form_parts.py"
          and "def tables(jar):" in bp_gen and '"formKeyAliases"' in bp_gen)
    check("shop lists Ultra 0.4.0's 109 client pieces; forms cover forms.json and every set table",
          len(bp_json["pieces"]) == 109 and "horns_vasto" in bp_json["pieces"]
          and len(bp_json["forms"]) >= 200 and all(k in bp_json["forms"] for k in _sr)
          and all(p in bp_json["pieces"] for k in _sr for p in bp_json["forms"][k]))
    check("no variant keys and no Noea-era renamed keys",
          not any("#" in k or k.split("/")[0] in ("demon", "alien", "sphinxian") for k in bp_json["forms"]))
    check("every form has a design; the catalog shape BodyPartCatalog reads is unchanged",
          set(bp_json["designs"]) == set(bp_json["forms"])
          and all(set(d) <= {"own", "trim", "horn", "light"} for d in bp_json["designs"].values())
          and '"pieces"' in bp_cat and '"forms"' in bp_cat and '"designs"' in bp_cat)
    check("a set piece's tail reads as the tail slot (single-tail rule), plural tails stay body",
          's.endsWith("_tail") || s.contains("_tail_")' in bp_cat
          and '"ystr_tail"' in bp_test and '"mua_sash_tails"' in bp_test)
    check("BodyPartCatalogSelfTest covers Soul Reaper, a renamed key and the 0.4.0 shop size",
          '"bankai_coat"' in bp_test and '"demon", "abyssforms", "demongod"' in bp_test
          and "shop.size() < 109" in bp_test)

    print("\n--- § gui follow-ups 4.6.249 ---")
    gf_spec = read(SRC / "gui/ultra/UltraScreenSpec.java")
    gf_style = read(SRC / "gui/ultra/UltraStyle.java")
    gf_hub = read(SRC / "gui/ultra/UltraHubScreens.java")
    gf_diff = read(SRC / "gui/ultra/UltraDifficultyScreens.java")
    gf_rival = read(SRC / "gui/ultra/UltraRivalScreens.java")
    gf_prog = read(SRC / "gui/ProgressionGuiApi.java")
    gf_main = gf_spec.split("public UltraScreenSpec footerBackToMain()", 1)[-1].split("\n    }\n", 1)[0]
    gf_admin = gf_hub.split("private static void openAdmin(", 1)[-1].split("\n    }\n", 1)[0]
    gf_chal = gf_rival.split("private static void openChallenge(", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest gui-followups-249 row", "gui-followups-249" in manifest)
    check("Home-tab footer reads Back to main menu (no lone Main menu)",
          "UltraStyle.BACK_TO_MAIN" in gf_main and "MAIN_MENU" not in gf_main.replace("BACK_TO_MAIN", ""))
    check("Staff Admin has one footer: Back to main menu",
          "spec.footerBackToMain();" in gf_admin and 'footerBack("nav:hub"' not in gf_admin)
    check("Difficulty sub-pages say Back to Difficulty Home and the label fits (real glyph widths)",
          'footerBackHome("Home")' not in gf_diff and gf_diff.count('footerBackHome("Difficulty Home")') == 4
          and "public static int textWidthPx(" in gf_style
          and "UltraStyle.textWidthPx(plainLabel) <= labelPx" in gf_spec)
    check("staff progression toggles reply with the display label",
          "ProgressionSystem.flagLabel(flag)" in gf_prog and "ProgressionSystem.flagLabel(key)" in gf_prog
          and '"Progression " + flag' not in gf_prog and '"Progression " + key' not in gf_prog
          and "Usage: /progression admin" not in gf_prog and "Usage: progression do flag" not in gf_prog)
    check("Rival Challenge tab: pending lines come from the same page as their buttons",
          "RivalGuiApi.pendingChallengeLinesFor(pageChallenges)" in gf_chal
          and "placePerChallengeButtons(spec, player, pageChallenges);" in gf_chal)

    def _w(t):
        return sum(2 if c in "i!,.:;|'`" else 3 if c == "l" else 4 if c in " It[]" else
                   5 if c in 'fk"()*<>{}' else 7 if c in "@~" else 6 for c in t)

    check("sim: Back to Difficulty Home fits the 134px label area (real widths)", _w("Back to Difficulty Home") <= 134)
    gf_boss = read(SRC / "progression/boss/PlayerBossSummons.java")
    gf_end = read(SRC / "progression/end/EndDimensionStrength.java")
    gf_act = read(SRC / "service/DifficultyActions.java")
    gf_spec2 = read(SRC / "rival/RivalSpectator.java")
    gf_bsum = gf_boss.split("public static SummonOutcome summonOutcome(", 1)[-1].split("\n    }\n", 1)[0]
    gf_dsum = gf_end.split("playerSummonOutcome(ServerPlayer player) {", 1)[-1].split("\n    }\n", 1)[0]
    gf_start = gf_spec2.split("public static String start(", 1)[-1].split("\n    }\n", 1)[0]
    check("menu-first: boss summon confirmations paint on the page, not in chat",
          "DmzRewards.msg" not in gf_bsum and "return SummonOutcome.ok(done);" in gf_bsum
          and "msg(player" not in gf_dsum and "SummonOutcome.ok(done)" in gf_dsum
          and gf_act.count("out.ok() ? Result.ok(out.text()) : Result.fail(out.text())") == 2)
    check("menu-first: spectate confirmation is returned, not chatted",
          "DmzRewards.msg" not in gf_start and '"Watching "' in gf_start)

    print("\n--- § kit duels + dodge counter ---")
    kd_ai = read(SRC / "ai/AdaptiveAiSystem.java")
    kd_evo = read(SRC / "evolution/EnemyEvolution.java")
    kd_dc = read(SRC / "ai/DodgeCounter.java")
    kd_ev = read(SRC / "event/DifficultyEvents.java")
    kd_cfg = read(SRC / "config/DifficultyConfig.java")
    kd_pack = kd_ai.split("static int packSize(DifficultyTier tier) {", 1)[-1].split("\n    }\n", 1)[0]
    kd_hook = kd_ev.split("public void onAttackDodgeCounter(", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest kit-duels-dodge-counter row", "kit-duels-dodge-counter" in manifest)
    check("config defaults: pack cap 4, pack off Mythic, kit-fighter Mythic, counter on at Master",
          "public int adaptiveAiPackCallMaxAllies = 4;" in kd_cfg
          and 'public String adaptiveAiPackCallOffTier = "MYTHIC";' in kd_cfg
          and 'public String adaptiveAiKitFighterTier = "MYTHIC";' in kd_cfg
          and "public boolean enableDodgeCounter = true;" in kd_cfg
          and 'public String dodgeCounterTier = "MASTER";' in kd_cfg)
    check("pack calls shrink with tier and stop at the off tier; frenzy follows the same cap",
          "return 0;" in kd_pack and "Math.min(Math.max(0, cfg.adaptiveAiPackCallMaxAllies), basePackSize(tier))" in kd_pack
          and "if (pack > 0) {" in kd_ai and "frenzyAllies(mob, level, Math.min(10.0, scan), packSize(tier));" in kd_ai
          and "if (maxAllies <= 0) {" in kd_ai)
    check("duel kit: one action per beat, cycled by range, kitCooldownScale cadence",
          "duelKit(mob, target, tier, tag, age, dist);" in kd_evo
          and 'DUEL_CLOSE = {"combo", "slam", "dodge", "retreat"}' in kd_evo
          and 'DUEL_MID = {"blast", "dash", "dodge"}' in kd_evo
          and 'DUEL_FAR = {"laser", "wave", "leap"}' in kd_evo
          and 'age - tag.m_128454_("dmz_ad_kit_act") < beat' in kd_evo
          and "long beat = kitCd(" in kd_evo and "dmz_ad_duel_reengage" in kd_evo)
    check("dodge counter: HIGH priority (before Iron's NORMAL), scaled attacker at counter tier, Evasion + Shroud only",
          "@SubscribeEvent(priority = EventPriority.HIGH)\n    public void onAttackDodgeCounter(" in kd_ev
          and "DodgeCounter.onAttack(event);" in kd_hook
          and "MobScaling.TAG_SCALED" in kd_dc and "tier.ordinalPower() < counter.ordinalPower()" in kd_dc
          and '"irons_spellbooks", "evasion"' in kd_dc and '"irons_spellbooks", "abyssal_shroud"' in kd_dc
          and "shield" not in kd_dc.lower().replace("never touches shield", "")
          and "scapegoat" not in kd_dc.lower().replace("or scapegoat", "")
          and "m_19564_" not in kd_dc  # never reads/edits Evasion's amplifier (hit budget)
          and "ironsspellbooks" not in kd_dc and "simplymore" not in kd_dc)

    # Mirrors of packSize / duelKit / DodgeCounter.
    order = ["NONE", "AWAKENED", "ENHANCED", "ELITE", "ADVANCED", "MASTER", "LEGENDARY", "GOD", "DIVINE",
             "IMPOSSIBLE", "TRANSCENDENT", "ETERNAL", "MYTHIC", "OMEGA", "ABSOLUTE", "APEX", "ZENITH"]
    P = {n: i for i, n in enumerate(order)}

    def _base(t):
        b = 2
        for name, v in (("GOD", 4), ("IMPOSSIBLE", 6), ("OMEGA", 8), ("ZENITH", 10)):
            if P[t] >= P[name]:
                b = v
        return b

    def _pack(t, cap=4, off="MYTHIC"):
        return 0 if P[t] >= P[off] else min(cap, _base(t))

    check("sim: pack sizes Master 2, God 4, Impossible 4 (was 6), Eternal 4, Mythic+ 0 (was 6-10)",
          [_pack(t) for t in ("MASTER", "GOD", "IMPOSSIBLE", "ETERNAL", "MYTHIC", "OMEGA", "ZENITH")]
          == [2, 4, 4, 4, 0, 0, 0])

    def _rotation(band, beats, cds=None):
        step, out = 0, []
        for _ in range(beats):
            for i in range(len(band)):
                act = band[(step + i) % len(band)]
                if not cds or act not in cds:
                    out.append(act)
                    step = (step + i + 1) % len(band)
                    break
        return out

    check("sim: close-range duel cycles combo, slam, dodge, retreat (no repeats in a cycle)",
          _rotation(["combo", "slam", "dodge", "retreat"], 4) == ["combo", "slam", "dodge", "retreat"])
    check("sim: an action on cooldown is skipped, the next one fires",
          _rotation(["blast", "dash", "dodge"], 2, cds={"blast"}) == ["dash", "dodge"])

    def _strip(attacker_scaled, attacker_tier, counter="MASTER", enabled=True, irons=True, effects=("evasion",)):
        if not enabled or not irons or not attacker_scaled or not effects:
            return ()
        return effects if P[attacker_tier] >= P[counter] else ()

    check("sim: Master+ scaled hit strips Evasion and Shroud; a re-cast is stripped again next hit",
          _strip(True, "MASTER", effects=("evasion", "abyssal_shroud")) == ("evasion", "abyssal_shroud")
          and _strip(True, "OMEGA", effects=("abyssal_shroud",)) == ("abyssal_shroud",))
    check("sim: low-tier or unscaled hits, counter off, or Iron's absent: nothing stripped",
          _strip(True, "ADVANCED") == () and _strip(False, "ZENITH") == ()
          and _strip(True, "ZENITH", enabled=False) == () and _strip(True, "ZENITH", irons=False) == ())

    print("\n--- § vipers call ---")
    vc_mix = read(SRC / "mixin/simplymore/VipersCallUnendingVenomMixin.java")
    vc_cap = read(SRC / "progression/combat/VipersCallCloudCap.java")
    vc_plugin = read(SRC / "mixin/LegacyMechanicsMixinPlugin.java")
    vc_json = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/resources/legacymechanics.mixins.json")
    vc_ev = read(SRC / "event/DifficultyEvents.java")
    vc_build = read(ROOT / "tools/dmz-adaptive-difficulty/build.sh")
    vc_join = vc_ev.split("public void onLivingJoin(", 1)[-1].split("\n    }\n", 1)[0]
    check("manifest vipers-call-balance row", "vipers-call-balance" in manifest)
    check("Unending Venom off: string-targeted mixin redirects the held re-add to a no-op",
          '@Mixin(targets = "net.rosemarythyme.simplymore.item.uniques.VipersCallItem", remap = false)' in vc_mix
          and 'method = "m_6883_"' in vc_mix
          and "Lnet/minecraft/world/entity/player/Player;m_7292_(Lnet/minecraft/world/effect/MobEffectInstance;)Z" in vc_mix
          and "return false;" in vc_mix and "private boolean lm$noUnendingVenom(" in vc_mix and "require = 0)" in vc_mix
          and "import net.rosemarythyme" not in vc_mix)
    check("mixin applies only with Simply More present (class-file lookup, no early load); registered and packaged",
          'endsWith("VipersCallUnendingVenomMixin")' in vc_plugin and "return vipersCallPresent();" in vc_plugin
          and '"net/rosemarythyme/simplymore/item/uniques/VipersCallItem.class"' in vc_plugin
          and '"simplymore.VipersCallUnendingVenomMixin"' in vc_json
          and "mixin/simplymore/." in vc_build)
    check("right-click cloud capped at 200 ticks on join, matched by class name; damage/poison/blacklist untouched",
          "MAX_TICKS = 200;" in vc_cap and "cloud.m_19734_(MAX_TICKS);" in vc_cap
          and '"net.rosemarythyme.simplymore.entity.VipersCallProjectileAreaEffectCloudEntity"' in vc_cap
          and "VipersCallCloudCap.onJoin(event.getEntity());" in vc_join
          and "m_19738_" not in vc_cap and "m_19732_" not in vc_cap and "addEffect" not in vc_cap
          and "VipersCallBlacklist" not in vc_cap + vc_mix)

    def _cloud(duration):
        return min(duration, 200)

    check("sim: Simply More's 360-tick cloud becomes 200 (10 s); a shorter cloud is left alone",
          _cloud(360) == 200 and _cloud(120) == 120)

    def _held(effect_ticks, ticks_held, unending=False):
        t = effect_ticks
        for _ in range(ticks_held):
            t -= 1
            if unending and t < 25:
                t = 25
            if t <= 0:
                return 0
        return t

    check("sim: holding Viper's Call no longer pins an effect (was pinned at 25 forever)",
          _held(100, 400, unending=True) == 25 and _held(100, 400, unending=False) == 0)

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
          # DMZ 2.2: every TP add ends in addTrainingPoints(double, boolean, boolean).
          and "addTrainingPoints(DZZ)V" in resources_mixin
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
    check("/lm revive: donator self form, staff-or-revive-access <player> form, DMZ revive called directly",
          "LmRevive.literal()" in read(SRC / "command/MechanicsCommands.java")
          and "StaffAccess.hasSelfRevive(player) && !StaffAccess.isStaff(player)" in lm_revive
          and "StaffAccess.denyUnlessReviveOthers(source)" in lm_revive
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
          and "hasTerminalPermission" in terminal_cmd and "hasDonatorPermission" not in terminal_cmd)
    check("bukkit /terminal is registered",
          "\n  terminal:" in terminal_yml
          and "cmi.customalias.terminal" in terminal_yml
          and "permission:" not in term_block
          and '"terminal".equals(name)' in terminal_plugin)
    check("/terminal allows the terminal node or the alias permission",
          "terminalNode()" in terminal_staff.split("public static boolean hasTerminalPermission(", 1)[1].split("}", 1)[0]
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
    check("skillcheck permission in plugin.yml is the donator node",
          "permission: legacymechanics.donator" in yml and "legacymechanics.skillcheck" not in yml)
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
          and 'method = "champion(Lnet/minecraft/server/level/ServerPlayer;I)' in nemesis_mix
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
    check("PrestigeSystem has no need-floor handling", "shrinkNeedFloor" not in prestige and "NeedFloor" not in prestige)

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
