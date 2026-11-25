"""
LM GuiKit audit from a REAL crawl: the menu packets the LM server actually sent (the uiprobe crawl of a release check)
-> GuiKit layout specs at the packets' own geometry -> findings/real-crawl-findings.md.

Unlike build_corpus.py (which re-lays out hand-written pages/*.json), nothing here is re-flowed: the panel size, every
button's x/y/w/h/style/pad, the body lines, the inputs, the player model, the pictures and the breadcrumb are taken
from the packet exactly as UltraScreenSpec.toMenuPacket sent them. Only what the CLIENT adds is ported, from
client/NativeMenuScreen.java:
  - body lines at bodyTop + bodyPad + i * (lineHeight + lineGap), cut to bodyRight() - EDGE (drawBody / textCache.fit);
  - the info box, or one card per header run when two or more lines start bold (lineGroups; a Notice line is not one);
  - text bars and progress dots (drawSkinLine), pictures (drawPictures), the paged book's Previous / Next buttons;
  - footer:true buttons pinned into the bottom band (pinFooter) and the client's Close button (closeSpot / free);
  - the Menu skins swatch beside a skin's name (swatchSkin).
The window fit is net/gui/NativeMenuFit: native size, shrunk to the window less the layout margin, never under 0.5x;
a fillScreen page (the config editor) scales its 426x240 design box to the window. The spec has no "scroll", so a page
still too tall at 0.5x is a GuiKit ERROR ("taller than the window even at 0.5x").

  python gen/from_crawl.py [uiprobe-packets.jsonl]            spec -> tools/real/guikit-crawl.json; findings from the
                                                              last GuiKit report (build/crawl-report.txt) if there is one
  python gen/from_crawl.py [uiprobe-packets.jsonl] --guikit   the same, then runs GuiKit on the spec and writes the
                                                              findings with its report
Run from tools/lm-gui-corpus with PYTHONUTF8=1 on Windows. Default packets: %USERPROFILE%/DMZRig/results/
release-4.7.83-run1/matrix/full/uiprobe-packets.jsonl.
"""
import json
import math
import os
import re
import shutil
import subprocess
import sys
from collections import Counter, OrderedDict, defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
from build_corpus import (ACCENT_NAME, BAR_RX, DOTS_RX, HEADER_RX, SKIN_IDS, lead_of, measure, plain,  # noqa: E402
                          safe_id)

DEFAULT_PACKETS = os.path.expanduser('~/DMZRig/results/release-4.7.83-run1/matrix/full/uiprobe-packets.jsonl')
SPEC = os.path.join(ROOT, 'tools', 'real', 'guikit-crawl.json')
REPORT = os.path.join(ROOT, 'build', 'crawl-report.txt')
FINDINGS = os.path.join(ROOT, 'findings', 'real-crawl-findings.md')
GUIKIT = os.path.normpath(os.path.join(ROOT, '..', '..', '..', 'mod-toolkit', 'scripts', 'guikit.ps1'))

EDGE, CLOSE_BAND, MIN_SCALE = 10, 28, 0.5
WHEEL_MIN = 40                                    # gui/ultra/ColorWheel.MIN_SIZE
SIZES = [(1920, 1080), (1366, 768), (854, 480)]
WANTED = [2, 3, 0]                                # 0 = Auto
DECLUTTER_AT = ((1366, 768), 3)
DECLUTTER_BELOW = 0.75


# ---------------------------------------------------------------- window / fit (GuiKit.scale + NativeMenuFit.place)
def gui_scale(w, h, wanted):
    """Minecraft's GUI scale: the largest that keeps the GUI at least 320x240, capped by the wanted one (0 = Auto)."""
    top = 1
    while w // (top + 1) >= 320 and h // (top + 1) >= 240:
        top += 1
    return top if wanted == 0 else max(1, min(wanted, top))


def configs():
    """[(label, W, H, gui scale, gui w, gui h)] the distinct window x scale pairs GuiKit checks."""
    out, seen = [], set()
    for w, h in SIZES:
        for wanted in WANTED:
            s = gui_scale(w, h, wanted)
            if (w, h, s) in seen:
                continue
            seen.add((w, h, s))
            auto = s == gui_scale(w, h, 0)
            out.append(('%dx%d@%d%s' % (w, h, s, ' (auto)' if auto else ''), w, h, s,
                        math.ceil(w / s), math.ceil(h / s)))
    return out


