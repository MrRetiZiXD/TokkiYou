package cat.narezany.tokkiyou;

import android.os.SystemClock;

/**
 * Where the mod's time goes, counted rather than guessed at.
 *
 * "It lags" has a cause, and the cause is some piece of the mod running more
 * often or for longer than anybody meant. Each piece worth suspecting is
 * timed, and every couple of seconds the totals go to the system log, where
 * `adb logcat -s TokkiYou` reads them while the phone is simply being used.
 *
 * Only in a test build. TEST is a constant, so in a release every call here
 * is an `if (false)` the compiler has already thrown away.
 */
final class Probe {

    private Probe() {}

    static final boolean ON = Version.TEST;

    static final int THEME = 0, FONTS = 1, DATES = 2, COLOURS = 3, BADGES = 4, ACCENT = 5,
            DIM_PRINT = 6, DIM_PASS = 7, DIM_FRAME = 8, PINK = 9, TEXT = 10, PAINT = 11,
            ALPHA = 12, NAMES = 13, RAIL = 14, FEED = 15, THEME_COLOUR = 16, DATE_FRAME = 17,
            FONT_ONE = 18, SHOWN = 19, SLICE = 20;
    private static final String[] NAMES_OF = {
            "theme walk", "font walk", "date walk", "role colours walk", "badge walk",
            "accent walk", "dim fingerprint", "dim pass", "dim per frame", "pink sweep",
            "setText", "paint colour", "setAlpha", "names", "rail visibility", "feed filter",
            "theme colour", "date per frame", "font one", "screen shown", "sliced walk"};

    private static final long[] spent = new long[NAMES_OF.length];
    private static final int[] calls = new int[NAMES_OF.length];
    private static final long[] longest = new long[NAMES_OF.length];
    private static long told;

    static long start() {
        return ON ? System.nanoTime() : 0L;
    }

    /** Not exact across threads, and does not need to be: these are totals. */
    static void stop(int what, long started) {
        if (!ON) return;
        long took = System.nanoTime() - started;
        spent[what] += took;
        calls[what]++;
        if (took > longest[what]) longest[what] = took;
        long now = SystemClock.uptimeMillis();
        if (now - told < 2000) return;
        long since = now - told;
        told = now;
        StringBuilder line = new StringBuilder();
        long all = 0;
        for (int i = 0; i < spent.length; i++) {
            if (calls[i] == 0) continue;
            all += spent[i];
            if (spent[i] >= 200000L) {
                line.append(NAMES_OF[i]).append(' ').append(calls[i]).append("x ")
                        .append(spent[i] / 100000L / 10f).append("ms (max ")
                        .append(longest[i] / 100000L / 10f).append(") | ");
            }
            spent[i] = 0;
            calls[i] = 0;
            longest[i] = 0;
        }
        if (all >= 1000000L) {
            // as an error, because this phone keeps nothing quieter from an app
            android.util.Log.e("TokkiYou", "in " + since + "ms the mod took "
                    + all / 100000L / 10f + "ms: " + line);
        }
    }
}
