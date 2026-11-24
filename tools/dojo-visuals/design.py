"""LM 4.7.68 dojo design data: ONE traditional Japanese hall per dojo (the dojo's guild hall), every room inside it.

One source for both the pictures (render.py) and the in-game generator (the template is exported into the LM jar).
A template is a dict {(x, y, z): symbol}; the palette resolves symbols to Minecraft block ids. x runs east, z south,
y up; (0, 0, 0) is the north-west corner of the hall's stone foundation.

The hall's bones (foundation, veranda, posts, shoji walls, sliding doors, tiled roof) are the same for every style;
the tier (basic, refined, masterwork) sets their materials. A style shows only in its accents: banners, ring
borders and inlays, cushions and lights, and how each room is dressed.

Bones symbols (tier):  N foundation  K veranda deck  P post  W shoji wall  J shoji grid  R roof  T roof ridge / trim
                       F floor  Y tatami  H handrail  O ground path  Q balcony floor
Style symbols:         A accent / banner  B ring border  I ring inlay  L light  M cushion  D dressing 1  E dressing 2
                       V greenery
"""

STYLES = ["ki", "melee", "strike", "evasion"]
STYLE_NAMES = {"ki": "Ki Masters", "melee": "Melee", "strike": "Strike Moves", "evasion": "Evasion"}
TIERS = [1, 2, 3]
TIER_NAMES = {1: "basic", 2: "refined", 3: "masterwork"}

BONES = {
    1: dict(N="cobblestone", K="spruce_planks", P="spruce_log", W="birch_planks", J="spruce_planks", R="spruce_planks",
            T="spruce_log", F="oak_planks", Y="oak_planks", H="spruce_planks", O="dirt_path", Q="spruce_planks"),
    2: dict(N="stone_bricks", K="dark_oak_planks", P="dark_oak_log", W="white_concrete", J="dark_oak_planks",
            R="deepslate_tiles", T="polished_deepslate", F="spruce_planks", Y="stripped_bamboo_block", H="dark_oak_planks",
            O="gravel", Q="dark_oak_planks"),
    3: dict(N="polished_blackstone_bricks", K="stripped_dark_oak_wood", P="stripped_dark_oak_log", W="white_concrete",
            J="dark_oak_planks", R="deepslate_tiles", T="gold_block", F="dark_oak_planks", Y="stripped_bamboo_block",
            H="red_concrete", O="polished_andesite", Q="stripped_dark_oak_wood"),
}

ACCENTS = {
    "ki": {1: dict(A="light_blue_wool", B="light_blue_concrete", I="calcite", L="lantern", M="light_blue_carpet",
                   D="white_wool", E="light_blue_wool", V="azalea_leaves"),
           2: dict(A="light_blue_wool", B="light_blue_concrete", I="light_blue_glazed_terracotta", L="soul_lantern",
                   M="light_blue_carpet", D="calcite", E="light_blue_stained_glass", V="azalea_leaves"),
           3: dict(A="cyan_wool", B="cyan_concrete", I="diamond_block", L="soul_lantern", M="cyan_carpet",
                   D="beacon", E="sea_lantern", V="flowering_azalea_leaves")},
    "melee": {1: dict(A="red_wool", B="red_wool", I="hay_block", L="lantern", M="red_carpet", D="hay_block",
                      E="target", V="oak_leaves"),
              2: dict(A="red_wool", B="red_concrete", I="hay_block", L="lantern", M="red_carpet", D="hay_block",
                      E="target", V="oak_leaves"),
              3: dict(A="red_wool", B="red_concrete", I="gold_block", L="lantern", M="red_carpet", D="hay_block",
                      E="target", V="cherry_leaves")},
    "strike": {1: dict(A="orange_wool", B="andesite", I="smooth_stone", L="lantern", M="gray_carpet", D="anvil",
                       E="smooth_stone", V="oak_leaves"),
               2: dict(A="orange_wool", B="orange_terracotta", I="polished_andesite", L="lantern", M="orange_carpet",
                       D="anvil", E="iron_block", V="oak_leaves"),
               3: dict(A="orange_wool", B="orange_concrete", I="copper_block", L="shroomlight", M="orange_carpet",
                       D="anvil", E="iron_block", V="oak_leaves")},
    "evasion": {1: dict(A="green_wool", B="bamboo_block", I="moss_block", L="lantern", M="green_carpet",
                        D="bamboo_block", E="moss_block", V="oak_leaves"),
                2: dict(A="green_wool", B="stripped_bamboo_block", I="moss_block", L="lantern", M="lime_carpet",
                        D="bamboo_block", E="azalea", V="azalea_leaves"),
                3: dict(A="lime_wool", B="emerald_block", I="moss_block", L="pearlescent_froglight", M="lime_carpet",
                        D="bamboo_block", E="flowering_azalea", V="cherry_leaves")},
}

