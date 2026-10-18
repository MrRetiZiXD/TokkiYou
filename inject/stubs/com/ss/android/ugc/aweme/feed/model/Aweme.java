package com.ss.android.ugc.aweme.feed.model;

/** A post in the feed: a video, its author, and what may be done with it. */
public class Aweme {

    /** Whether TikTok counts this post as an advertisement. */
    public boolean isAd() {
        throw new UnsupportedOperationException("stub");
    }

    /** The per-post ban on saving it. */
    public boolean isPreventDownload() {
        throw new UnsupportedOperationException("stub");
    }

    /** What kind of post it is. TikTok's own numbering; 101 is a live room. */
    public int getAwemeType() {
        throw new UnsupportedOperationException("stub");
    }

    /** Set on a post that is a live room rather than a recording. */
    public long getLiveId() {
        throw new UnsupportedOperationException("stub");
    }

    /** Set on a post that stands for a live room rather than a video. */
    public com.ss.android.ugc.aweme.feed.model.live.RoomFeedCellStruct
            getRoomFeedCellStruct() {
        throw new UnsupportedOperationException("stub");
    }

    /** There on a slideshow and nowhere else, which is how one is known. */
    public String getDesc() {
        throw new RuntimeException("stub");
    }

    public String getAuthorUid() {
        throw new RuntimeException("stub");
    }

    public PhotoModeImageInfo getPhotoModeImageInfo() {
        throw new UnsupportedOperationException("stub");
    }
}
