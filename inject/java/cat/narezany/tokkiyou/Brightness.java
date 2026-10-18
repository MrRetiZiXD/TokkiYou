package cat.narezany.tokkiyou;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;

/**
 * The brightness of the screen stays the one its owner set.
 *
 * TikTok takes it in two ways. A window can ask for a brightness of its own,
 * and the camera does: it asks for all of it, and the slider in the shade
 * does nothing until that screen is left. And an HDR video is allowed more
 * light than everything else on the screen, which in a dark room is the
 * whole display going to full for one video in the feed.
 *
 * The first is stopped where it is asked for. Every call TikTok makes to
 * change a window comes through here, and a brightness on it is taken off
 * before it is passed on; the rest of what the window was asked for goes
 * through as it was.
 *
 * The second used to be argued with the display -- a headroom of one on the
 * window and on every surface under it -- and the display did not listen.
 * TikTok has a switch of its own for this, kept among its accessibility
 * settings: with it on, the player leaves the HDR renditions of a video out
 * of what it chooses from and plays an ordinary one. The player asks the mod
 * the same question now, so no brighter picture is ever decoded. A video
 * that was published with nothing but an HDR rendition still plays as that.
 */
public final class Brightness {

    static final String KEY = "own_brightness";

    private static volatile Boolean cached;
    private static volatile boolean told;

    private Brightness() {}

    public static boolean isEnabled() {
        Boolean known = cached;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return true;  // too early to know; not remembered
        boolean on = prefs.getBoolean(KEY, true);
        cached = on;
        return on;
    }

    public static void setEnabled(boolean enabled) {
        cached = enabled;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY, enabled).apply();
    }

    private static SharedPreferences prefs() {
        Context context = TokkiYou.context();
        if (context == null) return null;
        try {
            return context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** TikTok is changing a window: everything it asks for but the brightness. */
    public static void setAttributes(Window window, WindowManager.LayoutParams wanted) {
        try {
            if (wanted != null && wanted.screenBrightness >= 0f && isEnabled()) {
                if (!told) {
                    told = true;
                    Diary.detail("brightness: a window asked for " + wanted.screenBrightness + " and was not given it");
                }
                wanted.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            }
        } catch (Throwable ignored) {
            // the window is changed as TikTok asked rather than not at all
        }
        window.setAttributes(wanted);
    }

    /** What TikTok's player asks before choosing a rendition: whether HDR ones are left out. */
    public static boolean flat() {
        return isEnabled();
    }

    /** A screen came up, perhaps with a brightness it was given before the setting was on. */
    static void apply(Activity activity) {
        if (activity == null || !isEnabled()) return;
        try {
            Window window = activity.getWindow();
            if (window == null) return;
            WindowManager.LayoutParams now = window.getAttributes();
            if (now != null && now.screenBrightness >= 0f) {
                now.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
                window.setAttributes(now);
            }
            // for the video that has only an HDR rendition: asked of the
            // display, which may or may not listen
            if (Build.VERSION.SDK_INT >= 26 && window.getColorMode() == ActivityInfo.COLOR_MODE_HDR) {
                window.setColorMode(ActivityInfo.COLOR_MODE_DEFAULT);
            }
            if (Build.VERSION.SDK_INT >= 35) window.setDesiredHdrHeadroom(1f);
        } catch (Throwable error) {
            Diary.detail("brightness: " + error);
        }
    }
}
