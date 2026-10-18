#!/usr/bin/env python3
"""The badge server.

Badges used to be a file in the repository that every phone re-read every five
minutes. That worked and cost nothing, and it had two limits worth leaving it
for: granting one meant a commit, and nobody could decide anything about their
own -- which of theirs to show, and in what order.

So this holds them instead. It is deliberately small: the standard library, one
sqlite file, and nginx in front for rate limiting. There is no framework here
because there is nothing a framework would do.

    GET  /badges            what every phone reads
    POST /claim             a phone asks what an account holds
    POST /prove             a code to put in that account's bio
    POST /prove/check       the page is read, and the account gets its key
    POST /profile           that account decides what to show and in what order
    POST /old               the badge anyone running the mod before the 24th gets
    GET  /icon/<name>.png   the pictures badges are drawn with

On authentication: the key to an account is given to whoever can change what
that account's profile page says. The server hands out a short code, the
person puts it in their bio, the server reads the page and sees it there.
Nobody else can put anything in somebody else's bio, so nobody else gets the
key.

This replaces first-come-first-served, which lasted exactly as long as it took
somebody to write a loop: fifty-odd badges were handed to accounts that had
never run the mod, and a million requests went through asking for keys to
accounts by id. Every key made under the old rule has been thrown away.

What is enforced, and enforced here rather than in the app where it could be
edited out: which badges a claim may grant, how often anything may be written,
and when the free badge stops being free.
"""

import cgi
import hashlib
import io
import json
import os
import re
import secrets
import sqlite3
import threading
import time
import urllib.request
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

import dep
import guard
import panel
import patcher
import tiktok

HERE = os.path.dirname(os.path.abspath(__file__))
DB = os.path.join(HERE, "badges.db")
ICONS = os.path.join(HERE, "icons")
PLUGINS = os.path.join(HERE, "plugins")

#: who may use the panel. Read from a file so it is not in the source, and
#: written by hand on the server rather than by anything here.
WHO = os.path.join(HERE, "admin.txt")

#: the free badge, and the moment it stops being given out. After this the
#: badge stays on everyone who took it and is offered to nobody -- which is
#: what makes it worth having and what makes its wording true.
FREE = "old"
FREE_UNTIL = 1790197200          # 2026-09-24 00:00 UTC+3

#: how often one account, or one address, may change anything. It used to be
#: a minute, back when anybody could write as anybody; now a write costs a
#: proved account, so the wait is only there to stop a stuck finger
EVERY = 5

MOST_BADGES = 16

#: the badge that makes somebody a supporter, and the two things it unlocks
SUPPORTER = "supporter"
TOKKIYOU_BADGE = "tokkiyou"

#: how often a gradient may be changed. Not a rule of taste: it is seen by
#: everyone, and does not need changing by the second
GRADIENT_WAIT = 60

#: as many colours as a gradient is worth having
MOST_COLOURS = 5

#: a crash report: how big one may be, how many are kept, how many an address
#: may send in an hour. The phone sends nothing until its owner has said yes.
CRASH_MOST = 32 * 1024
CRASH_KEEP = 500
CRASH_EACH = 20

#: proving an account is yours: how long a code is good for, and how many
#: times a page may be read before the code is spent. Long enough to open
#: TikTok, paste and come back; short enough that a code left in a bio is not
#: a key lying around.
PROOF_LIVES = 30 * 60
PROOF_TRIES = 15

#: a code with less than this left is not handed out again. Somebody who
#: opens the card gets a code they have time to use rather than the tail end
#: of one somebody asked for half an hour ago
PROOF_SPARE = 10 * 60

#: how many checks may read TikTok in a minute, all callers together. A name
#: is remembered once it is known, so a check usually costs one request.
#: Per-address limits are nginx's job; this one is about TikTok's patience
#: with this address, which the bot depends on as well. Twenty was too mean
#: on an evening when everybody was proving an account at once.
PROOF_READS = 60


# --------------------------------------------------------------- the store

def connect():
    db = sqlite3.connect(DB, timeout=10)
    db.execute("PRAGMA journal_mode=WAL")
    return db


def prepare():
    db = connect()
    db.executescript("""
        CREATE TABLE IF NOT EXISTS badge (
            id      TEXT PRIMARY KEY,
            colour  TEXT NOT NULL DEFAULT '',
            image   TEXT NOT NULL DEFAULT '',
            title   TEXT NOT NULL DEFAULT '',
            title_ru TEXT NOT NULL DEFAULT '',
            title_uk TEXT NOT NULL DEFAULT '',
            text    TEXT NOT NULL DEFAULT '',
            text_ru TEXT NOT NULL DEFAULT '',
            text_uk TEXT NOT NULL DEFAULT '',
            button  TEXT NOT NULL DEFAULT '',
            claimable INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE IF NOT EXISTS held (
            uid      TEXT NOT NULL,
            badge    TEXT NOT NULL,
            shown    INTEGER NOT NULL DEFAULT 1,
            place    INTEGER NOT NULL DEFAULT 0,
            given_at INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY (uid, badge)
        );
        CREATE TABLE IF NOT EXISTS owner (
            uid       TEXT PRIMARY KEY,
            token     TEXT NOT NULL,
            claimed   INTEGER NOT NULL,
            written   INTEGER NOT NULL DEFAULT 0,
            proved    INTEGER NOT NULL DEFAULT 0,
            proved_at INTEGER NOT NULL DEFAULT 0,
            name      TEXT NOT NULL DEFAULT ''
        );
        CREATE TABLE IF NOT EXISTS admin (
            user    TEXT PRIMARY KEY,
            salt    TEXT NOT NULL,
            hash    TEXT NOT NULL,
            secret  TEXT NOT NULL,
            changed INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE IF NOT EXISTS proofs (
            uid      TEXT NOT NULL,
            holder   TEXT NOT NULL,
            code     TEXT NOT NULL,
            made     INTEGER NOT NULL DEFAULT 0,
            tried    INTEGER NOT NULL DEFAULT 0,
            last     INTEGER NOT NULL DEFAULT 0,
            was      TEXT NOT NULL DEFAULT '',
            was_made INTEGER NOT NULL DEFAULT 0,
            ip       TEXT NOT NULL DEFAULT '',
            PRIMARY KEY (uid, holder)
        );
        CREATE TABLE IF NOT EXISTS gradient (
            uid     TEXT PRIMARY KEY,
            colours TEXT NOT NULL,
            changed INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE IF NOT EXISTS crash (
            id   INTEGER PRIMARY KEY AUTOINCREMENT,
            at   INTEGER NOT NULL,
            mod  TEXT NOT NULL DEFAULT '',
            what TEXT NOT NULL DEFAULT '',
            body TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS face (
            id   TEXT PRIMARY KEY,
            url  TEXT NOT NULL,
            seen INTEGER NOT NULL DEFAULT 0,
            told TEXT NOT NULL DEFAULT ''
        );
        CREATE TABLE IF NOT EXISTS visitor (
            ip      TEXT PRIMARY KEY,
            written INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE IF NOT EXISTS plugin (
            id      TEXT PRIMARY KEY,
            name    TEXT NOT NULL DEFAULT '',
            version TEXT NOT NULL DEFAULT '',
            author  TEXT NOT NULL DEFAULT '',
            about   TEXT NOT NULL DEFAULT '',
            tiktok  TEXT NOT NULL DEFAULT '',
            file    TEXT NOT NULL DEFAULT '',
            icon    TEXT NOT NULL DEFAULT '',
            size    INTEGER NOT NULL DEFAULT 0,
            added   INTEGER NOT NULL DEFAULT 0
        );
    """)
    # the name telegram gives a picture came after the table did, and a
    # server that is already running is not going to grow the column by
    # being asked for the table again
    if "told" not in [row[1] for row in db.execute("PRAGMA table_info(face)")]:
        db.execute("ALTER TABLE face ADD COLUMN told TEXT NOT NULL DEFAULT ''")
    # a badge grew a short name after it had a description: the description is
    # a sentence and belongs in the popup, the name is what it is called
    badge_columns = [row[1] for row in db.execute("PRAGMA table_info(badge)")]
    for column in ("title_ru", "title_uk"):
        if column not in badge_columns:
            db.execute("ALTER TABLE badge ADD COLUMN %s TEXT NOT NULL DEFAULT ''"
                       % column)
    # keys used to be handed to whoever asked first, so every key that exists
    # was made under a rule that no longer holds
    # codes are now one per asker rather than one per account: a code that
    # everybody was handed proved nothing about who was asking
    db.execute("DROP TABLE IF EXISTS proof")
    owner_columns = [row[1] for row in db.execute("PRAGMA table_info(owner)")]
    for column, kind in (("proved", "INTEGER NOT NULL DEFAULT 0"),
                         ("proved_at", "INTEGER NOT NULL DEFAULT 0"),
                         ("name", "TEXT NOT NULL DEFAULT ''")):
        if column not in owner_columns:
            db.execute("ALTER TABLE owner ADD COLUMN %s %s" % (column, kind))
    dep.prepare(db)
    db.commit()
    db.close()
    take_in_admin()


