"""Menu mockups from a mock rig run (test only).

A mock run (SRVJVM="-Duiprobe.mock=true" bash run.sh mock ...) shoots the proposed menus, built with LM's real spec API
and drawn by the real LM client, at GUI scale 2, 3 and 4. This script names the shots and puts DojoKit's pictures
(BlockRender, sky left out) where a page leaves room for them. Those pictures are the one new menu element (Phase 2
work); everything else on the page is the real client.

Two kinds of room, both measured on the screenshot, so a page can change without touching this file:
  right   beside short text lines: from under the title band (or tabs) to above the first button row
  band    a run of blank lines in the text box (a page sends empty lines where the pictures go): the pictures sit
          side by side in it, each scaled to fit

    python -I mock_compose.py <run dir (RIG_OUT/mock)> <picture dir (DojoKit's build/dojo/pics)> <out dir>

Writes menu_<name>_gs<N>.png per page and GUI scale, and menu_<name>.png (GUI 3).
"""
import os
import re
import sys

from PIL import Image

# page id -> (file name, room, pictures)
PAGES = {
    "mock_main_menu": ("main_menu", None, []),
    "mock_spar_board": ("spar_board", None, []),
    "mock_my_dojo": ("my_dojo", "right", ["menu_plan_start.png"]),
    "mock_dojo_upgrades": ("upgrades", "right", ["menu_plan_start.png"]),
    "mock_dojo_up_ring2": ("upgrade_ring2", "band",
                           ["upgrade_ring2_now.png", "upgrade_ring2_after.png", "upgrade_ring2_map.png"]),
    "mock_dojo_up_training1": ("upgrade_training1", "band",
                               ["upgrade_training1_now.png", "upgrade_training1_after.png", "upgrade_training1_map.png"]),
}
NUM = r"(-?[\d.]+)"


def blocks(text):
    """SCREEN blocks of uiprobe-client.txt: index, page, cfg, GUI scale, PANEL and buttons (GUI pixels)."""
    out, cur = [], None
    for line in text.splitlines():
        m = re.match(r"SCREEN (\d+) \S+ (\S+) cfg=(\S+) .* scale=" + NUM, line)
        if m:
            cur = {"i": int(m.group(1)), "page": m.group(2), "cfg": m.group(3), "g": float(m.group(4)),
                   "panel": None, "buttons": []}
            out.append(cur)
            continue
        if cur is None:
            continue
        m = re.match(r"\s+N PANEL " + " ".join([NUM] * 4) + r" scale=" + NUM, line)
        if m:
            cur["panel"] = [float(v) for v in m.groups()]
            continue
        m = re.match(r"\s+N B " + " ".join([NUM] * 4) + r" \S+ (\S+)", line)
        if m:
            cur["buttons"].append([float(v) for v in m.groups()[:4]] + [m.group(5)])
    return out


def text_area(b):
    """GUI-pixel box between the title band (or the tabs) and the first button row, inside the panel's edges."""
    x, y, w, h, s = b["panel"]
    tabs = [bt for bt in b["buttons"] if ":tab:" in bt[4]]
    rows = [bt for bt in b["buttons"] if ":tab:" not in bt[4]]
    top = (max(bt[1] + bt[3] for bt in tabs) if tabs else y + 30 * s) + 6 * s
    bottom = min(bt[1] for bt in rows) - 6 * s
    return x + 12 * s, top, x + w - 12 * s, bottom, s


def blank_band(img, b):
    """The tallest run of screen rows in the text box with no text on them (light pixels), in screen pixels."""
    x0, top, x1, bottom, s = text_area(b)
    g = b["g"]
    X0, X1, Y0, Y1 = int(x0 * g), int(x1 * g), int(top * g), int(bottom * g)
    px = img.load()
    best, run_start = (0, 0), None
    for yy in range(Y0, Y1 + 1):
        lit = 0
        if yy < Y1:
            for xx in range(X0, X1, 2):
                r, gg, bb = px[xx, yy][:3]
                if r + gg + bb > 420:
                    lit += 1
                    if lit > 1:
                        break
        if yy < Y1 and lit <= 1:
            run_start = yy if run_start is None else run_start
        else:
            if run_start is not None and yy - run_start > best[1] - best[0]:
                best = (run_start, yy)
            run_start = None
    pad = int(3 * s * g)
    return X0, best[0] + pad, X1, best[1] - pad


def paste_fit(img, pic, box):
    """Paste {pic} into the screen-pixel box, as large as fits, centred; transparency kept."""
    bx0, by0, bx1, by1 = box
    pic = pic.crop(pic.getbbox() or (0, 0, pic.width, pic.height))     # the render's empty margins off
    k = min((bx1 - bx0) / pic.width, (by1 - by0) / pic.height)
    w, h = max(1, int(pic.width * k)), max(1, int(pic.height * k))
    p = pic.resize((w, h), Image.LANCZOS)
    img.paste(p, (bx0 + (bx1 - bx0 - w) // 2, by0 + (by1 - by0 - h) // 2), p)


def place(img, b, room, pics):
    g = b["g"]
    if room == "right":
        x0, top, x1, bottom, s = text_area(b)
        w = (x1 - x0) * 0.42
        paste_fit(img, pics[0], (int((x1 - w) * g), int(top * g), int(x1 * g), int(bottom * g)))
    elif room == "band":
        X0, Y0, X1, Y1 = blank_band(img, b)
        gap = int(6 * b["panel"][4] * g)
        cell = (X1 - X0 - gap * (len(pics) - 1)) // len(pics)
        for i, pic in enumerate(pics):
            cx = X0 + i * (cell + gap)
            paste_fit(img, pic, (cx, Y0, cx + cell, Y1))


def main(run, pics_dir, out):
    os.makedirs(out, exist_ok=True)
    text = open(os.path.join(run, "uiprobe-client.txt"), encoding="utf-8", errors="replace").read()
    made = []
    for b in blocks(text):
        page = PAGES.get(b["page"])
        if page is None or b["panel"] is None or b["cfg"].endswith("-end"):
            continue
        shot = os.path.join(run, "ui-%03d-%s.png" % (b["i"], b["cfg"]))
        if not os.path.exists(shot):
            continue
        name, room, files = page
        img = Image.open(shot).convert("RGB")
        if room:
            place(img, b, room, [Image.open(os.path.join(pics_dir, f)).convert("RGBA") for f in files])
        gs = b["cfg"].split("-gs")[-1]
        img.save(os.path.join(out, "menu_%s_gs%s.png" % (name, gs)))
        if gs == "3":
            img.save(os.path.join(out, "menu_%s.png" % name))
        made.append("menu_%s_gs%s.png" % (name, gs))
    print("\n".join(made) or "no mock pages found")


if __name__ == "__main__":
    main(*sys.argv[1:4])
