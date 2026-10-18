package cat.narezany.margyt;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;

/**
 * Material 3 Expressive, drawn here.
 *
 * Material's own library is not available to this build (adding it means adding
 * resources, and this build never touches the resource table), so the parts of
 * M3 Expressive that matter are drawn in code:
 *
 *  - the shape library: cookie, clover, sunny, flower, burst and the rest, as
 *    smooth scalloped outlines that can morph from a circle and rotate;
 *  - segmented lists: one tile per row, large outer corners, small inner ones,
 *    a thin gap between them, and corners that spring open while pressed;
 *  - leading icons set inside a shape that changes colour and turns when the
 *    thing it stands for is switched on;
 *  - a thick, rounded slider with a standing handle;
 *  - round tonal buttons that become rounded squares under the finger.
 *
 * Nothing here holds an activity, and nothing needs a resource.
 */
final class Expressive {

    private Expressive() {}

    // ------------------------------------------------------------- shapes

    static final int COOKIE9 = 0, CLOVER4 = 1, SUNNY = 2, FLOWER6 = 3,
            BURST12 = 4, PENTAGON = 5, ARCH3 = 6, COOKIE7 = 7;
    static final int KINDS = 8;

    /** Lobes of each shape. */
    private static final int[] LOBES = {9, 4, 8, 6, 12, 5, 3, 7};
    /** How deep the valleys go: 0 is a circle, 0.5 would touch the middle. */
    private static final float[] DEPTH = {0.050f, 0.160f, 0.110f, 0.140f, 0.040f, 0.075f, 0.100f, 0.065f};

    static int lobes(int kind) {
        return LOBES[wrap(kind)];
    }

    private static int wrap(int kind) {
        return ((kind % KINDS) + KINDS) % KINDS;
    }

    /**
     * An expressive outline inside a circle of radius `radius` at (cx, cy).
     *
     * `amount` runs from 0 (a plain circle) to 1 (the full shape), which is the
     * whole of a morph. `turn` rotates it in degrees; a turn of 360 / lobes is a
     * full lobe and looks like the shape came back to where it started.
     */
    static void shape(Path out, int kind, float cx, float cy, float radius, float amount, float turn) {
        int k = wrap(kind);
        int n = LOBES[k];
        float depth = DEPTH[k] * Math.max(0f, amount);
        int steps = Math.max(96, n * 16);
        double start = Math.toRadians(turn) - Math.PI / 2.0;
        out.reset();
        for (int i = 0; i < steps; i++) {
            double t = Math.PI * 2.0 * i / steps;
            double wave = Math.cos(n * (t - start));
            // tips reach the radius, valleys sit `depth * 2` below it
            double r = radius * (1.0 - depth * (1.0 - wave));
            float x = (float) (cx + r * Math.cos(t));
            float y = (float) (cy + r * Math.sin(t));
            if (i == 0) out.moveTo(x, y);
            else out.lineTo(x, y);
        }
        out.close();
    }

    /** Distance from the middle at one angle: what `shape` draws, as a number. */
    static float reach(int kind, float amount, float turn, double angle) {
        int k = wrap(kind);
        int n = LOBES[k];
        float depth = DEPTH[k] * Math.max(0f, amount);
        double start = Math.toRadians(turn) - Math.PI / 2.0;
        double wave = Math.cos(n * (angle - start));
        return (float) (1.0 - depth * (1.0 - wave));
    }

    static int mix(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return Color.argb(
                Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t),
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }

    private static float dp(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }

    // -------------------------------------------------------------- tiles

    /**
     * One rounded tile whose corners move.
     *
     * At rest the top and bottom pairs of corners are whatever the list put
     * there; while pressed every corner heads for `pressed`. A button that is
     * round does the opposite way round -- it is told a small `pressed` and
     * turns into a rounded square. The move is a short spring.
     */
    static final class Tile extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF box = new RectF();
        private final float[] radii = new float[8];
        private final boolean mask;
        private final boolean animate;
        private float top, bottom, pressedRadius;
        private float press;
        private boolean down;
        private ValueAnimator motion;

        Tile(int colour, float top, float bottom, float pressedRadius, boolean mask, boolean animate) {
            this.top = top;
            this.bottom = bottom;
            this.pressedRadius = pressedRadius;
            this.mask = mask;
            this.animate = animate;
            paint.setColor(colour);
        }

        @Override
        public boolean isStateful() {
            return !mask;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean now = false;
            for (int one : state) {
                if (one == android.R.attr.state_pressed) now = true;
            }
            if (now == down) return false;
            down = now;
            moveTo(now ? 1f : 0f);
            return true;
        }

