"""Reading a public TikTok profile page.

Both the bot and the badge server need the same thing from TikTok: what an
@name says about itself, and which account id is behind it. TikTok will not
answer a third party any other way, so the page is read as any visitor reads
it.

Nothing here signs in and nothing here is asked more than once a minute by
anything that uses it.
"""

import json
import re
import time
import urllib.parse
import urllib.request

#: a phone rather than a desktop, and that is not a detail. TikTok's firewall
#: answers this box's address with a javascript challenge when the request
#: looks like a desktop browser, and serves the page when it looks like a
#: phone. The same request from a home connection is served either way.
BROWSER = ("Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X)"
           " AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5"
           " Mobile/15E148 Safari/604.1")

#: TikTok hands out names of letters, digits, dots and underscores -- but a
#: bug in 2024 let people take names in other alphabets, and those accounts
#: are still here. Latin only turned every one of them away before anything
#: was even asked.
NAME = re.compile(r"^[^\s/?#@&=%\\]{2,30}$")


def named(name):
    """Whether this is the shape of an @name at all."""
    return bool(name) and bool(NAME.match(name))


def page(name, patience=20):
    """The profile page as a browser would get it, or None."""
    if not named(name):
        return None
    try:
        request = urllib.request.Request(
            "https://www.tiktok.com/@" + urllib.parse.quote(name),
            headers={"User-Agent": BROWSER, "Accept-Language": "en"})
        with urllib.request.urlopen(request, timeout=patience) as answer:
            return answer.read(900000).decode("utf-8", "replace")
    except Exception:
        return None


class _KeepRedirect(urllib.request.HTTPRedirectHandler):
    """Stop at the redirect rather than following it: the address is the answer."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise _Moved(newurl)


class _Moved(Exception):
    def __init__(self, where):
        Exception.__init__(self, where)
        self.where = where


#: names an id has been seen under, so a second try costs one request
NAMES = {}
NAMES_KEEP = 6 * 3600


def name_of(uid, patience=15):
    """The @name behind an account id.

    TikTok has no page for an id, but it has a share link for one, and that
    link answers with the name. It means nobody has to type their own name
    into the mod to prove anything -- and the page the name leads to is
    checked against the id anyway, so a wrong answer here proves nothing.
    """
    if not uid or not uid.isdigit():
        return None
    known = NAMES.get(uid)
    if known and time.time() - known[0] < NAMES_KEEP:
        return known[1]
    opener = urllib.request.build_opener(_KeepRedirect)
    request = urllib.request.Request(
        "https://www.tiktok.com/share/user/" + uid,
        headers={"User-Agent": BROWSER, "Accept-Language": "en"})
    try:
        with opener.open(request, timeout=patience) as answer:
            where = answer.geturl()
    except _Moved as moved:
        where = moved.where
    except Exception:
        return None
    # the name in a redirect may arrive as itself or as percent-escapes,
    # depending on the alphabet it is written in
    found = re.search(r"/@([^/?#]{1,90})", where or "")
    if not found:
        return None
    name = urllib.parse.unquote(found.group(1))
    if not named(name):
        return None
    if len(NAMES) > 5000:
        NAMES.clear()
    NAMES[uid] = (time.time(), name)
    return name


def read(text, name):
    """What the page says about an account.

    The page carries a blob of json for its own use, and everything a profile
    shows is in it: the name, the picture, the words under it, how many follow
    and how much was liked. It is read as json while the blob is where it has
    always been, and by pattern when it is not -- an id alone is worth more
    than nothing, and guessing the rest is not.
    """
    found = re.search(r'<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__"'
                      r' type="application/json">(.*?)</script>', text, re.S)
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

    found = (re.search(r'"user":\{"id":"(\d+)"', text)
             or re.search(r'"id":"(\d{6,24})","shortId"', text))
    if not found:
        return None
    return {
        "uid": found.group(1),
        "username": name,
        "nickname": one(text, r'"nickname":"(.*?)"'),
        "avatar": one(text, r'"avatarMedium":"(.*?)"').replace("\\u002F", "/"),
        "about": one(text, r'"signature":"(.*?)"'),
    }


def one(text, pattern):
    found = re.search(pattern, text)
    if not found:
        return ""
    try:
        return json.loads('"%s"' % found.group(1))
    except Exception:
        return found.group(1)


def behind(uid, patience=20):
    """The account with this id, read off its own page.

    The share link is followed wherever it goes rather than picked apart. It
    answers with an @name most of the time, with percent-escapes when the name
    is not written in latin -- people took such names through a bug in 2024
    and still have them -- and with a sec_uid for some accounts. All three are
    a page; only the shape of the address differs, and the page says which id
    it belongs to, which is the thing being checked anyway.
    """
    if not uid or not uid.isdigit():
        return None
    for attempt in (0, 1):
        if attempt:
            time.sleep(1.5)
        try:
            request = urllib.request.Request(
                "https://www.tiktok.com/share/user/" + uid,
                headers={"User-Agent": BROWSER, "Accept-Language": "en"})
            with urllib.request.urlopen(request, timeout=patience) as answer:
                where = answer.geturl()
                body = answer.read(900000).decode("utf-8", "replace")
        except Exception:
            continue
        found = re.search(r"/@([^/?#]{1,90})", where or "")
        name = urllib.parse.unquote(found.group(1)) if found else ""
        who = read(body, name)
        if who:
            return who
    return None


def profile(name, patience=20):
    """Everything known about an @name, read fresh.

    Asked twice before giving up: TikTok refuses a request here and there,
    and one refusal used to come out as "TikTok did not answer" at somebody
    who had done everything right.
    """
    for attempt in (0, 1):
        if attempt:
            time.sleep(1.5)
        text = page(name, patience)
        if text:
            who = read(text, name)
            if who:
                return who
    return None
