package cat.narezany.margyt;

/**
 * The arithmetic of a dismissed number, apart from the views it is read off.
 */
final class TabCount {

    private TabCount() {}

    /** What "99+" is taken to be: more than any number that is written out. */
    static final int PLENTY = 100;

    /** "13" is thirteen and "99+" is plenty; anything else is not a number of unread things. */
    static int of(CharSequence text) {
        if (text == null) return 0;
        String said = text.toString().trim();
        if (said.endsWith("+")) {
            return said.length() > 1 && digits(said.substring(0, said.length() - 1)) ? PLENTY : 0;
        }
        if (said.length() > 3 || !digits(said)) return 0;
        return Integer.parseInt(said);
    }

    private static boolean digits(String text) {
        if (text.length() == 0) return false;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') return false;
        }
        return true;
    }

    /**
     * What is to be remembered once the number says `now`, given the one that
     * was dismissed. The same or less keeps it out of sight, and less is what
     * is remembered from then on, so that reading a few the ordinary way does
     * not hide the next to arrive. More, and there is something new: nothing
     * is remembered and the number is shown.
     */
    static int remembered(int dismissed, int now) {
        if (dismissed <= 0 || now <= 0 || now > dismissed) return 0;
        return now;
    }
}
