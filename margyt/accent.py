"""The accent colour, baked into the places code cannot reach.

Most of TikTok's pink is reachable at runtime: a constant in the bytecode, or a
colour resource read through `Resources.getColor`, both of which the dex patch
sends through the mod. What is not reachable is the pink written into compiled
XML -- a vector's `fillColor`, a selector's item, a shape's solid -- because
the framework parses those itself, inside its own code, where nothing of ours
runs.

Those are settled here instead, while the apk is being built, and both edits
are the size-preserving kind: a colour is eight bytes of Res_value wherever it
appears, so the new one goes exactly where the old one was.
"""

from __future__ import annotations

from typing import Dict, List, Tuple

from .apkzip import Apk
from .arsc import Arsc
from .axml import Axml
from .palette import captures, map_colour

# the colour types a Res_value can be: argb8, rgb8, argb4, rgb4
COLOUR_TYPES = (0x1C, 0x1D, 0x1E, 0x1F)


def bake(apk: Apk, arsc: Arsc, old: int, new: int, pairs: Dict[int, int] = None) -> List[str]:
    """Move TikTok's red family onto the accent, in the table and in compiled XML.

    Not one value: the family, at every opacity it is used at, mapped by the
    step that takes the brand pink to the chosen colour. margyt/palette.py says
    what is taken over and what is left alone.

    Asked for the colour the apk already has, this counts instead of writing --
    and says what it counted. Those places are the ones the bytecode patch can
    never reach, because the framework parses them inside its own code, so a
    build that leaves them alone should say so rather than report nothing to do.
    """
    writing = old != new
    report = []
    if pairs is None:
        pairs = {}

    entries, shades = _walk_table(arsc, old, new, writing, pairs)

    files = 0
    changes = 0
    for name in apk.names():
        if not (name.startswith("res/") and name.endswith(".xml")):
            continue
        try:
            axml = Axml.parse(apk.read(name))
        except Exception:
            continue
        here = 0
        for node in axml.nodes:
            for attribute in node.attributes:
                if attribute.kind in COLOUR_TYPES and captures(attribute.data, old):
                    shades.add(attribute.data)
                    if writing:
                        moved = map_colour(attribute.data, old, new)
                        # what the app will now hold, and what it used to be:
                        # the runtime needs the original to know where to send it
                        pairs[moved] = attribute.data
                        attribute.data = moved
                    here += 1
        if here:
            if writing:
                apk.replace(name, axml.build())
            files += 1
            changes += here

    if writing:
        report.append("resource entries: %d" % entries)
        report.append("compiled xml: %d colours in %d files" % (changes, files))
        report.append("%d shades of the family in all, alpha kept, moved together"
                      % len(shades))
    else:
        report.append("the accent is TikTok's own, nothing to bake")
        report.append("%d in the table and %d in %d compiled xml files stay as they are"
                      % (entries, changes, files))
        report.append("those are pictures rather than code -- the palette in the "
                      "settings cannot reach them; --accent is what moves them")
    return report


def _walk_table(arsc: Arsc, old: int, new: int, writing: bool, pairs: Dict[int, int]):
    """Every typed value in the table that belongs to the family."""
    found = 0
    shades = set()
    for package in arsc.packages:
        for type_id in list(package.types):
            for entry in _entries(arsc, package, type_id):
                if entry.kind in COLOUR_TYPES and captures(entry.data, old):
                    shades.add(entry.data)
                    if writing:
                        moved = map_colour(entry.data, old, new)
                        pairs[moved] = entry.data
                        arsc.set_value(entry, entry.kind, moved)
                    found += 1
    return found, shades


def _entries(arsc: Arsc, package, type_id: int):
    """Every value of every entry of one type, across its configurations."""
    for entry_id in range(_entry_count(arsc, package, type_id)):
        res_id = (package.id << 24) | (type_id << 16) | entry_id
        for value in arsc.values(res_id):
            yield value


def _entry_count(arsc: Arsc, package, type_id: int) -> int:
    import struct

    most = 0
    for chunk in package.types.get(type_id, []):
        count = struct.unpack_from("<I", arsc.data, chunk + 12)[0]
        most = max(most, count)
    return most
