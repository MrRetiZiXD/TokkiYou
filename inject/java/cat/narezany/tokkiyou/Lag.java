package cat.narezany.tokkiyou;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.View;
import android.view.Window;

/**
 * How long the app stopped for, and what it was doing, written down.
 *
 * "It freezes" is not something anyone can act on, and neither is a guess
 * about why. Two things are measured, because a stutter has two causes.
 *
 * One is the main thread busy with something else when it should be drawing.
 * A second thread asks it a question every twentieth of a second -- a message
 * that does nothing but say when it was reached -- and when no answer has
 * come for a tenth of a second it looks at what the main thread is doing
 * instead. That is the line worth having: the place, and the mod's own part
 * in it if it has one. A thread that turns out to be waiting for something
 * to do was not stalled, and nothing is counted.
 *
 * The other is a frame that took too long though nothing stopped: too much
 * to lay out, too much to draw, a GPU with too much to do. Android times
 * every frame a window draws and says how the time was spent, and those are
 * counted a screen at a time.
 *
 * A stall of most of a second is written in the diary when it happens, as it
 * always was. The short ones and the frames are only counted, and the counts
 * go in the full report.
 *
 * It used to be done by asking to be called on every frame, for ever, which
 * woke the app a hundred and twenty times a second on a screen showing
 * nothing new, and counted the app being put away as the app having stopped.
 * Now nothing is asked while no screen of the app is up, and a pause in
 * which this watcher did not run either is the process being frozen, not a
 * stall.
 */
public final class Lag {

    /** Long enough that a person notices, rather than merely a dropped frame. */
    private static final long BAD = 700;

    /** Long enough to be felt as a stutter while something is moving. */
    private static final long SHORT = 100;

    /** Enough to show a pattern without filling the diary. */
    private static final int ENOUGH = 12;

    /** How often the other thread looks in. */
    private static final long LOOK = 50;

    /** A wait longer than this is the phone asleep, whatever else it is. */
    private static final long ABSURD = 30000;

    private static final String[] STAGES = {
            "waiting for the main thread", "input", "animation", "layout", "draw", "sync", "commands", "swap", "gpu"};

    private static final LagTally TALLY = new LagTally(STAGES);
    private static final int WATCHED = 0x4D61724C;

    private static int told;
    private static volatile long worst;

    /** When the question now out was asked, or zero when there is none. */
    private static volatile long asked;
    /** How many times the watcher looked while it was out: none, and the process was not running. */
    private static volatile int looks;
    /** Where the main thread was a tenth of a second in, and most of a second in. */
    private static volatile StackTraceElement[] early, late;

    private static Handler frames;

    private Lag() {}

    public static void watch() {
        try {
            Thread watcher = new Thread(Lag::look, "tokkiyou-lag");
            watcher.setDaemon(true);
            watcher.setPriority(Thread.MIN_PRIORITY);
            watcher.start();
        } catch (Throwable error) {
            Diary.note("lag: " + error);
        }
    }

    private static void look() {
        final Thread main = Looper.getMainLooper().getThread();
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable answer = Lag::answered;
        while (true) {
            try {
                if (Watch.here() == null) {
                    // nothing of the app is on screen: nothing to stutter
                    Thread.sleep(1000);
                    continue;
                }
                Thread.sleep(LOOK);
                if (Probe.ON) sample(main.getStackTrace());
                long now = SystemClock.uptimeMillis(), since = asked;
                if (since == 0) {
                    early = null;
                    late = null;
                    looks = 0;
                    asked = now;
                    handler.post(answer);
                    continue;
                }
                looks++;
                if (now - since >= SHORT && early == null) early = main.getStackTrace();
                if (now - since >= BAD && late == null) late = main.getStackTrace();
            } catch (InterruptedException over) {
                return;
            } catch (Throwable ignored) {
                // a stack that could not be read is a stall with no place to it
            }
        }
    }

    // ---------------------------------------------- a test build's profile

    private static final java.util.HashMap<String, int[]> BUSY_IN = new java.util.HashMap<String, int[]>();
    private static int samples, busy, ours;

