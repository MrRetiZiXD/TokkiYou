# TokkiYou plugins

A plugin is code that runs inside TikTok, in the same process the app does,
loaded by the mod when the app starts.

The other plugin project uses Python. TokkiYou's plugins do not, and the reason
is repacking rather than taste. That project builds its own apk, so a Python
runtime goes in at build time. TokkiYou edits an apk somebody else built —
native libraries and its standard library into a 340 MB archive whose alignment
is not ours to decide is a different project with a doubtful ending.
`DexClassLoader` is an ordinary Android API that costs nothing and needs no
root, so the format here is the same shape as `.marp` — a manifest, an icon,
metadata, a list with switches — with a dex where the `main.py` would be.

## Honestly, about safety

**There is no sandbox, and there cannot be one.** A plugin runs with everything
TikTok has: its files, its session, its network, its permissions. The manifest
says who wrote it; nothing checks that this is true.

Install only what you have read yourself, or what comes from someone you trust.
This is written the same way in the settings screen, so nobody finds out later.

## The .mtp format

An ordinary zip renamed to `.mtp`:

```
tokkiyou.hello.mtp
├── manifest.json   required
├── classes.dex     required
└── icon.png        optional
```

Nested paths, duplicate entries, and files other than manifest.json, classes.dex, and icon.png are rejected. Limits: 16 MiB DEX, 64 KiB manifest, 1 MiB icon; limits count inflated bytes.
`icon.png` is what the plugin list draws; a square picture, 128×128 is plenty.
Without one the list draws a dot in the accent colour.

## manifest.json

```json
{
  "id": "tokkiyou.hello",
  "name": "Hello",
  "name_ru": "Привет",
  "version": "1.0",
  "author": "mrretizixd",
  "description": "Writes a line in the mod's diary every time TikTok starts.",
  "description_ru": "Пишет строку в журнал мода при каждом запуске TikTok.",
  "entry": "cat.narezany.hello.Hello",
  "min_api": 1
}
```

| Field | What it is |
|---|---|
| `id` | The plugin's name on disk and in the settings. Letters, digits, dots, dashes; anything else is replaced. Settings and updates are keyed by it — installing over the same id is how a plugin is updated, so do not change it between versions. Required. |
| `name` | The name in the list. Required. |
| `version` | A string, shown next to the name. Required. |
| `author` | Who wrote it. Required. |
| `entry` | The class that extends `TokkiYouPlugin`, in full. Required. |
| `description` | A line or two: what it does. |
| `min_api` | The oldest plugin api the plugin works with. The loader refuses a plugin that wants a newer one, with a reason rather than a crash. Defaults to 1. |
| `name_ru`, `description_uk`, … | The same field in another language. The app takes the one matching the phone's language and falls back to the plain field. |

## The plugin itself

Extend `TokkiYouPlugin` and override what you care about. Everything has a body
that does nothing, so a plugin that only wants one hook writes one method, and
hooks added in later versions of the mod do not break it.

```java
package cat.narezany.hello;

import android.content.Context;
import cat.narezany.tokkiyou.plugin.TokkiYouPlugin;

public final class Hello extends TokkiYouPlugin {
    @Override
    public void onStart(Context context) {
        tokkiyou().log("hello");
    }
}
```

The class needs a constructor that takes nothing — the default one will do —
and it must be `public`, because the loader reaches it by name.

### The hooks

| | When, and what it is for |
|---|---|
| `onStart(Context)` | The process has started. This runs in the mod's start-up provider, **before TikTok's own `Application.onCreate`** — early enough to get in front of most things, and early enough that most of the app does not exist yet. |
| `onActivityCreated(Activity)` | An activity was created. |
| `onActivityResumed(Activity)` | An activity came to the front. Called for every screen, so keep it cheap. |
| `onActivityPaused(Activity)` | An activity went away. |
| `int onColour(int)` | A colour is on its way to the screen — the constants the build redirected, and whatever came back from the framework, after the accent has had its say. Return the argument to leave it alone. **This is in the drawing path of half the app**: no allocation, no lookups, no logging. |
| `String onRegion(String key, String value)` | An answer about where the phone is, on its way back to TikTok. `key` is one of `sim_country`, `network_country`, `sim_operator`, `network_operator`, `sim_operator_name`, `network_operator_name`; `value` is what the mod was about to answer. Return it to leave it alone. |
| `onStop()` | The plugin was switched off. |

Hooks are called on whatever thread the app is on — the activity ones on the
main thread. Anything slow belongs on a thread of your own.

**A hook that throws is caught, written to the diary, and the plugin is dropped
for the rest of that process.** It does not take TikTok down, and it does not
throw the same exception on every frame either.

### What the mod hands you

`tokkiyou()` returns a `PluginContext`:

| | |
|---|---|
| `context()` | The application context. Never an activity, so it is safe to keep. |
| `id()` | Your id, as the manifest spells it. |
| `folder()` | Where your plugin was unpacked. Your own files are here; treat them as read-only. |
| `prefs()` | `SharedPreferences` of your own, in a file named after your id. Nothing else writes there. |
| `log(String)` | A line in the mod's diary, which the person can read and copy out of the settings screen. This is a plugin's way of saying anything at all: a TikTok repacked from a release has no log anyone is watching. |

### What is not there

There is no hooking of TikTok's own methods at runtime. Without root there is
no Xposed, and the app's own code is obfuscated and rewritten between releases;
the hooks above are the ones the mod can promise to still mean the same thing
next month. If you need one that is not here, the honest route is a patch to
`tokkiyou/dexpatch.py` that adds a call site, and a hook next to these — open an
issue rather than reaching in with reflection that will break.

## Building one

```bash
python3 -m tokkiyou.plugin examples/hello
```

