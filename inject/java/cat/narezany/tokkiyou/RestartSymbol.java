package cat.narezany.tokkiyou;

/** Same 24-unit vector as assets/ui/restart.svg; no added APK resources. */
final class RestartSymbol extends android.view.View {
    private final android.graphics.Paint ink = new android.graphics.Paint(3);
    private final android.graphics.Path path = new android.graphics.Path();
    RestartSymbol(android.content.Context context, int colour) {
        super(context);
        ink.setColor(colour); ink.setStyle(android.graphics.Paint.Style.STROKE);
        ink.setStrokeWidth(1.8f); ink.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        ink.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        path.moveTo(20,3); path.lineTo(20,8); path.lineTo(15,8);
        path.moveTo(20,8); path.cubicTo(18.6f,5.6f,15.7f,4,12,4);
        path.cubicTo(7.6f,4,4,7.6f,4,12);
        path.cubicTo(4,16.4f,7.6f,20,12,20);
        path.cubicTo(15.7f,20,18.8f,17.5f,19.7f,14);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void colour(int value) { ink.setColor(value); invalidate(); }
    @Override protected void onDraw(android.graphics.Canvas canvas) {
        super.onDraw(canvas);
        float unit = Math.min(getWidth(),getHeight()) / 24f;
        int saved = canvas.save();
        canvas.translate((getWidth()-24*unit)/2,(getHeight()-24*unit)/2);
        canvas.scale(unit,unit); canvas.drawPath(path,ink); canvas.restoreToCount(saved);
    }
}
