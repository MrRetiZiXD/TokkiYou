package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;

import com.ss.android.ugc.aweme.base.model.UrlModel;
import com.ss.android.ugc.aweme.profile.model.User;

import java.util.List;

/**
 * Saving somebody's avatar, which TikTok offers no way to do.
 *
 * The picture itself is easy: `User` has the address of it at four sizes, all
 * under real names. What is not easy is knowing which account is on screen
 * when the enlarged avatar is open -- the screen keeps it in a field whose
 * name the obfuscator invents afresh every release.
 *
 * So the mod does not ask the screen. It listens instead: every read of an
 * avatar address passes through here on its way to being drawn, and the last
 * one is remembered. By the time a picture is filling the screen, the address
 * of that picture is the last one anybody asked for.
 */
public final class Avatars {
    static void reloadSettings(){cached=null;}

    private Avatars() {}

public static final String KEY = "save_avatars";

    private static volatile Boolean cached;

    public static boolean isEnabled() {
        Boolean known = cached;
        if (known != null) return known;
        try {
            android.content.Context context = Margy.context();
            if (context == null) return true;  // on until there is somewhere to read from
            boolean on = context.getSharedPreferences(Margy.PREFS,
                    android.content.Context.MODE_PRIVATE).getBoolean(KEY, true);
            cached = on;
            return on;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setEnabled(boolean enabled) {
        cached = enabled;
        try {
            android.content.Context context = Margy.context();
            if (context == null) return;
            context.getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY, enabled).apply();
        } catch (Throwable ignored) {
        }
    }

    private static volatile UrlModel latest;
    private static volatile String latestUid;

    // ------------------------------------------------ where the reads land

    public static UrlModel getAvatarLarger(User user) {
        return remember(user, user == null ? null : user.getAvatarLarger());
    }

    public static UrlModel getAvatar300(User user) {
        return remember(user, user == null ? null : user.getAvatar300());
    }

    public static UrlModel getAvatarMedium(User user) {
        return remember(user, user == null ? null : user.getAvatarMedium());
    }

    /** Capture the signed-in person's real avatar as soon as TikTok exposes it. */
    public static void rememberCurrent(User user) {
        if (user == null) return;
        try {
            UrlModel url = user.getAvatarLarger();
            if (!usable(url)) url = user.getAvatar300();
            if (!usable(url)) url = user.getAvatarMedium();
            remember(user, url);
        } catch (Throwable error) {
            Diary.note("current profile avatar: " + error);
        }
    }

    private static UrlModel remember(User user, UrlModel url) {
        if (url == null) return null;
        try {
            if (usable(url)) {
                latest = url;
                latestUid = user == null ? null : user.getUid();
                if (user != null) Account.rememberAvatar(user.getUid(), first(url));
            }
        } catch (Throwable ignored) {
        }
        return url;
    }

    private static boolean usable(UrlModel url) {
        try {
            List urls = url.getUrlList();
            return urls != null && !urls.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean have() {
        return latest != null;
    }

    // ----------------------------------------------------------- saving it

    /** Fetch the last avatar seen and put it in the gallery. */
    public static void save(final Activity activity) {
        final UrlModel url = latest;
        final String uid = latestUid;
        if (url == null) {
            Popup.show(activity, null, Text.AVATAR_NOTHING, null);
            return;
        }
        final Context context = activity.getApplicationContext();
        Net.away("avatar", new Runnable() {
            @Override
            public void run() {
                String address = first(url);
                byte[] data = address == null ? null : Net.bytes(address);
                final String at = data == null ? null : Gallery.save(context, data,
                        Gallery.name("avatar_" + (uid == null ? "tiktok" : uid), address, "jpg"),
                        "image/jpeg");
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Popup.show(activity, null,
                                at == null ? Text.AVATAR_FAILED : Text.SAVED, null);
                    }
                });
            }
        });
    }

    private static String first(UrlModel url) {
        try {
            List urls = url.getUrlList();
            if (urls == null || urls.isEmpty()) return null;
            return String.valueOf(urls.get(0));
        } catch (Throwable ignored) {
            return null;
        }
    }
}
