"""Project logo (Modrinth icon, README, the in-game mod list): a heraldic shield parted in two nations' colours, a
silver tower and a gold crown, over two crossed swords inside a gold seal.

Drawn as 64x64 pixel art, then written nearest-scaled to 512x512 (what the mod pages show) and copied into the jar for
the mod list (logoFile in neoforge.mods.toml). Run: python3 tools/artgen/logo.py
"""

import math
import os

from pixelkit import Tex, fbm, hexc, mix, shade
from pngio import write_png

SIZE = 64
SCALE = 8
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "branding")
IN_JAR = os.path.join(ROOT, "src", "main", "resources", "nationsandwar_logo.png")

NIGHT = hexc("#070a12")
DUSK = hexc("#1b2740")
INK = hexc("#0b0d12")
GOLD_DARK = hexc("#7d5410")
GOLD = hexc("#d9a92b")
GOLD_LIGHT = hexc("#fff2a8")
STEEL_DARK = hexc("#4a4f58")
STEEL = hexc("#9097a1")
STEEL_LIGHT = hexc("#e4e8ec")
GRIP = hexc("#4c2f1f")
BLUE_DARK = hexc("#162a52")
BLUE = hexc("#2f5591")
BLUE_LIGHT = hexc("#5f8fd0")
RED_DARK = hexc("#4a0a0e")
RED = hexc("#a01d22")
RED_LIGHT = hexc("#d2483f")
TOWER = hexc("#d9dfe6")
TOWER_SHADE = hexc("#8c95a1")
GEM = hexc("#c4352f")

C = SIZE / 2


def background(t):
    """A night disc lit from above the shield, so the round icon crop still reads."""
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x + 0.5 - C, y + 0.5 - (C - 6)) / (SIZE * 0.7)
            n = fbm(x, y, 11, SIZE, SIZE, octaves=3, scale=5.0)
            k = max(0.0, 1.0 - d) ** 1.5 * (0.8 + 0.4 * n)
            t.set(x, y, mix(NIGHT, DUSK, min(1.0, k)))


def seal(t):
    """The gold ring: a bright inner edge, a dark outer one, notches like a coin's rim."""
    for y in range(SIZE):
        for x in range(SIZE):
            r = math.hypot(x + 0.5 - C, y + 0.5 - C)
            if 29.0 <= r < 31.0:
                a = math.atan2(y + 0.5 - C, x + 0.5 - C)
                notch = int((a + math.pi) / (2 * math.pi) * 48) % 2 == 0
                c = GOLD if r < 30.0 else GOLD_DARK
                if notch and r >= 30.0:
                    c = shade(GOLD_DARK, 0.7)
                lit = -math.sin(a) * 0.5 + 0.5  # brighter towards the top
                t.set(x, y, mix(c, GOLD_LIGHT, 0.25 * lit) if r < 30.0 else c)
            elif 28.0 <= r < 29.0:
                t.set(x, y, INK)


def thick_line(t, x0, y0, x1, y1, width, color_at):
    """A straight band from (x0, y0) to (x1, y1); color_at(u, v) picks a colour by the position along (u 0..1) and
    across (v -1..1) the band."""
    length = math.hypot(x1 - x0, y1 - y0)
    ux, uy = (x1 - x0) / length, (y1 - y0) / length
    for y in range(SIZE):
        for x in range(SIZE):
            px, py = x + 0.5 - x0, y + 0.5 - y0
            along = px * ux + py * uy
            across = -px * uy + py * ux
            if 0 <= along <= length and abs(across) <= width / 2:
                c = color_at(along / length, across / (width / 2))
                if c is not None:
                    t.set(x, y, c)


def sword(t, hilt, tip):
    """A sword from its pommel (hilt) to its point (tip): grip, gold crossguard, a blade with a lit edge."""
    hx, hy = hilt
    tx, ty = tip

    def blade(u, v):
        if u < 0.16:  # grip and pommel
            if u < 0.035:
                return GOLD if abs(v) < 0.9 else GOLD_DARK
            return GRIP if abs(v) < 0.55 else None
        if u < 0.205:  # crossguard is drawn separately
            return GOLD if abs(v) < 0.55 else None
        # blade, tapering to the point
        half = 0.85 * min(1.0, (1.0 - u) / 0.12)
        if abs(v) > half:
            return None
        if abs(v) > half - 0.3:
            return STEEL_DARK
        return STEEL_LIGHT if v < 0 else STEEL

    thick_line(t, hx, hy, tx, ty, 4.2, blade)
    # Crossguard: a short gold bar across the blade at the guard.
    gx, gy = hx + (tx - hx) * 0.18, hy + (ty - hy) * 0.18
    length = math.hypot(tx - hx, ty - hy)
    px, py = -(ty - hy) / length, (tx - hx) / length
    thick_line(t, gx - px * 5.5, gy - py * 5.5, gx + px * 5.5, gy + py * 5.5, 2.2,
               lambda u, v: GOLD_LIGHT if v < 0 else GOLD)


