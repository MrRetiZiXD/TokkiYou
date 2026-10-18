package cat.narezany.tokkiyou;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Wakes the streak round at the chosen minute, whatever the app is doing.
 *
 * A Handler counts uptime, and uptime stands still while the phone sleeps, so
 * a round scheduled on one drifts or never comes. AlarmManager is the system's
 * own clock: an exact "while idle" alarm fires on time even in Doze, starts
 * the process if it was killed, and this receiver (declared in the manifest by
 * the build) runs the round under a short wake lock.
 *
 * Without a connection nothing is sent and nothing is marked as sent: the
 * round waits for the network (a system callback wakes it the moment one
 * appears) and, once a send goes out, watches the flame to see it take.
 * A conversation still grey a few minutes later gets another send, three at most a day.
 *
 * If TikTok's streak service is not there yet -- the process was just started
 * and nothing has asked it anything -- the round waits a little for it, retries
 * every five minutes for three hours, and finally leaves a notification that
 * opens TikTok, where the service appears and the round runs by itself.
 */
public final class StreakAlarm extends BroadcastReceiver {

    static final String FIRE = "cat.narezany.tokkiyou.STREAK_FIRE";
    /** The network came back while a send was waiting for it. */
    static final String NET = "cat.narezany.tokkiyou.STREAK_NET";
    private static final int REQUEST = 0x5712;
    private static final long CADENCE = 15 * 60 * 1000L;
    /** One short wait for TikTok's service; the round is retried rather than held. */
    private static final long WAIT = 1500L;
    private static final String CHANNEL = "tokkiyou_streak";
    /** The last alarm the system actually took, so the screen can say which kind it was. */
    private static volatile long exactAt;
    private static volatile String lastMode;
    private static volatile long lastDenied;
    /** When the alarm was last put in, so the settings can say how fresh the chain is. */
    static final String KEY_ARMED = "streak_armed_at";

    /** Whether the system will wake the app at the minute asked for, to the second. */
    static boolean exactAllowed(Context c) {
        try {
            if (Build.VERSION.SDK_INT < 31) return true;
            AlarmManager alarms = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            return alarms != null && alarms.canScheduleExactAlarms();
        } catch (Throwable error) {
            return false;
        }
    }

    /** What happened to the alarm the last time one was set. */
    static String lastMode() {
        return lastMode;
    }

    /** When the alarm last went in exactly, or 0 when it never did. */
    static long lastExactAt() {
        return exactAt;
    }

    /** Whether the app may put a notification on the screen at all. */
    static boolean notificationsAllowed(Context c) {
        try {
            if (Build.VERSION.SDK_INT < 33) return true;
            return c.checkPermission(Manifest.permission.POST_NOTIFICATIONS, android.os.Process.myPid(),
                    android.os.Process.myUid()) == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** The channel the fallback notification uses, made early so the first one is never dropped. */
    static void channel(Context c) {
        try {
            if (Build.VERSION.SDK_INT < 26) return;
            NotificationManager manager = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null || manager.getNotificationChannel(CHANNEL) != null) return;
            // High importance, because this notification exists for the case where
            // everything else failed and the streak is about to be lost.
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL, Local.of("Серии"), NotificationManager.IMPORTANCE_HIGH));
        } catch (Throwable error) {
            Diary.note("streak channel: " + error);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        String action = intent == null ? null : intent.getAction();
        final boolean fire = FIRE.equals(action) || NET.equals(action);
        final PendingResult result = goAsync();
        Thread thread = new Thread(() -> {
            PowerManager.WakeLock lock = null;
            try {
                try {
                    PowerManager power = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                    lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tokkiyou:streak");
                    lock.acquire(60 * 1000L);
                } catch (Throwable ignored) {
                }
                // First the next alarm, so that nothing below can break the chain.
                schedule(app, false);
                if (fire && Streaks.isEnabled()) {
                    boolean retry = attempt(app);
                    if (retry) schedule(app, true);
                }
            } catch (Throwable error) {
                Diary.note("streak alarm: " + error);
            } finally {
                try { if (lock != null && lock.isHeld()) lock.release(); } catch (Throwable ignored) { }
                result.finish();
            }
        }, "tokkiyou-streak-alarm");
        thread.setDaemon(true);
        thread.start();
    }

