"""Binary XML (AXML) read and write.

Android's compiled XML is a handful of chunks: a string pool, a map from
attribute name to framework resource id, and a flat stream of start/end nodes.
This module parses that into objects, lets you edit them, and writes it back.

Nothing here rebuilds resources.arsc, so every id an attribute refers to has to
be an id the apk already has. Strings are different: the pool is ours to append
to, and appended strings never disturb the indices already in use -- which is
what keeps the resource map (aligned with the first N pool entries) valid.
"""

from __future__ import annotations

import struct
from typing import List, Optional

# chunk types
RES_STRING_POOL = 0x0001
RES_XML = 0x0003
RES_XML_START_NAMESPACE = 0x0100
RES_XML_END_NAMESPACE = 0x0101
RES_XML_START_ELEMENT = 0x0102
RES_XML_END_ELEMENT = 0x0103
RES_XML_CDATA = 0x0104
RES_XML_RESOURCE_MAP = 0x0180

# Res_value types
TYPE_NULL = 0x00
TYPE_REFERENCE = 0x01
TYPE_STRING = 0x03
TYPE_INT_BOOLEAN = 0x12

ANDROID_NS = "http://schemas.android.com/apk/res/android"

# The framework attributes we need to be able to write. These never change --
# they are public ids, fixed the day each one shipped -- and the build checks
# every one of them against android.jar rather than trusting this table.
ATTR_IDS = {
    "theme": 0x01010000,
    "label": 0x01010001,
    "icon": 0x01010002,
    "name": 0x01010003,
    "exported": 0x01010010,
    "enabled": 0x0101000E,
    "configChanges": 0x0101001F,
    "launchMode": 0x0101001D,
    "roundIcon": 0x0101052C,
    "authorities": 0x01010018,
    # 16842779, read out of android.jar rather than written down: the build
    # checks every one of these against it and stops on a disagreement
    "grantUriPermissions": 0x0101001B,
    "targetActivity": 0x01010202,
    "taskAffinity": 0x01010012,
    "minSdkVersion": 0x0101020C,
    "versionCode": 0x0101021B,
    # a widget's description, its layout, and the line that points to them
    "resource": 0x01010025,
    "value": 0x01010024,
    "id": 0x010100D0,
    "layout_width": 0x010100F4,
    "layout_height": 0x010100F5,
    "scaleType": 0x0101011D,
    "minWidth": 0x0101013F,
    "minHeight": 0x01010140,
    "updatePeriodMillis": 0x01010250,
    "initialLayout": 0x01010251,
    "resizeMode": 0x01010363,
    "widgetCategory": 0x010103C4,
    "targetCellWidth": 0x01010634,
    "targetCellHeight": 0x01010635,
}

NO_ENTRY = 0xFFFFFFFF


class AxmlError(Exception):
    pass


# --------------------------------------------------------------- string pool


class StringPool:
    """The pool of every string the file mentions, in its original order.

    Strings are appended, never reordered or removed: every node and every
    entry of the resource map refers to a string by index.
    """

    SORTED = 1 << 0
    UTF8 = 1 << 8

    def __init__(self, strings: List[str], flags: int, styles: bytes, style_offsets: List[int]):
        self.strings = strings
        # a sorted pool stops being sorted the moment we append to it, and
        # nothing in a manifest relies on the flag
        self.flags = flags & ~self.SORTED
        self.styles = styles
        self.style_offsets = style_offsets

    @property
    def utf8(self) -> bool:
        return bool(self.flags & self.UTF8)

    def index(self, value: str) -> int:
        """The index of `value`, appending it to the pool if it is new."""
        try:
            return self.strings.index(value)
        except ValueError:
            self.strings.append(value)
            return len(self.strings) - 1

    def get(self, index: int) -> Optional[str]:
        if index == NO_ENTRY or index < 0 or index >= len(self.strings):
            return None
        return self.strings[index]

    # -- parsing

    @classmethod
    def parse(cls, data: bytes, start: int) -> "StringPool":
        kind, header_size, size = struct.unpack_from("<HHI", data, start)
        if kind != RES_STRING_POOL:
            raise AxmlError("expected a string pool at %d, found chunk 0x%04x" % (start, kind))
        count, style_count, flags, strings_start, styles_start = struct.unpack_from(
            "<IIIII", data, start + 8
        )
        offsets = struct.unpack_from("<%dI" % count, data, start + header_size)
        style_offsets = list(
            struct.unpack_from("<%dI" % style_count, data, start + header_size + 4 * count)
        )

        base = start + strings_start
        utf8 = bool(flags & cls.UTF8)
        strings = [_read_string(data, base + off, utf8) for off in offsets]

        styles = b""
        if style_count:
            styles = data[start + styles_start : start + size]

        return cls(strings, flags, styles, style_offsets)

    # -- writing

    def build(self) -> bytes:
        offsets = []
        blob = bytearray()
        seen = {}
        for s in self.strings:
            if s in seen:
                offsets.append(seen[s])
                continue
            seen[s] = len(blob)
            offsets.append(len(blob))
            blob += _write_string(s, self.utf8)
        while len(blob) % 4:
            blob.append(0)

        header_size = 28
        strings_start = header_size + 4 * len(offsets) + 4 * len(self.style_offsets)
        styles_start = (strings_start + len(blob)) if self.styles else 0
        size = strings_start + len(blob) + len(self.styles)

        out = bytearray()
        out += struct.pack("<HHI", RES_STRING_POOL, header_size, size)
        out += struct.pack(
            "<IIIII",
            len(offsets),
            len(self.style_offsets),
            self.flags,
            strings_start,
            styles_start,
        )
        out += struct.pack("<%dI" % len(offsets), *offsets)
        out += struct.pack("<%dI" % len(self.style_offsets), *self.style_offsets)
        out += blob
        out += self.styles
        return bytes(out)


