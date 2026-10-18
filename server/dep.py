"""The Dep Calculator's money, kept where an edited apk cannot reach it.

The calculator in the mod is a joke with a prize at the end of it: a badge that
costs more dep coins than anybody is likely to see. A prize is only worth
having if it cannot be typed into a settings file, so nothing about the money
is decided on the phone. The balance lives here, the reels are turned here,
the cards are dealt here, and the phone is told how it came out.

Every game is a little worse than even, on purpose. Coins arrive once a day
and by borrowing, and what is borrowed has to be paid back before anything can
be bought -- so the only way to the badge is to be lucky for a long time, which
is the joke.

Only a proved account plays. The key is the one `/prove/check` hands out, so
nobody can stake coins in somebody else's name.
"""

import json
import secrets
import time

#: what an account starts with, and what it is given the first time it looks in each day
START = 1000
DAILY = 1000
#: one loan, and how much may be owed at once
LOAN = 1000
MOST_OWED = 10000
#: the badge, and what it costs with nothing owed
BADGE = "calculator"
PRICE = 1000000
#: the least time between two turns, so a script gains nothing over a thumb
PAUSE = 0.3
SYMBOLS = 6

SCHEMA = """
    CREATE TABLE IF NOT EXISTS dep (
        uid     TEXT PRIMARY KEY,
        balance INTEGER NOT NULL,
        debt    INTEGER NOT NULL DEFAULT 0,
        day     INTEGER NOT NULL DEFAULT 0,
        hand    TEXT NOT NULL DEFAULT '',
        acted   REAL NOT NULL DEFAULT 0
    );
"""

#: the badge as the table of badges holds it, added the first time the server starts with this
BADGE_ROW = {
    "id": BADGE, "colour": "7B4A1E", "image": "calculator.png",
    "title": "Calculator", "title_ru": "Калькуляторщик", "title_uk": "Калькуляторник",
    "text": "bought the badge in time, in the collab with Dep Calculator",
    "text_ru": "успел купить значок в коллаборации с Деп калькулятором",
    "text_uk": "встиг купити значок у колаборації з Деп калькулятором",
    "button": "Got it", "claimable": 0,
}

LUCK = secrets.SystemRandom()


def prepare(db):
    db.executescript(SCHEMA)
    if not db.execute("SELECT 1 FROM badge WHERE id = ?", (BADGE,)).fetchone():
        keys = sorted(BADGE_ROW)
        db.execute("INSERT INTO badge (%s) VALUES (%s)" % (", ".join(keys), ",".join("?" * len(keys))),
                   [BADGE_ROW[key] for key in keys])


# ------------------------------------------------------------- the rules

def pays(a, b, c):
    """What three reels pay, as a multiple of the stake.

    Sixty for sevens, twelve for any other three alike, the stake back for a
    pair. Out of 216 ways the reels can stop that comes to 210 stakes paid
    for 216 taken, which is the house's three in a hundred.
    """
    if a == b == c:
        return 60 if a == SYMBOLS - 1 else 12
    if a == b or b == c or a == c:
        return 1
    return 0


def red(number):
    if number <= 0 or number > 36:
        return False
    low = number <= 10 or 19 <= number <= 28
    return low == (number % 2 == 1)


def score(cards):
    """A hand at twenty-one: pictures are ten, an ace eleven until that is too many."""
    total, aces = 0, 0
    for card in cards:
        rank = card % 13
        if rank == 0:
            aces += 1
            total += 11
        else:
            total += min(rank + 1, 10)
    while total > 21 and aces:
        total -= 10
        aces -= 1
    return total


def settles(mine, dealer):
    """How a finished hand pays, in halves of the stake: 0, 2, 4, or 5 for a natural."""
    have, theirs = score(mine), score(dealer)
    if have > 21:
        return 0
    if have == 21 and len(mine) == 2 and not (theirs == 21 and len(dealer) == 2):
        return 5
    if theirs > 21 or have > theirs:
        return 4
    return 2 if have == theirs else 0


def draw(used, luck):
    while True:
        card = luck.randrange(52)
        if card not in used:
            return card


# -------------------------------------------------------------- the play