That compiles `java/` against `android.jar` and the plugin api, dexes it, and
writes `tokkiyou.hello.mtp` next to the manifest. It needs a JDK and Python 3 and
nothing else: the toolchain is the same pinned one `build.sh` downloads into
`tools/`, so whichever you run first fetches it.

The api classes are compiled to build against and then **left out of the dex**
on purpose. They are already in the app, and a second copy under the same name
would be a different class as far as the runtime is concerned — the plugin
would be handed a `PluginContext` it did not recognise.

Your folder wants to look like this:

```
examples/hello/
├── manifest.json
├── icon.png                     optional
└── java/
    └── cat/narezany/hello/Hello.java
```

## Installing one

**Settings and privacy → TokkiYou → Plugins → Install a plugin**, and pick the
`.mtp`. It is unpacked into the app's own files, so no storage permission is
involved and the picker is the only way in.

A plugin arrives switched off. The switch starts it there and then; switching
it off stops the hooks at once, though the classes stay loaded until the app is
restarted, which is why the mod asks for one. Hold a row to remove a plugin.

## Versions

`TokkiYouPlugin.API` is the plugin api version, and it goes up whenever a hook
changes shape. A plugin declares the oldest it works with as `min_api`, and the
loader refuses anything newer than it knows rather than calling a hook that no
longer means what the plugin thought.

| api | TokkiYou | What changed |
|---|---|---|
| 1 | 0.4 | The first one: start, the three activity hooks, colour, region. |

## What a plugin is told (API 2)

Beyond starting and stopping, a plugin hears about what the app is doing.

```java
@Override
public void onScreen(Activity activity, String name) {}
```

A screen came up, by the class that draws it. The names are TikTok's own and
most are renamed every release, which is why the name is handed over rather
than an enum of screens this mod would have to keep in step with. The one that
is stable is `com.ss.android.ugc.aweme.main.MainActivity` -- the feed, the
inbox and the profile are all fragments inside it.

```java
@Override
public java.util.List onFeed(java.util.List posts) { return posts; }
```

A page of the feed, before anything has drawn it. Return it as it came to
leave it alone, or a list with things left out. The items are TikTok's own
`Aweme` objects; read them by reflection or by compiling against a stub of
your own.

```java
@Override
public String onName(String uid, String name) { return name; }
```

A name about to be written somewhere, with the account it belongs to. The
mod's own badges go on after this, so returning a bare name does not take
anybody's badge away.

```java
@Override
public CharSequence onText(CharSequence text) { return text; }
```

Text on its way into a view.

## Putting something on the screen

```java
tokkiyou().addSettingsRow("Title", "the line under it", () -> { ... });
tokkiyou().showWindow("Title", "A sentence.");
tokkiyou().offer("Do the thing", () -> { ... });
tokkiyou().progress("Working", 40);
tokkiyou().progressGone();
Activity now = tokkiyou().screen();
```

`addSettingsRow` puts a row in the mod's own settings under the plugin's
heading. `offer` is the floating button the mod uses for saving a sticker: it
sits over whatever screen is up and goes when that screen does. `showWindow`
is the mod's own dialog, drawn from the colours measured off TikTok's settings
screen, so it matches whatever theme is on.

**A plugin cannot declare an Android component of its own.** Activities,
services and receivers are read out of the manifest when the app is installed,
and a plugin arrives long after that. What it can do instead is everything
short of one: its own views on an existing screen, the mod's own window, and
an ordinary `Intent` if it really does want a screen from another app.

## Off the main thread

```java
tokkiyou().away("fetching", () -> {
    byte[] raw = tokkiyou().fetch("https://example.invalid/thing.json");
});
```

`away` names the work so the diary can say whose it was when it goes wrong.
`fetch` answers null rather than throwing.

## Opening settings

Tapping an installed plugin opens its registered settings rows. API 3 remains backward compatible and now offers optional `boolean onOpenSettings(Activity activity)`: return true only after opening your window. Legacy public `openSettings`, `showSettings`, `showSettingsDialog` accepting Activity or Context are supported when they return void or boolean. A plugin with no rows or settings entry shows a short explanation; the host cannot invent controls for it.


## API 4

Additive API; old plugins do not need recompilation. Use `min_api: 4` if your plugin requires these helpers.

| Helper / hook | Behavior |
|---|---|
| `onAccountChanged(String uid)` | Active account changed; null means signed out. Re-read account preferences rather than keeping the previous account's values. |
| `onSettingsChanged(String key)` | A registered plugin setting was persisted by the mod menu. |
| `accountId()` / `accountPrefs()` | Active UID and private per-account preferences; signed-out preferences use a separate guest store. `prefs()` remains device-wide. |
| `accentColour()` | Current account's resolved accent. |
| `activity()` / `screen()` | Current Activity, or null. Do not retain it. |
| `onUiThread(Runnable)` | Post UI work to the main thread. |
| `copyText(String)` | Copy plain text on the main thread. |
| `download(url, name, Downloaded)` | Download outside the Activity into the plugin's writable files directory. Filename: 1–80 ASCII letters/digits/dot/underscore/dash, excluding `.` and `..`. Callback runs on the main thread with File and success flag. Concurrent duplicate filenames throw IllegalStateException. |
| `showWindow(title, message)` | Open the mod window in the current Activity on the main thread. |
| `progress(what, percent)` / `progressGone()` | Plugin-specific job; dismissing it does not dismiss another plugin's progress. |

```java
@Override public void onAccountChanged(String uid) {
    boolean enabled = tokkiyou().accountPrefs().getBoolean("enabled", false);
    tokkiyou().log("account preferences loaded: " + enabled);
}
```

Registered setting rows still use `prefs()` for backward compatibility. A plugin may use `accountPrefs()` for its own account-specific interface and state.
