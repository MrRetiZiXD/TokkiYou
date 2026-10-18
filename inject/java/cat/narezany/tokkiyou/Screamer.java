package cat.narezany.tokkiyou;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Now and then, the fat tilcayo, all over the screen, yelling.
 *
 * A joke somebody has to ask for: it is off until it is switched on, and the
 * switch says what it does. Once on, a photograph of the cat stretched to the
 * edges of the screen turns up every so often while TikTok is open, with a
 * yell, and goes away by itself in a second or at a touch.
 *
 * The yell is made here out of arithmetic rather than shipped as a file, and
 * it is played as media at whatever the volume already is: turning somebody's
 * phone up for them is not part of the joke. Nothing flashes.
 */
final class Screamer {

    private Screamer() {}

    static final String KEY = "screamers";
    /** The yell, which can be left out: the picture alone is enough for some. */
    static final String KEY_SOUND = "screamers_sound";
    /** Between one and the next, in minutes: somewhere from the first to the second. */
    private static final int SOONEST = 6, LATEST = 25;
    private static final long STAYS = 1300L;
    private static final int RATE = 22050;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static long due;
    private static boolean waiting;
    private static Bitmap cat;

    static boolean isOn(Context context) {
        try {
            return context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false);
        } catch (Throwable error) {
            return false;
        }
    }

    static void set(Context context, boolean on) {
        context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply();
        due = 0;
        if (on) plan(context);
    }

    static boolean loud(Context context) {
        try {
            return context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SOUND, true);
        } catch (Throwable error) {
            return true;
        }
    }

    static void setLoud(Context context, boolean on) {
        context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SOUND, on).apply();
    }

    /** A screen came up. If one is owed it comes in a moment; otherwise the next is planned. */
    static void attach(Activity activity) {
        if (!isOn(activity)) return;
        plan(activity);
        fetch(activity);
    }

    private static void plan(final Context context) {
        long now = android.os.SystemClock.uptimeMillis();
        if (due == 0) due = now + (SOONEST + (long) (Math.random() * (LATEST - SOONEST))) * 60000L;
        if (waiting) return;
        waiting = true;
        MAIN.postDelayed(() -> {
            waiting = false;
            if (!isOn(context)) return;
            Activity here = Watch.here();
            // nobody looking: it keeps until somebody is
            if (here == null) return;
            due = 0;
            now(here);
            plan(context);
        }, Math.max(3000L, due - now));
    }

    private static void fetch(final Context context) {
        if (cat != null) return;
        Net.away("screamer", () -> {
            try {
                byte[] raw = Cats.fat(context);
                if (raw == null) return;
                BitmapFactory.Options how = new BitmapFactory.Options();
                how.inSampleSize = 2;
                cat = BitmapFactory.decodeByteArray(raw, 0, raw.length, how);
            } catch (Throwable error) {
                Diary.note("screamer: " + error);
            }
        });
    }

    /** Show one on this screen, this instant. */
    static void now(final Activity activity) {
        try {
            if (cat == null) {
                fetch(activity);
                // the first one waits for the photograph to arrive
                MAIN.postDelayed(() -> { if (cat != null && !activity.isFinishing()) now(activity); }, 1500L);
                return;
            }
            View decor = activity.getWindow().getDecorView();
            if (!(decor instanceof FrameLayout) || !decor.isAttachedToWindow()) return;
            final FrameLayout screen = (FrameLayout) decor;
            final ImageView face = new ImageView(activity);
            face.setImageBitmap(cat);
            // stretched to the edges, which is the whole look of it
            face.setScaleType(ImageView.ScaleType.FIT_XY);
            face.setBackgroundColor(0xFF000000);
            face.setElevation(activity.getResources().getDisplayMetrics().density * 40);
            face.setClickable(true);
            final Runnable gone = () -> {
                if (face.getParent() instanceof ViewGroup) ((ViewGroup) face.getParent()).removeView(face);
            };
            face.setOnClickListener(v -> gone.run());
            screen.addView(face, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            face.setScaleX(0.3f);
            face.setScaleY(0.3f);
            face.animate().scaleX(1.25f).scaleY(1.25f).setDuration(160).withEndAction(
                    () -> face.animate().scaleX(1.1f).scaleY(1.1f).setDuration(STAYS - 160).start()).start();
            MAIN.postDelayed(gone, STAYS);
            if (loud(activity)) yell();
        } catch (Throwable error) {
            Diary.note("screamer: " + error);
        }
    }

    /**
     * A yell: a rough voice that climbs, wavers and breaks up, a second long.
     * A saw wave for the voice, a second one a little off it for the roughness,
     * hiss on top, and all of it pushed hard enough to flatten at the peaks.
     */
    static short[] voice() {
        int length = RATE;
        short[] out = new short[length];
        java.util.Random hiss = new java.util.Random(7);
        double phase = 0, other = 0;
        for (int i = 0; i < length; i++) {
            double t = i / (double) length;
            double pitch = 520 + 520 * Math.min(1.0, t * 3) + 60 * Math.sin(t * 2 * Math.PI * 11);
            phase += pitch / RATE;
            other += pitch * 1.493 / RATE;
            double saw = 2 * (phase - Math.floor(phase)) - 1;
            double rough = 2 * (other - Math.floor(other)) - 1;
            double sample = 0.6 * saw + 0.35 * rough + 0.25 * (hiss.nextDouble() * 2 - 1);
            double swell = Math.min(1.0, t * 40) * Math.min(1.0, (1 - t) * 6);
            sample = Math.max(-1, Math.min(1, sample * 2.2)) * swell;
            out[i] = (short) (sample * 30000);
        }
        return out;
    }

    private static void yell() {
        new Thread(() -> {
            try {
                short[] sound = voice();
                final AudioTrack track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .setBufferSizeInBytes(sound.length * 2).build();
                track.write(sound, 0, sound.length);
                track.play();
                MAIN.postDelayed(() -> {
                    try { track.release(); } catch (Throwable ignored) { }
                }, 1600L);
            } catch (Throwable error) {
                Diary.note("screamer: " + error);
            }
        }, "tokkiyou-yell").start();
    }
}
