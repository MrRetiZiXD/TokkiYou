#!/usr/bin/env python3
"""The TokkiYou bot.

Two things: whose TikTok profile is this -- the name, the picture and the
badges -- and here is an icon I drew.

It talks to Telegram by long polling rather than a webhook, which needs no
certificate and no port open -- the bot asks Telegram for what happened and
Telegram answers when something has. On a box that is already serving badges
over plain http, that is one less thing to get wrong.

The token lives in `bot.txt` beside the database, written by hand, never here.

On looking up a TikTok account by its @name: there is no API for that, so the
profile page is read and the id taken out of the blob the page carries for its
own use. That works today and is nobody's promise -- when it stops, the bot
says it cannot find them rather than pretending.
"""

import html
import json
import os
import re
import sqlite3
import sys
import threading
import time
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import badges as service
import patcher
import tiktok

TOKEN_FILE = os.path.join(HERE, "bot.txt")

#: where the badge service answers, for the avatar it passes on
SITE = "https://margy.narez.xyz"

#: who receives an icon somebody offers
OWNER = 7826361017

API = "https://api.telegram.org/bot%s/%s"

BROWSER = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
           " (KHTML, like Gecko) Chrome/124.0 Safari/537.36")


def token():
    with open(TOKEN_FILE, encoding="utf-8") as handle:
        return handle.read().strip()


# ---------------------------------------------------------------- the store

def prepare():
    service.prepare()
    db = service.connect()
    db.commit()
    db.close()


def badges_of(uid):
    """Which badges an account wears, in its own order.

    Only the ones it shows. Somebody who turned a badge off turned it off for
    everybody, and answering here with the full list, helpfully marked
    "скрыт", handed it back out again.
    """
    db = service.connect()
    rows = db.execute(
        "SELECT b.id, b.text_ru, b.text FROM held h"
        " JOIN badge b ON b.id = h.badge WHERE h.uid = ? AND h.shown = 1"
        " ORDER BY h.place, h.badge", (uid,)).fetchall()
    db.close()
    return [{"id": r[0], "text": r[1] or r[2] or r[0]} for r in rows]


# ------------------------------------------------------------ looking up

def account(which, patience=20):
    """An account from an id or an @name, as far as anything can tell.

    An id is taken as it is: TikTok will not confirm one and nothing here
    needs it to. A name is looked up on the profile page, which is the only
    way there is.
    """
    which = which.strip().lstrip("@")
    if which.isdigit() and 6 <= len(which) <= 24:
        return {"uid": which, "username": "", "nickname": "", "avatar": ""}
    return by_name(which, patience)


#: names looked up lately -- name -> (when, profile or None)
SEEN = {}
KEEPS = 300
FORGETS = 60

#: as many names as are worth keeping at once
MANY = 2000


def keep(name, who):
    """Remember a lookup, and throw out what has gone stale.

    An inline search asks on every key pressed, so this fills with half-typed
    names; nothing here ever emptied it.
    """
    now = time.time()
    SEEN[name] = (now, who)
    if len(SEEN) > MANY:
        for older in [key for key, (when, found) in SEEN.items()
                      if now - when > (KEEPS if found else FORGETS)]:
            SEEN.pop(older, None)
    while len(SEEN) > MANY:
        SEEN.pop(next(iter(SEEN)), None)


def remembered(name):
    found = SEEN.get(name.lower())
    if not found:
        return False, None
    when, who = found
    if time.time() - when > (KEEPS if who else FORGETS):
        return False, None
    return True, who


def by_name(name, patience=20):
    """The account behind an @name, read off its profile page.

    Answers are kept for a few minutes because a name is asked for far more
    often than it changes: an inline search asks again on every key pressed,
    and reading the page every time is what made the bot stop answering
    anything at all.
    """
    if not tiktok.named(name):
        return None
    known, who = remembered(name)
    if known:
        return who

    try:
        request = urllib.request.Request(
            "https://www.tiktok.com/@" + urllib.parse.quote(name),
            headers={"User-Agent": BROWSER, "Accept-Language": "en"})
        with urllib.request.urlopen(request, timeout=patience) as answer:
            page = answer.read(900000).decode("utf-8", "replace")
    except Exception:
        return None

    who = read_profile(page, name)
    keep(name.lower(), who)
    return who


