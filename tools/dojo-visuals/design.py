"""LM 4.7.68 dojo design data: styles, palettes, tiered room templates and the plot layout.

One source for both the pictures (render.py) and the in-game generator (the templates are exported to
src/main/resources/data/legacymechanics/dojo/ by export.py). A template is a dict {(x, y, z): symbol}; a style's
palette maps symbols to Minecraft block ids. x runs east, z south, y up; (0, 0, 0) is the room's north-west floor
corner, floor at y = 0.

Symbols
  F floor        I floor inlay     B ring border    W wall          P pillar / beam   Q gallery glass
  R roof         T roof trim       L light          A accent        G glass / screen
  M mat          D decor 1         E decor 2        V greenery      S path
"""

STYLES = ["ki", "melee", "strike", "evasion"]
STYLE_NAMES = {"ki": "Ki Masters", "melee": "Melee", "strike": "Strike Moves", "evasion": "Evasion"}
TIERS = [1, 2, 3]
TIER_NAMES = {1: "basic", 2: "refined", 3: "masterwork"}

# Palette per style and tier: symbol -> block id. Tiers share a style's identity and get richer.
PALETTES = {
    "ki": {
        1: dict(Q="glass", F="smooth_stone", I="calcite", B="light_blue_concrete", W="calcite", P="birch_log", R="smooth_stone_slab",
                T="calcite", L="lantern", A="light_blue_terracotta", G="glass", M="white_carpet", D="white_wool",
                E="light_blue_wool", V="azalea_leaves", S="gravel"),
        2: dict(Q="light_blue_stained_glass", F="smooth_quartz", I="light_blue_glazed_terracotta", B="light_blue_concrete", W="quartz_bricks",
                P="quartz_pillar", R="quartz_slab", T="smooth_quartz", L="soul_lantern", A="light_blue_concrete",
                G="light_blue_stained_glass", M="light_blue_carpet", D="white_concrete", E="cyan_terracotta",
                V="azalea_leaves", S="smooth_quartz"),
        3: dict(Q="light_blue_stained_glass", F="quartz_bricks", I="diamond_block", B="cyan_concrete", W="quartz_block", P="quartz_pillar",
                R="quartz_slab", T="sea_lantern", L="sea_lantern", A="prismarine_bricks",
                G="light_blue_stained_glass", M="cyan_carpet", D="beacon", E="diamond_block",
                V="flowering_azalea_leaves", S="quartz_bricks"),
    },
    "melee": {
        1: dict(Q="glass", F="spruce_planks", I="hay_block", B="stripped_spruce_log", W="birch_planks", P="spruce_log",
                R="spruce_slab", T="spruce_planks", L="lantern", A="red_wool", G="white_wool", M="yellow_carpet",
                D="hay_block", E="target", V="oak_leaves", S="dirt_path"),
        2: dict(Q="white_stained_glass", F="spruce_planks", I="stripped_birch_log", B="red_concrete", W="white_concrete", P="dark_oak_log",
                R="dark_oak_slab", T="red_terracotta", L="lantern", A="red_concrete", G="white_stained_glass",
                M="red_carpet", D="hay_block", E="target", V="oak_leaves", S="stone_bricks"),
        3: dict(Q="white_stained_glass", F="dark_oak_planks", I="stripped_bamboo_block", B="crimson_planks", W="white_concrete",
                P="stripped_dark_oak_log", R="dark_oak_slab", T="red_concrete", L="redstone_lamp", A="gold_block",
                G="white_stained_glass", M="red_carpet", D="crimson_stem", E="target", V="cherry_leaves",
                S="polished_andesite"),
    },
    "strike": {
        1: dict(Q="glass", F="cobblestone", I="stone", B="andesite", W="stone_bricks", P="stone", R="stone_slab",
                T="cobblestone", L="torch", A="terracotta", G="iron_bars", M="gray_carpet", D="anvil",
                E="smooth_stone", V="moss_block", S="gravel"),
        2: dict(Q="gray_stained_glass", F="stone_bricks", I="polished_andesite", B="orange_terracotta", W="deepslate_bricks",
                P="polished_basalt", R="deepslate_brick_slab", T="orange_terracotta", L="lantern",
                A="orange_terracotta", G="iron_bars", M="orange_carpet", D="anvil", E="blast_furnace",
                V="moss_block", S="polished_andesite"),
        3: dict(Q="gray_stained_glass", F="polished_deepslate", I="magma_block", B="orange_concrete", W="deepslate_tiles",
                P="polished_basalt", R="deepslate_tile_slab", T="copper_block", L="shroomlight", A="copper_block",
                G="iron_bars", M="black_carpet", D="anvil", E="lodestone", V="moss_block", S="polished_deepslate"),
    },
    "evasion": {
        1: dict(Q="glass", F="bamboo_planks", I="moss_block", B="bamboo_block", W="bamboo_planks", P="bamboo_block",
                R="bamboo_slab", T="bamboo_planks", L="lantern", A="green_wool", G="bamboo_mosaic",
                M="green_carpet", D="moss_block", E="bamboo_block", V="oak_leaves", S="dirt_path"),
        2: dict(Q="green_stained_glass", F="bamboo_mosaic", I="moss_block", B="stripped_bamboo_block", W="mangrove_planks",
                P="stripped_bamboo_block", R="mangrove_slab", T="moss_block", L="lantern", A="lime_terracotta",
                G="green_stained_glass", M="lime_carpet", D="moss_block", E="azalea", V="azalea_leaves",
                S="mud_bricks"),
        3: dict(Q="green_stained_glass", F="bamboo_mosaic", I="emerald_block", B="cherry_planks", W="cherry_planks", P="stripped_cherry_log",
                R="cherry_slab", T="pink_terracotta", L="pearlescent_froglight", A="emerald_block",
                G="pink_stained_glass", M="pink_carpet", D="moss_block", E="flowering_azalea",
                V="cherry_leaves", S="mud_bricks"),
    },
}

