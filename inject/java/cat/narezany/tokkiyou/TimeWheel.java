package cat.narezany.tokkiyou;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.PopupWindow;

/**
 * Two drums, hours and minutes, with a 12-hour AM/PM pair, drawn the way
 * Material 3 Expressive draws them: shapes that morph under a finger, springs
 * instead of eased tweens, the accent colour as the only colour.
 *
 * Hours and minutes loop without end. A flick coasts on friction and then a
 * spring settles it on a row. Every row that passes under the band gives a
 * short click and a tick of the haptic motor; the click follows the ringer
 * (silent or vibrate mode keeps it quiet) and the tick follows the system's
 * touch feedback setting.
 *
 * The result is a minute of the day, which is what `StreakSchedule` stores.
 */
final class TimeWheel extends View {

    private static final float STEP = 19f;   // degrees between two rows on the drum
    private static final int SIDE = 5;       // rows drawn on each side of the band

    private static final class Spring {
        float x, v, to;
        Spring(float start) { x = to = start; }
        void step(float dt, float k, float c) { v += (-k * (x - to) - c * v) * dt; x += v * dt; }
        boolean still() { return Math.abs(x - to) < .001f && Math.abs(v) < .01f; }
    }

    /** One looping drum: position in rows, friction, then a spring onto a row. */
    private final class Drum {
        final String[] items;
        float pos, vel, tgt, k = 260f, c = 28f;
        int mode, last;   // mode: 0 resting, 1 coasting, 2 settling

        Drum(String[] items, int start) { this.items = items; pos = tgt = start; last = start; }

        int index() { int n = items.length; return ((Math.round(pos) % n) + n) % n; }

        void step(float h) {
            if (mode == 1) {
                pos += vel * h; vel *= (float) Math.exp(-3.4f * h);
                if (Math.abs(vel) < 3f) { mode = 2; tgt = Math.round(pos); k = 260f; c = 28f; }
            } else if (mode == 2) {
                vel += (-k * (pos - tgt) - c * vel) * h; pos += vel * h;
                if (Math.abs(pos - tgt) < .001f && Math.abs(vel) < .01f) { pos = tgt; vel = 0; mode = 0; }
            }
            moved();
        }

        void moved() { int i = index(); if (i != last) { last = i; tick(); describe(); } }

        void goTo(float t) {
            if (!animated()) { pos = t; vel = 0; mode = 0; moved(); invalidate(); return; }
            tgt = t; k = 260f; c = 28f; mode = 2; kick();
        }

        void fling(float v) { vel = v; mode = 1; kick(); }
    }

    private final Skin skin;
    private final float density;
    private final float H;                    // row height, px
    private final float R;                    // drum radius, px
    private final Drum hour, minute;
    private final Spring[] mix = {new Spring(1), new Spring(0), new Spring(1), new Spring(0)}; // hour, minute, AM, PM
    private final Spring held = new Spring(0);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF hourBox = new RectF(), minuteBox = new RectF(), amBox = new RectF(), pmBox = new RectF(), stage = new RectF(), tmp = new RectF();
    private final Path clip = new Path();
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final int slop;

    private int accent;
    private boolean pm;
    private int active;                       // 0 hours, 1 minutes
    private Drum dragging;
    private float downX, downY, startPos;
    private boolean movedFar;
    private int pressedBox = -1;              // 0 hours, 1 minutes, 2 AM, 3 PM
    private int tapBox = -1;                  // last tapped time block, for the double tap
    private long tapAt;
    private PopupWindow field;                // the number typed over a block
    private ViewTreeObserver.OnGlobalLayoutListener follow;
    private VelocityTracker tracker;
    private boolean running;
    private long lastNs;

