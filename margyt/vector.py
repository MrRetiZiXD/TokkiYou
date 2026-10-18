"""Compiled vector drawables, written straight out as binary XML.

Android's aapt2 would compile these from source XML, and this build does not
run aapt2 -- so the compiled form is assembled here. The framework attribute
ids below are the ones TikTok's own vector drawables use; the build reads them
back out of the apk and checks them against this table before writing anything.
"""

from __future__ import annotations

import struct
from typing import List, Sequence, Tuple

from .axml import (
    ANDROID_NS,
    Attribute,
    Axml,
    Node,
    NO_ENTRY,
    RES_XML_END_ELEMENT,
    RES_XML_END_NAMESPACE,
    RES_XML_START_ELEMENT,
    RES_XML_START_NAMESPACE,
    StringPool,
    TYPE_STRING,
)

TYPE_FLOAT = 0x04
TYPE_DIMENSION = 0x05
TYPE_INT_COLOR_ARGB8 = 0x1C

# android.R.attr, fixed since the day each attribute shipped
ATTR_HEIGHT = 0x01010155
ATTR_WIDTH = 0x01010159
ATTR_VIEWPORT_WIDTH = 0x01010402
ATTR_VIEWPORT_HEIGHT = 0x01010403
ATTR_FILL_COLOR = 0x01010404
ATTR_PATH_DATA = 0x01010405

ATTR_NAMES = ["height", "width", "viewportWidth", "viewportHeight", "fillColor", "pathData"]
ATTR_IDS = [
    ATTR_HEIGHT,
    ATTR_WIDTH,
    ATTR_VIEWPORT_WIDTH,
    ATTR_VIEWPORT_HEIGHT,
    ATTR_FILL_COLOR,
    ATTR_PATH_DATA,
]

DIP = 1  # the unit part of a complex dimension


def dimension_dp(value: int) -> int:
    """A dimension in dp, in the packed form Res_value keeps them in."""
    return (value << 8) | DIP


def float_bits(value: float) -> int:
    return struct.unpack("<I", struct.pack("<f", value))[0]


def build(size: int, paths: Sequence[Tuple[int, str]]) -> bytes:
    """A `<vector>` of `size` dp, one `<path>` per (colour, path data) pair."""
    strings = list(ATTR_NAMES)
    strings += [data for _colour, data in paths]
    android = len(strings)
    strings.append("android")
    uri = len(strings)
    strings.append(ANDROID_NS)
    path_name = len(strings)
    strings.append("path")
    vector_name = len(strings)
    strings.append("vector")

    pool = StringPool(strings, StringPool.UTF8, b"", [])
    axml = Axml(pool, list(ATTR_IDS), [])

    open_ns = Node(RES_XML_START_NAMESPACE, 1, NO_ENTRY)
    open_ns.prefix, open_ns.uri = android, uri

    vector = Node(RES_XML_START_ELEMENT, 1, NO_ENTRY)
    vector.name = vector_name
    vector.attributes = [
        Attribute(uri, 0, NO_ENTRY, 8, 0, TYPE_DIMENSION, dimension_dp(size)),
        Attribute(uri, 1, NO_ENTRY, 8, 0, TYPE_DIMENSION, dimension_dp(size)),
        Attribute(uri, 2, NO_ENTRY, 8, 0, TYPE_FLOAT, float_bits(float(size))),
        Attribute(uri, 3, NO_ENTRY, 8, 0, TYPE_FLOAT, float_bits(float(size))),
    ]

    nodes: List[Node] = [open_ns, vector]
    for index, (colour, _data) in enumerate(paths):
        element = Node(RES_XML_START_ELEMENT, 1, NO_ENTRY)
        element.name = path_name
        string_index = len(ATTR_NAMES) + index
        element.attributes = [
            Attribute(uri, 4, NO_ENTRY, 8, 0, TYPE_INT_COLOR_ARGB8, colour),
            Attribute(uri, 5, string_index, 8, 0, TYPE_STRING, string_index),
        ]
        nodes.append(element)
        closing = Node(RES_XML_END_ELEMENT, 1, NO_ENTRY)
        closing.name = path_name
        nodes.append(closing)

    close_vector = Node(RES_XML_END_ELEMENT, 1, NO_ENTRY)
    close_vector.name = vector_name
    nodes.append(close_vector)

    close_ns = Node(RES_XML_END_NAMESPACE, 1, NO_ENTRY)
    close_ns.prefix, close_ns.uri = android, uri
    nodes.append(close_ns)

    axml.nodes = nodes
    return axml.build()
