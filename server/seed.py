#!/usr/bin/env python3
"""Fill the badge server from the file the badges used to live in."""

import json
import os
import sqlite3
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import badges as service

EXTRA = {
    "tokkiyou": {
        "colour": "5E2BCE",
        "image": "",
        "title": "TokkiYou",
        "text": "TokkiYou member",
        "text_ru": "Участник TokkiYou",
        "text_uk": "Учасник TokkiYou",
        "button": "",
        "claimable": 0,
    },
    # the free one: named here, granted by the server, and gone on the day
    "old": {
        "colour": "40E0D0",
        "image": "note.png",
        "title": "TokkiYou",
        "text": "Used TokkiYou before the 20th of September",
        "text_ru": "Использовал TokkiYou до 20-го сентября",
        "text_uk": "Користувався TokkiYou до 20-го вересня",
        "button": "Got it",
        "claimable": 1,
    },
}

#: which of the mod's own pictures a badge is drawn with
PICTURES = {"owner": "owner.png", "supporter": "supporter.png"}


def main():
    service.prepare()
    db = service.connect()

    with open(os.path.join(HERE, "badges.json"), encoding="utf-8") as handle:
        file = json.load(handle)

    now = int(time.time())
    for badge in file["badges"]:
        db.execute(
            "INSERT INTO badge (id, colour, image, title, text, text_ru, text_uk,"
            " button, claimable) VALUES (?,?,?,?,?,?,?,?,0)"
            " ON CONFLICT(id) DO UPDATE SET colour=excluded.colour,"
            " image=excluded.image, title=excluded.title, text=excluded.text,"
            " text_ru=excluded.text_ru, text_uk=excluded.text_uk,"
            " button=excluded.button",
            (badge["id"], badge.get("colour", ""),
             PICTURES.get(badge["id"], badge.get("image", "")),
             badge.get("title", ""), badge.get("text", ""),
             badge.get("text_ru", ""), badge.get("text_uk", ""),
             badge.get("button", "")))
        for uid in badge.get("users", []):
            db.execute("INSERT OR IGNORE INTO held (uid, badge, given_at)"
                       " VALUES (?, ?, ?)", (uid, badge["id"], now))

    for key, badge in EXTRA.items():
        db.execute(
            "INSERT INTO badge (id, colour, image, title, text, text_ru, text_uk,"
            " button, claimable) VALUES (?,?,?,?,?,?,?,?,?)"
            " ON CONFLICT(id) DO UPDATE SET colour=excluded.colour,"
            " image=excluded.image, text=excluded.text, text_ru=excluded.text_ru,"
            " text_uk=excluded.text_uk, claimable=excluded.claimable",
            (key, badge["colour"], badge["image"], badge["title"], badge["text"],
             badge["text_ru"], badge["text_uk"], badge["button"], badge["claimable"]))

    db.commit()
    counted = db.execute("SELECT COUNT(*) FROM badge").fetchone()[0]
    wearing = db.execute("SELECT COUNT(*) FROM held").fetchone()[0]
    db.close()
    print("badges: %d, worn: %d" % (counted, wearing))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
