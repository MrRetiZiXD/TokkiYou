package cat.narezany.tokkiyou;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.Locale;

/**
 * One calm question on the first launch: may the mod send crash reports?
 *
 * The card is the mod's own Panel, so it takes the screen's colours and the
 * accent. The icon sits in the middle; the answers stay hidden for five
 * seconds so the words get read, then fade in where the countdown was.
 * Nothing is sent before "yes", and the choice lives in the mod settings.
 */
final class CrashConsent {
    static final int SECONDS = 5;
    private static WeakReference<Dialog> open = new WeakReference<Dialog>(null);
    private static WeakReference<Activity> owner = new WeakReference<Activity>(null);

    private CrashConsent() {}

    /** Asked already, or the switch was used in settings before this window existed. */
    static boolean asked(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(CrashReports.ASKED, false) || prefs.getBoolean(CrashReports.KEY, false);
    }

    /** Shows the window; `then` runs after an answer, never after a pause. */
    static void show(final Activity activity, final Runnable then) {
        Dialog previous = open.get();
        if (previous != null && previous.isShowing()) return;
        if (activity.isFinishing() || activity.isDestroyed() || asked(activity)) return;

        final Skin skin = Skin.remembered(activity);
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);

        page.addView(icon(activity), new LinearLayout.LayoutParams(dp(activity, 104), dp(activity, 104)));

        TextView title = text(activity, Text.CONSENT_TITLE, skin.text, 22f, true);
        title.setPadding(0, dp(activity, 16), 0, dp(activity, 8));
        page.addView(title, wide());

        TextView body = text(activity, Text.CONSENT_BODY, skin.text, 15f, false);
        body.setAlpha(0.84f);
        body.setLineSpacing(0, 1.15f);
        page.addView(body, wide());

        TextView note = text(activity, Text.CONSENT_NOTE, skin.muted(), 12.5f, false);
        note.setPadding(dp(activity, 8), dp(activity, 14), dp(activity, 8), 0);
        page.addView(note, wide());

