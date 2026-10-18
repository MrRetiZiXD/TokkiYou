"""Which colours TikTok itself changes when the theme changes.

The obvious place to look is the wrong one. A colour that differs between
light and dark is normally a resource with a `night` variant, and TikTok has
twenty-two of those in the whole apk -- nowhere near a theme. Nor are there
names to go by: the resource names are stripped from this build.

The theme is in the styles. Every colour in TikTok's design system is a theme
attribute, and the two themes are two styles that give the same attribute two
different values -- `#FF121212` against `#FFFFFFFF`, `#1AFFFFFF` against
`#1A161823`. An attribute that holds exactly one colour is not part of the
switch; an attribute that holds two is, and both of those values are colours
the app repaints when the theme changes.

So this reads the style table and collects those values. That set, and nothing
else, is what the mod's theming is allowed to touch -- which is the whole of
the rule: repaint a colour only where TikTok would have repainted it.

What each value becomes is decided while the app runs, not here: the mod knows
which theme is on, and a colour's distance from that theme's own background is
the distance it keeps from the chosen one.
"""

from __future__ import annotations

import struct
from typing import List, Set, Tuple

from .arsc import Arsc
from .palette import captures

COLOUR_TYPES = (0x1C, 0x1D, 0x1E, 0x1F)

FLAG_SPARSE = 0x01
FLAG_OFFSET16 = 0x02
ENTRY_FLAG_COMPLEX = 0x0001


def theme_colours(arsc: Arsc, accent_reference: int) -> Tuple[List[int], List[int]]:
    """The theme's colours, and the subset that means nothing else.

    Two lists, because knowing a colour belongs to the theme is not the same as
    knowing that *this* use of it is the theme's. `#FFFFFFFF` is the dark
    theme's text and it is also the white of an icon over a video and the white
    of a photo's background; `#FF1B1B1B` is a card and is nothing else in the
    whole apk.

    So a colour that appears nowhere except in a theme token is repainted
    wherever it turns up. One that is used elsewhere as an ordinary colour is
    repainted only where it arrives *as* a theme colour -- read from a theme
    attribute or a colour resource -- and left alone when some drawing code
    simply asked for white.
    """
    holds = {}
    for package in arsc.packages:
        for type_id, chunks in package.types.items():
            name = package.type_names.get(type_id - 1) if package.type_names else None
            if name != "style":
                continue
            for chunk in chunks:
                for attribute, value in _bag_colours(arsc, chunk):
                    holds.setdefault(attribute, set()).add(value)

    out: Set[int] = set()
    for values in holds.values():
        # two for a plain light/dark pair, and up to four because some tokens
        # carry a variant or two beside them
        if not 2 <= len(values) <= 4:
            continue
        # the same colour at several opacities is one colour, not two themes
        if len(set(value & 0xFFFFFF for value in values)) < 2:
            continue
        for value in values:
            # the brand colour is the accent's to move, not the theme's
            if captures(value, accent_reference):
                continue
            out.add(value)

    # and the handful that are done the ordinary way, with a `night` variant
    out |= _night_pairs(arsc, accent_reference)

    elsewhere = _ordinary_colours(arsc)
    safe = sorted(value for value in out if value not in elsewhere)
    return sorted(out), safe


def _night_pairs(arsc: Arsc, accent_reference: int) -> Set[int]:
    """Colours that do have a `night` variant, few as they are.

    Twenty-two resources in this apk, which is nothing next to the style table
    -- but they are unambiguously the theme's, so there is no reason to leave
    them out.
    """
    from .accent import _entry_count

    out: Set[int] = set()
    for package in arsc.packages:
        for type_id in list(package.types):
            name = package.type_names.get(type_id - 1) if package.type_names else None
            if name != "color":
                continue
            for entry_id in range(_entry_count(arsc, package, type_id)):
                res_id = (package.id << 24) | (type_id << 16) | entry_id
                lit = dark = None
                for value in arsc.values(res_id):
                    if value.kind not in COLOUR_TYPES:
                        continue
                    if _is_night(value.config):
                        dark = value.data
                    elif not any(value.config[4:]):
                        lit = value.data
                if lit is None or dark is None or lit == dark:
                    continue
                for value in (lit, dark):
                    if not captures(value, accent_reference):
                        out.add(value)
    return out


# ResTable_config: screenLayout at 28, uiMode at 29
_UI_MODE = 29
_NIGHT_MASK = 0x30
_NIGHT_YES = 0x20


def _is_night(config: bytes) -> bool:
    if len(config) <= _UI_MODE:
        return False
    return (config[_UI_MODE] & _NIGHT_MASK) == _NIGHT_YES


def _ordinary_colours(arsc: Arsc) -> Set[int]:
    """Every colour the table holds outside a theme, as a plain resource."""
    from .accent import _entry_count

    seen: Set[int] = set()
    for package in arsc.packages:
        for type_id in list(package.types):
            name = package.type_names.get(type_id - 1) if package.type_names else None
            if name not in ("color", "drawable"):
                continue
            for entry_id in range(_entry_count(arsc, package, type_id)):
                res_id = (package.id << 24) | (type_id << 16) | entry_id
                for value in arsc.values(res_id):
                    if value.kind in COLOUR_TYPES:
                        seen.add(value.data)
    return seen


def _bag_colours(arsc: Arsc, chunk: int):
    """Every (attribute, colour) a style chunk sets."""
    data = arsc.data
    _kind, header_size, _size = struct.unpack_from("<HHI", data, chunk)
    flags = data[chunk + 9]
    count, entries_start = struct.unpack_from("<II", data, chunk + 12)
    table = chunk + header_size

    for index in range(count):
        if flags & FLAG_SPARSE:
            return  # nothing in this apk's styles is sparse; guessing is worse
        if flags & FLAG_OFFSET16:
            offset = struct.unpack_from("<H", data, table + index * 2)[0]
            if offset == 0xFFFF:
                continue
            offset *= 4
        else:
            offset = struct.unpack_from("<I", data, table + index * 4)[0]
            if offset == 0xFFFFFFFF:
                continue

        entry = chunk + entries_start + offset
        size, entry_flags = struct.unpack_from("<HH", data, entry)
        if not entry_flags & ENTRY_FLAG_COMPLEX:
            continue
        how_many = struct.unpack_from("<I", data, entry + 12)[0]
        at = entry + size
        for i in range(how_many):
            attribute = struct.unpack_from("<I", data, at + i * 12)[0]
            _size, _pad, kind, value = struct.unpack_from("<HBBI", data, at + i * 12 + 4)
            if kind in COLOUR_TYPES:
                yield attribute, value