def read_profile(page, name):
    """What the page says about an account.

    The page carries a blob of json for its own use, and everything a
    profile shows is in it: the name, the picture, the words under it, how
    many follow and how much was liked. It is read as json while the blob
    is where it has always been, and by pattern when it is not -- an id
    alone is worth more than nothing, and guessing the rest is not.
    """
    found = re.search(r'<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__"'
                      r' type="application/json">(.*?)</script>', page, re.S)
    if found:
        try:
            info = (json.loads(found.group(1))["__DEFAULT_SCOPE__"]
                    ["webapp.user-detail"]["userInfo"])
            user = info.get("user") or {}
            counts = info.get("stats") or info.get("statsV2") or {}
            if user.get("id"):
                return {
                    "uid": str(user["id"]),
                    "username": user.get("uniqueId") or name,
                    "nickname": user.get("nickname") or "",
                    "avatar": (user.get("avatarMedium")
                               or user.get("avatarThumb") or ""),
                    "about": (user.get("signature") or "").strip(),
                    "verified": bool(user.get("verified")),
                    "private": bool(user.get("privateAccount")),
                    "followers": counts.get("followerCount"),
                    "likes": counts.get("heartCount") or counts.get("heart"),
                    "videos": counts.get("videoCount"),
                }
        except Exception:
            pass

    found = (re.search(r'"user":\{"id":"(\d+)"', page)
             or re.search(r'"id":"(\d{6,24})","shortId"', page))
    if not found:
        return None
    return {
        "uid": found.group(1),
        "username": name,
        "nickname": one(page, r'"nickname":"(.*?)"'),
        "avatar": one(page, r'"avatarMedium":"(.*?)"').replace("\\u002F", "/"),
    }


def one(page, pattern):
    found = re.search(pattern, page)
    if not found:
        return ""
    try:
        return json.loads('"%s"' % found.group(1))
    except Exception:
        return found.group(1)


# ------------------------------------------------------------ what is said

def badge_lines(uid):
    worn = badges_of(uid)
    if not worn:
        return "Значков нет."
    out = []
    for badge in worn:
        out.append("• %s" % html.escape(badge["text"]))
    return "\n".join(out)


#: as much as a caption will hold
CAPTION = 1024

#: as much of somebody's own words as is worth repeating
ROOMY = 300


def heap(count):
    """A number with room to breathe, so it can be read at a glance."""
    try:
        return "{:,}".format(int(count)).replace(",", "\u2009")
    except (TypeError, ValueError):
        return ""


def profile_card(who):
    named = who.get("nickname") or who.get("username") or ""
    head = ("<b>%s</b>" % html.escape(named) if named
            else "<code>%s</code>" % html.escape(who["uid"]))
    if who.get("verified"):
        head += " ✔"

    under = []
    if who.get("username"):
        under.append("@%s" % html.escape(who["username"]))
    if named:
        under.append("<code>%s</code>" % html.escape(who["uid"]))
    if who.get("private"):
        under.append("закрытый")

    counted = []
    for number, word in ((who.get("followers"), "подписчиков"),
                         (who.get("likes"), "лайков"),
                         (who.get("videos"), "видео")):
        shown = heap(number)
        if shown:
            counted.append("%s %s" % (shown, word))

    about = (who.get("about") or "").strip()
    if len(about) > ROOMY:
        about = about[:ROOMY].rstrip() + "…"

    lines = [head]
    if under:
        lines.append(" · ".join(under))
    if counted:
        lines.append(" · ".join(counted))
    if about:
        lines += ["", html.escape(about)]
    lines += ["", badge_lines(who["uid"])]

    # a caption is refused past its length, and cutting mid-tag would be
    # refused for worse reasons, so whole lines go instead
    while len("\n".join(lines)) > CAPTION and len(lines) > 2:
        lines.pop()
        lines[-1] = "…"
    return "\n".join(lines)


