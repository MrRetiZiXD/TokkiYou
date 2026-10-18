"""The apk as a zip, rewritten entry by entry.

An apk this size is mostly bytes we have no business touching, so untouched
entries are copied across still compressed -- never inflated, never deflated
again. Only what the build actually replaces is new.

Two rules from the platform are kept while writing: `resources.arsc` stays
stored, not deflated, and every stored entry's data begins on a four-byte
boundary. Both are what zipalign would otherwise have to fix afterwards.
"""

from __future__ import annotations

import os
import struct
import zlib
from typing import Dict, List, Optional

STORED = 0
DEFLATED = 8

LOCAL_SIG = 0x04034B50
CENTRAL_SIG = 0x02014B50
EOCD_SIG = 0x06054B50

ALIGNMENT = 4

# the old signature, which is about to stop being true
SIGNATURE_FILES = (".SF", ".RSA", ".DSA", ".EC")


class ZipError(Exception):
    pass


class Entry:
    def __init__(self, name: str, method: int, crc: int, csize: int, usize: int, dos_time: int,
                 dos_date: int, source_offset: Optional[int] = None, data: Optional[bytes] = None):
        self.name = name
        self.method = method
        self.crc = crc
        self.csize = csize
        self.usize = usize
        self.dos_time = dos_time
        self.dos_date = dos_date
        self.source_offset = source_offset  # where the compressed bytes live in the input
        self.data = data  # set for entries this build produced


