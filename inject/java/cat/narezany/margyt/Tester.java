package cat.narezany.margyt;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.List;

/**
 * What a test build does that a release does not.
 *
 * The screen carries the account id, faint enough not to notice and plain
 * enough to survive a screenshot. It is redrawn every ten seconds so a shared
 * phone does not name the wrong person.
 *
 * The mod's settings open only for the supporter badge. TikTok itself is left
 * alone, so a leaked build is just TikTok.
 */
public final class Tester {

    /** The badge that makes a test build yours. */
    static final String SUPPORTER = "supporter";

    /** Faint enough to disappear into a video, solid enough to survive a jpeg. */
    private static final int INK = 0x14FFFFFF;

    private static final long AGAIN = 10_000L;

    private static final int TAG = 0x4D617254;   // "MarT"

    private static final Handler HAND = new Handler(Looper.getMainLooper());

    private Tester() {}

    /** Whether this build is a test build at all. */
    public static boolean on() {
        return Version.TEST;
    }

    /** The account's own list, hidden badges included. */
    public static boolean allowed() {
        if (!on()) return true;
        try {
            List<Mine.Held> held = Mine.held();
            for (Mine.Held one : held) {
                if (SUPPORTER.equals(one.id)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Whether the answer is known yet, as opposed to known to be no. */
    public static boolean asked() {
        try {
            return Mine.anything() || Mine.everAsked();
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ------------------------------------------------------------ the mark




    /**
     * The view goes into the window's root, not into TikTok's layouts, so
     * nothing of TikTok's removes it. It cannot be tapped.
     */
    static void mark(final Activity activity) {
        if (!on()) return;
        View root = activity.getWindow() == null ? null
                : activity.getWindow().getDecorView();
        if (!(root instanceof ViewGroup)) return;
        ViewGroup decor = (ViewGroup) root;
        if (decor.findViewWithTag(TAG) != null) return;

        final TextView mark = new TextView(activity);
        mark.setTag(TAG);
        mark.setText(who());
        mark.setTextSize(22);
        mark.setTextColor(INK);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setShadowLayer(1.5f, 0, 0, 0x10000000);
        mark.setClickable(false);
        mark.setFocusable(false);
        mark.setDuplicateParentStateEnabled(false);

        FrameLayout.LayoutParams where = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        decor.addView(mark, where);

        HAND.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mark.getParent() == null) return;
                mark.setText(who());
                mark.bringToFront();
                HAND.postDelayed(this, AGAIN);
            }
        }, AGAIN);
    }

    /** The account id, or a plain word when nobody is signed in yet. */
    private static String who() {
        try {
            String id = Account.id();
            if (id != null && id.length() > 0) return id;
        } catch (Throwable ignored) {
        }
        return Text.TEST_NOBODY;
    }

    // ---------------------------------------------------------- the refusal

    /** Say why the settings will not open, and what to do about it. */
    public static void refuse(Activity activity) {
        try {
            Popup.told(activity, Text.TEST_TITLE, Text.TEST_TEXT, Text.TEST_CLOSE,
                    new Runnable() {
                        @Override
                        public void run() {
                            activity.finish();
                        }
                    });
        } catch (Throwable error) {
            Diary.note("test: " + error);
            activity.finish();
        }
    }
}
