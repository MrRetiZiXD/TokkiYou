"""Patching the dex files several at a time, and remembering the answers.

Taking a dex apart and putting it back is where the build spends its time,
and there are forty-odd of them. Two things make that shorter.

They are done side by side. Each is a job of its own -- its own room, its
own java -- so nothing about one depends on another, and the only limit is
how much memory the machine has to give.

And three things are kept between builds, each under a name made from what
it depends on, so that a wrong answer cannot be found by accident:

  the answer    the patched dex, for this dex under these rules. A rebuild
                that changed only the mod's Java gets every one from here.

  the pieces    the dex taken apart, before anything is rewritten. That
                depends on the dex alone, so it survives any change to the
                rules, and a changed rule starts from here rather than from
                baksmali.

  the outcome   the patched dex again, but named by what was actually
                rewritten in it. Change one hook and the rules are "new" for
                every dex, yet nearly all of them come out rewritten exactly
                as before -- and those are recognised here and not put back
                together a second time.

TOKKIYOU_NO_CACHE=1 ignores all of it, TOKKIYOU_CACHE=dir moves it,
TOKKIYOU_WORKERS=n says how many to do at once.
"""

from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import time
from concurrent.futures import ProcessPoolExecutor, as_completed
from typing import Callable, Dict, List, Optional, Tuple

from . import dexpatch
from .dexpatch import Smali

#: bumped when what is kept changes shape, so nothing old is read as new
SHAPE = "3"

#: something not asked for in this long is not coming back: an old TikTok,
#: or rules nobody builds with any more
KEEP_DAYS = 30

#: what one java is allowed, and what one job is counted as when working out
#: how many fit. A job is either taking a dex apart or putting it together,
#: never both at once. Measured on the largest dex of 47.2.41: given 4g java
#: takes 3.7 GB and given 2g it takes 2.4, and is not a tenth of a second
#: slower for it.
HEAP = "2g"
ROOM_PER_JOB = 2.5


def folder() -> str:
    return os.environ.get("TOKKIYOU_CACHE") or os.path.join(
        os.path.expanduser("~"), ".cache", "tokkiyou-dex")


def _sha(*parts: bytes) -> str:
    digest = hashlib.sha256()
    for part in parts:
        digest.update(len(part).to_bytes(8, "big"))
        digest.update(part)
    return digest.hexdigest()


def _stamp(path: str) -> bytes:
    """A tool is the same tool while its size and date are the same."""
    try:
        seen = os.stat(path)
        return ("%s:%d:%d" % (os.path.basename(path), seen.st_size,
                              seen.st_mtime_ns)).encode()
    except OSError:
        return b"?"


def _touch(path: str) -> None:
    try:
        os.utime(path, None)
    except OSError:
        pass


