"""Nations & War textures: guard/envoy skins (64x64 player layout), the tintable tabard (64x32 armour layout),
the Town Core and Siege Banner. Run: python3 tools/artgen/nwart.py"""
import os, random, sys
sys.path.insert(0, os.path.dirname(__file__))
from pngio import write_png

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "nationsandwar", "textures")
CLEAR = (0, 0, 0, 0)

class Img:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [[CLEAR] * w for _ in range(h)]
    def rect(self, x0, y0, x1, y1, c):  # [x0,x1) x [y0,y1)
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.px[y][x] = c
    def set(self, x, y, c):
        self.px[y][x] = c
    def noise(self, x0, y0, x1, y1, base, amp, rng):
        for y in range(y0, y1):
            for x in range(x0, x1):
                f = 1 + rng.uniform(-amp, amp)
                self.px[y][x] = tuple(max(0, min(255, int(v * f))) for v in base[:3]) + (base[3] if len(base) > 3 else 255,)
    def save(self, rel):
        path = os.path.join(ROOT, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        write_png(path, self.px)

def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)

def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)

# cuboid layouts: (u, v, w, h, d) -> faces in the standard box UV layout
def box_faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}

def paint_box(img, u, v, w, h, d, fn, rng):
    for face, (x, y, fw, fh) in box_faces(u, v, w, h, d).items():
        for yy in range(fh):
            for xx in range(fw):
                img.set(x + xx, y + yy, fn(face, xx, yy, fw, fh, rng))

SKINS = [rgb("c8956d"), rgb("e0b088"), rgb("8d5a3b"), rgb("f1c9a5")]
HAIRS = [rgb("3b2a1d"), rgb("6b4a2b"), rgb("1c1c1c"), rgb("a67b45")]
GAMBESON = [rgb("6f6a5d"), rgb("5c5f66"), rgb("72604a"), rgb("4f5a4c")]

def skin_tex(i, envoy=False):
    rng = random.Random(i * 97 + (5 if envoy else 0))
    img = Img(64, 64)
    skin, hair = SKINS[i % 4], HAIRS[(i * 3 + 1) % 4]
    cloth = rgb("7a2f3f") if envoy else GAMBESON[i % 4]
    trouser = rgb("3a3328") if not envoy else rgb("5a2230")
    boot = rgb("2b2119")
    def head(face, x, y, w, h, r):
        if face == "top" or (face in ("back",) and y < 6) or (face in ("left", "right") and (y < 3 or (y < 6 and ((face == "right" and x < 4) or (face == "left" and x >= 4))))):
            return shade(hair, 1 + r.uniform(-.08, .08))
        if face == "front":
            if y < 2: return shade(hair, 1 + r.uniform(-.08, .08))
            if y == 3 and x in (1, 2, 5, 6): return shade(hair, .8)
            if y == 4 and x in (1, 6): return (240, 240, 240, 255)
            if y == 4 and x in (2, 5): return rgb("2b3a55") if i % 2 else rgb("3d2b1f")
            if y == 6 and 2 <= x <= 5: return shade(skin, .7)
            if not envoy and i % 2 == 0 and y == 7 and 1 <= x <= 6: return shade(hair, .9)
        return shade(skin, 1 + r.uniform(-.04, .04))
    paint_box(img, 0, 0, 8, 8, 8, head, rng)
    def body(face, x, y, w, h, r):
        c = cloth
        if envoy and face == "front" and x in (3, 4): c = rgb("d8c38a")
        if not envoy and y in (3, 7): c = shade(cloth, .8)  # quilting
        if y == 10: c = rgb("4a3420")  # belt
        if y == 10 and face == "front" and x in (3, 4): c = rgb("c9a227")
        return shade(c, 1 + r.uniform(-.06, .06))
    paint_box(img, 16, 16, 8, 12, 4, body, rng)
    def arm(face, x, y, w, h, r):
        if y >= 10 or (face == "bottom"): return shade(skin, 1 + r.uniform(-.04, .04))
        c = cloth if y < 9 else rgb("4a3420")
        return shade(c, 1 + r.uniform(-.06, .06))
    paint_box(img, 40, 16, 4, 12, 4, arm, rng)
    paint_box(img, 32, 48, 4, 12, 4, arm, rng)
    def leg(face, x, y, w, h, r):
        if envoy: c = cloth if y < 9 else boot
        else: c = trouser if y < 8 else boot
        return shade(c, 1 + r.uniform(-.06, .06))
    paint_box(img, 0, 16, 4, 12, 4, leg, rng)
    paint_box(img, 16, 48, 4, 12, 4, leg, rng)
    return img