def fit(p, gw, gh):
    """NativeMenuFit.place: (scale, too tall even at the floor)."""
    m = p['margin']
    aw, ah = max(1, gw - 2 * m), max(1, gh - 2 * m)
    if p['fill']:
        return min(aw / p['w'], ah / p['h']), False
    k = max(MIN_SCALE, min(1.0, aw / p['w'], ah / p['h']))
    return k, p['h'] * k > ah + 0.01


# ---------------------------------------------------------------- packet -> GuiKit spec
def header_line(l):
    """NativeMenuScreen.headerLine: starts bold (or the header mark + bold) and is not a Notice line."""
    if not l or not HEADER_RX.match(l):
        return False
    t = l
    while len(t) >= 2 and t[0] == '§':
        t = t[2:]
    return not t.startswith('Notice ')


def line_groups(lines):
    if sum(1 for l in lines if header_line(l)) < 2:
        return None
    out, start = [], -1
    for i, l in enumerate(lines):
        if header_line(l):
            if start >= 0 and i - 1 >= start:
                out.append((start, i - 1))
            start = i + 1
        elif start < 0:
            start = i
    if 0 <= start <= len(lines) - 1:
        out.append((start, len(lines) - 1))
    return out


def semantic(bid):
    """'@hub:do:menu_skin:x' -> 'do:menu_skin:x'."""
    return bid.split(':', 2)[2] if bid.startswith('@') and bid.count(':') >= 2 else bid


def overlaps(a, b):
    return a[0] < b[0] + b[2] and b[0] < a[0] + a[2] and a[1] < b[1] + b[3] and b[1] < a[1] + a[3]


