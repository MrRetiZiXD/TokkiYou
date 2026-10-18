import pathlib
import re
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "server"))

import badges


class TokkiYouServerTests(unittest.TestCase):
    def setUp(self):
        self.previous_db = badges.DB
        self.temporary = tempfile.TemporaryDirectory()
        badges.DB = str(pathlib.Path(self.temporary.name) / "badges.db")
        badges.prepare()
        self.addCleanup(self.restore_database)

    def restore_database(self):
        badges.DB = self.previous_db
        self.temporary.cleanup()

    def add_owner(self, proved):
        db = badges.connect()
        db.execute(
            "INSERT INTO owner (uid, token, claimed, written, proved, proved_at, name)"
            " VALUES (?, ?, ?, ?, ?, ?, ?)",
            ("123456789", "secret", 1, 0, int(proved), 1 if proved else 0, "user"),
        )
        db.commit()
        db.close()

    def test_unverified_accounts_do_not_receive_the_tokkiyou_badge(self):
        self.add_owner(False)
        status, response = badges.claim("123456789", "test")
        self.assertEqual(status, 200)
        self.assertFalse(response["proved"])
        self.assertEqual(response["badges"], [])

    def test_verified_accounts_receive_the_badge_once_and_publicly(self):
        self.add_owner(True)
        first_status, first = badges.claim("123456789", "test")
        second_status, second = badges.claim("123456789", "test")
        self.assertEqual((first_status, second_status), (200, 200))
        self.assertTrue(first["proved"])
        self.assertEqual([row["id"] for row in first["badges"]], [badges.TOKKIYOU_BADGE])
        self.assertEqual([row["id"] for row in second["badges"]], [badges.TOKKIYOU_BADGE])
        public = badges.public()
        record = next(row for row in public["badges"] if row["id"] == badges.TOKKIYOU_BADGE)
        self.assertEqual(record["users"], ["123456789"])

    def test_verified_account_can_set_a_gradient_without_supporter_badge(self):
        self.add_owner(True)
        status, response = badges.gradient(
            {"uid": "123456789", "token": "secret", "colours": ["123abc", "abcdef"]},
            "test",
        )
        self.assertEqual(status, 200)
        self.assertEqual(response["colours"], ["123ABC", "ABCDEF"])
        self.assertEqual(badges.public()["gradients"]["123456789"], ["123ABC", "ABCDEF"])

    def test_profile_proof_code_is_tokkiyou_plus_six_digits(self):
        status, response = badges.prove({"uid": "123456789"}, "test")
        self.assertEqual(status, 200)
        self.assertRegex(response["code"], re.compile(r"^tokkiyou-\d{6}$"))


if __name__ == "__main__":
    unittest.main()
