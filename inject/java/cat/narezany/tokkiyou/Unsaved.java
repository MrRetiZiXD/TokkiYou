package cat.narezany.tokkiyou;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Whether anything is still different from how it was when TikTok started.
 *
 * "Restart to apply" used to be a one-way door: change something, change it
 * back, and the bar stayed, asking for a restart that would change nothing.
 * What it should say is whether the settings as they stand are the settings
 * the running app was started with -- so those are written down the first
 * time this screen opens, and looked at again after every change.
 *
 * Only what a change touched is compared. The same files hold things that are
 * not settings at all -- a count of visits, what the screen was last measured
 * to look like -- and those move on their own; a key is taken to be a setting
 * when it moved between a finger going down and the change it caused.
 *
 * Two things cannot be read back out of the preferences, and both are settled
 * on the side of asking for the restart. A change that moved nothing here was
 * kept somewhere else -- a font file, a plugin -- and nothing here can tell
 * whether it was undone. And a key that was not there at the start has no
 * value to go back to; for a switch the first value written is the opposite
 * of what it showed, so that much is worked out, and for anything else it is
 * not guessed at.
 */
final class Unsaved {

    private Unsaved() {}

    private static Map<String, Object> start;
    private static Map<String, Object> before;
    private static final Set<String> touched = new HashSet<String>();
    private static final Map<String, Object> implied = new HashMap<String, Object>();
    private static boolean elsewhere;

    /** The settings as they are now, before whatever is about to change them. */
    static void look(Context context) {
        try {
            Map<String, Object> now = read(context);
            if (start == null) start = now;
            before = now;
        } catch (Throwable error) {
            Diary.note("unsaved: " + error);
        }
    }

    /** Something has just been changed. True while a restart is still owed. */
    static boolean after(Context context) {
        try {
            Map<String, Object> now = read(context);
            if (start == null || before == null) {
                // nothing to compare with, so there is no saying it was undone
                if (start == null) start = now;
                before = now;
                elsewhere = true;
                return true;
            }
            boolean moved = false;
            Set<String> keys = new HashSet<String>(now.keySet());
            keys.addAll(before.keySet());
            for (String key : keys) {
                Object is = now.get(key);
                if (same(is, before.get(key))) continue;
                moved = true;
                touched.add(key);
                if (!start.containsKey(key) && !implied.containsKey(key)
                        && is instanceof Boolean) {
                    implied.put(key, Boolean.valueOf(!((Boolean) is).booleanValue()));
                }
            }
            before = now;
            if (!moved) elsewhere = true;
            if (elsewhere) return true;
            for (String key : touched) {
                Object was = start.containsKey(key) ? start.get(key) : implied.get(key);
                if (!same(was, now.get(key))) return true;
            }
            return false;
        } catch (Throwable error) {
            Diary.note("unsaved: " + error);
            return true;
        }
    }

    /** A change that is not written in any preference, and so cannot be seen to be undone. */
    static void other() {
        elsewhere = true;
    }

    private static boolean same(Object one, Object two) {
        return one == null ? two == null : one.equals(two);
    }

    /** Every preference the mod keeps, under the name of the file it is in. */
    private static Map<String, Object> read(Context context) {
        Set<String> files = new HashSet<String>();
        files.add(TokkiYou.PREFS);
        files.add("tokkiyou_store");
        try {
            String[] names = new File(context.getApplicationInfo().dataDir,
                    "shared_prefs").list();
            if (names != null) {
                for (String name : names) {
                    if (name.startsWith(TokkiYou.PREFS) && name.endsWith(".xml")) {
                        files.add(name.substring(0, name.length() - 4));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        Map<String, Object> all = new HashMap<String, Object>();
        for (String file : files) {
            SharedPreferences prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE);
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                all.put(file + "/" + entry.getKey(), entry.getValue());
            }
        }
        // the look of this account is in a file named for it, which exists
        // only once something has been written there
        try {
            SharedPreferences own = AccountAppearance.prefs(context);
            if (own != null) {
                for (Map.Entry<String, ?> entry : own.getAll().entrySet()) {
                    all.put("appearance:" + AccountAppearance.scope(context) + "/"
                            + entry.getKey(), entry.getValue());
                }
            }
        } catch (Throwable ignored) {
        }
        return all;
    }
}
