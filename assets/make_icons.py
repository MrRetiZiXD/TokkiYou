#!/usr/bin/env python3
"""Make TokkiYou launcher artwork from the supplied square image.

The rendered source artwork is preserved for launcher icons and only
downsampled to Android's standard raster sizes. Its foreground is also
extracted so adaptive icons retain a transparent glyph layer.
"""

import argparse
from pathlib import Path

import numpy
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
BACKGROUND = (0xC6, 0xA4, 0xFF)
FOREGROUND = (0x5E, 0x2B, 0xCE)
ART_SCALE = 0.70
DENSITIES = {
    "mdpi": (48, 48),
    "hdpi": (72, 72),
    "xhdpi": (96, 96),
    "xxhdpi": (144, 144),
    "xxxhdpi": (192, 192),
}
ICON_OPTIONS = {
    "yellow": ROOT / "assets" / "icon-options" / "yellow.png",
    "neon": ROOT / "assets" / "icon-options" / "neon.png",
}


def make_glyph(source):
    rgba = source.convert("RGBA")
    rgb = numpy.asarray(rgba.convert("RGB"), dtype=numpy.float32)
    border = max(4, rgba.width // 64)
    edges = numpy.concatenate((
        rgb[:border, :, :].reshape(-1, 3), rgb[-border:, :, :].reshape(-1, 3),
        rgb[:, :border, :].reshape(-1, 3), rgb[:, -border:, :].reshape(-1, 3),
    ))
    background = numpy.median(edges, axis=0)
    distance = numpy.sqrt(numpy.sum((rgb - background) ** 2, axis=2))
    alpha = numpy.clip((distance - 14.0) / 36.0, 0.0, 1.0)
    alpha = (alpha * 255.0).astype(numpy.uint8)
    layer = Image.new("RGBA", rgba.size, FOREGROUND + (0,))
    layer.putalpha(Image.fromarray(alpha, mode="L").filter(ImageFilter.GaussianBlur(0.45)))
    return layer


def composite(glyph, size):
    background = Image.new("RGBA", size, BACKGROUND + (255,))
    front = glyph.resize(size, Image.Resampling.LANCZOS)
    return Image.alpha_composite(background, front)


def artwork_image(source, size):
    source = source.resize(size, Image.Resampling.LANCZOS)
    border = max(1, size[0] // 64)
    edges = numpy.concatenate((
        numpy.asarray(source.convert("RGB"))[:border, :, :].reshape(-1, 3),
        numpy.asarray(source.convert("RGB"))[-border:, :, :].reshape(-1, 3),
        numpy.asarray(source.convert("RGB"))[:, :border, :].reshape(-1, 3),
        numpy.asarray(source.convert("RGB"))[:, -border:, :].reshape(-1, 3),
    ))
    background = tuple(int(value) for value in numpy.median(edges, axis=0))
    canvas = Image.new("RGBA", size, background + (255,))
    inner_size = tuple(max(1, round(value * ART_SCALE)) for value in size)
    inner = source.resize(inner_size, Image.Resampling.LANCZOS)
    canvas.alpha_composite(inner, ((size[0] - inner_size[0]) // 2,
                                   (size[1] - inner_size[1]) // 2))
    return canvas


def inset_glyph(glyph):
    inner_size = tuple(max(1, round(value * ART_SCALE)) for value in glyph.size)
    inner = glyph.resize(inner_size, Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", glyph.size, (0, 0, 0, 0))
    canvas.alpha_composite(inner, ((glyph.width - inner.width) // 2,
                                   (glyph.height - inner.height) // 2))
    return canvas


def round_icon(image):
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, image.width - 1, image.height - 1), fill=255)
    rounded = image.copy()
    rounded.putalpha(mask)
    return rounded


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", nargs="?", default=str(ROOT / "assets" / "tokkiyou-source.png"))
    args = parser.parse_args()
    source = Image.open(args.source).convert("RGBA")
    if source.width != source.height:
        raise SystemExit("TokkiYou source artwork must be square; refusing to crop it")

    glyph = inset_glyph(make_glyph(source))
    icon = artwork_image(source, (1024, 1024))
    output = ROOT / "icon_out"
    output.mkdir(parents=True, exist_ok=True)
    icon.save(output / "tokkiyou-1024.png", optimize=True)
    glyph.save(output / "tokkiyou-1024-glyph.png", optimize=True)

    play = output / "play"
    play.mkdir(exist_ok=True)
    play_icon = artwork_image(source, (512, 512))
    play_icon.save(play / "ic_launcher-512.png", optimize=True)
    round_icon(play_icon).save(play / "ic_launcher-512-round.png", optimize=True)

    for density, size in DENSITIES.items():
        folder = output / ("mipmap-" + density)
        folder.mkdir(exist_ok=True)
        square = artwork_image(source, size)
        square.save(folder / "ic_launcher.png", optimize=True)
        round_icon(square).save(folder / "ic_launcher_round.png", optimize=True)
        Image.new("RGBA", size, BACKGROUND + (255,)).save(
            folder / "ic_launcher_background.png", optimize=True)
        foreground_size = (size[0] * 9 // 4, size[1] * 9 // 4)
        glyph.resize(foreground_size, Image.Resampling.LANCZOS).save(
            folder / "ic_launcher_foreground.png", optimize=True)

    launcher = ROOT / "icons" / "launcher"
    launcher.mkdir(parents=True, exist_ok=True)
    for name, path in ICON_OPTIONS.items():
        option = Image.open(path).convert("RGBA")
        if option.width != option.height:
            raise SystemExit("%s icon must be square; refusing to crop it" % name)
        option.resize((512, 512), Image.Resampling.LANCZOS).save(
            launcher / (name + ".png"), optimize=True)

    print("TokkiYou icons written to", output)


if __name__ == "__main__":
    main()
