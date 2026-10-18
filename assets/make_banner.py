#!/usr/bin/env python3
"""Render assets/banner.png in TokkiYou's purple palette."""

import pathlib
import os

from PIL import Image, ImageDraw, ImageFont

HERE = pathlib.Path(__file__).parent

W, H = 900, 260
MINT = (0xC6, 0xA4, 0xFF)
INK = (0x5E, 0x2B, 0xCE)
INK_SOFT = (0x5E, 0x2B, 0xCE)
WHITE = (255, 255, 255)

BOLD = r"C:\Windows\Fonts\arialbd.ttf"
REGULAR = r"C:\Windows\Fonts\arial.ttf"

WORDMARK = "TokkiYou"
SUBTITLE = "a TikTok mod for Android"


def note(draw, cx, cy, scale):
    """Beamed eighth note, same geometry as the launcher icon."""

    def p(x, y):
        return (cx + (x - 54) * scale, cy + (y - 53) * scale)

    for hx, hy in ((37, 76), (71, 68)):
        draw.ellipse([p(hx - 11, hy - 8), p(hx + 11, hy + 8)], fill=WHITE)
    draw.polygon([p(42, 30), p(48, 30), p(48, 76), p(42, 76)], fill=WHITE)
    draw.polygon([p(76, 22), p(82, 22), p(82, 68), p(76, 68)], fill=WHITE)
    draw.polygon([p(42, 30), p(82, 22), p(82, 34), p(42, 42)], fill=WHITE)


def main():
    im = Image.new("RGB", (W, H), MINT)
    d = ImageDraw.Draw(im)

    # faint rounded outline around the glyph
    d.rounded_rectangle(
        [65, 32, 250, 217], radius=58, outline=(0xD8, 0xC8, 0xFF), width=2
    )
    note(d, 157, 124, 0.95)

    bold = BOLD if os.path.isfile(BOLD) else HERE / "noto.ttf"
    regular = REGULAR if os.path.isfile(REGULAR) else HERE / "noto.ttf"
    wordmark = ImageFont.truetype(str(bold), 82)
    subtitle = ImageFont.truetype(str(regular), 30)

    d.text((285, 74), WORDMARK, font=wordmark, fill=INK)
    d.text((288, 168), SUBTITLE, font=subtitle, fill=INK_SOFT)

    out = HERE / "banner.png"
    im.save(out)
    print("wrote", out, im.size)


if __name__ == "__main__":
    main()
