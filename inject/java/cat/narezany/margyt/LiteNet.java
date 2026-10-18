package cat.narezany.margyt;

/**
 * What TikTok Lite's own network code asks the mod.
 *
 * Lite is told by the server which requests to throw away without sending:
 * that is how an account from a country Lite is not offered in ends up with a
 * feed and nothing else. The throwing away is done on the phone, so the phone
 * can decline to.
 */
public final class LiteNet {

    private static boolean told;

    private LiteNet() {}

    /** Whether a request the rules say to drop is in fact dropped. */
    public static boolean drops() {
        if (!Version.LITE) return true;
        if (!told) {
            told = true;
            Diary.note("network: Lite was told to drop requests, and they are sent all the same");
        }
        return false;
    }
}
