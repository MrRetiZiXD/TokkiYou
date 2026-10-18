"""Turn the logo's bitmap into the path an adaptive icon is drawn from.

    python3 assets/make_logo_vector.py

The soft note exists as a picture -- `icon_out/tokkiyou-1024-glyph.png`, white
on nothing, laid out in the same square an adaptive icon uses -- and a
launcher from Android 8 on does not take a picture for the front of an icon in
this apk: the file it reads there is an XML one, so what goes in has to be a
vector. This walks the edge of the picture and writes it down as one.

The edge is found between pixels rather than on them: where the picture goes
from more than half there to less than half, the crossing is placed by how far
each side is from half, which is what keeps a curve a curve instead of a
staircase. The points that add nothing are then dropped.

What it prints goes into `tokkiyou/artwork.py` as GLYPH.
"""
import math
import os
import re
import sys

import numpy
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "icon_out", "tokkiyou-1024-glyph.png")
VIEWPORT = 108.0
#: how far, in the 108-unit square, a dropped point may have been from the line
#: that replaces it. A hundredth of a unit is a twentieth of a pixel on the
#: densest screen there is.
CLOSE = 0.012


def edges(alpha):
    """Every crossing of the half-way line, as short segments with the shape on the left."""
    high, wide = alpha.shape
    solid = alpha >= 0.5
    out = []

    def cross(x0, y0, x1, y1):
        a, b = alpha[y0, x0], alpha[y1, x1]
        t = (0.5 - a) / (b - a)
        return (x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)

    for y in range(high - 1):
        row = solid[y] ^ solid[y + 1]
        mixed = row[:-1] | row[1:] | (solid[y, :-1] ^ solid[y, 1:]) | (solid[y + 1, :-1] ^ solid[y + 1, 1:])
        for x in numpy.nonzero(mixed)[0]:
            x = int(x)
            tl, tr = solid[y, x], solid[y, x + 1]
            bl, br = solid[y + 1, x], solid[y + 1, x + 1]
            case = (tl << 3) | (tr << 2) | (br << 1) | int(bl)
            if case in (0, 15):
                continue
            top = lambda: cross(x, y, x + 1, y)
            right = lambda: cross(x + 1, y, x + 1, y + 1)
            bottom = lambda: cross(x, y + 1, x + 1, y + 1)
            left = lambda: cross(x, y, x, y + 1)
            # each pair runs with the solid side on its left hand
            table = {
                1: [(bottom, left)], 2: [(right, bottom)], 3: [(right, left)],
                4: [(top, right)], 6: [(top, bottom)], 7: [(top, left)],
                8: [(left, top)], 9: [(bottom, top)], 11: [(right, top)],
                12: [(left, right)], 13: [(bottom, right)], 14: [(left, bottom)],
            }
            if case in (5, 10):
                # two corners across from each other: whether they join is
                # decided by what the middle of the square is
                middle = (alpha[y, x] + alpha[y, x + 1] + alpha[y + 1, x] + alpha[y + 1, x + 1]) / 4 >= 0.5
                if case == 5:
                    pairs = [(top, left), (bottom, right)] if middle else [(top, right), (bottom, left)]
                else:
                    pairs = [(left, bottom), (right, top)] if middle else [(left, top), (right, bottom)]
            else:
                pairs = table[case]
            for start, end in pairs:
                out.append((start(), end()))
    return out


def loops(segments):
    """Join the segments end to end into closed outlines."""
    key = lambda p: (round(p[0] * 4096), round(p[1] * 4096))
    after = {}
    for start, end in segments:
        after[key(start)] = (start, end)
    found = []
    while after:
        first = next(iter(after))
        at = first
        loop = []
        while at in after:
            start, end = after.pop(at)
            loop.append(start)
            at = key(end)
        if at != first:
            raise SystemExit("an outline that does not close: the picture touches its own edge")
        found.append(loop)
    return found


def fewer(points, close):
    """Drop every point that the line between its neighbours already passes."""
    def distance(p, a, b):
        dx, dy = b[0] - a[0], b[1] - a[1]
        length = math.hypot(dx, dy)
        if length == 0:
            return math.hypot(p[0] - a[0], p[1] - a[1])
        return abs(dx * (a[1] - p[1]) - dy * (a[0] - p[0])) / length

    def run(first, last):
        keep = [first, last]
        stack = [(first, last)]
        while stack:
            a, b = stack.pop()
            worst, where = 0.0, -1
            for i in range(a + 1, b):
                d = distance(points[i], points[a], points[b])
                if d > worst:
                    worst, where = d, i
            if worst > close:
                keep.append(where)
                stack += [(a, where), (where, b)]
        return keep

    # a closed outline has no ends, so it is cut at its two furthest points
    far = max(range(len(points)), key=lambda i: math.hypot(points[i][0] - points[0][0],
                                                           points[i][1] - points[0][1]))
    kept = sorted(set(run(0, far) + run(far, len(points) - 1)))
    return [points[i] for i in kept]


def trace(path=SOURCE):
    alpha = numpy.asarray(Image.open(path).convert("RGBA"), dtype=numpy.float64)[:, :, 3] / 255.0
    scale = VIEWPORT / alpha.shape[0]
    pieces = []
    contours = loops(edges(alpha))

    def area(points):
        return abs(sum(
            points[i][0] * points[(i + 1) % len(points)][1]
            - points[(i + 1) % len(points)][0] * points[i][1]
            for i in range(len(points))
        ) / 2.0)

    largest = max((area(loop) for loop in contours), default=0.0)
    # Keep the silhouette and any substantial cutouts, but skip tiny glossy
    # reflections and background-color noise that should not become vector holes.
    contours = [loop for loop in contours if area(loop) >= max(5000.0, largest * 0.01)]
    for loop in sorted(contours, key=len, reverse=True):
        # pixel centres are half a pixel in from the corner of the picture
        placed = [((x + 0.5) * scale, (y + 0.5) * scale) for x, y in loop]
        kept = fewer(placed, CLOSE)
        if len(kept) < 3:
            continue
        pieces.append("M" + " L".join("%.2f,%.2f" % point for point in kept) + " Z")
    return " ".join(pieces)


if __name__ == "__main__":
    data = trace(sys.argv[1] if len(sys.argv) > 1 else SOURCE)
    print(data)
    print("%d outlines, %d points, %d characters"
          % (data.count("M"), data.count(","), len(data)), file=sys.stderr)
    artwork = os.path.join(ROOT, "tokkiyou", "artwork.py")
    with open(artwork, encoding="utf-8") as source:
        text = source.read()
    chunks = [data[i:i + 96] for i in range(0, len(data), 96)]
    path = "_NOTE = (\n" + "".join('    "%s"\n' % chunk for chunk in chunks) + ")"
    start = text.find("_NOTE = (")
    end = text.find("\nGLYPH =", start)
    if start < 0 or end < 0:
        raise SystemExit("could not locate the generated glyph path in artwork.py")
    updated = text[:start] + path + text[end:].replace(
        "\nGLYPH = [(WHITE, _NOTE)]", "\nGLYPH = [(INK, _NOTE)]", 1)
    with open(artwork, "w", encoding="utf-8", newline="") as target:
        target.write(updated)
    print("updated", artwork, file=sys.stderr)
