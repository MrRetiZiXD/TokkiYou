"""What the patcher app asks the server: who somebody is, and what is early.

The app hands out the mods. The early versions of them are for the people who
support the project, and those people are known one way only: by being in the
supporters' group on Telegram. So the app has to learn somebody's Telegram
account without ever being trusted with it:

    the app asks for a one-time code            POST /patcher/login/start
    and opens the bot with it                   t.me/<bot>?start=p_<code>
    the person presses Start; the bot now
    knows who pressed, and writes it down       bot.py -> claim()
    the app, which has been asking, is given
    a token, a name, and whether that person
    is a supporter                              GET /patcher/login/check

After that the token is all the app keeps. `GET /patcher/me` says again
whether its owner is a supporter -- asked of Telegram each time, a little
while apart, because people leave groups -- and `GET /patcher/beta` gives a
supporter the early releases and anybody else an empty list.

Which group it is, is a chat id in supporters.txt. The owner sets it by
sending /supporters in the group, once the bot has been added there.
"""

import json
import os
import secrets
import sqlite3
import threading
import time
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
DB = os.path.join(HERE, "patcher.db")
TOKEN_FILE = os.path.join(HERE, "bot.txt")
GROUP_FILE = os.path.join(HERE, "supporters.txt")
BETA_FILE = os.path.join(HERE, "patcher-beta.json")

#: how long somebody has to press Start, and how long an answer about the group is believed
CODE_LIVES = 600
ASK_AGAIN = 600
#: no more codes than this from one address in an hour
CODES_AN_HOUR = 20

SCHEMA = """
    CREATE TABLE IF NOT EXISTS login (
        code    TEXT PRIMARY KEY,
        made    INTEGER NOT NULL,
        ip      TEXT NOT NULL DEFAULT '',
        who     INTEGER,
        name    TEXT NOT NULL DEFAULT ''
    );
    CREATE TABLE IF NOT EXISTS token (
        token   TEXT PRIMARY KEY,
        who     INTEGER NOT NULL,
        name    TEXT NOT NULL DEFAULT '',
        made    INTEGER NOT NULL
    );
"""

_local = threading.local()
_asked = {}
_asked_lock = threading.Lock()
_bot_name = []


def db():
    link = getattr(_local, "link", None)
    if link is None:
        link = sqlite3.connect(DB, timeout=10)
        link.executescript(SCHEMA)
        _local.link = link
    return link


def _telegram(what, **fields):
    try:
        with open(TOKEN_FILE) as handle:
            token = handle.read().strip()
    except OSError:
        return {}
    data = urllib.parse.urlencode(fields).encode()
    try:
        with urllib.request.urlopen(
                "https://api.telegram.org/bot%s/%s" % (token, what), data, timeout=15) as answer:
            return json.loads(answer.read())
    except Exception:
        return {}


def bot_name():
    if not _bot_name:
        name = _telegram("getMe").get("result", {}).get("username")
        if name:
            _bot_name.append(name)
    return _bot_name[0] if _bot_name else ""


def group():
    try:
        with open(GROUP_FILE) as handle:
            return int(handle.read().strip())
    except (OSError, ValueError):
        return None


def set_group(chat):
    with open(GROUP_FILE, "w") as handle:
        handle.write(str(int(chat)))
    with _asked_lock:
        _asked.clear()


def supporter(who):
    """Whether this Telegram account is in the supporters' group right now."""
    chat = group()
    if chat is None or not who:
        return False
    now = time.time()
    with _asked_lock:
        known = _asked.get(who)
        if known and now - known[0] < ASK_AGAIN:
            return known[1]
    answer = _telegram("getChatMember", chat_id=chat, user_id=who)
    member = answer.get("result", {})
    status = member.get("status")
    inside = status in ("creator", "administrator", "member") or (
        status == "restricted" and member.get("is_member", False))
    if answer:
        with _asked_lock:
            _asked[who] = (now, inside)
    return inside


# ------------------------------------------------------------ what the app asks

def start(ip):
    link = db()
    now = int(time.time())
    link.execute("DELETE FROM login WHERE made < ?", (now - CODE_LIVES,))
    recent = link.execute("SELECT COUNT(*) FROM login WHERE ip = ? AND made > ?",
                          (ip, now - 3600)).fetchone()[0]
    if recent >= CODES_AN_HOUR:
        link.commit()
        return 429, {"error": "too many"}
    name = bot_name()
    if not name:
        link.commit()
        return 503, {"error": "the bot is not answering"}
    code = secrets.token_hex(12)
    link.execute("INSERT INTO login (code, made, ip) VALUES (?, ?, ?)", (code, now, ip))
    link.commit()
    return 200, {"code": code, "link": "https://t.me/%s?start=p_%s" % (name, code)}


def check(code):
    link = db()
    now = int(time.time())
    row = link.execute("SELECT who, name, made FROM login WHERE code = ?", (str(code),)).fetchone()
    if not row or now - row[2] > CODE_LIVES:
        return 404, {"state": "gone"}
    if row[0] is None:
        return 200, {"state": "waiting"}
    token = secrets.token_hex(24)
    link.execute("INSERT INTO token (token, who, name, made) VALUES (?, ?, ?, ?)",
                 (token, row[0], row[1], now))
    link.execute("DELETE FROM login WHERE code = ?", (str(code),))
    link.commit()
    return 200, {"state": "ok", "token": token, "name": row[1], "supporter": supporter(row[0])}


def _owner(bearer):
    token = (bearer or "").strip()
    if token.lower().startswith("bearer "):
        token = token[7:].strip()
    if not token:
        return None
    return db().execute("SELECT who, name FROM token WHERE token = ?", (token,)).fetchone()


def me(bearer):
    row = _owner(bearer)
    if not row:
        return 401, {"error": "not signed in"}
    return 200, {"name": row[1], "supporter": supporter(row[0])}


def beta(bearer):
    row = _owner(bearer)
    if not row:
        return 401, {"error": "not signed in"}
    if not supporter(row[0]):
        return 200, {"mods": {}}
    try:
        with open(BETA_FILE, encoding="utf-8") as handle:
            return 200, json.load(handle)
    except (OSError, ValueError):
        return 200, {"mods": {}}


# ------------------------------------------------------------ what the bot tells

def claim(code, who, name):
    """Somebody pressed Start with this code. True if it was one of ours and still good."""
    link = db()
    now = int(time.time())
    done = link.execute(
        "UPDATE login SET who = ?, name = ? WHERE code = ? AND who IS NULL AND made > ?",
        (int(who), (name or "")[:64], str(code), now - CODE_LIVES)).rowcount
    link.commit()
    return done == 1
