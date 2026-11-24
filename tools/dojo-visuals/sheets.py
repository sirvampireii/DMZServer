"""Contact sheets of the dojo pictures: one grid per subject (rows = styles, columns = basic / refined / masterwork).

Usage: python sheets.py <dir with the renders>
"""
import os
import sys

from PIL import Image, ImageDraw

import design
import render


def sheet(folder, prefix, cell_w, out_name, title):
    cells = []
    for style in design.STYLES:
        row = []
        for tier in design.TIERS:
            p = os.path.join(folder, f"{prefix}_{style}_t{tier}.png")
            im = Image.open(p).convert("RGB")
            h = int(im.height * cell_w / im.width)
            row.append(im.resize((cell_w, h), Image.LANCZOS))
        cells.append(row)
    cell_h = max(im.height for row in cells for im in row)
    label_w = 150
    head = 70
    W = label_w + 3 * cell_w + 4 * 8
    H = head + 4 * cell_h + 5 * 8
    out = Image.new("RGB", (W, H), (22, 11, 13))
    d = ImageDraw.Draw(out)
    d.text((12, 10), title, fill=(240, 180, 41), font=render.font(24))
    for c, tier in enumerate(design.TIERS):
        d.text((label_w + 8 + c * (cell_w + 8) + 6, 44), design.TIER_NAMES[tier].capitalize(),
               fill=(246, 232, 228), font=render.font(18))
    for r, style in enumerate(design.STYLES):
        y = head + 8 + r * (cell_h + 8)
        d.text((12, y + cell_h // 2 - 10), design.STYLE_NAMES[style], fill=(246, 232, 228), font=render.font(18))
        for c in range(3):
            out.paste(cells[r][c], (label_w + 8 + c * (cell_w + 8), y))
    p = os.path.join(folder, out_name)
    out.save(p)
    return p


if __name__ == "__main__":
    folder = sys.argv[1] if len(sys.argv) > 1 else "out"
    print(sheet(folder, "plan", 420, "sheet_floor_plans.png", "Dojo hall floor plan (roof off) - 4 styles x 3 tiers"))
    print(sheet(folder, "outside", 420, "sheet_outside.png", "Dojo hall from outside - 4 styles x 3 tiers"))
    print(sheet(folder, "arena", 420, "sheet_arena_floor.png", "Arena floor from the balcony - 4 styles x 3 tiers"))
    print(sheet(folder, "training", 420, "sheet_training_area.png", "Training area - 4 styles x 3 tiers"))