    /** One round; true when it should be tried again soon. */
    private static boolean attempt(Context app) {
        // A round that is retried is better than a round that is held: the alarm
        // goes back in first, and the next one picks the work up, so a service
        // that is late cannot swallow the send.
        // Wait before the round: waiting afterwards never reran the failed round.
        boolean ready = Streaks.ready(WAIT);
        boolean retry = Streaks.runNow(app);
        if (retry && !ready && StreakSchedule.minute() >= 0) nudge(app);
        if (retry) watchNetwork(app); else unwatchNetwork(app);
        return retry;
    }

    // --------------------------------------------------------------- network

    /** Whether there is a connection to send through; when unsure, say yes and let the send decide. */
    static boolean online(Context c) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            if (Build.VERSION.SDK_INT >= 23) {
                android.net.Network network = cm.getActiveNetwork();
                if (network == null) return false;
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                return caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Throwable error) {
            return true;
        }
    }

    private static PendingIntent netIntent(Context c) {
        Intent i = new Intent(c, StreakAlarm.class).setAction(NET);
        int f = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        return PendingIntent.getBroadcast(c, REQUEST + 2, i, f);
    }

    /** Ask the system to wake the app the moment a network appears (Android 8 and up; older ones rely on the retries). */
    static void watchNetwork(Context c) {
        try {
            if (Build.VERSION.SDK_INT < 26 || online(c)) return;
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            cm.registerNetworkCallback(new android.net.NetworkRequest.Builder()
                    .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), netIntent(c));
        } catch (Throwable error) {
            Diary.note("streak network watch: " + error);
        }
    }

    static void unwatchNetwork(Context c) {
        try {
            if (Build.VERSION.SDK_INT < 26) return;
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(netIntent(c));
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------ scheduling

    private static PendingIntent intent(Context c, int flags) {
        Intent i = new Intent(c, StreakAlarm.class).setAction(FIRE);
        int f = flags | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(c, REQUEST, i, f);
    }

    /** Whether an alarm is already waiting. */
    static boolean pending(Context c) {
        return intent(c, PendingIntent.FLAG_NO_CREATE) != null;
    }

    /** Set (or replace) the one alarm: the next chosen minute, or a short retry. */
    static void schedule(Context c, boolean retry) {
        Streaks.rescheduleForeground();
        try {
            AlarmManager alarms = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (alarms == null) return;
            PendingIntent pi = intent(c, PendingIntent.FLAG_UPDATE_CURRENT);
            if (!Streaks.isEnabled()) {
                alarms.cancel(pi);
                lastMode = "off";
                return;
            }
            long at = StreakSchedule.next(System.currentTimeMillis(), StreakSchedule.minute(), retry, CADENCE);
            // When the alarm was last put in, so a chain broken while the process
            // was dead -- a reboot, a force stop -- is visible to the next round.
            try {
                c.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE).edit()
                        .putLong(KEY_ARMED, System.currentTimeMillis()).apply();
            } catch (Throwable ignored) {
            }
            // What matters is not which call is made but whether the system took
            // an exact one: a downgraded alarm is allowed to slide by up to an
            // hour in Doze, and a streak kept an hour late is a streak lost. So
            // the answer is remembered for the settings to show, and the ask is
            // repeated rather than assumed to have been granted once.
            if (exactAllowed(c)) {
                try {
                    if (Build.VERSION.SDK_INT >= 23) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                    else alarms.setExact(AlarmManager.RTC_WAKEUP, at, pi);
                    exactAt = at;
                    lastDenied = 0;
                    lastMode = "exact";
                    return;
                } catch (SecurityException denied) {
                    // Exact was refused even though it was reported as allowed.
                    lastDenied = System.currentTimeMillis();
                    Diary.note("streak alarm: the system refused an exact alarm");
                }
            }
            if (Build.VERSION.SDK_INT >= 23) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else alarms.set(AlarmManager.RTC_WAKEUP, at, pi);
            lastMode = "inexact";
        } catch (Throwable error) {
            Diary.note("streak alarm schedule: " + error);
        }
    }

    // ----------------------------------------------------------- permissions

    /** Ask, once each, for what exact timing in the background depends on. */
    static void permissions(Context c) {
        try {
            if (c == null) return;
            if (Build.VERSION.SDK_INT >= 31) {
                AlarmManager alarms = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
                if (alarms != null && !alarms.canScheduleExactAlarms()) {
                    open(c, new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + c.getPackageName())));
                    return;
                }
            }
            if (Build.VERSION.SDK_INT >= 23) {
                PowerManager power = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
                android.content.SharedPreferences prefs = c.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE);
                if (power != null && !power.isIgnoringBatteryOptimizations(c.getPackageName())
                        && !prefs.getBoolean("streak_battery_asked", false)) {
                    prefs.edit().putBoolean("streak_battery_asked", true).apply();
                    open(c, new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + c.getPackageName())));
                }
            }
        } catch (Throwable error) {
            Diary.note("streak permissions: " + error);
        }
    }

    private static void open(Context c, Intent intent) {
        if (!(c instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(intent);
    }

    // ---------------------------------------------------------- the last resort

    /** TikTok's streak service only exists once the app has been opened: say so, once a day. */
    private static void nudge(Context app) {
        try {
            android.content.SharedPreferences prefs = app.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();
            if (StreakSchedule.sameDay(now, prefs.getLong("streak_nudge_at", 0))) return;
            Intent open = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
            if (open == null) return;
            // Notifications may be switched off at the system level, which is the
            // one thing this fallback has to survive: the flag is only spent when
            // something is actually on the screen, so the next round tries again.
            if (!notificationsAllowed(app)) {
                Diary.note("streak nudge: notifications are not allowed for this app");
                return;
            }
            prefs.edit().putLong("streak_nudge_at", now).apply();
            NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            channel(app);
            PendingIntent tap = PendingIntent.getActivity(app, REQUEST + 1, open,
                    PendingIntent.FLAG_UPDATE_CURRENT
                            | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(app, CHANNEL) : new Notification.Builder(app);
            b.setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle(Local.of("Серия ждёт продления"))
                    .setContentText(Local.of("Откройте TikTok: автопродление сработает сразу"))
                    .setContentIntent(tap)
                    .setAutoCancel(true)
                    .setPriority(Notification.PRIORITY_HIGH);
            manager.notify(REQUEST, b.build());
        } catch (Throwable error) {
            Diary.note("streak nudge: " + error);
        }
    }

    /**
     * What the schedule is waiting on, in one line, for the settings to show.
     *
     * Autoprodlenie has three ways to fail quietly -- the alarm was downgraded to
     * an inexact one, the phone is in a battery saver that defers it, or the
     * notification that asks for the app to be opened cannot be shown -- and the
     * person holding the phone is the only one who can fix any of them. So the
     * state is said rather than guessed at.
     */
    static String status(Context c) {
        try {
            if (!Streaks.isEnabled()) return Local.of("Автопродление выключено");
            List<String> problems = new ArrayList<String>();
            if (!exactAllowed(c)) {
                problems.add(Local.of("точный будильник не разрешён (Android 12+: «Будильники и напоминания»)"));
            }
            try {
                PowerManager power = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
                if (power != null && !power.isIgnoringBatteryOptimizations(c.getPackageName())) {
                    problems.add(Local.of("экономия батареи не отключена для приложения"));
                }
            } catch (Throwable ignored) {
            }
            if (!notificationsAllowed(c)) problems.add(Local.of("уведомления приложения выключены"));
            int minute = StreakSchedule.minute();
            if (minute < 0) problems.add(Local.of("время не выбрано"));
            if (problems.isEmpty()) {
                return Local.of("Готово: отправка в ") + String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
            }
            StringBuilder out = new StringBuilder(Local.of("Проверьте: "));
            for (int i = 0; i < problems.size(); i++) {
                if (i > 0) out.append("; ");
                out.append(problems.get(i));
            }
            return out.toString();
        } catch (Throwable error) {
            return Local.of("Состояние неизвестно");
        }
    }
}
