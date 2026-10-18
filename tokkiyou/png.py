"""Just enough PNG to resize an icon.

Pillow would do this in three lines, and needing Pillow is one more thing
between someone and a build. Eight-bit PNGs, not interlaced -- which is every
icon this repository ships -- decode here, scale here, and go back out as RGBA.
"""

from __future__ import annotations

import struct
import zlib
from typing import Tuple

SIGNATURE = b"\x89PNG\r\n\x1a\n"

GREY, RGB, PALETTE, GREY_ALPHA, RGBA = 0, 2, 3, 4, 6
CHANNELS = {GREY: 1, RGB: 3, PALETTE: 1, GREY_ALPHA: 2, RGBA: 4}


class PngError(Exception):
    pass


class Image:
    """Eight bits a channel, four channels, one flat bytearray."""

    def __init__(self, width: int, height: int, pixels: bytearray):
        self.width = width
        self.height = height
        self.pixels = pixels

    def resized(self, size: int) -> "Image":
        """Square resize by averaging each source region into one pixel.

        Downscaling an icon is all this is ever asked to do, and averaging is
        what keeps a thin white glyph from breaking up on the way down.
        """
        if size == self.width and size == self.height:
            return Image(size, size, bytearray(self.pixels))
        out = bytearray(size * size * 4)
        xs = [(x * self.width // size, max((x + 1) * self.width // size, x * self.width // size + 1))
              for x in range(size)]
        for y in range(size):
            y0 = y * self.height // size
            y1 = max((y + 1) * self.height // size, y0 + 1)
            for x in range(size):
                x0, x1 = xs[x]
                r = g = b = a = 0
                n = 0
                for sy in range(y0, y1):
                    row = sy * self.width * 4
                    for sx in range(x0, x1):
                        at = row + sx * 4
                        alpha = self.pixels[at + 3]
                        # weight colour by alpha so transparent pixels do not
                        # bleed their colour into the edge
                        r += self.pixels[at] * alpha
                        g += self.pixels[at + 1] * alpha
                        b += self.pixels[at + 2] * alpha
                        a += alpha
                        n += 1
                at = (y * size + x) * 4
                if a:
                    out[at] = r // a
                    out[at + 1] = g // a
                    out[at + 2] = b // a
                    out[at + 3] = a // n
        return Image(size, size, out)


def decode(data: bytes) -> Image:
    if not data.startswith(SIGNATURE):
        raise PngError("not a PNG")
    width = height = depth = color = 0
    idat = bytearray()
    palette = b""
    transparency = b""
    pos = len(SIGNATURE)
    while pos + 8 <= len(data):
        length, kind = struct.unpack_from(">I4s", data, pos)
        body = data[pos + 8 : pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, depth, color, _comp, _filter, interlace = struct.unpack(">IIBBBBB", body)
            if depth != 8:
                raise PngError("only 8 bits a channel, this one has %d" % depth)
            if interlace:
                raise PngError("interlaced PNGs are not supported")
        elif kind == b"PLTE":
            palette = body
        elif kind == b"tRNS":
            transparency = body
        elif kind == b"IDAT":
            idat += body
        elif kind == b"IEND":
            break

    if color not in CHANNELS:
        raise PngError("colour type %d is not supported" % color)
    channels = CHANNELS[color]
    raw = zlib.decompress(bytes(idat))
    lines = _unfilter(raw, width, height, channels)
    return Image(width, height, _to_rgba(lines, width, height, color, palette, transparency))


def _unfilter(raw: bytes, width: int, height: int, channels: int) -> bytearray:
    stride = width * channels
    out = bytearray(stride * height)
    previous = bytearray(stride)
    pos = 0
    for y in range(height):
        kind = raw[pos]
        pos += 1
        line = bytearray(raw[pos : pos + stride])
        pos += stride
        if kind == 1:
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 0xFF
        elif kind == 2:
            for i in range(stride):
                line[i] = (line[i] + previous[i]) & 0xFF
        elif kind == 3:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((left + previous[i]) >> 1)) & 0xFF
        elif kind == 4:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                up = previous[i]
                corner = previous[i - channels] if i >= channels else 0
                p = left + up - corner
                pa, pb, pc = abs(p - left), abs(p - up), abs(p - corner)
                if pa <= pb and pa <= pc:
                    guess = left
                elif pb <= pc:
                    guess = up
                else:
                    guess = corner
                line[i] = (line[i] + guess) & 0xFF
        elif kind != 0:
            raise PngError("unknown row filter %d" % kind)
        out[y * stride : (y + 1) * stride] = line
        previous = line
    return out


def _to_rgba(lines: bytearray, width: int, height: int, color: int, palette: bytes,
             transparency: bytes) -> bytearray:
    if color == RGBA:
        return lines
    out = bytearray(width * height * 4)
    count = width * height
    if color == RGB:
        for i in range(count):
            out[i * 4 : i * 4 + 3] = lines[i * 3 : i * 3 + 3]
            out[i * 4 + 3] = 255
    elif color == GREY:
        for i in range(count):
            v = lines[i]
            out[i * 4 : i * 4 + 4] = bytes((v, v, v, 255))
    elif color == GREY_ALPHA:
        for i in range(count):
            v, a = lines[i * 2], lines[i * 2 + 1]
            out[i * 4 : i * 4 + 4] = bytes((v, v, v, a))
    elif color == PALETTE:
        for i in range(count):
            index = lines[i]
            out[i * 4 : i * 4 + 3] = palette[index * 3 : index * 3 + 3]
            out[i * 4 + 3] = transparency[index] if index < len(transparency) else 255
    return out


def encode(image: Image) -> bytes:
    stride = image.width * 4
    raw = bytearray()
    for y in range(image.height):
        raw.append(0)  # no filtering: an icon is small and zlib does the work
        raw += image.pixels[y * stride : (y + 1) * stride]

    out = bytearray(SIGNATURE)
    out += _chunk(b"IHDR", struct.pack(">IIBBBBB", image.width, image.height, 8, RGBA, 0, 0, 0))
    out += _chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    out += _chunk(b"IEND", b"")
    return bytes(out)


def _chunk(kind: bytes, body: bytes) -> bytes:
    return (
        struct.pack(">I", len(body))
        + kind
        + body
        + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)
    )


def size_of(data: bytes) -> Tuple[int, int]:
    """The dimensions of a PNG without decoding the pixels."""
    if not data.startswith(SIGNATURE):
        raise PngError("not a PNG")
    return struct.unpack_from(">II", data, 16)
