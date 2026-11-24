"""Menu mockups from a mock rig run (test only).

A mock run (SRVJVM="-Duiprobe.mock=true" bash run.sh mock ...) shoots the proposed menus, built with LM's real spec
API and drawn by the real LM client, at GUI scale 2, 3 and 4. This script names the shots and, on the dojo upgrade
pages, puts the floor-plan picture (a BlockRender top view from DojoKit) into the room the page leaves for it on the
right of its text box: that picture element is Phase 2 work, everything else on the page is the real client.

    python -I mock_compose.py <run dir (RIG_OUT/mock)> <plan picture dir> <out dir>

Writes menu_<page>_gs<N>.png per page and scale, and menu_<page>.png (GUI 3) for the README.
"""
import os
import re
import sys

from PIL import Image, ImageDraw

PAGES = {
    "mock_main_menu": "main_menu",
    "mock_spar_board": "spar_board",
    "mock_my_dojo": "my_dojo",
    "mock_dojo_upgrades": "upgrades",
    "mock_dojo_up_ring": "upgrade_ring",
}
PLANS = {"mock_dojo_upgrades": "menu_plan_plain_ki_t2.png", "mock_dojo_up_ring": "menu_plan_ki_t2.png"}
NUM = r"(-?[\d.]+)"


def blocks(text):
    """SCREEN blocks of uiprobe-client.txt: (index, page, cfg, gui scale, PANEL, accent, buttons)."""
    out = []
    cur = None
    for line in text.splitlines():
        m = re.match(r"SCREEN (\d+) \S+ (\S+) cfg=(\S+) .* scale=" + NUM, line)
        if m:
            cur = {"i": int(m.group(1)), "page": m.group(2), "cfg": m.group(3), "g": float(m.group(4)),
                   "panel": None, "accent": (43, 181, 176), "buttons": []}
            out.append(cur)
            continue
        if cur is None:
            continue
        m = re.match(r"\s+N PANEL " + " ".join([NUM] * 4) + r" scale=" + NUM, line)
        if m:
            cur["panel"] = [float(v) for v in m.groups()]
            continue
        m = re.match(r"\s+N SKIN \S+ accent=([0-9A-F]{6})", line)
        if m:
            h = m.group(1)
            cur["accent"] = (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))
            continue
        m = re.match(r"\s+N B " + " ".join([NUM] * 4) + r" \S+ (\S+)", line)
        if m:
            cur["buttons"].append([float(v) for v in m.groups()[:4]] + [m.group(5)])
    return out


def trim_sky(plan):
    """BlockRender draws sky round the building: keep the building only."""
    sky = plan.getpixel((0, plan.height - 1))
    mask = Image.new("L", plan.size, 0)
    px, mp = plan.load(), mask.load()
    for y in range(plan.height):
        for x in range(plan.width):
            if sum(abs(a - c) for a, c in zip(px[x, y], sky)) > 30:
                mp[x, y] = 255
    return plan.crop(mask.getbbox() or (0, 0, plan.width, plan.height))


def put_plan(img, b, plan):
    """The plan in the room right of the text: under the title band, above the first button row."""
    x, y, w, h, s = b["panel"]
    g = b["g"]
    tabs = [bt for bt in b["buttons"] if ":tab:" in bt[4]]
    rows = [bt for bt in b["buttons"] if ":tab:" not in bt[4]]
    top = (max(bt[1] + bt[3] for bt in tabs) if tabs else y + 30 * s) + 12 * s
    bottom = min(bt[1] for bt in rows) - 12 * s
    right = x + w - 16 * s
    ph = bottom - top
    pw = ph * plan.width / plan.height
    if pw > w * 0.44:
        pw = w * 0.44
        ph = pw * plan.height / plan.width
    left = right - pw
    top = top + (bottom - top - ph) / 2
    box = [round(left * g), round(top * g), round((left + pw) * g), round((top + ph) * g)]
    img.paste(plan.resize((box[2] - box[0], box[3] - box[1]), Image.LANCZOS), (box[0], box[1]))
    d = ImageDraw.Draw(img)
    pad = max(2, round(2 * g))
    d.rounded_rectangle([box[0] - pad, box[1] - pad, box[2] + pad - 1, box[3] + pad - 1], radius=3 * pad,
                        outline=b["accent"], width=max(1, round(g)))


def main(run, plans, out):
    os.makedirs(out, exist_ok=True)
    text = open(os.path.join(run, "uiprobe-client.txt"), encoding="utf-8", errors="replace").read()
    made = []
    for b in blocks(text):
        name = PAGES.get(b["page"])
        if name is None or b["panel"] is None or b["cfg"].endswith("-end"):
            continue
        shot = os.path.join(run, "ui-%03d-%s.png" % (b["i"], b["cfg"]))
        if not os.path.exists(shot):
            continue
        img = Image.open(shot).convert("RGB")
        if b["page"] in PLANS:
            put_plan(img, b, trim_sky(Image.open(os.path.join(plans, PLANS[b["page"]])).convert("RGB")))
        gs = b["cfg"].split("-gs")[-1]
        img.save(os.path.join(out, "menu_%s_gs%s.png" % (name, gs)))
        if gs == "3":
            img.save(os.path.join(out, "menu_%s.png" % name))
        made.append("menu_%s_gs%s.png" % (name, gs))
    print("\n".join(made) or "no mock pages found")


if __name__ == "__main__":
    main(*sys.argv[1:4])
