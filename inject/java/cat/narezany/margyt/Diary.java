package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * What the mod saw, kept so it can be read off the phone.
 *
 * A mod cannot be attached to a debugger and nobody is going to run logcat
 * against a modded TikTok. So the few things worth knowing -- did the start-up
 * hook run, which screen was that, what was its view made of, did the row go in
 * -- are written down here and shown at the bottom of TokkiYou's own settings.
 *
 * It survives the app being killed, which is the whole point: reaching TokkiYou's
 * settings can mean leaving TikTok first, and a diary that lives only in memory
 * is empty by the time anyone reads it.
 */
public final class Diary {

    private Diary() {}

    private static final String KEY = "diary";
    /**
     * A file of its own. It used to be one more entry among the settings,
     * and saving a settings file means writing all of it: every few seconds
     * that something was noted, every setting on the phone was written out
     * again, and Android makes the screen wait for those writes whenever an
     * activity is left. On a phone whose settings had grown that was a
     * stall, again and again, that nothing short of clearing the app's data
     * made better.
     *
     * Not named after the mod's settings files: the settings screen watches
     * those to say whether a restart is owed, and this one changes by itself.
     */
    private static final String FILE = "diary_margyt";
    private static final int KEEP = 40;
    private static final int KEEP_DEV = 400;

    private static final List<String> LINES = new ArrayList<String>();
    private static boolean loaded;

    /**
     * Saving is held back, because writing is the expensive half.
     *
     * A note is cheap and there are a hundred places that write one. Rebuilding
     * the whole diary and handing it to the preferences on each of those is
     * work in the middle of whatever the app was doing.
     */
    private static boolean dirty;
    private static long savedAt;
    private static final long REST = 3000;

    /** One formatter, because making one costs more than using it. */
    private static final SimpleDateFormat CLOCK =
            new SimpleDateFormat("HH:mm:ss", Locale.US);

    public static void note(String line) {
        if (flooding(line)) return;
        if (Version.TEST || Version.LITE) android.util.Log.i("TokkiYou", "diary: " + line);
        String stamped = stamp() + "  " + line;
        synchronized (LINES) {
            load();
            String last = LINES.isEmpty() ? "" : LINES.get(LINES.size() - 1);
            if (last.length() > 10 && last.substring(10).equals(stamped.substring(10))) return;
            LINES.add(stamped);
            int keep = DevMode.isOn() ? KEEP_DEV : KEEP;
            while (LINES.size() > keep) LINES.remove(0);
            dirty = true;
            long now = android.os.SystemClock.uptimeMillis();
            if (now - savedAt >= REST) {
                savedAt = now;
                save();
            }
        }
    }

    private static final java.util.HashMap<String, long[]> SAID = new java.util.HashMap<String, long[]>();

    /**
     * Whether a line has been said enough for now. Something that fails does
     * so every time it is tried, and what is tried on every video or every
     * frame would otherwise fill the diary with one line and spend the app's
     * time writing it: six times a minute is as often as any one line is
     * written. Numbers are left out of the comparison, so "took 812 ms" and
     * "took 790 ms" are the same line.
     */
    private static boolean flooding(String line) {
        if (line == null) return true;
        String kind = line.length() > 80 ? line.substring(0, 80) : line;
        kind = kind.replaceAll("[0-9]+", "#");
        long now = android.os.SystemClock.uptimeMillis();
        synchronized (SAID) {
            long[] count = SAID.get(kind);
            if (count == null) {
                if (SAID.size() > 300) SAID.clear();
                SAID.put(kind, new long[] {now, 1});
                return false;
            }
            if (now - count[0] > 60000L) {
                count[0] = now;
                count[1] = 1;
                return false;
            }
            return ++count[1] > 6;
        }
    }

    /** A line only a developer wants. Written when the developer switch is on. */
    public static void detail(String line) {
        if (DevMode.isOn()) note("· " + line);
    }

    public static List<String> lines() {
        synchronized (LINES) {
            load();
            if (dirty) save();
            return new ArrayList<String>(LINES);
        }
    }

    public static void clear() {
        synchronized (LINES) {
            LINES.clear();
            loaded = true;
            save();
        }
    }

    // ------------------------------------------------------------- storage

    private static void load() {
        if (loaded) return;
        SharedPreferences prefs = prefs();
        if (prefs == null) return;  // no context yet: try again next time
        loaded = true;
        String stored = prefs.getString(KEY, "");
        if (stored.length() == 0) {
            // what an older version kept among the settings: brought over
            // once, and taken out of there so it stops being written with them
            try {
                SharedPreferences settings = Margy.context()
                        .getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
                if (settings.contains(KEY)) {
                    stored = settings.getString(KEY, "");
                    settings.edit().remove(KEY).apply();
                }
            } catch (Throwable ignored) { }
        }
        if (stored.length() > 0) LINES.addAll(Arrays.asList(stored.split("\n")));
    }

    private static void save() {
        dirty = false;
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        StringBuilder out = new StringBuilder();
        for (String line : LINES) {
            if (out.length() > 0) out.append('\n');
            out.append(line);
        }
        prefs.edit().putString(KEY, out.toString()).apply();
    }

    private static SharedPreferences prefs() {
        try {
            Context context = Margy.context();
            if (context == null) return null;
            return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String stamp() {
        synchronized (CLOCK) {
            return CLOCK.format(new Date());
        }
    }
}