# ------------------------------------------------------------- the talking

def why(trouble):
    """What Telegram actually objected to.

    A refusal arrives as a 400 with the reason in the body, and reading only
    the status line turns every different mistake into the same "Bad Request"
    -- which is how a broken picture looked like a broken message for an hour.
    """
    body = getattr(trouble, "read", None)
    if body:
        try:
            said = json.loads(body().decode("utf-8", "replace"))
            return said.get("description") or said
        except Exception:
            pass
    return trouble


#: what the update being handled came from, for the answer to go back to
CURRENT = threading.local()


def here(chat):
    """The fields an answer needs to land where the question was asked.

    In a group that keeps topics every message belongs to one, and an answer
    without a topic goes to General -- which can be closed, and then it is
    refused outright rather than landing somewhere odd. Only the chat the
    question came from gets this; a forward to the owner must not.
    """
    topic = getattr(CURRENT, "topic", None)
    if topic and chat == getattr(CURRENT, "chat", None):
        return {"message_thread_id": topic}
    return {}


def call(what, **fields):
    data = json.dumps(fields).encode("utf-8")
    request = urllib.request.Request(
        API % (token(), what), data=data,
        headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=70) as answer:
            return json.loads(answer.read().decode("utf-8"))
    except Exception as trouble:
        if what != "getUpdates":
            print("%s: %s" % (what, why(trouble)), flush=True)
        return {}


def upload(what, field, name, blob, **fields):
    """A method call carrying a file, as multipart -- the only way to hand
    Telegram bytes rather than an address it has to fetch itself."""
    line = "----margyt%d" % time.time_ns()
    body = bytearray()
    for key, value in fields.items():
        body += ("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n"
                 % (line, key, value)).encode("utf-8")
    body += ("--%s\r\nContent-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\n"
             "Content-Type: application/octet-stream\r\n\r\n" % (line, field, name)).encode("utf-8")
    body += blob + ("\r\n--%s--\r\n" % line).encode("utf-8")

    request = urllib.request.Request(
        API % (token(), what), data=bytes(body),
        headers={"Content-Type": "multipart/form-data; boundary=" + line})
    try:
        with urllib.request.urlopen(request, timeout=90) as answer:
            return json.loads(answer.read().decode("utf-8"))
    except Exception as trouble:
        print("%s: %s" % (what, why(trouble)), flush=True)
        return {}


def picture(url):
    """The avatar itself, fetched here.

    Handing Telegram the address does not work -- it comes back with "failed
    to get HTTP URL content", because the CDN does not serve whoever Telegram
    is. Reading it the way the profile page was read does work, so the bytes
    go up with the message instead of an address.
    """
    if not url.startswith("http"):
        return None
    try:
        request = urllib.request.Request(url, headers={
            "User-Agent": BROWSER, "Referer": "https://www.tiktok.com/",
            "Accept": "image/avif,image/webp,image/*,*/*;q=0.8"})
        with urllib.request.urlopen(request, timeout=25) as answer:
            if not (answer.headers.get("Content-Type") or "").startswith("image/"):
                return None
            blob = answer.read(4 * 1024 * 1024)
    except Exception:
        return None
    return blob or None


def say(chat, text, reply=None):
    call("sendMessage", chat_id=chat, text=text, parse_mode="HTML",
         disable_web_page_preview=True, **here(chat),
         **({"reply_to_message_id": reply} if reply else {}))


