#!/usr/bin/env python3
"""
ColorOS 17 Digital Clock Font Converter & Normalizer.

Transforms standard TTF/OTF fonts into ColorOS 17 compliant Fixed (LayoutType 1)
or Flex (LayoutType 2) clock fonts with accurate UPM (1000), vertical metrics,
top anchoring, and variable axes ('wght' and 'HGHT') without point distortion.
"""

import argparse
import math
import os
import re
import sys
from fontTools.ttLib import TTFont, newTable
from fontTools.ttLib.tables._g_l_y_f import GlyphCoordinates
from fontTools.ttLib.tables._f_v_a_r import table__f_v_a_r, Axis, NamedInstance
from fontTools.ttLib.tables._g_v_a_r import table__g_v_a_r
from fontTools.ttLib.tables.TupleVariation import TupleVariation
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.recordingPen import DecomposingRecordingPen
from fontTools.subset import Subsetter, Options
from fontTools.varLib.instancer import instantiateVariableFont

# Required clock glyphs: digits 0-9, colon, and space
CLOCK_CHARS = "0123456789: "

def ensure_truetype_outlines(font):
    """Converts CFF / PostScript outlines to TrueType quadratic glyf outlines if needed."""
    if 'glyf' in font:
        return
    glyph_set = font.getGlyphSet()
    glyf = newTable('glyf')
    glyf.glyphs = {}
    glyf.glyphOrder = font.getGlyphOrder()
    hmtx = newTable('hmtx')
    hmtx.metrics = {}
    for name in font.getGlyphOrder():
        tt_pen = TTGlyphPen(glyph_set)
        cu_pen = Cu2QuPen(tt_pen, max_err=1.0)
        try:
            glyph_set[name].draw(cu_pen)
            g = tt_pen.glyph()
        except Exception:
            g = tt_pen.glyph()
        g.recalcBounds(glyf)
        glyf.glyphs[name] = g
        hmtx.metrics[name] = font['hmtx'].metrics.get(name, (500, 0)) if 'hmtx' in font else (500, 0)
    font['glyf'] = glyf
    font['hmtx'] = hmtx
    font['loca'] = newTable('loca')
    if 'CFF ' in font:
        del font['CFF ']
    if 'CFF2' in font:
        del font['CFF2']
    # TrueType sfnt version after replacing CFF outlines.
    font.sfntVersion = "\x00\x01\x00\x00"
    font['head'].glyphDataFormat = 0
    font['maxp'].tableVersion = 0x00010000
    maxp = font['maxp']
    for attr in ['maxZones', 'maxTwilightPoints', 'maxStorage', 'maxFunctionDefs', 'maxInstructionDefs', 'maxStackElements', 'maxSizeOfInstructions', 'maxComponentElements', 'maxComponentDepth', 'maxPoints', 'maxContours', 'maxCompositePoints', 'maxCompositeContours']:
        setattr(maxp, attr, 1 if attr == 'maxZones' else 0)
    maxp.recalc(font)
    if 'post' in font:
        font['post'].formatType = 3.0

def synthesize_colon_if_missing(font):
    """Synthesizes a clean two-dot colon glyph if colon is not mapped in cmap."""
    cmap = font.getBestCmap() or {}
    if ord(':') in cmap:
        return

    glyf = font['glyf']
    hmtx = font['hmtx']
    digit_names = [font.getBestCmap().get(ord(c)) for c in '0123456789' if ord(c) in font.getBestCmap()]
    digit_height = 700
    digit_y_min = 0
    digit_y_max = 700

    for name in digit_names:
        if name and name in glyf and hasattr(glyf[name], 'yMax'):
            digit_y_min = glyf[name].yMin
            digit_y_max = glyf[name].yMax
            digit_height = digit_y_max - digit_y_min
            break

    # Build 8-point quadratic circles for the two dots
    r = max(20, round(digit_height * 0.05))
    cx = 250
    mid_y = (digit_y_min + digit_y_max) / 2.0
    cy_low = round(mid_y - digit_height * 0.20)
    cy_high = round(mid_y + digit_height * 0.20)

    pen = TTGlyphPen(None)
    for cy in [cy_low, cy_high]:
        pen.moveTo((cx + r, cy))
        pen.qCurveTo((cx + r, cy + r), (cx, cy + r))
        pen.qCurveTo((cx - r, cy + r), (cx - r, cy))
        pen.qCurveTo((cx - r, cy - r), (cx, cy - r))
        pen.qCurveTo((cx + r, cy - r), (cx + r, cy))
        pen.closePath()

    colon_glyph = pen.glyph()
    colon_name = "colon"
    glyf[colon_name] = colon_glyph
    hmtx.metrics[colon_name] = (cx * 2, cx - r)
    for table in font['cmap'].tables:
        table.cmap[ord(':')] = colon_name
    order = list(font.getGlyphOrder())
    if colon_name not in order:
        order.append(colon_name)
        font.setGlyphOrder(order)