    TimeWheel(Context context, Skin skin, int minuteOfDay) {
        super(context);
        this.skin = skin;
        density = context.getResources().getDisplayMetrics().density;
        H = 44 * density;
        R = H / (2f * (float) Math.sin(Math.toRadians(STEP / 2f)));
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        String[] hours = new String[12], minutes = new String[60];
        for (int i = 0; i < 12; i++) hours[i] = String.valueOf(i + 1);
        for (int i = 0; i < 60; i++) minutes[i] = (i < 10 ? "0" : "") + i;
        int h24 = Math.max(0, Math.min(23, minuteOfDay / 60));
        int h12 = h24 % 12 == 0 ? 12 : h24 % 12;
        hour = new Drum(hours, h12 - 1);
        minute = new Drum(minutes, Math.max(0, Math.min(59, minuteOfDay % 60)));
        pm = h24 >= 12;
        mix[2].x = mix[2].to = pm ? 0 : 1; mix[3].x = mix[3].to = pm ? 1 : 0;
        accent = Accent.colour();
        text.setFontFeatureSettings("tnum");
        setFocusable(true);
        describe();
    }

    /** What the person chose, as a minute of the day (0..1439). */
    int minuteOfDay() { return ((hour.index() + 1) % 12 + (pm ? 12 : 0)) * 60 + minute.index(); }

    // ---------------------------------------------------------------- loop

    private boolean animated() { return Motion.enabled(getContext()); }

    private void kick() {
        if (!animated()) { settleNow(); return; }
        if (running) return;
        running = true; lastNs = System.nanoTime(); postOnAnimation(frame);
    }

    private void settleNow() {
        for (Spring s : mix) { s.x = s.to; s.v = 0; }
        held.x = held.to; held.v = 0; invalidate();
    }

    private final Runnable frame = new Runnable() {
        @Override public void run() {
            long now = System.nanoTime();
            float left = Math.min(.05f, (now - lastNs) / 1e9f); lastNs = now;
            while (left > 0) {
                float h = Math.min(left, 1f / 120f); left -= h;
                hour.step(h); minute.step(h);
                for (Spring s : mix) s.step(h, 380f, 30f);
                held.step(h, 380f, 30f);
            }
            invalidate();
            boolean busy = hour.mode != 0 || minute.mode != 0 || !held.still();
            for (Spring s : mix) busy |= !s.still();
            if (busy) postOnAnimation(this); else running = false;
        }
    };

    private void targets() {
        mix[0].to = active == 0 ? 1 : 0; mix[1].to = active == 1 ? 1 : 0;
        mix[2].to = pm ? 0 : 1; mix[3].to = pm ? 1 : 0;
        held.to = dragging != null ? 1 : 0;
        kick();
    }

    private void describe() {
        setContentDescription(hour.items[hour.index()] + ":" + minute.items[minute.index()] + (pm ? " PM" : " AM"));
    }

