"""Add resources to somebody else's resource table.

Everything else this build does to `resources.arsc` is an overwrite: a colour
becomes another colour of the same width, a path becomes a path of the same
length. Nothing grows, which is why nothing has to be moved.

An icon you can choose is different. Android decides an app's launcher icon
from the manifest, one icon per component, so offering eight of them means
eight `activity-alias` entries and eight icons for them to point at -- and
those icons do not exist in TikTok's table. They have to be added.

Two things grow, then. The table's string pool gains the paths of the new
files, and the table gains a package of its own to hold the entries. A new
package is used rather than a new type inside TikTok's, because a package is a
self-contained chunk that can simply be appended: nothing inside anybody
else's chunk moves, and if the id is wrong the worst case is that eight
resources do not resolve rather than that eight thousand shift.
"""

from __future__ import annotations

import struct
from typing import Dict, List

RES_TABLE = 0x0002
RES_STRING_POOL = 0x0001
RES_TABLE_PACKAGE = 0x0200
RES_TABLE_TYPE = 0x0201
RES_TABLE_TYPE_SPEC = 0x0202

TYPE_STRING = 0x03
UTF8_FLAG = 1 << 8


def add_files(data: bytes, package_id: int, type_name: str,
              names_and_paths: List[tuple], package_name: str = "tokkiyou") -> bytes:
    """Add one package holding a file resource per (name, path) given.

    The ids handed out are `package_id<<24 | 1<<16 | n`, in the order given.
    A second package added to the same table is given a name of its own:
    the system keeps a list of packages by name as well as by number.
    """
    table_kind, table_header, table_size = struct.unpack_from("<HHI", data, 0)
    if table_kind != RES_TABLE:
        raise ValueError("not a resource table")
    package_count = struct.unpack_from("<I", data, 8)[0]

    paths = [path for _name, path in names_and_paths]
    grown, first_string = _append_strings(data, table_header, paths)

    package = _package(package_id, type_name,
                       [name for name, _path in names_and_paths],
                       list(range(first_string, first_string + len(paths))),
                       package_name)

    out = bytearray(grown)
    out.extend(package)
    struct.pack_into("<I", out, 8, package_count + 1)
    struct.pack_into("<I", out, 4, len(out))
    return bytes(out)


def add_adaptive_icons(data: bytes, package_id: int,
                       names_and_paths: List[tuple]) -> bytes:
    """Add mipmaps with a PNG default and an adaptive XML value on API 26+.

    Each tuple is (resource name, legacy PNG path, adaptive XML or PNG path).
    Layer resources can use the same PNG in both configurations.
    """
    kind, header, _size = struct.unpack_from("<HHI", data, 0)
    if kind != RES_TABLE or not names_and_paths:
        raise ValueError("not a resource table or no icons")
    package_count = struct.unpack_from("<I", data, 8)[0]
    paths = [row[1] for row in names_and_paths] + [row[2] for row in names_and_paths]
    grown, first = _append_strings(data, header, paths)
    count = len(names_and_paths)
    package = _package_adaptive(
        package_id, [row[0] for row in names_and_paths],
        list(range(first, first + count)),
        list(range(first + count, first + 2 * count)),
    )
    out = bytearray(grown)
    out.extend(package)
    struct.pack_into("<I", out, 8, package_count + 1)
    struct.pack_into("<I", out, 4, len(out))
    return bytes(out)


def _package_adaptive(package_id: int, keys: List[str], legacy: List[int],
                      adaptive: List[int]) -> bytes:
    type_pool = _pool(["mipmap"])
    key_pool = _pool(keys)
    body = type_pool + key_pool + _spec(len(keys))
    body += _type(len(keys), legacy) + _type(len(keys), adaptive, sdk=26)
    header_size = 288
    head = bytearray(header_size)
    struct.pack_into("<HHI", head, 0, RES_TABLE_PACKAGE, header_size,
                     header_size + len(body))
    struct.pack_into("<I", head, 8, package_id)
    name = "tokkiyou".encode("utf-16-le")
    head[12:12 + len(name)] = name
    struct.pack_into("<4I", head, 268,
                     header_size, 1, header_size + len(type_pool), len(keys))
    return bytes(head) + body


