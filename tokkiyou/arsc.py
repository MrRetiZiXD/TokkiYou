"""resources.arsc, read and patched in place.

The table is never rebuilt. It is parsed to find out where a resource lives,
and the only writes are ones that cannot move a byte: a string swapped for
another of exactly the same encoded length, or a typed value overwritten with
another of the same eight bytes. Everything an id refers to -- the id itself,
every offset, every chunk size -- is left where aapt2 put it.

That is the whole trick behind this build. Rewriting a 25 MB resource table is
what needs aapt2, and aapt2 rebuilding a table it did not write is what breaks
an apk this size.
"""

from __future__ import annotations

import struct
from typing import Dict, List, Optional, Tuple

RES_STRING_POOL = 0x0001
RES_TABLE = 0x0002
RES_TABLE_PACKAGE = 0x0200
RES_TABLE_TYPE = 0x0201
RES_TABLE_TYPE_SPEC = 0x0202

TYPE_STRING = 0x03

FLAG_SPARSE = 0x01
FLAG_OFFSET16 = 0x02

ENTRY_FLAG_COMPLEX = 0x0001
ENTRY_FLAG_COMPACT = 0x0008

NO_ENTRY32 = 0xFFFFFFFF
NO_ENTRY16 = 0xFFFF


class ArscError(Exception):
    pass


class Value:
    """One configuration's value for one resource id."""

    def __init__(self, type_name: str, config: bytes, kind: int, data: int, offset: int):
        self.type_name = type_name
        self.config = config
        self.kind = kind
        self.data = data
        self.offset = offset  # where the 8-byte Res_value sits in the file

    @property
    def density(self) -> int:
        """The config's density in dpi, 0 when it does not name one."""
        if len(self.config) < 16:
            return 0
        return struct.unpack_from("<H", self.config, 14)[0]

    @property
    def sdk(self) -> int:
        if len(self.config) < 26:
            return 0
        return struct.unpack_from("<H", self.config, 24)[0]

    def __repr__(self) -> str:
        return "Value(%s, density=%d, kind=0x%02x, data=0x%08x)" % (
            self.type_name,
            self.density,
            self.kind,
            self.data,
        )


class StringPool:
    """The table's global string pool, with the offset of every string kept.

    The offsets are what makes an in-place swap possible: a string can be
    rewritten where it lies as long as the replacement encodes to the same
    number of bytes.
    """

    UTF8 = 1 << 8

    def __init__(self, data: bytearray, start: int):
        # the table's own buffer, not a copy: a string patched in place has to
        # be what the next read of it sees
        kind, header_size, size = struct.unpack_from("<HHI", data, start)
        if kind != RES_STRING_POOL:
            raise ArscError("expected a string pool at %d, found 0x%04x" % (start, kind))
        count, style_count, flags, strings_start, _styles_start = struct.unpack_from(
            "<IIIII", data, start + 8
        )
        self.utf8 = bool(flags & self.UTF8)
        self.count = count
        self.chunk_start = start
        self.chunk_end = start + size
        offsets = struct.unpack_from("<%dI" % count, data, start + header_size)
        self.base = start + strings_start
        self.offsets = offsets
        self._data = data
        self._cache: Dict[int, str] = {}

    def __len__(self) -> int:
        return self.count

    def get(self, index: int) -> Optional[str]:
        if index < 0 or index >= self.count:
            return None
        if index in self._cache:
            return self._cache[index]
        value = _read_string(self._data, self.base + self.offsets[index], self.utf8)
        self._cache[index] = value
        return value

    def encoded_span(self, index: int) -> Tuple[int, int]:
        """Where the string's bytes live: (offset of the first byte, length)."""
        pos = self.base + self.offsets[index]
        if self.utf8:
            _chars, pos = _read_len(self._data, pos, True)
            nbytes, pos = _read_len(self._data, pos, True)
            return pos, nbytes
        nchars, pos = _read_len(self._data, pos, False)
        return pos, nchars * 2


def _read_len(data: bytes, pos: int, utf8: bool) -> Tuple[int, int]:
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
        _chars, pos = _read_len(data, pos, True)
        nbytes, pos = _read_len(data, pos, True)
        return data[pos : pos + nbytes].decode("utf-8", "replace")
    nchars, pos = _read_len(data, pos, False)
    return data[pos : pos + nchars * 2].decode("utf-16-le", "replace")


class Package:
    def __init__(self, pid: int, name: str):
        self.id = pid
        self.name = name
        self.type_names: Optional[StringPool] = None
        # type id -> list of RES_TABLE_TYPE chunk starts
        self.types: Dict[int, List[int]] = {}