        // the countdown and the answers share one slot, so nothing jumps when they swap
        final FrameLayout slot = new FrameLayout(activity);
        final LinearLayout wait = new LinearLayout(activity);
        wait.setOrientation(LinearLayout.VERTICAL);
        wait.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout track = new FrameLayout(activity);
        track.setBackground(pill((skin.text & 0xFFFFFF) | 0x1F000000, dp(activity, 2)));
        final View fill = new View(activity);
        fill.setBackground(pill(Accent.colour(), dp(activity, 2)));
        fill.setPivotX(0f);
        fill.setScaleX(0f);
        track.addView(fill, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wait.addView(track, new LinearLayout.LayoutParams(dp(activity, 132), dp(activity, 4)));
        final TextView count = text(activity, "", skin.muted(), 13f, false);
        count.setPadding(0, dp(activity, 10), 0, 0);
        wait.addView(count, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        slot.addView(wait, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        final LinearLayout answers = new LinearLayout(activity);
        answers.setOrientation(LinearLayout.VERTICAL);
        final TextView yes = button(activity, skin, Text.CONSENT_YES, true);
        final TextView no = button(activity, skin, Text.CONSENT_NO, false);
        answers.addView(yes, wide());
        LinearLayout.LayoutParams gap = wide();
        gap.topMargin = dp(activity, 8);
        answers.addView(no, gap);
        answers.setAlpha(0f);
        answers.setVisibility(View.INVISIBLE);
        slot.addView(answers, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slotWhere = wide();
        slotWhere.topMargin = dp(activity, 18);
        page.addView(slot, slotWhere);

        final Panel panel = Panel.with(activity, skin, null);
        panel.view(page);
        final Dialog dialog = panel.dialog();
        dialog.setOnDismissListener(d -> {
            if (open.get() == dialog) { open.clear(); owner.clear(); }
        });
        open = new WeakReference<Dialog>(dialog);
        owner = new WeakReference<Activity>(activity);
        panel.show();
        // an answer is the only way out: no back, no tap outside, no swipe down
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        final boolean[] answered = {false};
        yes.setOnClickListener(v -> answer(activity, panel, true, answered, then));
        no.setOnClickListener(v -> answer(activity, panel, false, answered, then));

        final int[] second = {0};
        final Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            if (!dialog.isShowing() || answered[0]) return;
            int left = SECONDS - second[0];
            if (left <= 0) { reveal(activity, wait, answers); return; }
            count.setText(String.format(Locale.getDefault(), Text.CONSENT_WAIT, left));
            float target = (second[0] + 1) / (float) SECONDS;
            if (Motion.enabled(activity)) {
                fill.animate().scaleX(target).setDuration(1000L).setInterpolator(new LinearInterpolator()).start();
            } else {
                fill.setScaleX(target);
            }
            second[0]++;
            slot.postDelayed(tick[0], 1000L);
        };
        tick[0].run();
    }

    /** The window goes away with its screen; nothing is decided, so it comes back next time. */
    static void paused(Activity activity) {
        if (owner.get() != activity) return;
        Dialog dialog = open.get();
        open.clear();
        owner.clear();
        if (dialog != null) dialog.dismiss();
    }

    private static void answer(Activity activity, Panel panel, boolean on, boolean[] answered, Runnable then) {
        if (answered[0]) return;
        answered[0] = true;
        try {
            CrashReports.set(activity, on);   // also remembers that the question was asked
        } catch (Throwable error) {
            Diary.note("consent: " + error);
        }
        activity.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE).edit().putBoolean(CrashReports.ASKED, true).apply();
        panel.close();
        if (then != null) then.run();
    }

    private static void reveal(Activity activity, final View wait, View answers) {
        answers.setVisibility(View.VISIBLE);
        if (!Motion.enabled(activity)) {
            answers.setAlpha(1f);
            wait.setVisibility(View.GONE);
            return;
        }
        wait.animate().alpha(0f).setDuration(140L).withEndAction(() -> wait.setVisibility(View.GONE)).start();
        answers.animate().alpha(1f).setDuration(220L).setStartDelay(100L).start();
    }

    private static View icon(Activity activity) {
        FrameLayout frame = new FrameLayout(activity);
        GradientDrawable halo = new GradientDrawable();
        halo.setShape(GradientDrawable.OVAL);
        halo.setColor((Accent.colour() & 0xFFFFFF) | 0x1F000000);
        frame.setBackground(halo);
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable picture = null;
        try {
            picture = activity.getPackageManager().getApplicationIcon(activity.getApplicationInfo());
        } catch (Throwable ignored) {
        }
        if (picture == null) picture = SettingsGlyph.make("bug_report", Accent.colour());
        image.setImageDrawable(picture);
        frame.addView(image, new FrameLayout.LayoutParams(dp(activity, 64), dp(activity, 64), Gravity.CENTER));
        if (Motion.enabled(activity)) {
            frame.setAlpha(0f);
            frame.setScaleX(0.86f);
            frame.setScaleY(0.86f);
            frame.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(120L).setDuration(420L)
                    .setInterpolator(new OvershootInterpolator(1.4f)).start();
        }
        return frame;
    }

    private static TextView text(Context context, String words, int colour, float size, boolean medium) {
        TextView view = new TextView(context);
        view.setText(words);
        view.setTextColor(colour);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER_HORIZONTAL);
        if (medium) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    private static TextView button(Context context, Skin skin, String label, boolean main) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(15f);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        button.setMinHeight(dp(context, 48));
        int accent = Accent.colour();
        if (main) {
            button.setTextColor(onAccent(accent));
            button.setBackground(Expressive.tile(context, accent, 0x33FFFFFF, dp(context, 24), dp(context, 24), dp(context, 12)));
        } else {
            button.setTextColor(skin.text);
            button.setBackground(Expressive.tile(context, (skin.text & 0xFFFFFF) | 0x14000000,
                    (accent & 0xFFFFFF) | 0x33000000, dp(context, 24), dp(context, 24), dp(context, 12)));
        }
        button.setClickable(true);
        button.setFocusable(true);
        Motion.touch(button);
        return button;
    }

    private static GradientDrawable pill(int colour, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(colour);
        shape.setCornerRadius(radius);
        return shape;
    }

    private static LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int onAccent(int colour) {
        int red = (colour >> 16) & 0xFF, green = (colour >> 8) & 0xFF, blue = colour & 0xFF;
        return (red * 299 + green * 587 + blue * 114) / 1000 > 150 ? 0xFF111111 : 0xFFFFFFFF;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