    /**
     * In a test build only: what the main thread is doing whenever the watcher
     * looks, stalled or not. Twenty looks a second for a few minutes is a fair
     * picture of where its time goes, and how much of that is the mod's.
     */
    private static void sample(StackTraceElement[] stack) {
        if (stack == null || stack.length == 0) return;
        synchronized (BUSY_IN) {
            samples++;
            if (where(stack).length() == 0) return;
            busy++;
            String mine = mine(stack);
            if (mine == null) return;
            ours++;
            int[] count = BUSY_IN.get(mine);
            if (count == null) {
                if (BUSY_IN.size() >= 200) return;
                count = new int[1];
                BUSY_IN.put(mine, count);
            }
            count[0]++;
        }
    }

    private static String profile() {
        synchronized (BUSY_IN) {
            if (samples == 0) return "";
            StringBuilder out = new StringBuilder();
            out.append("main thread: busy in ").append(busy).append(" of ").append(samples)
                    .append(" looks, in the mod in ").append(ours).append('\n');
            java.util.ArrayList<String> names = new java.util.ArrayList<String>(BUSY_IN.keySet());
            java.util.Collections.sort(names, (one, two) -> BUSY_IN.get(two)[0] - BUSY_IN.get(one)[0]);
            for (int i = 0; i < names.size() && i < 25; i++) {
                out.append("  ").append(BUSY_IN.get(names.get(i))[0]).append("  ").append(names.get(i)).append('\n');
            }
            return out.toString();
        }
    }

    /** On the main thread: the question has been reached, this long after it was asked. */
    private static void answered() {
        try {
            long took = SystemClock.uptimeMillis() - asked;
            StackTraceElement[] stack = took >= BAD && late != null ? late : early;
            int seen = looks;
            asked = 0;
            if (took < SHORT || took > ABSURD || stack == null || Watch.here() == null) return;
            // the watcher sleeps a twentieth of a second at a time; had it
            // been running all the while, it would have looked far more often
            if (seen < took / (LOOK * 4)) return;
            String where = where(stack);
            if (where.length() == 0) return;
            // A plugin's code first: it is called from the app's own machinery
            // as often as from the mod's, and whoever reads the report wants
            // to know the stall was a plugin's before knowing where in it.
            String plugin = plugin(stack);
            String mine = mine(stack);
            TALLY.stall(plugin != null ? "plugin " + plugin : mine != null ? mine : brief(where), where, took);
            if (plugin != null) charge(plugin, took);
            if (took > worst) worst = took;
            if (took >= BAD && told < ENOUGH) {
                told++;
                Diary.note("lag: the app stopped for " + took + " ms in " + where);
            }
        } catch (Throwable ignored) {
            asked = 0;
        }
    }

