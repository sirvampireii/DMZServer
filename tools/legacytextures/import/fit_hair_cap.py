#!/usr/bin/env python3
"""Fit the HD hair cap to DragonMineZ's own hair_base shape (Legacy Textures 0.2.1).

usage: fit_hair_cap.py <dragonminez jar> [--check]

The ZoneMC HD hair_base.png (1024x1024) hangs lower over the forehead than DMZ's 64x64 one (a solid
fringe at row 9, where DMZ leaves the face open) and cuts holes at the ears where DMZ has hair, so in
HD the flat cap shows as a second "2D hair" under the 3D strands. This keeps the HD pixels only inside
DMZ's opaque 64x64 cells (16x16 HD pixels each) and fills the cells DMZ covers but the HD file left
empty with the cap's base white (DMZ tints it). Result: exactly DMZ's 248-cell footprint, rows 16-18
clear. Run it again after import_assets.py re-imports the HD set. --check only reports.
"""
from __future__ import annotations

import io
import sys
import zipfile
from pathlib import Path

from PIL import Image

HD = (Path(__file__).resolve().parent.parent / "src/main/resources/assets/legacytextures/textures/hd/dragonminez"
      "/entity/races/hair_base.png")
VANILLA = "assets/dragonminez/textures/entity/races/hair_base.png"
WHITE = (255, 255, 255, 255)


def footprint(img: Image.Image, cell: int) -> set[tuple[int, int]]:
    px = img.load()
    return {(cx, cy) for cy in range(img.size[1] // cell) for cx in range(img.size[0] // cell)
            if any(px[cx * cell + i, cy * cell + j][3] > 0 for j in range(cell) for i in range(cell))}


def main() -> int:
    vanilla = Image.open(io.BytesIO(zipfile.ZipFile(sys.argv[1]).read(VANILLA))).convert("RGBA")
    hd = Image.open(HD).convert("RGBA")
    cell = hd.size[0] // vanilla.size[0]
    want = footprint(vanilla, 1)
    have = footprint(hd, cell)
    print(f"DMZ cells {len(want)}, HD cells {len(have)}, extra {len(have - want)}, missing {len(want - have)}")
    if "--check" in sys.argv:
        return 0 if have == want else 1
    px = hd.load()
    for cx, cy in have - want:
        for j in range(cell):
            for i in range(cell):
                px[cx * cell + i, cy * cell + j] = (0, 0, 0, 0)
    for cx, cy in want - have:
        for j in range(cell):
            for i in range(cell):
                px[cx * cell + i, cy * cell + j] = WHITE
    hd.save(HD, optimize=True)
    print("fitted", HD)
    return 0


if __name__ == "__main__":
    sys.exit(main())