def take_in_admin():
    """Move the panel's password out of the file it used to live in.

    It was a json file beside the database: a name and a password in plain
    text, readable by anything that could read the disk. The name and the key
    the cookie is signed with come across as they are; the password is hashed
    on the way and the file is left with `.old` on the end, to be deleted by
    hand once signing in has been seen to work.
    """
    db = connect()
    already = db.execute("SELECT count(*) FROM admin").fetchone()[0]
    db.close()
    if already or not os.path.isfile(WHO):
        return
    try:
        with open(WHO, encoding="utf-8") as handle:
            said = json.load(handle)
        user = said.get("user", "")
        password = said.get("password", "")
        if not user or not password:
            return
        db = connect()
        db.execute("INSERT INTO admin (user, salt, hash, secret, changed)"
                   " VALUES (?,?,?,?,?)",
                   (user, "00", "", said.get("secret", "") or secrets.token_hex(32),
                    int(time.time())))
        db.commit()
        db.close()
        panel.set_password(user, password)
        os.rename(WHO, WHO + ".old")
        print("the panel password is in the database now")
    except Exception as trouble:
        print("could not take the panel password in: %s" % trouble)


# ------------------------------------------------------------ what is served

#: what a badge is drawn with when it says nothing: the mod's own note
PLAIN = "note.png"


def stamped(name):
    """A picture's name with a mark of what is in it.

    Phones keep a picture by the name it came under, and these names do not
    change when the drawing does -- so a new crown would never reach anybody
    who already had the old one. The mark makes a redrawn picture a different
    picture as far as any cache is concerned.
    """
    if not name:
        name = PLAIN
    path = os.path.join(ICONS, os.path.basename(name.split("?")[0]))
    try:
        with open(path, "rb") as handle:
            mark = hashlib.sha256(handle.read()).hexdigest()[:8]
        return "%s?v=%s" % (os.path.basename(path), mark)
    except OSError:
        return name


def supporter(db, uid):
    """Whether this account holds the supporter badge.

    A hidden badge counts: somebody who paid and keeps it off has still paid.
    """
    row = db.execute("SELECT 1 FROM held WHERE uid = ? AND badge = ?",
                     (uid, SUPPORTER)).fetchone()
    return row is not None


def vouched(db, body):
    """The account a request speaks for, if it really does.

    Returns the uid, or None. The token is the one given at `/prove/check`,
    and only to somebody who could write into that account's bio. A key from
    before that rule existed no longer counts: `proved` is nought on all of
    them.
    """
    uid, token = body.get("uid", ""), body.get("token", "")
    if not sane(uid) or not isinstance(token, str) or not token:
        return None
    row = db.execute("SELECT token, proved FROM owner WHERE uid = ?",
                     (uid,)).fetchone()
    if not row or not row[1]:
        return None
    if not secrets.compare_digest(row[0], token):
        return None
    return uid


def public():
    """Every badge and who wears it, in the shape the mod already reads."""
    db = connect()
    badges = {}
    for row in db.execute(
            "SELECT id, colour, image, title, text, text_ru, text_uk, button,"
            " title_ru, title_uk FROM badge"):
        badges[row[0]] = {
            "id": row[0], "colour": row[1], "image": stamped(row[2]), "title": row[3],
            "text": row[4], "text_ru": row[5], "text_uk": row[6], "button": row[7],
            "title_ru": row[8], "title_uk": row[9],
            "users": [],
        }
    # the order a person chose is the order their badges are listed in
    for uid, badge in db.execute(
            "SELECT uid, badge FROM held WHERE shown = 1 ORDER BY place, badge"):
        if badge in badges:
            badges[badge]["users"].append(uid)

    # Gradients belong to the account, not to a supporter badge.
    gradients = {uid: colours.split(",") for uid, colours
                 in db.execute("SELECT uid, colours FROM gradient")
                 if colours}
    db.close()

    out = [badges[key] for key in sorted(badges)]
    return {"badges": out, "gradients": gradients,
            "free_until": FREE_UNTIL, "now": int(time.time())}


