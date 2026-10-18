package cat.narezany.tokkiyou;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.WeakHashMap;

/**
 * Hold down Inbox or Friends in the bottom bar to be rid of the number on it.
 *
 * The number is everything unread, and for a good many people it has said
 * "99+" for as long as they have had the app: it no longer tells them
 * anything, and the only way TikTok offers to clear it is to open every one.
 * Here holding the tab asks whether to count it all as read, and the number
 * goes.
 *
 * Nothing is told to TikTok. What is remembered is what the number was when
 * it was dismissed, and the number stays out of sight while it is no more
 * than that; one more notification and it is back, showing what TikTok
 * shows. Reading some of them the ordinary way lowers what is remembered, so
 * the next one still brings the number back. The one thing this cannot see
 * is a number past ninety-nine growing, because TikTok does not write it.
 *
 * The tabs have no names that last from one release to the next, so they are
 * found the way a person finds them: by what they say.
 */
final class TabBadges {

    private TabBadges() {}

    private static final String[] INBOX = {"входящие", "inbox", "вхідні"};
    private static final String[] FRIENDS = {"друзья", "friends", "друзі"};
    /** The number that was dismissed, for each tab. */
    private static final String KEY = "tab_read_";
    /** How long a tab has to go without a number before it is taken to have none. */
    private static final long SETTLED = 20000L;
    private static final int TRIES = 20;
    private static final long BETWEEN = 600L;

    private static final class Tab {
        final String which;
        /** The number last looked at, so a frame with nothing new costs one comparison. */
        CharSequence seen;
        /** The view the mod has put out of sight, to be put back by the mod and nobody else. */
        WeakReference<View> hidden = new WeakReference<View>(null);
        /** When the number was first missing, or zero while there is one. */
        long missingSince;
        /** Whether its having gone has been acted on, so that is done once. */
        boolean forgotten;

        Tab(String which) {
            this.which = which;
        }
    }

    private static final WeakHashMap<View, Tab> TABS = new WeakHashMap<View, Tab>();
    private static WeakReference<ViewTreeObserver> watched = new WeakReference<ViewTreeObserver>(null);
    private static final ViewTreeObserver.OnPreDrawListener EVERY_FRAME = () -> {
        try {
            for (java.util.Map.Entry<View, Tab> entry : TABS.entrySet()) {
                View tab = entry.getKey();
                if (tab != null && tab.isAttachedToWindow()) settle((ViewGroup) tab, entry.getValue());
            }
        } catch (Throwable ignored) {
            // a tab taken apart in the middle of a frame is looked at again on the next
        }
        return true;
    };

    /** A screen came up. The bar is put together after it, so it is looked for more than once. */
    static void attach(Activity activity) {
        try {
            look(activity.getWindow().getDecorView(), 0);
        } catch (Throwable error) {
            Diary.detail("tab badges: " + error);
        }
    }

    private static void look(final View screen, final int attempt) {
        try {
            if (find(screen, screen, 0) > 0) {
                ViewTreeObserver next = screen.getViewTreeObserver(), old = watched.get();
                if (next != old && next.isAlive()) {
                    if (old != null && old.isAlive()) old.removeOnPreDrawListener(EVERY_FRAME);
                    next.addOnPreDrawListener(EVERY_FRAME);
                    watched = new WeakReference<ViewTreeObserver>(next);
                }
            } else if (attempt < TRIES) {
                // No asking whether the window is up: on a cold start it is not
                // yet when the screen says it has come, and a wait posted to a
                // view is kept until it is.
                screen.postDelayed(() -> look(screen, attempt + 1), BETWEEN);
            }
        } catch (Throwable error) {
            Diary.detail("tab badges: " + error);
        }
    }

    /** Both tabs, wherever they are under a screen. How many were found. */
    private static int find(View view, View screen, int depth) {
        if (view == null || depth > 30 || view.getVisibility() != View.VISIBLE) return 0;
        if (!(view instanceof ViewGroup)) return 0;
        ViewGroup group = (ViewGroup) view;
        String which = named(view.getContentDescription());
        if (which != null && view.isClickable() && low(view, screen)) {
            if (!TABS.containsKey(view)) {
                final Tab tab = new Tab(which);
                TABS.put(view, tab);
                view.setOnLongClickListener(held -> ask((ViewGroup) held, tab));
            }
            return 1;
        }
        int found = 0;
        for (int i = 0; i < group.getChildCount() && found < 2; i++) {
            found += find(group.getChildAt(i), screen, depth + 1);
        }
        return found;
    }