def decompose_composite_glyphs(font):
    """Decomposes all composite glyphs into simple glyphs with quadratic contours."""
    if 'glyf' not in font:
        return
    glyf = font['glyf']
    glyph_set = font.getGlyphSet()
    for name in font.getGlyphOrder():
        if name in glyf:
            g = glyf[name]
            if g.numberOfContours < 0:
                dec_pen = DecomposingRecordingPen(glyph_set)
                glyph_set[name].draw(dec_pen)
                tt_pen = TTGlyphPen(None)
                dec_pen.replay(tt_pen)
                new_glyph = tt_pen.glyph()
                new_glyph.recalcBounds(glyf)
                glyf[name] = new_glyph

def ensure_empty_space_glyph(font):
    """Ensures space glyph (0x20) exists and is strictly empty (0 contours, positive advance)."""
    cmap = font.getBestCmap() or {}
    glyf = font['glyf']
    hmtx = font['hmtx']
    space_name = cmap.get(ord(' '), 'space')
    pen = TTGlyphPen(None)
    space_glyph = pen.glyph()
    glyf[space_name] = space_glyph
    adv = hmtx.metrics.get(space_name, (300, 0))[0]
    if adv <= 0:
        adv = 300
    hmtx.metrics[space_name] = (adv, 0)
    for table in font['cmap'].tables:
        table.cmap[ord(' ')] = space_name
    order = list(font.getGlyphOrder())
    if space_name not in order:
        order.append(space_name)
        font.setGlyphOrder(order)

def calculate_contour_normals(coords, end_pts):
    """
    Computes outward normal unit vectors for each point on each contour.
    Used only when synthetic weight variation is explicitly enabled.
    """
    normals = []
    start = 0
    for ep in end_pts:
        pts = coords[start:ep + 1]
        start = ep + 1
        n = len(pts)
        if n == 0:
            continue
        area = sum(pts[j][0] * pts[(j + 1) % n][1] - pts[(j + 1) % n][0] * pts[j][1] for j in range(n)) / 2.0
        is_outer = (area < 0)

        for j in range(n):
            p_prev = pts[(j - 1) % n]
            p_next = pts[(j + 1) % n]
            tx = p_next[0] - p_prev[0]
            ty = p_next[1] - p_prev[1]
            l = math.hypot(tx, ty)
            if l < 1e-5:
                normals.append((0.0, 0.0))
            else:
                if is_outer:
                    normals.append((-ty / l, tx / l))
                else:
                    normals.append((ty / l, -tx / l))
    return normals

def stretch_y_piecewise(y, y_top=673.0, h_min=748.0, h_max=4201.0, cap_ratio=0.12):
    """
    Zonal piecewise vertical stretch anchored at y_top and expanding downwards.
    Preserves top and bottom horizontal stroke thicknesses while stretching vertical stems.
    """
    rel = (y_top - y) / h_min
    top_cap = h_min * cap_ratio
    bot_cap = h_min * cap_ratio
    mid_ratio = 1.0 - 2.0 * cap_ratio
    mid_expanded = h_max - top_cap - bot_cap

    if rel <= cap_ratio:
        new_dist = rel * h_min
    elif rel >= 1.0 - cap_ratio:
        dist_from_bot = (1.0 - rel) * h_min
        new_dist = h_max - dist_from_bot
    else:
        mid_rel = (rel - cap_ratio) / mid_ratio
        new_dist = top_cap + mid_rel * mid_expanded

    return y_top - new_dist

def stretch_colon_y(y, y_min_base=30.0, y_max_base=570.0, y_min_target=-2464.0, y_max_target=-401.0):
    """Stretches colon dots proportionally between compact and tall states."""
    h_base = y_max_base - y_min_base
    if h_base <= 0:
        return y
    rel = (y - y_min_base) / h_base
    return y_min_target + rel * (y_max_target - y_min_target)


