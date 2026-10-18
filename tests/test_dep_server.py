import pathlib, sqlite3, sys, unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "server"))

import dep


class Told:
    """Luck that says what it was told to, in order."""

    def __init__(self, *numbers):
        self.numbers = list(numbers)

    def randrange(self, below):
        number = self.numbers.pop(0)
        assert number < below, (number, below)
        return number


def table():
    db = sqlite3.connect(":memory:")
    db.executescript("""
        CREATE TABLE badge (id TEXT PRIMARY KEY, colour TEXT, image TEXT, title TEXT, title_ru TEXT,
            title_uk TEXT, text TEXT, text_ru TEXT, text_uk TEXT, button TEXT, claimable INTEGER);
        CREATE TABLE held (uid TEXT, badge TEXT, shown INTEGER DEFAULT 1, place INTEGER DEFAULT 0,
            given_at INTEGER DEFAULT 0, PRIMARY KEY (uid, badge));
    """)
    dep.prepare(db)
    return db


DAY = 86400.0


class DepServerTests(unittest.TestCase):
    def test_the_rules_leave_the_house_a_little_ahead(self):
        paid = sum(dep.pays(a, b, c) for a in range(6) for b in range(6) for c in range(6))
        self.assertEqual(paid, 210)   # for 216 staked
        self.assertEqual(sum(dep.red(n) for n in range(37)), 18)
        self.assertEqual(dep.score([0, 12]), 21)
        self.assertEqual(dep.score([0, 13, 8]), 21)
        self.assertEqual(dep.score([0, 5, 9]), 17)
        self.assertEqual(dep.settles([0, 12], [9, 8]), 5)       # a natural
        self.assertEqual(dep.settles([0, 12], [13, 25]), 2)     # against another one
        self.assertEqual(dep.settles([9, 8, 7], [9, 8]), 0)     # over
        self.assertEqual(dep.settles([9, 8], [9, 7, 8]), 4)     # the dealer over

    def test_the_badge_is_put_in_the_table_once(self):
        db = table()
        dep.prepare(db)
        rows = db.execute("SELECT colour, title_ru FROM badge WHERE id = ?", (dep.BADGE,)).fetchall()
        self.assertEqual(rows, [("7B4A1E", "Калькуляторщик")])

    def test_an_account_starts_with_a_thousand_and_is_given_more_each_day(self):
        db = table()
        code, said = dep.play(db, "1", {"do": "state"}, now=10 * DAY)
        self.assertEqual((code, said["balance"], said["debt"], said["held"]), (200, dep.START, 0, False))
        self.assertNotIn("daily", said)
        _, said = dep.play(db, "1", {"do": "state"}, now=10 * DAY + 3600)
        self.assertEqual(said["balance"], dep.START)
        _, said = dep.play(db, "1", {"do": "state"}, now=11 * DAY + 5)
        self.assertEqual((said["balance"], said["daily"]), (dep.START + dep.DAILY, dep.DAILY))

    def test_the_reels_are_turned_here_and_the_stake_is_cut_to_what_there_is(self):
        db = table()
        code, said = dep.play(db, "1", {"do": "spin", "stake": 5000}, now=DAY, luck=Told(5, 5, 5))
        self.assertEqual((code, said["stake"], said["times"], said["won"]), (200, 1000, 60, 60000))
        self.assertEqual(said["balance"], 60000)
        code, said = dep.play(db, "1", {"do": "spin", "stake": 100}, now=DAY + 1, luck=Told(0, 1, 2))
        self.assertEqual((said["won"], said["balance"]), (0, 59900))
        code, said = dep.play(db, "1", {"do": "spin", "stake": 100}, now=DAY + 2, luck=Told(3, 3, 0))
        self.assertEqual((said["times"], said["balance"]), (1, 59900))

    def test_a_turn_too_soon_after_the_last_is_refused_and_costs_nothing(self):
        db = table()
        dep.play(db, "1", {"do": "spin", "stake": 10}, now=DAY, luck=Told(0, 1, 2))
        code, said = dep.play(db, "1", {"do": "spin", "stake": 10}, now=DAY + 0.1, luck=Told(5, 5, 5))
        self.assertEqual((code, said["error"], said["balance"]), (429, "too often", 990))

    def test_what_is_not_a_stake_is_not_staked(self):
        db = table()
        for turn, stake in enumerate((0, -5, "100", 1.5, True, None)):
            code, said = dep.play(db, "1", {"do": "spin", "stake": stake}, now=DAY + turn * 10)
            self.assertEqual((code, said["error"], said["balance"]), (400, "stake", 1000), stake)

    def test_the_wheel(self):
        db = table()
        _, said = dep.play(db, "1", {"do": "wheel", "stake": 100, "on": "red"}, now=DAY, luck=Told(1))
        self.assertEqual((said["number"], said["won"], said["balance"]), (1, 200, 1100))
        _, said = dep.play(db, "1", {"do": "wheel", "stake": 100, "on": "red"}, now=DAY + 1, luck=Told(0))
        self.assertEqual((said["won"], said["balance"]), (0, 1000))
        _, said = dep.play(db, "1", {"do": "wheel", "stake": 100, "on": "zero"}, now=DAY + 2, luck=Told(0))
        self.assertEqual((said["won"], said["balance"]), (3600, 4500))
        code, said = dep.play(db, "1", {"do": "wheel", "stake": 100, "on": "green"}, now=DAY + 3)
        self.assertEqual((code, said["error"]), (400, "on"))

    def test_a_hand_of_twenty_one_from_deal_to_pay(self):
        db = table()
        # mine 10 and 6, the dealer shows a 9; one more card is a 5, and the dealer draws an 8
        code, said = dep.play(db, "1", {"do": "deal", "stake": 200}, now=DAY, luck=Told(9, 8, 5))
        self.assertEqual((code, said["balance"], said["hand"]["score"], said["hand"]["dealer"]), (200, 800, 16, [8]))
        # nothing else may be staked while a hand is open
        code, said = dep.play(db, "1", {"do": "spin", "stake": 10}, now=DAY + 1)
        self.assertEqual((code, said["error"]), (409, "hand"))
        code, said = dep.play(db, "1", {"do": "hit"}, now=DAY + 2, luck=Told(4, 7))
        self.assertEqual((said["over"], said["mine_score"], said["dealer_score"], said["won"], said["balance"]),
                         (True, 21, 17, 400, 1200))
        self.assertNotIn("hand", said)

    def test_standing_lets_the_dealer_draw_to_seventeen(self):
        db = table()
        dep.play(db, "1", {"do": "deal", "stake": 200}, now=DAY, luck=Told(9, 8, 22))   # 10 + 10, dealer 9
        code, said = dep.play(db, "1", {"do": "stand"}, now=DAY + 1, luck=Told(7))            # dealer 9 + 8
        self.assertEqual((said["over"], said["mine_score"], said["dealer_score"], said["won"], said["balance"]),
                         (True, 20, 17, 400, 1200))
        self.assertNotIn("hand", said)
        code, said = dep.play(db, "1", {"do": "stand"}, now=DAY + 2)
        self.assertEqual((code, said["error"]), (400, "no hand"))

    def test_borrowing_has_a_ceiling_and_repaying_takes_what_there_is(self):
        db = table()
        for n in range(1, 11):
            code, said = dep.play(db, "1", {"do": "borrow"}, now=DAY)
            self.assertEqual((code, said["debt"]), (200, 1000 * n))
        code, said = dep.play(db, "1", {"do": "borrow"}, now=DAY)
        self.assertEqual((code, said["error"], said["debt"], said["balance"]), (400, "owed", 10000, 11000))
        code, said = dep.play(db, "1", {"do": "repay"}, now=DAY)
        self.assertEqual((said["paid"], said["debt"], said["balance"]), (10000, 0, 1000))

    def test_the_badge_costs_the_price_with_nothing_owed(self):
        db = table()
        dep.play(db, "1", {"do": "state"}, now=DAY)
        code, said = dep.play(db, "1", {"do": "buy"}, now=DAY)
        self.assertEqual((code, said["error"]), (400, "short"))
        db.execute("UPDATE dep SET balance = ?, debt = 1000 WHERE uid = '1'", (dep.PRICE + 500,))
        code, said = dep.play(db, "1", {"do": "buy"}, now=DAY)
        self.assertEqual((code, said["error"], said["held"]), (400, "debt", False))
        db.execute("UPDATE dep SET debt = 0 WHERE uid = '1'")
        code, said = dep.play(db, "1", {"do": "buy"}, now=DAY)
        self.assertEqual((code, said["bought"], said["held"], said["balance"]), (200, True, True, 500))
        self.assertEqual(db.execute("SELECT badge FROM held WHERE uid = '1'").fetchall(), [(dep.BADGE,)])
        # and buying it twice takes nothing more
        code, said = dep.play(db, "1", {"do": "buy"}, now=DAY)
        self.assertEqual((code, said["balance"]), (200, 500))

    def test_one_account_cannot_reach_into_another(self):
        db = table()
        dep.play(db, "1", {"do": "spin", "stake": 1000}, now=DAY, luck=Told(0, 1, 2))
        _, said = dep.play(db, "2", {"do": "state"}, now=DAY)
        self.assertEqual(said["balance"], dep.START)


if __name__ == "__main__":
    unittest.main()