    private static String named(CharSequence label) {
        if (label == null || label.length() == 0 || label.length() > 12) return null;
        String text = label.toString().trim().toLowerCase(Locale.ROOT);
        for (String word : INBOX) if (word.equals(text)) return "inbox";
        for (String word : FRIENDS) if (word.equals(text)) return "friends";
        return null;
    }

    /** The header of the inbox says "Inbox" as well; only the bar at the bottom counts. */
    private static boolean low(View view, View screen) {
        int[] at = new int[2], origin = new int[2];
        view.getLocationOnScreen(at);
        screen.getLocationOnScreen(origin);
        return at[1] - origin[1] > screen.getHeight() * 0.8f;
    }

    /** The number on a tab: the text in it that is not its name. Null when there is none. */
    private static TextView number(ViewGroup tab) {
        CharSequence name = tab.getContentDescription();
        for (int i = 0; i < tab.getChildCount(); i++) {
            View child = tab.getChildAt(i);
            if (!(child instanceof TextView) || child.getVisibility() != View.VISIBLE) continue;
            CharSequence text = ((TextView) child).getText();
            if (text == null || text.length() == 0 || text.length() > 4) continue;
            if (name != null && text.toString().contentEquals(name)) continue;
            if (TabCount.of(text) > 0) return (TextView) child;
        }
        return null;
    }

    /** Before every frame: the number is out of sight while it is one already dismissed. */
    private static void settle(ViewGroup tab, Tab state) {
        TextView number = number(tab);
        View hidden = state.hidden.get();
        if (number == null) {
            if (hidden != null) {
                hidden.setAlpha(1f);
                state.hidden = new WeakReference<View>(null);
            }
            // Gone by itself, which is nothing unread and nothing left to
            // hold back -- once it has stayed gone a good while. The number
            // is not there yet when the app has just started, and TikTok
            // takes it off for a moment when it redraws the bar; forgetting
            // on either would bring a dismissed number straight back.
            if (state.forgotten) return;
            long now = android.os.SystemClock.uptimeMillis();
            if (state.missingSince == 0) {
                state.missingSince = now;
            } else if (now - state.missingSince >= SETTLED) {
                state.forgotten = true;
                state.seen = null;
                if (dismissed(tab.getContext(), state.which) > 0) remember(tab.getContext(), state.which, 0);
            }
            return;
        }
        state.missingSince = 0;
        state.forgotten = false;
        CharSequence text = number.getText();
        boolean hide;
        if (android.text.TextUtils.equals(text, state.seen)) {
            hide = hidden != null;
        } else {
            state.seen = text.toString();
            int before = dismissed(tab.getContext(), state.which);
            int after = TabCount.remembered(before, TabCount.of(text));
            if (after != before) remember(tab.getContext(), state.which, after);
            hide = after > 0;
        }
        if (hide) {
            if (hidden != number) {
                if (hidden != null) hidden.setAlpha(1f);
                state.hidden = new WeakReference<View>(number);
            }
            if (number.getAlpha() != 0f) number.setAlpha(0f);
        } else if (hidden != null) {
            hidden.setAlpha(1f);
            state.hidden = new WeakReference<View>(null);
        }
    }

    private static boolean ask(final ViewGroup view, final Tab tab) {
        try {
            final Context context = view.getContext();
            final TextView number = number(view);
            if (number == null) return false;  // nothing unread: the hold is TikTok's to have
            final boolean back = tab.hidden.get() != null;
            Panel.with(context, Skin.remembered(context), back ? Text.TAB_UNREAD_BACK : Text.TAB_READ_ALL)
                    .text(back ? Text.TAB_UNREAD_BACK_NOTE : Text.TAB_READ_ALL_NOTE)
                    .primary(back ? Text.TAB_UNREAD_BACK_YES : Text.TAB_READ_ALL_YES, () -> {
                        remember(context, tab.which, back ? 0 : TabCount.of(number.getText()));
                        tab.seen = null;  // looked at afresh on the next frame
                        view.invalidate();
                    })
                    .quiet(Text.CANCEL, null)
                    .show();
            return true;
        } catch (Throwable error) {
            Diary.note("tab badges: " + error);
            return false;
        }
    }

    private static int dismissed(Context context, String which) {
        SharedPreferences prefs = context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE);
        return prefs.getInt(KEY + which, 0);
    }

    private static void remember(Context context, String which, int number) {
        SharedPreferences.Editor edit =
                context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE).edit();
        if (number > 0) edit.putInt(KEY + which, number);
        else edit.remove(KEY + which);
        edit.apply();
    }
}
