"""
LM GuiKit corpus builder: page definitions (pages/*.json) -> GuiKit layout specs (tools/specs/*.json, tools/guikit.json)
plus a report of what the REAL LM layout does wrong (findings/layout-findings.md).

The placement mirrors LegacyMechanics, number for number:
  gui/ultra/UltraScreenSpec.java   toMenuPacket (fitMenu reflow of the legacy 350 grid), placeBack, placeBreadcrumb,
                                   appendTabButtons, addButton / menuLabel (server-side label clipping)
  gui/ultra/UltraStyle.java        MENU_* metrics, wrapBody, textWidthPx, backTo, labelMaxWidth
  gui/ultra/ModelPreview.java      menuBox
  gui/ultra/PageRegistry.java      titles and parents (read from the Java source at build time)
  client/NativeMenuScreen.java     body lines, info box / cards, bars and dots, Close button spot, breadcrumb
When any of those change, change this file; when a screen's code changes, change its page in pages/*.json.

A page may carry "mock": changes applied to the MOCKUP only (the target state or a fix for something the real layout
gets wrong). Every "mock" change is listed in findings/layout-findings.md with the real value next to it, so the
corpus always passes GuiKit and the findings always say what the Java must change.

  python gen/build_corpus.py            (from tools/lm-gui-corpus; PYTHONUTF8=1 on Windows)
"""
import json
import os
import re
import sys
import copy

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
LM = os.path.normpath(os.path.join(ROOT, '..', 'dmz-adaptive-difficulty', 'src', 'main', 'java', 'com', 'dbzlegacy',
                                   'adaptivedifficulty', 'gui', 'ultra'))

# ---------------------------------------------------------------- UltraStyle
EDGE, GAP, BH = 10, 6, 16
MENU_W = MENU_MODEL_W = 426
MENU_MAX_W = 460
TAB_Y = 28
HEADER_BOTTOM = 42
LINE_H, LINE_GAP, BODY_PAD = 11, 2, 6
FOOTER_GAP, END_PAD, CLOSE_BAND = 6, 6, 28
MODEL_BOTTOM = 195
COL_L, COL_R, BTN_W, BTN_MIN_W, BTN_PAD = 16, 174, 150, 80, 8
CHAR_W_EST = 6
TARGET_MIN_SCALE = 0.75

SYSTEM_ACCENT = {'difficulty': 'c', 'rival': 'f', 'spar': '3', 'dojo': '3', 'prestige': 'b', 'character': 'a',
                 'skillcheck': 'e', 'skills': 'e', 'admin': '4', 'config': '4', 'progression': '4'}
ACCENT_NAME = {'c': 'difficulty', 'f': 'rival', '3': 'spar', 'b': 'prestige', 'a': 'character', 'e': 'skillcheck',
               '4': 'admin'}


def text_width_px(s):
    """UltraStyle.textWidthPx (the server's estimate)."""
    w = 0
    for c in s:
        if c in "i!,.:;|'`":
            w += 2
        elif c == 'l':
            w += 3
        elif c in ' It[]':
            w += 4
        elif c in 'fk"()*<>{}':
            w += 5
        elif c in '@~':
            w += 7
        else:
            w += 6
    return w


def plain(s):
    return re.sub('§.', '', s or '').strip()


def label_max(w):
    return max(CHAR_W_EST, w - BTN_PAD * 2)


