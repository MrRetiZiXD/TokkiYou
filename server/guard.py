"""Keeping the panel's door shut on somebody trying every key.

One name and one password open the panel, and until now the only thing in the
way of guessing them was how fast nginx let a request through: sixty a minute
from one address, every minute of every day, from as many addresses as
somebody cares to rent.

So wrong passwords are counted, and counted where a restart does not forget
them.

  An address that gets it wrong five times in a quarter of an hour is not
  listened to for a quarter of an hour, and the next time it earns that it is
  an hour, then six, then a day.

  That stops one address. Somebody with a thousand of them gets five tries
  from each -- so wrong passwords are also counted over all of them, and
  when there are too many the door shuts to everybody it has not seen before.

  "Seen before" is what keeps that from shutting the owner out of their own
  panel, which is otherwise the cheapest attack there is. A browser that has
  signed in once is handed a long random mark to keep, and a browser showing
  one is counted on its own: other people's mistakes, and a shut door, are
  not held against it.

Being shut out, and a sign-in from a browser never seen before, are both said
to the owner in Telegram, through the bot that is already here.
"""

import hashlib
import json
import os
import secrets
import sqlite3
import threading
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
DB = os.path.join(HERE, "badges.db")

#: wrong passwords from one address, within WINDOW, before it has to wait
TRIES = 5
WINDOW = 15 * 60
#: how long it waits: longer every time it comes back for more
WAITS = (15 * 60, 60 * 60, 6 * 3600, 24 * 3600)
#: after this long without being shut out, an address starts from the top
FORGIVEN = 7 * 86400

#: wrong passwords from everywhere within WINDOW before strangers are refused
EVERYONE = 25

#: how long a browser stays known without signing in again
KNOWN = 180 * 86400

#: the same person the bot answers to
OWNER = 7826361017
TOKEN_FILE = os.path.join(HERE, "bot.txt")


def _db():
    db = sqlite3.connect(DB, timeout=10)
    db.executescript("""
        CREATE TABLE IF NOT EXISTS knock (
            who TEXT NOT NULL,
            at  INTEGER NOT NULL
        );
        CREATE INDEX IF NOT EXISTS knock_at ON knock (at);
        CREATE TABLE IF NOT EXISTS shut (
            who   TEXT PRIMARY KEY,
            until INTEGER NOT NULL,
            times INTEGER NOT NULL,
            at    INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS known (
            mark TEXT PRIMARY KEY,
            seen INTEGER NOT NULL
        );
    """)
    return db


def _mark(token):
    """What is kept of a browser's mark: enough to recognise it, not to forge it."""
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def device(cookie):
    """The mark a browser is showing, or nothing."""
    for piece in (cookie or "").split(";"):
        piece = piece.strip()
        if piece.startswith("margyt_seen="):
            value = piece[len("margyt_seen="):]
            if 32 <= len(value) <= 128 and value.isalnum():
                return value
    return ""


def _who(db, ip, token):
    """Who is counted: a browser seen before by its mark, anybody else by address."""
    if token:
        mark = _mark(token)
        row = db.execute("SELECT seen FROM known WHERE mark = ?", (mark,)).fetchone()
        if row and row[0] > time.time() - KNOWN:
            return "seen:" + mark, True
    return "at:" + (ip or "?"), False


def wait(ip, token):
    """How many seconds until this caller may try a password, 0 for now."""
    now = int(time.time())
    db = _db()
    try:
        who, known = _who(db, ip, token)
        row = db.execute("SELECT until FROM shut WHERE who = ?", (who,)).fetchone()
        if row and row[0] > now:
            return row[0] - now
        if not known:
            everywhere = db.execute("SELECT COUNT(*) FROM knock WHERE at > ?",
                                    (now - WINDOW,)).fetchone()[0]
            if everywhere >= EVERYONE:
                oldest = db.execute(
                    "SELECT at FROM knock WHERE at > ? ORDER BY at DESC LIMIT 1 OFFSET ?",
                    (now - WINDOW, EVERYONE - 1)).fetchone()
                return max(60, (oldest[0] if oldest else now) + WINDOW - now)
        return 0
    finally:
        db.close()


