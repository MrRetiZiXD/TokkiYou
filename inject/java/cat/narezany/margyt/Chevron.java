package cat.narezany.margyt;

/** Resource-free vector chevron, matching assets/ui/chevron.svg. */
final class Chevron extends android.view.View {
    private final android.graphics.Paint ink = new android.graphics.Paint(3);
    Chevron(android.content.Context context, int colour, boolean expanded, boolean animate) {
        super(context);
        ink.setColor(colour); ink.setStrokeWidth(1.8f);
        ink.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        float target = expanded ? 180f : 0f;
        setRotation(animate && Motion.enabled(context) ? 180f-target : target);
        if (animate && Motion.enabled(context)) post(() -> {
            if (isAttachedToWindow()) animate().rotation(target).setDuration(180L)
                    .setInterpolator(new android.view.animation.PathInterpolator(0.2f,0f,0f,1f)).start();
        });
    }
    @Override protected void onMeasure(int width, int height) {
        int size = Math.round(24*getResources().getDisplayMetrics().density);
        setMeasuredDimension(resolveSize(size,width),resolveSize(size,height));
    }
    @Override protected void onDraw(android.graphics.Canvas canvas) {
        super.onDraw(canvas);
        float unit = Math.min(getWidth(),getHeight())/24f;
        int saved=canvas.save();
        canvas.translate((getWidth()-24*unit)/2,(getHeight()-24*unit)/2);
        canvas.scale(unit,unit);
        canvas.drawLine(6,9,12,15,ink); canvas.drawLine(12,15,18,9,ink);
        canvas.restoreToCount(saved);
    }
    @Override protected void onDetachedFromWindow() {
        animate().cancel(); super.onDetachedFromWindow();
    }
}