def mine(uid):
    """What one account holds, shown or not, in the order it chose."""
    db = connect()
    rows = db.execute(
        "SELECT badge, shown, place FROM held WHERE uid = ? ORDER BY place, badge",
        (uid,)).fetchall()
    db.close()
    return [{"id": badge, "shown": bool(shown), "place": place}
            for badge, shown, place in rows]


# ------------------------------------------------------------- what is taken

def claim(uid, ip):
    """What an account holds, and whether it has proved it is anybody's.

    No key comes out of here any more. This used to hand one to whoever asked
    first, which meant whoever asked at all.
    """
    if not sane(uid):
        return 400, {"error": "that is not an account id"}
    db = connect()
    row = db.execute("SELECT proved FROM owner WHERE uid = ?", (uid,)).fetchone()
    proved = bool(row and row[0])
    if proved:
        db.execute(
            "INSERT OR IGNORE INTO badge (id, colour, image, title, text, text_ru,"
            " text_uk, button, claimable) VALUES (?, ?, '', ?, ?, ?, ?, '', 0)",
            (TOKKIYOU_BADGE, "5E2BCE", "TokkiYou", "TokkiYou member",
             "Участник TokkiYou", "Учасник TokkiYou"))
        place = db.execute(
            "SELECT COALESCE(MAX(place), -1) + 1 FROM held WHERE uid = ?",
            (uid,)).fetchone()[0]
        db.execute(
            "INSERT OR IGNORE INTO held (uid, badge, shown, place, given_at)"
            " VALUES (?, ?, 1, ?, ?)",
            (uid, TOKKIYOU_BADGE, place, int(time.time())))
        db.commit()
    db.close()
    return 200, {"uid": uid, "proved": proved, "badges": mine(uid)}


# ------------------------------------------------- proving an account is yours

_reads = []
_reads_lock = threading.Lock()


def a_read_to_spare():
    """Whether another profile page may be read this minute.

    Reading pages is the one thing here that TikTok can answer by refusing to
    answer at all, and the bot reads them too. So the whole server shares one
    allowance rather than each address having its own.
    """
    now = time.time()
    with _reads_lock:
        while _reads and now - _reads[0] > 60:
            _reads.pop(0)
        if len(_reads) >= PROOF_READS:
            return False
        _reads.append(now)
        return True


#: how many codes one account may have out at once, one per asker
PROOFS_EACH = 5


def prove(body, ip):
    """A code for this account to put in its bio, and a secret for the asker.

    One code per asker, not one per account. A code everybody was handed
    proved only that somebody, somewhere, had put it in the bio -- and the
    key went to whoever called the check first, which could be anyone
    watching that bio. Now the check asks for the code *that asker* was
    given, and only its holder can spend it.

    The same asker gets the same code back: a code that changes while it is
    sitting in somebody's bio is how people were stranded before.
    """
    uid = body.get("uid", "")
    if not sane(uid):
        return 400, {"error": "that is not an account id"}

    now = int(time.time())
    db = connect()
    # a phone that already has a secret for this account gets its own code
    # back; anybody else asking gets one of their own, which is not the code
    # sitting in that bio
    row = mine_proof(db, uid, body.get("holder", ""), ip)

    fresh = bool(body.get("fresh"))
    if row and not fresh:
        code, holder = row[1], row[0]
        db.execute("UPDATE proofs SET made = ?, tried = 0, last = 0, ip = ?"
                   " WHERE uid = ? AND holder = ?", (now, ip, uid, holder))
    elif row and fresh:
        # the one already in a bio keeps working for a while, so asking for
        # another does not strand whoever had pasted the first
        code, holder = "tokkiyou-" + str(secrets.randbelow(900000) + 100000), row[0]
        db.execute("UPDATE proofs SET code = ?, made = ?, tried = 0, last = 0,"
                   " was = ?, was_made = ?, ip = ?"
                   " WHERE uid = ? AND holder = ?",
                   (code, now, row[1], now, ip, uid, holder))
    else:
        code, holder = "tokkiyou-" + str(secrets.randbelow(900000) + 100000), secrets.token_hex(16)
        spare = db.execute(
            "SELECT holder FROM proofs WHERE uid = ? ORDER BY made DESC"
            " LIMIT -1 OFFSET ?", (uid, PROOFS_EACH - 1)).fetchall()
        for (old,) in spare:
            db.execute("DELETE FROM proofs WHERE uid = ? AND holder = ?", (uid, old))
        db.execute("INSERT INTO proofs (uid, holder, code, made, tried, last, ip)"
                   " VALUES (?,?,?,?,0,0,?)", (uid, holder, code, now, ip))
    db.commit()
    db.close()
    return 200, {"code": code, "holder": holder, "until": now + PROOF_LIVES}


def mine_proof(db, uid, holder, ip):
    """The row that belongs to this asker: (holder, code, was, was_made, tried, last).

    Only the secret answers. Matching on the address the request came from was
    the obvious kindness to older mods, and it is not safe here: half of these
    phones sit behind the same few VPN exits, so "the same address" is not the
    same person. A mod that sends no secret cannot prove anything any more,
    and has to be updated.
    """
    if not isinstance(holder, str) or not re.fullmatch(r"[0-9a-f]{32}", holder or ""):
        return None
    return db.execute(
        "SELECT holder, code, was, was_made, tried, last FROM proofs"
        " WHERE uid = ? AND holder = ?", (uid, holder)).fetchone()


