package com.ss.android.ugc.aweme.music.model;

/**
 * A sound, and what TikTok will let you do with it.
 *
 * A track pulled for copyright is not removed -- the video stays and the sound
 * is switched off, through these four answers.
 */
public class Music {

    public boolean available() {
        throw new UnsupportedOperationException("stub");
    }

    public int getMusicStatus() {
        throw new UnsupportedOperationException("stub");
    }

    public boolean isMuteShare() {
        throw new UnsupportedOperationException("stub");
    }

    public int getMuteType() {
        throw new UnsupportedOperationException("stub");
    }
}
