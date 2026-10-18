#!/usr/bin/env python3
"""The panel: badges and plugins, from a browser.

Small on purpose. There is one person using it, from one machine, and what it
does -- hand out a badge, put a plugin in the store -- is what `badgectl` does
from a shell. It exists because typing account ids into a shell over ssh is a
poor way to spend an evening.

The pages are written out here rather than served from files: there are four
of them, they are all form and table, and a template engine would be more
machinery than the thing it renders.

Signing in is a name and a password, and a signed cookie afterwards. The
password is in the database as a salted hash rather than beside it in a file
anybody with the disk can read: the box that keeps it also serves the panel,
so a file of plain text there is the same as no password at all.

Not a session store, because there is one session: the cookie carries when it
was issued and a signature over that, so the server can check it without
remembering anything.
"""

import base64
import hashlib
import hmac
import html
import os
import secrets
import sqlite3
import time

HERE = os.path.dirname(os.path.abspath(__file__))
DB = os.path.join(HERE, "badges.db")

HOURS = 12 * 3600

#: how long the hash takes to work out, which is how long a guess takes too
ROUNDS = 240000


def store():
    return sqlite3.connect(DB, timeout=10)


def kept():
    """The one account allowed in: name, salt, hash and the cookie key."""
    try:
        db = store()
        row = db.execute("SELECT user, salt, hash, secret FROM admin"
                         " ORDER BY user LIMIT 1").fetchone()
        db.close()
        return row
    except Exception:
        return None


def hashed(password, salt):
    return hashlib.pbkdf2_hmac(
        "sha256", password.encode("utf-8"), bytes.fromhex(salt), ROUNDS).hex()


def admit(user, password):
    """Whether this name and password are the ones."""
    row = kept()
    if not row or not user or not password:
        return False
    # the password is worked out whether or not the name is right, so how
    # long the answer takes says nothing about which of the two was wrong
    right = hmac.compare_digest(hashed(password, row[1]), row[2])
    return hmac.compare_digest(user.encode("utf-8"), row[0].encode("utf-8")) and right


def set_password(user, password):
    """Write a new password down as a salt and a hash, never as itself."""
    salt = secrets.token_hex(16)
    db = store()
    # a new key for the cookies as well: a browser that was signed in with
    # the old password is not still signed in with the new one
    secret = secrets.token_hex(32)
    db.execute("INSERT INTO admin (user, salt, hash, secret, changed)"
               " VALUES (?,?,?,?,?) ON CONFLICT(user) DO UPDATE SET"
               " salt = excluded.salt, hash = excluded.hash,"
               " secret = excluded.secret, changed = excluded.changed",
               (user, salt, hashed(password, salt), secret, int(time.time())))
    db.commit()
    db.close()


def secret():
    row = kept()
    return row[3] if row else ""


def who():
    """The name that may sign in, for a page that wants to show it."""
    row = kept()
    return row[0] if row else ""


def sign(when, secret):
    return hmac.new(secret.encode(), str(when).encode(), hashlib.sha256).hexdigest()[:32]


def ticket():
    when = int(time.time())
    return "%d.%s" % (when, sign(when, secret()))


def cookie(value):
    """The sign-in cookie, or the one that takes it away.

    Never sent over plain http, never readable by a script on the page, and
    never sent along with a request that began on somebody else's site.
    """
    return ("tokkiyou=%s; Path=/; Max-Age=%d; HttpOnly; Secure; SameSite=Strict"
            % (value, HOURS if value else 0))


def seen_cookie(mark):
    """What a browser keeps to be recognised by the next time it signs in."""
    return ("tokkiyou_seen=%s; Path=/admin; Max-Age=%d; HttpOnly; Secure; SameSite=Strict"
            % (mark, 180 * 86400))