REFERENCE_CHARS = "0123456789: "


def _bbox(points):
    if not points:
        return None
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    return (min(xs), max(xs), min(ys), max(ys))


def _map_points(points, src, dst):
    """Affine-map a point list from src=(xmin,xmax,ymin,ymax) to dst."""
    sx_min, sx_max, sy_min, sy_max = src
    dx_min, dx_max, dy_min, dy_max = dst
    if sx_max == sx_min:
        sx = 1.0
        dx0 = (dx_min + dx_max) / 2.0 - sx_min
    else:
        sx = (dx_max - dx_min) / float(sx_max - sx_min)
        dx0 = dx_min - sx_min * sx
    if sy_max == sy_min:
        sy = 1.0
        dy0 = (dy_min + dy_max) / 2.0 - sy_min
    else:
        sy = (dy_max - dy_min) / float(sy_max - sy_min)
        dy0 = dy_min - sy_min * sy
    return [(round(x * sx + dx0), round(y * sy + dy0)) for x, y in points]


def _extract_reference_boxes(font):
    """Extract per-character bbox and advance from a reference clock font."""
    cmap = font.getBestCmap() or {}
    glyf = font['glyf']
    hmtx = font['hmtx']
    boxes = {}
    for ch in REFERENCE_CHARS:
        name = cmap.get(ord(ch))
        if not name or name not in hmtx.metrics:
            continue
        advance = hmtx.metrics[name][0]
        glyph = glyf.get(name)
        if glyph is not None and getattr(glyph, 'numberOfContours', 0) > 0:
            boxes[ch] = (glyph.xMin, glyph.xMax, glyph.yMin, glyph.yMax, advance)
        else:
            boxes[ch] = (0, 0, 0, 0, advance)
    return boxes


def load_reference(path, mode, weight=700):
    """Load official clock-font geometry for reference-based conversion."""
    ref = TTFont(path)
    if 'glyf' not in ref:
        raise ValueError(f"reference font has no glyf table: {path}")
    cmap = ref.getBestCmap() or {}
    if not all(ord(c) in cmap for c in '0123456789:'):
        raise ValueError(f"reference font is missing clock digits/colon: {path}")

    hhea = ref['hhea']
    os2 = ref['OS/2']
    head = ref['head']
    metrics = {
        'ascent': hhea.ascent,
        'descent': hhea.descent,
        'line_gap': hhea.lineGap,
        'typo_ascender': os2.sTypoAscender,
        'typo_descender': os2.sTypoDescender,
        'typo_line_gap': os2.sTypoLineGap,
        'win_ascent': os2.usWinAscent,
        'win_descent': os2.usWinDescent,
        'x_avg': getattr(os2, 'xAvgCharWidth', 500),
        'head_bbox': (head.xMin, head.yMin, head.xMax, head.yMax),
        'fvar': [(a.axisTag, a.minValue, a.defaultValue, a.maxValue) for a in ref['fvar'].axes] if 'fvar' in ref else [],
    }

    if mode == 'flex':
        f1 = instantiateVariableFont(ref, {'wght': float(weight), 'HGHT': 1.0}, inplace=False, updateFontNames=False)
        f2 = instantiateVariableFont(ref, {'wght': float(weight), 'HGHT': 1000.0}, inplace=False, updateFontNames=False)
        compact = _extract_reference_boxes(f1)
        stretched = _extract_reference_boxes(f2)
        f1.close()
        f2.close()
        metrics['adv_max'] = max((v[4] for v in stretched.values()), default=hhea.advanceWidthMax)
        result = {'metrics': metrics, 'compact': compact, 'stretched': stretched}
    else:
        fixed = _extract_reference_boxes(ref)
        metrics['adv_max'] = max((v[4] for v in fixed.values()), default=hhea.advanceWidthMax)
        result = {'metrics': metrics, 'fixed': fixed}

    ref.close()
    return result


