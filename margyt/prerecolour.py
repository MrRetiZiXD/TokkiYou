"""Like animations recoloured while the apk is being built.

At runtime the mod can recolour TikTok's Lottie hearts for any colour, but
doing it takes real work (parse the JSON, repaint every PNG frame, encode it
all again). For the colours a person is most likely to pick -- TikTok's own
and the seven accents of the palette -- that work is done here instead, once,
and the finished files ship inside the apk as assets:

    assets/margyt/rc/<RRGGBB>/<first 16 hex of sha1(lower-case name)>.json

The mod looks there first, so even the very first launch after installing
shows an already recoloured heart. Any other colour (a custom one) still goes
through the runtime path and is kept in the on-disk cache afterwards.

This mirrors Accent.recolourLottie / recolourGradientFills /
embedFeedLikeImages / recolourLikePng in the Java mod; keep them in step.
"""

from __future__ import annotations

import base64
import hashlib
import json
import math
import re
from typing import Callable, Dict, List, Optional, Tuple

from . import png
from .palette import captures, map_colour

TIKTOK = 0xFFFE2C55
# Accent.PALETTE in Accent.java
PALETTE = [0xFF6750A4, 0xFF0B57D0, 0xFF006A6A, 0xFF146C2E, 0xFF8B5000, 0xFFB3261E, 0xFF984061]
COLOURS = [TIKTOK] + PALETTE

MAX_JSON = 1024 * 1024
MAX_PNG = 2 * 1024 * 1024
ROOT = "assets/margyt/rc/"

_PINK = re.compile(r"(?i)#(?:[0-9a-f]{2})?fe2c55")
_NUMBER = r"(-?[0-9.]+(?:[eE][-+]?[0-9]+)?)"
_COLOUR = re.compile(r'"(k|s|e)"\s*:\s*\[\s*' + _NUMBER + r"\s*," + r"\s*" + _NUMBER
                     + r"\s*," + r"\s*" + _NUMBER + r"\s*," + r"\s*" + _NUMBER + r"\s*\]")


def is_like(name: str) -> bool:
    """Accent.isLikeAsset."""
    if "dislike" in name:
        return False
    return any(word in name for word in ("like", "heart", "digg", "double_tap", "doubletap"))


def shipped_path(name: str, colour: int) -> str:
    key = hashlib.sha1(name.lower().encode("utf-8")).hexdigest()[:16]
    return "%s%06X/%s.json" % (ROOT, colour & 0xFFFFFF, key)


def _round(value: float) -> int:
    return int(math.floor(value + 0.5))


def _clamp_channel(value: float) -> int:
    return max(0, min(255, _round(value)))


def _number(channel: int) -> str:
    return "%.8f" % (channel / 255.0)


def recolour_png(data: bytes, target: int) -> bytes:
    """Accent.recolourLikePng: every visible pixel moved by the palette step."""
    image = png.decode(data)
    pixels = image.pixels
    memo: Dict[int, int] = {}
    for at in range(0, len(pixels), 4):
        alpha = pixels[at + 3]
        if not alpha:
            continue
        argb = (alpha << 24) | (pixels[at] << 16) | (pixels[at + 1] << 8) | pixels[at + 2]
        now = memo.get(argb)
        if now is None:
            now = map_colour(argb, TIKTOK, target)
            memo[argb] = now
        pixels[at] = (now >> 16) & 255
        pixels[at + 1] = (now >> 8) & 255
        pixels[at + 2] = now & 255
        pixels[at + 3] = (now >> 24) & 255
    return png.encode(image)


def recolour_json(text: str, colour: int, double_tap: bool) -> str:
    """Accent.recolourLottie for a like animation (like=true)."""
    def pink(match: "re.Match[str]") -> str:
        was = match.group(0)
        original = 0xFF000000 | int(was[-6:], 16)
        now = map_colour(original, TIKTOK, colour) if colour != TIKTOK else original
        hexed = "%06X" % (now & 0xFFFFFF)
        return "#" + was[1:3] + hexed if len(was) == 9 else "#" + hexed

    text = _PINK.sub(pink, text)
    if colour == TIKTOK:
        return text

    def flat(match: "re.Match[str]") -> str:
        red, green, blue = (float(match.group(i)) for i in (2, 3, 4))
        original = 0xFF000000 | (_round(red * 255) << 16) | (_round(green * 255) << 8) | _round(blue * 255)
        white = red > 0.97 and green > 0.97 and blue > 0.97
        if not captures(original, TIKTOK) and not (double_tap and white):
            return match.group(0)
        now = colour if (double_tap and white) else map_colour(original, TIKTOK, colour)
        return '"%s":[%s,%s,%s,%s]' % (match.group(1), _number((now >> 16) & 255),
                                       _number((now >> 8) & 255), _number(now & 255),
                                       match.group(5))

    text = _COLOUR.sub(flat, text)
    return _gradients(text, colour)


