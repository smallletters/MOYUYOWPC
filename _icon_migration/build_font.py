# -*- coding: utf-8 -*-
"""Build lucide.ttf icon font from downloaded Lucide SVGs.
Pipeline: SVG path -> sampled polyline -> shapely buffer (round cap/join) -> glyph contours -> fontTools TTF.
"""
import os, re, math, sys
import xml.etree.ElementTree as ET
from svgpathtools import parse_path, Line, CubicBezier, QuadraticBezier, Arc
from shapely.geometry import LineString, MultiLineString, Polygon
from shapely.ops import unary_union
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen

SVG_DIR = 'D:/MOYUYOWPC/_icon_migration/svg'
OUT_TTF = 'D:/MOYUYOWPC/moyuyo-app/static/fonts/lucide.ttf'
OUT_SCSS = 'D:/MOYUYOWPC/moyuyo-app/src/styles/lucide.scss'

SAMPLE_STEP = 0.35  # in 24-unit space
BUFFER_RAD = 0.85  # stroke width / 2 (lucide uses ~1.7px visual strokes)
QUAD_SEGS = 8
SIMPLIFY_TOL = 0.05  # Douglas-Peucker in 24-unit space (~2.1 font units @1000upm)

def svg_elem_shapes(elem):
    """Convert an SVG shape element to a path-d string."""
    tag = elem.tag.rsplit('}', 1)[-1]
    if tag == 'path':
        return elem.get('d')
    if tag == 'circle':
        cx, cy, r = float(elem.get('cx', 0)), float(elem.get('cy', 0)), float(elem.get('r', 0))
        return f'M {cx-r} {cy} a {r} {r} 0 1 0 {2*r} 0 a {r} {r} 0 1 0 {-2*r} 0 Z'
    if tag == 'ellipse':
        cx, cy, rx, ry = float(elem.get('cx', 0)), float(elem.get('cy', 0)), float(elem.get('rx', 0)), float(elem.get('ry', 0))
        return f'M {cx-rx} {cy} a {rx} {ry} 0 1 0 {2*rx} 0 a {rx} {ry} 0 1 0 {-2*rx} 0 Z'
    if tag == 'rect':
        x, y = float(elem.get('x', 0)), float(elem.get('y', 0))
        w, h = float(elem.get('width', 0)), float(elem.get('height', 0))
        rx, ry = float(elem.get('rx', 0) or 0), float(elem.get('ry', 0) or 0)
        if rx <= 0 and ry <= 0:
            return f'M {x} {y} L {x+w} {y} L {x+w} {y+h} L {x} {y+h} Z'
        r = min(rx if rx else ry, w / 2, h / 2)
        return (f'M {x+r} {y} L {x+w-r} {y} '
                f'A {r} {r} 0 0 1 {x+w} {y+r} L {x+w} {y+h-r} '
                f'A {r} {r} 0 0 1 {x+w-r} {y+h} L {x+r} {y+h} '
                f'A {r} {r} 0 0 1 {x} {y+h-r} L {x} {y+r} '
                f'A {r} {r} 0 0 1 {x+r} {y} Z')
    if tag == 'line':
        return f'M {elem.get("x1")} {elem.get("y1")} L {elem.get("x2")} {elem.get("y2")}'
    if tag == 'polyline':
        nums = elem.get('points').strip().replace(',', ' ').split()
        pairs = ['%s %s' % (nums[j], nums[j+1]) for j in range(0, len(nums) - 1, 2)]
        return 'M ' + ' L '.join(pairs)
    if tag == 'polygon':
        nums = elem.get('points').strip().replace(',', ' ').split()
        pairs = ['%s %s' % (nums[j], nums[j+1]) for j in range(0, len(nums) - 1, 2)]
        return 'M ' + ' L '.join(pairs) + ' Z'
    return None

