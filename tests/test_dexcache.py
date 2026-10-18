"""The three things the build keeps between runs, with java stood in for."""
import os
import sys
import tempfile
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

from margyt import dexcache, dexpatch  # noqa: E402


class Apart:
    """Takes a "dex" apart into one file a line, and counts how often."""
    taken = 0
    built = 0

    def __init__(self, jar, api, jobs=0, heap=""):
        self.jar, self.api = jar, api

    def disassemble(self, dex_path, out_dir):
        Apart.taken += 1
        os.makedirs(out_dir, exist_ok=True)
        with open(dex_path, "rb") as handle:
            lines = handle.read().decode().split("\n")
        for at, line in enumerate(lines):
            with open(os.path.join(out_dir, "%d.smali" % at), "w") as handle:
                handle.write(line)

    def assemble(self, smali_dir, dex_path):
        Apart.built += 1
        names = sorted(os.listdir(smali_dir), key=lambda n: int(n.split(".")[0]))
        lines = []
        for name in names:
            with open(os.path.join(smali_dir, name)) as handle:
                lines.append(handle.read())
        with open(dex_path, "wb") as handle:
            handle.write("\n".join(lines).encode())


class DexCacheTest(unittest.TestCase):
    def setUp(self):
        self.home = tempfile.TemporaryDirectory()
        self.addCleanup(self.home.cleanup)
        self.swap(dexcache, "Smali", Apart)
        self.swap(dexpatch, "dex_format", lambda dex: "035")
        self.swap(dexpatch, "FOUND", {})
        self.rule = ("pink", "mint")
        self.swap(dexpatch, "rewrite_everything", self.rewrite, create=True)
        Apart.taken = Apart.built = 0

    def swap(self, where, name, value, create=False):
        missing = object()
        before = getattr(where, name, missing)
        setattr(where, name, value)
        def back():
            if before is missing:
                delattr(where, name)
            else:
                setattr(where, name, before)
        self.addCleanup(back)

    def rewrite(self, root, literals, dex=None):
        """What the real rules do: read every file, write the ones that change."""
        old, new = self.rule
        counts = {}
        for name in os.listdir(root):
            path = os.path.join(root, name)
            with dexpatch.open(path) as handle:
                text = handle.read()
            if old in text:
                with dexpatch.open(path, "w") as handle:
                    handle.write(text.replace(old, new))
                counts[old] = counts.get(old, 0) + 1
        return counts

    def job(self, dex=b"a pink\nb\nc pink", found=None, name="classes2.dex"):
        return {"name": name, "dex": dex, "literals": {},
                "root": os.path.join(self.home.name, "kept"), "jar": "smali.jar",
                "api": 23, "jobs": 1, "heap": "1g", "found": found or {},
                "workspace": os.path.join(self.home.name, "work")}

    def read(self, path):
        with open(path, "rb") as handle:
            return handle.read()

    def test_the_same_build_twice_does_the_work_once(self):
        _, path, counts, said = dexcache.one(self.job())
        self.assertEqual((said, counts), ("reassembled", {"pink": 2}))
        self.assertEqual(self.read(path), b"a mint\nb\nc mint")
        _, again, counts, said = dexcache.one(self.job())
        self.assertEqual((said, again, counts), ("from the cache", path, {"pink": 2}))
        self.assertEqual((Apart.taken, Apart.built), (1, 1))

    def test_new_rules_that_rewrite_the_same_do_not_reassemble(self):
        _, path, _, _ = dexcache.one(self.job())
        # the rules are different as far as their name goes, and do the same
        _, again, counts, said = dexcache.one(self.job(found={"a": ("b", "c")}))
        self.assertEqual(said, "rewritten as before, not reassembled")
        self.assertEqual((again, counts), (path, {"pink": 2}))
        self.assertEqual((Apart.taken, Apart.built), (1, 1))

    def test_new_rules_that_rewrite_differently_do(self):
        _, path, _, _ = dexcache.one(self.job())
        self.rule = ("pink", "blue")
        _, other, counts, said = dexcache.one(self.job(found={"a": ("b", "c")}))
        self.assertEqual(said, "reassembled")
        self.assertNotEqual(other, path)
        self.assertEqual(self.read(other), b"a blue\nb\nc blue")
        self.assertEqual(self.read(path), b"a mint\nb\nc mint")
        # taken apart once all the same: the pieces do not depend on the rules
        self.assertEqual((Apart.taken, Apart.built), (1, 2))

    def test_the_kept_pieces_are_never_the_ones_rewritten(self):
        dexcache.one(self.job())
        pieces = os.path.join(self.home.name, "kept", "pieces")
        tree = os.path.join(pieces, os.listdir(pieces)[0], "smali")
        self.assertEqual(self.read(os.path.join(tree, "0.smali")), b"a pink")
        self.assertEqual(self.read(os.path.join(tree, "2.smali")), b"c pink")

    def test_a_dex_with_nothing_in_it_is_remembered_as_such(self):
        _, path, counts, said = dexcache.one(self.job(dex=b"a\nb"))
        self.assertEqual((path, counts), (None, {}))
        _, path, counts, said = dexcache.one(self.job(dex=b"a\nb"))
        self.assertEqual((path, counts, said), (None, {}, "nothing to rewrite, as before"))
        self.assertEqual((Apart.taken, Apart.built), (1, 0))

    def test_two_dex_files_do_not_share_an_answer(self):
        _, one, _, _ = dexcache.one(self.job())
        _, two, _, _ = dexcache.one(self.job(dex=b"x pink", name="classes3.dex"))
        self.assertNotEqual(one, two)
        self.assertEqual(self.read(two), b"x mint")

    def test_the_room_is_left_empty(self):
        dexcache.one(self.job())
        self.assertEqual(os.listdir(os.path.join(self.home.name, "work")), [])

    def test_how_many_at_once(self):
        self.assertEqual(dexcache.workers(1), 1)
        os.environ["MARGYT_WORKERS"] = "3"
        self.addCleanup(os.environ.pop, "MARGYT_WORKERS", None)
        self.assertEqual(dexcache.workers(40), 3)
        self.assertEqual(dexcache.workers(2), 2)


if __name__ == "__main__":
    unittest.main()