def _gradients(text: str, target: int) -> str:
    if '"gf"' not in text and '"gs"' not in text:
        return text
    try:
        root = json.loads(text)
    except ValueError:
        return text
    changed = [0]
    _walk(root, target, changed)
    return text if not changed[0] else json.dumps(root, separators=(",", ":"))


def _walk(node, target: int, changed: List[int]) -> None:
    if isinstance(node, list):
        for child in node:
            if isinstance(child, (dict, list)):
                _walk(child, target, changed)
    elif isinstance(node, dict):
        if node.get("ty") in ("gf", "gs"):
            stops = node.get("g")
            if isinstance(stops, dict) and isinstance(stops.get("k"), dict):
                _stop_property(stops["k"], int(stops.get("p", 0) or 0), target, changed)
        for child in list(node.values()):
            if isinstance(child, (dict, list)):
                _walk(child, target, changed)


def _stop_property(prop: dict, stops: int, target: int, changed: List[int]) -> None:
    values = prop.get("k")
    if not isinstance(values, list) or not values:
        return
    if isinstance(values[0], dict):
        for frame in values:
            if isinstance(frame, dict):
                _stop_list(frame.get("s"), stops, target, changed)
                _stop_list(frame.get("e"), stops, target, changed)
    else:
        _stop_list(values, stops, target, changed)


def _stop_list(items, stops: int, target: int, changed: List[int]) -> None:
    if not isinstance(items, list):
        return
    end = min(stops * 4, len(items)) if stops > 0 else len(items) // 4 * 4
    top = 0.0
    for i in range(0, end - 3, 4):
        for c in (1, 2, 3):
            top = max(top, _opt(items, i + c))
    scale = 1.0 if top > 1.0001 else 255.0
    mul = 1 if scale == 1.0 else 255
    for i in range(0, end - 3, 4):
        original = (0xFF000000
                    | (_clamp_channel(_opt(items, i + 1) * mul) << 16)
                    | (_clamp_channel(_opt(items, i + 2) * mul) << 8)
                    | _clamp_channel(_opt(items, i + 3) * mul))
        if not captures(original, TIKTOK):
            continue
        now = map_colour(original, TIKTOK, target)
        if now == original:
            continue
        items[i + 1] = ((now >> 16) & 255) / scale
        items[i + 2] = ((now >> 8) & 255) / scale
        items[i + 3] = (now & 255) / scale
        changed[0] += 1


def _opt(items, at: int) -> float:
    try:
        return float(items[at])
    except (TypeError, ValueError, IndexError):
        return 0.0


def embed_images(text: str, colour: int, read: Callable[[str], Optional[bytes]]) -> str:
    """Accent.embedFeedLikeImages: frames inlined as base64, recoloured."""
    try:
        composition = json.loads(text)
    except ValueError:
        return text
    if not isinstance(composition, dict) or not isinstance(composition.get("assets"), list):
        return text
    changed = False
    for image in composition["assets"]:
        if not isinstance(image, dict):
            continue
        name = str(image.get("p", ""))
        if not name.lower().endswith(".png") or name.startswith("data:"):
            continue
        raw = read(str(image.get("u", "")) + name)
        if raw is None:
            continue
        data = raw if colour == TIKTOK else recolour_png(raw, colour)
        if not data or len(data) > MAX_PNG:
            continue
        image["u"] = ""
        image["p"] = "data:image/png;base64," + base64.b64encode(data).decode("ascii")
        image["e"] = 1
        changed = True
    return json.dumps(composition, separators=(",", ":")) if changed else text


def make(name: str, source: bytes, colour: int,
         read: Callable[[str], Optional[bytes]]) -> Optional[bytes]:
    """The finished JSON for one animation and colour, or None to leave it to the app."""
    if len(source) > MAX_JSON:
        return None
    text = source.decode("utf-8-sig")
    if not text.lstrip().startswith(("{", "[")):
        return None
    lower = name.lower()
    double_tap = "double_tap" in lower or "doubletap" in lower
    text = recolour_json(text, colour, double_tap)
    text = embed_images(text, colour, read)
    return text.encode("utf-8")


def candidates(names: List[str]) -> List[Tuple[str, str]]:
    """(apk path, asset name) of the like animations TikTok ships."""
    out = []
    for path in names:
        if not path.startswith("assets/") or path.startswith("assets/margyt/"):
            continue
        name = path[len("assets/"):]
        lower = name.lower()
        if lower.endswith(".json") and is_like(lower):
            out.append((path, name))
    return out
