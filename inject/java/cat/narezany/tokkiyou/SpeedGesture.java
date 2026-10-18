package cat.narezany.tokkiyou;

import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * The rate a held video plays at, when it is not TikTok's own.
 *
 * Holding the edge of a video speeds it up, and TikTok's speed is two. The
 * mod lets that be another number, and the only moment to say so is when the
 * speeding starts -- so the touch that may start it comes through here, and
 * once TikTok says it is speeding the chosen rate is put in place of its own.
 *
 * This used to do a great deal more: it locked the speed on a swipe down and
 * drew a pill of its own to say so. TikTok has that lock itself, switched off
 * for most accounts, and {@link Flags} now switches it on for everybody. What
 * is left here takes no touch away from anything and draws nothing.
 */
public final class SpeedGesture {

    private SpeedGesture() {}

    /** TikTok's own rate, at which there is nothing for this to do. */
    private static final float NATIVE = 2f;

    private static Runnable watching;

    /** The top of TikTok's touch handler. Never the one to handle the touch. */
    public static boolean onTouch(Object listener, final View target, MotionEvent event) {
        try {
            if (listener == null || target == null || event == null) return false;
            if (event.getActionMasked() != MotionEvent.ACTION_DOWN) return false;
            if (Math.abs(PlayerTools.speed() - NATIVE) < 0.01f) return false;
            Object inside = read(listener, "l0");
            final Object component = inside == null ? listener : inside;  // LX/16wu is its own
            if (watching != null) target.removeCallbacks(watching);
            // a hold sends no further events, so TikTok's own flag is asked
            // for the two seconds in which a hold either starts or was a tap
            watching = new Runnable() {
                private int looks;

                @Override
                public void run() {
                    if (watching != this) return;
                    if (isSpeeding(ability(component))) {
                        watching = null;
                        PlayerTools.applyHoldRate();
                        return;
                    }
                    if (++looks < 40 && target.isAttachedToWindow()) target.postDelayed(this, 50L);
                    else watching = null;
                }
            };
            target.postDelayed(watching, 50L);
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Object ability(Object component) {
        Object edge = read(component, "LLJL"); // LX/16wu owns the actual speed touch path.
        if (edge != null) return edge;
        edge = read(component, "LLLILZJ");
        if (edge != null) return edge;
        // Older feed component layout keeps EdgeSpeedupAbility in a Kotlin lazy.
        return call(read(component, "LLLL"), "getValue");
    }

    private static boolean isSpeeding(Object ability) {
        Object value = call(ability, "R62");
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    private static Object call(Object receiver, String name) {
        if (receiver == null) return null;
        try {
            return method(receiver.getClass(), name).invoke(receiver);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Method> methods =
            new java.util.concurrent.ConcurrentHashMap<String, Method>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Field> fields =
            new java.util.concurrent.ConcurrentHashMap<String, Field>();
    private static final java.util.Set<String> absent = java.util.Collections.newSetFromMap(
            new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

    private static Method method(Class<?> type, String name) throws NoSuchMethodException {
        String key = type.getName() + "#" + name;
        Method cached = methods.get(key);
        if (cached != null) return cached;
        if (absent.contains(key)) throw new NoSuchMethodException(name);
        Class<?> current = type;
        while (current != null) {
            try {
                Method found = current.getDeclaredMethod(name);
                found.setAccessible(true);
                methods.put(key, found);
                return found;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        absent.add(key);
        throw new NoSuchMethodException(name);
    }

    private static Object read(Object receiver, String name) {
        if (receiver == null) return null;
        String key = receiver.getClass().getName() + "@" + name;
        if (absent.contains(key)) return null;
        Field cached = fields.get(key);
        if (cached != null) {
            try {
                return cached.get(receiver);
            } catch (Throwable ignored) {
                return null;
            }
        }
        Class<?> current = receiver.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                fields.put(key, field);
                return field.get(receiver);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        absent.add(key);
        return null;
    }
}
