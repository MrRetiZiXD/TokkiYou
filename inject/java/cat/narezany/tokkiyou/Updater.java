package cat.narezany.tokkiyou;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.util.Locale;

/**
 * Telling you there is a newer TokkiYou, and putting it on if you say so.
 *
 * `version.json` in the repository says what the latest is and where its apk
 * lives. It is read at startup and every five minutes, exactly as the badges
 * are, so an update announces itself without anybody being told to look.
 *
 * Refusing is remembered for as long as the app is running and no longer: the
 * offer comes back the next time TikTok starts, because a version worth
 * shipping is worth mentioning twice, and it does not come back five minutes
 * later, because that is nagging. Switching the reminders off in the settings
 * silences it for good; the check by hand still works.
 *
 * Nothing installs itself. The apk is fetched only after the button is
 * pressed, and Android asks its own question before anything is installed --
 * the mod cannot and does not answer that one.
 */
public final class Updater {

    private Updater() {}

    private static final String SOURCE =
            "https://raw.githubusercontent.com/MrRetiZiXD/TokkiYou/main/version.json";

    public static final String KEY_REMIND = "update_remind";

    /** Which version the apk sitting in files/ is, so it can be thrown away. */
    private static final String KEY_GOT = "update_got";

    private static final long EVERY = 5 * 60 * 1000L;

    private static volatile String latest;
    private static volatile String where;
    private static volatile String notes;
    private static volatile String[] features = new String[0];
    private static volatile boolean forceUpdate;

    /** Refused in this run of the app, so it is not asked again until a restart. */
    private static volatile boolean refused;
    private static volatile boolean started;

    // -------------------------------------------------------------- knowing

    public static synchronized void start(final Context context) {
        if (SOURCE.length() == 0) return;
        if (started) return;
        started = true;
        tidy(context);

        final Handler handler = new Handler(Looper.getMainLooper());
        final Context application = context.getApplicationContext();
        handler.post(new Runnable() {
            @Override
            public void run() {
                check(application, false);
                handler.postDelayed(this, EVERY);
            }
        });
    }

    /**
     * Ask the repository what the latest is.
     *
     * `byHand` is the button in the settings: it says something either way,
     * where the automatic check says nothing unless there is news.
     */
    public static void check(final Context context, final boolean byHand) {
        if (SOURCE.length() == 0) {
            if (byHand) Screen.say(Text.UPDATE_UNAVAILABLE);
            return;
        }
        Net.away("update", new Runnable() {
            @Override
            public void run() {
                String json = Net.text(SOURCE);
                if (json == null) {
                    if (byHand) Screen.say(Text.UPDATE_NO_ANSWER);
                    return;
                }
                try {
                    JSONObject root = new JSONObject(json);
                    latest = root.optString("version", "");
                    where = root.optString("url", "");
                    notes = localised(root, "notes");
                    features = stringArray(root, localizedArrayField());
                    forceUpdate = root.optBoolean("force_update", false);
                } catch (Throwable error) {
                    Diary.note("update: " + error);
                    if (byHand) Screen.say(Text.UPDATE_NO_ANSWER);
                    return;
                }

                if (!newer()) {
                    if (byHand) Screen.say(Text.UPDATE_NONE);
                    return;
                }
                if (!byHand && !forceUpdate && (refused || !remind(context))) return;
                offer(context);
            }
        });
    }

    /** Whether what the repository has is ahead of what is installed. */
    public static boolean newer() {
        return compare(latest, Version.MOD) > 0 && where != null && where.length() > 0;
    }

    public static String latest() {
        return latest;
    }

    /**
     * Compare two dotted versions by their numbers.
     *
     * "0.9" is behind "0.15", which a string comparison gets backwards, and
     * getting that backwards means either never offering an update or offering
     * one forever.
     */
    static int compare(String a, String b) {
        if (a == null) return -1;
        if (b == null) return 1;
        String[] left = a.split("\\."), right = b.split("\\.");
        int most = Math.max(left.length, right.length);
        for (int i = 0; i < most; i++) {
            int one = number(left, i), two = number(right, i);
            if (one != two) return one < two ? -1 : 1;
        }
        return 0;
    }

