package com.ss.android.ugc.aweme.feed.model;

import com.ss.android.ugc.aweme.base.model.UrlModel;

/**
 * One image of a photo post, in three versions.
 *
 * TikTok keeps the clean image beside the stamped ones rather than instead of
 * them, and all three are public fields of the same type -- which is what lets
 * the mod hand back one where the app asked for another.
 */
public class PhotoModeImageUrlModel {

    /** No stamp at all. */
    public UrlModel displayImageNoWatermark;

    /** Stamped with the account that posted it. */
    public UrlModel ownerWatermarkImage;

    /** Stamped with the account looking at it, and with where it came from. */
    public UrlModel userWatermarkImage;
}
