package cat.narezany.tokkiyou;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/**
 * The TokkiYou row, as a row of TikTok's settings like any other.
 *
 * TikTok's settings screen is drawn by Compose, a row a function, and there
 * is no list in it to add a view to; so the row used to sit above the whole
 * screen, over the title. This puts it in the list instead, at the head of
 * the first group, drawn by the same function TikTok draws its own rows
 * with.
 *
 * The build does two things to TikTok's code for it. The function that draws
 * the "Display" row is copied under another name, and the copy takes its
 * title from here in place of TikTok's string, and the icon of the music
 * row. And where the first group draws its first row, it calls `draw` just
 * before, which draws the copy. What the copy is given to draw is a state of
 * the same kind the Display row has, made here, whose one live part is what
 * happens on a tap -- and that opens TokkiYou's settings.
 *
 * Everything that can fail does so before the row is drawn, and then the
 * old row above the screen is used as it always was. A failure inside
 * TikTok's drawing cannot be caught, so every first drawing is counted
 * before it is tried and uncounted once the screen has stood for a second:
 * a count that was never taken off means the last try took the app down,
 * and it is not tried again.
 */
public final class SettingsCell {

    private SettingsCell() {}

    private static final String CELLS = "X.0MUp", STATE = "X.0MUl", COPY = "tokkiyou";
    private static final String TRIED = "settings_cell_tried";

    private static volatile Object state;
    private static volatile Method cell;
    private static volatile Object unit;
    private static volatile boolean broken, drawn, counted;

    /** Whether the row is in TikTok's list, and so not wanted above the screen as well. */
    static boolean drawn() {
        return drawn;
    }

    /** A settings screen has just been made: nothing has been drawn on it yet. */
    static void newScreen() {
        drawn = false;
    }

    /** Whether the row can be expected in TikTok's list: the build put the copy in and nothing has failed. */
    static boolean likely() {
        if (broken) return false;
        try {
            cell();
            return true;
        } catch (Throwable missing) {
            return false;
        }
    }

    /** What the row says. Asked for by the copy of TikTok's function. */
    public static String title() {
        return Text.ROW;
    }

    /**
     * Called from TikTok's code as it is about to draw the first row of the
     * first group, with whether that row is the first of its group and what
     * it draws with. Draws the TokkiYou row there, and says whether the row
     * after it is still the first: it is not, once this one has been drawn.
     */
    public static boolean draw(boolean first, Object composer) {
        if (broken || composer == null) return first;
        try {
            if (!counted) {
                counted = true;
                if (!mayTry()) {
                    broken = true;
                    return first;
                }
            }
            Object given = state();
            Method copy = cell();
            copy.invoke(null, given, first, false, composer, 0);
            if (!drawn) {
                drawn = true;
                Diary.note("settings row: in TikTok's own list");
            }
            return false;
        } catch (InvocationTargetException inside) {
            // TikTok's drawing has stopped half way; nothing here can put it right
            throw new RuntimeException(inside.getCause());
        } catch (Throwable error) {
            broken = true;
            Diary.note("settings row: stays above the screen: " + error);
            return first;
        }
    }

    /**
     * What Compose calls to draw the row again when something it showed has
     * changed. TikTok's own is a class that draws the Display row; this one
     * draws the copy.
     */
    public static Object again(final Object given, final boolean first, final boolean last, final int changed) {
        try {
            Class<?> kind = Class.forName("kotlin.jvm.functions.Function2");
            return Proxy.newProxyInstance(kind.getClassLoader(), new Class<?>[] {kind}, (proxy, method, args) -> {
                String name = method.getName();
                if (name.equals("invoke") && args != null && args.length == 2) {
                    try {
                        cell().invoke(null, given, first, last, args[0], changed | 1);
                    } catch (InvocationTargetException inside) {
                        throw inside.getCause();
                    }
                    return unit();
                }
                return plain(proxy, name, args);
            });
        } catch (Throwable error) {
            Diary.note("settings row: " + error);
            return null;
        }
    }

