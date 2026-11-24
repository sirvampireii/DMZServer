"""LM 4.7.68 menu mockups in Saiyan Royal chrome: the Dojo tab, an upgrade screen (layout map + card + Visit dojo)
and the Spar Board, plus the navigation map with tap counts from the main menu. Usage: python mock_menus.py <out>
"""
import os
import sys

from PIL import Image, ImageDraw

import render

S = 2  # pixels per design unit
PANEL = (22, 11, 13)
FACE = (43, 18, 20)
TRIM = (240, 180, 41)
TEXT = (246, 232, 228)
LABEL = (190, 175, 170)
DIM = (110, 100, 98)
ACCENT = (0, 170, 170)      # Sparring dark aqua
CONFIRM = (63, 175, 85)
DANGER = (226, 59, 59)
BG = (12, 8, 9)


def f(size):
    return render.font(size)


class Menu:
    def __init__(self, title, tabs, active, h):
        self.w = 426
        self.h = h
        self.img = Image.new("RGB", ((self.w + 40) * S, (h + 40) * S), BG)
        self.d = ImageDraw.Draw(self.img)
        self.ox = 20 * S
        self.oy = 20 * S
        d = self.d
        d.rounded_rectangle([self.ox, self.oy, self.ox + self.w * S, self.oy + h * S], radius=10 * S, fill=TRIM)
        d.rounded_rectangle([self.ox + 2, self.oy + 2, self.ox + self.w * S - 2, self.oy + h * S - 2], radius=10 * S - 1,
                            fill=PANEL)
        d.rounded_rectangle([self.ox + 4 * S, self.oy + 4 * S, self.ox + (self.w - 4) * S, self.oy + (h - 4) * S],
                            radius=8 * S, outline=tuple(int(ACCENT[i] * 0.4 + PANEL[i] * 0.6) for i in range(3)), width=2)
        tw = d.textlength(title, font=f(13 * S))
        d.text((self.ox + (self.w * S - tw) / 2, self.oy + 8 * S), title, fill=TRIM, font=f(13 * S))
        d.line([self.ox + 10 * S, self.oy + 28 * S, self.ox + (self.w - 10) * S, self.oy + 28 * S], fill=TRIM, width=S)
        self.y = 34
        if tabs:
            n = len(tabs)
            tw = (self.w - 20 - (n - 1) * 6) / n
            for i, t in enumerate(tabs):
                x = 10 + i * (tw + 6)
                self.button(x, self.y, tw, t, "tab" if t == active else "normal")
            self.y += 24

    def button(self, x, y, w, label, style="normal", h=16):
        d = self.d
        x0, y0 = self.ox + x * S, self.oy + y * S
        x1, y1 = x0 + w * S, y0 + h * S
        face = {"confirm": tuple(int(CONFIRM[i] * 0.38 + PANEL[i] * 0.62) for i in range(3)),
                "danger": tuple(int(DANGER[i] * 0.4 + PANEL[i] * 0.6) for i in range(3)),
                "dim": (35, 27, 28), "tab": tuple(int(ACCENT[i] * 0.3 + PANEL[i] * 0.7) for i in range(3))}.get(style, FACE)
        edge = {"confirm": CONFIRM, "danger": DANGER, "dim": (60, 52, 52), "tab": ACCENT}.get(style,
                tuple(int(ACCENT[i] * 0.6 + FACE[i] * 0.4) for i in range(3)))
        d.rounded_rectangle([x0, y0, x1, y1], radius=5 * S, fill=face, outline=edge, width=2)
        if style == "tab":
            d.rectangle([x0 + 6 * S, y1 - 3 * S, x1 - 6 * S, y1 - 2 * S], fill=ACCENT)
        ink = DIM if style == "dim" else (TEXT if style != "confirm" else (220, 255, 220))
        tw = d.textlength(label, font=f(8 * S))
        d.text((x0 + (w * S - tw) / 2, y0 + 3 * S), label, fill=ink, font=f(8 * S))

    def line(self, text, ink=TEXT, size=8):
        self.d.text((self.ox + 14 * S, self.oy + self.y * S), text, fill=ink, font=f(size * S))
        self.y += size + 4

    def kv(self, k, v):
        d = self.d
        d.text((self.ox + 14 * S, self.oy + self.y * S), k, fill=LABEL, font=f(8 * S))
        d.text((self.ox + 110 * S, self.oy + self.y * S), v, fill=TEXT, font=f(8 * S))
        self.y += 12

    def box_start(self):
        self.box_y = self.y
        self.y += 4

    def box_end(self):
        d = self.d
        d.rounded_rectangle([self.ox + 8 * S, self.oy + self.box_y * S, self.ox + (self.w - 8) * S,
                             self.oy + (self.y + 2) * S], radius=6 * S, outline=(70, 40, 42), width=2)
        self.y += 8

    def row(self, left, right=None, lstyle="normal", rstyle="normal"):
        if right is None:
            self.button(10, self.y, self.w - 20, left, lstyle)
        else:
            w = (self.w - 26) / 2
            self.button(10, self.y, w, left, lstyle)
            self.button(16 + w, self.y, w, right, rstyle)
        self.y += 22

    def note(self, x, y, text, color=(255, 230, 80)):
        """A mockup annotation (not part of the menu): tap counts in a yellow tag."""
        d = self.d
        x0, y0 = self.ox + x * S, self.oy + y * S
        tw = d.textlength(text, font=f(7 * S))
        d.rounded_rectangle([x0, y0, x0 + tw + 8 * S, y0 + 12 * S], radius=4 * S, fill=color)
        d.text((x0 + 4 * S, y0 + 2 * S), text, fill=(20, 20, 20), font=f(7 * S))

    def save(self, p):
        self.img.save(p)