def clip_label(raw, w):
    """UltraStyle.clipPlain + fitAsciiLabel: (w-16)/6 characters, else cut with '...'."""
    text = plain(raw)
    max_chars = max(1, label_max(w) // CHAR_W_EST)
    if len(text) <= max_chars:
        return text
    if max_chars <= 3:
        return text[:max_chars]
    return text[:max_chars - 3].strip() + '...'


def wrap_body(styled, fit_px):
    """UltraStyle.wrapBody: break on spaces at fitPx / 8 plain characters; colour codes ride along."""
    budget = max(8, max(8, fit_px) // 8)
    out = []
    for para in styled.split('\n'):
        words = para.split(' ')
        line, n, active = '', 0, ''
        for word in words:
            wl = len(plain(word)) if plain(word) else 0
            if not word:
                continue
            if n and n + 1 + wl > budget:
                out.append(line)
                line, n = active, 0
            if wl > budget and not n:
                # a token wider than the line continues on the next line
                out.append(line + word)
                line, n = '', 0
                continue
            line = line + (' ' if n else '') + word
            n += (1 if n else 0) + wl
            for m in re.finditer('§(.)', word):
                if m.group(1) in '0123456789abcdefr':
                    active = '§' + m.group(1)
                elif m.group(1) == 'l':
                    active += '§l'
        if plain(line):
            out.append(line)
    return out


def wrap_real(styled, room):
    """Target wrap: break on spaces where the real Minecraft width would pass the body box."""
    out = []
    for para in styled.split('\n'):
        line, active = '', ''
        for word in para.split(' '):
            if not word:
                continue
            tried = word if not plain(line) else line + ' ' + word
            if plain(line) and measure(tried) > room:
                out.append(line)
                line = active + word
            else:
                line = tried if plain(line) else (line + word)
            for m in re.finditer('§(.)', word):
                if m.group(1) in '0123456789abcdefr':
                    active = '§' + m.group(1)
                elif m.group(1) == 'l':
                    active += '§l'
        if plain(line):
            out.append(line)
    return out


def body_fit_px(model_x):
    list_w = MENU_W - 16
    if model_x is not None and model_x > 16:
        list_w = max(1, min(list_w, model_x - 16))
    return max(8, list_w - 6)


def menu_model_box(m):
    if m is None:
        return None
    lx, ly, lw, lh = m['x'], m['y'], m['w'], m['h']
    if (lx, ly, lw, lh) == (248, 60, 86, 120):
        return [MENU_MODEL_W - EDGE - 86, 46, 86, 120]
    if (lx, ly, lw, lh) == (190, 46, 144, 186):
        return [MENU_MODEL_W - EDGE - 99, 46, 99, 128]
    max_h = MODEL_BOTTOM - 46 - GAP - BH
    k = min(1.0, max_h / max(1, lh))
    w, h = max(16, round(lw * k)), max(16, round(lh * k))
    return [MENU_MODEL_W - EDGE - w, 46, w, h]


# ---------------------------------------------------------------- PageRegistry (read from the Java source)
class Registry:
    def __init__(self):
        src = open(os.path.join(LM, 'PageRegistry.java'), encoding='utf-8').read()
        src = src.replace('HUB_MAIN', '"main"').replace('HUB', '"hub"')
        self.pages = []
        for m in re.finditer(r'\b(p|item|staff)\(\s*"([^"]*)",\s*"([^"]*)",\s*"([^"]*)",\s*(null|"[^"]*")', src):
            self.pages.append((m.group(2), m.group(3), m.group(4), None if m.group(5) == 'null' else m.group(5)[1:-1]))
        for m in re.finditer(r'new Page\(\s*"([^"]*)",\s*"([^"]*)",\s*"([^"]*)",\s*(null|"[^"]*")', src):
            self.pages.append((m.group(1), m.group(2), m.group(3), None if m.group(4) == 'null' else m.group(4)[1:-1]))

    @staticmethod
    def match(pattern, page):
        rx = '^' + re.escape(pattern).replace(r'\*\*', '(.*)').replace(r'\*', '([^:]*)') + '$'
        return re.match(rx, page)

    def find(self, screen, page):
        for s, pat, title, parent in self.pages:
            if s == screen and pat == page:
                return (s, pat, title, parent, None)
        for s, pat, title, parent in self.pages:
            if s == screen:
                m = self.match(pat, page)
                if m:
                    return (s, pat, title, parent, m)
        return None

    def title(self, screen, page):
        f = self.find(screen, page)
        if MOCKING and f:
            key = screen + '|' + re.sub(r'(:\*+)+$', '', f[1])
            if key in MOCK_TITLES:
                return MOCK_TITLES[key]
        return f[2] if f else page

    def parent(self, screen, page):
        f = self.find(screen, page)
        if not f or not f[3]:
            return None
        parent = f[3]
        if f[4]:
            for i, g in enumerate(f[4].groups(), 1):
                parent = parent.replace('$%d' % i, g)
        s, p = parent.split('|', 1)
        return [s, p]

    def system_home(self, screen, page):
        up = self.parent(screen, page)
        return up is not None and up == ['hub', 'main'] and screen != 'hub'

    def crumbs(self, screen, page):
        out, seen = [], set()
        s, p = screen, page
        while s is not None and (s + '|' + p) not in seen and len(out) < 12:
            seen.add(s + '|' + p)
            out.insert(0, (s, p, self.title(s, p)))
            up = self.parent(s, p)
            s, p = (up[0], up[1]) if up else (None, None)
        return out


REG = None
MOCKING = False
# PageRegistry titles the MOCKUPS use instead of today's (each is a finding: change the title in PageRegistry.java)
GLOBAL_CHANGES = [
    ({'id': '(every page)', 'source': 'client/NativeMenuScreen.java headerLine / lineGroups'}, 'cards',
     'a UltraStyle.notice line ("Notice ...") starts bold, so headerLine counts it as a card heading and the notice text '
     'falls out of its card. Count a line as a heading only when it is UltraStyle.header (or skip lines starting "Notice ").'),
    ({'id': '(every page)', 'source': 'gui/ultra/UltraStyle.java wrapBody / BODY_GLYPH_PX'}, 'body wrap',
     'body text wraps at fitPx / 8 characters (50 per line) although the font averages 6 px: lines stop at about 60% '
     'of the 406 px body box. Wrap by measured width (textWidthPx) to the box.'),
    ({'id': '(every page)', 'source': 'gui/ultra/UltraScreenSpec.java toMenuPacket footer'}, 'footer width',
     'the Back / Main menu footer keeps UltraStyle.BTN_W (150) while the rows above widen to 198: use the widened '
     'column width (round(BTN_W * fx)) so the footer lines up with the columns.'),
    ({'id': '(every page)', 'source': 'net/gui/NativeMenuFit.java MIN_SCALE'}, 'fit floor',
     'MIN_SCALE 0.5 -> 0.75: a tall menu shrinks until 16 px rows are 8 px before it scrolls; at 0.75 every row stays '
     '12 px and the page scrolls instead (the corpus uses minScale 0.75 + scroll).'),
]
GLOBAL_CHANGES.append(({'id': '(light skins)', 'source': 'gui/ultra/MenuSkins.java Skin.muted'}, 'contrast',
    'muted() blends text toward the panel by 0.42 on a light skin: Zeno Omni inactive tabs and dim labels read 3.3:1 on '
    'the dim face (target 4.5:1). Blend 0.30 (4.7:1); dark skins keep 0.52. GuiKit LmLook.muted carries the target value.'))
MOCK_TITLES = {
    'prestige|mastery_lines': 'Forms',          # "Apply points to a form": Back reads "Back to Apply points to a", crumbs fold
    'prestige|mastery_wallet': 'Mastery',
    'spar|weights_input': 'Weight amount',
    'dojo|found_style': 'Style',                # founding steps 2-4 are all titled "Found a dojo": "Found a dojo > Found a dojo"
    'dojo|found_banner': 'Banner',
    'dojo|found_confirm': 'Confirm',      # "Weights > Weights": the amount page has its parent's title       # with the above, the Form mastery breadcrumb fits (it folded at 505 / 406 px)
}


def back_to(dest):
    """UltraStyle.backTo: a long page name drops its last words so the label fits a 150 button."""
    dest = dest.strip()
    room = label_max(BTN_W)
    while text_width_px('Back to ' + dest) > room and ' ' in dest:
        dest = dest[:dest.rfind(' ')].strip()
    return 'Back to ' + dest


def footer_for(page):
    """UltraScreenSpec.placeBack on an LM client (native nav; history = the registry parent). [(slot, label)]"""
    kind = page.get('footer')
    if kind == 'backOnly':
        return [('left', page.get('footerLabels', ['Back to main menu'])[0])]
    if kind == 'guide':
        labs = page.get('footerLabels', [])
        return [('left', labs[0]), ('right', labs[1] if len(labs) > 1 else 'Skip guide')]
    if not page.get('back'):
        return []
    screen, pg = page['screen'], page['page']
    tabs = page.get('tabs') or []
    active = page.get('activeTab', '')
    first_tab = bool(tabs) and tabs[0][0] == active
    other_tab = bool(tabs) and not first_tab
    home = first_tab or (not other_tab and REG.system_home(screen, pg))
    if not other_tab:
        prev = REG.parent(screen, pg)
        label = 'Back to main menu' if prev is None or prev == ['hub', 'main'] else back_to(REG.title(prev[0], prev[1]))
    else:
        up = None if home else REG.parent(screen, pg)
        if up is None or up == ['hub', 'main']:
            label = 'Back to main menu'
        elif up[0] != screen:
            label = back_to(REG.title(up[0], up[1])) if REG.system_home(up[0], up[1]) else 'Back to main menu'
        else:
            label = back_to(REG.title(up[0], up[1]))
    if home or label == 'Back to main menu':
        return [('right', label)]
    return [('left', label), ('right', 'Main menu')]


# ---------------------------------------------------------------- the reflow (UltraScreenSpec.toMenuPacket)
HEADER_RX = re.compile('^(§6§l|§l)')
BAR_RX = re.compile(r'\[(#*)(-*)\]\s*$')
DOTS_RX = re.compile(r'(\[[x ]\])(\s\[[x ]\])+\s*$')


def amp(s):
    return (s or '').replace('&', '§')


def safe_id(s, used):
    base = re.sub('[^a-z0-9]+', '_', (s or 'x').lower()).strip('_')[:40] or 'x'
    out, n = base, 1
    while out in used:
        n += 1
        out = '%s_%d' % (base, n)
    used.add(out)
    return out


def layout_menu(page, findings):
    """Place one fitMenu page. Returns (panel element, real-layout facts)."""
    screen = page['screen']
    accent_letter = SYSTEM_ACCENT.get(screen)
    tabs = page.get('tabs') or []
    has_tabs = bool(tabs)
    header_bottom = TAB_Y if has_tabs else HEADER_BOTTOM
    content_top = TAB_Y + BH + 4 if has_tabs else HEADER_BOTTOM
    model = menu_model_box(page.get('model'))
    lines = []
    for raw in page.get('lines', []):
        if raw == '':
            lines.append('')                  # pictures() pads its rows with literal empty lines
            continue
        if MOCKING:   # target: wrap to the body box by real widths (today: 8 px per glyph, about 60% of the box)
            room = ((model[0] - 6) if model else MENU_W - EDGE) - EDGE
            lines += wrap_real(amp(raw), room)
        else:
            lines += wrap_body(amp(raw), body_fit_px(model[0] if model else None))
    rows_in = page.get('rows', [])
    if page.get('book'):                     # a paged_book input is a row of its own at its legacy y (above the rows)
        rows_in = [[{'book': page['book']['id'], 'x': page['book']['x'], 'w': page['book']['w'], 'h': page['book']['h']}]] + rows_in
    fixed = [c for r in rows_in for c in r if c.get('fixed')]
    rows = [[c for c in r if not c.get('fixed')] for r in rows_in]
    rows = [r for r in rows if r]
    if not page.get('model'):
        rows += [[c] for c in fixed]
        fixed = []
    # menuWidth
    model_col = (model[2] + 8) if model else 0
    box_w = MENU_MODEL_W if model else MENU_W
    if tabs:
        box_w = max(box_w, EDGE * 2 + len(tabs) * BTN_MIN_W + (len(tabs) - 1) * GAP)
    for r in rows:
        box_w = max(box_w, EDGE * 2 + len(r) * BTN_MIN_W + (len(r) - 1) * GAP + model_col)
    if page.get('title'):
        box_w = max(box_w, text_width_px(plain(page['title'])) + EDGE * 2 + 16)
    box_w = min(MENU_MAX_W, box_w)
    if model:
        model = [model[0] + box_w - MENU_MODEL_W] + model[1:]
    left = EDGE
    right = (model[0] - 8) if model else box_w - EDGE
    legacy_left = COL_L
    legacy_right = max(COL_L + BTN_W, page['model']['x'] - 8) if page.get('model') else COL_R + BTN_W
    step = LINE_H + LINE_GAP
    body_h = BODY_PAD * 2 + len(lines) * step if lines else 0
    els, used = [], set()

    def button(label, x, y, w, kind='normal', accent=None, active=False, real_label=None, action=None):
        e = {'id': safe_id(plain(label) or action, used), 'type': 'button', 'text': label, 'x': x, 'y': y, 'w': w, 'h': BH}
        if kind != 'normal':
            e['kind'] = kind
        if accent:
            e['accent'] = accent
        if active:
            e['active'] = True
        if action:
            e['_comment'] = action
        els.append(e)
        return e

    # tabs (appendTabButtons)
    if tabs:
        inner = box_w - EDGE * 2
        n = len(tabs)
        tab_w = max(BTN_MIN_W, (inner - GAP * (n - 1)) // n)
        if tab_w * n + GAP * (n - 1) > inner:
            tab_w = max(40, (inner - GAP * (n - 1)) // n)
        x = EDGE
        for tid, tlabel in tabs:
            active = tid.lower() == (page.get('activeTab') or '').lower()
            shown = clip_label(tlabel, tab_w)
            if shown != plain(tlabel):
                findings.append((page, 'tab label cut', 'tab:%s "%s" shows as "%s" (%d px tab)' % (tid, tlabel, shown, tab_w)))
            button(shown, x, TAB_Y, tab_w, 'normal' if active else 'dim', active=active, action='tab:' + tid)
            x += tab_w + GAP
    # rows (legacy grid widened to the content)
    fx = (right - left) / max(1, legacy_right - legacy_left)
    y = content_top + (body_h + GAP if body_h > 0 else 0)
    bottom = content_top + body_h
    inputs = {i['id']: i for i in page.get('inputs', [])}
    for r in rows:
        n = len(r)
        xs, ws, clean = [0] * n, [0] * n, True
        for i, c in enumerate(r):
            w = round(c.get('w', BTN_W) * fx)
            if 'action' in c:
                w = max(BTN_MIN_W, w)
            w = min(w, right - left)
            x = left + round((c.get('x', COL_L) - legacy_left) * fx)
            xs[i] = max(left, min(x, right - w))
            ws[i] = w
            if i > 0 and xs[i] < xs[i - 1] + ws[i - 1] + GAP:
                clean = False
        if not clean:
            w = (right - left - GAP * (n - 1)) // n
            for i in range(n):
                ws[i], xs[i] = w, left + i * (w + GAP)
        row_h = BH
        for i, c in enumerate(r):
            if 'action' in c:
                raw = amp(c['label'])
                shown = clip_label(raw, ws[i])
                if shown != plain(raw):
                    findings.append((page, 'label cut', '%s "%s" is cut to "%s" on a %d px button (room for %d characters)'
                                     % (c['action'], plain(raw), shown, ws[i], label_max(ws[i]) // CHAR_W_EST)))
                kind = c.get('style', 'normal')
                if c['action'] == 'nav:none':
                    kind = 'locked'
                acc = c.get('palette')
                sk = swatch_for(page, r, c)
                if sk:                       # Menu skins: the locked label beside a skin is drawn as its live swatch
                    els.append({'id': safe_id('swatch_' + sk[0], used), 'type': 'swatch', 'skin': sk[0], 'x': xs[i], 'y': y,
                                'w': ws[i], 'h': BH, 'text': 'You are using this' if sk[1] else 'Sample text'})
                else:
                    button(shown, xs[i], y, ws[i], kind, accent=acc, action=c['action'])
            elif 'input' in c and inputs.get(c['input'], {}).get('type') == 'color_wheel':
                h = c.get('size', inputs[c['input']].get('size', 56))
                els.append({'id': safe_id('wheel_' + c['input'], used), 'type': 'colorwheel', 'x': xs[i], 'y': y,
                            'w': h, 'h': h, 'value': 0.0})
                row_h = max(row_h, h)
            elif 'input' in c:
                inp = inputs.get(c['input'], {})
                h = BH + 2
                if inp.get('type') == 'paged_book':
                    h = max(40, round(c.get('h', 88) * 0.75))
                e = {'id': safe_id('field_' + c['input'], used), 'type': 'field', 'x': xs[i], 'y': y, 'w': ws[i], 'h': h,
                     'placeholder': inp.get('placeholder', '')}
                if inp.get('value'):
                    e['text'] = inp['value']
                els.append(e)
                row_h = max(row_h, h)
            elif 'wheel' in c:
                h = c.get('h', c.get('size', 44))
                els.append({'id': safe_id('wheel_' + c['wheel'], used), 'type': 'colorwheel', 'x': xs[i], 'y': y,
                            'w': min(ws[i], h), 'h': h, 'value': (len(els) * 0.17) % 1})
                row_h = max(row_h, h)
            elif 'book' in c:
                b = page['book']
                h = max(40, round(c.get('h', 88) * 0.75))
                pg = b['pages'][b.get('startPage', 0)]
                nav_w = max(80, (ws[i] - 12) // 3)
                kids = []
                if len(b['pages']) > 1:
                    kids.append({'id': 'next', 'type': 'button', 'text': 'Next page', 'anchor': 'bottom_right', 'x': -4, 'y': -4,
                                 'w': nav_w, 'h': BH})
                els.append({'id': safe_id('book_' + b['id'], used), 'type': 'book', 'x': xs[i], 'y': y, 'w': ws[i], 'h': h,
                            'title': pg['title'], 'value': len(b['pages']), 'text': chr(10).join(pg['lines']), 'children': kids})
                row_h = max(row_h, h)
        bottom = max(bottom, y + row_h)
        y += row_h + GAP
    if model:
        model_bottom = model[1] + model[3]
        column_bottom = model_bottom + len(fixed) * (BH + GAP)
        footer_h = FOOTER_GAP + BH if footer_for(page) else 0
        scrolls = max(bottom, column_bottom) + footer_h + END_PAD + CLOSE_BAND > 250
        bottom = max(bottom, model_bottom)
        ty = y if scrolls else model_bottom + GAP
        for c in fixed:
            if scrolls:
                w, x = max(BTN_MIN_W, min(c.get('w', 86), right - left)), left
            else:
                w = max(BTN_MIN_W, min(c.get('w', 86), model[2]))
                x = min(box_w - EDGE - w, model[0] + (model[2] - w) // 2)
            button(clip_label(amp(c['label']), w), x, ty, w, c.get('style', 'normal'), action=c['action'])
            bottom = max(bottom, ty + BH)
            ty += BH + GAP
        els.append({'id': 'model', 'type': 'figure', 'x': model[0], 'y': model[1], 'w': model[2], 'h': model[3]})
    # footer row
    foot = footer_for(page)
    footer_y = bottom + FOOTER_GAP
    # today the footer keeps BTN_W (150) while the rows above widen to the content (198); the target matches the columns
    fw = BTN_W if not MOCKING or model else round(BTN_W * fx)
    for slot, label in foot:
        x = EDGE if slot == 'left' else box_w - EDGE - fw
        shown = label if text_width_px(label) <= label_max(fw) else clip_label(label, fw)
        button(shown, x, footer_y, fw, action='footer:' + slot)
    if foot:
        bottom = footer_y + BH
    panel_h = bottom + END_PAD + CLOSE_BAND
    # the client's Close (NativeMenuScreen.closeSpot): bottom band right, else left, else top right
    close = [box_w - EDGE - 80, panel_h - CLOSE_BAND + 6]
    button('Close', close[0], close[1], 80, action='close')
    # body (drawBody / drawInfoBox / drawSkinLine)
    if lines:
        body_right = (model[0] - 6) if model else box_w - EDGE
        max_w = body_right - EDGE
        def is_header(l):
            # NativeMenuScreen.headerLine counts any line that starts bold; UltraStyle.notice lines ("Notice ...") start
            # bold too, so today every notice opens a new card. Target: a notice is body text.
            return bool(HEADER_RX.match(l)) and not (MOCKING and plain(l).startswith('Notice '))
        headers = sum(1 for l in lines if is_header(l))
        groups = None
        if headers >= 2:
            groups, start = [], -1
            for i, l in enumerate(lines):
                if is_header(l):
                    if start >= 0 and i - 1 >= start:
                        groups.append((start, i - 1))
                    start = i + 1
                elif start < 0:
                    start = i
            if 0 <= start <= len(lines) - 1:
                groups.append((start, len(lines) - 1))
        body = []
        if groups:
            for a, b in groups:
                top = content_top + BODY_PAD + a * step - 3
                bot = content_top + BODY_PAD + b * step + LINE_H + 2
                body.append({'id': safe_id('card', used), 'type': 'inset', 'x': EDGE - 4, 'y': top, 'w': max_w + 8,
                             'h': bot - top, 'over': True})
        elif body_h - 2 >= 12:
            body.append({'id': 'info', 'type': 'inset', 'x': EDGE - 4, 'y': content_top + 1, 'w': max_w + 8,
                         'h': body_h - 2, 'over': True})
        pics = page.get('pictures')
        pic_rows = set()
        if pics and not pics.get('side'):
            pic_rows = set(range(pics.get('line', 0), pics.get('line', 0) + pics.get('rows', 0)))
        for i, l in enumerate(lines):
            if not plain(l) or i in pic_rows:
                continue
            ly = content_top + BODY_PAD + i * step + 2
            p = plain(l)
            bar, dots = BAR_RX.search(p), DOTS_RX.search(p)
            if bar and len(bar.group(1)) + len(bar.group(2)) >= 4 or dots:
                cut = (bar or dots).start()
                lead = lead_of(l, cut)
                x = EDGE
                if plain(lead):
                    body.append({'id': safe_id('line%d' % i, used), 'type': 'text', 'text': lead.rstrip(), 'x': x, 'y': ly})
                    x += measure(lead.rstrip()) + 4
                if bar:
                    fill = len(bar.group(1)) / max(1, len(bar.group(1)) + len(bar.group(2)))
                    w = max(24, min(EDGE + max_w - x, 144))
                    body.append({'id': safe_id('bar%d' % i, used), 'type': 'bar', 'x': x, 'y': ly, 'w': w, 'h': 8,
                                 'value': round(fill, 3)})
                else:
                    for j, d in enumerate(re.findall(r'\[([x ])\]', dots.group())):
                        body.append({'id': safe_id('dot%d_%d' % (i, j), used), 'type': 'bar', 'x': x + 14 * j, 'y': ly,
                                     'w': 8, 'h': 8, 'value': 1 if d == 'x' else 0})
                continue
            e = {'id': safe_id('line%d' % i, used), 'type': 'text', 'text': l, 'x': EDGE, 'y': ly, 'w': max_w}
            if groups and is_header(l):
                e['colour'] = 'trim'
            body.append(e)
        if pics:
            # drawPictures: rows [line, line+rows), full width or the right side (side permille); one picture each item
            first = pics.get('line', 0)
            top = content_top + BODY_PAD + first * step - 1
            bot = content_top + BODY_PAD + (first + pics.get('rows', 1)) * step - LINE_GAP
            pleft, pright = EDGE, body_right
            if pics.get('side'):
                pleft = pright - (pright - EDGE) * pics['side'] // 1000
            caps = pics.get('captions') or ['Your hall']
            cell = (pright - pleft - 6 * (len(caps) - 1)) // len(caps)
            for j, cap in enumerate(caps):
                ph_ = bot - top
                fw_ = cell - 12
                kids = [{'id': 'floor', 'type': 'box', 'x': 6, 'y': 14, 'w': fw_, 'h': ph_ - 20, 'radius': 2, 'colour': '#ff6b4a2c'}]
                # a sketch of the hall from above: rings along the floor, rooms at the back (the real picture is a texture)
                n = 3
                ring = max(8, min((fw_ - 8 * (n + 1)) // n, ph_ - 52, 18))
                for k in range(n):
                    kids.append({'id': 'ring%d' % k, 'type': 'box', 'x': 6 + 8 + k * (ring + 8), 'y': 14 + 6, 'w': ring, 'h': ring,
                                 'radius': ring // 2, 'colour': '#ff9a7838', 'over': True})
                kids.append({'id': 'rooms', 'type': 'box', 'x': 14, 'y': ph_ - 6 - 18, 'w': fw_ - 16, 'h': 12, 'radius': 2,
                             'colour': '#ff8a5a30', 'over': True})
                kids.append({'id': 'caption_band', 'type': 'box', 'x': 0, 'y': 0, 'w': min(cell, measure(cap) + 6), 'h': 11,
                             'colour': '#b0000000', 'over': True})
                kids.append({'id': 'caption', 'type': 'text', 'text': cap, 'x': 3, 'y': 2, 'shadow': True, 'over': True})
                body.append({'id': safe_id('picture%d' % j, used), 'type': 'box', 'x': pleft + j * (cell + 6), 'y': top,
                             'w': cell, 'h': ph_, 'radius': 3, 'colour': '#ff3a2418', 'over': True, 'children': kids})
        els = body + els
    accent = ACCENT_NAME.get(accent_letter) if accent_letter else 'default'
    panel = {'id': 'menu', 'type': 'panel', 'style': 'lm', 'w': box_w, 'h': panel_h, 'title': page['title'],
             'headerBottom': header_bottom, 'accent': accent, 'children': els}
    # breadcrumb (placeBreadcrumb): pages under a system Home with no tab row
    if not tabs:
        cr = REG.crumbs(screen, page['page'])
        if len(cr) >= 3 and REG.find(screen, page['page']):
            panel['crumbs'] = [[t, 'go'] for s, p, t in cr[:-1]] + [[cr[-1][2]]]
    facts = {'w': box_w, 'h': panel_h, 'footer': foot}
    return panel, facts


SKIN_IDS = ['saiyan_royal', 'namek_serenity', 'obsidian_gold', 'beerus_destruction', 'zeno_omni']


def swatch_for(page, row, cell):
    """NativeMenuScreen.swatchSkin: on hub|skins a locked button on the row of a do:menu_skin:<id> button previews that skin."""
    if page['screen'] != 'hub' or page['page'] != 'skins' or cell.get('action') != 'nav:none':
        return None
    for o in row:
        a = o.get('action', '')
        if o is not cell and a.startswith('do:menu_skin:'):
            sid = a[13:]
            # a custom palette token previews as its colours; the corpus's sample custom palette is Saiyan Royal's
            return (sid if sid in SKIN_IDS else 'saiyan_royal'), o.get('style') == 'confirm'
    return None


def target_rules(page, changes):
    """Target-state rules every mockup follows (each change is recorded as a finding):
    - a short first body line that names the page is a heading (UltraStyle.header), not plain text;
    - a heading said twice in a row is said once;
    - four or more green (confirm) buttons on a page are a picker, not primary actions: they use the normal style;
    - empty body lines only where pictures need the room."""
    lines = list(page.get('lines', []))
    for i, l in enumerate(lines):
        if 'Ancient Ancient Coins' in l:
            changes.append((page, 'doubled word', '"%s": CharacterServicesGuiApi.costLine adds " Ancient Coins" after '
                            'formatExactCost, which already ends in "Ancient"' % plain(amp(l))))
            lines[i] = l.replace('Ancient Ancient Coins', 'Ancient Coins')
    if len(lines) >= 2 and plain(amp(lines[0])).lower() == plain(amp(lines[1])).lower():
        changes.append((page, 'heading said twice', '"%s" then "%s": drop the first' % (plain(amp(lines[0])), plain(amp(lines[1])))))
        lines = lines[1:]
    if lines:
        first = amp(lines[0])
        codes = re.findall('§(.)', first)
        pl = plain(first)
        if not first.startswith('§6§l') and len(set(c for c in codes if c != 'l')) <= 1 and 0 < len(pl) <= 32                 and not pl.endswith('.') and ':' not in pl and not BAR_RX.search(pl) and not re.search(r'\d', pl):
            changes.append((page, 'heading', 'first line "%s" is plain text: make it UltraStyle.header' % pl))
            lines[0] = '&6&l' + pl
    if not page.get('pictures'):
        lines = [l for l in lines if l != '']
    page['lines'] = lines
    greens = [c for r in page.get('rows', []) for c in r if c.get('style') == 'confirm']
    if len(greens) >= 4:
        changes.append((page, 'green wall', '%d buttons in the confirm style (%s): a picker uses the normal style; '
                        'confirm is for the one primary action' % (len(greens), ', '.join('"%s"' % plain(amp(c['label'])) for c in greens[:4]) + (', ...' if len(greens) > 4 else ''))))
        for c in greens:
            c['style'] = 'normal'
            c['label'] = re.sub('^&a', '', c['label'])
    return page


_FONT = None


def _font():
    """Minecraft 1.20.1's bitmap font advances, read from the installed game jar (what Font.width gives)."""
    global _FONT
    if _FONT is not None:
        return _FONT
    import io
    import zipfile
    from PIL import Image
    jar = os.path.expanduser('~/curseforge/minecraft/Install/versions/1.20.1/1.20.1.jar')
    adv = {' ': 4.0}
    with zipfile.ZipFile(jar) as z:
        d = json.loads(z.read('assets/minecraft/font/include/default.json'))
        for prov in reversed(d['providers']):               # earlier providers win
            if prov.get('type') != 'bitmap':
                continue
            img = Image.open(io.BytesIO(z.read('assets/minecraft/textures/' + prov['file'].split(':')[1]))).convert('RGBA')
            rows = prov['chars']
            ch_h = img.height // len(rows)
            ch_w = img.width // max(len(r) for r in rows)
            k = prov.get('height', 8) / ch_h
            for ry, row in enumerate(rows):
                for rx, c in enumerate(row):
                    if c in ('\u0000', ' '):
                        continue
                    w = 0
                    for x in range(ch_w - 1, -1, -1):
                        if any(img.getpixel((rx * ch_w + x, ry * ch_h + y))[3] for y in range(ch_h)):
                            w = x + 1
                            break
                    adv[c] = int(0.5 + w * k) + 1
    _FONT = adv
    return adv


def measure(s):
    """Font.width: real advances, bold +1 per glyph, colour codes free."""
    f = _font()
    w, bold, i = 0.0, False, 0
    while i < len(s):
        c = s[i]
        if c == '§' and i + 1 < len(s):
            k = s[i + 1].lower()
            if k == 'l':
                bold = True
            elif k in '0123456789abcdefr':
                bold = False
            i += 2
            continue
        w += f.get(c, 6) + (1 if bold else 0)
        i += 1
    return int(-(-w // 1))


def lead_of(raw, plain_len):
    out, n, i = [], 0, 0
    while i < len(raw) and n < plain_len:
        if raw[i] == '§' and i + 1 < len(raw):
            out.append(raw[i:i + 2])
            i += 2
            continue
        out.append(raw[i])
        n += 1
        i += 1
    return ''.join(out)


def layout_fixed(page):
    """A non-fitMenu page (the config editor: a 426x240 fillScreen design box, 13 px rows) at its explicit spots."""
    pw, ph = page['panel']['w'], page['panel']['h']
    els, used = [], set()
    accent = ACCENT_NAME.get(SYSTEM_ACCENT.get(page['screen'], ''), 'default')
    inputs = {i['id']: i for i in page.get('inputs', [])}
    for r in page.get('rows', []):
        for c in r:
            h = c.get('h', 13)
            if 'action' in c:
                kind = 'locked' if c['action'] == 'nav:none' else c.get('style', 'normal')
                e = {'id': safe_id(plain(amp(c['label'])) or c['action'], used), 'type': 'button', 'text': amp(c['label']),
                     'x': c['x'], 'y': c['y'], 'w': c['w'], 'h': h, '_comment': c['action']}
                if kind != 'normal':
                    e['kind'] = kind
                els.append(e)
            elif 'input' in c:
                inp = inputs.get(c['input'], {})
                els.append({'id': safe_id('field_' + c['input'], used), 'type': 'field', 'x': c['x'], 'y': c['y'],
                            'w': c['w'], 'h': h, 'placeholder': inp.get('placeholder', ''), 'text': inp.get('value', '')})
    # the pinned footer (footer:true at the design box's bottom: Back at COL_L, Main menu at designWidth - BTN_W)
    labs = page.get('footerLabels', []) if page.get('back') else []
    fy = page.get('footerY', ph - 10 - 13)
    for j, lab in enumerate(labs):
        left = lab.startswith('Back') and len(labs) > 1
        # today: Back at COL_L (16); Main menu at 276, which the client's pinFooter pulls to 266 (EDGE inside the border).
        # target: Back at EDGE (10) like every other menu's footer
        x = (EDGE if MOCKING else COL_L) if left else pw - EDGE - BTN_W
        els.append({'id': safe_id(lab, used), 'type': 'button', 'text': lab, 'x': x, 'y': fy, 'w': BTN_W, 'h': 13,
                    '_comment': 'footer'})
    # the client's Close (closeSpot): the bottom band's right end when nothing is there
    els.append({'id': 'close', 'type': 'button', 'text': 'Close', 'x': pw - EDGE - 80, 'y': ph - CLOSE_BAND + 6, 'w': 80, 'h': 16})
    for i, l in enumerate(page.get('lines', [])):
        els.append({'id': safe_id('line%d' % i, used), 'type': 'text', 'text': amp(l), 'x': EDGE,
                    'y': page.get('bodyTop', 42) + 4 + i * 13})
    return {'id': 'menu', 'type': 'panel', 'style': 'lm', 'fit': 'fill', 'margin': 0, 'pad': 8, 'w': pw, 'h': ph,
            'title': page['title'], 'headerBottom': page.get('headerBottom', 42), 'accent': accent, 'children': els}, {}


def apply_mock(page, changes):
    """The mockup's own page: the real page with its "mock" changes (target state / fixes) laid over it."""
    m = page.get('mock')
    if not m:
        return page
    p = copy.deepcopy(page)
    for k, v in m.items():
        if k == 'labels':                     # {"action id": "new label"}
            for r in p['rows']:
                for c in r:
                    if c.get('action') in v:
                        changes.append((page, 'label', '%s: "%s" -> "%s"' % (c['action'], c['label'], v[c['action']])))
                        c['label'] = v[c['action']]
        elif k == 'relabel':                  # {"old label": "new label"} when one action id has several buttons
            for r in p['rows']:
                for c in r:
                    if c.get('label') in v:
                        changes.append((page, 'label', '%s: "%s" -> "%s"' % (c.get('action'), c['label'], v[c['label']])))
                        c['label'] = v[c['label']]
        elif k == 'widen':                    # ["label", ...]: that button gets its own row across both columns (buttonWide)
            rows = []
            for r in p['rows']:
                keep, wide = [], []
                for c in r:
                    (wide if c.get('label') in v else keep).append(c)
                if keep:
                    rows.append(keep)
                for c in wide:
                    changes.append((page, 'wide button', '%s "%s": buttonWide (x 16, w 306) on its own row' % (c.get('action'), c['label'])))
                    rows.append([dict(c, x=16, w=306)])
            p['rows'] = rows
        elif k == 'styles':
            for r in p['rows']:
                for c in r:
                    if c.get('action') in v:
                        changes.append((page, 'style', '%s "%s": %s -> %s' % (c['action'], c['label'], c.get('style'), v[c['action']])))
                        c['style'] = v[c['action']]
        elif k == 'why':
            continue
        else:
            changes.append((page, k, 'replaced (%s)' % m.get('why', 'see the page notes')))
            p[k] = v
    return p


def main():
    global REG, MOCKING
    REG = Registry()
    pages_dir = os.path.join(ROOT, 'pages')
    specs_dir = os.path.join(ROOT, 'tools', 'specs')
    os.makedirs(specs_dir, exist_ok=True)
    findings, changes, includes, real_screens = [], [], [], []
    real_index = {}
    for name in sorted(os.listdir(pages_dir)):
        if not name.endswith('.json'):
            continue
        doc = json.load(open(os.path.join(pages_dir, name), encoding='utf-8'))
        screens = []
        for page in doc['pages']:
            if page.get('skip'):
                continue
            # the REAL layout, for the findings
            real_f = []
            if page.get('fit', True):
                real_panel, facts = layout_menu(page, real_f)
            else:
                real_panel, facts = layout_fixed(page)
            real_index[page['id']] = {'panel': real_panel, 'facts': facts}
            findings += real_f
            mock = target_rules(copy.deepcopy(apply_mock(page, changes)), changes)
            junk = []
            MOCKING = True
            panel = layout_menu(mock, junk)[0] if mock.get('fit', True) else layout_fixed(mock)[0]
            MOCKING = False
            if mock.get('fit', True):
                # target: NativeMenuFit never shrinks a menu under 0.75x (16 px rows stay 12 px); past that it scrolls
                panel['minScale'] = TARGET_MIN_SCALE
                panel['scroll'] = True
            scr = {'id': page['id'], 'background': 'skin', 'elements': [panel]}
            if page.get('fit', True):
                real_panel['scroll'] = True             # today: floor 0.5x, then the whole menu scrolls
            real_screens.append({'id': page['id'], 'background': 'skin', 'elements': [real_panel]})
            for k in ('skin', 'look'):
                if page.get(k):
                    scr[k] = page[k]
            src = page.get('source', '')
            scr['_comment'] = '%s|%s  %s%s' % (page['screen'], page['page'], src, '  (4.7.80 target)' if page.get('target') else '')
            screens.append(scr)
            for v in page.get('variants', []):          # the same page in another skin / Lite
                s2 = copy.deepcopy(scr)
                s2['id'] = page['id'] + '_' + v.get('suffix', v.get('skin', v.get('look', 'v')))
                for k in ('skin', 'look'):
                    if v.get(k):
                        s2[k] = v[k]
                screens.append(s2)
        out = os.path.join(specs_dir, name)
        with open(out, 'w', encoding='utf-8') as f:
            json.dump({'_comment': 'GENERATED by gen/build_corpus.py from pages/%s - edit the page, not this file.' % name,
                       'screens': screens}, f, ensure_ascii=False, indent=1)
        includes.append('specs/' + name)
    guikit = {
        '_comment': 'LM GuiKit corpus. Real window sizes, GUI scale 2, 3 and Auto; Saiyan Royal by default. Run: '
                    'mod-toolkit\\scripts\\guikit.ps1 -Project <this folder>. Screens live in specs/ (generated).',
        'sizes': ['1920x1080', '1366x768', '854x480'], 'scales': [2, 3, 'auto'],
        'skin': 'saiyan_royal', 'look': 'full', 'format': 'jpg', 'sheets': False, 'keep': ['1920x1080@2'],
        'include': includes, 'screens': []}
    with open(os.path.join(ROOT, 'tools', 'guikit.json'), 'w', encoding='utf-8') as f:
        json.dump(guikit, f, ensure_ascii=False, indent=1)
    real = dict(guikit, include=[], screens=real_screens, keep=['320x240@1'])
    real['_comment'] = ('GENERATED: every page exactly as LM lays it out TODAY (NativeMenuFit floor 0.5x, no mockup changes). '
                        'Run with -Spec tools/real/guikit-real.json; its failures go into findings/layout-findings.md.')
    os.makedirs(os.path.join(ROOT, 'tools', 'real'), exist_ok=True)
    with open(os.path.join(ROOT, 'tools', 'real', 'guikit-real.json'), 'w', encoding='utf-8') as f:
        json.dump(real, f, ensure_ascii=False, indent=1)
    for page, kind, detail in GLOBAL_CHANGES:
        changes.append((page, kind, detail))
    for k, v in MOCK_TITLES.items():
        s, pg = k.split('|')
        changes.append(({'id': '(registry)', 'source': 'PageRegistry.java %s|%s' % (s, pg)}, 'page title',
                        '"%s" -> "%s"' % (REG.title(s, pg), v)))
    write_findings(findings, changes)
    print('pages -> specs: %d files; %d real-layout findings; %d mockup changes' % (len(includes), len(findings), len(changes)))


def write_findings(findings, changes):
    lines = ['# What the real LM layout does wrong', '',
             'GENERATED by gen/build_corpus.py. "Real layout" = what UltraScreenSpec.toMenuPacket sends today for the page',
             'in pages/*.json, measured the way the server measures it. "Mockup change" = what the mockup shows instead',
             '(a fix or the 4.7.80 target); apply it to the Java.', '']
    if findings:
        lines += ['## Real layout', '', '| Page | Source | Problem | Detail |', '|---|---|---|---|']
        for page, kind, detail in findings:
            lines.append('| %s | %s | %s | %s |' % (page['id'], page.get('source', '').replace('|', '/'), kind, detail.replace('|', '/')))
        lines.append('')
    rep = os.path.join(ROOT, 'build', 'real-report.txt')
    if os.path.isfile(rep):
        fails, small = [], {}
        for l in open(rep, encoding='utf-8'):
            l = l.rstrip()
            m = re.match(r'warn ([a-z0-9_]+)/.*under 12 px to click', l)
            if m:
                small[m.group(1)] = small.get(m.group(1), 0) + 1
            elif l.startswith('FAIL') or l.startswith('warn'):
                fails.append(l)
        lines += ['## GuiKit on the real layout (tools/real/guikit-real.json)', '']
        lines += ['- ' + f.replace('|', '/') for f in fails] or ['- no errors']
        if small:
            lines += ['', '%d pages have click targets under 12 px at some window size, because NativeMenuFit shrinks a '
                      'tall menu down to 0.5x before it scrolls (16 px rows become 8 px): %s' % (len(small), ', '.join(sorted(small)))]
        lines.append('')
    if changes:
        lines += ['## Mockup changes (apply to the Java)', '', '| Page | Source | Change | Detail |', '|---|---|---|---|']
        for page, kind, detail in changes:
            lines.append('| %s | %s | %s | %s |' % (page['id'], page.get('source', '').replace('|', '/'), kind, detail.replace('|', '/')))
        lines.append('')
    with open(os.path.join(ROOT, 'findings', 'layout-findings.md'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))


if __name__ == '__main__':
    sys.exit(main())