class Apk:
    """An apk opened for reading, with edits held until `write`."""

    def __init__(self, path: str):
        self.path = path
        self.file = open(path, "rb")
        self.entries: List[Entry] = []
        self.index: Dict[str, Entry] = {}
        self._read_central_directory()

    # ----------------------------------------------------------------- read

    #: Deflates a list of byte strings, each to a raw stream. None: zlib does it.
    deflater = None

    def _read_central_directory(self) -> None:
        size = os.path.getsize(self.path)
        tail = min(size, 66000)
        self.file.seek(size - tail)
        blob = self.file.read(tail)
        at = blob.rfind(struct.pack("<I", EOCD_SIG))
        if at < 0:
            raise ZipError("no end-of-central-directory record: not a zip")
        count, cd_size, cd_offset = struct.unpack_from("<HII", blob, at + 10)
        if cd_offset == 0xFFFFFFFF:
            raise ZipError("zip64 apks are not supported")

        self.file.seek(cd_offset)
        cd = self.file.read(cd_size)
        pos = 0
        for _ in range(count):
            sig, _ver, _need, _flag, method, dos_time, dos_date, crc, csize, usize = (
                struct.unpack_from("<IHHHHHHIII", cd, pos)
            )
            if sig != CENTRAL_SIG:
                raise ZipError("bad central directory entry at %d" % pos)
            name_len, extra_len, comment_len = struct.unpack_from("<HHH", cd, pos + 28)
            offset = struct.unpack_from("<I", cd, pos + 42)[0]
            name = cd[pos + 46 : pos + 46 + name_len].decode("utf-8", "replace")
            pos += 46 + name_len + extra_len + comment_len

            entry = Entry(name, method, crc, csize, usize, dos_time, dos_date, offset)
            self.entries.append(entry)
            self.index[name] = entry

    def _raw(self, entry: Entry) -> bytes:
        """The entry's bytes exactly as stored, compression and all."""
        if entry.data is not None:
            return _compress(entry.data, entry.method)
        self.file.seek(entry.source_offset)
        head = self.file.read(30)
        sig, _ver, _flag, _method, _t, _d, _crc, _cs, _us, name_len, extra_len = struct.unpack(
            "<IHHHHHIIIHH", head
        )
        if sig != LOCAL_SIG:
            raise ZipError("bad local header for %s" % entry.name)
        self.file.seek(entry.source_offset + 30 + name_len + extra_len)
        return self.file.read(entry.csize)

    def read(self, name: str) -> bytes:
        entry = self.index.get(name)
        if entry is None:
            raise KeyError(name)
        if entry.data is not None:
            return entry.data
        raw = self._raw(entry)
        if entry.method == STORED:
            return raw
        return zlib.decompress(raw, -15)

    def names(self) -> List[str]:
        return [e.name for e in self.entries]

    def has(self, name: str) -> bool:
        return name in self.index

    # ---------------------------------------------------------------- write

    def replace(self, name: str, data: bytes, method: Optional[int] = None) -> None:
        entry = self.index.get(name)
        if entry is None:
            raise KeyError(name)
        entry.data = data
        entry.usize = len(data)
        entry.crc = zlib.crc32(data) & 0xFFFFFFFF
        if method is not None:
            entry.method = method

    def add(self, name: str, data: bytes, method: int = DEFLATED) -> None:
        if name in self.index:
            self.replace(name, data, method)
            return
        entry = Entry(name, method, zlib.crc32(data) & 0xFFFFFFFF, 0, len(data), 0, 0x21, None, data)
        self.entries.append(entry)
        self.index[name] = entry

    def remove(self, name: str) -> None:
        entry = self.index.pop(name, None)
        if entry is not None:
            self.entries.remove(entry)

    def drop_signature(self) -> List[str]:
        """Throw away the signature the apk arrived with."""
        gone = []
        for name in list(self.index):
            upper = name.upper()
            if upper == "META-INF/MANIFEST.MF" or (
                upper.startswith("META-INF/") and upper.endswith(SIGNATURE_FILES)
            ):
                self.remove(name)
                gone.append(name)
        return gone

    def write(self, out_path: str) -> None:
        # What is new in this apk is deflated all at once and by the one
        # compressor the patcher app has too, when the build has given it:
        # an apk the app can make again has to be deflated the way it would.
        ready = {}
        if self.deflater is not None:
            fresh = [entry for entry in self.entries
                     if entry.data is not None and entry.method == DEFLATED]
            for entry, raw in zip(fresh, self.deflater([entry.data for entry in fresh])):
                ready[id(entry)] = raw
        with open(out_path, "wb") as out:
            offsets = []
            for entry in self.entries:
                raw = ready.get(id(entry))
                if raw is None:
                    raw = self._raw(entry)
                if entry.data is not None:
                    entry.csize = len(raw)
                name = entry.name.encode("utf-8")
                flag = 0 if name.isascii() else 0x800

                pad = 0
                if entry.method == STORED:
                    at = out.tell() + 30 + len(name)
                    pad = (-at) % ALIGNMENT

                offsets.append(out.tell())
                out.write(
                    struct.pack(
                        "<IHHHHHIIIHH",
                        LOCAL_SIG,
                        20,
                        flag,
                        entry.method,
                        entry.dos_time,
                        entry.dos_date,
                        entry.crc,
                        entry.csize,
                        entry.usize,
                        len(name),
                        pad,
                    )
                )
                out.write(name)
                if pad:
                    out.write(b"\x00" * pad)
                out.write(raw)

            cd_offset = out.tell()
            for entry, offset in zip(self.entries, offsets):
                name = entry.name.encode("utf-8")
                flag = 0 if name.isascii() else 0x800
                out.write(
                    struct.pack(
                        "<IHHHHHHIIIHHHHHII",
                        CENTRAL_SIG,
                        20,
                        20,
                        flag,
                        entry.method,
                        entry.dos_time,
                        entry.dos_date,
                        entry.crc,
                        entry.csize,
                        entry.usize,
                        len(name),
                        0,
                        0,
                        0,
                        0,
                        0,
                        offset,
                    )
                )
                out.write(name)
            cd_size = out.tell() - cd_offset
            out.write(
                struct.pack(
                    "<IHHHHIIH",
                    EOCD_SIG,
                    0,
                    0,
                    len(self.entries),
                    len(self.entries),
                    cd_size,
                    cd_offset,
                    0,
                )
            )

    def close(self) -> None:
        self.file.close()


def _compress(data: bytes, method: int) -> bytes:
    if method == STORED:
        return data
    compressor = zlib.compressobj(9, zlib.DEFLATED, -15)
    return compressor.compress(data) + compressor.flush()