    private static int number(String[] parts, int at) {
        if (at >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[at].trim());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String localised(JSONObject root, String field) {
        String language = Locale.getDefault().getLanguage();
        String translated = root.optString(field + "_" + language, "");
        return translated.length() > 0 ? translated : root.optString(field, "");
    }

    private static String localizedArrayField() {
        String language = Locale.getDefault().getLanguage();
        return "ru".equals(language) ? "features_ru"
                : "uk".equals(language) ? "features_uk" : "features";
    }

    static String[] stringArray(JSONObject root, String field) {
        org.json.JSONArray array = root.optJSONArray(field);
        if (array == null && !"features".equals(field)) array = root.optJSONArray("features");
        if (array == null) return new String[0];
        java.util.ArrayList<String> result = new java.util.ArrayList<String>();
        for (int i = 0; i < array.length() && i < 20; i++) {
            String item = array.optString(i, "").trim();
            if (item.length() > 0) result.add(item);
        }
        return result.toArray(new String[result.size()]);
    }

    public static boolean forceUpdate() {
        return forceUpdate;
    }

    // --------------------------------------------------------------- asking

    public static boolean remind(Context context) {
        try {
            return prefs(context).getBoolean(KEY_REMIND, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setRemind(Context context, boolean on) {
        try {
            prefs(context).edit().putBoolean(KEY_REMIND, on).apply();
        } catch (Throwable ignored) {
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE);
    }

    public static void offer(final Context context) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                android.app.Activity activity = Screen.now();
                if (activity == null || activity.isFinishing()) return;
                String message = Text.UPDATE_THERE_IS + " " + latest
                        + (notes == null || notes.length() == 0 ? "" : "\n\n" + notes);
                String[] added = features;
                if (added.length > 0) {
                    StringBuilder bullets = new StringBuilder("\n\n").append(Text.UPDATE_FEATURES);
                    for (String feature : added) bullets.append("\n• ").append(feature);
                    message += bullets.toString();
                }
                Popup.askUpdate(activity, Text.UPDATE, message,
                        Text.UPDATE_GET, new Runnable() {
                            @Override
                            public void run() {
                                fetch(context);
                            }
                        },
                        forceUpdate ? null : Text.UPDATE_LATER, new Runnable() {
                            @Override
                            public void run() {
                                refused = true;
                            }
                        },
                        forceUpdate ? null : Text.UPDATE_NEVER, new Popup.Ticked() {
                            @Override
                            public void ticked(boolean on) {
                                setRemind(context, !on);
                            }
                        }, forceUpdate);
            }
        });
    }

    // ------------------------------------------------------------- fetching

    /** Where the downloaded apk waits. Inside the app's own files, not the card. */
    public static File file(Context context) {
        return new File(context.getFilesDir(), "tokkiyou/update.apk");
    }

