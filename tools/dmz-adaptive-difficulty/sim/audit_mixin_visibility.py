#!/usr/bin/env python3
"""Fail when a @Mixin class has a non-private method Mixin would try to merge.

Handler methods (@Inject, @Overwrite, @Redirect, @ModifyArg, and the other
injector annotations) may use the visibility Mixin requires. Every other
method in the mixin class must be private. A package-private static helper
is copied onto the target and fails mixin application.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src" / "main" / "java"

# Annotations that mark a method as a mixin handler or a target member mirror.
HANDLER_ANNOTATIONS = frozenset({
    "Inject",
    "Overwrite",
    "Redirect",
    "ModifyArg",
    "ModifyArgs",
    "ModifyVariable",
    "ModifyConstant",
    "ModifyReceiver",
    "WrapOperation",
    "WrapWithCondition",
    "Accessor",
    "Invoker",
    "Shadow",
    "Unique",
    "SoftOverride",
})

STATEMENT_WORDS = frozenset({
    "if", "for", "while", "switch", "catch", "return", "new", "else", "do",
    "try", "throw", "this", "super", "synchronized",
})

METHOD_RE = re.compile(
    r"(?P<annos>(?:@[\w.]+(?:\s*\((?:[^()]|\([^()]*\))*\))?\s*)*)"
    r"(?P<mods>(?:(?:public|protected|private|static|final|abstract|native|synchronized|strictfp)\s+)*)"
    r"(?P<ret>(?:(?!(?:public|protected|private|static|final|abstract)\b)[\w.$@<>\[\],?])+)\s+"
    r"(?P<name>[A-Za-z_$][\w$]*)\s*"
    r"\((?P<params>[^)]*)\)\s*"
    r"(?:throws\s+[\w.,\s]+)?\s*"
    r"(?P<end>[{;])"
)


def strip_comments_and_strings(text: str) -> str:
    out: list[str] = []
    i = 0
    n = len(text)
    while i < n:
        if text.startswith("/*", i):
            end = text.find("*/", i + 2)
            if end < 0:
                out.append("".join("\n" if c == "\n" else " " for c in text[i:]))
                break
            chunk = text[i:end + 2]
            out.append("".join("\n" if c == "\n" else " " for c in chunk))
            i = end + 2
            continue
        if text.startswith("//", i):
            end = text.find("\n", i)
            if end < 0:
                break
            out.append(" " * (end - i))
            i = end
            continue
        if text[i] in "\"'":
            quote = text[i]
            out.append(" ")
            i += 1
            while i < n:
                if text[i] == "\n":
                    out.append("\n")
                else:
                    out.append(" ")
                if text[i] == "\\":
                    i += 1
                    if i < n:
                        out.append("\n" if text[i] == "\n" else " ")
                        i += 1
                    continue
                if text[i] == quote:
                    i += 1
                    break
                i += 1
            continue
        out.append(text[i])
        i += 1
    return "".join(out)


def brace_depth_at(text: str, index: int) -> int:
    return text[:index].count("{") - text[:index].count("}")


def annotation_names(annos: str) -> set[str]:
    return {match.group(1).split(".")[-1] for match in re.finditer(r"@([\w.]+)", annos)}


def mixin_class_open(text: str) -> int | None:
    mixin = re.search(r"@Mixin\b", text)
    if mixin is None:
        return None
    class_match = re.search(r"\b(?:class|interface|enum)\b[^{]*\{", text[mixin.start():])
    if class_match is None:
        return None
    return mixin.start() + class_match.end() - 1


def violations_in(path: Path) -> list[str]:
    raw = path.read_text(encoding="utf-8")
    if "@Mixin" not in raw:
        return []
    text = strip_comments_and_strings(raw)
    opened = mixin_class_open(text)
    if opened is None:
        return [f"{path}: @Mixin class body not found"]
    found: list[str] = []
    for match in METHOD_RE.finditer(text, opened + 1):
        if brace_depth_at(text, match.start()) != 1:
            continue
        name = match.group("name")
        ret = match.group("ret").strip().split()
        if not ret or ret[-1] in STATEMENT_WORDS or name in STATEMENT_WORDS:
            continue
        mods = match.group("mods").split()
        if "private" in mods:
            continue
        if annotation_names(match.group("annos")) & HANDLER_ANNOTATIONS:
            continue
        line = text[:match.start("name")].count("\n") + 1
        visibility = "package-private"
        if "public" in mods:
            visibility = "public"
        elif "protected" in mods:
            visibility = "protected"
        found.append(f"{path}:{line} {visibility} {name}()")
    return found


def main() -> int:
    # Optional source root so other mods (the melee fix) can run the same check.
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else SRC
    files = sorted(src.rglob("*.java"))
    problems: list[str] = []
    mixin_files = 0
    for path in files:
        raw = path.read_text(encoding="utf-8")
        if "@Mixin" not in raw:
            continue
        mixin_files += 1
        problems.extend(violations_in(path))
    if mixin_files == 0:
        print("FAIL: no @Mixin sources found", file=sys.stderr)
        return 1
    if problems:
        print("FAIL: mixin methods Mixin would merge into the target:", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        return 1
    print(f"PASS — mixin helpers are private ({mixin_files} @Mixin classes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
