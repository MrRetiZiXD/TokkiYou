package cat.narezany.margyt;

import android.content.SharedPreferences;

import java.lang.reflect.Method;

/**
 * The scrubbing bar, on every video rather than some.
 *
 * TikTok decides per video whether to draw one, and passes that decision to a
 * view as a number: `setSeekBarShowType`. The method's name is real; the class
 * that has it is not, and will be spelled differently next release. So the
 * rewrite matches the method wherever it is -- any owner, that name, that
 * shape -- and the receiver arrives here as a plain Object.
 *
 * Which means the call back has to go through reflection, and that is fine:
 * this runs when a video is set up, not when one is drawn.
 */
public final class Seekbar {

    private Seekbar() {}

    public static final String KEY = "seekbar_always";

    /** The value that means "show it". The others hide or defer it. */
    private static final int ALWAYS = 0;

    private static volatile Boolean cached;
    private static volatile Method original;
    private static final java.util.Map<Object,Integer> requested = new java.util.WeakHashMap<Object,Integer>();

    static void reloadSettings(){cached=null;setEnabled(isEnabled());}
    public static boolean isEnabled() {
        Boolean known = cached;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return false;
        boolean on = false;
        try {
            on = prefs.getBoolean(KEY, false);
        } catch (Throwable ignored) {
        }
        cached = on;
        return on;
    }

    public static void setEnabled(boolean enabled) {
        cached = enabled;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY, enabled).apply();
        java.util.List<java.util.Map.Entry<Object,Integer>> views=new java.util.ArrayList<>();
        synchronized(requested) {
            for (java.util.Map.Entry<Object,Integer> entry:requested.entrySet())
                views.add(new java.util.AbstractMap.SimpleImmutableEntry<Object,Integer>(entry));
        }
        for (java.util.Map.Entry<Object,Integer> entry:views) {
            Runnable update=() -> invoke(entry.getKey(),isEnabled()?ALWAYS:entry.getValue());
            if (entry.getKey() instanceof android.view.View) ((android.view.View)entry.getKey()).post(update);
            else new android.os.Handler(android.os.Looper.getMainLooper()).post(update);
        }
    }

    private static SharedPreferences prefs() {
        android.content.Context context = Margy.context();
        if (context == null) return null;
        return context.getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE);
    }

    // ------------------------------------------------- where the call lands

    public static void setSeekBarShowType(Object view, int type) {
        if (view == null) return;
        synchronized(requested) { requested.put(view,type); }
        invoke(view,isEnabled()?ALWAYS:type);
    }

    private static void invoke(Object view,int type) {
        try {
            Method setter = original;
            if (setter == null || !setter.getDeclaringClass().isInstance(view)) {
                setter = view.getClass().getMethod("setSeekBarShowType", int.class);
                setter.setAccessible(true);
                original = setter;
            }
            setter.invoke(view, Integer.valueOf(type));
        } catch (Throwable error) {
            // a release that moved the method is a release without this
            // feature, not one where the video fails to open
            Diary.note("seekbar: " + error);
        }
    }
}