# Hall geometry (blocks).
W = 89            # foundation width (x)
D = 73            # foundation depth (z)
VER = 3           # veranda width
FLOOR_Y = 2       # interior floor level
RING = 17         # ring size (square)
RING_COLS = [18, 44, 70]          # ring centers x
RING_ROWS = [17, 38]              # ring centers z
WING_Z = 50                       # partition between the hall floor and the south wing
ROOMS = {                          # south wing rooms: x range
    "training": (4, 24),
    "meditation": (26, 38),
    "trophy": (48, 64),
    "quarters": (66, 84),
}
CORRIDOR = (40, 46)
BALCONY_Z = (4, 7)                 # north mezzanine


def wall_height(tier):
    return 7 + tier


def balcony_y(tier):
    return FLOOR_Y + 5


def ring_center(k):
    """Ring k (0..5): row 1 (k 0-2) then row 2 (k 3-5), west to east."""
    return RING_COLS[k % 3], RING_ROWS[k // 3]


def put(t, x, y, z, s):
    t[(x, y, z)] = s


def fill(t, x0, y0, z0, x1, y1, z1, s):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                t[(x, y, z)] = s


def hall(tier, rings=1, rooms=None, balcony=0, roof=True):
    """The whole hall at {@code tier}: {@code rings} ring spaces built (1..6), {@code rooms} maps room name to its tier
    (0 = not built), {@code balcony} the balcony tier (0 = none)."""
    rooms = rooms or {}
    t = {}
    h = wall_height(tier)
    top = FLOOR_Y + h
    # Foundation and veranda deck.
    fill(t, 0, 0, 0, W - 1, FLOOR_Y - 2, D - 1, "N")
    fill(t, 0, FLOOR_Y - 1, 0, W - 1, FLOOR_Y - 1, D - 1, "K")
    # Veranda posts and rail.
    for x in range(0, W, 4):
        for z in (0, D - 1):
            fill(t, x, FLOOR_Y, z, x, top - 1, z, "H" if tier >= 3 else "P")
    for z in range(0, D, 4):
        for x in (0, W - 1):
            fill(t, x, FLOOR_Y, z, x, top - 1, z, "H" if tier >= 3 else "P")
    for x in range(W):
        for z in (0, D - 1):
            if not (z == D - 1 and abs(x - 43) <= 3):
                put(t, x, FLOOR_Y, z, "H")
    for z in range(D):
        for x in (0, W - 1):
            put(t, x, FLOOR_Y, z, "H")
    # Steps down to the south door.
    for i, z in enumerate(range(D, D + 3)):
        fill(t, 40, FLOOR_Y - 2 - i, z, 46, FLOOR_Y - 2 - i, z, "N")
    # Hall walls: posts every 4, shoji panels with grid bands, the main sliding doors on the south side.
    x0, z0, x1, z1 = VER, VER, W - 1 - VER, D - 1 - VER
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            wall_column(t, x, z, top, door=(z == z1 and CORRIDOR[0] <= x <= CORRIDOR[1]), along_x=True)
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            wall_column(t, x, z, top, door=False, along_x=False)
    # Interior floor.
    fill(t, x0 + 1, FLOOR_Y - 1, z0 + 1, x1 - 1, FLOOR_Y - 1, z1 - 1, "F")
    # Hall floor: rings where built, tatami where a ring could go.
    for k in range(6):
        cx, cz = ring_center(k)
        r = RING // 2
        if k < rings:
            ring(t, cx, cz, tier)
        else:
            fill(t, cx - r, FLOOR_Y - 1, cz - r, cx + r, FLOOR_Y - 1, cz + r, "Y")
    # Two rows of interior columns between the rings.
    for x in (31, 57):
        for z in range(9, WING_Z, 7):
            fill(t, x, FLOOR_Y, z, x, top - 1, z, "P")
    # Hanging lights over the rings.
    for k in range(6):
        cx, cz = ring_center(k)
        for dx in (-6, 6):
            put(t, cx + dx, top - 2, cz, "L")
    # Outside identity: style banners hanging from the eaves between the veranda posts (front and back; more of
    # them and longer as the tier rises), and a noren curtain over the main doors.
    step = 8 if tier == 1 else 4
    drop = 2 + tier
    for x in range(2, W - 1, step):
        for z in (0, D - 1):
            if z == D - 1 and abs(x - 43) <= 4:
                continue
            fill(t, x, top - drop, z, x, top - 1, z, "A")
    for x in range(CORRIDOR[0], CORRIDOR[1] + 1):
        put(t, x, FLOOR_Y + 3, D - 1 - VER, "A")
        if x % 2 == 0:
            put(t, x, FLOOR_Y + 2, D - 1 - VER, "A")
    # Banners (style accent) on the north wall.
    for x in range(8, x1, 13):
        fill(t, x, FLOOR_Y + 2, z0 + 1, x, FLOOR_Y + 5, z0 + 1, "A")
    # South wing partition with a sliding door into each room and the open corridor.
    for x in range(x0 + 1, x1):
        for y in range(FLOOR_Y, top):
            door = any(abs(x - (a + b) // 2) <= 1 for (a, b) in ROOMS.values()) and y < FLOOR_Y + 3
            corridor = CORRIDOR[0] <= x <= CORRIDOR[1] and y < FLOOR_Y + 4
            if not door and not corridor:
                put(t, x, y, WING_Z, "J" if (y - FLOOR_Y) in (3, h - 1) or x % 4 == 0 else "W")
    for (name, (a, b)) in ROOMS.items():
        for z in range(WING_Z + 1, z1):
            for y in range(FLOOR_Y, top):
                for xx in (a - 1, b + 1):
                    if x0 < xx < x1 and not (CORRIDOR[0] <= xx <= CORRIDOR[1]):
                        put(t, xx, y, z, "J" if z % 4 == 0 else "W")
        room(t, name, a, b, rooms.get(name, 0))
    # Spectator balcony: a mezzanine along the north wall with a rail, stairs down at the west end.
    if balcony > 0:
        by = balcony_y(tier)
        fill(t, x0 + 1, by, BALCONY_Z[0], x1 - 1, by, BALCONY_Z[1], "Q")
        for x in range(x0 + 1, x1):
            put(t, x, by + 1, BALCONY_Z[1], "H")
        for i in range(by - FLOOR_Y + 1):
            fill(t, x0 + 1, FLOOR_Y - 1 + i, BALCONY_Z[1] + 1 + (by - FLOOR_Y) - i, x0 + 3,
                 FLOOR_Y - 1 + i, BALCONY_Z[1] + 1 + (by - FLOOR_Y) - i, "Q")
        if balcony >= 2:
            for x in range(x0 + 4, x1, 8):
                put(t, x, by + 1, BALCONY_Z[0], "L")
        if balcony >= 3:
            for x in range(x0 + 2, x1, 6):
                put(t, x, by + 2, BALCONY_Z[1], "A")
    if roof:
        build_roof(t, tier, top)
    return t


def wall_column(t, x, z, top, door, along_x):
    post = (x % 4 == 3) if along_x else (z % 4 == 3)
    for y in range(FLOOR_Y, top):
        if door and y < FLOOR_Y + 4:
            continue
        if post:
            put(t, x, y, z, "P")
        elif (y - FLOOR_Y) in (0, 3) or y == top - 1:
            put(t, x, y, z, "J")
        else:
            put(t, x, y, z, "W")


def ring(t, cx, cz, tier):
    r = RING // 2
    fill(t, cx - r, FLOOR_Y - 1, cz - r, cx + r, FLOOR_Y - 1, cz + r, "F" if tier < 3 else "Y")
    for i in range(-r, r + 1):
        for (x, z) in [(cx + i, cz - r), (cx + i, cz + r), (cx - r, cz + i), (cx + r, cz + i)]:
            put(t, x, FLOOR_Y - 1, z, "B")
    put(t, cx, FLOOR_Y - 1, cz, "I")
    if tier >= 2:
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                d = ((x - cx) ** 2 + (z - cz) ** 2) ** 0.5
                if abs(d - 4.5) < 0.5:
                    put(t, x, FLOOR_Y - 1, z, "I")
        for dz in (-1, 0, 1):
            put(t, cx - 4, FLOOR_Y - 1, cz + dz, "A")
            put(t, cx + 4, FLOOR_Y - 1, cz + dz, "A")
    if tier >= 3:
        for k in range(2, r):
            for (sx, sz) in [(1, 1), (1, -1), (-1, 1), (-1, -1)]:
                put(t, cx + sx * k, FLOOR_Y - 1, cz + sz * k, "I")
    for (x, z) in [(cx - r, cz - r), (cx - r, cz + r), (cx + r, cz - r), (cx + r, cz + r)]:
        fill(t, x, FLOOR_Y, z, x, FLOOR_Y + tier, z, "P")
        put(t, x, FLOOR_Y + tier + 1, z, "L")


def room(t, name, a, b, tier):
    """A south-wing room between x = a..b. Not built: an empty floor behind its sliding door."""
    z0 = WING_Z + 1
    z1 = D - 1 - VER - 1
    if tier <= 0:
        return
    fill(t, a, FLOOR_Y - 1, z0, b, FLOOR_Y - 1, z1, "Y" if name in ("meditation", "quarters") or tier >= 2 else "F")
    put(t, a + 1, FLOOR_Y, z0 + 1, "L")
    put(t, b - 1, FLOOR_Y, z0 + 1, "L")
    if tier >= 2:
        put(t, a + 1, FLOOR_Y, z1 - 1, "L")
        put(t, b - 1, FLOOR_Y, z1 - 1, "L")
    if name == "training":
        for x in range(a + 2, b - 1, 3):
            put(t, x, FLOOR_Y, z1 - 2, "D")
            put(t, x, FLOOR_Y + 1, z1 - 2, "D")
            if tier >= 2:
                put(t, x, FLOOR_Y, z0 + 4, "E")
        if tier >= 3:
            for x in range(a + 3, b - 1, 3):
                put(t, x, FLOOR_Y + 2, z1 - 2, "A")
    elif name == "meditation":
        for x in range(a + 2, b - 1, 3):
            for z in range(z0 + 3, z1 - 1, 3):
                put(t, x, FLOOR_Y, z, "M")
        if tier >= 2:
            put(t, (a + b) // 2, FLOOR_Y, z1 - 2, "E")
        if tier >= 3:
            put(t, (a + b) // 2, FLOOR_Y + 1, z1 - 2, "L")
    elif name == "trophy":
        for i in range(6):
            x = a + 2 + (i % 3) * 5
            z = z0 + 4 if i < 3 else z0 + 10
            put(t, x, FLOOR_Y, z, "N")
            if tier >= 2:
                put(t, x, FLOOR_Y + 1, z, "T" if tier >= 3 else "A")
    elif name == "quarters":
        fill(t, b - 4, FLOOR_Y, z1 - 4, b - 2, FLOOR_Y, z1 - 2, "A")
        put(t, a + 2, FLOOR_Y, z1 - 2, "D" if tier < 3 else "E")
        if tier >= 2:
            fill(t, a + 2, FLOOR_Y, z0 + 2, a + 5, FLOOR_Y + 1, z0 + 2, "J")


def build_roof(t, tier, top):
    """A hip roof with flared eaves over the veranda: the eave course sticks out past the foundation with turned-up
    corners, then the roof steps in two blocks per level on the long sides and one on the short ends, up to a ridge
    (ridge trim and, at masterwork, gold finials at both ends). Each course is a solid band, so the roof never shows
    a gap from above."""
    lo_x, hi_x = -2, W + 1
    lo_z, hi_z = -2, D + 1
    y = top
    level = 0
    while lo_z <= hi_z and lo_x <= hi_x:
        for x in range(lo_x, hi_x + 1):
            for z in range(lo_z, hi_z + 1):
                if x in (lo_x, lo_x + 1, hi_x - 1, hi_x) or z in (lo_z, lo_z + 1, hi_z - 1, hi_z):
                    put(t, x, y, z, "T" if (level == 0 and tier >= 2) else "R")
        if level == 0:
            for (x, z) in [(lo_x - 1, lo_z - 1), (hi_x + 1, lo_z - 1), (lo_x - 1, hi_z + 1), (hi_x + 1, hi_z + 1)]:
                put(t, x, y + 1, z, "R")
        lo_z += 2
        hi_z -= 2
        lo_x += 1
        hi_x -= 1
        y += 1
        level += 1
    ridge_y = y - 1
    mid_z = D // 2
    for x in range(lo_x - 2, hi_x + 3):
        put(t, x, ridge_y + 1, mid_z, "T")
    if tier >= 3:
        for x in (lo_x - 2, hi_x + 2):
            put(t, x, ridge_y + 2, mid_z, "T")
            put(t, x, ridge_y + 3, mid_z, "L")


def grounds(tier, garden=1):
    """Outside the hall: lawn, a path to the south steps, stone lanterns, trees, and (garden tier 3) a torii gate."""
    t = {}
    gx0, gz0, gx1, gz1 = -16, -16, W + 15, D + 30
    fill(t, gx0, -1, gz0, gx1, -1, gz1, "grass_block")
    fill(t, 39, -1, D + 3, 47, -1, gz1, "O")
    if garden >= 1:
        for z in range(D + 6, gz1, 6):
            for x in (36, 50):
                put(t, x, 0, z, "N")
                put(t, x, 1, z, "L")
    if garden >= 2:
        for (tx, tz) in [(-8, D + 12), (W + 7, D + 12), (-8, 20), (W + 7, 20), (-8, -8), (W + 7, -8)]:
            fill(t, tx, 0, tz, tx, 3, tz, "spruce_log")
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    for y in (4, 5):
                        if abs(dx) + abs(dz) <= 3:
                            put(t, tx + dx, y, tz + dz, "V")
            put(t, tx, 6, tz, "V")
    if garden >= 3:
        for x in (37, 49):
            fill(t, x, 0, D + 22, x, 6, D + 22, "red_concrete")
        fill(t, 35, 7, D + 22, 51, 7, D + 22, "black_concrete")
        fill(t, 37, 5, D + 22, 49, 5, D + 22, "red_concrete")
    return t


def resolve(sym, style, tier):
    """Block id for a template symbol at a tier in a style (raw block ids pass through)."""
    if len(sym) == 1 and sym.isupper():
        acc = ACCENTS[style][tier]
        if sym in acc:
            return acc[sym]
        return BONES[tier][sym]
    return sym


def full(tier, style, roof=True):
    """A dojo with everything bought to {@code tier}: all six rings, every room, the balcony, the garden."""
    rooms = {n: tier for n in ROOMS}
    t = grounds(tier, tier)
    t.update(hall(tier, rings=6, rooms=rooms, balcony=tier, roof=roof))
    return {k: resolve(v, style, tier) for k, v in t.items()}