def _read_len(data: bytes, pos: int, utf8: bool):
    """Pool lengths are 1 or 2 units, the high bit of the first marking which."""
    if utf8:
        first = data[pos]
        if first & 0x80:
            return ((first & 0x7F) << 8) | data[pos + 1], pos + 2
        return first, pos + 1
    first = struct.unpack_from("<H", data, pos)[0]
    if first & 0x8000:
        second = struct.unpack_from("<H", data, pos + 2)[0]
        return ((first & 0x7FFF) << 16) | second, pos + 4
    return first, pos + 2


def _read_string(data: bytes, pos: int, utf8: bool) -> str:
    if utf8:
        _chars, pos = _read_len(data, pos, True)  # length in utf-16 units, unused
        nbytes, pos = _read_len(data, pos, True)
        return data[pos : pos + nbytes].decode("utf-8", "replace")
    nchars, pos = _read_len(data, pos, False)
    return data[pos : pos + nchars * 2].decode("utf-16-le", "replace")


def _write_len(value: int, utf8: bool) -> bytes:
    if utf8:
        if value > 0x7F:
            return bytes([0x80 | ((value >> 8) & 0x7F), value & 0xFF])
        return bytes([value])
    if value > 0x7FFF:
        return struct.pack("<HH", 0x8000 | ((value >> 16) & 0x7FFF), value & 0xFFFF)
    return struct.pack("<H", value)


