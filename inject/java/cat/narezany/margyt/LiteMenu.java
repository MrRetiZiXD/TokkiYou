package cat.narezany.margyt;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/**
 * The mod's row in the sheet TikTok Lite opens from the three lines of the
 * profile. Lite makes the row itself, the way it makes its own; this is what
 * it asks for the title and for what a tap does.
 */
public final class LiteMenu {

    private LiteMenu() {}

    public static String title() {
        return "TokkiYou Lite";
    }

    /** What the row does when tapped, as the kind of function Lite's rows take. */
    public static Object tapped() {
        try {
            Class<?> kind = Class.forName("kotlin.jvm.functions.Function1");
            return Proxy.newProxyInstance(kind.getClassLoader(), new Class<?>[] {kind}, (proxy, method, given) -> {
                switch (method.getName()) {
                    case "invoke":
                        open(given != null && given.length > 0 && given[0] instanceof View
                                ? ((View) given[0]).getContext() : null);
                        return nothing();
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return given != null && given.length == 1 && proxy == given[0];
                    case "toString":
                        return "TokkiYou Lite";
                    default:
                        return null;
                }
            });
        } catch (Throwable broken) {
            Diary.note("menu row: " + broken);
            return null;
        }
    }

    private static void open(Context from) {
        try {
            Context context = from != null ? from : Margy.context();
            if (context == null) return;
            Intent intent = new Intent(context, SettingsActivity.class);
            if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable broken) {
            Diary.note("menu row tapped: " + broken);
        }
    }

    /** Kotlin's Unit, which is what such a function is expected to return. */
    private static Object nothing() {
        try {
            Class<?> unit = Class.forName("kotlin.Unit");
            for (Field field : unit.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == unit) {
                    field.setAccessible(true);
                    return field.get(null);
                }
            }
        } catch (Throwable missing) {
            // a row that returns nothing still works
        }
        return null;
    }
}