        private void moveTo(float target) {
            if (motion != null) motion.cancel();
            if (!animate) {
                press = target;
                invalidateSelf();
                return;
            }
            motion = ValueAnimator.ofFloat(press, target);
            motion.setDuration(target > press ? 260L : 200L);
            motion.setInterpolator(target > press ? new OvershootInterpolator(1.8f) : new DecelerateInterpolator());
            motion.addUpdateListener(a -> {
                press = (Float) a.getAnimatedValue();
                invalidateSelf();
            });
            motion.start();
        }

        /** The corner radii as drawn right now, top pair then bottom pair. */
        float[] corners(float height) {
            float limit = height / 2f;
            float t = Math.min(top, limit), b = Math.min(bottom, limit);
            float p = Math.min(pressedRadius, limit);
            float amount = Math.max(0f, press);
            return new float[] {
                    clamp(t + (p - t) * amount, limit), clamp(b + (p - b) * amount, limit)};
        }

        private static float clamp(float value, float limit) {
            return Math.max(0f, Math.min(limit, value));
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) return;
            box.set(bounds);
            float[] c = corners(bounds.height());
            radii[0] = radii[1] = radii[2] = radii[3] = c[0];
            radii[4] = radii[5] = radii[6] = radii[7] = c[1];
            path.reset();
            path.addRoundRect(box, radii, Path.Direction.CW);
            canvas.drawPath(path, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** A tile with a ripple clipped to its own shape. */
    static Drawable tile(Context context, int fill, int ripple, float top, float bottom, float pressed) {
        boolean animate = Motion.enabled(context);
        return new RippleDrawable(ColorStateList.valueOf(ripple),
                new Tile(fill, top, bottom, pressed, false, animate),
                new Tile(0xFFFFFFFF, top, bottom, pressed, true, false));
    }

    // -------------------------------------------------------------- group

    /**
     * A segmented list: every child becomes a tile of its own.
     *
     * The first tile has large top corners, the last large bottom ones, and
     * between them the corners are small, with a thin gap -- the grouped list
     * of M3 Expressive. A child that already paints its own background (a
     * preview, a banner) is left as it is.
     */
    static final class Group extends LinearLayout {
        private final int fill, ripple;
        private final float outer, inner, gap, pressed;

        Group(Context context, int fill, int ripple) {
            super(context);
            setOrientation(VERTICAL);
            this.fill = fill;
            this.ripple = ripple;
            this.outer = dp(context, 26);
            this.inner = dp(context, 6);
            this.gap = dp(context, 2);
            this.pressed = dp(context, 28);
        }

        @Override
        public void onViewAdded(View child) {
            super.onViewAdded(child);
            restyle();
        }

        @Override
        public void onViewRemoved(View child) {
            super.onViewRemoved(child);
            restyle();
        }

        private static boolean ours(View view) {
            Drawable background = view.getBackground();
            if (background == null) return true;
            if (background instanceof RippleDrawable) return true;
            return background instanceof android.graphics.drawable.ColorDrawable
                    && Color.alpha(((android.graphics.drawable.ColorDrawable) background).getColor()) == 0;
        }

        private void restyle() {
            int count = getChildCount(), first = -1, last = -1;
            for (int i = 0; i < count; i++) {
                if (getChildAt(i).getVisibility() == GONE) continue;
                if (first < 0) first = i;
                last = i;
            }
            for (int i = 0; i < count; i++) {
                View view = getChildAt(i);
                if (view.getVisibility() == GONE) continue;
                ViewGroup.LayoutParams given = view.getLayoutParams();
                LinearLayout.LayoutParams params = given instanceof LinearLayout.LayoutParams
                        ? (LinearLayout.LayoutParams) given
                        : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                int want = i == first ? 0 : Math.round(gap);
                if (params.topMargin != want || params != given) {
                    params.topMargin = want;
                    view.setLayoutParams(params);
                }
                if (!ours(view)) continue;
                view.setBackground(tile(getContext(), fill, ripple,
                        i == first ? outer : inner, i == last ? outer : inner, pressed));
            }
        }
    }

    // -------------------------------------------------------------- badge

    /**
     * A leading icon inside an expressive shape.
     *
     * Off, the shape is quiet; on, it fills with the accent and turns by one
     * lobe with a little overshoot -- the same shape landing in a new place.
     */
    static final class Badge extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int kind, size, glyphSize;
        private final Drawable glyph;
        private final int fillOff, fillOn, inkOff, inkOn;
        private float state;
        private ValueAnimator motion;

        Badge(Context context, int kind, Drawable glyph, int sizeDp,
              int fillOff, int fillOn, int inkOff, int inkOn) {
            super(context);
            this.kind = wrap(kind);
            this.glyph = glyph;
            this.size = Math.round(dp(context, sizeDp));
            this.glyphSize = Math.round(dp(context, sizeDp * 0.55f));
            this.fillOff = fillOff;
            this.fillOn = fillOn;
            this.inkOff = inkOff;
            this.inkOn = inkOn;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        /** Whether this is lit; animated when asked and the phone allows motion. */
        void set(boolean on, boolean animated) {
            float target = on ? 1f : 0f;
            if (motion != null) motion.cancel();
            if (!animated || !Motion.enabled(getContext())) {
                state = target;
                invalidate();
                return;
            }
            motion = ValueAnimator.ofFloat(state, target);
            motion.setDuration(300L);
            motion.setInterpolator(new OvershootInterpolator(1.6f));
            motion.addUpdateListener(a -> {
                state = (Float) a.getAnimatedValue();
                invalidate();
            });
            motion.start();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(size, size);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) / 2f;
            shape(path, kind, cx, cy, radius, 1f, state * 360f / LOBES[kind]);
            paint.setColor(mix(fillOff, fillOn, state));
            canvas.drawPath(path, paint);
            if (glyph != null) {
                glyph.setBounds(Math.round(cx - glyphSize / 2f), Math.round(cy - glyphSize / 2f),
                        Math.round(cx + glyphSize / 2f), Math.round(cy + glyphSize / 2f));
                glyph.setColorFilter(mix(inkOff, inkOn, state), PorterDuff.Mode.SRC_IN);
                glyph.draw(canvas);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            if (motion != null) {
                motion.cancel();
                motion = null;
            }
            super.onDetachedFromWindow();
        }
    }

    // ------------------------------------------------------------- button

    /** A round tonal button that becomes a rounded square while it is held. */
    static View roundButton(Context context, Drawable glyph, int fill, int ripple, int ink, int sizeDp) {
        final int size = Math.round(dp(context, sizeDp));
        final int pad = Math.round(dp(context, sizeDp * 0.27f));
        FrameLayout box = new FrameLayout(context);
        box.setBackground(tile(context, fill, ripple, size / 2f, size / 2f, dp(context, sizeDp * 0.30f)));
        android.widget.ImageView picture = new android.widget.ImageView(context);
        glyph.setColorFilter(ink, PorterDuff.Mode.SRC_IN);
        picture.setImageDrawable(glyph);
        box.addView(picture, new FrameLayout.LayoutParams(size - pad * 2, size - pad * 2, Gravity.CENTER));
        box.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return box;
    }

    // ------------------------------------------------------------- slider

    /**
     * A thick, rounded track and a handle that stands upright.
     *
     * The filled part ends under the handle, so its flat edge is never seen.
     */
    static void slider(SeekBar bar, int accent, int quiet) {
        Context context = bar.getContext();
        GradientDrawable back = new GradientDrawable();
        back.setColor(quiet);
        back.setCornerRadius(dp(context, 8));
        back.setSize(-1, Math.round(dp(context, 16)));
        GradientDrawable front = new GradientDrawable();
        front.setColor(accent);
        front.setCornerRadius(dp(context, 8));
        front.setSize(-1, Math.round(dp(context, 16)));
        ClipDrawable clip = new ClipDrawable(front, Gravity.LEFT, ClipDrawable.HORIZONTAL);
        LayerDrawable layers = new LayerDrawable(new Drawable[] {back, clip});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        bar.setProgressDrawable(layers);

        GradientDrawable handle = new GradientDrawable();
        handle.setColor(accent);
        handle.setCornerRadius(dp(context, 3));
        handle.setSize(Math.round(dp(context, 6)), Math.round(dp(context, 44)));
        bar.setThumb(handle);
        bar.setThumbOffset(0);
        int side = Math.round(dp(context, 10));
        bar.setPadding(side, 0, side, 0);
        bar.setSplitTrack(false);
    }

    // ------------------------------------------------------------- search

    /** A magnifying glass on the 24-unit grid the other glyphs use. */
    static final class Magnifier extends Drawable {
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);

        Magnifier(int colour) {
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeCap(Paint.Cap.ROUND);
            ink.setColor(colour);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            int saved = canvas.save();
            canvas.translate(b.left, b.top);
            float unit = b.width() / 24f;
            canvas.scale(unit, unit);
            ink.setStrokeWidth(2.4f);
            canvas.drawCircle(10.5f, 10.5f, 6.5f, ink);
            canvas.drawLine(15.4f, 15.4f, 20.4f, 20.4f, ink);
            canvas.restoreToCount(saved);
        }

        @Override public void setAlpha(int alpha) { ink.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { ink.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
