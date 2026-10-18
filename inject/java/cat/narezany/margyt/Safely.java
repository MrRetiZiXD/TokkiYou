package cat.narezany.margyt;

import android.content.Context;
import android.content.res.Resources;

/**
 * Calls of TikTok's own that fail in a way the mod can stand in for.
 *
 * Nine in ten of the crash reports from every version of the mod were one
 * crash, and it is TikTok's: when a message arrives while the app is open,
 * the code that shows the banner for it asks for a string that is not in
 * the apk. It is in a part of TikTok that Google Play delivers separately
 * and that an apk installed by hand does not have. A banner with nothing
 * written where that string would go is a small loss; the app closing every
 * time somebody writes is not.
 */
public final class Safely {

    private Safely() {}

    private static volatile boolean told;

    /** The string, or nothing where there is none. */
    public static String getString(Context context, int id) {
        try {
            return context.getString(id);
        } catch (Resources.NotFoundException missing) {
            if (!told) {
                told = true;
                Diary.note("TikTok asked for a string this apk does not have: 0x" + Integer.toHexString(id));
            }
            return "";
        }
    }
}
