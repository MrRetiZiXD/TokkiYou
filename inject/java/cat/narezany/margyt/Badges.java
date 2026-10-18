package cat.narezany.margyt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The badges, and where they come from.
 *
 * They used to be one account and one picture, written into the code, which
 * meant a new badge was a new build of the mod for everybody. Now they are a
 * file in the repository -- `badges.json` -- read when the app starts and
 * again every five minutes. Adding a badge is editing that file.
 *
 * What arrives is kept on disk as well, so the badge is there on the next
 * start before the network has answered, and stays there if the server is not
 * reachable at all. Nothing waits on the network: the first draw uses whatever
 * is already known, and a refresh that finds something new simply applies from
 * then on.
 *
 * Pictures named by a badge are fetched the same way and cached beside the
 * file, by a name made from the path, so the same picture is never fetched
 * twice.
 */
public final class Badges {

    private Badges() {}

    /**
     * Where the badges live now.
     *
     * They used to be a file in the repository, which worked and cost nothing
     * and had two limits worth leaving it for: granting one meant a commit,
     * and nobody could decide anything about their own. So there is a small
     * service instead, and it answers with the same shape the file had.
     */
    public static final String SERVER = "https://margy.narez.xyz/api";

    private static final String SOURCE = SERVER + "/badges";
    private static final String FILES = SERVER + "/icon/";

    private static final long EVERY = 2 * 60 * 1000L;

    /** One badge, as the file describes it. */
    public static final class Badge {
        public final String id;
        public final String image;
        public final int colour;
        public final String title;
        public final String text;
        public final String button;

        Badge(String id, String image, int colour, String title, String text, String button) {
            this.id = id;
            this.image = image;
            this.colour = colour;
            this.title = title;
            this.text = text;
            this.button = button;
        }
    }

    private static final Badge TOKKIYOU_BADGE = new Badge(
            Mine.TOKKIYOU_BADGE, "", 0xFF5E2BCE,
            Text.MY_BADGE_TITLE, Text.MY_BADGE_TEXT, "");

public static final String KEY = "badges_on";

    private static volatile Boolean cached;

