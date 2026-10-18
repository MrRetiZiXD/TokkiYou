package cat.narezany.tokkiyou;

import android.app.Activity;
import android.content.Context;

import com.ss.android.ugc.aweme.im.message.template.card.StickerTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Saving a sticker out of a conversation, which TikTok does not offer.
 *
 * Touching one goes through `MessageListStickerClickAbility`, whose name is
 * real, and it is handed a `StickerTemplate`, whose name is real as well.
 * Five of its methods take that type -- the obfuscator renamed the methods,
 * not the types -- and all five are rewritten to come through here. Whatever
 * the app was going to do still happens: the sticker is noted on the way past
 * and the call is passed straight on.
 *
 * The receiver arrives as a plain Object rather than as the interface, and
 * deliberately: a call site names whatever static type it is holding, which is
 * the class implementing the interface and not the interface itself, so the
 * rewrite cannot ask for one particular owner. Which is why the call onwards
 * goes through reflection -- on a tap, where it costs nothing.
 *
 * The address inside the template is found by walking the object rather than
 * by naming its parts, because the parts are obfuscated and a walk of a few
 * dozen fields costs nothing on a tap. Anything that looks like an http
 * address and belongs to a sticker will do; the largest is preferred, since
 * these models keep the same picture at three sizes.
 */
public final class Stickers {
    static void reloadSettings(){cached=null;}

    private Stickers() {}

public static final String KEY = "save_stickers";

    private static volatile Boolean cached;