def dojo_map(m, x, y, w, h, cells, highlight):
    """Native layout map: rooms as colored blocks in their spot, built ones in their tier color, empty ones dashed."""
    d = m.d
    tier_col = {0: (45, 36, 37), 1: (120, 98, 72), 2: (150, 120, 60), 3: (240, 180, 41)}
    d.rounded_rectangle([m.ox + x * S, m.oy + y * S, m.ox + (x + w) * S, m.oy + (y + h) * S], radius=6 * S,
                        fill=(40, 70, 30), outline=(90, 120, 60), width=2)
    for (cx, cy, cw, ch, name, tier) in cells:
        x0 = m.ox + (x + cx) * S
        y0 = m.oy + (y + cy) * S
        x1 = x0 + cw * S
        y1 = y0 + ch * S
        fill = tier_col[tier]
        d.rounded_rectangle([x0, y0, x1, y1], radius=3 * S, fill=fill,
                            outline=(255, 230, 80) if name == highlight else (20, 20, 20), width=3 if name == highlight else 1)
        tw = d.textlength(name, font=f(6 * S))
        d.text((x0 + (cw * S - tw) / 2, y0 + (ch * S) / 2 - 4 * S), name, fill=TEXT if tier else DIM, font=f(6 * S))


def mock_dojo_tab(out):
    m = Menu("Sparring", ["Home", "Dojo", "Spar Board"], "Dojo", 330)
    m.box_start()
    m.line("Crimson Fist Dojo", TRIM, 10)
    m.kv("Master", "Kaito (you)")
    m.kv("Style", "Melee - punches and kicks")
    m.kv("Bonus", "Melee focus: +5% spar TP")
    m.kv("Dojo Points", "86")
    m.kv("Clout this season", "142  (3rd)")
    m.kv("Members", "12 of 15    Arenas 2 of 6")
    m.box_end()
    m.row("Upgrades (2 ready)", "Visit dojo")
    m.row("Wars (1 challenge)", "Style and bonus")
    m.row("Members and elites", "Dojo leaderboard")
    m.row("Roster and bonds", "Close dojo", "normal", "danger")
    m.row("Back to main menu")
    m.note(300, 186, "tap 1 from main menu (My dojo tile)")
    p = os.path.join(out, "menu_dojo_tab.png")
    m.save(p)
    return p


def mock_upgrade(out):
    m = Menu("Sparring", None, None, 360)
    m.line("Training area", TRIM, 10)
    m.line("A room inside your hall with training dummies and drill posts.", LABEL)
    m.kv("Now", "basic")
    m.kv("After", "refined: tatami floor, lanterns, a second row of dummies")
    m.kv("Cost", "60 Dojo Points (you have 86)")
    # The hall floor plan: the balcony strip on the north wall, two rows of three rings, the south wing rooms.
    cells = [(6, 4, 184, 8, "Spectator balcony", 1),
             (6, 16, 56, 30, "Ring 1", 1), (68, 16, 56, 30, "Ring 2", 1), (130, 16, 56, 30, "Ring 3", 0),
             (6, 50, 56, 30, "Ring 4", 0), (68, 50, 56, 30, "Ring 5", 0), (130, 50, 56, 30, "Ring 6", 0),
             (6, 86, 46, 26, "Training", 1), (56, 86, 34, 26, "Meditation", 1), (94, 86, 22, 26, "Hall", 2),
             (120, 86, 34, 26, "Trophies", 0), (158, 86, 32, 26, "Quarters", 1)]
    m.line("Your hall now (floor plan) - the outlined room is what this changes:", LABEL)
    dojo_map(m, 14, m.y, 196, 118, cells, "Training")
    legend_y = m.y
    for i, (t, name) in enumerate([(0, "not built"), (1, "basic"), (2, "refined"), (3, "masterwork")]):
        col = {0: (45, 36, 37), 1: (120, 98, 72), 2: (150, 120, 60), 3: (240, 180, 41)}[t]
        m.d.rounded_rectangle([m.ox + 222 * S, m.oy + (legend_y + 6 + i * 16) * S, m.ox + 234 * S,
                               m.oy + (legend_y + 16 + i * 16) * S], radius=2 * S, fill=col)
        m.d.text((m.ox + 240 * S, m.oy + (legend_y + 6 + i * 16) * S), name, fill=TEXT, font=f(7 * S))
    m.d.text((m.ox + 222 * S, m.oy + (legend_y + 76) * S), "Bridge players see the", fill=LABEL, font=f(6 * S))
    m.d.text((m.ox + 222 * S, m.oy + (legend_y + 86) * S), "same map as text rows.", fill=LABEL, font=f(6 * S))
    m.y += 124
    m.row("Buy for 60 points", "Visit dojo", "confirm", "normal")
    m.row("Back to Upgrades", "Main menu")
    m.note(250, 300, "Buy: tap 3 from main menu")
    p = os.path.join(out, "menu_upgrade.png")
    m.save(p)
    return p


