#!/usr/bin/env python3
"""Check every registered mixin against the bytecode it will be applied to.

javac never looks at mixin target strings, so a DragonMineZ update can delete or
reshape a target and the jar still builds. This audit reads each mixin source
named in the given mixin configs and, with javap on the compile classpath, fails
when:

  * the target class or method is missing;
  * a name-only ``method = "x"`` matches more than one overload (Mixin injects
    into every one of them, so the hook runs twice when one delegates);
  * an @Inject handler uses CallbackInfo for a non-void target, or
    CallbackInfoReturnable for a void one;
  * the handler's leading parameters do not match the target's parameters;
  * an @At(INVOKE / FIELD) target, or the requested ordinal, is not in the body;
  * an @Shadow field or method is missing.

usage: audit_mixin_targets.py --src <java root> --cp <a.jar:b.jar...> <mixins.json>...

A mixin listed in a config whose "required" is false is still checked; pass
--optional-missing-ok to let its target class be absent (an optional mod).
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

INJECTORS = ("Inject", "Redirect", "ModifyConstant", "ModifyArg", "ModifyArgs",
             "ModifyVariable", "WrapOperation", "WrapWithCondition", "ModifyExpressionValue",
             "ModifyReturnValue")
PRIMS = {"V": "void", "Z": "boolean", "B": "byte", "C": "char", "S": "short", "I": "int",
         "J": "long", "F": "float", "D": "double"}


def split_desc(desc: str) -> tuple[list[str], str]:
    """'(Lx/Y;I)V' -> (['x/Y', 'int'], 'void')."""
    def one(s: str, i: int) -> tuple[str, int]:
        dims = 0
        while s[i] == "[":
            dims += 1
            i += 1
        if s[i] == "L":
            j = s.index(";", i)
            t = s[i + 1:j]
            i = j + 1
        else:
            t = PRIMS[s[i]]
            i += 1
        return t + "[]" * dims, i

    assert desc[0] == "("
    args, i = [], 1
    while desc[i] != ")":
        t, i = one(desc, i)
        args.append(t)
    ret, _ = one(desc, i + 1)
    return args, ret


def simple(t: str) -> str:
    """Last segment of a type, inner classes with dots: 'a/b/C$D' -> 'D'."""
    t = re.sub(r"<.*>", "", t).strip()
    t = t.replace("...", "[]")
    return re.split(r"[./$]", t)[-1]


class ClassInfo:
    def __init__(self, name: str, text: str):
        self.name = name
        self.methods: dict[str, list[dict]] = {}
        self.fields: set[str] = set()
        cur = None
        for line in text.splitlines():
            m = re.match(r"^  (?:[\w$<>\[\], .?]+ )?([\w$.<>]+)\((.*)\)(?: throws [\w$., ]+)?;$", line)
            if m and not line.startswith("    "):
                name_ = "<init>" if m.group(1) == self.name else m.group(1)
                cur = {"name": name_, "desc": None, "code": [], "static": " static " in " " + line.split("(")[0] + " "}
                self.methods.setdefault(name_, []).append(cur)
                continue
            if line.strip() == "static {};":
                cur = {"name": "<clinit>", "desc": None, "code": [], "static": True}
                self.methods.setdefault("<clinit>", []).append(cur)
                continue
            fm = re.match(r"^  (?:[\w$<>\[\], .?]+ )([\w$]+);$", line)
            if fm and not line.startswith("    "):
                self.fields.add(fm.group(1))
                cur = None
                continue
            dm = re.match(r"^    descriptor: (.*)$", line)
            if dm and cur is not None and cur["desc"] is None:
                cur["desc"] = dm.group(1).strip()
                continue
            if cur is not None and re.match(r"^\s+\d+: ", line):
                cur["code"].append(line)

    def find(self, name: str, desc: str | None) -> list[dict]:
        found = [m for m in self.methods.get(name, []) if m["desc"]]
        if desc:
            found = [m for m in found if m["desc"] == desc]
        return found


class Javap:
    def __init__(self, cp: str):
        self.cp = cp
        self.cache: dict[str, ClassInfo | None] = {}

    def get(self, cls: str) -> ClassInfo | None:
        cls = cls.replace("/", ".")
        if cls not in self.cache:
            r = subprocess.run(["javap", "-p", "-s", "-c", "-cp", self.cp, cls],
                               capture_output=True, text=True, encoding="utf-8", errors="replace")
            self.cache[cls] = ClassInfo(cls, r.stdout) if r.returncode == 0 and r.stdout.strip() else None
        return self.cache[cls]


def balanced(text: str, start: int) -> int:
    """Index just past the ')' matching the '(' at text[start]."""
    depth, i, q = 0, start, None
    while i < len(text):
        c = text[i]
        if q:
            if c == "\\":
                i += 1
            elif c == q:
                q = None
        elif c in "\"'":
            q = c
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    raise ValueError("unbalanced")


def resolve_class(src: str, token: str) -> str:
    token = token.strip()
    if token.endswith(".class"):
        token = token[:-6]
    head = token.split(".")[0]
    m = re.search(r"^import\s+([\w.]+\." + re.escape(head) + r");", src, re.M)
    if m:
        return m.group(1) + token[len(head):].replace(".", "$")
    if "." in token and token[0].islower():
        return token
    pkg = re.search(r"^package\s+([\w.]+);", src, re.M)
    return (pkg.group(1) + "." if pkg else "") + token


STRING = r"\"(?:[^\"\\]|\\.)*\""


def strings_of(ann: str, key: str, consts: dict[str, str] | None = None) -> list[str]:
    """Values of key = "x", {"x", "y"}, CONSTANT, or {CONSTANT, "y"} in an annotation."""
    m = re.search(r"\b" + key + r"\s*=\s*(\{[^}]*\}|" + STRING + r"|[A-Z_][A-Z0-9_]*)", ann)
    if not m:
        return []
    out = []
    for lit, ident in re.findall(r"(" + STRING + r")|\b([A-Z_][A-Z0-9_]*)\b", m.group(1)):
        if ident:
            if not consts or ident not in consts:
                raise ValueError(f"cannot resolve constant {ident} in {key} =")
            out.append(consts[ident])
        else:
            out.append(lit[1:-1])
    return out


def string_constants(code: str) -> dict[str, str]:
    """static final String NAME = "a" + "b"; built from literal pieces only."""
    consts = {}
    for name, expr in re.findall(
            r"static\s+final\s+String\s+([A-Z_][A-Z0-9_]*)\s*=\s*((?:" + STRING + r"\s*\+?\s*)+);", code):
        consts[name] = "".join(piece[1:-1] for piece in re.findall(STRING, expr))
    return consts


def handler_params(after: str) -> tuple[str, list[str]] | None:
    m = re.search(r"\b[\w$]+\s*\(", after)
    if not m:
        return None
    end = balanced(after, m.end() - 1)
    body = after[m.end():end - 1]
    params, depth, cur = [], 0, ""
    for c in body:
        if c == "<":
            depth += 1
        elif c == ">":
            depth -= 1
        if c == "," and depth == 0:
            params.append(cur)
            cur = ""
        else:
            cur += c
    if cur.strip():
        params.append(cur)
    types = []
    for p in params:
        coerce = "@Coerce" in p
        p = re.sub(r"@\w+(\([^)]*\))?", "", p).replace("final ", "").strip()
        # @Coerce lets a handler take a supertype (an inaccessible private class as Object).
        types.append("*" if coerce else (p.rsplit(None, 1)[0] if " " in p else p))
    return after[:m.end() - 1], types


def check_mixin(path: Path, jp: Javap, optional: bool, errors: list[str], notes: list[str]) -> None:
    src = path.read_text(encoding="utf-8")
    code = re.sub(r"//[^\n]*", "", re.sub(r"/\*.*?\*/", "", src, flags=re.S))
    mm = re.search(r"@Mixin\s*\(", code)
    if not mm:
        errors.append(f"{path.name}: no @Mixin")
        return
    ann = code[mm.start():balanced(code, mm.end() - 1)]
    targets = strings_of(ann, "targets")
    for tok in re.findall(r"([\w.]+)\.class", ann):
        targets.append(resolve_class(src, tok))
    infos = []
    for t in targets:
        ci = jp.get(t)
        if ci is None:
            (notes if optional else errors).append(f"{path.name}: target class {t} not on the classpath")
        else:
            infos.append(ci)
    if not infos:
        return
    tag = path.stem
    consts = string_constants(code)

    for am in re.finditer(r"@(" + "|".join(INJECTORS) + r"|Shadow|Overwrite)\b\s*(\()?", code):
        kind = am.group(1)
        ann_end = balanced(code, am.end() - 1) if am.group(2) else am.end()
        ann_text = code[am.start():ann_end]
        rest = code[ann_end:]
        if kind == "Shadow":
            decl = re.match(r"\s*(?:@\w+(?:\([^)]*\))?\s*)*([^;{=]*)", rest).group(1).strip()
            dm = re.search(r"([\w$]+)\s*\(", decl)
            if dm:
                name = dm.group(1)
                ok = any(ci.methods.get(name) for ci in infos)
            else:
                name = decl.split()[-1] if decl.split() else "?"
                ok = any(name in ci.fields for ci in infos)
            if not ok:
                errors.append(f"{tag}: @Shadow {name} is not in {infos[0].name}")
            continue
        hp = handler_params(rest)
        try:
            methods = strings_of(ann_text, "method", consts)
        except ValueError as e:
            errors.append(f"{tag}: {e}")
            continue
        if kind != "Overwrite" and not methods:
            errors.append(f"{tag}: @{kind} without a method = target this audit can read")
            continue
        if kind == "Overwrite":
            if hp:
                name = re.search(r"([\w$]+)\s*$", hp[0]).group(1)
                if not any(ci.methods.get(name) for ci in infos):
                    errors.append(f"{tag}: @Overwrite {name} is not in {infos[0].name}")
            continue
        handler_name = re.search(r"([\w$]+)\s*$", hp[0]).group(1) if hp else "?"
        where = f"{tag}.{handler_name}"
        at_targets = re.findall(r"@At\s*\(([^@]*?)\)(?=\s*[,)}\]])", ann_text, re.S)
        for spec in methods:
            mname, mdesc = (spec.split("(", 1)[0], "(" + spec.split("(", 1)[1]) if "(" in spec else (spec, None)
            if mname.startswith("L") and ";" in mname:
                errors.append(f"{where}: owner-qualified method selector {spec} not supported by this audit")
                continue
            found = []
            for ci in infos:
                found += ci.find(mname, mdesc)
            if not found:
                errors.append(f"{where}: method {spec} not found in {infos[0].name}")
                continue
            if len(found) > 1:
                errors.append(f"{where}: method \"{spec}\" matches {len(found)} overloads "
                              f"({', '.join(f['desc'] for f in found)}); pin the descriptor")
            for f in found:
                args, ret = split_desc(f["desc"])
                # Mixin requires a static handler for a static target, and an instance one otherwise.
                handler_static = " static " in " " + hp[0].split("\n")[-1] + " " if hp else None
                if hp and kind in INJECTORS and handler_static != f["static"]:
                    errors.append(f"{where}: handler static={handler_static} but target "
                                  f"{mname}{f['desc']} static={f['static']}")
                if kind == "Inject" and hp:
                    ptypes = hp[1]
                    ci_idx = next((i for i, p in enumerate(ptypes) if simple(p) in ("CallbackInfo", "CallbackInfoReturnable")), None)
                    if ci_idx is None:
                        errors.append(f"{where}: @Inject handler has no CallbackInfo parameter")
                    else:
                        cbt = simple(ptypes[ci_idx])
                        if ret == "void" and cbt != "CallbackInfo":
                            errors.append(f"{where}: target {mname}{f['desc']} returns void; handler uses {cbt}")
                        if ret != "void" and cbt != "CallbackInfoReturnable":
                            errors.append(f"{where}: target {mname}{f['desc']} returns {ret}; handler must use CallbackInfoReturnable")
                        lead = ptypes[:ci_idx]
                        if lead and (len(lead) != len(args) or any(
                                simple(p) not in ("*", simple(a)) for p, a in zip(lead, args))):
                            errors.append(f"{where}: handler params ({', '.join(simple(p) for p in lead)}) "
                                          f"!= target params ({', '.join(simple(a) for a in args)}) of {mname}{f['desc']}")
                for at in at_targets:
                    vm = re.search(r"value\s*=\s*\"(\w+)\"", at) or re.match(r"\s*\"(\w+)\"", at)
                    value = vm.group(1) if vm else ""
                    tm = re.search(r"target\s*=\s*\"([^\"]+)\"", at)
                    if value not in ("INVOKE", "FIELD", "INVOKE_ASSIGN", "NEW") or not tm:
                        continue
                    tgt = tm.group(1)
                    om = re.search(r"ordinal\s*=\s*(\d+)", at)
                    ordinal = int(om.group(1)) if om else None
                    im = None
                    if value == "NEW":
                        needle = "class " + tgt.strip("L;").replace(".", "/")
                    elif value == "FIELD":
                        fm = re.match(r"L([^;]+);([\w$]+):(.+)", tgt)
                        needle = f"Field {fm.group(1)}.{fm.group(2)}:{fm.group(3)}" if fm else tgt
                    else:
                        im = re.match(r"L([^;]+);([\w$<>]+)(\(.*)", tgt)
                        if not im:
                            im = re.match(r"([\w/$]+)\.([\w$<>]+):?(\(.*)", tgt)
                        needle = f"{im.group(1)}.{im.group(2)}:{im.group(3)}" if im else tgt
                        if im and im.group(2) == "<init>":
                            needle = f"{im.group(1)}.\"<init>\":{im.group(3)}"
                    hits = [c for c in f["code"] if needle in c]
                    if not hits:
                        errors.append(f"{where}: @At({value}) {tgt} not in {mname}{f['desc']}")
                    elif ordinal is not None and ordinal >= len(hits):
                        errors.append(f"{where}: @At({value}) {tgt} ordinal {ordinal} but only {len(hits)} in {mname}")
                    elif kind == "Redirect" and value == "INVOKE" and hp and im:
                        # Handler takes the receiver (unless invokestatic), then the call's arguments.
                        call_args, _ = split_desc(im.group(3))
                        want = ([] if "invokestatic" in hits[0] else [im.group(1)]) + call_args
                        got = [simple(p) for p in hp[1]][:len(want)]
                        if got != [simple(w) for w in want]:
                            errors.append(f"{where}: @Redirect handler params ({', '.join(got)}) != "
                                          f"({', '.join(simple(w) for w in want)}) for {tgt}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--cp", required=True)
    ap.add_argument("--optional-missing-ok", action="store_true")
    ap.add_argument("--extra-mixins", default="",
                    help="comma list of mixin classes kept in source but not registered; checked too")
    ap.add_argument("configs", nargs="+")
    a = ap.parse_args()
    jp = Javap(a.cp)
    errors: list[str] = []
    notes: list[str] = []
    checked = 0
    for cfg_path in a.configs:
        cfg = json.loads(Path(cfg_path).read_text(encoding="utf-8"))
        pkg = cfg["package"]
        optional = (not cfg.get("required", True)) and a.optional_missing_ok
        names = list(cfg.get("mixins", [])) + list(cfg.get("server", [])) + list(cfg.get("client", []))
        if cfg_path == a.configs[0]:
            names += [n.strip() for n in a.extra_mixins.split(",") if n.strip()]
        for n in names:
            p = Path(a.src) / (pkg.replace(".", "/") + "/" + n.replace(".", "/") + ".java")
            if not p.is_file():
                errors.append(f"{Path(cfg_path).name}: {n} has no source at {p}")
                continue
            check_mixin(p, jp, optional, errors, notes)
            checked += 1
    for n in notes:
        print("note: " + n)
    if errors:
        print(f"ERROR: mixin target audit failed ({len(errors)}):", file=sys.stderr)
        for e in errors:
            print("  " + e, file=sys.stderr)
        return 1
    print(f"Mixin target audit OK: {checked} mixins match the compile classpath")
    return 0


if __name__ == "__main__":
    sys.exit(main())