def expand_hv(d):
    """Normalize path commands into a form svgpathtools can parse.
    Char-level parser: handles H/V, implicit repetition, S/T reflection,
    and SVG arc flags (single-char 0/1 that may be glued to numbers)."""
    NUM = re.compile(r'[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?')
    out = []
    cur = 0j
    start = 0j
    prev_ctrl = None
    i, n = 0, len(d)

    def skip():
        nonlocal i
        while i < n and d[i] in ' \t\r\n,':
            i += 1

    def num():
        nonlocal i
        skip()
        m = NUM.match(d, i)
        if not m:
            raise ValueError('bad number at %r' % d[i:i+12])
        v = float(m.group(0)); i = m.end()
        return v

    def is_cmd():
        skip()
        if i >= n:
            return True  # end of string stops the arg loop
        return d[i] in 'MmZzLlHhVvCcSsQqTtAa'

    def emit(cmd, args):
        out.append(cmd + ' ' + ' '.join('%g' % a for a in args))

    while i < n:
        skip()
        if i >= n:
            break
        c = d[i]; i += 1
        low = c.lower()
        if low == 'm':
            x, y = num(), num()
            if c == 'm': x, y = cur.real + x, cur.imag + y
            cur = complex(x, y); start = cur
            emit('M', (x, y))
            while not is_cmd():
                x, y = num(), num()
                if c == 'm': x, y = cur.real + x, cur.imag + y
                cur = complex(x, y)
                emit('L', (x, y))
            prev_ctrl = None
        elif low == 'z':
            emit('Z', ())
            cur = start; prev_ctrl = None
        elif low == 'l':
            while not is_cmd():
                x, y = num(), num()
                if c == 'l': x, y = cur.real + x, cur.imag + y
                cur = complex(x, y)
                emit('L', (x, y))
            prev_ctrl = None
        elif low == 'h':
            while not is_cmd():
                x = num()
                if c == 'h': x = cur.real + x
                cur = complex(x, cur.imag)
                emit('L', (x, cur.imag))
            prev_ctrl = None
        elif low == 'v':
            while not is_cmd():
                y = num()
                if c == 'v': y = cur.imag + y
                cur = complex(cur.real, y)
                emit('L', (cur.real, y))
            prev_ctrl = None
        elif low == 'c':
            while not is_cmd():
                x1, y1, x2, y2, x, y = num(), num(), num(), num(), num(), num()
                if c == 'c':
                    x1, y1 = cur.real+x1, cur.imag+y1; x2, y2 = cur.real+x2, cur.imag+y2; x, y = cur.real+x, cur.imag+y
                emit('C', (x1, y1, x2, y2, x, y))
                cur = complex(x, y); prev_ctrl = complex(x2, y2)
        elif low == 's':
            while not is_cmd():
                x2, y2, x, y = num(), num(), num(), num()
                if c == 's': x2, y2 = cur.real+x2, cur.imag+y2; x, y = cur.real+x, cur.imag+y
                if prev_ctrl is not None:
                    x1, y1 = 2*cur.real - prev_ctrl.real, 2*cur.imag - prev_ctrl.imag
                else:
                    x1, y1 = cur.real, cur.imag
                emit('C', (x1, y1, x2, y2, x, y))
                cur = complex(x, y); prev_ctrl = complex(x2, y2)
        elif low == 'q':
            while not is_cmd():
                x1, y1, x, y = num(), num(), num(), num()
                if c == 'q': x1, y1 = cur.real+x1, cur.imag+y1; x, y = cur.real+x, cur.imag+y
                emit('Q', (x1, y1, x, y))
                cur = complex(x, y); prev_ctrl = complex(x1, y1)
        elif low == 't':
            while not is_cmd():
                x, y = num(), num()
                if c == 't': x, y = cur.real+x, cur.imag+y
                if prev_ctrl is not None:
                    x1, y1 = 2*cur.real - prev_ctrl.real, 2*cur.imag - prev_ctrl.imag
                else:
                    x1, y1 = cur.real, cur.imag
                emit('Q', (x1, y1, x, y))
                cur = complex(x, y); prev_ctrl = complex(x1, y1)
        elif low == 'a':
            while not is_cmd():
                rx, ry, rot = num(), num(), num()
                skip()
                if i >= n or d[i] not in '01':
                    raise ValueError('arc flag expected at %r' % d[i:i+6])
                laf = d[i]; i += 1
                skip()
                if i >= n or d[i] not in '01':
                    raise ValueError('arc flag expected at %r' % d[i:i+6])
                sf = d[i]; i += 1
                x, y = num(), num()
                if c == 'a': x, y = cur.real+x, cur.imag+y
                emit('A', (rx, ry, rot, int(laf), int(sf), x, y))
                cur = complex(x, y); prev_ctrl = None
    return ' '.join(out)