class Arsc:
    def __init__(self, data: bytes):
        self.data = bytearray(data)
        self.dirty = False
        self.packages: List[Package] = []
        self._parse()

    # ------------------------------------------------------------- parsing

    def _parse(self) -> None:
        data = self.data
        kind, header_size, _size = struct.unpack_from("<HHI", data, 0)
        if kind != RES_TABLE:
            raise ArscError("not a resource table (first chunk is 0x%04x)" % kind)

        self.strings = StringPool(data, header_size)
        pos = self.strings.chunk_end
        while pos + 8 <= len(data):
            ctype, chdr, csize = struct.unpack_from("<HHI", data, pos)
            if csize < 8:
                raise ArscError("chunk 0x%04x at %d has size %d" % (ctype, pos, csize))
            if ctype == RES_TABLE_PACKAGE:
                self.packages.append(self._parse_package(pos, chdr, csize))
            pos += csize

    def _parse_package(self, start: int, header_size: int, size: int) -> Package:
        data = self.data
        pid = struct.unpack_from("<I", data, start + 8)[0]
        name = data[start + 12 : start + 12 + 256].decode("utf-16-le").split("\x00")[0]
        type_strings, _last_type, key_strings, _last_key = struct.unpack_from(
            "<IIII", data, start + 268
        )
        package = Package(pid, name)
        if type_strings:
            package.type_names = StringPool(data, start + type_strings)
        if key_strings:
            package.key_names = StringPool(data, start + key_strings)

        pos = start + header_size
        end = start + size
        while pos + 8 <= end:
            ctype, chdr, csize = struct.unpack_from("<HHI", data, pos)
            if csize < 8:
                break
            if ctype == RES_TABLE_TYPE:
                type_id = data[pos + 8]
                package.types.setdefault(type_id, []).append(pos)
            pos += csize
        return package

    # ------------------------------------------------------------- lookups

    def package(self, pid: int) -> Optional[Package]:
        for p in self.packages:
            if p.id == pid:
                return p
        return None

    def values(self, res_id: int) -> List[Value]:
        """Every configuration's value for `res_id`, in table order."""
        pid = (res_id >> 24) & 0xFF
        type_id = (res_id >> 16) & 0xFF
        entry_id = res_id & 0xFFFF
        package = self.package(pid)
        if package is None:
            return []
        type_name = "?"
        if package.type_names is not None:
            type_name = package.type_names.get(type_id - 1) or "?"

        out = []
        for chunk in package.types.get(type_id, []):
            value = self._entry_value(chunk, entry_id, type_name)
            if value is not None:
                out.append(value)
        return out

    def _entry_value(self, chunk: int, entry_id: int, type_name: str) -> Optional[Value]:
        data = self.data
        _ctype, header_size, _csize = struct.unpack_from("<HHI", data, chunk)
        flags = data[chunk + 9]
        entry_count, entries_start = struct.unpack_from("<II", data, chunk + 12)
        config_size = struct.unpack_from("<I", data, chunk + 20)[0]
        config = bytes(data[chunk + 20 : chunk + 20 + config_size])

        table = chunk + header_size
        if flags & FLAG_SPARSE:
            offset = None
            for i in range(entry_count):
                idx, off = struct.unpack_from("<HH", data, table + i * 4)
                if idx == entry_id:
                    offset = off * 4
                    break
            if offset is None:
                return None
        elif flags & FLAG_OFFSET16:
            if entry_id >= entry_count:
                return None
            off = struct.unpack_from("<H", data, table + entry_id * 2)[0]
            if off == NO_ENTRY16:
                return None
            offset = off * 4
        else:
            if entry_id >= entry_count:
                return None
            off = struct.unpack_from("<I", data, table + entry_id * 4)[0]
            if off == NO_ENTRY32:
                return None
            offset = off

        entry = chunk + entries_start + offset
        size_or_key, entry_flags = struct.unpack_from("<HH", data, entry)
        if entry_flags & ENTRY_FLAG_COMPACT:
            # the value is the entry: key in the size slot, type in the flags
            kind = entry_flags >> 8
            value = struct.unpack_from("<I", data, entry + 4)[0]
            return Value(type_name, config, kind, value, entry + 4)
        if entry_flags & ENTRY_FLAG_COMPLEX:
            return None  # a bag (style, array, attr); nothing here edits those
        at = entry + size_or_key
        _size, _res0, kind, value = struct.unpack_from("<HBBI", data, at)
        return Value(type_name, config, kind, value, at)

    def file_path(self, value: Value) -> Optional[str]:
        if value.kind != TYPE_STRING:
            return None
        return self.strings.get(value.data)

    # ------------------------------------------------------------- patching

    def replace_string(self, index: int, new: str) -> None:
        """Overwrite a pooled string with one of the same encoded length."""
        pos, length = self.strings.encoded_span(index)
        raw = new.encode("utf-8" if self.strings.utf8 else "utf-16-le")
        if len(raw) != length:
            raise ArscError(
                "%r does not encode to the %d bytes %r does"
                % (new, length, self.strings.get(index))
            )
        self.data[pos : pos + length] = raw
        self.strings._cache.pop(index, None)
        self.dirty = True

    def set_value(self, value: Value, kind: int, data: int) -> None:
        """Overwrite a typed value where it lies."""
        struct.pack_into("<HBBI", self.data, value.offset, 8, 0, kind, data)
        value.kind, value.data = kind, data
        self.dirty = True

    def build(self) -> bytes:
        return bytes(self.data)
