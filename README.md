<div align="center">

<img src="assets/banner.png" alt="TokkiYou" width="640">

**A TikTok mod for Android.**
The second in the line after [Margy](https://github.com/narezany/Margelet).
Built from the official apk with the patches in this repository.

[![channel](https://img.shields.io/badge/channel-TokkiYou-C6A4FF?style=flat-square)](https://t.me/tokkiyou)
[![forum](https://img.shields.io/badge/forum-TokkiYou-C6A4FF?style=flat-square)](https://t.me/tokkiyougroup)
[![plugins](https://img.shields.io/badge/plugins-docs-C6A4FF?style=flat-square)](docs/plugins.md)
[![licence](https://img.shields.io/badge/licence-MIT-C6A4FF?style=flat-square)](#licence)

</div>

---

Keeps TikTok's own package, `com.zhiliaoapp.musically`, so it goes in **instead
of** the official app rather than beside it: uninstall TikTok first, then
install this. arm64 only. No root.

Everything the mod adds is on one screen: **Settings and privacy → TokkiYou**,
the first row. There is no second icon on the home screen — the mod goes in
through TikTok's own settings and nowhere else.

Nothing here is hidden from the person using it. Every switch says what it does
and what it does not reach, the mod keeps a diary of what it saw that can be
copied out in one tap, and the parts that cannot work are written down rather
than quietly left broken.

## What it adds

<details>
<summary><b>The country the app thinks you are in</b></summary>

TikTok asks the device where it is: the SIM's country, the carrier, the MCC/MNC
pair, whether a card is present at all. Every one of those answers now comes
from the mod instead of from the phone. Twenty-four countries are on the list,
the Netherlands by default.

The interface language is left alone. Only the region changes — otherwise the
app would switch itself to Dutch along with the country.

This reaches `carrier_region` and `sys_region`, which is what the feed and a
good part of the feature gates read. It does not reach `store_region`: that one
is fixed on the server when the account is registered, and no client can move
it. Your IP is a separate matter and wants a VPN.

Switched off, the mod is not in the way: every redirected call hands the
question straight back to the real one, down to the exception it would have
thrown.
</details>

<details>
<summary><b>The phone's own colours</b></summary>

The palette Android works out from the wallpaper, over the theme TikTok is
wearing, and the accent taken from the same place. On by default on Android 12
and up; below that a phone has no palette of its own and the switch says so.
A colour picked by hand is still there for the accent. For the theme it is not:
there is one theme, and it is the one the rest of the phone is drawn in.

The rule this follows is the one the app already follows: a colour is repainted
only where TikTok itself would repaint it when the phone switches between light
and dark. Anything else is left exactly as it was — the brand pink, a photo,
the white of an avatar ring.

Working out that set is the whole of it, and the obvious place to look is the
wrong one. A colour that differs between themes is normally a resource with a
`night` variant, and there are twenty-two of those in the whole apk — nowhere
near a theme. The names are no help either; this build has them stripped. The
theme is in the **styles**: every colour in TikTok's design system is a theme
attribute, and the two themes are two styles giving the same attribute two
different values. `margyt/nightly.py` reads the style table, pairs them up, and
writes the answer into the mod as a sorted list of colours it is allowed to
touch. Nothing else is on the list, so nothing else can be repainted by
accident.
</details>

<details>
<summary><b>The colour it is all drawn in</b></summary>

TikTok is built around one pink, `#FE2C55` — and around a family of shades next
to it: the same pink at a dozen opacities, the magentas its gradients run
through, the reds of Shop and LIVE. `--accent RRGGBB` moves the whole family
onto a colour of your choosing.

Not by flattening it. Every shade is moved by the same step that takes the
brand pink to the chosen colour, in hue, saturation and value at once, so the
pink itself lands exactly on the accent, a light pink comes out a light accent,
and the two ends of a gradient stay two ends of a gradient. Alpha is never
touched. `margyt/palette.py` decides what belongs to the family; the hue window
is deliberately narrow enough to leave TikTok's other brand colour, the cyan
`#25F4EE`, exactly where it is.

In 46.9.42 that is 76 values in the resource table and 315 in 217 compiled xml
files — 104 distinct shades — plus the 72 places the bytecode spells a colour
out as a constant.

Those last 72 are the only ones that can still change afterwards: the mod sends
them through itself, so the palette in **Settings and privacy → TokkiYou** moves
them while the app runs. The rest are read by the framework out of the resource
table, inside its own code, and no client without root rewrites a 25 MB table
it has already mapped. So the palette reaches the code and `--accent` reaches
the pictures, and a build makes the two agree by baking the same colour the
palette starts on.
</details>

<details>
<summary><b>The advertisements, taken out of the page</b></summary>

TikTok's feed arrives as a `FeedItemList`, and every post in it says for itself
whether it is an advertisement — `Aweme.isAd()`, a real name on a real model.
So the ads are dropped where the page is read rather than hidden on each screen
that might draw one: `FeedItemList.getItems()` comes through the mod, and what
goes back is the same list without them.

Answering `isAd()` with false instead would be the wrong lever — the post would
still be there, just no longer labelled.
</details>

<details>
<summary><b>Saving what TikTok will not save</b></summary>

**A video without the stamp.** TikTok's own model carries two addresses for the
same file: `Video.getDownloadAddr()`, which the save button uses, and
`Video.getDownloadNoWatermarkAddr()` beside it. Both are real, unobfuscated
names, and the second is TikTok's own — the app already models it, for the
cases where it serves the file clean itself. The swap is one call site.

**A picture without the label.** That one is drawn on the phone: text and a logo
composed into a bitmap and stamped onto the picture with a single
`Canvas.drawBitmap`. Redirecting every `drawBitmap` in the apk would be absurd;
the build finds the one class that assembles the label — by a marker in its
template, not by its obfuscated name — and rewrites the one instruction inside
it.

**An image out of a comment**, where there is no clean copy to fall back on:
that stamp is made by a server, so the mod asks the server for the version
without it.

**An avatar**, which TikTok offers no way to keep. The picture is easy — `User`
has its address at four sizes under real names. Knowing whose profile is open
is not, because the screen keeps that in a field the obfuscator renames every
release. So the mod does not ask the screen: every read of an avatar address
passes through it on the way to being drawn, and the last one is the one on
screen.

**A sticker out of a conversation.** Touching one goes through
`MessageListStickerClickAbility` and is handed a `StickerTemplate` — both real
names. Five methods take that type; all five come through the mod, which notes
the sticker on the way past and passes the call straight on, so whatever the
app was going to do still happens.

Everything saved goes into the gallery two ways, because Android changed its
mind halfway: a MediaStore row on anything recent, which needs no permission
for an app writing its own entry, and a file in Pictures plus a word to the
scanner on older ones.
</details>

<details>
<summary><b>The sound TikTok switched off</b></summary>

When a track is pulled — a copyright claim, a region, whatever the label asked
— the video is not removed. It stays, with its sound turned off, through four
answers on the sound's own model: whether it is available at all, its status,
whether sharing it is muted, and how. All four are real names on a real class,
all four come through the mod, and with the switch on they say the sound is
fine.
</details>

<details>
<summary><b>The switches TikTok ships turned off for you</b></summary>

Every feature in the app is behind a named flag whose value is decided per
account on the server. Two people on the same version are running different
apps: background playback, voice comments, the feed scrolling itself are all
already written, already shipped, and simply not switched on for most accounts.

The flags are read through one class whose name is real, so the mod answers for
the three it knows by name and hands everything else straight back. Nothing is
invented here — these are TikTok's own features, turned on for the person who
paid for the phone.

Four more used to be on this list. They are gone because TikTok now ships them
to everybody, and a switch that does nothing is worse than no switch.
</details>

<details>
<summary><b>A scrubbing bar on every video</b></summary>

TikTok decides per video whether to draw one and passes the decision to a view
as a number: `setSeekBarShowType`. The method's name is real; the class holding
it is not, and will be spelled differently next release. So the rewrite matches
the method wherever it is — any owner, that name, that shape — and the receiver
arrives as a plain `Object`, which is why the call is handed back by reflection
rather than directly.
</details>

<details>
<summary><b>Streaks that keep themselves</b></summary>

The one thing in the mod that acts rather than answers, and the only one that
sends anything. Off unless switched on, it needs a sticker to have been chosen,
and it sends at most one sticker per conversation per day.

Both halves are found by real names at runtime: `StreakData` says which streaks
are fading, `IStreakService` is what sends. Nothing is scraped off a screen and
nothing is scheduled while the app is closed.
</details>

<details>
<summary><b>Badges</b></summary>

A mark after a name, wherever that name is written — the profile, a comment,
the line under a video.

There is no screen to patch for this, and there does not need to be: every one
of those places asks the same model the same question, so the answer carries
the mark. `getNickname()` comes back with one invisible character on the end,
the text is caught on its way into a `TextView`, and the character becomes a
picture.

The badges themselves are **[badges.json](badges.json)** in this repository,
read at startup and again every five minutes, and kept on disk so they are
there before the network answers. Adding one is editing that file — no new
build, no update for anybody.
</details>

<details>
<summary><b>Updates that announce themselves</b></summary>

[version.json](version.json) says what the latest TokkiYou is and where its apk
lives. The mod reads it the same way it reads the badges, offers the update in
a dialog of its own, downloads it and hands it to the installer.

Refusing is remembered for as long as the app is running and no longer: the
offer comes back the next time TikTok starts, not five minutes later.

Handing an apk to the installer needs a content uri rather than a path, which
normally means a `FileProvider` and its xml resource — and this build adds no
resources. The provider the mod already declares as its start-up hook serves
the file instead, read-only, out of one directory, for that one intent.
</details>

<details>
<summary><b>Plugins</b></summary>

Other people's code, running inside this one. A plugin is a zip — `.mtp` —
holding a manifest, a dex and an icon; installing one from
**Settings and privacy → TokkiYou → Plugins** unpacks it into the app's own
files, and the switch loads it with `DexClassLoader` and starts calling its
hooks. There is no storage permission involved: the document picker is the only
way in.

Margy's plugins are Python, and these are not, for a reason that is about
repacking rather than taste — a Python runtime goes into a fork at build time,
and this is somebody else's apk. The format is the same shape with a dex where
the `main.py` would be.

A plugin can hook the start of the process (before TikTok's own
`Application.onCreate`), the activities as they come and go, every colour on
its way to the screen, and every answer about where the phone is. One that
throws is written to the diary and dropped for the rest of that process rather
than asked again.

There is no sandbox and there cannot be one: a plugin has everything TikTok
has. The settings screen says so where the list is, and so does the
documentation.

`python3 -m margyt.plugin examples/hello` builds one, with the toolchain the
apk build already downloads. **[docs/plugins.md](docs/plugins.md)** is the rest:
the manifest, every hook, and what is deliberately not there.
</details>

<details>
<summary><b>Its own name and icon</b></summary>

`TokkiYou`, in the mint the whole line uses — `#C6A4FF`, straight off the Margy
banner — with a white note where Margy has a white paper plane.

The icon is not added as a new resource; the files behind TikTok's own icon are
rewritten where they lie. The adaptive icon's two layers become vector
drawables compiled by this repository, and each legacy density gets a bitmap
scaled to exactly the size the one it replaces was. The resource id, the table
entry and the density each file was chosen for never move.

The same rule covers everything the mod draws. The badge and the Material icons
on the settings screen are compiled into the mod's own dex as base64 PNGs
rather than added to `res/`, because adding one resource means rewriting a
25 MB resource table.
</details>

<details>
<summary><b>A row in TikTok's own settings</b></summary>

**Settings and privacy → TokkiYou**, at the top of the list, drawn like the rows
around it.

The row is not written into the bytecode that builds that list, and it could
not be: the screen is Jetpack Compose. One `ComposeView` draws the title, the
back arrow and every row, and from outside it there is nothing to get between
them. So the row goes above the screen instead of inside the list.

The mod gets there through a start-up hook of its own — a `<provider>`, which
Android instantiates before the application's onCreate whether anything queries
it or not — and from there watches for the settings screen. When it appears,
the mod asks the screen where it keeps its pages, wraps that container from the
outside, and puts the row above it. The container itself is left exactly where
it was, because that is what the fragment manager adds pages to and takes them
out of.

Two names hold this up, and both are real rather than obfuscated:
`SettingContainerActivity` and its own `getFragmentContainer()`. Androidx is no
help — it is in the apk with its method names shortened away, `getFragments()`
included — and the build checks the manifest still declares that screen,
stopping rather than shipping a mod whose settings cannot be reached.

The row is not styled by hand either. TikTok's rows are Compose, drawn from
colours and dimensions that live in obfuscated Kotlin — and the resource table
is no help, since TikTok's own colours are called `ag` and `ah` in there. So
the mod draws the screen it is about to sit on into a bitmap of its own and
reads the style out of the pixels: the card colour, the text colour, the margin
the cards keep from the edge, the radius of their corners. Whatever TikTok is
drawing today, in whichever theme, is what the row is built from, and the diary
records the numbers it found. The mod's own screen, and its dialogs, are drawn
from the same measurements.

The screen has no launcher entry of its own. It had one once, as a way in if
the row ever failed to appear, and it is gone because two icons for one app is
a poor trade for a fallback that cannot happen quietly: the build refuses to
write an apk whose `SettingContainerActivity` it cannot find, so an apk that
exists is one whose row has somewhere to go. Without the launcher entry the
screen wants no task of its own either, which is what lets the back button
return to the settings it was opened from.
</details>

<details>
<summary><b>The diary</b></summary>

Nobody is going to run logcat against a modded TikTok. So the mod writes down
the few things worth knowing — the start-up hook ran, this screen came up, the
style it measured, a rewrite that landed somewhere unexpected — and shows them
at the foot of its own screen, behind a row with a **Copy** button on it.

It survives the app being killed, which is the point: it is read after
something went wrong, and that is usually after a restart. Three separate bugs
in this repository were found by reading it rather than by guessing.
</details>

<details>
<summary><b>Where to find the mod, and who made it</b></summary>

The last card on the mod's screen: the channel, the forum, the account id you
would quote when reporting something, and a **Thanks** that opens the names —
and, for anyone who wants to, a card number and a YooMoney link.
</details>

<details>
<summary><b>No root, and no Xposed either</b></summary>

Without root there is no Xposed, and without Xposed there is nothing to hook at
runtime. So the calls are rewritten in the bytecode instead. Every

```smali
invoke-virtual {v0}, Landroid/telephony/TelephonyManager;->getSimCountryIso()Ljava/lang/String;
```

becomes

```smali
invoke-static {v0}, Lcat/narezany/margyt/Region;->getSimCountryIso(Landroid/telephony/TelephonyManager;)Ljava/lang/String;
```

The instruction format (35c), the register count and the return type all match,
so nothing has to be renumbered. The receiver moves to the first argument and is
handed back the real answer whenever the mod is off. In 46.9.42 telephony alone
is 26 call sites.

Everything else the mod does is the same rewrite pointed at something else:
TikTok's own models by their real names — `Video`, `Aweme`, `User`, `Music`,
`Comment`, `StreakData` — the framework's colour APIs, and a handful of methods
whose names survived obfuscation on classes whose names did not. Where only the
shape of a method is stable, the rule matches the shape and the receiver
arrives as an `Object`; where a class can only be recognised by something it
carries rather than by what it is called, the build finds it and writes the
name it found into the mod. A release that moves any of it leaves those rules
matching nothing, which is a feature that does not appear rather than an app
that breaks.

Nothing patches TikTok's `Application` class either. The mod needs a context to
read its settings and takes it from `ActivityThread.currentApplication()`, which
every Android process has and which no release can rename.
</details>

<details>
<summary><b>Signing in with Google still works</b></summary>

Resigning an apk changes its certificate, and Google Sign-In through Play
Services checks the package name against that certificate's SHA-1. For anything
built here that check can only fail: the package name is still TikTok's and the
certificate is not. Left alone the button leads to a developer error and no way
forward.

TikTok has a second way in, though, and it is the one it falls back to when
Play Services is not around: AppAuth, with a custom-scheme redirect —
`com.googleusercontent.apps.<client_id>` — and PKCE, in the browser. Google
does not check package or signature for that kind of client; it only checks
that whoever claims the scheme receives the redirect. So the build makes the
Play Services provider report itself unavailable, and the app takes its own
fallback:

```smali
# com/bytedance/lobby/google/GoogleAuth
.method public final isAvailable()Z
    .registers 1
    const/4 v0, 0x0
    return v0
.end method
```

which is enough, because the decision reads:

```java
provider = registry.get("google");
if (provider != null && provider.isAvailable()) return "google";
return "google_web";
```

The class name there is a real one rather than an obfuscated one, which is what
makes it safe to anchor on — and if a later release moves the method, the build
stops instead of quietly shipping a dead button.
</details>

## How the build works

The short version: **the apk is never taken apart.** No apktool, no aapt2,
nothing that rebuilds a resource table it did not write. The build edits the
bytes that have to change and copies everything else across exactly as it found
it.

| what | how |
|---|---|
| the zip | rewritten entry by entry, each one still compressed the way it arrived; `resources.arsc` stays stored and four-byte aligned |
| `AndroidManifest.xml` | parsed and rebuilt by `margyt/axml.py`, which round-trips aapt2's own output byte for byte |
| `resources.arsc` | read to find where things live, and written only in place: a colour repainted where it lies, never a byte moved |
| the icon | the files behind the existing resource are replaced, so no new id is ever needed |
| the accent | `margyt/palette.py` moves the whole family of reds onto the chosen colour, in the table and in every compiled xml that draws one |
| the theme | `margyt/nightly.py` reads the style table to find which colours TikTok itself repaints between light and dark, and writes that set into the mod |
| the dex | the files that hold something worth rewriting go through baksmali and smali; the rest are copied |
| the mod | javac and d8, in as the next `classesN.dex` — the run has to be unbroken or the runtime stops reading |
| the landing sites | every static a rewrite points at is checked against the mod's own dex before the apk is written: smali assembles a call to a method nobody wrote without a word, and the runtime only finds out on the screen that reaches it |

Five files in `inject/java/` are written by the build rather than by hand —
`Version`, `Baked`, `Nightly`, `Anchors`, `Emblem` and `Icons` — and each says
so at the top. They hold what only the apk in front of the build can know: what
it calls itself, what colour it was baked with, which colours are themed, and
where the methods the mod hands calls back to actually live.

The package name stays TikTok's. Renaming it is what made the earlier version of
this repository hard to trust: provider authorities collide with the official
app, the OAuth redirect scheme ends up claimed twice, and every
`com.zhiliaoapp.musically.something` string inside fifty-two dex files becomes
half true. The cost of keeping it is that the two cannot be installed side by
side — the signature differs, so Android will not put this one over the
official app, and the official app has to go first.

The one permission the build adds is `REQUEST_INSTALL_PACKAGES`, and only
because the mod offers its own updates.

## Building it yourself

You need a JDK (17 or newer) and Python 3. Nothing else: smali, d8, android.jar
and the signer are downloaded into `tools/` on the first run, each pinned to a
version. And a **universal** apk of TikTok — on APKMirror the variant of type
**APK**, not **BUNDLE**, `arm64-v8a`, `nodpi`. A split has no resource table of
its own to read.

```bash
git clone https://github.com/BottomSalamander/TokkiYou
cd TokkiYou
./build.sh path/to/tiktok.apk
```

The apk lands in `build/`. Reaching the colour where it actually lives means
opening most of the dex files rather than a handful, so the first build is
minutes of taking them apart and putting them back, several at a time and as
many as the machine has memory for.

The builds after it are shorter, because three things are kept in
`~/.cache/margyt-dex`: every dex taken apart, every patched dex under a name
made from what was rewritten in it, and which of those a given set of rules
led to. Change only the mod's Java and no dex is opened at all; change one
hook and every dex is rewritten again but only the ones that came out
different are reassembled. `MARGYT_NO_CACHE=1` ignores all of it,
`MARGYT_WORKERS` says how many dex files to do at once.

```
==> Opening the apk
    26151 entries
    package com.zhiliaoapp.musically, staying as it is
    minSdk 23, dex 035
==> The accent colour
    #FE2C55
==> Rewriting the bytecode
    38 of 52 dex files mention it
    classes22.dex: 21 (getSimCountryIso x1, getSimState x10, ...)
```

To move the brand colour everywhere, including the pictures the palette cannot
reach while the app runs:

```bash
./build.sh path/to/tiktok.apk --accent 8DD1B0
```

Everything the build produces is made for the apk's own minSdk, and it checks:
a dex assembled for a newer api is stamped with a newer format, and an Android
that does not know that format refuses the whole app rather than the one file.
TikTok's is dex 035, back to Android 6 — which is not where this would have been
noticed.

If a rewrite matches nothing where it must match something, the build stops
rather than handing you an apk that quietly does nothing.

## Tests

```bash
python3 -m unittest discover tests
```

No toolchain, no network, about a second, fifty-nine of them. They run against
`tests/data/fixture.apk` — seven kilobytes, built by aapt2 from `tests/fixture/`
and checked in — which has what the real apk has: a label from a string
resource, an adaptive icon whose layers are vectors, the icon at two densities,
a launcher entry that is an alias rather than an activity.

Several of them exist because they caught something real: a string-pool insert
that renumbered a manifest out from under an element being built, a dex
assembled in a format Android 6 would have refused, a scratch directory that
collided with the build's own output.

Rebuild the fixture with `tests/make_fixture.sh` if you change it; that is the
only thing here that wants aapt2.

## What this repository does not contain

- **TikTok's apk**, and nothing derived from it: not the resources, not the
  smali. It is someone else's proprietary code and it is not ours to
  redistribute. The build needs the apk; you bring your own.
- **A signing key.** uber-apk-signer uses its own debug one. Ship the result to
  anyone and they will have to uninstall before they can take an update signed
  by a different key.

## Files here

| | |
|---|---|
| `build.sh` | the whole thing, one command |
| `margyt/axml.py` | binary XML: parse, edit, write |
| `margyt/arsc.py` | the resource table, read and patched in place |
| `margyt/apkzip.py` | the zip, rewritten entry by entry |
| `margyt/dexpatch.py` | every rewrite, and what it is anchored on |
| `margyt/palette.py` | which colours the accent takes over, and what it turns them into |
| `margyt/nightly.py` | which colours TikTok itself repaints between light and dark |
| `margyt/icon.py` | the icon, replaced file by file |
| `margyt/png.py` | just enough PNG to resize an icon, so Pillow is not needed |
| `margyt/vector.py` | vector drawables, compiled without aapt2 |
| `margyt/plugin.py` | packs a folder into an `.mtp`, with the same pinned toolchain |
| `inject/java/` | the mod itself: its screen, and the methods the rewrites land in |
| `badges.json` | the badges, read from here by every install |
| `version.json` | what the latest release is, read from here by every install |
| `docs/plugins.md` | the plugin format, the hooks, and the honest part about safety |
| `examples/hello/` | a plugin small enough to read in a minute |
| `icons/` | the Material icons the settings screen draws, Apache 2.0 |
| `icon_out/` | the app icon: the 512px master the build scales from, and the density set |
| `tests/` | the fixture apk and what is asserted about it |

`margyt/dexpatch.py` is the file to read before moving the mod to a newer
TikTok. Everything else is either ours outright or finds its own targets; the
rewrites are the part that has to find them again in a rebuilt apk.

## Licence

GPL-3.0-or-later, with additional terms under its section 7 that are in
[ADDITIONAL-TERMS.md](ADDITIONAL-TERMS.md). The short version: fork it, change
it, ship it, but leave the screen that names the author and asks for support
where people can see it, and say that your build is yours rather than his.

Up to 0.23.2 this was MIT and those copies stay MIT.

TikTok's apk is not here and its terms are its own: this repository is patches,
not a redistribution.

The Material icons under `icons/` are Google's, Apache 2.0.
