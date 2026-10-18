package cat.narezany.tokkiyou;

import android.content.res.Resources;
import android.util.SparseArray;
import android.view.View;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a view is called, asked once.
 *
 * Several parts of the mod decide what a view is for by its resource name,
 * and asked the system for that name every time they looked at the view: on
 * every colour set, and for every view on the screen each time the screen was
 * walked. Most views have no name at all. Asking for the name of one of those
 * is not a quick "none" -- the system looks, fails, writes an error to its
 * log and throws -- and the mod was doing that nine hundred times a second
 * while a feed was simply sitting on the screen. It was most of the stutter.
 *
 * A name never changes while the app runs, so it is looked up once per id and
 * kept, and an id that cannot have a name is never asked about at all.
 */
final class Names {

    private Names() {}

    private static final SparseArray<String> names = new SparseArray<String>();
    private static final ConcurrentHashMap<Class<?>, String> types =
            new ConcurrentHashMap<Class<?>, String>();

    /** The view's resource name in lower case, or "" when it has none. */
    static String of(View view) {
        if (view == null) return "";
        int id = view.getId();
        // an app's own ids start 7f and the system's 01; anything else was
        // made up at run time, or is the "no id" that every plain view has
        int owner = id >>> 24;
        if (owner != 0x7f && owner != 0x01) return "";
        try {
            return of(view.getResources(), id);
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** The name of a resource in lower case, or "" when there is none to be had. */
    static String of(Resources resources, int id) {
        int owner = id >>> 24;
        if (resources == null || (owner != 0x7f && owner != 0x01)) return "";
        synchronized (names) {
            String known = names.get(id);
            if (known != null) return known;
        }
        String name;
        try {
            name = resources.getResourceEntryName(id).toLowerCase(Locale.ROOT);
        } catch (Throwable none) {
            name = "";
        }
        synchronized (names) {
            names.put(id, name);
        }
        return name;
    }

    /** The view's class name in lower case, worked out once for each class. */
    static String type(View view) {
        Class<?> type = view.getClass();
        String known = types.get(type);
        if (known == null) {
            known = type.getName().toLowerCase(Locale.ROOT);
            types.put(type, known);
        }
        return known;
    }
}
