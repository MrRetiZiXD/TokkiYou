package cat.narezany.tokkiyou;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import java.lang.reflect.Method;

/**
 * What turns while TikTok is fetching something.
 *
 * TikTok's own is two circles, one red and one cyan, passing each other. This
 * draws Material 3's instead: one shape in the accent colour, turning and
 * changing into the next of seven as it goes.
 *
 * The two views that draw the circles are found by what they are -- a plain
 * View that keeps a PorterDuffXfermode and has a `setProgressBarInfo` -- and
 * each has one call put at the top of its `onDraw`, which lands in {@link
 * #draw}. That call decides who draws.
 *
 * When to draw is not decided here, because nothing here knows: the views
 * keep their state in fields the obfuscator named, and a loader that is on
 * the screen is not always one that is showing. So TikTok is asked. Its own
 * `onDraw` is run first, onto a bitmap the size of a fingernail, and whether
 * it put anything there is the answer. That run also does everything else the
 * original did -- counts its frames, asks for the next one -- so the view
 * goes on living exactly as it did, and only what reaches the screen differs.
 */
public final class Loading {

    private Loading() {}

    public static final String KEY = "loading_shapes";

    private static volatile Boolean cached;

    static void reloadSettings() {
        cached = null;
    }

    public static boolean isEnabled() {
        if (Version.LITE) return false;
        Boolean known = cached;
        if (known != null) return known;
        try {
            android.content.Context context = TokkiYou.context();
            if (context == null) return true;  // on until there is somewhere to read from
            boolean on = context.getSharedPreferences(TokkiYou.PREFS,
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
            android.content.Context context = TokkiYou.context();
            if (context == null) return;
            context.getSharedPreferences(TokkiYou.PREFS, android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY, enabled).apply();
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------- who draws

    /** True while TikTok's own drawing is being run to see what it does. */
    private static boolean asking;
    private static boolean complained;

    /**
     * The top of the loader's `onDraw`. True when the mod has drawn it, and
     * the original must not.
     */
    public static boolean draw(View view, Canvas canvas) {
        // the question below comes back through here, and is TikTok's to answer
        if (asking) return false;
        try {
            if (view == null || canvas == null || !isEnabled()) return false;
            if (Looper.myLooper() != Looper.getMainLooper()) return false;
            int width = view.getWidth();
            int height = view.getHeight();
            if (width <= 0 || height <= 0) return false;

            if (!wouldDraw(view, width, height)) return true;
            shape(canvas, width, height);
            // TikTok asks for its next frame some milliseconds on; a shape
            // that turns wants every one
            if (view.isShown()) view.postInvalidateOnAnimation();
            return true;
        } catch (Throwable error) {
            if (!complained) {
                complained = true;
                Diary.note("loading: " + error);
            }
            return false;
        }
    }

    private static final int PROBE = 24;
    private static Bitmap probe;
    private static Canvas probeCanvas;
    private static final int[] seen = new int[PROBE * PROBE];
    private static Method onDraw;

    /** Whether TikTok would have put anything on the screen this frame. */
    private static boolean wouldDraw(View view, int width, int height) throws Exception {
        if (probe == null) {
            probe = Bitmap.createBitmap(PROBE, PROBE, Bitmap.Config.ARGB_8888);
            probeCanvas = new Canvas(probe);
            onDraw = View.class.getDeclaredMethod("onDraw", Canvas.class);
            onDraw.setAccessible(true);
        }
        probe.eraseColor(0);
        int saved = probeCanvas.save();
        probeCanvas.scale(PROBE / (float) width, PROBE / (float) height);
        asking = true;
        try {
            onDraw.invoke(view, probeCanvas);
        } finally {
            asking = false;
            probeCanvas.restoreToCount(saved);
        }
        probe.getPixels(seen, 0, PROBE, 0, 0, PROBE, PROBE);
        for (int pixel : seen) {
            if ((pixel >>> 24) > 24) return true;
        }
        return false;
    }

    // ------------------------------------------------------- the shape

    /** How long one shape takes to become the next. */
    private static final long STEP = 650;
    private static final int SHAPES = 7;
    private static final int POINTS = 96;
    /** How far the whole thing turns while one shape becomes the next. */
    private static final float TURN = 50f;

    private static final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path path = new Path();

    private static void shape(Canvas canvas, int width, int height) {
        long now = SystemClock.uptimeMillis();
        float along = (now % (STEP * SHAPES)) / (float) STEP;
        int from = (int) along;
        int to = (from + 1) % SHAPES;
        float eased = settle(along - from);

        // the seven take a whole number of turns between them only by luck,
        // so the turning is counted in steps taken since the phone started
        // rather than within one round, or it would jump as the round closed
        long steps = now / STEP;
        float angle = (float) Math.toRadians(((steps % 360) + eased) * TURN);

        float centreX = width / 2f;
        float centreY = height / 2f;
        float size = Math.min(width, height) * 0.46f;

        path.rewind();
        for (int i = 0; i < POINTS; i++) {
            double around = 2 * Math.PI * i / POINTS;
            float one = radius(from, around);
            float radius = (one + (radius(to, around) - one) * eased) * size;
            float x = centreX + radius * (float) Math.cos(around + angle);
            float y = centreY + radius * (float) Math.sin(around + angle);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Accent.colour() | 0xFF000000);
        canvas.drawPath(path, paint);
    }

    /**
     * A spring rather than a slide: the shape arrives early, goes a little
     * past, and comes back. It is what makes these read as one thing changing
     * and not as a fade between two.
     */
    private static float settle(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return 1f - (float) (Math.exp(-6.0 * t) * Math.cos(8.0 * t));
    }

    /**
     * How far from the centre a shape reaches in a given direction, out of 1.
     *
     * Each of the seven is written as that one number, so becoming another
     * shape is nothing more than moving every point along its own spoke.
     */
    private static float radius(int shape, double around) {
        switch (shape) {
            case 0:  // a soft burst, ten shallow points
                return 0.89f + 0.11f * (float) Math.cos(10 * around);
            case 1:  // a cookie with nine sides
                return 0.91f + 0.09f * (float) Math.cos(9 * around);
            case 2:  // a pentagon
                return sided(5, around);
            case 3:  // a pill
                return oval(0.56f, around);
            case 4:  // a sun, eight points
                return 0.86f + 0.14f * (float) Math.cos(8 * around);
            case 5:  // a cookie with four sides
                return 0.83f + 0.17f * (float) Math.cos(4 * around);
            default: // an oval
                return oval(0.74f, around);
        }
    }

    /** A polygon with its corners taken off. */
    private static float sided(int sides, double around) {
        double slice = 2 * Math.PI / sides;
        double within = around % slice;
        if (within < 0) within += slice;
        double flat = Math.cos(Math.PI / sides) / Math.cos(within - slice / 2);
        // the corner is where `flat` reaches 1; a soft minimum against a
        // little less than that rounds it without moving the sides
        double cap = 0.92;
        double sharp = 36.0;
        double soft = -Math.log(Math.exp(-sharp * flat) + Math.exp(-sharp * cap)) / sharp;
        return (float) (soft / cap);
    }

    private static float oval(float narrow, double around) {
        double x = narrow * Math.cos(around);
        double y = Math.sin(around);
        return (float) (narrow / Math.sqrt(x * x + y * y));
    }
}
