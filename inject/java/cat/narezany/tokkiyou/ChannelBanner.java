package cat.narezany.tokkiyou;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * The channel card in "About": a blue gradient, the Telegram plane in a soft disc, the
 * title, a neon "news / updates" line and a white "Subscribe" pill. The whole card is the button.
 */
final class ChannelBanner extends View {
    static final String URL = "https://t.me/tokkiyou";
    /** The design is drawn on a 1107 x 356 board and scaled to the width it gets. */
    private static final float W = 1107f, H = 356f;
    private static final float[][] SPARKS = {
            {138, 52, 3f}, {955, 82, 3.5f}, {4, 236, 3f}, {515, 302, 4f}, {1040, 330, 2.5f}, {92, 306, 2f}, {590, 40, 2.5f}};

    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path plane = new Path(), fold = new Path();
    private final RectF box = new RectF(), pill = new RectF();
    private ValueAnimator pulse;
    private float phase;
    private Shader background;
    private float backgroundWidth;

    ChannelBanner(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setContentDescription(Text.CHANNEL_TITLE + " TokkiYou. " + Text.CHANNEL_NEWS_A + Text.CHANNEL_NEWS_B + ". " + Text.CHANNEL_SUBSCRIBE);
        // A paper plane on a -1..1 board: the lit wing and body, and the shaded fold under them.
        plane.moveTo(-1f, 0.12f); plane.lineTo(1f, -0.78f); plane.lineTo(0.32f, 0.86f);
        plane.lineTo(-0.02f, 0.3f); plane.lineTo(-0.26f, 0.5f); plane.lineTo(-0.3f, 0.22f); plane.close();
        fold.moveTo(-0.3f, 0.22f); fold.lineTo(0.78f, -0.5f); fold.lineTo(-0.02f, 0.3f); fold.lineTo(-0.26f, 0.5f); fold.close();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        if (width <= 0) width = Math.round(340 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(width, Math.round(width * H / W));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (pulse != null) pulse.cancel();
        pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(2200L);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.addUpdateListener(a -> { phase = (Float) a.getAnimatedValue(); invalidate(); });
        pulse.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (pulse != null) { pulse.cancel(); pulse = null; }
        super.onDetachedFromWindow();
    }

    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), s = w / W;
        if (w <= 0) return;
        ink.setShader(null);
        ink.clearShadowLayer();
        ink.setStyle(Paint.Style.FILL);

        box.set(0, 0, w, H * s);
        if (background == null || backgroundWidth != w) {
            background = new LinearGradient(0, 0, w, H * s, 0xFF3DB9F7, 0xFF2A97D3, Shader.TileMode.CLAMP);
            backgroundWidth = w;
        }
        ink.setShader(background);
        canvas.drawRoundRect(box, 46 * s, 46 * s, ink);
        ink.setShader(null);

        // Faint dust, twinkling with the same breath as the neon.
        for (int i = 0; i < SPARKS.length; i++) {
            float wave = (i % 2 == 0) ? phase : 1f - phase;
            paint(ink,argb(0x30 + Math.round(0x40 * wave), 0xFFFFFF));
            canvas.drawCircle(SPARKS[i][0] * s, SPARKS[i][1] * s, SPARKS[i][2] * s, ink);
        }

        // Plane in a soft disc.
        paint(ink,argb(0x42, 0xFFFFFF));
        canvas.drawCircle(135 * s, 178 * s, 80 * s, ink);
        canvas.save();
        canvas.translate(135 * s - 3 * s, 178 * s + 2 * s);
        canvas.scale(46 * s, 46 * s);
        paint(ink,0xFFFFFFFF);
        canvas.drawPath(plane, ink);
        paint(ink,argb(0x88, 0xA9D7F7));
        canvas.drawPath(fold, ink);
        canvas.restore();

        // Title.
        ink.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        ink.setTextAlign(Paint.Align.LEFT);
        paint(ink,0xFFFFFFFF);
        ink.setTextSize(64 * s);
        canvas.drawText(Text.CHANNEL_TITLE, 265 * s, 102 * s, ink);
        canvas.drawText("TokkiYou", 265 * s, 168 * s, ink);

        // Neon line: a wide soft halo, a tight one, then the bright core on top.
        ink.setTextSize(45 * s);
        float breath = 0.55f + 0.45f * phase;
        int halo = 0xFF7DF9FF;
        paint(ink,argb(Math.round(0xFF * breath), 0x7DF9FF));
        ink.setShadowLayer((22 + 10 * phase) * s, 0, 0, halo);
        canvas.drawText(Text.CHANNEL_NEWS_A, 265 * s, 238 * s, ink);
        canvas.drawText(Text.CHANNEL_NEWS_B, 265 * s, 287 * s, ink);
        ink.setShadowLayer((8 + 4 * phase) * s, 0, 0, halo);
        paint(ink,0xFFE9FEFF);
        canvas.drawText(Text.CHANNEL_NEWS_A, 265 * s, 238 * s, ink);
        canvas.drawText(Text.CHANNEL_NEWS_B, 265 * s, 287 * s, ink);
        ink.clearShadowLayer();

        // "Subscribe" pill.
        pill.set(615 * s, 118 * s, 1055 * s, 236 * s);
        paint(ink,isPressed() ? 0xE6FFFCF7 : 0xFFFFFCF7);
        canvas.drawRoundRect(pill, 59 * s, 59 * s, ink);
        paint(ink,0xFF2C93CE);
        ink.setTextAlign(Paint.Align.CENTER);
        float size = 52 * s;
        ink.setTextSize(size);
        float room = pill.width() * 0.86f, need = ink.measureText(Text.CHANNEL_SUBSCRIBE);
        if (need > room) ink.setTextSize(size * room / need);
        Paint.FontMetrics m = ink.getFontMetrics();
        canvas.drawText(Text.CHANNEL_SUBSCRIBE, pill.centerX(), pill.centerY() - (m.ascent + m.descent) / 2f, ink);
        ink.setTextAlign(Paint.Align.LEFT);
    }

    /**
     * A packed colour onto the brush.
     *
     * Paint.setARGB wants the four components apart, and Paint.setColor is not
     * usable from inside the mod: every call to it is rewritten to the accent,
     * which is the point of that rewrite and not what a banner drawing its own
     * colours wants. So the packed value is taken apart here.
     */
    private static void paint(Paint brush, int colour) {
        brush.setARGB((colour >>> 24) & 0xFF, (colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF);
    }

    private static int argb(int alpha, int rgb) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }
}
