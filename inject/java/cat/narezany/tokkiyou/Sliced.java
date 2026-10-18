package cat.narezany.tokkiyou;

import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayDeque;
import java.util.WeakHashMap;

/**
 * A walk over every view on a screen, taken a little at a time.
 *
 * Several parts of the mod have to look at each view once: to give it the
 * accent, the colour of its role, and so on. On a screen that has been up for
 * a while that is cheap, because a view already seen is passed over. On a
 * screen that has only just appeared every view is new, the walk is twenty
 * or thirty milliseconds of solid work, and it used to be done all at once
 * at the very moment the screen was coming up -- which is two or three
 * frames gone, and the hitch people feel on opening a profile, a chat or the
 * comments.
 *
 * So the walk stops when it has used a couple of milliseconds, and picks up
 * where it left off before the next frame. A screen is finished within a
 * dozen frames and none of them is late for it.
 *
 * One walk per screen at a time. Asked for again while one is under way, it
 * goes round once more when it is done, which costs little: everything the
 * first round reached is already marked as seen.
 */
final class Sliced {

    private Sliced() {}

    /** What is done with each view. Must be quick to say "nothing to do". */
    interface Visit {
        void view(View view);
    }

    /** How long one stretch may run. A frame at 120 Hz is a little over eight. */
    private static final long STRETCH = 2;
    private static final int MOST = 12000;
    private static final int DEEP = 40;

    private static final class Walk implements Runnable {
        final View root;
        final Visit[] visits;
        final ArrayDeque<View> ahead = new ArrayDeque<View>();
        final ArrayDeque<Integer> depths = new ArrayDeque<Integer>();
        boolean again;
        int seen;

        Walk(View root, Visit[] visits) {
            this.root = root;
            this.visits = visits;
        }

        void begin() {
            ahead.clear();
            depths.clear();
            seen = 0;
            ahead.push(root);
            depths.push(0);
        }

        @Override
        public void run() {
            long until = SystemClock.uptimeMillis() + STRETCH;
            long probe = Probe.start();
            int since = 0;
            try {
                while (!ahead.isEmpty()) {
                    View view = ahead.pop();
                    int depth = depths.pop();
                    if (view == null || ++seen > MOST) continue;
                    for (Visit visit : visits) {
                        try {
                            visit.view(view);
                        } catch (Throwable ignored) {
                        }
                    }
                    if (depth < DEEP && view instanceof ViewGroup) {
                        ViewGroup group = (ViewGroup) view;
                        // pushed last to first, so they come off first to last
                        for (int i = group.getChildCount() - 1; i >= 0; i--) {
                            ahead.push(group.getChildAt(i));
                            depths.push(depth + 1);
                        }
                    }
                    if (++since >= 24) {
                        since = 0;
                        if (SystemClock.uptimeMillis() >= until) break;
                    }
                }
            } catch (Throwable error) {
                ahead.clear();
                depths.clear();
            }
            Probe.stop(Probe.SLICE, probe);
            if (!ahead.isEmpty() && root.isAttachedToWindow()) {
                root.postOnAnimation(this);
                return;
            }
            if (again && root.isAttachedToWindow()) {
                again = false;
                begin();
                root.postOnAnimation(this);
                return;
            }
            synchronized (running) {
                running.remove(root);
            }
        }
    }

    private static final WeakHashMap<View, Walk> running = new WeakHashMap<View, Walk>();

    /** Walk this screen, starting now and going on over the next frames. */
    static void walk(View root, Visit... visits) {
        if (root == null || visits.length == 0) return;
        Walk walk;
        synchronized (running) {
            walk = running.get(root);
            if (walk != null) {
                walk.again = true;
                return;
            }
            walk = new Walk(root, visits);
            running.put(root, walk);
        }
        walk.begin();
        // the first stretch straight away: what is at the top of a screen is
        // what is looked at first
        walk.run();
    }
}
