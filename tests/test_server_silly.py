"""The calculator's door on the server, against a database of its own."""

import os
import pathlib
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "server"))

# the server runs on a Python that still has `cgi`; nothing tested here reads a form
try:
    import cgi  # noqa: F401
except ImportError:
    import types
    sys.modules["cgi"] = types.ModuleType("cgi")

import badges
import dep


class SillyServerTests(unittest.TestCase):
    def setUp(self):
        self.room = tempfile.TemporaryDirectory()
        self.was = (badges.DB, badges.WHO)
        badges.DB = os.path.join(self.room.name, "badges.db")
        badges.WHO = os.path.join(self.room.name, "nobody.json")
        badges.prepare()
        db = badges.connect()
        for uid, proved in (("1111111", 1), ("2222222", 1), ("3333333", 0)):
            db.execute("INSERT INTO owner (uid, token, claimed, proved) VALUES (?,?,?,?)",
                       (uid, "key-" + uid, 1, proved))
        db.commit()
        db.close()

    def tearDown(self):
        badges.DB, badges.WHO = self.was
        self.room.cleanup()

    def test_the_calculator_is_for_proved_accounts_and_the_badge_exists(self):
        self.assertEqual(badges.dep_play({"do": "state"}, "ip"), (403, {"error": "prove"}))
        self.assertEqual(badges.dep_play({"do": "state", "uid": "3333333", "token": "key-3333333"}, "ip")[0], 403)
        code, said = badges.dep_play({"do": "state", "uid": "1111111", "token": "key-1111111"}, "ip")
        self.assertEqual((code, said["balance"], said["price"]), (200, dep.START, dep.PRICE))
        db = badges.connect()
        self.assertEqual(db.execute("SELECT title_ru FROM badge WHERE id = ?", (dep.BADGE,)).fetchone(),
                         ("Калькуляторщик",))
        db.close()
        # and the public list of badges still builds with it in
        self.assertIn(dep.BADGE, str(badges.public()))


if __name__ == "__main__":
    unittest.main()
