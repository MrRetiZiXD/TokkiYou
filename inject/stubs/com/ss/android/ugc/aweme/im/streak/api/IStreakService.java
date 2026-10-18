package com.ss.android.ugc.aweme.im.streak.api;

/**
 * TikTok's own streak service, in outline.
 *
 * The name of the interface is real and so are the signatures; the three
 * method names are not, and they are this release's. They are written here
 * because a call rewritten into the mod has to be handed back with the same
 * types the verifier saw, and that needs a declaration to compile against.
 *
 * Nothing else in the mod names them. The rules in `dexpatch` are the only
 * other place, and the build reports how many call sites each one matched, so
 * a release that renames them shows up as a zero rather than as silence.
 */
public interface IStreakService {

    /** The streak on a conversation, or null when there is none. */
    StreakData J(String conversation, boolean fresh);

    /** Whether that conversation has a streak at all. */
    boolean a0(String conversation);

    /** Whether the streak should be shown on it. */
    boolean h0(String conversation, boolean flag);

    /** The rest are other questions about one conversation, listened to only
     *  so that the conversation is known about at all. */
    int w(String conversation);

    boolean X(String conversation);

    boolean Y(String conversation);

    Integer l0(String conversation);

    String O(String conversation);
}
