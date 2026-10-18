package com.ss.android.ugc.aweme.feed.model;

import com.ss.android.ugc.aweme.base.model.UrlModel;

/**
 * The video of a post.
 *
 * Two of its addresses matter here and they are both TikTok's own: the one the
 * download button uses, which is stamped, and the one beside it, which is not.
 * Same type, same shape, which is what makes swapping them a rewrite rather
 * than a rebuild.
 */
public class Video {

    public UrlModel getDownloadAddr() {
        throw new UnsupportedOperationException("stub");
    }

    public UrlModel getDownloadNoWatermarkAddr() {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * The stream the video is played from, and the one that is never stamped:
     * the watermark is drawn for the download and for nothing else.
     */
    public VideoUrlModel getPlayAddr() {
        throw new UnsupportedOperationException("stub");
    }
}
