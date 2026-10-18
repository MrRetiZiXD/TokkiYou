package cat.narezany.margyt;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.WindowManager;

/** One motion lifecycle for buttons, back, and outside taps. */
final class SheetDialog extends Dialog {
    private final Context owner;
    private final boolean centered;
    private View surface;
    private android.animation.ValueAnimator backdrop;
    private float dim;
    private boolean closing;
    private Runnable entrance;
    private boolean dragAllowed=true;
    private final SheetDrag drag=new SheetDrag();
    private float dragOrigin;

    SheetDialog(Context context) { this(context, false); }
    SheetDialog(Context context, boolean centered) {
        super(context); owner = context; this.centered = centered;
    }

    @Override protected void onStart() {
        super.onStart();
        closing = false;
        if (getWindow() == null) return;
        getWindow().setWindowAnimations(0);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        dim = getWindow().getAttributes().dimAmount;
        if (Motion.enabled(getContext())) shade(0f,dim,280L);
        surface = getWindow().getDecorView().findViewById(android.R.id.content);
        if (surface != null) {
            Fonts.applyTree(surface);
            if (centered) {
                surface.setTranslationY(0f);
                surface.setAlpha(Motion.enabled(getContext()) ? 0f : 1f);
                if (Motion.enabled(getContext())) surface.animate().alpha(1f).setDuration(180L).start();
            } else {
                entrance = Motion.rise(surface);
                installGrip();
            }
        }
    }

    @Override public void dismiss() { leave(false); }
    @Override public void cancel() { leave(true); }
    @Override public void setCanceledOnTouchOutside(boolean allow){super.setCanceledOnTouchOutside(allow);dragAllowed=allow;}

    private void installGrip(){
        if(!(surface instanceof android.view.ViewGroup))return;
        android.view.ViewGroup content=(android.view.ViewGroup)surface;
        if(content.getChildCount()==0 || !(content.getChildAt(0) instanceof android.widget.LinearLayout))return;
        android.widget.LinearLayout card=(android.widget.LinearLayout)content.getChildAt(0);
        final int tag=0x4D477269;
        if(card.findViewWithTag(Integer.valueOf(tag))!=null)return;
        android.widget.FrameLayout grip=new android.widget.FrameLayout(owner);grip.setTag(Integer.valueOf(tag));
        card.addView(grip,0,new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,dp(16)));
        grip.setContentDescription(Local.of("Потяните вниз, чтобы закрыть"));grip.setClickable(true);grip.setFocusable(true);grip.setOnClickListener(v->{if(dragAllowed)cancel();});
        grip.setOnTouchListener((v,event)->{
            if(!dragAllowed || closing || surface==null)return false;
            switch(event.getActionMasked()){
                case android.view.MotionEvent.ACTION_DOWN:
                    if(entrance!=null)surface.removeCallbacks(entrance);entrance=null;
                    if(backdrop!=null)backdrop.cancel();dragOrigin=Math.max(0,surface.getTranslationY());surface.animate().withEndAction(null).cancel();surface.setAlpha(1f);
                    drag.start(event.getRawY(),event.getEventTime());return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    float distance=dragOrigin+drag.move(event.getRawY(),event.getEventTime());surface.setTranslationY(Math.min(distance,surface.getHeight()));
                    if(getWindow()!=null)getWindow().setDimAmount(dim*(1f-0.6f*Math.min(1f,distance/Math.max(1,surface.getHeight()))));return true;
                case android.view.MotionEvent.ACTION_UP:
                    if(drag.release(surface.getHeight(),owner.getResources().getDisplayMetrics().density,event.getEventTime())){cancel();return true;}
                    settle();return true;
                case android.view.MotionEvent.ACTION_CANCEL:settle();return true;
                default:return true;
            }
        });
    }
    private int dp(int n){return Math.round(n*owner.getResources().getDisplayMetrics().density);}
    private void settle(){
        if(surface==null)return;
        if(!Motion.enabled(owner)){surface.setTranslationY(0);if(getWindow()!=null)getWindow().setDimAmount(dim);return;}
        surface.animate().translationY(0).setDuration(220L).setInterpolator(new android.view.animation.PathInterpolator(0.2f,0f,0f,1f)).start();
        shade(getWindow()==null?dim:getWindow().getAttributes().dimAmount,dim,220L);
    }

    private void leave(final boolean cancelled) {
        boolean finishing = owner instanceof android.app.Activity
                && (((android.app.Activity)owner).isFinishing()
                || ((android.app.Activity)owner).isDestroyed());
        if (closing && !finishing) return;
        if (surface != null && entrance != null) surface.removeCallbacks(entrance);
        entrance = null;
        if (!isShowing() || surface == null || finishing || !Motion.enabled(getContext())) {
            finishLeave(cancelled);
            return;
        }
        closing = true;
        if (getWindow() != null) getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        shade(getWindow() == null ? dim : getWindow().getAttributes().dimAmount,0f,180L);
        surface.animate().withEndAction(null).cancel();
        surface.animate().translationY(centered ? 0f : Math.max(surface.getHeight(), 1)).alpha(0f)
                .setDuration(180L)
                .setInterpolator(new android.view.animation.PathInterpolator(0.4f,0f,1f,1f))
                .withEndAction(() -> finishLeave(cancelled)).start();
    }

    private void finishLeave(boolean cancelled) {
        // Dialog.cancel() calls dismiss() virtually. Bypass our delayed path then.
        closing = false;
        if (surface != null) surface.animate().withEndAction(null).cancel();
        surface = null;
        if (cancelled) super.cancel(); else super.dismiss();
    }

    private void shade(float from, float to, long duration) {
        if (backdrop != null) backdrop.cancel();
        if (getWindow() == null) return;
        getWindow().setDimAmount(from);
        backdrop = android.animation.ValueAnimator.ofFloat(from,to);
        backdrop.setDuration(duration);
        backdrop.addUpdateListener(a -> {
            // the sheet may have been taken off the screen between two frames of this
            try {
                if (getWindow() != null && isShowing()) getWindow().setDimAmount((Float)a.getAnimatedValue());
            } catch (IllegalArgumentException gone) {
                a.cancel();
            }
        });
        backdrop.start();
    }

    @Override protected void onStop() {
        if (backdrop != null) { backdrop.cancel(); backdrop = null; }
        if (getWindow() != null) getWindow().setDimAmount(dim);
        if (surface != null) {
            if (entrance != null) surface.removeCallbacks(entrance);
            surface.animate().withEndAction(null).cancel();
        }
        entrance = null;
        surface = null;
        super.onStop();
    }
}
