package cat.narezany.tokkiyou;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

/**
 * The way back from a flag that stops TikTok starting.
 *
 * A developer's answer for a flag is applied before the first screen is drawn,
 * and the only place to take it back is a screen inside the app. One wrong
 * answer could leave nothing to do but clear the app's data. So every start
 * with such answers in force is counted, and the count is wiped once the app
 * has stayed up for a while; three starts in a row that did not last that long
 * and the answers are left out, until somebody says to try them again.
 *
 * Only the main process counts. TikTok starts several others beside it, and
 * each would add one to a count that only the main one ever gets to wipe.
 */
final class FlagGuard {

    private FlagGuard() {}

    // not under the mod's own name: the settings screen watches those files
    // for changes, and this one changes by itself
    private static final String FILE = "flagguard_tokkiyou";
    private static final String STARTS = "starts";
    private static final int TOO_MANY = 3;
    private static final long LASTED = 15000L;

    /** 0 not asked yet, 1 held back, 2 let through. One answer a process. */
    private static volatile int answer;

    /** Whether the developer's answers may be applied in this process. */
    static boolean allows() {
        int known = answer;
        if (known != 0) return known == 2;
        Context context = TokkiYou.context();
        if (context == null) return false;
        try {
            final SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
            int starts = prefs.getInt(STARTS, 0);
            if (starts >= TOO_MANY) {
                answer = 1;
                Diary.note("flags: held back, TikTok did not stay up with them");
                return false;
            }
            if (context.getPackageName().equals(processName())) {
                // written at once: what this guards against is the process dying
                prefs.edit().putInt(STARTS, starts + 1).commit();
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> prefs.edit().putInt(STARTS, 0).apply(), LASTED);
            }
            answer = 2;
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    static boolean heldBack() {
        return answer == 1;
    }

    /** Somebody changed the answers, or asked for them back: start counting again. */
    static void again() {
        answer = 2;
        Context context = TokkiYou.context();
        if (context == null) return;
        try {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(STARTS, 0).apply();
        } catch (Throwable ignored) { }
    }

    private static String processName() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) return android.app.Application.getProcessName();
            return (String) Class.forName("android.app.ActivityThread")
                    .getMethod("currentProcessName").invoke(null);
        } catch (Throwable error) {
            return null;
        }
    }
}
