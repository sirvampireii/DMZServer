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


def check(label: str, cond: bool, detail: str = "", retired: bool = False) -> None:
    # retired=True only on checks of code that no longer exists (the deleted CNPC GUI); each
    # one is flagged at its call. Never retire by label: a matching word hid live checks.
    if retired:
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



def _after(src: str, anchor: str) -> str:
    """Text after ``anchor`` in ``src``. A missing anchor fails the audit (instead of quietly checking the whole
    file, which let renamed methods pass every "in" check on their body)."""
    if anchor not in src:
        check(f"anchor still in the source: {anchor.strip()[:70]!r}", False,
              "the code this check reads was renamed or moved; update the check")
        return ""
    return src.split(anchor, 1)[-1]


def has(code: str, *snippets: str) -> bool:
    """Every snippet is in ``code``, ignoring whitespace and line breaks. Prefer it for new checks: reformatting or
    re-wrapping code no longer breaks a check, only a real change does."""
    flat = " ".join(code.split())
    return all(" ".join(x.split()) in flat for x in snippets)


def ordered(code: str, *tokens: str) -> bool:
    """The tokens appear in this order in ``code`` ("A happens before B" without quoting the exact code)."""
    at = 0
    for t in tokens:
        at = code.find(t, at)
        if at < 0:
            return False
        at += len(t)
    return True

def boot_self_test(name: str) -> bool:
    """True when the startup self-test {name} runs at boot: LmGuiNetwork calls it directly, or
    calls UltraScreenSelfTest.runAll() and runAll() calls it (4.7.49)."""
    net = read(SRC / "net/gui/LmGuiNetwork.java")
    st = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    run_all = _after(st, "public static void runAll() {").split("\n    }\n", 1)[0]
    return (name + "();") in net or ("UltraScreenSelfTest.runAll();" in net and (name + "();") in run_all)


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
    check("PrestigeSystem need follows the held count (4.7.15; old veteran gate gone)",
          "LmOverhaulCapMath.prestigeRequirement(" in prestige and "HELD_GATE_MIN_COMPLETED" not in prestige)
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
    summon_body = _after(boss_src, "public static SummonOutcome summonOutcome(").split("\n    }\n", 1)[0]
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
    check("Summons tab lists Wither and Warden with their own pages and confirm pages; no summon command",
          '"page:wither"' in boss_ui and '"page:warden"' in boss_ui
          and '"page:summon_ask:" + page' in boss_ui and '"do:summon_wither"' in boss_ui and '"do:summon_warden"' in boss_ui
          and '"page:end_dragon"' in boss_ui
          and not any("summon_wither" in read(f) or "summon_warden" in read(f)
                      for f in (SRC / "command").glob("*.java")))

    print("\n--- § rival requests ---")
    rival_ui = read(SRC / "gui/ultra/UltraRivalScreens.java")
    rival_api = read(SRC / "gui/RivalGuiApi.java")
    rival_home = _after(rival_ui, "private static void openHome(").split("\n    }\n", 1)[0]
    rival_tab = _after(rival_ui, "private static void openActions(").split("\n    }\n", 1)[0]
    out_body = _after(rival_api, "public static int outgoingDeclareCount(").split("\n    }\n", 1)[0]
    check("manifest rival-requests-counts row", "rival-requests-counts" in manifest)
    check("Requests moved from Rival Home to the Rival tab next to Pick player",
          '"requests|Requests"' not in rival_home
          and '"pick_rival|Pick a player", "requests|" + (incoming > 0 ? "Requests (" + incoming + ")" : "Requests")' in rival_tab
          and 'case "requests", "pending", "invites" -> openRequests(' in rival_ui)
    # 4.7.81 (GuiKit): one Requests line (incoming from the Accept/Decline list, outgoing waiting on a reply);
    # the Accept/Decline buttons live on the Requests page only.
    check("Rival tab shows incoming requests from the Accept/Decline list and those waiting on a reply",
          '"Pending invites"' not in rival_ui
          and "int incoming = nonBlank(RivalGuiApi.pendingIncomingDeclareArgs(player)).size();" in rival_tab
          and '" waiting on a reply"' in rival_tab and "placeIncomingDeclareButtons" not in rival_tab)
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
    detail = _after(rival_ui, "private static void openListDetail(").split("\n    }\n", 1)[0]
    status_body = _after(rival_ui, "private static void profileStatus(").split("\n    }\n", 1)[0]
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
          and '"page:remove_confirm:" + profileArg(arg, returnPage)' in detail and "Not on your rival list." in detail
          # 4.7.71: the one back(); the page registry sends a profile back to the Rival tab.
          and "spec.back()" in detail
          and 'item("rival", "list_detail:**", "Rival profile", "rival|actions");' in read(SRC / "gui/ultra/PageRegistry.java"))
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
    term = _after(mob_scaling, "public static boolean terminateIfZeroHealth(").split("\n    }\n", 1)[0]
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
    has_sc = _after(don_staff, "public static boolean hasSkillCheck(").split("\n    }\n", 1)[0]
    has_don = _after(don_staff, "public static boolean hasDonatorPermission(").split("\n    }\n", 1)[0]
    revive_self = _after(don_revive, "private static int reviveSelf(").split("\n    }\n", 1)[0]
    revive_other = _after(don_revive, "private static int reviveOther(").split("\n    }\n", 1)[0]
    deny_other = _after(don_staff, "public static int denyUnlessReviveOthers(").split("\n    }\n", 1)[0]
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
          and "isStaffSource(src)" in _after(don_staff, "hasReviveOthersSource(").split("\n    }\n", 1)[0]
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
    pg_mult = _after(pg_src, "public static float winnerTpMultiplier(").split("\n    }\n", 1)[0]
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
          and '"Claimed"' in pg_bar and '"Champion " + champion' in pg_bar and 'String.join(" - ", parts)' in pg_bar
          and "pg.battles" in pg_bar
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
    end_win = _after(pg_mgr, "float baseWin = (related ? RivalConstants.CH_WIN_TP").split(
        "RivalProgression.get().onChallengeEnd(winP, loseP, ch, false, knockout);", 1)[0]
    end_draw = _after(pg_mgr, 'if ("draw".equals(reason) || "distance".equals(reason) || winner == null) {').split("deliverReport(", 1)[0]
    pb_body = _after(pg_src, "public static BattleResult processBattle(").split("\n    public static void touchDraw(", 1)[0]
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
    draw_rp = _after(pg_mgr, "private void awardDrawRp(").split("\n    }\n", 1)[0]
    ud_scale = _after(pg_mgr, "public float underdogDamageScale(").split("\n    }\n", 1)[0]
    ud_notice = _after(pg_src, "public static void underdogNotice(").split("\n    }\n", 1)[0]
    ud_is = _after(pg_src, "public static boolean isUnderdogOnGrounds(").split("\n    }\n", 1)[0]
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
    dp_pulse = _after(dp, "public static void pulse(").split("\n    }\n", 1)[0]
    dp_show = _after(dp, "private static void showBar(").split("\n    }\n", 1)[0]
    dp_hide = _after(dp, "private static void hideBar(").split("\n    }\n", 1)[0]
    dp_clear = _after(dp, "private static void clearExpired(").split("\n    }\n", 1)[0]
    dp_respawn = _after(dp_events, "public void onRespawn(").split("\n    }\n", 1)[0]
    dp_clone = _after(dp_events, "DifficultyCache.putData(neu, data);").split("\n    }\n", 1)[0]
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
          # 4.7.72: a HUD pill with no timer is hidden the same way.
          "if (BARS.containsKey(player.m_20148_())\n                        || com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.showing(player, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.PENALTY)) {\n                    hideBar(player);" in dp_pulse
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
    g_footer = _after(g_spec, "private void addFooterButton(").split("\n    }\n", 1)[0]
    g_info = _after(g_rival, "private static void openInfo(").split("\n    }\n", 1)[0]
    check("manifest gui-audit-248 row", "gui-audit-248" in manifest)
    check("footer ink matches the system's body buttons (no forced dark gray)",
          'accentCode = "8";' not in g_footer
          and "UltraStyle.readableButtonInk(face, UltraStyle.systemAccentCode(screenId))" in g_footer)
    check("Rival has three tabs; stats open from Home and return to Rival Home",
          # 4.7.84 (R-4): the Rival tab reads "My rivals".
          '.tabs("home|Home", "actions|My rivals", "challenge|Challenge");' in g_rival
          and '"info|My rival stats"' in g_rival and 'sub("info")' in g_info
          and ".back()" in g_info and 'p("rival", "info", "My rival stats", "rival|home");' in read(SRC / "gui/ultra/PageRegistry.java"))
    check("event log: the turn-on action is never dim while the log is off",
          'logOn ? UltraStyle.confirm("Event log - ON") : "Event log - ON"' in g_diff
          and 'UltraStyle.dim("Event log - ON")' not in g_diff)
    check("no Invalid / Usage replies in progression boosts or spar mentor settings",
          "Invalid" not in g_prog and "Usage: spar do mentor_tpmsg" not in g_spar and "Pick Mentor TP - ON or OFF" in g_spar)

    print("\n--- § summon loot bonus ---")
    sl = read(SRC / "progression/boss/SummonLootBonus.java")
    sl_boss = read(SRC / "progression/boss/PlayerBossSummons.java")
    sl_end = read(SRC / "progression/end/EndDimensionStrength.java")
    sl_events = read(SRC / "event/DifficultyEvents.java")
    sl_cfg = read(SRC / "config/DifficultyConfig.java")
    sl_drops = _after(sl_events, "public void onLivingDrops(").split("\n    }\n", 1)[0]
    sl_roll = _after(sl_end, "private static int rollEnderDragonLootTable(").split("\n    }\n", 1)[0]
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
    sl_once = _after(sl, "static boolean rollOnce(").split("\n    }\n", 1)[0]
    sl_copies = _after(sl, "public static List<ItemStack> rollCopies(").split("\n    }\n", 1)[0]
    sl_apply = _after(sl, "public static int applyToDrops(").split("\n    }\n", 1)[0]
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
    pd_start = _after(pd_events, "public void onServerStarting(").split("\n    }\n", 1)[0]
    pd_clone = _after(pd_events, "public void onClone(").split("\n    }\n", 1)[0]
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
    pd_keys = _after(pd_store, "DURABLE_KEYS = Set.of(").split(");", 1)[0]
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
    ut_load = _after(ut_data, "public void load(CompoundTag tag) {").split("\n    }\n", 1)[0]
    ut_tp_load = _after(ut_tp, "public void load(CompoundTag tag) {").split("\n    }\n", 1)[0]
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
    gm_start = _after(gm_events, "public void onServerStarting(").split("\n    }\n", 1)[0]
    gm_stop = _after(gm_events, "public void onServerStopping(").split("\n    }\n", 1)[0]
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
    check("status line beside the TP boost line in GUI home, Boosts tab, and legacy lore",
          "GlobalMasteryBoost.statusLine();" in gm_sys
          and '"mastery_boost"' in read(SRC / "gui/ProgressionGuiApi.java")
          and '"mastery_boost"' in read(SRC / "gui/ultra/UltraProgressionScreens.java"))
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
    bs_sel = _after(bs_scr, "static UltraScreenSpec boostsSelectorSpec(").split("\n    }\n", 1)[0]
    bs_view = _after(bs_scr, "static UltraScreenSpec boostControlSpec(").split("\n    }\n", 1)[0]
    bs_mb = _after(bs_api, "public static String masteryBoost(ServerPlayer actor, String argsJoined) {")
    bs_mb = bs_mb.split("\n    }\n", 1)[0]
    bs_tp = _after(bs_api, "public static String boost(ServerPlayer actor, String argsJoined) {")
    bs_tp = bs_tp.split("\n    }\n", 1)[0]
    check("manifest boosts-selector row", "boosts-selector" in manifest)
    check("selector: both status lines, TP Boost + Mastery Boost buttons, Back to main menu; tabs unchanged",
          "tpBoostLine(ph)" in bs_sel and "masteryBoostLine(ph)" in bs_sel
          and '"page:" + PAGE_TP_BOOST, "TP Boost"' in bs_sel
          and '"page:" + PAGE_MASTERY_BOOST, "Mastery Boost"' in bs_sel
          and ".back()" in bs_sel  # 4.7.71: a tab page's back() reads Back to main menu
          and '.tabs("home|Home", "boosts|Boosts", "coins|Coins")' in bs_scr)
    _presets = ['act + "1.25:30"', 'act + "1.5:30"', 'act + "2:30"', 'act + "2:60"', 'act + "3:30"']
    # 4.7.81 (GuiKit): presets are options (normal style); End stays danger.
    check("control view: own status line, five presets (normal style), danger End, Back to Boosts",
          all(p in bs_view for p in _presets) and bs_view.count("UltraStyle.boostPreset(") == 5
          and "UltraStyle.STYLE_CONFIRM" not in bs_view
          and 'act + "end", UltraStyle.danger("End this boost"), UltraStyle.STYLE_DANGER' in bs_view
          and 'String act = mastery ? "do:masteryboost:" : "do:boost:";' in bs_view
          and "mastery ? masteryBoostLine(ph) : tpBoostLine(ph)" in bs_view
          and ".back()" in bs_view
          and 'p("progression", "boosts_tp", "TP Boost", "progression|boosts");' in read(SRC / "gui/ultra/PageRegistry.java"))
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
    mw_lines = _after(mw, "public static List<Line> lines(ServerPlayer player) {").split("\n    }\n", 1)[0]
    mw_buy = _after(mw, "public static String buy(ServerPlayer player, String arg) {").split("\n    }\n", 1)[0]
    mw_apply = _after(mw, "public static String apply(ServerPlayer player, String arg) {").split("\n    }\n", 1)[0]
    check("manifest mastery-points-wallet row", "mastery-points-wallet" in manifest)
    check("fixed economy: 1 favor = 10 points, 1 point = 50 mastery, packs 1/2/5/10",
          "POINTS_PER_FAVOR = 10;" in mw and "MASTERY_PER_POINT = 50;" in mw
          and "FAVOR_PACKS = {1, 2, 5, 10};" in mw)
    check("balance lives only in the LM player file (lm_mastery_points via LmPlayerStore)",
          'KEY = "lm_mastery_points";' in mw and "LmPlayerStore.putLong(player, KEY," in mw
          and "LmPlayerStore.getLong(player, KEY," in mw and "LmPlayerStore.flush(" in mw
          and "getPersistentData" not in mw and "m_128469_" not in mw and "BonusStats" not in _after(mw, "*/")
          and "StatsData" not in _after(mw, "public static int balance(").split("public static boolean open()", 1)[0])
    check("config section masteryWallet.enabled gates selling; Shop button kept while points remain",
          "public MasteryWalletShop masteryWallet = new MasteryWalletShop();" in mw_cfg
          and "prestigeShopConfig.masteryWallet = new MasteryWalletShop();" in mw_cfg
          and "PrestigeShopConfig.get().masteryWallet.enabled" in mw and "if (!open())" in mw_buy
          and "cfg.masteryWallet.enabled || MasteryWallet.balance(player) > 0" in mw_scr
          and '"masteryWallet.enabled"' in mw_hooks)
    check("buy: standard favor wording, confirm page (no double click), favor taken then points added",
          "PrestigePointsSystem.notEnoughFavor(favor, have)" in mw_buy  # 4.7.56 shared favor sentence
          and "GuiClickConfirm" not in mw
          and mw_buy.index("PrestigePointsSystem.setPoints(player, have - favor);") < mw_buy.index("setBalance(player, balance + gained);")
          and "not refunded" in _after(mw, "public static String buyPrompt(").split("}", 1)[0])
    check("picker is live: radial group walk + getUnlockedForms / getUnlockedStackForms, no selectability filter",
          "TransformationsHelper.getUnlockedForms(data, race, group)" in mw_lines
          and "TransformationsHelper.getUnlockedStackForms(data, group)" in mw_lines
          and "isSkillAllowedForRace(TransformationsHelper.getSkillNameForType(type), race)" in mw_lines
          and "isSkillAllowedForRace(group, race)" in mw_lines
          and "getSelectable" not in mw.split("*/", 2)[-1]
          and "return withGodKi(ordered(raw), kiLines(player));" in mw_lines)
    check("radial order and hidden empty lines; names from DMZ lang",
          "!l.forms.isEmpty()" in mw and "comparingInt(l -> l.kind)" in mw
          and "String.CASE_INSENSITIVE_ORDER" in mw
          and 't.contains("super") || t.contains("legendary") || t.contains("android")' in mw
          and 'DmzLang.text("race.dragonminez." + raceKey + ".form."' in mw_lines
          and 'DmzLang.text("race.dragonminez.stack.group."' in mw_lines)
    check("apply: whole points, headroom / rate floor, balance bound, addMastery with cap, sync",
          "Math.floor(room / Math.max(1, rate) + 1.0E-9)" in mw
          and "points > fit" in mw_apply and "points > balance" in mw_apply
          and "line.stack ? ch.getStackFormMasteries() : ch.getFormMasteries()" in mw_apply
          and "masteries.addMastery(line.group, form.name, (double) points * MASTERY_PER_POINT, form.cap);" in mw_apply
          and "DmzSkillUtil.sync(player);" in mw_apply)
    check("apply confirm states points, form, resulting mastery, and no refund",
          all(s in _after(mw, "public static String applyPrompt(").split("\n    }\n", 1)[0]
              for s in ('" mastery point"', "formName(lineLabel, form)", "resulting(form.current, points, form.cap, form.rate)",
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
          and "§" not in _after(mw_scr, "// ── Mastery wallet").split("/** Exchange current prestige", 1)[0])
    check("self-test: balance, buy confirm page, cap math, whole-point limit, picker for a known unlock state",
          "checkMasteryWallet(broken);" in mw_test and "walletSpec(37, 4, true)" in mw_test
          and "masteryBuyAskSpec(2, 37, 4, true, true)" in mw_test and "maxPointsFor(c[0], c[1])" in mw_test
          and '"page:ask:mastery_apply:n:ssgrades:supersaiyan:1"' in mw_test
          and 'List.of("n:ssgrades", "n:supersaiyan", "n:oozaru", "s:kaioken")' in mw_test)

    def _fit(cur, cap):
        room = cap - cur
        return 0 if room <= 0 else int(room // 50)

    check("sim: 0/500 -> 10, 450/500 -> 1, 451/500 -> 0, 500/500 -> 0, 101/500 -> 7",
          [_fit(*c) for c in ((0, 500), (450, 500), (451, 500), (500, 500), (101, 500))] == [10, 1, 0, 0, 7])

    print("\n--- § mastery wallet paging ---")
    pg_scr = read(SRC / "gui/ultra/UltraPrestigeScreens.java")
    pg_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    pg_lines = _after(pg_scr, "static UltraScreenSpec linesSpec(").split("\n    }\n", 1)[0]
    pg_line = _after(pg_scr, "static UltraScreenSpec lineSpec(").split("\n    }\n", 1)[0]
    pg_form = _after(pg_scr, "static UltraScreenSpec formSpec(").split("\n    }\n", 1)[0]
    check("manifest mastery-wallet-paging row", "mastery-wallet-paging" in manifest)
    check("page size 8 for both pickers",
          "static final int MASTERY_PICKS_PER_PAGE = 8;" in pg_scr
          and "MASTERY_PICKS_PER_PAGE" in pg_lines and "MASTERY_PICKS_PER_PAGE" in pg_line)
    check("both pickers: pageCount, clampPage, pageSlice, pageLabel, placePrevNext",
          all(s in pg_lines and s in pg_line for s in (
              "UltraPickHelper.pageCount(", "UltraPickHelper.clampPage(page, pages)",
              "UltraPickHelper.pageSlice(", "UltraPickHelper.pageLabel(spec, pg, pages);",
              ))
          and 'pagerRow(spec, "mastery_lines", pg, pages);' in pg_lines
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
          # 4.7.71: back() to the line; the registry maps mw_form:<s|n>:<group>:<form> to mw_line:<s|n>:<group>.
          and ".back()" in pg_form
          and 'item("prestige", "mw_form:*:*:*:*", "Form mastery", "prestige|mw_line:$1:$2:$4");' in read(SRC / "gui/ultra/PageRegistry.java"))
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
    ds_copy = _after(ds_events, "private static void copyLmPersistentData(").split("\n    }\n", 1)[0]
    ds_start = _after(ds_events, "public void onServerStarting(").split("\n    }\n", 1)[0]
    ds_file = _after(ds, "static void stripFile(").split("\n    }\n", 1)[0]
    _frozen = ["dmz_adaptive_difficulty", "lm_progression", "prestige_total_completed",
               "prestige_total_staff_override", "prestige_need_floor", "lm_prestige_held", "lm_prestige_points",
               "pp_level_breakthroughs", "lm_personal_level_cap", "lm_personal_breakthroughs", "lmNemesisSpawnMode",
               "lm_prestige_confirm_until", "lm_cnpc_player_migrated", "lm_shadow_dummy_cd_until"]
    _keys_block = _after(ds, "FROZEN_KEYS = ").split(")));", 1)[0]
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
        _start = _after(_src, "public static String startBoost(ServerPlayer actor, int encoded, String purchaser) {")
        _start = _start.split("\n    }\n", 1)[0]
        # 4.6.261: the int form delegates to the String form, which builds the reply.
        _reply = _after(_src, "public static String undecodedReply(String encoded) {").split("\n    }\n", 1)[0]
        check(_name + " boost: undecodable value gets undecodedReply with the value and both forms",
              "if (decoded == null) {\n            return undecodedReply(encoded);" in _start
              and '" was not understood. Use one of these:' in _reply and "+ encoded +" in _reply
              # 4.7.88: the boost tree lives at /lm admin progression.
              and ('"/lm admin progression ' + _cmd + ' start <encoded>"') in _reply and '"2000030"' in _reply
              and ('"/lm admin progression ' + _cmd + ' start <multiplier> <minutes>"') in _reply
              and ('"/lm admin progression ' + _cmd + ' start 2 30"') in _reply
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
    _mo = _after(sd_cmd, "private static int boostMultiplierOnly(").split("\n    }\n", 1)[0]
    check("cap 100 is below every encoded value; whole numbers go to the encoded reply",
          "static final double MAX_START_MULTIPLIER = 100.0;" in sd_cmd and 100.0 < 1250000
          and "return boostEncoded(source, (int) value, null);" in _mo
          and "GlobalTpBoost.undecodedReply(String.valueOf(value))" in _mo)
    check("dispatch self-test parses the real tree: 2 30 Steve, 2.5 30, 2000030, 2000030 123, 2",
          "dispatcher.register(CommandAccess.underLmAdmin(adminBuild(\"progression\")));" in sd_cmd
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
    _run = _after(p22_mig, "public static void runIfNeeded(").split("\n    }\n", 1)[0]
    check("migration marker only when nothing was skipped; non-player .dat counted apart",
          "if (result.skipped > 0) {" in _run
          and _run.index("if (result.skipped > 0) {") < _run.index("writeMarker(marker, dir, result);")
          and "result.notPlayer++;" in p22_mig and "m.notPlayer = result.notPlayer;" in p22_mig)
    _srun = _after(p22_strip, "public static void runIfNeeded(").split("\n    }\n", 1)[0]
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
    check("Hakai gate gone with Noea (4.7.47)",
          "HakaiDestroyerGateMixin" not in og_json
          and not (SRC / "mixin/noea/HakaiDestroyerGateMixin.java").is_file())
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
    _req = _after(pf_ps, "public static int requiredLevel(ServerPlayer player) {").split("\n    }\n", 1)[0]
    check("requirement = need ladder of the held wallet (4.7.15); the bridge cap is the same ladder (4.7.16)",
          "LmOverhaulCapMath.prestigeRequirement(" in _req and "getHeldWallet(player)" in _req
          and "return LmOverhaulCapMath.prestigeRequirement(overhaulCount(data));" in pf_bridge
          and "PrestigeSystem.levelCap(" not in pf_bridge
          and "getCompleted" not in _req and "effectiveMaxLevel" not in pf_ps)
    _over = _after(pf_cap, "public static int overhaulLevelCap(StatsData data) {").split("\n    }\n", 1)[0]
    check("cap is Overhaul's flat configuredLevelMaximum (4.7.14): no ladder, no levelCap hook, 100k without revamp",
          _over.strip() == "return levelCap(data);"
          and '@Inject(method = "levelCap"' not in pf_mixin
          and "journeyPrestigeNeed" not in pf_cap
          and "OVERHAUL_INITIAL_LEVEL_CAP" not in pf_cap and "ladderCap" not in pf_cap and "LADDER_STEPS" not in pf_cap
          and '.getMethod("configuredLevelMaximum")' in pf_cap
          and "return OVERHAUL_LEVEL_CAP;" in _after(pf_cap, "public static int overhaulLevelCap() {").split("\n    }\n", 1)[0])
    check("breakthroughs gone from code paths; stored keys untouched",
          not any(k in pf_pp for k in ("getBreakthroughs", "setBreakthroughs", "breakthroughCost",
                                        "buyBreakthrough", "effectiveMaxLevel"))
          and "adjustBreakthroughs" not in pf_admin and "breakthroughs_max" not in pf_gui
          and "KEY_BREAKTHROUGHS" not in pf_mirror
          and '"pp_level_breakthroughs"' in read(SRC / "data/LmPlayerStore.java"))
    check("held max = LM's 20 everywhere; clamp and gate on Overhaul through the cap mixin (4.7.14)",
          "MAX_HELD" not in pf_ps
          and "LmOverhaulCapMath.configuredMaxPrestigeCount()" in pf_ps
          and pf_mixin.count("LmOverhaulCapMath.configuredMaxPrestigeCount()") == 2
          and "maxPrestigeCount" not in _after(pf_cfg_bridge, "public static void onServerStarting()")
          and "pinPrestigeCap" not in pf_cfg_bridge
          and "return LmOverhaulPrestigeIntegration.OVERHAUL_MAX_PRESTIGE;" in pf_cap)
    check("menus state the rule; next requirement shows the level cap",
          # 4.7.84: "Next prestige at level N" says the rule in fewer words.
          '"Next prestige at level"' in pf_ultra
          and "Reaching your current cap" not in pf_ultra and "Reaching your current cap" not in pf_gui
          and "Reaching your current cap" not in pf_ps
          and 'out.put("next_required", String.valueOf(required))' in pf_gui)
    check("self-test covers the need ladder, both routes, and the held cap (4.7.15)",
          "checkPrestigeFollowsOverhaul(broken);" in pf_test
          and "PrestigeSystem.requiredLevel(null)" in pf_test
          and "{50_000, 65_000, 80_000, 95_000, 110_000, 125_000, 140_000, 150_000" in pf_test
          and "blocksOverhaulPrestige(held, want - 1)" in pf_test
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
    # 4.7.43: SlotRecordCapture and its mixin also name it; they load only when the plugin finds Ultra's slots.
    check("only PrestigeCharacterUltra (and the 4.7.43 slot capture) names the Ultra slots API",
          not any("com.dmzultra.slots" in read(f) for f in SRC.rglob("*.java")
                  if f.name not in ("PrestigeCharacterUltra.java", "PrestigeCharacter.java",
                                    "SlotRecordCapture.java", "DmzultraSlotCaptureMixin.java",
                                    "LegacyMechanicsMixinPlugin.java"))
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
          and "startEmpty(character);" in _after(pc, "public static CompoundTag root(ServerPlayer player) {")
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
    _switch = _after(pc, "static void switchedIn(").split("\n    }\n", 1)[0]
    check("switch: Overhaul count, the new character's purchases, and tier eligibility re-applied on the next tick",
          "CharacterEvent.SwitchIn.class" in pc_ultra
          and ".scheduleSyncAfterStatsReset(p);" in _switch
          and "PrestigePointsSystem.reapplyAllShopPurchases(p);" in _switch
          and "UnlockSystem.syncUnlocks(p, data);" in _switch)
    _born = _after(pc, "static void born(").split("\n    }\n", 1)[0]
    _reset = _after(pc, "private static void resetToFresh(").split("\n    }\n", 1)[0]
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
    _on_switch = _after(pc_ultra, "private static void onSwitchIn(").split("\n    }\n", 1)[0]
    check("manifest blank-switch-in-reset row", "blank-switch-in-reset" in manifest)
    check("switch-in reads the incoming slot's stored record (blank flag, no dmz part; unreadable = not blank)",
          "SlotKey to = event.to();" in _on_switch
          and "blankRecord(player.m_20194_(), to)" in _on_switch
          and "Slots.characterId(to)" in _on_switch
          and 'record.m_128469_("parts").m_128469_("dmz").m_128456_()' in pc_ultra
          and "return false;\n        }\n    }" in _after(pc_ultra, "private static boolean blankRecord("))
    check("a blank incoming character gets the same fresh reset as a birth, every switch, before purchases re-apply",
          "if (resetOnSwitchIn(blank, toCharacter, active)) {" in _switch
          and "resetToFresh(p, account, active);" in _switch
          and _switch.find("resetToFresh(p, account, active);")
              < _switch.find("PrestigePointsSystem.reapplyAllShopPurchases(p);")
          and "claimBirth" not in _switch
          and "return blank && toCharacter != null && toCharacter.equals(active);" in pc)
    check("self-test covers the switch-in reset guard", "resetOnSwitchIn(true, fresh, other)" in pc)

    print("\n--- § ultra noscale jar (4.7.6) ---")
    ns_dir = ROOT / "tools" / "dmzultra-noscale"
    ns_patch = read(ns_dir / "NoScalePatch.java")
    ns_verify = read(ns_dir / "verify.py")
    ns_build = read(ns_dir / "build.sh")
    uf_cfg = read(SRC / "config/DifficultyConfig.java")
    uf_help = read(SRC / "config/editor/ConfigFieldHelp.java")
    uf_reg = read(SRC / "config/ConfigRegistry.java")
    check("manifest ultra-noscale-jar row; ultra-form-scale-off row retired",
          "ultra-noscale-jar" in manifest
          and "| ultra-form-scale-off | Retired in 4.7.6" in manifest)
    check("LM workaround gone: no UltraFormScale, no disableUltraFormScaling, no OverhaulScale call",
          not (SRC / "progression/bridge/UltraFormScale.java").exists()
          and not any("UltraFormScale" in read(f) or "com.dmzultra.form.OverhaulScale" in read(f) or "disableUltraFormScaling" in read(f)
                      for f in SRC.rglob("*.java"))
          and "disableUltraFormScaling" not in uf_cfg + uf_help + uf_reg
          and "ultra-form-scale" not in pc_ev)
    check("blank switch-in reset kept",
          "if (resetOnSwitchIn(blank, toCharacter, active)) {" in pc)
    check("Ultra patch: mixin leaves dmzultra.mixins.json and its class is deleted",
          'MIXIN = "FormDataScaleMixin"' in ns_patch and "case MIXIN_CLASS -> {" in ns_patch
          and "static String dropMixin(String json)" in ns_patch)
    check("Ultra patch: OverhaulScale stays, active() returns false (factorOf is 1.0 for ownerForm)",
          'patchClass(data, "active", "()Z", NoScalePatch::returnFalse)' in ns_patch
          and "Opcodes.ICONST_0" in ns_patch)
    check("Ultra patch: FormsChecks.overhaulScale no longer expects scaled values",
          'patchClass(data, "overhaulScale", "()V", NoScalePatch::logOff)' in ns_patch)
    check("Ultra patch: Build-Time stamped noscale, mod version stays the stock one",
          'STAMP = " noscale"' in ns_patch and "mod version stays the stock one (0.4.0, 0.5.3, 0.5.5, 0.5.7)" in ns_verify
          and "stock.read(\"META-INF/mods.toml\") == patched.read(\"META-INF/mods.toml\")" in ns_verify)
    check("Ultra patch 0.5.3 (not live): sha-gated, FormsChecks kept as shipped (dev-only; never runs in production)",
          'STOCK_SHA_053="0a84d1729e13cec17a9b4bc30bd50ca65b923253a67ec37c2862569b884f8afa"' in ns_build
          and '"$STOCK_SHA_053") VERSION="0.5.3"; CHECKS="keep" ;;' in ns_build
          and 'check("FormsChecks kept as shipped", stock.read(CHECKS) == patched.read(CHECKS))' in ns_verify)
    check("Ultra patch build checks the stock jar's sha256 and runs the verifier",
          'STOCK_SHA="ea63963c59e55ad6cab8ff28967380432c8ee94d7b95a6ee2d58c9ae1b9feacf"' in ns_build
          and 'verify.py" "$STOCK" "$OUT"' in ns_build)

    check("Ultra patch 0.5.7: sha-gated, FormsChecks kept as shipped",
          'STOCK_SHA_057="ca3cbbf1f1e97ac36fe5fbaa77374b3d41868cb93f304a6317bd67259960992a"' in ns_build
          and '"$STOCK_SHA_057") VERSION="0.5.7"; CHECKS="keep" ;;' in ns_build)

    print("\n--- § ultra 0.5.7 placefix (Karakura Town finds ground) ---")
    pf_dir = ROOT / "tools" / "dmzultra-placefix"
    pf_patch = read(pf_dir / "PlaceFixPatch.java")
    pf_fix = read(pf_dir / "PlaceGroundFix.java")
    pf_build = read(pf_dir / "build.sh")
    pf_verify = read(pf_dir / "verify.py")
    check("manifest ultra-057-placefix row", "| ultra-057-placefix |" in manifest)
    check("placefix: built on the noscale build of the sha-pinned stock 0.5.7, then verified",
          'STOCK_SHA_057="ca3cbbf1f1e97ac36fe5fbaa77374b3d41868cb93f304a6317bd67259960992a"' in pf_build
          and 'tools/dmzultra-noscale/build.sh" "$STOCK"' in pf_build
          and 'verify.py" "$NOSCALE" "$OUT"' in pf_build
          and "dmzultra-0.5.7-noscale-placefix.jar" in pf_build)
    check("placefix: PlaceGround.vegetation/natural start with PlaceGroundFix.plant/ground, rest unchanged",
          'prefix(node, "vegetation", "plant");' in pf_patch and 'prefix(node, "natural", "ground");' in pf_patch
          and "is otherwise unchanged" in pf_verify)
    check("placefix: a spot is refused as built after 8 built columns (stock 3); forced path keeps its built check",
          "static final int BUILT_MAX = 8;" in pf_patch and "static final int STOCK_BUILT_MAX = 3;" in pf_patch
          and '("iconst_3", "bipush 8")' in pf_verify)
    check("placefix: plants = non-persistent leaves, bushes/crops/vines, Pam's fruit, DMZ might trees; never a block entity or fluid",
          "block instanceof LeavesBlock" in pf_fix and "BlockStateProperties.f_61447_" in pf_fix
          and "block instanceof BushBlock" in pf_fix and "block instanceof IPlantable" in pf_fix
          and 'ns.equals("pamhc2trees") && path.startsWith("pam")' in pf_fix
          and 'ns.equals("dragonminez") && path.startsWith("might_tree_")' in pf_fix
          and pf_fix.count("state.m_155947_() || !state.m_60819_().m_76178_()") == 2)
    check("placefix: ground = SnowyDirtBlock (grass, slime grass) and Tinkers' slime dirt / congealed slime",
          "block instanceof SnowyDirtBlock" in pf_fix
          and 'path.endsWith("_slime_dirt") || path.endsWith("_congealed_slime")' in pf_fix)
    check("placefix: Build-Time stamped placefix, mod version stays 0.5.7",
          'STAMP = " placefix"' in pf_patch and "mods.toml still says version 0.5.7" in pf_verify)

    print("\n--- § ultra 0.5.7 podfix (port voyages survive leaving the pod) ---")
    pv_dir = ROOT / "tools" / "dmzultra-podfix"
    pv_patch = read(pv_dir / "PodFixPatch.java")
    pv_stub = read(pv_dir / "stub" / "com" / "dmzultra" / "multiverse" / "PodVoyage.java")
    pv_build = read(pv_dir / "build.sh")
    pv_verify = read(pv_dir / "verify.py")
    check("manifest ultra-057-podfix row", "| ultra-057-podfix |" in manifest)
    check("podfix: built on placefix (so on noscale), then verified",
          'tools/dmzultra-placefix/build.sh" "$STOCK"' in pv_build and 'verify.py" "$BASE" "$OUT"' in pv_build
          and "dmzultra-0.5.7-noscale-placefix-podfix.jar" in pv_build)
    check("podfix: advance's left-the-pod cancel calls podfixLeftPod(player, voyage, now); nothing else in PodVoyage changes",
          'static final String WHY = "they left the pod";' in pv_patch and 'HOOK = "podfixLeftPod"' in pv_patch
          and "every other PodVoyage method is unchanged" in pv_verify)
    check("podfix: only port voyages go on (alive, same world); every other case is the stock cancel",
          'if (voyage.port == null || !player.m_6084_() || !player.m_9236_().m_46472_().equals(voyage.from)) {\n            cancel(player, "they left the pod");' in pv_stub)
    check("podfix: the flight still runs out (or a skip ends it) before the trip; the pod left behind is removed on landing",
          "launchTicks(voyage.mode) + flightTicks(voyage.mode, voyage.far)) || voyage.skippedFlight" in pv_stub
          and "source.m_146870_();" in pv_stub)
    check("podfix: a port voyage's landing ticket loads full chunks out to 4 (stock 2), what the ready check reads",
          "static final int PORT_TICKET = 4;" in pv_patch
          and '("iconst_2", "iconst_4")' in pv_verify)
    check("podfix: Build-Time stamped podfix, mod version stays 0.5.7",
          'STAMP = " podfix"' in pv_patch and "mods.toml still says version 0.5.7" in pv_verify)

    print("\n--- § body parts catalog 0.4.0 ---")
    import json as _json
    bp_json = _json.loads(read(SRC.parents[3] / "resources" / "dmzultra-form-parts-0.4.0.json"))
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
          and 'new Expect("0.4.0", 109, 220, 220' in bp_test)

    print("\n--- § gui bridge 4.7.8 ---")
    gb_feat = ""  # 4.8.2: UltraClientFeatures is deleted (no KaiPath bridge); see the Part E replacements below.
    gb_net = read(SRC / "net/gui/LmGuiNetwork.java")
    gb_kit = ROOT / "tools" / "ultra-gui-kit"
    gb_reg = read(gb_kit / "kaipath-client/com/dmzlegacy/kaipath/ultra/consumers/UltraGuiConsumerRegistry.java")
    gb_prev = read(gb_kit / "kaipath-client/com/dmzlegacy/kaipath/ultra/consumers/legacymechanics/BodyPartPreview.java")
    gb_test = read(gb_kit / "test/com/dmzlegacy/kaipath/ultra/consumers/legacymechanics/GuiModelSyncPacketTest.java")
    gb_proto = read(ROOT / "docs" / "KAIPATH_GUI_PROTOCOL.md")
    gb_gaps = read(ROOT / "docs" / "KAIPATH_BRIDGE_GAPS.md")
    gb_login = _after(gb_net, "public static void logClientGuiCapability(").split("\n    }\n", 1)[0]
    check("manifest gui-bridge-478 row", "gui-bridge-478" in manifest)
    # 4.8.2 (Part E): the KaiPath feature advertisement (UltraClientFeatures, its login check and its 1.5.1
    # self-test) is deleted with the bridge. Retired here; the replacements assert the native state.
    check("login gap check waits for the advertisement (no warning at the login event)", False, retired=True)
    check("bodyPartsSync shipped (KaiPath 1.5.1 recommended); message-3 gate kept", False, retired=True)
    check("self-test covers the login check and a full 1.5.1 advertisement", False, retired=True)
    check("4.8.2: no KaiPath advertisement login check; the login probe logs the LM client only",
          not (SRC / "net/gui/UltraClientFeatures.java").is_file()
          and "UltraClientFeatures" not in pc_ev and "pulseLoginChecks" not in pc_ev
          and "LmNativeNetwork.hasNativeClient(player)" in gb_login and "kaipath" not in gb_login.lower())
    check("4.8.2: bodyPartsSync is an LM client hello feature; the per-recipient message-5 gate is kept",
          "return player != null && LmNativeNetwork.takesModelSync(player);" in gb_net
          and "KAIPATH_RECOMMENDED_VERSION" not in gb_net
          and "f.contains(LmNativeProtocol.FEATURE_BODY_PARTS_SYNC)" in read(SRC / "net/gui/LmNativeNetwork.java"))
    check("4.8.2: the native self-test pins the hello-only bodyPartsSync and the message-5 round trip",
          "LmNativeProtocol.MSG_MODEL_SYNC == 5" in read(SRC / "net/gui/LmNativeSelfTest.java")
          and "bodyPartsSync must be hello-only" in read(SRC / "net/gui/LmNativeSelfTest.java")
          and "UltraClientFeatures" not in pc_test.replace("UltraClientFeatures (KaiPath feature advertisement) is gone", ""))
    check("kit advertises after login and on each open, on every channel",
          "MinecraftForge.EVENT_BUS.addListener(UltraGuiConsumerRegistry::onLoggingIn);" in gb_reg
          and "public static void advertiseAll()" in gb_reg
          and gb_reg.count("advertiseFeatures(channelId, ADVERTISED_FEATURES_JSON);") >= 2)
    check("kit world state per <modid>:gui channel, combined for render, tested",
          "public static void applySync(String channelId, String partsJson, String reason)" in gb_prev
          and "static WorldState combine(" in gb_prev
          and "GuiModelSyncPacket.handle(packet, ctx, channelId)" in gb_reg
          and "multiChannelSync();" in gb_test)
    check("protocol + gaps docs: bodyPartsSync shipped, login timing, per-channel state",
          "**Advertised by KaiPath 1.3.31+**" in gb_proto and "context=login+10s" in gb_proto
          and "| Several mods |" in gb_proto
          and "## Closed on KaiPath 1.5.1 / LM 4.7.8" in gb_gaps
          and "Not shipped on any KaiPath yet" not in gb_proto)

    print("\n--- § saiyan tail restore 4.7.9 ---")
    st_help = read(SRC / "character/SaiyanTailWipeHelper.java")
    st_cs = read(SRC / "character/CharacterServicesSystem.java")
    st_flow = read(SRC / "character/RaceChangeCreationFlow.java")
    st_store = read(SRC / "noea/MajinAbsorptionStore.java")
    st_created = _after(st_flow, "public static void onCharacterCreated(").split("\n    }\n", 1)[0]
    check("manifest saiyan-tail-restore row", "saiyan-tail-restore" in manifest)
    check("restore mirrors the clear: tail family only, flag off only, both flags set, viewers resynced",
          "public static void restoreIfTailRace(ServerPlayer player, String via)" in st_help
          and "return !hasTail && TAIL_RACES.contains(normalizeRace(race));" in st_help
          and "ch.setHasSaiyanTail(true);" in st_help and "status.setTailVisible(true);" in st_help
          and "NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);" in st_help
          and 'Set.of("saiyan", "ancient_saiyan", "sento_saiyan", "monkey")' in st_help)
    check("restore runs after the clear on every race change path and on creation completion, never on login",
          st_cs.index("SaiyanTailWipeHelper.restoreIfTailRace(")
              > st_cs.index("SaiyanTailWipeHelper.clearIfRaceCannotHaveTail(")
          and "SaiyanTailWipeHelper.restoreIfTailRace(" in st_created
          and "restoreIfTailRace" not in pc_ev)
    check("Noea absorption reference removed; wipe entry points kept",
          "com.butterjaffa" not in st_store
          and "public static void clear(Player player)" in st_store
          and "public static void clearIfRaceChanges(StatsData stats, String nextRace)" in st_store)
    check("self-test covers the tail restore rule", "SaiyanTailWipeHelper.selfTest(broken);" in pc_test)

    print("\n--- § model sync 4.7.10 ---")
    ms_pkt = read(SRC / "net/gui/GuiModelSyncPacket.java")
    ms_net = read(SRC / "net/gui/LmGuiNetwork.java")
    ms_adv = _after(ms_net, "public static void onClientAdvertisement(").split("\n    }\n", 1)[0]
    ms_send = _after(ms_net, "public static boolean sendModelSync(").split("\n    }\n", 1)[0]
    check("manifest model-sync-478-hardening row", "model-sync-478-hardening" in manifest)
    check("degrade keeps equipped pieces: optional fields first, never an empty bodyParts for a player with pieces",
          '{"headBones", "form", "extras", "colors"}' in ms_pkt
          and "sending empty parts" not in ms_pkt
          and "pieces.remove(0);" in ms_pkt and "pieces.size() > 1" in ms_pkt
          and "PARTS_JSON_MAX = 8192;" in ms_pkt)
    check("login sync marked done only after it was sent; skipped attempts retry",
          "MODEL_SYNC_PENDING.add(id)" in ms_adv
          and ms_adv.rindex("MODEL_SYNCED.add(id);") > ms_adv.index("sendModelSync(viewer, GuiModelSyncPacket.REASON_LOGIN)")
          and "MODEL_SYNC_PENDING.remove(player.m_20148_());" in ms_net)
    check("message 3 gated per recipient (owner + advertising players), no blanket broadcast",
          "for (ServerPlayer viewer : modelSyncRecipients(player))" in ms_send
          and "TRACKING_ENTITY_AND_SELF" not in ms_net
          and "&& supportsModelSync(other)) {" in ms_net)
    check("boot self-test: a full set of pieces is sent whole; failure is loud, never fatal",
          "static String fullSetModelSync()" in ms_net
          and "String fullSet = fullSetModelSync();" in ms_net
          and "GuiModelSync full-set self-test FAILED" in ms_net
          and "failures.add(fullSet)" not in ms_net)

    print("\n--- § character home index 4.7.11 ---")
    ch_scr = read(SRC / "gui/ultra/UltraCharacterScreens.java")
    ch_home = _after(ch_scr, "private static void openHome(").split("\n    }\n", 1)[0]
    ch_bones = _after(ch_scr, "private static void openBones(").split("\n    private static void ", 1)[0]
    ch_parts = _after(ch_scr, "private static void openBodyParts(").split("\n    private static void ", 1)[0]
    check("manifest character-home-index row", "character-home-index" in manifest)
    # 4.7.84 (C-2): the tabs are Home / Looks / Story. Home has race, class and Android; Looks has every way to
    # change how you look (model and colors, body parts, part colors, skin, and staff's Preview forms).
    ch_looks = _after(ch_scr, "private static void openLooks(").split("\n    }\n", 1)[0]
    check("tabs are Home, Looks, Story (4.7.84; were Home, Saga, Android)",
          '.tabs("home|Home", PAGE_LOOKS + "|Looks", "story|Story")' in ch_scr)
    check("Home and Looks list their destinations once each, in order, and not the tabs",
          [a for a in ["page:race:0", "page:class:0"] if ch_home.count('"' + a + '"') == 1] == ["page:race:0", "page:class:0"]
          and [a for a in ["page:bones:0", "page:bodyparts:0", "page:reskin"] if ch_looks.count('"' + a + '"') == 1]
              == ["page:bones:0", "page:bodyparts:0", "page:reskin"]
          and "tab:saga" not in ch_home and "tab:android" not in ch_home and "saga_reset" not in ch_home
          and "placeAndroidButton(spec, android);" in ch_home
          and ch_home.index('"page:race:0"') < ch_home.index('"page:class:0"')
          and ch_looks.index('"page:bones:0"') < ch_looks.index('"page:bodyparts:0"') < ch_looks.index('"page:reskin"'))
    # 4.7.48: Preview forms is a destination too (on Looks since 4.7.84).
    check("each Home and Looks entry has a plain line when blocked; blocked entries stay listed, dimmed, with a reason",
          ch_home.count("blockLine(spec, ") == 3 and ch_looks.count("blockLine(spec, ") == 4
          and ch_looks.index('"page:reskin"') < ch_looks.index('"page:" + PAGE_FORM_PREVIEW + ":0"')
          and "CosmeticBodyPartService.equippedParts(player).size()" in ch_home
          and 'return "Turned off on this server right now.";' in ch_scr
          and 'return "Not available for your rank. Ask staff which rank unlocks this.";' in ch_scr
          and 'return "Available again in " + cooldownWait + ".";' in ch_scr
          and "spec.lockedButton(locked)" in _after(ch_scr, "private static void indexButton("))
    check("Body parts reached from Looks; its Back returns to Looks; Home footer Back to main menu (4.7.84)",
          "page:bodyparts:0" not in ch_bones
          and 'footerBack("page:bones:0", "Model")' not in ch_scr
          and ".back()" in ch_parts and ".back()" in ch_home
          and 'p("character", "bodyparts:0", "Body parts", "character|looks"' in read(SRC / "gui/ultra/PageRegistry.java"))
    check("self-test covers the Home index rules", "UltraCharacterScreens.selfTest(broken);" in pc_test)

    print("\n--- § prestige cap 20 4.7.12 ---")
    pc20_int = read(SRC / "progression/LmOverhaulPrestigeIntegration.java")
    pc20_math = read(SRC / "progression/LmOverhaulCapMath.java")
    pc20_json = read(MOD / "src/main/resources/legacymechanics.mixins.json")
    pc20_plugin = read(SRC / "mixin/LegacyMechanicsMixinPlugin.java")
    pc20_spar = read(SRC / "sparring/SparCombat.java")
    pc20_cfg = read(SRC / "config/DifficultyConfig.java")
    pc20_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    check("manifest prestige-cap-20 row", "prestige-cap-20" in manifest)
    check("cap 20: constant, Spar and reference follow (Overhaul side: see dmzrevamp 2.2.4 section)",
          "public static final int OVERHAUL_MAX_PRESTIGE = 20;" in pc20_int
          and "public static final int MAX_PRESTIGE_LEVEL = 20;" in pc20_spar
          and "public int referenceMaxPrestige = 20;" in pc20_cfg
          and "cfg.referenceMaxPrestige20MigratedV1 = Boolean.TRUE;" in pc20_cfg)
    check("ladder gone with dmzrevamp 2.2.4: no ladder mixin, constants or pin (4.7.14)",
          not (SRC / "mixin/DmzRevampPrestigeLadderMixin.java").exists()
          and "DmzRevampPrestigeLadderMixin" not in pc20_json
          and "DmzRevampPrestigeLadderMixin" not in pc20_plugin
          and "LADDER_STEPS" not in pc20_math and "pinPrestigeCap" not in pc20_math
          and "Overhaul ladder" not in pc20_test)

    print("\n--- § prestige exchange packs 4.7.13 ---")
    xp_pts = read(SRC / "progression/shop/PrestigePointsSystem.java")
    xp_cfg = read(SRC / "progression/shop/PrestigeShopConfig.java")
    xp_scr = read(SRC / "gui/ultra/UltraPrestigeScreens.java")
    xp_api = read(SRC / "gui/ProgressionGuiApi.java")
    xp_help = read(SRC / "command/LmCommandHelp.java")
    xp_sug = read(SRC / "command/LmCommandSuggestions.java")
    xp_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    check("manifest prestige-exchange-packs-18 row", "prestige-exchange-packs-18" in manifest)
    check("packs 1, 2, 3, 6, 9, 12, 15, 18 in code, config default and tab completion",
          "TURN_IN_AMOUNTS = {1, 2, 3, 6, 9, 12, 15, 18};" in xp_pts
          and "DEFAULT_AMOUNTS = List.of(1, 2, 3, 6, 9, 12, 15, 18);" in xp_cfg
          and '"1", "2", "3", "6", "9", "12", "15", "18"' in xp_sug
          and "/lm prestige turnin <runs>" in xp_help and "(1, 2, 3, 6, 9, 12, 15 or 18)" in xp_help)
    check("bonus table 3->4, 6->8, 9->13, 12->18, 15->23, 18->28",
          "1, 0, 2, 0, 3, 1, 6, 2, 9, 4, 12, 6, 15, 8, 18, 10);" in xp_cfg)
    check("old saved default list gains the new packs once; a staff list is kept",
          "OLD_DEFAULT_AMOUNTS = List.of(1, 2, 3, 6, 9);" in xp_cfg
          and "public boolean packs18MigratedV1 = false;" in xp_cfg
          and "Exchange.migratePacks18(exchange)" in xp_cfg
          and "if (!packsMigrated || !godKiMigrated) {" in xp_cfg)
    check("Bulk line and lore list every bonus pack; reply bonus is gained minus amount",
          "bulkLine(amounts, PrestigePointsSystem::pointsForTurnIn)" in xp_scr
          and "Trade more at once for a bonus: 3 for 4 favor, 6 for 8, 9 for 13, 12 for 18, 15 for 23," in xp_scr
          and "§f15§7, or §f18 §7at a time." in xp_api
          and "(gained - want)" in xp_pts)
    check("every exchange button shows its favor gain in a label that fits",
          'return "Trade " + amount + " for " + gain + " favor";' in xp_scr  # 4.7.84 (P-3)
          and "? exchangeLabel(amount, gain)" in xp_scr
          and "String label = exchangeLabel(packs[i], got);" in xp_scr)
    check("self-test covers exchange payouts, Bulk line, button fit and migration",
          "UltraPrestigeScreens.exchangeSelfTest(broken);" in xp_test)

    print("\n--- § dmzrevamp 2.2.4 4.7.14 ---")
    r24_mix = read(SRC / "mixin/DmzRevampPrestigeCapMixin.java")
    r24_math = read(SRC / "progression/LmOverhaulCapMath.java")
    r24_bridge = read(SRC / "progression/bridge/DmzRevampPrestigeBridge.java")
    r24_cfg = read(SRC / "progression/bridge/DmzRevampConfigBridge.java")
    r24_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    r24_build = read(MOD / "build.sh")
    r24_probe = _after(r24_test, "public static void checkOverhaulFlatCapAndCount()").split("\n    }\n", 1)[0]
    check("manifest dmzrevamp-224-prestige row", "dmzrevamp-224-prestige" in manifest)
    check("no hook or read of 2.2.3-only API (hexStatReference, levelCap, initialLevelCap, maxPrestigeCount)",
          "hexStatReference" not in r24_mix.split("@Mixin", 1)[1]
          and '"levelCap"' not in r24_mix and "PrestigeSystem.levelCap(" not in r24_bridge
          and '"initialLevelCap"' not in r24_math and '"maxPrestigeCount"' not in r24_math
          and '"initialLevelCap"' not in r24_test and '"maxPrestigeCount"' not in r24_test)
    check("Overhaul max = configuredLevelMaximum; fallback 150k with dmzrevamp; no player = that max",
          '.getMethod("configuredLevelMaximum")' in r24_math
          and "return OVERHAUL_ABSOLUTE_LEVEL_CAP;" in _after(r24_math, "public static int overhaulLevelCap() {").split("\n    }\n", 1)[0]
          and "return LmOverhaulCapMath.overhaulLevelCap();" in r24_bridge)
    check("cap 20 on Overhaul: setCount clamp + canPrestige and canPrestigeAuthoritative veto, all require = 0",
          '@Inject(method = "canPrestigeAuthoritative"' in r24_mix
          and '@Inject(method = "canPrestige"' in r24_mix
          and '@ModifyVariable(method = "setCount"' in r24_mix
          and r24_mix.count("require = 0, remap = false)") == 4
          and "cir.setReturnValue(true)" not in r24_mix
          and "private static boolean lm$blocked(StatsData data)" in r24_mix)
    check("server-start probe warns, never fails the boot",
          "UltraScreenSelfTest.checkOverhaulFlatCapAndCount();" in r24_cfg
          and "broken.add" not in r24_probe and "throw " not in r24_probe
          and '"canPrestigeAuthoritative"' in r24_probe and "LOGGER.warn" in r24_probe)
    check("LM compiles against dmzrevamp 2.2.4",
          'REVAMP="${DMZREVAMP_JAR:-$ROOT/libraries/dmzrevamp-2.2.4.jar}"' in r24_build)

    print("\n--- § prestige need ladder 4.7.15 ---")
    nl_math = read(SRC / "progression/LmOverhaulCapMath.java")
    nl_mix = read(SRC / "mixin/DmzRevampPrestigeCapMixin.java")
    nl_ps = read(SRC / "progression/shop/PrestigeSystem.java")
    nl_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    nl_req = _after(nl_math, "public static int prestigeRequirement(int held) {").split("\n    }\n", 1)[0]
    check("manifest prestige-need-ladder row", "prestige-need-ladder" in manifest)
    check("need ladder 50k..140k for 0-6 held, then 150k, never above the cap",
          "PRESTIGE_NEED = {50_000, 65_000, 80_000, 95_000, 110_000, 125_000, 140_000};" in nl_math
          and "PRESTIGE_NEED_TOP = 150_000;" in nl_math
          and "return Math.min(need, overhaulLevelCap());" in nl_req)
    check("level cap untouched: flat configuredLevelMaximum",
          '.getMethod("configuredLevelMaximum")' in nl_math
          and "prestigeRequirement" not in _after(nl_math, "public static int overhaulLevelCap() {").split("\n    }\n", 1)[0])
    check("both routes share the ladder: LM menu requiredLevel and Overhaul's gate",
          "LmOverhaulCapMath.prestigeRequirement(" in nl_ps
          and "LmOverhaulCapMath.blocksOverhaulPrestige(count, data.getLevel())" in nl_mix
          and "|| level < prestigeRequirement(count);" in nl_math
          and '@Inject(method = "canPrestigeAuthoritative"' in nl_mix)
    check("self-test and server-start probe cover the ladder on both routes",
          "blocksOverhaulPrestige(held, want - 1)" in nl_test
          and 'completeQuest("prestige_saga:3")' in nl_test)

    print("\n--- § first godform grant 4.7.25 ---")
    gf_choice = read(SRC / "progression/form/FirstSuperformChoice.java")
    gf_grant = read(SRC / "progression/form/FirstSuperformMastery.java")
    gf_mech = read(SRC / "command/MechanicsCommands.java")
    gf_kind = _after(gf_mech, "private static FirstSuperformChoice.Kind kindFromToken(").split("\n    }\n", 1)[0]
    gf_help = read(SRC / "command/LmCommandHelp.java")
    check("manifest first-godform-grant row", "first-godform-grant" in manifest)
    check("Kind.GOD picks the earliest godform like LEGENDARY, no level-0 rule",
          "LEGENDARY,\n        GOD" in gf_choice and 'inTier(candidate, "godform")' in gf_choice
          and "kind == Kind.LEGENDARY || kind == Kind.GOD" in gf_choice
          and "Candidate tier = earliest(forms, kind, 0);" in gf_choice
          and "kind == Kind.GOD ? !isGodform(candidate) : !isLegendary(candidate)" in gf_choice
          and "return earliest(forms, Kind.SUPER, 1);" in gf_choice)
    check("command reuses masteryGrant; god token checked first; notice and no-form message",
          'masteryGrant("grant-first-godform", FirstSuperformChoice.Kind.GOD)' in gf_mech
          and gf_kind.index('contains("god")') < gf_kind.index('contains("legendary")') < gf_kind.index('contains("super")')
          and '"Mastery was granted on your first god form."' in gf_mech
          and "has no god form to grant mastery to." in gf_grant and "first god form" in gf_grant)
    check("help and tab completion list grant-first-godform",
          all(f"/lm mastery grant-first-godform {s}" in gf_help for s in ("<amount>", "<amount> <player>", "<player> <amount>"))
          and '"grant-first-godform".equals(sub)' in read(SRC / "command/LmCommandSuggestions.java"))

    def _pick(forms, kind):  # mirror of FirstSuperformChoice.select
        need = {"SUPER": "superform", "LEGENDARY": "legendaryform", "GOD": "godform"}[kind]
        ok = [f for f in forms if need in f[2].lower() and (kind != "SUPER" or f[3] >= 1)]
        return min(ok, key=lambda f: (f[3], f[0].lower(), f[1].lower()))[1] if ok else None

    saiyan = [("oozaru", "oozaru", "superforms", 0), ("ssgrades", "ssj1", "superforms", 1),
              ("legendary", "lssj", "legendaryforms", 5), ("godforms", "ssg", "godforms", 20),
              ("godforms", "ssb", "godforms", 30)]
    check("sim: Saiyan god -> ssg, super -> ssj1, legendary -> lssj; no god forms -> None",
          _pick(saiyan, "GOD") == "ssg" and _pick(saiyan, "SUPER") == "ssj1"
          and _pick(saiyan, "LEGENDARY") == "lssj" and _pick(saiyan[:3], "GOD") is None)

    print("\n--- § god ki wallet 10k 4.7.34 ---")
    gw_cfg = read(SRC / "progression/shop/PrestigeShopConfig.java")
    gw_gk = read(SRC / "progression/shop/GodKiEnhancementShop.java")
    gw_kt = read(SRC / "progression/shop/KiMasteryTrack.java")
    gw_hooks = read(SRC / "progression/shop/PrestigeShopHooks.java")
    gw_mig = _after(gw_cfg, "public static boolean migrateMaxPoints10(GodKi godKi) {").split("\n        }\n", 1)[0]
    check("manifest godki-wallet-10k row", "godki-wallet-10k" in manifest)
    check("God Ki / Divine Ki tracks default to 10 levels x 1,000 mastery",
          "public int maxPoints = 10;" in gw_cfg and "public int masteryPerPoint = 1000;" in gw_cfg
          and "public static final int MAX_INVESTMENTS = 10;" in gw_gk
          and "return PrestigeShopConfig.get().godKi.maxPoints;" in gw_gk
          and "return Math.max(0, GodKiEnhancementShop.maxInvestments());" in gw_kt)
    check("saved cap 5 (old default) raised to 10 once; a staff-set cap is kept",
          "public boolean maxPoints10MigratedV1 = false;" in gw_cfg
          and "public static final int OLD_DEFAULT_MAX_POINTS = 5;" in gw_cfg
          and "if (godKi.maxPoints10MigratedV1) {" in gw_mig and "godKi.maxPoints10MigratedV1 = true;" in gw_mig
          and "if (godKi.maxPoints != OLD_DEFAULT_MAX_POINTS) {" in gw_mig and "godKi.maxPoints = 10;" in gw_mig
          and gw_mig.index("maxPoints10MigratedV1 = true") < gw_mig.index("godKi.maxPoints = 10")
          and "GodKi.migrateMaxPoints10(godKi)" in gw_cfg
          and "|| !godKiMigrated" in gw_cfg)
    check("Divine Ki line opens at the God Ki cap (level max or godki mastery max x 1,000)",
          "GOD_KI.level(player) >= max" in gw_kt
          and "GOD_KI.formMastery(player) >= (double) max * masteryPerLevel()" in gw_kt)
    check("config help covers the cap and its migration flag",
          '"godKi.maxPoints10MigratedV1"' in gw_hooks and "default 10 = 10,000 mastery" in gw_hooks)

    print("\n--- § lm 4.7.58 ---")
    p58_proto = read(SRC / "net/gui/LmNativeProtocol.java")
    p58_net = read(SRC / "net/gui/LmNativeNetwork.java")
    p58_bridge = read(SRC / "net/gui/LmGuiNetwork.java")
    p58_mod = read(SRC / "AdaptiveDifficultyMod.java")
    p58_cfg = read(SRC / "config/DifficultyConfig.java")
    p58_events = read(SRC / "event/DifficultyEvents.java")
    p58_screen = read(SRC / "client/NativeMenuScreen.java")
    p58_model = read(SRC / "client/NativeModelPreview.java")
    p58_client = read(SRC / "client/LmClient.java")
    p58_build = read(MOD / "build.sh")
    p58_toml = read(MOD / "src/main/resources/META-INF/mods.toml")
    check("manifest row for lm 4.7.58", "lm-4758-native-client" in manifest)
    check("native channel legacymechanics:native_ui, protocol 1, clicks reuse the bridge codec",
          'CHANNEL_PATH = "native_ui";' in p58_proto and 'PROTOCOL_VERSION = "1";' in p58_proto
          and "GuiActionPacket::encode" in p58_net and "GuiOpenPacket::encode" in p58_net
          and "NetworkRegistry.acceptMissingOr(LmNativeProtocol.PROTOCOL_VERSION)" in p58_net)
    route_at = p58_bridge.find("switch (LmNativeNetwork.route(player, wire))")
    # 4.8.2 (Part E): no bridge send any more; a menu goes native or nowhere (install / update message).
    check("each menu goes native or nowhere (4.8.2: the bridge send is gone)",
          route_at > 0 and "CHANNEL.send(" not in p58_bridge and "bridgeSkin" not in p58_bridge
          and "case NATIVE -> LmNativeNetwork.sendOpen(player, wire);" in p58_bridge
          and "LmNativeNetwork.requiredClientMessage()" in p58_bridge
          and "public static Route decide(" in p58_net)
    # A KaiPath-only client can list LM channel names in minecraft:register; only the handshake counts.
    check("LM client detected from the Forge login handshake only (mod list + channel)",
          "data.getChannels().containsKey(CHANNEL_ID)" in p58_net
          and "data.getModList().contains(AdaptiveDifficultyMod.MOD_ID)" in p58_net
          and "isRemotePresent" not in p58_net)
    check("a menu the LM client cannot fully draw is refused, never sent to KaiPath (4.8.2)",
          "if (kaipath) {" not in p58_net and "BRIDGE," not in p58_net and "Route.REFUSE" in p58_net
          and "return Route.NO_CLIENT;" in p58_net
          and '"combined.forms.v1"' in p58_proto and "FEATURE_FORMS" in p58_proto
          and '"combined.forms.v1",' not in p58_proto.split("CLIENT_FEATURES = List.of(")[1].split(");")[0])
    check("requireLmClient ships ON (4.8.2: the LM client is mandatory) and turns away a no-client login with a plain message",
          "public boolean requireLmClient = true;" in p58_cfg
          and "rejectWithoutClient(" in p58_events
          and p58_events.find("rejectWithoutClient(") < p58_events.find("logClientGuiCapability(player)")
          and "This server needs the Legacy Mechanics client mod." in p58_net)
    check("client classes stay isolated: booted by name on a game client, compiled in their own pass",
          'CLIENT_BOOT_CLASS = "com.dbzlegacy.adaptivedifficulty.client.LmClient";' in p58_mod
          and "import com.dbzlegacy.adaptivedifficulty.client" not in p58_mod
          and "-not -path \"*/$CLIENT_PKG/*\"" in p58_build and "@$CLIENT_LIST" in p58_build
          and "LmGuiNetwork" not in p58_client)
    check("native renderer: locked buttons inert, spin resets forward, scale-to-fit with a 0.5x floor",
          'NO_ACTION = "nav:none";' in p58_screen and "if (isNoAction(b.id())) {" in p58_screen
          # 4.7.60: front-facing is explicit (body yaw 180, front toward the viewer, never the back).
          and "yaw = LmNativeProtocol.spinAfterPacket(yaw, rotate);" in p58_model
          and "LmNativeProtocol.previewFacing(yaw)" in p58_model
          and "PREVIEW_FRONT_YAW = 180f;" in p58_proto and "return rotate ? spin : 0f;" in p58_proto
          and "NativeMenuFit.place(" in p58_screen
          and "MIN_SCALE = 0.5f;" in read(SRC / "net/gui/NativeMenuFit.java"))
    # 4.8.2: the kaipath dependency (one of the three BOTH entries) is gone with the bridge.
    check("mods.toml loads LM on both sides", p58_toml.count('side = "BOTH"') >= 2
          and 'modId = "kaipath"' not in p58_toml
          and "LmNativeSelfTest.run();" in p58_bridge)
    print("\n--- § lm 4.8.3 ---")
    c83 = SRC / "command"
    q83_da = read(c83 / "DojoAdminCommands.java")
    q83_sys = read(c83 / "AdminSystemsCommands.java")
    q83_spar = read(c83 / "SparCommands.java")
    q83_riv = read(c83 / "RivalCommands.java")
    q83_dif = read(c83 / "DifficultyCommands.java")
    q83_help = read(c83 / "LmCommandHelp.java")
    q83_ds = read(SRC / "dojo/DojoService.java")
    q83_war = read(SRC / "dojo/DojoWar.java")
    q83_world = read(SRC / "dojo/DojoWorld.java")
    q83_board = read(SRC / "dojo/BoardSpar.java")
    q83_ss = read(SRC / "sparring/SparringSystem.java")
    q83_rs = read(SRC / "rival/RivalStore.java")
    q83_clear = read(SRC / "data/PlayerDataClear.java")
    q83_us = read(SRC / "tier/UnlockSystem.java")
    q83_wg = read(SRC / "goals/WeeklyGoals.java")
    q83_saga = read(SRC / "quest/SagaResetService.java")
    q83_sc = read(SRC / "progression/shop/SkillCheckService.java")
    q83_probe = (ROOT / "tools/lm-ui-probe/src/com/dbzlegacy/uiprobe/UiProbe.java").read_text(encoding="utf-8")
    check("manifest row for lm 4.8.3", "lm-483-admin-commands" in manifest)
    # A: /lm admin dojo
    check("A: /lm admin dojo registered under /lm admin with every leaf",
          "CommandAccess.underLmAdmin(" in q83_da and "DojoAdminCommands.register()" in q83_spar
          and all(('m_82127_("' + leaf + '")' in q83_da or 'memberLeaf("' + leaf + '")' in q83_da) for leaf in (
              "help", "status", "save", "reload", "points", "clout", "close", "reopen", "disband", "rename",
              "style", "banner", "rebuild", "war", "cancel", "promote", "demote", "warrights", "found", "season",
              "advance", "confirm")))
    check("A: staff overrides and new dojo methods (disband clears the plot, forced rebuild, war cancel)",
          all(m in q83_ds for m in ("grantPoints(", "grantClout(", "closeByStaff(", "reopenByStaff(",
                                     "renameByStaff(", "styleByStaff(", "bannerByStaff(", "promoteByStaff(",
                                     "demoteByStaff(", "warRightsByStaff(", "foundByStaff(", "disband(",
                                     "reloadByStaff("))
          and "public static" in q83_war.split("staffCancel(", 1)[0][-200:]
          and "clearPlot(" in q83_world and "rebuild(" in q83_world)
    check("A: /lm admin clear <player> dojo (also in all)", '"dojo"' in q83_clear)
    check("A: old /lm dojo season advance still registered", 'staffLiteral("season")' in q83_spar)
    # B: /lm admin spar
    check("B: spar end / board remove / bout cancel / reset leaves and methods",
          all(t in q83_spar for t in ('m_82127_("end")', 'm_82127_("board")', 'm_82127_("bout")',
                                      'm_82127_("reset")'))
          and "staffEndSession(" in q83_ss and "resetSparRecord(" in q83_ss
          and "staffRemovePost(" in q83_board and "staffCancelBout(" in q83_board)
    # C: /lm admin rival
    check("C: rival rp set|add, challenge cancel, season advance (archive, RP to 0)",
          all(t in q83_riv for t in ('m_82127_("rp")', 'm_82127_("challenge")', 'm_82127_("season")'))
          and "advanceSeason(" in q83_rs and "seasonArchive" in q83_rs and "adjustTotalRp(" in q83_rs)
    # D-F: saga, skillcheck, goals
    check("D-F: /lm admin saga, skillcheck, goals",
          all('m_82127_("' + t + '")' in q83_sys for t in ("saga", "skillcheck", "goals", "unlock", "inspect",
                                                           "grant"))
          and "adminReset(" in q83_saga and "adminUnlock(" in q83_saga and "clearSessionIfAny(" in q83_sc
          and "adminGrant(" in q83_wg and "adminReset(" in q83_wg)
    # G: tier override
    check("G: difficulty tier grant/revoke persists through unlock resync",
          'tierRoot("tier")' in q83_dif and "grantStaffTier(" in q83_us and "revokeStaffTier(" in q83_us
          and "hasStaffGrantedTier(" in q83_us)
    # H: help lists the subtrees
    check("H: /lm admin help lists dojo, saga, skillcheck, goals and the tier and clear-dojo lines",
          all(t in q83_help for t in ('"/lm admin dojo help"', '"/lm admin saga help"', '"/lm admin skillcheck help"',
                                      '"/lm admin goals help"', '"/lm admin difficulty tier grant <player> <tier>"',
                                      "rival|spar|difficulty|progression|dojo")))
    check("H: Mohist name tab completion knows the new player slots",
          "private static boolean adminPlayerSlot483(String cmd, String[] args)" in
          read(c83 / "LmCommandSuggestions.java")
          and read(c83 / "LmCommandSuggestions.java").count("adminPlayerSlot483(cmd, args)") == 2)
    check("probe: V483 admin checks wired (full run and -Duiprobe.only=v483)",
          q83_probe.count("v483Admin();") == 2 and "V483AdminDojo.run(player, this::note);" in q83_probe
          and "V483AdminSparRival.run(player, this::note);" in q83_probe
          and "V483AdminSystems.run(player, this::note);" in q83_probe
          and "GameType.CREATIVE);\n            V483AdminDojo" in q83_probe.replace("\r\n", "\n")
          and '"v483".equals(set)' in q83_probe)
    print("\n--- § lm 4.8.2 ---")
    q_ds = read(SRC / "dojo/DojoService.java")
    q_store = read(SRC / "dojo/DojoStore.java")
    q_state = read(SRC / "dojo/DojoState.java")
    q_placer = read(SRC / "dojo/DojoPlacer.java")
    q_world = read(SRC / "dojo/DojoWorld.java")
    q_found = read(SRC / "gui/ultra/DojoFounding.java")
    q_edit = read(SRC / "gui/ultra/DojoBannerEditor.java")
    q_bd = read(SRC / "gui/ultra/BannerDesign.java")
    q_scr = read(SRC / "gui/ultra/UltraDojoScreens.java")
    q_rank = read(SRC / "sparring/DojoRankings.java")
    q_nms = read(SRC / "client/NativeMenuScreen.java")
    q_cfg = read(SRC / "config/DifficultyConfig.java")
    q_nn = read(SRC / "net/gui/LmNativeNetwork.java")
    q_gn = read(SRC / "net/gui/LmGuiNetwork.java")
    q_np = read(SRC / "net/gui/LmNativeProtocol.java")
    q_ug = read(SRC / "gui/ultra/UltraGui.java")
    q_hud = read(SRC / "gui/ultra/LmHud.java")
    q_skins = read(SRC / "gui/ultra/MenuSkins.java")
    q_selft = read(SRC / "net/gui/LmNativeSelfTest.java")
    q_res = MOD / "src/main/resources"
    q_probe = ROOT / "tools/lm-ui-probe"
    check("manifest row for lm 4.8.2", "lm-482-banners-mandatory" in manifest)
    # A: the editor builds a vanilla banner (registry patterns, up to 6 layers) and saves through setBanner.
    check("A: banner editor page saves base + ordered layers through DojoService.setBanner",
          'PAGE_EDIT = "dojo_banner_edit"' in q_edit
          and "DojoService.setBanner(p, e.base(), new ArrayList<>(e.layers()))" in q_edit
          and "DojoBannerEditor.owns(lower)" in q_found and '"Design a banner here' in q_found)
    check("A: pattern choices come from the vanilla banner pattern registry (no plain base), 6 layers max",
          "BuiltInRegistries.f_256878_" in q_bd and '"b".equals(hash)' in q_bd and "MAX_LAYERS = 6" in q_bd)
    check("A: the preview is the saved design drawn with its layers (lmbanner icon, banner cloth on the client)",
          "spec.icon(icon)" in q_edit and "BannerIcons.drawCloth(g, banner" in q_nms
          and "BannerIcons.isBanner(b.icon())" in q_nms
          and "BannerDesign.isIcon(trimmed)" in read(SRC / "gui/ultra/UltraStyle.java"))
    check("A: banner cost rule unchanged (first free, then 20 points + the 12h cooldown)",
          '"Save this banner (free)"' in q_edit and '"Save - needs "' in q_edit
          and "DojoService.identityChangeProblem(d, DojoCatalog.BANNER_CHANGE_COST)" in q_edit)
    # B: the built hall flies the picked banner.
    check("B: the build signature has a banner part; a banner change rebuilds the hall",
          '"|b=" + banner;' in q_state and 's.banner = p.length > 10 && p[10].startsWith("b=")' in q_state
          and "if (was != null && !was.banner.equals(now.banner)) {" in q_placer
          and "job.banner = DojoPlacer.design(d);" in q_world
          and "DojoWorld.ensureBuilt(p.m_20194_(), d);" in q_ds)
    check("B: every structure banner spot gets the picked base and pattern layers in order",
          "t.banner[i] = t.palette[i].m_60734_() instanceof AbstractBannerBlock;" in q_placer
          and "putBanner(j.level, pos.m_7949_(), t.palette[si], j.banner);" in q_placer
          and 'p.m_128405_("Color", l.colour().m_41060_());' in q_placer and "banner.m_142466_(tag);" in q_placer
          and "public static List<BannerSpot> bannersOn(ServerLevel level, int plot)" in q_world)
    check("B: an older hall without a pick is not rebuilt on upgrade (signature normalized)",
          "sig.equals(DojoState.normalize(d.builtSignature))" in q_world)
    # C: other players see the picked banner; the legacy colour is not read for display.
    check("C: dojo view, list, war pick/format/view show the dojo's banner",
          'UltraStyle.labelValue("Banner", DojoFounding.bannerText(d, 40))' in q_scr
          and ".icon(DojoFounding.bannerIcon(d))" in q_scr and '"Their banner' in q_scr
          and "warSide(spec, 0, w.fromDojo" in q_scr)
    check("C: dojos.json design wins; legacy bannerMaterial kept but not read for display",
          "DojoFounding.bannerIcon(" in q_rank
          and "profile.bannerMaterial" not in q_rank.split("dojoBannerMaterial", 1)[1].split("\n    }", 1)[0]
          and "public String bannerMaterial" in read(SRC / "sparring/SparStore.java"))
    # D: one free rename, any time, no cooldown; the command path follows the same rule; one name everywhere.
    check("D: free rename flag (no migration), skips cost and cooldown, then the 20-point rule",
          "public boolean freeRenameUsed;" in q_store and "public static boolean freeRenameLeft(Dojo d)" in q_ds
          and "d.freeRenameUsed = true;" in q_ds
          and q_ds.index("if (freeRenameLeft(d)) {") < q_ds.index(
              "String bad = identityChangeProblem(d, DojoCatalog.NAME_CHANGE_COST);"))
    check("D: rename copy shows Free and the free-rename lock line",
          'UltraStyle.labelValue("This rename", "Free")' in q_found and '"Rename (free)"' in q_found
          and ". Your free rename works now." in q_scr and "Your first rename is free and locks nothing." in q_scr)
    check("D: /lm dojo name goes through DojoService.rename; the ladder shows the dojo's name",
          "DojoService.rename(master, rawName)" in q_rank and "DojoService.byMaster(dojoKey)" in q_rank
          and "DojoRankings.syncDisplayName(d.masterUuid, n);" in q_ds)
    # E: the LM client is mandatory; the KaiPath bridge is gone.
    check("E: requireLmClient defaults to true", "public boolean requireLmClient = true;" in q_cfg)
    check("E: no bridge route; no client is turned away with the install message",
          "return Route.NO_CLIENT;" in q_nn and "BRIDGE," not in q_nn and "clientHasKaiPath" not in q_nn
          and "case NATIVE -> LmNativeNetwork.sendOpen(player, wire);" in q_gn
          and "LmNativeNetwork.requiredClientMessage()" in q_gn
          and "bridgeSkin" not in q_gn and "static final String KAIPATH_" not in q_gn and "NetworkRegistry.newSimpleChannel" not in q_gn)
    check("E: bridge-only files deleted (features model, features packet, KaiPath mixin, kaipath-ultra.txt)",
          not (SRC / "net/gui/UltraClientFeatures.java").exists()
          and not (SRC / "net/gui/GuiFeaturesPacket.java").exists()
          and not (SRC / "mixin/KaiPathLmConsumerBootstrapMixin.java").exists()
          and not (q_res / "META-INF/kaipath-ultra.txt").exists()
          and "KaiPath" not in (q_res / "legacymechanics.mixins.json").read_text(encoding="utf-8")
          and 'modId = "kaipath"' not in (q_res / "META-INF/mods.toml").read_text(encoding="utf-8"))
    check("E: menus open only with the LM client (captured probe sends excepted); no KaiPath install text",
          "!UltraScreenSpec.capturing() && !preferUltra(player)" in q_ug
          and "return player != null && LmNativeNetwork.hasNativeClient(player);" in q_ug
          and "Install KaiPath" not in q_ug)
    check("E: body-parts model sync is native (message 5, hello-only feature)",
          "MSG_MODEL_SYNC = 5;" in q_np and 'FEATURE_BODY_PARTS_SYNC = "bodyPartsSync";' in q_np
          and "return player != null && LmNativeNetwork.takesModelSync(player);" in q_gn)
    check("E: HUD keeps bars and chat only for HUD off and the hello wait",
          "Who still gets boss bars and chat (LM 4.8.2)" in q_hud
          and "return player != null && LmNativeNetwork.takesHud(player) && enabled(player);" in q_hud)
    check("E: bridge skin rewrite gone", "nearestBridgeLetter" not in q_skins and "BRIDGE_PANEL" not in q_skins
          and "bridgePrimary" not in q_skins)
    check("E: self-tests pin rejection, the true default and message 5",
          "no LM client must be rejected with the install message (there is no bridge)" in q_selft
          and "requireLmClient must default to true (the LM client is mandatory)" in q_selft
          and "LmNativeProtocol.MSG_MODEL_SYNC == 5" in q_selft)
    check("E: rig turns a KaiPath-only client away by default",
          "REQUIRE= REJECT=1" in (q_probe / "matrix.sh").read_text(encoding="utf-8")
          and "run.sh kai" not in (q_probe / "matrix.sh").read_text(encoding="utf-8")
          and "a KaiPath-only client (no LM client) is turned away by default"
          in (q_probe / "report.py").read_text(encoding="utf-8"))
    check("probes: V482BannerWorld and V482BannerUi run in the scripted checks",
          "V482BannerWorld.run(player, this::note);" in (q_probe / "src/com/dbzlegacy/uiprobe/UiProbe.java")
          .read_text(encoding="utf-8")
          and "V482BannerUi.all(player, this::note);" in (q_probe / "src/com/dbzlegacy/uiprobe/UiProbe.java")
          .read_text(encoding="utf-8"))
    print("\n--- § lm 4.8.1 ---")
    # The 4.7.88 string check reads Java only. CNPC scripts split a message into color pieces
    # (COLOR + "f/progression boost"), so a literal may start with the color code letter before the slash.
    _cnpc = ROOT / "customnpcs/scripts"
    check("manifest row for lm 4.8.1", "lm-481-cnpc-strings" in manifest)
    # 2026-11-26: the owner does not use the CNPC scripts; the three stubs fixed in 4.8.1 were deleted (LM runs
    # TP boost, boost end and Android conversion itself).
    check("CNPC TP boost / boost end / android stubs deleted (LM runs them)",
          not any((_cnpc / f).exists() for f in ("Global TP Boost.js", "TP boost end.js", "AndrioidConversion.js")))
    _js_old = re.compile(r'(?:^[0-9a-fk-or]?|§.|\\u00a7.|[^\w/.:\-])/(difficulty|diff|rival|spar|progression|prog|'
                         r'prestige|padmin|character|skillcheck|skills|enddragon|androidify|terminal)(?![\w\-])')
    _js_hits = []
    for _f in sorted(_cnpc.glob("*.js")):
        _src = re.sub(r"/\*.*?\*/", lambda m: "\n" * m.group(0).count("\n"),
                      _f.read_text(encoding="utf-8", errors="replace"), flags=re.S)
        for _n, _line in enumerate(_src.splitlines(), 1):
            _code = re.sub(r"^\s*//.*", "", _line)
            for _lit in re.findall(r'"((?:[^"\\\n]|\\.)*)"|\'((?:[^\'\\\n]|\\.)*)\'', _code):
                if _js_old.search(_lit[0] or _lit[1]):
                    _js_hits.append(_f.name + ":" + str(_n))
    check("no CNPC script string prints a deleted command (comments not scanned)",
          not _js_hits, ", ".join(_js_hits[:8]))
    print("\n--- § lm 4.7.88 ---")
    p88_acc = read(SRC / "command/CommandAccess.java")
    p88_staff = read(SRC / "util/StaffAccess.java")
    p88_gate = read(SRC / "command/AdminGate.java")
    p88_mech = read(SRC / "command/MechanicsCommands.java")
    p88_bridge = read(SRC / "command/MohistCommandBridge.java")
    p88_sug = read(SRC / "command/LmCommandSuggestions.java")
    p88_help = read(SRC / "command/LmCommandHelp.java")
    p88_diff = read(SRC / "command/DifficultyCommands.java")
    p88_prog = read(SRC / "command/ProgressionCommands.java")
    p88_rules = read(SRC / "dojo/DojoWorldRules.java")
    p88_svc = read(SRC / "dojo/DojoService.java")
    p88_dscr = read(SRC / "gui/ultra/UltraDojoScreens.java")
    p88_insp = read(SRC / "gui/ForgeInspectGui.java")
    p88_mod = read(SRC / "AdaptiveDifficultyMod.java")
    p88_probe = (ROOT / "tools/lm-ui-probe/src/com/dbzlegacy/uiprobe/UiProbe.java").read_text(encoding="utf-8")
    check("manifest row for lm 4.7.88", "lm-4788-commands-dojo" in manifest)
    check("A: every system registers under /lm (player branch) or /lm admin (staff branch)",
          all('CommandAccess.underLm' in read(SRC / ("command/" + f)) for f in (
              "DifficultyCommands.java", "RivalCommands.java", "SparCommands.java", "CharacterCommands.java",
              "ProgressionCommands.java", "TerminalCommands.java"))
          and "public static LiteralArgumentBuilder<CommandSourceStack> underLmAdmin(" in p88_acc)
    _roots = re.findall(r'register\(\s*(?:Commands\.m_82127_|CommandAccess\.playerRoot|CommandAccess\.staffRoot)\("([a-z]+)"',
                        "".join(read(f) for f in (SRC / "command").glob("*.java")))
    check("A: no old top-level roots are registered; the bridge forwards /lm only",
          _roots == [] and p88_bridge.count("registerOne(server, commandMap") == 1
          and '"lm", List.of(), null' in p88_bridge and "isStaffOnlyBukkitCommand" not in p88_acc
          and "isSkillCheckBukkitCommand" not in p88_acc)
    check("A: /progression admin is /lm admin progression flags (no admin admin)",
          'Commands.m_82127_("flags")' in p88_prog and 'Commands.m_82127_("admin")' not in p88_prog)
    check("B: dead leaves deleted (migrate-cnpc, LM vanilla difficulty setters, gamedifficulty)",
          "migrate-cnpc" not in p88_mech and "vanillaDifficultyLiteral" not in p88_diff
          and '"gamedifficulty"' not in p88_diff and "VANILLA_DIFFICULTY" not in p88_sug
          and "forceMigrateWorld" not in read(SRC / "data/CnpcDataMigrator.java"))
    check("C: /lm open dojo (picker entry) and goals, /lm hud on|off, dojo inspect; clear scopes end with progression (4.8.3 adds dojo before them)",
          "UltraDojoScreens.openDojoEntry(player);" in p88_mech and "UltraHubScreens.PAGE_GOALS" in p88_mech
          and '"dojo", "goals"' in p88_sug and 'm_82127_("hud")' in p88_mech
          and "LmHud.set(player, want);" in p88_mech and 'case "dojo" -> {' in p88_insp
          and '"all", "rival", "spar", "difficulty", "progression"\n    );' in p88_sug)
    check("D: admin nodes need staff in creative (console unchanged); tab-complete refreshes on a gamemode change",
          "public static boolean isAdminSource(CommandSourceStack src)" in p88_staff
          and "return ASSUME_CREATIVE.get() || player.m_7500_();" in p88_staff
          and 'return Commands.m_82127_(name).requires(StaffAccess::isAdminSource);' in p88_acc
          and '"Staff admin commands need creative mode."' in p88_gate
          and '" Switch to creative and try again."' in p88_gate
          and "PlayerChangeGameModeEvent" in p88_gate and "m_82095_(player)" in p88_gate
          and "AdminGate.register();" in p88_mod)
    check("D: revive, skillcheck and terminal keep their own rules",
          "StaffAccess.isAdminSource" not in read(SRC / "command/LmRevive.java")
          and "requires(StaffAccess::hasSkillCheckSource)" in p88_prog
          and "requires(TerminalCommands::mayOpen)" in read(SRC / "command/TerminalCommands.java"))
    check("E: /lm help is a plain guide grouped by what you want to do, no admin commands",
          '"Open a menu"' in p88_help and '"Your stuff"' in p88_help and '"Get help"' in p88_help
          and "public static String dojoPlayer()" in p88_help
          and "admin" not in p88_help.split("public static String lmPlayer()", 1)[1].split("// ------------------------------------------------------------------ /lm admin help", 1)[0])
    _old = re.compile(r'(?:\u00a7.|\\u00a7.|^|[^\w/.:\-])/(difficulty|diff|rival|spar|progression|prog|prestige|padmin|'
                      r'character|skillcheck|skills|enddragon|androidify|terminal)(?![\w\-])')
    _hits = []
    for _f in sorted(SRC.rglob("*.java")):
        for _n, _line in enumerate(_f.read_text(encoding="utf-8").splitlines(), 1):
            _t = _line.strip()
            if _t.startswith("//") or _t.startswith("*") or _t.startswith("/*"):
                continue
            for _lit in re.findall(r'"((?:[^"\\]|\\.)*)"', _line):
                if _old.search(_lit) and "/difficulty <level>" not in _lit:
                    _hits.append(_f.name + ":" + str(_n))
    check("E: no string in the mod prints a deleted command (vanilla /difficulty <level> named on purpose)",
          not _hits, ", ".join(_hits[:8]))
    check("F: dojo world rules are Forge events (no new mixin): only the plot's master builds; ki only in a bout",
          'ONLY_MASTER = "Only the master of this dojo can build here."' in p88_rules
          and "public static boolean kiBlocked(" in p88_rules and "SparCombat.classifyKiType" in p88_rules
          and "@Mixin" not in p88_rules and "DojoWorldRules.INSTANCE" in p88_mod)
    check("G: Your dojos picker for 2+ dojos; management checks the viewed dojo",
          "public static List<Affiliation> affiliations(UUID player)" in p88_svc
          and "public static void openDojoEntry(ServerPlayer player)" in p88_dscr
          and "viewedDojo(" in p88_dscr and '"dojo_mine"' in p88_dscr)
    check("probe: command tree, gate, help and hud; dojo rules; dojo picker with a real dual player",
          "private void v88Commands()" in p88_probe and "private void v88DojoRules()" in p88_probe
          and "private void v88DojoPicker()" in p88_probe)

    print("\n--- § lm 4.7.87 ---")
    p87_hud = read(SRC / "gui/ultra/LmHud.java")
    p87_over = read(SRC / "client/HudOverlay.java")
    p87_war = read(SRC / "dojo/DojoWar.java")
    p87_spar = read(SRC / "sparring/SparringSystem.java")
    p87_board = read(SRC / "dojo/BoardSpar.java")
    p87_probe = (ROOT / "tools/lm-ui-probe/src/com/dbzlegacy/uiprobe/UiProbe.java").read_text(encoding="utf-8")
    check("manifest row for lm 4.7.87", "lm-4787-hud-spar-war" in manifest)
    check("WAR element: registered server side and drawn in the HUD's top row",
          'public static final String WAR = "war";' in p87_hud
          and "private static final Slot WAR = new Slot(LmHud.WAR);" in p87_over
          and "TOP = {UNDERDOG, FIGHT, WAR," in p87_over)
    check("one helper: a HUD player gets the toast, everyone else the same chat line; login notices wait for the hello",
          "public static boolean toastOrChat(ServerPlayer player, String chat, String element" in p87_hud
          and "public static List<String> chatLines(String chat)" in p87_hud
          and "public static void whenClientKnown(ServerPlayer player, Runnable notice)" in p87_hud)
    check("spar lines on the HUD: start, end summary, streak, board post/accept, winnings, dojo nudge, bonds",
          has(p87_spar, "public static void startNotice(", "public static void streakNotice(",
              "public static void inviteNotice(", "static void bondNotice(")
          and has(p87_board, "public static void postNotice(", "public static void winningsNotice(",
                  "public static void nudgeNotice("))
    check("war lifecycle on the WAR element: requests, sign-up pill (reusing the DJ-6 state), announcements, results",
          has(p87_war, "public static void warNotice(", "static void request(", "public static void signupNotice(",
              "public static void signupPill(", "signupLeftMs(")
          and "SIGNUP_MS = 60_000L" in p87_war)
    check("probe: each converted line shows once on the HUD for a HUD player and once in chat otherwise",
          "private void v87HudMatrix()" in p87_probe and "static final String[][] HUD87_CASES" in p87_probe)

    print("\n--- § lm 4.7.86 ---")
    p86_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    p86_dash = read(SRC / "gui/ultra/HubDashboard.java")
    p86_dojo = read(SRC / "gui/ultra/UltraDojoScreens.java")
    p86_char = read(SRC / "gui/ultra/UltraCharacterScreens.java")
    p86_probe = (ROOT / "tools/lm-ui-probe/src/com/dbzlegacy/uiprobe/UiProbe.java").read_text(encoding="utf-8")
    p86_corpus = ROOT / "tools/lm-gui-corpus"
    check("manifest row for lm 4.7.86", "lm-4786-height-budget" in manifest)
    check("X5: the 300 px height budget is enforced at boot (strict), staff/config pages allowed",
          "HEIGHT_BUDGET_PX = 300;" in p86_test and "HEIGHT_BUDGET_STRICT = true;" in p86_test
          and 'throw new IllegalStateException("LegacyMechanics Ultra height budget self-check failed: "' in p86_test)
    check("X5: the probe fails a release when any player page goes over budget after the crawl",
          "UltraScreenSelfTest.overBudgetPages()" in p86_probe)
    check("height: one-line board posts, compact Dojo Home and upgrade pages, part colors in 4 columns, one-line Home prestige",
          "always one body line" in p86_dojo and "PICTURE_ROWS = 5" in p86_dojo
          and '"Level " + DmzRewards.formatWhole(p.level()) + " of "' in p86_dash
          and "swatches save at once" in p86_char)
    check("proof: the GuiKit crawl findings for the real 4.7.86 menus are committed",
          (p86_corpus / "findings/real-crawl-findings.md").exists()
          and "4.7.86" in (p86_corpus / "findings/real-crawl-findings.md").read_text(encoding="utf-8"))

    print("\n--- § lm 4.7.85 ---")
    p85_spec = read(SRC / "gui/ultra/UltraScreenSpec.java")
    p85_style = read(SRC / "gui/ultra/UltraStyle.java")
    p85_hub = read(SRC / "gui/ultra/UltraHubScreens.java")
    p85_reg = read(SRC / "gui/ultra/PageRegistry.java")
    p85_rank = read(SRC / "sparring/DojoRankings.java")
    p85_spar = read(SRC / "sparring/SparringSystem.java")
    check("manifest row for lm 4.7.85", "lm-4785-audit-fixes" in manifest)
    check("N6: offerGetMore is wired into every confirm page that can be short (coins, favor, Dojo Points)",
          "public UltraScreenSpec offerGetMore(UltraStyle.Shortfall kind, String goAction, boolean withLine)" in p85_spec
          and all(read(SRC / ("gui/ultra/" + f)).count("offerGetMore(UltraStyle.Shortfall.") >= n for f, n in (
              ("UltraDifficultyScreens.java", 1), ("UltraCharacterScreens.java", 5), ("UltraWeightShopScreens.java", 1),
              ("UltraPrestigeScreens.java", 1), ("UltraDojoScreens.java", 5), ("DojoFounding.java", 2)))
          and 'case FAVOR -> "Earn favor: Trade";' in p85_style and 'case COINS -> "go:hub|coins";' in p85_style
          and "GET_FAVOR_LINE" not in read(SRC / "gui/ultra/UltraPrestigeScreens.java"))
    check("N6: the coins button lands on a real page (Where to get coins), registered under More",
          "private static void openCoins(ServerPlayer player)" in p85_hub and "if (PAGE_COINS.equals(p)) {" in p85_hub
          and 'p(HUB, "coins", "Where to get coins", "hub|more"' in p85_reg)
    check("dead old-war write path and its 2x-RP award are gone; dojo_war_cancel -> dojoRevokeWar kept",
          "CHALLENGE_RP_MULT" not in p85_rank and "public static String challengeDojo(" not in p85_rank
          and "public static String acceptChallenge(" not in p85_rank and 'UltraStyle.DIM + "(2x RP)"' not in p85_rank
          and "dojoAcceptWar" not in p85_spar and "dojoDeclineWar" not in p85_spar
          and "public static String dojoRevokeWar(" in p85_spar
          and "public static String revokeOutgoingChallenge(" in p85_rank)
    check("Back never reads a bare Back: a name too long for the button reads Back to previous page",
          'BACK_TO_PREVIOUS = "Back to previous page";' in p85_style and "return LABEL + BACK_TO_PREVIOUS;" in p85_style)

    print("\n--- § lm 4.7.84 ---")
    p84_reg = read(SRC / "gui/ultra/PageRegistry.java")
    p84_nav = read(SRC / "gui/ultra/NavHistory.java")
    p84_spec = read(SRC / "gui/ultra/UltraScreenSpec.java")
    p84_gui = read(SRC / "gui/ultra/UltraGui.java")
    p84_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    p84_hub = read(SRC / "gui/ultra/UltraHubScreens.java")
    p84_board = read(SRC / "dojo/BoardSpar.java")
    p84_rank = read(SRC / "sparring/DojoRankings.java")
    p84_war = read(SRC / "dojo/DojoWar.java")
    p84_cmd = read(SRC / "command/SparCommands.java")
    p84_sapi = read(SRC / "gui/SparGuiApi.java")
    p84_cat = read(SRC / "dojo/DojoCatalog.java")
    p84_svc = read(SRC / "dojo/DojoService.java")
    p84_found = read(SRC / "gui/ultra/DojoFounding.java")
    p84_spar = read(SRC / "gui/ultra/UltraSparScreens.java")
    p84_sys = read(SRC / "sparring/SparringSystem.java")
    p84_cfg = read(SRC / "gui/ultra/UltraConfigScreens.java")
    check("manifest row for lm 4.7.84", "lm-4784-navigation" in manifest)
    check("N1: a system Home exits to the main menu (nav:hub), never Back to an earlier page",
          'action = "nav:hub";' in _after(p84_spec, "private void placeBack(ServerPlayer player) {")
          and "boolean firstTab = !tabs.isEmpty() && tabs.get(0).id().equals(activeTab);" in p84_spec)
    check("N2/N3: a fresh open starts a new trail; tabs replace each other in the trail",
          "NavHistory.fresh(player.m_20148_());" in p84_gui and "if (!inMenuClick()) {" in p84_gui
          and "NavHistory.markTab(screenId, pageId);" in p84_spec and "String tab = tabInStack(d, k);" in p84_nav)
    check("N4: short Back titles exist and every one fits 'Back to ...'",
          "private static void shortTitle(String screen, String pattern, String shortTitle)" in p84_reg
          and 'shortTitle("spar", "board_wager_coins", "Coin wager");' in p84_reg
          and "short title does not fit 'Back to'" in p84_reg)
    check("X5: the 300 px height budget is measured (overBudgetPages)",
          "overBudgetPages()" in p84_test and "observeHeight(packet)" in p84_spec)
    check("H2: search finds what players type; 'menu' and 'main' do not match every page",
          '{"hair", "character|reskin"}' in p84_reg and 'for (String word : new String[] {"main", "menu"})' in p84_reg)
    check("main menu Home is two cards: Your next prestige and Your systems",
          'UltraStyle.header("Your next prestige")' in p84_hub and 'UltraStyle.header("Your systems")' in p84_hub
          and 'UltraStyle.header("Compete")' not in p84_hub and "HubDashboard.streakLine(player)" not in
          _after(p84_hub, "private static void openMain(").split("\n    }\n", 1)[0])
    check("P2-1: a forfeit pays the winner both stakes; only a disconnect refunds",
          "public static UUID wagerPayee(UUID winner, String how) {" in p84_board
          and '!"DISCONNECT".equals(how) ? winner : null' in p84_board)
    check("P2-2: ki sense reads running menu wars", "runningWarEnemy(" in p84_rank)
    check("pick a: the old /spar dojo challenge declares through DojoWar",
          "public static String legacyChallenge(" in p84_war and "legacyChallenge(" in p84_cmd
          and "legacyChallenge(" in p84_sapi)
    check("pick c/d: founding grant 15 points once; a dojo can start with a default banner",
          "FOUNDING_GRANT = 15;" in p84_cat and "foundingGrantPaid" in p84_svc
          and 'DEFAULT_BANNER = "default";' in p84_found)
    check("pick b: a disciple's dojo title", '"Disciple of "' in p84_svc)
    check("DJ-7: Request master lists masters only; Leave mentor and Release dim when they can't act; real cap",
          "static List<String> onlineMasterNames(ServerPlayer self)" in p84_spar
          and 'spec.lockedButton("Leave mentor - none");' in p84_spar
          and 'spec.lockedButtonRight("Release - none");' in p84_spar
          and "SparringSystem.apprenticeCap(player.m_20148_().toString())" in p84_sapi
          and "SparringSystem.MAX_APPRENTICES" not in p84_sapi)
    check("SparringSystem copy: cooldown time left; capacity on Dojo > Upgrades",
          "You can invite again in " in p84_sys and "You can ask a new one in " in p84_sys
          and "Buy capacity in the Dojo tab" not in p84_sys
          and "Buy Disciple capacity on Dojo > Upgrades for more room." in p84_sys)
    check("P-A1: on/off config settings skip the Accepts row and the default chip; the module tab reads Progression",
          'String accepts = toggleRow ? "" : ConfigEditor.acceptedValues(module, path);' in p84_cfg
          and "boolean noChip = onOff && !dirty" in p84_cfg and '{"progression", "Progression"}' in p84_cfg)

    print("\n--- § lm 4.7.83 ---")
    p83_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    check("manifest row for lm 4.7.83", "lm-4783-selftest-label" in manifest)
    check("boot self-test: label clipping sample overflows by real width; a fitting label is kept",
          'String wide = "Android Removal Service";' in p83_test
          and "fitAsciiLabel must keep a label that fits by real width" in p83_test)

    print("\n--- § lm 4.7.82 ---")
    p82_hub = read(SRC / "gui/ultra/UltraHubScreens.java")
    p82_spec = read(SRC / "gui/ultra/UltraScreenSpec.java")
    p82_menu = read(SRC / "client/NativeMenuScreen.java")
    check("manifest row for lm 4.7.82", "lm-4782-color-wheel" in manifest)
    check("My colors: five name | hex rows and ONE wheel shared by the five fields",
          "spec.sharedColorWheel(SHARED_WHEEL, fields," in p82_hub
          and "spec.colorWheel(toneWheel(i)" not in p82_hub
          and "public UltraScreenSpec sharedColorWheel(String inputId, String[] targetInputIds, int x, int y, int size)" in p82_spec)
    check("LM client: a shared wheel colors the field tapped last and outlines it",
          "private String wheelTarget(Wheel w)" in p82_menu and "lastBox = f.id();" in p82_menu
          and "String target = wheelTarget(w);" in p82_menu and "// A shared wheel outlines the field it colors." in p82_menu)

    print("\n--- § lm 4.7.81 ---")
    p81_spec = read(SRC / "gui/ultra/UltraScreenSpec.java")
    p81_style = read(SRC / "gui/ultra/UltraStyle.java")
    p81_menu = read(SRC / "client/NativeMenuScreen.java")
    p81_skins = read(SRC / "gui/ultra/MenuSkins.java")
    p81_diff = read(SRC / "gui/ultra/UltraDifficultyScreens.java")
    p81_rival = read(SRC / "gui/ultra/UltraRivalScreens.java")
    p81_dojo = read(SRC / "gui/ultra/UltraDojoScreens.java")
    p81_found = read(SRC / "gui/ultra/DojoFounding.java")
    p81_pres = read(SRC / "gui/ultra/UltraPrestigeScreens.java")
    p81_reg = read(SRC / "gui/ultra/PageRegistry.java")
    p81_csapi = read(SRC / "gui/CharacterServicesGuiApi.java")
    p81_corpus = ROOT / "tools/lm-gui-corpus"
    check("manifest row for lm 4.7.81", "lm-4781-guikit-layout" in manifest)
    check("GuiKit corpus in the repo (specs, run script, findings)",
          (p81_corpus / "run.ps1").exists() and (p81_corpus / "tools/guikit.json").exists()
          and (p81_corpus / "findings/layout-findings.md").exists())
    check("every page: notices stay in their card, body wraps at 7px, labels cut by real width, footer as wide as the columns",
          "&& !notice(l);" in p81_menu and "BODY_GLYPH_PX = 7;" in p81_style
          and "static String clipByWidth(String text, int maxPx, boolean trim)" in p81_style
          and "menuFooterButton(b, x, footerY, fw, bh)" in p81_spec
          and "fillScreen && designWidth > 0 ? 8 : UltraStyle.COL_L" in p81_spec
          and "dark ? 0.52 : 0.30" in p81_skins)
    check("Difficulty Home: Back to main menu bottom right, paired rows, OFF toggles live",
          "placeDifficultyToggle(spec, personal, true);" in p81_diff and 'UltraStyle.plain("Difficulty - OFF")' in p81_diff
          and "Team mode resets to Personal" not in p81_diff)
    check("Rival: requests named on their buttons, Requests (N), Stop spectate only while spectating",
          '"requests|" + (incoming > 0 ? "Requests (" + incoming + ")" : "Requests")' in p81_rival
          and "RivalSpectator.isSpectating(player.m_20148_())" in p81_rival and "placeIncomingDeclareButtons" not in p81_rival)
    check("Dojo: short war action labels, wide watch and banner rows, coin wager labels, 24-char name field",
          has(p81_dojo, '"View the bracket"', '"Accept the war"', '"Turn it down"') and "buttonWide(" in p81_found
          and "DOJO_NAME_MAX" in p81_found and "coinLabel(" in p81_dojo)
    check("Prestige: options in normal style, two columns, irreversible exchange in danger style",
          "pagerRow(spec, \"mastery_lines\", pg, pages);" in p81_pres and "UltraStyle.STYLE_DANGER" in p81_pres
          and 'p("prestige", "mastery_lines", "Forms", "prestige|mastery_wallet");' in p81_reg)
    check("Character costs say Ancient Coins once", "Ancient Ancient" not in p81_csapi)
    check("race change menu offers keep 0/25/50/75/100 (server still accepts the configured steps)",
          "public static final int[] MENU_KEEP_STEPS = {0, 25, 50, 75, 100};" in read(SRC / "character/CharacterServicesConfig.java")
          and "int[] keepSteps = CharacterServicesConfig.MENU_KEEP_STEPS;" in read(SRC / "gui/ultra/UltraCharacterScreens.java"))

    print("\n--- § lm 4.7.80 ---")
    p80_hub = read(SRC / "gui/ultra/UltraHubScreens.java")
    p80_act = read(SRC / "gui/ultra/UltraGuiActions.java")
    p80_reg = read(SRC / "gui/ultra/PageRegistry.java")
    p80_goals = read(SRC / "goals/WeeklyGoals.java")
    p80_dojo = read(SRC / "gui/ultra/UltraDojoScreens.java")
    p80_board = read(SRC / "dojo/BoardSpar.java")
    p80_hud = read(SRC / "gui/ultra/LmHud.java")
    p80_chat = read(SRC / "util/LmChat.java")
    p80_note = read(SRC / "util/ScreenNotify.java")
    p80_pen = read(SRC / "progression/tp/DeathTpPenalty.java")
    p80_rcm = read(SRC / "rival/RivalChallengeManager.java")
    p80_tpb = read(SRC / "progression/tp/GlobalTpBoost.java")
    p80_mb = read(SRC / "progression/tp/GlobalMasteryBoost.java")
    p80_sense = read(SRC / "title/TitleSense.java")
    p80_probe = (ROOT / "tools/lm-ui-probe/src/com/dbzlegacy/uiprobe/UiProbe.java").read_text(encoding="utf-8")
    p80_more = _after(p80_hub, "private static void openMore(").split("\n    }\n", 1)[0]
    check("manifest row for lm 4.7.80", "lm-4780-hub-goals-wager" in manifest)
    check("1: hub tabs Home, Goals, More; no Search tab; the search page id opens More; search lives on More",
          '.tabs("home|Home", PAGE_GOALS + "|Goals", PAGE_MORE + "|More");' in p80_hub and '"|Search"' not in p80_hub
          and "openSearch" not in p80_hub and "if (PAGE_SEARCH.equals(p) || PAGE_MORE.equals(p)) {" in p80_hub
          and has(p80_more, "searchLines(spec, player)", "placeSearchButtons(spec, player, search);")
          and 'case "search", "more" -> UltraHubScreens.PAGE_MORE;' in p80_act
          and 'p(HUB, "goals", "Weekly goals", "hub|main"' in p80_reg and 'p(HUB, "search"' not in p80_reg)
    check("1: Goals is the weekly goals board: 3 goals from 3 systems, weekly clock, progress and Go to; no rewards",
          "private static void openGoals(ServerPlayer player)" in p80_hub and '"Go to " + g.goTitle()' in p80_hub
          and "DifficultyGuide.bar(" in _after(p80_hub, "private static void openGoals(")
          and has(p80_goals, "WEEK_MS = 7L * 86_400_000L", "!systems.contains(g.system())", "public static void note(UUID id")
          and "reward" not in p80_goals.lower().replace("no rewards", "").replace("no rewards or payouts", "")
          and all(("WeeklyGoals.note(" in read(SRC / f)) for f in ("dojo/BoardSpar.java", "sparring/SparringSystem.java",
                  "dojo/DojoWar.java", "rival/RivalChallengeManager.java", "title/TitleSystem.java",
                  "service/DifficultyTrial.java", "progression/skills/LivingWorldNearbyMeditation.java")))
    check("2: no founding from the Spar Board; the no-dojo board opens the Dojo section and keeps Find a dojo",
          '"Open the Dojo"' in p80_dojo and '"go:dojo|home"' in p80_dojo and '"page:dojo_list", "Find a dojo"' in p80_dojo
          and 'UltraStyle.confirm("Found a dojo")' not in p80_dojo)
    check("3: Wager is a sub-menu (kind, then amount within the caps); accept says the stake; accepter matches",
          has(p80_dojo, "static void openWager(", "static void openWagerAmounts(", "static void openAcceptAsk(",
              '"page:" + PAGE_WAGER, "Wager - "', "BoardSpar.ACT_WAGER.equals(action)")
          and '"do:" + BoardSpar.ACT_WAGER, "Wager - "' not in p80_dojo
          and has(p80_board, 'ACT_WAGER_SET = "board_wager_set"', "case ACT_WAGER_SET ->")
          and "(d.wager() + 1) % wagerSteps()" not in p80_board
          and has(p80_board, "String afford = canStake(viewer, post.wagerKind(), post.wager());", "if (!escrow(post, poster, acceptor)) {")
          and 'p("spar", "board_wager", "Wager", "spar|board_post"' in p80_reg)  # 4.7.84: under Post a spar
    check("4: no doubling: vanilla notices report to the capture; HUD players get no penalty chat, fight start, boost status",
          has(p80_hud, "public record Vanilla(", "public static Both captureBoth(Runnable r)")
          and 'LmHud.vanilla(player, "chat", line);' in p80_chat and 'LmHud.vanilla(player, "subtitle", subtitle);' in p80_note
          and "tellUnlessHud(player, LmChat.note(\"TP\", body));" in p80_pen
          and "public static void announceStart(MinecraftServer server, RivalChallenge ch)" in p80_rcm
          and "announceStart(server, ch);" in p80_rcm
          and "private static void broadcastStatus(String msg)" in p80_tpb and "private static void broadcastStatus(String msg)" in p80_mb
          and "public static void deliver(ServerPlayer player, DifficultyTitle equipped" in p80_sense)
    p80_pmp = read(SRC / "gui/ultra/PlayerModelParts.java")
    p80_mp = read(SRC / "gui/ultra/ModelPreview.java")
    p80_cat = read(SRC / "gui/ultra/BodyPartCatalog.java")
    p80_bpp = read(SRC / "client/BodyPartPreview.java")
    check("5: form previews send the form's pieces (base form = base/base) plus the owner's shop pieces; LM client builds a Look",
          "static JsonArray bodyPartsFor(" in p80_pmp
          and "return bodyPartsFor(player, race, group, form, stackGroup, stackForm, candidatePart);" in p80_pmp
          and 'JsonArray body = PlayerModelParts.bodyPartsFor(owner, form.race(), form.group(), form.form(), "", "", null);' in p80_mp
          and has(p80_cat, 'public static final String BASE_FORM = "base";', "String[] worn = wornForm(group, form);")
          and "return newLook(extra, colors);" in p80_bpp and "if (look == null || (!hasPieces && !hasColors))" not in p80_bpp)
    check("4: the probe checks every HUD element on and off through the code that sends it",
          "private void v80HudMatrix()" in p80_probe and all(f"LmHud.{e}" in _after(p80_probe, "static final String[] HUD_ELEMENTS")
              .split(";", 1)[0] for e in ("UNDERDOG", "INSTINCT", "TP", "PENALTY", "MOMENTUM", "FIGHT", "SPAR", "POTENTIAL",
                                          "COINS", "ELITE", "BOOST")))

    print("\n--- § lm 4.7.79 ---")
    p79_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    check("manifest row for lm 4.7.79", "lm-4779-dojo-selftest" in manifest)
    check("boot menu-fit self-test has a Dojo sample with the new tabs (and Sparring's new tabs)",
          has(p79_test, 'case "dojo" -> UltraScreenSpec.of("dojo")', '.tabs("home|Home", "dojo_upgrades|Upgrades", "dojo_wars|Wars")',
              '.tabs("home|Home", "leaderboard|Leaderboard", "sparboard|Spar Board")'))

    print("\n--- § lm 4.7.78 ---")
    p78_coin = read(SRC / "currency/AncientCoinEconomy.java")
    p78_skins = read(SRC / "gui/ultra/MenuSkins.java")
    p78_over = read(SRC / "client/HudOverlay.java")
    p78_hub = read(SRC / "gui/ultra/UltraHubScreens.java")
    p78_diff = read(SRC / "gui/ultra/UltraDifficultyScreens.java")
    p78_calc = read(SRC / "calc/DifficultyCalculator.java")
    p78_data = read(SRC / "data/PlayerDifficultyData.java")
    p78_mob = read(SRC / "scaling/MobScaling.java")
    p78_spar = read(SRC / "gui/ultra/UltraSparScreens.java")
    p78_dojo = read(SRC / "gui/ultra/UltraDojoScreens.java")
    p78_found = read(SRC / "gui/ultra/DojoFounding.java")
    p78_svc = read(SRC / "dojo/DojoService.java")
    p78_store = read(SRC / "dojo/DojoStore.java")
    p78_cat = read(SRC / "dojo/DojoCatalog.java")
    p78_reg = read(SRC / "gui/ultra/PageRegistry.java")
    check("manifest row for lm 4.7.78", "lm-4778-difficulty-dojo" in manifest)
    check("A: coin toast accent is the skin's gold (no hex literal)",
          "LmHud.ACCENT_CURRENCY" in p78_coin and '"#f0b429"' not in p78_coin
          and "public static int currencyGold(Skin skin)" in p78_skins and "MenuSkins.currencyGold(skin)" in p78_over)
    check("B: search row on More, not Home",
          "placeSearchButtons(spec, player, search);" in _after(p78_hub, "private static void openMore(").split("\n    }\n", 1)[0]
          and "placeSearchRow(spec" not in _after(p78_hub, "private static void openMain(").split("\n    }\n", 1)[0])
    check("C: Home has no Change tier / Clear tier; Tiers locked when off, clears the tier; Step 3 only Got it; replay label",
          'UltraStyle.confirm("Change tier")' not in p78_diff and "No tier yet. Pick one in Tiers." in p78_diff
          and "Turn difficulty on to pick a tier." in p78_diff  # 4.7.84 (D-2)
          and ordered(p78_diff, "if (!guide && active > 0) {", '"page:clear_tier_ask"')
          and ordered(p78_diff, '"Got it - take me to Difficulty"', "spec.footerGuide(", "return;", "placeDifficultyToggle(spec, personal, true);")
          and 'UltraStyle.plain("How difficulty works")' in p78_diff)
    check("C: rival teams removed; every player scales personally; stored team mode dropped",
          not (SRC / "team/TeamScaling.java").exists() and not (SRC / "data/TeamMode.java").exists()
          and not (SRC / "gui/DifficultyTeamGuiApi.java").exists() and "openTeam" not in p78_diff
          and "TeamScaling" not in p78_calc and 'tag.m_128473_("teamMode");' in p78_data
          and "double bossHpThreshold = cfg.bossHealthThreshold;" in p78_mob)
    check("D: hub Sparring and Dojo tiles; Sparring Home/Leaderboard/Spar Board; Dojo its own system",
          'new HubTile("spar", "Sparring"' in p78_hub and 'new HubTile("dojo", "Dojo"' in p78_hub
          and '.tabs("home|Home", "leaderboard|Leaderboard", "sparboard|Spar Board")' in p78_spar
          # 4.7.84 (SP-1): Sparring Home leads to the board ("Open the Spar Board"), reversing 4.7.78.
          and "private static void openLeaderboard(" in p78_spar and '"Open the Spar Board"' in p78_spar
          and 'TAB_DOJO_HOME + "|Home", TAB_UPGRADES + "|Upgrades", TAB_WARS + "|Wars"' in p78_dojo
          and "public static void openSystem(ServerPlayer player, String page)" in p78_dojo
          and 'p("dojo", "home", "Dojo", "hub|main", "my dojo");' in p78_reg and 'p("spar", "dojo"' not in p78_reg)
    check("D: founding asks name, style and banner; banner copied, never consumed; shown on Dojo Home",
          has(p78_found, "PAGE_NAME = \"found\"", "ACT_STYLE", "ACT_BANNER", "ACT_CONFIRM",
              "Craft any banner (6 wool and a stick; add patterns at a loom if you like),")
          and "shrink" not in p78_found and "m_41774_" not in p78_found
          and "public static String found(ServerPlayer p, String name, String styleId, String bannerBase, List<String> patterns)" in p78_svc
          # 4.7.84 (DJ-1): Dojo Home folds the banner into its stats card ("Banner" + DojoFounding.bannerText).
          and "public String bannerBase" in p78_store
          and 'UltraStyle.labelValue("Banner", DojoFounding.bannerText(d))' in p78_dojo)
    check("D amendment: style 40, name 20, banner 20 Dojo Points; one 12-hour cooldown; founding and first banner free",
          has(p78_cat, "STYLE_CHANGE_COST = 40;", "NAME_CHANGE_COST = 20;", "BANNER_CHANGE_COST = 20;",
              "IDENTITY_COOLDOWN_MS = 12L * 60L * 60L * 1000L;")
          and "public long identityChangedAt;" in p78_store
          and has(p78_svc, "d.points = Math.max(0, d.points - cost);", "identityChangeProblem(d, DojoCatalog.NAME_CHANGE_COST)",
                  "identityChangeProblem(d, DojoCatalog.STYLE_CHANGE_COST)", "boolean first = d.bannerBase == null"))

    print("\n--- § lm 4.7.77 ---")
    p77_over = read(SRC / "client/HudOverlay.java")
    p77_spec = read(SRC / "rival/RivalSpectator.java")
    p77_sense = read(SRC / "title/TitleSense.java")
    p77_ev = read(SRC / "event/DifficultyEvents.java")
    p77_pot = read(SRC / "progression/skills/PotentialProgression.java")
    p77_hud = read(SRC / "gui/ultra/LmHud.java")
    p77_rival = read(SRC / "gui/ultra/UltraRivalScreens.java")
    check("manifest row for lm 4.7.77", "lm-4777-bugfixes" in manifest)
    check("HUD panels re-bake after the cache releases them (no missing-texture checkerboard)",
          has(p77_over, "boolean[] gone", "old.gone()[0] = true;", "b == null || b.gone()[0]"))
    check("stop spectate and difficulty off hide their HUD elements",
          ordered(p77_spec, "public static String stop(", "ACTIVE.remove(spectator.m_20148_());", ".hide(spectator,")
          and ordered(p77_sense, "if (!SystemGate.participates(player)) {", ".hide(player,")
          and ordered(p77_ev, "SystemGate.isDisabled() || !SystemGate.allows(player)", "LmHud.ELITE);"))
    check("Potential notices stack, a reset clears the shown numbers, HUD maps forgotten on logout",
          has(p77_pot, "HUD_HINTS", "HUD_LAST.remove(player.m_20148_());", "public static void forgetHud(")
          and "PotentialProgression.forgetHud(" in p77_hud and "AncientCoinEconomy.forgetToast(" in p77_hud)
    p77_dojo = read(SRC / "gui/ultra/UltraDojoScreens.java")
    check("dojo leaderboard by clout and war wins, on the Wars tab; old boards open it",
          has(p77_dojo, 'lower.startsWith("dojo_list_wars")', "DojoService.byWarWins()",
              'spec.buttonRight("page:dojo_list_wars", "Dojo leaderboard"', 'lower.equals("dojo_top_wars")'))
    check("top pills keep a readable width; Rival settings show each state once",
          "TOP_MIN_W = 160" in p77_over and 'labelValue("TP Chat"' not in p77_rival)

    print("\n--- § lm 4.7.76 ---")
    p76_over = read(SRC / "client/HudOverlay.java")
    p76_coin = read(SRC / "currency/AncientCoinEconomy.java")
    check("manifest row for lm 4.7.76", "lm-4776-hud-fixes" in manifest)
    check("HUD priority: core pills first within the top share, cards before the newer bottom-row elements",
          has(p76_over, "TOP_SHARE = 0.42f", "TOP_EXTRA = {WAR, ELITE, BOOST}", "s != WAR && s != ELITE && s != BOOST",  # 4.7.87: War waits too
              "boolean meetsCards =", "s != MOMENTUM && meetsCards"))
    check("coin toast reads '+ 5x Iron Ancient Coins' (no doubled Ancient)",
          'UltraStyle.LABEL + "Coins")' in p76_coin and 'LABEL + "Ancient Coins")' not in p76_coin)

    print("\n--- § lm 4.7.75 ---")
    p75_hud = read(SRC / "gui/ultra/LmHud.java")
    p75_rc = read(SRC / "rival/RivalChallengeManager.java")
    p75_spec = read(SRC / "rival/RivalSpectator.java")
    p75_spar = read(SRC / "sparring/SparringSystem.java")
    p75_war = read(SRC / "dojo/DojoWar.java")
    p75_pot = read(SRC / "progression/skills/PotentialProgression.java")
    p75_coin = read(SRC / "currency/AncientCoinEconomy.java")
    p75_sense = read(SRC / "title/TitleSense.java")
    p75_mom = read(SRC / "progression/Momentum.java")
    p75_heal = read(SRC / "kube/LmHotPath.java")
    p75_char = read(SRC / "gui/ultra/UltraCharacterScreens.java")
    p75_over = read(SRC / "client/HudOverlay.java")
    check("manifest row for lm 4.7.75", "lm-4775-hud-chat" in manifest)
    check("HUD part 2 elements registered on both sides",
          all(f'"{e}"' in p75_hud for e in ("fight", "spar", "potential", "coins", "elite", "boost"))
          # 4.7.87 added the war / dojo pill under the fight pill; the 4.7.75 order around it stays.
          and has(p75_over, "TOP = {UNDERDOG, FIGHT, WAR, PENALTY, ELITE, BOOST, TP}", "BOTTOM = {MOMENTUM, SPAR, POTENTIAL}"))
    check("fight pill replaces the challenge score / range chat and the spectator line for HUD players only",
          has(p75_rc, "chatUnlessHud(a, scoreLine(ch));", "chatUnlessHud(a, LmChat.fail(\"Challenge\", TOO_FAR));",
              "public static java.util.List<String> fightLines(", "return LmChat.tagged(\"Challenge\", scoreBody(ch));")
          and "RivalChallengeManager.fightLines(ch, now, false)" in p75_spec and '" (starting soon)"' in p75_spec)
    check("spar sessions and war bouts get the fight pill; spar chip replaces Perfect / combo / recover chat",
          has(p75_spar, "sparHud(player, partner, rt, now);", "public static java.util.List<String> sparChipLines(",
              ".active(player) || now < rt.messageNext")
          and "static void boutHud(" in p75_war and "boutHud(server, r, now);" in p75_war)
    check("Potential meter instead of the per-hit subtitle; coin toast sums 5 s; elite chip; boost chip",
          "potentialHud(player, progress, required, null);" in p75_pot and "static boolean coinToast(" in p75_coin
          and "COIN_WINDOW_MS = 5_000L" in p75_hud and "Elite nearby" in p75_sense
          and "public static void boostHudPulse(" in p75_mom)
    check("healing caps on Character Services Home, not at every login",
          "HealCap.announce(player);" not in p75_heal and "HealCap.summary(player)" in p75_char)

    print("\n--- § lm 4.7.74 ---")
    p74_tc = read(SRC / "client/TextCache.java")
    p74_menu = read(SRC / "client/NativeMenuScreen.java")
    p74_style = read(SRC / "gui/ultra/UltraStyle.java")
    p74_spec = read(SRC / "gui/ultra/UltraScreenSpec.java")
    ultra_src = "".join(read(f) for f in sorted((SRC / "gui/ultra").glob("Ultra*Screens.java")))
    check("manifest row for lm 4.7.74", "lm-4774-speed-cleanup" in manifest)
    check("menu text laid out once per screen (no parse/contrast/measure per frame)",
          "final class TextCache" in p74_tc and "private final TextCache textCache = new TextCache();" in p74_menu
          and p74_menu.count("textCache.fit(") >= 6 and "textCache.wrap(" in p74_menu
          and "NativeText.parse(raw, base" not in p74_menu and "NativeText.parse(labelInk(b)" not in p74_menu
          and has(p74_menu, "void lookChanged() {", "textCache.clear();"))
    check("menu styles use the STYLE_* constants (no raw style strings in the screens)",
          not re.search(r',\s*"(confirm|dim|normal|danger)"\)', ultra_src))
    check("one toggle label format", "public static String toggle(String label, boolean on)" in p74_style
          and 'UltraStyle.toggle("HUD", hudOn)' in ultra_src)
    check("dead menu helpers removed", "public UltraScreenSpec clearPlayerModel()" not in p74_spec
          and "public static String hubTilePaletteName(" not in p74_style)

    print("\n--- § lm 4.7.73 ---")
    p73_reg = read(SRC / "gui/ultra/PageRegistry.java")
    p73_dojo = read(SRC / "gui/ultra/UltraDojoScreens.java")
    p73_hud = read(SRC / "client/HudOverlay.java")
    p73_pick = read(SRC / "gui/ultra/UltraPickHelper.java")
    p73_bg = read(SRC / "client/MenuBackground.java")
    p73_audit = read(ROOT / "tools/dmz-adaptive-difficulty/sim/audit_page_registry.py")
    check("manifest row for lm 4.7.73", "lm-4773-fixes" in manifest)
    check("Difficulty pages and parents are the state-routed ids; guide steps never a parent outside the guide",
          has(p73_reg, 'p("difficulty", "main", "Difficulty", "hub|main"', 'p("difficulty", "tiers", "Tiers", "difficulty|main"',
              'item("difficulty", "guide_tiers", "Tiers", "difficulty|guide");', 'parent replays the guide')
          and '"difficulty|guide_your_tier");' in p73_reg and 'p("difficulty", "guide_tiers"' not in p73_reg)
    check("registry audit sees sub()/base() pages, page-button pairs and the pager's later pages",
          has(p73_audit, "placePageButtons", "placePrevNext", '":2"'))
    check("dojo style change and bonus purchase ask first, naming the loss and the cost",
          has(p73_dojo, '"page:dojo_style_ask:" + s.name()', '"page:dojo_bonus_ask:" + b.id()',
              "static void openStyleAsk(", "static void openBonusAsk(", "Its \" + DojoCatalog.BONUS_COST",
              "can't change for"))
    check("demote is danger, toggles read Label - ON/OFF, an active Prev is a normal button",
          has(p73_dojo, 'UltraStyle.danger("Step down"), UltraStyle.STYLE_DANGER', 'UltraStyle.toggle("War rights"')
          and 'dim("Prev")' not in p73_pick)
    check("HUD: overlay under chat, starts below boss bars, clears effect icons, Momentum above the action bar",
          has(p73_hud, "registerBelow(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CHAT_PANEL.id()",
              "BossEventProgress", "effectCols * EFFECT_COL", "Math.max(rightHeight, ACTION_BAR_UP)",
              "TOP = {UNDERDOG")
          and ordered(p73_hud, "TOP = {UNDERDOG", "PENALTY", "TP};")
          and "RenderGuiEvent.Post" not in p73_hud)
    check("HUD bake handle kept per element; failed bakes close their image and are not retried every frame",
          has(p73_hud, "s.bakedSkin != skin", "FAILED.contains(key)", "img.close();")
          and has(p73_bg, "FAILED.contains(key)", "img.close();"))

    print("\n--- § lm 4.7.72 ---")
    p72_hud = read(SRC / "gui/ultra/LmHud.java")
    p72_pkt = read(SRC / "net/gui/NativeHudPacket.java")
    p72_net = read(SRC / "net/gui/LmNativeNetwork.java")
    p72_proto = read(SRC / "net/gui/LmNativeProtocol.java")
    p72_over = read(SRC / "client/HudOverlay.java")
    p72_client = read(SRC / "client/LmClient.java")
    p72_pg = read(SRC / "rival/ProvingGroundsBar.java")
    p72_ins = read(SRC / "rival/RivalInstinct.java")
    p72_tp = read(SRC / "util/TpBossNotify.java")
    p72_pen = read(SRC / "progression/tp/DeathTpPenalty.java")
    p72_mom = read(SRC / "progression/Momentum.java")
    p72_prog = read(SRC / "progression/ProgressionSystem.java")
    p72_hub = read(SRC / "gui/ultra/UltraHubScreens.java")
    p72_act = read(SRC / "gui/ultra/UltraGuiActions.java")
    p72_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    check("manifest row for lm 4.7.72", "lm-4772-native-hud" in manifest)
    check("native HUD: message 4 only to LM clients whose hello lists hud; never on the bridge",
          "MSG_HUD = 4" in p72_proto and 'FEATURE_HUD = "hud"' in p72_proto and "a.add(FEATURE_HUD);" in p72_proto
          and "LmNativeProtocol.MSG_HUD, NetworkDirection.PLAY_TO_CLIENT" in p72_net
          and "takesHud(player)) {" in p72_net
          and "return player != null && LmNativeNetwork.takesHud(player) && enabled(player);" in p72_hud)
    check("HUD switch per account, default on; Off hides the HUD so bars and chat come back",
          'static final String KEY = "lm_hud";' in p72_hud and "return !t.m_128441_(\"on\") || t.m_128471_(\"on\");" in p72_hud
          and "hideAll(player);" in p72_hud and "LmHud.ACTION_TOGGLE" in p72_hub and "LmHud.applyAction(viewer" in p72_act)
    check("Underdog: same parts as the bar, no bar for a HUD player",
          "hudLines(parts)" in p72_pg and "show(player, join(parts));" in p72_pg
          and 'return String.join(" - ", parts);' in p72_pg)
    check("Instinct: a card instead of the chat line, same cooldowns, a click opens that rival",
          "public static void deliver(ServerPlayer player" in p72_ins and '"go:rival|list_detail:" + rivalId' in p72_ins
          and "DmzRewards.msg(player, message);" in p72_ins and "COOLDOWNS.put(key, now);" in p72_ins)
    check("TP notice: same text, drains over SHOW_MS on the client, no boss bar for a HUD player",
          "(int) SHOW_MS, true);" in p72_tp and "hide(id);" in p72_tp)
    check("Death penalty: same text and fraction as the bar",
          "java.util.List.of(barText(player)), progress(player)" in p72_pen and "bar.m_142711_(progress(player));" in p72_pen)
    check("Momentum meter: the menu's clock, banked against the cap, only while fighting in the last 10 s",
          "MOMENTUM_FIGHT_MS = 10_000L" in p72_hud and "meterText(left)" in p72_mom
          and "Momentum.hudPulse(server, tick)" in p72_prog and "LAST_FIGHT.put(" in p72_mom)
    check("client: baked chrome per size/scale/skin, text laid out on arrival, clicks in chat, unbound key",
          "class HudChrome" in p72_over and "layout(s);" in p72_over and "instanceof ChatScreen" in p72_over
          and "GLFW.GLFW_KEY_UNKNOWN" in p72_over and "GLFW_HAND_CURSOR" in p72_over
          and "HudOverlay.init();" in p72_client and "HudOverlay.clear();" in p72_client)
    check("HUD self-test at boot (wire round trip, bar text rule)",
          "LmHud.selfTest()" in p72_test and "NativeHudPacket.decode(buf)" in p72_hud
          and "public static NativeHudPacket decode(FriendlyByteBuf buf)" in p72_pkt)

    print("\n--- § lm 4.7.71 ---")
    p71_reg = read(SRC / "gui/ultra/PageRegistry.java")
    p71_nav = read(SRC / "gui/ultra/NavHistory.java")
    p71_spec = read(SRC / "gui/ultra/UltraScreenSpec.java")
    p71_act = read(SRC / "gui/ultra/UltraGuiActions.java")
    p71_hub = read(SRC / "gui/ultra/UltraHubScreens.java")
    p71_prev = read(SRC / "gui/ultra/ModelPreview.java")
    p71_parts = read(SRC / "gui/ultra/PlayerModelParts.java")
    p71_look = read(SRC / "client/NativeFormLook.java")
    p71_model = read(SRC / "client/NativeModelPreview.java")
    p71_menu = read(SRC / "client/NativeMenuScreen.java")
    p71_proto = read(SRC / "net/gui/LmNativeProtocol.java")
    p71_ev = read(SRC / "event/DifficultyEvents.java")
    p71_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    p71_build = read(ROOT / "tools/dmz-adaptive-difficulty/build.sh")
    p71_fig = _after(p71_prev, "public static JsonObject formFigure(").split("\n    }\n", 1)[0]
    check("manifest row for lm 4.7.71", "lm-4771-nav-preview" in manifest)
    check("form figure: the viewer's colours for that form, head bones, appearance, skin owner; made-up subject",
          "PlayerModelParts.previewColors(owner, form.race(), form.group(), form.form())" in p71_fig
          and "PlayerModelParts.headBonesOf(owner)" in p71_fig and 'parts.addProperty("appearance", look);' in p71_fig
          and 'parts.addProperty("skinOwner", owner.m_20148_().toString());' in p71_fig
          and '"subject", "uuid:" + UUID.nameUUIDFromBytes' in p71_fig and '"local"' not in p71_fig
          and "public static JsonObject previewColors(ServerPlayer owner, String race, String group, String form)" in p71_parts)
    check("LM client loads the appearance and wears the skin owner's Minecraft skin",
          "ch.loadAppearance(" in p71_look and "skinOwner(parts)" in p71_model)
    check("page registry: one source for Back, breadcrumb and search; the build fails on a page without an entry",
          "public static String[] parent(String screen, String page)" in p71_reg
          and "public static List<Ref> crumbs(String screen, String page)" in p71_reg
          and "public static List<Hit> search(String query, boolean staff, int max)" in p71_reg
          and 'python3 "$HERE_SIM/audit_page_registry.py"' in p71_build
          and "PageRegistry.problems()" in p71_test and "NavHistory.selfTest()" in p71_test)
    check("one back(): history on the LM client, registry parent on the bridge; old footers gone",
          "public UltraScreenSpec back()" in p71_spec and "NavHistory.previous(" in p71_spec
          and "PageRegistry.parent(screenId, pageId)" in p71_spec
          and "public UltraScreenSpec footerBackHome(" not in p71_spec
          and '"nav:back".equals(action)' in p71_act and "NavHistory.pop(" in p71_act)
    check("page history per player, capped, cleared on logout",
          "MAX = 32" in p71_nav and "NavHistory.clear(" in p71_ev)
    check("breadcrumb and search only for clients whose hello lists nav",
          'FEATURE_NAV = "nav"' in p71_proto and "drawBreadcrumb(" in p71_menu and "clickCrumb(" in p71_menu
          and '"breadcrumb"' in p71_spec and 'action.startsWith("go:")' in p71_act)
    check("hub search: field row, 5 recents, no Goals placeholder (4.7.80: tabs Home, Goals, More; search on More)",
          '.tabs("home|Home", PAGE_GOALS + "|Goals", PAGE_MORE + "|More");' in p71_hub
          and "placeSearchRow(spec" in p71_hub and "RECENT_MAX = 5" in p71_hub
          and "No goals yet" not in p71_hub)

    print("\n--- § lm 4.7.68 ---")
    p68_board = read(SRC / "dojo/BoardSpar.java")
    p68_svc = read(SRC / "dojo/DojoService.java")
    p68_cat = read(SRC / "dojo/DojoCatalog.java")
    p68_world = read(SRC / "dojo/DojoWorld.java")
    p68_build = read(SRC / "dojo/DojoBuilder.java")
    p68_war = read(SRC / "dojo/DojoWar.java")
    p68_store = read(SRC / "dojo/DojoStore.java")
    p68_ui = read(SRC / "gui/ultra/UltraDojoScreens.java")
    p68_spar = read(SRC / "gui/ultra/UltraSparScreens.java")
    p68_sys = read(SRC / "sparring/SparringSystem.java")
    p68_combat = read(SRC / "sparring/SparCombat.java")
    p68_ev = read(SRC / "event/DifficultyEvents.java")
    p68_rank = read(SRC / "sparring/DojoRankings.java")
    res = ROOT / "tools/dmz-adaptive-difficulty/src/main/resources/data/legacymechanics"
    p68_dim = read(res / "dimension/dojo.json")
    p68_dimtype = read(res / "dimension_type/dojo.json")
    check("manifest row for lm 4.7.68", "lm-4768-dojos" in manifest)
    check("Sparring tabs: Home, Leaderboard, Spar Board (4.7.78: Dojo is its own system; wars live there)",
          '.tabs("home|Home", "leaderboard|Leaderboard", "sparboard|Spar Board")' in p68_spar
          and '.tabs("home|Home", "leaderboard|Leaderboard", TAB_BOARD + "|Spar Board")' in p68_ui
          and 'lower.equals("dojo_wars") || lower.equals("wars") || lower.equals("dojo_war")' in p68_ui)
    check("a formal spar starts only from the board: trading hits starts nothing, startBoardSession is the one start",
          "// 4.7.68: trading hits no longer starts a spar." in p68_sys
          and p68_sys.count("startSession(attacker, target)") == 0
          and "public static void startBoardSession(ServerPlayer a, ServerPlayer b, String boutId, boolean ranked," in p68_sys
          and "SparringSystem.startBoardSession(poster, acceptor, b.id, b.ranked, b.potentialA, b.potentialB);" in p68_board)
    check("no dojo, no spar: a human message with a button to the dojo list",
          '"Join a dojo first. No dojo, no spar."' in p68_board and 'spec.buttonLeft("page:dojo_list", "Find a dojo"' in p68_ui)
    check("fights in the poster's arena; both go back to exactly where they stood (offline ones on login)",
          "Dojo host = DojoService.dojoOf(poster);" in p68_board and 'DojoWorld.rememberSpot(poster, "spar");' in p68_board
          and "DojoWorld.returnPlayer(p);" in p68_board and "s.yaw = p.m_146908_();" in p68_world
          and "p.m_8999_(target, s.x, s.y, s.z, s.yaw, s.pitch);" in p68_world)
    # 4.7.84 (SP-4): the Friendly Fist mark left the listing. 4.7.86 (height budget): one line per post, no wait time.
    check("listing shows name, level, friendly/ranked and wager on one line; level range filter",
          '" Lv " + lvl' in p68_ui and '", Friendly Fist "' not in p68_ui and "always one body line" in p68_ui
          and "Math.abs(level(poster) - level(viewer)) > post.range()" in p68_board
          and "LEVEL_RANGES = {0, 25, 50, 100};" in p68_cat)
    check("ranked gate: 30 friendly spars, the count shown, ranked dim until then",
          "RANKED_GATE = 30;" in p68_cat and '" friendly spars: " + unranked + " done, "' in p68_ui
          and 'spec.lockedButton("Kind - Friendly");' in p68_ui and "return unranked(id) >= DojoCatalog.RANKED_GATE;" in p68_board)
    check("friendly pays nothing; ranked pays spar TP and alone feeds the leaderboard, streak and season RP",
          "if (!rt.boardRanked) {\n            return 0;\n        }" in p68_combat
          and "if (aRt.boardCredit) {\n            updateLeaderboard(player, aRt, duration);" in p68_sys
          and "if (bRt.boardCredit) {" in p68_sys)
    check("wagers: 5 netherite coins or 20 Dojo Points, both escrowed at accept, winner takes both, refunds otherwise",
          "MAX_COIN_WAGER = 5L * NETHERITE_COPPER;" in p68_cat and "MAX_POINT_WAGER = 20;" in p68_cat
          and "static boolean escrow(Post post, ServerPlayer poster, ServerPlayer acceptor)" in p68_board
          and 'payCoins(server, payTo.toString(), e.amount * 2);' in p68_board
          and 'return "refunded to both (" + text + " each)";' in p68_board
          and "public static void refundStaleEscrow()" in p68_board)
    check("anti-farm: counts after 1 minute with both landing damage; 15-minute pair cooldown for ranked credit",
          "MIN_FIGHT_MS = 60_000L;" in p68_cat and "PAIR_COOLDOWN_MS = 15L * 60L * 1000L;" in p68_cat
          and "boolean credit = b.ranked && counted && rankedFresh;" in p68_board
          and 'PAIR.put("ranked|" + pair, now);' in p68_board)
    check("bouts end by KO (lethal blow caught at 1 HP), ring-out past the ring's edge, forfeit, disconnect or the timeout",
          "BoardSpar.queueKnockout(victim, attacker);" in p68_sys and "RING_OUT_HALF = 12;" in p68_world
          and "Math.abs(p.m_20185_() - b.cx) > DojoWorld.RING_OUT_HALF" in p68_board
          and 'end(b, id.equals(b.a) ? b.b : b.a, "RING_OUT");' in p68_board
          and 'case "logout", "partner-offline" -> "DISCONNECT";' in p68_board)
    check("one void dojo dimension from the LM datapack; plots 4000 blocks apart, never reused",
          '"type": "minecraft:flat"' in p68_dim and '"biome": "minecraft:the_void"' in p68_dim
          and '"natural": false' in p68_dimtype and "PLOT_SPACING = 4000;" in p68_world
          and "d.plot = DojoStore.get().nextPlot++;" in p68_world)
    p68_place = read(SRC / "dojo/DojoPlacer.java")
    p68_state = read(SRC / "dojo/DojoState.java")
    p68_pics = read(SRC / "dojo/DojoPictures.java")
    p68_native = read(SRC / "client/NativeMenuScreen.java")
    p68_spec = read(SRC / "gui/ultra/UltraScreenSpec.java")
    p68_hub = read(SRC / "gui/ultra/UltraHubScreens.java")
    p68_index = read(res / "dojo/index.json")
    structs = res / "structures/dojo"
    check("one ring to start, up to 6; the hall's look follows the rings; upgrades with basic / refined / masterwork looks",
          "MAX_ARENAS = 6;" in p68_cat and "d.upgrades.put(DojoCatalog.ARENA, 1);" in p68_svc
          and "s.tier = DojoCatalog.lookTier(DojoCatalog.ARENA, s.arenas);" in p68_state
          and "static String[] palette(int look)" not in p68_build)
    check("one Japanese hall from the mod-toolkit: a base and 24 pieces per hall tier and style, and the plot index",
          all((structs / ("t%d_%s" % (t, st)) / "base.nbt").is_file() for t in (1, 2, 3)
              for st in ("none", "ki", "melee", "strike", "evasion"))
          and (structs / "t3_evasion/garden_3.nbt").is_file() and (structs / "t1_none/arena_6.nbt").is_file()
          and '"rings": [' in p68_index and '"spawn": [' in p68_index and '"balcony": [' in p68_index)
    check("a new dojo is the empty hall with one sparring mat; an upgrade rebuilds only its part (whole plot on a new tier or style)",
          'return "v2|t=" + tier + "|s=" + style + "|a=" + arenas' in p68_state
          and "if (was == null || !was.key().equals(key)) {" in p68_place
          and "steps.add(new Step(key + \"/base\", zero, boxes));" in p68_place
          and "Your hall starts with one ring; upgrades build into it." in p68_svc)  # 4.7.84 (X7): "ring"
    check("placed like DMZ's grounds: chunk tickets, a per-tick time budget, checks when the plot is done",
          "TICK_BUDGET_NANOS = 6_000_000L;" in p68_place and "cache.m_8387_(TICKET, c, 0, c);" in p68_place
          and "cache.m_8438_(TICKET, c, 0, c);" in p68_place and "static void afterBuild(ServerLevel level, int plot, String master)" in p68_world
          and 'safePulse("dojo-builder", com.dbzlegacy.adaptivedifficulty.dojo.DojoPlacer::tick);' in p68_ev)
    check("a spar waits for the host's hall to be built; fighters land on the hall's ring centres",
          'return host.name + "\'s hall is being built. Try again in a few seconds.";' in p68_board
          and "double[] c = DojoWorld.ringCenter(host, arena);" in p68_board)
    # 4.7.88: the staff-only block rule became DojoWorldRules (only the plot's master builds; no staff bypass).
    p68_rules = read(SRC / "dojo/DojoWorldRules.java")
    check("the dojo world is protected: only the plot's master breaks or places blocks; blasts break nothing",
          "public void onBreak(BlockEvent.BreakEvent event)" in p68_rules
          and "public void onPlace(BlockEvent.EntityPlaceEvent event)" in p68_rules
          and "onDojoBreak" not in p68_ev
          and "event.getAffectedBlocks().clear();" in p68_ev)
    check("main menu: Sparring and Dojo tiles, one tap each (4.7.78)",
          'new HubTile("spar", "Sparring"' in p68_hub and 'new HubTile("dojo", "Dojo"' in p68_hub
          and 'UltraGui.open(player, "spar", "sparboard");' in p68_hub and 'UltraGui.open(player, "spar", "dojo");' in p68_hub)
    check("upgrade pages show Now, After and Where (green) on LM clients; the text map stays for KaiPath",
          'JsonObject now = whole(closeUp(part, lvNow, before.style, before.tier), "Now");' in p68_pics
          and "JsonObject where = where(after, part, \"Where\");" in p68_pics
          and "public UltraScreenSpec pictures(List<String> textLines, int minRows, int sidePermille, JsonArray items)" in p68_spec
          and "private void drawPictures(GuiGraphics g)" in p68_native
          and "LmNativeNetwork.takesPictures(player)" in p68_ui and "DojoWorld.mapLines(d, up, !maxed)" in p68_ui)
    check("spectating needs the spectator balcony; spectators can't be hit or hit, and stay on the balcony",
          '" has no spectator room, so its spars can\'t be watched."' in p68_world
          and "DojoWorld.spectating(victim.m_20148_())" in p68_ev and "static double[] galleryBox(Dojo d)" in p68_world)
    check("upgrade preview: top-down map with the room highlighted as it would be, a card, Visit dojo",
          "public static List<String> mapLines(Dojo d, String highlight, boolean preview)" in p68_world
          and 'spec.buttonRight("do:dojo_visit:" + d.masterUuid, "Visit dojo"' in p68_ui)
    check("Dojo Points: earned only by dojo things with a daily cap per source, spent only on the dojo",
          "HOST(2, 20," in p68_cat and "WAR_BOUT(5, 40," in p68_cat and "TRAINING(3, 15," in p68_cat
          and "int grant = Math.max(0, Math.min(earn.points, earn.dailyCap - today));" in p68_svc
          and "awardTp" not in p68_svc + p68_cat + p68_war and "Prestige" not in p68_svc + p68_cat + p68_board + p68_war)
    check("styles and bonuses: four styles, one active bonus from the style's catalog, race scope, modest numbers",
          "KI(\"Ki Masters\"" in p68_cat and "EVASION(\"Evasion\"" in p68_cat
          and "FOCUS_PCT = 0.05;" in p68_cat and "RACE_PCT = 0.08;" in p68_cat
          and "BONUS_COOLDOWN_MS = 7L * 24L * 60L * 60L * 1000L;" in p68_cat
          and "DojoService.styleBonus(player, hitKind)" in p68_combat)
    check("members 5 to 30, two elites, elite wars need the master's sign-off unless given war rights",
          "BASE_DISCIPLES = 5;" in p68_cat and "MAX_MEMBERS = 30;" in p68_cat and "MAX_ELITES = 2;" in p68_cat
          and 'w.status = needsSignoff ? "PENDING_SIGNOFF" : "CHALLENGED";' in p68_war
          and "mine.apprenticeCount() >= apprenticeCap(" in p68_sys)
    check("wars: bracket or gauntlet, power-of-2 seeds, RULES / INTERMISSION / PREVIEW / BOUT, KO or ring-out",
          "public static int slots(int n)" in p68_war and 'case "RULES" ->' in p68_war
          and 'case "INTERMISSION" ->' in p68_war and 'case "PREVIEW" ->' in p68_war and 'case "BOUT" ->' in p68_war
          and '"GAUNTLET".equals(r.rec.format)' in p68_war and "DojoWar.onBoutDamage(victim, attacker, event.getAmount())" in p68_ev)
    check("war record: winner, score, fights; expired challenges stop blocking so a pair can challenge again",
          "public List<WarFight> fights" in p68_store and "public String winnerDojo" in p68_store
          and 'w.status = "EXPIRED";' in p68_war and "static boolean open(WarRecord w)" in p68_war
          and "SparStore.get().dojoChallenges.values().removeIf(c -> c == null || c.expiresAt < nowMs);" in p68_ev)
    check("clout is seasonal: reset on the one season rollover path, upgrades and rooms untouched",
          "DojoService.onSeasonAdvanced(ended, store.dojoSeason.seasonId);" in p68_rank
          and "public static void onSeasonAdvanced(int endedSeason, int newSeason)" in p68_svc
          and "upgrades" not in p68_svc.split("public static void onSeasonAdvanced(", 1)[1].split("public static List<Dojo> byClout", 1)[0])
    check("reopening the same dojo drops every upgrade one tier",
          "d.upgrades.put(up, DojoCatalog.afterReopen(up, t));" in p68_svc
          and "return Math.max(minTier(id), tier - 1);" in p68_cat)
    check("no void or fall deaths in the dojo world; outsiders can't hit a fighter mid-bout",
          '"fall".equals(id) || "outOfWorld".equals(id)' in p68_ev
          and "bout != null && attacker != null && !" in p68_ev)
    p68_pu = read(SRC / "progression/skills/PotentialProgression.java")
    check("Potential Unlock: normal in the world; in a spar session only for a fighter who opted in on a friendly spar",
          "boolean session = com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem.inSessionTogether(victim, attacker);" in p68_pu
          and "if (attackerTrains) {\n                apply(attacker, victim, atkMethod, atkPoints);" in p68_pu
          and "if (victimTrains) {\n                apply(victim, attacker, defMethod, defPoints);" in p68_pu
          and "return rt != null && rt.active && !rt.boardRanked && rt.boardPotential;" in p68_sys
          and "rt.boardPotential = !ranked && (p == a ? potentialA : potentialB);" in p68_sys
          and "b.potentialB = !b.ranked && draft(acceptor.m_20148_()).want() == Want.POTENTIAL;" in p68_board
          and 'TP("TP"), POTENTIAL("Potential Unlock"), ANY("Anything");' in p68_board
          and '", wants " + want' in p68_ui and 'w == BoardSpar.Want.POTENTIAL ? "PU"' in p68_ui)
    check("no dojo bonus, training session or arena touches Potential Unlock",
          "dojo" not in p68_pu.replace("dojo arena", "")
          and "potential" not in (p68_svc + p68_cat + p68_war).lower())
    check("dim, never hidden: ranked before the gate, upgrades out of reach, spars without a dojo",
          'spec.lockedButton("Post a spar")' in p68_ui and 'spec.lockedButton(maxed ? "Fully upgraded" : "Buy for "' in p68_ui)

    print("\n--- § lm 4.7.67 ---")
    p67_svc = read(SRC / "character/CosmeticBodyPartService.java")
    p67_scr = read(SRC / "gui/ultra/UltraCharacterScreens.java")
    p67_act = read(SRC / "gui/ultra/UltraGuiActions.java")
    check("manifest row for lm 4.7.67", "lm-4767-part-colors-wheel" in manifest)
    check("presets unchanged: the 16 swatches, two pages of 8, instant save; actions carry six hex digits",
          p67_svc.count("new Preset(") == 16 and '"Pick a swatch. It saves immediately."' in p67_scr
          and '"do:bodypart_color:" + channel + ":" + String.format(Locale.ROOT, "%06x", preset.rgb() & 0xFFFFFF);' in p67_scr
          and '"do:bodypart_color_reset:" + channel,' in p67_scr)
    check("Pick any color: hex field with a color square, Apply bound to it, the wheel on LM clients that draw one",
          'PICK_INPUT = "input:bodycolor_hex";' in p67_scr and "spec.colorPreview(input);" in p67_scr
          and 'spec.buttonBound("do:bodypart_color:" + channel + ":", UltraStyle.confirm("Apply this color"),' in p67_scr
          and "boolean wheel = typing && com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesColorWheel(player);" in p67_scr
          and "spec.colorWheel(PICK_WHEEL, input, UltraStyle.COL_L, spec.row(), 56);" in p67_scr)
    check("one save path: Apply joins the typed hex to do:bodypart_color and executeColorPick checks it",
          '"bodypart_color".equals(parts.action) && parts.arg != null && parts.arg.endsWith(":")' in p67_act
          and 'args.addProperty("arg", parts.arg + (typed.length() > 16 ? typed.substring(0, 16) : typed));' in p67_act
          and "int rgb = com.dbzlegacy.adaptivedifficulty.gui.ultra.MenuSkins.parseHex(bits[1]);" in p67_svc
          and "persistPartColor(player, channel, rgb);" in p67_svc)
    check("bad hex shows the human error and applies nothing; the old swatch message is gone; empty keeps the color",
          "return UltraStyle.DANGER + com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraHubScreens.BAD_HEX;" in p67_svc
          and "That swatch is not available" not in p67_svc
          and '"Nothing changed - type a color like #e23b3b, or tap a swatch."' in p67_svc)
    check("no text input: the config editor's plain note; storage, cost and channels unchanged",
          '"Typed fields need the Legacy Mechanics client. Until then pick a swatch."' in p67_scr
          and "LmGuiNetwork.sendModelSync(" in p67_svc and "GuiModelSyncPacket.REASON_COLOR" in p67_svc)

    print("\n--- § lm 4.7.66 ---")
    p66_reg = read(SRC / "gui/ultra/MenuSkins.java")
    p66_prefs = read(SRC / "gui/ultra/MenuPrefs.java")
    p66_hub = read(SRC / "gui/ultra/UltraHubScreens.java")
    p66_act = read(SRC / "gui/ultra/UltraGuiActions.java")
    p66_spec = read(SRC / "gui/ultra/UltraScreenSpec.java")
    p66_wheel = read(SRC / "gui/ultra/ColorWheel.java")
    p66_proto = read(SRC / "net/gui/LmNativeProtocol.java")
    p66_netw = read(SRC / "net/gui/LmNativeNetwork.java")
    p66_pkt = read(SRC / "net/gui/NativePrefsPacket.java")
    p66_screen = read(SRC / "client/NativeMenuScreen.java")
    p66_look = read(SRC / "client/MenuLook.java")
    check("manifest row for lm 4.7.66", "lm-4766-custom-skin" in manifest)
    check("Custom is a sixth choice after Zeno Omni, outside the five-skin registry; 'custom' is a known id",
          "if (ALL.size() != 5) {" in p66_reg and 'CUSTOM_ID = "custom";' in p66_reg
          and "return CUSTOM_ID.equals(k) || parseCustom(k) != null;" in p66_reg
          and 'String customAction = "do:" + ACTION_SKIN + ":" + MenuSkins.customToken(prefs.custom());' in p66_hub
          and p66_hub.index("for (MenuSkins.Skin skin : MenuSkins.ALL) {") < p66_hub.index("String customAction ="))
    check("picking Custom is free and opens My colors; Edit my colors while it is worn",
          "UltraHubScreens.pageAfterSkinAction(parts.action, stringArg(args, \"arg\"))" in p66_act
          and "return ACTION_SKIN.equals(action) && MenuSkins.isCustom(arg) ? PAGE_CUSTOM : PAGE_SKINS;" in p66_hub
          and 'spec.buttonLeft("page:" + PAGE_CUSTOM, "Edit my colors", UltraStyle.STYLE_NORMAL);' in p66_hub)
    check("editor: five hex fields, Style and Scene, Apply with live swatch, From presets, Reset; only Apply saves",
          'TONE_NAMES = {"Panel", "Primary", "Trim", "Text", "Confirm"};' in p66_hub
          and 'UltraStyle.dim("Preview")' in p66_hub and '":dark"' in p66_hub and '":motif"' in p66_hub
          and '":from:"' in p66_hub and '":reset"' in p66_hub
          and 'case "apply" -> next = applyDraft(' in p66_hub
          and p66_hub.count("MenuPrefs.setCustom(player, c);") == 1
          and "spec.bindInputsToButtons(fields).enterClicks(apply);" in p66_hub)
    check("hex: #RRGGBB or RRGGBB any case; a bad field applies nothing and keeps every field; empty keeps the color",
          'BAD_HEX = "That doesn\'t look like a color - use 6 hex digits, like #e23b3b";' in p66_hub
          and "if (!bad.isEmpty()) {\n            return new Draft(d.raw(), d.dark(), d.motif()," in p66_hub
          and "if (v.isEmpty()) {\n                continue;" in p66_hub
          and 'parseHex("#gggggg") != -1' in p66_reg and 'parseHex("red") != -1' in p66_reg)
    check("readability: custom text made readable on the panel last (3:1 dark, 4.5:1 light); the page says so",
          "return readable(t, c.panel(), min);" in p66_reg and "return dark ? 3.0 : 4.5;" in p66_reg
          and '"Text color adjusted so it stays readable (now "' in p66_hub
          and "contrast(s.text(), s.panel()) < textTarget(dark)" in p66_reg)
    check("derived tones: background and motif colors blend from the custom panel / primary / trim",
          "bgTop = blend(x.panel(), x.primary(), 0.10);" in p66_reg and "bgBottom = blend(x.panel(), BLACK, 0.75);" in p66_reg
          and "motifA = blend(x.primary(), BLACK, 0.40);" in p66_reg)
    # 4.8.2 (Part E): the bridge letters and the send-time rewrite are deleted with the KaiPath bridge.
    check("bridge: nearest readable color letters; starting tones keep the Saiyan Royal bytes; no panel tint",
          False, retired=True)
    check("4.8.2: no bridge letters on a skin (custom included), no nearestBridgeLetter / BRIDGE_PANEL",
          "nearestBridgeLetter" not in p66_reg and "BRIDGE_PANEL" not in p66_reg
          and "bridgePrimary" not in p66_reg and "bridgeText" not in p66_reg
          and "bgTop, bgBottom, motifA, x.trim(), x.motif());" in p66_reg)
    check("storage: lm_menu_prefs gains a custom object, kept when switching to a preset and back",
          'static final String CUSTOM_KEY = "custom";' in p66_prefs and "t.m_128365_(CUSTOM_KEY, ct);" in p66_prefs
          and "now.custom()" in p66_prefs.split("public static Prefs setSkin(", 1)[1].split("}", 1)[0]
          and "MenuSkins.isCustom(skin) ? MenuSkins.custom(custom) : MenuSkins.byId(skin)" in p66_prefs)
    check("no protocol change: the custom look rides message 3's skin string as a token (an old client draws Saiyan Royal)",
          "new NativePrefsPacket(p.skinOrDefault().id(), p.backgrounds(), p.lite())" in p66_netw
          and "GuiOpenPacket.trim(skin == null ? \"\" : skin, 64)" in p66_pkt
          and 'PROTOCOL_VERSION = "1";' in p66_proto and "MSG_PREFS = 3;" in p66_proto
          and "tok.length() > 64" in p66_reg and "if (!next.equals(skin)) {" in p66_look)
    check("clients that cannot edit (an older LM client; 4.8.2: no KaiPath) get a plain note and can still wear Custom",
          "return com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesTextInput(player)\n                && com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesCustomSkin(player);" in p66_hub
          and '"Editing your colors needs the Legacy Mechanics client, 4.7.66 or newer."' in p66_hub
          and 'FEATURE_CUSTOM_SKIN = "customSkin";' in p66_proto and "a.add(FEATURE_CUSTOM_SKIN);" in p66_proto)
    check("color wheel: additive input type, never routes a page, sent only to clients that draw it",
          'TYPE = "color_wheel";' in p66_wheel and 'FEATURE = "colorWheel";' in p66_wheel
          and "case com.dbzlegacy.adaptivedifficulty.gui.ultra.ColorWheel.TYPE -> { }" in p66_proto
          and "boolean wheels = editing && com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesColorWheel(player);" in p66_hub
          and 'o.addProperty("target", in.target == null ? "" : in.target);' in p66_spec
          and "} else if (ColorWheel.TYPE.equals(type)) {" in p66_screen and "private boolean pickWheel(" in p66_screen
          and "ColorWheel.selfTest()" in read(SRC / "net/gui/LmGuiNetwork.java"))
    check("LM client sends every listed field with a click; Enter applies; field color squares; live swatch",
          'for (String fid : str(args, "bindInputs", "").split(' in p66_screen
          and "Btn onEnter = enterButton(f.id());" in p66_screen and "private void drawFieldColor(" in p66_screen
          and "CUSTOM_PAGE.equalsIgnoreCase(packet.pageId())" in p66_screen)
    check("system accents stay fixed in every skin",
          "case 'c' -> 0xE23B3B;" in p66_reg and "case '3' -> 0x00AAAA;" in p66_reg and "case '4' -> 0xAA0000;" in p66_reg)

    print("\n--- § lm 4.7.65 ---")
    p65_sys = read(SRC / "sparring/SparringSystem.java")
    p65_rt = read(SRC / "sparring/SparPlayerRuntime.java")
    p65_ev = read(SRC / "event/DifficultyEvents.java")
    check("manifest row for lm 4.7.65", "lm-4765-ff-never-kills" in manifest)
    check("Friendly Fist guard: active spar partner only, not a rival challenge, either fighter's switch",
          "public static boolean friendlyFistGuards(ServerPlayer victim, ServerPlayer attacker)" in p65_sys
          and "!vRt.partner.equals(attacker.m_20148_())" in p65_sys
          and "return isFriendlyFistOn(victim) || isFriendlyFistOn(attacker);" in p65_sys
          and _after(p65_sys, "public static boolean friendlyFistGuards(").split("public static float", 1)[0]
          .count("RivalChallengeManager.get().isInChallenge(") == 2)
    check("a lethal partner hit is cut to leave 1 HP at the final damage step (no 1.5 HP pre-hit gate)",
          "if (amount < hp || !friendlyFistGuards(victim, attacker)) {" in p65_sys
          and "return Math.max(0.0f, hp - 1.0f);" in p65_sys
          and "@SubscribeEvent(priority = EventPriority.LOWEST)\n    public void onSparLethalDamage(LivingDamageEvent event)" in p65_ev
          and "SparringSystem.capLethalSparDamage(victim, attacker, event.getAmount())" in p65_ev)
    check("backstop: a guarded spar death is cancelled at HIGHEST (1 HP, healed), so it never records death",
          "@SubscribeEvent(priority = EventPriority.HIGHEST)\n    public void onSparLethalDeath(LivingDeathEvent event)" in p65_ev
          and "SparringSystem.catchLethalSparDeath(victim, attacker)) {\n            event.setCanceled(true);" in p65_ev
          and "victim.m_21153_(1.0f);" in p65_sys)
    check("protected every time: the once-per-session heal latch is gone; heal runs after the hit, spar continues",
          "ffKdHealed" not in p65_sys and "ffKdHealed" not in p65_rt
          and "server.m_6937_(new net.minecraft.server.TickTask(server.m_129921_(), heal));" in p65_sys
          and "friendlyFistHeal(victim, attacker, vRt);" in p65_sys
          and "endSession(" not in _after(p65_sys, "public static boolean friendlyFistGuards(")
          .split("private static boolean isFriendlyFistOn(", 1)[0])
    check("Friendly Fist off: a spar death still ends the session as death",
          'endSession(victim, partner, "death");' in p65_sys)

    print("\n--- § lm 4.7.64 ---")
    p64_hub = read(SRC / "gui/ultra/UltraHubScreens.java")
    p68_dojo = read(SRC / "dojo/BoardSpar.java") + read(SRC / "dojo/DojoWorld.java") + read(SRC / "dojo/DojoWar.java")         + read(SRC / "dojo/DojoService.java") + read(SRC / "gui/ultra/UltraDojoScreens.java")
    p64_dash = read(SRC / "gui/ultra/HubDashboard.java")
    p64_board = read(SRC / "sparring/SparBoard.java")
    p64_spar = read(SRC / "gui/ultra/UltraSparScreens.java")
    p64_diff = read(SRC / "gui/ultra/UltraDifficultyScreens.java")
    p64_pres = read(SRC / "gui/ultra/UltraPrestigeScreens.java")
    p64_act = read(SRC / "gui/ultra/UltraGuiActions.java")
    p64_native = read(SRC / "client/NativeMenuScreen.java")
    p64_sys = read(SRC / "sparring/SparringSystem.java")
    p64_ev = read(SRC / "event/DifficultyEvents.java")
    p64_gui = read(SRC / "net/gui/LmGuiNetwork.java")
    check("manifest row for lm 4.7.64", "lm-4764-central-hub" in manifest)
    # 4.7.71: Goals waits for real weekly goals; the hub tabs are Home, Search, More and old goals links open Search.
    check("hub: three tabs, Home first (4.7.80: Home, Goals, More)",
          '.tabs("home|Home", PAGE_GOALS + "|Goals", PAGE_MORE + "|More")' in p64_hub
          and 'case "search", "more" -> UltraHubScreens.PAGE_MORE;' in p64_act)
    # 4.7.84: Train / Compete / Progress became one "Your systems" card (X5 height budget); Prestige sits beside the
    # "Fastest now" button (H3), so its tile is a half-width pair now.
    check("hub Home: next prestige, Fastest now with its button, Your systems, Prestige beside Fastest now",
          'UltraStyle.header("Your next prestige")' in p64_hub and "HubDashboard.fastestNowWithGo(player)" in p64_hub
          and 'UltraStyle.header("Your systems")' in p64_hub and 'UltraStyle.header("Train")' not in p64_hub
          and 'new HubTile("prestige", "Prestige", UltraStyle.STYLE_NORMAL, false), true);' in p64_hub
          and 'new HubTile("dojo", "Dojo"' in p64_hub)
    check("Menu skins and Staff Admin moved to More; no Goals placeholder (4.7.71)",
          'new HubTile("menu_skins", "Menu skins"' in _after(p64_hub, "private static void openMore(")
          and 'new HubTile("admin", "Staff Admin"' in _after(p64_hub, "private static void openMore(")
          and 'spec.lockedButton("No goals yet")' not in p64_hub)
    check("LM links only to itself: no command runs from a menu, no other mod's screen",
          all(x not in p64_hub + p64_dash + p64_board + p64_spar + p68_dojo
              for x in ("performPrefixedCommand", "dispatchCommand", "/home", "/spawn", "/tpa", "/empire",
                        "/meditate", "dmzultra"))
          and '"/lm open spar sparboard"' in p68_dojo)
    check("Fastest now uses the tier table ratio (no made-up percentages) and ranks prestige first",
          "DifficultyGuide.change(before, after)" in p64_dash and "ut.enemyScalingMultiplier()" in p64_dash
          and '"Fastest now: T4 Veteran, +38% TP per kill."' in p64_dash
          and "%" not in _after(p64_dash, "public static String fastest(").split("public static String fastestNow", 1)[0].replace('" TP per kill."', ""))
    check("spar streak line only when the player has a streak",
          "bond.streakCurrent <= 0" in p64_dash and 'return "Your spar streak: "' in p64_dash)
    # 4.7.68 replaced the 4.7.64 list/ask board with the Spar Board tab (dojo.BoardSpar): this code no longer exists.
    check("spar board: 15 min listing, want cycle, ask with cooldown, say yes, cleared on spar start and logout",
          False, retired=True)
    check("spar board preview uses the real hit multiplier and pays nothing", False, retired=True)
    # 4.7.84 (D-4): Difficulty's "Beyond Difficulty" lines left (the main menu shows both); Prestige and Sparring
    # keep theirs.
    check("Prestige and Sparring Homes show lines from the other systems",
          'UltraStyle.header("Beyond Difficulty")' not in p64_diff
          and "HubDashboard.fastestNow(player)" in p64_pres
          and "HubDashboard.sparWinLine(player)" in p64_spar)
    check("native draws one box per header group; bridge text unchanged",
          "List<int[]> groups = bodyClipped() ? null : lineGroups(lines);" in p64_native
          and "if (headers < 2) {" in p64_native)
    p64_med = read(SRC / "progression/skills/LivingWorldNearbyMeditation.java")
    p64_help = read(SRC / "config/editor/ConfigFieldHelp.java")
    check("meditation: two-NPCs path removed, player path kept at full rate",
          "AmbientFighterEntity" not in p64_med and "nearTwoLivingCircleNpcs" not in p64_med
          and "withNpcs" not in p64_med and "long base = deltaMs;" in p64_med
          and "if (!withPlayer) {" in p64_med and "nearMeditatingPlayer(player, players, r2)" in p64_med
          and '"lm.lw_med.progress_ms"' in p64_med
          and "Meditate near another player.\");" in p64_med and "NPCs count at 75%" not in p64_med
          and "or two meditating NPCs" not in p64_help
          and "two living NPCs" not in p64_hub)
    check("hub and board self-tests at boot",
          "HubDashboard.selfTest()" in p64_gui and "SparBoard.selfTest()" in p64_gui)

    print("\n--- § lm 4.7.63 ---")
    p63_diff = read(SRC / "gui/ultra/UltraDifficultyScreens.java")
    p63_tut = read(SRC / "gui/ultra/DifficultyTutorial.java")
    p63_dact = read(SRC / "service/DifficultyActions.java")
    p63_act = read(SRC / "gui/ultra/UltraGuiActions.java")
    p63_spec = read(SRC / "gui/ultra/UltraScreenSpec.java")
    p63_gui = read(SRC / "net/gui/LmGuiNetwork.java")
    check("manifest row for lm 4.7.63", "lm-4763-guide-once" in manifest)
    check("guide done is per account in the LM record (account UUID, not the character id)",
          'KEY = "lm_difficulty_tutorial";' in p63_tut and "LmPlayerStore.root(account)" in p63_tut
          and "UUID id = player.m_20148_();" in p63_tut and "LmPlayerStore.flush(id);" in p63_tut
          and "PrestigeCharacter" not in p63_tut)
    check("opening Difficulty routes by guide state; guide ids always replay",
          "switch (DifficultyTutorial.route(lower, DifficultyTutorial.done(player)))" in p63_diff
          and 'case "", "main", "home", "settings", "coins":\n                return done ? View.HOME : View.STEP1;'
              in p63_tut.replace("\r\n", "\n")
          and "DifficultyTutorial.selfTest()" in p63_gui)
    check("Skip guide on every step and Got it both mark done and land on Home",
          p63_diff.count("spec.footerGuide(") == 3 and 'UltraStyle.confirm("Got it - take me to Difficulty")' in p63_diff
          and 'placeFooter(action, UltraStyle.backTo(destination), skipActionId, "Skip guide")' in p63_spec
          and "DifficultyTutorial.markDone(player);" in p63_dact
          and "DifficultyTutorial.ACT_DONE.equals(parts.action)" in p63_act)
    check("Step 1 says once that the guide shows once; Home replays it without clearing the mark",
          'GUIDE_ONCE = "You only see this guide once. Replay it any time from Difficulty Home.";' in p63_diff
          and 'UltraStyle.plain("How difficulty works")' in p63_diff   # reworded in 4.7.78 and 4.7.84
          and "markDone" not in p63_diff and '"done", false' not in p63_tut)

    print("\n--- § lm 4.7.62 ---")
    p62_reg = read(SRC / "gui/ultra/MenuSkins.java")
    p62_prefs = read(SRC / "gui/ultra/MenuPrefs.java")
    p62_hub = read(SRC / "gui/ultra/UltraHubScreens.java")
    p62_act = read(SRC / "gui/ultra/UltraGuiActions.java")
    p62_screen = read(SRC / "client/NativeMenuScreen.java")
    p62_text = read(SRC / "client/NativeText.java")
    p62_look = read(SRC / "client/MenuLook.java")
    p62_bake = read(SRC / "client/MenuBackground.java")
    p62_netw = read(SRC / "net/gui/LmNativeNetwork.java")
    p62_proto = read(SRC / "net/gui/LmNativeProtocol.java")
    p62_gui = read(SRC / "net/gui/LmGuiNetwork.java")
    p62_client = read(SRC / "client/LmClient.java")
    check("manifest row for lm 4.7.62", "lm-4762-menu-skins" in manifest)
    check("registry holds exactly the five skins with the mock's hex",
          "List.of(SAIYAN_ROYAL, NAMEK_SERENITY, OBSIDIAN_GOLD, BEERUS_DESTRUCTION," in p62_reg
          and "0x0D201B, 0x57D9A3, 0xFFD166, 0xE6F4EC" in p62_reg
          and "0x17140F, 0xE8B33C, 0xE8B33C, 0xF3EAD8, 0x4CAF6D" in p62_reg
          and "0x281840, 0xF0B429, 0xE86A9A, 0xF3EAFF" in p62_reg
          and '"zeno_omni", "Zeno Omni", false,' in p62_reg and "0xF6F9FF, 0x2563EB, 0xF59E0B, 0x1E2A4A" in p62_reg
          and 'bad.add("registry must hold exactly the five skins, has "' in p62_reg
          and "MenuSkins.selfTest()" in p62_gui)
    hexes = re.findall(r"0x[0-9A-Fa-f]{6,8}", p62_screen + p62_text)
    check("no hardcoded tones in NativeMenuScreen / NativeText (every tone is skin-owned)", not hexes,
          f"found {hexes[:5]}" if hexes else "")
    check("light-aware inks: every ink made readable on its surface (3:1 dark skins, 4.5:1 light)",
          "return readable(raw, bg, skin.dark() ? 3.0 : 4.5);" in p62_reg and "MenuSkins.ink(skin, code, bg, fallback)" in p62_text
          and "box.m_94202_(skin.fieldText());" in p62_screen)
    check("per-account choice in the player's LM record; default Saiyan Royal, backgrounds on, Full",
          'KEY = "lm_menu_prefs";' in p62_prefs and "LmPlayerStore.root(id).m_128365_(KEY, t);" in p62_prefs
          and "new Prefs(MenuSkins.DEFAULT.id(), true, false);" in p62_prefs)
    check("native look over message 3, only to clients whose hello said menuSkins; no handshake change",
          "MSG_PREFS = 3;" in p62_proto and 'FEATURE_MENU_SKINS = "menuSkins";' in p62_proto
          and "a.add(FEATURE_MENU_SKINS);" in p62_proto and 'PROTOCOL_VERSION = "1";' in p62_proto
          and "!takesPrefs(player)" in p62_netw and "context.enqueueWork(() -> sendPrefs(sender));" in p62_netw
          and "LmNativeNetwork.onPrefs(MenuLook::apply);" in p62_client)
    # 4.8.2 (Part E): the bridge skin rewrite (bridgeSkin / bridgeRewrite) is deleted with the KaiPath bridge.
    check("bridge: Saiyan Royal bytes unchanged, other skins rewrite letters only, never blank", False, retired=True)
    check("4.8.2: no bridge skin rewrite; skins are drawn only by the LM client",
          "bridgeRewrite(" not in p62_reg.replace("({@code bridgeRewrite})", "")
          and "bridgeSkin(" not in p62_gui and "Motif.CLOUDS);" in p62_reg)
    check("Menu skins picker: hub entry, five rows, instant apply, backgrounds and Full/Lite toggles",
          'new HubTile("menu_skins", "Menu skins"' in p62_hub and 'case "menu_skins" -> "page:" + PAGE_SKINS;' in p62_hub
          and 'spec.lockedButtonRight("You are using this");' in p62_hub
          and "UltraHubScreens.applySkinAction(viewer, parts.action, stringArg(args, \"arg\"))" in p62_act
          and 'UltraStyle.toggle("Backgrounds"' in p62_hub and '"Look - "' in p62_hub)
    check("themed backgrounds baked once per skin and size; panel shadow baked; Lite flattens and strips",
          "BG_MAX = 960;" in p62_bake and "MenuSkins.backgroundPixel(skin, (x + 0.5) / tw, v, lite)" in p62_bake
          and "int ambient = lite ? 0 : SaiyanRoyal.AMBIENT_RINGS;" in p62_bake
          and "MenuBackground.panelShadow(g, panelW, panelH, r, skin, lite, alpha);" in p62_screen
          and "if (lite && offScreen(b.y(), b.h())) {" in p62_screen
          and "SHAPES.computeIfAbsent(new Shape(" in p62_screen)
    check("polish uses transform/opacity only: page crossfade and slide, hover lift, tab slide, bar fill, shimmer",
          "previous.drawMenu(g, Integer.MIN_VALUE / 2" in p62_screen and "RenderSystem.setShaderColor(1f, 1f, 1f, alpha);" in p62_screen
          and "private float liftOf(" in p62_screen and "private void drawTabIndicator(" in p62_screen
          and "BAR_SHOWN.put(barKey(index), share);" in p62_screen and "private void shimmer(" in p62_screen
          and "MenuLook.fadingFrom()" in p62_screen and "private void drawFocus(" in p62_screen)
    check("fps sag suggests Lite once, never switches",
          "FPS_FLOOR = 45.0;" in p62_screen and "MenuLook.markLiteSuggested();" in p62_screen
          and "liteSuggested = false;" in p62_look)
    check("layout dump names skin, background and look for the rig",
          '"SKIN %s accent=%06X bg=%s look=%s dark=%s baked=%d"' in p62_screen and '"swatch:" + swatch.id()' in p62_screen)
    print("\n--- § lm 4.7.61 ---")
    p61_screen = read(SRC / "client/NativeMenuScreen.java")
    p61_editor = read(SRC / "config/editor/ConfigEditor.java")
    p61_diff = read(SRC / "gui/ultra/UltraDifficultyScreens.java")
    check("manifest row for lm 4.7.61", "lm-4761-ui-polish" in manifest)
    check("native footer: an edge footer pins into the bottom band, Close shares its row",
          "static int[] pinFooter(int x, int y, int w, int h, int panelW, int panelH)" in p61_screen
          and 'if (bool(b, "footer", false)) {' in p61_screen
          and "y = panelH - CLOSE_BAND + 6;" in p61_screen
          and "return new int[] {EDGE, b.y(), b.h()};" in p61_screen)
    check("native field hint is cut to the field; layout dump carries hint width and room",
          "private String fieldHint(Field f)" in p61_screen and "box.m_257771_(Component.m_237113_(fieldHint(f)));" in p61_screen
          and "hintw=%d room=%d" in p61_screen)
    check("native live button never draws the dark-gray locked ink",
          "label.charAt(1) == '8'" in p61_screen and '!"dim".equalsIgnoreCase(b.style())' in p61_screen)
    check("config editor shows number lists as text and edits them",
          "public static String valueText(Object value)" in p61_editor
          and "String.valueOf(located.value)" not in p61_editor
          and "rawClass.isArray() && rawClass.getComponentType().isPrimitive()" in p61_editor
          and '"numbers separated by commas, like 60, 300, 900"' in p61_editor)
    p61_look = read(SRC / "client/NativeFormLook.java")
    p61_model = read(SRC / "client/NativeModelPreview.java")
    p61_parts = read(SRC / "client/BodyPartPreview.java")
    p61_proto = read(SRC / "net/gui/LmNativeProtocol.java")
    p61_netw = read(SRC / "net/gui/LmNativeNetwork.java")
    p61_plugin = read(SRC / "mixin/LegacyMechanicsMixinPlugin.java")
    p61_cmix = read(SRC.parent.parent.parent.parent / "resources/legacymechanics.client.mixins.json")
    p61_mf = read(SRC.parent.parent.parent.parent / "resources/META-INF/MANIFEST.MF")
    check("Preview forms native: stand-in wears the named race, form and stack form",
          "ch.setRace(race);" in p61_look and "ch.setActiveForm(group, name);" in p61_look
          and "ch.setActiveStackForm(stackGroup, stackForm);" in p61_look
          and "data.getStatus().setHasCreatedCharacter(true);" in p61_look
          and p61_model.count("NativeFormLook.apply(") == 2
          and "BodyPartPreview.begin(entity, parts);" in p61_model and "BodyPartPreview.end();" in p61_model)
    check("Preview forms native: body parts merge into DMZUltra 0.5.7 records by component, preview only",
          "static Object rebuild(Object record, java.util.Map<String, Object> swap)" in p61_parts
          and "getRecordComponents()" in p61_parts and "public static boolean previewActive()" in p61_parts
          and '"FormPartsPreviewMixin"' in p61_cmix and '"RaceTailSuppressMixin"' in p61_cmix
          and '"client": [' in p61_cmix and "legacymechanics.client.mixins.json" in p61_mf
          and 'endsWith(".client.mixin.FormPartsPreviewMixin")' in p61_plugin
          and "return !dedicatedServer() && dmzRevampClassPresent(\"com/dmzultra/client/forms/FormParts.class\");" in p61_plugin)
    check("Preview forms native: hello adds combined.forms.v1 / bodyParts only when installed",
          "public static String clientFeaturesJson(boolean forms, boolean bodyParts)" in p61_proto
          and "LmNativeProtocol.clientFeaturesJson(LmNativeProtocol.clientDrawsForms()," in p61_netw
          and "LmNativeProtocol.clientDrawsBodyParts()" in p61_netw)
    check("Difficulty guide rows read Label: value on one line",
          '.labelValue("Stronger enemies:", "mobs near you match your tier")' in p61_diff
          and '.labelValue("Bigger rewards:", "more TP per kill, plus Momentum")' in p61_diff
          and '.labelValue("Change anytime:", "lowering your tier is free")' in p61_diff
          and 'spec.labelValue("Tier:", ' in p61_diff)
    print("\n--- § lm 4.7.60 ---")
    p60_skin = read(SRC / "gui/ultra/SaiyanRoyal.java")
    p60_style = read(SRC / "gui/ultra/UltraStyle.java")
    p60_screen = read(SRC / "client/NativeMenuScreen.java")
    p60_net = read(SRC / "net/gui/LmGuiNetwork.java")
    # 4.7.62 moved the palette and every native tone into MenuSkins; the 4.7.60 pins follow it there.
    p60_reg = read(SRC / "gui/ultra/MenuSkins.java")
    p60_bake = read(SRC / "client/MenuBackground.java")
    check("manifest row for lm 4.7.60", "lm-4760-saiyan-royal" in manifest)
    check("Saiyan Royal palette defined once with the owner's hex values",
          "0x160B0D, 0xE23B3B, 0xF0B429, 0xF6E8E4, 0x3FAF55" in p60_reg
          and "PANEL = MenuSkins.SAIYAN_ROYAL.panel();" in p60_skin
          and 'NAME = "saiyan_royal";' in p60_skin)
    check("skin on every menu natively; system accents keep their hue; bridge keeps the 4.7.57 letters",
          "return screenId != null;" in p60_skin and "case 'c' -> 0xE23B3B;" in p60_reg
          and "case '3' -> 0x00AAAA;" in p60_reg and "case 'f' -> 0xFFFFFF;" in p60_reg
          and "return MenuSkins.systemAccentHex(code);" in p60_skin
          and "BRIDGE_ACCENT = 'c';" in p60_skin
          and "BRIDGE_HEADER = '6';" in p60_skin
          and 'case "difficulty" -> String.valueOf(BRIDGE_ACCENT);' in p60_skin
          and 'case "rival" -> "f";' in p60_skin and 'case "spar", "dojo" -> "3";' in p60_skin
          and 'case "prestige" -> "b";' in p60_skin and 'case "character" -> "a";' in p60_skin
          and "BRIDGE_LABEL = '7';" in p60_skin and "BRIDGE_VALUE = 'f';" in p60_skin)
    # Menu builders read the skin: one table for chrome letters and system accents, bytes pinned literally.
    check("menu builders take chrome and accents from SaiyanRoyal; self-test pins the 4.7.57 bytes",
          # 4.8.2: bridgeAccent renamed wireAccent (the letters are the wire format; the bridge is gone).
          "return SaiyanRoyal.wireAccent(screenId);" in p60_style
          and "HEADER = MARK + SaiyanRoyal.BRIDGE_HEADER + BOLD;" in p60_style
          and "LABEL = MARK + SaiyanRoyal.BRIDGE_LABEL;" in p60_style
          and "VALUE = MARK + SaiyanRoyal.BRIDGE_VALUE;" in p60_style
          and 'ACCENT_RIVAL = MARK + SaiyanRoyal.wireAccent("rival");' in p60_style
          and '!UltraStyle.HEADER.equals(m + "6" + m + "l")' in p60_skin)
    check("native renderer: rounded panel with shadow and gold border, rounded buttons; clicks keep the rectangle",
          "PANEL_BG" not in p60_screen
          and "drawSkinButton(g, b, hover, down && !inert, inert, style, isActiveTab(b), dt);" in p60_screen
          and "return sys >= 0 ? sys : MenuSkins.paletteRgb(palette, skin, skin.defaultAccent());" in p60_screen
          and "private void drawPanel(GuiGraphics g, float alpha)" in p60_screen
          and "SaiyanRoyal.AMBIENT_RINGS" in p60_bake and "static void fillRounded(" in p60_screen
          and "fillRounded(g, bx, by, w, h, r, edge);" in p60_screen
          and "return x >= b.x() && x < b.x() + b.w() && y >= b.y() && y < b.y() + b.h();" in p60_screen)
    check("depth: layered panel shadow, gradient, raised buttons sink 2px, inset boxes, glossy bars, tab glow",
          "PANEL_RADIUS = 14;" in p60_skin and "PRESS_SINK = 2;" in p60_skin
          and "SaiyanRoyal.KEY_RINGS" in p60_bake and "skin.panelTop()" in p60_screen
          and "pressed ? SaiyanRoyal.PRESS_SINK" in p60_screen and "-SaiyanRoyal.TAB_LIFT" in p60_screen
          and "skin.edgeHighlight()" in p60_screen and "skin.edgeShade()" in p60_screen
          and "static void insetBox(" in p60_screen and "private void drawInfoBox(" in p60_screen
          and "glossy(g, x + 1, ty + 1, fw, h - 2" in p60_screen
          and "glow(g, at[0], at[1], at[2], at[3], r, skin.glow()" in p60_screen
          and "buttonOffset(true, false) != 2" in p60_skin
          # Labels inked in the button's own accent draw in the skin text (the accent is on the edge).
          and "MenuSkins.systemAccentHex(label.charAt(1)) == buttonTint(b)" in p60_screen)
    # tools/lm-ui-probe/analyze_look.py reads these fields; keep the dump and the analyzer in step.
    check("native layout dump carries button face/ink/cut and body ink/background for the rig's look check",
          'face=%06X ink=%06X cut=%s' in p60_screen and '"I %d ink=%06X bg=%06X"' in p60_screen
          and "private int[] faceAndEdge(" in p60_screen)
    check("skin self-test pins the palette, the accent mapping and the shape math at boot",
          "SaiyanRoyal.selfTest()" in p60_net and "public static List<String> selfTest()" in p60_skin)

    print("\n--- § lm 4.7.59 ---")
    p59_mom = read(SRC / "progression/Momentum.java")
    p59_trial = read(SRC / "service/DifficultyTrial.java")
    p59_guide = read(SRC / "gui/ultra/DifficultyGuide.java")
    p59_diff = read(SRC / "gui/ultra/UltraDifficultyScreens.java")
    p59_cfg = read(SRC / "config/DifficultyConfig.java")
    p59_events = read(SRC / "event/DifficultyEvents.java")
    p59_hit = read(SRC / "mixin/StatsEventsHitTakenMasteryMixin.java")
    p59_pot = read(SRC / "mixin/PotionEffectHelperHitTakenMasteryMixin.java")
    p59_char = read(SRC / "progression/shop/PrestigeCharacter.java")
    p59_act = read(SRC / "service/DifficultyActions.java")
    check("manifest row for lm 4.7.59", "lm-4759-momentum-guide" in manifest)
    check("Momentum config ships with the brief's defaults",
          "public boolean momentumEnabled = true;" in p59_cfg and "public int momentumSecondsPerKill = 10;" in p59_cfg
          and "public int momentumMaxBankSeconds = 300;" in p59_cfg
          and "public double momentumMasteryMultiplier = 1.5;" in p59_cfg)
    check("Momentum banks on difficulty mob kills only, never chamber wave foes",
          "return hostile && difficultyScaled && !chamberWaveFoe;" in p59_mom
          and "MobScaling.isAdPainted(mob)" in p59_mom and "ChamberKillMastery.WAVE_FOE" in p59_mom
          and "Momentum.onDeath(event);" in p59_events)
    check("Momentum multiplies only the attacker's hit mastery on difficulty mobs",
          "Momentum.begin(event);" in p59_hit and "Momentum.end();" in p59_hit
          and "Momentum.scale(entity, scaled);" in p59_pot
          and "!qualifies(event.getEntity())" in p59_mom)
    check("Momentum is per character and session-only (logout, slot switch)",
          "Momentum.clear(player);" in p59_events and "Momentum.clear(player);" in p59_char
          and "PrestigeCharacter.id(player)" in p59_mom)
    check("free trial: 5 minutes, one per tier per character, reverts to the previous tier",
          "TRIAL_MILLIS = 5L * 60L * 1000L;" in p59_trial
          and "return currentActive == trialTier ? Math.max(0, prev) : currentActive;" in p59_trial
          and "LmPlayerStore.root(PrestigeCharacter.id(player))" in p59_trial
          and "DifficultyTrial.pulse(server)" in p59_events and "case ACT_TRIAL ->" in p59_act)
    check("recommended tier: highest met, Tier 1 with no tier",
          "if (activeTier <= 0) {\n            return 1;" in p59_guide.replace("\r\n", "\n")
          and "DifficultyGuide.recommend(active, highestEligible)" in p59_diff)
    check("guide flow: 3 steps with dots, Back in the left slot, detail page with before/after and trial",
          'stepHeader(spec, 1, "What is Difficulty")' in p59_diff and 'stepHeader(spec, 2, "Pick your level")' in p59_diff
          and 'stepHeader(spec, 3, "Your tier")' in p59_diff and 'spec.footerGuide("nav:hub", "main menu", GUIDE_DONE);' in p59_diff
          # 4.7.84 (D-3): one card, "Enemies:" and "TP per kill:" (the mastery row went); "Summon - Locked".
          and '"Try one fight free"' in p59_diff and 'labelValue("Enemies:"' in p59_diff
          and 'labelValue("TP per kill:"' in p59_diff
          and 'spec.lockedButton("Summon - Locked")' in p59_diff)

    print("\n--- § lm 4.7.57 ---")
    p57_diff = read(SRC / "gui/ultra/UltraDifficultyScreens.java")
    p57_style = read(SRC / "gui/ultra/UltraStyle.java")
    p57_spec = read(SRC / "gui/ultra/UltraScreenSpec.java")
    p57_act = read(SRC / "gui/ultra/UltraGuiActions.java")
    p57_dact = read(SRC / "service/DifficultyActions.java")
    check("manifest row for lm 4.7.57", "lm-4757-menu-regressions" in manifest)
    check("locked summons show a dim, inert Summon locked button (LM54-C4)",
          p57_diff.count('spec.lockedButton("Summon - Locked").rowGap();') == 2
          and "Only a summon that can succeed gets a button" not in p57_diff
          and "Do the steps above, then come back to summon." in p57_diff
          and 'public static final String NO_ACTION = "nav:none";' in p57_act
          and "if (NO_ACTION.equals(action)) {" in p57_act
          and "button(UltraGuiActions.NO_ACTION, UltraStyle.dim(" in p57_spec)
    check("roomier menus: 426 wide, 6px gaps and pads, 16px buttons, 2px line gap",
          "MENU_W = 426;" in p57_style and "MENU_GAP = 6;" in p57_style and "MENU_BODY_PAD = 6;" in p57_style
          and "MENU_END_PAD = 6;" in p57_style and "MENU_BTN_H = 16;" in p57_style
          and "MENU_LINE_GAP = 2;" in p57_style)
    check("Tiers list: one button per tier, every tier opens its detail page, no per-tier lines",
          'String action = "page:tier_ask:" + t;' in p57_diff
          and 'spec.labelValue("T" + t + " " + ut.display, view.line());' not in p57_diff
          and "Only tiers you can" not in p57_diff and "tappable.add(t)" not in p57_diff
          # 4.7.59: locked caption names the tier (T5 Master - Locked).
          and 'UltraStyle.dim(name + " - Locked")' in p57_diff)
    check("tier detail page covers every tier; switch down confirms with Switch to T#",
          '"Switch to T" + t' in p57_diff and '"Unlocks at:"' in p57_diff
          and "Lower tiers are free - pick T" not in p57_diff
          and "diff-tier:" not in p57_dact and "Click again within 10 seconds to confirm" not in p57_dact[
              p57_dact.find("private static Result setTier"):p57_dact.find("private static Result lowerTier")])

    print("\n--- § lm 4.7.56 ---")
    g = lambda rel: read(SRC / rel)
    p56_pres = g("gui/ultra/UltraPrestigeScreens.java")
    p56_psys = g("progression/shop/PrestigeSystem.java")
    p56_diff = g("gui/ultra/UltraDifficultyScreens.java")
    p56_act = g("service/DifficultyActions.java")
    p56_rival = g("gui/ultra/UltraRivalScreens.java")
    p56_rapi = g("gui/RivalGuiApi.java")
    p56_spar = g("gui/ultra/UltraSparScreens.java")
    p56_char = g("gui/ultra/UltraCharacterScreens.java")
    p56_cfg = g("gui/ultra/UltraConfigScreens.java")
    p56_ed = g("config/editor/ConfigEditor.java")
    p56_coin = g("currency/AncientCoinEconomy.java")
    p56_pps = g("progression/shop/PrestigePointsSystem.java")
    p56_chal = g("rival/RivalChallengeManager.java")
    p56_cmd = g("command/DifficultyCommands.java")
    p56_chat = g("gui/DifficultyChatMenu.java")
    check("manifest row for lm 4.7.56", "lm-4756-gui-humanize" in manifest)
    check("B1/B3 prestige confirm names skills, techniques and quests; chat says level, not power",
          p56_pres.count("Prestiging resets your stats, skills, techniques and quest progress") == 1
          and "This resets your stats, skills, techniques and quest progress" in p56_psys
          and " power for the next one" not in p56_psys and " power earns your next prestige" not in p56_psys)
    check("A6 replace confirm button names the demotion",
          '"Demote " + dropPlain + " and accept " + pendingPlain' in p56_rival and 'UltraStyle.danger("Continue")' not in p56_rival)
    check("C2 Edit prestige shop opens the Prestige shop editor category",
          'addTab(tabs, "prestige-shop", "Prestige shop");' in p56_ed and '{"prestige-shop", "Prestige shop"}' in p56_cfg)
    check("C3 clear-tier / unequip-title ask pages act on the first Confirm",
          '!"clear_tier_ask".equals(page) && !GuiClickConfirm.confirmed' in p56_act
          and '!"title_unequip_ask".equals(page) && !GuiClickConfirm.confirmed' in p56_act
          and "Clear your active tier? You will need Ancient Coins to activate a tier again." in p56_diff
          and "Team mode resets to Personal" not in p56_diff)
    check("C1 tier rows show the reason only (no PurchaseState enum names)",
          "state.name() + \" - \"" not in g("gui/ultra/PurchaseState.java"))
    check("B2/C13 one favor sentence; coin shortfall without pay-up jargon",
          "Not enough favor: this costs " in p56_pps and "favor (have " not in p56_pres
          and "pay-up OK" not in p56_coin and "Overpaying is fine; change is returned." in p56_coin)
    check("LM-P1-12 chat settings menu retired: /difficulty admin settings opens the Ultra editor",
          "DifficultyMenu.open(player, page);" in p56_chat and "DifficultyChatMenu.open" not in p56_cmd)
    # C4 was reworked in 4.7.57: the locked summon button is back, dim and inert (see lm 4.7.57).
    check("C7/C9 Flush and Difficulty off ask first; C4 no Summon locked button that opens the ask page",
          '"page:flush_ask"' in p56_diff and '"page:difficulty_off_ask"' in p56_diff
          and 'UltraStyle.dim("Summon locked")' not in p56_diff and "Select End Dragon first" not in p56_diff)
    check("C8 Discard all / Reload disk confirm before dropping unsaved changes",
          '"config-discard-all"' in p56_cfg and '"config-reload:" + module' in p56_cfg and "change(s)" not in p56_cfg)
    check("B6/B13 free changes say Confirm; Clear parts and Race default ask first",
          'UltraStyle.confirm("Confirm & pay")' not in p56_char and '"page:clear_ask:bones:bones:" + pg' in p56_char
          and '"page:clear_ask:body:" + self' in p56_char)
    check("A3 spar confirm buttons name the action; no 'Confirm action' fallback",
          "confirmVerb(action, plain)" in p56_spar and '"Confirm action"' not in p56_spar)
    check("A4 bare challenge cancel names the request instead of dropping it",
          "a bare cancel names the request instead of dropping the first one" in p56_chal)
    check("A5 no exclamation marks on challenge chat",
          "Too far apart!" not in p56_chal and '"Victory!"' not in p56_chal and "Challenge accepted!" not in p56_chal)
    check("A9 full words: no Nem/Mut abbreviations, no W/L/D, no '?' markers",
          '"Nem"' not in p56_rival and "W/L/D" not in p56_rapi and '"? offline"' not in p56_rapi)
    check("item 52 pickers 20 a page: android, race, class",
          # 4.7.84 (C-3): all 24 races on one page.
          "ANDROID_PICK_PER_PAGE = 20;" in p56_char and "RACES_PER_PAGE = 24;" in p56_char and "CLASSES_PER_PAGE = 20;" in p56_char)
    check("C17 staff command text: no raw exception text, no JSON path",
          '"Invalid value: " + e.getMessage()' not in p56_cmd and "config/adaptivedifficulty.json" not in p56_cmd)

    print("\n--- § lm 4.7.55 ---")
    p55_spec = read(SRC / "gui/ultra/UltraScreenSpec.java")
    p55_style = read(SRC / "gui/ultra/UltraStyle.java")
    p55_prev = read(SRC / "gui/ultra/ModelPreview.java")
    p55_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    p55_char = read(SRC / "gui/ultra/UltraCharacterScreens.java")
    p55_cfg = read(SRC / "gui/ultra/UltraConfigScreens.java")
    p55_doc = read(ROOT / "docs" / "KAIPATH_GUI_PROTOCOL.md")
    p55_menu = _after(p55_spec, "private GuiOpenPacket toMenuPacket() {").split("\n    }\n", 1)[0]
    p55_fit = _after(p55_spec, "public UltraScreenSpec fitMenu() {").split("\n    }\n", 1)[0]
    p55_home = _after(p55_char, "private static void openHome(ServerPlayer player) {").split("\n    }\n", 1)[0]
    p55_forms = _after(p55_char, "private static void openFormPreview(ServerPlayer player, int index) {")
    p55_looks = _after(p55_char, "private static void openLooks(ServerPlayer player) {").split("\n    }\n", 1)[0]
    check("manifest row for lm 4.7.55", "lm-4755-centered-menus" in manifest)
    # 4.7.57 widened MENU_W from PANEL_W (350) to 426.
    check("menu sizes: 426 (also with a model), never over 460 (1080p at GUI 4); no design box",
          "MENU_W = 426;" in p55_style and "MENU_MODEL_W = 426;" in p55_style
          and "MENU_MAX_W = 480 - 2 * PANEL_MARGIN;" in p55_style and "MENU_SMALL_H = 270 - 2 * PANEL_MARGIN;" in p55_style
          and "MENU_MODEL_BOTTOM = 195;" in p55_style and "MENU_DESIGN_W" not in p55_style
          and "MENU_DESIGN" not in p55_prev and "MENU_DESIGN" not in p55_spec and "MENU_PANEL_W" not in p55_spec)
    check("fitMenu: centered with the standard margin, no fillScreen",
          "this.fillScreen = false;" in p55_fit and "this.centerPanel = true;" in p55_fit
          and "this.panelMargin = UltraStyle.PANEL_MARGIN;" in p55_fit)
    check("menu packet: centered, content-sized, body at full height, footer row not pinned",
          'layout.addProperty("center", true);' in p55_menu and 'layout.addProperty("margin", UltraStyle.PANEL_MARGIN);' in p55_menu
          and '"fillScreen"' not in p55_menu and '"designWidth"' not in p55_menu
          and 'layout.addProperty("scrollBody", false);' in p55_menu
          and "bottom + UltraStyle.MENU_END_PAD + UltraStyle.MENU_CLOSE_BAND" in p55_menu
          and "menuFooterButton(b, x, footerY, fw, bh)" in p55_menu
          and "int footerY = bottom + UltraStyle.MENU_FOOTER_GAP;" in p55_menu
          and "false, b.sound, b.buttonSkin, b.icon, b.raw);" in _after(p55_spec, "private Button menuFooterButton(")[:400])
    check("rows stay left of the pinned model; model column ends by MENU_MODEL_BOTTOM",
          "int right = model == null ? boxW - edge : model.x() - 8;" in p55_menu and "rowFx" not in p55_menu
          and "UltraStyle.MENU_MODEL_BOTTOM - 46 - UltraStyle.MENU_GAP - UltraStyle.MENU_BTN_H" in p55_prev)
    check("spin toggle joins the left rows on a page that scrolls at GUI 4 (never behind the pinned model)",
          "+ UltraStyle.MENU_CLOSE_BAND > UltraStyle.MENU_SMALL_H;" in p55_menu and "int ty = scrolls ? y : modelBottom + gap;" in p55_menu
          and "it would pass behind the model" in p55_test)
    check("staff config editor keeps its fillScreen box",
          ".fillScreen(CONFIG_DESIGN_W, CONFIG_DESIGN_H)" in p55_cfg and ".fitMenu()" not in p55_cfg
          and 'layout.addProperty("fillScreen", true);' in p55_spec)
    check("boot self-test asserts centered, content-sized, unpinned menus and the staff-only form preview",
          "verifyMenuFit();" in p55_test and "must not send" in p55_test
          and "menus scroll as one page" in p55_test and "is not sized to its content" in p55_test
          and "UltraStyle.MENU_SMALL_H" in p55_test and "UltraCharacterScreens.staffMayPreviewForms(null)" in p55_test
          and "ModelPreview.MENU_STANDARD" in p55_test and "UltraStyle.MENU_DESIGN" not in p55_test)
    check("Preview forms is staff-only: no line or button for players, the page refuses them",
          "static boolean staffMayPreviewForms(ServerPlayer fallback) {\n        return StaffAccess.isStaff(actingViewer(fallback));" in p55_char
          # 4.7.84 (C-2): Preview forms lives on the Looks tab.
          and "boolean formsShown = staffMayPreviewForms(player);" in p55_looks
          and p55_looks.count("if (formsShown) {") == 2
          and p55_forms.index("if (!staffMayPreviewForms(player)) {") < p55_forms.index("ModelPreview.raceForms(player)")
          and "Preview forms is a staff tool." in p55_forms)
    check("protocol doc: centered menus; fillScreen only for the config editor; wire unchanged",
          "#### Centered menus (LM 4.7.55)" in p55_doc and "**without**\n  `footer:true`" in p55_doc
          and "Only the staff config editor sets `fillScreen`" in p55_doc and 'protocol `"1"`' in p55_doc)
    p55_spar = read(SRC / "gui/ultra/UltraSparScreens.java")
    p55_rival = read(SRC / "gui/ultra/UltraRivalScreens.java")
    # 4.7.84 (DJ-7, X5): 8 names a dojo picker page, 10 a rival picker page, so pickers fit the height budget.
    check("name pickers: 8 / 10 names a page (dojo online picker and rival picker), still paged",
          "private static final int NAMES_PER_PAGE = 8;" in p55_spar
          and "UltraPickHelper.pageSlice(names, NAMES_PER_PAGE, pg)" in p55_spar
          and "UltraPickHelper.placePrevNext(spec, page, pg, pages);" in p55_spar
          and "private static final int PICK_PER_PAGE = 8;" in p55_rival
          and "UltraPickHelper.pageCount(total, PICK_PER_PAGE)" in p55_rival
          and "UltraPickHelper.pageLabel(spec, pg, pages);" in p55_rival)

    print("\n--- § lm 4.7.54 ---")
    p54_pick = read(SRC / "event/DragonBallRadarPickup.java")
    p54_hold = read(SRC / "event/DragonBallSetHold.java")
    p54_hold_set = p54_hold.split("public static String setOf(", 1)[0]
    p54_resp = read(SRC / "event/DragonBallRespawn.java")
    check("manifest row for lm 4.7.54", "lm-4754-biradar-demon" in manifest)
    check("Bi-Dimensional Radar takes Demon Realm balls (DMZ has no demon radar); exact-match radars unchanged",
          'return definition.supportsBallSet(set)\n                || (FUSED_RADAR.equals(definition.getId()) && ("earth".equals(set) || "namek".equals(set) || "demon".equals(set)));' in p54_pick)
    check("demon ball hit without the biradar names only the Bi-Dimensional Radar; set called Demon Realm",
          'String radar = "demon".equals(setId) ? "the Bi-Dimensional Radar"' in p54_hold
          and 'if ("demon".equals(setId)) {\n            return "Demon Realm";' in p54_hold)
    check("one set per player and the respawn stay set-generic (no earth/namek literal on the demon path)",
          "DragonBallDefinitions.getBallSet(setId)" in p54_hold_set
          and '"earth"' not in p54_hold_set and '"namek"' not in p54_hold_set
          and '"earth"' not in p54_resp and '"namek"' not in p54_resp)

    print("\n--- § lm 4.7.53 ---")
    p53_spirit = read(SRC / "progression/race/SpiritualistKiControl.java")
    p53_yard = read(SRC / "progression/race/YardratProgression.java")
    check("manifest row for lm 4.7.53", "lm-4753-ki-control-per-character" in manifest)
    check("Spiritualist Ki Control mark is per character; only that character can lose it",
          'KEY_GRANTED_PREFIX = KEY_GRANTED + ":";' in p53_spirit
          and "return KEY_GRANTED_PREFIX + PrestigeCharacter.id(player);" in p53_spirit
          and "ProgressionData.storedPutBool(player, grantedKey(player), true);" in p53_spirit
          and "String key = grantedKey(player);\n        if (!ProgressionData.storedGetBool(player, key)) {" in p53_spirit
          and "ProgressionData.storedRemove(player, key);" in p53_spirit)
    check("old account mark never strips: a confirmed Spiritualist with Ki Control adopts it, then it is dropped",
          "adoptAccountMark(player, skills);\n                if (grant(player, skills)) {" in p53_spirit
          and "ProgressionData.storedRemove(player, KEY_GRANTED);" in p53_spirit
          and p53_spirit.count("storedGetBool(player, KEY_GRANTED)") == 1)
    check("Spiritualist grant unchanged: 10 s confirm, level 1",
          "CLASS_CONFIRM_MS = 10_000L;" in p53_spirit and "DmzSkillUtil.setLevel(skills, KI_CONTROL, 1);" in p53_spirit)
    check("Yardrat starting Ki skills mark is per character; grant levels stay 5",
          'return "yardrat_starting_ki_skills_granted:" + PrestigeCharacter.id(player);' in p53_yard
          and p53_yard.count("grantedKey(player)") == 3 and '"yardrat_starting_ki_skills_granted"' not in p53_yard
          and "DmzSkillUtil.setLevel(skills, SKILL_ONE, 5);" in p53_yard and "DmzSkillUtil.setLevel(skills, SKILL_TWO, 5);" in p53_yard)

    print("\n--- § lm 4.7.52 ---")
    p52_spec = read(SRC / "gui/ultra/UltraScreenSpec.java")
    p52_style = read(SRC / "gui/ultra/UltraStyle.java")
    p52_prev = read(SRC / "gui/ultra/ModelPreview.java")
    p52_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    p52_doc = read(ROOT / "docs" / "KAIPATH_GUI_PROTOCOL.md")
    p52_builders = {n: read(SRC / ("gui/ultra/Ultra%sScreens.java" % n)) for n in (
        "Hub", "Difficulty", "Rival", "Spar", "Prestige", "Character", "Progression", "SkillCheck", "WeightShop")}
    check("manifest row for lm 4.7.52", "lm-4752-menu-fit" in manifest)
    check("menu design box is 426x240 (16:9), footer band and content bottom derived from it",
          "MENU_DESIGN_W = 426;" in p52_style and "MENU_DESIGN_H = 240;" in p52_style
          and "MENU_FOOTER_Y = MENU_DESIGN_H - (MENU_CLOSE_BAND + MENU_BTN_H);" in p52_style
          and "MENU_CONTENT_BOTTOM = MENU_FOOTER_Y - MENU_GAP;" in p52_style, retired=True)  # 4.7.55 replaced the fillScreen menu box (lm-4755-centered-menus)
    check("fitMenu: fillScreen over the menu box, locked, not centered, margin 0; packet asks 1920x1080",
          "public UltraScreenSpec fitMenu()" in p52_spec and "this.designWidth = UltraStyle.MENU_DESIGN_W;" in p52_spec
          and "this.centerPanel = false;" in p52_spec and "this.panelMargin = 0;" in p52_spec
          and "static final int MENU_PANEL_W = 1920;" in p52_spec and "static final int MENU_PANEL_H = 1080;" in p52_spec
          and "if (menuFit) {\n            return toMenuPacket();" in p52_spec, retired=True)  # 4.7.55 replaced the fillScreen menu box (lm-4755-centered-menus)
    check("every menu builder frame calls fitMenu (config editor keeps its own box)",
          all(b.count(".fitMenu()") >= (1 if n in ("SkillCheck", "WeightShop") else 2) for n, b in p52_builders.items())
          and ".fitMenu()" not in read(SRC / "gui/ultra/UltraConfigScreens.java"))
    check("reflow: rows packed under the body, regrouped into 2-4 columns of at least 80px when they do not fit",
          "for (int cols = 2; cols <= Math.min(4, maxCols); cols++)" in p52_spec
          and "(UltraStyle.BTN_MIN_W + gap)" in p52_spec and "Widening pushed two controls together" in p52_spec, retired=True)  # 4.7.55 replaced the fillScreen menu box (lm-4755-centered-menus)
    check("model boxes for the menu; scales fit against the box KaiPath draws; uniform-scale note",
          "MENU_STANDARD = new Box(" in p52_prev and "MENU_FORMS = new Box(" in p52_prev
          and "public static Box menuBox(Box legacy)" in p52_prev and "This assumes the box is scaled the same in" in p52_prev)
    check("boot self-test: every builder and the densest page fit the box, template from UltraStyle",
          "verifyMenuFit();" in p52_test and "public static void verifyMenuFit()" in p52_test
          and "UltraStyle.MENU_DESIGN_W" in p52_test and "GuiOpenPacket.LAYOUT_JSON_MAX" in p52_test
          and "ModelPreview.MENU_STANDARD" in p52_test and "UltraConfigScreens.CONFIG_DESIGN_W" in p52_test, retired=True)  # 4.7.55 replaced the fillScreen menu box (lm-4755-centered-menus)
    check("protocol doc: menu design box and the 16:9 rule; wire unchanged",
          "every LM menu sets `fillScreen`" in p52_doc and "**The box is 16:9.**" in p52_doc
          and "protocol `\"1\"`" in p52_doc, retired=True)  # 4.7.55 replaced the fillScreen menu box (lm-4755-centered-menus)

    print("\n--- § lm 4.7.51 ---")
    import json as _json51
    p51_cat = read(SRC / "gui/ultra/BodyPartCatalog.java")
    p51_test = read(SRC / "gui/ultra/BodyPartCatalogSelfTest.java")
    p51_build = read(MOD / "build.sh")
    p51_057 = _json51.loads(read(SRC.parents[3] / "resources" / "dmzultra-form-parts-0.5.7.json"))
    p51_053 = _json51.loads(read(SRC.parents[3] / "resources" / "dmzultra-form-parts-0.5.3.json"))
    p51_gt4 = ["saiyan/oozaru/" + f for f in ("supersaiyan4", "supersaiyan4fp", "supersaiyan4lb", "supersaiyan5")]
    check("manifest row for lm 4.7.51", "lm-4751-ultra-057-catalog" in manifest)
    check("0.5.7 catalog: generated from stock 0.5.7, 115 / 406 / 404, same shop as 0.5.3",
          p51_057.get("ultraVersion") == "0.5.7" and len(p51_057["pieces"]) == 115
          and len(p51_057["forms"]) == 406 and len(p51_057["designs"]) == 404
          and p51_057["pieces"] == p51_053["pieces"])
    check("0.5.7 catalog drops exactly the 4 GT SSJ4 Oozaru forms; no gt4_* piece anywhere",
          sorted(set(p51_053["forms"]) - set(p51_057["forms"])) == sorted(p51_gt4)
          and not set(p51_057["forms"]) - set(p51_053["forms"])
          and all(p51_053["forms"][k] == v for k, v in p51_057["forms"].items())
          and not any(p.startswith("gt4_") for v in p51_057["forms"].values() for p in v))
    check("0.5.7 catalog shipped and selected for Ultra 0.5.7",
          'SHIPPED = List.of("0.4.0", "0.5.3", "0.5.7")' in p51_cat
          and 'new Expect("0.5.7", 115, 406, 404, true, true, CLIENT_MESHES)' in p51_test
          and 'selects(fails, "0.5.7", "0.5.7", "shipped")' in p51_test
          and 'selects(fails, "0.6.0", "0.5.7", "shipped")' in p51_test
          and '-C "$RES" dmzultra-form-parts-0.5.7.json' in p51_build)
    check("generated-catalog gate unchanged (minimums are the newest shipped catalog's)",
          "BodyPartCatalogSelfTest.check(generated, BodyPartCatalogSelfTest.Expect.newerThan(fallback))" in p51_cat
          and "base.forms().size(), base.designs().size(), false," in p51_test)

    print("\n--- § lm 4.7.50 ---")
    p50_mx = read(SRC / "mixin/BioAndroidDrainTpMixin.java")
    p50_json = read(MOD / "src/main/resources/legacymechanics.mixins.json")
    p50_build = read(MOD / "build.sh")
    check("manifest row for lm 4.7.50", "lm-4750-bio-drain-no-tp" in manifest)
    check("Bio Android drain-kill TP: grantDrainTp cancelled at HEAD by its full descriptor, required",
          '@Mixin(targets = "com.dragonminez.common.racial.impl.BioAndroidEvolution", remap = false)' in p50_mx
          and '"grantDrainTp"' in p50_mx
          and "Lcom/dragonminez/common/config/GeneralServerConfig$BioAndroidRacialConfig;)V" in p50_mx
          and '@At("HEAD")' in p50_mx and "cancellable = true" in p50_mx and "require = 1" in p50_mx
          and "ci.cancel();" in p50_mx)
    check("drain mixin touches only grantDrainTp (heal, Ki, stun, kill, Majin absorb, Cell Jr untouched)",
          p50_mx.count("@Inject(") == 1 and p50_mx.count("method = ") == 1
          and "@ModifyArg" not in p50_mx and "@Redirect" not in p50_mx and "@Overwrite" not in p50_mx)
    check("drain mixin registered and packed",
          '"BioAndroidDrainTpMixin"' in p50_json and "BioAndroidDrainTpMixin \\" in p50_build)
    p50_abs = read(SRC / "progression/tp/BioAndroidAbsorb.java")
    check("LM absorb pays no TP (no NPC max-HP award, no player TP steal, no max-HP bonus)",
          "awardTp" not in p50_abs and "addTrainingPoints" not in p50_abs
          and "removeTrainingPoints" not in p50_abs and "getTrainingPoints" not in p50_abs)
    p50_guard = read(SRC / "progression/StaminaRegenGuard.java")
    check("stamina guard never clears the Bio drain cooldowns (Drain, DrainActive)",
          "removeCooldown(Cooldowns.DRAIN)" not in p50_guard and "removeCooldown(Cooldowns.DRAIN_ACTIVE)" not in p50_guard
          and "removeCooldown(Cooldowns.STAMINA_PAUSE)" in p50_guard)
    check("LM absorb still copies a player's skill",
          "learnableSkills(bioSkills, targetSkills)" in p50_abs and "DmzSkillUtil.setLevel(bioSkills, picked, newLevel);" in p50_abs)

    print("\n--- § lm 4.7.49 ---")
    p49_hub = read(SRC / "gui/ultra/UltraHubScreens.java")
    p49_char = read(SRC / "gui/ultra/UltraCharacterScreens.java")
    p49_prev = read(SRC / "gui/ultra/ModelPreview.java")
    p49_spec = read(SRC / "gui/ultra/UltraScreenSpec.java")
    p49_rival = read(SRC / "gui/ultra/UltraRivalScreens.java")
    p49_net = read(SRC / "net/gui/LmGuiNetwork.java")
    p49_sd = read(SRC / "mixin/StatsDataMixin.java")
    p49_res = read(SRC / "mixin/ResourcesEnergyDrainGuardMixin.java")
    p49_toml = read(MOD / "src/main/resources/META-INF/mods.toml")
    p49_shop = _after(p49_char, "private static void openBodyParts(").split("\n    /** Filter width", 1)[0]
    p49_shop = _after(p49_shop, "String filter = CosmeticBodyPartService.filter(filterRaw);")
    check("manifest row for lm 4.7.49", "lm-4749-layout-forms-audit" in manifest)
    check("main hub keeps system tiles only (no Find)", "PAGE_FIND" not in p49_hub)
    check("spin toggle is a fixed button under the model; page buttons start below it",
          "public UltraScreenSpec fixedButton(" in p49_spec and "appendButtonJson(btnArr, b, 0, height);" in p49_spec
          and "spec.fixedButton(" in p49_prev and "spec.setRow(Math.max(spec.row(), y + UltraStyle.BTN_H + UltraStyle.BTN_GAP));" in p49_prev)
    check("every standard model page calls the toggle right after buttonsAfterLines",
          p49_char.count("buttonsAfterLines();\n        ModelPreview.rotateButton(") + p49_char.count("buttonsAfterLines();\n            ModelPreview.rotateButton(") >= 8
          and "buttonsAfterLines();\n        ModelPreview.rotateButton(" in p49_rival)
    # 4.7.84 (C-7): the action row (Find, Clear parts, Part colors) sits above the cards; then the shared pager.
    _p49_order = ["placeFilterRow(spec, filter);", '"page:" + PAGE_FIND', '"page:clear_ask:body:" + self',
                  '"page:bodycolors"', "placePartCards(", 'placePager(spec, "page:bodyparts:"', ".back()"]
    check("body parts shop: filters, Find, Clear parts, Part colors, cards, pages, Back",
          all(x in p49_shop for x in _p49_order)
          and [p49_shop.index(x) for x in _p49_order] == sorted(p49_shop.index(x) for x in _p49_order))
    check("preview forms: bigger box, scale fit to the form; standard box unchanged",
          "FORMS = new Box(190, 46, 144, 186)" in p49_prev and "STANDARD = new Box(248, 60, 86, 120)" in p49_prev
          and "ModelPreview.formScale(form, ModelPreview.menuBox(box), ModelPreview.BASE_SCALE)" in p49_char
          and "getModelScaling()" in p49_prev and "assets/dragonminez/geo/entity/races/" in p49_prev
          and "Math.min(base, Math.min(heightFit, widthFit))" in p49_prev)
    check("worn and rival forms are fit too",
          "ModelPreview.wornScale(PlayerModelParts.renderTarget(player), ModelPreview.menuBox(box), ModelPreview.BASE_SCALE)" in p49_char
          and "ModelPreview.wornScale(rival, ModelPreview.menuBox(box), ModelPreview.BASE_SCALE)" in p49_rival
          and "ModelPreview.formScale(yours, ModelPreview.menuBox(box), ModelPreview.BASE_SCALE)" in p49_rival)
    check("menu self-test failure logs at boot instead of stopping the server (strict on test servers)",
          "UltraScreenSelfTest.runAll();" in p49_net and 'Boolean.getBoolean("lm.selftest.strict")' in p49_net
          and "Ultra GUI self-test FAILED at boot" in p49_net)
    check("dead StatsData cap handlers removed; energy clamp runs before revamp's store",
          "lm$personalMaxValue" not in p49_sd and "lm$clampStatBuy" not in p49_sd
          and "@Mixin(value = Resources.class, remap = false, priority = 900)" in p49_res)
    check("mods.toml: optional version ranges for every hard-hooked mod",
          all(('modId = "' + m + '"') in p49_toml for m in
              ("dmzultra", "dmzrevamp", "dmz_mohist_melee_fix", "simplymore", "curios", "customnpcs",
               "dmzlivingworld", "architectury", "attributeslib")))

    print("\n--- § lm 4.7.48 ---")
    p48_prev = read(SRC / "gui/ultra/ModelPreview.java")
    p48_char = read(SRC / "gui/ultra/UltraCharacterScreens.java")
    p48_rival = read(SRC / "gui/ultra/UltraRivalScreens.java")
    p48_act = read(SRC / "gui/ultra/UltraGuiActions.java")
    p48_parts = read(SRC / "gui/ultra/PlayerModelParts.java")
    p48_figure = _after(p48_prev, "public static JsonObject formFigure").split("public static JsonObject rivalFigure", 1)[0]
    # Labels avoid the word the CNPC-retired filter skips, so these run.
    check("manifest row for lm 4.7.48 model screens", "lm-4748-model-previews" in manifest)
    check("one spin action flips the viewer and resends the page",
          'ROTATE_ACTION = "model_rotate"' in p48_prev
          and "ModelPreview.ROTATE_ACTION.equals(parts.action)" in p48_act
          and "ModelPreview.flipRotate(viewer)" in p48_act)
    check("every character model page spins by the viewer's choice and has the toggle",
          "0.9f, true" not in p48_char
          and p48_char.count("ModelPreview.rotateButton(spec, player,") >= 9
          # 4.7.49: the standard box goes through standardModel / ModelPreview.STANDARD.
          and p48_char.count("ModelPreview.rotate(player)") >= 3)
    # 4.7.71: a form figure carries the viewer's own colours for that form (previewColors), still on a made-up subject.
    check("form figures are made-up uuid subjects with the viewer's colours for the form, never subject local",
          '"subject", "uuid:" + UUID.nameUUIDFromBytes' in p48_figure
          and '"local"' not in p48_prev and "PlayerModelParts.previewColors(" in p48_figure
          and "ConfigManager.getAllFormsForRace(race)" in p48_prev)
    check("Character Home forms page steps the race's forms",
          'PAGE_FORM_PREVIEW = "form_preview"' in p48_char and "openFormPreview" in p48_char
          and '"Preview forms", formsBlock' in p48_char and "ModelPreview.pick(player, form)" in p48_char)
    check("rival profile shows the rival via the inspect builder, with a you/rival switch",
          "return target == null ? null : build(target, true);" in p48_parts
          and "PlayerModelParts.otherPlayerSnapshot(rival)" in p48_prev
          and "ModelPreview.rivalFigure(player, rival)" in p48_rival
          and 'VIEW_YOU = "|you"' in p48_rival and '"Show you"' in p48_rival
          and 'ModelPreview.rotateButton(spec, player, "list_detail:" + payload)' in p48_rival)

    print("\n--- § lm 4.7.47 ---")
    p47_build = read(MOD / "build.sh")
    p47_sense = read(SRC / "mixin/DojoWarSenseMixin.java")
    p47_plugin = read(SRC / "mixin/LegacyMechanicsMixinPlugin.java")
    p47_json = read(MOD / "src/main/resources/legacymechanics.mixins.json")
    check("manifest lm-4747-noea-removed row", "lm-4747-noea-removed" in manifest)
    check("build needs no Noea jar; no LM source references Noea classes",
          "NOEA_JAR" not in p47_build and "$NOEA" not in p47_build
          and not any("com.butterjaffa" in f.read_text(encoding="utf-8") for f in SRC.rglob("*.java"))
          and not (SRC / "mixin/noea").exists()
          and '"noea.' not in p47_json)
    check("dojo war sense hooks DMZ Ultra's SenseService.gather, gated on Ultra's class file",
          '@Mixin(targets = "com.dmzultra.sense.SenseService"' in p47_sense
          and 'method = "gather"' in p47_sense and "SenseOptions.maxSignatures()" in p47_sense
          and '"com/dmzultra/sense/SenseService.class"' in p47_plugin)
    check("Noea-gated wipe mixins stay off as on live",
          'mixinClassName.endsWith("MeleeStatsResetAbsorptionWipeMixin"))) {\n            return false;' in p47_plugin)

    print("\n--- § lm 4.7.46 ---")
    import json as _json46
    p46_ver = read(SRC / "gui/ultra/UltraVersion.java")
    p46_cat = read(SRC / "gui/ultra/BodyPartCatalog.java")
    p46_gen = read(SRC / "gui/ultra/UltraFormPartsGenerator.java")
    p46_test = read(SRC / "gui/ultra/BodyPartCatalogSelfTest.java")
    p46_net = read(SRC / "net/gui/LmGuiNetwork.java")
    p46_build = read(MOD / "build.sh")
    p46_053 = _json46.loads(read(SRC.parents[3] / "resources" / "dmzultra-form-parts-0.5.3.json"))
    p46_040 = _json46.loads(read(SRC.parents[3] / "resources" / "dmzultra-form-parts-0.4.0.json"))
    check("manifest lm-4746 rows", all(r in manifest for r in (
        "lm-4746-ultra-version-catalogs", "lm-4746-tier-state-machine", "lm-4746-body-parts-shop-ui",
        "lm-4746-config-design-box-selftest")))
    check("Ultra version from the mod list; unknown = 0.4.0; one boot line before the screen self-test",
          'getModContainerById(MOD_ID)' in p46_ver and 'BASELINE = "0.4.0"' in p46_ver
          and "UltraVersion.logAtBoot();" in p46_net
          and p46_net.index("UltraVersion.logAtBoot();") < p46_net.index("UltraScreenSelfTest.runAll();"))
    check("0.5.3 catalog: generated from stock 0.5.3, 115 / 410 / 408, glind alias keys, 6 buffed pieces",
          p46_053.get("ultraVersion") == "0.5.3" and len(p46_053["pieces"]) == 115
          and len(p46_053["forms"]) == 410 and len(p46_053["designs"]) == 408
          and all("glind/%s/%s" % (g, f) in p46_053["forms"] for g in ("godforms", "demongodforms")
                  for f in ("demongodawakened", "demongodtrue"))
          and sum(p.endswith("_buffed") for p in p46_053["pieces"]) == 6
          and not any(k.startswith("heeter/part/") for k in p46_053["forms"]))
    check("0.4.0 catalog unchanged: 109 / 220 / 220",
          p46_040.get("ultraVersion") == "0.4.0" and len(p46_040["pieces"]) == 109
          and len(p46_040["forms"]) == 220 and len(p46_040["designs"]) == 220)
    check("selection: highest shipped <= detected, never newer; newer Ultra generates and must pass the self-test",
          'SHIPPED = List.of("0.4.0", "0.5.3"' in p46_cat
          and "UltraVersion.compare(v, detected) <= 0" in p46_cat
          and "BodyPartCatalogSelfTest.check(generated, BodyPartCatalogSelfTest.Expect.newerThan(fallback))" in p46_cat
          and "LOG.error(" in p46_cat and "return fallback;" in p46_cat)
    check("every part path reads the active catalog",
          "return active().resolve(" in p46_cat and "return active().shopPieces();" in p46_cat
          and "return active().design(" in p46_cat)
    check("Java generator mirrors the Python one (sets order, 3-segment keys, both alias passes, forms.json pieces)",
          'KEY = Pattern.compile("^[a-z0-9_]+/[a-z0-9_]+/[a-z0-9_]+$")' in p46_gen
          and "applyAliases(aliases, forms, designs);" in p46_gen
          and "applyAliases(serverAliases, forms, designs);" in p46_gen
          and 'out.add(BASE + "forms.json");' in p46_gen)
    check("self-test: both catalogs with their own counts, selection by version, generator parity",
          'new Expect("0.5.3", 115, 410, 408' in p46_test and 'new Expect("0.4.0", 109, 220, 220' in p46_test
          and 'selects(fails, "0.4.0", "0.4.0", "shipped")' in p46_test
          and 'selects(fails, "0.5.3", "0.5.3", "shipped")' in p46_test
          and 'selects(fails, "0.6.0", "0.5.3", "generated")' in p46_test
          and '"demon", "abyssforms", "demongod"' in p46_test)
    check("build ships both catalogs, the old name as the 0.4.0 alias, and the server aliases",
          'cp "$RES"/dmzultra-form-parts-*.json "$merge/"' in p46_build
          and 'cp "$RES/dmzultra-form-parts-0.4.0.json" "$merge/dmzultra-form-parts.json"' in p46_build
          and 'dmzultra-form-parts-server-aliases.json' in p46_build
          and '-C "$EXTRA" dmzultra-form-parts.json' in p46_build
          and '-C "$EXTRA" dmzultra-form-parts-server-aliases.json' in p46_build)
    check("Cerealian Tail hairstyle pieces never take the tail slot",
          'HAIR_TAIL_PREFIX = "cereal_tail_"' in p46_cat)
    p46_state = read(SRC / "gui/ultra/PurchaseState.java")
    p46_tier = read(SRC / "gui/ultra/UltraDifficultyTierUi.java")
    p46_diff = read(SRC / "gui/ultra/UltraDifficultyScreens.java")
    p46_caps = read(SRC / "progression/bridge/RevampEffectiveCaps.java")
    check("PurchaseState: Ultra's 10 states in FormsRules order; only OWNED / BUYABLE are tappable",
          "ACTIVE,\n" in p46_state and "LATER;\n" in p46_state
          and "return this == OWNED || this == BUYABLE;" in p46_state)
    check("tier lines quote revamp's effective level cap, never LmOverhaulCapMath or OverhaulScale",
          "RevampEffectiveCaps.levelMaximum()" in p46_tier and '"configuredLevelMaximum"' in p46_caps
          and "LmOverhaulCapMath" not in p46_tier and "OverhaulScale" not in p46_tier)
    # 4.7.57 retired the per-tier state lines: one button per tier, each opens its detail page.
    check("Tiers page: one button per tier, state shown by the caption (4.7.57 replaced the state lines)",
          # 4.7.59: caption from placeTierButton (You are here / Recommended / Locked), no state lines.
          "private static void placeTierButton(" in p46_diff and '" - You are here"' in p46_diff
          and "if (view.state().tappable()) {" not in p46_diff)
    p46_char = read(SRC / "gui/ultra/UltraCharacterScreens.java")
    p46_svc = read(SRC / "character/CosmeticBodyPartService.java")
    p46_hub = read(SRC / "gui/ultra/UltraHubScreens.java")
    p46_parts = read(SRC / "gui/ultra/PlayerModelParts.java")
    check("shop: priced locked cards, six slot filters, Find button",
          "priced(name, price.isEmpty() ? \"Locked\" : price)" in p46_char
          and 'FILTERS = List.of("all", "body", "tail", "ears", "wings", "horns")' in p46_svc
          and '"page:" + PAGE_FIND' in p46_char)
    check("find: server-side match, cap 40, 5 recents, empty-state echo, textinput fallback, shop entry only",
          "FIND_CAP = 40" in p46_svc and "RECENT_MAX = 5" in p46_svc
          and "No parts matching '\" + query + \"'" in p46_char
          and "LmNativeNetwork.takesTextInput(player)" in p46_char
          # 4.7.49: Find left the main hub; the Body parts shop keeps its Find button.
          and "PAGE_FIND" not in p46_hub and '"page:" + PAGE_FIND' in p46_char)
    check("confirm pages preview the candidate on the model",
          "PlayerModelParts.previewSnapshot(player," in p46_char
          and "public static JsonObject previewSnapshot(" in p46_parts)
    p46_st = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    check("config design box comes from UltraConfigScreens, not a literal",
          "int wantW = UltraConfigScreens.CONFIG_DESIGN_W;" in p46_st and "!= 426" not in p46_st)

    print("\n--- § lm 4.7.45 ---")
    p45 = read(SRC / "progression/ChamberKillMastery.java")
    p45_ev = read(SRC / "event/DifficultyEvents.java")
    p45_grant = _after(p45, "public static double grant(ServerPlayer killer) {")
    check("manifest lm-4745-chamber-kill-mastery row", "lm-4745-chamber-kill-mastery" in manifest)
    check("wave foe = Ultra's persistent-data uuid; lowest-priority death hook",
          'WAVE_FOE = "dmzultra_chamber_enemy"' in p45 and "PersistentDataAccess.get(dead).m_128403_(WAVE_FOE)" in p45
          and re.search(r"@SubscribeEvent\(priority = EventPriority\.LOWEST\)\s+public void onChamberWaveKill\("
                        r"LivingDeathEvent event\)", p45_ev) is not None)
    check("kill = 25 hits of masteryPerHitDealt through applyMasteryGainMultiplier then gainMastery, synced",
          "KILL_HITS = 25.0;" in p45
          and "PotionEffectHelper.applyMasteryGainMultiplier(killer, perHit * KILL_HITS)" in p45_grant
          and "character.gainMastery(group, form, value);" in p45_grant
          and "new StatsSyncS2C(killer)" in p45_grant)
    check("no LM factor, no own clamp or write: gainMastery owns the cap",
          "HitTakenMastery." not in p45 and "setMastery" not in p45 and "addMastery" not in p45
          and "Math.min" not in p45)
    check("player killers only (projectile / summon owners), never fake players; active-form guard",
          "source.m_7639_()" in p45 and "projectile.m_19749_()" in p45 and "ownable.m_269323_()" in p45
          and "instanceof FakePlayer ? null" in p45
          and "group == null || group.isEmpty() || form == null || form.isEmpty() || formData == null" in p45_grant)
    p45_build = read(MOD / "build.sh")
    check("chamber kill probe is test-only: src/probe, found by name, never in the production jar",
          not (SRC / "progression/ChamberKillMasteryProbe.java").exists()
          and (MOD / "src/probe/java/com/dbzlegacy/adaptivedifficulty/progression/ChamberKillMasteryProbe.java").exists()
          and '"lm.probe.chamberKill"' in p45_ev and "grep -Eq 'progression/ChamberKillMasteryProbe'" in p45_build)

    print("\n--- § lm 4.7.44 ---")
    p44_dr = read(SRC / "sparring/DojoRankings.java")
    p44_ui = read(SRC / "gui/ultra/UltraSparScreens.java")
    p44_api = read(SRC / "gui/SparGuiApi.java")
    p44_cmd = read(SRC / "command/SparCommands.java")
    p44_ensure = _after(p44_dr, "public static void ensureSeason() {").split("public static void advanceSeasonNow()", 1)[0]
    p44_prev = _after(p44_dr, "private static List<String> previousMemberLinesForKey(").split("private static Set<String> rosterUuids", 1)[0]
    check("manifest lm-4744-dojo-roster-previous-members row", "lm-4744-dojo-roster-previous-members" in manifest)
    check("members pages render the live roster (apprenticeCards / membershipDojoCards), not the whole board",
          "List<String> lines = ownedRosterLines(player, \"Your dojo\");" in p44_dr
          and "return joinedRosterLines(player, \"Your mentor's dojo\");" in p44_dr
          and "SparringSystem.apprenticeCards(player)" in p44_dr
          and "SparringSystem.membershipDojoCards(player)" in p44_dr
          and "memberLinesForKey" not in p44_dr)
    check("previous members = board rows not in the roster, ranked by RP, with the empty state",
          "!roster.contains(m.uuid.trim().toLowerCase(Locale.ROOT))" in p44_prev
          and "m.rpContributed).reversed()" in p44_prev
          and "No previous members this season." in p44_prev
          and ".remove(" not in p44_prev)
    check("Dojo tab offers Previous members with the your-dojo / mentor's-dojo toggle pages",
          '"dojo_previous_members|Previous members"' in p44_ui
          and 'case "dojo_previous_members", "dojo_previous_members_own" -> openDojo(player, "previous_own");' in p44_ui
          and 'case "dojo_previous_members_joined" -> openDojo(player, "previous_joined");' in p44_ui
          and "SparGuiApi.ownedDojoPreviousMemberLines(player)" in p44_ui
          and "SparGuiApi.joinedDojoPreviousMemberLines(player)" in p44_ui
          and '"page:" + prefix + "_own"' in p44_ui and '"page:" + prefix + "_joined"' in p44_ui
          and "ownedDojoPreviousMemberLines" in p44_api and "joinedDojoPreviousMemberLines" in p44_api)
    check("/spar dojo previous-members and staff-only /spar dojo season advance [confirm]",
          'Commands.m_82127_("previous-members")' in p44_cmd
          and 'CommandAccess.staffLiteral("season")' in p44_cmd
          and "dojoSeasonAdvance(ctx.getSource(), false)" in p44_cmd
          and "dojoSeasonAdvance(ctx.getSource(), true)" in p44_cmd
          and "StaffAccess.denyUnlessStaff(source) == 0" in _after(p44_cmd, "private static int dojoSeasonAdvance")[:400])
    check("one rollover path: ensureSeason expiry and the staff advance both call advanceSeasonNow",
          "advanceSeasonNow();" in p44_ensure and "archiveSeason(" not in p44_ensure
          and p44_dr.count("archiveSeason(store.dojoSeason);") == 1
          and "advanceSeasonNow();" in _after(p44_dr, "public static List<String> advanceSeasonByStaff()")[:900]
          and "broadcast(" not in _after(p44_dr, "public static void advanceSeasonNow()").split("private static SparStore.DojoSeason freshSeason", 1)[0])
    p44_ev = read(SRC / "event/DifficultyEvents.java")
    p44_build = read(MOD / "build.sh")
    check("dojo roster probe is test-only: src/probe, found by name, never in the production jar",
          not (SRC / "sparring/DojoRosterProbe.java").exists()
          and (MOD / "src/probe/java/com/dbzlegacy/adaptivedifficulty/sparring/DojoRosterProbe.java").exists()
          and '"lm.probe.dojoRoster"' in p44_ev and "grep -Eq 'sparring/DojoRosterProbe'" in p44_build)

    print("\n--- § lm 4.7.43 ---")
    p43_cap = read(SRC / "progression/shop/SlotRecordCapture.java")
    p43_mix = read(SRC / "mixin/dmzultra/DmzultraSlotCaptureMixin.java")
    p43_plugin = read(SRC / "mixin/LegacyMechanicsMixinPlugin.java")
    p43_json = read(MOD / "src/main/resources/legacymechanics.mixins.json")
    p43_ev = read(SRC / "event/DifficultyEvents.java")
    p43_build = read(MOD / "build.sh")
    p43_capture = _after(p43_cap, "static void capture(ServerPlayer player, SlotIndex index, String when) {").split("private static synchronized Method recordMethod", 1)[0]
    check("manifest lm-4743-slot-capture-on-logout row", "lm-4743-slot-capture-on-logout" in manifest)
    check("slot capture mixin registered and gated on Ultra's slots classes",
          '"dmzultra.DmzultraSlotCaptureMixin"' in p43_json
          and 'endsWith("DmzultraSlotCaptureMixin")' in p43_plugin
          and 'loader.getResource("com/dmzultra/slots/SlotService.class") != null' in p43_plugin)
    check("mixin: logout HEAD + RETURN and stopped HEAD on SlotService, require 0, all wrapped",
          '@Mixin(targets = "com.dmzultra.slots.SlotService", remap = false)' in p43_mix
          and p43_mix.count('@Inject(method = "logout", at = @At("HEAD"), require = 0, remap = false)') == 1
          and p43_mix.count('@Inject(method = "logout", at = @At("RETURN"), require = 0, remap = false)') == 1
          and p43_mix.count('@Inject(method = "stopped", at = @At("HEAD"), require = 0, remap = false)') == 1
          and p43_mix.count("catch (Throwable ignored)") == 3)
    check("capture mirrors the switch path: record(player, slot, index.gen, false) then SlotStore.writeRecord",
          'getDeclaredMethod("record", ServerPlayer.class, int.class, long.class,' in p43_cap
          and "m.invoke(null, player, slot, gen, false);" in p43_capture
          and "SlotStore.writeRecord(server, account, slot, out);" in p43_capture
          and "long gen = index.gen;" in p43_capture
          and "index.entry(slot) == null" in p43_capture)
    check("capture never ends the form (no toBase) and never throws",
          "toBase(" not in p43_capture and "DmzPart" not in p43_cap.split("public final class", 1)[1]
          and "} catch (Throwable t) {" in p43_capture)
    check("slot capture probe is test-only: src/probe, found by name, never in the production jar",
          not (SRC / "progression/shop/SlotCaptureProbe.java").exists()
          and (MOD / "src/probe/java/com/dbzlegacy/adaptivedifficulty/progression/shop/SlotCaptureProbe.java").exists()
          and '"lm.probe.slotCapture"' in p43_ev and "|shop/SlotCaptureProbe|" in p43_build)

    print("\n--- § lm 4.7.42 ---")
    p42_core = read(SRC / "progression/HitTakenMastery.java")
    p42_probe = read(MOD / "src/probe/java/com/dbzlegacy/adaptivedifficulty/progression/HitTakenMasteryProbe.java")
    p42_factor = _after(p42_core, "public static double factor(Entity attacker) {").split("public static boolean isSparNpc", 1)[0]
    check("manifest lm-4742-non-pvp-hit-taken-mastery row", "lm-4742-non-pvp-hit-taken-mastery" in manifest)
    check("NON_PVP_FACTOR 0.25 beside HIT_TAKEN_FACTOR 0.25 and NPC_SPAR_FACTOR 0.5",
          "public static final double NON_PVP_FACTOR = 0.25;" in p42_core
          and "public static final double HIT_TAKEN_FACTOR = 0.25;" in p42_core
          and "public static final double NPC_SPAR_FACTOR = 0.5;" in p42_core)
    check("factor: player 0.25, sparring NPC 0.03125, anything else (null included) 0.0625 - never PvP's by default",
          p42_factor.index("if (attacker instanceof ServerPlayer) {") < p42_factor.index("return HIT_TAKEN_FACTOR;")
          < p42_factor.index("if (isSparNpc(attacker)) {")
          < p42_factor.index("return HIT_TAKEN_FACTOR * NON_PVP_FACTOR * NPC_SPAR_FACTOR;")
          < p42_factor.index("return HIT_TAKEN_FACTOR * NON_PVP_FACTOR;")
          and p42_factor.count("return ") == 3)
    check("probe expects the 4.7.42 ladder, chamber and trial foes included",
          "expectTaken * 0.0625" in p42_probe and "expectTaken * 0.03125" in p42_probe
          and 'chamberFoe(level, "saga_saibaman1")' in p42_probe and 'chamberFoe(level, "saga_vegeta")' in p42_probe
          and "expectTaken * 0.125" not in p42_probe)

    print("\n--- § lm 4.7.41 ---")
    p41_pc = read(SRC / "progression/shop/PrestigeCharacter.java")
    p41_melee = read(SRC / "progression/shop/PrestigeCharacterMelee.java")
    p41_ultra = read(SRC / "progression/shop/PrestigeCharacterUltra.java")
    p41_ev = read(SRC / "event/DifficultyEvents.java")
    p41_build = read(MOD / "build.sh")
    p41_fresh = _after(p41_pc, "static void freshStats(ServerPlayer player) {").split("static List<String> keptStats", 1)[0]
    check("manifest lm-4741-born-fresh-stats row", "lm-4741-born-fresh-stats" in manifest)
    check("melee fix bridge: forceZeroSnapshot / adoptCurrent, loaded only when PrimaryStatRepair exists",
          "PrimaryStatRepair.forceZeroSnapshot(player);" in p41_melee
          and "PrimaryStatRepair.adoptCurrent(player);" in p41_melee
          and 'Class.forName("com.dbzlegacy.mohistmelee.PrimaryStatRepair", false,' in p41_pc
          and "PrestigeCharacterMelee.zero(player);" in p41_pc and "PrestigeCharacterMelee.adopt(player);" in p41_pc)
    check("freshStats clears the saved copy after the bases are 0 and before any read",
          p41_fresh.index("stats.copyFrom(new StatsData((Player) player));")
          < p41_fresh.index("live.m_22100_(stat.get().m_22082_());")
          < p41_fresh.index("meleeSnapshot(player, true);")
          < p41_fresh.index("StatsEvents.applyHealthBonus(player);")
          < p41_fresh.index("List<String> kept = keptStats(player);")
          and p41_fresh.count("meleeSnapshot(player, true);") == 1)
    check("freshStats warns when any of the six stats is still above 0",
          "new character still has stats after the reset" in p41_fresh)
    check("switch into a played character adopts its own stats as the saved copy",
          "if (player != null && !blank) {" in p41_ultra
          and "PrestigeCharacter.meleeSnapshot(player, false);" in p41_ultra)
    check("born fresh stats probe is test-only: src/probe, found by name, never in the production jar",
          not (SRC / "progression/shop/BornFreshStatsProbe.java").exists()
          and (MOD / "src/probe/java/com/dbzlegacy/adaptivedifficulty/progression/shop/BornFreshStatsProbe.java").exists()
          and '"lm.probe.bornFreshStats"' in p41_ev
          and '"com.dbzlegacy.adaptivedifficulty.progression.shop.BornFreshStatsProbe"' in p41_ev
          and "|shop/BornFreshStatsProbe|" in p41_build)

    print("\n--- § lm 4.7.40 ---")
    p40_core = read(SRC / "progression/HitTakenMastery.java")
    p40_hit = read(SRC / "mixin/StatsEventsHitTakenMasteryMixin.java")
    p40_ret = read(SRC / "mixin/PotionEffectHelperHitTakenMasteryMixin.java")
    p40_json = read(SRC.parents[3] / "resources/legacymechanics.mixins.json")
    p40_ev = read(SRC / "event/DifficultyEvents.java")
    p40_build = read(MOD / "build.sh")
    check("manifest lm-4740-hit-taken-mastery row", "lm-4740-hit-taken-mastery" in manifest)
    check("hit-taken factor 0.25; sparring NPC (shadow dummy / quest SPAR partner) x 0.5 more = 0.125",
          "public static final double HIT_TAKEN_FACTOR = 0.25;" in p40_core
          and "public static final double NPC_SPAR_FACTOR = 0.5;" in p40_core
          and "if (entity == null || entity instanceof ServerPlayer) {" in p40_core
          and "if (entity instanceof ShadowDummyEntity) {" in p40_core
          and "return QuestService.isSparPartner(entity);" in p40_core)
    check("only the hit victim (a player) is scaled; the attacker's dealt grant is untouched",
          "if (event == null || !(event.getEntity() instanceof ServerPlayer victim)) {" in p40_core
          and "return mark != null && entity != null && entity == mark.victim() ? value * mark.factor() : value;" in p40_core
          and p40_core.count("ACTIVE.remove();") == 2)
    check("hook: StatsEvents.onEntityHit HEAD begin / RETURN end (DMZ's per-hit mastery grant)",
          "@Mixin(value = StatsEvents.class, remap = false)" in p40_hit
          and '@Inject(method = "onEntityHit", at = @At("HEAD"), remap = false)' in p40_hit
          and '@Inject(method = "onEntityHit", at = @At("RETURN"), remap = false)' in p40_hit
          and "HitTakenMastery.begin(event);" in p40_hit and "HitTakenMastery.end();" in p40_hit)
    check("hook: applyMasteryGainMultiplier result scaled per entity (all factors multiply, boosts stay on top)",
          "@Mixin(value = PotionEffectHelper.class, remap = false)" in p40_ret
          and '@Inject(method = "applyMasteryGainMultiplier", at = @At("RETURN"), cancellable = true, remap = false)' in p40_ret
          and "double scaled = HitTakenMastery.scale(entity, value);" in p40_ret)
    check("both mixins registered", '"PotionEffectHelperHitTakenMasteryMixin"' in p40_json
          and '"StatsEventsHitTakenMasteryMixin"' in p40_json and "NpcSpar" not in p40_json)
    check("hit taken mastery probe is test-only: src/probe, found by name, never in the production jar",
          not (SRC / "progression/HitTakenMasteryProbe.java").exists()
          and (MOD / "src/probe/java/com/dbzlegacy/adaptivedifficulty/progression/HitTakenMasteryProbe.java").exists()
          and '"lm.probe.hitTakenMastery"' in p40_ev
          and '"com.dbzlegacy.adaptivedifficulty.progression.HitTakenMasteryProbe"' in p40_ev
          and "|HitTakenMasteryProbe)'" in p40_build)

    print("\n--- § lm 4.7.38 ---")
    import json as _json38
    p38_cat = _json38.loads(read(SRC.parents[3] / "resources" / "dmzultra-form-parts-0.4.0.json"))
    p38_alias = _json38.loads(read(MOD / "sim" / "form-parts-server-aliases.json"))
    p38_gen = read(MOD / "sim" / "gen_dmzultra_form_parts.py")
    p38_test = read(SRC / "gui/ultra/BodyPartCatalogSelfTest.java")
    _g = "glind/{}/{}"
    check("manifest lm-4738-glind-godforms-parts row", "lm-4738-glind-godforms-parts" in manifest)
    check("generator applies the LM-side alias file after Ultra's aliases",
          'SERVER_ALIASES = Path(__file__).resolve().parent / "form-parts-server-aliases.json"' in p38_gen
          and "apply_aliases(server_aliases(), forms, designs, owner, missing)" in p38_gen
          and p38_alias["aliases"] == {_g.format("godforms", "demongodawakened"): _g.format("kishinforms", "demongodawakened"),
                                       _g.format("godforms", "demongodtrue"): _g.format("kishinforms", "demongodtrue")})
    check("catalog: 220 forms / 220 designs; glind godforms keys copy kishinforms pieces and colors",
          len(p38_cat["forms"]) == 220 and len(p38_cat["designs"]) == 220
          and all(p38_cat["forms"][_g.format("godforms", f)] == p38_cat["forms"][_g.format("kishinforms", f)]
                  and p38_cat["designs"][_g.format("godforms", f)] == p38_cat["designs"][_g.format("kishinforms", f)]
                  for f in ("demongodawakened", "demongodtrue"))
          and len(p38_cat["forms"][_g.format("godforms", "demongodawakened")]) == 5
          and len(p38_cat["forms"][_g.format("godforms", "demongodtrue")]) == 7
          and _g.format("demongodforms", "demongodtrue") in p38_cat["forms"])
    check("self-test resolves glind/godforms pieces and colors",
          'c.resolve("glind", "godforms", "demongodawakened", "", "")' in p38_test
          and "new BodyPartCatalog.Design(4915330, 13151080, 4077125, 12482275)" in p38_test)

    print("\n--- § lm 4.7.37 ---")
    p37_shop = read(SRC / "progression/shop/GodKiEnhancementShop.java")
    p37_ev = read(SRC / "event/DifficultyEvents.java")
    p37_build = read(MOD / "build.sh")
    p37_gate = _after(p37_shop, "public static String blockReason(").split("/** Transform-gate feedback", 1)[0]
    p37_gods = _after(p37_gate, "if (GOD_FORMS_GROUP.equalsIgnoreCase(g))").split("if (ENHANCEMENT_GROUP", 1)[0]
    check("manifest lm-4737-godforms-natural-mastery row", "lm-4737-godforms-natural-mastery" in manifest)
    check("godforms gate: enhancement OR 1000 natural godki:godki mastery (formMastery, no new field)",
          "public static final double GOD_FORMS_MASTERY = 1000.0;" in p37_shop
          and "return hasEnhancement(player) || naturalGodKiMastery(player) >= GOD_FORMS_MASTERY - 1.0E-9;" in p37_shop
          and "KiMasteryTrack.GOD_KI.formMastery(player)" in p37_shop
          and "canUseCustomGodForms(player)" in p37_gods)
    check("godforms gate: wallet check kept in hasEnhancement (back-compat)",
          "if (KiMasteryTrack.GOD_KI.level(player) >= 1) {" in p37_shop)
    check("godforms message names the natural path first; godkienhancement messages unchanged",
          '"God forms need 1000 God Ki mastery - fight in God Ki form to raise it, or add a God Ki"' in p37_gods
          and '" Mastery level in the Mastery wallet (Prestige shop)."' in p37_gods
          and "God forms need God Ki -" not in p37_shop
          and '"You need God Ki - add a God Ki Mastery level in the Mastery wallet (Prestige shop)."' in p37_gate
          and '"Your God Ki mastery is too low - add God Ki Mastery levels in the Mastery wallet"' in p37_gate)
    check("god forms probe is test-only: src/probe, found by name, never in the production jar",
          not (SRC / "progression/GodFormsGateProbe.java").exists()
          and (MOD / "src/probe/java/com/dbzlegacy/adaptivedifficulty/progression/GodFormsGateProbe.java").exists()
          and '"com.dbzlegacy.adaptivedifficulty.progression.GodFormsGateProbe"' in p37_ev
          and '"lm.probe.godForms"' in p37_ev
          and "Class.forName(probeClass)" in p37_ev
          and "grep -Eq 'progression/(GodFormsGateProbe|ChamberWaveCapProbe" in p37_build)
    p37_cap = read(SRC / "progression/ChamberWaveCap.java")
    p37_mix = read(SRC / "mixin/dmzultra/DmzultraChamberWaveCooldownMixin.java")
    check("manifest lm-4737-chamber-wave-cap row", "lm-4737-chamber-wave-cap" in manifest)
    check("chamber cap: 10 waves x 5 foes, named constants, clamp not refuse, drills untouched",
          "public static final int MAX_WAVES = 10;" in p37_cap
          and "public static final int MAX_PER_WAVE = 5;" in p37_cap
          and "config.drill() > 0" in p37_cap
          and "Math.min(MAX_WAVES, config.waves())" in p37_cap
          and "Math.min(MAX_PER_WAVE, config.perWave())" in p37_cap
          and "new GodChamberWaves.Config(config.trial(), perWave, waves" in p37_cap)
    check("chamber cap: config replaced at start HEAD, notice at RETURN only for a started training run",
          '@ModifyVariable(method = "start", at = @At("HEAD"), argsOnly = true' in p37_mix
          and "ChamberWaveCap.onStart(player, config)" in p37_mix
          and '@Inject(method = "start", at = @At("RETURN")' in p37_mix
          and "ChamberWaveCap.afterStart(player)" in p37_mix
          and "status.mode() == GodChamberWaves.Status.TRAINING" in p37_cap
          and '"Wave count capped at "' in p37_cap and '"Enemies per wave capped at "' in p37_cap)
    check("chamber cap probe is test-only",
          not (SRC / "progression/ChamberWaveCapProbe.java").exists()
          and (MOD / "src/probe/java/com/dbzlegacy/adaptivedifficulty/progression/ChamberWaveCapProbe.java").exists()
          and '"com.dbzlegacy.adaptivedifficulty.progression.ChamberWaveCapProbe"' in p37_ev
          and '"lm.probe.chamberCap"' in p37_ev)

    print("\n--- § lm 4.7.36 ---")
    p36_ev = read(SRC / "event/DifficultyEvents.java")
    p36_guard = read(SRC / "event/DragonBallPistonGuard.java")
    p36_build = read(MOD / "build.sh")
    p36_help = read(SRC / "command/LmCommandHelp.java")
    p36_admin = read(SRC / "progression/shop/PrestigeAdmin.java")
    p36_self = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    p36_gk = _after(p36_self, "private static void checkGodKiWallet(").split("/** Exchange confirm:", 1)[0]
    check("manifest lm-4736-audit-pistons row", "lm-4736-audit-pistons" in manifest)
    check("pistons: PistonEvent.Pre cancelled when the push or destroy list holds a dragon ball",
          "public void onDragonBallPiston(net.minecraftforge.event.level.PistonEvent.Pre event)" in p36_ev
          and "DragonBallPistonGuard.onPistonPre(event);" in p36_ev
          and "structure.m_60422_();" in p36_guard
          and "hasBall(level, structure.m_60436_()) || hasBall(level, structure.m_60437_())" in p36_guard
          and "event.setCanceled(true);" in p36_guard)
    check("probe is test-only: not in src/main, in src/probe, found by name, never in the production jar",
          not (SRC / "event/DragonBallProbe.java").exists()
          and (MOD / "src/probe/java/com/dbzlegacy/adaptivedifficulty/event/DragonBallProbe.java").exists()
          and "DragonBallProbe.active()" not in p36_ev
          and 'Class.forName("com.dbzlegacy.adaptivedifficulty.event.DragonBallProbe")' in p36_ev
          and 'System.getProperty("lm.probe.dragonBalls", "").isEmpty()' in p36_ev
          and "grep -q 'event/DragonBallProbe'" in p36_build
          and 'LM_TEST_PROBES:-0' in p36_build
          and (ROOT / "docs/LM_TEST_PROBES.md").exists())
    check("staff God Ki strings read <0-10>",
          '"/lm admin prestige godki <player> set <0-10>"' in p36_help
          and "godki <player> <set|add|remove> <0-10>" in p36_admin
          and "<0-5>" not in p36_help and "<0-5>" not in p36_admin)
    check("self-test checks the 10-level God Ki wallet",
          "MasteryWallet.godKiLine(0, 10, 1000);" in p36_gk and "form.cap != 10000.0" in p36_gk
          and "form.maxPoints() != 100" in p36_gk and "godKiMaxLevel(form) != 10" in p36_gk
          and '"God Ki Level 0/10"' in p36_gk and '"Divine Ki Level 0/10"' in p36_gk
          and 'contains("Level 0/10")' in p36_gk and '"Level 0 -> 1 of 10"' in p36_gk
          and "0/5" not in p36_gk and ", 5, 1000)" not in p36_gk)

    print("\n--- § dragon balls 4.7.35 ---")
    db_pick = read(SRC / "event/DragonBallRadarPickup.java")
    db_hold = read(SRC / "event/DragonBallSetHold.java")
    db_resp = read(SRC / "event/DragonBallRespawn.java")
    db_ev = read(SRC / "event/DifficultyEvents.java")
    db_break = _after(db_pick, "public static void onBreak(").split("\n    }\n", 1)[0]
    check("manifest dragon-balls-4735 row", "dragon-balls-4735" in manifest)
    check("respawn: 3 minutes, armed by both pickup paths, fires DMZ's scatter, not regenerateSet",
          "DELAY_TICKS = 3 * 60 * 20;" in db_resp
          and "DragonBallsHandler.scatterDragonBalls(home, setId);" in db_resp
          and "regenerateSet(" not in db_resp
          and db_pick.count("DragonBallRespawn.afterPickup(") == 2
          and "inWorld(server, definition) > 0" in db_resp
          and "summonActive(server, setId)" in db_resp
          and 'safePulse("dragon-ball-respawn", () -> DragonBallRespawn.pulse(server, tick));' in db_ev
          and "DragonBallRespawn.reset();" in db_ev)
    check("respawn at boot: a set already at 0 gets a fresh timer (no instant spawn)",
          "if (!bootChecked) {" in db_resp
          and 'arm(server, definition.getId(), "no ball of it is in the world at boot");' in db_resp)
    check("one set per player: inventory, ender chest, bags, Curios; all 7 stars of one set",
          "scan(player.m_150109_(), definition, stars);" in db_hold
          and "scan(player.m_36327_(), definition, stars);" in db_hold
          and "ForgeCapabilities.ITEM_HANDLER" in db_hold
          and "getEquippedCurios" in db_hold
          and "return stars.containsAll(definition.getStars());" in db_hold)
    check("one set per player: radar left-click, radar break and ground pickup all refuse a full-set holder",
          db_pick.count("DragonBallSetHold.holdsFullSet(") == 4
          and "public static void onItemPickup(EntityItemPickupEvent event)" in db_pick
          and "DragonBallRadarPickup.onItemPickup(event);" in db_ev)
    check("radar-only break: no matching radar cancels the break",
          "if (!holdsMatchingRadar(player, ball)) {" in db_break
          and db_break.index("event.setCanceled(true);") < db_break.index("event.setCanceled(false);"))
    check("biradar takes Earth and Namek balls",
          'FUSED_RADAR = "fused_radar"' in db_pick
          and '(FUSED_RADAR.equals(definition.getId()) && ("earth".equals(set) || "namek".equals(set)' in db_pick)

    print("\n--- § text fixes 4.7.33 ---")
    tf_w = read(SRC / "gui/ultra/UltraWeightShopScreens.java")
    tf_r = read(SRC / "gui/ultra/UltraRivalScreens.java")
    tf_remove = _after(tf_r, "private static void openRemoveConfirm(").split("\n    }\n", 1)[0]
    tf_sys = read(SRC / "rival/RivalSystem.java")
    tf_api = read(SRC / "gui/RivalGuiApi.java")
    check("manifest text-fixes-4733 row", "text-fixes-4733" in manifest)
    check("weight shop: every player-facing max reads 10,000,000 (no raw MAX_WEIGHT_VALUE in text)",
          '"Type a whole number from 1 to " + MAX_WEIGHT_VALUE' not in tf_w
          and '"(1-" + MAX_WEIGHT_VALUE' not in tf_w
          and 'String.valueOf(MAX_WEIGHT_VALUE))' not in tf_w
          and tf_w.count('String.format(Locale.ROOT, "%,d", MAX_WEIGHT_VALUE)') == 5)
    check("remove ask page: one-way Declared says they come off your list, not drop to Declared",
          "RivalGuiApi.isOneWayDeclare(player, arg)" in tf_remove
          and "you come off their rival list" in tf_remove
          and "They drop to Declared - you can declare again any time." in tf_remove
          and "It moves to History - you can declare again any time." in tf_remove)
    check("isOneWayDeclare mirrors RivalSystem.remove: partner's declaredByMe decides",
          "return theirs == null || !theirs.declaredByMe;" in tf_api
          and "theirLink.declaredByMe" in tf_sys)
    check("Declared remove notify: claims Declared only when they still declare",
          "boolean theyKeepDeclare = theirLink != null && theirLink.declaredByMe;" in tf_sys
          and '"They are off your rival list too."' in tf_sys
          and "Saved to History. They may still have you as Declared." in tf_sys)

    print("\n--- § grant first mastery topup 4.7.32 ---")
    gt = read(SRC / "progression/form/FirstSuperformMastery.java")
    gt_grant = _after(gt, "public static Outcome grant(").split("\n    }\n", 1)[0]
    gt_probe = read(SRC / "progression/form/FormSkillGrantProbe.java")
    check("manifest grant-first-mastery-topup row", "grant-first-mastery-topup" in manifest)
    check("grant compares mastery exactly: no epsilon swallows a sub-epsilon gap",
          "boolean raised = target > current;" in gt_grant and "1.0E-9" not in gt_grant)
    check("raised branch writes the exact target and reads it back",
          gt_grant.index("boolean raised = target > current;")
          < gt_grant.index("masteries.setMastery(chosen.group, chosen.form, target, cap);")
          < gt_grant.index("if (masteries.getMastery(chosen.group, chosen.form) < target) {")
          and "Ask staff to re-grant after a relog." in gt_grant)
    check("skill repair still runs before the mastery top-up (one grant fixes both)",
          gt_grant.index("ensureSkill(data.getSkills(), skill, chosen.unlockLevel)")
          < gt_grant.index("boolean raised = target > current;"))
    check("probe covers hair 9.999999999999831 -> exactly 10.0 and exact 10.0 -> already has",
          "9.999999999999831" in gt_probe and "topped == 10.0" in gt_probe
          and 'exact.message.contains("already has")' in gt_probe)
    gt_hair = 9.999999999999831
    check("model: live hair fails the strict gate, old epsilon skipped it, exact compare raises it",
          not (gt_hair >= 10.0) and not (10.0 > gt_hair + 1e-9) and 10.0 > gt_hair)

    print("\n--- § gui humanize 4.7.31 ---")
    gh = lambda p: read(SRC / p)
    gh_rival = gh("gui/ultra/UltraRivalScreens.java")
    gh_remove = _after(gh_rival, "private static void openRemoveConfirm(").split("\n    }\n", 1)[0]
    gh_detail = _after(gh_rival, "private static void openListDetail(").split("\n    }\n", 1)[0]
    check("manifest gui-humanize-4731 row", "gui-humanize-4731" in manifest)
    check("P0 Remove rival: profile asks first, truthful notice, End rivalry confirm runs do:remove",
          "cannot be undone from this menu" not in gh_rival
          and '"page:remove_confirm:" + profileArg(arg, returnPage)' in gh_detail
          and '"do:remove:' not in gh_detail
          and 'p.startsWith("remove_confirm:")' in gh_rival
          and "They drop to Declared - you can declare again any time." in gh_remove
          and 'UltraStyle.danger("End rivalry")' in gh_remove and '"do:remove:" + arg' in gh_remove)
    gh_pres = gh("gui/ultra/UltraPrestigeScreens.java")
    gh_prog = gh("gui/ProgressionGuiApi.java")
    check("P1 prestige: level cap + real number, no Need power / Need higher level",
          all("Need power" not in x and "Need higher level" not in x for x in (gh_pres, gh_prog))
          and '.labelValue("Need",' not in gh_pres and '"Reach level " + ph.getOrDefault("required_fmt", "0")' in gh_pres
          and '.labelValue("Next prestige at level", ph.getOrDefault("required_fmt", "0")' in gh_pres
          and "int need = h < PRESTIGE_NEED.length ? PRESTIGE_NEED[h] : PRESTIGE_NEED_TOP;"
          in gh("progression/LmOverhaulCapMath.java"))
    check("P1 wording: full wipe, End this boost, GUI:/-> help, Ancient Coins path, weight names, Mutant/Majin",
          "full wipe" not in gh("gui/CharacterServicesGuiApi.java")
          and 'danger("End this boost")' in gh("gui/ultra/UltraProgressionScreens.java")
          and '"GUI: ' not in gh("gui/RivalGuiApi.java") and '"GUI: ' not in gh("sparring/SparringSystem.java")
          and '"-> Rival' not in gh("gui/RivalGuiApi.java")
          and "(GUI toggle)" not in gh("command/DifficultyCommands.java")
          and "turtle_shell, workout_weights" not in gh("gui/ultra/UltraWeightShopScreens.java")
          and '"Remove Mutant first"' in gh_pres and '"Remove Majin first"' in gh_pres)
    gh_char = gh("gui/ultra/UltraCharacterScreens.java")
    check("P1 Android: one accurate forms line on every Android page, no 'cannot be undone'",
          "It cannot be undone." not in gh_char and "within 10 seconds. This cannot be undone." not in gh_char
          and gh_char.count("ANDROID_FORMS_LINE") == 3  # 4.7.84 (C-2): the Android tab page is gone
          and '"Converting to Android deletes Super and Legendary forms."' in gh_char)
    gh_diff = gh("gui/ultra/UltraDifficultyScreens.java")
    check("P1 Clear active tier and P2 Unequip title go through ask pages",
          'spec.buttonLeft("page:clear_tier_ask"' in gh_diff and '.buttonRight("page:title_unequip_ask"' in gh_diff
          and gh_diff.count('"do:lower_tier:0"') == 1 and gh_diff.count('"do:clear_title"') == 1
          and "You will need Ancient Coins to activate a tier again." in gh_diff
          and '"clear_tier_ask".equals(p)' in gh_diff and '"title_unequip_ask".equals(p)' in gh_diff)
    gh_ch = gh("rival/RivalChallengeManager.java")
    gh_cancel = _after(gh_ch, "public String cancelChallenge(ServerPlayer player, String counterpartyPicker) {").split("\n    }\n", 1)[0]
    check("P1 challenges: bare cancel never forfeits a live duel; bare accept/decline list",
          gh_cancel.index("if (!named) {") < gh_cancel.index("endChallenge(")
          and "matchesCounterparty(other, counterpartyPicker, them)" in gh_cancel
          and gh_ch.count("return pendingIncomingList(player,") == 2
          and "acceptChallenge(player, who)" in gh("gui/RivalGuiApi.java")
          and 'challengeCancel(ctx.getSource(), "")' in gh("command/RivalCommands.java"))
    check("P1 chat menu: no ALL-CAPS status words",
          all(w not in gh("gui/DifficultyChatMenu.java") for w in
              ('"SYSTEM DISABLED"', '"NOT AVAILABLE"', '"DIFFICULTY OFF"', '"CURRENT"', '"[SWITCH]"', '"LOCKED"', '"EQUIPPED"')))
    check("P2 polish: Equipped, Reload Legacy Mechanics config, dash toggles, Staff only., full skill names",
          '"wearing " + UltraStyle.plain(bone)' in gh_char and "Reload LM config" not in gh_diff
          and "Reload LM config" not in gh("gui/ultra/UltraHubScreens.java")
          and "Turn event log" not in gh_diff and "Rival teams" not in gh_diff   # 4.7.78: team mode toggles removed
          and all("That menu is for staff only." not in gh(f) for f in
                  ("gui/CnpcStaffTestGui.java", "gui/ProgressionMenu.java", "gui/SkillsMenu.java"))
          and '"Def Pen"' not in gh_pres and '"Ki Manip"' not in gh_pres and '"Ki Prot"' not in gh_pres
          and '"Def Pen"' not in gh("progression/shop/SkillUnlockService.java"))
    gh_w = gh("gui/ultra/UltraWeightShopScreens.java")
    check("P2 weight shop: Ancient Copper, Free for staff, 10,000,000, no 1.3.9, says what to do",
          "Free (staff)" not in gh_w and "1.3.9" not in gh_w and "1 copper per" not in gh_w
          and "ancient copper" not in gh_w and '"%,d", MAX_WEIGHT_VALUE' in gh_w
          and "Ask staff to stock it." in gh_w)
    check("P2 shop hub has no staff button; Edit prestige shop lives on Staff Admin",
          '"page:staff_edit"' not in gh_pres
          and '"page:" + PAGE_PRESTIGE_SHOP_EDIT' in gh("gui/ultra/UltraHubScreens.java")
          and "UltraHubScreens.PAGE_PRESTIGE_SHOP_EDIT.equalsIgnoreCase(pg)" in gh("gui/ultra/UltraGui.java"))
    check("P2 rival chat has no exclamation marks; ProgressionChatMenu gone",
          "as a rival!" not in gh("rival/RivalSystem.java") and "Mutual!" not in gh("rival/RivalSystem.java")
          and not (SRC / "gui/ProgressionChatMenu.java").exists())

    print("\n--- § grant first skill usable 4.7.30 ---")
    gs = read(SRC / "progression/form/FirstSuperformMastery.java")
    gs_ens = _after(gs, "static SkillFix ensureSkill(Skills skills, String skill, int unlockLevel) {").split("\n    }\n", 1)[0]
    gs_grant = _after(gs, "public static Outcome grant(").split("\n    }\n", 1)[0]
    check("manifest grant-first-skill-usable row", "grant-first-skill-usable" in manifest)
    check("ensureSkill: no raw setSkillLevel; register usable max only when absent / too low; level >= 1; read back",
          "skills.setSkillLevel(" not in gs and "int need = Math.max(1, unlockLevel);" in gs_ens
          and "if (!had || skills.getMaxSkillLevel(skill) < need) {" in gs_ens
          and "Math.max(need, DmzSkillUtil.maxLevel(skills, skill, 10))" in gs_ens
          and "DmzSkillUtil.ensureRegistered(skills, skill, max);" in gs_ens
          and "DmzSkillUtil.setLevel(skills, skill, need);" in gs_ens
          and "skills.getSkillLevel(skill) < need) {\n                return SkillFix.FAILED;" in gs_ens)
    check("ensureRegistered falls back to 10 when no max is configured",
          "if (want <= 0) {\n                want = 10;" in read(SRC / "progression/DmzSkillUtil.java"))
    check("grant: skill first, failure names the skill and writes no mastery; notes on both success branches",
          gs_grant.index("ensureSkill(data.getSkills(), skill, chosen.unlockLevel)")
          < gs_grant.index("if (fix == SkillFix.FAILED) {")
          < gs_grant.index("masteries.getMastery(chosen.group, chosen.form)")
          < gs_grant.index("masteries.setMastery(")
          and '"Could not unlock the " + skillLabel(skill) + " skill for "' in gs_grant
          and "Ask staff to re-grant after a relog." in gs_grant
          and gs_grant.count("+ skillNote);") == 2 and "was broken and has been" in gs_grant)
    check("player text names skills, not ids",
          'case "superforms" -> "Super Forms";' in gs and 'case "godforms" -> "God Forms";' in gs
          and 'case "legendaryforms" -> "Legendary Forms";' in gs)
    check("probe registered and gated",
          "FormSkillGrantProbe.runIfRequested(event.getServer());" in read(SRC / "event/DifficultyEvents.java")
          and 'Boolean.getBoolean("lm.probe.formSkillGrant")' in read(SRC / "progression/form/FormSkillGrantProbe.java"))

    def _fix(has, level, mx, unlock, cfg_max=0, sticks=True):  # mirror of ensureSkill on a model skill
        need = max(1, unlock)
        if has and mx >= need and level >= need:
            return "READY", level, mx
        if not has or mx < need:
            mx = max(need, mx if mx > 0 else (cfg_max if cfg_max > 0 else 10))
        if sticks and level < need:
            level = min(need, mx)
        if not sticks or level < need:
            return "FAILED", level, mx
        return ("REPAIRED" if has else "UNLOCKED"), level, mx

    check("sim: absent -> UNLOCKED 1/10; 0/0 -> REPAIRED 1/10; healthy 3/8 READY; stuck -> FAILED",
          _fix(False, 0, 0, 1) == ("UNLOCKED", 1, 10) and _fix(True, 0, 0, 1) == ("REPAIRED", 1, 10)
          and _fix(True, 3, 8, 1) == ("READY", 3, 8) and _fix(True, 0, 0, 1, sticks=False)[0] == "FAILED"
          and _fix(False, 0, 0, 0)[1] == 1)

    print("\n--- § chamber endless waves off 4.7.29 ---")
    ew_mx = read(SRC / "mixin/dmzultra/DmzultraChamberWaveCooldownMixin.java")
    ew = read(SRC / "progression/ChamberWaveCooldown.java")
    ew_body = _after(ew, "public static boolean refuseEndless(").split("\n    }\n", 1)[0]
    check("manifest chamber-endless-waves-off row", "chamber-endless-waves-off" in manifest)
    check("same start HEAD hook refuses waves <= 0 before the cooldown; drills / demands exempt",
          "ChamberWaveCooldown.refuseEndless(player, config.waves(), config.drill())" in ew_mx
          and ew_mx.index("refuseEndless(") < ew_mx.index("refuseStart(")
          and "drill > 0 || waves > 0" in ew_body)
    check("action-bar refusal, no op exemption, no packet or screen",
          '"Endless waves are disabled. Pick a number of waves."' in ew_body and ", true);" in ew_body
          and "isOp" not in ew_body and "Packet" not in ew_body)

    def _endless(waves, drill):  # mirror of refuseEndless
        return not (drill > 0 or waves > 0)

    check("sim: waves 0 / -1 refused, 5 allowed, drill with 0 allowed",
          _endless(0, 0) and _endless(-1, 0) and not _endless(5, 0) and not _endless(0, 3))

    print("\n--- § wallet god ki / divine ki 4.7.29 ---")
    kt = read(SRC / "progression/shop/KiMasteryTrack.java")
    kt_mw = read(SRC / "progression/shop/MasteryWallet.java")
    kt_gk = read(SRC / "progression/shop/GodKiEnhancementShop.java")
    kt_pp = read(SRC / "progression/shop/PrestigePointsSystem.java")
    kt_scr = read(SRC / "gui/ultra/UltraPrestigeScreens.java")
    kt_reapply = _after(kt, "public void reapply(ServerPlayer player) {").split("\n    }\n", 1)[0]
    kt_open = _after(kt, "public static boolean divineKiOpen(ServerPlayer player) {").split("\n    }\n", 1)[0]
    kt_has = _after(kt_gk, "public static boolean hasEnhancement(ServerPlayer player) {").split("\n    }\n", 1)[0]
    kt_lines = _after(kt_mw, "public static List<Line> kiLines(ServerPlayer player) {").split("\n    }\n", 1)[0]
    kt_apply = _after(kt_mw, "private static String applyGodKi(")
    check("manifest wallet-godki-divineki row", "wallet-godki-divineki" in manifest)
    check("God Ki line feeds godki:godki on its own key; Divine Ki feeds godki:divineki on a new key",
          'GOD_KI("pp_godki_form", "godki", "God Ki")' in kt and 'DIVINE_KI("pp_divineki", "divineki", "Divine Ki")' in kt
          and 'public static final String GROUP = "godki";' in kt
          and "new Line(KiMasteryTrack.GROUP, track.label(), true" in kt_mw)
    check("old pp_godki_enh levels not read, converted or deleted by the wallet",
          "pp_godki_enh" not in _after(kt, "*/") and "GodKiEnhancementShop.invested(" not in kt_mw
          and "levelUpFromWallet" not in kt_mw and "storedRemove" not in kt)
    check("same scale and cost: godKi shop cap / mastery per level, 10 points a level",
          "return Math.max(0, GodKiEnhancementShop.maxInvestments());" in kt
          and "return Math.max(1, GodKiEnhancementShop.masteryPerPoint());" in kt
          and "points != GODKI_POINTS_PER_LEVEL" in kt_apply)
    check("level raises stack mastery to level x per level, never lowers, cap at least the level",
          "masteries.getMastery(GROUP, form) >= expected" in kt_reapply
          and "masteries.setMastery(GROUP, form, expected, Math.max(expected, configCap()));" in kt_reapply
          and "character.getStackFormMasteries()" in kt)
    check("Divine Ki hidden until God Ki is mastered (level cap or full godki mastery)",
          "GOD_KI.level(player) >= max" in kt_open
          and "GOD_KI.formMastery(player) >= (double) max * masteryPerLevel()" in kt_open
          and "if (KiMasteryTrack.divineKiOpen(player)) {" in kt_lines
          and kt_lines.index("GOD_KI") < kt_lines.index("DIVINE_KI"))
    check("level lines have their own tokens; real godkienhancement and godki lines dropped",
          "return LEVEL_TOKEN_PREFIX + track.form;" in kt_mw
          and "KiMasteryTrack.GROUP.equalsIgnoreCase(l.group)" in kt_mw)
    check("God forms gate: a wallet God Ki level keeps hasEnhancement true",
          kt_has.index("KiMasteryTrack.GOD_KI.level(player) >= 1") < kt_has.index("DmzSkillUtil.skills(player)"))
    check("login / respawn top-up covers both tracks",
          "KiMasteryTrack.reapplyAll(player);" in kt_pp and "for (KiMasteryTrack track : values())" in kt)
    check("screens read the line's own title; Divine Ki page has no God forms line",
          'line.kiName() + " Level "' in kt_scr and '"Confirm " + line.label + " level"' in kt_scr
          and 'boolean god = line.track == KiMasteryTrack.GOD_KI;' in kt_scr)
    check("trial, grantGodKi and fighting gain untouched by the tracks",
          all(s not in _after(kt, "*/") for s in ("grantGodKi", "GodChamber", "masteryPerHit", "AngelPath")))

    def _kt(level, current, per=1000):  # mirror of KiMasteryTrack.reapply
        want = level * per
        return current if level <= 0 or current >= want else want

    def _open(god_level, god_mastery, cap=5, per=1000):  # mirror of divineKiOpen
        return cap > 0 and (god_level >= cap or god_mastery >= cap * per)

    check("sim: level 1 from 40 -> 1000, level 2 from 3500 stays, level 0 untouched; Divine opens at 5/5 or 5000",
          _kt(1, 40) == 1000 and _kt(2, 3500) == 3500 and _kt(0, 12) == 12
          and _open(5, 0) and _open(0, 5000) and not _open(4, 4999))

    print("\n--- § chamber wave cooldown 4.7.28 ---")
    wc_mx = read(SRC / "mixin/dmzultra/DmzultraChamberWaveCooldownMixin.java")
    wc = read(SRC / "progression/ChamberWaveCooldown.java")
    wc_cfg = read(SRC / "config/DifficultyConfig.java")
    wc_plugin = read(SRC / "mixin/LegacyMechanicsMixinPlugin.java")
    wc_json = read(SRC.parents[3] / "resources/legacymechanics.mixins.json")
    wc_refuse = _after(wc, "public static boolean refuseStart(").split("\n    }\n", 1)[0]
    wc_stop = _after(wc_mx, "lm$recordChamberWaveEnd(").split("\n    }\n", 1)[0]
    check("manifest chamber-wave-cooldown row", "chamber-wave-cooldown" in manifest)
    check("config chamberWaveCooldownSeconds default 120, clamped >= 0, registered, explained",
          "public int chamberWaveCooldownSeconds = 120;" in wc_cfg
          and "cfg.chamberWaveCooldownSeconds = Math.max(0, cfg.chamberWaveCooldownSeconds);" in wc_cfg
          and '"chamberWaveCooldownSeconds"' in read(SRC / "config/ConfigRegistry.java")
          and "chamberWaveCooldownSeconds=" in read(SRC / "config/editor/ConfigFieldHelp.java"))
    check("cancellable HEAD inject on start; end recorded at HEAD of stop by status mode",
          '@Inject(method = "start", at = @At("HEAD"), cancellable = true' in wc_mx
          and "ci.cancel();" in wc_mx and '@Inject(method = "stop", at = @At("HEAD")' in wc_mx
          and "GodChamberWaves.status(player)" in wc_stop
          and "GodChamberWaves.Status.TRIAL" in wc_stop and "GodChamberWaves.Status.TRAINING" in wc_stop
          and "DRILL" not in wc_stop)
    check("drills / demands and ops exempt; separate training and trial timers",
          "drill > 0 || StaffAccess.isOp(player)" in wc_refuse
          and "int kind = trial ? TRIAL : TRAINING;" in wc_refuse
          and "config.trial(), config.drill()" in wc_mx)
    check("refusal is an action-bar line with m:ss, no packet or screen",
          "player.m_5661_(" in wc_refuse and ".m_130940_(ChatFormatting.YELLOW), true);" in wc_refuse
          and "can start again in" in wc_refuse
          and "Packet" not in wc_mx + wc and "sendState" not in wc_mx + wc)
    check("durable: LM-owned JSON written on every end (temp + atomic move), loaded lazily",
          'resolve("chamber-wave-cooldown.json")' in read(SRC / "config/ConfigPaths.java")
          and "ConfigPaths.chamberWaveCooldownPath()" in wc and "save(map);" in wc
          and "StandardCopyOption.ATOMIC_MOVE" in wc and "Files.newBufferedReader(file)" in wc)
    check("mixin listed and gated on the Ultra chamber classes",
          '"dmzultra.DmzultraChamberWaveCooldownMixin"' in wc_json
          and 'endsWith("DmzultraChamberWaveCooldownMixin")' in wc_plugin)
    check("no TP / god-ki / foe / wave changes in the cooldown mixin",
          all(s not in _after(wc_mx, "*/") for s in ("perFoe", "payOut", "grantGodKi", "spawnWave", "spec(", "@Redirect")))

    def _fmt(ms):  # mirror of ChamberWaveCooldown.formatLeft
        t = max(1, (ms + 999) // 1000)
        return f"{t // 60}:{t % 60:02d}"

    def _left(last, now, cd_s):  # mirror of remaining()
        cd = max(0, cd_s) * 1000
        return 0 if cd <= 0 or last <= 0 else max(0, last + cd - now)

    check("sim: 2:00 just after end, 1:05 at 55s, free at 120s, off at 0, m:ss rounding",
          _fmt(_left(1000, 1000, 120)) == "2:00" and _fmt(_left(1000, 56_000, 120)) == "1:05"
          and _left(1000, 121_000, 120) == 0 and _left(1, 2, 0) == 0 and _left(0, 5, 120) == 0
          and _fmt(8_001) == "0:09")

    print("\n--- § grant first group discovery 4.7.27 ---")
    gd_select = _after(gf_choice, "public static Candidate select(").split("\n    }\n", 1)[0]
    gd_name = _after(gf_choice, "private static Candidate earliestByName(").split("\n    }\n", 1)[0]
    check("manifest grant-first-group-discovery row", "grant-first-group-discovery" in manifest)
    check("tier match on group name or formType for all three kinds",
          "contains(candidate.group, token) || contains(candidate.formType, token)" in gf_choice
          and 'return inTier(candidate, "superform");' in gf_choice
          and 'return inTier(candidate, "legendaryform");' in gf_choice
          and 'return inTier(candidate, "godform");' in gf_choice)
    check("name fallback only for god / legendary, only when no tier match; SUPER floor kept",
          'return tier != null ? tier : earliestByName(forms, kind == Kind.GOD ? "god" : "legendary");' in gd_select
          and "return earliest(forms, Kind.SUPER, 1);" in gd_select
          and gd_select.count("earliestByName") == 1)
    check("name fallback skips God Ki toggle, duktemp and test groups, and stack forms",
          'java.util.Set.of("godkienhancement", "duktemp", "testformstype")' in gf_choice
          and "NAME_FALLBACK_EXCLUDED_TYPES.contains(candidate.formType.toLowerCase(Locale.ROOT))" in gd_name
          and "candidate.stack" in gd_name and "contains(candidate.form, token)" in gd_name
          and "better(candidate, best)" in gd_name)
    check("no-form messages unchanged",
          'fail("No legendary form is set for race " + race + ".")' in gf_grant
          and 'fail("Race " + race + " has no god form to grant mastery to.")' in gf_grant)

    _TOK = {"SUPER": "superform", "LEGENDARY": "legendaryform", "GOD": "godform"}
    _SKIP = {"godkienhancement", "duktemp", "testformstype"}

    def _pick2(forms, kind):  # mirror of 4.7.27 select: (group, form, formType, level)
        key = lambda f: (f[3], f[0].lower(), f[1].lower())
        ok = [f for f in forms if (_TOK[kind] in f[0].lower() or _TOK[kind] in f[2].lower())
              and (kind != "SUPER" or f[3] >= 1)]
        if not ok and kind != "SUPER":
            word = "god" if kind == "GOD" else "legendary"
            ok = [f for f in forms if f[2].lower() not in _SKIP and word in f[1].lower()]
        return min(ok, key=key)[1] if ok else None

    ancient = [("primal_ssjgrades", "supersaiyan", "superforms", 1), ("primalssj", "primal_ssj", "legendaryforms", 1),
               ("primalssj", "primalgod", "legendaryforms", 3),
               ("godkienhancement", "enhancewithgodki", "godkienhancement", 1),
               ("duktemp", "lord_of_nothing", "duktemp", 999)]
    angel = [("legendaryforms", "unboundhalo", "unboundforms", 1), ("godforms", "highangel", "godforms", 1)]
    fullbringer = [("fullbringerforms", "partial", "superforms", 1),
                   ("godkienhancement", "enhancewithgodki", "godkienhancement", 1)]
    check("sim: ancient_saiyan god -> primalgod (not the God Ki toggle); angel legendary by group; "
          "fullbringer god/legendary still none; Saiyan picks unchanged",
          _pick2(ancient, "GOD") == "primalgod" and _pick2(ancient, "LEGENDARY") == "primal_ssj"
          and _pick2(angel, "LEGENDARY") == "unboundhalo" and _pick2(angel, "GOD") == "highangel"
          and _pick2(fullbringer, "GOD") is None and _pick2(fullbringer, "LEGENDARY") is None
          and all(_pick2(saiyan, k) == _pick(saiyan, k) for k in _TOK))

    print("\n--- § chamber wave tp 4.7.24 ---")
    cw_mx = read(SRC / "mixin/dmzultra/DmzultraChamberWaveTpMixin.java")
    cw_pv = read(SRC / "mixin/dmzultra/DmzultraTimeChamberTpPreviewMixin.java")
    cw = read(SRC / "progression/ChamberWaveTp.java")
    cw_cfg = read(SRC / "config/DifficultyConfig.java")
    cw_json = read(SRC.parents[3] / "resources/legacymechanics.mixins.json")
    cw_plugin = read(SRC / "mixin/LegacyMechanicsMixinPlugin.java")
    cw_scale = _after(cw, "public static long scaleWaveTp(").split("\n    }\n", 1)[0]
    cw_s = _after(cw, "static long scale(long perFoe) {").split("\n    }\n", 1)[0]
    check("manifest chamber-wave-tp row", "chamber-wave-tp" in manifest)
    check("manifest chamber-wave-tp-default row", "chamber-wave-tp-default" in manifest)
    check("config chamberWaveTpMultiplier default 0.025 (4.7.28), clamped 0-1, registered, explained",
          "public double chamberWaveTpMultiplier = 0.025;" in cw_cfg
          and "cfg.chamberWaveTpMultiplier)) : 0.025;" in cw_cfg
          and "Math.min(1.0, m)) : 0.025;" in cw
          and "0.025 = 2.5%" in read(SRC / "config/editor/ConfigFieldHelp.java")
          and "Math.max(0.0, Math.min(1.0, cfg.chamberWaveTpMultiplier))" in cw_cfg
          and '"chamberWaveTpMultiplier"' in read(SRC / "config/ConfigRegistry.java")
          and "chamberWaveTpMultiplier=" in read(SRC / "config/editor/ConfigFieldHelp.java"))
    check("mixin scales the perFoe call in step only",
          'method = "step"' in cw_mx and "GodChamberWaves;perFoe(JIZ)J" in cw_mx
          and "payOut" not in _after(cw_mx, "*/") and "spawnWave" not in cw_mx and "spec(" not in cw_mx)
    check("floor of 1 TP per foe", "Math.max(1L, Math.round(perFoe * mult))" in cw_s)
    check("both mixins listed and gated on the Ultra chamber classes",
          '"dmzultra.DmzultraChamberWaveTpMixin"' in cw_json and '"dmzultra.DmzultraTimeChamberTpPreviewMixin"' in cw_json
          and "dmzultraChamberPresent()" in cw_plugin
          and 'getResource("com/dmzultra/divine/GodChamberWaves.class")' in cw_plugin)
    check("no other TP path reads the chamber multiplier",
          {p.name for p in SRC.rglob("*.java") if "ChamberWaveTp." in read(p)}
          == {"DmzultraChamberWaveTpMixin.java", "DmzultraTimeChamberTpPreviewMixin.java",
              "DmzultraGodChamberTpPreviewMixin.java"})

    def _cw(base, mult=0.025, trial=False):  # mirror of ChamberWaveTp (trial scaled since 4.7.26)
        if mult >= 1.0 or base <= 0:
            return base
        return max(1, int(base * mult + 0.5))

    check("sim: 1200 -> 30 at the 0.025 default, 1200 -> 120 at 0.1, 4 -> 1 (floor), 0.0 -> 1, 1.0 -> unchanged",
          _cw(1200) == 30 and _cw(1200, 0.1) == 120 and _cw(4) == 1 and _cw(50, 0.0) == 1 and _cw(1200, 1.0) == 1200)

    print("\n--- § chamber wave tp trial 4.7.26 ---")
    check("manifest chamber-wave-tp-trial row", "chamber-wave-tp-trial" in manifest)
    check("every chamber run scaled: no trial record, no start inject, no early return",
          "return scale(perFoe);" in cw_scale and "TRIAL_RUN" not in cw and "recordRun" not in cw
          and 'method = "start"' not in cw_mx and "@Inject" not in cw_mx
          and "ChamberWaveTp.scaleWaveTp(base)" in cw_mx)
    check("god-ki award, foe sizing and wave spawns untouched by the wave mixin",
          "GodKi" not in _after(cw_mx, "*/") and "godKi" not in _after(cw_mx, "*/")
          and "spawnWave" not in cw_mx and "spec(" not in cw_mx and "buildTotalMultiplier" not in cw)
    cw_gpv = read(SRC / "mixin/dmzultra/DmzultraGodChamberTpPreviewMixin.java")
    check("Popo's and Whis's menu TP per foe both scaled like the payout",
          'targets = "com.dmzultra.divine.TimeChamber"' in cw_pv and "ChamberWaveTp.scalePreview" in cw_pv
          and 'targets = "com.dmzultra.divine.GodChamber"' in cw_gpv and 'method = "sendState"' in cw_gpv
          and "GodChamberWaves;tpPerFoe(" in cw_gpv and "ChamberWaveTp.scalePreview" in cw_gpv
          and '"dmzultra.DmzultraGodChamberTpPreviewMixin"' in cw_json
          and 'endsWith("DmzultraGodChamberTpPreviewMixin")' in cw_plugin
          and 'getResource("com/dmzultra/divine/GodChamber.class")' in cw_plugin)
    check("config help no longer says the God Trial is unaffected",
          "God Trial is not affected" not in read(SRC / "config/editor/ConfigFieldHelp.java")
          and "God Trial is never scaled" not in cw_cfg)
    check("sim: a trial foe paying 1200 now pays 30, the same as a training foe",
          _cw(1200, trial=True) == 30 == _cw(1200))

    print("\n--- § revive cooldown and all 4.7.23 ---")
    rv = read(SRC / "command/LmRevive.java")
    rv_staff = read(SRC / "util/StaffAccess.java")
    rv_cfg = read(SRC / "config/DifficultyConfig.java")
    rv_lit = _after(rv, "static LiteralArgumentBuilder<CommandSourceStack> literal() {").split("\n    }\n", 1)[0]
    rv_all = _after(rv, "private static int reviveAll(").split("\n    }\n", 1)[0]
    rv_dead = _after(rv, "private static List<ServerPlayer> deadOnlinePlayers(").split("\n    }\n", 1)[0]
    rv_cd = _after(rv, "private static boolean onCooldown(").split("\n    }\n", 1)[0]
    rv_start = _after(rv, "private static void startCooldown(").split("\n    }\n", 1)[0]
    rv_call = _after(rv, "private static int revive(CommandSourceStack source, Collection<ServerPlayer> targets) {").split("\n    }\n", 1)[0]
    rv_op = _after(rv_staff, "public static boolean isOp(ServerPlayer player) {").split("\n    }\n", 1)[0]
    check("manifest revive-cooldown-all row", "revive-cooldown-all" in manifest)
    check("config reviveCooldownSeconds default 300, clamped, registered, explained",
          "public int reviveCooldownSeconds = 300;" in rv_cfg and "cfg.reviveCooldownSeconds = Math.max(0," in rv_cfg
          and '"reviveCooldownSeconds"' in read(SRC / "config/ConfigRegistry.java")
          and "reviveCooldownSeconds=" in read(SRC / "config/editor/ConfigFieldHelp.java"))
    check("isOp is op level 2 or Bukkit op only (no admin node, not isStaff)",
          "m_20310_(2)" in rv_op and "hasBukkitIsOp(player)" in rv_op
          and "adminPermission" not in rv_op and "isStaff" not in rv_op and "hasBukkitPermission" not in rv_op)
    check("cooldown exempts ops only, keyed by executor UUID, human time-left message",
          "StaffAccess.isOp(executor)" in rv_cd and "isStaff" not in rv_cd
          and "COOLDOWN_UNTIL.get(executor.m_20148_())" in rv_cd
          and "Revive is on cooldown — try again in " in rv_cd and "LmCommandFeedback.tell" in rv_cd
          and "StaffAccess.isOp(executor)" in rv_start and "reviveCooldownSeconds" in rv_start)
    check("cooldown starts only on a DMZ count above 0; every form checks it before reviving",
          "if (revived > 0) {\n                startCooldown(source);" in rv_call
          and rv.count("startCooldown(source);") == 1
          and rv.count("if (onCooldown(source)) {") == 3
          and rv_all.index("onCooldown(source)") < rv_all.index("revive(source, dead)"))
    check("revive all: literal before the player word, revive-others gate, dead online players only",
          rv_lit.index('Commands.m_82127_("all")') < rv_lit.index('playerWord("player")')
          and "StaffAccess.denyUnlessReviveOthers(source) == 0" in rv_all
          and "hasSelfRevive" not in rv_all
          and "m_6846_().m_11314_()" in rv_dead and "!stats.getStatus().isAlive()" in rv_dead
          and "NOBODY_DEAD" in rv_all and "No one is dead right now." in rv
          and rv_all.index("dead.isEmpty()") < rv_all.index("revive(source, dead)"))
    check("one reflective revivePlayers call takes the collection; no /dmzrevive dispatch",
          "method.invoke(null, source, targets)" in rv_call and rv.count(".invoke(") == 1
          and "revive(source, List.of(player))" in rv and "revive(source, List.of(target))" in rv
          and "performPrefixedCommand" not in rv and ".execute(" not in rv)
    check("help and tab completion list /lm revive all",
          '"/lm revive all"' in read(SRC / "command/LmCommandHelp.java")
          and 'names.add("all");' in read(SRC / "command/LmCommandSuggestions.java")
          and "LmCommandSuggestions.filterPlayerSlot(label, coalesced, source, partial)"
          in read(SRC / "command/MohistCommandBridge.java"))

    def _left(ms):  # mirror of LmRevive.formatLeft
        total = max(1, (ms + 999) // 1000)
        m, s = divmod(total, 60)
        return f"{s}s" if m == 0 else (f"{m}m" if s == 0 else f"{m}m {s}s")

    check("sim: time left reads 3m 12s / 5m / 45s / 1s",
          _left(192000) == "3m 12s" and _left(300000) == "5m" and _left(44001) == "45s" and _left(1) == "1s")

    print("\n--- § god ki refund order 4.7.22 ---")
    ro_gk = read(SRC / "progression/shop/GodKiEnhancementShop.java")
    ro_up = _after(ro_gk, "public static int levelUpFromWallet(ServerPlayer player) {").split("\n    }\n", 1)[0]
    ro_admin = _after(ro_gk, "static void adminSetInvested(ServerPlayer player, int count) {").split("\n    }\n", 1)[0]
    ro_refund = _after(ro_gk, "static void refundEffectsInvestment(ServerPlayer player) {").split("\n    }\n", 1)[0]
    ro_login = _after(ro_gk, "public static void refundOnLogin(ServerPlayer player) {").split("\n    }\n", 1)[0]
    ro_apply = read(SRC / "progression/shop/MasteryWallet.java").split("private static String applyGodKi(", 1)[-1]
    ro_events = read(SRC / "event/DifficultyEvents.java").split("public void onLogin(", 1)[-1].split("@SubscribeEvent", 1)[0]
    check("manifest godki-refund-order row", "godki-refund-order" in manifest)
    check("wallet level, staff set and wallet apply refund before the level write",
          ro_up.index("refundEffectsInvestment(player);") < ro_up.index("storeLevel(player, next);")
          and ro_admin.index("refundEffectsInvestment(player);") < ro_admin.index("storeLevel(player, clamped);")
          and ro_apply.index("GodKiEnhancementShop.refundEffectsInvestment(player);")
          < ro_apply.index("int level = track.level(player);")
          < ro_apply.index("int next = track.levelUp(player);"))
    check("refund waits for the character before writing anything",
          ro_refund.index("character(player) == null") < ro_refund.index("storedPutBool(player, KEY_REFUND_DONE, true)"))
    check("login trigger: onLogin -> refundOnLogin, now plus retries at 20 / 100 / 300 ticks",
          "GodKiEnhancementShop.refundOnLogin(player);" in ro_events
          and "refundEffectsInvestment(player);" in ro_login and "{20, 100, 300}" in ro_login
          and "refundEffectsInvestment(p);" in ro_login)
    check("probe covers the wallet-first order",
          "GodKiEnhancementShop.levelUpFromWallet(fp);" in read(SRC / "progression/GodKiWalletProbe.java"))

    print("\n--- § spend confirm pages 4.7.21 ---")
    sc_pre = read(SRC / "gui/ultra/UltraPrestigeScreens.java")
    sc_pre_src, sc_pre_ask = sc_pre.split("// ── Confirm pages (ask:<action>:<arg>, 4.7.21)", 1)
    sc_diff = read(SRC / "gui/ultra/UltraDifficultyScreens.java")
    sc_diff_src, sc_diff_ask = sc_diff.split("// ── Confirm pages (4.7.21)", 1)
    sc_char = read(SRC / "gui/ultra/UltraCharacterScreens.java")
    sc_char_ask = _after(sc_char, "private static void openUnlockAsk(").split("private static int parseBonePage(", 1)[0]
    sc_char_src = sc_char.replace(sc_char_ask, "")
    sc_actions = read(SRC / "gui/ultra/UltraGuiActions.java")
    sc_reopen = _after(sc_actions, "private static String reopenPage(")
    sc_dact = read(SRC / "service/DifficultyActions.java")
    sc_settier = _after(sc_dact, "private static Result setTier(").split("private static Result lowerTier(", 1)[0]
    spend_do = re.compile(r'"do:(turnin|skill|tier|majin|mutant|unmajin|unmutant|ultimate|custom_buy|custom_remove'
                          r'|mastery_buy|mastery_apply|godki)\b')
    check("manifest spend-confirm-pages row", "spend-confirm-pages" in manifest)
    check("GuiClickConfirm guards no spend: shops, wallet, God Ki, paid tier activation",
          all("GuiClickConfirm" not in read(SRC / ("progression/shop/" + n + ".java"))
              for n in ("MasteryWallet", "GodKiEnhancementShop", "PrestigePointsSystem", "PrestigeCustomShop"))
          # 4.7.57: the free switch down is confirmed on tier_ask too, so setTier has no click-again guard.
          and "GuiClickConfirm.confirmed" not in sc_settier)
    check("Prestige spend buttons open ask: pages; do: spend actions only on confirm pages",
          all(t in sc_pre_src for t in ('"page:ask:turnin:"', '"page:ask:skill:"', '"page:ask:tier:"',
                                        '"page:ask:majin"', '"page:ask:mutant"', '"page:ask:unmajin"',
                                        '"page:ask:unmutant"', '"page:ask:ultimate"', '"page:ask:custom_buy:"',
                                        '"page:ask:custom_remove:"', '"page:ask:mastery_buy:"',
                                        '"page:ask:mastery_apply:"'))
          and not spend_do.search(sc_pre_src)
          and all(t in sc_pre_ask for t in ('"do:turnin:"', '"do:skill:"', '"do:tier:"', '"do:custom_buy:"',
                                            '"do:custom_remove:"', '"do:mastery_buy:"', '"do:mastery_apply:"',
                                            '"do:ultimate"', '"do:unmajin"')))
    check("exchange confirm shows prestiges in and favor out from pointsForTurnIn",
          '.labelValue("You give", plural(amount, "prestige", "prestiges"))' in sc_pre_ask
          and '.labelValue("You get", gain + " favor")' in sc_pre_ask
          and "PrestigePointsSystem.pointsForTurnIn(amount)" in sc_pre_ask)
    check("confirm pages show the cost and only offer Confirm when nothing blocks it; Back to the source",
          "if (block == null) {" in sc_pre_ask and "spec.back();" in sc_pre_ask  # 4.7.71: registry parent
          and sc_pre_ask.count('.labelValue("Cost"') >= 5
          and '"exchange", "Trade"' in sc_pre_ask and '"mastery_wallet", "Mastery wallet"' in sc_pre_ask)
    check("Difficulty: coin tier upgrades -> tier_ask, summons -> summon_ask; do: only on confirm pages",
          'String action = "page:tier_ask:" + t;' in sc_diff_src and '"do:activate:' not in sc_diff_src
          and '"page:summon_ask:end_dragon"' in sc_diff_src and '"page:summon_ask:" + page' in sc_diff_src
          and '"do:summon' not in sc_diff_src
          and '"do:activate:" + t' in sc_diff_ask and '"do:summon_end_dragon"' in sc_diff_ask
          and '"do:summon_wither"' in sc_diff_ask and '"do:summon_warden"' in sc_diff_ask and '.labelValue("Cost"' in sc_diff_ask)
    check("Character: Ancient Coin unlocks -> part_ask / bone_ask confirm pages",
          '"page:bone_ask:" + pg + ":" + id' in sc_char_src and '"page:part_ask:" + token + ":" + id' in sc_char_src
          and '"do:bone_unlock:' not in sc_char_src and '"do:bodypart_unlock:' not in sc_char_src
          and '"do:bodypart_unlock:" : "do:bone_unlock:"' in sc_char_ask and '.labelValue("Cost"' in sc_char_ask)
    check("Confirm lands back on the source page (confirmReturn before the page echo)",
          "String afterConfirm = confirmReturn(screen, page);" in sc_reopen
          and sc_reopen.index("confirmReturn(screen, page)") < sc_reopen.index('if (!page.isBlank() && !"config".equals(screen)')
          and 'case "prestige" -> UltraPrestigeScreens.askReturn(page);' in sc_actions
          and 'case "difficulty" -> UltraDifficultyScreens.askReturn(page);' in sc_actions
          and 'case "character", "saga" -> UltraCharacterScreens.askReturn(page);' in sc_actions)

    print("\n--- § god ki mastery wallet 4.7.21 ---")
    gw_mw = read(SRC / "progression/shop/MasteryWallet.java")
    gw_gk = read(SRC / "progression/shop/GodKiEnhancementShop.java")
    gw_refund = _after(gw_gk, "static void refundEffectsInvestment(ServerPlayer player) {").split("\n    }\n", 1)[0]
    gw_reapply = _after(gw_gk, "public static void reapply(ServerPlayer player) {").split("\n    }\n", 1)[0]
    gw_store = _after(gw_gk, "private static void storeLevel(ServerPlayer player, int level) {").split("\n    }\n", 1)[0]
    gw_apply = _after(gw_mw, "private static String applyGodKi(")
    check("manifest godki-mastery-wallet row", "godki-mastery-wallet" in manifest)
    check("God Ki line: always listed first, real godkienhancement line dropped, own rate (masteryPerPoint / 10)",
          "public static final int GODKI_POINTS_PER_LEVEL = 10;" in gw_mw
          and "return Math.max(1, masteryPerLevel / GODKI_POINTS_PER_LEVEL);" in gw_mw
          and "List<Line> out = new ArrayList<>(levelLines);" in gw_mw
          and "ENHANCEMENT_GROUP.equalsIgnoreCase(l.group)" in gw_mw
          and gw_mw.count("return withGodKi(") == 4 and "MASTERY_PER_POINT = 50;" in gw_mw)
    check("God Ki apply: exactly 10 points a level, cap, on-sale, balance; level on its track (4.7.29)",
          "points != GODKI_POINTS_PER_LEVEL" in gw_apply and "level >= max" in gw_apply
          and "PrestigeShopConfig.get().godKi.enabled" in gw_apply and "balance < points" in gw_apply
          and "int next = track.levelUp(player);" in gw_apply
          and 'KEY_INVESTED = "pp_godki_enh";' in gw_gk
          and "storeLevel(player, next);\n        reapply(player);" in gw_gk)
    check("Effects refund: marker first in the same record, n favor back, level 0, God Ki wiped, flushed",
          'KEY_REFUND_DONE = "pp_godki_wallet_refund";' in gw_gk
          and "ProgressionData.storedGetBool(player, KEY_REFUND_DONE)" in gw_refund
          and gw_refund.index("storedPutBool(player, KEY_REFUND_DONE, true)") < gw_refund.index("storedPut(player, KEY_INVESTED, 0)")
          and "PrestigePointsSystem.getPoints(player) + levels" in gw_refund
          and 'GodKiWipeHelper.wipe(player, "godki_wallet_refund")' in gw_refund
          and "LmPlayerStore.flush(player.m_20148_())" in gw_refund and "PrestigeCharacter.flush(player)" in gw_refund)
    check("every level write sets the marker; refund runs from reapply once the character is attached",
          "ProgressionData.storedPutBool(player, KEY_REFUND_DONE, true);" in gw_store
          and "storeLevel(player, clamped);" in gw_gk
          and gw_reapply.index("if (character == null) {") < gw_reapply.index("refundEffectsInvestment(player);")
          < gw_reapply.index("int investments = invested(player);"))
    check("Effects page has no God Ki row; old do:godki only points to the wallet",
          '"do:godki"' not in sc_pre and "godki_invested" not in sc_pre
          and "God Ki Mastery moved to the Mastery wallet" in gw_gk
          and "ProgressionData.storedPut(player, KEY_INVESTED" not in _after(gw_gk, "public static String buy(").split("static int levelUpFromWallet", 1)[0])

    print("\n--- § ultimate not refundable 4.7.21 ---")
    un_pp = read(SRC / "progression/shop/PrestigePointsSystem.java")
    check("manifest ultimate-not-refundable row", "ultimate-not-refundable" in manifest)
    check("no unbuyUltimate, no Remove Ultimate; buy stays; Majin/Mutant remove stays behind ask pages",
          "unbuyUltimate" not in un_pp and "Remove Ultimate" not in sc_pre and '"do:unultimate"' not in sc_pre
          and "public static String buyUltimate(ServerPlayer player)" in un_pp
          and "ultimate = new FormOffer(3, 100)" in read(SRC / "progression/shop/PrestigeShopConfig.java")
          and '"page:ask:unmajin"' in sc_pre and '"page:ask:unmutant"' in sc_pre)

    print("\n--- § ultra chamber exempt 4.7.20 ---")
    ch_ms = read(SRC / "scaling/MobScaling.java")
    ch_exempt = _after(ch_ms, "public static boolean isExemptFromConversion(").split("\n    }\n", 1)[0]
    ch_ensure = _after(ch_ms, "public static void ensureExempt(").split("\n    }\n", 1)[0]
    ch_owned = _after(ch_ensure, "boolean owned =").split("if (owned)", 1)[0]
    check("manifest ultra-chamber-exempt row", "ultra-chamber-exempt" in manifest)
    check("TAG_CHAMBER_ENEMY is Ultra's dmzultra_chamber_enemy tag",
          'TAG_CHAMBER_ENEMY = "dmzultra_chamber_enemy"' in ch_ms)
    check("chamber tag exempts from conversion, no config toggle",
          "tag.m_128471_(TAG_CHAMBER_ENEMY)" in ch_exempt and "config" not in ch_exempt.lower())
    check("chamber tag is owned in ensureExempt (strip paint, never revertToBases)",
          "TAG_CHAMBER_ENEMY" in ch_owned and "revertToBases" not in ch_owned)

    print("\n--- § quest technique unlock 4.7.19 ---")
    qt_mix = read(SRC / "mixin/StatsDataQuestTechniqueMixin.java")
    qt_json = read(MOD / "src/main/resources/legacymechanics.mixins.json")
    qt_build = read(MOD / "build.sh")
    qt_ret = _after(qt_mix, "private void lm$unlockGrantedTechnique(")
    check("manifest quest-technique-unlock row", "quest-technique-unlock" in manifest)
    check("mixin registered and shipped: grantSkillLevel HEAD + RETURN, require = 0",
          '"StatsDataQuestTechniqueMixin"' in qt_json and "StatsDataQuestTechniqueMixin \\" in qt_build
          and qt_mix.count('@Inject(method = "grantSkillLevel"') == 2 and qt_mix.count("require = 0") == 2)
    check("only 0 -> 1+; ki, strike, evasion registries in DMZ's order; template copied via save/load",
          "before != 0 || level < 1" in qt_ret
          and qt_ret.index("PredefinedTechniques.REGISTRY") < qt_ret.index("PredefinedTechniques.STRIKE_REGISTRY")
          < qt_ret.index("PredefinedTechniques.EVASION_REGISTRY")
          and qt_ret.count(".save());") == 3 and "toLowerCase(Locale.ROOT)" in qt_ret)
    check("base techniques only; already unlocked skipped; failures caught",
          "getBaseTechniques()" in qt_ret and "getUnlockedTechniques().containsKey(copy.getId())" in qt_ret
          and "catch (Throwable t)" in qt_ret and "getTechniques()" not in qt_ret.replace("getBaseTechniques()", ""))

    print("\n--- § prestige cap held sync 4.7.18 ---")
    hs_ps = read(SRC / "progression/shop/PrestigeSystem.java")
    hs_pps = read(SRC / "progression/shop/PrestigePointsSystem.java")
    hs_br = read(SRC / "progression/bridge/DmzRevampPrestigeBridge.java")
    hs_prog = read(SRC / "progression/ProgressionSystem.java")
    hs_mx = read(SRC / "mixin/DmzRevampPrestigeCapMixin.java")
    hs_int = read(SRC / "mixin/PrestigeServiceIntegrationMixin.java")
    hs_set = _after(hs_ps, "public static void setHeldPublic(ServerPlayer player, int value) {").split("\n    }\n", 1)[0]
    hs_turn = _after(hs_pps, "public static String turnIn(ServerPlayer player, int amount) {").split("\n    }\n", 1)[0]
    hs_rec = _after(hs_br, "public static void reconcile(ServerPlayer player) {").split("\n    }\n", 1)[0]
    hs_blk = _after(hs_mx, "private static boolean lm$blocked(StatsData data) {").split("\n    }\n", 1)[0]
    check("manifest prestige-cap-held-sync row", "prestige-cap-held-sync" in manifest)
    check("every held change pushes Overhaul's count and the cap now; turn-in calls it too",
          "pushHeldToCap(player);" in hs_set and "PrestigeSystem.pushHeldToCap(player);" in hs_turn
          and "DmzRevampPrestigeBridge.syncFromLegacy(player);" in hs_ps
          and "PersonalLevelCapMirror.publish(player);" in _after(hs_ps, "public static void pushHeldToCap(").split("\n    }\n", 1)[0])
    check("10 s reconcile: re-sync only when Overhaul's count differs from the held wallet",
          "if (PrestigeSystem.count(data) != want) {" in hs_rec and "syncFromLegacy(player);" in hs_rec
          and "DmzRevampPrestigeBridge.reconcile(player);" in hs_prog)
    check("Overhaul's prestige button refused while LM owns prestige; player pointed to /lm",
          hs_blk.lstrip().startswith("if (lmOwnsPrestige()) {\n            return true;")
          and "LmOverhaulPrestigeIntegration.onOverhaulPrestigeButton(player);" in hs_int)

    print("\n--- § sparring stun clash 4.7.17 ---")
    sp_sys = read(SRC / "sparring/SparringSystem.java")
    sp_cmb = read(SRC / "sparring/SparCombat.java")
    sp_rt = read(SRC / "sparring/SparPlayerRuntime.java")
    sp_dr = read(SRC / "util/DmzRewards.java")
    sp_cfg = read(SRC / "config/DifficultyConfig.java")
    sp_help = read(SRC / "config/editor/ConfigFieldHelp.java")
    sp_act = _after(sp_sys, "private static void tickActivity(").split("\n    }\n", 1)[0]
    sp_frz = _after(sp_sys, "private static void updateFreezes(").split("private static boolean isAnyClashing(", 1)[0]
    sp_res = _after(sp_sys, "private static void tickStrikeClashResult(").split("\n    }\n", 1)[0]
    check("manifest sparring-stun-clash row", "sparring-stun-clash" in manifest)
    check("timers: melee 8000, grace 6000, distance grace 4000, post-clash hold 6000; 30s / 5-min gates unchanged",
          "MELEE_HIT_ACTIVITY_WINDOW_MS = 8000L;" in sp_sys and "SESSION_GRACE_PERIOD_MS = 6000L;" in sp_sys
          and "DISTANCE_GRACE_PERIOD_MS = 4000L;" in sp_sys and "POST_CLASH_HOLD_MS = 6000L;" in sp_sys
          and "MIN_COUNTED_SESSION_MS = 30_000L;" in sp_sys and "STREAK_MIN_SESSION_MS = 300_000L;" in sp_sys)
    check("timers are DifficultyConfig spar*Ms fields with help lines (config GUI Sparring section)",
          all(f"public long {f}" in sp_cfg and f"{f}=" in sp_help for f in (
              "sparMeleeHitWindowMs", "sparKiHitWindowMs", "sparMovementWindowMs", "sparSessionGraceMs",
              "sparClashHoldMs", "sparPostClashHoldMs", "sparComboTimeoutMs", "sparMomentumDurationMs"))
          and "public long sparMeleeHitWindowMs = 8000L;" in sp_cfg and "public long sparSessionGraceMs = 6000L;" in sp_cfg)
    check("stun / clash pause: Status.isStunned or any clash freezes the pair; expiry skipped; no faked stamps",
          "status.isStunned()" in sp_sys and "isStunned(player) || isAnyClashing(player.m_20148_())" in sp_frz
          and "rt.pairFrozenAt > 0L || pRt.pairFrozenAt > 0L" in sp_act
          and "lastOutAt =" not in sp_frz and "lastCombatOutAt" not in sp_frz
          and "now - rt.lastOutAt - paused <= hitWindowMs(rt.lastOutKind)" in sp_sys)
    check("combo and momentum freeze while the fighter is stunned or clashing",
          "setSelfFrozen(rt, selfHeld, now);" in sp_frz and "rt.comboUntil +=" in sp_frz and "rt.momentumUntil +=" in sp_frz
          and "rt.selfFrozenAt <= 0L && rt.momentumUntil > 0L" in sp_cmb)
    check("strike clash: DMZ StrikeClash.isClashing, 35 TP / 500 ms, 500 / 100 / 150 result by the loser's stun",
          "return StrikeClash.isClashing(uuid);" in sp_dr
          and "STRIKE_CLASH_TP_PER_TICK = 35.0f;" in sp_cmb and "STRIKE_CLASH_WIN_TP = 500.0f;" in sp_cmb
          and "STRIKE_CLASH_LOSS_TP = 100.0f;" in sp_cmb and "STRIKE_CLASH_TIE_TP = 150.0f;" in sp_cmb
          and "if (partnerStunned) {" in sp_res and "rt.clashTies++;" in sp_res
          and "public int clashWins;" in sp_rt and "Strike clashes: " in sp_sys)

    print("\n--- § prestige level cap ladder 4.7.16 ---")
    lc_bridge = read(SRC / "progression/bridge/DmzRevampPrestigeBridge.java")
    lc_stats = read(SRC / "mixin/StatsDataMixin.java")
    lc_mix = read(SRC / "mixin/DmzRevampPrestigeCapMixin.java")
    lc_math = read(SRC / "progression/LmOverhaulCapMath.java")
    lc_events = read(SRC / "event/DifficultyEvents.java")
    lc_test = read(SRC / "gui/ultra/UltraScreenSelfTest.java")
    lc_cap = _after(lc_bridge, "public static int levelCap(StatsData data) {").split("\n    }\n", 1)[0]
    lc_sync = _after(lc_bridge, "public static void syncFromLegacy(ServerPlayer player) {").split("\n    }\n", 1)[0]
    check("manifest prestige-level-cap-ladder row", "prestige-level-cap-ladder" in manifest)
    check("level cap = the need ladder of the Overhaul count (= held); flat only without a player or dmzrevamp",
          "return LmOverhaulCapMath.prestigeRequirement(overhaulCount(data));" in lc_cap
          and "if (data == null || !revampPresent()) {" in lc_cap
          and "public static int levelCap(StatsData data) {\n        return com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampPrestigeBridge.levelCap(data);" in lc_math)
    # 4.7.49 (M-P1-7): the StatsData cap handlers were dead (revamp's HEAD cancellers win) and are
    # gone; the max total still reaches LM through Overhaul's maxAssignableTotal hook.
    check("the cap applies to level gains: max total via Overhaul's maxAssignableTotal, TP soft-lock",
          "getMaxAllowedIncreaseForStat" not in lc_stats.split("*/", 2)[-1]
          and "cir.setReturnValue(LmOverhaulCapMath.maxAssignableTotal(data));" in lc_mix
          and "return maxAssignableTotal(data, overhaulLevelCap(data));" in lc_math
          and "LmOverhaulCapMath.levelCap(data);\n                    if (raw >= cap) {" in lc_events)
    check("new cap published right after the count syncs",
          "PersonalLevelCapMirror.publish(player);" in lc_sync)
    check("server-start probe: cap 50k at 0 held, 65k at 1, and in Overhaul's maxAssignableTotal",
          "LmOverhaulCapMath.levelCap(low)" in lc_test
          and 'ps.getMethod("maxAssignableTotal"' in lc_test
          and "level cap does not follow held prestige" in lc_test)

    print("\n--- § gui follow-ups 4.6.249 ---")
    gf_spec = read(SRC / "gui/ultra/UltraScreenSpec.java")
    gf_style = read(SRC / "gui/ultra/UltraStyle.java")
    gf_hub = read(SRC / "gui/ultra/UltraHubScreens.java")
    gf_diff = read(SRC / "gui/ultra/UltraDifficultyScreens.java")
    gf_rival = read(SRC / "gui/ultra/UltraRivalScreens.java")
    gf_prog = read(SRC / "gui/ProgressionGuiApi.java")
    gf_main = _after(gf_spec, "private void placeBack(ServerPlayer player)").split("\n    }\n", 1)[0]
    gf_admin = _after(gf_hub, "private static void openAdmin(").split("\n    }\n", 1)[0]
    gf_chal = _after(gf_rival, "private static void openChallenge(").split("\n    }\n", 1)[0]
    check("manifest gui-followups-249 row", "gui-followups-249" in manifest)
    check("Home-tab footer reads Back to main menu (no lone Main menu)",
          "UltraStyle.BACK_TO_MAIN" in gf_main and "placeFooter(null, null, action, label)" in gf_main)
    check("Staff Admin has one footer: Back to main menu",
          "spec.back();" in gf_admin and 'footerBack("nav:hub"' not in gf_admin)
    check("Difficulty sub-pages say Back to Difficulty Home and the label fits (real glyph widths)",
          "footerBackHome(" not in gf_diff and gf_diff.count(".back()") >= 4
          and "public static int textWidthPx(" in gf_style
          and "UltraStyle.textWidthPx(plainLabel) <= labelPx" in gf_spec)
    check("staff progression toggles reply with the display label",
          "ProgressionSystem.flagLabel(flag)" in gf_prog and "ProgressionSystem.flagLabel(key)" in gf_prog
          and '"Progression " + flag' not in gf_prog and '"Progression " + key' not in gf_prog
          and "Usage: /progression admin" not in gf_prog and "Usage: progression do flag" not in gf_prog)
    check("Rival Challenge tab: pending lines come from the same page as their buttons",
          "UltraPickHelper.pageSlice(challenges, CHALLENGES_PER_PAGE, pg)" in gf_chal
          and "placePerChallengeButtons(spec, player, pageChallenges);" in gf_chal)

    def _w(t):
        return sum(2 if c in "i!,.:;|'`" else 3 if c == "l" else 4 if c in " It[]" else
                   5 if c in 'fk"()*<>{}' else 7 if c in "@~" else 6 for c in t)

    check("sim: Back to Difficulty Home fits the 134px label area (real widths)", _w("Back to Difficulty Home") <= 134)
    gf_boss = read(SRC / "progression/boss/PlayerBossSummons.java")
    gf_end = read(SRC / "progression/end/EndDimensionStrength.java")
    gf_act = read(SRC / "service/DifficultyActions.java")
    gf_spec2 = read(SRC / "rival/RivalSpectator.java")
    gf_bsum = _after(gf_boss, "public static SummonOutcome summonOutcome(").split("\n    }\n", 1)[0]
    gf_dsum = _after(gf_end, "playerSummonOutcome(ServerPlayer player) {").split("\n    }\n", 1)[0]
    gf_start = _after(gf_spec2, "public static String start(").split("\n    }\n", 1)[0]
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
    kd_pack = _after(kd_ai, "static int packSize(DifficultyTier tier) {").split("\n    }\n", 1)[0]
    kd_hook = _after(kd_ev, "public void onAttackDodgeCounter(").split("\n    }\n", 1)[0]
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
    vc_join = _after(vc_ev, "public void onLivingJoin(").split("\n    }\n", 1)[0]
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
          boot_self_test("verifyTitlesSurvivePrestige"))

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
    # 4.7.81: UltraSparScreens.openDojoWarPending was unreachable (Dojo owns war requests) and is deleted.
    check("war requests: the unreachable Sparring copy is gone",
          "private static void openDojoWarPending(" not in spar_ui)
    check("Spar picker confirm Back returns to the picker page (history Back)",
          '"page:pick_confirm:" + action + "|" + here + "|" + name' in spar_ui)
    check("picker paging self-test runs at startup",
          boot_self_test("verifyPickerPaging"))
    pend = _after(spar_ui, "private static void openPending(").split("\n    }\n", 1)[0]
    check("bond invites: lines and buttons share one page slice (4.6.233)",
          pend.count("for (String card : pageCards)") == 2
          and "SparGuiApi.pendingMentorLine(card)" in pend)
    rival_ui = read(ultra_dir / "UltraRivalScreens.java")
    check("Rival name pickers use Page X of Y + shared Prev/Next; Spectate uses placeNameButtons",
          "UltraPickHelper.pageLabel(spec, pg, pages)" in rival_ui
          and "UltraPickHelper.placePrevNext(spec, pageBase, pg, pages)" in rival_ui
          and '" | page "' not in rival_ui
          and "UltraPickHelper.placeNameButtons(spec, doAction, choices, PICK_PER_PAGE, pageBase, pg, null)" in rival_ui)
    # 4.7.71: on the LM client Back pops the page history (the exact list page); bridge clients get the registry
    # parent, the Rival tab's first page.
    check("rival profile Back returns to the Rival tab page it came from",
          '"page:list_detail:" + profileArg(arg, page)' in rival_ui
          and 'item("rival", "list_detail:**", "Rival profile", "rival|actions");' in read(ultra_dir / "PageRegistry.java"))
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
          and "REPLACE_EXISTING" not in _after(cfg_editor, "static boolean backupEverySave(")
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
    shop_cfg_src = read(SRC / "progression/shop/PrestigeShopConfig.java")
    payout = _after(shop_cfg_src, "public int payout(int n) {").split("\n        }\n\n", 1)[0]
    check("turn-in payout: payoutOverrides, then the pack table, then the group bonus (4.6.229-2, 4.7.13)",
          "return PrestigeShopConfig.get().exchange.payout(amount);" in turn_in
          and "this.payoutOverrides" in payout and "PACK_BONUS.get(amount)" in payout
          and "this.bonusGroupSize" in payout
          and payout.index("payoutOverrides") < payout.index("PACK_BONUS") < payout.index("bonusGroupSize"),
          "pointsForTurnIn must use Exchange.payout: overrides, then the pack table, then the group bonus")

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
    # 4.7.64: player partners only; the two-meditating-NPCs path and its fighter scan are gone.
    check("nearby living world meditation (player partners only)",
          "class LivingWorldNearbyMeditation" in nearby
          and "AmbientFighterEntity" not in nearby
          and "isMeditationCircleMember" not in nearby
          and "nearTwoLivingCircleNpcs" not in nearby
          and "meditatingFightersNear" not in nearby
          and "NPC_RATE_PERCENT" not in nearby
          and "isPlayerMeditating" in nearby
          and "nearMeditatingPlayer" in nearby
          and "if (isPlayerMeditating != null) {" in nearby
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
          and "/lm progression android remove" in cmd_help)
    check("prestige effects view command",
          'm_82127_("effects")' in prog_cmds
          and "prestigeEffects" in prog_cmds
          and "/lm prestige effects" in cmd_help)

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
    check("/lm terminal is a player command",
          'm_82127_("terminal")' in terminal_cmd and "requires(TerminalCommands::mayOpen)" in terminal_cmd
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
          # 4.7.88: the bridge forwards /lm only; /lm terminal goes through it.
          and '"lm", List.of(), null' in terminal_mohist and '"terminal", List.of()' not in terminal_mohist)
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
          and "navSubmenu" not in cnpc_sk, retired=True)
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
    check("CNPC challenge pending paint", "pendingChallengeCards" in cnpc_rival, retired=True)
    check("rival declare pending_decide", "pending_decide:" in rival_chest and "pending_decide:" in cmi_rival)
    rival_store = read(SRC / "rival/RivalStore.java")
    check("rival duel requests persist in RivalStore", "challengeRequests" in rival_store and "upsertChallengeRequest" in rival_store)
    check("rival duel hydrate on load", "replaceRequestsFromStore" in rival_mgr)
    cleanup = rival_mgr.split("void cleanupPlayer", 1)[1].split("void ", 2)[0] if "void cleanupPlayer" in rival_mgr else ""
    check("rival duel not cleared on logout", "getRequestInvolving" not in cleanup)
    cnpc_rival = read(SRC / "gui/cnpc/CnpcLmRivalGui.java")
    check("rival CNPC reopen duel pending after send", '"challenge_send"' in cnpc_rival and '"challenge_pending"' in cnpc_rival, retired=True)
    check("rival declare vs challenge labels", "Declare invite" in cnpc_rival and "Challenge request" in cnpc_rival, retired=True)
    check("rival CNPC TP toggle label", 'toggleOn("TP")' in cnpc_rival and "Teleport msgs" not in cnpc_rival, retired=True)
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
    check("spar CNPC TP toggle label", 'toggleOn("TP")' in cnpc_spar and "teleport" not in cnpc_spar.lower(), retired=True)

    print("\n--- § spar ---")
    spar_chest = read(BUKKIT / "SparChestGui.java")
    cmi_spar = read(BUKKIT / "CmiSparGui.java")
    check("spar pending_decide", "pending_decide:" in spar_chest and "pending_decide:" in cmi_spar)
    check("spar dojo war pending decide", "dojo_war_pending_decide:" in spar_chest)
    dojo_rank = read(SRC / "sparring/DojoRankings.java")
    spar_api = read(SRC / "gui/SparGuiApi.java")
    # 4.7.85: the old challenge write path is deleted; a war on an offline master goes through DojoWar.legacyChallenge.
    check("dojo war offline by master uuid", "challengeDojoByMasterKey" not in dojo_rank
          and "public static String legacyChallenge(" in read(SRC / "dojo/DojoWar.java"))
    check("spar GUI dojo_challenge uuid path", "dojoChallengeByMasterUuid" not in read(SRC / "sparring/SparringSystem.java")
          # 4.7.84 (pick a): the old path declares through DojoWar.legacyChallenge, which still takes uuid: args.
          and "DojoWar.legacyChallenge(player, a)" in spar_api.split("dojo_challenge", 1)[1].split("dojo_accept", 1)[0]
          and 'raw.regionMatches(true, 0, "uuid:", 0, 5)' in read(SRC / "dojo/DojoWar.java"))
    dojo_war_cnpc = cnpc_spar.split("paintDojoWar", 1)[1].split("paintDojoWarPending", 1)[0] if "paintDojoWar" in cnpc_spar else ""
    check("CNPC dojo war hub no accept/decline", "dojo_accept" not in dojo_war_cnpc)
    check("spar Training bonds wording", "Training bonds" in cnpc_spar and "Training bonds" in spar_chest, retired=True)
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
          and "main Difficulty page" in cnpc_diff, retired=True)
    check("CNPC team scaling locked like tiers", "Teams locked" in cnpc_diff
          and "before using team scaling" in cnpc_diff, retired=True)
    check("Difficulty team action gated when personal OFF", "ACT_TEAM.equals(act)" in read(SRC / "service/DifficultyActions.java"),
          retired=True)  # 4.7.78: rival teams removed
    check("CNPC difficulty uses handleArgNoReopen", "handleArgNoReopen" in cnpc_diff, retired=True)

    print("\n--- § cnpc ---")
    cnpc_support = read(SRC / "gui/cnpc/CnpcGuiSupport.java")
    check("CNPC afterGuiClosed defer reopen", "afterGuiClosed" in cnpc_support, retired=True)
    check("CNPC flash notice separate widget ids", "ID_FLASH_LABEL_BASE" in cnpc_support, retired=True)
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
          and "public static String noticeBody" in gui_notice, retired=True)
    check("CNPC info blocks readable (readableInfoLine)", "readableInfoLine" in cnpc_support
          and "readableInfoLine" in read(SRC / "gui/cnpc/CnpcGuiStyle.java"), retired=True)
    check("CNPC difficulty clear tier avoids flash ids", "ID_GRID_BASE" in cnpc_diff
          and 'button(gui, 50, "§cClear active tier"' not in cnpc_diff, retired=True)
    cnpc_preview = read(SRC / "gui/cnpc/CnpcPlayerPreview.java")
    check("CNPC preview live player sync (inventory-style)", "tryBindLivePlayer" in cnpc_preview
          and "setEntitySyncedById" in cnpc_preview, retired=True)
    cnpc_prestige = read(SRC / "gui/cnpc/CnpcLmPrestigeGui.java")
    check("CNPC prestige section tags avoid info label ids",
          "ID_INLINE_NOTE" in cnpc_prestige and 'paintSectionTag(gui, 11,' not in cnpc_prestige
          and 'paintSectionTag(gui, 12,' not in cnpc_prestige, retired=True)
    check("Prestige CNPC tier grid avoids flash widget ids",
          "ID_PRESTIGE_TIER_GRID" in cnpc_prestige and "paintTwoColumnButtonGrid(player, gui, row, 50," not in cnpc_prestige, retired=True)
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
          # 4.7.84 (C-2): Home's Android button (placeAndroidButton) picks Remove / Convert / dim.
          and "case UPGRADED, ELIGIBLE ->" in char_ui and "private static void placeAndroidButton(" in char_ui
          and "state == AndroidConversion.Eligibility.UPGRADED" in char_ui
          and '"Eligible races: " + AndroidConversion.eligibleRaceHint()' in char_ui
          and "already an Android by race" in char_ui
          and 'UltraStyle.plain("Convert to Android")' in char_ui
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
          and "addLabel(50," not in cnpc_rival, retired=True)
    check("CNPC rival challenge duration grid avoids flash ids", "ID_GRID_BASE" in cnpc_rival
          and "paintTwoColumnButtonGrid(pl, gui, row, 30," not in cnpc_rival, retired=True)
    check("CNPC rival act defers menu reopen", "runDeferred(player, reopen)" in read(SRC / "gui/cnpc/CnpcGuiSupport.java"), retired=True)
    check("Rival challenge request window >= 5 min", "CH_REQUEST_EXPIRE_MS = 300_000" in read(SRC / "rival/RivalConstants.java"))
    rival_mgr = read(SRC / "rival/RivalChallengeManager.java")
    check("Rival accept not blocked by distance at accept", "blocks to accept" not in rival_mgr)
    cnpc_prog = read(SRC / "gui/cnpc/CnpcLmProgressionGui.java")
    check("CNPC TP boost presets avoid flash ids", "ID_BOOST_PRESET_BASE" in cnpc_prog
          and "boostPreset(gui, player, row, 50," not in cnpc_prog, retired=True)
    check("Progression CNPC staff flag toggles", "HINT_TOGGLE_STAFF" in cnpc_prog and "paintAllFlags" in cnpc_prog, retired=True)
    prog_chest = read(BUKKIT / "ProgressionChestGui.java")
    check("Progression chest staff section toggles", "allFlags" in prog_chest and "SlotAction.act(\"flag\"" in prog_chest)
    check("ProgressionChatMenu retired (4.7.31: dead code, zero referrers)",
          not (SRC / "gui/ProgressionChatMenu.java").exists())
    cmi_prog = read(BUKKIT / "CmiProgressionGui.java")
    check("Progression CMI no flag board methods", "openFlags" not in cmi_prog and "openFabledFlags" not in cmi_prog)
    check("Android build overlay ships AndroidConversion", "progression/race/AndroidConversion.class" in build_sh)
    check("build overlays TpBossNotify", "util/TpBossNotify.class" in build_sh)
    check("build overlays ConfigFieldHelp", "config/editor/ConfigFieldHelp.class" in build_sh)
    chest_prog = read(BUKKIT / "ProgressionChestGui.java")
    tooltips = read(GUI / "src/main/resources/gui-tooltips.json")
    check("Android GUI copy lists Saiyan eligible", "Saiyan excluded" not in chest_prog and "Saiyan excluded" not in tooltips)
    preview_tex = read(SRC / "gui/cnpc/DmzPreviewTexture.java")
    check("CNPC preview race textures (namekian/bio)", "namekian" in preview_tex and "bioandroid" in preview_tex, retired=True)
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
          and "CnpcUiFit.fit" in cnpc_support and "compressToWindow" in cnpc_support, retired=True)
    check("menus fit Minecraft's real GUI scale without a second font shrink",
          "guiScale(fb[0], fb[1])" in ui_fit
          and "Math.min(sx, sy)" in ui_fit
          and "tightScale" not in ui_fit
          and "label.setScale" not in ui_fit
          and "new int[] {4, 5}" not in ui_fit, retired=True)
    check("GUI scale uses Minecraft's 320x240 floor",
          "MIN_SCALED_WIDTH = 320" in ui_fit and "MIN_SCALED_HEIGHT = 240" in ui_fit
          and "framebufferWidth / (scale + 1)" in ui_fit, retired=True)
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
            retired=True)
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
    retired_n = sum(1 for x in ok if x.endswith(" [retired]"))
    print(f"PASS — ship manifest OK ({len(ok) - retired_n} checks run, {retired_n} retired)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
