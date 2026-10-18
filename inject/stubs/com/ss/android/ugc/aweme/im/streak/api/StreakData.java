package com.ss.android.ugc.aweme.im.streak.api;

/**
 * One streak, as the server describes it.
 *
 * Every name here is TikTok's own, and the fields are public and final on the
 * real class. `convId` says which conversation, `activeBefore` and `endAt` say
 * when the streak stops counting -- which together are the whole of what
 * keeping one alive needs to know.
 */
public class StreakData {

    public String convId;
    public long activeBefore;
    public long endAt;
    public Integer level;

    /** How many days in a row. A flame is shown from the third. */
    public int streak;
}