def to_spec(d, sid):
    """One packet -> (GuiKit screen, facts)."""
    lay = d.get('layout') or {}
    fill = bool(lay.get('fillScreen'))
    pw = lay.get('designWidth') if fill and lay.get('designWidth') else lay.get('panelWidth', d['panelW'])
    ph = lay.get('designHeight') if fill and lay.get('designHeight') else lay.get('panelHeight', d['panelH'])
    margin = max(0, lay.get('margin', 10))
    tabs_under = bool(lay.get('tabsUnderTitle'))
    header_bottom = max(12, lay.get('headerBottom', 28 if tabs_under else 42))
    body_top = max(0, lay.get('bodyTop', header_bottom))
    body_pad = max(0, lay.get('bodyPad', 6))
    line_h = max(8, lay.get('lineHeight', 11))
    line_gap = max(0, lay.get('lineGap', 2))
    step = line_h + line_gap
    model = lay.get('playerModel')
    pics = lay.get('pictures')
    lines = list(d.get('lines') or [])
    body_right = max(EDGE + 40, model.get('x', pw) - 6) if model else pw - EDGE
    max_w = body_right - EDGE
    used, els, body = set(), [], []
    taken = []                                   # NativeMenuScreen.free(): buttons, fields, wheels, books, the model

    # server buttons (footer:true pinned into the bottom band)
    server = []
    for b in d.get('buttons') or []:
        x, y, w, h = b.get('x', EDGE), b.get('y', 0), max(1, b.get('w', 80)), max(8, b.get('h', 16))
        if b.get('footer'):
            if y + h > ph - EDGE:
                y = ph - CLOSE_BAND + 6
            if x + w > pw - EDGE:
                x = max(EDGE, pw - EDGE - w)
            x = max(EDGE, x)
        server.append((b, x, y, w, h))
        taken.append((x, y, w, h))
    skins_page = d['screen'] == 'hub' and d['page'] == 'skins'
    for b, x, y, w, h in server:
        sem = semantic(b['id'])
        style = (b.get('style') or 'normal').lower()
        inert = sem == 'nav:none'
        label = b.get('label') or b['id']
        if inert and skins_page:
            partner = next((semantic(o['id']) for o, ox, oy, _, _ in server
                            if o is not b and oy == y and semantic(o['id']).startswith('do:menu_skin:')), None)
            if partner:                          # swatchSkin: the inert label beside a skin is its live preview
                sk = partner[len('do:menu_skin:'):]
                els.append({'id': safe_id('swatch_' + sk.split('.')[0], used), 'type': 'swatch',
                            'skin': sk if sk in SKIN_IDS else 'saiyan_royal', 'x': x, 'y': y, 'w': w, 'h': h,
                            'text': plain(label), '_comment': b['id']})
                continue
        e = {'id': safe_id(plain(label) or sem, used), 'type': 'button', 'text': label, 'x': x, 'y': y, 'w': w, 'h': h,
             '_comment': b['id']}
        if b.get('pad') is not None and b.get('pad') != 8:
            e['pad'] = b['pad']
        kind = 'dim' if inert else style
        if kind in ('confirm', 'danger', 'dim', 'locked'):
            e['kind'] = kind
        if ':tab:' in b['id'] and style == 'normal':
            e['active'] = True
            e.pop('kind', None)
        els.append(e)

    # inputs
    books = []
    for inp in d.get('inputs') or []:
        typ = (inp.get('type') or 'text').lower()
        x, y = inp.get('x', EDGE), inp.get('y', body_top)
        w = max(40, inp.get('w', pw - 2 * EDGE))
        if typ == 'paged_book':
            h = max(48, inp.get('h', 120))
            pages = inp.get('pages') or []
            page = min(max(0, len(pages) - 1), max(0, inp.get('page', 0)))
            pg = pages[page] if pages else {'title': '', 'lines': []}
            nav_w = max(80, (w - 12) // 3)
            kids = []
            if page > 0:
                kids.append({'id': 'prev', 'type': 'button', 'text': 'Previous page', 'x': 4, 'y': h - 20, 'w': nav_w, 'h': 16})
                taken.append((x + 4, y + h - 20, nav_w, 16))
            if page < len(pages) - 1:
                kids.append({'id': 'next', 'type': 'button', 'text': 'Next page', 'x': w - 4 - nav_w, 'y': h - 20,
                             'w': nav_w, 'h': 16})
                taken.append((x + w - 4 - nav_w, y + h - 20, nav_w, 16))
            els.append({'id': safe_id('book_' + inp['id'], used), 'type': 'book', 'x': x, 'y': y, 'w': w, 'h': h,
                        'title': pg.get('title', ''), 'value': max(1, len(pages)), 'text': '\n'.join(pg.get('lines', [])),
                        'children': kids, '_comment': inp['id']})
            books.append(inp)
        elif typ == 'text':
            h = max(12, inp.get('h', 16))
            e = {'id': safe_id('field_' + inp['id'].split(':')[-1], used), 'type': 'field', 'x': x, 'y': y, 'w': w, 'h': h,
                 'placeholder': inp.get('placeholder', ''), '_comment': inp['id']}
            if inp.get('value'):
                e['text'] = inp['value']
            els.append(e)
        elif typ == 'color_wheel':
            h = max(WHEEL_MIN, inp.get('h', WHEEL_MIN))
            els.append({'id': safe_id('wheel_' + inp['id'].split(':')[-1], used), 'type': 'colorwheel', 'x': x, 'y': y,
                        'w': w, 'h': h, 'value': 0.0, '_comment': inp['id']})
        else:
            continue
        taken.append((x, y, w, els[-1]['h']))
    if model:
        mbox = (model.get('x', 0), model.get('y', 0), model.get('width', 64), model.get('height', 96))
        taken.append(mbox)
        els.append({'id': 'model', 'type': 'figure', 'x': mbox[0], 'y': mbox[1], 'w': mbox[2], 'h': mbox[3]})

    def free(x, y, w, h):
        return not any(overlaps((x, y, w, h), t) for t in taken)

    # the client's Close (closeSpot)
    close = None
    for b, x, y, w, h in server:
        if y >= ph - CLOSE_BAND and free(EDGE, y, 80, h):
            close = (EDGE, y, h)
            break
    if close is None:
        spots = [(pw - EDGE - 80, ph - CLOSE_BAND + 6, 16), (EDGE, ph - CLOSE_BAND + 6, 16),
                 (pw - EDGE - 80, 4, max(12, min(16, header_bottom - 8)))]
        close = next((s for s in spots if free(s[0], s[1], 80, s[2])), spots[2])
    els.append({'id': 'close', 'type': 'button', 'text': 'Close', 'x': close[0], 'y': close[1], 'w': 80, 'h': close[2],
                '_comment': 'client Close'})

    # body (drawInfoBox / drawBody / drawSkinLine / drawPictures)
    if lines:
        natural = body_pad * 2 + len(lines) * step
        groups = line_groups(lines)
        if free(EDGE - 4, body_top + 1, max_w + 8, natural - 2) and natural - 2 >= 12:
            if groups:
                for a, b in groups:
                    top = body_top + body_pad + a * step - 3
                    bot = body_top + body_pad + b * step + line_h + 2
                    body.append({'id': safe_id('card', used), 'type': 'inset', 'x': EDGE - 4, 'y': top, 'w': max_w + 8,
                                 'h': bot - top, 'over': True})
            else:
                body.append({'id': 'info', 'type': 'inset', 'x': EDGE - 4, 'y': body_top + 1, 'w': max_w + 8,
                             'h': natural - 2, 'over': True})
        pic_rows = set()
        if pics and not pics.get('side'):
            pic_rows = set(range(pics.get('line', 0), pics.get('line', 0) + pics.get('rows', 0)))
        for i, l in enumerate(lines):
            if not plain(l) or i in pic_rows:
                continue
            ly = round(body_top + body_pad + i * step + (line_h - 9) / 2 + 1)
            p = plain(l)
            bar, dots = BAR_RX.search(p), DOTS_RX.search(p)
            if bar and len(bar.group(1)) + len(bar.group(2)) >= 4 or dots:
                lead = lead_of(l, (bar or dots).start())
                x = EDGE
                if plain(lead):
                    body.append({'id': safe_id('line%d' % i, used), 'type': 'text', 'text': lead.rstrip(), 'x': x, 'y': ly})
                    x += measure(lead.rstrip()) + 4
                if bar:
                    share = len(bar.group(1)) / max(1, len(bar.group(1)) + len(bar.group(2)))
                    body.append({'id': safe_id('bar%d' % i, used), 'type': 'bar', 'x': x, 'y': ly,
                                 'w': max(24, min(EDGE + max_w - x, 144)), 'h': 8, 'value': round(share, 3)})
                else:
                    for j, dd in enumerate(re.findall(r'\[([x ])\]', dots.group())):
                        body.append({'id': safe_id('dot%d_%d' % (i, j), used), 'type': 'bar', 'x': x + 14 * j, 'y': ly,
                                     'w': 8, 'h': 8, 'value': 1 if dd == 'x' else 0})
                continue
            e = {'id': safe_id('line%d' % i, used), 'type': 'text', 'text': l, 'x': EDGE, 'y': ly, 'w': max_w}
            if groups and header_line(l):
                e['colour'] = 'trim'
            body.append(e)
        if pics and pics.get('items'):
            first, rows = pics.get('line', 0), pics.get('rows', 0)
            top = body_top + body_pad + first * step - 1
            bot = body_top + body_pad + (first + rows) * step - line_gap
            left, right = EDGE, body_right
            if pics.get('side'):
                left = right - (right - EDGE) * pics['side'] // 1000
            n = max(1, len(pics['items']))
            cell = (right - left - 6 * (n - 1)) // n
            for j, it in enumerate(pics['items']):
                kids = []
                if it.get('c'):
                    kids = [{'id': 'caption', 'type': 'text', 'text': it['c'], 'x': 3, 'y': 2, 'shadow': True, 'over': True}]
                body.append({'id': safe_id('picture%d' % j, used), 'type': 'box', 'x': left + j * (cell + 6), 'y': top,
                             'w': cell, 'h': bot - top, 'radius': 3, 'colour': '#ff3a2418', 'over': True, 'children': kids})
    panel = {'id': 'menu', 'type': 'panel', 'style': 'lm', 'w': pw, 'h': ph, 'title': d.get('title') or '',
             'headerBottom': header_bottom, 'margin': margin, 'minScale': MIN_SCALE, 'children': body + els}
    acc = lay.get('accent')
    if acc and ACCENT_NAME.get(acc[-1]):
        panel['accent'] = ACCENT_NAME[acc[-1]]
    elif lay.get('palette'):
        panel['accent'] = lay['palette']
    if fill:
        panel.update({'fit': 'fill', 'margin': 0, 'pad': 8})
        panel.pop('minScale')
    crumbs = lay.get('breadcrumb') or []
    if crumbs:
        panel['crumbs'] = [[c[0], 'go'] if (len(c) > 1 and c[1]) else [c[0]] for c in crumbs]
    screen = {'id': sid, 'background': 'skin', 'elements': [panel],
              '_comment': '%s|%s tab %s  (packet %d: %s)' % (d['screen'], d['page'], d.get('tab'), d['i'], d.get('from', ''))}
    facts = {'w': pw, 'h': ph, 'margin': margin, 'fill': fill}
    return screen, facts


# ---------------------------------------------------------------- page facts
def page_facts(d):
    buttons = d.get('buttons') or []
    ids = Counter(b['id'] for b in buttons)
    labs = Counter(plain(b.get('label') or '') for b in buttons if plain(b.get('label') or ''))
    return {
        'buttons': len(buttons),
        'lines': len([l for l in d.get('lines') or [] if plain(l)]),
        'raw_lines': len(d.get('lines') or []),
        'inputs': len(d.get('inputs') or []),
        'dup_ids': sorted(k for k, v in ids.items() if v > 1),
        'dup_labels': sorted('%s x%d' % (k, v) for k, v in labs.items() if v > 1),
        'small_rows': sorted(set(b.get('h', 16) for b in buttons)),
    }


def screen_id(d, used):
    base = re.sub('[^a-z0-9]+', '_', ('%s_%s_%s' % (d['screen'], d['page'], d.get('tab') or '')).lower()).strip('_')[:70]
    out, n = base, 1
    while out in used:
        n += 1
        out = '%s_%d' % (base, n)
    used.add(out)
    return out


# ---------------------------------------------------------------- GuiKit report
SIZE_RX = re.compile(r'(\d+x\d+) scale (\d+)')


def read_report(path):
    """{screen id: [(level, element, message, [config labels] or 'N sizes')]}"""
    out = defaultdict(list)
    if not os.path.isfile(path):
        return None
    for raw in open(path, encoding='utf-8'):
        raw = raw.rstrip()
        m = re.match(r'^(FAIL|warn) (\S+?): (.*) \(([^()]*)\)$', raw)
        if not m:
            continue
        level, name, msg, where = m.groups()
        sid = name.split('/')[0]
        el = name[len(sid) + 1:]
        sizes = ['%s@%s' % s for s in SIZE_RX.findall(where)] or [where]
        out[sid].append(('ERROR' if level == 'FAIL' else 'warn', el, msg, sizes))
    return out


def run_guikit():
    if not os.path.isfile(GUIKIT):
        raise SystemExit('no GuiKit at ' + GUIKIT)
    preview = os.path.join(ROOT, 'build', 'preview', 'guikit')
    shutil.rmtree(preview, ignore_errors=True)
    r = subprocess.run(['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', GUIKIT, '-Project', ROOT,
                        '-Spec', SPEC], cwd=ROOT)
    rep = os.path.join(preview, 'report.txt')
    if not os.path.isfile(rep):
        raise SystemExit('GuiKit wrote no report (exit %d)' % r.returncode)
    os.makedirs(os.path.dirname(REPORT), exist_ok=True)
    shutil.copyfile(rep, REPORT)
    print('GuiKit exit %d; report -> %s' % (r.returncode, REPORT))


# ---------------------------------------------------------------- findings
def esc(s):
    return str(s).replace('|', '/').replace('\n', ' ')


def write_findings(pages, report, src, total_packets):
    cfgs = configs()
    cfg_short = [c[0].split(' ')[0] for c in cfgs]
    dc_label = '%dx%d@%d' % (DECLUTTER_AT[0][0], DECLUTTER_AT[0][1], DECLUTTER_AT[1])
    for p in pages:
        f = p['fit']
        p['scales'] = OrderedDict()
        p['too_tall'] = []
        for label, w, h, s, gw, gh in cfgs:
            k, tall = fit(f, gw, gh)
            short = label.split(' ')[0]
            p['scales'][short] = k
            if tall:
                p['too_tall'].append(short)
        # click targets under 12 px after the fit (GuiKit's rule, computed per window size)
        small = OrderedDict()
        for short, k in p['scales'].items():
            n = sum(1 for b in p['packet'].get('buttons') or [] if min(b.get('w', 80), b.get('h', 16)) * k < 12)
            n += 1 if 16 * k < 12 else 0          # the client's Close
            n += sum(1 for i in p['packet'].get('inputs') or [] if i.get('type') == 'text' and max(12, i.get('h', 16)) * k < 12)
            if n:
                small[short] = n
        p['small'] = small
        items = (report or {}).get(p['sid'], [])
        # a page too tall even at 0.5x has no "scroll" in the spec: GuiKit then also calls each child below the window
        # "off the screen"; those are folded into the one "taller than the window" error
        tall_sizes = set()
        for lv, el, msg, sz in items:
            if 'taller than the window' in msg:
                tall_sizes |= set(sz)
        kept, folded = [], 0
        for it in items:
            if it[0] == 'ERROR' and it[2] == 'off the screen' and set(it[3]) <= tall_sizes:
                folded += 1
                continue
            kept.append(it)
        p['folded'] = folded
        # GuiKit warns once per control and size for "under 12 px to click": one summary per page instead
        tiny = [i for i in kept if i[0] == 'warn' and 'under 12 px to click' in i[2]]
        p['tiny_controls'] = sorted(set(i[1] for i in tiny))
        p['tiny_min'] = min([float(m.group(1)) for i in tiny for m in [re.search(r'\(([\d.]+) px', i[2])] if m] or [0])
        kept = [i for i in kept if i not in tiny]
        p['issues'] = kept
        p['errors'] = [i for i in kept if i[0] == 'ERROR']
        p['warns'] = [i for i in kept if i[0] == 'warn']
        p['density'] = p['facts']['buttons'] + p['facts']['lines']

    L = ['# LM menus from the real crawl: GuiKit findings', '',
         'GENERATED by `gen/from_crawl.py` from %s (%d packets, %d distinct pages by screen|page|tab; per page the variant '
         'with the most buttons). Every panel, button, line and input is at the geometry the server sent (fitMenu reflow '
         'done); the client parts (body text, info box / cards, bars, Close, book buttons, footer pin) follow '
         '`client/NativeMenuScreen`. Fit: `NativeMenuFit` (native size, shrunk to the window less the margin, floor 0.5x; '
         'config editor: fillScreen design box). No scroll in the spec.' % (src.replace('\\', '/'), total_packets, len(pages)), '',
         'Window sizes (GUI scale; Auto is what Minecraft picks): ' + ', '.join(c[0] for c in cfgs) + '.', '']
    if report is None:
        L += ['**No GuiKit report yet**: run `python gen/from_crawl.py --guikit`. Only the computed facts are below.', '']

    # summary
    systems = OrderedDict()
    for p in sorted(pages, key=lambda p: (p['packet']['screen'], p['key'])):
        systems.setdefault(p['packet']['screen'], []).append(p)
    L += ['## Summary', '',
          '| System | Pages | Pages with errors | Errors | Warnings | Pages with click targets under 12 px | Pages under %.2fx at %s |'
          % (DECLUTTER_BELOW, dc_label), '|---|---|---|---|---|---|---|']
    tot = [0, 0, 0, 0, 0, 0]
    for sysname, ps in systems.items():
        row = [len(ps), sum(1 for p in ps if p['errors']), sum(len(p['errors']) for p in ps), sum(len(p['warns']) for p in ps),
               sum(1 for p in ps if p['small']), sum(1 for p in ps if p['scales'][dc_label] < DECLUTTER_BELOW)]
        tot = [a + b for a, b in zip(tot, row)]
        L.append('| %s | %s |' % (sysname, ' | '.join(str(x) for x in row)))
    L.append('| **all** | %s |' % ' | '.join('**%d**' % x for x in tot))
    L += ['', 'Errors and warnings count distinct GuiKit messages (one message may hold several window sizes). The "under 12 '
          'px to click" warnings are not in the Warnings column; they are the click-target column (pages, listed below).', '']

    small_pages = [p for p in pages if p['small']]
    L += ['### Pages with click targets under 12 px after the fit shrink (%d)' % len(small_pages), '',
          '| Page | Height | Window sizes (targets under 12 px) |', '|---|---|---|']
    for p in sorted(small_pages, key=lambda p: -p['fit']['h']):
        L.append('| %s | %d | %s |' % (esc(p['key']), p['fit']['h'],
                                       ', '.join('%s (%d, %.2fx)' % (k, v, p['scales'][k]) for k, v in p['small'].items())))
    L.append('')

    dc = sorted([p for p in pages if p['scales'][dc_label] < DECLUTTER_BELOW], key=lambda p: p['scales'][dc_label])
    L += ['### De-clutter candidates: under %.2fx at %s (%d)' % (DECLUTTER_BELOW, dc_label, len(dc)), '',
          'Pages tall enough that `NativeMenuFit` shrinks them under %.2fx at 1366x768, GUI 3 (a 16 px row is then under 12 px).'
          % DECLUTTER_BELOW, '',
          '| Page | Title | Size | Scale at %s | Buttons | Lines | Density |' % dc_label, '|---|---|---|---|---|---|---|']
    for p in dc:
        L.append('| %s | %s | %dx%d | %.2fx%s | %d | %d | %d |' % (
            esc(p['key']), esc(p['packet']['title']), p['fit']['w'], p['fit']['h'], p['scales'][dc_label],
            ' (still too tall)' if dc_label in p['too_tall'] else '', p['facts']['buttons'], p['facts']['lines'], p['density']))
    L.append('')

    # many pages are one builder with another argument (race_confirm:<race>:<n>, bodyparts:<n>:<slot>, edit:<module>:...):
    # the template lists keep the worst page of each builder so one builder does not fill the top 25
    worst = OrderedDict()
    for p in sorted(pages, key=lambda p: (-p['density'], -p['fit']['h'])):
        t = template(p)
        worst.setdefault(t, [p, 0])[1] += 1
    L += ['### The 25 most cluttered page templates (worst page of each builder; density = buttons + body lines)', '',
          '| # | Template | Pages | Worst page | Density | Buttons | Lines | Height | ' + ' | '.join(cfg_short) + ' |',
          '|---|---|---|---|---|---|---|---|' + '---|' * len(cfg_short)]
    for n, (t, (p, cnt)) in enumerate(list(worst.items())[:25], 1):
        L.append('| %d | %s | %d | %s | %d | %d | %d | %d | %s |' % (n, esc(t), cnt, esc(p['key']), p['density'],
                 p['facts']['buttons'], p['facts']['lines'], p['fit']['h'], ' | '.join(scale_cell(p, c) for c in cfg_short)))
    L.append('')
    tallest = OrderedDict()
    for p in sorted(pages, key=lambda p: (-p['fit']['h'], -p['density'])):
        tallest.setdefault(template(p), [p, 0])[1] += 1
    L += ['### The 25 tallest page templates (tallest page of each builder)', '',
          '| # | Template | Pages | Tallest page | Height | Density | ' + ' | '.join(cfg_short) + ' |',
          '|---|---|---|---|---|---|' + '---|' * len(cfg_short)]
    for n, (t, (p, cnt)) in enumerate(list(tallest.items())[:25], 1):
        L.append('| %d | %s | %d | %s | %d | %d | %s |' % (n, esc(t), cnt, esc(p['key']), p['fit']['h'], p['density'],
                                                           ' | '.join(scale_cell(p, c) for c in cfg_short)))
    L.append('')

    top = sorted(pages, key=lambda p: (-p['density'], -p['fit']['h']))[:25]
    L += ['### The 25 most cluttered pages (density = buttons + body lines)', '',
          '| # | Page | Density | Buttons | Lines | Height | ' + ' | '.join(cfg_short) + ' |',
          '|---|---|---|---|---|---|' + '---|' * len(cfg_short)]
    for n, p in enumerate(top, 1):
        L.append('| %d | %s | %d | %d | %d | %d | %s |' % (n, esc(p['key']), p['density'], p['facts']['buttons'],
                 p['facts']['lines'], p['fit']['h'], ' | '.join(scale_cell(p, c) for c in cfg_short)))
    L.append('')
    tall = sorted(pages, key=lambda p: -p['fit']['h'])[:25]
    L += ['### The 25 tallest pages', '',
          '| # | Page | Height | Density | ' + ' | '.join(cfg_short) + ' |', '|---|---|---|---|' + '---|' * len(cfg_short)]
    for n, p in enumerate(tall, 1):
        L.append('| %d | %s | %d | %d | %s |' % (n, esc(p['key']), p['fit']['h'], p['density'],
                                                 ' | '.join(scale_cell(p, c) for c in cfg_short)))
    L.append('')

    dup = [p for p in pages if p['facts']['dup_ids'] or p['facts']['dup_labels']]
    L += ['### Duplicated buttons (same id or label twice on a page): %d pages' % len(dup), '',
          '| Page | Same id | Same label |', '|---|---|---|']
    for p in dup:
        L.append('| %s | %s | %s |' % (esc(p['key']), esc(', '.join(p['facts']['dup_ids']) or '-'),
                                       esc(', '.join(p['facts']['dup_labels']) or '-')))
    L.append('')

    # every error, flat
    allerr = [(p, e) for p in pages for e in p['errors']]
    L += ['## Every ERROR (%d)' % len(allerr), '']
    if report is None:
        L += ['(no GuiKit report)', '']
    for p, (lv, el, msg, sz) in allerr:
        L.append('- `%s` %s: %s (%s)' % (p['sid'], el or 'menu', msg, ', '.join(sz)))
    L.append('')

    # per system, per page
    L += ['## Per system and page', '',
          'Scale = the `NativeMenuFit` scale at each window size; **TALL** = still taller than the window at the 0.5x floor '
          '(the real client scrolls the whole menu there). `small` = click targets under 12 px at that size.', '']
    for sysname, ps in systems.items():
        L += ['### %s (%d pages)' % (sysname, len(ps)), '']
        for p in ps:
            f = p['facts']
            d = p['packet']
            L.append('#### %s' % esc(p['key']))
            L.append('')
            L.append('- **%s**, GuiKit screen `%s`, packet %d (%s); %d variant(s) seen' % (
                esc(d['title']), p['sid'], d['i'], esc(d.get('from', '')), p['variants']))
            L.append('- size %dx%d%s; buttons %d, body lines %d%s, inputs %d; density %d' % (
                p['fit']['w'], p['fit']['h'], ' (fillScreen design box)' if p['fit']['fill'] else '', f['buttons'], f['lines'],
                '' if f['raw_lines'] == f['lines'] else ' (+%d blank)' % (f['raw_lines'] - f['lines']), f['inputs'], p['density']))
            L.append('- fit: ' + ', '.join('%s %s' % (c, scale_cell(p, c)) for c in cfg_short))
            if p['small']:
                L.append('- click targets under 12 px: ' + ', '.join('%s (%d)' % (k, v) for k, v in p['small'].items())
                         + ('; GuiKit: %d controls, smallest %.1f px' % (len(p['tiny_controls']), p['tiny_min'])
                            if p['tiny_controls'] else ''))
            if f['dup_ids'] or f['dup_labels']:
                L.append('- duplicated: ' + '; '.join(x for x in [
                    ('ids ' + ', '.join(f['dup_ids'])) if f['dup_ids'] else '',
                    ('labels ' + ', '.join(f['dup_labels'])) if f['dup_labels'] else ''] if x))
            for lv, el, msg, sz in p['issues']:
                L.append('- %s %s: %s (%s)' % (lv, el or 'menu', esc(msg), ', '.join(sz)))
            if p['folded']:
                L.append('- (%d "off the screen" errors of children below the window folded into the too-tall error)' % p['folded'])
            if report is not None and not p['issues']:
                L.append('- GuiKit: no errors, no warnings')
            L.append('')
    os.makedirs(os.path.dirname(FINDINGS), exist_ok=True)
    with open(FINDINGS, 'w', encoding='utf-8') as fh:
        fh.write('\n'.join(L) + '\n')
    return tot


def template(p):
    """The builder a page comes from: its screen and the page id up to the first ':' (plus the tab)."""
    d = p['packet']
    return '%s/%s%s [%s]' % (d['screen'], d['page'].split(':')[0], ':*' if ':' in d['page'] else '', d.get('tab') or '')


def scale_cell(p, c):
    k = p['scales'][c]
    return '%.2fx%s' % (k, ' TALL' if c in p['too_tall'] else '')


# ---------------------------------------------------------------- main
def main(argv):
    args = [a for a in argv if not a.startswith('--')]
    src = args[0] if args else DEFAULT_PACKETS
    packets = [json.loads(l) for l in open(src, encoding='utf-8') if l.strip()]
    best, count = OrderedDict(), Counter()
    for d in packets:
        key = '%s/%s [%s]' % (d['screen'], d['page'], d.get('tab') or '')
        count[key] += 1
        nb = len(d.get('buttons') or [])
        if key not in best or (nb, d.get('panelH', 0)) > (len(best[key].get('buttons') or []), best[key].get('panelH', 0)):
            best[key] = d
    used, screens, pages = set(), [], []
    for key, d in best.items():
        sid = screen_id(d, used)
        scr, fitf = to_spec(d, sid)
        screens.append(scr)
        pages.append({'key': key, 'sid': sid, 'packet': d, 'fit': fitf, 'facts': page_facts(d), 'variants': count[key]})
    spec = {'_comment': 'GENERATED by gen/from_crawl.py from %s: every LM page as the server really sent it '
                        '(NativeMenuFit floor 0.5x, no scroll). Do not hand-edit.' % os.path.basename(src),
            'sizes': ['%dx%d' % s for s in SIZES], 'scales': [2, 3, 'auto'], 'skin': 'saiyan_royal', 'look': 'full',
            'format': 'jpg', 'sheets': False, 'keep': ['1920x1080@2'], 'include': [], 'screens': screens}
    os.makedirs(os.path.dirname(SPEC), exist_ok=True)
    with open(SPEC, 'w', encoding='utf-8') as fh:
        json.dump(spec, fh, ensure_ascii=False, indent=1)
    print('%d packets -> %d pages -> %s' % (len(packets), len(pages), SPEC))
    if '--guikit' in argv:
        run_guikit()
    report = read_report(REPORT)
    tot = write_findings(pages, report, src, len(packets))
    print('findings -> %s (pages %d, pages with errors %d, errors %d, warnings %d, pages with sub-12 px targets %d)'
          % (FINDINGS, tot[0], tot[1], tot[2], tot[3], tot[4]))


if __name__ == '__main__':
    main(sys.argv[1:])
