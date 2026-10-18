package cat.narezany.tokkiyou;

/**
 * Whether this phone may publish to the plugin store. It may not.
 *
 * The store is filled from the server's own panel, by whoever runs the server,
 * and nothing an apk can say about itself is worth trusting with that. The
 * editor still asks, so that the answer lives in one place.
 */
final class AdminAccess {

    private AdminAccess() {}

    static boolean current() {
        return false;
    }
}