def failed(ip, token):
    """A wrong password. Counts it, and shuts the door if that was one too many."""
    now = int(time.time())
    db = _db()
    try:
        who, _known = _who(db, ip, token)
        db.execute("DELETE FROM knock WHERE at < ?", (now - 2 * 86400,))
        db.execute("INSERT INTO knock (who, at) VALUES (?, ?)", (who, now))
        row = db.execute("SELECT times, at FROM shut WHERE who = ?", (who,)).fetchone()
        # what was counted towards the last time it was shut out is spent,
        # and is not counted towards the next
        since = max(now - WINDOW, row[1] if row else 0)
        mine = db.execute("SELECT COUNT(*) FROM knock WHERE who = ? AND at > ?",
                          (who, since)).fetchone()[0]
        everywhere = db.execute("SELECT COUNT(*) FROM knock WHERE at > ?",
                                (now - WINDOW,)).fetchone()[0]
        if mine >= TRIES:
            times = row[0] if row and row[1] > now - FORGIVEN else 0
            length = WAITS[min(times, len(WAITS) - 1)]
            db.execute("INSERT INTO shut (who, until, times, at) VALUES (?,?,?,?)"
                       " ON CONFLICT(who) DO UPDATE SET until = excluded.until,"
                       " times = excluded.times, at = excluded.at",
                       (who, now + length, times + 1, now))
            _tell("shut", "Панель TokkiYou: %d неверных паролей подряд с адреса %s. "
                          "Этому адресу вход закрыт на %s."
                  % (TRIES, ip or "?", _span(length)))
        elif everywhere == EVERYONE:
            _tell("everyone", "Панель TokkiYou: пароль подбирают с разных адресов, "
                              "%d неверных попыток за четверть часа. Незнакомым "
                              "браузерам вход закрыт, пока это не прекратится; "
                              "ваш браузер войдёт как обычно." % everywhere)
        db.commit()
    finally:
        db.close()


def passed(ip, token):
    """The right password. Hands back a mark for the browser to keep, if it has none."""
    now = int(time.time())
    db = _db()
    try:
        who, known = _who(db, ip, token)
        db.execute("DELETE FROM knock WHERE who = ?", (who,))
        db.execute("DELETE FROM shut WHERE who = ?", (who,))
        db.execute("DELETE FROM known WHERE seen < ?", (now - KNOWN,))
        fresh = None
        if known:
            db.execute("UPDATE known SET seen = ? WHERE mark = ?", (now, _mark(token)))
        else:
            fresh = secrets.token_hex(32)
            db.execute("INSERT INTO known (mark, seen) VALUES (?, ?)", (_mark(fresh), now))
            _tell("new", "Панель TokkiYou: вход с нового браузера, адрес %s. "
                         "Если это не вы, смените пароль в панели: это выкинет "
                         "всех, кто вошёл." % (ip or "?"))
        db.commit()
        return fresh
    finally:
        db.close()


def forget_everyone():
    """No browser is known any more. For when the password has been changed."""
    db = _db()
    try:
        db.execute("DELETE FROM known")
        db.commit()
    finally:
        db.close()


def same_site(headers):
    """Whether a form was sent from the panel's own pages.

    A browser says where a form came from, and one sent from somebody else's
    page while the owner happens to be signed in is the oldest trick there is.
    A request that does not say is let through: it is the cookie's own rule
    that keeps those out, and a tool with no browser behind it says nothing.
    """
    origin = headers.get("Origin") or ""
    if not origin:
        return True
    host = (headers.get("Host") or "").lower().rsplit(":", 1)[0]
    # the same name on another port is the same panel: there is a spare one
    said = origin.split("://", 1)[-1].lower().rsplit(":", 1)[0]
    return bool(host) and said == host


def _span(seconds):
    if seconds >= 86400:
        return "сутки"
    if seconds >= 3600:
        hours = seconds // 3600
        return "%d ч" % hours
    return "%d мин" % (seconds // 60)


_told = {}
_telling = threading.Lock()


def _tell(kind, text):
    """Say it to the owner, and not more often than they can read.

    Somebody with a thousand addresses would otherwise be a thousand messages.
    """
    with _telling:
        now = time.time()
        if kind != "new" and now - _told.get(kind, 0) < 600:
            return
        _told[kind] = now
    threading.Thread(target=_send, args=(text,), daemon=True).start()


def _send(text):
    try:
        with open(TOKEN_FILE, encoding="utf-8") as handle:
            token = handle.read().strip()
        if not token:
            return
        request = urllib.request.Request(
            "https://api.telegram.org/bot%s/sendMessage" % token,
            data=json.dumps({"chat_id": OWNER, "text": text}).encode("utf-8"),
            headers={"Content-Type": "application/json"})
        urllib.request.urlopen(request, timeout=15).read()
    except Exception:
        pass  # no bot, no network: the door is shut all the same