def sample_path(d, step=SAMPLE_STEP):
    """Parse path-d and return list of (LineString, closed) per continuous subpath."""
    path = parse_path(expand_hv(d))
    subpaths = []
    for sub in path.continuous_subpaths():
        closed = False
        if hasattr(sub, 'isclosed'):
            closed = bool(sub.isclosed())
        pts = []
        for seg in sub:
            if isinstance(seg, Line):
                pts.extend([(seg.start.real, seg.start.imag), (seg.end.real, seg.end.imag)])
            elif isinstance(seg, (CubicBezier, QuadraticBezier)):
                n = max(2, int(seg.length(error=0.05) / step) + 1)
                pts.extend((seg.point(t / n).real, seg.point(t / n).imag) for t in range(n))
                pts.append((seg.end.real, seg.end.imag))
            elif isinstance(seg, Arc):
                # sample by angle
                n = max(2, int(seg.theta / (math.pi / 24)) + 1)
                pts.extend((seg.point(t / n).real, seg.point(t / n).imag) for t in range(n))
                pts.append((seg.end.real, seg.end.imag))
        # dedupe consecutive
        out = []
        for p in pts:
            if not out or abs(p[0]-out[-1][0]) > 1e-6 or abs(p[1]-out[-1][1]) > 1e-6:
                out.append(p)
        if closed:
            if len(out) >= 3:
                subpaths.append((LineString(out), closed))
        elif len(out) >= 2:
            subpaths.append((LineString(out), closed))
    return subpaths

def _flatten_polys(geom):
    """Yield simple Polygons from a geometry."""
    if geom.is_empty:
        return
    if geom.geom_type == 'Polygon':
        if geom.area > 1e-6:
            yield geom
    elif geom.geom_type == 'MultiPolygon':
        for p in geom.geoms:
            if p.area > 1e-6:
                yield p

def icon_outlines(d):
    """Return list of ring point-lists from a stroked icon.
    Each subpath is expanded into a filled polygon. Nested subpaths
    (e.g. camera lens, truck wheels) are treated as holes via a containment tree,
    preserving evenodd-like rendering semantics."""
    # 1. Expand each continuous subpath into its own filled polygon.
    #    Closed subpaths (camera body, circles, hearts) become hollow stroked
    #    outlines: outer.buffer(r) minus inner.buffer(-r).
    raw = []
    for line, closed in sample_path(d):
        if line.is_empty:
            continue
        if closed:
            poly = Polygon(line).buffer(0)
            if poly.is_empty or poly.area < 1e-6:
                continue
            outer = poly.buffer(BUFFER_RAD, quad_segs=QUAD_SEGS,
                               join_style='round')
            inner = poly.buffer(-BUFFER_RAD)
            inner_parts = list(_flatten_polys(inner)) if not inner.is_empty else []
            if inner_parts:
                p = outer.difference(unary_union(inner_parts))
            else:
                p = outer
        else:
            p = line.buffer(BUFFER_RAD, quad_segs=QUAD_SEGS,
                           cap_style='round', join_style='round')
        p = p.buffer(0)  # make valid / union self-intersections
        raw.extend(_flatten_polys(p))
    if not raw:
        return []

    # 2. Build a containment tree: a polygon's smallest containing neighbour is its parent.
    n = len(raw)
    parent = [None] * n
    for i in range(n):
        pi = raw[i]
        best = None
        for j in range(n):
            if i == j:
                continue
            pj = raw[j]
            if pj.area <= pi.area:
                continue
            try:
                if pi.within(pj):
                    if best is None or raw[best].area > pj.area:
                        best = j
            except Exception:
                pass
        parent[i] = best

    children = [[] for _ in range(n)]
    for i, p in enumerate(parent):
        if p is not None:
            children[p].append(i)

    def build(i):
        p = raw[i]
        for c in children[i]:
            p = p.difference(build(c))
        return p

    # 3. Top-level polygons, union and simplify.
    tops = [build(i) for i in range(n) if parent[i] is None]
    if not tops:
        return []
    final = unary_union(tops)
    final = final.simplify(SIMPLIFY_TOL, preserve_topology=True)
    if final.is_empty:
        return []

    rings = []
    for poly in _flatten_polys(final):
        if poly.exterior is None or len(poly.exterior.coords) < 4:
            continue
        rings.append(list(poly.exterior.coords))
        for interior in poly.interiors:
            if len(interior.coords) >= 4:
                rings.append(list(interior.coords))
    return rings

def ring_to_font_points(ring):
    """SVG y-down coords -> font coords (1000 upm, y-up, baseline at 0)."""
    S = 1000.0 / 24.0
    out = []
    for x, y in ring:
        fx = x * S
        fy = (24.0 - y) * S
        out.append((round(fx), round(fy)))
    # dedupe consecutive
    res = []
    for p in out:
        if not res or p != res[-1]:
            res.append(p)
    if len(res) > 2 and res[0] == res[-1]:
        res.pop()
    return res