    public static boolean isEnabled() {
        Boolean known = cached;
        if (known != null) return known;
        try {
            android.content.Context context = Margy.context();
            if (context == null) return true;  // on until there is somewhere to read from
            boolean on = context.getSharedPreferences(Margy.PREFS,
                    android.content.Context.MODE_PRIVATE).getBoolean(KEY, true);
            cached = on;
            return on;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setEnabled(boolean enabled) {
        cached = enabled;
        try {
            android.content.Context context = Margy.context();
            if (context == null) return;
            context.getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY, enabled).apply();
        } catch (Throwable ignored) {
        }
    }

    /**
     * uid -> every badge that account has, in the order the file lists them.
     * Replaced wholesale on a refresh, never edited in place.
     */
    private static volatile Map<String, Badge[]> known = new HashMap<String, Badge[]>();

    /**
     * The same badges by number, because a name has to carry which one it has.
     *
     * The mark left on a name is a single character, and there is nothing else
     * about it that says whose badge it is -- the view that draws it never sees
     * the account. So the character *is* the number: the first badge is
     * U+E000, the second U+E001, and the list here says which is which. They
     * are private-use codepoints, so nothing else can collide with them, and
     * the numbering only has to hold for as long as the app is running.
     */
    private static volatile Badge[] numbered = {TOKKIYOU_BADGE};

    /**
     * Where the marks sit in the private-use area -- and it moves every start.
     *
     * A mark used to be U+E000 plus the badge's number, the same on every
     * phone and every run. Which meant anybody could put that character in
     * their own name and wear somebody else's badge: the drawing code sees a
     * character, not an account.
     *
     * Two things fix that, and this is the second one. The first is that a
     * name is stripped of every private-use character before the mod adds its
     * own, so nothing typed into a name survives. This covers everywhere else
     * -- a comment, a bio, anything that is not a name: the run picks a random
     * place in the private-use area, so a character copied out of somebody's
     * screenshot is not a mark on anybody else's phone, and not on the same
     * phone tomorrow.
     */
    private static final char FIRST = pick();

    private static final int MOST = 65;

    private static char pick() {
        // U+E000..U+F8FF is the private-use area. Room for the badges and for
        // the gradients, which take the block straight after.
        int room = 0xF8FF - 0xE000 - MOST - Looks.MOST;
        return (char) (0xE000 + (int) (Math.random() * room));
    }

    /** Where the gradient marks begin, next door to the badges. */
    static char looksFirst() {
        return (char) (FIRST + MOST);
    }

    /** Anything in the private-use area, ours or not. Names are cleared of it. */
    public static boolean isPrivate(char c) {
        return c >= '\uE000' && c <= '\uF8FF';
    }

    private static volatile boolean started;

    /** The mod's own note, for anything that needs a picture and has none. */
    public static Bitmap note() {
        Bitmap known = ownNote;
        if (known != null) return known;
        try {
            byte[] png = android.util.Base64.decode(Emblem.PNG, android.util.Base64.DEFAULT);
            ownNote = BitmapFactory.decodeByteArray(png, 0, png.length);
        } catch (Throwable error) {
            Diary.note("badge: " + error);
        }
        return ownNote;
    }

    private static volatile Bitmap ownNote;

    /** One badge by the name the server gives it. */
    public static Badge byId(String id) {
        for (Badge badge : numbered) {
            if (badge != null && badge.id.equals(id)) return badge;
        }
        return null;
    }

    public static Badge[] of(String uid) {
        if (uid == null) return null;
        return known.get(uid);
    }

    /**
     * The characters that stand for this account's badges, in order.
     *
     * An account can hold several -- one for supporting the mod, one for
     * drawing an icon it ships with -- and each is its own character, so each
     * becomes its own picture and answers its own tap.
     */
    public static String marksFor(String uid) {
        if (!isEnabled()) return "";
        Badge[] held = of(uid);
        if (held == null || held.length == 0) return "";
        Badge[] list = numbered;
        StringBuilder out = new StringBuilder(held.length);
        for (Badge badge : held) {
            for (int i = 0; i < list.length; i++) {
                if (list[i] == badge) {
                    out.append((char) (FIRST + i));
                    break;
                }
            }
        }
        return out.toString();
    }

    /** Whether a character is one of ours, without looking anything up. */
    public static boolean isMark(char c) {
        return c >= FIRST && c < FIRST + MOST;
    }

    public static Badge byMark(char c) {
        Badge[] list = numbered;
        int at = c - FIRST;
        return at >= 0 && at < list.length ? list[at] : null;
    }

    // ------------------------------------------------------------- keeping up

    /**
     * Read what is on disk, then ask the server -- now and every five minutes.
     *
     * Called from the mod's start-up hook, so the first read happens before
     * TikTok has drawn anything.
     */
    public static synchronized void start(Context context) {
        if (started) return;
        started = true;

        byte[] cached = Net.read(file(context));
        if (cached != null) {
            apply(cached, false);
            prefetch(context);
        }

        final Handler handler = new Handler(Looper.getMainLooper());
        final Context application = context.getApplicationContext();
        handler.post(new Runnable() {
            @Override
            public void run() {
                refresh(application);
                handler.postDelayed(this, EVERY);
            }
        });
    }

    /** What the server said last time, so it need not say it again. */
    private static volatile String tag;

    /** Ask the server again now, after something of ours changed there. */
    static void refresh() {
        Context context = Margy.context();
        if (context != null) refresh(context);
    }

    private static void refresh(final Context context) {
        Net.away("badges", new Runnable() {
            @Override
            public void run() {
                // The server answers 304 when nothing has changed, which is
                // most of the time -- so asking every two minutes costs a few
                // hundred bytes rather than the whole list.
                Net.Answer said = Net.fetch(SOURCE, tag);
                if (said == null || said.unchanged) return;
                tag = said.tag;
                if (said.body == null) return;
                if (apply(said.body, true)) {
                    Net.save(file(context), said.body);
                    prefetch(context);
                    Diary.note("badges: " + known.size() + " accounts, "
                            + numbered.length + " badges");
                    told();
                }
            }
        });
    }

    /**
     * Somebody waiting to hear that the list has arrived.
     *
     * The settings screen, and nothing else: it is built from what is known at
     * the moment it opens, and on the first run after an update what is known
     * is a file cached before any of this existed. Without this the free badge
     * would be offered a minute after the screen it is offered on was drawn.
     */
    private static volatile Runnable waiting;

    public static void tell(Runnable then) {
        waiting = then;
    }

    /** The screen that asked to be told is going away: stop holding on to it. */
    public static void stopTelling(Runnable then) {
        if (waiting == then) waiting = null;
    }

    private static void told() {
        final Runnable then = waiting;
        if (then == null) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(then);
    }

    /** When the free badge stops being given out, as the server reckons it. */
    private static volatile long freeUntil;
    private static volatile long serverNow;
    private static volatile long readAt;

    /** Whether the offer is still open, by the server's clock and not ours. */
    public static boolean freeStillOpen() {
        if (serverNow <= 0) return false;
        long since = (android.os.SystemClock.elapsedRealtime() - readAt) / 1000;
        return serverNow + since < freeUntil;
    }

    /**
     * Fetch every badge's picture now rather than when a name needs it.
     *
     * A picture asked for while a name is being drawn cannot be waited on --
     * the badge falls back to the mod's own note and only becomes itself the
     * next time that view is drawn, which is why badges used to appear a beat
     * late or not at all on a profile. There are a handful of pictures and
     * they are cached on disk, so fetching them all at the start costs one
     * round trip on the first run and nothing afterwards.
     */
    private static void prefetch(Context context) {
        try {
            for (Badge badge : numbered) {
                if (badge != null && badge.image.length() > 0) {
                    picture(context, badge.image);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Read a list of badges. `live` says whether it just came from the server.
     *
     * The server's own clock only counts when it is fresh: taken off a file
     * written days ago it said the free badge was still being given out long
     * after it was not.
     */
    private static boolean apply(byte[] json, boolean live) {
        try {
            JSONObject root = new JSONObject(new String(json, "UTF-8"));
            JSONArray list = root.optJSONArray("badges");
            if (list == null) return false;
            long until = root.optLong("free_until", 0);
            long now = root.optLong("now", 0);
            if (live && until > 0 && now > 0) {
                freeUntil = until;
                serverNow = now;
                readAt = android.os.SystemClock.elapsedRealtime();
            }

            Looks.learn(root);

            Map<String, java.util.List<Badge>> built =
                    new HashMap<String, java.util.List<Badge>>();
            java.util.LinkedHashMap<Badge, Boolean> distinct =
                    new java.util.LinkedHashMap<Badge, Boolean>();
            distinct.put(TOKKIYOU_BADGE, Boolean.TRUE);
            for (int i = 0; i < list.length(); i++) {
                JSONObject one = list.optJSONObject(i);
                if (one == null) continue;
                String id = one.optString("id", "badge" + i);
                Badge badge = Mine.TOKKIYOU_BADGE.equals(id) ? TOKKIYOU_BADGE : new Badge(
                        id,
                        one.optString("image", ""),
                        colour(one.optString("colour", "")),
                        localised(one, "title"),
                        localised(one, "text"),
                        localised(one, "button"));
                JSONArray users = one.optJSONArray("users");
                if (users == null) continue;
                distinct.put(badge, Boolean.TRUE);
                for (int u = 0; u < users.length(); u++) {
                    String uid = users.optString(u, "");
                    if (uid.length() == 0) continue;
                    java.util.List<Badge> theirs = built.get(uid);
                    if (theirs == null) {
                        theirs = new java.util.ArrayList<Badge>(2);
                        built.put(uid, theirs);
                    }
                    if (!theirs.contains(badge)) theirs.add(badge);
                }
            }
            Badge[] order = new Badge[Math.min(distinct.size(), MOST)];
            int at = 0;
            for (Badge badge : distinct.keySet()) {
                if (at == order.length) break;
                order[at++] = badge;
            }

            Map<String, Badge[]> settled = new HashMap<String, Badge[]>(built.size());
            for (Map.Entry<String, java.util.List<Badge>> entry : built.entrySet()) {
                settled.put(entry.getKey(), entry.getValue().toArray(new Badge[0]));
            }

            numbered = order;
            known = settled;
            return true;
        } catch (Throwable error) {
            Diary.note("badges: unreadable, keeping the last ones -- " + error);
            return false;
        }
    }

    /** `text_ru` before `text`, so a badge can speak the phone's language. */
    private static String localised(JSONObject one, String field) {
        String language = Locale.getDefault().getLanguage();
        String translated = one.optString(field + "_" + language, "");
        if (translated.length() > 0) return translated;
        return one.optString(field, "");
    }

    private static int colour(String hex) {
        try {
            if (hex.length() == 0) return 0;
            return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), "margyt/badges.json");
    }

    // -------------------------------------------------------- their pictures

    private static final Map<String, Bitmap> pictures = new HashMap<String, Bitmap>();

    /** When each picture was last asked for, so a failure is not forever. */
    private static final Map<String, Long> asked = new HashMap<String, Long>();

    /** How long a picture that did not arrive is left alone. */
    private static final long AGAIN = 60000;

    /**
     * The picture a badge names, or null for the mod's own note.
     *
     * Answers from memory or from the cache on disk, and never waits: a
     * picture that is not here yet is fetched on a thread, and the badge draws
     * the note until the next time it is drawn.
     *
     * A fetch that fails is tried again a minute later rather than never: the
     * marker used to say "asked for" and nothing ever took it off, so one bad
     * minute of network left a badge as a note until the app was restarted.
     */
    public static Bitmap picture(final Context context, final String path) {
        if (path == null || path.length() == 0) return null;
        long now = android.os.SystemClock.elapsedRealtime();
        synchronized (pictures) {
            Bitmap known = pictures.get(path);
            if (known != null) return known;
            Long when = asked.get(path);
            if (when != null && now - when.longValue() < AGAIN) return null;
            asked.put(path, Long.valueOf(now));
        }

        final File cache = new File(context.getFilesDir(), "margyt/badges/" + name(path));
        byte[] have = Net.read(cache);
        if (have != null) {
            Bitmap drawn = remember(path, have);
            if (drawn != null) return drawn;
            // a cached file that will not decode is not a picture, and keeping
            // it meant the real one was never fetched again
            cache.delete();
        }

        Net.away("badge picture", new Runnable() {
            @Override
            public void run() {
                byte[] raw = Net.bytes(FILES + path);
                if (raw == null) return;
                Net.save(cache, raw);
                remember(path, raw);
            }
        });
        return null;
    }

    private static Bitmap remember(String path, byte[] raw) {
        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length);
            if (bitmap == null) return null;
            // trimmed the way the mod's own note is trimmed. A badge is drawn
            // into a box the height of the line it sits in, so a picture with
            // empty margin around it comes out smaller than one without --
            // which is why the server's badges looked shrunken beside it.
            // spelled out, because `Badge` inside this class means the one
            // nested in it rather than the class that draws them
            bitmap = cat.narezany.margyt.Badge.crop(bitmap);
            synchronized (pictures) {
                pictures.put(path, bitmap);
            }
            return bitmap;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** A file name that is a path's, without being a path. */
    private static String name(String path) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            out.append(Character.isLetterOrDigit(c) || c == '.' ? c : '_');
        }
        String cleaned = out.toString();
        // "." and ".." are not names, they are places
        return cleaned.replace("..", "__");
    }
}
