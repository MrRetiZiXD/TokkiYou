package cat.narezany.margyt;

/**
 * Written by the build. Do not edit.
 *
 * Where the methods the mod hands calls back to actually live in the
 * apk it was built from. Their signatures are in the patcher and are
 * the same every release; these names are not, which is why they are
 * found rather than written down.
 */
final class Anchors {

    private Anchors() {}

    /** comment sticker tapped */
    static final String COMMENT_STICKER_TAPPED = "X.0IKI";
    static final String COMMENT_STICKER_TAPPED_METHOD = "LIZ";

    /** comment sticker sheet */
    static final String COMMENT_STICKER_SHEET = "X.0IKC";
    static final String COMMENT_STICKER_SHEET_METHOD = "LJ";
}
