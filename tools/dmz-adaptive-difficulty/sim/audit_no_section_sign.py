#!/usr/bin/env python3
"""Fail-closed: no raw section-sign (U+00A7) outside UltraStyle, and no non-ASCII
in Ultra / selected GuiApi player-facing string literals.

Standing rule for LegacyMechanics player-facing text:
  - Never embed a bare section-sign character in new Java sources.
  - Route all colors through UltraStyle constants / helpers.
  - UltraStyle.java is the only allowed owner of the mark (\\u00A7).
  - Ultra wire copy and GuiApi player strings must be plain ASCII
    (no em-dashes, ellipsis, curly quotes, middle dots, etc.) so KaiPath
    / Minecraft chat cannot mojibake them (e.g. withe... -> witheâ€!).

Enforced on:
  - gui/ultra/** (except UltraStyle.java for the mark; ASCII on all)
  - DifficultyChatMenu.java / DifficultyActions.java — previously
    reintroduced § on feature branches; must stay at zero forever
  - DifficultyTeamGuiApi.java — reachable from Ultra via UltraGuiActions /
    UltraDifficultyScreens; colors must go through UltraStyle
  - *GuiApi.java under gui/ — non-ASCII outside § is rejected
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty"
MARK = "\u00a7"

ULTRA = SRC / "gui" / "ultra"
MUST_STAY_CLEAN = [
    SRC / "gui" / "DifficultyChatMenu.java",
    SRC / "service" / "DifficultyActions.java",
    SRC / "gui" / "DifficultyTeamGuiApi.java",
    # Cleaned 4.6.208: raw section signs routed through UltraStyle - stay clean.
    SRC / "character" / "CosmeticBodyPartService.java",
    SRC / "progression" / "form" / "FirstSuperformMastery.java",
    # Cleaned 4.6.213: God Ki shop copy routed through UltraStyle - stay clean.
    SRC / "progression" / "shop" / "GodKiEnhancementShop.java",
    # Cleaned 4.6.233: config editor strings routed through UltraStyle - stay clean.
    SRC / "config" / "editor" / "ConfigEditor.java",
]
ALLOW_MARK = {(ULTRA / "UltraStyle.java").resolve()}

# Unicode escapes that are fine in UltraStyle only (the mark).
MARK_ESCAPE = re.compile(r"\\u00[aA]7")
STRING_LIT = re.compile(r'"((?:\\.|[^"\\])*)"')
# Common non-ASCII that historically mojibaked on the wire.
BANNED_IN_STRINGS = {
    "\u2014",  # em dash
    "\u2013",  # en dash
    "\u2026",  # ellipsis
    "\u2212",  # minus
    "\u00b1",  # plus-minus
    "\u25cf",  # bullet
    "\u2192",  # arrows
    "\u2190",
    "\u2194",
    "\u00b7",  # middle dot
    "\u00d7",  # multiply
    "\u00ab",
    "\u00bb",
    "\u201c",
    "\u201d",
    "\u2018",
    "\u2019",
    "\u00a0",
}
BANNED_ESCAPES = {
    r"\u2014",
    r"\u2013",
    r"\u2026",
    r"\u2212",
    r"\u00b1",
    r"\u25cf",
    r"\u2192",
    r"\u2190",
    r"\u2194",
    r"\u00b7",
    r"\u00d7",
    r"\u00ab",
    r"\u00bb",
    r"\u201c",
    r"\u201d",
    r"\u2018",
    r"\u2019",
    r"\u00a0",
}


def iter_section_sign_files() -> list[Path]:
    seen: set[Path] = set()
    out: list[Path] = []
    for path in MUST_STAY_CLEAN:
        if path.is_file():
            rp = path.resolve()
            if rp not in seen:
                seen.add(rp)
                out.append(path)
    if ULTRA.is_dir():
        for path in sorted(ULTRA.glob("*.java")):
            rp = path.resolve()
            if rp in seen:
                continue
            seen.add(rp)
            out.append(path)
    return out


def iter_ascii_files() -> list[Path]:
    """Ultra screens + all GuiApi sources that can put copy on the wire."""
    seen: set[Path] = set()
    out: list[Path] = []
    if ULTRA.is_dir():
        for path in sorted(ULTRA.glob("*.java")):
            rp = path.resolve()
            if rp in seen:
                continue
            seen.add(rp)
            out.append(path)
    gui = SRC / "gui"
    if gui.is_dir():
        for path in sorted(gui.glob("*GuiApi.java")):
            rp = path.resolve()
            if rp in seen:
                continue
            seen.add(rp)
            out.append(path)
    return out


def audit_section_sign() -> list[str]:
    errors: list[str] = []
    for path in iter_section_sign_files():
        if path.resolve() in ALLOW_MARK:
            continue
        text = path.read_text(encoding="utf-8")
        for i, line in enumerate(text.splitlines(), 1):
            if MARK in line:
                rel = path.relative_to(ROOT)
                errors.append(f"{rel}:{i}: raw section-sign - use UltraStyle (no bare §)")
    return errors


def _string_has_banned_unicode(body: str) -> str | None:
    """Return a short reason if the Java string literal body has banned unicode."""
    for ch in BANNED_IN_STRINGS:
        if ch in body:
            return f"U+{ord(ch):04X} {ch!r}"
    lower_esc = body
    for esc in BANNED_ESCAPES:
        if esc in lower_esc or esc.lower() in lower_esc.lower():
            # Avoid flagging \\u00A7
            if esc.lower() == r"\u00a7":
                continue
            if esc.lower() in body.lower():
                return f"escape {esc}"
    # Any other non-ASCII except § (GuiApi legacy chat colors still use §)
    for ch in body:
        if ord(ch) > 127 and ch != MARK:
            return f"non-ASCII U+{ord(ch):04X}"
    return None


def audit_ascii_player_strings() -> list[str]:
    errors: list[str] = []
    for path in iter_ascii_files():
        text = path.read_text(encoding="utf-8")
        allow_mark_escape = path.resolve() in ALLOW_MARK
        for i, line in enumerate(text.splitlines(), 1):
            for m in STRING_LIT.finditer(line):
                body = m.group(1)
                if allow_mark_escape and (MARK in body or MARK_ESCAPE.search(body)):
                    # UltraStyle may own the mark; still ban other unicode there.
                    probe = MARK_ESCAPE.sub("", body.replace(MARK, ""))
                else:
                    probe = body
                reason = _string_has_banned_unicode(probe)
                if reason is None:
                    continue
                # GuiApi may still contain § for legacy CNPC/chat paths.
                if path.name.endswith("GuiApi.java") and reason.startswith("non-ASCII U+00A7"):
                    continue
                rel = path.relative_to(ROOT)
                errors.append(f"{rel}:{i}: non-ASCII in string ({reason}) - use plain ASCII")
    return errors


def audit() -> list[str]:
    return audit_section_sign() + audit_ascii_player_strings()


def main() -> int:
    section_errors = audit_section_sign()
    ascii_errors = audit_ascii_player_strings()
    print("=== No raw section-sign outside UltraStyle ===")
    for e in section_errors:
        print(f"  FAIL  {e}")
    if section_errors:
        print(f"\n{len(section_errors)} section-sign error(s)")
    else:
        print(
            "  OK  DifficultyChatMenu + DifficultyActions + DifficultyTeamGuiApi "
            "+ gui/ultra: zero bare § (UltraStyle owns the mark)"
        )

    print("\n=== Plain ASCII in Ultra / GuiApi player-facing strings ===")
    for e in ascii_errors:
        print(f"  FAIL  {e}")
    if ascii_errors:
        print(f"\n{len(ascii_errors)} ASCII error(s) - replace with plain ASCII equivalents")
    else:
        print("  OK  gui/ultra + *GuiApi.java string literals are plain ASCII (besides legacy § in GuiApi)")

    if section_errors or ascii_errors:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
