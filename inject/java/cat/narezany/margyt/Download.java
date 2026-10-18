package cat.narezany.margyt;

import android.content.SharedPreferences;

import com.ss.android.ugc.aweme.base.model.UrlModel;
import com.ss.android.ugc.aweme.feed.model.ACLCommonShare;
import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.feed.model.PhotoModeImageUrlModel;
import com.ss.android.ugc.aweme.feed.model.Video;
import com.ss.android.ugc.aweme.feed.model.VideoControl;

import java.util.List;
import com.ss.android.ugc.aweme.profile.model.User;

/**
 * Saving a video without the stamp burned into it.
 *
 * TikTok's own model carries two addresses for the same video:
 *
 *     Video.getDownloadAddr()             -- the one the save button uses
 *     Video.getDownloadNoWatermarkAddr()  -- the one beside it, unstamped
 *
 * Both are real, unobfuscated names, both return the same type, and the second
 * is TikTok's, not ours: the app already models it, for the cases where it
 * serves the file clean itself. So the swap is a rewrite of the call site --
 * the same 35c instruction, the same register count, the same return type --
 * rather than anything that has to understand the download.
 *
 * It is off by default and it falls back on itself: when the switch is off, or
 * when the unstamped address is missing for this post, what comes back is
 * exactly what TikTok asked for. A post whose clean address the server did not
 * send still saves, stamped, instead of failing to save at all.
 */
public final class Download {

    private Download() {}

    static void reloadSettings(){cached=cachedAlways=null;}
    public static final String KEY = "download_no_watermark";
    public static final String KEY_ALWAYS = "download_always";

    private static volatile Boolean cached;
    private static volatile Boolean cachedAlways;

    public static boolean isEnabled() {
        Boolean known = cached;
        if (known != null) return known;
        Boolean read = read(KEY);
        if (read == null) return false;  // too early to know; do not cache it
        cached = read;
        return read;
    }

    public static void setEnabled(boolean enabled) {
        cached = enabled;
        write(KEY, enabled);
    }

    /** Whether the save button is put back on the posts that hid it. */
    public static boolean isAlways() {
        Boolean known = cachedAlways;
        if (known != null) return known;
        Boolean read = read(KEY_ALWAYS);
        if (read == null) return false;
        cachedAlways = read;
        return read;
    }

    public static void setAlways(boolean enabled) {
        cachedAlways = enabled;
        write(KEY_ALWAYS, enabled);
    }

    private static Boolean read(String key) {
        SharedPreferences prefs = prefs();
        if (prefs == null) return null;
        try {
            return prefs.getBoolean(key, false);
        } catch (Throwable ignored) {
            return Boolean.FALSE;
        }
    }

    private static void write(String key, boolean value) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(key, value).apply();
    }

    private static SharedPreferences prefs() {
        try {
            android.content.Context context = Margy.context();
            if (context == null) return null;
            return context.getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------ where the call lands

    /**
     * Where every `Video.getDownloadAddr()` in TikTok's bytecode now goes.
     *
     * The receiver arrives as the first argument, which is what keeps the
     * rewrite from having to renumber anything around it.
     */
    public static UrlModel getDownloadAddr(Video video) {
        if (video == null) return null;
        UrlModel stamped = video.getDownloadAddr();
        if (!isEnabled()) return stamped;
        try {
            UrlModel clean = video.getDownloadNoWatermarkAddr();
            if (usable(clean)) return (UrlModel)DownloadQuality.video(video,clean);

            // The server does not always send a clean address, and for a while
            // that was the end of it -- which is why this saved a stamped video
            // often enough to look broken. It is not the end of it: the stream
            // the video is *played* from carries no stamp either. The watermark
            // is drawn for the download and for nothing else, so the thing on
            // screen is already the clean copy.
            UrlModel playing = video.getPlayAddr();
            if (usable(playing)) return (UrlModel)DownloadQuality.video(video,playing);
        } catch (Throwable ignored) {
            // a release that moved either address is a release that saves the
            // stamped one, not one that cannot save at all
        }
        return stamped;
    }

    /** An address with nowhere to fetch from is not an address. */
    private static boolean usable(UrlModel url) {
        if (url == null) return false;
        try {
            List urls = url.getUrlList();
            return urls != null && !urls.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ------------------------------------------- what a post says is allowed

    /**
     * The post's own permission to be saved and shared.
     *
     * Three numbers on a real class, and TikTok reads all three before it will
     * offer a download. With the switch on they say yes: nothing is bypassed
     * on the server, the app simply stops refusing on its own.
     */
    public static int getCode(ACLCommonShare acl) {
        if (acl == null) return 0;
        return isAlways() ? 0 : acl.getCode();
    }

    public static int getShowType(ACLCommonShare acl) {
        if (acl == null) return 2;
        return isAlways() ? 2 : acl.getShowType();
    }

    public static int getTranscode(ACLCommonShare acl) {
        if (acl == null) return 1;
        return isAlways() ? 1 : acl.getTranscode();
    }

    // ------------------------------------------------------- photo posts

    /**
     * A photo post keeps three versions of every image: one stamped with the
     * account that posted it, one stamped with the account looking at it --
     * which is the "from a TikTok comment" line as well -- and one with no
     * stamp at all, sitting right there beside them.
     *
     * A slideshow is not a video and never went near `getDownloadAddr`, which
     * is why the first build of this saved photos stamped while it saved
     * videos clean.
     */
    public static UrlModel ownerWatermarkImage(PhotoModeImageUrlModel image) {
        return clean(image, image == null ? null : image.ownerWatermarkImage);
    }

    public static UrlModel userWatermarkImage(PhotoModeImageUrlModel image) {
        return clean(image, image == null ? null : image.userWatermarkImage);
    }

    private static UrlModel clean(PhotoModeImageUrlModel image, UrlModel stamped) {
        if (image == null || !isEnabled()) return stamped;
        try {
            UrlModel plain = image.displayImageNoWatermark;
            if (plain != null) return plain;
        } catch (Throwable ignored) {
        }
        return stamped;
    }

    // ------------------------------------------- the ban on saving at all

    /**
     * The post's own ban, answered no when the switch is on.
     *
     * A new account has this set for it without being told: TikTok says out
     * loud that the posts are friends-only, and says nothing at all about
     * having turned saving off as well. And the ban stops nothing -- the
     * screen recorder is right there -- so what it costs is a working button,
     * not a copy of the video.
     */
    public static boolean isPreventDownload(Aweme aweme) {
        if (aweme == null) return false;
        if (isAlways()) return false;
        return aweme.isPreventDownload();
    }

    /** The same ban, set on the account rather than the post. */
    public static boolean isPreventDownload(User user) {
        if (user == null) return false;
        if (isAlways()) return false;
        return user.isPreventDownload();
    }

    /**
     * `VideoControl.allowDownload`, which is a field rather than a getter and
     * is boxed: null is the server having said nothing, which is not the same
     * as a no, and is left alone as carefully as a no is overridden.
     */
    public static Boolean allowDownload(VideoControl control) {
        if (control == null) return null;
        if (isAlways()) return Boolean.TRUE;
        return control.allowDownload;
    }
}