def prove_check(body, ip):
    """Read the account's own page and see the code there.

    The page says which id it belongs to, so a name is not taken on trust
    either: an account's bio proves that account and nothing else.
    """
    uid = body.get("uid", "")
    if not sane(uid):
        return 400, {"error": "that is not an account id"}
    # a name may come with the request, but only as a fallback: the id is what
    # is being proved, and TikTok will say which name it belongs to
    name = (body.get("name") or "").strip().lstrip("@")

    now = int(time.time())
    db = connect()
    # only the asker's own code counts. Somebody else's request for the same
    # account carries a different code, which is not the one in that bio
    row = mine_proof(db, uid, body.get("holder", ""), ip)
    if not row:
        db.close()
        if not body.get("holder"):
            return 403, {"error": "this mod is too old to prove anything"}
        return 410, {"error": "ask for a code first"}
    holder, tried, last = row[0], row[4], row[5]

    codes = []
    made = db.execute("SELECT made FROM proofs WHERE uid = ? AND holder = ?",
                      (uid, holder)).fetchone()
    if made and now - made[0] <= PROOF_LIVES:
        codes.append(row[1])
    # the one before it, for as long as somebody could still be looking at it
    if row[2] and now - row[3] <= PROOF_LIVES + PROOF_SPARE:
        codes.append(row[2])
    if not codes:
        db.close()
        return 410, {"error": "ask for a code first"}
    if tried >= PROOF_TRIES:
        db.close()
        return 429, {"error": "too many tries, ask for a new code"}
    if now - last < 5:
        db.close()
        return 429, {"error": "too often", "wait": 5 - (now - last)}
    db.execute("UPDATE proofs SET tried = tried + 1, last = ?"
               " WHERE uid = ? AND holder = ?", (now, uid, holder))
    db.commit()
    db.close()

    if not a_read_to_spare():
        return 503, {"error": "too many at once, try again in a minute"}

    # the id is what is being proved, so the page is reached from the id
    # rather than from a name anybody typed. A name sent with the request is
    # only a fallback for the day the share link stops answering
    who = tiktok.behind(uid)
    if not who and tiktok.named(name):
        who = tiktok.profile(name)
    if not who:
        # some accounts have no page the share link can reach: it answers
        # with a sec_uid, and TikTok's web has nothing behind one. Those
        # people have to say what their name is, and the page it leads to is
        # still checked against the id
        if not tiktok.named(name):
            return 502, {"error": "tell me the name"}
        return 502, {"error": "tiktok did not answer"}
    if who.get("uid") != uid:
        return 403, {"error": "that name belongs to another account"}
    said = " ".join([who.get("about") or "", who.get("nickname") or ""]).lower()
    if not any(one in said for one in codes):
        return 400, {"error": "the code is not in that profile yet"}

    token = secrets.token_hex(16)
    db = connect()
    db.execute("INSERT INTO owner (uid, token, claimed, proved, proved_at, name)"
               " VALUES (?,?,?,1,?,?) ON CONFLICT(uid) DO UPDATE SET"
               " token = excluded.token, proved = 1, proved_at = excluded.proved_at,"
               " name = excluded.name",
               (uid, token, now, now, who.get("username") or name))
    # every outstanding code for this account goes: it is proved now
    db.execute("DELETE FROM proofs WHERE uid = ?", (uid,))
    db.commit()
    out = mine(uid)
    db.close()
    return 200, {"uid": uid, "token": token, "proved": True, "badges": out}


def too_soon(db, uid, ip):
    """Whether this account or this address has written recently."""
    now = int(time.time())
    row = db.execute("SELECT written FROM owner WHERE uid = ?", (uid,)).fetchone()
    if row and now - row[0] < EVERY:
        return EVERY - (now - row[0])
    row = db.execute("SELECT written FROM visitor WHERE ip = ?", (ip,)).fetchone()
    if row and now - row[0] < EVERY:
        return EVERY - (now - row[0])
    return 0


def wrote(db, uid, ip):
    now = int(time.time())
    db.execute("UPDATE owner SET written = ? WHERE uid = ?", (now, uid))
    db.execute("INSERT INTO visitor (ip, written) VALUES (?, ?) "
               "ON CONFLICT(ip) DO UPDATE SET written = ?", (ip, now, now))
    # an address matters only until its wait is over, and nothing was ever
    # taking these out again
    db.execute("DELETE FROM visitor WHERE written < ?", (now - EVERY * 10,))


def profile(body, ip):
    """Which of an account's badges to show, and in what order."""
    db = connect()
    uid = vouched(db, body)
    if not uid:
        db.close()
        return 403, {"error": "prove"}

    wait = too_soon(db, uid, ip)
    if wait:
        db.close()
        return 429, {"error": "too often", "wait": wait}

    order = body.get("order") or []
    told = body.get("hidden") or []
    if not words(order) or not words(told) or len(order) > MOST_BADGES:
        db.close()
        return 400, {"error": "that is not a list of badges"}
    hidden = set(told)

    held = {badge for (badge,) in db.execute(
        "SELECT badge FROM held WHERE uid = ?", (uid,))}
    if not order and not hidden:
        # a save with nothing in it is a screen that had not loaded yet, not a
        # decision to show everything. It used to be the latter, which turned
        # every badge back on for whoever tapped save too early
        db.close()
        return 200, {"badges": mine(uid)}

    # only what this account actually holds; a list naming anything else is
    # somebody trying to award themselves something
    place = 0
    for badge in order:
        if badge not in held:
            continue
        db.execute("UPDATE held SET place = ?, shown = ? WHERE uid = ? AND badge = ?",
                   (place, 0 if badge in hidden else 1, uid, badge))
        place += 1
    # a badge the list did not mention keeps whatever it was, unless it was
    # named as hidden
    for badge in held:
        if badge not in order and badge in hidden:
            db.execute("UPDATE held SET shown = 0 WHERE uid = ? AND badge = ?",
                       (uid, badge))
    wrote(db, uid, ip)
    db.commit()
    out = mine(uid)
    db.close()
    return 200, {"badges": out}


# ----------------------------------------------------- what a supporter gets

def hexed(colour):
    """One colour, as six hex digits, or nothing."""
    if not isinstance(colour, str):
        return ""
    value = colour.strip().lstrip("#").upper()
    if len(value) != 6 or any(c not in "0123456789ABCDEF" for c in value):
        return ""
    return value


def waited(db, table, uid, how_long):
    """How long is left before this may be changed again, or zero."""
    row = db.execute("SELECT changed FROM %s WHERE uid = ?" % table, (uid,)).fetchone()
    if not row:
        return 0
    left = how_long - (int(time.time()) - int(row[0] or 0))
    return left if left > 0 else 0


def gradient(body, ip):
    """The verified account's name colours, subject to a short change limit."""
    db = connect()
    uid = vouched(db, body)
    if not uid:
        db.close()
        return 403, {"error": "prove"}
    left = waited(db, "gradient", uid, GRADIENT_WAIT)
    if left:
        db.close()
        return 429, {"error": "too often", "wait": left}

    asked = body.get("colours")
    if asked is None:
        db.execute("DELETE FROM gradient WHERE uid = ?", (uid,))
        db.commit()
        db.close()
        return 200, {"colours": []}

    if not isinstance(asked, list) or len(asked) < 2 or len(asked) > MOST_COLOURS:
        db.close()
        return 400, {"error": "two to %d colours" % MOST_COLOURS}
    colours = [hexed(one) for one in asked]
    if not all(colours):
        db.close()
        return 400, {"error": "colours are six hex digits"}

    db.execute("INSERT INTO gradient (uid, colours, changed) VALUES (?,?,?)"
               " ON CONFLICT(uid) DO UPDATE SET colours = excluded.colours,"
               " changed = excluded.changed",
               (uid, ",".join(colours), int(time.time())))
    db.commit()
    db.close()
    return 200, {"colours": colours}


#: address -> (the hour it was last counted in, how many it sent in it)
CRASHED = {}


