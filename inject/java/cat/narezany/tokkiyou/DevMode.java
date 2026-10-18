package cat.narezany.tokkiyou;

import android.content.Context;

/**
 * The developer switch: five taps in a row on the "TokkiYou" heading.
 *
 * It changes nothing for an ordinary person. Switched on, the diary keeps a lot
 * more lines, writes the detailed ones too, and the settings show and copy a
 * full report (device, build, hooks, plugins, the whole diary). The report
 * never includes the account id, tokens or session data.
 */
final class DevMode {
    private DevMode() {}

    static final String KEY = "developer";
    private static final int NEEDED = 5;
    private static final long GAP = 1000L;

    /** 0 not read yet, 1 off, 2 on. Kept so Diary.note() stays cheap. */
    private static volatile int state;
    private static int taps;
    private static long lastTap;

    static boolean isOn() {
        int known = state;
        if (known != 0) return known == 2;
        Context context = TokkiYou.context();
        if (context == null) return false;  // too early to know; do not cache it
        boolean on = false;
        try {
            on = context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY, false);
        } catch (Throwable ignored) { }
        state = on ? 2 : 1;
        return on;
    }

    static void set(boolean on) {
        state = on ? 2 : 1;
        Flags.forget();  // the developer's answers for flags stand only while this is on
        Context context = TokkiYou.context();
        if (context == null) return;
        try {
            context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY, on).apply();
        } catch (Throwable ignored) { }
    }

    /** One tap on the heading. True on the fifth in a row; one by accident does nothing. */
    static synchronized boolean tapped() {
        long now = android.os.SystemClock.uptimeMillis();
        taps = now - lastTap > GAP ? 1 : taps + 1;
        lastTap = now;
        if (taps < NEEDED) return false;
        taps = 0;
        return true;
    }
}