def workers(jobs: int) -> int:
    """How many dex files to do at once.

    Memory is what runs out first, not processors: each java here may take
    gigabytes, and a build that pushes the machine into swap is slower than
    one that waits its turn.
    """
    asked = os.environ.get("TOKKIYOU_WORKERS")
    if asked:
        return max(1, min(int(asked), jobs or 1))
    free = 4.0
    try:
        with open("/proc/meminfo", encoding="ascii") as handle:
            for line in handle:
                if line.startswith("MemAvailable:"):
                    free = int(line.split()[1]) / (1024.0 * 1024.0)
                    break
    except (OSError, ValueError):
        pass
    by_memory = int(free // ROOM_PER_JOB)
    by_cores = (os.cpu_count() or 2) // 2
    return max(1, min(by_memory, by_cores, 6, jobs or 1))


def prune(root: Optional[str] = None) -> None:
    """Throw away what has not been asked for in a month."""
    root = root or folder()
    limit = time.time() - KEEP_DAYS * 86400
    try:
        names = os.listdir(root)
    except OSError:
        return
    for name in names:
        path = os.path.join(root, name)
        try:
            if name == "pieces":
                for tree in os.listdir(path):
                    whole = os.path.join(path, tree)
                    mark = os.path.join(whole, "whole")
                    # one without its mark was interrupted half way
                    if not os.path.exists(mark) or os.path.getmtime(mark) < limit:
                        if os.path.getmtime(whole) < time.time() - 3600:
                            shutil.rmtree(whole, ignore_errors=True)
            elif os.path.isfile(path) and os.path.getmtime(path) < limit:
                os.remove(path)
        except OSError:
            pass


def _pieces(dex: bytes, dex_sha: str, smali: Smali, root: str, scratch: str) -> str:
    """The dex taken apart and untouched, made once and kept."""
    home = os.path.join(root, "pieces", "%s-a%d" % (dex_sha[:40], smali.api))
    mark = os.path.join(home, "whole")
    tree = os.path.join(home, "smali")
    if os.path.exists(mark):
        _touch(mark)
        return tree

    # made beside where it will live and moved in whole, so a build that is
    # stopped half way leaves nothing that looks finished
    making = "%s.%d" % (home, os.getpid())
    shutil.rmtree(making, ignore_errors=True)
    os.makedirs(making, exist_ok=True)
    dex_in = os.path.join(scratch, "in.dex")
    with open(dex_in, "wb") as handle:
        handle.write(dex)
    smali.disassemble(dex_in, os.path.join(making, "smali"))
    with open(os.path.join(making, "whole"), "w") as handle:
        handle.write("")
    shutil.rmtree(home, ignore_errors=True)
    try:
        os.rename(making, home)
    except OSError:
        # somebody else finished the same one first; theirs will do
        shutil.rmtree(making, ignore_errors=True)
        if not os.path.exists(mark):
            raise
    return tree


def _copy(tree: str, into: str) -> None:
    """A copy to rewrite, leaving the kept one as it was.

    On a filesystem that can share blocks this copies nothing but names;
    anywhere else it is an ordinary copy, which is still far quicker than
    taking the dex apart again.
    """
    shutil.rmtree(into, ignore_errors=True)
    os.makedirs(os.path.dirname(into), exist_ok=True)
    if os.name == "nt":
        robocopy = shutil.which("robocopy.exe") or shutil.which("robocopy")
        if robocopy:
            done = subprocess.run(
                [robocopy, tree, into, "/E", "/COPY:DAT", "/R:0", "/W:0",
                 "/MT:8", "/NFL", "/NDL", "/NJH", "/NJS", "/NP"],
                capture_output=True, text=True,
            )
            if done.returncode <= 7:
                return
            shutil.rmtree(into, ignore_errors=True)
            raise RuntimeError("robocopy failed to copy the cached dex tree: "
                               + done.stdout + done.stderr)
        shutil.copytree(tree, into)
        return
    done = subprocess.run(["cp", "-a", "--reflink=auto", tree, into],
                          capture_output=True, text=True)
    if done.returncode != 0:
        shutil.rmtree(into, ignore_errors=True)
        shutil.copytree(tree, into)


def _rewrite(root: str, literals: Dict[str, str],
             dex: Optional[bytes] = None) -> Tuple[Dict[str, int], List[str]]:
    """Run the rules, and say which files they wrote to.

    The rules open what they change with plain `open`, so that is where they
    are watched: nothing in them has to know it is being counted.
    """
    written = set()
    real = open

    def watched(path, mode="r", *rest, **named):
        if any(letter in mode for letter in "wax+"):
            written.add(os.path.abspath(path))
        return real(path, mode, *rest, **named)

    dexpatch.open = watched
    try:
        counts = dexpatch.rewrite_everything(root, literals, dex)
    finally:
        del dexpatch.open
    return counts, sorted(written)


def _outcome(dex_sha: str, smali: Smali, tree: str, room: str,
             written: List[str]) -> str:
    """A name for exactly this rewriting of exactly this dex."""
    parts = [SHAPE.encode(), dex_sha.encode(), str(smali.api).encode(),
             _stamp(smali.jar)]
    for path in written:
        inside = os.path.relpath(path, room)
        with open(path, "rb") as handle:
            now = handle.read()
        try:
            with open(os.path.join(tree, inside), "rb") as handle:
                if handle.read() == now:
                    continue   # written, and written back the same
        except OSError:
            pass
        parts.append(inside.encode("utf-8"))
        parts.append(hashlib.sha256(now).digest())
    return _sha(*parts)


def _save(path: str, data: bytes) -> None:
    part = "%s.%d.part" % (path, os.getpid())
    with open(part, "wb") as handle:
        handle.write(data)
    os.replace(part, path)


def one(job: dict) -> Tuple[str, Optional[str], Dict[str, int], str]:
    """Patch one dex. Runs in a process of its own.

    Hands back where the patched dex is rather than the dex: it is megabytes,
    and it is already on disk.
    """
    name = job["name"]
    dex = job["dex"]
    literals = job["literals"] or {}
    root = job["root"]
    smali = Smali(job["jar"], job["api"], job["jobs"], job["heap"])
    dexpatch.FOUND = dict(job["found"])
    os.makedirs(root, exist_ok=True)

    dex_sha = hashlib.sha256(dex).hexdigest()
    with open(dexpatch.__file__, "rb") as handle:
        rules = handle.read()
    answer = _sha(SHAPE.encode(), dex_sha.encode(), rules, _stamp(smali.jar),
                  repr((smali.api, sorted(literals.items()),
                        sorted(dexpatch.FOUND.items()))).encode("utf-8"))
    answer_path = os.path.join(root, "a-%s.json" % answer)
    try:
        with open(answer_path, encoding="utf-8") as handle:
            said = json.load(handle)
        counts = said["counts"]
        if not counts:
            _touch(answer_path)
            return name, None, {}, "nothing to rewrite, as before"
        out = os.path.join(root, "o-%s.dex" % said["outcome"])
        if os.path.isfile(out):
            _touch(answer_path)
            _touch(out)
            return name, out, counts, "from the cache"
    except (OSError, ValueError, KeyError):
        pass

    scratch = os.path.join(job["workspace"], name.replace(".dex", ""))
    shutil.rmtree(scratch, ignore_errors=True)
    os.makedirs(scratch, exist_ok=True)
    try:
        tree = _pieces(dex, dex_sha, smali, root, scratch)
        room = os.path.join(scratch, "smali")
        _copy(tree, room)
        counts, written = _rewrite(room, literals, dex)
        if not counts:
            _save(answer_path, json.dumps({"counts": {}}).encode("utf-8"))
            return name, None, {}, "nothing to rewrite after all"

        outcome = _outcome(dex_sha, smali, tree, room, written)
        out = os.path.join(root, "o-%s.dex" % outcome)
        if os.path.isfile(out):
            _touch(out)
            said = "rewritten as before, not reassembled"
        else:
            dex_out = os.path.join(scratch, "out.dex")
            smali.assemble(room, dex_out)
            with open(dex_out, "rb") as handle:
                patched = handle.read()
            if dexpatch.dex_format(patched) != dexpatch.dex_format(dex):
                raise RuntimeError(
                    "%s came back as dex %s, it went in as dex %s -- an Android old "
                    "enough to be in the apk's minSdk would refuse to load it"
                    % (name, dexpatch.dex_format(patched), dexpatch.dex_format(dex)))
            _save(out, patched)
            said = "reassembled"
        _save(answer_path, json.dumps({"counts": counts, "outcome": outcome})
              .encode("utf-8"))
        return name, out, counts, said
    finally:
        shutil.rmtree(scratch, ignore_errors=True)


def patch_all(dexes: Dict[str, bytes], jar: str, api: int, workspace: str,
              literals: Optional[Dict[str, str]],
              say: Callable[[str], None]) -> Dict[str, Tuple[Optional[bytes], Dict[str, int]]]:
    """Patch every dex given, and hand back the patched ones by name."""
    names = sorted(dexes)
    if os.environ.get("TOKKIYOU_NO_CACHE"):
        # a place nothing was ever kept in is the same as not looking
        root = os.path.join(workspace, "no-cache")
        shutil.rmtree(root, ignore_errors=True)
    else:
        root = folder()
        prune(root)
    os.makedirs(root, exist_ok=True)

    at_once = workers(len(names))
    cores = os.cpu_count() or 2
    threads = int(os.environ.get("TOKKIYOU_JOBS") or 0) or max(2, (2 * cores) // max(1, at_once))
    threads = min(threads, cores)
    heap = os.environ.get("TOKKIYOU_HEAP") or HEAP
    say("%d dex files, %d at a time" % (len(names), at_once))

    def job(name: str) -> dict:
        return {"name": name, "dex": dexes[name], "literals": literals,
                "root": root, "jar": jar, "api": api, "jobs": threads,
                "heap": heap, "found": dexpatch.FOUND, "workspace": workspace}

    found: Dict[str, Tuple[Optional[bytes], Dict[str, int]]] = {}
    started = time.time()
    # the largest first: they are the ones that decide how long it all takes
    order = sorted(names, key=lambda name: -len(dexes[name]))
    with ProcessPoolExecutor(max_workers=at_once) as pool:
        waiting = [pool.submit(one, job(name)) for name in order]
        try:
            for done in as_completed(waiting):
                name, path, counts, said = done.result()
                patched = None
                if path:
                    with open(path, "rb") as handle:
                        patched = handle.read()
                found[name] = (patched, counts)
                say("%s: %s (%d of %d, %ds)" % (name, said, len(found), len(names),
                                                 time.time() - started))
        except BaseException:
            for other in waiting:
                other.cancel()
            raise
    return found
