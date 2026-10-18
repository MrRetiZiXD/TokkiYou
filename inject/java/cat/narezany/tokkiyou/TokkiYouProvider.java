package cat.narezany.tokkiyou;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;

/**
 * The mod's way into a running TikTok, and it is not a patch at all.
 *
 * Android instantiates every content provider an app declares before the
 * application's own onCreate, whether anything ever queries it or not. So a
 * provider that answers nothing is a start-up hook TikTok's code knows nothing
 * about and cannot move: no Application class to patch, no method to find again
 * after the next release.
 *
 * All it does is remember the context and ask to be told when an activity
 * appears.
 */
public final class TokkiYouProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) return true;
        TokkiYou.attach(context);
        try { CrashReports.start(context); } catch(Throwable ignored) {}
        if (!Version.LITE) SocialLibrary.start();
        Diary.note("start-up hook ran");
        try {
            Context application = context.getApplicationContext();
            if (application instanceof Application) {
                ((Application) application).registerActivityLifecycleCallbacks(new SettingsRow());
                Watch.start((Application) application);
                Lag.watch();
                Diary.note("watching for the settings screen");
            } else {
                Diary.note("no application yet: " + application);
            }
        } catch (Throwable error) {
            // the mod failing to start is not a reason for the app not to
            Diary.note("hook failed: " + error);
        }
        try {
            Badges.start(context);
        } catch (Throwable error) {
            Diary.note("badges failed to start: " + error);
        }
        try {
            if (!Version.LITE) Streaks.start(context);
        } catch (Throwable error) {
            Diary.note("streaks failed to start: " + error);
        }
        try {
            if (!Version.LITE) Updater.start(context);
        } catch (Throwable error) {
            Diary.note("updates failed to start: " + error);
        }
        try {
            Plugins.startAll(context);
        } catch (Throwable error) {
            Diary.note("plugins failed to start: " + error);
        }
        return true;
    }

    // ------------------------------------------------- handing over a file

    /**
     * A content uri for a file of the mod's own.
     *
     * Android will not install an apk from a path any more; it wants a uri it
     * can be granted read on. A FileProvider is the usual answer and it needs
     * an xml resource, which this build cannot add -- but a provider is a
     * provider, and this one is already declared, so it serves the file
     * itself.
     */
    public static Uri share(Context context, File file) {
        try {
            if (!file.isFile()) return null;
            return Uri.parse("content://" + context.getPackageName() + ".tokkiyou/"
                    + file.getName());
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) {
        try {
            Context context = getContext();
            if (context == null) return null;
            String name = uri.getLastPathSegment();
            // one directory, no traversal, read only: the installer needs the
            // update and has no business anywhere else
            if (name == null || name.contains("/") || name.contains("..")) return null;
            File file = new File(new File(context.getFilesDir(), "tokkiyou"), name);
            if (!file.isFile()) return null;
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (Throwable error) {
            Diary.note("share: " + error);
            return null;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
