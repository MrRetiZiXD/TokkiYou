package cat.narezany.tokkiyou;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

/** Respect the phone's animation scale, including disabled motion. */
final class Motion {
    private Motion() {}

    /** A small press response keeps the original click and scrolling intact. */
    static void press(android.view.View view) {
        view.setFocusable(true);
        touch(view);
        android.graphics.drawable.Drawable base = view.getBackground();
        if (base instanceof android.graphics.drawable.RippleDrawable) return;
        if (base == null) base = new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT);
        view.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf((Accent.colour() & 0xFFFFFF) | 0x22000000),
                base, new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)));
    }

    /** Non-consuming feedback: scrolling cancels it, and the original click still runs. */
    static void touch(android.view.View view) {
        view.setOnTouchListener((target,event) -> {
            int action=event.getActionMasked();
            if(action==android.view.MotionEvent.ACTION_DOWN || action==android.view.MotionEvent.ACTION_UP
                    || action==android.view.MotionEvent.ACTION_CANCEL) {
                boolean down=action==android.view.MotionEvent.ACTION_DOWN && target.isEnabled() && target.isClickable() && enabled(target.getContext());
                target.animate().scaleX(down ? 0.985f : 1f).scaleY(down ? 0.985f : 1f)
                        .setDuration(down ? 80L : 140L)
                        .setInterpolator(new android.view.animation.PathInterpolator(0.2f,0f,0f,1f)).start();
            }
            return false;
        });
    }
    static void change(android.view.View view) {
        if(!enabled(view.getContext()))return;
        view.setAlpha(0.65f);view.animate().alpha(1f).setDuration(140L).start();
    }
    /** Finish the outgoing page before entering the selected section. */
    static void navigate(android.view.View view,float direction,Runnable replace) {
        view.animate().withEndAction(null).cancel();
        if(!enabled(view.getContext())){replace.run();return;}
        view.animate().alpha(0.7f).translationX(-direction*6*view.getResources().getDisplayMetrics().density)
                .setDuration(90L).withEndAction(() -> {replace.run();enter(view,direction);}).start();
    }

    /** Section navigation has one short directional transition, never per-row delays. */
    static void enter(android.view.View view, float direction) {
        view.animate().withEndAction(null).cancel();
        if (!enabled(view.getContext())) { view.setAlpha(1f); view.setTranslationX(0f); return; }
        view.setAlpha(0.6f);
        view.setTranslationX(direction * 12 * view.getResources().getDisplayMetrics().density);
        view.animate().alpha(1f).translationX(0f).setDuration(180L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    /** Begin below the measured surface, then settle without overshoot. */
    static Runnable rise(android.view.View view) {
        view.animate().withEndAction(null).cancel();
        if (!enabled(view.getContext())) {
            view.setAlpha(1f); view.setTranslationY(0f); return null;
        }
        view.setAlpha(0f);
        Runnable start = () -> {
            if (!view.isAttachedToWindow()) return;
            view.setTranslationY(Math.max(view.getHeight(), 1));
            view.animate().alpha(1f).translationY(0f).setDuration(280L)
                    .setInterpolator(new android.view.animation.PathInterpolator(0.2f,0f,0f,1f))
                    .start();
        };
        view.post(start);
        return start;
    }

    static boolean enabled(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 26) return ValueAnimator.areAnimatorsEnabled();
            return Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (Throwable ignored) {
            return true;
        }
    }
}
