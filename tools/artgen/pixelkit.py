"""Small pixel-art toolkit for the SupernaturalCraft art generator.

Ported from the Create: Nuclear Industry texturegen, minus Pillow: the canvas is a plain list of
rows so this runs on a bare Python install.

Everything is deterministic: the same seed always produces the same texture, so the
art set can be regenerated / tweaked without drifting.
"""

import math
import random

from pngio import write_png


# --------------------------------------------------------------------------------------
# colour helpers
# --------------------------------------------------------------------------------------

def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(4))


def shade(c, f):
    """Multiply RGB by f, keep alpha."""
    return (
        max(0, min(255, int(c[0] * f))),
        max(0, min(255, int(c[1] * f))),
        max(0, min(255, int(c[2] * f))),
        c[3],
    )


class Ramp:
    """A dark -> light colour ramp. Index 0 is the outline colour."""

    def __init__(self, *cols):
        self.c = [hexc(c) if isinstance(c, str) else c for c in cols]

    def __len__(self):
        return len(self.c)

    def __getitem__(self, i):
        if isinstance(i, slice):
            return self.c[i]
        return self.c[max(0, min(len(self.c) - 1, int(i)))]

    def t(self, x):
        """Sample the ramp with a 0..1 float."""
        return self[int(round(x * (len(self.c) - 1)))]

    def tinted(self, color, amount):
        return Ramp(*[mix(c, color, amount) for c in self.c])


# --------------------------------------------------------------------------------------
# deterministic value noise (seamless: it only ever samples wrapped coordinates)
# --------------------------------------------------------------------------------------

def _hash(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 362437) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFFFFFF) / 0xFFFFFFFF


def fbm(x, y, seed, w, h, octaves=3, scale=4.0):
    """Seamless-ish fractal noise in 0..1."""
    total, amp, norm = 0.0, 1.0, 0.0
    s = scale
    for o in range(octaves):
        ix, iy = int(x * s / w) % max(1, int(s)), int(y * s / h) % max(1, int(s))
        fx, fy = (x * s / w) % 1.0, (y * s / h) % 1.0
        si = int(s)
        c00 = _hash(ix, iy, seed + o)
        c10 = _hash((ix + 1) % si, iy, seed + o)
        c01 = _hash(ix, (iy + 1) % si, seed + o)
        c11 = _hash((ix + 1) % si, (iy + 1) % si, seed + o)
        fx = fx * fx * (3 - 2 * fx)
        fy = fy * fy * (3 - 2 * fy)
        v = (c00 * (1 - fx) + c10 * fx) * (1 - fy) + (c01 * (1 - fx) + c11 * fx) * fy
        total += v * amp
        norm += amp
        amp *= 0.5
        s *= 2
    return total / norm


# --------------------------------------------------------------------------------------
# canvas
# --------------------------------------------------------------------------------------

