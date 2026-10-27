"""Draws the mod's textures (pure stdlib, no PIL). Run: python tools/make_textures.py  (from the repo root)."""
import os
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

T = (0, 0, 0, 0)


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def canvas(w, h):
    return [[T for _ in range(w)] for _ in range(h)]


def write_png(path, px):
    h = len(px)
    w = len(px[0])
    raw = b"".join(b"\x00" + b"".join(struct.pack("4B", *p) for p in row) for row in px)

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("wrote", os.path.relpath(path, ROOT))


def put(px, x, y, c):
    if 0 <= y < len(px) and 0 <= x < len(px[0]):
        px[y][x] = c


def draw_map(px, ox, oy, rows, palette):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in palette:
                put(px, ox + x, oy + y, palette[ch])


# 16x16 upgrade item: a plate with a trim colour, a battery cell and a lightning bolt.
UPGRADE = [
    "................",
    "..oooooooooooo..",
    ".oTTTTTTTTTTTTo.",
    ".oTppppppppppTo.",
    ".oTpppkkkkpppTo.",
    ".oTppkYYYYkppTo.",
    ".oTppkYYbYkppTo.",
    ".oTppkYbbYkppTo.",
    ".oTppkbbbbkppTo.",
    ".oTppkYYbYkppTo.",
    ".oTppkYbYYkppTo.",
    ".oTppkbYYYkppTo.",
    ".oTpppkkkkpppTo.",
    ".oTTTTTTTTTTTTo.",
    "..oooooooooooo..",
    "................",
]


def upgrade_texture(trim, trim_dark, accent=None):
    px = canvas(16, 16)
    palette = {
        "o": hexc("2b2b2b"),
        "T": trim,
        "p": hexc("6f6f6f"),
        "k": hexc("1d1d1d"),
        "Y": hexc("3a3a3a"),
        "b": hexc("ffd23f"),
    }
    draw_map(px, 0, 0, UPGRADE, palette)
    # cell cap and a little shading on the trim
    put(px, 7, 3, hexc("1d1d1d"))
    put(px, 8, 3, hexc("1d1d1d"))
    for x in range(2, 14):
        put(px, x, 13, trim_dark)
    for y in range(3, 14):
        put(px, 13, y, trim_dark)
    if accent:
        for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
            put(px, x, y, accent)
    return px


def icon_inventory(px, ox, oy, on):
    frame = hexc("373737")
    cell = hexc("8b8b8b") if on else hexc("6b6b6b")
    for i in range(3):
        for j in range(3):
            x0 = ox + 1 + i * 5
            y0 = oy + 1 + j * 5
            for x in range(x0, x0 + 4):
                for y in range(y0, y0 + 4):
                    edge = x in (x0, x0 + 3) or y in (y0, y0 + 3)
                    put(px, x, y, frame if edge else cell)
    if on:
        bolt(px, ox + 5, oy + 4)


def icon_curios(px, ox, oy, on):
    ring = hexc("e3b23c") if on else hexc("8a7a50")
    dark = hexc("8a6a1c") if on else hexc("5a5040")
    pts = [(7, 3), (8, 3), (9, 4), (10, 5), (11, 6), (11, 7), (11, 8), (11, 9), (10, 10), (9, 11), (8, 12), (7, 12), (6, 11), (5, 10), (4, 9),
           (4, 8), (4, 7), (4, 6), (5, 5), (6, 4)]
    for (x, y) in pts:
        put(px, ox + x, oy + y, ring)
        put(px, ox + x, oy + y + 1, dark)
    gem = hexc("4fd8e8") if on else hexc("4a7a80")
    for (x, y) in [(7, 1), (8, 1), (7, 2), (8, 2)]:
        put(px, ox + x, oy + y, gem)
    if on:
        bolt(px, ox + 5, oy + 5)


def icon_contents(px, ox, oy, on):
    body = hexc("9c6b3a") if on else hexc("6e5a46")
    dark = hexc("5e3d1e") if on else hexc("463a2e")
    for x in range(2, 14):
        for y in range(4, 14):
            edge = x in (2, 13) or y in (4, 13)
            put(px, ox + x, oy + y, dark if edge else body)
    for x in range(4, 12):
        put(px, ox + x, oy + 2, dark)
        put(px, ox + x, oy + 3, dark)
    for x in range(3, 13):
        put(px, ox + x, oy + 8, dark)
    if on:
        bolt(px, ox + 5, oy + 5)


BOLT = [
    "...yy",
    "..yy.",
    ".yyyy",
    "..yy.",
    ".yy..",
    "yy...",
]


def bolt(px, ox, oy):
    draw_map(px, ox, oy, BOLT, {"y": hexc("ffd23f")})


def slash(px, ox, oy):
    red = hexc("d22b2b")
    for i in range(1, 15):
        put(px, ox + i, oy + i, red)
        put(px, ox + i + 1, oy + i, red)


def icons():
    px = canvas(256, 256)
    for idx, draw in enumerate([icon_inventory, icon_curios, icon_contents]):
        draw(px, idx * 32, 0, True)
        draw(px, idx * 32 + 16, 0, False)
        slash(px, idx * 32 + 16, 0)
    return px


def scale(px, factor):
    out = []
    for row in px:
        big = []
        for p in row:
            big.extend([p] * factor)
        for _ in range(factor):
            out.append(list(big))
    return out


def main():
    basic = upgrade_texture(hexc("c46a3c"), hexc("8a4426"))
    advanced = upgrade_texture(hexc("e8b830"), hexc("a07818"), accent=hexc("5fe0e8"))
    for project in ("neoforge-1.21.1", "forge-1.20.1"):
        res = os.path.join(ROOT, project, "src", "main", "resources")
        write_png(os.path.join(res, "assets", "sophisticatedcharging", "textures", "item", "charging_upgrade.png"), basic)
        write_png(os.path.join(res, "assets", "sophisticatedcharging", "textures", "item", "advanced_charging_upgrade.png"), advanced)
        write_png(os.path.join(res, "assets", "sophisticatedcharging", "textures", "gui", "icons.png"), icons())
        write_png(os.path.join(res, "logo.png"), scale(advanced, 8))
    # project icon for CurseForge / Modrinth (not part of the mod)
    write_png(os.path.join(ROOT, "dist", "icon-512.png"), scale(advanced, 32))


if __name__ == "__main__":
    sys.exit(main())
