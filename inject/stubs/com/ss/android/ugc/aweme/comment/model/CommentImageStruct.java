package com.ss.android.ugc.aweme.comment.model;

import com.ss.android.ugc.aweme.base.model.UrlModel;

/**
 * An image attached to a comment.
 *
 * Two addresses and no clean-versus-stamped pair, unlike a photo post: what
 * the stamp is doing here has to be worked out from the addresses themselves.
 */
public class CommentImageStruct {

    public UrlModel getCropUrl() {
        throw new UnsupportedOperationException("stub");
    }

    public UrlModel getOriginUrl() {
        throw new UnsupportedOperationException("stub");
    }
}
