package com.ss.android.ugc.profile.platform.base.data;

/**
 * An account as a loaded profile holds it.
 *
 * A profile is drawn twice: once from the `User` the feed already had, and
 * again from this once the profile itself has been fetched. Both are TikTok's
 * own names, and the second one is the reason a badge used to appear while a
 * profile loaded and vanish the moment it finished.
 */
public class UserProfileInfo {

    public String getNickname() {
        throw new UnsupportedOperationException("stub");
    }

    public String getUid() {
        throw new UnsupportedOperationException("stub");
    }
}
