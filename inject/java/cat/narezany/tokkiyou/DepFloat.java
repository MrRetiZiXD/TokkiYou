package cat.narezany.tokkiyou;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.WeakHashMap;

/**
 * The reels in a little window over whatever TikTok is showing.
 *
 * Switched on in the settings, it sits on every screen of the app as a pill
 * with the balance on it. A tap opens it into three reels, a stake and a
 * button; holding the pill and moving drags it out of the way. It is a view in
 * the app's own window and nothing more -- no permission to draw over other
 * apps is asked for, and it is not there outside TikTok.
 */
final class DepFloat {

    private DepFloat() {}

    private static final long[] STAKES = {10, 50, 100, 500, 1000};
    private static final WeakHashMap<Activity, View> SHOWN = new WeakHashMap<Activity, View>();

    // where it was left and what was being staked, the same on every screen
    private static float x = -1, y = -1;
    private static int stake = 1;
    private static boolean open;

    static boolean isOn(Context context) {
        try {
            return context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(DepGame.KEY_FLOAT, false);
        } catch (Throwable error) {
            return false;
        }
    }

    static void set(Context context, boolean on) {
        context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(DepGame.KEY_FLOAT, on).apply();
        Activity here = Watch.here();
        if (here != null) attach(here);
    }

    /** A screen came up, or the switch was moved: put the window on it or take it off. */
    static void attach(final Activity activity) {
        try {
            View had = SHOWN.get(activity);
            boolean wanted = isOn(activity)
                    && !activity.getClass().getName().startsWith("cat.narezany.tokkiyou.");
            if (!wanted) {
                if (had != null && had.getParent() instanceof ViewGroup) ((ViewGroup) had.getParent()).removeView(had);
                SHOWN.remove(activity);
                return;
            }
            View decor = activity.getWindow().getDecorView();
            if (!(decor instanceof FrameLayout)) return;
            if (had != null && had.getParent() == decor) {
                place(had, (FrameLayout) decor);
                return;
            }
            View made = build(activity, (FrameLayout) decor);
            ((FrameLayout) decor).addView(made, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.LEFT));
            SHOWN.put(activity, made);
            place(made, (FrameLayout) decor);
        } catch (Throwable error) {
            Diary.note("dep window: " + error);
        }
    }

    private static void place(View card, FrameLayout decor) {
        float unit = card.getResources().getDisplayMetrics().density;
        if (x < 0) { x = unit * 16; y = unit * 140; }
        card.setTranslationX(x);
        card.setTranslationY(y);
        card.bringToFront();
    }

    private static View build(final Activity activity, final FrameLayout decor) {
        final float unit = activity.getResources().getDisplayMetrics().density;
        final LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(unit * 8);
        card.setPadding(pad, pad, pad, pad);
        GradientDrawable back = new GradientDrawable();
        back.setColor(0xF01C1B21);
        back.setCornerRadius(unit * 22);
        card.setBackground(back);
        card.setElevation(unit * 12);

        final TextView pill = text(activity, 14, 0xFFFFFFFF);
        pill.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pill.setPadding(Math.round(unit * 8), Math.round(unit * 4), Math.round(unit * 8), Math.round(unit * 4));
        card.addView(pill);

        final LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        card.addView(body);

        final Skin dark = Skin.remembered(activity);
        final TextView[] reels = new TextView[3];
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER);
        for (int i = 0; i < 3; i++) {
            reels[i] = DepCalc.reel(activity, dark, unit, 48, 44, 22);
            reels[i].setTextColor(0xFFFFFFFF);
            ((GradientDrawable) reels[i].getBackground()).setColor(0xFF2C2B33);
            LinearLayout.LayoutParams where = new LinearLayout.LayoutParams(
                    Math.round(unit * 48), Math.round(unit * 44));
            where.setMargins(Math.round(unit * 3), Math.round(unit * 6), Math.round(unit * 3), Math.round(unit * 4));
            row.addView(reels[i], where);
        }
        body.addView(row);

        final TextView said = text(activity, 12, 0xFFB7B5C2);
        said.setGravity(Gravity.CENTER);
        body.addView(said);

        LinearLayout controls = new LinearLayout(activity);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        final TextView amount = text(activity, 14, 0xFFFFFFFF);
        amount.setGravity(Gravity.CENTER);
        final Runnable shown = () -> {
            pill.setText("🎰  " + (!DepGame.verified() ? Text.DEP_PROVE_SHORT
                    : DepGame.balance < 0 ? "…" : DepGame.balance + DepGame.COIN));
            amount.setText(STAKES[stake] + DepGame.COIN);
        };
        final boolean[] busy = {false};
        controls.addView(key(activity, unit, "−", 0xFF2C2B33, 0xFFFFFFFF, () -> {
            stake = Math.max(0, stake - 1);
            shown.run();
        }), new LinearLayout.LayoutParams(Math.round(unit * 36), Math.round(unit * 36)));
        controls.addView(amount, new LinearLayout.LayoutParams(Math.round(unit * 64), ViewGroup.LayoutParams.WRAP_CONTENT));
        controls.addView(key(activity, unit, "+", 0xFF2C2B33, 0xFFFFFFFF, () -> {
            stake = Math.min(STAKES.length - 1, stake + 1);
            shown.run();
        }), new LinearLayout.LayoutParams(Math.round(unit * 36), Math.round(unit * 36)));
        LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(Math.round(unit * 64), Math.round(unit * 36));
        wide.leftMargin = Math.round(unit * 6);
        controls.addView(key(activity, unit, Text.DEP_SPIN, Accent.colour(), DepCalc.onAccent(), () -> {
            if (busy[0]) return;
            busy[0] = true;
            said.setText("…");
            DepGame.spin(STAKES[stake], reels, (staked, won, times, trouble) -> {
                busy[0] = false;
                said.setText(trouble != null ? trouble
                        : won == 0 ? "−" + staked + DepGame.COIN : "+" + won + DepGame.COIN + " (×" + times + ")");
                shown.run();
            });
        }), wide);
        LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        under.topMargin = Math.round(unit * 4);
        body.addView(controls, under);
        shown.run();
        if (DepGame.verified() && DepGame.balance < 0) {
            DepGame.ask("state", null, (code, heard) -> {
                if (card.isAttachedToWindow()) shown.run();
            });
        }

        // the pill is the handle: a tap opens and shuts it, a drag moves it
        final int slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        pill.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY, fromX, fromY;
            boolean moved;
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX(); downY = event.getRawY();
                        fromX = card.getTranslationX(); fromY = card.getTranslationY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                        if (!moved && Math.hypot(dx, dy) < slop) return true;
                        moved = true;
                        x = Math.max(0, Math.min(decor.getWidth() - card.getWidth(), fromX + dx));
                        y = Math.max(0, Math.min(decor.getHeight() - card.getHeight(), fromY + dy));
                        card.setTranslationX(x);
                        card.setTranslationY(y);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            open = !open;
                            body.setVisibility(open ? View.VISIBLE : View.GONE);
                            shown.run();
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
        return card;
    }

    private static TextView text(Context context, int sp, int colour) {
        TextView view = new TextView(context);
        view.setTextColor(colour);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return view;
    }

    private static TextView key(Context context, float unit, String label, int fill, int ink, final Runnable then) {
        TextView key = text(context, 15, ink);
        key.setText(label);
        key.setGravity(Gravity.CENTER);
        key.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(unit * 18);
        key.setBackground(shape);
        key.setOnClickListener(v -> then.run());
        return key;
    }
}
