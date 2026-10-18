package com.ss.android.ugc.aweme.feed.model;

/**
 * What a post allows.
 *
 * `allowDownload` is a boxed Boolean rather than a boolean: TikTok's model
 * distinguishes "not allowed" from "the server said nothing", and the field is
 * read straight out of the object rather than through a getter.
 */
public class VideoControl {

    public Boolean allowDownload;
    public int preventDownloadType;
}