def show(chat, who, reply=None):
    """A profile with its picture, when there is one to show.

    The address comes off the same page the id did. If the picture cannot be
    had -- the address is signed and does expire -- the words go out on their
    own rather than nothing going out.
    """
    card = profile_card(who)
    blob = picture(who.get("avatar") or "")
    if blob:
        answer = upload("sendPhoto", "photo", "avatar.jpg", blob,
                        chat_id=chat, caption=card, parse_mode="HTML",
                        **{k: str(v) for k, v in here(chat).items()},
                        **({"reply_to_message_id": str(reply)} if reply else {}))
        if answer.get("ok"):
            return
    say(chat, card, reply)


def known_face(url):
    """A name telegram will accept for this avatar, making one if need be.

    An inline answer can only name a picture, never carry one, and the only
    addresses telegram will fetch are somebody else's: it refuses this
    server -- plain http on a bare address, no domain to put a certificate
    on -- while tiktok refuses telegram. So the avatar is handed over once,
    as bytes, and the name telegram gives it is kept and reused. The message
    it was handed in is deleted at once; the name outlives it.
    """
    if not url:
        return ""
    told = service.face_told(url)
    if told:
        return told

    blob = picture(url)
    if not blob:
        return ""
    answer = upload("sendPhoto", "photo", "avatar.jpg", blob,
                    chat_id=str(OWNER), disable_notification="true")
    sent = answer.get("result") or {}
    shots = sent.get("photo") or []
    if not shots:
        return ""
    call("deleteMessage", chat_id=OWNER, message_id=sent["message_id"])
    told = shots[-1]["file_id"]
    service.face_tell(url, told)
    return told


HELP = (
    "<b>TokkiYou</b>\n\n"
    "<b>профиль</b> &lt;ник в тиктоке или айди&gt; — ник, аватарка и значки\n"
    "<b>иконка</b> — пришли картинку документом и свой айди, попадёт владельцу\n\n"
    "В любом чате можно написать <code>@%s ник</code> — покажет то же самое."
)


def handle(update):
    if "inline_query" in update:
        return inline(update["inline_query"])

    message = update.get("message") or update.get("channel_post")
    if not message:
        return

    chat = message["chat"]["id"]
    CURRENT.chat = chat
    CURRENT.topic = (message.get("message_thread_id")
                     if message.get("is_topic_message") else None)
    from_who = message.get("from") or {}
    text = (message.get("text") or message.get("caption") or "").strip()
    low = text.lower()

    # A picture is an offered icon only where it can be nothing else: in a
    # private chat, or with the word in the caption. In a group every photo
    # anybody posts is not an offer, and answering each one with instructions
    # is the bot being in the way.
    if message.get("document") or message.get("photo"):
        private = (message["chat"].get("type") == "private")
        if private or low.startswith("иконка") or low.startswith("icon"):
            return offered(message, chat, from_who, text)
        return

    # Somebody signing in to the patcher app: it opened the bot with a
    # one-time code, and pressing Start is how the code learns who they are.
    if low.startswith("/start p_"):
        code = text.split("p_", 1)[1].strip()
        name = from_who.get("first_name") or from_who.get("username") or ""
        if from_who.get("id") and patcher.claim(code, from_who["id"], name):
            return say(chat, "Готово. Возвращайся в <b>Margy Patcher</b>.")
        return say(chat, "Этот вход уже не действует. Нажми «Войти через Telegram» "
                         "в патчере ещё раз.")

    # The owner says which group is the supporters' one, from inside it.
    if low.split("@")[0] == "/supporters":
        # An admin who posts as the group itself has no name to be known by,
        # and saying nothing left the owner guessing why nothing happened.
        if message.get("sender_chat") or from_who.get("id") == 1087968824:
            return say(chat, "Напиши это от своего имени, не анонимно: "
                             "я должен видеть, что это владелец.")
        if from_who.get("id") != OWNER:
            return
        if message["chat"].get("type") == "private":
            return say(chat, "Напиши это в группе поддержавших, куда я добавлен.")
        patcher.set_group(chat)
        return say(chat, "Запомнил: это группа поддержавших.")

    if low in ("/start", "/help", "помощь"):
        me = call("getMe").get("result", {}).get("username", "margyt_bot")
        return say(chat, HELP % me)

    # One name to ask about, and it is the TikTok one. A telegram name is a
    # different thing that happens to look the same, and having both meant
    # guessing which was meant.
    if low.startswith("профиль") or low.startswith("/profile"):
        which = text.split(maxsplit=1)[1].strip() if len(text.split()) > 1 else ""
        if not which:
            return say(chat, "Чей? Напиши ник в тиктоке: "
                             "<code>профиль mrretizixd</code>", message["message_id"])
        who = account(which)
        if not who:
            return say(chat, "Не нашёл такого в тиктоке.", message["message_id"])
        return show(chat, who, message["message_id"])

    if low.startswith("иконка"):
        return say(chat, "Пришли картинку <b>документом</b>, а в подписи — "
                         "свой айди в тиктоке.")


