"""LM 4.7.68 dojo pictures: top-down layout renders and eye-level views, from design.py's data.

Usage: python render.py <out dir>
Pure Python + Pillow. The eye-level view is a small voxel ray caster (sky, sun shading, fog), the top-down view
colors each column by its top block with height shading. Colors are average Minecraft block colors.
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

import design

COLORS = {
    "air": None,
    "grass": (95, 159, 53), "grass_block": (95, 159, 53), "dirt": (134, 96, 67), "dirt_path": (148, 121, 65),
    "gravel": (131, 127, 126), "stone_brick_wall": (122, 121, 122), "stone": (125, 125, 125),
    "smooth_stone": (158, 158, 158), "smooth_stone_slab": (158, 158, 158), "calcite": (223, 224, 220),
    "light_blue_concrete": (35, 137, 198), "birch_log": (216, 215, 210), "lantern": (232, 182, 92),
    "light_blue_terracotta": (113, 108, 137), "glass": (200, 228, 236), "white_carpet": (233, 236, 236),
    "white_wool": (233, 236, 236), "light_blue_wool": (58, 175, 217), "azalea_leaves": (90, 117, 44),
    "flowering_azalea_leaves": (112, 121, 64), "smooth_quartz": (235, 229, 222), "light_blue_glazed_terracotta": (94, 164, 208),
    "quartz_bricks": (234, 229, 221), "quartz_pillar": (235, 230, 224), "quartz_slab": (235, 229, 222),
    "soul_lantern": (90, 195, 205), "light_blue_stained_glass": (150, 200, 235), "light_blue_carpet": (58, 175, 217),
    "white_concrete": (207, 213, 214), "cyan_terracotta": (86, 91, 91), "diamond_block": (98, 237, 228),
    "cyan_concrete": (21, 119, 136), "quartz_block": (235, 229, 222), "sea_lantern": (172, 199, 190),
    "prismarine_bricks": (99, 171, 158), "cyan_carpet": (21, 137, 145), "beacon": (118, 221, 215),
    "spruce_planks": (114, 84, 48), "hay_block": (166, 136, 38), "stripped_spruce_log": (115, 89, 52),
    "birch_planks": (192, 175, 121), "spruce_log": (58, 37, 16), "spruce_slab": (114, 84, 48), "red_wool": (160, 39, 34),
    "yellow_carpet": (248, 197, 39), "target": (226, 170, 157), "oak_leaves": (59, 110, 30), "stripped_birch_log": (196, 176, 118),
    "red_concrete": (142, 32, 32), "dark_oak_log": (60, 46, 26), "dark_oak_slab": (66, 43, 20), "red_terracotta": (143, 61, 46),
    "white_stained_glass": (230, 230, 230), "red_carpet": (160, 39, 34), "stone_bricks": (122, 121, 122),
    "dark_oak_planks": (66, 43, 20), "stripped_bamboo_block": (196, 176, 82), "crimson_planks": (101, 48, 70),
    "stripped_dark_oak_log": (96, 76, 49), "redstone_lamp": (198, 128, 70), "gold_block": (246, 208, 61),
    "crimson_stem": (92, 25, 29), "cherry_leaves": (229, 172, 194), "polished_andesite": (132, 134, 133),
    "cobblestone": (127, 127, 127), "andesite": (136, 136, 136), "stone_slab": (158, 158, 158), "torch": (255, 216, 92),
    "terracotta": (152, 94, 67), "iron_bars": (110, 112, 112), "gray_carpet": (62, 68, 71), "anvil": (68, 68, 68),
    "moss_block": (89, 109, 45), "orange_terracotta": (161, 83, 37), "deepslate_bricks": (70, 70, 71),
    "polished_basalt": (99, 98, 100), "deepslate_brick_slab": (70, 70, 71), "orange_carpet": (240, 118, 19),
    "blast_furnace": (80, 80, 81), "polished_deepslate": (72, 72, 73), "magma_block": (142, 63, 31),
    "orange_concrete": (224, 97, 0), "deepslate_tiles": (54, 54, 55), "deepslate_tile_slab": (54, 54, 55),
    "copper_block": (192, 107, 79), "shroomlight": (240, 146, 70), "black_carpet": (20, 21, 25), "lodestone": (147, 149, 152),
    "bamboo_planks": (193, 173, 80), "bamboo_block": (127, 144, 58), "green_wool": (84, 109, 27),
    "bamboo_mosaic": (190, 170, 78), "green_carpet": (84, 109, 27), "bamboo_slab": (193, 173, 80),
    "mangrove_planks": (117, 54, 48), "mangrove_slab": (117, 54, 48), "lime_terracotta": (103, 117, 52),
    "green_stained_glass": (102, 127, 51), "lime_carpet": (112, 185, 25), "azalea": (101, 124, 47), "mud_bricks": (137, 103, 79),
    "emerald_block": (42, 203, 87), "cherry_planks": (226, 178, 172), "stripped_cherry_log": (215, 145, 148),
    "cherry_slab": (226, 178, 172), "pink_terracotta": (161, 78, 78), "pearlescent_froglight": (245, 240, 239),
    "pink_stained_glass": (242, 127, 165), "gray_stained_glass": (76, 76, 76), "pink_carpet": (237, 141, 172), "flowering_azalea": (112, 121, 64),
    "poppy": (220, 40, 40), "dandelion": (245, 220, 40), "blue_orchid": (40, 160, 220), "allium": (180, 100, 220),
    "oxeye_daisy": (240, 240, 230), "cherry_log": (54, 33, 44),
}
# Blocks drawn as thin (carpet, slab cap) or small (flowers, lanterns, torches) - still solid in the views.
EMISSIVE = {"lantern", "soul_lantern", "sea_lantern", "torch", "beacon", "redstone_lamp", "shroomlight",
            "pearlescent_froglight", "magma_block"}
SEE_THROUGH = {"glass", "light_blue_stained_glass", "white_stained_glass", "green_stained_glass", "pink_stained_glass",
               "gray_stained_glass",
               "iron_bars"}


def color(block):
    c = COLORS.get(block)
    if c is None:
        return (200, 0, 200)
    return c


def resolved(template, style, tier):
    return {k: design.resolve(s, style, tier) for k, s in template.items()}


def topdown(blocks, scale, bounds=None, title=None, highlight=None, labels=None):
    xs = [k[0] for k in blocks]
    zs = [k[2] for k in blocks]
    x0, x1 = (min(xs), max(xs)) if bounds is None else (bounds[0], bounds[2])
    z0, z1 = (min(zs), max(zs)) if bounds is None else (bounds[1], bounds[3])
    top = {}
    glass = {}
    for (x, y, z), b in blocks.items():
        if b in ("air", None):
            continue
        k = (x, z)
        if b in SEE_THROUGH:
            glass[k] = b
            continue
        if k not in top or y > top[k][0]:
            top[k] = (y, b)
    w = (x1 - x0 + 1) * scale
    h = (z1 - z0 + 1) * scale
    pad = 34 if title else 0
    img = Image.new("RGB", (w, h + pad), (18, 22, 30))
    d = ImageDraw.Draw(img)
    for (x, z), (y, b) in top.items():
        c = color(b)
        shade = 0.78 + 0.05 * min(y, 6)
        # sun from the north-west: a block higher than its south-east neighbor casts a soft edge
        se = top.get((x + 1, z + 1))
        if se is not None and se[0] < y:
            shade *= 1.06
        nw = top.get((x - 1, z - 1))
        if nw is not None and nw[0] > y:
            shade *= 0.82
        c = tuple(max(0, min(255, int(v * shade))) for v in c)
        if (x, z) in glass:
            g = color(glass[(x, z)])
            c = tuple(int(c[q] * 0.72 + g[q] * 0.28) for q in range(3))
        px = (x - x0) * scale
        pz = (z - z0) * scale + pad
        d.rectangle([px, pz, px + scale - 1, pz + scale - 1], fill=c)
    if highlight is not None:
        hx0, hz0, hx1, hz1 = highlight
        d.rectangle([(hx0 - x0) * scale - 2, (hz0 - z0) * scale + pad - 2, (hx1 - x0 + 1) * scale + 1,
                     (hz1 - z0 + 1) * scale + pad + 1], outline=(255, 230, 80), width=3)
    if title:
        d.text((10, 9), title, fill=(240, 180, 41), font=font(18))
    for (lx, lz, text) in (labels or []):
        tx = (lx - x0) * scale
        tz = (lz - z0) * scale + pad
        f = font(13)
        tw = d.textlength(text, font=f)
        d.rectangle([tx - tw / 2 - 4, tz - 9, tx + tw / 2 + 4, tz + 9], fill=(22, 11, 13))
        d.text((tx - tw / 2, tz - 8), text, fill=(246, 232, 228), font=f)
    return img


LAYOUT_LABELS = [(-48, -50, "Arena 1"), (0, -50, "Arena 2"), (48, -50, "Arena 3"), (-48, -90, "Arena 4"),
                 (0, -90, "Arena 5"), (48, -90, "Arena 6"), (0, -70, "Gallery above"), (0, 0, "Garden"),
                 (-48, 40, "Training hall"), (0, 40, "Meditation"), (48, 40, "Trophy hall"),
                 (-30, 78, "Master quarters"), (30, 78, "Bunks"), (0, 96, "Gate")]


_FONTS = {}


def font(size):
    if size not in _FONTS:
        for name in ("segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf"):
            try:
                _FONTS[size] = ImageFont.truetype(name, size)
                break
            except OSError:
                continue
        else:
            _FONTS[size] = ImageFont.load_default()
    return _FONTS[size]


def eye_level(blocks, eye, yaw_deg, pitch_deg, w=480, h=270, fov=70, title=None, sky=((120, 170, 230), (205, 225, 245))):
    """A voxel ray-cast view from {@code eye} (x, y, z) looking along yaw (0 = south, 90 = west) and pitch."""
    solid = {k: v for k, v in blocks.items() if v not in ("air", None)}
    img = Image.new("RGB", (w, h))
    px = img.load()
    yaw = math.radians(yaw_deg)
    pitch = math.radians(pitch_deg)
    fwd = (-math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch))
    right = (math.cos(yaw), 0.0, math.sin(yaw))
    up = (fwd[1] * right[2] - fwd[2] * right[1], fwd[2] * right[0] - fwd[0] * right[2],
          fwd[0] * right[1] - fwd[1] * right[0])
    tanf = math.tan(math.radians(fov) / 2)
    aspect = w / h
    sun = (-0.45, 0.8, -0.35)
    max_dist = 120.0
    for j in range(h):
        v = (1 - 2 * (j + 0.5) / h) * tanf
        for i in range(w):
            u = (2 * (i + 0.5) / w - 1) * tanf * aspect
            dx = fwd[0] + u * right[0] + v * up[0]
            dy = fwd[1] + u * right[1] + v * up[1]
            dz = fwd[2] + u * right[2] + v * up[2]
            n = math.sqrt(dx * dx + dy * dy + dz * dz)
            dx, dy, dz = dx / n, dy / n, dz / n
            ox, oy, oz = eye
            ix, iy, iz = math.floor(ox), math.floor(oy), math.floor(oz)
            stx = 1 if dx > 0 else -1
            sty = 1 if dy > 0 else -1
            stz = 1 if dz > 0 else -1
            tdx = abs(1 / dx) if dx != 0 else 1e9
            tdy = abs(1 / dy) if dy != 0 else 1e9
            tdz = abs(1 / dz) if dz != 0 else 1e9
            tmx = ((ix + 1 - ox) if dx > 0 else (ox - ix)) * tdx
            tmy = ((iy + 1 - oy) if dy > 0 else (oy - iy)) * tdy
            tmz = ((iz + 1 - oz) if dz > 0 else (oz - iz)) * tdz
            t = 0.0
            face = 1
            hit = None
            tint = (1.0, 1.0, 1.0)
            glass_t = None
            while t < max_dist:
                if tmx < tmy and tmx < tmz:
                    ix += stx
                    t = tmx
                    tmx += tdx
                    face = 0
                elif tmy < tmz:
                    iy += sty
                    t = tmy
                    tmy += tdy
                    face = 1
                else:
                    iz += stz
                    t = tmz
                    tmz += tdz
                    face = 2
                if iy < -3:
                    break
                b = solid.get((ix, iy, iz))
                if b is None:
                    continue
                if b in SEE_THROUGH:
                    c = color(b)
                    tint = (tint[0] * (0.55 + 0.45 * c[0] / 255), tint[1] * (0.55 + 0.45 * c[1] / 255),
                            tint[2] * (0.55 + 0.45 * c[2] / 255))
                    if glass_t is None:
                        glass_t = t
                    continue
                hit = b
                break
            if hit is None:
                k = max(0.0, min(1.0, 0.5 + dy))
                c = tuple(int(sky[0][q] * k + sky[1][q] * (1 - k)) for q in range(3))
            else:
                c = color(hit)
                # A 16 x 16 texel pattern on the face and darker block edges, so blocks read as blocks.
                hx = ox + dx * t
                hy = oy + dy * t
                hz = oz + dz * t
                if face == 0:
                    fu, fv = hz - math.floor(hz), hy - math.floor(hy)
                elif face == 1:
                    fu, fv = hx - math.floor(hx), hz - math.floor(hz)
                else:
                    fu, fv = hx - math.floor(hx), hy - math.floor(hy)
                tu = min(15, int(fu * 16))
                tv = min(15, int(fv * 16))
                hsh = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791) ^ (tu * 2654435761) ^ (tv * 40503) ^ face
                tex = 0.9 + ((hsh & 0xFFFF) / 65535.0) * 0.2
                if tu == 0 or tv == 0 or tu == 15 or tv == 15:
                    tex *= 0.9
                c = tuple(min(255, int(c[q] * tex)) for q in range(3))
                nrm = [(-stx, 0, 0), (0, -sty, 0), (0, 0, -stz)][face]
                lam = max(0.0, nrm[0] * sun[0] + nrm[1] * sun[1] + nrm[2] * sun[2])
                light = 0.55 + 0.5 * lam
                if hit in EMISSIVE:
                    light = 1.25
                fog = min(1.0, t / max_dist) ** 1.6
                c = tuple(int(min(255, c[q] * light) * (1 - fog) + sky[1][q] * fog) for q in range(3))
            if glass_t is not None:
                c = tuple(int(c[q] * tint[q]) for q in range(3))
            px[i, j] = c
    img = img.resize((w * 2, h * 2), Image.NEAREST)
    if title:
        d = ImageDraw.Draw(img)
        d.rectangle([0, 0, w * 2, 34], fill=(22, 11, 13))
        d.text((10, 7), title, fill=(240, 180, 41), font=font(18))
    return img


def label_bar(img, text):
    d = ImageDraw.Draw(img)
    d.rectangle([0, img.height - 28, img.width, img.height], fill=(22, 11, 13))
    d.text((10, img.height - 24), text, fill=(246, 232, 228), font=font(15))
    return img


def main(out):
    os.makedirs(out, exist_ok=True)
    made = []
    # 1. Top-down layout of a whole dojo at each tier, each style (the plot with all rooms and 6 arenas).
    for style in design.STYLES:
        for tier in design.TIERS:
            raw = design.plot(tier, style=style)
            raw.update(design.gallery_placed(tier))
            blocks = resolved(raw, style, tier)
            title = f"{design.STYLE_NAMES[style]} dojo - {design.TIER_NAMES[tier]} (top-down, full plot)"
            img = topdown(blocks, 4, bounds=design.ISLAND, title=title, labels=LAYOUT_LABELS)
            p = os.path.join(out, f"layout_{style}_t{tier}.png")
            img.save(p)
            made.append(p)
    # 2. Eye-level: the arena and the training hall at each tier, every style.
    for style in design.STYLES:
        for tier in design.TIERS:
            a = resolved(design.arena(tier, style), style, tier)
            for x in range(-12, 38):
                for z in range(-12, 38):
                    a.setdefault((x, 0, z), "grass")
            img = eye_level(a, (-5.0, 13.0, -5.0), -45, 36,
                            title=f"{design.STYLE_NAMES[style]} - sparring arena, {design.TIER_NAMES[tier]}")
            p = os.path.join(out, f"arena_{style}_t{tier}.png")
            img.save(p)
            made.append(p)
            hall = resolved(design.hall(tier, "training", style), style, tier)
            for x in range(-14, 34):
                for z in range(-24, 34):
                    hall.setdefault((x, 0, z), "grass")
            img = eye_level(hall, (-9.0, 5.5, -13.0), -38, 8,
                            title=f"{design.STYLE_NAMES[style]} - training hall, {design.TIER_NAMES[tier]}")
            p = os.path.join(out, f"training_{style}_t{tier}.png")
            img.save(p)
            made.append(p)
            print("rendered", style, tier, flush=True)
    return made


if __name__ == "__main__":
    for p in main(sys.argv[1] if len(sys.argv) > 1 else "out"):
        print(p)