def _write_string(value: str, utf8: bool) -> bytes:
    if utf8:
        raw = value.encode("utf-8")
        chars = len(value.encode("utf-16-le")) // 2
        return _write_len(chars, True) + _write_len(len(raw), True) + raw + b"\x00"
    raw = value.encode("utf-16-le")
    return _write_len(len(raw) // 2, False) + raw + b"\x00\x00"


# --------------------------------------------------------------------- nodes


class Attribute:
    def __init__(self, ns: int, name: int, raw: int, size: int, res0: int, kind: int, data: int):
        self.ns = ns
        self.name = name
        self.raw = raw
        self.size = size
        self.res0 = res0
        self.kind = kind
        self.data = data

    def pack(self) -> bytes:
        return struct.pack(
            "<IIIHBBI", self.ns, self.name, self.raw, self.size, self.res0, self.kind, self.data
        )


class Node:
    """One chunk of the node stream: an element, a namespace, or cdata."""

    def __init__(self, kind: int, line: int, comment: int):
        self.kind = kind
        self.line = line
        self.comment = comment
        # start/end namespace and cdata
        self.prefix = 0
        self.uri = 0
        # elements
        self.ns = NO_ENTRY
        self.name = NO_ENTRY
        self.id_index = 0
        self.class_index = 0
        self.style_index = 0
        self.attributes: List[Attribute] = []
        self.tail = b""  # cdata's Res_value, kept verbatim

    def pack(self) -> bytes:
        body = bytearray()
        if self.kind in (RES_XML_START_NAMESPACE, RES_XML_END_NAMESPACE):
            body += struct.pack("<II", self.prefix, self.uri)
        elif self.kind == RES_XML_START_ELEMENT:
            body += struct.pack("<II", self.ns, self.name)
            body += struct.pack(
                "<HHHHHH",
                20,  # attributeStart: right after this 20-byte block
                20,  # attributeSize
                len(self.attributes),
                self.id_index,
                self.class_index,
                self.style_index,
            )
            for attr in self.attributes:
                body += attr.pack()
        elif self.kind == RES_XML_END_ELEMENT:
            body += struct.pack("<II", self.ns, self.name)
        elif self.kind == RES_XML_CDATA:
            body += struct.pack("<I", self.name) + self.tail
        else:
            raise AxmlError("cannot write node 0x%04x" % self.kind)

        header_size = 16
        size = header_size + len(body)
        return struct.pack("<HHIII", self.kind, header_size, size, self.line, self.comment) + bytes(
            body
        )


class Axml:
    """A parsed binary XML file."""

    def __init__(self, pool: StringPool, resource_map: List[int], nodes: List[Node]):
        self.pool = pool
        self.resource_map = resource_map
        self.nodes = nodes
        # nodes made by make_element that are not in the tree yet: they hold
        # string indices too, and a pool insert has to move theirs as well
        self.pending: List[Node] = []

    # -- parsing

    @classmethod
    def parse(cls, data: bytes) -> "Axml":
        kind, header_size, _size = struct.unpack_from("<HHI", data, 0)
        if kind != RES_XML:
            raise AxmlError("not binary XML (first chunk is 0x%04x)" % kind)

        pos = header_size
        pool = None
        resource_map: List[int] = []
        nodes: List[Node] = []

        while pos + 8 <= len(data):
            ctype, chdr, csize = struct.unpack_from("<HHI", data, pos)
            if csize < 8:
                raise AxmlError("chunk 0x%04x at %d has size %d" % (ctype, pos, csize))
            if ctype == RES_STRING_POOL:
                pool = StringPool.parse(data, pos)
            elif ctype == RES_XML_RESOURCE_MAP:
                count = (csize - chdr) // 4
                resource_map = list(struct.unpack_from("<%dI" % count, data, pos + chdr))
            elif ctype in (
                RES_XML_START_NAMESPACE,
                RES_XML_END_NAMESPACE,
                RES_XML_START_ELEMENT,
                RES_XML_END_ELEMENT,
                RES_XML_CDATA,
            ):
                nodes.append(_parse_node(data, pos, ctype, chdr, csize))
            pos += csize

        if pool is None:
            raise AxmlError("no string pool")
        return cls(pool, resource_map, nodes)

    def build(self) -> bytes:
        body = bytearray()
        body += self.pool.build()
        if self.resource_map:
            size = 8 + 4 * len(self.resource_map)
            body += struct.pack("<HHI", RES_XML_RESOURCE_MAP, 8, size)
            body += struct.pack("<%dI" % len(self.resource_map), *self.resource_map)
        for node in self.nodes:
            body += node.pack()
        return struct.pack("<HHI", RES_XML, 8, 8 + len(body)) + bytes(body)

    # -- reading

    def string(self, index: int) -> Optional[str]:
        return self.pool.get(index)

    def elements(self, name: str) -> List[Node]:
        return [
            n
            for n in self.nodes
            if n.kind == RES_XML_START_ELEMENT and self.pool.get(n.name) == name
        ]

    def attr(self, node: Node, name: str) -> Optional[Attribute]:
        for a in node.attributes:
            if self.pool.get(a.name) == name and self.pool.get(a.ns) in (ANDROID_NS, None):
                return a
        return None

    def attr_string(self, node: Node, name: str) -> Optional[str]:
        a = self.attr(node, name)
        if a is None:
            return None
        if a.kind == TYPE_STRING:
            return self.pool.get(a.data)
        return self.pool.get(a.raw)

    # -- editing

    def attribute_name_index(self, name: str) -> int:
        """The pool index for an `android:` attribute name.

        The resource map runs parallel to the first entries of the pool, so an
        attribute name has to sit at the index whose map entry is its id. An
        attribute the file already uses is simply found there. One it does not
        is inserted at the end of the map -- in the middle of the pool -- and
        every index above it is shifted up to match.
        """
        if name not in ATTR_IDS:
            raise AxmlError("unknown framework attribute: %s" % name)
        wanted = ATTR_IDS[name]
        for i, rid in enumerate(self.resource_map):
            if rid == wanted:
                if self.pool.get(i) != name:
                    raise AxmlError(
                        "resource map says %d is %s, the pool says %r"
                        % (i, name, self.pool.get(i))
                    )
                return i

        at = len(self.resource_map)
        self.pool.strings.insert(at, name)
        self.resource_map.append(wanted)
        self._shift_indices(at)
        return at

    def _shift_indices(self, at: int) -> None:
        """Move every string reference at or above `at` up by one."""

        def bump(index: int) -> int:
            return index + 1 if index != NO_ENTRY and index >= at else index

        seen = set()
        for node in self.nodes + self.pending:
            if id(node) in seen:
                continue
            seen.add(id(node))
            if node.kind in (RES_XML_START_NAMESPACE, RES_XML_END_NAMESPACE):
                node.prefix = bump(node.prefix)
                node.uri = bump(node.uri)
                continue
            node.ns = bump(node.ns)
            node.name = bump(node.name)
            for a in node.attributes:
                a.ns = bump(a.ns)
                a.name = bump(a.name)
                a.raw = bump(a.raw)
                if a.kind == TYPE_STRING:
                    a.data = bump(a.data)
            if node.kind == RES_XML_CDATA and len(node.tail) == 8:
                size, res0, kind, data = struct.unpack("<HBBI", node.tail)
                if kind == TYPE_STRING:
                    node.tail = struct.pack("<HBBI", size, res0, kind, bump(data))

    def set_attr(self, node: Node, name: str, kind: int, data: int, raw: int = NO_ENTRY) -> None:
        """Set an `android:` attribute, replacing it if the element has it."""
        name_index = self.attribute_name_index(name)
        ns_index = self.pool.index(ANDROID_NS)
        existing = self.attr(node, name)
        if existing is not None:
            existing.ns = ns_index
            existing.name = name_index
            existing.raw = raw
            existing.size = 8
            existing.res0 = 0
            existing.kind = kind
            existing.data = data
            return
        attr = Attribute(ns_index, name_index, raw, 8, 0, kind, data)
        # attributes are expected in ascending resource-id order
        ids = [ATTR_IDS.get(self.pool.get(a.name) or "", 0) for a in node.attributes]
        where = len(node.attributes)
        for i, rid in enumerate(ids):
            if rid and rid > ATTR_IDS[name]:
                where = i
                break
        node.attributes.insert(where, attr)

    def set_attr_string(self, node: Node, name: str, value: str) -> None:
        # the attribute's own name goes in first: adding one renumbers the pool
        # from the end of the resource map up, and a value index looked up
        # before that would be pointing one string to the left afterwards
        self.attribute_name_index(name)
        index = self.pool.index(value)
        self.set_attr(node, name, TYPE_STRING, index, raw=index)

    def set_attr_bool(self, node: Node, name: str, value: bool) -> None:
        self.set_attr(node, name, TYPE_INT_BOOLEAN, 0xFFFFFFFF if value else 0)

    def set_attr_ref(self, node: Node, name: str, res_id: int) -> None:
        self.set_attr(node, name, TYPE_REFERENCE, res_id)

    def make_element(self, name: str) -> Node:
        node = Node(RES_XML_START_ELEMENT, 0, NO_ENTRY)
        node.ns = NO_ENTRY
        node.name = self.pool.index(name)
        self.pending.append(node)
        return node

    def close_element(self, start: Node) -> Node:
        node = Node(RES_XML_END_ELEMENT, 0, NO_ENTRY)
        node.ns = start.ns
        node.name = start.name
        self.pending.append(node)
        return node

    def insert_into(self, parent: Node, subtree: List[Node]) -> None:
        """Put a subtree in as the last child of `parent`."""
        depth = 0
        for i in range(self.nodes.index(parent) + 1, len(self.nodes)):
            node = self.nodes[i]
            if node.kind == RES_XML_START_ELEMENT:
                depth += 1
            elif node.kind == RES_XML_END_ELEMENT:
                if depth == 0:
                    self.nodes[i:i] = subtree
                    return
                depth -= 1
        raise AxmlError("element has no end tag")


def _parse_node(data: bytes, pos: int, ctype: int, chdr: int, csize: int) -> Node:
    line, comment = struct.unpack_from("<II", data, pos + 8)
    node = Node(ctype, line, comment)
    body = pos + chdr
    if ctype in (RES_XML_START_NAMESPACE, RES_XML_END_NAMESPACE):
        node.prefix, node.uri = struct.unpack_from("<II", data, body)
    elif ctype == RES_XML_START_ELEMENT:
        node.ns, node.name = struct.unpack_from("<II", data, body)
        attr_start, attr_size, attr_count, node.id_index, node.class_index, node.style_index = (
            struct.unpack_from("<HHHHHH", data, body + 8)
        )
        for i in range(attr_count):
            at = body + attr_start + i * attr_size
            ns, name, raw, size, res0, kind, value = struct.unpack_from("<IIIHBBI", data, at)
            node.attributes.append(Attribute(ns, name, raw, size, res0, kind, value))
    elif ctype == RES_XML_END_ELEMENT:
        node.ns, node.name = struct.unpack_from("<II", data, body)
    elif ctype == RES_XML_CDATA:
        node.name = struct.unpack_from("<I", data, body)[0]
        node.tail = data[body + 4 : pos + csize]
    return node