def mock_board(out):
    m = Menu("Sparring", ["Home", "Dojo", "Spar Board"], "Spar Board", 380)
    m.box_start()
    m.line("Your post", TRIM, 9)
    m.line("Friendly, no wager, any level, looking for Potential Unlock. 12:40 left.")
    m.kv("Ranked", "18 of 30 friendly spars done, 12 to go")
    m.box_end()
    m.box_start()
    m.line("On the board (3)", TRIM, 9)
    m.line("Rin  Lv 142, ranked, FF on, 1 netherite, looking for TP, waiting 2:10", TEXT)
    m.line("Goku_fan  Lv 96, friendly, FF off, no wager, waiting 0:45", TEXT)
    m.line("Vex  Lv 310, ranked, FF on, 20 points - challenges you", (85, 255, 85))
    m.box_end()
    m.row("Kind - Friendly", "Wager - none")
    m.row("Range - any level", "Take my post down")
    m.row("Looking for - Potential Unlock")
    m.row("Accept Rin", "Accept Goku_fan", "dim", "confirm")
    m.row("Accept Vex", "Challenge a player", "dim", "normal")
    m.row("Watch: Crimson Fist (2 spars)")
    m.row("Back to main menu")
    m.note(300, 248, "Accept: tap 2")
    p = os.path.join(out, "menu_spar_board.png")
    m.save(p)
    return p


def nav_map(out):
    """Every dojo action and its taps from the main menu, with the new main-menu tiles."""
    W, H = 1100, 640
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    d.text((20, 14), "Navigation: every dojo action in 3 taps or fewer from the main menu", fill=TRIM, font=f(24))
    d.text((20, 48), "Main menu tiles change: 'Sparring & Dojo' becomes 'Spar Board', 'Dojo wars' becomes 'My dojo'.",
           fill=LABEL, font=f(16))
    rows = [
        ("Spar: accept a post", "Spar Board", "Accept <name>", ""),
        ("Spar: post", "Spar Board", "Post for 15 min", ""),
        ("Spar: challenge a player", "Spar Board", "Challenge a player", "<player>"),
        ("Watch spars", "Spar Board", "Watch <dojo>", ""),
        ("View my dojo", "My dojo", "", ""),
        ("Visit my dojo", "My dojo", "Visit dojo", ""),
        ("Upgrade a room (buy)", "My dojo", "Upgrades", "Buy <room>"),
        ("Upgrade preview (map)", "My dojo", "Upgrades", "<room>"),
        ("Style / bonus", "My dojo", "Style and bonus", "<style or bonus>"),
        ("Declare a war", "My dojo", "Wars", "Bracket / Gauntlet vs <dojo>"),
        ("Accept a war / join", "My dojo", "Wars", "Accept / Join"),
        ("Name an elite", "My dojo", "Members and elites", "Make <name> elite"),
        ("Visit another dojo", "My dojo", "Dojo leaderboard", "Visit <dojo>"),
        ("Found a dojo (no dojo)", "My dojo", "Found a dojo", ""),
    ]
    y = 92
    for name, a, b, c in rows:
        d.text((20, y + 4), name, fill=TEXT, font=f(15))
        x = 300
        steps = [s for s in (a, b, c) if s]
        for i, sname in enumerate(steps):
            tw = d.textlength(sname, font=f(14))
            d.rounded_rectangle([x, y, x + tw + 20, y + 28], radius=8,
                                fill=(40, 22, 24) if i < len(steps) - 1 else (30, 60, 40),
                                outline=ACCENT if i < len(steps) - 1 else CONFIRM, width=2)
            d.text((x + 10, y + 5), sname, fill=TEXT, font=f(14))
            x += tw + 30
            if i < len(steps) - 1:
                d.text((x - 12, y + 3), ">", fill=TRIM, font=f(16))
        d.text((W - 90, y + 4), f"{len(steps)} tap" + ("s" if len(steps) > 1 else ""), fill=(255, 230, 80), font=f(15))
        y += 38
    p = os.path.join(out, "menu_navigation.png")
    img.save(p)
    return p


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "out"
    os.makedirs(out, exist_ok=True)
    for fn in (mock_dojo_tab, mock_upgrade, mock_board, nav_map):
        print(fn(out))