class Tex:
    def __init__(self, w=16, h=16, seed=0):
        self.w, self.h = w, h
        self.rows = [[(0, 0, 0, 0)] * w for _ in range(h)]
        self.seed = seed
        self.rng = random.Random(seed)

    # -- primitives ---------------------------------------------------------------

    def set(self, x, y, c):
        if c is None:
            return
        if 0 <= x < self.w and 0 <= y < self.h:
            self.rows[int(y)][int(x)] = tuple(c)

    def get(self, x, y):
        return self.rows[int(y) % self.h][int(x) % self.w]

    def blend(self, x, y, c, a):
        """Blend onto an existing pixel. Transparent pixels are never painted, so
        effects like glow() cannot leave a halo disc around an item silhouette."""
        if not (0 <= x < self.w and 0 <= y < self.h) or a <= 0:
            return
        dst = self.rows[int(y)][int(x)]
        if dst[3] == 0:
            return
        self.set(x, y, mix(dst, c, min(1.0, a)))

    def rect(self, x0, y0, x1, y1, c):
        for y in range(int(y0), int(y1) + 1):
            for x in range(int(x0), int(x1) + 1):
                self.set(x, y, c)

    def frame(self, x0, y0, x1, y1, c):
        for x in range(int(x0), int(x1) + 1):
            self.set(x, y0, c)
            self.set(x, y1, c)
        for y in range(int(y0), int(y1) + 1):
            self.set(x0, y, c)
            self.set(x1, y, c)

    def hline(self, x0, x1, y, c):
        for x in range(int(x0), int(x1) + 1):
            self.set(x, y, c)

    def vline(self, x, y0, y1, c):
        for y in range(int(y0), int(y1) + 1):
            self.set(x, y, c)

    def disc(self, cx, cy, r, c, feather=0.0):
        r2 = r * r
        for y in range(self.h):
            for x in range(self.w):
                d = (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2
                if d <= r2:
                    self.set(x, y, c)
                elif feather and d <= (r + feather) ** 2:
                    a = 1.0 - (math.sqrt(d) - r) / feather
                    self.blend(x, y, c, a * 0.6)

    def ring(self, cx, cy, r_out, r_in, c):
        for y in range(self.h):
            for x in range(self.w):
                d = math.sqrt((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2)
                if r_in <= d <= r_out:
                    self.set(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        steps = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(steps + 1):
            t = i / max(1, steps)
            self.set(round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), c)

    # -- string art ---------------------------------------------------------------

    def stamp(self, art, palette, ox=0, oy=0):
        """`art` is a list of equal-length strings; `palette` maps chars to colours.
        '.' and ' ' are skipped."""
        for y, row in enumerate(art):
            for x, ch in enumerate(row):
                if ch in ".  ":
                    continue
                c = palette.get(ch)
                if c is not None:
                    self.set(ox + x, oy + y, c)

    def copy_rows(self):
        return [list(r) for r in self.rows]

    def paste(self, other, ox, oy):
        """Copies another Tex in, skipping its transparent pixels."""
        for y in range(other.h):
            for x in range(other.w):
                c = other.rows[y][x]
                if c[3]:
                    self.set(ox + x, oy + y, c)

    def save(self, path):
        write_png(path, self.rows)


# --------------------------------------------------------------------------------------
# surface treatments
# --------------------------------------------------------------------------------------

def brushed(t, ramp, x0=0, y0=0, x1=None, y1=None, base=3.0, amp=0.9, seed=0,
            axis="h", scale=5.0):
    """Fill a rect with a brushed/grainy metal surface built from a ramp."""
    x1 = t.w - 1 if x1 is None else x1
    y1 = t.h - 1 if y1 is None else y1
    rng = random.Random(seed)
    rows = {}
    for y in range(int(y0), int(y1) + 1):
        rows[y] = rng.uniform(-0.45, 0.45)
    cols = {}
    for x in range(int(x0), int(x1) + 1):
        cols[x] = rng.uniform(-0.45, 0.45)
    for y in range(int(y0), int(y1) + 1):
        for x in range(int(x0), int(x1) + 1):
            n = fbm(x, y, seed, t.w, t.h, octaves=2, scale=scale) - 0.5
            streak = rows[y] if axis == "h" else cols[x]
            v = base + (n * 1.6 + streak * 0.8) * amp
            t.set(x, y, ramp[int(round(v))])


def bevel(t, x0, y0, x1, y1, ramp, hi=None, lo=None, corner=True):
    """Light from the top-left."""
    hi = ramp[len(ramp) - 1] if hi is None else hi
    lo = ramp[1] if lo is None else lo
    for x in range(int(x0), int(x1) + 1):
        t.set(x, y0, hi)
        t.set(x, y1, lo)
    for y in range(int(y0), int(y1) + 1):
        t.set(x0, y, hi)
        t.set(x1, y, lo)
    if corner:
        t.set(x1, y0, ramp[len(ramp) - 3])
        t.set(x0, y1, ramp[len(ramp) - 3])


def inset(t, x0, y0, x1, y1, ramp):
    """Inverse bevel: a recessed area."""
    bevel(t, x0, y0, x1, y1, ramp, hi=ramp[1], lo=ramp[len(ramp) - 2])


def outline(t, x0, y0, x1, y1, ramp, c=None):
    """`ramp` may be a Ramp (its darkest entry is used) or a plain colour."""
    if c is None:
        c = ramp[0] if isinstance(ramp, Ramp) else ramp
    t.frame(x0, y0, x1, y1, c)


def plate(t, ramp, x0=0, y0=0, x1=None, y1=None, seed=0, base=3.0, amp=0.8,
          border=True, axis="h"):
    """A riveted metal plate with an outline and a bevel."""
    x1 = t.w - 1 if x1 is None else x1
    y1 = t.h - 1 if y1 is None else y1
    brushed(t, ramp, x0, y0, x1, y1, base=base, amp=amp, seed=seed, axis=axis)
    if border:
        bevel(t, x0, y0, x1, y1, ramp)
        outline(t, x0, y0, x1, y1, ramp)


def rivet(t, x, y, ramp, size=1):
    """A tiny raised bolt."""
    if size == 1:
        t.set(x, y, ramp[len(ramp) - 1])
        t.set(x + 1, y + 1, ramp[1])
        return
    t.set(x, y, ramp[len(ramp) - 1])
    t.set(x + 1, y, ramp[len(ramp) - 2])
    t.set(x, y + 1, ramp[len(ramp) - 3])
    t.set(x + 1, y + 1, ramp[1])


def vents(t, x0, y0, x1, y1, ramp, step=2, dark=None, lite=None):
    dark = ramp[0] if dark is None else dark
    lite = ramp[len(ramp) - 2] if lite is None else lite
    y = y0
    while y <= y1:
        t.hline(x0, x1, y, dark)
        if y + 1 <= y1:
            t.hline(x0, x1, y + 1, lite)
        y += step + 1


def hazard(t, x0, y0, x1, y1, c1, c2, period=4, seed=0):
    for y in range(int(y0), int(y1) + 1):
        for x in range(int(x0), int(x1) + 1):
            t.set(x, y, c1 if ((x + y) % period) < period // 2 else c2)


def glow(t, cx, cy, r, color, strength=0.85):
    """Two restrained pixel highlight bands; no airbrushed halo."""
    for y in range(t.h):
        for x in range(t.w):
            d = math.sqrt((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2)
            if d > r:
                continue
            a = (0.18 if d < r * 0.38 else 0.06) * strength
            t.blend(x, y, color, a)


def scanlines(t, x0, y0, x1, y1, color, step=2, alpha=0.25):
    for y in range(int(y0), int(y1) + 1, step):
        for x in range(int(x0), int(x1) + 1):
            t.blend(x, y, color, alpha)


def speckle(t, x0, y0, x1, y1, color, chance=0.12, seed=0):
    rng = random.Random(seed)
    for y in range(int(y0), int(y1) + 1):
        for x in range(int(x0), int(x1) + 1):
            if rng.random() < chance:
                t.set(x, y, color)


def drop_shadow(t, ramp_dark=None, alpha=0.35):
    """Darken the bottom-right silhouette edge of an item for readability."""
    src = t.copy_rows()
    dark = (0, 0, 0, 255) if ramp_dark is None else ramp_dark
    for y in range(t.h):
        for x in range(t.w):
            if src[y][x][3] == 0:
                continue
            for dx, dy in ((1, 0), (0, 1), (1, 1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < t.w and 0 <= ny < t.h and src[ny][nx][3] == 0:
                    t.blend(nx, ny, dark, alpha)


def item_outline(t, color):
    """Add a hard outline around every opaque pixel (classic MC item look)."""
    src = t.copy_rows()
    for y in range(t.h):
        for x in range(t.w):
            if src[y][x][3] != 0:
                continue
            neigh = False
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < t.w and 0 <= ny < t.h and src[ny][nx][3] > 0:
                    neigh = True
                    break
            if neigh:
                t.set(x, y, color)
