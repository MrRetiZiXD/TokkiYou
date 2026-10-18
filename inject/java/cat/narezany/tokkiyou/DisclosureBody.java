package cat.narezany.tokkiyou;

/** Expand one inline group as a unit, keeping later rows in the layout. */
final class DisclosureBody extends android.widget.FrameLayout {
    private float progress;
    private boolean enter=true;
    private android.animation.ValueAnimator motion;
    DisclosureBody(android.content.Context context, android.view.View content) {
        this(context,content,true);
    }
    DisclosureBody(android.content.Context context,android.view.View content,boolean enter) {
        super(context);this.enter=enter;progress=enter ? 0f : 1f;
        addView(content,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        setClipChildren(true);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!enter || !Motion.enabled(getContext())) {progress=1f;requestLayout();return;}
        motion=android.animation.ValueAnimator.ofFloat(progress,1f);
        motion.setDuration(180L);
        motion.setInterpolator(new android.view.animation.PathInterpolator(0.2f,0f,0f,1f));
        motion.addUpdateListener(a -> {
            progress=(Float)a.getAnimatedValue();requestLayout();
        });
        motion.start();
    }
    void collapse(Runnable done) {
        if(motion!=null)motion.cancel();
        if(!Motion.enabled(getContext())){done.run();return;}
        motion=android.animation.ValueAnimator.ofFloat(progress,0f);motion.setDuration(180L);
        motion.setInterpolator(new android.view.animation.PathInterpolator(0.4f,0f,1f,1f));
        motion.addUpdateListener(a->{progress=(Float)a.getAnimatedValue();requestLayout();});
        motion.addListener(new android.animation.AnimatorListenerAdapter(){
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator a){cancelled=true;}
            @Override public void onAnimationEnd(android.animation.Animator a){if(!cancelled)done.run();}
        });
        motion.start();
    }
    @Override protected void onMeasure(int width, int height) {
        super.onMeasure(width,height);
        setMeasuredDimension(getMeasuredWidth(),Math.round(getMeasuredHeight()*progress));
    }
    @Override protected void onDetachedFromWindow() {
        if (motion!=null) {motion.cancel();motion=null;}
        progress=1f;super.onDetachedFromWindow();
    }
}