    private static Object plain(Object proxy, String name, Object[] args) {
        if (name.equals("hashCode")) return System.identityHashCode(proxy);
        if (name.equals("equals")) return args != null && args.length == 1 && proxy == args[0];
        if (name.equals("toString")) return "tokkiyou-settings-row";
        return null;
    }

    private static Method cell() throws Exception {
        Method known = cell;
        if (known != null) return known;
        for (Method method : Class.forName(CELLS).getDeclaredMethods()) {
            if (method.getName().equals(COPY) && Modifier.isStatic(method.getModifiers())) {
                method.setAccessible(true);
                cell = method;
                return method;
            }
        }
        throw new NoSuchMethodException("this build has no copy of the row to draw");
    }

    /** A state like the Display row's, with nothing in it but what a tap does. */
    private static Object state() throws Exception {
        Object known = state;
        if (known != null) return known;
        Class<?> type = Class.forName(STATE);
        Object made = null;
        for (Constructor<?> maker : type.getDeclaredConstructors()) {
            if (maker.getParameterTypes().length != 3) continue;
            maker.setAccessible(true);
            made = maker.newInstance(null, null, null);
            break;
        }
        if (made == null) throw new NoSuchMethodException("the row's state is made some other way now");
        Class<?> one = Class.forName("kotlin.jvm.functions.Function1");
        Class<?> two = Class.forName("kotlin.jvm.functions.Function2");
        Object tap = Proxy.newProxyInstance(one.getClassLoader(), new Class<?>[] {one, two}, (proxy, method, args) -> {
            String name = method.getName();
            if (name.equals("invoke")) {
                open(args);
                return unit();
            }
            return plain(proxy, name, args);
        });
        int set = 0;
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (field.getType() != one && field.getType() != two) continue;
            field.setAccessible(true);
            field.set(made, tap);
            set++;
        }
        if (set == 0) throw new NoSuchFieldException("the row's state has nowhere to say what a tap does");
        state = made;
        return made;
    }

    /** A tap on the row. What TikTok hands over holds the screen it was tapped on, somewhere. */
    private static void open(Object[] args) {
        try {
            Context from = Watch.here();
            if (from == null && args != null) {
                for (Object given : args) {
                    if (given instanceof Context) from = (Context) given;
                    if (from != null || given == null) continue;
                    for (Field field : given.getClass().getDeclaredFields()) {
                        if (!Context.class.isAssignableFrom(field.getType())) continue;
                        field.setAccessible(true);
                        Object held = field.get(given);
                        if (held instanceof Context) from = (Context) held;
                    }
                }
            }
            if (from == null) from = TokkiYou.context();
            if (from == null) return;
            Intent intent = new Intent(from, SettingsActivity.class);
            if (!(from instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            from.startActivity(intent);
        } catch (Throwable error) {
            Diary.note("settings row: could not open: " + error);
        }
    }

    /** Kotlin's "nothing to return", which is an object and has to be the right one. */
    private static Object unit() throws Exception {
        Object known = unit;
        if (known != null) return known;
        Class<?> type = Class.forName("kotlin.Unit");
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != type) continue;
            field.setAccessible(true);
            unit = field.get(null);
            if (unit != null) return unit;
        }
        throw new NoSuchFieldException("kotlin.Unit has no instance to give back");
    }

    /**
     * Whether the row may be drawn in TikTok's list this run. Counted before
     * the first drawing and uncounted a second after it; a count still there
     * from a run before is a run the drawing ended.
     */
    private static boolean mayTry() {
        Context context = TokkiYou.context();
        if (context == null) return false;
        final SharedPreferences prefs = context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE);
        final String key = TRIED + "_" + Version.MOD;
        int before = prefs.getInt(key, 0);
        if (before >= 2) {
            Diary.note("settings row: drawing it in the list ended the app before; it stays above the screen");
            return false;
        }
        prefs.edit().putInt(key, before + 1).commit();
        new Handler(Looper.getMainLooper()).postDelayed(() -> prefs.edit().remove(key).apply(), 1500L);
        return true;
    }
}