class Purse:
    """One account's row, read at the start of a request and written at the end."""

    def __init__(self, db, uid, now):
        self.db, self.uid = db, uid
        self.said = {}
        today = int(now // 86400)
        row = db.execute("SELECT balance, debt, day, hand, acted FROM dep WHERE uid = ?",
                         (uid,)).fetchone()
        if row is None:
            self.balance, self.debt, self.day, hand, self.acted = START, 0, today, "", 0.0
            db.execute("INSERT INTO dep (uid, balance, debt, day) VALUES (?,?,?,?)",
                       (uid, START, 0, today))
        else:
            self.balance, self.debt, self.day, hand, self.acted = row
            if self.day < today:
                self.balance += DAILY
                self.day = today
                self.said["daily"] = DAILY
        self.hand = json.loads(hand) if hand else None
        self.held = db.execute("SELECT 1 FROM held WHERE uid = ? AND badge = ?",
                               (uid, BADGE)).fetchone() is not None

    def answer(self, status, extra=None):
        self.db.execute(
            "UPDATE dep SET balance = ?, debt = ?, day = ?, hand = ?, acted = ? WHERE uid = ?",
            (self.balance, self.debt, self.day, json.dumps(self.hand) if self.hand else "",
             self.acted, self.uid))
        self.db.commit()
        out = {"balance": self.balance, "debt": self.debt, "price": PRICE,
               "most_owed": MOST_OWED, "loan": LOAN, "daily_gift": DAILY, "held": self.held}
        if self.hand:
            # the dealer's second card is not dealt until the hand is over,
            # so there is nothing here for a curious phone to read
            out["hand"] = {"stake": self.hand["stake"], "mine": self.hand["mine"],
                           "dealer": self.hand["dealer"], "score": score(self.hand["mine"])}
        out.update(self.said)
        if extra:
            out.update(extra)
        return status, out

    def finish(self, luck):
        """The dealer draws to seventeen and the hand is paid."""
        hand = self.hand
        mine, dealer = hand["mine"], hand["dealer"]
        while score(mine) <= 21 and score(dealer) < 17:
            dealer.append(draw(mine + dealer, luck))
        won = hand["stake"] * settles(mine, dealer) // 2
        self.balance += won
        self.hand = None
        return self.answer(200, {"stake": hand["stake"], "mine": mine, "dealer": dealer,
                                 "mine_score": score(mine), "dealer_score": score(dealer),
                                 "won": won, "over": True})


def play(db, uid, body, now=None, luck=None):
    """One request from a proved account. Returns (status, answer).

    The answer always carries the whole state, so the phone never has to
    work a balance out for itself.
    """
    now = time.time() if now is None else now
    luck = LUCK if luck is None else luck
    purse = Purse(db, uid, now)
    action = body.get("do", "state")

    if action == "state":
        return purse.answer(200)

    if action == "borrow":
        if purse.debt + LOAN > MOST_OWED:
            return purse.answer(400, {"error": "owed"})
        purse.balance += LOAN
        purse.debt += LOAN
        return purse.answer(200)

    if action == "repay":
        paid = min(purse.balance, purse.debt)
        purse.balance -= paid
        purse.debt -= paid
        return purse.answer(200, {"paid": paid})

    if action == "buy":
        if purse.held:
            return purse.answer(200)
        if purse.debt > 0:
            return purse.answer(400, {"error": "debt"})
        if purse.balance < PRICE:
            return purse.answer(400, {"error": "short"})
        purse.balance -= PRICE
        db.execute("INSERT OR IGNORE INTO held (uid, badge, given_at) VALUES (?,?,?)",
                   (uid, BADGE, int(now)))
        purse.held = True
        return purse.answer(200, {"bought": True})

    # everything from here is a turn at a game
    if now - purse.acted < PAUSE:
        return purse.answer(429, {"error": "too often"})
    purse.acted = now

    if action in ("hit", "stand"):
        if not purse.hand:
            return purse.answer(400, {"error": "no hand"})
        if action == "hit":
            hand = purse.hand
            hand["mine"].append(draw(hand["mine"] + hand["dealer"], luck))
            if score(hand["mine"]) < 21:
                return purse.answer(200)
        return purse.finish(luck)

    if purse.hand:
        return purse.answer(409, {"error": "hand"})

    stake = body.get("stake")
    if not isinstance(stake, int) or isinstance(stake, bool) or stake < 1:
        return purse.answer(400, {"error": "stake"})
    if purse.balance <= 0:
        return purse.answer(400, {"error": "broke"})
    stake = min(stake, purse.balance)

    if action == "spin":
        reels = [luck.randrange(SYMBOLS) for _ in range(3)]
        times = pays(*reels)
        won = stake * times
        purse.balance += won - stake
        return purse.answer(200, {"stake": stake, "reels": reels, "times": times, "won": won})

    if action == "wheel":
        on = body.get("on")
        if on not in ("red", "black", "zero"):
            return purse.answer(400, {"error": "on"})
        number = luck.randrange(37)
        if on == "zero":
            won = stake * 36 if number == 0 else 0
        else:
            won = stake * 2 if number and red(number) == (on == "red") else 0
        purse.balance += won - stake
        return purse.answer(200, {"stake": stake, "number": number, "won": won})

    if action == "deal":
        purse.balance -= stake
        mine = [draw([], luck)]
        dealer = [draw(mine, luck)]
        mine.append(draw(mine + dealer, luck))
        purse.hand = {"stake": stake, "mine": mine, "dealer": dealer}
        if score(mine) == 21:
            return purse.finish(luck)
        return purse.answer(200, {"stake": stake})

    return purse.answer(400, {"error": "do"})