def build():
    icons = {}
    for fn in sorted(os.listdir(SVG_DIR)):
        key = fn[:-4]
        tree = ET.parse(os.path.join(SVG_DIR, fn))
        root = tree.getroot()
        d_parts = []
        for elem in root.iter():
            d = svg_elem_shapes(elem)
            if d:
                d_parts.append(d)
        if not d_parts:
            print('no geometry:', key)
            continue
        d = ' '.join(expand_hv(part) for part in d_parts)
        try:
            rings = icon_outlines(d)
        except Exception as e:
            print('FAIL', key, e)
            print('  d=', d[:200])
            raise
        if not rings:
            print('no outlines:', key)
            continue
        icons[key] = rings
    print('built %d icons' % len(icons))

    # ---- font assembly ----
    # Code points are STABLE across rebuilds: existing icons keep their
    # assigned codepoints (codepoints.json); newly added icons get fresh
    # codepoints appended after the current max.
    glyph_order = ['.notdef', '.null']
    cmap = {}
    glyphs = {}
    metrics = {}
    names = {}
    cp_path = 'D:/MOYUYOWPC/_icon_migration/codepoints.json'
    if os.path.exists(cp_path):
        code_map = {k: int(v) for k, v in json.load(open(cp_path, encoding='utf-8')).items()}
    else:
        code_map = {}
    next_code = max(code_map.values()) + 1 if code_map else 0xE900
    for key in sorted(icons):
        if key in code_map:
            code = code_map[key]
        else:
            code = next_code
            while code in code_map.values():
                code += 1
            code_map[key] = code
            next_code = code + 1
        gname = 'luc%s' % key.replace('-', '')
        glyph_order.append(gname)
        cmap[code] = gname
        names[key] = (code, gname)
        pen = TTGlyphPen(None)
        for ring in icons[key]:
            pts = ring_to_font_points(ring)
            if len(pts) < 3:
                continue
            pen.moveTo(pts[0])
            for p in pts[1:]:
                pen.lineTo(p)
            pen.closePath()
        glyphs[gname] = pen.glyph()
        code += 1
    metrics = {g: (1000, 0) for g in glyph_order}  # advance width 1000, lsb 0
    metrics['.null'] = (0, 0)
    glyphs.setdefault('.notdef', TTGlyphPen(None).glyph())
    glyphs.setdefault('.null', TTGlyphPen(None).glyph())

    fb = FontBuilder(1000, isTTF=True)
    fb.setupGlyphOrder(glyph_order)
    fb.setupCharacterMap(cmap)
    fb.setupGlyf(glyphs)
    fb.setupHorizontalMetrics(metrics)
    fb.setupHorizontalHeader(ascent=1000, descent=0)
    fb.setupNameTable({
        'familyName': 'Lucide Icons',
        'styleName': 'Regular',
        'uniqueFontIdentifier': 'MOYUYO lucide icons 1.34 subset',
        'fullName': 'Lucide Icons',
        'psName': 'LucideIcons',
        'version': 'Version 1.34.0-subset',
    })
    fb.setupOS2(sTypoAscender=1000, sTypoDescender=0, usWinAscent=1000, usWinDescent=0,
                fsSelection=0x40, achVendID='MOYU', xAvgCharWidth=500,
                usWeightClass=400, usWidthClass=5)
    fb.setupPost()
    os.makedirs(os.path.dirname(OUT_TTF), exist_ok=True)
    fb.save(OUT_TTF)
    print('saved', OUT_TTF, os.path.getsize(OUT_TTF), 'bytes')

    # ---- scss ----
    lines = ["@font-face {", "  font-family: 'LucideIcons';",
             "  src: url('/static/fonts/lucide.ttf') format('truetype');",
             "  font-weight: normal;", "  font-style: normal;", "}", "",
             ".luc {", "  font-family: 'LucideIcons' !important;",
             "  font-size: inherit;", "  line-height: 1;",
             "  display: inline-block;", "  font-style: normal;",
             "  font-weight: normal;", "  font-variant: normal;",
             "  text-transform: none;", "  -webkit-font-smoothing: antialiased;",
             "  -moz-osx-font-smoothing: grayscale;", "}", ""]
    for key, (c, g) in sorted(names.items()):
        lines.append('.luc-%s::before { content: "\\%04x"; }' % (key, c))
    open(OUT_SCSS, 'w', encoding='utf-8').write('\n'.join(lines))
    print('saved', OUT_SCSS, 'with', len(names), 'classes')

    json.dump({k: v[0] for k, v in names.items()}, open('D:/MOYUYOWPC/_icon_migration/codepoints.json', 'w'))
    return icons

if __name__ == '__main__':
    import json
    build()
