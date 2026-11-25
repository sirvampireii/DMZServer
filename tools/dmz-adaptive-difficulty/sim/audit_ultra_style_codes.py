#!/usr/bin/env python3
"""Fail if gui/ultra callers embed UTF-8 section-sign bytes (C2 A7).

UltraStyle owns the mark. Every other ultra classfile must contain zero raw
C2 A7 byte pairs. Sources must not contain a bare section-sign character.
"""
from __future__ import annotations

import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/gui/ultra"
MARK_CHAR = "\u00a7"
MARK = MARK_CHAR.encode("utf-8")  # C2 A7


def java_string_hash(s: str) -> int:
    h = 0
    for ch in s:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    if h >= 0x80000000:
        h -= 0x100000000
    return h


def audit_sources() -> list[str]:
    import re

    errors: list[str] = []
    if not SRC.is_dir():
        return [f"Missing {SRC}"]
    for path in sorted(SRC.glob("*.java")):
        text = path.read_text(encoding="utf-8")
        for i, line in enumerate(text.splitlines(), 1):
            if MARK_CHAR in line:
                errors.append(
                    f"{path.name}:{i}: bare section-sign — use UltraStyle / \\u00A7 in UltraStyle only"
                )
        # String.hashCode of switch cases is embedded in lookupswitch tables.
        if path.name.startswith("UltraStyle"):
            continue
        for m in re.finditer(r'case\s+"([^"]+)"', text):
            raw = java_string_hash(m.group(1)).to_bytes(4, "big", signed=True)
            if MARK in raw:
                errors.append(
                    f'{path.name}: switch case "{m.group(1)}" hashCode bytes are C2 A7 — rename alias'
                )
    return errors


def audit_class_bytes(name: str, data: bytes) -> list[str]:
    short = name.rsplit("/", 1)[-1]
    if short.startswith("UltraStyle"):
        # UltraStyle.class owns the mark. Its inner classes (the 4.7.84 Shortfall enum, a switch map) may hold none.
        if short == "UltraStyle.class" and MARK not in data:
            return [f"{short}: expected section-sign mark bytes in UltraStyle"]
        return []
    n = data.count(MARK)
    if n:
        return [f"{short}: {n} raw section-sign byte pair(s) (C2 A7) — must be zero outside UltraStyle"]
    return []


def audit_jar(jar: Path) -> list[str]:
    errors: list[str] = []
    if not jar.is_file():
        return [f"Missing jar {jar}"]
    with zipfile.ZipFile(jar) as zf:
        for name in sorted(zf.namelist()):
            norm = name.replace("\\", "/")
            if not norm.endswith(".class") or "/gui/ultra/" not in norm:
                continue
            errors.extend(audit_class_bytes(norm, zf.read(name)))
    return errors


def main() -> int:
    errors = audit_sources()
    jar_arg = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    if jar_arg is None:
        mods = ROOT.parents[1] / "mods"
        jars = sorted(mods.glob("LegacyMechanics-*.jar"), key=lambda p: p.name)
        jar_arg = jars[-1] if jars else None
    if jar_arg is not None:
        errors.extend(audit_jar(jar_arg))
    print("=== Ultra style codes (raw C2 A7 only in UltraStyle) ===")
    for e in errors:
        print(f"  FAIL  {e}")
    if errors:
        print(f"\n{len(errors)} error(s)")
        return 1
    print("  OK  zero C2 A7 outside UltraStyle; UltraStyle owns the mark")
    return 0


if __name__ == "__main__":
    sys.exit(main())