def in_shield(x, y):
    """The heater shield: flat top at y 22, straight sides down to y 36, then curving to a point at y 54."""
    x0, x1, top, waist, point = 19.5, 44.5, 22, 36, 54
    if y < top or y > point:
        return False
    if y <= waist:
        return x0 <= x <= x1
    k = (y - waist) / (point - waist)
    half = (x1 - x0) / 2 * math.sqrt(max(0.0, 1 - k * k))
    return abs(x - C) <= half


def shield(t):
    cells = {(x, y) for y in range(SIZE) for x in range(SIZE) if in_shield(x + 0.5, y + 0.5)}
    for x, y in cells:
        left = x < C
        dark, mid, light = (BLUE_DARK, BLUE, BLUE_LIGHT) if left else (RED_DARK, RED, RED_LIGHT)
        n = fbm(x, y, 5, SIZE, SIZE, octaves=2, scale=8.0)
        # Lit from the top left: a soft gradient plus a faint cloth grain.
        k = 0.65 - (y - 22) / 40 - (x - 20) / 90 + (n - 0.5) * 0.15
        c = mix(dark, mid, min(1.0, max(0.0, k + 0.6)))
        if k > 0.35:
            c = mix(c, light, min(1.0, (k - 0.35) * 1.4))
        t.set(x, y, c)
    # The partition line and a gold border; an ink outline outside it.
    for y in range(SIZE):
        for x in range(SIZE):
            if (x, y) not in cells:
                continue
            edge = any((x + dx, y + dy) not in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                t.set(x, y, GOLD_LIGHT if y < 30 and x < C else GOLD)
            elif x in (31, 32) and y > 23:
                t.set(x, y, GOLD_DARK if x == 32 else GOLD)
    for y in range(SIZE):
        for x in range(SIZE):
            if (x, y) in cells:
                continue
            if any((x + dx, y + dy) in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                t.set(x, y, INK)


TOWER_ART = [
    "x.x.x.x.x",
    "xxxxxxxxx",
    ".xxxxxxx.",
    ".xxxxxxx.",
    ".xxx.xxx.",
    ".xxxxxxx.",
    ".xxxxxxx.",
    ".xx...xx.",
    ".xx...xx.",
    "xxx...xxx",
    "xxxxxxxxx",
]


def tower(t, ox, oy):
    """A silver keep across both halves of the shield, with a dark gate and an ink outline."""
    h, w = len(TOWER_ART), len(TOWER_ART[0])
    filled = {(ox + i, oy + j) for j, row in enumerate(TOWER_ART) for i, ch in enumerate(row) if ch == "x"}
    for x, y in filled:
        t.set(x, y, TOWER if x < ox + w // 2 + 1 else TOWER_SHADE)
    for j, row in enumerate(TOWER_ART):
        for i, ch in enumerate(row):
            if ch == "." and 7 <= j and 3 <= i <= 5:
                t.set(ox + i, oy + j, INK)
    for x, y in filled:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, y + dy)
            if p not in filled and not (oy + 7 <= p[1] <= oy + h - 1 and ox + 3 <= p[0] <= ox + 5):
                t.set(p[0], p[1], INK)


CROWN_ART = [
    "..g...g...g..",
    ".ggg.ggg.ggg.",
    ".ggggggggggg.",
    ".gggrgggrggg.",
    ".ggggggggggg.",
    "ooooooooooooo",
]


def crown(t, ox, oy):
    """A gold crown with two gems, resting on the shield's top edge."""
    for j, row in enumerate(CROWN_ART):
        for i, ch in enumerate(row):
            x, y = ox + i, oy + j
            if ch == "g":
                t.set(x, y, GOLD_LIGHT if j <= 1 or i < 5 else GOLD)
            elif ch == "r":
                t.set(x, y, GEM)
            elif ch == "o":
                t.set(x, y, GOLD_DARK)
    shape = {(ox + i, oy + j) for j, row in enumerate(CROWN_ART) for i, ch in enumerate(row) if ch != "."}
    for x, y in shape:
        for dx, dy in ((1, 0), (-1, 0), (0, -1)):
            if (x + dx, y + dy) not in shape:
                t.set(x + dx, y + dy, INK)


def build():
    t = Tex(SIZE, SIZE, seed=3)
    background(t)
    seal(t)
    # Crossed swords: hilts low, points up past the shield's shoulders.
    sword(t, (13, 51), (51, 10))
    sword(t, (51, 51), (13, 10))
    shield(t)
    tower(t, 28, 30)
    crown(t, 26, 16)
    return t


def scaled(rows, factor):
    out = []
    for row in rows:
        wide = [px for px in row for _ in range(factor)]
        out.extend(list(wide) for _ in range(factor))
    return out


if __name__ == "__main__":
    t = build()
    rows = t.copy_rows()
    os.makedirs(OUT, exist_ok=True)
    write_png(os.path.join(OUT, "logo_64.png"), rows)
    big = scaled(rows, SCALE)
    write_png(os.path.join(OUT, "logo.png"), big)
    write_png(IN_JAR, scaled(rows, 4))
    print("logo written:", os.path.join(OUT, "logo.png"))