def _append_strings(data: bytes, pool_at: int, strings: List[str]):
    """Put new strings at the end of the table's own pool.

    A pool is laid out as its header, the offsets of every string, the offsets
    of every style, the string data, and then the style data. Adding a string
    therefore moves three of those five: the style offsets and both blocks of
    data slide along, and the two pointers in the header that say where the
    data begins have to be moved with them. The offsets already written stay
    exactly as they are -- they are counted from the start of the data, and the
    data has not moved relative to itself.
    """
    kind, header_size, size = struct.unpack_from("<HHI", data, pool_at)
    if kind != RES_STRING_POOL:
        raise ValueError("the table does not start with its string pool")
    count, style_count, flags, strings_start, styles_start = struct.unpack_from(
        "<5I", data, pool_at + 8)
    utf8 = bool(flags & UTF8_FLAG)

    string_offsets_at = pool_at + header_size
    style_offsets_at = string_offsets_at + 4 * count
    strings_at = pool_at + strings_start
    styles_at = pool_at + styles_start if styles_start else pool_at + size
    end_of_pool = pool_at + size

    strings_length = styles_at - strings_at

    encoded = bytearray()
    added = []
    for text in strings:
        added.append(strings_length + len(encoded))
        encoded.extend(_encode(text, utf8))
    while (strings_length + len(encoded)) % 4:
        encoded.append(0)

    out = bytearray()
    out.extend(data[:style_offsets_at])                  # header + string offsets
    for offset in added:
        out.extend(struct.pack("<I", offset))            # ...and the new ones
    out.extend(data[style_offsets_at:strings_at])        # the style offsets
    out.extend(data[strings_at:styles_at])               # the strings that were there
    out.extend(encoded)                                  # ...and the new ones
    out.extend(data[styles_at:end_of_pool])              # the styles
    out.extend(data[end_of_pool:])                       # everything after the pool

    moved = 4 * len(strings)
    struct.pack_into("<I", out, pool_at + 8, count + len(strings))
    struct.pack_into("<I", out, pool_at + 20, strings_start + moved)
    if styles_start:
        struct.pack_into("<I", out, pool_at + 24, styles_start + moved + len(encoded))
    struct.pack_into("<I", out, pool_at + 4, size + moved + len(encoded))
    return out, count


def _encode(text: str, utf8: bool) -> bytes:
    if utf8:
        raw = text.encode("utf-8")
        if len(raw) > 0x7F:
            raise ValueError("a path that long needs the two-byte form")
        # utf-8 pools write the length in characters and then in bytes
        return bytes([len(text), len(raw)]) + raw + b"\x00"
    raw = text.encode("utf-16-le")
    if len(text) > 0x7FFF:
        raise ValueError("too long")
    return struct.pack("<H", len(text)) + raw + b"\x00\x00"


def _package(package_id: int, type_name: str, keys: List[str],
             string_indexes: List[int], package_name: str = "tokkiyou") -> bytes:
    """A whole package chunk: its two pools, a type spec, and one type."""
    type_pool = _pool([type_name])
    key_pool = _pool(keys)

    count = len(keys)
    spec = _spec(count)
    entries = _type(count, string_indexes)

    header_size = 288
    body = type_pool + key_pool + spec + entries
    head = bytearray(header_size)
    struct.pack_into("<HHI", head, 0, RES_TABLE_PACKAGE, header_size,
                     header_size + len(body))
    struct.pack_into("<I", head, 8, package_id)
    name = package_name.encode("utf-16-le")
    head[12:12 + len(name)] = name
    # typeStrings, lastPublicType, keyStrings, lastPublicKey
    struct.pack_into("<4I", head, 268,
                     header_size, 1, header_size + len(type_pool), count)
    return bytes(head) + body


def _pool(strings: List[str]) -> bytes:
    """A little utf-8 string pool, the shape aapt writes."""
    offsets = bytearray()
    body = bytearray()
    for text in strings:
        offsets.extend(struct.pack("<I", len(body)))
        body.extend(_encode(text, True))
    while len(body) % 4:
        body.append(0)

    header_size = 28
    strings_start = header_size + len(offsets)
    size = strings_start + len(body)
    head = bytearray(header_size)
    struct.pack_into("<HHI", head, 0, RES_STRING_POOL, header_size, size)
    struct.pack_into("<5I", head, 8, len(strings), 0, UTF8_FLAG, strings_start, 0)
    return bytes(head) + bytes(offsets) + bytes(body)


def _spec(count: int) -> bytes:
    header_size = 16
    size = header_size + 4 * count
    out = bytearray(size)
    struct.pack_into("<HHI", out, 0, RES_TABLE_TYPE_SPEC, header_size, size)
    out[8] = 1          # type id
    struct.pack_into("<I", out, 12, count)
    for i in range(count):
        struct.pack_into("<I", out, header_size + 4 * i, 0)
    return bytes(out)


def _type(count: int, string_indexes: List[int], sdk: int = 0) -> bytes:
    """One configuration -- the default one -- holding every entry."""
    config = bytearray(64)
    struct.pack_into("<I", config, 0, 64)
    struct.pack_into("<H", config, 24, sdk)

    entries = bytearray()
    offsets = bytearray()
    for index in string_indexes:
        offsets.extend(struct.pack("<I", len(entries)))
        entries.extend(struct.pack("<HHI", 8, 0, 0))        # size, flags, key
        entries.extend(struct.pack("<HBBI", 8, 0, TYPE_STRING, index))

    header_size = 20 + len(config)
    entries_start = header_size + len(offsets)
    size = entries_start + len(entries)

    out = bytearray(header_size)
    struct.pack_into("<HHI", out, 0, RES_TABLE_TYPE, header_size, size)
    out[8] = 1          # type id
    struct.pack_into("<II", out, 12, count, entries_start)
    out[20:20 + len(config)] = config
    return bytes(out) + bytes(offsets) + bytes(entries)