ARENA_R = 12      # ring radius (25 x 25 floor)
ROOM_R = 9        # room footprint radius (19 x 19)


def put(t, x, y, z, s):
    t[(x, y, z)] = s


def box_floor(t, x0, z0, x1, z1, y, s):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            put(t, x, y, z, s)


def ring_posts(t, size, tier, height):
    """Corner posts with lights on top; refined adds mid-side posts, masterwork taller with trim caps."""
    corners = [(0, 0), (0, size - 1), (size - 1, 0), (size - 1, size - 1)]
    posts = corners + ([(size // 2, 0), (size // 2, size - 1), (0, size // 2), (size - 1, size // 2)] if tier >= 2 else [])
    for (x, z) in posts:
        for y in range(1, height + 1):
            put(t, x, y, z, "P")
        put(t, x, height + 1, z, "L" if tier < 3 else "T")
        if tier >= 3:
            put(t, x, height + 2, z, "L")


def arena(tier, style="melee"):
    """A sparring ring: flat floor, inlaid border and center mark, posts; tiers add an inlay pattern. Style touches:
    Strike rings sit inside a low stone wall (open at the four sides), Evasion rings in a grove of bamboo posts,
    Ki rings get glowing corner stones."""
    t = {}
    size = 2 * ARENA_R + 1
    box_floor(t, 0, 0, size - 1, size - 1, 0, "F")
    for i in range(size):
        for (x, z) in [(i, 0), (i, size - 1), (0, i), (size - 1, i)]:
            put(t, x, 0, z, "B")
    c = ARENA_R
    put(t, c, 0, c, "I")
    if tier >= 2:
        # inner circle inlay and start lines
        for x in range(size):
            for z in range(size):
                d = ((x - c) ** 2 + (z - c) ** 2) ** 0.5
                if abs(d - 5.5) < 0.5:
                    put(t, x, 0, z, "I")
        for dz in (-1, 0, 1):
            put(t, c - 6, 0, c + dz, "A")
            put(t, c + 6, 0, c + dz, "A")
    if tier >= 3:
        # diagonal inlay rays and a raised rim of trim
        for k in range(2, ARENA_R - 1):
            for (sx, sz) in [(1, 1), (1, -1), (-1, 1), (-1, -1)]:
                put(t, c + sx * k, 0, c + sz * k, "I")
        for i in range(1, size - 1):
            for (x, z) in [(i, 1), (i, size - 2), (1, i), (size - 2, i)]:
                put(t, x, 0, z, "A")
    ring_posts(t, size, tier, 2 if tier == 1 else (3 if tier == 2 else 4))
    if style == "strike":
        for i in range(1, size - 1):
            for (x, z) in [(i, -1), (i, size), (-1, i), (size, i)]:
                if abs(i - ARENA_R) > 2:
                    put(t, x, 1, z, "W")
                    if tier >= 2:
                        put(t, x, 0, z, "F")
    elif style == "evasion":
        for i in range(0, size, 3):
            for (x, z) in [(i, -2), (i, size + 1), (-2, i), (size + 1, i)]:
                if abs(i - ARENA_R) > 2:
                    for y in range(1, 2 + tier):
                        put(t, x, y, z, "P")
                    if tier >= 2:
                        put(t, x, 2 + tier, z, "V")
    elif style == "ki":
        for (x, z) in [(1, 1), (1, size - 2), (size - 2, 1), (size - 2, size - 2)]:
            put(t, x, 0, z, "L" if tier >= 2 else "I")
    return t


def roof(t, size, y0, tier):
    """Stepped hip roof: a solid pyramid of roof blocks, one step in per level, with a one-block overhang;
    refined puts a trim band at the eaves, masterwork a second band and a ridge light."""
    k = -1
    level = 0
    while True:
        lo = k
        hi = size - 1 - k
        if lo > hi:
            break
        y = y0 + level
        for x in range(lo, hi + 1):
            for z in range(lo, hi + 1):
                if x in (lo, hi) or z in (lo, hi):
                    trim = (tier >= 2 and level == 0) or (tier >= 3 and level == 2)
                    put(t, x, y, z, "T" if trim else "R")
        k += 1
        level += 1
    if tier >= 3:
        c = size // 2
        put(t, c, y0 + level, c, "L")


def hall(tier, kind, style="melee"):
    """A room in the style's architecture, props by kind; tier raises the walls and the detail.
    Melee: a Japanese dojo, hip roof (refined and up add a second eave). Ki: an open pavilion, glass between white
    columns, flat roof with a raised clerestory. Strike: a fortress block, buttresses, flat roof with crenellations.
    Evasion: a stilt house on bamboo posts, lattice walls, a leafy thatch roof."""
    if style == "ki":
        return pavilion(tier, kind)
    if style == "strike":
        return fortress(tier, kind)
    if style == "evasion":
        return stilt_house(tier, kind)
    t = {}
    size = 2 * ROOM_R + 1
    h = 4 if tier == 1 else (5 if tier == 2 else 6)
    box_floor(t, 0, 0, size - 1, size - 1, 0, "F")
    for i in range(size):
        for y in range(1, h + 1):
            for (x, z, north) in [(i, 0, True), (i, size - 1, False), (0, i, False), (size - 1, i, False)]:
                door = north and abs(i - ROOM_R) <= 1 and y <= 3
                if door:
                    continue
                window = (y in (2, 3)) and (i % 4 == 2) and (tier >= 2 or y == 3)
                if y == 1 and tier >= 2:
                    sym = "F"          # base course
                elif y == 4 and tier >= 2:
                    sym = "P"          # beam band
                elif y == h and tier >= 3:
                    sym = "A"          # accent band under the eaves
                else:
                    sym = "W"
                put(t, x, y, z, "G" if window else sym)
    if tier >= 2:
        # a lintel over the door
        for i in range(ROOM_R - 2, ROOM_R + 3):
            put(t, i, 4, 0, "A")
    for (x, z) in [(0, 0), (0, size - 1), (size - 1, 0), (size - 1, size - 1)] + (
            [(ROOM_R, size - 1), (0, ROOM_R), (size - 1, ROOM_R)] if tier >= 2 else []):
        for y in range(1, h + 1):
            put(t, x, y, z, "P")
    roof(t, size, h + 1, tier)
    if tier >= 2:
        # a second, lower eave all round (the dojo's double roof line)
        for i in range(-2, size + 2):
            for (x, z) in [(i, -2), (i, size + 1), (-2, i), (size + 1, i)]:
                put(t, x, h - 1, z, "R")
    props(t, tier, kind, size)
    return t


def props(t, tier, kind, size, y0=0):
    """Lights and the furniture that says what a room is, standing on a floor at {@code y0}."""
    # lights inside
    for (x, z) in [(3, 3), (size - 4, 3), (3, size - 4), (size - 4, size - 4)]:
        put(t, x, y0 + 1, z, "L")
    if tier >= 2:
        # floor inlay runner from the door
        for z in range(1, size - 1):
            put(t, ROOM_R, y0, z, "I")
    if kind == "training":
        for x in range(3, size - 3, 3):
            put(t, x, y0 + 1, size - 3, "D")
            put(t, x, y0 + 2, size - 3, "D")
            put(t, x, y0 + 1, 5, "E")
        if tier >= 3:
            for x in range(4, size - 4, 3):
                put(t, x, y0 + 3, size - 3, "A")
    elif kind == "meditation":
        for x in range(3, size - 3, 3):
            for z in range(4, size - 3, 3):
                put(t, x, y0 + 1, z, "M")
        if tier >= 2:
            put(t, ROOM_R, y0 + 1, size - 4, "E")
        if tier >= 3:
            put(t, ROOM_R, y0 + 2, size - 4, "L")
    elif kind == "trophy":
        for i in range(6):
            x = 4 + (i % 3) * 5
            z = 6 if i < 3 else 12
            put(t, x, y0 + 1, z, "A")
            if tier >= 2:
                put(t, x, y0 + 2, z, "D" if tier == 2 else "E")
    elif kind == "bunks":
        for x in range(3, size - 3, 3):
            put(t, x, y0 + 1, 4, "D")
            put(t, x, y0 + 1, size - 5, "D")
    elif kind == "quarters":
        box_floor(t, 3, size - 5, 5, size - 3, y0 + 1, "A")
        for x in range(size - 6, size - 2):
            for y in (y0 + 1, y0 + 2):
                put(t, x, y, size - 3, "E")
        put(t, ROOM_R, y0 + 1, size - 4, "D")
    return t


def pavilion(tier, kind):
    """Ki Masters: an open pavilion. A raised floor, white columns every three blocks with glass between, a flat
    roof, and (refined up) a smaller raised clerestory box, (masterwork) a glowing crown."""
    t = {}
    size = 2 * ROOM_R + 1
    h = 4 if tier == 1 else 5
    box_floor(t, -1, -1, size, size, 0, "T" if tier >= 2 else "F")
    box_floor(t, 0, 0, size - 1, size - 1, 1, "F")
    for i in range(size):
        for (x, z, north) in [(i, 0, True), (i, size - 1, False), (0, i, False), (size - 1, i, False)]:
            column = i % 3 == 0
            door = north and abs(i - ROOM_R) <= 1
            for y in range(2, h + 2):
                if column:
                    put(t, x, y, z, "P")
                elif not door and tier >= 2:
                    put(t, x, y, z, "G")
                elif not door and y == h + 1:
                    put(t, x, y, z, "W")
    box_floor(t, -1, -1, size, size, h + 2, "R")
    if tier >= 2:
        for x in range(4, size - 4):
            for z in range(4, size - 4):
                if x in (4, size - 5) or z in (4, size - 5):
                    for y in (h + 3, h + 4):
                        put(t, x, y, z, "G" if y == h + 3 else "W")
        box_floor(t, 3, 3, size - 4, size - 4, h + 5, "R")
    if tier >= 3:
        for (x, z) in [(3, 3), (3, size - 4), (size - 4, 3), (size - 4, size - 4)]:
            put(t, x, h + 6, z, "L")
        put(t, ROOM_R, h + 6, ROOM_R, "D")
    props(t, tier, kind, size, 1)
    return t


def fortress(tier, kind):
    """Strike Moves: a fortress block. Thick walls with slit windows, buttresses outside, a flat roof with
    crenellations; refined adds corner towers, masterwork a glowing forge crown."""
    t = {}
    size = 2 * ROOM_R + 1
    h = 5 if tier == 1 else 6
    box_floor(t, 0, 0, size - 1, size - 1, 0, "F")
    for i in range(size):
        for (x, z, north) in [(i, 0, True), (i, size - 1, False), (0, i, False), (size - 1, i, False)]:
            door = north and abs(i - ROOM_R) <= 1
            for y in range(1, h + 1):
                if door and y <= 3:
                    continue
                slit = y in (2, 3) and i % 4 == 2
                put(t, x, y, z, "G" if slit else ("F" if y == 1 else "W"))
    for i in range(2, size - 2, 4):
        for (x, z) in [(i, -1), (i, size), (-1, i), (size, i)]:
            for y in range(1, h):
                put(t, x, y, z, "P")
    box_floor(t, 0, 0, size - 1, size - 1, h + 1, "R")
    for i in range(0, size, 2):
        for (x, z) in [(i, 0), (i, size - 1), (0, i), (size - 1, i)]:
            put(t, x, h + 2, z, "W")
    if tier >= 2:
        for (cx, cz) in [(0, 0), (0, size - 1), (size - 1, 0), (size - 1, size - 1)]:
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    for y in range(h + 1, h + 4):
                        put(t, cx + dx, y, cz + dz, "P")
            put(t, cx, h + 4, cz, "T")
    if tier >= 3:
        for x in range(ROOM_R - 2, ROOM_R + 3):
            for z in range(ROOM_R - 2, ROOM_R + 3):
                put(t, x, h + 2, z, "A")
        put(t, ROOM_R, h + 3, ROOM_R, "L")
    props(t, tier, kind, size)
    return t


def stilt_house(tier, kind):
    """Evasion: a stilt house. The floor stands on bamboo posts, lattice walls (open below the eaves), a leafy
    thatch hip roof; refined adds a veranda rail, masterwork lanterns hanging under the eaves."""
    t = {}
    size = 2 * ROOM_R + 1
    lift = 2
    for i in range(0, size, 3):
        for (x, z) in [(i, 0), (i, size - 1), (0, i), (size - 1, i)]:
            for y in range(0, lift + 1):
                put(t, x, y, z, "P")
    box_floor(t, -1, -1, size, size, lift, "F")
    h = 3 if tier == 1 else 4
    for i in range(size):
        for (x, z, north) in [(i, 0, True), (i, size - 1, False), (0, i, False), (size - 1, i, False)]:
            door = north and abs(i - ROOM_R) <= 1
            for y in range(lift + 1, lift + h + 1):
                if door:
                    continue
                lattice = (i + y) % 2 == 0
                put(t, x, y, z, "P" if i % 3 == 0 else ("G" if lattice else "W"))
    for z in range(-4, 0):
        for x in range(ROOM_R - 1, ROOM_R + 2):
            put(t, x, min(lift, (z + 4) * lift // 3), z, "F")
    if tier >= 2:
        for i in range(-1, size + 1):
            for (x, z) in [(i, -1), (i, size), (-1, i), (size, i)]:
                if not (z == -1 and abs(i - ROOM_R) <= 1):
                    put(t, x, lift + 1, z, "P")
    k = -2
    level = 0
    while True:
        lo = k
        hi = size - 1 - k
        if lo > hi:
            break
        for x in range(lo, hi + 1):
            for z in range(lo, hi + 1):
                if x in (lo, hi) or z in (lo, hi):
                    put(t, x, lift + h + 1 + level, z, "V" if tier >= 2 or level > 0 else "R")
        k += 1
        level += 1
    if tier >= 3:
        for (x, z) in [(-1, -1), (-1, size), (size, -1), (size, size)]:
            put(t, x, lift + h, z, "L")
    props(t, tier, kind, size, lift)
    return t


def gallery(tier):
    """Spectator room: a glass-floored gallery above the arenas with a solid walkway and rails; tiers add trim."""
    t = {}
    w, d = 133, 83
    for x in range(w):
        for z in range(d):
            put(t, x, 0, z, "Q")
    # The walkway runs east-west over the gap between the two arena rows, so every ring is in view below.
    mid = 40
    for x in range(1, w - 1):
        for z in range(mid - 2, mid + 3):
            put(t, x, 0, z, "F")
    for x in range(w):
        for y in (1, 2, 3):
            put(t, x, y, 0, "Q")
            put(t, x, y, d - 1, "Q")
    for z in range(d):
        for y in (1, 2, 3):
            put(t, 0, y, z, "Q")
            put(t, w - 1, y, z, "Q")
    if tier >= 2:
        for x in range(4, w - 1, 8):
            put(t, x, 1, mid - 3, "L")
            put(t, x, 1, mid + 3, "L")
    if tier >= 3:
        for x in range(w):
            put(t, x, 4, 0, "T")
            put(t, x, 4, d - 1, "T")
    return t


def garden(tier):
    t = {}
    w, d = 33, 25
    box_floor(t, 0, 0, w - 1, d - 1, 0, "V" if False else "F")
    for x in range(w):
        for z in range(d):
            t[(x, 0, z)] = "grass"
    for z in range(d):
        for x in range(w // 2 - 2, w // 2 + 3):
            put(t, x, 0, z, "S")
    if tier >= 1:
        flowers = ["poppy", "dandelion", "blue_orchid", "allium", "oxeye_daisy"]
        k = 0
        for x in range(2, w - 2, 3):
            for z in range(2, d - 2, 4):
                if abs(x - w // 2) > 3:
                    t[(x, 1, z)] = flowers[k % len(flowers)]
                    k += 1
    if tier >= 2:
        for (x, z) in [(4, 4), (w - 5, 4), (4, d - 5), (w - 5, d - 5)]:
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    put(t, x + dx, 1, z + dz, "V")
                    put(t, x + dx, 2, z + dz, "V")
    if tier >= 3:
        for (x, z) in [(8, d // 2), (w - 9, d // 2)]:
            for y in range(1, 5):
                put(t, x, y, z, "P")
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    for y in (5, 6):
                        if abs(dx) + abs(dz) < 4:
                            put(t, x + dx, y, z + dz, "V")
            put(t, x, 1, z + 3, "L")
    return t


# Plot layout: offsets of each piece's north-west corner from the plot origin (matches DojoWorld).
def arena_origin(k):
    cx = -48 + (k % 3) * 48
    cz = -50 - (k // 3) * 40
    return (cx - ARENA_R, cz - ARENA_R)


ROOM_ORIGINS = {
    "training": (-48 - ROOM_R, 40 - ROOM_R),
    "meditation": (0 - ROOM_R, 40 - ROOM_R),
    "trophy": (48 - ROOM_R, 40 - ROOM_R),
    "quarters": (-30 - ROOM_R, 78 - ROOM_R),
    "bunks": (30 - ROOM_R, 78 - ROOM_R),
}
GALLERY_ORIGIN = (-66, -110)
GALLERY_Y = 16
GARDEN_ORIGIN = (-16, -12)
ISLAND = (-80, -110, 80, 100)


def site(t, tier):
    """The grounds every dojo shares: lawn, a perimeter wall with a south gate arch, paths linking every room and
    arena, lamp posts along the paths and trees inside the wall. Tier raises the wall, the gate and the lights."""
    x0, z0, x1, z1 = ISLAND
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t[(x, 0, z)] = "grass"
    wall_h = 1 if tier == 1 else 2
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            for y in range(1, wall_h + 1):
                t[(x, y, z)] = "W"
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            for y in range(1, wall_h + 1):
                t[(x, y, z)] = "W"
    for x in range(x0, x1 + 1, 10):
        for z in (z0, z1):
            for y in range(1, wall_h + 2):
                t[(x, y, z)] = "P"
            if tier >= 2:
                t[(x, wall_h + 2, z)] = "L"
    for z in range(z0, z1 + 1, 10):
        for x in (x0, x1):
            for y in range(1, wall_h + 2):
                t[(x, y, z)] = "P"
            if tier >= 2:
                t[(x, wall_h + 2, z)] = "L"
    for x in range(-3, 4):
        for y in range(1, wall_h + 3):
            t.pop((x, y, z1), None)
    gate_h = 5 + tier
    for x in (-4, 4):
        for y in range(1, gate_h + 1):
            t[(x, y, z1)] = "P"
    for x in range(-5, 6):
        t[(x, gate_h + 1, z1)] = "T" if tier >= 2 else "R"
    if tier >= 3:
        for x in range(-3, 4):
            t[(x, gate_h + 2, z1)] = "R"
        t[(0, gate_h + 3, z1)] = "L"

    def path(xa, za, xb, zb, wdt=2):
        for x in range(min(xa, xb) - wdt, max(xa, xb) + wdt + 1):
            for z in range(min(za, zb) - wdt, max(za, zb) + wdt + 1):
                if t.get((x, 0, z)) == "grass":
                    t[(x, 0, z)] = "S"
    path(0, z1 - 1, 0, -38)
    path(-48, 24, 48, 24)
    path(-48, 62, 48, 62, 1)
    for x in (-48, 0, 48):
        path(x, 24, x, 30, 1)
        path(x, -38, x, -24, 1)
    path(-48, -24, 48, -24, 1)
    path(-30, 62, -30, 68, 1)
    path(30, 62, 30, 68, 1)
    for z in range(-30, z1 - 4, 12):
        for x in (-4, 4):
            t[(x, 1, z)] = "P"
            t[(x, 2, z)] = "P" if tier >= 2 else "L"
            if tier >= 2:
                t[(x, 3, z)] = "L"
    for x in range(-60, 61, 15):
        if abs(x) <= 4:
            continue
        t[(x, 1, 20)] = "P"
        t[(x, 2, 20)] = "L"
    for (tx, tz) in [(-72, 90), (72, 90), (-72, 10), (72, 10), (-72, -70), (72, -70), (-60, 92), (60, 92)]:
        for y in range(1, 4):
            t[(tx, y, tz)] = "P"
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                for y in (4, 5):
                    if abs(dx) + abs(dz) <= 3:
                        t[(tx + dx, y, tz + dz)] = "V"
        t[(tx, 6, tz)] = "V"


def plot(tier, arenas=6, rooms=None, style="melee"):
    """A whole dojo at one tier: grounds, arenas, rooms and garden (symbols, plus raw block ids)."""
    rooms = rooms if rooms is not None else ["training", "meditation", "trophy", "quarters", "bunks"]
    t = {}
    site(t, tier)
    g = garden(tier)
    for (x, y, z), s in g.items():
        t[(GARDEN_ORIGIN[0] + x, y, GARDEN_ORIGIN[1] + z)] = s
    for k in range(arenas):
        ox, oz = arena_origin(k)
        for (x, y, z), s in arena(tier, style).items():
            t[(ox + x, y, oz + z)] = s
    for name in rooms:
        ox, oz = ROOM_ORIGINS[name]
        for (x, y, z), s in hall(tier, name, style).items():
            t[(ox + x, y, oz + z)] = s
    return t


def gallery_placed(tier):
    out = {}
    for (x, y, z), s in gallery(tier).items():
        out[(GALLERY_ORIGIN[0] + x, GALLERY_Y + y, GALLERY_ORIGIN[1] + z)] = s
    return out


def resolve(sym, style, tier):
    """Block id for a template symbol (raw block ids pass through)."""
    if len(sym) == 1 and sym.isupper():
        return PALETTES[style][tier][sym]
    return sym