    // -------------------------------------------------------------- layout

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED ? Math.round(320 * density) : MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, Math.round((76 + 12 + 200) * density));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float gap = 6 * density, ap = 60 * density, colon = 14 * density;
        float block = Math.min(112 * density, (w - ap - colon - 3 * gap) / 2f);
        float total = 2 * block + colon + ap + 3 * gap, x = (w - total) / 2f, top = 0, bh = 76 * density;
        hourBox.set(x, top, x + block, top + bh); x += block + gap + colon + gap;
        minuteBox.set(x, top, x + block, top + bh); x += block + gap;
        float half = (bh - 4 * density) / 2f;
        amBox.set(x, top, x + ap, top + half); pmBox.set(x, top + half + 4 * density, x + ap, top + bh);
        stage.set(0, bh + 12 * density, w, bh + 12 * density + 200 * density);
    }

    // ------------------------------------------------------------- drawing

    private static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        return ((int) (((a >>> 24) & 255) + (((b >>> 24) & 255) - ((a >>> 24) & 255)) * t) << 24)
                | ((int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t) << 16)
                | ((int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t) << 8)
                | (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
    }

    private static float lum(int c) {
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        return .2126f * r * r + .7152f * g * g + .0722f * b * b;   // squared: close enough to linear light
    }

    private static float contrast(int a, int b) { float x = lum(a), y = lum(b); return (Math.max(x, y) + .05f) / (Math.min(x, y) + .05f); }

    private void label(Canvas canvas, RectF box, String s, float size, Typeface face, int color) {
        text.setTextAlign(Paint.Align.CENTER); text.setTypeface(face); text.setTextSize(size); text.setColor(color);
        Paint.FontMetrics m = text.getFontMetrics();
        canvas.drawText(s, box.centerX(), box.centerY() - (m.ascent + m.descent) / 2f, text);
    }

    @Override protected void onDraw(Canvas canvas) {
        int surface = lerp(skin.card, skin.text, .10f);                 // the unselected block
        int tonal = lerp(surface, accent, .26f);                        // the selected block
        int onTonal = contrast(accent, tonal) >= 3f ? accent : skin.text;
        float u = density;

        // time blocks and AM/PM: the selected one rounds off into a softer shape
        RectF[] boxes = {hourBox, minuteBox, amBox, pmBox};
        for (int i = 0; i < 4; i++) {
            float t = mix[i].x;
            float r = (i < 2 ? 16 + 10 * t : 20 - 8 * t) * u;
            fill.setColor(lerp(surface, tonal, t));
            canvas.drawRoundRect(boxes[i], r, r, fill);
        }
        label(canvas, hourBox, hour.items[hour.index()], 40 * u, regular, lerp(skin.text, onTonal, mix[0].x));
        label(canvas, minuteBox, minute.items[minute.index()], 40 * u, regular, lerp(skin.text, onTonal, mix[1].x));
        label(canvas, amBox, "AM", 15 * u, medium, lerp(skin.text, onTonal, mix[2].x));
        label(canvas, pmBox, "PM", 15 * u, medium, lerp(skin.text, onTonal, mix[3].x));
        tmp.set(hourBox.right, hourBox.top, minuteBox.left, hourBox.bottom);
        label(canvas, tmp, ":", 44 * u, regular, skin.text);

        // the stage
        canvas.save();
        clip.reset(); clip.addRoundRect(stage, 28 * u, 28 * u, Path.Direction.CW);
        canvas.clipPath(clip);
        fill.setColor(skin.page); canvas.drawRect(stage, fill);

        float cy = stage.centerY(), cx = stage.centerX(), hv = held.x;
        float br = (20 + 8 * hv) * u;
        tmp.set(12 * u, cy - 25 * u, stage.width() - 12 * u, cy + 25 * u);
        canvas.save();
        canvas.scale(1f + .02f * hv, 1f - .06f * hv, cx, cy);
        fill.setColor(lerp(lerp(skin.page, accent, .18f), lerp(skin.page, accent, .30f), hv));
        canvas.drawRoundRect(tmp, br, br, fill);
        canvas.restore();

        drum(canvas, hour, cx - 12 * u, cy, Paint.Align.RIGHT);
        drum(canvas, minute, cx + 12 * u, cy, Paint.Align.LEFT);
        text.setTextAlign(Paint.Align.CENTER); text.setTypeface(medium); text.setTextSize(30 * u); text.setColor(accent);
        Paint.FontMetrics m = text.getFontMetrics();
        canvas.drawText(":", cx, cy - (m.ascent + m.descent) / 2f - 2 * u, text);
        canvas.restore();
    }

    private void drum(Canvas canvas, Drum d, float x, float cy, Paint.Align align) {
        int c = Math.round(d.pos), n = d.items.length;
        text.setTextAlign(align); text.setTextSize(28 * density);
        for (int j = -SIDE; j <= SIDE; j++) {
            int idx = c + j;
            float off = idx - d.pos, a = off * STEP;
            if (Math.abs(a) > 88f) continue;
            double rad = Math.toRadians(a);
            float cos = (float) Math.cos(rad), y = cy + R * (float) Math.sin(rad);
            boolean on = Math.abs(off) < .5f;
            text.setTypeface(on ? medium : regular);
            int base = on ? accent : skin.text;
            text.setColor(base);
            text.setAlpha(Math.round(255f * (float) Math.pow(cos, 2.2) * (on ? 1f : .72f)));
            Paint.FontMetrics m = text.getFontMetrics();
            canvas.save();
            canvas.scale(.92f + .08f * cos, cos, x, y);                 // the drum turning away
            canvas.drawText(d.items[((idx % n) + n) % n], x, y - (m.ascent + m.descent) / 2f, text);
            canvas.restore();
        }
    }

    // --------------------------------------------------------------- touch

    private int boxAt(float x, float y) {
        if (hourBox.contains(x, y)) return 0;
        if (minuteBox.contains(x, y)) return 1;
        if (amBox.contains(x, y)) return 2;
        if (pmBox.contains(x, y)) return 3;
        return -1;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x; downY = y; movedFar = false;
                if (stage.contains(x, y)) {
                    dragging = x < stage.centerX() ? hour : minute;
                    active = dragging == hour ? 0 : 1;
                    dragging.mode = 0; dragging.vel = 0; startPos = dragging.pos;
                    tracker = VelocityTracker.obtain(); tracker.addMovement(e);
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    targets();
                } else pressedBox = boxAt(x, y);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging == null) return true;
                tracker.addMovement(e);
                if (Math.abs(y - downY) > slop) movedFar = true;
                dragging.pos = startPos - (y - downY) / H;
                dragging.moved(); invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
                if (dragging != null) {
                    Drum d = dragging; dragging = null;
                    tracker.addMovement(e); tracker.computeCurrentVelocity(1000);
                    float v = Math.max(-90f, Math.min(90f, -tracker.getYVelocity() / H));
                    tracker.recycle(); tracker = null;
                    if (up && !movedFar) {                             // a tap goes to the row it landed on
                        float s = Math.max(-1f, Math.min(1f, (y - stage.centerY()) / R));
                        d.goTo(Math.round(d.pos + (float) Math.toDegrees(Math.asin(s)) / STEP));
                    } else if (!up || Math.abs(v) < 6f || !animated()) d.goTo(Math.round(d.pos));
                    else d.fling(v);
                    targets();
                } else if (up && pressedBox >= 0 && pressedBox == boxAt(x, y)) {
                    long now = e.getEventTime();
                    if (pressedBox < 2) {
                        active = pressedBox;
                        if (pressedBox == tapBox && now - tapAt <= ViewConfiguration.getDoubleTapTimeout()) {
                            int which = pressedBox; tapBox = -1; pressedBox = -1;
                            targets(); edit(which);                      // two taps: type the number
                            return true;
                        }
                        tapBox = pressedBox; tapAt = now;
                    } else if (pm != (pressedBox == 3)) { pm = pressedBox == 3; tick(); describe(); }
                    targets();
                }
                pressedBox = -1;
                return true;
            default:
                return true;
        }
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); accent = Accent.colour(); }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow(); removeCallbacks(frame); running = false;
        if (field != null) { try { field.dismiss(); } catch (Throwable ignored) { } }
        synchronized (TimeWheel.class) { if (click != null) { try { click.release(); } catch (Throwable ignored) { } click = null; } }
    }

    // ------------------------------------------------- typing a number

    /** A field laid exactly over the block, in the block's own selected shape. */
    private void edit(final int which) {
        if (field != null) field.dismiss();
        final Drum d = which == 0 ? hour : minute;
        final RectF box = which == 0 ? hourBox : minuteBox;
        int surface = lerp(skin.card, skin.text, .10f), tonal = lerp(surface, accent, .26f);
        final EditText in = new EditText(getContext());
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        in.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});
        in.setSingleLine(true); in.setGravity(Gravity.CENTER); in.setPadding(0, 0, 0, 0);
        in.setTextSize(TypedValue.COMPLEX_UNIT_PX, 40 * density);
        in.setTypeface(regular);
        in.setTextColor(contrast(accent, tonal) >= 3f ? accent : skin.text);
        in.setHighlightColor((accent & 0xFFFFFF) | 0x55000000);
        in.setImeOptions(EditorInfo.IME_ACTION_DONE);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(tonal); shape.setCornerRadius(26 * density); shape.setStroke(Math.round(2 * density), accent);
        in.setBackground(shape);
        in.setText(d.items[d.index()]); in.selectAll();

        final PopupWindow w = new PopupWindow(in, Math.round(box.width()), Math.round(box.height()), true);
        w.setBackgroundDrawable(new ColorDrawable(0));
        w.setOutsideTouchable(true);
        w.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        final boolean[] done = {false};
        final Runnable finish = () -> { if (!done[0]) { done[0] = true; commit(which, in.getText().toString()); } };
        in.setOnEditorActionListener((v, action, event) -> { finish.run(); w.dismiss(); return true; });
        w.setOnDismissListener(() -> {
            finish.run(); field = null;
            if (follow != null) { try { getViewTreeObserver().removeOnGlobalLayoutListener(follow); } catch (Throwable ignored) { } follow = null; }
        });
        field = w;
        int[] at = new int[2]; getLocationOnScreen(at);
        w.showAtLocation(this, Gravity.NO_GRAVITY, at[0] + Math.round(box.left), at[1] + Math.round(box.top));
        in.requestFocus();
        // the sheet moves when the keyboard opens; keep the field on its block
        follow = () -> { int[] p = new int[2]; getLocationOnScreen(p); w.update(p[0] + Math.round(box.left), p[1] + Math.round(box.top), -1, -1); };
        getViewTreeObserver().addOnGlobalLayoutListener(follow);
    }

    /** Hours take 1..12, or 0 and 13..23 as a 24-hour time; minutes take 0..59. */
    private void commit(int which, String typed) {
        int v;
        try { v = Integer.parseInt(typed.trim()); } catch (Throwable ignored) { return; }
        if (v < 0) return;
        if (which == 0) {
            boolean toPm = pm;
            if (v == 0) { v = 12; toPm = false; }
            else if (v > 12) { v = Math.min(v, 23) - 12; toPm = true; }
            jumpTo(hour, v - 1);
            if (toPm != pm) { pm = toPm; tick(); describe(); }
        } else jumpTo(minute, Math.min(v, 59));
        targets();
    }

    /** The shortest way round the drum to a row. */
    private void jumpTo(Drum d, int index) {
        int n = d.items.length, cur = Math.round(d.pos);
        d.goTo(cur + (((index - cur + n / 2) % n) + n) % n - n / 2);
    }

    // ---------------------------------------------------- click and haptics

    private static AudioTrack click;
    private static long lastClick;

    private void tick() {
        try { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); } catch (Throwable ignored) { }
        long now = System.nanoTime();
        if (now - lastClick < 22_000_000L) return;
        lastClick = now;
        try {
            AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            if (am == null || am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) return;
            synchronized (TimeWheel.class) {
                if (click == null) click = makeClick();
                click.stop(); click.reloadStaticData(); click.play();
            }
        } catch (Throwable ignored) { }
    }

    /** 30 ms of a decaying 2.2 kHz sine, built once; no asset to ship. */
    private static AudioTrack makeClick() {
        int rate = 22050, n = rate * 30 / 1000;
        short[] pcm = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) rate;
            pcm[i] = (short) (Math.sin(2 * Math.PI * 2200 * t) * Math.exp(-t * 140) * .5 * 32767);
        }
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(n * 2).setTransferMode(AudioTrack.MODE_STATIC).build();
        track.write(pcm, 0, n);
        return track;
    }
}
