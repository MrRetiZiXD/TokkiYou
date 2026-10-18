package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.Display;
import android.view.Surface;
import android.view.Window;
import android.view.WindowManager;

/**
 * The frame rate, chosen here instead of by TikTok.
 *
 * The window is asked for a display mode with that rate, and TikTok's own
 * requests for a rate are answered with ours. Asking the window alone is not
 * enough: the app asks again and wins.
 */
public final class Rate {

    static final String KEY = "fps_lock";

    /** Whatever TikTok decides, which is how it arrives. */
    public static final String AUTO = "";

    public static final String[] CHOICES = {AUTO, "60", "90", "120"};

    /** A mode counts as the one asked for within this much. */
    private static final float CLOSE = 1.5f;

    private static volatile String chosen;

    private Rate() {}

    // ------------------------------------------------------------ the choice

    public static String name() {
        String known = chosen;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        String value = prefs == null ? AUTO : prefs.getString(KEY, AUTO);
        chosen = value;
        return value;
    }

    public static void choose(String value) {
        if (value == null) value = AUTO;
        chosen = value;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putString(KEY, value).apply();
    }

    /** The rate wanted, or zero for TikTok's own choice. */
    public static int wanted() {
        String value = name();
        if (value == null || value.length() == 0) return 0;
        try {
            return Integer.parseInt(value);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static SharedPreferences prefs() {
        Context context = Margy.context();
        if (context == null) return null;
        try {
            return context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------ asking the window


    /**
     * A mode is asked for by id where one matches. A bare preferred rate is a
     * hint the system often ignores. Only modes at the current size count.
     */
    public static void apply(Activity activity) {
        int rate = wanted();
        if (rate <= 0 || activity == null) return;
        try {
            Window window = activity.getWindow();
            if (window == null) return;
            WindowManager.LayoutParams params = window.getAttributes();

            if (Build.VERSION.SDK_INT >= 23) {
                Display display = window.getWindowManager().getDefaultDisplay();
                Display.Mode now = display.getMode();
                Display.Mode best = null;
                for (Display.Mode mode : display.getSupportedModes()) {
                    if (mode.getPhysicalWidth() != now.getPhysicalWidth()
                            || mode.getPhysicalHeight() != now.getPhysicalHeight()) {
                        continue;
                    }
                    if (Math.abs(mode.getRefreshRate() - rate) > CLOSE) continue;
                    if (best == null || mode.getRefreshRate() > best.getRefreshRate()) {
                        best = mode;
                    }
                }
                if (best != null) params.preferredDisplayModeId = best.getModeId();
            }
            params.preferredRefreshRate = rate;
            window.setAttributes(params);
        } catch (Throwable error) {
            Diary.note("fps: " + error);
        }
    }

    /** Whether the screen can show what was chosen, so the settings can say. */
    public static boolean reachable(Activity activity) {
        int rate = wanted();
        if (rate <= 0 || activity == null || Build.VERSION.SDK_INT < 23) return true;
        try {
            Display display = activity.getWindowManager().getDefaultDisplay();
            for (Display.Mode mode : display.getSupportedModes()) {
                if (Math.abs(mode.getRefreshRate() - rate) <= CLOSE) return true;
            }
            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }



    // ------------------------------------------- where TikTok asks for a rate

    /** TikTok asking for a rate. Answered with ours, or left alone. */
    public static void setFrameRate(Surface surface, float rate, int compatibility) {
        int ours = wanted();
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                surface.setFrameRate(ours > 0 ? ours : rate, compatibility);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void setFrameRate(Surface surface, float rate, int compatibility,
                                    int strategy) {
        int ours = wanted();
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                surface.setFrameRate(ours > 0 ? ours : rate, compatibility, strategy);
            } else if (Build.VERSION.SDK_INT >= 30) {
                surface.setFrameRate(ours > 0 ? ours : rate, compatibility);
            }
        } catch (Throwable ignored) {
        }
    }
}
