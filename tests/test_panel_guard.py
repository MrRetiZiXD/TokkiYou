"""The panel's door: who is shut out, for how long, and who is not."""
import os
import sys
import tempfile
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "server"))

import guard  # noqa: E402


class Clock:
    def __init__(self):
        self.now = 1_800_000_000.0

    def time(self):
        return self.now


class GuardTest(unittest.TestCase):
    def setUp(self):
        self.home = tempfile.TemporaryDirectory()
        self.addCleanup(self.home.cleanup)
        self.clock = Clock()
        self.said = []
        for name, value in (("DB", os.path.join(self.home.name, "badges.db")),
                            ("time", self.clock),
                            ("_tell", lambda kind, text: self.said.append(kind))):
            before = getattr(guard, name)
            setattr(guard, name, value)
            self.addCleanup(setattr, guard, name, before)

    def wrong(self, ip, times=1, token=""):
        for _ in range(times):
            guard.failed(ip, token)

    def test_five_wrong_from_one_address_and_it_waits(self):
        self.wrong("1.1.1.1", 4)
        self.assertEqual(guard.wait("1.1.1.1", ""), 0)
        self.wrong("1.1.1.1")
        self.assertEqual(guard.wait("1.1.1.1", ""), 15 * 60)
        self.assertEqual(guard.wait("2.2.2.2", ""), 0, "nobody else waits for it")
        self.assertEqual(self.said, ["shut"])

    def test_each_time_it_comes_back_the_wait_is_longer(self):
        for expected in (15 * 60, 60 * 60, 6 * 3600, 24 * 3600, 24 * 3600):
            self.wrong("1.1.1.1", 5)
            self.assertEqual(guard.wait("1.1.1.1", ""), expected)
            self.clock.now += expected + 1
            self.assertEqual(guard.wait("1.1.1.1", ""), 0)

    def test_a_week_of_quiet_starts_it_from_the_top(self):
        self.wrong("1.1.1.1", 5)
        self.clock.now += 8 * 86400
        self.wrong("1.1.1.1", 5)
        self.assertEqual(guard.wait("1.1.1.1", ""), 15 * 60)

    def test_wrong_passwords_spread_thin_do_not_add_up_to_a_lockout(self):
        for _ in range(4):
            self.wrong("1.1.1.1")
            self.clock.now += 6 * 60
        self.assertEqual(guard.wait("1.1.1.1", ""), 0)

    def test_many_addresses_shut_the_door_to_strangers_only(self):
        mark = guard.passed("9.9.9.9", "")
        self.assertTrue(mark)
        self.said.clear()
        for n in range(guard.EVERYONE):
            self.wrong("10.0.0.%d" % n)
        self.assertIn("everyone", self.said)
        self.assertGreater(guard.wait("8.8.8.8", ""), 0, "a stranger is refused")
        self.assertGreater(guard.wait("9.9.9.9", ""), 0, "the address alone is not enough")
        self.assertEqual(guard.wait("9.9.9.9", mark), 0, "the browser seen before is let try")
        self.assertGreater(guard.wait("8.8.8.8", "f" * 64), 0,
                         "a made-up mark is a stranger")
        self.clock.now += guard.WINDOW + 1
        self.assertEqual(guard.wait("8.8.8.8", ""), 0, "and it opens again when it stops")

    def test_a_known_browser_is_counted_on_its_own(self):
        mark = guard.passed("5.5.5.5", "")
        self.wrong("5.5.5.5", 5)                       # somebody else on the same address
        self.assertGreater(guard.wait("5.5.5.5", ""), 0)
        self.assertEqual(guard.wait("5.5.5.5", mark), 0)
        self.wrong("5.5.5.5", 5, mark)                 # and its own mistakes are its own
        self.assertEqual(guard.wait("5.5.5.5", mark), 15 * 60)

    def test_the_right_password_wipes_the_slate_and_marks_the_browser_once(self):
        self.wrong("1.1.1.1", 4)
        mark = guard.passed("1.1.1.1", "")
        self.assertEqual(len(mark), 64)
        self.assertEqual(self.said, ["new"])
        self.assertIsNone(guard.passed("1.1.1.1", mark), "a browser already known is not marked again")
        self.assertEqual(self.said, ["new"])
        self.wrong("1.1.1.1", 4)
        self.assertEqual(guard.wait("1.1.1.1", ""), 0)

    def test_a_changed_password_forgets_every_browser(self):
        mark = guard.passed("1.1.1.1", "")
        guard.forget_everyone()
        for n in range(guard.EVERYONE):
            self.wrong("10.0.0.%d" % n)
        self.assertGreater(guard.wait("1.1.1.1", mark), 0)

    def test_the_mark_is_read_out_of_the_cookie_and_nothing_else_is(self):
        self.assertEqual(guard.device("margyt=1.2; margyt_seen=" + "a" * 64), "a" * 64)
        self.assertEqual(guard.device("margyt_seen=short"), "")
        self.assertEqual(guard.device("margyt_seen=" + "a" * 40 + "';--"), "")
        self.assertEqual(guard.device(""), "")

    def test_a_form_from_another_site_is_not_the_panels(self):
        ours = {"Host": "margy.narez.xyz"}
        self.assertTrue(guard.same_site(dict(ours, Origin="https://margy.narez.xyz")))
        self.assertTrue(guard.same_site(dict(ours, Origin="https://margy.narez.xyz:8443")))
        self.assertTrue(guard.same_site(ours), "a tool that says nothing is not a browser")
        self.assertFalse(guard.same_site(dict(ours, Origin="https://evil.example")))
        self.assertFalse(guard.same_site(dict(ours, Origin="null")))
        self.assertFalse(guard.same_site(dict(ours, Origin="https://margy.narez.xyz.evil.example")))


if __name__ == "__main__":
    unittest.main()