    public static boolean isEnabled() {
        Boolean known = cached;
        if (known != null) return known;
        try {
            android.content.Context context = TokkiYou.context();
            if (context == null) return true;  // on until there is somewhere to read from
            boolean on = context.getSharedPreferences(TokkiYou.PREFS,
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
            android.content.Context context = TokkiYou.context();
            if (context == null) return;
            context.getSharedPreferences(TokkiYou.PREFS, android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY, enabled).apply();
        } catch (Throwable ignored) {
        }
    }

    private static volatile String latest;

    // ------------------------------------------------ where the taps land

    // The names are 47.2.41's and live in `tokkiyou/dexpatch.py` beside the
    // shapes they are matched by. A release that renames them matches nothing,
    // and the taps go where they always went.

    public static void RQ1(Object ability, Object view, Object a, Object b,
                           StickerTemplate sticker) {
        seen(sticker);
        onwards(ability, "RQ1", view, a, b, sticker);
    }

    public static void rP(Object ability, Object view, Object a, Object b,
                          StickerTemplate sticker) {
        seen(sticker);
        onwards(ability, "rP", view, a, b, sticker);
    }

    public static void DD2(Object ability, Object picture, Object manager, Object a,
                           StickerTemplate sticker) {
        seen(sticker);
        onwards(ability, "DD2", picture, manager, a, sticker);
    }

    public static void vQ1(Object ability, Object corner, StickerTemplate sticker) {
        seen(sticker);
        onwards(ability, "vQ1", corner, sticker);
    }

    /**
     * Hand the call back to TikTok.
     *
     * By name and by how many arguments it takes, rather than by their types:
     * the types are obfuscated, and the mod is holding the very objects that
     * were about to be passed, so there is nothing to resolve. If this fails
     * the tap does nothing -- which is why it happens last, after the mod has
     * already noted what it wanted.
     */
    private static void onwards(Object ability, String name, Object... args) {
        if (ability == null) return;
        try {
            for (Method method : ability.getClass().getMethods()) {
                if (!method.getName().equals(name)) continue;
                if (method.getParameterTypes().length != args.length) continue;
                method.setAccessible(true);
                method.invoke(ability, args);
                return;
            }
            Diary.note("sticker tap: no " + name + " taking " + args.length);
        } catch (Throwable error) {
            Diary.note("sticker tap: " + error);
        }
    }

    static void seen(Object sticker) {
        if (!isEnabled()) return;
        try {
            String url = find(sticker, new HashSet<Object>(), 0);
            if (url == null) return;
            latest = url;
            offer();
        } catch (Throwable error) {
            Diary.note("sticker: " + error);
        }
    }

    // ------------------------------------------------- the button in the sheet

    // A sticker in the comments opens a sheet with the sticker and two
    // buttons under it, Share and Save -- "save" there meaning into TikTok's
    // favourites. Saving the picture belongs beside them, as a third button
    // in the same row, and not as a pill of the mod's own floating somewhere
    // above: the two that are there give up a little width and it takes the
    // end of the row.
    //
    // The sheet is a window of its own and is built a moment after the tap,
    // once TikTok has fetched what it shows, so it is looked for rather than
    // waited on: for a couple of seconds, among the windows the app has open,
    // by what it is -- a row of exactly two buttons under a picture. If no
    // such sheet turns up, the offer is made the old way.

    private static final int PLACED = 0x4D617274;  // "Mart"
    private static final long LOOK_FOR = 2500L;
    private static final android.os.Handler MAIN =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private static Runnable looking;

    /** A sheet about this sticker is opening, or is expected to. */
    static void sheet(Object sticker) {
        if (!isEnabled()) return;
        try {
            String url = find(sticker, new HashSet<Object>(), 0);
            if (url == null) return;
            latest = url;
            final long until = android.os.SystemClock.uptimeMillis() + LOOK_FOR;
            if (looking != null) MAIN.removeCallbacks(looking);
            looking = new Runnable() {
                @Override
                public void run() {
                    if (looking != this) return;
                    boolean placed = false;
                    try {
                        placed = place();
                    } catch (Throwable error) {
                        Diary.note("sticker button: " + error);
                    }
                    if (placed) {
                        looking = null;
                    } else if (android.os.SystemClock.uptimeMillis() < until) {
                        MAIN.postDelayed(this, 60L);
                    } else {
                        looking = null;
                        offer();
                    }
                }
            };
            MAIN.post(looking);
        } catch (Throwable error) {
            Diary.note("sticker: " + error);
        }
    }

    private static void offer() {
        Screen.offer(Text.SAVE_STICKER, 0.46f, new Runnable() {
            @Override
            public void run() {
                save();
            }
        });
    }

    /** Every window the app has open, newest last. */
    private static List<android.view.View> windows() {
        try {
            Class<?> global = Class.forName("android.view.WindowManagerGlobal");
            Object manager = global.getMethod("getInstance").invoke(null);
            Field views = global.getDeclaredField("mViews");
            views.setAccessible(true);
            Object held = views.get(manager);
            if (held instanceof List) {
                return new java.util.ArrayList<android.view.View>((List<android.view.View>) held);
            }
        } catch (Throwable ignored) {
        }
        return new java.util.ArrayList<android.view.View>();
    }

    /** True once the button is in the sheet, or was already. */
    private static boolean place() {
        List<android.view.View> open = windows();
        for (int i = open.size() - 1; i >= 0; i--) {
            android.widget.LinearLayout row = row(open.get(i), 0, new int[] {4000});
            if (row == null) continue;
            if (row.getTag(PLACED) != null) return true;
            // not yet measured: come back when it has a height to copy
            if (row.getChildAt(0).getHeight() <= 0) return false;
            add(row);
            return true;
        }
        return false;
    }

    /** The row of two buttons under a picture, if this window is that sheet. */
    private static android.widget.LinearLayout row(android.view.View view, int depth, int[] budget) {
        if (view == null || depth > 40 || --budget[0] < 0
                || view.getVisibility() != android.view.View.VISIBLE) return null;
        if (!(view instanceof android.view.ViewGroup)) return null;
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        if (group instanceof android.widget.LinearLayout
                && ((android.widget.LinearLayout) group).getOrientation()
                        == android.widget.LinearLayout.HORIZONTAL) {
            if (group.getTag(PLACED) != null) return (android.widget.LinearLayout) group;
            if (group.getChildCount() == 2 && button(group.getChildAt(0))
                    && button(group.getChildAt(1)) && underPicture(group)) {
                return (android.widget.LinearLayout) group;
            }
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            android.widget.LinearLayout found = row(group.getChildAt(i), depth + 1, budget);
            if (found != null) return found;
        }
        return null;
    }

    private static boolean button(android.view.View view) {
        if (!(view instanceof android.widget.TextView)
                || view.getVisibility() != android.view.View.VISIBLE || !view.isClickable()) {
            return false;
        }
        CharSequence says = ((android.widget.TextView) view).getText();
        if (says == null || says.length() == 0) return false;
        // TikTok's button is a text view that calls itself a button
        return view instanceof android.widget.Button
                || "android.widget.Button".contentEquals(view.getAccessibilityClassName());
    }

    private static boolean underPicture(android.view.View row) {
        if (!(row.getParent() instanceof android.view.ViewGroup)) return false;
        android.view.ViewGroup around = (android.view.ViewGroup) row.getParent();
        for (int i = 0; i < around.getChildCount(); i++) {
            if (around.getChildAt(i) instanceof android.widget.ImageView) return true;
        }
        return false;
    }

    private static void add(android.widget.LinearLayout row) {
        Context context = row.getContext();
        android.widget.TextView first = (android.widget.TextView) row.getChildAt(0);
        android.view.View second = row.getChildAt(1);
        float density = context.getResources().getDisplayMetrics().density;
        int tall = first.getHeight();
        int gap = Math.max(second.getLeft() - first.getRight(), Math.round(8 * density));

        // the two that are there share the row evenly however they came to;
        // told to share it by weight, they make room for a third on their own
        for (android.view.View one : new android.view.View[] {first, second}) {
            android.widget.LinearLayout.LayoutParams how =
                    (android.widget.LinearLayout.LayoutParams) one.getLayoutParams();
            if (how.weight <= 0f) {
                how.width = 0;
                how.weight = 1f;
                one.setLayoutParams(how);
            }
        }

        android.widget.ImageView mine = new android.widget.ImageView(context);
        // the look of the quieter of the two, which is the first
        android.graphics.drawable.Drawable ground = null;
        try {
            android.graphics.drawable.Drawable theirs = first.getBackground();
            if (theirs != null && theirs.getConstantState() != null) {
                ground = theirs.getConstantState().newDrawable(context.getResources()).mutate();
            }
        } catch (Throwable ignored) {
        }
        int ink = first.getCurrentTextColor();
        if (ground == null) {
            android.graphics.drawable.GradientDrawable plain =
                    new android.graphics.drawable.GradientDrawable();
            plain.setColor((ink & 0x00FFFFFF) | 0x1F000000);
            plain.setCornerRadius(4 * density);
            ground = plain;
        }
        mine.setBackground(ground);
        mine.setImageDrawable(SettingsGlyph.make("download", ink));
        mine.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
        int inset = Math.max(0, (tall - Math.round(24 * density)) / 2);
        mine.setPadding(inset, inset, inset, inset);
        mine.setContentDescription(Text.SAVE_STICKER);
        mine.setClickable(true);
        mine.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View pressed) {
                save();
            }
        });

        android.widget.LinearLayout.LayoutParams where =
                new android.widget.LinearLayout.LayoutParams(tall, tall);
        where.setMarginStart(gap);
        where.gravity = ((android.widget.LinearLayout.LayoutParams)
                first.getLayoutParams()).gravity;
        row.addView(mine, where);
        row.setTag(PLACED, Boolean.TRUE);
    }

    // ---------------------------------------------------------- the walk

    private static final int DEEP = 4;

    /** The best http address anywhere inside an object, or null. */
    private static String find(Object thing, Set<Object> seen, int depth) {
        if (thing == null || depth > DEEP) return null;
        if (thing instanceof String) {
            String value = (String) thing;
            return value.startsWith("http") ? value : null;
        }
        if (thing instanceof List) {
            String best = null;
            for (Object item : (List) thing) {
                String found = find(item, seen, depth + 1);
                if (found != null && (best == null || found.length() > best.length())) best = found;
            }
            return best;
        }

        Class<?> type = thing.getClass();
        String name = type.getName();
        // the framework and the language are not where a sticker's url is
        if (name.startsWith("java.") || name.startsWith("android.")) return null;
        if (!seen.add(thing)) return null;

        String best = null;
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    String found = find(field.get(thing), seen, depth + 1);
                    // the same picture arrives at three sizes and the longest
                    // url is reliably the largest of them
                    if (found != null && (best == null || found.length() > best.length())) {
                        best = found;
                    }
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return best;
    }

    // --------------------------------------------------------- saving it

    private static void save() {
        final String url = latest;
        final Context context = TokkiYou.context();
        if (url == null || context == null) return;
        Net.away("sticker", new Runnable() {
            @Override
            public void run() {
                byte[] data = Net.bytes(url);
                boolean webp = url.contains(".webp") || url.contains("webp");
                String at = data == null ? null : Gallery.save(context, data,
                        Gallery.name("sticker", url, webp ? "webp" : "png"),
                        webp ? "image/webp" : "image/png");
                Screen.say(at == null ? Text.STICKER_FAILED : Text.SAVED);
            }
        });
    }

    /** Whether there is anything to save, for the settings screen to ask. */
    public static boolean have() {
        return latest != null;
    }
}
