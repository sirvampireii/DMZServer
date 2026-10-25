#!/usr/bin/env python3
"""Fail-closed: every Ultra button/tab action id must be routable by UltraGuiActions."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ULTRA = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/gui/ultra"
ACTIONS = ULTRA / "UltraGuiActions.java"
SPEC = ULTRA / "UltraScreenSpec.java"
STYLE = ULTRA / "UltraStyle.java"
PACKET = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/net/gui/GuiOpenPacket.java"
DOC = ROOT.parents[1] / "docs" / "KAIPATH_GUI_PROTOCOL.md"

# Known do-actions emitted by Ultra*Screens (action name after do: / before next :).
KNOWN_DO: dict[str, set[str]] = {
    "hub": {"reload", "skillcheck_denied"},
    "admin": {"reload"},
    "difficulty": {
        "activate",
        "lower_tier",
        "toggle_personal",
        "summon_end_dragon",
        "mode",
        "equip_title",
        "clear_title",
        "toggle_title_sense",
        "toggle_coin_chat",
        "reload",
        "syslog",
    },
    "character": {
        "reskin_confirm",
        "race_confirm",
        "class_confirm",
        "bone_race_default",
        "bone_toggle",
        "bone_unlock",
        "bone_equip",
        "bone_unequip",
        "bone_preview",
        "bone_restore",
        "bodypart_unlock",
        "bodypart_toggle",
        "bodypart_clear",
        "bodypart_color",
        "bodypart_color_reset",
        "saga_confirm",
        "saga_reset",
        "android_remove",
        "android",
        "android_staff",
        "nemesis_spawn_toggle",
    },
    "prestige": {
        "confirm",
        "confirm_now",
        "turnin",
        "skill",
        "tier",
        "majin",
        "mutant",
        "unmajin",
        "unmutant",
        "ultimate",
        "unultimate",
        "godki",
        # Custom-shop perk pages (live 228.1 UltraPrestigeScreens.openPerk/openCustom):
        # routed UltraGuiActions "prestige" -> ProgressionGuiApi.handlePrestigeDo ->
        # PrestigeShopHooks.handleDo -> PrestigeCustomShop.buy / .remove.
        "custom_buy",
        "custom_remove",
    },
    "progression": {"boost", "toggle_staff_free_coins"},
    "rival": {
        "tpmsg",
        "instinct",
        "accept_replace",
        "cancel_mutual_replace",
        "admin",
        "challenge",
        "challenge_accept",
        "challenge_decline",
        "challenge_cancel",
        "challenge_send",
        "spectate",
        "spectate_stop",
        "declare",
        "silent",
        "accept",
        "decline",
        "remove",
    },
    "spar": {
        "tpmsg",
        "end",
        "mentor_tpmsg",
        "mentor",
        "mentor_accept",
        "mentor_decline",
        "mentor_invite",
        "apprentice_invite",
        "mentor_release",
        "admin",
        "dojo_accept",
        "dojo_decline",
        "dojo_war_cancel",
        "dojo_challenge",
        "weight_quote",
        "weight_buy",
        "quote",
        "buy_weight",
    },
    "config": {
        "open_module",
        "select_module",
        "save_all",
        "discard_all",
        "reload_module",
        "toggle",
        "set",
        "add",
        "step",
        "cycle",
        "undo",
        "search",
        "search_clear",
        # Flow-layout setting titles (label-above-control rows) use a no-op refresh hit target.
        "refresh",
    },
    "skillcheck": set(),
    "skills": set(),
}


def extract_actions(text: str) -> list[str]:
    found: list[str] = []
    for m in re.finditer(r'"(do:[^"]+|tab:[^"]+|nav:[^"]+|open:[^"]+)"', text):
        found.append(m.group(1))
    if 'UltraStyle.PAGE_ANDROID_REMOVE' in text and 'do:"' in text.replace(" ", ""):
        found.append("do:android_remove")
    return found


def main() -> int:
    errors: list[str] = []

    actions_src = ACTIONS.read_text(encoding="utf-8")
    for needle in (
        "qualify(",
        "LOGGER.error",
        "UltraGuiActions.handle failed",
        "stringArg(",
        "bareHubOpenTarget",
        "hasVerbPrefix",
        "rawActionId",
        "requiresStaff",
        "rejectStaffGated",
        "StaffAccess.isStaff",
        "That menu action is not available.",
    ):
        if needle not in actions_src:
            errors.append(f"UltraGuiActions missing {needle}")
    if "rejectStaffGated(" not in actions_src or actions_src.count("rejectStaffGated(") < 3:
        # declaration + dispatch + dispatchResolved
        errors.append(
            "UltraGuiActions must call rejectStaffGated from dispatch and dispatchResolved"
        )
    for system in (
        "character",
        "difficulty",
        "progression",
        "prestige",
        "spar",
        "rival",
        "config",
        "admin",
    ):
        if f'case "{system}"' not in actions_src and f'"{system}"' not in actions_src:
            errors.append(f"UltraGuiActions bareHubOpenTarget missing {system}")
    if "rawActionId" not in actions_src:
        errors.append("UltraGuiActions must log rawActionId on failures")

    spec = SPEC.read_text(encoding="utf-8")
    if "qualifyAction" not in spec:
        errors.append("UltraScreenSpec must qualify action ids with @screen:")
    if "requireValidActionId" not in spec:
        errors.append("UltraScreenSpec must validate action ids via requireValidActionId")
    if "panelWidth" not in spec or "PANEL_W" not in spec:
        errors.append("UltraScreenSpec must set panelWidth/panelHeight")
    if '"args"' not in spec and "add(\"args\"" not in spec and 'add("args"' not in spec:
        errors.append("UltraScreenSpec buttons must include args {screen,page}")

    style = STYLE.read_text(encoding="utf-8")
    if "PANEL_W = 350" not in style or "PANEL_H = 300" not in style:
        errors.append("UltraStyle panel defaults must be 350×300 (compact Ultra dialog)")
    if "BTN_H = 18" not in style or "BTN_GAP = 6" not in style:
        errors.append("UltraStyle button height/gap must be 18 / 6")
    if "BTN_PAD = 8" not in style or "BTN_MIN_W = 80" not in style:
        errors.append("UltraStyle button pad/min width must be 8 / 80")
    if "PANEL_MARGIN = 10" not in style or "PANEL_MAX_H = 420" not in style:
        errors.append("UltraStyle panel margin/max must be 10 / 420")
    if "LINE_H = 16" not in style or "LINE_GAP = 8" not in style:
        errors.append("UltraStyle body line height/gap must be 16 / 8")
    if "BODY_PAD = 10" not in style or "BODY_MIN_H = 140" not in style:
        errors.append("UltraStyle body pad/min height must be 10 / 140")
    if 'TITLE_ALIGN = "center"' not in style:
        errors.append('UltraStyle TITLE_ALIGN must be "center"')
    if "fitAsciiLabel" not in style or "CHAR_W_EST" not in style:
        errors.append("UltraStyle must pre-fit labels (fitAsciiLabel) so KaiPath fitLabel never runs")
    if "fitAsciiLabel" not in (SPEC.read_text(encoding="utf-8")):
        errors.append("UltraScreenSpec must fitAsciiLabel tab/button labels before wire")
    char_tabs = (ULTRA / "UltraCharacterScreens.java").read_text(encoding="utf-8")
    if "android|Android Removal" in char_tabs or "saga|Saga Reset" in char_tabs:
        errors.append(
            "UltraCharacterScreens tabs must stay short (Home/Saga/Android) — long labels mojibake on KaiPath"
        )
    if '.tabs("home|Home", "saga|Saga", "android|Android")' not in char_tabs:
        errors.append(
            'UltraCharacterScreens must define tabs as exact home|Home, saga|Saga, android|Android'
        )
    for const in ("STYLE_NORMAL", "STYLE_CONFIRM", "STYLE_DANGER", "STYLE_DIM"):
        if f"{const} =" not in style:
            errors.append(f"UltraStyle must define {const} for button wire styles")
    spec_text_full = SPEC.read_text(encoding="utf-8")
    # Tab wire labels must stay plain ASCII (no UltraStyle.color) — Character self-test.
    tab_btn = ""
    if "private void appendTabButtons" in spec_text_full:
        tab_btn = spec_text_full.split("private void appendTabButtons", 1)[1].split(
            "private ", 1
        )[0]
    if "UltraStyle.color(" in tab_btn:
        errors.append(
            "UltraScreenSpec.appendTabButtons must emit plain tab labels "
            "(no UltraStyle.color) — Character self-test requires label=Home/Saga/Android"
        )
    if "plainWireLabel" not in spec_text_full:
        errors.append(
            "UltraScreenSpec must keep Character buttonsJson labels free of section-sign "
            "(plainWireLabel)"
        )
    if "playerModel" not in spec_text_full:
        errors.append("UltraScreenSpec must support optional playerModel in layoutJson")
    parts_helper = ULTRA / "PlayerModelParts.java"
    char_screens = (ULTRA / "UltraCharacterScreens.java").read_text(encoding="utf-8")
    if not parts_helper.is_file():
        errors.append("PlayerModelParts helper must exist for playerModel.parts snapshots")
    else:
        parts_text = parts_helper.read_text(encoding="utf-8")
        if "SCHEMA = 1" not in parts_text or "headBones" not in parts_text:
            errors.append("PlayerModelParts schema 1 must serialize headBones (+ form/extras)")
        if "AdminInspectSessions.resolveSubject" not in parts_text:
            errors.append(
                "PlayerModelParts must resolve the inspect subject for the model render target"
            )
        if 'addProperty("subject", "local")' in parts_text and "uuid:" not in parts_text:
            errors.append(
                "PlayerModelParts must send uuid:<subject> while inspecting, not only local"
            )
        if 'addProperty("parts"' not in spec_text_full and 'pm.add("parts"' not in spec_text_full:
            errors.append("UltraScreenSpec must emit optional playerModel.parts")
    if "playerModel(player, 248, 60," not in char_screens and ".playerModel(player, 248, 60," not in char_screens:
        # allow either chained form
        if "248, 60, 86, 120" not in char_screens:
            errors.append(
                "UltraCharacterScreens playerModel y must be 60 (below tab strip; 36 clips head)"
            )
    # Preview only on Character Home + model customize (bones) — not Saga/Android/race/class/reskin.
    if 'base(player, "main", "home", true)' not in char_screens:
        errors.append("UltraCharacterScreens must show playerModel on Character Home")
    if "sub(player, \"bones:\" + pg, true)" not in char_screens and 'sub(player, "bones:0", true)' not in char_screens:
        errors.append("UltraCharacterScreens must show playerModel on model customize (bones)")
    # Body Parts Shop: render every card the page owns — a hard numeric cap after the slice
    # silently dropped pieces (stride 14 vs cap 8 hid 28 of 68, incl. four tails).
    bp_m = re.search(
        r"(?ms)^\s{4}private static void openBodyParts\(.*?^\s{4}\}",
        char_screens,
    )
    bp_body = bp_m.group(0) if bp_m else ""
    if not bp_body:
        errors.append("UltraCharacterScreens.openBodyParts must exist for the Body Parts Shop")
    elif re.search(r"placed\s*>=\s*\d+", bp_body):
        errors.append(
            "UltraCharacterScreens.openBodyParts must not hard-drop cards after placed >= N "
            "(the page slice is the render capacity — every catalog piece stays reachable)"
        )
    if 'base(player, UltraStyle.PAGE_ANDROID_REMOVE, "android", false)' not in char_screens:
        errors.append("UltraCharacterScreens must omit playerModel on Android Removal")
    diff_screens = (ULTRA / "UltraDifficultyScreens.java").read_text(encoding="utf-8")
    if "Coin settings" in diff_screens or "openSettings" in diff_screens:
        errors.append("Difficulty must not keep a Coin settings submenu")
    if '"Coin messages - ON"' not in diff_screens or '"Coin messages - OFF"' not in diff_screens:
        errors.append("Difficulty home must toggle Coin messages - ON/OFF directly")
    if '"Coin messages: ' in diff_screens:
        errors.append("Difficulty home Coin messages toggle must use the dash pattern, not a colon")
    if 'case "toggle_coin_chat" -> "settings"' in (
        ULTRA / "UltraGuiActions.java"
    ).read_text(encoding="utf-8"):
        errors.append("toggle_coin_chat must reopen Difficulty home, not the removed settings page")
    if "page:android_staff" not in char_screens:
        errors.append("UltraCharacterScreens must offer a staff Another player picker")
    if '"Convert Android"' not in char_screens or '"Remove Android"' not in char_screens:
        errors.append("staff android target page must offer Convert Android and Remove Android")
    if 'labelValue("Player"' not in char_screens:
        errors.append("staff android confirm page must name the target player")
    if "isStaffAndroidPage" not in char_screens or "staffMayEditOthers" not in char_screens:
        errors.append("staff android pages must be gated inside UltraCharacterScreens.open")
    gui_actions = (ULTRA / "UltraGuiActions.java").read_text(encoding="utf-8")
    if '"android_staff"' not in gui_actions or "applyAndroidStaff" not in gui_actions:
        errors.append("UltraGuiActions must gate and apply android_staff")
    if "removeConfirmed" not in (
        ROOT
        / "src/main/java/com/dbzlegacy/adaptivedifficulty/progression/race/AndroidConversion.java"
    ).read_text(encoding="utf-8"):
        errors.append("AndroidConversion.removeConfirmed must apply a staff confirm without the 10s click")
    if "sub(player, \"race:\" + pg, false)" not in char_screens:
        errors.append("UltraCharacterScreens must omit playerModel on race list")
    if "sub(player, \"class:\" + pg, false)" not in char_screens:
        errors.append("UltraCharacterScreens must omit playerModel on class list")
    if 'sub(player, "reskin", false)' not in char_screens:
        errors.append("UltraCharacterScreens must omit playerModel on reskin")
    if "scrollBody" not in spec_text_full:
        errors.append("UltraScreenSpec must emit scrollBody in layoutJson")
    if "buttonShift" not in spec_text_full or "targetBodyH" not in spec_text_full:
        errors.append(
            "UltraScreenSpec must grow panel toward PANEL_MAX_H and shift buttons for dense body text"
        )
    body_block = ""
    if "int bodyMaxH" in spec_text_full and "boolean scrollBody" in spec_text_full:
        body_block = spec_text_full.split("int bodyMaxH", 1)[1].split("boolean scrollBody", 1)[0]
    if "BODY_MIN_H" in body_block or "bodyBottom - bodyTop" not in body_block:
        errors.append(
            "UltraScreenSpec bodyMaxH must cap at the button boundary "
            "(bodyBottom - bodyTop), not a minimum body height"
        )
    if "buttonsAfterLines" not in spec_text_full or "24 + lineCount" not in spec_text_full:
        errors.append(
            "UltraScreenSpec must place button rows from line count (buttonsAfterLines)"
        )
    for screen_name in (
        "UltraCharacterScreens.java",
        "UltraDifficultyScreens.java",
        "UltraPrestigeScreens.java",
        "UltraRivalScreens.java",
        "UltraSparScreens.java",
        "UltraProgressionScreens.java",
        "UltraWeightShopScreens.java",
        "UltraSkillCheckScreens.java",
    ):
        screen_text = (ULTRA / screen_name).read_text(encoding="utf-8")
        if "buttonsAfterLines" not in screen_text:
            errors.append(f"{screen_name} must place buttons with buttonsAfterLines")
        if (
            "setRow(UltraStyle." in screen_text
            or ".setRow(100)" in screen_text
            or "setRow(120)" in screen_text
        ):
            errors.append(f"{screen_name} must not hardcode body setRow offsets")
    if 'titleAlign' not in spec_text_full or "TITLE_ALIGN" not in spec_text_full:
        errors.append("UltraScreenSpec must emit layoutJson.titleAlign from UltraStyle.TITLE_ALIGN")
    if "textField" not in spec_text_full or "inputsJson" not in PACKET.read_text(encoding="utf-8"):
        errors.append("UltraScreenSpec/GuiOpenPacket must support textField → inputsJson (field 11)")
    if "bindInput" not in spec_text_full:
        errors.append("UltraScreenSpec buttons must support args.bindInput for text submit")
    config_screens = (ULTRA / "UltraConfigScreens.java").read_text(encoding="utf-8")
    number_block = config_screens.split("case NUMBER")[1].split("case ENUM")[0] if "case NUMBER" in config_screens else ""
    enum_block = config_screens.split("case ENUM")[1].split("case LIST")[0] if "case ENUM" in config_screens else ""
    list_block = (
        config_screens.split("case LIST, MAP, GROUP")[1].split("case TEXT")[0]
        if "case LIST, MAP, GROUP" in config_screens
        else ""
    )
    if "textFieldRow" not in number_block:
        errors.append("UltraConfigScreens NUMBER inline must offer textFieldRow (typed value), not only +/- steps")
    if "textFieldRow" not in enum_block:
        errors.append("UltraConfigScreens ENUM inline must offer textFieldRow + Apply")
    if "textFieldRow" not in list_block or "do:add:" not in list_block or "buttonBound" not in list_block:
        errors.append(
            "UltraConfigScreens LIST/MAP/GROUP inline must offer textFieldRow + buttonBound Apply (do:add)"
        )
    if 'dim("Back")' in list_block:
        errors.append("UltraConfigScreens LIST/MAP/GROUP must not stack a Back button (use Open + one footer)")
    if "typedValueFromArgs" not in config_screens:
        errors.append("UltraConfigScreens set/add must log and resolve typed args via typedValueFromArgs")
    if "placeCategoryRail" not in config_screens and "CATEGORIES" not in config_screens:
        errors.append("UltraConfigScreens must keep a left category rail (Difficulty…Currency)")
    if "chromeButton" not in config_screens:
        errors.append("UltraConfigScreens category rail must use chromeButton (body packing)")
    if "flowLabel" not in config_screens:
        errors.append("UltraConfigScreens settings must use flowLabel (label above control)")
    if "PANEL_W = 480" in config_screens:
        errors.append("UltraConfigScreens must not hardcode PANEL_W = 480 (clips on small UI)")
    if "CONFIG_DESIGN_W = 426" not in config_screens or "CONFIG_DESIGN_H = 240" not in config_screens:
        errors.append("UltraConfigScreens design box must stay 426x240 so a 240-tall GUI can show it")
    if "CONFIG_PANEL_W = 1920" not in config_screens or "CONFIG_PANEL_H = 1080" not in config_screens:
        errors.append(
            "UltraConfigScreens must request 1920x1080 so KaiPath 1.3.20 clamps the panel to the window"
        )
    if "SIDE_W = 148" not in config_screens:
        errors.append("UltraConfigScreens category rail must stay 148px wide")
    if "fillScreen(CONFIG_DESIGN_W, CONFIG_DESIGN_H)" not in config_screens or "lockPanel" not in config_screens:
        errors.append(
            "UltraConfigScreens must fillScreen its 426x240 design box (widget grid) while"
            " lockPanel keeps the 1920x1080 clamp request — KaiPath 1.3.31 scales panel/design"
        )
    if "fillScreen(CONFIG_PANEL_W, CONFIG_PANEL_H)" in config_screens:
        errors.append(
            "UltraConfigScreens must not declare the 1920x1080 panel as the fillScreen design"
            " box — KaiPath 1.3.31 scale = panel / design would shrink the 426-grid widgets"
            " into the top-left corner"
        )
    if "margin(0)" not in config_screens or "center(false)" not in config_screens:
        errors.append("UltraConfigScreens must anchor at the window (margin 0, center false)")
    if 'addProperty("fillScreen"' not in spec_text_full:
        errors.append("UltraScreenSpec must emit layoutJson.fillScreen")
    if "public UltraScreenSpec fillScreen(int designWidth, int designHeight)" not in spec_text_full:
        errors.append("UltraScreenSpec.fillScreen must take a design box separate from the panel request")
    if "fillScreen" not in DOC.read_text(encoding="utf-8"):
        errors.append("KAIPATH_GUI_PROTOCOL.md must document layoutJson.fillScreen")
    kit = ROOT.parents[1] / (
        "tools/ultra-gui-kit/kaipath-client/com/dmzlegacy/kaipath/ultra/"
        "consumers/legacymechanics/LmDynamicScreen.java"
    )
    if not kit.is_file() or "fillScreen" not in kit.read_text(encoding="utf-8"):
        errors.append("LmDynamicScreen must scale a fillScreen design box to the window")
    if 'do:search' not in config_screens or "rememberSearch" not in config_screens:
        errors.append("UltraConfigScreens must pin search (do:search + ConfigEditor.rememberSearch)")
    if 'do:save_all' not in config_screens or 'do:discard_all' not in config_screens or 'do:reload_module' not in config_screens:
        errors.append("UltraConfigScreens must keep Save all / Reload / Discard changes actions")
    if "searchAll" not in (ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/config/editor/ConfigEditor.java").read_text(encoding="utf-8"):
        errors.append("ConfigEditor.searchAll must power cross-category search")
    bridge_gaps = ROOT.parent.parent / "docs" / "KAIPATH_BRIDGE_GAPS.md"
    if not bridge_gaps.is_file():
        # ROOT = .../tools/dmz-adaptive-difficulty
        bridge_gaps = Path(__file__).resolve().parents[3] / "docs" / "KAIPATH_BRIDGE_GAPS.md"
    if not bridge_gaps.is_file():
        errors.append("docs/KAIPATH_BRIDGE_GAPS.md must document LM vs KaiPath gap matrix")
    if 'STYLE_DIM' not in (ultra_hub := (ULTRA / "UltraHubScreens.java").read_text(encoding="utf-8")):
        errors.append("UltraHubScreens must wire STYLE_DIM for unavailable system tiles")
    if "skillcheck_denied" not in ultra_hub or "STYLE_DIM" not in ultra_hub:
        errors.append("Hub Skill Check denied tile must send STYLE_DIM")

    # Staff inspect: Ultra must resolve AdminInspectSessions subject for data, send to viewer.
    ultra_gui = (ULTRA / "UltraGui.java").read_text(encoding="utf-8")
    ultra_actions = (ULTRA / "UltraGuiActions.java").read_text(encoding="utf-8")
    ultra_spec = (ULTRA / "UltraScreenSpec.java").read_text(encoding="utf-8")
    inspect_sessions = (
        ROOT
        / "src/main/java/com/dbzlegacy/adaptivedifficulty/gui/AdminInspectSessions.java"
    ).read_text(encoding="utf-8")
    if "AdminInspectSessions.resolveSubject" not in ultra_gui:
        errors.append("UltraGui.open must resolve AdminInspectSessions subject for menu data")
    if "bindInspectViewer" not in ultra_gui or "clearInspectViewer" not in ultra_gui:
        errors.append("UltraGui.openUltra must bindInspectViewer so packets go to staff viewer")
    if "AdminInspectSessions.resolveSubject" not in ultra_actions:
        errors.append("UltraGuiActions.dispatch must resolve inspect subject for edits")
    if "resolveActor" not in ultra_actions:
        errors.append("UltraGuiActions.runDo must use AdminInspectSessions.resolveActor")
    if "inspectBannerLine" not in ultra_spec or "prependInspectBanner" not in ultra_spec:
        errors.append("UltraScreenSpec.send must prepend inspectBannerLine during inspect")
    if "resolveActor" not in inspect_sessions:
        errors.append("AdminInspectSessions must expose resolveActor for staff-as-self ops")
    if "§" in inspect_sessions:
        errors.append("AdminInspectSessions inspectBannerLine must use UltraStyle (no bare §)")

    # Menu-action results paint on the reopened Ultra page (not chat).
    feedback = ULTRA / "UltraGuiFeedback.java"
    if not feedback.is_file():
        errors.append("UltraGuiFeedback.java must stash menu-action results for the next Ultra open")
    else:
        feedback_src = feedback.read_text(encoding="utf-8")
        if "setFromResult" not in feedback_src or "applyPending" not in feedback_src:
            errors.append("UltraGuiFeedback must expose setFromResult + applyPending")
        if "preferGui" not in feedback_src:
            errors.append("UltraGuiFeedback must preferGui (guiBackend != chat)")
    if "UltraGuiFeedback.applyPending" not in ultra_spec:
        errors.append("UltraScreenSpec.send must applyPending UltraGuiFeedback")
    if "UltraGuiFeedback.deliver" not in ultra_actions and "deliverFeedback(" not in ultra_actions:
        errors.append("UltraGuiActions must deliver menu results via UltraGuiFeedback")
    if "DmzRewards.msg" in ultra_actions:
        errors.append("UltraGuiActions must not chat menu-action results (use UltraGuiFeedback)")
    weight_src = (ULTRA / "UltraWeightShopScreens.java").read_text(encoding="utf-8")
    if "UltraGuiFeedback.deliver" not in weight_src:
        errors.append("UltraWeightShopScreens.buy must stash purchase results via UltraGuiFeedback")

    character = (ULTRA / "UltraCharacterScreens.java").read_text(encoding="utf-8")
    if "Keeping everything costs " not in character or "exact cost shown on the next page." not in character:
        errors.append("race select must pin a dim keep-100 cost line outside appendCompactLines")
    if character.count('UltraStyle.labelValue("Cost"') < 2:
        errors.append("race confirm and class pages must pin Cost via UltraStyle.labelValue")
    if "classAmountDue" not in character or "raceAmountDue" not in character:
        errors.append("pinned costs must use CharacterServicesSystem amount helpers")
    # Ambient / event feedback has no menu. Do not route it through Ultra.
    java_root = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty"
    for rel in (
        "rival/RivalProximity.java",
        "progression/tp/DeathTpPenalty.java",
        "title/TitleSense.java",
        "util/DmzRewards.java",
        "rival/RivalInstinct.java",
    ):
        ambient = (java_root / rel).read_text(encoding="utf-8")
        if "UltraGuiFeedback" in ambient or "UltraScreenSpec" in ambient:
            errors.append(rel + " is ambient; leave its feedback in chat or the boss bar")

    packet = PACKET.read_text(encoding="utf-8")
    if "panelWidth" not in packet or "layoutJson" not in packet:
        errors.append("GuiOpenPacket must carry panelWidth/panelHeight/layoutJson")
    if 'titleAlign\\":\\"center' not in packet and 'titleAlign":"center' not in packet:
        errors.append('GuiOpenPacket.defaultLayoutJson must include "titleAlign":"center"')
    if "sanitizeLayoutJson" not in packet:
        errors.append("GuiOpenPacket must sanitize layoutJson (force titleAlign, never mid-JSON trim)")
    if "LAYOUT_JSON_MAX = 4096" not in packet and "LAYOUT_JSON_MAX=4096" not in packet:
        errors.append("GuiOpenPacket LAYOUT_JSON_MAX must be 4096")
    if "inputsJson" not in packet:
        errors.append("GuiOpenPacket must carry inputsJson (field 11)")
    mech = (
        ROOT
        / "src/main/java/com/dbzlegacy/adaptivedifficulty/command/MechanicsCommands.java"
    ).read_text(encoding="utf-8")
    if "§" in mech:
        errors.append("MechanicsCommands must not contain bare § literals — use UltraStyle")

    net = (ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/net/gui/LmGuiNetwork.java").read_text(
        encoding="utf-8"
    )
    if 'o.addProperty("titleAlign", UltraStyle.TITLE_ALIGN)' not in net:
        errors.append("LmGuiNetwork.withLogicalSystem must force layoutJson.titleAlign=center")
    if 'titleAlign\\":\\"center' not in net and '"titleAlign":"center"' not in net:
        errors.append("LmGuiNetwork.withLogicalSystem catch fallback must include titleAlign=center")
    if "validatePanelBounds" not in net:
        errors.append("LmGuiNetwork must validate outgoing panelWidth/panelHeight")
    if "panelWidth <= 0" not in net and "w <= 0" not in net:
        errors.append("LmGuiNetwork must warn when panelWidth/panelHeight <= 0")
    if 'PROTOCOL_VERSION = "1"' not in net:
        errors.append("LmGuiNetwork must define PROTOCOL_VERSION = \"1\"")
    if "runPacketRoundTripSelfTest" not in net:
        errors.append("LmGuiNetwork must run packet round-trip self-test at register")
    if "screensTested" not in net:
        errors.append("LmGuiNetwork round-trip self-test must log screensTested")
    proto = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/net/gui/UltraGuiProtocol.java"
    if not proto.is_file():
        errors.append("Missing UltraGuiProtocol (shared <modid>:gui conventions)")
    else:
        proto_text = proto.read_text(encoding="utf-8")
        if 'PROTOCOL_VERSION = "1"' not in proto_text:
            errors.append("UltraGuiProtocol must define PROTOCOL_VERSION = \"1\"")
        if 'CHANNEL_PATH = "gui"' not in proto_text:
            errors.append("UltraGuiProtocol must define CHANNEL_PATH = \"gui\"")
        if "channel(String" not in proto_text and "channel(String modId)" not in proto_text:
            errors.append("UltraGuiProtocol must expose channel(modId) helper")
        if "MSG_FEATURES" not in proto_text:
            errors.append("UltraGuiProtocol must define MSG_FEATURES for GuiFeaturesPacket")
    features_pkt = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/net/gui/GuiFeaturesPacket.java"
    if not features_pkt.is_file():
        errors.append("Missing GuiFeaturesPacket (client capability advertisement)")
    client_feat = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/net/gui/UltraClientFeatures.java"
    if not client_feat.is_file():
        errors.append("Missing UltraClientFeatures (server-side feature gap diagnostics)")
    else:
        feat_text = client_feat.read_text(encoding="utf-8")
        for needle in (
            "FEATURE_STYLE",
            "FEATURE_ACTIVE_TAB",
            "FEATURE_TITLE_ALIGN",
            "FEATURE_PLAYER_MODEL",
            "FEATURE_TEXT_INPUT",
            "FEATURE_HEADER_BOTTOM",
            "FEATURE_ACCENT",
            "FEATURE_FIXED_FOOTER",
            "FEATURE_PALETTE",
            "FEATURE_SOUND",
            "FEATURE_BUTTON_SKIN",
            "FEATURE_FRAME",
            "FEATURE_PAGED_BOOK",
            "FEATURE_PORTRAIT_FRAME",
            "FEATURE_COMBINED_FORMS",
            "FEATURE_ICON",
        ):
            if needle not in feat_text:
                errors.append(f"UltraClientFeatures must define {needle}")
        if "titleAlign" not in feat_text or "playerModel" not in feat_text:
            errors.append(
                "UltraClientFeatures impact/missing lists must cover titleAlign + playerModel"
            )
        if "textInput" not in feat_text:
            errors.append("UltraClientFeatures must cover textInput (inputsJson)")
        if "FEATURE_HEADER_BOTTOM" not in feat_text or "FEATURE_ACCENT" not in feat_text:
            errors.append("UltraClientFeatures must define headerBottom + accent shipped features")
        if "SHIPPED_ON_RECOMMENDED" not in feat_text:
            errors.append("UltraClientFeatures must define SHIPPED_ON_RECOMMENDED")
        shipped_block = feat_text.split("SHIPPED_ON_RECOMMENDED", 1)[-1].split(
            "PENDING_NEXT", 1
        )[0]
        if "FEATURE_HEADER_BOTTOM" not in shipped_block or "FEATURE_ACCENT" not in shipped_block:
            errors.append(
                "SHIPPED_ON_RECOMMENDED must include FEATURE_HEADER_BOTTOM + FEATURE_ACCENT "
                "(KaiPath 1.3.13+)"
            )
        # KaiPath 1.3.17 ships fixedFooter — must be in SHIPPED_ON_RECOMMENDED, not PENDING_NEXT.
        if "FEATURE_FIXED_FOOTER" not in shipped_block:
            errors.append(
                "SHIPPED_ON_RECOMMENDED must include FEATURE_FIXED_FOOTER (KaiPath 1.3.17)"
            )
        for shipped_name in (
            "FEATURE_PALETTE",
            "FEATURE_SOUND",
            "FEATURE_BUTTON_SKIN",
            "FEATURE_FRAME",
            "FEATURE_PAGED_BOOK",
            "FEATURE_PORTRAIT_FRAME",
            "FEATURE_COMBINED_FORMS",
        ):
            if shipped_name not in shipped_block:
                errors.append(
                    f"SHIPPED_ON_RECOMMENDED must include {shipped_name} (KaiPath 1.3.17)"
                )
        if "FEATURE_ICON" not in shipped_block:
            errors.append(
                "SHIPPED_ON_RECOMMENDED must include FEATURE_ICON "
                "(advertised by KaiPath 1.3.22)"
            )
        if "FEATURE_BODY_PARTS" not in shipped_block:
            errors.append(
                "SHIPPED_ON_RECOMMENDED must include FEATURE_BODY_PARTS "
                "(advertised by KaiPath 1.3.29)"
            )
        pending_decl = "public static final Set<String> PENDING_NEXT"
        if pending_decl not in feat_text:
            errors.append("UltraClientFeatures must declare public PENDING_NEXT set")
            pending_block = ""
        else:
            pending_block = feat_text.split(pending_decl, 1)[-1].split(
                "private static final Map", 1
            )[0]
        if pending_block and (
            "FEATURE_HEADER_BOTTOM" in pending_block
            or "FEATURE_ACCENT" in pending_block
            or "FEATURE_FIXED_FOOTER" in pending_block
            or "FEATURE_PALETTE" in pending_block
            or "FEATURE_COMBINED_FORMS" in pending_block
        ):
            errors.append(
                "PENDING_NEXT must not include headerBottom/accent/fixedFooter/"
                "palette/combined.forms.v1 (shipped on KaiPath 1.3.17)"
            )
        if pending_block and "FEATURE_ICON" in pending_block:
            errors.append(
                "PENDING_NEXT must not include FEATURE_ICON (shipped on KaiPath 1.3.22)"
            )
        # Exact token — FEATURE_BODY_PARTS_SYNC (message 3 world sync, pending on
        # JLDK) is allowed in PENDING_NEXT; only the shipped bodyParts id is not.
        if pending_block and re.search(r"FEATURE_BODY_PARTS(?!_SYNC)", pending_block):
            errors.append(
                "PENDING_NEXT must not include FEATURE_BODY_PARTS "
                "(playerModel.parts.bodyParts, shipped on KaiPath 1.3.29)"
            )
        if "headerBottom" not in feat_text or 'FEATURE_ACCENT_DOC = "accent"' not in feat_text:
            errors.append("UltraClientFeatures must expose headerBottom/accent doc spellings")
        if "fixedFooter" not in feat_text:
            errors.append("UltraClientFeatures must expose fixedFooter doc spelling")
        if 'FEATURE_FIXED_FOOTER_DOC = "fixedFooter"' not in feat_text:
            errors.append("UltraClientFeatures must define FEATURE_FIXED_FOOTER_DOC = \"fixedFooter\"")
    lm_net = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/net/gui/LmGuiNetwork.java"
    if lm_net.is_file():
        lm_net_text = lm_net.read_text(encoding="utf-8")
        if 'KAIPATH_RECOMMENDED_VERSION = "1.3.29"' not in lm_net_text:
            errors.append('LmGuiNetwork.KAIPATH_RECOMMENDED_VERSION must be "1.3.29"')
    if "roundTripStyleAndActiveTab" not in net:
        errors.append("LmGuiNetwork must round-trip style + activeTab in self-test")
    if "GuiFeaturesPacket" not in net:
        errors.append("LmGuiNetwork must register GuiFeaturesPacket")

    ids = (ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/gui/ultra/UltraScreenIds.java").read_text(
        encoding="utf-8"
    )
    if "bareOpenTarget" not in ids:
        errors.append("UltraScreenIds must expose bareOpenTarget for zero-config bare hub actions")
    if "requireValidActionId" not in ids:
        errors.append("UltraScreenIds must enforce requireValidActionId")

    # Every Ultra*Screens builder must go through UltraScreenSpec.send (panel fields 8–10).
    for path in sorted(ULTRA.glob("Ultra*Screens.java")):
        text = path.read_text(encoding="utf-8")
        if "UltraScreenSpec" not in text:
            errors.append(f"{path.name}: must build via UltraScreenSpec")
        if ".send(" not in text and "send(player)" not in text:
            errors.append(f"{path.name}: must call UltraScreenSpec.send(player)")
        if "new GuiOpenPacket" in text or "LmGuiNetwork.sendOpen" in text:
            errors.append(f"{path.name}: must not bypass UltraScreenSpec.send")

    if not DOC.is_file():
        errors.append("Missing docs/KAIPATH_GUI_PROTOCOL.md")
    else:
        doc = DOC.read_text(encoding="utf-8")
        for needle in (
            "panelWidth",
            "center",
            "@<screen>",
            "GuiActionPacket",
            "GuiFeaturesPacket",
            "PROTOCOL_VERSION",
            "requireValidActionId",
            "buttonsJson",
            "activeTab",
            "confirm",
            "danger",
            "dim",
            "normal",
            "open:",
            "do:",
            "tab:",
            "playerModel",
            "scrollBody",
            "buttonPad",
            "titleAlign",
            "bodyPad",
            "combined.forms.v1",
            "namespace:path",
            "kaipath-ultra.txt",
            "replace with your own",
        ):
            if needle not in doc:
                errors.append(f"KAIPATH_GUI_PROTOCOL.md missing {needle}")
    readme = ROOT.parents[1] / "tools" / "ultra-gui-kit" / "README.md"
    if not readme.is_file() or "kaipath-ultra.txt" not in readme.read_text(encoding="utf-8"):
        errors.append("ultra-gui-kit README must document META-INF/kaipath-ultra.txt discovery")
    marker = ROOT / "src/main/resources/META-INF/kaipath-ultra.txt"
    if not marker.is_file() or marker.read_text(encoding="utf-8").strip() != "legacymechanics":
        errors.append("LM jar must ship META-INF/kaipath-ultra.txt containing legacymechanics")

    # Collect actions from every Ultra*Screens builder.
    for path in sorted(ULTRA.glob("Ultra*Screens.java")):
        text = path.read_text(encoding="utf-8")
        screen_m = re.search(r'UltraScreenSpec\.of\("([^"]+)"\)', text)
        screen = screen_m.group(1) if screen_m else path.stem.replace("Ultra", "").replace("Screens", "").lower()
        # Hub uses hub + admin.
        screens = {screen}
        if "UltraScreenSpec.of(\"admin\")" in text or "of(\"admin\")" in text:
            screens.add("admin")
        for raw in extract_actions(text):
            if raw.startswith("nav:") or raw.startswith("open:") or raw.startswith("tab:"):
                continue
            if not raw.startswith("do:"):
                continue
            body = raw[3:]
            act = body.split(":", 1)[0]
            if act == "android_remove" or act == "":
                act = "android_remove" if "android_remove" in raw or "PAGE_ANDROID_REMOVE" in text else act
            # Map screen aliases
            for s in screens:
                known = KNOWN_DO.get(s, set())
                # admin screen shares hub do-actions
                if s == "admin":
                    known = known | KNOWN_DO.get("hub", set())
                if act and act not in known and s in KNOWN_DO:
                    # Allow dynamic prefixes listed in KNOWN_DO
                    if act not in known:
                        # Only flag if this screen's KNOWN_DO is the primary of()
                        primary = screen_m.group(1) if screen_m else s
                        if s == primary and act not in KNOWN_DO.get(primary, set()):
                            # Check all screens' known sets for this file's of() targets
                            pass
            # Validate against the of() screen for non-open buttons in that file
            primary = next(iter(screens))
            if "of(\"hub\")" in text and path.name == "UltraHubScreens.java":
                # hub + admin handled separately below
                continue
            known = KNOWN_DO.get(primary, set())
            if primary == "admin":
                known = KNOWN_DO["admin"] | KNOWN_DO["hub"]
            if act and known is not None and act not in known:
                # Skip empty dynamic stubs from regex
                if act.endswith("<dynamic>") or not act:
                    continue
                errors.append(f"{path.name}: do:{act} not in known set for screen {primary}")

    # Explicit per-file checks (clearer than the loop above).
    checks = [
        ("UltraHubScreens.java", "hub", KNOWN_DO["hub"]),
        ("UltraDifficultyScreens.java", "difficulty", KNOWN_DO["difficulty"]),
        ("UltraWeightShopScreens.java", "spar", KNOWN_DO["spar"]),
        ("UltraCharacterScreens.java", "character", KNOWN_DO["character"]),
        ("UltraPrestigeScreens.java", "prestige", KNOWN_DO["prestige"]),
        ("UltraProgressionScreens.java", "progression", KNOWN_DO["progression"]),
        ("UltraRivalScreens.java", "rival", KNOWN_DO["rival"]),
        ("UltraSparScreens.java", "spar", KNOWN_DO["spar"]),
        ("UltraConfigScreens.java", "config", KNOWN_DO["config"]),
        ("UltraSkillCheckScreens.java", "skillcheck", KNOWN_DO["skillcheck"]),
    ]
    for fname, screen, known in checks:
        path = ULTRA / fname
        text = path.read_text(encoding="utf-8")
        for raw in extract_actions(text):
            if not raw.startswith("do:"):
                continue
            act = raw[3:].split(":", 1)[0]
            if not act:
                continue
            if act not in known:
                errors.append(f"{fname}: unexpected do:{act} for {screen}")

    # Hub builder must never call footerBackToMain (hub IS the main menu).
    hub = (ULTRA / "UltraHubScreens.java").read_text(encoding="utf-8")
    if "do:reload" not in hub:
        errors.append("UltraHubScreens missing do:reload")
    if ".footerBackToMain()" in hub:
        errors.append(
            "UltraHubScreens must not call .footerBackToMain() "
            "(hub main has no back; Staff Admin uses footerBack to the hub)"
        )
    admin_m = re.search(
        r"private static void openAdmin\b.*?^\s*private static void ",
        hub,
        re.S | re.M,
    )
    admin_body = admin_m.group(0) if admin_m else ""
    if 'footerBack("nav:hub", "Home")' not in admin_body and "footerBack(\"nav:hub\", \"Home\")" not in admin_body:
        errors.append(
            "UltraHubScreens.openAdmin must pin Back to Home and Main menu (footerBack nav:hub)"
        )
    if "BACK_TO_MAIN" in admin_body:
        errors.append(
            "UltraHubScreens.openAdmin must not use a lone Back to main menu content button"
        )
    # Hub main packet shape: of("hub") builder path must not emit nav:hub.
    if 'UltraScreenSpec.of("hub")' in hub:
        main_chunk = hub.split('UltraScreenSpec.of("hub")', 1)[1].split("openAdmin", 1)[0]
        if ".footerBackToMain()" in main_chunk or '"nav:hub"' in main_chunk:
            errors.append(
                'UltraHubScreens openMain (of("hub")) must not add nav:hub / .footerBackToMain()'
            )
    # Full Ultra menu UI shape audit (stacked Back / Cancel+footer / non-ASCII / unbound fields).
    ui_audit = ROOT / "sim" / "audit_ultra_ui_menus.py"
    if ui_audit.is_file():
        import subprocess

        r = subprocess.run(
            [sys.executable, str(ui_audit)],
            cwd=str(ROOT),
            capture_output=True,
            text=True,
        )
        if r.returncode != 0:
            errors.append("audit_ultra_ui_menus.py failed (broken Ultra menu UI patterns)")
            if r.stdout:
                for line in r.stdout.strip().splitlines()[-12:]:
                    errors.append("  " + line)
    else:
        errors.append("missing sim/audit_ultra_ui_menus.py")

    # Wire text must sanitize fancy punctuation (Â· / â€ mojibake) and Back must not use "<".
    style_src = (ULTRA / "UltraStyle.java").read_text(encoding="utf-8")
    if "wireText(" not in style_src:
        errors.append("UltraStyle.wireText must sanitize Ultra wire copy to ASCII (+ section-sign)")
    if 'BACK = LABEL + "< Back"' in style_src or 'BACK = LABEL + "<Back"' in style_src:
        errors.append('UltraStyle.BACK must not use a leading "<" (renders as garbage on some clients)')
    if 'BACK = LABEL + "Back"' not in style_src:
        errors.append('UltraStyle.BACK should be LABEL + "Back"')
    if 'MAIN_MENU = LABEL + "Main menu"' not in style_src:
        errors.append('UltraStyle.MAIN_MENU should be LABEL + "Main menu"')
    if 'LABEL + "Back to "' not in style_src:
        errors.append('UltraStyle.backTo must prefix "Back to " on destination names')
    spec_src = (ULTRA / "UltraScreenSpec.java").read_text(encoding="utf-8")
    if "UltraStyle.wireText" not in spec_src:
        errors.append("UltraScreenSpec must run UltraStyle.wireText on lines/titles before send")
    if 'addProperty("footer", true)' not in spec_src and 'o.addProperty("footer", true)' not in spec_src:
        errors.append("UltraScreenSpec must emit button footer:true for pinned footer rows")
    if "footerBack(String" not in spec_src and "footerBack(String backActionId" not in spec_src:
        errors.append("UltraScreenSpec must expose footerBack(actionId, destination)")
    if "footerBackHome(String" not in spec_src:
        errors.append("UltraScreenSpec.footerBackHome must take a destination name")

    # Weight shop must accept up to MAX_WEIGHT_VALUE digits (not a stale 4-digit / 9999 / 100k cap).
    weight_shop = (ULTRA / "UltraWeightShopScreens.java").read_text(encoding="utf-8")
    if (
        "MAX_WEIGHT_VALUE = 10_000_000" not in weight_shop
        and "MAX_WEIGHT_VALUE = 10000000" not in weight_shop
    ):
        errors.append("UltraWeightShopScreens.MAX_WEIGHT_VALUE must be 10000000")
    if "MAX_WEIGHT_VALUE = 100_000" in weight_shop or "MAX_WEIGHT_VALUE = 100000" in weight_shop:
        errors.append("UltraWeightShopScreens.MAX_WEIGHT_VALUE must not remain at the old 100000 cap")
    if "s.length() > 4" in weight_shop or "length() > 4" in weight_shop:
        errors.append("UltraWeightShopScreens.parseWeightValue must not hard-cap at 4 digits (9999)")
    if "MAX_WEIGHT_DIGITS" not in weight_shop:
        errors.append("UltraWeightShopScreens must define MAX_WEIGHT_DIGITS from MAX_WEIGHT_VALUE")
    # Every open* that sends must pin a footer; forbid generic "Back" / Cancel-as-back duplicates.
    footer_call = re.compile(r"footerBack(?:Home|ToMain)?\s*\(")
    for path in sorted(ULTRA.glob("Ultra*Screens.java")):
        if path.name == "UltraHubScreens.java":
            continue
        src = path.read_text(encoding="utf-8")
        for m in re.finditer(
            r"(?:private|public|static)\s+[^{;]+?\b(open\w*)\s*\([^)]*\)\s*\{",
            src,
        ):
            name = m.group(1)
            if name == "open":
                continue
            i = src.find("{", m.end() - 1)
            depth = 0
            end = i
            for j in range(i, len(src)):
                if src[j] == "{":
                    depth += 1
                elif src[j] == "}":
                    depth -= 1
                    if depth == 0:
                        end = j + 1
                        break
            body = src[m.start() : end]
            if "send(player)" not in body:
                continue
            if not footer_call.search(body):
                errors.append(
                    f"{path.name}.{name}: must call footerBackToMain / footerBackHome(dest) / "
                    "footerBack(action, dest) so every page has a pinned way back"
                )
                continue
            # Generic exact "Back" next to footer = same-feeling duplicate.
            if re.search(r'(?:dim|confirm|danger)\(\s*"Back"\s*\)', body):
                errors.append(
                    f'{path.name}.{name}: dim/confirm("Back") with footer - name the destination '
                    "or drop the contextual Back"
                )
            if re.search(r'dim\(\s*"Cancel"\s*\)', body):
                errors.append(
                    f'{path.name}.{name}: dim("Cancel") with footer - use footerBack for exits '
                    "(keep danger Cancel only when it is a distinct action)"
                )
            # True duplicate: page:main Back next to footer.
            if 'dim("Back")' in body or 'dim("Back")' in body:
                errors.append(f"{path.name}.{name}: dim(\"Back\") with footer - keep one Back")

    # Footers must not ship a Refresh button.
    spec_text = SPEC.read_text(encoding="utf-8")
    for method, pattern in (
        ("footerBackToMain", r"public UltraScreenSpec footerBackToMain\(\)\s*\{(.*?)\n    \}"),
        (
            "footerBackHome",
            r"public UltraScreenSpec footerBackHome\(String destination\)\s*\{(.*?)\n    \}",
        ),
        (
            "footerBack",
            r"public UltraScreenSpec footerBack\(String backActionId, String destination\)\s*\{(.*?)\n    \}",
        ),
    ):
        m = re.search(pattern, spec_text, re.S)
        if m is None:
            errors.append(f"UltraScreenSpec.{method} not found")
            continue
        body = m.group(1)
        if "nav:refresh" in body or "Refresh" in body:
            errors.append(f"UltraScreenSpec.{method} must not include Refresh / nav:refresh")
        if method == "footerBackToMain":
            # Tabbed pages: right Main menu only — no left Back.
            if "BACK_TO_MAIN" in body:
                errors.append(
                    "UltraScreenSpec.footerBackToMain must use MAIN_MENU (not BACK_TO_MAIN)"
                )
            if "MAIN_MENU" not in body or '"nav:hub"' not in body:
                errors.append(
                    "UltraScreenSpec.footerBackToMain must place nav:hub + MAIN_MENU on the right"
                )
            if "backTo(" in body or "COL_L" in body:
                errors.append(
                    "UltraScreenSpec.footerBackToMain must not place a left Back button"
                )
    place_footer = spec_text.split("private UltraScreenSpec placeFooter", 1)
    footer_body = (
        place_footer[1].split("private int footerRightX", 1)[0] if len(place_footer) > 1 else ""
    )
    if footer_body.count("STYLE_NORMAL") < 2:
        errors.append("Both footer buttons must use STYLE_NORMAL so they do not look disabled")
    if "STYLE_DIM" in footer_body:
        errors.append("Footer buttons must not use STYLE_DIM")
    # Hint lines in these screens used to be dark-gray (UltraStyle.DIM) and read as a
    # disabled footer. Body copy stays LABEL; STYLE_DIM remains for unavailable actions.
    for screen_name in (
        "UltraDifficultyScreens.java",
        "UltraRivalScreens.java",
        "UltraSparScreens.java",
        "UltraCharacterScreens.java",
        "UltraProgressionScreens.java",
        "UltraWeightShopScreens.java",
        "UltraSkillCheckScreens.java",
    ):
        screen_src = (ULTRA / screen_name).read_text(encoding="utf-8")
        if "UltraStyle.DIM" in screen_src:
            errors.append(
                f"{screen_name} must not use UltraStyle.DIM (footer and hint copy stay readable)"
            )

    # Tabs are hub-style buttons under the title line; tabsJson stays empty for every screen.
    self_test_path = ULTRA / "UltraScreenSelfTest.java"
    self_test = self_test_path.read_text(encoding="utf-8")
    if "TABLESS_SCREEN_IDS" not in self_test or "isTablessScreen" not in self_test:
        errors.append(
            "UltraScreenSelfTest must define TABLESS_SCREEN_IDS / isTablessScreen "
            "(skillcheck + skills must not call .tabs())"
        )
    else:
        for sid in ("skillcheck", "skills"):
            if f'"{sid}"' not in self_test.split("TABLESS_SCREEN_IDS", 1)[1].split(";", 1)[0]:
                errors.append(f"UltraScreenSelfTest.TABLESS_SCREEN_IDS missing {sid}")
    if "tabsJson must be empty" not in self_test and "tabs are buttons under the title" not in self_test:
        errors.append(
            "UltraScreenSelfTest.validatePacket must require empty tabsJson "
            "(tabs render as buttons under the title line)"
        )
    if "appendTabButtons" not in spec_text_full or "tabsUnderTitle" not in spec_text_full:
        errors.append(
            "UltraScreenSpec must emit tabsUnderTitle + appendTabButtons "
            "(hub-like tabs under the header line)"
        )
    prestige = (ULTRA / "UltraPrestigeScreens.java").read_text(encoding="utf-8")
    if "openShopHub" not in prestige or "openTiers" not in prestige or "openEffects" not in prestige:
        errors.append("UltraPrestigeScreens must expose shop hub + Skills/Tiers/Effects submenus")
    if "page:skills" not in prestige or "page:tiers" not in prestige or "page:effects" not in prestige:
        errors.append("UltraPrestigeScreens shop hub must link page:skills / page:tiers / page:effects")
    if "page:exchange" not in prestige or "openExchange" not in prestige:
        errors.append("UltraPrestigeScreens must put exchange shop under Shop (page:exchange)")
    if "openConfirm" not in prestige or "do:confirm_now" not in prestige:
        errors.append("UltraPrestigeScreens must use GUI confirm subpage (do:confirm_now)")
    if 'do:confirm"' in prestige and "page:confirm" not in prestige:
        errors.append("UltraPrestigeScreens Prestige now must open confirm page (not chat do:confirm)")
    if "SKILLS_PER_PAGE" in prestige or "Next skills page" in prestige:
        errors.append("UltraPrestigeScreens skill shop must be single-page (no skills_p pagination)")
    if 'buttonLeft("open:prestige"' in prestige or 'buttonRight("tab:shop"' in prestige:
        errors.append(
            "UltraPrestigeScreens must not use open:prestige / tab:shop for skill-shop Prev/Next "
            "(those reopen Home / page 0)"
        )
    if re.search(r'"page:skills:\d+"', prestige):
        errors.append(
            "UltraPrestigeScreens must not emit page:skills:N buttons "
            "(nested colons are fragile)"
        )
    if "do:tier:" not in prestige or "do:majin" not in prestige or "do:mutant" not in prestige:
        errors.append("UltraPrestigeScreens must wire do:tier / do:majin / do:mutant favor buys")
    if 'labelValue("Lifetime prestiges"' not in prestige and "Lifetime prestiges" not in prestige:
        errors.append('UltraPrestigeScreens must label wallet "Lifetime prestiges" (not Completed)')
    if "Completed prestiges" in prestige:
        errors.append('UltraPrestigeScreens must not use "Completed prestiges"')
    if "Current prestige amount" not in prestige or "Prestige favor" not in prestige:
        errors.append(
            "UltraPrestigeScreens must use Current prestige amount / Prestige favor wording"
        )
    if "paintFavorBalance" not in prestige:
        errors.append("UltraPrestigeScreens shops must use favor-only header (paintFavorBalance)")
    if "PrestigeUniverseRoleWarn" not in prestige:
        errors.append(
            "UltraPrestigeScreens must warn GoD / Supreme Kai via PrestigeUniverseRoleWarn "
            "on the confirm subpage"
        )
    if "confirmFromGui" not in (
        ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeSystem.java"
    ).read_text(encoding="utf-8"):
        errors.append("PrestigeSystem must expose confirmFromGui for Ultra confirm subpage")
    role_warn = (
        ROOT
        / "src/main/java/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeUniverseRoleWarn.java"
    ).read_text(encoding="utf-8")
    if "DestroyerRoleService" not in role_warn or '"DESTROYER"' not in role_warn:
        errors.append(
            "PrestigeUniverseRoleWarn must reflect Noea DestroyerRoleService and match DESTROYER"
        )
    if "KaiApi" not in role_warn or "isSupremeKai" not in role_warn:
        errors.append("PrestigeUniverseRoleWarn must reflect KaiApi.isSupremeKai")
    if "GOD_OF_DESTRUCTION" not in role_warn or "SUPREME_KAI" not in role_warn:
        errors.append("PrestigeUniverseRoleWarn must expose GoD + Supreme Kai warning copy")
    prestige_sys = (
        ROOT
        / "src/main/java/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeSystem.java"
    ).read_text(encoding="utf-8")
    reply_confirm_body = prestige_sys.split("private static String replyConfirm", 1)[-1].split(
        "private static String replyCap", 1
    )[0]
    if "PrestigeUniverseRoleWarn" not in reply_confirm_body:
        errors.append("PrestigeSystem.replyConfirm must include PrestigeUniverseRoleWarn lines")
    build_sh = (ROOT / "build.sh").read_text(encoding="utf-8")
    if "progression/shop/PrestigeUniverseRoleWarn.class" not in build_sh:
        errors.append(
            "build.sh must merge PrestigeUniverseRoleWarn.class "
            "(next to PrestigeSystem) or prestige confirm NoClassDefFoundErrors"
        )
    difficulty_titles = (ULTRA / "UltraDifficultyScreens.java").read_text(encoding="utf-8")
    if "titles_p" not in difficulty_titles or "TITLES_PER_PAGE" not in difficulty_titles:
        errors.append("UltraDifficultyScreens must paginate titles via titles_pN (no hard 10-cap drop)")
    if re.search(r"placed\s*>=\s*10", difficulty_titles):
        errors.append("UltraDifficultyScreens must not hard-drop titles after placed >= 10")
    # openShop historically stacked "page:main" Back + footer (same destination).
    shop_m = re.search(
        r"static void openShop\b.*?^\s*static void ",
        weight_shop,
        re.S | re.M,
    )
    shop_body = shop_m.group(0) if shop_m else ""
    if '"page:main"' in shop_body and "footerBack" in shop_body:
        errors.append(
            "UltraWeightShopScreens.openShop must not stack page:main Back with footerBack*"
        )
    if "footerBackHome(\"Spar Home\")" not in shop_body and 'footerBackHome("Spar Home")' not in shop_body:
        errors.append(
            'UltraWeightShopScreens.openShop is a Sparring subpage — use '
            'footerBackHome("Spar Home") (no system tabs)'
        )
    if 'of("spar")' not in weight_shop and 'of("sparring")' not in weight_shop:
        errors.append('UltraWeightShopScreens must emit screenId spar (moved off Difficulty)')
    if "footerBackToMain()" in shop_body:
        errors.append(
            "UltraWeightShopScreens.openShop must not use footerBackToMain "
            "(subpage keeps Back + Main menu)"
        )
    difficulty_home = (ULTRA / "UltraDifficultyScreens.java").read_text(encoding="utf-8")
    home_m = re.search(
        r"private static void openHome\b.*?^\s*private static void ",
        difficulty_home,
        re.S | re.M,
    )
    home_body = home_m.group(0) if home_m else ""
    if "Combat rating" in home_body or "Title score" in home_body:
        errors.append("UltraDifficultyScreens.openHome must not show Combat rating / Title score")
    if "weights|Weights" in home_body or '"weights|' in home_body:
        errors.append("UltraDifficultyScreens.openHome must not offer Weights (moved to Sparring)")
    if "stats|CR|staff" not in difficulty_home and "stats|Combat Rating|staff" not in difficulty_home:
        errors.append("UltraDifficultyScreens must expose staff-only Combat Rating tab (stats|CR|staff)")
    spar_home = (ULTRA / "UltraSparScreens.java").read_text(encoding="utf-8")
    spar_home_m = re.search(
        r"private static void openHome\b.*?^\s*private static void ",
        spar_home,
        re.S | re.M,
    )
    spar_home_body = spar_home_m.group(0) if spar_home_m else ""
    diff_screens = (ULTRA / "UltraDifficultyScreens.java").read_text(encoding="utf-8")
    team_m = re.search(
        r"private static void openTeam\b.*?^\s*private static void ",
        diff_screens,
        re.S | re.M,
    )
    team_body = team_m.group(0) if team_m else ""
    notice_m = re.search(
        r"private static void openTeamNotice\b.*?^\s*private static void ",
        diff_screens,
        re.S | re.M,
    )
    notice_body = notice_m.group(0) if notice_m else ""
    if "footerBackHome(\"Home\")" not in team_body and 'footerBackHome("Home")' not in team_body:
        errors.append("UltraDifficultyScreens.openTeam must pin Back to Home + Main menu")
    if "openFeatureLocked" in team_body:
        errors.append(
            "UltraDifficultyScreens.openTeam must not use the tabbed locked page (no left Back)"
        )
    if "footerBackHome(\"Home\")" not in notice_body and 'footerBackHome("Home")' not in notice_body:
        errors.append("UltraDifficultyScreens.openTeamNotice must pin Back to Home + Main menu")
    diff_home_m = re.search(
        r"private static void openHome\b.*?^\s*private static void ",
        diff_screens,
        re.S | re.M,
    )
    diff_home_body = diff_home_m.group(0) if diff_home_m else ""
    if "eventlog|Event log" in diff_home_body:
        errors.append("Difficulty home must not list Event log (submenu of Server tools)")
    staff_m = re.search(
        r"private static void openStaffAdmin\b.*?^\s*private static void ",
        diff_screens,
        re.S | re.M,
    )
    staff_body = staff_m.group(0) if staff_m else ""
    if "page:eventlog" not in staff_body:
        errors.append("Server tools must keep the Event log submenu")
    if "do:toggle_personal" in team_body:
        errors.append("Rival teams mode page must not offer the difficulty toggle")
    if "do:mode:personal" not in team_body or "do:mode:threshold" not in team_body or "do:mode:full" not in team_body:
        errors.append("Rival teams page must keep Personal, Threshold, and Full team")
    if "Personal ON" in diff_screens or "Personal OFF" in diff_screens:
        errors.append("Difficulty toggle must say Difficulty - ON/OFF, not Personal")
    if '"Difficulty: ' in diff_screens:
        errors.append("Difficulty toggle must use the dash pattern (Difficulty - ON/OFF), not a colon")
    if 'UltraStyle.confirm("Difficulty - OFF")' in diff_screens or 'UltraStyle.confirm("Turn personal ON")' in diff_screens:
        errors.append("Difficulty OFF must use dim, not confirm/gold")
    if 'UltraStyle.dim("Difficulty - OFF")' not in diff_screens or "STYLE_DIM" not in diff_screens:
        errors.append("Difficulty OFF toggle must be dim")
    if '"Log ON"' in diff_screens or '"Turn ON"' in diff_screens or '"Turn OFF"' in diff_screens or '"Log OFF"' in diff_screens:
        errors.append("Event log toggles must use the state pattern, not verb labels (Turn ON/OFF, Log ON/OFF)")
    if '"Event log - ON"' not in diff_screens or '"Event log - OFF"' not in diff_screens:
        errors.append("Event log toggles must say Event log - ON/OFF")
    if 'dim("Staff details")' in diff_screens:
        errors.append("Staff details must be danger/red, not dim")
    if 'danger("Staff details")' not in diff_screens:
        errors.append("Difficulty menus must offer Staff details in danger/red")
    chat_menu = (ULTRA.parent / "DifficultyChatMenu.java").read_text(encoding="utf-8")
    if 'dim("[Staff details]")' in chat_menu or "danger(\"[Staff details]\")" not in chat_menu:
        errors.append("Difficulty chat Staff details must be danger/red")
    spec_src = (ULTRA / "UltraScreenSpec.java").read_text(encoding="utf-8")
    if 'STYLE_DIM.equals(resolvedStyle)' not in spec_src or 'accentCode = "8"' not in spec_src:
        errors.append("Dim button labels must stay dark gray (not the system accent)")
    if "weights|Weights" not in spar_home_body:
        errors.append("UltraSparScreens.openHome must offer Weights")
    if "labelValue(\"Session\"" in spar_home_body or "labelValue(\"Partner\"" in spar_home_body:
        errors.append("UltraSparScreens.openHome must not show Session/Partner (staff Admin only)")
    if "mentor|Mentor bonds" in spar_home_body or "top|Rankings" in spar_home_body:
        errors.append("UltraSparScreens.openHome must not offer Mentor bonds or a Rankings label")
    if "infamy|Hall of Infamy" not in spar_home_body:
        errors.append("UltraSparScreens.openHome must label the board Hall of Infamy")
    if "stats|Your stats" not in spar_home_body:
        errors.append("UltraSparScreens.openHome must offer Your stats")
    if 'danger("Staff Admin")' not in spar_home_body or '"danger"' not in spar_home_body:
        errors.append("UltraSparScreens.openHome Staff Admin must be danger/red")
    if "admin|Staff Admin" in spar_home_body:
        errors.append("UltraSparScreens.openHome must not use the default Staff Admin button")
    if "settings|TP Chat" not in spar_home_body:
        errors.append("UltraSparScreens.openHome must offer TP Chat")
    if "settings|Chat settings" in spar_home_body:
        errors.append("UltraSparScreens.openHome must not label TP Chat as Chat settings")
    if "TP Chat - ON" not in spar_home or "TP Chat - OFF" not in spar_home:
        errors.append("UltraSparScreens settings toggle must read TP Chat - ON/OFF")
    if '"TP - ON"' in spar_home or '"TP - OFF"' in spar_home:
        errors.append("UltraSparScreens must not keep the TP - ON/OFF toggle label")
    if "fight_report:" not in spar_home and "recentFightCards" not in spar_home:
        errors.append("UltraSparScreens must offer clickable fight reports from stats")
    if "Last three fights" not in spar_home:
        errors.append("UltraSparScreens stats must offer the last three fights submenu")
    if "Hall of Infamy" not in spar_home:
        errors.append("UltraSparScreens must expose Hall of Infamy (sparring HOF rename)")
    wars_m = re.search(
        r"private static void openWars\b.*?^\s*private static void ",
        spar_home,
        re.S | re.M,
    )
    wars_body = wars_m.group(0) if wars_m else ""
    if "dojoHallOfFameLines" in wars_body:
        errors.append("UltraSparScreens.openWars must not bury Hall of Chosen in the wars body")
    if "dojo_hof|Hall of Chosen" not in wars_body or "dojo_top_rp|Top dojos" not in wars_body:
        errors.append("UltraSparScreens.openWars must offer Hall of Chosen beside Top dojos")
    hof_m = re.search(
        r"private static void openHallOfChosen\b.*?^\s*private static void ",
        spar_home,
        re.S | re.M,
    )
    hof_body = hof_m.group(0) if hof_m else ""
    if "dojoHallOfFameLines" not in hof_body:
        errors.append("UltraSparScreens.openHallOfChosen must show Hall of Chosen season lines")
    dojo_m = re.search(
        r"private static void openDojo\b.*?^\s*private static void ",
        spar_home,
        re.S | re.M,
    )
    dojo_body = dojo_m.group(0) if dojo_m else ""
    if "dojo_hof|Hall of Chosen" in dojo_body:
        errors.append("UltraSparScreens Dojo tab must not duplicate Hall of Chosen")
    if "dojo_members|Dojo members" not in dojo_body:
        errors.append("UltraSparScreens Dojo tab must offer Dojo members")
    if "mentor|Actions" not in dojo_body:
        errors.append("UltraSparScreens Dojo tab must offer Actions")
    members_m = re.search(
        r"private static void openDojoMembers\b.*?^\s*private static void ",
        spar_home,
        re.S | re.M,
    )
    members_body = members_m.group(0) if members_m else ""
    if "Your dojo" not in members_body or "Your mentor's dojo" not in members_body:
        errors.append("UltraSparScreens Dojo members must toggle your dojo and your mentor's dojo")
    if "page:mentor" in members_body or "Actions" in members_body:
        errors.append(
            "UltraSparScreens Dojo members must not duplicate Actions "
            "(Actions stays on the Dojo tab)"
        )
    if 'dojo|Home' in spar_home:
        errors.append("UltraSparScreens Dojo tab must not keep a Home button")
    if "§8??" in (ULTRA.parent / "SparGuiApi.java").read_text(encoding="utf-8"):
        errors.append("SparGuiApi must not use ?? decorative sparring headers")
    rival = (ULTRA / "UltraRivalScreens.java").read_text(encoding="utf-8")
    if 'confirm("Reload")' in rival and 'confirm("Reload data")' not in rival:
        errors.append('UltraRivalScreens staff admin must label refresh "Reload data"')
    # Challenge Accept/Decline only via placePerChallengeButtons on Challenge tab.
    home_m = re.search(
        r"private static void openHome\b.*?^\s*private static void ",
        rival,
        re.S | re.M,
    )
    actions_m = re.search(
        r"private static void openActions\b.*?^\s*private static void ",
        rival,
        re.S | re.M,
    )
    home_body = home_m.group(0) if home_m else ""
    actions_body = actions_m.group(0) if actions_m else ""
    if 'danger("Staff Admin")' not in home_body:
        errors.append("UltraRivalScreens.openHome Staff Admin must be danger/red")
    if "admin|Staff Admin" in home_body:
        errors.append("UltraRivalScreens.openHome must not use the default Staff Admin button")
    if "challenge_accept" in home_body or "challenge_decline" in home_body:
        errors.append("UltraRivalScreens.openHome must not duplicate challenge Accept/Decline")
    if "challenge_accept" in actions_body or "challenge_decline" in actions_body:
        errors.append("UltraRivalScreens.openActions must not duplicate challenge Accept/Decline")
    if "placePerChallengeButtons" not in rival or "challenge_accept" not in rival:
        errors.append("UltraRivalScreens must keep per-challenge Accept on Challenge via placePerChallengeButtons")
    detail_m = re.search(
        r"private static void openListDetail\b.*?^\s*private static void ",
        rival,
        re.S | re.M,
    )
    detail = detail_m.group(0) if detail_m else ""
    if "displayPickerArg" not in detail:
        errors.append("UltraRivalScreens.openListDetail must resolve Target via RivalGuiApi.displayPickerArg")
    list_m = re.search(
        r"private static void openList\b.*?^\s*private static void ",
        rival,
        re.S | re.M,
    )
    list_block = list_m.group(0) if list_m else ""
    if "displayPickerArg" not in list_block:
        errors.append("UltraRivalScreens.openList must resolve button labels via RivalGuiApi.displayPickerArg")
    if "lastIndexOf(':')" in list_block or "substring(arg.lastIndexOf" in list_block:
        errors.append("UltraRivalScreens.openList must not substring uuid: args for button labels")
    replace_m = re.search(
        r"private static void openReplacePick\b.*?^\s*private static void ",
        rival,
        re.S | re.M,
    )
    replace_block = replace_m.group(0) if replace_m else ""
    if "labeledArgs" not in replace_block and (
        "displayPickerArg" not in replace_block or '+ "|"' not in replace_block
    ):
        errors.append(
            "UltraRivalScreens.openReplacePick must resolve Mutual names via labeledArgs/displayPickerArg"
        )
    if "replace_confirm:" not in replace_block and "replace_confirm:" not in rival:
        errors.append("UltraRivalScreens mutual drop must go through replace_confirm before accept_replace")
    if 'placeNameButtons(spec, "accept_replace", RivalGuiApi.currentRivalArgs' in rival:
        errors.append(
            "UltraRivalScreens.openReplacePick must not pass raw currentRivalArgs to placeNameButtons"
        )
    if 'placeNameButtons(spec, "accept", incoming' in rival or 'placeNameButtons(spec, "decline", incoming' in rival:
        errors.append(
            "UltraRivalScreens.openRequests must label incoming declares via displayPickerArg "
            "(not raw placeNameButtons)"
        )
    if "onlinePlayerNames" in rival and "onlinePlayerArgs" not in rival:
        errors.append("UltraRivalScreens player pickers must use onlinePlayerArgs (UUID), not names only")
    if "PICK_PER_PAGE" not in rival and "pick_declare:" not in rival:
        errors.append("UltraRivalScreens player pickers must paginate (pick_*:N)")
    if 'do:challenge:accept"' in rival or "do:challenge:accept\"" in rival:
        # Generic challenge accept without a counterparty is ambiguous when multiple pending.
        if "challenge_accept:" not in rival:
            errors.append("UltraRivalScreens must use per-challenge do:challenge_accept:uuid (not generic)")
    if "do:challenge:accept" in rival and "challenge_accept:" not in rival:
        errors.append("UltraRivalScreens must wire per-challenge Accept via challenge_accept")
    if "hasIncomingDeclareFrom" not in rival:
        errors.append("UltraRivalScreens declare picker must Offer Accept when they already declared you")
    if "Accept their declare" not in rival or "accept_confirm:" not in rival:
        errors.append(
            "UltraRivalScreens must confirm Accept their declare (not silent Declare→Accept)"
        )
    # Home is navigation. Standing (RP/tier/nemesis/mutual) is the Info tab.
    # Silent/Declare/Accept live on the Rival tab (page id actions) via pick→type.
    info_m = re.search(
        r"private static void openInfo\b.*?^\s*private static void ",
        rival,
        re.S | re.M,
    )
    info_body = info_m.group(0) if info_m else ""
    if "nemesisNames" not in info_body or "mutualNames" not in info_body:
        errors.append("UltraRivalScreens.openInfo must show Nemesis and Mutual names")
    if "nemesisNames" in home_body or "mutualNames" in home_body:
        errors.append("UltraRivalScreens.openHome must not keep rival info (Info tab)")
    if "settings|TP Chat" not in home_body:
        errors.append("UltraRivalScreens.openHome settings button must read TP Chat")
    if "settings|Settings" in home_body:
        errors.append("UltraRivalScreens.openHome must not label TP Chat as Settings")
    if '"actions|Rival"' not in rival or '"info|Info"' not in rival:
        errors.append("UltraRivalScreens tabs must be Home, Rival, Challenge, and Info")
    if '"actions|Actions"' in rival:
        errors.append("UltraRivalScreens must not keep the Actions tab label")
    if "Wins" in home_body or "Losses" in home_body:
        errors.append("UltraRivalScreens.openHome must not show Wins/Losses (Challenge tab)")
    if "Tiers: Silent" in home_body or "Nemesis rises from challenge deaths" in home_body:
        errors.append("UltraRivalScreens.openHome must not explain tiers or Nemesis challenge deaths")
    if "rival_type:" not in rival or "rival_pick" not in rival or "placeRivalListButtons" not in rival:
        errors.append(
            "UltraRivalScreens Actions must consolidate Silent/Declare/Requests via pick→rival_type "
            "and show the rival list"
        )
    if "Silent (private)" not in rival:
        errors.append("UltraRivalScreens rival_type must offer Silent (private)")
    challenge_m = re.search(
        r"private static void openChallenge\b.*?^\s*private static void ",
        rival,
        re.S | re.M,
    )
    challenge_body = challenge_m.group(0) if challenge_m else ""
    if 'labelValue("Wins"' not in challenge_body or 'labelValue("Losses"' not in challenge_body:
        errors.append("UltraRivalScreens.openChallenge must show Wins and Losses")
    if "cancel_mutual_replace" not in rival or "replace_confirm:" not in rival:
        errors.append("UltraRivalScreens mutual swap needs Decline instead + replace_confirm step")
    if "demote" not in rival.lower() or "Mutual to Declared" not in rival:
        errors.append("UltraRivalScreens replace confirm must warn Mutual→Declared demotion")
    if "needsMutualReplacePick" not in (ULTRA / "UltraGuiActions.java").read_text(encoding="utf-8"):
        errors.append("UltraGuiActions must reopen pick_replace_mutual after Accept hits the slot cap")
    config = (ULTRA / "UltraConfigScreens.java").read_text(encoding="utf-8")
    review_m = re.search(
        r"private static void openReview\b.*?^\s*(?:public|private) static ",
        config,
        re.S | re.M,
    )
    review = review_m.group(0) if review_m else ""
    if 'buttonLeft("do:save_all"' in review and 'buttonRight("do:discard_all"' in review:
        errors.append("UltraConfigScreens.openReview must stack Confirm save / Discard all (not side-by-side)")
    typed_m = re.search(
        r"private static String typedValueFromArgs\b.*?^\s*(?:public|private) static ",
        config,
        re.S | re.M,
    )
    typed_block = typed_m.group(0) if typed_m else ""
    if "LOGGER.info(" in typed_block:
        errors.append("UltraConfigScreens.typedValueFromArgs must log at DEBUG (not INFO)")
    if "Typed fields need KaiPath textInput" in config:
        errors.append("UltraConfigScreens player copy must not mention protocol field textInput")
    weight = (ULTRA / "UltraWeightShopScreens.java").read_text(encoding="utf-8")
    if "GuiOpen field 11" in weight or "field 11 / textInput" in weight:
        errors.append("UltraWeightShopScreens player copy must not mention GuiOpen field 11 / textInput")
    spar = (ULTRA / "UltraSparScreens.java").read_text(encoding="utf-8")
    mentor_m = re.search(
        r"private static void openPendingDecide\b.*?^\s*private static void ",
        spar,
        re.S | re.M,
    )
    mentor = mentor_m.group(0) if mentor_m else ""
    if 'buttonLeft("do:mentor_accept:' in mentor and 'buttonRight("do:mentor_decline:' in mentor:
        errors.append("UltraSparScreens mentor Accept/Decline must stack vertically (misclick risk)")
    dojo_m = re.search(
        r"private static void openDojoWarDecide\b.*?^\s*private static void ",
        spar,
        re.S | re.M,
    )
    dojo = dojo_m.group(0) if dojo_m else ""
    if 'buttonLeft("do:dojo_accept:' in dojo and 'buttonRight("do:dojo_war_cancel:' in dojo:
        errors.append("UltraSparScreens dojo war Accept/Cancel must stack vertically (misclick risk)")
    if "print status" in spar:
        errors.append('UltraSparScreens admin must say "show status" not "print status"')
    hub = (ULTRA / "UltraHubScreens.java").read_text(encoding="utf-8")
    admin_tile = re.search(r'new HubTile\(\s*"admin"[\s\S]*?true\)', hub)
    if not admin_tile or "STYLE_NORMAL" not in admin_tile.group(0):
        errors.append("UltraHubScreens Staff Admin tile must use STYLE_NORMAL + accent (not danger)")
    if "hubTileAccentCode" not in hub and "systemAccent" not in hub:
        errors.append("UltraHubScreens must color tiles via hubTileAccentCode / systemAccent")
    # Hub: yellow/confirm means selected — nothing is selected on the hub.
    open_main = hub.split("private static void openMain", 1)[-1].split(
        "private static void openAdmin", 1
    )[0]
    for system in ("difficulty", "skillcheck", "skills", "character", "rival", "spar", "prestige", "admin"):
        tile = re.search(rf'new HubTile\(\s*"{system}"[\s\S]*?true\)', open_main)
        if not tile:
            tile = re.search(rf'new HubTile\(\s*"{system}"[\s\S]*?false\)', open_main)
        if tile and "STYLE_CONFIRM" in tile.group(0):
            errors.append(
                f'UltraHubScreens "{system}" tile must not use STYLE_CONFIRM on the hub'
            )
        if tile and "STYLE_DANGER" in tile.group(0):
            errors.append(
                f'UltraHubScreens "{system}" tile must not use STYLE_DANGER (use accent)'
            )
    # headerBottom + accent protocol fields; tab styles use accent not shared yellow.
    spec_src = (ULTRA / "UltraScreenSpec.java").read_text(encoding="utf-8")
    if '"headerBottom"' not in spec_src and "headerBottom" not in spec_src:
        errors.append("UltraScreenSpec must emit layoutJson.headerBottom")
    if "HEADER_BOTTOM" not in style or "HEADER_BOTTOM_TABS" not in style:
        errors.append("UltraStyle must define HEADER_BOTTOM / HEADER_BOTTOM_TABS")
    if "PANEL_COMPACT_MIN_H" not in style:
        errors.append("UltraStyle must define PANEL_COMPACT_MIN_H for hub packing")
    if "systemAccentCode" not in style or "ACCENT_DIFFICULTY" not in style:
        errors.append("UltraStyle must define per-system accent constants + systemAccentCode")
    if "tabStyle(" not in spec_src or "staffOnly()" not in spec_src:
        errors.append("UltraScreenSpec must style tabs via tabStyle/staffOnly")
    if 'addProperty("accent"' not in spec_src and 'o.addProperty("accent"' not in spec_src:
        errors.append("UltraScreenSpec must emit layoutJson.accent and/or button accent")
    if DOC.is_file():
        proto = DOC.read_text(encoding="utf-8")
        if "headerBottom" not in proto or "tabsUnderTitle" not in proto:
            errors.append("KAIPATH_GUI_PROTOCOL.md §4 must document headerBottom + tabsUnderTitle")
        if "`accent`" not in proto and "button `accent`" not in proto:
            errors.append("KAIPATH_GUI_PROTOCOL.md §4 must document accent")
    # Rival Challenge/Actions Accept+Decline must stack (same misclick risk as spar mentor/dojo).
    if re.search(
        r'buttonLeft\("do:challenge:accept"[^\n]*\n[^\n]*buttonRight\("do:challenge:decline"',
        rival,
    ):
        errors.append(
            "UltraRivalScreens Accept/Decline challenge must stack vertically (not side-by-side)"
        )
    difficulty = (ULTRA / "UltraDifficultyScreens.java").read_text(encoding="utf-8")
    for label, text in (
        ("UltraDifficultyScreens", difficulty),
        ("UltraWeightShopScreens", weight),
    ):
        if "free for staff" in text or "free (staff)" in text:
            errors.append(f'{label} must capitalize staff free cost as "Free ..."')
    if "TAB_ROW_Y" not in style:
        errors.append("UltraStyle must define TAB_ROW_Y for tabs under the title line")
    skill_ui = (ULTRA / "UltraSkillCheckScreens.java").read_text(encoding="utf-8")
    if ".tabs(" in skill_ui:
        errors.append("UltraSkillCheckScreens must not call .tabs() (no tab bar)")
    if 'skillsLines(player, "core")' in skill_ui:
        errors.append(
            "UltraSkillCheckScreens must use compact skillcheck lines "
            "(not coreLines with DMZ/Ki/Energy header)"
        )
    if "skillcheck" not in skill_ui and "skillCheckGuiLines" not in skill_ui:
        errors.append("UltraSkillCheckScreens must load skillcheck/compact skill lines")
    if ".lineGap(" not in skill_ui:
        errors.append("UltraSkillCheckScreens must tighten lineGap for dense skill list")
    unlock = (ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/progression/shop/SkillUnlockService.java").read_text(
        encoding="utf-8"
    )
    if "skillCheckGuiLines" not in unlock:
        errors.append("SkillUnlockService must expose skillCheckGuiLines (skills-only Ultra home)")

    spec_fit = (ULTRA / "UltraScreenSpec.java").read_text(encoding="utf-8")
    if "clipPlain" not in spec_fit:
        errors.append("UltraScreenSpec must clipPlain before fitAsciiLabel so buttons do not grow ...")
    if 'addProperty("portraitFrame"' in spec_fit:
        errors.append("UltraScreenSpec must not emit playerModel.portraitFrame (no preview border)")
    rival_ui = (ULTRA / "UltraRivalScreens.java").read_text(encoding="utf-8")
    chal_m = re.search(
        r"private static void openChallenge\b.*?^\s*private static void ",
        rival_ui,
        re.S | re.M,
    )
    chal_body = chal_m.group(0) if chal_m else ""
    if (
        'buttonLeft("page:pick_spectate"' not in chal_body
        or 'buttonRight("do:spectate_stop"' not in chal_body
    ):
        errors.append("Rival Challenge must pair Spectate with Stop spectate on one row")
    if "Send challenge..." in chal_body or "Spectate..." in chal_body:
        errors.append("Rival Challenge labels must not trail an ellipsis")
    if "Challenge history" in chal_body or "records:history" in chal_body:
        errors.append(
            "UltraRivalScreens.openChallenge must not duplicate Records as Challenge history"
        )
    gui_actions = (ULTRA / "UltraGuiActions.java").read_text(encoding="utf-8")
    reopen_m = re.search(
        r"private static String rivalListReopen\b.*?^\s*private static String ",
        gui_actions,
        re.S | re.M,
    )
    reopen_body = reopen_m.group(0) if reopen_m else ""
    if 'case "remove"' not in reopen_body or '"actions"' not in reopen_body:
        errors.append("rivalListReopen must send remove back to the actions rival list")
    if "list_detail" in reopen_body:
        errors.append("rivalListReopen must not stay on list_detail after a list mutation")
    footer_re = re.compile(r'footerBack(?:Home)?\(\s*(?:"[^"]*"\s*,\s*)?"([^"]+)"')
    banned = (
        "Personal difficulty - ON",
        "Personal difficulty - OFF",
        "Confirm summon - locked",
        "Challenge another dojo",
        "Prestige now - need higher level",
        "Buy Majin - remove Mutant",
        "Drop a Mutual to accept",
    )
    for path in sorted(ULTRA.glob("Ultra*.java")):
        text = path.read_text(encoding="utf-8")
        for m in footer_re.finditer(text):
            dest = m.group(1)
            if len("Back to " + dest) > 22:
                errors.append(f"{path.name} footer {dest!r} exceeds the 22-glyph button")
        for phrase in banned:
            for line in text.splitlines():
                if phrase not in line:
                    continue
                if not any(
                    tok in line
                    for tok in ("button(", "buttonLeft", "buttonRight", "placePageButtons")
                ):
                    continue
                errors.append(f"{path.name} still uses long button copy {phrase!r}")
                break

    print("=== Ultra action / panel audit ===")
    for e in errors:
        print(f"  FAIL  {e}")
    if errors:
        print(f"\n{len(errors)} error(s)")
        return 1
    print("  OK  action ids covered; @screen qualify; panel 350×300; protocol doc")
    return 0


if __name__ == "__main__":
    sys.exit(main())