    /** A screen came up: its frames are timed from here on. */
    static void frames(Activity activity) {
        if (Build.VERSION.SDK_INT < 24 || activity == null) return;
        try {
            Window window = activity.getWindow();
            View decor = window == null ? null : window.getDecorView();
            if (decor == null || decor.getTag(WATCHED) != null) return;
            decor.setTag(WATCHED, Boolean.TRUE);
            if (frames == null) {
                HandlerThread thread = new HandlerThread("tokkiyou-frames", android.os.Process.THREAD_PRIORITY_BACKGROUND);
                thread.start();
                frames = new Handler(thread.getLooper());
            }
            final String screen = activity.getClass().getSimpleName();
            float rate = 60f;
            try {
                rate = activity.getWindowManager().getDefaultDisplay().getRefreshRate();
            } catch (Throwable ignored) {
            }
            // two frames' worth: one missed is seen by nobody
            final long slow = (long) (2e9 / Math.max(30f, rate));
            final long[] spent = new long[STAGES.length];
            window.addOnFrameMetricsAvailableListener((from, metrics, dropped) -> {
                try {
                    long total = metrics.getMetric(FrameMetrics.TOTAL_DURATION);
                    if (total <= slow) {
                        TALLY.frame(screen, false, false, spent);
                        return;
                    }
                    spent[0] = metrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION);
                    spent[1] = metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION);
                    spent[2] = metrics.getMetric(FrameMetrics.ANIMATION_DURATION);
                    spent[3] = metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION);
                    spent[4] = metrics.getMetric(FrameMetrics.DRAW_DURATION);
                    spent[5] = metrics.getMetric(FrameMetrics.SYNC_DURATION);
                    spent[6] = metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION);
                    spent[7] = metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION);
                    spent[8] = Build.VERSION.SDK_INT >= 31 ? metrics.getMetric(FrameMetrics.GPU_DURATION) : 0;
                    TALLY.frame(screen, true, total > SHORT * 1000000L, spent);
                } catch (Throwable ignored) {
                    // a frame that could not be read is not counted
                }
            }, frames);
        } catch (Throwable error) {
            Diary.detail("lag: frames: " + error);
        }
    }

    /** What has been counted this run, for the full report. */
    static String report() {
        // How long this covers is the first thing to know about it: a report
        // copied a minute after the app was opened is a report on the app
        // being opened, whatever its numbers say.
        long watched = (SystemClock.elapsedRealtime() - BEGAN) / 1000L;
        return "since the app started: " + watched / 60L + " min " + watched % 60L + " s\n"
                + TALLY.report() + (Probe.ON ? profile() : "");
    }

    private static final long BEGAN = SystemClock.elapsedRealtime();

    /** The plugin whose code is nearest the top of a stack, or null when no plugin's is in it. */
    static String plugin(StackTraceElement[] stack) {
        if (stack == null) return null;
        for (StackTraceElement frame : stack) {
            String owner = Plugins.owner(frame.getClassName());
            if (owner != null) return owner;
        }
        return null;
    }

    private static final java.util.HashMap<String, long[]> PLUGIN_COST = new java.util.HashMap<String, long[]>();
    /** Past this much stalling in one run a plugin is named in the diary. */
    private static final long TOO_MUCH = 3000L;

    private static void charge(String plugin, long took) {
        boolean say = false;
        synchronized (PLUGIN_COST) {
            long[] cost = PLUGIN_COST.get(plugin);
            if (cost == null) {
                cost = new long[2];
                PLUGIN_COST.put(plugin, cost);
            }
            say = cost[0] < TOO_MUCH && cost[0] + took >= TOO_MUCH;
            cost[0] += took;
            cost[1]++;
        }
        if (say) Diary.note("lag: plugin " + plugin + " has stalled the app for " + TOO_MUCH / 1000 + " s this run");
    }

    /** How long a plugin has kept the app from drawing this run, in milliseconds. */
    static long cost(String plugin) {
        synchronized (PLUGIN_COST) {
            for (java.util.Map.Entry<String, long[]> one : PLUGIN_COST.entrySet()) {
                // two plugins that share a package are charged together
                if (one.getKey().equals(plugin) || one.getKey().contains(plugin + " / ") || one.getKey().endsWith(" / " + plugin)) {
                    return one.getValue()[0];
                }
            }
            return 0L;
        }
    }

    /** The mod's own first frame in a stack, or null when it has none. */
    static String mine(StackTraceElement[] stack) {
        if (stack == null) return null;
        for (StackTraceElement frame : stack) {
            if (frame.getClassName().startsWith("cat.narezany.tokkiyou.")) return name(frame);
        }
        return null;
    }

    /** The first two places of a stack said in one line: enough to tell one stall from another. */
    static String brief(String where) {
        int first = where.indexOf(" < ");
        int second = first < 0 ? -1 : where.indexOf(" < ", first + 3);
        return second < 0 ? where : where.substring(0, second);
    }

    /**
     * A stack said in one line: the first few frames that are not Android's
     * own plumbing, and the mod's first frame if it has one further down.
     * Empty when the thread is only waiting for its next message.
     */
    static String where(StackTraceElement[] stack) {
        if (stack == null || stack.length == 0) return "";
        String top = stack[0].getClassName() + "." + stack[0].getMethodName();
        if (top.equals("android.os.MessageQueue.nativePollOnce")) return "";
        StringBuilder out = new StringBuilder();
        int said = 0;
        String mine = null;
        for (StackTraceElement frame : stack) {
            String type = frame.getClassName();
            if (mine == null && type.startsWith("cat.narezany.tokkiyou.")) mine = name(frame);
            if (said < 5) {
                if (out.length() > 0) out.append(" < ");
                out.append(name(frame));
                said++;
            }
        }
        if (mine != null && out.indexOf(mine) < 0) out.append(" << ").append(mine);
        return out.toString();
    }

    private static String name(StackTraceElement frame) {
        String type = frame.getClassName();
        int dot = type.lastIndexOf('.');
        // a class with a real name is said without its package; an obfuscated
        // one keeps its "X." so it can be told from a real one
        String shortened = dot > 0 && !type.startsWith("X.") ? type.substring(dot + 1) : type;
        return shortened + "." + frame.getMethodName()
                + (frame.getLineNumber() > 0 && frame.getLineNumber() < 100000 ? ":" + frame.getLineNumber() : "");
    }

    /** The longest stall this run, for the settings to show. */
    public static long worst() {
        return worst;
    }
}
