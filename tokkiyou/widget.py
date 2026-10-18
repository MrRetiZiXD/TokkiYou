"""The home-screen widget: two small files and three ids.

A widget is described to the system twice over, both times in binary XML that
has to be inside the apk as a resource: what size it is and what it starts out
showing, and the layout it is drawn from. TikTok's table has neither, so both
are written here from nothing and added to the table the way the icons are --
each kind in a little package of its own, so that nothing in TikTok's moves.

The layout is one ImageView and its id is the framework's own
`android:id/background`. That saves adding an id to the table, and it is also
the id a launcher looks for to round a widget's corners.
"""

from __future__ import annotations

from typing import List

from . import axml as axml_module
from .axml import Axml, Node, StringPool

#: the packages the two kinds of file go into, next to the icons' 0x30
LAYOUT_PACKAGE = 0x31
XML_PACKAGE = 0x32
LAYOUT_ID = (LAYOUT_PACKAGE << 24) | (1 << 16)
XML_ID = (XML_PACKAGE << 24) | (1 << 16)

LAYOUT_PATH = "res/tokkiyou/widget_layout.xml"
XML_PATH = "res/tokkiyou/widget_info.xml"

#: android.R.id.background
VIEW_ID = 0x01020000

TYPE_DIMENSION = 0x05
TYPE_INT_DEC = 0x10
TYPE_INT_HEX = 0x11
DP = 1


def _dp(value: int) -> int:
    return (value << 8) | DP


def _document(element: str, attributes: List[tuple]) -> bytes:
    """One element with `android:` attributes and nothing inside it."""
    document = Axml(StringPool([], StringPool.UTF8, b"", []), [], [])
    start = document.make_element(element)
    for name, kind, data in attributes:
        document.set_attr(start, name, kind, data)
    end = document.close_element(start)
    opened = Node(axml_module.RES_XML_START_NAMESPACE, 0, axml_module.NO_ENTRY)
    closed = Node(axml_module.RES_XML_END_NAMESPACE, 0, axml_module.NO_ENTRY)
    for node in (opened, closed):
        node.prefix = document.pool.index("android")
        node.uri = document.pool.index(axml_module.ANDROID_NS)
    document.nodes = [opened, start, end, closed]
    document.pending = []
    return document.build()


def layout_xml() -> bytes:
    return _document("ImageView", [
        ("id", axml_module.TYPE_REFERENCE, VIEW_ID),
        ("layout_width", TYPE_INT_DEC, 0xFFFFFFFF),   # match_parent
        ("layout_height", TYPE_INT_DEC, 0xFFFFFFFF),
        ("scaleType", TYPE_INT_DEC, 6),               # centerCrop
    ])


def info_xml() -> bytes:
    return _document("appwidget-provider", [
        ("minWidth", TYPE_DIMENSION, _dp(110)),
        ("minHeight", TYPE_DIMENSION, _dp(110)),
        ("updatePeriodMillis", TYPE_INT_DEC, 0),      # it never changes by itself
        ("initialLayout", axml_module.TYPE_REFERENCE, LAYOUT_ID),
        ("resizeMode", TYPE_INT_HEX, 3),              # horizontal | vertical
        ("widgetCategory", TYPE_INT_HEX, 1),          # home screen
        ("targetCellWidth", TYPE_INT_DEC, 2),
        ("targetCellHeight", TYPE_INT_DEC, 2),
    ])