def offered(message, chat, from_who, caption):
    """An icon somebody drew, on its way to whoever decides."""
    which = ""
    for piece in caption.replace(",", " ").split():
        if piece.strip().isdigit() and 6 <= len(piece.strip()) <= 24:
            which = piece.strip()
            break

    if not which:
        return say(chat, "Добавь в подпись свой айди в тиктоке — иначе непонятно, "
                         "кому потом выдавать значок.", message["message_id"])

    who = "@" + from_who["username"] if from_who.get("username") else str(from_who.get("id"))
    call("forwardMessage", chat_id=OWNER, from_chat_id=chat,
         message_id=message["message_id"])
    say(OWNER, "Иконка от %s\nТикток: <code>%s</code>" % (html.escape(who), which))
    return say(chat, "Отправил владельцу. Если возьмут — значок за иконку твой.",
               message["message_id"])


def inline(query):
    which = (query.get("query") or "").strip()
    results = []
    if which:
        # an inline answer is due in seconds and then thrown away, so a slow
        # page is given up on rather than waited for
        who = account(which, patience=5)
        if who:
            worn = badges_of(who["uid"])
            title = who.get("nickname") or ("@" + who["username"] if who.get("username")
                                            else who["uid"])
            about = ("%d значк(ов)" % len(worn)) if worn else "значков нет"
            if who.get("followers") is not None:
                about = "%s подписчиков · %s" % (heap(who["followers"]), about)
            card = profile_card(who)
            told = known_face(who.get("avatar") or "")
            if told:
                results.append({
                    "type": "photo",
                    "id": who["uid"][:60],
                    "photo_file_id": told,
                    "title": title,
                    "description": about,
                    "caption": card,
                    "parse_mode": "HTML",
                })
            else:
                results.append({
                    "type": "article",
                    "id": who["uid"][:60],
                    "title": title,
                    "description": about,
                    "input_message_content": {
                        "message_text": card,
                        "parse_mode": "HTML",
                        "disable_web_page_preview": True,
                    },
                })
    call("answerInlineQuery", inline_query_id=query["id"], results=results,
         cache_time=30, is_personal=False)


#: how many updates may be in hand at once
ROOM = threading.BoundedSemaphore(8)


def apart(update):
    """One update, out of everybody else's way.

    Reading a profile page takes seconds, and doing it in the polling loop
    meant one inline search held up every message behind it. Each update is
    answered on its own thread, with a limit so a flood cannot make
    thousands of them.
    """
    try:
        handle(update)
    except Exception as trouble:
        print("update %s: %s" % (update.get("update_id"), trouble), flush=True)
    finally:
        ROOM.release()


def main():
    prepare()
    print("bot up", flush=True)
    offset = 0
    while True:
        answer = call("getUpdates", offset=offset, timeout=50,
                      allowed_updates=["message", "inline_query"])
        for update in answer.get("result", []):
            offset = update["update_id"] + 1
            ROOM.acquire()
            threading.Thread(target=apart, args=(update,), daemon=True).start()
        if not answer:
            time.sleep(3)


if __name__ == "__main__":
    main()
