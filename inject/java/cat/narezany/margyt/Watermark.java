package cat.narezany.margyt;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * The stamp TikTok paints onto a picture before it saves it.
 *
 * It looked for a while as though a server did this: the words are nowhere in
 * the apk in any language, and the file that comes out is a standard CDN width
 * with a colour profile and no exif. Both were red herrings. The label is
 * assembled on the phone -- text and a logo drawn into a bitmap of its own --
 * and then stamped onto the picture with a single `Canvas.drawBitmap`.
 *
 * That one call is what this replaces, and only inside the class that does it.
 * The class is found by a string it carries, `[tiktok_logo]`, rather than by
 * its name: the name is obfuscated and will be something else next release,
 * while the marker is part of the label's own template.
 *
 * With the switch off the call goes through exactly as it was, and with it on
 * nothing else about the picture changes -- the label is simply never stamped.
 */
public final class Watermark {

    private Watermark() {}

    public static void drawBitmap(Canvas canvas, Bitmap bitmap, float left, float top,
                                  Paint paint) {
        if (Download.isEnabled()) return;
        canvas.drawBitmap(bitmap, left, top, paint);
    }
}