def crash(body, raw, ip):
    """A crash somebody agreed to tell us about.

    Kept as it came: the phone has already left out the account, the messages
    and the text of the error, and picking the report apart again here would
    only be a second place for that list to go out of date. What is pulled
    out is what the panel shows in a row -- which version, and what threw.
    """
    hour = int(time.time()) // 3600
    seen = CRASHED.get(ip)
    count = seen[1] + 1 if seen and seen[0] == hour else 1
    if len(CRASHED) > 5000:
        CRASHED.clear()
    CRASHED[ip] = (hour, count)
    if count > CRASH_EACH:
        return 429, {"error": "too often"}

    stack = body.get("stack")
    if not isinstance(stack, str) or not stack.strip():
        return 400, {"error": "no stack"}
    meta = body.get("meta") if isinstance(body.get("meta"), dict) else {}
    mod = meta.get("mod") if isinstance(meta.get("mod"), dict) else {}
    thrown = meta.get("crash") if isinstance(meta.get("crash"), dict) else {}
    version = str(mod.get("version") or body.get("version") or "")[:24]
    what = str(thrown.get("type") or stack.strip().split("\n", 1)[0])[:160]

    db = connect()
    db.execute("INSERT INTO crash (at, mod, what, body) VALUES (?,?,?,?)",
               (int(time.time()), version, what, raw))
    db.execute("DELETE FROM crash WHERE id NOT IN"
               " (SELECT id FROM crash ORDER BY id DESC LIMIT ?)", (CRASH_KEEP,))
    db.commit()
    db.close()
    return 200, {"ok": True}


def crashes(limit=60):
    """The latest reports, for the panel."""
    db = connect()
    rows = db.execute("SELECT id, at, mod, what, body FROM crash"
                      " ORDER BY id DESC LIMIT ?", (limit,)).fetchall()
    db.close()
    return [{"id": r[0], "at": r[1], "mod": r[2], "what": r[3], "body": r[4]}
            for r in rows]


#: one turn at a time, whoever it is for: two requests that both read a
#: balance before either wrote it would both be staking the same coins
DEP_TURN = threading.Lock()


def dep_play(body, ip):
    """A turn at the Dep Calculator, for an account that has proved itself."""
    db = connect()
    try:
        uid = vouched(db, body)
        if not uid:
            return 403, {"error": "prove"}
        with DEP_TURN:
            return dep.play(db, uid, body)
    finally:
        db.close()


def free(body, ip):
    """The badge anybody running the mod before the day gets, and nothing else.

    The badge is named here and not by the caller. A request cannot ask for
    `owner` because nothing it sends is used to choose which badge it gets.
    """
    now = int(time.time())
    if now >= FREE_UNTIL:
        return 410, {"error": "that one is over"}

    db = connect()
    uid = vouched(db, body)
    if not uid:
        db.close()
        return 403, {"error": "prove"}

    wait = too_soon(db, uid, ip)
    if wait:
        db.close()
        return 429, {"error": "too often", "wait": wait}

    db.execute("INSERT OR IGNORE INTO held (uid, badge, given_at) VALUES (?, ?, ?)",
               (uid, FREE, now))
    wrote(db, uid, ip)
    db.commit()
    out = mine(uid)
    db.close()
    return 200, {"badges": out}


def sane(uid):
    return isinstance(uid, str) and uid.isdigit() and 6 <= len(uid) <= 24


#: what an id may be made of, for a badge and for a plugin alike. It ends up
#: in a file name and in a Location header, and neither wants a slash or a
#: line break in it
NAMED = re.compile(r"^[A-Za-z0-9._-]{1,64}$")


def named(which):
    return isinstance(which, str) and bool(NAMED.match(which)) and ".." not in which


def words(said):
    """A list of short strings, which is what a list of badge ids is.

    Anything else came from something other than the mod, and the shape of
    what it sent should not be the thing that decides whether this answers or
    drops the connection.
    """
    return (isinstance(said, list) and len(said) <= MOST_BADGES
            and all(isinstance(one, str) and 0 < len(one) <= 64 for one in said))


# --------------------------------------------------------------- the plugins

#: file name -> (when it was last changed, its sha256). A plugin is megabytes
#: and the list is asked for by every phone that opens the store.
DIGESTS = {}


def digest_of(name):
    """The sha256 of a stored plugin, worked out once per version of the file."""
    path = os.path.join(PLUGINS, os.path.basename(name))
    try:
        changed = os.path.getmtime(path)
    except OSError:
        return ""
    known = DIGESTS.get(name)
    if known and known[0] == changed:
        return known[1]
    with open(path, "rb") as handle:
        value = hashlib.sha256(handle.read()).hexdigest()
    if len(DIGESTS) > 500:
        DIGESTS.clear()
    DIGESTS[name] = (changed, value)
    return value


def plugins():
    """Everything the store offers, newest first.

    Each entry carries the hash of its file: the phone checks what it was
    handed against it before it lets the plugin anywhere near the app.
    """
    db = connect()
    rows = db.execute(
        "SELECT id, name, version, author, about, tiktok, file, icon, size"
        " FROM plugin ORDER BY added DESC").fetchall()
    db.close()
    return {"plugins": [
        {"id": r[0], "name": r[1], "version": r[2], "author": r[3], "about": r[4],
         "tiktok": r[5], "url": "/plugin/" + r[6], "icon": ("/picture/" + r[7])
         if r[7] else "", "size": r[8], "sha256": digest_of(r[6])}
        for r in rows]}


def take_plugin(raw, name):
    """Read a packed plugin, keep it, and remember what its manifest says.

    The store shows what the plugin says about itself, so the manifest is read
    here rather than typed into a form: a name and an author that disagree with
    what the phone will load are worse than none.
    """
    import io
    import zipfile

    with zipfile.ZipFile(io.BytesIO(raw)) as pack:
        try:
            said = json.loads(pack.read("manifest.json").decode("utf-8"))
        except KeyError:
            raise ValueError("no manifest.json in that file")
        icon = b""
        for at in ("icon.png", "icon.webp", "icon.jpg"):
            try:
                icon = pack.read(at)
                break
            except KeyError:
                continue

    which = str(said.get("id") or "").strip()
    if not named(which):
        raise ValueError("the manifest has no usable id")

    os.makedirs(PLUGINS, exist_ok=True)
    file = which + ".mtp"
    with open(os.path.join(PLUGINS, file), "wb") as handle:
        handle.write(raw)

    picture = ""
    if icon:
        picture = which + ".png"
        with open(os.path.join(ICONS, picture), "wb") as handle:
            handle.write(icon)

    db = connect()
    db.execute(
        "INSERT INTO plugin (id, name, version, author, about, tiktok, file, icon,"
        " size, added) VALUES (?,?,?,?,?,?,?,?,?,?)"
        " ON CONFLICT(id) DO UPDATE SET name=excluded.name, version=excluded.version,"
        " author=excluded.author, about=excluded.about, tiktok=excluded.tiktok,"
        " file=excluded.file, icon=excluded.icon, size=excluded.size,"
        " added=excluded.added",
        (which, str(said.get("name") or which), str(said.get("version") or "?"),
         str(said.get("author") or "?"), str(said.get("description") or ""),
         str(said.get("tiktok") or ""), file, picture, len(raw), int(time.time())))
    db.commit()
    db.close()
    return which