def tabard():
    """64x32 armour layout. White/greys only: the game tints it with the nation's colour."""
    rng = random.Random(7)
    img = Img(64, 32)
    W, L, D = (250, 250, 250, 255), (205, 205, 205, 255), (150, 150, 150, 255)
    def body(face, x, y, w, h, r):
        if face in ("front", "back"):
            if x in (0, w - 1): return D
            if face == "front" and (x in (3, 4) and 2 <= y <= 8 or y == 4 and 1 <= x <= 6): return L  # cross
            if y == 10: return CLEAR  # belt shows through
            return shade(W, 1 + r.uniform(-.03, .03))
        if face == "top": return W
        return CLEAR if y > 3 else L
    paint_box(img, 16, 16, 8, 12, 4, body, rng)
    def arm(face, x, y, w, h, r):  # shoulder cape
        return shade(W, 1 + r.uniform(-.03, .03)) if (face == "top" or y < 3) else CLEAR
    paint_box(img, 40, 16, 4, 12, 4, arm, rng)
    def leg(face, x, y, w, h, r):  # skirt over the thighs
        if face in ("front", "back") and y < 4: return D if y == 3 else W
        if face in ("left", "right") and y < 3: return L
        return CLEAR
    paint_box(img, 0, 16, 4, 12, 4, leg, rng)
    return img

def town_core():
    rng = random.Random(3)
    side, top = Img(16, 16), Img(16, 16)
    stone, gold = rgb("5d5a63"), rgb("d4a72c")
    side.noise(0, 0, 16, 16, stone, .08, rng)
    side.rect(0, 0, 16, 2, shade(gold, .9)); side.rect(0, 14, 16, 16, shade(gold, .75))
    for y in range(3, 13):
        for x in range(5, 11):
            d = abs(x - 7.5) + abs(y - 8) * .6
            if d < 3.2: side.set(x, y, shade(rgb("6fe3ff"), 1.25 - d * .15))
    side.rect(4, 3, 5, 13, shade(gold, .8)); side.rect(11, 3, 12, 13, shade(gold, .8))
    top.noise(0, 0, 16, 16, stone, .08, rng)
    top.rect(0, 0, 16, 1, gold); top.rect(0, 15, 16, 16, gold); top.rect(0, 0, 1, 16, gold); top.rect(15, 0, 16, 16, gold)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** .5
            if d < 3.5: top.set(x, y, shade(rgb("6fe3ff"), 1.3 - d * .12))
            elif d < 4.5: top.set(x, y, gold)
    side.save("block/town_core_side.png"); top.save("block/town_core_top.png")

def siege_banner():
    rng = random.Random(9)
    block, item = Img(16, 16), Img(16, 16)
    wood, cloth, gold = rgb("6b4a2b"), rgb("a8232e"), rgb("d4a72c")
    block.noise(0, 0, 2, 16, wood, .1, rng)  # pole strip (u 0-2)
    block.noise(2, 0, 16, 12, cloth, .07, rng)  # flag (u 2-16, v 0-12)
    for y in range(0, 12):
        for x in range(2, 16):
            if (x - 9) ** 2 + (y - 5) ** 2 < 6: block.set(x, y, gold)
            if y == 11 and x % 2 == 0: block.set(x, y, gold)
    block.rect(2, 12, 16, 16, gold)  # finial / trim strip (u 2-16, v 12-16)
    for y in range(16): item.set(3, y, shade(wood, 1 + rng.uniform(-.1, .1)))
    item.set(3, 0, gold)
    for y in range(1, 10):
        for x in range(4, 14 - (1 if y > 7 else 0)):
            item.set(x, y, shade(cloth, 1 + rng.uniform(-.07, .07)))
    for y in range(3, 7):
        for x in range(7, 11):
            if (x - 8.5) ** 2 + (y - 4.5) ** 2 < 3: item.set(x, y, gold)
    block.save("block/siege_banner.png"); item.save("item/siege_banner.png")

if __name__ == "__main__":
    for i in range(4):
        skin_tex(i).save(f"entity/guard/guard_{i}.png")
    skin_tex(1, envoy=True).save("entity/envoy.png")
    tabard().save("entity/tabard.png")
    town_core()
    siege_banner()
    print("textures written")
