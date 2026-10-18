# The badge server

Badges used to be `badges.json` in this repository, re-read by every phone
every five minutes. That worked and cost nothing. It had two limits worth
leaving it for: granting a badge meant a commit, and nobody could decide
anything about their own -- which of theirs to show, and in what order.

## What is here

    badges.py    the service: the standard library, one sqlite file
    panel.py     the pages the panel is made of
    bot.py       the telegram bot
    seed.py      fills it from badges.json, once
    badgectl     grant, take back, look up
    icons/       the pictures badges are drawn with
    plugins/     what the store offers

One secret is written by hand on the server and is not in this repository:
`bot.txt`, the bot's token. It is read at the moment it is needed, so
replacing it is writing the file. The panel's password lives in the database
as a salt and a hash; it used to be a second file, in plain text, on the same
disk that serves the panel.

No framework, because there is nothing a framework would do here. nginx sits
in front for rate limiting; the service listens on localhost only.

## What it answers

    GET  /badges            what every phone reads, with an ETag
    POST /claim             a phone asks what an account holds
    POST /prove             a code for that account to put in its bio
    POST /prove/check       the page is read, and the account is given its key
    POST /profile           that account decides what to show and in what order
    POST /old               the badge anyone running the mod before the 24th gets
    POST /gradient          a supporter's colours
    POST /crash             a crash report, from a phone whose owner said yes
    GET  /icon/<name>.png   the pictures

`/badges` is the same shape `badges.json` had, so the mod reads it the same
way. It carries `free_until` and `now` as well, which is how the app knows
whether the free badge is still being given out -- by the server's clock,
never by the phone's.

## On authentication

TikTok will not tell a third party that somebody is who they say they are, so
the server asks the one thing TikTok does say out loud: what a profile page
holds. `/prove` hands out a short code, the person puts it in their bio, and
`/prove/check` reads the page and looks for it. Nobody can write into somebody
else's bio, so nobody else gets the key.

The account id is what is proved, not the name. The name is found from the id
through TikTok's own share link, and the page it leads to is checked against
the id again -- so nothing has to be typed in, and a wrong name proves
nothing.

This replaced first-come-first-served, which lasted exactly as long as it took
somebody to write a loop: fifty-odd free badges went to accounts that had
never run the mod, and one address asked `/claim` a million times for keys to
accounts by id. Every key made under the old rule was thrown away.

What the server does not trust is anything that decides what is *given*:

- the key, which now costs a line in a bio rather than a request;
- which badge `/old` grants is written here, not sent by the caller, so no
  request can ask for `owner` or any other;
- whether the day has passed is the server's clock;
- a short wait per account **and** per address, counted in the database, so
  a client that stopped counting gains nothing;
- how many profile pages may be read a minute, all callers together, because
  TikTok's patience with this address is shared with the bot;
- nginx limits requests per address as well: reads and writes separately,
  since a flood would aim at writes.

## The panel

`/admin`, with the name and password kept in the database, and a password
that can be changed from the page itself. Badges: what exists, who
wears each one, handing one out to a list of account ids, taking one back, and
making a new one with a picture uploaded rather than named. Plugins: what the
store offers, and adding one by uploading the packed `.mtp` -- the manifest and
the icon are read out of that file rather than typed in, so what the store says
about a plugin is what the phone will load.

## The bot

`@margy_robot`, by long polling: no certificate, no port, nothing to expose. It
does two things.

    профиль <ник в тиктоке или айди>    the name, the picture and the badges

And an icon sent as a document with an account id in the caption is forwarded
to whoever decides.

Looking an account up by its @name has no API behind it: the profile page is
fetched and read out of the blob of json the page carries for its own use --
the name, the picture, the words under it, how many follow and how much was
liked, all of it sitting in `webapp.user-detail`. That works today and is
nobody's promise: when the blob moves, the id is still found by pattern and
the rest is left out rather than guessed at, and when even that fails the bot
says it cannot find them. A name that has been looked up is kept for a few
minutes -- an inline search asks again on every key pressed.

An account asked for by id gets its badges and nothing else: there is no page
to read without a name. Badges somebody has hidden are not listed -- hiding
one hides it from everybody, and the bot used to hand the full list back.

Every update is handled on its own thread. Reading a profile page takes
seconds, and doing that in the polling loop meant one inline search held up
every message behind it.

The avatar is a game of two refusals. Telegram will not fetch a picture from
tiktok -- the CDN does not serve whoever Telegram is -- and it will not fetch
one from this server either, because plain http on a bare address is not
something it will touch and there is no domain here to put a certificate on.
So the bytes are read here and handed over: uploaded with the message in a
chat, and for an inline answer -- which can only name a picture, never carry
one -- uploaded once to a message that is deleted immediately, keeping the
name Telegram gave it. Those names are kept in `face`. `/face/<id>.jpg` serves
avatars to anything that will fetch them, and is what the bot can use the day
there is a domain.

An answer carries the topic of the question it answers. In a group that keeps
topics, an answer without one goes to General -- and a closed General means
every answer is refused outright.

Inline mode has to be turned on in BotFather (`/setinline`) before the bot can
answer in other chats; nothing here can do that.

## Running it

    systemctl status margyt-badges margyt-bot
    journalctl -u margyt-badges -f
    journalctl -u margyt-bot -f

    badgectl list
    badgectl grant supporter 7551880794956989495
    badgectl who   7551880794956989495
    badgectl take  supporter 7551880794956989495

## What is left to do

It answers over plain http. A domain and a certificate would fix that; until
then the badges are public anyway, and the one thing worth protecting -- the
key a phone is given -- is worth protecting properly rather than partly.