def drop_plugin(which):
    db = connect()
    row = db.execute("SELECT file, icon FROM plugin WHERE id = ?", (which,)).fetchone()
    if row:
        for folder, name in ((PLUGINS, row[0]), (ICONS, row[1])):
            if name:
                try:
                    os.remove(os.path.join(folder, name))
                except OSError:
                    pass
    db.execute("DELETE FROM plugin WHERE id = ?", (which,))
    db.commit()
    db.close()


# ------------------------------------------------------------- the plumbing

# --------------------------------------------------------------- avatars

#: avatars fetched lately -- mark -> (when, kind, bytes). Several requests
#: land at once on a busy minute, so the map is only touched under its lock
FACES = {}
FACES_LOCK = threading.Lock()
FACE_KEEPS = 15 * 60

#: as many avatars as are worth holding in memory at once
FACE_MANY = 200

#: where an avatar may come from, and nowhere else
FACE_HOSTS = (".tiktokcdn.com", ".tiktokcdn-eu.com", ".tiktokcdn-us.com",
              ".tiktokcdn-in.com", ".byteimg.com", ".ibyteimg.com")

BROWSER = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
           " (KHTML, like Gecko) Chrome/124.0 Safari/537.36")


def face_named(url):
    return hashlib.sha256(url.encode("utf-8")).hexdigest()[:16]


def face_told(url):
    """What telegram calls this avatar, if it has been told about it.

    Telegram will not fetch a picture from this server -- it refuses plain
    http on a bare address, and there is no domain to put a certificate on
    -- so the bot hands it the bytes once and keeps the name it gets back.
    That name can be used in an answer for as long as it lasts, with no
    fetching by anybody.
    """
    db = connect()
    row = db.execute("SELECT told FROM face WHERE id = ?",
                     (face_named(url),)).fetchone()
    db.close()
    return row[0] if row and row[0] else ""


def face_tell(url, told):
    now = int(time.time())
    db = connect()
    db.execute("INSERT INTO face (id, url, seen, told) VALUES (?,?,?,?)"
               " ON CONFLICT(id) DO UPDATE SET seen = excluded.seen,"
               " told = excluded.told", (face_named(url), url, now, told))
    db.execute("DELETE FROM face WHERE seen < ?", (now - 7 * 86400,))
    db.commit()
    db.close()


def face_link(url):
    """Where somebody else can fetch a tiktok avatar.

    Telegram will not fetch one from tiktok -- the CDN does not serve
    whoever telegram is -- so it is fetched here and passed on.

    The address is kept here and the link carries only a short name for it.
    Putting the address in the link does not work for a fetcher that tidies
    up what it is about to fetch: an address with an escaped address inside
    it arrives cut at the first question mark.

    Telegram is not the reader of this -- it will not fetch from plain http
    on a bare address at all, which is what `face_told` is for. This is for
    anything that will, and for when there is a domain here.

    Only the hosts avatars come from, and only an address that was handed
    to this service on purpose: there is no way to make it fetch whatever
    anybody names.
    """
    if not (urlparse(url).hostname or "").lower().endswith(FACE_HOSTS):
        return ""
    which = face_named(url)
    now = int(time.time())
    db = connect()
    db.execute("INSERT INTO face (id, url, seen) VALUES (?,?,?)"
               " ON CONFLICT(id) DO UPDATE SET seen = excluded.seen",
               (which, url, now))
    db.execute("DELETE FROM face WHERE seen < ?", (now - 7 * 86400,))
    db.commit()
    db.close()
    return "/face/%s.jpg" % which


def face(which):
    now = time.time()
    with FACES_LOCK:
        for stale in [key for key, (when, _k, _b) in list(FACES.items())
                      if now - when > FACE_KEEPS]:
            FACES.pop(stale, None)
        kept = FACES.get(which)
    if kept:
        return kept[1], kept[2]

    db = connect()
    row = db.execute("SELECT url FROM face WHERE id = ?", (which,)).fetchone()
    db.close()
    if not row:
        return None, None
    url = row[0]
    if not (urlparse(url).hostname or "").lower().endswith(FACE_HOSTS):
        return None, None

    try:
        request = urllib.request.Request(url, headers={
            "User-Agent": BROWSER, "Referer": "https://www.tiktok.com/",
            "Accept": "image/avif,image/webp,image/*,*/*;q=0.8"})
        with urllib.request.urlopen(request, timeout=20) as answer:
            kind = (answer.headers.get("Content-Type") or "").split(";")[0]
            if not kind.startswith("image/"):
                return None, None
            blob = answer.read(4 * 1024 * 1024)
    except Exception:
        return None, None
    if not blob:
        return None, None
    with FACES_LOCK:
        FACES[which] = (now, kind, blob)
        while len(FACES) > FACE_MANY:
            FACES.pop(next(iter(FACES)), None)
    return kind, blob