def convert_font(
    input_path,
    output_path,
    mode="fixed",
    display_name=None,
    synthetic_weight=False,
    reference_path=None,
    reference_weight=700
):
    print(f"Loading font from {input_path}...")
    font = TTFont(input_path)
    reference = load_reference(reference_path, mode, reference_weight) if reference_path else None

    # 1. Convert CFF to TrueType if necessary
    ensure_truetype_outlines(font)

    # 2. Decompose composite glyphs to direct simple quadratic contours
    decompose_composite_glyphs(font)

    # 3. Synthesize colon ':' if missing and ensure empty space ' '
    synthesize_colon_if_missing(font)
    ensure_empty_space_glyph(font)

    # 4. Subsetting to clock characters
    options = Options()
    options.drop_tables = ["GPOS", "GSUB", "VDMX", "LTSH", "hdmx", "vhea", "vmtx"]
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    subsetter = Subsetter(options=options)
    subsetter.populate(text=CLOCK_CHARS)
    subsetter.subset(font)

    # 5. Re-verify decomposition & empty space post-subsetting
    decompose_composite_glyphs(font)
    ensure_empty_space_glyph(font)

    # 6. Rescale unitsPerEm to 1000 if different
    upm = font['head'].unitsPerEm
    scale_factor = 1000.0 / upm if upm != 1000 else 1.0
    if scale_factor != 1.0:
        font['head'].unitsPerEm = 1000
        glyf = font['glyf']
        hmtx = font['hmtx']
        for name in font.getGlyphOrder():
            if name in glyf:
                g = glyf[name]
                if hasattr(g, 'coordinates'):
                    new_coords = GlyphCoordinates([(round(p[0] * scale_factor), round(p[1] * scale_factor)) for p in g.coordinates])
                    g.coordinates = new_coords
            if name in hmtx.metrics:
                adv, lsb = hmtx.metrics[name]
                hmtx.metrics[name] = (round(adv * scale_factor), round(lsb * scale_factor))

    # Recalculate bounding boxes
    glyf = font['glyf']
    for name in font.getGlyphOrder():
        if name in glyf and hasattr(glyf[name], 'recalcBounds'):
            glyf[name].recalcBounds(glyf)

    # Measure digit bounding box
    cmap = font.getBestCmap()
    digit_names = [cmap.get(ord(c)) for c in '0123456789' if ord(c) in cmap]
    digit_names = [n for n in digit_names if n and n in glyf and hasattr(glyf[n], 'yMax')]

    if digit_names:
        digit_y_min = min(glyf[n].yMin for n in digit_names)
        digit_y_max = max(glyf[n].yMax for n in digit_names)
    else:
        digit_y_min = 0
        digit_y_max = 700
    digit_height = digit_y_max - digit_y_min

    # Clean existing variation tables
    for tbl in ['fvar', 'gvar', 'HVAR', 'MVAR', 'STAT', 'cvar']:
        if tbl in font:
            del font[tbl]

    name_table = font['name']
    name_table.names = []

    family_name = display_name or os.path.splitext(os.path.basename(input_path))[0]
    clean_family = re.sub(r'[\(\)\[\]\{\}<>\/%\?]', '', family_name).strip()
    subfamily_name = "Regular"
    full_name = f"{clean_family} {subfamily_name}"
    clean_ps_family = re.sub(r'[^a-zA-Z0-9]', '', clean_family) or "ClockFont"
    clean_ps_sub = re.sub(r'[^a-zA-Z0-9]', '', subfamily_name) or "Regular"
    postscript_name = f"{clean_ps_family}{clean_ps_sub}"

    for platform_id, enc_id, lang_id in [(3, 1, 0x409), (1, 0, 0)]:
        try:
            name_table.setName(clean_family, 1, platform_id, enc_id, lang_id)
            name_table.setName(subfamily_name, 2, platform_id, enc_id, lang_id)
            name_table.setName(f"1.000;RealOS;{postscript_name}", 3, platform_id, enc_id, lang_id)
            name_table.setName(full_name, 4, platform_id, enc_id, lang_id)
            name_table.setName("Version 1.000", 5, platform_id, enc_id, lang_id)
            name_table.setName(postscript_name, 6, platform_id, enc_id, lang_id)
        except Exception:
            pass

    if mode == "fixed":
        # Fixed Mode (LayoutType 1)
        if reference is not None:
            m = reference['metrics']
            font['hhea'].ascent = m['ascent']
            font['hhea'].descent = m['descent']
            font['hhea'].lineGap = m['line_gap']

            os2 = font['OS/2']
            os2.sTypoAscender = m['typo_ascender']
            os2.sTypoDescender = m['typo_descender']
            os2.sTypoLineGap = m['typo_line_gap']
            os2.usWinAscent = m['win_ascent']
            os2.usWinDescent = m['win_descent']
            os2.usWeightClass = 700
            os2.fsType = 0
            os2.xAvgCharWidth = m['x_avg']

            # Map each clock glyph to the official per-digit box and advance.
            hmtx = font['hmtx']
            for ch, box in reference['fixed'].items():
                name = cmap.get(ord(ch))
                if not name:
                    continue
                g = glyf.get(name)
                if g is not None and hasattr(g, 'coordinates') and box[1] > box[0] and box[3] > box[2]:
                    src = (g.xMin, g.xMax, g.yMin, g.yMax)
                    g.coordinates = GlyphCoordinates(_map_points(g.coordinates, src, box[:4]))
                    g.recalcBounds(glyf)
                    hmtx.metrics[name] = (box[4], box[0])
                elif name in hmtx.metrics:
                    hmtx.metrics[name] = (box[4], 0)

            font['hhea'].advanceWidthMax = m['adv_max']
            head = font['head']
            head.xMin, head.yMin, head.xMax, head.yMax = m['head_bbox']
        else:
            font['hhea'].ascent = 928
            font['hhea'].descent = -244
            font['hhea'].lineGap = 0

            os2 = font['OS/2']
            os2.sTypoAscender = 850
            os2.sTypoDescender = -150
            # Match stock fixed OS/2 metrics.
            os2.sTypoLineGap = 200
            os2.usWinAscent = 1048
            os2.usWinDescent = 271
            os2.usWeightClass = 700
            os2.fsType = 0

            # Preserve source height; move the digit box to the stock center.
            target_center_y = 333.5
            current_center_y = (digit_y_min + digit_y_max) / 2.0
            delta_y = round(target_center_y - current_center_y)

            for name in font.getGlyphOrder():
                if name in glyf and hasattr(glyf[name], 'coordinates'):
                    g = glyf[name]
                    new_coords = []
                    for x, y in g.coordinates:
                        new_coords.append((x, y + delta_y))
                    g.coordinates = GlyphCoordinates(new_coords)
                    g.recalcBounds(glyf)

        # Variable font axis 'wght' (100.0 .. 700.0, default 700.0)
        fvar = newTable('fvar')
        axis_wght = Axis()
        axis_wght.axisTag = 'wght'
        axis_wght.minValue = 100.0
        axis_wght.defaultValue = 700.0
        axis_wght.maxValue = 700.0
        if reference is not None:
            for tag, lo, default, hi in reference['metrics']['fvar']:
                if tag == 'wght':
                    axis_wght.minValue = lo
                    axis_wght.defaultValue = default
                    axis_wght.maxValue = hi
                    break
        axis_wght.flags = 0
        axis_wght.axisNameID = 256
        name_table.setName("Weight", 256, 3, 1, 0x409)

        fvar.axes = [axis_wght]
        fvar.instances = []
        font['fvar'] = fvar

        # gvar table
        gvar = newTable('gvar')
        gvar.version = 1
        gvar.reserved = 0
        gvar.variations = {}

        for name in font.getGlyphOrder():
            if name in glyf and hasattr(glyf[name], 'coordinates') and glyf[name].numberOfContours > 0:
                g = glyf[name]
                num_pts = len(g.coordinates)
                if synthetic_weight:
                    normals = calculate_contour_normals(g.coordinates, g.endPtsOfContours)
                    deltas = [(-round(35 * nx), -round(20 * ny)) for nx, ny in normals]
                else:
                    # Clean/pristine mode: zero delta maintains 100% original outline quality
                    deltas = [(0, 0)] * num_pts
                deltas += [(0, 0), (0, 0), (0, 0), (0, 0)]
                var = TupleVariation({'wght': (-1.0, -1.0, 0.0)}, deltas)
                gvar.variations[name] = [var]
            else:
                gvar.variations[name] = []
        font['gvar'] = gvar

    elif mode == "flex":
        # Flex Mode (LayoutType 2)
        if reference is not None:
            m = reference['metrics']
            font['hhea'].ascent = m['ascent']
            font['hhea'].descent = m['descent']
            font['hhea'].lineGap = m['line_gap']

            os2 = font['OS/2']
            os2.sTypoAscender = m['typo_ascender']
            os2.sTypoDescender = m['typo_descender']
            os2.sTypoLineGap = m['typo_line_gap']
            os2.usWinAscent = m['win_ascent']
            os2.usWinDescent = m['win_descent']
            os2.usWeightClass = 1000
            os2.fsType = 0
            os2.xAvgCharWidth = m['x_avg']
        else:
            font['hhea'].ascent = 700
            font['hhea'].descent = -3543
            font['hhea'].lineGap = 0

            os2 = font['OS/2']
            os2.sTypoAscender = 700
            os2.sTypoDescender = -3543
            os2.sTypoLineGap = 0
            os2.usWinAscent = 700
            os2.usWinDescent = 3543
            os2.usWeightClass = 1000
            os2.fsType = 0

        # Anchor top to y = 673, compact height = 748 (y in [-75, 673])
        y_top_anchor = 673.0
        h_min = 748.0
        h_max = 4201.0
        v_scale_compact = h_min / digit_height if digit_height > 0 else 1.0

        compact_coords = {}
        stretched_coords = {}
        char_for_name = {cmap.get(ord(ch)): ch for ch in REFERENCE_CHARS}
        hmtx = font['hmtx']

        for name in font.getGlyphOrder():
            if name in glyf and hasattr(glyf[name], 'coordinates'):
                g = glyf[name]
                c_pts = []
                s_pts = []
                is_colon = ("colon" in name)
                for x, y in g.coordinates:
                    if is_colon:
                        rel_y = (y - digit_y_min) / digit_height if digit_height > 0 else 0.5
                        cy = round(30.0 + rel_y * 537.0)
                        sy = round(stretch_colon_y(cy))
                    else:
                        cy = round(y_top_anchor - (digit_y_max - y) * v_scale_compact)
                        sy = round(stretch_y_piecewise(cy, y_top=y_top_anchor, h_min=h_min, h_max=h_max))
                    c_pts.append((x, cy))
                    s_pts.append((x, sy))

                if reference is not None:
                    ch = char_for_name.get(name)
                    ref_c = reference['compact'].get(ch)
                    ref_s = reference['stretched'].get(ch)
                    if ref_c is not None and ref_s is not None:
                        src_c = _bbox(c_pts)
                        src_s = _bbox(s_pts)
                        if src_c is not None and src_s is not None:
                            if ref_c[1] > ref_c[0] and ref_c[3] > ref_c[2]:
                                c_pts = _map_points(c_pts, src_c, ref_c[:4])
                            if ref_s[1] > ref_s[0] and ref_s[3] > ref_s[2]:
                                s_pts = _map_points(s_pts, src_s, ref_s[:4])
                        hmtx.metrics[name] = (ref_s[4], ref_s[0])

                compact_coords[name] = c_pts
                stretched_coords[name] = s_pts

                # Base master in glyf table is at HGHT=1000 (stretched master)
                g.coordinates = GlyphCoordinates(s_pts)
                g.recalcBounds(glyf)

        if reference is not None:
            # Set official advances for non-outline glyphs.
            for ch, box in reference['stretched'].items():
                name = cmap.get(ord(ch))
                if name and name in hmtx.metrics:
                    hmtx.metrics[name] = (box[4], box[0] if box[1] > box[0] else 0)
            font['hhea'].advanceWidthMax = reference['metrics']['adv_max']
            os2.xAvgCharWidth = reference['metrics']['x_avg']
            head = font['head']
            head.xMin, head.yMin, head.xMax, head.yMax = reference['metrics']['head_bbox']
        else:
            # Stock tunable fonts are tabular; match their equal advances.
            digit_names = [cmap.get(ord(c)) for c in '0123456789']
            digit_names = [n for n in digit_names if n and n in hmtx.metrics]
            if digit_names:
                uniform_adv = max(hmtx.metrics[n][0] for n in digit_names)
                for name in digit_names:
                    g = glyf[name]
                    x_min = getattr(g, 'xMin', hmtx.metrics[name][1])
                    x_max = getattr(g, 'xMax', x_min)
                    new_lsb = (uniform_adv - (x_max - x_min)) // 2
                    dx = new_lsb - x_min
                    if dx:
                        if hasattr(g, 'coordinates'):
                            g.coordinates = GlyphCoordinates([(x + dx, y) for x, y in g.coordinates])
                            g.recalcBounds(glyf)
                    hmtx.metrics[name] = (uniform_adv, new_lsb)
                font['hhea'].advanceWidthMax = max(adv for adv, _ in hmtx.metrics.values())

        # Build fvar with 'wght' and 'HGHT'
        fvar = newTable('fvar')
        axis_wght = Axis()
        axis_wght.axisTag = 'wght'
        axis_wght.minValue = 100.0
        axis_wght.defaultValue = 1000.0
        axis_wght.maxValue = 1000.0
        axis_hght = Axis()
        axis_hght.axisTag = 'HGHT'
        axis_hght.minValue = 1.0
        axis_hght.defaultValue = 1000.0
        axis_hght.maxValue = 1000.0
        if reference is not None:
            for tag, lo, default, hi in reference['metrics']['fvar']:
                if tag == 'wght':
                    axis_wght.minValue = lo
                    axis_wght.defaultValue = default
                    axis_wght.maxValue = hi
                elif tag == 'HGHT':
                    axis_hght.minValue = lo
                    axis_hght.defaultValue = default
                    axis_hght.maxValue = hi
        axis_wght.flags = 0
        axis_wght.axisNameID = 256
        name_table.setName("Weight", 256, 3, 1, 0x409)

        axis_hght.flags = 0
        axis_hght.axisNameID = 257
        name_table.setName("Height", 257, 3, 1, 0x409)

        fvar.axes = [axis_wght, axis_hght]
        fvar.instances = []
        font['fvar'] = fvar

        # Build gvar
        gvar = newTable('gvar')
        gvar.version = 1
        gvar.reserved = 0
        gvar.variations = {}

        for name in font.getGlyphOrder():
            if name in stretched_coords and name in glyf and glyf[name].numberOfContours > 0:
                s_pts = stretched_coords[name]
                c_pts = compact_coords[name]
                num_pts = len(s_pts)
                
                # HGHT variation deltas (stretched -> compact as HGHT goes 1000 -> 1)
                hght_deltas = [(c_pts[i][0] - s_pts[i][0], c_pts[i][1] - s_pts[i][1]) for i in range(num_pts)]
                hght_deltas += [(0, 0), (0, 0), (0, 0), (0, 0)]
                var_hght = TupleVariation({'HGHT': (-1.0, -1.0, 0.0)}, hght_deltas)

                # wght variation deltas
                if synthetic_weight:
                    g = glyf[name]
                    normals = calculate_contour_normals(s_pts, g.endPtsOfContours)
                    wght_deltas = [(-round(30 * nx), -round(15 * ny)) for nx, ny in normals]
                else:
                    # Clean/pristine mode: zero delta preserves original vector contours
                    wght_deltas = [(0, 0)] * num_pts
                wght_deltas += [(0, 0), (0, 0), (0, 0), (0, 0)]
                var_wght = TupleVariation({'wght': (-1.0, -1.0, 0.0)}, wght_deltas)

                gvar.variations[name] = [var_hght, var_wght]
            else:
                gvar.variations[name] = []
        font['gvar'] = gvar

    # Save output font
    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    font.save(output_path)
    print(f"Successfully converted -> {output_path} ({os.path.getsize(output_path)} bytes)")

def main():
    parser = argparse.ArgumentParser(description="ColorOS 17 Digital Clock Font Converter")
    parser.add_argument("input", help="Path to input TTF or OTF font")
    parser.add_argument("-o", "--output", required=True, help="Path to output TTF")
    parser.add_argument("--mode", choices=["fixed", "flex"], default="fixed", help="Target clock font mode")
    parser.add_argument("--name", help="Display name for the font")
    parser.add_argument("--synthetic-weight", action="store_true", help="Enable synthetic normal-based thinning (default: False, keeps original outlines)")
    parser.add_argument("--reference", help="Official clock TTF/OTF to use as the geometry reference")
    parser.add_argument("--reference-weight", type=int, default=700, help="Weight used to measure a variable reference font (default: 700)")

    args = parser.parse_args()
    convert_font(
        input_path=args.input,
        output_path=args.output,
        mode=args.mode,
        display_name=args.name,
        synthetic_weight=args.synthetic_weight,
        reference_path=args.reference,
        reference_weight=args.reference_weight
    )

if __name__ == "__main__":
    main()
