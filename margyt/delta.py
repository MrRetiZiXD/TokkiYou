"""What the patcher app needs to turn TikTok's own apk into a release of the mod.

A release apk is mostly TikTok's apk: of twenty-six thousand files the mod
changes fifty and adds a hundred. So instead of the whole apk a release can be
handed out as a patch -- a recipe that says, for every stretch of the release
apk, where its bytes come from:

    COPY     a stretch of TikTok's apk, as it is
    LITERAL  bytes the patch carries itself: the zip's headers and directory,
             the signature, the files the mod adds
    PATCH    a file the mod changed, as a binary difference against TikTok's
             (bsdiff), deflated again afterwards the way the build deflated it

Applied to the right apk it gives back the release apk byte for byte, signature
and all, which is what makes it installable over an earlier release and what
lets the result be checked against one hash.

The patch is a zip, stored, of:

    patch.json    which apk it is for and which it makes (sizes, sha256)
    ops.xz        the recipe
    literal.xz    every LITERAL's bytes, end to end
    d/N.c.xz      bsdiff's control tuples for the Nth PATCH
    d/N.d.xz      its difference bytes
    d/N.e.xz      its extra bytes

Run as `python -m margyt.delta make TIKTOK.apk RELEASE.apk -o OUT.mpatch`, and
`... apply TIKTOK.apk PATCH OUT.apk` to do on a computer what the app does on a
phone. Making needs the `bsdiff4` package; applying does not.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import lzma
import os
import struct
import sys
import time
import zipfile
import zlib
from concurrent.futures import ProcessPoolExecutor
from typing import Dict, List, Optional, Tuple

FORMAT = 1
COPY, LITERAL, PATCH = 0, 1, 2
STORED, DEFLATED = 0, 8
#: What deflates a patched file again. Named in the patch so an app that
#: carries another compressor refuses it rather than making a broken apk.
DEFLATE = {"with": "jzlib-1.1.3", "level": 6}
_XZ = {"format": lzma.FORMAT_XZ, "preset": 6}


class DeltaError(Exception):
    pass


class _Entry:
    __slots__ = ("name", "method", "crc", "csize", "usize", "data")

    def __init__(self, name, method, crc, csize, usize, data):
        self.name, self.method, self.crc = name, method, crc
        self.csize, self.usize, self.data = csize, usize, data


def _entries(path: str) -> Dict[str, _Entry]:
    """Every file in a zip and where its stored bytes begin."""
    out: Dict[str, _Entry] = {}
    with zipfile.ZipFile(path) as archive, open(path, "rb") as raw:
        for info in archive.infolist():
            raw.seek(info.header_offset)
            head = raw.read(30)
            if head[:4] != b"PK\x03\x04":
                raise DeltaError("%s: no local header for %s" % (path, info.filename))
            name_len, extra_len = struct.unpack("<HH", head[26:30])
            out[info.filename] = _Entry(
                info.filename, info.compress_type, info.CRC, info.compress_size,
                info.file_size, info.header_offset + 30 + name_len + extra_len)
    return out


def _sha256(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def _plain(raw: bytes, method: int) -> bytes:
    return raw if method == STORED else zlib.decompress(raw, -15)


def _difference(pair: Tuple[bytes, bytes]) -> Tuple[bytes, bytes, bytes]:
    """bsdiff of one file, as three compressed streams."""
    from bsdiff4 import core  # only making needs it

    old, new = pair
    control, difference, extra = core.diff(old, new)
    packed = b"".join(struct.pack("<qqq", *row) for row in control)
    return (lzma.compress(packed, **_XZ), lzma.compress(difference, **_XZ),
            lzma.compress(extra, **_XZ))


def make(base_path: str, target_path: str, out_path: str, deflate_many=None,
         say=print) -> dict:
    """Write the patch that turns `base_path` into `target_path`."""
    started = time.time()
    base, target = _entries(base_path), _entries(target_path)
    size = os.path.getsize(target_path)

    # (where in the target, how long, op)
    regions: List[Tuple[int, int, tuple]] = []
    changed: List[Tuple[_Entry, _Entry]] = []
    with open(base_path, "rb") as old, open(target_path, "rb") as new:
        for entry in target.values():
            if entry.csize == 0:
                continue
            theirs = base.get(entry.name)
            if theirs is None:
                continue
            if (theirs.crc == entry.crc and theirs.csize == entry.csize
                    and theirs.method == entry.method):
                old.seek(theirs.data)
                new.seek(entry.data)
                if old.read(theirs.csize) == new.read(entry.csize):
                    regions.append((entry.data, entry.csize, (COPY, theirs.data, entry.csize)))
                    continue
            if theirs.usize and entry.usize and theirs.csize:
                changed.append((theirs, entry))

        # A changed file can be made again only if deflating it gives the
        # bytes the build wrote. The build uses the same compressor, so it
        # does -- unless the apk was built before it did, and then the file
        # travels whole.
        pairs: List[Tuple[bytes, bytes]] = []
        kept: List[Tuple[_Entry, _Entry]] = []
        plains, raws = [], []
        for theirs, entry in changed:
            new.seek(entry.data)
            raw = new.read(entry.csize)
            plains.append(_plain(raw, entry.method))
            raws.append(raw)
        again = deflate_many([plain for plain, (_t, entry) in zip(plains, changed)
                              if entry.method == DEFLATED]) if deflate_many else []
        again_at = 0
        whole = 0
        for (theirs, entry), plain, raw in zip(changed, plains, raws):
            if entry.method == DEFLATED:
                same = bool(again) and again[again_at] == raw
                again_at += 1
                if not same:
                    whole += 1
                    continue
            old.seek(theirs.data)
            pairs.append((_plain(old.read(theirs.csize), theirs.method), plain))
            kept.append((theirs, entry))
        if whole:
            say("    %d changed file(s) were not deflated the patcher's way and travel whole" % whole)

        say("    %d files copied, %d patched; working out the differences" % (len(regions), len(kept)))
        with ProcessPoolExecutor(max_workers=min(4, os.cpu_count() or 1)) as pool:
            streams = list(pool.map(_difference, pairs))
        for at, (theirs, entry) in enumerate(kept):
            regions.append((entry.data, entry.csize,
                            (PATCH, at, theirs.data, theirs.csize, theirs.method, theirs.usize,
                             entry.method, entry.usize, entry.csize, entry.crc)))

        regions.sort()
        ops: List[tuple] = []
        literal = io.BytesIO()

        def carry(start: int, end: int) -> None:
            if end <= start:
                return
            new.seek(start)
            literal.write(new.read(end - start))
            if ops and ops[-1][0] == LITERAL:
                ops[-1] = (LITERAL, ops[-1][1] + end - start)
            else:
                ops.append((LITERAL, end - start))

        at = 0
        for start, length, op in regions:
            if start < at:
                raise DeltaError("two files of the apk overlap at %d" % start)
            carry(at, start)
            last = ops[-1] if ops else None
            if (op[0] == COPY and last and last[0] == COPY
                    and last[1] + last[2] == op[1]):
                ops[-1] = (COPY, last[1], last[2] + op[2])
            else:
                ops.append(op)
            at = start + length
        carry(at, size)

    recipe = io.BytesIO()
    recipe.write(struct.pack("<I", len(ops)))
    for op in ops:
        if op[0] == COPY:
            recipe.write(struct.pack("<BQQ", COPY, op[1], op[2]))
        elif op[0] == LITERAL:
            recipe.write(struct.pack("<BQ", LITERAL, op[1]))
        else:
            recipe.write(struct.pack("<BIQQBQBQQI", *op))

    about = {
        "format": FORMAT,
        "deflate": DEFLATE,
        "base": {"sha256": _sha256(base_path), "size": os.path.getsize(base_path)},
        "result": {"sha256": _sha256(target_path), "size": size},
        "patched": len(kept),
    }
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_STORED) as out:
        out.writestr("patch.json", json.dumps(about, indent=1))
        out.writestr("ops.xz", lzma.compress(recipe.getvalue(), **_XZ))
        out.writestr("literal.xz", lzma.compress(literal.getvalue(), **_XZ))
        for at, (control, difference, extra) in enumerate(streams):
            out.writestr("d/%d.c.xz" % at, control)
            out.writestr("d/%d.d.xz" % at, difference)
            out.writestr("d/%d.e.xz" % at, extra)
    about["patch"] = {"sha256": _sha256(out_path), "size": os.path.getsize(out_path)}
    say("    %.1f MB of patch for a %.0f MB apk, %d steps, in %.0f s"
        % (about["patch"]["size"] / 1e6, size / 1e6, len(ops), time.time() - started))
    return about


def _bspatch(old: bytes, size: int, control: bytes, difference: bytes, extra: bytes) -> bytes:
    new = bytearray(size)
    at = from_old = from_diff = from_extra = 0
    for row in range(0, len(control), 24):
        add, copy, seek = struct.unpack_from("<qqq", control, row)
        piece = bytearray(difference[from_diff:from_diff + add])
        span = old[from_old:from_old + add] if 0 <= from_old else b""
        for i in range(min(len(span), add)):
            piece[i] = (piece[i] + span[i]) & 0xFF
        new[at:at + add] = piece
        at += add
        from_diff += add
        from_old += add
        new[at:at + copy] = extra[from_extra:from_extra + copy]
        at += copy
        from_extra += copy
        from_old += seek
    if at != size:
        raise DeltaError("a patched file came out %d bytes long, not %d" % (at, size))
    return bytes(new)


def apply(base_path: str, patch_path: str, out_path: str, deflate_many, say=print) -> None:
    """Do on a computer what the app does on a phone. Slow, and only a check."""
    with zipfile.ZipFile(patch_path) as patch, open(base_path, "rb") as old, \
            open(out_path, "wb") as out:
        about = json.loads(patch.read("patch.json"))
        if about["format"] != FORMAT:
            raise DeltaError("a patch of format %s, and this reads %s" % (about["format"], FORMAT))
        if _sha256(base_path) != about["base"]["sha256"]:
            raise DeltaError("this is not the apk the patch was made for")
        recipe = lzma.decompress(patch.read("ops.xz"))
        literal = io.BytesIO(lzma.decompress(patch.read("literal.xz")))
        (count,), at = struct.unpack_from("<I", recipe), 4
        for _ in range(count):
            kind = recipe[at]
            if kind == COPY:
                _k, source, length = struct.unpack_from("<BQQ", recipe, at)
                at += 17
                old.seek(source)
                out.write(old.read(length))
            elif kind == LITERAL:
                _k, length = struct.unpack_from("<BQ", recipe, at)
                at += 9
                out.write(literal.read(length))
            else:
                (_k, index, source, csize, method, usize, to_method, to_usize, to_csize,
                 crc) = struct.unpack_from("<BIQQBQBQQI", recipe, at)
                at += struct.calcsize("<BIQQBQBQQI")
                old.seek(source)
                plain = _plain(old.read(csize), method)
                made = _bspatch(plain, to_usize,
                                lzma.decompress(patch.read("d/%d.c.xz" % index)),
                                lzma.decompress(patch.read("d/%d.d.xz" % index)),
                                lzma.decompress(patch.read("d/%d.e.xz" % index)))
                if zlib.crc32(made) & 0xFFFFFFFF != crc:
                    raise DeltaError("patched file %d does not check out" % index)
                raw = made if to_method == STORED else deflate_many([made])[0]
                if len(raw) != to_csize:
                    raise DeltaError("patched file %d deflated to another size" % index)
                out.write(raw)
    if _sha256(out_path) != about["result"]["sha256"]:
        raise DeltaError("the apk that came out is not the release")
    say("    the release apk, byte for byte")


def main(argv: Optional[List[str]] = None) -> int:
    from .toolchain import Toolchain

    parser = argparse.ArgumentParser(prog="margyt.delta", description=__doc__.split("\n")[0])
    parser.add_argument("--tools", default=os.path.join(os.path.dirname(os.path.dirname(
        os.path.abspath(__file__))), "tools"), help="where the build keeps its jars")
    commands = parser.add_subparsers(dest="command", required=True)
    making = commands.add_parser("make")
    making.add_argument("base")
    making.add_argument("release")
    making.add_argument("-o", "--out", required=True)
    applying = commands.add_parser("apply")
    applying.add_argument("base")
    applying.add_argument("patch")
    applying.add_argument("out")
    args = parser.parse_args(argv)
    tools = Toolchain(args.tools, quiet=True)
    try:
        if args.command == "make":
            about = make(args.base, args.release, args.out, tools.deflate_many)
            print(json.dumps(about, indent=1))
        else:
            apply(args.base, args.patch, args.out, tools.deflate_many)
    except DeltaError as problem:
        print("delta: %s" % problem, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