def span(seconds):
    if seconds >= 3600:
        return "%d ч %d мин" % (seconds // 3600, seconds % 3600 // 60)
    return "%d мин" % max(1, (seconds + 59) // 60)


def allowed(cookie):
    key = secret()
    if not key or not cookie:
        return False
    for piece in cookie.split(";"):
        piece = piece.strip()
        if not piece.startswith("tokkiyou="):
            continue
        value = piece[len("tokkiyou="):]
        try:
            when, mark = value.split(".", 1)
            if int(time.time()) - int(when) > HOURS:
                return False
            return hmac.compare_digest(mark, sign(int(when), key))
        except Exception:
            return False
    return False


# ------------------------------------------------------------------ the look

STYLE = """
:root { color-scheme: dark; --bg:#121212; --card:#1c1c1e; --line:#2c2c2e;
        --text:#f2f2f2; --dim:#8e8e93; --accent:#8dd1b0; }
* { box-sizing: border-box; }
body { margin:0; padding:32px 16px; background:var(--bg); color:var(--text);
       font:15px/1.5 -apple-system,Segoe UI,Roboto,sans-serif; }
main { max-width: 860px; margin: 0 auto; }
h1 { font-size: 26px; margin: 0 0 4px; }
h2 { font-size: 17px; margin: 28px 0 10px; color: var(--dim); font-weight: 600; }
.card { background:var(--card); border-radius:16px; padding:18px; margin-bottom:14px; }
table { width:100%; border-collapse: collapse; }
td, th { text-align:left; padding:9px 8px; border-bottom:1px solid var(--line); }
th { color: var(--dim); font-weight:600; font-size:13px; }
tr:last-child td { border-bottom: none; }
input, textarea, select { width:100%; padding:10px 12px; border-radius:10px;
       border:1px solid var(--line); background:#151516; color:var(--text);
       font: inherit; }
label { display:block; font-size:13px; color:var(--dim); margin:10px 0 4px; }
button, .button { display:inline-block; padding:10px 16px; border-radius:11px;
       border:none; background:var(--accent); color:#10221f; font:inherit;
       font-weight:600; cursor:pointer; text-decoration:none; }
.quiet { background:transparent; color:var(--text); border:1px solid var(--line); }
.row { display:flex; gap:10px; align-items:flex-end; flex-wrap: wrap; }
.row > * { flex:1; min-width: 140px; }
.row > .narrow { flex:0 0 auto; }
.dim { color: var(--dim); font-size: 13px; }
pre { white-space: pre-wrap; word-break: break-word; font-size: 12px; margin: 8px 0 0; }
.bar { display:inline-block; width:96px; height:14px; border-radius:7px;
       vertical-align:middle }
.dot { width:26px; height:26px; border-radius:7px; vertical-align:middle;
       margin-right:8px; object-fit: contain; }
.said { background:#1f2d27; border:1px solid #2f4a3f; padding:10px 14px;
        border-radius:11px; margin-bottom:14px; }
"""


def page(title, body, said=""):
    return ("<!doctype html><meta charset=utf-8>"
            "<meta name=viewport content='width=device-width,initial-scale=1'>"
            "<title>%s</title><style>%s</style><main>%s%s</main>"
            % (html.escape(title), STYLE,
               ("<div class=said>%s</div>" % html.escape(said)) if said else "",
               body)).encode("utf-8")


def sign_in_page(trouble=""):
    return page("TokkiYou", """
        <h1>TokkiYou</h1>
        <p class=dim>Панель</p>
        <form class=card method=post action="/admin/in">
          <label>Имя</label><input name=user autocomplete=username>
          <label>Пароль</label>
          <input name=password type=password autocomplete=current-password>
          <p><button>Войти</button></p>
        </form>""", trouble)


def when(stamp):
    """A time, as how long ago."""
    try:
        gone = int(time.time()) - int(stamp or 0)
    except Exception:
        return "-"
    if gone < 90:
        return "только что"
    if gone < 3600:
        return "%d мин назад" % (gone // 60)
    if gone < 86400:
        return "%d ч назад" % (gone // 3600)
    return "%d дн назад" % (gone // 86400)


def main_page(badges, plugins, gradients=(), said="", crashes=()):
    rows = []
    for badge in badges:
        rows.append(
            "<tr><td><img class=dot src='/icon/%s'></td><td><b>%s</b></td>"
            "<td class=dim>#%s</td><td class=dim>%s</td><td class=dim>%d</td>"
            "<td><a class='button quiet' href='/admin/badge?id=%s'>Открыть</a></td></tr>"
            % (html.escape(badge["image"] or "note.png"), html.escape(badge["id"]),
               html.escape(badge["colour"] or "-"), html.escape(badge["text"] or ""),
               badge["worn"], html.escape(badge["id"])))

    packs = []
    for one in plugins:
        packs.append(
            "<tr><td><img class=dot src='%s'></td><td><b>%s</b> <span class=dim>%s</span></td>"
            "<td class=dim>%s</td><td class=dim>%s</td>"
            "<td><form method=post action='/admin/plugin/drop' "
            "style='display:inline'><input type=hidden name=id value='%s'>"
            "<button class=quiet>Убрать</button></form></td></tr>"
            % (html.escape(one["icon"] or "/icon/note.png"), html.escape(one["name"]),
               html.escape(one["version"]), html.escape(one["author"]),
               html.escape(one["tiktok"] or "любая"), html.escape(one["id"])))

    painted = []
    for one in gradients:
        strip = ", ".join("#" + html.escape(c) for c in one["colours"])
        painted.append(
            "<tr><td class=dim>%s</td>"
            "<td><span class=bar style=\"background:linear-gradient(90deg,%s)\">"
            "</span> <span class=dim>%s</span></td>"
            "<td><form method=post action='/admin/gradient/drop' style='display:inline'>"
            "<input type=hidden name=uid value='%s'>"
            "<button class=quiet>Снять</button></form></td></tr>"
            % (html.escape(one["uid"]), strip, strip, html.escape(one["uid"])))

    fallen = []
    for one in crashes:
        fallen.append(
            "<tr><td class=dim>%s</td><td class=dim>%s</td>"
            "<td><details><summary>%s</summary><pre>%s</pre></details></td></tr>"
            % (when(one["at"]), html.escape(one["mod"] or "?"),
               html.escape(one["what"] or "?"), html.escape(one["body"])))

    return page("TokkiYou", """
        <h1>TokkiYou</h1>
        <p class=dim>Значки, плагины и градиенты</p>

        <h2>Значки</h2>
        <div class=card><table>
          <tr><th></th><th>id</th><th>цвет</th><th>текст</th><th>у кого</th><th></th></tr>
          %s
        </table></div>

        <h2>Новый значок</h2>
        <form class=card method=post action="/admin/badge/new"
              enctype="multipart/form-data">
          <div class=row>
            <div><label>id</label><input name=id placeholder="supporter"></div>
            <div><label>цвет</label><input name=colour placeholder="FF6FA5"></div>
          </div>
          <label>картинка — png, прозрачный фон; пусто оставит ноту</label>
          <input type=file name=picture accept="image/png,image/webp">
          <div class=row>
            <div><label>название</label><input name=title placeholder="Поддержал"></div>
            <div><label>название (ru)</label><input name=title_ru></div>
            <div><label>название (uk)</label><input name=title_uk></div>
          </div>
          <label>текст</label><input name=text placeholder="поддержал разработку TokkiYou">
          <div class=row>
            <div><label>текст (ru)</label><input name=text_ru></div>
            <div><label>текст (uk)</label><input name=text_uk></div>
          </div>
          <p><button>Создать</button></p>
        </form>

        <h2>Градиенты ников</h2>
        <div class=card><table>
          <tr><th>айди</th><th>цвета</th><th></th></tr>
          %s
        </table></div>

        <h2>Плагины</h2>
        <div class=card><table>
          <tr><th></th><th>плагин</th><th>автор</th><th>TikTok</th><th></th></tr>
          %s
        </table></div>

        <h2>Загрузить плагин</h2>
        <form class=card method=post action="/admin/plugin/add"
              enctype="multipart/form-data">
          <p class=dim>Файл .mtp — манифест и иконка читаются из него.</p>
          <input type=file name=file accept=".mtp,.zip">
          <p><button>Загрузить</button></p>
        </form>

        <h2>Сбои</h2>
        <div class=card><table>
          <tr><th>когда</th><th>версия</th><th>что упало</th></tr>
          %s
        </table></div>

        <h2>Пароль от панели</h2>
        <form class=card method=post action="/admin/password">
          <p class=dim>Хранится солёным хешем в базе, не текстом.</p>
          <div class=row>
            <div><label>старый</label><input name=now type=password></div>
            <div><label>новый, от десяти знаков</label>
                 <input name=fresh type=password></div>
          </div>
          <p><button>Сменить</button></p>
        </form>

        <p><a class='button quiet' href="/admin/out">Выйти</a></p>
        """ % ("".join(rows) or "<tr><td class=dim>пока пусто</td></tr>",
               "".join(painted) or "<tr><td class=dim>пока пусто</td></tr>",
               "".join(packs) or "<tr><td class=dim>пока пусто</td></tr>",
               "".join(fallen) or "<tr><td class=dim>пока пусто</td></tr>"), said)


def badge_page(badge, wearers, said=""):
    # the third column says whether the account proved it is anybody's, which
    # is the answer to "почему у него не сохраняются значки"
    rows = "".join(
        "<tr><td>%s</td><td class=dim>%s</td><td class=dim>%s</td>"
        "<td><form method=post "
        "action='/admin/badge/take' style='display:inline'>"
        "<input type=hidden name=id value='%s'><input type=hidden name=uid value='%s'>"
        "<button class=quiet>Забрать</button></form></td></tr>"
        % (html.escape(uid), "показан" if shown else "скрыт",
           "подтверждён" if proved else "не подтверждён",
           html.escape(badge["id"]), html.escape(uid))
        for uid, shown, proved in wearers)

    return page("TokkiYou", """
        <h1>%s</h1>
        <p class=dim><img class=dot src='/icon/%s'> #%s</p>

        <h2>Выдать</h2>
        <form class=card method=post action="/admin/badge/give">
          <input type=hidden name=id value='%s'>
          <label>айди аккаунтов, через пробел или с новой строки</label>
          <textarea name=uids rows=3 placeholder="7551880794956989495"></textarea>
          <p><button>Выдать</button></p>
        </form>

        <h2>У кого он есть</h2>
        <div class=card><table>
          <tr><th>аккаунт</th><th></th><th></th><th></th></tr>%s
        </table></div>

        <p><a class='button quiet' href="/admin">Назад</a></p>
        """ % (html.escape(badge["text"] or badge["id"]),
               html.escape(badge["image"] or "note.png"), html.escape(badge["id"]),
               html.escape(badge["id"]),
               rows or "<tr><td class=dim>пока ни у кого</td></tr>"), said)