    private static final java.util.concurrent.atomic.AtomicBoolean fetching=new java.util.concurrent.atomic.AtomicBoolean();
    public static void fetch(Context caller) {
        if(!fetching.compareAndSet(false,true))return;
        final Context context=caller.getApplicationContext();
        final String url=where;
        final File apk = file(context);
        final DownloadTasks.Control control=DownloadTasks.begin("update",Local.of("Обновление TokkiYou"),Local.of("Мод"),()->fetch(context));
        Net.away("update apk", new Runnable() {
            @Override
            public void run() {
                boolean downloaded=false;
                try {
                Screen.progress("update",Text.UPDATE_GETTING, 0);
                boolean done = Net.download(url, apk, new Net.Along() {
                    @Override
                    public void at(int percent, long got, long total) {
                        DownloadTasks.progress("update",percent);Screen.progress("update",Text.UPDATE_GETTING, percent);
                    }
                },control);
                if (!done) {
                    if(!control.cancelled())Screen.say(Text.UPDATE_FAILED);
                    apk.delete();
                    return;
                }
                downloaded=true;
                try {
                    context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE)
                            .edit().putString(KEY_GOT, latest()).apply();
                } catch (Throwable ignored) {
                }
                install(context);
                } finally {fetching.set(false);DownloadTasks.finish("update",downloaded);if(downloaded)Screen.progressDone("update");else Screen.progressGone("update");}
            }
        });
    }

    /**
     * Hand the apk to Android, which asks its own questions.
     *
     * Before it will ask them at all, the person has to have allowed this app
     * to install others -- a switch in the system settings that only they can
     * turn. If it is off, that screen is opened instead, and the install is
     * offered again from the mod's settings afterwards.
     */
    public static void install(final Context context) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (Build.VERSION.SDK_INT >= 26
                            && !context.getPackageManager().canRequestPackageInstalls()) {
                        // Asking for the permission means leaving the app, and
                        // what used to happen is that coming back forgot the
                        // whole thing: the apk sat on disk, downloaded, and
                        // nothing ever offered to put it on again. So this
                        // remembers that an install was underway, and the
                        // first screen that comes up afterwards picks it up.
                        waitingOnPermission = true;
                        Screen.say(Text.UPDATE_ALLOW);
                        Intent allow = new Intent(
                                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + context.getPackageName()));
                        allow.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(allow);
                        return;
                    }
                    waitingOnPermission = false;

                    Uri apk = TokkiYouProvider.share(context, file(context));
                    if (apk == null) {
                        Screen.say(Text.UPDATE_FAILED);
                        return;
                    }
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(apk, "application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(intent);
                } catch (Throwable error) {
                    Diary.note("install: " + error);
                    Screen.say(Text.UPDATE_FAILED);
                }
            }
        });
    }

    /** Set while the person is away granting the permission the install needs. */
    private static volatile boolean waitingOnPermission;

    /**
     * A screen came up. If an install was interrupted to ask for permission
     * and the permission is now there, carry on with it.
     *
     * Called for every screen of the app, so it answers in two comparisons
     * unless something is actually waiting.
     */
    public static void resumed(Context context) {
        if (!waitingOnPermission) return;
        try {
            if (Build.VERSION.SDK_INT >= 26
                    && !context.getPackageManager().canRequestPackageInstalls()) {
                return;  // still not granted; it can stay pending
            }
            if (!waiting(context)) {
                waitingOnPermission = false;
                return;
            }
            waitingOnPermission = false;
            Diary.note("install: the permission is there, carrying on");
            install(context);
        } catch (Throwable error) {
            Diary.note("install: " + error);
        }
    }

    /** Whether an apk is already waiting, so the settings can offer to put it on. */
    public static boolean waiting(Context context) {
        File apk = file(context);
        return apk.isFile() && apk.length() > 0 && ahead(context);
    }

    /** The version of the waiting apk, as it was when it came down. */
    private static String got(Context context) {
        try {
            return context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_GOT, "");
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean ahead(Context context) {
        String which = got(context);
        return which.length() > 0 && compare(which, Version.MOD) > 0;
    }

    /**
     * Throw away an update that has already been installed.
     *
     * It is a third of a gigabyte inside the app's own files. Nothing ever
     * deleted it, so it sat there for good and the settings went on offering
     * to install the version that was already running.
     */
    static void tidy(Context context) {
        try {
            File apk = file(context);
            if (!apk.isFile()) return;
            if (ahead(context)) return;
            if (apk.delete()) Diary.note("update: the old download is gone");
            context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE)
                    .edit().remove(KEY_GOT).apply();
        } catch (Throwable error) {
            Diary.note("update: " + error);
        }
    }
}