class Handler(BaseHTTPRequestHandler):
    server_version = "margyt"
    sys_version = ""

    #: a connection that says nothing for this long is dropped, so a socket
    #: held open on purpose costs one thread rather than a thread for good
    timeout = 30

    #: a HEAD is answered with the headers and nothing else
    headless = False

    def log_message(self, *args):
        pass   # nginx keeps the log; this would only duplicate it

    def push(self, data):
        if not self.headless:
            self.wfile.write(data)

    def do_HEAD(self):
        """The same answer as a GET, minus the body.

        Telegram asks HEAD before it fetches a picture somebody named, and
        an unanswered HEAD is a 501 with an html body -- which it reads as
        the address not being a picture at all, and refuses it.
        """
        self.headless = True
        try:
            self.do_GET()
        finally:
            self.headless = False

    def who(self):
        # nginx is in front, so the address that matters is the one it passes
        return self.headers.get("X-Real-IP") or self.client_address[0]

    def answer(self, code, body, kind="application/json; charset=utf-8", raw=None):
        data = raw if raw is not None else json.dumps(
            body, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(data)))
        if code == 200 and raw is None:
            tag = '"%s"' % hashlib.sha256(data).hexdigest()[:32]
            self.send_header("ETag", tag)
            self.send_header("Cache-Control", "public, max-age=30")
            if self.headers.get("If-None-Match") == tag:
                self.send_response(304)
                self.send_header("ETag", tag)
                self.end_headers()
                return
        self.end_headers()
        self.push(data)

    # ------------------------------------------------------------- the panel

    def signed_in(self):
        return panel.allowed(self.headers.get("Cookie", ""))

    def html(self, body, code=200, cookie=None):
        self.send_response(code)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        if cookie is not None:
            self.send_header("Set-Cookie", cookie)
        self.end_headers()
        self.push(body)

    def go(self, where, cookie=None):
        self.send_response(303)
        # a header ends at a line break, so nothing that reaches one may hold
        # one, whoever it came from
        self.send_header("Location", where.replace("\r", " ").replace("\n", " "))
        # one cookie or several: signing in sets two
        for one in ([cookie] if isinstance(cookie, str) else cookie or []):
            self.send_header("Set-Cookie", one)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def form(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length > 32 * 1024 * 1024:
            return {}
        raw = self.rfile.read(length)
        return {k: v[0] for k, v in parse_qs(raw.decode("utf-8", "replace")).items()}

    def panel_get(self, path, query):
        if not self.signed_in():
            self.html(panel.sign_in_page())
            return

        if path == "/admin":
            db = connect()
            badges = [
                {"id": r[0], "colour": r[1], "image": r[2].split("?")[0],
                 "text": r[4] or r[3], "worn": r[5]}
                for r in db.execute(
                    "SELECT b.id, b.colour, b.image, b.title, b.text, COUNT(h.uid)"
                    " FROM badge b LEFT JOIN held h ON h.badge = b.id"
                    " GROUP BY b.id ORDER BY b.id")]
            colours = [{"uid": r[0], "colours": r[1].split(","), "changed": r[2]}
                       for r in db.execute(
                           "SELECT uid, colours, changed FROM gradient"
                           " ORDER BY changed DESC")]
            db.close()
            self.html(panel.main_page(badges, plugins()["plugins"], colours,
                                      query.get("said", [""])[0], crashes()))
            return

        if path == "/admin/badge":
            which = query.get("id", [""])[0]
            db = connect()
            row = db.execute(
                "SELECT id, colour, image, text FROM badge WHERE id = ?",
                (which,)).fetchone()
            if not row:
                db.close()
                self.go("/admin?said=нет такого значка")
                return
            wearers = db.execute(
                "SELECT h.uid, h.shown, COALESCE(o.proved, 0) FROM held h"
                " LEFT JOIN owner o ON o.uid = h.uid"
                " WHERE h.badge = ? ORDER BY h.given_at DESC",
                (which,)).fetchall()
            db.close()
            self.html(panel.badge_page(
                {"id": row[0], "colour": row[1], "image": row[2].split("?")[0],
                 "text": row[3]}, wearers))
            return

        if path == "/admin/out":
            self.go("/admin", panel.cookie(""))
            return

        self.answer(404, {"error": "no such page"})

    def panel_post(self, path):
        if not guard.same_site(self.headers):
            self.answer(403, {"error": "not from here"})
            return

        if path == "/admin/in":
            said = self.form()
            ip = self.who()
            seen = guard.device(self.headers.get("Cookie", ""))
            # asked before the password is so much as looked at: somebody
            # shut out learns nothing from trying, not even that it was wrong
            left = guard.wait(ip, seen)
            if left:
                self.html(panel.sign_in_page(
                    "слишком много неверных попыток, вход закрыт ещё на %s"
                    % panel.span(left)), 429)
                return
            if panel.admit(said.get("user", ""), said.get("password", "")):
                cookies = [panel.cookie(panel.ticket())]
                fresh = guard.passed(ip, seen)
                if fresh:
                    cookies.append(panel.seen_cookie(fresh))
                self.go("/admin", cookies)
            else:
                guard.failed(ip, seen)
                self.html(panel.sign_in_page("не подошло"))
            return

        if not self.signed_in():
            self.html(panel.sign_in_page())
            return

        if path == "/admin/password":
            said = self.form()
            now = said.get("now", "")
            fresh = said.get("fresh", "")
            if not panel.admit(panel.who(), now):
                self.go("/admin?said=старый пароль не подошёл")
                return
            if len(fresh) < 10:
                self.go("/admin?said=новый пароль короче десяти знаков")
                return
            panel.set_password(panel.who(), fresh)
            # the key the cookies are signed with changed with it, so every
            # browser that was in is out; this one is let straight back
            guard.forget_everyone()
            cookies = [panel.cookie(panel.ticket())]
            again = guard.passed(self.who(), "")
            if again:
                cookies.append(panel.seen_cookie(again))
            self.go("/admin?said=пароль сменён, остальные браузеры выкинуты", cookies)
            return

        if path == "/admin/badge/new":
            # a form with a file in it is read whole rather than as a query
            # string, so the picture arrives the same way a plugin does
            try:
                form = cgi.FieldStorage(
                    fp=self.rfile, headers=self.headers,
                    environ={"REQUEST_METHOD": "POST",
                             "CONTENT_TYPE": self.headers.get("Content-Type", "")})
            except Exception as trouble:
                self.go("/admin?said=не вышло: %s" % trouble)
                return

            def field(name):
                try:
                    return (form.getfirst(name) or "").strip()
                except Exception:
                    return ""

            which = field("id")
            if not named(which):
                self.go("/admin?said=id: буквы, цифры, точка, дефис")
                return

            picture = ""
            try:
                item = form["picture"]
                raw = item.file.read() if item.filename else b""
            except Exception:
                raw = b""
            if raw:
                picture = which + ".png"
                with open(os.path.join(ICONS, picture), "wb") as handle:
                    handle.write(raw)

            # the name heads the popup, the text is the sentence under it.
            # Every badge was called "TokkiYou" because of this line
            db = connect()
            db.execute(
                "INSERT INTO badge (id, colour, image, title, title_ru, title_uk,"
                " text, text_ru, text_uk, button) VALUES (?,?,?,?,?,?,?,?,?,?)"
                " ON CONFLICT(id) DO UPDATE SET colour=excluded.colour,"
                " image=excluded.image, title=excluded.title,"
                " title_ru=excluded.title_ru, title_uk=excluded.title_uk,"
                " text=excluded.text, text_ru=excluded.text_ru,"
                " text_uk=excluded.text_uk",
                (which, field("colour").lstrip("#"), picture,
                 field("title") or which, field("title_ru"), field("title_uk"),
                 field("text"), field("text_ru"), field("text_uk"), "Got it"))
            db.commit()
            db.close()
            self.go("/admin?said=значок %s готов" % which)
            return

        if path == "/admin/badge/give":
            said = self.form()
            which = said.get("id", "")
            given = 0
            db = connect()
            if db.execute("SELECT 1 FROM badge WHERE id = ?", (which,)).fetchone():
                now = int(time.time())
                for uid in (said.get("uids") or "").replace(",", " ").split():
                    if sane(uid):
                        db.execute("INSERT OR IGNORE INTO held (uid, badge, given_at)"
                                   " VALUES (?, ?, ?)", (uid, which, now))
                        given += 1
                db.commit()
            db.close()
            self.go("/admin/badge?id=%s" % which)
            return

        if path == "/admin/badge/take":
            said = self.form()
            db = connect()
            db.execute("DELETE FROM held WHERE uid = ? AND badge = ?",
                       (said.get("uid", ""), said.get("id", "")))
            db.commit()
            db.close()
            self.go("/admin/badge?id=%s" % said.get("id", ""))
            return

        if path == "/admin/gradient/drop":
            said = self.form()
            uid = said.get("uid", "")
            if sane(uid):
                db = connect()
                db.execute("DELETE FROM gradient WHERE uid = ?", (uid,))
                db.commit()
                db.close()
            self.go("/admin?said=градиент снят")
            return

        if path == "/admin/plugin/drop":
            said = self.form()
            drop_plugin(said.get("id", ""))
            self.go("/admin?said=плагин убран")
            return

        if path == "/admin/plugin/add":
            try:
                form = cgi.FieldStorage(
                    fp=self.rfile, headers=self.headers,
                    environ={"REQUEST_METHOD": "POST",
                             "CONTENT_TYPE": self.headers.get("Content-Type", "")})
                item = form["file"]
                which = take_plugin(item.file.read(), item.filename or "plugin.mtp")
                self.go("/admin?said=плагин %s в магазине" % which)
            except Exception as trouble:
                self.go("/admin?said=не вышло: %s" % trouble)
            return

        self.answer(404, {"error": "no such page"})

    def do_GET(self):
        parts = urlparse(self.path)
        path = parts.path
        if path.startswith("/admin"):
            self.panel_get(path, parse_qs(parts.query))
            return
        if path.startswith("/plugin/"):
            name = os.path.basename(path[len("/plugin/"):])
            file = os.path.join(PLUGINS, name)
            if not name.endswith(".mtp") or not os.path.isfile(file):
                self.answer(404, {"error": "no such plugin"})
                return
            with open(file, "rb") as handle:
                self.answer(200, None, "application/octet-stream", handle.read())
            return
        if path == "/plugins":
            self.answer(200, plugins())
            return
        if path.startswith("/picture/"):
            name = os.path.basename(path[len("/picture/"):])
            file = os.path.join(ICONS, name)
            if not os.path.isfile(file):
                self.answer(404, {"error": "no such picture"})
                return
            with open(file, "rb") as handle:
                self.answer(200, None, "image/png", handle.read())
            return
        if path == "/badges":
            data = json.dumps(public(), ensure_ascii=False).encode("utf-8")
            tag = '"%s"' % hashlib.sha256(data).hexdigest()[:32]
            if self.headers.get("If-None-Match") == tag:
                self.send_response(304)
                self.send_header("ETag", tag)
                self.end_headers()
                return
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("ETag", tag)
            self.send_header("Cache-Control", "public, max-age=30")
            self.end_headers()
            self.push(data)
            return

        if path.startswith("/icon/"):
            name = os.path.basename(path[len("/icon/"):])
            if not name.endswith(".png") or "/" in name:
                self.answer(404, {"error": "no"})
                return
            file = os.path.join(ICONS, name)
            if not os.path.isfile(file):
                self.answer(404, {"error": "no such picture"})
                return
            with open(file, "rb") as handle:
                self.answer(200, None, "image/png", handle.read())
            return

        if path.startswith("/face/"):
            which = os.path.basename(path[len("/face/"):])
            if which.endswith(".jpg"):
                which = which[:-len(".jpg")]
            kind, blob = (None, None)
            if len(which) == 16 and all(c in "0123456789abcdef" for c in which):
                kind, blob = face(which)
            if not blob:
                self.answer(404, {"error": "no such face"})
                return
            self.send_response(200)
            self.send_header("Content-Type", kind)
            self.send_header("Content-Length", str(len(blob)))
            self.send_header("Cache-Control", "public, max-age=900")
            self.end_headers()
            self.push(blob)
            return

        if path == "/health":
            self.answer(200, {"ok": True})
            return

        # the patcher app: who has signed in, and what is early
        if path == "/patcher/login/check":
            code, out = patcher.check((parse_qs(parts.query).get("code") or [""])[0])
            self.answer(code, out)
            return
        if path == "/patcher/me":
            code, out = patcher.me(self.headers.get("Authorization"))
            self.answer(code, out)
            return
        if path == "/patcher/beta":
            code, out = patcher.beta(self.headers.get("Authorization"))
            self.answer(code, out)
            return

        self.answer(404, {"error": "no such thing"})

    def do_POST(self):
        path = urlparse(self.path).path
        if path.startswith("/admin"):
            self.panel_post(path)
            return
        try:
            length = int(self.headers.get("Content-Length") or 0)
            if length < 0:
                self.answer(400, {"error": "unreadable"})
                return
            if length > (CRASH_MOST if path == "/crash" else 8192):
                self.answer(413, {"error": "that is a lot of json"})
                return
            raw = self.rfile.read(length) or b"{}"
            body = json.loads(raw)
            if not isinstance(body, dict):
                raise ValueError
        except Exception:
            self.answer(400, {"error": "unreadable"})
            return

        ip = self.who()
        if path == "/claim":
            code, out = claim(body.get("uid", ""), ip)
        elif path == "/prove":
            code, out = prove(body, ip)
        elif path == "/prove/check":
            code, out = prove_check(body, ip)
        elif path == "/profile":
            code, out = profile(body, ip)
        elif path == "/old":
            code, out = free(body, ip)
        elif path == "/dep":
            code, out = dep_play(body, ip)
        elif path == "/gradient":
            code, out = gradient(body, ip)
        elif path == "/crash":
            code, out = crash(body, raw.decode("utf-8", "replace"), ip)
        elif path == "/patcher/login/start":
            code, out = patcher.start(ip)
        elif path == "/patches":
            # 1.x asked here for fixes that travel without an apk. There are
            # none any more, and "nothing new" keeps those phones quiet
            code, out = 200, {}
        else:
            code, out = 404, {"error": "no such thing"}
        self.answer(code, out)


def main():
    prepare()
    server = ThreadingHTTPServer(("127.0.0.1", 8080), Handler)
    server.serve_forever()


if __name__ == "__main__":
    main()
